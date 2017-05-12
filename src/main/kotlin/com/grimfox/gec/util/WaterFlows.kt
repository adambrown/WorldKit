package com.grimfox.gec.util

import com.grimfox.gec.biomes.Biomes
import com.grimfox.gec.biomes.Biomes.Biome
import com.grimfox.gec.biomes.Biomes.ErosionLevel
import com.grimfox.gec.biomes.Biomes.ErosionSettings
import com.grimfox.gec.biomes.Biomes.RegionData
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

    private val DISTANCE_SCALE = 50000.0f
    private val SIMPLEX_SCALE = 96.0f
    private val threadCount = Runtime.getRuntime().availableProcessors()

    class WaterNode(val id: Int,
                    val isExternal: Boolean,
                    val area: Float,
                    val adjacents: ArrayList<Pair<WaterNode, Float>>,
                    val simplexX: Float,
                    val simplexY: Float,
                    var uplift: Float,
                    var height: Float,
                    var drainageArea: Float,
                    var biome: Int) {

        var lake: Int = -1
        var parent: WaterNode = this
        var distanceToParent: Float = 0.0f
        val children: MutableCollection<WaterNode> = LinkedList()
    }

    data class PassKey(val lake1: Int, val lake2: Int)

    class Pass(val passKey: PassKey, val id1: Int, val id2: Int, val height: Float)

    fun generateWaterFlows(random: Random, inputGraph: Graph, inputMask: Matrix<Byte>, flowGraphSmall: Graph, flowGraphMedium: Graph, flowGraphLarge: Graph, executor: ExecutorService, outputWidth: Int): BufferedImage {
        val randomSeed = random.nextLong()
        val biomes = arrayListOf(Biomes.COASTAL_MOUNTAINS_BIOME, Biomes.ROLLING_HILLS_BIOME)
        val smallMapsFuture = executor.call {
            val regionData = buildRegionData(executor, flowGraphSmall, inputGraph, inputMask)
            val biomeMask = calculateBiomes(executor, flowGraphSmall, regionData, biomes)
            val (nodeIndex, nodes, rivers) = bootstrapErosion(executor, flowGraphSmall, regionData, biomes, biomeMask, DISTANCE_SCALE, Random(randomSeed))
            performErosion(executor, flowGraphSmall, nodeIndex, nodes, rivers, 50, biomes.map { it.erosionLowSettings }, 1024, 512)
        }
        val midNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphMedium, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val highNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphLarge, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val midMapsFuture = executor.call {
            val (heightMap, upliftMap, biomeMap) = smallMapsFuture.value
            val (nodeIndex, nodes, rivers) = midNodesFuture.value
            val erosionSettings = biomes.map { it.erosionMidSettings }
            applyMapsToNodes(executor, flowGraphMedium.vertices, heightMap, upliftMap!!, biomes, erosionSettings, biomeMap!!, nodes)
            performErosion(executor, flowGraphMedium, nodeIndex, nodes, rivers, 25, erosionSettings, 2048, 1024)
        }
        val highMapsFuture = executor.call {
            val (heightMap, upliftMap, biomeMap) = midMapsFuture.value
            val (nodeIndex, nodes, rivers) = highNodesFuture.value
            val erosionSettings = biomes.map { it.erosionHighSettings }
            applyMapsToNodes(executor, flowGraphLarge.vertices, heightMap, upliftMap!!, biomes, erosionSettings, biomeMap!!, nodes)
            performErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, 5, erosionSettings, outputWidth)
        }
        return writeHeightMap(highMapsFuture.value.first)
