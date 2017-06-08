package com.grimfox.gec.util

import com.grimfox.gec.biomes.Biomes
import com.grimfox.gec.biomes.Biomes.Biome
import com.grimfox.gec.biomes.Biomes.ErosionLevel
import com.grimfox.gec.biomes.Biomes.ErosionSettings
import com.grimfox.gec.biomes.Biomes.RegionData
import com.grimfox.gec.extensions.call
import com.grimfox.gec.extensions.join
import com.grimfox.gec.extensions.value
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Point3F
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.buildTextureRedShort
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRedByte
import com.grimfox.gec.ui.widgets.TextureBuilder.render
import com.grimfox.gec.util.Rendering.renderCoastalBorders
import com.grimfox.gec.util.Rendering.renderRegionBorders
import com.grimfox.gec.util.Rendering.renderRegions
import com.grimfox.gec.util.geometry.renderTriangle
import org.joml.SimplexNoise.noise
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

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

    fun generateWaterFlows(random: Random, inputGraph: Graph, inputMask: Matrix<Byte>, flowGraphSmall: Graph, flowGraphMedium: Graph, flowGraphLarge: Graph, regionTextureId: TextureId, executor: ExecutorService, outputWidth: Int): TextureId {
        val randomSeeds = Array(4) { random.nextLong() }
        val biomes = arrayListOf(Biomes.COASTAL_MOUNTAINS_BIOME, Biomes.MOUNTAINS_BIOME, Biomes.FOOTHILLS_BIOME, Biomes.ROLLING_HILLS_BIOME, Biomes.PLAINS_BIOME, Biomes.PLATEAU_BIOME)
//        val biomes = arrayListOf(Biomes.PLATEAU_BIOME)

        val biomeMasksFuture = executor.call {
            val regionBorderTextureId = renderRegionBorders(executor, inputGraph, inputMask, threadCount)

            val coastalBorderTextureId = renderCoastalBorders(executor, inputGraph, inputMask, threadCount)

            val biomeCount = biomes.size
            val biomeGraphSmallFuture = executor.call {
                val innerRandom = Random(randomSeeds[0])
                val graph = Graphs.generateGraph(14, innerRandom, 0.98)
                val mask = ByteArrayMatrix(graph.stride!!) { ((Math.abs(innerRandom.nextInt()) % biomeCount) + 1).toByte() }
                graph to mask
            }
            val biomeGraphMidFuture = executor.call {
                val innerRandom = Random(randomSeeds[1])
                val graph = Graphs.generateGraph(28, innerRandom, 0.98)
                val vertices = graph.vertices
                val (parentGraph, parentMask) = biomeGraphSmallFuture.value
                val mask = ByteArrayMatrix(graph.stride!!) { i ->
                    val point = vertices[i].point
                    parentMask[parentGraph.getClosestPoint(point, parentGraph.getClosePoints(point, 2))]
                }
                graph to mask
            }
            val biomeGraphHighFuture = executor.call {
                val innerRandom = Random(randomSeeds[2])
                val graph = Graphs.generateGraph(72, innerRandom, 0.88)
                val vertices = graph.vertices
                val (parentGraph, parentMask) = biomeGraphMidFuture.value
                val mask = ByteArrayMatrix(graph.stride!!) { i ->
                    val point = vertices[i].point
                    parentMask[parentGraph.getClosestPoint(point, parentGraph.getClosePoints(point, 2))]
                }
                graph to mask
            }
            val biomeGraphFinalFuture = executor.call {
                val graph = inputGraph
                val vertices = graph.vertices
                val (parentGraph, parentMask) = biomeGraphHighFuture.value
                val mask = ByteArrayMatrix(inputGraph.stride!!) { i ->
                    val vertex = vertices[i]
                    val point = vertex.point
                    if (inputMask[i] < 1 || vertex.cell.isBorder) {
                        0.toByte()
                    } else {
                        parentMask[parentGraph.getClosestPoint(point, parentGraph.getClosePoints(point, 2))]
                    }
                }
                graph to mask
            }
            val biomeTextureId = renderRegions(biomeGraphFinalFuture.value.first, biomeGraphFinalFuture.value.second)
            val biomeMask = ByteBufferMatrix(4096, extractTextureRedByte(biomeTextureId, 4096))

            val biomeBorderTextureId = renderRegionBorders(executor, biomeGraphFinalFuture.value.first, biomeGraphFinalFuture.value.second, threadCount)

            val upliftTextureId = render { _, dynamicGeometry2D, textureRenderer ->
                glDisable(GL11.GL_BLEND)
                glDisable(GL11.GL_CULL_FACE)
                glDisable(GL13.GL_MULTISAMPLE)
                glEnable(GL_DEPTH_TEST)
                glDisable(GL11.GL_SCISSOR_TEST)
                glDisable(GL13.GL_MULTISAMPLE)
                textureRenderer.bind()
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                val biomeRegions = buildUpliftTriangles(biomeGraphFinalFuture.value.first, biomeGraphFinalFuture.value.second)
                biomeRegions.forEachIndexed { i, (vertexData, indexData) ->
                    val biome = biomes[i]
                    biome.upliftShader.bind(regionTextureId, biomeTextureId, regionBorderTextureId, coastalBorderTextureId, biomeBorderTextureId)
                    dynamicGeometry2D.render(vertexData, indexData, biome.upliftShader.positionAttribute)
                }
                val retVal = textureRenderer.newRedTextureByte(GL_LINEAR, GL_LINEAR)
                textureRenderer.unbind()
                retVal
            }
            val upliftMask = ByteBufferMatrix(4096, extractTextureRedByte(upliftTextureId, 4096))

            regionBorderTextureId.free()
            coastalBorderTextureId.free()
            biomeTextureId.free()
            biomeBorderTextureId.free()
            upliftTextureId.free()
            biomeMask to upliftMask
        }

        val smallMapsFuture = executor.call {
            val regionData = buildRegionData(executor, flowGraphSmall, inputGraph, inputMask)
            val (nodeIndex, nodes, rivers) = bootstrapErosion(executor, flowGraphSmall, regionData, biomes, biomeMasksFuture.value.first, biomeMasksFuture.value.second, DISTANCE_SCALE, Random(randomSeeds[3]))
            performErosion(executor, flowGraphSmall, nodeIndex, nodes, rivers, 50, biomes.map { it.erosionLowSettings }, 1024)
        }
        val midNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphMedium, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val highNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphLarge, inputGraph, inputMask, DISTANCE_SCALE)
        }
        val midMapsFuture = executor.call {
            val heightMap = smallMapsFuture.value
            val (nodeIndex, nodes, rivers) = midNodesFuture.value
            val erosionSettings = biomes.map { it.erosionMidSettings }
            applyMapsToNodes(executor, flowGraphMedium.vertices, heightMap, biomeMasksFuture.value.second, biomes, erosionSettings, biomeMasksFuture.value.first, nodes)
            performErosion(executor, flowGraphMedium, nodeIndex, nodes, rivers, 25, erosionSettings, 2048)
        }
        val highMapsFuture = executor.call {
            val heightMap = midMapsFuture.value
            val (nodeIndex, nodes, rivers) = highNodesFuture.value
            val erosionSettings = biomes.map { it.erosionHighSettings }
            applyMapsToNodes(executor, flowGraphLarge.vertices, heightMap, biomeMasksFuture.value.second, biomes, erosionSettings, biomeMasksFuture.value.first, nodes)
            performErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, 5, erosionSettings, outputWidth)
        }
        return writeHeightMap(highMapsFuture.value)
