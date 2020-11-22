package wk.api

import kotlin.math.*

@PublicApi
class IndexDisplayData(val image: ByteArrayMatrix)

@PublicApi
fun ByteArrayMatrix.toIndexDisplayData() = IndexDisplayData(this)

@PublicApi
sealed class ImageDisplayData(val isRgb: Boolean) {
    abstract val image: Any

    @PublicApi
    class Rgb8(override val image: IntArrayMatrix) : ImageDisplayData(true)

    @PublicApi
    class GrayU8(override val image: ByteArrayMatrix) : ImageDisplayData(false)

    @PublicApi
    class GrayU16(override val image: ShortArrayMatrix) : ImageDisplayData(false)

    @PublicApi
    class GrayF32(override val image: FloatArrayMatrix) : ImageDisplayData(false)
}

@PublicApi
fun IntArrayMatrix.toImageDisplayData() = ImageDisplayData.Rgb8(this)

@PublicApi
fun ByteArrayMatrix.toImageDisplayData() = ImageDisplayData.GrayU8(this)

@PublicApi
fun ShortArrayMatrix.toImageDisplayData() = ImageDisplayData.GrayU16(this)

@PublicApi
fun FloatArrayMatrix.toImageDisplayData() = ImageDisplayData.GrayF32(this)

internal val floatToByteRaw = { f: Float -> f.roundToInt().coerceIn(0, 255).toByte() }
internal val floatToByte = { f: Float -> (f * 255).roundToInt().coerceIn(0, 255).toByte() }
internal val byteToFloatRaw = { b: Byte -> (b.toInt() and 0xFF).toFloat() }
internal const val byteToFloatFactor = 1.0f / 255.0f
internal val byteToFloat = { b: Byte -> (b.toInt() and 0xFF) * byteToFloatFactor }

internal val floatToShortRaw = { f: Float -> f.roundToInt().coerceIn(0, 65535).toShort() }
internal val floatToShort = { f: Float -> (f * 65535).roundToInt().coerceIn(0, 65535).toShort() }
internal val shortToFloatRaw = { s: Short -> (s.toInt() and 0xFFFF).toFloat() }
internal const val shortToFloatFactor = 1.0f / 65535.0f
internal val shortToFloat = { s: Short -> (s.toInt() and 0xFFFF) * shortToFloatFactor }

internal val byteToByte = { b: Byte -> b }
internal val shortToShort = { s: Short -> s }
internal val floatToFloat = { f: Float -> f }

internal val byteToShort = { b: Byte -> floatToShort(byteToFloat(b)) }
internal val shortToByte = { s: Short -> floatToByte(shortToFloat(s)) }

internal val byteComparator = Comparator { o1: Byte, o2: Byte -> (o1.toInt() and 0xFF) - (o2.toInt() and 0xFF) }
internal val shortComparator = Comparator { o1: Short, o2: Short -> (o1.toInt() and 0xFFFF) - (o2.toInt() and 0xFFFF) }
internal val floatComparator = Comparator { o1: Float, o2: Float -> if (o1 < o2) -1 else if (o1 == o2) 0 else 1 }

internal val newByteMatrixUniform = { wh: Int -> ByteArrayMatrix(wh) }
internal val newShortMatrixUniform = { wh: Int -> ShortArrayMatrix(wh) }
internal val newFloatMatrixUniform = { wh: Int -> FloatArrayMatrix(wh) }

internal val newByteMatrixNonUniform = { w: Int, h: Int -> ByteArrayMatrix(w, h) }
internal val newShortMatrixNonUniform = { w: Int, h: Int -> ShortArrayMatrix(w, h) }
internal val newFloatMatrixNonUniform = { w: Int, h: Int -> FloatArrayMatrix(w, h) }

private typealias Combiner = (Float, Float) -> Float
private val combinerAdd: Combiner = { a, b -> a + b }
private val combinerSubtract: Combiner = { a, b -> a - b }
private val combinerMultiply: Combiner = { a, b -> a * b }
private val combinerDivide: Combiner = { a, b -> a / b }
private val combinerMin: Combiner = { a, b -> min(a, b) }
private val combinerMax: Combiner = { a, b -> max(a, b) }
private val combinerScreen: Combiner = { a, b -> 1.0f - (1.0f - a) * (1.0f - b) }
private val combinerOverlay: Combiner = { a, b -> if (a < 0.5f) 2.0f * a * b else 1.0f - 2.0f * (1.0f - a) * (1.0f - b) }
private val combinerDifference: Combiner = { a, b -> abs(a - b) }

@PublicApi
fun ByteArrayMatrix.upSample(outputWidth: Int) = upSample(this, outputWidth, newByteMatrixNonUniform, floatToByteRaw, byteToFloatRaw)

@PublicApi
fun ShortArrayMatrix.upSample(outputWidth: Int) = upSample(this, outputWidth, newShortMatrixNonUniform, floatToShortRaw, shortToFloatRaw)

@PublicApi
fun FloatArrayMatrix.upSample(outputWidth: Int) = upSample(this, outputWidth, newFloatMatrixNonUniform, floatToFloat, floatToFloat)

