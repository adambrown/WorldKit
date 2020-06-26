package com.grimfox.gec.util

import com.grimfox.gec.*
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.ui.widgets.TextureBuilder
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.buildTextureRedShort
import com.grimfox.gec.ui.widgets.TextureBuilder.buildTextureRgbFloat
import com.grimfox.gec.ui.widgets.TextureBuilder.buildTextureRgbaByte
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRedByte
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRedFloat
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRedShort
import com.grimfox.gec.ui.widgets.TextureBuilder.render
import com.grimfox.gec.ui.widgets.TextureBuilder.renderLandImage
import com.grimfox.gec.ui.widgets.TextureBuilder.renderNormalAndAoRgbaByte
import com.grimfox.gec.ui.widgets.TextureBuilder.renderTrianglesRedFloat
import com.grimfox.gec.util.Biomes.*
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.gec.util.Rendering.renderEdges
import com.grimfox.gec.util.Rendering.renderRegionBorders
import com.grimfox.gec.util.Rendering.renderRegions
import com.grimfox.gec.util.geometry.renderTriangle
import com.grimfox.joml.Matrix4f
import com.grimfox.joml.SimplexNoise.noise
import kotlinx.coroutines.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11.*
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.io.File
import java.nio.ByteBuffer
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import kotlin.collections.ArrayList
import kotlin.math.*

object WaterFlows {

