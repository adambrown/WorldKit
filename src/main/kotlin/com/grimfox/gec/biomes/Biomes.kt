package com.grimfox.gec.biomes

import com.grimfox.gec.CACHE_DIR
import com.grimfox.gec.executor
import com.grimfox.gec.extensions.call
import com.grimfox.gec.extensions.join
import com.grimfox.gec.extensions.value
import com.grimfox.gec.model.FloatArrayMatrix
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Point3F
import com.grimfox.gec.opengl.*
import com.grimfox.gec.threadCount
import com.grimfox.gec.ui.widgets.TextureBuilder
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.buildTextureRedShort
import com.grimfox.gec.util.Graphs
import org.joml.Matrix4f
import org.joml.SimplexNoise.noise
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL20.*
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Math.*
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.*
import java.util.concurrent.Future
import javax.imageio.ImageIO

object Biomes {

    fun init() {

    }

    interface UpliftShader {

        val positionAttribute: ShaderAttribute

        fun bind(regionMask: TextureId, biomeMask: TextureId, regionBorderMask: TextureId, coastBorderMask: TextureId, biomeBorderMask: TextureId)
    }

    private val TALUS_ANGLES_NO_VARIANCE = buildParabolicTalusAngles(15.0, 30.0, 0.0)

    private val TALUS_ANGLES_LOW_VARIANCE = buildParabolicTalusAngles(15.0, 30.0, 0.1)

    private val TALUS_ANGLES_MEDIUM_VARIANCE = buildParabolicTalusAngles(15.0, 30.0, 0.3)

    private val TALUS_ANGLES_HIGH_VARIANCE = buildParabolicTalusAngles(15.0, 30.0, 0.5)

    private val TALUS_ANGLES_HIGH_NO_VARIANCE = buildParabolicTalusAngles(30.0, 15.0, 0.0)

    private val TALUS_ANGLES_HIGH_LOW_VARIANCE = buildParabolicTalusAngles(30.0, 15.0, 0.1)

    private val TALUS_ANGLES_HIGH_MEDIUM_VARIANCE = buildParabolicTalusAngles(30.0, 15.0, 0.2)

    private val TALUS_ANGLES_HIGH_HIGH_VARIANCE = buildParabolicTalusAngles(30.0, 15.0, 0.5)

    private val TALUS_ANGLES_NORMAL_DISTRIBUTION = buildNormalTalusAngles(30000.0, 270.0, 512.0, 0.05)

    private val TALUS_ANGLES_NORMAL_DISTRIBUTION_FLAT = buildNormalTalusAngles(600.0, 310.0, 512.0, 0.005)

    private val TALUS_ANGLES_PLATEAU = buildPlateauTalusAngles()

    private fun buildPlateauTalusAngles(): FloatArray {
        val profiles = arrayOf(
                arrayOf(240 to 30.0,
                        520 to 89.0,
                        760 to 45.0,
                        990 to 89.0,
                        2000 to 0.05))
        return FloatArray(1024 * 256) { computeNevadaTalusAngle(it, profiles) }
    }

    private fun computeNevadaTalusAngle(i: Int, profiles: Array<Array<Pair<Int, Double>>>): Float {
        val height = i / 256
        val variance = i % 256
        val profile = profiles[Math.round(variance / 255.0f * (profiles.size - 1))]
        var currentLayer = profile.last()
        for (layer in profile) {
            if (height < layer.first) {
                currentLayer = layer
                break
            }
        }
        return tan(toRadians(currentLayer.second)).toFloat()
    }

    private fun buildParabolicTalusAngles(minAngle: Double, deltaAngle: Double, jitter: Double): FloatArray {
        val angleIncrement = deltaAngle / 1023.0
        return FloatArray(1024 * 256) { computeTalusAngle(it, minAngle, angleIncrement, jitter) }
    }

    private fun computeTalusAngle(i: Int, minAngle: Double, angleIncrement: Double, jitter: Double): Float {
        val height = i / 256
        val variance = i % 256
        val base = height * angleIncrement + minAngle
        val baseVariance = base * jitter
        return tan(toRadians(base + (variance / 255.0 * baseVariance * 2.0 - baseVariance))).toFloat()
    }

