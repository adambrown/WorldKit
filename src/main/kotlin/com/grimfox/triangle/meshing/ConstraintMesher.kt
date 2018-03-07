package com.grimfox.triangle.meshing

import com.grimfox.logging.LOG
import com.grimfox.triangle.*
import com.grimfox.triangle.Mesh.InsertVertexResult
import com.grimfox.triangle.TriangleLocator.LocateResult
import com.grimfox.triangle.geometry.*
import com.grimfox.triangle.geometry.Vertex.VertexType
import com.grimfox.triangle.meshing.iterators.RegionIterator
import java.util.*

class ConstraintMesher(internal var mesh: Mesh, config: Configuration) {

    enum class FindDirectionResult {
        WITHIN,
        LEFT_COLLINEAR,
        RIGHT_COLLINEAR
    }

    internal var predicates: Predicates = config.predicates.invoke()

    internal var behavior: Behavior = mesh.behavior

    internal var locator: TriangleLocator = mesh.locator

    internal var viri: ArrayList<Triangle> = ArrayList()

    fun apply(input: Polygon, options: ConstraintOptions?, noExact: Boolean) {
        behavior.isPlanarStraightLineGraph = input.segments.size > 0
        if (options != null) {
            behavior.makeConformingDelaunay = options.makeConformingDelaunay
            behavior.encloseConvexHull = options.encloseConvexHull
            behavior.boundarySplitMode = options.boundarySplitMode
            if (behavior.makeConformingDelaunay) {
                behavior.applyMeshQualityConstraints = true
            }
        }
        behavior.useRegions = input.regions.isNotEmpty()
        mesh.infvertex1 = null
        mesh.infvertex2 = null
        mesh.infvertex3 = null
        if (behavior.useSegments) {
            mesh.checksegments = true
            formSkeleton(input, noExact)
        }
        if (behavior.isPlanarStraightLineGraph && mesh._triangles.isNotEmpty()) {
            mesh._holes.addAll(input.holes)
            mesh.regions.addAll(input.regions)
            carveHoles(noExact)
        }
    }

    private fun carveHoles(noExact: Boolean) {
        val searchtri = OTri()
        var searchorg: Vertex
        var searchdest: Vertex
        var intersect: LocateResult
        var regionTris: Array<Triangle>? = null
        val dummytri = mesh.dummytri
        if (!mesh.behavior.encloseConvexHull) {
            infectHull()
        }
        if (!mesh.behavior.ignoreHolesInPolygons) {
            for (hole in mesh._holes) {
                if (mesh._bounds.contains(hole)) {
                    searchtri.triangle = dummytri
                    searchtri.orient = 0
                    searchtri.sym()
                    searchorg = searchtri.org()!!
                    searchdest = searchtri.dest()!!
                    if (predicates.counterClockwise(searchorg, searchdest, hole, noExact) > 0.0) {
                        intersect = mesh.locator.locate(hole, searchtri, noExact)
                        if (intersect !== LocateResult.OUTSIDE && !searchtri.isInfected) {
                            searchtri.infect()
                            viri.add(searchtri.triangle!!)
                        }
                    }
                }
            }
        }
        if (mesh.regions.size > 0) {
            regionTris = Array(mesh.regions.size) { dummytri }
            for ((i, region) in mesh.regions.withIndex()) {
                if (mesh._bounds.contains(region.point)) {
                    searchtri.triangle = dummytri
                    searchtri.orient = 0
                    searchtri.sym()
                    searchorg = searchtri.org()!!
                    searchdest = searchtri.dest()!!
                    if (predicates.counterClockwise(searchorg, searchdest, region.point, noExact) > 0.0) {
                        intersect = mesh.locator.locate(region.point, searchtri, noExact)
                        if (intersect !== LocateResult.OUTSIDE && !searchtri.isInfected) {
                            regionTris[i] = searchtri.triangle!!
                            regionTris[i].label = region.id
                            regionTris[i].area = region.area
                        }
                    }
                }
            }
        }
        if (viri.size > 0) {
            plague()
        }
        if (regionTris != null) {
            val iterator = RegionIterator()
            for (i in regionTris.indices) {
                if (regionTris[i].id != Mesh.DUMMY) {
                    if (!OTri.isDead(regionTris[i])) {
                        iterator.process(regionTris[i])
                    }
                }
            }
        }
        viri.clear()
    }