//        return writeNoise2(performHighErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, outputWidth))
//        return writeNoise3(executor, smallMapsFuture.value.first)
    }

    private fun calculateBiomes(executor: ExecutorService, graph: Graph, regionData: RegionData, biomes: List<Biome>): Matrix<Byte> {
        val biomeMap = ByteArrayMatrix(graph.stride!!) { -128 }
        val biomeFutures = regionData.regions.mapIndexed { i, region ->
            val biome = (i % biomes.size).toByte()
            executor.call {
                region.forEach { biomeMap[it] = biome }
            }
        }
        biomeFutures.map { it.join() }
        return biomeMap
    }

    private fun buildRegionData(executor: ExecutorService, graph: Graph, inputGraph: Graph, inputMask: Matrix<Byte>): RegionData {
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
        val borderPairs = LinkedHashMap<Int, Pair<Int, Int>>()
        regionBorders.forEachIndexed { i, regionBorder ->
            regionBorder.forEach { id ->
                if (!borderPairs.containsKey(id)) {
                    var isBorderPair = false
                    var mate = -1
                    val adjacentsToPair = ArrayList<Int>()
                    vertices.getAdjacentVertices(id).forEach { adjacentId ->
                        for ((j, otherRegionBorder) in regionBorders.withIndex()) {
                            if (i != j && otherRegionBorder.contains(adjacentId)) {
                                if (isBorderPair && mate != j) {
                                    isBorderPair = false
                                    break
                                } else {
                                    isBorderPair = true
                                    mate = j
                                    adjacentsToPair.add(adjacentId)
                                }
                            }
                        }
                    }
                    if (isBorderPair) {
                        val pair = Pair(Math.min(i, mate), Math.max(i, mate))
                        borderPairs[id] = pair
                        adjacentsToPair.forEach { adjacentId ->
                            borderPairs[adjacentId] = pair
                        }
                    }
                }
            }
        }
        regionBeaches.forEachIndexed { i, regionBeach ->
            regionBeach.removeAll(regionBorders[i])
        }
        return RegionData(land, water, beach, regions, regionBeaches, regionBorders, borderPairs)
    }

    private fun bootstrapErosion(executor: ExecutorService, graph: Graph, regionData: RegionData, biomes: List<Biome>, biomeMask: Matrix<Byte>, distanceScale: Float, random: Random): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        val vertices = graph.vertices
        val land = regionData.land
        val beach = regionData.beach
        val regions = regionData.regions
        val regionBeaches = regionData.regionBeaches
        val regionBorders = regionData.regionBorders
        val biomeUpliftMapFutures = biomes.map { biome ->
            executor.call {
                val upliftMap = ByteArrayMatrix(graph.stride!!) { -128 }
                val upliftMapFutures = regions.mapIndexed { i, region ->
                    executor.call {
                        biome.upliftFunction.buildUpliftMap(vertices, region, regionBeaches[i], regionBorders[i], upliftMap, regionData, biomeMask)
                    }
                }
                upliftMapFutures.forEach { it.join() }
                upliftMap
            }
        }
        val biomeUpliftMaps: List<ByteArrayMatrix> = biomeUpliftMapFutures.map { it.value }
        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, land, beach, biomes, biomeMask, biomeUpliftMaps, distanceScale)
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
        val biomeBootstrapSettings = biomes.map { it.bootstrapSettings }
        computeHeights(executor, rivers, biomeBootstrapSettings)
        return Triple(nodeIndex, nodes, rivers)
    }

    private fun performErosion(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>, iterations: Int, erosionLevels: List<ErosionLevel>, heightMapWidth: Int, intermediateMapWidth: Int? = null): Triple<FloatArrayMatrix, FloatArrayMatrix?, ByteArrayMatrix?> {
        val biomeSettings = erosionLevels.map { erosionLevel ->
            val erosionSettingsRaw = erosionLevel.erosionSettings.flatMapTo(ArrayList()) {
                val expanded = ArrayList<ErosionSettings>(it.iterations)
                for (i in 1..it.iterations) {
                    expanded.add(it)
                }
                expanded
            }
            if (erosionSettingsRaw.size > iterations) {
                erosionSettingsRaw.subList(0, iterations)
            } else if (erosionSettingsRaw.size < iterations) {
                val last = erosionSettingsRaw.last()
                erosionSettingsRaw + (1..iterations - erosionSettingsRaw.size).map { last }
            } else {
                erosionSettingsRaw
            }
        }
        val lakes = ArrayList<WaterNode>()
        val passes = LinkedHashMap<PassKey, Pass>()
        for (i in 0..iterations - 1) {
            lakes.clear()
            passes.clear()
            prepareNodesAndLakes(executor, lakes, nodes, rivers)
            computeLakeConnections(graph.vertices, lakes, nodeIndex, passes, rivers)
            computeAreas(executor, rivers)
            computeHeights(executor, rivers, biomeSettings.map { it[i] })
        }
        val heightMap = FloatArrayMatrix(heightMapWidth) { -1.0f }
        if (intermediateMapWidth != null) {
            val upliftMap = FloatArrayMatrix(intermediateMapWidth) { 0.0f }
            val biomeMap = ByteArrayMatrix(intermediateMapWidth) { 0 }
            renderAllMaps(executor, graph, nodeIndex, heightMap, upliftMap, biomeMap, threadCount)
            return Triple(heightMap, upliftMap, biomeMap)
        } else {
            renderHeightmap(executor, graph, nodeIndex, heightMap, threadCount)
            return Triple(heightMap, null, null)
        }
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

    private fun prepareNodesAndLakes(executor: ExecutorService, lakes: ArrayList<WaterNode>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>) {
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

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: List<Int>, riverMouths: LinkedHashSet<Int>, biomes: List<Biome>, biomeMask: Matrix<Byte>, biomeUpliftMasks: List<ByteArrayMatrix>, distanceScale: Float): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..land.size - 1 step threadCount) {
                    val landId = land[id]
                    val biomeId = biomeMask[landId].toInt()
                    val biome = biomes[biomeId]
                    val upliftMap = biomeUpliftMasks[biomeId]
                    val tempLift = upliftMap[landId].toInt() + 128
                    val uplift: Float = if (tempLift == 0) {
                        0.0f
                    } else {
                        (tempLift / 256.0f) * biome.deltaUplift + biome.minUplift
                    }
                    val isExternal = riverMouths.contains(landId)
                    val area = vertices.getArea(landId) * areaScale
                    val point = vertices.getPoint(landId)
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, uplift, 0.0f, area, biomeId)
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
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, 0.0f, 0.0f, area, 0)
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

    private fun applyMapsToNodes(executor: ExecutorService, vertices: Vertices, heightMap: Matrix<Float>, upliftMap: Matrix<Float>, biomes: List<Biome>, erosionSettings: List<ErosionLevel>, biomeMap: Matrix<Byte>, nodes: ArrayList<WaterNode>) {
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
                        val biomeId = biomeMap[uIndex].toInt()
                        node.biome = biomeId
                        node.uplift = Math.max(biomes[biomeId].minUplift, uplift * erosionSettings[biomeId].upliftMultiplier)
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

    private fun computeHeights(executor: ExecutorService, rivers: ArrayList<WaterNode>, erosionSettings: List<ErosionSettings>) {
        val heightFutures = rivers.map { river ->
            executor.call {
                recurseHeights(river, erosionSettings)
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
        node.adjacents.forEach { (otherNode) ->
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

    private fun recurseHeights(node: WaterNode, erosionSettings: List<ErosionSettings>) {
        if (node.isExternal) {
            node.children.forEach { recurseHeights(it, erosionSettings) }
        } else {
            val settings = erosionSettings[node.biome]
            val parent = node.parent
            val parentHeight = parent.height
            val flow = settings.erosionPower * Math.pow(node.drainageArea.toDouble(), 0.5)
            val erosion = flow / node.distanceToParent
            val denominator = 1.0 + (erosion * settings.deltaTime)
            val numerator = node.height + (settings.deltaTime * (node.uplift + (erosion * parentHeight)))
            node.height = (numerator / denominator).toFloat()
            val variance = noise(node.simplexX, node.simplexY, node.height / 10.0f)
            val talus = settings.talusAngles[(Math.min(1023, Math.max(0, Math.round(node.height * settings.heightMultiplier))) * 256) + Math.round((variance + 1) * 128)]
            if ((node.height - parent.height) / node.distanceToParent > talus) {
                node.height = (node.distanceToParent * talus) + parent.height
            }
            node.children.forEach { recurseHeights(it, erosionSettings) }
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

    fun renderHeightmap(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMap: Matrix<Float>, threadCount: Int) {
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
            }
        }
        futures.forEach(Future<*>::join)
    }

    fun renderAllMaps(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMap: Matrix<Float>, upliftMap: Matrix<Float>, biomeMap: Matrix<Byte>, threadCount: Int) {
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
                        val biome = if (nb.biome == nc.biome) {
                            nb.biome.toByte()
                        } else {
                            na.biome.toByte()
                        }
                        if (cross.c < 0) {
                            renderTriangle(ah, bh, ch, heightMap)
                            renderTriangle(au, bu, cu, upliftMap)
                            renderTriangle(pa, pb, pc, biome, biomeMap)
                        } else {
                            renderTriangle(ah, ch, bh, heightMap)
                            renderTriangle(au, cu, bu, upliftMap)
                            renderTriangle(pa, pc, pb, biome, biomeMap)
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
                    val magnitude = octaves[i]
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