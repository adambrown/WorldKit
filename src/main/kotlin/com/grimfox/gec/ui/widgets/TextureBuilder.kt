package com.grimfox.gec.ui.widgets

import com.grimfox.gec.model.FloatArrayMatrix
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.opengl.*
import com.grimfox.gec.ui.LOG
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.mRef
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

object TextureBuilder {

    private val NULL_MATRIX: Matrix<Float> = FloatArrayMatrix(0)
    private val renderQueue = ConcurrentLinkedQueue<Pair<Pair<FloatArray, IntArray>, Triple<CountDownLatch, MutableReference<Matrix<Float>>, MutableReference<Throwable?>>>>()
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

    fun renderTriangles(input: Pair<FloatArray, IntArray>): Matrix<Float> {
        val latch = CountDownLatch(1)
        val returnReference = mRef(NULL_MATRIX)
        val throwableReference = mRef(null as Throwable?)
        val result = Triple(latch, returnReference, throwableReference)
        renderQueue.add(input to result)
        while (latch.count > 0) {
            latch.await()
        }
        val throwable = throwableReference.value
        if (throwable != null) {
            throw throwable
        }
        val returnValue = returnReference.value
        if (returnValue === NULL_MATRIX) {
            throw RuntimeException("Failed to render triangles.")
        }
        return returnValue
    }

    fun renderTriangles(vertices: FloatArray, indices: IntArray): Matrix<Float> {
        return renderTriangles(vertices to indices)
    }

    fun onDrawFrame() {
        var poll = renderQueue.poll()
        while (poll != null) {
            try {
                mvpMatrix.setOrtho(0.0f, 1.0f, 0.0f, 1.0f, -1.0f, 2.0f)

                glDisable(GL_BLEND)
                glDisable(GL_CULL_FACE)
                glEnable(GL_DEPTH_TEST)
                glDisable(GL_SCISSOR_TEST)
                glDisable(GL13.GL_MULTISAMPLE)

                textureRenderer.bind()
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                drawTriangles(poll.first)
                poll.second.second.value = textureRenderer.readTexture()
                textureRenderer.unbind()
            } catch (t: Throwable) {
                poll.second.third.value = t
            } finally {
                poll.second.first.countDown()
            }
            poll = renderQueue.poll()
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

            glEnable(GL_TEXTURE_2D)
            renderTextureId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, renderTextureId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R16, width, height, 0, GL_RED, GL_UNSIGNED_SHORT, MemoryUtil.NULL)

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

        fun readTexture(): Matrix<Float> {
            val matrix = FloatArrayMatrix(width)
            glReadBuffer(GL_COLOR_ATTACHMENT0)
            glReadPixels(0, 0, width, height, GL_RED, GL_FLOAT, matrix.array)
            return matrix
        }

        fun finalize() {
            glDeleteFramebuffers(fboId)
            glDeleteRenderbuffers(depthBufferId)
            glDeleteTextures(renderTextureId)
        }
    }

    private class DynamicHeightGeometry(positionAttribute: ShaderAttribute, heightMapVertexData: FloatArray, heightMapIndexData: IntArray, var vertexCount: Int = heightMapIndexData.size) {

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

        fun finalize() {
            glDeleteBuffers(vbo)
            glDeleteBuffers(ibo)
            glDeleteVertexArrays(vao)
        }
    }
}