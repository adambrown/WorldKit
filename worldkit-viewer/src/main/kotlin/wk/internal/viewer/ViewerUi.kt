package wk.internal.viewer

import wk.internal.application.MainThread.performMainThreadTasks
import wk.internal.ui.nvgproxy.*
import wk.internal.ui.style.*
import wk.internal.ui.widgets.*
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import wk.api.MapScale
import wk.internal.application.cancelRequestCount
import wk.internal.application.taskRunning
import wk.api.getFlowGraphSpec
import wk.internal.application.taskStartedAt
import wk.internal.ui.*
import wk.internal.ui.util.loadTexture2D
import wk.api.mRef
import wk.api.ref
import wk.internal.ui.util.windowState
import java.util.*
import kotlin.math.abs

const val DEFAULT_LIGHT_ELEVATION = 35.0f
const val DEFAULT_LIGHT_HEADING = 0.0f

val DEFAULT_MAP_DETAIL_SCALE = MapScale.MapScale8K

val defaultHeightScale = DEFAULT_MAP_DETAIL_SCALE.viewportScale
val maxHeightScale = MapScale.MapScale1K.viewportScale * 1.2f
val defaultHeightRangeMeters = DEFAULT_MAP_DETAIL_SCALE.heightRangeMeters

const val DEFAULT_COLOR_HEIGHT_SCALE = 4.0f

val heightScaleFunction = { scale: Float ->
    scale * maxHeightScale
}

val heightScaleFunctionInverse = { value: Float ->
    value / maxHeightScale
}

val projectFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)

val waterShaderParams = WaterShaderParams()
val fogShaderParams = FogShaderParams()
val heightMapScaleFactor = ref(defaultHeightScale)
val colorHeightScaleFactor = ref(DEFAULT_COLOR_HEIGHT_SCALE)
val gradientOffset = ref(0.0f)
val lightColor = Array(3) { ref(3.6f) }
val lightElevation = ref(DEFAULT_LIGHT_ELEVATION)
val lightHeading = ref(DEFAULT_LIGHT_HEADING)
val indirectIntensity = ref(1.8f)
val occlusionPower = ref(4.0f)
val baseColor = Array(3) { ref(1.0f) }
val materialParams = arrayOf(ref(0.0f), ref(0.75f), ref(0.5f))
val metallic = materialParams[0]
val roughness = materialParams[1]
val specularIntensity = materialParams[2]
val waterPlaneOn = ref(true)
val perspectiveOn = ref(true)
val heightColorsOn = ref(true)
val riversOn = ref(true)
val skyOn = ref(true)
val fogOn = ref(true)
val resetView = mRef(false)
val imageMode = ref(ViewportMode.HeightMap)
val disableCursor = ref(false)
val rootRef = ref(NO_BLOCK)
val heightRangeMeters = ref(defaultHeightRangeMeters)

val meshViewport = MeshViewport3D(
        resetView = resetView,
        perspectiveOn = perspectiveOn,
        waterPlaneOn = waterPlaneOn,
        heightColorsOn = heightColorsOn,
        riversOn = riversOn,
        skyOn = skyOn,
        fogOn = fogOn,
        heightMapScaleFactor = heightMapScaleFactor,
        heightRangeMeters = heightRangeMeters,
        colorHeightScaleFactor = colorHeightScaleFactor,
        gradientOffset = gradientOffset,
        lightColor = lightColor,
        lightElevation = lightElevation,
        lightHeading = lightHeading,
        indirectIntensity = indirectIntensity,
        occlusionPower = occlusionPower,
        baseColor = baseColor,
        metallic = metallic,
        roughness = roughness,
        specularIntensity = specularIntensity,
        waterParams = waterShaderParams,
        fogParams = fogShaderParams,
        imageMode = imageMode,
        disableCursor = disableCursor,
        uiRoot = rootRef)

var mainLayer = NO_BLOCK
var dialogLayer = NO_BLOCK
var mouseOverlayLayer = NO_BLOCK

var generatingMessageBlock = NO_BLOCK
var generatingPrimaryMessage = StaticTextReference()
var generatingSecondaryMessage = StaticTextReference()

val icon = ref(-1)

val onWindowResize = ref(arrayListOf<() -> Unit>())

fun linearClampedScaleFunction(min: Float, max: Float): (Float) -> Float {
    return { scale: Float ->
        ((scale * (max - min)) + min).coerceIn(min, max)
    }
}

