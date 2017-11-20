package com.grimfox.gec.model

import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.model.geometry.Polygon2F.Companion.fromUnsortedEdges
import java.util.*
import kotlin.collections.LinkedHashSet


class Graph(val seed: Long,
            val vertexIdsToPoints: FloatArray,
            val vertexToVertices: List<List<Int>>,
            val vertexToTriangles: List<List<Int>>,
            val triangleToCenters: FloatArray,
            val triangleToVertices: IntArray,
            val triangleToTriangles: IntArray,
            val stride: Int? = null,
            val areas: FloatArray? = null,
            val borders: BooleanArray? = null,
            cacheVertices: Boolean = true,
            cacheTriangles: Boolean = true,
            cacheAreas: Boolean = areas != null,
            cacheBorders: Boolean = borders != null) {

    var useVirtualConnections = false
    var virtualConnections = HashMap<Int, LinkedHashSet<Int>>()
    val vertices = Vertices()
    val triangles = Triangles()

    private val vertexFactory = if (cacheVertices) CachingVertexFactory() else DumbVertexFactory()
    private val triangleFactory = if (cacheTriangles) CachingTriangleFactory() else DumbTriangleFactory()
    private val areaFactory = if (cacheAreas) CachingAreaFactory() else DumbAreaFactory()
    private val borderFactory = if (cacheBorders) CachingBorderFactory() else DumbBorderFactory()

    companion object {
        internal val bounds = Bounds2F(Point2F(0.0f, 0.0f), Point2F(1.0f, 1.0f))
    }

    private interface VertexFactory {

        fun newVertex(id: Int): Vertex
    }

    private interface TriangleFactory {

        fun newTriangle(id: Int): Triangle
    }

    private interface AreaFactory {

        fun getArea(cell: Cell): Float

        fun getArea(id: Int): Float
    }

    private interface BorderFactory {

        fun isBorder(cell: Cell): Boolean

        fun isBorder(id: Int): Boolean
    }

    private inner class CachingVertexFactory: VertexFactory {

        private val vertexCache: Array<Vertex?> = arrayOfNulls(vertices.size)

        override fun newVertex(id: Int): Vertex {
            var vertex = vertexCache[id]
            if (vertex == null) {
                vertex = Vertex(id)
                vertexCache[id] = vertex
            }
            return vertex
        }
    }

    private inner class DumbVertexFactory: VertexFactory {

        override fun newVertex(id: Int): Vertex {
            return Vertex(id)
        }
    }

    private inner class CachingTriangleFactory: TriangleFactory {

        private val triangleCache: Array<Triangle?> = arrayOfNulls(triangles.size)

        override fun newTriangle(id: Int): Triangle {
            var triangle = triangleCache[id]
            if (triangle == null) {
                triangle = Triangle(id)
                triangleCache[id] = triangle
            }
            return triangle
        }
    }

    private inner class DumbTriangleFactory: TriangleFactory {

        override fun newTriangle(id: Int): Triangle {
            return Triangle(id)
        }
    }

    private inner class CachingAreaFactory: AreaFactory {

        val areas: FloatArray = this@Graph.areas!!

        override fun getArea(cell: Cell): Float {
            return areas[cell.id]
        }

        override fun getArea(id: Int): Float {
            return areas[id]
        }
    }

    private inner class DumbAreaFactory: AreaFactory {

        override fun getArea(cell: Cell): Float {
            val border = cell.border
            var sum1 = 0.0f
            var sum2 = 0.0f
            for (i in 1..border.size) {
                val p1 = border[i - 1]
                val p2 = border[i % border.size]
                sum1 += p1.x * p2.y
                sum2 += p1.y * p2.x
            }
            return Math.abs((sum1 - sum2) / 2)
        }

        override fun getArea(id: Int): Float {
            return getArea(vertexFactory.newVertex(id).cell)
        }
    }

    private inner class CachingBorderFactory: BorderFactory {

        val borders: BooleanArray = this@Graph.borders!!

        override fun isBorder(cell: Cell): Boolean {
            return borders[cell.id]
        }

        override fun isBorder(id: Int): Boolean {
            return borders[id]
        }
    }

    private inner class DumbBorderFactory: BorderFactory {

        override fun isBorder(cell: Cell): Boolean {
            if (!cell.isClosedRaw) return true
            cell.borderRaw.forEach { if (it.x < 0.0f || it.x >= 1.0f || it.y < 0.0f || it.y >= 1.0f) return true }
            return false
        }

        override fun isBorder(id: Int): Boolean {
            return isBorder(vertexFactory.newVertex(id).cell)
        }
    }

    private fun newVertex(id: Int): Vertex {
        return vertexFactory.newVertex(id)
    }

    private fun newTriangle(id: Int): Triangle {
        return triangleFactory.newTriangle(id)
    }

    inner class CellEdge(val tri1: Triangle, val tri2: Triangle) {

        val graph = this@Graph

        val length2: Float by lazy {
            tri1.center.distance2(tri2.center)
        }

        val length: Float by lazy {
            Math.sqrt(length2.toDouble()).toFloat()
        }

        fun intersects(other: CellEdge): Boolean {
            return LineSegment2F(tri1.center, tri2.center).intersectsOrTouches(LineSegment2F(other.tri1.center, other.tri2.center))
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as CellEdge
            if (graph !== other.graph) return false
            if (tri1.id == other.tri1.id && tri2.id == other.tri2.id) return true
            if (tri1.id == other.tri2.id && tri2.id == other.tri1.id) return true
            return false
        }

        private val hashCode by lazy {
            val id1 = Math.min(tri1.id, tri2.id)
            val id2 = Math.max(tri1.id, tri2.id)
            val p1hc = id1.hashCode()
            val p2hc = id2.hashCode()
            31 * p1hc + p2hc
        }

        override fun hashCode(): Int = hashCode
    }

    inner class Vertex(val id: Int) {

        val graph = this@Graph

        val point: Point2F by lazy { vertices.getPoint(id) }

        val adjacentVertices: List<Vertex> by lazy { vertices.getAdjacentVertices(id).map { newVertex(it) } }

        val adjacentTriangles: List<Triangle> by lazy { vertices.getAdjacentTriangles(id).map { newTriangle(it) } }

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

        val center: Point2F by lazy { triangles.getCenter(id) }

        val vertices: List<Vertex> by lazy {
            val (v1, v2, v3) = triangles.getVertices(id)
            listOf(newVertex(v1), newVertex(v2), newVertex(v3))
        }

        val a: Vertex by lazy { vertices[0] }

        val b: Vertex by lazy { vertices[1] }

        val c: Vertex by lazy { vertices[2] }

        val adjacentTriangles: List<Triangle> by lazy { triangles.getAdjacentTriangles(id).map { newTriangle(it) } }

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

        internal val borderRaw: List<Point2F> by lazy { vertex.adjacentTriangles.map { it.center } }

        internal val isClosedRaw: Boolean by lazy { vertex.adjacentTriangles.size > 2 && vertex.adjacentTriangles.first().adjacentTriangles.contains(vertex.adjacentTriangles.last()) }

        private val borderEdgesRaw: List<CellEdge> by lazy {
            val edges = ArrayList<CellEdge>()
            val adjacentTris = vertex.adjacentTriangles
            for (i in 1..adjacentTris.size - if (isClosedRaw) 0 else 1) {
                edges.add(CellEdge(adjacentTris[i - 1], adjacentTris[i % borderRaw.size]))
            }
            edges
        }

        private val borderEdgesComplete: ArrayList<Pair<LineSegment2F, Boolean>> by lazy {
            if (!isBorder) {
                ArrayList(borderEdgesRaw.map { Pair(LineSegment2F(it.tri1.center, it.tri2.center), true) })
            } else if (!isClosedRaw) {
                val edges = ArrayList<Pair<LineSegment2F, Boolean>>()
                val otherPoint1 = vertex.adjacentVertices.first().point
                val otherPoint2 = vertex.adjacentVertices.last().point
                val seg1 = LineSegment2F(borderRaw.first() + Vector2F(vertex.point, otherPoint1).getPerpendicular().getUnit(), borderRaw.first())
                val seg2 = LineSegment2F(borderRaw.last(), borderRaw.last() + Vector2F(otherPoint2, vertex.point).getPerpendicular().getUnit())
                edges.add(Pair(seg1, bounds.isWithin(seg1)))
                edges.addAll(borderEdgesRaw.map {
                    val line = LineSegment2F(it.tri1.center, it.tri2.center)
                    Pair(line, bounds.isWithin(line))
                })
                edges.add(Pair(seg2, bounds.isWithin(seg2)))
                edges
            } else {
                val borderEdges = ArrayList<Pair<LineSegment2F, Boolean>>()
                borderEdgesRaw.forEach {
                    val line = LineSegment2F(it.tri1.center, it.tri2.center)
                    borderEdges.add(Pair(line, bounds.isWithin(line)))
                }
                borderEdges
            }
        }

        val borderEdges: List<LineSegment2F> by lazy {
            if (!isBorder) {
                borderEdgesComplete.map { it.first }
            } else {
                val edges = ArrayList<LineSegment2F>()
                borderEdgesComplete.forEach {
                    if (it.second) {
                        edges.add(it.first)
                    }
                }
                var lastIntersection: Pair<Int, Point2F?>? = null
                var insertIndex = -1
                var newLine1: LineSegment2F? = null
                var newLine2: LineSegment2F? = null
                for (i in 0..edges.size - 1) {
                    val edge = edges[i]
                    val intersection = bounds.singleIntersection(edge)
                    if (intersection.first > -1) {
                        if (bounds.isWithin(edge.a)) {
                            edge.b = intersection.second!!
                        } else {
                            edge.a = intersection.second!!
                        }
                        if (lastIntersection == null) {
                            lastIntersection = intersection
                            insertIndex = i + 1
                        } else {
                            if (insertIndex == i) {
                                val side1 = lastIntersection.first
                                val side2 = intersection.first
                                if (side1 == side2) {
                                    newLine1 = LineSegment2F(lastIntersection.second!!, intersection.second!!)
                                } else if ((side1 == 0 && side2 == 1) || (side2 == 0 && side1 == 1)) {
                                    newLine1 = LineSegment2F(lastIntersection.second!!, bounds.c2)
                                    newLine2 = LineSegment2F(bounds.c2, intersection.second!!)
                                } else if ((side1 == 1 && side2 == 2) || (side2 == 1 && side1 == 2)) {
                                    newLine1 = LineSegment2F(lastIntersection.second!!, bounds.c3)
                                    newLine2 = LineSegment2F(bounds.c3, intersection.second!!)
                                } else if ((side1 == 2 && side2 == 3) || (side2 == 2 && side1 == 3)) {
                                    newLine1 = LineSegment2F(lastIntersection.second!!, bounds.c4)
                                    newLine2 = LineSegment2F(bounds.c4, intersection.second!!)
                                } else {
                                    newLine1 = LineSegment2F(lastIntersection.second!!, bounds.c1)
                                    newLine2 = LineSegment2F(bounds.c1, intersection.second!!)
                                }
                            } else {
                                val side1 = intersection.first
                                val side2 = lastIntersection.first
                                if (side1 == side2) {
                                    newLine1 = LineSegment2F(intersection.second!!, lastIntersection.second!!)
                                } else if ((side1 == 0 && side2 == 1) || (side2 == 0 && side1 == 1)) {
                                    newLine1 = LineSegment2F(intersection.second!!, bounds.c2)
                                    newLine2 = LineSegment2F(bounds.c2, lastIntersection.second!!)
                                } else if ((side1 == 1 && side2 == 2) || (side2 == 1 && side1 == 2)) {
                                    newLine1 = LineSegment2F(intersection.second!!, bounds.c3)
                                    newLine2 = LineSegment2F(bounds.c3, lastIntersection.second!!)
                                } else if ((side1 == 2 && side2 == 3) || (side2 == 2 && side1 == 3)) {
                                    newLine1 = LineSegment2F(intersection.second!!, bounds.c4)
                                    newLine2 = LineSegment2F(bounds.c4, lastIntersection.second!!)
                                } else {
                                    newLine1 = LineSegment2F(intersection.second!!, bounds.c1)
                                    newLine2 = LineSegment2F(bounds.c1, lastIntersection.second!!)
                                }
                                insertIndex = 0
                            }
                        }
                    }
                }
                if (insertIndex > -1 && newLine1 != null) {
                    if (newLine2 != null) {
                        edges.add(insertIndex, newLine2)
                    }
                    edges.add(insertIndex, newLine1)
                }
                edges
            }
        }

        val border: List<Point2F> by lazy {
            borderEdges.map { it.a }
        }

        val isBorder: Boolean by lazy { borderFactory.isBorder(this) }

        val area: Float by lazy { areaFactory.getArea(this) }

        fun sharedEdge(other: Cell, useTriangles: Boolean): LineSegment2F? {
            if (useTriangles) {
                val adjacent = vertices.getAdjacentTriangles(id)
                val otherAdjacent = vertices.getAdjacentTriangles(other.id)
                var first: Int? = null
                var second: Int? = null
                var lastWasVert: Boolean = false
                for (vert in adjacent) {
                    if (otherAdjacent.contains(vert)) {
                        lastWasVert = true
                        if (first == null) {
                            first = vert
                            continue
                        } else {
                            second = vert
                            break
                        }
                    } else if (lastWasVert) {
                        break
                    }
                    lastWasVert = false
                }
                if (second == null && lastWasVert) {
                    val vert = adjacent.last()
                    if (otherAdjacent.contains(vert)) {
                        second = first
                        first = vert
                    }
                }
                if (second == null) {
                    return null
                }
                return LineSegment2F(triangles[first!!].center, triangles[second].center)
            } else {
                borderEdges.forEach { edge1 ->
                    other.borderEdges.forEach { edge2 ->
                        if (edge1.epsilonEquals(edge2)) {
                            return edge1
                        }
                    }
                }
                return null
            }
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

        operator fun get(id: Int): Vertex = newVertex(id)

        operator fun get(x: Int, y: Int): Vertex {
            if (stride == null) throw UnsupportedOperationException()
            return get(y * stride + x)
        }

        fun getPoint(id: Int): Point2F {
            val o = id * 2
            return Point2F(vertexIdsToPoints[o], vertexIdsToPoints[o + 1])
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

        fun isBorder(id: Int): Boolean {
            return borderFactory.isBorder(id)
        }

        fun getArea(id: Int): Float {
            return areaFactory.getArea(id)
        }

        override fun iterator(): Iterator<Vertex> = (0..size - 1).asSequence().map { newVertex(it) }.iterator()
    }

    inner class Triangles internal constructor() : Iterable<Triangle> {

        val size: Int by lazy { triangleToCenters.size / 2 }

        operator fun get(id: Int): Triangle = newTriangle(id)

        fun getCenter(id: Int): Point2F {
            val o = id * 2
            return Point2F(triangleToCenters[o], triangleToCenters[o + 1])
        }

        fun getVertices(id: Int): Triple<Int, Int, Int> {
            var o = id * 3
            return Triple(triangleToVertices[o++], triangleToVertices[o++], triangleToVertices[o])
//            return triangleToVertices.slice(o..o + 2).toList()
        }

        fun getAdjacentTriangles(id: Int): List<Int> {
            val o = id * 3
            return triangleToTriangles.slice(o..o + 2).filter { it >= 0 }.toList()
        }

        override fun iterator(): Iterator<Triangle> = (0..size - 1).asSequence().map { newTriangle(it) }.iterator()
    }

    fun getClosestPoint(point: Point2F, closePoints: Set<Int> = getClosePoints(point)): Int {
        var closestPoint: Int = -1
        var minD2 = Float.MAX_VALUE
        closePoints.forEach {
            val d2 = vertices.getPoint(it).distance2(point)
            if (d2 < minD2) {
                closestPoint = it
                minD2 = d2
            }
        }
        return closestPoint
    }

    fun getClosePoints(point: Point2F, expansions: Int = 3, includeLower: Boolean = true): Set<Int> {
        if (stride == null) throw UnsupportedOperationException()
        val strideMinus1 = stride - 1
        val gridX = Math.round(point.x * (strideMinus1))
        val gridY = Math.round(point.y * (strideMinus1))
        val seed = vertices[gridX, gridY].id
        val nearPoints = LinkedHashSet<Int>()
        nearPoints.add(seed)
        var nextPoints = LinkedHashSet<Int>(nearPoints)
        for (i in 0..expansions - 1) {
            val newPoints = LinkedHashSet<Int>()
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

    fun getClosePoints(vertexId: Int, expansions: Int = 3, includeLower: Boolean = true): Set<Int> {
        if (stride == null) throw UnsupportedOperationException()
        val nearPoints = LinkedHashSet<Int>()
        nearPoints.add(vertexId)
        var nextPoints = LinkedHashSet<Int>(nearPoints)
        for (i in 0..expansions - 1) {
            val newPoints = LinkedHashSet<Int>()
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

    fun getClosePointSets(vertexId: Int, vararg importantExpansions: Int): List<Set<Int>> {
        val expansions = ArrayList(importantExpansions.toList())
        expansions.sort()
        if (stride == null) throw UnsupportedOperationException()
        val nearPoints = LinkedHashSet<Int>()
        nearPoints.add(vertexId)
        var nextPoints = LinkedHashSet<Int>(nearPoints)
        val savedPoints = LinkedHashSet<Int>()
        val expansionSets = ArrayList<Set<Int>>(expansions.size)
        for (i in 0..expansions.last() - 1) {
            if (i == expansions.first()) {
                expansions.removeAt(0)
                val currentLevel = LinkedHashSet<Int>(nearPoints)
                currentLevel.removeAll(savedPoints)
                savedPoints.addAll(currentLevel)
                expansionSets.add(currentLevel)
            }
            val newPoints = LinkedHashSet<Int>()
            nextPoints.forEach {
                newPoints.addAll(vertices.getAdjacentVertices(it))
            }
            newPoints.removeAll(nearPoints)
            nearPoints.addAll(newPoints)
            nextPoints = newPoints
        }
        nearPoints.removeAll(savedPoints)
        expansionSets.add(nearPoints)
        return expansionSets
    }

    fun getPointsWithinRadius(point: Point2F, radius: Float): Set<Int> {
        if (stride == null) throw UnsupportedOperationException()
        val gridSize = 1.0f / stride
        val expansions = Math.ceil((radius / gridSize).toDouble()).toInt() + 1
        val testPoints = getClosePoints(point, expansions)
        val pointsWithin = LinkedHashSet<Int>()
        val r2 = radius * radius
        testPoints.forEach {
            if (point.distance2(vertices[it].point) <= r2) {
                pointsWithin.add(it)
            }
        }
        return pointsWithin
    }

    fun getClosePointDegrees(id: Int, expansions: Int = 1): ArrayList<LinkedHashSet<Int>> {
        val degrees = ArrayList<LinkedHashSet<Int>>()
        val nearPoints = LinkedHashSet<Int>()
        nearPoints.add(id)
        var nextPoints = LinkedHashSet<Int>(nearPoints)
        for (i in 0..expansions - 1) {
            val newPoints = LinkedHashSet<Int>()
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

    fun getConnectedBodies(mask: LinkedHashSet<Int>): ArrayList<LinkedHashSet<Int>> {
        val bodies = ArrayList<LinkedHashSet<Int>>()
        val unconnected = LinkedHashSet<Int>(mask)
        while (unconnected.isNotEmpty()) {
            val seed = unconnected.first()
            unconnected.remove(seed)
            val body = getConnectedIds(seed, mask)
            unconnected.removeAll(body)
            bodies.add(body)
        }
        return bodies
    }

    fun getConnectedIds(seed: Int, mask: LinkedHashSet<Int>): LinkedHashSet<Int> {
        val connectedPoints = LinkedHashSet<Int>()
        connectedPoints.add(seed)
        var nextPoints = LinkedHashSet<Int>(connectedPoints)
        while (nextPoints.isNotEmpty()) {
            val newPoints = LinkedHashSet<Int>()
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

    fun findBorderIds(ids: LinkedHashSet<Int>, mask: LinkedHashSet<Int>? = null, negate: Boolean = false): LinkedHashSet<Int> {
        val borderIds = LinkedHashSet<Int>()
        ids.forEach { id ->
            val adjacents = vertices.getAdjacentVertices(id)
            for (i in 0..adjacents.size - 1) {
                val adjacent = adjacents[i]
                if (!ids.contains(adjacent)) {
                    if (mask == null) {
                        borderIds.add(id)
                        break
                    } else {
                        if (!negate && mask.contains(adjacent)) {
                            borderIds.add(id)
                            break
                        } else if (negate && !mask.contains(adjacent)) {
                            borderIds.add(id)
                            break
                        }
                    }
                }
            }
        }
        return borderIds
    }

    fun findBorder(ids: LinkedHashSet<Int>, mask: LinkedHashSet<Int>? = null, negate: Boolean = false, splices: ArrayList<Pair<LineSegment2F, Point2F>>? = null, useTriangles: Boolean): ArrayList<Polygon2F> {
        val edges = findBorderEdges(ids, mask, negate, useTriangles)
        if (edges.isEmpty()) {
            return arrayListOf()
        }
        val edgeSegments = LineSegment2F.getConnectedEdgeSegments(edges, 0.0f)
        val borders = ArrayList<Polygon2F>()
        edgeSegments.forEach {
            borders.add(fromUnsortedEdges(it, splices, true))
        }
        return borders
    }

    fun findBorderEdges(ids: LinkedHashSet<Int>, mask: LinkedHashSet<Int>? = null, negate: Boolean = false, useTriangles: Boolean): List<LineSegment2F> {
        val borderIds = ArrayList<LineSegment2F>()
        ids.forEach { id ->
            vertices.getAdjacentVertices(id).forEach { adjacentId ->
                if (!ids.contains(adjacentId)) {
                    if (mask == null) {
                        addBorderEdge(borderIds, id, adjacentId, useTriangles)
                    } else {
                        if (!negate && mask.contains(adjacentId)) {
                            addBorderEdge(borderIds, id, adjacentId, useTriangles)
                        } else if (negate && !mask.contains(adjacentId)) {
                            addBorderEdge(borderIds, id, adjacentId, useTriangles)
                        }
                    }
                }
            }
        }
        return borderIds
    }

    private fun addBorderEdge(borderEdges: ArrayList<LineSegment2F>, id: Int, adjacentId: Int, useTriangles: Boolean) {
        val cell1 = vertices[id].cell
        val cell2 = vertices[adjacentId].cell
        val edge = cell1.sharedEdge(cell2, useTriangles)
        if (edge != null) {
            borderEdges.add(edge)
        }
    }
}