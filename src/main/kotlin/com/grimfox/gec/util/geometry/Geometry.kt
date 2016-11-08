package com.grimfox.gec.util.geometry

import com.grimfox.gec.model.ArrayListMatrix
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.drawing.*
import com.grimfox.gec.util.geometry.Geometry.debug
import com.grimfox.gec.util.geometry.Geometry.debugFinal
import com.grimfox.gec.util.geometry.Geometry.debugCount
import com.grimfox.gec.util.geometry.Geometry.debugIteration
import com.grimfox.gec.util.geometry.Geometry.debugResolution
import com.grimfox.gec.util.geometry.Geometry.trace
import com.grimfox.gec.util.printList
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Math.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

class GeometryException(message: String? = null, cause: Throwable? = null, var test: Int? = null, var id: Int? = null, val data: ArrayList<String> = ArrayList<String>()): Exception(message, cause) {

    fun with(adjustment: GeometryException.() -> Unit): GeometryException {
        this.adjustment()
        return this
    }
}

fun breakPoint() {
    doNothing()
}

private fun doNothing() {}

private const val DEG_TO_RAD = PI / 180.0
private const val COLLINEAR_THRESHOLD_DEG = 4.0
private const val EXTREME_COLLINEAR_THRESHOLD_DEG = 0.5
private const val COLLINEAR_THRESHOLD = DEG_TO_RAD * COLLINEAR_THRESHOLD_DEG
private const val EXTREME_COLLINEAR_THRESHOLD = DEG_TO_RAD * EXTREME_COLLINEAR_THRESHOLD_DEG
private const val DOUBLE_COLLINEAR_THRESHOLD = COLLINEAR_THRESHOLD * 2
private const val COLLINEAR_ANGLE = PI - COLLINEAR_THRESHOLD
private const val EXTREME_COLLINEAR_ANGLE = PI - EXTREME_COLLINEAR_THRESHOLD
private const val COLLINEAR_HALF_ANGLE = PI - (COLLINEAR_THRESHOLD * 0.5)
private const val DOUBLE_COLLINEAR_ANGLE = PI - DOUBLE_COLLINEAR_THRESHOLD

object Geometry {

    var debug = false
    var debugFinal = false
    var trace = false
    var debugCount = AtomicInteger(1)
    var debugIteration = AtomicInteger(1)
    var debugResolution = 4096
}

private class CollinearPatch(val start: Point2F, val end: Point2F, val points: ArrayList<Point2F>)

fun triangulatePolygon(vertices: ArrayList<Point3F>, polygon: ArrayList<Pair<Int, Int>>): LinkedHashSet<Set<Int>> {
    var points = ArrayList<Point2F>(polygon.map { vertices[it.first] })
    if (areaOfPolygon(points) < 0) {
        points.reverse()
    }
    var collinearPatches = findCollinearPatches(points)
    collinearPatches.forEach {
        if (debug) {
            draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                graphics.color = Color.BLACK
                for (i in 1..points.size) {
                    val a = points[i - 1]
                    val b = points[i % points.size]
                    drawEdge(a, b)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                }
                graphics.color = Color.RED
                drawPoint(it.start, 4)
                drawPoint(it.end, 4)
                graphics.color = Color.GREEN
                it.points.forEach {
                    drawPoint(it, 2)
                }
            }
            breakPoint()
        }
        points.removeAll(it.points)
    }
    var reducedPoints = ArrayList(points)
    if (reducedPoints.size < 3) {
        points = ArrayList(polygon.map { vertices[it.first] })
        reducedPoints = points
        collinearPatches = ArrayList()
    }
    val newEdges = ArrayList<LineSegment2F>()
    while (points.size > 3) {
        val (ai, bi, ci) = findNextEar(points)
        try {
            newEdges.add(LineSegment2F(points[ai], points[ci]))
            if (debug) {
                draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                    graphics.color = Color.BLACK
                    for (i in 1..points.size) {
                        val a = points[i - 1]
                        val b = points[i % points.size]
                        drawEdge(a, b)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                    }
                    graphics.color = Color.RED
                    drawEdge(points[ai], points[ci])
                    drawPoint(points[ai], 4)
                    drawPoint(points[ci], 4)
                    graphics.color = Color.GREEN
                    points.forEach {
                        drawPoint(it, 2)
                    }
                }
                breakPoint()
            }
            points.removeAt(bi)
        } catch (g: GeometryException) {
            throw g
        } catch (e: Exception) {
            if (debug) {
                draw(debugResolution, "debug-triangulatePolygon2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                    graphics.color = Color.BLACK
                    newEdges.forEach {
                        val a = it.a
                        val b = it.b
                        drawEdge(a, b)
                        drawPoint(a, 4)
                        drawPoint(b, 4)
                    }
                    graphics.color = Color.RED
                    polygon.forEach {
                        val a = vertices[it.first]
                        val b = vertices[it.second]
                        drawEdge(a, b)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                    }
                    graphics.color = Color.GREEN
                    points.forEach {
                        drawPoint(it, 2)
                    }
                }
                breakPoint()
            }
            throw GeometryException("unable to triangulate", e).with {
                data.add("val test = {")
                data.add("val vertices = ${printList(vertices)}")
                data.add("val polygon = ${printList(polygon) { "Pair$it" }}")
                data.add("triangulatePolygon(vertices, polygon)")
                data.add("}")
            }
        }
    }
    val pointSet = PointSet2F(vertices)
    var reducedPolygon = polygonFromPoints(pointSet, reducedPoints)
    var meshMinusPatches = buildMesh(reducedPolygon + newEdges.map { Pair(pointSet[it.a], pointSet[it.b]) }, vertices.size)
    while (collinearPatches.isNotEmpty()) {
        val patch = collinearPatches.first()
        collinearPatches.remove(patch)
        val sid = pointSet[patch.start]
        val eid = pointSet[patch.end]
        val edge = listOf(sid, eid)
        for (tri in ArrayList(meshMinusPatches)) {
            if (tri.containsAll(edge)) {
                meshMinusPatches.remove(tri)
                val convergence = LinkedHashSet(tri)
                convergence.removeAll(edge)
                val focus = vertices[convergence.first()]
                patch.points.forEach {
                    newEdges.add(LineSegment2F(it, focus))
                }
                break
            }
        }
        addPatchPoints(reducedPoints, patch)
        reducedPolygon = polygonFromPoints(pointSet, reducedPoints)
        meshMinusPatches = buildMesh(reducedPolygon + newEdges.map { Pair(pointSet[it.a], pointSet[it.b]) }, vertices.size)
    }
    val triangles = buildMesh(polygon + newEdges.map { Pair(pointSet[it.a], pointSet[it.b]) }, vertices.size)
    if (debug) {
        draw(debugResolution, "debug-triangulatePolygon3-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
            graphics.color = Color.BLACK
            triangles.forEach {
                val tri = it.toList()
                val a = vertices[tri[0]]
                val b = vertices[tri[1]]
                val c = vertices[tri[2]]
                drawEdge(a, b)
                drawEdge(b, c)
                drawEdge(c, a)
                drawPoint(a, 3)
                drawPoint(b, 3)
                drawPoint(c, 3)
            }
        }
        breakPoint()
    }
    val flipped = flipEdges(vertices, triangles, polygon)
    if (debug) {
        draw(debugResolution, "debug-triangulatePolygon4-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
            graphics.color = Color.BLACK
            flipped.forEach {
                val tri = it.toList()
                val a = vertices[tri[0]]
                val b = vertices[tri[1]]
                val c = vertices[tri[2]]
                drawEdge(a, b)
                drawEdge(b, c)
                drawEdge(c, a)
                drawPoint(a, 3)
                drawPoint(b, 3)
                drawPoint(c, 3)
            }
        }
        breakPoint()
    }
    return flipped
}

private fun addPatchPoints(points: ArrayList<Point2F>, patch: CollinearPatch) {
    val insertionPoint = points.indexOf(patch.start) + 1
    patch.points.reversed().forEach {
        points.add(insertionPoint, it)
    }
}

private fun polygonFromPoints(vertices: PointSet2F, points: ArrayList<Point2F>): ArrayList<Pair<Int, Int>> {
    val edges = ArrayList<Pair<Int, Int>>()
    for (i in 1..points.size) {
        val a = vertices[points[i - 1]]
        val b = vertices[points[i % points.size]]
        edges.add(Pair(a, b))
    }
    return edges
}

private fun findNextEar(points: ArrayList<Point2F>, strict: Boolean = false): Triple<Int, Int, Int> {
    var index1 = -1
    var index2 = -1
    var index3 = -1
    var angle = -0.1
    for (i in 1..points.size) {
        val ai = i - 1
        val bi = i % points.size
        val ci = (i + 1) % points.size
        val a = points[ai]
        val b = points[bi]
        val c = points[ci]
        if (debug && trace) {
            draw(debugResolution, "debug-triangulatePolygon4-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, points) {
                graphics.color = Color.BLACK
                for (j in 1..points.size) {
                    val k = points[j - 1]
                    val m = points[j % points.size]
                    drawEdge(k, m)
                    drawPoint(k, 3)
                    drawPoint(m, 3)
                }
                graphics.color = Color.RED
                drawEdge(a, b)
                drawEdge(b, c)
                drawPoint(a, 4)
                drawPoint(b, 4)
                drawPoint(c, 4)
            }
            breakPoint()
        }
        val normal = (a - b).cross(c - b)
        if (normal >= 0.0f) {
            continue
        }
        if (anyPointWithin(points, ai, bi, ci)) {
            continue
        }
        val newWeight = PI - angle(points, ai, bi, ci)
        if (newWeight > angle) {
            angle = newWeight
            index1 = ai
            index2 = bi
            index3 = ci
        }
    }
    if (index1 == -1 && !strict) {
        return findNextEar(points, true)
    }
    return Triple(index1, index2, index3)
}

private fun findCollinearPatches(points: ArrayList<Point2F>): ArrayList<CollinearPatch> {
    val collinearIds = LinkedHashSet<Int>()
    var patchSum = 0.0
    for (i in 1..points.size) {
        val ai = i - 1
        val bi = i % points.size
        val ci = (i + 1) % points.size
        if (debug) {
            draw(debugResolution, "debug-findCollinearPatches-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, points) {
                graphics.color = Color.BLACK
                for (p in 1..points.size) {
                    val a = points[p - 1]
                    val b = points[p % points.size]
                    drawEdge(a, b)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                }
                graphics.color = Color.RED
                drawEdge(points[bi], points[ai])
                drawEdge(points[bi], points[ci])
                drawPoint(points[ai], 4)
                drawPoint(points[bi], 4)
                drawPoint(points[ci], 4)
                graphics.color = Color.GREEN
                points.forEach {
                    drawPoint(it, 2)
                }
            }
            breakPoint()
        }
        val angle = angle(points, ai, bi, ci)
        val piDiff = PI - angle
        if (angle > COLLINEAR_ANGLE && patchSum + piDiff < DOUBLE_COLLINEAR_THRESHOLD) {
            collinearIds.add(bi)
            patchSum += PI - angle
        } else {
            patchSum = 0.0
        }
    }
    val collinearPatches = ArrayList<CollinearPatch>()
    while (collinearIds.isNotEmpty()) {
        collinearPatches.add(buildCollinearPatch(points, collinearIds, collinearIds.first()))
    }
    return collinearPatches
}

