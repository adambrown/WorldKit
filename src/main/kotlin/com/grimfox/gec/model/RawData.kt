package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.disposeDirect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*

class RawData(private val bitsPerFragment: Int, fragmentCount: Long, channel: FileChannel, mode: FileChannel.MapMode, offset: Long) {

    companion object {
        private val MAX_CHUNK_SIZE = 268435456

        private val ONE_BIT_MASK = 0x1
        private val TWO_BIT_MASK = 0x3
        private val FOUR_BIT_MASK = 0xF

        private fun calculateFragmentsPerChunk(bitsPerFragment: Int): Long {
            val maxBitSize = MAX_CHUNK_SIZE.toLong() * 8
            return maxBitSize / bitsPerFragment
        }

        private fun calculateFragmentAlignedChunkSize(bitsPerFragment: Int, fragmentsPerChunk: Long): Int {
            val bitsUsedPerChunk = fragmentsPerChunk * bitsPerFragment
            if (bitsUsedPerChunk % 8 > 0) {
                return ((bitsUsedPerChunk / 8) + 1).toInt()
            } else {
                return (bitsUsedPerChunk / 8).toInt()
            }
        }

        private fun calculateLastChunkSize(bitsPerFragment: Int, fragmentsPerChunk: Long, fragmentCount: Long): Int {
            val remainder = fragmentCount % fragmentsPerChunk
            val fragmentSize = if (remainder == 0L) {
                if (fragmentCount == 0L) { 0L } else { fragmentsPerChunk }
            } else {
                remainder
            }
            val totalBits = fragmentSize * bitsPerFragment
            if (totalBits % 8 > 0) {
                return ((totalBits / 8) + 1).toInt()
            } else {
                return (totalBits / 8).toInt()
            }
        }

        private fun calculateChunkCount(fragmentsPerChunk: Long, fragmentCount: Long): Int {
            return (fragmentCount / fragmentsPerChunk).toInt() + (if (fragmentCount % fragmentsPerChunk > 0) { 1 } else { 0 })
        }

        private fun FileChannel.mapChunks(mode: FileChannel.MapMode, offset: Long, chunkSize: Int, chunkCount: Int, lastChunkSize: Int): MutableList<ByteBuffer> {
            val chunks = ArrayList<ByteBuffer>(chunkSize)
            for (i in 0..(chunkCount - 2)) {
                chunks.add(map(mode, offset + (i * chunkSize.toLong()), chunkSize.toLong()).order(ByteOrder.LITTLE_ENDIAN))
            }
            chunks.add(map(mode, offset + ((chunkCount - 1) * chunkSize.toLong()), lastChunkSize.toLong()).order(ByteOrder.LITTLE_ENDIAN))
            return chunks
        }

    }

    private val useFullBytes = if (bitsPerFragment % 8 == 0) {
        true
    } else if (bitsPerFragment == 1 || bitsPerFragment == 2 || bitsPerFragment == 4) {
        false
    } else {
        throw IllegalArgumentException("bitsPerFragment must be less than 8 or a multiple of 8")
    }
    private val bitMask = if (bitsPerFragment == 1) {
        ONE_BIT_MASK
    } else if (bitsPerFragment == 2) {
        TWO_BIT_MASK
    } else {
        FOUR_BIT_MASK
    }
    private val bytesPerFragment = Math.max(1, bitsPerFragment / 8)
    private val fragmentsPerChunk = calculateFragmentsPerChunk(bitsPerFragment)
    private val chunkSize = calculateFragmentAlignedChunkSize(bitsPerFragment, fragmentsPerChunk)
    private val lastChunkSize = calculateLastChunkSize(bitsPerFragment, fragmentsPerChunk, fragmentCount)
    private val chunkCount = calculateChunkCount(fragmentsPerChunk, fragmentCount)
    private val chunks = channel.mapChunks(mode, offset, chunkSize, chunkCount, lastChunkSize)

    operator fun set(index: Int, value: ByteArray) {
        set(index.toLong(), value)
    }

    operator fun set(index: Int, value: Int) {
        set(index.toLong(), value)
    }

    operator fun set(index: Int, value: Byte) {
        set(index.toLong(), value)
    }

    operator fun set(index: Long, value: ByteArray) {
        if (useFullBytes) {
            val chunkIndex = (index / fragmentsPerChunk).toInt()
            val offset = (((index % fragmentsPerChunk) * bitsPerFragment) / 8).toInt()
            val chunk = chunks[chunkIndex]
            for (i in 0..bytesPerFragment - 1) {
                chunk.put(offset + i, value[i])
            }
        } else {
            set(index, value[0].toInt())
        }
    }

    operator fun set(index: Long, value: Int) {
        if (useFullBytes) {
            set(index, value.toByte())
        } else {
            val chunkIndex = (index / fragmentsPerChunk).toInt()
            val localOffset = (index % fragmentsPerChunk) * bitsPerFragment
            val byteOffset = (localOffset / 8).toInt()
            val bitOffset = (localOffset % 8).toInt()
            val chunk = chunks[chunkIndex]
            val mask = bitMask shl bitOffset
            val clearMask = mask xor 0xFF
            val currentState = chunk[byteOffset].toInt()
            val clearedState = currentState and clearMask
            val newValue = value shl bitOffset
            val newState = clearedState xor newValue
            chunk.put(byteOffset, newState.toByte())
        }
    }

    operator fun set(index: Long, value: Byte) {
        if (useFullBytes) {
            val chunkIndex = (index / fragmentsPerChunk).toInt()
            val offset = (((index % fragmentsPerChunk) * bitsPerFragment) / 8).toInt()
            chunks[chunkIndex].put(offset, value)
        } else {
            set(index, value.toInt())
        }
    }

    operator fun get(index: Int): ByteArray {
        return get(index.toLong())
    }

    operator fun get(index: Long): ByteArray {
        if (useFullBytes) {
            val chunkIndex = (index / fragmentsPerChunk).toInt()
            val offset = (((index % fragmentsPerChunk) * bitsPerFragment) / 8).toInt()
            val chunk = chunks[chunkIndex]
            val value = ByteArray(bytesPerFragment)
            for (i in 0..bytesPerFragment - 1) {
                value[i] = chunk.get(offset + i)
            }
            return value
        } else {
            val chunkIndex = (index / fragmentsPerChunk).toInt()
            val localOffset = (index % fragmentsPerChunk) * bitsPerFragment
            val byteOffset = (localOffset / 8).toInt()
            val bitOffset = (localOffset % 8).toInt()
            val chunk = chunks[chunkIndex]
            val state = chunk[byteOffset].toInt() and 0xFF
            val shiftedState = state ushr bitOffset
            val value = shiftedState and bitMask
            return byteArrayOf(value.toByte())
        }
    }

    fun close() {
        try {
            chunks.forEach { it.disposeDirect() }
        } finally {
            chunks.clear()
        }
    }
}