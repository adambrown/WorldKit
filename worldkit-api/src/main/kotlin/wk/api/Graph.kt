package wk.api

import cern.colt.list.IntArrayList
import wk.internal.ext.intList
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.*

private typealias Point = Pair<Double, Double>
private typealias Triangle = Triple<Int, Int, Int>
private typealias Edge = Pair<Point, Point>

@PublicApi
class GraphLite(
        @PublicApi
        val stride: Int,
        @PublicApi
        val width: Float,
        private val maxConnections: Int,
        private val minConnectionDist: Float,
        private val connectionDelta: Float,
        private val minArea: Float,
        private val areaDelta: Float,
        private val points: ShortArray,
        @PublishedApi
        internal val connectionsAreas: IntArray,
        private val connectionDists: ByteArray) {

    companion object {

        @PublicApi
        const val SELF_OFFSET: Byte = 12

        @PublicApi
        fun from(seed: Long, stride: Int, width: Double = 1.0, constraint: Double = 0.85): GraphLite {
            return from(stride, width.toFloat(), generateSemiRandomPoints(seed, width, stride, constraint))
        }

        @PublicApi
        fun from(stride: Int, width: Float, points: DoubleArray): GraphLite {
            val pointCount = points.size / 2
            val strideM1 = stride - 1
            val result = triangulate(points, stride)
            val triangles = result.triangles.elements()
            val halfedges = result.halfedges.elements()
            val pointToHalfedgeIndex = constructPointToHalfedgeIndex(points, triangles, halfedges)
            val circumcenters = constructCircumcenters(points, triangles)
            val cellAreas = constructCellAreas(points, halfedges, pointToHalfedgeIndex, circumcenters)
            val connectionsAreas = IntArray(pointCount)
            val lock = Object()
            var maxConnections = 0
            var minArea = Float.POSITIVE_INFINITY
            var maxArea = 0.0f
            var minConnectionDist2 = Float.POSITIVE_INFINITY
            var maxConnectionDist2 = 0.0f
            val threads = 256
            (0 until threads).toList().parallelStream().forEach { thread ->
                var lMaxConnections = 0
                var lMinArea = Float.POSITIVE_INFINITY
                var lMaxArea = 0.0f
                var lMinConnectionDist2 = Float.POSITIVE_INFINITY
                var lMaxConnectionDist2 = 0.0f
                val eapIt = EdgesAroundPointIterator()
                val papIt = PointsAdjacentToPointIterator(eapIt, triangles)
                for (pointId in thread until pointCount step threads) {
                    val (pointX, pointY) = points.unpack(pointId)
                    val x = (pointId % stride)
                    val y = (pointId / stride)
                    var localMaxCons = 0
                    var bitSet = 0
                    for (otherId in papIt.reuse(getEdgesAroundPoint(halfedges, pointToHalfedgeIndex, pointId, eapIt), triangles)) {
                        val (otherPointX, otherPointY) = points.unpack(otherId)
                        val dist = dist2(pointX, pointY, otherPointX, otherPointY).toFloat()
                        if (dist < lMinConnectionDist2) {
                            lMinConnectionDist2 = dist
                        }
                        if (dist > lMaxConnectionDist2) {
                            lMaxConnectionDist2 = dist
                        }
                        val ox = (otherId % stride)
                        val oy = (otherId / stride)
                        val dx = (ox - x) + 2
                        val dy = (oy - y) + 2
                        if (dy in (0..4) && dx in (0..4)) {
                            var mask = dy * 5 + dx
                            if (mask > 11) {
                                mask -= 1
                            }
                            bitSet = bitSet or (1 shl mask)
                            localMaxCons += 1
                        }
                    }
                    connectionsAreas[pointId] = bitSet
                    if (localMaxCons > lMaxConnections) {
                        lMaxConnections = localMaxCons
                    }
                    if (!(x == 0 || x == strideM1 || y == 0 || y == strideM1)) {
                        val cellArea = cellAreas[pointId]
                        if (cellArea < lMinArea) {
                            lMinArea = cellArea
                        }
                        if (cellArea > lMaxArea) {
                            lMaxArea = cellArea
                        }
                    }
                }
                synchronized(lock) {
                    if (lMaxConnections > maxConnections) {
                        maxConnections = lMaxConnections
                    }
                    if (lMinArea < minArea) {
                        minArea = lMinArea
                    }
                    if (lMaxArea > maxArea) {
                        maxArea = lMaxArea
                    }
                    if (lMinConnectionDist2 < minConnectionDist2) {
                        minConnectionDist2 = lMinConnectionDist2
                    }
                    if (lMaxConnectionDist2 > maxConnectionDist2) {
                        maxConnectionDist2 = lMaxConnectionDist2
                    }
                }
            }
            val areaDelta = maxArea - minArea
            val areaIncrement = 255.0f / areaDelta
            val minConnectionDist = sqrt(minConnectionDist2)
            val maxConnectionDist = sqrt(maxConnectionDist2)
            val connectionDelta = maxConnectionDist - minConnectionDist
            val connectionDistIncrement = 255.0f / connectionDelta
            val pixelWidth = width / strideM1
            val pixelMultiplier = 255.0 / pixelWidth.toDouble()
            val newPoints = ShortArray(pointCount)
            val connectionDists = ByteArray(pointCount * maxConnections)
            (0 until threads).toList().parallelStream().forEach { thread ->
                val ptpIt = PointToPointIterator()
                for (pointId in thread until pointCount step threads) {
                    var otherDistOff = (pointId * maxConnections)
                    val x = pointId % stride
                    val y = pointId / stride
                    val isBorder = x == 0 || x == strideM1 || y == 0 || y == strideM1
                    val offset = if (isBorder) 0.0f else 0.5f
                    val px = (x - offset) * pixelWidth
                    val py = (y - offset) * pixelWidth
                    val (pointX, pointY) = points.unpack(pointId)
                    val dx = pointX - px.toDouble()
                    val dy = pointY - py.toDouble()
                    val bx = (dx * pixelMultiplier + 0.5).toInt()
                    val by = (dy * pixelMultiplier + 0.5).toInt()
                    newPoints[pointId] = (bx or (by shl 8)).toShort()
                    for (otherId in ptpIt.reuse(stride, x, y, connectionsAreas[pointId])) {
                        val (otherPointX, otherPointY) = points.unpack(otherId)
                        val dist = dist(pointX, pointY, otherPointX, otherPointY)
                        connectionDists[otherDistOff] = ((dist - minConnectionDist) * connectionDistIncrement + 0.5).toInt().toByte()
                        otherDistOff += 1
                    }
                    if (!isBorder) {
                        val cellArea = cellAreas[pointId]
                        connectionsAreas[pointId] = connectionsAreas[pointId] or ((((cellArea - minArea) * areaIncrement).roundToInt()) shl 24)
                    }
                }
            }

            return GraphLite(
                    stride = stride,
                    width = width,
                    maxConnections = maxConnections,
                    minConnectionDist = minConnectionDist,
                    connectionDelta = connectionDelta,
                    minArea = minArea,
                    areaDelta = areaDelta,
                    points = newPoints,
                    connectionsAreas = connectionsAreas,
                    connectionDists = connectionDists)
        }

        @PublicApi
        fun deserializeFrom(input: DataInputStream): GraphLite {
            val stride = input.readInt()
            val width = input.readFloat()
            val maxConnections = input.readByte().toInt() and 0xFF
            val minConnectionDist = input.readFloat()
            val connectionDelta = input.readFloat()
            val minArea = input.readFloat()
            val areaDelta = input.readFloat()
            var count = input.readInt()
            val points = ShortArray(count) {
                input.readShort()
            }
            count = input.readInt()
            val connectionsAreas = IntArray(count) {
                input.readInt()
            }
            count = input.readInt()
            val connectionDists = ByteArray(count) {
                input.readByte()
            }
            return GraphLite(
                    stride = stride,
                    width = width,
                    maxConnections = maxConnections,
                    minConnectionDist = minConnectionDist,
                    connectionDelta = connectionDelta,
                    minArea = minArea,
                    areaDelta = areaDelta,
                    points = points,
                    connectionsAreas = connectionsAreas,
                    connectionDists = connectionDists)
        }
    }

    @PublicApi
    fun serializeTo(output: DataOutputStream) {
        output.writeInt(stride)
        output.writeFloat(width)
        output.writeByte(maxConnections)
        output.writeFloat(minConnectionDist)
        output.writeFloat(connectionDelta)
        output.writeFloat(minArea)
        output.writeFloat(areaDelta)
        output.writeInt(points.size)
        points.forEach {
            output.writeShort(it.toInt())
        }
        output.writeInt(connectionsAreas.size)
        connectionsAreas.forEach {
            output.writeInt(it)
        }
        output.writeInt(connectionDists.size)
        connectionDists.forEach {
            output.writeByte(it.toInt())
        }
    }

    @PublicApi
    val size = stride * stride
    private val strideM1 = stride - 1
    private val pixelWidth = width / strideM1
    private val pixelIncrement = pixelWidth / 255.0f
    private val areaIncrement = areaDelta / 255.0f
    private val connectionDistIncrement = connectionDelta / 255.0f

    @PublicApi
    fun getIdFromOffset(id: Int, offset: Byte): Int {
        val index = offset.toInt()
        if (index == 12) return id
        val ox = (id % stride) + ((index % 5) - 2)
        val oy = (id / stride) + ((index / 5) - 2)
        return oy * stride + ox
    }

    @PublicApi
    fun getIdAndDistanceFromOffset(id: Int, offset: Byte, retVal: M2<Int, Float>): M2<Int, Float> {
        val index = offset.toInt()
        if (index == 12) {
            retVal.first = id
            retVal.second = 0.0f
            return retVal
        }
        val x = id % stride
        val y = id / stride
        val bitSet = connectionsAreas[id]
        var bitsToCheck = (-1 shl if (index > 11) index - 1 else index).inv() and bitSet
        var rightSetBits = 0
        while (bitsToCheck > 0) {
            bitsToCheck = bitsToCheck and bitsToCheck - 1
            rightSetBits++
        }
        val ox = x + ((index % 5) - 2)
        val oy = y + ((index / 5) - 2)
        val distance = (connectionDists[id * maxConnections + rightSetBits].toInt() and 0xFF) * connectionDistIncrement + minConnectionDist
        retVal.first = oy * stride + ox
        retVal.second = distance
        return retVal
    }

    @PublicApi
    fun getOffsetFromAdjacentIds(centerId: Int, adjacentId: Int): Byte {
        val cx = centerId % stride
        val cy = centerId / stride
        val ax = adjacentId % stride
        val ay = adjacentId / stride
        val dx = (ax - cx) + 2
        val dy = (ay - cy) + 2
        return (dy * 5 + dx).toByte()
    }

    @PublicApi
    inline fun forEachAdjacent(id: Int, callback: (adjacent: Int) -> Unit) {
        val x = id % stride
        val y = id / stride
        val bitSet = connectionsAreas[id]
        for (i in 0..23) {
            if ((bitSet and (1 shl i)) != 0) {
                val index = if (i > 11) i + 1 else i
                val ox = x + ((index % 5) - 2)
                val oy = y + ((index / 5) - 2)
                callback(oy * stride + ox)
            }
        }
    }

    @PublicApi
    fun getPoint2F(id: Int, outPoint: Point2F = point2()): Point2F {
        val x = id % stride
        val y = id / stride
        val isBorder = x == 0 || x == strideM1 || y == 0 || y == strideM1
        val offset = if (isBorder) 0.0f else 0.5f
        val px = (x - offset) * pixelWidth
        val py = (y - offset) * pixelWidth
        val b = points[id].toInt()
        val bx = b and 0x00FF
        val by = (b and 0xFF00) shr 8
        outPoint.x = bx * pixelIncrement + px
        outPoint.y = by * pixelIncrement + py
        return outPoint
    }

    @PublicApi
    fun getArea(id: Int): Float {
        val x = id % stride
        val y = id / stride
        return if (x == 0 || x == strideM1 || y == 0 || y == strideM1) 0.0f else (connectionsAreas[id] ushr 24) * areaIncrement + minArea
    }

    @PublicApi
    fun getBorder(): MutableList<Int> {
        val borderIds = intList(strideM1 * 4)
        borderIds.addAll(0 until stride)
        (1 until strideM1).forEach { y ->
            val yOff = y * stride
            borderIds.add(yOff)
            borderIds.add(yOff + strideM1)
        }
        borderIds.addAll((stride * strideM1) until size)
        return borderIds
    }
}