private inline fun <T : Number, M : Matrix<T>> upSample(input: Matrix<T>, outputWidth: Int, matrix: (Int, Int) -> M, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float): M {
    val textureWidth = input.width
    val scale = textureWidth * 1.2f
    val outputWidthInverse = 1.0f / (outputWidth - 1)
    val textureWidthInverse = 1.0f / (textureWidth - 1)
    val outputToInputScale = textureWidth.toFloat() / outputWidth
    val output1 = matrix(textureWidth, outputWidth)
    (0 until textureWidth).inParallel { oy ->
        taskYield()
        val tyOff = oy * textureWidth
        val oyOff = oy * outputWidth
        (0 until outputWidth).forEach { ox ->
            val tx = (ox * outputToInputScale).roundToInt().coerceIn(0 until textureWidth)
            val u = ox * outputWidthInverse
            var sum = 0.0f
            var cSum = 0.0f
            for (n in -3..3) {
                val txo = tx + n
                if (txo < 0 || txo >= textureWidth) {
                    continue
                }
                val ut = txo * textureWidthInverse
                val dx =  (u - ut) * scale
                val d2 = dx * dx
                val c = (0.56418958354 * 2.7182818284590452354.pow(-d2.toDouble())).toFloat()
                sum += toFloat(input[tyOff + txo]) * c
                cSum += c
            }
            output1[oyOff + ox] = fromFloat(sum / cSum)
        }
    }
    val output2 = matrix(outputWidth, outputWidth)
    (0 until outputWidth).inParallel { oy ->
        val yOff = oy * outputWidth
        val ty = (oy * outputToInputScale).roundToInt().coerceIn(0 until textureWidth)
        val v = oy * outputWidthInverse
        (0 until outputWidth).forEach { ox ->
            var sum = 0.0f
            var cSum = 0.0f
            for (m in -3..3) {
                val tyo = ty + m
                if (tyo < 0 || tyo >= textureWidth) {
                    continue
                }
                val vt = tyo * textureWidthInverse
                val dy =  (v - vt) * scale
                val d2 = dy * dy
                val c = (0.56418958354 * 2.7182818284590452354.pow(-d2.toDouble())).toFloat()
                sum += toFloat(output1[tyo * outputWidth + ox]) * c
                cSum += c
            }
            output2[yOff + ox] = fromFloat(sum / cSum)
        }
    }
    return output2
}

@PublicApi
fun ByteArrayMatrix.blur(sigma: Double, repeat: Int = 1) = blur(this, newByteMatrixUniform, floatToByteRaw, byteToFloatRaw, buildBlurKernel(sigma), repeat)

@PublicApi
fun ShortArrayMatrix.blur(sigma: Double, repeat: Int = 1) = blur(this, newShortMatrixUniform, floatToShortRaw, shortToFloatRaw, buildBlurKernel(sigma), repeat)

@PublicApi
fun FloatArrayMatrix.blur(sigma: Double, repeat: Int = 1) = blur(this, newFloatMatrixUniform, floatToFloat, floatToFloat, buildBlurKernel(sigma), repeat)

@PublicApi
fun ByteArrayMatrix.blur(kernel: FloatArray, repeat: Int = 1) = blur(this, newByteMatrixUniform, floatToByteRaw, byteToFloatRaw, kernel, repeat)

@PublicApi
fun ShortArrayMatrix.blur(kernel: FloatArray, repeat: Int = 1) = blur(this, newShortMatrixUniform, floatToShortRaw, shortToFloatRaw, kernel, repeat)

@PublicApi
fun FloatArrayMatrix.blur(kernel: FloatArray, repeat: Int = 1) = blur(this, newFloatMatrixUniform, floatToFloat, floatToFloat, kernel, repeat)

private inline fun <T : Number, M : Matrix<T>> blur(input: Matrix<T>, matrix: (Int) -> M, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float, kernel: FloatArray, times: Int = 1): M {
    val width = input.width
    var current = input
    val output1 = matrix(width)
    val output2 = matrix(width)
    val kernelSize = kernel.size
    if (kernelSize % 2 != 1) {
        throw IllegalArgumentException("Kernel size must not be even.")
    }
    val kernelHalfSize = kernelSize / 2

    repeat(times) {
        (0 until width).inParallel { y ->
            taskYield()
            val yOff = y * width
            (0 until width).forEach { x ->
                var sum = 0.0f
                var cSum = 0.0f
                kernel.indices.forEach { o ->
                    val ox = x + o - kernelHalfSize
                    if (ox in 0 until width) {
                        val c = kernel[o]
                        sum += c * toFloat(current[yOff + ox])
                        cSum += c
                    }
                }
                output1[yOff + x] = fromFloat(sum / cSum)
            }
        }
        (0 until width).inParallel { x ->
            (0 until width).forEach { y ->
                var sum = 0.0f
                var cSum = 0.0f
                kernel.indices.forEach { o ->
                    val oy = y + o - kernelHalfSize
                    if (oy in 0 until width) {
                        val c = kernel[o]
                        sum += c * toFloat(output1[oy * width + x])
                        cSum += c
                    }
                }
                output2[y * width + x] = fromFloat(sum / cSum)
            }
        }
        current = output2
    }
    return output2
}

