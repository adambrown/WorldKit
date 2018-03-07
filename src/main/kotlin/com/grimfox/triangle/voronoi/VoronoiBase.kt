package com.grimfox.triangle.voronoi

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.Predicates
import com.grimfox.triangle.Reference
import com.grimfox.triangle.geometry.*
import java.util.*

abstract class VoronoiBase protected constructor(
        mesh: Mesh,
        protected var factory: IVoronoiFactory = DefaultVoronoiFactory(),
        protected var predicates: Predicates = mesh.predicates,
        generate: Boolean = true) : DcelMesh(false) {

    protected var rays: MutableList<DcelHalfEdge> = mutableListOf()

    init {
        if (generate) {
            generate(mesh)
        }
    }

    protected fun generate(mesh: Mesh) {
        mesh.renumber()
        super.halfEdges = ArrayList<DcelHalfEdge>()
        this.rays = ArrayList<DcelHalfEdge>()
        val vertices = arrayOfNulls<DcelVertex>(mesh._triangles.size + mesh.hullsize)
        val faces = arrayOfNulls<DcelFace>(mesh._vertices.size)
        factory.initialize(vertices.size, 2 * mesh.numberOfEdges, faces.size)
        val map = computeVertices(mesh, vertices, mesh.behavior.disableExactMath)
        for (vertex in mesh._vertices.values) {
            faces[vertex.id] = factory.createFace(vertex)
        }
        computeEdges(mesh, vertices, faces, map)
        connectEdges(map)
        super.vertices = ArrayList(Arrays.asList<DcelVertex>(*vertices))
        super.faces = ArrayList(Arrays.asList<DcelFace>(*faces))
    }

    protected fun computeVertices(mesh: Mesh, vertices: Array<DcelVertex?>, noExact: Boolean): List<MutableList<DcelHalfEdge>> {
        val tri = OTri()
        val xi = Reference(0.0)
        val eta = Reference(0.0)
        var vertex: DcelVertex
        var pt: Point
        var id: Int
        val map = ArrayList<MutableList<DcelHalfEdge>>(mesh._triangles.size)
        for (t in mesh._triangles) {
            id = t.id
            tri.triangle = t
            pt = predicates.findCircumcenter(tri.org()!!, tri.dest()!!, tri.apex()!!, xi, eta, noExact)
            vertex = factory.createVertex(pt.x, pt.y)
            vertex.id = id
            vertices[id] = vertex
            map.add(id, ArrayList<DcelHalfEdge>())
        }
        return map
    }

    protected fun computeEdges(mesh: Mesh, vertices: Array<DcelVertex?>, faces: Array<DcelFace?>, map: List<MutableList<DcelHalfEdge>>) {
        val tri = OTri()
        val neighbor = OTri()
        var org: Vertex
        var dest: Vertex
        var px: Double
        var py: Double
        var id: Int
        var nid: Int
        val count = mesh._triangles.size
        var face: DcelFace?
        var neighborFace: DcelFace?
        var edge: DcelHalfEdge
        var twin: DcelHalfEdge
        var vertex: DcelVertex?
        var end: DcelVertex?
        var j = 0
        var k = 0
        for (t in mesh._triangles) {
            id = t.id
            tri.triangle = t
            for (i in 0..2) {
                tri.orient = i
                tri.sym(neighbor)
                nid = neighbor.triangle!!.id
                if (id < nid || nid < 0) {
                    org = tri.org()!!
                    dest = tri.dest()!!
                    face = faces[org.id]
                    neighborFace = faces[dest.id]
                    vertex = vertices[id]
                    if (nid < 0) {
                        px = dest.y - org.y
                        py = org.x - dest.x
                        end = factory.createVertex(vertex!!.x + px, vertex.y + py)
                        end.id = count + j++
                        vertices[end.id] = end
                        edge = factory.createHalfEdge(end, face!!)
                        twin = factory.createHalfEdge(vertex, neighborFace!!)
                        face.edge = edge
                        face.bounded = false
                        map[id].add(twin)
                        rays.add(twin)
                    } else {
                        end = vertices[nid]
                        edge = factory.createHalfEdge(end!!, face!!)
                        twin = factory.createHalfEdge(vertex!!, neighborFace!!)
                        map[nid].add(edge)
                        map[id].add(twin)
                    }
                    vertex.leaving = twin
                    end.leaving = edge
                    edge.twin = twin
                    twin.twin = edge
                    edge.id = k++
                    twin.id = k++
                    this.halfEdges.add(edge)
                    this.halfEdges.add(twin)
                }
            }
        }
    }

    protected fun connectEdges(map: List<List<DcelHalfEdge>>) {
        val length = map.size
        for (edge in this.halfEdges) {
            val face = edge.face!!.generator!!.id
            val id = edge.twin!!.origin!!.id
            if (id < length) {
                for (next in map[id]) {
                    if (next.face!!.generator!!.id == face) {
                        edge.next = next
                        break
                    }
                }
            }
        }
    }

    override fun enumerateEdges(): ArrayList<IEdge> {
        val edges = ArrayList<IEdge>(this.halfEdges.size / 2)
        for (edge in this.halfEdges) {
            val twin = edge.twin
            if (twin == null) {
                edges.add(Edge(edge.origin!!.id, edge.next!!.origin!!.id, 0))
            } else if (edge.id < twin.id) {
                edges.add(Edge(edge.origin!!.id, twin.origin!!.id, 0))
            }
        }
        return edges
    }
}