fun linearClampedScaleFunctionInverse(min: Float, max: Float): (Float) -> Float {
    return { value: Float ->
        (abs((value - min) / (max - min))).coerceIn(0.0f, 1.0f)
    }
}

object ViewerUi {

    @Volatile
    private var uiIsShown: Boolean = false

    init {
        getFlowGraphSpec(0)
    }

    @JvmStatic
    fun mainViewer() {
        val uiThread = Thread { runUi() }
        uiThread.isDaemon = false
        uiThread.start()
        while (!uiIsShown) {
            try {
                Thread.sleep(200)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun runUi() {

        val titleText = DynamicTextReference("WorldKit - Viewer", 67, TEXT_STYLE_NORMAL)

        val uiLayout = layout { ui ->
            ui {
                disableCursor.addListener { old, new ->
                    if (old != new) {
                        if (new) {
                            disableCursor()
                        } else {
                            enableCursor()
                        }
                    }
                }
                background.set(45, 45, 48)

                textFont.value = createFont("/fonts/Roboto.ttf", "Roboto")
                glyphFont.value = createFont("/fonts/WorldKitUi.ttf", "Glyphs")

                val maxRestoreGlyph = MemoryUtil.memUTF8(if (isMaximized) GLYPH_RESTORE else GLYPH_MAXIMIZE, false)
                maximizeHandler = {
                    MemoryUtil.memUTF8(GLYPH_RESTORE, false, maxRestoreGlyph, 0)
                }
                restoreHandler = {
                    MemoryUtil.memUTF8(GLYPH_MAXIMIZE, false, maxRestoreGlyph, 0)
                }

                val (texId, texWidth, texHeight) = loadTexture2D(
                        GL11.GL_LINEAR_MIPMAP_NEAREST, GL11.GL_LINEAR, "/textures/icon/512.png", true, true, 0, 0.0f,
                        "/textures/icon/256.png",
                        "/textures/icon/128.png",
                        "/textures/icon/64.png",
                        "/textures/icon/32.png",
                        "/textures/icon/16.png")
                icon.value = createImage(texId, texWidth, texHeight, 0)

                setWindowIcon(
                        createGlfwImages(
                                "/textures/icon/16.png",
                                "/textures/icon/20.png",
                                "/textures/icon/24.png",
                                "/textures/icon/30.png",
                                "/textures/icon/32.png",
                                "/textures/icon/36.png",
                                "/textures/icon/40.png",
                                "/textures/icon/48.png",
                                "/textures/icon/50.png",
                                "/textures/icon/60.png",
                                "/textures/icon/64.png",
                                "/textures/icon/72.png",
                                "/textures/icon/80.png",
                                "/textures/icon/96.png",
                                "/textures/icon/128.png",
                                "/textures/icon/160.png",
                                "/textures/icon/192.png",
                                "/textures/icon/256.png",
                                "/textures/icon/320.png",
                                "/textures/icon/384.png",
                                "/textures/icon/512.png"
                        ))

                meshViewport.init()

                root {
                    mainLayer = block {
                        isFallThrough = true
                    }
                    dialogLayer = block {
                        isFallThrough = false
                        isMouseAware = true
                        isVisible = false
                        shape = FILL_GREY_OUT
                    }
                    mouseOverlayLayer = block {
                        isFallThrough = true
                    }
                }
                rootRef.value = root
                dialogLayer {
                    generatingMessageBlock = block {
                        hAlign = HorizontalAlignment.CENTER
                        vAlign = VerticalAlignment.MIDDLE
                        hSizing = Sizing.SHRINK
                        vSizing = Sizing.SHRINK
                        block {
                            layout = Layout.VERTICAL
                            hAlign = HorizontalAlignment.CENTER
                            vAlign = VerticalAlignment.TOP
                            hSizing = Sizing.SHRINK
                            vSizing = Sizing.SHRINK
                            text = generatingPrimaryMessage
                            isVisible = true
                        }
                        vSpacer(MEDIUM_SPACER_SIZE)
                        block {
                            layout = Layout.VERTICAL
                            hAlign = HorizontalAlignment.CENTER
                            vAlign = VerticalAlignment.TOP
                            hSizing = Sizing.SHRINK
                            vSizing = Sizing.SHRINK
                            text = generatingSecondaryMessage
                            isVisible = true
                        }
                        isVisible = false
                    }
                }
                mainLayer {
                    block {
                        vSizing = Sizing.STATIC
                        height = MEDIUM_ROW_HEIGHT
                        layout = Layout.VERTICAL
                        icon(icon.value, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT)
                        hSpacer(SMALL_SPACER_SIZE)
                        hSpacer(SMALL_SPACER_SIZE)
                        dragArea = dragArea(titleText.text)
                        hSpacer(SMALL_SPACER_SIZE)
                        button(glyph(GLYPH_MINIMIZE), WINDOW_DECORATE_BUTTON_STYLE) { minimizeWindow() }
                        button(glyph(maxRestoreGlyph), WINDOW_DECORATE_BUTTON_STYLE) { toggleMaximized() }
                        button(glyph(GLYPH_CLOSE), WINDOW_DECORATE_BUTTON_STYLE) {
                            closeWindow()
                        }
                    }
                    block {
                        vSizing = Sizing.GROW
                        layout = Layout.VERTICAL
                        hAlign = HorizontalAlignment.LEFT
                        block {
                            xOffset = SMALL_SPACER_SIZE
                            yOffset = SMALL_SPACER_SIZE
                            width = -2 * SMALL_SPACER_SIZE
                            height = -2 * SMALL_SPACER_SIZE
                            leftPanel(ui, dialogLayer)
                            viewportPanel(ui)
                        }
                    }
                    resizeAreaSouthEast = resizeArea(ShapeTriangle.Direction.SOUTH_EAST)
                    resizeAreaSouthWest = resizeArea(ShapeTriangle.Direction.SOUTH_WEST)
                }
                hotKeyHandler = HotKeyHandler { key, _, action, _ ->
                    if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_ESCAPE) {
                        if (taskRunning) {
                            cancelRequestCount = (cancelRequestCount + 1) and Int.MAX_VALUE
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

            }
        }
        var wasResizing = false
        var wasMinimized = true
        var waitingForShow = true
        var wasMaximizedWaitFrames = 0
        var wasShownWaitFrames = 0
        ui(uiLayout, windowState, afterShow = { uiIsShown = true }) {
            wasResizing = if (isResizing) {
                true
            } else {
                if (wasResizing) {
                    onWindowResize.value.forEach { it() }
                }
                false
            }
            if (wasMaximizedWaitFrames > 0) {
                wasMaximizedWaitFrames--
                if (wasMaximizedWaitFrames == 0) {
                    onWindowResize.value.forEach { it() }
                }
            }
            if (isMaximized) {
                if (wasMinimized) {
                    wasMaximizedWaitFrames = 2
                }
                wasMinimized = false
            } else {
                wasMinimized = true
            }
            if (wasShownWaitFrames > 0) {
                wasShownWaitFrames--
                if (wasShownWaitFrames == 0) {
                    onWindowResize.value.forEach { it() }
                }
            }
            if (waitingForShow && uiIsShown) {
                wasShownWaitFrames = 2
                waitingForShow = false
            }
            performMainThreadTasks()
        }
    }
}

fun <T> doWithProgressMessage(
        messageString: String,
        secondaryMessageString: String,
        ui: UserInterface,
        dialogLayer: Block,
        primaryMessageBlock: Block,
        primaryMessage: StaticTextReference,
        secondaryMessage: StaticTextReference,
        task: () -> T
): T {
    dialogLayer.isVisible = true
    primaryMessage.reference.value = text("$messageString... 0:00", TEXT_STYLE_LARGE_MESSAGE)
    secondaryMessage.reference.value = text(secondaryMessageString, TEXT_STYLE_SMALL_MESSAGE)
    primaryMessageBlock.isVisible = true
    val startTime = System.currentTimeMillis()
    val generationTimer = Timer(true)
    generationTimer.schedule(object : TimerTask() {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = (currentTime - startTime)
            val seconds = String.format("%02d", (elapsedTime / 1000).toInt() % 60)
            val minutes = (elapsedTime / (1000 * 60) % 60).toInt()
            primaryMessage.reference.value = text("$messageString... $minutes:$seconds", TEXT_STYLE_LARGE_MESSAGE)
            ui.layout.root.movedOrResized = true
        }
    }, 1000, 1000)
    taskStartedAt = cancelRequestCount
    taskRunning = true
    try {
        return task()
    } finally {
        generationTimer.cancel()
        primaryMessageBlock.isVisible = false
        dialogLayer.isVisible = false
        taskRunning = false
    }
}
