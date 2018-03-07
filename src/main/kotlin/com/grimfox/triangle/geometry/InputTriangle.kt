package com.grimfox.triangle.geometry

import java.util.*

class InputTriangle(p0: Int, p1: Int, p2: Int) : ITriangle {

    private val vertices: IntArray = intArrayOf(p0, p1, p2)

    override var id: Int
        get() = 0
        set(value) {}

    override var label: Int = 0

    override var area: Double = 0.0

    override fun getVertexId(index: Int): Int {
        return vertices[index]
    }

    override fun getVertex(index: Int): Vertex? {
        return null
    }

    override fun getNeighbor(index: Int): ITriangle? {
        return null
    }

    override fun getNeighborId(index: Int): Int {
        return -1
    }

    override fun getSegment(index: Int): ISegment? {
        return null
    }

    override fun toString(): String {
        return "InputTriangle(vertices=${Arrays.toString(vertices)}, label=$label, area=$area)"
    }
}
