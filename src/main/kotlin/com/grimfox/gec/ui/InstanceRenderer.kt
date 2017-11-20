package com.grimfox.gec.ui

import com.grimfox.gec.doOnMainThread
import com.grimfox.gec.util.ShaderAttribute
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.glDrawArraysInstanced
import org.lwjgl.opengl.GL33.glVertexAttribDivisor
import org.lwjgl.system.MemoryUtil

class InstanceRenderer(
        width: Float,
        private val positionAttribute: ShaderAttribute,
        private val uvAttribute: ShaderAttribute,
        private val instancePositionAttribute: ShaderAttribute) {

    private var vao = -1
    private var vbo = -1
    private var instancePositionBufferId = -1

    init {
        try {
            val vertexData = BufferUtils.createFloatBuffer(16)
            vertexData.put(0.0f).put(0.0f).put(0.0f).put(1.0f)
            vertexData.put(width).put(0.0f).put(1.0f).put(1.0f)
            vertexData.put(0.0f).put(width).put(0.0f).put(0.0f)
            vertexData.put(width).put(width).put(1.0f).put(0.0f)
            vertexData.flip()
            vao = glGenVertexArrays()
            if (vao > 0) {
                glBindVertexArray(vao)
                vbo = glGenBuffers()
                instancePositionBufferId = glGenBuffers()
                if (vbo > 0 && instancePositionBufferId > 0) {
                    glBindBuffer(GL_ARRAY_BUFFER, vbo)
                    glBufferData(GL_ARRAY_BUFFER, vertexData, GL_STATIC_DRAW)
                    if (positionAttribute.location >= 0) {
                        glEnableVertexAttribArray(positionAttribute.location)
                        glVertexAttribPointer(positionAttribute.location, 2, GL_FLOAT, false, 16, 0)
                    }
                    if (uvAttribute.location >= 0) {
                        glEnableVertexAttribArray(uvAttribute.location)
                        glVertexAttribPointer(uvAttribute.location, 2, GL_FLOAT, false, 16, 8)
                    }
                    createInstanceAttributeVecBuffer(instancePositionAttribute, instancePositionBufferId, 4)
                } else {
                    throw RuntimeException("error setting up buffers")
                }
                glBindVertexArray(0)
            } else {
                throw RuntimeException("error generating vao")
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            throw t
        }
    }

    fun render(instances: List<RenderableInstance>) {
        if (vbo > 0 && instancePositionBufferId > 0) {
            glBindVertexArray(vao)

            val instanceCount = instances.size
            val positions = FloatArray(instanceCount * 4)

            instances.forEachIndexed { i, it ->
                it.setInstanceValues(i, positions)
            }

            bufferInstanceData(positions, instancePositionBufferId)

            setAttribDivisor(positionAttribute, 0)
            setAttribDivisor(uvAttribute, 0)
            setAttribDivisor(instancePositionAttribute,1)

            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount)

            glBindVertexArray(0)
        }
    }

    private fun setAttribDivisor(attribute: ShaderAttribute, divisor: Int) {
        if (attribute.location > 0) {
            glVertexAttribDivisor(attribute.location, divisor)
        }
    }

    private fun bufferInstanceData(data: FloatArray, bufferId: Int) {
        glBindBuffer(GL_ARRAY_BUFFER, bufferId)
        glBufferData(GL_ARRAY_BUFFER, data.size * 4L, GL_STREAM_DRAW)
        glBufferSubData(GL_ARRAY_BUFFER, 0, data)
    }

    private fun createInstanceAttributeVecBuffer(attribute: ShaderAttribute, bufferId: Int, vecSize: Int) {
        glBindBuffer(GL_ARRAY_BUFFER, bufferId)
        glBufferData(GL_ARRAY_BUFFER, MemoryUtil.NULL, GL_STREAM_DRAW)
        if (attribute.location >= 0) {
            glEnableVertexAttribArray(attribute.location)
            glVertexAttribPointer(attribute.location, vecSize, GL_FLOAT, false, 0, 0)
        }
    }

    @Suppress("unused")
    fun finalize() {
        doOnMainThread {
            glDeleteBuffers(vbo)
            glDeleteBuffers(instancePositionBufferId)
            glDeleteVertexArrays(vao)
        }
    }
}
