package com.grimfox.gec.biomes

import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.Point3F
import com.grimfox.gec.util.Graphs
import org.joml.SimplexNoise.*
import java.lang.Math.*
import java.util.*

object Biomes {

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

    private val TALUS_ANGLES_NEVADA = buildNevadaTalusAngles()

    private fun buildNevadaTalusAngles(): FloatArray {
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
            val water: LinkedHashSet<Int>,
            val beach: LinkedHashSet<Int>,
            val regions: List<LinkedHashSet<Int>>,
            val regionBeaches: List<LinkedHashSet<Int>>,
            val regionBorders: List<LinkedHashSet<Int>>,
            val borderPairs: LinkedHashMap<Int, Pair<Int, Int>>)

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

    class ErosionBootstrap(
            val minUplift: Float,
            val deltaUplift: Float,
            val talusAngles: FloatArray,
            val deltaTime: Float,
            val heightMultiplier: Float,
            val erosionPower: Float,
            val upliftFunction: UpliftFunction)

    class Biome(
            val minUplift: Float,
            val deltaUplift: Float,
            val upliftFunction: UpliftFunction,
            val bootstrapSettings: ErosionSettings,
            val erosionLowSettings: ErosionLevel,
            val erosionMidSettings: ErosionLevel,
            val erosionHighSettings: ErosionLevel)

