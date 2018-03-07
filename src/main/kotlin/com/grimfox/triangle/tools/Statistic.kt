package com.grimfox.triangle.tools

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.Point
import java.util.concurrent.atomic.AtomicLong

class Statistic(mesh: Mesh) {

    companion object {

        private const val SAMPLE_DEGREES = 60

        val inCircleCount = AtomicLong(0)

        val inCircleAdaptCount = AtomicLong(0)

        val counterClockwiseCount = AtomicLong(0)

        val counterClockwiseAdaptCount = AtomicLong(0)

        val orient3dCount = AtomicLong(0)

        val hyperbolaCount = AtomicLong(0)

        val circumcenterCount = AtomicLong(0)

        val circleTopCount = AtomicLong(0)

        val relocationCount = AtomicLong(0)

        fun printStats(): String {
            return """
    inCircleCount = $inCircleCount
    inCircleAdaptCount = $inCircleAdaptCount
    counterClockwiseCount = $counterClockwiseCount
    counterClockwiseAdaptCount = $counterClockwiseAdaptCount
    orient3dCount = $orient3dCount
    hyperbolaCount = $hyperbolaCount
    circumcenterCount = $circumcenterCount
    circleTopCount = $circleTopCount
    relocationCount = $relocationCount
"""
        }

        private val plus1Mod3 = intArrayOf(1, 2, 0)

        private val minus1Mod3 = intArrayOf(2, 0, 1)
    }

    var shortestEdge = 0.0
        private set

    var longestEdge = 0.0
        private set

    var shortestAltitude = 0.0
        private set

    var largestAspectRatio = 0.0
        private set

    var smallestArea = 0.0
        private set

    var largestArea = 0.0
        private set

    var smallestAngle = 0.0
        private set

    var largestAngle = 0.0
        private set

    val angleHistogram = IntArray(SAMPLE_DEGREES)

    val minAngleHistogram = IntArray(SAMPLE_DEGREES)

    val maxAngleHistogram = IntArray(SAMPLE_DEGREES)

