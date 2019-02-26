package com.grimfox.gec.model

class Coefficients(val rows: Int, val columns: Int) {

    private val kArray = IntArray(rows) { -1 }

    private val cArray = FloatArray(rows)

    val reverseIndex by lazy {
        val reverseIndex = List(columns) { ArrayList<Int>() }
        kArray.forEachIndexed { i, it ->
            reverseIndex[it].add(i)
        }
        reverseIndex as List<List<Int>>
    }

    operator fun set(row: Int, column: Int, value: Float) {
        kArray[row] = column
        cArray[row] = value
    }

    operator fun get(row: Int, column: Int, other: RcMatrix): Float {
        val k = kArray[row]
        if (k == -1) {
            return 0.0f
        }
        return cArray[row] * other[k, column]
    }
}