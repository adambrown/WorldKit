package wk.utils

import wk.api.inParallel
import wk.api.IntArrayMatrix
import wk.api.Matrix
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*
import kotlin.system.measureNanoTime

object FlowCalculator {

    var time = 0L
    val nVector = Vector2D(0.0, 1.0)
    val neVector = Vector2D(1.0, 1.0).getUnit()
    val eVector = Vector2D(1.0, 0.0)
    val seVector = Vector2D(1.0, -1.0).getUnit()
    val sVector = Vector2D(0.0, -1.0)
    val swVector = Vector2D(-1.0, -1.0).getUnit()
    val wVector = Vector2D(-1.0, 0.0)
    val nwVector = Vector2D(-1.0, 1.0).getUnit()

    @JvmStatic
    fun main(vararg args: String) {
        val inputFile = File(args[0])
        val bufferedImage = ImageIO.read(inputFile)
        val flowMagnitudeOutput = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_flowmap.png")
        val flowVectorOutput = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_flowvector.png")
        val sourceData = bufferedImage.raster
        val width = sourceData.width
        val heights = DoubleArrayMatrix(width)
        var water1 = DoubleArrayMatrix(width)
        var water2 = DoubleArrayMatrix(width)
        val cumulativeFlowVectors = DoubleArray(width * width * 2)
        val water = Array(2) { if (it == 0) water1 else water2 }
        val movementIndices = IntArrayMatrix(width) { 4 }
        val movementAmounts = DoubleArrayMatrix(width)
        val movementVectors = DoubleArray(width * width * 2)
        val cumulativeFlowAmounts = DoubleArrayMatrix(width)
        for (x in 0 until width) {
            for (y in 0 until width) {
                heights[x, y] = sourceData.getSampleDouble(x, y, 0)
            }
        }
        val rainPerStep = 0.04
        val evaporationPerStep = 0.008
        val flowPersistence = 0.35
        val iterations = 3000
        val warmUp = 1000
        for (i in 1..iterations) {
            println("iteration: $i")
            water1 = water[i % 2]
            water2 = water[(i + 1) % 2]
            addWater(water1, rainPerStep, evaporationPerStep)
            calculateFlows(movementIndices, movementAmounts, movementVectors, cumulativeFlowVectors, heights, water1, width)
            applyFlows(movementIndices, movementAmounts, movementVectors, cumulativeFlowAmounts, cumulativeFlowVectors, water1, water2, width, flowPersistence, i > warmUp)
        }
        writeCumulativeFlows(cumulativeFlowAmounts, cumulativeFlowVectors, flowMagnitudeOutput, flowVectorOutput, width)
        println("took ${time / 1000000000.0}s")
    }

    private fun writeCumulativeFlows(cumulativeFlowAmounts: DoubleArrayMatrix, cumulativeFlowVectors: DoubleArray, flowMagnitudeOutput: File, flowVectorOutput: File, width: Int) {
        var minFlow = Double.MAX_VALUE
        var maxFlow = -Double.MAX_VALUE
        for (y in 0 until width) {
            for (x in 0 until width) {
                val flowValue = cumulativeFlowAmounts[x, y]
                if (flowValue < minFlow) {
                    minFlow = flowValue
                }
                if (flowValue > maxFlow) {
                    maxFlow = flowValue
                }
            }
        }
        val deltaFlow = maxFlow - minFlow
        val output = BufferedImage(width, width, BufferedImage.TYPE_USHORT_GRAY)
        val outputData = output.raster
        for (y in 0 until width) {
            for (x in 0 until width) {
                outputData.setSample(x, y, 0, ((cumulativeFlowAmounts[x, y] - minFlow) / deltaFlow) * 65535)
            }
        }
        ImageIO.write(output, "png", flowMagnitudeOutput)


        val vectorOutput = BufferedImage(width, width, BufferedImage.TYPE_3BYTE_BGR)
        val vectorOutputData = vectorOutput.raster
        for (y in 0 until width) {
            val yOff = y * width
            for (x in 0 until width) {
                val index = (yOff + x) * 2
                val flowX = cumulativeFlowVectors[index]
                val flowY = cumulativeFlowVectors[index + 1]
                if (flowX != 0.0 || flowY != 0.0) {
                    val flowVector = Vector2D(flowX, flowY).getUnit()
                    vectorOutputData.setSample(x, y, 0, (flowVector.a * 0.5 + 0.5) * 255)
                    vectorOutputData.setSample(x, y, 1, (flowVector.b * 0.5 + 0.5) * 255)
                } else {
                    vectorOutputData.setSample(x, y, 0, 127)
                    vectorOutputData.setSample(x, y, 1, 127)
                }
            }
        }
        ImageIO.write(vectorOutput, "png", flowVectorOutput)
    }

