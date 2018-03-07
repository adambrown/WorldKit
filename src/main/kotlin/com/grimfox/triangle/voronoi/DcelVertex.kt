package com.grimfox.triangle.voronoi

import com.grimfox.triangle.geometry.Point
import java.util.*

class DcelVertex(
        x: Double,
        y: Double,
        var leaving: DcelHalfEdge? = null) : Point(x, y, 0, 0) {

    val edges: Iterable<DcelHalfEdge> by lazy {
        var edge = leaving
        val first = edge?.id
        val halfEdges = ArrayList<DcelHalfEdge>(6)
        do {
            if (edge != null) {
                halfEdges.add(edge)
            }
            edge = edge?.twin?.next
        } while (edge?.id != first)
        halfEdges
    }
}
