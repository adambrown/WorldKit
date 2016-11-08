package com.grimfox.gec.model.geometry

import java.lang.Math.*
import java.util.*

class SpatialPointSet2F() {

    companion object {
        private val levels = 7
        private val baseCenters = centersForLayer(4)
        private val widths = IntArray(levels) {i -> round(pow(2.0, i.toDouble())).toInt() * 4 }
        private val offsets = FloatArray(levels) { i -> offsetForLayer(widths[i]) }
        private val diagonals = FloatArray(levels) { i -> diagonalForLayer(widths[i]) }

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

    interface Quadrant {
        val center: Point2F
        var a: Quadrant?
        var b: Quadrant?
        var c: Quadrant?
        var d: Quadrant?
    }

    class QuadrantBranch(override val center: Point2F, override var a: Quadrant? = null, override var b: Quadrant? = null, override var c: Quadrant? = null, override var d: Quadrant? = null): Quadrant

    class QuadrantLeaf(override val center: Point2F, val points: ArrayList<Point2F> = ArrayList()): Quadrant {
        override var a: Quadrant?
            get() = null
            set(value) {
            }
        override var b: Quadrant?
            get() = null
            set(value) {
            }
        override var c: Quadrant?
            get() = null
            set(value) {
            }
        override var d: Quadrant?
            get() = null
            set(value) {
            }

    }

    private val data = Array<Quadrant?>(16) { null }

    fun closestPoint(point: Point2F): Point2F? {
        return pickClosest(closestPoints(point), point)
    }

    fun closestPoints(point: Point2F): List<Point2F> {
        val quadsToCheck = ArrayList<Pair<Quadrant, Float>>()
        var nextMinDist2 = Float.MAX_VALUE
        for (quadrant in data) {
            if (quadrant != null) {
                val distance = point.distance2(quadrant.center)
                quadsToCheck.add(Pair(quadrant, distance))
                if (distance < nextMinDist2) {
                    nextMinDist2 = distance
                }
            }
        }
        nextMinDist2 = (sqrt(nextMinDist2.toDouble()) + diagonals[0]).toFloat()
        nextMinDist2 *= nextMinDist2
        return checkQuads(quadsToCheck, 1, nextMinDist2, point)
    }

    private fun checkQuads(quads: Collection<Pair<Quadrant, Float>>, level: Int, minDist2: Float, point: Point2F): List<Point2F> {
        val quadsToCheck = ArrayList<Pair<Quadrant, Float>>()
        var nextMinDist2 = Float.MAX_VALUE
        for (pair in quads) {
            if (pair.second <= minDist2) {
                val quadrant = pair.first
                val qa = quadrant.a
                if (qa != null) {
                    val distance = point.distance2(qa.center)
                    quadsToCheck.add(Pair(qa, distance))
                    if (distance < nextMinDist2) {
                        nextMinDist2 = distance
                    }
                }
                val qb = quadrant.b
                if (qb != null) {
                    val distance = point.distance2(qb.center)
                    quadsToCheck.add(Pair(qb, distance))
                    if (distance < nextMinDist2) {
                        nextMinDist2 = distance
                    }
                }
                val qc = quadrant.c
                if (qc != null) {
                    val distance = point.distance2(qc.center)
                    quadsToCheck.add(Pair(qc, distance))
                    if (distance < nextMinDist2) {
                        nextMinDist2 = distance
                    }
                }
                val qd = quadrant.d
                if (qd != null) {
                    val distance = point.distance2(qd.center)
                    quadsToCheck.add(Pair(qd, distance))
                    if (distance < nextMinDist2) {
                        nextMinDist2 = distance
                    }
                }
            }
        }
        nextMinDist2 = (sqrt(nextMinDist2.toDouble()) + diagonals[level]).toFloat()
        nextMinDist2 *= nextMinDist2
        if (level < levels - 1) {
            return checkQuads(quadsToCheck, level + 1, nextMinDist2, point)
        } else {
            val points = ArrayList<Point2F>()
            quadsToCheck.forEach {
                if (it.second <= nextMinDist2) {
                    points.addAll((it.first as QuadrantLeaf).points)
                }
            }
            return points
        }
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
        val i = (point.y * 4.0).toInt() * 4 + (point.x * 4.0).toInt()
        var quadrant = data[i]
        if (quadrant == null) {
            quadrant = QuadrantBranch(baseCenters[i])
            data[i] = quadrant
        }
        for (j in 1..levels - 1) {
            quadrant = getOrCreateNextQuadrant(quadrant!!, j, point)
        }
        (quadrant as QuadrantLeaf).points.add(point)
    }

    private fun getOrCreateNextQuadrant(quadrant: Quadrant, level: Int, point: Point2F): Quadrant {
        val width = widths[level]
        val x = (point.x * width).toInt()
        val y = (point.y * width).toInt()
        val xIdx = x % 2
        val yIdx = y % 2
        val i = yIdx * 2 + xIdx
        var nextQuadrant = when (i) {
            0 -> quadrant.a
            1 -> quadrant.b
            2 -> quadrant.c
            else -> quadrant.d
        }
        if (nextQuadrant == null) {
            val center = Point2F(quadrant.center.x + ((if (xIdx == 0) -1 else 1) * offsets[level]), quadrant.center.y + ((if (yIdx == 0) -1 else 1) * offsets[level]))
            nextQuadrant = if (level == levels - 1) {
                QuadrantLeaf(center)
            } else {
                QuadrantBranch(center)
            }
            when (i) {
                0 -> quadrant.a = nextQuadrant
                1 -> quadrant.b = nextQuadrant
                2 -> quadrant.c = nextQuadrant
                else -> quadrant.d = nextQuadrant
            }
        }
        return nextQuadrant
    }
}