package com.grimfox.triangle.meshing.algorithm

import com.grimfox.logging.LOG
import com.grimfox.triangle.Configuration
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.Predicates
import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.Vertex
import com.grimfox.triangle.geometry.Vertex.VertexType
import com.grimfox.triangle.tools.VertexSorter

class Dwyer : TriangulationAlgorithm {

    internal var predicates: Predicates = Predicates.default

    internal var sortArray: Array<Vertex> = arrayOf()

    var useDwyer = true

    override fun triangulate(points: List<Vertex>, config: Configuration): Mesh {
        this.predicates = config.predicates.invoke()
        val mesh = Mesh(config)
        mesh.transferNodes(points)
        val hullLeft = OTri()
        val hullRight = OTri()
        val n = points.size
        this.sortArray = Array(n) { i ->
            points[i]
        }
        VertexSorter.sort(sortArray)
        var i = 0
        var j = 1
        while (j < n) {
            if (sortArray[i].x == sortArray[j].x && sortArray[i].y == sortArray[j].y) {
                LOG.debug { "A duplicate vertex appeared and was ignored (ID ${sortArray[j].id})." }
                sortArray[j].type = VertexType.UNDEAD_VERTEX
                mesh.undeads++
            } else {
                i++
                sortArray[i] = sortArray[j]
            }
            j++
        }
        i++
        if (useDwyer) {
            VertexSorter.alternate(sortArray, i)
        }
        divideAndConquerRecurse(mesh, 0, i - 1, 0, hullLeft, hullRight, mesh.behavior.disableExactMath)
        mesh.hullsize = removeGhosts(mesh, hullLeft)
        return mesh
    }