private fun getTriangleOfEdge(edgeId: Int): Int {
    return edgeId / 3
}

private fun constructPointToHalfedgeIndex(points: DoubleArray, triangles: IntArray, halfedges: IntArray): IntArray {
    val pointCount = points.size / 2
    val pointToHalfEdgeIndex = IntArray(pointCount) { NONE }
    val threads = 256
    (0 until threads).toList().parallelStream().forEach { thread ->
        for (edgeId in thread until triangles.size step threads) {
            val endId = triangles[nextHalfedge(edgeId)]
            if (pointToHalfEdgeIndex[endId] == NONE || halfedges[edgeId] == NONE) {
                pointToHalfEdgeIndex[endId] = edgeId
            }
        }
    }
    return pointToHalfEdgeIndex
}

private fun constructCircumcenters(points: DoubleArray, triangles: IntArray): FloatArray {
    val triCount = (triangles.size / 3)
    val circumcenters = FloatArray(triCount * 2)
    val threads = 256
    (0 until threads).toList().parallelStream().forEach { thread ->
        for (triangleId in thread until triCount step threads) {
            val (x, y) = computeCircumcenter(points, triangles, triangleId)
            val i = triangleId * 2
            circumcenters[i] = x.toFloat()
            circumcenters[i + 1] = y.toFloat()
        }
    }
    return circumcenters
}

