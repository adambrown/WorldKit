package com.grimfox.gec.util

import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils.findConcavityWeights
import com.grimfox.gec.util.drawing.*
import com.grimfox.gec.util.geometry.Geometry
import com.grimfox.gec.util.geometry.Geometry.debugCount
import com.grimfox.gec.util.geometry.Geometry.debugResolution
import com.grimfox.gec.util.geometry.GeometryException
import java.awt.Color
import java.lang.Math.*
import java.util.*

object Rivers {

    enum class NodeType {
        CONTINUATION,
        SYMMETRIC,
        ASYMMETRIC
    }

    class RiverNode(var type: NodeType, var point: Point2F, var priority: Int, var riverPriority: Int, var elevation: Float, var maxTerrainSlope: Float, var region: Int)

    val MAX_CANDIDATE_ELEVATION_DIFF = 0.005f
    val IDEAL_RIVER_EDGE_DISTANCE = 0.017f
    val MIN_RIVER_EDGE_DISTANCE = 0.012f
    val RIVER_EDGE_DISTANCE_SEARCH_RADIUS = 0.02f
    val IDEAL_RIVER_EDGE_DISTANCE_SQUARED = IDEAL_RIVER_EDGE_DISTANCE * IDEAL_RIVER_EDGE_DISTANCE
    val MIN_RIVER_EDGE_DISTANCE_SQUARED = MIN_RIVER_EDGE_DISTANCE * MIN_RIVER_EDGE_DISTANCE
    val MIN_RIVER_SEPARATION = 0.01f
    val MIN_RIVER_SEPARATION_SQUARED = MIN_RIVER_SEPARATION * MIN_RIVER_SEPARATION
    val MIN_DISTANCE_FROM_BORDER = 0.01f
    val MIN_DISTANCE_FROM_BORDER_SQUARED = MIN_DISTANCE_FROM_BORDER * MIN_DISTANCE_FROM_BORDER
    val CONTINUATION_PRIORITY = 0.25f
    val SYMMETRIC_PRIORITY = 0.40f
    val MIN_RIVER_SLOPE = 0.005f
    val MAX_RIVER_SLOPE = 0.37f
    val MIN_TERRAIN_SLOPE = 0.05f
    val MAX_TERRAIN_SLOPE = 0.8f
    val MAX_RIVER_CHILDREN = 3

