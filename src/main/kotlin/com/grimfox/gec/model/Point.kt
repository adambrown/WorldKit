package com.grimfox.gec.model

open class Point(val x: Float, val y: Float) {

    fun distanceSquaredTo(other: Point): Float {
        val deltaX = x - other.x
        val deltaXsq = deltaX * deltaX
        val deltaY = y - other.y
        val deltaYsq = deltaY * deltaY
        return deltaXsq + deltaYsq
    }
}