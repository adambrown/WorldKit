package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow

class IntArrayMatrix(override val width: Int, init: ((Int) -> Int)? = null) : Matrix<Int> {

    override val exponent: Int get() = throw UnsupportedOperationException()
    override val size = width.toLong().pow(2)

    val array = if (init != null) IntArray(size.toInt(), init) else IntArray(size.toInt())

    override fun set(i: Int, value: Int) {
        array[i] = value
    }

    override fun get(i: Int): Int {
        return array[i]
    }

    override fun close() {
    }
}