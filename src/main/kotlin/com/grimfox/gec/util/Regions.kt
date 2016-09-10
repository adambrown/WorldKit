package com.grimfox.gec.util

import com.grimfox.gec.model.ArrayListMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.Point
import java.util.*

object Regions {

    private data class RegionGrowth(val regionPoints: HashSet<Int>, var growSet: HashSet<Int>, var iterateSet: HashSet<Int>)

    fun buildRegions(graph: Graph, random: Random, count: Int, waterMask: HashSet<Int>) : Matrix<Int> {
        graph.useVirtualConnections = true
        val stride = graph.stride
        val startPoints = pickStartingPoints(graph, waterMask, random, count)
        val regions = startPoints.map { hashSetOf(it) }.toList()
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

    private fun forceClaimUnwantedLand(graph: Graph, random: Random, regions: List<HashSet<Int>>, unclaimedLand: HashSet<Int>) {
        while (unclaimedLand.isNotEmpty()) {
            regions.randomized(random).forEach { region ->
                tryAndClaimLand(graph, unclaimedLand, region, random)
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

    private fun giveUpDisconnectedLand(graph: Graph, unclaimedLand: HashSet<Int>, region: HashSet<Int>) {
        val regionBodies = ArrayList<Set<Int>>()
        val regionPointsToClaim = HashSet<Int>(region)
        while (regionPointsToClaim.isNotEmpty()) {
            regionBodies.add(buildLandBody(graph, regionPointsToClaim))
        }
        regionBodies.sortBy { it.size }
        regionBodies.removeAt(regionBodies.size - 1)
        regionBodies.forEach {
            region.removeAll(it)
            unclaimedLand.addAll(it)
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

    private fun findBorderPoints(graph: Graph, landPoints: HashSet<Int>, region: HashSet<Int>): Set<Int> {
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


    private fun findLandPoints(graph: Graph, waterMask: HashSet<Int>): HashSet<Int> {
        val landPoints = HashSet<Int>()
        for (i in 0..graph.vertices.size - 1) {
            if (!waterMask.contains(i)) {
                landPoints.add(i)
            }
        }
        return landPoints
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

    private fun addAllConnectedPoints(graph: Graph, unclaimedPoints: MutableSet<Int>, regionPoints: MutableSet<Int>, growSet: MutableSet<Int>, index: Int) {
        graph.vertices.getAdjacentVertices(index).forEach { adjacentIndex ->
            if (unclaimedPoints.contains(adjacentIndex) && !regionPoints.contains(adjacentIndex)) {
                unclaimedPoints.remove(adjacentIndex)
                regionPoints.add(adjacentIndex)
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