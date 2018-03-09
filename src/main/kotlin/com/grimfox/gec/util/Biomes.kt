package com.grimfox.gec.util

import com.grimfox.gec.CACHE_DIR
import com.grimfox.gec.executor
import com.grimfox.gec.model.FloatArrayMatrix
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Point3F
import com.grimfox.gec.threadCount
import com.grimfox.gec.ui.widgets.TextureBuilder
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.buildTextureRedShort
import com.grimfox.joml.Matrix4f
import com.grimfox.joml.SimplexNoise.noise
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Math.*
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.*
import java.util.concurrent.Future
import javax.imageio.ImageIO

class Biomes {

    interface Shader {

        val positionAttribute: ShaderAttribute

        fun bind(
                textureScale: Float,
                borderDistanceScale: Float,
                heightScale: Float,
                landMask: TextureId,
                coastBorderMask: TextureId,
                biomeMask: TextureId,
                biomeBorderMask: TextureId,
                riverBorderMask: TextureId,
                mountainBorderMask: TextureId,
                customElevationPowerMap: TextureId,
                customStartingHeightsMap: TextureId,
                customSoilMobilityMap: TextureId)
    }

    fun ordinalToBiome(it: Int): Biome {
        return when (it) {
            0 -> MOUNTAINS_BIOME
            1 -> COASTAL_MOUNTAINS_BIOME
            2 -> FOOTHILLS_BIOME
            3 -> ROLLING_HILLS_BIOME
            4 -> PLAINS_BIOME
            5 -> PLATEAU_BIOME
            6 -> SHARP_PLATEAU_BIOME
            7 -> CUSTOM_BIOME
            else -> MOUNTAINS_BIOME
        }
    }

    val DEGREES_TO_SLOPES = degreesToSlopes()

    private val TALUS_ANGLES_SHARP_PLATEAU = buildParabolicTalusAngles(88.9f, 0.1f, 0.0f)

    private val TALUS_ANGLES_COASTAL_MOUNTAINS = buildParabolicTalusAngles(15.0f, 30.0f, 2.0f)

    private val TALUS_ANGLES_FOOTHILLS = buildParabolicTalusAngles(20.0f, 20.0f, 2.0f)

    private val TALUS_ANGLES_MOUNTAINS = buildParabolicTalusAngles(30.0f, 15.0f, 5.0f)

    private val TALUS_ANGLES_ROLLING_HILLS = buildNormalTalusAngles(30000.0f, 270.0f, 512.0f, 0.05f)

    private val TALUS_ANGLES_PLAINS = buildNormalTalusAngles(20000.0f, 310.0f, 512.0f, 0.005f)

    private val TALUS_ANGLES_PLATEAU = buildPlateauTalusAngles()

    private val TALUS_ANGLES_BASIC = buildParabolicTalusAngles(25.0f, 25.0f, 5.0f)

    private val TALUS_ANGLES_UNDERWATER = buildParabolicTalusAngles(70.0f, 15.0f, 0.0f)

    private fun buildPlateauTalusAngles(): Pair<FloatArray, FloatArray> {
        val profile = arrayOf(
                240 to 30.0f,
                520 to 89.0f,
                760 to 45.0f,
                990 to 89.0f,
                2000 to 0.05f)
        return FloatArray(1024) { computePlateauTalusAngle(it, profile) } to FloatArray(1024) { 0.0f }
    }

    private fun computePlateauTalusAngle(i: Int, profile: Array<Pair<Int, Float>>): Float {
        var currentLayer = profile.last()
        for (layer in profile) {
            if (i < layer.first) {
                currentLayer = layer
                break
            }
        }
        return (currentLayer.second / 90.0f).coerceIn(0.0f, 1.0f)
    }

    private fun degreesToSlopes(): FloatArray {
        return FloatArray(65536) { tan(toRadians(((it + 2.0) / 65540.0) * 90.0)).toFloat() }
    }

