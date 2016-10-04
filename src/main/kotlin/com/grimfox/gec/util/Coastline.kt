package com.grimfox.gec.util

import com.grimfox.gec.command.BuildContinent.Parameters
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.LineSegment2F.Companion.getConnectedEdgeSegments
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Polygon2F
import com.grimfox.gec.util.Utils.pow
import java.util.*

object Coastline {

    fun refineCoastline(graph: Graph, random: Random, idMask: Matrix<Int>, parameters: Parameters) {
        val approximateAreaPerPoint = 1.0f / graph.vertices.size
        val pointsPerRegion = Math.round(parameters.minRegionSize / approximateAreaPerPoint)
        val borderPoints = buildBorderPoints(graph)
        val water = extractWaterFromIds(graph, idMask)
        applyBorderConstraintsToWater(water, borderPoints, idMask)
        val offLimitPoints = getOffLimitPoints(graph, idMask, water, parameters.protectedInset, parameters.protectedRadius)
        erodeCoastline(graph, water, borderPoints, idMask, random, offLimitPoints, parameters.landPercent, parameters.smallIsland, parameters.largeIsland, parameters.minPerturbation, parameters.maxIterations, pointsPerRegion)
        forceClaimUnwantedLand(graph, idMask, forceGiveUpDisconnectedLand(graph, idMask))
    }

    fun applyMask(graph: Graph, maskGraph: Graph, mask: Matrix<Int>) : Matrix<Int> {
        val borderPoints = buildBorderPoints(maskGraph)
        val land = extractLandFromIds(maskGraph, mask)
        applyBorderConstraintsToLand(land, borderPoints, mask)
        val bodies = maskGraph.getConnectedBodies(land).sortedByDescending { it.size }
        val bodyIds = ArrayListMatrix(maskGraph.stride!!) { vertexId ->
            var bodyId = 0
            for (i in 0..bodies.size - 1) {
                val body = bodies[i]
                if (body.contains(vertexId)) {
                    bodyId = i + 1
                    break
                }
            }
            bodyId
        }
        val bodyBorders = ArrayList<LineSegment2F>()
        bodies.forEach {
            bodyBorders.addAll(maskGraph.findBorderEdges(it))
        }
        val vertices = graph.vertices
        val newBodyIds = ArrayListMatrix(graph.stride!!) { 0 }
        val newMask = ArrayListMatrix(graph.stride) { vertexId ->
            val vertex = vertices[vertexId]
            val point = vertex.point
            val closePoint = maskGraph.getClosestPoint(point)
            newBodyIds[vertexId] = bodyIds[closePoint]
            mask[closePoint]
        }
        val newBodies = ArrayList<LinkedHashSet<Int>>(bodies.size)
        for (i in 0..bodies.size - 1) {
            newBodies.add(LinkedHashSet())
        }
        for (i in 0..vertices.size - 1) {
            val bodyId = newBodyIds[i] - 1
            if (bodyId > -1) {
                newBodies[bodyId].add(i)
            }
        }
        newBodies.forEachIndexed { currentBodyIndex, body ->
            val currentBodyId = currentBodyIndex + 1
            body.forEach { currentId ->
                val adjacents = vertices.getAdjacentVertices(currentId)
                for (i in 0..adjacents.size - 1) {
                    val adjacentBodyId = newBodyIds[adjacents[i]]
                    if (adjacentBodyId > 0 && adjacentBodyId != currentBodyId) {
                        newBodyIds[currentId] = 0
                        newMask[currentId] = 0
                    }
                }
            }

        }
        val newBorderPoints = buildBorderPoints(graph)
        val water = extractWaterFromIds(graph, newMask)
        applyBorderConstraintsToWater(water, newBorderPoints, newMask)

        val waterBodies = graph.getConnectedBodies(water)
        if (waterBodies.size > 1) {
            waterBodies.sortBy { it.size }
            val ocean = waterBodies.last()
            for (i in 0..waterBodies.size - 1) {
                val lake = waterBodies[i]
                var sumX = 0.0f
                var sumY = 0.0f
                lake.forEach {
                    val lakePoint = vertices[it].point
                    sumX += lakePoint.x
                    sumY += lakePoint.y
                }
                val lakeCenter = Point2F(sumX / lake.size, sumY / lake.size)
                val sortedBorders = ArrayList(bodyBorders.sortedBy { Math.min(it.a.distance2(lakeCenter), it.b.distance2(lakeCenter)) })
                while (sortedBorders.isNotEmpty()) {
                    val nearestBorder = sortedBorders.removeAt(0)
                    val lakeShore = lake.flatMap { vertices.getAdjacentVertices(it) }.toSet().filter { !lake.contains(it) }.toHashSet()
                    var oceanConnected = false
                    lakeShore.forEach {
                        if (cellIntersectsEdge(nearestBorder, vertices[it].cell)) {
                            lake.add(it)
                            if (isOceanConnected(vertices, ocean, it)) {
                                oceanConnected = true
                            }
                        }
                    }
                    if (oceanConnected) {
                        lake.forEach {
                            newMask[it] = 0
                        }
                        ocean.addAll(lake)
                        break
                    }
                }
            }
        }
        return newMask
    }

