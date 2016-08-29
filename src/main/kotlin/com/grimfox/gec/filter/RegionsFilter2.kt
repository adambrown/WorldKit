package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.generator.Point
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.Uint24Matrix
import com.grimfox.gec.util.Utils.buildEdgeGraph
import com.grimfox.gec.util.Utils.buildEdgeMap
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File
import java.util.*

@Command(name = "regions2", description = "Create points mask based on raster mask.")
class RegionsFilter2 : Runnable {

    private data class RegionGrowth(val regionPoints: HashSet<Int>, var growSet: HashSet<Int>, var iterateSet: HashSet<Int>)

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    var inputFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-m", "--mask-file"), description = "The mask file to read as input.", required = true)
    var maskFile: File = File(Main.workingDir, "mask.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-c", "--region-count"), description = "The number of regions to generate.", required = true)
    var regionCount: Int = 1

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var seed: Long = System.currentTimeMillis()

    override fun run() {
        DataFiles.openAndUse<ClosestPoints>(inputFile) { closestPoints ->
            DataFiles.openAndUse<Int>(maskFile) { mask ->
                DataFiles.createAndUse<Uint24Matrix>(outputFile, mask.exponent) { regionIdMask ->
                    val edges = buildEdgeMap(closestPoints)
                    val pointCount = edges.size
                    val pointsWidth = Math.round(Math.sqrt(pointCount.toDouble())).toInt()
                    val edgeGraph = buildEdgeGraph(edges, pointCount)
                    val random = Random(seed)
                    val startPoints = pickStartingPoints(mask, closestPoints, random)
                    val regions = startPoints.map { hashSetOf(it) }.toList()
                    val landPoints = findLandPoints(mask, pointCount)
                    connectIslands(edgeGraph, landPoints, pointsWidth)
                    val unclaimedLand = HashSet(landPoints)
                    unclaimedLand.removeAll(startPoints)
                    buildUpRegions(edgeGraph, unclaimedLand, regions)
                    val maxPointsPerRegion = Math.ceil(landPoints.size / regionCount.toDouble()).toInt()
                    equalizeRegions(edgeGraph, landPoints, maxPointsPerRegion, random, regions)
                    normalizeRegionShapes(edgeGraph, landPoints, maxPointsPerRegion, pointsWidth, random, regions, 6)
                    equalizeRegions(edgeGraph, landPoints, maxPointsPerRegion, random, regions)
                    normalizeRegionShapes(edgeGraph, landPoints, maxPointsPerRegion, pointsWidth, random, regions, 3)
                    equalizeRegions(edgeGraph, landPoints, maxPointsPerRegion, random, regions)
                    normalizeRegionShapes(edgeGraph, landPoints, maxPointsPerRegion, pointsWidth, random, regions, 2)
                    equalizeRegions(edgeGraph, landPoints, maxPointsPerRegion, random, regions)

                    for (i in 0..pointCount - 1) {
                        var id = 0
                        for (j in 0..regions.size - 1) {
                            if (regions[j].contains(i)) {
                                id = j + 1
                                break
                            }
                        }
                        regionIdMask[i] = id
                    }
                }
            }
        }
    }

    private fun normalizeRegionShapes(edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, maxPointsPerRegion: Int, pointsWidth: Int, random: Random, regions: List<HashSet<Int>>, iterations: Int) {
        for (i in 0..iterations - 1) {
            normalizeRegionShapes(edgeGraph, landPoints, maxPointsPerRegion, pointsWidth, random, regions)
        }
    }

    private fun normalizeRegionShapes(edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, maxPointsPerRegion: Int, pointsWidth: Int, random: Random, regions: List<HashSet<Int>>) {
        regions.randomized(random).forEach { region ->
            normalizeRegionShape(edgeGraph, landPoints, maxPointsPerRegion, pointsWidth, region, regions)
        }
    }

    private fun normalizeRegionShape(edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, maxPointsPerRegion: Int, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>) {
        val (avgX, avgY) = findRegionCenter(pointsWidth, region)
        val farQueue = buildFurthestFirstQueue(avgX, avgY, edgeGraph, landPoints, pointsWidth, region)
        giveAwayFurthestPoints(avgX, avgY, edgeGraph, landPoints, pointsWidth, region, regions, farQueue)
        val nearQueue = buildNearestFirstQueue(farQueue)
        takeBackNearestPoints(avgX, avgY, edgeGraph, landPoints, maxPointsPerRegion, pointsWidth, region, regions, nearQueue)
    }

    private fun takeBackNearestPoints(avgX: Int, avgY: Int, edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, maxPointsPerRegion: Int, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>, nearQueue: PriorityQueue<Pair<Int, Int>>) {
        for (i in 0..maxPointsPerRegion - region.size - 1) {
            takeBackNearestPoint(avgX, avgY, edgeGraph, landPoints, pointsWidth, region, regions, nearQueue)
        }
    }

    private fun takeBackNearestPoint(avgX: Int, avgY: Int, edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>, nearQueue: PriorityQueue<Pair<Int, Int>>) {
        val pointToTakeNear = nearQueue.remove()
        var take1 = false
        var take2 = false
        edgeGraph[pointToTakeNear.first].forEach inside@ { adjacentPoint ->
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

    private fun giveAwayFurthestPoints(avgX: Int, avgY: Int, edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>, farQueue: PriorityQueue<Pair<Int, Int>>) {
        for (i in 0..4) {
            giveAwayFurthestPoint(avgX, avgY, edgeGraph, landPoints, pointsWidth, region, regions, farQueue)
        }
    }

    private fun buildNearestFirstQueue(farQueue: PriorityQueue<Pair<Int, Int>>): PriorityQueue<Pair<Int, Int>> {
        val nearQueue = PriorityQueue<Pair<Int, Int>>(farQueue.size) { p1, p2 -> p1.second.compareTo(p2.second) }
        nearQueue.addAll(farQueue)
        return nearQueue
    }

    private fun giveAwayFurthestPoint(avgX: Int, avgY: Int, edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, pointsWidth: Int, region: HashSet<Int>, regions: List<HashSet<Int>>, farQueue: PriorityQueue<Pair<Int, Int>>) {
        val pointToTrade = farQueue.remove()
        edgeGraph[pointToTrade.first].forEach { adjacentPoint ->
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

    private fun buildFurthestFirstQueue(avgX: Int, avgY: Int, edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, pointsWidth: Int, region: HashSet<Int>): PriorityQueue<Pair<Int, Int>> {
        val borderPoints = findBorderPoints(edgeGraph, landPoints, region)
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

    private fun equalizeRegions(edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, maxPointsPerRegion: Int, random: Random, regions: List<HashSet<Int>>) {
        val unclaimedLand = HashSet<Int>()
        var lastUnclaimedLand: HashSet<Int>? = null
        do {
            if (unclaimedLand.equals(lastUnclaimedLand)) {
                forceClaimUnwantedLand(edgeGraph, random, regions, unclaimedLand)
            }
            lastUnclaimedLand = unclaimedLand
            regions.forEach { region ->
                giveUpLandIfTooBig(edgeGraph, maxPointsPerRegion, landPoints, random, region, unclaimedLand)
            }
            regions.randomized(random).forEach { region ->
                claimLandIfNeeded(edgeGraph, maxPointsPerRegion, random, region, unclaimedLand)
            }
            regions.forEach {
                giveUpDisconnectedLand(edgeGraph, unclaimedLand, it)
            }
        } while (unclaimedLand.isNotEmpty())
    }

    private fun forceClaimUnwantedLand(edgeGraph: ArrayList<ArrayList<Int>>, random: Random, regions: List<HashSet<Int>>, unclaimedLand: HashSet<Int>) {
        while (unclaimedLand.isNotEmpty()) {
            regions.randomized(random).forEach { region ->
                tryAndClaimLand(edgeGraph, unclaimedLand, region, random)
            }
        }
    }

    private fun giveUpLandIfTooBig(edgeGraph: ArrayList<ArrayList<Int>>, maxPointsPerRegion: Int, landPoints: HashSet<Int>, random: Random, region: HashSet<Int>, unclaimedLand: HashSet<Int>) {
        if (region.size > maxPointsPerRegion) {
            val borderPoints = ArrayList(findBorderPoints(edgeGraph, landPoints, region))
            for (i in 0..region.size - (maxPointsPerRegion + 6)) {
                if (borderPoints.isNotEmpty()) {
                    val pointToGive = borderPoints.removeAt(random.nextInt(borderPoints.size))
                    region.remove(pointToGive)
                    unclaimedLand.add(pointToGive)
                }
            }
        }
    }

    private fun claimLandIfNeeded(edgeGraph: ArrayList<ArrayList<Int>>, maxPointsPerRegion: Int, random: Random, region: HashSet<Int>, unclaimedLand: HashSet<Int>) {
        if (region.size < maxPointsPerRegion) {
            for (i in 0..maxPointsPerRegion - region.size - 1) {
                tryAndClaimLand(edgeGraph, unclaimedLand, region, random)
            }
        }
    }

    private fun <T> List<T>.randomized(random: Random): ArrayList<T> {
        val randomized = ArrayList(this)
        Collections.shuffle(randomized, random)
        return randomized
    }

    private fun giveUpDisconnectedLand(edgeGraph: ArrayList<ArrayList<Int>>, unclaimedLand: HashSet<Int>, region: HashSet<Int>) {
        val regionBodies = ArrayList<Set<Int>>()
        val regionPointsToClaim = HashSet<Int>(region)
        while (regionPointsToClaim.isNotEmpty()) {
            regionBodies.add(buildLandBody(edgeGraph, regionPointsToClaim))
        }
        regionBodies.sortBy { it.size }
        regionBodies.removeAt(regionBodies.size - 1)
        regionBodies.forEach {
            region.removeAll(it)
            unclaimedLand.addAll(it)
        }
    }

    private fun tryAndClaimLand(edgeGraph: ArrayList<ArrayList<Int>>, unclaimedLand: HashSet<Int>, region: HashSet<Int>, random: Random) {
        val landToClaim = ArrayList(unclaimedLand)
        Collections.shuffle(landToClaim, random)
        landToClaim.forEach {
            edgeGraph[it].forEach { adjacentPoint ->
                if (region.contains(adjacentPoint)) {
                    unclaimedLand.remove(it)
                    region.add(it)
                }
            }
        }
    }

    private fun findBorderPoints(edgeGraph: ArrayList<ArrayList<Int>>, landPoints: HashSet<Int>, region: HashSet<Int>): Set<Int> {
        val borderPoints = HashSet<Int>()
        region.forEach {
            edgeGraph[it].forEach inside@ { adjacentPoint ->
                if (landPoints.contains(adjacentPoint) && !region.contains(adjacentPoint)) {
                    borderPoints.add(it)
                    return@inside
                }
            }
        }
        return borderPoints
    }

    fun connectIslands(edgeGraph: ArrayList<ArrayList<Int>>, landPoints: Set<Int>, pointsWidth: Int) {
        val landBodies = ArrayList<Set<Int>>()
        val unclaimedPoints = HashSet<Int>(landPoints)
        while (unclaimedPoints.isNotEmpty()) {
            landBodies.add(buildLandBody(edgeGraph, unclaimedPoints))
        }
        landBodies.sortBy { it.size }
        val mainland = landBodies.removeAt(landBodies.size - 1)
        landBodies.forEach { island ->
            var closestPair: Triple<Int, Int, Int>? = null
            island.forEach { islandPoint ->
                if (isCoastalPoint(edgeGraph, islandPoint, landPoints)) {
                    val localClosestPair = findClosestPointAndDistance(islandPoint, mainland, pointsWidth)
                    if (closestPair == null || localClosestPair.second < closestPair!!.third) {
                        closestPair = Triple(islandPoint, localClosestPair.first, localClosestPair.second)
                    }
                }
            }
            edgeGraph[closestPair!!.first].add(closestPair!!.second)
            edgeGraph[closestPair!!.second].add(closestPair!!.first)
        }
    }

    private fun isCoastalPoint(edgeGraph: ArrayList<ArrayList<Int>>, islandPoint: Int, landPoints: Set<Int>): Boolean {
        edgeGraph[islandPoint].forEach { adjacentPoint ->
            if (!landPoints.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    private fun findClosestPointAndDistance(islandPoint: Int, mainland: Set<Int>, pointsWidth: Int): Pair<Int, Int> {
        var closestPair: Pair<Int, Int>? = null
        var radius = 1
        while (closestPair == null) {
            closestPair = findApproximatelyClosestPoint(islandPoint, mainland, pointsWidth, radius)
            if (closestPair != null) {
                if (closestPair.second <= radius) {
                    break
                } else {
                    for (biggerRadius in radius + 1..closestPair!!.second) {
                        val otherClosestPair = findApproximatelyClosestPoint(islandPoint, mainland, pointsWidth, biggerRadius)
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

    private fun findApproximatelyClosestPoint(islandPoint: Int, mainland: Set<Int>, pointsWidth: Int, boxRadius: Int): Pair<Int, Int>? {
        var closestDist = Int.MAX_VALUE
        var closestIndex: Int? = null
        val x0 = islandPoint % pointsWidth
        val y0 = islandPoint / pointsWidth
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
                val x1 = it % pointsWidth
                val y1 = it / pointsWidth
                val xDelta = x0 - x1
                val yDelta = y0 - y1
                val dist = xDelta * xDelta + yDelta * yDelta
                if (dist < closestDist) {
                    closestDist = dist
                    closestIndex = it
                }
            }
        }
        if (closestIndex != null) {
            return Pair(closestIndex!!, Math.ceil(Math.sqrt(closestDist.toDouble())).toInt())
        }
        return null
    }

    private fun buildLandBody(edgeGraph: List<List<Int>>, landPoints: MutableSet<Int>): Set<Int> {
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
                addAllConnectedPoints(edgeGraph, landPoints, landBody, growSet, index)
            }
        }
        return landBody
    }


    private fun findLandPoints(mask: Matrix<Int>, pointCount: Int): HashSet<Int> {
        val landPoints = HashSet<Int>(pointCount)
        for (i in 0..pointCount - 1) {
            if (mask[i] > 0) {
                landPoints.add(i)
            }
        }
        return landPoints
    }

    private fun buildUpRegions(edgeGraph: List<List<Int>>, unclaimedPoints: MutableSet<Int>, regions: List<HashSet<Int>>) {
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
                addAllConnectedPoints(edgeGraph, unclaimedPoints, region.regionPoints, region.growSet, pointIndex)
                queue.add(region)
            }
        }
    }

    private fun addAllConnectedPoints(edgeGraph: List<List<Int>>, unclaimedPoints: MutableSet<Int>, regionPoints: MutableSet<Int>, growSet: MutableSet<Int>, index: Int) {
        edgeGraph[index].forEach { adjacentIndex ->
            if (unclaimedPoints.contains(adjacentIndex) && !regionPoints.contains(adjacentIndex)) {
                unclaimedPoints.remove(adjacentIndex)
                regionPoints.add(adjacentIndex)
                growSet.add(adjacentIndex)
            }
        }
    }


    private fun pickStartingPoints(mask: Matrix<Int>, points: Matrix<ClosestPoints>, random: Random): Set<Int> {
        val minDistSquared = minDistanceBetweenPointsSquared()
        val startingPoints = HashSet<Pair<Int, Point>>()
        while (startingPoints.size < regionCount) {
            var iterations = 0
            while (iterations < 1000 && startingPoints.size < regionCount) {
                val closestPointIndex = (random.nextDouble() * points.size).toLong()
                val x = (closestPointIndex % points.width).toInt()
                val y = (closestPointIndex / points.width).toInt()
                val closestPoint = points[x, y].p0!!
                val pointIndex = closestPoint.first
                if (mask[pointIndex] > 0) {
                    val point = Point(x.toFloat() / points.width, y.toFloat() / points.width)
                    var isFarEnoughApart = true
                    startingPoints.forEach { startingPoint ->
                        if (point.distanceSquaredTo(startingPoint.second) < minDistSquared) {
                            isFarEnoughApart = false
                            return@forEach
                        }
                    }
                    if (isFarEnoughApart) {
                        startingPoints.add(Pair(pointIndex, point))
                    }
                    iterations++
                }
            }
        }
        return startingPoints.map { it.first }.toSet()
    }

    private fun minDistanceBetweenPointsSquared(): Float {
        var minDistSquared = 1 / regionCount.toFloat()
        minDistSquared *= minDistSquared
        return minDistSquared * 2
    }
}