@PublicApi
fun ByteArrayMatrix.threshold(threshold: Byte = Byte.MAX_VALUE, on: Byte = -1, off: Byte = 0, output: ByteArrayMatrix = this) = threshold(this, threshold, on, off, byteComparator, output)

@PublicApi
fun ShortArrayMatrix.threshold(threshold: Short = Short.MAX_VALUE, on: Short = -1, off: Short = 0, output: ShortArrayMatrix = this) = threshold(this, threshold, on, off, shortComparator, output)

@PublicApi
fun FloatArrayMatrix.threshold(threshold: Float = 0.5f, on: Float = 1.0f, off: Float = 0.0f, output: FloatArrayMatrix = this) = threshold(this, threshold, on, off, floatComparator, output)

private fun <T : Comparable<T>, M : Matrix<T>> threshold(input: M, threshold: T, on: T, off: T, comparator: Comparator<T>, output: M = input): M {
    (0 until input.height).inParallel { y ->
        taskYield()
        val yOff = y * input.width
        for (x in 0 until input.width) {
            val i = yOff + x
            output[i] = if (comparator.compare(input[i], threshold) < 0) off else on
        }
    }
    return output
}

@PublicApi
fun ByteArrayMatrix.applyMask(target: ByteArrayMatrix, threshold: Byte = Byte.MAX_VALUE, on: (Byte) -> Byte = byteToByte, off: (Byte) -> Byte = { 0 }, output: ByteArrayMatrix = target) = applyMaskB(target, output, threshold, on, off)

@PublicApi
fun ByteArrayMatrix.applyMask(target: ShortArrayMatrix, threshold: Byte = Byte.MAX_VALUE, on: (Short) -> Short = shortToShort, off: (Short) -> Short = { 0 }, output: ShortArrayMatrix  = target) = applyMaskB(target, output, threshold, on, off)

@PublicApi
fun ByteArrayMatrix.applyMask(target: FloatArrayMatrix, threshold: Byte = Byte.MAX_VALUE, on: (Float) -> Float = floatToFloat, off: (Float) -> Float = { 0.0f }, output: FloatArrayMatrix  = target) = applyMaskB(target, output, threshold, on, off)

@PublicApi
fun ShortArrayMatrix.applyMask(target: ByteArrayMatrix, threshold: Short = Short.MAX_VALUE, on: (Byte) -> Byte = byteToByte, off: (Byte) -> Byte = { 0 }, output: ByteArrayMatrix = target) = applyMaskS(target, output, threshold, on, off)

@PublicApi
fun ShortArrayMatrix.applyMask(target: ShortArrayMatrix, threshold: Short = Short.MAX_VALUE, on: (Short) -> Short = shortToShort, off: (Short) -> Short = { 0 }, output: ShortArrayMatrix = target) = applyMaskS(target, output, threshold, on, off)

@PublicApi
fun ShortArrayMatrix.applyMask(target: FloatArrayMatrix, threshold: Short = Short.MAX_VALUE, on: (Float) -> Float = floatToFloat, off: (Float) -> Float = { 0.0f }, output: FloatArrayMatrix = target) = applyMaskS(target, output, threshold, on, off)

@PublicApi
fun FloatArrayMatrix.applyMask(target: ByteArrayMatrix, threshold: Float = 0.5f, on: (Byte) -> Byte = byteToByte, off: (Byte) -> Byte = { 0 }, output: ByteArrayMatrix = target) = applyMaskF(target, output, threshold, on, off)

@PublicApi
fun FloatArrayMatrix.applyMask(target: ShortArrayMatrix, threshold: Float = 0.5f, on: (Short) -> Short = shortToShort, off: (Short) -> Short = { 0 }, output: ShortArrayMatrix = target) = applyMaskF(target, output, threshold, on, off)

@PublicApi
fun FloatArrayMatrix.applyMask(target: FloatArrayMatrix, threshold: Float = 0.5f, on: (Float) -> Float = floatToFloat, off: (Float) -> Float = { 0.0f }, output: FloatArrayMatrix = target) = applyMaskF(target, output, threshold, on, off)

private inline fun <T, M : Matrix<T>> ByteArrayMatrix.applyMaskB(target: M, output: M, threshold: Byte, crossinline on: (T) -> T, crossinline off: (T) -> T) = applyMask(this, target, output, threshold, on, off, byteComparator)

private inline fun <T, M : Matrix<T>> ShortArrayMatrix.applyMaskS(target: M, output: M, threshold: Short, crossinline on: (T) -> T, crossinline off: (T) -> T) = applyMask(this, target, output, threshold, on, off, shortComparator)

private inline fun <T, M : Matrix<T>> FloatArrayMatrix.applyMaskF(target: M, output: M, threshold: Float, crossinline on: (T) -> T, crossinline off: (T) -> T) = applyMask(this, target, output, threshold, on, off, floatComparator)

