package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.ClosestPointsMatrix
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.util.Utils
import com.grimfox.gec.util.Utils.pow
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File

@Command(name = "closest-points", description = "Create a matrix of the closest points to each pixel from points.")
class ClosestPointsFilter : Runnable {

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    var inputFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-e", "--exponent"), description = "The size of the output as an exponent of 2. The output width and height will be equal to 2 to the power of size.", required = false)
    var exponent: Int? = null

    @Option(name = arrayOf("-p", "--point-count"), description = "The number of closest points to output per fragment.", required = false)
    var pointCount: Int = 2

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-w", "--wrap"), description = "Whether or not the points should wrap at the edges.", required = false)
    var wrapEdges: Boolean = false

    override fun run() {
        DataFiles.openAndUse<Point2F>(inputFile) { points ->
            val outputExponent = exponent ?: points.exponent + 3
            val outputWidth = 2.pow(outputExponent)
            val gridStride = points.width
            val gridSquareSize = outputWidth / gridStride
            val pointWrapOffset = outputWidth.toFloat()
            val pointsPerFragment = Math.min(Math.max(pointCount, 2), 5)
            fun <M : ClosestPointsMatrix> execute(format: Class<M>) {
                DataFiles.createAndUse(outputFile, outputExponent, format) { heightMap ->
                    val end = heightMap.width - 1
                    for (y in 0..end) {
                        for (x in 0..end) {
                            val gridX = x / gridSquareSize
                            val gridY = y / gridSquareSize
                            val closestPoints = Utils.findClosestPoints(points, gridX, gridY, gridStride, pointWrapOffset, Point2F(x.toFloat(), y.toFloat()), outputWidth, wrapEdges)
                            heightMap[x, y] = closestPoints
                        }
                    }
                }
            }
            when (pointsPerFragment) {
                3 -> execute(ClosestPointsMatrix.M3::class.java)
                4 -> execute(ClosestPointsMatrix.M4::class.java)
                5 -> execute(ClosestPointsMatrix.M5::class.java)
                else -> execute(ClosestPointsMatrix.M2::class.java)
            }
        }
    }
}
