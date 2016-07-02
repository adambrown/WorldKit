package com.grimfox.gec.output

import com.grimfox.gec.model.RawMatrixData
import com.grimfox.gec.model.RawMatrixData.DataFormat
import com.grimfox.gec.model.RawMatrixData.Format
import com.grimfox.gec.util.Utils.primitiveToWrapper
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

interface PngGrayOutput : Runnable {

    val bufferedImageType: Int

    val rangeMax: Int

    var inputFile: File

    var outputFile: File

    var normalize: Boolean

    var minValue: Double

    var maxValue: Double

    override fun run() {
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