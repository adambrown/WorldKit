package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow

class ShortArrayMatrix(override val width: Int, array: ShortArray? = null, init: ((Int) -> Short)? = null) : Matrix<Short> {

    override val size = width.toLong().pow(2)

    val array = array ?: if (init != null) ShortArray(size.toInt(), init) else ShortArray(size.toInt())

    override fun set(i: Int, value: Short) {
        array[i] = value
    }

    override fun get(i: Int): Short {
        return array[i]
    }

    override fun close() {
    }
}