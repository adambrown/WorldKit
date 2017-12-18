package com.grimfox.gec.util

import com.grimfox.gec.util.Biomes.Biome
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
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRedShort
import com.grimfox.gec.ui.widgets.TextureBuilder.render
import com.grimfox.gec.ui.widgets.TextureBuilder.renderLandImage
import com.grimfox.gec.util.Biomes.DEGREES_TO_SLOPES
import com.grimfox.gec.util.Biomes.UNDER_WATER_BIOME
import com.grimfox.gec.util.Rendering.renderEdges
import com.grimfox.gec.util.Rendering.renderRegionBorders
import com.grimfox.gec.util.Rendering.renderRegions
import com.grimfox.gec.util.geometry.renderTriangle
import org.joml.SimplexNoise.noise
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13
import java.lang.Math.log
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
                    var elevationPower: Float,
                    var height: Float,
                    var drainageArea: Float,
                    var biome: Int,
                    val soilMobility: Float,
                    val isPinned: Boolean) {

        var lake: Int = -1
        var parent: WaterNode = this
        var distanceToParent: Float = 0.0f
        val children: MutableCollection<WaterNode> = LinkedList()
    }

    data class PassKey(val lake1: Int, val lake2: Int)

    class Pass(val passKey: PassKey, val id1: Int, val id2: Int, val height: Float)

    class Masks(val biomeMask: Matrix<Byte>, val landMask: Matrix<Byte>, val underWaterMask: Matrix<Float>, val elevationPowerMask: Matrix<Short>, val startingHeightsMask: Matrix<Short>, val soilMobilityMask: Matrix<Short>)

    fun generateWaterFlows(
            random: Random,
            regionSplines: RegionSplines,
            biomeGraph: Graph,
            biomeMask: Matrix<Byte>,
            flowGraphSmall: Graph,
            flowGraphMedium: Graph,
            flowGraphLarge: Graph,
            executor: ExecutorService,
            outputWidth: Int,
            mapScale: Int,
            biomes: List<Biome>,
            customElevationPowerMap: TextureId,
            customStartingHeightsMap: TextureId,
            customSoilMobilityMap: TextureId): Pair<TextureId, TextureId> {
        val scale = ((mapScale * mapScale) / 400.0f).coerceIn(0.0f, 1.0f)
        val distanceScale = scale * 990000 + 10000
        val shaderTextureScale = ((mapScale / 20.0f).coerceIn(0.0f, 1.0f) * 0.75f) + 0.25f
        val shaderBorderDistanceScale = ((1.0f - ((mapScale / 20.0f).coerceIn(0.0f, 1.0f))) * 0.5f) + 0.5f
        val heightScale = ((log(shaderTextureScale - 0.21) * 0.4) + 1.59).toFloat()
        val minFlowScale = ((mapScale * mapScale * mapScale) / 8000.0f).coerceIn(0.0f, 1.0f) * 600000000 + 250000

        val randomSeeds = Array(2) { random.nextLong() }

        val biomeMasksFuture = executor.call {
            val biomeTextureId = renderRegions(biomeGraph, biomeMask)
            val biomeMap = ByteBufferMatrix(4096, extractTextureRedByte(biomeTextureId, 4096))
            val biomeBorderTextureId = renderRegionBorders(executor, biomeGraph, biomeMask, threadCount)
            val landMapTextureId = renderLandImage(regionSplines.coastPoints)
            val riverBorderTextureId = renderEdges(executor, regionSplines.riverEdges.flatMap { it } + regionSplines.customRiverEdges.flatMap { it }, threadCount)
            val mountainBorderTextureId = renderEdges(executor, regionSplines.mountainEdges.flatMap { it } + regionSplines.customMountainEdges.flatMap { it }, threadCount)
            val coastalBorderTextureId = renderEdges(executor, regionSplines.coastEdges.flatMap { it.first + it.second.flatMap { it } }, threadCount)
            val biomeRegions = buildTriangles(biomeGraph, biomeMask)
            val elevationPowerTextureId = render { _, dynamicGeometry2D, textureRenderer ->
                glDisable(GL11.GL_BLEND)
                glDisable(GL11.GL_CULL_FACE)
                glDisable(GL13.GL_MULTISAMPLE)
                glEnable(GL_DEPTH_TEST)
                glDisable(GL11.GL_SCISSOR_TEST)
                glDisable(GL13.GL_MULTISAMPLE)
                textureRenderer.bind()
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                biomeRegions.forEachIndexed { i, (vertexData, indexData) ->
                    val biome = biomes[i]
                    biome.elevationPowerShader.bind(
                            textureScale = shaderTextureScale,
                            borderDistanceScale = shaderBorderDistanceScale,
                            heightScale = heightScale,
                            landMask = landMapTextureId,
                            coastBorderMask = coastalBorderTextureId,
                            biomeMask = biomeTextureId,
                            biomeBorderMask = biomeBorderTextureId,
                            riverBorderMask = riverBorderTextureId,
                            mountainBorderMask = mountainBorderTextureId,
                            customElevationPowerMap = customElevationPowerMap,
                            customStartingHeightsMap = customStartingHeightsMap,
                            customSoilMobilityMap = customSoilMobilityMap)
                    dynamicGeometry2D.render(vertexData, indexData, biome.elevationPowerShader.positionAttribute)
                }
                val retVal = textureRenderer.newRedTextureShort(GL_LINEAR, GL_LINEAR)
                textureRenderer.unbind()
                retVal
            }
            val startingHeightsTextureId = render { _, dynamicGeometry2D, textureRenderer ->
                glDisable(GL11.GL_BLEND)
                glDisable(GL11.GL_CULL_FACE)
                glDisable(GL13.GL_MULTISAMPLE)
                glEnable(GL_DEPTH_TEST)
                glDisable(GL11.GL_SCISSOR_TEST)
                glDisable(GL13.GL_MULTISAMPLE)
                textureRenderer.bind()
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                biomeRegions.forEachIndexed { i, (vertexData, indexData) ->
                    val biome = biomes[i]
                    biome.startingHeightShader.bind(
                            textureScale = shaderTextureScale,
                            borderDistanceScale = shaderBorderDistanceScale,
                            heightScale = heightScale,
                            landMask = landMapTextureId,
                            coastBorderMask = coastalBorderTextureId,
                            biomeMask = biomeTextureId,
                            biomeBorderMask = biomeBorderTextureId,
                            riverBorderMask = riverBorderTextureId,
                            mountainBorderMask = mountainBorderTextureId,
                            customElevationPowerMap = customElevationPowerMap,
                            customStartingHeightsMap = customStartingHeightsMap,
                            customSoilMobilityMap = customSoilMobilityMap)
                    dynamicGeometry2D.render(vertexData, indexData, biome.startingHeightShader.positionAttribute)
                }
                val retVal = textureRenderer.newRedTextureShort(GL_LINEAR, GL_LINEAR)
                textureRenderer.unbind()
                retVal
            }
            val soilMobilityTextureId = render { _, dynamicGeometry2D, textureRenderer ->
                glDisable(GL11.GL_BLEND)
                glDisable(GL11.GL_CULL_FACE)
                glDisable(GL13.GL_MULTISAMPLE)
                glEnable(GL_DEPTH_TEST)
                glDisable(GL11.GL_SCISSOR_TEST)
                glDisable(GL13.GL_MULTISAMPLE)
                textureRenderer.bind()
                glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                biomeRegions.forEachIndexed { i, (vertexData, indexData) ->
                    val biome = biomes[i]
                    val shader = biome.soilMobilityShader
                    if (shader != null) {
                        shader.bind(
                                textureScale = shaderTextureScale,
                                borderDistanceScale = shaderBorderDistanceScale,
                                heightScale = heightScale,
                                landMask = landMapTextureId,
                                coastBorderMask = coastalBorderTextureId,
                                biomeMask = biomeTextureId,
                                biomeBorderMask = biomeBorderTextureId,
                                riverBorderMask = riverBorderTextureId,
                                mountainBorderMask = mountainBorderTextureId,
                                customElevationPowerMap = customStartingHeightsMap,
                                customStartingHeightsMap = customElevationPowerMap,
                                customSoilMobilityMap = customSoilMobilityMap)
                        dynamicGeometry2D.render(vertexData, indexData, shader.positionAttribute)
                    }
                }
                val retVal = textureRenderer.newRedTextureShort(GL_LINEAR, GL_LINEAR)
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
                UNDER_WATER_BIOME.elevationPowerShader.bind(
                        textureScale = shaderTextureScale,
                        borderDistanceScale = shaderBorderDistanceScale,
                        heightScale = heightScale,
                        landMask = landMapTextureId,
                        coastBorderMask = coastalBorderTextureId,
                        biomeMask = biomeTextureId,
                        biomeBorderMask = biomeBorderTextureId,
                        riverBorderMask = riverBorderTextureId,
                        mountainBorderMask = mountainBorderTextureId,
                        customElevationPowerMap = customElevationPowerMap,
                        customStartingHeightsMap = customStartingHeightsMap,
                        customSoilMobilityMap = customSoilMobilityMap)
                dynamicGeometry2D.render(vertexData, indexData, UNDER_WATER_BIOME.elevationPowerShader.positionAttribute)
                val retVal = textureRenderer.newRedTextureByte(GL_LINEAR, GL_LINEAR)
                textureRenderer.unbind()
                retVal
            }
            val elevationMask = ShortArrayMatrix(4096, extractTextureRedShort(elevationPowerTextureId, 4096))
            val startingHeights = ShortArrayMatrix(4096, extractTextureRedShort(startingHeightsTextureId, 4096))
            val underWaterMask = FloatArrayMatrix(4096, extractTextureRedFloat(underWaterTextureId, 4096))
            val landMask = ByteBufferMatrix(4096, extractTextureRedByte(landMapTextureId, 4096))
            val soilMobilityMask = ShortArrayMatrix(4096, extractTextureRedShort(soilMobilityTextureId, 4096))

            riverBorderTextureId.free()
            mountainBorderTextureId.free()
            coastalBorderTextureId.free()
            biomeTextureId.free()
            biomeBorderTextureId.free()
            elevationPowerTextureId.free()
            startingHeightsTextureId.free()
            Masks(biomeMap, landMask, underWaterMask, elevationMask, startingHeights, soilMobilityMask)
        }
        val regionDataFuture = executor.call {
            buildRegionData(flowGraphSmall, biomeMasksFuture.value.landMask)
        }
        val smallMapsFuture = executor.call {
            val (nodeIndex, nodes, rivers) = bootstrapErosion(executor, flowGraphSmall, regionDataFuture.value, biomes, biomeMasksFuture.value.biomeMask, biomeMasksFuture.value.elevationPowerMask, biomeMasksFuture.value.startingHeightsMask, biomeMasksFuture.value.soilMobilityMask, distanceScale, Random(randomSeeds[1]))
            performErosion(executor, flowGraphSmall, biomeMasksFuture.value.biomeMask, nodeIndex, nodes, rivers, 50, biomes, biomes.map { it.lowPassSettings }, 1024, null,-1.0f)
        }
        val smallWaterMapsFuture = executor.call {
            val (nodeIndex, nodes, rivers) = bootstrapUnderWaterErosion(executor, flowGraphSmall, regionDataFuture.value, biomeMasksFuture.value.underWaterMask, biomeMasksFuture.value.soilMobilityMask, distanceScale, Random(randomSeeds[1]))
            performErosion(executor, flowGraphSmall, null, nodeIndex, nodes, rivers, 10, listOf(UNDER_WATER_BIOME), listOf(UNDER_WATER_BIOME.lowPassSettings), 1024, null, 0.0f)
        }
        val midNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphMedium, biomeMasksFuture.value.landMask, biomeMasksFuture.value.soilMobilityMask, distanceScale)
        }
        val midWaterNodesFuture = executor.call {
            prepareGraphNodesUnderWater(executor, flowGraphMedium, biomeMasksFuture.value.landMask, biomeMasksFuture.value.soilMobilityMask, distanceScale)
        }
        val highNodesFuture = executor.call {
            prepareGraphNodes(executor, flowGraphLarge, biomeMasksFuture.value.landMask, biomeMasksFuture.value.soilMobilityMask, distanceScale)
        }
        val highWaterNodesFuture = executor.call {
            prepareGraphNodesUnderWater(executor, flowGraphLarge, biomeMasksFuture.value.landMask, biomeMasksFuture.value.soilMobilityMask, distanceScale)
        }
        val midMapsFuture = executor.call {
            val heightMap = smallMapsFuture.value
            val (nodeIndex, nodes, rivers) = midNodesFuture.value
            val erosionSettings = biomes.map { it.midPassSettings }
            applyMapsToNodes(executor, flowGraphMedium.vertices, heightMap, biomeMasksFuture.value.elevationPowerMask, biomeMasksFuture.value.startingHeightsMask, erosionSettings, biomeMasksFuture.value.biomeMask, nodes)
            performErosion(executor, flowGraphMedium, biomeMasksFuture.value.biomeMask, nodeIndex, nodes, rivers, 25, biomes, erosionSettings, 2048, null, -1.0f)
        }
        val midWaterMapsFuture = executor.call {
            val heightMap = smallWaterMapsFuture.value
            val (nodeIndex, nodes, rivers, water, border) = midWaterNodesFuture.value
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
            performErosion(executor, flowGraphMedium, null, nodeIndex, nodes, rivers, 10, listOf(UNDER_WATER_BIOME), listOf(UNDER_WATER_BIOME.midPassSettings), 2048, null, 0.0f)
        }
        val highWaterMapsFuture = executor.call {
            val heightMap = midWaterMapsFuture.value
            val (nodeIndex, nodes, rivers, water, border) = highWaterNodesFuture.value
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

            performErosion(executor, flowGraphLarge, null, nodeIndex, nodes, rivers, 2, listOf(UNDER_WATER_BIOME), listOf(UNDER_WATER_BIOME.highPassSettings), outputWidth, null, 0.0f)
        }
        val highMapsFuture = executor.call {
            val heightMap = midMapsFuture.value
            val (nodeIndex, nodes, rivers) = highNodesFuture.value
            val erosionSettings = biomes.map { it.highPassSettings }
            applyMapsToNodes(executor, flowGraphLarge.vertices, heightMap, biomeMasksFuture.value.elevationPowerMask, biomeMasksFuture.value.startingHeightsMask, erosionSettings, biomeMasksFuture.value.biomeMask, nodes)
            val underWaterMask = highWaterMapsFuture.value
            val retVal = performErosion(executor, flowGraphLarge, biomeMasksFuture.value.biomeMask, nodeIndex, nodes, rivers, 25, biomes, erosionSettings, outputWidth, underWaterMask, -600.0f)
            val riverEdges = ArrayList<LineSegment2F>()
            rivers.forEach {
                riverEdges.addAll(recurseFindRiverEdges(flowGraphLarge.vertices, it, minFlowScale))
            }
            Pair(retVal, riverEdges)
        }
        return Pair(writeHeightMap(highMapsFuture.value.first), renderEdges(executor, highMapsFuture.value.second, threadCount, GL_LINEAR, GL_LINEAR))