    private fun formSkeleton(input: Polygon, noExact: Boolean) {
        var p: Vertex
        var q: Vertex
        mesh.insegments = 0
        if (behavior.isPlanarStraightLineGraph) {
            if (mesh._triangles.isEmpty()) {
                return
            }
            if (input.segments.size > 0) {
                mesh.makeVertexMap()
            }
            for (seg in input.segments) {
                mesh.insegments++
                p = seg.getVertex(0)!!
                q = seg.getVertex(1)!!
                if (p.x == q.x && p.y == q.y) {
                    LOG.debug { "Endpoints of segment (IDs ${p.id}/${q.id}) are coincident." }
                } else {
                    insertSegment(p, q, seg.label, noExact)
                }
            }
        }
        if (behavior.encloseConvexHull || !behavior.isPlanarStraightLineGraph) {
            markHull()
        }
    }

    private fun infectHull() {
        val hulltri = OTri()
        val nexttri = OTri()
        val starttri = OTri()
        val hullsubseg = OSub()
        var horg: Vertex
        var hdest: Vertex
        val dummytri = mesh.dummytri
        hulltri.triangle = dummytri
        hulltri.orient = 0
        hulltri.sym()
        hulltri.copy(starttri)
        do {
            if (!hulltri.isInfected) {
                hulltri.pivot(hullsubseg)
                if (hullsubseg.segment!!.hash == Mesh.DUMMY) {
                    if (!hulltri.isInfected) {
                        hulltri.infect()
                        viri.add(hulltri.triangle!!)
                    }
                } else {
                    if (hullsubseg.segment!!.label == 0) {
                        hullsubseg.segment!!.label = 1
                        horg = hulltri.org()!!
                        hdest = hulltri.dest()!!
                        if (horg.label == 0) {
                            horg.label = 1
                        }
                        if (hdest.label == 0) {
                            hdest.label = 1
                        }
                    }
                }
            }
            hulltri.lnext()
            hulltri.oprev(nexttri)
            while (nexttri.triangle!!.id != Mesh.DUMMY) {
                nexttri.copy(hulltri)
                hulltri.oprev(nexttri)
            }
        } while (!hulltri.equals(starttri))
    }

    internal fun plague() {
        val testtri = OTri()
        val neighbor = OTri()
        val neighborsubseg = OSub()
        var testvertex: Vertex?
        var norg: Vertex
        var ndest: Vertex
        val dummysub = mesh.dummysub
        val dummytri = mesh.dummytri
        var killorg: Boolean
        var i = 0
        while (i < viri.size) {
            testtri.triangle = viri[i]
            testtri.uninfect()
            testtri.orient = 0
            while (testtri.orient < 3) {
                testtri.sym(neighbor)
                testtri.pivot(neighborsubseg)
                if (neighbor.triangle!!.id == Mesh.DUMMY || neighbor.isInfected) {
                    if (neighborsubseg.segment!!.hash != Mesh.DUMMY) {
                        mesh.subsegDealloc(neighborsubseg.segment!!)
                        if (neighbor.triangle!!.id != Mesh.DUMMY) {
                            neighbor.uninfect()
                            neighbor.segDissolve(dummysub)
                            neighbor.infect()
                        }
                    }
                } else {
                    if (neighborsubseg.segment!!.hash == Mesh.DUMMY) {
                        neighbor.infect()
                        viri.add(neighbor.triangle!!)
                    } else {
                        neighborsubseg.triDissolve(dummytri)
                        if (neighborsubseg.segment!!.label == 0) {
                            neighborsubseg.segment!!.label = 1
                        }
                        norg = neighbor.org()!!
                        ndest = neighbor.dest()!!
                        if (norg.label == 0) {
                            norg.label = 1
                        }
                        if (ndest.label == 0) {
                            ndest.label = 1
                        }
                    }
                }
                testtri.orient++
            }
            testtri.infect()
            i++
        }
        for (virus in viri) {
            testtri.triangle = virus
            testtri.orient = 0
            while (testtri.orient < 3) {
                testvertex = testtri.org()
                if (testvertex != null) {
                    killorg = true
                    testtri.setOrg(null)
                    testtri.onext(neighbor)
                    while (neighbor.triangle!!.id != Mesh.DUMMY && !neighbor.equals(testtri)) {
                        if (neighbor.isInfected) {
                            neighbor.setOrg(null)
                        } else {
                            killorg = false
                        }
                        neighbor.onext()
                    }
                    if (neighbor.triangle!!.id == Mesh.DUMMY) {
                        testtri.oprev(neighbor)
                        while (neighbor.triangle!!.id != Mesh.DUMMY) {
                            if (neighbor.isInfected) {
                                neighbor.setOrg(null)
                            } else {
                                killorg = false
                            }
                            neighbor.oprev()
                        }
                    }
                    if (killorg) {
                        testvertex.type = VertexType.UNDEAD_VERTEX
                        mesh.undeads++
                    }
                }
                testtri.orient++
            }
            testtri.orient = 0
            while (testtri.orient < 3) {
                testtri.sym(neighbor)
                if (neighbor.triangle!!.id == Mesh.DUMMY) {
                    mesh.hullsize = mesh.hullsize - 1
                } else {
                    neighbor.dissolve(dummytri)
                    mesh.hullsize = mesh.hullsize + 1
                }
                testtri.orient++
            }
            mesh.triangleDealloc(testtri.triangle!!)
        }
        viri.clear()
    }

