package wk.api

import kotlin.math.*

private const val SCAN_COUNT = 16
private const val SCAN_FACTOR = 1.0f / SCAN_COUNT
private const val TWO_OVER_PI = (2.0 / PI).toFloat()

private val scanDeltas = intArrayOf(
        1, 0,
        0, 1,
        -1, 0,
        0, -1,
        1, 1,
        -1, -1,
        1, -1,
        -1, 1,
        2, 1,
        2, -1,
        -2, 1,
        -2, -1,
        1, 2,
        1, -2,
        -1, 2,
        -1, -2
)

@PublicApi
fun Matrix<Float>.toOcclusion(occlusionScale: Float = 1.0f) = computeOcclusion(this, occlusionScale)

@PublicApi
fun Matrix<Float>.toOcclusion(mapScale: MapScale) = computeOcclusion(this, mapScale.mapSizeMeters / width)

private fun computeOcclusion(heightMap: Matrix<Float>, occlusionScale: Float): ByteArrayMatrix {
    val result = FloatArrayMatrix(heightMap.width, heightMap.height)
    val startPoints = IntArray(6 * max(heightMap.width, heightMap.height))
    for (i in 0 until SCAN_COUNT * 2 step 2) {
        taskYield()
        val dx = scanDeltas[i]
        val dy = scanDeltas[i + 1]
        horizonScan(heightMap, result, startPoints, dx, dy, occlusionScale)
    }
    val ret = ByteArrayMatrix(heightMap.width, heightMap.height)
    taskYield()
    (0 until result.size).inParallel {
        ret[it] = ((1.0f - result[it] * SCAN_FACTOR) * 255.0f).roundToInt().coerceIn(0, 255).toByte()
    }
    return ret
}

private fun horizonScan(heightmap: Matrix<Float>, result: FloatArrayMatrix, startPoints: IntArray, dx: Int, dy: Int, occlusionScale: Float) {
    val width = heightmap.width
    val height = heightmap.height
    val (sweepCount, pathLength) = initSweep(startPoints, dx, dy, width, height)
    val hullBuffer = FloatArray(pathLength * sweepCount)
    (0 until sweepCount).inParallel { sweep ->
        taskYield()
        val hullPtr = sweep * pathLength
        val sweep2 = sweep * 2
        var i = startPoints[sweep2]
        var j = startPoints[sweep2 + 1]
        var px = i * occlusionScale
        var py = j * occlusionScale
        var pz = heightmap[i.coerceIn(0, width - 1), j.coerceIn(0, height - 1)]
        var stackPtr = hullPtr
        hullBuffer[stackPtr] = px
        hullBuffer[stackPtr + 1] = py
        hullBuffer[stackPtr + 2] = pz
        i += dx
        j += dy
        while (i in 0 until width && j in 0 until height) {
            px = i * occlusionScale
            py = j * occlusionScale
            pz = heightmap[i, j]
            while (stackPtr > hullPtr) {
                val s1 = computeSlope(px, py, pz, hullBuffer[stackPtr], hullBuffer[stackPtr + 1], hullBuffer[stackPtr + 2])
                val s2 = computeSlope(px, py, pz, hullBuffer[stackPtr - 3], hullBuffer[stackPtr - 2], hullBuffer[stackPtr - 1])
                if (s1 >= s2) {
                    break
                }
                stackPtr -= 3
            }
            val hx = hullBuffer[stackPtr]
            val hy = hullBuffer[stackPtr + 1]
            val hz = hullBuffer[stackPtr + 2]
            stackPtr += 3
            hullBuffer[stackPtr] = px
            hullBuffer[stackPtr + 1] = py
            hullBuffer[stackPtr + 2] = pz
            val occlusion = computeOcclusion(px, py, pz, hx, hy, hz)
            result[i, j] += occlusion
            i += dx
            j += dy
        }
    }
}

private fun initSweep(startPoints: IntArray, dx: Int, dy: Int, width: Int, height: Int): Pair<Int, Int> {
    val sx = dx.sign
    val sy = dy.sign
    val ax = abs(dx)
    val ay = abs(dy)
    val sweepCount = ay * width + ax * height - (ax + ay - 1)
    var p = 0
    for (x in -ax until width - ax) {
        for (y in -ay until height - ay) {
            if (x in 0 until width && y in 0 until height) {
                continue
            }
            startPoints[p++] = if (sx < 0) width - x - 1 else x
            startPoints[p++] = if (sy < 0) height - y - 1 else y
        }
    }
    var pathLength = 0
    var i = startPoints[0]
    var j = startPoints[1]
    do {
        i += dx
        j += dy
        pathLength++
    } while (i in 0 until width && j in 0 until height)
    return sweepCount to pathLength * 3
}

private fun computeSlope(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return (bz - az) / sqrt(dx * dx + dy * dy)
}

private fun computeOcclusion(px: Float, py: Float, pz: Float, hx: Float, hy: Float, hz: Float): Float {
    val dx = hx - px
    val dy = hy - py
    val dz = hz - pz
    val s = dz / sqrt(dx * dx + dy * dy + dz * dz)
    return if (s <= 0.0f) 0.0f else atan(s) * TWO_OVER_PI
}
