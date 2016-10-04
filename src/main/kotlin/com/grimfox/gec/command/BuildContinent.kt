package com.grimfox.gec.command

import com.grimfox.gec.Main
import com.grimfox.gec.model.ArrayListMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Graph.Vertex
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.Range2F
import com.grimfox.gec.model.TreeNode
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.model.geometry.LineSegment2F.Companion.getConnectedEdgeSegments
import com.grimfox.gec.util.Coastline.applyMask
import com.grimfox.gec.util.Coastline.getBorders
import com.grimfox.gec.util.Coastline.refineCoastline
import com.grimfox.gec.util.Regions.buildRegions
import com.grimfox.gec.util.Rivers.RiverNode
import com.grimfox.gec.util.Rivers.buildRiverGraph
import com.grimfox.gec.util.Rivers.buildRivers
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils.generatePoints
import com.grimfox.gec.util.drawing.*
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Math.*
import java.util.*
import javax.imageio.ImageIO

@Command(name = "build-continent", description = "Builds a continent.")
class BuildContinent() : Runnable {

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var randomSeed: Long = System.currentTimeMillis()

    @Option(name = arrayOf("-s", "--strides"), description = "The number of points in the stride of each iteration.", required = false)
    var strides: ArrayList<Int> = ArrayList()

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

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
            val outputWidth = 4096
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
            val borders = getBorders(graph, regionMask)
            rivers.forEachIndexed { i, body ->
                val coastline = body.first
                val riverSet = body.second
                val border = borders[i]
                val riverGraph = buildRiverGraph(riverSet)
                val riverFlows = calculateRiverFlows(riverGraph.vertices, coastline, riverSet, 1600000000.0f, 0.39f)
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-rivers$i", Color(160, 200, 255)) {
//                    drawRivers(graph, regionMask, riverSet, listOf(coastSpline), border)
//                }
                draw(outputWidth, "test-new-${String.format("%05d", test)}-graph$i", Color.WHITE) {
                    drawGraph(riverGraph)
                }
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
                draw(outputWidth, "test-new-${String.format("%05d", test)}-ids$i") {
                    graphics.color = Color.BLACK
                    drawVertexIds(riverGraph)
                }
                val reverseRiverMap = HashMap<Int, RiverNode>()
                riverSet.forEach {
                    it.forEach { node ->
                        reverseRiverMap[node.pointIndex] = node
                    }
                }
                val crestPoints = PointSet2F(0.001f)
                val fixedCrestElevations = HashMap<Int, Float>(riverGraph.triangles.size)
                val crestElevations = HashMap<Int, Float>(riverGraph.triangles.size)
                riverGraph.triangles.forEach { crest ->
                    val riverA = reverseRiverMap[crest.a.id]!!
                    val riverB = reverseRiverMap[crest.b.id]!!
                    val riverC = reverseRiverMap[crest.c.id]!!
                    val elevationStart = max(riverA.elevation, max(riverB.elevation, riverC.elevation))
                    val terrainSlopeAvg = (riverA.maxTerrainSlope + riverB.maxTerrainSlope + riverC.maxTerrainSlope) / 3.0f
                    val radius = crest.center.distance(crest.a.point)
                    crestPoints.add(crest.center)
                    val crestPointId = crestPoints[crest.center]
                    val currentCrestElevation = fixedCrestElevations[crestPointId] ?: 0.0f
                    fixedCrestElevations[crestPointId] = max(currentCrestElevation, elevationStart + (radius * terrainSlopeAvg))
                }
                riverGraph.triangles.forEach { crest ->
                    crestElevations[crest.id] = fixedCrestElevations[crestPoints[crest.center]]!!
                }

                val riverSplines = calculateRiverSegments(riverGraph, random, riverSet, riverFlows)
                val cellsToRiverSegments = HashMap<Int, ArrayList<RiverSegment>>()
                riverSplines.forEach {
                    it.forEach {
                        cellsToRiverSegments.getOrPut(it.vertexId, { ArrayList() }).add(it)
                    }
                }

                val heightMap = ArrayListMatrix(4096) { -Float.MAX_VALUE }

                val globalVertexSet = PointSet2F(0.0001f)

                val renderedCells = renderCoastalCells(riverGraph, cellsToRiverSegments, crestElevations, coastline, reverseRiverMap, heightMap, globalVertexSet)
                renderInlandCells(riverGraph, cellsToRiverSegments, crestElevations, renderedCells, heightMap, globalVertexSet)

                writeHeightData("test-new-${String.format("%05d", test)}-heightMap$i", heightMap)

                val segmentLength = 1.0f / 4096.0f
                draw(outputWidth, "test-new-${String.format("%05d", test)}-splines$i") {
                    graphics.color = Color.BLACK
                    graphics.stroke = BasicStroke(1.0f)
                    drawRiverPolyLines(riverSplines, segmentLength, 3, false)
                }
                println()
            }
        }
    }

    private fun renderInlandCells(riverGraph: Graph, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, crestElevations: HashMap<Int, Float>, alreadyRendered: Set<Int>, heightMap: ArrayListMatrix<Float>, globalVertexSet: PointSet2F) {
        val cellPolygons = buildBasicInteriorCellPolygons(riverGraph, cellsToRiverSegments, alreadyRendered)
        val riverPolygons = buildInlandRiverPolygons(cellPolygons, cellsToRiverSegments)
        val (edgeSkeletons, riverSkeletons) = buildInlandCellSkeletons(riverGraph, crestElevations, cellPolygons, riverPolygons)
        drawInlandCellPolygons(edgeSkeletons, riverSkeletons, cellPolygons, heightMap, globalVertexSet)
    }

    private fun buildInlandRiverPolygons(cellPolygons: HashMap<Int, Polygon2F>, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>): HashMap<Int, ArrayList<Polygon2F>> {
        val riverPolygons = HashMap<Int, ArrayList<Polygon2F>>()
        cellPolygons.keys.forEach {
            riverPolygons[it] = buildCellRiverPolygons(cellsToRiverSegments, it)
        }
        return riverPolygons
    }

    private fun buildInlandCellSkeletons(riverGraph: Graph,
                                         crestElevations: HashMap<Int, Float>,
                                         cellPolygons: HashMap<Int, Polygon2F>,
                                         riverPolygons: HashMap<Int, ArrayList<Polygon2F>>): Pair<HashMap<Int, ArrayList<LineSegment3F>>, HashMap<Int, ArrayList<LineSegment3F>>> {
        val edgeSkeletons = HashMap<Int, ArrayList<LineSegment3F>>()
        val riverSkeletons = HashMap<Int, ArrayList<LineSegment3F>>()
        cellPolygons.forEach {
            val localEdgeSkeleton = ArrayList<LineSegment3F>()
            val localRiverSkeleton = ArrayList<LineSegment3F>()
            val id = it.key
            val polygon = it.value
            val polyPoints = PointSet2F(polygon.points)
            val pointsWithHeights = PointSet2F()
            val localRiverPolys = riverPolygons[id]
            if (localRiverPolys != null) {
                localRiverSkeleton.addAll(localRiverPolys.flatMap { it.edges }.map { LineSegment3F(it.a as Point3F, it.b as Point3F) })
                pointsWithHeights.addAll(localRiverSkeleton.flatMap { listOf(it.a, it.b) })
            }
            riverGraph.vertices[id].adjacentTriangles.forEach {
                val height = crestElevations[it.id]
                if (height != null) {
                    val point = Point3F(it.center.x, it.center.y, height)
                    if (polyPoints.contains(point)) {
                        pointsWithHeights.add(point)
                    }
                }
            }
            polygon.edges.forEach {
                val a2d = it.a
                val b2d = it.b
                val a2dId = pointsWithHeights[a2d]
                val a3d = if (a2dId > -1) pointsWithHeights[a2dId] as Point3F? ?: Point3F(a2d.x, a2d.y, 0.0f) else Point3F(a2d.x, a2d.y, 0.0f)
                val b2dId = pointsWithHeights[b2d]
                val b3d = if (b2dId > -1) pointsWithHeights[b2dId] as Point3F? ?: Point3F(b2d.x, b2d.y, 0.0f) else Point3F(b2d.x, b2d.y, 0.0f)
                localEdgeSkeleton.addAll(LineSegment3F(a3d, b3d).subdivided2d(1.0f / 512))
            }
            edgeSkeletons[id] = localEdgeSkeleton
            riverSkeletons[id] = localRiverSkeleton
        }
        return Pair(edgeSkeletons, riverSkeletons)
    }

    private fun renderCoastalCells(riverGraph: Graph, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, crestElevations: HashMap<Int, Float>, coastline: Polygon2F, reverseRiverMap: HashMap<Int, RiverNode>, heightMap: ArrayListMatrix<Float>, globalVertexSet: PointSet2F): Set<Int> {
        val coastMultigon = Multigon2F(coastline, 20)
        val coastCells = ArrayList<Int>()
        riverGraph.vertices.forEach { vertex ->
            if (cellIntersectsCoast(coastMultigon, vertex)) {
                coastCells.add(vertex.id)
            }
        }
        val riverPoints = PointSet2F(riverGraph.vertices.map { it.point })
        riverPoints.retainAll(coastline.points)
        val (coastalPolygons, unconnectedPolygons) = buildBasicCoastalCellPolygons(riverGraph, cellsToRiverSegments, riverPoints, coastMultigon, coastline, coastCells)
        val (riverPolygons, adjacencyPatches, inclusionPatches) = reviseCoastalCellPolygonsForRiverAndCoast(riverGraph, cellsToRiverSegments, coastalPolygons, unconnectedPolygons)
        val smoothedCoastalCellPolys = smoothCoastline(riverGraph, cellsToRiverSegments, coastalPolygons, adjacencyPatches)
        val (edgeSkeletons, riverSkeletons) = buildCoastalCellSkeletons(riverGraph, cellsToRiverSegments, crestElevations, riverPolygons, coastalPolygons, adjacencyPatches, inclusionPatches, smoothedCoastalCellPolys)
        drawCoastalCellPolygons(reverseRiverMap, edgeSkeletons, riverSkeletons, smoothedCoastalCellPolys, heightMap, globalVertexSet)
        return coastCells.toSet()
    }

    private fun drawCoastalCellPolygons(reverseRiverMap: HashMap<Int, RiverNode>,
                                        edgeSkeletons: HashMap<Int, ArrayList<LineSegment3F>>,
                                        riverSkeletons: HashMap<Int, ArrayList<LineSegment3F>>,
                                        smoothedCoastalCellPolys: HashMap<Int, Polygon2F>, heightMap: ArrayListMatrix<Float>,
                                        globalVertexSet: PointSet2F) {
        smoothedCoastalCellPolys.forEach {
            val id = it.key
            val polygon = it.value
            val edgeSkeleton = edgeSkeletons[id]
            val riverSkeleton = riverSkeletons[id] ?: arrayListOf()
            val maxTerrainSlope = reverseRiverMap[id]?.maxTerrainSlope ?: 0.0f
            if (edgeSkeleton != null) {
                val (vertices, triangles) = buildMesh(id, polygon, edgeSkeleton, riverSkeleton)
                spliceZeroHeightTriangles(vertices, triangles, maxTerrainSlope)
                renderTriangles(vertices, triangles, heightMap, globalVertexSet)
            }
        }
    }

    private fun drawInlandCellPolygons(edgeSkeletons: HashMap<Int, ArrayList<LineSegment3F>>,
                                       riverSkeletons: HashMap<Int, ArrayList<LineSegment3F>>,
                                       polygons: HashMap<Int, Polygon2F>,
                                       heightMap: ArrayListMatrix<Float>,
                                       globalVertexSet: PointSet2F) {
        polygons.forEach {
            val edgeSkeleton = edgeSkeletons[it.key]
            val riverSkeleton = riverSkeletons[it.key] ?: arrayListOf()
            if (edgeSkeleton != null) {
                val (vertices, triangles) = buildMesh(it.key, it.value, edgeSkeleton, riverSkeleton)
                renderTriangles(vertices, triangles, heightMap, globalVertexSet)
                if (it.key == 848) {
                    draw(4096, "testing") {
                        graphics.color = Color.BLACK
                        graphics.stroke = BasicStroke(1.0f)
                        triangles.forEach {
                            val tri = it.toList()
                            val a = vertices[tri[0]]
                            val b = vertices[tri[1]]
                            val c = vertices[tri[2]]
                            drawEdge(a, b)
                            drawEdge(b, c)
                            drawEdge(c, a)
                        }
                    }
                    println()
                }
            }
        }
    }

    private fun renderTriangles(vertices: ArrayList<Point2F>, triangles: LinkedHashSet<Set<Int>>, heightMap: ArrayListMatrix<Float>, globalVertexSet: PointSet2F) {
        globalVertexSet.addAll(vertices)
        triangles.forEach {
            val tri = it.toList()
            val a = globalVertexSet[globalVertexSet[vertices[tri[0]]]] as Point3F
            val b = globalVertexSet[globalVertexSet[vertices[tri[1]]]] as Point3F
            val c = globalVertexSet[globalVertexSet[vertices[tri[2]]]] as Point3F
            val cross = (b - a).cross(c - a)
            if (cross.c < 0) {
                drawTriangle(a, b, c, heightMap)
            } else {
                drawTriangle(a, c, b, heightMap)
            }
        }
    }

    private fun spliceZeroHeightTriangles(vertices: ArrayList<Point2F>, triangles: LinkedHashSet<Set<Int>>, maxTerrainSlope: Float) {
        val splices = LinkedHashMap<Pair<Int, Int>, Point3F>()
        triangles.forEach { triangle ->
            val tri = triangle.toList()
            val aId = tri[0]
            val bId = tri[1]
            val cId = tri[2]
            val a = vertices[aId] as Point3F
            val b = vertices[bId] as Point3F
            val c = vertices[cId] as Point3F
            if (a.z == 0.0f && b.z == 0.0f) {
                triangles.forEach { other ->
                    addHeightPointIfNeeded(splices, triangle, other, a, b, c, aId, bId, maxTerrainSlope)
                }
            }
            if (b.z == 0.0f && c.z == 0.0f) {
                triangles.forEach { other ->
                    addHeightPointIfNeeded(splices, triangle, other, b, c, a, bId, cId, maxTerrainSlope)
                }

            }
            if (c.z == 0.0f && a.z == 0.0f) {
                triangles.forEach { other ->
                    addHeightPointIfNeeded(splices, triangle, other, c, a, b, cId, aId, maxTerrainSlope)
                }
            }
        }
        splices.forEach { edge, point ->
            val trianglesToModify = ArrayList<Set<Int>>(2)
            val pointIndex = vertices.size
            vertices.add(point)
            triangles.forEach {
                if (it.containsAll(edge.toList())) {
                    trianglesToModify.add(it)
                }
            }
            triangles.removeAll(trianglesToModify)
            trianglesToModify.forEach {
                val first = HashSet(it)
                val second = HashSet(it)
                first.remove(edge.first)
                second.remove(edge.second)
                first.add(pointIndex)
                second.add(pointIndex)
                triangles.add(first)
                triangles.add(second)
            }
        }
    }

    private fun buildMesh(id: Int, polygon: Polygon2F, edgeSkeleton: ArrayList<LineSegment3F>, riverSkeleton: ArrayList<LineSegment3F>): Pair<ArrayList<Point2F>, LinkedHashSet<Set<Int>>> {
        val meshPoints = PointSet2F()
        meshPoints.addAll(edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) })
        val cellGraph = buildGraph(1.0f, ArrayList(meshPoints))
        if (id == 848) {
            draw(4096, "testing-graph") {
                drawGraph(cellGraph)
            }
            println()
        }

        val edges = LinkedHashSet<Pair<Int, Int>>()
        fun edge(a: Int, b: Int) = edges.add(Pair(min(a, b), max(a, b)))
        cellGraph.triangles.forEach {
            val a = meshPoints[it.a.point]
            val b = meshPoints[it.b.point]
            val c = meshPoints[it.c.point]
            edge(a, b)
            edge(b, c)
            edge(c, a)
        }
        val fixedEdges = LinkedHashSet<Pair<Int, Int>>()
        fun fixedEdge(a: Int, b: Int) = fixedEdges.add(Pair(min(a, b), max(a, b)))
        val borderEdges = LinkedHashSet<Pair<Int, Int>>()
        edgeSkeleton.forEach { fixedEdge(meshPoints[it.a], meshPoints[it.b]) }
        borderEdges.addAll(fixedEdges)
        riverSkeleton.forEach { fixedEdge(meshPoints[it.a], meshPoints[it.b]) }
        if (id == 848) {
            draw(4096, "testing0") {
                graphics.color = Color.BLACK
                graphics.stroke = BasicStroke(1.0f)
                fixedEdges.forEach {
                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                }
            }
            println()
        }
        if (id == 848) {
            draw(4096, "testing1") {
                graphics.color = Color.BLACK
                graphics.stroke = BasicStroke(1.0f)
                edges.forEach {
                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                }
            }
            println()
        }
        edges.removeAll(fixedEdges)
        var badEdge: Pair<Int, Int>? = Pair(-1, -1)
        while (badEdge != null) {
            badEdge = null
            for (first in edges) {
                for (second in edges) {
                    if (first != second && LineSegment2F(meshPoints[first.first]!!, meshPoints[first.second]!!).intersects(LineSegment2F(meshPoints[second.first]!!, meshPoints[second.second]!!))) {
                        badEdge = first
                        break
                    }
                }
                if (badEdge != null) {
                    break
                }
            }
            edges.remove(badEdge)
        }
        if (id == 848) {
            draw(4096, "testing2") {
                graphics.color = Color.BLACK
                graphics.stroke = BasicStroke(1.0f)
                edges.forEach {
                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                }
            }
            println()
        }
        fun Pair<Int, Int>.toLine() = LineSegment2F(meshPoints[first]!!, meshPoints[second]!!)
        for (edge in ArrayList(edges)) {
            val line = edge.toLine()
            val mid = line.interpolate(0.5f)
            if (!polygon.isWithin(mid) || meshPoints.contains(mid)) {
                edges.remove(edge)
//                if (id == 848) {
//                    draw(4096, "testing4") {
//                        graphics.color = Color.BLACK
//                        graphics.stroke = BasicStroke(1.0f)
//                        edges.forEach {
//                            drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                        }
//                    }
//                    println()
//                }
                continue
            }
            for (fixedEdge in borderEdges) {
                if (fixedEdge.toLine().collinearOverlappingEpsilon(line)) {
                    edges.remove(edge)
//                    if (id == 848) {
//                        draw(4096, "testing4") {
//                            graphics.color = Color.BLACK
//                            graphics.stroke = BasicStroke(1.0f)
//                            edges.forEach {
//                                drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                            }
//                        }
//                        println()
//                    }
                    break
                }
            }
        }
        if (id == 848) {
            draw(4096, "testing3") {
                graphics.color = Color.BLACK
                graphics.stroke = BasicStroke(1.0f)
                edges.forEach {
                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                }
            }
            println()
        }
        val edgeList = ArrayList(edges + fixedEdges)
        val vertexList = ArrayList(meshPoints)
        val vertexToVertexMap = ArrayList<ArrayList<Int>>()
        for (v in 0..vertexList.size - 1) {
            vertexToVertexMap.add(ArrayList(5))
        }
        edgeList.forEach { edge ->
            vertexToVertexMap[edge.first].add(edge.second)
            vertexToVertexMap[edge.second].add(edge.first)
        }
        val triangleIndices = LinkedHashSet<Set<Int>>()
        val edgeConnections = HashMap<Pair<Int, Int>, Int>()
        for (a in 0..vertexList.size - 1) {
            val adjacents = vertexToVertexMap[a]
            for (p in 0..adjacents.size - 2) {
                val b = adjacents[p]
                if (b != a) {
                    val secondAdjacents = vertexToVertexMap[b]
                    for (q in p + 1..adjacents.size - 1) {
                        val c = adjacents[q]
                        if (c != a && c != b && secondAdjacents.contains(c)) {
                            triangleIndices.add(setOf(a, b, c))
                            val ab = Pair(min(a, b), max(a, b))
                            val bc = Pair(min(b, c), max(b, c))
                            val ca = Pair(min(c, a), max(c, a))
                            edgeConnections[ab] = (edgeConnections[ab] ?: 0) + 1
                            edgeConnections[bc] = (edgeConnections[bc] ?: 0) + 1
                            edgeConnections[ca] = (edgeConnections[ca] ?: 0) + 1
                        }
                    }
                }
            }
        }
        val holes = LinkedHashSet(edgeConnections.filter { it.value == 3 }.map { it.key })
        holes.removeAll(borderEdges)
        if (id == 848) {
            draw(4096, "testing5") {
                graphics.color = Color.BLACK
                graphics.stroke = BasicStroke(1.0f)
                edgeList.forEach {
                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                }
                graphics.color = Color.RED
                holes.forEach {
                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                }
            }
            println()
        }
        return Pair(vertexList, triangleIndices)
    }

    private fun buildCoastalCellSkeletons(riverGraph: Graph,
                                          cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>,
                                          crestElevations: HashMap<Int, Float>,
                                          riverPolys: HashMap<Int, ArrayList<Polygon2F>>,
                                          coastalCellPolys: HashMap<Int, Polygon2F>,
                                          adjacencyPatches: HashMap<Int, HashSet<Int>>,
                                          inclusionPatches: HashMap<Int, HashSet<Int>>,
                                          smoothedCoastalCellPolys: HashMap<Int, Polygon2F>): Pair<HashMap<Int, ArrayList<LineSegment3F>>, HashMap<Int, ArrayList<LineSegment3F>>> {
        val edgeSkeletons = HashMap<Int, ArrayList<LineSegment3F>>()
        val riverSkeletons = HashMap<Int, ArrayList<LineSegment3F>>()
        smoothedCoastalCellPolys.forEach {
            val localEdgeSkeleton = ArrayList<LineSegment3F>()
            val localRiverSkeleton = ArrayList<LineSegment3F>()
            val id = it.key
            val polygon = it.value
            val polyPoints = PointSet2F(polygon.points)
            val untouchablePoints = getUntouchablePoints(riverGraph, coastalCellPolys, adjacencyPatches, riverGraph.vertices[id], cellsToRiverSegments[id])
            val pointsWithHeights = PointSet2F()
            val localRiverPolys = riverPolys[id]
            if (localRiverPolys != null) {
                localRiverSkeleton.addAll(localRiverPolys.flatMap { it.edges }.map { LineSegment3F(it.a as Point3F, it.b as Point3F) })
                pointsWithHeights.addAll(localRiverSkeleton.flatMap { listOf(it.a, it.b) })
            }
            (listOf(riverGraph.vertices[id]) + ((inclusionPatches[id] ?: HashSet()).map { riverGraph.vertices[it] })).flatMap { it.adjacentTriangles }.forEach {
                val height = crestElevations[it.id]
                if (height != null) {
                    val point = Point3F(it.center.x, it.center.y, height)
                    if (polyPoints.contains(point)) {
                        pointsWithHeights.add(point)
                    }
                }
            }
            polygon.edges.forEach {
                val a2d = it.a
                val b2d = it.b
                val a2dId = pointsWithHeights[a2d]
                val a3d = if (a2dId > -1) pointsWithHeights[a2dId] as Point3F? ?: Point3F(a2d.x, a2d.y, 0.0f) else Point3F(a2d.x, a2d.y, 0.0f)
                val b2dId = pointsWithHeights[b2d]
                val b3d = if (b2dId > -1) pointsWithHeights[b2dId] as Point3F? ?: Point3F(b2d.x, b2d.y, 0.0f) else Point3F(b2d.x, b2d.y, 0.0f)
                if (a3d.z == 0.0f && b3d.z == 0.0f && untouchablePoints.contains(a2d) && untouchablePoints.contains(b2d)) {
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
        return Pair(edgeSkeletons, riverSkeletons)
    }

    private fun smoothCoastline(riverGraph: Graph, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, revisedEdgePolys: HashMap<Int, Polygon2F>, adjacencyPatches: HashMap<Int, HashSet<Int>>): HashMap<Int, Polygon2F> {
        val interpolatedEdgePolys = HashMap<Int, Polygon2F>()
        revisedEdgePolys.forEach {
            val id = it.key
            val polygon = it.value
            val vertex = riverGraph.vertices[id]
            val borderPoints = getUntouchablePoints(riverGraph, revisedEdgePolys, adjacencyPatches, vertex, cellsToRiverSegments[id])
            val interiorEdges = ArrayList<LineSegment2F>()
            val coastEdges = ArrayList<LineSegment2F>()
            polygon.edges.forEach {
                if (borderPoints.contains(it.a) && borderPoints.contains(it.b)) {
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
        return interpolatedEdgePolys
    }

    private fun reviseCoastalCellPolygonsForRiverAndCoast(riverGraph: Graph, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, edgePolys: HashMap<Int, Polygon2F>, unconnectedPolys: HashMap<Int, ArrayList<Polygon2F>>): Triple<HashMap<Int, ArrayList<Polygon2F>>, HashMap<Int, HashSet<Int>>, HashMap<Int, HashSet<Int>>> {
        val revisedEdgePolys = HashMap<Int, Polygon2F>()
        val riverPolys = HashMap<Int, ArrayList<Polygon2F>>()
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
            val localRiverPolys = buildCellRiverPolygons(cellsToRiverSegments, id)
            riverPolys.put(id, localRiverPolys)
            revisedEdgePolys[id] = revisePolygonIfRiverIntersects(riverGraph, edgePolys, cellsToRiverSegments, adjacencyPatches, riverGraph.vertices[id], polygon, localRiverPolys)
        }
        edgePolys.clear()
        edgePolys.putAll(revisedEdgePolys)
        return Triple(riverPolys, adjacencyPatches, inclusionPatches)
    }

    private fun buildCellRiverPolygons(cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, id: Int): ArrayList<Polygon2F> {
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
                var lastPoint = Point3F(startPoint.x, startPoint.y, endElevation)
                pointsIn3d.add(lastPoint)
                for (edge in polyLine.edges) {
                    val newPoint = Point3F(edge.b.x, edge.b.y, lastPoint.z - (edge.length * slope))
                    pointsIn3d.add(newPoint)
                    lastPoint = newPoint
                }
                pointsIn3d.removeAt(pointsIn3d.size - 1)
                val endPoint = polyLine.points.last()
                pointsIn3d.add(Point3F(endPoint.x, endPoint.y, endElevation))
                localRiverPolys.add(Polygon2F(pointsIn3d, false))
            }
        }
        return localRiverPolys
    }

    private fun buildBasicInteriorCellPolygons(riverGraph: Graph, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, ignoreCells: Set<Int>): HashMap<Int, Polygon2F> {
        val polygons = HashMap<Int, Polygon2F>()
        for (i in 0..riverGraph.vertices.size - 1) {
            if (!ignoreCells.contains(i)) {
                val vertex = riverGraph.vertices[i]
                val riverSegments = cellsToRiverSegments[i]
                polygons[i] = spliceCellWithRiverSegments(vertex, riverSegments)
            }
        }
        return polygons
    }

    private fun buildBasicCoastalCellPolygons(riverGraph: Graph, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, riverPoints: PointSet2F, coastMultigon: Multigon2F, coastline: Polygon2F, coastCells: ArrayList<Int>): Pair<HashMap<Int, Polygon2F>, HashMap<Int, ArrayList<Polygon2F>>> {
        val edgePolys = HashMap<Int, Polygon2F>()
        val unconnectedPolys = HashMap<Int, ArrayList<Polygon2F>>()
        coastCells.forEach {
            val vertex = riverGraph.vertices[it]
            val polygons = getConnectedPolygons(buildConnectedEdgeSegments(coastMultigon, coastline, vertex, cellsToRiverSegments[it]))
            polygons.forEach {
                if (riverPoints.contains(vertex.point)) {
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
        return Pair(edgePolys, unconnectedPolys)
    }

    private fun addHeightPointIfNeeded(splices: LinkedHashMap<Pair<Int, Int>, Point3F>, triangle: Set<Int>, other: Set<Int>, p1: Point3F, p2: Point3F, p3: Point3F, id1: Int, id2: Int, maxTerrainSlope: Float) {
        if (triangle != other && other.contains(id1) && other.contains(id2)) {
            val splicePoint = LineSegment2F(p1, p2).interpolate(0.5f)
            val height = sqrt(min(splicePoint.distance2(p1), splicePoint.distance2(p2), splicePoint.distance2(p3)).toDouble()) * maxTerrainSlope
            val key = Pair(id1, id2)
            if (height < splices[key]?.z ?: Float.MAX_VALUE) {
                splices[key] = Point3F(splicePoint.x, splicePoint.y, height.toFloat())
            }
        }
    }

    private fun writeHeightData(name: String, heightMap: ArrayListMatrix<Float>) {
        var minValue = Double.MAX_VALUE
        var maxValue = -Double.MAX_VALUE
        for (y in (0..heightMap.width - 1)) {
            for (x in (0..heightMap.width - 1)) {
                val valueF = heightMap[x, y]
                if (valueF == -Float.MAX_VALUE) {
                    continue
                }
                val value = valueF.toDouble()
                minValue = min(minValue, value)
                maxValue = max(maxValue, value)
            }
        }
        val adjustedMinValue = minValue - ((maxValue - minValue) * 0.5)
        val range = 1.0 / (maxValue - adjustedMinValue)
        val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
        val raster = output.raster
        for (y in (0..heightMap.width - 1)) {
            for (x in (0..heightMap.width - 1)) {
                val value = heightMap[x, y].toDouble()
                val pixel = round(((min(max(value, adjustedMinValue), maxValue) - adjustedMinValue) * range) * 65535).toInt()
                raster.setSample(x, y, 0, pixel)
            }
        }
        ImageIO.write(output, "png", File("output/$name.png"))
    }

    fun drawTriangle(a: Point3F, b: Point3F, c: Point3F, heightMap: ArrayListMatrix<Float>) {
        val stride = heightMap.width
        val strideF = stride.toFloat()
        val p1 = Point3F(a.x * strideF, a.y * strideF, a.z)
        val p2 = Point3F(b.x * strideF, b.y * strideF, b.z)
        val p3 = Point3F(c.x * strideF, c.y * strideF, c.z)
        val normal = (p2 - p1).cross(p3 - p1)
        val d = -normal.dot(p1)
        val na = normal.a
        val nb = normal.b
        val nc = normal.c

        fun interpolateZ(x: Int, y: Int) = -((na * x) + (nb * y) + d) / nc

        val ax: Int = round(16.0f * p1.x)
        val bx: Int = round(16.0f * p2.x)
        val cx: Int = round(16.0f * p3.x)

        val ay: Int = round(16.0f * p1.y)
        val by: Int = round(16.0f * p2.y)
        val cy: Int = round(16.0f * p3.y)

        val dxAb: Int = ax - bx
        val dxBc: Int = bx - cx
        val dxCa: Int = cx - ax

        val dyAb: Int = ay - by
        val dyBc: Int = by - cy
        val dyCa: Int = cy - ay

        val fdxAb: Int = dxAb shl 4
        val fdxBc: Int = dxBc shl 4
        val fdxCa: Int = dxCa shl 4

        val fdyAb: Int = dyAb shl 4
        val fdyBc: Int = dyBc shl 4
        val fdyCa: Int = dyCa shl 4

        var minX: Int = (min(ax, bx, cx) + 0xF) shr 4
        val maxX: Int = (max(ax, bx, cx) + 0xF) shr 4
        var minY: Int = (min(ay, by, cy) + 0xF) shr 4
        val maxY: Int = (max(ay, by, cy) + 0xF) shr 4

        val q: Int = 8

        minX = minX and (q - 1).complement()
        minY = minY and (q - 1).complement()

        var c1: Int = dyAb * ax - dxAb * ay
        var c2: Int = dyBc * bx - dxBc * by
        var c3: Int = dyCa * cx - dxCa * cy

        if(dyAb < 0 || (dyAb == 0 && dxAb > 0)) c1++
        if(dyBc < 0 || (dyBc == 0 && dxBc > 0)) c2++
        if(dyCa < 0 || (dyCa == 0 && dxCa > 0)) c3++

        var blockYOffset: Int = minY * stride

        var y: Int = minY
        while (y < maxY) {
            var x: Int = minX
            while (x < maxX) {
                val x0: Int = x shl 4
                val x1: Int = (x + q - 1) shl 4
                val y0: Int = y shl 4
                val y1: Int = (y + q - 1) shl 4

                val a00: Int = if (c1 + dxAb * y0 - dyAb * x0 > 0) 1 else 0
                val a10: Int = if (c1 + dxAb * y0 - dyAb * x1 > 0) 1 else 0
                val a01: Int = if (c1 + dxAb * y1 - dyAb * x0 > 0) 1 else 0
                val a11: Int = if (c1 + dxAb * y1 - dyAb * x1 > 0) 1 else 0
                val hs1 = a00 or (a10 shl 1) or (a01 shl 2) or (a11 shl 3)

                val b00: Int = if (c2 + dxBc * y0 - dyBc * x0 > 0) 1 else 0
                val b10: Int = if (c2 + dxBc * y0 - dyBc * x1 > 0) 1 else 0
                val b01: Int = if (c2 + dxBc * y1 - dyBc * x0 > 0) 1 else 0
                val b11: Int = if (c2 + dxBc * y1 - dyBc * x1 > 0) 1 else 0
                val hs2: Int = b00 or (b10 shl 1) or (b01 shl 2) or (b11 shl 3)

                val c00: Int = if (c3 + dxCa * y0 - dyCa * x0 > 0) 1 else 0
                val c10: Int = if (c3 + dxCa * y0 - dyCa * x1 > 0) 1 else 0
                val c01: Int = if (c3 + dxCa * y1 - dyCa * x0 > 0) 1 else 0
                val c11: Int = if (c3 + dxCa * y1 - dyCa * x1 > 0) 1 else 0
                val hs3: Int = c00 or (c10 shl 1) or (c01 shl 2) or (c11 shl 3)

                if (hs1 == 0x0 || hs2 == 0x0 || hs3 == 0x0) {
                    x += q
                    continue
                }

                var yOffset: Int = blockYOffset

                if (hs1 == 0xF && hs2 == 0xF && hs3 == 0xF) {
                    var iy: Int = y
                    val endY = y + q
                    while (iy < endY) {
                        var ix: Int = x
                        val endX = x + q
                        while (ix < endX) {
                            heightMap[yOffset + ix] = interpolateZ(ix, iy)
                            ix++
                        }
                        yOffset += stride
                        iy++
                    }
                } else {
                    var cy1: Int = c1 + dxAb * y0 - dyAb * x0
                    var cy2: Int = c2 + dxBc * y0 - dyBc * x0
                    var cy3: Int = c3 + dxCa * y0 - dyCa * x0

                    var iy = y
                    val endY = y + q
                    while (iy < endY) {
                        var cx1: Int = cy1
                        var cx2: Int = cy2
                        var cx3: Int = cy3

                        var ix = x
                        val endX = x + q
                        while (ix < endX) {
                            if(cx1 > 0 && cx2 > 0 && cx3 > 0) {
                                heightMap[yOffset + ix] = interpolateZ(ix, iy)
                            }
                            cx1 -= fdyAb
                            cx2 -= fdyBc
                            cx3 -= fdyCa
                            ix++
                        }

                        cy1 += fdxAb
                        cy2 += fdxBc
                        cy3 += fdxCa

                        yOffset += stride
                        iy++
                    }
                }
                x += q
            }
            blockYOffset += q * stride
            y += q
        }
    }

    private fun max(a: Int, b: Int, c: Int) = max(max(a, b), c)

    private fun min(a: Int, b: Int, c: Int) = min(min(a, b), c)

    private fun min(a: Float, b: Float, c: Float) = min(min(a, b), c)

    private fun getUntouchablePoints(riverGraph: Graph, edgePolygons: HashMap<Int, Polygon2F>, adjacencyPatches: HashMap<Int, HashSet<Int>>, vertex: Vertex, riverSegments: ArrayList<RiverSegment>?): PointSet2F {
        val untouchables = PointSet2F(vertex.cell.border)
        untouchables.add(vertex.point)
        riverSegments?.forEach {
            untouchables.addAll(it.splices.map { it.second })
        }
        (riverGraph.vertices.getAdjacentVertices(vertex.id) + (adjacencyPatches[vertex.id] ?: HashSet())).forEach { id ->
            val adjacent = edgePolygons[id]
            if (adjacent != null) {
                untouchables.addAll(adjacent.points)
            }
        }
        return untouchables
    }

    private fun revisePolygonIfRiverIntersects(graph: Graph, edgePolygons: HashMap<Int, Polygon2F>, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, adjacencyPatches: HashMap<Int, HashSet<Int>>, vertex: Vertex, polygon: Polygon2F, riverPolys: ArrayList<Polygon2F>): Polygon2F {
        val untouchables = getUntouchablePoints(graph, edgePolygons, adjacencyPatches, vertex, cellsToRiverSegments[vertex.id])
        val riverEdges = ArrayList<LineSegment2F>(riverPolys.flatMap { it.edges }.filter { !untouchables.contains(it.a) && !untouchables.contains(it.b) })
        var adjustedPolygon = polygon
        var adjusted = true
        while (adjusted) {
            adjusted = false
            for (riverEdge in riverEdges) {
                val newPoints = PointSet2F(adjustedPolygon.points)
                val adjustedEdges = adjustedPolygon.edges
                for (i in 0..adjustedEdges.size - 1) {
                    val edge = adjustedEdges[i]
                    if (riverEdge.intersectsOrTouches(edge)) {
                        val key1 = edge.a
                        if (!untouchables.contains(key1)) {
                            newPoints.remove(key1)
                            adjustedPolygon = Polygon2F(newPoints.toList(), true)
                            adjusted = true
                            break
                        }
                        val key2 = edge.b
                        if (!untouchables.contains(key2)) {
                            newPoints.remove(key2)
                            adjustedPolygon = Polygon2F(newPoints.toList(), true)
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
                    if (!untouchables.contains(point) && riverEdge.distance2(point) < 0.0000025f) {
                        newPoints.remove(point)
                        adjustedPolygon = Polygon2F(newPoints.toList(), true)
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
        val polygon = spliceCellWithRiverSegments(vertex, riverSegments)
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

    private fun spliceCellWithRiverSegments(vertex: Vertex, riverSegments: ArrayList<RiverSegment>?): Polygon2F {
        val cell = vertex.cell
        val splices = ArrayList<Pair<LineSegment2F, Point2F>>()
        riverSegments?.forEach { riverSegment ->
            splices.addAll(riverSegment.splices)
        }
        val polygon = Polygon2F.fromUnsortedEdges(cell.borderEdges, splices)
        return polygon
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

    private fun calculateRiverSegments(graph: Graph, random: Random, rivers: ArrayList<TreeNode<RiverNode>>, flows: FloatArray): ArrayList<TreeNode<RiverSegment>> {
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
                        val deviation = max(0.0f, min(profile.deviation.valueAt(power), 1.0f))
                        val magnitude = profile.strength.valueAt(power)
                        val originalVector = Vector2F(modifyPoint.p, modifyPoint.cp2)
                        val deviantVector = originalVector.getPerpendicular() * (deviation * direction)
                        val newUnitVector = ((originalVector * (1.0f - deviation)) + deviantVector).getUnit()
                        val newVector1 = newUnitVector * (originalVector.length * magnitude)
                        val newVector2 = -newUnitVector * (Vector2F(modifyPoint.p, modifyPoint.cp1).length * magnitude)
                        modifyPoint.cp1 = modifyPoint.p + newVector2
                        modifyPoint.cp2 = modifyPoint.p + newVector1
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
            var elevations: Vector2F,
            var vertexId: Int,
            var profile: RiverProfile,
            val splices: ArrayList<Pair<LineSegment2F, Point2F>> = ArrayList())

    private fun calculateRiverSplines(graph: Graph, transition: RiverNodeTransition? , node: TreeNode<RiverNode>, flows: FloatArray): TreeNode<RiverSegment> {
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
            TreeNode(RiverSegment(spline, elevations, vertex.id, calculateRiverProfile(flow, slope, elevations)))
        } else {
            junctions.value.spline = connectEntryToDrain(junctions.value.point, junctions.value.vector, drainPoint, drainVector)
            junctions.value.splineElevation = drainElevation
            if (transitionEdge != null) {
                junctions.value.splices.add(Pair(transitionEdge, drainPoint))
            }
            toSplineTree(graph, junctions, flows)!!
        }
        return rootNode
    }

    private fun toSplineTree(graph: Graph, junctions: TreeNode<Junction>, flows: FloatArray): TreeNode<RiverSegment>? {
        val spline = junctions.value.spline ?: return null
        val elevations = Vector2F(junctions.value.splineElevation!!, junctions.value.elevation)
        val slope = calculateSlope(spline, elevations)
        val treeNode = TreeNode(RiverSegment(spline, elevations, junctions.value.vertexId, calculateRiverProfile(junctions.value.flow, slope, elevations), junctions.value.splices))
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
        return Spline2F(mutableListOf(sp1, sp2), false)
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
            drainElevation + ((min(elevation1, elevation2) - drainElevation) * jOffset)
        } else {
            desiredJunctionElevation
        }
        val normalizedFlowVector = (Vector2F(entry1, drainPoint) + Vector2F(entry2, drainPoint) * (totalDist * 0.5f)).getUnit()
        val outputVector = ((entryVector1 * dist1.toFloat()) + (entryVector2 * dist2.toFloat()) + normalizedFlowVector).getUnit()
        sp3.cp1 = junction + (-outputVector * (totalDist * 0.15f))
        return Triple(Junction(vertexId, junction, totalFlow, outputVector, junctionElevation), Spline2F(mutableListOf(sp1, sp3), false), Spline2F(mutableListOf(sp2, sp3), false))
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