private fun buildCollinearPatch(points: ArrayList<Point2F>, collinearIds: LinkedHashSet<Int>, seed: Int): CollinearPatch {
    val collinearPoints = ArrayList<Point2F>()
    collinearIds.remove(seed)
    collinearPoints.add(points[seed])
    var next = (seed + 1) % points.size
    while (collinearIds.contains(next)) {
        collinearIds.remove(next)
        collinearPoints.add(points[next])
        next = (next + 1) % points.size
    }
    val end = points[next]
    next = (seed - 1 + points.size) % points.size
    while (collinearIds.contains(next)) {
        collinearIds.remove(next)
        collinearPoints.add(0, points[next])
        next = (next - 1 + points.size) % points.size
    }
    val start = points[next]
    return (CollinearPatch(start, end, collinearPoints))
}

fun isOnBorder(edgeSkeleton: ArrayList<LineSegment3F>, point: Point2F): Boolean {
    for (edge in edgeSkeleton) {
        if (pointIsOnLine(LineSegment2F(edge.a, edge.b), point)) {
            return true
        }
    }
    return false
}

private fun pointIsOnLine(edge: LineSegment2F, point: Point2F, strict: Boolean = false, distance2: Float = if (strict) 0.000000005f else 0.0000005f, angle: Double = if (strict) EXTREME_COLLINEAR_ANGLE else COLLINEAR_ANGLE): Boolean {
    return edge.distance2(point) < distance2 && angle(edge.a, point, edge.b) > angle
}

private fun anyPointWithin(points: ArrayList<Point2F>, ai: Int, bi: Int, ci: Int, strict: Boolean = false): Boolean {
    val a = points[ai]
    val b = points[bi]
    val c = points[ci]

    val area = 1.0 / (-b.y * c.x + a.y * (-b.x + c.x) + a.x * (b.y - c.y) + b.x * c.y)

    val s1 = a.y*c.x - a.x*c.y
    val sx = c.y - a.y
    val sy = a.x - c.x

    val t1 = a.x * b.y - a.y*b.x
    val tx = a.y - b.y
    val ty = b.x - a.x

    val ac = LineSegment2F(a, c)

    for (i in 0..points.size - 1) {
        if (i == ai || i == bi || i == ci) {
            continue
        }
        val p = points[i]
        val s = area * (s1 + sx * p.x + sy * p.y)
        val t = area * (t1 + tx * p.x + ty * p.y)
        if (s > 0 && t > 0 && 1.0 - s - t > 0) {
            return true
        }
        if (debug && trace) {
            draw(debugResolution, "debug-anyPointWithin-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, points) {
                graphics.color = Color.BLACK
                for (pi in 1..points.size) {
                    val p1 = points[pi - 1]
                    val p2 = points[pi % points.size]
                    drawEdge(p1, p2)
                    drawPoint(p1, 7)
                    drawPoint(p2, 7)
                }
                graphics.color = Color.RED
                drawEdge(points[bi], points[ai])
                drawEdge(points[bi], points[ci])
                drawEdge(points[ci], points[ai])
                drawPoint(points[ai], 5)
                graphics.color = Color.GREEN
                drawPoint(points[bi], 5)
                graphics.color = Color.CYAN
                drawPoint(points[ci], 5)
                graphics.color = Color.MAGENTA
                drawPoint(p, 5)
                graphics.color = Color.BLUE
                drawEdge(points[ai], points[ci])
            }
            breakPoint()
        }
        if (pointIsOnLine(ac, p, strict)) {
            return true
        }
    }
    return false
}

private fun angle(points: List<Point2F>, ai: Int, bi: Int, ci: Int): Double {
    return angle(points[ai], points[bi], points[ci])
}

private fun angle(a: Point2F, b: Point2F, c: Point2F): Double {
    val ax = a.x.toDouble()
    val ay = a.y.toDouble()
    val bx = b.x.toDouble()
    val by = b.y.toDouble()
    val cx = c.x.toDouble()
    val cy = c.y.toDouble()
    val ba = distance(ax, ay, bx, by)
    val bc = distance(cx, cy, bx, by)
    val ac = distance(cx, cy, ax, ay)
    return acos(max(-1.0, min(1.0, (((ba * ba) + (bc * bc) - (ac * ac)) / (2 * ba * bc)))))
}

private fun distance(ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = ax - bx
    val dy = ay - by
    return sqrt(dx * dx + dy * dy)
}

private fun areaOfPolygon(points: ArrayList<Point2F>): Float {
    var sum1 = 0.0f
    var sum2 = 0.0f
    for (i in 1..points.size) {
        val p1 = points[i - 1]
        val p2 = points[i % points.size]
        sum1 += p1.x * p2.y
        sum2 += p1.y * p2.x
    }
    return (sum1 - sum2) / 2
}

private fun buildMesh(edges: Collection<Pair<Int, Int>>, vertexCount: Int): LinkedHashSet<Set<Int>> {
    val vertexToVertexMap = buildVertexAdjacencyMap(edges, vertexCount)
    val triangleIndices = LinkedHashSet<Set<Int>>()
    for (a in 0..vertexCount - 1) {
        val adjacents = vertexToVertexMap[a]
        for (p in 0..adjacents.size - 2) {
            val b = adjacents[p]
            if (b != a) {
                val secondAdjacents = vertexToVertexMap[b]
                for (q in p + 1..adjacents.size - 1) {
                    val c = adjacents[q]
                    if (c != a && c != b && secondAdjacents.contains(c)) {
                        triangleIndices.add(setOf(a, b, c))
                    }
                }
            }
        }
    }
    return triangleIndices
}

private fun buildVertexAdjacencyMap(edges: Collection<Pair<Int, Int>>, vertexCount: Int): ArrayList<ArrayList<Int>> {
    val vertexToVertexMap = ArrayList<ArrayList<Int>>()
    for (v in 0..vertexCount - 1) {
        vertexToVertexMap.add(ArrayList(5))
    }
    edges.forEach { edge ->
        vertexToVertexMap[edge.first].add(edge.second)
        vertexToVertexMap[edge.second].add(edge.first)
    }
    return vertexToVertexMap
}

private class EdgeNode(val index: Int, var p1: Int, var p2: Int, var t1: TriNode, var t2: TriNode)

private class TriNode(var p1: Int, var p2: Int, var p3: Int, val edges: ArrayList<EdgeNode> = ArrayList())

