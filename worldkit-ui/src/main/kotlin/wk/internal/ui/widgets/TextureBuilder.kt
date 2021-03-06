package wk.internal.ui.widgets

import wk.internal.ui.nvgproxy.*
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import wk.api.*
import wk.internal.application.LOG
import wk.internal.ui.util.*
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import wk.api.color as c4f

object TextureBuilder {

    val RENDER_WIDTHS = intArrayOf(128, 256, 512, 1024, 2048, 4096, 8192).toList()

    private var nvg: Long = -1
    private var executionThread: Thread? = null

    fun init(nvg: Long, thread: Thread) {
        this.nvg = nvg
        this.executionThread = thread
    }

    private val deadTextureQueue = ConcurrentLinkedQueue<Int>()
    private val deadProgramQueue = ConcurrentLinkedQueue<Int>()

    class TextureIdImpl(override val id: Int): TextureId {

        @Volatile private var free: Boolean = false

        override fun free() {
            if (!free) {
                synchronized(this) {
                    if (!free) {
                        free = true
                        deadTextureQueue.add(id)
                    }
                }
            }
        }

        fun finalize() {
            free()
        }
    }

    class ShaderProgramId(val id: Int) {

        @Volatile private var free: Boolean = false

        fun free() {
            if (!free) {
                synchronized(this) {
                    if (!free) {
                        free = true
                        deadProgramQueue.add(id)
                    }
                }
            }
        }

        @Suppress("unused")
        fun finalize() {
            free()
        }
    }

    private class ValueCollector<T : Any>(val retriever: () -> T) {
        lateinit var value: T

        fun retrieve() {
            value = retriever()
        }
    }

    private val workQueue = ConcurrentLinkedQueue<Triple<CountDownLatch, ValueCollector<*>, MutableReference<Throwable?>>>()
    private val mvpMatrixUniformDynamicGeometry = ShaderUniform("modelViewProjectionMatrix")
    private val positionAttributeDynamicGeometry = ShaderAttribute("position")
    private val mvpMatrix = Matrix4f()
    private val floatBuffer = BufferUtils.createFloatBuffer(16)
    private var dynamicGeometryProgram: Int = 0
    private var dynamicGeometry3D: DynamicGeometry3D
    private var dynamicGeometry2D: DynamicGeometry2D
    private val mvpMatrixUniformNormalAndAo = ShaderUniform("modelViewProjectionMatrix")
    private val heightScaleUniformNormalAndAo = ShaderUniform("heightScale")
    private val uvScaleUniformNormalAndAo = ShaderUniform("uvScale")
    private val heightMapTextureUniformNormalAndAo = ShaderUniform("heightMapTexture")
    private val positionAttributeNormalAndAo = ShaderAttribute("position")
    private val uvAttributeNormalAndAo = ShaderAttribute("uv")
    private var normalAndAoProgram: Int = 0
    private var normalAndAoImagePlane: ImagePlane
    private var textureRenderersLookup: List<Pair<Int, TextureRenderer>>
    private var textureRenderers: Map<Int, TextureRenderer>

    init {
        val dynamicGeometryVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/dynamic-geometry.vert"))
        val dynamicGeometryFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/dynamic-geometry.frag"))

        dynamicGeometryProgram = createAndLinkProgram(
                listOf(dynamicGeometryVertexShader, dynamicGeometryFragmentShader),
                listOf(positionAttributeDynamicGeometry),
                listOf(mvpMatrixUniformDynamicGeometry))

        dynamicGeometry3D = DynamicGeometry3D()
        dynamicGeometry2D = DynamicGeometry2D()

        normalAndAoImagePlane = ImagePlane(1.0f, positionAttributeNormalAndAo, uvAttributeNormalAndAo, 0.5f)

        textureRenderersLookup = ArrayList(RENDER_WIDTHS.map { it to TextureRenderer(it, it) })
        textureRenderers = hashMapOf(*textureRenderersLookup.toTypedArray())
    }

    private fun <T : Any> doDeferredOpenglWork(collector: ValueCollector<T>): T {
        return if (Thread.currentThread() == executionThread) {
            collector.retriever()
        } else {
            val latch = CountDownLatch(1)
            val throwableReference = mRef(null as Throwable?)
            workQueue.add(Triple(latch, collector, throwableReference))
            while (latch.count > 0) {
                latch.await()
            }
            val throwable = throwableReference.value
            if (throwable != null) {
                throw throwable
            }
            collector.value
        }
    }

