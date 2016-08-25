package com.grimfox.gec.pipeline

import com.grimfox.gec.Main
import com.grimfox.gec.filter.ClosestPointsFilter
import com.grimfox.gec.filter.DistanceBasedHeightFilter
import com.grimfox.gec.output.PngGray16Output
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File

@Command(name = "mountain-permutations", description = "Generate all permuations of mountain filter and output png files.")
class MountainPermutationPipeline() : Runnable {

    @Option(name = arrayOf("-s", "--size"), description = "The size of the images as an exponent of 2. The output width and height will be equal to 2 to the power of size.", required = false)
    var size: Int = 10

    @Option(name = arrayOf("-f", "--file"), description = "The data file prefix to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-p", "--point-spacing"), description = "The approximate spacing of the points to generate as an exponent of 2. The points will be generated one per quad in a grid of 2 to the power of point-spacing squares.", required = false)
    var pointSpacing: Int = 4

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var seed: Long = System.currentTimeMillis()

    @Option(name = arrayOf("-w", "--wrap"), description = "Whether or not the points should wrap at the edges.", required = false)
    var wrapEdges: Boolean = false

    override fun run() {
        val filePrefix = outputFile.canonicalPath
        val pointsFile = File("$filePrefix-points.bin")
        val closePointsFile = File("$filePrefix-closest-points.bin")

//        val pointsGen = PointsGenerator()
//        pointsGen.size = size
//        pointsGen.outputFile = pointsFile
//        pointsGen.pointSpacing = pointSpacing
//        pointsGen.seed = seed
//        pointsGen.run()

        val closePointsFilter = ClosestPointsFilter()
        closePointsFilter.inputFile = pointsFile
        closePointsFilter.outputFile = closePointsFile
        closePointsFilter.wrapEdges = wrapEdges
        closePointsFilter.run()

        for (c1 in listOf(-1, 0, 1)) {
            for (c2 in listOf(-1, 0, 1)) {
                for (c3 in listOf(-1, 0, 1)) {
                    for (c4 in listOf(-1, 0, 1)) {
                        for (c5 in listOf(-1, 0, 1)) {
                                val permutationPrefix = "-${c1 + 1}${c2 + 1}${c3 + 1}${c4 + 1}${c5 + 1}"
                                val permutationBinFile = File("$filePrefix$permutationPrefix.bin")
                                val permutationPngFile = File("$filePrefix$permutationPrefix.png")

                                val dbhFilter = DistanceBasedHeightFilter()
                                dbhFilter.inputFile = closePointsFile
                                dbhFilter.outputFile = permutationBinFile
                                dbhFilter.coeff1 = c1.toFloat()
                                dbhFilter.coeff2 = c2.toFloat()
                                dbhFilter.coeff3 = c3.toFloat()
                                dbhFilter.coeff4 = c4.toFloat()
                                dbhFilter.coeff5 = c5.toFloat()
                                dbhFilter.run()

                                val output = PngGray16Output()
                                output.inputFile = permutationBinFile
                                output.outputFile = permutationPngFile
                                output.run()

                                permutationBinFile.deleteOnExit()
                                permutationBinFile.delete()
                        }
                    }
                }
            }
        }

    }
}
