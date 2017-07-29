package com.grimfox.gec.ui.widgets

import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.ui.LOG
import com.grimfox.gec.util.*
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_MULTISAMPLE
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

object TextureBuilder {

    private var nvg: Long = -1

    fun init(nvg: Long) {
        this.nvg = nvg
    }

    private val deadTextureQueue = ConcurrentLinkedQueue<Int>()
    private val deadProgramQueue = ConcurrentLinkedQueue<Int>()

    class TextureId(val id: Int) {

        @Volatile private var free: Boolean = false

        fun free() {
            if (!free) {
                synchronized(this) {
                    if (!free) {
                        free = true
                        deadTextureQueue.add(id)
                    }
                }
            }
        }

        @Suppress("unused")
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
    private var textureRenderer: TextureRenderer

    init {
        val dynamicGeometryVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/dynamic-geometry.vert"))
        val dynamicGeometryFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/dynamic-geometry.frag"))

        dynamicGeometryProgram = createAndLinkProgram(
                listOf(dynamicGeometryVertexShader, dynamicGeometryFragmentShader),
                listOf(positionAttributeDynamicGeometry),
                listOf(mvpMatrixUniformDynamicGeometry))

        dynamicGeometry3D = DynamicGeometry3D()
        dynamicGeometry2D = DynamicGeometry2D()
        textureRenderer = TextureRenderer(4096, 4096)
    }

    private fun <T : Any> doDeferredOpenglWork(collector: ValueCollector<T>): T {
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
        return collector.value
    }

    fun buildShaderProgram(builder: () -> Int): ShaderProgramId {
        return ShaderProgramId(doDeferredOpenglWork(ValueCollector { builder() }))
    }

    fun <T : Any> render(builder: (dynamicGeometry3D: DynamicGeometry3D, dynamicGeometry2D: DynamicGeometry2D, textureRenderer: TextureRenderer) -> T): T {
        return doDeferredOpenglWork(ValueCollector {
            builder(dynamicGeometry3D, dynamicGeometry2D, textureRenderer)
        })
    }

    private fun <T : Any> renderTrianglesInternal(input: Pair<FloatArray, IntArray>, collector: (TextureRenderer) -> T): T {
        return doDeferredOpenglWork(ValueCollector {
            mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 2.0f)
            glDisable(GL_BLEND)
            glDisable(GL_CULL_FACE)
            glDisable(GL_MULTISAMPLE)
            glEnable(GL_DEPTH_TEST)
            glDisable(GL_SCISSOR_TEST)
            glDisable(GL_MULTISAMPLE)
            textureRenderer.bind()
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            drawTriangles(input)
            val retVal = collector(textureRenderer)
            textureRenderer.unbind()
            retVal
        })
    }

    private fun <T : Any> renderNvgInternal(collector: (TextureRenderer) -> T): T {
        return doDeferredOpenglWork(ValueCollector { collector(textureRenderer) })
    }

    fun renderLandImage(landBodyPolygons: List<Pair<List<Point2F>, List<List<Point2F>>>>): TextureId {
        return renderNvgInternal { textureRenderer ->
            val width = textureRenderer.width
            val height = textureRenderer.height

            textureRenderer.bind()
            glDisable(GL_MULTISAMPLE)

            nvgSave(nvg)
            glViewport(0, 0, width, height)
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
            nvgBeginFrame(nvg, width, height, 1.0f)

            val color = NVGColor.create()
            rgba(255, 255, 255, 255, color)
            nvgFillColor(nvg, color)
            landBodyPolygons.forEach {
                nvgBeginPath(nvg)
                drawShape(nvg, it.first, true)
                it.second.forEach {
                    drawHole(nvg, it)
                }
                nvgFill(nvg)
            }

            nvgEndFrame(nvg)
            nvgRestore(nvg)
            val retVal = textureRenderer.newRedTextureByte(GL_LINEAR, GL_LINEAR)
            textureRenderer.unbind()
            retVal
        }
    }

    fun rgba(r: Int, g: Int, b: Int, a: Int, color: NVGColor): NVGColor {
        return nvgRGBA(r.toByte(), g.toByte(), b.toByte(), a.toByte(), color)
    }

    fun rgba(r: Float, g: Float, b: Float, a: Float, color: NVGColor): NVGColor {
        return nvgRGBAf(r, g, b, a, color)
    }

