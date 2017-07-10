package com.grimfox.gec.util

import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.util.Utils.generateSemiUniformPointsD
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.geometry.Polygon
import com.grimfox.triangle.geometry.Triangle
import com.grimfox.triangle.geometry.Vertex
import java.io.*
import java.util.*

object Graphs {

    fun serialize(graph: Graph, outputStream: OutputStream) {
        if (outputStream is BufferedOutputStream) {
            DataOutputStream(outputStream)
        } else {
            DataOutputStream(outputStream.buffered())
        }.use { output ->
            output.writeLong(graph.seed)
            writeFloatArray(graph.vertexIdsToPoints, output)
            writeListListInt(graph.vertexToVertices, output)
            writeListListInt(graph.vertexToTriangles, output)
            writeFloatArray(graph.triangleToCenters, output)
            writeIntArray(graph.triangleToVertices, output)
            writeIntArray(graph.triangleToTriangles, output)
            writeNullablePositiveInt(graph.stride, output)
            val areas = FloatArray(graph.vertices.size)
            val borders = BooleanArray(graph.vertices.size)
            graph.vertices.forEach {
                val cell = it.cell
                areas[it.id] = cell.area
                borders[it.id] = cell.isBorder
            }
            writeNullableFloatArray(areas, output)
            writeNullableBooleanArray(borders, output)
        }
    }

    fun deserialize(inputStream: InputStream): Graph {
        val input = inputStream as? DataInputStream ?: if (inputStream is BufferedInputStream) {
            DataInputStream(inputStream)
        } else {
            DataInputStream(inputStream.buffered())
        }
        return input.use { stream ->
            val seed = stream.readLong()
            val vertexIdsToPoints = readFloatArray(stream)
            val vertexToVertices = readListListInt(stream)
            val vertexToTriangles = readListListInt(stream)
            val triangleToCenters = readFloatArray(stream)
            val triangleToVertices = readIntArray(stream)
            val triangleToTriangles = readIntArray(stream)
            val stride = readNullablePositiveInt(stream)
            val areas = readNullableFloatArray(stream)
            val borders = readNullableBooleanArray(stream)
            Graph(seed, vertexIdsToPoints, vertexToVertices, vertexToTriangles, triangleToCenters, triangleToVertices, triangleToTriangles, stride, areas, borders, false, false)
        }
    }

    private fun writeFloatArray(array: FloatArray, output: DataOutputStream) {
        output.writeInt(array.size)
        array.forEach {
            output.writeFloat(it)
        }
    }

    private fun writeListListInt(list: List<List<Int>>, output: DataOutputStream) {
        output.writeInt(list.size)
        list.forEach {
            output.writeInt(it.size)
            it.forEach {
                output.writeInt(it)
            }
        }
    }

    private fun writeIntArray(array: IntArray, output: DataOutputStream) {
        output.writeInt(array.size)
        array.forEach {
            output.writeInt(it)
        }
    }

    private fun writeNullablePositiveInt(i: Int?, output: DataOutputStream) {
        output.writeInt(i ?: -1)
    }

    private fun writeNullableFloatArray(array: FloatArray?, output: DataOutputStream) {
        output.writeInt(array?.size ?: -1)
        array?.forEach {
            output.writeFloat(it)
        }
    }

    private fun writeNullableBooleanArray(array: BooleanArray?, output: DataOutputStream) {
        output.writeInt(array?.size ?: -1)
        array?.forEach {
            output.writeBoolean(it)
        }
    }

    private fun readFloatArray(input: DataInputStream): FloatArray {
        val array = FloatArray(input.readInt())
        for (i in 0..array.size - 1) {
            array[i] = input.readFloat()
        }
        return array
    }

    private fun readListListInt(input: DataInputStream): List<List<Int>> {
        val outerListSize = input.readInt()
        val outerList = ArrayList<List<Int>>(outerListSize)
        for (i in 0..outerListSize - 1) {
            val innerListSize = input.readInt()
            val innerList = ArrayList<Int>(innerListSize)
            outerList.add(innerList)
            for (j in 0..innerListSize - 1) {
                innerList.add(input.readInt())
            }
        }
        return outerList
    }

    private fun readIntArray(input: DataInputStream): IntArray {
        val array = IntArray(input.readInt())
        for (i in 0..array.size - 1) {
            array[i] = input.readInt()
        }
        return array
    }

    private fun readNullablePositiveInt(input: DataInputStream): Int? {
        val raw: Int = input.readInt()
        return if (raw < 0) null else raw
    }

    private fun readNullableFloatArray(input: DataInputStream): FloatArray? {
        val size = readNullablePositiveInt(input) ?: return null
        val array = FloatArray(size)
        for (i in 0..size - 1) {
            array[i] = input.readFloat()
        }
        return array
    }

    private fun readNullableBooleanArray(input: DataInputStream): BooleanArray? {
        val size = readNullablePositiveInt(input) ?: return null
        val array = BooleanArray(size)
        for (i in 0..size - 1) {
            array[i] = input.readBoolean()
        }
        return array
    }

    fun generateGraph(stride: Int, seed: Long, constraint: Double, cacheVertices: Boolean = true, cacheTriangles: Boolean = true): Graph {
        val random = Random(seed)
        val polygon = Polygon(stride * stride)
        generateSemiUniformPointsD(stride, 1.0, random, constraint) { _, x, y ->
            polygon.add(Vertex(x, y))
        }
        val mesh = polygon.triangulate()
        return buildGraph(seed, mesh, stride, null, cacheVertices, cacheTriangles)
    }

    private fun buildGraph(seed: Long, mesh: Mesh, stride: Int? = null, width: Float? = null, cacheVertices: Boolean = true, cacheTriangles: Boolean = true): Graph {
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
        return Graph(seed, vertexIdsToPoints, vertexToVertices, vertexToTriangles, triangleToCenters, triangleToVertices, triangleToTriangles, stride, null, null, cacheVertices, cacheTriangles)
    }
}