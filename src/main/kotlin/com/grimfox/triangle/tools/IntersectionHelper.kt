package com.grimfox.triangle.tools

import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.geometry.Rectangle

object IntersectionHelper {

    fun intersectSegments(p0: Point, p1: Point, q0: Point, q1: Point, c0: Point) {
        val ux = p1.x - p0.x
        val uy = p1.y - p0.y
        val vx = q1.x - q0.x
        val vy = q1.y - q0.y
        val wx = p0.x - q0.x
        val wy = p0.y - q0.y
        val d = ux * vy - uy * vx
        val s = (vx * wy - vy * wx) / d
        c0.x = p0.x + s * ux
        c0.y = p0.y + s * uy
    }

    fun liangBarsky(rect: Rectangle, p0: Point, p1: Point, c0: Point, c1: Point): Boolean {
        val xmin = rect.left
        val xmax = rect.right
        val ymin = rect.bottom
        val ymax = rect.top
        val x0 = p0.x
        val y0 = p0.y
        val x1 = p1.x
        val y1 = p1.y
        var t0: Double = 0.0
        var t1: Double = 1.0
        val dx = x1 - x0
        val dy = y1 - y0
        var p: Double = 0.0
        var q: Double = 0.0
        var r: Double
        for (edge in 0..3) {
            if (edge == 0) {
                p = -dx
                q = -(xmin - x0)
            }
            if (edge == 1) {
                p = dx
                q = xmax - x0
            }
            if (edge == 2) {
                p = -dy
                q = -(ymin - y0)
            }
            if (edge == 3) {
                p = dy
                q = ymax - y0
            }
            r = q / p
            if (p == 0.0 && q < 0)
                return false
            if (p < 0) {
                if (r > t1)
                    return false
                else
                    if (r > t0)
                        t0 = r
            } else
                if (p > 0) {
                    if (r < t0)
                        return false
                    else
                        if (r < t1)
                            t1 = r
                }
        }
        c0.x = x0 + t0 * dx
        c0.y = y0 + t0 * dy
        c1.x = x0 + t1 * dx
        c1.y = y0 + t1 * dy
        return true
    }

    fun boxRayIntersection(rect: Rectangle, p0: Point, p1: Point, c1: Point): Boolean {
        val x = p0.x
        val y = p0.y
        val dx = p1.x - x
        val dy = p1.y - y
        val t1: Double?
        val x1: Double?
        val y1: Double?
        val t2: Double?
        val x2: Double?
        val y2: Double?
        val xmin = rect.left
        val xmax = rect.right
        val ymin = rect.bottom
        val ymax = rect.top
        if (x < xmin || x > xmax || y < ymin || y > ymax) {
            return false
        }
        if (dx < 0) {
            t1 = (xmin - x) / dx
            x1 = xmin
            y1 = y + t1 * dy
        } else if (dx > 0) {
            t1 = (xmax - x) / dx
            x1 = xmax
            y1 = y + t1 * dy
        } else {
            t1 = java.lang.Double.MAX_VALUE
            x1 = 0.0
            y1 = 0.0
        }
        if (dy < 0) {
            t2 = (ymin - y) / dy
            x2 = x + t2 * dx
            y2 = ymin
        } else if (dy > 0) {
            t2 = (ymax - y) / dy
            x2 = x + t2 * dx
            y2 = ymax
        } else {
            t2 = java.lang.Double.MAX_VALUE
            x2 = 0.0
            y2 = 0.0
        }
        if (t1 < t2) {
            c1.x = x1
            c1.y = y1
        } else {
            c1.x = x2
            c1.y = y2
        }
        return true
    }
}
