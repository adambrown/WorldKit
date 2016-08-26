package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.model.BitMatrix
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.util.Utils.exp2FromSize
import com.grimfox.gec.util.Utils.pow
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File
import java.util.*

@Command(name = "coastline-create", description = "Create coastline from closest points.")
class CoastlineCreateFilter : CoastlineFilter {

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    var inputFile: File = File(Main.workingDir, "input.bin")

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
        DataFiles.openAndUse<ClosestPoints>(inputFile) { closestPoints ->
            val stride = closestPoints.width
            val strideMinusOne = stride - 1

            val edges = buildEdgeMap(closestPoints)

            val borderPoints = buildBorderPoints(closestPoints, strideMinusOne)

            val waterPoints = buildCornerPoints(closestPoints, stride, strideMinusOne)
            waterPoints.addAll(borderPoints)

            val pointCount = edges.size

            val edgeGraph = buildEdgeGraph(edges, pointCount)

            val landPointCount = pointCount - waterPoints.size

            val coastalPoints = buildCoastalPoints(edgeGraph, waterPoints)

            val coastalPointDegrees = buildCoastalPointDegreeSets(coastalPoints)

            val random = Random(seed)
            var iterations = Math.min(landPointCount, Math.round(landPointCount * percent))
            var skips = reduceCoastline(edgeGraph, waterPoints, coastalPoints, coastalPointDegrees, random, iterations)
            iterations = (iterations - skips) / 2
            skips = buildUpCoastline(borderPoints, edgeGraph, waterPoints, coastalPoints, coastalPointDegrees, random, iterations)
            iterations -= skips
            skips = reduceCoastline(edgeGraph, waterPoints, coastalPoints, coastalPointDegrees, random, iterations)
            iterations -= skips
            buildUpCoastline(borderPoints, edgeGraph, waterPoints, coastalPoints, coastalPointDegrees, random, skips)

            removeLakes(edgeGraph, borderPoints, waterPoints)
            removeIslands(edgeGraph, waterPoints, pointCount, smallIsland, largeIsland)

            writeOutput(outputFile, pointCount, waterPoints)
        }
    }
}
