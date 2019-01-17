package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow

class DoubleArrayMatrix(override val width: Int, array: DoubleArray? = null, init: ((Int) -> Double)? = null) : Matrix<Double> {

    override val size = width.toLong().pow(2)

    val array = array ?: if (init != null) DoubleArray(size.toInt(), init) else DoubleArray(size.toInt())

    override fun set(i: Int, value: Double) {
        array[i] = value
    }

    override fun get(i: Int): Double {
        return array[i]
    }

    override fun close() {
    }
}