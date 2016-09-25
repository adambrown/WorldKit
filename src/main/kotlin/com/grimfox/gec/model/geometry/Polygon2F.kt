package com.grimfox.gec.model.geometry

import java.util.*

class Polygon2F(val points: List<Point2F>, val isClosed: Boolean) {

    val bounds: Bounds2F by lazy {
        var xMin = Float.MAX_VALUE
        var xMax = Float.MIN_VALUE
        var yMin = Float.MIN_VALUE
        var yMax = Float.MAX_VALUE
        points.forEach {
            if (it.x < xMin) {
                xMin = it.x
            }
            if (it.x > xMax) {
                xMax = it.x
            }
            if (it.y < yMin) {
                yMin = it.y
            }
            if (it.y > yMax) {
                yMax = it.y
            }
        }
        Bounds2F(Point2F(xMin, yMin), Point2F(xMax, yMax))
    }

    val edges: List<LineSegment2F> by lazy {
        val edges = ArrayList<LineSegment2F>()
        for (i in 1..points.size - if (isClosed) 0 else 1) {
            edges.add(LineSegment2F(points[i - 1], points[i % points.size]))
        }
        edges
    }

    fun doesEdgeIntersect(edge: LineSegment2F): Boolean {
        if (!bounds.isWithin(edge)) {
            return false
        } else {
            edges.forEach {
                if (edge.intersects(it)) {
                    return true
                }
            }
        }
        return false
    }

    fun doesEdgeIntersect(other: Polygon2F): Boolean {
        edges.forEach {
            if (other.doesEdgeIntersect(it)) {
                return true
            }
        }
        return false
    }

    fun distance2(point: Point2F): Float {
        var minDist = Float.MAX_VALUE
        edges.forEach {
            val localDist = it.distance2(point)
            if (localDist == 0.0f) {
                return localDist
            }
            if (localDist < minDist) {
                minDist = localDist
            }
        }
        return minDist
    }

    fun distance2(edge: LineSegment2F): Float {
        var minDist = Float.MAX_VALUE
        edges.forEach {
            val localDist = edge.distance2(it)
            if (localDist == 0.0f) {
                return localDist
            }
            if (localDist < minDist) {
                minDist = localDist
            }
        }
        return minDist
    }
}