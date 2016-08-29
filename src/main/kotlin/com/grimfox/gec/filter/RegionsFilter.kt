package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.generator.Point
import com.grimfox.gec.model.BitMatrix
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.model.Uint24Matrix
import com.grimfox.gec.util.Utils
import com.grimfox.gec.util.Utils.findClosestPoint
import com.grimfox.gec.util.Utils.pow
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File

@Command(name = "regions", description = "Create points mask based on raster mask.")
class RegionsFilter : Runnable {

    @Option(name = arrayOf("-p", "--points-file"), description = "The points file to read as input.", required = true)
    var pointsFile: File = File(Main.workingDir, "points.bin")

    @Option(name = arrayOf("-m", "--mask-file"), description = "The mask file to read as input.", required = true)
    var maskFile: File = File(Main.workingDir, "mask.bin")

    @Option(name = arrayOf("-r", "--region-points"), description = "The points file to read as region input.", required = true)
    var regionPointsFile: File = File(Main.workingDir, "points.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    override fun run() {
        DataFiles.openAndUse<Point>(pointsFile) { points ->
            DataFiles.openAndUse<Int>(maskFile) { mask ->
                DataFiles.openAndUse<Point>(regionPointsFile) { regionPoints ->
                    DataFiles.createAndUse<Uint24Matrix>(outputFile, points.exponent) { regionIdMask ->
                        val outputWidth = regionIdMask.width
                        val gridStride = regionPoints.width
                        val gridSquareSize = outputWidth / gridStride
                        val pointWrapOffset = outputWidth.toFloat()
                        for (y in 0..points.width - 1) {
                            for (x in 0..points.width - 1) {
                                val point = points[x, y]
                                if (mask[x, y] > 0) {
                                    val gridX = x / gridSquareSize
                                    val gridY = y / gridSquareSize
                                    val closestPoint = findClosestPoint(regionPoints, gridX, gridY, gridStride, pointWrapOffset, Point(point.x * outputWidth, point.y * outputWidth), outputWidth, false)
                                    regionIdMask[x, y] = closestPoint + 1
                                } else {
                                    regionIdMask[x, y] = 0
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
