package com.grimfox.gec.command

import com.grimfox.gec.Main
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.model.geometry.LineSegment2F.Companion.getConnectedEdgeSegments
import com.grimfox.gec.util.Coastline.applyMask
import com.grimfox.gec.util.Coastline.getBorders
import com.grimfox.gec.util.Coastline.getCoastline
import com.grimfox.gec.util.Coastline.refineCoastline
import com.grimfox.gec.util.drawing.*
import com.grimfox.gec.util.Regions.buildRegions
import com.grimfox.gec.util.Rivers
import com.grimfox.gec.util.Rivers.RiverNode
import com.grimfox.gec.util.Rivers.buildRiverGraph
import com.grimfox.gec.util.Rivers.buildRivers
import com.grimfox.gec.util.Triangulate
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils
import com.grimfox.gec.util.Utils.generatePoints
import com.grimfox.gec.util.Utils.generateSemiUniformPoints
import com.sun.scenario.animation.SplineInterpolator
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.awt.BasicStroke
import java.awt.Color
import java.io.File
import java.util.*

@Command(name = "build-continent", description = "Builds a continent.")
class BuildContinent() : Runnable {

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var randomSeed: Long = System.currentTimeMillis()

    @Option(name = arrayOf("-s", "--strides"), description = "The number of points in the stride of each iteration.", required = false)
    var strides: ArrayList<Int> = ArrayList()

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    var draw = true

    data class Parameters(
            var stride: Int,
            var landPercent: Float,
            var minPerturbation: Float,
            var maxIterations: Int,
            var protectedInset: Float,
            var protectedRadius: Float,
            var minRegionSize: Float,
            var largeIsland: Float,
            var smallIsland: Float)

    data class ParameterSet(
            var seed: Long = System.currentTimeMillis(),
            var stride: Int = 7,
            var regionCount: Int = 8,
            var connectedness: Float = 0.11f,
            var regionSize: Float = 0.035f,
            var initialReduction: Int = 7,
            var regionPoints: Int = 2,
            var maxRegionTries: Int = 200,
            var maxIslandTries: Int = 2000,
            var islandDesire: Int = 1,
            var parameters: ArrayList<Parameters> = arrayListOf(
                    Parameters(30, 0.35f, 0.05f, 4, 0.1f, 0.05f, 0.035f, 2.0f, 0.0f),
                    Parameters(80, 0.39f, 0.05f, 3, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f),
                    Parameters(140, 0.39f, 0.03f, 2, 0.1f, 0.05f, 0.035f, 2.0f, 0.01f),
                    Parameters(256, 0.39f, 0.01f, 2, 0.1f, 0.05f, 0.035f, 2.0f, 0.015f)
            ),
            var currentIteration: Int = 0)

