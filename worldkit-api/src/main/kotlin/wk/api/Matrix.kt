package wk.api

import wk.internal.ext.fastFloorI
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

@PublicApi
interface Matrix<T> {

    val width: Int
    val height: Int
    val size: Int

    @PublicApi
    operator fun set(x: Int, y: Int, value: T) {
        set(y * width + x, value)
    }

    @PublicApi
    operator fun get(x: Int, y: Int): T {
        return get(y * width + x)
    }

    @PublicApi
    operator fun set(i: Int, value: T)

    @PublicApi
    operator fun get(i: Int): T
}

@PublicApi
class ByteArrayMatrix(override val width: Int, override val height: Int = width, array: ByteArray? = null, init: ((Int) -> Byte)? = null) : Matrix<Byte> {

    override val size = width * height

    @PublicApi
    val array = array ?: if (init != null) ByteArray(size, init) else ByteArray(size)

    override fun set(i: Int, value: Byte) {
        array[i] = value
    }

    override fun get(i: Int): Byte {
        return array[i]
    }

    @PublicApi
    fun copy(): ByteArrayMatrix {
        val newArray = ByteArray(size)
        System.arraycopy(array, 0, newArray, 0, size)
        return ByteArrayMatrix(width, height, newArray)
    }
}

@PublicApi
class ByteBufferMatrix(override val width: Int, override val height: Int = width, buffer: ByteBuffer? = null, init: ((Int) -> Byte)? = null) : Matrix<Byte> {

    override val size = width * height

    @PublicApi
    val buffer: ByteBuffer = buffer ?: ByteBuffer.wrap(if (init != null) ByteArray(size, init) else ByteArray(size))

    override fun set(i: Int, value: Byte) {
        buffer.put(i, value)
    }

    override fun get(i: Int): Byte {
        return buffer[i]
    }
}

@PublicApi
class ShortArrayMatrix(override val width: Int, override val height: Int = width, array: ShortArray? = null, init: ((Int) -> Short)? = null) : Matrix<Short> {

    override val size = width * height

    @PublicApi
    val array = array ?: if (init != null) ShortArray(size, init) else ShortArray(size)

    override fun set(i: Int, value: Short) {
        array[i] = value
    }

    override fun get(i: Int): Short {
        return array[i]
    }

    @PublicApi
    fun copy(): ShortArrayMatrix {
        val newArray = ShortArray(size)
        System.arraycopy(array, 0, newArray, 0, size)
        return ShortArrayMatrix(width, height, newArray)
    }
}

@PublicApi
class IntArrayMatrix(override val width: Int, override val height: Int = width, array: IntArray? = null, init: ((Int) -> Int)? = null) : Matrix<Int> {

    override val size = width * height

    @PublicApi
    val array = array ?: if (init != null) IntArray(size, init) else IntArray(size)

    override fun set(i: Int, value: Int) {
        array[i] = value
    }

    override fun get(i: Int): Int {
        return array[i]
    }

    @PublicApi
    fun copy(): IntArrayMatrix {
        val newArray = IntArray(size)
        System.arraycopy(array, 0, newArray, 0, size)
        return IntArrayMatrix(width, height, newArray)
    }
}

@PublicApi
class FloatArrayMatrix(override val width: Int, override val height: Int = width, array: FloatArray? = null, init: ((Int) -> Float)? = null) : Matrix<Float> {

    override val size = width * height

    @PublicApi
    val array = array ?: if (init != null) FloatArray(size, init) else FloatArray(size)

    override fun set(i: Int, value: Float) {
        array[i] = value
    }

    override fun get(i: Int): Float {
        return array[i]
    }

    @PublicApi
    fun copy(): FloatArrayMatrix {
        val newArray = FloatArray(size)
        System.arraycopy(array, 0, newArray, 0, size)
        return FloatArrayMatrix(width, height, newArray)
    }
}

@PublicApi
operator fun Matrix<Byte>.get(u: Float, v: Float): Byte {
    val widthM1 = width - 1
    val heightM1 = height - 1
    val x =  (u * widthM1).coerceIn(0.0f, widthM1.toFloat())
    val y =  (v * heightM1).coerceIn(0.0f, heightM1.toFloat())
    val x1 = fastFloorI(x)
    val x2 = min(x1 + 1, widthM1)
    val y1 = fastFloorI(y)
    val y2 = min(y1 + 1, heightM1)
    val interpX = x - x1
    val interpXi = 1.0f - interpX
    val interpY = y - y1
    val interpYi = 1.0f - interpY
    return (((this[x1, y1].toInt() and 0xFF) * interpXi + (this[x2, y1].toInt() and 0xFF) * interpX) * interpYi + ((this[x1, y2].toInt() and 0xFF) * interpXi + (this[x2, y2].toInt() and 0xFF) * interpX) * interpY).roundToInt().coerceIn(0, 255).toByte()
}

@PublicApi
operator fun Matrix<Short>.get(u: Float, v: Float): Short {
    val widthM1 = width - 1
    val heightM1 = height - 1
    val x =  (u * widthM1).coerceIn(0.0f, widthM1.toFloat())
    val y =  (v * heightM1).coerceIn(0.0f, heightM1.toFloat())
    val x1 = fastFloorI(x)
    val x2 = min(x1 + 1, widthM1)
    val y1 = fastFloorI(y)
    val y2 = min(y1 + 1, heightM1)
    val interpX = x - x1
    val interpXi = 1.0f - interpX
    val interpY = y - y1
    val interpYi = 1.0f - interpY
    return (((this[x1, y1].toInt() and 0xFFFF) * interpXi + (this[x2, y1].toInt() and 0xFFFF) * interpX) * interpYi + ((this[x1, y2].toInt() and 0xFFFF) * interpXi + (this[x2, y2].toInt() and 0xFFFF) * interpX) * interpY).roundToInt().coerceIn(0, 65535).toShort()
}

@PublicApi
operator fun Matrix<Float>.get(u: Float, v: Float): Float {
    val widthM1 = width - 1
    val heightM1 = height - 1
    val x =  (u * widthM1).coerceIn(0.0f, widthM1.toFloat())
    val y =  (v * heightM1).coerceIn(0.0f, heightM1.toFloat())
    val x1 = fastFloorI(x)
    val x2 = min(x1 + 1, widthM1)
    val y1 = fastFloorI(y)
    val y2 = min(y1 + 1, heightM1)
    val interpX = x - x1
    val interpXi = 1.0f - interpX
    val interpY = y - y1
    val interpYi = 1.0f - interpY
    return (this[x1, y1] * interpXi + this[x2, y1] * interpX) * interpYi + (this[x1, y2] * interpXi + this[x2, y2] * interpX) * interpY
}
