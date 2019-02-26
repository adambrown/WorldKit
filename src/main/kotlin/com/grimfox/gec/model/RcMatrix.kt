package com.grimfox.gec.model

import java.io.*
import java.lang.Math.sqrt
import java.util.zip.*
import kotlin.Float.Companion.NaN
import kotlin.math.max

class RcMatrix(val rows: Int, val columns: Int, array: FloatArray? = null, init: ((Int) -> Float)? = null){

    companion object {
        val ALL = -1..-1

        fun read(file: File) = DataInputStream(GZIPInputStream(file.inputStream().buffered()).buffered()).use { read(it) }

        fun read(input: DataInputStream): RcMatrix {
                val rows = input.readInt()
                val columns = input.readInt()
                return RcMatrix(rows, columns) {
                    input.readFloat()
                }
        }
    }

    val size = rows * columns

    val array = array ?: if (init != null) FloatArray(size, init) else FloatArray(size)

    val allRows = 0 until rows

    val allColumns = 0 until columns

    operator fun set(i: Int, value: Float) {
        array[i] = value
    }

    operator fun set(row: Int, column: Int, value: Float) = set(row * columns + column, value)

    operator fun set(rowRange: IntRange, columnRange: IntRange, values: RcMatrix) {
        (if (rowRange === ALL) allRows else rowRange).forEachIndexed { rowIndex, row ->
            (if (columnRange === ALL) allColumns else columnRange).forEachIndexed { columnIndex, column ->
                set(row, column, values[rowIndex, columnIndex])
            }
        }
    }

    operator fun set(rowRange: IntRange, column: Int, values: RcMatrix) = set(rowRange, column..column, values)

    operator fun set(row: Int, columnRange: IntRange, values: RcMatrix) = set(row..row, columnRange, values)

    operator fun get(i: Int) = array[i]

    operator fun get(row: Int, column: Int) = get(row * columns + column)

    operator fun get(rowRange: IntRange, columnRange: IntRange): RcMatrix {
        val actualRowRange = if (rowRange === ALL) allRows else rowRange
        val actualColumnRange = if (columnRange === ALL) allColumns else columnRange
        val output = RcMatrix(max(0, actualRowRange.last - actualRowRange.first + 1), max(0, actualColumnRange.last - actualColumnRange.first + 1))
        actualRowRange.forEachIndexed { rowIndex, row ->
            actualColumnRange.forEachIndexed { columnIndex, column ->
                output[rowIndex, columnIndex] = get(row, column)
            }
        }
        return output
    }

    operator fun get(rowRange: IntRange, columnRange: IntRange, alpha: Float): RcMatrix {
        val actualRowRange = if (rowRange === ALL) allRows else rowRange
        val actualColumnRange = if (columnRange === ALL) allColumns else columnRange
        val output = RcMatrix(max(0, actualRowRange.last - actualRowRange.first + 1), max(0, actualColumnRange.last - actualColumnRange.first + 1))
        actualRowRange.forEachIndexed { rowIndex, row ->
            actualColumnRange.forEachIndexed { columnIndex, column ->
                output[rowIndex, columnIndex] = get(row, column) * alpha
            }
        }
        return output
    }

    operator fun get(row: Int, columnRange: IntRange) = get(row..row, columnRange)

    operator fun get(rowRange: IntRange, column: Int) = get(rowRange, column..column)

