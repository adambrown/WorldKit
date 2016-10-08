package com.grimfox.gec.command

import com.grimfox.gec.Main
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.Vertex
import com.grimfox.gec.model.Graph.Vertices
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
import com.grimfox.gec.util.geometry.triangulatePolygon3
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
            val heightMap = ArrayListMatrix(outputWidth) { -Float.MAX_VALUE }
            val globalVertices = PointSet2F(0.0001f)
            val globalTriangles = LinkedHashSet<Set<Int>>()
            rivers.forEachIndexed { i, body ->
                val coastline = body.first
                val riverSet = body.second
                val border = borders[i]
                val riverGraph = buildRiverGraph(riverSet)
                val riverFlows = calculateRiverFlows(riverGraph.vertices, coastline, riverSet, 1600000000.0f, 0.39f)
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-rivers$i", Color(160, 200, 255)) {
//                    drawRivers(graph, regionMask, riverSet, coastline, border)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-graph$i", Color.WHITE) {
//                    drawGraph(riverGraph)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-coast$i") {
//                    graphics.color = Color.BLACK
//                    drawPolygon(coastline, true)
//                }
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

                val renderedCells = renderCoastalCells(riverGraph, cellsToRiverSegments, crestElevations, coastline, reverseRiverMap, heightMap, globalVertices, globalTriangles)
                renderInlandCells(riverGraph, cellsToRiverSegments, crestElevations, renderedCells, heightMap, globalVertices, globalTriangles)

                val segmentLength = 1.0f / 4096.0f
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-splines$i") {
//                    graphics.color = Color.BLACK
//                    graphics.stroke = BasicStroke(1.0f)
//                    drawRiverPolyLines(riverSplines, segmentLength, 3, false)
//                }
            }
