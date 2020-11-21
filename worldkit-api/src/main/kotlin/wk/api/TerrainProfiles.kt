package wk.api

import org.joml.SimplexNoise
import wk.internal.ext.fastFloorI
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.*

typealias TerraceFunction = (input: Float, simplexX: Float, simplexY: Float, jitter: Float) -> Float

private const val inverse1023 = 1.0f / 1023.0f
private const val inverse255 = 1.0f / 255.0f
private const val byte0: Byte = 0

private val anglesToSlopes = FloatArray(65536) { tan(Math.toRadians(((it + 2.0) / 65540.0) * 90.0)).toFloat() }

class ControlValues(val base: Float, val variance: Float)

operator fun List<ControlValues>.get(f: Float): Pair<Float, Float> {
    val lookup = f.coerceIn(0.0f, 1.0f) * (size - 1.0f)
    val i1 = fastFloorI(lookup)
    val alpha2 = lookup - i1
    val alpha1 = 1.0f - alpha2
    val i2 = (i1 + 1).coerceIn(indices)
    val cv1 = get(i1)
    val cv2 = get(i2)
    return Pair(cv1.base * alpha1 + cv2.base * alpha2, cv1.variance * alpha1 + cv2.variance * alpha2)
}

class ErosionSettings(
        val upliftPower: Float,
        val erosionPower: Float,
        val talusOverride: Float? = null,
        val terraceJitter: Float = 0.0f,
        val terraceJitterFrequency: Float = 1.0f,
        val terraceFunction: ((Float, Float) -> TerraceFunction)? = null)

class TerrainProfile(
        val talusAngles: List<ControlValues>,
        val upliftConstant: Float,
        val upliftNoise: ShortArrayMatrix?,
        val heightScale: Float,
        val erosionSettings: List<ErosionSettings>)

class UnderwaterProfile(
        val talusAngles: List<ControlValues>,
        val seaFloorNoise: ShortArrayMatrix,
        val heightScale: Float,
        val erosionPowers: List<Float>)


fun angleToSlope(normalizedTalusAngle: Float) = anglesToSlopes[(normalizedTalusAngle * 65535.0f).roundToInt().coerceIn(0, 65535)]

fun buildLinearTalusAngles(minAngleDegrees: Float, deltaAngleDegrees: Float, varianceDegrees: Float): List<ControlValues> {
    val angleIncrement = deltaAngleDegrees / 1023.0f
    val angles = ArrayList<ControlValues>(1024)
    (0..1023).forEach {
        val baseAngle = ((minAngleDegrees + (it * angleIncrement)) / 90.0f).coerceIn(0.0f, 1.0f)
        val low = abs(baseAngle)
        val high = abs(1.0f - baseAngle)
        val variance = min(varianceDegrees / 90.0f, min(low, high)).coerceIn(0.0f, 1.0f)
        angles.add(ControlValues(baseAngle, variance))
    }
    return angles
}

fun buildSplitTalusAngles(minAngleDegrees: Float, deltaAngleDegrees: Float, split: Float, left: Float, right: Float, varianceDegrees: Float): List<ControlValues> {
    val angles = ArrayList<ControlValues>(1024)
    (0..1023).forEach {
        val baseAngle = ((minAngleDegrees + splitFunction(it / 1023.0f, split, left, right) * deltaAngleDegrees) / 90.0f).coerceIn(0.0f, 1.0f)
        val low = abs(baseAngle)
        val high = abs(1.0f - baseAngle)
        val variance = min(varianceDegrees / 90.0f, min(low, high)).coerceIn(0.0f, 1.0f)
        angles.add(ControlValues(baseAngle, variance))
    }
    return angles
}

fun buildNormalTalusAngles(scale: Float, standardDeviation: Float, mean: Float, varianceDegrees: Float): List<ControlValues> {
    val term0 = -2.0 * standardDeviation * standardDeviation
    val term1 = scale * (1.0 / sqrt(PI * -term0))
    val angles = ArrayList<ControlValues>(1024)
    (0..1023).forEach {
        angles.add(computeNormalTalusAngle(it, term0, term1, mean, varianceDegrees))
    }
    return angles
}

fun buildLinearControlValues(multiplier: Float, offset: Float, variance: Float): List<ControlValues> {
    val values = ArrayList<ControlValues>(1024)
    (0..1023).forEach {
        val base = (it / 1023.0f) * multiplier + offset
        values.add(ControlValues(base, min(variance, min(abs(base), abs(1.0f - base))).coerceIn(0.0f, 1.0f)))
    }
    return values
}

fun buildLogisticControlValues(horizontalScale: Float, horizontalShift: Float, verticalShift: Float, shape: Float, variance: Float): List<ControlValues> {
    val values = ArrayList<ControlValues>(1024)
    (0..1023).forEach {
        val base = (1.0 / (1.0 + 2.7182818284590452353602875.pow(horizontalScale * (it / 1023.0) + horizontalShift)).pow(shape.toDouble()) + verticalShift).coerceIn(0.0, 1.0).toFloat()
        values.add(ControlValues(base, min(variance, min(abs(base), abs(1.0f - base))).coerceIn(0.0f, 1.0f)))
    }
    return values
}

