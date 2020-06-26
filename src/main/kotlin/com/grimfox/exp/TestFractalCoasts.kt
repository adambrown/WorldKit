package com.grimfox.exp

import com.grimfox.joml.Math.*
import com.grimfox.joml.SimplexNoise.noise
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.roundToInt

const val ON = 255.toByte()
const val OFF = 0.toByte()

object TestFractalCoasts {

    @JvmStatic
    fun main(args: Array<String>) {
//        main1(args)
        main2(args)
    }

    @JvmStatic
    fun main1(args: Array<String>) {
        for (width in intArrayOf(1024)) {
            (0L..1000L).toList().parallelStream().forEach { seed ->
                val random = Random(seed)
                val widthInverse = 1.0f / width
                val halfWidth = width / 2.0
                val radius = width * 0.1
                val cornerBias = 0.85
                val angleOffsets = getOffsets(random.nextLong())
                val positionalOffsets = getOffsets(random.nextLong())
                val adjustmentVariance = radius * 4
                val positionalMultiplier = 4.0f
                val radialMultiplier = 0.2
                val result = BufferedImage(width, width, BufferedImage.TYPE_BYTE_GRAY)
                val buffer = (result.raster.dataBuffer as DataBufferByte).data
                (0 until width).toList().parallelStream().forEach { y ->
                    val yOff = y * width
                    for (x in 0 until width) {
                        val dy = y - halfWidth + 0.5
                        val dx = x - halfWidth + 0.5
                        val dist = sqrt(dy * dy + dx * dx)
                        val uy = dy / dist
                        val ux = dx / dist
                        val maxLeg = max(abs(dx), abs(dy))
                        val legRatio = dist / maxLeg
                        val adjustedRadius = radius * (((legRatio - 1.0) * cornerBias) + 1.0)
                        val positionalAdjustment = noiseMulti(x * widthInverse * positionalMultiplier, y * widthInverse * positionalMultiplier, positionalOffsets)
                        val radiusAtAngle = adjustedRadius * (noiseMulti((ux * radialMultiplier).toFloat(), (uy * radialMultiplier).toFloat(), angleOffsets) + 1.0f) + positionalAdjustment * adjustmentVariance * legRatio
                        buffer[yOff + x] = if (dist < radiusAtAngle) OFF else ON
                    }
                }
                ImageIO.write(result, "png", File("t.$seed.$width.${(adjustmentVariance).roundToInt()}.${(positionalMultiplier).roundToInt()}.${(radialMultiplier * 10).roundToInt()}.png"))
            }
        }
    }


    @JvmStatic
    fun main2(args: Array<String>) {
        for (width in intArrayOf(1024)) {
            (0L..1000L).toList().parallelStream().forEach { seed ->
                val random = Random(seed)
                do {
                    val widthInverse = 1.0f / width
                    val halfWidth = width / 2.0
                    val radius = width * 0.26
                    val cornerBias = 0.9
                    val angleOffsets = getOffsets(random.nextLong())
                    val positionalOffsets = getOffsets(random.nextLong())
                    val adjustmentVariance = radius * 1.6
                    val positionalMultiplier = 3.0f
                    val radialMultiplier = 0.2
                    val result = BufferedImage(width, width, BufferedImage.TYPE_BYTE_GRAY)
                    val buffer = (result.raster.dataBuffer as DataBufferByte).data
                    (0 until width).toList().parallelStream().forEach { y ->
                        val yOff = y * width
                        for (x in 0 until width) {
                            val dy = y - halfWidth + 0.5
                            val dx = x - halfWidth + 0.5
                            val dist = sqrt(dy * dy + dx * dx)
                            val uy = dy / dist
                            val ux = dx / dist
                            val maxLeg = max(abs(dx), abs(dy))
                            val legRatio = dist / maxLeg
                            val adjustedRadius = radius * (((legRatio - 1.0) * cornerBias) + 1.0)
                            val positionalAdjustment = noiseMulti(x * widthInverse * positionalMultiplier, y * widthInverse * positionalMultiplier, positionalOffsets)
                            val radiusAtAngle = adjustedRadius * (noiseMulti((ux * radialMultiplier).toFloat(), (uy * radialMultiplier).toFloat(), angleOffsets) + 1.0f) + positionalAdjustment * adjustmentVariance * legRatio
                            buffer[yOff + x] = if (dist < radiusAtAngle) ON else OFF
                        }
                    }
                    if (floodFillOutside(buffer, width)) {
                        ImageIO.write(result, "png", File("t.$seed.png"))
//                        ImageIO.write(result, "png", File("t.$seed.$width.${(adjustmentVariance).roundToInt()}.${(positionalMultiplier).roundToInt()}.${(radialMultiplier * 10).roundToInt()}.png"))
                        break
                    }
                } while (true)
            }
        }
    }

