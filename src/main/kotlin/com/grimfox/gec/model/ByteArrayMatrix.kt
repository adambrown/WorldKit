package com.grimfox.gec.model

import java.io.*

class ByteArrayMatrix(override val width: Int, array: ByteArray? = null, init: ((Int) -> Byte)? = null) : Matrix<Byte> {

    companion object {

        fun deserialize(input: DataInputStream): ByteArrayMatrix {
            val maskWidth = input.readInt()
            val maskBytes = ByteArray(maskWidth * maskWidth)
            input.readFully(maskBytes)
            return ByteArrayMatrix(maskWidth, maskBytes)
        }
    }

    override val size = width.toLong() * width

    val array = array ?: if (init != null) ByteArray(size.toInt(), init) else ByteArray(size.toInt())

    override fun set(i: Int, value: Byte) {
        array[i] = value
    }

    override fun get(i: Int): Byte {
        return array[i]
    }

    override fun close() {
    }

    fun serialize(output: DataOutputStream) {
        output.writeInt(width)
        output.write(array)
    }
}