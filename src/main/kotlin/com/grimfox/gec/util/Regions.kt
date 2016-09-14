package com.grimfox.gec.util

import com.grimfox.gec.model.ArrayListMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.Point
import com.grimfox.gec.util.Mask.reverseMask
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils.generatePoints
import java.lang.Math.max
import java.lang.Math.min
import java.util.*

object Regions {

    private data class RegionGrowth(val regionPoints: HashSet<Int>, var growSet: HashSet<Int>, var iterateSet: HashSet<Int>)

    private class Region(val ids: HashSet<Int>, var area: Float) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false
            other as Region
            return ids.equals(other.ids)
        }

        override fun hashCode(): Int {
            return ids.hashCode()
        }
    }

    fun buildRegions(stride: Int, random: Random, count: Int): Pair<Graph, Matrix<Int>> {
        var bestGraphValue = Float.MIN_VALUE
        var bestPair: Pair<Graph, Matrix<Int>>? = null
        var tries = 0
        while (tries < 1000) {
            val virtualWidth = 100000.0f
            val graph = buildGraph(stride, virtualWidth, generatePoints(stride, virtualWidth, random))
            val vertices = ArrayList(graph.vertices.toList())
            val interiorVertices = hashMapOf(*vertices.filter { !it.cell.isBorder }.map { Pair(it.id, it) }.toTypedArray())
            val possibleRegions = pickStartRegions(graph, interiorVertices, pickStartCells(graph, random, interiorVertices, count))
            val acceptableConnectedness = 0.132f
            var bestValue = Float.MIN_VALUE
            var bestValueId = -1
            var fixerValue = Float.MIN_VALUE
            var fixerId = 0
            possibleRegions.forEachIndexed { i, regionSet ->
                var minConnectedness = Float.MAX_VALUE
                var minPoints = Int.MAX_VALUE
                var areaSum = 0.0f
                val maxSpread = regionSet.map { region ->
                    var sumX = 0.0f
                    var sumY = 0.0f
                    val points = region.ids.map {
                        val cell = interiorVertices[it]!!.cell
                        val connectedness = calculateConnectedness(graph, interiorVertices, region, cell)
                        if (region.ids.size > 1 && connectedness < minConnectedness) {
                            minConnectedness = connectedness
                        }
                        val point = cell.vertex.point
                        sumX += point.x
                        sumY += point.y
                        point
                    }
                    if (region.ids.size < minPoints) {
                        minPoints = region.ids.size
                    }
                    val center = Point(sumX / region.ids.size, sumY / region.ids.size)
                    areaSum += region.area
                    points.map { it.distanceSquaredTo(center) }.max()!!
                }.max()!!
                val avgArea = areaSum / regionSet.size
                var maxDeviation = Float.MIN_VALUE
                regionSet.forEach {
                    val deviation = Math.abs(avgArea - it.area)
                    if (deviation > maxDeviation) {
                        maxDeviation = deviation
                    }
                }
                val setValue = minConnectedness * (1.0f - maxSpread) * (1.0f - maxDeviation)
                if (minConnectedness > acceptableConnectedness && minPoints > 2 && setValue > bestValue) {
                    bestValue = setValue
                    bestValueId = i
                }
                if (setValue > fixerValue) {
                    fixerValue = setValue
                    fixerId = i
                }
            }
            if (bestValueId < 0) {
                if (fixerValue > bestGraphValue) {
                    bestGraphValue = fixerValue
                    bestPair = Pair(graph, ArrayListMatrix(graph.stride) { findRegionId(possibleRegions[fixerId], it) })
                }
            } else {
                return Pair(graph, ArrayListMatrix(graph.stride) { findRegionId(possibleRegions[bestValueId], it) })
            }
            tries++
        }
        return bestPair!!
    }

    private fun calculateConnectedness(graph: Graph, interiorVertices: HashMap<Int, Vertex>, region: Region, cell: Cell): Float {
        val sharedEdges = HashSet(region.ids.filter { cell.id != it }.map {
            cell.sharedEdge(interiorVertices[it]!!.cell)
        }.filterNotNull())
        return graph.getConnectedEdgeSegments(sharedEdges).map { it.map { it.length }.sum() }.min() ?: 0.0f
    }

    private fun pickStartRegions(graph: Graph, interiorVertices: HashMap<Int, Vertex>, startCellSets: HashSet<HashSet<Int>>): ArrayList<ArrayList<Region>> {
        val possibilities = HashSet<HashSet<Region>>()
        startCellSets.forEach {
            possibilities.add(pickStartRegions(graph, interiorVertices, it))
        }
        return ArrayList(possibilities.map { ArrayList(it) })
    }

    private fun pickStartRegions(graph: Graph, interiorVertices: HashMap<Int, Vertex>, startCells: HashSet<Int>): HashSet<Region> {
        val canPick = interiorVertices.map { it.key }.toHashSet()
        canPick.removeAll(startCells)
        val regionQueue = PriorityQueue<Region>(startCells.size) { r1: Region, r2: Region ->
            r1.area.compareTo(r2.area)
        }
        regionQueue.addAll(startCells.map { Region(hashSetOf(it), interiorVertices[it]!!.cell.area) })
        val regions = HashSet<Region>(startCells.size)
        regions.addAll(regionQueue)
        while (canPick.isNotEmpty() && regionQueue.isNotEmpty()) {
            val smallestRegion = regionQueue.remove()
            val candidates = smallestRegion.ids.flatMap { interiorVertices[it]!!.adjacentVertices.map { it.id } }.toSet().filter { canPick.contains(it) }.sortedByDescending {
                calculateConnectedness(graph, interiorVertices, smallestRegion, interiorVertices[it]!!.cell)
            }
            if (candidates.isNotEmpty()) {
                val picked = candidates.first()
                canPick.remove(picked)
                smallestRegion.ids.add(picked)
                smallestRegion.area += interiorVertices[picked]!!.cell.area
                regionQueue.add(smallestRegion)
            }
        }
        return regions
    }

    private fun pickStartCells(graph: Graph, random: Random, interiorVertices: HashMap<Int, Vertex>, count: Int): HashSet<HashSet<Int>> {
        val validStarts = HashSet<HashSet<Int>>()
        val interiorCellIds = ArrayList(interiorVertices.map { it.value.cell }.sortedByDescending { it.area }.map { it.id })
        val seeds = ArrayList((0..interiorCellIds.size - 1).toList())
        Collections.shuffle(seeds, random)
        var maxCount = 0
        for (i in 0..interiorCellIds.size - 1) {
            val seed = seeds[i]
            val picks = HashSet<Int>()
            val canPick = HashSet(interiorCellIds)
            var pick = interiorCellIds[seed]
            while (canPick.isNotEmpty()) {
                picks.add(pick)
                canPick.remove(pick)
                val closePoints = graph.getClosePointDegrees(pick, 2)
                canPick.removeAll(closePoints[0])
                val options = ArrayList(closePoints[1])
                var hasNewPick = false
                for (j in 0..options.size - 1) {
                    val potentialPick = options[j]
                    if (canPick.contains(potentialPick)) {
                        pick = potentialPick
                        hasNewPick = true
                        break
                    }
                }
                if (!hasNewPick && canPick.isNotEmpty()) {
                    pick = canPick.first()
                }
            }
            if (picks.size > maxCount) {
                maxCount = picks.size
                validStarts.clear()
            }
            if (picks.size == maxCount) {
                validStarts.addAll(getSubsets(picks, Math.min(count, maxCount)))
            }
        }
        if (maxCount >= count) {
            return validStarts
        }
        if (interiorCellIds.size < count) {
            throw IllegalStateException("not enough cells for number of regions")
        } else {
            validStarts.forEach {
                while (it.size < count) {
                    it.add(interiorCellIds[random.nextInt(interiorCellIds.size)])
                }
            }
            return validStarts
        }
    }


    private fun findRegionId(regions: ArrayList<Region>, id: Int): Int {
        regions.forEachIndexed { i, region ->
            if (region.ids.contains(id)) {
                return i + 1
            }
        }
        return 0
    }

    fun getSubsets(superSet: HashSet<Int>, subsetSize: Int): ArrayList<HashSet<Int>> {
        return getSubsets(superSet.toList(), subsetSize, 0, HashSet<Int>(), ArrayList())
    }

    private fun getSubsets(superSet: List<Int>, subsetSize: Int, index: Int, current: HashSet<Int>, subsets: ArrayList<HashSet<Int>>): ArrayList<HashSet<Int>> {
        if (current.size == subsetSize) {
            subsets.add(HashSet(current))
            return subsets
        }
        if (index == superSet.size) {
            return subsets
        }
        val x = superSet[index]
        current.add(x)
        getSubsets(superSet, subsetSize, index + 1, current, subsets)
        current.remove(x)
        getSubsets(superSet, subsetSize, index + 1, current, subsets)
        return subsets
    }























































    fun buildRegions3(graph: Graph, random: Random, waterMask: HashSet<Int>, count: Int): Matrix<Int> {
        val regionGraph = buildRandomGraph(random, 10)
        val reversedMask = reverseMask(regionGraph, graph, waterMask, true)
        val simplifiedWaterMask = buildSimplifiedWaterMask(regionGraph, reversedMask)
        var maxCellWeight = 0
        reversedMask.forEach { i, hashSet ->
            if (hashSet.size > maxCellWeight) {
                maxCellWeight = hashSet.size
            }
        }
        val simplifiedIdealPointsPerRegion = Math.round(((graph.vertices.size - waterMask.size) / count.toDouble())).toInt()
        val simplifiedMaxPointsPerRegion = simplifiedIdealPointsPerRegion + maxCellWeight
        val normalizationMaxPointsPerRegion = Math.ceil((regionGraph.vertices.size - simplifiedWaterMask.size) / count.toDouble()).toInt()

        regionGraph.useVirtualConnections = true
        val stride = regionGraph.stride
        val startPoints = pickStartingPoints(regionGraph, simplifiedWaterMask, random, count)
        val regions = ArrayList(startPoints.map { hashSetOf(it) })
        val simplifiedLandPoints = findLandPoints(regionGraph, simplifiedWaterMask)
        connectIslands(regionGraph, simplifiedLandPoints)
        val unclaimedLand = HashSet(simplifiedLandPoints)
        unclaimedLand.removeAll(startPoints)
        buildUpRegions(regionGraph, unclaimedLand, regions)
        equalizeRegions2(regionGraph, simplifiedLandPoints, simplifiedMaxPointsPerRegion, simplifiedIdealPointsPerRegion, random, regions, reversedMask)
        normalizeRegionShapes(regionGraph, simplifiedLandPoints, normalizationMaxPointsPerRegion, stride, random, regions, 8)
        equalizeRegions2(regionGraph, simplifiedLandPoints, simplifiedMaxPointsPerRegion, simplifiedIdealPointsPerRegion, random, regions, reversedMask)
        forceGiveUpDisconnectedLand2(regionGraph, simplifiedLandPoints, regions, unclaimedLand)
        forceClaimUnwantedLand(regionGraph, random, regions, unclaimedLand)
        normalizeRegionShapes(regionGraph, simplifiedLandPoints, normalizationMaxPointsPerRegion, stride, random, regions, 4)
        equalizeRegions(regionGraph, simplifiedLandPoints, simplifiedMaxPointsPerRegion, random, regions)
        for (i in 0..regions.size - 1) {
            regions[i] = regions[i].flatMap { reversedMask[it] ?: emptyList<Int>() }.toHashSet()
        }
        graph.useVirtualConnections = true
        val landPoints = findLandPoints(graph, waterMask)
        val maxPointsPerRegion = Math.ceil(landPoints.size / count.toDouble()).toInt()
        val idealPointsPerRegion = Math.round(landPoints.size / count.toDouble()).toInt()
        unclaimedLand.clear()
        forceGiveUpDisconnectedLand(graph, regions, unclaimedLand)
        forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
//        equalizeRegions(graph, landPoints, maxPointsPerRegion, random, regions)
//        smoothRegions(graph, random, landPoints, waterMask, regions, 5, 0.02f)
        forceGiveUpDisconnectedLand(graph, regions, unclaimedLand)
        forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
        graph.useVirtualConnections = false
        graph.virtualConnections = HashMap(0)
        return ArrayListMatrix(graph.stride) { findRegionId(regions, it) }
    }

    private fun buildSimplifiedWaterMask(regionGraph: Graph, reversedMask: HashMap<Int, HashSet<Int>>): HashSet<Int> {
        val simplifiedWaterMask = HashSet<Int>()
        for (i in 0..regionGraph.vertices.size - 1) {
            if (reversedMask[i] == null) {
                simplifiedWaterMask.add(i)
            }
        }
        return simplifiedWaterMask
    }

    private fun buildRandomGraph(random: Random, stride: Int): Graph {
        val points = generatePoints(stride, 100000.0f, random)
        val graph = buildGraph(stride, 100000.0f, points)
        return graph
    }


    fun buildRegions2(graph: Graph, random: Random, waterMask: HashSet<Int>, count: Int): Matrix<Int> {
        val vertices = graph.vertices
        val land = buildLand(vertices, waterMask)
        val bodies = graph.getConnectedBodies(land)
        bodies.sortBy { it.size }
        val mainland = bodies.last()
        val center = findCenter(vertices, mainland)
        val border = buildBorder(graph, mainland)
        val startPoints = findRegionStartPoints(vertices, border, center, count)
        val regions = buildInitialRegions(vertices, land, startPoints)
        val maxPointsPerRegion = Math.ceil(land.size / regions.size.toDouble()).toInt()
        graph.useVirtualConnections = true
        connectIslands(graph, land)
        var sortedRegions = regions.sortedBy { it.size }.toMutableList()
        while (sortedRegions.last().size - sortedRegions.first().size > regions.size) {
            val unclaimedLand = HashSet<Int>()
            forceGiveUpDisconnectedLand(graph, regions, unclaimedLand)
            forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
            equalizeRegions2(graph, land, maxPointsPerRegion, regions)
            forceGiveUpDisconnectedLand(graph, regions, unclaimedLand)
            forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
            smoothRegions(graph, random, land, waterMask, regions, iterations = 4, tradeWeight = 0.2f)
            sortedRegions = regions.sortedBy { it.size }.toMutableList()
        }
        graph.useVirtualConnections = false
        graph.virtualConnections = HashMap(0)
        return ArrayListMatrix(graph.stride) { findRegionId(regions, it) }
    }

    private fun equalizeRegions2(graph: Graph, land: HashSet<Int>, maxPointsPerRegion: Int, regions: ArrayList<HashSet<Int>>) {
        val vertices = graph.vertices
        var sortedRegions = regions.sortedBy { it.size }.toMutableList()
        while (sortedRegions.last().size - sortedRegions.first().size > regions.size) {
            val alreadyTradedIds = HashSet<Int>()
            while (sortedRegions.last().size - sortedRegions.first().size > regions.size) {
                val smallestRegion = sortedRegions[0]
                var pointsToAdd = maxPointsPerRegion - smallestRegion.size
                var viableIds = HashSet<Int>(listOf(-1))
                while (pointsToAdd > 0 && viableIds.isNotEmpty()) {
                    val borderPoints = findBorderPoints(graph, land, smallestRegion)
                    viableIds = findViableBorderExpansionIds(vertices, land, smallestRegion, borderPoints, alreadyTradedIds, pointsToAdd)
                    viableIds.forEach { viableId ->
                        for (i in 1..sortedRegions.size - 1) {
                            val otherRegion = sortedRegions[i]
                            if (otherRegion.contains(viableId)) {
                                otherRegion.remove(viableId)
                                smallestRegion.add(viableId)
                                alreadyTradedIds.add(viableId)
                                pointsToAdd--
                                break
                            }
                        }
                    }
                }
                if (viableIds.isEmpty()) {
                    break
                }
                sortedRegions = regions.sortedBy { it.size }.toMutableList()
            }
        }
    }

    private fun findViableBorderExpansionIds(vertices: Graph.Vertices, land: HashSet<Int>, region: HashSet<Int>, borderPoints: HashSet<Int>, alreadyTradedIds: HashSet<Int>, countToAdd: Int): HashSet<Int> {
        val viableExpansionPoints = HashSet<Int>()
        borderPoints.forEach { borderId ->
            if (findViableBorderExpansionIds(vertices, land, region, viableExpansionPoints, alreadyTradedIds, borderId, countToAdd)) {
                return viableExpansionPoints
            }
        }
        return viableExpansionPoints
    }

    private fun findViableBorderExpansionIds(vertices: Graph.Vertices, land: HashSet<Int>, region: HashSet<Int>, viableExpansionIds: HashSet<Int>, alreadyTradedIds: HashSet<Int>, borderId: Int, countToAdd: Int): Boolean {
        vertices.getAdjacentVertices(borderId).forEach { adjacentToBorder ->
            if (findViableBorderExpansionIds(land, region, viableExpansionIds, alreadyTradedIds, countToAdd, adjacentToBorder)) {
                return true
            }
        }
        return false
    }

    private fun findViableBorderExpansionIds(land: HashSet<Int>, region: HashSet<Int>, viableExpansionIds: HashSet<Int>, alreadyTradedIds: HashSet<Int>, countToAdd: Int, candidateId: Int): Boolean {
        if (!region.contains(candidateId) && land.contains(candidateId) && !alreadyTradedIds.contains(candidateId)) {
            viableExpansionIds.add(candidateId)
            if (viableExpansionIds.size == countToAdd) {
                return true
            }
        }
        return false
    }

    private fun buildInitialRegions(vertices: Graph.Vertices, land: HashSet<Int>, startPoints: ArrayList<Point>): ArrayList<HashSet<Int>> {
        val regions = ArrayList<HashSet<Int>>()
        for (i in 1..startPoints.size) {
            regions.add(HashSet<Int>())
        }
        land.forEach {
            val point = vertices.getPoint(it)
            var closestId = 0
            var closestDist = startPoints[0].distanceSquaredTo(point)
            for (i in 1..startPoints.size - 1) {
                val dist = startPoints[i].distanceSquaredTo(point)
                if (dist < closestDist) {
                    closestDist = dist
                    closestId = i
                }
            }
            regions[closestId].add(it)
        }
        return regions
    }

    private fun findRegionStartPoints(vertices: Graph.Vertices, border: HashSet<Int>, center: Point, count: Int): ArrayList<Point> {
        val increment = (2 * Math.PI) / count
        val spokes = ArrayList<Pair<Point, Point>>(count)
        for (i in 0..count - 1) {
            val dx = (Math.cos(increment * i) * 2.0).toFloat()
            val dy = (Math.sin(increment * i) * 2.0).toFloat()
            val endPoint = Point(center.x + dx, center.y + dy)
            spokes.add(Pair(center, endPoint))
        }
        val spokeCellIntersections = ArrayList<HashSet<Int>>()
        for (i in 1..spokes.size) {
            spokeCellIntersections.add(HashSet<Int>())
        }
        border.forEach {
            checkForSpokeIntersections(spokes, spokeCellIntersections, vertices[it])
        }
        val regionPoints = ArrayList<Point>()
        spokeCellIntersections.forEach { spokeSet ->
            var maxDistance = Float.MIN_VALUE
            var maxPoint: Point? = null
            spokeSet.forEach { cellId ->
                val point = vertices[cellId].point
                val dist = center.distanceSquaredTo(point)
                if (dist > maxDistance) {
                    maxDistance = dist
                    maxPoint = point
                }
            }
            if (maxPoint != null) {
                regionPoints.add(maxPoint!!)
            }
        }
        return regionPoints
    }

    private fun checkForSpokeIntersections(spokes: ArrayList<Pair<Point, Point>>, spokeCellIntersections: ArrayList<HashSet<Int>>, vertex: Vertex) {
        val cell = vertex.cell
        val cellId = cell.id
        val borderPoints = cell.border
        for (i in 1..borderPoints.size - 1) {
            checkForSpokeIntersections(spokes, spokeCellIntersections, Pair(borderPoints[i - 1], borderPoints[i]), cellId)
        }
        if (cell.isClosed) {
            checkForSpokeIntersections(spokes, spokeCellIntersections, Pair(borderPoints[borderPoints.size - 2], borderPoints[borderPoints.size - 1]), cellId)
        }
    }

    private fun checkForSpokeIntersections(spokes: ArrayList<Pair<Point, Point>>, spokeCellIntersections: ArrayList<HashSet<Int>>, edge: Pair<Point, Point>, cellId: Int) {
        spokes.forEachIndexed { spokeId, spoke ->
            val spokeIntersections = spokeCellIntersections[spokeId]
            if (!spokeIntersections.contains(cellId)) {
                if (linesIntersect(edge, spoke)) {
                    spokeIntersections.add(cellId)
                }
            }
        }
    }

    private fun findCenter(vertices: Graph.Vertices, mainland: HashSet<Int>): Point {
        var xSum = 0.0f
        var ySum = 0.0f
        mainland.forEach {
            val point = vertices.getPoint(it)
            xSum += point.x
            ySum += point.y
        }
        return Point(xSum / mainland.size, ySum / mainland.size)
    }

    private fun buildLand(vertices: Graph.Vertices, waterMask: HashSet<Int>): HashSet<Int> {
        val land = HashSet<Int>()
        for (i in 0..vertices.size - 1) {
            if (!waterMask.contains(i)) {
                land.add(i)
            }
        }
        return land
    }

    fun buildBorder(graph: Graph, body: HashSet<Int>): HashSet<Int> {
        val vertices = graph.vertices
        val border = HashSet<Int>()
        body.forEach {
            if (isOnBorder(vertices, body, it)) {
                border.add(it)
            }
        }
        return border
    }

    private fun isOnBorder(vertices: Graph.Vertices, body: HashSet<Int>, vertexId: Int): Boolean {
        vertices.getAdjacentVertices(vertexId).forEach {
            if (!body.contains(it)) {
                return true
            }
        }
        return false
    }

    fun orientation(p1: Point, p2: Point, p3: Point): Int {
        val det = (p2.y - p1.y) * (p3.x - p2.x) - (p2.x - p1.x) * (p3.y - p2.y)
        if (det == 0.0f) return 0
        return if (det > 0.0f) 1 else -1
    }

    fun onSegment(p1: Point, p2: Point, p3: Point): Boolean {
        return (p2.x <= max(p1.x, p3.x)
                && p2.x >= min(p1.x, p3.x)
                && p2.y <= max(p1.y, p3.y)
                && p2.y >= min(p1.y, p3.y))
    }

    fun linesIntersect(line1: Pair<Point, Point>, line2: Pair<Point, Point>): Boolean {
        val o1 = orientation(line1.first, line1.second, line2.first)
        val o2 = orientation(line1.first, line1.second, line2.second)
        val o3 = orientation(line2.first, line2.second, line1.first)
        val o4 = orientation(line2.first, line2.second, line1.second)
        if (o1 != o2 && o3 != o4) {
            return true
        }
        return (o1 == 0 && (onSegment(line1.first, line2.first, line1.second)
                         || onSegment(line1.first, line2.second, line1.second)
                         || onSegment(line2.first, line1.first, line2.second)
                         || onSegment(line2.first, line1.second, line2.second)))
    }
































    fun refineRegions(graph: Graph, random: Random, waterMask: HashSet<Int>, regionMask: Matrix<Int>) : Matrix<Int> {
        graph.useVirtualConnections = true
        val stride = graph.stride
        val landPoints = findLandPoints(graph, waterMask)
        connectIslands(graph, landPoints)
        val regions = extractRegions(graph, regionMask)
        val maxPointsPerRegion = Math.ceil(landPoints.size / regions.size.toDouble()).toInt()
        smoothRegions(graph, random, landPoints, waterMask, regions, iterations = 4, tradeWeight = 0.4f)
        equalizeRegions(graph, landPoints, maxPointsPerRegion, random, regions)
        normalizeRegionShape(graph, random, landPoints, waterMask, regions)
//        normalizeRegionShapes(graph, landPoints, maxPointsPerRegion, stride, random, regions, 2)
//        equalizeRegions(graph, landPoints, maxPointsPerRegion, random, regions)
        smoothRegions(graph, random, landPoints, waterMask, regions, iterations = 8, tradeWeight = 0.05f)
        graph.useVirtualConnections = false
        graph.virtualConnections = HashMap(0)
        return ArrayListMatrix(graph.stride) { findRegionId(regions, it) }
    }

    fun buildRegions(graph: Graph, random: Random, count: Int, waterMask: HashSet<Int>) : Matrix<Int> {
        graph.useVirtualConnections = true
        val stride = graph.stride
        val startPoints = pickStartingPoints(graph, waterMask, random, count)
        val regions = ArrayList(startPoints.map { hashSetOf(it) })
        val landPoints = findLandPoints(graph, waterMask)
        connectIslands(graph, landPoints)
        val unclaimedLand = HashSet(landPoints)
        unclaimedLand.removeAll(startPoints)
        buildUpRegions(graph, unclaimedLand, regions)
        val maxPointsPerRegion = Math.ceil(landPoints.size / count.toDouble()).toInt()
        equalizeRegions(graph, landPoints, maxPointsPerRegion, random, regions)
        normalizeRegionShapes(graph, landPoints, maxPointsPerRegion, stride, random, regions, 6)
        equalizeRegions(graph, landPoints, maxPointsPerRegion, random, regions)
        normalizeRegionShapes(graph, landPoints, maxPointsPerRegion, stride, random, regions, 3)
        equalizeRegions(graph, landPoints, maxPointsPerRegion, random, regions)
        normalizeRegionShapes(graph, landPoints, maxPointsPerRegion, stride, random, regions, 2)
        equalizeRegions(graph, landPoints, maxPointsPerRegion, random, regions)
        graph.useVirtualConnections = false
        graph.virtualConnections = HashMap(0)
        return ArrayListMatrix(graph.stride) { findRegionId(regions, it) }
    }

    private fun normalizeRegionShape(graph: Graph, random: Random, landPoints: HashSet<Int>, water: HashSet<Int>, regions: ArrayList<HashSet<Int>>) {
        val pointsToGiveUpPerIteration = 0.02f
        val iterations = 10
        val unclaimedLand = HashSet<Int>()
        for (i in 1..iterations) {
            regions.forEach { region ->
                val pointsToGiveUp = min(region.size - 1, (region.size * pointsToGiveUpPerIteration).toInt())
                val borderPoints = findBorderPoints(graph, landPoints, region)
                val weights = findConcavityWeightsLarge(graph, water, region, borderPoints)
                for (j in 0..min(weights.size, pointsToGiveUp) - 1) {
                    val id = weights[j].first
                    region.remove(id)
                    unclaimedLand.add(id)
                }
            }
        }
        forceGiveUpDisconnectedLand(graph, regions, unclaimedLand)
        forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
    }

    private fun smoothRegions(graph: Graph, random: Random, landPoints: HashSet<Int>, water: HashSet<Int>, regions: ArrayList<HashSet<Int>>, iterations: Int = 3, tradeWeight: Float = 0.3f) {
        val unclaimedLand = HashSet<Int>()
        forceGiveUpDisconnectedLand(graph, regions, unclaimedLand)
        forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
        for (i in 1..iterations) {
            val regionConnections = ArrayList<Pair<Int, Int>>()
            for (j in 0..regions.size - 2) {
                val borderPoints = findBorderPoints(graph, landPoints, regions[j])
                for (k in j + 1..regions.size - 1) {
                    val other = regions[k]
                    checkForRegionConnection(graph, borderPoints, j, k, other, regionConnections)
                }
            }
            regionConnections.forEach {
                val border1 = HashSet<Int>()
                val border2 = HashSet<Int>()
                val region1 = regions[it.first]
                val region2 = regions[it.second]
                region1.forEach { r1Id ->
                    graph.vertices.getAdjacentVertices(r1Id).forEach { r2Id ->
                        if (region2.contains(r2Id)) {
                            border1.add(r1Id)
                            border2.add(r2Id)
                        }
                    }
                }
                val weights1 = findConcavityWeightsSmall(graph, water, region1, border1).sortedBy { it.second }
                val weights2 = findConcavityWeightsSmall(graph, water, region2, border2).sortedBy { it.second }
                val pointsToTrade = Math.round(((weights1.size + weights2.size) / 2) * tradeWeight)
                for (j in 0..min(min(weights1.size, weights2.size), pointsToTrade) - 1) {
                    val r1Id = weights1[j].first
                    val r2Id = weights2[j].first
                    region1.remove(r1Id)
                    region2.add(r1Id)
                    region2.remove(r2Id)
                    region1.add(r2Id)
                }
            }
        }
        forceGiveUpDisconnectedLand(graph, regions, unclaimedLand)
        forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
    }

    private fun forceGiveUpDisconnectedLand(graph: Graph, regions: ArrayList<HashSet<Int>>, unclaimedLand: HashSet<Int>) {
        regions.forEach {
            giveUpDisconnectedLand(graph, unclaimedLand, it)
        }
    }

    private fun forceGiveUpDisconnectedLand2(graph: Graph, landPoints: HashSet<Int>, regions: ArrayList<HashSet<Int>>, unclaimedLand: HashSet<Int>) {
        regions.forEach {
            giveUpDisconnectedLand2(graph, unclaimedLand, landPoints, it)
        }
    }

    private fun checkForRegionConnection(graph: Graph, borderPoints: HashSet<Int>, regionId1: Int, regionId2: Int, other: HashSet<Int>, regionConnections: ArrayList<Pair<Int, Int>>) {
        borderPoints.forEach {
            if (regionIsConnectedToOther(graph, regionId1, it, regionId2, other, regionConnections)) return
        }
    }

    private fun regionIsConnectedToOther(graph: Graph, region1Id: Int, pointId: Int, region2Id: Int, other: HashSet<Int>, regionConnections: ArrayList<Pair<Int, Int>>): Boolean {
        graph.vertices.getAdjacentVertices(pointId).forEach {
            if (other.contains(it)) {
                regionConnections.add(Pair(region1Id, region2Id))
                return true
            }
        }
        return false
    }

    private fun findConcavityWeightsSmall(graph: Graph, water: HashSet<Int>, body: HashSet<Int>, testPoints: HashSet<Int>): ArrayList<Pair<Int, Float>> {
        val weights = ArrayList<Pair<Int, Float>>(testPoints.size)
        testPoints.forEach { it ->
            var landWaterRatio = calculateConcavityRatio(graph, water, body, it, 2, 0.000075f)
            landWaterRatio *= calculateConcavityRatio(graph, water, body, it, 4, 0.00035f)
            landWaterRatio *= calculateConcavityRatio(graph, water, body, it, 6, 0.00075f)
            weights.add(Pair(it, landWaterRatio))
        }
        return weights
    }

    private fun findConcavityWeightsLarge(graph: Graph, water: HashSet<Int>, body: HashSet<Int>, testPoints: HashSet<Int>): ArrayList<Pair<Int, Float>> {
        return ArrayList(testPoints.map { Pair(it, calculateConcavityRatio(graph, water, body, it, 12, 0.035f)) }.sortedBy { it.second })
    }

    private fun calculateConcavityRatio(graph: Graph, mask: HashSet<Int>, body: HashSet<Int>, vertexId: Int, expansions: Int, radiusSquared: Float): Float {
        val vertices = graph.vertices
        val vertex = vertices[vertexId]
        val point = vertex.point
        val closeVertices = graph.getClosePoints(point, expansions).map { vertices[it] }.filter { !mask.contains(it.id) && point.distanceSquaredTo(it.point) < radiusSquared }
        var inCount = 0
        closeVertices.forEach {
            if (body.contains(it.id)) {
                inCount++
            }
        }
        return inCount.toFloat() / closeVertices.size
    }

    private fun extractRegions(graph: Graph, regionMask: Matrix<Int>): ArrayList<HashSet<Int>> {
        val regions = ArrayList<HashSet<Int>>()
        for (i in 0..graph.vertices.size - 1) {
            val regionId = regionMask[i]
            if (regionId > 0) {
                if (regions.size < regionId) {
                    for (j in 1..regionId - regions.size) {
                        regions.add(HashSet<Int>())
                    }
                }
                regions[regionId - 1].add(i)
            }
        }
        return regions
    }

    private fun findRegionId(regions: List<HashSet<Int>>, id: Int): Int {
        regions.forEachIndexed { i, region ->
            if (region.contains(id)) {
                return i + 1
            }
        }
        return 0
    }

    private fun normalizeRegionShapes(graph: Graph, landPoints: HashSet<Int>, maxPointsPerRegion: Int, pointsWidth: Int, random: Random, regions: List<HashSet<Int>>, iterations: Int) {
        for (i in 0..iterations - 1) {
            normalizeRegionShapes(graph, landPoints, maxPointsPerRegion, pointsWidth, random, regions)
        }
    }

    private fun normalizeRegionShapes(graph: Graph, landPoints: HashSet<Int>, maxPointsPerRegion: Int, pointsWidth: Int, random: Random, regions: List<HashSet<Int>>) {
        regions.randomized(random).forEach { region ->
            normalizeRegionShape(graph, landPoints, maxPointsPerRegion, pointsWidth, region, regions)
        }
    }

    private fun normalizeRegionShape(graph: Graph, landPoints: HashSet<Int>, maxPointsPerRegion: Int, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>) {
        val (avgX, avgY) = findRegionCenter(pointsWidth, region)
        val farQueue = buildFurthestFirstQueue(avgX, avgY, graph, landPoints, pointsWidth, region)
        giveAwayFurthestPoints(avgX, avgY, graph, landPoints, pointsWidth, region, regions, farQueue)
        val nearQueue = buildNearestFirstQueue(farQueue)
        takeBackNearestPoints(avgX, avgY, graph, landPoints, maxPointsPerRegion, pointsWidth, region, regions, nearQueue)
    }

    private fun takeBackNearestPoints(avgX: Int, avgY: Int, graph: Graph, landPoints: HashSet<Int>, maxPointsPerRegion: Int, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>, nearQueue: PriorityQueue<Pair<Int, Int>>) {
        for (i in 0..maxPointsPerRegion - region.size - 1) {
            takeBackNearestPoint(avgX, avgY, graph, landPoints, pointsWidth, region, regions, nearQueue)
        }
    }

    private fun takeBackNearestPoint(avgX: Int, avgY: Int, graph: Graph, landPoints: HashSet<Int>, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>, nearQueue: PriorityQueue<Pair<Int, Int>>) {
        val pointToTakeNear = nearQueue.remove()
        var take1 = false
        var take2 = false
        graph.vertices.getAdjacentVertices(pointToTakeNear.first).forEach inside@ { adjacentPoint ->
            if (landPoints.contains(adjacentPoint)) {
                regions.forEach { otherRegion ->
                    if (otherRegion !== region) {
                        if (otherRegion.contains(adjacentPoint)) {
                            if (!take1) {
                                otherRegion.remove(adjacentPoint)
                                region.add(adjacentPoint)
                                val x = adjacentPoint % pointsWidth
                                val y = adjacentPoint / pointsWidth
                                val deltaX = avgX - x
                                val deltaY = avgY - y
                                val dist = deltaX * deltaX + deltaY * deltaY
                                nearQueue.add(Pair(adjacentPoint, dist))
                                take1 = true
                            } else if (!take2) {
                                take2 = true
                            }
                        }
                    }
                }
            }
        }
        if (take2) {
            nearQueue.add(pointToTakeNear)
        }
    }

    private fun giveAwayFurthestPoints(avgX: Int, avgY: Int, graph: Graph, landPoints: HashSet<Int>, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>, farQueue: PriorityQueue<Pair<Int, Int>>) {
        for (i in 0..4) {
            giveAwayFurthestPoint(avgX, avgY, graph, landPoints, pointsWidth, region, regions, farQueue)
        }
    }

    private fun buildNearestFirstQueue(farQueue: PriorityQueue<Pair<Int, Int>>): PriorityQueue<Pair<Int, Int>> {
        val nearQueue = PriorityQueue<Pair<Int, Int>>(farQueue.size) { p1, p2 -> p1.second.compareTo(p2.second) }
        nearQueue.addAll(farQueue)
        return nearQueue
    }

    private fun giveAwayFurthestPoint(avgX: Int, avgY: Int, graph: Graph, landPoints: HashSet<Int>, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>, farQueue: PriorityQueue<Pair<Int, Int>>) {
        val pointToTrade = farQueue.remove()
        graph.vertices.getAdjacentVertices(pointToTrade.first).forEach { adjacentPoint ->
            if (landPoints.contains(adjacentPoint)) {
                regions.forEach { otherRegion ->
                    if (otherRegion === region) {
                        val x = adjacentPoint % pointsWidth
                        val y = adjacentPoint / pointsWidth
                        val deltaX = avgX - x
                        val deltaY = avgY - y
                        val dist = deltaX * deltaX + deltaY * deltaY
                        farQueue.add(Pair(adjacentPoint, dist))
                    } else {
                        if (otherRegion.contains(adjacentPoint) && region.contains(pointToTrade.first)) {
                            otherRegion.add(pointToTrade.first)
                            region.remove(pointToTrade.first)
                        }
                    }
                }
            }
        }
    }

    private fun buildFurthestFirstQueue(avgX: Int, avgY: Int, graph: Graph, landPoints: HashSet<Int>, pointsWidth: Int, region: HashSet<Int>): PriorityQueue<Pair<Int, Int>> {
        val borderPoints = findBorderPoints(graph, landPoints, region)
        val farQueue = PriorityQueue<Pair<Int, Int>>(borderPoints.size) { p1, p2 -> p2.second.compareTo(p1.second) }
        borderPoints.forEach { borderPoint ->
            val x = borderPoint % pointsWidth
            val y = borderPoint / pointsWidth
            val deltaX = avgX - x
            val deltaY = avgY - y
            val dist = deltaX * deltaX + deltaY * deltaY
            farQueue.add(Pair(borderPoint, dist))
        }
        return farQueue
    }

    private fun findRegionCenter(pointsWidth: Int, region: HashSet<Int>): Pair<Int, Int> {
        var sumX = 0
        var sumY = 0
        region.forEach {
            sumX += it % pointsWidth
            sumY += it / pointsWidth
        }
        val avgX = Math.round(sumX / region.size.toFloat())
        val avgY = Math.round(sumY / region.size.toFloat())
        return Pair(avgX, avgY)
    }

    private fun equalizeRegions(graph: Graph, landPoints: HashSet<Int>, maxPointsPerRegion: Int, random: Random, regions: List<HashSet<Int>>) {
        val unclaimedLand = HashSet<Int>()
        var lastUnclaimedLand: HashSet<Int>? = null
        do {
            if (unclaimedLand.equals(lastUnclaimedLand)) {
                forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
            }
            lastUnclaimedLand = unclaimedLand
            regions.forEach { region ->
                giveUpLandIfTooBig(graph, maxPointsPerRegion, landPoints, random, region, unclaimedLand)
            }
            regions.randomized(random).forEach { region ->
                claimLandIfNeeded(graph, maxPointsPerRegion, random, region, unclaimedLand)
            }
            regions.forEach {
                giveUpDisconnectedLand(graph, unclaimedLand, it)
            }
        } while (unclaimedLand.isNotEmpty())
    }

    private fun equalizeRegions2(graph: Graph, landPoints: HashSet<Int>, maxPointsPerRegion: Int, idealPointsPerRegion: Int, random: Random, regions: List<HashSet<Int>>, reversedMask: HashMap<Int, HashSet<Int>>) {
        val unclaimedLand = HashSet<Int>()
        var lastUnclaimedLand: HashSet<Int>? = null
        var count = 0
        do {
            if (unclaimedLand.equals(lastUnclaimedLand)) {
                forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
            }
            if (count == 50) break
            lastUnclaimedLand = unclaimedLand
            regions.forEach { region ->
                giveUpLandIfTooBig2(graph, maxPointsPerRegion, landPoints, region, unclaimedLand, reversedMask)
            }
            regions.randomized(random).forEach { region ->
                claimLandIfNeeded2(graph, idealPointsPerRegion, random, region, unclaimedLand, reversedMask)
            }
            regions.forEach {
                giveUpDisconnectedLand2(graph, unclaimedLand, landPoints, it)
            }
            count++
        } while (unclaimedLand.isNotEmpty())
    }

    private fun equalizeRegions3(graph: Graph, landPoints: HashSet<Int>, maxPointsPerRegion: Int, idealPointsPerRegion: Int, random: Random, regions: List<HashSet<Int>>) {
        val alreadyTraded = HashSet<Int>()
        val unclaimedLand = HashSet<Int>()
        var lastUnclaimedLand: HashSet<Int>? = null
        do {
            if (unclaimedLand.equals(lastUnclaimedLand)) {
                forceClaimUnwantedLand(graph, random, regions, unclaimedLand)
            }
            lastUnclaimedLand = unclaimedLand
            regions.forEach { region ->
                giveUpLandIfTooBig3(graph, maxPointsPerRegion, landPoints, region, unclaimedLand)
            }
            regions.randomized(random).forEach { region ->
                claimLandIfNeeded3(graph, idealPointsPerRegion + 1, random, landPoints, region, unclaimedLand, regions, alreadyTraded)
            }
            regions.forEach {
                giveUpDisconnectedLand(graph, unclaimedLand, it)
            }
        } while (unclaimedLand.isNotEmpty())
    }

    private fun forceClaimUnwantedLand(graph: Graph, random: Random, regions: List<HashSet<Int>>, unclaimedLand: HashSet<Int>) {
        while (unclaimedLand.isNotEmpty()) {
            regions.randomized(random).forEach { region ->
                tryAndClaimLand(graph, unclaimedLand, region, random)
            }
        }
    }

    private fun giveUpLandIfTooBig2(graph: Graph, maxPointsPerRegion: Int, landPoints: HashSet<Int>, region: HashSet<Int>, unclaimedLand: HashSet<Int>, reversedMask: HashMap<Int, HashSet<Int>>) {
        while (region.sumBy { reversedMask[it]?.size ?: 0 } > maxPointsPerRegion) {
            val borderPoints = ArrayList(findBorderPoints(graph, landPoints, region).sortedBy { graph.vertices.getAdjacentVertices(it).sumBy { if (region.contains(it)) { 1 } else { 0 } } })
            if (borderPoints.isNotEmpty()) {
                val pointToGive = borderPoints.removeAt(0)
                region.remove(pointToGive)
                unclaimedLand.add(pointToGive)
            }
        }
    }

    private fun giveUpLandIfTooBig3(graph: Graph, maxPointsPerRegion: Int, landPoints: HashSet<Int>, region: HashSet<Int>, unclaimedLand: HashSet<Int>) {
        while (region.size > maxPointsPerRegion) {
            val borderPoints = ArrayList(findBorderPoints(graph, landPoints, region).map { Pair(it, graph.vertices.getAdjacentVertices(it).sumBy { if (region.contains(it)) { 1 } else { 0 } }) }.sortedBy { it.second })
            val borderWeight = borderPoints.first().second
            while (borderPoints.isNotEmpty() && region.size > maxPointsPerRegion) {
                val pointToGive = borderPoints.removeAt(0)
                if (pointToGive.second > borderWeight) {
                    break
                }
                region.remove(pointToGive.first)
                unclaimedLand.add(pointToGive.first)
            }
        }
    }

    private fun giveUpLandIfTooBig(graph: Graph, maxPointsPerRegion: Int, landPoints: HashSet<Int>, random: Random, region: HashSet<Int>, unclaimedLand: HashSet<Int>) {
        if (region.size > maxPointsPerRegion) {
            val borderPoints = ArrayList(findBorderPoints(graph, landPoints, region))
            for (i in 0..region.size - (maxPointsPerRegion + 6)) {
                if (borderPoints.isNotEmpty()) {
                    val pointToGive = borderPoints.removeAt(random.nextInt(borderPoints.size))
                    region.remove(pointToGive)
                    unclaimedLand.add(pointToGive)
                }
            }
        }
    }

    private fun claimLandIfNeeded2(graph: Graph, idealPointsPerRegion: Int, random: Random, region: HashSet<Int>, unclaimedLand: HashSet<Int>, reversedMask: HashMap<Int, HashSet<Int>>) {
        var lastRegionSize = -1
        var regionSize = region.sumBy { reversedMask[it]?.size ?: 0 }
        while (regionSize < idealPointsPerRegion && regionSize != lastRegionSize) {
            lastRegionSize = regionSize
            tryAndClaimLand(graph, unclaimedLand, region, random)
            regionSize = region.sumBy { reversedMask[it]?.size ?: 0 }
        }
    }

    private fun claimLandIfNeeded3(graph: Graph, idealPointsPerRegion: Int, random: Random, landPoints: HashSet<Int>, region: HashSet<Int>, unclaimedLand: HashSet<Int>, regions: List<HashSet<Int>>, alreadyTradedIds: HashSet<Int>) {
        var lastRegionSize = -1
        while (region.size < idealPointsPerRegion && region.size != lastRegionSize) {
            lastRegionSize = region.size
            tryAndClaimLand2(graph, unclaimedLand, landPoints, region, idealPointsPerRegion, random, regions, alreadyTradedIds)
        }
    }

    private fun claimLandIfNeeded(graph: Graph, maxPointsPerRegion: Int, random: Random, region: HashSet<Int>, unclaimedLand: HashSet<Int>) {
        if (region.size < maxPointsPerRegion) {
            for (i in 0..maxPointsPerRegion - region.size - 1) {
                tryAndClaimLand(graph, unclaimedLand, region, random)
            }
        }
    }

    private fun <T> List<T>.randomized(random: Random): ArrayList<T> {
        val randomized = ArrayList(this)
        Collections.shuffle(randomized, random)
        return randomized
    }

    private fun getRegionBodies(graph: Graph, region: HashSet<Int>): ArrayList<HashSet<Int>> {
        val regionBodies = ArrayList<HashSet<Int>>()
        val regionPointsToClaim = HashSet<Int>(region)
        while (regionPointsToClaim.isNotEmpty()) {
            regionBodies.add(buildLandBody(graph, regionPointsToClaim))
        }
        return regionBodies
    }

    private fun giveUpDisconnectedLand2(graph: Graph, unclaimedLand: HashSet<Int>, landPoints: HashSet<Int>, region: HashSet<Int>) {
        val regionBodies = getRegionBodies(graph, region)
        if (regionBodies.size > 1) {
            regionBodies.sortBy { it.size }
            regionBodies.removeAt(regionBodies.size - 1)
            regionBodies.forEach { body ->
                region.removeAll(body)
                unclaimedLand.addAll(body)
            }
        }
        val badBorderPoints = findBorderPoints(graph, landPoints, region).filter { graph.vertices.getAdjacentVertices(it).sumBy { if (region.contains(it)) { 1 } else { 0 } } == 1 }
        if (badBorderPoints.size < region.size) {
            region.removeAll(badBorderPoints)
            unclaimedLand.addAll(badBorderPoints)
        }
    }

    private fun giveUpDisconnectedLand(graph: Graph, unclaimedLand: HashSet<Int>, region: HashSet<Int>) {
        val regionBodies = getRegionBodies(graph, region)
        if (regionBodies.size > 1) {
            regionBodies.sortBy { it.size }
            regionBodies.removeAt(regionBodies.size - 1)
            regionBodies.forEach { body ->
                region.removeAll(body)
                unclaimedLand.addAll(body)
            }
        }
    }

    private fun tryAndClaimLand(graph: Graph, unclaimedLand: HashSet<Int>, region: HashSet<Int>, random: Random) {
        val vertices = graph.vertices
        val landToClaim = ArrayList(unclaimedLand)
        Collections.shuffle(landToClaim, random)
        landToClaim.forEach {
            vertices.getAdjacentVertices(it).forEach { adjacentPoint ->
                if (region.contains(adjacentPoint)) {
                    unclaimedLand.remove(it)
                    region.add(it)
                }
            }
        }
    }

    private fun tryAndClaimLand2(graph: Graph, unclaimedLand: HashSet<Int>, landPoints: HashSet<Int>, region: HashSet<Int>, idealRegionSize: Int, random: Random, regions: List<HashSet<Int>>, alreadyTradedIds: HashSet<Int>) {
        val vertices = graph.vertices
        val landToClaim = ArrayList(unclaimedLand)
        Collections.shuffle(landToClaim, random)
        landToClaim.forEach {
            vertices.getAdjacentVertices(it).forEach { adjacentPoint ->
                if (region.contains(adjacentPoint)) {
                    unclaimedLand.remove(it)
                    region.add(it)
                }
            }
        }
        while (region.size < idealRegionSize) {
            val borderPoints = ArrayList(findBorderPoints(graph, landPoints, region).map { Pair(it, graph.vertices.getAdjacentVertices(it).sumBy { if (region.contains(it)) { 1 } else { 0 } }) }.sortedByDescending { it.second })
            var borderWeight = borderPoints.first().second
            while (borderPoints.isNotEmpty() && region.size < idealRegionSize) {
                val pointToTakeNear = borderPoints.removeAt(0)
                if (pointToTakeNear.second < borderWeight) {
                    break
                }
                val addPoints = ArrayList(graph.vertices.getAdjacentVertices(pointToTakeNear.first).filter { landPoints.contains(it) && !region.contains(it) && !alreadyTradedIds.contains(it) }.map { Pair(it, graph.vertices.getAdjacentVertices(it).sumBy { if (region.contains(it)) { 1 } else { 0 } }) }.sortedByDescending { it.second })
                if (addPoints.isNotEmpty()) {
                    val addWeight = addPoints.first().second
                    while (addPoints.isNotEmpty() && region.size < idealRegionSize) {
                        val pointToAdd = addPoints.removeAt(0)
                        if (pointToAdd.second < addWeight) {
                            break
                        }
                        for (i in 0..regions.size - 1) {
                            if (regions[i].remove(pointToAdd.first)) {
                                break
                            }
                        }
                        unclaimedLand.remove(pointToAdd.first)
                        region.add(pointToAdd.first)
                        alreadyTradedIds.add(pointToAdd.first)
                    }
                } else if (borderPoints.isNotEmpty()){
                    borderWeight = borderPoints.first().second
                }
            }
        }

    }

    private fun findBorderPoints(graph: Graph, landPoints: HashSet<Int>, region: HashSet<Int>): HashSet<Int> {
        val vertices = graph.vertices
        val borderPoints = HashSet<Int>()
        region.forEach {
            vertices.getAdjacentVertices(it).forEach inside@ { adjacentPoint ->
                if (landPoints.contains(adjacentPoint) && !region.contains(adjacentPoint)) {
                    borderPoints.add(it)
                    return@inside
                }
            }
        }
        return borderPoints
    }

    fun connectIslands(graph: Graph, landPoints: HashSet<Int>) {
        val stride = graph.stride
        val landBodies = ArrayList<HashSet<Int>>()
        val unclaimedPoints = HashSet<Int>(landPoints)
        while (unclaimedPoints.isNotEmpty()) {
            landBodies.add(buildLandBody(graph, unclaimedPoints))
        }
        landBodies.sortBy { it.size }
        val mainland = landBodies.removeAt(landBodies.size - 1)
        landBodies.forEach { island ->
            var closestPair: Triple<Int, Int, Float>? = null
            island.forEach { islandVertexId ->
                if (isCoastalPoint(graph, islandVertexId, landPoints)) {
                    val localClosestPair = findClosestPointAndDistance(graph, islandVertexId, mainland, stride)
                    if (closestPair == null || localClosestPair.second < closestPair!!.third) {
                        closestPair = Triple(islandVertexId, localClosestPair.first, localClosestPair.second)
                    }
                }
            }
            graph.virtualConnections.getOrPut(closestPair!!.first, { HashSet<Int>(1) }).add(closestPair!!.second)
            graph.virtualConnections.getOrPut(closestPair!!.second, { HashSet<Int>(1) }).add(closestPair!!.first)
        }
    }

    private fun isCoastalPoint(graph: Graph, islandPoint: Int, landPoints: HashSet<Int>): Boolean {
        graph.vertices.getAdjacentVertices(islandPoint).forEach { adjacentPoint ->
            if (!landPoints.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    private fun findClosestPointAndDistance(graph: Graph, islandVertexId: Int, mainland: HashSet<Int>, pointsWidth: Int): Pair<Int, Float> {
        var closestPair: Pair<Int, Float>? = null
        var radius = 1
        while (closestPair == null) {
            closestPair = findApproximatelyClosestPoint(graph, islandVertexId, mainland, pointsWidth, radius)
            if (closestPair != null) {
                if (closestPair.second <= radius) {
                    break
                } else {
                    for (biggerRadius in radius + 1..Math.ceil(closestPair!!.second.toDouble()).toInt()) {
                        val otherClosestPair = findApproximatelyClosestPoint(graph, islandVertexId, mainland, pointsWidth, biggerRadius)
                        if (otherClosestPair != null && otherClosestPair.second < closestPair.second) {
                            closestPair = otherClosestPair
                        }
                    }
                    break
                }
            }
            radius++
        }
        return closestPair!!
    }

    private fun findApproximatelyClosestPoint(graph: Graph, islandVertexId: Int, mainland: HashSet<Int>, pointsWidth: Int, boxRadius: Int): Pair<Int, Float>? {
        val vertices = graph.vertices
        var closestDist = Float.MAX_VALUE
        var closestIndex: Int? = null
        val x0 = islandVertexId % pointsWidth
        val y0 = islandVertexId / pointsWidth
        val islandVertex = vertices[islandVertexId]
        val islandPoint = islandVertex.point
        val minX = x0 - boxRadius
        val maxX = x0 + boxRadius
        val minY = y0 - boxRadius
        val maxY = y0 + boxRadius
        val checkPoints = HashSet<Int>()
        for (x in minX..maxX) {
            if (x >= 0 && x < pointsWidth) {
                if (minY >= 0) {
                    checkPoints.add(minY * pointsWidth + x)
                }
                if (maxY < pointsWidth) {
                    checkPoints.add(maxY * pointsWidth + x)
                }
            }
        }
        for (y in minY..maxY) {
            if (y >= 0 && y < pointsWidth) {
                val yOff = y * pointsWidth
                if (minX >= 0) {
                    checkPoints.add(yOff + minX)
                }
                if (maxX < pointsWidth) {
                    checkPoints.add(yOff + maxX)
                }
            }
        }
        checkPoints.forEach {
            if (mainland.contains(it)) {
                val dist = islandPoint.distanceSquaredTo(vertices[it].point)
                if (dist < closestDist) {
                    closestDist = dist
                    closestIndex = it
                }
            }
        }
        if (closestIndex != null) {
            return Pair(closestIndex!!, Math.sqrt(closestDist.toDouble()).toFloat())
        }
        return null
    }

//    private fun buildLandBody(graph: Graph, landPoints: MutableSet<Int>): HashSet<Int> {
//        val unclaimedPoints = HashSet<Int>(landPoints)
//        val claimedPoints = HashSet<Int>()
//        claimedPoints.add(unclaimedPoints.first())
//        unclaimedPoints.remove(unclaimedPoints.first())
//        val growSet = HashSet<Int>(claimedPoints)
//        while (unclaimedPoints.isNotEmpty() && growSet.isNotEmpty()) {
//            val iterateSet = ArrayList<Int>(growSet)
//            growSet.clear()
//            iterateSet.forEach {
//                addAllConnectedPoints(graph, unclaimedPoints, claimedPoints, growSet, it)
//            }
//        }
//        return claimedPoints
//    }


    private fun findLandPoints(graph: Graph, waterMask: HashSet<Int>): HashSet<Int> {
        val landPoints = HashSet<Int>()
        for (i in 0..graph.vertices.size - 1) {
            if (!waterMask.contains(i)) {
                landPoints.add(i)
            }
        }
        return landPoints
    }

//    private fun buildUpRegions(graph: Graph, unclaimedPoints: MutableSet<Int>, regions: List<HashSet<Int>>) {
//        val queue = PriorityQueue<RegionGrowth>(regions.size) { g1, g2 -> g1.regionPoints.size.compareTo(g2.regionPoints.size) }
//        regions.forEach {
//            queue.add(RegionGrowth(it, HashSet(it), HashSet<Int>()))
//        }
//
//        while (queue.isNotEmpty()) {
//            val region = queue.remove()
//            if (region.iterateSet.isEmpty()) {
//                region.iterateSet = region.growSet
//                region.growSet = HashSet<Int>()
//            }
//            if (region.iterateSet.isNotEmpty()) {
//                val pointIndex = region.iterateSet.first()
//                region.iterateSet.remove(pointIndex)
//                addAllConnectedPoints(graph, unclaimedPoints, region.regionPoints, region.growSet, pointIndex)
//                queue.add(region)
//            }
//        }
//    }
//
//    private fun addAllConnectedPoints(graph: Graph, unclaimedPoints: MutableSet<Int>, claimedPoints: MutableSet<Int>, newlyClaimed: MutableSet<Int>, index: Int) {
//        graph.vertices.getAdjacentVertices(index).forEach { adjacentIndex ->
//            if (unclaimedPoints.contains(adjacentIndex) && !claimedPoints.contains(adjacentIndex)) {
//                unclaimedPoints.remove(adjacentIndex)
//                claimedPoints.add(adjacentIndex)
//                newlyClaimed.add(adjacentIndex)
//            }
//        }
//    }

    private fun buildLandBody(graph: Graph, landPoints: MutableSet<Int>): HashSet<Int> {
        val landBody = HashSet<Int>()
        var growSet = HashSet<Int>()
        growSet.add(landPoints.first())
        var iterateSet: HashSet<Int>
        while (growSet.isNotEmpty()) {
            landBody.addAll(growSet)
            landPoints.removeAll(growSet)
            iterateSet = growSet
            growSet = HashSet<Int>()
            iterateSet.forEach { index ->
                addAllConnectedPoints(graph, landPoints, landBody, growSet, index)
            }
        }
        return landBody
    }


    private fun buildUpRegions(graph: Graph, unclaimedPoints: MutableSet<Int>, regions: List<HashSet<Int>>) {
        val queue = PriorityQueue<RegionGrowth>(regions.size) { g1, g2 -> g1.regionPoints.size.compareTo(g2.regionPoints.size) }
        regions.forEach {
            queue.add(RegionGrowth(it, HashSet(it), HashSet<Int>()))
        }
        while (queue.isNotEmpty()) {
            val region = queue.remove()
            if (region.iterateSet.isEmpty()) {
                region.iterateSet = region.growSet
                region.growSet = HashSet<Int>()
            }
            if (region.iterateSet.isNotEmpty()) {
                val pointIndex = region.iterateSet.first()
                region.iterateSet.remove(pointIndex)
                addAllConnectedPoints(graph, unclaimedPoints, region.regionPoints, region.growSet, pointIndex)
                queue.add(region)
            }
        }
    }

    private fun addAllConnectedPoints(graph: Graph, unclaimedPoints: MutableSet<Int>, claimedPoints: MutableSet<Int>, growSet: MutableSet<Int>, index: Int) {
        graph.vertices.getAdjacentVertices(index).forEach { adjacentIndex ->
            if (unclaimedPoints.contains(adjacentIndex) && !claimedPoints.contains(adjacentIndex)) {
                unclaimedPoints.remove(adjacentIndex)
                claimedPoints.add(adjacentIndex)
                growSet.add(adjacentIndex)
            }
        }
    }



    private fun pickStartingPoints(graph: Graph, waterMask: HashSet<Int>, random: Random, regionCount: Int): Set<Int> {
        val stride = graph.stride
        val size = graph.vertices.size
        val minDistSquared = minDistanceBetweenPointsSquared(regionCount)
        val startingPoints = HashSet<Pair<Int, Point>>()
        while (startingPoints.size < regionCount) {
            var iterations = 0
            while (iterations < 1000 && startingPoints.size < regionCount) {
                val closestVertexId = (random.nextDouble() * size).toLong()
                val x = (closestVertexId % stride).toInt()
                val y = (closestVertexId / stride).toInt()
                val closestVertex = graph.vertices[x, y]
                if (!waterMask.contains(closestVertex.id)) {
                    val point = closestVertex.point
                    var isFarEnoughApart = true
                    startingPoints.forEach { startingPoint ->
                        if (point.distanceSquaredTo(startingPoint.second) < minDistSquared) {
                            isFarEnoughApart = false
                            return@forEach
                        }
                    }
                    if (isFarEnoughApart) {
                        startingPoints.add(Pair(closestVertex.id, point))
                    }
                    iterations++
                }
            }
        }
        return startingPoints.map { it.first }.toSet()
    }

    private fun minDistanceBetweenPointsSquared(regionCount: Int): Float {
        var minDistSquared = 1 / regionCount.toFloat()
        minDistSquared *= minDistSquared
        return minDistSquared * 2
    }
}