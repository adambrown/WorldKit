package com.grimfox.triangle.geometry

class Rectangle {

    var left: Double

    var bottom: Double

    var right: Double

    var top: Double

    constructor() {
        this.bottom = java.lang.Double.MAX_VALUE
        this.left = java.lang.Double.MAX_VALUE
        this.top = -java.lang.Double.MAX_VALUE
        this.right = -java.lang.Double.MAX_VALUE
    }

    constructor(x: Double, y: Double, width: Double, height: Double) {
        this.left = x
        this.bottom = y
        this.right = x + width
        this.top = y + height
    }

    constructor(other: Rectangle) : this(other.left, other.bottom, other.right, other.top)

    val width: Double
        get() = right - left
    val height: Double
        get() = top - bottom

    fun resize(dx: Double, dy: Double) {
        left -= dx
        right += dx
        bottom -= dy
        top += dy
    }

    fun expand(p: Point) {
        left = Math.min(left, p.x)
        bottom = Math.min(bottom, p.y)
        right = Math.max(right, p.x)
        top = Math.max(top, p.y)
    }

    fun expand(points: Collection<Point>) {
        for (p in points) {
            expand(p)
        }
    }

    fun expand(other: Rectangle) {
        left = Math.min(left, other.left)
        bottom = Math.min(bottom, other.bottom)
        right = Math.max(right, other.right)
        top = Math.max(top, other.top)
    }

    fun contains(x: Double, y: Double): Boolean {
        return x in left..right && y >= bottom && y <= top
    }

    operator fun contains(pt: Point): Boolean {
        return contains(pt.x, pt.y)
    }

    operator fun contains(other: Rectangle): Boolean {
        return left <= other.left && other.right <= right && bottom <= other.bottom && other.top <= top
    }

    fun intersects(other: Rectangle): Boolean {
        return other.left < right && left < other.right && other.bottom < top && bottom < other.top
    }
}
