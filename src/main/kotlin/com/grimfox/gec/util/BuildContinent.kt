package com.grimfox.gec.util

import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Polygon2F
import com.grimfox.gec.model.geometry.Vector2F
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.util.Biomes.Biome
import com.grimfox.gec.util.Coastline.applyMask
import com.grimfox.gec.util.Coastline.refineCoastline
import com.grimfox.gec.util.Graphs.generateGraph
import com.grimfox.gec.util.Regions.buildRegions
import com.grimfox.gec.util.WaterFlows.generateWaterFlows
import java.util.*
import java.util.concurrent.ExecutorService

object BuildContinent {

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
            var regionsSeed: Long = System.currentTimeMillis(),
            var biomesSeed: Long = System.currentTimeMillis(),
            var regionsMapScale: Int = 4,
            var biomesMapScale: Int = 4,
            var stride: Int = 7,
            var regionCount: Int = 8,
            var connectedness: Float = 0.11f,
            var regionSize: Float = 0.035f,
            var initialReduction: Int = 7,
            var regionPoints: Int = 2,
            var maxRegionTries: Int = 50,
            var maxIslandTries: Int = 500,
            var islandDesire: Int = 1,
            var parameters: ArrayList<Parameters> = arrayListOf(
                    Parameters(24, 0.40f, 0.1f, 8, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f),
                    Parameters(32, 0.40f, 0.2f, 6, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f),
//                    Parameters(80, 0.39f, 0.05f, 3, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f)
                    Parameters(64, 0.40f, 0.1f, 4, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f),
                    Parameters(128, 0.39f, 0.05f, 3, 0.1f, 0.05f, 0.035f, 2.0f, 0.01f)
//                    Parameters(140, 0.39f, 0.03f, 2, 0.1f, 0.05f, 0.035f, 2.0f, 0.01f)
//                    Parameters(256, 0.39f, 0.01f, 2, 0.1f, 0.05f, 0.035f, 2.0f, 0.015f)
            ),
            var currentIteration: Int = 0,
            var mountainsOn: Boolean = true,
            var coastalMountainsOn: Boolean = true,
            var foothillsOn: Boolean = true,
            var rollingHillsOn: Boolean = true,
            var plateausOn: Boolean = true,
            var plainsOn: Boolean = true)

    fun generateRegions(parameterSet: ParameterSet = ParameterSet(), executor: ExecutorService): Pair<Graph, Matrix<Byte>> {
        return timeIt("generated regions in") {
            val random = Random(parameterSet.regionsSeed)
            var (graph, regionMask) = buildRegions(parameterSet)
            parameterSet.parameters.forEachIndexed { i, parameters ->
                parameterSet.currentIteration = i
                val localGraph = generateGraph(parameters.stride, random, 0.8)
                val (mask, water, borderPoints) = applyMask(localGraph, graph, regionMask, executor)
                regionMask = mask
                refineCoastline(localGraph, random, regionMask, water, borderPoints, parameters)
                graph = localGraph
            }
            Pair(graph, regionMask)
        }
    }

    fun buildBiomeMaps(executor: ExecutorService, randomSeed: Long, inputGraph: Graph, biomes: List<Biome>, biomeScale: Int): Pair<Graph, Matrix<Byte>> {
        val random = Random(randomSeed)
        val randomSeeds = Array(3) { random.nextLong() }
        val biomeCount = biomes.size
        val biomeGraphSmallFuture = executor.call {
            val innerRandom = Random(randomSeeds[0])
            val graph = Graphs.generateGraph(biomeScale, innerRandom, 0.98)
            val mask = ByteArrayMatrix(graph.stride!!) { ((Math.abs(innerRandom.nextInt()) % biomeCount) + 1).toByte() }
            graph to mask
        }
        val biomeGraphMidFuture = executor.call {
            val innerRandom = Random(randomSeeds[1])
            val graph = Graphs.generateGraph(28, innerRandom, 0.98)
            val vertices = graph.vertices
            val (parentGraph, parentMask) = biomeGraphSmallFuture.value
            val mask = ByteArrayMatrix(graph.stride!!) { i ->
                val point = vertices[i].point
                parentMask[parentGraph.getClosestPoint(point, parentGraph.getClosePoints(point, 2))]
            }
            graph to mask
        }
        val biomeGraphHighFuture = executor.call {
            val innerRandom = Random(randomSeeds[2])
            val graph = Graphs.generateGraph(72, innerRandom, 0.88)
            val vertices = graph.vertices
            val (parentGraph, parentMask) = biomeGraphMidFuture.value
            val mask = ByteArrayMatrix(graph.stride!!) { i ->
                val point = vertices[i].point
                parentMask[parentGraph.getClosestPoint(point, parentGraph.getClosePoints(point, 2))]
            }
            graph to mask
        }
        val biomeGraphFinalFuture = executor.call {
            val graph = inputGraph
            val vertices = graph.vertices
            val (parentGraph, parentMask) = biomeGraphHighFuture.value
            val mask = ByteArrayMatrix(inputGraph.stride!!) { i ->
                val vertex = vertices[i]
                val point = vertex.point
                parentMask[parentGraph.getClosestPoint(point, parentGraph.getClosePoints(point, 2))]
            }
            graph to mask
        }
        return biomeGraphFinalFuture.value
    }

    class RegionSplines(val coastEdges: List<LineSegment2F>, val coastPoints: List<List<Point2F>>, val riverEdges: List<LineSegment2F>, val riverPoints: List<List<Point2F>>, val mountainEdges: List<LineSegment2F>, val mountainPoints: List<List<Point2F>>)

    fun generateRegionSplines(random: Random, regionGraph: Graph, regionMask: Matrix<Byte>, mapScale: Int): RegionSplines {
        val smoothing = (1.0f - (mapScale / 20.0f)).coerceIn(0.0f, 1.0f)
        val water = LinkedHashSet<Int>(regionGraph.vertices.size)
        val regions = ArrayList<LinkedHashSet<Int>>(16)
        for (i in 0..regionGraph.vertices.size - 1) {
            val maskValue = regionMask[i]
            if (maskValue < 1) {
                water.add(i)
            } else {
                val regionId = maskValue - 1
                if (regions.size < maskValue) {
                    for (j in 0..regionId - regions.size) {
                        regions.add(LinkedHashSet<Int>())
                    }
                }
                regions[regionId].add(i)
            }
        }

        val borderSegments = findAllBorderSegments(random, regionGraph, regions, water)

        val coastEdges = ArrayList<LineSegment2F>()
        val coastPoints = ArrayList<List<Point2F>>()
        borderSegments.first.forEach {
            val points = buildClosedEdges(it, smoothing)
            coastPoints.add(points)
            (1..points.size - 1).mapTo(coastEdges) { LineSegment2F(points[it - 1], points[it]) }
        }

        val riverEdges = ArrayList<LineSegment2F>()
        val riverPoints = ArrayList<List<Point2F>>()
        borderSegments.second.forEach {
            val points = buildOpenEdges(it, smoothing)
            riverPoints.add(points)
            (1..points.size - 1).mapTo(riverEdges) { LineSegment2F(points[it - 1], points[it]) }
        }

        val mountainEdges = ArrayList<LineSegment2F>()
        val mountainPoints = ArrayList<List<Point2F>>()
        borderSegments.third.forEach {
            val points = buildOpenEdges(it, smoothing)
            mountainPoints.add(points)
            (1..points.size - 1).mapTo(mountainEdges) { LineSegment2F(points[it - 1], points[it]) }
        }

        return RegionSplines(coastEdges, coastPoints, riverEdges, riverPoints, mountainEdges, mountainPoints)
    }

    private fun buildOpenEdges(polygon: Polygon2F, smoothing: Float): List<Point2F> {
        return buildEdges(getCurvePoints(polygon.points, false, 0.00035f, smoothing), false, true)
    }

    private fun buildClosedEdges(polygons: List<Polygon2F>, smoothing: Float): List<Point2F> {
        val points = ArrayList<Point2F>()
        if (polygons.size == 1) {
            points.addAll(buildEdges(getCurvePoints(polygons.first().points, true, 0.00035f, smoothing), true, true))
        } else {
            polygons.forEachIndexed { i, it ->
                points.addAll(buildEdges(getCurvePoints(it.points, false, 0.00035f, smoothing), false, i == 0))
            }
        }
        return points
    }

    fun findAllBorderSegments(random: Random, graph: Graph, regions: List<LinkedHashSet<Int>>, water: LinkedHashSet<Int>): Triple<ArrayList<ArrayList<Polygon2F>>, ArrayList<Polygon2F>, ArrayList<Polygon2F>> {
        val vertices = graph.vertices
        val epsilon = 0.0f
        val coasts = Array(regions.size) { ArrayList<LineSegment2F>() }
        val borders = Array(regions.size * regions.size) { ArrayList<LineSegment2F>() }
        for (i in 0..regions.size - 1) {
            val region = regions[i]
            region.forEach { id ->
                vertices.getAdjacentVertices(id).forEach { adjacentId ->
                    if (!region.contains(adjacentId)) {
                        if (water.contains(adjacentId)) {
                            addBorderEdge(vertices, coasts[i], id, adjacentId, true)
                        } else {
                            for (j in i + 1..regions.size - 1) {
                                val otherRegion = regions[j]
                                if (otherRegion.contains(adjacentId)) {
                                    addBorderEdge(vertices, borders[i * regions.size + j], id, adjacentId, true)
                                }
                            }
                        }
                    }
                }
            }
        }
        val closedPolygons = ArrayList<ArrayList<Polygon2F>>()
        val coastPolygons = ArrayList<ArrayList<Polygon2F>>()
        coasts.forEach {
            if (it.isNotEmpty()) {
                val edgeSegments = LineSegment2F.getConnectedEdgeSegments(it, epsilon)
                edgeSegments.forEach {
                    val polygon = Polygon2F.fromUnsortedEdges(it, null, true, epsilon)
                    if (polygon.isClosed) {
                        closedPolygons.add(arrayListOf(polygon))
                    } else {
                        val first = polygon.points.first()
                        val last = polygon.points.last()
                        var makeNew = true
                        for (other in coastPolygons) {
                            val otherFirst1 = other.first().points.first()
                            val otherFirst2 = other.first().points.last()
                            val otherLast1 = other.last().points.first()
                            val otherLast2 = other.last().points.last()
                            if (first.epsilonEquals(otherFirst1, 0.0f)
                                    || first.epsilonEquals(otherFirst2, epsilon)
                                    || last.epsilonEquals(otherFirst1, epsilon)
                                    || last.epsilonEquals(otherFirst2, epsilon)) {
                                other.reverse()
                                other.add(polygon)
                                makeNew = false
                                break
                            } else if (first.epsilonEquals(otherLast1, epsilon)
                                    || first.epsilonEquals(otherLast2, epsilon)
                                    || last.epsilonEquals(otherLast1, epsilon)
                                    || last.epsilonEquals(otherLast2, epsilon)) {
                                other.add(polygon)
                                makeNew = false
                                break
                            }
                        }
                        if (makeNew) {
                            coastPolygons.add(arrayListOf(polygon))
                        }
                    }
                }
            }
        }
        for (i in coastPolygons.size - 1 downTo 0) {
            val polygonList = coastPolygons[i]
            val first1 = polygonList.first().points.first()
            val first2 = polygonList.first().points.last()
            val last1 = polygonList.last().points.first()
            val last2 = polygonList.last().points.last()
            for (j in i - 1 downTo 0) {
                val other = coastPolygons[j]
                val otherFirst1 = other.first().points.first()
                val otherFirst2 = other.first().points.last()
                val otherLast1 = other.last().points.first()
                val otherLast2 = other.last().points.last()
                if (first1.epsilonEquals(otherFirst1, 0.0f)
                        || first1.epsilonEquals(otherFirst2, epsilon)
                        || first2.epsilonEquals(otherFirst1, epsilon)
                        || first2.epsilonEquals(otherFirst2, epsilon)) {
                    other.reverse()
                    other.addAll(polygonList)
                    coastPolygons.removeAt(i)
                    break
                } else if (last1.epsilonEquals(otherFirst1, epsilon)
                        || last1.epsilonEquals(otherFirst2, epsilon)
                        || last2.epsilonEquals(otherFirst1, epsilon)
                        || last2.epsilonEquals(otherFirst2, epsilon)) {
                    other.reverse()
                    polygonList.reverse()
                    other.addAll(polygonList)
                    coastPolygons.removeAt(i)
                    break
                } else if (first1.epsilonEquals(otherLast1, epsilon)
                        || first1.epsilonEquals(otherLast2, epsilon)
                        || first2.epsilonEquals(otherLast1, epsilon)
                        || first2.epsilonEquals(otherLast2, epsilon)) {
                    other.addAll(polygonList)
                    coastPolygons.removeAt(i)
                    break
                } else if (last1.epsilonEquals(otherLast1, epsilon)
                        || last1.epsilonEquals(otherLast2, epsilon)
                        || last2.epsilonEquals(otherLast1, epsilon)
                        || last2.epsilonEquals(otherLast2, epsilon)) {
                    polygonList.reverse()
                    other.addAll(polygonList)
                    coastPolygons.removeAt(i)
                    break
                }
            }
        }
        coastPolygons.filter { it.size >= 2 }.forEach {
            if (it.size == 2) {
                if (!it.first().points.last().epsilonEquals(it.last().points.first())) {
                    it[it.size - 1] = Polygon2F(it[it.size - 1].points.reversed(), false)
                }
            } else {
                for (i in 0..it.size - 2) {
                    if (i == 0) {
                        val current = it[i]
                        val next = it[i + 1]
                        val currentFirst = current.points.first()
                        val currentLast = current.points.last()
                        val nextFirst = next.points.first()
                        val nextLast = next.points.last()
                        if (currentLast.epsilonEquals(nextLast, epsilon)) {
                            it[i + 1] = Polygon2F(next.points.reversed(), false)
                        } else if (currentFirst.epsilonEquals(nextFirst, epsilon)) {
                            it[i] = Polygon2F(current.points.reversed(), false)
                        } else if (currentFirst.epsilonEquals(nextLast, epsilon)) {
                            it[i] = Polygon2F(current.points.reversed(), false)
                            it[i + 1] = Polygon2F(next.points.reversed(), false)
                        }
                    } else {
                        val next = it[i + 1]
                        val currentLast = it[i].points.last()
                        val nextFirst = next.points.first()
                        if (!currentLast.epsilonEquals(nextFirst, epsilon)) {
                            it[i + 1] = Polygon2F(next.points.reversed(), false)
                        }
                    }
                }
            }
        }
        coastPolygons.addAll(closedPolygons)
        val borderPolygons = ArrayList<Polygon2F>()
        val coastPoints = coastPolygons.flatMap { it.flatMap { it.points } }
        val potentialOutlets = ArrayList<Point2F>()
        val finalRiverPolygons = ArrayList<Polygon2F>()
        val finalMountainPolygons = ArrayList<Polygon2F>()
        borders.forEach {
            if (it.isNotEmpty()) {
                val edgeSegments = LineSegment2F.getConnectedEdgeSegments(it, epsilon)
                edgeSegments.forEach {
                    val polygon = Polygon2F.fromUnsortedEdges(it, null, true, epsilon)
                    val first = polygon.points.first()
                    val last = polygon.points.last()
                    var firstMatch = false
                    var lastMatch = false
                    val matches = ArrayList<Point2F>()
                    for (point in coastPoints) {
                        if (point.epsilonEquals(first, epsilon)) {
                            matches.add(point)
                            firstMatch = true
                        } else if (point.epsilonEquals(last, epsilon)) {
                            matches.add(point)
                            lastMatch = true
                        }
                        if (firstMatch && lastMatch) {
                            break
                        }
                    }
                    if (firstMatch && lastMatch) {
                        val poly1 = Polygon2F(polygon.points.subList(0, polygon.points.size / 2 + 1), false)
                        val poly2 = Polygon2F(polygon.points.subList(polygon.points.size / 2, polygon.points.size), false)
                        finalRiverPolygons.add(poly1)
                        potentialOutlets.add(poly1.points.first())
                        potentialOutlets.add(poly1.points.last())
                        finalMountainPolygons.add(poly2)
                    } else {
                        borderPolygons.add(polygon)
                        potentialOutlets.addAll(matches)
                    }
                }
            }
        }
        val riverPolygons = ArrayList<Polygon2F>()
        val mountainPolygons = ArrayList<Polygon2F>()

        borderPolygons.forEachIndexed { i, it ->
            if ((i + 1) % 2 == 0) {
                mountainPolygons.add(it)
            } else {
                riverPolygons.add(it)
            }
        }

        val isolatedRivers = ArrayList<Polygon2F>()

        riverPolygons.forEach {
            val first = it.points.first()
            val last = it.points.last()
            var hasOutlet = false
            for (i in 0..potentialOutlets.size - 1) {
                val point = potentialOutlets[i]
                if (point.epsilonEquals(first, epsilon)) {
                    potentialOutlets.add(last)
                    hasOutlet = true
                    break
                } else if (point.epsilonEquals(last, epsilon)) {
                    potentialOutlets.add(first)
                    hasOutlet = true
                    break
                }
            }
            if (!hasOutlet) {
                isolatedRivers.add(it)
            }
        }

        var isolatedRiverSize: Int
        do {
            isolatedRiverSize = isolatedRivers.size
            for (i in isolatedRivers.size - 1 downTo 0) {
                val river = isolatedRivers[i]
                val first = river.points.first()
                val last = river.points.last()
                for (j in 0..potentialOutlets.size - 1) {
                    val point = potentialOutlets[j]
                    if (point.epsilonEquals(first, epsilon)) {
                        potentialOutlets.add(last)
                        isolatedRivers.removeAt(i)
                        riverPolygons.add(river)
                        break
                    } else if (point.epsilonEquals(last, epsilon)) {
                        potentialOutlets.add(first)
                        isolatedRivers.removeAt(i)
                        riverPolygons.add(river)
                        break
                    }
                }
            }
        } while (isolatedRiverSize != isolatedRivers.size)

        var mountainToRiverCount = isolatedRivers.size
        riverPolygons.removeAll(isolatedRivers)
        mountainPolygons.addAll(isolatedRivers)

        if (mountainToRiverCount > 0) {
            val randomMountainOrder = ArrayList(mountainPolygons)
            Collections.shuffle(randomMountainOrder, random)
            while (mountainToRiverCount > 0) {
                val nextCandidate = randomMountainOrder.removeAt(randomMountainOrder.size - 1)
                val first = nextCandidate.points.first()
                val last = nextCandidate.points.last()
                for (i in 0..potentialOutlets.size - 1) {
                    val point = potentialOutlets[i]
                    if (point.epsilonEquals(first, epsilon)) {
                        potentialOutlets.add(last)
                        mountainToRiverCount--
                        riverPolygons.add(nextCandidate)
                        mountainPolygons.remove(nextCandidate)
                        break
                    } else if (point.epsilonEquals(last, epsilon)) {
                        potentialOutlets.add(first)
                        mountainToRiverCount--
                        riverPolygons.add(nextCandidate)
                        mountainPolygons.remove(nextCandidate)
                        break
                    }
                }
            }
        }

        finalRiverPolygons.addAll(riverPolygons)
        finalMountainPolygons.addAll(mountainPolygons)

        return Triple(coastPolygons, finalRiverPolygons, finalMountainPolygons)
    }

    private fun addBorderEdge(vertices: Vertices, borderEdges: ArrayList<LineSegment2F>, id: Int, adjacentId: Int, useTriangles: Boolean) {
        val cell1 = vertices[id].cell
        val cell2 = vertices[adjacentId].cell
        val edge = cell1.sharedEdge(cell2, useTriangles)
        if (edge != null) {
            borderEdges.add(edge)
        }
    }

    private fun buildEdges(inPoints: List<Point2F>, isClosed: Boolean, moveToFirst: Boolean): List<Point2F> {
        val outPoints = ArrayList<Point2F>()
        val start = inPoints.first()
        if (moveToFirst) {
            outPoints.add(start)
        }
        for (i in if (isClosed) 1..inPoints.size else 1..inPoints.size - 1) {
            val id = i % inPoints.size
            val lastId = i - 1
            val lastPoint = inPoints[lastId]
            val point = inPoints[id]
            if (i == 1 && moveToFirst) {
                outPoints[0] = lastPoint
            }
            outPoints.add(point)
        }
        return outPoints
    }

    private fun getCurvePoints(points: List<Point2F>, isClosed: Boolean, segmentSize: Float, smoothing: Float): List<Point2F> {

        val newPoints = ArrayList<Vector2F>()
        val copyPoints = ArrayList(points)

        val firstPoint = copyPoints.first()
        if (isClosed) {
            copyPoints.add(firstPoint)
        }

        newPoints.add(Vector2F(firstPoint.x, (1.0f - firstPoint.y)))
        for (i in 1..copyPoints.size - 1) {

            val lastPoint = copyPoints[i - 1]
            val thisPoint = copyPoints[i]

            val vector = thisPoint - lastPoint
            val length = vector.length
            if (length > segmentSize) {
                val segments = Math.ceil(length / segmentSize.toDouble()).toInt()
                val offset = vector / segments.toFloat()
                (1..segments - 1)
                        .map { lastPoint + (offset * it.toFloat()) }
                        .mapTo(newPoints) { Vector2F(it.x, (1.0f - it.y)) }
            }
            newPoints.add(Vector2F(thisPoint.x, (1.0f - thisPoint.y)))
        }

        val newPoints2 = newPoints.mapTo(ArrayList<Vector2F>(newPoints.size)) { Vector2F(it.a, it.b) }
        var output: MutableList<Vector2F> = newPoints2
        var input: MutableList<Vector2F>
        var size = newPoints.size
        val smoothFactor = Math.round(smoothing * 15).coerceIn(0, 15) + 9
        (1..smoothFactor).forEach { iteration ->
            input = if (iteration % 2 == 0) {
                output = newPoints
                newPoints2
            } else {
                output = newPoints2
                newPoints
            }
            if (iteration % 5 == 0) {
                for (i in if (isClosed) size - 2 downTo 0 step 2 else size - 3 downTo 1 step 2) {
                    input.removeAt(i)
                    output.removeAt(i)
                }
                size = input.size
            }
            for (i in if (isClosed) 1..size else 1..size - 2) {
                val initialPosition = input[i % size]
                var affectingPoint = input[i - 1]
                var x = affectingPoint.a
                var y = affectingPoint.b
                affectingPoint = input[(i + 1) % size]
                x += affectingPoint.a
                y += affectingPoint.b
                x *= 0.325f
                y *= 0.325f
                x += initialPosition.a * 0.35f
                y += initialPosition.b * 0.35f
                val nextPosition = output[i % size]
                nextPosition.a = x
                nextPosition.b = y
            }
        }

        return output.map { Point2F(it.a, 1.0f - it.b) }
    }

    fun generateWaterFlows(parameterSet: ParameterSet, regionSplines: RegionSplines, biomeGraph: Graph, biomeMask: Matrix<Byte>, biomes: List<Biome>, flowGraphSmall: Graph, flowGraphMedium: Graph, flowGraphLarge: Graph, executor: ExecutorService, mapScale: Int): Pair<TextureId, TextureId> {
        return timeIt("generated water flow in") {
            generateWaterFlows(Random(parameterSet.regionsSeed), regionSplines, biomeGraph, biomeMask, flowGraphSmall, flowGraphMedium, flowGraphLarge, executor, 4096, mapScale, biomes)
        }
    }
}
