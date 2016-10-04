package com.grimfox.gec.model.geometry

open class Point3F(x: Float, y: Float, val z: Float): Point2F(x, y) {

    operator fun plus(v: Vector3F) = Point3F(x + v.a, y + v.b, z + v.c)

    operator fun minus(other: Point3F) = Vector3F(other, this)

    fun distance2(other: Point3F): Float {
        val deltaX = x - other.x
        val deltaY = y - other.y
        val deltaZ = z - other.z
        return (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ)
    }

    fun distance(other: Point3F): Float {
        return Math.sqrt(distance2(other).toDouble()).toFloat()
    }

    override operator fun times(s: Float) = Point3F(x * s, y * s, z * s)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Point3F
        if (x != other.x) return false
        if (y != other.y) return false
        if (z != other.z) return false
        return true
    }

    override fun hashCode(): Int{
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    fun epsilonEquals(other: Point3F, epsilon: Float = 0.0000001f): Boolean {
        return Math.abs(x - other.x) < epsilon && Math.abs(y - other.y) < epsilon && Math.abs(z - other.z) < epsilon
    }
}