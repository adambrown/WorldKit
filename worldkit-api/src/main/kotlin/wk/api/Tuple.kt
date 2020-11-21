package wk.api

import kotlin.math.sqrt

interface C2<A, B> {
    val first: A
    val second: B

    operator fun component1(): A

    operator fun component2(): B
}

interface M2<A, B> : C2<A, B> {
    override var first: A
    override var second: B
}

interface C3<A, B, C>  : C2<A, B> {
    val third: C

    operator fun component3(): C
}

interface M3<A, B, C> : C3<A, B, C>, M2<A, B> {
    override var first: A
    override var second: B
    override var third: C
}

interface C4<A, B, C, D>  : C3<A, B, C> {
    val fourth: D

    operator fun component4(): D
}

interface M4<A, B, C, D> : C4<A, B, C, D>, M3<A, B, C> {
    override var first: A
    override var second: B
    override var third: C
    override var fourth: D
}

data class T2<A, B>(
        override var first: A,
        override var second: B
) : M2<A, B>

data class T3<A, B, C>(
        override var first: A,
        override var second: B,
        override var third: C
) : M3<A, B, C>

data class T4<A, B, C, D>(
        override var first: A,
        override var second: B,
        override var third: C,
        override var fourth: D
) : M4<A, B, C, D>

typealias Vec2 = M2<Float, Float>
typealias Vec2C = C2<Float, Float>
inline var Vec2.x get() = first; set(v) { first = v }
inline var Vec2.y get() = second; set(v) { second = v }
inline val Vec2C.x get() = first
inline val Vec2C.y get() = second

typealias Vec3 = M3<Float, Float, Float>
typealias Vec3C = C3<Float, Float, Float>
inline var Vec3.x get() = first; set(v) { first = v }
inline var Vec3.y get() = second; set(v) { second = v }
inline var Vec3.z get() = third; set(v) { third = v }
inline val Vec3C.x get() = first
inline val Vec3C.y get() = second
inline val Vec3C.z get() = third

typealias Vec4 = M4<Float, Float, Float, Float>
typealias Vec4C = C4<Float, Float, Float, Float>
inline var Vec4.x get() = first; set(v) { first = v }
inline var Vec4.y get() = second; set(v) { second = v }
inline var Vec4.z get() = third; set(v) { third = v }
inline var Vec4.w get() = fourth; set(v) { fourth = v }
inline val Vec4C.x get() = first
inline val Vec4C.y get() = second
inline val Vec4C.z get() = third
inline val Vec4C.w get() = fourth

typealias Point2F = Vec2
typealias Point2FC = Vec2C
typealias Point3F = Vec3
typealias Point3FC = Vec3C
typealias ColorF = Vec4
typealias ColorFC = Vec4C

inline var ColorF.r get() = first; set(v) { first = v }
inline var ColorF.g get() = second; set(v) { second = v }
inline var ColorF.b get() = third; set(v) { third = v }
inline var ColorF.a get() = fourth; set(v) { fourth = v }
inline val Vec4C.r get() = first
inline val Vec4C.g get() = second
inline val Vec4C.b get() = third
inline val Vec4C.a get() = fourth

fun vec2(x: Float = 0.0f, y: Float = 0.0f): Vec2 = T2(x, y)

fun point2(x: Float = 0.0f, y: Float = 0.0f): Point2F = T2(x, y)

fun vec3(x: Float = 0.0f, y: Float = 0.0f, z: Float = 0.0f): Vec3 = T3(x, y, z)

fun point3(x: Float = 0.0f, y: Float = 0.0f, z: Float = 0.0f): Point3F = T3(x, y, z)

fun vec4(x: Float = 0.0f, y: Float = 0.0f, z: Float = 0.0f, w: Float = 0.0f): Vec4 = T4(x, y, z, w)

fun color(r: Float, g: Float, b: Float, a: Float): ColorF = T4(r, g, b, a)

fun vec2C(x: Float, y: Float): Vec2C = T2(x, y)

fun point2C(x: Float, y: Float): Point2FC = T2(x, y)

fun vec3C(x: Float, y: Float, z: Float): Vec3C = T3(x, y, z)

fun point3C(x: Float, y: Float, z: Float): Point3FC = T3(x, y, z)

fun vec4C(x: Float, y: Float, z: Float, w: Float): Vec4C = T4(x, y, z, w)