//        return writeHeightMapUBytes(upliftMask)
//        return Biomes.rollingHillsNoiseTexture
//        return writeHeightMap(coastalBorderMap)
//        val heightMap = timeIt("rendered regions in") {
//            val heightMap = FloatArrayMatrix(outputWidth) { -1.0f }
//            renderRegions(executor, inputGraph, inputMask, heightMap, threadCount)
//            heightMap
//        }
//        return writeHeightMap(heightMap)

//        val heightMap = timeIt("rendered region borders in") {
//            val heightMap = FloatArrayMatrix(outputWidth)
//            renderRegionBorders(executor, inputGraph, inputMask, heightMap, threadCount)
//            heightMap
//        }
//        return writeHeightMap(heightMap)

//        return timeIt("rendered region borders in") {
//            renderRegionBorders(executor, inputGraph, inputMask, threadCount)
//        }
//        return regionBorderTextureId
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
        return RegionData(land, beach, regions)
    }

    private fun bootstrapErosion(executor: ExecutorService, graph: Graph, regionData: RegionData, biomes: List<Biome>, biomeMask: Matrix<Byte>, upliftMask: Matrix<Byte>, distanceScale: Float, random: Random): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        val vertices = graph.vertices
        val land = regionData.land
        val beach = regionData.beach
        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, land, beach, biomes, biomeMask, upliftMask, distanceScale)
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

    private fun performErosion(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>, iterations: Int, erosionLevels: List<ErosionLevel>, heightMapWidth: Int): FloatArrayMatrix {
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
        renderHeightmap(executor, graph, nodeIndex, heightMap, threadCount)
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

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: List<Int>, riverMouths: LinkedHashSet<Int>, biomes: List<Biome>, biomeMask: Matrix<Byte>, upliftMask: Matrix<Byte>, distanceScale: Float): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val biomeWidth = biomeMask.width
        val biomeWidthM1 = biomeWidth - 1
        val upliftWidth = upliftMask.width
        val upliftWidthM1 = upliftWidth - 1
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..land.size - 1 step threadCount) {
                    val landId = land[id]
                    val isExternal = riverMouths.contains(landId)
                    val area = vertices.getArea(landId) * areaScale
                    val point = vertices.getPoint(landId)
                    val bIndex = (Math.round(point.y * biomeWidthM1) * biomeWidth) + Math.round(point.x * biomeWidthM1)
                    val biomeId = Math.max(0, biomeMask[bIndex].toInt() - 1)
                    val biome = biomes[biomeId]
                    val uIndex = (Math.round(point.y * upliftWidthM1) * upliftWidth) + Math.round(point.x * upliftWidthM1)
                    val tempLift = upliftMask[uIndex].toInt() and 0xFF
                    val uplift: Float = if (tempLift == 0) {
                        0.0f
                    } else {
                        ((tempLift / 256.0f) * biome.deltaUplift + biome.minUplift) * biome.erosionLowSettings.upliftMultiplier
                    }
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

    private fun applyMapsToNodes(executor: ExecutorService, vertices: Vertices, heightMap: Matrix<Float>, upliftMask: Matrix<Byte>, biomes: List<Biome>, erosionSettings: List<ErosionLevel>, biomeMask: Matrix<Byte>, nodes: ArrayList<WaterNode>) {
        val hWidth = heightMap.width
        val hWidthM1 = hWidth - 1
        val bWidth = biomeMask.width
        val bWidthM1 = bWidth - 1
        val uWidth = upliftMask.width
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
                        val bIndex = (Math.round(point.y * bWidthM1) * bWidth) + Math.round(point.x * bWidthM1)
                        val biomeId = Math.max(0 , biomeMask[bIndex].toInt() - 1)
                        val biome = biomes[biomeId]
                        val uIndex = (Math.round(point.y * uWidthM1) * uWidth) + Math.round(point.x * uWidthM1)
                        val tempLift = upliftMask[uIndex].toInt() and 0xFF
                        val uplift: Float = if (tempLift == 0) {
                            0.0f
                        } else {
                            (tempLift / 256.0f) * biome.deltaUplift + biome.minUplift
                        }
                        node.biome = biomeId
                        node.uplift = Math.max(biome.minUplift, uplift * erosionSettings[biomeId].upliftMultiplier)
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

    fun writeHeightMapBytes(heightMap: Matrix<Byte>): TextureId {
        val width = heightMap.width
        val output = ShortArray(width * width)
        val maxLandValue = (0..heightMap.size.toInt() - 1).asSequence().map { heightMap[it] }.max()?.toFloat() ?: 0.0f
        println("maxLandValue = $maxLandValue")
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0..width - 1)) {
            for (x in (0..width - 1)) {
                val heightValue = heightMap[x, y].toFloat()
                if (heightValue < 0.0f) {
                    output[y * width + x] = 0
                } else {
                    output[y * width + x] = (((heightValue * landFactor) + waterLine) * 65535).toInt().toShort()
                }
            }
        }
        return buildTextureRedShort(output, width, GL_LINEAR, GL_LINEAR)
    }

    fun writeHeightMapUBytes(heightMap: Matrix<Byte>): TextureId {
        val width = heightMap.width
        val output = ShortArray(width * width)
        val maxLandValue = (0..heightMap.size.toInt() - 1).asSequence().map { heightMap[it].toInt() and 0xFF }.max()?.toFloat() ?: 0.0f
        println("maxLandValue = $maxLandValue")
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0..width - 1)) {
            for (x in (0..width - 1)) {
                val heightValue = (heightMap[x, y].toInt() and 0xFF).toFloat()
                if (heightValue < 0.0f) {
                    output[y * width + x] = 0
                } else {
                    output[y * width + x] = (((heightValue * landFactor) + waterLine) * 65535).toInt().toShort()
                }
            }
        }
        return buildTextureRedShort(output, width, GL_LINEAR, GL_LINEAR)
    }

    fun writeHeightMap(heightMap: Matrix<Float>): TextureId {
        val width = heightMap.width
        val output = ShortArray(width * width)
        val maxLandValue = (0..heightMap.size.toInt() - 1).asSequence().map { heightMap[it] }.max() ?: 0.0f
        println("maxLandValue = $maxLandValue")
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0..width - 1)) {
            for (x in (0..width - 1)) {
                val heightValue = heightMap[x, y]
                if (heightValue < 0.0f) {
                    output[y * width + x] = 0
                } else {
                    output[y * width + x] = (((heightValue * landFactor) + waterLine) * 65535).toInt().toShort()
                }
            }
        }
        return buildTextureRedShort(output, width, GL_LINEAR, GL_LINEAR)
    }

    fun buildUpliftTriangles(graph: Graph, regionMask: Matrix<Byte>): List<Pair<FloatArray, IntArray>> {
        val regions = ArrayList<Triple<ArrayList<Float>, ArrayList<Int>, AtomicInteger>>(16)
        val vertices = graph.vertices
        for (id in 0..vertices.size - 1) {
            val regionId = regionMask[id]
            if (regionId < 1) {
                continue
            }
            if (regions.size < regionId) {
                for (i in regions.size..regionId - 1) {
                    regions.add(Triple(ArrayList<Float>(), ArrayList<Int>(), AtomicInteger(0)))
                }
            }
            val region = regions[regionId - 1]
            val vertexData = region.first
            val indexData = region.second
            val vertexIndex = region.third

            val vertex = vertices[id]
            val border = vertex.cell.border

            fun buildVertex(point: Point2F): Int {
                vertexData.add(point.x)
                vertexData.add(point.y)
                return vertexIndex.getAndIncrement()
            }

            fun buildTriangle(v1: Int, v2: Int, v3: Int) {
                indexData.add(v1)
                indexData.add(v3)
                indexData.add(v2)
            }

            val centerId = buildVertex(vertex.point)
            val borderIds = border.map { buildVertex(it) }

            for (i in 1..border.size) {
                buildTriangle(centerId, borderIds[i % border.size], borderIds[i - 1])
            }
        }
        return regions.map { it.first.toFloatArray() to it.second.toIntArray() }
    }
}