package com.grimfox.gec.model.geometry

open class Point2F(val x: Float, val y: Float) {

    operator fun plus(v: Vector2F) = Point2F(x + v.a, y + v.b)

    operator fun minus(other: Point2F) = Vector2F(other, this)

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

    open operator fun times(s: Float) = Point2F(x * s, y * s)

    override fun equals(other: Any?): Boolean {
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

    fun epsilonEquals(other: Point2F, epsilon: Float = 0.0000001f): Boolean {
        return Math.abs(x - other.x) < epsilon && Math.abs(y - other.y) < epsilon
    }

    override fun toString(): String {
        return "Point2F(x=${x}f, y=${y}f)"
    }
}