//        return Pair(writeHeightMap(highMapsFuture.value.first), renderEdges(executor, emptyList(), threadCount, GL_LINEAR, GL_LINEAR))
//        return Pair(writeHeightMapUShorts(biomeMasksFuture.value.second), renderEdges(executor, emptyList(), threadCount, GL_LINEAR, GL_LINEAR))
//        return writeHeightMapUBytes(elevationMask)
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

    private fun bootstrapUnderWaterErosion(executor: ExecutorService, graph: Graph, regionData: RegionData, heightMap: Matrix<Float>, soilMobilityMap: Matrix<Short>, distanceScale: Float, random: Random): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
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
        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, water, border, heightMap, soilMobilityMap, distanceScale, coast)
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
        computeHeights(executor, rivers, listOf(UNDER_WATER_BIOME), listOf(ErosionSettings(1.0f, 1.0f, 0.001f)))
        return Triple(nodeIndex, nodes, rivers)
    }

    private fun bootstrapErosion(executor: ExecutorService, graph: Graph, regionData: RegionData, biomes: List<Biome>, biomeMask: Matrix<Byte>, elevationMask: Matrix<Short>, startingHeights: Matrix<Short>, soilMobilityMap: Matrix<Short>, distanceScale: Float, random: Random): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        val vertices = graph.vertices
        val land = regionData.land
        val beach = regionData.beach
        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, land, beach, biomes, biomeMask, elevationMask, startingHeights, soilMobilityMap, distanceScale)
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
        val bootstrapErosion = ErosionSettings(1.0f, 1.0f, 1.0f)
        computeHeights(executor, rivers, biomes, biomes.map { bootstrapErosion })
        return Triple(nodeIndex, nodes, rivers)
    }

    private fun performErosion(executor: ExecutorService, graph: Graph, biomeMask: Matrix<Byte>?, nodeIndex: Array<WaterNode?>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>, iterations: Int, biomes: List<Biome>, erosionSettings: List<ErosionSettings>, heightMapWidth: Int, fallback: Matrix<Float>? = null, defaultValue: Float = 0.0f): FloatArrayMatrix {
        val lakes = ArrayList<WaterNode>()
        val passes = LinkedHashMap<PassKey, Pass>()
        for (i in 0..iterations - 1) {
            lakes.clear()
            passes.clear()
            prepareNodesAndLakes(executor, lakes, nodes, rivers)
            computeLakeConnections(graph.vertices, lakes, nodeIndex, passes, rivers)
            computeAreas(executor, rivers)
            computeHeights(executor, rivers, biomes, erosionSettings)
        }
        val heightMap = FloatArrayMatrix(heightMapWidth) { defaultValue }
        renderHeightMap(executor, graph, nodeIndex, heightMap, fallback, threadCount)
        val biomeExtremes = Array(erosionSettings.size) { Pair(mRef(Float.MAX_VALUE), mRef(-Float.MAX_VALUE))}
        nodes.forEach {
            val (min, max) = biomeExtremes[it.biome]
            val height = it.height
            if (height < min.value) {
                min.value = height
            }
            if (height > max.value) {
                max.value = height
            }
        }
        if (biomeMask != null) {
            applyTerracing(executor, heightMap, biomeMask, erosionSettings, biomeExtremes, threadCount)
        }
        return heightMap
    }

    private fun prepareGraphNodesUnderWater(executor: ExecutorService, graph: Graph, landMask: Matrix<Byte>, soilMobilityMap: Matrix<Short>, distanceScale: Float): Quintuple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>, ArrayList<Int>, LinkedHashSet<Int>> {
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
        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, water, border, soilMobilityMap, distanceScale, coast)
        val rivers = ArrayList<WaterNode>()
        border.forEach { id ->
            rivers.add(nodeIndex[id]!!)
        }
        return Quintuple(nodeIndex, nodes, rivers, water, border)
    }

    private fun prepareGraphNodes(executor: ExecutorService, graph: Graph, landMask: Matrix<Byte>, soilMobilityMap: Matrix<Short>, distanceScale: Float): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
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

        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, land, beach, soilMobilityMap, distanceScale)
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

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: List<Int>, riverMouths: LinkedHashSet<Int>, heightMap: Matrix<Float>, soilMobilityMap: Matrix<Short>, distanceScale: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val hWidth = heightMap.width
        val hWidthM1 = hWidth - 1
        val eWidth = soilMobilityMap.width
        val eWidthM1 = eWidth - 1
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..land.size - 1 step threadCount) {
                    val landId = land[id]
                    val isExternal = riverMouths.contains(landId)
                    val area = vertices.getArea(landId) * areaScale
                    val point = vertices.getPoint(landId)
                    val hIndex = (Math.round(point.y * hWidthM1) * hWidth) + Math.round(point.x * hWidthM1)
                    val isPinned = isExternal || pinned?.contains(landId) ?: false
                    val height = if (isExternal) 0.0f else if (isPinned) 600.0f else heightMap[hIndex] * 600.0f
                    val eIndex = (Math.round(point.y * eWidthM1) * eWidth) + Math.round(point.x * eWidthM1)
                    val soilMobility = ((soilMobilityMap[eIndex].toInt() and 0xFFFF) / 65535.0f) * 0.000001122f
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, 0.0f, height, area, 0, soilMobility, isPinned)
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

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: List<Int>, riverMouths: LinkedHashSet<Int>, biomes: List<Biome>, biomeMask: Matrix<Byte>, elevationMask: Matrix<Short>, startingHeights: Matrix<Short>, soilMobilityMap: Matrix<Short>, distanceScale: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val biomeWidth = biomeMask.width
        val biomeWidthM1 = biomeWidth - 1
        val elevationWidth = elevationMask.width
        val elevationWidthM1 = elevationWidth - 1
        val heightWidth = startingHeights.width
        val heightWidthM1 = heightWidth - 1
        val soilMobilityWidth = soilMobilityMap.width
        val soilMobilityWidthM1 = soilMobilityWidth - 1
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
                    val uIndex = (Math.round(point.y * elevationWidthM1) * elevationWidth) + Math.round(point.x * elevationWidthM1)
                    val elevationPower = toElevationPower(elevationMask[uIndex], biome.lowPassSettings.elevationPowerMultiplier)
                    val hIndex = (Math.round(point.y * heightWidthM1) * heightWidth) + Math.round(point.x * heightWidthM1)
                    val height = ((startingHeights[hIndex].toInt() and 0xFFFF) / 65536.0f) * 3000.0f
                    val eIndex = (Math.round(point.y * soilMobilityWidthM1) * soilMobilityWidth) + Math.round(point.x * soilMobilityWidthM1)
                    val soilMobility = ((soilMobilityMap[eIndex].toInt() and 0xFFFF) / 65535.0f) * 0.000001122f
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, elevationPower, height, area, biomeId, soilMobility, isExternal || pinned?.contains(landId) ?: false)
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

    private fun toElevationPower(asShort: Short, multiplier: Float): Float {
        val temp = (asShort.toInt() and 0xFFFF)
        return if (temp == 0) {
            0.0f
        } else {
            ((((temp + 1) / 65536.0f) * 0.0005f) * multiplier).coerceAtLeast(0.0f)
        }
    }

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: ArrayList<Int>, riverMouths: LinkedHashSet<Int>, soilMobilityMap: Matrix<Short>, distanceScale: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val eWidth = soilMobilityMap.width
        val eWidthM1 = eWidth - 1
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
                    val eIndex = (Math.round(point.y * eWidthM1) * eWidth) + Math.round(point.x * eWidthM1)
                    val soilMobility = ((soilMobilityMap[eIndex].toInt() and 0xFFFF) / 65535.0f) * 0.000001122f
                    val node = WaterNode(landId, isExternal, area, ArrayList<Pair<WaterNode, Float>>(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, 0.0f, 0.0f, area, 0, soilMobility, isPinned)
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
                    node.elevationPower = 0.0f
                    node.biome = 0
                }
            }
        }
        nodeFutures.forEach { it.join() }
    }

    private fun applyMapsToNodes(executor: ExecutorService, vertices: Vertices, heightMap: Matrix<Float>, elevationMask: Matrix<Short>, startingHeights: Matrix<Short>, erosionSettings: List<ErosionSettings>, biomeMask: Matrix<Byte>, nodes: ArrayList<WaterNode>) {
        val heightWidth = heightMap.width
        val heightWidthM1 = heightWidth - 1
        val startHeightWidth = startingHeights.width
        val startHeightWidthM1 = startHeightWidth - 1
        val biomeWidth = biomeMask.width
        val biomeWidthM1 = biomeWidth - 1
        val elevationWidth = elevationMask.width
        val elevationWidthM1 = elevationWidth - 1
        val nodeFutures = (0..threadCount - 1).map { i ->
            executor.call {
                for (id in i..nodes.size - 1 step threadCount) {
                    val node = nodes[id]
                    if (!node.isPinned) {
                        val point = vertices.getPoint(node.id)
                        val hIndex = (Math.round(point.y * heightWidthM1) * heightWidth) + Math.round(point.x * heightWidthM1)
                        val height1 = heightMap[hIndex]
                        val shIndex = (Math.round(point.y * startHeightWidthM1) * startHeightWidth) + Math.round(point.x * startHeightWidthM1)
                        val height2 = ((startingHeights[shIndex].toInt() and 0xFFFF) / 65536.0f) * 3000.0f
                        val bIndex = (Math.round(point.y * biomeWidthM1) * biomeWidth) + Math.round(point.x * biomeWidthM1)
                        val biomeId = Math.max(0 , biomeMask[bIndex].toInt() - 1)
                        val uIndex = (Math.round(point.y * elevationWidthM1) * elevationWidth) + Math.round(point.x * elevationWidthM1)
                        node.biome = biomeId
                        val settings = erosionSettings[biomeId]
                        val blendFactor = settings.previousTierBlendWeight
                        val blendFactorI = 1.0f - blendFactor
                        val height = height1 * blendFactor + height2 * blendFactorI
                        node.height = Math.max(0.0f, height)
                        node.elevationPower = toElevationPower(elevationMask[uIndex], settings.elevationPowerMultiplier)
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

    private fun computeHeights(executor: ExecutorService, rivers: ArrayList<WaterNode>, biomes: List<Biome>, erosionSettings: List<ErosionSettings>) {
        val heightFutures = rivers.map { river ->
            executor.call {
                recurseHeights(river, biomes, erosionSettings)
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

    private fun recurseHeights(node: WaterNode, biomes: List<Biome>, erosionSettings: List<ErosionSettings>) {
        if (node.isExternal) {
            node.children.forEach { recurseHeights(it, biomes, erosionSettings) }
        } else {
            if (!node.isPinned) {
                val biome = biomes[node.biome]
                val settings = erosionSettings[node.biome]
                val parent = node.parent
                val parentHeight = parent.height
                val flow = node.soilMobility * settings.soilMobilityMultiplier * Math.pow(node.drainageArea.toDouble(), 0.5)
                val erosion = flow / node.distanceToParent
                val denominator = 1.0 + (erosion * 250000.0f)
                val numerator = node.height + (250000.0f * (node.elevationPower + (erosion * parentHeight)))
                node.height = (numerator / denominator).toFloat()
                val variance = noise(node.simplexX, node.simplexY, node.height / 10.0f)
                val (talusSet, talusVarianceSet) = biome.talusAngles
                val heightIndex = Math.round(node.height * biome.heightMultiplier).coerceIn(0, 1023)
                val talus = DEGREES_TO_SLOPES[Math.round((talusSet[heightIndex] + talusVarianceSet[heightIndex] * variance) * 65535.0f).coerceIn(0, 65535)]
                if ((node.height - parent.height) / node.distanceToParent > talus) {
                    node.height = (node.distanceToParent * talus) + parent.height
                }
            }
            node.children.forEach { recurseHeights(it, biomes, erosionSettings) }
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

    fun <T : Reference<Float>> applyTerracing(executor: ExecutorService, heightMap: Matrix<Float>, biomeMask: Matrix<Byte>, erosionSettings: List<ErosionSettings>, biomeExtremes: Array<Pair<T, T>>, threadCount: Int) {
        val futures = ArrayList<Future<*>>(threadCount)
        val terraceFunctions = Array(erosionSettings.size + 1) {
            if (it == 0) {
                null
            } else {
                val (min, max) = biomeExtremes[it - 1]
                erosionSettings[it - 1].terraceFunction?.invoke(min.value, max.value)
            }
        }
        (0..threadCount - 1).mapTo(futures) {
            executor.submit {
                for (i in it..heightMap.size.toInt() - 1 step threadCount) {
                    val terracing = terraceFunctions[biomeMask[i].toInt()]
                    if (terracing != null) {
                        val height = heightMap[i]
                        val newHeight = terracing.apply(height)
                        if (newHeight != height) {
                            heightMap[i] = newHeight
                        }
                    }
                }
            }
        }
        futures.forEach(Future<*>::join)
    }

    private fun renderHeightMap(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMap: Matrix<Float>, fallback: Matrix<Float>?, threadCount: Int) {
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

    private fun writeHeightMapBytes(heightMap: Matrix<Byte>): TextureId {
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

    private fun writeHeightMapUBytes(heightMap: Matrix<Byte>): TextureId {
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

    private fun writeHeightMapUShorts(heightMap: Matrix<Short>): TextureId {
        val width = heightMap.width
        val output = ShortArray(width * width)
        val maxLandValue = (0..heightMap.size.toInt() - 1).asSequence().map { heightMap[it].toInt() and 0xFFFF }.max()?.toFloat() ?: 0.0f
        println("maxLandValue = $maxLandValue")
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0..width - 1)) {
            for (x in (0..width - 1)) {
                val heightValue = (heightMap[x, y].toInt() and 0xFFFF).toFloat()
                if (heightValue < 0.0f) {
                    output[y * width + x] = 0
                } else {
                    output[y * width + x] = (((heightValue * landFactor) + waterLine) * 65535).toInt().toShort()
                }
            }
        }
        return buildTextureRedShort(output, width, GL_LINEAR, GL_LINEAR)
    }

    private fun writeHeightMap(heightMap: Matrix<Float>): TextureId {
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

    private fun buildTriangles(graph: Graph, regionMask: Matrix<Byte>): List<Pair<FloatArray, IntArray>> {
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