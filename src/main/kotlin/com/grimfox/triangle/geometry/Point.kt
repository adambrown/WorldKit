package com.grimfox.triangle.geometry

open class Point(
        var x: Double = 0.0,
        var y: Double = 0.0,
        var label: Int = 0,
        var id: Int = 0) : Comparable<Point> {

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        val p = other as? Point ?: return false
        return x == p.x && y == p.y
    }

    fun equals(p: Point): Boolean {
        return x == p.x && y == p.y
    }

    override fun compareTo(other: Point): Int {
        if (x == other.x && y == other.y) {
            return 0
        }
        return if (x < other.x || x == other.x && y < other.y) -1 else 1
    }

    override fun hashCode() = (589 + x.hashCode()) * 31 + y.hashCode()
}
