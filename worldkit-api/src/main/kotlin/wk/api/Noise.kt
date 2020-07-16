package wk.api

import org.joml.SimplexNoise
import wk.internal.ext.fastFloor
import wk.internal.ext.fastFloorI
import java.io.File
import kotlin.collections.ArrayList
import kotlin.math.*
import kotlin.random.Random

typealias DistanceFun = (ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) -> Float
typealias FractalFun = (height: Float, min: Float, scale: Float, coefficient: Float) -> Float
typealias WorleyFunF1 = (f1: Float) -> Float
typealias WorleyFunF2 = (f1: Float, f2: Float) -> Float
typealias WorleyFunF3 = (f1: Float, f2: Float, f3: Float) -> Float
typealias NoiseSourceFun = (x: Float, y: Float) -> Float
typealias NoiseFunction = (Float) -> Float

private const val ON: Byte = -1
private const val OFF: Byte = 0

private fun curl(x: Float, y: Float, frequency: Float, power: Float, eps: Float = 1.0f): Pair<Float, Float> {
    val a = (simplexNoise(x * frequency, (y + eps) * frequency) - simplexNoise(x * frequency, (y - eps) * frequency)) / (2 * eps)
    val b = (simplexNoise((x + eps) * frequency, y * frequency) - simplexNoise((x - eps) * frequency, y * frequency)) / (2 * eps)
    return a * power to -b * power
}

private fun curlOctaves(octaves: Int, x: Float, y: Float, frequency: Float, power: Float, scales: FloatArray, offsets: FloatArray, coefficients: FloatArray, eps: Float = 1.0f): Pair<Float, Float> {
    val a = (noiseOctaves(octaves, x * frequency, (y + eps) * frequency, scales, offsets, coefficients) - noiseOctaves(octaves, x * frequency, (y - eps) * frequency, scales, offsets, coefficients)) / (2 * eps)
    val b = (noiseOctaves(octaves, (x + eps) * frequency, y * frequency, scales, offsets, coefficients) - noiseOctaves(octaves, (x - eps) * frequency, y * frequency, scales, offsets, coefficients)) / (2 * eps)
    return a * power to -b * power
}

private inline fun noiseOctaves(octaves: Int, x: Float, y: Float, scales: FloatArray, offsets: FloatArray, coefficients: FloatArray, useNewSimplex: Boolean = false, noiseBasis: FloatArrayMatrix? = null, noiseFunction: (Float) -> Float = { it }): Float {
    var sum = 0.0f
    if (useNewSimplex) {
        return noiseFunction(iqNoise(x * scales[0] + offsets[0], y * scales[0] + offsets[1], octaves, noiseBasis!!))
    } else {
        for (i in 0 until octaves) {
            val offset = i * 2
            sum += noiseFunction(SimplexNoise.noise(x * scales[i] + offsets[offset], y * scales[i] + offsets[offset + 1])) * coefficients[i]
        }
    }
    return sum
}

@PublicApi
fun generateRadialNoise(
        randomSeed: Long,
        octaves: Int,
        width: Int,
        radius: Float,
        radialVariance: Float,
        radialFrequency: Float,
        positionalVariance: Float,
        positionalFrequency: Float,
        cornerBias: Float,
        noiseSourceRadial: NoiseSource,
        noiseFunctionRadial: NoiseFunction,
        noiseSourcePositional: NoiseSource,
        noiseFunctionPositional: NoiseFunction,
        whiteOnBlack: Boolean = true
): ByteArrayMatrix {
    val radialFun = noiseSourceRadial.function
    val positionalFun = noiseSourcePositional.function
    val on = if (whiteOnBlack) ON else OFF
    val off = if (whiteOnBlack) OFF else ON
    val random = Random(randomSeed)
    val widthInverse = 1.0f / width
    val halfWidth = width / 2.0
    val actualRadius = width * radius
    val coefficients = getNoiseCoefficients(octaves)
    val radialScales = getNoiseScales(octaves, radialFrequency)
    val positionalScales = getNoiseScales(octaves, positionalFrequency)
    val radialOffsets = getNoiseOffsets(octaves, random.nextLong())
    val positionalOffsets = getNoiseOffsets(octaves, random.nextLong())
    val adjustedRadialVariance = actualRadius * radialVariance
    val adjustedPositionalVariance = actualRadius * positionalVariance
    val buffer = ByteArrayMatrix(width)
    (0 until width).toList().parallelStream().forEach { y ->
        taskYield()
        val yOff = y * width
        for (x in 0 until width) {
            val dy = y - halfWidth + 0.5
            val dx = x - halfWidth + 0.5
            val dist = sqrt(dy * dy + dx * dx)
            val uy = dy / dist
            val ux = dx / dist
            val maxLeg = max(abs(dx), abs(dy))
            val legRatio = dist / maxLeg
            val adjustedRadius = actualRadius * (((legRatio - 1.0) * cornerBias) + 1.0)
            val positionalNoise = fbmNoiseSingleVal(
                    x = x * widthInverse,
                    y = y * widthInverse,
                    octaves = octaves,
                    coefficients = coefficients,
                    scales = positionalScales,
                    offsets = positionalOffsets,
                    noiseSource = positionalFun,
                    noiseFunction = noiseFunctionPositional)
            val radialNoise = fbmNoiseSingleVal(
                    x = (ux).toFloat(),
                    y = (uy).toFloat(),
                    octaves = octaves,
                    coefficients = coefficients,
                    scales = radialScales,
                    offsets = radialOffsets,
                    noiseSource = radialFun,
                    noiseFunction = noiseFunctionRadial)
            val radiusAtAngle = adjustedRadius + (radialNoise * adjustedRadialVariance) + positionalNoise * adjustedPositionalVariance
            buffer[yOff + x] = if (dist < radiusAtAngle) on else off
        }
    }
    return buffer
}

@PublicApi
fun ByteArrayMatrix.distortByNoise(
        xDistortion: ByteArrayMatrix,
        yDistortion: ByteArrayMatrix,
        power: Float = 1.0f,
        output: ByteArrayMatrix = ByteArrayMatrix(width, height)
): ByteArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = ByteArrayMatrix::get,
        distLookup = ByteArrayMatrix::get,
        toFloat = byteToFloat
)

