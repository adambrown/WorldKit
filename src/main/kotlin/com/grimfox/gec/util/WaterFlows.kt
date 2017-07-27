package com.grimfox.gec.util

import com.grimfox.gec.util.Biomes.Biome
import com.grimfox.gec.util.Biomes.ErosionLevel
import com.grimfox.gec.util.Biomes.ErosionSettings
import com.grimfox.gec.util.Biomes.RegionData
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.buildTextureRedShort
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRedByte
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRedFloat
import com.grimfox.gec.ui.widgets.TextureBuilder.render
import com.grimfox.gec.ui.widgets.TextureBuilder.renderLandImage
import com.grimfox.gec.util.Biomes.UNDER_WATER_BIOME
import com.grimfox.gec.util.Rendering.renderEdges
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
import kotlin.collections.ArrayList

object WaterFlows {

    private val SIMPLEX_SCALE = 96.0f
    private val threadCount = Runtime.getRuntime().availableProcessors()

    class WaterNode(val id: Int,
                    var isExternal: Boolean,
                    val area: Float,
                    val adjacents: ArrayList<Pair<WaterNode, Float>>,
                    val simplexX: Float,
                    val simplexY: Float,
                    var uplift: Float,
                    var height: Float,
                    var drainageArea: Float,
                    var biome: Int,
                    val isPinned: Boolean) {

        var lake: Int = -1
        var parent: WaterNode = this
        var distanceToParent: Float = 0.0f
        val children: MutableCollection<WaterNode> = LinkedList()
    }

    data class PassKey(val lake1: Int, val lake2: Int)

    class Pass(val passKey: PassKey, val id1: Int, val id2: Int, val height: Float)

