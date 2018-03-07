package com.grimfox.triangle.meshing

import com.grimfox.logging.LOG
import com.grimfox.triangle.Configuration
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.*
import com.grimfox.triangle.voronoi.DcelFace
import com.grimfox.triangle.voronoi.DcelHalfEdge
import com.grimfox.triangle.voronoi.DcelMesh
import com.grimfox.triangle.voronoi.DcelVertex
import java.util.*

object Converter {

    fun toMesh(polygon: Polygon, triangles: List<ITriangle>): Mesh {
        val tri = OTri()
        val subseg = OSub()
        val elements = triangles.size
        val segments = polygon.segments.size
        val mesh = Mesh(Configuration())
        mesh.transferNodes(polygon.points)
        mesh.regions.addAll(polygon.regions)
        mesh.behavior.useRegions = polygon.regions.isNotEmpty()
        if (polygon.segments.size > 0) {
            mesh.behavior.isPlanarStraightLineGraph = true
            mesh._holes.addAll(polygon.holes)
        }
        var i = 0
        while (i < elements) {
            mesh.makeTriangle(tri)
            i++
        }
        if (mesh.behavior.isPlanarStraightLineGraph) {
            mesh.insegments = segments
            i = 0
            while (i < segments) {
                mesh.makeSegment(subseg)
                i++
            }
        }
        val vertexarray = setNeighbors(mesh, triangles)
        setSegments(mesh, polygon, vertexarray)
        return mesh
    }

    private fun setNeighbors(mesh: Mesh, triangles: List<ITriangle>): List<MutableList<OTri>> {
        val tri = OTri()
        val triangleleft = OTri()
        val checktri = OTri()
        val checkleft = OTri()
        val nexttri = OTri()
        var tdest: Vertex
        var tapex: Vertex
        var checkdest: Vertex
        var checkapex: Vertex
        val corner = IntArray(3)
        var aroundvertex: Int
        val vertexarray = ArrayList<MutableList<OTri>>(mesh._vertices.size)
        var i = 0
        while (i < mesh._vertices.size) {
            val tmp = OTri()
            tmp.triangle = mesh.dummytri
            vertexarray.add(ArrayList<OTri>(3))
            vertexarray[i].add(tmp.copy())
            i++
        }
        i = 0
        for (item in mesh._triangles) {
            tri.triangle = item
            for (j in 0..2) {
                corner[j] = triangles[i].getVertexId(j)
                if (corner[j] < 0 || corner[j] >= mesh.numberOfInputPoints) {
                    LOG.error("Triangle has an invalid vertex index.")
                    throw Exception("Triangle has an invalid vertex index.")
                }
            }
            tri.triangle!!.label = triangles[i].label
            if (mesh.behavior.applyMaxTriangleAreaConstraints) {
                tri.triangle!!.area = triangles[i].area
            }
            tri.orient = 0
            tri.setOrg(mesh._vertices[corner[0]])
            tri.setDest(mesh._vertices[corner[1]])
            tri.setApex(mesh._vertices[corner[2]])
            tri.orient = 0
            while (tri.orient < 3) {
                aroundvertex = corner[tri.orient]
                var index = vertexarray[aroundvertex].size - 1
                vertexarray[aroundvertex][index].copy(nexttri)
                vertexarray[aroundvertex].add(tri.copy())
                nexttri.copy(checktri)
                if (checktri.triangle!!.id != Mesh.DUMMY) {
                    tdest = tri.dest()!!
                    tapex = tri.apex()!!
                    do {
                        checkdest = checktri.dest()!!
                        checkapex = checktri.apex()!!
                        if (tapex === checkdest) {
                            tri.lprev(triangleleft)
                            triangleleft.bond(checktri)
                        }
                        if (tdest === checkapex) {
                            checktri.lprev(checkleft)
                            tri.bond(checkleft)
                        }
                        index--
                        vertexarray[aroundvertex][index].copy(nexttri)
                        nexttri.copy(checktri)
                    } while (checktri.triangle!!.id != Mesh.DUMMY)
                }
                tri.orient++
            }
            i++
        }
        return vertexarray
    }

