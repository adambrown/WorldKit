package com.grimfox.gec.model

import java.util.*


class Graph(val stride: Int,
            val vertexIdsToPoints: FloatArray,
            val vertexToVertices: List<List<Int>>,
            val vertexToTriangles: List<List<Int>>,
            val triangleToCenters: FloatArray,
            val triangleToVertices: IntArray,
            val triangleToTriangles: IntArray) {

    var useVirtualConnections = false
    var virtualConnections = HashMap<Int, HashSet<Int>>()
    val vertices = Vertices()
    val triangles = Triangles()

    inner class Vertex(val id: Int) {

        val graph = this@Graph

        val point: Point by lazy { vertices.getPoint(id) }

        val adjacentVertices: List<Vertex> by lazy { vertices.getAdjacentVertices(id).map { Vertex(it) } }

        val adjacentTriangles: List<Triangle> by lazy { vertices.getAdjacentTriangles(id).map { Triangle(it) } }

        val cell: Cell by lazy { Cell(id) }

        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as Vertex
            return id == other.id && graph === other.graph
        }

        override fun hashCode() = id
    }

    inner class Triangle(val id: Int) {

        val graph = this@Graph

        val center: Point by lazy { triangles.getCenter(id) }

        val vertices: List<Vertex> by lazy { triangles.getVertices(id).map { Vertex(it) } }

        val a: Vertex by lazy { vertices[0] }

        val b: Vertex by lazy { vertices[1] }

        val c: Vertex by lazy { vertices[2] }

        val adjacentTriangles: List<Triangle> by lazy { triangles.getAdjacentTriangles(id).map { Triangle(it) } }

        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as Triangle
            return id == other.id && graph === other.graph
        }

        override fun hashCode() = id
    }

    inner class Cell(val id: Int) {

        val graph = this@Graph

        val vertex: Vertex by lazy {Vertex(id) }

        val border: List<Point> by lazy { vertex.adjacentTriangles.map { it.center } }

        val isClosed: Boolean by lazy { vertex.adjacentTriangles.first().adjacentTriangles.contains(vertex.adjacentTriangles.last()) }

        val isBorder: Boolean by lazy {
            if (!isClosed) return@lazy true
            border.forEach { if (it.x < 0.0f || it.x >= 1.0f || it.y < 0.0f || it.y >= 1.0f) return@lazy true }
            false
        }

        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as Cell
            return id == other.id && graph === other.graph
        }

        override fun hashCode() = id
    }

    inner class Vertices internal constructor() : Iterable<Vertex> {

        val size: Int by lazy { vertexIdsToPoints.size / 2 }

        operator fun get(id: Int): Vertex = Vertex(id)

        operator fun get(x: Int, y: Int): Vertex {
            return get(y * stride + x)
        }

        fun getPoint(id: Int): Point {
            val o = id * 2
            return Point(vertexIdsToPoints[o], vertexIdsToPoints[o + 1])
        }

        fun getAdjacentVertices(id: Int): List<Int> {
            if (useVirtualConnections) {
                val virtual = virtualConnections[id]
                if (virtual != null) {
                    return (vertexToVertices[id] + virtual).toList()
                }
            }
            return vertexToVertices[id]
        }

        fun getAdjacentTriangles(id: Int): List<Int> {
            return vertexToTriangles[id]
        }

        override fun iterator(): Iterator<Vertex> = (0..size - 1).map { Vertex(it) }.iterator()
    }

    inner class Triangles internal constructor() : Iterable<Triangle> {

        val size: Int by lazy { triangleToCenters.size / 2 }

        operator fun get(id: Int): Triangle = Triangle(id)

        fun getCenter(id: Int): Point {
            val o = id * 2
            return Point(triangleToCenters[o], triangleToCenters[o + 1])
        }

        fun getVertices(id: Int): List<Int> {
            val o = id * 3
            return triangleToVertices.slice(o..o + 2).toList()
        }

        fun getAdjacentTriangles(id: Int): List<Int> {
            val o = id * 3
            return triangleToTriangles.slice(o..o + 2).filter { it >= 0 }.toList()
        }

        override fun iterator(): Iterator<Triangle> = (0..size - 1).map { Triangle(it) }.iterator()
    }

    fun getClosestPoint(point: Point, closePoints: Set<Int> = getClosePoints(point)): Int {
        var closestPoint: Int = -1
        var minD2 = Float.MAX_VALUE
        closePoints.forEach {
            val d2 = vertices.getPoint(it).distanceSquaredTo(point)
            if (d2 < minD2) {
                closestPoint = it
                minD2 = d2
            }
        }
        return closestPoint
    }

    fun getClosePoints(point: Point, expansions: Int = 3): Set<Int> {
        val strideMinus1 = stride - 1
        val gridX = Math.round(point.x * (strideMinus1))
        val gridY = Math.round(point.y * (strideMinus1))
        val seed = vertices[gridX, gridY].id
        val nearPoints = HashSet<Int>()
        nearPoints.add(seed)
        var nextPoints = HashSet<Int>(nearPoints)
        for (i in 0..expansions - 1) {
            val newPoints = HashSet<Int>()
            nextPoints.forEach {
                newPoints.addAll(vertices.getAdjacentVertices(it))
            }
            newPoints.removeAll(nearPoints)
            nearPoints.addAll(newPoints)
            nextPoints = newPoints
        }
        return nearPoints
    }

}