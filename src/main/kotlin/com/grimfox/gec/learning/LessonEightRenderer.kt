package com.grimfox.gec.learning

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.opengl.*
import com.grimfox.gec.ui.*
import com.grimfox.gec.util.geometry.min
import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.nuklear.NkColor
import org.lwjgl.nuklear.Nuklear.nk_rgb
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryStack
import java.awt.image.DataBufferUShort
import java.io.File
import java.lang.Math.sqrt
import java.lang.Math.round
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.text.FieldPosition
import javax.imageio.ImageIO

/**
 * This class implements our custom renderer. Note that the GL10 parameter
 * passed in is unused for OpenGL ES 2.0 renderers -- the static class GLES20 is
 * used instead.
 */
class LessonEightRenderer(val perspectiveOn: IntBuffer, val heightMapScaleFactor: FloatBuffer) {

    companion object {
        private val BYTES_PER_FLOAT = 4
    }

    private val modelMatrix = Matrix4f()
    private val viewMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()
    private val mvpMatrix = Matrix4f()
    private val normalMatrix = Matrix3f()

    /** Additional matrices.  */
    private val defaultRotation = Quaternionf().rotate(0.0f, 0.0f, 0.0f)
    private val rotation = Quaternionf(defaultRotation)
    private val deltaRotation = Quaternionf()

    private val floatBuffer = BufferUtils.createFloatBuffer(16)

    private val modelScale = 512.0f

    private val minZoom = 0.005f
    private val maxZoom = 100.0f
    private val defaultZoom = 0.6187f
    private val zoomIncrement = 0.1f
    private var zoom = defaultZoom

    private val modelScaleAdjustment = (((-1.0 / modelScale) * 0.0666666) + 1.06604).toFloat()

    private val defaultTranslation = Vector3f(0.0f, 0.0f, -7.6f).mul(modelScale * modelScaleAdjustment)
    private val translation = Vector3f(defaultTranslation)

    private var lastScroll = 0.0f
    private var scroll = 0.0f

    private val perspectiveToOrtho = 8.10102f

    /** OpenGL handles to our program uniforms.  */
    private val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
    private val mvMatrixUniform = ShaderUniform("modelViewMatrix")
    private val nMatrixUniform = ShaderUniform("normalMatrix")
    private val lightDirectionUniform = ShaderUniform("lightDirection")
    private val colorUniform = ShaderUniform("color")
    private val ambientUniform = ShaderUniform("ambientColor")
    private val diffuseUniform = ShaderUniform("diffuseColor")
    private val specularUniform = ShaderUniform("specularColor")
    private val shininessUniform = ShaderUniform("shininess")
    private val heightScaleUniform = ShaderUniform("heightScale")
    private val uvScaleUniform = ShaderUniform("uvScale")
    private val heightMapTextureUniform = ShaderUniform("heightMapTexture")

    /** OpenGL handles to our program attributes.  */
    private val positionAttribute = ShaderAttribute("position", 0)
    private val uvAttribute = ShaderAttribute("uv", 1)

    private var textureId = -1
    private var textureResolution = 0

    private val background = nk_rgb(30, 30, 30, NkColor.create())

    private val lightDirection = Vector3f(1.0f, 1.0f, 2.0f)

    /** This is a handle to our cube shading program.  */
    private var program: Int = 0

    /** Retain the most recent delta for touch events.  */
    // These still work without volatile, but refreshes are not guaranteed to
    // happen.
    @Volatile var deltaX: Float = 0.0f
    @Volatile var deltaY: Float = 0.0f
    var lastMouseX = 0.0f
    var lastMouseY = 0.0f
    var mouseSpeed = 0.0035f
    var lastMouse1Down = false
    var lastMouse2Down = false
    var isRollOn = false
    var isRotateOn = false
    var isTranslateOn = false
    var isResetOn = false

    val axisRotation = AxisAngle4f(0.0f, 0.0f, 0.0f, 0.0f)

    /** The current heightmap object.  */
    private var heightMap: HexGrid? = null

