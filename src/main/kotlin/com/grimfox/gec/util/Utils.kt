package com.grimfox.gec.util

import com.grimfox.gec.Main
import com.grimfox.gec.model.Graph
import org.lwjgl.BufferUtils
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicLong

interface Reference<out T> {

    val value: T
}

interface MutableReference<T> : Reference<T> {

    override var value: T
}

interface ObservableReference<T> : Reference<T> {

    val listeners: MutableList<(oldValue: T, newValue: T) -> Unit>

    override val value: T

    fun addListener(listener: (oldValue: T, newValue: T) -> Unit): ObservableReference<T>

    fun removeListener(listener: (T, T) -> Unit): Boolean
}

interface ObservableMutableReference<T> : ObservableReference<T>, MutableReference<T> {

    override val listeners: MutableList<(oldValue: T, newValue: T) -> Unit>

    override var value: T

    override fun addListener(listener: (oldValue: T, newValue: T) -> Unit): ObservableMutableReference<T>

    override fun removeListener(listener: (T, T) -> Unit): Boolean
}

private class CRef<out T>(override val value: T) : Reference<T>

private class MRef<T>(override var value: T) : MutableReference<T>

private class Ref<T>(value: T) : ObservableMutableReference<T> {

    override val listeners: MutableList<(oldValue: T, newValue: T) -> Unit> = ArrayList()

    private var _value: T = value
    override var value: T
        get() = _value
        set(value) {
            val old = _value
            _value = value
            listeners.forEach { it(old, value) }
        }

    override fun addListener(listener: (oldValue: T, newValue: T) -> Unit): ObservableMutableReference<T> {
        listeners.add(listener)
        return this
    }

    override fun removeListener(listener: (T, T) -> Unit): Boolean {
        return listeners.remove(listener)
    }
}

fun <T> cRef(value: T): Reference<T> {
    return CRef(value)
}

fun <T> mRef(value: T): MutableReference<T> {
    return MRef(value)
}

fun <T> ref(value: T): ObservableMutableReference<T> {
    return Ref(value)
}

inline fun <reified T> printList(list: Collection<T>): String {
    return printList(list, { it.toString() })
}

inline fun <reified T> printList(list: Collection<T>, noinline transform: (T) -> CharSequence): String {
    if (list.isEmpty()) {
        return "arrayListOf<${T::class.simpleName}>()"
    }
    return list.joinToString(", ", "arrayListOf(", ")", transform = transform)
}

fun <E> Collection<E>.containsAny(other: Collection<E>): Boolean {
    other.forEach {
        if (this.contains(it)) {
            return true
        }
    }
    return false
}

fun getResourceStream(resource: String): InputStream {
    return Utils::class.java.getResourceAsStream(resource)
}

fun loadResource(resource: String, bufferSize: Int): ByteBuffer {
    var buffer: ByteBuffer? = null

    val path = Paths.get(resource)
    if (Files.isReadable(path)) {
        Files.newByteChannel(path).use({ fc ->
            buffer = BufferUtils.createByteBuffer(fc.size().toInt() + 1)
            var read = 0
            while (read != -1) {
                read = fc.read(buffer)
            }
        })
    } else {
        Utils::class.java.getResourceAsStream(resource).use { source ->
            Channels.newChannel(source).use { rbc ->
                buffer = BufferUtils.createByteBuffer(bufferSize)

                while (true) {
                    val bytes = rbc.read(buffer)
                    if (bytes == -1)
                        break
                    if (buffer!!.remaining() == 0)
                        buffer = resizeBuffer(buffer!!, buffer!!.capacity() * 2)
                }
            }
        }
    }
    buffer!!.flip()
    return buffer!!
}

private fun resizeBuffer(buffer: ByteBuffer, newCapacity: Int): ByteBuffer {
    val newBuffer = BufferUtils.createByteBuffer(newCapacity)
    buffer.flip()
    newBuffer.put(buffer)
    return newBuffer
}

fun clamp(value: Int, min: Int, max: Int): Int {
    if (value < min) {
        return min
    }
    if (value > max) {
        return max
    }
    return value
}

fun clamp(value: Float, min: Float, max: Float): Float {
    if (value < min) {
        return min
    }
    if (value > max) {
        return max
    }
    return value
}

fun clamp(value: Double, min: Double, max: Double): Double {
    if (value < min) {
        return min
    }
    if (value > max) {
        return max
    }
    return value
}

val mute = false

inline fun <reified T> timeIt(callback: () -> T): T {
    return timeIt(null, null, callback)
}

inline fun <reified T> timeIt(accumulator: AtomicLong, callback: () -> T): T {
    return timeIt(null, accumulator, callback)
}

inline fun <reified T> timeIt(message: String, callback: () -> T): T {
    return timeIt(message, null, callback)
}

