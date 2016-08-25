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

@Command(name = "coastline", description = "Create coastline from points and closest points.")
class CoastlineFilter : Runnable {

    @Option(name = arrayOf("-i", "--input"), description = "The data file to read as input.", required = true)
    var inputFile: File = File(Main.workingDir, "input.bin")

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    @Option(name = arrayOf("-p", "--percent"), description = "The percent of reduction.", required = false)
    var percent: Float = 0.3f

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var seed: Long = System.currentTimeMillis()

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

            writeOutput(pointCount, waterPoints)
        }
    }

    private fun removeLakes(edgeGraph: ArrayList<List<Int>>, borderPoints: HashSet<Int>, waterPoints: HashSet<Int>) {
        val oceanPoints = HashSet<Int>()
        var growSet = HashSet<Int>(borderPoints)
        var iterateSet: HashSet<Int>
        while (growSet.isNotEmpty()) {
            oceanPoints.addAll(growSet)
            iterateSet = growSet
            growSet = HashSet<Int>()
            iterateSet.forEach { index ->
                addAllConnectedWater(edgeGraph, waterPoints, oceanPoints, growSet, index)
            }

        }
        val lakeSet = HashSet<Int>()
        waterPoints.forEach {
            if (!oceanPoints.contains(it)) {
                lakeSet.add(it)
            }
        }
        waterPoints.removeAll(lakeSet)
    }

    private fun addAllConnectedWater(edgeGraph: List<List<Int>>, waterPoints: Set<Int>, oceanPoints: Set<Int>, growSet: MutableSet<Int>, index: Int) {
        edgeGraph[index].forEach { adjacentIndex ->
            if (waterPoints.contains(adjacentIndex) && !oceanPoints.contains(adjacentIndex)) {
                growSet.add(adjacentIndex)
            }
        }
    }

    private fun writeOutput(pointCount: Int, waterPoints: HashSet<Int>) {
        val exponent = exp2FromSize(Math.round(Math.sqrt(pointCount.toDouble())).toInt())
        DataFiles.createAndUse<BitMatrix>(outputFile, exponent) { bitMatrix ->
            for (i in 0..pointCount - 1) {
                val y = i / bitMatrix.width
                val x = i % bitMatrix.width
                bitMatrix[x, y] = if (!waterPoints.contains(i)) { 1 } else { 0 }
            }
        }
    }

    private fun reduceCoastline(edgeGraph: ArrayList<List<Int>>, waterPoints: HashSet<Int>, coastalPoints: HashMap<Int, Int>, coastalPointDegrees: ArrayList<MutableList<Int>>, random: Random, iterations: Int): Int {
        var skips = 0
        for (i in 1..iterations) {
            val pickList = degreeWeightedPick(coastalPointDegrees, random)
            if (pickList.isEmpty()) {
                skips++
                continue
            }
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
                        if (degree < 7) {
                            coastalPointDegrees[degree - 2].remove(adjacentPointIndex)
                            coastalPointDegrees[degree - 1].add(adjacentPointIndex)
                        }
                    }
                }
            }
        }
        return skips
    }

    private fun buildUpCoastline(borderPoints: Set<Int>, edgeGraph: ArrayList<List<Int>>, waterPoints: HashSet<Int>, coastalPoints: HashMap<Int, Int>, coastalPointDegrees: ArrayList<MutableList<Int>>, random: Random, iterations: Int): Int {
        var skips = 0
        for (i in 1..iterations) {
            val pickList = degreeWeightedPick(coastalPointDegrees, random)
            if (pickList.isEmpty()) {
                skips++
                continue
            }
            val coastPick = pickList[random.nextInt(pickList.size)]
            val adjacentWater = ArrayList<Int>()
            edgeGraph[coastPick].forEach { adjacentPointIndex ->
                if (waterPoints.contains(adjacentPointIndex)) {
                    adjacentWater.add(adjacentPointIndex)
                }
            }
            if (adjacentWater.isEmpty()) {
                skips++
                continue
            }
            val pickPoint = adjacentWater[random.nextInt(adjacentWater.size)]
            if (borderPoints.contains(pickPoint)) {
                skips++
                continue
            }
            waterPoints.remove(pickPoint)
            var degree = 0
            edgeGraph[pickPoint].forEach { adjacentPointIndex ->
                if (waterPoints.contains(adjacentPointIndex)) {
                    degree++
                }
            }
            coastalPoints.put(pickPoint, degree)
            edgeGraph[pickPoint].forEach { adjacentPointIndex ->
                if (!waterPoints.contains(adjacentPointIndex)) {
                    val adjacentDegree = coastalPoints[adjacentPointIndex]
                    if (adjacentDegree != null) {
                        if (adjacentDegree == 1) {
                            coastalPoints.remove(adjacentPointIndex)
                            coastalPointDegrees[0].remove(adjacentPointIndex)
                        } else {
                            coastalPoints[adjacentPointIndex] = adjacentDegree - 1
                            if (adjacentDegree < 7) {
                                coastalPointDegrees[adjacentDegree - 1].remove(adjacentPointIndex)
                                coastalPointDegrees[adjacentDegree - 2].add(adjacentPointIndex)
                            }
                        }
                    }
                }
            }
        }
        return skips
    }

    private fun buildCoastalPointDegreeSets(coastalPoints: HashMap<Int, Int>): ArrayList<MutableList<Int>> {
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
        return coastalPointDegrees
    }

    private fun buildCoastalPoints(edgeGraph: ArrayList<List<Int>>, waterPoints: HashSet<Int>): HashMap<Int, Int> {
        val coastalPoints = HashMap<Int, Int>()
        waterPoints.forEach { waterPointIndex ->
            edgeGraph[waterPointIndex].forEach { adjacentPointIndex ->
                if (!waterPoints.contains(adjacentPointIndex)) {
                    coastalPoints[adjacentPointIndex] = coastalPoints.getOrPut(adjacentPointIndex, { 0 }) + 1
                }
            }
        }
        return coastalPoints
    }

    private fun buildEdgeGraph(edges: HashMap<Int, MutableSet<Int>>, pointCount: Int): ArrayList<List<Int>> {
        val edgeGraph = ArrayList<List<Int>>(pointCount)
        for (i in 0..pointCount - 1) {
            edgeGraph.add(i, edges[i]!!.toList().sorted())
        }
        return edgeGraph
    }

    private fun buildCornerPoints(closestPoints: Matrix<ClosestPoints>, stride: Int, strideMinusOne: Int): HashSet<Int> {
        val cornerPoints = HashSet<Int>()
        val cornerWidth = Math.round(stride * 0.293f) - 1
        fun addPoint(x: Int, y: Int) {
            val closePoint = closestPoints[x, y].p0?.first
            if (closePoint != null) {
                cornerPoints.add(closePoint)
            }
        }
        for (y in 0..cornerWidth) {
            for (x in 0..cornerWidth - y) {
                addPoint(x, y)
                addPoint(strideMinusOne - x, y)
                addPoint(x, strideMinusOne - y)
                addPoint(strideMinusOne - x, strideMinusOne - y)
            }
        }
        return cornerPoints
    }

    private fun buildBorderPoints(closestPoints: Matrix<ClosestPoints>, strideMinusOne: Int): HashSet<Int> {
        val borderPoints = HashSet<Int>()
        fun addPoint(x: Int, y: Int) {
            val closePoint = closestPoints[x, y].p0?.first
            if (closePoint != null) {
                borderPoints.add(closePoint)
            }
        }
        for (i in 0..strideMinusOne) {
            addPoint(i, 0)
            addPoint(i, strideMinusOne)
            addPoint(0, i)
            addPoint(strideMinusOne, i)
        }
        return borderPoints
    }

    private fun buildEdgeMap(closestPoints: Matrix<ClosestPoints>): HashMap<Int, MutableSet<Int>> {
        val edges = HashMap<Int, MutableSet<Int>>()
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
        return edges
    }

    private fun degreeWeightedPick(coastalPointDegrees: ArrayList<MutableList<Int>>, random: Random): MutableList<Int> {
        val degreeWeights = ArrayList<Float>(6)
        var subtotal = 0.0f
        for (j in 0..5) {
            val weight = coastalPointDegrees[j].size * 2.pow(j)
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
