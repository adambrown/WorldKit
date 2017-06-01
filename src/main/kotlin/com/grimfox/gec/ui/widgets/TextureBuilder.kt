package com.grimfox.gec.ui.widgets

import com.grimfox.gec.opengl.*
import com.grimfox.gec.ui.LOG
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.mRef
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL13.GL_MULTISAMPLE
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

object TextureBuilder {

    private val deadTextureQueue = ConcurrentLinkedQueue<Int>()

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

    private class RenderValueCollector<T : Any>(val retriever: (TextureRenderer) -> T) {
        lateinit var value: T

        fun retrieve(renderer: TextureRenderer) {
            value = retriever(renderer)
        }
    }

    private class ExtractValueCollector<T : Any>(val retriever: () -> T) {
        lateinit var value: T

        fun retrieve() {
            value = retriever()
        }
    }

    private class RawValueCollector<T : Any>(val retriever: () -> T) {
        lateinit var value: T

        fun retrieve() {
            value = retriever()
        }
    }

    private val renderQueue = ConcurrentLinkedQueue<Pair<Pair<FloatArray, IntArray>, Triple<CountDownLatch, RenderValueCollector<*>, MutableReference<Throwable?>>>>()
    private val extractQueue = ConcurrentLinkedQueue<Triple<CountDownLatch, ExtractValueCollector<*>, MutableReference<Throwable?>>>()
    private val rawQueue = ConcurrentLinkedQueue<Triple<CountDownLatch, RawValueCollector<*>, MutableReference<Throwable?>>>()
    private val mvpMatrixUniformDynamicGeometry = ShaderUniform("modelViewProjectionMatrix")
    private val positionAttributeDynamicGeometry = ShaderAttribute("position")
    private val mvpMatrix = Matrix4f()
    private val floatBuffer = BufferUtils.createFloatBuffer(16)
    private var dynamicGeometryProgram: Int = 0
    private var dynamicGeometry: DynamicHeightGeometry
    private var textureRenderer: TextureRenderer

    init {
        val dynamicGeometryVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/dynamic-geometry.vert"))
        val dynamicGeometryFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/dynamic-geometry.frag"))

        dynamicGeometryProgram = createAndLinkProgram(
                listOf(dynamicGeometryVertexShader, dynamicGeometryFragmentShader),
                listOf(positionAttributeDynamicGeometry),
                listOf(mvpMatrixUniformDynamicGeometry))

        dynamicGeometry = DynamicHeightGeometry(positionAttributeDynamicGeometry, FloatArray(0), IntArray(0))

        textureRenderer = TextureRenderer(4096, 4096)
    }

    private fun <T : Any> renderTrianglesInternal(input: Pair<FloatArray, IntArray>, collector: RenderValueCollector<T>): T {
        val latch = CountDownLatch(1)
        val throwableReference = mRef(null as Throwable?)
        val result = Triple(latch, collector, throwableReference)
        renderQueue.add(input to result)
        while (latch.count > 0) {
            latch.await()
        }
        val throwable = throwableReference.value
        if (throwable != null) {
            throw throwable
        }
        return collector.value
    }

    fun renderTrianglesRedFloat(input: Pair<FloatArray, IntArray>): FloatArray {
        return renderTrianglesInternal(input, RenderValueCollector { it.readRedTextureFloat() })
    }

    fun renderTrianglesRedFloat(vertices: FloatArray, indices: IntArray): FloatArray {
        return renderTrianglesRedFloat(vertices to indices)
    }

    fun renderTrianglesRedShort(input: Pair<FloatArray, IntArray>): ShortArray {
        return renderTrianglesInternal(input, RenderValueCollector { it.readRedTextureShort() })
    }

    fun renderTrianglesRedShort(vertices: FloatArray, indices: IntArray): ShortArray {
        return renderTrianglesRedShort(vertices to indices)
    }

    fun renderTrianglesRedByte(input: Pair<FloatArray, IntArray>): ByteBuffer {
        return renderTrianglesInternal(input, RenderValueCollector { it.readRedTextureByte() })
    }

    fun renderTrianglesRedByte(vertices: FloatArray, indices: IntArray): ByteBuffer {
        return renderTrianglesRedByte(vertices to indices)
    }

    fun renderTrianglesRgbaByte(input: Pair<FloatArray, IntArray>): ByteBuffer {
        return renderTrianglesInternal(input, RenderValueCollector { it.readRgbaTextureByte() })
    }

