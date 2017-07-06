package com.grimfox.gec

import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.HistoryQueue
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.ParameterSet
import com.grimfox.gec.util.BuildContinent.buildBiomeMaps
import com.grimfox.gec.util.BuildContinent.generateRegionSplines
import com.grimfox.gec.util.BuildContinent.generateRegions
import com.grimfox.gec.util.BuildContinent.generateWaterFlows
import org.lwjgl.glfw.GLFW
import java.io.File
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO


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

private val shrinkGroup = hShrinkGroup()
private val regionFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val biomeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val useRegionFile = ref(false)
private val useBiomeFile = ref(false)
private val allowUnsafe = ref(false)
private val regionsSeed = ref(1L)
private val biomesSeed = ref(1L)
private val regionsMapScale = ref(4)
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
private val historyRegionsBackQueue = HistoryQueue<ParameterSet>(100)
private val historyRegionsCurrent = ref<ParameterSet?>(null)
private val historyRegionsForwardQueue = HistoryQueue<ParameterSet>(100)
private val historyBiomesBackQueue = HistoryQueue<ParameterSet>(100)
private val historyBiomesCurrent = ref<ParameterSet?>(null)
private val historyBiomesForwardQueue = HistoryQueue<ParameterSet>(100)
private val onUpdateParamsFun = {
    if (!allowUnsafe.value) {
        stride.value = ((regions.value + 3) / 4) + 5
        val stride2 = stride.value * stride.value
        val approxInland = stride2 - (4 * stride.value) + 4
        startPoints.value = Math.min(6, Math.max(1, Math.floor(approxInland / (regions.value.toDouble() + (islands.value / 2))).toInt() - 1))
        reduction.value = Math.floor((approxInland - (startPoints.value * regions.value)) * 0.8).toInt()
        connectedness.value = Math.round(((1.0f / stride.value) * 0.77f) * 1000.0f) / 1000.0f
        val unitLength = 1.0f / stride.value
        val unit2 = unitLength * unitLength
        regionSize.value = Math.round((((unit2 * (approxInland - reduction.value)) / regions.value) * 0.763f) * 1000.0f) / 1000.0f
    }
}
private val generateLock = ReentrantLock()
private var generating = false
private var generateRegionsButton = NO_BLOCK
private var generateRegionsLabel = NO_BLOCK
private var generateRandomRegionsButton = NO_BLOCK
private var generateRandomRegionsLabel = NO_BLOCK
private var buildButton = NO_BLOCK
private var buildLabel = NO_BLOCK
private var backRegionsButton = NO_BLOCK
private var forwardRegionsButton = NO_BLOCK
private var backRegionsLabel = NO_BLOCK
private var forwardRegionsLabel = NO_BLOCK
private var showRegionsButton = NO_BLOCK
private var showRegionsLabel = NO_BLOCK
private var showBiomesButton = NO_BLOCK
private var showBiomesLabel = NO_BLOCK
private var showMapButton = NO_BLOCK
private var showMapLabel = NO_BLOCK
private var showMeshButton = NO_BLOCK
private var showMeshLabel = NO_BLOCK
private var generateBiomesButton = NO_BLOCK
private var generateBiomesLabel = NO_BLOCK
private var generateRandomBiomesButton = NO_BLOCK
private var generateRandomBiomesLabel = NO_BLOCK
private var backBiomesButton = NO_BLOCK
private var forwardBiomesButton = NO_BLOCK
private var backBiomesLabel = NO_BLOCK
private var forwardBiomesLabel = NO_BLOCK

private val biomes = ref(emptyList<Int>())
private val selectedBiomes = Array(16) { ref(it % biomeValues.size) }.toList()

private enum class DisplayMode { REGIONS, MAP, BIOMES, MESH }

private var displayMode = DisplayMode.MAP
private var defaultToMap = true