operator fun C2<Float, Float>.plus(other: C2<Float, Float>) = T2(first + other.first, second + other.second)

operator fun C2<Float, Float>.plus(f: Float) = T2(first + f, second + f)

operator fun Float.plus(other: C2<Float, Float>) = T2(this + other.first, this + other.second)

operator fun C2<Float, Float>.minus(other: C2<Float, Float>) = T2(first - other.first, second - other.second)

operator fun C2<Float, Float>.minus(f: Float) = T2(first - f, second - f)

operator fun Float.minus(other: C2<Float, Float>) = T2(this - other.first, this - other.second)

operator fun C2<Float, Float>.times(other: C2<Float, Float>) = T2(first * other.first, second * other.second)

operator fun C2<Float, Float>.times(f: Float) = T2(first * f, second * f)

operator fun Float.times(other: C2<Float, Float>) = T2(this * other.first, this * other.second)

operator fun C2<Float, Float>.div(other: C2<Float, Float>) = T2(first / other.first, second / other.second)

operator fun C2<Float, Float>.div(f: Float) = T2(first / f, second / f)

operator fun Float.div(other: C2<Float, Float>) = T2(this / other.first, this / other.second)

fun M2<Float, Float>.set(other: M2<Float, Float>) {
    first = other.first
    second = other.second
}

fun M2<Float, Float>.set(x: Float, y: Float) {
    first = x
    second = y
}


operator fun C3<Float, Float, Float>.plus(other: C3<Float, Float, Float>) = T3(first + other.first, second + other.second, third + other.third)

operator fun C3<Float, Float, Float>.plus(f: Float) = T3(first + f, second + f, third + f)

operator fun Float.plus(other: C3<Float, Float, Float>) = T3(this + other.first, this + other.second, this + other.third)

operator fun C3<Float, Float, Float>.minus(other: C3<Float, Float, Float>) = T3(first - other.first, second - other.second, third - other.third)

operator fun C3<Float, Float, Float>.minus(f: Float) = T3(first - f, second - f, third - f)

operator fun Float.minus(other: C3<Float, Float, Float>) = T3(this - other.first, this - other.second, this - other.third)

operator fun C3<Float, Float, Float>.times(other: C3<Float, Float, Float>) = T3(first * other.first, second * other.second, third * other.third)

operator fun C3<Float, Float, Float>.times(f: Float) = T3(first * f, second * f, third * f)

operator fun Float.times(other: C3<Float, Float, Float>) = T3(this * other.first, this * other.second, this * other.third)

operator fun C3<Float, Float, Float>.div(other: C3<Float, Float, Float>) = T3(first / other.first, second / other.second, third / other.third)

operator fun C3<Float, Float, Float>.div(f: Float) = T3(first / f, second / f, third / f)

operator fun Float.div(other: C3<Float, Float, Float>) = T3(this / other.first, this / other.second, this / other.third)

fun M3<Float, Float, Float>.set(other: M3<Float, Float, Float>) {
    first = other.first
    second = other.second
    third = other.third
}

fun M3<Float, Float, Float>.set(x: Float, y: Float, z: Float) {
    first = x
    second = y
    third = z
}


inline val Vec2C.length2 get() = x * x + y * y

inline val Vec2C.length get() = sqrt(length2)

infix fun Point2FC.dist2(other: Point2FC) = (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)

infix fun Point2FC.dist(other: Point2FC) = sqrt(dist2(other))

fun Vec2.toUnit(): Vec2 {
    val length = length
    x /= length
    y /= length
    return this
}

inline val Vec2C.unit get() = vec2(x, y).toUnit()

infix fun Vec2C.dot(other: Vec2C) = x * other.x + y * other.y


inline val Vec3C.length2 get() = x * x + y * y + z * z

inline val Vec3C.length get() = sqrt(length2)

infix fun Point3FC.dist2(other: Point3FC) = (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y) + (z - other.z) * (z - other.z)

infix fun Point3FC.dist(other: Point3FC) = sqrt(dist2(other))

fun Vec3.toUnit(): Vec3 {
    val length = length
    x /= length
    y /= length
    z /= length
    return this
}

inline val Vec3C.unit get() = vec3(x, y, z).toUnit()

infix fun Vec3C.dot(other: Vec3C) = x * other.x + y * other.y + z * other.z