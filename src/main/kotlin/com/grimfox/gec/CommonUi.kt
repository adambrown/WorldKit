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
import com.grimfox.gec.util.Biomes.Biome
import com.grimfox.gec.util.BuildContinent.BiomeParameters
import com.grimfox.gec.util.BuildContinent.RegionParameters
import com.grimfox.gec.util.BuildContinent.RegionSplines
import org.lwjgl.nanovg.NVGColor
import java.util.*

val EXPERIMENTAL_BUILD = false

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

val BIOME_NAMES = linkedMapOf(
        "Mountains" to 0,
        "Coastal mountains" to 1,
        "Foothills" to 2,
        "Rolling hills" to 3,
        "Plateaus" to 4,
        "Plains" to 5
)

val BIOME_ORDINALS = linkedMapOf(*BIOME_NAMES.map { it.value  to it.key }.toTypedArray())

fun ordinalToBiome(it: Int): Biome {
    return when (it) {
        0 -> Biomes.MOUNTAINS_BIOME
        1 -> Biomes.COASTAL_MOUNTAINS_BIOME
        2 -> Biomes.FOOTHILLS_BIOME
        3 -> Biomes.ROLLING_HILLS_BIOME
        4 -> Biomes.PLATEAU_BIOME
        5 -> Biomes.PLAINS_BIOME
        else -> Biomes.MOUNTAINS_BIOME
    }
}

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

class RegionsHistoryItem(val parameters: RegionParameters, val graphSeed: Long, val mask: ByteArrayMatrix) {

    fun copy(): RegionsHistoryItem {
        return RegionsHistoryItem(parameters.copy(), graphSeed, ByteArrayMatrix(mask.width, mask.array.copyOf()))
    }
}

class BiomesHistoryItem(val parameters: BiomeParameters, val graphSeed: Long, val mask: ByteArrayMatrix) {

    fun copy(): BiomesHistoryItem {
        return BiomesHistoryItem(parameters.copy(), graphSeed, ByteArrayMatrix(mask.width, mask.array.copyOf()))
    }
}

val currentState: CurrentState get() = currentProject.value?.currentState?.value ?: CurrentState()

val experimentalWidgets: MutableList<Block> = ArrayList()
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
val historyRegionsBackQueue get() = currentProject.value?.historyRegionsBackQueue ?: HistoryQueue(0)
val historyRegionsCurrent: MonitoredReference<RegionsHistoryItem?> get() = currentProject.value?.historyRegionsCurrent ?: ref<RegionsHistoryItem?>(null)
val historyRegionsForwardQueue get() = currentProject.value?.historyRegionsForwardQueue ?: HistoryQueue(0)
val historySplinesBackQueue get() = currentProject.value?.historySplinesBackQueue ?: HistoryQueue(0)
val historySplinesCurrent: MonitoredReference<RegionSplines?> get() = currentProject.value?.historySplinesCurrent ?: ref<RegionSplines?>(null)
val historySplinesForwardQueue get() = currentProject.value?.historySplinesForwardQueue ?: HistoryQueue(0)
val historyBiomesBackQueue get() = currentProject.value?.historyBiomesBackQueue ?: HistoryQueue(0)
val historyBiomesCurrent: MonitoredReference<BiomesHistoryItem?> get() = currentProject.value?.historyBiomesCurrent ?: ref<BiomesHistoryItem?>(null)
val historyBiomesForwardQueue get() = currentProject.value?.historyBiomesForwardQueue ?: HistoryQueue(0)
val displayMode = ref(DisplayMode.MAP)
val defaultToMap = ref(true)

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

val generationLock = DisableSetLock()
val editToggleSet = ToggleSet(executor)

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
    if (currentProject.value != null && currentProjectHasModifications.value) {
        dialogLayer.isVisible = true
        overwriteWarningReference.value = "Do you want to save the current project before creating a new one?"
        overwriteWarningDialog.isVisible = true
        dialogCallback.value = {
            currentProject.value = Project(isModifiedSinceSave = ref(true))
            meshViewport.reset()
            imageMode.value = 3
            dialogCallback.value = noop
        }
    } else {
        currentProject.value = Project(isModifiedSinceSave = ref(true))
        meshViewport.reset()
        imageMode.value = 3
    }
}

fun updateRegionsHistory(parameters: RegionParameters, graph: Graph, regionMask: ByteArrayMatrix) {
    val historyLast = historyRegionsCurrent.value
    if (historyLast != null) {
        if ((historyRegionsBackQueue.size == 0 || historyRegionsBackQueue.peek() != historyLast) && (parameters != historyLast.parameters || historyLast.graphSeed != graph.seed || !Arrays.equals(historyLast.mask.array, regionMask.array))) {
            historyRegionsBackQueue.push(historyLast.copy())
        }
    }
    historyRegionsForwardQueue.clear()
    historyRegionsCurrent.value = RegionsHistoryItem(parameters.copy(), graph.seed, ByteArrayMatrix(regionMask.width, regionMask.array.copyOf()))
}

fun updateSplinesHistory(splines: BuildContinent.RegionSplines) {
    val historyLast = historySplinesCurrent.value
    if (historyLast != null) {
        if ((historySplinesBackQueue.size == 0 || historySplinesBackQueue.peek() != historyLast)) {
            historySplinesBackQueue.push(historyLast.copy())
        }
    }
    historySplinesForwardQueue.clear()
    historySplinesCurrent.value = splines
}

fun updateBiomesHistory(parameters: BiomeParameters, graph: Graph, biomeMask: ByteArrayMatrix) {
    val historyLast = historyBiomesCurrent.value
    if (historyLast != null) {
        if ((historyBiomesBackQueue.size == 0 || historyBiomesBackQueue.peek() != historyLast) && (parameters != historyLast.parameters || historyLast.graphSeed != graph.seed || !Arrays.equals(historyLast.mask.array, biomeMask.array))) {
            historyBiomesBackQueue.push(historyLast.copy())
        }
    }
    historyBiomesForwardQueue.clear()
    historyBiomesCurrent.value = BiomesHistoryItem(parameters.copy(), graph.seed, ByteArrayMatrix(biomeMask.width, biomeMask.array.copyOf()))
}

fun afterProjectOpen() {
    val currentBiomeGraph = currentState.biomeGraph.value
    val currentBiomeMask = currentState.biomeMask.value
    val currentSplines = currentState.regionSplines.value
    if (currentBiomeGraph != null && currentBiomeMask != null) {
        val biomeTextureId = Rendering.renderRegions(currentBiomeGraph, currentBiomeMask)
        val splineTextureId = if (currentSplines != null) {
            TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
        } else {
            TextureBuilder.renderSplines(emptyList(), emptyList(), emptyList())
        }
        meshViewport.setBiomes(biomeTextureId, splineTextureId)
        imageMode.value = 2
        displayMode.value = DisplayMode.BIOMES
    } else {
        val currentRegionSplines = currentState.regionSplines.value
        if (currentRegionSplines != null) {
            val regionTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
            meshViewport.setImage(regionTextureId)
            imageMode.value = 1
            displayMode.value = DisplayMode.MAP
            defaultToMap.value = true
        }
    }
}