    init {
        val p = arrayOfNulls<Point>(3)
        var k1: Int
        var k2: Int
        var degreeStep: Int
        val cosSquareTable = DoubleArray(SAMPLE_DEGREES / 2 - 1)
        val dx = DoubleArray(3)
        val dy = DoubleArray(3)
        val edgeLength = DoubleArray(3)
        var dotProduct: Double
        var cosSquare: Double
        var triArea: Double
        var triLongest2: Double
        var triMinAltitude2: Double
        var triAspect2: Double
        val radconst = Math.PI / SAMPLE_DEGREES
        val degconst = 180.0 / Math.PI
        for (i in 0..SAMPLE_DEGREES / 2 - 1 - 1) {
            cosSquareTable[i] = Math.cos(radconst * (i + 1))
            cosSquareTable[i] = cosSquareTable[i] * cosSquareTable[i]
        }
        for (i in 0..SAMPLE_DEGREES - 1) {
            angleHistogram[i] = 0
        }
        shortestAltitude = mesh._bounds.width + mesh._bounds.height
        shortestAltitude *= shortestAltitude
        largestAspectRatio = 0.0
        shortestEdge = shortestAltitude
        longestEdge = 0.0
        smallestArea = shortestAltitude
        largestArea = 0.0
        smallestAngle = 0.0
        largestAngle = 2.0
        var acuteBiggest = true
        var acuteBiggestTri = true
        var triMinAngle: Double
        var triMaxAngle: Double
        for (tri in mesh._triangles) {
            triMinAngle = 0.0
            triMaxAngle = 1.0
            p[0] = tri.vertices[0]
            p[1] = tri.vertices[1]
            p[2] = tri.vertices[2]
            triLongest2 = 0.0
            for (i in 0..2) {
                k1 = plus1Mod3[i]
                k2 = minus1Mod3[i]
                dx[i] = p[k1]!!.x - p[k2]!!.x
                dy[i] = p[k1]!!.y - p[k2]!!.y
                edgeLength[i] = dx[i] * dx[i] + dy[i] * dy[i]
                if (edgeLength[i] > triLongest2) {
                    triLongest2 = edgeLength[i]
                }
                if (edgeLength[i] > longestEdge) {
                    longestEdge = edgeLength[i]
                }
                if (edgeLength[i] < shortestEdge) {
                    shortestEdge = edgeLength[i]
                }
            }
            triArea = Math.abs((p[2]!!.x - p[0]!!.x) * (p[1]!!.y - p[0]!!.y) - (p[1]!!.x - p[0]!!.x) * (p[2]!!.y - p[0]!!.y))
            if (triArea < smallestArea) {
                smallestArea = triArea
            }
            if (triArea > largestArea) {
                largestArea = triArea
            }
            triMinAltitude2 = triArea * triArea / triLongest2
            if (triMinAltitude2 < shortestAltitude) {
                shortestAltitude = triMinAltitude2
            }
            triAspect2 = triLongest2 / triMinAltitude2
            if (triAspect2 > largestAspectRatio) {
                largestAspectRatio = triAspect2
            }
            for (i in 0..2) {
                k1 = plus1Mod3[i]
                k2 = minus1Mod3[i]
                dotProduct = dx[k1] * dx[k2] + dy[k1] * dy[k2]
                cosSquare = dotProduct * dotProduct / (edgeLength[k1] * edgeLength[k2])
                degreeStep = SAMPLE_DEGREES / 2 - 1
                (degreeStep - 1 downTo 0)
                        .asSequence()
                        .filter { cosSquare > cosSquareTable[it] }
                        .forEach { degreeStep = it }
                if (dotProduct <= 0.0) {
                    angleHistogram[degreeStep]++
                    if (cosSquare > smallestAngle) {
                        smallestAngle = cosSquare
                    }
                    if (acuteBiggest && cosSquare < largestAngle) {
                        largestAngle = cosSquare
                    }
                    if (cosSquare > triMinAngle) {
                        triMinAngle = cosSquare
                    }
                    if (acuteBiggestTri && cosSquare < triMaxAngle) {
                        triMaxAngle = cosSquare
                    }
                } else {
                    angleHistogram[SAMPLE_DEGREES - degreeStep - 1]++
                    if (acuteBiggest || cosSquare > largestAngle) {
                        largestAngle = cosSquare
                        acuteBiggest = false
                    }
                    if (acuteBiggestTri || cosSquare > triMaxAngle) {
                        triMaxAngle = cosSquare
                        acuteBiggestTri = false
                    }
                }
            }
            degreeStep = SAMPLE_DEGREES / 2 - 1
            (degreeStep - 1 downTo 0)
                    .asSequence()
                    .filter { triMinAngle > cosSquareTable[it] }
                    .forEach { degreeStep = it }
            minAngleHistogram[degreeStep]++
            degreeStep = SAMPLE_DEGREES / 2 - 1
            (degreeStep - 1 downTo 0)
                    .asSequence()
                    .filter { triMaxAngle > cosSquareTable[it] }
                    .forEach { degreeStep = it }
            if (acuteBiggestTri) {
                maxAngleHistogram[degreeStep]++
            } else {
                maxAngleHistogram[SAMPLE_DEGREES - degreeStep - 1]++
            }
            acuteBiggestTri = true
        }
        shortestEdge = Math.sqrt(shortestEdge)
        longestEdge = Math.sqrt(longestEdge)
        shortestAltitude = Math.sqrt(shortestAltitude)
        largestAspectRatio = Math.sqrt(largestAspectRatio)
        smallestArea *= 0.5
        largestArea *= 0.5
        if (smallestAngle >= 1.0) {
            smallestAngle = 0.0
        } else {
            smallestAngle = degconst * Math.acos(Math.sqrt(smallestAngle))
        }
        if (largestAngle >= 1.0) {
            largestAngle = 180.0
        } else {
            if (acuteBiggest) {
                largestAngle = degconst * Math.acos(Math.sqrt(largestAngle))
            } else {
                largestAngle = 180.0 - degconst * Math.acos(Math.sqrt(largestAngle))
            }
        }
    }
}