    fun buildRivers(graph: Graph, idMask: Matrix<Int>, random: Random): ArrayList<Triple<Polygon2F, ArrayList<TreeNode<RiverNode>>, ArrayList<GeometryException>>> {
        val vertices = graph.vertices
        val (water, regions) = extractRegionsFromIdMask(graph, idMask)
        val (beach, coastalWater) = extractBeachFromGraphAndWater(vertices, water)
        val land = LinkedHashSet(regions.flatMap { it })
        val landCenter = calculateBodyCenter(vertices, land)
        val rivers = ArrayList<LinkedHashSet<TreeNode<RiverNode>>>()
        val exceptions = ArrayList<ArrayList<GeometryException>>()
        val bodies = graph.getConnectedBodies(land).sortedByDescending { it.size }
        val inlandRiverMouths = LinkedHashSet<Int>()
        val coastPoints = PointSet2F(0.002f)
        val (coastlines, riverCandidates) = findRiverMouthCandidates(graph, coastPoints, bodies, beach)
        val regionBodyLookup = ArrayList<Int>()
        regions.forEachIndexed { regionId, regionUndivided ->
            val isolatedChunks = detectIsolatedChunks(graph, water, regionUndivided)
            isolatedChunks.forEachIndexed { chunkId, regionTriple ->
                val region = regionTriple.first
                val fakeWater = regionTriple.second
                val negateFakeWater = regionTriple.third
                val testId = regionUndivided.first()
                for (i in 0..bodies.size - 1) {
                    val body = bodies[i]
                    if (body.contains(testId)) {
                        regionBodyLookup.add(i)
                        break
                    }
                }
                val regionBorder = graph.findBorder(region).first()
                val regionInlandBorders = graph.findBorder(region, water, true)
                val fakeInteriorBorderPoints: LinkedHashSet<Int>
                val furthestFromBorder: Int
                val interiorBorderPoints = findBorderPoints(vertices, water, region)
                val coastalBorderPoints = findCoastalPoints(vertices, water, region)
                val terrainSlope: HashMap<Int, Float>
                val riverSlope = if (interiorBorderPoints.isEmpty() || coastalBorderPoints.isEmpty()) {
                    val regionCenter = calculateBodyCenter(vertices, region)
                    val seedId = findFurthestPoint(vertices, region, regionCenter)
                    val bisector = Pair(regionCenter, vertices[seedId].point)
                    val oneSide = LinkedHashSet<Int>()
                    val twoSide = LinkedHashSet<Int>()
                    (interiorBorderPoints + coastalBorderPoints).forEach {
                        val localDist = distanceToLine(vertices, bisector, it)
                        if (localDist >= 0) {
                            oneSide.add(it)
                        } else {
                            twoSide.add(it)
                        }
                    }
                    if (interiorBorderPoints.isEmpty()) {
                        val landward = distanceToLine(bisector, landCenter)
                        val fakeCoastPoints = if (landward >= 0) oneSide else twoSide
                        fakeInteriorBorderPoints = if (landward >= 0) twoSide else oneSide
                        val furthestFromCoast = findFurthestPointFromAllInOtherSet(vertices, fakeInteriorBorderPoints, fakeCoastPoints)
                        furthestFromBorder = findFurthestPointFromAllInOtherSet(vertices, fakeCoastPoints, fakeInteriorBorderPoints)
                        terrainSlope = buildTerrainSlopeWeights(vertices, fakeInteriorBorderPoints, fakeCoastPoints, region)
                        buildRiverSlopeWeights(vertices, fakeInteriorBorderPoints, fakeCoastPoints, furthestFromBorder, furthestFromCoast, region)
                    } else {
                        if (isolatedChunks.size > 1) {
                            val fakeCoastPoints = if (negateFakeWater) {
                                fakeInteriorBorderPoints = findCoastalPoints(vertices, fakeWater, region)
                                findBorderPoints(vertices, fakeWater, region)
                            } else {
                                fakeInteriorBorderPoints = findBorderPoints(vertices, fakeWater, region)
                                findCoastalPoints(vertices, fakeWater, region)
                            }
                            val furthestFromCoast = findFurthestPointFromAllInOtherSet(vertices, fakeInteriorBorderPoints, fakeCoastPoints)
                            furthestFromBorder = findFurthestPointFromAllInOtherSet(vertices, fakeCoastPoints, fakeInteriorBorderPoints)
                            val borderPoints = PointSet2F()
                            regionBorder.points.forEach {
                                borderPoints.add(it)
                            }
                            fakeCoastPoints.forEach {
                                for (point in graph.vertices[it].cell.border) {
                                    if (borderPoints[point] > -1) {
                                        val inlandMouthIndex = coastPoints.addOrGetIndex(borderPoints[borderPoints[point]]!!)
                                        riverCandidates[inlandMouthIndex] = 1.0f
                                        inlandRiverMouths.add(inlandMouthIndex)
                                    }
                                }
                            }
                            terrainSlope = buildTerrainSlopeWeights(vertices, fakeInteriorBorderPoints, fakeCoastPoints, region)
                            buildRiverSlopeWeights(vertices, fakeInteriorBorderPoints, fakeCoastPoints, furthestFromBorder, furthestFromCoast, region)
                        } else {
                            val inlandExtremeId = findFurthestPointFromAllInOtherSet(vertices, interiorBorderPoints, coastalWater)
                            val inland = distanceToLine(bisector, vertices[inlandExtremeId].point)
                            val fakeCoastPoints = if (inland >= 0) twoSide else oneSide
                            fakeInteriorBorderPoints = if (inland >= 0) oneSide else twoSide
                            val furthestFromCoast = findFurthestPointFromAllInOtherSet(vertices, fakeInteriorBorderPoints, fakeCoastPoints)
                            furthestFromBorder = findFurthestPointFromAllInOtherSet(vertices, fakeCoastPoints, fakeInteriorBorderPoints)
                            val borderPoints = PointSet2F()
                            regionBorder.points.forEach {
                                borderPoints.add(it)
                            }
                            for (point in graph.vertices[furthestFromBorder].cell.border) {
                                if (borderPoints[point] > -1) {
                                    val inlandMouthIndex = coastPoints.addOrGetIndex(borderPoints[borderPoints[point]]!!)
                                    riverCandidates[inlandMouthIndex] = 1.0f
                                    inlandRiverMouths.add(inlandMouthIndex)
                                }
                            }
                            terrainSlope = buildTerrainSlopeWeights(vertices, fakeInteriorBorderPoints, fakeCoastPoints, region)
                            buildRiverSlopeWeights(vertices, fakeInteriorBorderPoints, fakeCoastPoints, furthestFromBorder, furthestFromCoast, region)
                        }
                    }
                } else {
                    val furthestFromCoast = findFurthestPointFromAllInOtherSet(vertices, interiorBorderPoints, coastalBorderPoints)
                    furthestFromBorder = findFurthestPointFromAllInOtherSet(vertices, coastalBorderPoints, interiorBorderPoints)
                    terrainSlope = buildTerrainSlopeWeights(vertices, interiorBorderPoints, coastalBorderPoints, region)
                    buildRiverSlopeWeights(vertices, interiorBorderPoints, coastalBorderPoints, furthestFromBorder, furthestFromCoast, region)
                }
                val regionCoastBorders = graph.findBorder(region, water, false)
                val localRiverMouths = ArrayList<Int>()
                regionCoastBorders.flatMap { it.points }.forEach {
                    val index = coastPoints[it]
                    if (index > -1) {
                        localRiverMouths.add(index)
                    }
                }
                if (localRiverMouths.isEmpty()) {
                    regionBorder.points.forEach {
                        val index = coastPoints[it]
                        if (index > -1) {
                            localRiverMouths.add(index)
                        }
                    }
                }
                updateLocalRiverCandidates(coastPoints, regionInlandBorders, localRiverMouths, riverCandidates)
                val regionExceptions = ArrayList<GeometryException>()
                try {
                    buildRiverNetwork(graph, random, water, rivers, coastPoints, inlandRiverMouths, riverCandidates, localRiverMouths, region, regionId + 1, riverSlope, terrainSlope)
                } catch (e: GeometryException) {
                    regionExceptions.add(e)
                }
                exceptions.add(regionExceptions)
                if (isolatedChunks.size > 1 && chunkId > 0) {
                    val landlockedRivers = removeLandlockedRivers(coastPoints, inlandRiverMouths, rivers.last())
                    val riversCopy = ArrayList(rivers)
                    riversCopy.removeAt(riversCopy.size - 1)
                    landlockedRivers.forEach { landlockedRiverMouth ->
                        val possibleConnectingRivers = getPossibleConnectingRivers(coastPoints, riversCopy, inlandRiverMouths, landlockedRiverMouth.value.region, false)
                        connectRiver(possibleConnectingRivers, landlockedRiverMouth)
                    }
                }
            }
        }
        rivers.forEachIndexed { i, regionRivers ->
            val landlockedRivers = removeLandlockedRivers(coastPoints, inlandRiverMouths, regionRivers)
            landlockedRivers.forEach { landlockedRiverMouth ->
                val possibleConnectingRivers = getPossibleConnectingRivers(coastPoints, rivers, inlandRiverMouths, landlockedRiverMouth.value.region, true)
                connectRiver(possibleConnectingRivers, landlockedRiverMouth)
            }
        }
        val riverSets = ArrayList<Triple<Polygon2F, ArrayList<TreeNode<RiverNode>>, ArrayList<GeometryException>>>(bodies.size)
        for (i in 0..bodies.size - 1) {
            riverSets.add(Triple(Polygon2F(coastlines[i].map { coastPoints[it]!! }, true), ArrayList(), ArrayList()))
        }
        rivers.forEachIndexed { i, regionSet ->
            regionSet.forEach { river ->
                riverSets[regionBodyLookup[i]].second.add(river)
            }
        }
        exceptions.forEachIndexed { i, exceptionList ->
            exceptionList.forEach { exception ->
                riverSets[regionBodyLookup[i]].third.add(exception)
            }
        }
        return riverSets
    }

    private fun connectRiver(possibleConnectingRivers: ArrayList<TreeNode<RiverNode>>, landlockedRiverMouth: TreeNode<RiverNode>) {
        val nodes = ArrayList<TreeNode<RiverNode>>()
        possibleConnectingRivers.forEach {
            getNodes(it, nodes)
        }
        nodes.sortBy { landlockedRiverMouth.value.point.distance2(it.value.point) }
        while (nodes.isNotEmpty()) {
            val nextCandidate = nodes.removeAt(0)
            if (isViableConnection(possibleConnectingRivers, landlockedRiverMouth, nextCandidate)) {
                adjustElevationOfGraph(landlockedRiverMouth, nextCandidate.value.elevation)
                landlockedRiverMouth.parent = nextCandidate
                nextCandidate.children.add(landlockedRiverMouth)
                break
            }
        }
    }

    private fun getPossibleConnectingRivers(coastPoints: PointSet2F, rivers: ArrayList<LinkedHashSet<TreeNode<RiverNode>>>, inlandRiverMouths: LinkedHashSet<Int>, currentRegion: Int, negate: Boolean): ArrayList<TreeNode<RiverNode>> {
        val possibleConnectingRivers = ArrayList<TreeNode<RiverNode>>()
        rivers.forEach { regionRivers ->
            regionRivers.forEach {
                if ((!negate && it.value.region == currentRegion) || (negate && it.value.region != currentRegion)) {
                    if (!inlandRiverMouths.contains(coastPoints[it.value.point])) {
                        possibleConnectingRivers.add(it)
                    }
                }
            }
        }
        return possibleConnectingRivers
    }

