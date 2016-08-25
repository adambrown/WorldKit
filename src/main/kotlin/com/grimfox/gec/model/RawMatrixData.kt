package com.grimfox.gec.model

import com.grimfox.gec.generator.Point
import com.grimfox.gec.util.Utils.disposeDirect
import com.grimfox.gec.util.Utils.imageSizeAsExp2
import com.grimfox.gec.util.Utils.mapChunks
import com.grimfox.gec.util.Utils.pow
import com.grimfox.gec.util.Utils.primitiveToWrapper
import com.grimfox.gec.util.Utils.readInt
import com.grimfox.gec.util.Utils.readUint24
import com.grimfox.gec.util.Utils.toRandomAccessFileMode
import com.grimfox.gec.util.Utils.writeInt
import com.grimfox.gec.util.Utils.writeUint24
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

class DeprecatedRawMatrixData<T> private constructor(private val mode: MapMode, private val channel: FileChannel, private val size: Int, val rasterWidth: Int, val segmentWidth: Int, val format: Format<T>) {

    private constructor(mode: MapMode, channel: FileChannel, format: Format<T>) : this(mode, channel, channel.imageSizeAsExp2(format.dataFormat.size, METADATA_BYTES), channel.readInt(4), channel.readInt(8), format)

    private constructor(file: File, size: Int, rasterWidth: Int, segmentWidth: Int, format: Format<T>) : this(MapMode.READ_WRITE, RandomAccessFile(file, MapMode.READ_WRITE.toRandomAccessFileMode()).channel, size, rasterWidth, segmentWidth, format) {
        channel.writeInt(0L, format.id)
        channel.writeInt(4L, rasterWidth)
        channel.writeInt(8L, segmentWidth)
    }

    interface DataFormat<T> {

        val type: Class<T>

        val size: Int

        val get: (ByteBuffer, Int) -> T

        val put: (ByteBuffer, Int, T) -> Unit
    }

    private open class BaseFormat<T>(override val type: Class<T>, override val size: Int, override val get: (ByteBuffer, Int) -> T, override val put: (ByteBuffer, Int, T) -> Unit) : DataFormat<T>

    private class FloatFormat() : BaseFormat<Float>(
            type = Float::class.java,
            size = 4,
            get = { buffer, offset ->
                buffer.getFloat(offset)
            },
            put = { buffer, offset, value ->
                buffer.putFloat(offset, value)
            }
    )

    private class IntFormat() : BaseFormat<Int>(
            type = Int::class.java,
            size = 4,
            get = { buffer, offset ->
                buffer.getInt(offset)
            },
            put = { buffer, offset, value ->
                buffer.putInt(offset, value)
            }
    )

    private class Uint24Format() : BaseFormat<Int>(
            type = Int::class.java,
            size = 4,
            get = { buffer, offset ->
                buffer.readUint24(offset)
            },
            put = { buffer, offset, value ->
                buffer.writeUint24(offset, value)
            }
    )

    private class ClosestPointsFormat() : BaseFormat<ClosestPoints>(
            type = ClosestPoints::class.java,
            size = 36,
            get = { buffer, offset ->
                val cursor = AtomicInteger(offset)
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
                ClosestPoints(readPair(), readPair(), readPair(), readPair(), readPair())
            },
            put = { buffer, offset, value ->
                val cursor = AtomicInteger(offset)
                var nullMask = 0x1F
                for (i in 0..4) {
                    if (value[i] == null) {
                        nullMask = (0x01 shl i) xor nullMask
                    }
                }
                fun writePair(pair: Pair<Int, Float>) {
                    buffer.writeUint24(cursor.getAndAdd(3), pair.first)
                    buffer.putFloat(cursor.getAndAdd(4), pair.second)
                }
                buffer.put(cursor.getAndAdd(1), nullMask.toByte())
                for (i in 0..4) {
                    writePair(value[i] ?: ZERO_INT_FLOAT_PAIR)
                }
            }
    )

    private class PointFormat() : BaseFormat<Point>(
            type = Point::class.java,
            size = 8,
            get = { buffer, offset ->
                Point(buffer.getFloat(offset), buffer.getFloat(offset + 4))
            },
            put = { buffer, offset, value ->
                buffer.putFloat(offset, value.x)
                buffer.putFloat(offset + 4, value.y)
            }
    )

    class Format<T> private constructor(val dataFormat: DataFormat<T>, val name: String, val id: Int) {

        init {
            nameFormatMap[name] = this
            idFormatMap[id] = this
        }

