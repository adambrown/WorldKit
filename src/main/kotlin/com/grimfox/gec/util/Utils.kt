package com.grimfox.gec.util

import com.grimfox.gec.Main
import com.grimfox.gec.model.Point
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Graph.CellEdge
import com.grimfox.gec.model.Matrix
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*

object Utils {

    val LOG = LoggerFactory.getLogger(Main::class.java)

    fun Int.pow(exponent: Int): Int {
        val value = BigInteger.valueOf(this.toLong()).pow(exponent)
        if (value > BigInteger.valueOf(Int.MAX_VALUE.toLong())) throw IllegalStateException("Value too large for int: $value")
        return value.toInt()
    }

    fun Long.pow(exponent: Int): Long {
        val value = BigInteger.valueOf(this).pow(exponent)
        if (value > BigInteger.valueOf(Long.MAX_VALUE)) throw IllegalStateException("Value too large for long: $value")
        return value.toLong()
    }

    fun BigInteger.pow(exponent: Int): BigInteger {
        var value = BigInteger.valueOf(1)
        for (i in 1..exponent) {
            value *= this
        }
        return value
    }

    fun ByteBuffer.disposeDirect() {
        if (!isDirect) return
        var logFail = true
        if (LOG.isDebugEnabled) {
            LOG.debug("Disposing of direct buffer.")
        }
        try {
            val cleaner = javaClass.getMethod("cleaner") ?: return
            cleaner.isAccessible = true
            val clean = Class.forName("sun.misc.Cleaner").getMethod("clean") ?: return
            clean.isAccessible = true
            clean.invoke(cleaner.invoke(this))
            logFail = false
        } catch(e: Exception) {
            if (LOG.isDebugEnabled) {
                LOG.debug("Failed to dispose of direct buffer.", e)
            }
            logFail = false
        } finally {
            if (logFail) {
                if (LOG.isDebugEnabled) {
                    LOG.debug("Failed to dispose of direct buffer.")
                }
            } else {
                if (LOG.isDebugEnabled) {
                    LOG.debug("Disposed of direct buffer.")
                }
            }
        }
    }

    fun FileChannel.mapChunks(mode: FileChannel.MapMode, offset: Int, chunkSize: Int, chunkCount: Int): MutableList<ByteBuffer> {
        val chunks = ArrayList<ByteBuffer>()
        for (i in 0..(chunkCount - 1)) {
            chunks.add(map(mode, offset + (i * chunkSize.toLong()), chunkSize.toLong()).order(ByteOrder.LITTLE_ENDIAN))
        }
        return chunks
    }

    fun FileChannel.imageSizeAsExp2(bytesPerPixel: Int, metaBytes: Int): Int {
        return exp2FromSize(Math.sqrt((size() - metaBytes) / bytesPerPixel.toDouble()).toInt())
    }

    fun exp2FromSize(size: Int): Int {
        var count = 0
        var currentDiv = size
        while (currentDiv > 1) {
            currentDiv /= 2
            count++
        }
        return count
    }

    fun FileChannel.MapMode.toRandomAccessFileMode(): String {
        return when (this) {
            FileChannel.MapMode.READ_ONLY -> "r"
            else -> "rw"
        }
    }

    private val primitivesToWrappers = mapOf<Class<*>, Class<*>>(
            Pair(Boolean::class.java, java.lang.Boolean::class.java),
            Pair(Byte::class.java, java.lang.Byte::class.java),
            Pair(Char::class.java, java.lang.Character::class.java),
            Pair(Double::class.java, java.lang.Double::class.java),
            Pair(Float::class.java, java.lang.Float::class.java),
            Pair(Int::class.java, java.lang.Integer::class.java),
            Pair(Long::class.java, java.lang.Long::class.java),
            Pair(Short::class.java, java.lang.Short::class.java))

    fun primitiveToWrapper(type: Class<*>): Class<*> {
        if (type.isPrimitive) {
            val possible = primitivesToWrappers[type]
            if (possible != null) {
                return possible
            }
        }
        return type
    }