private fun flipEdges(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, polygonIndices: List<Pair<Int, Int>>, modifiedEdges: LinkedHashSet<Set<Int>>? = null): LinkedHashSet<Set<Int>> {
    val polygon = Polygon2F.fromUnsortedEdges(polygonIndices.map { LineSegment2F(vertices[it.first], vertices[it.second]) })
    val edgeNodes = ArrayList<EdgeNode>()
    val triNodes = ArrayList<TriNode>()
    val edgeMap = LinkedHashMap<Set<Int>, ArrayList<TriNode>>()
    triangles.forEach {
        val tri = it.toList()
        val a = tri[0]
        val b = tri[1]
        val c = tri[2]
        val ab = setOf(a, b)
        val bc = setOf(b, c)
        val ca = setOf(c, a)
        val triNode = TriNode(a, b, c)
        edgeMap.getOrPut(ab, { ArrayList() }).add(triNode)
        edgeMap.getOrPut(bc, { ArrayList() }).add(triNode)
        edgeMap.getOrPut(ca, { ArrayList() }).add(triNode)
        triNodes.add(triNode)
    }
    var edgeNodesToCheck: MutableCollection<Int> = ArrayList()
    edgeMap.filter { it.value.size == 2 }.entries.forEach {
        val edge = it.key.toList()
        val edgeNode = EdgeNode(edgeNodes.size, edge[0], edge[1], it.value[0], it.value[1])
        edgeNode.t1.edges.add(edgeNode)
        edgeNode.t2.edges.add(edgeNode)
        if (modifiedEdges == null || modifiedEdges.contains(it.key)) {
            edgeNodesToCheck.add(edgeNode.index)
        }
        edgeNodes.add(edgeNode)
    }
    var nextNodesToCheck: MutableCollection<Int> = LinkedHashSet()
    val nodesToCheckByAngle = LinkedHashSet<Int>(edgeNodesToCheck)
    var iterations = 0
    var flips = 1
    while (flips > 0 && iterations < 100) {
        flips = 0
        edgeNodesToCheck.forEach { nodeId ->
            val edgeNode = edgeNodes[nodeId]
            val tri1 = edgeNode.t1
            val tri2 = edgeNode.t2
            val peaks = mutableSetOf(tri1.p1, tri1.p2, tri1.p3, tri2.p1, tri2.p2, tri2.p3)
            peaks.remove(edgeNode.p1)
            peaks.remove(edgeNode.p2)
            val peakLineIds = peaks.toList()
            val baseLine = LineSegment2F(vertices[edgeNode.p1], vertices[edgeNode.p2])
            val peakLine = LineSegment2F(vertices[peakLineIds[0]], vertices[peakLineIds[1]])
            val check1 = baseLine.intersectsOrTouches(peakLine)
            if (debug) {
                val check2 = !polygon.isWithin(baseLine.interpolate(0.5f))
                val check3 = baseLineIntersectsPolygon(polygon, baseLine)
                val check4 = hasCollinearTriangle(vertices, tri1, tri2)
                val check5 = (!containsCollinearPoints(buildQuad(vertices, tri1, tri2)) && peakLine.length2 < baseLine.length2)
                draw(debugResolution, "debug-flipEdges2-1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                    graphics.color = Color.BLACK
                    triNodes.forEach {
                        val a = vertices[it.p1]
                        val b = vertices[it.p2]
                        val c = vertices[it.p3]
                        drawEdge(a, b)
                        drawEdge(b, c)
                        drawEdge(c, a)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                        drawPoint(c, 3)
                    }
                    graphics.color = if ((check1 && check5) || ((check1 || check2 || check3) && check4)) Color.RED else Color.BLUE
                    listOf(tri1, tri2).forEach {
                        val a = vertices[it.p1]
                        val b = vertices[it.p2]
                        val c = vertices[it.p3]
                        drawEdge(a, b)
                        drawEdge(b, c)
                        drawEdge(c, a)
                        drawPoint(a, 2)
                        drawPoint(b, 2)
                        drawPoint(c, 2)
                    }
                }
                breakPoint()
            }
            if ((check1 && (!containsCollinearPoints(buildQuad(vertices, tri1, tri2)) && peakLine.length2 < baseLine.length2)) || ((check1 || !polygon.isWithin(baseLine.interpolate(0.5f)) || baseLineIntersectsPolygon(polygon, baseLine)) && hasCollinearTriangle(vertices, tri1, tri2))) {
                val t1Edges = ArrayList(tri1.edges)
                val t2Edges = ArrayList(tri2.edges)
                t1Edges.remove(edgeNode)
                t2Edges.remove(edgeNode)
                tri1.p1 = peakLineIds[0]
                tri1.p2 = peakLineIds[1]
                tri1.p3 = edgeNode.p1
                tri2.p1 = peakLineIds[0]
                tri2.p2 = peakLineIds[1]
                tri2.p3 = edgeNode.p2
                edgeNode.p1 = peakLineIds[0]
                edgeNode.p2 = peakLineIds[1]
                tri1.edges.clear()
                tri2.edges.clear()
                tri1.edges.add(edgeNode)
                tri2.edges.add(edgeNode)
                (t1Edges + t2Edges).forEach { edge ->
                    if ((edge.p1 == tri1.p1 || edge.p1 == tri1.p2 || edge.p1 == tri1.p3) && (edge.p2 == tri1.p1 || edge.p2 == tri1.p2 || edge.p2 == tri1.p3)) {
                        if (edge.t1 == tri1 || edge.t1 == tri2) {
                            edge.t1 = tri1
                        } else {
                            edge.t2 = tri1
                        }
                        tri1.edges.add(edge)
                    } else {
                        if (edge.t1 == tri1 || edge.t1 == tri2) {
                            edge.t1 = tri2
                        } else {
                            edge.t2 = tri2
                        }
                        tri2.edges.add(edge)
                    }
                    nextNodesToCheck.add(edge.index)
                    nodesToCheckByAngle.add(edge.index)
                }
                flips++
            }
        }
        edgeNodesToCheck = nextNodesToCheck
        nextNodesToCheck = LinkedHashSet()
        iterations++
    }
    nodesToCheckByAngle.forEach { nodeId ->
        val edgeNode = edgeNodes[nodeId]
        val tri1 = edgeNode.t1
        val tri2 = edgeNode.t2
        val peaks = mutableSetOf(tri1.p1, tri1.p2, tri1.p3, tri2.p1, tri2.p2, tri2.p3)
        peaks.remove(edgeNode.p1)
        peaks.remove(edgeNode.p2)
        val peakLineIds = peaks.toList()
        val baseLine = LineSegment2F(vertices[edgeNode.p1], vertices[edgeNode.p2])
        val peakLine = LineSegment2F(vertices[peakLineIds[0]], vertices[peakLineIds[1]])
        if (debug) {
            val check1 = baseLine.intersects(peakLine)
            val check2 = (!containsCollinearPoints(buildQuad(vertices, tri1, tri2)) || ((isCollinearTriangle(vertices, tri1) || isCollinearTriangle(vertices, tri2)) && (!isCollinearTriangle(vertices, peakLineIds[0], peakLineIds[1], edgeNode.p1) && !isCollinearTriangle(vertices, peakLineIds[0], peakLineIds[1], edgeNode.p2))))
            val check3 = anglesNeedFlipping(getMinAndMaxAngles(baseLine, peakLine))
            draw(debugResolution, "debug-flipEdges2-2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                graphics.color = Color.BLACK
                triNodes.forEach {
                    val a = vertices[it.p1]
                    val b = vertices[it.p2]
                    val c = vertices[it.p3]
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                    drawPoint(c, 3)
                }
                graphics.color = if (check1 && check2 && check3) Color.RED else Color.BLUE
                listOf(tri1, tri2).forEach {
                    val a = vertices[it.p1]
                    val b = vertices[it.p2]
                    val c = vertices[it.p3]
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 2)
                    drawPoint(b, 2)
                    drawPoint(c, 2)
                }
            }
            breakPoint()
        }
        if (baseLine.intersects(peakLine) && (!containsCollinearPoints(buildQuad(vertices, tri1, tri2)) || ((isCollinearTriangle(vertices, tri1) || isCollinearTriangle(vertices, tri2)) && (!isCollinearTriangle(vertices, peakLineIds[0], peakLineIds[1], edgeNode.p1) && !isCollinearTriangle(vertices, peakLineIds[0], peakLineIds[1], edgeNode.p2)))) && anglesNeedFlipping(getMinAndMaxAngles(baseLine, peakLine))) {
            val t1Edges = ArrayList(tri1.edges)
            val t2Edges = ArrayList(tri2.edges)
            t1Edges.remove(edgeNode)
            t2Edges.remove(edgeNode)
            tri1.p1 = peakLineIds[0]
            tri1.p2 = peakLineIds[1]
            tri1.p3 = edgeNode.p1
            tri2.p1 = peakLineIds[0]
            tri2.p2 = peakLineIds[1]
            tri2.p3 = edgeNode.p2
            edgeNode.p1 = peakLineIds[0]
            edgeNode.p2 = peakLineIds[1]
            tri1.edges.clear()
            tri2.edges.clear()
            tri1.edges.add(edgeNode)
            tri2.edges.add(edgeNode)
            (t1Edges + t2Edges).forEach { edge ->
                if ((edge.p1 == tri1.p1 || edge.p1 == tri1.p2 || edge.p1 == tri1.p3) && (edge.p2 == tri1.p1 || edge.p2 == tri1.p2 || edge.p2 == tri1.p3)) {
                    if (edge.t1 == tri1 || edge.t1 == tri2) {
                        edge.t1 = tri1
                    } else {
                        edge.t2 = tri1
                    }
                    tri1.edges.add(edge)
                } else {
                    if (edge.t1 == tri1 || edge.t1 == tri2) {
                        edge.t1 = tri2
                    } else {
                        edge.t2 = tri2
                    }
                    tri2.edges.add(edge)
                }
            }
        }
    }
    triangles.clear()
    triNodes.forEach {
        triangles.add(setOf(it.p1, it.p2, it.p3))
    }
    return triangles
}

private fun baseLineIntersectsPolygon(polygon: Polygon2F, baseLine: LineSegment2F): Boolean {
    var check3 = false
    for (polyEdge in polygon.edges) {
        if (!(polyEdge.a.epsilonEquals(baseLine.a) || polyEdge.a.epsilonEquals(baseLine.b) || polyEdge.b.epsilonEquals(baseLine.a) || polyEdge.b.epsilonEquals(baseLine.b))) {
            if (polyEdge.intersectsOrTouches(baseLine)) {
                check3 = true
                break
            }
        }
    }
    return check3
}

private fun buildQuad(vertices: ArrayList<Point3F>, tri1: TriNode, tri2: TriNode): ArrayList<Point2F> {
    val quadEdges = LinkedHashSet<Set<Int>>()
    quadEdges.add(setOf(tri1.p1, tri1.p2))
    quadEdges.add(setOf(tri1.p2, tri1.p3))
    quadEdges.add(setOf(tri1.p3, tri1.p1))
    var t2Edge = setOf(tri2.p1, tri2.p2)
    if (!quadEdges.add(t2Edge)) {
        quadEdges.remove(t2Edge)
    }
    t2Edge = setOf(tri2.p2, tri2.p3)
    if (!quadEdges.add(t2Edge)) {
        quadEdges.remove(t2Edge)
    }
    t2Edge = setOf(tri2.p3, tri2.p1)
    if (!quadEdges.add(t2Edge)) {
        quadEdges.remove(t2Edge)
    }
    val quad = ArrayList<Point2F>(orderSegment(quadEdges).map { vertices[it.first] })
    return quad
}

private fun anglesNeedFlipping(minMax: Pair<Double, Double>) = (minMax.first < 0.55 && minMax.second > 2.0)

private fun getMinAndMaxAngles(baseLine: LineSegment2F, peakLine: LineSegment2F): Pair<Double, Double> {
    val angle1 = angle(baseLine.a, peakLine.a, baseLine.b)
    val angle2 = angle(baseLine.b, peakLine.b, baseLine.a)
    val minAngle = min(angle1, angle2)
    val maxAngle = max(angle1, angle2)
    return Pair(minAngle, maxAngle)
}

private fun hasCollinearTriangle(vertices: ArrayList<Point3F>, tri1: TriNode, tri2: TriNode) = isCollinearTriangle(vertices, tri1) || isCollinearTriangle(vertices, tri2)

private fun isCollinearTriangle(vertices: ArrayList<Point3F>, triangle: TriNode) = isCollinearTriangle(vertices[triangle.p1], vertices[triangle.p2], vertices[triangle.p3])

private fun isCollinearTriangle(vertices: ArrayList<Point3F>, a: Int, b: Int, c: Int) = isCollinearTriangle(vertices[a], vertices[b], vertices[c])

private fun isCollinearTriangle(p1: Point2F, p2: Point2F, p3: Point2F) =  containsCollinearPoints(listOf(p1, p2, p3)) || isCollinearTriangleAltMethod(p1, p2, p3)

private fun isCollinearTriangleAltMethod(a: Point2F, b: Point2F, c:Point2F): Boolean {
    val angle1 = angle(a, b, c)
    val angle2 = angle(b, c, a)
    val angle3 = angle(c, a, b)
    return angle1 > COLLINEAR_HALF_ANGLE || angle2 > COLLINEAR_HALF_ANGLE || angle3 > COLLINEAR_HALF_ANGLE
}

private fun containsCollinearPoints(points: List<Point2F>): Boolean {
    for (i in 1..points.size) {
        val ai = i % points.size
        val bi = i - 1
        val ci = (i + 1) % points.size
        val a = points[ai]
        val b = points[bi]
        val c = points[ci]
        val area = 0.5f * (-b.y * c.x + a.y * (-b.x + c.x) + a.x * (b.y - c.y) + b.x * c.y)
        if (area == 0.0f) {
            return true
        }
        val normal = (a - b).cross(c - b) / LineSegment2F(b, c).length
        if (Math.abs(normal) < 0.00001f) {
            return true
        }
    }
    return false
}

