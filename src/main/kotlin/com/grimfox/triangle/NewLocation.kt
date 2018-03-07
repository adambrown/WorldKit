package com.grimfox.triangle

import com.grimfox.triangle.TriangleLocator.LocateResult
import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.geometry.Vertex
import com.grimfox.triangle.geometry.Vertex.VertexType
import com.grimfox.triangle.tools.Statistic

class NewLocation(
        internal var mesh: Mesh,
        internal var predicates: Predicates) {

    internal var behavior: Behavior = mesh.behavior

    private var petalx = DoubleArray(20)

    private var petaly = DoubleArray(20)

    private var petalr = DoubleArray(20)

    private var wedges = DoubleArray(500)

    private val initialConvexPoly = DoubleArray(500)

    private val points_p = DoubleArray(500)

    private val points_q = DoubleArray(500)

    private val points_r = DoubleArray(500)

    private val poly1 = DoubleArray(100)

    private val poly2 = DoubleArray(100)

    private val polys = Array(3) { DoubleArray(0) }

    fun findLocation(org: Vertex, dest: Vertex, apex: Vertex, xi: Reference<Double>, eta: Reference<Double>, constBadotri: OTri, noExact: Boolean): Point {
        val badotri = constBadotri.copy()
        if (behavior.maxAngleConstraint == 0.0) {
            return findNewLocationWithoutMaxAngle(org, dest, apex, xi, eta, true, badotri, noExact)
        }
        return findNewLocation(org, dest, apex, xi, eta, true, badotri, noExact)
    }

    private fun findNewLocationWithoutMaxAngle(torg: Vertex, tdest: Vertex, tapex: Vertex, xi: Reference<Double>, eta: Reference<Double>, offcenter: Boolean, constBadotri: OTri, noExact: Boolean): Point {
        val badotri = constBadotri.copy()
        val offconstant = behavior.offConstant
        val denominator: Double
        var dx: Double
        var dy: Double
        val dxoff: Double
        val dyoff: Double
        val xShortestEdge: Double
        val yShortestEdge: Double
        val shortestEdgeDist: Double
        val middleEdgeDist: Double
        val longestEdgeDist: Double
        val smallestAngleCorner: Point
        val middleAngleCorner: Point
        val largestAngleCorner: Point
        val orientation: Int
        val myCircumcenter: Point
        var neighborCircumcenter: Point
        var almostGood = 0
        val cosMaxAngle: Double
        val isObtuse: Boolean
        val petalRadius: Double
        val xPetalCtr_1: Double
        val yPetalCtr_1: Double
        val xPetalCtr_2: Double
        val yPetalCtr_2: Double
        val xPetalCtr: Double
        val yPetalCtr: Double
        val xMidOfShortestEdge: Double
        val yMidOfShortestEdge: Double
        val dxcenter1: Double
        val dycenter1: Double
        val dxcenter2: Double
        val dycenter2: Double
        val neighborotri = OTri()
        val thirdPoint = DoubleArray(2)
        var neighborNotFound: Boolean
        var neighborvertex_1: Vertex
        var neighborvertex_2: Vertex
        var neighborvertex_3: Vertex
        val xi_tmp = Reference(0.0)
        val eta_tmp = Reference(0.0)
        var vector_x: Double
        var vector_y: Double
        val xMidOfLongestEdge: Double
        val yMidOfLongestEdge: Double
        var inter_x: Double
        var inter_y: Double
        val p = DoubleArray(5)
        val voronoiOrInter = DoubleArray(4)
        var isCorrect: Boolean
        var ax: Double
        var ay: Double
        var d: Double
        val pertConst = 0.06
        val lengthConst = 1.0
        val justAcute = 1.0
        var relocated = 0
        val newloc = DoubleArray(2)
        var origin_x = 0.0
        var origin_y = 0.0
        val delotri = OTri()
        var dxFirstSuggestion: Double
        var dyFirstSuggestion: Double
        var dxSecondSuggestion: Double
        var dySecondSuggestion: Double
        val xMidOfMiddleEdge: Double
        val yMidOfMiddleEdge: Double
        Statistic.circumcenterCount.andIncrement
        val xdo = tdest.x - torg.x
        val ydo = tdest.y - torg.y
        val xao = tapex.x - torg.x
        val yao = tapex.y - torg.y
        val xda = tapex.x - tdest.x
        val yda = tapex.y - tdest.y
        val dodist = xdo * xdo + ydo * ydo
        val aodist = xao * xao + yao * yao
        val dadist = (tdest.x - tapex.x) * (tdest.x - tapex.x) + (tdest.y - tapex.y) * (tdest.y - tapex.y)
        if (noExact) {
            denominator = 0.5 / (xdo * yao - xao * ydo)
        } else {
            denominator = 0.5 / predicates.counterClockwise(tdest, tapex, torg, noExact)
            Statistic.counterClockwiseCount.andDecrement
        }
        dx = (yao * dodist - ydo * aodist) * denominator
        dy = (xdo * aodist - xao * dodist) * denominator
        myCircumcenter = Point(torg.x + dx, torg.y + dy)
        badotri.copy(delotri)
        orientation = longestShortestEdge(aodist, dadist, dodist)
        when (orientation) {
            123 -> {
                xShortestEdge = xao
                yShortestEdge = yao
                shortestEdgeDist = aodist
                middleEdgeDist = dadist
                longestEdgeDist = dodist
                smallestAngleCorner = tdest
                middleAngleCorner = torg
                largestAngleCorner = tapex
            }
            132 -> {
                xShortestEdge = xao
                yShortestEdge = yao
                shortestEdgeDist = aodist
                middleEdgeDist = dodist
                longestEdgeDist = dadist
                smallestAngleCorner = tdest
                middleAngleCorner = tapex
                largestAngleCorner = torg
            }
            213 -> {
                xShortestEdge = xda
                yShortestEdge = yda
                shortestEdgeDist = dadist
                middleEdgeDist = aodist
                longestEdgeDist = dodist
                smallestAngleCorner = torg
                middleAngleCorner = tdest
                largestAngleCorner = tapex
            }
            231 -> {
                xShortestEdge = xda
                yShortestEdge = yda
                shortestEdgeDist = dadist
                middleEdgeDist = dodist
                longestEdgeDist = aodist
                smallestAngleCorner = torg
                middleAngleCorner = tapex
                largestAngleCorner = tdest
            }
            312 -> {
                xShortestEdge = xdo
                yShortestEdge = ydo
                shortestEdgeDist = dodist
                middleEdgeDist = aodist
                longestEdgeDist = dadist
                smallestAngleCorner = tapex
                middleAngleCorner = tdest
                largestAngleCorner = torg
            }
            else -> {
                xShortestEdge = xdo
                yShortestEdge = ydo
                shortestEdgeDist = dodist
                middleEdgeDist = dadist
                longestEdgeDist = aodist
                smallestAngleCorner = tapex
                middleAngleCorner = torg
                largestAngleCorner = tdest
            }
        }
        if (offcenter && offconstant > 0.0) {
            if (orientation == 213 || orientation == 231) {
                dxoff = 0.5 * xShortestEdge - offconstant * yShortestEdge
                dyoff = 0.5 * yShortestEdge + offconstant * xShortestEdge
                if (dxoff * dxoff + dyoff * dyoff < (dx - xdo) * (dx - xdo) + (dy - ydo) * (dy - ydo)) {
                    dx = xdo + dxoff
                    dy = ydo + dyoff
                } else {
                    almostGood = 1
                }
            } else
                if (orientation == 123 || orientation == 132) {
                    dxoff = 0.5 * xShortestEdge + offconstant * yShortestEdge
                    dyoff = 0.5 * yShortestEdge - offconstant * xShortestEdge
                    if (dxoff * dxoff + dyoff * dyoff < dx * dx + dy * dy) {
                        dx = dxoff
                        dy = dyoff
                    } else {
                        almostGood = 1
                    }
                } else {
                    dxoff = 0.5 * xShortestEdge - offconstant * yShortestEdge
                    dyoff = 0.5 * yShortestEdge + offconstant * xShortestEdge
                    if (dxoff * dxoff + dyoff * dyoff < dx * dx + dy * dy) {
                        dx = dxoff
                        dy = dyoff
                    } else {
                        almostGood = 1
                    }
                }
        }
        if (almostGood == 1) {
            cosMaxAngle = (middleEdgeDist + shortestEdgeDist - longestEdgeDist) / (2.0 * Math.sqrt(middleEdgeDist) * Math.sqrt(shortestEdgeDist))
            if (cosMaxAngle < 0.0) {
                isObtuse = true
            } else {
                isObtuse = Math.abs(cosMaxAngle - 0.0) <= EPS
            }
            relocated = doSmoothing(delotri, torg, tdest, tapex, newloc)
            if (relocated > 0) {
                Statistic.relocationCount.andIncrement
                dx = newloc[0] - torg.x
                dy = newloc[1] - torg.y
                origin_x = torg.x
                origin_y = torg.y
                when (relocated) {
                    1 ->
                        mesh.deleteVertex(delotri, noExact)
                    2 -> {
                        delotri.lnext()
                        mesh.deleteVertex(delotri, noExact)
                    }
                    3 -> {
                        delotri.lprev()
                        mesh.deleteVertex(delotri, noExact)
                    }
                }
            } else {
                petalRadius = Math.sqrt(shortestEdgeDist) / (2 * Math.sin(behavior.minAngleConstraint * Math.PI / 180.0))
                xMidOfShortestEdge = (middleAngleCorner.x + largestAngleCorner.x) / 2.0
                yMidOfShortestEdge = (middleAngleCorner.y + largestAngleCorner.y) / 2.0
                xPetalCtr_1 = xMidOfShortestEdge + Math.sqrt(petalRadius * petalRadius - shortestEdgeDist / 4) * (middleAngleCorner.y - largestAngleCorner.y) / Math.sqrt(shortestEdgeDist)
                yPetalCtr_1 = yMidOfShortestEdge + Math.sqrt(petalRadius * petalRadius - shortestEdgeDist / 4) * (largestAngleCorner.x - middleAngleCorner.x) / Math.sqrt(shortestEdgeDist)
                xPetalCtr_2 = xMidOfShortestEdge - Math.sqrt(petalRadius * petalRadius - shortestEdgeDist / 4) * (middleAngleCorner.y - largestAngleCorner.y) / Math.sqrt(shortestEdgeDist)
                yPetalCtr_2 = yMidOfShortestEdge - Math.sqrt(petalRadius * petalRadius - shortestEdgeDist / 4) * (largestAngleCorner.x - middleAngleCorner.x) / Math.sqrt(shortestEdgeDist)
                dxcenter1 = (xPetalCtr_1 - smallestAngleCorner.x) * (xPetalCtr_1 - smallestAngleCorner.x)
                dycenter1 = (yPetalCtr_1 - smallestAngleCorner.y) * (yPetalCtr_1 - smallestAngleCorner.y)
                dxcenter2 = (xPetalCtr_2 - smallestAngleCorner.x) * (xPetalCtr_2 - smallestAngleCorner.x)
                dycenter2 = (yPetalCtr_2 - smallestAngleCorner.y) * (yPetalCtr_2 - smallestAngleCorner.y)
                if (dxcenter1 + dycenter1 <= dxcenter2 + dycenter2) {
                    xPetalCtr = xPetalCtr_1
                    yPetalCtr = yPetalCtr_1
                } else {
                    xPetalCtr = xPetalCtr_2
                    yPetalCtr = yPetalCtr_2
                }
                neighborNotFound = getNeighborsVertex(badotri, middleAngleCorner.x, middleAngleCorner.y, smallestAngleCorner.x, smallestAngleCorner.y, thirdPoint, neighborotri)
                dxFirstSuggestion = dx
                dyFirstSuggestion = dy
                if (!neighborNotFound) {
                    neighborvertex_1 = neighborotri.org()!!
                    neighborvertex_2 = neighborotri.dest()!!
                    neighborvertex_3 = neighborotri.apex()!!
                    neighborCircumcenter = predicates.findCircumcenter(neighborvertex_1, neighborvertex_2, neighborvertex_3, xi, eta, noExact)
                    vector_x = middleAngleCorner.y - smallestAngleCorner.y
                    vector_y = smallestAngleCorner.x - middleAngleCorner.x
                    vector_x += myCircumcenter.x
                    vector_y += myCircumcenter.y
                    circleLineIntersection(myCircumcenter.x, myCircumcenter.y, vector_x, vector_y, xPetalCtr, yPetalCtr, petalRadius, p)
                    xMidOfLongestEdge = (middleAngleCorner.x + smallestAngleCorner.x) / 2.0
                    yMidOfLongestEdge = (middleAngleCorner.y + smallestAngleCorner.y) / 2.0
                    isCorrect = chooseCorrectPoint(xMidOfLongestEdge, yMidOfLongestEdge, p[3], p[4], myCircumcenter.x, myCircumcenter.y, isObtuse)
                    if (isCorrect) {
                        inter_x = p[3]
                        inter_y = p[4]
                    } else {
                        inter_x = p[1]
                        inter_y = p[2]
                    }
                    pointBetweenPoints(inter_x, inter_y, myCircumcenter.x, myCircumcenter.y, neighborCircumcenter.x, neighborCircumcenter.y, voronoiOrInter)
                    if (p[0] > 0.0) {
                        if (Math.abs(voronoiOrInter[0] - 1.0) <= EPS) {
                            if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, neighborCircumcenter.x, neighborCircumcenter.y)) {
                                dxFirstSuggestion = dx
                                dyFirstSuggestion = dy
                            } else {
                                dxFirstSuggestion = voronoiOrInter[2] - torg.x
                                dyFirstSuggestion = voronoiOrInter[3] - torg.y
                            }
                        } else {
                            if (isBadTriangleAngle(largestAngleCorner.x, largestAngleCorner.y, middleAngleCorner.x, middleAngleCorner.y, inter_x, inter_y)) {
                                d = Math.sqrt((inter_x - myCircumcenter.x) * (inter_x - myCircumcenter.x) + (inter_y - myCircumcenter.y) * (inter_y - myCircumcenter.y))
                                ax = myCircumcenter.x - inter_x
                                ay = myCircumcenter.y - inter_y
                                ax /= d
                                ay /= d
                                inter_x += ax * pertConst * Math.sqrt(shortestEdgeDist)
                                inter_y += ay * pertConst * Math.sqrt(shortestEdgeDist)
                                if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, inter_x, inter_y)) {
                                    dxFirstSuggestion = dx
                                    dyFirstSuggestion = dy
                                } else {
                                    dxFirstSuggestion = inter_x - torg.x
                                    dyFirstSuggestion = inter_y - torg.y
                                }
                            } else {
                                dxFirstSuggestion = inter_x - torg.x
                                dyFirstSuggestion = inter_y - torg.y
                            }
                        }
                        if ((smallestAngleCorner.x - myCircumcenter.x) * (smallestAngleCorner.x - myCircumcenter.x) + (smallestAngleCorner.y - myCircumcenter.y) * (smallestAngleCorner.y - myCircumcenter.y) > lengthConst * ((smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) * (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) + (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)) * (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)))) {
                            dxFirstSuggestion = dx
                            dyFirstSuggestion = dy
                        }
                    }
                }
                neighborNotFound = getNeighborsVertex(badotri, largestAngleCorner.x, largestAngleCorner.y, smallestAngleCorner.x, smallestAngleCorner.y, thirdPoint, neighborotri)
                dxSecondSuggestion = dx
                dySecondSuggestion = dy
                if (!neighborNotFound) {
                    neighborvertex_1 = neighborotri.org()!!
                    neighborvertex_2 = neighborotri.dest()!!
                    neighborvertex_3 = neighborotri.apex()!!
                    neighborCircumcenter = predicates.findCircumcenter(neighborvertex_1, neighborvertex_2, neighborvertex_3, xi_tmp, eta_tmp, noExact)
                    vector_x = largestAngleCorner.y - smallestAngleCorner.y
                    vector_y = smallestAngleCorner.x - largestAngleCorner.x
                    vector_x += myCircumcenter.x
                    vector_y += myCircumcenter.y
                    circleLineIntersection(myCircumcenter.x, myCircumcenter.y, vector_x, vector_y, xPetalCtr, yPetalCtr, petalRadius, p)
                    xMidOfMiddleEdge = (largestAngleCorner.x + smallestAngleCorner.x) / 2.0
                    yMidOfMiddleEdge = (largestAngleCorner.y + smallestAngleCorner.y) / 2.0
                    isCorrect = chooseCorrectPoint(xMidOfMiddleEdge, yMidOfMiddleEdge, p[3], p[4], myCircumcenter.x, myCircumcenter.y, false)
                    if (isCorrect) {
                        inter_x = p[3]
                        inter_y = p[4]
                    } else {
                        inter_x = p[1]
                        inter_y = p[2]
                    }
                    pointBetweenPoints(inter_x, inter_y, myCircumcenter.x, myCircumcenter.y, neighborCircumcenter.x, neighborCircumcenter.y, voronoiOrInter)
                    if (p[0] > 0.0) {
                        if (Math.abs(voronoiOrInter[0] - 1.0) <= EPS) {
                            if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, neighborCircumcenter.x, neighborCircumcenter.y)) {
                                dxSecondSuggestion = dx
                                dySecondSuggestion = dy
                            } else {
                                dxSecondSuggestion = voronoiOrInter[2] - torg.x
                                dySecondSuggestion = voronoiOrInter[3] - torg.y
                            }
                        } else {
                            if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, inter_x, inter_y)) {
                                d = Math.sqrt((inter_x - myCircumcenter.x) * (inter_x - myCircumcenter.x) + (inter_y - myCircumcenter.y) * (inter_y - myCircumcenter.y))
                                ax = myCircumcenter.x - inter_x
                                ay = myCircumcenter.y - inter_y
                                ax /= d
                                ay /= d
                                inter_x += ax * pertConst * Math.sqrt(shortestEdgeDist)
                                inter_y += ay * pertConst * Math.sqrt(shortestEdgeDist)
                                if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, inter_x, inter_y)) {
                                    dxSecondSuggestion = dx
                                    dySecondSuggestion = dy
                                } else {
                                    dxSecondSuggestion = inter_x - torg.x
                                    dySecondSuggestion = inter_y - torg.y
                                }
                            } else {
                                dxSecondSuggestion = inter_x - torg.x
                                dySecondSuggestion = inter_y - torg.y
                            }
                        }
                        if ((smallestAngleCorner.x - myCircumcenter.x) * (smallestAngleCorner.x - myCircumcenter.x) + (smallestAngleCorner.y - myCircumcenter.y) * (smallestAngleCorner.y - myCircumcenter.y) > lengthConst * ((smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) * (smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) + (smallestAngleCorner.y - (dySecondSuggestion + torg.y)) * (smallestAngleCorner.y - (dySecondSuggestion + torg.y)))) {
                            dxSecondSuggestion = dx
                            dySecondSuggestion = dy
                        }
                    }
                }
                if (isObtuse) {
                    dx = dxFirstSuggestion
                    dy = dyFirstSuggestion
                } else {
                    if (justAcute * ((smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) * (smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) + (smallestAngleCorner.y - (dySecondSuggestion + torg.y)) * (smallestAngleCorner.y - (dySecondSuggestion + torg.y))) > (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) * (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) + (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)) * (smallestAngleCorner.y - (dyFirstSuggestion + torg.y))) {
                        dx = dxSecondSuggestion
                        dy = dySecondSuggestion
                    } else {
                        dx = dxFirstSuggestion
                        dy = dyFirstSuggestion
                    }
                }
            }
        }
        val circumcenter = Point()
        if (relocated <= 0) {
            circumcenter.x = torg.x + dx
            circumcenter.y = torg.y + dy
        } else {
            circumcenter.x = origin_x + dx
            circumcenter.y = origin_y + dy
        }
        xi.value = (yao * dx - xao * dy) * (2.0 * denominator)
        eta.value = (xdo * dy - ydo * dx) * (2.0 * denominator)
        return circumcenter
    }

    private fun findNewLocation(torg: Vertex, tdest: Vertex, tapex: Vertex, xi: Reference<Double>, eta: Reference<Double>, offcenter: Boolean, constBadotri: OTri, noExact: Boolean): Point {
        val badotri = constBadotri.copy()
        val offconstant = behavior.offConstant
        val denominator: Double
        var dx: Double
        var dy: Double
        val dxoff: Double
        val dyoff: Double
        val xShortestEdge: Double
        val yShortestEdge: Double
        val shortestEdgeDist: Double
        val middleEdgeDist: Double
        val longestEdgeDist: Double
        val smallestAngleCorner: Point
        val middleAngleCorner: Point
        val largestAngleCorner: Point
        val orientation: Int
        val myCircumcenter: Point
        var neighborCircumcenter: Point
        var almostGood = 0
        val cosMaxAngle: Double
        val isObtuse: Boolean
        val petalRadius: Double
        val xPetalCtr_1: Double
        val yPetalCtr_1: Double
        val xPetalCtr_2: Double
        val yPetalCtr_2: Double
        val xPetalCtr: Double
        val yPetalCtr: Double
        val xMidOfShortestEdge: Double
        val yMidOfShortestEdge: Double
        val dxcenter1: Double
        val dycenter1: Double
        val dxcenter2: Double
        val dycenter2: Double
        val neighborotri = OTri()
        val thirdPoint = DoubleArray(2)
        var neighborvertex_1: Vertex
        var neighborvertex_2: Vertex
        var neighborvertex_3: Vertex
        val xi_tmp = Reference(0.0)
        val eta_tmp = Reference(0.0)
        var vector_x: Double
        var vector_y: Double
        val xMidOfLongestEdge: Double
        val yMidOfLongestEdge: Double
        var inter_x: Double
        var inter_y: Double
        val p = DoubleArray(5)
        val voronoiOrInter = DoubleArray(4)
        var isCorrect: Boolean
        var ax: Double
        var ay: Double
        var d: Double
        val pertConst = 0.06
        val lengthConst = 1.0
        val justAcute = 1.0
        var relocated = 0
        val newloc = DoubleArray(2)
        var origin_x = 0.0
        var origin_y = 0.0
        val delotri = OTri()
        var dxFirstSuggestion: Double
        var dyFirstSuggestion: Double
        var dxSecondSuggestion: Double
        var dySecondSuggestion: Double
        val xMidOfMiddleEdge: Double
        val yMidOfMiddleEdge: Double
        var minangle: Double
        var linepnt1_x: Double
        var linepnt1_y: Double
        var linepnt2_x: Double
        var linepnt2_y: Double
        var line_inter_x = 0.0
        var line_inter_y = 0.0
        val line_vector_x: Double
        val line_vector_y: Double
        val line_p = DoubleArray(3)
        val line_result = DoubleArray(4)
        val petal_slab_inter_x_first: Double
        val petal_slab_inter_y_first: Double
        val petal_slab_inter_x_second: Double
        val petal_slab_inter_y_second: Double
        val x_1: Double
        val y_1: Double
        val x_2: Double
        val y_2: Double
        val petal_bisector_x: Double
        val petal_bisector_y: Double
        val dist: Double
        val alpha: Double
        val neighborNotFound_first: Boolean
        val neighborNotFound_second: Boolean
        Statistic.circumcenterCount.andIncrement
        val xdo = tdest.x - torg.x
        val ydo = tdest.y - torg.y
        val xao = tapex.x - torg.x
        val yao = tapex.y - torg.y
        val xda = tapex.x - tdest.x
        val yda = tapex.y - tdest.y
        val dodist = xdo * xdo + ydo * ydo
        val aodist = xao * xao + yao * yao
        val dadist = (tdest.x - tapex.x) * (tdest.x - tapex.x) + (tdest.y - tapex.y) * (tdest.y - tapex.y)
        if (noExact) {
            denominator = 0.5 / (xdo * yao - xao * ydo)
        } else {
            denominator = 0.5 / predicates.counterClockwise(tdest, tapex, torg, noExact)
            Statistic.counterClockwiseCount.andDecrement
        }
        dx = (yao * dodist - ydo * aodist) * denominator
        dy = (xdo * aodist - xao * dodist) * denominator
        myCircumcenter = Point(torg.x + dx, torg.y + dy)
        badotri.copy(delotri)
        orientation = longestShortestEdge(aodist, dadist, dodist)
        when (orientation) {
            123 -> {
                xShortestEdge = xao
                yShortestEdge = yao
                shortestEdgeDist = aodist
                middleEdgeDist = dadist
                longestEdgeDist = dodist
                smallestAngleCorner = tdest
                middleAngleCorner = torg
                largestAngleCorner = tapex
            }
            132 -> {
                xShortestEdge = xao
                yShortestEdge = yao
                shortestEdgeDist = aodist
                middleEdgeDist = dodist
                longestEdgeDist = dadist
                smallestAngleCorner = tdest
                middleAngleCorner = tapex
                largestAngleCorner = torg
            }
            213 -> {
                xShortestEdge = xda
                yShortestEdge = yda
                shortestEdgeDist = dadist
                middleEdgeDist = aodist
                longestEdgeDist = dodist
                smallestAngleCorner = torg
                middleAngleCorner = tdest
                largestAngleCorner = tapex
            }
            231 -> {
                xShortestEdge = xda
                yShortestEdge = yda
                shortestEdgeDist = dadist
                middleEdgeDist = dodist
                longestEdgeDist = aodist
                smallestAngleCorner = torg
                middleAngleCorner = tapex
                largestAngleCorner = tdest
            }
            312 -> {
                xShortestEdge = xdo
                yShortestEdge = ydo
                shortestEdgeDist = dodist
                middleEdgeDist = aodist
                longestEdgeDist = dadist
                smallestAngleCorner = tapex
                middleAngleCorner = tdest
                largestAngleCorner = torg
            }
            else -> {
                xShortestEdge = xdo
                yShortestEdge = ydo
                shortestEdgeDist = dodist
                middleEdgeDist = dadist
                longestEdgeDist = aodist
                smallestAngleCorner = tapex
                middleAngleCorner = torg
                largestAngleCorner = tdest
            }
        }
        if (offcenter && offconstant > 0.0) {
            if (orientation == 213 || orientation == 231) {
                dxoff = 0.5 * xShortestEdge - offconstant * yShortestEdge
                dyoff = 0.5 * yShortestEdge + offconstant * xShortestEdge
                if (dxoff * dxoff + dyoff * dyoff < (dx - xdo) * (dx - xdo) + (dy - ydo) * (dy - ydo)) {
                    dx = xdo + dxoff
                    dy = ydo + dyoff
                } else {
                    almostGood = 1
                }
            } else
                if (orientation == 123 || orientation == 132) {
                    dxoff = 0.5 * xShortestEdge + offconstant * yShortestEdge
                    dyoff = 0.5 * yShortestEdge - offconstant * xShortestEdge
                    if (dxoff * dxoff + dyoff * dyoff < dx * dx + dy * dy) {
                        dx = dxoff
                        dy = dyoff
                    } else {
                        almostGood = 1
                    }
                } else {
                    dxoff = 0.5 * xShortestEdge - offconstant * yShortestEdge
                    dyoff = 0.5 * yShortestEdge + offconstant * xShortestEdge
                    if (dxoff * dxoff + dyoff * dyoff < dx * dx + dy * dy) {
                        dx = dxoff
                        dy = dyoff
                    } else {
                        almostGood = 1
                    }
                }
        }
        if (almostGood == 1) {
            cosMaxAngle = (middleEdgeDist + shortestEdgeDist - longestEdgeDist) / (2.0 * Math.sqrt(middleEdgeDist) * Math.sqrt(shortestEdgeDist))
            if (cosMaxAngle < 0.0) {
                isObtuse = true
            } else {
                isObtuse = Math.abs(cosMaxAngle - 0.0) <= EPS
            }
            relocated = doSmoothing(delotri, torg, tdest, tapex, newloc)
            if (relocated > 0) {
                Statistic.relocationCount.andIncrement
                dx = newloc[0] - torg.x
                dy = newloc[1] - torg.y
                origin_x = torg.x
                origin_y = torg.y
                when (relocated) {
                    1 ->
                        mesh.deleteVertex(delotri, noExact)
                    2 -> {
                        delotri.lnext()
                        mesh.deleteVertex(delotri, noExact)
                    }
                    3 -> {
                        delotri.lprev()
                        mesh.deleteVertex(delotri, noExact)
                    }
                }
            } else {
                minangle = Math.acos((middleEdgeDist + longestEdgeDist - shortestEdgeDist) / (2.0 * Math.sqrt(middleEdgeDist) * Math.sqrt(longestEdgeDist))) * 180.0 / Math.PI
                if (behavior.minAngleConstraint > minangle) {
                    minangle = behavior.minAngleConstraint
                } else {
                    minangle += 0.5
                }
                petalRadius = Math.sqrt(shortestEdgeDist) / (2 * Math.sin(minangle * Math.PI / 180.0))
                xMidOfShortestEdge = (middleAngleCorner.x + largestAngleCorner.x) / 2.0
                yMidOfShortestEdge = (middleAngleCorner.y + largestAngleCorner.y) / 2.0
                xPetalCtr_1 = xMidOfShortestEdge + Math.sqrt(petalRadius * petalRadius - shortestEdgeDist / 4) * (middleAngleCorner.y - largestAngleCorner.y) / Math.sqrt(shortestEdgeDist)
                yPetalCtr_1 = yMidOfShortestEdge + Math.sqrt(petalRadius * petalRadius - shortestEdgeDist / 4) * (largestAngleCorner.x - middleAngleCorner.x) / Math.sqrt(shortestEdgeDist)
                xPetalCtr_2 = xMidOfShortestEdge - Math.sqrt(petalRadius * petalRadius - shortestEdgeDist / 4) * (middleAngleCorner.y - largestAngleCorner.y) / Math.sqrt(shortestEdgeDist)
                yPetalCtr_2 = yMidOfShortestEdge - Math.sqrt(petalRadius * petalRadius - shortestEdgeDist / 4) * (largestAngleCorner.x - middleAngleCorner.x) / Math.sqrt(shortestEdgeDist)
                dxcenter1 = (xPetalCtr_1 - smallestAngleCorner.x) * (xPetalCtr_1 - smallestAngleCorner.x)
                dycenter1 = (yPetalCtr_1 - smallestAngleCorner.y) * (yPetalCtr_1 - smallestAngleCorner.y)
                dxcenter2 = (xPetalCtr_2 - smallestAngleCorner.x) * (xPetalCtr_2 - smallestAngleCorner.x)
                dycenter2 = (yPetalCtr_2 - smallestAngleCorner.y) * (yPetalCtr_2 - smallestAngleCorner.y)
                if (dxcenter1 + dycenter1 <= dxcenter2 + dycenter2) {
                    xPetalCtr = xPetalCtr_1
                    yPetalCtr = yPetalCtr_1
                } else {
                    xPetalCtr = xPetalCtr_2
                    yPetalCtr = yPetalCtr_2
                }
                neighborNotFound_first = getNeighborsVertex(badotri, middleAngleCorner.x, middleAngleCorner.y, smallestAngleCorner.x, smallestAngleCorner.y, thirdPoint, neighborotri)
                dxFirstSuggestion = dx
                dyFirstSuggestion = dy
                dist = Math.sqrt((xPetalCtr - xMidOfShortestEdge) * (xPetalCtr - xMidOfShortestEdge) + (yPetalCtr - yMidOfShortestEdge) * (yPetalCtr - yMidOfShortestEdge))
                line_vector_x = (xPetalCtr - xMidOfShortestEdge) / dist
                line_vector_y = (yPetalCtr - yMidOfShortestEdge) / dist
                petal_bisector_x = xPetalCtr + line_vector_x * petalRadius
                petal_bisector_y = yPetalCtr + line_vector_y * petalRadius
                alpha = (2.0 * behavior.maxAngleConstraint + minangle - 180.0) * Math.PI / 180.0
                x_1 = petal_bisector_x * Math.cos(alpha) + petal_bisector_y * Math.sin(alpha) + xPetalCtr - xPetalCtr * Math.cos(alpha) - yPetalCtr * Math.sin(alpha)
                y_1 = -petal_bisector_x * Math.sin(alpha) + petal_bisector_y * Math.cos(alpha) + yPetalCtr + xPetalCtr * Math.sin(alpha) - yPetalCtr * Math.cos(alpha)
                x_2 = petal_bisector_x * Math.cos(alpha) - petal_bisector_y * Math.sin(alpha) + xPetalCtr - xPetalCtr * Math.cos(alpha) + yPetalCtr * Math.sin(alpha)
                y_2 = petal_bisector_x * Math.sin(alpha) + petal_bisector_y * Math.cos(alpha) + yPetalCtr - xPetalCtr * Math.sin(alpha) - yPetalCtr * Math.cos(alpha)
                isCorrect = chooseCorrectPoint(x_2, y_2, middleAngleCorner.x, middleAngleCorner.y, x_1, y_1, true)
                if (isCorrect) {
                    petal_slab_inter_x_first = x_1
                    petal_slab_inter_y_first = y_1
                    petal_slab_inter_x_second = x_2
                    petal_slab_inter_y_second = y_2
                } else {
                    petal_slab_inter_x_first = x_2
                    petal_slab_inter_y_first = y_2
                    petal_slab_inter_x_second = x_1
                    petal_slab_inter_y_second = y_1
                }
                xMidOfLongestEdge = (middleAngleCorner.x + smallestAngleCorner.x) / 2.0
                yMidOfLongestEdge = (middleAngleCorner.y + smallestAngleCorner.y) / 2.0
                if (!neighborNotFound_first) {
                    neighborvertex_1 = neighborotri.org()!!
                    neighborvertex_2 = neighborotri.dest()!!
                    neighborvertex_3 = neighborotri.apex()!!
                    neighborCircumcenter = predicates.findCircumcenter(neighborvertex_1, neighborvertex_2, neighborvertex_3, xi_tmp, eta_tmp, noExact)
                    vector_x = middleAngleCorner.y - smallestAngleCorner.y
                    vector_y = smallestAngleCorner.x - middleAngleCorner.x
                    vector_x += myCircumcenter.x
                    vector_y += myCircumcenter.y
                    circleLineIntersection(myCircumcenter.x, myCircumcenter.y, vector_x, vector_y, xPetalCtr, yPetalCtr, petalRadius, p)
                    isCorrect = chooseCorrectPoint(xMidOfLongestEdge, yMidOfLongestEdge, p[3], p[4], myCircumcenter.x, myCircumcenter.y, isObtuse)
                    if (isCorrect) {
                        inter_x = p[3]
                        inter_y = p[4]
                    } else {
                        inter_x = p[1]
                        inter_y = p[2]
                    }
                    linepnt1_x = middleAngleCorner.x
                    linepnt1_y = middleAngleCorner.y
                    linepnt2_x = petal_slab_inter_x_first
                    linepnt2_y = petal_slab_inter_y_first
                    lineLineIntersection(myCircumcenter.x, myCircumcenter.y, vector_x, vector_y, linepnt1_x, linepnt1_y, linepnt2_x, linepnt2_y, line_p)
                    if (line_p[0] > 0.0) {
                        line_inter_x = line_p[1]
                        line_inter_y = line_p[2]
                    } else {
                    }
                    pointBetweenPoints(inter_x, inter_y, myCircumcenter.x, myCircumcenter.y, neighborCircumcenter.x, neighborCircumcenter.y, voronoiOrInter)
                    if (p[0] > 0.0) {
                        if (Math.abs(voronoiOrInter[0] - 1.0) <= EPS) {
                            pointBetweenPoints(voronoiOrInter[2], voronoiOrInter[3], myCircumcenter.x, myCircumcenter.y, line_inter_x, line_inter_y, line_result)
                            if (Math.abs(line_result[0] - 1.0) <= EPS && line_p[0] > 0.0) {
                                if ((smallestAngleCorner.x - petal_slab_inter_x_first) * (smallestAngleCorner.x - petal_slab_inter_x_first) + (smallestAngleCorner.y - petal_slab_inter_y_first) * (smallestAngleCorner.y - petal_slab_inter_y_first) > lengthConst * ((smallestAngleCorner.x - line_inter_x) * (smallestAngleCorner.x - line_inter_x) + (smallestAngleCorner.y - line_inter_y) * (smallestAngleCorner.y - line_inter_y)) && isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, petal_slab_inter_x_first, petal_slab_inter_y_first) && minDistanceToNeighbor(petal_slab_inter_x_first, petal_slab_inter_y_first, neighborotri, noExact) > minDistanceToNeighbor(line_inter_x, line_inter_y, neighborotri, noExact)) {
                                    dxFirstSuggestion = petal_slab_inter_x_first - torg.x
                                    dyFirstSuggestion = petal_slab_inter_y_first - torg.y
                                } else {
                                    if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, line_inter_x, line_inter_y)) {
                                        d = Math.sqrt((line_inter_x - myCircumcenter.x) * (line_inter_x - myCircumcenter.x) + (line_inter_y - myCircumcenter.y) * (line_inter_y - myCircumcenter.y))
                                        ax = myCircumcenter.x - line_inter_x
                                        ay = myCircumcenter.y - line_inter_y
                                        ax /= d
                                        ay /= d
                                        line_inter_x += ax * pertConst * Math.sqrt(shortestEdgeDist)
                                        line_inter_y += ay * pertConst * Math.sqrt(shortestEdgeDist)
                                        if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, line_inter_x, line_inter_y)) {
                                            dxFirstSuggestion = dx
                                            dyFirstSuggestion = dy
                                        } else {
                                            dxFirstSuggestion = line_inter_x - torg.x
                                            dyFirstSuggestion = line_inter_y - torg.y
                                        }
                                    } else {
                                        dxFirstSuggestion = line_result[2] - torg.x
                                        dyFirstSuggestion = line_result[3] - torg.y
                                    }
                                }
                            } else {
                                if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, neighborCircumcenter.x, neighborCircumcenter.y)) {
                                    dxFirstSuggestion = dx
                                    dyFirstSuggestion = dy
                                } else {
                                    dxFirstSuggestion = voronoiOrInter[2] - torg.x
                                    dyFirstSuggestion = voronoiOrInter[3] - torg.y
                                }
                            }
                        } else {
                            pointBetweenPoints(inter_x, inter_y, myCircumcenter.x, myCircumcenter.y, line_inter_x, line_inter_y, line_result)
                            if (Math.abs(line_result[0] - 1.0) <= EPS && line_p[0] > 0.0) {
                                if ((smallestAngleCorner.x - petal_slab_inter_x_first) * (smallestAngleCorner.x - petal_slab_inter_x_first) + (smallestAngleCorner.y - petal_slab_inter_y_first) * (smallestAngleCorner.y - petal_slab_inter_y_first) > lengthConst * ((smallestAngleCorner.x - line_inter_x) * (smallestAngleCorner.x - line_inter_x) + (smallestAngleCorner.y - line_inter_y) * (smallestAngleCorner.y - line_inter_y)) && isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, petal_slab_inter_x_first, petal_slab_inter_y_first) && minDistanceToNeighbor(petal_slab_inter_x_first, petal_slab_inter_y_first, neighborotri, noExact) > minDistanceToNeighbor(line_inter_x, line_inter_y, neighborotri, noExact)) {
                                    dxFirstSuggestion = petal_slab_inter_x_first - torg.x
                                    dyFirstSuggestion = petal_slab_inter_y_first - torg.y
                                } else {
                                    if (isBadTriangleAngle(largestAngleCorner.x, largestAngleCorner.y, middleAngleCorner.x, middleAngleCorner.y, line_inter_x, line_inter_y)) {
                                        d = Math.sqrt((line_inter_x - myCircumcenter.x) * (line_inter_x - myCircumcenter.x) + (line_inter_y - myCircumcenter.y) * (line_inter_y - myCircumcenter.y))
                                        ax = myCircumcenter.x - line_inter_x
                                        ay = myCircumcenter.y - line_inter_y
                                        ax /= d
                                        ay /= d
                                        line_inter_x += ax * pertConst * Math.sqrt(shortestEdgeDist)
                                        line_inter_y += ay * pertConst * Math.sqrt(shortestEdgeDist)
                                        if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, line_inter_x, line_inter_y)) {
                                            dxFirstSuggestion = dx
                                            dyFirstSuggestion = dy
                                        } else {
                                            dxFirstSuggestion = line_inter_x - torg.x
                                            dyFirstSuggestion = line_inter_y - torg.y
                                        }
                                    } else {
                                        dxFirstSuggestion = line_result[2] - torg.x
                                        dyFirstSuggestion = line_result[3] - torg.y
                                    }
                                }
                            } else {
                                if (isBadTriangleAngle(largestAngleCorner.x, largestAngleCorner.y, middleAngleCorner.x, middleAngleCorner.y, inter_x, inter_y)) {
                                    d = Math.sqrt((inter_x - myCircumcenter.x) * (inter_x - myCircumcenter.x) + (inter_y - myCircumcenter.y) * (inter_y - myCircumcenter.y))
                                    ax = myCircumcenter.x - inter_x
                                    ay = myCircumcenter.y - inter_y
                                    ax /= d
                                    ay /= d
                                    inter_x += ax * pertConst * Math.sqrt(shortestEdgeDist)
                                    inter_y += ay * pertConst * Math.sqrt(shortestEdgeDist)
                                    if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, inter_x, inter_y)) {
                                        dxFirstSuggestion = dx
                                        dyFirstSuggestion = dy
                                    } else {
                                        dxFirstSuggestion = inter_x - torg.x
                                        dyFirstSuggestion = inter_y - torg.y
                                    }
                                } else {
                                    dxFirstSuggestion = inter_x - torg.x
                                    dyFirstSuggestion = inter_y - torg.y
                                }
                            }
                        }
                        if ((smallestAngleCorner.x - myCircumcenter.x) * (smallestAngleCorner.x - myCircumcenter.x) + (smallestAngleCorner.y - myCircumcenter.y) * (smallestAngleCorner.y - myCircumcenter.y) > lengthConst * ((smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) * (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) + (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)) * (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)))) {
                            dxFirstSuggestion = dx
                            dyFirstSuggestion = dy
                        }
                    }
                }
                neighborNotFound_second = getNeighborsVertex(badotri, largestAngleCorner.x, largestAngleCorner.y, smallestAngleCorner.x, smallestAngleCorner.y, thirdPoint, neighborotri)
                dxSecondSuggestion = dx
                dySecondSuggestion = dy
                xMidOfMiddleEdge = (largestAngleCorner.x + smallestAngleCorner.x) / 2.0
                yMidOfMiddleEdge = (largestAngleCorner.y + smallestAngleCorner.y) / 2.0
                if (!neighborNotFound_second) {
                    neighborvertex_1 = neighborotri.org()!!
                    neighborvertex_2 = neighborotri.dest()!!
                    neighborvertex_3 = neighborotri.apex()!!
                    neighborCircumcenter = predicates.findCircumcenter(neighborvertex_1, neighborvertex_2, neighborvertex_3, xi_tmp, eta_tmp, noExact)
                    vector_x = largestAngleCorner.y - smallestAngleCorner.y
                    vector_y = smallestAngleCorner.x - largestAngleCorner.x
                    vector_x += myCircumcenter.x
                    vector_y += myCircumcenter.y
                    circleLineIntersection(myCircumcenter.x, myCircumcenter.y, vector_x, vector_y, xPetalCtr, yPetalCtr, petalRadius, p)
                    isCorrect = chooseCorrectPoint(xMidOfMiddleEdge, yMidOfMiddleEdge, p[3], p[4], myCircumcenter.x, myCircumcenter.y, false)
                    if (isCorrect) {
                        inter_x = p[3]
                        inter_y = p[4]
                    } else {
                        inter_x = p[1]
                        inter_y = p[2]
                    }
                    linepnt1_x = largestAngleCorner.x
                    linepnt1_y = largestAngleCorner.y
                    linepnt2_x = petal_slab_inter_x_second
                    linepnt2_y = petal_slab_inter_y_second
                    lineLineIntersection(myCircumcenter.x, myCircumcenter.y, vector_x, vector_y, linepnt1_x, linepnt1_y, linepnt2_x, linepnt2_y, line_p)
                    if (line_p[0] > 0.0) {
                        line_inter_x = line_p[1]
                        line_inter_y = line_p[2]
                    } else {
                    }
                    pointBetweenPoints(inter_x, inter_y, myCircumcenter.x, myCircumcenter.y, neighborCircumcenter.x, neighborCircumcenter.y, voronoiOrInter)
                    if (p[0] > 0.0) {
                        if (Math.abs(voronoiOrInter[0] - 1.0) <= EPS) {
                            pointBetweenPoints(voronoiOrInter[2], voronoiOrInter[3], myCircumcenter.x, myCircumcenter.y, line_inter_x, line_inter_y, line_result)
                            if (Math.abs(line_result[0] - 1.0) <= EPS && line_p[0] > 0.0) {
                                if ((smallestAngleCorner.x - petal_slab_inter_x_second) * (smallestAngleCorner.x - petal_slab_inter_x_second) + (smallestAngleCorner.y - petal_slab_inter_y_second) * (smallestAngleCorner.y - petal_slab_inter_y_second) > lengthConst * ((smallestAngleCorner.x - line_inter_x) * (smallestAngleCorner.x - line_inter_x) + (smallestAngleCorner.y - line_inter_y) * (smallestAngleCorner.y - line_inter_y)) && isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, petal_slab_inter_x_second, petal_slab_inter_y_second) && minDistanceToNeighbor(petal_slab_inter_x_second, petal_slab_inter_y_second, neighborotri, noExact) > minDistanceToNeighbor(line_inter_x, line_inter_y, neighborotri, noExact)) {
                                    dxSecondSuggestion = petal_slab_inter_x_second - torg.x
                                    dySecondSuggestion = petal_slab_inter_y_second - torg.y
                                } else {
                                    if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, line_inter_x, line_inter_y)) {
                                        d = Math.sqrt((line_inter_x - myCircumcenter.x) * (line_inter_x - myCircumcenter.x) + (line_inter_y - myCircumcenter.y) * (line_inter_y - myCircumcenter.y))
                                        ax = myCircumcenter.x - line_inter_x
                                        ay = myCircumcenter.y - line_inter_y
                                        ax /= d
                                        ay /= d
                                        line_inter_x += ax * pertConst * Math.sqrt(shortestEdgeDist)
                                        line_inter_y += ay * pertConst * Math.sqrt(shortestEdgeDist)
                                        if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, line_inter_x, line_inter_y)) {
                                            dxSecondSuggestion = dx
                                            dySecondSuggestion = dy
                                        } else {
                                            dxSecondSuggestion = line_inter_x - torg.x
                                            dySecondSuggestion = line_inter_y - torg.y
                                        }
                                    } else {
                                        dxSecondSuggestion = line_result[2] - torg.x
                                        dySecondSuggestion = line_result[3] - torg.y
                                    }
                                }
                            } else {
                                if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, neighborCircumcenter.x, neighborCircumcenter.y)) {
                                    dxSecondSuggestion = dx
                                    dySecondSuggestion = dy
                                } else {
                                    dxSecondSuggestion = voronoiOrInter[2] - torg.x
                                    dySecondSuggestion = voronoiOrInter[3] - torg.y
                                }
                            }
                        } else {
                            pointBetweenPoints(inter_x, inter_y, myCircumcenter.x, myCircumcenter.y, line_inter_x, line_inter_y, line_result)
                            if (Math.abs(line_result[0] - 1.0) <= EPS && line_p[0] > 0.0) {
                                if ((smallestAngleCorner.x - petal_slab_inter_x_second) * (smallestAngleCorner.x - petal_slab_inter_x_second) + (smallestAngleCorner.y - petal_slab_inter_y_second) * (smallestAngleCorner.y - petal_slab_inter_y_second) > lengthConst * ((smallestAngleCorner.x - line_inter_x) * (smallestAngleCorner.x - line_inter_x) + (smallestAngleCorner.y - line_inter_y) * (smallestAngleCorner.y - line_inter_y)) && isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, petal_slab_inter_x_second, petal_slab_inter_y_second) && minDistanceToNeighbor(petal_slab_inter_x_second, petal_slab_inter_y_second, neighborotri, noExact) > minDistanceToNeighbor(line_inter_x, line_inter_y, neighborotri, noExact)) {
                                    dxSecondSuggestion = petal_slab_inter_x_second - torg.x
                                    dySecondSuggestion = petal_slab_inter_y_second - torg.y
                                } else {
                                    if (isBadTriangleAngle(largestAngleCorner.x, largestAngleCorner.y, middleAngleCorner.x, middleAngleCorner.y, line_inter_x, line_inter_y)) {
                                        d = Math.sqrt((line_inter_x - myCircumcenter.x) * (line_inter_x - myCircumcenter.x) + (line_inter_y - myCircumcenter.y) * (line_inter_y - myCircumcenter.y))
                                        ax = myCircumcenter.x - line_inter_x
                                        ay = myCircumcenter.y - line_inter_y
                                        ax /= d
                                        ay /= d
                                        line_inter_x += ax * pertConst * Math.sqrt(shortestEdgeDist)
                                        line_inter_y += ay * pertConst * Math.sqrt(shortestEdgeDist)
                                        if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, line_inter_x, line_inter_y)) {
                                            dxSecondSuggestion = dx
                                            dySecondSuggestion = dy
                                        } else {
                                            dxSecondSuggestion = line_inter_x - torg.x
                                            dySecondSuggestion = line_inter_y - torg.y
                                        }
                                    } else {
                                        dxSecondSuggestion = line_result[2] - torg.x
                                        dySecondSuggestion = line_result[3] - torg.y
                                    }
                                }
                            } else {
                                if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, inter_x, inter_y)) {
                                    d = Math.sqrt((inter_x - myCircumcenter.x) * (inter_x - myCircumcenter.x) + (inter_y - myCircumcenter.y) * (inter_y - myCircumcenter.y))
                                    ax = myCircumcenter.x - inter_x
                                    ay = myCircumcenter.y - inter_y
                                    ax /= d
                                    ay /= d
                                    inter_x += ax * pertConst * Math.sqrt(shortestEdgeDist)
                                    inter_y += ay * pertConst * Math.sqrt(shortestEdgeDist)
                                    if (isBadTriangleAngle(middleAngleCorner.x, middleAngleCorner.y, largestAngleCorner.x, largestAngleCorner.y, inter_x, inter_y)) {
                                        dxSecondSuggestion = dx
                                        dySecondSuggestion = dy
                                    } else {
                                        dxSecondSuggestion = inter_x - torg.x
                                        dySecondSuggestion = inter_y - torg.y
                                    }
                                } else {
                                    dxSecondSuggestion = inter_x - torg.x
                                    dySecondSuggestion = inter_y - torg.y
                                }
                            }
                        }
                        if ((smallestAngleCorner.x - myCircumcenter.x) * (smallestAngleCorner.x - myCircumcenter.x) + (smallestAngleCorner.y - myCircumcenter.y) * (smallestAngleCorner.y - myCircumcenter.y) > lengthConst * ((smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) * (smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) + (smallestAngleCorner.y - (dySecondSuggestion + torg.y)) * (smallestAngleCorner.y - (dySecondSuggestion + torg.y)))) {
                            dxSecondSuggestion = dx
                            dySecondSuggestion = dy
                        }
                    }
                }
                if (isObtuse) {
                    if (neighborNotFound_first && neighborNotFound_second) {
                        if (justAcute * ((smallestAngleCorner.x - xMidOfMiddleEdge) * (smallestAngleCorner.x - xMidOfMiddleEdge) + (smallestAngleCorner.y - yMidOfMiddleEdge) * (smallestAngleCorner.y - yMidOfMiddleEdge)) > (smallestAngleCorner.x - xMidOfLongestEdge) * (smallestAngleCorner.x - xMidOfLongestEdge) + (smallestAngleCorner.y - yMidOfLongestEdge) * (smallestAngleCorner.y - yMidOfLongestEdge)) {
                            dx = dxSecondSuggestion
                            dy = dySecondSuggestion
                        } else {
                            dx = dxFirstSuggestion
                            dy = dyFirstSuggestion
                        }
                    } else if (neighborNotFound_first) {
                        if (justAcute * ((smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) * (smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) + (smallestAngleCorner.y - (dySecondSuggestion + torg.y)) * (smallestAngleCorner.y - (dySecondSuggestion + torg.y))) > (smallestAngleCorner.x - xMidOfLongestEdge) * (smallestAngleCorner.x - xMidOfLongestEdge) + (smallestAngleCorner.y - yMidOfLongestEdge) * (smallestAngleCorner.y - yMidOfLongestEdge)) {
                            dx = dxSecondSuggestion
                            dy = dySecondSuggestion
                        } else {
                            dx = dxFirstSuggestion
                            dy = dyFirstSuggestion
                        }
                    } else if (neighborNotFound_second) {
                        if (justAcute * ((smallestAngleCorner.x - xMidOfMiddleEdge) * (smallestAngleCorner.x - xMidOfMiddleEdge) + (smallestAngleCorner.y - yMidOfMiddleEdge) * (smallestAngleCorner.y - yMidOfMiddleEdge)) > (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) * (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) + (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)) * (smallestAngleCorner.y - (dyFirstSuggestion + torg.y))) {
                            dx = dxSecondSuggestion
                            dy = dySecondSuggestion
                        } else {
                            dx = dxFirstSuggestion
                            dy = dyFirstSuggestion
                        }
                    } else {
                        if (justAcute * ((smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) * (smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) + (smallestAngleCorner.y - (dySecondSuggestion + torg.y)) * (smallestAngleCorner.y - (dySecondSuggestion + torg.y))) > (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) * (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) + (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)) * (smallestAngleCorner.y - (dyFirstSuggestion + torg.y))) {
                            dx = dxSecondSuggestion
                            dy = dySecondSuggestion
                        } else {
                            dx = dxFirstSuggestion
                            dy = dyFirstSuggestion
                        }
                    }
                } else {
                    if (neighborNotFound_first && neighborNotFound_second) {
                        if (justAcute * ((smallestAngleCorner.x - xMidOfMiddleEdge) * (smallestAngleCorner.x - xMidOfMiddleEdge) + (smallestAngleCorner.y - yMidOfMiddleEdge) * (smallestAngleCorner.y - yMidOfMiddleEdge)) > (smallestAngleCorner.x - xMidOfLongestEdge) * (smallestAngleCorner.x - xMidOfLongestEdge) + (smallestAngleCorner.y - yMidOfLongestEdge) * (smallestAngleCorner.y - yMidOfLongestEdge)) {
                            dx = dxSecondSuggestion
                            dy = dySecondSuggestion
                        } else {
                            dx = dxFirstSuggestion
                            dy = dyFirstSuggestion
                        }
                    } else if (neighborNotFound_first) {
                        if (justAcute * ((smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) * (smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) + (smallestAngleCorner.y - (dySecondSuggestion + torg.y)) * (smallestAngleCorner.y - (dySecondSuggestion + torg.y))) > (smallestAngleCorner.x - xMidOfLongestEdge) * (smallestAngleCorner.x - xMidOfLongestEdge) + (smallestAngleCorner.y - yMidOfLongestEdge) * (smallestAngleCorner.y - yMidOfLongestEdge)) {
                            dx = dxSecondSuggestion
                            dy = dySecondSuggestion
                        } else {
                            dx = dxFirstSuggestion
                            dy = dyFirstSuggestion
                        }
                    } else if (neighborNotFound_second) {
                        if (justAcute * ((smallestAngleCorner.x - xMidOfMiddleEdge) * (smallestAngleCorner.x - xMidOfMiddleEdge) + (smallestAngleCorner.y - yMidOfMiddleEdge) * (smallestAngleCorner.y - yMidOfMiddleEdge)) > (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) * (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) + (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)) * (smallestAngleCorner.y - (dyFirstSuggestion + torg.y))) {
                            dx = dxSecondSuggestion
                            dy = dySecondSuggestion
                        } else {
                            dx = dxFirstSuggestion
                            dy = dyFirstSuggestion
                        }
                    } else {
                        if (justAcute * ((smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) * (smallestAngleCorner.x - (dxSecondSuggestion + torg.x)) + (smallestAngleCorner.y - (dySecondSuggestion + torg.y)) * (smallestAngleCorner.y - (dySecondSuggestion + torg.y))) > (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) * (smallestAngleCorner.x - (dxFirstSuggestion + torg.x)) + (smallestAngleCorner.y - (dyFirstSuggestion + torg.y)) * (smallestAngleCorner.y - (dyFirstSuggestion + torg.y))) {
                            dx = dxSecondSuggestion
                            dy = dySecondSuggestion
                        } else {
                            dx = dxFirstSuggestion
                            dy = dyFirstSuggestion
                        }
                    }
                }
            }
        }
        val circumcenter = Point()
        if (relocated <= 0) {
            circumcenter.x = torg.x + dx
            circumcenter.y = torg.y + dy
        } else {
            circumcenter.x = origin_x + dx
            circumcenter.y = origin_y + dy
        }
        xi.value = (yao * dx - xao * dy) * (2.0 * denominator)
        eta.value = (xdo * dy - ydo * dx) * (2.0 * denominator)
        return circumcenter
    }

    private fun longestShortestEdge(aodist: Double, dadist: Double, dodist: Double): Int {
        val max: Int
        val min: Int
        val mid: Int
        val minMidMax: Int
        if (dodist < aodist && dodist < dadist) {
            min = 3
            if (aodist < dadist) {
                max = 2
                mid = 1
            } else {
                max = 1
                mid = 2
            }
        } else
            if (aodist < dadist) {
                min = 1
                if (dodist < dadist) {
                    max = 2
                    mid = 3
                } else {
                    max = 3
                    mid = 2
                }
            } else {
                min = 2
                if (aodist < dodist) {
                    max = 3
                    mid = 1
                } else {
                    max = 1
                    mid = 3
                }
            }
        minMidMax = min * 100 + mid * 10 + max
        return minMidMax
    }

    private fun doSmoothing(badotri: OTri, torg: Vertex, tdest: Vertex, tapex: Vertex, newloc: DoubleArray): Int {
        val possibilities = DoubleArray(6)
        var num_pos: Int = 0
        var flag1: Int = 0
        var flag2: Int = 0
        var flag3: Int = 0
        var newLocFound: Boolean
        val numpoints_p = getStarPoints(badotri, torg, tdest, tapex, 1, points_p)
        if (torg.type === VertexType.FREE_VERTEX && numpoints_p != 0 && validPolygonAngles(numpoints_p, points_p)) {
            if (behavior.maxAngleConstraint == 0.0) {
                newLocFound = getWedgeIntersectionWithoutMaxAngle(numpoints_p, points_p, newloc)
            } else {
                newLocFound = getWedgeIntersection(numpoints_p, points_p, newloc)
            }
            if (newLocFound) {
                possibilities[0] = newloc[0]
                possibilities[1] = newloc[1]
                num_pos++
                flag1 = 1
            }
        }
        val numpoints_q = getStarPoints(badotri, torg, tdest, tapex, 2, points_q)
        if (tdest.type === VertexType.FREE_VERTEX && numpoints_q != 0 && validPolygonAngles(numpoints_q, points_q)) {
            if (behavior.maxAngleConstraint == 0.0) {
                newLocFound = getWedgeIntersectionWithoutMaxAngle(numpoints_q, points_q, newloc)
            } else {
                newLocFound = getWedgeIntersection(numpoints_q, points_q, newloc)
            }
            if (newLocFound) {
                possibilities[2] = newloc[0]
                possibilities[3] = newloc[1]
                num_pos++
                flag2 = 2
            }
        }
        val numpoints_r = getStarPoints(badotri, torg, tdest, tapex, 3, points_r)
        if (tapex.type === VertexType.FREE_VERTEX && numpoints_r != 0 && validPolygonAngles(numpoints_r, points_r)) {
            if (behavior.maxAngleConstraint == 0.0) {
                newLocFound = getWedgeIntersectionWithoutMaxAngle(numpoints_r, points_r, newloc)
            } else {
                newLocFound = getWedgeIntersection(numpoints_r, points_r, newloc)
            }
            if (newLocFound) {
                possibilities[4] = newloc[0]
                possibilities[5] = newloc[1]
                num_pos++
                flag3 = 3
            }
        }
        if (num_pos > 0) {
            if (flag1 > 0) {
                newloc[0] = possibilities[0]
                newloc[1] = possibilities[1]
                return flag1
            } else {
                if (flag2 > 0) {
                    newloc[0] = possibilities[2]
                    newloc[1] = possibilities[3]
                    return flag2
                } else {
                    if (flag3 > 0) {
                        newloc[0] = possibilities[4]
                        newloc[1] = possibilities[5]
                        return flag3
                    }
                }
            }
        }
        return 0
    }

    private fun getStarPoints(badotri: OTri, p: Vertex, q: Vertex, r: Vertex, whichPoint: Int, points: DoubleArray): Int {
        val neighotri = OTri()
        val tempotri = OTri()
        var first_x: Double = 0.0
        var first_y: Double = 0.0
        var second_x: Double = 0.0
        var second_y: Double = 0.0
        var third_x: Double = 0.0
        var third_y: Double = 0.0
        val returnPoint = DoubleArray(2)
        var numvertices: Int = 0
        when (whichPoint) {
            1 -> {
                first_x = p.x
                first_y = p.y
                second_x = r.x
                second_y = r.y
                third_x = q.x
                third_y = q.y
            }
            2 -> {
                first_x = q.x
                first_y = q.y
                second_x = p.x
                second_y = p.y
                third_x = r.x
                third_y = r.y
            }
            3 -> {
                first_x = r.x
                first_y = r.y
                second_x = q.x
                second_y = q.y
                third_x = p.x
                third_y = p.y
            }
        }
        badotri.copy(tempotri)
        points[numvertices] = second_x
        numvertices++
        points[numvertices] = second_y
        numvertices++
        returnPoint[0] = second_x
        returnPoint[1] = second_y
        do {
            val boolVar___2 = !getNeighborsVertex(tempotri, first_x, first_y, second_x, second_y, returnPoint, neighotri)
            if (boolVar___2) {
                neighotri.copy(tempotri)
                second_x = returnPoint[0]
                second_y = returnPoint[1]
                points[numvertices] = returnPoint[0]
                numvertices++
                points[numvertices] = returnPoint[1]
                numvertices++
            } else {
                numvertices = 0
                break
            }
        } while (!(Math.abs(returnPoint[0] - third_x) <= EPS && Math.abs(returnPoint[1] - third_y) <= EPS))
        return numvertices / 2
    }

    private fun getNeighborsVertex(constBadotri: OTri, first_x: Double, first_y: Double, second_x: Double, second_y: Double, thirdpoint: DoubleArray, neighotri: OTri): Boolean {
        val badotri = constBadotri.copy()
        val neighbor = OTri()
        var notFound = false
        var neighborvertex_1: Vertex? = null
        var neighborvertex_2: Vertex? = null
        var neighborvertex_3: Vertex? = null
        var firstVertexMatched = 0
        var secondVertexMatched = 0
        badotri.orient = 0
        while (badotri.orient < 3) {
            badotri.sym(neighbor)
            if (neighbor.triangle!!.id != Mesh.DUMMY) {
                neighborvertex_1 = neighbor.org()
                neighborvertex_2 = neighbor.dest()
                neighborvertex_3 = neighbor.apex()
                if (neighborvertex_1!!.x == neighborvertex_2!!.x && neighborvertex_1.y == neighborvertex_2.y || neighborvertex_2.x == neighborvertex_3!!.x && neighborvertex_2.y == neighborvertex_3.y || neighborvertex_1.x == neighborvertex_3.x && neighborvertex_1.y == neighborvertex_3.y) {
                } else {
                    firstVertexMatched = 0
                    if (Math.abs(first_x - neighborvertex_1.x) < EPS && Math.abs(first_y - neighborvertex_1.y) < EPS) {
                        firstVertexMatched = 11
                    } else
                        if (Math.abs(first_x - neighborvertex_2.x) < EPS && Math.abs(first_y - neighborvertex_2.y) < EPS) {
                            firstVertexMatched = 12
                        } else
                            if (Math.abs(first_x - neighborvertex_3.x) < EPS && Math.abs(first_y - neighborvertex_3.y) < EPS) {
                                firstVertexMatched = 13
                            }
                    secondVertexMatched = 0
                    if (Math.abs(second_x - neighborvertex_1.x) < EPS && Math.abs(second_y - neighborvertex_1.y) < EPS) {
                        secondVertexMatched = 21
                    } else
                        if (Math.abs(second_x - neighborvertex_2.x) < EPS && Math.abs(second_y - neighborvertex_2.y) < EPS) {
                            secondVertexMatched = 22
                        } else
                            if (Math.abs(second_x - neighborvertex_3.x) < EPS && Math.abs(second_y - neighborvertex_3.y) < EPS) {
                                secondVertexMatched = 23
                            }
                }
            }
            if (firstVertexMatched == 11 && (secondVertexMatched == 22 || secondVertexMatched == 23) || firstVertexMatched == 12 && (secondVertexMatched == 21 || secondVertexMatched == 23) || firstVertexMatched == 13 && (secondVertexMatched == 21 || secondVertexMatched == 22))
                break
            badotri.orient++
        }
        when (firstVertexMatched) {
            0 -> notFound = true
            11 -> if (secondVertexMatched == 22) {
                thirdpoint[0] = neighborvertex_3!!.x
                thirdpoint[1] = neighborvertex_3.y
            } else if (secondVertexMatched == 23) {
                thirdpoint[0] = neighborvertex_2!!.x
                thirdpoint[1] = neighborvertex_2.y
            } else {
                notFound = true
            }
            12 -> if (secondVertexMatched == 21) {
                thirdpoint[0] = neighborvertex_3!!.x
                thirdpoint[1] = neighborvertex_3.y
            } else if (secondVertexMatched == 23) {
                thirdpoint[0] = neighborvertex_1!!.x
                thirdpoint[1] = neighborvertex_1.y
            } else {
                notFound = true
            }
            13 -> if (secondVertexMatched == 21) {
                thirdpoint[0] = neighborvertex_2!!.x
                thirdpoint[1] = neighborvertex_2.y
            } else if (secondVertexMatched == 22) {
                thirdpoint[0] = neighborvertex_1!!.x
                thirdpoint[1] = neighborvertex_1.y
            } else {
                notFound = true
            }
            else -> if (secondVertexMatched == 0) {
                notFound = true
            }
        }
        neighbor.copy(neighotri)
        return notFound
    }

    private fun getWedgeIntersectionWithoutMaxAngle(numpoints: Int, points: DoubleArray, newloc: DoubleArray): Boolean {
        var x0: Double
        var y0: Double
        var x1: Double
        var y1: Double
        var x2: Double
        var y2: Double
        var x01: Double
        var y01: Double
        var d01: Double
        if (2 * numpoints > petalx.size) {
            petalx = DoubleArray(2 * numpoints)
            petaly = DoubleArray(2 * numpoints)
            petalr = DoubleArray(2 * numpoints)
            wedges = DoubleArray(2 * numpoints * 16 + 36)
        }
        var xmid: Double
        var ymid: Double
        var dist: Double
        var x3: Double
        var y3: Double
        var x_1: Double
        var y_1: Double
        var x_2: Double
        var y_2: Double
        var x_3: Double
        var y_3: Double
        var x_4: Double
        var y_4: Double
        var tempx: Double
        var tempy: Double
        var ux: Double
        var uy: Double
        val p1 = DoubleArray(3)
        var numpolypoints: Int = 0
        var i: Int
        var j: Int
        val s: Int
        var flag: Int
        var count: Int
        var num: Int
        val petalcenterconstant: Double
        val petalradiusconstant: Double
        x0 = points[2 * numpoints - 4]
        y0 = points[2 * numpoints - 3]
        x1 = points[2 * numpoints - 2]
        y1 = points[2 * numpoints - 1]
        val alpha = behavior.minAngleConstraint * Math.PI / 180.0
        if (behavior.goodAngle == 1.0) {
            petalcenterconstant = 0.0
            petalradiusconstant = 0.0
        } else {
            petalcenterconstant = 0.5 / Math.tan(alpha)
            petalradiusconstant = 0.5 / Math.sin(alpha)
        }
        i = 0
        while (i < numpoints * 2) {
            x2 = points[i]
            y2 = points[i + 1]
            x01 = x1 - x0
            y01 = y1 - y0
            d01 = Math.sqrt(x01 * x01 + y01 * y01)
            petalx[i / 2] = x0 + 0.5 * x01 - petalcenterconstant * y01
            petaly[i / 2] = y0 + 0.5 * y01 + petalcenterconstant * x01
            petalr[i / 2] = petalradiusconstant * d01
            petalx[numpoints + i / 2] = petalx[i / 2]
            petaly[numpoints + i / 2] = petaly[i / 2]
            petalr[numpoints + i / 2] = petalr[i / 2]
            xmid = (x0 + x1) / 2.0
            ymid = (y0 + y1) / 2.0
            dist = Math.sqrt((petalx[i / 2] - xmid) * (petalx[i / 2] - xmid) + (petaly[i / 2] - ymid) * (petaly[i / 2] - ymid))
            ux = (petalx[i / 2] - xmid) / dist
            uy = (petaly[i / 2] - ymid) / dist
            x3 = petalx[i / 2] + ux * petalr[i / 2]
            y3 = petaly[i / 2] + uy * petalr[i / 2]
            x_1 = x1 * Math.cos(alpha) - y1 * Math.sin(alpha) + x0 - x0 * Math.cos(alpha) + y0 * Math.sin(alpha)
            y_1 = x1 * Math.sin(alpha) + y1 * Math.cos(alpha) + y0 - x0 * Math.sin(alpha) - y0 * Math.cos(alpha)
            wedges[i * 16] = x0
            wedges[i * 16 + 1] = y0
            wedges[i * 16 + 2] = x_1
            wedges[i * 16 + 3] = y_1
            x_2 = x0 * Math.cos(alpha) + y0 * Math.sin(alpha) + x1 - x1 * Math.cos(alpha) - y1 * Math.sin(alpha)
            y_2 = -x0 * Math.sin(alpha) + y0 * Math.cos(alpha) + y1 + x1 * Math.sin(alpha) - y1 * Math.cos(alpha)
            wedges[i * 16 + 4] = x_2
            wedges[i * 16 + 5] = y_2
            wedges[i * 16 + 6] = x1
            wedges[i * 16 + 7] = y1
            tempx = x3
            tempy = y3
            j = 1
            while (j < 4) {
                x_3 = x3 * Math.cos((Math.PI / 3.0 - alpha) * j) + y3 * Math.sin((Math.PI / 3.0 - alpha) * j) + petalx[i / 2] - petalx[i / 2] * Math.cos((Math.PI / 3.0 - alpha) * j) - petaly[i / 2] * Math.sin((Math.PI / 3.0 - alpha) * j)
                y_3 = -x3 * Math.sin((Math.PI / 3.0 - alpha) * j) + y3 * Math.cos((Math.PI / 3.0 - alpha) * j) + petaly[i / 2] + petalx[i / 2] * Math.sin((Math.PI / 3.0 - alpha) * j) - petaly[i / 2] * Math.cos((Math.PI / 3.0 - alpha) * j)
                wedges[i * 16 + 8 + 4 * (j - 1)] = x_3
                wedges[i * 16 + 9 + 4 * (j - 1)] = y_3
                wedges[i * 16 + 10 + 4 * (j - 1)] = tempx
                wedges[i * 16 + 11 + 4 * (j - 1)] = tempy
                tempx = x_3
                tempy = y_3
                j++
            }
            tempx = x3
            tempy = y3
            j = 1
            while (j < 4) {
                x_4 = x3 * Math.cos((Math.PI / 3.0 - alpha) * j) - y3 * Math.sin((Math.PI / 3.0 - alpha) * j) + petalx[i / 2] - petalx[i / 2] * Math.cos((Math.PI / 3.0 - alpha) * j) + petaly[i / 2] * Math.sin((Math.PI / 3.0 - alpha) * j)
                y_4 = x3 * Math.sin((Math.PI / 3.0 - alpha) * j) + y3 * Math.cos((Math.PI / 3.0 - alpha) * j) + petaly[i / 2] - petalx[i / 2] * Math.sin((Math.PI / 3.0 - alpha) * j) - petaly[i / 2] * Math.cos((Math.PI / 3.0 - alpha) * j)
                wedges[i * 16 + 20 + 4 * (j - 1)] = tempx
                wedges[i * 16 + 21 + 4 * (j - 1)] = tempy
                wedges[i * 16 + 22 + 4 * (j - 1)] = x_4
                wedges[i * 16 + 23 + 4 * (j - 1)] = y_4
                tempx = x_4
                tempy = y_4
                j++
            }
            if (i == 0) {
                lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_2, y_2, p1)
                if (p1[0] == 1.0) {
                    initialConvexPoly[0] = p1[1]
                    initialConvexPoly[1] = p1[2]
                    initialConvexPoly[2] = wedges[i * 16 + 16]
                    initialConvexPoly[3] = wedges[i * 16 + 17]
                    initialConvexPoly[4] = wedges[i * 16 + 12]
                    initialConvexPoly[5] = wedges[i * 16 + 13]
                    initialConvexPoly[6] = wedges[i * 16 + 8]
                    initialConvexPoly[7] = wedges[i * 16 + 9]
                    initialConvexPoly[8] = x3
                    initialConvexPoly[9] = y3
                    initialConvexPoly[10] = wedges[i * 16 + 22]
                    initialConvexPoly[11] = wedges[i * 16 + 23]
                    initialConvexPoly[12] = wedges[i * 16 + 26]
                    initialConvexPoly[13] = wedges[i * 16 + 27]
                    initialConvexPoly[14] = wedges[i * 16 + 30]
                    initialConvexPoly[15] = wedges[i * 16 + 31]
                }
            }
            x0 = x1
            y0 = y1
            x1 = x2
            y1 = y2
            i += 2
        }
        if (numpoints != 0) {
            s = (numpoints - 1) / 2 + 1
            flag = 0
            count = 0
            i = 1
            num = 8
            j = 0
            while (j < 32) {
                numpolypoints = halfPlaneIntersection(num, initialConvexPoly, wedges[32 * s + j], wedges[32 * s + 1 + j], wedges[32 * s + 2 + j], wedges[32 * s + 3 + j])
                if (numpolypoints == 0)
                    return false
                else
                    num = numpolypoints
                j += 4
            }
            count++
            while (count < numpoints - 1) {
                j = 0
                while (j < 32) {
                    numpolypoints = halfPlaneIntersection(num, initialConvexPoly, wedges[32 * (i + s * flag) + j], wedges[32 * (i + s * flag) + 1 + j], wedges[32 * (i + s * flag) + 2 + j], wedges[32 * (i + s * flag) + 3 + j])
                    if (numpolypoints == 0)
                        return false
                    else
                        num = numpolypoints
                    j += 4
                }
                i += flag
                flag = (flag + 1) % 2
                count++
            }
            findPolyCentroid(numpolypoints, initialConvexPoly, newloc)
            if (!behavior.fixedArea) {
                return true
            }
        }
        return false
    }

    private fun getWedgeIntersection(numpoints: Int, points: DoubleArray, newloc: DoubleArray): Boolean {
        var x0: Double
        var y0: Double
        var x1: Double
        var y1: Double
        var x2: Double
        var y2: Double
        var x01: Double
        var y01: Double
        var d01: Double
        if (2 * numpoints > petalx.size) {
            petalx = DoubleArray(2 * numpoints)
            petaly = DoubleArray(2 * numpoints)
            petalr = DoubleArray(2 * numpoints)
            wedges = DoubleArray(2 * numpoints * 20 + 40)
        }
        var xmid: Double
        var ymid: Double
        var dist: Double
        var x3: Double
        var y3: Double
        var x_1: Double
        var y_1: Double
        var x_2: Double
        var y_2: Double
        var x_3: Double
        var y_3: Double
        var x_4: Double
        var y_4: Double
        var tempx: Double
        var tempy: Double
        var x_5: Double
        var y_5: Double
        var x_6: Double
        var y_6: Double
        var ux: Double
        var uy: Double
        val p1 = DoubleArray(3)
        val p2 = DoubleArray(3)
        val p3 = DoubleArray(3)
        val p4 = DoubleArray(3)
        var numpolypoints: Int = 0
        var howManyPoints: Int = 0
        var line345: Double
        var line789: Double
        var numBadTriangle: Int
        var i: Int
        var j: Int
        var k: Int
        val s: Int
        var flag: Int
        var count: Int
        var num: Int
        val n: Int
        var e: Int
        var weight: Double
        val petalcenterconstant: Double
        val petalradiusconstant: Double
        x0 = points[2 * numpoints - 4]
        y0 = points[2 * numpoints - 3]
        x1 = points[2 * numpoints - 2]
        y1 = points[2 * numpoints - 1]
        var alpha: Double
        val sinAlpha: Double
        val cosAlpha: Double
        val sinBeta: Double
        val cosBeta: Double
        alpha = behavior.minAngleConstraint * Math.PI / 180.0
        sinAlpha = Math.sin(alpha)
        cosAlpha = Math.cos(alpha)
        val beta = behavior.maxAngleConstraint * Math.PI / 180.0
        sinBeta = Math.sin(beta)
        cosBeta = Math.cos(beta)
        if (behavior.goodAngle == 1.0) {
            petalcenterconstant = 0.0
            petalradiusconstant = 0.0
        } else {
            petalcenterconstant = 0.5 / Math.tan(alpha)
            petalradiusconstant = 0.5 / Math.sin(alpha)
        }
        i = 0
        while (i < numpoints * 2) {
            x2 = points[i]
            y2 = points[i + 1]
            x01 = x1 - x0
            y01 = y1 - y0
            d01 = Math.sqrt(x01 * x01 + y01 * y01)
            petalx[i / 2] = x0 + 0.5 * x01 - petalcenterconstant * y01
            petaly[i / 2] = y0 + 0.5 * y01 + petalcenterconstant * x01
            petalr[i / 2] = petalradiusconstant * d01
            petalx[numpoints + i / 2] = petalx[i / 2]
            petaly[numpoints + i / 2] = petaly[i / 2]
            petalr[numpoints + i / 2] = petalr[i / 2]
            xmid = (x0 + x1) / 2.0
            ymid = (y0 + y1) / 2.0
            dist = Math.sqrt((petalx[i / 2] - xmid) * (petalx[i / 2] - xmid) + (petaly[i / 2] - ymid) * (petaly[i / 2] - ymid))
            ux = (petalx[i / 2] - xmid) / dist
            uy = (petaly[i / 2] - ymid) / dist
            x3 = petalx[i / 2] + ux * petalr[i / 2]
            y3 = petaly[i / 2] + uy * petalr[i / 2]
            x_1 = x1 * cosAlpha - y1 * sinAlpha + x0 - x0 * cosAlpha + y0 * sinAlpha
            y_1 = x1 * sinAlpha + y1 * cosAlpha + y0 - x0 * sinAlpha - y0 * cosAlpha
            wedges[i * 20] = x0
            wedges[i * 20 + 1] = y0
            wedges[i * 20 + 2] = x_1
            wedges[i * 20 + 3] = y_1
            x_2 = x0 * cosAlpha + y0 * sinAlpha + x1 - x1 * cosAlpha - y1 * sinAlpha
            y_2 = -x0 * sinAlpha + y0 * cosAlpha + y1 + x1 * sinAlpha - y1 * cosAlpha
            wedges[i * 20 + 4] = x_2
            wedges[i * 20 + 5] = y_2
            wedges[i * 20 + 6] = x1
            wedges[i * 20 + 7] = y1
            tempx = x3
            tempy = y3
            alpha = 2.0 * behavior.maxAngleConstraint + behavior.minAngleConstraint - 180.0
            if (alpha <= 0.0) {
                howManyPoints = 4
                line345 = 1.0
                line789 = 1.0
            } else if (alpha <= 5.0) {
                howManyPoints = 6
                line345 = 2.0
                line789 = 2.0
            } else if (alpha <= 10.0) {
                howManyPoints = 8
                line345 = 3.0
                line789 = 3.0
            } else {
                howManyPoints = 10
                line345 = 4.0
                line789 = 4.0
            }
            alpha = alpha * Math.PI / 180.0
            j = 1
            while (j < line345) {
                if (line345 == 1.0) {
                    j++
                    continue
                }
                x_3 = x3 * Math.cos(alpha / (line345 - 1.0) * j) + y3 * Math.sin(alpha / (line345 - 1.0) * j) + petalx[i / 2] - petalx[i / 2] * Math.cos(alpha / (line345 - 1.0) * j) - petaly[i / 2] * Math.sin(alpha / (line345 - 1.0) * j)
                y_3 = -x3 * Math.sin(alpha / (line345 - 1.0) * j) + y3 * Math.cos(alpha / (line345 - 1.0) * j) + petaly[i / 2] + petalx[i / 2] * Math.sin(alpha / (line345 - 1.0) * j) - petaly[i / 2] * Math.cos(alpha / (line345 - 1.0) * j)
                wedges[i * 20 + 8 + 4 * (j - 1)] = x_3
                wedges[i * 20 + 9 + 4 * (j - 1)] = y_3
                wedges[i * 20 + 10 + 4 * (j - 1)] = tempx
                wedges[i * 20 + 11 + 4 * (j - 1)] = tempy
                tempx = x_3
                tempy = y_3
                j++
            }
            x_5 = x0 * cosBeta + y0 * sinBeta + x1 - x1 * cosBeta - y1 * sinBeta
            y_5 = -x0 * sinBeta + y0 * cosBeta + y1 + x1 * sinBeta - y1 * cosBeta
            wedges[i * 20 + 20] = x1
            wedges[i * 20 + 21] = y1
            wedges[i * 20 + 22] = x_5
            wedges[i * 20 + 23] = y_5
            tempx = x3
            tempy = y3
            j = 1
            while (j < line789) {
                if (line789 == 1.0) {
                    j++
                    continue
                }
                x_4 = x3 * Math.cos(alpha / (line789 - 1.0) * j) - y3 * Math.sin(alpha / (line789 - 1.0) * j) + petalx[i / 2] - petalx[i / 2] * Math.cos(alpha / (line789 - 1.0) * j) + petaly[i / 2] * Math.sin(alpha / (line789 - 1.0) * j)
                y_4 = x3 * Math.sin(alpha / (line789 - 1.0) * j) + y3 * Math.cos(alpha / (line789 - 1.0) * j) + petaly[i / 2] - petalx[i / 2] * Math.sin(alpha / (line789 - 1.0) * j) - petaly[i / 2] * Math.cos(alpha / (line789 - 1.0) * j)
                wedges[i * 20 + 24 + 4 * (j - 1)] = tempx
                wedges[i * 20 + 25 + 4 * (j - 1)] = tempy
                wedges[i * 20 + 26 + 4 * (j - 1)] = x_4
                wedges[i * 20 + 27 + 4 * (j - 1)] = y_4
                tempx = x_4
                tempy = y_4
                j++
            }
            x_6 = x1 * cosBeta - y1 * sinBeta + x0 - x0 * cosBeta + y0 * sinBeta
            y_6 = x1 * sinBeta + y1 * cosBeta + y0 - x0 * sinBeta - y0 * cosBeta
            wedges[i * 20 + 36] = x_6
            wedges[i * 20 + 37] = y_6
            wedges[i * 20 + 38] = x0
            wedges[i * 20 + 39] = y0
            if (i == 0) {
                when (howManyPoints) {
                    4 -> {
                        lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_2, y_2, p1)
                        lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_5, y_5, p2)
                        lineLineIntersection(x0, y0, x_6, y_6, x1, y1, x_5, y_5, p3)
                        lineLineIntersection(x0, y0, x_6, y_6, x1, y1, x_2, y_2, p4)
                        if (p1[0] == 1.0 && p2[0] == 1.0 && p3[0] == 1.0 && p4[0] == 1.0) {
                            initialConvexPoly[0] = p1[1]
                            initialConvexPoly[1] = p1[2]
                            initialConvexPoly[2] = p2[1]
                            initialConvexPoly[3] = p2[2]
                            initialConvexPoly[4] = p3[1]
                            initialConvexPoly[5] = p3[2]
                            initialConvexPoly[6] = p4[1]
                            initialConvexPoly[7] = p4[2]
                        }
                    }
                    6 -> {
                        lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_2, y_2, p1)
                        lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_5, y_5, p2)
                        lineLineIntersection(x0, y0, x_6, y_6, x1, y1, x_2, y_2, p3)
                        if (p1[0] == 1.0 && p2[0] == 1.0 && p3[0] == 1.0) {
                            initialConvexPoly[0] = p1[1]
                            initialConvexPoly[1] = p1[2]
                            initialConvexPoly[2] = p2[1]
                            initialConvexPoly[3] = p2[2]
                            initialConvexPoly[4] = wedges[i * 20 + 8]
                            initialConvexPoly[5] = wedges[i * 20 + 9]
                            initialConvexPoly[6] = x3
                            initialConvexPoly[7] = y3
                            initialConvexPoly[8] = wedges[i * 20 + 26]
                            initialConvexPoly[9] = wedges[i * 20 + 27]
                            initialConvexPoly[10] = p3[1]
                            initialConvexPoly[11] = p3[2]
                        }
                    }
                    8 -> {
                        lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_2, y_2, p1)
                        lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_5, y_5, p2)
                        lineLineIntersection(x0, y0, x_6, y_6, x1, y1, x_2, y_2, p3)
                        if (p1[0] == 1.0 && p2[0] == 1.0 && p3[0] == 1.0) {
                            initialConvexPoly[0] = p1[1]
                            initialConvexPoly[1] = p1[2]
                            initialConvexPoly[2] = p2[1]
                            initialConvexPoly[3] = p2[2]
                            initialConvexPoly[4] = wedges[i * 20 + 12]
                            initialConvexPoly[5] = wedges[i * 20 + 13]
                            initialConvexPoly[6] = wedges[i * 20 + 8]
                            initialConvexPoly[7] = wedges[i * 20 + 9]
                            initialConvexPoly[8] = x3
                            initialConvexPoly[9] = y3
                            initialConvexPoly[10] = wedges[i * 20 + 26]
                            initialConvexPoly[11] = wedges[i * 20 + 27]
                            initialConvexPoly[12] = wedges[i * 20 + 30]
                            initialConvexPoly[13] = wedges[i * 20 + 31]
                            initialConvexPoly[14] = p3[1]
                            initialConvexPoly[15] = p3[2]
                        }
                    }
                    10 -> {
                        lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_2, y_2, p1)
                        lineLineIntersection(x0, y0, x_1, y_1, x1, y1, x_5, y_5, p2)
                        lineLineIntersection(x0, y0, x_6, y_6, x1, y1, x_2, y_2, p3)
                        if (p1[0] == 1.0 && p2[0] == 1.0 && p3[0] == 1.0) {
                            initialConvexPoly[0] = p1[1]
                            initialConvexPoly[1] = p1[2]
                            initialConvexPoly[2] = p2[1]
                            initialConvexPoly[3] = p2[2]
                            initialConvexPoly[4] = wedges[i * 20 + 16]
                            initialConvexPoly[5] = wedges[i * 20 + 17]
                            initialConvexPoly[6] = wedges[i * 20 + 12]
                            initialConvexPoly[7] = wedges[i * 20 + 13]
                            initialConvexPoly[8] = wedges[i * 20 + 8]
                            initialConvexPoly[9] = wedges[i * 20 + 9]
                            initialConvexPoly[10] = x3
                            initialConvexPoly[11] = y3
                            initialConvexPoly[12] = wedges[i * 20 + 28]
                            initialConvexPoly[13] = wedges[i * 20 + 29]
                            initialConvexPoly[14] = wedges[i * 20 + 32]
                            initialConvexPoly[15] = wedges[i * 20 + 33]
                            initialConvexPoly[16] = wedges[i * 20 + 34]
                            initialConvexPoly[17] = wedges[i * 20 + 35]
                            initialConvexPoly[18] = p3[1]
                            initialConvexPoly[19] = p3[2]
                        }
                    }
                }
            }
            x0 = x1
            y0 = y1
            x1 = x2
            y1 = y2
            i += 2
        }
        if (numpoints != 0) {
            s = (numpoints - 1) / 2 + 1
            flag = 0
            count = 0
            i = 1
            num = howManyPoints
            j = 0
            while (j < 40) {
                if (howManyPoints == 4 && (j == 8 || j == 12 || j == 16 || j == 24 || j == 28 || j == 32)) {
                    j += 4
                    continue
                } else if (howManyPoints == 6 && (j == 12 || j == 16 || j == 28 || j == 32)) {
                    continue
                } else if (howManyPoints == 8 && (j == 16 || j == 32)) {
                    continue
                }
                numpolypoints = halfPlaneIntersection(num, initialConvexPoly, wedges[40 * s + j], wedges[40 * s + 1 + j], wedges[40 * s + 2 + j], wedges[40 * s + 3 + j])
                if (numpolypoints == 0)
                    return false
                else
                    num = numpolypoints
                j += 4
            }
            count++
            while (count < numpoints - 1) {
                j = 0
                while (j < 40) {
                    if (howManyPoints == 4 && (j == 8 || j == 12 || j == 16 || j == 24 || j == 28 || j == 32)) {
                        j += 4
                        continue
                    } else if (howManyPoints == 6 && (j == 12 || j == 16 || j == 28 || j == 32)) {
                        continue
                    } else if (howManyPoints == 8 && (j == 16 || j == 32)) {
                        continue
                    }
                    numpolypoints = halfPlaneIntersection(num, initialConvexPoly, wedges[40 * (i + s * flag) + j], wedges[40 * (i + s * flag) + 1 + j], wedges[40 * (i + s * flag) + 2 + j], wedges[40 * (i + s * flag) + 3 + j])
                    if (numpolypoints == 0)
                        return false
                    else
                        num = numpolypoints
                    j += 4
                }
                i += flag
                flag = (flag + 1) % 2
                count++
            }
            findPolyCentroid(numpolypoints, initialConvexPoly, newloc)
            if (behavior.maxAngleConstraint != 0.0) {
                numBadTriangle = 0
                j = 0
                while (j < numpoints * 2 - 2) {
                    if (isBadTriangleAngle(newloc[0], newloc[1], points[j], points[j + 1], points[j + 2], points[j + 3])) {
                        numBadTriangle++
                    }
                    j += 2
                }
                if (isBadTriangleAngle(newloc[0], newloc[1], points[0], points[1], points[numpoints * 2 - 2], points[numpoints * 2 - 1])) {
                    numBadTriangle++
                }
                if (numBadTriangle == 0) {
                    return true
                }
                n = if (numpoints <= 2) 20 else 30
                k = 0
                while (k < 2 * numpoints) {
                    e = 1
                    while (e < n) {
                        newloc[0] = 0.0
                        newloc[1] = 0.0
                        i = 0
                        while (i < 2 * numpoints) {
                            weight = 1.0 / numpoints
                            if (i == k) {
                                newloc[0] = newloc[0] + 0.1 * e.toDouble() * weight * points[i]
                                newloc[1] = newloc[1] + 0.1 * e.toDouble() * weight * points[i + 1]
                            } else {
                                weight = (1.0 - 0.1 * e.toDouble() * weight) / (numpoints - 1.0)
                                newloc[0] = newloc[0] + weight * points[i]
                                newloc[1] = newloc[1] + weight * points[i + 1]
                            }
                            i += 2
                        }
                        numBadTriangle = 0
                        j = 0
                        while (j < numpoints * 2 - 2) {
                            if (isBadTriangleAngle(newloc[0], newloc[1], points[j], points[j + 1], points[j + 2], points[j + 3])) {
                                numBadTriangle++
                            }
                            j += 2
                        }
                        if (isBadTriangleAngle(newloc[0], newloc[1], points[0], points[1], points[numpoints * 2 - 2], points[numpoints * 2 - 1])) {
                            numBadTriangle++
                        }
                        if (numBadTriangle == 0) {
                            return true
                        }
                        e += 1
                    }
                    k += 2
                }
            } else {
                return true
            }
        }
        return false
    }

    private fun validPolygonAngles(numpoints: Int, points: DoubleArray): Boolean {
        var i = 0
        while (i < numpoints) {
            if (i == numpoints - 1) {
                if (isBadPolygonAngle(points[i * 2], points[i * 2 + 1], points[0], points[1], points[2], points[3])) {
                    return false
                }
            } else
                if (i == numpoints - 2) {
                    if (isBadPolygonAngle(points[i * 2], points[i * 2 + 1], points[(i + 1) * 2], points[(i + 1) * 2 + 1], points[0], points[1])) {
                        return false
                    }
                } else {
                    if (isBadPolygonAngle(points[i * 2], points[i * 2 + 1], points[(i + 1) * 2], points[(i + 1) * 2 + 1], points[(i + 2) * 2], points[(i + 2) * 2 + 1])) {
                        return false
                    }
                }
            i++
        }
        return true
    }

    private fun isBadPolygonAngle(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Boolean {
        val dx12 = x1 - x2
        val dy12 = y1 - y2
        val dx23 = x2 - x3
        val dy23 = y2 - y3
        val dx31 = x3 - x1
        val dy31 = y3 - y1
        val dist12 = dx12 * dx12 + dy12 * dy12
        val dist23 = dx23 * dx23 + dy23 * dy23
        val dist31 = dx31 * dx31 + dy31 * dy31
        val cosAngle = (dist12 + dist23 - dist31) / (2.0 * Math.sqrt(dist12) * Math.sqrt(dist23))
        if (Math.acos(cosAngle) < 2 * Math.acos(Math.sqrt(behavior.goodAngle))) {
            return true
        }
        return false
    }

    private fun lineLineIntersection(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, x4: Double, y4: Double, p: DoubleArray) {
        val denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1)
        var u_a = (x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)
        var u_b = (x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)
        if (Math.abs(denom - 0.0) < EPS && Math.abs(u_b - 0.0) < EPS && Math.abs(u_a - 0.0) < EPS) {
            p[0] = 0.0
        } else
            if (Math.abs(denom - 0.0) < EPS) {
                p[0] = 0.0
            } else {
                p[0] = 1.0
                u_a /= denom
                u_b /= denom
                p[1] = x1 + u_a * (x2 - x1)
                p[2] = y1 + u_a * (y2 - y1)
            }
    }

    private fun halfPlaneIntersection(numvertices: Int, convexPoly: DoubleArray, x1: Double, y1: Double, x2: Double, y2: Double): Int {
        var z: Double
        var min: Double
        var max: Double
        var i: Int
        var j: Int
        var res: DoubleArray? = null
        var count = 0
        var intFound = 0
        val dx = x2 - x1
        val dy = y2 - y1
        val numpolys = splitConvexPolygon(numvertices, convexPoly, x1, y1, x2, y2, polys)
        if (numpolys == 3) {
            count = numvertices
        } else {
            i = 0
            while (i < numpolys) {
                min = java.lang.Double.MAX_VALUE
                max = java.lang.Double.MIN_VALUE
                j = 1
                while (j <= 2 * polys[i][0] - 1) {
                    z = dx * (polys[i][j + 1] - y1) - dy * (polys[i][j] - x1)
                    min = if (z < min) z else min
                    max = if (z > max) z else max
                    j += 2
                }
                z = if (Math.abs(min) > Math.abs(max)) min else max
                if (z > 0.0) {
                    res = polys[i]
                    intFound = 1
                    break
                }
                i++
            }
            if (intFound == 1) {
                while (count < res!![0]) {
                    convexPoly[2 * count] = res[2 * count + 1]
                    convexPoly[2 * count + 1] = res[2 * count + 2]
                    count++
                }
            }
        }
        return count
    }

    private fun splitConvexPolygon(numvertices: Int, convexPoly: DoubleArray, x1: Double, y1: Double, x2: Double, y2: Double, polys: Array<DoubleArray>): Int {
        var state = 0
        val p = DoubleArray(3)
        var poly1counter = 0
        var poly2counter = 0
        val numpolys: Int
        val compConst = 0.000000000001
        var case1 = 0
        var case2 = 0
        var case3 = 0
        var case31 = 0
        var case32 = 0
        var case33 = 0
        var case311 = 0
        var case3111 = 0
        var i = 0
        while (i < 2 * numvertices) {
            val j = if (i + 2 >= 2 * numvertices) 0 else i + 2
            lineLineSegmentIntersection(x1, y1, x2, y2, convexPoly[i], convexPoly[i + 1], convexPoly[j], convexPoly[j + 1], p)
            if (Math.abs(p[0] - 0.0) <= compConst) {
                if (state == 1) {
                    poly2counter++
                    poly2[2 * poly2counter - 1] = convexPoly[j]
                    poly2[2 * poly2counter] = convexPoly[j + 1]
                } else {
                    poly1counter++
                    poly1[2 * poly1counter - 1] = convexPoly[j]
                    poly1[2 * poly1counter] = convexPoly[j + 1]
                }
                case1++
            } else
                if (Math.abs(p[0] - 2.0) <= compConst) {
                    poly1counter++
                    poly1[2 * poly1counter - 1] = convexPoly[j]
                    poly1[2 * poly1counter] = convexPoly[j + 1]
                    case2++
                } else {
                    case3++
                    if (Math.abs(p[1] - convexPoly[j]) <= compConst && Math.abs(p[2] - convexPoly[j + 1]) <= compConst) {
                        case31++
                        if (state == 1) {
                            poly2counter++
                            poly2[2 * poly2counter - 1] = convexPoly[j]
                            poly2[2 * poly2counter] = convexPoly[j + 1]
                            poly1counter++
                            poly1[2 * poly1counter - 1] = convexPoly[j]
                            poly1[2 * poly1counter] = convexPoly[j + 1]
                            state++
                        } else if (state == 0) {
                            case311++
                            poly1counter++
                            poly1[2 * poly1counter - 1] = convexPoly[j]
                            poly1[2 * poly1counter] = convexPoly[j + 1]
                            if (i + 4 < 2 * numvertices) {
                                val s1 = linePointLocation(x1, y1, x2, y2, convexPoly[i], convexPoly[i + 1])
                                val s2 = linePointLocation(x1, y1, x2, y2, convexPoly[i + 4], convexPoly[i + 5])
                                if (s1 != s2 && s1 != 0 && s2 != 0) {
                                    case3111++
                                    poly2counter++
                                    poly2[2 * poly2counter - 1] = convexPoly[j]
                                    poly2[2 * poly2counter] = convexPoly[j + 1]
                                    state++
                                }
                            }
                        }
                    } else
                        if (!(Math.abs(p[1] - convexPoly[i]) <= compConst && Math.abs(p[2] - convexPoly[i + 1]) <= compConst)) {
                            case32++
                            poly1counter++
                            poly1[2 * poly1counter - 1] = p[1]
                            poly1[2 * poly1counter] = p[2]
                            poly2counter++
                            poly2[2 * poly2counter - 1] = p[1]
                            poly2[2 * poly2counter] = p[2]
                            if (state == 1) {
                                poly1counter++
                                poly1[2 * poly1counter - 1] = convexPoly[j]
                                poly1[2 * poly1counter] = convexPoly[j + 1]
                            } else if (state == 0) {
                                poly2counter++
                                poly2[2 * poly2counter - 1] = convexPoly[j]
                                poly2[2 * poly2counter] = convexPoly[j + 1]
                            }
                            state++
                        } else {
                            case33++
                            if (state == 1) {
                                poly2counter++
                                poly2[2 * poly2counter - 1] = convexPoly[j]
                                poly2[2 * poly2counter] = convexPoly[j + 1]
                            } else {
                                poly1counter++
                                poly1[2 * poly1counter - 1] = convexPoly[j]
                                poly1[2 * poly1counter] = convexPoly[j + 1]
                            }
                        }
                }
            i += 2
        }
        if (state != 0 && state != 2) {
            numpolys = 3
        } else {
            numpolys = if (state == 0) 1 else 2
            poly1[0] = poly1counter.toDouble()
            poly2[0] = poly2counter.toDouble()
            polys[0] = poly1
            if (state == 2) {
                polys[1] = poly2
            }
        }
        return numpolys
    }

    private fun linePointLocation(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double): Int {
        if (Math.atan((y2 - y1) / (x2 - x1)) * 180.0 / Math.PI == 90.0) {
            if (Math.abs(x1 - x) <= 0.00000000001)
                return 0
        } else {
            if (Math.abs(y1 + (y2 - y1) * (x - x1) / (x2 - x1) - y) <= EPS)
                return 0
        }
        val z = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
        if (Math.abs(z - 0.0) <= 0.00000000001) {
            return 0
        } else if (z > 0) {
            return 1
        } else {
            return 2
        }
    }

    private fun lineLineSegmentIntersection(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, x4: Double, y4: Double, p: DoubleArray) {
        val compConst = 0.0000000000001
        val denom = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1)
        var u_a = (x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)
        var u_b = (x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)
        if (Math.abs(denom - 0.0) < compConst) {
            if (Math.abs(u_b - 0.0) < compConst && Math.abs(u_a - 0.0) < compConst) {
                p[0] = 2.0
            } else {
                p[0] = 0.0
            }
        } else {
            u_b /= denom
            u_a /= denom
            if (u_b < -compConst || u_b > 1.0 + compConst) {
                p[0] = 0.0
            } else {
                p[0] = 1.0
                p[1] = x1 + u_a * (x2 - x1)
                p[2] = y1 + u_a * (y2 - y1)
            }
        }
    }

    private fun findPolyCentroid(numpoints: Int, points: DoubleArray, centroid: DoubleArray) {
        centroid[0] = 0.0
        centroid[1] = 0.0
        var i = 0
        while (i < 2 * numpoints) {
            centroid[0] = centroid[0] + points[i]
            centroid[1] = centroid[1] + points[i + 1]
            i += 2
        }
        centroid[0] = centroid[0] / numpoints
        centroid[1] = centroid[1] / numpoints
    }

    private fun circleLineIntersection(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, r: Double, p: DoubleArray) {
        var mu: Double
        val a = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
        val b = 2 * ((x2 - x1) * (x1 - x3) + (y2 - y1) * (y1 - y3))
        val c = x3 * x3 + y3 * y3 + x1 * x1 + y1 * y1 - 2 * (x3 * x1 + y3 * y1) - r * r
        val i = b * b - 4.0 * a * c
        if (i < 0.0) {
            p[0] = 0.0
        } else if (Math.abs(i - 0.0) < EPS) {
            p[0] = 1.0
            mu = -b / (2 * a)
            p[1] = x1 + mu * (x2 - x1)
            p[2] = y1 + mu * (y2 - y1)
        } else if (i > 0.0 && Math.abs(a - 0.0) >= EPS) {
            p[0] = 2.0
            mu = (-b + Math.sqrt(i)) / (2 * a)
            p[1] = x1 + mu * (x2 - x1)
            p[2] = y1 + mu * (y2 - y1)
            mu = (-b - Math.sqrt(i)) / (2 * a)
            p[3] = x1 + mu * (x2 - x1)
            p[4] = y1 + mu * (y2 - y1)
        } else {
            p[0] = 0.0
        }
    }

    private fun chooseCorrectPoint(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double, isObtuse: Boolean): Boolean {
        val d1 = (x2 - x3) * (x2 - x3) + (y2 - y3) * (y2 - y3)
        val d2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
        return if (isObtuse) {
            d2 >= d1
        } else {
            d2 < d1
        }
    }

    private fun pointBetweenPoints(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double, p: DoubleArray) {
        if ((x2 - x) * (x2 - x) + (y2 - y) * (y2 - y) < (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)) {
            p[0] = 1.0
            p[1] = (x - x2) * (x - x2) + (y - y2) * (y - y2)
            p[2] = x
            p[3] = y
        } else {
            p[0] = 0.0
            p[1] = 0.0
            p[2] = 0.0
            p[3] = 0.0
        }
    }

    private fun isBadTriangleAngle(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Boolean {
        val dxod = x1 - x2
        val dyod = y1 - y2
        val dxda = x2 - x3
        val dyda = y2 - y3
        val dxao = x3 - x1
        val dyao = y3 - y1
        val dxod2 = dxod * dxod
        val dyod2 = dyod * dyod
        val dxda2 = dxda * dxda
        val dyda2 = dyda * dyda
        val dxao2 = dxao * dxao
        val dyao2 = dyao * dyao
        val apexlen = dxod2 + dyod2
        val orglen = dxda2 + dyda2
        val destlen = dxao2 + dyao2
        var angle: Double
        if (apexlen < orglen && apexlen < destlen) {
            angle = dxda * dxao + dyda * dyao
            angle = angle * angle / (orglen * destlen)
        } else if (orglen < destlen) {
            angle = dxod * dxao + dyod * dyao
            angle = angle * angle / (apexlen * destlen)
        } else {
            angle = dxod * dxda + dyod * dyda
            angle = angle * angle / (apexlen * orglen)
        }
        val maxAngle = if (apexlen > orglen && apexlen > destlen) {
            (orglen + destlen - apexlen) / (2 * Math.sqrt(orglen * destlen))
        } else if (orglen > destlen) {
            (apexlen + destlen - orglen) / (2 * Math.sqrt(apexlen * destlen))
        } else {
            (apexlen + orglen - destlen) / (2 * Math.sqrt(apexlen * orglen))
        }
        if (angle > behavior.goodAngle || behavior.maxAngleConstraint != 0.00 && maxAngle < behavior.maxGoodAngle) {
            return true
        }
        return false
    }

    private fun minDistanceToNeighbor(newlocX: Double, newlocY: Double, searchtri: OTri, noExact: Boolean): Double {
        val horiz = OTri()
        var intersect = LocateResult.OUTSIDE
        val v1: Vertex
        val v2: Vertex
        val v3: Vertex
        val d1: Double
        val d2: Double
        val d3: Double
        val ahead: Double
        val newvertex = Point(newlocX, newlocY)
        val torg = searchtri.org()!!
        val tdest = searchtri.dest()!!
        if (torg.x == newvertex.x && torg.y == newvertex.y) {
            intersect = LocateResult.ON_VERTEX
            searchtri.copy(horiz)
        } else if (tdest.x == newvertex.x && tdest.y == newvertex.y) {
            searchtri.lnext()
            intersect = LocateResult.ON_VERTEX
            searchtri.copy(horiz)
        } else {
            ahead = predicates.counterClockwise(torg, tdest, newvertex, noExact)
            if (ahead < 0.0) {
                searchtri.sym()
                searchtri.copy(horiz)
                intersect = mesh.locator.preciseLocate(newvertex, horiz, false, noExact)
            } else if (ahead == 0.0) {
                if (torg.x < newvertex.x == newvertex.x < tdest.x && torg.y < newvertex.y == newvertex.y < tdest.y) {
                    intersect = LocateResult.ON_EDGE
                    searchtri.copy(horiz)
                }
            } else {
                searchtri.copy(horiz)
                intersect = mesh.locator.preciseLocate(newvertex, horiz, false, noExact)
            }
        }
        if (intersect == LocateResult.ON_VERTEX || intersect == LocateResult.OUTSIDE) {
            return 0.0
        } else {
            v1 = horiz.org()!!
            v2 = horiz.dest()!!
            v3 = horiz.apex()!!
            d1 = (v1.x - newvertex.x) * (v1.x - newvertex.x) + (v1.y - newvertex.y) * (v1.y - newvertex.y)
            d2 = (v2.x - newvertex.x) * (v2.x - newvertex.x) + (v2.y - newvertex.y) * (v2.y - newvertex.y)
            d3 = (v3.x - newvertex.x) * (v3.x - newvertex.x) + (v3.y - newvertex.y) * (v3.y - newvertex.y)
            if (d1 <= d2 && d1 <= d3) {
                return d1
            } else if (d2 <= d3) {
                return d2
            } else {
                return d3
            }
        }
    }

    companion object {
        internal val EPS = 1e-50
    }
}
