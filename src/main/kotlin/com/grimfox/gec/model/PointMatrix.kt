package com.grimfox.gec.model

import com.grimfox.gec.generator.Point
import com.grimfox.gec.util.Utils.pow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class PointMatrix(channel: FileChannel, mode: FileChannel.MapMode, override val exponent: Int, offset: Long): Matrix<Point> {

    override val width: Int = 2.pow(exponent)
    override val size = width.toLong().pow(2)

    private val data = RawData(64, size, channel, mode, offset)

    override fun set(i: Int, value: Point) {
        val buffer = ByteBuffer.wrap(ByteArray(8)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(0, value.x)
        buffer.putFloat(4, value.y)
        data[i] = buffer.array()
    }

    override fun get(i: Int): Point {
        val buffer = ByteBuffer.wrap(data[i]).order(ByteOrder.LITTLE_ENDIAN)
        return Point(buffer.getFloat(0), buffer.getFloat(4))
    }

    override fun close() {
        data.close()
    }
}