package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.util.Utils.exp2FromSize
import com.grimfox.gec.generator.Point
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.RawMatrixData
import com.grimfox.gec.model.RawMatrixData.Format
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File
import java.util.*

@Command(name = "closest-points", description = "Create a matrix of the closest points to each pixel from points.")
class ClosestPointsFilter : Runnable {

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    var inputFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    override fun run() {
        RawMatrixData.openAndUse<Point>(inputFile) { points ->
            val gridStride = points.width
            val gridSquareSize = points.segmentWidth
            RawMatrixData.createAndUse(outputFile, exp2FromSize(points.rasterWidth), Format.CLOSEST_POINTS) { heightMap ->
                val end = heightMap.width - 1
                for (y in 0..end) {
                    for (x in 0..end) {
                        val gridX = x / gridSquareSize
                        val gridY = y / gridSquareSize
                        val closestPoints = getClosestPoints(points, gridX, gridY, gridStride, Point(x.toFloat(), y.toFloat()))
                        heightMap[x, y] = closestPoints
                    }
                }
            }
        }
    }

    private fun getClosestPoints(points: RawMatrixData<Point>, x: Int, y: Int, gridStride: Int, point: Point): ClosestPoints {
        val closestPoints = ArrayList<Pair<Int, Float>>(49)
        for (yOff in -3..3) {
            for (xOff in -3..3) {
                val ox = x + xOff
                val oy = y + yOff
                if (oy >= 0 && oy < gridStride && ox >= 0 && ox < gridStride) {
                    val index = oy * gridStride + ox
                    val distance = point.distanceSquaredTo(points[ox, oy])
                    closestPoints.add(Pair(index, distance))
                }

            }
        }
        closestPoints.sort { p1, p2 ->
            p1.second.compareTo(p2.second)
        }
        return ClosestPoints(closestPoints[0],
                closestPoints[1],
                closestPoints[2],
                closestPoints[3],
                closestPoints[4],
                closestPoints[5],
                closestPoints[6],
                closestPoints[7],
                closestPoints[8])
    }
}