    fun getCoastline(graph: Graph, idMask: Matrix<Int>): ArrayList<Polygon2F> {
        val land = extractLandFromIds(graph, idMask)
        val bodies = graph.getConnectedBodies(land).sortedByDescending { it.size }
        return ArrayList(bodies.flatMap { graph.findBorder(it) })
    }

    fun getBorders(graph: Graph, idMask: Matrix<Int>, body: LinkedHashSet<Int>): ArrayList<Polygon2F> {
        return getBorders(graph, idMask, body, false)
    }

    fun getBorders(graph: Graph, idMask: Matrix<Int>): ArrayList<ArrayList<Polygon2F>> {
        val land = extractLandFromIds(graph, idMask)
        val bodies = graph.getConnectedBodies(land).sortedByDescending { it.size }
        return ArrayList(bodies.map { getBorders(graph, idMask, it) })
    }

    private fun isOceanConnected(vertices: Vertices, ocean: LinkedHashSet<Int>, id: Int): Boolean {
        vertices.getAdjacentVertices(id).forEach {
            if (ocean.contains(it)) {
                return true
            }
        }
        return false
    }

    private fun cellIntersectsEdge(edge: LineSegment2F, cell: Cell): Boolean {
        cell.borderEdges.forEach {
            if (edge.intersectsOrTouches(it)) {
                return true
            }
        }
        return false
    }

    private fun getOffLimitPoints(graph: Graph, idMask: Matrix<Int>, water: LinkedHashSet<Int>, inset: Float, radius: Float): LinkedHashSet<Int> {
        val borderPoints = insetBorders(getBorders(graph, idMask, water, true), inset).flatMap { it.points }.toSet()
        val offLimitPoints = LinkedHashSet<Int>()
        borderPoints.forEach {
            offLimitPoints.addAll(graph.getPointsWithinRadius(it, radius))
        }
        return offLimitPoints
    }

    private fun getBorders(graph: Graph, idMask: Matrix<Int>, mask: LinkedHashSet<Int>, negate: Boolean = false): ArrayList<Polygon2F> {
        val regions = extractRegionsFromIds(graph, idMask)
        val borders = ArrayList<Polygon2F>()
        regions.forEach {
            borders.addAll(graph.findBorder(it, mask, negate))
        }
        return borders
    }

    private fun insetBorders(borders: ArrayList<Polygon2F>, inset: Float): ArrayList<Polygon2F> {
        val reducedBorders = ArrayList<Polygon2F>()
        borders.forEach {
            val points = it.points
            ArrayList(points)
            var dist = 0.0
            var rangeStart = 0
            for (i in 0..points.size - 2) {
                val localDist = Math.sqrt((points[i].distance2(points[i + 1])).toDouble())
                dist += localDist
                if (dist > inset) {
                    rangeStart = i + 1
                    break
                }
            }
            dist = 0.0
            var rangeEnd = points.size
            val sizeMinus1 = points.size - 1
            for (i in 0..points.size - 2) {
                val localDist = Math.sqrt((points[sizeMinus1 - i - 1].distance2(points[sizeMinus1 - i])).toDouble())
                dist += localDist
                if (dist > inset) {
                    rangeEnd = it.points.size - (i + 1)
                    break
                }
            }
            if (rangeStart > rangeEnd) {
                reducedBorders.add(it)
            } else {
                reducedBorders.add(Polygon2F(it.points.subList(Math.max(0, rangeStart), Math.min(it.points.size, rangeEnd)), false))
            }
        }
        return reducedBorders
    }

