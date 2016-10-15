package com.grimfox.gec.model.geometry

import java.util.*

open class LineSegment2F(open var a: Point2F, open var b: Point2F) {

    companion object {

        fun getConnectedEdgeSegments(edgeSet: Collection<LineSegment2F>): ArrayList<ArrayList<LineSegment2F>> {
            val segments = ArrayList<ArrayList<LineSegment2F>>()
            val unconnected = LinkedHashSet<LineSegment2F>(edgeSet)
            while (unconnected.isNotEmpty()) {
                val seed = unconnected.first()
                unconnected.remove(seed)
                val segment = seed.getConnectedEdges(edgeSet)
                unconnected.removeAll(segment)
                segments.add(segment)
            }
            return segments
        }
    }

    open val length2: Float by lazy {
        a.distance2(b)
    }

    open val length: Float by lazy {
        Math.sqrt(length2.toDouble()).toFloat()
    }

    open fun toVector() = Vector2F(this)

    fun intersects(other: LineSegment2F): Boolean {
        val o1 = sideOfLine(a, b, other.a)
        val o2 = sideOfLine(a, b, other.b)
        val o3 = sideOfLine(other.a, other.b, a)
        val o4 = sideOfLine(other.a, other.b, b)
        return o1 != o2 && o3 != o4 && o1 != 0 && o2 != 0 && o3 != 0 && o4 != 0
    }

    fun collinear(other: LineSegment2F): Boolean {
        return sideOfLine(a, b, other.a) == 0 && sideOfLine(a, b, other.b) == 0
    }

    fun collinearEpsilon(other: LineSegment2F, epsilon: Float = 0.0001f): Boolean {
        val u = Vector2F(this)
        val v = Vector2F(other)
        val det = (u.a * v.b) - (v.a * u.b)
        return det * det < epsilon * epsilon * u.length2 * v.length2
    }

    fun collinearOverlappingEpsilon(other: LineSegment2F, slopeEpsilon: Float = 0.0001f, overlapEpsilon: Float = 0.00001f): Boolean {
        val overlapEpsilon2 = overlapEpsilon * overlapEpsilon
        return collinearEpsilon(other, slopeEpsilon)
                && (overlapsLine(this, other.a, overlapEpsilon2)
                || overlapsLine(this, other.b, overlapEpsilon2)
                || overlapsLine(other, a, overlapEpsilon2)
                || overlapsLine(other, b, overlapEpsilon2))
    }

    fun collinearTouching(other: LineSegment2F): Boolean {
        return collinear(other) && (touchesLine(a, b, other.a) || touchesLine(a, b, other.b))
    }

    fun intersectsOrTouches(other: LineSegment2F): Boolean {
        val o1 = sideOfLine(a, b, other.a)
        val o2 = sideOfLine(a, b, other.b)
        val o3 = sideOfLine(other.a, other.b, a)
        val o4 = sideOfLine(other.a, other.b, b)
        if (o1 != o2 && o3 != o4) {
            return true
        }
        return ((o1 == 0 && touchesLine(a, b, other.a))
                || (o2 == 0 && touchesLine(a, b, other.b))
                || (o3 == 0 && touchesLine(other.a, other.b, a))
                || (o4 == 0 && touchesLine(other.a, other.b, b)))
    }

    private fun sideOfLine(p1: Point2F, p2: Point2F, p3: Point2F): Int {
        val det = (p2.y - p1.y) * (p3.x - p2.x) - (p2.x - p1.x) * (p3.y - p2.y)
        if (det == 0.0f) return 0
        return if (det > 0.0f) 1 else -1
    }

    private fun touchesLine(p1: Point2F, p2: Point2F, p3: Point2F): Boolean {
        return (p3.x <= Math.max(p1.x, p2.x)
                && p3.x >= Math.min(p1.x, p2.x)
                && p3.y <= Math.max(p1.y, p2.y)
                && p3.y >= Math.min(p1.y, p2.y))
    }

    private fun overlapsLine(line: LineSegment2F, point: Point2F, epsilon2: Float = 0.00001f): Boolean {
        return line.distance2(point) < epsilon2
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

    fun distanceInfinite(point: Point2F): Float {
        return Math.sqrt(distanceInfinite2(point).toDouble()).toFloat()
    }

    fun distanceInfinite2(point: Point2F): Float {
        if (length2 == 0.0f) {
            return a.distance2(point)
        }
        val dx = b.x - a.x
        val dy = b.y - a.y
        val u = (((point.x - a.x) * dx) + ((point.y - a.y) * dy)) / length2
        return point.distance2(Point2F(a.x - (u * dx), b.y - (u * dy)))
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
        val dx1 = x2 - x1
        val dy1 = y2 - y1

        val dot = a * dx1 + b * dy1
        val length2 = dx1 * dx1 + dy1 * dy1
        var interpolation = -1.0f
        if (length2 !== 0.0f) {
            interpolation = dot / length2
        }

        val closeX: Float
        val closeY: Float
        if (interpolation < 0.0f) {
            closeX = x1
            closeY = y1
        } else if (interpolation > 1.0f) {
            closeX = x2
            closeY = y2
        } else {
            closeX = x1 + interpolation * dx1
            closeY = y1 + interpolation * dy1
        }

        val dx = x - closeX
        val dy = y - closeY
        return dx * dx + dy * dy
    }

    fun distance2(line2: LineSegment2F): Float {
        if (intersectsOrTouches(line2)) {
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

    fun epsilonEquals(other: LineSegment2F, epsilon: Float = 0.0000001f): Boolean {
        return (a.epsilonEquals(other.a, epsilon) && b.epsilonEquals(other.b, epsilon)) || (a.epsilonEquals(other.b, epsilon) && b.epsilonEquals(other.a, epsilon))
    }

    fun getConnectedEdges(edgeSet: Collection<LineSegment2F>, epsilon: Float = 0.0000001f): ArrayList<LineSegment2F> {
        val connectedEdges = ArrayList<LineSegment2F>()
        connectedEdges.add(this)
        var nextEdges = LinkedHashSet<LineSegment2F>(connectedEdges)
        while (nextEdges.isNotEmpty()) {
            val newEdges = LinkedHashSet<LineSegment2F>()
            nextEdges.forEach { edge ->
                edgeSet.forEach {
                    if (edge.a.epsilonEquals(it.a, epsilon)
                            || edge.a.epsilonEquals(it.b, epsilon)
                            || edge.b.epsilonEquals(it.a, epsilon)
                            || edge.b.epsilonEquals(it.b, epsilon)) {
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

    override fun toString(): String {
        return "LineSegment2F(a=$a, b=$b)"
    }
}