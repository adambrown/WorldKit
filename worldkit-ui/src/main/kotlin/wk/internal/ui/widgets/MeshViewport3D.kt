package wk.internal.ui.widgets

import wk.internal.ui.KeyboardHandler
import wk.internal.ui.nvgproxy.*
import wk.internal.ui.widgets.ViewportMode.*
import org.joml.*
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT
import org.lwjgl.opengl.GL20.*
import wk.api.*
import wk.internal.application.MainThread.doOnMainThread
import wk.api.Vec2
import wk.api.vec2
import wk.api.TextureId
import wk.internal.ui.util.*
import wk.internal.ui.widgets.TextureBuilder.TextureIdImpl
import java.io.File
import java.lang.Math.*
import java.util.*
import wk.api.color as c4f

const val VIEWPORT_HEIGHTMAP_SIZE = 8192
const val VIEWPORT_MESH_SCALE = 2600.0f
const val HALF_VIEWPORT_MESH_SCALE = VIEWPORT_MESH_SCALE * 0.5f

enum class ViewportMode {
    Index,
    Image,
    HeightMap
}

val INDEX_COLORS = arrayListOf(
        color(0, 0, 0),

        color(0, 137, 65), color(0, 111, 166), color(163, 0, 89), color(122, 73, 0),
        color(0, 0, 166), color(183, 151, 98), color(0, 77, 67), color(90, 0, 7),
        color(79, 198, 1), color(186, 9, 0), color(107, 121, 0), color(0, 194, 160),
        color(185, 3, 170), color(123, 79, 75), color(0, 132, 111), color(160, 121, 191),
        color(204, 7, 68), color(0, 72, 156), color(111, 0, 98), color(12, 189, 102),
        color(69, 109, 117), color(183, 123, 104), color(136, 85, 120), color(190, 196, 89),
        color(136, 111, 76), color(0, 166, 170), color(87, 83, 41), color(176, 91, 111),
        color(59, 151, 0), color(30, 110, 0), color(167, 117, 0), color(99, 103, 169),
        color(160, 88, 55), color(107, 0, 44), color(119, 38, 0), color(155, 151, 0),
        color(84, 158, 121), color(114, 65, 143), color(58, 36, 101), color(146, 35, 41),
        color(91, 69, 52), color(0, 137, 163), color(203, 126, 152), color(50, 78, 114),
        color(106, 58, 76), color(131, 171, 88), color(191, 86, 80), color(1, 44, 88),
        color(148, 58, 77), color(149, 86, 189), color(2, 82, 95), color(126, 100, 5),
        color(2, 104, 78), color(150, 43, 117), color(141, 133, 70), color(62, 137, 190),
        color(202, 131, 78), color(81, 138, 135), color(91, 17, 60), color(85, 129, 59),
        color(0, 0, 95), color(75, 129, 96), color(89, 115, 138), color(100, 49, 39)
)

class TerrainDisplayTextures(
        val mapScale: MapScale,
        val heightMapTexture: TextureId,
        val riverTexture: TextureId,
        val normalAndAoTexture: TextureId
)

class IndexDisplayTexture(val texture: TextureId)

class ImageDisplayTexture(val texture: TextureId)

class FogShaderParams(
        val color: Array<ObservableMutableReference<Float>> = arrayOf(
                ref(0.2f),
                ref(0.3f),
                ref(0.45f)),
        val atmosphericFogDensity: ObservableMutableReference<Float> = ref(
                0.007f),
        val exponentialFogDensity: ObservableMutableReference<Float> = ref(
                0.02f),
        val exponentialFogHeightFalloff: ObservableMutableReference<Float> = ref(
                0.2f),
        val fogHeightClampPower: ObservableMutableReference<Float> = ref(
                -20.0f),
        val fogOn: ObservableMutableReference<Float> = ref(
                1.0f),
        private val allParams: Array<ObservableMutableReference<Float>> = arrayOf(
                color[0],
                color[1],
                color[2],
                atmosphericFogDensity,
                exponentialFogDensity,
                exponentialFogHeightFalloff,
                fogHeightClampPower
        )
) {

    fun addListener(listener: (Float, Float) -> Unit) {
        allParams.forEach { it.addListener(listener) }
    }
}

