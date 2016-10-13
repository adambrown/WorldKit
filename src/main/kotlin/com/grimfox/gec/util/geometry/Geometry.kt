package com.grimfox.gec.util.geometry

import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.drawing.draw
import com.grimfox.gec.util.drawing.drawEdge
import com.grimfox.gec.util.drawing.drawPoint
import com.grimfox.gec.util.geometry.Geometry.debug
import com.grimfox.gec.util.geometry.Geometry.debugCount
import com.grimfox.gec.util.geometry.Geometry.debugIteration
import com.grimfox.gec.util.geometry.Geometry.debugResolution
import com.grimfox.gec.util.printList
import java.awt.BasicStroke
import java.awt.Color
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class GeometryException(message: String? = null, cause: Throwable? = null, var test: Int? = null, var id: Int? = null, val data: ArrayList<String> = ArrayList<String>()): Exception(message, cause) {

    fun with(adjustment: GeometryException.() -> Unit): GeometryException {
        this.adjustment()
        return this
    }
}

private fun breakPoint() {
    doNothing()
}

private fun doNothing() {}

object Geometry {

    var debug = false
    var debugCount = AtomicInteger(1)
    var debugIteration = AtomicInteger(1)
    var debugResolution = 4096

    @JvmStatic fun main(vararg args: String) {

        val keeper1 = {
            val vertices = PointSet2F(points=arrayListOf(Point3F(x=0.657852f, y=0.52122694f, z=0.0f), Point3F(x=0.6578838f, y=0.5195371f, z=0.0f), Point3F(x=0.6580053f, y=0.5194083f, z=0.0f), Point3F(x=0.6593489f, y=0.5195656f, z=0.0f), Point3F(x=0.66068435f, y=0.51934993f, z=0.0f), Point3F(x=0.6616354f, y=0.51807094f, z=0.0f), Point3F(x=0.6618671f, y=0.5164941f, z=0.0f), Point3F(x=0.6611924f, y=0.5149553f, z=0.0f), Point3F(x=0.6605042f, y=0.51342237f, z=0.0f), Point3F(x=0.66095287f, y=0.5125212f, z=0.0f), Point3F(x=0.6616357f, y=0.5117815f, z=0.0f), Point3F(x=0.66165805f, y=0.5115545f, z=9.758234E-4f), Point3F(x=0.6616804f, y=0.5113275f, z=0.0f), Point3F(x=0.6607708f, y=0.5100297f, z=0.0f), Point3F(x=0.6597271f, y=0.5088368f, z=0.0f), Point3F(x=0.65864193f, y=0.5084596f, z=0.0f), Point3F(x=0.6579075f, y=0.5067031f, z=0.0f), Point3F(x=0.6575375f, y=0.5062737f, z=0.0f), Point3F(x=0.6581144f, y=0.5050086f, z=0.004732944f), Point3F(x=0.6599802f, y=0.50457114f, z=0.005903714f), Point3F(x=0.661846f, y=0.5041337f, z=0.0070744837f), Point3F(x=0.6637118f, y=0.50369626f, z=0.008245254f), Point3F(x=0.66557753f, y=0.5032588f, z=0.009416023f), Point3F(x=0.66709393f, y=0.50457025f, z=0.009447934f), Point3F(x=0.66861033f, y=0.50588167f, z=0.009479845f), Point3F(x=0.6701267f, y=0.50719315f, z=0.009511756f), Point3F(x=0.6702467f, y=0.50898916f, z=0.0073777726f), Point3F(x=0.67036676f, y=0.51078516f, z=0.0052437894f), Point3F(x=0.6704868f, y=0.51258117f, z=0.0031098062f), Point3F(x=0.67060685f, y=0.5143771f, z=9.758234E-4f), Point3F(x=0.6707269f, y=0.5161731f, z=0.0033218227f), Point3F(x=0.67084694f, y=0.51796913f, z=0.005667822f), Point3F(x=0.670967f, y=0.51976514f, z=0.008013821f), Point3F(x=0.6710871f, y=0.52156115f, z=0.01035982f), Point3F(x=0.6691972f, y=0.5217548f, z=0.008879846f), Point3F(x=0.6673072f, y=0.5219485f, z=0.007399872f), Point3F(x=0.6654172f, y=0.52214223f, z=0.0059198975f), Point3F(x=0.6635272f, y=0.52233595f, z=0.004439923f), Point3F(x=0.6616372f, y=0.52252966f, z=0.0029599487f), Point3F(x=0.6597472f, y=0.5227234f, z=0.0014799744f), Point3F(x=0.6578572f, y=0.5229171f, z=0.0f), Point3F(x=0.6691004f, y=0.51313883f, z=8.1323006E-4f), Point3F(x=0.66772753f, y=0.5117935f, z=6.5296283E-4f), Point3F(x=0.66613245f, y=0.5129658f, z=4.8791163E-4f), Point3F(x=0.6645374f, y=0.5141381f, z=3.2286043E-4f), Point3F(x=0.6631645f, y=0.51279277f, z=1.6259319E-4f)))
            val polygon = arrayListOf(Pair(12, 11), Pair(11, 45), Pair(45, 44), Pair(44, 43), Pair(43, 42), Pair(42, 41), Pair(41, 29), Pair(29, 28), Pair(28, 27), Pair(27, 26), Pair(26, 25), Pair(25, 24), Pair(24, 23), Pair(23, 22), Pair(22, 21), Pair(21, 20), Pair(20, 19), Pair(19, 18), Pair(18, 17), Pair(17, 16), Pair(16, 15), Pair(15, 14), Pair(14, 13), Pair(13, 12))
            triangulatePolygon(vertices, polygon)
        }

        val keeper2 = {
            val vertices = PointSet2F(points=arrayListOf(Point3F(x=0.5040726f, y=0.18875736f, z=0.0f), Point3F(x=0.50388956f, y=0.19026884f, z=0.0f), Point3F(x=0.50371873f, y=0.19178124f, z=0.0f), Point3F(x=0.5037761f, y=0.1918694f, z=0.0f), Point3F(x=0.50361437f, y=0.19342753f, z=0.0f), Point3F(x=0.5029236f, y=0.19420259f, z=0.0f), Point3F(x=0.50211763f, y=0.19485693f, z=0.0f), Point3F(x=0.5019059f, y=0.19505912f, z=0.0f), Point3F(x=0.50243187f, y=0.19627202f, z=0.0f), Point3F(x=0.5030295f, y=0.19745126f, z=0.0f), Point3F(x=0.5032157f, y=0.19876012f, z=0.0f), Point3F(x=0.5025165f, y=0.19976038f, z=0.0f), Point3F(x=0.5013204f, y=0.2000029f, z=0.0f), Point3F(x=0.5001255f, y=0.19894356f, z=0.0f), Point3F(x=0.4988648f, y=0.19796355f, z=0.0f), Point3F(x=0.49752885f, y=0.19889478f, z=0.0f), Point3F(x=0.49632633f, y=0.19999295f, z=0.0f), Point3F(x=0.4952897f, y=0.19915582f, z=0.0f), Point3F(x=0.4940482f, y=0.19963315f, z=0.0f), Point3F(x=0.49459764f, y=0.19779412f, z=6.8200426E-4f), Point3F(x=0.495147f, y=0.19595513f, z=0.0013640085f), Point3F(x=0.49569634f, y=0.19411613f, z=0.0020460128f), Point3F(x=0.49624568f, y=0.19227713f, z=0.002728017f), Point3F(x=0.49679503f, y=0.19043814f, z=0.0034100213f), Point3F(x=0.49734437f, y=0.18859914f, z=0.0040920256f), Point3F(x=0.49789372f, y=0.18676014f, z=0.00477403f), Point3F(x=0.49844307f, y=0.18492115f, z=0.005456034f), Point3F(x=0.5003828f, y=0.18569665f, z=0.0036373562f), Point3F(x=0.50232255f, y=0.18647213f, z=0.0018186781f), Point3F(x=0.5042623f, y=0.18724762f, z=0.0f)))
            val polygon = arrayListOf(Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), Pair(8, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), Pair(12, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), Pair(16, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), Pair(20, 21), Pair(21, 22), Pair(22, 23), Pair(23, 24), Pair(24, 25), Pair(25, 26), Pair(26, 27), Pair(27, 28), Pair(28, 29), Pair(29, 0))
            triangulatePolygon(vertices, polygon)
        }

