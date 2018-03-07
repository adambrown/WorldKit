package com.grimfox.triangle.geometry

open class Vertex(x: Double = 0.0, y: Double = 0.0, mark: Int = 0) : Point(x, y, mark, 0) {

    enum class VertexType {
        INPUT_VERTEX,
        SEGMENT_VERTEX,
        FREE_VERTEX,
        DEAD_VERTEX,
        UNDEAD_VERTEX
    }

    constructor(x: Double, y: Double) : this(x, y, 0)

    var hash: Int = 0

    var type = VertexType.INPUT_VERTEX

    val tri = OTri()

    override fun equals(other: Any?): Boolean = super.equals(other)

    override fun hashCode() = hash

    override fun toString(): String {
        return "Vertex(hash=$hash, type=$type, tri=$tri)"
    }
}
