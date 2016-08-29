package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow
import com.grimfox.gec.util.Utils.readUint24
import com.grimfox.gec.util.Utils.writeUint24
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger

abstract class ClosestPointsMatrix private constructor(channel: FileChannel, mode: FileChannel.MapMode, override val exponent: Int, offset: Long, private val pointCount: Int): Matrix<ClosestPoints> {

    class M2(channel: FileChannel, mode: FileChannel.MapMode, exponent: Int, offset: Long): ClosestPointsMatrix(channel, mode, exponent, offset, 2)

    class M3(channel: FileChannel, mode: FileChannel.MapMode, exponent: Int, offset: Long): ClosestPointsMatrix(channel, mode, exponent, offset, 3)

    class M4(channel: FileChannel, mode: FileChannel.MapMode, exponent: Int, offset: Long): ClosestPointsMatrix(channel, mode, exponent, offset, 4)

    class M5(channel: FileChannel, mode: FileChannel.MapMode, exponent: Int, offset: Long): ClosestPointsMatrix(channel, mode, exponent, offset, 5)

    private val ZERO_INT_FLOAT_PAIR = Pair(0, 0.0f)

    override val width: Int = 2.pow(exponent)
    override val size = width.toLong().pow(2)

    private val bytesPerFragment = ((pointCount * 7) + 1)
    private val bitsPerFragment = bytesPerFragment * 8

    private val data = RawData(bitsPerFragment, size, channel, mode, offset)

    override fun set(i: Int, value: ClosestPoints) {
        val buffer = ByteBuffer.wrap(ByteArray(bytesPerFragment)).order(ByteOrder.LITTLE_ENDIAN)
        val cursor = AtomicInteger(0)
        var nullMask = 0x1F
        for (j in 0..4) {
            if (value[j] == null) {
                nullMask = (0x01 shl j) xor nullMask
            }
        }
        fun writePair(pair: Pair<Int, Float>) {
            buffer.writeUint24(cursor.getAndAdd(3), pair.first)
            buffer.putFloat(cursor.getAndAdd(4), pair.second)
        }
        buffer.put(cursor.getAndAdd(1), nullMask.toByte())
        for (j in 0..pointCount - 1) {
            writePair(value[j] ?: ZERO_INT_FLOAT_PAIR)
        }
        data[i] = buffer.array()
    }

    override fun get(i: Int): ClosestPoints {
        val buffer = ByteBuffer.wrap(data[i]).order(ByteOrder.LITTLE_ENDIAN)
        val cursor = AtomicInteger(0)
        val counter = AtomicInteger(0)
        val nullMask = buffer.get(cursor.andIncrement).toInt()
        fun readPair(): Pair<Int, Float>? {
            val pair = Pair(buffer.readUint24(cursor.getAndAdd(3)), buffer.getFloat(cursor.getAndAdd(4)))
            val mask = (0x01 shl counter.andIncrement)
            if (mask and nullMask != 0) {
                return pair
            } else {
                return null
            }
        }
        val points = ClosestPoints()
        for (j in 0..pointCount - 1) {
            points[j] = readPair()
        }
        return points
    }

    override fun close() {
        data.close()
    }
}