private fun constructCellAreas(points: DoubleArray, halfedges: IntArray, pointToHalfedgeIndex: IntArray, circumcenters: FloatArray): FloatArray {
    val pointCount = points.size / 2
    val cellAreas = FloatArray(pointCount)
    val threads = 256
    (0 until threads).toList().parallelStream().forEach { thread ->
        val ei = EdgesAroundPointIterator()
        val vi = VoronoiEdgesAroundPointIterator(ei)
        (thread until pointCount step threads).forEach { pointId ->
            cellAreas[pointId] = computeCellArea(halfedges, pointToHalfedgeIndex, circumcenters, pointId, vi, ei)
        }
    }
    return cellAreas
}

private fun computeCircumcenter(points: DoubleArray, triangles: IntArray, triangleId: Int): Point {
    val (aId, bId, cId) = getPointsOfTriangle(triangles, triangleId)
    val a = points.unpack(aId)
    val b = points.unpack(bId)
    val c = points.unpack(cId)

    val ad = a.x * a.x + a.y * a.y
    val bd = b.x * b.x + b.y * b.y
    val cd = c.x * c.x + c.y * c.y
    val d = 2.0 * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
    return Point(
            1.0 / d * (ad * (b.y - c.y) + bd * (c.y - a.y) + cd * (a.y - b.y)),
            1.0 / d * (ad * (c.x - b.x) + bd * (a.x - c.x) + cd * (b.x - a.x)))
}

