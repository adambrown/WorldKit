package com.grimfox.gec

import com.grimfox.gec.brushes.*
import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.ShortArrayMatrix
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRgbaByte
import com.grimfox.gec.ui.widgets.TextureBuilder.renderMapImage
import com.grimfox.gec.ui.widgets.TextureBuilder.renderSplineSelectors
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.ParameterSet
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.gec.util.BuildContinent.buildBiomeMaps
import com.grimfox.gec.util.BuildContinent.generateWaterFlows
import com.grimfox.gec.util.Rendering.renderRegions
import org.lwjgl.glfw.GLFW
import java.io.File
import java.lang.Math.round
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO
import kotlin.collections.LinkedHashMap

private val biomeValues = linkedMapOf(
        "Mountains" to 0,
        "Coastal mountains" to 1,
        "Foothills" to 2,
        "Rolling hills" to 3,
        "Plateaus" to 4,
        "Plains" to 5
)

private val cachedGraph256 = preferences.cachedGraph256!!

private val cachedGraph512 = preferences.cachedGraph512!!

private val cachedGraph1024 = preferences.cachedGraph1024!!

private val editToggleCurrentPointer = ref<MutableReference<Boolean>?>(null)
private val editToggleLatch = ref<CountDownLatch?>(null)

private val shrinkGroup = hShrinkGroup()
private val regionFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val biomeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val useRegionFile = ref(false)
private val useBiomeFile = ref(false)
private val allowUnsafe = ref(false)
private val editRegionsMode = ref(false)
private val drawSplinesMode = ref(false)
private val splineSmoothing = ref(10)
private val editSplinesMode = ref(false)
private val editSplinesSelectionRadius = ref(80)
private val deleteSplinesMode = ref(false)
private val deleteSplineSelectionRadius = ref(80)
private val editBiomesMode = ref(false)
private val regionsSeed = ref(1L)
private val biomesSeed = ref(1L)
private val edgeDetailScale = ref(4)
private val biomesMapScale = ref(4)
private val biomeCount = ref(1)
private val regions = ref(8)
private val islands = ref(1)
private val stride = ref(7)
private val startPoints = ref(2)
private val reduction = ref(7)
private val connectedness = ref(0.114f)
private val regionSize = ref(0.034f)
private val iterations = ref(5)
private val currentBiomeBrushValue = ref(1.toByte())
private val mapDetailScale = ref(4)
private val onUpdateParamsFun = {
    if (!allowUnsafe.value) {
        stride.value = ((regions.value + 3) / 4) + 5
        val stride2 = stride.value * stride.value
        val approxInland = stride2 - (4 * stride.value) + 4
        startPoints.value = Math.min(6, Math.max(1, Math.floor(approxInland / (regions.value.toDouble() + (islands.value / 2))).toInt() - 1))
        reduction.value = Math.floor((approxInland - (startPoints.value * regions.value)) * 0.8).toInt()
        connectedness.value = round(((1.0f / stride.value) * 0.77f) * 1000.0f) / 1000.0f
        val unitLength = 1.0f / stride.value
        val unit2 = unitLength * unitLength
        regionSize.value = round((((unit2 * (approxInland - reduction.value)) / regions.value) * 0.763f) * 1000.0f) / 1000.0f
    }
}
private val generateLock = ReentrantLock()
private var generating = false
private var editRegionsToggle = NO_BLOCK
private var drawSplinesToggle = NO_BLOCK
private var editSplinesToggle = NO_BLOCK
private var deleteSplinesToggle = NO_BLOCK
private var editBiomesToggle = NO_BLOCK

private val biomes = ref(emptyList<Int>())
private val selectedBiomes = Array(16) { ref(it % biomeValues.size) }.toList()

enum class DisplayMode { REGIONS, MAP, BIOMES, MESH }

private val displayMode = ref(DisplayMode.MAP)
private val defaultToMap = ref(true)

private val regionsBuilder = RegionsBuilder(regionFile, useRegionFile, displayMode, defaultToMap)

private val disableOnGenerateButtons = ArrayList<DisableSet>()

fun Block.disableOnGenerateButton(label: String, disableCondition: () -> Boolean = { false }, onClick: () -> Unit = {}) {
    disableOnGenerateButtons.add(disableButton(label, disableCondition, onClick))
}

fun disableOnGenerateSet(selector: () -> Int, vararg sets: DisableSet) {
    disableOnGenerateButtons.add(disableSet(selector, *sets))
}

fun disableGenerateButtons() {
    disableOnGenerateButtons.forEach(DisableSet::disable)
}

private fun enableGenerateButtons() {
    disableOnGenerateButtons.forEach(DisableSet::enable)
}

private fun doGeneration(doWork: () -> Unit) {
    if (generateLock.tryLock()) {
        try {
            if (!generating) {
                generating = true
                try {
                    disableGenerateButtons()
                    try {
                        doWork()
                    } finally {
                        enableGenerateButtons()
                    }
                } finally {
                    generating = false
                }
            }
        } finally {
            generateLock.unlock()
        }
    }
}

private fun doGenerationStart() {
    if (generateLock.tryLock()) {
        try {
            if (!generating) {
                generating = true
                disableGenerateButtons()
            }
        } finally {
            generateLock.unlock()
        }
    }
}

private fun doGenerationStop() {
    if (generateLock.tryLock()) {
        try {
            if (generating) {
                enableGenerateButtons()
                generating = false
            }
        } finally {
            generateLock.unlock()
        }
    }
}

private fun buildBiomesFun(parameters: ParameterSet, refreshOnly: Boolean = false) {
    val currentBiomeGraph = currentState.biomeGraph
    val currentBiomeMask = currentState.biomeMask
    val (biomeGraph, biomeMask) = if (refreshOnly && currentBiomeGraph != null && currentBiomeMask != null) {
        currentBiomeGraph to currentBiomeMask
    } else {
        val finalBiomeFile = biomeFile.reference.value
        if (useBiomeFile.value && finalBiomeFile.isNotBlank()) {
            val mask = loadBiomeMaskFromImage(File(finalBiomeFile))
            for (i in 0..mask.size.toInt() - 1) {
                mask[i] = ((mask[i].toInt() % parameters.biomes.size) + 1).toByte()
            }
            val graph = Graphs.generateGraph(128, parameters.biomesSeed, 0.8)
            Pair(graph, mask)
        } else {
            val scale = ((parameters.biomesMapScale * parameters.biomesMapScale) / 400.0f).coerceIn(0.0f, 1.0f)
            val biomeScale = round(scale * 21) + 7
            val graph = Graphs.generateGraph(128, parameters.biomesSeed, 0.8)
            buildBiomeMaps(executor, parameters.biomesSeed, graph, parameters.biomes.size, biomeScale)
        }
    }
    currentState.biomeGraph = biomeGraph
    currentState.biomeMask = biomeMask
    currentState.biomes = parameters.biomes.map {
        when (it) {
            0 -> Biomes.MOUNTAINS_BIOME
            1 -> Biomes.COASTAL_MOUNTAINS_BIOME
            2 -> Biomes.FOOTHILLS_BIOME
            3 -> Biomes.ROLLING_HILLS_BIOME
            4 -> Biomes.PLATEAU_BIOME
            5 -> Biomes.PLAINS_BIOME
            else -> Biomes.MOUNTAINS_BIOME
        }
    }
    currentState.heightMapTexture = null
    currentState.riverMapTexture = null
    val biomeTextureId = renderRegions(biomeGraph, biomeMask)
    val currentSplines = currentState.regionSplines
    val splineTextureId = if (currentSplines != null) {
        TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
    } else {
        TextureBuilder.renderSplines(emptyList(), emptyList(), emptyList())
    }
    meshViewport.setBiomes(biomeTextureId, splineTextureId)
    imageMode.value = 2
    displayMode.value = DisplayMode.BIOMES
}

