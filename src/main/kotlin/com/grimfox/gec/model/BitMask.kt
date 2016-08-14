package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.disposeDirect
import com.grimfox.gec.util.Utils.pow
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class BitMask(mode: FileChannel.MapMode, private val channel: FileChannel, exponent: Int) {

    private val BITMASK_TRUE = 0x0080
    private val BITMASK_FALSE = 0xFF7F

    val stride: Int = 2.pow(exponent)
    val size = stride.pow(2)
    val buffer: ByteBuffer = channel.map(mode, 0, (size / 8).toLong())

    operator fun set(x: Int, y: Int, value: Boolean) {
        val index = (y * stride + x)
        val byte = index / 8
        val bit = index % 8
        var currentVal = buffer.get(byte).toInt()
        if (value) {
            currentVal = currentVal or (BITMASK_TRUE ushr bit)
        } else {
            currentVal = currentVal and (BITMASK_FALSE ushr bit)
        }
        buffer.put(byte, currentVal.toByte())
    }

    operator fun get(x: Int, y: Int): Boolean {
        val index = (y * stride + x)
        val byte = index / 8
        val bit = index % 8
        val value = buffer.get(byte)
        return (BITMASK_TRUE ushr bit) and value.toInt() != 0
    }

    fun close() {
        try {
            channel.close()
        } finally {
            buffer.disposeDirect()
        }
    }

}