    fun generateWaterFlows(random: Random, regionSplines: RegionSplines, biomeGraph: Graph, biomeMask: Matrix<Byte>, flowGraphSmall: Graph, flowGraphMedium: Graph, flowGraphLarge: Graph, executor: ExecutorService, outputWidth: Int, mapScale: Int, biomes: List<Biome>): Pair<TextureId, TextureId> {
        val scale = ((mapScale * mapScale) / 400.0f).coerceIn(0.0f, 1.0f)
        val distanceScale = scale * 990000 + 10000
        val shaderTextureScale = ((mapScale / 20.0f).coerceIn(0.0f, 1.0f) * 0.75f) + 0.25f
        val shaderBorderDistanceScale = ((1.0f - ((mapScale / 20.0f).coerceIn(0.0f, 1.0f))) * 0.5f) + 0.5f
        val minFlowScale = ((mapScale * mapScale * mapScale) / 8000.0f).coerceIn(0.0f, 1.0f) * 600000000 + 250000

        val randomSeeds = Array(2) { random.nextLong() }

        val biomeMasksFuture = executor.call {
            val biomeTextureId = renderRegions(biomeGraph, biomeMask)
            val biomeMap = ByteBufferMatrix(4096, extractTextureRedByte(biomeTextureId, 4096))
            val biomeBorderTextureId = renderRegionBorders(executor, biomeGraph, biomeMask, threadCount)
            val landMapTextureId = renderLandImage(regionSplines.coastPoints)
            val riverBorderTextureId = renderEdges(executor, regionSplines.riverEdges.flatMap { it }, threadCount)
            val mountainBorderTextureId = renderEdges(executor, regionSplines.mountainEdges.flatMap { it }, threadCount)
            val coastalBorderTextureId = renderEdges(executor, regionSplines.coastEdges.flatMap { it.first + it.second.flatMap { it } }, threadCount)
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
                val biomeRegions = buildUpliftTriangles(biomeGraph, biomeMask)
                biomeRegions.forEachIndexed { i, (vertexData, indexData) ->
                    val biome = biomes[i]
                    biome.upliftShader.bind(shaderTextureScale, shaderBorderDistanceScale, landMapTextureId, coastalBorderTextureId, biomeTextureId, biomeBorderTextureId, riverBorderTextureId, mountainBorderTextureId)
                    dynamicGeometry2D.render(vertexData, indexData, biome.upliftShader.positionAttribute)
                }
                val retVal = textureRenderer.newRedTextureByte(GL_LINEAR, GL_LINEAR)
                textureRenderer.unbind()
                retVal
            }
            val underWaterTextureId = render { _, dynamicGeometry2D, textureRenderer ->
                glDisable(GL11.GL_BLEND)
                glDisable(GL11.GL_CULL_FACE)
                glDisable(GL13.GL_MULTISAMPLE)
                glEnable(GL_DEPTH_TEST)
                glDisable(GL11.GL_SCISSOR_TEST)
                glDisable(GL13.GL_MULTISAMPLE)
                textureRenderer.bind()
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                val vertexData = floatArrayOf(
                        0.0f, 0.0f,
                        1.0f, 0.0f,
                        1.0f, 1.0f,
                        0.0f, 1.0f)
                val indexData = intArrayOf(0, 1, 2, 2, 3, 0)
                UNDER_WATER_BIOME.upliftShader.bind(shaderTextureScale, shaderBorderDistanceScale, landMapTextureId, coastalBorderTextureId, biomeTextureId, biomeBorderTextureId, riverBorderTextureId, mountainBorderTextureId)
                dynamicGeometry2D.render(vertexData, indexData, UNDER_WATER_BIOME.upliftShader.positionAttribute)
                val retVal = textureRenderer.newRedTextureByte(GL_LINEAR, GL_LINEAR)
                textureRenderer.unbind()
                retVal
            }
            val upliftMask = ByteBufferMatrix(4096, extractTextureRedByte(upliftTextureId, 4096))
            val underWaterMask = FloatArrayMatrix(4096, extractTextureRedFloat(underWaterTextureId, 4096))
            val landMask = ByteBufferMatrix(4096, extractTextureRedByte(landMapTextureId, 4096))

            riverBorderTextureId.free()
            mountainBorderTextureId.free()
            coastalBorderTextureId.free()
            biomeTextureId.free()
            biomeBorderTextureId.free()
            upliftTextureId.free()
            Quadruple(biomeMap, upliftMask, landMask, underWaterMask)
        }
        val regionDataFuture = executor.call {
            buildRegionData(flowGraphSmall, biomeMasksFuture.value.third)
        }
        val smallMapsFuture = executor.call {
            val (nodeIndex, nodes, rivers) = bootstrapErosion(executor, flowGraphSmall, regionDataFuture.value, biomes, biomeMasksFuture.value.first, biomeMasksFuture.value.second, distanceScale, Random(randomSeeds[1]))
            performErosion(executor, flowGraphSmall, nodeIndex, nodes, rivers, 50, biomes.map { it.erosionLowSettings }, 1024, null,-1.0f)
        }
        val smallWaterMapsFuture = executor.call {
            val (nodeIndex, nodes, rivers) = bootstrapUnderWaterErosion(executor, flowGraphSmall, regionDataFuture.value, biomeMasksFuture.value.fourth, distanceScale, Random(randomSeeds[1]))
            performErosion(executor, flowGraphSmall, nodeIndex, nodes, rivers, 10, listOf(UNDER_WATER_BIOME.erosionLowSettings), 1024, null, 0.0f)
        }
        val midNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphMedium, biomeMasksFuture.value.third, distanceScale)
        }
        val midWaterNodesFuture = executor.call {
            prepareGraphNodesUnderWater(executor, flowGraphMedium, biomeMasksFuture.value.third, distanceScale)
        }
        val highNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphLarge, biomeMasksFuture.value.third, distanceScale)
        }
        val highWaterNodesFuture = executor.call {
            prepareGraphNodesUnderWater(executor, flowGraphLarge, biomeMasksFuture.value.third, distanceScale)
        }
        val midMapsFuture = executor.call {
            val heightMap = smallMapsFuture.value
            val (nodeIndex, nodes, rivers) = midNodesFuture.value
            val erosionSettings = biomes.map { it.erosionMidSettings }
            applyMapsToNodes(executor, flowGraphMedium.vertices, heightMap, biomeMasksFuture.value.second, biomes, erosionSettings, biomeMasksFuture.value.first, nodes)
            performErosion(executor, flowGraphMedium, nodeIndex, nodes, rivers, 25, erosionSettings, 2048, null, -1.0f)
        }
        val midWaterMapsFuture = executor.call {
            val heightMap = smallWaterMapsFuture.value
            val (nodeIndex, nodes, rivers, water, border) = midWaterNodesFuture.value
            val erosionSettings = listOf(UNDER_WATER_BIOME.erosionMidSettings)
            applyMapsToUnderWaterNodes(executor, flowGraphMedium.vertices, heightMap, nodes)

            val unused = LinkedHashSet(water)
            val used = LinkedHashSet(border)
            unused.removeAll(used)
            val next = LinkedHashSet(border)
            var lastUnusedCount = unused.size
            while (unused.isNotEmpty()) {
                val nextOrder = ArrayList(next)
                next.clear()
                nextOrder.forEach { id ->
                    val node = nodeIndex[id]!!
                    node.adjacents.forEach { (otherNode) ->
                        if (!used.contains(otherNode.id)) {
                            next.add(otherNode.id)
                            used.add(otherNode.id)
                            unused.remove(otherNode.id)
                        }
                    }
                }
                if (unused.isNotEmpty() && lastUnusedCount == unused.size) {
                    val makeSink = unused.asSequence().map { nodeIndex[it]!! }.filter { !it.isPinned }.toList().sortedBy { it.height }.first()
                    makeSink.isExternal = true
                    rivers.add(makeSink)
                    used.add(makeSink.id)
                    unused.remove(makeSink.id)
                    next.add(makeSink.id)
                }
                lastUnusedCount = unused.size
            }

            performErosion(executor, flowGraphMedium, nodeIndex, nodes, rivers, 10, erosionSettings, 2048, null, 0.0f)
        }
        val highWaterMapsFuture = executor.call {
            val heightMap = midWaterMapsFuture.value
            val (nodeIndex, nodes, rivers, water, border) = highWaterNodesFuture.value
            val erosionSettings = listOf(UNDER_WATER_BIOME.erosionHighSettings)
            applyMapsToUnderWaterNodes(executor, flowGraphLarge.vertices, heightMap, nodes)

            val unused = LinkedHashSet(water)
            val used = LinkedHashSet(border)
            unused.removeAll(used)
            val next = LinkedHashSet(border)
            var lastUnusedCount = unused.size
            while (unused.isNotEmpty()) {
                val nextOrder = ArrayList(next)
                next.clear()
                nextOrder.forEach { id ->
                    val node = nodeIndex[id]!!
                    node.adjacents.forEach { (otherNode) ->
                        if (!used.contains(otherNode.id)) {
                            next.add(otherNode.id)
                            used.add(otherNode.id)
                            unused.remove(otherNode.id)
                        }
                    }
                }
                if (unused.isNotEmpty() && lastUnusedCount == unused.size) {
                    val makeSink = unused.asSequence().map { nodeIndex[it]!! }.filter { !it.isPinned }.toList().sortedBy { it.height }.first()
                    makeSink.isExternal = true
                    rivers.add(makeSink)
                    used.add(makeSink.id)
                    unused.remove(makeSink.id)
                    next.add(makeSink.id)
                }
                lastUnusedCount = unused.size
            }

            performErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, 2, erosionSettings, outputWidth, null, 0.0f)
        }
        val highMapsFuture = executor.call {
            val heightMap = midMapsFuture.value
            val (nodeIndex, nodes, rivers) = highNodesFuture.value
            val erosionSettings = biomes.map { it.erosionHighSettings }
            applyMapsToNodes(executor, flowGraphLarge.vertices, heightMap, biomeMasksFuture.value.second, biomes, erosionSettings, biomeMasksFuture.value.first, nodes)
            val underWaterMask = highWaterMapsFuture.value
            val retVal = performErosion(executor, flowGraphLarge, nodeIndex, nodes, rivers, 5, erosionSettings, outputWidth, underWaterMask, -600.0f)
            val riverEdges = ArrayList<LineSegment2F>()
            rivers.forEach {
                riverEdges.addAll(recurseFindRiverEdges(flowGraphLarge.vertices, it, minFlowScale))
            }
            Pair(retVal, riverEdges)
        }
        return Pair(writeHeightMap(highMapsFuture.value.first), renderEdges(executor, highMapsFuture.value.second, threadCount, GL_LINEAR, GL_LINEAR))