    fun onSurfaceCreated() {

        // Enable depth testing
        glEnable(GL_DEPTH_TEST)

        // Position the eye in front of the origin.
        val eye = Vector3f(0.0f, 0.0f, 0.5f)

        // We are looking toward the distance
        val eyeCenter = Vector3f(0.0f, 0.0f, -5.0f)

        // Set our up vector. This is where our head would be pointing were we
        // holding the camera.
        val eyeUp = Vector3f(0.0f, 1.0f, 0.0f)

        // Set the view matrix. This matrix can be said to represent the camera
        // position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination
        // of a model and view matrix. In OpenGL 2, we can keep track of these
        // matrices separately if we choose.
        viewMatrix.setLookAt(eye, eyeCenter, eyeUp)

        val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/test.vert"))
        val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/test.frag"))

        program = createAndLinkProgram(
                listOf(vertexShader, fragmentShader),
                listOf(positionAttribute, uvAttribute),
                listOf(mvpMatrixUniform, mvMatrixUniform, nMatrixUniform, lightDirectionUniform, colorUniform, ambientUniform, diffuseUniform, specularUniform, shininessUniform, heightScaleUniform, uvScaleUniform, heightMapTextureUniform))

        heightMap = HexGrid(10.0f * modelScale, 2048)

        twr(MemoryStack.stackPush()) { stack ->
            val bufferedImage = ImageIO.read(File(getPathForResource("/textures/height-map.png")))
            textureResolution = bufferedImage.width
            val dataBuffer = (bufferedImage.raster.dataBuffer as DataBufferUShort).data
            val textureData = ByteBuffer.allocateDirect(dataBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            textureData.put(dataBuffer)
            textureData.flip()
            textureId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, textureId)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 2)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R16, bufferedImage.width, bufferedImage.height, 0, GL_RED, GL_UNSIGNED_SHORT, textureData)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        }

        lightDirection.normalize()
    }

    fun onDrawFrame(xPosition: Int, yPosition: Int, width: Int, height: Int, mouseX: Int, mouseY: Int, mouseWheel: Float, isMouse1Down: Boolean, isMouse2Down: Boolean) {

        if (width <= 0 || height <= 0) {
            return
        }

        lastScroll = scroll
        scroll = mouseWheel
        val deltaScroll = scroll - lastScroll

        val isMouseOver = mouseX > xPosition && mouseX < width && mouseY > yPosition && mouseY < height

        val adjustedMouseX = mouseX - xPosition
        val adjustedMouseY = mouseY - yPosition
        val adjustedWidth = width - xPosition
        val adjustedHeight = height - yPosition

        val marginWidth = Math.min(220, ((adjustedWidth * 0.33333333f) + 0.5f).toInt() / 2)
        val hotZoneWidth = adjustedWidth - (2 * marginWidth)
        val marginHeight = Math.min(220, ((adjustedHeight * 0.33333333f) + 0.5f).toInt() / 2)
        val hotZoneHeight = adjustedHeight - (2 * marginHeight)

        val mouseDistanceMultiplier = (heightMap!!.width / (height - yPosition))

        if (isMouseOver) {
            val isMouseOverMargin = isMouseOver && (adjustedMouseX <= marginWidth || adjustedMouseX > marginWidth + hotZoneWidth || adjustedMouseY <= marginHeight || adjustedMouseY > marginHeight + hotZoneHeight)
            if (isMouseOverMargin) {
                if (isMouse1Down) {
                    if (!lastMouse1Down && !isMouse2Down && !isRotateOn && !isTranslateOn) {
                        lastMouse1Down = true
                        isRollOn = true
                    }
                }
            } else {
                if (isMouse1Down) {
                    if (!lastMouse1Down && !isMouse2Down && !isRotateOn && !isTranslateOn) {
                        lastMouse1Down = true
                        isRotateOn = true
                    }
                }
            }
            if (isMouse2Down) {
                if (!lastMouse2Down && !isMouse1Down && !isRotateOn && !isTranslateOn) {
                    lastMouse2Down = true
                    isTranslateOn = true
                }
                zoom -= deltaScroll * (zoomIncrement * zoom)
                zoom = Math.max(minZoom, Math.min(maxZoom, zoom))
            } else {
                translation.z += deltaScroll * 0.3f * modelScale
            }
            if (isMouse1Down && isMouse2Down) {
                if (isRotateOn || isTranslateOn || (!lastMouse1Down && !lastMouse2Down)) {
                    isRotateOn = false
                    isTranslateOn = false
                    isResetOn = true
                }
            }
        }
        if (isMouse1Down) {
            lastMouse1Down = true
        }
        if (isMouse2Down) {
            lastMouse2Down = true
        }
        if (!isMouse1Down) {
            isRollOn = false
            isRotateOn = false
            isResetOn = false
            lastMouse1Down = false
        }
        if (!isMouse2Down) {
            isTranslateOn = false
            isResetOn = false
            lastMouse2Down = false
        }

        if (isResetOn) {
            translation.set(defaultTranslation)
            rotation.set(defaultRotation)
            zoom = defaultZoom
        }

        val perspectiveOn = perspectiveOn[0] > 0
        deltaX = mouseX - lastMouseX
        deltaY = mouseY - lastMouseY

        lastMouseX = mouseX.toFloat()
        lastMouseY = mouseY.toFloat()

        // setup global state
        glDisable(GL_BLEND)
        glDisable(GL_CULL_FACE)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_SCISSOR_TEST)
        glEnable(GL_MULTISAMPLE)

        glClearColor(background.rFloat, background.gFloat, background.bFloat, background.aFloat)
        glScissor(xPosition, 0, width - xPosition, height - yPosition)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        glViewport(xPosition, 0, width - xPosition, height - yPosition)

        // Create a new perspective projection matrix. The height will stay the
        // same while the width will vary as per aspect ratio.
        val premulRatio = ((width - xPosition.toFloat()) / (height - yPosition))
        val ratio = premulRatio * zoom
        if (perspectiveOn) {
            projectionMatrix.setFrustum(-ratio, ratio, -zoom, zoom, 1.0f, 10000.0f)
        } else {
            val orthoZoom = zoom * perspectiveToOrtho * modelScale
            projectionMatrix.setOrtho(premulRatio * -orthoZoom, premulRatio * orthoZoom, -orthoZoom, orthoZoom, 1.0f, 10000.0f)
        }

        glUseProgram(program)
        if (isTranslateOn) {
            translation.x += deltaX * mouseDistanceMultiplier
            translation.y += deltaY * -mouseDistanceMultiplier
        }
        modelMatrix.translation(translation)

        deltaRotation.identity()
        if (isRotateOn) {
            deltaRotation.rotate(deltaY * mouseSpeed, deltaX * mouseSpeed, 0.0f)
        } else if (isRollOn) {
            var deltaRoll = 0.0f
            if (adjustedMouseX <= adjustedWidth / 2) {
                deltaRoll += deltaY
            } else {
                deltaRoll -= deltaY
            }
            if (adjustedMouseY <= adjustedHeight / 2) {
                deltaRoll -= deltaX
            } else {
                deltaRoll += deltaX
            }
            deltaRotation.rotate(0.0f, 0.0f, deltaRoll * mouseSpeed * 0.5f)
        }

        rotation.premul(deltaRotation)

        modelMatrix.rotate(rotation)

        // This multiplies the view matrix by the model matrix, and stores
        // the result in the MVP matrix
        // (which currently contains model * view).
        viewMatrix.mul(modelMatrix, mvpMatrix)

        // Pass in the modelview matrix.
        glUniformMatrix4fv(mvMatrixUniform.location, false, mvpMatrix.get(0, floatBuffer))

        normalMatrix.set(mvpMatrix).invert().transpose()

        glUniformMatrix3fv(nMatrixUniform.location, false, normalMatrix.get(0, floatBuffer))

        // This multiplies the modelview matrix by the projection matrix,
        // and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        projectionMatrix.mul(mvpMatrix, mvpMatrix)

        // Pass in the combined matrix.
        glUniformMatrix4fv(mvpMatrixUniform.location, false, mvpMatrix.get(0, floatBuffer))

        // Pass in the light position in eye space.

        glUniform3f(lightDirectionUniform.location, lightDirection.x, lightDirection.y, lightDirection.z)

        glUniform4f(colorUniform.location, 0.4f, 0.6f, 0.39f, 1.0f)

        glUniform4f(ambientUniform.location, 0.1f, 0.1f, 0.1f, 1.0f)

        glUniform4f(diffuseUniform.location, 0.6f, 0.6f, 0.6f, 1.0f)

        glUniform4f(specularUniform.location, 0.85f, 0.85f, 0.85f, 1.0f)

        glUniform1f(shininessUniform.location, 1.7f)

        glUniform1f(heightScaleUniform.location, heightMapScaleFactor[0] * modelScale)

        glUniform1f(uvScaleUniform.location, heightMap!!.width / textureResolution)

        glUniform1i(heightMapTextureUniform.location, 0)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textureId)

        // Render the heightmap.
        heightMap!!.render()
        glScissor(0, 0, width, height)
        glDisable(GL_SCISSOR_TEST)
    }

    internal inner class HexGrid(val width: Float, xResolution: Int) {

        val halfXIncrement = width / (xResolution * 2 - 1)
        val xIncrement = halfXIncrement * 2
        val yResolution = round(width / (sqrt(3.0) * halfXIncrement)).toInt() + 1
        val yIncrement = width / (yResolution - 1)
        val halfUIncrement = 1.0f / (xResolution * 2 - 1)
        val uIncrement = halfUIncrement * 2
        val vIncrement = 1.0f / (yResolution - 1)
        val minXY = width / -2.0f
        val maxXY = minXY + width

        var vao = 0
        var indexCount = 0

        init {
            try {
                val floatsPerVertex = 4
                val vertexCount = xResolution * yResolution + 2
                val heightMapVertexData = BufferUtils.createFloatBuffer(vertexCount * floatsPerVertex)
                for (y in 0..yResolution - 1) {
                    if (y == 0) {
                        heightMapVertexData.put(minXY).put(maxXY).put(0.0f).put(0.0f)
                    }
                    val yOffset = maxXY - y * yIncrement
                    val vOffset = if (y == yResolution - 1) 1.0f else y * vIncrement
                    val isEven = y % 2 == 0
                    val xOffset = minXY + if (isEven) halfXIncrement else 0.0f
                    val uOffset = if (isEven) halfUIncrement else 0.0f
                    for (x in 0..xResolution - 1) {
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
                indexCount = stripCount * vertsPerStrip + scaffoldVerts

                val heightMapIndexData = BufferUtils.createIntBuffer(stripCount * vertsPerStrip + scaffoldVerts)
                for (strip in 0..stripCount - 1) {
                    if (strip == 0) {
                        heightMapIndexData.put(0)
                    }
                    if (strip % 2 == 0) {
                        var topStart = (strip * xResolution) + 1
                        var bottomStart = topStart + xResolution
                        for (i in 0..xResolution - 1) {
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
                        for (i in 0..xResolution - 1) {
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

                vao = glGenVertexArrays()

                if (vao > 0) {

                    val stride = floatsPerVertex * BYTES_PER_FLOAT
                    glBindVertexArray(vao)
                    val vbo = glGenBuffers()
                    val ibo = glGenBuffers()

                    if (vbo > 0 && ibo > 0) {
                        glBindBuffer(GL_ARRAY_BUFFER, vbo)

                        glBufferData(GL_ARRAY_BUFFER, heightMapVertexData, GL_STATIC_DRAW)

                        glEnableVertexAttribArray(positionAttribute.location)
                        glVertexAttribPointer(positionAttribute.location, 2, GL_FLOAT, false, stride, 0)

                        glEnableVertexAttribArray(uvAttribute.location)
                        glVertexAttribPointer(uvAttribute.location, 2, GL_FLOAT, false, stride, (2 * BYTES_PER_FLOAT).toLong())

                        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)

                        glBufferData(GL_ELEMENT_ARRAY_BUFFER, heightMapIndexData, GL_STATIC_DRAW)

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
                t.printStackTrace()
                throw t
            }
        }

        fun render() {
            if (vao > 0) {
                glBindVertexArray(vao)
                glDrawElements(GL_TRIANGLE_STRIP, indexCount, GL_UNSIGNED_INT, 0)
                glBindVertexArray(0)
            }
        }

        fun finalize() {
            glDeleteVertexArrays(vao)
        }
    }
}
