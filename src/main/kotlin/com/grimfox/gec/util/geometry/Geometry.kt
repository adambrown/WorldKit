package com.grimfox.gec.util.geometry

import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.drawing.draw
import com.grimfox.gec.util.drawing.drawEdge
import com.grimfox.gec.util.drawing.drawPoint
import com.grimfox.gec.util.printList
import java.awt.Color
import java.util.*

object Geometry {

    @JvmStatic fun main(vararg args: String) {
        val vertices = PointSet2F(points=arrayListOf(Point3F(x=0.7564781f, y=0.42845213f, z=0.0f), Point3F(x=0.7553417f, y=0.4286143f, z=0.0f), Point3F(x=0.7546523f, y=0.42940307f, z=0.0f), Point3F(x=0.75454897f, y=0.43044558f, z=0.0f), Point3F(x=0.7551896f, y=0.43161875f, z=0.0f), Point3F(x=0.75607f, y=0.4326246f, z=0.0f), Point3F(x=0.7564139f, y=0.43391618f, z=0.0f), Point3F(x=0.7550185f, y=0.43482316f, z=0.0f), Point3F(x=0.7534947f, y=0.43549234f, z=0.0f), Point3F(x=0.7533207f, y=0.4368464f, z=0.0f), Point3F(x=0.7529711f, y=0.43816614f, z=0.0f), Point3F(x=0.7513704f, y=0.43817058f, z=0.0f), Point3F(x=0.75015974f, y=0.43712327f, z=0.0f), Point3F(x=0.7499492f, y=0.43526706f, z=0.0f), Point3F(x=0.7502339f, y=0.43342072f, z=0.0f), Point3F(x=0.7489347f, y=0.43351996f, z=0.0f), Point3F(x=0.7483201f, y=0.4335875f, z=0.0f), Point3F(x=0.7479284f, y=0.43516508f, z=0.0f), Point3F(x=0.74724954f, y=0.43664196f, z=0.0f), Point3F(x=0.7454507f, y=0.43684426f, z=0.0f), Point3F(x=0.74370533f, y=0.43732464f, z=0.0f), Point3F(x=0.7451505f, y=0.44047138f, z=0.0f), Point3F(x=0.7462477f, y=0.44187608f, z=0.0f), Point3F(x=0.74646205f, y=0.44193253f, z=0.0f), Point3F(x=0.7468019f, y=0.4433211f, z=0.0f), Point3F(x=0.7470781f, y=0.44472376f, z=0.0f), Point3F(x=0.7461674f, y=0.4429437f, z=7.8960974E-4f), Point3F(x=0.7452915f, y=0.44109794f, z=0.0015792195f), Point3F(x=0.7435396f, y=0.43740645f, z=0.003158439f), Point3F(x=0.7426636f, y=0.4355607f, z=0.0039480487f), Point3F(x=0.7417877f, y=0.43371496f, z=0.0047376584f), Point3F(x=0.7409117f, y=0.4318692f, z=0.005527268f), Point3F(x=0.74003595f, y=0.43002337f, z=0.006316878f), Point3F(x=0.7409004f, y=0.4280459f, z=0.0074915714f), Point3F(x=0.74176484f, y=0.42606843f, z=0.008666265f), Point3F(x=0.7426293f, y=0.42409095f, z=0.009840958f), Point3F(x=0.74349374f, y=0.42211348f, z=0.011015652f), Point3F(x=0.7453025f, y=0.42262396f, z=0.010883305f), Point3F(x=0.74711126f, y=0.42313445f, z=0.010750959f), Point3F(x=0.74892f, y=0.42364493f, z=0.010618612f), Point3F(x=0.7507288f, y=0.4241554f, z=0.010486266f), Point3F(x=0.75253755f, y=0.4246659f, z=0.010353919f), Point3F(x=0.7543463f, y=0.42517638f, z=0.010221573f), Point3F(x=0.75615525f, y=0.42568693f, z=0.010089227f), Point3F(x=0.7568813f, y=0.42717412f, z=0.0050446135f), Point3F(x=0.75760734f, y=0.42866132f, z=0.0f), Point3F(x=0.7444155f, y=0.4392522f, z=0.0023688292f), Point3F(x=0.7440432f, y=0.43907464f, z=0.0f)))
        val polygon = arrayListOf(Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), Pair(4, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), Pair(8, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), Pair(12, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), Pair(16, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), Pair(20, 46), Pair(46, 21), Pair(21, 22), Pair(22, 23), Pair(23, 24), Pair(24, 25), Pair(25, 26), Pair(26, 27), Pair(27, 47), Pair(47, 28), Pair(28, 29), Pair(29, 30), Pair(30, 31), Pair(31, 32), Pair(32, 33), Pair(33, 34), Pair(34, 35), Pair(35, 36), Pair(36, 37), Pair(37, 38), Pair(38, 39), Pair(39, 40), Pair(40, 41), Pair(41, 42), Pair(42, 43), Pair(43, 44), Pair(44, 45), Pair(45, 0))