fun disableGenerateButtons() {
    generateRegionsButton.isVisible = false
    generateRegionsLabel.isVisible = true
    generateRandomRegionsButton.isVisible = false
    generateRandomRegionsLabel.isVisible = true
    backRegionsButton.isVisible = false
    backRegionsLabel.isVisible = true
    forwardRegionsButton.isVisible = false
    forwardRegionsLabel.isVisible = true
    generateBiomesButton.isVisible = false
    generateBiomesLabel.isVisible = true
    generateRandomBiomesButton.isVisible = false
    generateRandomBiomesLabel.isVisible = true
    backBiomesButton.isVisible = false
    backBiomesLabel.isVisible = true
    forwardBiomesButton.isVisible = false
    forwardBiomesLabel.isVisible = true
    showRegionsButton.isVisible = false
    showRegionsLabel.isVisible = true
    showBiomesButton.isVisible = false
    showBiomesLabel.isVisible = true
    showMapButton.isVisible = false
    val displayBuildLabel = buildButton.isVisible || buildLabel.isVisible
    showMapLabel.isVisible = true
    showMeshButton.isVisible = false
    showMeshLabel.isVisible = !displayBuildLabel
    buildButton.isVisible = false
    buildLabel.isVisible = displayBuildLabel
}

private fun enableGenerateButtons() {
    generateRegionsLabel.isVisible = false
    generateRegionsButton.isVisible = true
    generateRandomRegionsLabel.isVisible = false
    generateRandomRegionsButton.isVisible = true
    backRegionsButton.isVisible = historyRegionsBackQueue.size != 0
    backRegionsLabel.isVisible = historyRegionsBackQueue.size == 0
    forwardRegionsButton.isVisible = historyRegionsForwardQueue.size != 0
    forwardRegionsLabel.isVisible = historyRegionsForwardQueue.size == 0
    generateBiomesLabel.isVisible = false
    generateBiomesButton.isVisible = true
    generateRandomBiomesLabel.isVisible = false
    generateRandomBiomesButton.isVisible = true
    backBiomesButton.isVisible = historyBiomesBackQueue.size != 0
    backBiomesLabel.isVisible = historyBiomesBackQueue.size == 0
    forwardBiomesButton.isVisible = historyBiomesForwardQueue.size != 0
    forwardBiomesLabel.isVisible = historyBiomesForwardQueue.size == 0
    val hasRegions = currentState.regionGraph != null && currentState.regionMask != null && displayMode != DisplayMode.REGIONS
    showRegionsButton.isVisible = hasRegions
    showRegionsLabel.isVisible = !hasRegions
    val hasBiomes = currentState.biomeGraph != null && currentState.biomeMask != null && displayMode != DisplayMode.BIOMES
    showBiomesButton.isVisible = hasBiomes
    showBiomesLabel.isVisible = !hasBiomes
    val hasRegionSplines = currentState.regionSplines != null && displayMode != DisplayMode.MAP
    showMapButton.isVisible = hasRegionSplines
    showMapLabel.isVisible = !hasRegionSplines
    val hasMesh = currentState.heightMapTexture != null && currentState.riverMapTexture != null
    val hasDataForMeshBuilding = currentState.regionGraph != null && currentState.regionMask != null && currentState.regionSplines != null && currentState.biomeGraph != null && currentState.biomeGraph != null
    if (hasMesh) {
        if (displayMode != DisplayMode.MESH) {
            showMeshLabel.isVisible = false
            buildLabel.isVisible = false
            buildButton.isVisible = false
            showMeshButton.isVisible = true
        } else {
            showMeshButton.isVisible = false
            buildLabel.isVisible = false
            buildButton.isVisible = false
            showMeshLabel.isVisible = true
        }
    } else {
        if (hasDataForMeshBuilding) {
            showMeshLabel.isVisible = false
            showMeshButton.isVisible = false
            buildLabel.isVisible = false
            buildButton.isVisible = true
        } else {
            showMeshLabel.isVisible = false
            showMeshButton.isVisible = false
            buildButton.isVisible = false
            buildLabel.isVisible = true
        }
    }
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

private fun buildRegionsFun(parameters: ParameterSet) {
    val finalRegionFile = regionFile.reference.value
    val (regionGraph, regionMask) = if (useRegionFile.value && finalRegionFile.isNotBlank()) {
        val mask = loadRegionMaskFromImage(File(finalRegionFile))
        val graph = Graphs.generateGraph(128, Random(parameters.regionsSeed), 0.8)
        Pair(graph, mask)
    } else {
        generateRegions(parameters.copy(), executor)
    }
    val regionSplines = generateRegionSplines(Random(parameters.regionsSeed), regionGraph, regionMask, parameters.regionsMapScale)
    currentState.parameters = parameters.copy()
    currentState.regionGraph = regionGraph
    currentState.regionMask = regionMask
    currentState.regionSplines = regionSplines
    currentState.meshScale = parameters.regionsMapScale
    currentState.heightMapTexture = null
    currentState.riverMapTexture = null
    if (displayMode == DisplayMode.MAP || (defaultToMap && displayMode != DisplayMode.REGIONS)) {
        val regionTextureId = TextureBuilder.renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints, regionSplines.mountainPoints)
        meshViewport.setImage(regionTextureId)
        imageMode.value = 1
        displayMode = DisplayMode.MAP
    } else {
        val regionTextureId = Rendering.renderRegions(regionGraph, regionMask)
        meshViewport.setRegions(regionTextureId)
        imageMode.value = 0
        displayMode = DisplayMode.REGIONS
    }
}

