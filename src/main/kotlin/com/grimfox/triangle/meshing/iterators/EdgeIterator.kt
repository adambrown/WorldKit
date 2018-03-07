package com.grimfox.triangle.meshing.iterators

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.*

class EdgeIterator(mesh: Mesh) : Iterator<Edge> {

    private var triangles: Iterator<Triangle> = mesh._triangles.iterator()

    private val tri = OTri()

    private val neighbor = OTri()

    private val sub = OSub()

    private var current: Edge? = null

    internal var p1: Vertex? = null

    internal var p2: Vertex? = null

    init {
        if (triangles.hasNext()) {
            tri.triangle = triangles.next()
            tri.orient = 0
        }
        advance()
    }

    private fun advance(): Boolean {
        if (tri.triangle == null) {
            return false
        }
        current = null
        while (current == null) {
            if (tri.orient == 3) {
                if (triangles.hasNext()) {
                    tri.triangle = triangles.next()
                    tri.orient = 0
                } else {
                    return false
                }
            }
            tri.sym(neighbor)
            if (tri.triangle!!.id < neighbor.triangle!!.id || neighbor.triangle!!.id == Mesh.DUMMY) {
                p1 = tri.org()
                p2 = tri.dest()
                tri.pivot(sub)
                current = Edge(p1!!.id, p2!!.id, sub.segment!!.label)
            }
            tri.orient++
        }
        return true
    }

    override fun hasNext(): Boolean {
        return current != null
    }

    override fun next(): Edge {
        val temp = current!!
        advance()
        return temp
    }
}
