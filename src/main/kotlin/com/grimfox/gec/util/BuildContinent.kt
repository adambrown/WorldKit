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
import java.util.concurrent.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

object BuildContinent {

    data class RegionIterationParameters(
            var stride: Int,
            var landPercent: Float,
            var minPerturbation: Float,
            var maxIterations: Int,
            var protectedInset: Float,
            var protectedRadius: Float,
            var minRegionSize: Float,
            var largeIsland: Float,
            var smallIsland: Float)

    data class RegionParameters(
            var regionsSeed: Long = System.currentTimeMillis(),
            var edgeDetailScale: Int = 4,
            var stride: Int = 7,
            var regionCount: Int = 8,
            var connectedness: Float = 0.11f,
            var regionSize: Float = 0.035f,
            var initialReduction: Int = 7,
            var regionPoints: Int = 2,
            var maxRegionTries: Int = 50,
            var maxIslandTries: Int = 500,
            var islandDesire: Int = 1,
            var parameters: ArrayList<RegionIterationParameters> = arrayListOf(
                    RegionIterationParameters(24, 0.40f, 0.1f, 8, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f),
                    RegionIterationParameters(32, 0.40f, 0.2f, 6, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f),
//                    Parameters(80, 0.39f, 0.05f, 3, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f)
                    RegionIterationParameters(64, 0.40f, 0.1f, 4, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f),
                    RegionIterationParameters(128, 0.39f, 0.05f, 3, 0.1f, 0.05f, 0.035f, 2.0f, 0.01f)
//                    Parameters(140, 0.39f, 0.03f, 2, 0.1f, 0.05f, 0.035f, 2.0f, 0.01f)
//                    RegionIterationParameters(256, 0.39f, 0.05f, 3, 0.1f, 0.05f, 0.035f, 2.0f, 0.01f)
            ),
            var currentIteration: Int = 0)

    data class BiomeParameters(
            var biomesSeed: Long = System.currentTimeMillis(),
            var biomesMapScale: Int = 4,
            var biomes: List<Int> = arrayListOf(0))

    fun generateRegions(parameterSet: RegionParameters = RegionParameters(), executor: ExecutorService): Pair<Graph, ByteArrayMatrix> {
        return timeIt("generated regions in") {
            val random = Random(parameterSet.regionsSeed)
            var (graph, regionMask) = buildRegions(parameterSet)
            parameterSet.parameters.forEachIndexed { i, parameters ->
                parameterSet.currentIteration = i
                val localGraph = generateGraph(parameters.stride, random.nextLong(), 0.8)
                val (mask, water, borderPoints) = applyMask(localGraph, graph, regionMask, executor)
                regionMask = mask
                refineCoastline(localGraph, random, regionMask, water, borderPoints, parameters)
                graph = localGraph
            }
            Pair(graph, regionMask)
        }
    }