    private fun mergeHulls(mesh: Mesh, farLeft: OTri, innerLeft: OTri, innerRight: OTri, farRight: OTri, axis: Int, noExact: Boolean) {
        val leftCandidate = OTri()
        val rightCandidate = OTri()
        val nextEdge = OTri()
        val sideCasing = OTri()
        val topCasing = OTri()
        val outerCasing = OTri()
        val checkEdge = OTri()
        val baseEdge = OTri()
        var innerLeftDest: Vertex
        var innerRightOrg: Vertex
        var innerLeftApex: Vertex
        var innerRightApex: Vertex
        var farLeftPt: Vertex
        var farRightPt: Vertex
        var farLeftApex: Vertex
        var farRightApex: Vertex
        var lowerLeft: Vertex
        var lowerRight: Vertex
        var upperLeft: Vertex
        var upperRight: Vertex
        var nextApex: Vertex?
        var checkVertex: Vertex
        var changeMade: Boolean
        var badEdge: Boolean
        var leftFinished: Boolean
        var rightFinished: Boolean
        innerLeftDest = innerLeft.dest()!!
        innerLeftApex = innerLeft.apex()!!
        innerRightOrg = innerRight.org()!!
        innerRightApex = innerRight.apex()!!
        if (useDwyer && axis == 1) {
            farLeftPt = farLeft.org()!!
            farLeftApex = farLeft.apex()!!
            farRightPt = farRight.dest()!!
            while (farLeftApex.y < farLeftPt.y) {
                farLeft.lnext()
                farLeft.sym()
                farLeftPt = farLeftApex
                farLeftApex = farLeft.apex()!!
            }
            innerLeft.sym(checkEdge)
            checkVertex = checkEdge.apex()!!
            while (checkVertex.y > innerLeftDest.y) {
                checkEdge.lnext(innerLeft)
                innerLeftApex = innerLeftDest
                innerLeftDest = checkVertex
                innerLeft.sym(checkEdge)
                checkVertex = checkEdge.apex()!!
            }
            while (innerRightApex.y < innerRightOrg.y) {
                innerRight.lnext()
                innerRight.sym()
                innerRightOrg = innerRightApex
                innerRightApex = innerRight.apex()!!
            }
            farRight.sym(checkEdge)
            checkVertex = checkEdge.apex()!!
            while (checkVertex.y > farRightPt.y) {
                checkEdge.lnext(farRight)
                farRightPt = checkVertex
                farRight.sym(checkEdge)
                checkVertex = checkEdge.apex()!!
            }
        }
        do {
            changeMade = false
            if (predicates.counterClockwise(innerLeftDest, innerLeftApex, innerRightOrg, noExact) > 0.0) {
                innerLeft.lprev()
                innerLeft.sym()
                innerLeftDest = innerLeftApex
                innerLeftApex = innerLeft.apex()!!
                changeMade = true
            }
            if (predicates.counterClockwise(innerRightApex, innerRightOrg, innerLeftDest, noExact) > 0.0) {
                innerRight.lnext()
                innerRight.sym()
                innerRightOrg = innerRightApex
                innerRightApex = innerRight.apex()!!
                changeMade = true
            }
        } while (changeMade)
        innerLeft.sym(leftCandidate)
        innerRight.sym(rightCandidate)
        mesh.makeTriangle(baseEdge)
        baseEdge.bond(innerLeft)
        baseEdge.lnext()
        baseEdge.bond(innerRight)
        baseEdge.lnext()
        baseEdge.setOrg(innerRightOrg)
        baseEdge.setDest(innerLeftDest)
        farLeftPt = farLeft.org()!!
        if (innerLeftDest === farLeftPt) {
            baseEdge.lnext(farLeft)
        }
        farRightPt = farRight.dest()!!
        if (innerRightOrg === farRightPt) {
            baseEdge.lprev(farRight)
        }
        lowerLeft = innerLeftDest
        lowerRight = innerRightOrg
        upperLeft = leftCandidate.apex()!!
        upperRight = rightCandidate.apex()!!
        while (true) {
            leftFinished = predicates.counterClockwise(upperLeft, lowerLeft, lowerRight, noExact) <= 0.0
            rightFinished = predicates.counterClockwise(upperRight, lowerLeft, lowerRight, noExact) <= 0.0
            if (leftFinished && rightFinished) {
                mesh.makeTriangle(nextEdge)
                nextEdge.setOrg(lowerLeft)
                nextEdge.setDest(lowerRight)
                nextEdge.bond(baseEdge)
                nextEdge.lnext()
                nextEdge.bond(rightCandidate)
                nextEdge.lnext()
                nextEdge.bond(leftCandidate)
                if (useDwyer && axis == 1) {
                    farLeftPt = farLeft.org()!!
                    farRightPt = farRight.dest()!!
                    farRightApex = farRight.apex()!!
                    farLeft.sym(checkEdge)
                    checkVertex = checkEdge.apex()!!
                    while (checkVertex.x < farLeftPt.x) {
                        checkEdge.lprev(farLeft)
                        farLeftPt = checkVertex
                        farLeft.sym(checkEdge)
                        checkVertex = checkEdge.apex()!!
                    }
                    while (farRightApex.x > farRightPt.x) {
                        farRight.lprev()
                        farRight.sym()
                        farRightPt = farRightApex
                        farRightApex = farRight.apex()!!
                    }
                }
                return
            }
            if (!leftFinished) {
                leftCandidate.lprev(nextEdge)
                nextEdge.sym()
                nextApex = nextEdge.apex()
                if (nextApex != null) {
                    badEdge = predicates.inCircle(lowerLeft, lowerRight, upperLeft, nextApex, noExact) > 0.0
                    while (badEdge) {
                        nextEdge.lnext()
                        nextEdge.sym(topCasing)
                        nextEdge.lnext()
                        nextEdge.sym(sideCasing)
                        nextEdge.bond(topCasing)
                        leftCandidate.bond(sideCasing)
                        leftCandidate.lnext()
                        leftCandidate.sym(outerCasing)
                        nextEdge.lprev()
                        nextEdge.bond(outerCasing)
                        leftCandidate.setOrg(lowerLeft)
                        leftCandidate.setDest(null)
                        leftCandidate.setApex(nextApex)
                        nextEdge.setOrg(null)
                        nextEdge.setDest(upperLeft)
                        nextEdge.setApex(nextApex)
                        upperLeft = nextApex!!
                        sideCasing.copy(nextEdge)
                        nextApex = nextEdge.apex()
                        if (nextApex != null) {
                            badEdge = predicates.inCircle(lowerLeft, lowerRight, upperLeft, nextApex, noExact) > 0.0
                        } else {
                            badEdge = false
                        }
                    }
                }
            }
            if (!rightFinished) {
                rightCandidate.lnext(nextEdge)
                nextEdge.sym()
                nextApex = nextEdge.apex()
                if (nextApex != null) {
                    badEdge = predicates.inCircle(lowerLeft, lowerRight, upperRight, nextApex, noExact) > 0.0
                    while (badEdge) {
                        nextEdge.lprev()
                        nextEdge.sym(topCasing)
                        nextEdge.lprev()
                        nextEdge.sym(sideCasing)
                        nextEdge.bond(topCasing)
                        rightCandidate.bond(sideCasing)
                        rightCandidate.lprev()
                        rightCandidate.sym(outerCasing)
                        nextEdge.lnext()
                        nextEdge.bond(outerCasing)
                        rightCandidate.setOrg(null)
                        rightCandidate.setDest(lowerRight)
                        rightCandidate.setApex(nextApex)
                        nextEdge.setOrg(upperRight)
                        nextEdge.setDest(null)
                        nextEdge.setApex(nextApex)
                        upperRight = nextApex!!
                        sideCasing.copy(nextEdge)
                        nextApex = nextEdge.apex()
                        if (nextApex != null) {
                            badEdge = predicates.inCircle(lowerLeft, lowerRight, upperRight, nextApex, noExact) > 0.0
                        } else {
                            badEdge = false
                        }
                    }
                }
            }
            if (leftFinished || !rightFinished && predicates.inCircle(upperLeft, lowerLeft, lowerRight, upperRight, noExact) > 0.0) {
                baseEdge.bond(rightCandidate)
                rightCandidate.lprev(baseEdge)
                baseEdge.setDest(lowerLeft)
                lowerRight = upperRight
                baseEdge.sym(rightCandidate)
                upperRight = rightCandidate.apex()!!
            } else {
                baseEdge.bond(leftCandidate)
                leftCandidate.lnext(baseEdge)
                baseEdge.setOrg(lowerRight)
                lowerLeft = upperLeft
                baseEdge.sym(leftCandidate)
                upperLeft = leftCandidate.apex()!!
            }
        }
    }