@PublicApi
fun ByteArrayMatrix.distortByNoise(
        xDistortion: ShortArrayMatrix,
        yDistortion: ShortArrayMatrix,
        power: Float = 1.0f,
        output: ByteArrayMatrix = ByteArrayMatrix(width, height)
): ByteArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = ByteArrayMatrix::get,
        distLookup = ShortArrayMatrix::get,
        toFloat = shortToFloat
)

@PublicApi
fun ByteArrayMatrix.distortByNoise(
        xDistortion: FloatArrayMatrix,
        yDistortion: FloatArrayMatrix,
        power: Float = 1.0f,
        output: ByteArrayMatrix = ByteArrayMatrix(width, height)
): ByteArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = ByteArrayMatrix::get,
        distLookup = FloatArrayMatrix::get,
        toFloat = floatToFloat
)

@PublicApi
fun ShortArrayMatrix.distortByNoise(
        xDistortion: ByteArrayMatrix,
        yDistortion: ByteArrayMatrix,
        power: Float = 1.0f,
        output: ShortArrayMatrix = ShortArrayMatrix(width, height)
): ShortArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = ShortArrayMatrix::get,
        distLookup = ByteArrayMatrix::get,
        toFloat = byteToFloat
)

@PublicApi
fun ShortArrayMatrix.distortByNoise(
        xDistortion: ShortArrayMatrix,
        yDistortion: ShortArrayMatrix,
        power: Float = 1.0f,
        output: ShortArrayMatrix = ShortArrayMatrix(width, height)
): ShortArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = ShortArrayMatrix::get,
        distLookup = ShortArrayMatrix::get,
        toFloat = shortToFloat
)

@PublicApi
fun ShortArrayMatrix.distortByNoise(
        xDistortion: FloatArrayMatrix,
        yDistortion: FloatArrayMatrix,
        power: Float = 1.0f,
        output: ShortArrayMatrix = ShortArrayMatrix(width, height)
): ShortArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = ShortArrayMatrix::get,
        distLookup = FloatArrayMatrix::get,
        toFloat = floatToFloat
)

@PublicApi
fun FloatArrayMatrix.distortByNoise(
        xDistortion: ByteArrayMatrix,
        yDistortion: ByteArrayMatrix,
        power: Float = 1.0f,
        output: FloatArrayMatrix = FloatArrayMatrix(width, height)
): FloatArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = FloatArrayMatrix::get,
        distLookup = ByteArrayMatrix::get,
        toFloat = byteToFloat
)

@PublicApi
fun FloatArrayMatrix.distortByNoise(
        xDistortion: ShortArrayMatrix,
        yDistortion: ShortArrayMatrix,
        power: Float = 1.0f,
        output: FloatArrayMatrix = FloatArrayMatrix(width, height)
): FloatArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = FloatArrayMatrix::get,
        distLookup = ShortArrayMatrix::get,
        toFloat = shortToFloat
)

@PublicApi
fun FloatArrayMatrix.distortByNoise(
        xDistortion: FloatArrayMatrix,
        yDistortion: FloatArrayMatrix,
        power: Float = 1.0f,
        output: FloatArrayMatrix = FloatArrayMatrix(width, height)
): FloatArrayMatrix = distortByNoise(
        input = this,
        output = output,
        xDistortion = xDistortion,
        yDistortion = yDistortion,
        power = power,
        inputLookup = FloatArrayMatrix::get,
        distLookup = FloatArrayMatrix::get,
        toFloat = floatToFloat
)

private fun <T : Comparable<T>, M : Matrix<T>, N : Comparable<N>, D : Matrix<N>> distortByNoise(
        input: M,
        output: M,
        xDistortion: D,
        yDistortion: D,
        power: Float,
        inputLookup: M.(Float, Float) -> T,
        distLookup: D.(Float, Float) -> N,
        toFloat: (N) -> Float
): M {
    val inWidthInverse = 1.0f / (input.width - 1)
    val inHeightInverse = 1.0f / (input.height - 1)
    (0 until input.height).inParallel { y ->
        taskYield()
        val yOff = y * input.width
        val v = y * inWidthInverse
        for (x in 0 until input.width) {
            val u = x * inHeightInverse
            val du = (toFloat(xDistortion.distLookup(u, v)) - 0.5f) * power
            val dv = (toFloat(yDistortion.distLookup(u, v)) - 0.5f) * power
            output[yOff + x] = input.inputLookup(u + du, v + dv)
        }
    }
    return output
}

fun Matrix<Byte>.distortByCurl(frequency: Float, power: Float): ByteArrayMatrix {
    val input = this
    val widthInverse = 1.0f / width
    val buffer = ByteArrayMatrix(width)
    (0 until width).inParallel { y ->
        taskYield()
        val yOff = y * width
        val v = y * widthInverse
        for (x in 0 until width) {
            val u = x * widthInverse
            val (cu, cv) = curl(u, v, frequency, power)
            val ui = ((u + cu) * width).roundToInt().coerceIn(0, width - 1)
            val vi = ((v + cv) * width).roundToInt().coerceIn(0, width - 1)
            buffer[yOff + x] = input[vi * width + ui]
        }
    }
    return buffer
}

fun Matrix<Byte>.distortByCurl(octaves: Int, randomSeed: Long, frequency: Float, power: Float): ByteArrayMatrix {
    val input = this
    val widthInverse = 1.0f / width
    val scales = getNoiseScales(octaves)
    val offsets = getNoiseOffsets(octaves, randomSeed)
    val coefficients = getNoiseCoefficients(octaves)
    val buffer = ByteArrayMatrix(width)
    (0 until width).inParallel { y ->
        taskYield()
        val yOff = y * width
        val v = y * widthInverse
        for (x in 0 until width) {
            val u = x * widthInverse
            val (cu, cv) = curlOctaves(octaves, u, v, frequency, power, scales, offsets, coefficients)
            val ui = ((u + cu) * width).roundToInt().coerceIn(0, width - 1)
            val vi = ((v + cv) * width).roundToInt().coerceIn(0, width - 1)
            buffer[yOff + x] = input[vi * width + ui]
        }
    }
    return buffer
}

fun getNoiseOffsets(octaves: Int, randomSeed: Long, multiplier: Float = 1000.0f): FloatArray {
    val random = Random(randomSeed)
    return FloatArray(octaves * 2) {
        random.nextFloat() * multiplier
    }
}

