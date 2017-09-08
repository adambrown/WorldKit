package com.grimfox.gec

import com.grimfox.gec.brushes.*
import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.BiomeParameters
import com.grimfox.gec.util.BuildContinent.generateWaterFlows
import com.grimfox.gec.util.Rendering.renderRegions
import org.lwjgl.glfw.GLFW
import java.util.*

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

private val leftPanelLabelShrinkGroup = hShrinkGroup()
private val biomeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val useBiomeFile = ref(false)
private val biomesSeed = ref(1L)
private val biomesMapScale = ref(4)
private val biomeCount = ref(1)
private val currentBiomeBrushValue = ref(1.toByte())
private val mapDetailScale = ref(4)

private val biomes = ref(emptyList<Int>())
private val selectedBiomes = Array(16) { ref(it % biomeValues.size) }.toList()

enum class DisplayMode { REGIONS, MAP, BIOMES, MESH }

private val biomesBuilder = BiomesBuilder(biomeFile, useBiomeFile, displayMode)
private val generationLock = DisableSetLock()
private val editToggleSet = ToggleSet(executor)

private fun syncParameterValues(parameters: BiomeParameters) {
    val randomSeed = parameters.biomesSeed
    val randomString = randomSeed.toString()
    if (randomString.length > 18) {
        biomesSeed.value = randomString.substring(0, 18).toLong()
    } else {
        biomesSeed.value = randomSeed
    }
    biomesMapScale.value = parameters.biomesMapScale
    biomes.value = parameters.biomes
    biomes.value.forEachIndexed { i, id ->
        selectedBiomes[i].value = id
    }
}

