package com.grimfox.gec.model.geometry

class LineSegment2FKey(val a: Point2FKey, val b: Point2FKey, val line: LineSegment2F) {

    constructor(line: LineSegment2F, multiplier: Int = 1000000): this(Point2FKey(line.a, multiplier), Point2FKey(line.b, multiplier), line)

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as LineSegment2FKey
        return (a == other.a && b == other.b) || (a == other.b && b == other.a)
    }

    override fun hashCode(): Int{
        return a.hashCode() + b.hashCode()
    }
}