    val COASTAL_MOUNTAINS_BIOME = Biome(
            minUplift = 0.000004f,
            deltaUplift = 0.00049600005f,
            upliftFunction = CoastalMountainsUplift(),
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
                                    talusAngles = TALUS_ANGLES_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f),
                            ErosionSettings(
                                    iterations = 4,
                                    deltaTime = 75000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f),
                            ErosionSettings(
                                    iterations = 45,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_HIGH_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f))),
            erosionMidSettings = ErosionLevel(
                    upliftMultiplier = 0.5f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 25,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_MEDIUM_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f))),
            erosionHighSettings = ErosionLevel(
                    upliftMultiplier = 0.4f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 2,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_LOW_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f),
                            ErosionSettings(
                                    iterations = 3,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000000561f)))
    )

    private class CoastalMountainsUplift : UpliftFunction {

        override fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>, regionData: RegionData, biomeMask: Matrix<Byte>) {
            val remaining = HashSet(region)
            var currentIds = HashSet(borders)
            var nextIds = HashSet<Int>(borders.size)
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
            currentIds.addAll(beaches)
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
    }

    val ROLLING_HILLS_BIOME = Biome(
            minUplift = 0.00000065f,
            deltaUplift = 0.0000765f,
            upliftFunction = RollingHillsUplift(),
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

    private class RollingHillsUplift : UpliftFunction {

        override fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>, regionData: RegionData, biomeMask: Matrix<Byte>) {
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
                if (borders.contains(i)) {
                    val biome = biomeMask[i]
                    val borderPair = regionData.borderPairs[i]
                    if (borderPair != null && vertices.getAdjacentVertices(i).all { biomeMask[it] == biome}) {
                        upliftMap[i] = (-108).toByte()
                    } else {
                        val height = (rawUplifts[i] - min) / delta
                        upliftMap[i] = (Math.round(height * 253.0f) - 126).toByte()
                    }
                } else if (region.contains(i)) {
                    val height = (rawUplifts[i] - min) / delta
                    upliftMap[i] = (Math.round(height * 253.0f) - 126).toByte()
                }
            }
        }
    }

    val FOOTHILLS_BIOME = Biome(
            minUplift = 0.0000005f,
            deltaUplift = 0.0005f,
            upliftFunction = FoothillsUplift(),
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
                    upliftMultiplier = 0.5f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 5,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_LOW_VARIANCE,
                                    heightMultiplier = 1.0f,
                                    erosionPower = 0.000001f)))
    )

    private class FoothillsUplift : UpliftFunction {

        override fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>, regionData: RegionData, biomeMask: Matrix<Byte>) {
            var max = -Float.MAX_VALUE
            var min = Float.MAX_VALUE
            val rawUplifts = FloatArray(vertices.size) { i ->
                if (region.contains(i)) {
                    val point = vertices.getPoint(i)
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
                    height
                } else {
                    -1.0f
                }
            }
            val delta = max - min
            for (i in (0..upliftMap.size.toInt() - 1)) {
                if (borders.contains(i)) {
                    if (i % 3 == 0) {
                        upliftMap[i] = (-86).toByte()
                    } else {
                        upliftMap[i] = (127).toByte()
                    }
                } else if (region.contains(i)) {
                    val height = (rawUplifts[i] - min) / delta
                    upliftMap[i] = (Math.round(height * 253.0f) - 126).toByte()
                }
            }
        }
    }

    val MOUNTAINS_BIOME = Biome(
            minUplift = 0.000002f,
            deltaUplift = 0.00045f,
            upliftFunction = MountainsUplift(),
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
                    upliftMultiplier = 0.4f,
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

    private class MountainsUplift : UpliftFunction {

        override fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>, regionData: RegionData, biomeMask: Matrix<Byte>) {
            val octaves = floatArrayOf(0.3f, 0.25f, 0.2f, 0.15f, 0.03f, 0.025f, 0.02f, 0.015f, 0.006f, 0.004f)
            val multipliers = floatArrayOf(31.0f, 67.0f, 17.0f, 7.0f, 127.0f, 257.0f, 509.0f, 1021.0f, 2053.0f, 4093.0f)
            var max = -Float.MAX_VALUE
            var min = Float.MAX_VALUE
            val rawUplifts = FloatArray(vertices.size) { i ->
                if (region.contains(i)) {
                    val point = vertices.getPoint(i)
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
                    height
                } else {
                    -1.0f
                }
            }
            val delta = max - min
            for (i in (0..upliftMap.size.toInt() - 1)) {
                if (borders.contains(i)) {
                    val biome = biomeMask[i]
                    val borderPair = regionData.borderPairs[i]
                    if (borderPair != null && vertices.getAdjacentVertices(i).all { biomeMask[it] == biome}) {
                        upliftMap[i] = (-120).toByte()
                    } else {
                        val height = (rawUplifts[i] - min) / delta
                        upliftMap[i] = (Math.round(height * 253.0f) - 126).toByte()
                    }
                } else if (region.contains(i)) {
                    val height = (rawUplifts[i] - min) / delta
                    upliftMap[i] = (Math.round(height * 253.0f) - 126).toByte()
                }
            }
        }
    }

    val PLAINS_BIOME = Biome(
            minUplift = 0.0000015f,
            deltaUplift = 0.000008f,
            upliftFunction = PlainsUplift(),
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

    private class PlainsUplift : UpliftFunction {

        override fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>, regionData: RegionData, biomeMask: Matrix<Byte>) {
            var max = -Float.MAX_VALUE
            var min = Float.MAX_VALUE
            val rawUplifts = FloatArray(vertices.size) { i ->
                if (region.contains(i)) {
                    val point = vertices.getPoint(i)
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
                    height
                } else {
                    -1.0f
                }
            }
            val delta = max - min
            for (i in (0..upliftMap.size.toInt() - 1)) {
                if (borders.contains(i)) {
                    val biome = biomeMask[i]
                    val borderPair = regionData.borderPairs[i]
                    if (borderPair != null && vertices.getAdjacentVertices(i).all { biomeMask[it] == biome}) {
                        upliftMap[i] = (-126).toByte()
                    } else {
                        val height = (rawUplifts[i] - min) / delta
                        upliftMap[i] = (Math.round(height * 253.0f) - 126).toByte()
                    }
                } else if (region.contains(i)) {
                    val height = (rawUplifts[i] - min) / delta
                    upliftMap[i] = (Math.round(height * 253.0f) - 126).toByte()
                }
            }
        }
    }

    val PLATEAU_BIOME = Biome(
            minUplift = 0.000001f,
            deltaUplift = 0.00001f,
            upliftFunction = PlateauUplift(),
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
                                    talusAngles = TALUS_ANGLES_NEVADA,
                                    heightMultiplier = 10.0f,
                                    erosionPower = 0.0000004f))),
            erosionMidSettings = ErosionLevel(
                    upliftMultiplier = 0.9f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 25,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NEVADA,
                                    heightMultiplier = 10.0f,
                                    erosionPower = 0.0000001f))),
            erosionHighSettings = ErosionLevel(
                    upliftMultiplier = 0.0f,
                    erosionSettings = arrayListOf(
                            ErosionSettings(
                                    iterations = 5,
                                    deltaTime = 50000.0f,
                                    talusAngles = TALUS_ANGLES_NEVADA,
                                    heightMultiplier = 10.0f,
                                    erosionPower = 0.0000004f)))
    )

    private class PlateauUplift : UpliftFunction {

        override fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>, regionData: RegionData, biomeMask: Matrix<Byte>) {
            for (i in (0..upliftMap.size.toInt() - 1)) {
                if (beaches.contains(i)) {
                    upliftMap[i] = (-126).toByte()
                } else if (borders.contains(i)) {
                    val biome = biomeMask[i]
                    val borderPair = regionData.borderPairs[i]
                    if (borderPair != null && vertices.getAdjacentVertices(i).all { biomeMask[it] == biome}) {
                        upliftMap[i] = (-126).toByte()
                    } else {
                        upliftMap[i] = 127
                    }
                } else if (region.contains(i)) {
                    upliftMap[i] = 127
                }
            }
        }
    }
}