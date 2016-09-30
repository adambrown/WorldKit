package com.grimfox.gec.model.geometry

class Point2FKey(val x: Int, val y: Int, val point: Point2F) {

    constructor(point: Point2F, multiplier: Int = 1000000): this(Math.round(point.x * multiplier), Math.round(point.y * multiplier), point)

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Point2FKey
        if (x != other.x) return false
        if (y != other.y) return false
        return true
    }

    override fun hashCode(): Int{
        return x + y
    }
}