private fun computeCellArea(halfedges: IntArray, pointToHalfedgeIndex: IntArray, circumcenters: FloatArray, pointId: Int, voronoiIterator: VoronoiEdgesAroundPointIterator, edgeIterator: EdgesAroundPointIterator): Float {
    val edges = voronoiIterator.reuse(getEdgesAroundPoint(halfedges, pointToHalfedgeIndex, pointId, edgeIterator), circumcenters)
    var sum = 0.0
    for ((a, b) in edges) {
        sum += a.x * b.y - a.y * b.x
    }
    return abs((sum * 0.5).toFloat())
}

private fun getPointsOfTriangle(triangles: IntArray, triangleId: Int): Triangle {
    val i = 3 * triangleId
    return Triangle(triangles[i], triangles[i + 1], triangles[i + 2])
}

private fun getEdgesAroundPoint(halfedges: IntArray, pointToHalfedgeIndex: IntArray, pointId: Int, iterator: EdgesAroundPointIterator): EdgesAroundPointIterator {
    return iterator.reuse(halfedges, pointToHalfedgeIndex, pointId)
}

private class EdgesAroundPointIterator(private var halfedges: IntArray = intArrayOf(), private var start: Int = NONE, private var incoming: Int = start) : Iterator<Int> {

    private var next: Int? = null

    init {
        next = primeNext()
    }

    override fun next(): Int {
        val tmp = next!!
        next = primeNext()
        return tmp
    }

    private fun primeNext(): Int? {
        val tmp = incoming
        return if (tmp == NONE) {
            return null
        } else {
            incoming = halfedges[nextHalfedge(tmp)]
            if (incoming == start) {
                incoming = NONE
            }
            tmp
        }
    }

    override fun hasNext(): Boolean {
        val tmp = next != null
        if (!tmp) {
            reset()
        }
        return tmp
    }

    fun reset() {
        incoming = start
        next = primeNext()
    }

    fun reuse(halfedges: IntArray, pointToHalfedgeIndex: IntArray, pointId: Int): EdgesAroundPointIterator {
        this.halfedges = halfedges
        this.start = pointToHalfedgeIndex[pointId]
        reset()
        return this
    }
}