fun buildMesh(edgeSkeletonIn: ArrayList<LineSegment3F>, riverSkeletonIn: ArrayList<LineSegment3F>, globalVertices: PointSet2F): Pair<ArrayList<Point3F>, LinkedHashSet<Set<Int>>> {
    try {
        val edgeSkeleton = ArrayList(edgeSkeletonIn)
        val riverSkeleton = ArrayList(riverSkeletonIn)
        if (debug) {
            draw(debugResolution, "debug-buildMesh1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
                graphics.color = Color.BLACK
                graphics.stroke = BasicStroke(3.0f)
                edgeSkeleton.forEach {
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 5)
                    drawPoint(it.b, 5)
                }
                graphics.stroke = BasicStroke(1.0f)
                graphics.color = Color.RED
                riverSkeleton.forEach {
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 3)
                    drawPoint(it.b, 3)
                }
            }
            breakPoint()
        }
        globalMapEdges(globalVertices, edgeSkeleton)
        globalMapEdges(globalVertices, riverSkeleton)
        closeEdge(edgeSkeleton)
        globalMapEdges(globalVertices, edgeSkeleton)
        unTwistEdges(edgeSkeleton, true)
        moveRiverInsideBorder(globalVertices, edgeSkeleton, riverSkeleton)
        unTwistEdges(riverSkeleton)
        if (debug) {
            draw(debugResolution, "debug-buildMesh2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
                graphics.color = Color.BLACK
                graphics.stroke = BasicStroke(3.0f)
                edgeSkeleton.forEach {
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 5)
                    drawPoint(it.b, 5)
                }
                graphics.stroke = BasicStroke(1.0f)
                graphics.color = Color.RED
                riverSkeleton.forEach {
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 3)
                    drawPoint(it.b, 3)
                }
            }
            breakPoint()
        }
        moveRiverInsideBorder(globalVertices, edgeSkeleton, riverSkeleton)
        if (debug) {
            draw(debugResolution, "debug-buildMesh3-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
                graphics.color = Color.BLACK
                graphics.stroke = BasicStroke(3.0f)
                edgeSkeleton.forEach {
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 5)
                    drawPoint(it.b, 5)
                }
                graphics.stroke = BasicStroke(1.0f)
                graphics.color = Color.RED
                riverSkeleton.forEach {
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 3)
                    drawPoint(it.b, 3)
                }
            }
            breakPoint()
        }
        globalMapEdges(globalVertices, riverSkeleton)
        val edgePoints = PointSet2F()
        edgePoints.addAll(edgeSkeleton.flatMap { listOf(it.a, it.b) })
        val edgeEdges = LinkedHashSet<Pair<Int, Int>>()
        fun edgeEdge(a: Int, b: Int) = edgeEdges.add(Pair(min(a, b), max(a, b)))
        edgeSkeleton.forEach {
            edgeEdge(edgePoints[it.a], edgePoints[it.b])
        }
        val edgePolygons = getPolygonEdgeSets(edgePoints, edgeEdges, 0, false, false)
        val riverPoints = PointSet2F()
        riverPoints.addAll(riverSkeleton.flatMap { listOf(it.a, it.b) })
        val riverEdges = LinkedHashSet<Pair<Int, Int>>()
        fun riverEdge(a: Int, b: Int) = riverEdges.add(Pair(min(a, b), max(a, b)))
        riverSkeleton.forEach {
            riverEdge(riverPoints[it.a], riverPoints[it.b])
        }
        val riverPolygons = getPolygonEdgeSets(riverPoints, riverEdges, 0, false, true)
        removeCycles(riverPolygons)
        val meshPoints = PointSet2F()
        val edges = LinkedHashSet<Pair<Int, Int>>()
        fun edge(a: Int, b: Int) = edges.add(Pair(min(a, b), max(a, b)))
        meshPoints.addAll(edgePolygons.flatMap { it.flatMap { listOf(edgePoints[it.first]!!, edgePoints[it.second]!!) } })
        edgePolygons.flatMap { it }.forEach {
            edge(meshPoints[edgePoints[it.first]!!], meshPoints[edgePoints[it.second]!!])
        }
        meshPoints.addAll(riverPolygons.flatMap { it.flatMap { listOf(riverPoints[it.first]!!, riverPoints[it.second]!!) } })
        riverPolygons.flatMap { it }.forEach {
            edge(meshPoints[riverPoints[it.first]!!], meshPoints[riverPoints[it.second]!!])
        }
        val polygons = getPolygonEdgeSets(meshPoints, edges, 0)
        if (debug) {
            draw(debugResolution, "debug-buildMesh4-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
                graphics.color = Color.BLACK
                polygons.forEach {
                    it.forEach {
                        drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                    }
                }
            }
            breakPoint()
        }
        if (debug) {
            draw(debugResolution, "debug-buildMesh5-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
                graphics.color = Color.BLACK
                polygons.forEach {
                    it.forEach {
                        drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                    }
                }
            }
            breakPoint()
        }
        val vertices = ArrayList(meshPoints.map { it as Point3F })
        val triangles = LinkedHashSet<Set<Int>>()
        polygons.forEach { polygon ->
            triangles.addAll(buildRelaxedTriangles(vertices, polygon))
        }
        if (debug || debugFinal) {
            draw(debugResolution, "debug-buildMesh6-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) }) {
                graphics.color = Color.BLACK
                triangles.forEach {
                    val tri = it.toList()
                    val a = vertices[tri[0]]
                    val b = vertices[tri[1]]
                    val c = vertices[tri[2]]
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                    drawPoint(c, 3)
                }
            }
            breakPoint()
        }
        return Pair(vertices, triangles)
    } catch (g: GeometryException) {
            throw g
    } catch (e: Exception) {
        throw GeometryException("unable to build mesh for unknown reason", e)
    }
}

private fun buildRelaxedTriangles(vertices: ArrayList<Point3F>, polygon: ArrayList<Pair<Int, Int>>): Set<Set<Int>> {
    val triangles = triangulatePolygon(vertices, polygon)
    val internalVertexStart = vertices.size
    val maxTriArea = 2e-6f
    var lastSize = -1
    var count = 0
    while (lastSize != vertices.size && count < 100) {
        lastSize = vertices.size
        val edgesToCheck = LinkedHashSet<Set<Int>>()
        for (triangle in ArrayList(triangles)) {
            val tri = triangle.toList()
            val aid = tri[0]
            val bid = tri[1]
            val cid = tri[2]
            val a = vertices[aid]
            val b = vertices[bid]
            val c = vertices[cid]
            val area = area2d(a, b, c)
            if (area > maxTriArea) {
                val did = vertices.size
                vertices.add(Point3F((a.x + b.x + c.x) / 3.0f, (a.y + b.y + c.y) / 3.0f, (a.z + b.z + c.z) / 3.0f))
                triangles.remove(triangle)
                edgesToCheck.add(setOf(aid, bid))
                edgesToCheck.add(setOf(bid, cid))
                edgesToCheck.add(setOf(cid, aid))
                edgesToCheck.add(setOf(aid, did))
                edgesToCheck.add(setOf(bid, did))
                edgesToCheck.add(setOf(cid, did))
                triangles.add(setOf(aid, bid, did))
                triangles.add(setOf(bid, cid, did))
                triangles.add(setOf(cid, aid, did))
                if (debug) {
                    draw(debugResolution, "test-create-smaller-triangles${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                        graphics.color = Color.BLACK
                        triangles.forEach {
                            val one = it.toList()
                            val drawA = vertices[one[0]]
                            val drawB = vertices[one[1]]
                            val drawC = vertices[one[2]]
                            drawEdge(drawA, drawB)
                            drawEdge(drawB, drawC)
                            drawEdge(drawC, drawA)
                            drawPoint(drawA, 4)
                            drawPoint(drawB, 4)
                            drawPoint(drawC, 4)
                        }
                        graphics.color = Color.RED
                        drawEdge(a, b)
                        drawEdge(b, c)
                        drawEdge(c, a)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                        drawPoint(c, 3)
                        graphics.color = Color.GREEN
                        val d = vertices[did]
                        drawEdge(a, d)
                        drawEdge(b, d)
                        drawEdge(c, d)
                        drawPoint(d, 3)
                    }
                    breakPoint()
                }
            } else if (debug) {
                draw(debugResolution, "test-create-smaller-triangles${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                    graphics.color = Color.BLACK
                    triangles.forEach {
                        val one = it.toList()
                        val drawA = vertices[one[0]]
                        val drawB = vertices[one[1]]
                        val drawC = vertices[one[2]]
                        drawEdge(drawA, drawB)
                        drawEdge(drawB, drawC)
                        drawEdge(drawC, drawA)
                        drawPoint(drawA, 4)
                        drawPoint(drawB, 4)
                        drawPoint(drawC, 4)
                    }
                    graphics.color = Color.BLUE
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 3)
                    drawPoint(b, 3)
                    drawPoint(c, 3)
                }
                breakPoint()
            }
        }
        flipEdges(vertices, triangles, polygon, edgesToCheck)
        relaxTriangles(vertices, triangles, internalVertexStart, 3)
        count++
    }
    relaxTriangles(vertices, triangles, internalVertexStart, 7)
    if (debug) {
        draw(debugResolution, "test-triangles${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
            graphics.color = Color.BLACK
            graphics.stroke = BasicStroke(1.0f)
            triangles.forEach {
                val tri = it.toList()
                val a = vertices[tri[0]]
                val b = vertices[tri[1]]
                val c = vertices[tri[2]]
                val normal = (b - a).cross(c - a)
                val pointSize = if (normal.c == 0.0f) {
                    graphics.color = Color.RED
                    graphics.stroke = BasicStroke(2.0f)
                    8
                } else {
                    4
                }
                drawEdge(a, b)
                drawEdge(b, c)
                drawEdge(c, a)
                drawPoint(a, pointSize)
                drawPoint(b, pointSize)
                drawPoint(c, pointSize)
                if (normal.c == 0.0f) {
                    graphics.color = Color.BLACK
                    graphics.stroke = BasicStroke(1.0f)
                }
            }
        }
        val xVals = vertices.map { it.x }
        val yVals = vertices.map { it.y }
        val xMin = xVals.min()!!
        val xMax = xVals.max()!!
        val yMin = yVals.min()!!
        val yMax = yVals.max()!!
        val xDelta = xMax - xMin
        val yDelta = yMax - yMin
        val delta = max(xDelta, yDelta)
        val multiplier = 0.98f / delta
        val heightMap = ArrayListMatrix(debugResolution) { -Float.MAX_VALUE }
        val revisedVertices = ArrayList(vertices.map { Point3F(((it.x - xMin) * multiplier) + 0.01f, ((it.y - yMin) * multiplier) + 0.01f, it.z) })
        renderTriangles(revisedVertices, triangles, heightMap)
        writeHeightData("test-heightMap", heightMap)
    }
    return triangles
}

