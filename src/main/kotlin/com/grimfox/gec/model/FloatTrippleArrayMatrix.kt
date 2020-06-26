package com.grimfox.gec.model

class FloatTripleArrayMatrix(override val width: Int, array: FloatArray? = null) : Matrix<Triple<Float, Float, Float>> {

    override val size = width.toLong() * width.toLong()

    val array = array ?: FloatArray(size.toInt() * 3)

    override fun set(i: Int, value: Triple<Float, Float, Float>) {
        val startIndex = i * 3
        array[startIndex] = value.first
        array[startIndex + 1] = value.second
        array[startIndex + 2] = value.third
    }

    override fun get(i: Int): Triple<Float, Float, Float> {
        val startIndex = i * 3
        return Triple(array[startIndex], array[startIndex + 1], array[startIndex + 2])
    }

    override fun close() {
    }
}