private class PointsAdjacentToPointIterator(private var edges: EdgesAroundPointIterator = EdgesAroundPointIterator(), private var triangles: IntArray = intArrayOf()): Iterator<Int> {

    override fun next(): Int {
        return triangles[edges.next()]
    }

    override fun hasNext(): Boolean {
        return edges.hasNext()
    }

    fun reset() {
        edges.reset()
    }

    fun reuse(edges: EdgesAroundPointIterator, triangles: IntArray): PointsAdjacentToPointIterator {
        this.edges = edges
        this.triangles = triangles
        reset()
        return this
    }
}

private class VoronoiEdgesAroundPointIterator(
        private var edges: EdgesAroundPointIterator = EdgesAroundPointIterator(),
        private var circumcenters: FloatArray = floatArrayOf(),
        private var start: Int = NONE,
        private var last: Int = NONE) : Iterator<Edge> {

    private var next: Edge? = null

    init {
        next = primeNext()
    }

    override fun next(): Edge {
        val tmp = next!!
        next = primeNext()
        return tmp
    }

    private fun primeNext(): Edge? {
        if (start != NONE && start == last) {
            return null
        }
        if (start == NONE) {
            if (!edges.hasNext()) {
                return null
            } else {
                val tmp = edges.next()
                start = tmp
                last = tmp
            }
        }
        return if (!edges.hasNext()) {
            val next = Edge(
                    circumcenters.unpack(getTriangleOfEdge(last)),
                    circumcenters.unpack(getTriangleOfEdge(start)))
            last = start
            next
        } else {
            val tmp = edges.next()
            val next = Edge(
                    circumcenters.unpack(getTriangleOfEdge(last)),
                    circumcenters.unpack(getTriangleOfEdge(tmp)))
            last = tmp
            next
        }
    }

    override fun hasNext(): Boolean {
        val tmp = next != null
        if (!tmp) {
            reset()
        }
        return tmp
    }

    fun reset() {
        edges.reset()
        start = NONE
        last = NONE
        next = primeNext()
    }

    fun reuse(edges: EdgesAroundPointIterator, circumcenters: FloatArray): VoronoiEdgesAroundPointIterator {
        this.edges = edges
        this.circumcenters = circumcenters
        reset()
        return this
    }
}

private class PointToPointIterator(
        private var stride: Int = 0,
        private var x: Int = 0,
        private var y: Int = 0,
        private var bitSet: Int = 0,
        private var shift: Int = 0) : Iterator<Int> {

    private var next: Int? = null

    init {
        next = primeNext()
    }

    override fun next(): Int {
        val tmp = next!!
        next = primeNext()
        return tmp
    }

    fun primeNext(): Int? {
        while ((bitSet and (1 shl shift)) == 0 && shift < 24) {
            shift += 1
        }
        return if (shift == 24) {
            shift = 0
            null
        } else {
            val tmp = shift
            shift += 1
            val index = if (tmp > 11) tmp + 1 else tmp
            val ox = x + (index % 5 - 2)
            val oy = y + (index / 5 - 2)
            oy * stride + ox
        }
    }

    override fun hasNext(): Boolean {
        val tmp = next != null
        if (!tmp) {
            reset()
        }
        return tmp
    }

    fun reset() {
        shift = 0
        next = primeNext()
    }

    fun reuse(stride: Int, x: Int, y: Int, bitSet: Int): PointToPointIterator {
        this.stride = stride
        this.x = x
        this.y = y
        this.bitSet = bitSet
        reset()
        return this
    }
}

private const val NONE = -1

private const val EPSILON = 1.0E-323

private fun dist2(x1: Double, y1: Double, x2: Double, y2: Double): Double {
    val dx = x1 - x2
    val dy = y1 - y2
    return dx * dx + dy * dy
}

private fun dist(x1: Double, y1: Double, x2: Double, y2: Double): Double {
    return sqrt(dist2(x1, y1, x2, y2))
}

private fun circumradius2(points: DoubleArray, a: Int, b: Int, c: Int): Double {
    val (x, y) = circumdelta(points, a, b, c)
    return x * x + y * y
}

private fun circumdelta(points: DoubleArray, a: Int, b: Int, c: Int): Point {
    val (ax, ay) = points.unpack(a)
    val (bx, by) = points.unpack(b)
    val (cx, cy) = points.unpack(c)

    val dx = bx - ax
    val dy = by - ay
    val ex = cx - ax
    val ey = cy - ay

    val bl = dx * dx + dy * dy
    val cl = ex * ex + ey * ey
    val d = 0.5 / (dx * ey - dy * ex)

    val x = (ey * bl - dy * cl) * d
    val y = (dx * cl - ex * bl) * d
    return x to y
}

