package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class ByteArrayMatrix(override val width: Int, init: ((Int) -> Byte)? = null) : Matrix<Byte> {

    override val exponent: Int get() = throw NotImplementedException()
    override val size = width.toLong().pow(2)

    val array = if (init != null) ByteArray(size.toInt(), init) else ByteArray(size.toInt())

    override fun set(i: Int, value: Byte) {
        array[i] = value
    }

    override fun get(i: Int): Byte {
        return array[i]
    }

    override fun close() {
    }
}