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
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

@Command(name = "borders", description = "Create mountains along the region borders.")
class BorderDefinitionFilter : Runnable {

    private enum class NodeType {
        CONTINUATION,
        SYMMETRIC,
        ASYMMETRIC
    }

    private class RiverNode(var type: NodeType, var parent: RiverNode?, var children: MutableList<RiverNode>, var pointIndex: Int, var pointLocation: Point, var priority: Int, var riverPriority: Int, var elevation: Float)
    private class WaterNode(val pointIndex: Int, var adjacentIn: WaterNode?, var adjacentOut: WaterNode?, val adjacentAll: MutableSet<Int>)

    companion object {
        val MAX_CANDIDATE_ELEVATION_DIFF = 3.0f
        val IDEAL_RIVER_EDGE_DISTANCE = 3.0f
        val MIN_RIVER_EDGE_DISTANCE = 2.0f
        val IDEAL_RIVER_EDGE_DISTANCE_SQUARED = IDEAL_RIVER_EDGE_DISTANCE * IDEAL_RIVER_EDGE_DISTANCE
        val MIN_RIVER_EDGE_DISTANCE_SQUARED = MIN_RIVER_EDGE_DISTANCE * MIN_RIVER_EDGE_DISTANCE
        val CONTINUATION_PRIORITY = 0.2f
        val SYMMETRIC_PRIORITY = 0.3f

    }

