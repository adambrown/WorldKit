package com.grimfox.gec.generator

import com.grimfox.gec.util.Utils.pow
import com.grimfox.gec.Main
import com.grimfox.gec.model.RawMatrixData
import com.grimfox.gec.model.RawMatrixData.Format
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File
import java.util.*

@Command(name = "points", description = "Generate semi-randomly placed points in a grid.")
class PointsGenerator() : Runnable {

    @Option(name = arrayOf("-s", "--size"), description = "The size of the noise as an exponent of 2. The output width and height will be equal to 2 to the power of size.", required = false)
    var size: Int = 10

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-p", "--point-spacing"), description = "The approximate spacing of the points to generate as an exponent of 2. The points will be generated one per quad in a grid of 2 to the power of point-spacing squares.", required = false)
    var pointSpacing: Int = 4

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var seed: Long = System.currentTimeMillis()

    override fun run() {
        val random = Random(seed)
        val gridSquareSize = 2.pow(pointSpacing)
        val minDistanceSq = (gridSquareSize / 4.0f) * (gridSquareSize / 4.0f)
        val mapSize = 2.pow(size)
        val gridStride = mapSize / gridSquareSize
        val endGrid = gridStride - 1
        RawMatrixData.createAndUse(outputFile, size - pointSpacing, Format.POINT_FLOAT, mapSize, gridSquareSize) { points ->
            for (y in 0..endGrid) {
                for (x in 0..endGrid) {
                    while (true) {
                        val xMin = x * gridSquareSize
                        val xMax = ((x + 1) * gridSquareSize)
                        val yMin = y * gridSquareSize
                        val yMax = ((y + 1) * gridSquareSize)
                        val randomX = random.nextFloat() * (xMax - xMin) + xMin
                        val randomY = random.nextFloat() * (yMax - yMin) + yMin
                        val point = Point(randomX, randomY)
                        if (checkDistances(points, x, y, gridStride, minDistanceSq, point)) {
                            points[x, y] = point
                            break
                        }
                    }
                }
            }
        }
    }

    private fun checkDistances(points: RawMatrixData<Point>, x: Int, y: Int, gridStride: Int, minDistance: Float, point: Point): Boolean {
        for (yOff in -3..3) {
            for (xOff in -3..3) {
                val ox = x + xOff
                val oy = y + yOff
                if (oy >= 0 && oy < gridStride && ox >= 0 && ox < gridStride) {
                    if (oy < y || (oy == y && ox < x)) {
                        if (point.distanceSquaredTo(points[ox, oy]) < minDistance) {
                            return false
                        }
                    } else {
                        return true
                    }
                }
            }
        }
        return true
    }
}
