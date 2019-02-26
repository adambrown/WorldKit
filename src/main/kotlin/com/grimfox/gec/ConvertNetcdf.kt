package com.grimfox.gec

import com.grimfox.gec.model.RcMatrix
import com.grimfox.gec.util.clamp
import ucar.netcdf.NetcdfFile
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ConvertNetcdf {

    @JvmStatic fun main(vararg args: String) {
        val inputFile = File(args[0])
        val netcdfFile = NetcdfFile(inputFile, true)
        val i = netcdfFile.iterator()
        var lat = -1
        var lon = -1
        netcdfFile.dimensions.toArray().forEach {
            if (it.name.equals("lat", true)) {
                lat = it.length
            }
            if (it.name.equals("lon", true)) {
                lon = it.length
            }
        }
        if (lat > 0 && lon > 0) {
            while (i.hasNext()) {
                val v = i.next()
                if (v.name.equals("band1", true) || v.name.equals("z", true)) {
                    val data = v.toArray() as FloatArray
                    val rank = v.rank
                    if (rank == 2 && data.size == lat * lon) {
                        println("lat / lon good")
                    }
                    val imageMatrix = RcMatrix(lat, lon)
                    data.forEachIndexed { i, f ->
                        imageMatrix[i] = f
                    }
                    val inputName = inputFile.nameWithoutExtension
                    writeNormalizedTestImage(imageMatrix, inputFile.parentFile, "$inputName.png")
                }
            }
        } else {
            println("no lat / lon")
        }
    }

    private fun writeNormalizedTestImage(matrixToWrite: RcMatrix, outputDir: File, fileName: String) {
        val output = BufferedImage(matrixToWrite.columns, matrixToWrite.rows, BufferedImage.TYPE_USHORT_GRAY)
        val outputData = output.raster
        val min = matrixToWrite.min()
        val max = matrixToWrite.max()
        val scale = (1.0f / (max - min)) * 65535.0f
        for (row in 0 until matrixToWrite.rows) {
            for (column in 0 until matrixToWrite.columns) {
                outputData.setSample(column, row, 0, clamp(Math.round((matrixToWrite[row, column] - min) * scale), 0, 65535) and 0xFFFF)
            }
        }
        println("normalization values for: $fileName = min: ${min.format(4)}, max: ${max.format(4)}, scale: ${scale.format(4)}")
        ImageIO.write(output, "png", File(outputDir, fileName))
    }

    private fun Float.format(digits: Int) = java.lang.String.format("%.${digits}f", this)!!
}