    private fun divideAndConquerRecurse(mesh: Mesh, left: Int, right: Int, axis: Int, farLeft: OTri, farRight: OTri, noExact: Boolean) {
        val midTri = OTri()
        val tri1 = OTri()
        val tri2 = OTri()
        val tri3 = OTri()
        val innerLeft = OTri()
        val innerRight = OTri()
        val area: Double
        val vertices = right - left + 1
        val divider: Int
        if (vertices == 2) {
            mesh.makeTriangle(farLeft)
            farLeft.setOrg(sortArray[left])
            farLeft.setDest(sortArray[left + 1])
            mesh.makeTriangle(farRight)
            farRight.setOrg(sortArray[left + 1])
            farRight.setDest(sortArray[left])
            farLeft.bond(farRight)
            farLeft.lprev()
            farRight.lnext()
            farLeft.bond(farRight)
            farLeft.lprev()
            farRight.lnext()
            farLeft.bond(farRight)
            farRight.lprev(farLeft)
            return
        } else if (vertices == 3) {
            mesh.makeTriangle(midTri)
            mesh.makeTriangle(tri1)
            mesh.makeTriangle(tri2)
            mesh.makeTriangle(tri3)
            area = predicates.counterClockwise(sortArray[left], sortArray[left + 1], sortArray[left + 2], noExact)
            if (area == 0.0) {
                midTri.setOrg(sortArray[left])
                midTri.setDest(sortArray[left + 1])
                tri1.setOrg(sortArray[left + 1])
                tri1.setDest(sortArray[left])
                tri2.setOrg(sortArray[left + 2])
                tri2.setDest(sortArray[left + 1])
                tri3.setOrg(sortArray[left + 1])
                tri3.setDest(sortArray[left + 2])
                midTri.bond(tri1)
                tri2.bond(tri3)
                midTri.lnext()
                tri1.lprev()
                tri2.lnext()
                tri3.lprev()
                midTri.bond(tri3)
                tri1.bond(tri2)
                midTri.lnext()
                tri1.lprev()
                tri2.lnext()
                tri3.lprev()
                midTri.bond(tri1)
                tri2.bond(tri3)
                tri1.copy(farLeft)
                tri2.copy(farRight)
            } else {
                midTri.setOrg(sortArray[left])
                tri1.setDest(sortArray[left])
                tri3.setOrg(sortArray[left])
                if (area > 0.0) {
                    midTri.setDest(sortArray[left + 1])
                    tri1.setOrg(sortArray[left + 1])
                    tri2.setDest(sortArray[left + 1])
                    midTri.setApex(sortArray[left + 2])
                    tri2.setOrg(sortArray[left + 2])
                    tri3.setDest(sortArray[left + 2])
                } else {
                    midTri.setDest(sortArray[left + 2])
                    tri1.setOrg(sortArray[left + 2])
                    tri2.setDest(sortArray[left + 2])
                    midTri.setApex(sortArray[left + 1])
                    tri2.setOrg(sortArray[left + 1])
                    tri3.setDest(sortArray[left + 1])
                }
                midTri.bond(tri1)
                midTri.lnext()
                midTri.bond(tri2)
                midTri.lnext()
                midTri.bond(tri3)
                tri1.lprev()
                tri2.lnext()
                tri1.bond(tri2)
                tri1.lprev()
                tri3.lprev()
                tri1.bond(tri3)
                tri2.lnext()
                tri3.lprev()
                tri2.bond(tri3)
                tri1.copy(farLeft)
                if (area > 0.0) {
                    tri2.copy(farRight)
                } else {
                    farLeft.lnext(farRight)
                }
            }
            return
        } else {
            divider = vertices shr 1
            divideAndConquerRecurse(mesh, left, left + divider - 1, 1 - axis, farLeft, innerLeft, noExact)
            divideAndConquerRecurse(mesh, left + divider, right, 1 - axis, innerRight, farRight, noExact)
            mergeHulls(mesh, farLeft, innerLeft, innerRight, farRight, axis, noExact)
        }
    }

    private fun removeGhosts(mesh: Mesh, startGhost: OTri): Int {
        val searchEdge = OTri()
        val dissolveEdge = OTri()
        val deadTriangle = OTri()
        var markOrg: Vertex
        val noPoly = !mesh.behavior.isPlanarStraightLineGraph
        startGhost.lprev(searchEdge)
        searchEdge.sym()
        mesh.dummytri.neighbors[0] = searchEdge
        startGhost.copy(dissolveEdge)
        var hullSize = 0
        do {
            hullSize++
            dissolveEdge.lnext(deadTriangle)
            dissolveEdge.lprev()
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
            deadTriangle.sym(dissolveEdge)
            mesh.triangleDealloc(deadTriangle.triangle!!)
        } while (!dissolveEdge.equals(startGhost))
        return hullSize
    }
}