    fun buildShaderProgram(builder: () -> Int): ShaderProgramId {
        return ShaderProgramId(doDeferredOpenglWork(ValueCollector { builder() }))
    }

    fun <T : Any> render(resolution: Int, builder: (dynamicGeometry3D: DynamicGeometry3D, dynamicGeometry2D: DynamicGeometry2D, textureRenderer: TextureRenderer) -> T): T {
        return doDeferredOpenglWork(ValueCollector {
            val textureRenderer = textureRenderers.getOrDefault(resolution, textureRenderersLookup.last().second)
            builder(dynamicGeometry3D, dynamicGeometry2D, textureRenderer)
        })
    }

    private fun <T : Any> renderTrianglesInternal(resolution: Int, input: Pair<FloatArray, IntArray>, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f), collector: (TextureRenderer) -> T): T {
        return doDeferredOpenglWork(ValueCollector {
            val textureRenderer = textureRenderers.getOrDefault(resolution, textureRenderersLookup.last().second)
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 2.0f)
            glDisable(GL_BLEND)
            glDisable(GL_CULL_FACE)
            glDisable(GL_MULTISAMPLE)
            glEnable(GL_DEPTH_TEST)
            glDisable(GL_SCISSOR_TEST)
            textureRenderer.bind()
            glClearColor(clearColor.first, clearColor.second, clearColor.third, clearColor.fourth)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            drawTriangles(input)
            val retVal = collector(textureRenderer)
            textureRenderer.unbind()
            retVal
        })
    }

    private fun <T : Any> renderNormalAndAoInternal(resolution: Int, heightMapTexture: TextureId, heightScale: Float, uvScale: Float, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f), collector: (TextureRenderer) -> T): T {
        return doDeferredOpenglWork(ValueCollector {
            val textureRenderer = textureRenderers.getOrDefault(resolution, textureRenderersLookup.last().second)
            mvpMatrix.setOrtho(0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 2.0f)
            glDisable(GL_BLEND)
            glDisable(GL_CULL_FACE)
            glDisable(GL_MULTISAMPLE)
            glDisable(GL_DEPTH_TEST)
            glDisable(GL_SCISSOR_TEST)
            textureRenderer.bind()
            glClearColor(clearColor.first, clearColor.second, clearColor.third, clearColor.fourth)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            drawNormalsAndSAo(heightMapTexture, heightScale, uvScale)
            val retVal = collector(textureRenderer)
            textureRenderer.unbind()
            retVal
        })
    }


    private fun <T : Any> renderNvgInternal(resolution: Int, collector: (TextureRenderer) -> T): T {
        val textureRenderer = textureRenderers.getOrDefault(resolution, textureRenderersLookup.last().second)
        return doDeferredOpenglWork(ValueCollector { collector(textureRenderer) })
    }

    fun rgba(r: Int, g: Int, b: Int, a: Int, color: NPColor): NPColor {
        return nvgRGBA(r.toByte(), g.toByte(), b.toByte(), a.toByte(), color)
    }

    fun renderSplineSelectors(resolution: Int, splines: List<Pair<Int, List<Point2F>>>, strokeWidth: Float): TextureId {
        return renderNvgInternal(resolution) { textureRenderer ->
            val width = textureRenderer.width
            val height = textureRenderer.height

            textureRenderer.bind()
            glDisable(GL_MULTISAMPLE)

            nvgSave(nvg)
            glViewport(0, 0, width, height)
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
            nvgBeginFrame(nvg, width, height, 1.0f)

            val color = NPColor.create()
            nvgStrokeWidth(nvg, strokeWidth)
            splines.forEach { (index, spline) ->
                val r = index and 0x000000FF
                val g = (index shr 8) and 0x000000FF
                rgba(r, g, 0, 255, color)
                nvgStrokeColor(nvg, color)
                drawShape(nvg, resolution, spline, false)
                nvgStroke(nvg)
            }

            nvgEndFrame(nvg)
            nvgRestore(nvg)
            val retVal = textureRenderer.newRgbaTextureByte(GL_NEAREST, GL_NEAREST)
            textureRenderer.unbind()
            retVal
        }
    }

    fun renderSplines(resolution: Int, polyLines: List<List<Point2F>>, strokeWidth: Float, target: TextureId? = null): TextureId {
        return renderNvgInternal(resolution) { textureRenderer ->
            val width = textureRenderer.width
            val height = textureRenderer.height

            textureRenderer.bind()
            glEnable(GL_MULTISAMPLE)

            nvgSave(nvg)
            glViewport(0, 0, width, height)
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
            nvgBeginFrame(nvg, width, height, 1.0f)

            val color = NPColor.create()
            rgba(255, 255, 255, 255, color)
            nvgStrokeColor(nvg, color)
            nvgStrokeWidth(nvg, strokeWidth)
            polyLines.forEach {
                drawShape(nvg, resolution, it, false)
                nvgStroke(nvg)
            }
            nvgEndFrame(nvg)
            nvgRestore(nvg)

            if (target == null) {
                val retVal = textureRenderer.newRgbaTextureByte(GL_LINEAR, GL_LINEAR)
                textureRenderer.unbind()
                retVal
            } else {
                textureRenderer.copyTexture(target)
                textureRenderer.unbind()
                target
            }
        }
    }

    fun drawHole(nvg: Long, resolution: Int, points: List<Point2F>) {
        drawLines(nvg, points, true, true, resolution.toFloat())
        nvgPathWinding(nvg, NVG_HOLE)
    }

    fun drawShape(nvg: Long, resolution: Int, points: List<Point2F>, isClosed: Boolean, isComposite: Boolean = false) {
        if (!isComposite) {
            nvgBeginPath(nvg)
        }
        drawLines(nvg, points, isClosed, true, resolution.toFloat())
        if (isClosed) {
            if (!isComposite) {
                nvgPathWinding(nvg, NVG_SOLID)
                nvgClosePath(nvg)
            }
        }
    }

    private fun drawLines(nvg: Long, points: List<Point2F>, isClosed: Boolean, moveToFirst: Boolean, multiplier: Float) {
        for (i in if (isClosed) 1..points.size else 1 until points.size) {
            val id = i % points.size
            val lastId = i - 1
            val lastPoint = points[lastId]
            val point = points[id]
            if (i == 1 && moveToFirst) {
                nvgMoveTo(nvg, lastPoint.x * multiplier, (1.0f - lastPoint.y) * multiplier)
                nvgLineTo(nvg, point.x * multiplier, (1.0f - point.y) * multiplier)
            } else {
                nvgLineTo(nvg, point.x * multiplier, (1.0f - point.y) * multiplier)
            }
        }
    }

    fun renderNormalAndAoRgbaByte(resolution: Int, heightMapTexture: TextureId, heightScale: Float, uvScale: Float, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): ByteBuffer {
        return renderNormalAndAoInternal(resolution, heightMapTexture, heightScale, uvScale, clearColor, {
            val id = it.newRgbaTextureByte(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRgbaByte(id, resolution)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRedFloat(resolution: Int, input: Pair<FloatArray, IntArray>, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): FloatArray {
        return renderTrianglesInternal(resolution, input, clearColor, {
            val id = it.newRedTextureFloat(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRedFloat(id, resolution)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRedFloat(resolution: Int, vertices: FloatArray, indices: IntArray, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): FloatArray {
        return renderTrianglesRedFloat(resolution, vertices to indices, clearColor)
    }

    fun renderTrianglesRedShort(resolution: Int, input: Pair<FloatArray, IntArray>, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): ShortArray {
        return renderTrianglesInternal(resolution, input, clearColor, {
            val id = it.newRedTextureShort(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRedShort(id, resolution)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRedShort(resolution: Int, vertices: FloatArray, indices: IntArray, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): ShortArray {
        return renderTrianglesRedShort(resolution,vertices to indices, clearColor)
    }

    fun renderTrianglesRedByte(resolution: Int, input: Pair<FloatArray, IntArray>, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): ByteBuffer {
        return renderTrianglesInternal(resolution, input, clearColor, {
            val id = it.newRedTextureByte(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRedByte(id, resolution)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRedByte(resolution: Int, vertices: FloatArray, indices: IntArray, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): ByteBuffer {
        return renderTrianglesRedByte(resolution,vertices to indices, clearColor)
    }

    fun renderTrianglesRgbaByte(resolution: Int, input: Pair<FloatArray, IntArray>, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): ByteBuffer {
        return renderTrianglesInternal(resolution, input, clearColor, {
            val id = it.newRgbaTextureByte(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRgbaByte(id, resolution)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRgbaByte(resolution: Int, vertices: FloatArray, indices: IntArray, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): ByteBuffer {
        return renderTrianglesRgbaByte(resolution,vertices to indices, clearColor)
    }


    fun renderTrianglesTexRedFloat(resolution: Int, input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesInternal(resolution, input, clearColor, { it.newRedTextureFloat(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedFloat(resolution: Int, vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesTexRedFloat(resolution, vertices to indices, minFilter, magFilter, clearColor)
    }

    fun renderTrianglesTexRedShort(resolution: Int, input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesInternal(resolution, input, clearColor, { it.newRedTextureShort(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedShort(resolution: Int, vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesTexRedShort(resolution, vertices to indices, minFilter, magFilter, clearColor)
    }

    fun renderTrianglesTexRedByte(resolution: Int, input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesInternal(resolution, input, clearColor, { it.newRedTextureByte(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedByte(resolution: Int, vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesTexRedByte(resolution, vertices to indices, minFilter, magFilter, clearColor)
    }

    fun renderTrianglesTexRgbaByte(resolution: Int, input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesInternal(resolution, input, clearColor, { it.newRgbaTextureByte(minFilter, magFilter) })
    }

    fun renderTrianglesTexRgbaByte(resolution: Int, vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesTexRgbaByte(resolution, vertices to indices, minFilter, magFilter, clearColor)
    }

    fun renderTrianglesToTexture(resolution: Int, vertices: FloatArray, indices: IntArray, textureId: TextureId, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesToTexture(resolution, vertices to indices, textureId, clearColor)
    }

    fun renderTrianglesToTexture(resolution: Int, input: Pair<FloatArray, IntArray>, textureId: TextureId, clearColor: ColorF = c4f(0.0f, 0.0f, 0.0f, 1.0f)): TextureId {
        return renderTrianglesInternal(resolution, input, clearColor, {
            it.copyTexture(textureId)
            textureId
        })
    }

    fun extractTextureRedFloat(textureId: TextureId, width: Int): FloatArray {
        return doDeferredOpenglWork(ValueCollector {
            val matrix = FloatArray(width * width)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_FLOAT, matrix)
            matrix
        })
    }

    fun extractTextureRedShort(textureId: TextureId, width: Int): ShortArray {
        return doDeferredOpenglWork(ValueCollector {
            val matrix = ShortArray(width * width)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_SHORT, matrix)
            matrix
        })
    }

    fun extractTextureRedByte(textureId: TextureId, width: Int): ByteBuffer {
        return doDeferredOpenglWork(ValueCollector {
            val matrix = BufferUtils.createByteBuffer(width * width)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_BYTE, matrix)
            matrix
        })
    }

    fun extractTextureRgbaByte(textureId: TextureId, width: Int): ByteBuffer {
        return doDeferredOpenglWork(ValueCollector {
            val matrix = BufferUtils.createByteBuffer(width * width * 4)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, matrix)
            matrix
        })
    }

    fun buildTextureRedFloat(data: FloatArray, width: Int, minFilter: Int, magFilter: Int): TextureId {
        return doDeferredOpenglWork(ValueCollector {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, width, width, 0, GL_RED, GL_FLOAT, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            TextureIdImpl(newTexId)
        })
    }

    fun buildTextureRgbFloat(data: FloatArray, width: Int, minFilter: Int, magFilter: Int): TextureId {
        return doDeferredOpenglWork(ValueCollector {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB32F, width, width, 0, GL_RGB, GL_FLOAT, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            TextureIdImpl(newTexId)
        })
    }

    fun buildTextureRedFloat(data: FloatBuffer, width: Int, minFilter: Int, magFilter: Int): TextureId {
        return doDeferredOpenglWork(ValueCollector {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, width, width, 0, GL_RED, GL_FLOAT, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            TextureIdImpl(newTexId)
        })
    }

    fun buildTextureRedShort(data: ShortArray, width: Int, minFilter: Int, magFilter: Int): TextureId {
        return doDeferredOpenglWork(ValueCollector {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 2)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R16, width, width, 0, GL_RED, GL_UNSIGNED_SHORT, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            TextureIdImpl(newTexId)
        })
    }

    fun buildTextureRedShort(data: ShortBuffer, width: Int, minFilter: Int, magFilter: Int): TextureId {
        return doDeferredOpenglWork(ValueCollector {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 2)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R16, width, width, 0, GL_RED, GL_UNSIGNED_SHORT, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            TextureIdImpl(newTexId)
        })
    }

    fun buildTextureRedByte(data: ByteBuffer, width: Int, minFilter: Int, magFilter: Int): TextureId {
        return doDeferredOpenglWork(ValueCollector {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, width, 0, GL_RED, GL_UNSIGNED_BYTE, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            TextureIdImpl(newTexId)
        })
    }

    fun buildTextureRgbaByte(data: ByteBuffer, width: Int, minFilter: Int, magFilter: Int): TextureId {
        return doDeferredOpenglWork(ValueCollector {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, width, 0, GL_RGBA, GL_UNSIGNED_BYTE, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            TextureIdImpl(newTexId)
        })
    }

    fun onDrawFrame() {
        var pollDeadTex = deadTextureQueue.poll()
        val deadTexIds = ArrayList<Int>()
        while (pollDeadTex != null) {
            deadTexIds.add(pollDeadTex)
            pollDeadTex = deadTextureQueue.poll()
        }
        if (deadTexIds.isNotEmpty()) {
            glDeleteTextures(deadTexIds.toIntArray())
        }
        var pollDeadProg = deadProgramQueue.poll()
        while (pollDeadProg != null) {
            glDeleteProgram(pollDeadProg)
            pollDeadProg = deadProgramQueue.poll()
        }
        var poll = workQueue.poll()
        while (poll != null) {
            try {
                poll.second.retrieve()
            } catch (t: Throwable) {
                poll.third.value = t
            } finally {
                poll.first.countDown()
            }
            poll = workQueue.poll()
        }
    }

    private fun drawTriangles(triangles: Pair<FloatArray, IntArray>) {
        glUseProgram(dynamicGeometryProgram)
        glUniformMatrix4fv(mvpMatrixUniformDynamicGeometry.location, false, mvpMatrix.get(0, floatBuffer))
        dynamicGeometry3D.render(triangles.first, triangles.second, positionAttributeDynamicGeometry)
    }

    private fun drawNormalsAndSAo(heightMapTexture: TextureId, heightScale: Float, uvScale: Float) {
        glUseProgram(normalAndAoProgram)
        glUniformMatrix4fv(mvpMatrixUniformNormalAndAo.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform1f(heightScaleUniformNormalAndAo.location, heightScale)
        glUniform1f(uvScaleUniformNormalAndAo.location, uvScale)
        glUniform1i(heightMapTextureUniformNormalAndAo.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, heightMapTexture.id)
        normalAndAoImagePlane.render()
    }

    class TextureRenderer(val width: Int, val height: Int) {

        val fboId = glGenFramebuffers()
        val renderTextureId: Int
        val depthBufferId: Int

        init {
            glBindFramebuffer(GL_FRAMEBUFFER, fboId)

            depthBufferId = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, depthBufferId)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthBufferId)

            renderTextureId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, renderTextureId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, MemoryUtil.NULL)
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, renderTextureId, 0)

            glDrawBuffers(intArrayOf(GL_COLOR_ATTACHMENT0))
            val frameBufferStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER)
            if (frameBufferStatus != GL_FRAMEBUFFER_COMPLETE) {
                throw RuntimeException("Framebuffer not created successfully. Code: $frameBufferStatus")
            }
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
        }

        fun bind() {
            glBindFramebuffer(GL_FRAMEBUFFER, fboId)
            glViewport(0, 0, width, height)
        }

        fun unbind() {
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
        }

        fun newRedTextureFloat(minFilter: Int, magFilter: Int): TextureId {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, width, height, 0, GL_RED, GL_FLOAT, MemoryUtil.NULL)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            return TextureIdImpl(newTexId)
        }

        fun newRedTextureShort(minFilter: Int, magFilter: Int): TextureId {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R16, width, height, 0, GL_RED, GL_UNSIGNED_SHORT, MemoryUtil.NULL)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            return TextureIdImpl(newTexId)
        }

        fun newRedTextureByte(minFilter: Int, magFilter: Int): TextureId {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, height, 0, GL_RED, GL_UNSIGNED_BYTE, MemoryUtil.NULL)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            return TextureIdImpl(newTexId)
        }

        fun newRgbaTextureByte(minFilter: Int, magFilter: Int): TextureId {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            return TextureIdImpl(newTexId)
        }

        fun copyTexture(textureId: TextureId) {
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
        }

        @Suppress("unused")
        fun finalize() {
            glDeleteFramebuffers(fboId)
            glDeleteRenderbuffers(depthBufferId)
            glDeleteTextures(renderTextureId)
        }
    }

    class DynamicGeometry3D {

        var vao = 0
        var vbo = 0
        var ibo = 0

        val floatsPerVertex = 3
        val stride = floatsPerVertex * 4

        init {
            try {
                vao = glGenVertexArrays()

                if (vao > 0) {
                    glBindVertexArray(vao)

                    vbo = glGenBuffers()
                    ibo = glGenBuffers()

                    if (vbo > 0 && ibo > 0) {
                        glBindBuffer(GL_ARRAY_BUFFER, vbo)
                        glBufferData(GL_ARRAY_BUFFER, NULL, GL_STATIC_DRAW)
                        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
                        glBufferData(GL_ELEMENT_ARRAY_BUFFER, NULL, GL_STATIC_DRAW)
                    } else {
                        throw RuntimeException("error setting up buffers")
                    }

                    glBindVertexArray(0)
                } else {
                    throw RuntimeException("error generating vao")
                }
            } catch (t: Throwable) {
                LOG.error(t)
                throw t
            }
        }

        fun render(heightMapVertexData: FloatArray, heightMapIndexData: IntArray, positionAttribute: ShaderAttribute) {
            if (vao > 0) {
                glBindVertexArray(vao)
                glBindBuffer(GL_ARRAY_BUFFER, vbo)
                glBufferData(GL_ARRAY_BUFFER, heightMapVertexData, GL_STATIC_DRAW)
                if (positionAttribute.location >= 0) {
                    glEnableVertexAttribArray(positionAttribute.location)
                    glVertexAttribPointer(positionAttribute.location, 3, GL_FLOAT, false, stride, 0)
                }
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, heightMapIndexData, GL_STATIC_DRAW)
                glDrawElements(GL_TRIANGLES, heightMapIndexData.size, GL_UNSIGNED_INT, 0)
                glBindVertexArray(0)
            }
        }

        @Suppress("unused")
        fun finalize() {
            glDeleteBuffers(vbo)
            glDeleteBuffers(ibo)
            glDeleteVertexArrays(vao)
        }
    }

    class DynamicGeometry2D {

        var vao = 0
        var vbo = 0
        var ibo = 0

        val floatsPerVertex = 2
        val stride = floatsPerVertex * 4

        init {
            try {
                vao = glGenVertexArrays()

                if (vao > 0) {
                    glBindVertexArray(vao)

                    vbo = glGenBuffers()
                    ibo = glGenBuffers()

                    if (vbo > 0 && ibo > 0) {
                        glBindBuffer(GL_ARRAY_BUFFER, vbo)
                        glBufferData(GL_ARRAY_BUFFER, NULL, GL_STATIC_DRAW)
                        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
                        glBufferData(GL_ELEMENT_ARRAY_BUFFER, NULL, GL_STATIC_DRAW)
                    } else {
                        throw RuntimeException("error setting up buffers")
                    }

                    glBindVertexArray(0)
                } else {
                    throw RuntimeException("error generating vao")
                }
            } catch (t: Throwable) {
                LOG.error(t)
                throw t
            }
        }

        fun render(heightMapVertexData: FloatArray, heightMapIndexData: IntArray, positionAttribute: ShaderAttribute) {
            if (vao > 0) {
                glBindVertexArray(vao)
                glBindBuffer(GL_ARRAY_BUFFER, vbo)
                glBufferData(GL_ARRAY_BUFFER, heightMapVertexData, GL_STATIC_DRAW)
                if (positionAttribute.location >= 0) {
                    glEnableVertexAttribArray(positionAttribute.location)
                    glVertexAttribPointer(positionAttribute.location, 2, GL_FLOAT, false, stride, 0)
                }
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, heightMapIndexData, GL_STATIC_DRAW)
                glDrawElements(GL_TRIANGLES, heightMapIndexData.size, GL_UNSIGNED_INT, 0)
                glBindVertexArray(0)
            }
        }

        @Suppress("unused")
        fun finalize() {
            glDeleteBuffers(vbo)
            glDeleteBuffers(ibo)
            glDeleteVertexArrays(vao)
        }
    }
}