fun relaxTriangles(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, internalVertexStart: Int, iterations: Int) {
    for (iteration in 1..iterations) {
        for (i in internalVertexStart..vertices.size - 1) {
            val affectingPoints = LinkedHashSet<Int>()
            triangles.forEach {
                if (it.contains(i)) {
                    affectingPoints.addAll(it)
                }
            }
            affectingPoints.remove(i)
            val n = affectingPoints.size
            val b = if (n > 3) 3.0f / (8.0f * n) else (3.0f / 16.0f)
            val inb = 1.0f - (n * b)
            val initialPosition = vertices[i]
            var x = 0.0f
            var y = 0.0f
            var z = 0.0f
            affectingPoints.forEach {
                val point = vertices[it]
                x += point.x
                y += point.y
                z += point.z
            }
            x *= b
            y *= b
            z *= b
            x += initialPosition.x * inb
            y += initialPosition.y * inb
            z += initialPosition.z * inb
            vertices[i] = Point3F(x, y, z)
        }
    }
}

private fun area2d(a: Point3F, b: Point3F, c: Point3F): Float {
    val a2d = Point3F(a.x, a.y, 0.0f)
    val b2d = Point3F(b.x, b.y, 0.0f)
    val c2d = Point3F(c.x, c.y, 0.0f)
    return (b2d - a2d).cross(c2d - a2d).length / 2.0f
}

private fun removeCycles(polygons: ArrayList<ArrayList<Pair<Int, Int>>>) {
    for (polygon in polygons) {
        if (polygon.first().first == polygon.last().second) {
            var drop = false
            val drops = ArrayList<Pair<Int, Int>>()
            for (edge in polygon) {
                if (isJunction(polygons, polygon, edge)) {
                    drop = !drop
                    if (!drop) {
                        break
                    }
                }
                if (drop) {
                    drops.add(edge)
                }
            }
            polygon.removeAll(drops)
        }
    }
}

fun isJunction(polygons: ArrayList<ArrayList<Pair<Int, Int>>>, polygon: ArrayList<Pair<Int, Int>>, edge: Pair<Int, Int>): Boolean {
    for (each in polygons) {
        if (each == polygon) {
            continue
        }
        for (other in each) {
            if (edge.first == other.first || edge.first == other.second) {
                return true
            }
        }
    }
    return false
}

private fun globalMapEdges(globalVertexSet: PointSet2F, edgeSkeleton: ArrayList<LineSegment3F>) {
    edgeSkeleton.forEach {
        globalVertexSet.add(it.a)
        globalVertexSet.add(it.b)
    }
    val globalMappedEdgeSkeleton = edgeSkeleton.map { LineSegment3F(globalVertexSet[globalVertexSet[it.a]] as Point3F, globalVertexSet[globalVertexSet[it.b]] as Point3F) }.filter { it.a != it.b }
    edgeSkeleton.clear()
    edgeSkeleton.addAll(globalMappedEdgeSkeleton)
}

private fun unTwistEdges(skeleton: ArrayList<LineSegment3F>, secondaryCleanup: Boolean = false) {
    var hasFix = true
    while (hasFix) {
        hasFix = false
        var fixUp: Pair<LineSegment3F, LineSegment3F>? = null
        for (first in skeleton) {
            for (second in skeleton) {
                if (first != second && LineSegment2F(first.a, first.b).intersects(LineSegment2F(second.a, second.b))) {
                    fixUp = Pair(first, second)
                    break
                }
            }
            if (fixUp != null) {
                break
            }
        }
        if (fixUp != null) {
            skeleton.remove(fixUp.first)
            skeleton.remove(fixUp.second)
            val skeletonCopy = LinkedHashSet(skeleton)
            val fix1 = LineSegment3F(fixUp.first.a, fixUp.second.a)
            val fix2 = LineSegment3F(fixUp.first.b, fixUp.second.b)
            skeletonCopy.add(fix1)
            skeletonCopy.add(fix2)
            if (LineSegment2F.getConnectedEdgeSegments(skeletonCopy.map { LineSegment2F(it.a, it.b) }).size == 1) {
                skeleton.add(fix1)
                skeleton.add(fix2)
            } else {
                skeleton.add(LineSegment3F(fixUp.first.a, fixUp.second.b))
                skeleton.add(LineSegment3F(fixUp.first.b, fixUp.second.a))
            }
            hasFix = true
            if (debug) {
                draw(debugResolution, "debug-unTwistEdges1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, skeleton.flatMap { listOf(it.a, it.b) }) {
                    graphics.color = Color.BLACK
                    skeleton.forEach {
                        drawEdge(it.a, it.b)
                        drawPoint(it.a, 2)
                        drawPoint(it.b, 2)
                    }
                    graphics.color = Color.RED
                    drawEdge(fixUp!!.first.a, fixUp!!.first.b)
                    drawPoint(fixUp!!.first.a, 2)
                    drawPoint(fixUp!!.first.b, 2)
                    drawEdge(fixUp!!.second.a, fixUp!!.second.b)
                    drawPoint(fixUp!!.second.a, 2)
                    drawPoint(fixUp!!.second.b, 2)
                }
                breakPoint()
            }
        }
    }
    if (secondaryCleanup) {
        var modified = false
        for (point in ArrayList(skeleton.map { it.a })) {
            var badPoint = false
            for (edge in skeleton) {
                if (!edge.a.epsilonEquals(point) && !edge.b.epsilonEquals(point) && pointIsOnLine(LineSegment2F(edge.a, edge.b), point, angle = DOUBLE_COLLINEAR_ANGLE)) {
                    badPoint = true
                    if (debug) {
                        draw(debugResolution, "debug-unTwistEdges2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, skeleton.flatMap { listOf(it.a, it.b) }) {
                            graphics.color = Color.BLACK
                            skeleton.forEach {
                                drawEdge(it.a, it.b)
                                drawPoint(it.a, 2)
                                drawPoint(it.b, 2)
                            }
                            graphics.color = Color.GREEN
                            drawEdge(edge.a, edge.b)
                            graphics.color = Color.RED
                            drawPoint(point, 5)
                        }
                        breakPoint()
                    }
                    break
                }
            }
            if (badPoint) {
                if (debug) {
                    draw(debugResolution, "debug-unTwistEdges2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, skeleton.flatMap { listOf(it.a, it.b) }) {
                        graphics.color = Color.BLACK
                        skeleton.forEach {
                            drawEdge(it.a, it.b)
                            drawPoint(it.a, 2)
                            drawPoint(it.b, 2)
                        }
                        graphics.color = Color.RED
                        drawPoint(point, 5)
                    }
                    breakPoint()
                }
                val dropEdges = ArrayList<LineSegment3F>(2)
                for (edge in skeleton) {
                    if (edge.a.epsilonEquals(point) || edge.b.epsilonEquals(point)) {
                        dropEdges.add(edge)
                    }
                }
                dropEdges.forEach {
                    skeleton.remove(it)
                    modified = true
                }
            }
        }
        if (!modified) {
            return
        } else {
            val newPolygon = Polygon2F(Polygon2F.fromUnsortedEdges(skeleton.map { LineSegment2F(it.a, it.b) }).points, true)
            skeleton.clear()
            skeleton.addAll(newPolygon.edges.map { LineSegment3F(it.a as Point3F, it.b as Point3F) })
        }
    }
}

private fun closeEdge(edges: ArrayList<LineSegment3F>) {
    try {
        if (edges.first().a.epsilonEquals(edges.last().b)) {
            return
        }
        val unmodified = Polygon2F.fromUnsortedEdges(edges.map { LineSegment2F(it.a, it.b) })
        if (unmodified.isClosed) {
            return
        }
        val fillEdges = LineSegment3F(unmodified.edges.last().b as Point3F, unmodified.edges.first().a as Point3F).subdivided2d(0.002f)
        var newEdges = unmodified.edges.map { LineSegment3F(it.a as Point3F, it.b as Point3F) } + fillEdges
        val modified = Polygon2F.fromUnsortedEdges(newEdges.map { LineSegment2F(it.a, it.b) })
        if (!modified.isClosed) {
            newEdges = Polygon2F(modified.points, true).edges.map { LineSegment3F(it.a as Point3F, it.b as Point3F) }
        }
        if (debug) {
            draw(debugResolution, "debug-closeEdge-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edges.flatMap { listOf(it.a, it.b) }) {
                graphics.color = Color.RED
                newEdges.forEach {
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 2)
                    drawPoint(it.b, 2)
                }
                graphics.color = Color.BLACK
                edges.forEach {
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 2)
                    drawPoint(it.b, 2)
                }
            }
            breakPoint()
        }
        edges.clear()
        edges.addAll(newEdges)
    } catch (g: GeometryException) {
        throw g
    } catch (e: Exception) {
        throw GeometryException("unable to close empty polygon")
    }
}

