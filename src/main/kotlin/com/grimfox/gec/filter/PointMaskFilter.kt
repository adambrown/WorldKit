package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.generator.Point
import com.grimfox.gec.model.BitMatrix
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.util.Utils.pow
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File
import java.util.*

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
        DataFiles.openAndUse<Point>(pointsFile) { points ->
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
                            val closestPoint = getClosestPoint(points, gridX, gridY, gridStride, pointWrapOffset, Point(x.toFloat(), y.toFloat()), outputWidth)
                            heightMap[x, y] = mask[closestPoint % mask.width, closestPoint / mask.width]
                        }
                    }
                }
            }
        }
    }

    private fun getClosestPoint(points: Matrix<Point>, x: Int, y: Int, gridStride: Int, pointWrapOffset: Float, point: Point, outputWidth: Int): Int {
        val closestPoints = ArrayList<Pair<Int, Float>>(25)
        for (yOff in -1..1) {
            for (xOff in -1..1) {
                var ox = x + xOff
                var oy = y + yOff
                var xDistAdjust = 0.0f
                var yDistAdjust = 0.0f
                if (wrapEdges) {
                    val ox1 = ox
                    ox = (ox + gridStride) % gridStride
                    if (ox1 > ox) {
                        xDistAdjust = pointWrapOffset
                    } else if (ox1 < ox) {
                        xDistAdjust = -pointWrapOffset
                    }
                    val oy1 = oy
                    oy = (oy + gridStride) % gridStride
                    if (oy1 > oy) {
                        yDistAdjust = pointWrapOffset
                    } else if (oy1 < oy) {
                        yDistAdjust = -pointWrapOffset
                    }
                }
                if (oy >= 0 && oy < gridStride && ox >= 0 && ox < gridStride) {
                    val index = oy * gridStride + ox
                    val other = points[ox, oy]
                    val distance = point.distanceSquaredTo(Point(other.x * outputWidth + xDistAdjust, other.y * outputWidth + yDistAdjust))
                    closestPoints.add(Pair(index, distance))
                }
            }
        }
        closestPoints.sort { p1, p2 ->
            p1.second.compareTo(p2.second)
        }
        return closestPoints[0].first
    }
}