    private fun setSegments(mesh: Mesh, polygon: Polygon, vertexarray: List<MutableList<OTri>>) {
        val checktri = OTri()
        val nexttri = OTri()
        var checkdest: Vertex
        val checkneighbor = OTri()
        val subseg = OSub()
        val prevlink = OTri()
        var tmp: Vertex
        var sorg: Vertex
        var sdest: Vertex
        var notfound: Boolean
        var boundmarker: Int
        var aroundvertex: Int
        var i: Int
        var hullsize = 0
        if (mesh.behavior.isPlanarStraightLineGraph) {
            i = 0
            for (item in mesh._subsegs.values) {
                subseg.segment = item
                sorg = polygon.segments[i].getVertex(0)!!
                sdest = polygon.segments[i].getVertex(1)!!
                boundmarker = polygon.segments[i].label
                if (sorg.id < 0 || sorg.id >= mesh.numberOfInputPoints || sdest.id < 0 || sdest.id >= mesh.numberOfInputPoints) {
                    LOG.error("Segment has an invalid vertex index.")
                    throw Exception("Segment has an invalid vertex index.")
                }
                subseg.orient = 0
                subseg.setOrg(sorg)
                subseg.setDest(sdest)
                subseg.setSegOrg(sorg)
                subseg.setSegDest(sdest)
                subseg.segment!!.label = boundmarker
                subseg.orient = 0
                while (subseg.orient < 2) {
                    aroundvertex = if (subseg.orient == 1) sorg.id else sdest.id
                    var index = vertexarray[aroundvertex].size - 1
                    vertexarray[aroundvertex][index].copy(prevlink)
                    vertexarray[aroundvertex][index].copy(nexttri)
                    nexttri.copy(checktri)
                    tmp = subseg.org()!!
                    notfound = true
                    while (notfound && checktri.triangle!!.id != Mesh.DUMMY) {
                        checkdest = checktri.dest()!!
                        if (tmp === checkdest) {
                            vertexarray[aroundvertex].remove(prevlink)
                            checktri.segBond(subseg)
                            checktri.sym(checkneighbor)
                            if (checkneighbor.triangle!!.id == Mesh.DUMMY) {
                                mesh.insertSubseg(checktri, 1)
                                hullsize++
                            }
                            notfound = false
                        }
                        index--
                        vertexarray[aroundvertex][index].copy(prevlink)
                        vertexarray[aroundvertex][index].copy(nexttri)
                        nexttri.copy(checktri)
                    }
                    subseg.orient++
                }
                i++
            }
        }
        i = 0
        while (i < mesh._vertices.size) {
            var index = vertexarray[i].size - 1
            vertexarray[i][index].copy(nexttri)
            nexttri.copy(checktri)
            while (checktri.triangle!!.id != Mesh.DUMMY) {
                index--
                vertexarray[i][index].copy(nexttri)
                checktri.segDissolve(mesh.dummysub)
                checktri.sym(checkneighbor)
                if (checkneighbor.triangle!!.id == Mesh.DUMMY) {
                    mesh.insertSubseg(checktri, 1)
                    hullsize++
                }
                nexttri.copy(checktri)
            }
            i++
        }
        mesh.hullsize = hullsize
    }

    fun toDCEL(mesh: Mesh): DcelMesh {
        val dcel = DcelMesh()
        val vertices = arrayOfNulls<DcelVertex>(mesh._vertices.size)
        val faces = arrayOfNulls<DcelFace>(mesh._triangles.size)
        dcel.halfEdges.ensureCapacity(2 * mesh.numberOfEdges)
        mesh.renumber()
        var vertex: DcelVertex
        for (v in mesh._vertices.values) {
            vertex = DcelVertex(v.x, v.y)
            vertex.id = v.id
            vertex.label = v.label
            vertices[v.id] = vertex
        }
        val map = ArrayList<MutableList<DcelHalfEdge>>(mesh._triangles.size)
        var face: DcelFace
        for (t in mesh._triangles) {
            face = DcelFace(null)
            face.id = t.id
            faces[t.id] = face
            map[t.id] = ArrayList<DcelHalfEdge>(3)
        }
        val tri = OTri()
        val neighbor = OTri()
        var org: Vertex
        var dest: Vertex
        var id: Int
        var nid: Int
        var edge: DcelHalfEdge
        var twin: DcelHalfEdge
        var next: DcelHalfEdge
        val edges = dcel.halfEdges
        var k = 0
        val boundary = HashMap<Int, DcelHalfEdge>()
        for (t in mesh._triangles) {
            id = t.id
            tri.triangle = t
            for (i in 0..2) {
                tri.orient = i
                tri.sym(neighbor)
                nid = neighbor.triangle!!.id
                if (id < nid || nid < 0) {
                    face = faces[id]!!
                    org = tri.org()!!
                    dest = tri.dest()!!
                    edge = DcelHalfEdge(vertices[org.id]!!, face)
                    twin = DcelHalfEdge(vertices[dest.id]!!, if (nid < 0) DcelFace.EMPTY else faces[nid])
                    map[id].add(edge)
                    if (nid >= 0) {
                        map[nid].add(twin)
                    } else {
                        boundary.put(dest.id, twin)
                    }
                    edge.origin!!.leaving = edge
                    twin.origin!!.leaving = twin
                    edge.twin = twin
                    twin.twin = edge
                    edge.id = k++
                    twin.id = k++
                    edges.add(edge)
                    edges.add(twin)
                }
            }
        }
        for (t in map) {
            edge = t[0]
            next = t[1]
            if (edge.twin!!.origin!!.id == next.origin!!.id) {
                edge.next = next
                next.next = t[2]
                t[2].next = edge
            } else {
                edge.next = t[2]
                next.next = edge
                t[2].next = next
            }
        }
        for (e in boundary.values) {
            e.next = boundary[e.twin!!.origin!!.id]
        }
        dcel.vertices.addAll(vertices.filterNotNull())
        dcel.faces.addAll(Arrays.asList<DcelFace>(*faces))
        return dcel
    }
}
