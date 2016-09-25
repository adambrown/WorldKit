package com.grimfox.gec.util

import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.CellEdge
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Polygon2F
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils.findConcavityWeights
import java.util.*

object Rivers {

    enum class NodeType {
        CONTINUATION,
        SYMMETRIC,
        ASYMMETRIC
    }

    class RiverNode(var type: NodeType, var pointIndex: Int, var pointLocation: Point2F, var priority: Int, var riverPriority: Int, var elevation: Float)

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
    val MAX_RIVER_SLOPE = 0.37f
    val MAX_RIVER_CHILDREN = 3

    fun buildRivers(graph: Graph, idMask: Matrix<Int>, random: Random): ArrayList<Pair<Polygon2F, ArrayList<TreeNode<RiverNode>>>> {
        val vertices = graph.vertices
        val (water, regions) = extractRegionsFromIdMask(graph, idMask)
        val (beach, coastalWater) = extractBeachFromGraphAndWater(vertices, water)
        val land = LinkedHashSet(regions.flatMap { it })
        val landCenter = calculateBodyCenter(vertices, land)
        val rivers = ArrayList<LinkedHashSet<TreeNode<RiverNode>>>()
        val (bestRiverCandidates, riverCandidates) = findRiverMouthCandidates(graph, land, beach)
        regions.forEach { region ->
            val fakeCoastPoints: LinkedHashSet<Int>
            val fakeInteriorBorderPoints: LinkedHashSet<Int>
            val furthestFromBorder: Int
            val interiorBorderPoints = findBorderPoints(vertices, water, region)
            val coastalBorderPoints = findCoastalPoints(vertices, water, region)
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
            val localRiverMouths = LinkedHashSet(bestRiverCandidates)
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
        val landlockedRivers = LinkedHashSet<TreeNode<RiverNode>>()
        rivers.forEach { regionRivers ->
            ArrayList(regionRivers).forEach {
                if (!water.contains(it.value.pointIndex)) {
                    landlockedRivers.add(it)
                    regionRivers.remove(it)
                }
            }
        }
        landlockedRivers.forEach { landlockedRiverMouth ->
            val thisRegion = idMask[landlockedRiverMouth.value.pointIndex]
            val adjacentRegions = LinkedHashSet<Int>()
            vertices.getAdjacentVertices(landlockedRiverMouth.value.pointIndex).forEach {
                if (idMask[it] != thisRegion) {
                    adjacentRegions.add(idMask[it] - 1)
                }
            }
            val adjacentRegionsRivers = ArrayList<TreeNode<RiverNode>>()
            adjacentRegions.forEach {
                adjacentRegionsRivers.addAll(rivers[it])
            }
            val nodes = ArrayList<TreeNode<RiverNode>>()
            adjacentRegionsRivers.forEach {
                getPoints(it, nodes)
            }
            nodes.sortBy { landlockedRiverMouth.value.pointLocation.distance2(it.value.pointLocation) }
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
        val coastSplices = HashMap<CellEdge, Point2F>()
        rivers.forEach { region ->
            LinkedHashSet(region).forEach { river ->
                if (water.contains(river.value.pointIndex)) {
                    val inlandStart = river.children.first()
                    val rootVertex = vertices[river.value.pointIndex]
                    val inlandVertex = vertices[inlandStart.value.pointIndex]
                    val coastEdge = rootVertex.cell.sharedEdge(inlandVertex.cell)
                    if (coastEdge != null) {
                        val line = LineSegment2F(coastEdge.tri1.center, coastEdge.tri2.center)
                        val outlet = line.intersection(LineSegment2F(rootVertex.point, inlandVertex.point)) ?: line.interpolate(0.5f)
                        inlandStart.parent = null
                        river.children.clear()
                        inlandStart.value.pointLocation = outlet
                        region.remove(river)
                        region.add(inlandStart)
                        coastSplices[coastEdge] = outlet
                    }
                }
            }
        }
        val coastlines = bodies.map { graph.findBorder(it, splices = coastSplices)!! }
        val riverSets = ArrayList<Pair<Polygon2F, ArrayList<TreeNode<RiverNode>>>>(bodies.size)
        for (i in 0..bodies.size - 1) {
            riverSets.add(Pair(coastlines[i], ArrayList()))
        }
        rivers.forEach { regionSet ->
            regionSet.forEach { river ->
                for (i in 0..bodies.size - 1) {
                    if (bodies[i].contains(river.value.pointIndex)) {
                        riverSets[i].second.add(river)
                    }
                }
            }
        }
        return riverSets
    }

    fun buildRiverGraph(rivers: ArrayList<TreeNode<RiverNode>>): Graph {
        val points = ArrayList<Point2F>()
        rivers.forEach {
            adjustRiverPoints(it, points)
        }
        return buildGraph(1.0f, points)
    }

    private fun adjustRiverPoints(river: TreeNode<RiverNode>, points: ArrayList<Point2F>) {
        river.value.pointIndex = points.size
        points.add(river.value.pointLocation)
        river.children.forEach {
            adjustRiverPoints(it, points)
        }
    }

    private fun isViableConnection(rivers: ArrayList<TreeNode<RiverNode>>, landlockedRiverMouth: TreeNode<RiverNode>, nextCandidate: TreeNode<RiverNode>): Boolean {
        val newEdge = LineSegment2F(nextCandidate.value.pointLocation, landlockedRiverMouth.value.pointLocation)
        rivers.forEach {
            if (isTooCloseToRiver(it, it, nextCandidate, newEdge, Float.MIN_VALUE)) {
                return false
            }
        }
        return true
    }

    private fun getPoints(river: TreeNode<RiverNode>, points: ArrayList<TreeNode<RiverNode>>) {
        points.add(river)
        river.children.forEach {
            getPoints(it, points)
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

    private fun findRiverMouthCandidates(graph: Graph, body: LinkedHashSet<Int>, beach: LinkedHashSet<Int>): Pair<LinkedHashSet<Int>, HashMap<Int, Float>> {
        val approxCellSize = 1.0f / graph.stride!!
        val concavityWeights = findConcavityWeights(graph, body, beach, approxCellSize * 3, approxCellSize * 5, approxCellSize * 7, approxCellSize * 13)
        val riverCandidates = HashMap<Int, Float>(concavityWeights.size).apply { putAll(concavityWeights) }
        val optimalRivers = LinkedHashSet<Int>()
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
        return vertices[id1].point.distance2(vertices[id2].point)
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

    private fun findFurthestPoint(vertices: Vertices, testSet: Set<Int>, point: Point2F): Int {
        var furthestPoint: Int? = null
        var maxDistance = Float.MIN_VALUE
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
                                  water: LinkedHashSet<Int>,
                                  rivers: ArrayList<LinkedHashSet<TreeNode<RiverNode>>>,
                                  riverCandidates: HashMap<Int, Float>,
                                  region: LinkedHashSet<Int>,
                                  riverSlope: Map<Int, Float>,
                                  localRiverMouths: LinkedHashSet<Int>) {
        val border = graph.findBorder(region)!!
        val vertices = graph.vertices
        val candidateRiverNodes = findCandidateRiverNodes(vertices, localRiverMouths, riverCandidates)
        val riverNodes = ArrayList(candidateRiverNodes)
        ArrayList(riverNodes).forEach { riverNode ->
            val possibleOutlets = ArrayList<Int>()
            vertices.getAdjacentVertices(riverNode.value.pointIndex).forEach { adjacent ->
                if (water.contains(adjacent)) {
                    possibleOutlets.add(adjacent)
                }
            }
            val outlet = possibleOutlets.sortedBy { vertices[it].point.distance2(riverNode.value.pointLocation) }.firstOrNull()
            if (outlet != null) {
                riverNode.parent = TreeNode(RiverNode(NodeType.CONTINUATION, outlet, vertices[outlet].point, riverNode.value.priority, riverNode.value.riverPriority, 0.0f), null, mutableListOf(riverNode))
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
                        newNodeCandidate = findMostViableNewNodeCandidate(graph, border, region, LinkedHashSet(listOf(landlockedRiverNode)), landlockedRiverNode, radius, distFromBorder2)
                        if (newNodeCandidate != null) {
                            break
                        }
                        radius += radius
                        distFromBorder2 = Math.max(0.0f, distFromBorder2 - (distFromBorderIncrement))
                        decrementCount++
                    }
                    candidateRiverNodes.remove(landlockedRiverNode)
                    val newPoint = vertices[newNodeCandidate!!].point
                    val node = landlockedRiverNode.value
                    val edgeDist = Math.sqrt(node.pointLocation.distance2(newPoint).toDouble()).toFloat()
                    val localRiverSlope = riverSlope[newNodeCandidate] ?: 0.0f
                    val newElevation = node.elevation + (localRiverSlope * edgeDist)
                    val newNode = TreeNode(RiverNode(NodeType.CONTINUATION, newNodeCandidate, newPoint, node.priority, node.riverPriority, newElevation), landlockedRiverNode, ArrayList())
                    landlockedRiverNode.children.add(newNode)
                    riverNodes.add(newNode)
                    candidateRiverNodes.add(newNode)
                    landlockedRiverNode = newNode
                }
            }
        }
        val localRivers = LinkedHashSet<TreeNode<RiverNode>>()
        while (true) {
            val lowestElevation = findElevationOfLowestRiverNode(candidateRiverNodes)
            val admissibleCandidates = findAdmissibleCandidates(candidateRiverNodes, lowestElevation)
            val expansionCandidate = findExpansionCandidate(admissibleCandidates) ?: break
            val root = findRoot(expansionCandidate)
            if (!localRivers.contains(root)) {
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
                val node = expansionCandidate.value
                val edgeDist = Math.sqrt(node.pointLocation.distance2(newPoint).toDouble()).toFloat()
                val localRiverSlope = riverSlope[newNodeCandidate] ?: 0.0f
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
                val newNode = TreeNode(RiverNode(getRandomNodeType(random), newNodeCandidate, newPoint, newPriority, node.riverPriority, newElevation), expansionCandidate, ArrayList())
                expansionCandidate.children.add(newNode)
                candidateRiverNodes.add(newNode)
            }
        }
        rivers.add(localRivers)
    }

    private fun isTooCloseToRiver(localRivers: LinkedHashSet<TreeNode<RiverNode>>, expansionCandidate: TreeNode<RiverNode>): Boolean {
        localRivers.forEach {
            val riverRoot = findRoot(it)
            if (isTooCloseToRiver(riverRoot, riverRoot, expansionCandidate)) {
                return true
            }
        }
        return false
    }

    private fun findCandidateRiverNodes(vertices: Vertices, localRiverMouths: LinkedHashSet<Int>, riverCandidates: HashMap<Int, Float>): ArrayList<TreeNode<RiverNode>> {
        val orderedRiverMouths = ArrayList<Int>(localRiverMouths)
        orderedRiverMouths.sortByDescending { riverCandidates[it] ?: 0.0f }
        val candidateRiverNodes = ArrayList<TreeNode<RiverNode>>()
        orderedRiverMouths.forEachIndexed { i, pointIndex ->
            candidateRiverNodes.add(TreeNode(RiverNode(NodeType.CONTINUATION, pointIndex, vertices[pointIndex].point, 1, (orderedRiverMouths.size - i) + 1, 0.0f), null, ArrayList()))
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

    private fun findMostViableNewNodeCandidate(graph: Graph, border: Polygon2F, region: Set<Int>, rivers: LinkedHashSet<TreeNode<RiverNode>>, expansionCandidate: TreeNode<RiverNode>, radius: Float, minDistanceBorder2: Float = MIN_DISTANCE_FROM_BORDER_SQUARED, byAngle: Boolean = false): Int? {
        val vertices = graph.vertices
        val expansionPointIndex = expansionCandidate.value.pointIndex
        val point = vertices[expansionPointIndex].point
        var idealLocation: Point2F? = null
        if (byAngle) {
            val l1 = expansionCandidate.parent!!.value.pointLocation
            val l2 = expansionCandidate.value.pointLocation
            val distance = Math.sqrt(l1.distance2(l2).toDouble())
            val ndx = (l2.x - l1.x) / distance
            val ndy = (l2.y - l1.y) / distance
            val vx = ndx * IDEAL_RIVER_EDGE_DISTANCE
            val vy = ndy * IDEAL_RIVER_EDGE_DISTANCE
            idealLocation = Point2F((l2.x + vx).toFloat(), (l2.y + vy).toFloat())
        }
        graph.getPointsWithinRadius(point, radius)
                .filter { region.contains(it) }
                .map { vertices[it] }
                .map { Pair(it, point.distance2(it.point)) }
                .filter { it.second >= MIN_RIVER_EDGE_DISTANCE_SQUARED }
                .sortedBy { if (idealLocation == null) { Math.abs(it.second - IDEAL_RIVER_EDGE_DISTANCE_SQUARED) } else { idealLocation!!.distance2(it.first.point) } }.forEach { vertexPair ->
            val pointLocation = vertexPair.first.point
            if (isValidExpansionPoint(rivers, border, expansionCandidate, pointLocation, vertexPair.second, minDistanceBorder2)) {
                return vertexPair.first.id
            }
        }
        return null
    }

    private fun isValidExpansionPoint(rivers: LinkedHashSet<TreeNode<RiverNode>>, border: Polygon2F, expansionCandidate: TreeNode<RiverNode>, pointLocation: Point2F, edgeLength2: Float, minDistanceBorder2: Float = MIN_DISTANCE_FROM_BORDER_SQUARED): Boolean {
        val newEdge = LineSegment2F(expansionCandidate.value.pointLocation, pointLocation)
        if (border.doesEdgeIntersect(newEdge)) {
            return false
        }
        if (border.distance2(pointLocation) < minDistanceBorder2) {
            return false
        }
        rivers.forEach {
            if (isTooCloseToRiver(it, it, expansionCandidate, newEdge, edgeLength2)) {
                return false
            }
        }
        return true
    }

    private fun isTooCloseToRiver(rootNode: TreeNode<RiverNode>, riverNode: TreeNode<RiverNode>, expansionCandidate: TreeNode<RiverNode>): Boolean {
        val p1 = riverNode.value.pointLocation
        riverNode.children.forEach {
            if (LineSegment2F(p1, it.value.pointLocation).distance2(expansionCandidate.value.pointLocation) < MIN_RIVER_SEPARATION_SQUARED) {
                return true
            }
            if (isTooCloseToRiver(rootNode, it, expansionCandidate)) {
                return true
            }
        }
        if (riverNode == rootNode && riverNode.children.isEmpty()) {
            return riverNode.value.pointLocation.distance2(expansionCandidate.value.pointLocation) < MIN_RIVER_SEPARATION_SQUARED
        }
        return false
    }

    private fun isTooCloseToRiver(rootNode: TreeNode<RiverNode>, riverNode: TreeNode<RiverNode>, expansionCandidate: TreeNode<RiverNode>, edge: LineSegment2F, edgeLength2: Float): Boolean {
        val node = riverNode.value
        val p1 = node.pointLocation
        if (riverNode != expansionCandidate && p1.distance2(edge.b) <= edgeLength2) {
            return true
        }
        riverNode.children.forEach {
            val child = it.value
            if (!(it == expansionCandidate || riverNode == expansionCandidate)) {
                if (edge.distance2(LineSegment2F(p1, child.pointLocation)) < MIN_RIVER_SEPARATION_SQUARED) {
                    return true
                }
            } else {
                if (LineSegment2F(p1, child.pointLocation).distance2(edge.b) < MIN_RIVER_SEPARATION_SQUARED) {
                    return true
                }
            }
            if (isTooCloseToRiver(rootNode, it, expansionCandidate, edge, edgeLength2)) {
                return true
            }
        }
        if (riverNode == rootNode && riverNode != expansionCandidate && riverNode.children.isEmpty()) {
            return edge.distance2(node.pointLocation) < MIN_RIVER_SEPARATION_SQUARED
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