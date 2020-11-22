package wk.api

import kotlin.math.roundToLong
import kotlin.math.sqrt

@PublicApi
fun Matrix<Byte>.toSdf(sdfWidth: Float = width.toFloat(), sdfMax: Float = sdfWidth * 0.5f): ShortArrayMatrix {
    val mask = this
    val distanceFactor = (sdfWidth / width) / sdfMax
    val dists = computeSdf(mask)
    return ShortArrayMatrix(width) { i ->
        val d = if ((mask[i].toInt() and 0xFF) < 128) -dists[i] else dists[i]
        (((d * distanceFactor + 1) * 0.5).coerceIn(0.0, 1.0) * 65535.0 + 0.5).roundToLong().toShort()
    }
}

private fun computeSdf(image: Matrix<Byte>): FloatArrayMatrix {
    val sdf = sdfInit(image)
    (0 until sdf.width).inParallel(
            context = { Triple(FloatArray(sdf.height), FloatArray(sdf.height + 1), ShortArray(sdf.height)) }
    ) { (buffer, intersections, indices), x ->
        taskYield()
        sdfVerticalStripe(sdf, buffer, intersections, indices, x)
    }
    (0 until sdf.height).inParallel(
            context = { Triple(FloatArray(sdf.width), FloatArray(sdf.width + 1), ShortArray(sdf.width)) }
    ) { (buffer, intersections, indices), y ->
        taskYield()
        sdfHorizontalStripe(sdf, buffer, intersections, indices, y)
    }
    return sdf
}

private fun sdfInit(image: Matrix<Byte>): FloatArrayMatrix {
    val sdf = FloatArrayMatrix(image.width)
    val maxIndexX = sdf.width - 1
    val maxIndexY = sdf.height - 1
    (0 until sdf.height).inParallel { y ->
        taskYield()
        val yOff = y * sdf.width
        val yOffM1 = (y - 1).coerceIn(0, maxIndexY) * sdf.width
        val yOffP1 = (y + 1).coerceIn(0, maxIndexY) * sdf.width
        for (x in 0 until sdf.width) {
            val i = yOff + x
            val pixel = image[i]
            if (pixel.toInt() and 0xFF > 127 &&
                    (image[yOff + (x - 1).coerceIn(0, maxIndexX)] != pixel
                            || image[yOff + (x + 1).coerceIn(0, maxIndexX)] != pixel
                            || image[yOffM1 + x] != pixel || image[yOffP1 + x] != pixel)) {
                sdf[i] = 0.0f
            } else {
                sdf[i] = Float.MAX_VALUE
            }
        }
    }
    return sdf
}

private fun sdfVerticalStripe(sdf: FloatArrayMatrix, buffer: FloatArray, intersections: FloatArray, indices: ShortArray, x: Int) {
    var i = 0
    indices[0] = 0
    intersections[0] = -Float.MAX_VALUE
    intersections[1] = Float.MAX_VALUE
    for (y in 1 until sdf.height) {
        val d = sdf[x, y] + (y * y)
        val y2 = 2 * y
        var index = indices[i].toInt()
        var s = (d - (sdf[x, index] + index * index)) / (y2 - 2 * index)
        while (s <= intersections[i]) {
            i--
            index = indices[i].toInt()
            s = (d - (sdf[x, index] + index * index)) / (y2 - 2 * index)
        }
        i++
        indices[i] = y.toShort()
        intersections[i] = s
        intersections[i + 1] = Float.MAX_VALUE
    }
    i = 0
    for (y in 0 until sdf.height) {
        while (intersections[i + 1] < y) {
            i++
        }
        val index = indices[i].toInt()
        buffer[y] = ((y - index) * (y - index)) + sdf[x, index]
    }
    for (y in 0 until sdf.height) {
        sdf[x, y] = buffer[y]
    }
}