fun buildCubicControlValues(radius: Float, variance: Float): List<ControlValues> {
    val values = ArrayList<ControlValues>(1024)
    val r2i = 1.0f / (radius * radius)
    (0..1023).forEach {
        val dist = (it / 1023.0f)
        val base = if (dist < radius) {
            val f = 1.0f - ((dist * dist) * r2i)
            f * f * f
        } else {
            0.0f
        }
        values.add(ControlValues(base, min(variance, min(abs(base), abs(1.0f - base))).coerceIn(0.0f, 1.0f)))
    }
    return values
}

fun List<ControlValues>.toTexture(): ByteArrayMatrix {
    val buffer = ByteArrayMatrix(1024)
    (0 until 1024).forEach { x ->
        val values = this[x]
        val (range, startFill, endFill) = values.toFillRange()
        (0 until 1024).forEach { y ->
            buffer[(1023 - y) * 1024 + x] = when (y) {
                range.first -> startFill
                range.last -> endFill
                in range -> -1
                else -> 0
            }
        }
    }
    return buffer
}

fun ByteArrayMatrix.toControlValues(): List<ControlValues> {
    val output = ArrayList<ControlValues>(1024)
    (0 until 1024).forEach { x ->
        var start = 1024
        var end = -1
        var startFill: Byte = 0
        var endFill: Byte = 0
        for (y in 0 until 1024) {
            val byte = this[(1023 - y) * 1024 + x]
            if (byte == byte0) continue
            if (start == 1024) {
                start = y
                startFill = byte
            }
            if (end < y) {
                end = y
                endFill = byte
            }
        }
        if (end - start < 0) {
            output.add(ControlValues(0.0f, 0.0f))
        } else {
            output.add(controlValueFromFillRange(start..end, startFill, endFill))
        }
    }
    return output
}

fun ByteArrayMatrix.toControlValuesCleanup(): List<ControlValues> {
    val output = ArrayList<ControlValues>(1024)
    (0 until 1024).forEach { x ->
        var weightSum = 0.0f
        var positionSum = 0.0f
        for (y in 0 until 1024) {
            val byte = this[(1023 - y) * 1024 + x]
            if (byte == byte0) continue
            val weight = (byte.toInt() and 0xFF) * inverse255
            weightSum += weight
            positionSum += (y + 0.5f) * weight
        }
        val base = (((positionSum / weightSum) - 0.5f) * inverse1023).coerceIn(0.0f, 1.0f)
        val variance = ((weightSum - 1.0f) * inverse1023).coerceIn(0.0f, 1.0f)
        output.add(ControlValues(base, min(variance, min(abs(base), abs(1.0f - base))).coerceIn(0.0f, 1.0f)))
    }
    return output
}

class TerraceStep(val range: Float, val compression: Float, val noiseOffset: Float)

fun buildRandomTerraceFunction(randomSeed: Long, minCount: Int, maxCount: Int, easeIn: Float, easeOut: Float, minSpacing: Float, maxSpacing: Float, minCompression: Float, maxCompression: Float): (Float, Float) -> TerraceFunction {
    val random = Random(randomSeed)
    val hasEaseIn = easeIn > 0.0f
    val hasEaseOut = easeOut > 0.0f
    var adjustedMinCount = minCount
    if (hasEaseIn) adjustedMinCount++
    if (hasEaseOut) adjustedMinCount++
    val adjustedMaxCount = maxCount.coerceAtLeast(adjustedMinCount)
    val count = if (adjustedMinCount == adjustedMaxCount) adjustedMaxCount else random.nextInt(adjustedMaxCount - adjustedMinCount + 1) + adjustedMinCount
    val spaceMin = min(minSpacing, maxSpacing)
    val spaceDelta = max(minSpacing, maxSpacing) - spaceMin
    val compMin = min(minCompression, maxCompression)
    val compDelta = max(minCompression, maxCompression) - compMin
    val terraceSteps = java.util.ArrayList<TerraceStep>(count)
    (0 until count).forEach {
        terraceSteps.add(
                if (it == 0 && hasEaseIn || it == count - 1 && hasEaseOut) {
                    TerraceStep(
                            range = if (it == 0) easeIn else easeOut,
                            compression = 1.0f,
                            noiseOffset = random.nextFloat() * 10000)
                } else {
                    TerraceStep(
                            range = random.nextFloat() * spaceDelta + spaceMin,
                            compression = random.nextFloat() * compDelta + compMin,
                            noiseOffset = random.nextFloat() * 10000)
                }
        )
    }
    return buildTerraceFunction(terraceSteps)
}

