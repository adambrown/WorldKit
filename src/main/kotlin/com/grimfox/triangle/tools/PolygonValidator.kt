package com.grimfox.triangle.tools

import com.grimfox.logging.LOG
import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.geometry.Polygon
import com.grimfox.triangle.geometry.Vertex
import java.util.*

object PolygonValidator {

    fun isConsistent(poly: Polygon): Boolean {
        val points = poly.points
        var horrors = 0
        var i = 0
        val count = points.size
        if (count < 3) {
            LOG.warn("Polygon must have at least 3 vertices.")
            return false
        }
        for (p in points) {
            if (java.lang.Double.isNaN(p.x) || java.lang.Double.isNaN(p.y)) {
                horrors++
                LOG.warn("Point $i has invalid coordinates.")
            } else if (java.lang.Double.isInfinite(p.x) || java.lang.Double.isInfinite(p.y)) {
                horrors++
                LOG.warn("Point $i has invalid coordinates.")
            }
            i++
        }
        i = 0
        for (seg in poly.segments) {
            val p = seg.getVertex(0)
            val q = seg.getVertex(1)
            if (p!!.x == q!!.x && p.y == q.y) {
                horrors++
                LOG.warn("Endpoints of segment $i are coincident (IDs ${p.id} / ${q.id}).")
            }
            i++
        }
        if (points[0].id == points[1].id) {
            horrors += checkVertexIDs(poly, count)
        } else {
            horrors += checkDuplicateIDs(poly)
        }
        return horrors == 0
    }

    fun hasDuplicateVertices(poly: Polygon): Boolean {
        var horrors = 0
        VertexSorter.sort(poly.points.toTypedArray())
        for (i in 1..poly.points.toTypedArray().size - 1) {
            if (poly.points.toTypedArray()[i - 1] === poly.points.toTypedArray()[i]) {
                horrors++
                LOG.warn("Found duplicate point " + poly.points.toTypedArray()[i] + ".")
            }
        }
        return horrors > 0
    }

    fun hasBadAngles(poly: Polygon, threshold: Double): Boolean {
        var horrors = 0
        var i = 0
        var p0: Point? = null
        var p1: Point? = null
        var q0: Point?
        var q1: Point?
        for (seg in poly.segments) {
            q0 = p0
            q1 = p1
            p0 = seg.getVertex(0)
            p1 = seg.getVertex(1)
            if (p0 === p1 || q0 === q1) {
                continue
            }
            if (q0 != null && q1 != null) {
                if (p0 === q1 && p1 != null) {
                    if (isBadAngle(q0, p0, p1, threshold)) {
                        horrors++
                        LOG.warn("Bad segment angle found at index $i.")
                    }
                }
            }
            i++
        }
        return horrors > 0
    }

    private fun isBadAngle(a: Point, b: Point, c: Point, threshold: Double): Boolean {
        val x = dotProduct(a, b, c)
        val y = crossProductLength(a, b, c)
        return Math.abs(Math.atan2(y, x)) <= threshold
    }

    private fun dotProduct(a: Point, b: Point, c: Point): Double {
        return (a.x - b.x) * (c.x - b.x) + (a.y - b.y) * (c.y - b.y)
    }

    private fun crossProductLength(a: Point, b: Point, c: Point): Double {
        return (a.x - b.x) * (c.y - b.y) - (a.y - b.y) * (c.x - b.x)
    }

    private fun checkVertexIDs(poly: Polygon, count: Int): Int {
        var horrors = 0
        var p: Vertex
        var q: Vertex
        for ((i, seg) in poly.segments.withIndex()) {
            p = seg.getVertex(0)!!
            q = seg.getVertex(1)!!
            if (p.id < 0 || p.id >= count) {
                horrors++
                LOG.warn("Segment $i has invalid startpoint.")
            }
            if (q.id < 0 || q.id >= count) {
                horrors++
                LOG.warn("Segment $i has invalid endpoint.")
            }
        }
        return horrors
    }

    private fun checkDuplicateIDs(poly: Polygon): Int {
        val ids = HashSet<Int>()
        for (p in poly.points) {
            if (!ids.add(p.id)) {
                LOG.warn("Found duplicate vertex ids.")
                return 1
            }
        }
        return 0
    }
}
