package wk.api

import wk.internal.application.lookupProjectPath
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min
import wk.internal.application.taskYield as _taskYield

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PublicApi

@PublicApi
val PARALLELISM = max(2, Runtime.getRuntime().availableProcessors())

@PublishedApi
internal val PARALLEL_LIST = (0 until PARALLELISM).toList()

@PublicApi
inline fun IntRange.inParallel(threads: Int = PARALLELISM, crossinline job: (index: Int) -> Unit) {
    val actualThreads = min(last - first + 1, threads).coerceAtLeast(1)
    val list = if (actualThreads == PARALLELISM) PARALLEL_LIST else (0 until actualThreads).toList()
    list.parallelStream().forEach { thread ->
        (((first + thread)..(last)) step actualThreads).forEach(job)
    }
}

@PublicApi
inline fun <T> IntRange.inParallel(threads: Int = PARALLELISM, crossinline context: (thread: Int) -> T, crossinline job: (context: T, index: Int) -> Unit): List<T> {
    val actualThreads = min(last - first + 1, threads).coerceAtLeast(1)
    val list = if (actualThreads == PARALLELISM) PARALLEL_LIST else (0 until actualThreads).toList()
    val contexts = ArrayList<T>(actualThreads)
    list.forEach { thread ->
        contexts.add(context(thread))
    }
    list.parallelStream().forEach { thread ->
        val threadLocal = contexts[thread]
        (((first + thread)..(last)) step actualThreads).forEach { job(threadLocal, it) }
    }
    return contexts
}

@PublicApi
inline fun <T> List<T>.inParallel(threads: Int = PARALLELISM, crossinline job: (index: Int, element: T) -> Unit) {
    indices.inParallel(threads) { job(it, this[it]) }
}

@PublicApi
inline fun <T> List<T>.inParallel(threads: Int = PARALLELISM, crossinline job: (element: T) -> Unit) {
    indices.inParallel(threads) { job(this[it]) }
}

@PublicApi
inline fun <T, C> List<T>.inParallel(threads: Int = PARALLELISM, crossinline context: (thread: Int) -> C, crossinline job: (context: C, index: Int, element: T) -> Unit): List<C> {
    return indices.inParallel(threads, context) { c, id -> job(c, id, this[id]) }
}

@PublicApi
inline fun <T, C> List<T>.inParallel(threads: Int = PARALLELISM, crossinline context: (thread: Int) -> C, crossinline job: (context: C, element: T) -> Unit): List<C> {
    return indices.inParallel(threads, context) { c, id -> job(c, this[id]) }
}

@PublicApi
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Executable(val index: Int = 0)

@PublicApi
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
@Suppress("unused")
annotation class DependsOn(val path: String)

@PublicApi
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Output(val index: Int = 0)

@PublicApi
fun Any.resolveProjectPath() = lookupProjectPath()

@PublicApi
fun taskYield() = _taskYield()

@PublicApi
data class GenerateLandShapeParams(
        val octaves: Int,
        val width: Int,
        val radius: Float,
        val radialVariance: Float,
        val radialFrequency: Float,
        val positionalVariance: Float,
        val positionalFrequency: Float,
        val cornerBias: Float,
        val whiteOnBlack: Boolean,
        val minIsland: Int,
        val noiseSourceRadial: NoiseSource,
        val noiseFunctionRadial: NoiseFunction,
        val noiseSourcePositional: NoiseSource,
        val noiseFunctionPositional: NoiseFunction
)

