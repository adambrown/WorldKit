package com.grimfox.gec.util

import com.grimfox.gec.command.BuildContinent.ParameterSet
import com.grimfox.gec.model.ArrayListMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.Point
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils.generatePoints
import java.util.*

object Regions {

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

    fun buildRegions(parameters: ParameterSet): Pair<Graph, Matrix<Int>> {
        val random = Random(parameters.seed)
        val results = ArrayList<Triple<Graph, Matrix<Int>, Int>>()
        for (i in 0..parameters.islandDesire) {
            results.add(buildRegions(random, parameters, i))
        }
        val winner = results.filter { it.third <= parameters.islandDesire }.sortedByDescending { it.third }.first()
        return Pair(winner.first, winner.second)
    }

    private fun buildRegions(random: Random, parameters: ParameterSet, islandDesire: Int): Triple<Graph, Matrix<Int>, Int> {
        var bestGraphValue = Float.MIN_VALUE
        var bestPair: Triple<Graph, Matrix<Int>, Int>? = null
        var check1Fails = 0
        var check2Fails = 0
        var check3Fails = 0
        var check4Fails = 0
        var tries = 0
        while (tries < parameters.maxRegionTries) {
            val virtualWidth = 100000.0f
            val graph = buildGraph(parameters.stride, virtualWidth, generatePoints(parameters.stride, virtualWidth, random))
            val (interiorVertices, islandCount) = findInteriorVertices(graph, random, parameters, islandDesire, parameters.maxIslandTries)
            val possibleRegions = pickStartRegions(graph, interiorVertices, pickStartCells(graph, random, interiorVertices, parameters.regionCount))
            var bestValue = Float.MIN_VALUE
            var bestValueId = -1
            var fixerValue = Float.MIN_VALUE
            var fixerId = 0
            possibleRegions.forEachIndexed { i, regionSet ->
                var minConnectedness = Float.MAX_VALUE
                var minPoints = Int.MAX_VALUE
                var minSize = Float.MAX_VALUE
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
                    if (minSize > region.area) {
                        minSize = region.area
                    }
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
                val setValue = minConnectedness * (1.0f - maxSpread) * (1.0f - maxDeviation) * minSize
                val check1 = minConnectedness >= parameters.connectedness
                val check2 = minPoints >= parameters.regionPoints
                val check3 = minSize >= parameters.regionSize
                val check4 = setValue > bestValue
                if (!check1) check1Fails++
                if (!check2) check2Fails++
                if (!check3) check3Fails++
                if (!check4) check4Fails++
                if (check1 && check2 && check3 && check4) {
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
                    bestPair = Triple(graph, ArrayListMatrix(graph.stride) { findRegionId(possibleRegions[fixerId], it) }, islandCount)
                }
            } else {
                return Triple(graph, ArrayListMatrix(graph.stride) { findRegionId(possibleRegions[bestValueId], it) }, islandCount)
            }
            tries++
        }
        println("check1Fails: $check1Fails\ncheck2Fails: $check2Fails\ncheck3Fails: $check3Fails\ncheck4Fails: $check4Fails\n")
        return bestPair!!
    }

    private fun findInteriorVertices(graph: Graph, random: Random, parameters: ParameterSet, islandDesire: Int, islandTries: Int): Pair<HashMap<Int, Vertex>, Int> {
        val desires = ArrayList<Pair<Int, Int>>()
        if (islandDesire > 0) {
            var maxIterations = islandTries
            for (i in 1..islandDesire) {
                desires.add(Pair(i, maxIterations))
                maxIterations /= 10
            }
            desires.reverse()
        }
        val maxBodies = 1 + islandDesire
        var seed = random.nextLong()
        var maxBodyCount = -1
        var maxBodySeed = seed
        var tries = 0
        while (true) {
            if (desires.isNotEmpty()) {
                val desire = desires.first()
                if (maxBodyCount > desire.first) {
                    seed = maxBodySeed
                }
            } else if (maxBodyCount > 0) {
                seed = maxBodySeed
            }
            val localRandom = Random(seed)
            val vertices = ArrayList(graph.vertices.toList())
            val interiorVertices = hashMapOf(*vertices.filter { !it.cell.isBorder }.map { Pair(it.id, it) }.toTypedArray())
            val interiorVertexIds = HashSet(interiorVertices.keys)
            for (i in 1..parameters.initialReduction) {
                val borderPoints = ArrayList(graph.findBorderIds(interiorVertexIds))
                val idToRemove = borderPoints[localRandom.nextInt(borderPoints.size)]
                interiorVertexIds.remove(idToRemove)
                interiorVertices.remove(idToRemove)
            }
            val bodies = graph.getConnectedBodies(interiorVertexIds)
            val isViable = bodies.size <= maxBodies && bodies.map { it.size }.min()!! >= parameters.regionPoints
            if (isViable && bodies.size > maxBodyCount) {
                maxBodyCount = bodies.size
                maxBodySeed = seed
            }
            if (desires.isNotEmpty()) {
                val desire = desires.first()
                if (tries >= desire.second) {
                    desires.removeAt(0)
                }
                if (tries <= desire.second && bodies.size < desire.first + 1) {
                    seed = random.nextLong()
                    tries++
                    continue
                }
            }
            if (isViable) {
                return Pair(interiorVertices, bodies.size - 1)
            }
            seed = random.nextLong()
            tries++
        }
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
}