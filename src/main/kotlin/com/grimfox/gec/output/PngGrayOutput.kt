package com.grimfox.gec.output

import com.grimfox.gec.model.BitMask
import com.grimfox.gec.model.BitMatrix
import com.grimfox.gec.model.RawMatrixData
import com.grimfox.gec.util.Utils
import com.grimfox.gec.util.Utils.toRandomAccessFileMode
import java.awt.image.BufferedImage
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import javax.imageio.ImageIO

interface PngGrayOutput : Runnable {

    val bufferedImageType: Int

    val rangeMax: Int

    var binaryInput: Boolean

    var inputFile: File

    var outputFile: File

    var normalize: Boolean

    var minValue: Double

    var maxValue: Double

    override fun run() {
        if (binaryInput) {
            val range = maxValue - minValue
            val channel = RandomAccessFile(inputFile, FileChannel.MapMode.READ_ONLY.toRandomAccessFileMode()).channel
            val width = Math.round(Math.sqrt(channel.size() * 8.0)).toInt()
            val size = Utils.exp2FromSize(width)
            val bitMask = BitMatrix(channel, FileChannel.MapMode.READ_ONLY, size, 0)
            try {
                val output = BufferedImage(width, width, bufferedImageType)
                val raster = output.raster
                for (y in (0..width - 1)) {
                    for (x in (0..width - 1)) {
                        val value = bitMask[x, y].toDouble()
                        val pixel = Math.round(((Math.min(Math.max(minValue, value), maxValue) - minValue) / range) * rangeMax).toInt()
                        raster.setSample(x, y, 0, pixel)
                    }
                }
                ImageIO.write(output, "PNG", outputFile)
            } finally {
                bitMask.close()
            }
        } else {
            RawMatrixData.openAndUse<Number>(inputFile) { heightMap ->
                if (normalize) {
                    minValue = Double.MAX_VALUE
                    maxValue = Double.MIN_VALUE
                    for (y in (0..heightMap.height - 1)) {
                        for (x in (0..heightMap.width - 1)) {
                            val value = heightMap[x, y].toDouble()
                            minValue = Math.min(minValue, value)
                            maxValue = Math.max(maxValue, value)
                        }
                    }
                }
                val range = maxValue - minValue
                val output = BufferedImage(heightMap.width, heightMap.height, bufferedImageType)
                val raster = output.raster
                for (y in (0..heightMap.height - 1)) {
                    for (x in (0..heightMap.width - 1)) {
                        val value = heightMap[x, y].toDouble()
                        val pixel = Math.round(((Math.min(Math.max(minValue, value), maxValue) - minValue) / range) * rangeMax).toInt()
                        raster.setSample(x, y, 0, pixel)
                    }
                }
                ImageIO.write(output, "PNG", outputFile)
            }
        }
    }
}