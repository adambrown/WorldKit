package com.grimfox.gec.util

import com.grimfox.gec.extensions.*
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.geometry.renderTriangle
import org.joml.SimplexNoise.*
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

object WaterFlows {

    private val noiseGraph64 = Graphs.generateGraph(64, Random(123), 0.98)
    private val noisePoints64 = arrayOfNulls<Point3F>(noiseGraph64.vertices.size)

    init {
        val vertices = noiseGraph64.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints64[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 31, point.y * 31)) / 64.0f)
        }
    }

    private val MIN_U = 0.000004f
    private val MAX_U = 0.0005f
    private val DELTA_U = MAX_U - MIN_U
    private val K = 0.000000561f
    private val M = 0.5
    private val DT = 250000.0f
    private val DISTANCE_SCALE = 50000.0f
    private val SIMPLEX_SCALE = 96.0f

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

    private val SCALE = 30000.0
    private val SD = 270.0
    private val MEAN =  512.0
    private val T0 = -2.0 * SD * SD
    private val T1 = SCALE * (1.0 / Math.sqrt(Math.PI * -T0))
    private val VARIANCE_FACTOR_ROLLING_HILLS = 0.05f
    private val HEIGHT_VARIANCE_ROLLING_HILLS = FloatArray(1024 * 256) { i ->
        val height = i / 256
        val variance = i % 256
        val base = T1 * Math.pow(Math.E, (((height - MEAN) * (height - MEAN)) / T0))
        val baseVariance = (base * VARIANCE_FACTOR_ROLLING_HILLS)
        val varianceMod = ((variance / 255.0) * baseVariance * 2.0) - baseVariance
        val angle = Math.toRadians(base + varianceMod)
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
        val randomSeed1 = random.nextLong()
        val randomSeed2 = random.nextLong()
        val smallMapsFutureCoastalMountains = executor.call {
            val (nodeIndex, nodes, rivers) = bootstrapErosion(executor, flowGraphSmall, inputGraph, inputMask, DISTANCE_SCALE, Random(randomSeed1), DELTA_U, MIN_U, HEIGHT_VARIANCE_FIXED, 1.0f, K, calculateUpliftCoastalMountains)
            solveInitialLakes(executor, flowGraphSmall, nodeIndex, nodes, rivers, HEIGHT_VARIANCE_LOW, 1.0f, K)
        }
        val smallMapsFutureRollingHills = executor.call {
            val (nodeIndex, nodes, rivers) = bootstrapErosion(executor, flowGraphSmall, inputGraph, inputMask, DISTANCE_SCALE, Random(randomSeed2), DELTA_U * 0.15f, MIN_U * 0.15f, HEIGHT_VARIANCE_FIXED, 1.0f, K, calculateUpliftRollingHills)
            solveInitialLakes(executor, flowGraphSmall, nodeIndex, nodes, rivers, HEIGHT_VARIANCE_ROLLING_HILLS, 11.0f, K * 8.0f)
        }
        val midNodesFutureCoastalMountains = executor.call {
            prepareGraphNodes(executor, flowGraphMedium, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val midNodesFutureRollingHills = executor.call {
            prepareGraphNodes(executor, flowGraphMedium, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val highNodesFutureCoastalMountains = executor.call {
            prepareGraphNodes(executor, flowGraphLarge, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val highNodesFutureRollingHills = executor.call {
            prepareGraphNodes(executor, flowGraphLarge, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val midMapsFutureCoastalMountains = executor.call {
            val (heightMap, upliftMap) = smallMapsFutureCoastalMountains.value
            val (nodeIndex, nodes, rivers) = midNodesFutureCoastalMountains.value
            applyMapsToNodes(executor, flowGraphMedium.vertices, heightMap, upliftMap, nodes, 0.5f)
            performMidErosion(executor, flowGraphMedium, nodeIndex, nodes, rivers, HEIGHT_VARIANCE_MID, 1.0f, K)
        }
        val midMapsFutureRollingHills = executor.call {
            val (heightMap, upliftMap) = smallMapsFutureRollingHills.value
            val (nodeIndex, nodes, rivers) = midNodesFutureRollingHills.value
            applyMapsToNodes(executor, flowGraphMedium.vertices, heightMap, upliftMap, nodes, 0.4f)
            performMidErosion(executor, flowGraphMedium, nodeIndex, nodes, rivers, HEIGHT_VARIANCE_ROLLING_HILLS, 11.0f, K * 4.0f)
        }
        val highMapsFutureCoastalMountains = executor.call {
            val (heightMap, upliftMap) = midMapsFutureCoastalMountains.value
            val (nodeIndex, nodes, rivers) = highNodesFutureCoastalMountains.value
            applyMapsToNodes(executor, flowGraphLarge.vertices, heightMap, upliftMap, nodes, 0.4f)
            performHighErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, HEIGHT_VARIANCE_HIGH, HEIGHT_VARIANCE_FIXED, 1.0f, K, outputWidth)
        }
        val highMapsFutureRollingHills = executor.call {
            val (heightMap, upliftMap) = midMapsFutureRollingHills.value
            val (nodeIndex, nodes, rivers) = highNodesFutureRollingHills.value
            applyMapsToNodes(executor, flowGraphLarge.vertices, heightMap, upliftMap, nodes, 0.4f)
            performHighErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, HEIGHT_VARIANCE_ROLLING_HILLS, HEIGHT_VARIANCE_ROLLING_HILLS, 11.0f, K * 2.5f, outputWidth)
        }
//        return writeHeightMap(highMapsFutureRollingHills.value)
        return writeDualMap(highMapsFutureCoastalMountains.value, highMapsFutureRollingHills.value)
//        return writeNoise(performHighErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, outputWidth))
//        return writeNoise2(performHighErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, outputWidth))
//        return writeNoise3(executor, smallMapsFuture.value.first)
    }

    private fun bootstrapErosion(executor: ExecutorService, graph: Graph, inputGraph: Graph, inputMask: Matrix<Byte>, distanceScale: Float, random: Random, deltaUplift: Float, minUplift: Float, varianceTable: FloatArray, heightMultiplier: Float, k: Float, upliftFunction: (Vertices, LinkedHashSet<Int>, LinkedHashSet<Int>, LinkedHashSet<Int>, Matrix<Byte>) -> Unit): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        val vertices = graph.vertices
        val land = ArrayList<Int>(vertices.size)
        val water = LinkedHashSet<Int>(vertices.size)
        val regions = ArrayList<LinkedHashSet<Int>>(16)
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
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
        val regionBorderFutures = regions.map { region ->
            executor.call {
                LinkedHashSet(region.filter { isBorderPoint(vertices, water, region, it) })
            }
        }
        val regionBeaches = regionBeachFutures.map { it.value }
        val regionBorders = regionBorderFutures.map { it.value }
        regionBeaches.forEachIndexed { i, regionBeach ->
            regionBeach.removeAll(regionBorders[i])
        }
        val upliftMap = ByteArrayMatrix(graph.stride!!) { -128 }
        val upliftMapFutures = regions.mapIndexed { i, region ->
            executor.call {
                upliftFunction(vertices, region, regionBeaches[i], regionBorders[i], upliftMap)
            }
        }
        upliftMapFutures.forEach { it.join() }

        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, land, beach, upliftMap, distanceScale, deltaUplift, minUplift)
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
                node.adjacents.forEach { (otherNode, second) ->
                    if (!used.contains(otherNode.id)) {
                        otherNode.parent.children.remove(otherNode)
                        otherNode.parent = node
                        otherNode.distanceToParent = second
                        node.children.add(otherNode)
                        next.add(otherNode.id)
                        used.add(otherNode.id)
                        unused.remove(otherNode.id)
                    }
                }
            }
        }
        computeAreas(executor, rivers)
        computeHeights(executor, DT * 0.34f, rivers, varianceTable, heightMultiplier, k)
        return Triple(nodeIndex, nodes, rivers)
    }

    private fun solveInitialLakes(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>, varianceTable: FloatArray, heightMultiplier: Float, k: Float): Pair<FloatArrayMatrix, FloatArrayMatrix> {
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
            computeHeights(executor, deltaTime, rivers, varianceTable, heightMultiplier, k)
        }
        val heightMap = FloatArrayMatrix(1024) { -1.0f }
        val upliftMap = FloatArrayMatrix(512) { 0.0f }
        renderTrianglesAndUplift(executor, graph, nodeIndex, heightMap, upliftMap, threadCount)
        return Pair(heightMap, upliftMap)
    }

    private fun performMidErosion(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>, varianceTable: FloatArray, heightMultiplier: Float, k: Float): Pair<FloatArrayMatrix, FloatArrayMatrix> {
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
            computeHeights(executor, deltaTime, rivers, varianceTable, heightMultiplier, k)
        }
        val heightMap = FloatArrayMatrix(2048) { -1.0f }
        val upliftMap = FloatArrayMatrix(1024) { 0.0f }
        renderTrianglesAndUplift(executor, graph, nodeIndex, heightMap, upliftMap, threadCount)
        return Pair(heightMap, upliftMap)
    }

    private fun performHighErosion(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>, varianceTable1: FloatArray, varianceTable2: FloatArray, heightMultiplier: Float, k: Float, outputWidth: Int): Matrix<Float> {
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
                computeHeights(executor, deltaTime, rivers, varianceTable1, heightMultiplier, k)
            } else {
                computeHeights(executor, deltaTime, rivers, varianceTable2, heightMultiplier, k)
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
            val point = vertices.getPoint(i)
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

    private fun computeLakeConnections(vertices: Vertices, lakes: ArrayList<WaterNode>, nodeIndex: Array<WaterNode?>, passes: LinkedHashMap<PassKey, Pass>, rivers: ArrayList<WaterNode>) {
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
                    childNode.distanceToParent = vertices.getPoint(childNode.id).distance(vertices.getPoint(parentNode.id))
                    break
                }
            }
        }
    }

    private fun prepareNodesAndLakes(executor: ExecutorService, lakes: ArrayList<WaterNode>, localLakePool: Array<ArrayList<WaterNode>>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>) {
        for (id in 0..nodes.size - 1) {
            val node = nodes[id]
            node.lake = -1
            if (!node.isExternal) {
                var minHeight = node.height
                var minNode = node
                var distToMin = 0.0f
                node.adjacents.forEach { (otherNode, dist) ->
                    if (otherNode.height < minHeight) {
                        minNode = otherNode
                        distToMin = dist
                        minHeight = otherNode.height
                    }
                }
                if (minNode != node.parent) {
                    node.parent.children.remove(node)
                    node.parent = minNode
                    node.distanceToParent = distToMin
                    if (minNode != node) {
                        minNode.children.add(node)
                    }
                }
                if (minNode == node) {
                    lakes.add(node)
                }
            }
        }
        val futures = ArrayList<Future<*>>()
        rivers.forEachIndexed { id, waterNode ->
            futures.add(executor.call { recurseSetLake(id, waterNode) })
        }
        lakes.forEachIndexed { id, waterNode ->
            futures.add(executor.call { recurseSetLake(id + rivers.size, waterNode) })
        }
        futures.forEach { it.join() }
    }

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: ArrayList<Int>, riverMouths: LinkedHashSet<Int>, upliftMap: ByteArrayMatrix, distanceScale: Float, deltaUplift: Float, minUplift: Float): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
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
                        (tempLift / 256.0f) * deltaUplift + minUplift
                    }
                    val isExternal = riverMouths.contains(landId)
                    val area = vertices.getArea(landId) * areaScale
                    val point = vertices.getPoint(landId)
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, uplift, 0.0f, area)
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
                    val position = vertices.getPoint(node.id)
                    vertices.getAdjacentVertices(node.id).forEach { adjacent ->
                        val otherNode = nodeIndex[adjacent]
                        if (otherNode != null) {
                            node.adjacents.add(Pair(otherNode, position.distance(vertices.getPoint(adjacent)) * distanceScale))
                        }
                    }
                }
            }
        }
        nodeFutures2.forEach { it.join() }
        return Pair(nodeIndex, nodes)
    }

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: ArrayList<Int>, riverMouths: LinkedHashSet<Int>, distanceScale: Float): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..land.size - 1 step threadCount) {
                    val landId = land[id]
                    val isExternal = riverMouths.contains(landId)
                    val area = vertices.getArea(landId) * areaScale
                    val point = vertices.getPoint(landId)
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, if (isExternal) 0.0f else MIN_U, 0.0f, area)
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
                    val position = vertices.getPoint(node.id)
                    vertices.getAdjacentVertices(node.id).forEach { adjacent ->
                        val otherNode = nodeIndex[adjacent]
                        if (otherNode != null) {
                            node.adjacents.add(Pair(otherNode, position.distance(vertices.getPoint(adjacent)) * distanceScale))
                        }
                    }
                }
            }
        }
        nodeFutures2.forEach { it.join() }
        return Pair(nodeIndex, nodes)
    }

    private fun applyMapsToNodes(executor: ExecutorService, vertices: Vertices, heightMap: Matrix<Float>, upliftMap: Matrix<Float>, nodes: ArrayList<WaterNode>, upliftMultiplier: Float) {
        val hWidth = heightMap.width
        val hWidthM1 = hWidth - 1
        val uWidth = upliftMap.width
        val uWidthM1 = uWidth - 1
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..nodes.size - 1 step threadCount) {
                    val node = nodes[id]
                    if (!node.isExternal) {
                        val point = vertices.getPoint(node.id)
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

    private fun computeHeights(executor: ExecutorService, deltaTime: Float, rivers: ArrayList<WaterNode>, varianceTable: FloatArray, heightMultiplier: Float, k: Float) {
        val heightFutures = rivers.map { river ->
            executor.call {
                recurseHeights(river, deltaTime, varianceTable, heightMultiplier, k)
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

    private fun recurseHeights(node: WaterNode, deltaTime: Float, varianceTable: FloatArray, heightMultiplier: Float, k: Float) {
        if (node.isExternal) {
            node.children.forEach { recurseHeights(it, deltaTime, varianceTable, heightMultiplier, k) }
        } else {
            val parent = node.parent
            val parentHeight = parent.height
            val flow = k * Math.pow(node.drainageArea.toDouble(), M)
            val erosion = flow / node.distanceToParent
            val denominator = 1.0 + (erosion * deltaTime)
            val numerator = node.height + (deltaTime * (node.uplift + (erosion * parentHeight)))
            node.height = (numerator / denominator).toFloat()
            val variance = noise(node.simplexX, node.simplexY, node.height / 10.0f)
            val talus = varianceTable[(Math.min(1023, Math.max(0, Math.round(node.height * heightMultiplier))) * 256) + Math.round((variance + 1) * 128)]
            if ((node.height - parent.height) / node.distanceToParent > talus) {
                node.height = (node.distanceToParent * talus) + parent.height
            }
            node.children.forEach { recurseHeights(it, deltaTime, varianceTable, heightMultiplier, k) }
        }
    }

    private val calculateUplift = { vertices: Vertices, region: LinkedHashSet<Int>, beach: LinkedHashSet<Int>, border: LinkedHashSet<Int>, upliftMap: Matrix<Byte> ->
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

    private val calculateUpliftCoastalMountains = { vertices: Vertices, region: LinkedHashSet<Int>, beach: LinkedHashSet<Int>, border: LinkedHashSet<Int>, upliftMap: Matrix<Byte> ->
        val remaining = HashSet(region)
        var currentIds = HashSet(border)
        var nextIds = HashSet<Int>(border.size)
        var currentUplift = (-40).toByte()
        for (i in 0..5) {
            currentIds.forEachIndexed { even, it ->
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = (currentUplift + if (even % 2 == 0) 0 else 80).toByte()
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
            if (i == 0) {
                currentUplift = 60
            }
            if (i == 2) {
                currentUplift = (currentUplift - 81).toByte()
            }
        }
        currentIds.clear()
        currentIds.addAll(beach)
        nextIds.clear()
        currentUplift = -126
        for (i in 0..9) {
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
            if (i == 1) {
                currentUplift = (currentUplift + 15).toByte()
            }
        }
        currentUplift = (currentUplift + 25).toByte()
        remaining.forEach { upliftMap[it] = currentUplift }
    }

    private val calculateUpliftRollingHills = { vertices: Vertices, region: LinkedHashSet<Int>, _: LinkedHashSet<Int>, _: LinkedHashSet<Int>, upliftMap: Matrix<Byte> ->
        var max = -Float.MAX_VALUE
        var min = Float.MAX_VALUE
        val rawUplifts = FloatArray(vertices.size) { i ->
            if (region.contains(i)) {
                val point = vertices.getPoint(i)
                val point3d = Point3F(point.x, point.y, 0.0f)
                val closePoints = noiseGraph64.getClosePoints(point, 3).map {
                    point3d.distance2(noisePoints64[it]!!)
                }.sorted()
                val height = -closePoints[0]
                if (height > max) {
                    max = height
                }
                if (height < min) {
                    min = height
                }
                height
            } else {
                -1.0f
            }
        }
        val delta = max - min
        for (i in (0..upliftMap.size.toInt() - 1)) {
            if (region.contains(i)) {
                val height = (rawUplifts[i] - min) / delta
                upliftMap[i] = (Math.round(height * 253.0f) - 126).toByte()
            }
        }
    }

    private fun extractBeachFromGraphAndWater(vertices: Vertices, water: LinkedHashSet<Int>) =
            (0..vertices.size - 1).asSequence().filterTo(LinkedHashSet<Int>()) { isCoastalPoint(vertices, water, it) }

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

    private fun isBorderPoint(vertices: Vertices, water: Set<Int>, region: Set<Int>, vertexId: Int): Boolean {
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
        (0..threadCount - 1).mapTo(futures) {
            executor.submit {
                for (t in it..triangles.size - 1 step threadCount) {
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
            }
        }
        futures.forEach(Future<*>::join)
    }

    fun writeHeightMap(heightMap: Matrix<Float>): BufferedImage {
        val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
        val raster = output.raster
        val maxLandValue = (0..heightMap.size.toInt() - 1).asSequence().map { heightMap[it] }.max() ?: 0.0f
        println("maxLandValue = $maxLandValue")
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0..heightMap.width - 1)) {
            for (x in (0..heightMap.width - 1)) {
                val heightValue = heightMap[x, y]
                if (heightValue < 0.0f) {
                    raster.setSample(x, y, 0, 0)
                } else {
                    val sample = (((heightValue * landFactor) + waterLine) * 65535).toInt()
                    raster.setSample(x, y, 0, sample)
                }
            }
        }
        return output
    }

    fun writeDualMap(mountainMap: Matrix<Float>, hillMap: Matrix<Float>): BufferedImage {
        val output = BufferedImage(mountainMap.width, mountainMap.width, BufferedImage.TYPE_USHORT_GRAY)
        val raster = output.raster
        val maxLandValue = (0..mountainMap.size.toInt() - 1).asSequence().map { mountainMap[it] }.max() ?: 0.0f
        println("maxLandValue = $maxLandValue")
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0..mountainMap.width - 1)) {
            for (x in (0..mountainMap.width - 1)) {
                val heightValue = mountainMap[x, y]
                if (heightValue < 0.0f) {
                    raster.setSample(x, y, 0, 0)
                } else {
                    val heightValue2 = hillMap[x, y]
                    var noise = noise(x / 512.0f, y / 512.0f)
                    val sign = Math.signum(noise)
                    noise = 1 - Math.abs(noise)
                    noise *= noise * noise * noise
                    noise = 1 - noise
                    noise *= sign
                    val weight = (noise + 1.0f) / 2.0f
                    val interpolated = (heightValue * weight) + (heightValue2 * (1.0f - weight))
                    val sample = (((interpolated * landFactor) + waterLine) * 65535).toInt()
                    raster.setSample(x, y, 0, sample)
                }
            }
        }
        return output
    }

    fun writeNoise2(heightMap: Matrix<Float>): BufferedImage {
//        val targetFirst = 0.001f
//        var seed = 3.1985903E-6f
//        var increment = 1.328289f
//        var last = seed
//        var current = seed
//        var keepGoing = true
//        var direction = false
//        while (keepGoing) {
//            for (i in 0..7) {
//                val next = (last + current) * increment
//                last = current
//                current = next
//            }
//            if (current < targetFirst) {
//                while (current < targetFirst) {
//                    seed = Math.nextUp(seed)
//                    last = seed
//                    current = seed
//                    for (i in 0..7) {
//                        val next = (last + current) * increment
//                        last = current
//                        current = next
//                    }
//                }
//                seed = Math.nextDown(seed)
//            } else if (current > targetFirst) {
//                while (current > targetFirst) {
//                    seed = Math.nextDown(seed)
//                    last = seed
//                    current = seed
//                    for (i in 0..7) {
//                        val next = (last + current) * increment
//                        last = current
//                        current = next
//                    }
//                }
//            }
//            last = seed
//            current = seed
//            for (i in 0..7) {
//                println("pre-seed${i + 1}: $current")
//                val next = (last + current) * increment
//                last = current
//                current = next
//            }
//            println("divisor: $increment, seed1: $last, seed2: $current")
//            current = targetFirst
//            val octaveArray = FloatArray(10)
//            octaveArray[0] = current
//            for (i in 1..9) {
//                val next = (last + current) * increment
//                last = current
//                current = next
//                octaveArray[i] = current
//            }
//            val sum = octaveArray.sum()
//            println(octaveArray.toList().reversed())
//            println(sum)
//            if (sum > 1.0f) {
//                direction = true
//                increment = Math.nextDown(increment)
//            } else if (sum < 1.0f) {
//                if (direction) {
//                    keepGoing = false
//                } else {
//                    direction = false
//                    increment = Math.nextUp(increment)
//                }
//            }
//        }

//        val octaves = floatArrayOf(0.062919f, 0.125481f, 0.499078f, 0.250249f, 0.031549f, 0.015819f, 0.007932f, 0.003978f, 0.001995f, 0.001f)
//        val octaves = floatArrayOf(0.499078f, 0.250249f, 0.125481f, 0.062919f, 0.031549f, 0.015819f, 0.007932f, 0.003978f, 0.001995f, 0.001f)
        val octaves = floatArrayOf(0.3f, 0.25f, 0.2f, 0.15f, 0.03f, 0.025f, 0.02f, 0.015f, 0.006f, 0.004f)
//        println(octaves.sum())
        val divisors = floatArrayOf(127.0f, 67.0f, 257.0f, 509.0f, 31.0f, 17.0f, 7.0f, 5.0f, 2.0f, 1.0f)
        val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
        val raster = output.raster
        for (y in (0..heightMap.width - 1)) {
            for (x in (0..heightMap.width - 1)) {
                var sum = 0.0f
                for (i in 0..7) {
                    val magnitude = octaves[i].toFloat()
                    val divisor = divisors[i]
                    sum += ((noise(x.toFloat() / divisor, y.toFloat() / divisor, 0.0f) + 1) / 2.0f) * magnitude
                }
                val sample = Math.round(sum * 55535.0f) + 9999
                raster.setSample(x, y, 0, sample)
            }
        }
        return output
    }

    fun writeNoise3(executor: ExecutorService, heightMap: Matrix<Float>): BufferedImage {
        return timeIt("generated noise in") {
            val widthF = heightMap.width.toFloat()
            val widthI = heightMap.width
            val futures = ArrayList<Future<Pair<Float, Float>>>(threadCount)
            (0..threadCount - 1).mapTo(futures) {
                executor.call {
                    var max = -Float.MAX_VALUE
                    var min = Float.MAX_VALUE
                    for (i in it..(heightMap.size - 1).toInt() step threadCount) {
                        val point = Point2F(((i % widthI) + 0.5f) / widthF, ((i / widthI) + 0.5f) / widthF)
                        val point3d = Point3F(point.x, point.y, 0.0f)
                        val closePoints = noiseGraph64.getClosePoints(point, 3).map {
                            point3d.distance2(noisePoints64[it]!!)
                        }.sorted()
                        val height = -closePoints[0]
                        if (height > max) {
                            max = height
                        }
                        if (height < min) {
                            min = height
                        }
                        heightMap[i] = height
                    }
                    Pair(min, max)
                }
            }
            val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
            val raster = output.raster
            val extremes = futures.map { it.value }
            val min = extremes.map { it.first }.min()!!
            val max = extremes.map { it.second }.max()!!
            val delta = max - min
            for (y in (0..heightMap.width - 1)) {
                for (x in (0..heightMap.width - 1)) {
                    val height = (heightMap[x, y] - min) / delta
                    val sample = Math.round(height * 65534.0f)
                    raster.setSample(x, y, 0, sample)
                }
            }
            output
        }
    }
}