    private fun noiseMulti(x: Float, y: Float, offsets: FloatArray): Float {
        return noise(x + offsets[0], y + offsets[1]) * 0.5f +
                noise(x * 2 + offsets[2], y * 2 + offsets[3]) * 0.25f +
                noise(x * 4 + offsets[4], y * 4 + offsets[5]) * 0.125f +
                noise(x * 8 + offsets[6], y * 8 + offsets[7]) * 0.0625f +
                noise(x * 16 + offsets[8], y * 16 + offsets[9]) * 0.03125f +
                noise(x * 32 + offsets[10], y * 32 + offsets[11]) * 0.015625f
    }

    private fun getOffsets(seed: Long): FloatArray {
        val random = Random(seed)
        return FloatArray(16) {
            random.nextFloat() * 100
        }
    }

    private fun floodFillOutside(buffer: ByteArray, width: Int): Boolean {
        (0 until width).forEach { y ->
            val yOff = y * width
            for (x in listOf(0, width - 1)) {
                if (buffer[yOff + x] == ON) {
                    return false
                }
            }
        }
        listOf(0, width - 1).forEach { y ->
            val yOff = y * width
            for (x in 0 until width) {
                if (buffer[yOff + x] == ON) {
                    return false
                }
            }
        }
        floodFill(buffer, width, 0, OFF, 10.toByte(), ON, 20.toByte(), 30)
        (0 until width).toList().parallelStream().forEach { y ->
            val yOff = y * width
            for (x in 0 until width) {
                when (buffer[yOff + x]) {
                    10.toByte() -> {
                        buffer[yOff + x] = OFF
                    }
                    else -> {
                        buffer[yOff + x] = ON
                    }
                }
            }
        }
        return true
    }

    private fun floodFill(buffer: ByteArray, width: Int, seed: Int, oceanSearch: Byte, oceanFill: Byte, islandSearch: Byte, islandFill: Byte, minIsland: Int) {
        var frontier1 = ArrayList<Int>()
        var frontier2 = ArrayList<Int>()
        frontier1.add(seed)
        buffer[seed] = oceanFill
        do {
            frontier1.forEach { i ->
                getAdjacent(width, i).forEach { a ->
                    if (buffer[a] == oceanSearch) {
                        buffer[a] = oceanFill
                        frontier2.add(a)
                    } else if (buffer[a] == islandSearch) {
                        val islandIndices = floodFill(buffer, width, a, islandSearch, islandFill)
                        if (islandIndices.size < minIsland) {
                            islandIndices.forEach { j ->
                                buffer[j] = oceanFill
                            }
                        }
                    }
                }
            }
            val temp = frontier1
            temp.clear()
            frontier1 = frontier2
            frontier2 = temp
        } while (frontier1.isNotEmpty())
    }

    private fun floodFill(buffer: ByteArray, width: Int, seed: Int, islandSearch: Byte, islandFill: Byte): ArrayList<Int> {
        var frontier1 = ArrayList<Int>()
        var frontier2 = ArrayList<Int>()
        val wholeSet = ArrayList<Int>()
        frontier1.add(seed)
        buffer[seed] = islandFill
        do {
            wholeSet.addAll(frontier1)
            frontier1.forEach { i ->
                getAdjacent(width, i).forEach { a ->
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
        return wholeSet
    }

    private fun getAdjacent(width: Int, i: Int): List<Int> {
        val x = i % width
        val y = i / width
        val adjacent = ArrayList<Int>(9)

//        for (oy in ((y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(width - 1))) {
//            val yOff = oy * width
//            for (ox in ((x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(width - 1))) {
//                if (ox == x && oy == y) {
//                    continue
//                }
//                adjacent.add(yOff + ox)
//            }
//        }
        for (oy in ((y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(width - 1))) {
            if (oy == y) {
                continue
            }
            adjacent.add(oy * width + x)
        }
        for (ox in ((x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(width - 1))) {
            if (ox == x) {
                continue
            }
            adjacent.add(y * width + ox)
        }
        return adjacent
    }
}