    private fun calculateFlows(movementIndices: IntArrayMatrix, movementAmounts: DoubleArrayMatrix, movementVectors: DoubleArray, cumulativeFlowVectors: DoubleArray, heights: DoubleArrayMatrix, water1: DoubleArrayMatrix, width: Int) {
        time += measureNanoTime {
            (1 until width - 1).inParallel(
                    context = { DoubleArray(9) to DoubleArray(9) }
            ) { (waterHeights, terrainHeights), y ->
                val ym1 = y - 1
                val yp1 = y + 1
                val yOff = y * width
                for (x in 1 until width - 1) {
                    val xm1 = x - 1
                    val xp1 = x + 1
                    waterHeights[0] = water1[xm1, ym1]
                    waterHeights[1] = water1[x, ym1]
                    waterHeights[2] = water1[xp1, ym1]
                    waterHeights[3] = water1[xm1, y]
                    waterHeights[4] = water1[x, y]
                    waterHeights[5] = water1[xp1, y]
                    waterHeights[6] = water1[xm1, yp1]
                    waterHeights[7] = water1[x, yp1]
                    waterHeights[8] = water1[xp1, yp1]
                    terrainHeights[0] = heights[xm1, ym1] + waterHeights[0]
                    terrainHeights[1] = heights[x, ym1] + waterHeights[1]
                    terrainHeights[2] = heights[xp1, ym1] + waterHeights[2]
                    terrainHeights[3] = heights[xm1, y] + waterHeights[3]
                    terrainHeights[4] = heights[x, y] + waterHeights[4]
                    terrainHeights[5] = heights[xp1, y] + waterHeights[5]
                    terrainHeights[6] = heights[xm1, yp1] + waterHeights[6]
                    terrainHeights[7] = heights[x, yp1] + waterHeights[7]
                    terrainHeights[8] = heights[xp1, yp1] + waterHeights[8]
                    var minHeight = terrainHeights[4]
                    var minIndex = 4
                    var heightDiff = 0.0
                    terrainHeights.forEachIndexed { index, height ->
                        if (height < minHeight) {
                            minHeight = height
                            minIndex = index
                            heightDiff = terrainHeights[4] - minHeight
                        }
                    }
                    movementIndices[x, y] = minIndex
                    movementAmounts[x, y] = min(heightDiff * 0.125, waterHeights[4])
                    val index = (yOff + x) * 2
                    movementVectors[index] = cumulativeFlowVectors[index]
                    movementVectors[index + 1] = cumulativeFlowVectors[index + 1]
                }
            }
        }
    }

