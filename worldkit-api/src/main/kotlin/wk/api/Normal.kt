package wk.api

import wk.internal.ext.fastFloorI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@PublicApi
fun Matrix<Float>.toNormalMap(normalScale: Float) = computeNormal(this, normalScale)

@PublicApi
fun Matrix<Float>.toNormalMap(mapScale: MapScale) = computeNormal(this, mapScale.mapSizeMeters / width)

private fun computeNormal(heightMap: Matrix<Float>, normalScale: Float): IntArrayMatrix {
    val positionLeft = 0.0f
    val positionCenter: Float = normalScale
    val positionRight: Float = normalScale * 2.0f

    val width = heightMap.width
    val height = heightMap.height

    val widthIndices = 0 until width
    val heightIndices = 0 until height
    val output = IntArrayMatrix(width, height)

    (0 until height).inParallel(
            context = { vec3() }
    ) { sum, y ->
        taskYield()
        val yOff = y * width
        for (x in 0 until width) {
            val xm1 = (x - 1).coerceIn(widthIndices)
            val xp1 = (x + 1).coerceIn(widthIndices)
            val ym1 = (y - 1).coerceIn(heightIndices)
            val yp1 = (y + 1).coerceIn(heightIndices)
            val center = heightMap[yOff + x]
            val westPixel = heightMap[xm1, y]
            val eastPixel = heightMap[xp1, y]
            val northPixel = heightMap[x, ym1]
            val southPixel = heightMap[x, yp1]
            val northWestPixel = heightMap[xm1, ym1]
            val southEastPixel = heightMap[xp1, yp1]
            val northEastPixel = heightMap[xp1, ym1]
            val southWestPixel = heightMap[xm1, yp1]
            sum.set(0.0f, 0.0f, 0.0f)
            accumulateNormal(positionCenter, positionCenter, center, positionLeft, positionRight, northWestPixel, positionLeft, positionCenter, westPixel, sum)
            accumulateNormal(positionCenter, positionCenter, center, positionLeft, positionCenter, westPixel, positionLeft, positionLeft, southWestPixel, sum)
            accumulateNormal(positionCenter, positionCenter, center, positionLeft, positionLeft, southWestPixel, positionCenter, positionLeft, southPixel, sum)
            accumulateNormal(positionCenter, positionCenter, center, positionCenter, positionLeft, southPixel, positionRight, positionLeft, southEastPixel, sum)
            accumulateNormal(positionCenter, positionCenter, center, positionRight, positionLeft, southEastPixel, positionRight, positionCenter, eastPixel, sum)
            accumulateNormal(positionCenter, positionCenter, center, positionRight, positionCenter, eastPixel, positionRight, positionRight, northEastPixel, sum)
            accumulateNormal(positionCenter, positionCenter, center, positionRight, positionRight, northEastPixel, positionCenter, positionRight, northPixel, sum)
            accumulateNormal(positionCenter, positionCenter, center, positionCenter, positionRight, northPixel, positionLeft, positionRight, northWestPixel, sum)
            var (r, g, b) = sum
            val l = (1.0f / sqrt(r * r + g * g + b * b)) * 0.5f
            r = r * l + 0.5f
            g = g * l + 0.5f
            b = b * l + 0.5f
            output[yOff + x] = ((r * 255).roundToInt().coerceIn(0, 255) shl 16) or ((g * 255).roundToInt().coerceIn(0, 255) shl 8) or (b * 255).roundToInt().coerceIn(0, 255)
        }
    }
    return output
}

private fun accumulateNormal(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, sum: Vec3) {
    val ux = bx - ax
    val uy = by - ay
    val uz = bz - az
    val vx = cx - ax
    val vy = cy - ay
    val vz = cz - az
    sum.x += uy * vz - uz * vy
    sum.y += uz * vx - ux * vz
    sum.z += ux * vy - uy * vx
}

