package com.grimfox.gec.ui.widgets

import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.opengl.*
import com.grimfox.gec.ui.*
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.Reference
import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import java.awt.image.BufferedImage
import java.lang.Math.*

class MeshViewport3D(
        val resetView: MutableReference<Boolean>,
        val rotateAroundCamera: Reference<Boolean>,
        val perspectiveOn: Reference<Boolean>,
        val waterPlaneOn: Reference<Boolean>,
        val heightMapScaleFactor: Reference<Float>,
        val imagePlaneOn: Reference<Boolean>) {

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

    private val mvpMatrixUniformImage = ShaderUniform("modelViewProjectionMatrix")
//    private val imageTextureUniform = ShaderUniform("imageTexture")
    private val graphTextureUniform = ShaderUniform("graphTexture")
    private val maskTextureUniform = ShaderUniform("maskTexture")

    private val positionAttribute = ShaderAttribute("position")
    private val uvAttribute = ShaderAttribute("uv")

    private val positionAttributeWater = ShaderAttribute("position")
    private val uvAttributeWater = ShaderAttribute("uv")

    private val positionAttributeImage = ShaderAttribute("position")
    private val uvAttributeImage = ShaderAttribute("uv")

    private var hasTexture = false
    private var textureId = -1
    private var textureResolution = 0
    private var textureToLoad: BufferedImage? = null
    private var textureIdToDelete: Int? = null
    private var textureMagFilter: Int = GL_LINEAR

    private var hasGraph = false
    private var graphTextureId = -1
    private var maskTextureId = -1
    private var graphTextureIdToDelete: Int? = null
    private var maskTextureIdToDelete: Int? = null
    private var graphToLoad: Graph? = null
    private var maskToLoad: Matrix<Byte>? = null

    private val background = NVGColor.create().set(30, 30, 30)

    private val lightDirection = Vector3f(1.0f, 1.0f, 2.0f)

    private var heightMapProgram: Int = 0
    private var waterPlaneProgram: Int = 0
    private var imagePlaneProgram: Int = 0

    private var lastScroll = 0.0f
    private var scroll = 0.0f
    private var deltaScroll = 0.0f

    private var deltaX: Float = 0.0f
    private var deltaY: Float = 0.0f

    private var lastMouseX = 0.0f
    private var lastMouseY = 0.0f

    private var mouseSpeed = 0.0035f

    private var mouseX = 0
    private var mouseY = 0

    private var hotZoneX1 = 0
    private var hotZoneX2 = 0
    private var hotZoneY1 = 0
    private var hotZoneY2 = 0

    private var isRollOn = false
    private var isRotateOn = false
    private var isTranslateOn = false

    private lateinit var heightMap: HexGrid

    private lateinit var waterPlane: HexGrid

    private lateinit var imagePlane: ImagePlane

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

        val imageVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/image-plane.vert"))
        val imageFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/image-plane.frag"))

//        val (texId, texWidth) = loadTexture2D(GL_NEAREST, GL_LINEAR, "/textures/height-map.png", false, true)
//        textureId = texId
//        textureResolution = texWidth

        heightMapProgram = createAndLinkProgram(
                listOf(heightMapVertexShader, heightMapFragmentShader),
                listOf(positionAttribute, uvAttribute),
                listOf(mvpMatrixUniform, mvMatrixUniform, nMatrixUniform, lightDirectionUniform, color1Uniform, color2Uniform, color3Uniform, color4Uniform, color5Uniform, color6Uniform, ambientUniform, diffuseUniform, specularUniform, shininessUniform, heightScaleUniform, uvScaleUniform, heightMapTextureUniform))

        waterPlaneProgram = createAndLinkProgram(
                listOf(waterVertexShader, waterFragmentShader),
                listOf(positionAttributeWater, uvAttributeWater),
                listOf(mvpMatrixUniformWater, mvMatrixUniformWater, nMatrixUniformWater, lightDirectionUniformWater, colorUniformWater, ambientUniformWater, diffuseUniformWater, specularUniformWater, shininessUniformWater, heightScaleUniformWater))

        imagePlaneProgram = createAndLinkProgram(
                listOf(imageVertexShader, imageFragmentShader),
                listOf(positionAttributeImage, uvAttributeImage),
                listOf(mvpMatrixUniformImage, graphTextureUniform, maskTextureUniform))

        heightMap = HexGrid(2560.0f, 1024, positionAttribute, uvAttribute, true)

        waterPlane = HexGrid(2600.0f, 16, positionAttributeWater, uvAttributeWater, true)

        imagePlane = ImagePlane(2600.0f, positionAttributeImage, uvAttributeImage)

        lightDirection.normalize()

        modelMatrix.translate(translation)
    }

    fun setTexture(bufferedImage: BufferedImage, magFilter: Int = GL_LINEAR) {
        synchronized(hasTexture) {
            if (hasTexture) {
                hasTexture = false
                textureIdToDelete = textureId
                textureId = -1
                textureResolution = 0
            }
            textureToLoad = bufferedImage
            textureMagFilter = magFilter
        }
    }

    fun setGraph(pair: Pair<Graph, Matrix<Byte>>) {
        setGraph(pair.first, pair.second)
    }

    fun setGraph(graph: Graph, mask: Matrix<Byte>) {
        synchronized(hasTexture) {
            if (hasGraph) {
                hasGraph = false
                graphTextureIdToDelete = graphTextureId
                maskTextureIdToDelete = maskTextureId
                graphTextureId = -1
                maskTextureId = -1
            }
            graphToLoad = graph
            maskToLoad = mask
        }
    }

    fun onMouseDown(button: Int, x: Int, y: Int) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (x <= hotZoneX1 || x > hotZoneX2 || y <= hotZoneY1 || y > hotZoneY2) {
                if (!isRollOn && !isRotateOn && !isTranslateOn) {
                    lastMouseX = x.toFloat()
                    lastMouseY = y.toFloat()
                    isRollOn = true
                }
            } else {
                if (!isRollOn && !isRotateOn && !isTranslateOn) {
                    lastMouseX = x.toFloat()
                    lastMouseY = y.toFloat()
                    isRotateOn = true
                    modelMatrix.getTranslation(pivot)
                }
            }
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            if (!isRollOn && !isRotateOn && !isTranslateOn) {
                lastMouseX = x.toFloat()
                lastMouseY = y.toFloat()
                isTranslateOn = true
            }
        }
    }

    fun onMouseDrag(x: Int, y: Int) {
        mouseX = x
        mouseY = y
    }

    fun onMouseRelease(button: Int) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            isRollOn = false
            isRotateOn = false
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            isTranslateOn = false
        }
    }

    fun onScroll(scrollDelta: Double) {
        lastScroll = scroll
        deltaScroll = scrollDelta.toFloat()
        scroll += deltaScroll
        if (isTranslateOn) {
            zoom -= deltaScroll * (zoomIncrement * zoom)
            zoom = Math.max(minZoom, Math.min(maxZoom, zoom))
        } else {
            val scaledScroll = deltaScroll * 76.0f
            translation.z += scaledScroll
            tempMatrix.translation(0.0f, 0.0f, scaledScroll)
            tempMatrix.mul(modelMatrix, modelMatrix)
        }
    }

    fun onDrawFrame(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        if (imagePlaneOn.value) {
            onDrawFrameInternalImage(xPosition, yPosition, width, height, rootHeight, scale)
        } else {
            onDrawFrameInternalHeightmap(xPosition, yPosition, width, height, rootHeight, scale)
        }
    }

    private fun onDrawFrameInternalImage(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        if (width < 1 || height < 1) {
            return
        }
        synchronized(hasTexture) {
            handleNewHeightMapLoad()
            handleNewGraphLoad()
            if (hasGraph) {
                val adjustedWidth = round(width / scale)
                val adjustedHeight = round(height / scale)
                val adjustedX = round(xPosition / scale)
                val adjustedY = round(yPosition / scale)

                val marginWidth = Math.min(220, ((adjustedWidth * 0.33333333f) + 0.5f).toInt() / 2)
                val hotZoneWidth = adjustedWidth - (2 * marginWidth)
                hotZoneX1 = adjustedX + marginWidth
                hotZoneX2 = adjustedX + marginWidth + hotZoneWidth
                val marginHeight = Math.min(220, ((adjustedHeight * 0.33333333f) + 0.5f).toInt() / 2)
                val hotZoneHeight = adjustedHeight - (2 * marginHeight)
                hotZoneY1 = adjustedY + marginHeight
                hotZoneY2 = adjustedY + marginHeight + hotZoneHeight

                val flippedY = rootHeight - (yPosition + height)

                val premulRatio = width / height.toFloat()
                val orthoZoom = zoom * perspectiveToOrtho
                projectionMatrix.setOrtho(premulRatio * -orthoZoom, premulRatio * orthoZoom, -orthoZoom, orthoZoom, 6.0f, 6000.0f)

                translation.set(defaultTranslation)
                rotation.set(defaultRotation)
                zoom = defaultZoom
                modelMatrix.translation(translation).rotate(rotation)

                deltaX = mouseX - lastMouseX
                deltaY = mouseY - lastMouseY

                lastMouseX = mouseX.toFloat()
                lastMouseY = mouseY.toFloat()

                deltaRotation.identity()

                viewMatrix.mul(modelMatrix, mvMatrix)
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

                drawImagePlane()

                glDisable(GL_SCISSOR_TEST)
            }
        }
    }

    private fun onDrawFrameInternalHeightmap(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {

        if (width < 1 || height < 1) {
            return
        }
        synchronized(hasTexture) {
            handleNewHeightMapLoad()
            handleNewGraphLoad()
            if (hasTexture) {
                val adjustedWidth = round(width / scale)
                val adjustedHeight = round(height / scale)
                val adjustedX = round(xPosition / scale)
                val adjustedY = round(yPosition / scale)

                val marginWidth = Math.min(220, ((adjustedWidth * 0.33333333f) + 0.5f).toInt() / 2)
                val hotZoneWidth = adjustedWidth - (2 * marginWidth)
                hotZoneX1 = adjustedX + marginWidth
                hotZoneX2 = adjustedX + marginWidth + hotZoneWidth
                val marginHeight = Math.min(220, ((adjustedHeight * 0.33333333f) + 0.5f).toInt() / 2)
                val hotZoneHeight = adjustedHeight - (2 * marginHeight)
                hotZoneY1 = adjustedY + marginHeight
                hotZoneY2 = adjustedY + marginHeight + hotZoneHeight

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

                val mouseDistanceMultiplier = (heightMap.width / (height))

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
                    if (mouseX - adjustedX <= adjustedWidth / 2) {
                        deltaRoll += deltaY
                    } else {
                        deltaRoll -= deltaY
                    }
                    if (mouseY - adjustedY <= adjustedHeight / 2) {
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
        }
    }

    private fun handleNewHeightMapLoad() {
        val texToLoad = textureToLoad
        if (texToLoad != null) {
            val texToDelete = textureIdToDelete
            if (texToDelete != null) {
                glDeleteTextures(texToDelete)
                textureIdToDelete = null
            }
            val (texId, texWidth) = loadTexture2D(GL_NEAREST, textureMagFilter, texToLoad, false, true)
            textureId = texId
            textureResolution = texWidth
            hasTexture = true
            textureToLoad = null
        }
    }

    private fun handleNewGraphLoad() {
        val graphToLoadInternal = graphToLoad
        val maskToLoadInternal = maskToLoad
        if (graphToLoadInternal != null && maskToLoadInternal != null) {
            var texToDelete = graphTextureIdToDelete
            if (texToDelete != null) {
                glDeleteTextures(texToDelete)
                graphTextureIdToDelete = null
            }
            texToDelete = maskTextureIdToDelete
            if (texToDelete != null) {
                glDeleteTextures(texToDelete)
            }
            val (graphTexId) = loadGraphPointsAsTexture(graphToLoadInternal)
            graphTextureId = graphTexId
            val (maskTexId) = loadRegionMaskAsTexture(maskToLoadInternal)
            maskTextureId = maskTexId
            hasGraph = true
            graphToLoad = null
        }
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
        glUniform4f(colorUniformWater.location, 0.1f, 0.2f, 0.5f, 1.0f)
        glUniform4f(ambientUniformWater.location, 0.6f, 0.6f, 0.6f, 1.0f)
        glUniform4f(diffuseUniformWater.location, 0.6f, 0.6f, 0.6f, 1.0f)
        glUniform4f(specularUniformWater.location, 0.95f, 0.95f, 0.95f, 1.0f)
        glUniform1f(shininessUniformWater.location, 5.0f)
        glUniform1f(heightScaleUniformWater.location, heightMapScaleFactor.value)
        waterPlane.render()
    }

    private fun drawImagePlane() {
        glUseProgram(imagePlaneProgram)
        glUniformMatrix4fv(mvpMatrixUniformImage.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform1i(graphTextureUniform.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, graphTextureId)
        glUniform1i(maskTextureUniform.location, 1)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, maskTextureId)
        imagePlane.render()
    }

    internal class ImagePlane(val width: Float, positionAttribute: ShaderAttribute, uvAttribute: ShaderAttribute) {

        val minXY = width / -2.0f
        val maxXY = minXY + width

        var vao = 0

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
                LOG.error(t.message, t)
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

        fun finalize() {
            glDeleteVertexArrays(vao)
        }
    }

    internal class HexGrid(val width: Float, xResolution: Int, positionAttribute: ShaderAttribute, uvAttribute: ShaderAttribute, val useStrips: Boolean) {

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
                indexCount = if (useStrips) { stripCount * vertsPerStrip + scaffoldVerts } else { (2 * (xResolution - 1) * (yResolution - 1) + yResolution) * 3 }

                val heightMapIndexData = if (useStrips) {
                    val heightMapIndexData = BufferUtils.createIntBuffer(indexCount)
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
                    for (strip in 0..stripCount - 1) {
                        if (strip == 0) {
                            push(0)
                        }
                        if (strip % 2 == 0) {
                            var topStart = (strip * xResolution) + 1
                            var bottomStart = topStart + xResolution
                            for (i in 0..xResolution - 1) {
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
                            for (i in 0..xResolution - 1) {
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

                vao = glGenVertexArrays()

                if (vao > 0) {

                    val stride = floatsPerVertex * 4
                    glBindVertexArray(vao)

                    val vbo = glGenBuffers()
                    val ibo = glGenBuffers()

                    if (vbo > 0 && ibo > 0) {
                        glBindBuffer(GL_ARRAY_BUFFER, vbo)

                        glBufferData(GL_ARRAY_BUFFER, heightMapVertexData, GL_STATIC_DRAW)


                        if (positionAttribute.location >= 0) {
                            glEnableVertexAttribArray(positionAttribute.location)
                            glVertexAttribPointer(positionAttribute.location, 2, GL_FLOAT, false, stride, 0)
                        }

                        if (uvAttribute.location >= 0) {
                            glEnableVertexAttribArray(uvAttribute.location)
                            glVertexAttribPointer(uvAttribute.location, 2, GL_FLOAT, false, stride, 8)
                        }

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
                LOG.error(t.message, t)
                throw t
            }
        }

        fun render() {
            if (vao > 0) {
                glBindVertexArray(vao)
                if (useStrips) {
                    glDrawElements(GL_TRIANGLE_STRIP, indexCount, GL_UNSIGNED_INT, 0)
                } else {
                    glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0)
                }
                glBindVertexArray(0)
            }
        }

        fun finalize() {
            glDeleteVertexArrays(vao)
        }
    }
}
