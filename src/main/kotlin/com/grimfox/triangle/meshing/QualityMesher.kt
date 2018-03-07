package com.grimfox.triangle.meshing

import com.grimfox.logging.LOG
import com.grimfox.triangle.*
import com.grimfox.triangle.Behavior.BoundarySplitMode
import com.grimfox.triangle.Mesh.InsertVertexResult
import com.grimfox.triangle.geometry.*
import com.grimfox.triangle.geometry.Vertex.VertexType
import com.grimfox.triangle.meshing.data.BadSubsegment
import com.grimfox.triangle.meshing.data.BadTriQueue
import com.grimfox.triangle.meshing.data.BadTriangle
import java.util.*

class QualityMesher(
        internal var mesh: Mesh,
        config: Configuration) {

    internal var predicates: Predicates = config.predicates.invoke()

    internal var badsubsegs: LinkedList<BadSubsegment> = LinkedList()

    internal var queue: BadTriQueue = BadTriQueue()

    internal var behavior: Behavior = mesh.behavior

    internal var newLocation: NewLocation

    internal var newvertex_tri: Triangle = Triangle()

    init {
        newLocation = NewLocation(mesh, predicates)
    }

    fun apply(quality: QualityOptions?, noExact: Boolean) {
        apply(quality, false, noExact)
    }

    fun apply(quality: QualityOptions?, delaunay: Boolean, noExact: Boolean) {
        if (quality != null) {
            behavior.applyMeshQualityConstraints = true
            behavior.minAngleConstraint = quality.minimumAngle
            behavior.maxAngleConstraint = quality.maximumAngle
            behavior.maxAreaConstraint = quality.maximumArea
            behavior.userDefinedTriangleConstraint = quality.userTest
            behavior.applyMaxTriangleAreaConstraints = quality.constrainArea
            behavior.makeConformingDelaunay = behavior.makeConformingDelaunay || delaunay
            mesh.steinerleft = if (quality.steinerPoints == 0) -1 else quality.steinerPoints
        }
        if (!behavior.isPlanarStraightLineGraph) {
            behavior.applyMaxTriangleAreaConstraints = false
        }
        mesh.infvertex1 = null
        mesh.infvertex2 = null
        mesh.infvertex3 = null
        if (behavior.useSegments) {
            mesh.checksegments = true
        }
        if (behavior.applyMeshQualityConstraints && mesh._triangles.isNotEmpty()) {
            enforceQuality(noExact)
        }
    }

    fun addBadSubseg(badseg: BadSubsegment) {
        badsubsegs.add(badseg)
    }

    fun checkSeg4Encroach(testsubseg: OSub): Int {
        val neighbortri = OTri()
        val testsym = OSub()
        val encroachedseg: BadSubsegment
        var dotproduct: Double
        var encroached: Int
        var eapex: Vertex
        encroached = 0
        var sides = 0
        val eorg = testsubseg.org()!!
        val edest = testsubseg.dest()!!
        testsubseg.pivot(neighbortri)
        if (neighbortri.triangle!!.id != Mesh.DUMMY) {
            sides++
            eapex = neighbortri.apex()!!
            dotproduct = (eorg.x - eapex.x) * (edest.x - eapex.x) + (eorg.y - eapex.y) * (edest.y - eapex.y)
            if (dotproduct < 0.0) {
                if (behavior.makeConformingDelaunay || dotproduct * dotproduct >= (2.0 * behavior.goodAngle - 1.0) * (2.0 * behavior.goodAngle - 1.0) * ((eorg.x - eapex.x) * (eorg.x - eapex.x) + (eorg.y - eapex.y) * (eorg.y - eapex.y)) * ((edest.x - eapex.x) * (edest.x - eapex.x) + (edest.y - eapex.y) * (edest.y - eapex.y))) {
                    encroached = 1
                }
            }
        }
        testsubseg.sym(testsym)
        testsym.pivot(neighbortri)
        if (neighbortri.triangle!!.id != Mesh.DUMMY) {
            sides++
            eapex = neighbortri.apex()!!
            dotproduct = (eorg.x - eapex.x) * (edest.x - eapex.x) + (eorg.y - eapex.y) * (edest.y - eapex.y)
            if (dotproduct < 0.0) {
                if (behavior.makeConformingDelaunay || dotproduct * dotproduct >= (2.0 * behavior.goodAngle - 1.0) * (2.0 * behavior.goodAngle - 1.0) * ((eorg.x - eapex.x) * (eorg.x - eapex.x) + (eorg.y - eapex.y) * (eorg.y - eapex.y)) * ((edest.x - eapex.x) * (edest.x - eapex.x) + (edest.y - eapex.y) * (edest.y - eapex.y))) {
                    encroached += 2
                }
            }
        }
        if (encroached > 0 && (behavior.boundarySplitMode == BoundarySplitMode.SPLIT || behavior.boundarySplitMode == BoundarySplitMode.SPLIT_INTERNAL_ONLY && sides == 2)) {
            encroachedseg = BadSubsegment()
            if (encroached == 1) {
                testsubseg.copy(encroachedseg.subsegment)
                encroachedseg.org = eorg
                encroachedseg.dest = edest
            } else {
                testsym.copy(encroachedseg.subsegment)
                encroachedseg.org = edest
                encroachedseg.dest = eorg
            }
            badsubsegs.add(encroachedseg)
        }
        return encroached
    }

    fun testTriangle(testtri: OTri) {
        val tri1 = OTri()
        val tri2 = OTri()
        val testsub = OSub()
        val base1: Vertex
        val base2: Vertex
        val org1: Vertex
        val dest1: Vertex
        val org2: Vertex
        val dest2: Vertex
        var joinvertex: Vertex?
        val dxod: Double
        val dyod: Double
        val dxda: Double
        val dyda: Double
        val dxao: Double
        val dyao: Double
        val dxod2: Double
        val dyod2: Double
        val dxda2: Double
        val dyda2: Double
        val dxao2: Double
        val dyao2: Double
        val apexlen: Double
        val orglen: Double
        val destlen: Double
        val minedge: Double
        var angle: Double
        val area: Double
        val dist1: Double
        val dist2: Double
        val maxangle: Double
        val torg = testtri.org()!!
        val tdest = testtri.dest()!!
        val tapex = testtri.apex()!!
        dxod = torg.x - tdest.x
        dyod = torg.y - tdest.y
        dxda = tdest.x - tapex.x
        dyda = tdest.y - tapex.y
        dxao = tapex.x - torg.x
        dyao = tapex.y - torg.y
        dxod2 = dxod * dxod
        dyod2 = dyod * dyod
        dxda2 = dxda * dxda
        dyda2 = dyda * dyda
        dxao2 = dxao * dxao
        dyao2 = dyao * dyao
        apexlen = dxod2 + dyod2
        orglen = dxda2 + dyda2
        destlen = dxao2 + dyao2
        if (apexlen < orglen && apexlen < destlen) {
            minedge = apexlen
            angle = dxda * dxao + dyda * dyao
            angle = angle * angle / (orglen * destlen)
            base1 = torg
            base2 = tdest
            testtri.copy(tri1)
        } else if (orglen < destlen) {
            minedge = orglen
            angle = dxod * dxao + dyod * dyao
            angle = angle * angle / (apexlen * destlen)
            base1 = tdest
            base2 = tapex
            testtri.lnext(tri1)
        } else {
            minedge = destlen
            angle = dxod * dxda + dyod * dyda
            angle = angle * angle / (apexlen * orglen)
            base1 = tapex
            base2 = torg
            testtri.lprev(tri1)
        }
        if (behavior.applyMaxTriangleAreaConstraints || behavior.fixedArea || behavior.userDefinedTriangleConstraint != null) {
            area = 0.5 * (dxod * dyda - dyod * dxda)
            if (behavior.fixedArea && area > behavior.maxAreaConstraint) {
                queue.enqueue(testtri, minedge, tapex, torg, tdest)
                return
            }
            if (behavior.applyMaxTriangleAreaConstraints && area > testtri.triangle!!.area && testtri.triangle!!.area > 0.0) {
                queue.enqueue(testtri, minedge, tapex, torg, tdest)
                return
            }
            if (behavior.userDefinedTriangleConstraint != null && behavior.userDefinedTriangleConstraint!!.invoke(testtri.triangle!!, area)) {
                queue.enqueue(testtri, minedge, tapex, torg, tdest)
                return
            }
        }
        if (apexlen > orglen && apexlen > destlen) {
            maxangle = (orglen + destlen - apexlen) / (2 * Math.sqrt(orglen * destlen))
        } else if (orglen > destlen) {
            maxangle = (apexlen + destlen - orglen) / (2 * Math.sqrt(apexlen * destlen))
        } else {
            maxangle = (apexlen + orglen - destlen) / (2 * Math.sqrt(apexlen * orglen))
        }
        if (angle > behavior.goodAngle || maxangle < behavior.maxGoodAngle && behavior.maxAngleConstraint != 0.0) {
            if (base1.type == VertexType.SEGMENT_VERTEX && base2.type == VertexType.SEGMENT_VERTEX) {
                tri1.pivot(testsub)
                if (testsub.segment!!.hash == Mesh.DUMMY) {
                    tri1.copy(tri2)
                    do {
                        tri1.oprev()
                        tri1.pivot(testsub)
                    } while (testsub.segment!!.hash == Mesh.DUMMY)
                    org1 = testsub.segOrg()!!
                    dest1 = testsub.segDest()!!
                    do {
                        tri2.dnext()
                        tri2.pivot(testsub)
                    } while (testsub.segment!!.hash == Mesh.DUMMY)
                    org2 = testsub.segOrg()!!
                    dest2 = testsub.segDest()!!
                    joinvertex = null
                    if (dest1.x == org2.x && dest1.y == org2.y) {
                        joinvertex = dest1
                    } else if (org1.x == dest2.x && org1.y == dest2.y) {
                        joinvertex = org1
                    }
                    if (joinvertex != null) {
                        dist1 = (base1.x - joinvertex.x) * (base1.x - joinvertex.x) + (base1.y - joinvertex.y) * (base1.y - joinvertex.y)
                        dist2 = (base2.x - joinvertex.x) * (base2.x - joinvertex.x) + (base2.y - joinvertex.y) * (base2.y - joinvertex.y)
                        if (dist1 < 1.001 * dist2 && dist1 > 0.999 * dist2) {
                            return
                        }
                    }
                }
            }
            queue.enqueue(testtri, minedge, tapex, torg, tdest)
        }
    }

    private fun tallyEncs() {
        val subsegloop = OSub()
        subsegloop.orient = 0
        for (seg in mesh._subsegs.values) {
            subsegloop.segment = seg
            checkSeg4Encroach(subsegloop)
        }
    }

    private fun splitEncSegs(triflaws: Boolean, noExact: Boolean) {
        val enctri = OTri()
        val testtri = OTri()
        val testsh = OSub()
        val currentenc = OSub()
        var seg: BadSubsegment
        var eorg: Vertex
        var edest: Vertex
        var eapex: Vertex
        var newvertex: Vertex
        var success: InsertVertexResult
        var segmentlength: Double
        var nearestpoweroftwo: Double
        var split: Double
        var multiplier: Double
        var divisor: Double
        var acuteorg: Boolean
        var acuteorg2: Boolean
        var acutedest: Boolean
        var acutedest2: Boolean
        while (badsubsegs.size > 0) {
            if (mesh.steinerleft == 0) {
                break
            }
            seg = badsubsegs.remove()
            seg.subsegment.copy(currentenc)
            eorg = currentenc.org()!!
            edest = currentenc.dest()!!
            if (!OSub.isDead(currentenc.segment!!) && eorg == seg.org && edest == seg.dest) {
                currentenc.pivot(enctri)
                enctri.lnext(testtri)
                testtri.pivot(testsh)
                acuteorg = testsh.segment!!.hash != Mesh.DUMMY
                testtri.lnext()
                testtri.pivot(testsh)
                acutedest = testsh.segment!!.hash != Mesh.DUMMY
                if (!behavior.makeConformingDelaunay && !acuteorg && !acutedest) {
                    eapex = enctri.apex()!!
                    while (eapex.type == VertexType.FREE_VERTEX && (eorg.x - eapex.x) * (edest.x - eapex.x) + (eorg.y - eapex.y) * (edest.y - eapex.y) < 0.0) {
                        mesh.deleteVertex(testtri, noExact)
                        currentenc.pivot(enctri)
                        eapex = enctri.apex()!!
                        enctri.lprev(testtri)
                    }
                }
                enctri.sym(testtri)
                if (testtri.triangle!!.id != Mesh.DUMMY) {
                    testtri.lnext()
                    testtri.pivot(testsh)
                    acutedest2 = testsh.segment!!.hash != Mesh.DUMMY
                    acutedest = acutedest || acutedest2
                    testtri.lnext()
                    testtri.pivot(testsh)
                    acuteorg2 = testsh.segment!!.hash != Mesh.DUMMY
                    acuteorg = acuteorg || acuteorg2
                    if (!behavior.makeConformingDelaunay && !acuteorg2 && !acutedest2) {
                        eapex = testtri.org()!!
                        while (eapex.type == VertexType.FREE_VERTEX && (eorg.x - eapex.x) * (edest.x - eapex.x) + (eorg.y - eapex.y) * (edest.y - eapex.y) < 0.0) {
                            mesh.deleteVertex(testtri, noExact)
                            enctri.sym(testtri)
                            eapex = testtri.apex()!!
                            testtri.lprev()
                        }
                    }
                }
                if (acuteorg || acutedest) {
                    segmentlength = Math.sqrt((edest.x - eorg.x) * (edest.x - eorg.x) + (edest.y - eorg.y) * (edest.y - eorg.y))
                    nearestpoweroftwo = 1.0
                    while (segmentlength > 3.0 * nearestpoweroftwo) {
                        nearestpoweroftwo *= 2.0
                    }
                    while (segmentlength < 1.5 * nearestpoweroftwo) {
                        nearestpoweroftwo *= 0.5
                    }
                    split = nearestpoweroftwo / segmentlength
                    if (acutedest) {
                        split = 1.0 - split
                    }
                } else {
                    split = 0.5
                }
                newvertex = Vertex(eorg.x + split * (edest.x - eorg.x), eorg.y + split * (edest.y - eorg.y), currentenc.segment!!.label)
                newvertex.type = VertexType.SEGMENT_VERTEX
                newvertex.hash = mesh.hash_vtx++
                newvertex.id = newvertex.hash
                mesh._vertices.put(newvertex.hash, newvertex)
                if (!noExact) {
                    multiplier = predicates.counterClockwise(eorg, edest, newvertex, noExact)
                    divisor = (eorg.x - edest.x) * (eorg.x - edest.x) + (eorg.y - edest.y) * (eorg.y - edest.y)
                    if (multiplier != 0.0 && divisor != 0.0) {
                        multiplier /= divisor
                        if (!java.lang.Double.isNaN(multiplier)) {
                            newvertex.x += multiplier * (edest.y - eorg.y)
                            newvertex.y += multiplier * (eorg.x - edest.x)
                        }
                    }
                }
                if (newvertex.x == eorg.x && newvertex.y == eorg.y || newvertex.x == edest.x && newvertex.y == edest.y) {
                    LOG.error("Ran out of precision: I attempted to split a segment to a smaller size than can be accommodated by the finite precision of floating point arithmetic.")
                    throw Exception("Ran out of precision")
                }
                success = mesh.insertVertex(newvertex, enctri, currentenc, true, triflaws, noExact)
                if (success != InsertVertexResult.SUCCESSFUL && success != InsertVertexResult.ENCROACHING) {
                    LOG.error("Failure to split a segment.")
                    throw Exception("Failure to split a segment.")
                }
                if (mesh.steinerleft > 0) {
                    mesh.steinerleft = mesh.steinerleft - 1
                }
                checkSeg4Encroach(currentenc)
                currentenc.next()
                checkSeg4Encroach(currentenc)
            }
            seg.org = null
        }
    }

    private fun tallyFaces() {
        val triangleloop = OTri()
        triangleloop.orient = 0
        for (tri in mesh._triangles) {
            triangleloop.triangle = tri
            testTriangle(triangleloop)
        }
    }

    private fun splitTriangle(badtri: BadTriangle, noExact: Boolean) {
        val badotri = OTri()
        val borg: Vertex
        val bdest: Vertex
        val bapex: Vertex
        val newloc: Point
        val xi = Reference(0.0)
        val eta = Reference(0.0)
        val success: InsertVertexResult
        var errorflag: Boolean
        badtri.poortri.copy(badotri)
        borg = badotri.org()!!
        bdest = badotri.dest()!!
        bapex = badotri.apex()!!
        if (!OTri.isDead(badotri.triangle!!) && borg == badtri.org && bdest == badtri.dest && bapex == badtri.apex) {
            errorflag = false
            if (behavior.fixedArea || behavior.applyMaxTriangleAreaConstraints) {
                newloc = predicates.findCircumcenter(borg, bdest, bapex, xi, eta, behavior.offConstant, noExact)
            } else {
                newloc = newLocation.findLocation(borg, bdest, bapex, xi, eta, badotri, noExact)
            }
            if (newloc.x == borg.x && newloc.y == borg.y || newloc.x == bdest.x && newloc.y == bdest.y || newloc.x == bapex.x && newloc.y == bapex.y) {
                LOG.debug {
                    LOG.debug("New vertex falls on existing vertex.")
                    errorflag = true
                    null
                }
            } else {
                val newvertex = Vertex(newloc.x, newloc.y, 0)
                newvertex.type = VertexType.FREE_VERTEX
                if (eta.value < xi.value) {
                    badotri.lprev()
                }
                newvertex.tri.triangle = newvertex_tri
                val tmp = OSub()
                success = mesh.insertVertex(newvertex, badotri, tmp, true, true, noExact)
                if (success == InsertVertexResult.SUCCESSFUL) {
                    newvertex.hash = mesh.hash_vtx++
                    newvertex.id = newvertex.hash
                    mesh._vertices.put(newvertex.hash, newvertex)
                    if (mesh.steinerleft > 0) {
                        mesh.steinerleft = mesh.steinerleft - 1
                    }
                } else if (success == InsertVertexResult.ENCROACHING) {
                    mesh.undoVertex()
                } else if (success == InsertVertexResult.VIOLATING) {
                } else {
                    LOG.debug {
                        LOG.warn("New vertex falls on existing vertex.")
                        errorflag = true
                        null
                    }
                }
            }
            if (errorflag) {
                LOG.error("The new vertex is at the circumcenter of com.grimfox.triangle: This probably means that I am trying to refine triangles to a smaller size than can be accommodated by the finite precision of floating point arithmetic.")
                throw Exception("The new vertex is at the circumcenter of com.grimfox.triangle.")
            }
        }
    }

    private fun enforceQuality(noExact: Boolean) {
        var badtri: BadTriangle
        tallyEncs()
        splitEncSegs(false, noExact)
        if (behavior.minAngleConstraint > 0.0 || behavior.applyMaxTriangleAreaConstraints || behavior.fixedArea || behavior.userDefinedTriangleConstraint != null) {
            tallyFaces()
            mesh.checkquality = true
            while (queue.size > 0 && mesh.steinerleft != 0) {
                badtri = queue.dequeue()!!
                splitTriangle(badtri, noExact)
                if (badsubsegs.size > 0) {
                    queue.enqueue(badtri)
                    splitEncSegs(true, noExact)
                }
            }
        }
        if (behavior.makeConformingDelaunay && badsubsegs.size > 0 && mesh.steinerleft == 0) {
            LOG.debug("I ran out of Steiner points, but the meshing has encroached subsegments, and therefore might not be truly Delaunay. If the Delaunay property is important to you, try increasing the number of Steiner points.")
        }
    }
}
