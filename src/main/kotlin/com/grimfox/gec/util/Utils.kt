package com.grimfox.gec.util

import com.grimfox.gec.Main
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*

object Utils {

    val LOG = LoggerFactory.getLogger(Main::class.java)

    fun Int.pow(exponent: Int): Int {
        val value = BigInteger.valueOf(this.toLong()).pow(exponent)
        if (value > BigInteger.valueOf(Int.MAX_VALUE.toLong())) throw IllegalStateException("Value too large for int: $value")
        return value.toInt()
    }

    fun Long.pow(exponent: Int): Long {
        val value = BigInteger.valueOf(this).pow(exponent)
        if (value > BigInteger.valueOf(Long.MAX_VALUE)) throw IllegalStateException("Value too large for long: $value")
        return value.toLong()
    }

    fun BigInteger.pow(exponent: Int): BigInteger {
        var value = BigInteger.valueOf(1)
        for (i in 1..exponent) {
            value *= this
        }
        return value
    }

    fun ByteBuffer.disposeDirect() {
        if (!isDirect) return
        var logFail = true
        if (LOG.isDebugEnabled) {
            LOG.debug("Disposing of direct buffer.")
        }
        try {
            val cleaner = javaClass.getMethod("cleaner") ?: return
            cleaner.isAccessible = true
            val clean = Class.forName("sun.misc.Cleaner").getMethod("clean") ?: return
            clean.isAccessible = true
            clean.invoke(cleaner.invoke(this))
            logFail = false
        } catch(e: Exception) {
            if (LOG.isDebugEnabled) {
                LOG.debug("Failed to dispose of direct buffer.", e)
            }
            logFail = false
        } finally {
            if (logFail) {
                if (LOG.isDebugEnabled) {
                    LOG.debug("Failed to dispose of direct buffer.")
                }
            } else {
                if (LOG.isDebugEnabled) {
                    LOG.debug("Disposed of direct buffer.")
                }
            }
        }
    }

    fun FileChannel.mapChunks(mode: FileChannel.MapMode, offset: Int, chunkSize: Int, chunkCount: Int): MutableList<ByteBuffer> {
        val chunks = ArrayList<ByteBuffer>()
        for (i in 0..(chunkCount - 1)) {
            chunks.add(map(mode, offset + (i * chunkSize.toLong()), chunkSize.toLong()).order(ByteOrder.LITTLE_ENDIAN))
        }
        return chunks
    }

    fun FileChannel.imageSizeAsExp2(bytesPerPixel: Int, metaBytes: Int): Int {
        return exp2FromSize(Math.sqrt((size() - metaBytes) / bytesPerPixel.toDouble()).toInt())
    }

    fun exp2FromSize(size: Int): Int {
        var count = 0
        var currentDiv = size
        while (currentDiv > 1) {
            currentDiv /= 2
            count++
        }
        return count
    }

    fun FileChannel.MapMode.toRandomAccessFileMode(): String {
        return when (this) {
            FileChannel.MapMode.READ_ONLY -> "r"
            else -> "rw"
        }
    }

    private val primitivesToWrappers = mapOf<Class<*>, Class<*>>(
            Pair(Boolean::class.java, java.lang.Boolean::class.java),
            Pair(Byte::class.java, java.lang.Byte::class.java),
            Pair(Char::class.java, java.lang.Character::class.java),
            Pair(Double::class.java, java.lang.Double::class.java),
            Pair(Float::class.java, java.lang.Float::class.java),
            Pair(Int::class.java, java.lang.Integer::class.java),
            Pair(Long::class.java, java.lang.Long::class.java),
            Pair(Short::class.java, java.lang.Short::class.java))

    fun primitiveToWrapper(type: Class<*>): Class<*> {
        if (type.isPrimitive) {
            val possible = primitivesToWrappers[type]
            if (possible != null) {
                return possible
            }
        }
        return type
    }

    fun FileChannel.writeInt(position: Long, value: Int) {
        val buffer = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, value)
        write(buffer, position)
    }

    fun FileChannel.readInt(position: Long): Int {
        val buffer = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)
        read(buffer, position)
        return buffer.getInt(0)
    }

    fun ByteBuffer.readUint24(position: Int): Int {
        return (get(position).toInt() and 0xFF) + ((get(position + 1).toInt() and 0xFF) shl 8) + ((get(position + 2).toInt() and 0xFF) shl 16)
    }

    fun ByteBuffer.writeUint24(position: Int, value: Int) {
        put(position, (value and 0xFF).toByte())
        put(position + 1, ((value ushr 8) and 0xFF).toByte())
        put(position + 2, ((value ushr 16) and 0xFF).toByte())
    }
}