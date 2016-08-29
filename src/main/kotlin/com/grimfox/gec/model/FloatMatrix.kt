package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class FloatMatrix(channel: FileChannel, mode: FileChannel.MapMode, override val exponent: Int, offset: Long): Matrix<Float> {

    override val width: Int = 2.pow(exponent)
    override val size = width.toLong().pow(2)

    private val data = RawData(32, size, channel, mode, offset)

    override fun set(i: Int, value: Float) {
        val buffer = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(0, value)
        data[i] = buffer.array()
    }

    override fun get(i: Int): Float {
        val buffer = ByteBuffer.wrap(data[i]).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.getFloat(0)
    }

    override fun close() {
        data.close()
    }
}