private inline fun <T : Comparable<T>, M : Matrix<T>, V, O : Matrix<V>> applyMask(mask: M, target: O, output: O, threshold: T, crossinline on: (V) -> V, crossinline off: (V) -> V, comparator: Comparator<T>): O {
    (0 until mask.height).inParallel { y ->
        taskYield()
        val yOff = y * mask.width
        for (x in 0 until mask.width) {
            val i = yOff + x
            output[i] = if (comparator.compare(mask[i], threshold) < 0) off(target[i]) else on(target[i])
        }
    }
    return output
}

private const val OFF: Byte = 0
private const val ON: Byte = -1

@PublicApi
fun <M : Matrix<Byte>> M.removeSmallIslands(minIsland: Int, whiteOnBlack: Boolean = true, invert: Boolean = false): Boolean {
    val buffer = this
    val oceanSearch = if (whiteOnBlack) OFF else ON
    val islandSearch = if (whiteOnBlack) ON else OFF
    val oceanOut = if (invert) islandSearch else oceanSearch
    val islandOut = if (invert) oceanSearch else islandSearch
    val largestIslandWasRemoved = floodFill(0, oceanSearch, 10, islandSearch, 20, minIsland)
    if (largestIslandWasRemoved) {
        return false
    }
    (0 until width).inParallel { y ->
        taskYield()
        val yOff = y * width
        for (i in yOff until yOff + width) {
            buffer[i] = if (buffer[i] == 10.toByte()) oceanOut else islandOut
        }
    }
    return true
}

private fun Matrix<Byte>.floodFill(seed: Int, oceanSearch: Byte, oceanFill: Byte, islandSearch: Byte, islandFill: Byte, minIsland: Int): Boolean {
    val buffer = this
    var frontier1 = ArrayList<Int>()
    var frontier2 = ArrayList<Int>()
    val tempList1 = ArrayList<Int>()
    val tempList2 = ArrayList<Int>()
    val outList = ArrayList<Int>()
    frontier1.add(seed)
    buffer[seed] = oceanFill
    var largestIsland = 0
    var largestIslandWasRemoved = false
    do {
        frontier1.forEach { i ->
            val x = i % width
            val y = i / width
            forEachAdjacent(width, i, x, y) { a ->
                if (buffer[a] == oceanSearch) {
                    buffer[a] = oceanFill
                    frontier2.add(a)
                } else if (buffer[a] == islandSearch) {
                    val isOnBorder = floodFill(a, islandSearch, islandFill, tempList1, tempList2, outList)
                    if (outList.size > largestIsland) {
                        largestIsland = outList.size
                        largestIslandWasRemoved = isOnBorder
                    }
                    if (isOnBorder || outList.size < minIsland) {
                        outList.forEach { j ->
                            buffer[j] = oceanSearch
                        }
                        frontier2.addAll(outList)
                    }
                }
            }
        }
        val temp = frontier1
        temp.clear()
        frontier1 = frontier2
        frontier2 = temp
    } while (frontier1.isNotEmpty())
    return largestIslandWasRemoved
}

private fun Matrix<Byte>.floodFill(seed: Int, islandSearch: Byte, islandFill: Byte, tempList1: ArrayList<Int>, tempList2: ArrayList<Int>, outList: ArrayList<Int>): Boolean {
    val buffer = this
    val widthM1 = width - 1
    var isOnBorder = false
    tempList1.clear()
    tempList2.clear()
    outList.clear()
    var frontier1 = tempList1
    var frontier2 = tempList2
    frontier1.add(seed)
    buffer[seed] = islandFill
    do {
        outList.addAll(frontier1)
        frontier1.forEach { i ->
            val x = i % width
            val y = i / width
            if (!isOnBorder && (x == 0 || x == widthM1 || y == 0 || y == widthM1)) {
                isOnBorder = true
            }
            forEachAdjacent(width, i, x, y) { a ->
                if (buffer[a] == islandSearch) {
                    buffer[a] = islandFill
                    frontier2.add(a)
                }
            }
        }
        val temp = frontier1
        temp.clear()
        frontier1 = frontier2
        frontier2 = temp
    } while (frontier1.isNotEmpty())
    return isOnBorder
}

private inline fun forEachAdjacent(width: Int, i: Int, x: Int, y: Int, callback: (Int) -> Unit) {
    if (y > 0) {
        callback(i - width)
    }
    if (y < width - 1) {
        callback(i + width)
    }
    if (x > 0) {
        callback(i - 1)
    }
    if (x < width - 1) {
        callback(i + 1)
    }
}