@PublicApi
fun batchGenerateLandShapes(
        startSeed: Long,
        count: Int,
        outFilePrefix: String,
        params: GenerateLandShapeParams,
        threads: Int = PARALLELISM,
): List<File> {
    val output = Collections.synchronizedList(ArrayList<File>())
    val atomicSeed = AtomicLong(startSeed)
    with(params) {
        (0 until count).inParallel(threads) {
            var seed: Long
            var noise: ByteArrayMatrix
            do {
                seed = atomicSeed.getAndIncrement()
                noise = generateRadialNoise(
                        randomSeed = seed,
                        octaves = octaves,
                        width = width,
                        radius = radius,
                        radialVariance = radialVariance,
                        radialFrequency = radialFrequency,
                        positionalVariance = positionalVariance,
                        positionalFrequency = positionalFrequency,
                        cornerBias = cornerBias,
                        noiseSourceRadial = noiseSourceRadial,
                        noiseFunctionRadial = noiseFunctionRadial,
                        noiseSourcePositional = noiseSourcePositional,
                        noiseFunctionPositional = noiseFunctionPositional,
                        whiteOnBlack = whiteOnBlack
                )
                taskYield()
            } while (!noise.removeSmallIslands(minIsland, whiteOnBlack))
            output.add(noise.writeGrayU8("$outFilePrefix.$seed.$width"))
        }
    }
    return output
}

@PublicApi
fun generateLandShape(
        seed: Long,
        params: GenerateLandShapeParams,
): ByteArrayMatrix {
    with(params) {
        val noise = generateRadialNoise(
                randomSeed = seed,
                octaves = octaves,
                width = width,
                radius = radius,
                radialVariance = radialVariance,
                radialFrequency = radialFrequency,
                positionalVariance = positionalVariance,
                positionalFrequency = positionalFrequency,
                cornerBias = cornerBias,
                noiseSourceRadial = noiseSourceRadial,
                noiseFunctionRadial = noiseFunctionRadial,
                noiseSourcePositional = noiseSourcePositional,
                noiseFunctionPositional = noiseFunctionPositional,
                whiteOnBlack = whiteOnBlack
        )
        if (!noise.removeSmallIslands(minIsland, whiteOnBlack)) {
            throw IllegalArgumentException("Input shape was too close to borders. Unable to produce a result within image frame.")
        }
        return noise
    }
}

@PublicApi
fun ByteArrayMatrix.refineLandShape(
        randomSeed: Long,
        octaves: Int,
        maskWidth: Int,
        frameWidth: Int,
        frameValue: Byte,
        frequency: Float,
        power: Float,
        blur: Float,
        minIsland: Int,
        whiteOnBlack: Boolean
): ByteArrayMatrix {
    val refined = upSample(maskWidth)
            .threshold()
            .distortByCurl(octaves, randomSeed, frequency, power)
            .blur(blur.toDouble())
            .threshold()
    if (!refined.removeSmallIslands(minIsland, whiteOnBlack)) {
        throw IllegalArgumentException("Input shape was too close to borders. Unable to produce a refined result within image frame.")
    }
    return refined.frame(frameWidth, frameValue)
}

internal fun <T: Comparable<T>> Matrix<T>.parallelMinMax(initialMin: T, initialMax: T): Pair<T, T> {
    return parallelMinMax(initialMin, initialMax) { it }
}

internal inline fun <C: Comparable<C>, T> Matrix<T>.parallelMinMax(initialMin: C, initialMax: C, crossinline function: (T) -> C): Pair<C, C> {
    var min = initialMin
    var max = initialMax
    (0 until width).toList().parallelStream().map { y ->
        val yOff = y * width
        (0 until width).minMaxBy(initialMin, initialMax) { function(this[yOff + it]) }
    }.forEach { (localMin, localMax) ->
        if (min > localMin) {
            min = localMin
        }
        if (max < localMax) {
            max = localMax
        }
    }
    return min to max
}

internal inline fun <T: Comparable<T>> IntRange.minMaxBy(initialMin: T, initialMax: T, function: (Int) -> T): Pair<T, T> {
    var min: T = initialMin
    var max: T = initialMax
    this.forEach {
        val other = function(it)
        if (min > other) {
            min = other
        }
        if (max < other) {
            max = other
        }
    }
    return min to max
}