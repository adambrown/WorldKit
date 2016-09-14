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

    inner class CellEdge(val tri1: Triangle, val tri2: Triangle) {

        val graph = this@Graph

        val length2: Float by lazy {
            tri1.center.distanceSquaredTo(tri2.center)
        }

        val length: Float by lazy {
            Math.sqrt(length2.toDouble()).toFloat()
        }

        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as CellEdge
            if (graph !== other.graph) return false
            if (tri1.id == other.tri1.id && tri2.id == other.tri2.id) return true
            if (tri1.id == other.tri2.id && tri2.id == other.tri1.id) return true
            return false
        }

        private val hashCode by lazy {
            val p1hc = tri1.id.hashCode()
            val p2hc = tri2.id.hashCode()
            31 * Math.min(p1hc, p2hc) + Math.max(p1hc, p2hc)
        }

        override fun hashCode(): Int = hashCode
    }

    inner class Vertex(val id: Int) {

        val graph = this@Graph

        val point: Point by lazy { vertices.getPoint(id) }

        val adjacentVertices: List<Vertex> by lazy { vertices.getAdjacentVertices(id).map { Vertex(it) } }

        val adjacentTriangles: List<Triangle> by lazy { vertices.getAdjacentTriangles(id).map { Triangle(it) } }

        val cell: Cell by lazy { Cell(this) }

        override fun equals(other: Any?): Boolean {
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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as Triangle
            return id == other.id && graph === other.graph
        }

        override fun hashCode() = id
    }

    inner class Cell(val vertex: Vertex, val id: Int = vertex.id) {

        val graph = this@Graph

        val border: List<Point> by lazy { vertex.adjacentTriangles.map { it.center } }

        val isClosed: Boolean by lazy { vertex.adjacentTriangles.first().adjacentTriangles.contains(vertex.adjacentTriangles.last()) }

        val isBorder: Boolean by lazy {
            if (!isClosed) return@lazy true
            border.forEach { if (it.x < 0.0f || it.x >= 1.0f || it.y < 0.0f || it.y >= 1.0f) return@lazy true }
            false
        }

        val borderEdges: List<CellEdge> by lazy {
            val edges = ArrayList<CellEdge>()
            val adjacentTris = vertex.adjacentTriangles
            for (i in 1..adjacentTris.size - if (isClosed) 0 else 1) {
                edges.add(CellEdge(adjacentTris[i - 1], adjacentTris[i % border.size]))
            }
            edges
        }

        val area: Float by lazy {
            if (!isClosed) {
                0.0f
            } else {
                var sum1 = 0.0f
                var sum2 = 0.0f
                for (i in 1..border.size) {
                    val p1 = border[i - 1]
                    val p2 = border[i % border.size]
                    sum1 += p1.x * p2.y
                    sum2 += p1.y * p2.x
                }
                Math.abs((sum1 - sum2) / 2)
            }
        }

        fun sharedEdge(other: Cell): CellEdge? {
            borderEdges.forEach { edge1 ->
                other.borderEdges.forEach { edge2 ->
                    if (edge1 == edge2) {
                        return edge1
                    }
                }
            }
            return null
        }

        override fun equals(other: Any?): Boolean {
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

    fun getClosePoints(point: Point, expansions: Int = 3, includeLower: Boolean = true): Set<Int> {
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
        if (includeLower) {
            return nearPoints
        } else {
            return nextPoints
        }
    }

    fun getClosePointDegrees(id: Int, expansions: Int = 1): ArrayList<HashSet<Int>> {
        val degrees = ArrayList<HashSet<Int>>()
        val nearPoints = HashSet<Int>()
        nearPoints.add(id)
        var nextPoints = HashSet<Int>(nearPoints)
        for (i in 0..expansions - 1) {
            val newPoints = HashSet<Int>()
            nextPoints.forEach {
                newPoints.addAll(vertices.getAdjacentVertices(it))
            }
            newPoints.removeAll(nearPoints)
            nearPoints.addAll(newPoints)
            degrees.add(newPoints)
            nextPoints = newPoints
        }
        return degrees
    }

    fun getConnectedBodies(mask: HashSet<Int>): ArrayList<HashSet<Int>> {
        val bodies = ArrayList<HashSet<Int>>()
        val unconnected = HashSet<Int>(mask)
        while (unconnected.isNotEmpty()) {
            val seed = unconnected.first()
            unconnected.remove(seed)
            val body = getConnectedIds(seed, mask)
            unconnected.removeAll(body)
            bodies.add(body)
        }
        return bodies
    }

    fun getConnectedIds(seed: Int, mask: HashSet<Int>): HashSet<Int> {
        val connectedPoints = HashSet<Int>()
        connectedPoints.add(seed)
        var nextPoints = HashSet<Int>(connectedPoints)
        while (nextPoints.isNotEmpty()) {
            val newPoints = HashSet<Int>()
            nextPoints.forEach {
                vertices.getAdjacentVertices(it).forEach {
                    if (mask.contains(it)) {
                        newPoints.add(it)
                    }
                }
            }
            newPoints.removeAll(connectedPoints)
            connectedPoints.addAll(newPoints)
            nextPoints = newPoints
        }
        return connectedPoints
    }

    fun getConnectedEdgeSegments(edgeSet: HashSet<CellEdge>): ArrayList<HashSet<CellEdge>> {
        val segments = ArrayList<HashSet<CellEdge>>()
        val unconnected = HashSet<CellEdge>(edgeSet)
        while (unconnected.isNotEmpty()) {
            val seed = unconnected.first()
            unconnected.remove(seed)
            val segment = getConnectedEdges(seed, edgeSet)
            unconnected.removeAll(segment)
            segments.add(segment)
        }
        return segments
    }

    fun getConnectedEdges(edge: CellEdge, edgeSet: HashSet<CellEdge>): HashSet<CellEdge> {
        val connectedEdges = HashSet<CellEdge>()
        connectedEdges.add(edge)
        var nextEdges = HashSet<CellEdge>(connectedEdges)
        while (nextEdges.isNotEmpty()) {
            val newEdges = HashSet<CellEdge>()
            nextEdges.forEach { edge ->
                edgeSet.forEach {
                    if (edge.tri1.id == it.tri1.id
                            || edge.tri1.id == it.tri2.id
                            || edge.tri2.id == it.tri1.id
                            || edge.tri2.id == it.tri2.id) {
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
}