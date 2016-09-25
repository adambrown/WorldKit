package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.BitMatrix
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.model.Uint24Matrix
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File

@Command(name = "mask-point-ids", description = "Create points mask based on raster mask.")
class MaskPointIdsFilter : Runnable {

    @Option(name = arrayOf("-p", "--points-file"), description = "The points file to read as input.", required = true)
    var pointsFile: File = File(Main.workingDir, "points.bin")

    @Option(name = arrayOf("-m", "--mask-file"), description = "The mask file to read as input.", required = true)
    var maskFile: File = File(Main.workingDir, "mask.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    override fun run() {
        DataFiles.openAndUse<Point2F>(pointsFile) { points ->
            DataFiles.openAndUse<Int>(maskFile) { mask ->
                val rasterWidth = mask.width
                DataFiles.createAndUse<Uint24Matrix>(outputFile, points.exponent) { bitMatrix ->
                    for (y in 0..points.width - 1) {
                        for (x in 0..points.width - 1) {
                            val point = points[x, y]
                            val pointX = (point.x * rasterWidth).toInt()
                            val pointY = (point.y * rasterWidth).toInt()
                            bitMatrix[x, y] = mask[pointX, pointY]
                        }
                    }
                }
            }
        }
    }
}
