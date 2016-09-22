package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.distance2Between
import com.grimfox.gec.util.Utils.doesEdgeIntersect
import com.grimfox.gec.util.Utils.linesIntersect
import java.util.*

class Polygon(val points: List<Point>, val isClosed: Boolean) {

    val bounds: Pair<Point, Point> by lazy {
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
        Pair(Point(xMin, yMin), Point(xMax, yMax))
    }

    val edges: List<Pair<Point, Point>> by lazy {
        val edges = ArrayList<Pair<Point, Point>>()
        for (i in 1..points.size - if (isClosed) 0 else 1) {
            edges.add(Pair(points[i - 1], points[i % points.size]))
        }
        edges
    }

    fun doesEdgeIntersect(edge: Pair<Point, Point>): Boolean {
        if (!bounds.doesEdgeIntersect(edge)) {
            return false
        } else {
            edges.forEach {
                if (linesIntersect(edge, it)) {
                    return true
                }
            }
        }
        return false
    }

    fun doesEdgeIntersect(other: Polygon): Boolean {
        edges.forEach {
            if (other.doesEdgeIntersect(it)) {
                return true
            }
        }
        return false
    }

    fun distance2Between(point: Point): Float {
        var minDist = Float.MAX_VALUE
        edges.forEach {
            val localDist = distance2Between(it, point)
            if (localDist == 0.0f) {
                return localDist
            }
            if (localDist < minDist) {
                minDist = localDist
            }
        }
        return minDist
    }

    fun distance2Between(edge: Pair<Point, Point>): Float {
        var minDist = Float.MAX_VALUE
        edges.forEach {
            val localDist = distance2Between(edge, it)
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