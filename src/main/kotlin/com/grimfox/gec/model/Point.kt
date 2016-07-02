package com.grimfox.gec.generator

data class Point(var x: Float, var y: Float) {

    fun distanceSquaredTo(other: Point): Float {
        val deltaX = x - other.x
        val deltaXsq = deltaX * deltaX
        val deltaY = y - other.y
        val deltaYsq = deltaY * deltaY
        return deltaXsq + deltaYsq
    }
}