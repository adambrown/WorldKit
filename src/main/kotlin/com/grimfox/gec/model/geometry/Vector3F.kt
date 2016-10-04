package com.grimfox.gec.model.geometry

class Vector3F(var a: Float, var b: Float, var c: Float) {

    val length2: Float by lazy { a * a + b * b + c * c }

    val length: Float by lazy { Math.sqrt(length2.toDouble()).toFloat() }

    constructor(p1: Point3F, p2: Point3F) : this(p2.x - p1.x, p2.y - p1.y, p2.z - p1.z)

    constructor(lineSegment: LineSegment3F) : this(lineSegment.a, lineSegment.b)

    operator fun set(i: Int, f: Float) = when (i) { 0 -> { a = f } 1 -> { b = f } 2 -> { c = f } else -> { throw IndexOutOfBoundsException("index: $i, size: 2") } }

    operator fun get(i: Int): Float = when (i) { 0 -> { a } 1 -> { b } 2 -> { c } else -> { throw IndexOutOfBoundsException("index: $i, size: 2") } }

    operator fun unaryMinus() = Vector3F(-a, -b, -c)

    operator fun plus(s: Float) = Vector3F(a + s, b + s, c + s)

    operator fun plusAssign(s: Float) {
        a += s
        b += s
        c += s
    }

    operator fun plus(other: Vector3F) = Vector3F(a + other.a, b + other.b, c + other.c)

    operator fun plusAssign(other: Vector3F) {
        a += other.a
        b += other.b
        c += other.c
    }

    operator fun minus(s: Float) = Vector3F(a - s, b - s, c - s)

    operator fun minusAssign(s: Float) {
        a -= s
        b -= s
        c -= s
    }

    operator fun minus(other: Vector3F) = Vector3F(a - other.a, b - other.b, c - other.c)

    operator fun minusAssign(other: Vector3F) {
        a -= other.a
        b -= other.b
        c -= other.c
    }

    operator fun times(s: Float) = Vector3F(a * s, b * s, c * s)

    operator fun timesAssign(s: Float) {
        a *= s
        b *= s
        c *= s
    }

    operator fun div(s: Float) = Vector3F(a / s, b / s, c / s)

    operator fun divAssign(s: Float) {
        a /= s
        b /= s
        c /= s
    }

    fun toList() = listOf(a, b, c)

    fun getUnit() = Vector3F(a / length, b / length, c / length)

    fun cross(other: Vector3F) = Vector3F((b * other.c) - (c * other.b), (c * other.a) - (a * other.c), (a * other.b) - (b * other.a))

    fun dot(other: Vector3F) = (a * other.a) + (b * other.b) + (c * other.c)

    fun dot(other: Point3F) = (a * other.x) + (b * other.y) + (c * other.z)
}