fun getNoiseScales(octaves: Int, start: Float = 1.0f, multiplier: Float = 2.0f): FloatArray {
    var scale = start
    return FloatArray(octaves) {
        val temp = scale
        scale *= multiplier
        temp
    }
}

fun getNoiseCoefficients(octaves: Int, sum: Float = 1.0f, multiplier: Float = 0.5f): FloatArray {
    val m = 1.0f / multiplier
    var coeffSum = 0.0f
    var coeff = 1.0f
    val coeffs = FloatArray(octaves) {
        val temp = coeff
        coeffSum += coeff
        coeff *= m
        temp
    }
    val scale = sum / coeffSum
    coeffs.indices.forEach {
        coeffs[it] = coeffs[it] * scale
    }
    return coeffs.reversedArray()
}

private data class AreaIndexNoiseValues(
        val index: Byte,
        val noiseScale: Float,
        val noiseOffset: Float,
        val noiseThreshold: Float,
        val isLast: Boolean
)

@PublicApi
data class GenerateRandomAreaIndexParams(
        val width: Int,
        val frequency1: Float = 60.0f,
        val power1: Float = 0.008f,
        val frequency2: Float = 20.0f,
        val power2: Float = 0.013f,
        val types: List<AreaType>
)

@PublicApi
fun batchGenerateRandomAreaIndices(
        startSeed: Int,
        count: Int,
        outFilePrefix: String,
        params: GenerateRandomAreaIndexParams,
        threads: Int = PARALLELISM
): List<File> {
    val output = arrayOfNulls<File>(count)
    (startSeed until (startSeed + count)).inParallel(threads) {
        val noise = generateRandomAreaIndex(it.toLong(), params)
        output[it] = noise.writeGrayU8("$outFilePrefix.$it.${params.width}")
    }
    return output.mapNotNull { it }.toList()
}

@PublicApi
class AreaType(val noiseScale: Float, val weight: Float)

@PublicApi
fun generateRandomAreaIndex(
        randomSeed: Long,
        params: GenerateRandomAreaIndexParams
): ByteArrayMatrix {
    with(params) {
        val random = Random(randomSeed)
        val maskWidthInverse = 1.0f / width
        var count = 0
        val noiseValues = ArrayList<AreaIndexNoiseValues>()
        var totalWeight = 0.0f
        types.forEach { totalWeight += it.weight }
        var remainingWeight = 1.0f
        types.forEachIndexed { i, areaSpec ->
            val slice = areaSpec.weight / totalWeight
            noiseValues.add(
                    AreaIndexNoiseValues(
                            index = (i + 1).toByte(),
                            noiseScale = areaSpec.noiseScale,
                            noiseOffset = random.nextFloat() * 10000,
                            noiseThreshold = ((1.0f - (slice / remainingWeight)) * 2.0f) - 1.0f,
                            isLast = types.size - count == 1))
            remainingWeight -= slice
            count++
        }
        fun curl(uv: Pair<Float, Float>, frequency: Float, power: Float): Pair<Float, Float> {
            val (u, v) = uv
            val (cu, cv) = curl(u, v, frequency, power)
            return (u + cu) to (v + cv)
        }

        val biomeMap = ByteArrayMatrix(width)
        (0 until width).toList().parallelStream().forEach { y ->
            taskYield()
            val yOff = y * width
            val v = y * maskWidthInverse
            for (x in 0 until width) {
                val u = x * maskWidthInverse
                val (cu, cv) = curl(curl(u to v, frequency1, power1), frequency2, power2)
                val ui = (cu * width)
                val vi = (cv * width)
                for (biomeNoise in noiseValues) {
                    if (biomeNoise.isLast) {
                        biomeMap[yOff + x] = biomeNoise.index
                    } else {
                        val noise = simplexNoise(
                                ui * biomeNoise.noiseScale + biomeNoise.noiseOffset,
                                vi * biomeNoise.noiseScale + biomeNoise.noiseOffset)
                        if (noise > biomeNoise.noiseThreshold) {
                            biomeMap[yOff + x] = biomeNoise.index
                            break
                        }
                    }
                }
            }
        }
        return biomeMap.normalize(true)
    }
}

private operator fun FloatArrayMatrix.get(u: Float, v: Float, s: Float): Float {
    val x =  (((u * s * (width - 1) + 0.5f).toInt() % width) + width) % width
    val y =  (((v * s * (width - 1) + 0.5f).toInt() % width) + width) % width
    return this[x, y]
}

private fun getIqBasisRandom(randomSeed: Long, width: Int): FloatArrayMatrix {
    val random = Random(randomSeed)
    return FloatArrayMatrix(width) { random.nextFloat() }.normalize()
}

private val basicNoiseFunction: NoiseFunction = { it }

fun getIqBasisSimplex(randomSeed: Long, width: Int, frequency: Float): FloatArrayMatrix {
    return generateNoiseMultifractal(
            randomSeed = randomSeed,
            octaves = 6,
            width = width,
            frequency = frequency,
            gain = 2.0f,
            roughness = 0.5f,
            noiseSource = NoiseSource.Simplex(),
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = basicNoiseFunction
    ).normalize()
}

private fun getIqBasisPerlin(randomSeed: Long, width: Int, frequency: Float): FloatArrayMatrix {
    return generateNoiseMultifractal(
            randomSeed = randomSeed,
            octaves = 6,
            width = width,
            frequency = frequency,
            gain = 2.0f,
            roughness = 0.5f,
            noiseSource = NoiseSource.Perlin(),
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = basicNoiseFunction
    ).normalize()
}

private fun getIqBasisCubic(randomSeed: Long, width: Int, pointStride: Int = 256): FloatArrayMatrix {
    return generateNoiseCubic(
            randomSeed = randomSeed,
            octaves = 5,
            width = width,
            roughness = 0.5f,
            pointStride = pointStride,
            pointConstraint = 0.95f,
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = basicNoiseFunction
    ).normalize()
}

