package com.grimfox.gec.util

import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.util.Utils.pow
import java.util.*

object Coastline {

    fun refineCoastline(graph: Graph, random: Random, idMask: Matrix<Int>, landPercent: Float = 0.6f, smallIsland: Float = 0.0f, largeIsland: Float = 0.0f, minPerturbation: Float = 0.02f, maxIterations: Int = 2) {
        val borderPoints = buildBorderPoints(graph)
        val water = extractWaterFromIds(borderPoints, graph, idMask)
        erodeCoastline(graph, water, borderPoints, idMask, random, landPercent, smallIsland, largeIsland, minPerturbation, maxIterations)
        forceClaimUnwantedLand(graph, idMask, forceGiveUpDisconnectedLand(graph, idMask))
    }

    private fun extractWaterFromIds(borderPoints: HashSet<Int>, graph: Graph, idMask: Matrix<Int>): HashSet<Int> {
        val water = HashSet<Int>()
        for (i in 0..graph.vertices.size - 1) {
            if (idMask[i] == 0) {
                water.add(i)
            }
        }
        water.addAll(borderPoints)
        borderPoints.forEach {
            idMask[it] = 0
        }
        return water
    }

    private fun removeIslands(graph: Graph, waterPoints: HashSet<Int>, idMask: Matrix<Int>, smallIsland: Int, largeIsland: Int) {
        val pointCount = graph.vertices.size
        if (waterPoints.size == pointCount) {
            return
        }
        val landPoints = HashSet<Int>(pointCount)
        for (i in 0..pointCount - 1) {
            if (!waterPoints.contains(i)) {
                landPoints.add(i)
            }
        }
        val landBodies = graph.getConnectedBodies(landPoints)
        landBodies.sortBy { it.size }
        landBodies.removeAt(landBodies.size - 1)
        landBodies.forEach {
            if (it.size < smallIsland || it.size > largeIsland) {
                waterPoints.addAll(it)
                it.forEach {
                    idMask[it] = 0
                }
            }
        }
    }

    private fun removeLakes(graph: Graph, waterPoints: HashSet<Int>, borderPoints: HashSet<Int>, idMask: Matrix<Int>) {
        val vertices = graph.vertices
        val oceanPoints = HashSet<Int>()
        var growSet = HashSet<Int>(borderPoints)
        var iterateSet: HashSet<Int>
        while (growSet.isNotEmpty()) {
            oceanPoints.addAll(growSet)
            iterateSet = growSet
            growSet = HashSet<Int>()
            iterateSet.forEach { index ->
                addAllConnectedPoints(graph, waterPoints, oceanPoints, growSet, index)
            }
        }
        val lakeSet = HashSet<Int>()
        waterPoints.forEach {
            if (!oceanPoints.contains(it)) {
                lakeSet.add(it)
            }
        }
        waterPoints.removeAll(lakeSet)
        while (lakeSet.isNotEmpty()) {
            val lakeShore = ArrayList<Int>()
            lakeSet.forEach {
                vertices.getAdjacentVertices(it).forEach { adjacent ->
                    val adjacentMask = idMask[adjacent]
                    if (adjacentMask > 0) {
                        lakeShore.add(it)
                        idMask[it] = adjacentMask
                    }
                }
            }
            lakeSet.removeAll(lakeShore)
        }
    }

    private fun addAllConnectedPoints(graph: Graph, positiveFilter: Set<Int>, negativeFilter: Set<Int>, growSet: MutableSet<Int>, index: Int) {
        graph.vertices.getAdjacentVertices(index).forEach { adjacentIndex ->
            if (positiveFilter.contains(adjacentIndex) && !negativeFilter.contains(adjacentIndex)) {
                growSet.add(adjacentIndex)
            }
        }
    }

