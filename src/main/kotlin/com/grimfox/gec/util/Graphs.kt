package com.grimfox.gec.util

import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.util.Utils.generateSemiUniformPointsD
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.*
import java.util.*

object Graphs {

    fun generateGraph(stride: Int, random: Random, constraint: Double): Graph {
        val polygon = Polygon(stride * stride)
        generateSemiUniformPointsD(stride, 1.0, random, constraint) { i, x, y ->
            polygon.add(Vertex(x, y))
        }
        val mesh = polygon.triangulate()
        return buildGraph(mesh, stride)
    }

    fun buildGraph(width: Float, points: List<Point2F>, stride: Int? = null): Graph {
        val polygon = Polygon(points.size)
        points.mapIndexedTo(polygon.points) { i, point ->
            val v = Vertex(point.x.toDouble(), point.y.toDouble())
            v.id = i
            v
        }
        val mesh = polygon.triangulate()
        return buildGraph(mesh, stride, width)
    }

    private fun buildGraph(mesh: Mesh, stride: Int? = null, width: Float? = null): Graph {
        mesh.renumber()
        val points: List<Point> = mesh.vertices
        val triangles: List<Triangle> = mesh.triangles
        val vertexIdsToPoints = FloatArray(points.size * 2)
        if (width == null) {
            points.forEachIndexed { i, point ->
                val o = i * 2
                vertexIdsToPoints[o] = point.x.toFloat()
                vertexIdsToPoints[o + 1] = point.y.toFloat()
            }
        } else {
            points.forEachIndexed { i, point ->
                val o = i * 2
                vertexIdsToPoints[o] = point.x.toFloat() / width
                vertexIdsToPoints[o + 1] = point.y.toFloat() / width
            }
        }
        val triangleToCenters = FloatArray(triangles.size * 2)
        val triangleToTriangles = IntArray(triangles.size * 3)
        val triangleToVertices = IntArray(triangles.size * 3)
        val vertexToTrianglesTemp = ArrayList<ArrayList<Int>>(points.size)
        for (i in 0..points.size - 1) {
            vertexToTrianglesTemp.add(ArrayList(5))
        }
        if (width == null) {
            triangles.forEachIndexed { i, triangle ->
                val vertex = mesh.findCircumcenter(triangle)
                var o = i * 2
                triangleToCenters[o++] = vertex.x.toFloat()
                triangleToCenters[o] = vertex.y.toFloat()
                o = i * 3
                triangleToTriangles[o++] = triangle.getNeighborId(0)
                triangleToTriangles[o++] = triangle.getNeighborId(1)
                triangleToTriangles[o] = triangle.getNeighborId(2)
                o = i * 3
                val a = triangle.getVertexId(0)
                val b = triangle.getVertexId(1)
                val c = triangle.getVertexId(2)
                triangleToVertices[o++] = a
                triangleToVertices[o++] = b
                triangleToVertices[o] = c
                vertexToTrianglesTemp[a].add(i)
                vertexToTrianglesTemp[b].add(i)
                vertexToTrianglesTemp[c].add(i)
            }
        } else {
            triangles.forEachIndexed { i, triangle ->
                val vertex = mesh.findCircumcenter(triangle)
                var o = i * 2
                triangleToCenters[o++] = vertex.x.toFloat() / width
                triangleToCenters[o] = vertex.y.toFloat() / width
                o = i * 3
                triangleToTriangles[o++] = triangle.getNeighborId(0)
                triangleToTriangles[o++] = triangle.getNeighborId(1)
                triangleToTriangles[o] = triangle.getNeighborId(2)
                o = i * 3
                val a = triangle.getVertexId(0)
                val b = triangle.getVertexId(1)
                val c = triangle.getVertexId(2)
                triangleToVertices[o++] = a
                triangleToVertices[o++] = b
                triangleToVertices[o] = c
                vertexToTrianglesTemp[a].add(i)
                vertexToTrianglesTemp[b].add(i)
                vertexToTrianglesTemp[c].add(i)
            }
        }
        val vertexToTriangles = ArrayList<List<Int>>(points.size)
        val vertexToVertices = ArrayList<List<Int>>(points.size)
        for (i in 0..points.size - 1) {
            val trianglesToOrder = vertexToTrianglesTemp[i].filter { it >= 0 }
            val hullEdges = ArrayList<Triple<Int, Int, Int>>(trianglesToOrder.size)
            trianglesToOrder.forEach {
                var o = it * 3
                val a = triangleToVertices[o++]
                val b = triangleToVertices[o++]
                val c = triangleToVertices[o]
                if (a == i) {
                    hullEdges.add(Triple(it, b, c))
                } else if (b == i) {
                    hullEdges.add(Triple(it, c, a))
                } else {
                    hullEdges.add(Triple(it, a, b))
                }
            }
            val newHullEdges = ArrayList<Triple<Int, Int, Int>>(trianglesToOrder.size)
            newHullEdges.add(hullEdges.removeAt(0))
            for (j in 0..hullEdges.size - 1) {
                for (k in 0..hullEdges.size - 1) {
                    val nextEdge = hullEdges[k]
                    if (newHullEdges.last().third == nextEdge.second) {
                        newHullEdges.add(hullEdges.removeAt(k))
                        break
                    } else if (newHullEdges.last().third == nextEdge.third) {
                        newHullEdges.add(hullEdges.removeAt(k).copy(second = nextEdge.third, third = nextEdge.second))
                        break
                    } else if (newHullEdges.first().second == nextEdge.second) {
                        newHullEdges.add(0, hullEdges.removeAt(k).copy(second = nextEdge.third, third = nextEdge.second))
                        break
                    } else if (newHullEdges.first().second == nextEdge.third) {
                        newHullEdges.add(0, hullEdges.removeAt(k))
                        break
                    }
                }
            }
            val adjacentTriangles = newHullEdges.map { it.first }.toMutableList()
            val adjacentPoints = newHullEdges.map { it.second }.toMutableList()
            val possibleExtraPoint = newHullEdges.last().third
            if (adjacentPoints.first() != possibleExtraPoint) {
                adjacentPoints.add(possibleExtraPoint)
            }
            val a = adjacentPoints[0]
            val b = adjacentPoints[1]
            val center = i
            val pa = points[a]
            val pb = points[b]
            val pc = points[center]
            if (mesh.findOrientation(pa, pb, pc) < 0.0) {
                adjacentTriangles.reverse()
                adjacentPoints.reverse()
            }
            vertexToTriangles.add(adjacentTriangles)
            vertexToVertices.add(adjacentPoints)
        }
        return Graph(vertexIdsToPoints, vertexToVertices, vertexToTriangles, triangleToCenters, triangleToVertices, triangleToTriangles, stride)
    }
}