@PublicApi
fun iqNoise(x: Float, y: Float, octaves: Int, noiseSource: FloatArrayMatrix): Float {
    var px = x
    var py = y
    var a = 0.0f
    var b = 1.0f
    var dx = 0.0f
    var dy = 0.0f
    for (i in 0 until octaves) {
        val xi = fastFloorI(px)
        val yi = fastFloorI(py)
        val fx = px - xi
        val fy = py - yi
        val ux = fx * fx * (3.0f - 2.0f * fx)
        val uy = fy * fy * (3.0f - 2.0f * fy)
        val px0 = ((xi % noiseSource.width) + noiseSource.width) % noiseSource.width
        val px1 = (((xi + 1) % noiseSource.width) + noiseSource.width) % noiseSource.width
        val py0 = ((yi % noiseSource.width) + noiseSource.width) % noiseSource.width
        val py1 = (((yi + 1) % noiseSource.width) + noiseSource.width) % noiseSource.width
        val s1 = noiseSource[px0, py0]
        val s2 = noiseSource[px1, py0]
        val s3 = noiseSource[px0, py1]
        val s4 = noiseSource[px1, py1]
        val ba = s2 - s1
        val ca = s3 - s1
        val abcd = s1 - s2 - s3 + s4
        val nx = s1 + ba * ux + ca * uy + abcd * ux * uy
        val ny = 6.0f * fx * (1.0f - fx) * (ba + abcd * uy)
        val nz = 6.0f * fy * (1.0f - fy) * (ca + abcd * ux)
        dx += ny
        dy += nz
        a += b * nx / (1.0f + (dx * dx + dy * dy))
        b *= 0.5f
        val tx = px
        px = (0.8f * px + 0.6f * py) * 2.0f
        py = (-0.6f * tx + 0.8f * py) * 2.0f
    }
    return a - 1.0f
}

@PublicApi
sealed class IqNoiseBasis {

    abstract fun create(): FloatArrayMatrix

    @PublicApi
    class Random(private val randomSeed: Long, private val width: Int) : IqNoiseBasis() {
        override fun create() = getIqBasisRandom(randomSeed, width)
    }

    @PublicApi
    class Simplex(private val randomSeed: Long, private val width: Int, private val frequency: Float) : IqNoiseBasis() {
        override fun create() = getIqBasisSimplex(randomSeed, width, frequency)
    }

    @PublicApi
    class Perlin(private val randomSeed: Long, private val width: Int, private val frequency: Float) : IqNoiseBasis() {
        override fun create() = getIqBasisPerlin(randomSeed, width, frequency)
    }

    @PublicApi
    class Cubic(private val randomSeed: Long, private val width: Int, private val pointStride: Int) : IqNoiseBasis() {
        override fun create() = getIqBasisCubic(randomSeed, width, pointStride)
    }
}

@PublicApi
fun generateNoiseIQ(
        octaves: Int,
        width: Int,
        scale: Float,
        xOffset: Float = 0.0f,
        yOffset: Float = 0.0f,
        basis: IqNoiseBasis = IqNoiseBasis.Random(0, 256)
): FloatArrayMatrix {
    val iqBasis = basis.create()
    val buffer = FloatArrayMatrix(width)
    (0 until width).toList().parallelStream().forEach { y ->
        taskYield()
        val yOff = y * width
        val yF = y * scale + yOffset
        for (x in 0 until width) {
            buffer[yOff + x] = iqNoise(x * scale + xOffset, yF, octaves, iqBasis)
        }
    }
    return buffer
}

private val squaredDistFun: DistanceFun = { ax, ay, az, bx, by, bz ->
    (ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz)
}

private val rectilinearDistFun: DistanceFun = { ax, ay, az, bx, by, bz ->
    abs(ax - bx) + abs(ay - by) + abs(az - bz)
}

private val maxValueDistFun: DistanceFun = { ax, ay, az, bx, by, bz ->
    max(max(abs(ax - bx), abs(ay - by)), abs(az - bz))
}

private val fbmFractalFun: FractalFun = { _, _, _, coefficient ->
    coefficient
}

private val terrainFractalFun: FractalFun = { height, min, scale, coefficient ->
    val heightFactor = ((0.5f - coefficient) * 2.0f).coerceIn(0.0f, 1.0f)
    val normalizedHeight = (height - min) * scale
    normalizedHeight * heightFactor * coefficient + (1 - heightFactor) * coefficient
}

private val fbmTerrainFractalFun: FractalFun = { height, min, scale, coefficient ->
    val heightFactor = ((0.5f - coefficient) * 2.0f).coerceIn(0.0f, 1.0f)
    val normalizedHeight = (height - min) * scale
    ((normalizedHeight * heightFactor * coefficient + (1 - heightFactor) * coefficient) + coefficient) * 0.5f
}

@PublicApi
inline fun generateNoiseWorley(
        randomSeed: Long,
        octaves: Int = 1,
        width: Int,
        roughness: Float = 0.5f,
        zJitter: Float = 1.0f,
        pointStride: Int = 32,
        pointConstraint: Float = 0.85f,
        searchRadius: Int = 2,
        distanceFunction: DistanceFunction = DistanceFunction.Euclidean,
        fractalFunction: FractalFunction = FractalFunction.Fbm,
        crossinline noiseFunction: WorleyFunF1 = { it }
): FloatArrayMatrix {
    val modifiedNoiseFun: WorleyFunF3 = if (distanceFunction == DistanceFunction.Euclidean) {
        { f1, _, _ ->
            noiseFunction(sqrt(f1))
        }
    } else {
        { f1, _, _ ->
            noiseFunction(f1)
        }
    }
    return generateNoiseWorley(
            randomSeed = randomSeed,
            octaves = octaves,
            width = width,
            roughness = roughness,
            zJitter = zJitter,
            pointStride = pointStride,
            pointConstraint = pointConstraint,
            searchRadius = searchRadius,
            distanceFunction = distanceFunction.function,
            fractalFunction = fractalFunction.function,
            noiseFunction = modifiedNoiseFun
    )
}

