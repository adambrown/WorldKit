package com.grimfox.gec.model.geometry

open class Point2F(val x: Float, val y: Float) {

    operator fun plus(v: Vector2F) = Point2F(x + v.a, y + v.b)

    fun distance2(other: Point2F): Float {
        val deltaX = x - other.x
        val deltaXsq = deltaX * deltaX
        val deltaY = y - other.y
        val deltaYsq = deltaY * deltaY
        return deltaXsq + deltaYsq
    }

    fun distance(other: Point2F): Float {
        return Math.sqrt(distance2(other).toDouble()).toFloat()
    }

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Point2F
        if (x != other.x) return false
        if (y != other.y) return false
        return true
    }

    override fun hashCode(): Int{
        return 31 * x.hashCode() + y.hashCode()
    }
}