    private fun buildParabolicTalusAngles(minAngle: Float, deltaAngle: Float, jitter: Float): Pair<FloatArray, FloatArray> {
        val angleIncrement = deltaAngle / 1023.0f
        val baseAngles = FloatArray(1024) { ((minAngle + (it * angleIncrement)) / 90.0f).coerceIn(0.0f, 1.0f) }
        val jitters = FloatArray(1024) {
            val baseAngle = baseAngles[it]
            val low = Math.abs(baseAngle - 0.0f)
            val high = Math.abs(1.0f - baseAngle)
            Math.min(jitter / 90.0f, Math.min(low, high)).coerceIn(0.0f, 1.0f)
        }
        return baseAngles to jitters
    }

    private fun buildNormalTalusAngles(scale: Float, standardDeviation: Float, mean: Float, jitter: Float): Pair<FloatArray, FloatArray> {
        val term0 = -2.0 * standardDeviation * standardDeviation
        val term1 = scale * (1.0 / Math.sqrt(Math.PI * -term0))
        val baseAngles = FloatArray(1024)
        val jitters = FloatArray(1024)
        (0..1023).forEach {
            val (baseAngle, variance) = computeNormalTalusAngle(it, term0, term1, mean, jitter)
            baseAngles[it] = baseAngle
            jitters[it] = variance
        }
        return baseAngles to jitters
    }

    private fun computeNormalTalusAngle(i: Int, term0: Double, term1: Double, mean: Float, jitter: Float): Pair<Float, Float> {
        val baseAngle = ((term1 * pow(E, (((i - mean) * (i - mean)) / term0))) / 90.0).toFloat().coerceIn(0.0f, 1.0f)
        val low = Math.abs(baseAngle - 0.0f)
        val high = Math.abs(1.0f - baseAngle)
        val variance = Math.min(baseAngle * jitter, Math.min(low, high)).coerceIn(0.0f, 1.0f)
        return baseAngle to variance
    }

    private val noiseGraph128 = Graphs.generateGraph(128, 123, 0.98)
    private val noisePoints128 = arrayOfNulls<Point3F>(noiseGraph128.vertices.size)