private fun moveRiverInsideBorder(globalVertices: PointSet2F, edgeSkeleton: ArrayList<LineSegment3F>, riverSkeleton: ArrayList<LineSegment3F>) {
    if (riverSkeleton.isEmpty()) {
        return
    }
    val polygon = ArrayList(edgeSkeleton.map { Pair(globalVertices[it.a], globalVertices[it.b]) })
    val borderPoints = LinkedHashSet(polygon.map { it.first })
    val segments = LineSegment2F.getConnectedEdgeSegments(riverSkeleton.map { LineSegment2F(it.a, it.b) })
    val newRiverSkeleton = ArrayList<LineSegment3F>()
    var unmodified = true
    for (segment in segments) {
        val dropVertices = LinkedHashSet<Int>()
        segment.forEach {
            if (debug) {
                draw(debugResolution, "debug-closeEdge-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, edgeSkeleton.flatMap { listOf(it.a, it.b) }) {
                    graphics.color = Color.BLACK
                    edgeSkeleton.forEach {
                        drawEdge(it.a, it.b)
                        drawPoint(it.a, 2)
                        drawPoint(it.b, 2)
                    }
                    graphics.color = Color.BLUE
                    riverSkeleton.forEach {
                        drawEdge(it.a, it.b)
                        drawPoint(it.a, 2)
                        drawPoint(it.b, 2)
                    }
                    graphics.color = Color.RED
                    drawEdge(it.a, it.b)
                    drawPoint(it.a, 3)
                    drawPoint(it.b, 3)
                }
                breakPoint()
            }
            val a = globalVertices[it.a]
            val b = globalVertices[it.b]
            if (!borderPoints.contains(a) && (!containsPoint(globalVertices, polygon, a) || isOnBorder(edgeSkeleton, it.a))) {
                dropVertices.add(a)
            }
            if (!borderPoints.contains(b) && (!containsPoint(globalVertices, polygon, b) || isOnBorder(edgeSkeleton, it.b))) {
                dropVertices.add(b)
            }
        }
        if (dropVertices.isEmpty()) {
            if (segments.size == 1) {
                return
            }
            newRiverSkeleton.addAll(segment.map { LineSegment3F(it.a as Point3F, it.b as Point3F) })
        } else {
            unmodified = false
            val segmentIndex = segment.map { Pair(globalVertices[it.a], globalVertices[it.b]) }
            val dropMap = HashMap<Int, ArrayList<Int>>()
            dropVertices.forEach { dropVertex ->
                segmentIndex.forEach {
                    if (it.first == dropVertex) {
                        dropMap.getOrPut(dropVertex, { ArrayList() }).add(it.second)
                    } else if (it.second == dropVertex) {
                        dropMap.getOrPut(dropVertex, { ArrayList() }).add(it.first)
                    }
                }
            }
            for (line in segmentIndex) {
                if (dropVertices.contains(line.first) && dropVertices.contains(line.second)) {
                    continue
                }
                if (!dropVertices.contains(line.first) && !dropVertices.contains(line.second)) {
                    newRiverSkeleton.add(LineSegment3F(globalVertices[line.first] as Point3F, globalVertices[line.second] as Point3F))
                } else if (dropVertices.contains(line.first)) {
                    newRiverSkeleton.add(LineSegment3F(globalVertices[findSuitableReplacement(globalVertices, polygon, borderPoints, dropMap, line.first, line.second)] as Point3F, globalVertices[line.second] as Point3F))
                } else {
                    newRiverSkeleton.add(LineSegment3F(globalVertices[line.first] as Point3F, globalVertices[findSuitableReplacement(globalVertices, polygon, borderPoints, dropMap, line.second, line.first)] as Point3F))
                }
            }
        }
    }
    if (unmodified) {
        return
    }
    riverSkeleton.clear()
    riverSkeleton.addAll(newRiverSkeleton)
}

private fun findSuitableReplacement(globalVertices: PointSet2F, polygon: ArrayList<Pair<Int, Int>>, borderPoints: LinkedHashSet<Int>, dropMap: HashMap<Int, ArrayList<Int>>, toReplace: Int, cantUse: Int): Int {
    val cantUseSet = LinkedHashSet<Int>()
    cantUseSet.add(cantUse)
    val options = LinkedHashSet<Int>()
    options.add(toReplace)
    while (options.isNotEmpty()) {
        val option = options.first()
        options.remove(option)
        if (!dropMap.containsKey(option) && !cantUseSet.contains(option)) {
            return option
        } else {
            cantUseSet.add(option)
            val newOptions = LinkedHashSet(dropMap[option]!!)
            newOptions.removeAll(cantUseSet)
            options.addAll(newOptions)
        }
    }
    val polyEdges = polygon.map { LineSegment2F(globalVertices[it.first]!!, globalVertices[it.second]!!) }
    val orderedBorderPoints = ArrayList(borderPoints)
    orderedBorderPoints.sortBy { globalVertices[it]!!.distance2(globalVertices[toReplace]!!) }
    orderedBorderPoints.forEach {
        if (isValidInteriorEdge(polyEdges, LineSegment2F(globalVertices[cantUse]!!, globalVertices[it]!!))) {
            return it
        }
    }
    return orderedBorderPoints.first()
}

fun isValidInteriorEdge(polyEdges: List<LineSegment2F>, edge: LineSegment2F): Boolean {
    for (polyEdge in polyEdges) {
        if (polyEdge.epsilonEquals(edge)) {
            return false
        }
        if (polyEdge.a.epsilonEquals(edge.a) || polyEdge.b.epsilonEquals(edge.a) || polyEdge.a.epsilonEquals(edge.b) || polyEdge.b.epsilonEquals(edge.b)) {
            continue
        }
        if (polyEdge.intersectsOrTouches(edge)) {
            return false
        }
    }
    return true
}

private fun getPolygonEdgeSets(meshPoints: PointSet2F, edges: Collection<Pair<Int, Int>>, count: Int, putNonCyclesInCycles: Boolean = true, keepNonCycles: Boolean = true): ArrayList<ArrayList<Pair<Int, Int>>> {
    if (count > 100) {
        throw GeometryException("infinite recursion trying to get polygon edge sets")
    }
    val allPaths = ArrayList<ArrayList<Pair<Int, Int>>>()
    val segmentCycles = LinkedHashSet<LinkedHashSet<Int>>()
    val nodesInCycles = LinkedHashSet<Int>()
    val segments = getConnectedSegments(edges)
    val newEdges = LinkedHashSet<Pair<Int, Int>>()
    segments.forEach { segment ->
        val connections = HashMap<Int, LinkedHashSet<Int>>()
        val nodes = LinkedHashSet<Int>()
        segment.forEach {
            val edge = it.toList()
            val a = edge[0]
            val b = edge[1]
            nodes.add(a)
            nodes.add(b)
            connections.getOrPut(a, { LinkedHashSet() }).add(b)
            connections.getOrPut(b, { LinkedHashSet() }).add(a)
        }
        val segmentPaths = ArrayList<ArrayList<Pair<Int, Int>>>()
        val nonCycleNodes = LinkedHashSet<Int>()
        nodes.forEach { node ->
            if (!nodesInCycles.contains(node)) {
                val paths = findPaths(connections, LinkedHashSet<Set<Int>>(), node, node)
                if (paths != null) {
                    removeDuplicates(paths)
                    for (path in paths) {
                        if (isInnerPath(meshPoints, paths, path)) {
                            val segmentCycle = LinkedHashSet(path.flatMap { listOf(it.first, it.second) })
                            if (segmentCycles.add(segmentCycle)) {
                                nodesInCycles.addAll(segmentCycle)
                                segmentPaths.add(path)
                            }
                            break
                        }
                    }
                } else {
                    nonCycleNodes.add(node)
                }
            }
        }
        if (nonCycleNodes.isNotEmpty()) {
            val nonCycleSegments = getConnectedSegments(edges.filter { nonCycleNodes.contains(it.first) || nonCycleNodes.contains(it.second) })
            val orderedNonCycleSegments = ArrayList<ArrayList<Pair<Int, Int>>>()
            nonCycleSegments.forEach {
                val segmentSet = LinkedHashSet(it)
                while (segmentSet.isNotEmpty()) {
                    val newSegment = orderSegment(segmentSet)
                    segmentSet.removeAll(newSegment.map { setOf(it.first, it.second) })
                    orderedNonCycleSegments.add(newSegment)
                }
            }
            if (putNonCyclesInCycles) {
                orderedNonCycleSegments.forEach {
                    val (splicePoint, containingCycle) = findContainingCycle(meshPoints, segmentPaths, it)
                    if (splicePoint != null && containingCycle != null) {
                        val spliceEdge = findSuitableSpliceEdge(meshPoints, orderedNonCycleSegments, containingCycle, it, splicePoint)
                        val spliceLine = LineSegment3F(meshPoints[spliceEdge.first]!! as Point3F, meshPoints[spliceEdge.second]!! as Point3F)
                        val newLines = spliceLine.subdivided2d(0.002f)
                        newLines.forEach {
                            meshPoints.add(it.a)
                            meshPoints.add(it.b)
                        }
                        newEdges.addAll(newLines.map { Pair(meshPoints[it.a], meshPoints[it.b]) }.filter { it.first != it.second })
                    }
                }
            } else if (keepNonCycles) {
                segmentPaths.addAll(orderedNonCycleSegments)
            }
        }
        allPaths.addAll(segmentPaths)
    }
    if (newEdges.isEmpty()) {
        return allPaths
    } else {
        return getPolygonEdgeSets(meshPoints, edges + newEdges, count + 1, putNonCyclesInCycles)
    }
}

private fun containsPoint(meshPoints: PointSet2F, polygon: List<Pair<Int, Int>>, id: Int): Boolean {
    return containsPoint(meshPoints, polygon, meshPoints[id]!!)
}

private fun containsPoint(meshPoints: PointSet2F, polygon: List<Pair<Int, Int>>, point: Point2F): Boolean {
    val points = polygon.map { meshPoints[it.first]!! }
    var i: Int = 0
    var j: Int = points.size - 1
    var c = false
    while (i < points.size) {
        val pi = points[i]
        val pj = points[j]
        if (((pi.y > point.y) != (pj.y > point.y)) && (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)) {
            c = !c
        }
        j = i
        i++
    }
    return c
}

private fun getConnectedSegments(edges: Collection<Pair<Int, Int>>): List<Set<Set<Int>>> {
    return getConnectedEdgeSegments(edges.map { setOf(it.first, it.second) })
}

private fun getConnectedEdgeSegments(setEdges: Collection<Set<Int>>): List<Set<Set<Int>>> {
    val fullSet = LinkedHashSet(setEdges.filter { it.size == 2 })
    val unconnected = LinkedHashSet(fullSet)
    val segments = ArrayList<Set<Set<Int>>>()
    while (unconnected.isNotEmpty()) {
        val seed = unconnected.first()
        unconnected.remove(seed)
        val segment = getConnectedEdges(seed, fullSet)
        unconnected.removeAll(segment)
        segments.add(segment)
    }
    return segments
}

private fun getConnectedEdges(seed: Set<Int>, edgeSet: Collection<Set<Int>>): Set<Set<Int>> {
    val connectedEdges = LinkedHashSet<Set<Int>>()
    connectedEdges.add(seed)
    var nextEdges = LinkedHashSet<Set<Int>>(connectedEdges)
    while (nextEdges.isNotEmpty()) {
        val newEdges = LinkedHashSet<Set<Int>>()
        nextEdges.forEach { edge ->
            edgeSet.forEach {
                val intersection = HashSet(edge)
                intersection.retainAll(it)
                if (intersection.isNotEmpty()) {
                    newEdges.add(it)
                }
            }
        }
        newEdges.removeAll(connectedEdges)
        connectedEdges.addAll(newEdges)
        nextEdges = newEdges
    }
    return connectedEdges
}