private fun sdfHorizontalStripe(sdf: FloatArrayMatrix, buffer: FloatArray, intersections: FloatArray, indices: ShortArray, y: Int) {
    val yOff = y * sdf.width
    var i = 0
    indices[0] = 0
    intersections[0] = -Float.MAX_VALUE
    intersections[1] = Float.MAX_VALUE
    for (x in 1 until sdf.width) {
        val d = sdf[yOff + x] + (x * x)
        val x2 = 2 * x
        var index = indices[i].toInt()
        var s = (d - (sdf[yOff + index] + index * index)) / (x2 - 2 * index)
        while (s <= intersections[i]) {
            i--
            index = indices[i].toInt()
            s = (d - (sdf[yOff + index] + index * index)) / (x2 - 2 * index)
        }
        i++
        indices[i] = x.toShort()
        intersections[i] = s
        intersections[i + 1] = Float.MAX_VALUE
    }
    i = 0
    for (x in 0 until sdf.width) {
        while (intersections[i + 1] < x) {
            i++
        }
        val index = indices[i].toInt()
        buffer[x] = ((x - index) * (x - index)) + sdf[yOff + index]
    }
    for (x in 0 until sdf.width) {
        sdf[yOff + x] = sqrt(buffer[x])
    }
}

typealias SdfApplyFun<T> = (Float, T) -> Float

@PublicApi
fun ByteArrayMatrix.applySdf(sdf: ByteArrayMatrix, output: ByteArrayMatrix = this, apply: SdfApplyFun<Byte>) = applySdf(sdf, this, output, apply, byteToFloat, floatToByteRaw)

@PublicApi
fun ByteArrayMatrix.applySdf(sdf: ShortArrayMatrix, output: ByteArrayMatrix = this, apply: SdfApplyFun<Byte>) = applySdf(sdf, this, output, apply, shortToFloat, floatToByteRaw)

@PublicApi
fun ByteArrayMatrix.applySdf(sdf: FloatArrayMatrix, output: ByteArrayMatrix = this, apply: SdfApplyFun<Byte>) = applySdf(sdf, this, output, apply, floatToFloat, floatToByteRaw)

@PublicApi
fun ShortArrayMatrix.applySdf(sdf: ByteArrayMatrix, output: ShortArrayMatrix = this, apply: SdfApplyFun<Short>) = applySdf(sdf, this, output, apply, byteToFloat, floatToShortRaw)

@PublicApi
fun ShortArrayMatrix.applySdf(sdf: ShortArrayMatrix, output: ShortArrayMatrix = this, apply: SdfApplyFun<Short>) = applySdf(sdf, this, output, apply, shortToFloat, floatToShortRaw)

@PublicApi
fun ShortArrayMatrix.applySdf(sdf: FloatArrayMatrix, output: ShortArrayMatrix = this, apply: SdfApplyFun<Short>) = applySdf(sdf, this, output, apply, floatToFloat, floatToShortRaw)

@PublicApi
fun FloatArrayMatrix.applySdf(sdf: ByteArrayMatrix, output: FloatArrayMatrix = this, apply: SdfApplyFun<Float>) = applySdf(sdf, this, output, apply, byteToFloat, floatToFloat)

@PublicApi
fun FloatArrayMatrix.applySdf(sdf: ShortArrayMatrix, output: FloatArrayMatrix = this, apply: SdfApplyFun<Float>) = applySdf(sdf, this, output, apply, shortToFloat, floatToFloat)

@PublicApi
fun FloatArrayMatrix.applySdf(sdf: FloatArrayMatrix, output: FloatArrayMatrix = this, apply: SdfApplyFun<Float>) = applySdf(sdf, this, output, apply, floatToFloat, floatToFloat)

private inline fun <F, S : Matrix<F>, T, M : Matrix<T>> applySdf(sdf: S, input: M, output: M, crossinline apply: SdfApplyFun<T>, crossinline sdfToFloat: (F) -> Float, crossinline fromFloat: (Float) -> T): M {
    (0 until sdf.height).inParallel { y ->
        taskYield()
        val yOff = y * sdf.width
        for (x in 0 until sdf.width) {
            val i = yOff + x
            val d = ((sdfToFloat(sdf[i]) - 0.5f) * 2.0f).coerceIn(-1.0f, 1.0f)
            output[i] = fromFloat(apply(d, input[i]))
        }
    }
    return output
}