private fun syncParameterValues(parameters: ParameterSet) {
    var randomSeed = parameters.regionsSeed
    var randomString = randomSeed.toString()
    if (randomString.length > 18) {
        regionsSeed.value = randomString.substring(0, 18).toLong()
    } else {
        regionsSeed.value = randomSeed
    }
    randomSeed = parameters.biomesSeed
    randomString = randomSeed.toString()
    if (randomString.length > 18) {
        biomesSeed.value = randomString.substring(0, 18).toLong()
    } else {
        biomesSeed.value = randomSeed
    }
    edgeDetailScale.value = parameters.edgeDetailScale
    biomesMapScale.value = parameters.biomesMapScale
    mapDetailScale.value = parameters.mapDetailScale
    regions.value = parameters.regionCount
    stride.value = parameters.stride
    islands.value = parameters.islandDesire
    startPoints.value = parameters.regionPoints
    reduction.value = parameters.initialReduction
    connectedness.value = parameters.connectedness
    regionSize.value = parameters.regionSize
    iterations.value = parameters.maxRegionTries / 10
    iterations.value = parameters.maxIslandTries / 100
    biomes.value = parameters.biomes
    biomes.value.forEachIndexed { i, id ->
        selectedBiomes[i].value = id
    }
}

private fun extractCurrentParameters(): ParameterSet {
    val parameters = ParameterSet(
            regionsSeed = regionsSeed.value,
            biomesSeed = biomesSeed.value,
            edgeDetailScale = edgeDetailScale.value,
            biomesMapScale = biomesMapScale.value,
            mapDetailScale = mapDetailScale.value,
            regionCount = regions.value,
            stride = stride.value,
            islandDesire = islands.value,
            regionPoints = startPoints.value,
            initialReduction = reduction.value,
            connectedness = connectedness.value,
            regionSize = regionSize.value,
            maxRegionTries = iterations.value * 10,
            maxIslandTries = iterations.value * 100,
            biomes = biomes.value)
    return parameters
}

fun Block.leftPanel(ui: UserInterface, uiLayout: UiLayout, dialogLayer: Block): Block {
    return block {
        val leftPanel = this
        hSizing = Sizing.STATIC
        width = 370.0f
        layout = Layout.HORIZONTAL
        hAlign = HorizontalAlignment.LEFT
        block {
            hSizing = Sizing.GROW
            layout = Layout.HORIZONTAL
            hAlign = HorizontalAlignment.LEFT
            block {
                xOffset = SMALL_SPACER_SIZE
                yOffset = SMALL_SPACER_SIZE
                width = -SMALL_SPACER_SIZE
                height = -2 * SMALL_SPACER_SIZE
                block {
                    xOffset = 8.0f
                    yOffset = 8.0f
                    width = -16.0f
                    height = -16.0f
                    leftPanelWidgets(ui, uiLayout, dialogLayer)
                    block {
                        vSizing = Sizing.STATIC
                        height = 1.0f
                        layout = Layout.VERTICAL
                        shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                    }
                }
                block {
                    shape = SHAPE_BORDER_ONLY
                    canOverflow = true
                    isMouseAware = false
                }
            }
        }
        block {
            hSizing = Sizing.STATIC
            width = SMALL_SPACER_SIZE
            layout = Layout.HORIZONTAL
            val grabber = button(NO_TEXT, NORMAL_TEXT_BUTTON_STYLE {
                copy(template = template.copy(
                        vSizing = Sizing.STATIC,
                        height = 3 * LARGE_ROW_HEIGHT,
                        vAlign = VerticalAlignment.MIDDLE,
                        hSizing = Sizing.RELATIVE,
                        width = -2.0f,
                        hAlign = HorizontalAlignment.CENTER))
            })
            var lastX = 0.0f
            onMouseDown { button, x, _, _ ->
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    lastX = x.toFloat()
                }
            }
            onMouseDrag { button, x, _, _ ->
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    val delta = x - lastX
                    val adjustedDelta = Math.max(370.0f, Math.min(root.width * 0.75f, leftPanel.width + delta)) - leftPanel.width
                    lastX += adjustedDelta
                    leftPanel.width += adjustedDelta
                }
            }
            supplantEvents(grabber)
        }
    }
}