//        return writeHeightMap(highMapsFuture.value)
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

    private fun recurseFindRiverEdges(vertices: Vertices, river: WaterNode, minFlow: Float): List<LineSegment2F> {
        val riverEdges = ArrayList<LineSegment2F>()
        river.children.forEach {
            makeEdgeIfSufficientFlow(vertices, it, riverEdges, minFlow)
        }
        return riverEdges
    }

    private fun makeEdgeIfSufficientFlow(vertices: Vertices, river: WaterNode, riverEdges: ArrayList<LineSegment2F>, minFlow: Float) {
        if (river.drainageArea > minFlow) {
            riverEdges.add(LineSegment2F(vertices[river.id].point, vertices[river.parent.id].point))
            river.children.forEach {
                makeEdgeIfSufficientFlow(vertices, it, riverEdges, minFlow)
            }
        }
    }

    private fun buildRegionData(graph: Graph, landMask: Matrix<Byte>): RegionData {
        val vertices = graph.vertices
        val land = ArrayList<Int>(vertices.size)
        val water = LinkedHashSet<Int>(vertices.size)
        val landMaskWidth = landMask.width
        val landMaskWidthM1 = landMaskWidth - 1
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            val index = (Math.round(point.y * landMaskWidthM1) * landMaskWidth) + Math.round(point.x * landMaskWidthM1)
            if (landMask[index].toInt() and 0xFF < 128) {
                water.add(i)
            } else {
                land.add(i)
            }
        }
        val beach = extractBeachFromGraphAndWater(vertices, water)
        return RegionData(land, water.toList(), beach)
    }

    private fun bootstrapUnderWaterErosion(executor: ExecutorService, graph: Graph, regionData: RegionData, heightMap: Matrix<Float>, distanceScale: Float, random: Random): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        val vertices = graph.vertices
        val land = LinkedHashSet(regionData.land)
        val water = ArrayList(regionData.water)
        val coast = LinkedHashSet(regionData.beach)
        val border = LinkedHashSet(graph.vertices.asSequence().filter { it.cell.isBorder }.map { it.id }.toList())
        val beach1 = LinkedHashSet(coast.flatMap { vertices.getAdjacentVertices(it) }.toSet().filter { land.contains(it) })
        val beach2 = coast.flatMap { vertices.getAdjacentVertices(it) }.toSet().filter { !beach1.contains(it) && land.contains(it) }
        water.addAll(beach1)
        water.addAll(beach2)
        coast.addAll(beach1)
        coast.addAll(beach2)
        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, water, border, heightMap, distanceScale, coast)
        val rivers = ArrayList<WaterNode>()
        border.forEach { id ->
            rivers.add(nodeIndex[id]!!)
        }
        val unused = LinkedHashSet(water)
        val used = LinkedHashSet(border)
        unused.removeAll(used)
        val next = LinkedHashSet(border)
        var lastUnusedCount = unused.size
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
            if (unused.isNotEmpty() && lastUnusedCount == unused.size) {
                val makeSink = unused.asSequence().map { nodeIndex[it]!! }.filter { !it.isPinned }.toList().sortedBy { it.height }.first()
                makeSink.isExternal = true
                rivers.add(makeSink)
                used.add(makeSink.id)
                unused.remove(makeSink.id)
                next.add(makeSink.id)
            }
            lastUnusedCount = unused.size
        }
        computeAreas(executor, rivers)
        val biomeBootstrapSettings = listOf(UNDER_WATER_BIOME.bootstrapSettings)
        computeHeights(executor, rivers, biomeBootstrapSettings)
        return Triple(nodeIndex, nodes, rivers)
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

    private fun performErosion(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>, iterations: Int, erosionLevels: List<ErosionLevel>, heightMapWidth: Int, fallback: Matrix<Float>? = null, defaultValue: Float = 0.0f): FloatArrayMatrix {
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
        val heightMap = FloatArrayMatrix(heightMapWidth) { defaultValue }
        renderHeightmap(executor, graph, nodeIndex, heightMap, fallback, threadCount)
        return heightMap
    }

    private fun prepareGraphNodesUnderWater(executor: ExecutorService, graph: Graph, landMask: Matrix<Byte>, distanceScale: Float): Quintuple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>, ArrayList<Int>, LinkedHashSet<Int>> {
        val vertices = graph.vertices
        val land = LinkedHashSet<Int>(vertices.size)
        val water = ArrayList<Int>(vertices.size)
        val landMaskWidth = landMask.width
        val landMaskWidthM1 = landMaskWidth - 1
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            val index = (Math.round(point.y * landMaskWidthM1) * landMaskWidth) + Math.round(point.x * landMaskWidthM1)
            if (landMask[index].toInt() and 0xFF < 128) {
                water.add(i)
            } else {
                land.add(i)
            }
        }
        val border = LinkedHashSet(graph.vertices.asSequence().filter { it.cell.isBorder }.map { it.id }.toList())
        val coast = extractBeachFromGraphAndWater(graph.vertices, LinkedHashSet(water))
        val beach1 = LinkedHashSet(coast.flatMap { vertices.getAdjacentVertices(it) }.toSet().filter { land.contains(it) })
        val beach2 = coast.flatMap { vertices.getAdjacentVertices(it) }.toSet().filter { !beach1.contains(it) && land.contains(it) }
        water.addAll(beach1)
        water.addAll(beach2)
        coast.addAll(beach1)
        coast.addAll(beach2)
        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, water, border, distanceScale, coast)
        val rivers = ArrayList<WaterNode>()
        border.forEach { id ->
            rivers.add(nodeIndex[id]!!)
        }
        return Quintuple(nodeIndex, nodes, rivers, water, border)
    }

    private fun prepareGraphNodes(executor: ExecutorService, graph: Graph, landMask: Matrix<Byte>, distanceScale: Float): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        val vertices = graph.vertices
        val land = ArrayList<Int>(vertices.size)
        val water = LinkedHashSet<Int>(vertices.size)
        val landMaskWidth = landMask.width
        val landMaskWidthM1 = landMaskWidth - 1
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            val index = (Math.round(point.y * landMaskWidthM1) * landMaskWidth) + Math.round(point.x * landMaskWidthM1)
            if (landMask[index].toInt() and 0xFF < 128) {
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

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: List<Int>, riverMouths: LinkedHashSet<Int>, heightMap: Matrix<Float>, distanceScale: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val width = heightMap.width
        val widthM1 = width - 1
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..land.size - 1 step threadCount) {
                    val landId = land[id]
                    val isExternal = riverMouths.contains(landId)
                    val area = vertices.getArea(landId) * areaScale
                    val point = vertices.getPoint(landId)
                    val index = (Math.round(point.y * widthM1) * width) + Math.round(point.x * widthM1)
                    val isPinned = isExternal || pinned?.contains(landId) ?: false
                    val height = if (isExternal) 0.0f else if (isPinned) 600.0f else heightMap[index] * 600.0f
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, 0.0f, height, area, 0, isPinned)
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

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: List<Int>, riverMouths: LinkedHashSet<Int>, biomes: List<Biome>, biomeMask: Matrix<Byte>, upliftMask: Matrix<Byte>, distanceScale: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
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
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, uplift, 0.0f, area, biomeId, isExternal || pinned?.contains(landId) ?: false)
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

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: ArrayList<Int>, riverMouths: LinkedHashSet<Int>, distanceScale: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..land.size - 1 step threadCount) {
                    val landId = land[id]
                    val isExternal = riverMouths.contains(landId)
                    val area = vertices.getArea(landId) * areaScale
                    val point = vertices.getPoint(landId)
                    val isPinned = isExternal || pinned?.contains(landId) ?: false
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, 0.0f, 0.0f, area, 0, isPinned)
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

    private fun applyMapsToUnderWaterNodes(executor: ExecutorService, vertices: Vertices, heightMap: Matrix<Float>, nodes: ArrayList<WaterNode>) {
        val hWidth = heightMap.width
        val hWidthM1 = hWidth - 1
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..nodes.size - 1 step threadCount) {
                    val node = nodes[id]
                    val height = if (node.isExternal) {
                        0.0f
                    } else if (node.isPinned) {
                        600.0f
                    } else {
                        val point = vertices.getPoint(node.id)
                        val hIndex = (Math.round(point.y * hWidthM1) * hWidth) + Math.round(point.x * hWidthM1)
                        heightMap[hIndex]
                    }
                    node.height = Math.max(0.0f, height)
                    node.uplift = 0.0f
                    node.biome = 0
                }
            }
        }
        nodeFutures.forEach { it.join() }
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
                    if (!node.isPinned) {
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
            if (!node.isPinned) {
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

    fun renderHeightmap(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMap: Matrix<Float>, fallback: Matrix<Float>?, threadCount: Int) {
        val fWidth = fallback?.width ?: 0
        val fWidthM1 = fWidth - 1
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
                        if (fallback != null) {
                            val naH = if (na != null) {
                                na.height
                            } else {
                                val point = va.point
                                val index = (Math.round(point.y * fWidthM1) * fWidth) + Math.round(point.x * fWidthM1)
                                fallback[index] - 600
                            }
                            val nbH = if (nb != null) {
                                nb.height
                            } else {
                                val point = vb.point
                                val index = (Math.round(point.y * fWidthM1) * fWidth) + Math.round(point.x * fWidthM1)
                                fallback[index] - 600
                            }
                            val ncH = if (nc != null) {
                                nc.height
                            } else {
                                val point = vc.point
                                val index = (Math.round(point.y * fWidthM1) * fWidth) + Math.round(point.x * fWidthM1)
                                fallback[index] - 600
                            }
                            val pa = va.point
                            val pb = vb.point
                            val pc = vc.point
                            val a = Point3F(pa.x, pa.y, naH)
                            val b = Point3F(pb.x, pb.y, nbH)
                            val c = Point3F(pc.x, pc.y, ncH)
                            val cross = (b - a).cross(c - a)
                            if (cross.c < 0) {
                                renderTriangle(a, b, c, heightMap)
                            } else {
                                renderTriangle(a, c, b, heightMap)
                            }
                        } else {
                            continue
                        }
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
        val minWaterValue = (0..heightMap.size.toInt() - 1).asSequence().map { heightMap[it] }.min() ?: 0.0f
        println("minWaterValue = $minWaterValue")
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        val waterFactor = (1.0f / minWaterValue) * waterLine
        for (y in (0..width - 1)) {
            for (x in (0..width - 1)) {
                val heightValue = heightMap[x, y]
                if (heightValue < 0.0f) {
                    output[y * width + x] = ((waterLine - (heightValue * waterFactor)) * 65535).toInt().toShort()
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