    fun buildBiomeMaps(executor: ExecutorService, randomSeed: Long, inputGraph: Graph, biomeCount: Int, biomeScale: Int): Pair<Graph, ByteArrayMatrix> {
        val random = Random(randomSeed)
        val randomSeeds = Array(3) { random.nextLong() }
        val biomeGraphSmallFuture = executor.call {
            val innerRandom = Random(randomSeeds[0])
            val graph = Graphs.generateGraph(biomeScale, randomSeeds[0], 0.98)
            val mask = ByteArrayMatrix(graph.stride!!) { ((Math.abs(innerRandom.nextInt()) % biomeCount) + 1).toByte() }
            graph to mask
        }
        val biomeGraphMidFuture = executor.call {
            val graph = Graphs.generateGraph(28, randomSeeds[1], 0.98)
            val vertices = graph.vertices
            val (parentGraph, parentMask) = biomeGraphSmallFuture.value
            val mask = ByteArrayMatrix(graph.stride!!) { i ->
                val point = vertices[i].point
                parentMask[parentGraph.getClosestPoint(point, parentGraph.getClosePoints(point, 2))]
            }
            graph to mask
        }
        val biomeGraphHighFuture = executor.call {
            val graph = Graphs.generateGraph(72, randomSeeds[2], 0.88)
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

    data class RegionSplines(
            val hasCustomizations: Boolean,
            val coastEdges: List<Pair<List<LineSegment2F>, List<List<LineSegment2F>>>>,
            val coastPoints: List<Pair<List<Point2F>, List<List<Point2F>>>>,
            val riverOrigins: List<List<Point2F>>,
            val riverEdges: List<List<LineSegment2F>>,
            val riverPoints: List<List<Point2F>>,
            val mountainOrigins: List<List<Point2F>>,
            val mountainEdges: List<List<LineSegment2F>>,
            val mountainPoints: List<List<Point2F>>,
            val ignoredOrigins: List<List<Point2F>> = listOf(),
            val ignoredEdges: List<List<LineSegment2F>> = listOf(),
            val ignoredPoints: List<List<Point2F>> = listOf(),
            val deletedOrigins: List<List<Point2F>> = listOf(),
            val deletedEdges: List<List<LineSegment2F>> = listOf(),
            val deletedPoints: List<List<Point2F>> = listOf(),
            val customRiverEdges: List<List<LineSegment2F>> = listOf(),
            val customRiverPoints: List<List<Point2F>> = listOf(),
            val customMountainEdges: List<List<LineSegment2F>> = listOf(),
            val customMountainPoints: List<List<Point2F>> = listOf(),
            val customIgnoredEdges: List<List<LineSegment2F>> = listOf(),
            val customIgnoredPoints: List<List<Point2F>> = listOf()) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as RegionSplines
            if (hasCustomizations != other.hasCustomizations) return false
            if (coastEdges != other.coastEdges) return false
            if (coastPoints != other.coastPoints) return false
            if (riverOrigins != other.riverOrigins) return false
            if (riverEdges != other.riverEdges) return false
            if (riverPoints != other.riverPoints) return false
            if (mountainOrigins != other.mountainOrigins) return false
            if (mountainEdges != other.mountainEdges) return false
            if (mountainPoints != other.mountainPoints) return false
            if (ignoredOrigins != other.ignoredOrigins) return false
            if (ignoredEdges != other.ignoredEdges) return false
            if (ignoredPoints != other.ignoredPoints) return false
            if (deletedOrigins != other.deletedOrigins) return false
            if (deletedEdges != other.deletedEdges) return false
            if (deletedPoints != other.deletedPoints) return false
            if (customRiverEdges != other.customRiverEdges) return false
            if (customRiverPoints != other.customRiverPoints) return false
            if (customMountainEdges != other.customMountainEdges) return false
            if (customMountainPoints != other.customMountainPoints) return false
            if (customIgnoredEdges != other.customIgnoredEdges) return false
            if (customIgnoredPoints != other.customIgnoredPoints) return false
            return true
        }

        override fun hashCode(): Int {
            var result = hasCustomizations.hashCode()
            result = 31 * result + coastEdges.hashCode()
            result = 31 * result + coastPoints.hashCode()
            result = 31 * result + riverOrigins.hashCode()
            result = 31 * result + riverEdges.hashCode()
            result = 31 * result + riverPoints.hashCode()
            result = 31 * result + mountainOrigins.hashCode()
            result = 31 * result + mountainEdges.hashCode()
            result = 31 * result + mountainPoints.hashCode()
            result = 31 * result + ignoredOrigins.hashCode()
            result = 31 * result + ignoredEdges.hashCode()
            result = 31 * result + ignoredPoints.hashCode()
            result = 31 * result + deletedOrigins.hashCode()
            result = 31 * result + deletedEdges.hashCode()
            result = 31 * result + deletedPoints.hashCode()
            result = 31 * result + customRiverEdges.hashCode()
            result = 31 * result + customRiverPoints.hashCode()
            result = 31 * result + customMountainEdges.hashCode()
            result = 31 * result + customMountainPoints.hashCode()
            result = 31 * result + customIgnoredEdges.hashCode()
            result = 31 * result + customIgnoredPoints.hashCode()
            return result
        }
    }