        val tests = listOf<() -> Any?>(

        )

        debug = true

        tests.forEach { test ->
            test()
            debugIteration.incrementAndGet()
        }
    }
}

private class CollinearPatch(val start: Point2F, val end: Point2F, val points: ArrayList<Point2F>)

fun triangulatePolygon(vertices: PointSet2F, polygon: ArrayList<Pair<Int, Int>>): LinkedHashSet<Set<Int>> {
    val points = ArrayList(polygon.map { vertices[it.first]!! })
    if (areaOfPolygon(points) < 0) {
        points.reverse()
    }
    val collinearPatches = findCollinearPatches(points)
    collinearPatches.forEach {
        points.removeAll(it.points)
    }
    val reducedPoints = ArrayList(points)
    val newEdges = ArrayList<LineSegment2F>()
    while (points.size > 3) {
        val (ai, bi, ci) = findNextEar(points)
        try {
            newEdges.add(LineSegment2F(points[bi], points[ci]))
            if (debug) {
                draw(debugResolution, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
                    graphics.color = Color.BLACK
                    for (i in 1..points.size) {
                        val a = points[i - 1]!!
                        val b = points[i % points.size]!!
                        drawEdge(a, b)
                        drawPoint(a, 3)
                        drawPoint(b, 3)
                    }
                    graphics.color = Color.RED
                    drawEdge(points[bi], points[ci])
                    drawPoint(points[bi], 4)
                    drawPoint(points[ci], 4)
                    graphics.color = Color.GREEN
                    points.forEach {
                        drawPoint(it, 2)
                    }
                }
                breakPoint()
            }
            points.removeAt(ai)
        } catch (e: Exception) {
            if (debug) {
                draw(debugResolution, "debug-triangulatePolygon2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
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
                        val a = vertices[it.first]!!
                        val b = vertices[it.second]!!
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
                data.add("val vertices = $vertices")
                data.add("val polygon = ${printList(polygon) { "Pair$it" }}")
                data.add("triangulatePolygon(vertices, polygon)")
                data.add("}")
            }
        }
    }
    var reducedPolygon = polygonFromPoints(vertices, reducedPoints)
    var meshMinusPatches = buildMesh(reducedPolygon + newEdges.map { Pair(vertices[it.a], vertices[it.b]) }, vertices.size)
    while (collinearPatches.isNotEmpty()) {
        val patch = collinearPatches.first()
        collinearPatches.remove(patch)
        val sid = vertices[patch.start]
        val eid = vertices[patch.end]
        val edge = listOf(sid, eid)
        for (tri in ArrayList(meshMinusPatches)) {
            if (tri.containsAll(edge)) {
                meshMinusPatches.remove(tri)
                val convergence = LinkedHashSet(tri)
                convergence.removeAll(edge)
                val focus = vertices[convergence.first()]!!
                patch.points.forEach {
                    newEdges.add(LineSegment2F(it, focus))
                }
                break
            }
        }
        addPatchPoints(reducedPoints, patch)
        reducedPolygon = polygonFromPoints(vertices, reducedPoints)
        meshMinusPatches = buildMesh(reducedPolygon + newEdges.map { Pair(vertices[it.a], vertices[it.b]) }, vertices.size)
    }
    return flipEdges(vertices, buildMesh(polygon + newEdges.map { Pair(vertices[it.a], vertices[it.b]) }, vertices.size))
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

private fun findNextEar(points: ArrayList<Point2F>): Triple<Int, Int, Int> {
    var index1 = -1
    var index2 = -1
    var index3 = -1
    var angle = -0.1
    for (i in 1..points.size) {
        val ai = i % points.size
        val bi = i - 1
        val ci = (i + 1) % points.size
        val a = points[ai]
        val b = points[bi]
        val c = points[ci]
        val normal = (b - a).cross(c - a)
        if (normal >= 0.0f) {
            continue
        }
        if (anyPointWithin(points, bi, ci, ai)) {
            continue
        }
        val newWeight = angle(points, bi, ai, ci)
        if (newWeight > angle) {
            angle = newWeight
            index1 = ai
            index2 = bi
            index3 = ci
        }
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
            draw(debugResolution, "debug-findCollinearPatches-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(points.map { it.x }.min()!!) + 0.0005f, -(points.map { it.y }.min()!!) + 0.0005f)) {
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
        val angle = halfAngle(points, ai, bi, ci)
        if (Math.abs(angle) < 0.08 && patchSum + angle < 0.16) {
            collinearIds.add(bi)
            patchSum += angle
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

private fun anyPointWithin(points: ArrayList<Point2F>, ai: Int, bi: Int, ci: Int): Boolean {
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
        val angle = angle(points, ai, i, bi)
        if (LineSegment2F(b, c).distance2(p) < 0.000005f && angle < 0.08f) {
            return true
        }
    }
    return false
}

private fun halfAngle(points: ArrayList<Point2F>, ai: Int, bi: Int, ci: Int): Double {
    val a = points[ai]
    val b = points[bi]
    val c = points[ci]
    val ba = Vector2F(b, a)
    val bc = Vector2F(b, c)
    val ba3d = Vector3F(ba.a, ba.b, 0.0f)
    val bc3d = Vector3F(bc.a, bc.b, 0.0f)
    return Math.atan(ba3d.cross(bc3d).length / ba.dot(bc).toDouble())
}

private fun angle(points: ArrayList<Point2F>, ai: Int, bi: Int, ci: Int): Double {
    val halfAngle = halfAngle(points, ai, bi, ci)
    return Math.abs(if (halfAngle < 0.0f) {
        halfAngle
    } else {
        -(Math.PI / 2) - ((Math.PI / 2.0) - halfAngle)
    })
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

private class EdgeNode(var p1: Int, var p2: Int, var t1: TriNode, var t2: TriNode)

private class TriNode(var p1: Int, var p2: Int, var p3: Int, val edges: ArrayList<EdgeNode> = ArrayList())

private fun flipEdges(vertices: PointSet2F, triangles: LinkedHashSet<Set<Int>>): LinkedHashSet<Set<Int>> {
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
    edgeMap.filter { it.value.size == 2 }.entries.forEach {
        val edge = it.key.toList()
        val edgeNode = EdgeNode(edge[0], edge[1], it.value[0], it.value[1])
        edgeNode.t1.edges.add(edgeNode)
        edgeNode.t2.edges.add(edgeNode)
        edgeNodes.add(edgeNode)
    }
    var iterations = 0
    var flips = 1
    while (flips > 0 && iterations < 100) {
        flips = 0
        edgeNodes.forEach { edgeNode ->
            val tri1 = edgeNode.t1
            val tri2 = edgeNode.t2
            val peaks = mutableSetOf(tri1.p1, tri1.p2, tri1.p3, tri2.p1, tri2.p2, tri2.p3)
            val quad = ArrayList(peaks.map { vertices[it]!! })
            peaks.remove(edgeNode.p1)
            peaks.remove(edgeNode.p2)
            val peakLineIds = peaks.toList()
            val baseLine = LineSegment2F(vertices[edgeNode.p1]!!, vertices[edgeNode.p2]!!)
            val peakLine = LineSegment2F(vertices[peakLineIds[0]]!!, vertices[peakLineIds[1]]!!)
            if (hasCollinearTriangle(vertices, tri1, tri2) || (baseLine.intersects(peakLine) && !containsCollinearPoints(quad) && peakLine.length2 < baseLine.length2)) {
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
                flips++
            }
        }
        iterations++
    }
    triangles.clear()
    triNodes.forEach {
        triangles.add(setOf(it.p1, it.p2, it.p3))
    }
    return triangles
}

private fun hasCollinearTriangle(vertices: PointSet2F, tri1: TriNode, tri2: TriNode) = isCollinearTriangle(vertices, tri1) || isCollinearTriangle(vertices, tri2)

private fun isCollinearTriangle(vertices: PointSet2F, triangle: TriNode) = (vertices[triangle.p2]!! - vertices[triangle.p1]!!).cross(vertices[triangle.p3]!! - vertices[triangle.p1]!!) == 0.0f

private fun containsCollinearPoints(points: ArrayList<Point2F>): Boolean {
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
    val edgeSkeleton = ArrayList(edgeSkeletonIn)
    val riverSkeleton = ArrayList(riverSkeletonIn)
    if (debug) {
        draw(debugResolution, "debug-buildMesh1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
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
    unTwistEdges(edgeSkeleton)
    moveRiverInsideBorder(globalVertices, edgeSkeleton, riverSkeleton)
    unTwistEdges(riverSkeleton)
    if (debug) {
        draw(debugResolution, "debug-buildMesh2-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
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
        draw(debugResolution, "debug-buildMesh3-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
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
    val meshPoints = PointSet2F()
    meshPoints.addAll(edgeSkeleton.flatMap { listOf(it.a, it.b) })
    meshPoints.addAll(riverSkeleton.flatMap { listOf(it.a, it.b) })
    val edges = LinkedHashSet<Pair<Int, Int>>()
    fun edge(a: Int, b: Int) = edges.add(Pair(Math.min(a, b), Math.max(a, b)))
    edgeSkeleton.forEach {
        edge(meshPoints[it.a], meshPoints[it.b])
    }
    riverSkeleton.forEach {
        edge(meshPoints[it.a], meshPoints[it.b])
    }
    val polygons = getPolygonEdgeSets(meshPoints, edges)
    if (debug) {
        draw(debugResolution, "debug-buildMesh4-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
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
    if (debug) {
        draw(debugResolution, "debug-buildMesh5-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            polygons.forEach {
                it.forEach {
                    drawEdge(vertices[it.first], vertices[it.second])
                }
            }
        }
        breakPoint()
    }
    val triangles = LinkedHashSet<Set<Int>>()
    polygons.forEach {
        triangles.addAll(triangulatePolygon(meshPoints, it))
    }
    if (debug) {
        draw(debugResolution, "debug-buildMesh6-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
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

private fun unTwistEdges(skeleton: ArrayList<LineSegment3F>) {
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
                draw(debugResolution, "debug-unTwistEdges-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(skeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(skeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
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
}

private fun closeEdge(edges: ArrayList<LineSegment3F>) {
    if (edges.first().a.epsilonEquals(edges.last().b)) {
        return
    }
    val unmodified = Polygon2F.fromUnsortedEdges(edges.map { LineSegment2F(it.a, it.b) })
    if (unmodified.isClosed) {
        return
    }
    val newEdges = Polygon2F(unmodified.points, true).edges.map { LineSegment3F(it.a as Point3F, it.b as Point3F) }
    if (debug) {
        draw(debugResolution, "debug-closeEdge-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(edges.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edges.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
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
            val a = globalVertices[it.a]
            val b = globalVertices[it.b]
            if (!borderPoints.contains(a) && !containsPoint(globalVertices, polygon, a)) {
                dropVertices.add(a)
            }
            if (!borderPoints.contains(b) && !containsPoint(globalVertices, polygon, b)) {
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
                    newRiverSkeleton.add(LineSegment3F(globalVertices[findSuitableReplacement(dropMap, line.first, line.second)] as Point3F, globalVertices[line.second] as Point3F))
                } else {
                    newRiverSkeleton.add(LineSegment3F(globalVertices[line.first] as Point3F, globalVertices[findSuitableReplacement(dropMap, line.second, line.first)] as Point3F))
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

private fun findSuitableReplacement(dropMap: HashMap<Int, ArrayList<Int>>, toReplace: Int, cantUse: Int): Int {
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
    throw GeometryException("unable to reconnect segment")
}

private fun getPolygonEdgeSets(meshPoints: PointSet2F, edges: Collection<Pair<Int, Int>>, putNonCyclesInCycles: Boolean = true): ArrayList<ArrayList<Pair<Int, Int>>> {
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
                orderedNonCycleSegments.add(orderSegment(it))
            }
            if (putNonCyclesInCycles) {
                orderedNonCycleSegments.forEach {
                    val (splicePoint, containingCycle) = findContainingCycle(meshPoints, segmentPaths, it)
                    if (splicePoint != null && containingCycle != null) {
                        newEdges.add(findSuitableSpliceEdge(meshPoints, orderedNonCycleSegments, containingCycle, it, splicePoint))
                    }
                }
            } else {
                segmentPaths.addAll(orderedNonCycleSegments)
            }
        }
        allPaths.addAll(segmentPaths)
    }
    if (newEdges.isEmpty()) {
        return allPaths
    } else {
        return getPolygonEdgeSets(meshPoints, edges + newEdges, putNonCyclesInCycles)
    }
}

private fun containsPoint(meshPoints: PointSet2F, polygon: ArrayList<Pair<Int, Int>>, id: Int): Boolean {
    val point = meshPoints[id]!!
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

private fun  getConnectedSegments(edges: Collection<Pair<Int, Int>>): List<Set<Set<Int>>> {
    val fullSet = LinkedHashSet(edges.map { setOf(it.first, it.second) }.filter { it.size == 2 })
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
                draw(debugResolution * 4, "debug-triangulatePolygon1-${debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE, 30.0f, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
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

fun max(a: Int, b: Int, c: Int) = Math.max(Math.max(a, b), c)

fun min(a: Int, b: Int, c: Int) = Math.min(Math.min(a, b), c)

fun min(a: Float, b: Float, c: Float) = Math.min(Math.min(a, b), c)

fun max(a: Float, b: Float, c: Float) = Math.max(Math.max(a, b), c)

fun clamp(min: Float, max: Float, f: Float) = Math.min(Math.max(min, f), max)