@PublicApi
fun buildBlurKernel(sigma: Double = 1.0): FloatArray {
    val rawKernelWidth: Int = (6 * sigma).roundToInt()
    val width = if (rawKernelWidth % 2 == 0) rawKernelWidth + 1 else rawKernelWidth
    val mean = width / 2.0
    val c1 = -1.0 / (2.0 * sigma * sigma)
    val c2 = 1.0 / (2.0 * PI * sigma * sigma)
    val kernel2D = DoubleArray(width * width)
    (0 until width).inParallel { y ->
        val yOff = y * width
        (0 until width).forEach { x ->
            var sum = 0.0
            (-10..9).forEach { xi ->
                val xd = x + 0.525 + xi * 0.05 - mean
                val xd2 = xd * xd
                (-10..9).forEach { yi ->
                    val yd = y + 0.525 + yi * 0.05 - mean
                    val yd2 = yd * yd
                    sum += exp((xd2 + yd2) * c1) * c2
                }
            }
            kernel2D[yOff + x] = sum
        }
    }
    val kernel1D = DoubleArray(width)
    (0 until width).inParallel { y ->
        val yOff = y * width
        var sum = 0.0
        (0 until width).forEach { x ->
            sum += kernel2D[yOff + x]
        }
        kernel1D[y] = sum
    }
    (0 until width / 2).forEach { i ->
        val i2 = width - 1 - i
        val avg = (kernel1D[i] + kernel1D[i2]) * 0.5
        kernel1D[i] = avg
        kernel1D[i2] = avg
    }
    val scale = 1.0 / kernel1D.sum()
    val kernel = FloatArray(width)
    kernel1D.forEachIndexed { i, it ->
        kernel[i] = (it * scale).toFloat()
    }
    return kernel
}

@PublicApi
fun ByteArrayMatrix.frame(outputWidth: Int, frameValue: Byte) = frame(this, outputWidth, frameValue, newByteMatrixUniform)

@PublicApi
fun ShortArrayMatrix.frame(outputWidth: Int, frameValue: Short) = frame(this, outputWidth, frameValue, newShortMatrixUniform)

@PublicApi
fun FloatArrayMatrix.frame(outputWidth: Int, frameValue: Float) = frame(this, outputWidth, frameValue, newFloatMatrixUniform)

private inline fun <T : Number, M : Matrix<T>> frame(input: Matrix<T>, outputWidth: Int, frameValue: T, matrix: (Int) -> M): M {
    val buffer = matrix(outputWidth)
    val xOffset = (outputWidth - input.width) / 2
    val yOffset = (outputWidth - input.height) / 2
    (0 until outputWidth).inParallel { yOut ->
        taskYield()
        val yOffOut = yOut * outputWidth
        val yIn = yOut - yOffset
        val yOffIn = yIn * input.width
        val yInRange = yIn in (0 until input.height)
        for (xOut in 0 until outputWidth) {
            val i = yOffOut + xOut
            val xIn = xOut - xOffset
            if (yInRange && xIn in (0 until input.width)) {
                buffer[i] = input[yOffIn + xIn]
            } else {
                buffer[i] = frameValue
            }
        }
    }
    return buffer
}

@PublicApi
fun ByteArrayMatrix.normalize(keepZero: Boolean = false) = normalize(this, floatToByte, byteToFloatRaw, keepZero)

@PublicApi
fun ShortArrayMatrix.normalize(keepZero: Boolean = false) = normalize(this, floatToShort, shortToFloatRaw, keepZero)

@PublicApi
fun FloatArrayMatrix.normalize(keepZero: Boolean = false) = normalize(this, floatToFloat, floatToFloat, keepZero)

private inline fun <T, M : Matrix<T>> normalize(input: M, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float, keepZero: Boolean): M {
    val (min, max) = input.parallelMinMax(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, toFloat)
    val adjustedMin = if (keepZero) min(0.0f, min) else min
    val delta = max - adjustedMin
    val deltaI = 1.0f / delta
    for (i in 0 until input.size) {
        input[i] = fromFloat((toFloat(input[i]) - adjustedMin) * deltaI)
    }
    return input
}

@PublicApi
fun ByteArrayMatrix.compressIndex(forceIncludeZero: Boolean = false): ByteArrayMatrix {
    val mapping = ByteArray(256)
    if (forceIncludeZero) {
        mapping[0] = 1
    }
    (0 until height).inParallel { y ->
        taskYield()
        val yOff = y * width
        for (x in 0 until width) {
            mapping[this[yOff + x].toInt() and 0xFF] = 1
        }
    }
    mapping.mapIndexed { i, it -> i to it }
            .filter { it.second == 1.toByte() }
            .forEachIndexed { to, (from, _) -> mapping[from] = to.toByte() }
    (0 until height).inParallel { y ->
        taskYield()
        val yOff = y * width
        for (x in 0 until width) {
            this[yOff + x] = mapping[this[yOff + x].toInt() and 0xFF]
        }
    }
    return this
}

@PublicApi
fun ByteArrayMatrix.toShortMatrix(output: ShortArrayMatrix = ShortArrayMatrix(width, height)) = convert(this, output, byteToShort)

@PublicApi
fun ByteArrayMatrix.toFloatMatrix(output: FloatArrayMatrix = FloatArrayMatrix(width, height)) = convert(this, output, byteToFloat)

@PublicApi
fun ShortArrayMatrix.toByteMatrix(output: ByteArrayMatrix = ByteArrayMatrix(width, height)) = convert(this, output, shortToByte)

@PublicApi
fun ShortArrayMatrix.toFloatMatrix(output: FloatArrayMatrix = FloatArrayMatrix(width, height)) = convert(this, output, shortToFloat)