    fun generateRegionSplines(random: Random, regionGraph: Graph, regionMask: Matrix<Byte>, mapScale: Int): RegionSplines {
        val smoothing = (1.0f - (mapScale / 20.0f)).coerceIn(0.0f, 1.0f)
        val water = LinkedHashSet<Int>(regionGraph.vertices.size)
        val land = LinkedHashSet<Int>(regionGraph.vertices.size)
        var regionCount = 1
        for (i in 0..regionGraph.vertices.size - 1) {
            val maskValue = regionMask[i]
            if (maskValue < 1) {
                water.add(i)
            } else {
                land.add(i)
                if (maskValue > regionCount) {
                    regionCount = maskValue.toInt()
                }
            }
        }

        val borderSegments = findAllBorderSegments(random, regionGraph, regionMask, regionCount, land, water)

        val coastEdges = ArrayList<Pair<List<LineSegment2F>, List<List<LineSegment2F>>>>()
        val coastPoints = ArrayList<Pair<List<Point2F>, List<List<Point2F>>>>()
        borderSegments.first.forEach { pair ->
            val outsideCoast = pair.first
            val outsideCoastPoints = buildClosedEdges(outsideCoast, smoothing)
            val insideCoasts = pair.second
            val insideCoastPoints = insideCoasts.map { buildClosedEdges(it, smoothing) }
            coastPoints.add(outsideCoastPoints to insideCoastPoints)
            coastEdges.add((1..outsideCoastPoints.size - 1).map { LineSegment2F(outsideCoastPoints[it - 1], outsideCoastPoints[it]) } to insideCoastPoints.map { insideCoast -> (1..insideCoast.size - 1).map { LineSegment2F(insideCoast[it - 1], insideCoast[it]) } })
        }

        val riverOrigins = ArrayList<List<Point2F>>()
        val riverEdges = ArrayList<List<LineSegment2F>>()
        val riverPoints = ArrayList<List<Point2F>>()
        borderSegments.second.forEach {
            riverOrigins.add(it.points)
            val points = if (it.isClosed) {
                buildClosedEdges(listOf(it), smoothing)
            } else {
                buildOpenEdges(it, smoothing)
            }
            riverPoints.add(points)
            riverEdges.add((1..points.size - 1).mapTo(ArrayList()) { LineSegment2F(points[it - 1], points[it]) })
        }

        val mountainOrigins = ArrayList<List<Point2F>>()
        val mountainEdges = ArrayList<List<LineSegment2F>>()
        val mountainPoints = ArrayList<List<Point2F>>()
        borderSegments.third.forEach {
            mountainOrigins.add(it.points)
            val points = if (it.isClosed) {
                buildClosedEdges(listOf(it), smoothing)
            } else {
                buildOpenEdges(it, smoothing)
            }
            mountainPoints.add(points)
            mountainEdges.add((1..points.size - 1).mapTo(ArrayList()) { LineSegment2F(points[it - 1], points[it]) })
        }

        return RegionSplines(false, coastEdges, coastPoints, riverOrigins, riverEdges, riverPoints, mountainOrigins, mountainEdges, mountainPoints)
    }

    fun buildOpenEdges(polygon: Polygon2F, smoothing: Float): List<Point2F> {
        return buildEdges(getCurvePoints(polygon.points, false, 0.00035f, smoothing), false, true)
    }

    fun buildOpenEdges(polygon: Polygon2F, smoothing: Int): MutableList<Point2F> {
        return buildEdges(getCurvePoints(polygon.points, false, 0.00035f, smoothing), false, true)
    }

