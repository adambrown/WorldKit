package com.grimfox.gec.model.geometry

import java.lang.Math.sqrt
import java.util.*

class SpatialPointSet2F() {

    companion object {
        private val q1Centers = centersForLayer(4)
        private val q1Diagonal = diagonalForLayer(4)
        private val q2Offset = offsetForLayer(8)
        private val q2Diagonal = diagonalForLayer(8)
        private val q3Offset = offsetForLayer(16)
        private val q3Diagonal = diagonalForLayer(16)
        private val q4Offset = offsetForLayer(32)
        private val q4Diagonal = diagonalForLayer(32)
        private val q5Offset = offsetForLayer(64)
        private val q5Diagonal = diagonalForLayer(64)
        private val q6Offset = offsetForLayer(128)
        private val q6Diagonal = diagonalForLayer(128)

        private fun centersForLayer(width: Int): Array<Point2F> {
            val centers = Array(width * width) { Point2F(0.0f, 0.0f)}
            val grid = 1.0 / width
            val halfGrid = grid / 2.0
            for (yInt in 0..3) {
                val y = yInt * grid + halfGrid
                for (xInt in 0..3) {
                    val x = xInt * grid + halfGrid
                    centers[yInt * width + xInt] = Point2F(x.toFloat(), y.toFloat())
                }
            }
            return centers
        }

        private fun offsetForLayer(width: Int): Float {
            return (1.0f / width) / 2.0f
        }

        private fun diagonalForLayer(width: Int): Float {
            val grid = 1.0 / width
            return sqrt(grid * grid + grid * grid).toFloat()
        }
    }

    class Quadrant<T>(val center: Point2F, var a: T, var b: T, var c: T, var d: T) {
        operator fun get(i: Int): T {
            when (i) {
                0 -> return a
                1 -> return b
                2 -> return c
                3 -> return d
                else -> throw IndexOutOfBoundsException(i.toString())
            }
        }

        operator fun set(i: Int, value: T) {
            when (i) {
                0 -> a = value
                1 -> b = value
                2 -> c = value
                3 -> d = value
                else -> throw IndexOutOfBoundsException(i.toString())
            }
        }
    }

    private val data = Array<Quadrant<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>?>?>(16) { null }

    fun closestPoint(point: Point2F): Point2F? {
        return pickClosest(closestPoints(point), point)
    }

    fun closestPoints(point: Point2F): List<Point2F> {
        val quadsToCheck = ArrayList<Pair<Quadrant<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>?>, Float>>(16)
        var minDist2 = Float.MAX_VALUE
        for (i in 0..15) {
            val quadrant = data[i]
            if (quadrant != null) {
                val distance = point.distance2(quadrant.center)
                quadsToCheck.add(Pair(quadrant, distance))
                if (distance < minDist2) {
                    minDist2 = distance
                }
            }
        }
        minDist2 = (sqrt(minDist2.toDouble()) + q1Diagonal).toFloat()
        minDist2 *= minDist2
        return checkQuads2(quadsToCheck.filter { it.second <= minDist2 }, point)
    }

    private fun checkQuads2(quads: List<Pair<Quadrant<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>?>, Float>>, point: Point2F): List<Point2F> {
        val quadsToCheck = ArrayList<Pair<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>, Float>>()
        var minDist2 = Float.MAX_VALUE
        for (pair in quads) {
            val parent = pair.first
            for (i in 0..3) {
                val quadrant = parent[i]
                if (quadrant != null) {
                    val distance = point.distance2(quadrant.center)
                    quadsToCheck.add(Pair(quadrant, distance))
                    if (distance < minDist2) {
                        minDist2 = distance
                    }
                }
            }
        }
        minDist2 = (sqrt(minDist2.toDouble()) + q2Diagonal).toFloat()
        minDist2 *= minDist2
        return checkQuads3(quadsToCheck.filter { it.second <= minDist2 }, point)
    }

    private fun checkQuads3(quads: List<Pair<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>, Float>>, point: Point2F): List<Point2F> {
        val quadsToCheck = ArrayList<Pair<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>, Float>>()
        var minDist2 = Float.MAX_VALUE
        for (pair in quads) {
            val parent = pair.first
            for (i in 0..3) {
                val quadrant = parent[i]
                if (quadrant != null) {
                    val distance = point.distance2(quadrant.center)
                    quadsToCheck.add(Pair(quadrant, distance))
                    if (distance < minDist2) {
                        minDist2 = distance
                    }
                }
            }
        }
        minDist2 = (sqrt(minDist2.toDouble()) + q3Diagonal).toFloat()
        minDist2 *= minDist2
        return checkQuads4(quadsToCheck.filter { it.second <= minDist2 }, point)
    }