private fun orient(points: DoubleArray, a: Int, q: Int, r: Int): Boolean {
    val (ax, ay) = points.unpack(a)
    val (qx, qy) = points.unpack(q)
    val (rx, ry) = points.unpack(r)
    return (qy - ay) * (rx - qx) - (qx - ax) * (ry - qy) < 0.0
}

private fun circumcenter(points: DoubleArray, a: Int, b: Int, c: Int): Point {
    val (x, y) = circumdelta(points, a, b, c)
    val i = a * 2
    return points[i] + x to points[i + 1] + y
}

private fun inCircle(points: DoubleArray, a: Int, b: Int, c: Int, p: Int): Boolean {
    val (ax, ay) = points.unpack(a)
    val (bx, by) = points.unpack(b)
    val (cx, cy) = points.unpack(c)
    val (px, py) = points.unpack(p)

    val dx = ax - px
    val dy = ay - py
    val ex = bx - px
    val ey = by - py
    val fx = cx - px
    val fy = cy - py

    val ap = dx * dx + dy * dy
    val bp = ex * ex + ey * ey
    val cp = fx * fx + fy * fy

    return dx * (ey * cp - bp * fy) - dy * (ex * cp - bp * fx) + ap * (ex * fy - ey * fx) < 0.0
}

private fun nearlyEquals(points: DoubleArray, p0: Int, p1: Int): Boolean {
    val (p0x, p0y) = points.unpack(p0)
    val (p1x, p1y) = points.unpack(p1)
    return abs(p0x - p1x) <= EPSILON && abs(p0y - p1y) <= EPSILON
}

private fun nextHalfedge(edgeId: Int): Int {
    return if (edgeId % 3 == 2) {
        edgeId - 2
    } else {
        edgeId + 1
    }
}

private fun previousHalfedge(edgeId: Int): Int {
    return if (edgeId % 3 == 0) {
        edgeId + 2
    } else {
        edgeId - 1
    }
}

private class Triangulation(val triangles: IntArrayList, val halfedges: IntArrayList, val hull: IntArrayList) {

    constructor(n: Int) : this(
            triangles = IntArrayList((2 * n - 5) * 3),
            halfedges = IntArrayList((2 * n - 5) * 3),
            hull = IntArrayList(8))

    fun addTriangle(
            p0: Int,
            p1: Int,
            p2: Int,
            a: Int,
            b: Int,
            c: Int): Int {
        val triangleId = triangles.size()

        triangles.add(p0)
        triangles.add(p1)
        triangles.add(p2)

        halfedges.add(a)
        halfedges.add(b)
        halfedges.add(c)

        if (a != NONE) {
            halfedges.setQuick(a, triangleId)
        }
        if (b != NONE) {
            halfedges.setQuick(b, triangleId + 1)
        }
        if (c != NONE) {
            halfedges.setQuick(c, triangleId + 2)
        }

        return triangleId
    }

    fun legalize(a: Int, points: DoubleArray, hull: Hull): Int {
        val b = halfedges.getQuick(a)

        val ar = previousHalfedge(a)

        if (b == NONE) {
            return ar
        }

        val al = nextHalfedge(a)
        val bl = previousHalfedge(b)

        val p0 = triangles.getQuick(ar)
        val pr = triangles.getQuick(a)
        val pl = triangles.getQuick(al)
        val p1 = triangles.getQuick(bl)

        val illegal = inCircle(points, p0, pr, pl, p1)
        if (illegal) {
            triangles.setQuick(a, p1)
            triangles.setQuick(b, p0)

            val hbl = halfedges.getQuick(bl)
            val har = halfedges.getQuick(ar)

            if (hbl == NONE) {
                var e = hull.start
                while (true) {
                    if (hull.tri[e] == bl) {
                        hull.tri[e] = a
                        break
                    }
                    e = hull.next[e]
                    if (e == hull.start) {
                        break
                    }
                }
            }

            halfedges.setQuick(a, hbl)
            halfedges.setQuick(b, har)
            halfedges.setQuick(ar, bl)

            if (hbl != NONE) {
                halfedges.setQuick(hbl, a)
            }
            if (har != NONE) {
                halfedges.setQuick(har, b)
            }
            if (bl != NONE) {
                halfedges.setQuick(bl, ar)
            }

            val br = nextHalfedge(b)

            legalize(a, points, hull)
            return legalize(br, points, hull)
        }
        return ar
    }
}

