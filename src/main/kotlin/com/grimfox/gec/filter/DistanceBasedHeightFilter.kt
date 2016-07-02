package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.RawMatrixData.Format
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.io.File

@Command(name = "mountains", description = "Create mountains from points.")
class DistanceBasedHeightFilter() : ClosestPointsDataFilter<Float> {

    override val heightFunction = { closestPoints: ClosestPoints ->
        var height = coeff1 * (closestPoints[0]?.second ?: 0.0f)
        height += coeff2 * (closestPoints[1]?.second ?: 0.0f)
        height += coeff3 * (closestPoints[2]?.second ?: 0.0f)
        height += coeff4 * (closestPoints[3]?.second ?: 0.0f)
        height += coeff5 * (closestPoints[4]?.second ?: 0.0f)
        height += coeff6 * (closestPoints[5]?.second ?: 0.0f)
        height
    }

    override val format = Format.FLOAT

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    override var inputFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    override var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-c1", "--coeff-1"), description = "The coefficient to use with term 1.", required = false)
    var coeff1: Float = -1.0f

    @Option(name = arrayOf("-c2", "--coeff-2"), description = "The coefficient to use with term 2.", required = false)
    var coeff2: Float = 1.0f

    @Option(name = arrayOf("-c3", "--coeff-3"), description = "The coefficient to use with term 3.", required = false)
    var coeff3: Float = 0.0f

    @Option(name = arrayOf("-c4", "--coeff-4"), description = "The coefficient to use with term 4.", required = false)
    var coeff4: Float = 0.0f

    @Option(name = arrayOf("-c5", "--coeff-5"), description = "The coefficient to use with term 5.", required = false)
    var coeff5: Float = 0.0f

    @Option(name = arrayOf("-c6", "--coeff-6"), description = "The coefficient to use with term 6.", required = false)
    var coeff6: Float = 0.0f
}
