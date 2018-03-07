package com.grimfox.triangle

import com.grimfox.triangle.geometry.OSub
import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.geometry.Vertex

class TriangleLocator(
        internal var mesh: Mesh,
        internal var predicates: Predicates = Predicates.default) {

    enum class LocateResult {
        IN_TRIANGLE,
        ON_EDGE,
        ON_VERTEX,
        OUTSIDE
    }

    private var sampler: TriangleSampler = TriangleSampler(mesh)

    val recentTri = OTri()

    fun update(otri: OTri) {
        otri.copy(recentTri)
    }

    fun reset() {
        sampler.reset()
        recentTri.triangle = null
    }

    fun preciseLocate(searchPoint: Point, searchTri: OTri, stopAtSubsegment: Boolean, noExact: Boolean): LocateResult {
        val backTrackTri = OTri()
        val checkEdge = OSub()
        var fOrg: Vertex
        var fDest: Vertex
        var fApex: Vertex
        var orgOrient: Double
        var destOrient: Double
        var moveLeft: Boolean
        fOrg = searchTri.org()!!
        fDest = searchTri.dest()!!
        fApex = searchTri.apex()!!
        while (true) {
            if (fApex.x == searchPoint.x && fApex.y == searchPoint.y) {
                searchTri.lprev()
                return LocateResult.ON_VERTEX
            }
            destOrient = predicates.counterClockwise(fOrg, fApex, searchPoint, noExact)
            orgOrient = predicates.counterClockwise(fApex, fDest, searchPoint, noExact)
            if (destOrient > 0.0) {
                if (orgOrient > 0.0) {
                    moveLeft = (fApex.x - searchPoint.x) * (fDest.x - fOrg.x) + (fApex.y - searchPoint.y) * (fDest.y - fOrg.y) > 0.0
                } else {
                    moveLeft = true
                }
            } else {
                if (orgOrient > 0.0) {
                    moveLeft = false
                } else {
                    if (destOrient == 0.0) {
                        searchTri.lprev()
                        return LocateResult.ON_EDGE
                    }
                    if (orgOrient == 0.0) {
                        searchTri.lnext()
                        return LocateResult.ON_EDGE
                    }
                    return LocateResult.IN_TRIANGLE
                }
            }
            if (moveLeft) {
                searchTri.lprev(backTrackTri)
                fDest = fApex
            } else {
                searchTri.lnext(backTrackTri)
                fOrg = fApex
            }
            backTrackTri.sym(searchTri)
            if (mesh.checksegments && stopAtSubsegment) {
                backTrackTri.pivot(checkEdge)
                if (checkEdge.segment!!.hash != Mesh.DUMMY) {
                    backTrackTri.copy(searchTri)
                    return LocateResult.OUTSIDE
                }
            }
            if (searchTri.triangle!!.id == Mesh.DUMMY) {
                backTrackTri.copy(searchTri)
                return LocateResult.OUTSIDE
            }
            fApex = searchTri.apex()!!
        }
    }

    fun locate(searchPoint: Point, searchTri: OTri, noExact: Boolean): LocateResult {
        val sampleTri = OTri()
        var searchDist: Double
        var dist: Double
        val ahead: Double
        var tOrg: Vertex = searchTri.org()!!
        searchDist = (searchPoint.x - tOrg.x) * (searchPoint.x - tOrg.x) + (searchPoint.y - tOrg.y) * (searchPoint.y - tOrg.y)
        if (recentTri.triangle != null) {
            if (!OTri.isDead(recentTri.triangle!!)) {
                tOrg = recentTri.org()!!
                if (tOrg.x == searchPoint.x && tOrg.y == searchPoint.y) {
                    recentTri.copy(searchTri)
                    return LocateResult.ON_VERTEX
                }
                dist = (searchPoint.x - tOrg.x) * (searchPoint.x - tOrg.x) + (searchPoint.y - tOrg.y) * (searchPoint.y - tOrg.y)
                if (dist < searchDist) {
                    recentTri.copy(searchTri)
                    searchDist = dist
                }
            }
        }
        sampler.update()
        for (t in sampler) {
            sampleTri.triangle = t
            if (!OTri.isDead(sampleTri.triangle!!)) {
                tOrg = sampleTri.org()!!
                dist = (searchPoint.x - tOrg.x) * (searchPoint.x - tOrg.x) + (searchPoint.y - tOrg.y) * (searchPoint.y - tOrg.y)
                if (dist < searchDist) {
                    sampleTri.copy(searchTri)
                    searchDist = dist
                }
            }
        }
        tOrg = searchTri.org()!!
        val tDest = searchTri.dest()!!
        if (tOrg.x == searchPoint.x && tOrg.y == searchPoint.y) {
            return LocateResult.ON_VERTEX
        }
        if (tDest.x == searchPoint.x && tDest.y == searchPoint.y) {
            searchTri.lnext()
            return LocateResult.ON_VERTEX
        }
        ahead = predicates.counterClockwise(tOrg, tDest, searchPoint, noExact)
        if (ahead < 0.0) {
            searchTri.sym()
        } else if (ahead == 0.0) {
            if (tOrg.x < searchPoint.x == searchPoint.x < tDest.x && tOrg.y < searchPoint.y == searchPoint.y < tDest.y) {
                return LocateResult.ON_EDGE
            }
        }
        return preciseLocate(searchPoint, searchTri, false, noExact)
    }
}