private fun findPaths(connections: HashMap<Int, LinkedHashSet<Int>>, usedEdges: LinkedHashSet<Set<Int>>, start: Int, end: Int): ArrayList<ArrayList<Pair<Int, Int>>>? {
    val options = connections[start] ?: return null
    if (options.isEmpty()) {
        return null
    }
    val pathsFromHere = ArrayList<ArrayList<Pair<Int, Int>>>()
    if (options.contains(end) && !usedEdges.contains(setOf(start, end))) {
        pathsFromHere.add(arrayListOf(Pair(start, end)))
        return pathsFromHere
    }
    val paths = ArrayList<Pair<Pair<Int, Int>, ArrayList<Pair<Int, Int>>>>()
    options.forEach { option ->
        val theEdge = setOf(start, option)
        if (!usedEdges.contains(theEdge)) {
            val newUsedEdges = LinkedHashSet(usedEdges)
            newUsedEdges.add(theEdge)
            val nextLegs = findPaths(connections, newUsedEdges, option, end)
            nextLegs?.forEach { nextLeg ->
                paths.add(Pair(Pair(start, option), nextLeg))
            }
        }
    }
    if (paths.isEmpty()) {
        return null
    } else {
        paths.sortBy { it.second.size }
        paths.forEach {
            val thePath = ArrayList<Pair<Int, Int>>()
            thePath.add(it.first)
            thePath.addAll(it.second)
            pathsFromHere.add(thePath)
        }
        return pathsFromHere
    }
}

private fun removeDuplicates(paths: ArrayList<ArrayList<Pair<Int, Int>>>) {
    if (paths.size < 2) {
        return
    }
    val unorderedPaths = ArrayList<LinkedHashSet<Set<Int>>>()
    paths.forEach {
        unorderedPaths.add(LinkedHashSet(it.map { setOf(it.first, it.second) }))
    }
    for (i in paths.size - 1 downTo 0) {
        val oneUnorderedPath = unorderedPaths[i]
        for (j in 0..i - 1) {
            if (oneUnorderedPath == unorderedPaths[j]) {
                paths.removeAt(i)
                break
            }
        }
    }
}

private fun isInnerPath(meshPoints: PointSet2F, paths: ArrayList<ArrayList<Pair<Int, Int>>>, path: ArrayList<Pair<Int, Int>>): Boolean {
    val otherPaths = ArrayList(paths)
    otherPaths.remove(path)
    otherPaths.forEach {
        if (pathAContainsB(meshPoints, path, it)) {
            return false
        }
    }
    return true
}

private fun pathAContainsB(meshPoints: PointSet2F, a: ArrayList<Pair<Int, Int>>, b: ArrayList<Pair<Int, Int>>): Boolean {
    val aIds = a.map { it.first }
    val bIds = LinkedHashSet(b.map { it.first })
    bIds.removeAll(aIds)
    if (bIds.isEmpty()) {
        return true
    }
    return containsPoint(meshPoints, a, bIds.first())
}

private fun orderSegment(segment: Collection<Set<Int>>): ArrayList<Pair<Int, Int>> {
    try {
        val path = ArrayList<Pair<Int, Int>>()
        val mutable = ArrayList(segment.filter { it.size == 2 })
        val seed = mutable.removeAt(mutable.size - 1).toList()
        val seedPair = Pair(seed[0], seed[1])
        path.add(seedPair)
        var pair = seedPair
        var hasNext = true
        while (hasNext) {
            hasNext = false
            for (i in 0..mutable.size - 1) {
                if (mutable[i].contains(pair.first)) {
                    val next = LinkedHashSet(mutable.removeAt(i))
                    next.remove(pair.first)
                    pair = Pair(next.first(), pair.first)
                    path.add(0, pair)
                    hasNext = true
                    break
                }
            }
        }
        pair = seedPair
        hasNext = true
        while (hasNext) {
            hasNext = false
            for (i in 0..mutable.size - 1) {
                if (mutable[i].contains(pair.second)) {
                    val next = LinkedHashSet(mutable.removeAt(i))
                    next.remove(pair.second)
                    pair = Pair(pair.second, next.first())
                    path.add(pair)
                    hasNext = true
                    break
                }
            }
        }
        return path
    } catch (g: GeometryException) {
        throw g
    } catch (e: Exception) {
        throw GeometryException("unable to order empty segment")
    }
}

private fun  findContainingCycle(meshPoints: PointSet2F, cycles: ArrayList<ArrayList<Pair<Int, Int>>>, segment: ArrayList<Pair<Int, Int>>): Pair<Int?, ArrayList<Pair<Int, Int>>?> {
    val end1 = segment.first().first
    val end2 = segment.last().second
    val cyclesToTest = ArrayList<Pair<Int, ArrayList<Pair<Int, Int>>>>()
    for (cycle in cycles) {
        for (edge in cycle) {
            if (edge.first == end1 || edge.second == end1) {
                cyclesToTest.add(Pair(end2, cycle))
                break
            }
            if (edge.first == end2 || edge.second == end2) {
                cyclesToTest.add(Pair(end1, cycle))
                break
            }
        }
    }
    cyclesToTest.forEach {
        if (containsPoint(meshPoints, it.second, it.first)) {
            return it
        }
    }
    return Pair(null, null)
}

private fun findSuitableSpliceEdge(meshPoints: PointSet2F, segments: ArrayList<ArrayList<Pair<Int, Int>>>, containingCycle: ArrayList<Pair<Int, Int>>, segment: ArrayList<Pair<Int, Int>>, splicePoint: Int): Pair<Int, Int> {
    val b = meshPoints[splicePoint]!!
    val a = if (segment.first().first == splicePoint) {
        meshPoints[segment.last().second]!!
    } else {
        meshPoints[segment.first().first]!!
    }
    val vector = LineSegment2F(a, b).toVector().getUnit()
    val c = b + vector
    val testLine = LineSegment2F(b, c)
    var intersection = c
    var minDist2 = Float.MAX_VALUE
    containingCycle.forEach {
        val line = LineSegment2F(meshPoints[it.first]!!, meshPoints[it.second]!!)
        val currentIntersect = line.intersection(testLine)
        if (currentIntersect != null) {
            val distance2 = currentIntersect.distance2(b)
            if (distance2 < minDist2) {
                intersection = currentIntersect
                minDist2 = distance2
            }
        }
    }
    val constrainedEdges = (segments.flatMap { it } + containingCycle).map { LineSegment2F(meshPoints[it.first]!!, meshPoints[it.second]!!) }
    for ((id, point) in containingCycle.map { Pair(it.first, meshPoints[it.first]!!) }.sortedBy { it.second.distance2(intersection) }) {
        val line = LineSegment2F(b, point)
        var intersects = false
        for (constrainedEdge in constrainedEdges) {
            if (line.intersects(constrainedEdge)) {
                intersects = true
                break
            }
        }
        if (!intersects) {
            return Pair(splicePoint, id)
        }
    }
    throw GeometryException("there are no non-intersecting connections between a line segment contained within an edge cycle").with {
        data.add("val meshPoints = $meshPoints")
        data.add("val segments = ${printList(segments) { printList(it) { "Pair$it" }}}")
        data.add("val containingCycle = ${printList(containingCycle) { "Pair$it" }}")
        data.add("val segment = ${printList(segment) { "Pair$it" }}")
        data.add("val splicePoint = $splicePoint")
    }
}

fun spliceZeroHeightTriangles(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, maxTerrainSlope: Float) {
    val splices = LinkedHashMap<Pair<Int, Int>, Point3F>()
    triangles.forEach { triangle ->
        val tri = triangle.toList()
        val aId = tri[0]
        val bId = tri[1]
        val cId = tri[2]
        val a = vertices[aId]
        val b = vertices[bId]
        val c = vertices[cId]
        if (a.z == 0.0f && b.z == 0.0f) {
            triangles.forEach { other ->
                addHeightPointIfNeeded(splices, triangle, other, a, b, c, aId, bId, maxTerrainSlope)
            }
        }
        if (b.z == 0.0f && c.z == 0.0f) {
            triangles.forEach { other ->
                addHeightPointIfNeeded(splices, triangle, other, b, c, a, bId, cId, maxTerrainSlope)
            }

        }
        if (c.z == 0.0f && a.z == 0.0f) {
            triangles.forEach { other ->
                addHeightPointIfNeeded(splices, triangle, other, c, a, b, cId, aId, maxTerrainSlope)
            }
        }
    }
    splices.forEach { edge, point ->
        val trianglesToModify = ArrayList<Set<Int>>(2)
        val pointIndex = vertices.size
        vertices.add(point)
        triangles.forEach {
            if (it.containsAll(edge.toList())) {
                trianglesToModify.add(it)
            }
        }
        triangles.removeAll(trianglesToModify)
        trianglesToModify.forEach {
            val first = HashSet(it)
            val second = HashSet(it)
            first.remove(edge.first)
            second.remove(edge.second)
            first.add(pointIndex)
            second.add(pointIndex)
            triangles.add(first)
            triangles.add(second)
            if (debug) {
                draw(debugResolution * 4, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, vertices) {
                    graphics.color = Color.BLACK
                    triangles.forEach {
                        val tri = it.toList()
                        val p1 = vertices[tri[0]]
                        val p2 = vertices[tri[1]]
                        val p3 = vertices[tri[2]]
                        drawEdge(p1, p2)
                        drawEdge(p2, p3)
                        drawEdge(p3, p1)
                        drawPoint(p1, 8)
                        drawPoint(p2, 8)
                        drawPoint(p3, 8)
                    }
                    graphics.color = Color.RED
                    trianglesToModify.forEach {
                        val tri = it.toList()
                        val p1 = vertices[tri[0]]
                        val p2 = vertices[tri[1]]
                        val p3 = vertices[tri[2]]
                        drawEdge(p1, p2)
                        drawEdge(p2, p3)
                        drawEdge(p3, p1)
                        drawPoint(p1, 5)
                        drawPoint(p2, 5)
                        drawPoint(p3, 5)
                    }
                    graphics.color = Color.GREEN
                    drawEdge(vertices[edge.first], vertices[edge.second])
                    drawPoint(vertices[edge.first],3)
                    drawPoint(vertices[edge.second], 3)
                    graphics.color = Color.MAGENTA
                    drawPoint(point, 2)
                    graphics.color = Color.BLUE
                    val tri1 = first.toList()
                    val p11 = vertices[tri1[0]]
                    val p12 = vertices[tri1[1]]
                    val p13 = vertices[tri1[2]]
                    drawEdge(p11, p12)
                    drawEdge(p12, p13)
                    drawEdge(p13, p11)
                    drawPoint(p11, 2)
                    drawPoint(p12, 2)
                    drawPoint(p13, 2)
                    graphics.color = Color.CYAN
                    val tri2 = second.toList()
                    val p21 = vertices[tri2[0]]
                    val p22 = vertices[tri2[1]]
                    val p23 = vertices[tri2[2]]
                    drawEdge(p21, p22)
                    drawEdge(p22, p23)
                    drawEdge(p23, p21)
                    drawPoint(p21, 2)
                    drawPoint(p22, 2)
                    drawPoint(p23, 2)
                }
                breakPoint()
            }
        }
    }
}

