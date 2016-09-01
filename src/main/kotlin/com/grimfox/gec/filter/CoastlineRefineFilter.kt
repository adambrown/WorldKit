package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.util.Utils.buildEdgeGraph
import com.grimfox.gec.util.Utils.buildEdgeMap
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode.READ_WRITE
import java.util.*

@Command(name = "coastline-refine", description = "Create coastline from closest points.")
class CoastlineRefineFilter : CoastlineFilter {

    @Option(name = arrayOf("-c", "--closest-points"), description = "The closest points file to read as input.", required = true)
    var closestPointsFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-m", "--mask"), description = "The points mask file to read as input.", required = true)
    var pointsMaskFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-i", "--id-file"), description = "The points mask file to read as input.", required = false)
    var pointIdsFile: File? = null

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-p", "--percent"), description = "The percent of reduction.", required = false)
    var percent: Float = 0.3f

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var seed: Long = System.currentTimeMillis()

    @Option(name = arrayOf("-s", "--small-island"), description = "The minimum number of points in an island.", required = false)
    var smallIsland: Int = 0

    @Option(name = arrayOf("-l", "--large-island"), description = "The maximum number of points in an island.", required = false)
    var largeIsland: Int = 0

    override fun run() {
        DataFiles.openAndUse<ClosestPoints>(closestPointsFile) { closestPoints ->
            DataFiles.openAndUse<Int>(pointsMaskFile) { mask ->
                val idFile = pointIdsFile
                if (idFile == null) {
                    doWork(closestPoints, null, mask)
                } else {
                    DataFiles.openAndUse<Int>(idFile, READ_WRITE) { idMask ->
                        doWork(closestPoints, idMask, mask)
                    }
                }
            }
        }
    }

    private fun doWork(closestPoints: Matrix<ClosestPoints>, idMask: Matrix<Int>?, mask: Matrix<Int>) {
        val stride = closestPoints.width
        val strideMinusOne = stride - 1

        val edges = buildEdgeMap(closestPoints)

        val borderPoints = buildBorderPoints(closestPoints, strideMinusOne)
        val waterPoints = HashSet<Int>(borderPoints)
        val pointCount = edges.size
        for (i in 0..pointCount - 1) {
            if (mask[i] == 0) {
                waterPoints.add(i)
            }
        }

        val edgeGraph = buildEdgeGraph(edges, pointCount)

        val landPointCount = pointCount - waterPoints.size

        val coastalPoints = buildCoastalPoints(edgeGraph, waterPoints)

        val coastalPointDegrees = buildCoastalPointDegreeSets(coastalPoints)

        val random = Random(seed)
        var iterations = Math.min(landPointCount, Math.round(landPointCount * percent))
        var skips = reduceCoastline(edgeGraph, waterPoints, coastalPoints, coastalPointDegrees, idMask, random, iterations)
        iterations -= skips
        skips = buildUpCoastline(borderPoints, edgeGraph, waterPoints, coastalPoints, coastalPointDegrees, idMask, random, iterations)
        iterations -= skips
        skips = reduceCoastline(edgeGraph, waterPoints, coastalPoints, coastalPointDegrees, idMask, random, iterations)
        iterations -= skips
        buildUpCoastline(borderPoints, edgeGraph, waterPoints, coastalPoints, coastalPointDegrees, idMask, random, iterations)

        removeLakes(edgeGraph, borderPoints, waterPoints, idMask)
        removeIslands(edgeGraph, waterPoints, idMask, pointCount, smallIsland, largeIsland)

        writeOutput(outputFile, pointCount, waterPoints)
    }
}
