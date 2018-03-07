package com.grimfox.triangle.voronoi

import com.grimfox.triangle.geometry.Point
import java.util.*

class DcelFace(
        var generator: Point?,
        var edge: DcelHalfEdge? = null) {

    companion object {

        val EMPTY: DcelFace = DcelFace(null)

        init {
            EMPTY.id = -1
        }
    }

    var id: Int = 0

    var mark: Int = 0

    var bounded: Boolean = true

    init {
        if (generator != null) {
            this.id = generator!!.id
        }
    }

    val edges: Iterable<DcelHalfEdge> by lazy {
        var edge = edge
        val first = edge?.id
        val halfEdges = ArrayList<DcelHalfEdge>(6)
        do {
            if (edge != null) {
                halfEdges.add(edge)
            } else {
                break
            }
            edge = edge.next
        } while (edge?.id != first)
        halfEdges
    }
}
