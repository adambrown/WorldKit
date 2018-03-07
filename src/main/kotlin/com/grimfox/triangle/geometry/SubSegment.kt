package com.grimfox.triangle.geometry

import com.grimfox.triangle.Mesh
import java.util.*

class SubSegment : ISegment {

    var hash: Int = 0

    val subsegs = arrayOf(OSub(), OSub())

    val vertices = arrayOfNulls<Vertex>(4)

    val triangles = arrayOf(OTri(), OTri())

    override var label = 0

    override val p0: Int
        get() = this.vertices[0]?.id ?: -1

    override val p1: Int
        get() = this.vertices[1]?.id ?: -1

    override fun getVertex(index: Int): Vertex? {
        return this.vertices[index]
    }

    override fun getTriangle(index: Int): ITriangle? {
        return if (triangles[index].triangle!!.hash == Mesh.DUMMY) null else triangles[index].triangle
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return this.hash
    }

    override fun toString(): String {
        return "SubSegment(hash=$hash, subsegs=${Arrays.toString(subsegs)}, vertices=${Arrays.toString(vertices)}, triangles=${Arrays.toString(triangles)}, label=$label)"
    }
}
