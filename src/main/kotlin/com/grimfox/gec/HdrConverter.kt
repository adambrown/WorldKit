package com.grimfox.gec

import com.grimfox.gec.hdr.HDREncoder
import com.grimfox.gec.util.clamp
import java.awt.image.BufferedImage
import java.io.*
import java.util.zip.GZIPOutputStream
import javax.imageio.ImageIO
import kotlin.math.round

object HdrConverter {

    @JvmStatic
    fun main(vararg args: String) {
        args.forEach { filePath ->
            val fileBase = File(filePath)
            val hdr = HDREncoder.readHDR(fileBase, true)
            val fileParent = fileBase.parentFile
            val fileName = fileBase.nameWithoutExtension
            val outFile = File(fileParent, "$fileName.tex")
            val output = DataOutputStream(GZIPOutputStream(outFile.outputStream().buffered()).buffered())
            output.use { stream ->
                val pngOut = BufferedImage(hdr.width, hdr.height, BufferedImage.TYPE_3BYTE_BGR)
                val pngData = pngOut.raster

                stream.writeInt(1)
                stream.writeInt(hdr.width)
                for (y in 0 until hdr.width) {
                    for (x in 0 until hdr.width) {
                        stream.writeFloat(hdr.getPixelValue(x, y, 0))
                        stream.writeFloat(hdr.getPixelValue(x, y, 1))
                        stream.writeFloat(hdr.getPixelValue(x, y, 2))
                        pngData.setSample(x, y, 0, linearFloatToGammaByte(hdr.getPixelValue(x, y, 0)))
                        pngData.setSample(x, y, 1, linearFloatToGammaByte(hdr.getPixelValue(x, y, 1)))
                        pngData.setSample(x, y, 2, linearFloatToGammaByte(hdr.getPixelValue(x, y, 2)))
                    }
                }
                ImageIO.write(pngOut, "png", File(fileParent, "$fileName.png"))
            }
        }
    }

    private fun linearFloatToGammaByte(value: Float) = clamp(round(Math.pow(value.toDouble(), EnvMapCalculator.gamma) * 255.0).toInt(), 0, 255)
}