    fun FileChannel.writeInt(position: Long, value: Int) {
        val buffer = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, value)
        write(buffer, position)
    }

    fun FileChannel.readInt(position: Long): Int {
        val buffer = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)
        read(buffer, position)
        return buffer.getInt(0)
    }

    fun ByteBuffer.readUint24(position: Int): Int {
        return (get(position).toInt() and 0xFF) + ((get(position + 1).toInt() and 0xFF) shl 8) + ((get(position + 2).toInt() and 0xFF) shl 16)
    }

    fun ByteBuffer.writeUint24(position: Int, value: Int) {
        put(position, (value and 0xFF).toByte())
        put(position + 1, ((value ushr 8) and 0xFF).toByte())
        put(position + 2, ((value ushr 16) and 0xFF).toByte())
    }

    fun findClosestPoint(points: Matrix<Point>, x: Int, y: Int, gridStride: Int, pointWrapOffset: Float, point: Point, outputWidth: Int, wrapEdges: Boolean): Int {
        return findClosestPoints(points, x, y, gridStride, pointWrapOffset, point, outputWidth, wrapEdges, 1)[0].first
    }

    fun findClosestPoints(points: Matrix<Point>, x: Int, y: Int, gridStride: Int, pointWrapOffset: Float, point: Point, outputWidth: Int, wrapEdges: Boolean): ClosestPoints {
        val closestPoints = findClosestPoints(points, x, y, gridStride, pointWrapOffset, point, outputWidth, wrapEdges, 3)
        return ClosestPoints(closestPoints[0],
                closestPoints[1],
                closestPoints[2],
                closestPoints[3],
                closestPoints[4])
    }

    private fun findClosestPoints(points: Matrix<Point>, x: Int, y: Int, gridStride: Int, pointWrapOffset: Float, point: Point, outputWidth: Int, wrapEdges: Boolean, ringCount: Int): ArrayList<Pair<Int, Float>> {
        val ringWidth = ringCount * 2 + 1
        val closestPoints = ArrayList<Pair<Int, Float>>(ringWidth * ringWidth)
        for (yOff in -ringCount..ringCount) {
            for (xOff in -ringCount..ringCount) {
                var ox = x + xOff
                var oy = y + yOff
                var xDistAdjust = 0.0f
                var yDistAdjust = 0.0f
                if (wrapEdges) {
                    val ox1 = ox
                    ox = (ox + gridStride) % gridStride
                    if (ox1 > ox) {
                        xDistAdjust = pointWrapOffset
                    } else if (ox1 < ox) {
                        xDistAdjust = -pointWrapOffset
                    }
                    val oy1 = oy
                    oy = (oy + gridStride) % gridStride
                    if (oy1 > oy) {
                        yDistAdjust = pointWrapOffset
                    } else if (oy1 < oy) {
                        yDistAdjust = -pointWrapOffset
                    }
                }
                if (oy >= 0 && oy < gridStride && ox >= 0 && ox < gridStride) {
                    val index = oy * gridStride + ox
                    val other = points[ox, oy]
                    val distance = point.distanceSquaredTo(Point(other.x * outputWidth + xDistAdjust, other.y * outputWidth + yDistAdjust))
                    closestPoints.add(Pair(index, distance))
                }
            }
        }
        closestPoints.sort { p1, p2 ->
            p1.second.compareTo(p2.second)
        }
        return closestPoints
    }

    fun buildEdgeMap(closestPoints: Matrix<ClosestPoints>): HashMap<Int, MutableSet<Int>> {
        val edges = HashMap<Int, MutableSet<Int>>()
        val end = closestPoints.width - 1
        for (y in 0..end) {
            for (x in 0..end) {
                val points = closestPoints[x, y]
                val p0 = points.p0?.first
                val p1 = points.p1?.first
                if (p0 != null && p1 != null) {
                    val p0Cons = edges.getOrPut(p0, { HashSet() })
                    p0Cons.add(p1)
                    val p1Cons = edges.getOrPut(p1, { HashSet() })
                    p1Cons.add(p0)
                }
            }
        }
        return edges
    }

    fun buildEdgeGraph(edges: HashMap<Int, MutableSet<Int>>, pointCount: Int): ArrayList<ArrayList<Int>> {
        val edgeGraph = ArrayList<ArrayList<Int>>(pointCount)
        for (i in 0..pointCount - 1) {
            edgeGraph.add(i, ArrayList(edges[i]!!.toList().sorted()))
        }
        return edgeGraph
    }

    fun generatePoints(stride: Int, width: Float, random: Random): ArrayList<Point> {
        val gridSquare = width / stride
        val minDistSquared = (gridSquare / 3.0f) * (gridSquare / 3.0f)
        val points = ArrayList<Point>()
        for (y in 0..stride - 1) {
            val oy = y * gridSquare
            for (x in 0..stride - 1) {
                while (true) {
                    val px = x * gridSquare + random.nextFloat() * gridSquare
                    val py = oy + random.nextFloat() * gridSquare
                    val point = Point(px, py)
                    if (checkDistances(points, x, y, stride, minDistSquared, point)) {
                        points.add(point)
                        break
                    }
                }
            }
        }
        return points
    }

    private fun checkDistances(points: List<Point>, x: Int, y: Int, stride: Int, minDistance: Float, point: Point): Boolean {
        for (yOff in -3..3) {
            for (xOff in -3..3) {
                val ox = x + xOff
                val oy = y + yOff
                if (oy >= 0 && oy < stride && ox >= 0 && ox < stride) {
                    if (oy < y || (oy == y && ox < x)) {
                        if (point.distanceSquaredTo(points[oy * stride + ox]) < minDistance) {
                            return false
                        }
                    } else {
                        return true
                    }
                }
            }
        }
        return true
    }

    fun <T, ML : MutableList<T>> ML.init(size: Int, init: (Int) -> T): ML {
        this.clear()
        for (i in 0..size - 1) {
            this.add(init(i))
        }
        return this
    }

    fun edgesIntersect(edge1: CellEdge, edge2: CellEdge): Boolean {
        return linesIntersect(Pair(edge1.tri1.center, edge1.tri2.center), Pair(edge2.tri1.center, edge2.tri2.center))
    }

    fun linesIntersect(line1: Pair<Point, Point>, line2: Pair<Point, Point>): Boolean {
        val o1 = orientation(line1.first, line1.second, line2.first)
        val o2 = orientation(line1.first, line1.second, line2.second)
        val o3 = orientation(line2.first, line2.second, line1.first)
        val o4 = orientation(line2.first, line2.second, line1.second)
        if (o1 != o2 && o3 != o4) {
            return true
        }
        return (o1 == 0 && (onSegment(line1.first, line2.first, line1.second)
                || onSegment(line1.first, line2.second, line1.second)
                || onSegment(line2.first, line1.first, line2.second)
                || onSegment(line2.first, line1.second, line2.second)))
    }

    private fun orientation(p1: Point, p2: Point, p3: Point): Int {
        val det = (p2.y - p1.y) * (p3.x - p2.x) - (p2.x - p1.x) * (p3.y - p2.y)
        if (det == 0.0f) return 0
        return if (det > 0.0f) 1 else -1
    }

    private fun onSegment(p1: Point, p2: Point, p3: Point): Boolean {
        return (p2.x <= Math.max(p1.x, p3.x)
                && p2.x >= Math.min(p1.x, p3.x)
                && p2.y <= Math.max(p1.y, p3.y)
                && p2.y >= Math.min(p1.y, p3.y))
    }

    private fun pointInBetween(p0_x: Float, p0_y: Float, p1_x: Float, p1_y: Float, p2_x: Float, p2_y: Float): Boolean {
        return (p1_x <= Math.max(p0_x, p2_x)
                && p1_x >= Math.min(p0_x, p2_x)
                && p1_y <= Math.max(p0_y, p2_y)
                && p1_y >= Math.min(p0_y, p2_y))
    }

    fun getLineIntersection(line1: Pair<Point, Point>, line2: Pair<Point, Point>): Point? {
        return getLineIntersection(line1.first.x, line1.first.y, line1.second.x, line1.second.y, line2.first.x, line2.first.y, line2.second.x, line2.second.y)
    }

    fun getLineIntersection(p0_x: Float, p0_y: Float, p1_x: Float, p1_y: Float, p2_x: Float, p2_y: Float, p3_x: Float, p3_y: Float): Point? {
        val s1_x = p1_x - p0_x
        val s1_y = p1_y - p0_y
        val s2_x = p3_x - p2_x
        val s2_y = p3_y - p2_y
        val denominator = -s2_x * s1_y + s1_x * s2_y
        if (denominator == 0.0f) {
            if (pointInBetween(p0_x, p0_y, p2_x, p2_y, p1_x, p1_y)) {
                return Point(p2_x, p2_y)
            }
            if (pointInBetween(p0_x, p0_y, p3_x, p3_y, p1_x, p1_y)) {
                return Point(p3_x, p3_y)
            }
            if (pointInBetween(p2_x, p2_y, p0_x, p0_y, p3_x, p3_y)) {
                return Point(p0_x, p0_y)
            }
            if (pointInBetween(p2_x, p2_y, p1_x, p1_y, p3_x, p3_y)) {
                return Point(p1_x, p1_y)
            }
            return null
        }
        val tNumerator = (s2_x * (p0_y - p2_y) - s2_y * (p0_x - p2_x))
//        if ((tNumerator > denominator) || (tNumerator < 0.0f && denominator > 0.0f)) {
//            return null
//        }
        val sNumerator = (-s1_y * (p0_x - p2_x) + s1_x * (p0_y - p2_y))
//        if ((sNumerator > denominator) || (sNumerator < 0.0f && denominator > 0.0f)) {
//            return null
//        }
        val s = sNumerator / denominator
        val t = tNumerator / denominator
        if (s >= 0 && s <= 1 && t >= 0 && t <= 1) {
            return Point(p0_x + (t * s1_x), p0_y + (t * s1_y))
        }
        return null
    }

    fun findConcavityWeights(graph: Graph, body: HashSet<Int>, vertexIds: HashSet<Int>, vararg radii: Float): ArrayList<Pair<Int, Float>> {
        return findConcavityWeights(graph, body, vertexIds, null, *radii)
    }

    fun findConcavityWeights(graph: Graph, body: HashSet<Int>, vertexIds: HashSet<Int>, mask: HashSet<Int>?, vararg radii: Float): ArrayList<Pair<Int, Float>> {
        val weights = ArrayList<Pair<Int, Float>>(vertexIds.size)
        vertexIds.forEach { vertexId ->
            weights.add(Pair(vertexId, findConcavityWeight(graph, body, vertexId, mask, *radii)))
        }
        return weights
    }

    fun findConcavityWeight(graph: Graph, body: HashSet<Int>, vertexId: Int, mask: HashSet<Int>?, vararg radii: Float): Float {
        var landWaterRatio = 1.0f
        radii.forEach { radius ->
            landWaterRatio *= calculateConcavityRatio(graph, body, vertexId, mask, radius)
        }
        return landWaterRatio
    }

    private fun calculateConcavityRatio(graph: Graph, body: HashSet<Int>, vertexId: Int, mask: HashSet<Int>?, radius: Float): Float {
        val vertices = graph.vertices
        val vertex = vertices[vertexId]
        val point = vertex.point
        val closeVertices = graph.getPointsWithinRadius(point, radius).filter { !(mask?.contains(it) ?: false) }
        var inCount = 0
        closeVertices.forEach {
            if (body.contains(it)) {
                inCount++
            }
        }
        return inCount.toFloat() / closeVertices.size
    }

    fun Pair<Point, Point>.isPointWithin(point: Point): Boolean {
        return point.x >= first.x && point.x <= second.x && point.y >= first.y && point.y <= second.y
    }

    fun Pair<Point, Point>.doesEdgeIntersect(edge: Pair<Point, Point>): Boolean {
        return isPointWithin(edge.first) || isPointWithin(edge.second)
    }

    fun distance2Between(line: Pair<Point, Point>, point: Point): Float {
        val x = point.x
        val y = point.y
        val x1 = line.first.x
        val y1 = line.first.y
        val x2 = line.second.x
        val y2 = line.second.y
        
        val a = x - x1
        val b = y - y1
        val c = x2 - x1
        val d = y2 - y1

        val dot = a * c + b * d
        val len_sq = c * c + d * d
        var param = -1.0f
        if (len_sq !== 0.0f) {
            param = dot / len_sq
        }

        val xx: Float
        val yy: Float
        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * c
            yy = y1 + param * d
        }

        val dx = x - xx
        val dy = y - yy
        return dx * dx + dy * dy 
    }
    
    fun distance2Between(line1: Pair<Point, Point>, line2: Pair<Point, Point>): Float {
        if (linesIntersect(line1, line2)) {
            return 0.0f
        }
        var minDist = distance2Between(line1, line2.first)
        minDist = Math.min(minDist, distance2Between(line1, line2.second))
        minDist = Math.min(minDist, distance2Between(line2, line1.first))
        return Math.min(minDist, distance2Between(line2, line1.second))
    }

    fun midPoint(p1: Point, p2: Point): Point {
        return Point((p1.x + p2.x) * 0.5f, (p1.y + p2.y) * 0.5f)
    }
}