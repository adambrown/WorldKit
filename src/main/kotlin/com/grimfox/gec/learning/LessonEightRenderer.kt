package com.grimfox.gec.learning

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.opengl.*
import com.grimfox.gec.ui.*
import org.lwjgl.BufferUtils
import org.lwjgl.nuklear.NkColor
import org.lwjgl.nuklear.Nuklear.nk_rgb
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryStack
import java.awt.image.DataBufferUShort
import java.io.File
import java.lang.Math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.imageio.ImageIO

/**
 * This class implements our custom renderer. Note that the GL10 parameter
 * passed in is unused for OpenGL ES 2.0 renderers -- the static class GLES20 is
 * used instead.
 */
class LessonEightRenderer(val heightMapScaleFactor: FloatBuffer) {

    companion object {

        private val POSITION_DATA_SIZE_IN_ELEMENTS = 3
        private val NORMAL_DATA_SIZE_IN_ELEMENTS = 3

        private val BYTES_PER_FLOAT = 4

        private val STRIDE = (POSITION_DATA_SIZE_IN_ELEMENTS + NORMAL_DATA_SIZE_IN_ELEMENTS) * BYTES_PER_FLOAT

        private val SIZE_PER_SIDE = 128
        private val MIN_POSITION = -50.0f
        private val POSITION_RANGE = 100.0f
    }

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    /** Additional matrices.  */
    private val accumulatedRotation = FloatArray(16)
    private val currentRotation = FloatArray(16)
    private val lightModelMatrix = FloatArray(16)
    private val temporaryMatrix = FloatArray(16)

    /** OpenGL handles to our program uniforms.  */
    private val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
    private val mvMatrixUniform = ShaderUniform("modelViewMatrix")
    private val nMatrixUniform = ShaderUniform("normalMatrix")
    private val lightPosUniform = ShaderUniform("lightPosition")
    private val colorUniform = ShaderUniform("color")
    private val ambientUniform = ShaderUniform("ambientColor")
    private val diffuseUniform = ShaderUniform("diffuseColor")
    private val specularUniform = ShaderUniform("specularColor")
    private val shininessUniform = ShaderUniform("shininess")
    private val heightScaleUniform = ShaderUniform("heightScale")
    private val uvOffsetUniform = ShaderUniform("uvOffset")
    private val uvScaleUniform = ShaderUniform("uvScale")
    private val heightMapTextureUniform = ShaderUniform("heightMapTexture")

    /** OpenGL handles to our program attributes.  */
    private val positionAttribute = ShaderAttribute("position", 0)
    private val normalAttribute = ShaderAttribute("normal", 1)
    private val uvAttribute = ShaderAttribute("uv", 1)

    private var textureId = -1

    private val background = nk_rgb(30, 30, 30, NkColor.create())
    /**
     * Used to hold a light centered on the origin in model space. We need a 4th
     * coordinate so we can get translations to work when we multiply this by
     * our transformation matrices.
     */
    private val lightPosInModelSpace = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)

    /**
     * Used to hold the current position of the light in world space (after
     * transformation via model matrix).
     */
    private val lightPosInWorldSpace = FloatArray(4)

    /**
     * Used to hold the transformed position of the light in eye space (after
     * transformation via modelview matrix)
     */
    private val lightPosInEyeSpace = FloatArray(4)

    /** This is a handle to our cube shading program.  */
    private var program: Int = 0

    /** Retain the most recent delta for touch events.  */
    // These still work without volatile, but refreshes are not guaranteed to
    // happen.
    @Volatile var deltaX: Float = 0.toFloat()
    @Volatile var deltaY: Float = 0.toFloat()
    var lastMouseX = 0.0f
    var lastMouseY = 0.0f

    /** The current heightmap object.  */
    private var heightMap: HexGrid? = null

    fun onSurfaceCreated() {

        // Enable depth testing
        glEnable(GL_DEPTH_TEST)

        // Position the eye in front of the origin.
        val eyeX = 0.0f
        val eyeY = 0.0f
        val eyeZ = -0.5f

        // We are looking toward the distance
        val lookX = 0.0f
        val lookY = 0.0f
        val lookZ = -5.0f

        // Set our up vector. This is where our head would be pointing were we
        // holding the camera.
        val upX = 0.0f
        val upY = 1.0f
        val upZ = 0.0f

        // Set the view matrix. This matrix can be said to represent the camera
        // position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination
        // of a model and view matrix. In OpenGL 2, we can keep track of these
        // matrices separately if we choose.
        setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ)