@PublicApi
fun FloatArrayMatrix.toByteMatrix(output: ByteArrayMatrix = ByteArrayMatrix(width, height)) = convert(this, output, floatToByte)

@PublicApi
fun FloatArrayMatrix.toShortMatrix(output: ShortArrayMatrix = ShortArrayMatrix(width, height)) = convert(this, output, floatToShort)

private inline fun <S, I : Matrix<S>, T, O : Matrix<T>> convert(input: I, output: O, crossinline convert: (S) -> T): O {
    (0 until input.height).inParallel { y ->
        val yOff = y * input.width
        (0 until input.width).forEach { x ->
            val i = yOff + x
            output[i] = convert(input[i])
        }
    }
    return output
}

@PublicApi
fun FloatArrayMatrix.toStandardizedHeightMap(mapScale: MapScale, output: ShortArrayMatrix = ShortArrayMatrix(width, height)): Pair<ShortArrayMatrix, Float> {
    val scale = 1.0f / mapScale.heightRangeMeters
    val min = -mapScale.waterDepthMeters
    val adjustedScale = scale * 65535
    var maxHeight = 0.0f
    (0 until width).inParallel(
            context = { ref(0.0f) }
    ) { localMaxRef, y ->
        val yOff = y * width
        var localMaxHeight = localMaxRef.value
        for (x in (0 until width)) {
            val heightValue = this[yOff + x]
            if (heightValue > localMaxHeight) {
                localMaxHeight = heightValue
            }
            output[yOff + x] = ((heightValue - min) * adjustedScale).roundToInt().coerceIn(0, 65535).toShort()
        }
        localMaxRef.value = localMaxHeight
    }.forEach {
        if (it.value > maxHeight) {
            maxHeight = it.value
        }
    }
    return output to 1.0f / ((maxHeight - min) * scale).coerceIn(0.0f, 1.0f)
}


@PublicApi
fun ByteArrayMatrix.blend(other: ByteArrayMatrix, alpha: Float = 0.5f, output: ByteArrayMatrix = this) = blend(this, other, output, alpha, floatToByteRaw, byteToFloatRaw)

@PublicApi
fun ShortArrayMatrix.blend(other: ShortArrayMatrix, alpha: Float = 0.5f, output: ShortArrayMatrix = this) = blend(this, other, output, alpha, floatToShortRaw, shortToFloatRaw)

@PublicApi
fun FloatArrayMatrix.blend(other: FloatArrayMatrix, alpha: Float = 0.5f, output: FloatArrayMatrix = this) = blend(this, other, output, alpha, floatToFloat, floatToFloat)

private inline fun <T : Comparable<T>, M : Matrix<T>> blend(input1: M, input2: M, output: M, alpha: Float, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float): M {
    val iAlpha = 1.0f - alpha
    (0 until input1.height).inParallel { y ->
        val yOff = y * input1.width
        for (x in 0 until input1.width) {
            val i = yOff + x
            output[i] = fromFloat(toFloat(input1[i]) * iAlpha + toFloat(input2[i]) * alpha)
        }
    }
    return output
}

@PublicApi
fun ByteArrayMatrix.blend(other: ByteArrayMatrix, blendMask: ByteArrayMatrix, output: ByteArrayMatrix = this) = blend(this, other, output, blendMask, floatToByteRaw, byteToFloatRaw, byteToFloat)

@PublicApi
fun ByteArrayMatrix.blend(other: ByteArrayMatrix, blendMask: ShortArrayMatrix, output: ByteArrayMatrix = this) = blend(this, other, output, blendMask, floatToByteRaw, byteToFloatRaw, shortToFloat)

@PublicApi
fun ByteArrayMatrix.blend(other: ByteArrayMatrix, blendMask: FloatArrayMatrix, output: ByteArrayMatrix = this) = blend(this, other, output, blendMask, floatToByteRaw, byteToFloatRaw, floatToFloat)

@PublicApi
fun ShortArrayMatrix.blend(other: ShortArrayMatrix, blendMask: ByteArrayMatrix, output: ShortArrayMatrix = this) = blend(this, other, output, blendMask, floatToShortRaw, shortToFloatRaw, byteToFloat)

@PublicApi
fun ShortArrayMatrix.blend(other: ShortArrayMatrix, blendMask: ShortArrayMatrix, output: ShortArrayMatrix = this) = blend(this, other, output, blendMask, floatToShortRaw, shortToFloatRaw, shortToFloat)

@PublicApi
fun ShortArrayMatrix.blend(other: ShortArrayMatrix, blendMask: FloatArrayMatrix, output: ShortArrayMatrix = this) = blend(this, other, output, blendMask, floatToShortRaw, shortToFloatRaw, floatToFloat)

@PublicApi
fun FloatArrayMatrix.blend(other: FloatArrayMatrix, blendMask: ByteArrayMatrix, output: FloatArrayMatrix = this) = blend(this, other, output, blendMask, floatToFloat, floatToFloat, byteToFloat)