@PublicApi
inline fun generateNoiseWorley(
        randomSeed: Long,
        octaves: Int = 1,
        width: Int,
        roughness: Float = 0.5f,
        zJitter: Float = 1.0f,
        pointStride: Int = 32,
        pointConstraint: Float = 0.85f,
        searchRadius: Int = 2,
        distanceFunction: DistanceFunction = DistanceFunction.Euclidean,
        fractalFunction: FractalFunction = FractalFunction.Fbm,
        crossinline noiseFunction: WorleyFunF2 = { f1, _ -> f1 }
): FloatArrayMatrix {
    val modifiedNoiseFun: WorleyFunF3 = if (distanceFunction == DistanceFunction.Euclidean) {
        { f1, f2, _ ->
            noiseFunction(sqrt(f1), sqrt(f2))
        }
    } else {
        { f1, f2, _ ->
            noiseFunction(f1, f2)
        }
    }
    return generateNoiseWorley(
            randomSeed = randomSeed,
            octaves = octaves,
            width = width,
            roughness = roughness,
            zJitter = zJitter,
            pointStride = pointStride,
            pointConstraint = pointConstraint,
            searchRadius = searchRadius,
            distanceFunction = distanceFunction.function,
            fractalFunction = fractalFunction.function,
            noiseFunction = modifiedNoiseFun
    )
}

@PublicApi
inline fun generateNoiseWorley(
        randomSeed: Long,
        octaves: Int = 1,
        width: Int,
        roughness: Float = 0.5f,
        zJitter: Float = 1.0f,
        pointStride: Int = 32,
        pointConstraint: Float = 0.85f,
        searchRadius: Int = 2,
        distanceFunction: DistanceFunction = DistanceFunction.Euclidean,
        fractalFunction: FractalFunction = FractalFunction.Fbm,
        crossinline noiseFunction: WorleyFunF3 = { f1, _, _ -> f1 }
): FloatArrayMatrix {
    val modifiedNoiseFun: WorleyFunF3 = if (distanceFunction == DistanceFunction.Euclidean) {
        { f1, f2, f3 ->
            noiseFunction(sqrt(f1), sqrt(f2), sqrt(f3))
        }
    } else {
        { f1, f2, f3 ->
            noiseFunction(f1, f2, f3)
        }
    }
    return generateNoiseWorley(
            randomSeed = randomSeed,
            octaves = octaves,
            width = width,
            roughness = roughness,
            zJitter = zJitter,
            pointStride = pointStride,
            pointConstraint = pointConstraint,
            searchRadius = searchRadius,
            distanceFunction = distanceFunction.function,
            fractalFunction = fractalFunction.function,
            noiseFunction = modifiedNoiseFun
    )
}

inline fun generateNoiseWorley(
        randomSeed: Long,
        octaves: Int,
        width: Int,
        roughness: Float,
        zJitter: Float,
        pointStride: Int,
        pointConstraint: Float,
        searchRadius: Int,
        crossinline distanceFunction: DistanceFun,
        crossinline fractalFunction: (height: Float, min: Float, scale: Float, coefficient: Float) -> Float,
        crossinline noiseFunction: WorleyFunF3
): FloatArrayMatrix {
    val random = Random(randomSeed)
    val offsets = getNoiseOffsets(octaves, random.nextLong(), 10000.0f)
    var currentStride = pointStride
    val points = Array(octaves) {
        val points = generateSemiRandomPoints(random.nextLong(), 1.0, currentStride, pointConstraint.toDouble())
        currentStride *= 2
        points
    }
    currentStride = pointStride
    val heights = Array(octaves) {
        val currentCount = currentStride * currentStride
        val heights = FloatArray(currentCount)
        val currentPoints = points[it]
        val offX = offsets[it * 2]
        val offY = offsets[it * 2 + 1]
        val frequency = currentStride * 0.5f
        val scale = (1.0f / currentStride) * zJitter
        for (i in 0 until currentCount) {
            val i2 = i * 2
            val px = currentPoints[i2]
            val py = currentPoints[i2 + 1]
            heights[i] = simplexNoise(offX + px.toFloat() * frequency, offY + py.toFloat() * frequency) * scale
        }
        currentStride *= 2
        heights
    }
    val coefficients = getNoiseCoefficients(octaves, 1.0f, roughness)
    var max = -Float.MAX_VALUE
    var min = Float.MAX_VALUE
    var currentMin = 0.0f
    var currentScale = 1.0f
    var isFirstPass = true
    val output = FloatArrayMatrix(width)
    currentStride = pointStride
    for (o in 0 until octaves) {
        val oPoints = points[o]
        val oHeights = heights[o]
        val coeff = coefficients[o]
        (0 until width).toList().parallelStream().forEach { y ->
            taskYield()
            val yOff = y * width
            val py = (y + 0.5f) / width
            val piy = (py * currentStride).toInt()
            for (x in 0 until width) {
                val px = (x + 0.5f) / width
                val pix = (px * currentStride).toInt()
                var minDist1 = Float.POSITIVE_INFINITY
                var minDist2 = Float.POSITIVE_INFINITY
                var minDist3 = Float.POSITIVE_INFINITY
                for (oiy in (piy - searchRadius).coerceAtLeast(0)..(piy + searchRadius).coerceAtMost(currentStride - 1)) {
                    for (oix in (pix - searchRadius).coerceAtLeast(0)..(pix + searchRadius).coerceAtMost(currentStride - 1)) {
                        val oi = oiy * currentStride + oix
                        val oi2 = oi * 2
                        val ox = oPoints[oi2].toFloat()
                        val oy = oPoints[oi2 + 1].toFloat()
                        val oz = oHeights[oi]
                        val dist = distanceFunction(px, py, 0.0f, ox, oy, oz)
                        when {
                            dist < minDist1 -> {
                                minDist3 = minDist2
                                minDist2 = minDist1
                                minDist1 = dist
                            }
                            dist < minDist2 -> {
                                minDist3 = minDist2
                                minDist2 = dist
                            }
                            dist < minDist3 -> {
                                minDist3 = dist
                            }
                        }
                    }
                }
                val i = yOff + x
                var height = output[i]
                height += if (isFirstPass) {
                    noiseFunction(minDist1, minDist2, minDist3) * coeff
                } else {
                    noiseFunction(minDist1, minDist2, minDist3) * fractalFunction(height, currentMin, currentScale, coeff)
                }
                if (height > max) {
                    max = height
                }
                if (height < min) {
                    min = height
                }
                output[i] = height
            }
        }
        isFirstPass = false
        currentMin = min
        currentScale = 1.0f / (max - min)
        max = -Float.MAX_VALUE
        min = Float.MAX_VALUE
        currentStride *= 2
    }
    return output
}

