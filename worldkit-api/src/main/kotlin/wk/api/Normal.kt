package wk.api

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
