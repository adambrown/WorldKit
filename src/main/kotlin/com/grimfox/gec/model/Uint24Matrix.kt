package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow
import com.grimfox.gec.util.Utils.readUint24
import com.grimfox.gec.util.Utils.writeUint24
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class Uint24Matrix(channel: FileChannel, mode: FileChannel.MapMode, override val exponent: Int, offset: Long): Matrix<Int> {

    override val width: Int = 2.pow(exponent)
    override val size = width.toLong().pow(2)

    private val data = RawData(24, size, channel, mode, offset)

    override fun set(i: Int, value: Int) {
        val buffer = ByteBuffer.wrap(ByteArray(3)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.writeUint24(0, value)
        data[i] = buffer.array()
    }

    override fun get(i: Int): Int {
        val buffer = ByteBuffer.wrap(data[i]).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.readUint24(0)
    }

    override fun close() {
        data.close()
    }
}