private class Hull(val points: DoubleArray, val prev: IntArray, val next: IntArray, val tri: IntArray, val hash: IntArray, var start: Int, private var centerX: Double, private var centerY: Double) {

    constructor(n: Int, centerX: Double, centerY: Double, i0: Int, i1: Int, i2: Int, points: DoubleArray) : this(
            points = points,
            prev = IntArray(n),
            next = IntArray(n),
            tri = IntArray(n),
            hash = IntArray(sqrt(n.toDouble()).toInt()) { NONE },
            start = i0,
            centerX = centerX,
            centerY = centerY) {

        next[i0] = i1
        prev[i2] = i1
        next[i1] = i2
        prev[i0] = i2
        next[i2] = i0
        prev[i1] = i0

        tri[i0] = 0
        tri[i1] = 1
        tri[i2] = 2

        hashEdge(i0)
        hashEdge(i1)
        hashEdge(i2)
    }

    private fun hashKey(p: Int): Int {
        val (px, py) = points.unpack(p)

        val dx = px - centerX
        val dy = py - centerY

        val d = dx / (abs(dx) + abs(dy))
        val a = (if (dy > 0.0) 3.0 - d else 1.0 + d) / 4.0

        return floor(hash.size.toDouble() * a).roundToInt() % hash.size
    }

    fun hashEdge(p: Int) {
        hash[hashKey(p)] = p
    }

    fun findVisibleEdge(p: Int): Pair<Int, Boolean> {
        var start = 0
        val key = hashKey(p)
        val len = hash.size
        for (j in 0 until len) {
            start = hash[(key + j) % len]
            if (start != NONE && next[start] != NONE) {
                break
            }
        }
        start = prev[start]
        var e = start

        while (!orient(points, p, e, next[e])) {
            e = next[e]
            if (e == start) {
                return NONE to false
            }
        }
        return e to (e == start)
    }
}

private fun getCenterPoint(points: DoubleArray, stride: Int): Int {
    if (stride % 2 == 0) {
        val halfStride = stride / 2
        val halfStrideM1 = halfStride - 1
        val p1 = halfStrideM1 * stride + halfStrideM1
        val p2 = p1 + 1
        val p3 = halfStride * stride + halfStrideM1
        val p4 = p3 + 1
        val (p1x, p1y) = points.unpack(p1)
        val (p2x, p2y) = points.unpack(p2)
        val (p3x, p3y) = points.unpack(p3)
        val (p4x, p4y) = points.unpack(p4)
        val cx = (p1x + p2x + p3x + p4x) * 0.25
        val cy = (p1y + p2y + p3y + p4y) * 0.25
        val p1d = dist2(cx, cy, p1x, p1y)
        val p2d = dist2(cx, cy, p1x, p1y)
        val p3d = dist2(cx, cy, p1x, p1y)
        val p4d = dist2(cx, cy, p1x, p1y)
        var minDist = p1d
        var minId = p1
        if (p2d < minDist) {
            minDist = p2d
            minId = p2
        }
        if (p3d < minDist) {
            minDist = p3d
            minId = p3
        }
        if (p4d < minDist) {
            minId = p4
        }
        return minId
    } else {
        val halfStride = stride / 2
        return halfStride * stride + halfStride
    }
}

private fun findClosestPoint(points: DoubleArray, stride: Int, pointId: Int): Int {
    val (px, py) = points.unpack(pointId)
    val pointIndexX = pointId % stride
    val pointIndexY = pointId / stride
    var minDist = Double.POSITIVE_INFINITY
    var closestId = -1
    for (otherIndexY in (max(0, pointIndexY - 2) until min(stride, pointIndexY + 2))) {
        val yOff = otherIndexY * stride
        for (otherIndexX in (max(0, pointIndexX - 2) until min(stride, pointIndexX + 2))) {
            val otherId = yOff + otherIndexX
            val (ox, oy) = points.unpack(otherId)
            val dist2 = dist2(px, py, ox, oy)
            if (dist2 > 0.0 && dist2 < minDist) {
                closestId = otherId
                minDist = dist2
            }
        }
    }
    return closestId
}

private fun DoubleArray.unpack(id: Int): Point {
    val ai = id * 2
    return this[ai] to this[ai + 1]
}