@PublicApi
fun FloatArrayMatrix.blend(other: FloatArrayMatrix, blendMask: ShortArrayMatrix, output: FloatArrayMatrix = this) = blend(this, other, output, blendMask, floatToFloat, floatToFloat, shortToFloat)

@PublicApi
fun FloatArrayMatrix.blend(other: FloatArrayMatrix, blendMask: FloatArrayMatrix, output: FloatArrayMatrix = this) = blend(this, other, output, blendMask, floatToFloat, floatToFloat, floatToFloat)

private inline fun <T : Comparable<T>, M : Matrix<T>, A : Comparable<A>, B : Matrix<A>> blend(input1: M, input2: M, output: M, blendMask: B, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float, crossinline toAlpha: (A) -> Float): M {
    (0 until input1.height).inParallel { y ->
        val yOff = y * input1.width
        for (x in 0 until input1.width) {
            val i = yOff + x
            val alpha = toAlpha(blendMask[i])
            val iAlpha = 1.0f - alpha
            output[i] = fromFloat(toFloat(input1[i]) * iAlpha + toFloat(input2[i]) * alpha)
        }
    }
    return output
}

@PublicApi
fun ByteArrayMatrix.add(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerAdd)

@PublicApi
fun ShortArrayMatrix.add(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerAdd)

@PublicApi
fun FloatArrayMatrix.add(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerAdd)

@PublicApi
fun ByteArrayMatrix.subtract(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerSubtract)

@PublicApi
fun ShortArrayMatrix.subtract(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerSubtract)

@PublicApi
fun FloatArrayMatrix.subtract(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerSubtract)

@PublicApi
fun ByteArrayMatrix.multiply(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerMultiply)

@PublicApi
fun ShortArrayMatrix.multiply(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerMultiply)

@PublicApi
fun FloatArrayMatrix.multiply(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerMultiply)

@PublicApi
fun ByteArrayMatrix.divide(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerDivide)

@PublicApi
fun ShortArrayMatrix.divide(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerDivide)

@PublicApi
fun FloatArrayMatrix.divide(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerDivide)

@PublicApi
fun ByteArrayMatrix.min(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerMin)

@PublicApi
fun ShortArrayMatrix.min(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerMin)

@PublicApi
fun FloatArrayMatrix.min(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerMin)

@PublicApi
fun ByteArrayMatrix.max(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerMax)

@PublicApi
fun ShortArrayMatrix.max(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerMax)

@PublicApi
fun FloatArrayMatrix.max(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerMax)

@PublicApi
fun ByteArrayMatrix.screen(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerScreen)

@PublicApi
fun ShortArrayMatrix.screen(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerScreen)

@PublicApi
fun FloatArrayMatrix.screen(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerScreen)

@PublicApi
fun ByteArrayMatrix.overlay(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerOverlay)

@PublicApi
fun ShortArrayMatrix.overlay(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerOverlay)

@PublicApi
fun FloatArrayMatrix.overlay(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerOverlay)

@PublicApi
fun ByteArrayMatrix.difference(other: ByteArrayMatrix, output: ByteArrayMatrix = this) = combine(this, other, output, floatToByte, byteToFloat, combinerDifference)

@PublicApi
fun ShortArrayMatrix.difference(other: ShortArrayMatrix, output: ShortArrayMatrix = this) = combine(this, other, output, floatToShort, shortToFloat, combinerDifference)

@PublicApi
fun FloatArrayMatrix.difference(other: FloatArrayMatrix, output: FloatArrayMatrix = this) = combine(this, other, output, floatToFloat, floatToFloat, combinerDifference)

private inline fun <T : Comparable<T>, M : Matrix<T>> combine(input1: M, input2: M, output: M, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float, crossinline combiner: (Float, Float) -> Float): M {
    (0 until input1.height).inParallel { y ->
        val yOff = y * input1.width
        for (x in 0 until input1.width) {
            val i = yOff + x
            output[i] = fromFloat(combiner(toFloat(input1[i]), toFloat(input2[i])))
        }
    }
    return output
}

@PublicApi
fun ByteArrayMatrix.adjust(multiply: Float = 2.0f, add: Float = -1.0f, range: ClosedFloatingPointRange<Float> = 0.0f..1.0f, output: ByteArrayMatrix = this) = adjust(this, output, multiply, add, range, floatToByte, byteToFloatRaw)

@PublicApi
fun ShortArrayMatrix.adjust(multiply: Float = 2.0f, add: Float = -1.0f, range: ClosedFloatingPointRange<Float> = 0.0f..1.0f, output: ShortArrayMatrix = this) = adjust(this, output, multiply, add, range, floatToShort, shortToFloatRaw)

@PublicApi
fun FloatArrayMatrix.adjust(multiply: Float = 2.0f, add: Float = -1.0f, range: ClosedFloatingPointRange<Float> = 0.0f..1.0f, output: FloatArrayMatrix = this) = adjust(this, output, multiply, add, range, floatToFloat, floatToFloat)