    private fun erodeCoastline(graph: Graph, waterPoints: HashSet<Int>, borderPoints: HashSet<Int>, idMask: Matrix<Int>, random: Random, landPercent: Float, smallIsland: Float, largeIsland: Float, minPerturbation: Float = 0.2f, maxIterations: Int = 5) {
        val coastalPoints = buildCoastalPoints(graph, waterPoints)
        val coastalPointDegrees = buildCoastalPointDegreeSets(coastalPoints)
        val maxLandPointCount = graph.vertices.size - borderPoints.size
        val desiredLandPointCount = Math.min(maxLandPointCount, Math.round(graph.vertices.size * landPercent))
        val largeIslandCount = Math.max(0, Math.min(Int.MAX_VALUE.toLong(), Math.round(largeIsland.toDouble() * graph.vertices.size)).toInt())
        val smallIslandCount = Math.max(0, Math.min(Int.MAX_VALUE.toLong(), Math.round(smallIsland.toDouble() * graph.vertices.size)).toInt())
        ensureSufficientLandForPerturbation(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, desiredLandPointCount, minPerturbation, maxIterations)
        var landPointCount = graph.vertices.size - waterPoints.size
        var pointCountToRemove = landPointCount - desiredLandPointCount
        var i = 0
        while (pointCountToRemove > 0 && i < maxIterations) {
            modifyCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, pointCountToRemove)
            removeIslands(graph, waterPoints, idMask, smallIslandCount, largeIslandCount)
            removeLakes(graph, waterPoints, borderPoints, idMask)
            landPointCount = graph.vertices.size - waterPoints.size
            pointCountToRemove = landPointCount - desiredLandPointCount
            i++
        }
        i = 0
        while (pointCountToRemove > 0 && i < maxIterations) {
            reduceCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, idMask, random, pointCountToRemove)
            pointCountToRemove = landPointCount - desiredLandPointCount
            removeIslands(graph, waterPoints, idMask, smallIslandCount, largeIslandCount)
            removeLakes(graph, waterPoints, borderPoints, idMask)
            i++
        }
        removeLakes(graph, waterPoints, borderPoints, idMask)
        removeIslands(graph, waterPoints, idMask, smallIslandCount, largeIslandCount)
    }

    private fun ensureSufficientLandForPerturbation(graph: Graph,
                                                    waterPoints: HashSet<Int>,
                                                    coastalPoints: HashMap<Int, Int>,
                                                    coastalPointDegrees: ArrayList<ArrayList<Int>>,
                                                    borderPoints: HashSet<Int>,
                                                    idMask: Matrix<Int>,
                                                    random: Random,
                                                    desiredLandPointCount: Int, minPerturbation: Float = 0.2f, maxIterations: Int = 10) {
        val maxLandPointCount = graph.vertices.size - borderPoints.size
        val minimumPerturbation = Math.round(maxLandPointCount * minPerturbation)
        var landPointCount = graph.vertices.size - waterPoints.size
        var pointCountToRemove = landPointCount - desiredLandPointCount
        var i = 0
        while (pointCountToRemove < minimumPerturbation && landPointCount < maxLandPointCount && i < maxIterations) {
            val addFirst = Math.min(minimumPerturbation - pointCountToRemove, maxLandPointCount - landPointCount)
            buildUpCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, addFirst)
            landPointCount = graph.vertices.size - waterPoints.size
            pointCountToRemove = landPointCount - desiredLandPointCount
            i++
        }
    }

    private fun modifyCoastline(graph: Graph, waterPoints: HashSet<Int>, coastalPoints: HashMap<Int, Int>, coastalPointDegrees: ArrayList<ArrayList<Int>>, borderPoints: HashSet<Int>, idMask: Matrix<Int>, random: Random, pointCountToRemove: Int) {
        val adjustedCount = pointCountToRemove + pointCountToRemove / 4
        var skips = reduceCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, idMask, random, adjustedCount)
        var pointCountToAdd = (adjustedCount - skips) / 2
        skips = buildUpCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, pointCountToAdd)
        val actualRemoved = adjustedCount - (pointCountToAdd - skips)
        if (actualRemoved > pointCountToRemove) {
            pointCountToAdd = actualRemoved - pointCountToRemove
            buildUpCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, pointCountToAdd)
        }
    }

    private fun buildBorderPoints(graph: Graph): HashSet<Int> {
        return graph.vertices.filter { it.cell.isBorder }.map { it.id }.toHashSet()
    }

    private fun buildCoastalPoints(graph: Graph, waterPoints: HashSet<Int>): HashMap<Int, Int> {
        val vertices = graph.vertices
        val coastalPoints = HashMap<Int, Int>()
        waterPoints.forEach { waterId ->
            vertices.getAdjacentVertices(waterId).forEach { vertexId ->
                if (!waterPoints.contains(vertexId)) {
                    coastalPoints[vertexId] = coastalPoints.getOrPut(vertexId, { 0 }) + 1
                }
            }
        }
        return coastalPoints
    }

    private fun buildCoastalPointDegreeSets(coastalPoints: HashMap<Int, Int>): ArrayList<ArrayList<Int>> {
        val coastalPointDegreeSets = ArrayList<HashSet<Int>>(6)
        for (i in 0..5) {
            coastalPointDegreeSets.add(HashSet<Int>())
        }
        coastalPoints.forEach { index, degree ->
            var effectiveDegree = degree - 1
            if (effectiveDegree > 5) {
                effectiveDegree = 5
            }
            coastalPointDegreeSets[effectiveDegree].add(index)
        }
        val coastalPointDegrees = ArrayList<ArrayList<Int>>(6)
        for (i in 0..5) {
            coastalPointDegrees.add(ArrayList(coastalPointDegreeSets[i]))
        }
        return coastalPointDegrees
    }

    private fun reduceCoastline(graph: Graph, waterPoints: HashSet<Int>, coastalPoints: HashMap<Int, Int>, coastalPointDegrees: ArrayList<ArrayList<Int>>, idMask: Matrix<Int>, random: Random, iterations: Int): Int {
        val vertices = graph.vertices
        var skips = 0
        for (i in 1..iterations) {
            val pickList = degreeWeightedPick(coastalPointDegrees, random)
            if (pickList.isEmpty()) {
                skips++
                continue
            }
            val pickPoint = pickList.removeAt(random.nextInt(pickList.size))
            waterPoints.add(pickPoint)
            idMask[pickPoint] = 0
            coastalPoints.remove(pickPoint)
            vertices.getAdjacentVertices(pickPoint).forEach { adjacentPointIndex ->
                if (!waterPoints.contains(adjacentPointIndex)) {
                    val degree = coastalPoints.getOrPut(adjacentPointIndex, { 0 }) + 1
                    coastalPoints[adjacentPointIndex] = degree
                    if (degree == 1) {
                        coastalPointDegrees[0].add(adjacentPointIndex)
                    } else {
                        if (degree < 7) {
                            coastalPointDegrees[degree - 2].remove(adjacentPointIndex)
                            coastalPointDegrees[degree - 1].add(adjacentPointIndex)
                        }
                    }
                }
            }
        }
        return skips
    }

    private fun degreeWeightedPick(coastalPointDegrees: ArrayList<ArrayList<Int>>, random: Random): ArrayList<Int> {
        val degreeWeights = ArrayList<Float>(6)
        var subtotal = 0.0f
        for (j in 0..5) {
            val weight = coastalPointDegrees[j].size * 2.pow(j)
            subtotal += weight
            degreeWeights.add(weight.toFloat())
        }
        val total = subtotal.toFloat()
        subtotal = 0.0f
        for (j in 0..5) {
            subtotal += degreeWeights[j] / total
            degreeWeights[j] = subtotal
        }
        val pick = random.nextFloat()
        var pickList = coastalPointDegrees[0]
        for (j in 0..5) {
            if (pick < degreeWeights[j]) {
                val degreeList = coastalPointDegrees[j]
                if (degreeList.isNotEmpty()) {
                    pickList = degreeList
                    break
                }
            }
        }
        return pickList
    }

    private fun buildUpCoastline(graph: Graph, waterPoints: HashSet<Int>, coastalPoints: HashMap<Int, Int>, coastalPointDegrees: ArrayList<ArrayList<Int>>, borderPoints: Set<Int>, idMask: Matrix<Int>, random: Random, iterations: Int): Int {
        val vertices = graph.vertices
        var skips = 0
        for (i in 1..iterations) {
            val pickList = degreeWeightedPick(coastalPointDegrees, random)
            if (pickList.isEmpty()) {
                skips++
                continue
            }
            val coastPick = pickList[random.nextInt(pickList.size)]
            val adjacentWater = ArrayList<Int>()
            vertices.getAdjacentVertices(coastPick).forEach { adjacentPointIndex ->
                if (waterPoints.contains(adjacentPointIndex)) {
                    adjacentWater.add(adjacentPointIndex)
                }
            }
            if (adjacentWater.isEmpty()) {
                skips++
                continue
            }
            val pickPoint = adjacentWater[random.nextInt(adjacentWater.size)]
            if (borderPoints.contains(pickPoint)) {
                skips++
                continue
            }
            waterPoints.remove(pickPoint)
            idMask[pickPoint] = idMask[coastPick]
            var degree = 0
            vertices.getAdjacentVertices(pickPoint).forEach { adjacentPointIndex ->
                if (waterPoints.contains(adjacentPointIndex)) {
                    degree++
                }
            }
            coastalPoints.put(pickPoint, degree)
            vertices.getAdjacentVertices(pickPoint).forEach { adjacentPointIndex ->
                if (!waterPoints.contains(adjacentPointIndex)) {
                    val adjacentDegree = coastalPoints[adjacentPointIndex]
                    if (adjacentDegree != null) {
                        if (adjacentDegree == 1) {
                            coastalPoints.remove(adjacentPointIndex)
                            coastalPointDegrees[0].remove(adjacentPointIndex)
                        } else {
                            coastalPoints[adjacentPointIndex] = adjacentDegree - 1
                            if (adjacentDegree < 7) {
                                coastalPointDegrees[adjacentDegree - 1].remove(adjacentPointIndex)
                                coastalPointDegrees[adjacentDegree - 2].add(adjacentPointIndex)
                            }
                        }
                    }
                }
            }
        }
        return skips
    }

    private fun forceGiveUpDisconnectedLand(graph: Graph, idMask: Matrix<Int>): HashSet<Int> {
        val unclaimedLand = HashSet<Int>()
        val regions = ArrayList<HashSet<Int>>()
        for (i in 0..graph.vertices.size - 1) {
            val regionId = idMask[i]
            if (regionId > 0) {
                val regionIndex = regionId - 1
                if (regions.size < regionId) {
                    for (j in regions.size..regionIndex) {
                        regions.add(HashSet<Int>())
                    }
                }
                regions[regionIndex].add(i)
            }
        }
        regions.forEach {
            giveUpDisconnectedLand(graph, unclaimedLand, it)
        }
        unclaimedLand.forEach {
            idMask[it] = -1
        }
        return unclaimedLand
    }

    private fun giveUpDisconnectedLand(graph: Graph, unclaimedLand: HashSet<Int>, region: HashSet<Int>) {
        val regionBodies = graph.getConnectedBodies(region)
        if (regionBodies.size > 1) {
            regionBodies.sortBy { it.size }
            regionBodies.removeAt(regionBodies.size - 1)
            regionBodies.forEach { body ->
                unclaimedLand.addAll(body)
            }
        }
    }

    private fun forceClaimUnwantedLand(graph: Graph, idMask: Matrix<Int>, unclaimedLand: HashSet<Int>) {
        val vertices = graph.vertices
        var lastSize = 0
        while (unclaimedLand.isNotEmpty() && unclaimedLand.size != lastSize) {
            lastSize = unclaimedLand.size
            ArrayList(unclaimedLand).forEach {
                tryClaimLand(vertices, idMask, unclaimedLand, it)
            }
        }
        if (unclaimedLand.isNotEmpty()) {
            unclaimedLand.forEach {
                idMask[it] = 0
            }
        }
    }

    private fun tryClaimLand(vertices: Vertices, idMask: Matrix<Int>, unclaimedLand: HashSet<Int>, unclaimedId: Int) {
        vertices.getAdjacentVertices(unclaimedId).forEach { adjacentId ->
            val adjacentMask = idMask[adjacentId]
            if (adjacentMask > 0) {
                unclaimedLand.remove(unclaimedId)
                idMask[unclaimedId] = adjacentMask
                return
            }
        }
    }
}