    private fun extractWaterFromIds(graph: Graph, idMask: Matrix<Int>): LinkedHashSet<Int> {
        val water = LinkedHashSet<Int>()
        for (i in 0..graph.vertices.size - 1) {
            if (idMask[i] == 0) {
                water.add(i)
            }
        }
        return water
    }

    private fun applyBorderConstraintsToWater(water: LinkedHashSet<Int>, borderPoints: LinkedHashSet<Int>, idMask: Matrix<Int>) {
        water.addAll(borderPoints)
        borderPoints.forEach {
            idMask[it] = 0
        }
    }

    private fun applyBorderConstraintsToLand(land: LinkedHashSet<Int>, borderPoints: LinkedHashSet<Int>, idMask: Matrix<Int>) {
        land.removeAll(borderPoints)
        borderPoints.forEach {
            idMask[it] = 0
        }
    }

    private fun extractLandFromIds(graph: Graph, idMask: Matrix<Int>): LinkedHashSet<Int> {
        val land = LinkedHashSet<Int>()
        for (i in 0..graph.vertices.size - 1) {
            if (idMask[i] != 0) {
                land.add(i)
            }
        }
        return land
    }

    private fun removeIslands(graph: Graph, waterPoints: LinkedHashSet<Int>, idMask: Matrix<Int>, smallIsland: Int, largeIsland: Int) {
        val pointCount = graph.vertices.size
        if (waterPoints.size == pointCount) {
            return
        }
        val landPoints = LinkedHashSet<Int>(pointCount)
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
                it.forEach { pointId ->
                    idMask[pointId] = 0
                }
            }
        }
    }

    private fun removeLakes(graph: Graph, waterPoints: LinkedHashSet<Int>, borderPoints: LinkedHashSet<Int>, idMask: Matrix<Int>) {
        val vertices = graph.vertices
        val oceanPoints = LinkedHashSet<Int>()
        var growSet = LinkedHashSet<Int>(borderPoints)
        var iterateSet: LinkedHashSet<Int>
        while (growSet.isNotEmpty()) {
            oceanPoints.addAll(growSet)
            iterateSet = growSet
            growSet = LinkedHashSet<Int>()
            iterateSet.forEach { index ->
                addAllConnectedPoints(graph, waterPoints, oceanPoints, growSet, index)
            }
        }
        val lakeSet = LinkedHashSet<Int>()
        waterPoints.forEach {
            if (!oceanPoints.contains(it)) {
                lakeSet.add(it)
            }
        }
        waterPoints.removeAll(lakeSet)
        while (lakeSet.isNotEmpty()) {
            val lakeShore = ArrayList<Int>()
            lakeSet.forEach { lakeId ->
                vertices.getAdjacentVertices(lakeId).forEach { adjacent ->
                    val adjacentMask = idMask[adjacent]
                    if (adjacentMask > 0) {
                        lakeShore.add(lakeId)
                        idMask[lakeId] = adjacentMask
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

    private fun erodeCoastline(graph: Graph, waterPoints: LinkedHashSet<Int>, borderPoints: LinkedHashSet<Int>, idMask: Matrix<Int>, random: Random, offLimitPoints: LinkedHashSet<Int>, landPercent: Float, smallIsland: Float, largeIsland: Float, minPerturbation: Float, maxIterations: Int, pointsPerRegion: Int) {
        val maxLandPointCount = graph.vertices.size - borderPoints.size
        val desiredLandPointCount = Math.min(maxLandPointCount, Math.round(graph.vertices.size * landPercent))
        val largeIslandCount = Math.max(0, Math.min(Int.MAX_VALUE.toLong(), Math.round(largeIsland.toDouble() * graph.vertices.size)).toInt())
        val smallIslandCount = Math.max(0, Math.min(Int.MAX_VALUE.toLong(), Math.round(smallIsland.toDouble() * graph.vertices.size)).toInt())
        var coastalPoints = buildCoastalPoints(graph, waterPoints)
        var coastalPointDegrees = buildCoastalPointDegreeSets(coastalPoints)
        ensureSufficientLandForPerturbation(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, desiredLandPointCount, minPerturbation, maxIterations)
        removeIslands(graph, waterPoints, idMask, smallIslandCount, largeIslandCount)
        removeLakes(graph, waterPoints, borderPoints, idMask)
        coastalPoints = buildCoastalPoints(graph, waterPoints)
        coastalPointDegrees = buildCoastalPointDegreeSets(coastalPoints)
        var landPointCount = graph.vertices.size - waterPoints.size
        var pointCountToRemove = landPointCount - desiredLandPointCount
        var i = 0
        while (pointCountToRemove > 0 && i < maxIterations) {
            modifyCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, offLimitPoints, pointCountToRemove, pointsPerRegion)
            removeIslands(graph, waterPoints, idMask, smallIslandCount, largeIslandCount)
            removeLakes(graph, waterPoints, borderPoints, idMask)
            coastalPoints = buildCoastalPoints(graph, waterPoints)
            coastalPointDegrees = buildCoastalPointDegreeSets(coastalPoints)
            landPointCount = graph.vertices.size - waterPoints.size
            pointCountToRemove = landPointCount - desiredLandPointCount
            i++
        }
        i = 0
        while (pointCountToRemove > 0 && i < maxIterations) {
            reduceCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, idMask, random, offLimitPoints, pointCountToRemove, pointsPerRegion)
            pointCountToRemove = landPointCount - desiredLandPointCount
            removeIslands(graph, waterPoints, idMask, smallIslandCount, largeIslandCount)
            removeLakes(graph, waterPoints, borderPoints, idMask)
            coastalPoints = buildCoastalPoints(graph, waterPoints)
            coastalPointDegrees = buildCoastalPointDegreeSets(coastalPoints)
            i++
        }
        removeLakes(graph, waterPoints, borderPoints, idMask)
        removeIslands(graph, waterPoints, idMask, smallIslandCount, largeIslandCount)
    }

    private fun ensureSufficientLandForPerturbation(graph: Graph,
                                                    waterPoints: LinkedHashSet<Int>,
                                                    coastalPoints: HashMap<Int, Int>,
                                                    coastalPointDegrees: ArrayList<ArrayList<Int>>,
                                                    borderPoints: LinkedHashSet<Int>,
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

    private fun modifyCoastline(graph: Graph, waterPoints: LinkedHashSet<Int>, coastalPoints: HashMap<Int, Int>, coastalPointDegrees: ArrayList<ArrayList<Int>>, borderPoints: LinkedHashSet<Int>, idMask: Matrix<Int>, random: Random, offLimitPoints: LinkedHashSet<Int>, pointCountToRemove: Int, pointsPerRegion: Int) {
        val adjustedCount = pointCountToRemove + pointCountToRemove / 4
        var skips = reduceCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, idMask, random, offLimitPoints, adjustedCount, pointsPerRegion)
        var pointCountToAdd = (adjustedCount - skips) / 2
        skips = buildUpCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, pointCountToAdd)
        val actualRemoved = adjustedCount - (pointCountToAdd - skips)
        if (actualRemoved > pointCountToRemove) {
            pointCountToAdd = actualRemoved - pointCountToRemove
            buildUpCoastline(graph, waterPoints, coastalPoints, coastalPointDegrees, borderPoints, idMask, random, pointCountToAdd)
        }
    }

    private fun buildBorderPoints(graph: Graph): LinkedHashSet<Int> {
        return LinkedHashSet(graph.vertices.filter { it.cell.isBorder }.map { it.id })
    }

    private fun buildCoastalPoints(graph: Graph, waterPoints: LinkedHashSet<Int>): HashMap<Int, Int> {
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
        val coastalPointDegreeSets = ArrayList<LinkedHashSet<Int>>(6)
        for (i in 0..5) {
            coastalPointDegreeSets.add(LinkedHashSet<Int>())
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

    private fun reduceCoastline(graph: Graph, waterPoints: LinkedHashSet<Int>, coastalPoints: HashMap<Int, Int>, coastalPointDegrees: ArrayList<ArrayList<Int>>, idMask: Matrix<Int>, random: Random, offLimitPoints: LinkedHashSet<Int>, iterations: Int, pointsPerRegion: Int): Int {
        val regions = extractRegionsFromIds(graph, idMask)
        val vertices = graph.vertices
        var skips = 0
        var i = 0
        while (i < iterations) {
            val pickList = degreeWeightedPick(coastalPointDegrees, random)
            if (pickList.isEmpty()) {
                skips++
                i++
                continue
            }
            val idChoices = (0..pickList.size - 1).toMutableList()
            Collections.shuffle(idChoices, random)
            var index = 0
            var id = -1
            while (index < idChoices.size) {
                val tempId = idChoices[index]
                val tempPick = pickList[tempId]
                val currentRegion = regions[idMask[tempPick] - 1]
                if (!offLimitPoints.contains(tempPick) && currentRegion.size > pointsPerRegion) {
                    id = tempId
                    break
                }
                index++
            }
            if (id == -1) {
                skips++
                i++
                continue
            }
            val pickPoint = pickList.removeAt(id)
            waterPoints.add(pickPoint)
            regions[idMask[pickPoint] - 1].remove(pickPoint)
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
            i++
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

    private fun buildUpCoastline(graph: Graph, waterPoints: LinkedHashSet<Int>, coastalPoints: HashMap<Int, Int>, coastalPointDegrees: ArrayList<ArrayList<Int>>, borderPoints: Set<Int>, idMask: Matrix<Int>, random: Random, iterations: Int): Int {
        val vertices = graph.vertices
        var skips = 0
        for (i in 1..iterations) {
            val pickList = degreeWeightedPick(coastalPointDegrees, random)
            if (pickList.isEmpty()) {
                skips++
                continue
            }
            val randomizedPickList = ArrayList((0..pickList.size - 1).toList())
            Collections.shuffle(randomizedPickList, random)
            var coastPick: Int = -1
            var pickPoint: Int = -1
            for (j in 0..randomizedPickList.size - 1) {
                val coastId = pickList[randomizedPickList[j]]
                val adjacentWater = ArrayList<Int>()
                vertices.getAdjacentVertices(coastId).forEach { adjacentPointIndex ->
                    if (waterPoints.contains(adjacentPointIndex)) {
                        adjacentWater.add(adjacentPointIndex)
                    }
                }
                if (adjacentWater.isEmpty()) {
                    continue
                }
                val pickId = adjacentWater[random.nextInt(adjacentWater.size)]
                if (borderPoints.contains(pickId)) {
                    continue
                }
                val landAdjacentEdges = LinkedHashSet<LineSegment2F>()
                val pickCell = vertices[pickId].cell
                vertices.getAdjacentVertices(pickId).forEach { adjacentId ->
                    if (idMask[adjacentId] > 0) {
                        val sharedEdge = pickCell.sharedEdge(vertices[adjacentId].cell)
                        if (sharedEdge != null) {
                            landAdjacentEdges.add(sharedEdge)
                        }
                    }
                }
                if (getConnectedEdgeSegments(landAdjacentEdges).size > 1) {
                    continue
                }
                coastPick = coastId
                pickPoint = pickId
                break
            }
            if (pickPoint == -1) {
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

    private fun forceGiveUpDisconnectedLand(graph: Graph, idMask: Matrix<Int>): LinkedHashSet<Int> {
        val unclaimedLand = LinkedHashSet<Int>()
        extractRegionsFromIds(graph, idMask).forEach {
            giveUpDisconnectedLand(graph, unclaimedLand, it)
        }
        unclaimedLand.forEach {
            idMask[it] = -1
        }
        return unclaimedLand
    }

    private fun extractRegionsFromIds(graph: Graph, idMask: Matrix<Int>): ArrayList<LinkedHashSet<Int>> {
        val regions = ArrayList<LinkedHashSet<Int>>()
        for (i in 0..graph.vertices.size - 1) {
            val regionId = idMask[i]
            if (regionId > 0) {
                val regionIndex = regionId - 1
                if (regions.size < regionId) {
                    for (j in regions.size..regionIndex) {
                        regions.add(LinkedHashSet<Int>())
                    }
                }
                regions[regionIndex].add(i)
            }
        }
        return regions
    }

    private fun giveUpDisconnectedLand(graph: Graph, unclaimedLand: LinkedHashSet<Int>, region: LinkedHashSet<Int>) {
        val regionBodies = graph.getConnectedBodies(region)
        if (regionBodies.size > 1) {
            regionBodies.sortBy { it.size }
            regionBodies.removeAt(regionBodies.size - 1)
            regionBodies.forEach { body ->
                unclaimedLand.addAll(body)
            }
        }
    }

    private fun forceClaimUnwantedLand(graph: Graph, idMask: Matrix<Int>, unclaimedLand: LinkedHashSet<Int>) {
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

    private fun tryClaimLand(vertices: Vertices, idMask: Matrix<Int>, unclaimedLand: LinkedHashSet<Int>, unclaimedId: Int) {
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