    init {
        val vertices = noiseGraph128.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints128[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 127, point.y * 127)) / 128.0f)
        }
    }

    private val noiseGraph64 = Graphs.generateGraph(64, 456, 0.98)
    private val noisePoints64 = arrayOfNulls<Point3F>(noiseGraph64.vertices.size)

    init {
        val vertices = noiseGraph64.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints64[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 31, point.y * 31)) / 64.0f)
        }
    }

    private val noiseGraph32 = Graphs.generateGraph(32, 789, 0.98)
    private val noisePoints32 = arrayOfNulls<Point3F>(noiseGraph32.vertices.size)

    init {
        val vertices = noiseGraph32.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints32[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 17, point.y * 17)) / 32.0f)
        }
    }

    private val noiseGraph16 = Graphs.generateGraph(16, 12, 0.98)
    private val noisePoints16 = arrayOfNulls<Point3F>(noiseGraph16.vertices.size)

    init {
        val vertices = noiseGraph16.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints16[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 7, point.y * 7)) / 16.0f)
        }
    }

    interface TerraceFunction {

        fun apply(input: Float): Float
    }

    class BasicTerraceFunction(val implementation: (Float) -> Float) : TerraceFunction {

        override fun apply(input: Float): Float {
            return implementation(input)
        }
    }

    fun terraceFunction(apply: (Float) -> Float): TerraceFunction {
        return BasicTerraceFunction(apply)
    }

    class RegionData(
            val land: List<Int>,
            val water: List<Int>,
            val beach: LinkedHashSet<Int>)

    class ErosionSettings(
            val previousTierBlendWeight: Float,
            val elevationPowerMultiplier: Float,
            val soilMobilityMultiplier: Float,
            val terraceFunction: ((Float, Float) -> TerraceFunction)? = null)

    class Biome(
            val talusAngles: Pair<FloatArray, FloatArray>,
            val heightMultiplier: Float,
            val lowPassSettings: ErosionSettings,
            val midPassSettings: ErosionSettings,
            val highPassSettings: ErosionSettings,
            val elevationPowerShader: Shader,
            val startingHeightShader: Shader,
            val soilMobilityShader: Shader? = null)

    private fun loadTexture(name: String, noiseBuilder: (Int, ShortBuffer) -> Any, width: Int): TextureId {
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
//            val output = BufferedImage(width, width, BufferedImage.TYPE_USHORT_GRAY)
//            val raster = output.raster
//            for (i in 0..size - 1) {
//                raster.setSample(i % raster.width, i / raster.width, 0, shortData[i].toInt() and 0xFFFF)
//            }
//            ImageIO.write(output, "png", File(CACHE_DIR, "$name.png"))
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

    private val basicNoise = { width: Int, buffer: ShortBuffer ->
        for (y in 0..width - 1) {
            for (x in 0..width - 1) {
                buffer.put(y * width + x, (((noise(x * 0.5f, y * 0.5f) + 1.0f) / 2.0f) * 65535).toInt().toShort())
            }
        }
    }

    private val rollingHillsNoise = { width: Int, buffer: ShortBuffer ->
        val noiseGraph = Graphs.generateGraph(256, 6874475222, 0.98)
        val noisePoints = arrayOfNulls<Point3F>(noiseGraph.vertices.size)
        val vertices = noiseGraph.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 127, point.y * 127)) / 256.0f)
        }
        var max = -Float.MAX_VALUE
        var min = Float.MAX_VALUE
        val floatMatrix = FloatArrayMatrix(width)
        val futures = (0..threadCount - 1).map { thread ->
            executor.call {
                for (i in thread..floatMatrix.size.toInt() - 1 step threadCount) {
                    val point = Point2F(((i / width) + 0.5f) / width, ((i % width) + 0.5f) / width)
                    val point3d = Point3F(point.x, point.y, 0.0f)
                    val closePoints = noiseGraph.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints[it]!!)
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
                buffer.put(y * width + x, levels(height.toDouble(), 0.35, 2.0, 1.0))
            }
        }
    }

    private val foothillsNoise = { width: Int, buffer: ShortBuffer ->
        val noiseGraph = Graphs.generateGraph(256, 253487348644, 0.98)
        val noisePoints = arrayOfNulls<Point3F>(noiseGraph.vertices.size)
        val vertices = noiseGraph.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 127, point.y * 127)) / 256.0f)
        }
        var max = -Float.MAX_VALUE
        var min = Float.MAX_VALUE
        val floatMatrix = FloatArrayMatrix(width)
        val futures = (0..threadCount - 1).map { thread ->
            executor.call {
                for (i in thread..floatMatrix.size.toInt() - 1 step threadCount) {
                    val point = Point2F(((i / width) + 0.5f) / width, ((i % width) + 0.5f) / width)
                    val point3d = Point3F(point.x, point.y, 0.0f)
                    val closePoints = noiseGraph.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints[it]!!)
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
        val noiseGraph512 = Graphs.generateGraph(512, 136420669786, 0.98)
        val noisePoints512 = arrayOfNulls<Point3F>(noiseGraph512.vertices.size)
        val noiseGraph512Vertices = noiseGraph512.vertices
        for (i in 0..noiseGraph512Vertices.size - 1) {
            val point = noiseGraph512Vertices.getPoint(i)
            noisePoints512[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 512, point.y * 512)) / 512.0f)
        }
        val noiseGraph256 = Graphs.generateGraph(256, 7642367947869, 0.98)
        val noisePoints256 = arrayOfNulls<Point3F>(noiseGraph256.vertices.size)
        val noiseGraph256Vertices = noiseGraph256.vertices
        for (i in 0..noiseGraph256Vertices.size - 1) {
            val point = noiseGraph256Vertices.getPoint(i)
            noisePoints256[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 127, point.y * 127)) / 256)
        }
        val noiseGraph128 = Graphs.generateGraph(128, 458653243663, 0.98)
        val noisePoints128 = arrayOfNulls<Point3F>(noiseGraph128.vertices.size)
        val noiseGraph128Vertices = noiseGraph128.vertices
        for (i in 0..noiseGraph128Vertices.size - 1) {
            val point = noiseGraph128Vertices.getPoint(i)
            noisePoints128[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 63, point.y * 63)) / 128)
        }
        var max = -Float.MAX_VALUE
        var min = Float.MAX_VALUE
        val floatMatrix = FloatArrayMatrix(width)
        val futures = (0..threadCount - 1).map { thread ->
            executor.call {
                for (i in thread..floatMatrix.size.toInt() - 1 step threadCount) {
                    val point = Point2F(((i / width) + 0.5f) / width, ((i % width) + 0.5f) / width)
                    val point3d = Point3F(point.x, point.y, 0.0f)
                    val closePoints512 = noiseGraph512.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints512[it]!!)
                    }.sorted()
                    val closePoints256 = noiseGraph256.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints256[it]!!)
                    }.sorted()
                    val closePoints128 = noiseGraph128.getClosePoints(point, 3).map {
                        point3d.distance2(noisePoints128[it]!!)
                    }.sorted()
                    val height512 = (-closePoints512[0] + closePoints512[1]) * 4.0f
                    val height256 = (-closePoints256[0] + closePoints256[1]) * 2.0f
                    val height128 = -closePoints128[0] + closePoints128[1]
                    var sum = 0.0f
                    for (o in 0..7) {
                        val magnitude = octaves[o]
                        val multiplier = multipliers[o]
                        sum += ((noise(point.x * multiplier, point.y * multiplier, 0.0f) + 1) / 2.0f) * magnitude
                    }
                    val height = height512 + height256 + height128 + 0.001f * sum * sum
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
                buffer.put(y * width + x, levels(height.toDouble(), 0.055, 0.75, 0.77))
            }
        }
    }

    private val plainsNoise = { width: Int, buffer: ShortBuffer ->
        val noiseGraph128 = Graphs.generateGraph(128, 458653243663, 0.98)
        val noisePoints128 = arrayOfNulls<Point3F>(noiseGraph128.vertices.size)
        val noiseGraph128Vertices = noiseGraph128.vertices
        for (i in 0..noiseGraph128Vertices.size - 1) {
            val point = noiseGraph128Vertices.getPoint(i)
            noisePoints128[i] = Point3F(point.x, point.y, Math.abs(noise(point.x * 63, point.y * 63)) / 128.0f)
        }
        val noiseGraph64 = Graphs.generateGraph(64, 3766796523564, 0.98)
        val noisePoints64 = arrayOfNulls<Point3F>(noiseGraph64.vertices.size)
        val noiseGraph64Vertices = noiseGraph64.vertices
        for (i in 0..noiseGraph64Vertices.size - 1) {
            val point = noiseGraph64Vertices.getPoint(i)
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
                    val closePoints16 = noiseGraph64.getClosePoints(point, 3).map {
                        val otherPoint = noisePoints64[it]!!
                        point3d.distance2(Point3F(otherPoint.x, otherPoint.y, otherPoint.z * 0.3f))
                    }.sorted()
                    val closePoints32 = noiseGraph128.getClosePoints(point, 3).map {
                        val otherPoint = noisePoints128[it]!!
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
                buffer.put(y * width + x, levels(height.toDouble(), 0.4, 2.52, 1.0))
            }
        }
    }

    private val basicNoiseTexture = loadTexture("basic-noise", basicNoise, 1024)
    private val rollingHillsNoiseTexture = loadTexture("rolling-hills-noise", rollingHillsNoise, 1024)
    private val foothillsNoiseTexture = loadTexture("foothills-noise", foothillsNoise, 1024)
    private val mountainsNoiseTexture = loadTexture("mountains-noise", mountainsNoise, 1024)
    private val plainsNoiseTexture = loadTexture("plains-noise", plainsNoise, 1024)

    private val coastalMountainsElevationShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")

        val shaderProgram: TextureBuilder.ShaderProgramId
        init {
            try {
                shaderProgram = TextureBuilder.buildShaderProgram {
                    val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/coastal-mountains-biome.vert"))
                    val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/coastal-mountains-biome.frag"))
                    createAndLinkProgram(
                            listOf(vertexShader, fragmentShader),
                            listOf(positionAttribute),
                            listOf(mvpMatrixUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        override fun bind(
                textureScale: Float,
                borderDistanceScale: Float,
                heightScale: Float,
                landMask: TextureId,
                coastBorderMask: TextureId,
                biomeMask: TextureId,
                biomeBorderMask: TextureId,
                riverBorderMask: TextureId,
                mountainBorderMask: TextureId,
                customElevationPowerMap: TextureId,
                customStartingHeightsMap: TextureId,
                customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
        }
    }

    private val coastalMountainsStartingHeightsShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram: TextureBuilder.ShaderProgramId
        init {
            try {
                shaderProgram = TextureBuilder.buildShaderProgram {
                    val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/coastal-mountains-biome.vert"))
                    val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/coastal-mountains-biome-starting-heights.frag"))
                    createAndLinkProgram(
                            listOf(vertexShader, fragmentShader),
                            listOf(positionAttribute),
                            listOf(mvpMatrixUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }

        override fun bind(
                textureScale: Float,
                borderDistanceScale: Float,
                heightScale: Float,
                landMask: TextureId,
                coastBorderMask: TextureId,
                biomeMask: TextureId,
                biomeBorderMask: TextureId,
                riverBorderMask: TextureId,
                mountainBorderMask: TextureId,
                customElevationPowerMap: TextureId,
                customStartingHeightsMap: TextureId,
                customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.basicNoiseTexture.id)
        }
    }

    val COASTAL_MOUNTAINS_BIOME = Biome(
            elevationPowerShader = coastalMountainsElevationShader,
            startingHeightShader = coastalMountainsStartingHeightsShader,
            talusAngles = TALUS_ANGLES_COASTAL_MOUNTAINS,
            heightMultiplier = 1.0f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 1.0f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.8f,
                    soilMobilityMultiplier = 3.0f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.4f,
                    soilMobilityMultiplier = 1.8f))

    private val rollingHillsElevationShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/rolling-hills-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/rolling-hills-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.rollingHillsNoiseTexture.id)
        }
    }

    private val rollingHillsStartingHeightsShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/rolling-hills-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/rolling-hills-biome-starting-heights.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.rollingHillsNoiseTexture.id)
        }
    }

    val ROLLING_HILLS_BIOME = Biome(
            elevationPowerShader = rollingHillsElevationShader,
            startingHeightShader = rollingHillsStartingHeightsShader,
            talusAngles = TALUS_ANGLES_ROLLING_HILLS,
            heightMultiplier = 30.0f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 8.0f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.4f,
                    soilMobilityMultiplier = 4.0f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.4f,
                    soilMobilityMultiplier = 2.5f))

    private val foothillsElevationShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/foothills-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/foothills-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.foothillsNoiseTexture.id)
        }
    }

    private val foothillsStartingHeightsShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/foothills-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/foothills-biome-starting-heights.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.foothillsNoiseTexture.id)
        }
    }

    val FOOTHILLS_BIOME = Biome(
            elevationPowerShader = foothillsElevationShader,
            startingHeightShader = foothillsStartingHeightsShader,
            talusAngles = TALUS_ANGLES_FOOTHILLS,
            heightMultiplier = 1.0f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 1.0f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.5f,
                    soilMobilityMultiplier = 2.0f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.25f,
                    soilMobilityMultiplier = 2.0f))

    private val mountainsElevationShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/mountains-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/mountains-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.mountainsNoiseTexture.id)
        }
    }

    private val mountainsStartingHeightsShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/mountains-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/mountains-biome-starting-heights.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.mountainsNoiseTexture.id)
        }
    }

    val MOUNTAINS_BIOME = Biome(
            elevationPowerShader = mountainsElevationShader,
            startingHeightShader = mountainsStartingHeightsShader,
            talusAngles = TALUS_ANGLES_MOUNTAINS,
            heightMultiplier = 1.0f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 1.0f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.5f,
                    soilMobilityMultiplier = 1.0f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.2f,
                    soilMobilityMultiplier = 1.0f))

    private val plainsElevationShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/plains-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/plains-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.plainsNoiseTexture.id)
        }
    }

    private val plainsStartingHeightsShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/plains-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/plains-biome-starting-heights.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.plainsNoiseTexture.id)
        }
    }

    val PLAINS_BIOME = Biome(
            elevationPowerShader = plainsElevationShader,
            startingHeightShader = plainsStartingHeightsShader,
            talusAngles = TALUS_ANGLES_PLAINS,
            heightMultiplier = 20.0f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 5.0f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 5.0f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 3.0f))

    private val plateauBiomeElevationShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/plateau-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/plateau-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
        }
    }

    private val plateauBiomeStartingHeightsShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val mountainBorderDistanceTextureUniform = ShaderUniform("mountainBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/plateau-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/plateau-biome-starting-heights.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, mountainBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(mountainBorderDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 3)
            glActiveTexture(GL_TEXTURE3)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.basicNoiseTexture.id)
        }
    }

    val PLATEAU_BIOME = Biome(
            elevationPowerShader = plateauBiomeElevationShader,
            startingHeightShader = plateauBiomeStartingHeightsShader,
            talusAngles = TALUS_ANGLES_PLATEAU,
            heightMultiplier = 8.5f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 1.0f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.9f,
                    soilMobilityMultiplier = 0.2f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.1f,
                    soilMobilityMultiplier = 0.1f))

    private val sharpPlateauBiomeElevationShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/sharp-plateau-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/sharp-plateau-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, borderDistanceScaleUniform, riverBorderDistanceTextureUniform, coastDistanceTextureUniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
        }
    }

    private val sharpPlateauBiomeStartingHeightsShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val heightScaleUniform = ShaderUniform("heightScale")
        val riverBorderDistanceTextureUniform = ShaderUniform("riverBorderDistanceMask")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/sharp-plateau-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/sharp-plateau-biome-starting-heights.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, borderDistanceScaleUniform, heightScaleUniform, riverBorderDistanceTextureUniform, coastDistanceTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glUniform1f(heightScaleUniform.location, heightScale)
            glUniform1i(riverBorderDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, riverBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(noiseTexture1Uniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.basicNoiseTexture.id)
        }
    }

    val SHARP_PLATEAU_BIOME = Biome(
            elevationPowerShader = sharpPlateauBiomeElevationShader,
            startingHeightShader = sharpPlateauBiomeStartingHeightsShader,
            talusAngles = TALUS_ANGLES_SHARP_PLATEAU,
            heightMultiplier = 1.0f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 0.7f,
                    soilMobilityMultiplier = 0.1f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 0.4f,
                    elevationPowerMultiplier = 0.5f,
                    soilMobilityMultiplier = 0.01f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 0.15f,
                    elevationPowerMultiplier = 0.4f,
                    soilMobilityMultiplier = 0.15f,
                    terraceFunction = { min, max ->
                        val delta = max - min
                        val steps = listOf(
                                addTerraceStep(0.22f, 0.32f, 0.1f, min, delta),
                                addTerraceStep(0.50f, 0.24f, 0.1f, min, delta),
                                addTerraceStep(0.70f, 0.16f, 0.2f, min, delta),
                                addTerraceStep(0.81f, 0.06f, 0.35f, min, delta))
                        terraceFunction { input ->
                            applyTerrace(input, steps)
                        }
                    }))

    private val customElevationPowerShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val mapTextureUniform = ShaderUniform("map")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/custom-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/custom-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, mapTextureUniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(mapTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, customElevationPowerMap.id)
        }
    }

    private val customStartingHeightsShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val mapTextureUniform = ShaderUniform("map")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/custom-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/custom-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, mapTextureUniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(mapTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, customStartingHeightsMap.id)
        }
    }

    private val customSoilMobilityShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val mapTextureUniform = ShaderUniform("map")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/custom-biome.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/custom-biome.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, mapTextureUniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1i(mapTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, customSoilMobilityMap.id)
        }
    }

    val CUSTOM_BIOME = Biome(
            elevationPowerShader = customElevationPowerShader,
            startingHeightShader = customStartingHeightsShader,
            soilMobilityShader = customSoilMobilityShader,
            talusAngles = TALUS_ANGLES_BASIC,
            heightMultiplier = 1.0f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 1.0f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 1.0f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 1.0f))

    private fun applyTerrace(input: Float, steps: List<(Float) -> Float?>): Float {
        steps.forEach {
            val output = it(input)
            if (output != null) {
                return output
            }
        }
        return input
    }

    private fun addTerraceStep(height: Float, coverage: Float, compression: Float, min: Float, delta: Float): (Float) -> Float? {
        val midpoint = height * delta + min
        val adjustedCoverage = coverage * delta
        val halfCoverage = adjustedCoverage * 0.5f
        val minExtreme = midpoint - halfCoverage
        val maxExtreme = midpoint + halfCoverage
        return { input ->
            if (input > minExtreme && input <= maxExtreme) {
                ((input - minExtreme) * compression) + midpoint
            } else {
                null
            }
        }
    }

    private val underWaterElevationShader = object : Shader {

        val floatBuffer = BufferUtils.createFloatBuffer(16)

        init {
            val mvpMatrix = Matrix4f()
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 1.0f)
            mvpMatrix.get(0, floatBuffer)
        }

        override val positionAttribute = ShaderAttribute("position")

        val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
        val textureScaleUniform = ShaderUniform("textureScale")
        val borderDistanceScaleUniform = ShaderUniform("borderDistanceScale")
        val coastDistanceTextureUniform = ShaderUniform("coastDistanceMask")
        val landMaskTextureUniform = ShaderUniform("landMask")
        val noiseTexture1Uniform = ShaderUniform("noiseMask1")

        val shaderProgram = TextureBuilder.buildShaderProgram {
            val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/under-water.vert"))
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/under-water.frag"))
            createAndLinkProgram(
                    listOf(vertexShader, fragmentShader),
                    listOf(positionAttribute),
                    listOf(mvpMatrixUniform, textureScaleUniform, borderDistanceScaleUniform, coastDistanceTextureUniform, landMaskTextureUniform, noiseTexture1Uniform))
        }

        override fun bind(textureScale: Float,
                          borderDistanceScale: Float,
                          heightScale: Float,
                          landMask: TextureId,
                          coastBorderMask: TextureId,
                          biomeMask: TextureId,
                          biomeBorderMask: TextureId,
                          riverBorderMask: TextureId,
                          mountainBorderMask: TextureId,
                          customElevationPowerMap: TextureId,
                          customStartingHeightsMap: TextureId,
                          customSoilMobilityMap: TextureId) {
            glUseProgram(shaderProgram.id)
            glUniformMatrix4fv(mvpMatrixUniform.location, false, floatBuffer)
            glUniform1f(textureScaleUniform.location, textureScale)
            glUniform1f(borderDistanceScaleUniform.location, borderDistanceScale)
            glBindTexture(GL_TEXTURE_2D, mountainBorderMask.id)
            glUniform1i(coastDistanceTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, coastBorderMask.id)
            glUniform1i(landMaskTextureUniform.location, 1)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, landMask.id)
            glUniform1i(noiseTexture1Uniform.location, 2)
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, this@Biomes.mountainsNoiseTexture.id)
        }
    }

    val UNDER_WATER_BIOME = Biome(
            elevationPowerShader = underWaterElevationShader,
            startingHeightShader = underWaterElevationShader,
            talusAngles = TALUS_ANGLES_UNDERWATER,
            heightMultiplier = 1.0f,
            lowPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 0.2f),
            midPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 0.03f),
            highPassSettings = ErosionSettings(
                    previousTierBlendWeight = 1.0f,
                    elevationPowerMultiplier = 1.0f,
                    soilMobilityMultiplier = 0.035f))
}