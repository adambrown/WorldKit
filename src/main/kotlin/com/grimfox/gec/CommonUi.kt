package com.grimfox.gec

import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.HistoryQueue
import com.grimfox.gec.ui.bInt
import com.grimfox.gec.ui.color
import com.grimfox.gec.ui.gInt
import com.grimfox.gec.ui.rInt
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.ParameterSet
import com.grimfox.gec.util.BuildContinent.RegionSplines
import org.lwjgl.nanovg.NVGColor
import java.util.*

val DEBUG_BUILD = false

val REGION_COLORS = arrayListOf(
        color(0.0f, 0.0f, 0.0f),
        color(0.28627452f, 0.93333334f, 0.28627452f),
        color(1.0f, 0.6392157f, 0.9411765f),
        color(1.0f, 1.0f, 0.5294118f),
        color(0.627451f, 0.24313726f, 0.8901961f),
        color(0.8392157f, 0.53333336f, 0.21960784f),
        color(0.41568628f, 0.41568628f, 1.0f),
        color(0.9647059f, 0.34509805f, 0.34509805f),
        color(0.1882353f, 0.8039216f, 0.80784315f),
        color(0.17254902f, 0.57254905f, 0.17254902f),
        color(0.7607843f, 0.16862746f, 0.65882355f),
        color(0.69803923f, 0.7019608f, 0.1764706f),
        color(0.38431373f, 0.18039216f, 0.5372549f),
        color(0.49411765f, 0.34117648f, 0.19215687f),
        color(0.16470589f, 0.16470589f, 0.6627451f),
        color(0.61960787f, 0.16078432f, 0.16078432f),
        color(0.20392157f, 0.45490196f, 0.45490196f)
) as List<NVGColor>

private fun colorToInt(id: Int): Int {
    val color = REGION_COLORS[id]
    return ((color.rInt and 0xFF) shl 16) + ((color.gInt and 0xFF) shl 8) + (color.bInt and 0xFF)
}

val REGION_COLOR_INTS = Array(REGION_COLORS.size) { i ->
    colorToInt(i)
}.toList()


val RANDOM = Random()
val DEFAULT_HEIGHT_SCALE = 50.0f
val MAX_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 10
val MIN_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 0
val HEIGHT_SCALE_CONST = DEFAULT_HEIGHT_SCALE * 0.968746f
val HEIGHT_SCALE_MULTIPLIER = DEFAULT_HEIGHT_SCALE * 32.00223

val heightScaleFunction = { scale: Float ->
    Math.min(MAX_HEIGHT_SCALE, Math.max(MIN_HEIGHT_SCALE, if (scale <= 0.5f) {
        scale * (2 * DEFAULT_HEIGHT_SCALE)
    } else {
        (HEIGHT_SCALE_CONST + (HEIGHT_SCALE_MULTIPLIER * Math.pow(scale - 0.46874918, 2.0))).toFloat()
    }))
}

val heightScaleFunctionInverse = { value: Float ->
    Math.min(1.0f, Math.max(0.0f, if (value <= DEFAULT_HEIGHT_SCALE) {
        value / (2 * DEFAULT_HEIGHT_SCALE)
    } else {
        (Math.sqrt((value - HEIGHT_SCALE_CONST) / HEIGHT_SCALE_MULTIPLIER) + 0.46874918).toFloat()
    }))
}

class HistoryItem(val parameters: ParameterSet, val graphSeed: Long, val mask: ByteArrayMatrix) {

    fun copy(): HistoryItem {
        return HistoryItem(parameters.copy(), graphSeed, ByteArrayMatrix(mask.width, mask.array.copyOf()))
    }
}

val debugWidgets: MutableList<Block> = ArrayList()
val heightMapScaleFactor = ref(DEFAULT_HEIGHT_SCALE)
val waterPlaneOn = ref(true)
val perspectiveOn = ref(true)
val rotateAroundCamera = ref(false)
val resetView = mRef(false)
val imageMode = ref(3)
val disableCursor = ref(false)
val hideCursor = ref(false)
val brushOn = ref(false)
val brushActive = ref(false)
val brushSize = ref(10.0f)
val regionEditBrushSize = ref(0.0859375f)
val drawSplineBrushSize = ref(0.006f)
val biomeEditBrushSize = ref(0.0859375f)
val currentEditBrushSize = ref(regionEditBrushSize)
val brushListener = ref<MeshViewport3D.BrushListener?>(null)
val pickerOn = ref(false)
val pointPicker = ref<MeshViewport3D.PointPicker?>(null)
val rememberWindowState = ref(preferences.rememberWindowState)
val projectDir = DynamicTextReference(preferences.projectDir.canonicalPath, 1024, TEXT_STYLE_NORMAL)
val tempDir = DynamicTextReference(preferences.tempDir.canonicalPath, 1024, TEXT_STYLE_NORMAL)
val historyRegionsBackQueue = HistoryQueue<HistoryItem>(1000)
val historyRegionsCurrent = ref<HistoryItem?>(null)
val historyRegionsForwardQueue = HistoryQueue<HistoryItem>(1000)
val historySplinesBackQueue = HistoryQueue<RegionSplines>(1000)
val historySplinesCurrent = ref<RegionSplines?>(null)
val historySplinesForwardQueue = HistoryQueue<RegionSplines>(1000)
val historyBiomesBackQueue = HistoryQueue<HistoryItem>(1000)
val historyBiomesCurrent = ref<HistoryItem?>(null)
val historyBiomesForwardQueue = HistoryQueue<HistoryItem>(1000)

