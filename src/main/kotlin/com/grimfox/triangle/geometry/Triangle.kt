package com.grimfox.triangle.geometry

import com.grimfox.triangle.Mesh
import java.util.*

class Triangle : ITriangle {

    val neighbors = arrayOf(OTri(), OTri(), OTri())

    val vertices = arrayOfNulls<Vertex>(3)

    val subsegs = arrayOf(OSub(), OSub(), OSub())

    var infected: Boolean = false

    var hash: Int = 0

    override var id: Int = 0

    override var label: Int = 0

    override var area: Double = 0.0

    override fun getVertex(index: Int): Vertex? {
        return this.vertices[index]
    }

    override fun getVertexId(index: Int): Int {
        return this.vertices[index]?.id ?: -2
    }

    override fun getNeighbor(index: Int): ITriangle? {
        return if (neighbors[index].triangle!!.hash == Mesh.DUMMY) null else neighbors[index].triangle
    }

    override fun getNeighborId(index: Int): Int {
        return if (neighbors[index].triangle!!.hash == Mesh.DUMMY) -1 else neighbors[index].triangle!!.id
    }

    override fun getSegment(index: Int): ISegment? {
        return if (subsegs[index].segment!!.hash == Mesh.DUMMY) null else subsegs[index].segment
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return this.hash
    }

    override fun toString(): String {
        return "Triangle(neighbors=${Arrays.toString(neighbors)}, vertices=${Arrays.toString(vertices)}, subsegs=${Arrays.toString(subsegs)}, infected=$infected, hash=$hash, id=$id, label=$label, area=$area)"
    }
}
