package com.grimfox.gec.util

import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.CellEdge
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.util.Coastline.getCoastline
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils.distance2Between
import com.grimfox.gec.util.Utils.findConcavityWeights
import com.grimfox.gec.util.Utils.getLineIntersection
import com.grimfox.gec.util.Utils.midPoint
import java.util.*

object Rivers {

    enum class NodeType {
        CONTINUATION,
        SYMMETRIC,
        ASYMMETRIC
    }

    class RiverNode(var type: NodeType, var parent: RiverNode?, var children: MutableList<RiverNode>, var pointIndex: Int, var pointLocation: Point, var priority: Int, var riverPriority: Int, var elevation: Float)

    val MAX_CANDIDATE_ELEVATION_DIFF = 0.005f
    val MIN_RIVER_MOUTH_SEPARATION = 0.001f
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
    val MAX_RIVER_SLOPE = 0.25f
    val MAX_RIVER_CHILDREN = 3

    fun buildRivers(graph: Graph, idMask: Matrix<Int>, random: Random): ArrayList<Pair<Polygon, HashSet<RiverNode>>> {
        val vertices = graph.vertices
        val (water, regions) = extractRegionsFromIdMask(graph, idMask)
        val (beach, coastalWater) = extractBeachFromGraphAndWater(vertices, water)
        val land = regions.flatMap { it }.toHashSet()
        val landCenter = calculateBodyCenter(vertices, land)
        val rivers = ArrayList<HashSet<RiverNode>>()
        val (bestRiverCandidates, riverCandidates) = findRiverMouthCandidates(graph, land, beach)
        regions.forEach { region ->
            val fakeCoastPoints: HashSet<Int>
            val fakeInteriorBorderPoints: HashSet<Int>
            val furthestFromBorder: Int
            val interiorBorderPoints = findBorderPoints(vertices, water, region)
            val coastalBorderPoints = findCoastalPoints(vertices, water, region)
            val riverSlope = if (interiorBorderPoints.isEmpty() || coastalBorderPoints.isEmpty()) {
                val regionCenter = calculateBodyCenter(vertices, region)
                val seedId = findFurthestPoint(vertices, region, regionCenter)
                val bisector = Pair(regionCenter, vertices[seedId].point)
                val oneSide = HashSet<Int>()
                val twoSide = HashSet<Int>()
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
                    fakeCoastPoints = if (landward >= 0) oneSide else twoSide
                    fakeInteriorBorderPoints = if (landward >= 0) twoSide else oneSide
                    val furthestFromCoast = findFurthestPointFromAllInOtherSet(vertices, fakeInteriorBorderPoints, fakeCoastPoints)
                    furthestFromBorder = findFurthestPointFromAllInOtherSet(vertices, fakeCoastPoints, fakeInteriorBorderPoints)
                    buildRiverSlopeWeights(vertices, fakeInteriorBorderPoints, fakeCoastPoints, furthestFromBorder, furthestFromCoast, region)
                } else {
                    val inlandExtremeId = findFurthestPointFromAllInOtherSet(vertices, interiorBorderPoints, coastalWater)
                    val inland = distanceToLine(bisector, vertices[inlandExtremeId].point)
                    fakeCoastPoints = if (inland >= 0) twoSide else oneSide
                    fakeInteriorBorderPoints = if (inland >= 0) oneSide else twoSide
                    val furthestFromCoast = findFurthestPointFromAllInOtherSet(vertices, fakeInteriorBorderPoints, fakeCoastPoints)
                    furthestFromBorder = findFurthestPointFromAllInOtherSet(vertices, fakeCoastPoints, fakeInteriorBorderPoints)
                    riverCandidates[furthestFromBorder] = 1.0f
                    bestRiverCandidates.add(furthestFromBorder)
                    buildRiverSlopeWeights(vertices, fakeInteriorBorderPoints, fakeCoastPoints, furthestFromBorder, furthestFromCoast, region)
                }
            } else {
                fakeCoastPoints = coastalBorderPoints
                fakeInteriorBorderPoints = interiorBorderPoints
                val furthestFromCoast = findFurthestPointFromAllInOtherSet(vertices, interiorBorderPoints, coastalBorderPoints)
                furthestFromBorder = findFurthestPointFromAllInOtherSet(vertices, coastalBorderPoints, interiorBorderPoints)
                buildRiverSlopeWeights(vertices, interiorBorderPoints, coastalBorderPoints, furthestFromBorder, furthestFromCoast, region)
            }
            val localRiverMouths = HashSet(bestRiverCandidates)
            localRiverMouths.retainAll(fakeCoastPoints + fakeInteriorBorderPoints)
            if (localRiverMouths.isEmpty()) {
                localRiverMouths.addAll(riverCandidates.keys)
                localRiverMouths.retainAll(fakeCoastPoints + fakeInteriorBorderPoints)
            }
            if (localRiverMouths.isEmpty()) {
                localRiverMouths.add(furthestFromBorder)
            }
            updateLocalRiverCandidates(vertices, fakeInteriorBorderPoints, localRiverMouths, riverCandidates)
            buildRiverNetwork(graph, random, water, rivers, riverCandidates, region, riverSlope, localRiverMouths)
        }
        val landlockedRivers = HashSet<RiverNode>()
        rivers.forEach { regionRivers ->
            ArrayList(regionRivers).forEach {
                if (!water.contains(it.pointIndex)) {
                    landlockedRivers.add(it)
                    regionRivers.remove(it)
                }
            }
        }
        landlockedRivers.forEach { landlockedRiverMouth ->
            val thisRegion = idMask[landlockedRiverMouth.pointIndex]
            val adjacentRegions = HashSet<Int>()
            vertices.getAdjacentVertices(landlockedRiverMouth.pointIndex).forEach {
                if (idMask[it] != thisRegion) {
                    adjacentRegions.add(idMask[it] - 1)
                }
            }
            val adjacentRegionsRivers = ArrayList<RiverNode>()
            adjacentRegions.forEach {
                adjacentRegionsRivers.addAll(rivers[it])
            }
            val nodes = ArrayList<RiverNode>()
            adjacentRegionsRivers.forEach {
                getPoints(it, nodes)
            }
            nodes.sortBy { landlockedRiverMouth.pointLocation.distanceSquaredTo(it.pointLocation) }
            while (nodes.isNotEmpty()) {
                val nextCandidate = nodes.removeAt(0)
                if (isViableConnection(adjacentRegionsRivers, landlockedRiverMouth, nextCandidate)) {
                    landlockedRiverMouth.parent = nextCandidate
                    nextCandidate.children.add(landlockedRiverMouth)
                    break
                }
            }
        }
        val bodies = graph.getConnectedBodies(land).sortedByDescending { it.size }
        val coastSplices = HashMap<CellEdge, Point>()
        rivers.forEach { region ->
            HashSet(region).forEach { river ->
                if (water.contains(river.pointIndex)) {
                    val inlandStart = river.children.first()
                    val rootVertex = vertices[river.pointIndex]
                    val inlandVertex = vertices[inlandStart.pointIndex]
                    val coastEdge = rootVertex.cell.sharedEdge(inlandVertex.cell)
                    if (coastEdge != null) {
                        val outlet = getLineIntersection(Pair(coastEdge.tri1.center, coastEdge.tri2.center), Pair(rootVertex.point, inlandVertex.point)) ?: midPoint(coastEdge.tri1.center, coastEdge.tri2.center)
                        inlandStart.parent = null
                        river.children.clear()
                        inlandStart.pointLocation = outlet
                        region.remove(river)
                        region.add(inlandStart)
                        coastSplices[coastEdge] = outlet
                    }
                }
            }
        }
        val coastlines = bodies.map { graph.findBorder(it, splices = coastSplices)!! }
        val riverSets = ArrayList<Pair<Polygon, HashSet<RiverNode>>>(bodies.size)
        for (i in 0..bodies.size - 1) {
            riverSets.add(Pair(coastlines[i], HashSet()))
        }
        rivers.forEach { regionSet ->
            regionSet.forEach { river ->
                for (i in 0..bodies.size - 1) {
                    if (bodies[i].contains(river.pointIndex)) {
                        riverSets[i].second.add(river)
                    }
                }
            }
        }
        return riverSets
    }

    fun buildRiverGraph(rivers: HashSet<RiverNode>): Graph {
        val points = ArrayList<Point>()
        rivers.forEach {
            adjustRiverPoints(it, points)
        }
        return buildGraph(1.0f, points)
    }

    private fun adjustRiverPoints(river: RiverNode, points: ArrayList<Point>) {
        river.pointIndex = points.size
        points.add(river.pointLocation)
        river.children.forEach {
            adjustRiverPoints(it, points)
        }
    }

    private fun isViableConnection(rivers: ArrayList<RiverNode>, landlockedRiverMouth: RiverNode, nextCandidate: RiverNode): Boolean {
        val newEdge = Pair(nextCandidate.pointLocation, landlockedRiverMouth.pointLocation)
        rivers.forEach {
            if (isTooCloseToRiver(it, it, nextCandidate, newEdge, Float.MIN_VALUE)) {
                return false
            }
        }
        return true
    }

    private fun getPoints(river: RiverNode, points: ArrayList<RiverNode>) {
        points.add(river)
        river.children.forEach {
            getPoints(it, points)
        }
    }

    private fun calculateBodyCenter(vertices: Vertices, region: HashSet<Int>): Point {
        var sumX = 0.0f
        var sumY = 0.0f
        region.forEach {
            val lakePoint = vertices[it].point
            sumX += lakePoint.x
            sumY += lakePoint.y
        }
        val regionCenter = Point(sumX / region.size, sumY / region.size)
        return regionCenter
    }

    private fun extractBeachFromGraphAndWater(vertices: Vertices, water: HashSet<Int>): Pair<HashSet<Int>, HashSet<Int>> {
        val beach = HashSet<Int>()
        val coastalWater = HashSet<Int>()
        for (vertexId in 0..vertices.size - 1) {
            if (isCoastalPoint(vertices, water, vertexId)) {
                beach.add(vertexId)
            } else if (isCoastalWaterPoint(vertices, water, vertexId)) {
                coastalWater.add(vertexId)
            }
        }
        return Pair(beach, coastalWater)
    }

    private fun extractRegionsFromIdMask(graph: Graph, idMask: Matrix<Int>): Pair<HashSet<Int>, ArrayList<HashSet<Int>>> {
        val water = HashSet<Int>()
        val regions = ArrayList<HashSet<Int>>()
        val vertices = graph.vertices
        for (i in 0..vertices.size - 1) {
            val maskValue = idMask[i]
            if (maskValue == 0) {
                water.add(i)
            } else {
                val regionId = maskValue - 1
                if (regions.size < maskValue) {
                    for (j in 0..regionId - regions.size) {
                        regions.add(HashSet<Int>())
                    }
                }
                regions[regionId].add(i)
            }
        }
        return Pair(water, regions)
    }

    private fun findBorderPoints(vertices: Vertices, water: HashSet<Int>, region: HashSet<Int>): HashSet<Int> {
        return region.filter { isBorderPoint(vertices, water, region, it) }.toHashSet()
    }

    private fun isBorderPoint(vertices: Vertices, water: Set<Int>, region: Set<Int>, vertexId: Int): Boolean {
        vertices.getAdjacentVertices(vertexId).forEach { adjacentPoint ->
            if (!water.contains(adjacentPoint) && !region.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    private fun findCoastalPoints(vertices: Vertices, water: HashSet<Int>, region: HashSet<Int>): HashSet<Int> {
        return region.filter { isCoastalPoint(vertices, water, it) }.toHashSet()
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

    private fun isCoastalWaterPoint(vertices: Vertices, water: HashSet<Int>, vertexId: Int): Boolean {
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

    private fun findRiverMouthCandidates(graph: Graph, body: HashSet<Int>, beach: HashSet<Int>): Pair<HashSet<Int>, HashMap<Int, Float>> {
        val approxCellSize = 1.0f / graph.stride!!
        val concavityWeights = findConcavityWeights(graph, body, beach, approxCellSize * 3, approxCellSize * 5, approxCellSize * 7, approxCellSize * 13)
        val riverCandidates = HashMap<Int, Float>(concavityWeights.size).apply { putAll(concavityWeights) }
        val optimalRivers = HashSet<Int>()
        beach.forEach {
            if (isLocalOptimalRiver(graph, riverCandidates, it, MIN_RIVER_MOUTH_SEPARATION)) {
                optimalRivers.add(it)
            }
        }
        ArrayList(riverCandidates.keys).forEach {
            if (riverCandidates[it] == 0.0f) {
                riverCandidates.remove(it)
            }
        }
        return Pair(optimalRivers, riverCandidates)
    }

    private fun isLocalOptimalRiver(graph: Graph, riverCandidates: Map<Int, Float>, candidateId: Int, radius: Float): Boolean {
        val candidateValue = riverCandidates[candidateId]!!
        val candidate = graph.vertices[candidateId]
        val candidatePoint = candidate.point
        graph.getPointsWithinRadius(candidatePoint, radius).forEach {
            if (riverCandidates[it] ?: 0.0f > candidateValue) {
                return false
            }
        }
        return true
    }

    private fun findFurthestPointFromAllInOtherSet(vertices: Vertices, testSet: Set<Int>, otherSet: Set<Int>): Int {
        var furthestAway: Int? = null
        var distanceAway = Float.MIN_VALUE
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
        return vertices[id1].point.distanceSquaredTo(vertices[id2].point)
    }

    private fun buildRiverSlopeWeights(vertices: Vertices, border: Set<Int>, coast: Set<Int>, furthestFromBorder: Int, furthestFromCoast: Int, region: Collection<Int>): Map<Int, Float> {
        val vector = perpendicularVector(vertices, furthestFromBorder, furthestFromCoast)
        var extremityLine = perpendicularLine(vertices, furthestFromBorder, furthestFromCoast)
        var minPoint: Int? = null
        var minDist: Float = Float.MAX_VALUE
        var maxPoint: Int? = null
        var maxDist: Float = Float.MIN_VALUE
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
        var maxDistFromCoast = Float.MIN_VALUE
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

    private fun perpendicularVector(vertices: Vertices, id1: Int, id2: Int): Point {
        return perpendicularVector(vertices[id1].point, vertices[id2].point)
    }

    private fun perpendicularVector(p1: Point, p2: Point): Point {
        return Point(p1.y - p2.y, -(p1.x - p2.x))
    }

    private fun perpendicularLine(vertices: Vertices, id1: Int, id2: Int): Pair<Point, Point> {
        return perpendicularLine(vertices[id1].point, vertices[id2].point)
    }

    private fun perpendicularLine(p1: Point, p2: Point): Pair<Point, Point> {
        return lineFromPointAndVector(p1, perpendicularVector(p1, p2))
    }

    private fun lineFromPointAndVector(point: Point, vector: Point): Pair<Point, Point> {
        return Pair(point, Point(point.x + vector.x, point.y + vector.y))
    }

    private fun distanceToLine(vertices: Vertices, line: Pair<Point, Point>, id: Int): Float {
        return distanceToLine(line, vertices[id].point)
    }

    private fun distanceToLine(line: Pair<Point, Point>, point: Point): Float {
        val a = line.first.y - line.second.y
        val b = line.second.x - line.first.x
        val c = line.first.x * line.second.y - line.second.x * line.first.y
        val numerator = a * point.x + b * point.y + c
        val denominator = Math.sqrt((a * a + b * b).toDouble())
        return (numerator / denominator).toFloat()
    }

    private fun pointVectorToLine(vertices: Vertices, id: Int, vector: Point): Pair<Point, Point> {
        val point = vertices[id].point
        val lp1 = Point(point.x + vector.x, point.y + vector.y)
        return Pair(point, lp1)
    }

    private fun findClosestPointDistance(vertices: Vertices, testSet: Set<Int>, id: Int): Float {
        var closestPoint: Int? = null
        var minDistance = Float.MAX_VALUE
        val point = vertices[id].point
        testSet.forEach { testId ->
            val testPoint = vertices[testId].point
            val dist = point.distanceSquaredTo(testPoint)
            if (closestPoint == null || dist < minDistance) {
                closestPoint = testId
                minDistance = dist
            }
        }
        return Math.sqrt(minDistance.toDouble()).toFloat()
    }

    private fun findFurthestPoint(vertices: Vertices, testSet: Set<Int>, point: Point): Int {
        var furthestPoint: Int? = null
        var maxDistance = Float.MIN_VALUE
        testSet.forEach { testId ->
            val testPoint = vertices[testId].point
            val dist = point.distanceSquaredTo(testPoint)
            if (furthestPoint == null || dist > maxDistance) {
                furthestPoint = testId
                maxDistance = dist
            }
        }
        return furthestPoint!!
    }

    private fun updateLocalRiverCandidates(vertices: Vertices, border: Set<Int>, localRiverMouths: Set<Int>, riverCandidates: HashMap<Int, Float>) {
        val orderedRiverMouths = localRiverMouths.toList()
        val mouthDistances = ArrayList<Float>(orderedRiverMouths.size)
        var maxDist = Float.MIN_VALUE
        orderedRiverMouths.forEach {
            val closestDist = findClosestPointDistance(vertices, border, it)
            mouthDistances.add(closestDist)
            if (closestDist > maxDist) {
                maxDist = closestDist
            }
        }
        orderedRiverMouths.forEachIndexed { i, it ->
            val normalizedDistance = mouthDistances[i] / maxDist
            val candidateWeight = riverCandidates[it] ?: 0.0f
            val distanceWeighted = (0.1f * candidateWeight) + (0.9f * (candidateWeight * normalizedDistance))
            riverCandidates[it] = distanceWeighted
        }
    }

    private fun buildRiverNetwork(graph: Graph,
                                  random: Random,
                                  water: HashSet<Int>,
                                  rivers: ArrayList<HashSet<RiverNode>>,
                                  riverCandidates: HashMap<Int, Float>,
                                  region: HashSet<Int>,
                                  riverSlope: Map<Int, Float>,
                                  localRiverMouths: HashSet<Int>) {
        val border = graph.findBorder(region)!!
        val vertices = graph.vertices
        val candidateRiverNodes = findCandidateRiverNodes(vertices, localRiverMouths, riverCandidates)
        val riverNodes = ArrayList(candidateRiverNodes)
        ArrayList(riverNodes).forEach { riverNode ->
            val possibleOutlets = ArrayList<Int>()
            vertices.getAdjacentVertices(riverNode.pointIndex).forEach { adjacent ->
                if (water.contains(adjacent)) {
                    possibleOutlets.add(adjacent)
                }
            }
            val outlet = possibleOutlets.sortedBy { vertices[it].point.distanceSquaredTo(riverNode.pointLocation) }.firstOrNull()
            if (outlet != null) {
                riverNode.parent = RiverNode(NodeType.CONTINUATION, null, mutableListOf(riverNode), outlet, vertices[outlet].point, riverNode.priority, riverNode.riverPriority, 0.0f)
            } else {
                var landlockedRiverNode = riverNode
                var decrementCount = 1
                while (decrementCount > 0) {
                    var newNodeCandidate: Int? = null
                    var radius = RIVER_EDGE_DISTANCE_SEARCH_RADIUS
                    var distFromBorder2 = MIN_DISTANCE_FROM_BORDER_SQUARED
                    val distFromBorderIncrement = distFromBorder2 / 4
                    decrementCount = 0
                    while (newNodeCandidate == null) {
                        newNodeCandidate = findMostViableNewNodeCandidate(graph, border, region, hashSetOf(landlockedRiverNode), landlockedRiverNode, radius, distFromBorder2)
                        if (newNodeCandidate != null) {
                            break
                        }
                        radius += radius
                        distFromBorder2 = Math.max(0.0f, distFromBorder2 - (distFromBorderIncrement))
                        decrementCount++
                    }
                    candidateRiverNodes.remove(landlockedRiverNode)
                    val newPoint = vertices[newNodeCandidate!!].point
                    val edgeDist = Math.sqrt(landlockedRiverNode.pointLocation.distanceSquaredTo(newPoint).toDouble()).toFloat()
                    val localRiverSlope = riverSlope[newNodeCandidate] ?: 0.0f
                    val newElevation = landlockedRiverNode.elevation + (localRiverSlope * edgeDist)
                    val newNode = RiverNode(NodeType.CONTINUATION, landlockedRiverNode, ArrayList(), newNodeCandidate, newPoint, landlockedRiverNode.priority, landlockedRiverNode.riverPriority, newElevation)
                    landlockedRiverNode.children.add(newNode)
                    riverNodes.add(newNode)
                    candidateRiverNodes.add(newNode)
                    landlockedRiverNode = newNode
                }
            }
        }
        val localRivers = HashSet<RiverNode>()
//        riverNodes.forEach {
//            localRivers.add(findRoot(it))
//        }
        while (true) {
            val lowestElevation = findElevationOfLowestRiverNode(candidateRiverNodes)
            val admissibleCandidates = findAdmissibleCandidates(candidateRiverNodes, lowestElevation)
            val expansionCandidate = findExpansionCandidate(admissibleCandidates) ?: break
            val root = findRoot(expansionCandidate)
            if (root == expansionCandidate.parent) {
                if (isTooCloseToRiver(localRivers, expansionCandidate)) {
                    candidateRiverNodes.remove(expansionCandidate)
                    continue
                }
                localRivers.add(root)
            }
            if (expansionCandidate.children.size >= MAX_RIVER_CHILDREN) {
                candidateRiverNodes.remove(expansionCandidate)
                continue
            }
            val newNodeCandidate = findMostViableNewNodeCandidate(graph, border, region, localRivers, expansionCandidate, RIVER_EDGE_DISTANCE_SEARCH_RADIUS, MIN_DISTANCE_FROM_BORDER_SQUARED, expansionCandidate.parent == findRoot(expansionCandidate))
            if (newNodeCandidate == null) {
                candidateRiverNodes.remove(expansionCandidate)
            } else {
                val newPoint = vertices[newNodeCandidate].point
                val edgeDist = Math.sqrt(expansionCandidate.pointLocation.distanceSquaredTo(newPoint).toDouble()).toFloat()
                val localRiverSlope = riverSlope[newNodeCandidate] ?: 0.0f
                val newElevation = expansionCandidate.elevation + (localRiverSlope * edgeDist)
                val newPriority = if (expansionCandidate.type == NodeType.CONTINUATION) {
                    candidateRiverNodes.remove(expansionCandidate)
                    expansionCandidate.priority
                } else if (expansionCandidate.type == NodeType.SYMMETRIC) {
                    if (expansionCandidate.priority == 1) {
                        increasePriorityOfGraph(expansionCandidate)
                    }
                    expansionCandidate.priority - 1
                } else {
                    if (expansionCandidate.children.isEmpty()) {
                        expansionCandidate.priority
                    } else {
                        if (expansionCandidate.priority == 1) {
                            increasePriorityOfGraph(expansionCandidate)
                        }
                        expansionCandidate.priority - 1
                    }
                }
                val newNode = RiverNode(getRandomNodeType(random), expansionCandidate, ArrayList(), newNodeCandidate, newPoint, newPriority, expansionCandidate.riverPriority, newElevation)
                expansionCandidate.children.add(newNode)
//                riverNodes.add(newNode)
                candidateRiverNodes.add(newNode)
//                localRivers.add(findRoot(newNode))
            }
        }
        rivers.add(localRivers)
    }

    private fun isTooCloseToRiver(localRivers: HashSet<RiverNode>, expansionCandidate: RiverNode): Boolean {
        localRivers.forEach {
            val riverRoot = findRoot(it)
            if (isTooCloseToRiver(riverRoot, riverRoot, expansionCandidate)) {
                return true
            }
        }
        return false
    }

    private fun findCandidateRiverNodes(vertices: Vertices, localRiverMouths: HashSet<Int>, riverCandidates: HashMap<Int, Float>): ArrayList<RiverNode> {
        val orderedRiverMouths = ArrayList<Int>(localRiverMouths)
        orderedRiverMouths.sortByDescending { riverCandidates[it] ?: 0.0f }
        val candidateRiverNodes = ArrayList<RiverNode>()
        orderedRiverMouths.forEachIndexed { i, pointIndex ->
            candidateRiverNodes.add(RiverNode(NodeType.CONTINUATION, null, ArrayList(), pointIndex, vertices[pointIndex].point, 1, (orderedRiverMouths.size - i) + 1, 0.0f))
        }
        return candidateRiverNodes
    }

    private fun findElevationOfLowestRiverNode(candidateRiverNodes: ArrayList<RiverNode>): Float {
        var minElevation = Float.MAX_VALUE
        candidateRiverNodes.forEach {
            if (it.elevation < minElevation) {
                minElevation = it.elevation
            }
        }
        return minElevation
    }

    private fun findAdmissibleCandidates(candidateRiverNodes: ArrayList<RiverNode>, lowestElevation: Float): List<RiverNode> {
        val admissibleCandidates = ArrayList<RiverNode>()
        candidateRiverNodes.forEach {
            if (it.elevation < lowestElevation + MAX_CANDIDATE_ELEVATION_DIFF) {
                admissibleCandidates.add(it)
            }
        }
        return admissibleCandidates
    }

    private fun findExpansionCandidate(admissibleCandidates: List<RiverNode>): RiverNode? {
        var bestCandidate: RiverNode? = null
        var bestPriority = Int.MIN_VALUE
        admissibleCandidates.forEach {
            val finalBest = bestCandidate
            if (finalBest == null) {
                bestCandidate = it
                bestPriority = it.priority + it.riverPriority
            } else {
                val localPriority = it.priority + it.riverPriority
                if (localPriority > bestPriority || (localPriority == bestPriority && it.elevation < finalBest.elevation)) {
                    bestCandidate = it
                    bestPriority = localPriority
                }
            }
        }
        return bestCandidate
    }

    private fun findMostViableNewNodeCandidate(graph: Graph, border: Polygon, region: Set<Int>, rivers: HashSet<RiverNode>, expansionCandidate: RiverNode, radius: Float, minDistanceBorder2: Float = MIN_DISTANCE_FROM_BORDER_SQUARED, byAngle: Boolean = false): Int? {
        val vertices = graph.vertices
        val expansionPointIndex = expansionCandidate.pointIndex
        val point = vertices[expansionPointIndex].point
        var idealLocation: Point? = null
        if (byAngle) {
            val l1 = expansionCandidate.parent!!.pointLocation
            val l2 = expansionCandidate.pointLocation
            val distance = Math.sqrt(l1.distanceSquaredTo(l2).toDouble())
            val ndx = (l2.x - l1.x) / distance
            val ndy = (l2.y - l1.y) / distance
            val vx = ndx * IDEAL_RIVER_EDGE_DISTANCE
            val vy = ndy * IDEAL_RIVER_EDGE_DISTANCE
            idealLocation = Point((l2.x + vx).toFloat(), (l2.y + vy).toFloat())
        }
        graph.getPointsWithinRadius(point, radius)
                .filter { region.contains(it) }
                .map { vertices[it] }
                .map { Pair(it, point.distanceSquaredTo(it.point)) }
                .filter { it.second >= MIN_RIVER_EDGE_DISTANCE_SQUARED }
                .sortedBy { if (idealLocation == null) { Math.abs(it.second - IDEAL_RIVER_EDGE_DISTANCE_SQUARED) } else { idealLocation!!.distanceSquaredTo(it.first.point) } }.forEach { vertexPair ->
            val pointLocation = vertexPair.first.point
            if (isValidExpansionPoint(rivers, border, expansionCandidate, pointLocation, vertexPair.second, minDistanceBorder2)) {
                return vertexPair.first.id
            }
        }
        return null
    }

    private fun isValidExpansionPoint(rivers: HashSet<RiverNode>, border: Polygon, expansionCandidate: RiverNode, pointLocation: Point, edgeLength: Float, minDistanceBorder2: Float = MIN_DISTANCE_FROM_BORDER_SQUARED): Boolean {
        val newEdge = Pair(expansionCandidate.pointLocation, pointLocation)
        if (border.doesEdgeIntersect(newEdge)) {
            return false
        }
        if (border.distance2Between(pointLocation) < minDistanceBorder2) {
            return false
        }
        rivers.forEach {
            if (isTooCloseToRiver(it, it, expansionCandidate, newEdge, edgeLength)) {
                return false
            }
        }
        return true
    }

    private fun isTooCloseToRiver(rootNode: RiverNode, riverNode: RiverNode, expansionCandidate: RiverNode): Boolean {
        val p1 = riverNode.pointLocation
        riverNode.children.forEach {
            if (distance2Between(Pair(p1, it.pointLocation), expansionCandidate.pointLocation) < MIN_RIVER_SEPARATION_SQUARED) {
                return true
            }
            if (isTooCloseToRiver(rootNode, it, expansionCandidate)) {
                return true
            }
        }
        if (riverNode == rootNode && riverNode.children.isEmpty()) {
            return riverNode.pointLocation.distanceSquaredTo(expansionCandidate.pointLocation) < MIN_RIVER_SEPARATION_SQUARED
        }
        return false
    }

    private fun isTooCloseToRiver(rootNode: RiverNode, riverNode: RiverNode, expansionCandidate: RiverNode, edge: Pair<Point, Point>, edgeLength: Float): Boolean {
        val p1 = riverNode.pointLocation
        if (riverNode != expansionCandidate && riverNode.pointLocation.distanceSquaredTo(edge.second) <= edgeLength) {
            return true
        }
        riverNode.children.forEach {
            if (!(it == expansionCandidate || riverNode == expansionCandidate)) {
                if (distance2Between(edge, Pair(p1, it.pointLocation)) < MIN_RIVER_SEPARATION_SQUARED) {
                    return true
                }
            } else {
                if (distance2Between(Pair(p1, it.pointLocation), edge.second) < MIN_RIVER_SEPARATION_SQUARED) {
                    return true
                }
            }
            if (isTooCloseToRiver(rootNode, it, expansionCandidate, edge, edgeLength)) {
                return true
            }
        }
        if (riverNode == rootNode && riverNode != expansionCandidate && riverNode.children.isEmpty()) {
            return distance2Between(edge, riverNode.pointLocation) < MIN_RIVER_SEPARATION_SQUARED
        }
        return false
    }

    private fun findRoot(riverNode: RiverNode): RiverNode {
        val parent = riverNode.parent
        if (parent == null) {
            return riverNode
        } else {
            return findRoot(parent)
        }
    }

    private fun increasePriorityOfGraph(riverNode: RiverNode) {
        increasePriorityOfDownGraph(findRoot(riverNode))
    }

    private fun increasePriorityOfDownGraph(riverNode: RiverNode) {
        riverNode.children.forEach {
            increasePriorityOfDownGraph(it)
        }
        riverNode.priority += 1
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