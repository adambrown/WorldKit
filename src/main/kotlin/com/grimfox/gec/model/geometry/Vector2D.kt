package com.grimfox.gec.model.geometry

class Vector2D(var a: Double, var b: Double) {

    val length2: Double by lazy { a * a + b * b }

    val length: Double by lazy { Math.sqrt(length2) }

    operator fun set(i: Int, f: Double) = when (i) { 0 -> { a = f } 1 -> { b = f } else -> { throw IndexOutOfBoundsException("index: $i, size: 2") } }

    operator fun get(i: Int): Double = when (i) { 0 -> { a } 1 -> { b } else -> { throw IndexOutOfBoundsException("index: $i, size: 2") } }

    operator fun unaryMinus() = Vector2D(-a, -b)

    operator fun plus(c: Double) = Vector2D(a + c, b + c)

    operator fun plusAssign(c: Double) {
        a += c
        b += c
    }

    operator fun plus(other: Vector2D) = Vector2D(a + other.a, b + other.b)

    operator fun plusAssign(other: Vector2D) {
        a += other.a
        b += other.b
    }

    operator fun minus(c: Double) = Vector2D(a - c, b - c)

    operator fun minusAssign(c: Double) {
        a -= c
        b -= c
    }

    operator fun minus(other: Vector2D) = Vector2D(a - other.a, b - other.b)

    operator fun minusAssign(other: Vector2D) {
        a -= other.a
        b -= other.b
    }

    operator fun times(c: Double) = Vector2D(a * c, b * c)

    operator fun timesAssign(c: Double) {
        a *= c
        b *= c
    }

    operator fun div(c: Double) = Vector2D(a / c, b / c)

    operator fun divAssign(c: Double) {
        a /= c
        b /= c
    }

    fun toList() = listOf(a, b)

    fun getUnit() = Vector2D(a / length, b / length)

    fun getPerpendicular() = Vector2D(b, -a)

    fun cross(other: Vector2D) = (a * other.b) - (b * other.a)

    fun dot(other: Vector2D) = (a * other.a) + (b * other.b)
}