    private fun removeLandlockedRivers(coastPoints: PointSet2F, inlandRiverMouths: LinkedHashSet<Int>, regionRivers: LinkedHashSet<TreeNode<RiverNode>>): LinkedHashSet<TreeNode<RiverNode>> {
        val landlockedRivers = LinkedHashSet<TreeNode<RiverNode>>()
        ArrayList(regionRivers).forEach {
            if (inlandRiverMouths.contains(coastPoints[it.value.point])) {
                landlockedRivers.add(it)
                regionRivers.remove(it)
            }
        }
        return landlockedRivers
    }

    fun buildRiverGraph(rivers: ArrayList<TreeNode<RiverNode>>): Graph {
        val points = PointSet2F()
        rivers.forEach {
            getPoints(it, points)
        }
        return buildGraph(1.0f, ArrayList(points))
    }

    private fun getPoints(river: TreeNode<RiverNode>, points: PointSet2F) {
        points.add(river.value.point)
        river.children.forEach {
            getPoints(it, points)
        }
    }

    private fun isViableConnection(rivers: ArrayList<TreeNode<RiverNode>>, landlockedRiverMouth: TreeNode<RiverNode>, nextCandidate: TreeNode<RiverNode>): Boolean {
        val newEdge = LineSegment2F(nextCandidate.value.point, landlockedRiverMouth.value.point)
        rivers.forEach {
            if (isTooCloseToRiver(it, it, nextCandidate, newEdge, newEdge.interpolate(0.5f), -Float.MAX_VALUE)) {
                return false
            }
        }
        return true
    }

    private fun getNodes(river: TreeNode<RiverNode>, points: ArrayList<TreeNode<RiverNode>>) {
        points.add(river)
        river.children.forEach {
            getNodes(it, points)
        }
    }

    private fun calculateBodyCenter(vertices: Vertices, region: LinkedHashSet<Int>): Point2F {
        var sumX = 0.0f
        var sumY = 0.0f
        region.forEach {
            val lakePoint = vertices[it].point
            sumX += lakePoint.x
            sumY += lakePoint.y
        }
        val regionCenter = Point2F(sumX / region.size, sumY / region.size)
        return regionCenter
    }

    private fun extractBeachFromGraphAndWater(vertices: Vertices, water: LinkedHashSet<Int>): Pair<LinkedHashSet<Int>, LinkedHashSet<Int>> {
        val beach = LinkedHashSet<Int>()
        val coastalWater = LinkedHashSet<Int>()
        for (vertexId in 0..vertices.size - 1) {
            if (isCoastalPoint(vertices, water, vertexId)) {
                beach.add(vertexId)
            } else if (isCoastalWaterPoint(vertices, water, vertexId)) {
                coastalWater.add(vertexId)
            }
        }
        return Pair(beach, coastalWater)
    }

