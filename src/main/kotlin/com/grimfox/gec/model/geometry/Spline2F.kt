package com.grimfox.gec.model.geometry

import java.util.*

class Spline2F(val points: MutableList<SplinePoint2F>, var isClosed: Boolean) {

    constructor(polygon: Polygon2F): this(polygon.points.map { SplinePoint2F(it) }.toMutableList(), polygon.isClosed) {
        var lastNorm: Point2F? = null
        var lastLength: Float? = null
        for (i in 1..points.size - if (isClosed) 0 else 1) {
            val p1 = points[i - 1]
            val p2 = points[i % points.size]
            val p3 = points[(i + 1) % points.size]
            val norm1 = if (lastNorm != null) {
                lastNorm
            } else {
                val dx1 = p2.p.x - p1.p.x
                val dy1 = p2.p.y - p1.p.y
                Point2F(dy1, -dx1)
            }
            val dx2 = p3.p.x - p2.p.x
            val dy2 = p3.p.y - p2.p.y
            val norm2 = Point2F(dy2, -dx2)
            lastNorm = norm2
            val avgNorm = Point2F((norm1.x + norm2.x) * 0.5f, (norm1.y + norm2.y) * 0.5f)
            val length = Math.sqrt((avgNorm.x * avgNorm.x) + (avgNorm.y * avgNorm.y).toDouble())
            val norm = Point2F((avgNorm.x / length).toFloat(), (avgNorm.y / length).toFloat())
            val backVector = Point2F(norm.y, -norm.x)
            val forwardVector = Point2F(-norm.y, norm.x)
            val backLength = if (lastLength != null) {
                lastLength
            } else {
                (Math.sqrt(p1.p.distance2(p2.p).toDouble()) * 0.3666666667).toFloat()
            }
            p2.cp1 = Point2F(p2.p.x + (backVector.x * backLength), p2.p.y + (backVector.y * backLength))
            val forwardLength = (Math.sqrt(p2.p.distance2(p3.p).toDouble()) * 0.3666666667).toFloat()
            lastLength = forwardLength
            p2.cp2 = Point2F(p2.p.x + (forwardVector.x * forwardLength), p2.p.y + (forwardVector.y * forwardLength))
        }
    }

    fun toPolygon(samples: Int, iterations: Int): Polygon2F {
        val polyPoints = ArrayList<Point2F>()
        for (i in 1..points.size - if (isClosed) 0 else 1) {
            val p1 = points[i - 1]
            val p2 = points[i % points.size]
            val times = p1.interpolationTimes(p2, samples, iterations).second
            for (t in 0..times.size - 2) {
                polyPoints.add(p1.interpolate(p2, times[t]).p)
            }
            if (!isClosed && i == points.size - 1) {
                polyPoints.add(p2.p)
            }
        }
        return Polygon2F(polyPoints, isClosed)
    }

    fun toPolygon(desiredSegmentLength: Float, iterations: Int): Polygon2F {
        val polyPoints = ArrayList<Point2F>()
        for (i in 1..points.size - if (isClosed) 0 else 1) {
            val p1 = points[i - 1]
            val p2 = points[i % points.size]
            val times = p1.interpolationTimes(p2, desiredSegmentLength, iterations).second
            for (t in 0..times.size - 2) {
                polyPoints.add(p1.interpolate(p2, times[t]).p)
            }
            if (!isClosed && i == points.size - 1) {
                polyPoints.add(p2.p)
            }
        }
        return Polygon2F(polyPoints, isClosed)
    }

    fun subdivided(desiredSegmentLength: Float, iterations: Int): Spline2F {
        val splinePoints = ArrayList<SplinePoint2F>()
        for (i in 1..points.size - if (isClosed) 0 else 1) {
            val p1 = points[i - 1]
            val p2 = points[i % points.size]
            val (length, times) = p1.interpolationTimes(p2, desiredSegmentLength, iterations)
            val newCpLength = (length / (times.size - 1)) * 0.3666666667f
            var newPoint = SplinePoint2F(p1.p, p1.cp1, p1.cp2)
            newPoint.cp2 = newPoint.p + (Vector2F(newPoint.p, newPoint.cp2).getUnit() * newCpLength)
            splinePoints.add(newPoint)
            for (t in 1..times.size - 2) {
                newPoint = p1.interpolate(p2, times[t])
                newPoint.cp1 = newPoint.p + (Vector2F(newPoint.p, newPoint.cp1).getUnit() * newCpLength)
                newPoint.cp2 = newPoint.p + (Vector2F(newPoint.p, newPoint.cp2).getUnit() * newCpLength)
                splinePoints.add(newPoint)
            }
            if (!isClosed && i == points.size - 1) {
                newPoint = SplinePoint2F(p2.p, p2.cp1, p2.cp2)
                newPoint.cp1 = newPoint.p + (Vector2F(newPoint.p, newPoint.cp1).getUnit() * newCpLength)
                splinePoints.add(newPoint)
            }
        }
        return Spline2F(splinePoints, isClosed)
    }
}