fun buildTerraceFunction(terraceSteps: List<TerraceStep>): (Float, Float) -> TerraceFunction {
    var rangeSum = 0.0f
    terraceSteps.forEach { rangeSum += it.range }
    val rangeNorm = 1.0f / rangeSum
    val coverages = FloatArray(terraceSteps.size) { (terraceSteps[it].range * rangeNorm).coerceIn(0.0f, 1.0f) }

    data class PreparedValues(val height: Float, val coverage: Float, val compression: Float, val offset: Float)

    var heightSum = 0.0f
    val preparedValues = Array(terraceSteps.size) {
        val coverage = coverages[it]
        val height = coverage / 2.0f + heightSum
        heightSum += coverage
        val terraceStep = terraceSteps[it]
        PreparedValues(height, coverage, terraceStep.compression, terraceStep.noiseOffset)
    }
    return { min, max ->
        val delta = max - min
        val steps = preparedValues.map {
            buildTerraceStep(it.height, it.coverage, it.compression, min, delta, it.offset)
        }
        val terraceFunction: TerraceFunction = { input, simplexX, simplexY, jitter ->
            applyTerrace(input, simplexX, simplexY, jitter, steps)
        }
        terraceFunction
    }
}

private fun applyTerrace(input: Float, simplexX: Float, simplexY: Float, jitter: Float, steps: List<(Float, Float, Float, Float) -> Float?>): Float {
    steps.forEach {
        val output = it(input, simplexX, simplexY, jitter)
        if (output != null) {
            return output
        }
    }
    return input
}

private fun buildTerraceStep(height: Float, coverage: Float, compression: Float, min: Float, delta: Float, noiseOffset: Float = 0.0f): (Float, Float, Float, Float) -> Float? {
    val midpoint = height * delta + min
    val adjustedCoverage = coverage * delta
    val halfCoverage = adjustedCoverage * 0.5f
    val minExtreme = midpoint - halfCoverage
    val maxExtreme = midpoint + halfCoverage
    return { input, simplexX, simplexY, jitter ->
        val variance = SimplexNoise.noise(simplexX + noiseOffset, simplexY + noiseOffset)
        val normalized = ((input - min) / delta).coerceIn(0.0f, 1.0f)
        val heightMultiplier = 1 - abs(((normalized - 0.5) * 2).pow(3.0))
        val adjustedJitter = variance * jitter * heightMultiplier.toFloat()
        val adjusted = input - adjustedJitter
        if (adjusted > minExtreme && adjusted <= maxExtreme) {
            ((adjusted - minExtreme) * compression) + midpoint + adjustedJitter
        } else {
            null
        }
    }
}

private fun splitFunction(x: Float, split: Float, left: Float, right: Float): Float {
    return if (x < split) {
        abs(((x - split).toDouble() * (1.0 / split)).pow(left.toDouble())).toFloat()
    } else {
        abs(((x - split).toDouble() * (1.0 / (1.0 - split))).pow(right.toDouble())).toFloat()
    }
}

private fun computeNormalTalusAngle(i: Int, term0: Double, term1: Double, mean: Float, jitter: Float): ControlValues {
    val baseAngle = ((term1 * exp((((i - mean) * (i - mean)) / term0))) / 90.0).toFloat().coerceIn(0.0f, 1.0f)
    val low = abs(baseAngle - 0.0f)
    val high = abs(1.0f - baseAngle)
    val variance = min(baseAngle * jitter, min(low, high)).coerceIn(0.0f, 1.0f)
    return ControlValues(baseAngle, variance)
}

private fun ControlValues.toFillRange(): Triple<IntRange, Byte, Byte> {
    val pixelCoverage = variance * 1023.0f + 1.0f
    val pixelRadius = pixelCoverage * 0.5f
    val center = base * 1023.0f + 0.5f
    val start = (center - pixelRadius).coerceIn(0.0f, 1023.0f)
    val end = (center + pixelRadius).coerceIn(1.0f, 1024.0f)
    val startFloor = fastFloorI(start).coerceIn(0, 1023)
    val endFloor = fastFloorI(end).coerceIn(1, 1024)
    val startFill = ((1.0f - (start - startFloor).coerceIn(0.0f, 1.0f)) * 255.0f).roundToInt().toByte()
    val endFill = ((end - endFloor).coerceIn(0.0f, 1.0f) * 255.0f).roundToInt().toByte()
    return Triple(startFloor..endFloor, startFill, endFill)
}

private fun controlValueFromFillRange(range: IntRange, startFill: Byte, endFill: Byte): ControlValues {
    val startFloor = range.first
    val endFloor = range.last
    val startFrac = 1.0f - (startFill.toInt() and 0xFF) * inverse255
    val endFrac = (endFill.toInt() and 0xFF) * inverse255
    val start = startFloor + startFrac
    val end = endFloor + endFrac
    val pixelCoverage = end - start
    val pixelRadius = pixelCoverage * 0.5f
    val variance = (pixelCoverage - 1.0f) * inverse1023
    val center = start + pixelRadius
    val base = (center - 0.5f) * inverse1023
    return ControlValues(base, variance)
}