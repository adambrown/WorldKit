package com.grimfox.gec.model

class SplinePoint(var point: Point, var cp1: Point = point, var cp2: Point = point) {

    fun interpolate(other: SplinePoint, interpolation: Float): SplinePoint {
        if (interpolation >= 1.0f) {
            return other
        }
        if (interpolation <= 0.0f) {
            return this
        }
        val dx1 = cp2.x - point.x
        val dy1 = cp2.y - point.y
        val dx2 = other.cp1.x - cp2.x
        val dy2 = other.cp1.y - cp2.y
        val dx3 = other.point.x - other.cp1.x
        val dy3 = other.point.y - other.cp1.y
        val p1x = point.x + dx1 * interpolation
        val p1y = point.y + dy1 * interpolation
        val p2x = cp2.x + dx2 * interpolation
        val p2y = cp2.y + dy2 * interpolation
        val p3x = other.cp1.x + dx3 * interpolation
        val p3y = other.cp1.y + dy3 * interpolation
        val qdx1 = p2x - p1x
        val qdy1 = p2y - p1y
        val qdx2 = p3x - p2x
        val qdy2 = p3y - p2y
        val qp1x = p1x + qdx1 * interpolation
        val qp1y = p1y + qdy1 * interpolation
        val qp2x = p2x + qdx2 * interpolation
        val qp2y = p2y + qdy2 * interpolation
        val dx = qp2x - qp1x
        val dy = qp2y - qp1y
        return SplinePoint(Point(qp1x + dx * interpolation, qp1y + dy * interpolation), Point(qp1x, qp1y), Point(qp2x, qp2y))
    }
}