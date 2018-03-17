package com.grimfox.gec.util

import com.grimfox.logging.LOG
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*

class HexGrid(val width: Float, xResolution: Int, positionAttribute: ShaderAttribute, uvAttribute: ShaderAttribute, private val useStrips: Boolean) {

    private val halfXIncrement = width / (xResolution * 2 - 1)
    private val xIncrement = halfXIncrement * 2
    private val yResolution = Math.round(width / (Math.sqrt(3.0) * halfXIncrement)).toInt() + 1
    private val yIncrement = width / (yResolution - 1)
    private val halfUIncrement = 1.0f / (xResolution * 2 - 1)
    private val uIncrement = halfUIncrement * 2
    private val vIncrement = 1.0f / (yResolution - 1)
    private val minXY = width / -2.0f
    private val maxXY = minXY + width

    private var vao = 0
    private var indexCount = 0

    init {
        try {
            val floatsPerVertex = 4
            val vertexCount = xResolution * yResolution + 2
            val heightMapVertexData = BufferUtils.createFloatBuffer(vertexCount * floatsPerVertex)
            for (y in 0 until yResolution) {
                if (y == 0) {
                    heightMapVertexData.put(minXY).put(maxXY).put(0.0f).put(0.0f)
                }
                val yOffset = maxXY - y * yIncrement
                val vOffset = if (y == yResolution - 1) 1.0f else y * vIncrement
                val isEven = y % 2 == 0
                val xOffset = minXY + if (isEven) halfXIncrement else 0.0f
                val uOffset = if (isEven) halfUIncrement else 0.0f
                for (x in 0 until xResolution) {
                    heightMapVertexData.put(xOffset + x * xIncrement)
                    heightMapVertexData.put(yOffset)
                    heightMapVertexData.put(uOffset + x * uIncrement)
                    heightMapVertexData.put(vOffset)
                }
                if (y == yResolution - 1) {
                    if (isEven) {
                        heightMapVertexData.put(minXY)
                    } else {
                        heightMapVertexData.put(minXY + width)
                    }
                    heightMapVertexData.put(minXY)
                    if (isEven) {
                        heightMapVertexData.put(0.0f)
                    } else {
                        heightMapVertexData.put(1.0f)
                    }
                    heightMapVertexData.put(1.0f)
                }
            }
            heightMapVertexData.flip()
            val stripCount = yResolution - 1
            val scaffoldVerts = (stripCount - 1) + 2
            val vertsPerStrip = xResolution * 2
            indexCount = if (useStrips) { stripCount * vertsPerStrip + scaffoldVerts } else { (2 * (xResolution - 1) * (yResolution - 1) + yResolution) * 3 }

            val heightMapIndexData = if (useStrips) {
                val heightMapIndexData = BufferUtils.createIntBuffer(indexCount)
                for (strip in 0 until stripCount) {
                    if (strip == 0) {
                        heightMapIndexData.put(0)
                    }
                    if (strip % 2 == 0) {
                        var topStart = (strip * xResolution) + 1
                        var bottomStart = topStart + xResolution
                        for (i in 0 until xResolution) {
                            heightMapIndexData.put(bottomStart++)
                            heightMapIndexData.put(topStart++)
                        }
                        if (strip != stripCount - 1) {
                            heightMapIndexData.put(bottomStart + xResolution - 1)
                        }
                    } else {
                        val topStart = (strip * xResolution) + 1
                        val bottomStart = topStart + xResolution
                        val scaffold = bottomStart + xResolution
                        var bottomEnd = scaffold - 1
                        var topEnd = bottomStart - 1
                        for (i in 0 until xResolution) {
                            heightMapIndexData.put(bottomEnd--)
                            heightMapIndexData.put(topEnd--)
                        }
                        if (strip != stripCount - 1) {
                            heightMapIndexData.put(scaffold)
                        }
                    }
                    if (strip == stripCount - 1) {
                        heightMapIndexData.put(vertexCount - 1)
                    }
                }
                heightMapIndexData.flip()
                heightMapIndexData
            } else {
                val heightMapIndexData = BufferUtils.createIntBuffer(indexCount)
                var a: Int
                var b = -1
                var c = -1
                var windCc = false
                fun push(value: Int) {
                    windCc = !windCc
                    a = b
                    b = c
                    c = value
                    if (a > -1 && a != b && b != c && c != a) {
                        if (windCc) {
                            heightMapIndexData.put(a)
                            heightMapIndexData.put(b)
                            heightMapIndexData.put(c)
                        } else {
                            heightMapIndexData.put(a)
                            heightMapIndexData.put(c)
                            heightMapIndexData.put(b)
                        }
                    }
                }
                for (strip in 0 until stripCount) {
                    if (strip == 0) {
                        push(0)
                    }
                    if (strip % 2 == 0) {
                        var topStart = (strip * xResolution) + 1
                        var bottomStart = topStart + xResolution
                        for (i in 0 until xResolution) {
                            push(bottomStart++)
                            push(topStart++)
                        }
                        if (strip != stripCount - 1) {
                            push(bottomStart + xResolution - 1)
                        }
                    } else {
                        val topStart = (strip * xResolution) + 1
                        val bottomStart = topStart + xResolution
                        val scaffold = bottomStart + xResolution
                        var bottomEnd = scaffold - 1
                        var topEnd = bottomStart - 1
                        for (i in 0 until xResolution) {
                            push(bottomEnd--)
                            push(topEnd--)
                        }
                        if (strip != stripCount - 1) {
                            push(scaffold)
                        }
                    }
                    if (strip == stripCount - 1) {
                        push(vertexCount - 1)
                    }
                }
                heightMapIndexData.flip()
                heightMapIndexData
            }

            vao = GL30.glGenVertexArrays()

            if (vao > 0) {

                val stride = floatsPerVertex * 4
                GL30.glBindVertexArray(vao)

                val vbo = GL15.glGenBuffers()
                val ibo = GL15.glGenBuffers()

                if (vbo > 0 && ibo > 0) {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)

                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, heightMapVertexData, GL15.GL_STATIC_DRAW)


                    if (positionAttribute.location >= 0) {
                        GL20.glEnableVertexAttribArray(positionAttribute.location)
                        GL20.glVertexAttribPointer(positionAttribute.location, 2, GL11.GL_FLOAT, false, stride, 0)
                    }

                    if (uvAttribute.location >= 0) {
                        GL20.glEnableVertexAttribArray(uvAttribute.location)
                        GL20.glVertexAttribPointer(uvAttribute.location, 2, GL11.GL_FLOAT, false, stride, 8)
                    }

                    GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo)

                    GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, heightMapIndexData, GL15.GL_STATIC_DRAW)

                } else {
                    throw RuntimeException("error setting up buffers")
                }

                GL30.glBindVertexArray(0)

                GL15.glDeleteBuffers(vbo)
                GL15.glDeleteBuffers(ibo)
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
            GL30.glBindVertexArray(vao)
            if (useStrips) {
                GL11.glDrawElements(GL11.GL_TRIANGLE_STRIP, indexCount, GL11.GL_UNSIGNED_INT, 0)
            } else {
                GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0)
            }
            GL30.glBindVertexArray(0)
        }
    }

    @Suppress("unused")
    fun finalize() {
        GL30.glDeleteVertexArrays(vao)
    }
}