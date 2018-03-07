package com.grimfox.triangle.voronoi

class DcelHalfEdge(
        var origin: DcelVertex?,
        var face: DcelFace? = null) {

    var id: Int = 0

    var boundary: Int = 0

    var twin: DcelHalfEdge? = null

    var next: DcelHalfEdge? = null

    init {
        val face = face
        if (face != null && face.edge == null) {
            face.edge = this
        }
    }
}