@PublicApi
inline fun generateNoiseCubic(
        randomSeed: Long,
        octaves: Int = 1,
        width: Int,
        roughness: Float = 0.5f,
        pointStride: Int = 32,
        pointConstraint: Float = 0.85f,
        fractalFunction: FractalFunction = FractalFunction.Fbm,
        crossinline noiseFunction: NoiseFunction = { f1 -> f1 }
): FloatArrayMatrix {
    val fractalFun = fractalFunction.function
    val random = Random(randomSeed)
    val iWidth = 1.0f / width
    var currentStride = pointStride
    val points = Array(octaves) {
        val points = generateSemiRandomPoints(random.nextLong(), 1.0, currentStride, pointConstraint.toDouble())
        currentStride *= 2
        points
    }
    currentStride = pointStride
    val values = Array(octaves) {
        val currentCount = currentStride * currentStride
        val values = FloatArray(currentCount)
        for (i in 0 until currentCount) {
            values[i] = random.nextFloat() * 2.0f - 1.0f
        }
        currentStride *= 2
        values
    }
    val coefficients = getNoiseCoefficients(octaves, 1.0f, roughness)
    var max = -Float.MAX_VALUE
    var min = Float.MAX_VALUE
    var currentMin = 0.0f
    var currentScale = 1.0f
    var isFirstPass = true
    val output = FloatArrayMatrix(width)
    currentStride = pointStride
    for (o in 0 until octaves) {
        val currentStrideM1 = currentStride - 1
        val r2 = (1.999f * (1.0f / currentStride)) * (1.999f * (1.0f / currentStride))
        val r2i = 1.0f / r2
        val oPoints = points[o]
        val oValues = values[o]
        val coeff = coefficients[o]
        (0 until width).toList().parallelStream().forEach { y ->
            taskYield()
            val yOff = y * width
            val py = (y + 0.5f) * iWidth
            val piy = (py * currentStride).toInt()
            for (x in 0 until width) {
                val px = (x + 0.5f) * iWidth
                val pix = (px * currentStride).toInt()
                var localHeight = 0.0f
                var localCoeffSum = 0.0f
                for (oiy in (piy - 2).coerceAtLeast(0)..(piy + 2).coerceAtMost(currentStrideM1)) {
                    for (oix in (pix - 2).coerceAtLeast(0)..(pix + 2).coerceAtMost(currentStrideM1)) {
                        val oi = oiy * currentStride + oix
                        val oi2 = oi * 2
                        val ox = oPoints[oi2].toFloat()
                        val oy = oPoints[oi2 + 1].toFloat()
                        val d2 = (px - ox) * (px - ox) + (py - oy) * (py - oy)
                        if (d2 > r2) {
                            continue
                        }
                        var c = 1.0f - d2 * r2i
                        c *= c * c
                        val v = oValues[oi]
                        localCoeffSum += c
                        localHeight += c * v
                    }
                }
                val i = yOff + x
                var height = output[i]
                height += if (isFirstPass) {
                    noiseFunction(localHeight / localCoeffSum) * coeff
                } else {
                    noiseFunction(localHeight / localCoeffSum) * fractalFun(height, currentMin, currentScale, coeff)
                }
                if (height > max) {
                    max = height
                }
                if (height < min) {
                    min = height
                }
                output[i] = height
            }
        }
        isFirstPass = false
        currentMin = min
        currentScale = 1.0f / (max - min)
        max = -Float.MAX_VALUE
        min = Float.MAX_VALUE
        currentStride *= 2
    }
    return output
}

private fun xsh(x: Int, y: Int): Int {
    var i = (y shl 16) or (y ushr 16)
    i = ((i and 16711935) shl 8) or ((i and -16711936) ushr 8)
    i = ((i and 252645135) shl 4) or ((i and -252645136) ushr 4)
    i = (x xor i)
    i = (i xor (i shr 15)) * 668265263
    i = (i xor (i shr 13)) * 374761393
    return i xor (i shr 16)
}

private const val randIntToFloat: Float = 1.0f / 0x80000000L

private fun pseudoRandomPoint(x: Int, y: Int): Triple<Float, Float, Float> {
    val h1 = xsh(x, y) * -1255789435
    val a = h1 * randIntToFloat * 0.49f + 0.5f + x
    val h2 = h1 * -1255789435
    val b = h2 * randIntToFloat * 0.49f + 0.5f + y
    val c = h2 * -1255789435 * randIntToFloat
    return Triple(a, b, c)
}

private val perlinSourceFun: NoiseSourceFun = { x, y -> perlinNoise(x, y) }

private val simplexSourceFun: NoiseSourceFun = { x, y -> simplexNoise(x, y) }

private val cubicSourceFun: NoiseSourceFun = { x, y -> cubicNoise(x, y) }

@PublicApi
sealed class NoiseSource {

    abstract val function: NoiseSourceFun

    @PublicApi
    class Perlin: NoiseSource() {
        override val function= perlinSourceFun
    }

    @PublicApi
    class Simplex: NoiseSource() {
        override val function = simplexSourceFun
    }

    @PublicApi
    class Cubic: NoiseSource() {
        override val function = cubicSourceFun
    }

    @PublicApi
    class Worley(private val zJitter: Float,
                 private val searchRadius: Int,
                 distanceFunction: DistanceFunction,
                 private val noiseFunction: WorleyFunF3): NoiseSource() {
        private val distanceFun = distanceFunction.function
        override val function: NoiseSourceFun get() = { x, y -> worleyNoise(x, y, zJitter, searchRadius, distanceFun, noiseFunction) }
    }
}

@PublicApi
enum class DistanceFunction(val function: DistanceFun) {

    @PublicApi
    Euclidean(squaredDistFun),

    @PublicApi
    Manhattan(rectilinearDistFun),

    @PublicApi
    Chebyshev(maxValueDistFun)
}

@PublicApi
enum class FractalFunction(val function: FractalFun) {

    @PublicApi
    Fbm(fbmFractalFun),

    @PublicApi
    Terrain(terrainFractalFun),

    @PublicApi
    FbmTerrain(fbmTerrainFractalFun)
}