    private fun findDirection(searchtri: OTri, searchpoint: Vertex, noExact: Boolean): FindDirectionResult {
        val checktri = OTri()
        var leftvertex: Vertex
        var rightvertex: Vertex
        var leftccw: Double
        var rightccw: Double
        var leftflag: Boolean
        var rightflag: Boolean
        val startvertex = searchtri.org()!!
        rightvertex = searchtri.dest()!!
        leftvertex = searchtri.apex()!!
        leftccw = predicates.counterClockwise(searchpoint, startvertex, leftvertex, noExact)
        leftflag = leftccw > 0.0
        rightccw = predicates.counterClockwise(startvertex, searchpoint, rightvertex, noExact)
        rightflag = rightccw > 0.0
        if (leftflag && rightflag) {
            searchtri.onext(checktri)
            if (checktri.triangle!!.id == Mesh.DUMMY) {
                leftflag = false
            } else {
                rightflag = false
            }
        }
        while (leftflag) {
            searchtri.onext()
            if (searchtri.triangle!!.id == Mesh.DUMMY) {
                LOG.error("Unable to find a com.grimfox.triangle on path.")
                throw Exception("Unable to find a com.grimfox.triangle on path.")
            }
            leftvertex = searchtri.apex()!!
            rightccw = leftccw
            leftccw = predicates.counterClockwise(searchpoint, startvertex, leftvertex, noExact)
            leftflag = leftccw > 0.0
        }
        while (rightflag) {
            searchtri.oprev()
            if (searchtri.triangle!!.id == Mesh.DUMMY) {
                LOG.error("Unable to find a com.grimfox.triangle on path.")
                throw Exception("Unable to find a com.grimfox.triangle on path.")
            }
            rightvertex = searchtri.dest()!!
            leftccw = rightccw
            rightccw = predicates.counterClockwise(startvertex, searchpoint, rightvertex, noExact)
            rightflag = rightccw > 0.0
        }
        if (leftccw == 0.0) {
            return FindDirectionResult.LEFT_COLLINEAR
        } else if (rightccw == 0.0) {
            return FindDirectionResult.RIGHT_COLLINEAR
        } else {
            return FindDirectionResult.WITHIN
        }
    }

    private fun segmentIntersection(splittri: OTri, splitsubseg: OSub, endpoint2: Vertex, noExact: Boolean) {
        val opposubseg = OSub()
        val newvertex: Vertex
        val dummysub = mesh.dummysub
        val ex: Double
        val ey: Double
        val tx: Double
        val ty: Double
        val etx: Double
        val ety: Double
        val split: Double
        val denom: Double
        val endpoint1 = splittri.apex()!!
        val torg = splittri.org()!!
        val tdest = splittri.dest()!!
        tx = tdest.x - torg.x
        ty = tdest.y - torg.y
        ex = endpoint2.x - endpoint1.x
        ey = endpoint2.y - endpoint1.y
        etx = torg.x - endpoint2.x
        ety = torg.y - endpoint2.y
        denom = ty * ex - tx * ey
        if (denom == 0.0) {
            LOG.error("Attempt to find intersection of parallel segments.")
            throw Exception("Attempt to find intersection of parallel segments.")
        }
        split = (ey * etx - ex * ety) / denom
        newvertex = Vertex(torg.x + split * (tdest.x - torg.x), torg.y + split * (tdest.y - torg.y), splitsubseg.segment!!.label)
        newvertex.hash = mesh.hash_vtx++
        newvertex.id = newvertex.hash
        mesh._vertices.put(newvertex.hash, newvertex)
        val success = mesh.insertVertex(newvertex, splittri, splitsubseg, false, false, noExact)
        if (success !== InsertVertexResult.SUCCESSFUL) {
            LOG.error("Failure to split a segment.")
            throw Exception("Failure to split a segment.")
        }
        splittri.copy(newvertex.tri)
        if (mesh.steinerleft > 0) {
            mesh.steinerleft = mesh.steinerleft - 1
        }
        splitsubseg.sym()
        splitsubseg.pivot(opposubseg)
        splitsubseg.dissolve(dummysub)
        opposubseg.dissolve(dummysub)
        do {
            splitsubseg.setSegOrg(newvertex)
            splitsubseg.next()
        } while (splitsubseg.segment!!.hash != Mesh.DUMMY)
        do {
            opposubseg.setSegOrg(newvertex)
            opposubseg.next()
        } while (opposubseg.segment!!.hash != Mesh.DUMMY)
        findDirection(splittri, endpoint1, noExact)
        val rightvertex = splittri.dest()!!
        val leftvertex = splittri.apex()!!
        if (leftvertex.x == endpoint1.x && leftvertex.y == endpoint1.y) {
            splittri.onext()
        } else if (rightvertex.x != endpoint1.x || rightvertex.y != endpoint1.y) {
            LOG.error("Topological inconsistency after splitting a segment.")
            throw Exception("Topological inconsistency after splitting a segment.")
        }
    }

