package com.grimfox.triangle.geometry

import com.grimfox.triangle.Predicates
import java.util.*

class Contour(points: List<Vertex>,
              internal var marker: Int = 0,
              internal var convex: Boolean = false) {

    var points: MutableList<Vertex> = ArrayList(points)

    init {
        val lastIndex = this.points.size - 1
        if (this.points[0] == this.points[lastIndex]) {
            this.points.removeAt(lastIndex)
        }
    }

    val segments: List<ISegment>
        get() {
            val p = this.points
            val count = p.size - 1
            val segments = (0..count - 1).mapTo(ArrayList<ISegment>()) { Segment(p[it], p[it + 1], marker) }
            segments.add(Segment(p[count], p[0], marker))
            return segments
        }

    fun findInteriorPoint(noExact: Boolean): Point {
        return findInteriorPoint(5, 2e-5, noExact)
    }

    fun findInteriorPoint(limit: Int, eps: Double, noExact: Boolean): Point {
        if (convex) {
            val count = this.points.size
            val point = Point(0.0, 0.0)
            for (i in 0..count - 1) {
                point.x += this.points[i].x
                point.y += this.points[i].y
            }
            point.x /= count.toDouble()
            point.y /= count.toDouble()
            return point
        }
        return findPointInPolygon(this.points, limit, eps, noExact)
    }

    private fun findPointInPolygon(contour: List<Vertex>, limit: Int, eps: Double, noExact: Boolean): Point {
        val bounds = Rectangle()
        bounds.expand(contour)
        val length = contour.size
        val test = Point()
        var a: Point
        var b: Point
        var c: Point
        var bx: Double
        var by: Double
        var dx: Double
        var dy: Double
        var h: Double
        val predicates = Predicates()
        a = contour[0]
        b = contour[1]
        for (i in 0..length - 1) {
            c = contour[(i + 2) % length]
            bx = b.x
            by = b.y
            h = predicates.counterClockwise(a, b, c, noExact)
            if (Math.abs(h) < eps) {
                dx = (c.y - a.y) / 2
                dy = (a.x - c.x) / 2
            } else {
                dx = (a.x + c.x) / 2 - bx
                dy = (a.y + c.y) / 2 - by
            }
            a = b
            b = c
            h = 1.0
            for (j in 0..limit - 1) {
                test.x = bx + dx * h
                test.y = by + dy * h
                if (bounds.contains(test) && isPointInPolygon(test, contour)) {
                    return test
                }
                test.x = bx - dx * h
                test.y = by - dy * h
                if (bounds.contains(test) && isPointInPolygon(test, contour)) {
                    return test
                }
                h /= 2
            }
        }
        throw Exception()
    }

    private fun isPointInPolygon(point: Point, poly: List<Vertex>): Boolean {
        var inside = false
        val x = point.x
        val y = point.y
        val count = poly.size
        var i = 0
        var j = count - 1
        while (i < count) {
            if ((poly[i].y < y && poly[j].y >= y || poly[j].y < y && poly[i].y >= y) && (poly[i].x <= x || poly[j].x <= x)) {
                inside = inside xor (poly[i].x + (y - poly[i].y) / (poly[j].y - poly[i].y) * (poly[j].x - poly[i].x) < x)
            }
            j = i
            i++
        }
        return inside
    }
}
