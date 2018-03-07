package com.grimfox.triangle.geometry

class Edge(
        override var p0: Int = 0,
        override var p1: Int = 0,
        override var label: Int = 0) : IEdge {

    override fun toString(): String {
        return "Edge(p0=$p0, p1=$p1, label=$label)"
    }
}
