package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow
import java.nio.ByteBuffer

class ByteBufferMatrix(override val width: Int, buffer: ByteBuffer? = null,  init: ((Int) -> Byte)? = null) : Matrix<Byte> {

    override val size = width.toLong().pow(2)

    val buffer: ByteBuffer = buffer ?: ByteBuffer.wrap(if (init != null) ByteArray(size.toInt(), init) else ByteArray(size.toInt()))

    override fun set(i: Int, value: Byte) {
        buffer.put(i, value)
    }

    override fun get(i: Int): Byte {
        return buffer[i]
    }

    override fun close() {
    }
}