package com.grimfox.triangle.geometry

class Segment(
        internal var v0: Vertex,
        internal var v1: Vertex,
        override var label: Int = 0) : ISegment {

    override val p0: Int
        get() = v0.id

    override val p1: Int
        get() = v1.id

    override fun getVertex(index: Int): Vertex? {
        if (index == 0) {
            return v0
        }
        if (index == 1) {
            return v1
        }
        throw IndexOutOfBoundsException()
    }

    override fun getTriangle(index: Int): ITriangle? {
        throw UnsupportedOperationException()
    }

    override fun toString(): String {
        return "Segment(v0=$v0, v1=$v1, label=$label)"
    }
}