    private fun extractRegionsFromIdMask(graph: Graph, idMask: Matrix<Int>): Pair<LinkedHashSet<Int>, ArrayList<LinkedHashSet<Int>>> {
        val water = LinkedHashSet<Int>()
        val regions = ArrayList<LinkedHashSet<Int>>()
        val vertices = graph.vertices
        for (i in 0..vertices.size - 1) {
            val maskValue = idMask[i]
            if (maskValue == 0) {
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
        return Pair(water, regions)
    }

    private fun findBorderPoints(vertices: Vertices, water: LinkedHashSet<Int>, region: LinkedHashSet<Int>): LinkedHashSet<Int> {
        return LinkedHashSet(region.filter { isBorderPoint(vertices, water, region, it) })
    }

    private fun isBorderPoint(vertices: Vertices, water: Set<Int>, region: Set<Int>, vertexId: Int): Boolean {
        vertices.getAdjacentVertices(vertexId).forEach { adjacentPoint ->
            if (!water.contains(adjacentPoint) && !region.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    private fun findCoastalPoints(vertices: Vertices, water: LinkedHashSet<Int>, region: LinkedHashSet<Int>): LinkedHashSet<Int> {
        return LinkedHashSet(region.filter { isCoastalPoint(vertices, water, it) })
    }

    private fun isCoastalPoint(vertices: Vertices, water: Set<Int>, vertexId: Int): Boolean {
        if (water.contains(vertexId)) {
            return false
        }
        vertices.getAdjacentVertices(vertexId).forEach { adjacentVertexId ->
            if (water.contains(adjacentVertexId)) {
                return true
            }
        }
        return false
    }

    private fun isCoastalWaterPoint(vertices: Vertices, water: LinkedHashSet<Int>, vertexId: Int): Boolean {
        if (!water.contains(vertexId)) {
            return false
        }
        vertices.getAdjacentVertices(vertexId).forEach { adjacentVertexId ->
            if (!water.contains(adjacentVertexId)) {
                return true
            }
        }
        return false
    }

    private fun findRiverMouthCandidates(graph: Graph, vertices: PointSet2F, bodies: List<LinkedHashSet<Int>>, beach: LinkedHashSet<Int>): Pair<ArrayList<ArrayList<Int>>, HashMap<Int, Float>> {
        val coastlines = ArrayList<ArrayList<Int>>()
        bodies.forEach {
            val coastlinePolygon = graph.findBorder(it).first()
            val coastlineIndices = ArrayList<Int>()
            var lastIndex = -1
            coastlinePolygon.points.forEach {
                if (vertices.add(it)) {
                    val nextIndex = vertices[it]
                    coastlineIndices.add(nextIndex)
                    lastIndex = nextIndex
                } else {
                    if (vertices[it] != lastIndex) {
                        val conflictPoint = vertices[vertices[it]]!!
                        val newPoint = conflictPoint + (Vector2F(conflictPoint, it).getUnit() * 0.0022f)
                        if (vertices.add(newPoint)) {
                            val nextIndex = vertices[newPoint]
                            coastlineIndices.add(nextIndex)
                            lastIndex = nextIndex
                        } else {
                            if (vertices[newPoint] != lastIndex) {
                                val conflictPoint2 = vertices[vertices[newPoint]]!!
                                if (conflictPoint != conflictPoint2) {
                                    val newPoint2 = conflictPoint2 + (Vector2F(conflictPoint2, it).getUnit() * 0.0022f)
                                    val avgNewPoint = LineSegment2F(conflictPoint, conflictPoint2).interpolate(0.5f) + ((Vector2F(conflictPoint, newPoint) + Vector2F(conflictPoint2, newPoint2)).getUnit() * 0.0022f)
                                    if (vertices.add(avgNewPoint)) {
                                        val nextIndex = vertices[avgNewPoint]
                                        coastlineIndices.add(nextIndex)
                                        lastIndex = nextIndex
                                    }
                                }
                            }
                        }
                    }
                }
            }
            coastlines.add(coastlineIndices)
        }
        val coastlineWeights = ArrayList<ArrayList<Float>>()
        vertices.forEach { coastlineWeights.add(ArrayList(2)) }
        val approxCellSize = 1.0f / graph.stride!!
        bodies.forEach {
            val concavityWeights = findConcavityWeights(graph, it, beach, approxCellSize * 3, approxCellSize * 5, approxCellSize * 7, approxCellSize * 13)
            concavityWeights.forEach {
                val id = it.first
                val weight = it.second
                graph.vertices[id].cell.border.forEach { borderPoint ->
                    val index = vertices[borderPoint]
                    if (index > -1) {
                        coastlineWeights[index].add(weight)
                    }
                }
            }
        }
        val riverCandidates = HashMap<Int, Float>(coastlineWeights.size)
        coastlineWeights.forEachIndexed { i, weights ->
            riverCandidates[i] = weights.average().toFloat()
        }
        return Pair(coastlines, riverCandidates)
    }

    private fun findFurthestPointFromAllInOtherSet(vertices: Vertices, testSet: Set<Int>, otherSet: Set<Int>): Int {
        var furthestAway: Int? = null
        var distanceAway = -Float.MAX_VALUE
        testSet.forEach { testPoint ->
            var localLeastDistance = Float.MAX_VALUE
            otherSet.forEach { otherPoint ->
                val dist = distanceSquaredBetween(vertices, testPoint, otherPoint)
                if (localLeastDistance > dist) {
                    localLeastDistance = dist
                }
            }
            if (furthestAway == null || localLeastDistance > distanceAway) {
                furthestAway = testPoint
                distanceAway = localLeastDistance
            }
        }
        return furthestAway!!
    }

    private fun distanceSquaredBetween(vertices: Vertices, id1: Int, id2: Int): Float {
        return vertices[id1].point.distance2(vertices[id2].point)
    }

    private fun buildRiverSlopeWeights(vertices: Vertices, border: Set<Int>, coast: Set<Int>, furthestFromBorder: Int, furthestFromCoast: Int, region: Collection<Int>): Map<Int, Float> {
        val vector = perpendicularVector(vertices, furthestFromBorder, furthestFromCoast)
        var extremityLine = perpendicularLine(vertices, furthestFromBorder, furthestFromCoast)
        var minPoint: Int? = null
        var minDist: Float = Float.MAX_VALUE
        var maxPoint: Int? = null
        var maxDist: Float = -Float.MAX_VALUE
        (border + coast).forEach {
            val localDist = distanceToLine(vertices, extremityLine, it)
            if (minPoint == null || localDist < minDist) {
                minPoint = it
                minDist = localDist
            }
            if (maxPoint == null || localDist > maxDist) {
                maxPoint = it
                maxDist = localDist
            }
        }
        val minExtremity = pointVectorToLine(vertices, minPoint!!, vector)
        val maxExtremity = pointVectorToLine(vertices, maxPoint!!, vector)
        val distToMinExtremity = distanceToLine(vertices, minExtremity, furthestFromBorder)
        val distToMaxExtremity = distanceToLine(vertices, maxExtremity, furthestFromBorder)
        val extremityDist = if (distToMaxExtremity < distToMinExtremity) {
            extremityLine = maxExtremity
            distanceToLine(vertices, extremityLine, minPoint!!)
        } else {
            extremityLine = minExtremity
            distanceToLine(vertices, extremityLine, maxPoint!!)
        }
        val riverSlopeWeights = HashMap<Int, Float>(region.size)
        var maxDistFromCoast = -Float.MAX_VALUE
        region.forEach { point ->
            val dist = findClosestPointDistance(vertices, coast, point)
            if (dist > maxDistFromCoast) {
                maxDistFromCoast = dist
            }
            riverSlopeWeights.put(point, dist)
        }
        region.forEach { point ->
            val distFromCoastNormalized = riverSlopeWeights[point] ?: 0 / maxDistFromCoast * 0.5f
            val distFromExtremity = distanceToLine(vertices, extremityLine, point) / extremityDist * 0.5f
            riverSlopeWeights[point] = distFromCoastNormalized + distFromExtremity
        }
        val maxSlope = riverSlopeWeights.values.max()!!
        val minSlope = riverSlopeWeights.values.min()!!
        val slopeDelta = maxSlope - minSlope
        val neededSlopeDelta = MAX_RIVER_SLOPE - MIN_RIVER_SLOPE
        ArrayList(riverSlopeWeights.keys).forEach {
            riverSlopeWeights[it] = (((riverSlopeWeights[it]!! - minSlope) / slopeDelta) * neededSlopeDelta) + MIN_RIVER_SLOPE
        }
        return riverSlopeWeights
    }

    private fun buildTerrainSlopeWeights(vertices: Vertices, border: Set<Int>, coast: Set<Int>, region: Collection<Int>): HashMap<Int, Float> {
        var minDistFromCoast = Float.MAX_VALUE
        var maxDistFromCoast = -Float.MAX_VALUE
        var minDistFromBorder = Float.MAX_VALUE
        var maxDistFromBorder = -Float.MAX_VALUE
        val distances = region.map { point ->
            val distFromCoast = findClosestPointDistance(vertices, coast, point)
            val distFromBorder = findClosestPointDistance(vertices, border, point)
            if (distFromCoast > maxDistFromCoast) {
                maxDistFromCoast = distFromCoast
            }
            if (distFromCoast < minDistFromCoast) {
                minDistFromCoast = distFromCoast
            }
            if (distFromBorder > maxDistFromBorder) {
                maxDistFromBorder = distFromBorder
            }
            if (distFromBorder < minDistFromBorder) {
                minDistFromBorder = distFromBorder
            }
            Triple(point, distFromCoast, distFromBorder)
        }
        val coastDelta = maxDistFromCoast - minDistFromCoast
        val borderDelta = maxDistFromBorder - minDistFromBorder
        val slopeDelta = MAX_TERRAIN_SLOPE - MIN_TERRAIN_SLOPE
        return hashMapOf(*distances.map {
            val normalizedCoast = if (coastDelta == 0.0f) 0.0f else (it.second - minDistFromCoast) / coastDelta
            val normalizedBorder = if (borderDelta == 0.0f) 0.0f else (it.third - minDistFromBorder) / borderDelta
            val borderWeight = MIN_TERRAIN_SLOPE + (sigmoidBorder(normalizedBorder) * slopeDelta)
            val coastWeight = MIN_TERRAIN_SLOPE + (sigmoidCoast(normalizedCoast) * slopeDelta)
            Pair(it.first, max(coastWeight, borderWeight))
        }.toTypedArray())
    }

    private fun sigmoidCoast(x: Float): Float {
        return ((1.0 / (0.5 + pow(0.03, x - 0.7))) * 0.2).toFloat()
    }

    private fun sigmoidBorder(x: Float): Float {
        return ((2.02 - (1.0 / (0.5 + pow(0.0000000000000001, x - 0.1)))) * 0.501).toFloat()
    }

    private fun perpendicularVector(vertices: Vertices, id1: Int, id2: Int): Point2F {
        return perpendicularVector(vertices[id1].point, vertices[id2].point)
    }

    private fun perpendicularVector(p1: Point2F, p2: Point2F): Point2F {
        return Point2F(p1.y - p2.y, -(p1.x - p2.x))
    }

    private fun perpendicularLine(vertices: Vertices, id1: Int, id2: Int): Pair<Point2F, Point2F> {
        return perpendicularLine(vertices[id1].point, vertices[id2].point)
    }

    private fun perpendicularLine(p1: Point2F, p2: Point2F): Pair<Point2F, Point2F> {
        return lineFromPointAndVector(p1, perpendicularVector(p1, p2))
    }

    private fun lineFromPointAndVector(point: Point2F, vector: Point2F): Pair<Point2F, Point2F> {
        return Pair(point, Point2F(point.x + vector.x, point.y + vector.y))
    }

    private fun distanceToLine(vertices: Vertices, line: Pair<Point2F, Point2F>, id: Int): Float {
        return distanceToLine(line, vertices[id].point)
    }

    private fun distanceToLine(line: Pair<Point2F, Point2F>, point: Point2F): Float {
        val a = line.first.y - line.second.y
        val b = line.second.x - line.first.x
        val c = line.first.x * line.second.y - line.second.x * line.first.y
        val numerator = a * point.x + b * point.y + c
        val denominator = Math.sqrt((a * a + b * b).toDouble())
        return (numerator / denominator).toFloat()
    }

    private fun pointVectorToLine(vertices: Vertices, id: Int, vector: Point2F): Pair<Point2F, Point2F> {
        val point = vertices[id].point
        val lp1 = Point2F(point.x + vector.x, point.y + vector.y)
        return Pair(point, lp1)
    }

    private fun findClosestPointDistance(vertices: Vertices, testSet: Set<Int>, id: Int): Float {
        var closestPoint: Int? = null
        var minDistance = Float.MAX_VALUE
        val point = vertices[id].point
        testSet.forEach { testId ->
            val testPoint = vertices[testId].point
            val dist = point.distance2(testPoint)
            if (closestPoint == null || dist < minDistance) {
                closestPoint = testId
                minDistance = dist
            }
        }
        return Math.sqrt(minDistance.toDouble()).toFloat()
    }

    private fun findClosestPointDistance(borders: ArrayList<Polygon2F>, point: Point2F): Float {
        var minDistance = Float.MAX_VALUE
        borders.forEach { border ->
            border.points.forEach { borderPoint ->
                val dist = point.distance2(borderPoint)
                if (dist < minDistance) {
                    minDistance = dist
                }
            }
        }
        return Math.sqrt(minDistance.toDouble()).toFloat()
    }

    private fun findFurthestPoint(vertices: Vertices, testSet: Set<Int>, point: Point2F): Int {
        var furthestPoint: Int? = null
        var maxDistance = -Float.MAX_VALUE
        testSet.forEach { testId ->
            val testPoint = vertices[testId].point
            val dist = point.distance2(testPoint)
            if (furthestPoint == null || dist > maxDistance) {
                furthestPoint = testId
                maxDistance = dist
            }
        }
        return furthestPoint!!
    }

    private fun updateLocalRiverCandidates(coastPoints: PointSet2F, border: ArrayList<Polygon2F>, localRiverMouths: List<Int>, riverCandidates: HashMap<Int, Float>) {
        val mouthDistances = ArrayList<Float>(localRiverMouths.size)
        var maxDist = -Float.MAX_VALUE
        localRiverMouths.forEach {
            val closestDist = findClosestPointDistance(border, coastPoints[it]!!)
            mouthDistances.add(closestDist)
            if (closestDist > maxDist) {
                maxDist = closestDist
            }
        }
        localRiverMouths.forEachIndexed { i, it ->
            val normalizedDistance = mouthDistances[i] / maxDist
            val candidateWeight = riverCandidates[it] ?: 0.0f
            val distanceWeighted = (0.2f * candidateWeight) + (0.8f * (candidateWeight * normalizedDistance))
            riverCandidates[it] = distanceWeighted
        }
    }

    private fun buildRiverNetwork(graph: Graph,
                                  random: Random,
                                  water: LinkedHashSet<Int>,
                                  rivers: ArrayList<LinkedHashSet<TreeNode<RiverNode>>>,
                                  coastPoints: PointSet2F,
                                  inlandRiverMouths: LinkedHashSet<Int>,
                                  riverCandidates: HashMap<Int, Float>,
                                  localRiverMouths: ArrayList<Int>,
                                  region: LinkedHashSet<Int>,
                                  regionId: Int,
                                  riverSlope: Map<Int, Float>,
                                  terrainSlope: Map<Int, Float>) {
        val borderWithCoast = graph.findBorder(region)
        val borderWithoutCoast = graph.findBorder(region, water, true)
        val vertices = graph.vertices
        val candidateRiverNodes = sortCandidateRiverNodes(coastPoints, riverCandidates, localRiverMouths, regionId)
        var landlocked = false
        for (riverNode in candidateRiverNodes) {
            if (inlandRiverMouths.contains(coastPoints[riverNode.value.point])) {
                landlocked = true
                break
            }
        }
        val landlockedRegion = landlocked
        val localRivers = LinkedHashSet<TreeNode<RiverNode>>()
        val savedCandidates = ArrayList<TreeNode<RiverNode>>()
        while (true) {
            val lowestElevation = findElevationOfLowestRiverNode(candidateRiverNodes)
            val admissibleCandidates = findAdmissibleCandidates(candidateRiverNodes, lowestElevation)
            var expansionCandidate = findExpansionCandidate(admissibleCandidates) ?: break
            if (expansionCandidate.children.size >= MAX_RIVER_CHILDREN) {
                candidateRiverNodes.remove(expansionCandidate)
                continue
            }
            var newNodeCandidate = findMostViableNewNodeCandidate(graph, borderWithCoast, region, rivers + listOf(localRivers), expansionCandidate, RIVER_EDGE_DISTANCE_SEARCH_RADIUS, MIN_DISTANCE_FROM_BORDER_SQUARED)
            if (newNodeCandidate == null && candidateRiverNodes.size < 2 && localRivers.map { it.count() }.filter { it >= 2 }.sum() < 2) {
                val riverEdgeMinDistIncrement = MIN_RIVER_EDGE_DISTANCE / 10.0f
                val distFromBorderIncrement = MIN_DISTANCE_FROM_BORDER / 10.0f
                var count = 0
                var riverEdgeMinDist = MIN_RIVER_EDGE_DISTANCE
                var distFromBorder = MIN_DISTANCE_FROM_BORDER
                val maxCount = if (landlockedRegion) 9 else 8
                while (newNodeCandidate == null && count < maxCount) {
                    riverEdgeMinDist -= riverEdgeMinDistIncrement
                    val minRiverEdge2 = riverEdgeMinDist * riverEdgeMinDist
                    distFromBorder -= distFromBorderIncrement
                    val distFromBorder2 = distFromBorder * distFromBorder
                    for (candidate in candidateRiverNodes + savedCandidates) {
                        val localRoot = findRoot(candidate)
                        if (!localRivers.contains(localRoot)) {
                            if (!landlocked && !isValidRiverStart(localRivers, rivers, borderWithoutCoast, localRoot, distFromBorder2)) {
                                continue
                            }
                        }
                        newNodeCandidate = findMostViableNewNodeCandidate(graph, borderWithCoast, region, rivers + listOf(localRivers), candidate, RIVER_EDGE_DISTANCE_SEARCH_RADIUS, distFromBorder2, minRiverEdge2)
                        if (newNodeCandidate != null) {
                            expansionCandidate = candidate
                            localRivers.add(localRoot)
                            break
                        }
                    }
                    count++
                }
            }
            if (newNodeCandidate == null) {
                candidateRiverNodes.remove(expansionCandidate)
                val root = findRoot(expansionCandidate)
                if (root == expansionCandidate && !localRivers.contains(root)) {
                    savedCandidates.add(expansionCandidate)
                }
            } else {
                val root = findRoot(expansionCandidate)
                if (!localRivers.contains(root)) {
                    if (!landlocked && !isValidRiverStart(localRivers, rivers, borderWithoutCoast, root)) {
                        candidateRiverNodes.remove(expansionCandidate)
                        if (root == expansionCandidate) {
                            savedCandidates.add(expansionCandidate)
                        }
                        if (candidateRiverNodes.isEmpty() && localRivers.map { it.count() }.filter { it >= 2 }.sum() < 2) {
                            candidateRiverNodes.addAll(localRivers.filter { it.children.isEmpty() }.sortedByDescending { it.value.priority })
                        }
                        if (candidateRiverNodes.isEmpty() && localRivers.isEmpty()) {
                            landlocked = true
                            val sortedCandidates = sortCandidateRiverNodes(coastPoints, riverCandidates, localRiverMouths, regionId)
                            candidateRiverNodes.addAll(sortedCandidates.subList(0, min(3, sortedCandidates.size)))
                        }
                        continue
                    }
                    localRivers.add(root)
                    landlocked = false
                }
                val newPoint = vertices[newNodeCandidate].point
                val node = expansionCandidate.value
                val edgeDist = Math.sqrt(node.point.distance2(newPoint).toDouble()).toFloat()
                val localRiverSlope = riverSlope[newNodeCandidate] ?: 0.0f
                val newTerrainSlope = terrainSlope[newNodeCandidate] ?: MIN_TERRAIN_SLOPE
                val newElevation = node.elevation + (localRiverSlope * edgeDist)
                val newPriority = if (node.type == NodeType.CONTINUATION) {
                    candidateRiverNodes.remove(expansionCandidate)
                    node.priority
                } else if (node.type == NodeType.SYMMETRIC) {
                    if (node.priority == 1) {
                        increasePriorityOfGraph(expansionCandidate)
                    }
                    node.priority - 1
                } else {
                    if (expansionCandidate.children.isEmpty()) {
                        node.priority
                    } else {
                        if (node.priority == 1) {
                            increasePriorityOfGraph(expansionCandidate)
                        }
                        node.priority - 1
                    }
                }
                val newNode = TreeNode(RiverNode(getRandomNodeType(random), newPoint, newPriority, node.riverPriority, newElevation, newTerrainSlope, regionId), expansionCandidate, ArrayList())
                expansionCandidate.children.add(newNode)
                candidateRiverNodes.add(newNode)
            }
//            draw(4096, "debug-buildRiverNetwork-${debugCount.andIncrement}", "output", Color.WHITE) {
//                graphics.color = Color.BLACK
//                borderWithCoast.forEach {
//                    drawPolygon(it, false)
//                }
//                graphics.color = Color.RED
//                borderWithoutCoast.forEach {
//                    drawPolygon(it, false)
//                }
//                graphics.color = Color.MAGENTA
//                candidateRiverNodes.forEach {
//                    drawPoint(it.value.point, 2)
//                }
//                graphics.color = Color.BLUE
//                localRivers.forEach {
//                    it.nodeIterable().forEach {
//                        val parent = it.parent
//                        if (parent != null) {
//                            drawEdge(parent.value.point, it.value.point)
//                        }
//                        drawPoint(it.value.point, 2)
//                    }
//                }
//            }
        }
        if (landlockedRegion) {
            ArrayList(localRivers).forEach {
                if (it.count() == 1) {
                    localRivers.remove(it)
                }
            }
        } else {
            savedCandidates.forEach {
                if (!localRivers.contains(it)) {
                    if (isValidRiverStart(localRivers, rivers, borderWithoutCoast, it)) {
                        localRivers.add(it)
                    }
                }
            }
        }
        rivers.add(localRivers)
        if (region.size > 100 && localRivers.map { it.count() }.filter { it >= 2 }.sum() < 2) {
            throw GeometryException("generated region with no watershed data").with {
                data.add("val region = ${printList(region) { graph.vertices[it].point.toString() }}")
            }
        }
    }

    private fun detectIsolatedChunks(graph: Graph, water: LinkedHashSet<Int>, region: LinkedHashSet<Int>): ArrayList<Triple<LinkedHashSet<Int>, LinkedHashSet<Int>, Boolean>> {
        val borders = graph.findBorder(region, water, true)
        val coasts = graph.findBorder(region, water, false)
        val borderPoints = graph.findBorderIds(region, water, true)
        val landlocked = coasts.isEmpty() || (coasts.size == 1 && coasts.first().edges.isEmpty())
        val usefulPoints = LinkedHashSet<Int>()
        region.forEach {
            if (!isTooCloseToBorder(borders, graph.vertices[it].point)) {
                usefulPoints.add(it)
            }
        }
        val connectedBodies = graph.getConnectedBodies(usefulPoints)
        ArrayList(connectedBodies).forEach { body ->
            if ((landlocked && body.size < 4) || (!landlocked && body.size < 30)) {
                connectedBodies.remove(body)
            }
        }

//        draw(debugResolution, "debug-connectedBodies-${Geometry.debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE) {
//            graphics.color = Color.BLACK
//            drawMask(graph, connectedBodies.flatMap { it }.toSet(), true)
//        }
//        println()

        val limitedSizeBodies = ArrayList<LinkedHashSet<Int>>()
        if (connectedBodies.size == 1 && !landlocked) {
            val nonBorderCoast = graph.findBorder(connectedBodies.first(), water, false)
            if (nonBorderCoast.isNotEmpty()) {
                return arrayListOf(Triple(region, water, false))
            } else {
                val secondBody = findCoastalPoints(graph.vertices, water, region)
                limitedSizeBodies.add(connectedBodies.first())
                connectedBodies.add(secondBody)
            }
        }
        val unconnectedIds = LinkedHashSet(region)
        unconnectedIds.removeAll(connectedBodies.flatMap { it })
        var hasRoomToGrow = true
        var stillNeedsToConnect = limitedSizeBodies.isNotEmpty()
        var lastCount = -1
        while (unconnectedIds.isNotEmpty() && lastCount != unconnectedIds.size && ((connectedBodies.size > 1 && limitedSizeBodies.isEmpty()) || hasRoomToGrow || stillNeedsToConnect)) {
            lastCount = unconnectedIds.size
            connectedBodies.sortedByDescending { it.size }.forEach { body ->
                if (hasRoomToGrow || !limitedSizeBodies.contains(body)) {
                    ArrayList(body).forEach { bodyId ->
                        graph.vertices.getAdjacentVertices(bodyId).forEach { adjacentId ->
                            if (unconnectedIds.contains(adjacentId)) {
                                body.add(adjacentId)
                                unconnectedIds.remove(adjacentId)
                            }
                        }
                    }
                }
            }
            if (limitedSizeBodies.isNotEmpty()) {
                hasRoomToGrow = hasRoomToGrow && !borderPoints.containsAny(limitedSizeBodies.flatMap { it })
                val flatLimitedBodies = LinkedHashSet(limitedSizeBodies.flatMap { it })
                val adjacentToFlatLimitedBodies = LinkedHashSet(graph.findBorderIds(flatLimitedBodies).flatMap { graph.vertices.getAdjacentVertices(it) }.filter { !flatLimitedBodies.contains(it) })
                stillNeedsToConnect = stillNeedsToConnect && !adjacentToFlatLimitedBodies.containsAny(connectedBodies.filter { !limitedSizeBodies.contains(it) }.flatMap { it })
            } else {
                hasRoomToGrow = hasRoomToGrow && !borderPoints.containsAny(connectedBodies.flatMap { it })
            }
        }


//        connectedBodies.forEach {
//            draw(debugResolution, "debug-connectedBodies-${Geometry.debugIteration.get()}-${debugCount.andIncrement}", "output", Color.WHITE) {
//                graphics.color = Color.BLACK
//                drawMask(graph, it, true)
//            }
//            println()
//        }
//        println()


        if (landlocked) {
            val sortedBodies = ArrayList(connectedBodies.sortedByDescending { it.size })
            val mainBody = sortedBodies.removeAt(0)
            val sortedTriples = connectAllToWater(graph, mainBody, sortedBodies)
            val mainBodyUnusablePoints = LinkedHashSet(sortedBodies.flatMap { it })
            sortedTriples.add(0, Triple(mainBody, mainBodyUnusablePoints, true))
            return sortedTriples
        }
        return connectAllToWater(graph, water, connectedBodies)
    }

    private fun connectAllToWater(graph: Graph, water: LinkedHashSet<Int>, connectedBodies: ArrayList<LinkedHashSet<Int>>): ArrayList<Triple<LinkedHashSet<Int>, LinkedHashSet<Int>, Boolean>> {
        val unconnectedToWater = ArrayList(connectedBodies)
        val pseudoWater = LinkedHashSet<Int>(water)
        val bodiesWithWater = ArrayList<Triple<LinkedHashSet<Int>, LinkedHashSet<Int>, Boolean>>()
        while (unconnectedToWater.isNotEmpty()) {
            val toAdd = ArrayList<LinkedHashSet<Int>>()
            for (unconnected in unconnectedToWater) {
                val coasts = graph.findBorder(unconnected, pseudoWater, false)
                if (!coasts.isEmpty()) {
                    toAdd.add(unconnected)
                }
            }
            val pseudoWaterCopy = LinkedHashSet<Int>(pseudoWater)
            toAdd.forEach {
                bodiesWithWater.add(Triple(it, pseudoWaterCopy, false))
                unconnectedToWater.remove(it)
                pseudoWater.addAll(it)
            }
        }
        return bodiesWithWater
    }

    private fun isTooCloseToBorder(borders: ArrayList<Polygon2F>, point: Point2F): Boolean {
        borders.flatMap { it.edges }.forEach {
            if (it.distance2(point) < MIN_DISTANCE_FROM_BORDER_SQUARED) {
                return true
            }
        }
        return false
    }

    private fun isTooCloseToRiver(rivers: LinkedHashSet<TreeNode<RiverNode>>, expansionCandidate: TreeNode<RiverNode>): Boolean {
        rivers.forEach {
            val riverRoot = findRoot(it)
            if (isTooCloseToRiver(riverRoot, riverRoot, expansionCandidate.value)) {
                return true
            }
        }
        return false
    }

    private fun sortCandidateRiverNodes(coastPoints: PointSet2F, riverCandidates: HashMap<Int, Float>, riverMouths: ArrayList<Int>, region: Int): ArrayList<TreeNode<RiverNode>> {
        riverMouths.sortByDescending { riverCandidates[it] ?: 0.0f }
        val candidateRiverNodes = ArrayList<TreeNode<RiverNode>>()
        riverMouths.forEachIndexed { i, pointIndex ->
            candidateRiverNodes.add(TreeNode(RiverNode(NodeType.CONTINUATION, coastPoints[pointIndex]!!, 1, (riverMouths.size - i) + 1, 0.0f, MIN_TERRAIN_SLOPE, region), null, ArrayList()))
        }
        return candidateRiverNodes
    }

    private fun findElevationOfLowestRiverNode(candidateRiverNodes: ArrayList<TreeNode<RiverNode>>): Float {
        var minElevation = Float.MAX_VALUE
        candidateRiverNodes.forEach {
            if (it.value.elevation < minElevation) {
                minElevation = it.value.elevation
            }
        }
        return minElevation
    }

    private fun findAdmissibleCandidates(candidateRiverNodes: ArrayList<TreeNode<RiverNode>>, lowestElevation: Float): List<TreeNode<RiverNode>> {
        val admissibleCandidates = ArrayList<TreeNode<RiverNode>>()
        candidateRiverNodes.forEach {
            if (it.value.elevation < lowestElevation + MAX_CANDIDATE_ELEVATION_DIFF) {
                admissibleCandidates.add(it)
            }
        }
        return admissibleCandidates
    }

    private fun findExpansionCandidate(admissibleCandidates: List<TreeNode<RiverNode>>): TreeNode<RiverNode>? {
        var bestCandidate: TreeNode<RiverNode>? = null
        var bestPriority = Int.MIN_VALUE
        admissibleCandidates.forEach {
            val finalBest = bestCandidate
            val node = it.value
            if (finalBest == null) {
                bestCandidate = it
                bestPriority = node.priority + node.riverPriority
            } else {
                val localPriority = node.priority + node.riverPriority
                if (localPriority > bestPriority || (localPriority == bestPriority && node.elevation < finalBest.value.elevation)) {
                    bestCandidate = it
                    bestPriority = localPriority
                }
            }
        }
        return bestCandidate
    }

    private fun findMostViableNewNodeCandidate(graph: Graph, border: ArrayList<Polygon2F>, region: Set<Int>, rivers: List<LinkedHashSet<TreeNode<RiverNode>>>, expansionCandidate: TreeNode<RiverNode>, radius: Float, minDistanceBorder2: Float = MIN_DISTANCE_FROM_BORDER_SQUARED, minRiverEdgeLength2: Float = MIN_RIVER_EDGE_DISTANCE_SQUARED): Int? {
        val vertices = graph.vertices
        val point = expansionCandidate.value.point
        graph.getPointsWithinRadius(point, radius)
                .filter { region.contains(it) }
                .map { vertices[it] }
                .map { Pair(it, point.distance2(it.point)) }
                .filter { it.second >= minRiverEdgeLength2 }
                .sortedBy { Math.abs(it.second - IDEAL_RIVER_EDGE_DISTANCE_SQUARED) }.forEach { vertexPair ->
            val pointLocation = vertexPair.first.point
            if (isValidExpansionPoint(rivers, border, expansionCandidate, pointLocation, minDistanceBorder2)) {
                return vertexPair.first.id
            }
        }
        return null
    }

    private fun isValidRiverStart(localRivers: LinkedHashSet<TreeNode<RiverNode>>, globalRivers: ArrayList<LinkedHashSet<TreeNode<RiverNode>>>, borders: ArrayList<Polygon2F>, expansionCandidate: TreeNode<RiverNode>, minDistanceBorder2: Float = MIN_DISTANCE_FROM_BORDER_SQUARED): Boolean {
        val pointLocation = expansionCandidate.value.point
        borders.forEach { border ->
            if (border.distance2(pointLocation) < minDistanceBorder2) {
                return false
            }
        }
        if (isTooCloseToRiver(localRivers, expansionCandidate)) {
            return false
        }
        globalRivers.forEach {
            if (isTooCloseToRiver(it, expansionCandidate)) {
                return false
            }
        }
        return true
    }

    private fun isValidExpansionPoint(rivers: List<LinkedHashSet<TreeNode<RiverNode>>>, borders: ArrayList<Polygon2F>, expansionCandidate: TreeNode<RiverNode>, pointLocation: Point2F, minDistanceBorder2: Float = MIN_DISTANCE_FROM_BORDER_SQUARED): Boolean {
        val newEdge = LineSegment2F(expansionCandidate.value.point, pointLocation)
        borders.forEach { border ->
            if (border.doesEdgeIntersect(newEdge, newEdge.a).first) {
                return false
            }
            if (border.distance2(pointLocation) < minDistanceBorder2) {
                return false
            }
        }
        var minSafeDistance2 = newEdge.length * 0.52f
        minSafeDistance2 *= minSafeDistance2
        rivers.flatMap { it }.forEach {
            if (isTooCloseToRiver(it, it, expansionCandidate, newEdge, newEdge.interpolate(0.5f), minSafeDistance2)) {
                return false
            }
        }
        return true
    }

    private fun isTooCloseToRiver(rootNode: TreeNode<RiverNode>, riverNode: TreeNode<RiverNode>, expansionCandidate: RiverNode): Boolean {
        riverNode.children.forEach {
            val line = LineSegment2F(riverNode.value.point, it.value.point)
            var minSafeDistance2 = line.length * 0.52f
            minSafeDistance2 *= minSafeDistance2
            if (line.distance2(expansionCandidate.point) < MIN_RIVER_SEPARATION_SQUARED || line.interpolate(0.5f).distance2(expansionCandidate.point) < minSafeDistance2) {
                return true
            }
            if (isTooCloseToRiver(rootNode, it, expansionCandidate)) {
                return true
            }
        }
        if (riverNode == rootNode && riverNode.children.isEmpty()) {
            return riverNode.value.point.distance2(expansionCandidate.point) < MIN_RIVER_SEPARATION_SQUARED
        }
        return false
    }

    private fun isTooCloseToRiver(rootNode: TreeNode<RiverNode>, riverNode: TreeNode<RiverNode>, expansionCandidate: TreeNode<RiverNode>, expansionEdge: LineSegment2F, expansionMidpoint: Point2F, minSafeDistance2: Float): Boolean {
        val node = riverNode.value
        if (riverNode != expansionCandidate && expansionMidpoint.distance2(node.point) <= minSafeDistance2) {
            return true
        }
        riverNode.children.forEach {
            val child = it.value
            if (!(it == expansionCandidate || riverNode == expansionCandidate)) {
                if (expansionEdge.distance2(LineSegment2F(node.point, child.point)) < MIN_RIVER_SEPARATION_SQUARED) {
                    return true
                }
            } else {
                if (LineSegment2F(node.point, child.point).distance2(expansionEdge.b) < MIN_RIVER_SEPARATION_SQUARED) {
                    return true
                }
            }
            if (it != expansionCandidate) {
                val otherLine = LineSegment2F(node.point, child.point)
                var minSafeDistance2ToOther = otherLine.length * 0.52f
                minSafeDistance2ToOther *= minSafeDistance2ToOther
                if (otherLine.interpolate(0.5f).distance2(expansionEdge.b) < minSafeDistance2ToOther) {
                    return true
                }
            }
            if (isTooCloseToRiver(rootNode, it, expansionCandidate, expansionEdge, expansionMidpoint, minSafeDistance2)) {
                return true
            }
        }
        if (riverNode == rootNode && riverNode != expansionCandidate && riverNode.children.isEmpty()) {
            return expansionEdge.distance2(node.point) < MIN_RIVER_SEPARATION_SQUARED || expansionMidpoint.distance2(node.point) < minSafeDistance2
        }
        return false
    }

    private fun findRoot(riverNode: TreeNode<RiverNode>): TreeNode<RiverNode> {
        val parent = riverNode.parent
        if (parent == null) {
            return riverNode
        } else {
            return findRoot(parent)
        }
    }

    private fun increasePriorityOfGraph(riverNode: TreeNode<RiverNode>) {
        increasePriorityOfDownGraph(findRoot(riverNode))
    }

    private fun increasePriorityOfDownGraph(riverNode: TreeNode<RiverNode>) {
        riverNode.children.forEach {
            increasePriorityOfDownGraph(it)
        }
        riverNode.value.priority += 1
    }

    private fun adjustElevationOfGraph(riverNode: TreeNode<RiverNode>, adjustment: Float) {
        adjustElevationOfDownGraph(findRoot(riverNode), adjustment)
    }

    private fun adjustElevationOfDownGraph(riverNode: TreeNode<RiverNode>, adjustment: Float) {
        riverNode.children.forEach {
            adjustElevationOfDownGraph(it, adjustment)
        }
        riverNode.value.elevation += adjustment
    }

    private fun getRandomNodeType(random: Random): NodeType {
        val nodeType = random.nextFloat()
        return if (nodeType < CONTINUATION_PRIORITY) {
            NodeType.CONTINUATION
        } else if (nodeType < SYMMETRIC_PRIORITY) {
            NodeType.SYMMETRIC
        } else {
            NodeType.ASYMMETRIC
        }
    }
}