    fun renderMapImage(landBodyPolygons: List<Pair<List<Point2F>, List<List<Point2F>>>>, riverPolygons: List<List<Point2F>>, mountainPolygons: List<List<Point2F>>, ignoredPolygons: List<List<Point2F>>, pendingDeletePolygons: List<List<Point2F>> = listOf(), target: TextureId? = null): TextureId {
        return renderNvgInternal { textureRenderer ->
            val width = textureRenderer.width
            val height = textureRenderer.height

            textureRenderer.bind()
            glEnable(GL_MULTISAMPLE)

            nvgSave(nvg)
            glViewport(0, 0, width, height)
            glClearColor(0.34f, 0.35f, 0.9f, 1.0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
            nvgBeginFrame(nvg, width, height, 1.0f)

            val color = NVGColor.create()
            rgba(110, 210, 115, 255, color)
            nvgFillColor(nvg, color)
            landBodyPolygons.forEach {
                nvgBeginPath(nvg)
                drawShape(nvg, it.first, true)
                it.second.forEach {
                    drawHole(nvg, it)
                }
                nvgFill(nvg)
            }
            nvgStrokeWidth(nvg, 6.0f)
            rgba(40, 45, 245, 255, color)
            nvgStrokeColor(nvg, color)
            riverPolygons.forEach {
                drawShape(nvg, it, false)
                nvgStroke(nvg)
            }
            rgba(245, 45, 40, 255, color)
            nvgStrokeColor(nvg, color)
            mountainPolygons.forEach {
                drawShape(nvg, it, false)
                nvgStroke(nvg)
            }
            rgba(220, 220, 220, 255, color)
            nvgStrokeColor(nvg, color)
            ignoredPolygons.forEach {
                drawShape(nvg, it, false)
                nvgStroke(nvg)
            }
            rgba(220, 100, 210, 255, color)
            nvgStrokeColor(nvg, color)
            pendingDeletePolygons.forEach {
                drawShape(nvg, it, false)
                nvgStroke(nvg)
            }
            rgba(0, 0, 0, 255, color)
            nvgStrokeWidth(nvg, 6.0f)
            nvgStrokeColor(nvg, color)
            landBodyPolygons.forEach {
                drawShape(nvg, it.first, true)
                nvgStroke(nvg)
                it.second.forEach {
                    drawShape(nvg, it, true)
                    nvgStroke(nvg)
                }
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

    fun renderSplineSelectors(splines: List<Pair<Int, List<Point2F>>>): TextureId {
        return renderNvgInternal { textureRenderer ->
            val width = textureRenderer.width
            val height = textureRenderer.height

            textureRenderer.bind()
            glDisable(GL_MULTISAMPLE)

            nvgSave(nvg)
            glViewport(0, 0, width, height)
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
            nvgBeginFrame(nvg, width, height, 1.0f)

            val color = NVGColor.create()
            nvgStrokeWidth(nvg, 80.0f)
            splines.forEach { (index, spline) ->
                val r = index and 0x000000FF
                val g = (index shr 8) and 0x000000FF
                rgba(r, g, 0, 255, color)
                nvgStrokeColor(nvg, color)
                drawShape(nvg, spline, false)
                nvgStroke(nvg)
            }

            nvgEndFrame(nvg)
            nvgRestore(nvg)
            val retVal = textureRenderer.newRgbaTextureByte(GL_NEAREST, GL_NEAREST)
            textureRenderer.unbind()
            retVal
        }
    }

    fun renderSplines(landBodyPolygons: List<Pair<List<Point2F>, List<List<Point2F>>>>, riverPolygons: List<List<Point2F>>, mountainPolygons: List<List<Point2F>>): TextureId {
        return renderNvgInternal { textureRenderer ->
            val width = textureRenderer.width
            val height = textureRenderer.height

            textureRenderer.bind()
            glEnable(GL_MULTISAMPLE)

            nvgSave(nvg)
            glViewport(0, 0, width, height)
            if (landBodyPolygons.isEmpty() && riverPolygons.isEmpty() && mountainPolygons.isEmpty()) {
                glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            } else {
                glClearColor(0.0f, 0.8f, 0.0f, 1.0f)
            }
            glClear(GL_COLOR_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)
            nvgBeginFrame(nvg, width, height, 1.0f)

            val color = NVGColor.create()
            rgba(255, 0, 0, 255, color)
            nvgFillColor(nvg, color)
            landBodyPolygons.forEach {
                nvgBeginPath(nvg)
                drawShape(nvg, it.first, true)
                it.second.forEach {
                    drawHole(nvg, it)
                }
                nvgFill(nvg)
            }
            nvgStrokeWidth(nvg, 8.0f)
            rgba(0, 255, 0, 255, color)
            nvgStrokeColor(nvg, color)
            riverPolygons.forEach {
                drawShape(nvg, it, false)
                nvgStroke(nvg)
            }
            mountainPolygons.forEach {
                drawShape(nvg, it, false)
                nvgStroke(nvg)
            }
            nvgStrokeWidth(nvg, 4.0f)
            rgba(255, 255, 0, 255, color)
            nvgStrokeColor(nvg, color)
            riverPolygons.forEach {
                drawShape(nvg, it, false)
                nvgStroke(nvg)
            }
            mountainPolygons.forEach {
                drawShape(nvg, it, false)
                nvgStroke(nvg)
            }
            nvgStrokeWidth(nvg, 8.0f)
            rgba(255, 255, 0, 255, color)
            nvgStrokeColor(nvg, color)
            landBodyPolygons.forEach {
                drawShape(nvg, it.first, true)
                nvgStroke(nvg)
                it.second.forEach {
                    drawShape(nvg, it, true)
                    nvgStroke(nvg)
                }
            }
            nvgStrokeWidth(nvg, 4.0f)
            rgba(0, 255, 0, 255, color)
            nvgStrokeColor(nvg, color)
            landBodyPolygons.forEach {
                drawShape(nvg, it.first, true)
                nvgStroke(nvg)
                it.second.forEach {
                    drawShape(nvg, it, true)
                    nvgStroke(nvg)
                }
            }

            nvgEndFrame(nvg)
            nvgRestore(nvg)
            val retVal = textureRenderer.newRgbaTextureByte(GL_LINEAR, GL_LINEAR)
            textureRenderer.unbind()
            retVal
        }
    }

    fun drawHole(nvg: Long, points: List<Point2F>) {
        drawLines(nvg, points, true, true, 4096.0f)
        nvgPathWinding(nvg, NVG_HOLE)
    }

    fun drawShape(nvg: Long, points: List<Point2F>, isClosed: Boolean, isComposite: Boolean = false) {
        if (!isComposite) {
            nvgBeginPath(nvg)
        }
        drawLines(nvg, points, isClosed, true, 4096.0f)
        if (isClosed) {
            if (!isComposite) {
                nvgPathWinding(nvg, NVG_SOLID)
                nvgClosePath(nvg)
            }
        }
    }

    private fun drawLines(nvg: Long, points: List<Point2F>, isClosed: Boolean, moveToFirst: Boolean, multiplier: Float) {
        for (i in if (isClosed) 1..points.size else 1..points.size - 1) {
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

    fun renderTrianglesRedFloat(input: Pair<FloatArray, IntArray>): FloatArray {
        return renderTrianglesInternal(input, {
            val id = it.newRedTextureFloat(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRedFloat(id, 4096)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRedFloat(vertices: FloatArray, indices: IntArray): FloatArray {
        return renderTrianglesRedFloat(vertices to indices)
    }

    fun renderTrianglesRedShort(input: Pair<FloatArray, IntArray>): ShortArray {
        return renderTrianglesInternal(input, {
            val id = it.newRedTextureShort(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRedShort(id, 4096)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRedShort(vertices: FloatArray, indices: IntArray): ShortArray {
        return renderTrianglesRedShort(vertices to indices)
    }

    fun renderTrianglesRedByte(input: Pair<FloatArray, IntArray>): ByteBuffer {
        return renderTrianglesInternal(input, {
            val id = it.newRedTextureByte(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRedByte(id, 4096)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRedByte(vertices: FloatArray, indices: IntArray): ByteBuffer {
        return renderTrianglesRedByte(vertices to indices)
    }

    fun renderTrianglesRgbaByte(input: Pair<FloatArray, IntArray>): ByteBuffer {
        return renderTrianglesInternal(input, {
            val id = it.newRgbaTextureByte(GL_NEAREST, GL_NEAREST)
            val retVal = extractTextureRgbaByte(id, 4096)
            id.free()
            retVal
        })
    }

    fun renderTrianglesRgbaByte(vertices: FloatArray, indices: IntArray): ByteBuffer {
        return renderTrianglesRgbaByte(vertices to indices)
    }


    fun renderTrianglesTexRedFloat(input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesInternal(input, { it.newRedTextureFloat(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedFloat(vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesTexRedFloat(vertices to indices, minFilter, magFilter)
    }

    fun renderTrianglesTexRedShort(input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesInternal(input, { it.newRedTextureShort(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedShort(vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesTexRedShort(vertices to indices, minFilter, magFilter)
    }

    fun renderTrianglesTexRedByte(input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesInternal(input, { it.newRedTextureByte(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedByte(vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesTexRedByte(vertices to indices, minFilter, magFilter)
    }

    fun renderTrianglesTexRgbaByte(input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesInternal(input, { it.newRgbaTextureByte(minFilter, magFilter) })
    }

    fun renderTrianglesTexRgbaByte(vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesTexRgbaByte(vertices to indices, minFilter, magFilter)
    }

    fun renderTrianglesToTexture(vertices: FloatArray, indices: IntArray, textureId: TextureId): TextureId {
        return renderTrianglesToTexture(vertices to indices, textureId)
    }

    fun renderTrianglesToTexture(input: Pair<FloatArray, IntArray>, textureId: TextureId): TextureId {
        return renderTrianglesInternal(input, {
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
            TextureId(newTexId)
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
            TextureId(newTexId)
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
            TextureId(newTexId)
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
            TextureId(newTexId)
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
            glPixelStorei(GL_UNPACK_ALIGNMENT, 2)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, width, 0, GL_RED, GL_UNSIGNED_BYTE, data)
            glBindTexture(GL_TEXTURE_2D, 0)
            TextureId(newTexId)
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
            return TextureId(newTexId)
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
            return TextureId(newTexId)
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
            return TextureId(newTexId)
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
            return TextureId(newTexId)
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
                LOG.error(t.message, t)
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
                LOG.error(t.message, t)
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