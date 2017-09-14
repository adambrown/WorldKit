package com.grimfox.gec

import com.grimfox.gec.brushes.PreSelectDrawBrushListener
import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import java.util.ArrayList

private val biomeValues = linkedMapOf(
        "Mountains" to 0,
        "Coastal mountains" to 1,
        "Foothills" to 2,
        "Rolling hills" to 3,
        "Plateaus" to 4,
        "Plains" to 5
)
private val biomesSeed = ref(1L)
private val biomesMapScale = ref(4)
private val biomeCount = ref(1)
private val currentBiomeBrushValue = ref(1.toByte())
private val biomeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val useBiomeFile = ref(false)
private val biomesBuilder = BiomesBuilder(biomeFile, useBiomeFile, displayMode)
private val biomes = ref(emptyList<Int>())
private val selectedBiomes = Array(16) { ref(it % biomeValues.size) }.toList()

private fun syncParameterValues(parameters: BuildContinent.BiomeParameters) {
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

private fun extractCurrentParameters(): BuildContinent.BiomeParameters {
    return BuildContinent.BiomeParameters(
            biomesSeed = biomesSeed.value,
            biomesMapScale = biomesMapScale.value,
            biomes = biomes.value)
}

fun Block.editBiomesPanel(
        biomePanelExpanded: MonitoredReference<Boolean>,
        generationLock: DisableSetLock,
        editToggleSet: ToggleSet,
        leftPanelLabelShrinkGroup: ShrinkGroup,
        ui: UserInterface, uiLayout: UiLayout, dialogLayer: Block): Block {
    val biomePanel = vExpandPanel("Edit biomes", expanded = biomePanelExpanded) {
        vSpacer(HALF_ROW_HEIGHT)
        vButtonRow(LARGE_ROW_HEIGHT) {
            generationLock.disableOnLockButton(this, "Import biomes") {
                generationLock.doWithLock {
                    val historyItem = importBiomesFile(dialogLayer, preferences, ui)
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
            hSpacer(SMALL_SPACER_SIZE)
            generationLock.disableOnLockButton(this, "Export biomes", { historyBiomesCurrent.value == null }) {
                generationLock.doWithLock {
                    exportBiomesFile(historyBiomesCurrent.value, dialogLayer, preferences, ui)
                }
            }
        }
        vSpacer(HALF_ROW_HEIGHT)
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
                            val biomeTextureId = Rendering.renderRegions(currentBiomeGraph, currentBiomeMask)
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
                        val textureReference = ref(TextureBuilder.TextureId(-1))
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
    return biomePanel
}
