package com.grimfox.gec.util

import com.grimfox.gec.extensions.call
import com.grimfox.gec.extensions.join
import com.grimfox.gec.extensions.value
import com.grimfox.gec.model.FloatArrayMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.ByteArrayMatrix
import com.grimfox.gec.model.geometry.Point3F
import com.grimfox.gec.util.geometry.renderTriangle
import org.joml.SimplexNoise.noise
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

object WaterFlows {

    private val MIN_U = 0.000004f
    private val MAX_U = 0.0005f
    private val DELTA_U = MAX_U - MIN_U
    private val K = 0.000000561f
    private val M = 0.5
    private val DT = 250000.0f
    private val DISTANCE_SCALE = 50000.0f

    private val HEIGHT_INCREMENT = 30.0 / 1023.0
    private val VARIANCE_FACTOR_LOW = 0.5
    private val HEIGHT_VARIANCE_LOW = FloatArray(1024 * 256) { i ->
        val height = i / 256
        val variance = i % 256
        val base = (height * HEIGHT_INCREMENT) + 15
        val baseVariance = (base * VARIANCE_FACTOR_LOW)
        val varianceMod = ((variance / 255.0) * baseVariance * 2.0) - baseVariance
        val angle = Math.toRadians(base + varianceMod)
        Math.tan(angle).toFloat()
    }
    private val VARIANCE_FACTOR_MID = 0.3
    private val HEIGHT_VARIANCE_MID = FloatArray(1024 * 256) { i ->
        val height = i / 256
        val variance = i % 256
        val base = (height * HEIGHT_INCREMENT) + 15
        val baseVariance = (base * VARIANCE_FACTOR_MID)
        val varianceMod = ((variance / 255.0) * baseVariance * 2.0) - baseVariance
        val angle = Math.toRadians(base + varianceMod)
        Math.tan(angle).toFloat()
    }
    private val VARIANCE_FACTOR_HIGH = 0.1
    private val HEIGHT_VARIANCE_HIGH = FloatArray(1024 * 256) { i ->
        val height = i / 256
        val variance = i % 256
        val base = (height * HEIGHT_INCREMENT) + 15
        val baseVariance = (base * VARIANCE_FACTOR_HIGH)
        val varianceMod = ((variance / 255.0) * baseVariance * 2.0) - baseVariance
        val angle = Math.toRadians(base + varianceMod)
        Math.tan(angle).toFloat()
    }
    private val HEIGHT_VARIANCE_FIXED = FloatArray(1024 * 256) { i ->
        val height = i / 256
        val base = (height * HEIGHT_INCREMENT) + 15
        val angle = Math.toRadians(base)
        Math.tan(angle).toFloat()
    }

    private val threadCount = Runtime.getRuntime().availableProcessors()

    class WaterNode(val id: Int,
                    val isExternal: Boolean,
                    val area: Float,
                    val adjacents: ArrayList<Pair<WaterNode, Float>>,
                    val simplexX: Float,
                    val simplexY: Float,
                    var uplift: Float,
                    var height: Float,
                    var drainageArea: Float) {

        var lake: Int = -1
        var parent: WaterNode = this
        var distanceToParent: Float = 0.0f
        val children: MutableCollection<WaterNode> = LinkedList()
    }

    data class PassKey(val lake1: Int, val lake2: Int)

    class Pass(val passKey: PassKey, val id1: Int, val id2: Int, val height: Float)