    fun renderTrianglesRgbaByte(vertices: FloatArray, indices: IntArray): ByteBuffer {
        return renderTrianglesRgbaByte(vertices to indices)
    }


    fun renderTrianglesTexRedFloat(input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesInternal(input, RenderValueCollector { it.newRedTextureFloat(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedFloat(vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesTexRedFloat(vertices to indices, minFilter, magFilter)
    }

    fun renderTrianglesTexRedShort(input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesInternal(input, RenderValueCollector { it.newRedTextureShort(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedShort(vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesTexRedShort(vertices to indices, minFilter, magFilter)
    }

    fun renderTrianglesTexRedByte(input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesInternal(input, RenderValueCollector { it.newRedTextureByte(minFilter, magFilter) })
    }

    fun renderTrianglesTexRedByte(vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesTexRedByte(vertices to indices, minFilter, magFilter)
    }

    fun renderTrianglesTexRgbaByte(input: Pair<FloatArray, IntArray>, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesInternal(input, RenderValueCollector { it.newRgbaTextureByte(minFilter, magFilter) })
    }

    fun renderTrianglesTexRgbaByte(vertices: FloatArray, indices: IntArray, minFilter: Int, magFilter: Int): TextureId {
        return renderTrianglesTexRgbaByte(vertices to indices, minFilter, magFilter)
    }

    private fun <T : Any> extractTextureInternal(collector: ExtractValueCollector<T>): T {
        val latch = CountDownLatch(1)
        val throwableReference = mRef(null as Throwable?)
        extractQueue.add(Triple(latch, collector, throwableReference))
        while (latch.count > 0) {
            latch.await()
        }
        val throwable = throwableReference.value
        if (throwable != null) {
            throw throwable
        }
        return collector.value
    }

    fun extractTextureRedFloat(textureId: TextureId, width: Int): FloatArray {
        return extractTextureInternal(ExtractValueCollector {
            val matrix = FloatArray(width * width)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_FLOAT, matrix)
            matrix
        })
    }

    fun extractTextureRedShort(textureId: TextureId, width: Int): ShortArray {
        return extractTextureInternal(ExtractValueCollector {
            val matrix = ShortArray(width * width)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_SHORT, matrix)
            matrix
        })
    }

    fun extractTextureRedByte(textureId: TextureId, width: Int): ByteBuffer {
        return extractTextureInternal(ExtractValueCollector {
            val matrix = BufferUtils.createByteBuffer(width * width)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_BYTE, matrix)
            matrix
        })
    }

    fun extractTextureRgbaByte(textureId: TextureId, width: Int): ByteBuffer {
        return extractTextureInternal(ExtractValueCollector {
            val matrix = BufferUtils.createByteBuffer(width * width)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, matrix)
            matrix
        })
    }

    private fun <T : Any> buildTextureInternal(collector: RawValueCollector<T>): T {
        val latch = CountDownLatch(1)
        val throwableReference = mRef(null as Throwable?)
        rawQueue.add(Triple(latch, collector, throwableReference))
        while (latch.count > 0) {
            latch.await()
        }
        val throwable = throwableReference.value
        if (throwable != null) {
            throw throwable
        }
        return collector.value
    }

    fun buildTextureRedFloat(data: FloatArray, width: Int, minFilter: Int, magFilter: Int): TextureId {
        return extractTextureInternal(ExtractValueCollector {
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
        return extractTextureInternal(ExtractValueCollector {
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
        var pollRender = renderQueue.poll()
        while (pollRender != null) {
            try {
                mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 2.0f)
                glDisable(GL_BLEND)
                glDisable(GL_CULL_FACE)
                glDisable(GL_MULTISAMPLE)
                glEnable(GL_DEPTH_TEST)
                glDisable(GL_SCISSOR_TEST)
                glDisable(GL13.GL_MULTISAMPLE)
                textureRenderer.bind()
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                drawTriangles(pollRender.first)
                pollRender.second.second.retrieve(textureRenderer)
                textureRenderer.unbind()
            } catch (t: Throwable) {
                pollRender.second.third.value = t
            } finally {
                pollRender.second.first.countDown()
            }
            pollRender = renderQueue.poll()
        }
        var pollExtract = extractQueue.poll()
        while (pollExtract != null) {
            try {
                pollExtract.second.retrieve()
            } catch (t: Throwable) {
                pollExtract.third.value = t
            } finally {
                pollExtract.first.countDown()
            }
            pollExtract = extractQueue.poll()
        }
        var pollRaw = rawQueue.poll()
        while (pollRaw != null) {
            try {
                pollRaw.second.retrieve()
            } catch (t: Throwable) {
                pollRaw.third.value = t
            } finally {
                pollRaw.first.countDown()
            }
            pollRaw = rawQueue.poll()
        }
    }

    private fun drawTriangles(triangles: Pair<FloatArray, IntArray>) {
        glUseProgram(dynamicGeometryProgram)
        glUniformMatrix4fv(mvpMatrixUniformDynamicGeometry.location, false, mvpMatrix.get(0, floatBuffer))
        dynamicGeometry.render(triangles.first, triangles.second)
    }

    private class TextureRenderer(val width: Int, val height: Int) {

        val fboId = glGenFramebuffers()
        val renderTextureId: Int
        val depthBufferId: Int

        init {
            glBindFramebuffer(GL_FRAMEBUFFER, fboId)

            depthBufferId = glGenRenderbuffers()
            glBindRenderbuffer(GL_RENDERBUFFER, depthBufferId)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, width, height)

            renderTextureId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, renderTextureId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, MemoryUtil.NULL)

            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBufferId)
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

        fun readRedTextureFloat(): FloatArray {
            val matrix = FloatArray(width * height)
            glBindTexture(GL_TEXTURE_2D, renderTextureId)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_FLOAT, matrix)
            return matrix
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

        fun readRedTextureShort(): ShortArray {
            val matrix = ShortArray(width * height)
            glBindTexture(GL_TEXTURE_2D, renderTextureId)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_SHORT, matrix)
            return matrix
        }

        fun newRedTextureShort(minFilter: Int, magFilter: Int): TextureId {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, height, 0, GL_RED, GL_UNSIGNED_SHORT, MemoryUtil.NULL)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            return TextureId(newTexId)
        }

        fun readRedTextureByte(): ByteBuffer {
            val matrix = BufferUtils.createByteBuffer(width * height)
            glBindTexture(GL_TEXTURE_2D, renderTextureId)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RED, GL_UNSIGNED_BYTE, matrix)
            return matrix
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

        fun readRgbaTextureByte(): ByteBuffer {
            val matrix = BufferUtils.createByteBuffer(width * height)
            glBindTexture(GL_TEXTURE_2D, renderTextureId)
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, matrix)
            return matrix
        }

        fun newRgbaTextureByte(minFilter: Int, magFilter: Int): TextureId {
            val newTexId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, newTexId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height)
            return TextureId(newTexId)
        }

        @Suppress("unused")
        fun finalize() {
            glDeleteFramebuffers(fboId)
            glDeleteRenderbuffers(depthBufferId)
            glDeleteTextures(renderTextureId)
        }
    }

    private class DynamicHeightGeometry(positionAttribute: ShaderAttribute, heightMapVertexData: FloatArray, heightMapIndexData: IntArray) {

        var vao = 0
        var vbo = 0
        var ibo = 0

        init {
            try {
                vao = glGenVertexArrays()

                if (vao > 0) {
                    val floatsPerVertex = 3
                    val stride = floatsPerVertex * 4

                    glBindVertexArray(vao)

                    vbo = glGenBuffers()
                    ibo = glGenBuffers()

                    if (vbo > 0 && ibo > 0) {
                        glBindBuffer(GL_ARRAY_BUFFER, vbo)
                        glBufferData(GL_ARRAY_BUFFER, heightMapVertexData, GL_STATIC_DRAW)
                        if (positionAttribute.location >= 0) {
                            glEnableVertexAttribArray(positionAttribute.location)
                            glVertexAttribPointer(positionAttribute.location, 3, GL_FLOAT, false, stride, 0)
                        }
                        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
                        glBufferData(GL_ELEMENT_ARRAY_BUFFER, heightMapIndexData, GL_STATIC_DRAW)
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

        fun render(heightMapVertexData: FloatArray, heightMapIndexData: IntArray) {
            if (vao > 0) {
                glBindVertexArray(vao)
                glBindBuffer(GL_ARRAY_BUFFER, vbo)
                glBufferData(GL_ARRAY_BUFFER, heightMapVertexData, GL_STATIC_DRAW)
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