    override fun run() {
        val parameterSet = ParameterSet()
        if (strides.isEmpty()) {
            strides.addAll(listOf(7, 40, 80, 140, 256))
        } else {
            strides.sort()
        }
        for (test in 1..10000) {
            val virtualWidth = 100000.0f
            val outputWidth = 8192
            parameterSet.seed = test.toLong()
            val random = Random(parameterSet.seed)
            var (graph, regionMask) = buildRegions(parameterSet)
            parameterSet.parameters.forEachIndexed { i, parameters ->
                parameterSet.currentIteration = i
                val localLastGraph = graph
                val localGraph: Graph
                val points = generatePoints(parameters.stride, virtualWidth, random)
                localGraph = buildGraph(virtualWidth, points, parameters.stride)
                regionMask = applyMask(localGraph, localLastGraph, regionMask)
                refineCoastline(localGraph, random, regionMask, parameters)
                graph = localGraph
            }
            val rivers = buildRivers(graph, regionMask, random)
//            draw(outputWidth, "test-new-${String.format("%05d", test)}-concavity") {
//                graphics.color = Color.BLACK
//                drawConcavity(graph, rivers.first)
//            }
            val borders = getBorders(graph, regionMask)
            rivers.forEachIndexed { i, body ->
                val coastline = body.first
                val coastSpline = Spline2F(coastline)
                val riverSet = body.second
                val border = borders[i]
                val riverGraph = buildRiverGraph(riverSet)
                val riverFlows = calculateRiverFlows(riverGraph.vertices, coastline, riverSet, 1600000000.0f, 0.39f)
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-rivers$i", Color(160, 200, 255)) {
//                    drawRivers(graph, regionMask, riverSet, listOf(coastSpline), border)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-graph$i", Color.WHITE) {
//                    drawGraph(riverGraph)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-mask$i", Color.BLACK) {
//                    graphics.color = Color.WHITE
//                    fillSpline(coastSpline)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-coast$i") {
//                    graphics.color = Color.BLACK
//                    drawSpline(coastSpline, false)
//                }
                draw(outputWidth, "test-new-${String.format("%05d", test)}-coast$i") {
                    graphics.color = Color.BLACK
                    drawPolygon(coastline, true)
                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-ids$i") {
//                    graphics.color = Color.BLACK
//                    drawVertexIds(riverGraph)
//                }
                val reverseRiverMap = HashMap<Int, RiverNode>()
                riverSet.forEach {
                    it.forEach { node ->
                        reverseRiverMap[node.pointIndex] = node
                    }
                }
                val crestElevations = HashMap<Int, Float>(riverGraph.triangles.size)
                riverGraph.triangles.forEach { crest ->
                    val riverA = reverseRiverMap[crest.a.id]!!
                    val riverB = reverseRiverMap[crest.b.id]!!
                    val riverC = reverseRiverMap[crest.c.id]!!
                    val elevationStart = Math.max(riverA.elevation, Math.max(riverB.elevation, riverC.elevation))
                    val terrainSlopeAvg = (riverA.maxTerrainSlope + riverB.maxTerrainSlope + riverC.maxTerrainSlope) / 3.0f
                    val radius = crest.center.distance(crest.a.point)
                    crestElevations[crest.id] = elevationStart + (radius * terrainSlopeAvg)
                }

                val riverSplines = calculateRiverSegments(riverGraph, random, riverSet, riverFlows, test)
                val cellsToRiverSegments = HashMap<Int, ArrayList<RiverSegment>>()
                riverSplines.forEach {
                    it.forEach {
                        cellsToRiverSegments.getOrPut(it.vertexId, { ArrayList() }).add(it)
                    }
                }

                val coastMultigon = Multigon2F(coastline, 20)
                val coastCells = ArrayList<Int>()
                riverGraph.vertices.forEach { vertex ->
                    if (cellIntersectsCoast(coastMultigon, vertex)) {
                        coastCells.add(vertex.id)
                    }
                }
                val riverPoints = LinkedHashSet(riverGraph.vertices.map { Point2FKey(it.point) })
                riverPoints.retainAll(LinkedHashSet(coastline.points.map { Point2FKey(it) }))

                val edgePolys = HashMap<Int, Polygon2F>()
                val unconnectedPolys = HashMap<Int, ArrayList<Polygon2F>>()
                val riverPolys = HashMap<Int, ArrayList<Polygon2F>>()

                coastCells.forEach {
                    val vertex = riverGraph.vertices[it]
                    val polygons = getConnectedPolygons(buildConnectedEdgeSegments(coastMultigon, coastline, vertex, cellsToRiverSegments[it]))
                    polygons.forEach {
                        if (riverPoints.contains(Point2FKey(vertex.point))) {
                            if (containsPoint(it, vertex.point)) {
                                edgePolys[vertex.id] = it
                            } else {
                                unconnectedPolys.getOrPut(vertex.id, { ArrayList() }).add(it)
                            }
                        } else {
                            if (it.isWithin(vertex.point)) {
                                edgePolys[vertex.id] = it
                            } else {
                                unconnectedPolys.getOrPut(vertex.id, { ArrayList() }).add(it)
                            }
                        }
                    }
                }

                val revisedEdgePolys = HashMap<Int, Polygon2F>()
                val adjacencyPatches = HashMap<Int, HashSet<Int>>()
                val inclusionPatches = HashMap<Int, HashSet<Int>>()

                edgePolys.forEach {
                    val id = it.key
                    var polygon = it.value
                    val adjacentUnconnected = ArrayList<Pair<Int, Polygon2F>>()
                    riverGraph.vertices.getAdjacentVertices(id).forEach { id ->
                        unconnectedPolys[id]?.forEach { adjacentUnconnected.add(Pair(id, it)) }
                    }
                    val polysToTake = ArrayList<Pair<Int, Polygon2F>>()
                    val newConnections = HashSet<Int>()
                    adjacentUnconnected.forEach { adjacentPoly ->
                        val secondAdjacentEdgePolys = ArrayList<Pair<Int, Polygon2F>>()
                        riverGraph.vertices.getAdjacentVertices(adjacentPoly.first).forEach {
                            val adjacentEdgePoly = edgePolys[it]
                            if (adjacentEdgePoly != null) {
                                secondAdjacentEdgePolys.add(Pair(it, adjacentEdgePoly))
                            }
                        }
                        var winner = -1
                        var winningConnection = 0.0f
                        val localNewConnections = ArrayList<Int>()
                        secondAdjacentEdgePolys.forEach { secondAdjacentPoly ->
                            var connectedness = 0.0f
                            adjacentPoly.second.edges.forEach { adjacentPolyEdge ->
                                secondAdjacentPoly.second.edges.forEach { localPolyEdge ->
                                    if (adjacentPolyEdge.epsilonEquals(localPolyEdge)) {
                                        connectedness += localPolyEdge.length
                                    }
                                }
                            }
                            if (connectedness > 0.0f) {
                                localNewConnections.add(secondAdjacentPoly.first)
                            }
                            if (connectedness > winningConnection) {
                                winner = secondAdjacentPoly.first
                                winningConnection = connectedness
                            }
                        }
                        if (winner == id) {
                            polysToTake.add(adjacentPoly)
                            newConnections.addAll(localNewConnections)
                            inclusionPatches.getOrPut(id, { HashSet() }).add(adjacentPoly.first)
                        }
                    }
                    polysToTake.forEach {
                        unconnectedPolys[it.first]?.remove(it.second)
                        val combinedEdges = ArrayList<LineSegment2F>(it.second.edges)
                        combinedEdges.addAll(polygon.edges)
                        it.second.edges.forEach { adjacentEdge ->
                            polygon.edges.forEach { localEdge ->
                                if (localEdge.epsilonEquals(adjacentEdge)) {
                                    combinedEdges.remove(localEdge)
                                    combinedEdges.remove(adjacentEdge)
                                }
                            }
                        }
                        polygon = Polygon2F.fromUnsortedEdges(combinedEdges)
                    }
                    newConnections.remove(id)
                    newConnections.removeAll(riverGraph.vertices.getAdjacentVertices(id))
                    adjacencyPatches.getOrPut(id, { HashSet() }).addAll(newConnections)
                    newConnections.forEach {
                        adjacencyPatches.getOrPut(it, { HashSet() }).add(id)
                    }
                    val localRiverPolys = ArrayList<Polygon2F>()
                    cellsToRiverSegments[id]?.forEach { riverSegment ->
                        if (riverSegment.spline.points.size > 1) {
                            val polyLine = riverSegment.spline.toPolygon(1.0f / 512, 3)
                            val length = polyLine.edges.map { it.length }.sum()
                            val startElevation = riverSegment.elevations.a
                            val endElevation = riverSegment.elevations.b
                            val elevationDelta = endElevation - startElevation
                            val slope = elevationDelta / length
                            val pointsIn3d = ArrayList<Point3F>()
                            val startPoint = polyLine.points.first()
                            var lastPoint = Point3F(startPoint.x, startPoint.y, startElevation)
                            pointsIn3d.add(lastPoint)
                            for (edge in polyLine.edges) {
                                val newPoint = Point3F(edge.b.x, edge.b.y, lastPoint.z + (edge.length * slope))
                                pointsIn3d.add(newPoint)
                                lastPoint = newPoint
                            }
                            pointsIn3d.removeAt(pointsIn3d.size - 1)
                            val endPoint = polyLine.points.last()
                            pointsIn3d.add(Point3F(endPoint.x, endPoint.y, endElevation))
                            localRiverPolys.add(Polygon2F(pointsIn3d, false))
                        }
                    }
                    revisedEdgePolys[id] = revisePolygonIfRiverIntersects(riverGraph, edgePolys, cellsToRiverSegments, adjacencyPatches, riverGraph.vertices[id], polygon, localRiverPolys)
                    riverPolys.put(id, localRiverPolys)
                }

                val interpolatedEdgePolys = HashMap<Int, Polygon2F>()

                revisedEdgePolys.forEach {
                    val id = it.key
                    val polygon = it.value
                    val vertex = riverGraph.vertices[id]
                    val borderPoints = getUntouchablePoints(riverGraph, revisedEdgePolys, adjacencyPatches, vertex, cellsToRiverSegments[id])
                    val interiorEdges = ArrayList<LineSegment2F>()
                    val coastEdges = ArrayList<LineSegment2F>()
                    polygon.edges.forEach {
                        if (borderPoints.contains(Point2FKey(it.a)) && borderPoints.contains(Point2FKey(it.b))) {
                            interiorEdges.add(it)
                        } else {
                            coastEdges.add(it)
                        }
                    }
                    if (coastEdges.isNotEmpty()) {
                        val coasts = getConnectedEdgeSegments(coastEdges)
                        val subdividedCoastEdges = ArrayList<LineSegment2F>()
                        coasts.forEach {
                            if (it.isNotEmpty()) {
                                val coast = Spline2F(Polygon2F.fromUnsortedEdges(it)).toPolygon(1.0f / 512, 3)
                                subdividedCoastEdges.addAll(coast.edges)
                            }
                        }
                        interpolatedEdgePolys[id] = Polygon2F.fromUnsortedEdges(subdividedCoastEdges + interiorEdges)
                    } else {
                        interpolatedEdgePolys[id] = polygon
                    }
                }

                val edgeSkeletons = HashMap<Int, ArrayList<LineSegment3F>>()
                val riverSkeletons = HashMap<Int, ArrayList<LineSegment3F>>()

                interpolatedEdgePolys.forEach {
                    val localEdgeSkeleton = ArrayList<LineSegment3F>()
                    val localRiverSkeleton = ArrayList<LineSegment3F>()
                    val id = it.key
                    val polygon = it.value
                    val untouchablePoints = getUntouchablePoints(riverGraph, revisedEdgePolys, adjacencyPatches, riverGraph.vertices[id], cellsToRiverSegments[id])
                    val pointsWithHeights = HashMap<Point2FKey, Point3F>()
                    val localRiverPolys = riverPolys[id]
                    if (localRiverPolys != null) {
                        localRiverSkeleton.addAll(localRiverPolys.flatMap { it.edges }.map { LineSegment3F(it.a as Point3F, it.b as Point3F) })
                        pointsWithHeights.putAll(localRiverSkeleton.flatMap { listOf(Point2FKey(it.a), Point2FKey(it.b)) }.map { Pair(it, it.point as Point3F) })
                    }
                    (listOf(riverGraph.vertices[id]) + ((inclusionPatches[id] ?: HashSet()).map { riverGraph.vertices[it] })).flatMap { it.adjacentTriangles }.forEach {
                        val height = crestElevations[it.id]
                        if (height != null) {
                            val point = Point3F(it.center.x, it.center.y, height)
                            pointsWithHeights[Point2FKey(point)] = point
                        }
                    }
                    polygon.edges.forEach {
                        val a2d = it.a
                        val b2d = it.b
                        val aKey = Point2FKey(a2d)
                        val a3d = pointsWithHeights[aKey] ?: Point3F(a2d.x, a2d.y, 0.0f)
                        val bKey = Point2FKey(b2d)
                        val b3d = pointsWithHeights[bKey] ?: Point3F(b2d.x, b2d.y, 0.0f)
                        if (a3d.z == 0.0f && b3d.z == 0.0f && untouchablePoints.contains(aKey) && untouchablePoints.contains(bKey)) {
                            val mid = LineSegment2F(a3d, b3d).interpolate(0.5f)
                            val height = a3d.distance(mid) * 0.03f
                            val c3d = Point3F(mid.x, mid.y, height)
                            localEdgeSkeleton.addAll(LineSegment3F(a3d, c3d).subdivided2d(1.0f / 512))
                            localEdgeSkeleton.addAll(LineSegment3F(c3d, b3d).subdivided2d(1.0f / 512))
                        } else {
                            localEdgeSkeleton.addAll(LineSegment3F(a3d, b3d).subdivided2d(1.0f / 512))
                        }
                    }
                    edgeSkeletons[id] = localEdgeSkeleton
                    riverSkeletons[id] = localRiverSkeleton
                }

                val fillPoints = ArrayList<Point2F>()

                interpolatedEdgePolys.forEach {
                    val id = it.key
                    val polygon = it.value
                    val edgeSkeleton = edgeSkeletons[id]
                    val riverSkeleton = riverSkeletons[id] ?: arrayListOf()
                    if (edgeSkeleton != null) {
                        val startPoints = LinkedHashSet((riverSkeleton.flatMap { listOf(Point2FKey(it.a, 1400), Point2FKey(it.b, 1400)) } + edgeSkeleton.flatMap { listOf(Point2FKey(it.a, 1400), Point2FKey(it.b, 1400)) }).flatMap {
                            listOf(Point2FKey(it.x - 1, it.y - 1, it.point),
                                    Point2FKey(it.x - 1, it.y, it.point),
                                    Point2FKey(it.x - 1, it.y + 1, it.point),
                                    Point2FKey(it.x, it.y - 1, it.point),
                                    Point2FKey(it.x, it.y, it.point),
                                    Point2FKey(it.x, it.y + 1, it.point),
                                    Point2FKey(it.x + 1, it.y - 1, it.point),
                                    Point2FKey(it.x + 1, it.y, it.point),
                                    Point2FKey(it.x + 1, it.y + 1, it.point))
                        })
                        val allPoints = LinkedHashSet(startPoints)
                        val bounds = polygon.bounds
                        val minX = bounds.min.x
                        val maxX = bounds.max.x
                        val minY = bounds.min.y
                        val maxY = bounds.max.y
                        val dx = maxX - minX
                        val dy = maxY - minY
                        var tries = 0
                        val minDist = 1.0f / 700
                        val pointWidth = Math.max(dx, dy)
                        for (point in generateSemiUniformPoints(Math.round(pointWidth / minDist), pointWidth, random)) {
                            val newPoint = Point2FKey(Point2F(point.x + minX, point.y + minY), 1400)
                            if (allPoints.add(newPoint)) {
                                if (!polygon.isWithin(newPoint.point)) {
                                    allPoints.remove(newPoint)
                                }
                            }
                            tries++
                        }
                        allPoints.removeAll(startPoints)
                        fillPoints.addAll(allPoints.map { it.point })

                        val meshPoints = ArrayList<Point2F>()
                        meshPoints.addAll((edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) } + allPoints.map { it.point }).map { Point2FKey(it) }.toSet().map { it.point })

                        val cellGraph = buildGraph(1.0f, meshPoints)
                        val lines = LinkedHashSet<LineSegment2FKey>()
                        cellGraph.triangles.forEach {
                            val a = it.a.point
                            val b = it.b.point
                            val c = it.c.point
                            val ab = LineSegment2F(a, b)
                            val bc = LineSegment2F(b, c)
                            val ca = LineSegment2F(c, a)
                            lines.add(LineSegment2FKey(ab, 512))
                            lines.add(LineSegment2FKey(bc, 512))
                            lines.add(LineSegment2FKey(ca, 512))
                        }

                        val fixedLines = LinkedHashSet(edgeSkeleton.map { LineSegment2FKey(LineSegment2F(it.a, it.b), 512) } + riverSkeleton.map { LineSegment2FKey(LineSegment2F(it.a, it.b), 512) })
                        lines.removeAll(fixedLines)
                        for (line in ArrayList(lines)) {
                            val mid = line.line.interpolate(0.5f)
                            if (!polygon.isWithin(mid)) {
                                lines.remove(line)
                                continue
                            }
                            for (fixedLine in fixedLines) {
                                if (fixedLine.a != line.a && fixedLine.a != line.b && fixedLine.b != line.a && fixedLine.b != line.b && fixedLine.line.intersects(line.line)) {
                                    lines.remove(line)
                                    break
                                }
                            }
                        }
                        val internalLines = lines.map { it.line }

                        draw(outputWidth, "test-new-${String.format("%05d", test)}-graph$i", Color.WHITE) {
                            graphics.color = Color.BLACK
                            graphics.stroke = BasicStroke(3.0f)
                            internalLines.forEach {
                                drawEdge(it.a, it.b)
                                drawPoint(it.a, 3)
                                drawPoint(it.b, 3)
                            }

                            graphics.color = Color.RED
                            graphics.stroke = BasicStroke(1.0f)
                            fixedLines.forEach {
                                drawEdge(it.line.a, it.line.b)
                                drawPoint(it.line.a, 2)
                                drawPoint(it.line.b, 2)
                            }
                        }
                        println()
                    }
                }




                draw(outputWidth, "test-new-${String.format("%05d", test)}-edgePolys$i") {
                    graphics.color = Color.MAGENTA
                    edgeSkeletons.forEach {
                        it.value.forEach {
                            drawEdge(it.a, it.b)
                            drawPoint(it.a, 2)
                            drawPoint(it.b, 2)
                        }
                    }

                    graphics.color = Color.BLUE
                    riverSkeletons.forEach {
                        it.value.forEach {
                            drawEdge(it.a, it.b)
                            drawPoint(it.a, 2)
                            drawPoint(it.b, 2)
                        }
                    }

                    graphics.color = Color.BLACK
                    fillPoints.forEach {
                        drawPoint(it, 1)
                    }
                }

//                val segmentLength = 1.0f / 4096.0f
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-splines$i") {
//                    graphics.color = Color.BLACK
//                    graphics.stroke = BasicStroke(1.0f)
//                    drawRiverPolyLines(riverSplines, segmentLength, 3, false)
//                }
            }
        }
    }

    private fun getUntouchablePoints(riverGraph: Graph, edgePolygons: HashMap<Int, Polygon2F>, adjacencyPatches: HashMap<Int, HashSet<Int>>, vertex: Vertex, riverSegments: ArrayList<RiverSegment>?): LinkedHashSet<Point2FKey> {
        val untouchables = LinkedHashSet(vertex.cell.border.map { Point2FKey(it) })
        untouchables.add(Point2FKey(vertex.point))
        riverSegments?.forEach {
            untouchables.addAll(it.splices.map { Point2FKey(it.second) })
        }
        (riverGraph.vertices.getAdjacentVertices(vertex.id) + (adjacencyPatches[vertex.id] ?: HashSet())).forEach { id ->
            val adjacent = edgePolygons[id]
            if (adjacent != null) {
                untouchables.addAll(adjacent.points.map { Point2FKey(it) })
            }
        }
        return untouchables
    }

    private fun revisePolygonIfRiverIntersects(graph: Graph, edgePolygons: HashMap<Int, Polygon2F>, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, adjacencyPatches: HashMap<Int, HashSet<Int>>, vertex: Vertex, polygon: Polygon2F, riverPolys: ArrayList<Polygon2F>): Polygon2F {
        val untouchables = getUntouchablePoints(graph, edgePolygons, adjacencyPatches, vertex, cellsToRiverSegments[vertex.id])
        val riverEdges = ArrayList<LineSegment2F>(riverPolys.flatMap { it.edges }.filter { !untouchables.contains(Point2FKey(it.a)) && !untouchables.contains(Point2FKey(it.b)) })
        var adjustedPolygon = polygon
        var adjusted = true
        while (adjusted) {
            adjusted = false
            for (riverEdge in riverEdges) {
                val newPoints = LinkedHashSet<Point2FKey>(adjustedPolygon.points.map { Point2FKey(it) })
                val adjustedEdges = adjustedPolygon.edges
                for (i in 0..adjustedEdges.size - 1) {
                    val edge = adjustedEdges[i]
                    if (riverEdge.intersects(edge)) {
                        val key1 = Point2FKey(edge.a)
                        if (!untouchables.contains(key1)) {
                            newPoints.remove(key1)
                            adjustedPolygon = Polygon2F(newPoints.map { it.point }, true)
                            adjusted = true
                            break
                        }
                        val key2 = Point2FKey(edge.b)
                        if (!untouchables.contains(key2)) {
                            newPoints.remove(key2)
                            adjustedPolygon = Polygon2F(newPoints.map { it.point }, true)
                            adjusted = true
                            break
                        }
                    }
                }
                if (adjusted) {
                    break
                }
                for (i in 0..adjustedPolygon.points.size - 1) {
                    val point = adjustedPolygon.points[i]
                    val key = Point2FKey(point)
                    if (!untouchables.contains(key) && riverEdge.distance2(point) < 0.0000025f) {
                        newPoints.remove(key)
                        adjustedPolygon = Polygon2F(newPoints.map { it.point }, true)
                        adjusted = true
                        break
                    }
                }
                if (adjusted) {
                    break
                }
            }
        }
        return adjustedPolygon
    }

    private fun buildConnectedEdgeSegments(coastMultigon: Multigon2F, coastline: Polygon2F, vertex: Vertex, riverSegments: ArrayList<RiverSegment>?): ArrayList<ArrayList<LineSegment2F>> {
        val cell = vertex.cell
        val splices = ArrayList<Pair<LineSegment2F, Point2F>>()
        riverSegments?.forEach { riverSegment ->
            splices.addAll(riverSegment.splices)
        }
        val polygon = Polygon2F.fromUnsortedEdges(cell.borderEdges, splices)
        val segmentedEdges = ArrayList<LineSegment2F>()
        val intersections = ArrayList<Pair<Point2F, Int>>()
        polygon.edges.forEach { borderEdge ->
            val localIntersections = coastMultigon.intersections(borderEdge)
            intersections.addAll(localIntersections)
            if (localIntersections.isEmpty()) {
                if (coastline.isWithin(borderEdge.interpolate(0.5f))) {
                    segmentedEdges.add(borderEdge)
                }
            } else {
                var currentPoint = borderEdge.a
                localIntersections.sortedBy { borderEdge.a.distance2(it.first) }.forEach {
                    val newLine = LineSegment2F(currentPoint, it.first)
                    if (coastline.isWithin(newLine.interpolate(0.5f))) {
                        segmentedEdges.add(newLine)
                    }
                    currentPoint = it.first
                }
                val newLine = LineSegment2F(currentPoint, borderEdge.b)
                if (coastline.isWithin(newLine.interpolate(0.5f))) {
                    segmentedEdges.add(newLine)
                }
            }
        }
        if (intersections.isNotEmpty()) {
            val min = intersections.map { it.second }.min()!!
            val coastOrderedIntersections = intersections.map {
                val delta = Math.abs(min - it.second)
                val negativeIndex = it.second - coastline.points.size
                val deltaNeg = Math.abs(min - negativeIndex)
                if (deltaNeg < delta) {
                    Pair(it.first, negativeIndex)
                } else {
                    it
                }
            }.sortedBy { it.second }
            var j = 1
            while (j < coastOrderedIntersections.size) {
                val firstSplice = coastOrderedIntersections[j - 1]
                val lastSplice = coastOrderedIntersections[j]
                var firstIndex = firstSplice.second + 1
                var lastIndex = lastSplice.second + 1
                val segment = if (firstIndex < 0) {
                    firstIndex += coastline.points.size
                    if (lastIndex <= 0) {
                        lastIndex += coastline.points.size
                        if (firstIndex > lastIndex) {
                            ArrayList()
                        } else {
                            ArrayList(coastline.points.subList(firstIndex, lastIndex))
                        }
                    } else {
                        ArrayList(coastline.points.subList(firstIndex, coastline.points.size) + coastline.points.subList(0, lastIndex))
                    }
                } else {
                    if (firstIndex > lastIndex) {
                        ArrayList()
                    } else {
                        ArrayList(coastline.points.subList(firstIndex, lastIndex))
                    }
                }
                segment.add(0, firstSplice.first)
                segment.add(lastSplice.first)
                segmentedEdges.addAll(Polygon2F(segment, false).edges)
                j += 2
            }
        }
        return getConnectedEdgeSegments(segmentedEdges)
    }

    private fun getConnectedPolygons(connectedEdges: ArrayList<ArrayList<LineSegment2F>>): ArrayList<Polygon2F> {
        val polygons = ArrayList<Polygon2F>()
        connectedEdges.forEach {
            polygons.add(Polygon2F.fromUnsortedEdges(it))
        }
        return polygons
    }

    private fun containsPoint(polygon: Polygon2F, point: Point2F): Boolean {
        polygon.points.forEach {
            if (point.epsilonEquals(it)) {
                return true
            }
        }
        return false
    }

    private fun cellIntersectsCoast(coastMultigon: Multigon2F, vertex: Vertex): Boolean {
        vertex.cell.borderEdges.forEach { borderEdge ->
            if (coastMultigon.intersects(borderEdge)) {
                return true
            }
        }
        return false
    }

    private fun calculateRiverSegments(graph: Graph, random: Random, rivers: ArrayList<TreeNode<RiverNode>>, flows: FloatArray, test: Int): ArrayList<TreeNode<RiverSegment>> {
        val trees = ArrayList<TreeNode<RiverSegment>>()
        rivers.forEach { outlet ->
            trees.add(calculateRiverSplines(graph, null, outlet,  flows))
        }
        var direction = -1.0f
        trees.forEach { tree ->
            tree.forEach { segment ->
                val profile = segment.profile
                val spline = segment.spline.subdivided(profile.spacing.nextValue(random), 3)
                if (spline.points.size > 2) {
                    for (i in 1..spline.points.size - 2) {
                        val modifyPoint = spline.points[i]
                        direction = -direction
                        val power = random.nextFloat()
                        val deviation = Math.max(0.0f, Math.min(profile.deviation.valueAt(power), 1.0f))
                        val magnitude = profile.strength.valueAt(power)
                        val originalVector = Vector2F(modifyPoint.p, modifyPoint.cp2)
                        val deviantVector = originalVector.getPerpendicular() * (deviation * direction)
                        val newUnitVector = ((originalVector * (1.0f - deviation)) + deviantVector).getUnit()
                        val newVector1 = newUnitVector * (originalVector.length * magnitude)
                        val newVector2 = -newUnitVector * (Vector2F(modifyPoint.p, modifyPoint.cp1).length * magnitude)
                        modifyPoint.cp1 = modifyPoint.p + newVector2
                        modifyPoint.cp2 = modifyPoint.p + newVector1
//                        if (draw) {
//                            val xVals = spline.points.flatMap { listOf(it.p.x, it.cp1.x, it.cp2.x) }
//                            val minX = xVals.min()!!
//                            val maxX = xVals.max()!!
//                            val yVals = spline.points.flatMap { listOf(it.p.y, it.cp1.y, it.cp2.y) }
//                            val minY = yVals.min()!!
//                            val maxY = yVals.max()!!
//                            val deltaX = maxX - minX
//                            val deltaY = maxY - minY
//                            val shift = Vector2F(-minX + 0.001f, -minY + 0.001f)
//                            draw(1024, "testing", 60.0f, shift) {
//                                graphics.stroke = BasicStroke(1.5f)
//                                graphics.color = Color.BLACK
//                                drawSpline(segment.spline, true, true, true, Color.BLUE)
//                                graphics.color = Color.RED
//                                drawSpline(spline, true, true, true, Color.GREEN)
//                            }
//                        }
                    }
                }
                segment.spline = spline
            }
        }
        return trees
    }

    class RiverNodeTransition(val elevation: Float, val point: Point2F, val vector: Vector2F, val edge: LineSegment2F)

    class Junction(
            val vertexId: Int,
            val point: Point2F,
            val flow: Float,
            val vector: Vector2F,
            val elevation: Float,
            var splineElevation: Float? = null,
            var spline: Spline2F? = null,
            var node: TreeNode<RiverNode>? = null,
            val splices: ArrayList<Pair<LineSegment2F, Point2F>> = ArrayList())

    class RiverSegment(
            var spline: Spline2F,
            var flow: Float,
            var elevations: Vector2F,
            var slope: Float,
            var vertexId: Int,
            var profile: RiverProfile,
            val splices: ArrayList<Pair<LineSegment2F, Point2F>> = ArrayList())

    private fun calculateRiverSplines(graph: Graph, transition: RiverNodeTransition? , node: TreeNode<RiverNode>, flows: FloatArray): TreeNode<RiverSegment> {
//        if (node.pointIndex == 569) {
//            println("start this bitch")
//            draw = true
//        }
        val vertex = graph.vertices[node.value.pointIndex]
        val vertexElevation = node.value.elevation
        val cell = vertex.cell
        val drainVector: Vector2F
        val cellEdges = cell.borderEdges
        var drainEdgeIndex = -1
        val drainElevation: Float
        val transitionEdge: LineSegment2F?
        val drainPoint = if (transition == null) {
            if (node.children.isEmpty()) {
                drainVector = Vector2F(0.0f, 0.0f)
            } else {
                drainVector = Vector2F(vertex.point, node.children.first().value.pointLocation).getUnit()
            }
            drainElevation = vertexElevation
            transitionEdge = null
            vertex.point
        } else {
            for (i in 0..cellEdges.size - 1) {
                if (cellEdges[i] == transition.edge) {
                    drainEdgeIndex = i
                }
            }
            drainVector = -transition.vector
            drainElevation = transition.elevation
            transitionEdge = transition.edge
            transition.point
        }
        val entryPoints = ArrayList<Pair<Junction, Int>>()
        var inFlow = 0.0f
        node.children.forEach { child ->
            val childVertex = graph.vertices[child.value.pointIndex]
            val childElevation = child.value.elevation
            val childCell = childVertex.cell
            try {
                val childEdge = cell.sharedEdge(childCell)!!
                inFlow += flows[child.value.pointIndex]
                for (i in 0..cellEdges.size - 1) {
                    val cellEdge = cellEdges[i]
                    if (cellEdge == childEdge) {
                        val edge = cellEdge
                        val entryPoint = edge.interpolate(0.5f)
                        entryPoints.add(Pair(Junction(vertex.id, entryPoint, flows[child.value.pointIndex], calculateEntryVectorFromEdge(edge), (childElevation + vertexElevation) * 0.5f, node = child, splices = arrayListOf(Pair(cellEdge, entryPoint))), i))
                    }
                }
            } catch (e: Exception) {
                draw(4096, "testing") {
                    graphics.color = Color.BLACK
                    cell.borderEdges.forEach { drawEdge(it.a, it.b) }
                    graphics.color = Color.RED
                    childCell.borderEdges.forEach { drawEdge(it.a, it.b) }
                    graphics.color = Color.BLUE
                    drawEdge(vertex.point, childVertex.point)
                    graphics.color = Color.GREEN
                    drawPoint(vertex.point, 3)
                    graphics.color = Color.MAGENTA
                    drawPoint(childVertex.point, 3)
                }
                println()
            }
        }
        val entries = ArrayList(entryPoints.sortedBy { if (it.second < drainEdgeIndex) { it.second + cellEdges.size } else { it.second } }.map { it.first })
        var junctions: TreeNode<Junction> = if (entries.isEmpty()) {
            TreeNode(Junction(vertex.id, vertex.point, 0.0f, Vector2F(vertex.point, drainPoint).getUnit(), vertexElevation))
        } else {
            TreeNode(entries.removeAt(0))
        }
        while (entries.isNotEmpty()) {
            val newEntry = entries.removeAt(0)
            val (junction, spline1, spline2) = connectRiverEntries(vertex.id, drainPoint, drainElevation, junctions.value.point, junctions.value.elevation, junctions.value.flow, junctions.value.vector, newEntry.point, newEntry.elevation, newEntry.flow, newEntry.vector)
//            if (draw) {
//                draw(4096, "testing") {
//                    graphics.color = Color.BLACK
//                    cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
//                    graphics.color = Color.RED
//                    drawPoint(vertex.point, 3)
//                    graphics.color = Color.BLUE
//                    localSplines.forEach { drawSpline(it, false) }
//                    graphics.color = Color.MAGENTA
//                    entryPoints.forEach { drawPoint(it.first.first, 3) }
//                    graphics.color = Color.CYAN
//                    drawPoint(drainPoint, 3)
//                    graphics.color = Color.ORANGE
//                    drawSpline(junctionSplines.first)
//                    graphics.color = Color.PINK
//                    drawSpline(junctionSplines.second)
//                    graphics.color = Color.GREEN
//                    drawPoint(junction.first, 3)
//                }
//            }
            val newJunctionNode = TreeNode(junction)
            junctions.parent = newJunctionNode
            newJunctionNode.children.add(junctions)
            junctions.value.spline = spline1
            junctions.value.splineElevation = junction.elevation
            val entryNode = TreeNode(newEntry)
            entryNode.parent = newJunctionNode
            newEntry.spline = spline2
            newEntry.splineElevation = junction.elevation
            newJunctionNode.children.add(entryNode)
            junctions = newJunctionNode
        }
        val rootNode = if (junctions.value.point == vertex.point && drainPoint == vertex.point) {
            val spline = Spline2F(mutableListOf(SplinePoint2F(vertex.point)), false)
            val elevations = Vector2F(vertexElevation, vertexElevation)
            val flow = flows[vertex.id]
            val slope = calculateSlope(spline, elevations)
            TreeNode(RiverSegment(spline, flow, elevations, slope, vertex.id, calculateRiverProfile(flow, slope, elevations)))
        } else {
            junctions.value.spline = connectEntryToDrain(junctions.value.point, junctions.value.vector, drainPoint, drainVector)
            junctions.value.splineElevation = drainElevation
            if (transitionEdge != null) {
                junctions.value.splices.add(Pair(transitionEdge, drainPoint))
            }
            toSplineTree(graph, junctions, flows)!!
        }
//        if (draw) {
//            draw(4096, "testing") {
//                graphics.color = Color.BLACK
//                cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
//                graphics.color = Color.RED
//                drawPoint(vertex.point, 3)
//                graphics.color = Color.BLUE
//                localSplines.forEach { drawSpline(it, false) }
//                graphics.color = Color.MAGENTA
//                entryPoints.forEach { drawPoint(it.first.first, 3) }
//                graphics.color = Color.CYAN
//                drawPoint(drainPoint, 3)
//                graphics.color = Color.GREEN
//                junctions.forEach {
//                    drawPoint(it, 3)
//                }
//            }
//        }
        return rootNode
    }

    private fun toSplineTree(graph: Graph, junctions: TreeNode<Junction>, flows: FloatArray): TreeNode<RiverSegment>? {
        val spline = junctions.value.spline ?: return null
        val elevations = Vector2F(junctions.value.splineElevation!!, junctions.value.elevation)
        val slope = calculateSlope(spline, elevations)
        val treeNode = TreeNode(RiverSegment(spline, junctions.value.flow, elevations, slope, junctions.value.vertexId, calculateRiverProfile(junctions.value.flow, slope, elevations), junctions.value.splices))
        val riverNode = junctions.value.node
        if (riverNode != null) {
            treeNode.children.add(calculateRiverSplines(graph, RiverNodeTransition(junctions.value.elevation, junctions.value.point, junctions.value.vector, junctions.value.splices.first().first), riverNode, flows))
        }
        junctions.children.forEach {
            val subTree = toSplineTree(graph, it, flows)
            if (subTree != null) {
                treeNode.children.add(subTree)
            }
        }
        return treeNode
    }

    private fun calculateSlope(spline: Spline2F, elevations: Vector2F): Float {
        val points = spline.points
        if (points.size < 2) {
            if (elevations.a == elevations.b) {
                return 0.0f
            } else {
                return 1.0f
            }
        } else {
            val start = points.first().p
            val end = points.last().p
            val dx = start.distance(end)
            if (dx == 0.0f) {
                if (elevations.a == elevations.b) {
                    return 0.0f
                } else {
                    return 1.0f
                }
            }
            val dy = Math.abs(elevations.b - elevations.a)
            return dy / (dx + dy)
        }
    }

    private fun  connectEntryToDrain(junction: Point2F, junctionVector: Vector2F, drain: Point2F, drainVector: Vector2F): Spline2F {
        val dist = junction.distance(drain) * 0.3666666667f
        val cp1 = junction + (junctionVector * dist)
        val cp2 = drain + (drainVector * dist)
        val sp1 = SplinePoint2F(junction, junction, cp1)
        val sp2 = SplinePoint2F(drain, cp2, drain)
        val ret = Spline2F(mutableListOf(sp1, sp2), false)
//        if (draw) {
//            draw(4096, "testing") {
//                graphics.color = Color.BLACK
//                drawPoint(drain, 3)
//                graphics.color = Color.RED
//                drawPoint(junction, 3)
//                graphics.color = Color.BLUE
//                drawEdge(junction, Point2F(junction.x + junctionVector.a, junction.y + junctionVector.b))
//                graphics.color = Color.MAGENTA
//                drawEdge(drain, Point2F(drain.x + drainVector.a, drain.y + drainVector.b))
//                graphics.color = Color.GREEN
//                drawSpline(ret)
//            }
//        }
        return ret
    }

    private fun calculateEntryVectorFromEdge(edge: LineSegment2F): Vector2F {
        return -edge.toVector().getPerpendicular().getUnit()
    }

    private fun connectRiverEntries(vertexId: Int, drainPoint: Point2F, drainElevation: Float, entry1: Point2F, elevation1: Float, flow1: Float, entryVector1: Vector2F, entry2: Point2F, elevation2: Float, flow2: Float, entryVector2: Vector2F): Triple<Junction, Spline2F, Spline2F> {
        val nanSafeFlow1 = if (flow1 < 0.00000001f) { 0.00000001f } else { flow1 }
        val nanSafeFlow2 = if (flow1 < 0.00000001f) { 0.00000001f } else { flow2 }
        val totalDist = (drainPoint.distance(entry1) + drainPoint.distance(entry2)) * 0.3f
        val totalFlow = nanSafeFlow1 + nanSafeFlow2
        val junctionOffset = nanSafeFlow1 / totalFlow
        val dist1 = junctionOffset * totalDist
        val dist2 = (nanSafeFlow2 / totalFlow) * totalDist
        val cp1 = entry1 + (entryVector1 * dist1)
        val cp2 = entry2 + (entryVector2 * dist2)
        val sp1 = SplinePoint2F(entry1, entry1, cp1)
        val sp2 = SplinePoint2F(entry2, cp2, entry2)
        val sp3 = sp1.interpolate(sp2, 1.0f - junctionOffset)
//        if (draw) {
//            draw(4096, "testing") {
//                graphics.color = Color.BLACK
//                drawPoint(drainPoint, 3)
//                graphics.color = Color.RED
//                drawPoint(entry1, 3)
//                graphics.color = Color.BLUE
//                drawPoint(entry2, 3)
//                graphics.color = Color.MAGENTA
//                drawEdge(entry1, Point2F(entry1.x + entryVector1.a, entry1.y + entryVector1.b))
//                graphics.color = Color.CYAN
//                drawEdge(entry2, Point2F(entry2.x + entryVector2.a, entry2.y + entryVector2.b))
//                graphics.color = Color.ORANGE
//                drawPoint(sp3.p, 3)
//                graphics.color = Color.GREEN
//                drawSpline(Spline(mutableListOf(sp1, sp2), false))
//            }
//        }
        sp1.cp2 = entry1 + (entryVector1 * (dist1 * 0.3f))
        sp2.cp1 = entry2
        sp2.cp2 = entry2 + (entryVector2 * (dist2 * 0.3f))
        val junction = sp3.p

        val avgEntryElevation = (elevation1 + elevation2) * 0.5f
        val dist1ToJ = entry1.distance(junction)
        val dist2ToJ = entry2.distance(junction)
        val avgDistToJ = (dist1ToJ + dist2ToJ) * 0.5f
        val distDToJ = drainPoint.distance(junction)
        val totalRun = avgDistToJ + distDToJ
        val jOffset = distDToJ / totalRun
        val elevationDiff = avgEntryElevation - drainElevation
        val desiredJunctionElevation = drainElevation + (elevationDiff * jOffset)
        val junctionElevation = if (desiredJunctionElevation >= elevation1 || desiredJunctionElevation >= elevation2) {
            drainElevation + ((Math.min(elevation1, elevation2) - drainElevation) * jOffset)
        } else {
            desiredJunctionElevation
        }
        val normalizedFlowVector = (Vector2F(entry1, drainPoint) + Vector2F(entry2, drainPoint) * (totalDist * 0.5f)).getUnit()
        val outputVector = ((entryVector1 * dist1.toFloat()) + (entryVector2 * dist2.toFloat()) + normalizedFlowVector).getUnit()
        sp3.cp1 = junction + (-outputVector * (totalDist * 0.15f))
        val ret = Triple(Junction(vertexId, junction, totalFlow, outputVector, junctionElevation), Spline2F(mutableListOf(sp1, sp3), false), Spline2F(mutableListOf(sp2, sp3), false))
//        if (draw) {
//            draw(4096, "testing") {
//                graphics.color = Color.BLACK
//                drawPoint(drainPoint, 3)
//                graphics.color = Color.RED
//                drawPoint(entry1, 3)
//                graphics.color = Color.BLUE
//                drawPoint(entry2, 3)
//                graphics.color = Color.MAGENTA
//                drawEdge(entry1, Point2F(entry1.x + entryVector1.x, entry1.y + entryVector1.y))
//                graphics.color = Color.CYAN
//                drawEdge(entry2, Point2F(entry2.x + entryVector2.x, entry2.y + entryVector2.y))
//                graphics.color = Color.ORANGE
//                drawPoint(junction, 3)
//                graphics.color = Color.PINK
//                drawEdge(junction, Point2F(junction.x + junctionOutput.x, junction.y + junctionOutput.y))
//                graphics.color = Color.GREEN
//                drawSpline(ret.second.first)
//                drawSpline(ret.second.second)
//            }
//        }
        return ret
    }

    private fun calculateRiverFlows(vertices: Vertices, coastline: Polygon2F, rivers: ArrayList<TreeNode<RiverNode>>, simulationSizeM2: Float, landPercent: Float): FloatArray {
        val standardArea = (simulationSizeM2 * landPercent) / vertices.size.toFloat()
        val flows = FloatArray(vertices.size)
        rivers.forEach {
            calculateRiverFlow(vertices, coastline, it, simulationSizeM2, standardArea, flows)
        }
        return flows
    }

    private fun calculateRiverFlow(vertices: Vertices, coastline: Polygon2F, river: TreeNode<RiverNode>, simulationSizeM2: Float, standardArea: Float, flows: FloatArray): Double {
        var shedArea = 0.0
        river.children.forEach {
            shedArea += calculateRiverFlow(vertices, coastline, it, simulationSizeM2, standardArea, flows)
        }
        val cell = vertices[river.value.pointIndex].cell
        if (cell.area != 0.0f && !cell.isBorder && !Polygon2F(cell.border, true).doesEdgeIntersect(coastline).first) {
            shedArea += cell.area * simulationSizeM2
        } else {
            shedArea += standardArea
        }
        flows[river.value.pointIndex] = (0.42 * Math.pow(shedArea, 0.69)).toFloat()
        return shedArea
    }

    class RiverProfile(val spacing: Range2F, val strength: Range2F, val deviation: Range2F)

    private fun calculateRiverProfile(flow: Float, slope: Float, elevations: Vector2F): RiverProfile {
        val elevation = (elevations.a + elevations.b) * 0.5f
        val highElevation = elevation > 0.0085f
        val highFlow = flow > 14500.0f
        val highSlope = slope > 0.124f
        if (highElevation) {
            if (highSlope) {
                if (highFlow) {
                    return RiverProfile(Range2F(0.0035f, 0.0045f), Range2F(1.0f, 1.2f), Range2F(0.1f, 0.2f))
                } else {
                    return RiverProfile(Range2F(0.003f, 0.0035f), Range2F(1.0f, 1.4f), Range2F(0.1f, 0.45f))
                }
            } else {
                if (highFlow) {
                    return RiverProfile(Range2F(0.0035f, 0.0045f), Range2F(1.0f, 1.2f), Range2F(0.1f, 0.2f))
                } else {
                    return RiverProfile(Range2F(0.0025f, 0.0035f), Range2F(1.0f, 2.5f), Range2F(0.1f, 0.8f))
                }
            }
        } else {
            if (highSlope) {
                if (highFlow) {
                    return RiverProfile(Range2F(0.0045f, 0.005f), Range2F(1.0f, 1.2f), Range2F(0.1f, 0.2f))
                } else {
                    return RiverProfile(Range2F(0.004f, 0.0045f), Range2F(1.0f, 1.3f), Range2F(0.1f, 0.3f))
                }
            } else {
                if (highFlow) {
                    return RiverProfile(Range2F(0.0055f, 0.0065f), Range2F(1.0f, 1.1f), Range2F(0.1f, 0.3f))
                } else {
                    return RiverProfile(Range2F(0.004f, 0.005f), Range2F(1.0f, 2.5f), Range2F(0.1f, 0.95f))
                }
            }
        }
    }
}
