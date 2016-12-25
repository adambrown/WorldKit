package com.grimfox.gec.ui.widgets

import com.grimfox.gec.opengl.*
import com.grimfox.gec.ui.*
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.Reference
import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import java.lang.Math.round
import java.lang.Math.sqrt

class MeshViewport3D(
        val resetView: MutableReference<Boolean>,
        val rotateAroundCamera: Reference<Boolean>,
        val perspectiveOn: Reference<Boolean>,
        val waterPlaneOn: Reference<Boolean>,
        val heightMapScaleFactor: Reference<Float>) {

    private val modelMatrix = Matrix4f()
    private val viewMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()
    private val mvMatrix = Matrix4f()
    private val mvpMatrix = Matrix4f()
    private val normalMatrix = Matrix3f()
    private val tempMatrix = Matrix4f()

    private val defaultRotation = Quaternionf().rotate(0.0f, 0.0f, 0.0f)
    private val rotation = Quaternionf(defaultRotation)
    private val deltaRotation = Quaternionf()

    private val floatBuffer = BufferUtils.createFloatBuffer(16)

    private val minZoom = 0.02f
    private val maxZoom = 100.0f
    private val defaultZoom = 3.836f

    private val zoomIncrement = 0.05f
    private var zoom = defaultZoom

    private val defaultTranslation = Vector3f(0.0f, 0.0f, -2073.58f)
    private val translation = Vector3f(defaultTranslation)
    private val deltaTranslation = Vector3f()
    private val pivot = Vector3f(0.0f, 0.0f, 0.0f)

    private var lastScroll = 0.0f
    private var scroll = 0.0f

    private val perspectiveToOrtho = 340.0f

    private val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
    private val mvMatrixUniform = ShaderUniform("modelViewMatrix")
    private val nMatrixUniform = ShaderUniform("normalMatrix")
    private val lightDirectionUniform = ShaderUniform("lightDirection")
    private val color1Uniform = ShaderUniform("color1")
    private val color2Uniform = ShaderUniform("color2")
    private val color3Uniform = ShaderUniform("color3")
    private val color4Uniform = ShaderUniform("color4")
    private val color5Uniform = ShaderUniform("color5")
    private val color6Uniform = ShaderUniform("color6")
    private val ambientUniform = ShaderUniform("ambientColor")
    private val diffuseUniform = ShaderUniform("diffuseColor")
    private val specularUniform = ShaderUniform("specularColor")
    private val shininessUniform = ShaderUniform("shininess")
    private val heightScaleUniform = ShaderUniform("heightScale")
    private val uvScaleUniform = ShaderUniform("uvScale")
    private val heightMapTextureUniform = ShaderUniform("heightMapTexture")

    private val mvpMatrixUniformWater = ShaderUniform("modelViewProjectionMatrix")
    private val mvMatrixUniformWater = ShaderUniform("modelViewMatrix")
    private val nMatrixUniformWater = ShaderUniform("normalMatrix")
    private val lightDirectionUniformWater = ShaderUniform("lightDirection")
    private val colorUniformWater = ShaderUniform("color")
    private val ambientUniformWater = ShaderUniform("ambientColor")
    private val diffuseUniformWater = ShaderUniform("diffuseColor")
    private val specularUniformWater = ShaderUniform("specularColor")
    private val shininessUniformWater = ShaderUniform("shininess")
    private val heightScaleUniformWater = ShaderUniform("heightScale")

    private val positionAttribute = ShaderAttribute("position", 0)
    private val uvAttribute = ShaderAttribute("uv", 1)

    private val positionAttributeWater = ShaderAttribute("position", 0)
    private val uvAttributeWater = ShaderAttribute("uv", 1)

    private var textureId = -1
    private var textureResolution = 0

    private val background = NVGColor.create().set(30, 30, 30)

    private val lightDirection = Vector3f(1.0f, 1.0f, 2.0f)

    private var heightMapProgram: Int = 0
    private var waterPlaneProgram: Int = 0

    var deltaX: Float = 0.0f
    var deltaY: Float = 0.0f
    var lastMouseX = 0.0f
    var lastMouseY = 0.0f
    var mouseSpeed = 0.0035f
    var lastMouse1Down = false
    var lastMouse2Down = false
    var isRollOn = false
    var isRotateOn = false
    var isTranslateOn = false
    var isResetOn = false

    private lateinit var heightMap: HexGrid

    private lateinit var waterPlane: HexGrid

    fun init() {

        glEnable(GL_DEPTH_TEST)

        val eye = Vector3f(0.0f, 0.0f, 0.5f)
        val eyeCenter = Vector3f(0.0f, 0.0f, -5.0f)
        val eyeUp = Vector3f(0.0f, 1.0f, 0.0f)

        viewMatrix.setLookAt(eye, eyeCenter, eyeUp)

        val heightMapVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/height-map.vert"))
        val heightMapFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/height-map.frag"))

        val waterVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/water-plane.vert"))
        val waterFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/water-plane.frag"))

        heightMapProgram = createAndLinkProgram(
                listOf(heightMapVertexShader, heightMapFragmentShader),
                listOf(positionAttribute, uvAttribute),
                listOf(mvpMatrixUniform, mvMatrixUniform, nMatrixUniform, lightDirectionUniform, color1Uniform, color2Uniform, color3Uniform, color4Uniform, color5Uniform, color6Uniform, ambientUniform, diffuseUniform, specularUniform, shininessUniform, heightScaleUniform, uvScaleUniform, heightMapTextureUniform))

        waterPlaneProgram = createAndLinkProgram(
                listOf(waterVertexShader, waterFragmentShader),
                listOf(positionAttributeWater, uvAttributeWater),
                listOf(mvpMatrixUniformWater, mvMatrixUniformWater, nMatrixUniformWater, lightDirectionUniformWater, colorUniformWater, ambientUniformWater, diffuseUniformWater, specularUniformWater, shininessUniformWater, heightScaleUniformWater))

        heightMap = HexGrid(2560.0f, 512)

        waterPlane = HexGrid(2600.0f, 16)

        val (texId, texWidth) = loadTexture2D(GL_NEAREST, GL_LINEAR, "/textures/height-map.png", false, true)
        textureId = texId
        textureResolution = texWidth

        lightDirection.normalize()

        modelMatrix.translate(translation)
    }

    var mouseX = 0
    var mouseY = 0
    var mouseWheel = 0.0f
    var isMouse1Down = false
    var isMouse2Down = false

    fun doInput(mouseX: Int, mouseY: Int, mouseWheel: Float, isMouse1Down: Boolean, isMouse2Down: Boolean) {
        this.mouseX = mouseX
        this.mouseY = mouseY
        this.mouseWheel = mouseWheel
        this.isMouse1Down = isMouse1Down
        this.isMouse2Down = isMouse2Down
    }

    fun onDrawFrame(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int) {

        val flippedY = rootHeight - (yPosition + height)

        val waterOn = waterPlaneOn.value
        val doReset = resetView.value
        if (doReset) {
            resetView.value = false
        }
        val perspectiveOn = perspectiveOn.value
        val rotateAroundCamera = rotateAroundCamera.value

        val premulRatio = width / height.toFloat()
        val ratio = premulRatio * zoom
        if (perspectiveOn) {
            projectionMatrix.setFrustum(-ratio, ratio, -zoom, zoom, 6.0f, 6000.0f)
        } else {
            val orthoZoom = zoom * perspectiveToOrtho
            projectionMatrix.setOrtho(premulRatio * -orthoZoom, premulRatio * orthoZoom, -orthoZoom, orthoZoom, 6.0f, 6000.0f)
        }

        lastScroll = scroll
        scroll = mouseWheel
        val deltaScroll = scroll - lastScroll

        val isMouseOver = mouseX > xPosition && mouseX < xPosition + width && mouseY > yPosition && mouseY < yPosition + height

        val adjustedMouseX = mouseX - xPosition
        val adjustedMouseY = mouseY - yPosition

        val marginWidth = Math.min(220, ((width * 0.33333333f) + 0.5f).toInt() / 2)
        val hotZoneWidth = width - (2 * marginWidth)
        val marginHeight = Math.min(220, ((height * 0.33333333f) + 0.5f).toInt() / 2)
        val hotZoneHeight = height - (2 * marginHeight)

        val mouseDistanceMultiplier = (heightMap.width / (height))

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
                        modelMatrix.getTranslation(pivot)
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
                val scaledScroll = deltaScroll * 76.0f
                translation.z += scaledScroll
                tempMatrix.translation(0.0f, 0.0f, scaledScroll)
                tempMatrix.mul(modelMatrix, modelMatrix)
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
            lastMouse1Down = false
        }
        if (!isMouse2Down) {
            isTranslateOn = false
            lastMouse2Down = false
        }

        if (doReset) {
            translation.set(defaultTranslation)
            rotation.set(defaultRotation)
            zoom = defaultZoom
            modelMatrix.translation(translation).rotate(rotation)
        }

        deltaX = mouseX - lastMouseX
        deltaY = mouseY - lastMouseY

        lastMouseX = mouseX.toFloat()
        lastMouseY = mouseY.toFloat()


        if (isTranslateOn) {
            translation.x += deltaX * mouseDistanceMultiplier
            translation.y += deltaY * -mouseDistanceMultiplier
            tempMatrix.translation(deltaX * mouseDistanceMultiplier, deltaY * -mouseDistanceMultiplier, 0.0f)
            tempMatrix.mul(modelMatrix, modelMatrix)
        }

        deltaRotation.identity()
        if (isRotateOn) {
            deltaRotation.rotate(deltaY * mouseSpeed, deltaX * mouseSpeed, 0.0f)
            if (rotateAroundCamera) {
                modelMatrix.rotateAroundLocal(deltaRotation, 0.0f, 0.0f, 0.5f)
            } else {
                modelMatrix.getTranslation(deltaTranslation)
                modelMatrix.rotateAroundLocal(deltaRotation, deltaTranslation.x, deltaTranslation.y, deltaTranslation.z)
            }
        } else if (isRollOn) {
            var deltaRoll = 0.0f
            if (adjustedMouseX <= width / 2) {
                deltaRoll += deltaY
            } else {
                deltaRoll -= deltaY
            }
            if (adjustedMouseY <= height / 2) {
                deltaRoll -= deltaX
            } else {
                deltaRoll += deltaX
            }
            deltaRotation.rotate(0.0f, 0.0f, deltaRoll * mouseSpeed * 0.5f)
            modelMatrix.rotateAroundLocal(deltaRotation, 0.0f, 0.0f, 0.0f)
        }

        viewMatrix.mul(modelMatrix, mvMatrix)
        normalMatrix.set(mvMatrix).invert().transpose()
        projectionMatrix.mul(mvMatrix, mvpMatrix)

        glDisable(GL_BLEND)
        glDisable(GL_CULL_FACE)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_SCISSOR_TEST)
        glEnable(GL_MULTISAMPLE)

        glClearColor(background.r, background.g, background.b, background.a)
        glScissor(xPosition, flippedY, width, height)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        glViewport(xPosition, flippedY, width, height)

        if (waterOn) {
            drawWaterPlane()
        }
        drawHeightMap()

        glDisable(GL_SCISSOR_TEST)
    }

    private fun drawHeightMap() {
        glUseProgram(heightMapProgram)
        glUniformMatrix4fv(mvMatrixUniform.location, false, mvMatrix.get(0, floatBuffer))
        glUniformMatrix3fv(nMatrixUniform.location, false, normalMatrix.get(0, floatBuffer))
        glUniformMatrix4fv(mvpMatrixUniform.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform3f(lightDirectionUniform.location, lightDirection.x, lightDirection.y, lightDirection.z)
        glUniform4f(color1Uniform.location, 0.157f, 0.165f, 0.424f, 1.0f)
        glUniform4f(color2Uniform.location, 0.459f, 0.761f, 0.859f, 1.0f)
        glUniform4f(color3Uniform.location, 0.353f, 0.706f, 0.275f, 1.0f)
        glUniform4f(color4Uniform.location, 0.922f, 0.922f, 0.157f, 1.0f)
        glUniform4f(color5Uniform.location, 0.835f, 0.176f, 0.165f, 1.0f)
        glUniform4f(color6Uniform.location, 0.955f, 0.955f, 0.955f, 1.0f)
        glUniform4f(ambientUniform.location, 0.1f, 0.1f, 0.1f, 1.0f)
        glUniform4f(diffuseUniform.location, 0.6f, 0.6f, 0.6f, 1.0f)
        glUniform4f(specularUniform.location, 0.85f, 0.85f, 0.85f, 1.0f)
        glUniform1f(shininessUniform.location, 1.7f)
        glUniform1f(heightScaleUniform.location, heightMapScaleFactor.value)
        glUniform1f(uvScaleUniform.location, heightMap.width / textureResolution)
        glUniform1i(heightMapTextureUniform.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textureId)
        heightMap.render()
    }

    private fun drawWaterPlane() {
        glUseProgram(waterPlaneProgram)
        glUniformMatrix4fv(mvMatrixUniformWater.location, false, mvMatrix.get(0, floatBuffer))
        glUniformMatrix3fv(nMatrixUniformWater.location, false, normalMatrix.get(0, floatBuffer))
        glUniformMatrix4fv(mvpMatrixUniformWater.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform3f(lightDirectionUniformWater.location, lightDirection.x, lightDirection.y, lightDirection.z)
        glUniform4f(colorUniformWater.location, 0.1f, 0.2f, 0.4f, 1.0f)
        glUniform4f(ambientUniformWater.location, 0.4f, 0.4f, 0.4f, 1.0f)
        glUniform4f(diffuseUniformWater.location, 0.6f, 0.6f, 0.6f, 1.0f)
        glUniform4f(specularUniformWater.location, 0.95f, 0.95f, 0.95f, 1.0f)
        glUniform1f(shininessUniformWater.location, 5.0f)
        glUniform1f(heightScaleUniformWater.location, heightMapScaleFactor.value)
        waterPlane.render()
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

                    val stride = floatsPerVertex * 4
                    glBindVertexArray(vao)
                    val vbo = glGenBuffers()
                    val ibo = glGenBuffers()

                    if (vbo > 0 && ibo > 0) {
                        glBindBuffer(GL_ARRAY_BUFFER, vbo)

                        glBufferData(GL_ARRAY_BUFFER, heightMapVertexData, GL_STATIC_DRAW)

                        glEnableVertexAttribArray(positionAttribute.location)
                        glVertexAttribPointer(positionAttribute.location, 2, GL_FLOAT, false, stride, 0)

                        glEnableVertexAttribArray(uvAttribute.location)
                        glVertexAttribPointer(uvAttribute.location, 2, GL_FLOAT, false, stride, 8)

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
