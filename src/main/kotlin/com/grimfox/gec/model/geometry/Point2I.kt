package com.grimfox.gec.model.geometry

open class Point2I(val x: Int, val y: Int) {

    constructor(point: Point2F): this(Math.round(point.x), Math.round(point.y))

    fun distance2(other: Point2I): Int {
        val deltaX = x - other.x
        val deltaXsq = deltaX * deltaX
        val deltaY = y - other.y
        val deltaYsq = deltaY * deltaY
        return deltaXsq + deltaYsq
    }

    fun distance(other: Point2I): Float {
        return Math.sqrt(distance2(other).toDouble()).toFloat()
    }

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Point2I
        if (x != other.x) return false
        if (y != other.y) return false
        return true
    }

    override fun hashCode(): Int{
        return 31 * x.hashCode() + y.hashCode()
    }
}