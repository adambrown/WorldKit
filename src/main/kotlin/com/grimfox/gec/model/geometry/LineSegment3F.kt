package com.grimfox.gec.model.geometry

import java.util.*

class LineSegment3F(var a: Point3F, var b: Point3F) {

    val length2: Float by lazy {
        a.distance2(b)
    }

    val length: Float by lazy {
        Math.sqrt(length2.toDouble()).toFloat()
    }

    fun toVector() = Vector3F(this)

    fun subdivided2d(segmentLength: Float): ArrayList<LineSegment3F> {
        val segmentCount = Math.round(LineSegment2F(a, b).length / segmentLength)
        if (segmentCount < 2) {
            return arrayListOf(this)
        }
        val segments = ArrayList<LineSegment3F>()
        val vector = toVector() * (1.0f / segmentCount)
        var lastPoint = a
        for (i in 1..segmentCount - 1) {
            val newPoint = lastPoint + vector
            segments.add(LineSegment3F(lastPoint, newPoint))
            lastPoint = newPoint
        }
        segments.add(LineSegment3F(lastPoint, b))
        return segments
    }
}