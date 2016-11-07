package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class FloatArrayMatrix(override val width: Int, init: (Int) -> Float = { 0.0f }) : Matrix<Float> {

    override val exponent: Int get() = throw NotImplementedException()
    override val size = width.toLong().pow(2)

    val array = FloatArray(size.toInt(), init)

    override fun set(i: Int, value: Float) {
        array[i] = value
    }

    override fun get(i: Int): Float {
        return array[i]
    }

    override fun close() {
    }
}