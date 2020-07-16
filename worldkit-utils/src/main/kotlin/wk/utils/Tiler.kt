package wk.utils

import wk.api.taskYield
import java.awt.image.*
import java.io.File
import javax.imageio.ImageIO

object Tiler {

    @JvmStatic
    fun main(vararg args: String) {
        val isHeightMapMode = args[0].toLowerCase() == "HeightMap"
        if (isHeightMapMode) {
            tileHeightMap(*args)
        } else {
            tileMasks(*args)
        }
    }

    private fun tileMasks(vararg args: String) {
        val inputDir = File(args[1])
        val inputName = args[2]
        val outputDir = File(args[3])
        val inputWidth = args[4].toInt()
        val tileWidth = args[5].toInt()
        tileMasks(inputDir, inputName, outputDir, inputWidth, tileWidth)
    }

    fun tileMasks(inputDir: File, inputName: String, outputDir: File, inputWidth: Int, tileWidth: Int) {
        val weightBuffer = ByteArray(inputWidth * inputWidth)
        inputDir.listFiles { path ->
            path.name.endsWith(".png")
                    && (path.name.substringBefore('-').toIntOrNull() != null
                    || path.name.substringBefore('-').equals("a", ignoreCase = true))
                    && path.name.substringAfter('-').substringBefore('-').equals(inputName, ignoreCase = true)
        }!!.sortedByDescending {
            val order = it.name.substringBefore('-')
            if (order == "a") -1 else order.toInt()
        }.forEach { tileMask(it, outputDir, tileWidth, inputWidth, weightBuffer) }
        weightBuffer.forEach {
            if (it.toInt() and 0xFF != 255) throw RuntimeException("Weight doesn't add up to 255: $it")
        }
    }

    private fun tileHeightMap(vararg args: String) {
        val inputFile = File(args[1])
        val outputDir = File(args[2])
        val name = args[3]
        val tileWidth = args[4].toInt()
        tileHeightMap(inputFile, outputDir, name, tileWidth)
    }

    fun tileHeightMap(inputFile: File, outputDir: File, outputName: String, tileWidth: Int) {
        val tileWidthM1 = tileWidth - 1
        val bufferedImage = ImageIO.read(inputFile)
        val width = bufferedImage.width
        val tiles = width / tileWidthM1
        if (tiles * tileWidthM1 + 1 != width) {
            throw RuntimeException("Invalid image size: $width for tile width: $tileWidth")
        }
        val sourceData = bufferedImage.raster
        for (tileX in 0 until tiles) {
            val tileXOff = tileX * tileWidthM1
            for (tileY in 0 until tiles) {
                val tileYOff = tileY * tileWidthM1
                val output = BufferedImage(tileWidth, tileWidth, BufferedImage.TYPE_USHORT_GRAY)
                val outputData = output.raster
                taskYield()
                for (y in 0 until tileWidth) {
                    for (x in 0 until tileWidth) {
                        outputData.setSample(x, y, 0, sourceData.getSample(tileXOff + x, tileYOff + y, 0))
                    }
                }
                val outputFileName = "${outputName}_x${tileX}_y$tileY.png"
                taskYield()
                ImageIO.write(output, "png", File(outputDir, outputFileName))
                taskYield()
            }
            taskYield()
        }
    }

    private fun tileMask(inputFile: File, baseOutputDir: File, tileWidth: Int, inputWidth: Int, weightBuffer: ByteArray) {
        val inputWidthM1 = inputWidth - 1
        val name = inputFile.name.substringBeforeLast('.')
        val isAlpha = name.startsWith("a-")
        val maskOutputDir = File(baseOutputDir, name)
        maskOutputDir.mkdirs()
        val tileWidthM1 = tileWidth - 1
        val bufferedImage = ImageIO.read(inputFile)
        val width = bufferedImage.width
        val tiles = width / tileWidthM1
        if (tiles * tileWidthM1 + 1 != width) {
            throw RuntimeException("Invalid image size: $width for tile width: $tileWidth")
        }
        val sourceData = bufferedImage.raster
        for (tileX in 0 until tiles) {
            val tileXOff = tileX * tileWidthM1
            for (tileY in 0 until tiles) {
                val tileYOff = tileY * tileWidthM1
                val output = BufferedImage(tileWidth, tileWidth, BufferedImage.TYPE_BYTE_GRAY)
                val outputData = output.raster
                for (y in 0 until tileWidth) {
                    val yOff = tileYOff + y
                    for (x in 0 until tileWidth) {
                        val xOff = tileXOff + x
                        var sample = sourceData.getSample(xOff, yOff, 0) and 0xFF
                        if (isAlpha) {
                            outputData.setSample(x, y, 0, sample)
                        } else {
                            val index = yOff * inputWidth + xOff
                            val currentWeight = weightBuffer[index].toInt() and 0xFF
                            val desiredWeight = sample + currentWeight
                            if (desiredWeight > 255) {
                                sample = 255 - currentWeight
                            }
                            if ((x != tileWidthM1 && y != tileWidthM1) || (xOff == inputWidthM1 && y != tileWidthM1) || (yOff == inputWidthM1 && x != tileWidthM1) || (xOff == inputWidthM1 && yOff == inputWidthM1)) {
                                val newWeight = currentWeight + sample
                                weightBuffer[index] = newWeight.toByte()
                            }
                            outputData.setSample(x, y, 0, sample)
                        }
                    }
                }
                ImageIO.write(output, "png", File(maskOutputDir, "${name}_x${tileX}_y$tileY.png"))
            }
        }
    }
}