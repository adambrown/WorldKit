package com.grimfox.gec.model

class Spline(val points: MutableList<SplinePoint>, var isClosed: Boolean) {

    constructor(polygon: Polygon): this(polygon.points.map { SplinePoint(it) }.toMutableList(), polygon.isClosed) {
        var lastNorm: Point? = null
        var lastLength: Float? = null
        for (i in 1..points.size - if (isClosed) 0 else 1) {
            val p1 = points[i - 1]
            val p2 = points[i % points.size]
            val p3 = points[(i + 1) % points.size]
            val norm1 = if (lastNorm != null) {
                lastNorm
            } else {
                val dx1 = p2.point.x - p1.point.x
                val dy1 = p2.point.y - p1.point.y
                Point(dy1, -dx1)
            }
            val dx2 = p3.point.x - p2.point.x
            val dy2 = p3.point.y - p2.point.y
            val norm2 = Point(dy2, -dx2)
            lastNorm = norm2
            val avgNorm = Point((norm1.x + norm2.x) * 0.5f, (norm1.y + norm2.y) * 0.5f)
            val length = Math.sqrt((avgNorm.x * avgNorm.x) + (avgNorm.y * avgNorm.y).toDouble())
            val norm = Point((avgNorm.x / length).toFloat(), (avgNorm.y / length).toFloat())
            val backVector = Point(norm.y, -norm.x)
            val forwardVector = Point(-norm.y, norm.x)
            val backLength = if (lastLength != null) {
                lastLength
            } else {
                (Math.sqrt(p1.point.distanceSquaredTo(p2.point).toDouble()) * 0.3666666667).toFloat()
            }
            p2.cp1 = Point(p2.point.x + (backVector.x * backLength), p2.point.y + (backVector.y * backLength))
            val forwardLength = (Math.sqrt(p2.point.distanceSquaredTo(p3.point).toDouble()) * 0.3666666667).toFloat()
            lastLength = forwardLength
            p2.cp2 = Point(p2.point.x + (forwardVector.x * forwardLength), p2.point.y + (forwardVector.y * forwardLength))
        }
    }
}