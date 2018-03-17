package com.grimfox.gec.util

import com.grimfox.logging.LOG
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

class ImagePlane(val width: Float, positionAttribute: ShaderAttribute, uvAttribute: ShaderAttribute, val offset: Float = 0.0f) {

    private val minXY = width / -2.0f + offset
    private val maxXY = minXY + width

    private var vao = 0

    init {
        try {
            val floatsPerVertex = 4
            val vertexCount = 4
            val vertexData = BufferUtils.createFloatBuffer(vertexCount * floatsPerVertex)
            vertexData.put(minXY).put(maxXY).put(0.0f).put(0.0f)
            vertexData.put(maxXY).put(maxXY).put(1.0f).put(0.0f)
            vertexData.put(maxXY).put(minXY).put(1.0f).put(1.0f)
            vertexData.put(minXY).put(minXY).put(0.0f).put(1.0f)
            vertexData.flip()
            val indexData = BufferUtils.createIntBuffer(6)
            indexData.put(0).put(1).put(2).put(2).put(3).put(0)
            indexData.flip()
            vao = glGenVertexArrays()
            if (vao > 0) {
                val stride = floatsPerVertex * 4
                glBindVertexArray(vao)
                val vbo = glGenBuffers()
                val ibo = glGenBuffers()
                if (vbo > 0 && ibo > 0) {
                    glBindBuffer(GL_ARRAY_BUFFER, vbo)
                    glBufferData(GL_ARRAY_BUFFER, vertexData, GL_STATIC_DRAW)
                    if (positionAttribute.location >= 0) {
                        glEnableVertexAttribArray(positionAttribute.location)
                        glVertexAttribPointer(positionAttribute.location, 2, GL_FLOAT, false, stride, 0)
                    }
                    if (uvAttribute.location >= 0) {
                        glEnableVertexAttribArray(uvAttribute.location)
                        glVertexAttribPointer(uvAttribute.location, 2, GL_FLOAT, false, stride, 8)
                    }
                    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
                    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexData, GL_STATIC_DRAW)
                } else {
                    throw RuntimeException("error setting up buffers")
                }
                glBindVertexArray(0)
                glDeleteBuffers(vbo)
                glDeleteBuffers(ibo)
            } else {
                throw RuntimeException("error generating vao")
            }
        } catch (t: Throwable) {
            LOG.error(t)
            throw t
        }
    }

    fun render() {
        if (vao > 0) {
            glBindVertexArray(vao)
            glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0)
            glBindVertexArray(0)
        }
    }

    @Suppress("unused")
    fun finalize() {
        glDeleteVertexArrays(vao)
    }
}