package com.grimfox.gec.model.geometry

class LineSegment2F(var a: Point2F, var b: Point2F) {

    fun toVector() = Vector2F(this)

    fun intersects(other: LineSegment2F): Boolean {
        val o1 = orientation(a, b, other.a)
        val o2 = orientation(a, b, other.b)
        val o3 = orientation(other.a, other.b, a)
        val o4 = orientation(other.a, other.b, b)
        if (o1 != o2 && o3 != o4) {
            return true
        }
        return (o1 == 0 && (onSegment(a, other.a, b)
                || onSegment(a, other.b, b)
                || onSegment(other.a, a, other.b)
                || onSegment(other.a, b, other.b)))
    }

    private fun orientation(p1: Point2F, p2: Point2F, p3: Point2F): Int {
        val det = (p2.y - p1.y) * (p3.x - p2.x) - (p2.x - p1.x) * (p3.y - p2.y)
        if (det == 0.0f) return 0
        return if (det > 0.0f) 1 else -1
    }

    private fun onSegment(p1: Point2F, p2: Point2F, p3: Point2F): Boolean {
        return (p2.x <= Math.max(p1.x, p3.x)
                && p2.x >= Math.min(p1.x, p3.x)
                && p2.y <= Math.max(p1.y, p3.y)
                && p2.y >= Math.min(p1.y, p3.y))
    }

    private fun pointInBetween(p0_x: Float, p0_y: Float, p1_x: Float, p1_y: Float, p2_x: Float, p2_y: Float): Boolean {
        return (p1_x <= Math.max(p0_x, p2_x)
                && p1_x >= Math.min(p0_x, p2_x)
                && p1_y <= Math.max(p0_y, p2_y)
                && p1_y >= Math.min(p0_y, p2_y))
    }

    fun intersection(other: LineSegment2F): Point2F? {
        return getLineIntersection(a.x, a.y, b.x, b.y, other.a.x, other.a.y, other.b.x, other.b.y)
    }

    private fun getLineIntersection(p0_x: Float, p0_y: Float, p1_x: Float, p1_y: Float, p2_x: Float, p2_y: Float, p3_x: Float, p3_y: Float): Point2F? {
        val s1_x = p1_x - p0_x
        val s1_y = p1_y - p0_y
        val s2_x = p3_x - p2_x
        val s2_y = p3_y - p2_y
        val denominator = -s2_x * s1_y + s1_x * s2_y
        if (denominator == 0.0f) {
            if (pointInBetween(p0_x, p0_y, p2_x, p2_y, p1_x, p1_y)) {
                return Point2F(p2_x, p2_y)
            }
            if (pointInBetween(p0_x, p0_y, p3_x, p3_y, p1_x, p1_y)) {
                return Point2F(p3_x, p3_y)
            }
            if (pointInBetween(p2_x, p2_y, p0_x, p0_y, p3_x, p3_y)) {
                return Point2F(p0_x, p0_y)
            }
            if (pointInBetween(p2_x, p2_y, p1_x, p1_y, p3_x, p3_y)) {
                return Point2F(p1_x, p1_y)
            }
            return null
        }
        val tNumerator = (s2_x * (p0_y - p2_y) - s2_y * (p0_x - p2_x))
//        if ((tNumerator > denominator) || (tNumerator < 0.0f && denominator > 0.0f)) {
//            return null
//        }
        val sNumerator = (-s1_y * (p0_x - p2_x) + s1_x * (p0_y - p2_y))
//        if ((sNumerator > denominator) || (sNumerator < 0.0f && denominator > 0.0f)) {
//            return null
//        }
        val s = sNumerator / denominator
        val t = tNumerator / denominator
        if (s >= 0 && s <= 1 && t >= 0 && t <= 1) {
            return Point2F(p0_x + (t * s1_x), p0_y + (t * s1_y))
        }
        return null
    }

    fun distance2(point: Point2F): Float {
        val x = point.x
        val y = point.y
        val x1 = a.x
        val y1 = a.y
        val x2 = b.x
        val y2 = b.y

        val a = x - x1
        val b = y - y1
        val c = x2 - x1
        val d = y2 - y1

        val dot = a * c + b * d
        val len_sq = c * c + d * d
        var param = -1.0f
        if (len_sq !== 0.0f) {
            param = dot / len_sq
        }

        val xx: Float
        val yy: Float
        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * c
            yy = y1 + param * d
        }

        val dx = x - xx
        val dy = y - yy
        return dx * dx + dy * dy
    }

    fun distance2(line2: LineSegment2F): Float {
        if (intersects(line2)) {
            return 0.0f
        }
        var minDist = distance2(line2.a)
        minDist = Math.min(minDist, distance2(line2.b))
        minDist = Math.min(minDist, line2.distance2(a))
        return Math.min(minDist, line2.distance2(b))
    }

    fun interpolate(interpolation: Float): Point2F {
        if (interpolation >= 1.0f) {
            return b
        }
        if (interpolation <= 0.0f) {
            return a
        }
        val vector = Vector2F(this) * interpolation
        return Point2F(a.x + vector.a, a.y + vector.b)
    }
}