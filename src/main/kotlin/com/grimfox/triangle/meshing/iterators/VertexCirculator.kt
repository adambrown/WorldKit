package com.grimfox.triangle.meshing.iterators

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.ITriangle
import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.Vertex
import java.util.*

class VertexCirculator(mesh: Mesh) {

    internal var cache: MutableList<OTri> = ArrayList()

    init {
        mesh.makeVertexMap()
    }

    fun enumerateVertices(vertex: Vertex): Iterator<Vertex> {
        buildCache(vertex, true)
        return cache.map { it.dest()!! }.iterator()
    }

    fun enumerateTriangles(vertex: Vertex): Iterator<ITriangle> {
        buildCache(vertex, false)
        return cache.map { it.triangle!! }.iterator()
    }

    private fun buildCache(vertex: Vertex, vertices: Boolean) {
        cache.clear()
        val init = OTri()
        vertex.tri.copy(init)
        val next = OTri()
        val prev = OTri()
        init.copy(next)
        while (next.triangle!!.id != Mesh.DUMMY) {
            cache.add(next.copy())
            next.copy(prev)
            next.onext()
            if (next.equals(init)) {
                break
            }
        }
        if (next.triangle!!.id == Mesh.DUMMY) {
            init.copy(next)
            if (vertices) {
                prev.lnext()
                cache.add(prev.copy())
            }
            next.oprev()
            while (next.triangle!!.id != Mesh.DUMMY) {
                cache.add(0, next.copy())
                next.oprev()
                if (next.equals(init)) {
                    break
                }
            }
        }
    }
}
