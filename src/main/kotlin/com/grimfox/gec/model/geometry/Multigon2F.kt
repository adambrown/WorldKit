package com.grimfox.gec.model.geometry

import java.util.*

class Multigon2F(val polygons: MutableList<Polygon2F>, val polySize: Int) {

    constructor(polygon: Polygon2F, segments: Int): this(polygon.split(segments), segments)

    fun intersects(edge: LineSegment2F): Boolean {
        polygons.forEachIndexed { _, polygon ->
            if (polygon.doesEdgeIntersect(edge).first) {
                return true
            }
        }
        return false
    }

    fun intersections(edge: LineSegment2F): ArrayList<Pair<Point2F, Int>> {
        val intersections = ArrayList<Pair<Point2F, Int>>()
        polygons.forEachIndexed { i, polygon ->
            val edgeOffset = i * polySize
            var (intersects, index) = polygon.doesEdgeIntersect(edge)
            val indices = ArrayList<Int>()
            while (intersects) {
                indices.add(index)
                val newTest = polygon.doesEdgeIntersect(edge, index + 1)
                intersects = newTest.first
                index = newTest.second
            }
            indices.forEach { index ->
                val intersection = edge.intersection(polygon.edges[index])
                if (intersection != null) {
                    intersections.add(Pair(intersection, edgeOffset + index))
                }
            }
        }
        return intersections
    }
}