class WaterShaderParams(
        val level: ObservableMutableReference<Float> = ref(0.0f),
        val depthPower: ObservableMutableReference<Float> = ref(0.11f),
        val color: Array<ObservableMutableReference<Float>> = arrayOf(ref(0.03f), ref(0.12f), ref(0.15f)),
        val shallowColor: Array<ObservableMutableReference<Float>> = arrayOf(ref(0.36f), ref(0.94f), ref(0.81f)),
        val materialParams: Array<ObservableMutableReference<Float>> = arrayOf(ref(0.2f), ref(0.075f), ref(0.5f)),
        val metallic: ObservableMutableReference<Float> = materialParams[0],
        val roughness: ObservableMutableReference<Float> = materialParams[1],
        val specularIntensity: ObservableMutableReference<Float> = materialParams[2],
        val normalOffsets: Array<ObservableMutableReference<Float>> = arrayOf(
                ref(0.3f),
                ref(0.065f),
                ref(0.008f),
                ref(0.0004f),
                ref(0.00004f),
                ref(0.00333f),
                ref(0.6f),
                ref(0.000233245f),
                ref(0.8f)),
        val fadeStarts: Array<ObservableMutableReference<Float>> = arrayOf(ref(10.0f), ref(60.0f), ref(200.0f)),
        val fadeEnds: Array<ObservableMutableReference<Float>> = arrayOf(ref(70.0f), ref(280.0f), ref(2000.0f)),
        val normalStrengths: Array<ObservableMutableReference<Float>> = arrayOf(ref(1.8f), ref(1.25f), ref(1.0f), ref(10.0f)),
        private val allParams: Array<ObservableMutableReference<Float>> = arrayOf(
                level,
                color[0],
                color[1],
                color[2],
                metallic,
                roughness,
                specularIntensity,
                normalOffsets[0],
                normalOffsets[1],
                normalOffsets[2],
                normalOffsets[3],
                normalOffsets[4],
                normalOffsets[5],
                normalOffsets[6],
                normalOffsets[7],
                normalOffsets[8],
                fadeStarts[0],
                fadeStarts[1],
                fadeStarts[2],
                fadeEnds[0],
                fadeEnds[1],
                fadeEnds[2],
                normalStrengths[0],
                normalStrengths[1],
                normalStrengths[2],
                normalStrengths[3]
        )
) {

    fun addListener(listener: (Float, Float) -> Unit) {
        allParams.forEach { it.addListener(listener) }
    }
}

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
        private val gradientOffset: Reference<Float>,
        private val lightColor: Array<ObservableMutableReference<Float>>,
        private val lightElevation: ObservableReference<Float>,
        private val lightHeading: ObservableReference<Float>,
        private val indirectIntensity: ObservableReference<Float>,
        private val occlusionPower: ObservableReference<Float>,
        private val baseColor: Array<ObservableMutableReference<Float>>,
        private val metallic: ObservableMutableReference<Float>,
        private val roughness: ObservableMutableReference<Float>,
        private val specularIntensity: ObservableMutableReference<Float>,
        private val waterParams: WaterShaderParams,
        private val fogParams: FogShaderParams,
        private val imageMode: Reference<ViewportMode>,
        private val disableCursor: MutableReference<Boolean>,
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
    private val cameraVelocity = Vector3f(0.0f)
    private val cameraTargetVelocity = Vector3f(0.0f)
    private var cameraInclination = defaultCameraInclination
    private var cameraHeading = defaultCameraHeading
    private var targetCameraInclination = defaultCameraInclination
    private var targetCameraHeading = defaultCameraHeading
    private val lookVector = Vector3f(defaultLookVector)

    private val defaultRotation = Quaternionf().rotateXYZ(0.0f, 0.0f, 0.0f)
    private val deltaRotation = Quaternionf()

    private val defaultViewMatrix = Matrix4f().setLookAt(Vector3f(0.0f, 0.0f, 0.5f), Vector3f(0.0f, 0.0f, -5.0f), Vector3f(0.0f, 1.0f, 0.0f))

    private val floatBuffer = BufferUtils.createFloatBuffer(16)

    private val minSpeed = 10.0f
    private val maxSpeed = 2000.0f
    private val defaultSpeed = 200.0f

    private var speed = defaultSpeed

    private var lastImageZoom = 1.0f
    private var imageZoom = 1.0f

    private val defaultTranslation = Vector3f(0.0f, 0.0f, -2073.58f)

    private val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
    private val mvMatrixUniform = ShaderUniform("modelViewMatrix")
    private val nMatrixUniform = ShaderUniform("normalMatrix")
    private val lightDirectionUniform = ShaderUniform("lightDirection")
    private val cameraPositionUniform = ShaderUniform("cameraPosition")
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
    private val occlusionPowerUniform = ShaderUniform("occlusionPower")
    private val heightScaleUniform = ShaderUniform("heightScale")
    private val heightScaleMetersUniform = ShaderUniform("heightScaleMeters")
    private val colorHeightScaleUniform = ShaderUniform("colorHeightScale")
    private val uvScaleUniform = ShaderUniform("uvScale")
    private val renderOptionsUniform = ShaderUniform("renderOptions")
    private val horizonBlendUniform = ShaderUniform("horizonBlend")
    private val lightMaxFogEffectUniform = ShaderUniform("lightMaxFogEffect")
    private val gradientOffsetUniform = ShaderUniform("gradientOffset")
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
    private val demGradientTextureUniform = ShaderUniform("demGradientTexture")


    private val mvpMatrixUniformWater = ShaderUniform("modelViewProjectionMatrix")
    private val mvMatrixUniformWater = ShaderUniform("modelViewMatrix")
    private val lightDirectionUniformWater = ShaderUniform("lightDirection")
    private val cameraPositionUniformWater = ShaderUniform("cameraPosition")
    private val baseColorUniformWater = ShaderUniform("baseColor")
    private val shallowColorUniformWater = ShaderUniform("shallowColor")
    private val lightColorUniformWater = ShaderUniform("lightColor")
    private val horizonBlendUniformWater = ShaderUniform("horizonBlend")
    private val lightMaxFogEffectUniformWater = ShaderUniform("lightMaxFogEffect")
    private val roughnessUniformWater = ShaderUniform("roughness")
    private val metallicUniformWater = ShaderUniform("metallic")
    private val specularIntensityUniformWater = ShaderUniform("specularIntensity")
    private val indirectIntensityUniformWater = ShaderUniform("indirectIntensity")
    private val heightScaleUniformWater = ShaderUniform("heightScale")
    private val waterLevelUniformWater = ShaderUniform("waterLevel")
    private val depthPowerUniformWater = ShaderUniform("depthPower")
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
    private val indexTextureUniform = ShaderUniform("indexTexture")
    private val colorLutTextureUniform = ShaderUniform("colorLutTexture")

    private val mvpMatrixUniformImage = ShaderUniform("modelViewProjectionMatrix")
    private val isRgbUniform = ShaderUniform("isRgb")
    private val imageTextureUniform = ShaderUniform("imageTexture")

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

    private val positionAttributeSky = ShaderAttribute("position")
    private val uvAttributeSky = ShaderAttribute("uv")

    private val textureLock = Object()
    private var hasHeightmap = false
    private var heightMapId: TextureId? = null
    private var normalAoMapId: TextureId? = null
    private var rivermapId: TextureId? = null
    private var heightMapResolution = 0

    private var hasIndex = false
    private var indexTextureId: TextureId? = null

    private var hasImage = false
    private var isImageRgb = true
    private var imageTextureId: TextureId? = null

    private val background = NPColor.create().set(30, 30, 30)

    private val lightDirection = Vector3f(0.0f, 0.0f, 1.0f)

    private var heightMapProgram: Int = 0
    private var waterPlaneProgram: Int = 0
    private var regionPlaneProgram: Int = 0
    private var imagePlaneProgram: Int = 0
    private var skyPlaneProgram: Int = 0

    private var lastScroll = 0.0f
    private var scroll = 0.0f
    private var deltaScroll = 0.0f

    private var deltaX: Float = 0.0f
    private var deltaY: Float = 0.0f
    private var rotationalVelocityX = 0.0f
    private var rotationalVelocityY = 0.0f
    private var targetRotationalVelocityX: Float = 0.0f
    private var targetRotationalVelocityY: Float = 0.0f

    private var lastMouseX = 0.0f
    private var lastMouseY = 0.0f

    private var mouseSpeed = 0.05f

    private var mouseX = 0
    private var mouseY = 0

    private var mouseDownX = 0
    private var mouseDownY = 0

    private var imageOffsetX = 0.0f
    private var imageOffsetY = 0.0f

    private var startImageOffsetX = 0.0f
    private var startImageOffsetY = 0.0f

    private var xPosition = 0
    private var width = 0
    private var yPosition = 0
    private var height = 0
    private var scale = 1.0f

    private var isFlyModeOn = false

    private lateinit var heightMap: HexGrid

    private lateinit var waterPlane: HexGrid

    private lateinit var imagePlane: ImagePlane

    private lateinit var regionPlane: ImagePlane

    private lateinit var skyPlane: ImagePlane

    private lateinit var irradianceMap: TextureId

    private lateinit var specularMap: TextureId

    private lateinit var skyMap: TextureId

    private lateinit var brdfMap: TextureId

    private lateinit var waterNormalTexture: TextureId

    private lateinit var lossTexture: TextureId

    private lateinit var inscatterTexture: TextureId

    private lateinit var inscatterHorizonTexture: TextureId

    private lateinit var demGradientTexture: TextureId

    private lateinit var indexColorsTextureId: TextureId

    val keyboardHandler: KeyboardHandler = viewportKeyboardHandler()

    fun init() {

        val refreshUiListener: (Any, Any) -> Unit = { old, new -> if (old != new) refreshUi() }

        lightColor[0].addListener(refreshUiListener)
        lightColor[1].addListener(refreshUiListener)
        lightColor[2].addListener(refreshUiListener)

        lightElevation.addListener(refreshUiListener)
        lightHeading.addListener(refreshUiListener)

        indirectIntensity.addListener(refreshUiListener)
        occlusionPower.addListener(refreshUiListener)

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

        val iblMaps = loadIblMaps("/textures/environment.ibl")

        irradianceMap = iblMaps.irradiance.id

        specularMap = iblMaps.specular.id

        skyMap = iblMaps.image.id

        brdfMap = loadBrdfMap("/textures/environment-brdf.tex").first

        waterNormalTexture = loadNormalMap("/textures/water-normal.png").first

        lossTexture = loadTextureF32("/textures/loss.tex").first

        inscatterTexture = loadTextureF32("/textures/inscatter.tex").first

        inscatterHorizonTexture = loadTextureF32("/textures/inscatter-horizon.tex").first

        demGradientTexture = TextureIdImpl(loadTexture2D(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR, "/textures/dem-gradient.png", generateMipMaps = true, clampToEdge = true).first)

        val heightMapVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/height-map.vert"))
        val heightMapFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/height-map.frag"))

        val waterVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/water-plane.vert"))
        val waterFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/water-plane.frag"))

        val regionVertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource("/shaders/terrain/index.vert"))
        val regionFragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource("/shaders/terrain/index.frag"))

        indexColorsTextureId = TextureIdImpl(loadColorsAsTexture(INDEX_COLORS, 256, 0))

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
                       occlusionPowerUniform,
                       heightScaleUniform,
                       heightScaleMetersUniform,
                       colorHeightScaleUniform,
                       uvScaleUniform,
                       renderOptionsUniform,
                       horizonBlendUniform,
                       lightMaxFogEffectUniform,
                       gradientOffsetUniform,
                       heightMapTextureUniform,
                       normalAoTextureUniform,
                       fogParamsUniform,
                       riverMapTextureUniform,
                       brdfMapUniform,
                       irradianceMapUniform,
                       specularMapUniform,
                       lossTextureUniform,
                       inscatterTextureUniform,
                       inscatterHorizonTextureUniform,
                       demGradientTextureUniform))

        waterPlaneProgram = createAndLinkProgram(
                listOf(waterVertexShader, waterFragmentShader),
                listOf(positionAttributeWater, uvAttributeWater),
                listOf(mvpMatrixUniformWater,
                       mvMatrixUniformWater,
                       lightDirectionUniformWater,
                       cameraPositionUniformWater,
                       baseColorUniformWater,
                       shallowColorUniformWater,
                       lightColorUniformWater,
                       horizonBlendUniformWater,
                       lightMaxFogEffectUniformWater,
                       roughnessUniformWater,
                       metallicUniformWater,
                       specularIntensityUniformWater,
                       indirectIntensityUniformWater,
                       heightScaleUniformWater,
                       waterLevelUniformWater,
                       depthPowerUniformWater,
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
                listOf(mvpMatrixUniformRegion, indexTextureUniform, colorLutTextureUniform))

        imagePlaneProgram = createAndLinkProgram(
                listOf(imagePlaneVertexShader, imagePlaneFragmentShader),
                listOf(positionAttributeImage, uvAttributeImage),
                listOf(mvpMatrixUniformImage, imageTextureUniform, isRgbUniform))

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

        heightMap = HexGrid(VIEWPORT_MESH_SCALE - 40.0f, 8192, positionAttribute, uvAttribute, true)

        waterPlane = HexGrid(100000.0f, 16, positionAttributeWater, uvAttributeWater, true)

        imagePlane = ImagePlane(VIEWPORT_MESH_SCALE, positionAttributeImage, uvAttributeImage)

        regionPlane = ImagePlane(VIEWPORT_MESH_SCALE, positionAttributeRegion, uvAttributeRegion)

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
            hasIndex = false
            indexTextureId = null
            hasImage = false
            imageTextureId = null
        }
        refreshUi()
    }

    fun loadIbl(newIbl: String) {
        doOnMainThread {
            synchronized(textureLock) {
                val iblMaps = loadIblMaps(File(newIbl))
                irradianceMap = iblMaps.irradiance.id
                specularMap = iblMaps.specular.id
                skyMap = iblMaps.image.id
            }
            refreshUi()
        }
    }

    fun loadDemGradient(newGradient: String) {
        doOnMainThread {
            synchronized(textureLock) {
                val demGradient = loadDemGradient(File(newGradient))
                demGradientTexture = demGradient
            }
            refreshUi()
        }
    }

    fun setHeightmap(newTexture: TerrainDisplayTextures, resolution: Int) {
        synchronized(textureLock) {
            hasHeightmap = true
            heightMapId = newTexture.heightMapTexture
            normalAoMapId = newTexture.normalAndAoTexture
            rivermapId = newTexture.riverTexture
            heightMapResolution = resolution
        }
        refreshUi()
    }

    fun setIndex(textureId: TextureId) {
        synchronized(textureLock) {
            hasIndex = true
            indexTextureId = textureId
        }
        refreshUi()
    }

    fun setImage(textureId: TextureId, isRgb: Boolean) {
        synchronized(textureLock) {
            hasImage = true
            isImageRgb = isRgb
            imageTextureId = textureId
        }
        refreshUi()
    }

    fun onTick(x: Int, y: Int) {
        mouseX = x
        mouseY = y
    }

    fun onMouseDown(button: Int, x: Int, y: Int) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            mouseDownX = x
            mouseDownY = y
            if (imageMode.value == Image || imageMode.value == Index) {
                startImageOffsetX = imageOffsetX
                startImageOffsetY = imageOffsetY
            }
            refreshUi()
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            lastMouseX = x.toFloat()
            lastMouseY = y.toFloat()
            isFlyModeOn = true
            refreshUi()
        }
    }

    fun onMouseDrag(button: Int, x: Int, y: Int) {
        mouseX = x
        mouseY = y
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (imageMode.value == Image || imageMode.value == Index) {
                imageOffsetX = startImageOffsetX + (mouseDownX - mouseX).toFloat()
                imageOffsetY = startImageOffsetY + (mouseY - mouseDownY).toFloat()
            }
        }
        refreshUi()
    }

    fun onMouseRelease(button: Int, x: Int, y: Int) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            mouseX = x
            mouseY = y
            if (imageMode.value == Image || imageMode.value == Index) {
                imageOffsetX = startImageOffsetX + (mouseDownX - mouseX).toFloat()
                imageOffsetY = startImageOffsetY + (mouseY - mouseDownY).toFloat()
            }
            refreshUi()
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            if (isFlyModeOn) {
                isFlyModeOn = false
                eliminateMovement = true
            }
            refreshUi()
        }
    }

    fun onScroll(scrollDelta: Double) {
        lastScroll = scroll
        deltaScroll = scrollDelta.toFloat()
        when (imageMode.value) {
            Image, Index -> {
                imageZoom = (imageZoom + deltaScroll * -imageZoom * 0.2f).coerceIn(0.005f, 1.015f)
            }
            HeightMap -> {
                val increment = (speed - minSpeed + 110.0f) / (maxSpeed - minSpeed + 110.0f)
                val desiredSpeed = speed + increment * deltaScroll * 200.0f
                speed = desiredSpeed.coerceIn(minSpeed, maxSpeed)
            }
        }
        refreshUi()
    }

    private fun checkKeysPressed() {
        if (isFlyModeOn) {
            if (!disableCursor.value) {
                disableCursor.value = true
                refreshUi()
            }
            val newTargetVelocity = Vector3f(0.0f)
            if (pressedKeys.contains(GLFW.GLFW_KEY_W)) {
                newTargetVelocity.add(lookVector.mul(speed, Vector3f()))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_S)) {
                newTargetVelocity.add(lookVector.mul(-speed, Vector3f()))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_A)) {
                val right = lookVector.cross(0.0f, 0.0f, 1.0f, Vector3f())
                newTargetVelocity.add(right.mul(-speed))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_D)) {
                val right = lookVector.cross(0.0f, 0.0f, 1.0f, Vector3f())
                newTargetVelocity.add(right.mul(speed))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_SPACE)) {
                newTargetVelocity.add(Vector3f(0.0f, 0.0f, speed))
                refreshUi()
            }
            if (pressedKeys.contains(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                newTargetVelocity.add(Vector3f(0.0f, 0.0f, -speed))
                refreshUi()
            }
            cameraTargetVelocity.set(newTargetVelocity)
        } else if (disableCursor.value) {
            disableCursor.value = false
            refreshUi()
        }
        if ((imageMode.value == Image || imageMode.value == Index) && pressedKeys.contains(GLFW.GLFW_KEY_F)) {
            lastImageZoom = 1.0f
            imageZoom = 1.0f
            imageOffsetX = 0.0f
            imageOffsetY = 0.0f
            startImageOffsetX = 0.0f
            startImageOffsetY = 0.0f
            mouseDownX = mouseX
            mouseDownY = mouseY
            refreshUi()
        }
    }

    fun clearKeysPressed() {
        pressedKeys.clear()
    }

    private var lastFrame = -1L
    private var deltaTime = 0.0f

    fun onDrawFrame(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        val currentTime = System.nanoTime()
        if (lastFrame == -1L) {
            lastFrame = currentTime
            return
        }
        deltaTime = (currentTime - lastFrame) / 1000000000.0f
        lastFrame = currentTime

        when(imageMode.value) {
            Index -> onDrawFrameInternalIndex(xPosition, yPosition, width, height, rootHeight, scale)
            Image -> onDrawFrameInternalImage(xPosition, yPosition, width, height, rootHeight, scale)
            else -> onDrawFrameInternalHeightMap(xPosition, yPosition, width, height, rootHeight, scale)
        }
    }

    private fun onDrawFrameInternalIndex(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        if (width < 1 || height < 1) {
            return
        }
        synchronized(textureLock) {
            updateViewportArea(xPosition, yPosition, width, height, scale)
            checkKeysPressed()
            if (hasIndex) {

                val flippedY = rootHeight - (yPosition + height)

                val premulRatio = width / height.toFloat()
                val orthoZoom = HALF_VIEWPORT_MESH_SCALE * imageZoom
                val texelScale = ((VIEWPORT_MESH_SCALE * scale) / height) * imageZoom
                var imageOffsetX = imageOffsetX
                var imageOffsetY = imageOffsetY
                var startImageOffsetX = startImageOffsetX
                var startImageOffsetY = startImageOffsetY
                if (imageZoom != lastImageZoom) {
                    val mouseX = mouseX
                    val mouseY = mouseY
                    val (dx, dy) = calcZoomDeltas(mouseX, mouseY, lastImageZoom, imageZoom)
                    imageOffsetX += dx
                    imageOffsetY -= dy
                    startImageOffsetX += dx
                    startImageOffsetY -= dy
                    this.imageOffsetX = imageOffsetX
                    this.imageOffsetY = imageOffsetY
                    this.startImageOffsetX = startImageOffsetX
                    this.startImageOffsetY = startImageOffsetY
                    this.lastImageZoom = imageZoom
                }
                val xOffset = imageOffsetX * texelScale
                val yOffset = imageOffsetY * texelScale
                projectionMatrix.setOrtho(premulRatio * -orthoZoom + xOffset, premulRatio * orthoZoom + xOffset, -orthoZoom + yOffset, orthoZoom + yOffset, 6.0f, 6000.0f)

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

                drawIndexPlane()

                glDisable(GL_SCISSOR_TEST)
            }
        }
    }

    private fun onDrawFrameInternalImage(xPosition: Int, yPosition: Int, width: Int, height: Int, rootHeight: Int, scale: Float) {
        if (width < 1 || height < 1) {
            return
        }
        synchronized(textureLock) {
            updateViewportArea(xPosition, yPosition, width, height, scale)
            checkKeysPressed()
            if (hasImage) {

                val flippedY = rootHeight - (yPosition + height)

                val imageZoom = imageZoom
                val lastImageZoom = lastImageZoom
                val premulRatio = width / height.toFloat()
                val orthoZoom = HALF_VIEWPORT_MESH_SCALE * imageZoom
                val texelScale = ((VIEWPORT_MESH_SCALE * scale) / height) * imageZoom
                var imageOffsetX = imageOffsetX
                var imageOffsetY = imageOffsetY
                var startImageOffsetX = startImageOffsetX
                var startImageOffsetY = startImageOffsetY
                if (imageZoom != lastImageZoom) {
                    val mouseX = mouseX
                    val mouseY = mouseY
                    val (dx, dy) = calcZoomDeltas(mouseX, mouseY, lastImageZoom, imageZoom)
                    imageOffsetX += dx
                    imageOffsetY -= dy
                    startImageOffsetX += dx
                    startImageOffsetY -= dy
                    this.imageOffsetX = imageOffsetX
                    this.imageOffsetY = imageOffsetY
                    this.startImageOffsetX = startImageOffsetX
                    this.startImageOffsetY = startImageOffsetY
                    this.lastImageZoom = imageZoom
                }
                val xOffset = imageOffsetX * texelScale
                val yOffset = imageOffsetY * texelScale
                projectionMatrix.setOrtho(premulRatio * -orthoZoom + xOffset, premulRatio * orthoZoom + xOffset, -orthoZoom + yOffset, orthoZoom + yOffset, 6.0f, 6000.0f)

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
            updateViewportArea(xPosition, yPosition, width, height, scale)
            checkKeysPressed()
            if (hasHeightmap) {
                val eliminateMovementNext = eliminateMovement && !disableCursor.value

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
                }

                cameraVelocity.set(interpolate(cameraVelocity, cameraTargetVelocity, deltaTime, 4.0f))
                val cameraDelta = cameraVelocity.mul(deltaTime, Vector3f())

                if (eliminateMovement) {
                    cameraTargetVelocity.set(0.0f)
                    cameraVelocity.set(0.0f)

                    targetRotationalVelocityX = 0.0f
                    targetRotationalVelocityY = 0.0f
                    rotationalVelocityX = 0.0f
                    rotationalVelocityY = 0.0f

                    deltaX = 0.0f
                    deltaY = 0.0f

                    lastMouseX = mouseX.toFloat()
                    lastMouseY = mouseY.toFloat()

                    if (eliminateMovementNext) {
                        eliminateMovement = false
                    }
                } else {
                    targetRotationalVelocityX = (mouseX - lastMouseX) / deltaTime
                    targetRotationalVelocityY = (mouseY - lastMouseY) / deltaTime

                    rotationalVelocityX = interpolate(rotationalVelocityX, targetRotationalVelocityX, deltaTime, 7.0f)
                    rotationalVelocityY = interpolate(rotationalVelocityY, targetRotationalVelocityY, deltaTime, 7.0f)

                    deltaX = rotationalVelocityX * deltaTime
                    deltaY = rotationalVelocityY * deltaTime

                    lastMouseX = mouseX.toFloat()
                    lastMouseY = mouseY.toFloat()
                }

                cameraPosition.add(cameraDelta)

                val premulRatio = width / height.toFloat()
                if (perspectiveOn) {
                    val fov = 1.5708f
                    val aspect = width / height.toFloat()
                    projectionMatrix.set(fov / aspect, 0.0f, 0.0f, 0.0f, 0.0f, fov, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, -1.0f, 0.0f)
                } else {
                    val orthoZoom = HALF_VIEWPORT_MESH_SCALE
                    projectionMatrix.setOrtho(premulRatio * -orthoZoom, premulRatio * orthoZoom, -orthoZoom, orthoZoom, 6.0f, 6000.0f)
                }

                if (isFlyModeOn) {
                    targetCameraInclination = (cameraInclination + (deltaY * mouseSpeed)).coerceIn(-1.48353f, 1.48353f)
                    targetCameraHeading = cameraHeading + (-deltaX * mouseSpeed)
                    cameraInclination = interpolate(cameraInclination, targetCameraInclination, deltaTime, 3.5f)
                    cameraHeading = ((interpolate(cameraHeading, targetCameraHeading, deltaTime, 3.5f) + 6.28318530718f) % 6.28318530718f)
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

    private fun updateViewportArea(xPosition: Int, yPosition: Int, width: Int, height: Int, scale: Float) {
        this.xPosition = xPosition
        this.yPosition = yPosition
        this.width = width
        this.height = height
        this.scale = scale
    }

    private fun calcZoomDeltas(mouseX: Int, mouseY: Int, lastImageZoom: Float, newImageZoom: Float): Vec2 {
        val scale = 1.0f / scale
        val halfScale = 0.5f * scale
        val x = (mouseX - xPosition * scale - width * halfScale + imageOffsetX)
        val y = (mouseY - yPosition * scale - height * halfScale - imageOffsetY)
        val zoomRatio = lastImageZoom / newImageZoom
        return vec2(x * zoomRatio - x, y * zoomRatio - y)
    }

    private fun interpolate(current: Vector3f, target: Vector3f, deltaTime: Float, speed: Float): Vector3f {
        if (speed <= 0.0f) {
            return target
        }
        val dist = target.sub(current, Vector3f())
        if (dist.lengthSquared() < 1.0e-4f) {
            return target
        }
        val delta = dist.mul((deltaTime * speed).coerceIn(0.0f, 1.0f), Vector3f())
        return current.add(delta, Vector3f())
    }

    private fun interpolate(current: Float, target: Float, deltaTime: Float, speed: Float): Float {
        if (speed <= 0.0f) {
            return target
        }
        val dist = target - current
        if( dist * dist < 1.0e-8f) {
            return target
        }
        val delta = dist * (deltaTime * speed).coerceIn(0.0f, 1.0f)
        return current + delta
    }

    private fun filterLight(): ColorFC {
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
        return c4f(r, g, b, p)
    }

    private fun lightFilterValue(lower: FloatArray, upper: FloatArray, index: Int, intensity: Float, alpha: Float, iAlpha: Float): Float = lightColor[index].value * intensity * (lower[index] * iAlpha + upper[index] * alpha)

    private fun lightPowerValue(lower: FloatArray, upper: FloatArray, index: Int, alpha: Float, iAlpha: Float): Float = (lower[index] * iAlpha + upper[index] * alpha)

    private fun drawSkyPlane(lightColor: ColorFC) {
        glUseProgram(skyPlaneProgram)
        glUniformMatrix4fv(pMatrixUniformSky.location, false, projectionMatrix.get(0, floatBuffer))
        glUniformMatrix4fv(mvMatrixUniformSky.location, false, mvMatrix.get(0, floatBuffer))
        glUniform3f(lightDirectionUniformSky.location, lightDirection.x, lightDirection.y, lightDirection.z)
        glUniform4f(lightColorUniformSky.location, lightColor.first, lightColor.second, lightColor.third, 1.0f)
        glUniform3f(cameraPositionUniformSky.location, cameraPosition.x, cameraPosition.y, cameraPosition.z)
        glUniform1f(horizonBlendUniformSky.location, 1.0f - (toRadians(lightElevation.value.toDouble()).toFloat() * 5.0f).coerceIn(0.0f, 1.0f))
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

    private fun drawHeightMap(renderOptions: Int, lightColor: ColorFC) {
        glUseProgram(heightMapProgram)
        glUniformMatrix4fv(mvMatrixUniform.location, false, mvMatrix.get(0, floatBuffer))
        glUniformMatrix3fv(nMatrixUniform.location, false, normalMatrix.get(0, floatBuffer))
        glUniformMatrix4fv(mvpMatrixUniform.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform3f(lightDirectionUniform.location, lightDirection.x, lightDirection.y, lightDirection.z)
        glUniform3f(cameraPositionUniform.location, cameraPosition.x, cameraPosition.y, cameraPosition.z)
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
        glUniform1f(occlusionPowerUniform.location, occlusionPower.value)
        glUniform1f(heightScaleUniform.location, heightMapScaleFactor.value)
        glUniform1f(heightScaleMetersUniform.location, heightRangeMeters.value)
        glUniform1f(colorHeightScaleUniform.location, colorHeightScaleFactor.value)
        glUniform1f(uvScaleUniform.location, (1.0f / ((heightMapScaleFactor.value / heightRangeMeters.value) / VIEWPORT_MESH_SCALE)) / heightMapResolution)
        glUniform1i(renderOptionsUniform.location, renderOptions)
        glUniform1f(horizonBlendUniform.location, 1.0f - (toRadians(lightElevation.value.toDouble()).toFloat() * 5.0f).coerceIn(0.0f, 1.0f))
        glUniform1f(lightMaxFogEffectUniform.location, lightColor.fourth)
        glUniform1f(gradientOffsetUniform.location, gradientOffset.value)
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
        glUniform1i(demGradientTextureUniform.location, 9)
        glActiveTexture(GL_TEXTURE9)
        glBindTexture(GL_TEXTURE_2D, demGradientTexture.id)

        heightMap.render()
    }

    private fun drawWaterPlane(lightColor: ColorFC) {
        glUseProgram(waterPlaneProgram)
        glUniformMatrix4fv(mvMatrixUniformWater.location, false, mvMatrix.get(0, floatBuffer))
        glUniformMatrix4fv(mvpMatrixUniformWater.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform3f(lightDirectionUniformWater.location, lightDirection.x, lightDirection.y, lightDirection.z)
        glUniform3f(cameraPositionUniformWater.location, cameraPosition.x, cameraPosition.y, cameraPosition.z)
        glUniform4f(baseColorUniformWater.location, waterParams.color[0].value, waterParams.color[1].value, waterParams.color[2].value, 1.0f)
        glUniform4f(shallowColorUniformWater.location, waterParams.shallowColor[0].value, waterParams.shallowColor[1].value, waterParams.shallowColor[2].value, 1.0f)
        glUniform4f(lightColorUniformWater.location, lightColor.first, lightColor.second, lightColor.third, 1.0f)
        glUniform1f(horizonBlendUniformWater.location, 1.0f - (toRadians(lightElevation.value.toDouble()).toFloat() * 5.0f).coerceIn(0.0f, 1.0f))
        glUniform1f(lightMaxFogEffectUniformWater.location, lightColor.fourth)
        glUniform1f(roughnessUniformWater.location, waterParams.roughness.value)
        glUniform1f(metallicUniformWater.location, waterParams.metallic.value)
        glUniform1f(specularIntensityUniformWater.location, waterParams.specularIntensity.value)
        glUniform1f(indirectIntensityUniformWater.location, indirectIntensity.value * 2.2f)
        glUniform1f(heightScaleUniformWater.location, heightMapScaleFactor.value)
        glUniform1f(waterLevelUniformWater.location, waterParams.level.value)
        glUniform1f(depthPowerUniformWater.location, waterParams.depthPower.value)
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

    private fun drawIndexPlane() {
        glUseProgram(regionPlaneProgram)
        glUniformMatrix4fv(mvpMatrixUniformRegion.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform1i(indexTextureUniform.location, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, indexTextureId?.id ?: -1)
        glUniform1i(colorLutTextureUniform.location, 1)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, indexColorsTextureId.id)
        regionPlane.render()
    }

    private fun drawImagePlane() {
        glUseProgram(imagePlaneProgram)
        glUniformMatrix4fv(mvpMatrixUniformImage.location, false, mvpMatrix.get(0, floatBuffer))
        glUniform1i(imageTextureUniform.location, 0)
        glUniform1i(isRgbUniform.location, if (isImageRgb) 1 else 0)
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
                            GLFW.GLFW_KEY_W,
                            GLFW.GLFW_KEY_A,
                            GLFW.GLFW_KEY_S,
                            GLFW.GLFW_KEY_D,
                            GLFW.GLFW_KEY_F,
                            GLFW.GLFW_KEY_Q,
                            GLFW.GLFW_KEY_E,
                            GLFW.GLFW_KEY_SPACE,
                            GLFW.GLFW_KEY_LEFT_SHIFT,
                            -> {
                                pressedKeys.add(key)
                                refreshUi()
                            }
                        }
                    }
                    if (action == GLFW.GLFW_RELEASE) {
                        when (key) {
                            GLFW.GLFW_KEY_W,
                            GLFW.GLFW_KEY_A,
                            GLFW.GLFW_KEY_S,
                            GLFW.GLFW_KEY_D,
                            GLFW.GLFW_KEY_F,
                            GLFW.GLFW_KEY_Q,
                            GLFW.GLFW_KEY_E,
                            GLFW.GLFW_KEY_SPACE,
                            GLFW.GLFW_KEY_LEFT_SHIFT,
                            -> {
                                pressedKeys.remove(key)
                                refreshUi()
                            }
                        }
                    }
                })
    }
}