        draw(1024, "error1", Color.WHITE, 30.0f, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            polygon.forEach {
                val a = vertices[it.first]!!
                val b = vertices[it.second]!!
                drawEdge(a, b)
                drawPoint(a, 3)
                drawPoint(b, 3)
            }
        }

        val triangles = triangulatePolygon3(vertices, polygon)

        draw(1024, "error3", Color.WHITE, 30.0f, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
            graphics.color = Color.BLACK
            triangles.forEach {
                val tri = it.toList()
                val a = vertices[tri[0]]!!
                val b = vertices[tri[1]]!!
                val c = vertices[tri[2]]!!
                drawEdge(a, b)
                drawEdge(b, c)
                drawEdge(c, a)
                drawPoint(a, 3)
                drawPoint(b, 3)
                drawPoint(c, 3)
            }
            graphics.color = Color.RED
            polygon.forEach {
                val a = vertices[it.first]!!
                val b = vertices[it.second]!!
                drawEdge(a, b)
                drawPoint(a, 3)
                drawPoint(b, 3)
            }
        }
        println()
    }
}

private class CollinearPatch(val start: Point2F, val end: Point2F, val points: ArrayList<Point2F>)

fun triangulatePolygon3(vertices: PointSet2F, polygon: ArrayList<Pair<Int, Int>>): LinkedHashSet<Set<Int>> {
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

//            draw(1024, "error2", Color.WHITE, 30.0f, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
//                graphics.color = Color.BLACK
//                for (i in 1..points.size) {
//                    val a = points[i - 1]!!
//                    val b = points[i % points.size]!!
//                    drawEdge(a, b)
//                    drawPoint(a, 3)
//                    drawPoint(b, 3)
//                }
//                graphics.color = Color.RED
//                drawEdge(points[bi], points[ci])
//                drawPoint(points[bi], 4)
//                drawPoint(points[ci], 4)
//                graphics.color = Color.GREEN
//                points.forEach {
//                    drawPoint(it, 2)
//                }
//            }
//            println()

            points.removeAt(ai)
        } catch (e: Exception) {
            println(vertices)
            println(printList(polygon) { "Pair$it" })
            draw(1024, "error3", Color.WHITE, 30.0f, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
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
            println()
            throw e
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
    var angle = Float.MAX_VALUE
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
        val newWeight = angle(points, ai, bi, ci)
        if (newWeight < angle) {
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
    for (i in 1..points.size) {
        val ai = i % points.size
        val bi = i - 1
        val ci = (i + 1) % points.size
        val a = points[ai]
        val b = points[bi]
        val c = points[ci]
        val area = 0.5f * (-b.y * c.x + a.y * (-b.x + c.x) + a.x * (b.y - c.y) + b.x * c.y)
        if (area == 0.0f) {
            collinearIds.add(ai)
        }
        val normal = (a - b).cross(c - b) / area
        if (Math.abs(normal) < 0.1f) {
            collinearIds.add(ai)
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
//        if ((b - p).cross(c - p) == 0.0f) {
//            return true
//        }
        val s = area * (s1 + sx * p.x + sy * p.y)
        val t = area * (t1 + tx * p.x + ty * p.y)
        if (s > 0 && t > 0 && 1.0 - s - t > 0) {
            return true
        }
    }
    return false
}

private fun angle(points: ArrayList<Point2F>, ai: Int, bi: Int, ci: Int): Float {
    val a = points[ai]
    val b = points[bi]
    val c = points[ci]
    val ab = LineSegment2F(a, b)
    val ac = LineSegment2F(a, c)
    val bc = LineSegment2F(b, c)
    return Math.acos((ab.length2 + ac.length2 - bc.length2) / (2.0f * ab.length * ac.length).toDouble()).toFloat()
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
            if (baseLine.intersects(peakLine) && !containsCollinearPoints(quad) && peakLine.length2 < baseLine.length2) {
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
        val normal = (a - b).cross(c - b) / area
        if (Math.abs(normal) < 0.1f) {
            return true
        }
    }
    return false
}