    fun generateWaterFlows(random: Random, inputGraph: Graph, inputMask: Matrix<Byte>, flowGraphSmall: Graph, flowGraphMedium: Graph, flowGraphLarge: Graph, executor: ExecutorService, outputWidth: Int): BufferedImage {
        val smallMapsFuture = executor.call {
            val (nodeIndex, nodes, rivers) = bootstrapErosion(executor, flowGraphSmall, inputGraph, inputMask, DISTANCE_SCALE, random)
            solveInitialLakes(executor, flowGraphSmall, nodeIndex, nodes, rivers)
        }
        val midNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphMedium, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val highNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphLarge, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val midMapsFuture = executor.call {
            val (heightMap, upliftMap) = smallMapsFuture.value
            val (nodeIndex, nodes, rivers) = midNodesFuture.value
            applyMapsToNodes(executor, flowGraphMedium.vertices, heightMap, upliftMap, nodes, 0.5f)
            performMidErosion(executor, flowGraphMedium, nodeIndex, nodes, rivers)
        }
        val (heightMap, upliftMap) = midMapsFuture.value
        val (nodeIndex, nodes, rivers) = highNodesFuture.value
        applyMapsToNodes(executor, flowGraphLarge.vertices, heightMap, upliftMap, nodes, 0.25f)
        return writeHeightMap(performHighErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, outputWidth))
//        return writeHeightMap(midMapsFuture.value.first)
//        return writeNoise(performHighErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, outputWidth))
    }

    private fun bootstrapErosion(executor: ExecutorService, graph: Graph, inputGraph: Graph, inputMask: Matrix<Byte>, distanceScale: Float, random: Random): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        val vertices = graph.vertices
        val land = ArrayList<Int>(vertices.size)
        val water = LinkedHashSet<Int>(vertices.size)
        val regions = ArrayList<LinkedHashSet<Int>>(16)
        for (i in 0..vertices.size - 1) {
            val point = vertices[i].point
            val closePoint = inputGraph.getClosestPoint(point, inputGraph.getClosePoints(point, 1))
            val maskValue = inputMask[closePoint]
            if (maskValue < 1) {
                water.add(i)
            } else {
                land.add(i)
                val regionId = maskValue - 1
                if (regions.size < maskValue) {
                    for (j in 0..regionId - regions.size) {
                        regions.add(LinkedHashSet<Int>())
                    }
                }
                regions[regionId].add(i)
            }
        }
        val beach = extractBeachFromGraphAndWater(vertices, water)
        val regionBeachFutures = regions.map { region ->
            executor.call {
                LinkedHashSet(region.filter { beach.contains(it) })
            }
        }
        val regionBeaches = regionBeachFutures.map { it.value }
        val regionBorderFutures = regions.map { region ->
            executor.call {
                LinkedHashSet(region.filter { isBorderPoint(vertices, water, region, it) })
            }
        }
        val regionBorders = regionBorderFutures.map { it.value }
        regionBeaches.forEachIndexed { i, regionBeach ->
            regionBeach.removeAll(regionBorders[i])
        }
        val upliftMap = ByteArrayMatrix(graph.stride!!) { -128 }
        val upliftMapFutures = regions.mapIndexed { i, region ->
            executor.call {
                calculateUplift3(vertices, region, regionBeaches[i], regionBorders[i], upliftMap)
            }
        }
        upliftMapFutures.forEach { it.join() }

        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, land, beach, upliftMap, distanceScale)
        val rivers = ArrayList<WaterNode>()
        beach.forEach { id ->
            rivers.add(nodeIndex[id]!!)
        }
        val unused = LinkedHashSet(land)
        val used = LinkedHashSet(beach)
        unused.removeAll(used)
        val next = LinkedHashSet(beach)
        while (unused.isNotEmpty()) {
            val nextOrder = ArrayList(next)
            Collections.shuffle(nextOrder, random)
            next.clear()
            nextOrder.forEach { id ->
                val node = nodeIndex[id]!!
                node.adjacents.forEach { pair ->
                    val otherNode = pair.first
                    if (!used.contains(otherNode.id)) {
                        otherNode.parent.children.remove(otherNode)
                        otherNode.parent = node
                        otherNode.distanceToParent = pair.second
                        node.children.add(otherNode)
                        next.add(otherNode.id)
                        used.add(otherNode.id)
                        unused.remove(otherNode.id)
                    }
                }
            }
        }
        computeAreas(executor, rivers)
        computeHeights(executor, DT * 0.34f, rivers, HEIGHT_VARIANCE_FIXED)
        return Triple(nodeIndex, nodes, rivers)
    }

    private fun solveInitialLakes(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>): Pair<FloatArrayMatrix, FloatArrayMatrix> {
        var deltaTime = DT * 12
        val lakes = ArrayList<WaterNode>()
        val localLakePool = Array<ArrayList<WaterNode>>(threadCount) { ArrayList() }
        val passes = LinkedHashMap<PassKey, Pass>()
        for (i in 1..50) {
            if (i == 2) {
                deltaTime = DT * 0.3f
            } else if (i == 6) {
                deltaTime = DT
            }
            lakes.clear()
            passes.clear()
            prepareNodesAndLakes(executor, lakes, localLakePool, nodes, rivers)
            computeLakeConnections(graph.vertices, lakes, nodeIndex, passes, rivers)
            computeAreas(executor, rivers)
            computeHeights(executor, deltaTime, rivers, HEIGHT_VARIANCE_LOW)
        }
        val heightMap = FloatArrayMatrix(1024) { -1.0f }
        val upliftMap = FloatArrayMatrix(512) { 0.0f }
        renderTrianglesAndUplift(executor, graph, nodeIndex, heightMap, upliftMap, threadCount)
        return Pair(heightMap, upliftMap)
    }

    private fun performMidErosion(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>): Pair<FloatArrayMatrix, FloatArrayMatrix> {
        val deltaTime = DT
        val lakes = ArrayList<WaterNode>()
        val localLakePool = Array<ArrayList<WaterNode>>(threadCount) { ArrayList() }
        val passes = LinkedHashMap<PassKey, Pass>()
        for (i in 1..25) {
            lakes.clear()
            passes.clear()
            prepareNodesAndLakes(executor, lakes, localLakePool, nodes, rivers)
            computeLakeConnections(graph.vertices, lakes, nodeIndex, passes, rivers)
            computeAreas(executor, rivers)
            computeHeights(executor, deltaTime, rivers, HEIGHT_VARIANCE_MID)
        }
        val heightMap = FloatArrayMatrix(2048) { -1.0f }
        val upliftMap = FloatArrayMatrix(1024) { 0.0f }
        renderTrianglesAndUplift(executor, graph, nodeIndex, heightMap, upliftMap, threadCount)
        return Pair(heightMap, upliftMap)
    }

    private fun performHighErosion(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>, outputWidth: Int): Matrix<Float> {
        val deltaTime = DT
        val lakes = ArrayList<WaterNode>()
        val localLakePool = Array<ArrayList<WaterNode>>(threadCount) { ArrayList() }
        val passes = LinkedHashMap<PassKey, Pass>()
        for (i in 1..5) {
            lakes.clear()
            passes.clear()
            prepareNodesAndLakes(executor, lakes, localLakePool, nodes, rivers)
            computeLakeConnections(graph.vertices, lakes, nodeIndex, passes, rivers)
            computeAreas(executor, rivers)
            if (i < 3) {
                computeHeights(executor, deltaTime, rivers, HEIGHT_VARIANCE_HIGH)
            } else {
                computeHeights(executor, deltaTime, rivers, HEIGHT_VARIANCE_FIXED)
            }
        }
        val heightMap = FloatArrayMatrix(outputWidth) { -1.0f }
        renderTriangles(executor, graph, nodeIndex, heightMap, threadCount)
        return heightMap
    }

    private fun prepareGraphNodes(executor: ExecutorService, graph: Graph, inputGraph: Graph, inputMask: Matrix<Byte>, distanceScale: Float): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        val vertices = graph.vertices
        val land = ArrayList<Int>(vertices.size)
        val water = LinkedHashSet<Int>(vertices.size)
        for (i in 0..vertices.size - 1) {
            val point = vertices[i].point
            val closePoint = inputGraph.getClosestPoint(point, inputGraph.getClosePoints(point, 1))
            val maskValue = inputMask[closePoint]
            if (maskValue < 1) {
                water.add(i)
            } else {
                land.add(i)
            }
        }
        val beach = extractBeachFromGraphAndWater(vertices, water)

        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, land, beach, distanceScale)
        val rivers = ArrayList<WaterNode>()
        beach.forEach { id ->
            rivers.add(nodeIndex[id]!!)
        }
        return Triple(nodeIndex, nodes, rivers)
    }

    private fun computeLakeConnections(vertices: Graph.Vertices, lakes: ArrayList<WaterNode>, nodeIndex: Array<WaterNode?>, passes: LinkedHashMap<PassKey, Pass>, rivers: ArrayList<WaterNode>) {
        lakes.forEach { waterNode ->
            recurseFindPasses(nodeIndex, waterNode, passes)
        }
        val expandedPasses = ArrayList<Pass>(passes.size * 2)
        passes.values.forEach {
            expandedPasses.add(it)
            expandedPasses.add(Pass(PassKey(it.passKey.lake2, it.passKey.lake1), it.id2, it.id1, it.height))
        }
        expandedPasses.sortByDescending { it.height }
        val outflowing = LinkedHashSet<Int>()
        rivers.forEach {
            outflowing.add(it.lake)
        }
        while (expandedPasses.isNotEmpty()) {
            for (j in (expandedPasses.size - 1) downTo 0) {
                val currentPass = expandedPasses[j]
                if (outflowing.contains(currentPass.passKey.lake1)) {
                    expandedPasses.removeAt(j)
                    continue
                } else if (outflowing.contains(currentPass.passKey.lake2)) {
                    outflowing.add(currentPass.passKey.lake1)
                    expandedPasses.removeAt(j)
                    val childNode = recurseFindRoot(nodeIndex[currentPass.id1]!!)
                    val parentNode = nodeIndex[currentPass.id2]!!
                    parentNode.children.add(childNode)
                    childNode.parent = parentNode
                    childNode.distanceToParent = vertices[childNode.id].point.distance(vertices[parentNode.id].point)
                    break
                }
            }
        }
    }

    private fun prepareNodesAndLakes(executor: ExecutorService, lakes: ArrayList<WaterNode>, localLakePool: Array<ArrayList<WaterNode>>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>) {
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                val localLakes = localLakePool[i]
                localLakes.clear()
                for (id in i..nodes.size - 1 step threadCount) {
                    val node = nodes[id]
                    node.lake = -1
                    if (!node.isExternal) {
                        var minHeight = node.height
                        var minNode = node
                        var distToMin = 0.0f
                        node.adjacents.forEach { pair ->
                            val otherNode = pair.first
                            if (otherNode.height < minHeight) {
                                minNode = otherNode
                                distToMin = pair.second
                                minHeight = otherNode.height
                            }
                        }
                        if (minNode != node.parent) {
                            synchronized(node.parent.children) {
                                node.parent.children.remove(node)
                            }
                            node.parent = minNode
                            node.distanceToParent = distToMin
                            if (minNode != node) {
                                synchronized(minNode.children) {
                                    minNode.children.add(node)
                                }
                            }
                        }
                        if (minNode == node) {
                            localLakes.add(node)
                        }
                    }
                }
                localLakes
            }
        }
        nodeFutures.forEach { lakes.addAll(it.value) }
        val futures = ArrayList<Future<*>>()
        rivers.forEachIndexed { id, waterNode ->
            futures.add(executor.call { recurseSetLake(id, waterNode) })
        }
        lakes.forEachIndexed { id, waterNode ->
            futures.add(executor.call { recurseSetLake(id + rivers.size, waterNode) })
        }
        futures.forEach { it.join() }
    }

    private fun createWaterNodes(executor: ExecutorService, vertices: Graph.Vertices, land: ArrayList<Int>, riverMouths: LinkedHashSet<Int>, upliftMap: ByteArrayMatrix, distanceScale: Float): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..land.size - 1 step threadCount) {
                    val landId = land[id]
                    val tempLift = upliftMap[landId].toInt() + 128
                    val uplift: Float = if (tempLift == 0) {
                        0.0f
                    } else {
                        (tempLift / 256.0f) * DELTA_U + MIN_U
                    }
                    val isExternal = riverMouths.contains(landId)
                    val vertex = vertices[landId]
                    val area = vertex.cell.area * areaScale
                    val point = vertex.point
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * 128, point.y * 128, uplift, 0.0f, area)
                    nodeIndex[landId] = node
                }
            }
        }
        nodeFutures.forEach { it.join() }
        val nodes = ArrayList<WaterNode>(land.size)
        nodeIndex.forEach {
            if (it != null) {
                nodes.add(it)
            }
        }
        val nodeFutures2 = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..nodes.size - 1 step threadCount) {
                    val node = nodes[id]
                    val vertex = vertices[node.id]
                    val position = vertex.point
                    vertex.adjacentVertices.forEach { adjacent ->
                        val otherNode = nodeIndex[adjacent.id]
                        if (otherNode != null) {
                            node.adjacents.add(Pair(otherNode, position.distance(adjacent.point) * distanceScale))
                        }
                    }
                }
            }
        }
        nodeFutures2.forEach { it.join() }
        return Pair(nodeIndex, nodes)
    }

    private fun createWaterNodes(executor: ExecutorService, vertices: Graph.Vertices, land: ArrayList<Int>, riverMouths: LinkedHashSet<Int>, distanceScale: Float): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..land.size - 1 step threadCount) {
                    val landId = land[id]
                    val isExternal = riverMouths.contains(landId)
                    val vertex = vertices[landId]
                    val area = vertex.cell.area * areaScale
                    val point = vertex.point
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * 128, point.y * 128, if (isExternal) 0.0f else MIN_U, 0.0f, area)
                    nodeIndex[landId] = node
                }
            }
        }
        nodeFutures.forEach { it.join() }
        val nodes = ArrayList<WaterNode>(land.size)
        nodeIndex.forEach {
            if (it != null) {
                nodes.add(it)
            }
        }
        val nodeFutures2 = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..nodes.size - 1 step threadCount) {
                    val node = nodes[id]
                    val vertex = vertices[node.id]
                    val position = vertex.point
                    vertex.adjacentVertices.forEach { adjacent ->
                        val otherNode = nodeIndex[adjacent.id]
                        if (otherNode != null) {
                            node.adjacents.add(Pair(otherNode, position.distance(adjacent.point) * distanceScale))
                        }
                    }
                }
            }
        }
        nodeFutures2.forEach { it.join() }
        return Pair(nodeIndex, nodes)
    }

    private fun applyMapsToNodes(executor: ExecutorService, vertices: Graph.Vertices, heightMap: Matrix<Float>, upliftMap: Matrix<Float>, nodes: ArrayList<WaterNode>, upliftMultiplier: Float) {
        val hWidth = heightMap.width
        val hWidthM1 = hWidth - 1
        val uWidth = upliftMap.width
        val uWidthM1 = uWidth - 1
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..nodes.size - 1 step threadCount) {
                    val node = nodes[id]
                    if (!node.isExternal) {
                        val point = vertices[node.id].point
                        val hIndex = (Math.round(point.y * hWidthM1) * hWidth) + Math.round(point.x * hWidthM1)
                        val height = heightMap[hIndex]
                        node.height = Math.max(0.0f, height)
                        val uIndex = (Math.round(point.y * uWidthM1) * uWidth) + Math.round(point.x * uWidthM1)
                        val uplift = upliftMap[uIndex]
                        node.uplift = Math.max(MIN_U, uplift * upliftMultiplier)
                    }
                }
            }
        }
        nodeFutures.forEach { it.join() }
    }

    private fun computeAreas(executor: ExecutorService, rivers: ArrayList<WaterNode>) {
        val areaFutures = rivers.map { river ->
            executor.call {
                recurseArea(river)
            }
        }
        areaFutures.forEach { it.join() }
    }

    private fun computeHeights(executor: ExecutorService, deltaTime: Float, rivers: ArrayList<WaterNode>, varianceTable: FloatArray) {
        val heightFutures = rivers.map { river ->
            executor.call {
                recurseHeights(river, deltaTime, varianceTable)
            }
        }
        heightFutures.forEach { it.join() }
    }

    private fun recurseFindRoot(waterNode: WaterNode): WaterNode {
        if (waterNode.isExternal || waterNode.parent == waterNode) {
            return waterNode
        }
        return recurseFindRoot(waterNode.parent)
    }

    private fun recurseFindPasses(nodeIndex: Array<WaterNode?>, node: WaterNode, passes: LinkedHashMap<PassKey, Pass>) {
        node.adjacents.forEach { pair ->
            val otherNode = pair.first
            if (otherNode.lake != node.lake) {
                val minLakeId = Math.min(node.lake, otherNode.lake)
                val swapped = node.lake != minLakeId
                val passKey = PassKey(minLakeId, Math.max(node.lake, otherNode.lake))
                val passHeight = Math.max(node.height, otherNode.height)
                val currentMin = passes[passKey]
                if (currentMin == null || currentMin.height > passHeight) {
                    if (swapped) {
                        passes[passKey] = Pass(passKey, otherNode.id, node.id, passHeight)
                    } else {
                        passes[passKey] = Pass(passKey, node.id, otherNode.id, passHeight)
                    }
                }
            }
        }
        node.children.forEach { recurseFindPasses(nodeIndex, it, passes) }
    }

    private fun recurseSetLake(id: Int, node: WaterNode) {
        node.lake = id
        node.children.forEach { child ->
            recurseSetLake(id, child)
        }
    }

    private fun recurseArea(node: WaterNode): Float {
        if (node.children.isEmpty()) {
            node.drainageArea = node.area
            return node.drainageArea
        } else {
            var sum: Float = node.area
            node.children.forEach { child ->
                sum += recurseArea(child)
            }
            node.drainageArea = sum
            return sum
        }
    }

    private fun recurseHeights(node: WaterNode, deltaTime: Float, varianceTable: FloatArray) {
        if (node.isExternal) {
            node.children.forEach { recurseHeights(it, deltaTime, varianceTable) }
        } else {
            val parent = node.parent
            val parentHeight = parent.height
            val flow = K * Math.pow(node.drainageArea.toDouble(), M)
            val erosion = flow / node.distanceToParent
            val denominator = 1.0 + (erosion * deltaTime)
            val numerator = node.height + (deltaTime * (node.uplift + (erosion * parentHeight)))
            node.height = (numerator / denominator).toFloat()
            val variance = noise(node.simplexX, node.simplexY, node.height / 10.0f)
            val talus = varianceTable[(Math.min(1023, Math.max(0, Math.round(node.height))) * 256) + Math.round((variance + 1) * 128)]
            if ((node.height - parent.height) / node.distanceToParent > talus) {
                node.height = (node.distanceToParent * talus) + parent.height
            }
            node.children.forEach { recurseHeights(it, deltaTime, varianceTable) }
        }
    }

    private fun calculateUplift(vertices: Graph.Vertices, region: LinkedHashSet<Int>, beach: LinkedHashSet<Int>, border: LinkedHashSet<Int>, upliftMap: Matrix<Byte>) {
        val remaining = HashSet(region)
        var currentIds = HashSet(border)
        var nextIds = HashSet<Int>(border.size)
        var currentUplift = 127.toByte()
        for (i in 0..11) {
            currentIds.forEach {
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = currentUplift
                    vertices.getAdjacentVertices(it).forEach { adjacentId ->
                        if (remaining.contains(adjacentId) && !currentIds.contains(adjacentId)) {
                            nextIds.add(adjacentId)
                        }
                    }
                }
            }
            val temp = currentIds
            currentIds = nextIds
            nextIds = temp
            nextIds.clear()
            currentUplift = (currentUplift - if (i % 2 == 0) 13 else 14).toByte()
        }
        currentIds.clear()
        currentIds.addAll(beach)
        nextIds.clear()
        currentUplift = -126
        for (i in 1..33) {
            currentIds.forEach {
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = currentUplift
                    vertices.getAdjacentVertices(it).forEach { adjacentId ->
                        if (remaining.contains(adjacentId) && !currentIds.contains(adjacentId)) {
                            nextIds.add(adjacentId)
                        }
                    }
                }
            }
            val temp = currentIds
            currentIds = nextIds
            nextIds = temp
            nextIds.clear()
            if (i < 33) {
                currentUplift = (currentUplift + if (i % 2 == 0) 2 else 3).toByte()
            }
        }
        remaining.forEach { upliftMap[it] = currentUplift }
    }

    private fun calculateUplift2(vertices: Graph.Vertices, region: LinkedHashSet<Int>, beach: LinkedHashSet<Int>, border: LinkedHashSet<Int>, upliftMap: Matrix<Byte>) {
        val remaining = HashSet(region)
        var currentIds = HashSet(border)
        var nextIds = HashSet<Int>(border.size)
        var currentUplift = 127.toByte()
        for (i in 0..5) {
            currentIds.forEach {
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = currentUplift
                    vertices.getAdjacentVertices(it).forEach { adjacentId ->
                        if (remaining.contains(adjacentId) && !currentIds.contains(adjacentId)) {
                            nextIds.add(adjacentId)
                        }
                    }
                }
            }
            val temp = currentIds
            currentIds = nextIds
            nextIds = temp
            nextIds.clear()
            currentUplift = (currentUplift - 27).toByte()
        }
        currentIds.clear()
        currentIds.addAll(beach)
        nextIds.clear()
        currentUplift = -126
        for (i in 1..16) {
            currentIds.forEach {
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = currentUplift
                    vertices.getAdjacentVertices(it).forEach { adjacentId ->
                        if (remaining.contains(adjacentId) && !currentIds.contains(adjacentId)) {
                            nextIds.add(adjacentId)
                        }
                    }
                }
            }
            val temp = currentIds
            currentIds = nextIds
            nextIds = temp
            nextIds.clear()
            if (i < 16) {
                currentUplift = (currentUplift + if (i % 5 == 0) 6 else 5).toByte()
            }
        }
        remaining.forEach { upliftMap[it] = currentUplift }
    }

    private fun calculateUplift3(vertices: Graph.Vertices, region: LinkedHashSet<Int>, beach: LinkedHashSet<Int>, border: LinkedHashSet<Int>, upliftMap: Matrix<Byte>) {
        val remaining = HashSet(region)
        var currentIds = HashSet(border)
        var nextIds = HashSet<Int>(border.size)
        var currentUplift = 127.toByte()
        for (i in 0..2) {
            currentIds.forEach {
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = currentUplift
                    vertices.getAdjacentVertices(it).forEach { adjacentId ->
                        if (remaining.contains(adjacentId) && !currentIds.contains(adjacentId)) {
                            nextIds.add(adjacentId)
                        }
                    }
                }
            }
            val temp = currentIds
            currentIds = nextIds
            nextIds = temp
            nextIds.clear()
            currentUplift = (currentUplift - 54).toByte()
        }
        currentIds.clear()
        currentIds.addAll(beach)
        nextIds.clear()
        currentUplift = -126
        for (i in 0..7) {
            currentIds.forEach {
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = currentUplift
                    vertices.getAdjacentVertices(it).forEach { adjacentId ->
                        if (remaining.contains(adjacentId) && !currentIds.contains(adjacentId)) {
                            nextIds.add(adjacentId)
                        }
                    }
                }
            }
            val temp = currentIds
            currentIds = nextIds
            nextIds = temp
            nextIds.clear()
            if (i < 7) {
                currentUplift = (currentUplift + if (i % 3 == 0) 11 else 10).toByte()
            }
        }
        remaining.forEach { upliftMap[it] = currentUplift }
    }

    private fun extractBeachFromGraphAndWater(vertices: Graph.Vertices, water: LinkedHashSet<Int>) =
            (0..vertices.size - 1).asSequence().filterTo(LinkedHashSet<Int>()) { isCoastalPoint(vertices, water, it) }

    private fun isCoastalPoint(vertices: Graph.Vertices, water: Set<Int>, vertexId: Int): Boolean {
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

    private fun isBorderPoint(vertices: Graph.Vertices, water: Set<Int>, region: Set<Int>, vertexId: Int): Boolean {
        vertices.getAdjacentVertices(vertexId).forEach { adjacentPoint ->
            if (!water.contains(adjacentPoint) && !region.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    fun renderTriangles(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMap: Matrix<Float>, threadCount: Int) {
        val futures = ArrayList<Future<*>>(threadCount)
        val triangles = graph.triangles
        for (i in 0..threadCount - 1) {
            futures.add(executor.submit {
                for (t in i..triangles.size - 1 step threadCount) {
                    val triangle = triangles[t]
                    val va = triangle.a
                    val vb = triangle.b
                    val vc = triangle.c
                    val na = nodeIndex[va.id]
                    val nb = nodeIndex[vb.id]
                    val nc = nodeIndex[vc.id]
                    if (na == null || nb == null || nc == null) {
                        continue
                    } else {
                        val pa = va.point
                        val pb = vb.point
                        val pc = vc.point
                        val a = Point3F(pa.x, pa.y, na.height)
                        val b = Point3F(pb.x, pb.y, nb.height)
                        val c = Point3F(pc.x, pc.y, nc.height)
                        val cross = (b - a).cross(c - a)
                        if (cross.c < 0) {
                            renderTriangle(a, b, c, heightMap)
                        } else {
                            renderTriangle(a, c, b, heightMap)
                        }
                    }
                }
            })
        }
        futures.forEach(Future<*>::join)
    }

    fun renderTrianglesAndUplift(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMap: Matrix<Float>, upliftMap: Matrix<Float>, threadCount: Int) {
        val futures = ArrayList<Future<*>>(threadCount)
        val triangles = graph.triangles
        for (i in 0..threadCount - 1) {
            futures.add(executor.submit {
                for (t in i..triangles.size - 1 step threadCount) {
                    val triangle = triangles[t]
                    val va = triangle.a
                    val vb = triangle.b
                    val vc = triangle.c
                    val na = nodeIndex[va.id]
                    val nb = nodeIndex[vb.id]
                    val nc = nodeIndex[vc.id]
                    if (na == null || nb == null || nc == null) {
                        continue
                    } else {
                        val pa = va.point
                        val pb = vb.point
                        val pc = vc.point
                        val ah = Point3F(pa.x, pa.y, na.height)
                        val bh = Point3F(pb.x, pb.y, nb.height)
                        val ch = Point3F(pc.x, pc.y, nc.height)
                        val au = Point3F(pa.x, pa.y, na.uplift)
                        val bu = Point3F(pb.x, pb.y, nb.uplift)
                        val cu = Point3F(pc.x, pc.y, nc.uplift)
                        val cross = (bh - ah).cross(ch - ah)
                        if (cross.c < 0) {
                            renderTriangle(ah, bh, ch, heightMap)
                            renderTriangle(au, bu, cu, upliftMap)
                        } else {
                            renderTriangle(ah, ch, bh, heightMap)
                            renderTriangle(au, cu, bu, upliftMap)
                        }
                    }
                }
            })
        }
        futures.forEach(Future<*>::join)
    }

    fun writeHeightMap(heightMap: Matrix<Float>): BufferedImage {
        val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
        val raster = output.raster
        var max = 0.0f
        for (i in 0..heightMap.size.toInt() - 1) {
            val height = heightMap[i]
            if (height > max) {
                max = height
            }
        }
        for (y in (0..heightMap.width - 1)) {
            for (x in (0..heightMap.width - 1)) {
                val heightValue = heightMap[x, y]
                if (heightValue < 0.0f) {
                    raster.setSample(x, y, 0, 0)
                } else {
                    val sample = Math.round((heightValue / max) * 55535.0f) + 9999
                    raster.setSample(x, y, 0, sample)
                }
            }
        }
        return output
    }

    fun writeNoise(heightMap: Matrix<Float>): BufferedImage {
        val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
        val raster = output.raster
        for (y in (0..heightMap.width - 1)) {
            for (x in (0..heightMap.width - 1)) {
                val heightValue = noise(x.toFloat() / 32.0f, y.toFloat() / 32.0f, 0.0f)
                val sample = Math.round(((heightValue + 1) / 2.0f) * 55535.0f) + 9999
                raster.setSample(x, y, 0, sample)
            }
        }
        return output
    }
}