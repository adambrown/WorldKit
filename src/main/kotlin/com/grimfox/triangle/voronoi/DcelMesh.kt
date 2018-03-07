package com.grimfox.triangle.voronoi

import com.grimfox.triangle.geometry.Edge
import com.grimfox.triangle.geometry.IEdge
import java.util.*

open class DcelMesh protected constructor(initialize: Boolean) {

    constructor() : this(true)

    lateinit var vertices: ArrayList<DcelVertex>
        protected set

    lateinit var halfEdges: ArrayList<DcelHalfEdge>
        protected set

    lateinit var faces: ArrayList<DcelFace>
        protected set

    init {
        if (initialize) {
            vertices = ArrayList<DcelVertex>()
            halfEdges = ArrayList<DcelHalfEdge>()
            faces = ArrayList<DcelFace>()
        }
    }

    val edges: ArrayList<IEdge> by lazy {
        enumerateEdges()
    }

    fun isConsistent(closed: Boolean, depth: Int): Boolean {
        for (vertex in vertices) {
            if (vertex.id < 0) {
                continue
            }
            if (vertex.leaving == null) {
                return false
            }
            if (vertex.leaving!!.origin!!.id != vertex.id) {
                return false
            }
        }
        for (face in faces) {
            if (face.id < 0) {
                continue
            }
            if (face.edge == null) {
                return false
            }
            if (face.id != face.edge!!.face!!.id) {
                return false
            }
        }
        for (edge in halfEdges) {
            if (edge.id < 0) {
                continue
            }
            if (edge.twin == null) {
                return false
            }
            if (edge.origin == null) {
                return false
            }
            if (edge.face == null) {
                return false
            }
            if (closed && edge.next == null) {
                return false
            }
        }
        for (edge in halfEdges) {
            if (edge.id < 0) {
                continue
            }
            val twin = edge.twin
            val next = edge.next
            if (edge.id != twin!!.twin!!.id) {
                return false
            }
            if (closed) {
                if (next!!.origin!!.id != twin.origin!!.id) {
                    return false
                }
                if (next.twin!!.next!!.origin!!.id != edge.twin!!.origin!!.id) {
                    return false
                }
            }
        }
        if (closed && depth > 0) {
            for (face in faces) {
                if (face.id < 0) {
                    continue
                }
                val edge = face.edge!!
                var next = edge.next
                val id = edge.id
                var k = 0
                while (next!!.id != id && k < depth) {
                    next = next.next
                    k++
                }
                if (next.id != id) {
                    return false
                }
            }
        }
        return true
    }

    fun resolveBoundaryEdges() {
        val map = HashMap<Int, DcelHalfEdge>()
        for (edge in this.halfEdges) {
            if (edge.twin == null) {
                edge.twin = DcelHalfEdge(edge.next!!.origin, DcelFace.EMPTY)
                val twin = edge.twin
                twin!!.twin = edge
                map.put(twin.origin!!.id, twin)
            }
        }
        var j = halfEdges.size
        for (edge in map.values) {
            edge.id = j++
            edge.next = map[edge.twin!!.origin!!.id]
            this.halfEdges.add(edge)
        }
    }

    protected open fun enumerateEdges(): ArrayList<IEdge> {
        val edges = ArrayList<IEdge>(this.halfEdges.size / 2)
        for (edge in this.halfEdges) {
            val twin = edge.twin
            if (edge.id < twin!!.id) {
                edges.add(Edge(edge.origin!!.id, twin.origin!!.id, 0))
            }
        }
        return edges
    }
}
