package com.grimfox.gec.model.geometry

class Vector2F(var a: Float, var b: Float) {

    val length2: Float by lazy { a * a + b * b }

    val length: Float by lazy { Math.sqrt(length2.toDouble()).toFloat() }

    constructor(p1: Point2F, p2: Point2F) : this(p2.x - p1.x, p2.y - p1.y)

    constructor(lineSegment: LineSegment2F) : this(lineSegment.a, lineSegment.b)

    operator fun set(i: Int, f: Float) = when (i) { 0 -> { a = f } 1 -> { b = f } else -> { throw IndexOutOfBoundsException("index: $i, size: 2") } }

    operator fun get(i: Int): Float = when (i) { 0 -> { a } 1 -> { b } else -> { throw IndexOutOfBoundsException("index: $i, size: 2") } }

    operator fun unaryMinus() = Vector2F(-a, -b)

    operator fun plus(c: Float) = Vector2F(a + c, b + c)

    operator fun plusAssign(c: Float) {
        a += c
        b += c
    }

    operator fun plus(other: Vector2F) = Vector2F(a + other.a, b + other.b)

    operator fun plusAssign(other: Vector2F) {
        a += other.a
        b += other.b
    }

    operator fun minus(c: Float) = Vector2F(a - c, b - c)

    operator fun minusAssign(c: Float) {
        a -= c
        b -= c
    }

    operator fun minus(other: Vector2F) = Vector2F(a - other.a, b - other.b)

    operator fun minusAssign(other: Vector2F) {
        a -= other.a
        b -= other.b
    }

    operator fun times(c: Float) = Vector2F(a * c, b * c)

    operator fun timesAssign(c: Float) {
        a *= c
        b *= c
    }

    operator fun div(c: Float) = Vector2F(a / c, b / c)

    operator fun divAssign(c: Float) {
        a /= c
        b /= c
    }

    fun toList() = listOf(a, b)

    fun getUnit() = Vector2F(a / length, b / length)

    fun getPerpendicular() = Vector2F(b, -a)

    fun cross(other: Vector2F) = (a * other.b) - (b * other.a)

    fun dot(other: Vector2F) = (a * other.a) + (b * other.b)
}