    operator fun plus(other: RcMatrix): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (i in 0 until size) {
            output[i] = get(i) + other[i]
        }
        return output
    }

    operator fun plus(scalar: Float): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (i in 0 until size) {
            output[i] = get(i) + scalar
        }
        return output
    }

    operator fun minus(other: RcMatrix): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (i in 0 until size) {
            output[i] = get(i) - other[i]
        }
        return output
    }

    operator fun minus(scalar: Float): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (i in 0 until size) {
            output[i] = get(i) - scalar
        }
        return output
    }

    operator fun times(other: RcMatrix): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (i in 0 until size) {
            output[i] = get(i) * other[i]
        }
        return output
    }

    operator fun times(scalar: Float): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (i in 0 until size) {
            output[i] = get(i) * scalar
        }
        return output
    }

    operator fun div(other: RcMatrix): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (i in 0 until size) {
            output[i] = get(i) / other[i]
        }
        return output
    }

    operator fun div(scalar: Float): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (i in 0 until size) {
            output[i] = get(i) / scalar
        }
        return output
    }

    fun mean(rowRange: IntRange, columnRange: IntRange): Float {
        val actualRowRange = if (rowRange === ALL) allRows else rowRange
        val actualColumnRange = if (columnRange === ALL) allColumns else columnRange
        var sum = 0.0
        var count = 0
        actualRowRange.forEach { row ->
            actualColumnRange.forEach{ column ->
                sum += get(row, column)
                count++
            }
        }
        return if (count == 0) NaN else (sum / count).toFloat()
    }

    fun sum(rowRange: IntRange, columnRange: IntRange): Float {
        val actualRowRange = if (rowRange === ALL) allRows else rowRange
        val actualColumnRange = if (columnRange === ALL) allColumns else columnRange
        var sum = 0.0
        actualRowRange.forEach { row ->
            actualColumnRange.forEach{ column ->
                sum += get(row, column)
            }
        }
        return sum.toFloat()
    }

    fun sum(row: Int, columnRange: IntRange): Float {
        val actualColumnRange = if (columnRange === ALL) allColumns else columnRange
        var sum = 0.0
        val offset = row * columns
        actualColumnRange.forEach{ column ->
            sum += get(offset + column)
        }
        return sum.toFloat()
    }

    fun sum(rowRange: IntRange, column: Int): Float {
        val actualRowRange = if (rowRange === ALL) allRows else rowRange
        var sum = 0.0
        actualRowRange.forEach { row ->
            sum += get(row, column)
        }
        return sum.toFloat()
    }

    fun mean(): Float = array.average().toFloat()

    fun min() = array.min() ?: NaN

    fun max() = array.max() ?: NaN

    fun transpose(): RcMatrix {
        val output = RcMatrix(columns, rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                output[column, row] = get(row, column)
            }
        }
        return output
    }

    fun asyncTranspose(): RcMatrix {
        val output = RcMatrix(columns, rows)
        (0 until rows).toList().parallelStream().forEach { row ->
            (0 until columns).forEach { column ->
                output[column, row] = get(row, column)
            }
        }
        return output
    }

    fun norm(row: Int, columnRange: IntRange): Float {
        val actualColumnRange = if (columnRange === ALL) allColumns else columnRange
        var sum = 0.0
        for (column in actualColumnRange) {
            val value = get(row, column)
            sum += value * value
        }
        return sqrt(sum).toFloat()
    }

    fun norm(rowRange: IntRange, column: Int): Float {
        val actualRowRange = if (rowRange === ALL) allRows else rowRange
        var sum = 0.0
        for (row in actualRowRange) {
            val value = get(row, column)
            sum += value * value
        }
        return sqrt(sum).toFloat()
    }

    fun reshape(rows: Int, columns: Int): RcMatrix {
        val output = RcMatrix(rows, columns)
        for (row in 0 until rows) {
            val offset = row * columns
            for (column in 0 until columns) {
                val j = column * columns + row
                output[offset + column] = get(j % this.rows, j / this.rows)
            }
        }
        return output
    }

    fun matrixMultiply(other: RcMatrix): RcMatrix {
        if (columns != other.rows) {
            throw IllegalArgumentException("A.Columns: $columns did not match B.Rows ${other.rows}.")
        }
        val output = RcMatrix(rows, other.columns)
        for (i in 0 until rows) {
            for (k in 0 until columns) {
                val ik = get(i, k)
                if (ik == 0.0f) continue
                for (j in 0 until other.columns) {
                    output[i, j] = output[i, j] + ik * other[k, j]
                }
            }
        }
        return output
    }

    fun rotate90Clockwise() {
        if (rows != columns) {
            throw IllegalArgumentException("Rows must equal columns.")
        }
        val a = this
        for (i in 0 until rows / 2) {
            for (j in i until rows - i - 1) {
                val rowsM1 = rows - 1
                val rowsM1Mi = rowsM1 - i
                val rowsM1Mj = rowsM1 - j
                val temp = a[i, j]
                a[i, j] = a[rowsM1Mj, i]
                a[rowsM1Mj, i] = a[rowsM1Mi, rowsM1Mj]
                a[rowsM1Mi, rowsM1Mj] = a[j, rowsM1Mi]
                a[j, rowsM1Mi] = temp
            }
        }
    }

    fun asyncMatrixMultiply(other: RcMatrix): RcMatrix {
        if (columns != other.rows) {
            throw IllegalArgumentException("A.Columns: $columns did not match B.Rows ${other.rows}.")
        }
        val output = RcMatrix(rows, other.columns)
        (0 until rows).toList().parallelStream().forEach { i ->
            for (k in 0 until columns) {
                val ik = get(i, k)
                if (ik == 0.0f) continue
                for (j in 0 until other.columns) {
                    output[i, j] = output[i, j] + ik * other[k, j]
                }
            }
        }
        return output
    }

    fun write(file: File) = DataOutputStream(GZIPOutputStream(file.outputStream().buffered()).buffered()).use { write(it) }

    fun write(output: DataOutputStream) {
        output.writeInt(rows)
        output.writeInt(columns)
        array.forEach(output::writeFloat)
    }
}