private fun buildBiomesFun(parameters: ParameterSet) {
    val finalBiomeFile = biomeFile.reference.value
    val (biomeGraph, biomeMask) = if (useBiomeFile.value && finalBiomeFile.isNotBlank()) {
        val mask = loadBiomeMaskFromImage(File(finalBiomeFile))
        for (i in 0..mask.size.toInt() - 1) {
            mask[i] = ((mask[i].toInt() % parameters.biomes.size) + 1).toByte()
        }
        val graph = Graphs.generateGraph(128, Random(parameters.biomesSeed), 0.8)
        Pair(graph, mask)
    } else {
        val scale = ((parameters.biomesMapScale * parameters.biomesMapScale) / 400.0f).coerceIn(0.0f, 1.0f)
        val biomeScale = Math.round(scale * 18) + 10
        val graph = Graphs.generateGraph(128, Random(parameters.biomesSeed), 0.8)
        buildBiomeMaps(executor, parameters.biomesSeed, graph, parameters.biomes.size, biomeScale)
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
    val biomeTextureId = Rendering.renderRegions(biomeGraph, biomeMask)
    meshViewport.setRegions(biomeTextureId)
    imageMode.value = 0
    displayMode = DisplayMode.BIOMES
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
    regionsMapScale.value = parameters.regionsMapScale
    biomesMapScale.value = parameters.biomesMapScale
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

private fun Block.leftPanelWidgets(ui: UserInterface, uiLayout: UiLayout, dialogLayer: Block) {
    onUpdateParamsFun()
    regions.listener { _, _ -> onUpdateParamsFun() }
    islands.listener { _, _ -> onUpdateParamsFun() }
    block {
        hSizing = Sizing.GROW
        layout = Layout.HORIZONTAL
        vExpandPanel("Region parameters", expanded = true) {
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
            vSliderWithValueRow(regionsMapScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
            vSliderWithValueRow(regions, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Regions:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
            vSliderWithValueRow(islands, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Island strength:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..5), linearClampedScaleFunctionInverse(0..5))
            vSliderRow(iterations, LARGE_ROW_HEIGHT, text("Iterations:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(2..30), linearClampedScaleFunctionInverse(2..30))
            vToggleRow(allowUnsafe, LARGE_ROW_HEIGHT, text("Unsafe mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
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
            vButtonRow(LARGE_ROW_HEIGHT) {
                generateRegionsButton = button(text("Generate"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = ParameterSet(
                                regionsSeed = regionsSeed.value,
                                biomesSeed = biomesSeed.value,
                                regionsMapScale = regionsMapScale.value,
                                biomesMapScale = biomesMapScale.value,
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
                        buildRegionsFun(parameters)
                        val historyLast = historyRegionsCurrent.value
                        if (historyLast != null) {
                            if ((historyRegionsBackQueue.size == 0 || historyRegionsBackQueue.peek() != historyLast) && parameters != historyLast) {
                                historyRegionsBackQueue.push(historyLast.copy())
                            }
                        }
                        historyRegionsForwardQueue.clear()
                        historyRegionsCurrent.value = parameters.copy()
                    }
                }
                generateRegionsLabel = button(text("Generate"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateRegionsLabel.isMouseAware = false
                generateRegionsLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                generateRandomRegionsButton = button(text("Generate random"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val randomSeed = RANDOM.nextLong()
                        val randomString = randomSeed.toString()
                        if (randomString.length > 18) {
                            regionsSeed.value = randomString.substring(0, 18).toLong()
                        } else {
                            regionsSeed.value = randomSeed
                        }
                        val parameters = ParameterSet(
                                regionsSeed = regionsSeed.value,
                                biomesSeed = biomesSeed.value,
                                regionsMapScale = regionsMapScale.value,
                                biomesMapScale = biomesMapScale.value,
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
                        buildRegionsFun(parameters)
                        val historyLast = historyRegionsCurrent.value
                        if (historyLast != null) {
                            if ((historyRegionsBackQueue.size == 0 || historyRegionsBackQueue.peek() != historyLast) && parameters != historyLast) {
                                historyRegionsBackQueue.push(historyLast.copy())
                            }
                        }
                        historyRegionsForwardQueue.clear()
                        historyRegionsCurrent.value = parameters.copy()
                    }
                }
                generateRandomRegionsLabel = button(text("Generate random"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateRandomRegionsLabel.isMouseAware = false
                generateRandomRegionsLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                backRegionsButton = button(text("Back"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = historyRegionsBackQueue.pop()
                        if (parameters != null) {
                            val historyLast = historyRegionsCurrent.value
                            if (historyLast != null) {
                                historyRegionsForwardQueue.push(historyLast.copy())
                            }
                            syncParameterValues(parameters)
                            buildRegionsFun(parameters)
                            historyRegionsCurrent.value = parameters.copy()
                        }
                    }
                }
                backRegionsLabel = button(text("Back"), DISABLED_TEXT_BUTTON_STYLE) {}
                backRegionsLabel.isMouseAware = false
                hSpacer(SMALL_SPACER_SIZE)
                forwardRegionsButton = button(text("Forward"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = historyRegionsForwardQueue.pop()
                        if (parameters != null) {
                            val historyLast = historyRegionsCurrent.value
                            if (historyLast != null) {
                                historyRegionsBackQueue.push(historyLast.copy())
                            }
                            syncParameterValues(parameters)
                            buildRegionsFun(parameters)
                            historyRegionsCurrent.value = parameters.copy()
                        }
                    }
                }
                forwardRegionsLabel = button(text("Forward"), DISABLED_TEXT_BUTTON_STYLE) {}
                forwardRegionsLabel.isMouseAware = false
                backRegionsButton.isVisible = false
                forwardRegionsButton.isVisible = false
            }
            vSpacer(HALF_ROW_HEIGHT)
        }
        vExpandPanel("Biome parameters", expanded = true) {
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
                            biomeRows.vDropdownRow(dropdownLayer, REGION_COLORS[biomeRows.layoutChildren.size + 1], biomeValues.keys.toList(), selectedValue, LARGE_ROW_HEIGHT, shrinkGroup, MEDIUM_SPACER_SIZE)
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
            vButtonRow(LARGE_ROW_HEIGHT) {
                generateBiomesButton = button(text("Generate"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = ParameterSet(
                                regionsSeed = regionsSeed.value,
                                biomesSeed = biomesSeed.value,
                                regionsMapScale = regionsMapScale.value,
                                biomesMapScale = biomesMapScale.value,
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
                        buildBiomesFun(parameters)
                        val historyLast = historyBiomesCurrent.value
                        if (historyLast != null) {
                            if ((historyBiomesBackQueue.size == 0 || historyBiomesBackQueue.peek() != historyLast) && parameters != historyLast) {
                                historyBiomesBackQueue.push(historyLast.copy())
                            }
                        }
                        historyBiomesForwardQueue.clear()
                        historyBiomesCurrent.value = parameters.copy()
                    }
                }
                generateBiomesLabel = button(text("Generate"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateBiomesLabel.isMouseAware = false
                generateBiomesLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                generateRandomBiomesButton = button(text("Generate random"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val randomSeed = RANDOM.nextLong()
                        val randomString = randomSeed.toString()
                        if (randomString.length > 18) {
                            biomesSeed.value = randomString.substring(0, 18).toLong()
                        } else {
                            biomesSeed.value = randomSeed
                        }
                        val parameters = ParameterSet(
                                regionsSeed = regionsSeed.value,
                                biomesSeed = biomesSeed.value,
                                regionsMapScale = regionsMapScale.value,
                                biomesMapScale = biomesMapScale.value,
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
                        buildBiomesFun(parameters)
                        val historyLast = historyBiomesCurrent.value
                        if (historyLast != null) {
                            if ((historyBiomesBackQueue.size == 0 || historyBiomesBackQueue.peek() != historyLast) && parameters != historyLast) {
                                historyBiomesBackQueue.push(historyLast.copy())
                            }
                        }
                        historyBiomesForwardQueue.clear()
                        historyBiomesCurrent.value = parameters.copy()
                    }
                }
                generateRandomBiomesLabel = button(text("Generate random"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateRandomBiomesLabel.isMouseAware = false
                generateRandomBiomesLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                backBiomesButton = button(text("Back"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = historyBiomesBackQueue.pop()
                        if (parameters != null) {
                            val historyLast = historyBiomesCurrent.value
                            if (historyLast != null) {
                                historyBiomesForwardQueue.push(historyLast.copy())
                            }
                            syncParameterValues(parameters)
                            buildBiomesFun(parameters)
                            historyBiomesCurrent.value = parameters.copy()
                        }
                    }
                }
                backBiomesLabel = button(text("Back"), DISABLED_TEXT_BUTTON_STYLE) {}
                backBiomesLabel.isMouseAware = false
                hSpacer(SMALL_SPACER_SIZE)
                forwardBiomesButton = button(text("Forward"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = historyBiomesForwardQueue.pop()
                        if (parameters != null) {
                            val historyLast = historyBiomesCurrent.value
                            if (historyLast != null) {
                                historyBiomesBackQueue.push(historyLast.copy())
                            }
                            syncParameterValues(parameters)
                            buildBiomesFun(parameters)
                            historyBiomesCurrent.value = parameters.copy()
                        }
                    }
                }
                forwardBiomesLabel = button(text("Forward"), DISABLED_TEXT_BUTTON_STYLE) {}
                forwardBiomesLabel.isMouseAware = false
                backBiomesButton.isVisible = false
                forwardBiomesButton.isVisible = false
            }
            vSpacer(HALF_ROW_HEIGHT)
        }
        vButtonRow(LARGE_ROW_HEIGHT) {
            showRegionsButton = button(text("Show regions"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentRegionGraph = currentState.regionGraph
                    val currentRegionMask = currentState.regionMask
                    if (currentRegionGraph != null && currentRegionMask != null) {
                        val regionTextureId = Rendering.renderRegions(currentRegionGraph, currentRegionMask)
                        meshViewport.setRegions(regionTextureId)
                        imageMode.value = 0
                        displayMode = DisplayMode.REGIONS
                        defaultToMap = false
                    }
                }
            }
            showRegionsLabel = button(text("Show regions"), DISABLED_TEXT_BUTTON_STYLE) {}
            showRegionsLabel.isMouseAware = false
            showRegionsLabel.isVisible = false
            showMapButton = button(text("Show map"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentRegionSplines = currentState.regionSplines
                    if (currentRegionSplines != null) {
                        val regionTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints, currentRegionSplines.mountainPoints)
                        meshViewport.setImage(regionTextureId)
                        imageMode.value = 1
                        displayMode = DisplayMode.MAP
                        defaultToMap = true
                    }
                }
            }
            showMapLabel = button(text("Show map"), DISABLED_TEXT_BUTTON_STYLE) {}
            showMapLabel.isMouseAware = false
            showMapLabel.isVisible = false
            showBiomesButton = button(text("Show biomes"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentBiomeGraph = currentState.biomeGraph
                    val currentBiomeMask = currentState.biomeMask
                    if (currentBiomeGraph != null && currentBiomeMask != null) {
                        val regionTextureId = Rendering.renderRegions(currentBiomeGraph, currentBiomeMask)
                        meshViewport.setRegions(regionTextureId)
                        imageMode.value = 0
                        displayMode = DisplayMode.BIOMES
                    }
                }
            }
            showBiomesLabel = button(text("Show biomes"), DISABLED_TEXT_BUTTON_STYLE) {}
            showBiomesLabel.isMouseAware = false
            showBiomesLabel.isVisible = false
            buildButton = button(text("Build mesh"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentParameters = currentState.parameters
                    val currentRegionGraph = currentState.regionGraph
                    val currentRegionMask = currentState.regionMask
                    val currentRegionSplines = currentState.regionSplines
                    val currentBiomeGraph = currentState.biomeGraph
                    val currentBiomeMask = currentState.biomeMask
                    val currentBiomes = currentState.biomes
                    val currentMapScale = currentState.meshScale
                    if (currentParameters != null && currentRegionGraph != null && currentRegionMask != null && currentRegionSplines != null && currentBiomeGraph != null && currentBiomeMask != null && currentBiomes != null && currentMapScale != null) {
                        val (heightMapTexId, riverMapTexId) = generateWaterFlows(currentParameters, currentRegionSplines, currentBiomeGraph, currentBiomeMask, currentBiomes, cachedGraph256.value, cachedGraph512.value, cachedGraph1024.value, executor, currentMapScale)
                        meshViewport.setHeightmap(Pair(heightMapTexId, riverMapTexId), 4096)
                        currentState.heightMapTexture = heightMapTexId
                        currentState.riverMapTexture = riverMapTexId
                        val linearDistanceScaleInKilometers = (((currentMapScale * currentMapScale) / 400.0f) * 990000 + 10000) / 1000
                        heightMapScaleFactor.value = ((-Math.log10(linearDistanceScaleInKilometers - 9.0) - 1) * 28 + 122).toFloat()
                        imageMode.value = 2
                        displayMode = DisplayMode.MESH
                    }
                }
            }
            buildLabel = button(text("Build mesh"), DISABLED_TEXT_BUTTON_STYLE) {}
            buildLabel.isMouseAware = false
            buildLabel.isVisible = false
            showMeshButton = button(text("Show mesh"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentHeightMap = currentState.heightMapTexture
                    val currentRiverMap = currentState.riverMapTexture
                    if (currentHeightMap != null && currentRiverMap != null) {
                        meshViewport.setHeightmap(Pair(currentHeightMap, currentRiverMap), 4096)
                        imageMode.value = 2
                        displayMode = DisplayMode.MESH
                    }
                }
            }
            showMeshLabel = button(text("Show mesh"), DISABLED_TEXT_BUTTON_STYLE) {}
            showMeshLabel.isMouseAware = false
            showMeshLabel.isVisible = false
        }
        enableGenerateButtons()
    }
}

private fun loadRegionMaskFromImage(file: File): Matrix<Byte> {
    val colorMap = LinkedHashMap<Int, Int>(16)
    colorMap[0] = 0
    val bufferedImage = ImageIO.read(file)
    val widthM1 = bufferedImage.width - 1
    val heightM1 = bufferedImage.height - 1
    var unknownColors = false
    for (y in 0..127) {
        for (x in 0..127) {
            val actualX = Math.round(((x + 0.5f) / 128.0f) * widthM1)
            val actualY = Math.round(((y + 0.5f) / 128.0f) * heightM1)
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
            colorMap[value] = i
        }
    }
    return ByteArrayMatrix(128) { i ->
        (colorMap[bufferedImage.getRGB(Math.round((((i % 128) + 0.5f) / 128.0f) * widthM1), Math.round((((i / 128) + 0.5f) / 128.0f) * heightM1)) and 0X00FFFFFF]!! and 0x00FFFFFF).toByte()
    }
}

private fun loadBiomeMaskFromImage(file: File): Matrix<Byte> {
    val colorMap = LinkedHashMap<Int, Int>(16)
    val bufferedImage = ImageIO.read(file)
    val widthM1 = bufferedImage.width - 1
    val heightM1 = bufferedImage.height - 1
    var unknownColors = false
    for (y in 0..127) {
        for (x in 0..127) {
            val actualX = Math.round(((x + 0.5f) / 128.0f) * widthM1)
            val actualY = Math.round(((y + 0.5f) / 128.0f) * heightM1)
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
        (colorMap[bufferedImage.getRGB(Math.round((((i % 128) + 0.5f) / 128.0f) * widthM1), Math.round((((i / 128) + 0.5f) / 128.0f) * heightM1)) and 0X00FFFFFF]!! and 0x00FFFFFF).toByte()
    }
}