    private fun buildNormalTalusAngles(scale: Double, standardDeviation: Double, mean: Double, jitter: Double): FloatArray {
        val term0 = -2.0 * standardDeviation * standardDeviation
        val term1 = scale * (1.0 / Math.sqrt(Math.PI * -term0))
        return FloatArray(1024 * 256) { computeNormalTalusAngle(it, term0, term1, mean, jitter) }
    }

    private fun computeNormalTalusAngle(i: Int, term0: Double, term1: Double, mean: Double, jitter: Double): Float {
        val height = i / 256
        val variance = i % 256
        val base = term1 * pow(E, (((height - mean) * (height - mean)) / term0))
        val baseVariance = (base * jitter)
        val varianceMod = ((variance / 255.0) * baseVariance * 2.0) - baseVariance
        val angle = toRadians(base + varianceMod)
        return tan(angle).toFloat()
    }

    private val noiseGraph128 = Graphs.generateGraph(128, Random(123), 0.98)
    private val noisePoints128 = arrayOfNulls<Point3F>(noiseGraph128.vertices.size)

    init {
        val vertices = noiseGraph128.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints128[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 127, point.y * 127)) / 128.0f)
        }
    }

    private val noiseGraph64 = Graphs.generateGraph(64, Random(456), 0.98)
    private val noisePoints64 = arrayOfNulls<Point3F>(noiseGraph64.vertices.size)

    init {
        val vertices = noiseGraph64.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints64[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 31, point.y * 31)) / 64.0f)
        }
    }

    private val noiseGraph32 = Graphs.generateGraph(32, Random(789), 0.98)
    private val noisePoints32 = arrayOfNulls<Point3F>(noiseGraph32.vertices.size)

    init {
        val vertices = noiseGraph32.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints32[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 17, point.y * 17)) / 32.0f)
        }
    }

    private val noiseGraph16 = Graphs.generateGraph(16, Random(12), 0.98)
    private val noisePoints16 = arrayOfNulls<Point3F>(noiseGraph16.vertices.size)

    init {
        val vertices = noiseGraph16.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints16[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 7, point.y * 7)) / 16.0f)
        }
    }

    class RegionData(
            val land: List<Int>,
            val beach: LinkedHashSet<Int>,
            val regions: List<LinkedHashSet<Int>>)

    interface UpliftFunction {

        fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>, regionData: RegionData, biomeMask: Matrix<Byte>)
    }

    class ErosionSettings(
            val iterations: Int,
            val deltaTime: Float,
            val talusAngles: FloatArray,
            val heightMultiplier: Float,
            val erosionPower: Float)

    class ErosionLevel(
            val upliftMultiplier: Float,
            val erosionSettings: List<ErosionSettings>)

    class Biome(
            val minUplift: Float,
            val deltaUplift: Float,
            val bootstrapSettings: ErosionSettings,
            val erosionLowSettings: ErosionLevel,
            val erosionMidSettings: ErosionLevel,
            val erosionHighSettings: ErosionLevel,
            val upliftShader: UpliftShader)

    private fun loadTexture(name: String, noiseBuilder: (Int, ShortBuffer) -> Any, width: Int): Future<TextureId> {
        return executor.call {
            loadTextureSync(name, noiseBuilder, width)
        }
    }

    private fun loadTextureSync(name: String, noiseBuilder: (Int, ShortBuffer) -> Any, width: Int): TextureId {
        val data = writeNoiseTextureIfNotExists(name, noiseBuilder, width)
        return buildTextureRedShort(data, width, GL_LINEAR, GL_LINEAR)
    }

    private fun writeNoiseTextureIfNotExists(name: String, noiseBuilder: (Int, ShortBuffer) -> Any, width: Int): ShortBuffer {
        val file = File(CACHE_DIR, "$name.tex")
        val size = width * width
        val size2 = size * 2
        if (!file.exists()) {
            val data = BufferUtils.createByteBuffer(size * 2).order(ByteOrder.nativeOrder())
            val shortData = data.asShortBuffer()
            noiseBuilder(width, shortData)
            val output = BufferedImage(width, width, BufferedImage.TYPE_USHORT_GRAY)
            val raster = output.raster
            for (i in 0..size - 1) {
                raster.setSample(i % raster.width, i / raster.width, 0, shortData[i].toInt() and 0xFFFF)
            }
            ImageIO.write(output, "png", File(CACHE_DIR, "$name.png"))
            file.outputStream().channel.use {
                var written = 0
                while (written < size2) {
                    written += it.write(data)
                }
            }
            data.flip()
            return shortData
        } else {
            val data = BufferUtils.createByteBuffer(size * 2).order(ByteOrder.nativeOrder())
            val shortData = data.asShortBuffer()
            file.inputStream().channel.use {
                var read = 0
                while (read < size2) {
                    read += it.read(data)
                }
            }
            data.flip()
            return shortData
        }
    }

    private fun levels(height: Double, inMin: Double, gamma: Double, inMax: Double): Short {
        return 65535.coerceAtMost(0.coerceAtLeast((pow((height - inMin) / (inMax - inMin), gamma) * 65535).toInt())).toShort()
    }

    private val rollingHillsNoise = { width: Int, buffer: ShortBuffer ->
        val noiseGraph64 = Graphs.generateGraph(64, Random(6874475222), 0.98)
        val noisePoints64 = arrayOfNulls<Point3F>(noiseGraph64.vertices.size)
        val vertices = noiseGraph64.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints64[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 31, point.y * 31)) / 64.0f)
        }
        var max = -Float.MAX_VALUE
        var min = Float.MAX_VALUE
        val floatMatrix = FloatArrayMatrix(width)
        val futures = (0..threadCount - 1).map { thread ->
            executor.call {
                for (i in thread..floatMatrix.size.toInt() - 1 step threadCount) {
                    val point = Point2F(((i / width) + 0.5f) / width, ((i % width) + 0.5f) / width)
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
                    floatMatrix[i] = height
                }
            }
        }
        futures.forEach(Future<Unit>::join)
        val delta = max - min
        for (y in 0..width - 1) {
            for (x in 0..width - 1) {
                val height = (floatMatrix[x, y] - min) / delta
                buffer.put(y * width + x, levels(height.toDouble(), 0.35, 1.85, 1.0))
            }
        }
    }

    private val foothillsNoise = { width: Int, buffer: ShortBuffer ->
        val noiseGraph64 = Graphs.generateGraph(64, Random(253487348644), 0.98)
        val noisePoints64 = arrayOfNulls<Point3F>(noiseGraph64.vertices.size)
        val vertices = noiseGraph64.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints64[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 31, point.y * 31)) / 64.0f)
        }
        var max = -Float.MAX_VALUE
        var min = Float.MAX_VALUE
        val floatMatrix = FloatArrayMatrix(width)
        val futures = (0..threadCount - 1).map { thread ->
            executor.call {
                for (i in thread..floatMatrix.size.toInt() - 1 step threadCount) {
                    val point = Point2F(((i / width) + 0.5f) / width, ((i % width) + 0.5f) / width)
                    val point3d = Point3F(point.x, point.y, 0.0f)
                    val closePoints = noiseGraph64.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints64[it]!!)
                    }.sorted()
                    val height = -closePoints[0] + closePoints[1]
                    if (height > max) {
                        max = height
                    }
                    if (height < min) {
                        min = height
                    }
                    floatMatrix[i] = height
                }
            }
        }
        futures.forEach(Future<Unit>::join)
        val delta = max - min
        for (y in 0..width - 1) {
            for (x in 0..width - 1) {
                val height = (floatMatrix[x, y] - min) / delta
                buffer.put(y * width + x, levels(height.toDouble(), 0.0, 0.4, 0.6))
            }
        }
    }

    private val mountainsNoise = { width: Int, buffer: ShortBuffer ->
        val octaves = floatArrayOf(0.3f, 0.25f, 0.2f, 0.15f, 0.03f, 0.025f, 0.02f, 0.015f, 0.006f, 0.004f)
        val multipliers = floatArrayOf(31.0f, 67.0f, 17.0f, 7.0f, 127.0f, 257.0f, 509.0f, 1021.0f, 2053.0f, 4093.0f)
        val noiseGraph128 = Graphs.generateGraph(128, Random(136420669786), 0.98)
        val noisePoints128 = arrayOfNulls<Point3F>(noiseGraph128.vertices.size)
        val noiseGraph128Vertices = noiseGraph128.vertices
        for (i in 0..noiseGraph128Vertices.size - 1) {
            val point = noiseGraph128Vertices.getPoint(i)
            noisePoints128[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 127, point.y * 127)) / 128.0f)
        }
        val noiseGraph64 = Graphs.generateGraph(64, Random(7642367947869), 0.98)
        val noisePoints64 = arrayOfNulls<Point3F>(noiseGraph64.vertices.size)
        val noiseGraph64Vertices = noiseGraph64.vertices
        for (i in 0..noiseGraph64Vertices.size - 1) {
            val point = noiseGraph64Vertices.getPoint(i)
            noisePoints64[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 31, point.y * 31)) / 64.0f)
        }
        val noiseGraph32 = Graphs.generateGraph(32, Random(458653243663), 0.98)
        val noisePoints32 = arrayOfNulls<Point3F>(noiseGraph32.vertices.size)
        val noiseGraph32Vertices = noiseGraph32.vertices
        for (i in 0..noiseGraph32Vertices.size - 1) {
            val point = noiseGraph32Vertices.getPoint(i)
            noisePoints32[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 17, point.y * 17)) / 32.0f)
        }
        var max = -Float.MAX_VALUE
        var min = Float.MAX_VALUE
        val floatMatrix = FloatArrayMatrix(width)
        val futures = (0..threadCount - 1).map { thread ->
            executor.call {
                for (i in thread..floatMatrix.size.toInt() - 1 step threadCount) {
                    val point = Point2F(((i / width) + 0.5f) / width, ((i % width) + 0.5f) / width)
                    val point3d = Point3F(point.x, point.y, 0.0f)
                    val closePoints128 = noiseGraph128.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints128[it]!!)
                    }.sorted()
                    val closePoints64 = noiseGraph64.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints64[it]!!)
                    }.sorted()
                    val closePoints32 = noiseGraph32.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints32[it]!!)
                    }.sorted()
                    val height128 = (-closePoints128[0] + closePoints128[1]) * 4.0f
                    val height64 = (-closePoints64[0] + closePoints64[1]) * 2.0f
                    val height32 = -closePoints32[0] + closePoints32[1]
                    var sum = 0.0f
                    for (o in 0..7) {
                        val magnitude = octaves[o]
                        val multiplier = multipliers[o]
                        sum += ((noise(point.x * multiplier, point.y * multiplier, 0.0f) + 1) / 2.0f) * magnitude
                    }
                    val height = height128 + height64 + height32 + 0.001f * sum * sum
                    if (height > max) {
                        max = height
                    }
                    if (height < min) {
                        min = height
                    }
                    floatMatrix[i] = height
                }
            }
        }
        futures.forEach(Future<Unit>::join)
        val delta = max - min
        for (y in 0..width - 1) {
            for (x in 0..width - 1) {
                val height = (floatMatrix[x, y] - min) / delta
                buffer.put(y * width + x, levels(height.toDouble(), 0.02, 0.7, 0.52))
            }
        }
    }

    private val plainsNoise = { width: Int, buffer: ShortBuffer ->
        val noiseGraph32 = Graphs.generateGraph(32, Random(458653243663), 0.98)
        val noisePoints32 = arrayOfNulls<Point3F>(noiseGraph32.vertices.size)
        val noiseGraph32Vertices = noiseGraph32.vertices
        for (i in 0..noiseGraph32Vertices.size - 1) {
            val point = noiseGraph32Vertices.getPoint(i)
            noisePoints32[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 17, point.y * 17)) / 32.0f)
        }
        val noiseGraph16 = Graphs.generateGraph(16, Random(3766796523564), 0.98)
        val noisePoints16 = arrayOfNulls<Point3F>(noiseGraph16.vertices.size)
        val noiseGraph16Vertices = noiseGraph16.vertices
        for (i in 0..noiseGraph16Vertices.size - 1) {
            val point = noiseGraph16Vertices.getPoint(i)
            noisePoints16[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 7, point.y * 7)) / 16.0f)
        }
        var max = -Float.MAX_VALUE
        var min = Float.MAX_VALUE
        val floatMatrix = FloatArrayMatrix(width)
        val futures = (0..threadCount - 1).map { thread ->
            executor.call {
                for (i in thread..floatMatrix.size.toInt() - 1 step threadCount) {
                    val point = Point2F(((i / width) + 0.5f) / width, ((i % width) + 0.5f) / width)
                    val point3d = Point3F(point.x, point.y, 0.0f)
                    val closePoints16 = noiseGraph16.getClosePoints(point, 3).map {
                        val otherPoint = noisePoints16[it]!!
                        point3d.distance2(Point3F(otherPoint.x, otherPoint.y, otherPoint.z * 0.3f))
                    }.sorted()
                    val closePoints32 = noiseGraph32.getClosePoints(point, 3).map {
                        val otherPoint = noisePoints32[it]!!
                        point3d.distance2(Point3F(otherPoint.x, otherPoint.y, otherPoint.z * 0.1f))
                    }.sorted()
                    val height = -closePoints16[0] - closePoints32[0] * 0.5f
                    if (height > max) {
                        max = height
                    }
                    if (height < min) {
                        min = height
                    }
                    floatMatrix[i] = height
                }
            }
        }
        futures.forEach(Future<Unit>::join)
        val delta = max - min
        for (y in 0..width - 1) {
            for (x in 0..width - 1) {
                val height = (floatMatrix[x, y] - min) / delta
                buffer.put(y * width + x, levels(height.toDouble(), 0.4, 2.4, 1.0))
            }
        }
    }

    private val rollingHillsNoiseTextureFuture = loadTexture("rolling-hills-noise", rollingHillsNoise, 1024)
    private val foothillsNoiseTextureFuture = loadTexture("foothills-noise", foothillsNoise, 1024)
    private val mountainsNoiseTextureFuture = loadTexture("mountains-noise", mountainsNoise, 1024)
    private val plainsNoiseTextureFuture = loadTexture("plains-noise", plainsNoise, 1024)

    private val coastalMountainsUpliftShader = object : UpliftShader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val regionBorderDistanceTextureUniform = ShaderUniform("regionBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL20.GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/coastal-mountains-biome.vert"))
            val fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/coastal-mountains-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, regionBorderDistanceTextureUniform, coastDistanceTextureUniform))
        }

        override fun bind(regionMask: TextureId, biomeMask: TextureId, regionBorderMask: TextureId, coastBorderMask: TextureId, biomeBorderMask: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(regionBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, regionBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
        }
    }

    val COASTAL_MOUNTAINS_BIOME = Biome(
            minUplift = 0.000004f,
            deltaUplift = 0.00045f,
            upliftShader = coastalMountainsUpliftShader,
            bootstrapSettings = ErosionSettings(
                    iterations = 1,
                    deltaTime = 85000.0f,
                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                    heightMultiplier = 1.0f,
                    erosionPower = 0.0000006f),
            erosionLowSettings = ErosionLevel(
                    upliftMultiplier = 1.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 1,
                                    deltaTime = 3000000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.0000006f),
                            ErosionSettings(
                                    iterations = 4,
                                    deltaTime = 75000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.0000006f),
                            ErosionSettings(
                                    iterations = 45,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.0000006f))),
            erosionMidSettings = ErosionLevel(
                    upliftMultiplier = 0.35f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 25,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_MEDIUM_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.0000006f))),
            erosionHighSettings = ErosionLevel(
                    upliftMultiplier = 0.15f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 2,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_LOW_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.0000006f),
                            ErosionSettings(
                                    iterations = 3,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.0000006f)))
    )

    val rollingHillsNoiseTexture by lazy { rollingHillsNoiseTextureFuture.value }

    private val rollingHillsUpliftShader = object : UpliftShader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val regionBorderDistanceTextureUniform = ShaderUniform("regionBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL20.GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/rolling-hills-biome.vert"))
            val fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/rolling-hills-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, regionBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(regionMask: TextureId, biomeMask: TextureId, regionBorderMask: TextureId, coastBorderMask: TextureId, biomeBorderMask: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(regionBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, regionBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, rollingHillsNoiseTexture.id)
        }
    }

    val ROLLING_HILLS_BIOME = Biome(
            minUplift = 0.000001f,
            deltaUplift = 0.000074f,
            upliftShader = rollingHillsUpliftShader,
            bootstrapSettings = ErosionSettings(
                    iterations = 1,
                    deltaTime = 85000.0f,
                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                    heightMultiplier = 1.0f,
                    erosionPower = 0.000000561f),
            erosionLowSettings = ErosionLevel(
                    upliftMultiplier = 1.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 1,
                                    deltaTime = 3000000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION,
                                    heightMultiplier = 11.0f,
                                    erosionPower = 0.000004488f),
                            ErosionSettings(
                                    iterations = 4,
                                    deltaTime = 75000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION,
                                    heightMultiplier = 11.0f,
                                    erosionPower = 0.000004488f),
                            ErosionSettings(
                                    iterations = 45,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION,
                                    heightMultiplier = 11.0f,
                                    erosionPower = 0.000004488f))),
            erosionMidSettings = ErosionLevel(
                    upliftMultiplier = 0.4f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 25,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION,
                                    heightMultiplier = 11.0f,
                                    erosionPower = 0.000002244f))),
            erosionHighSettings = ErosionLevel(
                    upliftMultiplier = 0.4f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 5,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION,
                                    heightMultiplier = 11.0f,
                                    erosionPower = 0.0000014025f)))
    )

    val foothillsNoiseTexture by lazy { foothillsNoiseTextureFuture.value }

    private val foothillsUpliftShader = object : UpliftShader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val regionBorderDistanceTextureUniform = ShaderUniform("regionBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL20.GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/foothills-biome.vert"))
            val fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/foothills-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, regionBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(regionMask: TextureId, biomeMask: TextureId, regionBorderMask: TextureId, coastBorderMask: TextureId, biomeBorderMask: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(regionBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, regionBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, foothillsNoiseTexture.id)
        }
    }


    val FOOTHILLS_BIOME = Biome(
            minUplift = 0.0000001f,
            deltaUplift = 0.0003f,
            upliftShader = foothillsUpliftShader,
            bootstrapSettings = ErosionSettings(
                    iterations = 1,
                    deltaTime = 85000.0f,
                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                    heightMultiplier = 1.0f,
                    erosionPower = 0.0000006f),
            erosionLowSettings = ErosionLevel(
                    upliftMultiplier = 1.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 50,
                                    deltaTime = 3000000.0f,
                                    talusAngles = TALUS_ANGLES_LOW_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000002f))),
            erosionMidSettings = ErosionLevel(
                    upliftMultiplier = 0.5f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 25,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_LOW_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000001f))),
            erosionHighSettings = ErosionLevel(
                    upliftMultiplier = 0.25f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 5,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_LOW_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000001f)))
    )

    val mountainsNoiseTexture by lazy { mountainsNoiseTextureFuture.value }

    private val mountainsUpliftShader = object : UpliftShader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val regionBorderDistanceTextureUniform = ShaderUniform("regionBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL20.GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/mountains-biome.vert"))
            val fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/mountains-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, regionBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(regionMask: TextureId, biomeMask: TextureId, regionBorderMask: TextureId, coastBorderMask: TextureId, biomeBorderMask: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(regionBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, regionBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, mountainsNoiseTexture.id)
        }
    }


    val MOUNTAINS_BIOME = Biome(
            minUplift = 0.0000001f,
            deltaUplift = 0.0004519f,
            upliftShader = mountainsUpliftShader,
            bootstrapSettings = ErosionSettings(
                    iterations = 1,
                    deltaTime = 85000.0f,
                    talusAngles = TALUS_ANGLES_HIGH_NO_VARIANCE,
                    heightMultiplier = 1.0f,
                    erosionPower = 0.000000561f),
            erosionLowSettings = ErosionLevel(
                    upliftMultiplier = 1.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 1,
                                    deltaTime = 3000000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f),
                            ErosionSettings(
                                    iterations = 4,
                                    deltaTime = 75000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f),
                            ErosionSettings(
                                    iterations = 45,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f))),
            erosionMidSettings = ErosionLevel(
                    upliftMultiplier = 0.5f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 25,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_MEDIUM_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f))),
            erosionHighSettings = ErosionLevel(
                    upliftMultiplier = 0.2f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 2,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_LOW_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f),
                            ErosionSettings(
                                    iterations = 3,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_NO_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f)))
    )

    val plainsNoiseTexture by lazy { plainsNoiseTextureFuture.value }

    private val plainsUpliftShader = object : UpliftShader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val regionBorderDistanceTextureUniform = ShaderUniform("regionBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL20.GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/plains-biome.vert"))
            val fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/plains-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, regionBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(regionMask: TextureId, biomeMask: TextureId, regionBorderMask: TextureId, coastBorderMask: TextureId, biomeBorderMask: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(regionBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, regionBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, plainsNoiseTexture.id)
        }
    }

    val PLAINS_BIOME = Biome(
            minUplift = 0.0000015f,
            deltaUplift = 0.00001f,
            upliftShader = plainsUpliftShader,
            bootstrapSettings = ErosionSettings(
                    iterations = 1,
                    deltaTime = 85000.0f,
                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                    heightMultiplier = 1.0f,
                    erosionPower = 0.000000561f),
            erosionLowSettings = ErosionLevel(
                    upliftMultiplier = 1.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 1,
                                    deltaTime = 3000000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION_FLAT,
                                    heightMultiplier = 100.0f,
                                    erosionPower = 0.000003f),
                            ErosionSettings(
                                    iterations = 4,
                                    deltaTime = 75000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION_FLAT,
                                    heightMultiplier = 100.0f,
                                    erosionPower = 0.000003f),
                            ErosionSettings(
                                    iterations = 45,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION_FLAT,
                                    heightMultiplier = 100.0f,
                                    erosionPower = 0.000004f))),
            erosionMidSettings = ErosionLevel(
                    upliftMultiplier = 1.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 25,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION_FLAT,
                                    heightMultiplier = 100.0f,
                                    erosionPower = 0.000004f))),
            erosionHighSettings = ErosionLevel(
                    upliftMultiplier = 1.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 5,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION_FLAT,
                                    heightMultiplier = 100.0f,
                                    erosionPower = 0.0000028f)))
    )

    private val plateauBiomeUpliftShader = object : UpliftShader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val regionBorderDistanceTextureUniform = ShaderUniform("regionBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL20.GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/plateau-biome.vert"))
            val fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/plateau-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, regionBorderDistanceTextureUniform, coastDistanceTextureUniform))
        }

        override fun bind(regionMask: TextureId, biomeMask: TextureId, regionBorderMask: TextureId, coastBorderMask: TextureId, biomeBorderMask: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(regionBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, regionBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
        }
    }


    val PLATEAU_BIOME = Biome(
            minUplift = 0.000001f,
            deltaUplift = 0.00001f,
            upliftShader = plateauBiomeUpliftShader,
            bootstrapSettings = ErosionSettings(
                    iterations = 1,
                    deltaTime = 85000.0f,
                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                    heightMultiplier = 10.0f,
                    erosionPower = 0.0000004f),
            erosionLowSettings = ErosionLevel(
                    upliftMultiplier = 1.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 50,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_PLATEAU,
                                    heightMultiplier = 10.0f,
                                    erosionPower = 0.0000004f))),
            erosionMidSettings = ErosionLevel(
                    upliftMultiplier = 0.9f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 25,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_PLATEAU,
                                    heightMultiplier = 10.0f,
                                    erosionPower = 0.0000001f))),
            erosionHighSettings = ErosionLevel(
                    upliftMultiplier = 0.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 5,
                                    deltaTime = 50000.0f,
                                    talusAngles = TALUS_ANGLES_PLATEAU,
                                    heightMultiplier = 10.0f,
                                    erosionPower = 0.0000004f)))
    )
}