inline fun <reified T> timeIt(message: String?, accumulator: AtomicLong?, callback: () -> T): T {
    if (!mute) {
        val time = System.nanoTime()
        val ret = callback()
        val totalNano = System.nanoTime() - time
        if (message != null) {
            println("$message: ${totalNano / 1000000.0}")
        }
        accumulator?.addAndGet(totalNano)
        return ret
    } else if (accumulator != null) {
        val time = System.nanoTime()
        val ret = callback()
        val totalNano = System.nanoTime() - time
        accumulator.addAndGet(totalNano)
        return ret
    } else {
        return callback()
    }
}

class StopWatch(var message: String? = null, var accumulator: AtomicLong? = null) {
    var time = 0L

    fun start(): StopWatch {
        time = System.nanoTime()
        return this
    }

    fun start(message: String?): StopWatch {
        this.message = message
        time = System.nanoTime()
        return this
    }

    fun stop(): Long {
        val totalNano = System.nanoTime() - time
        if (message != null) {
            println("$message: ${totalNano / 1000000.0}")
        }
        accumulator?.addAndGet(totalNano)
        return totalNano
    }
}

fun timer(message: String? = null, accumulator: AtomicLong? = null): StopWatch {
    return StopWatch(message, accumulator)
}

object Utils {

    val LOG = LoggerFactory.getLogger(Main::class.java)!!

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

    fun ByteBuffer.readUint24(position: Int): Int {
        return (get(position).toInt() and 0xFF) + ((get(position + 1).toInt() and 0xFF) shl 8) + ((get(position + 2).toInt() and 0xFF) shl 16)
    }

    fun ByteBuffer.writeUint24(position: Int, value: Int) {
        put(position, (value and 0xFF).toByte())
        put(position + 1, ((value ushr 8) and 0xFF).toByte())
        put(position + 2, ((value ushr 16) and 0xFF).toByte())
    }


    inline fun generateSemiUniformPointsD(stride: Int, width: Double, random: Random, constraint: Double = 0.5, callback: (i: Int, x: Double, y: Double) -> Unit) {
        val realConstraint = clamp(constraint, 0.0, 1.0)
        val gridSquare = width / stride
        val quarterSquare = gridSquare * realConstraint
        val margin = (gridSquare * (1.0 - realConstraint)) * 0.5
        var i = 0
        for (y in 0..stride - 1) {
            val oy = (y * gridSquare) + margin
            for (x in 0..stride - 1) {
                callback(i++, (x * gridSquare) + margin + (random.nextDouble() * quarterSquare), oy + (random.nextDouble() * quarterSquare))
            }
        }
    }

    inline fun generateSemiUniformPointsF(stride: Int, width: Float, random: Random, constraint: Float = 0.5f, callback: (i: Int, x: Float, y: Float) -> Unit) {
        generateSemiUniformPointsD(stride, width.toDouble(), random, constraint.toDouble()) { i, x, y -> callback(i, x.toFloat(), y.toFloat()) }
    }

    fun <T, ML : MutableList<T>> ML.init(size: Int, init: (Int) -> T): ML {
        this.clear()
        for (i in 0..size - 1) {
            this.add(init(i))
        }
        return this
    }

    fun findConcavityWeights(graph: Graph, body: LinkedHashSet<Int>, vertexIds: LinkedHashSet<Int>, vararg radii: Float): ArrayList<Pair<Int, Float>> {
        return findConcavityWeights(graph, body, vertexIds, null, *radii)
    }

    fun findConcavityWeights(graph: Graph, body: LinkedHashSet<Int>, vertexIds: LinkedHashSet<Int>, mask: LinkedHashSet<Int>?, vararg radii: Float): ArrayList<Pair<Int, Float>> {
        val weights = ArrayList<Pair<Int, Float>>(vertexIds.size)
        vertexIds.forEach { vertexId ->
            weights.add(Pair(vertexId, findConcavityWeight(graph, body, vertexId, mask, *radii)))
        }
        return weights
    }

    fun findConcavityWeight(graph: Graph, body: LinkedHashSet<Int>, vertexId: Int, mask: LinkedHashSet<Int>?, vararg radii: Float): Float {
        var landWaterRatio = 1.0f
        radii.forEach { radius ->
            landWaterRatio *= calculateConcavityRatio(graph, body, vertexId, mask, radius)
        }
        return landWaterRatio
    }

    private fun calculateConcavityRatio(graph: Graph, body: LinkedHashSet<Int>, vertexId: Int, mask: LinkedHashSet<Int>?, radius: Float): Float {
        val vertices = graph.vertices
        val vertex = vertices[vertexId]
        val point = vertex.point
        val closeVertices = graph.getPointsWithinRadius(point, radius).filter { !(mask?.contains(it) ?: false) }
        var inCount = 0
        closeVertices.forEach {
            if (body.contains(it)) {
                inCount++
            }
        }
        return inCount.toFloat() / closeVertices.size
    }
}