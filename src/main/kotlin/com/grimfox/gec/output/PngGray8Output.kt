package com.grimfox.gec.output

import com.grimfox.gec.Main
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.awt.image.BufferedImage
import java.io.File

@Command(name = "png8", description = "Output to png 8-bit grayscale.")
class PngGray8Output : PngGrayOutput {

    override val bufferedImageType: Int = BufferedImage.TYPE_BYTE_GRAY

    override val rangeMax: Int = 255

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    override var inputFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The png file to write as output.", required = true)
    override var outputFile: File = File(Main.workingDir, "output.png")

    @Option(name = arrayOf("-n", "--normalize"), description = "Whether to normalize the output values to minimum and maximum height.", required = false)
    override var normalize: Boolean = true

    @Option(name = arrayOf("-m", "--min"), description = "The minimum input value.", required = false)
    override var minValue: Double = 0.0

    @Option(name = arrayOf("-x", "--max"), description = "The maximum input value.", required = false)
    override var maxValue: Double = 1.0
}