    private fun scoutSegment(searchtri: OTri, endpoint2: Vertex, newmark: Int, noExact: Boolean): Boolean {
        val crosstri = OTri()
        val crosssubseg = OSub()
        val collinear = findDirection(searchtri, endpoint2, noExact)
        val rightvertex = searchtri.dest()!!
        val leftvertex = searchtri.apex()!!
        if (leftvertex.x == endpoint2.x && leftvertex.y == endpoint2.y || rightvertex.x == endpoint2.x && rightvertex.y == endpoint2.y) {
            if (leftvertex.x == endpoint2.x && leftvertex.y == endpoint2.y) {
                searchtri.lprev()
            }
            mesh.insertSubseg(searchtri, newmark)
            return true
        } else if (collinear === FindDirectionResult.LEFT_COLLINEAR) {
            searchtri.lprev()
            mesh.insertSubseg(searchtri, newmark)
            return scoutSegment(searchtri, endpoint2, newmark, noExact)
        } else
            if (collinear === FindDirectionResult.RIGHT_COLLINEAR) {
                mesh.insertSubseg(searchtri, newmark)
                searchtri.lnext()
                return scoutSegment(searchtri, endpoint2, newmark, noExact)
            } else {
                searchtri.lnext(crosstri)
                crosstri.pivot(crosssubseg)
                if (crosssubseg.segment!!.hash == Mesh.DUMMY) {
                    return false
                } else {
                    segmentIntersection(crosstri, crosssubseg, endpoint2, noExact)
                    crosstri.copy(searchtri)
                    mesh.insertSubseg(searchtri, newmark)
                    return scoutSegment(searchtri, endpoint2, newmark, noExact)
                }
            }
    }

    private fun delaunayFixup(fixuptri: OTri, leftside: Boolean, noExact: Boolean) {
        val neartri = OTri()
        val fartri = OTri()
        val faredge = OSub()
        val nearvertex: Vertex
        val leftvertex: Vertex
        val rightvertex: Vertex
        val farvertex: Vertex
        fixuptri.lnext(neartri)
        neartri.sym(fartri)
        if (fartri.triangle!!.id == Mesh.DUMMY) {
            return
        }
        neartri.pivot(faredge)
        if (faredge.segment!!.hash != Mesh.DUMMY) {
            return
        }
        nearvertex = neartri.apex()!!
        leftvertex = neartri.org()!!
        rightvertex = neartri.dest()!!
        farvertex = fartri.apex()!!
        if (leftside) {
            if (predicates.counterClockwise(nearvertex, leftvertex, farvertex, noExact) <= 0.0) {
                return
            }
        } else {
            if (predicates.counterClockwise(farvertex, rightvertex, nearvertex, noExact) <= 0.0) {
                return
            }
        }
        if (predicates.counterClockwise(rightvertex, leftvertex, farvertex, noExact) > 0.0) {
            if (predicates.inCircle(leftvertex, farvertex, rightvertex, nearvertex, noExact) <= 0.0) {
                return
            }
        }
        mesh.flip(neartri)
        fixuptri.lprev()
        delaunayFixup(fixuptri, leftside, noExact)
        delaunayFixup(fartri, leftside, noExact)
    }