    fun buildClosedEdges(polygons: List<Polygon2F>, smoothing: Float): List<Point2F> {
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

    fun buildClosedEdges(polygons: List<Polygon2F>, smoothing: Int): MutableList<Point2F> {
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

    private class Body(val ids: LinkedHashSet<Int>, val children: List<Body>, var coasts: Array<ArrayList<LineSegment2F>>, var lakes: Array<ArrayList<LineSegment2F>>, var borders: Array<ArrayList<LineSegment2F>>)

    private fun findNestedBodies(graph: Graph, currentBodies: List<LinkedHashSet<Int>>, currentPool: ArrayList<LinkedHashSet<Int>>, nextPool: ArrayList<LinkedHashSet<Int>>, regionCount: Int): List<Body> {
        return currentBodies.mapTo(ArrayList<Body>()) { currentBody ->
            Body(currentBody, findNestedBodies(graph, findChildren(graph, currentBody, currentPool), nextPool, currentPool, regionCount), Array(regionCount) { ArrayList<LineSegment2F>() }, Array(regionCount) { ArrayList<LineSegment2F>() }, Array(regionCount * regionCount) { ArrayList<LineSegment2F>() })
        }
    }

    fun findAllBorderSegments(random: Random, graph: Graph, regionMask: Matrix<Byte>, regionCount: Int, land: LinkedHashSet<Int>, water: LinkedHashSet<Int>): Triple<ArrayList<Pair<ArrayList<Polygon2F>, ArrayList<ArrayList<Polygon2F>>>>, ArrayList<Polygon2F>, ArrayList<Polygon2F>> {
        val borderPoints = graph.vertices.asSequence().filter { it.cell.isBorder }.map { it.id }.toList()
        val waterBodies = graph.getConnectedBodies(water)
        val landBodies = graph.getConnectedBodies(land)
        val oceanWaterBodies = waterBodies.filter { it.containsAny(borderPoints) }
        val lakeWaterBodies = ArrayList(waterBodies.filter { !oceanWaterBodies.contains(it) })
        val oceanWater = oceanWaterBodies.flatMapTo(LinkedHashSet<Int>()) { it }
        val ocean = findNestedBodies(graph, listOf(oceanWater), landBodies, lakeWaterBodies, regionCount).first()
        val vertices = graph.vertices
        val epsilon = 0.0f
        val bodiesToDraw = ArrayList<Body>()
        findBordersCoastsAndLakes(bodiesToDraw, ocean, regionMask, vertices, regionCount)
        val coasts = ArrayList<Pair<ArrayList<Polygon2F>, ArrayList<ArrayList<Polygon2F>>>>()
        val rivers = ArrayList<Polygon2F>()
        val mountains = ArrayList<Polygon2F>()
        bodiesToDraw.forEach { body ->
            val (coast, lakes, localRivers, localMountains) = extractBodyPolygons(body, epsilon, random)
            rivers.addAll(localRivers)
            mountains.addAll(localMountains)
            coasts.add(coast to lakes)
        }
        return Triple(coasts, rivers, mountains)
    }

    private fun extractBodyPolygons(body: Body, epsilon: Float, random: Random): Quadruple<ArrayList<Polygon2F>, ArrayList<ArrayList<Polygon2F>>, ArrayList<Polygon2F>, ArrayList<Polygon2F>> {
        val closedCoastPolygons = ArrayList<ArrayList<Polygon2F>>()
        val coastPolygons = ArrayList<ArrayList<Polygon2F>>()
        val closedLakePolygons = ArrayList<ArrayList<Polygon2F>>()
        val lakePolygons = ArrayList<ArrayList<Polygon2F>>()
        buildCoastPolygons(body.coasts, closedCoastPolygons, coastPolygons, epsilon)
        buildCoastPolygons(body.lakes, closedLakePolygons, lakePolygons, epsilon)
        orderAndOrientCoastPolygons(closedCoastPolygons, coastPolygons, epsilon)
        orderAndOrientCoastPolygons(closedLakePolygons, lakePolygons, epsilon)
        val borderPolygons = ArrayList<Polygon2F>()
        val coastPoints = coastPolygons.flatMap { it.flatMap { it.points } } + lakePolygons.flatMap { it.flatMap { it.points } }
        val potentialOutlets = ArrayList<Point2F>()
        val finalRiverPolygons = ArrayList<Polygon2F>()
        val finalMountainPolygons = ArrayList<Polygon2F>()
        body.borders.forEach {
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
                        break
                    } else if (point.epsilonEquals(last, epsilon)) {
                        potentialOutlets.add(first)
                        isolatedRivers.removeAt(i)
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

        if (coastPolygons.size != 1) {
            throw RuntimeException("A single land body created ${coastPolygons.size} polygons. How is this possible.")
        }

        return Quadruple(coastPolygons.first(), lakePolygons, finalRiverPolygons, finalMountainPolygons)
    }

    private fun findBordersCoastsAndLakes(bodiesWithLakes: MutableList<Body>, enclosingWaterBody: Body, regionMask: Matrix<Byte>, vertices: Vertices, regionCount: Int) {
        enclosingWaterBody.children.forEach { body ->
            bodiesWithLakes.add(body)
            body.ids.forEach { id ->
                val region = regionMask[id]
                vertices.getAdjacentVertices(id).forEach { adjacentId ->
                    val adjacentRegion = regionMask[adjacentId]
                    if (adjacentRegion > region && body.ids.contains(adjacentId)) {
                        addBorderEdge(vertices, body.borders[(region - 1) * regionCount + adjacentRegion - 1], id, adjacentId, true)
                    } else if (enclosingWaterBody.ids.contains(adjacentId)) {
                        addBorderEdge(vertices, body.coasts[region - 1], id, adjacentId, true)
                    } else if (body.children.any { it.ids.contains(adjacentId) }) {
                        addBorderEdge(vertices, body.lakes[region - 1], id, adjacentId, true)
                    }
                }
            }
            body.children.forEach { enclosedWaterBody ->
                findBordersCoastsAndLakes(bodiesWithLakes, enclosedWaterBody, regionMask, vertices, regionCount)
            }
        }
    }

    private fun findChildren(graph: Graph, parent: LinkedHashSet<Int>, options: ArrayList<LinkedHashSet<Int>>): ArrayList<LinkedHashSet<Int>> {
        return (options.size - 1 downTo 0)
                .filter { hasBorder(graph, parent, options[it]) }
                .mapTo(ArrayList<LinkedHashSet<Int>>()) { options.removeAt(it) }
    }

    private fun hasBorder(graph: Graph, parent: LinkedHashSet<Int>, option: LinkedHashSet<Int>): Boolean {
        val vertices = graph.vertices
        option.forEach { id ->
            vertices.getAdjacentVertices(id)
                    .asSequence()
                    .filter { parent.contains(it) }
                    .forEach { return true }
        }
        return false
    }

    private fun orderAndOrientCoastPolygons(closedCoastPolygons: ArrayList<ArrayList<Polygon2F>>, coastPolygons: ArrayList<ArrayList<Polygon2F>>, epsilon: Float) {
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
                if (first1.epsilonEquals(otherFirst1, epsilon)
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
        coastPolygons.addAll(closedCoastPolygons)
    }

    private fun buildCoastPolygons(coasts: Array<ArrayList<LineSegment2F>>, closedCoastPolygons: ArrayList<ArrayList<Polygon2F>>, coastPolygons: ArrayList<ArrayList<Polygon2F>>, epsilon: Float) {
        coasts.forEach {
            if (it.isNotEmpty()) {
                val edgeSegments = LineSegment2F.getConnectedEdgeSegments(it, epsilon)
                edgeSegments.forEach {
                    val polygon = Polygon2F.fromUnsortedEdges(it, null, true, epsilon)
                    if (polygon.isClosed) {
                        closedCoastPolygons.add(arrayListOf(polygon))
                    } else {
                        val first = polygon.points.first()
                        val last = polygon.points.last()
                        var makeNew = true
                        for (other in coastPolygons) {
                            val otherFirst1 = other.first().points.first()
                            val otherFirst2 = other.first().points.last()
                            val otherLast1 = other.last().points.first()
                            val otherLast2 = other.last().points.last()
                            if (first.epsilonEquals(otherFirst1, epsilon)
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
    }

    private fun addBorderEdge(vertices: Vertices, borderEdges: ArrayList<LineSegment2F>, id: Int, adjacentId: Int, useTriangles: Boolean) {
        val cell1 = vertices[id].cell
        val cell2 = vertices[adjacentId].cell
        val edge = cell1.sharedEdge(cell2, useTriangles)
        if (edge != null) {
            borderEdges.add(edge)
        }
    }

    private fun addBorderEdge(vertices: Vertices, borderEdges: ArrayList<Pair<Int, LineSegment2F>>, body: Int, id: Int, adjacentId: Int, useTriangles: Boolean) {
        val cell1 = vertices[id].cell
        val cell2 = vertices[adjacentId].cell
        val edge = cell1.sharedEdge(cell2, useTriangles)
        if (edge != null) {
            borderEdges.add(body to edge)
        }
    }

    private fun buildEdges(inPoints: List<Point2F>, isClosed: Boolean, moveToFirst: Boolean): MutableList<Point2F> {
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
        val smoothFactor = Math.round(smoothing * 15).coerceIn(0, 15) + 9
        return getCurvePoints(points, isClosed, segmentSize, smoothFactor)
    }

    private fun getCurvePoints(points: List<Point2F>, isClosed: Boolean, segmentSize: Float, smoothFactor: Int): List<Point2F> {

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

        if (size > 3) {
            (1..smoothFactor).forEach { iteration ->
                input = if (iteration % 2 == 0) {
                    output = newPoints
                    newPoints2
                } else {
                    output = newPoints2
                    newPoints
                }
                if (iteration % 5 == 0) {
                    if (size > 3) {
                        for (i in if (isClosed) size - 2 downTo 0 step 2 else size - 3 downTo 1 step 2) {
                            input.removeAt(i)
                            output.removeAt(i)
                        }
                        size = input.size
                    }
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
        }
        return output.map { Point2F(it.a, 1.0f - it.b) }
    }

    fun generateWaterFlows(
            parameterSet: RegionParameters,
            regionSplines: RegionSplines,
            biomeGraph: Graph,
            biomeMask: Matrix<Byte>,
            biomes: List<Biome>,
            flowGraphSmall: Graph,
            flowGraphMedium: Graph,
            flowGraphLarge: Graph,
            executor: ExecutorService,
            mapScale: Int,
            customElevationPowerMap: TextureId,
            customStartingHeightsMap: TextureId,
            customSoilMobilityMap: TextureId,
            canceled: Reference<Boolean>): Pair<TextureId, TextureId> {
        return timeIt("generated water flow in") {
            generateWaterFlows(
                    random = Random(parameterSet.regionsSeed),
                    regionSplines = regionSplines,
                    biomeGraph = biomeGraph,
                    biomeMask = biomeMask,
                    flowGraphSmall = flowGraphSmall,
                    flowGraphMedium = flowGraphMedium,
                    flowGraphLarge = flowGraphLarge,
                    executor = executor,
                    outputWidth = 4096,
                    mapScale = mapScale,
                    biomes = biomes,
                    customElevationPowerMap = customElevationPowerMap,
                    customStartingHeightsMap = customStartingHeightsMap,
                    customSoilMobilityMap = customSoilMobilityMap,
                    canceled = canceled)
        }
    }
}