private inline fun <T : Comparable<T>, M : Matrix<T>> adjust(input1: M, output: M, multiply: Float, add: Float, range: ClosedFloatingPointRange<Float> = 0.0f..1.0f, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float): M {
    (0 until input1.height).inParallel { y ->
        val yOff = y * input1.width
        for (x in 0 until input1.width) {
            val i = yOff + x
            output[i] = fromFloat((toFloat(input1[i]) * multiply + add).coerceIn(range))
        }
    }
    return output
}

@PublicApi
fun ByteArrayMatrix.step(steps: Int, output: ByteArrayMatrix = this) = step(this, output, steps, floatToByte, byteToFloatRaw)

@PublicApi
fun ShortArrayMatrix.step(steps: Int, output: ShortArrayMatrix = this) = step(this, output, steps, floatToShort, shortToFloatRaw)

@PublicApi
fun FloatArrayMatrix.step(steps: Int, output: FloatArrayMatrix = this) = step(this, output, steps, floatToFloat, floatToFloat)

private inline fun <T : Comparable<T>, M : Matrix<T>> step(input1: M, output: M, steps: Int, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float): M {
    val stepsM1 = steps - 1.0f
    val stepsM1I = 1.0f / stepsM1
    (0 until input1.height).inParallel { y ->
        val yOff = y * input1.width
        for (x in 0 until input1.width) {
            val i = yOff + x
            output[i] = fromFloat((toFloat(input1[i]) * stepsM1).roundToInt() * stepsM1I)
        }
    }
    return output
}

@PublicApi
fun ByteArrayMatrix.levels(range: ClosedFloatingPointRange<Float>, gamma: Float, output: ByteArrayMatrix = this) = levels(this, output, range, gamma, floatToByte, byteToFloatRaw)

@PublicApi
fun ShortArrayMatrix.levels(range: ClosedFloatingPointRange<Float>, gamma: Float, output: ShortArrayMatrix = this) = levels(this, output, range, gamma, floatToShort, shortToFloatRaw)

@PublicApi
fun FloatArrayMatrix.levels(range: ClosedFloatingPointRange<Float>, gamma: Float, output: FloatArrayMatrix = this) = levels(this, output, range, gamma, floatToFloat, floatToFloat)

private inline fun <T : Comparable<T>, M : Matrix<T>> levels(input1: M, output: M, range: ClosedFloatingPointRange<Float>, gamma: Float, crossinline fromFloat: (Float) -> T, crossinline toFloat: (T) -> Float): M {
    val min = range.start
    val max = range.endInclusive
    val delta = max - min
    val deltaI = 1.0f / delta
    (0 until input1.height).inParallel { y ->
        val yOff = y * input1.width
        for (x in 0 until input1.width) {
            val i = yOff + x
            output[i] = fromFloat(max(0.0f, (toFloat(input1[i]) - min) * deltaI).pow(gamma).coerceIn(0.0f, 1.0f))
        }
    }
    return output
}

@PublicApi
interface TextureId {

    val id: Int

    fun free()
}

@PublicApi
fun FloatArrayMatrix.decompose(levels: Int): List<FloatArrayMatrix> {
    if (width == 0) throw IllegalArgumentException("Cannot decompose zero area matrix.")
    if (width != height) throw IllegalArgumentException("Cannot decompose rectangular matrix.")
    val decompWidth = nextPowerOfTwo(width)
    var baseWidth = decompWidth
    for (i in 1 until levels) {
        baseWidth /= 2
    }
    if (baseWidth == 0) throw java.lang.IllegalArgumentException("Not enough resolution to decompose into $levels levels.")
    val decomp = if (width != decompWidth) {
        upSample(decompWidth)
    } else {
        this
    }
    val output = ArrayList<FloatArrayMatrix>(levels)
    var currentLevel = decomp
    for (i in 1 until levels) {
        val (nextLevel, decompLayer) = currentLevel.decomp()
        output.add(decompLayer)
        currentLevel = nextLevel
    }
    output.add(currentLevel)
    return output.reversed()
}

private fun FloatArrayMatrix.decomp(): Pair<FloatArrayMatrix, FloatArrayMatrix> {
    val high = this
    val halfWidth = width / 2
    val halfRes = FloatArrayMatrix(halfWidth)
    val diff = FloatArrayMatrix(width)
    (0 until halfWidth).inParallel { y ->
        val lowYOff = y * halfWidth
        for (x in 0 until halfWidth) {
            val i1 = ((y * 2) * width) + x * 2
            val i2 = i1 + 1
            val i3 = (((y * 2) + 1) * width) + x * 2
            val i4 = i3 + 1
            val p1 = high[i1]
            val p2 = high[i2]
            val p3 = high[i3]
            val p4 = high[i4]
            val m = min(min(p1, p2), min(p3, p4))
            halfRes[lowYOff + x] = m
            diff[i1] = p1 - m
            diff[i2] = p2 - m
            diff[i3] = p3 - m
            diff[i4] = p4 - m
        }
    }
    return halfRes to diff
}

private fun nextPowerOfTwo(i: Int): Int {
    var v = i - 1
    v = v or (v shr 1)
    v = v or (v shr 2)
    v = v or (v shr 4)
    v = v or (v shr 8)
    v = v or (v shr 16)
    return v + 1
}