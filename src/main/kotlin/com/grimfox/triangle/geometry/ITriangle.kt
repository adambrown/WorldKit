package com.grimfox.triangle.geometry

interface ITriangle {

    var id: Int

    var label: Int

    var area: Double

    fun getVertex(index: Int): Vertex?

    fun getVertexId(index: Int): Int

    fun getNeighbor(index: Int): ITriangle?

    fun getNeighborId(index: Int): Int

    fun getSegment(index: Int): ISegment?

    fun contains(p: Point): Boolean {
        return contains(p.x, p.y)
    }

    fun contains(x: Double, y: Double): Boolean {
        val t0 = this.getVertex(0)
        val t1 = this.getVertex(1)
        val t2 = this.getVertex(2)
        if (t0 == null || t1 == null || t2 == null) {
            return false
        }
        val d0 = Point(t1.x - t0.x, t1.y - t0.y)
        val d1 = Point(t2.x - t0.x, t2.y - t0.y)
        val d2 = Point(x - t0.x, y - t0.y)
        val c0 = Point(-d0.y, d0.x)
        val c1 = Point(-d1.y, d1.x)
        val s = dotProduct(d2, c1) / dotProduct(d0, c1)
        val v = dotProduct(d2, c0) / dotProduct(d1, c0)
        if (s >= 0 && v >= 0 && s + v <= 1) {
            return true
        }
        return false
    }

    fun bounds(): Rectangle {
        val bounds = Rectangle()
        for (i in 0..2) {
            bounds.expand(getVertex(i)!!)
        }
        return bounds
    }

    private fun dotProduct(p: Point, q: Point): Double {
        return p.x * q.x + p.y * q.y
    }
}