inline fun generateNoiseMultifractal(
        randomSeed: Long,
        octaves: Int,
        width: Int,
        frequency: Float,
        gain: Float = 2.0f,
        roughness: Float = 0.5f,
        noiseSource: NoiseSource = NoiseSource.Simplex(),
        fractalFunction: FractalFunction = FractalFunction.Fbm,
        crossinline noiseFunction: NoiseFunction = { f1 -> f1 }
) = generateNoiseMultifractal(
        randomSeed = randomSeed,
        octaves = octaves,
        width = width,
        frequency = frequency,
        gain = gain,
        roughness = roughness,
        noiseSource = noiseSource.function,
        fractalFunction = fractalFunction.function,
        noiseFunction = noiseFunction
)

@PublishedApi
internal inline fun generateNoiseMultifractal(
        randomSeed: Long,
        octaves: Int,
        width: Int,
        frequency: Float,
        gain: Float,
        roughness: Float,
        crossinline noiseSource: NoiseSourceFun,
        crossinline fractalFunction: FractalFun,
        crossinline noiseFunction: NoiseFunction
): FloatArrayMatrix {
    val iWidth = (1.0f / width) * frequency
    val coefficients = getNoiseCoefficients(octaves, 1.0f, roughness)
    val scales = getNoiseScales(octaves, iWidth, gain)
    val offsets = getNoiseOffsets(octaves, randomSeed, 10000.0f)
    var max = -Float.MAX_VALUE
    var min = Float.MAX_VALUE
    var currentMin = 0.0f
    var currentScale = 1.0f
    var isFirstPass = true
    val output = FloatArrayMatrix(width)
    for (octave in 0 until octaves) {
        val coeff = coefficients[octave]
        val scale = scales[octave]
        val offX = offsets[octave * 2]
        val offY = offsets[octave * 2 + 1]
        (0 until width).toList().parallelStream().forEach { y ->
            taskYield()
            val yOff = y * width
            val py = (y + 0.5f) * scale + offY
            for (x in 0 until width) {
                val px = (x + 0.5f) * scale + offX
                val i = yOff + x
                var height = output[i]
                height += if (isFirstPass) {
                    noiseFunction(noiseSource(px, py)) * coeff
                } else {
                    noiseFunction(noiseSource(px, py)) * fractalFunction(height, currentMin, currentScale, coeff)
                }
                if (height > max) {
                    max = height
                }
                if (height < min) {
                    min = height
                }
                output[i] = height
            }
        }
        isFirstPass = false
        currentMin = min
        currentScale = 1.0f / (max - min)
        max = -Float.MAX_VALUE
        min = Float.MAX_VALUE
    }
    return output
}

private inline fun fbmNoiseSingleVal(
        x: Float,
        y: Float,
        octaves: Int,
        coefficients: FloatArray,
        scales: FloatArray,
        offsets: FloatArray,
        crossinline noiseSource: NoiseSourceFun,
        crossinline noiseFunction: NoiseFunction
): Float {
    var height = 0.0f
    for (octave in 0 until octaves) {
        val scale = scales[octave]
        height += noiseFunction(noiseSource(x * scale + offsets[octave * 2], y * scale + offsets[octave * 2 + 1])) * coefficients[octave]
    }
    return height
}

@PublicApi
fun worleyNoise(
        x: Float,
        y: Float,
        zJitter: Float,
        searchRadius: Int,
        distanceFunction: DistanceFun,
        noiseFunction: WorleyFunF3
): Float {
    val pix = x.roundToInt()
    val piy = y.roundToInt()
    var minDist1 = Float.POSITIVE_INFINITY
    var minDist2 = Float.POSITIVE_INFINITY
    var minDist3 = Float.POSITIVE_INFINITY
    for (oiy in (piy - searchRadius)..(piy + searchRadius)) {
        for (oix in (pix - searchRadius)..(pix + searchRadius)) {
            val (ox, oy, oz) = pseudoRandomPoint(oix, oiy)
            val dist = distanceFunction(x, y, 0.0f, ox, oy, oz * zJitter)
            when {
                dist < minDist1 -> {
                    minDist3 = minDist2
                    minDist2 = minDist1
                    minDist1 = dist
                }
                dist < minDist2 -> {
                    minDist3 = minDist2
                    minDist2 = dist
                }
                dist < minDist3 -> {
                    minDist3 = dist
                }
            }
        }
    }
    return noiseFunction(minDist1, minDist2, minDist3)
}

@PublicApi
fun cubicNoise(x: Float, y: Float): Float {
    val pix = x.roundToInt()
    val piy = y.roundToInt()
    var localHeight = 0.0f
    var localCoeffSum = 0.0f
    for (oiy in (piy - 2)..(piy + 2)) {
        for (oix in (pix - 2)..(pix + 2)) {
            val (ox, oy, v) = pseudoRandomPoint(oix, oiy)
            val d2 = (x - ox) * (x - ox) + (y - oy) * (y - oy)
            if (d2 > 3.99f) {
                continue
            }
            var c = 1.0f - d2 * 0.25062656641f
            c *= c * c
            localCoeffSum += c
            localHeight += c * v
        }
    }
    return localHeight / localCoeffSum
}