    private val gaussShader = object {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureSizeUniform = ShaderUniform("textureSize")
        val gaussMultiplierUniform = ShaderUniform("gaussMultiplier")
        val vertexPositionsTextureUniformLocation = ShaderUniform("vertexPositions")

        val shaderProgram: TextureBuilder.ShaderProgramId
        init {
            try {
                shaderProgram = TextureBuilder.buildShaderProgram {
                    val vertexShader = compileShader(GL20.GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/gauss.vert"))
                    val fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/gauss.frag"))
                    createAndLinkProgram(
                            listOf(vertexShader, fragmentShader),
                            listOf(positionAttribute),
                            listOf(mvpMatrixUniform, textureSizeUniform, gaussMultiplierUniform, vertexPositionsTextureUniformLocation))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        fun bind(textureSize: Int, gaussMultiplier: Float, vertexPositionMap: TextureId) {
            GL20.glUseProgram(shaderProgram.id)
            GL20.glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            GL20.glUniform1f(textureSizeUniform.location, textureSize.toFloat())
            GL20.glUniform1f(gaussMultiplierUniform.location, gaussMultiplier)
            GL20.glUniform1i(vertexPositionsTextureUniformLocation.location, 0)
            GL20.glActiveTexture(GL20.GL_TEXTURE0)
            GL20.glBindTexture(GL20.GL_TEXTURE_2D, vertexPositionMap.id)
        }
    }

    private const val SIMPLEX_SCALE = 96.0f
    private val threadCount = Runtime.getRuntime().availableProcessors()
    private val lowDictionariesTriplet = preferences.lowDictionaries4!!.value
    private val lowMaskSize = lowDictionariesTriplet.first
    private val lowOffset = lowDictionariesTriplet.second
    private val lowDictionaries = lowDictionariesTriplet.third
    private val highDictionariesTriplet8 = preferences.highDictionaries8!!.value
    private val highMaskSize = highDictionariesTriplet8.first
    private val highOffset = highDictionariesTriplet8.second
    private val highDictionaries4 = preferences.highDictionaries4!!.value.third
    private val highDictionaries8 = highDictionariesTriplet8.third

    class WaterNode constructor(val id: Int,
                                var isExternal: Boolean,
                                val area: Float,
                                val adjacents: ArrayList<Pair<WaterNode, Float>>,
                                val simplexX: Float,
                                val simplexY: Float,
                                var elevationPower: Float,
                                var height: Float,
                                var drainageArea: Float,
                                var biome: Int,
                                var density: Float,
                                val soilMobility: Float,
                                val isPinned: Boolean,
                                var isPeak: Boolean = false) {

        var lake: Int = -1
        var parent: WaterNode = this
        var distanceToParent = 0.0f
        var maxUpstreamLength = 0.0f
        val children: MutableCollection<WaterNode> = LinkedList()
    }

    data class PassKey(val lake1: Int, val lake2: Int)

    class Pass(val passKey: PassKey, val id1: Int, val id2: Int, val height: Float)

    class Masks(
            val biomeMask: Matrix<Byte>,
            val landMask: Matrix<Byte>,
            val underWaterMask: Matrix<Float>,
            val elevationPowerMask: ShortArrayMatrix,
            val startingHeightsMask: ShortArrayMatrix,
            val soilMobilityMask: ShortArrayMatrix,
            val coastalDistanceMask: ShortArrayMatrix)

    class ExportFiles(
            val outputSize: Int = 256,
            val elevationFile: File?,
            val slopeFile: File?,
            val aoFile: File?,
            val normalFile: File?,
            val soilDensityFile: File?,
            val biomeFile: File?,
            val waterFlowFile: File?,
            val peakFile: File?,
            val riverFile: File?,
            val riverSplinesFile: File?,
            val biomeBorderFile: File?,
            val landMaskFile: File?,
            val riverBorderFile: File?,
            val mountainBorderFile: File?,
            val coastalBorderFile: File?,
            val detailIndexFile: File?,
            val objFile: File?)

    private inline fun <T> doOrCancel(canceled: Reference<Boolean>, work: () -> T): T {
        if (!canceled.value) {
            return work()
        } else {
            throw CancellationException()
        }
    }

    fun generateWaterFlows(
            random: Random,
            regionSplines: RegionSplines,
            biomeGraph: Graph,
            biomeMask: Matrix<Byte>,
            flowGraph1: Graph,
            flowGraph2: Graph,
            flowGraph3: Graph,
            flowGraph4: Graph,
            executor: ExecutorService,
            mapScale: Int,
            biomes: List<Biome>,
            customElevationPowerMap: TextureId,
            customStartingHeightsMap: TextureId,
            customSoilMobilityMap: TextureId,
            canceled: Reference<Boolean>,
            biomeTemplates: Biomes,
            renderLevel: Int,
            colorHeightScaleFactor: MutableReference<Float>? = null,
            exportFiles: ExportFiles? = null): Triple<TextureId?, TextureId?, TextureId?> {

        val textureWidth = if (exportFiles == null) {
            VIEWPORT_HEIGHTMAP_SIZE
        } else {
            val minimumSize = kotlin.math.max(1024, exportFiles.outputSize)
            var bestWidth = TextureBuilder.RENDER_WIDTHS.last()
            for (width in TextureBuilder.RENDER_WIDTHS) {
                if (width >= minimumSize) {
                    bestWidth = width
                    break
                }
            }
            bestWidth
        }

        val mapSizeMeters = mapScaleToLinearDistanceMeters(mapScale)
        val heightRangeMeters = mapScaleToHeightRangeMeters(mapScale)
        val renderScale = 1.0f / heightRangeMeters
        val waterDepthMeters = linearDistanceMetersToWaterLevelMeters(mapSizeMeters)
        val shaderTextureScale = ((mapSizeMeters / maxLinearDistanceMeters).coerceIn(0.0f, 1.0f) * 0.9375f) + 0.0625f
        val shaderBorderDistanceScale = ((1.0f - ((mapSizeMeters / maxLinearDistanceMeters).coerceIn(0.0f, 1.0f))) * 0.625f) + 0.375f
        val heightScale = if (shaderTextureScale < 0.25) shaderTextureScale * 4.3f - 0.0441739f else ((ln(shaderTextureScale - 0.21) * 0.4) + 1.59).toFloat()

        val randomSeeds = Array(2) { random.nextLong() }

        val biomeMasksFuture = executor.call {
            val biomeTextureId = doOrCancel(canceled) { renderRegions(textureWidth, biomeGraph, biomeMask) }
            val biomeMap = doOrCancel(canceled) { ByteBufferMatrix(textureWidth, extractTextureRedByte(biomeTextureId, textureWidth)) }
            val biomeBorderTextureId = doOrCancel(canceled) { renderRegionBorders(textureWidth, executor, biomeGraph, biomeMask, threadCount) }
            val landMapTextureId = doOrCancel(canceled) { renderLandImage(textureWidth, regionSplines.coastPoints) }
            val riverBorderTextureId = doOrCancel(canceled) { renderEdges(textureWidth, executor, regionSplines.riverEdges.flatMap { it } + regionSplines.customRiverEdges.flatMap { it }, threadCount) }
            val mountainBorderTextureId = doOrCancel(canceled) { renderEdges(textureWidth, executor, regionSplines.mountainEdges.flatMap { it } + regionSplines.customMountainEdges.flatMap { it }, threadCount) }
            val coastalBorderTextureId = doOrCancel(canceled) { renderEdges(textureWidth, executor, regionSplines.coastEdges.flatMap { it.first + it.second.flatMap { it } }, threadCount) }
            val biomeRegions = doOrCancel(canceled) { buildTriangles(biomeGraph, biomeMask) }
            val elevationPowerTextureId = doOrCancel(canceled) {
                render(textureWidth) { _, dynamicGeometry2D, textureRenderer ->
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
            }
            val startingHeightsTextureId = doOrCancel(canceled) {
                render(textureWidth) { _, dynamicGeometry2D, textureRenderer ->
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
            }
            val soilMobilityTextureId = doOrCancel(canceled) {
                render(textureWidth) { _, dynamicGeometry2D, textureRenderer ->
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
            }
            val underWaterTextureId = doOrCancel(canceled) {
                render(textureWidth) { _, dynamicGeometry2D, textureRenderer ->
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
                    biomeTemplates.UNDER_WATER_BIOME.elevationPowerShader.bind(
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
                    dynamicGeometry2D.render(vertexData, indexData, biomeTemplates.UNDER_WATER_BIOME.elevationPowerShader.positionAttribute)
                    val retVal = textureRenderer.newRedTextureByte(GL_LINEAR, GL_LINEAR)
                    textureRenderer.unbind()
                    retVal
                }
            }
            val elevationMask = doOrCancel(canceled) { ShortArrayMatrix(textureWidth, extractTextureRedShort(elevationPowerTextureId, textureWidth)) }
            val startingHeights = doOrCancel(canceled) { ShortArrayMatrix(textureWidth, extractTextureRedShort(startingHeightsTextureId, textureWidth)) }
            val underWaterMask = doOrCancel(canceled) { FloatArrayMatrix(textureWidth, extractTextureRedFloat(underWaterTextureId, textureWidth)) }
            val landMask = doOrCancel(canceled) { ByteBufferMatrix(textureWidth, extractTextureRedByte(landMapTextureId, textureWidth)) }
            val soilMobilityMask = doOrCancel(canceled) { ShortArrayMatrix(textureWidth, extractTextureRedShort(soilMobilityTextureId, textureWidth)) }
            val coastalDistanceMask = doOrCancel(canceled) { ShortArrayMatrix(textureWidth, extractTextureRedShort(coastalBorderTextureId, textureWidth)) }

            riverBorderTextureId.free()
            mountainBorderTextureId.free()
            coastalBorderTextureId.free()
            biomeTextureId.free()
            biomeBorderTextureId.free()
            elevationPowerTextureId.free()
            startingHeightsTextureId.free()
            Masks(biomeMap, landMask, underWaterMask, elevationMask, startingHeights, soilMobilityMask, coastalDistanceMask)
        }
        val regionDataFuture = executor.call {
            doOrCancel(canceled) { buildRegionData(flowGraph1, biomeMasksFuture.value.landMask) }
        }
        val bootstrapWaterMapsFuture = executor.call {
            val (nodeIndex, nodes, rivers) = doOrCancel(canceled) { bootstrapUnderWaterErosion(executor, flowGraph1, regionDataFuture.value, biomeMasksFuture.value.underWaterMask, biomeMasksFuture.value.soilMobilityMask, mapSizeMeters, waterDepthMeters, Random(randomSeeds[1]), biomeTemplates) }
            doOrCancel(canceled) { performErosion(canceled, executor, flowGraph1, null, nodeIndex, nodes, rivers, 40, listOf(biomeTemplates.UNDER_WATER_BIOME), listOf(biomeTemplates.UNDER_WATER_BIOME.lowPassSettings), 1024, mapSizeMeters, waterDepthMeters, textureWidth, null, 0.0f, biomeTemplates) }
        }
        val mapsFuture1 = executor.call {
            val preAmplifiedWidth = if (renderLevel == 0) {
                textureWidth
            } else {
                8192
            }

            val (nodeIndex, nodes, rivers) = doOrCancel(canceled) { bootstrapErosion(canceled, executor, flowGraph1, regionDataFuture.value, biomes, biomeMasksFuture.value.biomeMask, biomeMasksFuture.value.elevationPowerMask, biomeMasksFuture.value.startingHeightsMask, biomeMasksFuture.value.soilMobilityMask, mapSizeMeters, Random(randomSeeds[1]), biomeTemplates) }
            val underWaterMask = bootstrapWaterMapsFuture.value.heightMap
            val exportRivers = exportFiles?.waterFlowFile != null
            val returnRivers = renderLevel == 0 && (exportRivers || exportFiles == null)
            doOrCancel(canceled) { performErosion(canceled, executor, flowGraph1, biomeMasksFuture.value.biomeMask, nodeIndex, nodes, rivers, 150, biomes, biomes.map { it.lowPassSettings }, preAmplifiedWidth, mapSizeMeters, waterDepthMeters, textureWidth, underWaterMask, -waterDepthMeters, biomeTemplates, returnRivers, gaussRender = true, gaussMultiplier = if (renderLevel == 0) 0.56f else 2.0f) }
        }
        val mapsFuture2 = buildMapsFuture(1, canceled, executor, renderLevel, textureWidth, mapSizeMeters, waterDepthMeters, biomeTemplates, biomes, biomeMasksFuture, flowGraph2, exportFiles, mapsFuture1)
        val mapsFuture3 = buildMapsFuture(2, canceled, executor, renderLevel, textureWidth, mapSizeMeters, waterDepthMeters, biomeTemplates, biomes, biomeMasksFuture, flowGraph3, exportFiles, mapsFuture2)
        val mapsFuture4 = buildMapsFuture(3, canceled, executor, renderLevel, textureWidth, mapSizeMeters, waterDepthMeters, biomeTemplates, biomes, biomeMasksFuture, flowGraph3, exportFiles, mapsFuture2)
        val mapsFuture5 = if (renderLevel > 3) {
            val factor = when (renderLevel) {
                5 -> 4
                6 -> 8
                7 -> 4
                8 -> 8
                else -> 1
            }
            val preAmplifiedWidth = if (renderLevel > 4) {
                if (exportFiles?.outputSize != null) {
                    exportFiles.outputSize / factor + if (exportFiles.outputSize % factor != 0) 1 else 0
                } else {
                    when (renderLevel) {
                        5 -> 4096
                        6 -> 4096
                        7 -> 8192
                        8 -> 8192
                        else -> textureWidth
                    }
                }
            } else {
                textureWidth
            }
            val highWaterNodesFuture = executor.call {
                doOrCancel(canceled) { prepareGraphNodesUnderWater(canceled, executor, flowGraph4, biomeMasksFuture.value.landMask, biomeMasksFuture.value.soilMobilityMask, mapSizeMeters) }
            }
            val highNodesFuture = executor.call {
                doOrCancel(canceled) { prepareGraphNodes(canceled, executor, flowGraph4, biomeMasksFuture.value.landMask, biomeMasksFuture.value.soilMobilityMask, mapSizeMeters) }
            }
            val highWaterMapsFuture = buildWaterMapsFuture(canceled, executor, textureWidth, preAmplifiedWidth, mapSizeMeters, waterDepthMeters, biomeTemplates, flowGraph4, biomeTemplates.UNDER_WATER_BIOME.highPassSettings, highWaterNodesFuture, mapsFuture3!!)
            val highMapsFuture = executor.call {
                val smallHeightMap = mapsFuture3.value.heightMap
                val (nodeIndex, nodes, rivers) = highNodesFuture.value
                val erosionSettings = doOrCancel(canceled) { biomes.map { it.highPassSettings } }
                doOrCancel(canceled) { applyMapsToNodes(executor, flowGraph4.vertices, smallHeightMap, biomeMasksFuture.value.elevationPowerMask, biomeMasksFuture.value.startingHeightsMask, erosionSettings, biomeMasksFuture.value.biomeMask, nodes) }
                val underWaterMask = highWaterMapsFuture.value.heightMap
                val exportRivers = exportFiles?.waterFlowFile != null
                val returnRivers = exportRivers || exportFiles == null
                val returnSoilDensity = exportFiles?.soilDensityFile != null
                val returnPeaks = exportFiles?.peakFile != null
                val returnRiverLines = exportFiles?.riverFile != null
                val returnRiverSplines = exportFiles?.riverSplinesFile != null

                val erosionResult = doOrCancel(canceled) {
                    performErosion(canceled, executor, flowGraph4, biomeMasksFuture.value.biomeMask, nodeIndex, nodes, rivers, 30, biomes, erosionSettings, preAmplifiedWidth, mapSizeMeters, waterDepthMeters, textureWidth, underWaterMask, -waterDepthMeters, biomeTemplates, returnRivers, returnSoilDensity, returnPeaks, returnRiverLines, returnRiverSplines, exportRivers, exportFiles?.objFile, preAmplifiedWidth, min(8192, exportFiles?.outputSize ?: textureWidth), min(8192, exportFiles?.outputSize ?: 4096), true)
                }

                if (renderLevel > 4) {
                    val (input, minWaterValue, maxLandValue) = combineHeightMapsToRcMatrix(erosionResult.heightMap, biomeMasksFuture.value.coastalDistanceMask, preAmplifiedWidth,  waterDepthMeters, renderScale,20, 10, 2.0f, -2.0f)
                    val dictionaryWidth = (input.rows + highMaskSize) / highOffset
                    val inputIndexMask = buildBiomeIndexMask(erosionResult.heightMap, biomeMasksFuture.value.landMask, biomeMasksFuture.value.biomeMask, biomes, biomeTemplates, dictionaryWidth)
                    val (amplified, min, outputScale) = TerrainAmplification.amplify(factor, input, inputIndexMask, highMaskSize, highOffset, if (factor == 4) highDictionaries4 else highDictionaries8)
                    val offset = highMaskSize * factor
                    val columns = input.columns * factor
                    val amplifiedHeightMap = FloatArrayMatrix(columns)
                    val colRange = offset until offset + columns
                    (offset until offset + columns).toList().parallelStream().forEach { row ->
                        val rowOff = (row - offset) * columns
                        for (column in colRange) {
                            amplifiedHeightMap[rowOff + column - offset] = amplified[row, column] * heightRangeMeters - waterDepthMeters
                        }
                    }
                    ErosionResult(amplifiedHeightMap, erosionResult.flowMap, erosionResult.soilDensityMap, erosionResult.peakLines, erosionResult.riverLines, erosionResult.riverSplines)
                } else {
                    erosionResult
                }
            }
            highMapsFuture
        } else {
            null
        }
        val mapsFuture = when (renderLevel) {
            0 -> mapsFuture1
            1 -> mapsFuture2!!
            2 -> mapsFuture3!!
            3 -> mapsFuture4!!
            else -> mapsFuture5!!
        }
        val firstDeferred = doOrCancel(canceled) {
            task {
                val heightMapAsShortArray = if (exportFiles == null || exportFiles.elevationFile != null || exportFiles.slopeFile != null || exportFiles.normalFile != null || exportFiles.aoFile != null) {
                    writeHeightMapAsShortArray(mapsFuture.value.heightMap, normalize = false, min = -waterDepthMeters, scale = renderScale, heightScaleFactor = colorHeightScaleFactor)
                } else {
                    null
                }

                val textureId = if ((exportFiles == null && heightMapAsShortArray != null) || (exportFiles != null && heightMapAsShortArray != null && (exportFiles.slopeFile != null || exportFiles.normalFile != null || exportFiles.aoFile != null))) {
                    heightMapToTextureId(heightMapAsShortArray, mapsFuture.value.heightMap.width)
                } else {
                    null
                }
                Triple(textureId, heightMapAsShortArray, mapsFuture.value.heightMap.width)
            }
        }

        val flowMap = mapsFuture.value.flowMap ?: FloatArrayMatrix(1)
        val secondDeferred = doOrCancel(canceled) {
            task {
                val flowMapAsShortArray = if (exportFiles == null || exportFiles.waterFlowFile != null) {
                    writeHeightMapAsShortArray(flowMap, null, 0.0f)
                } else {
                    null
                }
                val textureId = if (exportFiles == null && flowMapAsShortArray != null) {
                    heightMapToTextureId(flowMapAsShortArray, flowMap.width)
                } else {
                    null
                }
                textureId to flowMapAsShortArray
            }
        }

        val densityMap = mapsFuture.value.soilDensityMap
        val thirdDeferred = if (densityMap != null) {
            doOrCancel(canceled) {
                task {
                    if (exportFiles == null || exportFiles.soilDensityFile != null) {
                        writeHeightMapAsShortArray(densityMap, normalize = false)
                    } else {
                        null
                    }
                }
            }
        } else {
            null
        }
        val fourthDeferred = if (exportFiles == null || exportFiles.aoFile != null || exportFiles.normalFile != null) {
            doOrCancel(canceled) {
                task {
                    runBlocking {
                        val first = firstDeferred.await()
                        val heightMapTextureId = first.first
                        if (heightMapTextureId != null) {
                            val textureSize =  min(8192, exportFiles?.outputSize ?: VIEWPORT_HEIGHTMAP_SIZE)
                            val uvScale = mapSizeMeters / textureSize
                            val normalAndAoAsBytes = renderNormalAndAoRgbaByte(textureWidth, heightMapTextureId, heightRangeMeters, uvScale, QuadFloat(0.5f, 0.5f, 1.0f, 1.0f))
                            val normalAoTextureId = normalAndAoToTextureId(normalAndAoAsBytes, textureSize)
                            normalAoTextureId to normalAndAoAsBytes
                        } else {
                            null
                        }
                    }
                }
            }
        } else {
            null
        }
        val fifthDeferred = if (exportFiles?.biomeFile != null) {
            doOrCancel(canceled) {
                task {
                    runBlocking {
                        val outputSize = min(8192, exportFiles.outputSize)
                        val biomeTextureId = doOrCancel(canceled) { renderRegions(textureWidth, biomeGraph, biomeMask, scale = renderScale) }
                        val biomeMap = doOrCancel(canceled) { extractTextureRedByte(biomeTextureId, textureWidth) }
                        val landMapTextureId = doOrCancel(canceled) { renderLandImage(textureWidth, regionSplines.coastPoints, scale = renderScale) }
                        val landMap = doOrCancel(canceled) { extractTextureRedByte(landMapTextureId, textureWidth) }
                        for (y in 0 until outputSize) {
                            val yOff = y * textureWidth
                            for (x in 0 until outputSize) {
                                val index = yOff + x
                                val land = landMap[index].toInt() and 0xFF
                                val biome = biomeMap[index].toInt() and 0xFF
                                if (land > 0) {
                                    biomeMap.put(index, (biome + 1).toByte())
                                } else {
                                    biomeMap.put(index, 0.toByte())
                                }
                            }
                        }
                        biomeMap
                    }
                }
            }
        } else {
            null
        }
        val sixthDeferred = if (exportFiles?.detailIndexFile != null) {
            doOrCancel(canceled) {
                task {
                    runBlocking {
                        val outputSize = min(8192, exportFiles.outputSize)
                        val biomeTextureId = doOrCancel(canceled) { renderRegions(textureWidth, biomeGraph, biomeMask, scale = renderScale) }
                        val biomeMap = doOrCancel(canceled) { extractTextureRedByte(biomeTextureId, textureWidth) }
                        val landMapTextureId = doOrCancel(canceled) { renderLandImage(textureWidth, regionSplines.coastPoints, scale = renderScale) }
                        val landMap = doOrCancel(canceled) { extractTextureRedByte(landMapTextureId, textureWidth) }
                        val heightMap = mapsFuture.value.heightMap
                        writeAugmentedBiomeMapAsByteBuffer(heightMap, ByteBufferMatrix(outputSize, landMap), ByteBufferMatrix(outputSize, biomeMap), biomes, biomeTemplates)
                    }
                }
            }
        } else {
            null
        }
        return runBlocking {
            val first = firstDeferred.await()
            val second = secondDeferred.await()
            val third = thirdDeferred?.await()
            val fourth = fourthDeferred?.await()
            val fifth = fifthDeferred?.await()
            val sixth = sixthDeferred?.await()
            val task1 = doOrCancel(canceled) {
                task {
                    exportFiles?.elevationFile?.exportMap16Bit(exportFiles.outputSize, first.second, first.third)
                }
            }
            val task2 = doOrCancel(canceled) {
                task {
                    exportFiles?.waterFlowFile?.exportMap16Bit(min(8192, exportFiles.outputSize), second?.second, textureWidth)
                }
            }
            val task3 = doOrCancel(canceled) {
                task {
                    exportFiles?.soilDensityFile?.exportMap16Bit(min(8192, exportFiles.outputSize), third, textureWidth)
                }
            }
            val task4 = doOrCancel(canceled) {
                task {
                    exportFiles?.normalFile?.exportMap8BitRGB(min(8192, exportFiles.outputSize), fourth?.second, textureWidth)
                }
            }
            val task5 = doOrCancel(canceled) {
                task {
                    exportFiles?.aoFile?.exportMap8BitA(min(8192, exportFiles.outputSize), fourth?.second, textureWidth)
                }
            }
            val task6 = doOrCancel(canceled) {
                task {
                    exportFiles?.biomeFile?.exportMap8BitGrey(min(8192, exportFiles.outputSize), fifth, textureWidth)
                }
            }
            val task7 = doOrCancel(canceled) {
                task {
                    exportFiles?.peakFile?.exportMap16Bit(min(8192, exportFiles.outputSize), mapsFuture.value.peakLines?.array, textureWidth)
                }
            }
            val task8 = doOrCancel(canceled) {
                task {
                    exportFiles?.riverFile?.exportMap16Bit(min(8192, exportFiles.outputSize), mapsFuture.value.riverLines?.array, textureWidth)
                }
            }
            val task9 = doOrCancel(canceled) {
                task {
                    if (exportFiles?.biomeBorderFile != null) {
                        val scale = min(8192, exportFiles.outputSize) / textureWidth.toFloat()
                        val biomeBorderTextureId = doOrCancel(canceled) { renderRegionBorders(textureWidth, executor, biomeGraph, biomeMask, threadCount, scale) }
                        val biomeBorderMap = doOrCancel(canceled) { ShortArrayMatrix(textureWidth, extractTextureRedShort(biomeBorderTextureId, textureWidth)) }
                        exportFiles.biomeBorderFile.exportMap16Bit(min(8192, exportFiles.outputSize), biomeBorderMap.array, textureWidth)
                    }
                }
            }
            val task10 = doOrCancel(canceled) {
                task {
                    if (exportFiles?.landMaskFile != null) {
                        val scale = min(8192, exportFiles.outputSize) / textureWidth.toFloat()
                        val landMapTextureId = doOrCancel(canceled) { renderLandImage(textureWidth, regionSplines.coastPoints, scale) }
                        val landMap = doOrCancel(canceled) { ByteBufferMatrix(textureWidth, extractTextureRedByte(landMapTextureId, textureWidth)) }
                        exportFiles.landMaskFile.exportMap8BitGrey(min(8192, exportFiles.outputSize), landMap.buffer, textureWidth)
                    }
                }
            }
            val task11 = doOrCancel(canceled) {
                task {
                    if (exportFiles?.riverBorderFile != null) {
                        val scale = min(8192, exportFiles.outputSize) / textureWidth.toFloat()
                        val riverBorderTextureId = doOrCancel(canceled) { renderEdges(textureWidth, executor, regionSplines.riverEdges.flatMap { it } + regionSplines.customRiverEdges.flatMap { it }, threadCount, scale) }
                        val riverBorderMap = doOrCancel(canceled) { ShortArrayMatrix(textureWidth, extractTextureRedShort(riverBorderTextureId, textureWidth)) }
                        exportFiles.riverBorderFile.exportMap16Bit(min(8192, exportFiles.outputSize), riverBorderMap.array, textureWidth)
                    }
                }
            }
            val task12 = doOrCancel(canceled) {
                task {
                    if (exportFiles?.mountainBorderFile != null) {
                        val scale = min(8192, exportFiles.outputSize) / textureWidth.toFloat()
                        val mountainBorderTextureId = doOrCancel(canceled) { renderEdges(textureWidth, executor, regionSplines.mountainEdges.flatMap { it } + regionSplines.customMountainEdges.flatMap { it }, threadCount, scale) }
                        val mountainBorderMap = doOrCancel(canceled) { ShortArrayMatrix(textureWidth, extractTextureRedShort(mountainBorderTextureId, textureWidth)) }
                        exportFiles.mountainBorderFile.exportMap16Bit(min(8192, exportFiles.outputSize), mountainBorderMap.array, textureWidth)
                    }
                }
            }
            val task13 = doOrCancel(canceled) {
                task {
                    if (exportFiles?.coastalBorderFile != null) {
                        val scale = min(8192, exportFiles.outputSize) / textureWidth.toFloat()
                        val coastalBorderTextureId = doOrCancel(canceled) { renderEdges(textureWidth, executor, regionSplines.coastEdges.flatMap { it.first + it.second.flatMap { it } }, threadCount, scale) }
                        val coastalBorderMap = doOrCancel(canceled) { ShortArrayMatrix(textureWidth, extractTextureRedShort(coastalBorderTextureId, textureWidth)) }
                        exportFiles.coastalBorderFile.exportMap16Bit(min(8192, exportFiles.outputSize), coastalBorderMap.array, textureWidth)
                    }
                }
            }
            val task14 = doOrCancel(canceled) {
                task {
                    exportFiles?.detailIndexFile?.exportMap8BitGrey(min(8192, exportFiles.outputSize), sixth, textureWidth)
                }
            }
            val task15 = doOrCancel(canceled) {
                task {
                    exportFiles?.riverSplinesFile?.exportPolyLines(mapsFuture.value.riverSplines, exportFiles.outputSize)
                }
            }

            task1.await()
            task2.await()
            task3.await()
            task4.await()
            task5.await()
            task6.await()
            task7.await()
            task8.await()
            task9.await()
            task10.await()
            task11.await()
            task12.await()
            task13.await()
            task14.await()
            task15.await()
            Triple(first.first, second.first, fourth?.first)
        }
    }

    private fun buildMapsFuture(
            level: Int,
            canceled: Reference<Boolean>,
            executor: ExecutorService,
            renderLevel: Int,
            textureWidth: Int,
            mapSizeMeters: Float,
            waterDepthMeters: Float,
            biomeTemplates: Biomes,
            biomes: List<Biome>,
            biomeMasksFuture: Future<Masks>,
            flowGraph: Graph,
            exportFiles: ExportFiles?,
            previousMapsFuture: Future<ErosionResult>?): Future<ErosionResult>? {
        if (renderLevel < level || previousMapsFuture == null) {
            return null
        }
        val preAmplifiedWidth = if (renderLevel == level) {
            textureWidth
        } else {
            8192
        }
        val waterNodesFuture = executor.call {
            doOrCancel(canceled) { prepareGraphNodesUnderWater(canceled, executor, flowGraph, biomeMasksFuture.value.landMask, biomeMasksFuture.value.soilMobilityMask, mapSizeMeters) }
        }
        val landNodesFuture = executor.call {
            doOrCancel(canceled) { prepareGraphNodes(canceled, executor, flowGraph, biomeMasksFuture.value.landMask, biomeMasksFuture.value.soilMobilityMask, mapSizeMeters) }
        }
        val waterMapsFuture = buildWaterMapsFuture(canceled, executor, textureWidth, preAmplifiedWidth, mapSizeMeters, waterDepthMeters, biomeTemplates, flowGraph, biomeTemplates.UNDER_WATER_BIOME.midPassSettings, waterNodesFuture, previousMapsFuture)
        return executor.call {
            val smallHeightMap = previousMapsFuture.value.heightMap
            val (nodeIndex, nodes, rivers) = landNodesFuture.value
            val erosionSettings = doOrCancel(canceled) { biomes.map { it.midPassSettings } }
            doOrCancel(canceled) { applyMapsToNodes(executor, flowGraph.vertices, smallHeightMap, biomeMasksFuture.value.elevationPowerMask, biomeMasksFuture.value.startingHeightsMask, erosionSettings, biomeMasksFuture.value.biomeMask, nodes) }
            val underWaterMask = waterMapsFuture.value.heightMap
            val exportRivers = exportFiles?.waterFlowFile != null
            val returnRivers = renderLevel == level && (exportRivers || exportFiles == null)
            doOrCancel(canceled) { performErosion(canceled, executor, flowGraph, biomeMasksFuture.value.biomeMask, nodeIndex, nodes, rivers, 30, biomes, erosionSettings, preAmplifiedWidth, mapSizeMeters, waterDepthMeters, textureWidth, underWaterMask, -waterDepthMeters, biomeTemplates, returnRivers, gaussRender = true) }
        }
    }

    private fun buildWaterMapsFuture(
            canceled: Reference<Boolean>,
            executor: ExecutorService,
            textureWidth: Int,
            preAmplifiedWidth: Int,
            mapSizeMeters: Float,
            waterDepthMeters: Float,
            biomeTemplates: Biomes,
            flowGraph: Graph,
            erosionSettings: ErosionSettings,
            waterNodesFuture: Future<Quintuple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>, ArrayList<Int>, LinkedHashSet<Int>>>,
            previousMapsFuture: Future<ErosionResult>): Future<ErosionResult> {
        return executor.call {
            val heightMap = previousMapsFuture.value.heightMap
            val (nodeIndex, nodes, rivers, water, border) = waterNodesFuture.value
            doOrCancel(canceled) { applyMapsToUnderWaterNodes(executor, flowGraph.vertices, heightMap, nodes, waterDepthMeters) }
            val unused = LinkedHashSet(water)
            val used = LinkedHashSet(border)
            unused.removeAll(used)
            val next = LinkedHashSet(border)
            var lastUnusedCount = unused.size
            while (unused.isNotEmpty()) {
                doOrCancel(canceled) {
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
                        val makeSink = unused.asSequence().map { nodeIndex[it]!! }.filter { !it.isPinned }.toList().minBy { it.height }!!
                        makeSink.isExternal = true
                        rivers.add(makeSink)
                        used.add(makeSink.id)
                        unused.remove(makeSink.id)
                        next.add(makeSink.id)
                    }
                    lastUnusedCount = unused.size
                }
            }
            doOrCancel(canceled) { performErosion(canceled, executor, flowGraph, null, nodeIndex, nodes, rivers, 12, listOf(biomeTemplates.UNDER_WATER_BIOME), listOf(erosionSettings), preAmplifiedWidth, mapSizeMeters, waterDepthMeters, textureWidth, null, 0.0f, biomeTemplates) }
        }
    }

    private fun lerpInputs(rowOrColumn: Int, scale: Float, maxRowOrColumn: Int): Quadruple<Int, Int, Float, Float> {
        val colF = rowOrColumn * scale
        val index1 = clamp(round(floor(colF.toDouble())).toInt(), 0, maxRowOrColumn)
        val colAlpha = clamp(colF - index1, 0.0f, 1.0f)
        return Quadruple(index1, clamp(index1 + 1, 0, maxRowOrColumn), colAlpha, 1 - colAlpha)
    }

    private fun bilerp(texture: Matrix<Short>, rowLerps: Quadruple<Int, Int, Float, Float>, colLerps: Quadruple<Int, Int, Float, Float>) =
            (((texture[colLerps.first, rowLerps.first].toInt() and 0xFFFF) * rowLerps.fourth + (texture[colLerps.first, rowLerps.second].toInt() and 0xFFFF) * rowLerps.third) * colLerps.fourth) +
                    (((texture[colLerps.second, rowLerps.first].toInt() and 0xFFFF) * rowLerps.fourth + (texture[colLerps.second, rowLerps.second].toInt() and 0xFFFF) * rowLerps.third) * colLerps.third)

    private fun bilerp(texture: RcMatrix, rowLerps: Quadruple<Int, Int, Float, Float>, colLerps: Quadruple<Int, Int, Float, Float>) =
            ((texture[rowLerps.first, colLerps.first] * colLerps.fourth + texture[rowLerps.first, colLerps.second] * colLerps.third) * rowLerps.fourth) +
                    ((texture[rowLerps.second, colLerps.first] * colLerps.fourth + texture[rowLerps.second, colLerps.second] * colLerps.third) * rowLerps.third)

    private fun buildRegionData(graph: Graph, landMask: Matrix<Byte>): RegionData {
        val vertices = graph.vertices
        val land = ArrayList<Int>(vertices.size)
        val water = LinkedHashSet<Int>(vertices.size)
        val landMaskWidth = landMask.width
        val landMaskWidthM1 = landMaskWidth - 1
        for (i in 0 until vertices.size) {
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

    private fun bootstrapUnderWaterErosion(executor: ExecutorService, graph: Graph, regionData: RegionData, heightMap: Matrix<Float>, soilMobilityMap: Matrix<Short>, distanceScale: Float, waterDepthMeters: Float, random: Random, biomeTemplates: Biomes): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
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
        val (nodeIndex, nodes) = createWaterNodes(executor, vertices, water, border, heightMap, soilMobilityMap, distanceScale, waterDepthMeters, coast)
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
        computeHeights(executor, rivers, listOf(biomeTemplates.UNDER_WATER_BIOME), listOf(ErosionSettings(1.0f, 1.0f, 0.001f)), biomeTemplates)
        return Triple(nodeIndex, nodes, rivers)
    }

    private fun bootstrapErosion(canceled: Reference<Boolean>, executor: ExecutorService, graph: Graph, regionData: RegionData, biomes: List<Biome>, biomeMask: Matrix<Byte>, elevationMask: Matrix<Short>, startingHeights: Matrix<Short>, soilMobilityMap: Matrix<Short>, distanceScale: Float, random: Random, biomeTemplates: Biomes): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        fun <T> doOrCancel(work: () -> T): T {
            if (!canceled.value) {
                return work()
            } else {
                throw CancellationException()
            }
        }

        val vertices = graph.vertices
        val land = regionData.land
        val beach = regionData.beach
        val (nodeIndex, nodes) = doOrCancel { createWaterNodes(canceled, executor, vertices, land, beach, biomes, biomeMask, elevationMask, startingHeights, soilMobilityMap, distanceScale) }
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
                doOrCancel {
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
        }
        doOrCancel { computeAreas(executor, rivers) }
        val bootstrapErosion = ErosionSettings(1.0f, 1.0f, 1.0f)
        doOrCancel { computeHeights(executor, rivers, biomes, biomes.map { bootstrapErosion }, biomeTemplates) }
        return Triple(nodeIndex, nodes, rivers)
    }

    private data class ErosionResult(
            val heightMap: FloatArrayMatrix,
            val flowMap: FloatArrayMatrix? = null,
            val soilDensityMap: FloatArrayMatrix? = null,
            val peakLines: ShortArrayMatrix? = null,
            val riverLines: ShortArrayMatrix? = null,
            val riverSplines: List<List<Point3F>>? = null)

    private fun performErosion(
            canceled: Reference<Boolean>,
            executor: ExecutorService,
            graph: Graph,
            biomeMask: Matrix<Byte>?,
            nodeIndex: Array<WaterNode?>,
            nodes: ArrayList<WaterNode>,
            rivers: ArrayList<WaterNode>,
            iterations: Int,
            biomes: List<Biome>,
            erosionSettings: List<ErosionSettings>,
            heightMapWidth: Int,
            mapSizeMeters: Float,
            waterDepthMeters: Float,
            textureWidth: Int,
            fallback: Matrix<Float>? = null,
            defaultValue: Float = 0.0f,
            biomeTemplates: Biomes,
            returnRivers: Boolean = false,
            returnSoilDensity: Boolean = false,
            returnPeaks: Boolean = false,
            returnRiverLines: Boolean = false,
            returnRiverSplines: Boolean = false,
            forExport: Boolean = false,
            objFile: File? = null,
            outputWidth: Int = heightMapWidth,
            outputSupplementalWidth: Int = heightMapWidth,
            riverMapWidth: Int = outputSupplementalWidth,
            gaussRender: Boolean = false,
            gaussMultiplier: Float = 0.56f): ErosionResult {
        fun <T> doOrCancel(work: () -> T): T {
            if (!canceled.value) {
                return work()
            } else {
                throw CancellationException()
            }
        }

        val lakes = ArrayList<WaterNode>()
        val passes = LinkedHashMap<PassKey, Pass>()
        val prepareNodesAndLakesTimer = AtomicLong(0)
        val computeLakeConnectionsTimer = AtomicLong(0)
        val computeAreasTimer = AtomicLong(0)
        val computeHeightsTimer = AtomicLong(0)
        timeIt("performErosion") {
            for (i in 0 until iterations) {
                lakes.clear()
                passes.clear()
                doOrCancel { timeIt(prepareNodesAndLakesTimer) { prepareNodesAndLakes(executor, lakes, nodes, rivers) } }
                doOrCancel { timeIt(computeLakeConnectionsTimer) { computeLakeConnections(canceled, graph.vertices, lakes, nodeIndex, passes, rivers) } }
                doOrCancel { timeIt(computeAreasTimer) { computeAreas(executor, rivers) } }
                doOrCancel { timeIt(computeHeightsTimer) { computeHeights(executor, rivers, biomes, erosionSettings, biomeTemplates) } }
            }
        }
        println("""performErosion:
  prepareNodesAndLakes: ${(prepareNodesAndLakesTimer.get() / 1000000) / 1000.0f}s
  computeLakeConnections: ${(computeLakeConnectionsTimer.get() / 1000000) / 1000.0f}s
  computeAreas: ${(computeAreasTimer.get() / 1000000) / 1000.0f}s
  computeHeights: ${(computeHeightsTimer.get() / 1000000) / 1000.0f}s""")
        var riverMapDeferred: Deferred<FloatArrayMatrix>? = null
        var densityMapDeferred: Deferred<FloatArrayMatrix>? = null
        var riverLinesDeferred: Deferred<Pair<ShortArrayMatrix, List<List<Point3F>>>>? = null
        var peakMapDeferred: Deferred<ShortArrayMatrix>? = null

        if (returnRivers) {
            riverMapDeferred = doOrCancel { task { renderHeightMap(executor, graph, nodeIndex, null, 0.0f, riverMapWidth, riverMapWidth, threadCount, waterDepthMeters, if (forExport) 1.0f else 0.5f) { drainageArea } } }
        }
        if (returnRiverLines || returnRiverSplines) {
            riverLinesDeferred = doOrCancel {
                task {
                    val vertices = graph.vertices
                    var numFlows = 0
                    var sumFlows = 0.0
                    nodes.forEach {
                        sumFlows += it.drainageArea
                        numFlows++
                    }
                    val avgFlow = (sumFlows / numFlows).toFloat()
                    numFlows = 0
                    sumFlows = 0.0
                    nodes.forEach {
                        if (it.drainageArea > avgFlow) {
                            sumFlows += it.drainageArea
                            numFlows++
                        }
                    }
                    val flowThreshold = (sumFlows / numFlows).toFloat() * 0.1f
                    val riverLines = ArrayList<LineSegment2F>()
                    var numRiversWithLength = 0
                    var sumRiverLengths = 0.0
                    val rootsToConsider = ArrayList<WaterNode>()
                    nodes.forEach {
                        if (it.id == it.parent.id) {
                            recurseCalcMaxUpstreamLengths(it, flowThreshold)
                            if (it.maxUpstreamLength > 0.0f) {
                                numRiversWithLength++
                                sumRiverLengths += it.maxUpstreamLength
                                println("maxUpstream: ${it.maxUpstreamLength}")
                                rootsToConsider.add(it)
                            }
                        }
                    }
                    val avgUpstreamLength = (sumRiverLengths / numRiversWithLength).toFloat()
                    numRiversWithLength = 0
                    sumRiverLengths = 0.0
                    rootsToConsider.forEach {
                        if (it.maxUpstreamLength > avgUpstreamLength) {
                            sumRiverLengths += it.maxUpstreamLength
                            numRiversWithLength++
                        }
                    }
                    val riverLengthThreshold = (sumRiverLengths / numRiversWithLength).toFloat() * 0.8f
                    val riverPolyLines = ArrayList<MutableList<Point3F>>()
                    rootsToConsider.forEach {
                        if (it.maxUpstreamLength > riverLengthThreshold) {
                            val currentPolyLine = ArrayList<Point3F>()
                            riverPolyLines.add(currentPolyLine)
                            recurseAddRiverLines(it, flowThreshold, riverLengthThreshold * 0.4f, vertices, riverLines, riverPolyLines, currentPolyLine)
                            if (currentPolyLine.size < 2) {
                                riverPolyLines.remove(currentPolyLine)
                            }
                        }
                    }
                    println("riverLengthThreshold: $riverLengthThreshold")
                    val textureId = renderEdges(outputSupplementalWidth, executor, riverLines, threadCount)
                    ShortArrayMatrix(outputSupplementalWidth, extractTextureRedShort(textureId, outputSupplementalWidth)) to riverPolyLines
                }
            }
        }
        if (returnSoilDensity) {
            densityMapDeferred = doOrCancel { task { renderHeightMap(executor, graph, nodeIndex, null, 0.0f, heightMapWidth, outputSupplementalWidth, threadCount, waterDepthMeters, 1.0f) { density / 65536.0f } } }
        }
        if (returnPeaks) {
            peakMapDeferred = doOrCancel {
                task {
                    nodes.forEach {
                        val nodeHeight = it.height
                        if (nodeHeight > 205.0f) {
                            val closePoints = graph.getClosePoints(it.id, 0.007f, false)
                            var isPeak = true
                            for (closePoint in closePoints) {
                                if (closePoint == it.id) {
                                    continue
                                }
                                val node = nodeIndex[closePoint]
                                if (node != null && node.height > nodeHeight) {
                                    isPeak = false
                                    break
                                }
                            }
                            if (isPeak) {
                                it.isPeak = true
                            }
                        }
                    }
                    renderPeakMap(executor, graph, nodeIndex, heightMapWidth, outputSupplementalWidth, textureWidth, threadCount) { if (isPeak) height else 0.0f }
                }
            }
        }
        val heightMap = if (!gaussRender || fallback == null) {
            doOrCancel { renderHeightMap(executor, graph, nodeIndex, fallback, defaultValue, heightMapWidth, outputWidth, threadCount, waterDepthMeters) }
        } else {
            doOrCancel { gaussRenderHeightMap(graph, nodeIndex, fallback, outputWidth, waterDepthMeters, multiplier = gaussMultiplier) }
        }

        if (objFile != null && (objFile.canWrite() || (!objFile.exists() && objFile.parentFile.isDirectory && objFile.parentFile.canWrite()))) {
            writeObj(executor, graph, nodeIndex, fallback, threadCount, objFile, mapSizeMeters, waterDepthMeters)
        }
        val biomeExtremes = Array(erosionSettings.size) { Pair(mRef(Float.MAX_VALUE), mRef(-Float.MAX_VALUE)) }
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
            doOrCancel { applyTerracing(executor, heightMap, biomeMask, erosionSettings, biomeExtremes, threadCount) }
        }
        return runBlocking {
            ErosionResult(
                    heightMap = heightMap,
                    flowMap = if (returnRivers) riverMapDeferred?.await() else null,
                    soilDensityMap = if (returnSoilDensity) densityMapDeferred?.await() else null,
                    peakLines = if (returnPeaks) peakMapDeferred?.await() else null,
                    riverLines = if (returnRiverLines) riverLinesDeferred?.await()?.first else null,
                    riverSplines = if (returnRiverSplines) riverLinesDeferred?.await()?.second else null)
        }
    }

    private fun recurseCalcMaxUpstreamLengths(node: WaterNode, flowThreshold: Float): Float {
        return if (node.id == node.parent.id || node.drainageArea > flowThreshold) {
            if (node.children.isEmpty()) {
                node.distanceToParent
                node.maxUpstreamLength = node.distanceToParent
                node.maxUpstreamLength
            } else {
                val maxChildLength = node.children.map {
                    recurseCalcMaxUpstreamLengths(it, flowThreshold)
                }.max()
                node.maxUpstreamLength = node.distanceToParent + maxChildLength!!
                node.maxUpstreamLength
            }
        } else {
            node.maxUpstreamLength = 0.0f
            node.maxUpstreamLength
        }
    }

    private fun recurseAddRiverLines(node: WaterNode, flowThreshold: Float, leafThreshold: Float, vertices: Vertices, riverLines: MutableList<LineSegment2F>, riverPolyLines: MutableList<MutableList<Point3F>>, currentPolyLine: MutableList<Point3F>) {
        if (node.drainageArea > flowThreshold && node.id != node.parent.id) {
            val point = vertices.getPoint(node.id)
            val parentPoint = vertices.getPoint(node.parent.id)
            riverLines.add(LineSegment2F(point, parentPoint))
            currentPolyLine.add(Point3F(point.x, point.y, node.height))
        } else if (node.id == node.parent.id) {
            val point = vertices.getPoint(node.id)
            currentPolyLine.add(Point3F(point.x, point.y, node.height))
        }
        val childrenWithUpstreamLength = node.children.filter { it.maxUpstreamLength > 0.0f }.sortedByDescending { it.maxUpstreamLength }
        if (childrenWithUpstreamLength.size > 1) {
            val longestUpstream = childrenWithUpstreamLength.first()
            if (longestUpstream.maxUpstreamLength < leafThreshold) {
                recurseAddRiverLines(longestUpstream, flowThreshold, leafThreshold, vertices, riverLines, riverPolyLines, currentPolyLine)
            } else {
                var nextPolyLine = currentPolyLine
                for (child in childrenWithUpstreamLength) {
                    if (child.maxUpstreamLength >= leafThreshold) {
                        if (nextPolyLine != currentPolyLine) {
                            riverPolyLines.add(nextPolyLine)
                        }
                        recurseAddRiverLines(child, flowThreshold, leafThreshold, vertices, riverLines, riverPolyLines, nextPolyLine)
                        nextPolyLine = ArrayList()
                    } else {
                        break
                    }
                }
            }
        } else {
            childrenWithUpstreamLength.forEach {
                recurseAddRiverLines(it, flowThreshold, leafThreshold, vertices, riverLines, riverPolyLines, currentPolyLine)
            }
        }
    }

    private fun prepareGraphNodesUnderWater(canceled: Reference<Boolean>, executor: ExecutorService, graph: Graph, landMask: Matrix<Byte>, soilMobilityMap: Matrix<Short>, distanceScale: Float): Quintuple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>, ArrayList<Int>, LinkedHashSet<Int>> {
        fun <T> doOrCancel(work: () -> T): T {
            if (!canceled.value) {
                return work()
            } else {
                throw CancellationException()
            }
        }

        val vertices = graph.vertices
        val land = LinkedHashSet<Int>(vertices.size)
        val water = ArrayList<Int>(vertices.size)
        val landMaskWidth = landMask.width
        val landMaskWidthM1 = landMaskWidth - 1
        for (i in 0 until vertices.size) {
            doOrCancel {
                val point = vertices.getPoint(i)
                val index = (Math.round(point.y * landMaskWidthM1) * landMaskWidth) + Math.round(point.x * landMaskWidthM1)
                if (landMask[index].toInt() and 0xFF < 128) {
                    water.add(i)
                } else {
                    land.add(i)
                }
            }
        }
        val border = doOrCancel { LinkedHashSet(graph.vertices.asSequence().filter { it.cell.isBorder }.map { it.id }.toList()) }
        val coast = doOrCancel { extractBeachFromGraphAndWater(graph.vertices, LinkedHashSet(water)) }
        val beach1 = doOrCancel { LinkedHashSet(coast.flatMap { vertices.getAdjacentVertices(it) }.toSet().filter { land.contains(it) }) }
        val beach2 = doOrCancel { coast.flatMap { vertices.getAdjacentVertices(it) }.toSet().filter { !beach1.contains(it) && land.contains(it) } }
        doOrCancel { water.addAll(beach1) }
        doOrCancel { water.addAll(beach2) }
        doOrCancel { coast.addAll(beach1) }
        doOrCancel { coast.addAll(beach2) }
        val (nodeIndex, nodes) = doOrCancel { createWaterNodes(canceled, executor, vertices, water, border, soilMobilityMap, distanceScale, coast) }
        val rivers = ArrayList<WaterNode>()
        border.forEach { id ->
            rivers.add(nodeIndex[id]!!)
        }
        return Quintuple(nodeIndex, nodes, rivers, water, border)
    }

    private fun prepareGraphNodes(canceled: Reference<Boolean>, executor: ExecutorService, graph: Graph, landMask: Matrix<Byte>, soilMobilityMap: Matrix<Short>, distanceScale: Float): Triple<Array<WaterNode?>, ArrayList<WaterNode>, ArrayList<WaterNode>> {
        fun <T> doOrCancel(work: () -> T): T {
            if (!canceled.value) {
                return work()
            } else {
                throw CancellationException()
            }
        }

        val vertices = graph.vertices
        val land = ArrayList<Int>(vertices.size)
        val water = LinkedHashSet<Int>(vertices.size)
        val landMaskWidth = landMask.width
        val landMaskWidthM1 = landMaskWidth - 1
        for (i in 0 until vertices.size) {
            doOrCancel {
                val point = vertices.getPoint(i)
                val index = (Math.round(point.y * landMaskWidthM1) * landMaskWidth) + Math.round(point.x * landMaskWidthM1)
                if (landMask[index].toInt() and 0xFF < 128) {
                    water.add(i)
                } else {
                    land.add(i)
                }
            }
        }
        val beach = doOrCancel { extractBeachFromGraphAndWater(vertices, water) }

        val (nodeIndex, nodes) = doOrCancel { createWaterNodes(canceled, executor, vertices, land, beach, soilMobilityMap, distanceScale) }
        val rivers = ArrayList<WaterNode>()
        beach.forEach { id ->
            rivers.add(nodeIndex[id]!!)
        }
        return Triple(nodeIndex, nodes, rivers)
    }

    private fun computeLakeConnections(canceled: Reference<Boolean>, vertices: Vertices, lakes: ArrayList<WaterNode>, nodeIndex: Array<WaterNode?>, passes: LinkedHashMap<PassKey, Pass>, rivers: ArrayList<WaterNode>) {
        fun <T> doOrCancel(work: () -> T): T {
            if (!canceled.value) {
                return work()
            } else {
                throw CancellationException()
            }
        }
        lakes.forEach { waterNode ->
            doOrCancel { recurseFindPasses(nodeIndex, waterNode, passes) }
        }
        val expandedPasses = ArrayList<Pass>(passes.size * 2)
        passes.values.forEach {
            doOrCancel {
                expandedPasses.add(it)
                expandedPasses.add(Pass(PassKey(it.passKey.lake2, it.passKey.lake1), it.id2, it.id1, it.height))
            }
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
                    doOrCancel { expandedPasses.removeAt(j) }
                    continue
                } else if (outflowing.contains(currentPass.passKey.lake2)) {
                    doOrCancel {
                        outflowing.add(currentPass.passKey.lake1)
                        expandedPasses.removeAt(j)
                        val childNode = recurseFindRoot(nodeIndex[currentPass.id1]!!)
                        val parentNode = nodeIndex[currentPass.id2]!!
                        parentNode.children.add(childNode)
                        childNode.parent = parentNode
                        childNode.distanceToParent = vertices.getPoint(childNode.id).distance(vertices.getPoint(parentNode.id))
                    }
                    break
                }
            }
        }
    }

    private fun prepareNodesAndLakes(executor: ExecutorService, lakes: ArrayList<WaterNode>, nodes: ArrayList<WaterNode>, rivers: ArrayList<WaterNode>) {
        nodes.parallelStream().forEach { node ->
            node.lake = -1
            node.children.clear()
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
                    node.parent = minNode
                    node.distanceToParent = distToMin
                }
                if (minNode == node) {
                    synchronized(lakes) {
                        lakes.add(node)
                    }
                }
            }
        }

        nodes.parallelStream().forEach { node ->
            node.lake = -1
            node.adjacents.forEach { (otherNode, _) ->
                if (otherNode.parent == node) {
                    node.children.add(otherNode)
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

    private fun createWaterNodes(executor: ExecutorService, vertices: Vertices, land: List<Int>, riverMouths: LinkedHashSet<Int>, heightMap: Matrix<Float>, soilMobilityMap: Matrix<Short>, distanceScale: Float, waterDepthMeters: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val hWidth = heightMap.width
        val hWidthM1 = hWidth - 1
        val eWidth = soilMobilityMap.width
        val eWidthM1 = eWidth - 1
        val nodeFutures = (0 until threadCount).map { i ->
            executor.call {
                for (id in i until land.size step threadCount) {
                    val landId = land[id]
                    val isExternal = riverMouths.contains(landId)
                    val area = vertices.getArea(landId) * areaScale
                    val point = vertices.getPoint(landId)
                    val hIndex = (Math.round(point.y * hWidthM1) * hWidth) + Math.round(point.x * hWidthM1)
                    val isPinned = isExternal || pinned?.contains(landId) ?: false
                    val height = if (isExternal) 0.0f else if (isPinned) waterDepthMeters else heightMap[hIndex] * waterDepthMeters
                    val eIndex = (Math.round(point.y * eWidthM1) * eWidth) + Math.round(point.x * eWidthM1)
                    val soilMobility = ((soilMobilityMap[eIndex].toInt() and 0xFFFF) / 65535.0f) * 0.000001122f
                    val node = WaterNode(landId, isExternal, area, ArrayList(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, 0.0f, height, area, 0, 0.0f, soilMobility, isPinned)
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
        val nodeFutures2 = (0 until threadCount).map { i ->
            executor.call {
                for (id in i until nodes.size step threadCount) {
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

    private fun createWaterNodes(canceled: Reference<Boolean>, executor: ExecutorService, vertices: Vertices, land: List<Int>, riverMouths: LinkedHashSet<Int>, biomes: List<Biome>, biomeMask: Matrix<Byte>, elevationMask: Matrix<Short>, startingHeights: Matrix<Short>, soilMobilityMap: Matrix<Short>, distanceScale: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        fun <T> doOrCancel(work: () -> T): T {
            if (!canceled.value) {
                return work()
            } else {
                throw CancellationException()
            }
        }

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
        val nodeFutures = (0 until threadCount).map { i ->
            executor.call {
                for (id in i until land.size step threadCount) {
                    doOrCancel {
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
                        val node = WaterNode(landId, isExternal, area, ArrayList(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, elevationPower, height, area, biomeId, 0.0f, soilMobility, isExternal || pinned?.contains(landId) ?: false)
                        nodeIndex[landId] = node
                    }
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
        val nodeFutures2 = (0 until threadCount).map { i ->
            executor.call {
                for (id in i until nodes.size step threadCount) {
                    doOrCancel {
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

    private fun createWaterNodes(canceled: Reference<Boolean>, executor: ExecutorService, vertices: Vertices, land: ArrayList<Int>, riverMouths: LinkedHashSet<Int>, soilMobilityMap: Matrix<Short>, distanceScale: Float, pinned: LinkedHashSet<Int>? = null): Pair<Array<WaterNode?>, ArrayList<WaterNode>> {
        fun <T> doOrCancel(work: () -> T): T {
            if (!canceled.value) {
                return work()
            } else {
                throw CancellationException()
            }
        }

        val eWidth = soilMobilityMap.width
        val eWidthM1 = eWidth - 1
        val areaScale = distanceScale * distanceScale
        val nodeIndex = arrayOfNulls<WaterNode>(vertices.size)
        val nodeFutures = (0 until threadCount).map { i ->
            executor.call {
                for (id in i until land.size step threadCount) {
                    doOrCancel {
                        val landId = land[id]
                        val isExternal = riverMouths.contains(landId)
                        val area = vertices.getArea(landId) * areaScale
                        val point = vertices.getPoint(landId)
                        val isPinned = isExternal || pinned?.contains(landId) ?: false
                        val eIndex = (Math.round(point.y * eWidthM1) * eWidth) + Math.round(point.x * eWidthM1)
                        val soilMobility = ((soilMobilityMap[eIndex].toInt() and 0xFFFF) / 65535.0f) * 0.000001122f
                        val node = WaterNode(landId, isExternal, area, ArrayList(vertices.getAdjacentVertices(landId).size), point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, 0.0f, 0.0f, area, 0, 0.0f, soilMobility, isPinned)
                        nodeIndex[landId] = node
                    }
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
        val nodeFutures2 = (0 until threadCount).map { i ->
            executor.call {
                for (id in i until nodes.size step threadCount) {
                    doOrCancel {
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
        }
        nodeFutures2.forEach { it.join() }
        return Pair(nodeIndex, nodes)
    }

    private fun applyMapsToUnderWaterNodes(executor: ExecutorService, vertices: Vertices, heightMap: Matrix<Float>, nodes: ArrayList<WaterNode>, waterDepthMeters: Float) {
        val hWidth = heightMap.width
        val hWidthM1 = hWidth - 1
        val nodeFutures = (0 until threadCount).map { i ->
            executor.call {
                for (id in i until nodes.size step threadCount) {
                    val node = nodes[id]
                    val height = if (node.isExternal) {
                        0.0f
                    } else if (node.isPinned) {
                        waterDepthMeters
                    } else {
                        val point = vertices.getPoint(node.id)
                        val hIndex = (Math.round(point.y * hWidthM1) * hWidth) + Math.round(point.x * hWidthM1)
                        clamp(heightMap[hIndex] + waterDepthMeters, 0.0f, waterDepthMeters)
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
                        val biomeId = Math.max(0, biomeMask[bIndex].toInt() - 1)
                        val uIndex = (Math.round(point.y * elevationWidthM1) * elevationWidth) + Math.round(point.x * elevationWidthM1)
                        node.biome = biomeId
                        val settings = erosionSettings[biomeId]
                        val blendFactor = settings.previousTierBlendWeight
                        val blendFactorI = 1.0f - blendFactor
                        val height = height1 * blendFactor + height2 * blendFactorI
                        node.height = Math.max(0.2f, height)
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

    private fun computeHeights(executor: ExecutorService, rivers: ArrayList<WaterNode>, biomes: List<Biome>, erosionSettings: List<ErosionSettings>, biomeTemplates: Biomes) {
        val heightFutures = rivers.map { river ->
            executor.call {
                recurseHeights(river, biomes, erosionSettings, biomeTemplates)
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

    private fun recurseHeights(node: WaterNode, biomes: List<Biome>, erosionSettings: List<ErosionSettings>, biomeTemplates: Biomes) {
        if (node.isExternal) {
            node.children.forEach { recurseHeights(it, biomes, erosionSettings, biomeTemplates) }
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
                val (talusSet, talusVarianceSet, talusThresholds) = biome.talusAngles
                val nodeHeightIndex = Math.round(node.height * biome.heightMultiplier).coerceIn(0, 1023)
                if (talusThresholds != null && settings.talusOverride == null) {
                    val talusDegrees = Math.round((talusSet[nodeHeightIndex] + talusVarianceSet[nodeHeightIndex] * variance) * 65535.0f).coerceIn(0, 65535)
                    node.density = talusDegrees.toFloat()
                    val talusSlope = biomeTemplates.DEGREES_TO_SLOPES[talusDegrees]
                    val currentSlope = (node.height - parent.height) / node.distanceToParent
                    if (currentSlope > talusSlope) {
                        val parentHeightIndex = Math.round(parent.height * biome.heightMultiplier).coerceIn(0, 1023)
                        val parentTalusDegrees = Math.round((talusSet[parentHeightIndex] + talusVarianceSet[parentHeightIndex] * variance) * 65535.0f).coerceIn(0, 65535)
                        if (currentSlope > parentTalusDegrees) {
                            node.height = (node.distanceToParent * talusSlope) + parent.height
                        } else {
                            val threshold = talusThresholds[nodeHeightIndex]
                            if (parentHeightIndex < threshold && nodeHeightIndex >= threshold) {
                                node.height = threshold / biome.heightMultiplier
                            } else {
                                node.height = (node.distanceToParent * talusSlope) + parent.height
                            }
                        }
                    }
                } else {
                    val talusDegrees = if (settings.talusOverride != null) {
                        Math.round((settings.talusOverride + talusVarianceSet[nodeHeightIndex] * variance) * 65535.0f).coerceIn(0, 65535)
                    } else {
                        Math.round((talusSet[nodeHeightIndex] + talusVarianceSet[nodeHeightIndex] * variance) * 65535.0f).coerceIn(0, 65535)
                    }
                    node.density = talusDegrees.toFloat()
                    val talusSlope = biomeTemplates.DEGREES_TO_SLOPES[talusDegrees]
                    if ((node.height - parent.height) / node.distanceToParent > talusSlope) {
                        node.height = (node.distanceToParent * talusSlope) + parent.height
                    }
                }
            }
            node.children.forEach { recurseHeights(it, biomes, erosionSettings, biomeTemplates) }
        }
    }

    private fun extractBeachFromGraphAndWater(vertices: Vertices, water: LinkedHashSet<Int>) = (0 until vertices.size).asSequence().filterTo(LinkedHashSet<Int>()) { isCoastalPoint(vertices, water, it) }

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
                Triple(null, 0.0f, 0.0f)
            } else {
                val id = it -1
                val (min, max) = biomeExtremes[id]
                val biomeSettings = erosionSettings[id]
                Triple(biomeSettings.terraceFunction?.invoke(min.value, max.value), biomeSettings.terraceJitter, biomeSettings.terraceJitterFrequency)
            }
        }
        (0 until threadCount).mapTo(futures) {
            executor.submit {
                for (i in it until heightMap.size.toInt() step threadCount) {
                    val (terracing, jitter, jitterFrequency) = terraceFunctions[biomeMask[i].toInt()]
                    if (terracing != null) {
                        val simplexX = ((i % heightMap.width) / heightMap.width.toFloat()) * jitterFrequency
                        val simplexY = ((i / heightMap.width) / heightMap.width.toFloat()) * jitterFrequency
                        val height = heightMap[i]
                        val newHeight = terracing.apply(height, simplexX, simplexY, jitter)
                        if (newHeight != height) {
                            heightMap[i] = newHeight
                        }
                    }
                }
            }
        }
        futures.forEach(Future<*>::join)
    }

    private fun writePolyLineObj(polyLines: List<List<Point3F>>, outputFile: File, scaleFactor: Float = 1.0f) {
        val format = DecimalFormat("0.####")
        outputFile.outputStream().bufferedWriter().use { writer ->
            polyLines.forEach { polyLine ->
                polyLine.forEach { point ->
                    writer.write("v ${format.format((-point.y + 0.5f) * scaleFactor)} ${format.format(point.z)} ${format.format((point.x - 0.5f) * scaleFactor)}\n")
                }
            }
            var vertexId = 1
            polyLines.forEach { polyLine ->
                writer.write("l")
                repeat(polyLine.size) {
                    writer.write(" ")
                    writer.write(vertexId.toString())
                    vertexId++
                }
                writer.write("\n")
            }
        }
    }

    private fun writeObj(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, fallback: Matrix<Float>?, threadCount: Int, outputFile: File, mapSizeMeters: Float, waterDepthMeters: Float) {
        val fWidth = fallback?.width ?: 0
        val fWidthM1 = fWidth - 1
        val triangles = graph.triangles
        val graphVertices = graph.vertices
        var vertexIndex = 0
        val meshVertices = Array(graphVertices.size) { i ->
            val point = graphVertices.getPoint(i)
            val node = nodeIndex[i]
            if (node == null) {
                if (fallback != null) {
                    val z = fallback[(Math.round(point.y * fWidthM1) * fWidth) + Math.round(point.x * fWidthM1)] - waterDepthMeters
                    vertexIndex++ to Point3F(point.x, point.y, z)
                } else {
                    null
                }
            } else {
                val z = node.height
                vertexIndex++ to Point3F(point.x, point.y, z)
            }
        }
        val vertexData = FloatArray(vertexIndex * 3)
        meshVertices.forEach {
            if (it != null) {
                val (i, point) = it
                var index = i * 3
                vertexData[index++] = point.x * mapSizeMeters
                vertexData[index++] = point.y * mapSizeMeters
                vertexData[index] = point.z
            }
        }
        val futures = ArrayList<Future<*>>(threadCount)
        val indexData = IntArray(triangles.size * 3)
        (0 until threadCount).mapTo(futures) {
            executor.submit {
                for (t in it until triangles.size step threadCount) {
                    val triangleVertices = triangles.getVertices(t)
                    val a = meshVertices[triangleVertices[0]]
                    val b = meshVertices[triangleVertices[1]]
                    val c = meshVertices[triangleVertices[2]]
                    if (a != null && b != null && c != null) {
                        var offset = t * 3
                        indexData[offset++] = a.first
                        indexData[offset++] = b.first
                        indexData[offset] = c.first
                    }
                }
            }
        }
        futures.forEach(Future<*>::join)
        val format = DecimalFormat("0.####")
        outputFile.outputStream().bufferedWriter().use { writer ->
            for (i in 0 until vertexData.size step 3) {
                writer.write("v ${format.format(vertexData[i])} ${format.format(vertexData[i + 1])} ${format.format(vertexData[i + 2])}\n")
            }
            for (i in 0 until indexData.size step 3) {
                writer.write("f ${indexData[i] + 1} ${indexData[i + 1] + 1} ${indexData[i + 2] + 1}\n")
            }
        }
    }

    private inline fun renderPeakMap(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMapWidth: Int, outputWidth: Int, textureWidth: Int, threadCount: Int, property: WaterNode.() -> Float): ShortArrayMatrix {
        val scale = outputWidth / heightMapWidth.toFloat()
        val graphVertices = graph.vertices
        var minHeight = Float.MAX_VALUE
        var maxHeight = 0.0f
        val peaks = nodeIndex.mapNotNull { node ->
            if (node != null) {
                val z = node.property()
                if (node.property() != 0.0f) {
                    val point = graphVertices.getPoint(node.id)
                    if (z < minHeight) {
                        minHeight = z
                    }
                    if (z > maxHeight) {
                        maxHeight = z
                    }
                    Point3F(point.x * scale, point.y * scale, z)
                } else {
                    null
                }
            } else {
                null
            }
        }
        val deltaHeight = maxHeight - minHeight
        val edges = peaks.flatMap { peak ->
            val height = (peak.z - minHeight) / deltaHeight
            val edgeLength = (0.0025f + 0.01f * height) * scale
            val peak2D = Point2F(peak.x, peak.y)
            listOf(LineSegment2F(Point2F(peak.x - edgeLength, peak.y + edgeLength), peak2D), LineSegment2F(peak2D, Point2F(peak.x + edgeLength, peak.y + edgeLength)))
        }
        val textureId = renderEdges(textureWidth, executor, edges, threadCount)
        return ShortArrayMatrix(heightMapWidth, extractTextureRedShort(textureId, heightMapWidth))
    }

    private fun gaussRenderHeightMap(graph: Graph, nodeIndex: Array<WaterNode?>, fallback: Matrix<Float>, outputWidth: Int, waterDepthMeters: Float, multiplier: Float = 0.56f): FloatArrayMatrix {
        val fWidth = fallback.width
        val fWidthM1 = fWidth - 1
        val graphVertices = graph.vertices

        val vertexPositions = FloatArray(graph.stride!! * graph.stride * 3)
        (0 until graph.stride).toList().parallelStream().forEach { y ->
            val yOff = y * graph.stride
            (0 until graph.stride).forEach { x ->
                val i = yOff + x
                val point = graphVertices.getPoint(i)
                val node = nodeIndex[i]
                if (node == null) {
                    val z = fallback[((point.y * fWidthM1).roundToInt() * fWidth) + (point.x * fWidthM1).roundToInt()] - waterDepthMeters
                    val o = i * 3
                    vertexPositions[o] = point.x
                    vertexPositions[o + 1] = point.y
                    vertexPositions[o + 2] = z
                } else {
                    val z = node.height
                    val o = i * 3
                    vertexPositions[o] = point.x
                    vertexPositions[o + 1] = point.y
                    vertexPositions[o + 2] = z
                }
            }
        }

        val vertexPositionTexId = buildTextureRgbFloat(vertexPositions, graph.stride, GL_NEAREST, GL_NEAREST)

        val gaussTextureId = render(outputWidth) { _, dynamicGeometry2D, textureRenderer ->
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
            gaussShader.bind(graph.stride, multiplier, vertexPositionTexId)
            dynamicGeometry2D.render(vertexData, indexData, gaussShader.positionAttribute)
            val retVal = textureRenderer.newRedTextureFloat(GL_LINEAR, GL_LINEAR)
            textureRenderer.unbind()
            retVal
        }
        return FloatArrayMatrix(outputWidth, extractTextureRedFloat(gaussTextureId, outputWidth))
    }

    private inline fun renderHeightMap(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, fallback: Matrix<Float>?, defaultValue: Float, heightMapWidth: Int, outputWidth: Int, threadCount: Int, waterDepthMeters: Float, cap: Float = 1.0f, property: WaterNode.() -> Float = { height }): FloatArrayMatrix {
        val scale = outputWidth / heightMapWidth.toFloat()
        val fWidth = fallback?.width ?: 0
        val fWidthM1 = fWidth - 1
        val triangles = graph.triangles
        val graphVertices = graph.vertices
        var vertexIndex = 0
        var minHeight = defaultValue
        var maxHeight = defaultValue
        val meshVertices = Array(graphVertices.size) { i ->
            val point = graphVertices.getPoint(i)
            val node = nodeIndex[i]
            if (node == null) {
                if (fallback != null) {
                    val z = fallback[(Math.round(point.y * fWidthM1) * fWidth) + Math.round(point.x * fWidthM1)] - waterDepthMeters
                    if (z < minHeight) {
                        minHeight = z
                    }
                    if (z > maxHeight) {
                        maxHeight = z
                    }
                    vertexIndex++ to Point3F(point.x * scale, point.y * scale, z)
                } else {
                    null
                }
            } else {
                val z = node.property()
                if (z < minHeight) {
                    minHeight = z
                }
                if (z > maxHeight) {
                    maxHeight = z
                }
                vertexIndex++ to Point3F(point.x * scale, point.y * scale, z)
            }
        }
        val deltaHeight = maxHeight - minHeight
        val capInverse = 1.0f / cap
        val vertexData = FloatArray(vertexIndex * 3)
        meshVertices.forEach {
            if (it != null) {
                val (i, point) = it
                var index = i * 3
                vertexData[index++] = point.x
                vertexData[index++] = point.y
                vertexData[index] = min(1.0f, (((point.z - minHeight) / deltaHeight) * capInverse))
            }
        }
        val futures = ArrayList<Future<*>>(threadCount)
        val indexData = IntArray(triangles.size * 3)
        (0 until threadCount).mapTo(futures) {
            executor.submit {
                for (t in it until triangles.size step threadCount) {
                    val triangleVertices = triangles.getVertices(t)
                    val a = meshVertices[triangleVertices[0]]
                    val b = meshVertices[triangleVertices[1]]
                    val c = meshVertices[triangleVertices[2]]
                    if (a != null && b != null && c != null) {
                        var offset = t * 3
                        indexData[offset++] = a.first
                        indexData[offset++] = b.first
                        indexData[offset] = c.first
                    }
                }
            }
        }
        futures.forEach(Future<*>::join)
        val adjustedDefault = (defaultValue - minHeight) / deltaHeight
        val array = renderTrianglesRedFloat(heightMapWidth, vertexData, indexData, QuadFloat(adjustedDefault, adjustedDefault, adjustedDefault, 1.0f))
        for (i in 0 until array.size) {
            array[i] = (array[i] * deltaHeight) + minHeight
        }
        return FloatArrayMatrix(heightMapWidth, array)
    }

    private fun renderHeightMap(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMap: Matrix<Float>, fallback: Matrix<Float>?, threadCount: Int, waterDepthMeters: Float) {
        val fWidth = fallback?.width ?: 0
        val fWidthM1 = fWidth - 1
        val futures = ArrayList<Future<*>>(threadCount)
        val triangles = graph.triangles
        (0 until threadCount).mapTo(futures) {
            executor.submit {
                for (t in it until triangles.size step threadCount) {
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
                                fallback[index] - waterDepthMeters
                            }
                            val nbH = if (nb != null) {
                                nb.height
                            } else {
                                val point = vb.point
                                val index = (Math.round(point.y * fWidthM1) * fWidth) + Math.round(point.x * fWidthM1)
                                fallback[index] - waterDepthMeters
                            }
                            val ncH = if (nc != null) {
                                nc.height
                            } else {
                                val point = vc.point
                                val index = (Math.round(point.y * fWidthM1) * fWidth) + Math.round(point.x * fWidthM1)
                                fallback[index] - waterDepthMeters
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
        val maxLandValue = (0 until heightMap.size.toInt()).asSequence().map { heightMap[it] }.max()?.toFloat() ?: 0.0f
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0 until width)) {
            for (x in (0 until width)) {
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
        val maxLandValue = (0 until heightMap.size.toInt()).asSequence().map { heightMap[it].toInt() and 0xFF }.max()?.toFloat() ?: 0.0f
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0 until width)) {
            for (x in (0 until width)) {
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
        val maxLandValue = (0 until heightMap.size.toInt()).asSequence().map { heightMap[it].toInt() and 0xFFFF }.max()?.toFloat() ?: 0.0f
        val waterLine = 0.30f
        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
        for (y in (0 until width)) {
            for (x in (0 until width)) {
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

    private fun writeHeightMap(heightMap: Matrix<Float>, coastalDistanceMask: Matrix<Short>? = null, waterLine: Float = 0.3f): TextureId {
        val output = writeHeightMapAsShortArray(heightMap, null, waterLine)
        return heightMapToTextureId(output, heightMap.width)
    }

    private fun heightMapToTextureId(input: ShortArray, width: Int): TextureId {
        return buildTextureRedShort(input, width, GL_LINEAR, GL_LINEAR)
    }

    private fun normalAndAoToTextureId(input: ByteBuffer, width: Int): TextureId {
        return buildTextureRgbaByte(input, width, GL_LINEAR, GL_LINEAR)
    }

    private fun buildBiomeIndexMask(heightMap: Matrix<Float>, landMap: Matrix<Byte>, biomeMap: Matrix<Byte>, biomes: List<Biome>, biomeTemplates: Biomes, dictionaryWidth: Int): IntArray {
        val incrementLand = landMap.width / dictionaryWidth.toFloat()
        val incrementBiome = biomeMap.width / dictionaryWidth.toFloat()
        val incrementHeight = heightMap.width / dictionaryWidth.toFloat()
        val output = IntArray(dictionaryWidth * dictionaryWidth)
        for (y in 0 until dictionaryWidth) {
            val yLookupHeight = (y * incrementHeight).toInt()
            val yLookupLand = (y * incrementLand).toInt()
            val yLookupBiome = (y * incrementBiome).toInt()
            for (x in 0 until dictionaryWidth) {
                val biome = if (landMap[yLookupLand, (x * incrementLand).toInt()].toInt() and 0xFF > 0) {
                    biomes[(biomeMap[yLookupBiome, (x * incrementBiome).toInt()].toInt() and 0xFF) - 1]
                } else {
                    biomeTemplates.UNDER_WATER_BIOME
                }
                output[x * dictionaryWidth + y] = biome.detailSelector(heightMap[yLookupHeight, (x * incrementHeight).toInt()]).toInt()
            }
        }
        return output
    }

    private fun writeAugmentedBiomeMapAsByteBuffer(heightMap: Matrix<Float>, landMap: Matrix<Byte>, biomeMap: Matrix<Byte>, biomes: List<Biome>, biomeTemplates: Biomes): ByteBuffer {
        val width = heightMap.width
        val landWidth = landMap.width
        val landWidthM1 = landWidth - 1
        val ratio = landWidth / width.toFloat()
        val output = ByteArray(width * width)
        for (y in (0 until width)) {
            val lookupY = clamp(Math.round(y * ratio), 0, landWidthM1)
            for (x in (0 until width)) {
                val lookupX = clamp(Math.round(x * ratio), 0, landWidthM1)
                val biome = if (landMap[lookupX, lookupY].toInt() and 0xFF > 0) {
                    biomes[(biomeMap[lookupX, lookupY].toInt() and 0xFF) - 1]
                } else {
                    biomeTemplates.UNDER_WATER_BIOME
                }
                output[y * width + x] = biome.detailSelector(heightMap[x, y])
            }
        }
        return ByteBuffer.wrap(output)
    }

    private fun combineHeightMapsToRcMatrix(heightMap: Matrix<Float>, coastalDistanceMask: Matrix<Short>, width: Int, waterDepthMeters: Float, renderScale: Float, underwaterBeachFalloff: Int, inlandBeachFalloff: Int, inlandBoost: Float = 0.0f, underwaterBoost: Float = 0.0f): Triple<RcMatrix, Float, Float> {
        val scaleFactor = coastalDistanceMask.width / width.toFloat()
        val waterLine = 0.3f
        val output = RcMatrix(width, width)
//        val maxLandValue = (0 until heightMap.size.toInt()).asSequence().map { heightMap[it] }.max() ?: 0.0f
//        val minWaterValue = (0 until heightMap.size.toInt()).asSequence().map { heightMap[it] }.min() ?: 0.0f
//        val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
//        val waterFactor = (1.0f / minWaterValue) * waterLine
        val maximumBeachFactor = 0.97f
        for (y in (0 until width)) {
            val coastalY = (y * scaleFactor).toInt()
            for (x in (0 until width)) {
//                val heightValue = heightMap[x, y]
                output[y, x] = ((heightMap[x, y] + waterDepthMeters) * renderScale).coerceIn(0.0f, 1.0f)
//                val coastalDistanceValue = -((coastalDistanceMask[(x * scaleFactor).toInt(), coastalY].toInt() and 0xFFFF) - 65535)
//                if (heightValue < 0.0f) {
//                    if (underwaterBeachFalloff > 0 && coastalDistanceValue <= underwaterBeachFalloff) {
//                        val beachFalloff = clamp((underwaterBeachFalloff - coastalDistanceValue) / underwaterBeachFalloff.toFloat(), 0.0f, 1.0f)
//                        val beachFactor = beachFalloff * maximumBeachFactor
//                        val naturalHeight = waterLine - (heightValue * waterFactor)
//                        val adjustedHeight = (beachFactor * waterLine) + ((1.0f - beachFactor) * naturalHeight)
//                        output[y, x] = adjustedHeight * 256.0f + (clamp(1.0f - beachFalloff, 0.0f, 1.0f) * underwaterBoost)
//                    } else {
//                        output[y, x] = (waterLine - (heightValue * waterFactor)) * 256.0f + underwaterBoost
//                    }
//                } else {
//                    if (inlandBeachFalloff > 0 && coastalDistanceValue <= inlandBeachFalloff) {
//                        val beachFalloff = clamp((inlandBeachFalloff - coastalDistanceValue) / inlandBeachFalloff.toFloat(), 0.0f, 1.0f)
//                        val beachFactor = beachFalloff * maximumBeachFactor
//                        val naturalHeight = (heightValue * landFactor) + waterLine
//                        val adjustedHeight = (beachFactor * waterLine) + ((1.0f - beachFactor) * naturalHeight)
//                        output[y, x] = adjustedHeight * 256.0f + (clamp(1.0f - beachFalloff, 0.0f, 1.0f) * inlandBoost)
//                    } else {
//                        output[y, x] = ((heightValue * landFactor) + waterLine) * 256.0f + inlandBoost
//                    }
//                }
            }
        }
//        return Triple(output, minWaterValue, maxLandValue)
        return Triple(output, 0.0f, 0.0f)
    }

    private fun writeHeightMapAsShortArray(heightMap: Matrix<Float>, coastalDistanceMask: Matrix<Short>? = null, waterLine: Float = 0.3f, normalize: Boolean = true, min: Float = 0.0f, scale: Float = 1.0f, heightScaleFactor: MutableReference<Float>? = null): ShortArray {
        val width = heightMap.width
        val output = ShortArray(width * width)
        if (normalize) {
            val (minWaterValue, maxLandValue) = (0 until heightMap.size.toInt()).asSequence().map { heightMap[it] }.minMax() ?: 0.0f to 0.0f
            val landFactor = (1.0f / maxLandValue) * (1.0f - waterLine)
            val waterFactor = (1.0f / minWaterValue) * waterLine
            val maximumBeachFactor = 0.97f
            val underwaterBeachFalloff = 250
            val inlandBeachFalloff = 100
            (0 until width).toList().parallelStream().forEach { y ->
                val yOff = y * width
                val yCoastalOff = y * (coastalDistanceMask?.width ?: 0)
                for (x in (0 until width)) {
                    val heightValue = heightMap[yOff + x]
                    if (coastalDistanceMask != null) {
                        val coastalDistanceValue = -((coastalDistanceMask[yCoastalOff + x].toInt() and 0xFFFF) - 65535)
                        if (heightValue < 0.0f) {
                            if (coastalDistanceValue <= underwaterBeachFalloff) {
                                val beachFactor = ((underwaterBeachFalloff - coastalDistanceValue) / underwaterBeachFalloff.toFloat()) * maximumBeachFactor
                                val naturalHeight = waterLine - (heightValue * waterFactor)
                                val adjustedHeight = (beachFactor * waterLine) + ((1.0f - beachFactor) * naturalHeight)
                                output[yOff + x] = (adjustedHeight * 65535).toInt().toShort()
                            } else {
                                output[yOff + x] = ((waterLine - (heightValue * waterFactor)) * 65535).toInt().toShort()
                            }
                        } else {
                            if (coastalDistanceValue <= inlandBeachFalloff) {
                                val beachFactor = ((inlandBeachFalloff - coastalDistanceValue) / inlandBeachFalloff.toFloat()) * maximumBeachFactor
                                val naturalHeight = (heightValue * landFactor) + waterLine
                                val adjustedHeight = (beachFactor * waterLine) + ((1.0f - beachFactor) * naturalHeight)
                                output[yOff + x] = (adjustedHeight * 65535).toInt().toShort()
                            } else {
                                output[yOff + x] = (((heightValue * landFactor) + waterLine) * 65535).toInt().toShort()
                            }
                        }
                    } else {
                        if (heightValue < 0.0f) {
                            output[yOff + x] = ((waterLine - (heightValue * waterFactor)) * 65535).toInt().toShort()
                        } else {
                            output[yOff + x] = (((heightValue * landFactor) + waterLine) * 65535).toInt().toShort()
                        }
                    }
                }
            }
        } else {
            val adjustedScale = scale * 65535
            var maxHeight = 0.0f
            (0 until width).toList().parallelStream().map { y ->
                val yOff = y * width
                var localMaxHeight = 0.0f
                for (x in (0 until width)) {
                    val heightValue = heightMap[yOff + x]
                    if (heightValue > localMaxHeight) {
                        localMaxHeight = heightValue
                    }
                    output[yOff + x] = ((heightValue - min) * adjustedScale).roundToInt().coerceIn(0, 65535).toShort()
                }
                localMaxHeight
            }.forEach {
                if (it > maxHeight) {
                    maxHeight = it
                }
            }
            if (heightScaleFactor != null) {
                heightScaleFactor.value = 1.0f / ((maxHeight - min) * scale).coerceIn(0.0f, 1.0f)
            }
        }
        return output
    }

    private fun buildTriangles(graph: Graph, regionMask: Matrix<Byte>): List<Pair<FloatArray, IntArray>> {
        val regions = ArrayList<Triple<ArrayList<Float>, ArrayList<Int>, AtomicInteger>>(16)
        val vertices = graph.vertices
        for (id in 0 until vertices.size) {
            val regionId = regionMask[id]
            if (regionId < 1) {
                continue
            }
            if (regions.size < regionId) {
                for (i in regions.size until regionId) {
                    regions.add(Triple(ArrayList(), ArrayList(), AtomicInteger(0)))
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

    private fun File.exportPolyLines(polyLines: List<List<Point3F>>?, outputSize: Int) {
        if (polyLines == null) return
        if ((!this.exists() && this.parentFile.isDirectory && this.parentFile.canWrite()) || this.canWrite()) {
            writePolyLineObj(polyLines, this, outputSize.toFloat())
        }
    }

    private fun File.exportMap16Bit(outputSize: Int, heightMap: ShortArray?, heightMapWidth: Int) {
        if (heightMap == null) return
        if ((!this.exists() && this.parentFile.isDirectory && this.parentFile.canWrite()) || this.canWrite()) {
            val output = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_USHORT_GRAY)
            val raster = output.raster
            for (y in 0 until outputSize) {
                val yOff = y * heightMapWidth
                for (x in 0 until outputSize) {
                    raster.setSample(x, y, 0, heightMap[yOff + x].toInt() and 0xFFFF)
                }
            }
            ImageIO.write(output, "png", this)
        }
    }

    private fun File.exportMap8BitRGB(outputSize: Int, heightMap: ByteBuffer?, heightMapWidth: Int) {
        if (heightMap == null) return
        if ((!this.exists() && this.parentFile.isDirectory && this.parentFile.canWrite()) || this.canWrite()) {
            val colorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), intArrayOf(8, 8, 8), false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
            val output = BufferedImage(colorModel, colorModel.createCompatibleWritableRaster(outputSize, outputSize), colorModel.isAlphaPremultiplied, null)
            val raster = output.raster
            for (y in 0 until outputSize) {
                val yOff = y * heightMapWidth
                for (x in 0 until outputSize) {
                    val index = (yOff + x) * 4
                    raster.setSample(x, y, 0, heightMap[index].toInt() and 0xFF)
                    raster.setSample(x, y, 1, heightMap[index + 1].toInt() and 0xFF)
                    raster.setSample(x, y, 2, heightMap[index + 2].toInt() and 0xFF)
                }
            }
            ImageIO.write(output, "png", this)
        }
    }

    private fun File.exportMap8BitA(outputSize: Int, heightMap: ByteBuffer?, heightMapWidth: Int) {
        if (heightMap == null) return
        if ((!this.exists() && this.parentFile.isDirectory && this.parentFile.canWrite()) || this.canWrite()) {
            val output = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_BYTE_GRAY)
            val raster = output.raster
            for (y in 0 until outputSize) {
                val yOff = y * heightMapWidth
                for (x in 0 until outputSize) {
                    val index = (yOff + x) * 4
                    raster.setSample(x, y, 0, heightMap[index + 3].toInt() and 0xFF)
                }
            }
            ImageIO.write(output, "png", this)
        }
    }

    private fun File.exportMap8BitGrey(outputSize: Int, heightMap: ByteBuffer?, heightMapWidth: Int) {
        if (heightMap == null) return
        if ((!this.exists() && this.parentFile.isDirectory && this.parentFile.canWrite()) || this.canWrite()) {
            val output = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_BYTE_GRAY)
            val raster = output.raster
            for (y in 0 until outputSize) {
                val yOff = y * heightMapWidth
                for (x in 0 until outputSize) {
                    val index = yOff + x
                    raster.setSample(x, y, 0, heightMap[index].toInt() and 0xFF)
                }
            }
            ImageIO.write(output, "png", this)
        }
    }

    private fun shrinkTemp(currentSize: Int, heightMap: ShortArray, sharpen: Double): Pair<Int, ShortArray> {
        val outputSize = currentSize / 2
        val output = ShortArray(outputSize * outputSize)
        for (y in 0 until outputSize) {
            val yi = (((y + 0.5f) / outputSize) * currentSize + 0.1f).toInt()
            for (x in 0 until outputSize) {
                val xi = (((x + 0.5f) / outputSize) * currentSize + 0.1f).toInt()
                output[y * outputSize + x] = interpolateNew(heightMap, currentSize, xi, yi, sharpen).toShort()
            }
        }
        return outputSize to output
    }

    private fun interpolateNew(heightMap: ShortArray, stride: Int, x: Int, y: Int, sharpen: Double): Int {
        if (sharpen == 0.0) {
            var sum = 0.0f
            for (m in -1..0) {
                for (n in -1..0) {
                    sum += sample(heightMap, stride, x + m, y + n)
                }
            }
            return (sum * 0.25f).roundToInt().coerceIn(0, 65535)
        } else {
            val samples = FloatArrayMatrix(4)
            for (m in -2..1) {
                for (n in -2..1) {
                    samples[m + 2, n + 2] = sample(heightMap, stride, x + m, y + n).toFloat()
                }
            }
            val horizontal = FloatArray(4)
            for (i in 0..3) {
                column(horizontal, samples, i)
            }
            val vertical = FloatArray(4)
            for (i in 0..3) {
                row(vertical, samples, i)
            }
            val (diag1) = interpolateCubic(QuadFloat(samples[0, 0], samples[1, 1], samples[2, 2], samples[3, 3]), sharpen)
            val (diag2) = interpolateCubic(QuadFloat(samples[0, 3], samples[1, 2], samples[2, 1], samples[3, 0]), sharpen)
            val (comp1) = interpolateCubic(QuadFloat(horizontal[0], horizontal[1], horizontal[2], horizontal[3]), sharpen)
            val (comp2) = interpolateCubic(QuadFloat(vertical[0], vertical[1], vertical[2], vertical[3]), sharpen)
            return ((diag1 + diag2 + comp1 + comp2) * 0.25f).roundToInt().coerceIn(0, 65535)
        }
    }

    private fun column(horizontal: FloatArray, samples: FloatArrayMatrix, col: Int) {
        horizontal[col] = interpolateCubic(QuadFloat(samples[col, 0], samples[col, 1], samples[col, 2], samples[col, 3]), 1.0).first
    }

    private fun row(vertical: FloatArray, samples: FloatArrayMatrix, row: Int) {
        vertical[row] = interpolateCubic(QuadFloat(samples[0, row], samples[1, row], samples[2, row], samples[3, row]), 1.0).first
    }

    private fun interpolateCubic(heights: QuadFloat, sharpen: Double): Pair<Float, Float> {
        if (sharpen == 0.0) {
            return (heights.second + heights.third) * 0.5f to 0.0f
        } else {
            val controlHeights = computeControlHeights(heights)

            val p1y = controlHeights.first * 0.5
            val p2y = controlHeights.second * 0.5
            val p3y = controlHeights.third * 0.5
            val p4y = controlHeights.fourth * 0.5

            val d1a = (p1y + p2y) * 0.5
            val d1b = (p2y + p3y) * 0.5
            val d1c = (p3y + p4y) * 0.5

            val avg = p1y + p4y
            val diff = (((d1a + d1b) * 0.5) + ((d1b + d1c) * 0.5)) - avg
            return (avg + (diff * sharpen)).toFloat() to diff.toFloat()
        }
    }

    private fun computeControlHeights(heights: QuadFloat): QuadFloat {
        val r0 = heights.first + 2 * heights.second
        val r1 = (4 * heights.second + 2 * heights.third) - 0.5f * r0
        val r2 = (8 * heights.third + heights.fourth) - 0.57142857142f * r1

        val p12 = r2 / 6.42857142858f

        val p11 = (r1 - p12) / 3.5f
        val p21 = 2 * heights.third - p12

        return QuadFloat(heights.second, p11, p21, heights.third)
    }

    private fun sample(heightMap: ShortArray, stride: Int, x: Int, y: Int): Int {
        return heightMap[(y.coerceIn(0, stride - 1) * stride) + x.coerceIn(0, stride - 1)].toInt() and 0xFFFF
    }

    private fun Sequence<Float>.minMax(): Pair<Float, Float>? {
        val iterator = iterator()
        if (!iterator.hasNext()) return null
        var max = iterator.next()
        var min = max
        if (max.isNaN()) return max to max
        while (iterator.hasNext()) {
            val f = iterator.next()
            if (f.isNaN()) return f to f
            if (max < f) max = f
            if (min > f) min = f
        }
        return min to max
    }

}