    private fun checkQuads4(quads: List<Pair<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>, Float>>, point: Point2F): List<Point2F> {
        val quadsToCheck = ArrayList<Pair<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>, Float>>()
        var minDist2 = Float.MAX_VALUE
        for (pair in quads) {
            val parent = pair.first
            for (i in 0..3) {
                val quadrant = parent[i]
                if (quadrant != null) {
                    val distance = point.distance2(quadrant.center)
                    quadsToCheck.add(Pair(quadrant, distance))
                    if (distance < minDist2) {
                        minDist2 = distance
                    }
                }
            }
        }
        minDist2 = (sqrt(minDist2.toDouble()) + q4Diagonal).toFloat()
        minDist2 *= minDist2
        return checkQuads5(quadsToCheck.filter { it.second <= minDist2 }, point)
    }

    private fun checkQuads5(quads: List<Pair<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>, Float>>, point: Point2F): List<Point2F> {
        val quadsToCheck = ArrayList<Pair<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>, Float>>()
        var minDist2 = Float.MAX_VALUE
        for (pair in quads) {
            val parent = pair.first
            for (i in 0..3) {
                val quadrant = parent[i]
                if (quadrant != null) {
                    val distance = point.distance2(quadrant.center)
                    quadsToCheck.add(Pair(quadrant, distance))
                    if (distance < minDist2) {
                        minDist2 = distance
                    }
                }
            }
        }
        minDist2 = (sqrt(minDist2.toDouble()) + q5Diagonal).toFloat()
        minDist2 *= minDist2
        return checkQuads6(quadsToCheck.filter { it.second <= minDist2 }, point)
    }

    private fun checkQuads6(quads: List<Pair<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>, Float>>, point: Point2F): List<Point2F> {
        val quadsToCheck = ArrayList<Pair<Pair<Point2F, ArrayList<Point2F>>, Float>>()
        var minDist2 = Float.MAX_VALUE
        for (pair in quads) {
            val parent = pair.first
            for (i in 0..3) {
                val quadrant = parent[i]
                if (quadrant != null) {
                    val distance = point.distance2(quadrant.first)
                    quadsToCheck.add(Pair(quadrant, distance))
                    if (distance < minDist2) {
                        minDist2 = distance
                    }
                }
            }
        }
        minDist2 = (sqrt(minDist2.toDouble()) + q6Diagonal).toFloat()
        minDist2 *= minDist2
        return quadsToCheck.filter { it.second <= minDist2 }.flatMap { it.first.second }
    }

    private fun pickClosest(points: List<Point2F>, point: Point2F): Point2F? {
        var closestPoint: Point2F? = null
        var minDist = Float.MAX_VALUE
        for (other in points) {
            val dist = point.distance2(other)
            if (dist < minDist || closestPoint == null) {
                closestPoint = other
                minDist = dist
            }
        }
        return closestPoint
    }

    fun add(point: Point2F) {
        val x = point.x * 4.0
        val y = point.y * 4.0
        val xIdx = x.toInt()
        val yIdx = y.toInt()
        val i = yIdx * 4 + xIdx
        var q1 = data[i]
        if (q1 == null) {
            q1 = Quadrant(q1Centers[i], null, null, null, null)
            data[i] = q1
        }
        val carries1 = Pair(x - xIdx, y - yIdx)
        val (q2, carries2) = getOrCreateNextQuadrant2(q1, carries1)
        val (q3, carries3) = getOrCreateNextQuadrant3(q2, carries2)
        val (q4, carries4) = getOrCreateNextQuadrant4(q3, carries3)
        val (q5, carries5) = getOrCreateNextQuadrant5(q4, carries4)
        getOrCreateNextQuadrant6(q5, carries5).second.add(point)
    }

    private fun getOrCreateNextQuadrant2(quadrant: Quadrant<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>?>, locals: Pair<Double, Double>): Pair<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>, Pair<Double, Double>> {
        var (nextQuadrant, carries, indices) = getNextQuadrant2(quadrant, locals.first, locals.second)
        if (nextQuadrant == null) {
            val center = Point2F(quadrant.center.x + ((if (indices.second == 0) -1 else 1) * q2Offset), quadrant.center.y + ((if (indices.third == 0) -1 else 1) * q2Offset))
            nextQuadrant = Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>(center, null, null, null, null)
            quadrant[indices.first] = nextQuadrant
        }
        return Pair(nextQuadrant, carries)
    }

    private fun getNextQuadrant2(quadrant: Quadrant<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>?>, xLocal: Double, yLocal: Double): Triple<Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>?, Pair<Double, Double>, Triple<Int, Int, Int>> {
        val x = xLocal * 2.0
        val y = yLocal * 2.0
        val xIdx = x.toInt()
        val yIdx = y.toInt()
        val xCarry = x - xIdx
        val yCarry = y - yIdx
        val i = yIdx * 2 + xIdx
        return Triple(quadrant[i], Pair(xCarry, yCarry), Triple(i, xIdx, yIdx))
    }