private fun Block.leftPanelWidgets(ui: UserInterface, uiLayout: UiLayout, dialogLayer: Block) {
    onUpdateParamsFun()
    regions.listener { _, _ -> onUpdateParamsFun() }
    islands.listener { _, _ -> onUpdateParamsFun() }
    block {
        hSizing = Sizing.GROW
        layout = Layout.HORIZONTAL
        val scroller = ref(NO_BLOCK)
        val resetScroller: () -> Unit = {
            val scrollerInternal = scroller.value
            scrollerInternal.onScroll?.invoke(scrollerInternal, 0.0, 0.0)
        }
        val resetScrollerListener: (Boolean, Boolean) -> Unit = { old, new ->
            if (old != new && !new) {
                resetScroller()
            }
        }
        scroller.value = block {
            receiveChildEvents = true
            vSizing = Sizing.SHRINK
            layout = Layout.VERTICAL
            val regionPanelExpanded = ref(true)
            val regionPanel = vExpandPanel("Edit regions", expanded = regionPanelExpanded) {
                vSpacer(HALF_ROW_HEIGHT)
                vButtonRow(LARGE_ROW_HEIGHT) {
                    disableOnGenerateButton("Import regions") {
                        doGeneration {
                            val historyItem = openRegionsFile(dialogLayer, preferences, ui)
                            if (historyItem != null) {
                                val historyLast = historyRegionsCurrent.value
                                if (historyLast != null) {
                                    historyRegionsBackQueue.push(historyLast.copy())
                                }
                                syncParameterValues(historyItem.parameters)
                                currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                                currentState.regionMask = historyItem.mask
                                regionsBuilder.build(historyItem.parameters, true, true)
                                historyRegionsCurrent.value = historyItem.copy()
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Export regions", { historyRegionsCurrent.value == null }) {
                        doGeneration {
                            exportRegionsFile(historyRegionsCurrent.value, dialogLayer, preferences, ui)
                        }
                    }
                }
                vSpacer(HALF_ROW_HEIGHT)
                vLongInputRow(regionsSeed, LARGE_ROW_HEIGHT, text("Seed:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, uiLayout) {
                    hSpacer(SMALL_SPACER_SIZE)
                    button(text("Randomize"), NORMAL_TEXT_BUTTON_STYLE) {
                        val randomSeed = RANDOM.nextLong()
                        val randomString = randomSeed.toString()
                        if (randomString.length > 18) {
                            regionsSeed.value = randomString.substring(0, 18).toLong()
                        } else {
                            regionsSeed.value = randomSeed
                        }
                    }
                }
                vFileRowWithToggle(regionFile, useRegionFile, LARGE_ROW_HEIGHT, text("Region file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
                val regionScaleSlider = vSliderWithValueRow(edgeDetailScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Edge detail:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
                var regionScaleSliderInitial = edgeDetailScale.value
                val regionScaleSliderMouseDown = regionScaleSlider.onMouseDown
                regionScaleSlider.onMouseDown { a, b, c, d ->
                    regionScaleSliderMouseDown?.invoke(regionScaleSlider, a, b, c, d)
                    regionScaleSliderInitial = edgeDetailScale.value
                }
                val regionScaleSliderMouseRelease = regionScaleSlider.onMouseRelease
                regionScaleSlider.onMouseRelease { a, b, c, d ->
                    regionScaleSliderMouseRelease?.invoke(regionScaleSlider, a, b, c, d)
                    executor.call {
                        doGeneration {
                            val currentGraph = currentState.regionGraph
                            val currentMask = currentState.regionMask
                            val currentSplines = currentState.regionSplines
                            if (edgeDetailScale.value != regionScaleSliderInitial && currentGraph != null && currentMask != null && currentSplines != null) {
                                val parameters = extractCurrentParameters()
                                regionsBuilder.build(parameters, true, true)
                                updateRegionsHistory(parameters, currentGraph, currentMask)
                                val currentRegionSplines = currentState.regionSplines
                                if (currentRegionSplines != null && displayMode.value == DisplayMode.MAP) {
                                    val regionTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
                                    meshViewport.setImage(regionTextureId)
                                }
                            }
                        }
                    }
                }
                vSliderWithValueRow(regions, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Number of regions:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
                vSliderWithValueRow(islands, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Prefer islands:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..5), linearClampedScaleFunctionInverse(0..5))
                debugWidgets.add(vSliderRow(iterations, LARGE_ROW_HEIGHT, text("Iterations:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(2..30), linearClampedScaleFunctionInverse(2..30)))
                debugWidgets.add(vToggleRow(allowUnsafe, LARGE_ROW_HEIGHT, text("Unsafe mode:"), shrinkGroup, MEDIUM_SPACER_SIZE))
                val unsafeBlocks = ArrayList<Block>()
                unsafeBlocks.add(vSliderWithValueRow(stride, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Stride:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(5..10), linearClampedScaleFunctionInverse(5..10)).with { isMouseAware = allowUnsafe.value })
                unsafeBlocks.add(vSliderWithValueRow(startPoints, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Start points:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..6), linearClampedScaleFunctionInverse(1..6)).with { isMouseAware = allowUnsafe.value })
                unsafeBlocks.add(vSliderWithValueRow(reduction, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Reduction:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..40), linearClampedScaleFunctionInverse(0..40)).with { isMouseAware = allowUnsafe.value })
                unsafeBlocks.add(vSliderWithValueRow(connectedness, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Connection:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.004f, 0.2f), linearClampedScaleFunctionInverse(0.004f, 0.2f)).with { isMouseAware = allowUnsafe.value })
                unsafeBlocks.add(vSliderWithValueRow(regionSize, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Region size:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.005f, 0.05f), linearClampedScaleFunctionInverse(0.005f, 0.05f)).with { isMouseAware = allowUnsafe.value })
                allowUnsafe.listener { _, new ->
                    if (!new) {
                        onUpdateParamsFun()
                    }
                    unsafeBlocks.forEach {
                        it.isMouseAware = new
                        it.isVisible = new
                    }
                }
                allowUnsafe.value = false
                editRegionsToggle = vToggleRow(editRegionsMode, LARGE_ROW_HEIGHT, text("Manual edit:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                val editRegionsBlocks = ArrayList<Block>()
                editRegionsBlocks.add(vSliderRow(regionEditBrushSize, LARGE_ROW_HEIGHT, text("Brush size:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.015625f, 0.15625f), linearClampedScaleFunctionInverse(0.015625f, 0.15625f)))
                var regionEditActivated = false
                editRegionsMode.listener { old, new ->
                    editRegionsBlocks.forEach {
                        it.isMouseAware = new
                        it.isVisible = new
                    }
                    if (old != new) {
                        executor.call {
                            if (new) {
                                disableCurrentToggleIfEnabled()
                                val currentGraph = currentState.regionGraph
                                val currentMask = currentState.regionMask
                                if (currentGraph != null && currentMask != null) {
                                    doGenerationStart()
                                    editToggleCurrentPointer.value = editRegionsMode
                                    regionEditActivated = true
                                    val regionTextureId = renderRegions(currentGraph, currentMask)
                                    meshViewport.setRegions(regionTextureId)
                                    imageMode.value = 0
                                    displayMode.value = DisplayMode.REGIONS
                                    defaultToMap.value = false
                                    val textureReference = ref(TextureId(-1))
                                    textureReference.listener { oldTexture, newTexture ->
                                        if (oldTexture != newTexture) {
                                            meshViewport.setRegions(newTexture)
                                        }
                                    }
                                    currentEditBrushSize.value = regionEditBrushSize
                                    brushListener.value = PickAndGoDrawBrushListener(currentGraph, currentMask, regionEditBrushSize, textureReference)
                                    brushOn.value = true
                                } else {
                                    editRegionsMode.value = false
                                }
                            } else {
                                if (regionEditActivated) {
                                    brushListener.value = null
                                    brushOn.value = false
                                    val parameters = extractCurrentParameters()
                                    regionsBuilder.build(parameters, true)
                                    val currentRegionSplines = currentState.regionSplines
                                    val currentGraph = currentState.regionGraph
                                    val currentMask = currentState.regionMask
                                    if (currentRegionSplines != null && currentGraph != null && currentMask != null) {
                                        updateRegionsHistory(parameters, currentGraph, currentMask)
                                        val mapTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
                                        meshViewport.setImage(mapTextureId)
                                        imageMode.value = 1
                                        displayMode.value = DisplayMode.MAP
                                        defaultToMap.value = true
                                    }
                                    regionEditActivated = false
                                    if (editToggleCurrentPointer.value == editRegionsMode) {
                                        editToggleCurrentPointer.value = null
                                    }
                                    doGenerationStop()
                                }
                                editToggleLatch.value?.countDown()
                            }
                        }
                    }
                }
                editRegionsMode.value = false
                vSpacer(HALF_ROW_HEIGHT)
                vButtonRow(LARGE_ROW_HEIGHT) {
                    disableOnGenerateButton("Generate") {
                        doGeneration {
                            val parameters = extractCurrentParameters()
                            regionsBuilder.build(parameters)
                            val currentGraph = currentState.regionGraph
                            val currentMask = currentState.regionMask
                            if (currentGraph != null && currentMask != null) {
                                updateRegionsHistory(parameters, currentGraph, currentMask)
                                val currentSplines = currentState.regionSplines
                                if (historySplinesCurrent.value == null && currentSplines != null) {
                                    updateSplinesHistory(currentSplines)
                                }
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Generate random") {
                        doGeneration {
                            val randomSeed = RANDOM.nextLong()
                            val randomString = randomSeed.toString()
                            if (randomString.length > 18) {
                                regionsSeed.value = randomString.substring(0, 18).toLong()
                            } else {
                                regionsSeed.value = randomSeed
                            }
                            val parameters = extractCurrentParameters()
                            regionsBuilder.build(parameters)
                            val currentGraph = currentState.regionGraph
                            val currentMask = currentState.regionMask
                            if (currentGraph != null && currentMask != null) {
                                updateRegionsHistory(parameters, currentGraph, currentMask)
                                val currentSplines = currentState.regionSplines
                                if (historySplinesCurrent.value == null && currentSplines != null) {
                                    updateSplinesHistory(currentSplines)
                                }
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Back", { historyRegionsBackQueue.size == 0 }) {
                        doGeneration {
                            val historyItem = historyRegionsBackQueue.pop()
                            if (historyItem != null) {
                                val historyLast = historyRegionsCurrent.value
                                if (historyLast != null) {
                                    historyRegionsForwardQueue.push(historyLast.copy())
                                }
                                syncParameterValues(historyItem.parameters)
                                currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                                currentState.regionMask = historyItem.mask
                                regionsBuilder.build(historyItem.parameters, true, true)
                                historyRegionsCurrent.value = historyItem.copy()
                                val currentSplines = currentState.regionSplines
                                if (historySplinesCurrent.value == null && currentSplines != null) {
                                    updateSplinesHistory(currentSplines)
                                }
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Forward", { historyRegionsForwardQueue.size == 0 }) {
                        doGeneration {
                            val historyItem = historyRegionsForwardQueue.pop()
                            if (historyItem != null) {
                                val historyLast = historyRegionsCurrent.value
                                if (historyLast != null) {
                                    historyRegionsBackQueue.push(historyLast.copy())
                                }
                                syncParameterValues(historyItem.parameters)
                                currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                                currentState.regionMask = historyItem.mask
                                regionsBuilder.build(historyItem.parameters, true, true)
                                historyRegionsCurrent.value = historyItem.copy()
                                val currentSplines = currentState.regionSplines
                                if (historySplinesCurrent.value == null && currentSplines != null) {
                                    updateSplinesHistory(currentSplines)
                                }
                            }
                        }
                    }
                }
                vSpacer(HALF_ROW_HEIGHT)
            }
            regionPanel.isVisible = false
            regionPanelExpanded.listeners.add(resetScrollerListener)
            val splinePanelExpanded = ref(true)
            val splinePanel = vExpandPanel("Edit splines", expanded = splinePanelExpanded) {
                drawSplinesToggle = vToggleRow(drawSplinesMode, LARGE_ROW_HEIGHT, text("Draw splines:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                val drawSplinesBlocks = ArrayList<Block>()
                drawSplinesBlocks.add(vSliderRow(splineSmoothing, LARGE_ROW_HEIGHT, text("Smoothing:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20)))
                var drawSplinesActivated = false
                drawSplinesMode.listener { old, new ->
                    drawSplinesBlocks.forEach {
                        it.isMouseAware = new
                        it.isVisible = new
                    }
                    if (old != new) {
                        executor.call {
                            if (new) {
                                disableCurrentToggleIfEnabled()
                                val currentSplines = currentState.regionSplines
                                if (currentSplines != null) {
                                    doGenerationStart()
                                    editToggleCurrentPointer.value = drawSplinesMode
                                    drawSplinesActivated = true
                                    val splineMap = LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>()
                                    var index = 0
                                    currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 0, pair, edges))
                                    }
                                    currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 1, pair, edges))
                                    }
                                    currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 2, pair, edges))
                                    }
                                    currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 3, points to points, edges))
                                    }
                                    currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 4, points to points, edges))
                                    }
                                    currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 5, points to points, edges))
                                    }
                                    if (displayMode.value != DisplayMode.MAP) {
                                        val mapTextureId = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints)
                                        meshViewport.setImage(mapTextureId)
                                        imageMode.value = 1
                                        displayMode.value = DisplayMode.MAP
                                        defaultToMap.value = true
                                    }
                                    val textureReference = ref(TextureId(-1))
                                    textureReference.listener { oldTexture, newTexture ->
                                        if (oldTexture != newTexture) {
                                            meshViewport.setImage(newTexture)
                                        }
                                    }
                                    currentEditBrushSize.value = drawSplineBrushSize
                                    brushListener.value = SplineDrawBrushListener(splineSmoothing, currentState, currentSplines, splineMap, textureReference)
                                    brushOn.value = true
                                } else {
                                    drawSplinesMode.value = false
                                }
                            } else {
                                if (drawSplinesActivated) {
                                    brushListener.value = null
                                    brushOn.value = false
                                    val parameters = extractCurrentParameters()
                                    regionsBuilder.build(parameters, true, false)
                                    val currentSplines = currentState.regionSplines
                                    if (currentSplines != null) {
                                        updateSplinesHistory(currentSplines)
                                    }
                                    drawSplinesActivated = false
                                    if (editToggleCurrentPointer.value == drawSplinesMode) {
                                        editToggleCurrentPointer.value = null
                                    }
                                    doGenerationStop()
                                }
                                editToggleLatch.value?.countDown()
                            }
                        }
                    }
                }
                drawSplinesMode.value = false
                editSplinesToggle = vToggleRow(editSplinesMode, LARGE_ROW_HEIGHT, text("Toggle splines:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                val editSplinesBlocks = ArrayList<Block>()
                val splineEditRadiusSlider = vSliderRow(editSplinesSelectionRadius, LARGE_ROW_HEIGHT, text("Selection radius:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(10..500), linearClampedScaleFunctionInverse(10..500))
                editSplinesBlocks.add(splineEditRadiusSlider)
                var splineEditRadiusSliderInitial = editSplinesSelectionRadius.value
                val splineEditRadiusSliderMouseDown = splineEditRadiusSlider.onMouseDown
                splineEditRadiusSlider.onMouseDown { a, b, c, d ->
                    splineEditRadiusSliderMouseDown?.invoke(splineEditRadiusSlider, a, b, c, d)
                    splineEditRadiusSliderInitial = editSplinesSelectionRadius.value
                }
                var splineEditSelectorMap: LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>? = null
                var splineEditSelectorMatrix: ShortArrayMatrix? = null
                val splineEditRadiusSliderMouseRelease = splineEditRadiusSlider.onMouseRelease
                splineEditRadiusSlider.onMouseRelease { a, b, c, d ->
                    splineEditRadiusSliderMouseRelease?.invoke(splineEditRadiusSlider, a, b, c, d)
                    executor.call {
                        val splineMap = splineEditSelectorMap
                        val selectorMatrix = splineEditSelectorMatrix
                        if (editSplinesSelectionRadius.value != splineEditRadiusSliderInitial && selectorMatrix != null && splineMap != null) {
                            val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, editSplinesSelectionRadius.value.toFloat()), 4096)
                            for (i in 0..16777215) {
                                var offset = i * 4
                                val r = splineSelectors[offset++].toInt() and 0x000000FF
                                val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                                selectorMatrix[i] = (r or g).toShort()
                            }
                        }
                    }
                }
                var editSplinesActivated = false
                editSplinesMode.listener { old, new ->
                    editSplinesBlocks.forEach {
                        it.isMouseAware = new
                        it.isVisible = new
                    }
                    if (old != new) {
                        executor.call {
                            if (new) {
                                disableCurrentToggleIfEnabled()
                                val currentSplines = currentState.regionSplines
                                if (currentSplines != null) {
                                    doGenerationStart()
                                    editToggleCurrentPointer.value = editSplinesMode
                                    editSplinesActivated = true
                                    val splineMap = LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>()
                                    var index = 0
                                    currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 0, pair, edges))
                                    }
                                    currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 1, pair, edges))
                                    }
                                    currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 2, pair, edges))
                                    }
                                    currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 3, points to points, edges))
                                    }
                                    currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 4, points to points, edges))
                                    }
                                    currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quadruple(index, 5, points to points, edges))
                                    }
                                    splineEditSelectorMap = splineMap
                                    if (displayMode.value != DisplayMode.MAP) {
                                        val mapTextureId = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints)
                                        meshViewport.setImage(mapTextureId)
                                        imageMode.value = 1
                                        displayMode.value = DisplayMode.MAP
                                        defaultToMap.value = true
                                    }
                                    val textureReference = ref(TextureId(-1))
                                    textureReference.listener { oldTexture, newTexture ->
                                        if (oldTexture != newTexture) {
                                            meshViewport.setImage(newTexture)
                                        }
                                    }
                                    val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, editSplinesSelectionRadius.value.toFloat()), 4096)
                                    val selectorMatrix = ShortArrayMatrix(4096) { i ->
                                        var offset = i * 4
                                        val r = splineSelectors[offset++].toInt() and 0x000000FF
                                        val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                                        (r or g).toShort()
                                    }
                                    splineEditSelectorMatrix = selectorMatrix
                                    pointPicker.value = SplinePointPicker(currentState, currentSplines, splineMap, selectorMatrix, textureReference)
                                    pickerOn.value = true
                                } else {
                                    editSplinesMode.value = false
                                }
                            } else {
                                if (editSplinesActivated) {
                                    splineEditSelectorMatrix = null
                                    pointPicker.value = null
                                    pickerOn.value = false
                                    val parameters = extractCurrentParameters()
                                    regionsBuilder.build(parameters, true, false)
                                    val currentSplines = currentState.regionSplines
                                    if (currentSplines != null) {
                                        updateSplinesHistory(currentSplines)
                                    }
                                    editSplinesActivated = false
                                    if (editToggleCurrentPointer.value == editSplinesMode) {
                                        editToggleCurrentPointer.value = null
                                    }
                                    doGenerationStop()
                                }
                                editToggleLatch.value?.countDown()
                            }
                        }
                    }
                }
                editSplinesMode.value = false
                deleteSplinesToggle = vToggleRow(deleteSplinesMode, LARGE_ROW_HEIGHT, text("Delete splines:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                val deleteSplineBlocks = ArrayList<Block>()
                val splineDeleteRadiusSlider = vSliderRow(deleteSplineSelectionRadius, LARGE_ROW_HEIGHT, text("Selection radius:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(10..500), linearClampedScaleFunctionInverse(10..500))
                deleteSplineBlocks.add(splineDeleteRadiusSlider)
                var splineDeleteRadiusSliderInitial = deleteSplineSelectionRadius.value
                val splineDeleteRadiusSliderMouseDown = splineDeleteRadiusSlider.onMouseDown
                splineDeleteRadiusSlider.onMouseDown { a, b, c, d ->
                    splineDeleteRadiusSliderMouseDown?.invoke(splineDeleteRadiusSlider, a, b, c, d)
                    splineDeleteRadiusSliderInitial = deleteSplineSelectionRadius.value
                }
                var splineDeleteSelectorMap: LinkedHashMap<Int, Quintuple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>, Boolean>>? = null
                var splineDeleteSelectorMatrix: ShortArrayMatrix? = null
                val splineDeleteRadiusSliderMouseRelease = splineDeleteRadiusSlider.onMouseRelease
                splineDeleteRadiusSlider.onMouseRelease { a, b, c, d ->
                    splineDeleteRadiusSliderMouseRelease?.invoke(splineDeleteRadiusSlider, a, b, c, d)
                    executor.call {
                        val splineMap = splineDeleteSelectorMap
                        val selectorMatrix = splineDeleteSelectorMatrix
                        if (deleteSplineSelectionRadius.value != splineDeleteRadiusSliderInitial && selectorMatrix != null && splineMap != null) {
                            val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, deleteSplineSelectionRadius.value.toFloat()), 4096)
                            for (i in 0..16777215) {
                                var offset = i * 4
                                val r = splineSelectors[offset++].toInt() and 0x000000FF
                                val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                                selectorMatrix[i] = (r or g).toShort()
                            }
                        }
                    }
                }
                var deleteSplinesActivated = false
                deleteSplinesMode.listener { old, new ->
                    deleteSplineBlocks.forEach {
                        it.isMouseAware = new
                        it.isVisible = new
                    }
                    if (old != new) {
                        executor.call {
                            if (new) {
                                disableCurrentToggleIfEnabled()
                                val currentSplines = currentState.regionSplines
                                if (currentSplines != null) {
                                    doGenerationStart()
                                    editToggleCurrentPointer.value = deleteSplinesMode
                                    deleteSplinesActivated = true
                                    val splineMap = LinkedHashMap<Int, Quintuple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>, Boolean>>()
                                    var index = 0
                                    currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quintuple(index, 0, pair, edges, false))
                                    }
                                    currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quintuple(index, 1, pair, edges, false))
                                    }
                                    currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quintuple(index, 2, pair, edges, false))
                                    }
                                    currentSplines.deletedOrigins.zip(currentSplines.deletedPoints).zip(currentSplines.deletedEdges).forEach { (pair, edges) ->
                                        index++
                                        splineMap.put(index, Quintuple(index, 2, pair, edges, true))
                                    }
                                    currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quintuple(index, 3, points to points, edges, false))
                                    }
                                    currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quintuple(index, 4, points to points, edges, false))
                                    }
                                    currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                                        index++
                                        splineMap.put(index, Quintuple(index, 5, points to points, edges, false))
                                    }
                                    splineDeleteSelectorMap = splineMap
                                    if (displayMode.value != DisplayMode.MAP) {
                                        val mapTextureId = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints)
                                        meshViewport.setImage(mapTextureId)
                                        imageMode.value = 1
                                        displayMode.value = DisplayMode.MAP
                                        defaultToMap.value = true
                                    }
                                    val textureReference = ref(TextureId(-1))
                                    textureReference.listener { oldTexture, newTexture ->
                                        if (oldTexture != newTexture) {
                                            meshViewport.setImage(newTexture)
                                        }
                                    }
                                    executor.call {
                                        textureReference.value = renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints, currentSplines.deletedPoints)
                                    }
                                    val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, 80.0f), 4096)
                                    val selectorMatrix = ShortArrayMatrix(4096) { i ->
                                        var offset = i * 4
                                        val r = splineSelectors[offset++].toInt() and 0x000000FF
                                        val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                                        (r or g).toShort()
                                    }
                                    splineDeleteSelectorMatrix = selectorMatrix
                                    pointPicker.value = SplineDeletePicker(currentState, currentSplines, splineMap, selectorMatrix, textureReference)
                                    pickerOn.value = true
                                } else {
                                    deleteSplinesMode.value = false
                                }
                            } else {
                                if (deleteSplinesActivated) {
                                    splineDeleteSelectorMatrix = null
                                    pointPicker.value = null
                                    pickerOn.value = false
                                    val parameters = extractCurrentParameters()
                                    regionsBuilder.build(parameters, true, false)
                                    val currentSplines = currentState.regionSplines
                                    if (currentSplines != null) {
                                        updateSplinesHistory(currentSplines)
                                    }
                                    deleteSplinesActivated = false
                                    if (editToggleCurrentPointer.value == deleteSplinesMode) {
                                        editToggleCurrentPointer.value = null
                                    }
                                    doGenerationStop()
                                }
                                editToggleLatch.value?.countDown()
                            }
                        }
                    }
                }
                deleteSplinesMode.value = false
                vButtonRow(LARGE_ROW_HEIGHT) {
                    disableOnGenerateButton("Clear edits", { !(currentState.regionSplines?.hasCustomizations ?: false) }) {
                        doGeneration {
                            val parameters = extractCurrentParameters()
                            currentState.regionSplines = null
                            regionsBuilder.build(parameters, true, true)
                            val currentSplines = currentState.regionSplines
                            if (currentSplines != null) {
                                updateSplinesHistory(currentSplines)
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Restore deleted", { currentState.regionSplines?.deletedOrigins?.isEmpty() ?: true }) {
                        doGeneration {
                            val parameters = extractCurrentParameters()
                            currentState.regionSplines = currentState.regionSplines?.copy(deletedOrigins = listOf(), deletedPoints = listOf(), deletedEdges = listOf())
                            regionsBuilder.build(parameters, true, true)
                            val currentSplines = currentState.regionSplines
                            if (currentSplines != null) {
                                updateSplinesHistory(currentSplines)
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Back", { historySplinesBackQueue.size == 0 }) {
                        doGeneration {
                            val historyItem = historySplinesBackQueue.pop()
                            if (historyItem != null) {
                                val historyLast = historySplinesCurrent.value
                                if (historyLast != null) {
                                    historySplinesForwardQueue.push(historyLast.copy())
                                }
                                currentState.regionSplines = historyItem
                                val parameters = currentState.parameters
                                if (parameters != null) {
                                    regionsBuilder.build(parameters, true, true)
                                }
                                historySplinesCurrent.value = historyItem.copy()
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Forward", { historySplinesForwardQueue.size == 0 }) {
                        doGeneration {
                            val historyItem = historySplinesForwardQueue.pop()
                            if (historyItem != null) {
                                val historyLast = historySplinesCurrent.value
                                if (historyLast != null) {
                                    historySplinesBackQueue.push(historyLast.copy())
                                }
                                currentState.regionSplines = historyItem
                                val parameters = currentState.parameters
                                if (parameters != null) {
                                    regionsBuilder.build(parameters, true, true)
                                }
                                historySplinesCurrent.value = historyItem.copy()
                            }
                        }
                    }
                }
                vSpacer(HALF_ROW_HEIGHT)
            }
            splinePanel.isVisible = false
            splinePanelExpanded.listeners.add(resetScrollerListener)
            val biomePanelExpanded = ref(true)
            val biomePanel = vExpandPanel("Edit biomes", expanded = biomePanelExpanded) {
                vLongInputRow(biomesSeed, LARGE_ROW_HEIGHT, text("Seed:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, uiLayout) {
                    hSpacer(SMALL_SPACER_SIZE)
                    button(text("Randomize"), NORMAL_TEXT_BUTTON_STYLE) {
                        val randomSeed = RANDOM.nextLong()
                        val randomString = randomSeed.toString()
                        if (randomString.length > 18) {
                            biomesSeed.value = randomString.substring(0, 18).toLong()
                        } else {
                            biomesSeed.value = randomSeed
                        }
                    }
                }
                vFileRowWithToggle(biomeFile, useBiomeFile, LARGE_ROW_HEIGHT, text("Region file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
                vSliderWithValueRow(biomesMapScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
                vSliderWithValueRow(biomeCount, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Biome count:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
                val biomeRows = block {
                    vAlign = VerticalAlignment.TOP
                    hAlign = HorizontalAlignment.LEFT
                    layout = Layout.VERTICAL
                    hSizing = Sizing.RELATIVE
                    vSizing = Sizing.SHRINK
                }
                biomeCount.listener { oldBiomeCount, newBiomeCount ->
                    if (oldBiomeCount != newBiomeCount) {
                        val newBiomes = ArrayList(biomes.value)
                        if (biomeRows.layoutChildren.size > newBiomeCount) {
                            for (i in 1..biomeRows.layoutChildren.size - newBiomeCount) {
                                val removeAt = biomeRows.layoutChildren.size - 1
                                biomeRows.layoutChildren.removeAt(removeAt)
                                biomeRows.renderChildren.removeAt(removeAt)
                                newBiomes.removeAt(removeAt)
                            }
                        } else if (biomeRows.layoutChildren.size < newBiomeCount) {
                            for (i in 1..newBiomeCount - biomeRows.layoutChildren.size) {
                                val index = newBiomes.size
                                val selectedValue = selectedBiomes[index]
                                biomeRows.vBiomeDropdownRow(editBiomesMode, currentBiomeBrushValue, dropdownLayer, REGION_COLORS[biomeRows.layoutChildren.size + 1], biomeValues.keys.toList(), selectedValue, index, LARGE_ROW_HEIGHT, shrinkGroup, MEDIUM_SPACER_SIZE)
                                newBiomes.add(selectedValue.value)
                                selectedValue.listener { oldBiomeId, newBiomeId ->
                                    if (oldBiomeId != newBiomeId) {
                                        val changedBiomes = ArrayList(biomes.value)
                                        changedBiomes[index] = newBiomeId
                                        biomes.value = changedBiomes
                                    }
                                }
                            }
                        }
                        biomes.value = newBiomes
                    }
                }
                biomeCount.value = 6
                editBiomesToggle = vToggleRow(editBiomesMode, LARGE_ROW_HEIGHT, text("Edit mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                val editBlocks = ArrayList<Block>()
                editBlocks.add(vSliderRow(biomeEditBrushSize, LARGE_ROW_HEIGHT, text("Brush size:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.015625f, 0.15625f), linearClampedScaleFunctionInverse(0.015625f, 0.15625f)))
                var editBiomesActivated = false
                editBiomesMode.listener { old, new ->
                    editBlocks.forEach {
                        it.isMouseAware = new
                        it.isVisible = new
                    }
                    if (old != new) {
                        executor.call {
                            if (new) {
                                disableCurrentToggleIfEnabled()
                                val currentBiomeGraph = currentState.biomeGraph
                                val currentBiomeMask = currentState.biomeMask
                                if (currentBiomeGraph != null && currentBiomeMask != null) {
                                    doGenerationStart()
                                    editToggleCurrentPointer.value = editBiomesMode
                                    editBiomesActivated = true
                                    if (displayMode.value != DisplayMode.BIOMES) {
                                        val biomeTextureId = renderRegions(currentBiomeGraph, currentBiomeMask)
                                        val currentSplines = currentState.regionSplines
                                        val splineTextureId = if (currentSplines != null) {
                                            TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
                                        } else {
                                            TextureBuilder.renderSplines(emptyList(), emptyList(), emptyList())
                                        }
                                        meshViewport.setBiomes(biomeTextureId, splineTextureId)
                                        imageMode.value = 2
                                        displayMode.value = DisplayMode.BIOMES
                                    }
                                    val textureReference = ref(TextureId(-1))
                                    textureReference.listener { oldTexture, newTexture ->
                                        if (oldTexture != newTexture) {
                                            meshViewport.setBiomes(newTexture)
                                        }
                                    }
                                    currentEditBrushSize.value = biomeEditBrushSize
                                    brushListener.value = PreSelectDrawBrushListener(currentBiomeGraph, currentBiomeMask, biomeEditBrushSize, textureReference, currentBiomeBrushValue)
                                    brushOn.value = true
                                } else {
                                    editBiomesMode.value = false
                                }
                            } else {
                                if (editBiomesActivated) {
                                    brushListener.value = null
                                    brushOn.value = false
                                    val parameters = extractCurrentParameters()
                                    buildBiomesFun(parameters, true)
                                    val currentGraph = currentState.biomeGraph
                                    val currentMask = currentState.biomeMask
                                    if (currentGraph != null && currentMask != null) {
                                        updateBiomesHistory(parameters, currentGraph, currentMask)
                                    }
                                    editBiomesActivated = false
                                    if (editToggleCurrentPointer.value == editBiomesMode) {
                                        editToggleCurrentPointer.value = null
                                    }
                                    doGenerationStop()
                                }
                                editToggleLatch.value?.countDown()
                            }
                        }
                    }
                }
                editBiomesMode.value = false
                vButtonRow(LARGE_ROW_HEIGHT) {
                    disableOnGenerateButton("Generate") {
                        doGeneration {
                            val parameters = extractCurrentParameters()
                            buildBiomesFun(parameters)
                            val currentGraph = currentState.biomeGraph
                            val currentMask = currentState.biomeMask
                            if (currentGraph != null && currentMask != null) {
                                updateBiomesHistory(parameters, currentGraph, currentMask)
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Generate random") {
                        doGeneration {
                            val randomSeed = RANDOM.nextLong()
                            val randomString = randomSeed.toString()
                            if (randomString.length > 18) {
                                biomesSeed.value = randomString.substring(0, 18).toLong()
                            } else {
                                biomesSeed.value = randomSeed
                            }
                            val parameters = extractCurrentParameters()
                            buildBiomesFun(parameters)
                            val currentGraph = currentState.biomeGraph
                            val currentMask = currentState.biomeMask
                            if (currentGraph != null && currentMask != null) {
                                updateBiomesHistory(parameters, currentGraph, currentMask)
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Back", { historyBiomesBackQueue.size == 0 }) {
                        doGeneration {
                            val historyItem = historyBiomesBackQueue.pop()
                            if (historyItem != null) {
                                val historyLast = historyBiomesCurrent.value
                                if (historyLast != null) {
                                    historyBiomesForwardQueue.push(historyLast.copy())
                                }
                                syncParameterValues(historyItem.parameters)
                                currentState.biomeGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                                currentState.biomeMask = historyItem.mask
                                buildBiomesFun(historyItem.parameters, true)
                                historyBiomesCurrent.value = historyItem.copy()
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    disableOnGenerateButton("Forward", { historyBiomesForwardQueue.size == 0 }) {
                        doGeneration {
                            val historyItem = historyBiomesForwardQueue.pop()
                            if (historyItem != null) {
                                val historyLast = historyBiomesCurrent.value
                                if (historyLast != null) {
                                    historyBiomesBackQueue.push(historyLast.copy())
                                }
                                syncParameterValues(historyItem.parameters)
                                currentState.biomeGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                                currentState.biomeMask = historyItem.mask
                                buildBiomesFun(historyItem.parameters, true)
                                historyBiomesCurrent.value = historyItem.copy()
                            }
                        }
                    }
                }
                vSpacer(HALF_ROW_HEIGHT)
            }
            biomePanel.isVisible = false
            biomePanelExpanded.listeners.add(resetScrollerListener)
            val mapDetailScaleSlider = vSliderWithValueRow(mapDetailScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map detail scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
            mapDetailScaleSlider.isVisible = false
            mapDetailScale.listener { old, new ->
                if (old != new) {
                    doGeneration {
                        currentState.mapDetailScale = mapDetailScale.value
                        currentState.heightMapTexture = null
                        currentState.riverMapTexture = null
                    }
                }
            }
            val mainButtonsRow = vButtonRow(LARGE_ROW_HEIGHT) {
                disableOnGenerateButton("Show regions", { currentState.regionGraph == null || currentState.regionMask == null || displayMode.value == DisplayMode.REGIONS }) {
                    doGeneration {
                        val currentRegionGraph = currentState.regionGraph
                        val currentRegionMask = currentState.regionMask
                        if (currentRegionGraph != null && currentRegionMask != null) {
                            val regionTextureId = renderRegions(currentRegionGraph, currentRegionMask)
                            meshViewport.setRegions(regionTextureId)
                            imageMode.value = 0
                            displayMode.value = DisplayMode.REGIONS
                            defaultToMap.value = false
                        }
                    }
                }
                disableOnGenerateButton("Show map", { currentState.regionSplines == null || displayMode.value == DisplayMode.MAP }) {
                    doGeneration {
                        val currentRegionSplines = currentState.regionSplines
                        if (currentRegionSplines != null) {
                            val regionTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
                            meshViewport.setImage(regionTextureId)
                            imageMode.value = 1
                            displayMode.value = DisplayMode.MAP
                            defaultToMap.value = true
                        }
                    }
                }
                disableOnGenerateButton("Show biomes", { currentState.biomeGraph == null || currentState.biomeMask == null || displayMode.value == DisplayMode.BIOMES }) {
                    doGeneration {
                        val currentBiomeGraph = currentState.biomeGraph
                        val currentBiomeMask = currentState.biomeMask
                        val currentSplines = currentState.regionSplines
                        if (currentBiomeGraph != null && currentBiomeMask != null) {
                            val biomeTextureId = renderRegions(currentBiomeGraph, currentBiomeMask)
                            val splineTextureId = if (currentSplines != null) {
                                TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
                            } else {
                                TextureBuilder.renderSplines(emptyList(), emptyList(), emptyList())
                            }
                            meshViewport.setBiomes(biomeTextureId, splineTextureId)
                            imageMode.value = 2
                            displayMode.value = DisplayMode.BIOMES
                        }
                    }
                }
                disableOnGenerateSet({ if (currentState.heightMapTexture != null && currentState.riverMapTexture != null) 1 else 0 },
                        disableButton("Build mesh", { currentState.regionGraph == null || currentState.regionMask == null || currentState.regionSplines == null || currentState.biomeGraph == null || currentState.biomeGraph == null }) {
                            doGeneration {
                                val currentParameters = currentState.parameters
                                val currentRegionGraph = currentState.regionGraph
                                val currentRegionMask = currentState.regionMask
                                val currentRegionSplines = currentState.regionSplines
                                val currentBiomeGraph = currentState.biomeGraph
                                val currentBiomeMask = currentState.biomeMask
                                val currentBiomes = currentState.biomes
                                val currentMapScale = currentState.mapDetailScale
                                if (currentParameters != null && currentRegionGraph != null && currentRegionMask != null && currentRegionSplines != null && currentBiomeGraph != null && currentBiomeMask != null && currentBiomes != null && currentMapScale != null) {
                                    val (heightMapTexId, riverMapTexId) = generateWaterFlows(currentParameters, currentRegionSplines, currentBiomeGraph, currentBiomeMask, currentBiomes, cachedGraph256.value, cachedGraph512.value, cachedGraph1024.value, executor, currentMapScale)
                                    meshViewport.setHeightmap(Pair(heightMapTexId, riverMapTexId), 4096)
                                    currentState.heightMapTexture = heightMapTexId
                                    currentState.riverMapTexture = riverMapTexId
                                    val linearDistanceScaleInKilometers = (((currentMapScale * currentMapScale) / 400.0f) * 990000 + 10000) / 1000
                                    heightMapScaleFactor.value = ((-Math.log10(linearDistanceScaleInKilometers - 9.0) - 1) * 28 + 122).toFloat()
                                    imageMode.value = 3
                                    displayMode.value = DisplayMode.MESH
                                }
                            }
                        },
                        disableButton("Show mesh", { displayMode.value == DisplayMode.MESH }) {
                            doGeneration {
                                val currentHeightMap = currentState.heightMapTexture
                                val currentRiverMap = currentState.riverMapTexture
                                if (currentHeightMap != null && currentRiverMap != null) {
                                    meshViewport.setHeightmap(Pair(currentHeightMap, currentRiverMap), 4096)
                                    imageMode.value = 3
                                    displayMode.value = DisplayMode.MESH
                                }
                            }
                        }
                )
            }
            mainButtonsRow.isVisible = false
            val newProjectPanel = block {
                layout = Layout.ABSOLUTE
                vSizing = Sizing.SHRINK
                vSpacer(HALF_ROW_HEIGHT)
                vButtonRow(LARGE_ROW_HEIGHT) {
                    button(text("New project"), NORMAL_TEXT_BUTTON_STYLE) {
                        newProject(overwriteWarningReference, overwriteWarningDialog, dialogCallback, noop)
                    }
                }
            }
            currentProject.listener { _, new ->
                if (new != null) {
                    newProjectPanel.isVisible = false
                    regionPanel.isVisible = true
                    splinePanel.isVisible = true
                    biomePanel.isVisible = true
                    mapDetailScaleSlider.isVisible = true
                    mainButtonsRow.isVisible = true
                    resetScroller()
                    enableGenerateButtons()
                } else {
                    regionPanel.isVisible = false
                    splinePanel.isVisible = false
                    biomePanel.isVisible = false
                    mapDetailScaleSlider.isVisible = false
                    mainButtonsRow.isVisible = false
                    newProjectPanel.isVisible = true
                    resetScroller()
                    enableGenerateButtons()
                }
            }
            enableGenerateButtons()
        }
        scroller.value.onScroll { _, y ->
            val internalScroller = scroller.value
            val scrollerHeight = internalScroller.height
            val parentHeight = internalScroller.parent.height
            if (scrollerHeight > parentHeight) {
                val diff = parentHeight - scrollerHeight
                val newOffset = internalScroller.yOffset + y * LARGE_ROW_HEIGHT * 3
                val clampedOffset = Math.max(diff.toDouble(), Math.min(0.0, newOffset)).toFloat()
                internalScroller.yOffset = clampedOffset
            } else {
                internalScroller.yOffset = 0.0f
            }
        }
        val oldWindowResize = onWindowResize
        onWindowResize = {
            oldWindowResize()
            resetScroller()
        }
    }
}

private fun disableCurrentToggleIfEnabled() {
    val currentToggle = editToggleCurrentPointer.value
    if (currentToggle != null) {
        val waiter = CountDownLatch(1)
        editToggleLatch.value = waiter
        currentToggle.value = false
        while (waiter.count > 0) {
            try {
                waiter.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        editToggleLatch.value = null
    }
}

private fun updateRegionsHistory(parameters: ParameterSet, graph: Graph, regionMask: ByteArrayMatrix) {
    val historyLast = historyRegionsCurrent.value
    if (historyLast != null) {
        if ((historyRegionsBackQueue.size == 0 || historyRegionsBackQueue.peek() != historyLast) && (parameters != historyLast.parameters || historyLast.graphSeed != graph.seed || !Arrays.equals(historyLast.mask.array, regionMask.array))) {
            historyRegionsBackQueue.push(historyLast.copy())
        }
    }
    historyRegionsForwardQueue.clear()
    historyRegionsCurrent.value = HistoryItem(parameters.copy(), graph.seed, ByteArrayMatrix(regionMask.width, regionMask.array.copyOf()))
}

private fun updateSplinesHistory(splines: RegionSplines) {
    val historyLast = historySplinesCurrent.value
    if (historyLast != null) {
        if ((historySplinesBackQueue.size == 0 || historySplinesBackQueue.peek() != historyLast)) {
            historySplinesBackQueue.push(historyLast.copy())
        }
    }
    historySplinesForwardQueue.clear()
    historySplinesCurrent.value = splines
}

private fun updateBiomesHistory(parameters: ParameterSet, graph: Graph, biomeMask: ByteArrayMatrix) {
    val historyLast = historyBiomesCurrent.value
    if (historyLast != null) {
        if ((historyBiomesBackQueue.size == 0 || historyBiomesBackQueue.peek() != historyLast) && (parameters != historyLast.parameters || historyLast.graphSeed != graph.seed || !Arrays.equals(historyLast.mask.array, biomeMask.array))) {
            historyBiomesBackQueue.push(historyLast.copy())
        }
    }
    historyBiomesForwardQueue.clear()
    historyBiomesCurrent.value = HistoryItem(parameters.copy(), graph.seed, ByteArrayMatrix(biomeMask.width, biomeMask.array.copyOf()))
}

private fun loadBiomeMaskFromImage(file: File): ByteArrayMatrix {
    val colorMap = LinkedHashMap<Int, Int>(16)
    val bufferedImage = ImageIO.read(file)
    val widthM1 = bufferedImage.width - 1
    val heightM1 = bufferedImage.height - 1
    var unknownColors = false
    for (y in 0..127) {
        for (x in 0..127) {
            val actualX = round(((x + 0.5f) / 128.0f) * widthM1)
            val actualY = round(((y + 0.5f) / 128.0f) * heightM1)
            val imageValue = bufferedImage.getRGB(actualX, actualY) and 0x00FFFFFF
            val curVal = colorMap.putIfAbsent(imageValue, colorMap.size)
            if (!unknownColors && curVal == null) {
                if (!REGION_COLOR_INTS.contains(imageValue)) {
                    unknownColors = true
                }
            }
        }
    }
    if (unknownColors) {
        colorMap.map { it.key to it.value }.sortedByDescending { it.first and 0x00FF0000 ushr 16 }.forEachIndexed { i, (first) -> colorMap[first] = i }
    } else {
        colorMap.clear()
        REGION_COLOR_INTS.forEachIndexed { i, value ->
            colorMap[value] = i - 1
        }
    }
    return ByteArrayMatrix(128) { i ->
        (colorMap[bufferedImage.getRGB(round((((i % 128) + 0.5f) / 128.0f) * widthM1), round((((i / 128) + 0.5f) / 128.0f) * heightM1)) and 0X00FFFFFF]!! and 0x00FFFFFF).toByte()
    }
}