//        val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/per-pixel-mixed.vert.glsl"))
//        val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/per-pixel-mixed.frag.glsl"))
        val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/test.vert"))
        val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/test.frag"))

        program = createAndLinkProgram(
                listOf(vertexShader, fragmentShader),
                listOf(positionAttribute, uvAttribute),
                listOf(mvpMatrixUniform, mvMatrixUniform, nMatrixUniform, lightPosUniform, colorUniform, ambientUniform, diffuseUniform, specularUniform, shininessUniform, heightScaleUniform, uvOffsetUniform, uvScaleUniform, heightMapTextureUniform))

        heightMap = HexGrid(150.0f, 256)

        twr(MemoryStack.stackPush()) { stack ->
            val bufferedImage = ImageIO.read(File(getPathForResource("/textures/height-map.png")))
            val dataBuffer = (bufferedImage.raster.dataBuffer as DataBufferUShort).data
            val textureData = ByteBuffer.allocateDirect(dataBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            textureData.put(dataBuffer)
            textureData.flip()
            textureId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, textureId)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 2)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R16, bufferedImage.width, bufferedImage.height, 0, GL_RED, GL_UNSIGNED_SHORT, textureData)
            glGenerateMipmap(GL_TEXTURE_2D)
        }

        // Initialize the accumulated rotation matrix
        setIdentityM(accumulatedRotation, 0)
    }

    fun onDrawFrame(width: Int, height: Int, mouseX: Int, mouseY: Int) {

        if (width <= 0 || height <= 0) {
            return
        }

        deltaX = mouseX - lastMouseX
        deltaY = mouseY - lastMouseY

        lastMouseX = mouseX.toFloat()
        lastMouseY = mouseY.toFloat()

        // setup global state
        glDisable(GL_BLEND)
        glDisable(GL_CULL_FACE)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_SCISSOR_TEST)

        glClearColor(background.rFloat, background.gFloat, background.bFloat, background.aFloat)
        glScissor(60, 60, width - 120, height - 120)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        glViewport(60, 60, width - 120, height - 120)

        // Create a new perspective projection matrix. The height will stay the
        // same while the width will vary as per aspect ratio.
        val ratio = width.toFloat() / height
        val left = -ratio
        val right = ratio
        val bottom = -1.0f
        val top = 1.0f
        val near = 1.0f
        val far = 1000.0f

        frustumM(projectionMatrix, 0, left, right, bottom, top, near, far)

        // Set our per-vertex lighting program.
        glUseProgram(program)


        // Calculate position of the light. Push into the distance.
        setIdentityM(lightModelMatrix, 0)
        translateM(lightModelMatrix, 0, 0.0f, 7.5f, -8.0f)

        multiplyMV(lightPosInWorldSpace, 0, lightModelMatrix, 0, lightPosInModelSpace, 0)
        multiplyMV(lightPosInEyeSpace, 0, viewMatrix, 0, lightPosInWorldSpace, 0)

        // Draw the heightmap.
        // Translate the heightmap into the screen.
        setIdentityM(modelMatrix, 0)
        translateM(modelMatrix, 0, 0.0f, 0.0f, -102.0f)

        // Set a matrix that contains the current rotation.
        setIdentityM(currentRotation, 0)
        rotateM(currentRotation, 0, deltaX, 0.0f, 1.0f, 0.0f)
        rotateM(currentRotation, 0, deltaY, 1.0f, 0.0f, 0.0f)
        deltaX = 0.0f
        deltaY = 0.0f

        // Multiply the current rotation by the accumulated rotation, and then
        // set the accumulated rotation to the result.
        multiplyMM(temporaryMatrix, 0, currentRotation, 0, accumulatedRotation, 0)
        System.arraycopy(temporaryMatrix, 0, accumulatedRotation, 0, 16)

        // Rotate the cube taking the overall rotation into account.
        multiplyMM(temporaryMatrix, 0, modelMatrix, 0, accumulatedRotation, 0)
        System.arraycopy(temporaryMatrix, 0, modelMatrix, 0, 16)

        // This multiplies the view matrix by the model matrix, and stores
        // the result in the MVP matrix
        // (which currently contains model * view).
        multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)

        // Pass in the modelview matrix.
        glUniformMatrix4fv(mvMatrixUniform.location, false, mvpMatrix)


        invertM(normalMatrix, 0, mvpMatrix, 0)

        glUniformMatrix4fv(nMatrixUniform.location, true, normalMatrix)

        // This multiplies the modelview matrix by the projection matrix,
        // and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        multiplyMM(temporaryMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        System.arraycopy(temporaryMatrix, 0, mvpMatrix, 0, 16)

        // Pass in the combined matrix.
        glUniformMatrix4fv(mvpMatrixUniform.location, false, mvpMatrix)

        // Pass in the light position in eye space.

        glUniform4f(lightPosUniform.location, lightPosInEyeSpace[0], lightPosInEyeSpace[1], lightPosInEyeSpace[2], lightPosInEyeSpace[3])

        glUniform4f(colorUniform.location, 0.4f, 0.6f, 0.39f, 1.0f)

        glUniform4f(ambientUniform.location, 0.1f, 0.1f, 0.1f, 1.0f)

        glUniform4f(diffuseUniform.location, 0.6f, 0.6f, 0.6f, 1.0f)

        glUniform4f(specularUniform.location, 0.85f, 0.85f, 0.85f, 1.0f)

        glUniform1f(shininessUniform.location, 10.0f)

        glUniform1f(heightScaleUniform.location, heightMapScaleFactor[0])

        glUniform1f(uvOffsetUniform.location, heightMap!!.uIncrement)

        glUniform1f(uvScaleUniform.location, 150.0f / heightMap!!.uIncrement)

        glUniform1i(heightMapTextureUniform.location, 0)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textureId)

        // Render the heightmap.
        heightMap!!.render()
        glScissor(0, 0, width, height)
        glDisable(GL_SCISSOR_TEST)
    }

    internal inner class HexGrid(width: Float, xResolution: Int) {

        val halfXIncrement = width / (xResolution * 2 - 1)
        val xIncrement = halfXIncrement * 2
        val yResolution = Math.round(width / (sqrt(3.0) * halfXIncrement)).toInt() + 1
        val yIncrement = width / (yResolution - 1)
        val halfUIncrement = 1.0f / (xResolution * 2 - 1)
        val uIncrement = halfUIncrement * 2
        val vIncrement = 1.0f / (yResolution - 1)
        val minXY = width / -2.0f

        var vao = 0
        var indexCount = 0

        init {
            try {
                val floatsPerVertex = 4
                val vertexCount = xResolution * yResolution + 2
                val heightMapVertexData = BufferUtils.createFloatBuffer(vertexCount * floatsPerVertex)
                for (y in 0..yResolution - 1) {
                    if (y == 0) {
                        heightMapVertexData.put(minXY).put(minXY).put(0.0f).put(0.0f)
                    }
                    val yOffset = minXY + y * yIncrement
                    val vOffset = y * vIncrement
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
                        heightMapVertexData.put(minXY + width).put(1.0f).put(1.0f)
                    }
                }
                heightMapVertexData.flip()
                val stripCount = yResolution - 1
                val scaffoldVerts = (stripCount - 1) + 2
                val vertsPerStrip = xResolution * 2
                indexCount = stripCount * vertsPerStrip + scaffoldVerts

                val heightMapIndexData = BufferUtils.createIntBuffer(stripCount * vertsPerStrip + scaffoldVerts)
//                val heightMapTest = IntArray(stripCount * vertsPerStrip + scaffoldVerts)
//                var cursor = 0
                for (strip in 0..stripCount - 1) {
                    if (strip == 0) {
//                        heightMapTest[cursor++] = 0
                        heightMapIndexData.put(0)
                    }
                    if (strip % 2 == 0) {
                        var topStart = (strip * xResolution) + 1
                        var bottomStart = topStart + xResolution
                        for (i in 0..xResolution - 1) {
//                            heightMapTest[cursor++] = bottomStart
//                            heightMapTest[cursor++] = topStart
                            heightMapIndexData.put(bottomStart++)
                            heightMapIndexData.put(topStart++)
                        }
                        if (strip != stripCount - 1) {
//                            heightMapTest[cursor++] = bottomStart + xResolution - 1
                            heightMapIndexData.put(bottomStart + xResolution - 1)
                        }
                    } else {
                        val topStart = (strip * xResolution) + 1
                        val bottomStart = topStart + xResolution
                        val scaffold = bottomStart + xResolution
                        var bottomEnd = scaffold - 1
                        var topEnd = bottomStart - 1
                        for (i in 0..xResolution - 1) {
//                            heightMapTest[cursor++] = bottomEnd
//                            heightMapTest[cursor++] = topEnd
                            heightMapIndexData.put(bottomEnd--)
                            heightMapIndexData.put(topEnd--)
                        }
                        if (strip != stripCount - 1) {
//                            heightMapTest[cursor++] = scaffold
                            heightMapIndexData.put(scaffold)
                        }
                    }
                    if (strip == stripCount - 1) {
//                        heightMapTest[cursor++] = vertexCount - 1
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

    internal inner class HeightMap {

        var vao: Int = 0

        var indexCount: Int = 0

        init {
            try {
                val floatsPerVertex = POSITION_DATA_SIZE_IN_ELEMENTS + NORMAL_DATA_SIZE_IN_ELEMENTS
                val xLength = SIZE_PER_SIDE
                val yLength = SIZE_PER_SIDE

                val heightMapVertexData = BufferUtils.createFloatBuffer(xLength * yLength * floatsPerVertex)
                // First, build the data for the vertex buffer
                for (y in 0..yLength - 1) {
                    for (x in 0..xLength - 1) {
                        val xRatio = x / (xLength - 1).toFloat()

                        // Build our heightmap from the top down, so that our triangles are counter-clockwise.
                        val yRatio = 1f - y / (yLength - 1).toFloat()

                        val xPosition = MIN_POSITION + xRatio * POSITION_RANGE
                        val yPosition = MIN_POSITION + yRatio * POSITION_RANGE

                        // Position
                        heightMapVertexData.put(xPosition)
                        heightMapVertexData.put(yPosition)
                        heightMapVertexData.put((xPosition * xPosition + yPosition * yPosition) / POSITION_RANGE)

                        // Cheap normal using a derivative of the function.
                        // The slope for X will be 2X, for Y will be 2Y.
                        // Divide by 10 since the position's Z is also divided by 10.
                        val xSlope = 2 * xPosition / POSITION_RANGE
                        val ySlope = 2 * yPosition / POSITION_RANGE

                        // Calculate the normal using the cross product of the slopes.
                        val planeVectorX = floatArrayOf(1f, 0f, xSlope)
                        val planeVectorY = floatArrayOf(0f, 1f, ySlope)
                        val normalVector = floatArrayOf(planeVectorX[1] * planeVectorY[2] - planeVectorX[2] * planeVectorY[1], planeVectorX[2] * planeVectorY[0] - planeVectorX[0] * planeVectorY[2], planeVectorX[0] * planeVectorY[1] - planeVectorX[1] * planeVectorY[0])

                        // Normalize the normal
                        val length = length(normalVector[0], normalVector[1], normalVector[2])

                        heightMapVertexData.put(normalVector[0] / length)
                        heightMapVertexData.put(normalVector[1] / length)
                        heightMapVertexData.put(normalVector[2] / length)
                    }
                }
                heightMapVertexData.flip()

                // Now build the index data
                val numStripsRequired = yLength - 1
                val numDegeneratesRequired = 2 * (numStripsRequired - 1)
                val verticesPerStrip = 2 * xLength

                val heightMapIndexData = BufferUtils.createIntBuffer(verticesPerStrip * numStripsRequired + numDegeneratesRequired)

                for (y in 0..yLength - 1 - 1) {
                    if (y > 0) {
                        // Degenerate begin: repeat first vertex
                        heightMapIndexData.put((y * yLength))
                    }

                    for (x in 0..xLength - 1) {
                        // One part of the strip
                        heightMapIndexData.put((y * yLength + x))
                        heightMapIndexData.put(((y + 1) * yLength + x))
                    }

                    if (y < yLength - 2) {
                        // Degenerate end: repeat last vertex
                        heightMapIndexData.put(((y + 1) * yLength + (xLength - 1)))
                    }
                }
                heightMapIndexData.flip()

                indexCount = heightMapIndexData.limit()

                vao = glGenVertexArrays()

                if (vao > 0) {

                    glBindVertexArray(vao)
                    val vbo = glGenBuffers()
                    val ibo = glGenBuffers()

                    if (vbo > 0 && ibo > 0) {
                        glBindBuffer(GL_ARRAY_BUFFER, vbo)

                        glBufferData(GL_ARRAY_BUFFER, heightMapVertexData, GL_STATIC_DRAW)

                        glEnableVertexAttribArray(positionAttribute.location)
                        glVertexAttribPointer(positionAttribute.location, POSITION_DATA_SIZE_IN_ELEMENTS, GL_FLOAT, false, STRIDE, 0)

                        glEnableVertexAttribArray(normalAttribute.location)
                        glVertexAttribPointer(normalAttribute.location, NORMAL_DATA_SIZE_IN_ELEMENTS, GL_FLOAT, false, STRIDE, (POSITION_DATA_SIZE_IN_ELEMENTS * BYTES_PER_FLOAT).toLong())

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