    private fun applyFlows(movementIndices: IntArrayMatrix, movementAmounts: DoubleArrayMatrix, movementVectors: DoubleArray, cumulativeFlowAmounts: DoubleArrayMatrix, cumulativeFlowVectors: DoubleArray, water1: DoubleArrayMatrix, water2: DoubleArrayMatrix, width: Int, flowPersistence: Double, accumulate: Boolean) {
        time += measureNanoTime {
            (1 until width - 1).inParallel { y ->
                val ym1 = max(0, y - 1)
                val yp1 = min(width - 1, y + 1)
                for (x in 1 until width - 1) {
                    val xm1 = max(0, x - 1)
                    val xp1 = min(width - 1, x + 1)
                    var fvx = 0.0
                    var fvy = 0.0
                    fun appendVector(vector: Vector2D, xLookup: Int, yLookup: Int, magnitude: Double): Double {
                        val index = (yLookup * width + xLookup) * 2
                        var evx = movementVectors[index]
                        var evy = movementVectors[index + 1]
                        if (evx != 0.0 || evy != 0.0) {
//                            var length = sqrt(evx * evx + evy * evy)
//                            evx /= length
//                            evy /= length
                            evx = vector.a + evx * flowPersistence
                            evy = vector.b + evy * flowPersistence
                            val length = sqrt(evx * evx + evy * evy)
                            evx /= length
                            evy /= length
                        } else {
                            evx = vector.a
                            evy = vector.b
                        }
                        fvx += evx * magnitude
                        fvy += evy * magnitude
                        return magnitude
                    }
                    var inflow = if (movementIndices[xm1, ym1] == 8) appendVector(seVector, xm1, ym1, movementAmounts[xm1, ym1]) else 0.0
                    inflow += if (movementIndices[x, ym1] == 7) appendVector(sVector, x, ym1, movementAmounts[x, ym1]) else 0.0
                    inflow += if (movementIndices[xp1, ym1] == 6) appendVector(swVector, xp1, ym1, movementAmounts[xp1, ym1]) else 0.0
                    inflow += if (movementIndices[xm1, y] == 5) appendVector(eVector, xm1, y, movementAmounts[xm1, y]) else 0.0
                    inflow += if (movementIndices[xp1, y] == 3) appendVector(wVector, xp1, y, movementAmounts[xp1, y]) else 0.0
                    inflow += if (movementIndices[xm1, yp1] == 2) appendVector(neVector, xm1, yp1, movementAmounts[xm1, yp1]) else 0.0
                    inflow += if (movementIndices[x, yp1] == 1) appendVector(nVector, x, yp1, movementAmounts[x, yp1]) else 0.0
                    inflow += if (movementIndices[xp1, yp1] == 0) appendVector(nwVector, xp1, yp1, movementAmounts[xp1, yp1]) else 0.0
                    val outflow = if (movementIndices[x, y] == 4) 0.0 else movementAmounts[x, y]
                    water2[x, y] = water1[x, y] + inflow - outflow
                    if (accumulate) {
                        cumulativeFlowAmounts[x, y] += inflow
                        val index = (y * width + x) * 2
                        cumulativeFlowVectors[index] += fvx
                        cumulativeFlowVectors[index + 1] += fvy
                    }
                }
            }
        }
    }

    private fun addWater(water1: DoubleArrayMatrix, rainPerStep: Double, evaporationPerStep: Double) {
        for (i in 0 until water1.size) {
            val currentWater = max(0.0, water1[i] - evaporationPerStep)
            water1[i] = currentWater + rainPerStep
        }
    }
}

class Vector2D(var a: Double, var b: Double) {

    val length2: Double by lazy { a * a + b * b }

    val length: Double by lazy { Math.sqrt(length2) }

    operator fun set(i: Int, f: Double) = when (i) { 0 -> { a = f } 1 -> { b = f } else -> { throw IndexOutOfBoundsException("index: $i, size: 2") } }

    operator fun get(i: Int): Double = when (i) { 0 -> { a } 1 -> { b } else -> { throw IndexOutOfBoundsException("index: $i, size: 2") } }

    operator fun unaryMinus() = Vector2D(-a, -b)

    operator fun plus(c: Double) = Vector2D(a + c, b + c)

    operator fun plusAssign(c: Double) {
        a += c
        b += c
    }

    operator fun plus(other: Vector2D) = Vector2D(a + other.a, b + other.b)

    operator fun plusAssign(other: Vector2D) {
        a += other.a
        b += other.b
    }

    operator fun minus(c: Double) = Vector2D(a - c, b - c)

    operator fun minusAssign(c: Double) {
        a -= c
        b -= c
    }

    operator fun minus(other: Vector2D) = Vector2D(a - other.a, b - other.b)

    operator fun minusAssign(other: Vector2D) {
        a -= other.a
        b -= other.b
    }

    operator fun times(c: Double) = Vector2D(a * c, b * c)

    operator fun timesAssign(c: Double) {
        a *= c
        b *= c
    }

    operator fun div(c: Double) = Vector2D(a / c, b / c)

    operator fun divAssign(c: Double) {
        a /= c
        b /= c
    }

    fun toList() = listOf(a, b)

    fun getUnit() = Vector2D(a / length, b / length)

    fun getPerpendicular() = Vector2D(b, -a)

    fun cross(other: Vector2D) = (a * other.b) - (b * other.a)

    fun dot(other: Vector2D) = (a * other.a) + (b * other.b)
}

class DoubleArrayMatrix(override val width: Int, override val height: Int = width, array: DoubleArray? = null, init: ((Int) -> Double)? = null) : Matrix<Double> {

    override val size = width * height

    val array = array ?: if (init != null) DoubleArray(size, init) else DoubleArray(size)

    override fun set(i: Int, value: Double) {
        array[i] = value
    }

    override fun get(i: Int): Double {
        return array[i]
    }
}