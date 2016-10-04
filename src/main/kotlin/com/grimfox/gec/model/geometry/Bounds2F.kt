package com.grimfox.gec.model.geometry

class Bounds2F(val min: Point2F, val max: Point2F) {

    val c1: Point2F by lazy { min }
    val c2: Point2F by lazy { Point2F(max.x, min.y) }
    val c3: Point2F by lazy { max }
    val c4: Point2F by lazy { Point2F(min.x, max.y)}
    val e1: LineSegment2F by lazy { LineSegment2F(c1, c2) }
    val e2: LineSegment2F by lazy { LineSegment2F(c2, c3) }
    val e3: LineSegment2F by lazy { LineSegment2F(c3, c4) }
    val e4: LineSegment2F by lazy { LineSegment2F(c4, c1) }

    fun isWithin(point: Point2F): Boolean {
        return point.x >= min.x && point.x <= max.x && point.y >= min.y && point.y <= max.y
    }

    fun isWithin(line: LineSegment2F): Boolean {
        return isWithin(line.a) || isWithin(line.b) || line.intersectsOrTouches(e1) || line.intersectsOrTouches(e2) || line.intersectsOrTouches(e3) || line.intersectsOrTouches(e4)
    }

    fun singleIntersection(line: LineSegment2F): Pair<Int, Point2F?> {
        val aWithin = isWithin(line.a)
        val bWithin = isWithin(line.b)
        if ((!aWithin && !bWithin) || (aWithin && bWithin)) {
            return Pair(-1, null)
        }
        var intersection = line.intersection(e1)
        if (intersection != null) {
            return Pair(0, intersection)
        }
        intersection = line.intersection(e2)
        if (intersection != null) {
            return Pair(1, intersection)
        }
        intersection = line.intersection(e3)
        if (intersection != null) {
            return Pair(2, intersection)
        }
        intersection = line.intersection(e4)
        if (intersection != null) {
            return Pair(3, intersection)
        }
        return Pair(-1, null)
    }
}