private fun extractCurrentParameters(): BiomeParameters {
    return BiomeParameters(
            biomesSeed = biomesSeed.value,
            biomesMapScale = biomesMapScale.value,
            biomes = biomes.value)
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
            val regionPanel = editRegionsPanel(regionPanelExpanded, generationLock, editToggleSet, leftPanelLabelShrinkGroup, dialogLayer, ui, uiLayout)
            regionPanel.isVisible = false
            regionPanelExpanded.listeners.add(resetScrollerListener)
            val splinePanelExpanded = ref(true)
            val splinePanel = editMapPanel(splinePanelExpanded, generationLock, editToggleSet, leftPanelLabelShrinkGroup)
            splinePanel.isVisible = false
            splinePanelExpanded.listeners.add(resetScrollerListener)
            val biomePanelExpanded = ref(true)
            val biomePanel = vExpandPanel("Edit biomes", expanded = biomePanelExpanded) {
                vLongInputRow(biomesSeed, LARGE_ROW_HEIGHT, text("Seed:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, ui, uiLayout) {
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
                vFileRowWithToggle(biomeFile, useBiomeFile, LARGE_ROW_HEIGHT, text("Region file:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
                vSliderWithValueRow(biomesMapScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map scale:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
                vSliderWithValueRow(biomeCount, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Biome count:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
                val biomeRows = block {
                    vAlign = VerticalAlignment.TOP
                    hAlign = HorizontalAlignment.LEFT
                    layout = Layout.VERTICAL
                    hSizing = Sizing.RELATIVE
                    vSizing = Sizing.SHRINK
                }
                val editBiomesMode = ref(false)
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
                                biomeRows.vBiomeDropdownRow(editBiomesMode, currentBiomeBrushValue, dropdownLayer, REGION_COLORS[biomeRows.layoutChildren.size + 1], biomeValues.keys.toList(), selectedValue, index, LARGE_ROW_HEIGHT, leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE)
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
                vToggleRow(editBiomesMode, LARGE_ROW_HEIGHT, text("Edit mode:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE)
                editToggleSet.add(editBiomesMode,
                        {
                            val currentBiomeGraph = currentState.biomeGraph
                            val currentBiomeMask = currentState.biomeMask
                            if (currentBiomeGraph != null && currentBiomeMask != null) {
                                generationLock.lock()
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
                                true
                            } else {
                                false
                            }
                        },
                        {
                            brushListener.value = null
                            brushOn.value = false
                            val parameters = extractCurrentParameters()
                            biomesBuilder.build(parameters, true)
                            val currentGraph = currentState.biomeGraph
                            val currentMask = currentState.biomeMask
                            if (currentGraph != null && currentMask != null) {
                                updateBiomesHistory(parameters, currentGraph, currentMask)
                            }
                            generationLock.unlock()
                        },
                        vSliderRow(biomeEditBrushSize, LARGE_ROW_HEIGHT, text("Brush size:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.015625f, 0.15625f), linearClampedScaleFunctionInverse(0.015625f, 0.15625f)))
                vButtonRow(LARGE_ROW_HEIGHT) {
                    generationLock.disableOnLockButton(this, "Generate") {
                        generationLock.doWithLock {
                            val parameters = extractCurrentParameters()
                            biomesBuilder.build(parameters)
                            val currentGraph = currentState.biomeGraph
                            val currentMask = currentState.biomeMask
                            if (currentGraph != null && currentMask != null) {
                                updateBiomesHistory(parameters, currentGraph, currentMask)
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    generationLock.disableOnLockButton(this, "Generate random") {
                        generationLock.doWithLock {
                            val randomSeed = RANDOM.nextLong()
                            val randomString = randomSeed.toString()
                            if (randomString.length > 18) {
                                biomesSeed.value = randomString.substring(0, 18).toLong()
                            } else {
                                biomesSeed.value = randomSeed
                            }
                            val parameters = extractCurrentParameters()
                            biomesBuilder.build(parameters)
                            val currentGraph = currentState.biomeGraph
                            val currentMask = currentState.biomeMask
                            if (currentGraph != null && currentMask != null) {
                                updateBiomesHistory(parameters, currentGraph, currentMask)
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    generationLock.disableOnLockButton(this, "Back", { historyBiomesBackQueue.size == 0 }) {
                        generationLock.doWithLock {
                            val historyItem = historyBiomesBackQueue.pop()
                            if (historyItem != null) {
                                val historyLast = historyBiomesCurrent.value
                                if (historyLast != null) {
                                    historyBiomesForwardQueue.push(historyLast.copy())
                                }
                                syncParameterValues(historyItem.parameters)
                                currentState.biomeGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                                currentState.biomeMask = historyItem.mask
                                biomesBuilder.build(historyItem.parameters, true)
                                historyBiomesCurrent.value = historyItem.copy()
                            }
                        }
                    }
                    hSpacer(SMALL_SPACER_SIZE)
                    generationLock.disableOnLockButton(this, "Forward", { historyBiomesForwardQueue.size == 0 }) {
                        generationLock.doWithLock {
                            val historyItem = historyBiomesForwardQueue.pop()
                            if (historyItem != null) {
                                val historyLast = historyBiomesCurrent.value
                                if (historyLast != null) {
                                    historyBiomesBackQueue.push(historyLast.copy())
                                }
                                syncParameterValues(historyItem.parameters)
                                currentState.biomeGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                                currentState.biomeMask = historyItem.mask
                                biomesBuilder.build(historyItem.parameters, true)
                                historyBiomesCurrent.value = historyItem.copy()
                            }
                        }
                    }
                }
                vSpacer(HALF_ROW_HEIGHT)
            }
            biomePanel.isVisible = false
            biomePanelExpanded.listeners.add(resetScrollerListener)
            val mapDetailScaleSlider = vSliderWithValueRow(mapDetailScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map detail scale:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
            mapDetailScaleSlider.isVisible = false
            mapDetailScale.listener { old, new ->
                if (old != new) {
                    generationLock.doWithLock {
                        currentState.heightMapTexture = null
                        currentState.riverMapTexture = null
                    }
                }
            }
            val mainButtonsRow = vButtonRow(LARGE_ROW_HEIGHT) {
                generationLock.disableOnLockButton(this, "Show regions", { currentState.regionGraph == null || currentState.regionMask == null || displayMode.value == DisplayMode.REGIONS }) {
                    generationLock.doWithLock {
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
                generationLock.disableOnLockButton(this, "Show map", { currentState.regionSplines == null || displayMode.value == DisplayMode.MAP }) {
                    generationLock.doWithLock {
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
                generationLock.disableOnLockButton(this, "Show biomes", { currentState.biomeGraph == null || currentState.biomeMask == null || displayMode.value == DisplayMode.BIOMES }) {
                    generationLock.doWithLock {
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
                generationLock.disableOnLockSet({ if (currentState.heightMapTexture != null && currentState.riverMapTexture != null) 1 else 0 },
                        disableButton("Build mesh", { currentState.regionGraph == null || currentState.regionMask == null || currentState.regionSplines == null || currentState.biomeGraph == null || currentState.biomeGraph == null }) {
                            generationLock.doWithLock {
                                val currentParameters = currentState.parameters
                                val currentRegionGraph = currentState.regionGraph
                                val currentRegionMask = currentState.regionMask
                                val currentRegionSplines = currentState.regionSplines
                                val currentBiomeGraph = currentState.biomeGraph
                                val currentBiomeMask = currentState.biomeMask
                                val currentBiomes = currentState.biomes
                                val currentMapScale = mapDetailScale.value
                                if (currentParameters != null && currentRegionGraph != null && currentRegionMask != null && currentRegionSplines != null && currentBiomeGraph != null && currentBiomeMask != null && currentBiomes != null) {
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
                            generationLock.doWithLock {
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
                    generationLock.enable()
                } else {
                    regionPanel.isVisible = false
                    splinePanel.isVisible = false
                    biomePanel.isVisible = false
                    mapDetailScaleSlider.isVisible = false
                    mainButtonsRow.isVisible = false
                    newProjectPanel.isVisible = true
                    resetScroller()
                    generationLock.enable()
                }
            }
            generationLock.enable()
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
