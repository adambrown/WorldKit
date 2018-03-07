package com.grimfox.triangle.meshing.algorithm

import com.grimfox.logging.LOG
import com.grimfox.triangle.Configuration
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.Mesh.InsertVertexResult
import com.grimfox.triangle.geometry.OSub
import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.Vertex
import com.grimfox.triangle.geometry.Vertex.VertexType

class Incremental : TriangulationAlgorithm {

    override fun triangulate(points: List<Vertex>, config: Configuration): Mesh {
        val mesh = Mesh(config)
        mesh.transferNodes(points)
        val startTri = OTri()
        getBoundingBox(mesh)
        val noExact = mesh.behavior.disableExactMath
        for (v in mesh._vertices.values) {
            startTri.triangle = mesh.dummytri
            val tmp = OSub()
            if (mesh.insertVertex(v, startTri, tmp, false, false, noExact) == InsertVertexResult.DUPLICATE) {
                LOG.debug("A duplicate vertex appeared and was ignored.")
                v.type = VertexType.UNDEAD_VERTEX
                mesh.undeads++
            }
        }
        mesh.hullsize = removeBox(mesh)
        return mesh
    }

    private fun getBoundingBox(mesh: Mesh) {
        val infinityTri = OTri()
        val box = mesh._bounds
        var width = box.width
        if (box.height > width) {
            width = box.height
        }
        if (width == 0.0) {
            width = 1.0
        }
        mesh.infvertex1 = Vertex(box.left - 50.0 * width, box.bottom - 40.0 * width)
        mesh.infvertex2 = Vertex(box.right + 50.0 * width, box.bottom - 40.0 * width)
        mesh.infvertex3 = Vertex(0.5 * (box.left + box.right), box.top + 60.0 * width)
        mesh.makeTriangle(infinityTri)
        infinityTri.setOrg(mesh.infvertex1)
        infinityTri.setDest(mesh.infvertex2)
        infinityTri.setApex(mesh.infvertex3)
        infinityTri.copy(mesh.dummytri.neighbors[0])
    }

    internal fun removeBox(mesh: Mesh): Int {
        val deadTriangle = OTri()
        val searchEdge = OTri()
        val checkEdge = OTri()
        val nextEdge = OTri()
        val finalEdge = OTri()
        val dissolveEdge = OTri()
        var markOrg: Vertex
        val noPoly = !mesh.behavior.isPlanarStraightLineGraph
        nextEdge.triangle = mesh.dummytri
        nextEdge.orient = 0
        nextEdge.sym()
        nextEdge.lprev(finalEdge)
        nextEdge.lnext()
        nextEdge.sym()
        nextEdge.lprev(searchEdge)
        searchEdge.sym()
        nextEdge.lnext(checkEdge)
        checkEdge.sym()
        if (checkEdge.triangle!!.id == Mesh.DUMMY) {
            searchEdge.lprev()
            searchEdge.sym()
        }
        mesh.dummytri.neighbors[0] = searchEdge
        var hullSize = -2
        while (!nextEdge.equals(finalEdge)) {
            hullSize++
            nextEdge.lprev(dissolveEdge)
            dissolveEdge.sym()
            if (noPoly) {
                if (dissolveEdge.triangle!!.id != Mesh.DUMMY) {
                    markOrg = dissolveEdge.org()!!
                    if (markOrg.label == 0) {
                        markOrg.label = 1
                    }
                }
            }
            dissolveEdge.dissolve(mesh.dummytri)
            nextEdge.lnext(deadTriangle)
            deadTriangle.sym(nextEdge)
            mesh.triangleDealloc(deadTriangle.triangle!!)
            if (nextEdge.triangle!!.id == Mesh.DUMMY) {
                dissolveEdge.copy(nextEdge)
            }
        }
        mesh.triangleDealloc(finalEdge.triangle!!)
        return hullSize
    }
}
