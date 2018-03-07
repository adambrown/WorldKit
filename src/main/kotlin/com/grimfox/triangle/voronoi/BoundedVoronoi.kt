package com.grimfox.triangle.voronoi

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.Predicates
import com.grimfox.triangle.geometry.Vertex
import com.grimfox.triangle.tools.IntersectionHelper

class BoundedVoronoi(
        mesh: Mesh,
        factory: IVoronoiFactory = DefaultVoronoiFactory(),
        predicates: Predicates = mesh.predicates) : VoronoiBase(mesh, factory, predicates) {

    internal var offset: Int = 0

    init {
        offset = vertices.size
        vertices.ensureCapacity(offset + mesh.hullsize)
        postProcess(mesh.behavior.disableExactMath)
        resolveBoundaryEdges()
    }

    private fun postProcess(noExact: Boolean) {
        for (edge in rays) {
            val twin = edge.twin
            val v1 = edge.face!!.generator as Vertex
            val v2 = twin!!.face!!.generator as Vertex
            val dir = predicates.counterClockwise(v1, v2, edge.origin!!, noExact)
            if (dir <= 0) {
                handleCase1(edge, v1, v2)
            } else {
                handleCase2(edge, v1, v2)
            }
        }
    }

    private fun handleCase1(edge: DcelHalfEdge, v1: Vertex, v2: Vertex) {
        val v = edge.twin!!.origin
        v!!.x = (v1.x + v2.x) / 2.0
        v.y = (v1.y + v2.y) / 2.0
        val gen = factory.createVertex(v1.x, v1.y)
        val h1 = factory.createHalfEdge(edge.twin!!.origin!!, edge.face!!)
        val h2 = factory.createHalfEdge(gen, edge.face!!)
        edge.next = h1
        h1.next = h2
        h2.next = edge.face!!.edge
        gen.leaving = h2
        edge.face!!.edge = h2
        super.halfEdges.add(h1)
        super.halfEdges.add(h2)
        val count = super.halfEdges.size
        h1.id = count
        h2.id = count + 1
        gen.id = offset++
        super.vertices.add(gen)
    }

    private fun handleCase2(edge: DcelHalfEdge, v1: Vertex, v2: Vertex) {
        val e1 = edge.twin!!.next
        val e2 = e1!!.twin!!.next
        IntersectionHelper.intersectSegments(v1, v2, e1.origin!!, e1.twin!!.origin!!, edge.twin!!.origin!!)
        IntersectionHelper.intersectSegments(v1, v2, e2!!.origin!!, e2.twin!!.origin!!, edge.origin!!)
        e1.twin!!.next = edge.twin
        edge.twin!!.next = e2
        edge.twin!!.face = e2.face
        e1.origin = edge.twin!!.origin
        edge.twin!!.twin = null
        edge.twin = null
        val gen = factory.createVertex(v1.x, v1.y)
        val he = factory.createHalfEdge(gen, edge.face!!)
        edge.next = he
        he.next = edge.face!!.edge
        edge.face!!.edge = he
        super.halfEdges.add(he)
        he.id = super.halfEdges.size
        gen.id = offset++
        super.vertices.add(gen)
    }
}
