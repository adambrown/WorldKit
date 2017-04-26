package com.grimfox.gec.util

import com.grimfox.gec.Main
import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.Point2F
import org.lwjgl.BufferUtils
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

interface Reference<out T> {

    val value: T
}

interface MutableReference<T> : Reference<T> {

    override var value: T
}

interface MonitoredReference<T> : MutableReference<T> {

    val listeners: MutableList<(oldValue: T, newValue: T) -> Unit>

    override var value: T

    fun listener(listener: (oldValue: T, newValue: T) -> Unit): MonitoredReference<T>
}

private class CRef<out T>(override val value: T) : Reference<T>

private class MRef<T>(override var value: T) : MutableReference<T>

private class Ref<T>(value: T) : MonitoredReference<T> {

    override val listeners: MutableList<(oldValue: T, newValue: T) -> Unit> = ArrayList()

    private var _value: T = value
    override var value: T
        get() = _value
        set(value) {
            val old = _value
            _value = value
            listeners.forEach { it(old, value) }
        }

    override fun listener(listener: (oldValue: T, newValue: T) -> Unit): MonitoredReference<T> {
        listeners.add(listener)
        return this
    }
}

fun <T> cRef(value: T): Reference<T> {
    return CRef(value)
}

fun <T> mRef(value: T): MutableReference<T> {
    return MRef(value)
}

fun <T> ref(value: T): MonitoredReference<T> {
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
                    if (buffer!!.remaining() === 0)
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

    fun findClosestPoint(points: Matrix<Point2F>, x: Int, y: Int, gridStride: Int, pointWrapOffset: Float, point: Point2F, outputWidth: Int, wrapEdges: Boolean): Int {
        return findClosestPoints(points, x, y, gridStride, pointWrapOffset, point, outputWidth, wrapEdges, 1)[0].first
    }

    fun findClosestPoints(points: Matrix<Point2F>, x: Int, y: Int, gridStride: Int, pointWrapOffset: Float, point: Point2F, outputWidth: Int, wrapEdges: Boolean): ClosestPoints {
        val closestPoints = findClosestPoints(points, x, y, gridStride, pointWrapOffset, point, outputWidth, wrapEdges, 3)
        return ClosestPoints(closestPoints[0],
                closestPoints[1],
                closestPoints[2],
                closestPoints[3],
                closestPoints[4])
    }

    private fun findClosestPoints(points: Matrix<Point2F>, x: Int, y: Int, gridStride: Int, pointWrapOffset: Float, point: Point2F, outputWidth: Int, wrapEdges: Boolean, ringCount: Int): ArrayList<Pair<Int, Float>> {
        val ringWidth = ringCount * 2 + 1
        val closestPoints = ArrayList<Pair<Int, Float>>(ringWidth * ringWidth)
        for (yOff in -ringCount..ringCount) {
            for (xOff in -ringCount..ringCount) {
                var ox = x + xOff
                var oy = y + yOff
                var xDistAdjust = 0.0f
                var yDistAdjust = 0.0f
                if (wrapEdges) {
                    val ox1 = ox
                    ox = (ox + gridStride) % gridStride
                    if (ox1 > ox) {
                        xDistAdjust = pointWrapOffset
                    } else if (ox1 < ox) {
                        xDistAdjust = -pointWrapOffset
                    }
                    val oy1 = oy
                    oy = (oy + gridStride) % gridStride
                    if (oy1 > oy) {
                        yDistAdjust = pointWrapOffset
                    } else if (oy1 < oy) {
                        yDistAdjust = -pointWrapOffset
                    }
                }
                if (oy >= 0 && oy < gridStride && ox >= 0 && ox < gridStride) {
                    val index = oy * gridStride + ox
                    val other = points[ox, oy]
                    val distance = point.distance2(Point2F(other.x * outputWidth + xDistAdjust, other.y * outputWidth + yDistAdjust))
                    closestPoints.add(Pair(index, distance))
                }
            }
        }
        closestPoints.sort { p1, p2 ->
            p1.second.compareTo(p2.second)
        }
        return closestPoints
    }

    fun buildEdgeMap(closestPoints: Matrix<ClosestPoints>): HashMap<Int, MutableSet<Int>> {
        val edges = HashMap<Int, MutableSet<Int>>()
        val end = closestPoints.width - 1
        for (y in 0..end) {
            for (x in 0..end) {
                val points = closestPoints[x, y]
                val p0 = points.p0?.first
                val p1 = points.p1?.first
                if (p0 != null && p1 != null) {
                    val p0Cons = edges.getOrPut(p0, { LinkedHashSet() })
                    p0Cons.add(p1)
                    val p1Cons = edges.getOrPut(p1, { LinkedHashSet() })
                    p1Cons.add(p0)
                }
            }
        }
        return edges
    }

    fun buildEdgeGraph(edges: HashMap<Int, MutableSet<Int>>, pointCount: Int): ArrayList<ArrayList<Int>> {
        val edgeGraph = ArrayList<ArrayList<Int>>(pointCount)
        for (i in 0..pointCount - 1) {
            edgeGraph.add(i, ArrayList(edges[i]!!.toList().sorted()))
        }
        return edgeGraph
    }

    fun generateSemiUniformPoints(stride: Int, width: Float, random: Random, constraint: Float = 0.5f): ArrayList<Point2F> {
        val realConstraint = Math.min(1.0f, Math.max(0.0f, constraint))
        val gridSquare = width / stride
        val quarterSquare = gridSquare * realConstraint
        val margin = (gridSquare * (1.0f - realConstraint)) * 0.5f
        val points = ArrayList<Point2F>()
        for (y in 0..stride - 1) {
            val oy = (y * gridSquare) + margin
            for (x in 0..stride - 1) {
                val px = (x * gridSquare) + margin + (random.nextFloat() * quarterSquare)
                val py = oy + (random.nextFloat() * quarterSquare)
                val point = Point2F(px, py)
                points.add(point)
            }
        }
        return points
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

    fun generatePoints(stride: Int, width: Float, random: Random, minDist: Float = (width / stride) / 3.0f): ArrayList<Point2F> {
        val gridSquare = width / stride
        val minDistSquared = minDist * minDist
        val points = ArrayList<Point2F>()
        for (y in 0..stride - 1) {
            val oy = y * gridSquare
            for (x in 0..stride - 1) {
                while (true) {
                    val px = x * gridSquare + random.nextFloat() * gridSquare
                    val py = oy + random.nextFloat() * gridSquare
                    val point = Point2F(px, py)
                    if (checkDistances(points, x, y, stride, minDistSquared, point)) {
                        points.add(point)
                        break
                    }
                }
            }
        }
        return points
    }

    private fun checkDistances(points: List<Point2F>, x: Int, y: Int, stride: Int, minDistance: Float, point: Point2F): Boolean {
        for (yOff in -3..3) {
            for (xOff in -3..3) {
                val ox = x + xOff
                val oy = y + yOff
                if (oy >= 0 && oy < stride && ox >= 0 && ox < stride) {
                    if (oy < y || (oy == y && ox < x)) {
                        if (point.distance2(points[oy * stride + ox]) < minDistance) {
                            return false
                        }
                    } else {
                        return true
                    }
                }
            }
        }
        return true
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