    private fun getOrCreateNextQuadrant3(quadrant: Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>, locals: Pair<Double, Double>): Pair<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>, Pair<Double, Double>> {
        var (nextQuadrant, carries, indices) = getNextQuadrant3(quadrant, locals.first, locals.second)
        if (nextQuadrant == null) {
            val center = Point2F(quadrant.center.x + ((if (indices.second == 0) -1 else 1) * q3Offset), quadrant.center.y + ((if (indices.third == 0) -1 else 1) * q3Offset))
            nextQuadrant = Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>(center, null, null, null, null)
            quadrant[indices.first] = nextQuadrant
        }
        return Pair(nextQuadrant, carries)
    }

    private fun getNextQuadrant3(quadrant: Quadrant<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?>, xLocal: Double, yLocal: Double): Triple<Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>?, Pair<Double, Double>, Triple<Int, Int, Int>> {
        val x = xLocal * 2.0
        val y = yLocal * 2.0
        val xIdx = x.toInt()
        val yIdx = y.toInt()
        val xCarry = x - xIdx
        val yCarry = y - yIdx
        val i = yIdx * 2 + xIdx
        return Triple(quadrant[i], Pair(xCarry, yCarry), Triple(i, xIdx, yIdx))
    }

    private fun getOrCreateNextQuadrant4(quadrant: Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>, locals: Pair<Double, Double>): Pair<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>, Pair<Double, Double>> {
        var (nextQuadrant, carries, indices) = getNextQuadrant4(quadrant, locals.first, locals.second)
        if (nextQuadrant == null) {
            val center = Point2F(quadrant.center.x + ((if (indices.second == 0) -1 else 1) * q4Offset), quadrant.center.y + ((if (indices.third == 0) -1 else 1) * q4Offset))
            nextQuadrant = Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>(center, null, null, null, null)
            quadrant[indices.first] = nextQuadrant
        }
        return Pair(nextQuadrant, carries)
    }

    private fun getNextQuadrant4(quadrant: Quadrant<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?>, xLocal: Double, yLocal: Double): Triple<Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>?, Pair<Double, Double>, Triple<Int, Int, Int>> {
        val x = xLocal * 2.0
        val y = yLocal * 2.0
        val xIdx = x.toInt()
        val yIdx = y.toInt()
        val xCarry = x - xIdx
        val yCarry = y - yIdx
        val i = yIdx * 2 + xIdx
        return Triple(quadrant[i], Pair(xCarry, yCarry), Triple(i, xIdx, yIdx))
    }

    private fun getOrCreateNextQuadrant5(quadrant: Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>, locals: Pair<Double, Double>): Pair<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>, Pair<Double, Double>> {
        var (nextQuadrant, carries, indices) = getNextQuadrant5(quadrant, locals.first, locals.second)
        if (nextQuadrant == null) {
            val center = Point2F(quadrant.center.x + ((if (indices.second == 0) -1 else 1) * q5Offset), quadrant.center.y + ((if (indices.third == 0) -1 else 1) * q5Offset))
            nextQuadrant = Quadrant<Pair<Point2F, ArrayList<Point2F>>?>(center, null, null, null, null)
            quadrant[indices.first] = nextQuadrant
        }
        return Pair(nextQuadrant, carries)
    }

    private fun getNextQuadrant5(quadrant: Quadrant<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?>, xLocal: Double, yLocal: Double): Triple<Quadrant<Pair<Point2F, ArrayList<Point2F>>?>?, Pair<Double, Double>, Triple<Int, Int, Int>> {
        val x = xLocal * 2.0
        val y = yLocal * 2.0
        val xIdx = x.toInt()
        val yIdx = y.toInt()
        val xCarry = x - xIdx
        val yCarry = y - yIdx
        val i = yIdx * 2 + xIdx
        return Triple(quadrant[i], Pair(xCarry, yCarry), Triple(i, xIdx, yIdx))
    }

    private fun getOrCreateNextQuadrant6(quadrant: Quadrant<Pair<Point2F, ArrayList<Point2F>>?>, locals: Pair<Double, Double>): Pair<Point2F, ArrayList<Point2F>> {
        var (nextQuadrant, indices) = getNextQuadrant6(quadrant, locals.first, locals.second)
        if (nextQuadrant == null) {
            val center = Point2F(quadrant.center.x + ((if (indices.second == 0) -1 else 1) * q6Offset), quadrant.center.y + ((if (indices.third == 0) -1 else 1) * q6Offset))
            nextQuadrant = Pair(center, ArrayList<Point2F>())
            quadrant[indices.first] = nextQuadrant
        }
        return nextQuadrant
    }

    private fun getNextQuadrant6(quadrant: Quadrant<Pair<Point2F, ArrayList<Point2F>>?>, xLocal: Double, yLocal: Double): Pair<Pair<Point2F, ArrayList<Point2F>>?, Triple<Int, Int, Int>> {
        val x = xLocal * 2.0
        val y = yLocal * 2.0
        val xIdx = x.toInt()
        val yIdx = y.toInt()
        val i = yIdx * 2 + xIdx
        return Pair(quadrant[i], Triple(i, xIdx, yIdx))
    }
}