val meshViewport = MeshViewport3D(resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor, imageMode, disableCursor, hideCursor, brushOn, brushActive, brushListener, brushSize, currentEditBrushSize, pickerOn, pointPicker)

var mainLayer = NO_BLOCK
var panelLayer = NO_BLOCK
var menuLayer = NO_BLOCK
var dropdownLayer = NO_BLOCK
var dialogLayer = NO_BLOCK
var mouseOverlayLayer = NO_BLOCK
var brushShapeOuter = NO_BLOCK
var brushShapeInner = NO_BLOCK

var preferencesPanel = NO_BLOCK
var exportPanel = NO_BLOCK

var onWindowResize: () -> Unit = {}

class CurrentState(var parameters: ParameterSet? = null,
                   var regionGraph: Graph? = null,
                   var regionMask: ByteArrayMatrix? = null,
                   var regionSplines: RegionSplines? = null,
                   var biomeGraph: Graph? = null,
                   var biomeMask: ByteArrayMatrix? = null,
                   var biomes: List<Biomes.Biome>? = null,
                   var heightMapTexture: TextureBuilder.TextureId? = null,
                   var riverMapTexture: TextureBuilder.TextureId? = null,
                   var edgeDetailScale: Int? = null,
                   var mapDetailScale: Int? = null)

var currentState = CurrentState()

fun linearClampedScaleFunction(range: IntRange): (Float) -> Int {
    return { scale: Float ->
        clamp(Math.round(scale * (range.last - range.first)) + range.first, range.first, range.last)
    }
}

fun linearClampedScaleFunctionInverse(range: IntRange): (Int) -> Float {
    return { value: Int ->
        clamp(Math.abs((value - range.first).toFloat() / (range.last - range.first)), 0.0f, 1.0f)
    }
}

fun linearClampedScaleFunction(min: Float, max: Float): (Float) -> Float {
    return { scale: Float ->
        clamp((scale * (max - min)) + min, min, max)
    }
}

fun linearClampedScaleFunctionInverse(min: Float, max: Float): (Float) -> Float {
    return { value: Float ->
        clamp(Math.abs((value - min) / (max - min)), 0.0f, 1.0f)
    }
}

val overwriteWarningDynamic = dynamicParagraph("", 300)
val overwriteWarningText = overwriteWarningDynamic.text
val overwriteWarningReference = overwriteWarningDynamic.reference

val errorMessageDynamic = dynamicParagraph("", 600)
val errorMessageText = errorMessageDynamic.text
val errorMessageReference = errorMessageDynamic.reference

var overwriteWarningDialog = NO_BLOCK
var errorMessageDialog = NO_BLOCK
val noop = {}
val dialogCallback = mRef(noop)

fun newProject(overwriteWarningReference: MonitoredReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, noop: () -> Unit) {
    if (currentProject.value != null) {
        dialogLayer.isVisible = true
        overwriteWarningReference.value = "Do you want to save the current project before creating a new one?"
        overwriteWarningDialog.isVisible = true
        dialogCallback.value = {
            clearHistories()
            currentProject.value = Project()
            currentState = CurrentState()
            meshViewport.reset()
            imageMode.value = 3
            dialogCallback.value = noop
        }
    } else {
        clearHistories()
        currentProject.value = Project()
        currentState = CurrentState()
        meshViewport.reset()
        imageMode.value = 3
    }
}

private fun clearHistories() {
    historyRegionsBackQueue.clear()
    historyRegionsCurrent.value = null
    historyRegionsForwardQueue.clear()
    historySplinesBackQueue.clear()
    historySplinesCurrent.value = null
    historySplinesForwardQueue.clear()
    historyBiomesBackQueue.clear()
    historyBiomesCurrent.value = null
    historyBiomesForwardQueue.clear()
}
