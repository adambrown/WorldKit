package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.BitMatrix
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.util.Utils.findClosestPoint
import com.grimfox.gec.util.Utils.pow
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File

@Command(name = "point-mask", description = "Create a matrix masked by values assigned to each point.")
class PointMaskFilter : Runnable {

    @Option(name = arrayOf("-p", "--points-file"), description = "The points file to read as input.", required = true)
    var pointsFile: File = File(Main.workingDir, "points.bin")

    @Option(name = arrayOf("-m", "--mask-file"), description = "The mask file to read as input.", required = true)
    var maskFile: File = File(Main.workingDir, "mask.bin")

    @Option(name = arrayOf("-e", "--exponent"), description = "The size of the output as an exponent of 2. The output width and height will be equal to 2 to the power of size.", required = false)
    var exponent: Int? = null

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-w", "--wrap"), description = "Whether or not the points should wrap at the edges.", required = false)
    var wrapEdges: Boolean = false

    override fun run() {
        DataFiles.openAndUse<Point2F>(pointsFile) { points ->
            DataFiles.openAndUse<Int>(maskFile) { mask ->
                val outputExponent = exponent ?: points.exponent + 3
                val outputWidth = 2.pow(outputExponent)
                val gridStride = points.width
                val gridSquareSize = outputWidth / gridStride
                val pointWrapOffset = outputWidth.toFloat()
                DataFiles.createAndUse<BitMatrix>(outputFile, outputExponent) { heightMap ->
                    val end = heightMap.width - 1
                    for (y in 0..end) {
                        for (x in 0..end) {
                            val gridX = x / gridSquareSize
                            val gridY = y / gridSquareSize
                            val closestPoint = findClosestPoint(points, gridX, gridY, gridStride, pointWrapOffset, Point2F(x.toFloat(), y.toFloat()), outputWidth, wrapEdges)
                            heightMap[x, y] = mask[closestPoint]
                        }
                    }
                }
            }
        }
    }
}