fun FloatArrayMatrix.getHeightAndDownSlopeVector(u: Float, v: Float, normalScale: Float, outVector: Vec3): Float {
    val posLow = 0.0f
    val posMid: Float = normalScale
    val posHigh: Float = normalScale * 2.0f

    val widthM1 = width - 1
    val heightM1 = height - 1
    val x = (u * widthM1).coerceIn(0.0f, widthM1.toFloat())
    val y = (v * heightM1).coerceIn(0.0f, heightM1.toFloat())
    val x1 = fastFloorI(x)
    val x0 = max(x1 - 1, 0)
    val x2 = min(x1 + 1, widthM1)
    val x3 = min(x1 + 2, widthM1)
    val y1 = fastFloorI(y)
    val y0 = max(y1 - 1, 0)
    val y2 = min(y1 + 1, heightM1)
    val y3 = min(y1 + 2, heightM1)

    val yOff0 = y0 * width
    val yOff1 = y1 * width
    val yOff2 = y2 * width
    val yOff3 = y3 * width
    val h10 = array[yOff0 + x1]
    val h20 = array[yOff0 + x2]
    val h01 = array[yOff1 + x0]
    val h11 = array[yOff1 + x1]
    val h21 = array[yOff1 + x2]
    val h31 = array[yOff1 + x3]
    val h02 = array[yOff2 + x0]
    val h12 = array[yOff2 + x1]
    val h22 = array[yOff2 + x2]
    val h32 = array[yOff2 + x3]
    val h13 = array[yOff3 + x1]
    val h23 = array[yOff3 + x2]

    outVector.set(0.0f, 0.0f, 0.0f)
    accumulateNormal(posMid, posMid, h11, posMid, posHigh, h10, posLow, posMid, h01, outVector)
    accumulateNormal(posMid, posMid, h11, posLow, posMid, h01, posMid, posLow, h12, outVector)
    accumulateNormal(posMid, posMid, h11, posMid, posLow, h12, posHigh, posMid, h21, outVector)
    accumulateNormal(posMid, posMid, h11, posHigh, posMid, h21, posMid, posHigh, h10, outVector)
    var (n0x, n0y, n0z) = outVector
    val l0 = 1.0f / sqrt(n0x * n0x + n0y * n0y + n0z * n0z)
    n0x *= l0
    n0y *= l0
    n0z *= l0

    outVector.set(0.0f, 0.0f, 0.0f)
    accumulateNormal(posMid, posMid, h21, posMid, posHigh, h20, posLow, posMid, h11, outVector)
    accumulateNormal(posMid, posMid, h21, posLow, posMid, h11, posMid, posLow, h22, outVector)
    accumulateNormal(posMid, posMid, h21, posMid, posLow, h22, posHigh, posMid, h31, outVector)
    accumulateNormal(posMid, posMid, h21, posHigh, posMid, h31, posMid, posHigh, h20, outVector)
    var (n1x, n1y, n1z) = outVector
    val l1 = 1.0f / sqrt(n1x * n1x + n1y * n1y + n1z * n1z)
    n1x *= l1
    n1y *= l1
    n1z *= l1

    outVector.set(0.0f, 0.0f, 0.0f)
    accumulateNormal(posMid, posMid, h12, posMid, posHigh, h11, posLow, posMid, h02, outVector)
    accumulateNormal(posMid, posMid, h12, posLow, posMid, h02, posMid, posLow, h13, outVector)
    accumulateNormal(posMid, posMid, h12, posMid, posLow, h13, posHigh, posMid, h22, outVector)
    accumulateNormal(posMid, posMid, h12, posHigh, posMid, h22, posMid, posHigh, h11, outVector)
    var (n2x, n2y, n2z) = outVector
    val l2 = 1.0f / sqrt(n2x * n2x + n2y * n2y + n2z * n2z)
    n2x *= l2
    n2y *= l2
    n2z *= l2

    outVector.set(0.0f, 0.0f, 0.0f)
    accumulateNormal(posMid, posMid, h22, posMid, posHigh, h21, posLow, posMid, h12, outVector)
    accumulateNormal(posMid, posMid, h22, posLow, posMid, h12, posMid, posLow, h23, outVector)
    accumulateNormal(posMid, posMid, h22, posMid, posLow, h23, posHigh, posMid, h32, outVector)
    accumulateNormal(posMid, posMid, h22, posHigh, posMid, h32, posMid, posHigh, h21, outVector)
    var (n3x, n3y, n3z) = outVector
    val l3 = 1.0f / sqrt(n3x * n3x + n3y * n3y + n3z * n3z)
    n3x *= l3
    n3y *= l3
    n3z *= l3

    val lerpX = x - x1
    val iLerpX = 1.0f - lerpX
    val lerpY = y - y1
    val iLerpY = 1.0f - lerpY
    var nx = (n0x * iLerpX + n1x * lerpX) * iLerpY + (n2x * iLerpX + n3x * lerpX) * lerpY
    var ny = (n0y * iLerpX + n1y * lerpX) * iLerpY + (n2y * iLerpX + n3y * lerpX) * lerpY
    var nz = (n0z * iLerpX + n1z * lerpX) * iLerpY + (n2z * iLerpX + n3z * lerpX) * lerpY
    val l = 1.0f / sqrt(nx * nx + ny * ny + nz * nz)
    nx *= l
    ny *= -l
    nz *= l

    outVector.set(nx * nz, ny * nz, -(nx * nx) - (ny * ny))

    return (h11 * iLerpX + h21 * lerpX) * iLerpY + (h12 * iLerpX + h22 * lerpX) * lerpY
}