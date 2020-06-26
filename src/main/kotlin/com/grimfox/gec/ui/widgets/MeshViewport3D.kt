package com.grimfox.gec.ui.widgets

import com.grimfox.gec.*
import com.grimfox.gec.ui.KeyboardHandler
import com.grimfox.gec.ui.nvgproxy.*
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.util.*
import com.grimfox.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*
import java.lang.Math.*
import java.util.*

class MeshViewport3D(
        private val resetView: MutableReference<Boolean>,
        private val perspectiveOn: Reference<Boolean>,
        private val waterPlaneOn: Reference<Boolean>,
        private val heightColorsOn: Reference<Boolean>,
        private val riversOn: Reference<Boolean>,
        private val skyOn: ObservableMutableReference<Boolean>,
        private val fogOn: ObservableMutableReference<Boolean>,
        private val heightMapScaleFactor: Reference<Float>,
        private val heightRangeMeters: Reference<Float>,
        private val colorHeightScaleFactor: Reference<Float>,
        private val lightColor: Array<ObservableMutableReference<Float>>,
        private val lightElevation: ObservableReference<Float>,
        private val lightHeading: ObservableReference<Float>,
        private val indirectIntensity: ObservableReference<Float>,
        private val baseColor: Array<ObservableMutableReference<Float>>,
        private val metallic: ObservableMutableReference<Float>,
        private val roughness: ObservableMutableReference<Float>,
        private val specularIntensity: ObservableMutableReference<Float>,
        private val waterParams: WaterShaderParams,
        private val fogParams: FogShaderParams,
        private val imageMode: Reference<Int>,
        private val disableCursor: MutableReference<Boolean>,
        private val hideCursor: MutableReference<Boolean>,
        private val brushOn: Reference<Boolean>,
        private val brushActive: MutableReference<Boolean>,
        private val brushListener: Reference<BrushListener?>,
        private val brushSize: MutableReference<Float>,
        private val editBrushSizeRef: Reference<Reference<Float>>,
        private val pickerOn: Reference<Boolean>,
        private val pointPicker: Reference<PointPicker?>,
        private val uiRoot: Reference<Block>) {

    private val pressedKeys = Collections.synchronizedSet(LinkedHashSet<Int>())

    private val lightFilter = arrayOf(
            floatArrayOf(0.3564f, 0.0140f, 0.001f, 0.098f, 0.5f, 1.0f),
            floatArrayOf(0.7084f, 0.0262f, 0.003f, 0.95f, 0.5f, 1.0f),
            floatArrayOf(0.9f, 0.1f, 0.008f, 3.3f, 0.5f, 1.0f),
            floatArrayOf(1f, 0.2232f, 0.0144f, 3.5f, 0.5217f, 1.0f),
            floatArrayOf(1f, 0.4851f, 0.0887f, 7.0f, 0.6696f, 0.6f),
            floatArrayOf(1f, 0.7605f, 0.3663f, 18.0f, 0.8783f, 0.2f),
            floatArrayOf(1f, 0.8469f, 0.5395f, 30.0f, 0.9478f, 0.2f),
            floatArrayOf(1f, 0.9131f, 0.6939f, 60.0f, 1.0f, 0.4f),
            floatArrayOf(1f, 0.9131f, 0.6939f, 91.0f, 1.0f, 0.4f)
    )

    private var eliminateMovement = false

    private val modelMatrix = Matrix4f()
    private val viewMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()
    private val mvMatrix = Matrix4f()
    private val mvpMatrix = Matrix4f()
    private val normalMatrix = Matrix3f()

    private val defaultCameraPosition = Vector3f(0.903f, 0.0f, 0.303f).mul(VIEWPORT_MESH_SCALE)
    private val defaultCameraInclination = kotlin.math.atan2(defaultCameraPosition.z, Vector3f(defaultCameraPosition.x, defaultCameraPosition.y, 0.0f).length())
    private val defaultCameraHeading = kotlin.math.atan2(-defaultCameraPosition.y, -defaultCameraPosition.x)
    private val defaultLookVector = Vector3f(1.0f, 0.0f, 0.0f).rotateAxis(defaultCameraInclination, 0.0f, 1.0f, 0.0f).rotateAxis(defaultCameraHeading, 0.0f, 0.0f, 1.0f).normalize()

    private val cameraPosition = Vector3f(defaultCameraPosition)
    private var cameraInclination = defaultCameraInclination
    private var cameraHeading = defaultCameraHeading
    private val lookVector = Vector3f(defaultLookVector)

    private val defaultRotation = Quaternionf().rotate(0.0f, 0.0f, 0.0f)
    private val deltaRotation = Quaternionf()

    private val defaultViewMatrix = Matrix4f().setLookAt(Vector3f(0.0f, 0.0f, 0.5f), Vector3f(0.0f, 0.0f, -5.0f), Vector3f(0.0f, 1.0f, 0.0f))

    private val floatBuffer = BufferUtils.createFloatBuffer(16)

    private val minSpeed = 10.0f
    private val maxSpeed = 2000.0f
    private val defaultSpeed = 200.0f

    private var speed = defaultSpeed

    private val defaultTranslation = Vector3f(0.0f, 0.0f, -2073.58f)

    private val perspectiveToOrtho = 1302.0f

    private val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
    private val mvMatrixUniform = ShaderUniform("modelViewMatrix")
    private val nMatrixUniform = ShaderUniform("normalMatrix")
    private val lightDirectionUniform = ShaderUniform("lightDirection")
    private val cameraPositionUniform = ShaderUniform("cameraPosition")
    private val color1Uniform = ShaderUniform("color1")
    private val color2Uniform = ShaderUniform("color2")
    private val color3Uniform = ShaderUniform("color3")
    private val color4Uniform = ShaderUniform("color4")
    private val color5Uniform = ShaderUniform("color5")
    private val color6Uniform = ShaderUniform("color6")
    private val lightColorUniform = ShaderUniform("lightColor")
    private val baseColorUniform = ShaderUniform("baseColor")
    private val roughnessUniform = ShaderUniform("roughness")
    private val metallicUniform = ShaderUniform("metallic")
    private val specularIntensityUniform = ShaderUniform("specularIntensity")
    private val waterColorUniform = ShaderUniform("waterColor")
    private val waterRoughnessUniform = ShaderUniform("waterRoughness")
    private val waterMetallicUniform = ShaderUniform("waterMetallic")
    private val waterSpecularIntensityUniform = ShaderUniform("waterSpecularIntensity")
    private val indirectIntensityUniform = ShaderUniform("indirectIntensity")
    private val heightScaleUniform = ShaderUniform("heightScale")
    private val heightScaleMetersUniform = ShaderUniform("heightScaleMeters")
    private val colorHeightScaleUniform = ShaderUniform("colorHeightScale")
    private val uvScaleUniform = ShaderUniform("uvScale")
    private val renderOptionsUniform = ShaderUniform("renderOptions")
    private val horizonBlendUniform = ShaderUniform("horizonBlend")
    private val lightMaxFogEffectUniform = ShaderUniform("lightMaxFogEffect")
    private val heightMapTextureUniform = ShaderUniform("heightMapTexture")
    private val normalAoTextureUniform = ShaderUniform("normalAoTexture")
    private val fogParamsUniform = ShaderUniform("fogParams")
    private val riverMapTextureUniform = ShaderUniform("riverMapTexture")
    private val brdfMapUniform = ShaderUniform("brdfMap")
    private val irradianceMapUniform = ShaderUniform("irradianceMap")
    private val specularMapUniform = ShaderUniform("specularMap")
    private val lossTextureUniform = ShaderUniform("lossTexture")
    private val inscatterTextureUniform = ShaderUniform("inscatterTexture")
    private val inscatterHorizonTextureUniform = ShaderUniform("inscatterHorizonTexture")


    private val mvpMatrixUniformWater = ShaderUniform("modelViewProjectionMatrix")
    private val mvMatrixUniformWater = ShaderUniform("modelViewMatrix")
    private val lightDirectionUniformWater = ShaderUniform("lightDirection")
    private val cameraPositionUniformWater = ShaderUniform("cameraPosition")
    private val baseColorUniformWater = ShaderUniform("baseColor")
    private val lightColorUniformWater = ShaderUniform("lightColor")
    private val horizonBlendUniformWater = ShaderUniform("horizonBlend")
    private val lightMaxFogEffectUniformWater = ShaderUniform("lightMaxFogEffect")
    private val roughnessUniformWater = ShaderUniform("roughness")
    private val metallicUniformWater = ShaderUniform("metallic")
    private val specularIntensityUniformWater = ShaderUniform("specularIntensity")
    private val indirectIntensityUniformWater = ShaderUniform("indirectIntensity")
    private val heightScaleUniformWater = ShaderUniform("heightScale")
    private val waterLevelUniformWater = ShaderUniform("waterLevel")
    private val normalOffsetsUniformWater = ShaderUniform("normalOffsets")
    private val fadeStartsUniformWater = ShaderUniform("fadeStarts")
    private val fadeEndsUniformWater = ShaderUniform("fadeEnds")
    private val normalStrengthsUniformWater = ShaderUniform("normalStrengths")
    private val fogParamsUniformWater = ShaderUniform("fogParams")
    private val heightMapTextureUniformWater = ShaderUniform("heightMapTexture")
    private val brdfMapUniformWater = ShaderUniform("brdfMap")
    private val skyMapTextureUniformWater = ShaderUniform("skyMap")
    private val irradianceMapUniformWater = ShaderUniform("irradianceMap")
    private val waterNormalTextureUniform = ShaderUniform("waterNormalTexture")
    private val lossTextureUniformWater = ShaderUniform("lossTexture")
    private val inscatterTextureUniformWater = ShaderUniform("inscatterTexture")
    private val inscatterHorizonTextureUniformWater = ShaderUniform("inscatterHorizonTexture")


    private val mvpMatrixUniformRegion = ShaderUniform("modelViewProjectionMatrix")
    private val regionTextureUniform = ShaderUniform("regionTexture")

    private val mvpMatrixUniformImage = ShaderUniform("modelViewProjectionMatrix")
    private val imageTextureUniform = ShaderUniform("imageTexture")

    private val mvpMatrixUniformBiome = ShaderUniform("modelViewProjectionMatrix")
    private val biomeTextureUniform = ShaderUniform("biomeTexture")
    private val splineTextureUniform = ShaderUniform("splineTexture")
    private val biomeColorsTextureUniform = ShaderUniform("biomeColorsTexture")

    private val pMatrixUniformSky = ShaderUniform("projectionMatrix")
    private val mvMatrixUniformSky = ShaderUniform("modelViewMatrix")
    private val lightDirectionUniformSky = ShaderUniform("lightDirection")
    private val lightColorUniformSky = ShaderUniform("lightColor")
    private val cameraPositionUniformSky = ShaderUniform("cameraPosition")
    private val horizonBlendUniformSky = ShaderUniform("horizonBlend")
    private val lightMaxFogEffectUniformSky = ShaderUniform("lightMaxFogEffect")
    private val fogParamsUniformSky = ShaderUniform("fogParams")
    private val skyMapUniformSky = ShaderUniform("skyMap")
    private val lossTextureUniformSky = ShaderUniform("lossTexture")
    private val inscatterTextureUniformSky = ShaderUniform("inscatterTexture")
    private val inscatterHorizonTextureUniformSky = ShaderUniform("inscatterHorizonTexture")


    private val positionAttribute = ShaderAttribute("position")
    private val uvAttribute = ShaderAttribute("uv")

    private val positionAttributeWater = ShaderAttribute("position")
    private val uvAttributeWater = ShaderAttribute("uv")

    private val positionAttributeRegion = ShaderAttribute("position")
    private val uvAttributeRegion = ShaderAttribute("uv")

    private val positionAttributeImage = ShaderAttribute("position")
    private val uvAttributeImage = ShaderAttribute("uv")

    private val positionAttributeBiome = ShaderAttribute("position")
    private val uvAttributeBiome = ShaderAttribute("uv")

    private val positionAttributeSky = ShaderAttribute("position")
    private val uvAttributeSky = ShaderAttribute("uv")

    private val textureLock = Object()
    private var hasHeightmap = false
    private var heightMapId: TextureId? = null
    private var normalAoMapId: TextureId? = null
    private var rivermapId: TextureId? = null
    private var heightMapResolution = 0

    private var hasRegions = false
    private var regionTextureId: TextureId? = null

    private var hasBiomes = false
    private var biomeTextureId: TextureId? = null
    private var splineTextureId: TextureId? = null
    private var biomeColorsTextureId: TextureId? = null

    private var hasImage = false
    private var imageTextureId: TextureId? = null

    private val background = NPColor.create().set(30, 30, 30)

    private val lightDirection = Vector3f(0.0f, 0.0f, 1.0f)

    private var heightMapProgram: Int = 0
    private var waterPlaneProgram: Int = 0
    private var regionPlaneProgram: Int = 0
    private var imagePlaneProgram: Int = 0
    private var biomePlaneProgram: Int = 0
    private var skyPlaneProgram: Int = 0

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

    private var texCoordX = 0.0f
    private var texCoordY = 0.0f

    private var lastTexCoordX = 0.0f
    private var lastTexCoordY = 0.0f

    private var hotZoneX1 = 0
    private var hotZoneX2 = 0
    private var hotZoneY1 = 0
    private var hotZoneY2 = 0

    private var texAreaX1 = 0
    private var texAreaX2 = 0
    private var texAreaY1 = 0
    private var texAreaY2 = 0

    private var isFlyModeOn = false

    private lateinit var heightMap: HexGrid

    private lateinit var waterPlane: HexGrid

    private lateinit var imagePlane: ImagePlane

    private lateinit var regionPlane: ImagePlane

    private lateinit var biomePlane: ImagePlane

    private lateinit var skyPlane: ImagePlane

    private lateinit var irradianceMap: TextureId

    private lateinit var specularMap: TextureId

    private lateinit var skyMap: TextureId

    private lateinit var brdfMap: TextureId

    private lateinit var waterNormalTexture: TextureId

    private lateinit var lossTexture: TextureId

    private lateinit var inscatterTexture: TextureId

    private lateinit var inscatterHorizonTexture: TextureId

    val keyboardHandler: KeyboardHandler = viewportKeyboardHandler()

    fun init() {

        val refreshUiListener: (Any, Any) -> Unit = { old, new -> if (old != new) refreshUi() }

        lightColor[0].addListener(refreshUiListener)
        lightColor[1].addListener(refreshUiListener)
        lightColor[2].addListener(refreshUiListener)

        lightElevation.addListener(refreshUiListener)
        lightHeading.addListener(refreshUiListener)

        indirectIntensity.addListener(refreshUiListener)

        baseColor[0].addListener(refreshUiListener)
        baseColor[1].addListener(refreshUiListener)
        baseColor[2].addListener(refreshUiListener)

        metallic.addListener(refreshUiListener)
        roughness.addListener(refreshUiListener)
        specularIntensity.addListener(refreshUiListener)

        waterParams.addListener(refreshUiListener)

        fogParams.addListener(refreshUiListener)

        skyOn.addListener(refreshUiListener)

        fogOn.addListener(refreshUiListener)

        glEnable(GL_DEPTH_TEST)


        viewMatrix.set(defaultViewMatrix)

        irradianceMap = loadCubeMap3DF32("/textures/environment-irra.cub").first

        specularMap = loadCubeMap3DF32("/textures/environment-spec.cub").first

        skyMap = loadCubeMap3DF8("/textures/environment-sky.cub").first

        brdfMap = loadBrdfMap("/textures/environment-brdf.tex").first

        waterNormalTexture = loadNormalMap("/textures/water-normal.png").first

        lossTexture = loadTextureF32("/textures/loss.tex").first

        inscatterTexture = loadTextureF32("/textures/inscatter.tex").first

        inscatterHorizonTexture = loadTextureF32("/textures/inscatter-horizon.tex").first

        val heightMapVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/height-map.vert"))
        val heightMapFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/height-map.frag"))

        val waterVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/water-plane.vert"))
        val waterFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/water-plane.frag"))

        val regionVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/regions.vert"))
        val regionFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/regions.frag"))

        val biomeVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/biomes.vert"))
        val biomeFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/biomes.frag"))

        biomeColorsTextureId = TextureId(loadColorsAsTexture(BIOME_COLORS, 8, 1))

        val imagePlaneVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/image-plane.vert"))
        val imagePlaneFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/image-plane.frag"))

        val skyPlaneVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/sky-plane.vert"))
        val skyPlaneFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/sky-plane.frag"))

        heightMapProgram = createAndLinkProgram(
                listOf(heightMapVertexShader, heightMapFragmentShader),
                listOf(positionAttribute, uvAttribute),
                listOf(mvpMatrixUniform,
                        mvMatrixUniform,
                        nMatrixUniform,
                        lightDirectionUniform,
                        cameraPositionUniform,
                        color1Uniform,
                        color2Uniform,
                        color3Uniform,
                        color4Uniform,
                        color5Uniform,
                        color6Uniform,
                        lightColorUniform,
                        baseColorUniform,
                        roughnessUniform,
                        metallicUniform,
                        specularIntensityUniform,
                        waterColorUniform,
                        waterRoughnessUniform,
                        waterMetallicUniform,
                        waterSpecularIntensityUniform,
                        indirectIntensityUniform,
                        heightScaleUniform,
                        heightScaleMetersUniform,
                        colorHeightScaleUniform,
                        uvScaleUniform,
                        renderOptionsUniform,
                        horizonBlendUniform,
                        lightMaxFogEffectUniform,
                        heightMapTextureUniform,
                        normalAoTextureUniform,
                        fogParamsUniform,
                        riverMapTextureUniform,
                        brdfMapUniform,
                        irradianceMapUniform,
                        specularMapUniform,
                        lossTextureUniform,
                        inscatterTextureUniform,
                        inscatterHorizonTextureUniform))

        waterPlaneProgram = createAndLinkProgram(
                listOf(waterVertexShader, waterFragmentShader),
                listOf(positionAttributeWater, uvAttributeWater),
                listOf(mvpMatrixUniformWater,
                        mvMatrixUniformWater,
                        lightDirectionUniformWater,
                        cameraPositionUniformWater,
                        baseColorUniformWater,
                        lightColorUniformWater,
                        horizonBlendUniformWater,
                        lightMaxFogEffectUniformWater,
                        roughnessUniformWater,
                        metallicUniformWater,
                        specularIntensityUniformWater,
                        indirectIntensityUniformWater,
                        heightScaleUniformWater,
                        waterLevelUniformWater,
                        normalOffsetsUniformWater,
                        fadeStartsUniformWater,
                        fadeEndsUniformWater,
                        normalStrengthsUniformWater,
                        fogParamsUniformWater,
                        brdfMapUniformWater,
                        skyMapTextureUniformWater,
                        irradianceMapUniformWater,
                        waterNormalTextureUniform,
                        heightMapTextureUniformWater,
                        lossTextureUniformWater,
                        inscatterTextureUniformWater,
                        inscatterHorizonTextureUniformWater))

        regionPlaneProgram = createAndLinkProgram(
                listOf(regionVertexShader, regionFragmentShader),
                listOf(positionAttributeRegion, uvAttributeRegion),
                listOf(mvpMatrixUniformRegion, regionTextureUniform))

        biomePlaneProgram = createAndLinkProgram(
                listOf(biomeVertexShader, biomeFragmentShader),
                listOf(positionAttributeBiome, uvAttributeBiome),
                listOf(mvpMatrixUniformBiome, biomeTextureUniform, splineTextureUniform, biomeColorsTextureUniform))

        imagePlaneProgram = createAndLinkProgram(
                listOf(imagePlaneVertexShader, imagePlaneFragmentShader),
                listOf(positionAttributeImage, uvAttributeImage),
                listOf(mvpMatrixUniformImage, imageTextureUniform))

        skyPlaneProgram = createAndLinkProgram(
                listOf(skyPlaneVertexShader, skyPlaneFragmentShader),
                listOf(positionAttributeSky, uvAttributeSky),
                listOf(pMatrixUniformSky,
                        mvMatrixUniformSky,
                        lightDirectionUniformSky,
                        lightColorUniformSky,
                        cameraPositionUniformSky,
                        horizonBlendUniformSky,
                        lightMaxFogEffectUniformSky,
                        fogParamsUniformSky,
                        skyMapUniformSky,
                        lossTextureUniformSky,
                        inscatterTextureUniformSky,
                        inscatterHorizonTextureUniformSky
                ))

        heightMap = HexGrid(VIEWPORT_MESH_SCALE - 40.0f, 10000, positionAttribute, uvAttribute, true)

        waterPlane = HexGrid(100000.0f, 16, positionAttributeWater, uvAttributeWater, true)

        imagePlane = ImagePlane(VIEWPORT_MESH_SCALE, positionAttributeImage, uvAttributeImage)

        regionPlane = ImagePlane(VIEWPORT_MESH_SCALE, positionAttributeRegion, uvAttributeRegion)

        biomePlane = ImagePlane(VIEWPORT_MESH_SCALE, positionAttributeBiome, uvAttributeBiome)

        skyPlane = ImagePlane(2.0f, positionAttributeSky, uvAttributeSky)

        lightDirection.normalize()

        modelMatrix.translate(defaultTranslation)
    }

    private fun refreshUi() {
        uiRoot.value.movedOrResized = true
    }

    fun reset() {
        synchronized(textureLock) {
            hasHeightmap = false
            heightMapId = null
            normalAoMapId = null
            rivermapId = null
            heightMapResolution = 0
            hasRegions = false
            regionTextureId = null
            hasBiomes = false
            biomeTextureId = null
            splineTextureId = null
            hasImage = false
            imageTextureId = null
        }
        refreshUi()
    }

    fun setHeightmap(newTexture: Triple<TextureId?, TextureId?, TextureId?>, resolution: Int) {
        synchronized(textureLock) {
            hasHeightmap = true
            heightMapId = newTexture.first
            normalAoMapId = newTexture.third
            rivermapId = newTexture.second
            heightMapResolution = resolution
        }
        refreshUi()
    }

    fun setRegions(textureId: TextureId) {
        synchronized(textureLock) {
            hasRegions = true
            regionTextureId = textureId
        }
        refreshUi()
    }

    fun setBiomes(biomeTextureId: TextureId, splineTextureId: TextureId) {
        synchronized(textureLock) {
            hasBiomes = true
            this.biomeTextureId = biomeTextureId
            this.splineTextureId = splineTextureId
        }
        refreshUi()
    }

    fun setBiomes(biomeTextureId: TextureId) {
        synchronized(textureLock) {
            this.biomeTextureId = biomeTextureId
        }
        refreshUi()
    }

    fun setSplines(splineTextureId: TextureId) {
        synchronized(textureLock) {
            this.splineTextureId = splineTextureId
        }
        refreshUi()
    }

    fun setImage(textureId: TextureId) {
        synchronized(textureLock) {
            hasImage = true
            imageTextureId = textureId
        }
        refreshUi()
    }

    fun onMouseDown(button: Int, x: Int, y: Int) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (brushOn.value) {
                if (x in texAreaX1..texAreaX2 && y in texAreaY1..texAreaY2) {
                    val width = texAreaX2 - texAreaX1
                    val height = texAreaY2 - texAreaY1
                    val xOff = x - texAreaX1
                    val yOff = y - texAreaY1
                    texCoordX = xOff.toFloat() / width
                    texCoordY = yOff.toFloat() / height
                    lastTexCoordX = texCoordX
                    lastTexCoordY = texCoordY
                    brushListener.value?.onMouseDown(texCoordX, texCoordY)
                    brushSize.value = editBrushSizeRef.value.value * width
                    brushActive.value = true
                    hideCursor.value = true
                }
            } else if (pickerOn.value) {
                if (x in texAreaX1..texAreaX2 && y in texAreaY1..texAreaY2) {
                    val width = texAreaX2 - texAreaX1
                    val height = texAreaY2 - texAreaY1
                    val xOff = x - texAreaX1
                    val yOff = y - texAreaY1
                    pointPicker.value?.onMouseDown(xOff.toFloat() / width, yOff.toFloat() / height)
                }
            }
            refreshUi()
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            lastMouseX = x.toFloat()
            lastMouseY = y.toFloat()
            isFlyModeOn = true
            refreshUi()
        }
    }

    fun onMouseDrag(x: Int, y: Int) {
        mouseX = x
        mouseY = y
        if (brushOn.value && x in texAreaX1..texAreaX2 && y in texAreaY1..texAreaY2) {
            val width = texAreaX2 - texAreaX1
            val height = texAreaY2 - texAreaY1
            val xOff = x - texAreaX1
            val yOff = y - texAreaY1
            val tempTexCoordX = xOff.toFloat() / width
            val tempTexCoordY = yOff.toFloat() / height
            if (tempTexCoordX != lastTexCoordX || tempTexCoordY != lastTexCoordY) {
                lastTexCoordX = texCoordX
                lastTexCoordY = texCoordY
                texCoordX = tempTexCoordX
                texCoordY = tempTexCoordY
                brushListener.value?.onLine(lastTexCoordX, lastTexCoordY, texCoordX, texCoordY)
            }
        }
        refreshUi()
    }

    fun onMouseRelease(button: Int, x: Int, y: Int) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (brushOn.value) {
                val width = texAreaX2 - texAreaX1
                val height = texAreaY2 - texAreaY1
                val xOff = x - texAreaX1
                val yOff = y - texAreaY1
                val tempTexCoordX = xOff.toFloat() / width
                val tempTexCoordY = yOff.toFloat() / height
                lastTexCoordX = texCoordX
                lastTexCoordY = texCoordY
                texCoordX = tempTexCoordX
                texCoordY = tempTexCoordY
                brushListener.value?.onMouseUp(lastTexCoordX, lastTexCoordY, texCoordX, texCoordY)
            }
            refreshUi()
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            if (isFlyModeOn) {
                isFlyModeOn = false
                eliminateMovement = true
            }
            refreshUi()
        }
        if (brushActive.value && hideCursor.value) {
            hideCursor.value = false
        }
        brushActive.value = false
    }

    fun onScroll(scrollDelta: Double) {
        lastScroll = scroll
        deltaScroll = scrollDelta.toFloat()
        val increment = (speed - minSpeed + 110.0f) / (maxSpeed - minSpeed + 110.0f)
        val desiredSpeed = speed + increment * deltaScroll * 200.0f
        speed = clamp(desiredSpeed, minSpeed, maxSpeed)
        refreshUi()
    }

    private var lastKeyCheck = -1L

    private fun checkKeysPressed() {
        val currentTime = System.nanoTime()
        if (lastKeyCheck == -1L) {
            lastKeyCheck = currentTime
            return
        }
        val deltaTime = (currentTime - lastKeyCheck) / 1000000000.0f
        lastKeyCheck = currentTime
        if (isFlyModeOn) {
            if (!disableCursor.value) {
                disableCursor.value = true
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_W)) {
                val increment = speed * deltaTime
                cameraPosition.add(lookVector.mul(increment, Vector3f()))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_S)) {
                val increment = -speed * deltaTime
                cameraPosition.add(lookVector.mul(increment, Vector3f()))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_A)) {
                val increment = -speed * deltaTime
                val right = lookVector.cross(0.0f, 0.0f, 1.0f, Vector3f())
                cameraPosition.add(right.mul(increment))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_D)) {
                val increment = speed * deltaTime
                val right = lookVector.cross(0.0f, 0.0f, 1.0f, Vector3f())
                cameraPosition.add(right.mul(increment))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_SPACE)) {
                val increment = speed * deltaTime
                cameraPosition.z += increment
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                val increment = -speed * deltaTime
                cameraPosition.z += increment
                refreshUi()
            }
        } else if (disableCursor.value) {
            disableCursor.value = false
            refreshUi()
        }
    }

    fun clearKeysPressed() {
        pressedKeys.clear()
    }

    fun onDrawFrame(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        when {
            imageMode.value == 0 -> onDrawFrameInternalRegion(xPosition, yPosition, width, height, rootHeight, scale)
            imageMode.value == 1 -> onDrawFrameInternalImage(xPosition, yPosition, width, height, rootHeight, scale)
            imageMode.value == 2 -> onDrawFrameInternalBiome(xPosition, yPosition, width, height, rootHeight, scale)
            else -> onDrawFrameInternalHeightMap(xPosition, yPosition, width, height, rootHeight, scale)
        }
    }

    private fun onDrawFrameInternalRegion(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        if (width < 1 || height < 1) {
            return
        }
        synchronized(textureLock) {
            if (hasRegions) {
                val adjustedWidth = round(width / scale)
                val adjustedHeight = round(height / scale)
                val adjustedX = round(xPosition / scale)
                val adjustedY = round(yPosition / scale)

                texAreaX1 = (adjustedX + (adjustedWidth / 2)) - (adjustedHeight / 2)
                texAreaY1 = adjustedY
                texAreaX2 = texAreaX1 + adjustedHeight
                texAreaY2 = texAreaY1 + adjustedHeight

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
                val orthoZoom = perspectiveToOrtho
                projectionMatrix.setOrtho(premulRatio * -orthoZoom, premulRatio * orthoZoom, -orthoZoom, orthoZoom, 6.0f, 6000.0f)

                speed = defaultSpeed
                modelMatrix.translation(defaultTranslation).rotate(defaultRotation)

                deltaX = mouseX - lastMouseX
                deltaY = mouseY - lastMouseY

                lastMouseX = mouseX.toFloat()
                lastMouseY = mouseY.toFloat()

                deltaRotation.identity()

                viewMatrix.set(defaultViewMatrix)
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

                drawRegionPlane()

                glDisable(GL_SCISSOR_TEST)
            }
        }
    }

    private fun onDrawFrameInternalBiome(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        if (width < 1 || height < 1) {
            return
        }
        synchronized(textureLock) {
            if (hasBiomes) {
                val adjustedWidth = round(width / scale)
                val adjustedHeight = round(height / scale)
                val adjustedX = round(xPosition / scale)
                val adjustedY = round(yPosition / scale)

                texAreaX1 = (adjustedX + (adjustedWidth / 2)) - (adjustedHeight / 2)
                texAreaY1 = adjustedY
                texAreaX2 = texAreaX1 + adjustedHeight
                texAreaY2 = texAreaY1 + adjustedHeight

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
                val orthoZoom = perspectiveToOrtho
                projectionMatrix.setOrtho(premulRatio * -orthoZoom, premulRatio * orthoZoom, -orthoZoom, orthoZoom, 6.0f, 6000.0f)

                speed = defaultSpeed
                modelMatrix.translation(defaultTranslation).rotate(defaultRotation)

                deltaX = mouseX - lastMouseX
                deltaY = mouseY - lastMouseY

                lastMouseX = mouseX.toFloat()
                lastMouseY = mouseY.toFloat()

                deltaRotation.identity()

                viewMatrix.set(defaultViewMatrix)
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

                drawBiomePlane()

                glDisable(GL_SCISSOR_TEST)
            }
        }
    }

    private fun onDrawFrameInternalImage(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        if (width < 1 || height < 1) {
            return
        }
        synchronized(textureLock) {
            if (hasImage) {
                val adjustedWidth = round(width / scale)
                val adjustedHeight = round(height / scale)
                val adjustedX = round(xPosition / scale)
                val adjustedY = round(yPosition / scale)

                texAreaX1 = (adjustedX + (adjustedWidth / 2)) - (adjustedHeight / 2)
                texAreaY1 = adjustedY
                texAreaX2 = texAreaX1 + adjustedHeight
                texAreaY2 = texAreaY1 + adjustedHeight

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
                val orthoZoom = perspectiveToOrtho
                projectionMatrix.setOrtho(premulRatio * -orthoZoom, premulRatio * orthoZoom, -orthoZoom, orthoZoom, 6.0f, 6000.0f)

                speed = defaultSpeed
                modelMatrix.translation(defaultTranslation).rotate(defaultRotation)

                deltaX = mouseX - lastMouseX
                deltaY = mouseY - lastMouseY

                lastMouseX = mouseX.toFloat()
                lastMouseY = mouseY.toFloat()

                deltaRotation.identity()

                viewMatrix.set(defaultViewMatrix)
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

    private fun onDrawFrameInternalHeightMap(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {

        if (width < 1 || height < 1) {
            return
        }
        synchronized(textureLock) {
            if (hasHeightmap) {
                val eliminateMovementNext = eliminateMovement && !disableCursor.value
                checkKeysPressed()
                val adjustedWidth = round(width / scale)
                val adjustedHeight = round(height / scale)
                val adjustedX = round(xPosition / scale)
                val adjustedY = round(yPosition / scale)

                texAreaX1 = (adjustedX + (adjustedWidth / 2)) - (adjustedHeight / 2)
                texAreaY1 = adjustedY
                texAreaX2 = texAreaX1 + adjustedHeight
                texAreaY2 = texAreaY1 + adjustedHeight

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
                val skyOn = skyOn.value
                val fogOn = fogOn.value
                val heightColorsOn = heightColorsOn.value
                val riversOn = riversOn.value
                val doReset = resetView.value
                if (doReset) {
                    resetView.value = false
                }
                val perspectiveOn = perspectiveOn.value

                if (doReset) {
                    speed = defaultSpeed
                    modelMatrix.translation(defaultTranslation).rotate(defaultRotation)
                    cameraPosition.set(defaultCameraPosition)
                    cameraInclination = defaultCameraInclination
                    cameraHeading = defaultCameraHeading
                    lookVector.set(defaultLookVector)
                    waterParams.level.value = defaultWaterLevel
                }

                if (eliminateMovement) {
                    deltaX = 0.0f
                    deltaY = 0.0f

                    lastMouseX = mouseX.toFloat()
                    lastMouseY = mouseY.toFloat()

                    if (eliminateMovementNext) {
                        eliminateMovement = false
                    }
                } else {
                    deltaX = mouseX - lastMouseX
                    deltaY = mouseY - lastMouseY

                    lastMouseX = mouseX.toFloat()
                    lastMouseY = mouseY.toFloat()
                }

                val premulRatio = width / height.toFloat()
                if (perspectiveOn) {
                    val fov = 1.5708f
                    val aspect = width / height.toFloat()
                    projectionMatrix.set(fov / aspect, 0.0f, 0.0f, 0.0f, 0.0f, fov, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f)
                } else {
                    val orthoZoom = perspectiveToOrtho
                    projectionMatrix.setOrtho(premulRatio * -orthoZoom, premulRatio * orthoZoom, -orthoZoom, orthoZoom, 6.0f, 6000.0f)
                }

                if (isFlyModeOn) {
                    cameraInclination = clamp(cameraInclination + (deltaY * mouseSpeed), -1.48353f, 1.48353f)
                    cameraHeading = ((cameraHeading + (-deltaX * mouseSpeed) + 6.28319f) % 6.28319f)
                }

                modelMatrix.identity()
                lookVector.set(Vector3f(1.0f, 0.0f, 0.0f).rotateAxis(cameraInclination, 0.0f, 1.0f, 0.0f).rotateAxis(cameraHeading, 0.0f, 0.0f, 1.0f).normalize())
                viewMatrix.setLookAt(cameraPosition, cameraPosition.add(lookVector, Vector3f()), Vector3f(0.0f, 0.0f, 1.0f))

                viewMatrix.mul(modelMatrix, mvMatrix)
                normalMatrix.set(mvMatrix).invert().transpose()
                projectionMatrix.mul(mvMatrix, mvpMatrix)

                lightDirection.set(1.0f, 0.0f, 0.0f).rotateAxis(toRadians(-lightElevation.value.toDouble()).toFloat(), 0.0f, 1.0f, 0.0f).rotateAxis(toRadians(-lightHeading.value.toDouble()).toFloat(), 0.0f, 0.0f, 1.0f).normalize()

                glDisable(GL_BLEND)
                glDisable(GL_CULL_FACE)
                glEnable(GL_DEPTH_TEST)
                glEnable(GL_SCISSOR_TEST)
                glEnable(GL_MULTISAMPLE)

                val filteredLight = filterLight()

                glClearColor(background.r, background.g, background.b, background.a)
                glScissor(xPosition, flippedY, width, height)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

                glViewport(xPosition, flippedY, width, height)

                fogParams.fogOn.value = if (fogOn) 1.0f else 0.0f

                val renderOptions = (if (heightColorsOn) 0 else 1) + (if (riversOn) 0 else 2)
                drawHeightMap(renderOptions, filteredLight)
                if (waterOn) {
                    drawWaterPlane(filteredLight)
                }
                if (skyOn) {
                    drawSkyPlane(filteredLight)
                }

                glDisable(GL_SCISSOR_TEST)
            }
        }
    }

    private fun filterLight(): Quadruple<Float, Float, Float, Float> {
        var lower = lightFilter[0]
        var upper = lightFilter[1]
        val elevation = lightElevation.value
        for (i in 0 until lightFilter.size - 1) {
            if (elevation > lightFilter[i][3] && elevation <= lightFilter[i + 1][3]) {
                lower = lightFilter[i]
                upper = lightFilter[i + 1]
                break
            }
        }
        val delta = upper[3] - lower[3]
        val offset = elevation - lower[3]
        val alpha = offset / delta
        val iAlpha = 1 - alpha
        val intensity = lower[4] * iAlpha + upper[4] * alpha
        val r = lightFilterValue(lower, upper, 0, intensity, alpha, iAlpha)
        val g = lightFilterValue(lower, upper, 1, intensity, alpha, iAlpha)
        val b = lightFilterValue(lower, upper, 2, intensity, alpha, iAlpha)
        val p = lightPowerValue(lower, upper, 5, alpha, iAlpha)
        return Quadruple(r, g, b, p)
    }

    private fun lightFilterValue(lower: FloatArray, upper: FloatArray, index: Int, intensity: Float, alpha: Float, iAlpha: Float): Float = lightColor[index].value * intensity * (lower[index] * iAlpha + upper[index] * alpha)

    private fun lightPowerValue(lower: FloatArray, upper: FloatArray, index: Int, alpha: Float, iAlpha: Float): Float = (lower[index] * iAlpha + upper[index] * alpha)

    private fun drawSkyPlane(lightColor: Quadruple<Float, Float, Float, Float>) {
        glUseProgram(skyPlaneProgram)
        glUniformMatrix4fv(pMatrixUniformSky.location, false, projectionMatrix.get(0, floatBuffer))
        glUniformMatrix4fv(mvMatrixUniformSky.location, false, mvMatrix.get(0, floatBuffer))
        glUniform3f(lightDirectionUniformSky.location, lightDirection.x, lightDirection.y, lightDirection.z)
        glUniform4f(lightColorUniformSky.location, lightColor.first, lightColor.second, lightColor.third, 1.0f)
        glUniform3f(cameraPositionUniformSky.location, cameraPosition.x, cameraPosition.y, cameraPosition.z)
        glUniform1f(horizonBlendUniformSky.location, 1.0f - clamp(toRadians(lightElevation.value.toDouble()).toFloat() * 5.0f, 0.0f, 1.0f))
        glUniform1f(lightMaxFogEffectUniformSky.location, lightColor.fourth)
        glUniformMatrix3fv(fogParamsUniformSky.location, false, floatArrayOf(
                fogParams.color[0].value,
                fogParams.color[1].value,
                fogParams.color[2].value,
                fogParams.atmosphericFogDensity.value,
                fogParams.exponentialFogDensity.value,
                fogParams.exponentialFogHeightFalloff.value,
                fogParams.fogHeightClampPower.value,
                fogParams.fogOn.value,
                0.0f))
        glUniform1i(skyMapUniformSky.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_CUBE_MAP, skyMap.id)
        glUniform1i(lossTextureUniformSky.location, 1)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, lossTexture.id)
        glUniform1i(inscatterTextureUniformSky.location, 2)
        glActiveTexture(GL_TEXTURE2)
        glBindTexture(GL_TEXTURE_2D, inscatterTexture.id)
        glUniform1i(inscatterHorizonTextureUniformSky.location, 3)
        glActiveTexture(GL_TEXTURE3)
        glBindTexture(GL_TEXTURE_2D, inscatterHorizonTexture.id)
        skyPlane.render()
    }

    private fun drawHeightMap(renderOptions: Int, lightColor: Quadruple<Float, Float, Float, Float>) {
        glUseProgram(heightMapProgram)
        glUniformMatrix4fv(mvMatrixUniform.location, false, mvMatrix.get(0, floatBuffer))
        glUniformMatrix3fv(nMatrixUniform.location, false, normalMatrix.get(0, floatBuffer))
        glUniformMatrix4fv(mvpMatrixUniform.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform3f(lightDirectionUniform.location, lightDirection.x, lightDirection.y, lightDirection.z)
        glUniform3f(cameraPositionUniform.location, cameraPosition.x, cameraPosition.y, cameraPosition.z)
        glUniform4f(color1Uniform.location, 0.157f, 0.165f, 0.424f, 1.0f)
        glUniform4f(color2Uniform.location, 0.459f, 0.761f, 0.859f, 1.0f)
        glUniform4f(color3Uniform.location, 0.353f, 0.706f, 0.275f, 1.0f)
        glUniform4f(color4Uniform.location, 0.922f, 0.922f, 0.157f, 1.0f)
        glUniform4f(color5Uniform.location, 0.835f, 0.176f, 0.165f, 1.0f)
        glUniform4f(color6Uniform.location, 0.955f, 0.955f, 0.955f, 1.0f)
        glUniform4f(lightColorUniform.location, lightColor.first, lightColor.second, lightColor.third, 1.0f)
        glUniform4f(baseColorUniform.location, baseColor[0].value, baseColor[1].value, baseColor[2].value, 1.0f)
        glUniform1f(roughnessUniform.location, roughness.value)
        glUniform1f(metallicUniform.location, metallic.value)
        glUniform1f(specularIntensityUniform.location, specularIntensity.value)
        glUniform4f(waterColorUniform.location, waterParams.color[0].value, waterParams.color[1].value, waterParams.color[2].value, 1.0f)
        glUniform1f(waterRoughnessUniform.location, waterParams.roughness.value)
        glUniform1f(waterMetallicUniform.location, waterParams.metallic.value)
        glUniform1f(waterSpecularIntensityUniform.location, waterParams.specularIntensity.value)
        glUniform1f(indirectIntensityUniform.location, indirectIntensity.value)
        glUniform1f(heightScaleUniform.location, heightMapScaleFactor.value)
        glUniform1f(heightScaleMetersUniform.location, heightRangeMeters.value)
        glUniform1f(colorHeightScaleUniform.location, colorHeightScaleFactor.value)
        glUniform1f(uvScaleUniform.location, (1.0f / ((heightMapScaleFactor.value / heightRangeMeters.value) / VIEWPORT_MESH_SCALE)) / heightMapResolution)
        glUniform1i(renderOptionsUniform.location, renderOptions)
        glUniform1f(horizonBlendUniform.location, 1.0f - clamp(toRadians(lightElevation.value.toDouble()).toFloat() * 5.0f, 0.0f, 1.0f))
        glUniform1f(lightMaxFogEffectUniform.location, lightColor.fourth)
        glUniformMatrix3fv(fogParamsUniform.location, false, floatArrayOf(
                fogParams.color[0].value,
                fogParams.color[1].value,
                fogParams.color[2].value,
                fogParams.atmosphericFogDensity.value,
                fogParams.exponentialFogDensity.value,
                fogParams.exponentialFogHeightFalloff.value,
                fogParams.fogHeightClampPower.value,
                fogParams.fogOn.value,
                0.0f))
        glUniform1i(heightMapTextureUniform.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, heightMapId?.id ?: -1)
        glUniform1i(normalAoTextureUniform.location, 1)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, normalAoMapId?.id ?: -1)
        glUniform1i(riverMapTextureUniform.location, 2)
        glActiveTexture(GL_TEXTURE2)
        glBindTexture(GL_TEXTURE_2D, rivermapId?.id ?: -1)
        glUniform1i(brdfMapUniform.location, 3)
        glActiveTexture(GL_TEXTURE3)
        glBindTexture(GL_TEXTURE_2D, brdfMap.id)
        glUniform1i(irradianceMapUniform.location, 4)
        glActiveTexture(GL_TEXTURE4)
        glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap.id)
        glUniform1i(specularMapUniform.location, 5)
        glActiveTexture(GL_TEXTURE5)
        glBindTexture(GL_TEXTURE_CUBE_MAP, specularMap.id)
        glUniform1i(lossTextureUniform.location, 6)
        glActiveTexture(GL_TEXTURE6)
        glBindTexture(GL_TEXTURE_2D, lossTexture.id)
        glUniform1i(inscatterTextureUniform.location, 7)
        glActiveTexture(GL_TEXTURE7)
        glBindTexture(GL_TEXTURE_2D, inscatterTexture.id)
        glUniform1i(inscatterHorizonTextureUniform.location, 8)
        glActiveTexture(GL_TEXTURE8)
        glBindTexture(GL_TEXTURE_2D, inscatterHorizonTexture.id)

        heightMap.render()
    }

    private fun drawWaterPlane(lightColor: Quadruple<Float, Float, Float, Float>) {
        glUseProgram(waterPlaneProgram)
        glUniformMatrix4fv(mvMatrixUniformWater.location, false, mvMatrix.get(0, floatBuffer))
        glUniformMatrix4fv(mvpMatrixUniformWater.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform3f(lightDirectionUniformWater.location, lightDirection.x, lightDirection.y, lightDirection.z)
        glUniform3f(cameraPositionUniformWater.location, cameraPosition.x, cameraPosition.y, cameraPosition.z)
        glUniform4f(baseColorUniformWater.location, waterParams.color[0].value, waterParams.color[1].value, waterParams.color[2].value, 1.0f)
        glUniform4f(lightColorUniformWater.location, lightColor.first, lightColor.second, lightColor.third, 1.0f)
        glUniform1f(horizonBlendUniformWater.location, 1.0f - clamp(toRadians(lightElevation.value.toDouble()).toFloat() * 5.0f, 0.0f, 1.0f))
        glUniform1f(lightMaxFogEffectUniformWater.location, lightColor.fourth)
        glUniform1f(roughnessUniformWater.location, waterParams.roughness.value)
        glUniform1f(metallicUniformWater.location, waterParams.metallic.value)
        glUniform1f(specularIntensityUniformWater.location, waterParams.specularIntensity.value)
        glUniform1f(indirectIntensityUniformWater.location, indirectIntensity.value * 2.2f)
        glUniform1f(heightScaleUniformWater.location, heightMapScaleFactor.value)
        glUniform1f(waterLevelUniformWater.location, waterParams.level.value)
        glUniformMatrix3fv(fogParamsUniformWater.location, false, floatArrayOf(
                fogParams.color[0].value,
                fogParams.color[1].value,
                fogParams.color[2].value,
                fogParams.atmosphericFogDensity.value,
                fogParams.exponentialFogDensity.value,
                fogParams.exponentialFogHeightFalloff.value,
                fogParams.fogHeightClampPower.value,
                fogParams.fogOn.value,
                0.0f))
        glUniformMatrix3fv(normalOffsetsUniformWater.location, false, waterParams.normalOffsets.map { it.value }.toFloatArray())
        glUniform3f(fadeStartsUniformWater.location, waterParams.fadeStarts[0].value, waterParams.fadeStarts[1].value, waterParams.fadeStarts[2].value)
        glUniform3f(fadeEndsUniformWater.location, waterParams.fadeEnds[0].value, waterParams.fadeEnds[1].value, waterParams.fadeEnds[2].value)
        glUniform4f(normalStrengthsUniformWater.location, waterParams.normalStrengths[0].value, waterParams.normalStrengths[1].value, waterParams.normalStrengths[2].value, waterParams.normalStrengths[3].value)
        glUniform1i(brdfMapUniformWater.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, brdfMap.id)
        glUniform1i(irradianceMapUniformWater.location, 1)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_CUBE_MAP, irradianceMap.id)
        glUniform1i(skyMapTextureUniformWater.location, 2)
        glActiveTexture(GL_TEXTURE2)
        glBindTexture(GL_TEXTURE_CUBE_MAP, skyMap.id)
        glUniform1i(waterNormalTextureUniform.location, 3)
        glActiveTexture(GL_TEXTURE3)
        glBindTexture(GL_TEXTURE_2D, waterNormalTexture.id)
        glUniform1i(heightMapTextureUniformWater.location, 4)
        glActiveTexture(GL_TEXTURE4)
        glBindTexture(GL_TEXTURE_2D, heightMapId?.id ?: -1)
        glUniform1i(lossTextureUniformWater.location, 5)
        glActiveTexture(GL_TEXTURE5)
        glBindTexture(GL_TEXTURE_2D, lossTexture.id)
        glUniform1i(inscatterTextureUniformWater.location, 6)
        glActiveTexture(GL_TEXTURE6)
        glBindTexture(GL_TEXTURE_2D, inscatterTexture.id)
        glUniform1i(inscatterHorizonTextureUniformWater.location, 7)
        glActiveTexture(GL_TEXTURE7)
        glBindTexture(GL_TEXTURE_2D, inscatterHorizonTexture.id)
        waterPlane.render()
    }

    private fun drawRegionPlane() {
        glUseProgram(regionPlaneProgram)
        glUniformMatrix4fv(mvpMatrixUniformRegion.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform1i(regionTextureUniform.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, regionTextureId?.id ?: -1)
        regionPlane.render()
    }

    private fun drawBiomePlane() {
        glUseProgram(biomePlaneProgram)
        glUniformMatrix4fv(mvpMatrixUniformBiome.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform1i(biomeTextureUniform.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, biomeTextureId?.id ?: -1)
        glUniform1i(splineTextureUniform.location, 1)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, splineTextureId?.id ?: -1)
        glUniform1i(biomeColorsTextureUniform.location, 2)
        glActiveTexture(GL_TEXTURE2)
        glBindTexture(GL_TEXTURE_2D, biomeColorsTextureId?.id ?: -1)
        biomePlane.render()
    }

    private fun drawImagePlane() {
        glUseProgram(imagePlaneProgram)
        glUniformMatrix4fv(mvpMatrixUniformImage.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform1i(imageTextureUniform.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, imageTextureId?.id ?: -1)
        imagePlane.render()
    }

    private fun viewportKeyboardHandler(): KeyboardHandler {
        return KeyboardHandler(
                onChar = {},
                onKey = { key, _, action, _ ->
                    if (action == GLFW.GLFW_PRESS) {
                        when (key) {
                            GLFW.GLFW_KEY_W -> {
                                pressedKeys.add(GLFW.GLFW_KEY_W)
                            }
                            GLFW.GLFW_KEY_A -> {
                                pressedKeys.add(GLFW.GLFW_KEY_A)
                            }
                            GLFW.GLFW_KEY_S -> {
                                pressedKeys.add(GLFW.GLFW_KEY_S)
                            }
                            GLFW.GLFW_KEY_D -> {
                                pressedKeys.add(GLFW.GLFW_KEY_D)
                            }
                            GLFW.GLFW_KEY_Q -> {
                                pressedKeys.add(GLFW.GLFW_KEY_Q)
                            }
                            GLFW.GLFW_KEY_E -> {
                                pressedKeys.add(GLFW.GLFW_KEY_E)
                            }
                            GLFW.GLFW_KEY_SPACE -> {
                                pressedKeys.add(GLFW.GLFW_KEY_SPACE)
                            }
                            GLFW.GLFW_KEY_LEFT_SHIFT -> {
                                pressedKeys.add(GLFW.GLFW_KEY_LEFT_SHIFT)
                            }
                        }
                    }
                    if (action == GLFW.GLFW_RELEASE) {
                        when (key) {
                            GLFW.GLFW_KEY_W -> {
                                pressedKeys.remove(GLFW.GLFW_KEY_W)
                            }
                            GLFW.GLFW_KEY_A -> {
                                pressedKeys.remove(GLFW.GLFW_KEY_A)
                            }
                            GLFW.GLFW_KEY_S -> {
                                pressedKeys.remove(GLFW.GLFW_KEY_S)
                            }
                            GLFW.GLFW_KEY_D -> {
                                pressedKeys.remove(GLFW.GLFW_KEY_D)
                            }
                            GLFW.GLFW_KEY_Q -> {
                                pressedKeys.remove(GLFW.GLFW_KEY_Q)
                            }
                            GLFW.GLFW_KEY_E -> {
                                pressedKeys.remove(GLFW.GLFW_KEY_E)
                            }
                            GLFW.GLFW_KEY_SPACE -> {
                                pressedKeys.remove(GLFW.GLFW_KEY_SPACE)
                            }
                            GLFW.GLFW_KEY_LEFT_SHIFT -> {
                                pressedKeys.remove(GLFW.GLFW_KEY_LEFT_SHIFT)
                            }
                        }
                    }
                })
    }

    interface PointPicker {

        fun onMouseDown(x: Float, y: Float)
    }

    interface BrushListener: PointPicker {

        fun onLine(x1: Float, y1: Float, x2: Float, y2: Float)

        fun onMouseUp(x1: Float, y1: Float, x2: Float, y2: Float)
    }

}
