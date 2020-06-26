package com.grimfox.gec

import com.grimfox.gec.model.*
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.nvgproxy.*
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.BiomeParameters
import com.grimfox.gec.util.BuildContinent.RegionParameters
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.logging.LOG
import java.awt.Desktop
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.CountDownLatch

const val DEMO_BUILD = false
const val EXPERIMENTAL_BUILD = false

const val VIEWPORT_TEXTURE_SIZE = 2048
const val VIEWPORT_HEIGHTMAP_SIZE = 8192
const val VIEWPORT_MESH_SCALE = 2600.0f

const val REGION_GRAPH_WIDTH = 512
const val REGION_GRAPH_WIDTH_M1 = REGION_GRAPH_WIDTH - 1
const val REGION_GRAPH_WIDTH_F = REGION_GRAPH_WIDTH.toFloat()

const val BIOME_GRAPH_WIDTH = 512
const val BIOME_GRAPH_WIDTH_M1 = BIOME_GRAPH_WIDTH - 1
const val BIOME_GRAPH_WIDTH_F = BIOME_GRAPH_WIDTH.toFloat()

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
) as List<NPColor>

val BIOME_COLORS = arrayListOf(
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


private fun colorToInt(id: Int): Int {
    val color = REGION_COLORS[id]
    return ((color.rInt and 0xFF) shl 16) + ((color.gInt and 0xFF) shl 8) + (color.bInt and 0xFF)
}

val REGION_COLOR_INTS = Array(REGION_COLORS.size) { i ->
    colorToInt(i)
}.toList()

val BIOME_TEMPLATES_REF = ref<Biomes?>(null)

val BIOME_NAMES_AS_TEXT = ObservableMutableList(ArrayList<Text>())

val RANDOM = Random()

const val DEFAULT_LIGHT_ELEVATION = 20.0f
const val DEFAULT_LIGHT_HEADING = 0.0f

const val DEFAULT_OUTPUT_QUALITY = 1
const val DEFAULT_MAP_DETAIL_SCALE = 6

val heightRanges = listOf(300, 400, 500, 700, 800, 1000, 1300, 1600, 1900, 2600, 3200, 3200, 3200, 3200, 3200, 3200)

val mapDetailScales = listOf(1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24, 32, 48, 64, 128)
val maxLinearDistanceMeters = 1024.0f * mapDetailScales.last()
val defaultHeightScale = linearDistanceMetersToViewportScaleFactor(mapScaleToLinearDistanceMeters(DEFAULT_MAP_DETAIL_SCALE), mapScaleToHeightRangeMeters(DEFAULT_MAP_DETAIL_SCALE))
val maxHeightScale = linearDistanceMetersToViewportScaleFactor(mapScaleToLinearDistanceMeters(0), mapScaleToHeightRangeMeters(0)) * 1.2f
val defaultHeightRangeMeters = mapScaleToHeightRangeMeters(DEFAULT_MAP_DETAIL_SCALE)

const val DEFAULT_COLOR_HEIGHT_SCALE = 4.0f

val defaultWaterLevel = linearDistanceMetersToRenderScale(linearDistanceMetersToWaterLevelMeters(mapScaleToLinearDistanceMeters(DEFAULT_MAP_DETAIL_SCALE)), mapScaleToHeightRangeMeters(DEFAULT_MAP_DETAIL_SCALE))

val heightScaleFunction = { scale: Float ->
    scale * maxHeightScale
}

val heightScaleFunctionInverse = { value: Float ->
    value / maxHeightScale
}

class RegionsHistoryItem(val parameters: RegionParameters, val graphSeed: Long, val mask: ByteArrayMatrix) {

    companion object {

        fun deserialize(input: DataInputStream): RegionsHistoryItem {
            return RegionsHistoryItem(
                    parameters = RegionParameters.deserialize(input),
                    graphSeed = input.readLong(),
                    mask = ByteArrayMatrix.deserialize(input))
        }
    }

    fun serialize(output: DataOutputStream) {
        parameters.serialize(output)
        output.writeLong(graphSeed)
        mask.serialize(output)
    }

    fun copy(): RegionsHistoryItem {
        return RegionsHistoryItem(parameters.copy(), graphSeed, ByteArrayMatrix(mask.width, mask.array.copyOf()))
    }
}

class BiomesHistoryItem(val parameters: BiomeParameters, val graphSeed: Long, val mask: ByteArrayMatrix) {

    companion object {

        fun deserialize(input: DataInputStream): BiomesHistoryItem {
            return BiomesHistoryItem(
                    parameters = BiomeParameters.deserialize(input),
                    graphSeed = input.readLong(),
                    mask = ByteArrayMatrix.deserialize(input))
        }
    }

    fun serialize(output: DataOutputStream) {
        parameters.serialize(output)
        output.writeLong(graphSeed)
        mask.serialize(output)
    }

    fun copy(): BiomesHistoryItem {
        return BiomesHistoryItem(parameters.copy(), graphSeed, ByteArrayMatrix(mask.width, mask.array.copyOf()))
    }
}

class WaterShaderParams(
        val level: ObservableMutableReference<Float> = ref(defaultWaterLevel),
        val color: Array<ObservableMutableReference<Float>> = arrayOf(ref(0.03f), ref(0.12f), ref(0.15f)),
        val metallic: ObservableMutableReference<Float> = ref(0.97f),
        val roughness: ObservableMutableReference<Float> = ref(0.03f),
        val specularIntensity: ObservableMutableReference<Float> = ref(1.0f),
        val normalOffsets: Array<ObservableMutableReference<Float>> = arrayOf(ref(0.3f), ref(0.065f), ref(0.008f), ref(0.0004f), ref(0.00004f), ref(0.00333f), ref(0.6f), ref(0.000233245f), ref(0.8f)),
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

class FogShaderParams(
        val color: Array<ObservableMutableReference<Float>> = arrayOf(ref(0.2f), ref(0.3f), ref(0.45f)),
        val atmosphericFogDensity: ObservableMutableReference<Float> = ref(0.007f),
        val exponentialFogDensity: ObservableMutableReference<Float> = ref(0.02f),
        val exponentialFogHeightFalloff: ObservableMutableReference<Float> = ref(0.2f),
        val fogHeightClampPower: ObservableMutableReference<Float> = ref(-20.0f),
        val fogOn: ObservableMutableReference<Float> = ref(1.0f),
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

val currentState: CurrentState get() = currentProject.value?.currentState?.value ?: CurrentState()

val waterShaderParams = WaterShaderParams()
val fogShaderParams = FogShaderParams()
val experimentalWidgets: MutableList<Block> = ArrayList()
val heightMapScaleFactor = ref(defaultHeightScale)
val colorHeightScaleFactor = ref(DEFAULT_COLOR_HEIGHT_SCALE)
val lightColor = Array(3) { ref(5.0f) }
val lightElevation = ref(DEFAULT_LIGHT_ELEVATION)
val lightHeading = ref(DEFAULT_LIGHT_HEADING)
val indirectIntensity = ref(1.0f)
val baseColor = Array(3) { ref(1.0f) }
val metallic = ref(0.0f)
val roughness = ref(0.95f)
val specularIntensity = ref(0.5f)
val waterPlaneOn = ref(true)
val perspectiveOn = ref(true)
val heightColorsOn = ref(true)
val riversOn = ref(true)
val skyOn = ref(true)
val fogOn = ref(true)
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
val historyRegionsCurrent: ObservableMutableReference<RegionsHistoryItem?> get() = currentProject.value?.historyRegionsCurrent ?: ref<RegionsHistoryItem?>(null)
val historyRegionsForwardQueue get() = currentProject.value?.historyRegionsForwardQueue ?: HistoryQueue(0)
val historySplinesBackQueue get() = currentProject.value?.historySplinesBackQueue ?: HistoryQueue(0)
val historySplinesCurrent: ObservableMutableReference<RegionSplines?> get() = currentProject.value?.historySplinesCurrent ?: ref<RegionSplines?>(null)
val historySplinesForwardQueue get() = currentProject.value?.historySplinesForwardQueue ?: HistoryQueue(0)
val historyBiomesBackQueue get() = currentProject.value?.historyBiomesBackQueue ?: HistoryQueue(0)
val historyBiomesCurrent: ObservableMutableReference<BiomesHistoryItem?> get() = currentProject.value?.historyBiomesCurrent ?: ref<BiomesHistoryItem?>(null)
val historyBiomesForwardQueue get() = currentProject.value?.historyBiomesForwardQueue ?: HistoryQueue(0)
val displayMode = ref(DisplayMode.MAP)
val defaultToMap = ref(true)
val rootRef = ref(NO_BLOCK)
val mapDetailScale = ref(DEFAULT_MAP_DETAIL_SCALE)
val heightRangeMeters = ref(defaultHeightRangeMeters)
val outputQuality = ref(DEFAULT_OUTPUT_QUALITY)


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
        lightColor = lightColor,
        lightElevation = lightElevation,
        lightHeading = lightHeading,
        indirectIntensity = indirectIntensity,
        baseColor = baseColor,
        metallic = metallic,
        roughness = roughness,
        specularIntensity = specularIntensity,
        waterParams = waterShaderParams,
        fogParams = fogShaderParams,
        imageMode = imageMode,
        disableCursor = disableCursor,
        hideCursor = hideCursor,
        brushOn = brushOn,
        brushActive = brushActive,
        brushListener = brushListener,
        brushSize = brushSize,
        editBrushSizeRef = currentEditBrushSize,
        pickerOn = pickerOn,
        pointPicker = pointPicker,
        uiRoot = rootRef)

var mainLayer = NO_BLOCK
var panelLayer = NO_BLOCK
var menuLayer = NO_BLOCK
var dropdownLayer = NO_BLOCK
var dialogLayer = NO_BLOCK
var dialogDropdownLayer = NO_BLOCK
var mouseOverlayLayer = NO_BLOCK
var brushShapeOuter = NO_BLOCK
var brushShapeInner = NO_BLOCK

var preferencesPanel = NO_BLOCK
var exportPanel = NO_BLOCK
var tilePanel = NO_BLOCK
var aboutPanel = NO_BLOCK
var customBiomePanel = NO_BLOCK
val icon = ref(-1)


var onWindowResize: () -> Unit = {}

val generationLock = DisableSetLock()
val editToggleSet = ToggleSet(executor)

fun mapScaleToLinearDistanceMeters(mapScale: Int): Float {
    return 1024.0f * mapDetailScales[mapScale]
//    val adjustedScale = mapScale - 5
//    return if (adjustedScale < 0) adjustedScale * 1.95f + 10 else ((((adjustedScale) * (adjustedScale)) / 400.0f) * 990000 + 10000) / 1000
}

fun mapScaleToHeightRangeMeters(mapScale: Int): Float {
    return heightRanges[mapScale].toFloat()
}

fun linearDistanceMetersToViewportScaleFactor(linearDistanceMeters: Float, heightRangeMeters: Float): Float {
    return heightRangeMeters * (VIEWPORT_MESH_SCALE / linearDistanceMeters)

//    val linearDistanceScaleInKilometers = linearDistanceScaleInMeters / 1000.0f
//    return if (linearDistanceScaleInKilometers < 10.0f) {
//        linearDistanceScaleInKilometers * 12.1f + 215.0015f
//    } else {
//        ((-log10(linearDistanceScaleInKilometers - 9.0) - 1) * 28 + 122).toFloat()
//    }
}

fun linearDistanceMetersToRenderScale(linearDistanceMeters: Float, heightRangeMeters: Float): Float {
    return linearDistanceMeters / heightRangeMeters
}

fun linearDistanceMetersToWaterLevelMeters(linearDistanceMeters: Float): Float {
    return (((linearDistanceMeters - mapScaleToLinearDistanceMeters(0)) / 60.0f) + 30.0f).coerceIn(30.0f, 500.0f)
}

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
var generatingMessageBlock = NO_BLOCK
var generatingPrimaryMessage = StaticTextReference()
var generatingSecondaryMessage = StaticTextReference()
val noop = {}
val dialogCallback = mRef(noop)

val cancelCurrentRunningTask: ObservableMutableReference<MutableReference<Boolean>?> = ref(null)

class MainThreadTask<T>(val latch: CountDownLatch? = null, val work: () -> T) {

    var returnVal: T? = null
    var error: Throwable? = null

    fun perform() {
        try {
            returnVal = work()
        } catch (t: Throwable) {
            error = t
        } finally {
            latch?.countDown()
        }
    }
}

private val mainThreadTasksLock = Object()
private var mainThreadTasks = ArrayList<MainThreadTask<*>>()

fun performMainThreadTasks() {
    synchronized(mainThreadTasksLock) {
        val copy = mainThreadTasks
        mainThreadTasks = ArrayList()
        copy.forEach(MainThreadTask<*>::perform)
    }
}

fun <T> doOnMainThreadAndWait(callable: () -> T): T {
    val latch = CountDownLatch(1)
    val task = MainThreadTask(latch, callable)
    synchronized(mainThreadTasksLock) {
        mainThreadTasks.add(task)
    }
    while (latch.count > 0) {
        latch.await()
    }
    val error = task.error
    if (error != null) {
        throw error
    } else {
        return task.returnVal!!
    }
}

fun doOnMainThread(callable: () -> Unit) {
    synchronized(mainThreadTasksLock) {
        mainThreadTasks.add(MainThreadTask(null, callable))
    }
}

fun newProject(overwriteWarningReference: ObservableMutableReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, noop: () -> Unit) {
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

fun openProject(ui: UserInterface, errorHandler: ErrorDialog) {
    val openFun = {
        try {
            val openedProject = openProject(dialogLayer, preferences, ui)
            if (openedProject != null) {
                currentProject.value = openedProject
                afterProjectOpen()
            }
        } catch (e: IOException) {
            errorHandler.displayErrorMessage("Unable to read from the selected file while trying to open project.")
        } catch (e: Exception) {
            errorHandler.displayErrorMessage("Encountered an unexpected error while trying to open project.")
        }
    }
    if (currentProject.value != null && currentProjectHasModifications.value) {
        dialogLayer.isVisible = true
        overwriteWarningReference.value = "Do you want to save the current project before opening a different one?"
        overwriteWarningDialog.isVisible = true
        dialogCallback.value = openFun
    } else {
        openFun()
    }
}

fun openProject(errorHandler: ErrorDialog, projectFile: File?) {
    val openFun = {
        try {
            val openedProject = openProject(projectFile)
            if (openedProject != null) {
                currentProject.value = openedProject
                afterProjectOpen()
            } else {
                errorHandler.displayErrorMessage("The selected file \"${projectFile?.canonicalPath}\" could not be opened.")
            }
        } catch (e: IOException) {
            errorHandler.displayErrorMessage("Unable to read from the selected file while trying to open project.")
        } catch (e: Exception) {
            errorHandler.displayErrorMessage("Encountered an unexpected error while trying to open project.")
        }
    }
    if (currentProject.value != null && currentProjectHasModifications.value) {
        dialogLayer.isVisible = true
        overwriteWarningReference.value = "Do you want to save the current project before opening a different one?"
        overwriteWarningDialog.isVisible = true
        dialogCallback.value = openFun
    } else {
        openFun()
    }
}

fun exportMaps() {
    panelLayer.isVisible = true
    exportPanel.isVisible = true
}

fun tileMaps() {
    panelLayer.isVisible = true
    tilePanel.isVisible = true
}

fun UserInterface.closeWindowSafely() {
    if (currentProject.value != null && currentProjectHasModifications.value) {
        dialogLayer.isVisible = true
        overwriteWarningReference.value = "Do you want to save the current project before exiting?"
        overwriteWarningDialog.isVisible = true
        dialogCallback.value = {
            closeWindow()
        }
    } else {
        closeWindow()
    }
}

fun openPreferences() {
    panelLayer.isVisible = true
    preferencesPanel.isVisible = true
}

fun openHelp(errorHandler: ErrorDialog) {
    if (OFFLINE_HELP_INDEX_FILE.isFile && OFFLINE_HELP_INDEX_FILE.canRead()) {
        openWebPage(OFFLINE_HELP_INDEX_FILE.toURI(), errorHandler)
    } else {
        errorHandler.displayErrorMessage("The offline help files are not currently installed. Please install the offline help files, or select one of the online help options.")
    }
}

fun openAboutPanel() {
    panelLayer.isVisible = true
    aboutPanel.isVisible = true
}

fun downloadOfflineHelp(errorHandler: ErrorDialog) {
    errorHandler.displayErrorMessage("Offline help is not currently available for this version of WorldKit. Please select one of the online help options.")
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
    val currentBiomeParameters = currentState.biomeParameters.value
    if (currentBiomeParameters != null) {
        syncBiomeParameterValues(currentBiomeParameters)
    }
    val currentRegionParameters = currentState.regionParameters.value
    if (currentRegionParameters != null) {
        syncRegionParameterValues(currentRegionParameters)
    }
    val currentBiomeGraph = currentState.biomeGraph.value
    val currentBiomeMask = currentState.biomeMask.value
    val currentSplines = currentState.regionSplines.value
    if (currentBiomeGraph != null && currentBiomeMask != null) {
        val biomeTextureId = Rendering.renderRegions(VIEWPORT_TEXTURE_SIZE, currentBiomeGraph, currentBiomeMask)
        val splineTextureId = if (currentSplines != null) {
            TextureBuilder.renderSplines(VIEWPORT_TEXTURE_SIZE, currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
        } else {
            TextureBuilder.renderSplines(VIEWPORT_TEXTURE_SIZE, emptyList(), emptyList(), emptyList())
        }
        meshViewport.setBiomes(biomeTextureId, splineTextureId)
        imageMode.value = 2
        displayMode.value = DisplayMode.BIOMES
    } else {
        val currentRegionSplines = currentState.regionSplines.value
        if (currentRegionSplines != null) {
            val regionTextureId = TextureBuilder.renderMapImage(VIEWPORT_TEXTURE_SIZE, currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
            meshViewport.setImage(regionTextureId)
            imageMode.value = 1
            displayMode.value = DisplayMode.MAP
            defaultToMap.value = true
        }
    }
}

fun openWebPage(uri: URI, onError: ErrorDialog? = null) {
    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
        try {
            desktop.browse(uri)
        } catch (e: Exception) {
            LOG.error("Unable to open web page: $uri", e)
            onError?.displayErrorMessage(e.message)
        }
    }
}

fun openWebPage(url: URL, onError: ErrorDialog? = null) {
    try {
        openWebPage(url.toURI(), onError)
    } catch (e: URISyntaxException) {
        LOG.error("Unable to open web page: $url", e)
        onError?.displayErrorMessage(e.message)
    }
}

fun openWebPage(url: String, onError: ErrorDialog? = null) {
    try {
        openWebPage(URL(url), onError)
    } catch (e: Exception) {
        LOG.error("Unable to open web page: $url", e)
        onError?.displayErrorMessage(e.message)
    }
}