//            draw(outputWidth, "test-new-${String.format("%05d", test)}-triangles") {
//                globalTriangles.forEach {
//                    val tri = it.toList()
//                    val a = globalVertices[tri[0]]!!
//                    val b = globalVertices[tri[1]]!!
//                    val c = globalVertices[tri[2]]!!
//                    graphics.color = Color.BLACK
//                    drawEdge(a, b)
//                    drawEdge(b, c)
//                    drawEdge(c, a)
//                    graphics.color = Color.RED
//                    drawPoint(a, 1)
//                    drawPoint(b, 1)
//                    drawPoint(c, 1)
//                }
//            }
            writeHeightData("test-new-${String.format("%05d", test)}-heightMap", heightMap)
        }
    }

    private fun renderInlandCells(riverGraph: Graph, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, crestElevations: HashMap<Int, Float>, alreadyRendered: Set<Int>, heightMap: ArrayListMatrix<Float>, globalVertices: PointSet2F, globalTriangles: LinkedHashSet<Set<Int>>) {
        val cellPolygons = buildBasicInteriorCellPolygons(riverGraph, cellsToRiverSegments, alreadyRendered)
        val riverPolygons = buildInlandRiverPolygons(cellPolygons, cellsToRiverSegments)
        val (edgeSkeletons, riverSkeletons) = buildInlandCellSkeletons(riverGraph, crestElevations, cellPolygons, riverPolygons)
        drawInlandCellPolygons(edgeSkeletons, riverSkeletons, cellPolygons, heightMap, globalVertices, globalTriangles)
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

    private fun renderCoastalCells(riverGraph: Graph, cellsToRiverSegments: HashMap<Int, ArrayList<RiverSegment>>, crestElevations: HashMap<Int, Float>, coastline: Polygon2F, reverseRiverMap: HashMap<Int, RiverNode>, heightMap: ArrayListMatrix<Float>, globalVertices: PointSet2F, globalTriangles: LinkedHashSet<Set<Int>>): Set<Int> {
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
        drawCoastalCellPolygons(reverseRiverMap, edgeSkeletons, riverSkeletons, smoothedCoastalCellPolys, heightMap, globalVertices, globalTriangles)
        return coastCells.toSet()
    }

    private fun drawCoastalCellPolygons(reverseRiverMap: HashMap<Int, RiverNode>,
                                        edgeSkeletons: HashMap<Int, ArrayList<LineSegment3F>>,
                                        riverSkeletons: HashMap<Int, ArrayList<LineSegment3F>>,
                                        smoothedCoastalCellPolys: HashMap<Int, Polygon2F>,
                                        heightMap: ArrayListMatrix<Float>,
                                        globalVertexSet: PointSet2F, globalTriangles: LinkedHashSet<Set<Int>>) {
        smoothedCoastalCellPolys.forEach {
            val id = it.key
            val polygon = it.value
            val edgeSkeleton = edgeSkeletons[id]
            val riverSkeleton = riverSkeletons[id] ?: arrayListOf()
            val maxTerrainSlope = reverseRiverMap[id]?.maxTerrainSlope ?: 0.0f
            if (edgeSkeleton != null) {
                val (vertices, triangles) = buildMesh2(id, edgeSkeleton, riverSkeleton, globalVertexSet, globalTriangles)
//                val (vertices, triangles) = buildMesh(id, polygon, edgeSkeleton, riverSkeleton)
                spliceZeroHeightTriangles(vertices, triangles, maxTerrainSlope)
//                if (id == 58) {
//                    draw(1024, "error", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
//                        graphics.color = Color.BLACK
//                        triangles.forEach {
//                            val tri = it.toList()
//                            val a = vertices[tri[0]]
//                            val b = vertices[tri[1]]
//                            val c = vertices[tri[2]]
//                            drawEdge(a, b)
//                            drawEdge(b, c)
//                            drawEdge(c, a)
//                            drawPoint(a, 3)
//                            drawPoint(b, 3)
//                            drawPoint(c, 3)
//                        }
//                    }
//                    println()
//                }
                renderTriangles(vertices, triangles, heightMap, globalVertexSet)
            }
        }
    }

    private fun drawInlandCellPolygons(edgeSkeletons: HashMap<Int, ArrayList<LineSegment3F>>,
                                       riverSkeletons: HashMap<Int, ArrayList<LineSegment3F>>,
                                       polygons: HashMap<Int, Polygon2F>,
                                       heightMap: ArrayListMatrix<Float>,
                                       globalVertexSet: PointSet2F,
                                       globalTriangles: LinkedHashSet<Set<Int>>) {
        polygons.forEach {
            val edgeSkeleton = edgeSkeletons[it.key]
            val riverSkeleton = riverSkeletons[it.key] ?: arrayListOf()
            if (edgeSkeleton != null) {
                val (vertices, triangles) = buildMesh2(it.key, edgeSkeleton, riverSkeleton, globalVertexSet, globalTriangles)
//                val (vertices, triangles) = buildMesh(it.key, it.value, edgeSkeleton, riverSkeleton)
                renderTriangles(vertices, triangles, heightMap, globalVertexSet)
            }
        }
    }

    private fun renderTriangles(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, heightMap: ArrayListMatrix<Float>, globalVertexSet: PointSet2F) {
        globalVertexSet.addAll(vertices)
        triangles.forEach {
            val tri = it.toList()
            val a = globalVertexSet[globalVertexSet[vertices[tri[0]]]] as Point3F
            val b = globalVertexSet[globalVertexSet[vertices[tri[1]]]] as Point3F
            val c = globalVertexSet[globalVertexSet[vertices[tri[2]]]] as Point3F
            val cross = (b - a).cross(c - a)
            if (cross.c < 0) {
                renderTriangle(a, b, c, heightMap)
            } else {
                renderTriangle(a, c, b, heightMap)
            }
        }
    }

    private fun spliceZeroHeightTriangles(vertices: ArrayList<Point3F>, triangles: LinkedHashSet<Set<Int>>, maxTerrainSlope: Float) {
        val splices = LinkedHashMap<Pair<Int, Int>, Point3F>()
        triangles.forEach { triangle ->
            val tri = triangle.toList()
            val aId = tri[0]
            val bId = tri[1]
            val cId = tri[2]
            val a = vertices[aId]
            val b = vertices[bId]
            val c = vertices[cId]
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

    private fun buildMesh2(id: Int, edgeSkeleton: ArrayList<LineSegment3F>, riverSkeleton: ArrayList<LineSegment3F>, globalVertices: PointSet2F, globalTriangles: LinkedHashSet<Set<Int>>): Pair<ArrayList<Point3F>, LinkedHashSet<Set<Int>>> {
        globalMapEdges(globalVertices, edgeSkeleton)
        globalMapEdges(globalVertices, riverSkeleton)
        closeEdge(edgeSkeleton)
        unTwistEdges(edgeSkeleton)
        unTwistEdges(riverSkeleton)
//        if (id == 88) {
//            draw(1024, "error1", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
//                graphics.color = Color.BLACK
//                edgeSkeleton.forEach {
//                    drawEdge(it.a, it.b)
//                }
//                graphics.color = Color.BLUE
//                riverSkeleton.forEach {
//                    drawEdge(it.a, it.b)
//                }
//            }
//            println()
//        }
        val meshPoints = PointSet2F()
        meshPoints.addAll(edgeSkeleton.flatMap { listOf(it.a, it.b) })
        meshPoints.addAll(riverSkeleton.flatMap { listOf(it.a, it.b) })
        val edges = LinkedHashSet<Pair<Int, Int>>()
        fun edge(a: Int, b: Int) = edges.add(Pair(min(a, b), max(a, b)))
        edgeSkeleton.forEach {
            edge(meshPoints[it.a], meshPoints[it.b])
        }
        riverSkeleton.forEach {
            edge(meshPoints[it.a], meshPoints[it.b])
        }
        val polygons = getPolygonEdgeSets(meshPoints, edges)
//        if (id == 88) {
//            draw(1024, "error2", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
//                graphics.color = Color.BLACK
//                polygons.forEach {
//                    it.forEach {
//                        drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                    }
//                }
//            }
//            println()
//        }

        val vertices = ArrayList(meshPoints.map { it as Point3F })

//        if (id == 88) {
//            draw(1024, "error2", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
//                graphics.color = Color.BLACK
//                polygons.forEach {
//                    it.forEach {
//                        drawEdge(vertices[it.first], vertices[it.second])
//                    }
//                }
//            }
//            println()
//        }
        val triangles = LinkedHashSet<Set<Int>>()
        polygons.forEach {
            triangles.addAll(triangulatePolygon3(meshPoints, it))
        }
//        draw(1024, "error", Color.WHITE, 30.0f, Vector2F(-(edgeSkeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edgeSkeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
//            graphics.color = Color.BLACK
//            triangles.forEach {
//                val tri = it.toList()
//                val a = vertices[tri[0]]
//                val b = vertices[tri[1]]
//                val c = vertices[tri[2]]
//                drawEdge(a, b)
//                drawEdge(b, c)
//                drawEdge(c, a)
//                drawPoint(a, 3)
//                drawPoint(b, 3)
//                drawPoint(c, 3)
//            }
//        }
//        println()
        triangles.forEach {
            globalTriangles.add(it.map { globalVertices[vertices[it]] }.toSet())
        }
        return Pair(vertices, triangles)
    }

    private fun globalMapEdges(globalVertexSet: PointSet2F, edgeSkeleton: ArrayList<LineSegment3F>) {
        edgeSkeleton.forEach {
            globalVertexSet.add(it.a)
            globalVertexSet.add(it.b)
        }
        val globalMappedEdgeSkeleton = edgeSkeleton.map { LineSegment3F(globalVertexSet[globalVertexSet[it.a]] as Point3F, globalVertexSet[globalVertexSet[it.b]] as Point3F) }.filter { it.a != it.b }
        edgeSkeleton.clear()
        edgeSkeleton.addAll(globalMappedEdgeSkeleton)
    }

    private fun triangulatePolygon(vertices: List<Point2F>, polygon: ArrayList<Pair<Int, Int>>, minAngle: Float): LinkedHashSet<Set<Int>> {
        val borderEdges = ArrayList(polygon.map { LineSegment2F(vertices[it.first], vertices[it.second]) })
        val edgeLookups = LinkedHashSet(polygon)
        val biDiEdgeLookups = LinkedHashSet(polygon.map { setOf(it.first, it.second) })
        val points = polygon.map { vertices[it.first] }
        val pointIds = polygon.map { it.first }
        val mwt = ArrayListMatrix(points.size) { Float.MAX_VALUE }
        val p3s = ArrayListMatrix(points.size) { -1 }
        for (i in points.size - 2 downTo 0) {
            val idi = pointIds[i]
            val pi = points[i]
            for (j in i + 1..points.size - 1) {
                val idj = pointIds[j]
                val pj = points[j]
//                draw(1024, "error1", Color.WHITE, 30.0f, Vector2F(-(vertices.map { it.x }.min()!!) + 0.0005f, -(vertices.map { it.y }.min()!!) + 0.0005f)) {
//                    graphics.color = Color.BLACK
//                    borderEdges.forEach {
//                        drawEdge(it.a, it.b)
//                        drawPoint(it.a, 3)
//                        drawPoint(it.b, 3)
//                    }
//                    graphics.stroke = BasicStroke(2.0f)
//                    graphics.color = Color.RED
//                    drawEdge(pi, pj)
//                    drawPoint(pi, 5)
//                    drawPoint(pj, 5)
//                }
//                println()
                if (edgeLookups.contains(Pair(idi, idj))) {
                    mwt[i, j] = pi.distance(pj)
                } else {
                    val isABorderEdge = biDiEdgeLookups.contains(setOf(idi, idj))
                    val isWithinPolygon = containsLine(points, borderEdges, LineSegment2F(pi, pj))
                    val isCollinear = isCollinearEdge(points, i, j, minAngle)
                    if (isABorderEdge || (isWithinPolygon && !isCollinear)) {
                        var min = Float.MAX_VALUE
                        var p3 = -1
                        for (k in i + 1..j - 1) {
                            val kwt = mwt[i, k] + mwt[k, j]
                            if (kwt < min) {
                                min = kwt
                                p3 = k
                            }
                        }
                        mwt[i, j] = pi.distance(pj) + min
                        p3s[i, j] = p3
                    } else {
                        mwt[i, j] = Float.MAX_VALUE
                    }
                }
            }
        }
        try {
            return produceTriangles(polygon, vertices, points, pointIds, p3s, 0, points.size - 1, LinkedHashSet<Set<Int>>())
        } catch (e: Exception) {
            if (minAngle < 0.00000000000000001f) {
                throw e
            }
            return triangulatePolygon(vertices, polygon, minAngle * minAngle)
        }
//        val swaps = ArrayList<Triple<Set<Int>, Set<Int>, Set<Int>>>()
//        rawTriangles.forEach { firstTri ->
//            val tri = firstTri.toList()
//            val a = tri[0]
//            val b = tri[1]
//            val c = tri[2]
//            val p1 = points[a]
//            val p2 = points[b]
//            val p3 = points[c]
//            if ((p2 - p1).cross(p3 - p1) == 0.0f) {
//                val e1 = setOf(a, b)
//                val e2 = setOf(b, c)
//                val e3 = setOf(c, a)
//                val e1IsBorder = biDiEdgeLookups.contains(e1)
//                val e2IsBorder = biDiEdgeLookups.contains(e2)
//                val e3IsBorder = biDiEdgeLookups.contains(e3)
//                if (e1IsBorder && e2IsBorder) {
//                    rawTriangles.forEach {
//                        if (it.containsAll(e3) && it != firstTri) {
//                            swaps.add(Triple(e3, firstTri, it))
//                        }
//                    }
//                } else if (e2IsBorder && e3IsBorder) {
//                    rawTriangles.forEach {
//                        if (it.containsAll(e1) && it != firstTri) {
//                            swaps.add(Triple(e1, firstTri, it))
//                        }
//                    }
//                } else {
//                    rawTriangles.forEach {
//                        if (it.containsAll(e2) && it != firstTri) {
//                            swaps.add(Triple(e2, firstTri, it))
//                        }
//                    }
//                }
//            }
//        }
//        swaps.forEach {
//            rawTriangles.remove(it.second)
//            rawTriangles.remove(it.third)
//            val base = LinkedHashSet(it.second)
//            base.addAll(it.third)
//            base.removeAll(it.first)
//            val commonEdge = it.first.toList()
//            val newTri1 = LinkedHashSet(base)
//            newTri1.add(commonEdge[0])
//            val newTri2 = LinkedHashSet(base)
//            newTri2.add(commonEdge[1])
//            rawTriangles.add(newTri1)
//            rawTriangles.add(newTri2)
//        }
//        val triangles = LinkedHashSet<Set<Int>>(rawTriangles.size)
//        rawTriangles.forEach {
//            triangles.add(it.map { pointIds[it] }.toSet())
//        }
//        return triangles
    }

    private fun isCollinearEdge(points: List<Point2F>, i: Int, j: Int, epsilon: Float): Boolean {
        val a = points[i]
        val b = points[j]
        for (k in 0..points.size - 1) {
            if (k == i || k == j) continue
            val c = points[k]
            val check1 = abs((b - a).cross(c - a)) < epsilon
            val check2 = min(a.x, b.x) <= c.x && c.x <= max(a.x, b.x)
            val check3 = min(a.y, b.y) <= c.y && c.y <= max(a.y, b.y)
            if (check1 && check2 && check3) {
//                draw(1024, "error2", Color.WHITE, 40.0f, Vector2F(-min(a.x, b.x, c.x) + 0.0005f, -min(a.y, b.y, c.y) + 0.0005f)) {
//                    graphics.color = Color.BLACK
//                    drawEdge(a, b)
//                    drawEdge(b, c)
//                    drawEdge(c, a)
//                    graphics.color = Color.RED
//                    drawPoint(a, 5)
//                    drawPoint(b, 5)
//                    drawPoint(c, 5)
//                }
//                println()
                return true
            }
        }
        return false
    }

    private fun produceTriangles(polygon: ArrayList<Pair<Int, Int>>, vertices: List<Point2F>, points: List<Point2F>, pointIds: List<Int>, p3s: Matrix<Int>, i: Int, j: Int, triangles: LinkedHashSet<Set<Int>>): LinkedHashSet<Set<Int>> {
        if (j - i < 2) {
            return triangles
        }
        val k = p3s[i, j]
        if (k == -1) {
            val pi = points[i]
            val pj = points[j]
//            draw(1024, "error2", Color.WHITE, 280.0f, Vector2F(-min(pi.x, pj.x) + 0.001f, -min(pi.y, pj.y) + 0.001f)) {
//                graphics.color = Color.BLACK
//                polygon.forEach {
//                    drawEdge(vertices[it.first], vertices[it.second])
//                    drawPoint(vertices[it.first], 3)
//                    drawPoint(vertices[it.second], 3)
//                }
//                graphics.color = Color.RED
//                drawEdge(pi, pj)
//                drawPoint(pi, 2)
//                drawPoint(pj, 2)
//            }
//            println()
//            draw(1024, "error3", Color.WHITE, 40.0f, Vector2F(-(polygon.flatMap { listOf(vertices[it.first].x, vertices[it.second].x) }.min()!!) + 0.0005f, -(polygon.flatMap { listOf(vertices[it.first].y, vertices[it.second].y) }.min()!!) + 0.0005f)) {
//                graphics.color = Color.BLACK
//                polygon.forEach {
//                    drawEdge(vertices[it.first], vertices[it.second])
//                }
//            }
//            println()
        }
        produceTriangles(polygon, vertices, points, pointIds, p3s, i, k, triangles)
        triangles.add(setOf(pointIds[i], pointIds[k], pointIds[j]))
        produceTriangles(polygon, vertices, points, pointIds, p3s, k, j, triangles)
        return triangles
    }

    private fun unTwistEdges(skeleton: ArrayList<LineSegment3F>) {
        var hasFix = true
        while (hasFix) {
            hasFix = false
            var fixUp: Pair<LineSegment3F, LineSegment3F>? = null
            for (first in skeleton) {
                for (second in skeleton) {
                    if (first != second && LineSegment2F(first.a, first.b).intersects(LineSegment2F(second.a, second.b))) {
                        fixUp = Pair(first, second)
                        break
                    }
                }
                if (fixUp != null) {
                    break
                }
            }
            if (fixUp != null) {
                skeleton.remove(fixUp.first)
                skeleton.remove(fixUp.second)
                val skeletonCopy = LinkedHashSet(skeleton)
                val fix1 = LineSegment3F(fixUp.first.a, fixUp.second.a)
                val fix2 = LineSegment3F(fixUp.first.b, fixUp.second.b)
                skeletonCopy.add(fix1)
                skeletonCopy.add(fix2)
                if (LineSegment2F.getConnectedEdgeSegments(skeletonCopy.map { LineSegment2F(it.a, it.b) }).size == 1) {
                    skeleton.add(fix1)
                    skeleton.add(fix2)
                } else {
                    skeleton.add(LineSegment3F(fixUp.first.a, fixUp.second.b))
                    skeleton.add(LineSegment3F(fixUp.first.b, fixUp.second.a))
                }
                hasFix = true
//                draw(1024, "error", Color.WHITE, 30.0f, Vector2F(-(skeleton.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(skeleton.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
//                    graphics.color = Color.BLACK
//                    skeleton.forEach {
//                        drawEdge(it.a, it.b)
//                        drawPoint(it.a, 2)
//                        drawPoint(it.b, 2)
//                    }
//                    graphics.color = Color.RED
//                    drawEdge(fixUp!!.first.a, fixUp!!.first.b)
//                    drawPoint(fixUp!!.first.a, 2)
//                    drawPoint(fixUp!!.first.b, 2)
//                    drawEdge(fixUp!!.second.a, fixUp!!.second.b)
//                    drawPoint(fixUp!!.second.a, 2)
//                    drawPoint(fixUp!!.second.b, 2)
//                }
//                println()
            }
        }
    }

    private fun closeEdge(edges: ArrayList<LineSegment3F>) {
        if (edges.first().a.epsilonEquals(edges.last().b)) {
            return
        }
        val unmodified = Polygon2F.fromUnsortedEdges(edges.map { LineSegment2F(it.a, it.b) })
        if (unmodified.isClosed) {
            return
        }
        val newEdges = Polygon2F(unmodified.points, true).edges.map { LineSegment3F(it.a as Point3F, it.b as Point3F) }
//        draw(4096, "error", Color.WHITE, 30.0f, Vector2F(-(edges.flatMap { listOf(it.a.x, it.b.x) }.min()!!) + 0.0005f, -(edges.flatMap { listOf(it.a.y, it.b.y) }.min()!!) + 0.0005f)) {
//            graphics.color = Color.RED
//            newEdges.forEach {
//                drawEdge(it.a, it.b)
//                drawPoint(it.a, 2)
//                drawPoint(it.b, 2)
//            }
//            graphics.color = Color.BLACK
//            edges.forEach {
//                drawEdge(it.a, it.b)
//                drawPoint(it.a, 2)
//                drawPoint(it.b, 2)
//            }
//        }
//        println()
        edges.clear()
        edges.addAll(newEdges)
    }

    fun triangulatePolygon2(vertices: PointSet2F, polygon: ArrayList<Pair<Int, Int>>): LinkedHashSet<Set<Int>> {
        val (meshPoints, edges) = buildBasicEdgeSet(polygon.map { vertices[it.first]!! })
        val fixedEdges = LinkedHashSet<Pair<Int, Int>>()
        fun fixedEdge(a: Int, b: Int) = fixedEdges.add(Pair(min(a, b), max(a, b)))
        polygon.forEach { fixedEdge(meshPoints[vertices[it.first]!!], meshPoints[vertices[it.second]!!]) }
        ArrayList(fixedEdges).forEach {
            if (it.first == it.second) {
                fixedEdges.remove(it)
            }
        }
        dropBadEdges(Polygon2F(polygon.map { vertices[it.first]!! }, true), meshPoints, fixedEdges, fixedEdges, edges)
        val (meshVertices, triangles) = buildMesh(meshPoints, fixedEdges, edges)
        val outputTriangles = LinkedHashSet<Set<Int>>()
        triangles.forEach {
            outputTriangles.add(it.map { vertices[meshVertices[it]] }.toSet())
        }
        return outputTriangles
    }

    private fun buildMesh(id: Int, polygon: Polygon2F, edgeSkeleton: ArrayList<LineSegment3F>, riverSkeleton: ArrayList<LineSegment3F>): Pair<ArrayList<Point3F>, LinkedHashSet<Set<Int>>> {
        closeEdge(edgeSkeleton)
        unTwistEdges(edgeSkeleton)
        unTwistEdges(riverSkeleton)
        val (meshPoints, edges) = buildBasicEdgeSet(edgeSkeleton.flatMap { listOf(it.a, it.b) } + riverSkeleton.flatMap { listOf(it.a, it.b) })
        val fixedEdges = LinkedHashSet<Pair<Int, Int>>()
        fun fixedEdge(a: Int, b: Int) = fixedEdges.add(Pair(min(a, b), max(a, b)))
        val borderEdges = LinkedHashSet<Pair<Int, Int>>()
        edgeSkeleton.forEach { fixedEdge(meshPoints[it.a], meshPoints[it.b]) }
        borderEdges.addAll(fixedEdges)
        riverSkeleton.forEach { fixedEdge(meshPoints[it.a], meshPoints[it.b]) }
        ArrayList(fixedEdges).forEach {
            if (it.first == it.second) {
                borderEdges.remove(it)
                fixedEdges.remove(it)
            }
        }
        dropBadEdges(polygon, meshPoints, borderEdges, fixedEdges, edges)
        var constrain = true
        var count = -1
        while (true) {
            count++
            val (vertexList, triangleIndices) = buildMesh(meshPoints, fixedEdges, edges)
            val holes = findHolesInMesh(borderEdges, triangleIndices)


            if (id == 509) {
                draw(4096, "error", Color.WHITE, 30.0f, Vector2F(-(edges.flatMap { listOf(meshPoints[it.first]!!.x, meshPoints[it.second]!!.x) }.min()!!) + 0.0005f, -(edges.flatMap { listOf(meshPoints[it.first]!!.y, meshPoints[it.second]!!.y) }.min()!!) + 0.0005f)) {
                    graphics.color = Color.BLACK
                    fixedEdges.forEach {
                        drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
                        drawPoint(meshPoints[it.first]!!, 2)
                        drawPoint(meshPoints[it.second]!!, 2)
                    }
                    triangleIndices.forEach {
                        val tri = it.toList()
                        val a = vertexList[tri[0]]
                        val b = vertexList[tri[1]]
                        val c = vertexList[tri[2]]
                        drawEdge(a, b)
                        drawEdge(b, c)
                        drawEdge(c, a)
                    }
                    graphics.color = Color.RED
                    holes.forEach {
                        drawEdge(vertexList[it.first], vertexList[it.second])
                        drawPoint(vertexList[it.first], 2)
                        drawPoint(vertexList[it.second], 2)
                    }
                }
                println()
            }


            if (constrain && holes.isNotEmpty()) {
                val points = LinkedHashSet(holes.flatMap { listOf(it.first, it.second) })
                if (points.size < 3) {
                    return Pair(vertexList, triangleIndices)
                }
                if (holes.size <= 3 && points.size == 3) {
                    triangleIndices.add(points)
                    return Pair(vertexList, triangleIndices)
                }
                if (holes.size == 4 && points.size == 4) {
                    val adjacentVertices = buildVertexAdjacencyMap(vertexList, holes)
                    val a = points.first()
                    val cs = adjacentVertices[a]
                    val otherPoints = HashSet(points)
                    otherPoints.remove(a)
                    otherPoints.removeAll(cs)
                    val b = otherPoints.first()
                    cs.forEach {
                        triangleIndices.add(setOf(a, b, it))
                    }
                    return Pair(vertexList, triangleIndices)
                }
                var selfIntersecting = false
                holes.forEach { first ->
                    holes.forEach { second ->
                        if (first != second) {
                            if (borderEdges.contains(first) && borderEdges.contains(second)) {
                                val firstLine = LineSegment2F(meshPoints[first.first]!!, meshPoints[first.second]!!)
                                val secondLine = LineSegment2F(meshPoints[second.first]!!, meshPoints[second.second]!!)
                                if (firstLine.intersects(secondLine)) {
                                    fixedEdges.remove(first)
                                    fixedEdges.remove(second)
                                    borderEdges.remove(first)
                                    borderEdges.remove(second)
                                    selfIntersecting = true
                                }
                            }
                        }
                    }
                }
                if (selfIntersecting) {
                    constrain = false
                }
                val groups = getPolygonEdgeSets(meshPoints, holes, false)
                for (group in groups) {
                    val (holeMeshPoints, holeEdges) = buildBasicEdgeSet(group.flatMap { listOf(vertexList[it.first], vertexList[it.second]) }.toSet())
                    addNewEdges(meshPoints, edges, holeMeshPoints, holeEdges)
                }
                dropBadEdges(polygon, meshPoints, borderEdges, fixedEdges, edges, constrain)
            } else {
                return Pair(vertexList, triangleIndices)
            }
        }
    }

    private fun getPolygonEdgeSets(meshPoints: PointSet2F, edges: Collection<Pair<Int, Int>>, putNonCyclesInCycles: Boolean = true): ArrayList<ArrayList<Pair<Int, Int>>> {
        val allPaths = ArrayList<ArrayList<Pair<Int, Int>>>()
        val segmentCycles = LinkedHashSet<LinkedHashSet<Int>>()
        val nodesInCycles = LinkedHashSet<Int>()
        val segments = getConnectedSegments(edges)
        val newEdges = LinkedHashSet<Pair<Int, Int>>()
        segments.forEach { segment ->
            val connections = HashMap<Int, LinkedHashSet<Int>>()
            val nodes = LinkedHashSet<Int>()
            segment.forEach {
                val edge = it.toList()
                val a = edge[0]
                val b = edge[1]
                nodes.add(a)
                nodes.add(b)
                connections.getOrPut(a, { LinkedHashSet() }).add(b)
                connections.getOrPut(b, { LinkedHashSet() }).add(a)
            }
            val segmentPaths = ArrayList<ArrayList<Pair<Int, Int>>>()
            val nonCycleNodes = LinkedHashSet<Int>()
            nodes.forEach { node ->
                if (!nodesInCycles.contains(node)) {
                    val paths = findPaths(connections, LinkedHashSet<Set<Int>>(), node, node)
                    if (paths != null) {
                        removeDuplicates(paths)
                        for (path in paths) {
                            if (isInnerPath(meshPoints, paths, path)) {
                                val segmentCycle = LinkedHashSet(path.flatMap { listOf(it.first, it.second) })
                                if (segmentCycles.add(segmentCycle)) {
                                    nodesInCycles.addAll(segmentCycle)
                                    segmentPaths.add(path)
                                }
                                break
                            }
                        }
                    } else {
                        nonCycleNodes.add(node)
                    }
                }
            }
            if (nonCycleNodes.isNotEmpty()) {
                val nonCycleSegments = getConnectedSegments(edges.filter { nonCycleNodes.contains(it.first) || nonCycleNodes.contains(it.second) })
                val orderedNonCycleSegments = ArrayList<ArrayList<Pair<Int, Int>>>()
                nonCycleSegments.forEach {
                    orderedNonCycleSegments.add(orderSegment(it))
                }
                if (putNonCyclesInCycles) {
                    orderedNonCycleSegments.forEach {
                        val (splicePoint, containingCycle) = findContainingCycle(meshPoints, edges, segmentPaths, it)
                        if (splicePoint != null && containingCycle != null) {
                            newEdges.add(findSuitableSpliceEdge(meshPoints, orderedNonCycleSegments, containingCycle, it, splicePoint))
                        }
                    }
                } else {
                    segmentPaths.addAll(orderedNonCycleSegments)
                }
            }
            allPaths.addAll(segmentPaths)
        }
        if (newEdges.isEmpty()) {
            return allPaths
        } else {
            val adjustedPaths = getPolygonEdgeSets(meshPoints, edges + newEdges, putNonCyclesInCycles)
//            val colors = listOf(Color.MAGENTA, Color.CYAN, Color.GREEN, Color.PINK, Color.ORANGE, Color.YELLOW)
//            draw(4096, "error", Color.WHITE, 30.0f, Vector2F(-(edges.flatMap { listOf(meshPoints[it.first]!!.x, meshPoints[it.second]!!.x) }.min()!!) + 0.0005f, -(edges.flatMap { listOf(meshPoints[it.first]!!.y, meshPoints[it.second]!!.y) }.min()!!) + 0.0005f)) {
//                graphics.color = Color.BLACK
//                edges.forEach {
//                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                    drawPoint(meshPoints[it.first]!!, 2)
//                    drawPoint(meshPoints[it.second]!!, 2)
//                }
//                for (i in 0..adjustedPaths.size - 1) {
//                    graphics.color = colors[i]
//                    val polygon = adjustedPaths[i]
//                    polygon.forEach {
//                        drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                        drawPoint(meshPoints[it.first]!!, 5)
//                        drawPoint(meshPoints[it.second]!!, 5)
//                    }
//                }
//                graphics.color = Color.RED
//                newEdges.forEach {
//                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                    drawPoint(meshPoints[it.first]!!, 2)
//                    drawPoint(meshPoints[it.second]!!, 2)
//                }
//            }
//            println()
            return adjustedPaths
        }
    }

    private fun removeDuplicates(paths: ArrayList<ArrayList<Pair<Int, Int>>>) {
        if (paths.size < 2) {
            return
        }
        val unorderedPaths = ArrayList<LinkedHashSet<Set<Int>>>()
        paths.forEach {
            unorderedPaths.add(LinkedHashSet(it.map { setOf(it.first, it.second) }))
        }
        for (i in paths.size - 1 downTo 0) {
            val oneUnorderedPath = unorderedPaths[i]
            for (j in 0..i - 1) {
                if (oneUnorderedPath == unorderedPaths[j]) {
                    paths.removeAt(i)
                    break
                }
            }
        }
    }

    private fun isInnerPath(meshPoints: PointSet2F, paths: ArrayList<ArrayList<Pair<Int, Int>>>, path: ArrayList<Pair<Int, Int>>): Boolean {
        val otherPaths = ArrayList(paths)
        otherPaths.remove(path)
        otherPaths.forEach {
            if (pathAContainsB(meshPoints, path, it)) {
                return false
            }
        }
        return true
    }

    private fun pathAContainsB(meshPoints: PointSet2F, a: ArrayList<Pair<Int, Int>>, b: ArrayList<Pair<Int, Int>>): Boolean {
        val aIds = a.map { it.first }
        val bIds = LinkedHashSet(b.map { it.first })
        bIds.removeAll(aIds)
        if (bIds.isEmpty()) {
            return true
        }
        return containsPoint(meshPoints, a, bIds.first())
    }

    private fun findSuitableSpliceEdge(meshPoints: PointSet2F, segments: ArrayList<ArrayList<Pair<Int, Int>>>, containingCycle: ArrayList<Pair<Int, Int>>, segment: ArrayList<Pair<Int, Int>>, splicePoint: Int): Pair<Int, Int> {
        val b = meshPoints[splicePoint]!!
        val a = if (segment.first().first == splicePoint) {
            meshPoints[segment.last().second]!!
        } else {
            meshPoints[segment.first().first]!!
        }
        val vector = LineSegment2F(a, b).toVector().getUnit()
        val c = b + vector
        val testLine = LineSegment2F(b, c)
        var intersection = c
        var minDist2 = Float.MAX_VALUE
        containingCycle.forEach {
            val line = LineSegment2F(meshPoints[it.first]!!, meshPoints[it.second]!!)
            val currentIntersect = line.intersection(testLine)
            if (currentIntersect != null) {
                val distance2 = currentIntersect.distance2(b)
                if (distance2 < minDist2) {
                    intersection = currentIntersect
                    minDist2 = distance2
                }
            }
        }
        val constrainedEdges = (segments.flatMap { it } + containingCycle).map { LineSegment2F(meshPoints[it.first]!!, meshPoints[it.second]!!) }
        for ((id, point) in containingCycle.map { Pair(it.first, meshPoints[it.first]!!) }.sortedBy { it.second.distance2(intersection) }) {
            val line = LineSegment2F(b, point)
            var intersects = false
            for (constrainedEdge in constrainedEdges) {
                if (line.intersects(constrainedEdge)) {
                    intersects = true
                    break
                }
            }
            if (!intersects) {
                return Pair(splicePoint, id)
            }
        }
        throw Exception("how is it possible that there are no non-intersecting connections between a line segment contained within an edge cycle?")
    }

    private fun  findContainingCycle(meshPoints: PointSet2F, edges: Collection<Pair<Int, Int>>, cycles: ArrayList<ArrayList<Pair<Int, Int>>>, segment: ArrayList<Pair<Int, Int>>): Pair<Int?, ArrayList<Pair<Int, Int>>?> {
        val end1 = segment.first().first
        val end2 = segment.last().second
        val cyclesToTest = ArrayList<Pair<Int, ArrayList<Pair<Int, Int>>>>()
        for (cycle in cycles) {
            for (edge in cycle) {
                if (edge.first == end1 || edge.second == end1) {
                    cyclesToTest.add(Pair(end2, cycle))
                    break
                }
                if (edge.first == end2 || edge.second == end2) {
                    cyclesToTest.add(Pair(end1, cycle))
                    break
                }
            }
        }
        cyclesToTest.forEach {
            if (containsPoint(meshPoints, it.second, it.first)) {
                return it
            }
        }
        val colors = listOf(Color.MAGENTA, Color.CYAN, Color.GREEN, Color.PINK, Color.ORANGE, Color.YELLOW)
//        draw(4096, "error1", Color.WHITE, 30.0f, Vector2F(-(meshPoints.map { it.x }.min()!!) + 0.0005f, -(meshPoints.map { it.y }.min()!!) + 0.0005f)) {
//            graphics.color = Color.BLACK
//            graphics.stroke = BasicStroke(3.0f)
//            edges.forEach {
//                drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                drawPoint(meshPoints[it.first]!!, 5)
//                drawPoint(meshPoints[it.second]!!, 5)
//            }
//            graphics.stroke = BasicStroke(1.0f)
//            cycles.forEachIndexed { i, it ->
//                graphics.color = colors[i]
//                it.forEach {
//                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                    drawPoint(meshPoints[it.first]!!, 4)
//                    drawPoint(meshPoints[it.second]!!, 4)
//                }
//            }
//            graphics.color = Color.RED
//            segment.forEach {
//                drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                drawPoint(meshPoints[it.first]!!, 3)
//                drawPoint(meshPoints[it.second]!!, 3)
//            }
//        }
//        draw(4096, "error2", Color.WHITE) {
//            graphics.color = Color.BLACK
//            graphics.stroke = BasicStroke(2.0f)
//            edges.forEach {
//                drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                drawPoint(meshPoints[it.first]!!, 3)
//                drawPoint(meshPoints[it.second]!!, 3)
//            }
//            graphics.stroke = BasicStroke(1.0f)
//            cycles.forEachIndexed { i, it ->
//                graphics.color = colors[i]
//                it.forEach {
//                    drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                    drawPoint(meshPoints[it.first]!!, 2)
//                    drawPoint(meshPoints[it.second]!!, 2)
//                }
//            }
//            graphics.color = Color.RED
//            segment.forEach {
//                drawEdge(meshPoints[it.first]!!, meshPoints[it.second]!!)
//                drawPoint(meshPoints[it.first]!!, 1)
//                drawPoint(meshPoints[it.second]!!, 1)
//            }
//        }
//        println()
        return Pair(null, null)
    }

    fun containsPoint(meshPoints: PointSet2F, polygon: ArrayList<Pair<Int, Int>>, id: Int): Boolean {
        val point = meshPoints[id]!!
        val points = polygon.map { meshPoints[it.first]!! }
        var i: Int = 0
        var j: Int = points.size - 1
        var c = false
        while (i < points.size) {
            val pi = points[i]
            val pj = points[j]
            if (((pi.y > point.y) != (pj.y > point.y)) && (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                c = !c
            }
            j = i
            i++
        }
        return c
    }

    fun containsLine(polygon: List<Point2F>, borderEdges: List<LineSegment2F>, line: LineSegment2F): Boolean {
        val point = line.interpolate(0.5f)
        var i: Int = 0
        var j: Int = polygon.size - 1
        var c = false
        while (i < polygon.size) {
            val pi = polygon[i]
            val pj = polygon[j]
            if (((pi.y > point.y) != (pj.y > point.y)) && (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                c = !c
            }
            j = i
            i++
        }
        if (!c) {
            return false
        }
        borderEdges.forEach {
            if (!(line.a.epsilonEquals(it.a) || line.b.epsilonEquals(it.a) || line.a.epsilonEquals(it.b) || line.b.epsilonEquals(it.b)) && line.intersectsOrTouches(it)) {
                return false
            }
        }
        return true
    }

    private fun orderSegment(segment: Collection<Set<Int>>): ArrayList<Pair<Int, Int>> {
        val path = ArrayList<Pair<Int, Int>>()
        val mutable = ArrayList(segment.filter { it.size == 2 })
        val seed = mutable.removeAt(mutable.size - 1).toList()
        val seedPair = Pair(seed[0], seed[1])
        path.add(seedPair)
        var pair = seedPair
        var hasNext = true
        while (hasNext) {
            hasNext = false
            for (i in 0..mutable.size - 1) {
                if (mutable[i].contains(pair.first)) {
                    val next = LinkedHashSet(mutable.removeAt(i))
                    next.remove(pair.first)
                    pair = Pair(next.first(), pair.first)
                    path.add(0, pair)
                    hasNext = true
                    break
                }
            }
        }
        pair = seedPair
        hasNext = true
        while (hasNext) {
            hasNext = false
            for (i in 0..mutable.size - 1) {
                if (mutable[i].contains(pair.second)) {
                    val next = LinkedHashSet(mutable.removeAt(i))
                    next.remove(pair.second)
                    pair = Pair(pair.second, next.first())
                    path.add(pair)
                    hasNext = true
                    break
                }
            }
        }
        return path
    }

    private fun findPaths(connections: HashMap<Int, LinkedHashSet<Int>>, usedEdges: LinkedHashSet<Set<Int>>, start: Int, end: Int): ArrayList<ArrayList<Pair<Int, Int>>>? {
        val options = connections[start] ?: return null
        if (options.isEmpty()) {
            return null
        }
        val pathsFromHere = ArrayList<ArrayList<Pair<Int, Int>>>()
        if (options.contains(end) && !usedEdges.contains(setOf(start, end))) {
            pathsFromHere.add(arrayListOf(Pair(start, end)))
            return pathsFromHere
        }
        val paths = ArrayList<Pair<Pair<Int, Int>, ArrayList<Pair<Int, Int>>>>()
        options.forEach { option ->
            val theEdge = setOf(start, option)
            if (!usedEdges.contains(theEdge)) {
                val newUsedEdges = LinkedHashSet(usedEdges)
                newUsedEdges.add(theEdge)
                val nextLegs = findPaths(connections, newUsedEdges, option, end)
                nextLegs?.forEach { nextLeg ->
                    paths.add(Pair(Pair(start, option), nextLeg))
                }
            }
        }
        if (paths.isEmpty()) {
            return null
        } else {
            paths.sortBy { it.second.size }
            paths.forEach {
                val thePath = ArrayList<Pair<Int, Int>>()
                thePath.add(it.first)
                thePath.addAll(it.second)
                pathsFromHere.add(thePath)
            }
            return pathsFromHere
        }
    }

    private fun  getConnectedSegments(edges: Collection<Pair<Int, Int>>): List<Set<Set<Int>>> {
        val fullSet = LinkedHashSet(edges.map { setOf(it.first, it.second) }.filter { it.size == 2 })
        val unconnected = LinkedHashSet(fullSet)
        val segments = ArrayList<Set<Set<Int>>>()
        while (unconnected.isNotEmpty()) {
            val seed = unconnected.first()
            unconnected.remove(seed)
            val segment = getConnectedEdges(seed, fullSet)
            unconnected.removeAll(segment)
            segments.add(segment)
        }
        return segments
    }

    fun getConnectedEdges(seed: Set<Int>, edgeSet: Collection<Set<Int>>): Set<Set<Int>> {
        val connectedEdges = LinkedHashSet<Set<Int>>()
        connectedEdges.add(seed)
        var nextEdges = LinkedHashSet<Set<Int>>(connectedEdges)
        while (nextEdges.isNotEmpty()) {
            val newEdges = LinkedHashSet<Set<Int>>()
            nextEdges.forEach { edge ->
                edgeSet.forEach {
                    val intersection = HashSet(edge)
                    intersection.retainAll(it)
                    if (intersection.isNotEmpty()) {
                        newEdges.add(it)
                    }
                }
            }
            newEdges.removeAll(connectedEdges)
            connectedEdges.addAll(newEdges)
            nextEdges = newEdges
        }
        return connectedEdges
    }

    private fun findHolesInMesh(borderEdges: LinkedHashSet<Pair<Int, Int>>, triangleIndices: LinkedHashSet<Set<Int>>): LinkedHashSet<Pair<Int, Int>> {
        val edgeConnections = HashMap<Pair<Int, Int>, Int>()
        triangleIndices.forEach {
            val tri = it.toList()
            val a = tri[0]
            val b = tri[1]
            val c = tri[2]
            val ab = Pair(min(a, b), max(a, b))
            val bc = Pair(min(b, c), max(b, c))
            val ca = Pair(min(c, a), max(c, a))
            edgeConnections[ab] = (edgeConnections[ab] ?: 0) + 1
            edgeConnections[bc] = (edgeConnections[bc] ?: 0) + 1
            edgeConnections[ca] = (edgeConnections[ca] ?: 0) + 1
        }
        val borderOrphans = LinkedHashSet(borderEdges)
        borderOrphans.removeAll(edgeConnections.keys)
        val holes = LinkedHashSet(edgeConnections.filter { it.value == 1 }.map { it.key })
        holes.removeAll(borderEdges)
        holes.addAll(borderOrphans)
        return holes
    }

    private fun addNewEdges(meshPoints: PointSet2F, edges: LinkedHashSet<Pair<Int, Int>>, holeMeshPoints: PointSet2F, holeEdges: LinkedHashSet<Pair<Int, Int>>) {
        val holeMeshToMeshMap = HashMap<Int, Int>(holeMeshPoints.size)
        holeMeshPoints.forEach {
            val holeIndex = holeMeshPoints[it]
            val meshIndex = meshPoints[it]
            holeMeshToMeshMap[holeIndex] = meshIndex
        }
        fun edge(a: Int, b: Int) = edges.add(Pair(min(a, b), max(a, b)))
        holeEdges.forEach {
            edge(holeMeshToMeshMap[it.first]!!, holeMeshToMeshMap[it.second]!!)
        }
    }

    private fun buildMesh(meshPoints: PointSet2F, fixedEdges: LinkedHashSet<Pair<Int, Int>>, edges: LinkedHashSet<Pair<Int, Int>>): Pair<ArrayList<Point3F>, LinkedHashSet<Set<Int>>> {
        val edgeList = ArrayList(edges + fixedEdges)
        val vertexList = ArrayList(meshPoints.map { it as Point3F })
        val vertexToVertexMap = buildVertexAdjacencyMap(vertexList, edgeList)
        val triangleIndices = LinkedHashSet<Set<Int>>()
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
                        }
                    }
                }
            }
        }
        return Pair(vertexList, triangleIndices)
    }

    private fun buildVertexAdjacencyMap(vertices: List<Point2F>, edges: Collection<Pair<Int, Int>>): ArrayList<ArrayList<Int>> {
        val vertexToVertexMap = ArrayList<ArrayList<Int>>()
        for (v in 0..vertices.size - 1) {
            vertexToVertexMap.add(ArrayList(5))
        }
        edges.forEach { edge ->
            vertexToVertexMap[edge.first].add(edge.second)
            vertexToVertexMap[edge.second].add(edge.first)
        }
        return vertexToVertexMap
    }

    private fun dropBadEdges(polygon: Polygon2F, meshPoints: PointSet2F, borderEdges: LinkedHashSet<Pair<Int, Int>>, fixedEdges: LinkedHashSet<Pair<Int, Int>>, edges: LinkedHashSet<Pair<Int, Int>>, checkWithinPoly: Boolean = true) {
        edges.removeAll(fixedEdges)
        ArrayList(edges).forEach {
            if (it.first == it.second) {
                edges.remove(it)
            }
        }
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
        fun Pair<Int, Int>.toLine() = LineSegment2F(meshPoints[first]!!, meshPoints[second]!!)
        for (edge in ArrayList(edges)) {
            val line = edge.toLine()
            val mid = line.interpolate(0.5f)
            if ((checkWithinPoly && !polygon.isWithin(mid)) || meshPoints.contains(mid)) {
                edges.remove(edge)
                continue
            }
            for (fixedEdge in borderEdges) {
                val fixedLine = fixedEdge.toLine()
                if (fixedLine.intersects(line) || fixedLine.collinearOverlappingEpsilon(line)) {
                    edges.remove(edge)
                    break
                }
            }
        }
    }

    private fun buildBasicEdgeSet(points: Collection<Point2F>): Pair<PointSet2F, LinkedHashSet<Pair<Int, Int>>> {
        val meshPoints = PointSet2F()
        meshPoints.addAll(points)
        val cellGraph = buildGraph(1.0f, ArrayList(meshPoints))
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
        return Pair(meshPoints, edges)
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
        val adjustedMinValue = minValue - ((maxValue - minValue) * 0.1)
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

    fun renderTriangle(a: Point3F, b: Point3F, c: Point3F, heightMap: ArrayListMatrix<Float>) {
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
        val minZ = min(p1.z, p2.z, p3.z)
        val maxZ = max(p1.z, p2.z, p3.z)

        fun interpolateZ(x: Int, y: Int): Float {
            val height = clamp(minZ, maxZ, -((na * x) + (nb * y) + d) / nc)
            if (height.isNaN()) {
                draw(4096, "error", Color.WHITE, 30.0f, Vector2F(-(min(a.x, b.x, c.x)) + 0.0005f, -(min(a.y, b.y, c.y)) + 0.0005f)) {
                    graphics.color = Color.BLACK
                    drawEdge(a, b)
                    drawEdge(b, c)
                    drawEdge(c, a)
                    drawPoint(a, 5)
                    drawPoint(b, 5)
                    drawPoint(c, 5)
                }
                println("WTF!!")
            }
            return height
        }

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

    private fun max(a: Float, b: Float, c: Float) = max(max(a, b), c)

    private fun clamp(min: Float, max: Float, f: Float) = min(max(min, f), max)

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
                val delta = abs(min - it.second)
                val negativeIndex = it.second - coastline.points.size
                val deltaNeg = abs(min - negativeIndex)
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
            var childEdge: LineSegment2F? = null
            try {
                childEdge = cell.sharedEdge(childCell)!!
            } catch (e: Exception) {
                draw(4096, "error1", Color.WHITE, 20.0f, Vector2F(-(cell.border.map { it.x } + childCell.border.map { it.x }).min()!! + 0.0001f, -(cell.border.map { it.y } + childCell.border.map { it.y }).min()!! + 0.0001f)) {
                    graphics.color = Color.BLUE
                    drawCell(cell)
                    graphics.color = Color.RED
                    drawCell(childCell)
                    graphics.color = Color.BLACK
                    drawEdge(cell.vertex.point, childCell.vertex.point)
                }
                draw(4096, "error2", Color.WHITE) {
                    graphics.color = Color.BLUE
                    drawCell(cell)
                    graphics.color = Color.RED
                    drawCell(childCell)
                    graphics.color = Color.BLACK
                    drawEdge(cell.vertex.point, childCell.vertex.point)
                }
                println()
                throw e
            }
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
            val dy = abs(elevations.b - elevations.a)
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
