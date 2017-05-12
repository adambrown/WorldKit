package com.grimfox.gec.biomes

import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.Point3F
import com.grimfox.gec.util.Graphs
import org.joml.SimplexNoise
import java.lang.Math.*
import java.util.*

object Biomes {

    private val HEIGHT_INCREMENT = 30.0 / 1023.0

    private val TALUS_ANGLES_NO_VARIANCE = FloatArray(1024 * 256) { computeTalusAngle(it, 0.0) }

    private val TALUS_ANGLES_LOW_VARIANCE = FloatArray(1024 * 256) { computeTalusAngle(it, 0.1) }

    private val TALUS_ANGLES_MEDIUM_VARIANCE = FloatArray(1024 * 256) { computeTalusAngle(it, 0.3) }

    private val TALUS_ANGLES_HIGH_VARIANCE = FloatArray(1024 * 256) { computeTalusAngle(it, 0.5) }

    private val TALUS_ANGLES_NORMAL_DISTRIBUTION = buildNormalTalusAngles(30000.0, 270.0, 512.0, 0.05)

    private fun computeTalusAngle(i: Int, jitter: Double): Float {
        val height = i / 256
        val variance = i % 256
        val base = height * HEIGHT_INCREMENT + 15
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

    interface UpliftFunction {

        fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>)
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
            val bootstrapSettings: ErosionBootstrap,
            val erosionLowSettings: ErosionLevel,
            val erosionMidSettings: ErosionLevel,
            val erosionHighSettings: ErosionLevel)

    val COASTAL_MOUNTAINS_BIOME = Biome(
            bootstrapSettings = ErosionBootstrap(
                    minUplift = 0.000004f,
                    deltaUplift = 0.00049600005f,
                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                    deltaTime = 85000.0f,
                    heightMultiplier = 1.0f,
                    erosionPower = 0.000000561f,
                    upliftFunction = CoastalMountainsUplift()),
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

        override fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>) {
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
            bootstrapSettings = ErosionBootstrap(
                    minUplift = 0.0000006f,
                    deltaUplift = 0.0000744f,
                    talusAngles = TALUS_ANGLES_NO_VARIANCE,
                    deltaTime = 85000.0f,
                    heightMultiplier = 1.0f,
                    erosionPower = 0.000000561f,
                    upliftFunction = RollingHillsUplift()),
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
                                    iterations = 2,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION,
                                    heightMultiplier = 11.0f,
                                    erosionPower = 0.0000014025f),
                            ErosionSettings(
                                    iterations = 3,
                                    deltaTime = 250000.0f,
                                    talusAngles = TALUS_ANGLES_NORMAL_DISTRIBUTION,
                                    heightMultiplier = 11.0f,
                                    erosionPower = 0.0000014025f)))
    )

    private val noiseGraph64 = Graphs.generateGraph(64, Random(123), 0.98)
    private val noisePoints64 = arrayOfNulls<Point3F>(noiseGraph64.vertices.size)

    init {
        val vertices = noiseGraph64.vertices
        for (i in 0..vertices.size - 1) {
            val point = vertices.getPoint(i)
            noisePoints64[i] = Point3F(point.x, point.y, Math.abs(SimplexNoise.noise(point.x * 31, point.y * 31)) / 64.0f)
        }
    }

    private class RollingHillsUplift : UpliftFunction {

        override fun buildUpliftMap(vertices: Vertices, region: LinkedHashSet<Int>, beaches: LinkedHashSet<Int>, borders: LinkedHashSet<Int>, upliftMap: Matrix<Byte>) {
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
    }
}