private fun addHeightPointIfNeeded(splices: LinkedHashMap<Pair<Int, Int>, Point3F>, triangle: Set<Int>, other: Set<Int>, p1: Point3F, p2: Point3F, p3: Point3F, id1: Int, id2: Int, maxTerrainSlope: Float) {
    if (triangle != other && other.contains(id1) && other.contains(id2)) {
        val splicePoint = LineSegment2F(p1, p2).interpolate(0.5f)
        val height = Math.sqrt(min(splicePoint.distance2(p1), splicePoint.distance2(p2), splicePoint.distance2(p3)).toDouble()) * maxTerrainSlope
        val key = Pair(id1, id2)
        if (height < splices[key]?.z ?: Float.MAX_VALUE) {
            splices[key] = Point3F(splicePoint.x, splicePoint.y, height.toFloat())
        }
    }
}

fun renderTriangles(executor: ExecutorService, vertices: ArrayList<Point3F>, triangles: ArrayList<Int>, heightMap: Matrix<Float>, threadCount: Int) {
    val futures = ArrayList<Future<*>>(threadCount)
    val step = 3 * threadCount
    for (i in 0..threadCount - 1) {
        futures.add(executor.submit {
            for (t in (i * 3)..triangles.size - 1 step step) {
                val a = vertices[triangles[t]]
                val b = vertices[triangles[t + 1]]
                val c = vertices[triangles[t + 2]]
                val cross = (b - a).cross(c - a)
                if (cross.c < 0) {
                    renderTriangle(a, b, c, heightMap)
                } else {
                    renderTriangle(a, c, b, heightMap)
                }
            }
        })
    }
    futures.forEach {
        it.get()
    }
}

fun renderTriangles(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, heightMap: Matrix<Float>) {
    triangles.forEach {
        val tri = it.toList()
        val a = vertices[tri[0]]
        val b = vertices[tri[1]]
        val c = vertices[tri[2]]
        val cross = (b - a).cross(c - a)
        if (cross.c < 0) {
            renderTriangle(a, b, c, heightMap)
        } else {
            renderTriangle(a, c, b, heightMap)
        }
    }
}

fun renderTriangle(a: Point3F, b: Point3F, c: Point3F, heightMap: Matrix<Float>) {
    val stride = heightMap.width
    val strideF = stride.toFloat()
    val p1 = Point3F(a.x * strideF, a.y * strideF, a.z)
    val p2 = Point3F(b.x * strideF, b.y * strideF, b.z)
    val p3 = Point3F(c.x * strideF, c.y * strideF, c.z)
    val normal = (p2 - p1).cross(p3 - p1)
    val d = -normal.dot(p1)
    val na = normal.a
    val nb = normal.b
    val nc = normal.c
    val minZ = min(p1.z, p2.z, p3.z)
    val maxZ = max(p1.z, p2.z, p3.z)
    if (nc == 0.0f || p1.z.isNaN() || p2.z.isNaN() || p3.z.isNaN()) {
        if (debug) {
            throw GeometryException("collinear triangle").with {
                data.add("val a = $a")
                data.add("val b = $b")
                data.add("val c = $c")
            }
        }
        return
    }

    fun interpolateZ(x: Int, y: Int): Float {
        val height = clamp(minZ, maxZ, -((na * x) + (nb * y) + d) / nc)
        if (height.isNaN()) {
            if (debug) {
                throw GeometryException("collinear triangle").with {
                    data.add("val a = $a")
                    data.add("val b = $b")
                    data.add("val c = $c")
                }
            }
        }
        return height
    }

    val ax: Int = round(16.0f * p1.x)
    val bx: Int = round(16.0f * p2.x)
    val cx: Int = round(16.0f * p3.x)

    val ay: Int = round(16.0f * p1.y)
    val by: Int = round(16.0f * p2.y)
    val cy: Int = round(16.0f * p3.y)

    val dxAb: Int = ax - bx
    val dxBc: Int = bx - cx
    val dxCa: Int = cx - ax

    val dyAb: Int = ay - by
    val dyBc: Int = by - cy
    val dyCa: Int = cy - ay

    val fdxAb: Int = dxAb shl 4
    val fdxBc: Int = dxBc shl 4
    val fdxCa: Int = dxCa shl 4

    val fdyAb: Int = dyAb shl 4
    val fdyBc: Int = dyBc shl 4
    val fdyCa: Int = dyCa shl 4

    var minX: Int = (min(ax, bx, cx) + 0xF) shr 4
    val maxX: Int = (max(ax, bx, cx) + 0xF) shr 4
    var minY: Int = (min(ay, by, cy) + 0xF) shr 4
    val maxY: Int = (max(ay, by, cy) + 0xF) shr 4

    val q: Int = 8

    minX = minX and (q - 1).complement()
    minY = minY and (q - 1).complement()

    var c1: Int = dyAb * ax - dxAb * ay
    var c2: Int = dyBc * bx - dxBc * by
    var c3: Int = dyCa * cx - dxCa * cy

    if(dyAb < 0 || (dyAb == 0 && dxAb > 0)) c1++
    if(dyBc < 0 || (dyBc == 0 && dxBc > 0)) c2++
    if(dyCa < 0 || (dyCa == 0 && dxCa > 0)) c3++

    var blockYOffset: Int = minY * stride

    var y: Int = minY
    while (y < maxY) {
        var x: Int = minX
        while (x < maxX) {
            val x0: Int = x shl 4
            val x1: Int = (x + q - 1) shl 4
            val y0: Int = y shl 4
            val y1: Int = (y + q - 1) shl 4

            val a00: Int = if (c1 + dxAb * y0 - dyAb * x0 > 0) 1 else 0
            val a10: Int = if (c1 + dxAb * y0 - dyAb * x1 > 0) 1 else 0
            val a01: Int = if (c1 + dxAb * y1 - dyAb * x0 > 0) 1 else 0
            val a11: Int = if (c1 + dxAb * y1 - dyAb * x1 > 0) 1 else 0
            val hs1 = a00 or (a10 shl 1) or (a01 shl 2) or (a11 shl 3)

            val b00: Int = if (c2 + dxBc * y0 - dyBc * x0 > 0) 1 else 0
            val b10: Int = if (c2 + dxBc * y0 - dyBc * x1 > 0) 1 else 0
            val b01: Int = if (c2 + dxBc * y1 - dyBc * x0 > 0) 1 else 0
            val b11: Int = if (c2 + dxBc * y1 - dyBc * x1 > 0) 1 else 0
            val hs2: Int = b00 or (b10 shl 1) or (b01 shl 2) or (b11 shl 3)

            val c00: Int = if (c3 + dxCa * y0 - dyCa * x0 > 0) 1 else 0
            val c10: Int = if (c3 + dxCa * y0 - dyCa * x1 > 0) 1 else 0
            val c01: Int = if (c3 + dxCa * y1 - dyCa * x0 > 0) 1 else 0
            val c11: Int = if (c3 + dxCa * y1 - dyCa * x1 > 0) 1 else 0
            val hs3: Int = c00 or (c10 shl 1) or (c01 shl 2) or (c11 shl 3)

            if (hs1 == 0x0 || hs2 == 0x0 || hs3 == 0x0) {
                x += q
                continue
            }

            var yOffset: Int = blockYOffset

            if (hs1 == 0xF && hs2 == 0xF && hs3 == 0xF) {
                var iy: Int = y
                val endY = y + q
                while (iy < endY) {
                    var ix: Int = x
                    val endX = x + q
                    while (ix < endX) {
                        val index = yOffset + ix
                        if (index < heightMap.size) {
                            heightMap[index] = interpolateZ(ix, iy)
                        }
                        ix++
                    }
                    yOffset += stride
                    iy++
                }
            } else {
                var cy1: Int = c1 + dxAb * y0 - dyAb * x0
                var cy2: Int = c2 + dxBc * y0 - dyBc * x0
                var cy3: Int = c3 + dxCa * y0 - dyCa * x0

                var iy = y
                val endY = y + q
                while (iy < endY) {
                    var cx1: Int = cy1
                    var cx2: Int = cy2
                    var cx3: Int = cy3

                    var ix = x
                    val endX = x + q
                    while (ix < endX) {
                        if(cx1 > 0 && cx2 > 0 && cx3 > 0) {
                            val index = yOffset + ix
                            if (index < heightMap.size) {
                                heightMap[index] = interpolateZ(ix, iy)
                            }
                        }
                        cx1 -= fdyAb
                        cx2 -= fdyBc
                        cx3 -= fdyCa
                        ix++
                    }

                    cy1 += fdxAb
                    cy2 += fdxBc
                    cy3 += fdxCa

                    yOffset += stride
                    iy++
                }
            }
            x += q
        }
        blockYOffset += q * stride
        y += q
    }
}

fun writeHeightData(name: String, heightMap: Matrix<Float>) {
    var maxLandValue = -Double.MAX_VALUE
    var minWaterValue = Double.MAX_VALUE
    for (y in (0..heightMap.width - 1)) {
        for (x in (0..heightMap.width - 1)) {
            val valueF = heightMap[x, y]
            if (valueF == -Float.MAX_VALUE) {
                continue
            }
            if (valueF < 0.0f) {
                val value = valueF.toDouble()
                minWaterValue = min(minWaterValue, value)
            } else {
                val value = valueF.toDouble()
                maxLandValue = max(maxLandValue, value)
            }
        }
    }
    val waterLine = 0.30f
    val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
    val waterFactor = (1.0f / -minWaterValue) * waterLine
    val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
    val raster = output.raster
    for (y in (0..heightMap.width - 1)) {
        for (x in (0..heightMap.width - 1)) {
            val value = heightMap[x, y]
            if (value < 0.0f) {
                val pixel = (((value - minWaterValue) * waterFactor) * 65535).toInt()
                raster.setSample(x, y, 0, pixel)
            } else {
                val pixel = (((value * landFactor) + waterLine) * 65535).toInt()
                raster.setSample(x, y, 0, pixel)
            }
        }
    }
    ImageIO.write(output, "png", File("output/$name.png"))
}

fun max(a: Int, b: Int, c: Int) = max(max(a, b), c)

fun min(a: Int, b: Int, c: Int) = min(min(a, b), c)

fun min(a: Float, b: Float, c: Float) = min(min(a, b), c)

fun max(a: Float, b: Float, c: Float) = max(max(a, b), c)

fun clamp(min: Float, max: Float, f: Float) = min(max(min, f), max)