    private fun constrainedEdge(starttri: OTri, endpoint2: Vertex, newmark: Int, noExact: Boolean) {
        val fixuptri = OTri()
        val fixuptri2 = OTri()
        val crosssubseg = OSub()
        var farvertex: Vertex
        var area: Double
        var collision: Boolean
        var done: Boolean
        val endpoint1 = starttri.org()!!
        starttri.lnext(fixuptri)
        mesh.flip(fixuptri)
        collision = false
        done = false
        do {
            farvertex = fixuptri.org()!!
            if (farvertex.x == endpoint2.x && farvertex.y == endpoint2.y) {
                fixuptri.oprev(fixuptri2)
                delaunayFixup(fixuptri, false, noExact)
                delaunayFixup(fixuptri2, true, noExact)
                done = true
            } else {
                area = predicates.counterClockwise(endpoint1, endpoint2, farvertex, noExact)
                if (area == 0.0) {
                    collision = true
                    fixuptri.oprev(fixuptri2)
                    delaunayFixup(fixuptri, false, noExact)
                    delaunayFixup(fixuptri2, true, noExact)
                    done = true
                } else {
                    if (area > 0.0) {
                        fixuptri.oprev(fixuptri2)
                        delaunayFixup(fixuptri2, true, noExact)
                        fixuptri.lprev()
                    } else {
                        delaunayFixup(fixuptri, false, noExact)
                        fixuptri.oprev()
                    }
                    fixuptri.pivot(crosssubseg)
                    if (crosssubseg.segment!!.hash == Mesh.DUMMY) {
                        mesh.flip(fixuptri)
                    } else {
                        collision = true
                        segmentIntersection(fixuptri, crosssubseg, endpoint2, noExact)
                        done = true
                    }
                }
            }
        } while (!done)
        mesh.insertSubseg(fixuptri, newmark)
        if (collision) {
            if (!scoutSegment(fixuptri, endpoint2, newmark, noExact)) {
                constrainedEdge(fixuptri, endpoint2, newmark, noExact)
            }
        }
    }

    private fun insertSegment(endpoint1: Vertex, endpoint2: Vertex, newmark: Int, noExact: Boolean) {
        val searchtri1 = OTri()
        val searchtri2 = OTri()
        var checkvertex: Vertex? = null
        val dummytri = mesh.dummytri
        endpoint1.tri.copy(searchtri1)
        if (searchtri1.triangle != null) {
            checkvertex = searchtri1.org()
        }
        if (checkvertex != endpoint1) {
            searchtri1.triangle = dummytri
            searchtri1.orient = 0
            searchtri1.sym()
            if (locator.locate(endpoint1, searchtri1, noExact) !== LocateResult.ON_VERTEX) {
                LOG.error("Unable to locate PSLG vertex in triangulation.")
                throw Exception("Unable to locate PSLG vertex in triangulation.")
            }
        }
        locator.update(searchtri1)
        if (scoutSegment(searchtri1, endpoint2, newmark, noExact)) {
            return
        }
        val newEndpoint1 = searchtri1.org()!!
        checkvertex = null
        endpoint2.tri.copy(searchtri2)
        if (searchtri2.triangle == null) {
            checkvertex = searchtri2.org()
        }
        if (checkvertex !== endpoint2) {
            searchtri2.triangle = dummytri
            searchtri2.orient = 0
            searchtri2.sym()
            if (locator.locate(endpoint2, searchtri2, noExact) !== LocateResult.ON_VERTEX) {
                LOG.error("Unable to locate PSLG vertex in triangulation.")
                throw Exception("Unable to locate PSLG vertex in triangulation.")
            }
        }
        locator.update(searchtri2)
        if (scoutSegment(searchtri2, newEndpoint1, newmark, noExact)) {
            return
        }
        val newEndpoint2 = searchtri2.org()!!
        constrainedEdge(searchtri1, newEndpoint2, newmark, noExact)
    }

    private fun markHull() {
        val hulltri = OTri()
        val nexttri = OTri()
        val starttri = OTri()
        hulltri.triangle = mesh.dummytri
        hulltri.orient = 0
        hulltri.sym()
        hulltri.copy(starttri)
        do {
            mesh.insertSubseg(hulltri, 1)
            hulltri.lnext()
            hulltri.oprev(nexttri)
            while (nexttri.triangle!!.id != Mesh.DUMMY) {
                nexttri.copy(hulltri)
                hulltri.oprev(nexttri)
            }
        } while (!hulltri.equals(starttri))
    }
}