    @Option(name = arrayOf("-p", "--points"), description = "The data file to read as input.", required = true)
    var pointsFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-c", "--closest-points"), description = "The data file to read as input.", required = true)
    var closestPointsFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-m", "--mask-file"), description = "The mask file to read as input.", required = true)
    var maskFile: File = File(Main.workingDir, "mask.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var seed: Long = System.currentTimeMillis()

    override fun run() {
        DataFiles.openAndUse<ClosestPoints>(closestPointsFile) { closestPoints ->
            DataFiles.openAndUse<Int>(maskFile) { mask ->
                DataFiles.openAndUse<Point>(pointsFile) { points ->
                    DataFiles.createAndUse<Uint24Matrix>(outputFile, mask.exponent) { output ->
                        val random = Random(seed)
                        val edges = buildEdgeMap(closestPoints)
                        val pointCount = edges.size
                        val stride = mask.width
                        val edgeGraph = buildEdgeGraph(edges, pointCount)
                        val water = HashSet<Int>()
                        val beach = HashSet<Int>()
                        val coastalWater = HashSet<Int>()
                        val regions = ArrayList<HashSet<Int>>()
                        for (i in 0..mask.size.toInt() - 1) {
                            val maskValue = mask[i]
                            if (maskValue == 0) {
                                water.add(i)
                            } else {
                                val regionId = maskValue - 1
                                if (regions.size < maskValue) {
                                    for (j in 0..maskValue - regions.size) {
                                        regions.add(HashSet<Int>())
                                    }
                                }
                                regions[regionId].add(i)
                            }
                        }
                        for (i in 0..mask.size.toInt() - 1) {
                            if (isCoastalPoint(edgeGraph, water, i)) {
                                beach.add(i)
                            }
                        }
                        for (i in 0..mask.size.toInt() - 1) {
                            if (isCoastalWaterPoint(edgeGraph, water, i)) {
                                coastalWater.add(i)
                            }
                        }
                        val rivers = HashSet<RiverNode>()
                        val riverCandidates = findRiverMouthCandidates(stride, water, beach)
                        regions.forEach { region ->
                            val border = findBorderPoints(edgeGraph, water, region)
                            val coast = findCoastalPoints(edgeGraph, water, region)
                            val furthestFromCoast = findFurthestPointFromAllInOtherSet(border, coast, stride)
                            val furthestFromBorder = findFurthestPointFromAllInOtherSet(coast, border, stride)
                            val riverSlope = buildRiverSlopeWeights(border, coast, furthestFromBorder, furthestFromCoast, region, stride)
                            val localRiverMouths = HashSet(riverCandidates.keys)
                            localRiverMouths.retainAll(coast)
                            updateLocalRiverCandidates(stride, border, localRiverMouths, riverCandidates)
                            buildRiverNetwork(stride, random, edgeGraph, points, water, beach, rivers, riverCandidates, region, border, riverSlope, localRiverMouths)

                        }

                        val coastalWaterEdges = HashSet<Pair<Int, Int>>()
                        coastalWater.forEach { point1 ->
                            edgeGraph[point1].forEach { point2 ->
                                if (coastalWater.contains(point2)) {
                                    coastalWaterEdges.add(Pair(Math.min(point1, point2), Math.max(point1, point2)))
                                }
                            }
                        }

                        for (i in 0..mask.size.toInt() - 1) {
                            output[i] = Math.round((riverCandidates[i] ?: 0.0f) * 40000)
                        }

                        val multiplier = 8
                        val imageWidth = stride * multiplier
                        val image = BufferedImage(imageWidth, imageWidth, BufferedImage.TYPE_USHORT_GRAY)
                        val graphics = image.createGraphics()
                        graphics.background = Color.WHITE
                        graphics.clearRect(0, 0, imageWidth, imageWidth)
                        graphics.color = Color.BLACK

                        rivers.forEach {
                            drawGraph(graphics, it, multiplier)
                        }

                        coastalWaterEdges.forEach {
                            drawEdge(stride, points, graphics, it, multiplier)
                        }

                        coastalWater.forEach {
                            drawPoint(stride, points, graphics, it, multiplier)
                        }
                        ImageIO.write(image, "png", File("output/testing.png"))


                    }
                }
            }
        }
    }

    private fun buildRiverNetwork(stride: Int,
                                  random: Random,
                                  edgeGraph: ArrayList<ArrayList<Int>>,
                                  points: Matrix<Point>,
                                  water: HashSet<Int>,
                                  beach: HashSet<Int>,
                                  rivers: HashSet<RiverNode>,
                                  riverCandidates: HashMap<Int, Float>,
                                  region: HashSet<Int>,
                                  border: Set<Int>,
                                  riverSlope: Map<Int, Float>,
                                  localRiverMouths: HashSet<Int>) {
        val edgeRadius = buildRadiusMask(4)
        val candidateRiverNodes = findCandidateRiverNodes(stride, localRiverMouths, points, riverCandidates)
        val riverNodes = ArrayList(candidateRiverNodes)
        riverNodes.forEach { riverNode ->
            val possibleOutlets = ArrayList<Int>()
            edgeGraph[riverNode.pointIndex].forEach { adjacent ->
                if (water.contains(adjacent)) {
                    possibleOutlets.add(adjacent)
                }
            }
            val outlet = possibleOutlets.sortedBy { getPointLocation(stride, points, it).distanceSquaredTo(riverNode.pointLocation) }.first()
            riverNode.parent = RiverNode(NodeType.CONTINUATION, null, mutableListOf(riverNode), outlet, getPointLocation(stride, points, outlet), riverNode.priority, riverNode.riverPriority, 0.0f)
        }
        while (true) {
            val lowestElevation = findElevationOfLowestRiverNode(candidateRiverNodes)
            val admissibleCandidates = findAdmissibleCandidates(candidateRiverNodes, lowestElevation)
            val expansionCandidate = findExpansionCandidate(admissibleCandidates) ?: break
            val newNodeCandidate = findMostViableNewNodeCandidate(stride, edgeGraph, water, beach, border, region, edgeRadius, points, riverNodes, expansionCandidate, expansionCandidate.parent == findRoot(expansionCandidate))
            if (newNodeCandidate == null) {
                candidateRiverNodes.remove(expansionCandidate)
            } else {
                val newPoint = getPointLocation(stride, points, newNodeCandidate)
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
                riverNodes.add(newNode)
                candidateRiverNodes.add(newNode)
            }
        }
        riverNodes.forEach {
            rivers.add(findRoot(it))
        }
    }

    private fun drawGraph(graphics: Graphics2D, riverNode: RiverNode, multiplier: Int) {
        val x0 = Math.round(riverNode.pointLocation.x * multiplier)
        val y0 = Math.round(riverNode.pointLocation.y * multiplier)
        riverNode.children.forEach {
            val x1 = Math.round(it.pointLocation.x * multiplier)
            val y1 = Math.round(it.pointLocation.y * multiplier)
            graphics.drawLine(x0, y0, x1, y1)
            drawGraph(graphics, it, multiplier)
        }
        graphics.fillOval(x0 - 2, y0 - 2, 5, 5)
    }

    private fun drawPoint(stride: Int, points: Matrix<Point>, graphics: Graphics2D, pointIndex: Int, multiplier: Int) {
        val location = getPointLocation(stride, points, pointIndex)
        val x0 = Math.round(location.x * multiplier)
        val y0 = Math.round(location.y * multiplier)
        graphics.fillOval(x0 - 2, y0 - 2, 5, 5)
    }

    private fun drawEdge(stride: Int, points: Matrix<Point>, graphics: Graphics2D, edge: Pair<Int, Int>, multiplier: Int) {
        val l0 = getPointLocation(stride, points, edge.first)
        val l1 = getPointLocation(stride, points, edge.second)
        val x0 = Math.round(l0.x * multiplier)
        val y0 = Math.round(l0.y * multiplier)
        val x1 = Math.round(l1.x * multiplier)
        val y1 = Math.round(l1.y * multiplier)
        graphics.drawLine(x0, y0, x1, y1)
    }

    private fun increasePriorityOfGraph(riverNode: RiverNode) {
        increasePriorityOfDownGraph(findRoot(riverNode))
    }

    private fun findRoot(riverNode: RiverNode): RiverNode {
        val parent = riverNode.parent
        if (parent == null) {
            return riverNode
        } else {
            return findRoot(parent)
        }
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

    private fun findMostViableNewNodeCandidate(stride: Int, edgeGraph: ArrayList<ArrayList<Int>>, water: Set<Int>, beach: Set<Int>, border: Set<Int>, region: Set<Int>, mask: List<List<Boolean>>, points: Matrix<Point>, riverNodes: Collection<RiverNode>, expansionCandidate: RiverNode, byAngle: Boolean = false): Int? {
        val expansionPointIndex = expansionCandidate.pointIndex
        val maskOffset = mask.size / 2
        val (x0, y0) = pointFromIndex(stride, expansionPointIndex)
        var mostViableCandidate: Int? = null
        var mostViableDistanceError = Float.MAX_VALUE
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
        for (y in Math.max(0, y0 - maskOffset)..Math.min(stride - 1, y0 + maskOffset)) {
            for (x in Math.max(0, x0 - maskOffset)..Math.min(stride - 1, x0 + maskOffset)) {
                val maskY = (y - y0) + maskOffset
                val maskX = (x - x0) + maskOffset
                if (mask[maskY][maskX]) {
                    val pointIndex = y * stride + x
                    if (region.contains(pointIndex) && !water.contains(pointIndex) && !beach.contains(pointIndex) && !border.contains(pointIndex)) {
                        var isValid = true
                        edgeGraph[pointIndex].forEach outter@ { adjacentPoint ->
                            if (water.contains(adjacentPoint) || beach.contains(adjacentPoint) || border.contains(adjacentPoint)) {
                                isValid = false
                                return@outter
                            }
                        }
                        if (isValid) {
                            val pointLocation = getPointLocation(stride, points, pointIndex)
                            riverNodes.forEach {
                                if (it != expansionCandidate && pointLocation.distanceSquaredTo(it.pointLocation) < IDEAL_RIVER_EDGE_DISTANCE_SQUARED) {
                                    isValid = false
                                    return@forEach
                                }
                            }
                            if (isValid) {
                                if (byAngle) {
                                    val distanceSquared = expansionCandidate.pointLocation.distanceSquaredTo(pointLocation)
                                    val distanceError = idealLocation!!.distanceSquaredTo(pointLocation)
                                    if (mostViableCandidate == null || distanceError < mostViableDistanceError) {
                                        if (distanceSquared > MIN_RIVER_EDGE_DISTANCE_SQUARED) {
                                            mostViableCandidate = pointIndex
                                            mostViableDistanceError = distanceError
                                        }
                                    }
                                } else {
                                    val distanceSquared = expansionCandidate.pointLocation.distanceSquaredTo(pointLocation)
                                    val distanceError = Math.abs(IDEAL_RIVER_EDGE_DISTANCE_SQUARED - distanceSquared)
                                    if (mostViableCandidate == null || distanceError < mostViableDistanceError) {
                                        if (distanceSquared > MIN_RIVER_EDGE_DISTANCE_SQUARED) {
                                            mostViableCandidate = pointIndex
                                            mostViableDistanceError = distanceError
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return mostViableCandidate
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

    private fun findAdmissibleCandidates(candidateRiverNodes: ArrayList<RiverNode>, lowestElevation: Float): List<RiverNode> {
        val admissibleCandidates = ArrayList<RiverNode>()
        candidateRiverNodes.forEach {
            if (it.elevation < lowestElevation + MAX_CANDIDATE_ELEVATION_DIFF) {
                admissibleCandidates.add(it)
            }
        }
        return admissibleCandidates
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

    private fun findCandidateRiverNodes(stride: Int, localRiverMouths: HashSet<Int>, points: Matrix<Point>, riverCandidates: HashMap<Int, Float>): ArrayList<RiverNode> {
        val orderedRiverMouths = ArrayList<Int>(localRiverMouths)
        orderedRiverMouths.sortedByDescending { riverCandidates[it] ?: 0.0f }
        val candidateRiverNodes = ArrayList<RiverNode>()
        orderedRiverMouths.forEachIndexed { i, pointIndex ->
            candidateRiverNodes.add(RiverNode(NodeType.CONTINUATION, null, ArrayList(), pointIndex, getPointLocation(stride, points, pointIndex), 1, (orderedRiverMouths.size - i) + 1, 0.0f))
        }
        return candidateRiverNodes
    }

    private fun getPointLocation(stride: Int, points: Matrix<Point>, pointIndex: Int): Point {
        val pointLocation = points[pointIndex]
        val actualLocation = Point(stride * pointLocation.x, stride * pointLocation.y)
        return actualLocation
    }

    private fun updateLocalRiverCandidates(stride: Int, border: Set<Int>, localRiverMouths: Set<Int>, riverCandidates: HashMap<Int, Float>) {
        val orderedRiverMouths = localRiverMouths.toList()
        val mouthDistances = ArrayList<Float>(orderedRiverMouths.size)
        var maxDist = Float.MIN_VALUE
        orderedRiverMouths.forEach {
            val closestDist = findClosestPointDistance(stride, border, it)
            mouthDistances.add(closestDist)
            if (closestDist > maxDist) {
                maxDist = closestDist
            }
        }
        orderedRiverMouths.forEachIndexed { i, it ->
            val normalizedDistance = mouthDistances[i] / maxDist
            val candidateWeight = riverCandidates[it] ?: 0.0f
            val distanceWeighted = (0.2f * candidateWeight) + (0.8f * (candidateWeight * normalizedDistance))
            riverCandidates[it] = distanceWeighted
        }
    }

    private fun findRiverMouthCandidates(stride: Int, water: HashSet<Int>, beach: HashSet<Int>): HashMap<Int, Float> {
        val riverCandidates = HashMap<Int, Float>(beach.size)
        val radiusMask1 = buildRadiusMask(3)
        val radiusMask2 = buildRadiusMask(6)
        val radiusMask3 = buildRadiusMask(10)
        val radiusMask4 = buildRadiusMask(16)
        beach.forEach { it ->
            var landWaterRatio = calculateLandWaterRatioWithMinThreshold(stride, water, radiusMask1, it, 1.0f, 0.75f, 0.15f)
            landWaterRatio = calculateLandWaterRatioWithMinThreshold(stride, water, radiusMask2, it, landWaterRatio, 0.65f, 0.25f)
            landWaterRatio = calculateLandWaterRatioWithMinThreshold(stride, water, radiusMask3, it, landWaterRatio, 0.6f, 0.35f)
            riverCandidates[it] = calculateLandWaterRatioWithMinThreshold(stride, water, radiusMask4, it, landWaterRatio, 0.55f, 0.25f)
        }
        val riverCandidateRadiusMask = buildRadiusMask(10)
        beach.forEach {
            if (!isLocalOptimalRiver(stride, beach, riverCandidates, riverCandidateRadiusMask, it)) {
                riverCandidates.remove(it)
            }
        }
        ArrayList(riverCandidates.keys).forEach {
            if (riverCandidates[it] == 0.0f) {
                riverCandidates.remove(it)
            }
        }
        return riverCandidates
    }

    private fun calculateLandWaterRatioWithMinThreshold(stride: Int, water: HashSet<Int>, radiusMask: List<List<Boolean>>, point: Int, currentRatio: Float, threshold: Float, weight: Float): Float {
        if (currentRatio > 0.0f) {
            val landWaterRatio = calculateLandWaterRatio(stride, water, radiusMask, point)
            if (landWaterRatio < threshold) {
                return 0.0f
            } else {
                return (landWaterRatio * weight) * currentRatio
            }
        }
        return 0.0f
    }

    private fun calculateLandWaterRatio(stride: Int, water: Set<Int>, mask: List<List<Boolean>>, point: Int): Float {
        val maskOffset = mask.size / 2
        val (x0, y0) = pointFromIndex(stride, point)
        var total = 0
        var land = 0
        for (y in Math.max(0, y0 - maskOffset)..Math.min(stride - 1, y0 + maskOffset)) {
            for (x in Math.max(0, x0 - maskOffset)..Math.min(stride - 1, x0 + maskOffset)) {
                val maskY = (y - y0) + maskOffset
                val maskX = (x - x0) + maskOffset
                if (mask[maskY][maskX]) {
                    val pointIndex = y * stride + x
                    if (!water.contains(pointIndex)) {
                        land++
                    }
                    total++
                }
            }
        }
        return land.toFloat() / total
    }

    private fun isLocalOptimalRiver(stride: Int, beach: Set<Int>, riverCandidates: Map<Int, Float>, mask: List<List<Boolean>>, point: Int): Boolean {
        val maskOffset = mask.size / 2
        val (x0, y0) = pointFromIndex(stride, point)
        val pointValue = riverCandidates[point] ?: 0.0f
        for (y in Math.max(0, y0 - maskOffset)..Math.min(stride - 1, y0 + maskOffset)) {
            for (x in Math.max(0, x0 - maskOffset)..Math.min(stride - 1, x0 + maskOffset)) {
                val maskY = (y - y0) + maskOffset
                val maskX = (x - x0) + maskOffset
                if (mask[maskY][maskX]) {
                    val pointIndex = y * stride + x
                    if (beach.contains(pointIndex)) {
                        if (riverCandidates[pointIndex] ?: 0.0f > pointValue) {
                            return false
                        }
                    }
                }
            }
        }
        return true
    }

    private fun buildRadiusMask(radius: Int): List<List<Boolean>> {
        val stride = radius * 2 + 1
        val x0 = radius
        val y0 = radius
        val mask = ArrayList<MutableList<Boolean>>(stride)
        for (y in 0..stride - 1) {
            val row = ArrayList<Boolean>()
            mask.add(row)
            for (x in 0..stride - 1) {
                row.add(Math.sqrt(distanceSquared(x0, y0, x, y).toDouble()) <= radius)
            }
        }
        return mask
    }

    private fun buildRiverSlopeWeights(border: Set<Int>, coast: Set<Int>, furthestFromBorder: Int, furthestFromCoast: Int, region: Collection<Int>, stride: Int): Map<Int, Float> {
        val vector = perpendicularVector(stride, furthestFromBorder, furthestFromCoast)
        var extremityLine = perpendicularLine(stride, furthestFromBorder, furthestFromCoast)
        var minPoint: Int? = null
        var minDist: Float = Float.MAX_VALUE
        var maxPoint: Int? = null
        var maxDist: Float = Float.MIN_VALUE
        (border + coast).forEach {
            val localDist = distanceToLine(stride, extremityLine, it)
            if (minPoint == null || localDist < minDist) {
                minPoint = it
                minDist = localDist
            }
            if (maxPoint == null || localDist > maxDist) {
                maxPoint = it
                maxDist = localDist
            }
        }
        val minExtremity = pointVectorToLine(stride, minPoint!!, vector)
        val maxExtremity = pointVectorToLine(stride, maxPoint!!, vector)
        val distToMinExtremity = distanceToLine(stride, minExtremity, furthestFromBorder)
        val distToMaxExtremity = distanceToLine(stride, maxExtremity, furthestFromBorder)
        val extremityDist = if (distToMaxExtremity < distToMinExtremity) {
            extremityLine = maxExtremity
            distanceToLine(stride, extremityLine, minPoint!!)
        } else {
            extremityLine = minExtremity
            distanceToLine(stride, extremityLine, maxPoint!!)
        }
        val distsFromCoast = HashMap<Int, Float>(region.size)
        var maxDistFromCoast = Float.MIN_VALUE
        region.forEach { point ->
            val dist = findClosestPointDistance(stride, coast, point)
            if (dist > maxDistFromCoast) {
                maxDistFromCoast = dist
            }
            distsFromCoast.put(point, dist)
        }
        region.forEachIndexed { i, point ->
            val distFromCoastNormalized = distsFromCoast[i] ?: 0 / maxDistFromCoast * 0.5f
            val distFromExtremity = distanceToLine(stride, extremityLine, point) / extremityDist * 0.5f
            distsFromCoast[i] = distFromCoastNormalized + distFromExtremity
        }
        return distsFromCoast
    }

    private fun findFurthestPointFromAllInOtherSet(testSet: Set<Int>, otherSet: Set<Int>, stride: Int): Int {
        var furthestAway: Int? = null
        var distanceAway: Int = 0
        testSet.forEach { testPoint ->
            var localLeastDistance: Int = Int.MAX_VALUE
            otherSet.forEach { otherPoint ->
                val dist = distanceSquaredBetween(stride, testPoint, otherPoint)
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

    private fun findClosestPointDistance(stride: Int, testSet: Set<Int>, point: Int): Float {
        var closestPoint: Int? = null
        var minDistance: Int = Int.MAX_VALUE
        val (x0, y0) = pointFromIndex(stride, point)
        testSet.forEach { testPoint ->
            val (x1, y1) = pointFromIndex(stride, testPoint)
            val dist = distanceSquared(x0, y0, x1, y1)
            if (closestPoint == null || dist < minDistance) {
                closestPoint = testPoint
                minDistance = dist
            }
        }
        return Math.sqrt(minDistance.toDouble()).toFloat()
    }

    private fun findBorderPoints(edgeGraph: ArrayList<ArrayList<Int>>, water: HashSet<Int>, region: HashSet<Int>): Set<Int> {
        return region.filter { isBorderPoint(edgeGraph, water, region, it) }.toSet()
    }

    private fun isBorderPoint(edgeGraph: ArrayList<ArrayList<Int>>, water: Set<Int>, region: Set<Int>, testPoint: Int): Boolean {
        edgeGraph[testPoint].forEach { adjacentPoint ->
            if (!water.contains(adjacentPoint) && !region.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    private fun findCoastalPoints(edgeGraph: ArrayList<ArrayList<Int>>, water: HashSet<Int>, region: HashSet<Int>): Set<Int> {
        return region.filter { isCoastalPoint(edgeGraph, water, it) }.toSet()
    }

    private fun isCoastalPoint(edgeGraph: ArrayList<ArrayList<Int>>, water: Set<Int>, testPoint: Int): Boolean {
        if (water.contains(testPoint)) {
            return false
        }
        edgeGraph[testPoint].forEach { adjacentPoint ->
            if (water.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    private fun isCoastalWaterPoint(edgeGraph: ArrayList<ArrayList<Int>>, water: HashSet<Int>, testPoint: Int): Boolean {
        if (!water.contains(testPoint)) {
            return false
        }
        edgeGraph[testPoint].forEach { adjacentPoint ->
            if (!water.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    private fun distanceSquaredBetween(stride: Int, p0: Int, p1: Int): Int {
        val (x0, y0) = pointFromIndex(stride, p0)
        val (x1, y1) = pointFromIndex(stride, p1)
        return distanceSquared(x0, y0, x1, y1)
    }

    private fun distanceSquared(x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val xDelta = x0 - x1
        val yDelta = y0 - y1
        return xDelta * xDelta + yDelta * yDelta
    }

    private fun perpendicularLine(stride: Int, p0: Int, p1: Int): Pair<Point, Point> {
        val (x0, y0) = pointFromIndex(stride, p0)
        val (x1, y1) = pointFromIndex(stride, p1)
        val xDelta = x0 - x1
        val yDelta = y0 - y1
        val lp0 = Point(x0.toFloat(), y0.toFloat())
        val lp1 = Point(lp0.x + yDelta.toFloat(), lp0.y - xDelta.toFloat())
        return Pair(lp0, lp1)
    }

    private fun pointVectorToLine(stride: Int, point: Int, vector: Point): Pair<Point, Point> {
        val x = (point % stride).toFloat()
        val y = (point / stride).toFloat()
        val lp0 = Point(x, y)
        val lp1 = Point(lp0.x + vector.x, lp0.y + vector.y)
        return Pair(lp0, lp1)
    }

    private fun perpendicularVector(stride: Int, p0: Int, p1: Int): Point {
        val (x0, y0) = pointFromIndex(stride, p0)
        val (x1, y1) = pointFromIndex(stride, p1)
        val xDelta = x0 - x1
        val yDelta = y0 - y1
        return Point(yDelta.toFloat(), -xDelta.toFloat())
    }

    private fun pointFromIndex(stride: Int, p0: Int): Pair<Int, Int> {
        val x0 = p0 % stride
        val y0 = p0 / stride
        return Pair(x0, y0)
    }

    private fun distanceToLine(stride: Int, line: Pair<Point, Point>, point: Int): Float {
        val x = (point % stride).toDouble()
        val y = (point / stride).toDouble()
        val a = line.first.y - line.second.y
        val b = line.second.x - line.first.x
        val c = line.first.x * line.second.y - line.second.x * line.first.y
        val numerator = a * x + b * y + c
        val denominator = Math.sqrt((a * a + b * b).toDouble())
        return (numerator / denominator).toFloat()
    }
}