@PublicApi
fun perlinNoise(x: Float, y: Float): Float {
    val xi = fastFloor(x)
    val yi = fastFloor(y)
    var a = xi % 289.0f
    a *= (a * 34.0f + 1.0f)
    var b = (xi + 1.0f) % 289.0f
    b *= (b * 34.0f + 1.0f)
    a -= fastFloor(a * 0.00346020761f) * 289.0f
    b -= fastFloor(b * 0.00346020761f) * 289.0f
    val t1 = (yi + 1.0f) % 289.0f
    var c = a + t1
    var d = b + t1
    val t2 = yi % 289.0f
    a += t2
    b += t2
    a *= (a * 34.0f + 1.0f)
    b *= (b * 34.0f + 1.0f)
    c *= (c * 34.0f + 1.0f)
    d *= (d * 34.0f + 1.0f)
    a -= fastFloor(a * 0.00346020761f) * 289.0f
    b -= fastFloor(b * 0.00346020761f) * 289.0f
    c -= fastFloor(c * 0.00346020761f) * 289.0f
    d -= fastFloor(d * 0.00346020761f) * 289.0f
    a *= 0.0243902439f
    b *= 0.0243902439f
    c *= 0.0243902439f
    d *= 0.0243902439f
    a -= fastFloor(a)
    b -= fastFloor(b)
    c -= fastFloor(c)
    d -= fastFloor(d)
    a = 2.0f * a - 1.0f
    b = 2.0f * b - 1.0f
    c = 2.0f * c - 1.0f
    d = 2.0f * d - 1.0f
    val gx = abs(a) - 0.5f
    val gy = abs(b) - 0.5f
    val gz = abs(c) - 0.5f
    val gw = abs(d) - 0.5f
    a -= fastFloor(a + 0.5f)
    b -= fastFloor(b + 0.5f)
    c -= fastFloor(c + 0.5f)
    d -= fastFloor(d + 0.5f)
    var nx = 1.79284291400159f - (a * a + gx * gx) * 0.85373472095314f
    var ny = 1.79284291400159f - (c * c + gz * gz) * 0.85373472095314f
    var nz = 1.79284291400159f - (b * b + gy * gy) * 0.85373472095314f
    var nw = 1.79284291400159f - (d * d + gw * gw) * 0.85373472095314f
    val xf = x - xi
    val yf = y - yi
    nx = a * nx * xf + gx * nx * yf
    ny = c * ny * xf + gz * ny * (yf - 1.0f)
    nz = b * nz * (xf - 1.0f) + gy * nz * yf
    nw = d * nw * (xf - 1.0f) + gw * nw * (yf - 1.0f)
    val m = (xf * xf * xf) * (xf * (xf * 6.0f - 15.0f) + 10.0f)
    val n = nx + m * (nz - nx)
    return 2.3f * (n + ((yf * yf * yf) * (yf * (yf * 6.0f - 15.0f) + 10.0f)) * ((ny + m * (nw - ny)) - n))
}

@PublicApi
fun simplexNoise(x: Float, y: Float): Float {
    val dot1 = x * 0.366025403784439f + y * 0.366025403784439f
    var ix = fastFloor(x + dot1)
    var iy = fastFloor(y + dot1)
    val dot2 = ix * 0.211324865405187f + iy * 0.211324865405187f
    val x0x = x - ix + dot2
    val x0y = y - iy + dot2
    var i1x = 0.0f
    var i1y = 1.0f
    if (x0x > x0y) {
        i1x = 1.0f
        i1y = 0.0f
    }
    val x12x = x0x + 0.211324865405187f - i1x
    val x12y = x0y + 0.211324865405187f - i1y
    val x12z = x0x - 0.577350269189626f
    val x12w = x0y - 0.577350269189626f
    ix %= 289.0f
    iy %= 289.0f
    var px = iy
    var py = iy + i1y
    var pz = iy + 1.0f
    px *= (px * 34.0f + 1.0f)
    py *= (py * 34.0f + 1.0f)
    pz *= (pz * 34.0f + 1.0f)
    px -= fastFloor(px * 0.00346020761f) * 289.0f
    py -= fastFloor(py * 0.00346020761f) * 289.0f
    pz -= fastFloor(pz * 0.00346020761f) * 289.0f
    px += ix
    py += ix + i1x
    pz += ix + 1.0f
    px *= (px * 34.0f + 1.0f)
    py *= (py * 34.0f + 1.0f)
    pz *= (pz * 34.0f + 1.0f)
    px -= fastFloor(px * 0.00346020761f) * 289.0f
    py -= fastFloor(py * 0.00346020761f) * 289.0f
    pz -= fastFloor(pz * 0.00346020761f) * 289.0f
    px *= 0.024390243902439f
    py *= 0.024390243902439f
    pz *= 0.024390243902439f
    px = (px - fastFloor(px)) * 2.0f - 1.0f
    py = (py - fastFloor(py)) * 2.0f - 1.0f
    pz = (pz - fastFloor(pz)) * 2.0f - 1.0f
    val hx = abs(px) - 0.5f
    val hy = abs(py) - 0.5f
    val hz = abs(pz) - 0.5f
    val oxx = fastFloor(px + 0.5f)
    val oxy = fastFloor(py + 0.5f)
    val oxz = fastFloor(pz + 0.5f)
    val a0x = px - oxx
    val a0y = py - oxy
    val a0z = pz - oxz
    var mx = max(0.0f, 0.5f - (x0x * x0x + x0y * x0y))
    var my = max(0.0f, 0.5f - (x12x * x12x + x12y * x12y))
    var mz = max(0.0f, 0.5f - (x12z * x12z + x12w * x12w))
    mx *= mx * mx
    my *= my * my
    mz *= mz * mz
    val a0h2x = 1.79284291400159f - (a0x * a0x + hx * hx) * 0.85373472095314f
    val a0h2y = 1.79284291400159f - (a0y * a0y + hy * hy) * 0.85373472095314f
    val a0h2z = 1.79284291400159f - (a0z * a0z + hz * hz) * 0.85373472095314f
    mx *= a0h2x
    my *= a0h2y
    mz *= a0h2z
    return 50.0f * (mx * (a0x * x0x + hx * x0y) + my * (a0y * x12x + hy * x12y) + mz * (a0z * x12z + hz * x12w))
}

@PublicApi
fun generateSemiRandomPoints(seed: Long, width: Double, stride: Int, constraint: Double): DoubleArray {
    val strideM1 = stride - 1
    val outPoints = DoubleArray(stride * stride * 2)
    val random = java.util.Random(seed)
    val realConstraint = constraint.coerceIn(0.0, 1.0)
    val pixel = width / (stride - 1.0)
    val halfPixel = pixel * 0.5
    val bounds = pixel * realConstraint
    val margin = (pixel * (1.0 - realConstraint)) * 0.5 + halfPixel
    (0 until stride).map { it to random.nextLong() }.toList().parallelStream().forEach { (y, localSeed) ->
        val yIndexOffset = y * stride
        val localRandom = java.util.Random(localSeed)
        val bYOff = y * pixel
        val yOff = ((y - 1.0) * pixel) + margin
        for (x in 0 until stride) {
            val i = (yIndexOffset + x) * 2
            if (x == 0 || x == strideM1 || y == 0 || y == strideM1) {
                outPoints[i] = x * pixel
                outPoints[i + 1] = bYOff
            } else {
                outPoints[i] = (x - 1) * pixel + margin + localRandom.nextDouble() * bounds
                outPoints[i + 1] = yOff + localRandom.nextDouble() * bounds
            }
        }
    }
    return outPoints
}
