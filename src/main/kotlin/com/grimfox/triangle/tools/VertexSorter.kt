package com.grimfox.triangle.tools

import com.grimfox.triangle.geometry.Vertex
import java.util.*

class VertexSorter internal constructor(
        internal var points: Array<Vertex>,
        seed: Long) {

    companion object {

        private const val RANDOM_SEED = 57113L

        fun sort(array: Array<Vertex>, seed: Long = RANDOM_SEED) {
            val qs = VertexSorter(array, seed)
            qs.quickSort(0, array.size - 1)
        }

        fun alternate(array: Array<Vertex>, length: Int, seed: Long = RANDOM_SEED) {
            val qs = VertexSorter(array, seed)
            val divider = length shr 1
            if (length - divider >= 2) {
                if (divider >= 2) {
                    qs.alternateAxes(0, divider - 1, 1)
                }
                qs.alternateAxes(divider, length - 1, 1)
            }
        }
    }

    private val rand: Random = Random(seed)

    private fun quickSort(left: Int, right: Int) {
        var newLeft = left
        var newRight = right
        val oleft = newLeft
        val oright = newRight
        val arraysize = newRight - newLeft + 1
        val pivot: Int
        val pivotx: Double
        val pivoty: Double
        var temp: Vertex
        val array = this.points
        if (arraysize < 32) {
            for (i in newLeft + 1..newRight) {
                val a = array[i]
                var j = i - 1
                while (j >= newLeft && (array[j].x > a.x || array[j].x == a.x && array[j].y > a.y)) {
                    array[j + 1] = array[j]
                    j--
                }
                array[j + 1] = a
            }
            return
        }
        val delta = newRight - newLeft
        pivot = rand.nextInt(delta) + newLeft
        pivotx = array[pivot].x
        pivoty = array[pivot].y
        newLeft--
        newRight++
        while (newLeft < newRight) {
            do {
                newLeft++
            } while (newLeft <= newRight && (array[newLeft].x < pivotx || array[newLeft].x == pivotx && array[newLeft].y < pivoty))
            do {
                newRight--
            } while (newLeft <= newRight && (array[newRight].x > pivotx || array[newRight].x == pivotx && array[newRight].y > pivoty))
            if (newLeft < newRight) {
                temp = array[newLeft]
                array[newLeft] = array[newRight]
                array[newRight] = temp
            }
        }
        if (newLeft > oleft) {
            quickSort(oleft, newLeft)
        }
        if (oright > newRight + 1) {
            quickSort(newRight + 1, oright)
        }
    }

    private fun alternateAxes(left: Int, right: Int, axis: Int) {
        var newAxis = axis
        val size = right - left + 1
        val divider = size shr 1
        if (size <= 3) {
            newAxis = 0
        }
        if (newAxis == 0) {
            vertexMedianX(left, right, left + divider)
        } else {
            vertexMedianY(left, right, left + divider)
        }
        if (size - divider >= 2) {
            if (divider >= 2) {
                alternateAxes(left, left + divider - 1, 1 - newAxis)
            }
            alternateAxes(left + divider, right, 1 - newAxis)
        }
    }

    private fun vertexMedianX(left: Int, right: Int, median: Int) {
        var newLeft = left
        var newRight = right
        val arraysize = newRight - newLeft + 1
        val oleft = newLeft
        val oright = newRight
        val pivot: Int
        val pivot1: Double
        val pivot2: Double
        var temp: Vertex
        val array = this.points
        if (arraysize == 2) {
            if (array[newLeft].x > array[newRight].x || array[newLeft].x == array[newRight].x && array[newLeft].y > array[newRight].y) {
                temp = array[newRight]
                array[newRight] = array[newLeft]
                array[newLeft] = temp
            }
            return
        }
        val delta = newRight - newLeft
        pivot = rand.nextInt(delta) + newLeft
        pivot1 = array[pivot].x
        pivot2 = array[pivot].y
        newLeft--
        newRight++
        while (newLeft < newRight) {
            do {
                newLeft++
            } while (newLeft <= newRight && (array[newLeft].x < pivot1 || array[newLeft].x == pivot1 && array[newLeft].y < pivot2))
            do {
                newRight--
            } while (newLeft <= newRight && (array[newRight].x > pivot1 || array[newRight].x == pivot1 && array[newRight].y > pivot2))
            if (newLeft < newRight) {
                temp = array[newLeft]
                array[newLeft] = array[newRight]
                array[newRight] = temp
            }
        }
        if (newLeft > median) {
            vertexMedianX(oleft, newLeft - 1, median)
        }
        if (newRight < median - 1) {
            vertexMedianX(newRight + 1, oright, median)
        }
    }

    private fun vertexMedianY(left: Int, right: Int, median: Int) {
        var newLeft = left
        var newRight = right
        val arraysize = newRight - newLeft + 1
        val oleft = newLeft
        val oright = newRight
        val pivot: Int
        val pivot1: Double
        val pivot2: Double
        var temp: Vertex
        val array = this.points
        if (arraysize == 2) {
            if (array[newLeft].y > array[newRight].y || array[newLeft].y == array[newRight].y && array[newLeft].x > array[newRight].x) {
                temp = array[newRight]
                array[newRight] = array[newLeft]
                array[newLeft] = temp
            }
            return
        }
        val delta = newRight - newLeft
        pivot = rand.nextInt(delta) + newLeft
        pivot1 = array[pivot].y
        pivot2 = array[pivot].x
        newLeft--
        newRight++
        while (newLeft < newRight) {
            do {
                newLeft++
            } while (newLeft <= newRight && (array[newLeft].y < pivot1 || array[newLeft].y == pivot1 && array[newLeft].x < pivot2))
            do {
                newRight--
            } while (newLeft <= newRight && (array[newRight].y > pivot1 || array[newRight].y == pivot1 && array[newRight].x > pivot2))
            if (newLeft < newRight) {
                temp = array[newLeft]
                array[newLeft] = array[newRight]
                array[newRight] = temp
            }
        }
        if (newLeft > median) {
            vertexMedianY(oleft, newLeft - 1, median)
        }
        if (newRight < median - 1) {
            vertexMedianY(newRight + 1, oright, median)
        }
    }
}