private fun FloatArray.unpack(id: Int): Point {
    val ai = id * 2
    return this[ai].toDouble() to this[ai + 1].toDouble()
}

private val Point.x: Double get() = this.first
private val Point.y: Double get() = this.second

private fun findSeedTriangle(points: DoubleArray, stride: Int): Triangle {
    val p0 = getCenterPoint(points, stride)
    val p0IndexX = p0 % stride
    val p0IndexY = p0 / stride
    val p1 = findClosestPoint(points, stride, p0)
    val p1IndexX = p1 % stride
    val p1IndexY = p1 / stride
    val minIndexX = min(p0IndexX, p1IndexX)
    val maxIndexX = max(p0IndexX, p1IndexX)
    val minIndexY = min(p0IndexY, p1IndexY)
    val maxIndexY = max(p0IndexY, p1IndexY)

    var minRadius = Double.POSITIVE_INFINITY
    var p2 = 0
    for (otherIndexY in (max(0, minIndexY - 5) until min(stride, maxIndexY + 5))) {
        val yOff = otherIndexY * stride
        for (otherIndexX in (max(0, minIndexX - 5) until min(stride, maxIndexX + 5))) {
            val i = yOff + otherIndexX
            if (i == p0 || i == p1) {
                continue
            }
            val r = circumradius2(points, p0, p1, i)
            if (r < minRadius) {
                p2 = i
                minRadius = r
            }
        }
    }

    if (minRadius == Double.POSITIVE_INFINITY) {
        throw IllegalArgumentException("Triangulation must contain at least 3 points")
    } else {
        return if (orient(points, p0, p1, p2)) {
            Triangle(p0, p2, p1)
        } else {
            Triangle(p0, p1, p2)
        }
    }
}

private fun triangulate(points: DoubleArray, stride: Int): Triangulation {
    val numPoints = points.size / 2

    val (seedA, seedB, seedC) = findSeedTriangle(points, stride)
    val (centerX, centerY) = circumcenter(points, seedA, seedB, seedC)

    val triangulation = Triangulation(numPoints)
    triangulation.addTriangle(seedA, seedB, seedC, NONE, NONE, NONE)

    val sortedIndices = IntArrayList(IntArray(numPoints) { it })
    val dists = DoubleArray(numPoints) {
        val (px, py) = points.unpack(it)
        dist2(centerX, centerY, px, py)
    }
    sortedIndices.quickSortFromTo(0, numPoints - 1) { a: Int, b: Int ->
        dists[a].compareTo(dists[b])
    }
    val hull = Hull(numPoints, centerX, centerY, seedA, seedB, seedC, points)

    for (k in 0 until sortedIndices.size()) {
        val p = sortedIndices.getQuick(k)

        if (k > 0 && nearlyEquals(points, p, sortedIndices[k - 1])) {
            continue
        }

        if (p == seedA || p == seedB || p == seedC) {
            continue
        }

        var (e, walkBack) = hull.findVisibleEdge(p)
        if (e == NONE) {
            continue
        }

        val firstTriangle = triangulation.addTriangle(e, p, hull.next[e], NONE, NONE, hull.tri[e])

        hull.tri[p] = triangulation.legalize(firstTriangle + 2, points, hull)
        hull.tri[e] = firstTriangle

        var next = hull.next[e]
        while (true) {
            val q = hull.next[next]
            if (!orient(points, p, next, q)) {
                break
            }
            val t = triangulation.addTriangle(next, p, q, hull.tri[p], NONE, hull.tri[next])
            hull.tri[p] = triangulation.legalize(t + 2, points, hull)
            hull.next[next] = NONE
            next = q
        }

        if (walkBack) {
            while (true) {
                val q = hull.prev[e]
                if (!orient(points, p, q, e)) {
                    break
                }
                val t = triangulation.addTriangle(q, p, e, NONE, hull.tri[e], hull.tri[q])
                triangulation.legalize(t + 2, points, hull)
                hull.tri[q] = t
                hull.next[e] = NONE
                e = q
            }
        }

        hull.prev[p] = e
        hull.next[p] = next
        hull.prev[next] = p
        hull.next[e] = p
        hull.start = e

        hull.hashEdge(p)
        hull.hashEdge(e)
    }

    var e = hull.start
    while (true) {
        triangulation.hull.add(e)
        e = hull.next[e]
        if (e == hull.start) {
            break
        }
    }

    triangulation.halfedges.trimToSize()
    triangulation.triangles.trimToSize()

    return triangulation
}
