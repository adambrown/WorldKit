package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow
import java.nio.channels.FileChannel

class BitMatrix(channel: FileChannel, mode: FileChannel.MapMode, override val exponent: Int, offset: Long): Matrix<Int> {

    override val width: Int = 2.pow(exponent)
    override val size = width.toLong().pow(2)

    private val data = RawData(1, size, channel, mode, offset)

    override fun set(i: Int, value: Int) {
        data[i] = if (value > 0) { 1 } else { 0 }
    }

    override fun get(i: Int): Int {
        return data[i][0].toInt()
    }

    override fun close() {
        data.close()
    }
}