        companion object {

            private val nameFormatMap = HashMap<String, Format<*>>()
            private val idFormatMap = HashMap<Int, Format<*>>()

            val FLOAT: Format<Float> = Format(FloatFormat(), "FLOAT", 0)
            val INT32: Format<Int> = Format(IntFormat(), "INT32", 1)
            val UINT24: Format<Int> = Format(Uint24Format(), "UINT24", 2)
            val POINT_FLOAT: Format<Point> = Format(PointFormat(), "POINT_FLOAT", 3)
            val CLOSEST_POINTS: Format<ClosestPoints> = Format(ClosestPointsFormat(), "CLOSEST_POINTS", 4)

            inline fun <reified T : Any> fromFile(channel: FileChannel): Format<T> {
                return checkFormatType(fromFileUntyped(channel))
            }

            fun fromFileUntyped(channel: FileChannel): Format<*> {
                val buffer = ByteBuffer.wrap(ByteArray(4)).order(ByteOrder.LITTLE_ENDIAN)
                channel.read(buffer)
                val id = buffer.getInt(0)
                return fromIdUntyped(id)
            }

            inline fun <reified T : Any> fromName(name: String): Format<T> {
                return checkFormatType(fromNameUntyped(name))
            }

            fun fromNameUntyped(name: String): Format<*> {
                return nameFormatMap[name] ?: throw IllegalArgumentException("Invalid format name for RawMatrixData: $name")
            }

            inline fun <reified T : Any> fromId(id: Int): Format<T> {
                return checkFormatType(fromIdUntyped(id))
            }

            fun fromIdUntyped(id: Int): Format<*> {
                return idFormatMap[id] ?: throw IllegalArgumentException("Invalid format id for RawMatrixData: $id")
            }

            inline fun <reified T : Any> checkFormatType(formatUntyped: Format<*>): Format<T> {
                if (T::class.java.isAssignableFrom(formatUntyped.dataFormat.type)) {
                    @Suppress("UNCHECKED_CAST") return formatUntyped as Format<T>
                } else {
                    throw IllegalArgumentException("Format type does not match: ${T::class.qualifiedName}")
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            return other is Format<*> && id == other.id
        }

        override fun hashCode(): Int {
            return id
        }
    }

    companion object {

        private val ZERO_INT_FLOAT_PAIR = Pair(0, 0.0f)
        private val METADATA_BYTES = 12

        fun <T> createAndUse(file: File, size: Int, format: Format<T>, rasterWidth: Int = 2.pow(size), segmentWidth: Int = 1, codeBlock: (DeprecatedRawMatrixData<T>) -> Unit) {
            if (file.exists()) {
                file.delete()
            }
            DeprecatedRawMatrixData(file, size, rasterWidth, segmentWidth, format).use(codeBlock)
        }

        fun <T : Any> openAndUse(file: File, type: Class<T>, mode: MapMode = MapMode.READ_ONLY, codeBlock: (DeprecatedRawMatrixData<T>) -> Unit) {
            checkRawMatrixDataType(createRawMatrixDataUntyped(file, mode), type).use(codeBlock)
        }

        fun <T : Any> openAndUse(file: File, type: KClass<T>, mode: MapMode = MapMode.READ_ONLY, codeBlock: (DeprecatedRawMatrixData<T>) -> Unit) {
            openAndUse(file, type.java, mode, codeBlock)
        }

        inline fun <reified T : Any> openAndUse(file: File, mode: MapMode = MapMode.READ_ONLY, noinline codeBlock: (DeprecatedRawMatrixData<T>) -> Unit) {
            openAndUse(file, T::class.java, mode, codeBlock)
        }

        fun openAndUseUntyped(file: File, mode: MapMode = MapMode.READ_ONLY, codeBlock: (DeprecatedRawMatrixData<Any>) -> Unit) {
            openAndUse(file, Any::class.java, mode, codeBlock)
        }

        private fun <T : Any> checkRawMatrixDataType(data: DeprecatedRawMatrixData<*>, type: Class<T>): DeprecatedRawMatrixData<T> {
            if (type.isAssignableFrom(data.format.dataFormat.type) || type.isAssignableFrom(primitiveToWrapper(data.format.dataFormat.type))) {
                @Suppress("UNCHECKED_CAST") return data as DeprecatedRawMatrixData<T>
            } else {
                throw IllegalArgumentException("RawMatrixData type does not match: ${type.canonicalName}")
            }
        }

        private fun createRawMatrixDataUntyped(file: File, mode: MapMode): DeprecatedRawMatrixData<*> {
            if (!file.isFile) {
                throw IllegalArgumentException("File ${file.canonicalPath} does not exist.")
            }
            val channel = RandomAccessFile(file, mode.toRandomAccessFileMode()).channel
            return DeprecatedRawMatrixData(mode, channel, Format.fromFileUntyped(channel))
        }
    }

    private val memorySize = 2L.pow(size).pow(2) * format.dataFormat.size
    private val valid = if (size < 2) {
        throw IllegalArgumentException("Size is invalid: $size. Must be an integer greater than 1.")
    } else if (memorySize > 34359738368L) {
        throw IllegalArgumentException("Size of data buffer: $memorySize bytes is too large.")
    } else { true }
    private val dataFormat = format.dataFormat
    private val stride = if (size > 10) 2.pow(11) else 2.pow(size)
    private val chunkSize = stride.pow(2) * dataFormat.size
    private val chunkStride = if (size < 12) 1 else 2.pow(size - 11)
    private val chunkCount = chunkStride.pow(2)
    private val chunks = channel.mapChunks(mode, METADATA_BYTES, chunkSize, chunkCount)
    private var open = true
    val width = stride * chunkStride
    val height = width

    operator fun set(x: Int, y: Int, value: T) {
        if (open) {
            getChunk(x, y)[x, y] = value
        } else {
            throw IllegalStateException("Calling get on closed buffers.")
        }
    }

    operator fun get(x: Int, y: Int): T {
        if (open) {
            return getChunk(x, y)[x, y]
        }
        throw IllegalStateException("Calling get on closed buffers.")
    }

    internal fun use(codeBlock: (DeprecatedRawMatrixData<T>) -> Unit) {
        try {
            codeBlock(this)
        } finally {
            close()
        }
    }

    private fun close() {
        try {
            channel.close()
        } finally {
            try {
                chunks.forEach { it.disposeDirect() }
            } finally {
                chunks.clear()
            }
        }
    }

    private fun getChunk(x: Int, y: Int) = chunks[((y / stride) * chunkStride) + (x / stride)]

    private operator fun ByteBuffer.get(x: Int, y: Int) = dataFormat.get(this, (((y % stride) * stride) + (x % stride)) * dataFormat.size)

    private operator fun ByteBuffer.set(x: Int, y: Int, value: T) = dataFormat.put(this, (((y % stride) * stride) + (x % stride)) * dataFormat.size, value)
}
