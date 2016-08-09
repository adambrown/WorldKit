package com.grimfox.gec.filter

import com.grimfox.gec.Main
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.RawMatrixData
import io.airlift.airline.Option
import java.io.File
import java.util.*
import com.grimfox.gec.model.RawMatrixData.Format
import com.grimfox.gec.util.Utils
import com.grimfox.gec.util.Utils.pow
import io.airlift.airline.Command

@Command(name = "coastline", description = "Create coastline from points and closest points.")
class CoastlineFilter : Runnable {

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    var inputFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var seed: Long = System.currentTimeMillis()

    override fun run() {
        RawMatrixData.openAndUse<ClosestPoints>(inputFile) { closestPoints ->
            val edges = HashMap<Int, MutableSet<Int>>()
            val waterPoints = HashSet<Int>()
            val end = closestPoints.width - 1
            for (y in 0..end) {
                for (x in 0..end) {
                    val points = closestPoints[x, y]
                    val p0 = points.p0?.first
                    val p1 = points.p1?.first
                    if (p0 != null && p1 != null) {
                        val p0Cons = edges.getOrPut(p0, { HashSet() })
                        p0Cons.add(p1)
                        val p1Cons = edges.getOrPut(p1, { HashSet() })
                        p1Cons.add(p0)
                    }
                }
            }
            val stride = closestPoints.rasterWidth
            val strideMinusOne = stride - 1
            for (i in 0..strideMinusOne) {
                val closePointBottom = closestPoints[i, 0].p0?.first
                if (closePointBottom != null) {
                    waterPoints.add(closePointBottom)
                }
                val closePointTop = closestPoints[i, strideMinusOne].p0?.first
                if (closePointTop != null) {
                    waterPoints.add(closePointTop)
                }
                val closePointLeft = closestPoints[0, i].p0?.first
                if (closePointLeft != null) {
                    waterPoints.add(closePointLeft)
                }
                val closePointRight = closestPoints[strideMinusOne, i].p0?.first
                if (closePointRight != null) {
                    waterPoints.add(closePointRight)
                }
            }
            val points = edges.keys.toList().sorted()
            val edgeGraph = ArrayList<List<Int>>(points.last())
            for (i in points) {
                edgeGraph.add(i, edges[i]!!.toList().sorted())
            }



            val coastalPoints = HashMap<Int, Int>()
            waterPoints.forEach { waterPointIndex ->
                edgeGraph[waterPointIndex].forEach { adjacentPointIndex ->
                    if (!waterPoints.contains(adjacentPointIndex)) {
                        coastalPoints[adjacentPointIndex] = coastalPoints.getOrPut(adjacentPointIndex, { 0 }) + 1
                    }
                }
            }
            val coastalPointDegreeSets = ArrayList<MutableSet<Int>>(6)
            for (i in 0..5) {
                coastalPointDegreeSets.add(HashSet<Int>())
            }
            coastalPoints.forEach { index, degree ->
                var effectiveDegree = degree - 1
                if (effectiveDegree > 5) {
                    effectiveDegree = 5
                }
                coastalPointDegreeSets[effectiveDegree].add(index)
            }
            val coastalPointDegrees = ArrayList<MutableList<Int>>(6)
            for (i in 0..5) {
                coastalPointDegrees.add(coastalPointDegreeSets[i].toMutableList())
            }

            val random = Random(seed)
            for (i in 0..70) {


                val pickList = degreeWeightedPick(coastalPointDegrees, random)
                val pickPoint = pickList.removeAt(random.nextInt(pickList.size))
                waterPoints.add(pickPoint)
                coastalPoints.remove(pickPoint)
                edgeGraph[pickPoint].forEach { adjacentPointIndex ->
                    if (!waterPoints.contains(adjacentPointIndex)) {
                        val degree = coastalPoints.getOrPut(adjacentPointIndex, { 0 }) + 1
                        coastalPoints[adjacentPointIndex] = degree
                        if (degree == 1) {
                            coastalPointDegrees[0].add(adjacentPointIndex)
                        } else {
                            coastalPointDegrees[degree - 2].remove(adjacentPointIndex)
                            coastalPointDegrees[degree - 1].add(adjacentPointIndex)
                        }
                    }
                }

            }



            val heightFunction: (ClosestPoints) -> Int = { if (waterPoints.contains(it.p0?.first)) { 0 } else { 1 } }
            RawMatrixData.createAndUse(outputFile, Utils.exp2FromSize(closestPoints.rasterWidth), Format.UINT24) { heightMap ->
                val rasterEnd = heightMap.width - 1
                for (y in 0..rasterEnd) {
                    for (x in 0..rasterEnd) {
                        heightMap[x, y] = heightFunction(closestPoints[x, y])
                    }
                }
            }
        }
    }

    private fun degreeWeightedPick(coastalPointDegrees: ArrayList<MutableList<Int>>, random: Random): MutableList<Int> {
        val degreeWeights = ArrayList<Float>(6)
        var subtotal = 0.0f
        for (j in 0..5) {
            val weight = coastalPointDegrees[j].size * 3.pow(j)
            subtotal += weight
            degreeWeights.add(weight.toFloat())
        }
        val total = subtotal.toFloat()
        subtotal = 0.0f
        for (j in 0..5) {
            subtotal += degreeWeights[j] / total
            degreeWeights[j] = subtotal
        }
        val pick = random.nextFloat()
        var pickList = coastalPointDegrees[0]
        for (j in 0..5) {
            if (pick < degreeWeights[j]) {
                val degreeList = coastalPointDegrees[j]
                if (degreeList.isNotEmpty()) {
                    pickList = degreeList
                    break
                }
            }
        }
        return pickList
    }
}
