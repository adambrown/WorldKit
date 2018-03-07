package com.grimfox.triangle.geometry

class RegionPointer(
        x: Double,
        y: Double,
        var id: Int,
        area: Double = 0.0,
        var point: Point = Point(x, y)) {

    var area: Double = area
        set(value) {
            if (value < 0.0) {
                throw IllegalArgumentException("Area constraints must not be negative.")
            }
            field = value
        }
}
