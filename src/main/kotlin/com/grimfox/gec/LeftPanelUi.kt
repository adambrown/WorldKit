package com.grimfox.gec

import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.generateWaterFlows
import com.grimfox.gec.util.Rendering.renderRegions
import org.lwjgl.glfw.GLFW

private val cachedGraph256 = preferences.cachedGraph256!!
private val cachedGraph512 = preferences.cachedGraph512!!
private val cachedGraph1024 = preferences.cachedGraph1024!!

private val leftPanelLabelShrinkGroup = hShrinkGroup()
private val mapDetailScale = ref(4)

enum class DisplayMode { REGIONS, MAP, BIOMES, MESH }

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
            val regionPanel = editRegionsPanel(regionPanelExpanded, generationLock, editToggleSet, leftPanelLabelShrinkGroup, ui, uiLayout, dialogLayer)
            regionPanel.isVisible = false
            regionPanelExpanded.listeners.add(resetScrollerListener)
            val splinePanelExpanded = ref(true)
            val splinePanel = editMapPanel(splinePanelExpanded, generationLock, editToggleSet, leftPanelLabelShrinkGroup, ui, dialogLayer)
            splinePanel.isVisible = false
            splinePanelExpanded.listeners.add(resetScrollerListener)
            val biomePanelExpanded = ref(true)
            val biomePanel = editBiomesPanel(biomePanelExpanded, generationLock, editToggleSet, leftPanelLabelShrinkGroup, ui, uiLayout, dialogLayer)
            biomePanel.isVisible = false
            biomePanelExpanded.listeners.add(resetScrollerListener)
            val mapDetailScaleSlider = vSliderWithValueRow(mapDetailScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map detail scale:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
            mapDetailScaleSlider.isVisible = false
            mapDetailScale.listener { old, new ->
                if (old != new) {
                    editToggleSet.suspend {
                        generationLock.doWithLock {
                            currentState.heightMapTexture.value = null
                            currentState.riverMapTexture.value = null
                        }
                    }
                }
            }
            val mainButtonsRow = vButtonRow(LARGE_ROW_HEIGHT) {
                generationLock.disableOnLockButton(this, "Show regions", { currentState.regionGraph.value == null || currentState.regionMask.value == null || displayMode.value == DisplayMode.REGIONS }) {
                    editToggleSet.suspend {
                        generationLock.doWithLock {
                            val currentRegionGraph = currentState.regionGraph.value
                            val currentRegionMask = currentState.regionMask.value
                            if (currentRegionGraph != null && currentRegionMask != null) {
                                val regionTextureId = renderRegions(currentRegionGraph, currentRegionMask)
                                meshViewport.setRegions(regionTextureId)
                                imageMode.value = 0
                                displayMode.value = DisplayMode.REGIONS
                                defaultToMap.value = false
                            }
                        }
                    }
                }
                generationLock.disableOnLockButton(this, "Show map", { currentState.regionSplines.value == null || displayMode.value == DisplayMode.MAP }) {
                    editToggleSet.suspend {
                        generationLock.doWithLock {
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
                }
                generationLock.disableOnLockButton(this, "Show biomes", { currentState.biomeGraph.value == null || currentState.biomeMask.value == null || displayMode.value == DisplayMode.BIOMES }) {
                    editToggleSet.suspend {
                        generationLock.doWithLock {
                            val currentBiomeGraph = currentState.biomeGraph.value
                            val currentBiomeMask = currentState.biomeMask.value
                            val currentSplines = currentState.regionSplines.value
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
                }
                generationLock.disableOnLockSet({ if (currentState.heightMapTexture.value != null && currentState.riverMapTexture.value != null) 1 else 0 },
                        disableButton("Build mesh", { currentState.regionGraph.value == null || currentState.regionMask.value == null || currentState.regionSplines.value == null || currentState.biomeGraph.value == null || currentState.biomeGraph.value == null }) {
                            editToggleSet.suspend {
                                generationLock.doWithLock {
                                    val currentParameters = currentState.regionParameters.value
                                    val currentRegionGraph = currentState.regionGraph.value
                                    val currentRegionMask = currentState.regionMask.value
                                    val currentRegionSplines = currentState.regionSplines.value
                                    val currentBiomeGraph = currentState.biomeGraph.value
                                    val currentBiomeMask = currentState.biomeMask.value
                                    val currentBiomes = currentState.biomes.value
                                    val currentMapScale = mapDetailScale.value
                                    if (currentParameters != null && currentRegionGraph != null && currentRegionMask != null && currentRegionSplines != null && currentBiomeGraph != null && currentBiomeMask != null && currentBiomes != null) {
                                        val (heightMapTexId, riverMapTexId) = generateWaterFlows(
                                                parameterSet = currentParameters,
                                                regionSplines = currentRegionSplines,
                                                biomeGraph = currentBiomeGraph,
                                                biomeMask = currentBiomeMask,
                                                biomes = currentBiomes,
                                                flowGraphSmall = cachedGraph256.value,
                                                flowGraphMedium = cachedGraph512.value,
                                                flowGraphLarge = cachedGraph1024.value,
                                                executor = executor,
                                                mapScale = currentMapScale,
                                                customElevationPowerMap = currentState.customElevationPowerMap.value,
                                                customStartingHeightsMap = currentState.customStartingHeightsMap.value,
                                                customSoilMobilityMap = currentState.customSoilMobilityMap.value)
                                        meshViewport.setHeightmap(Pair(heightMapTexId, riverMapTexId), 4096)
                                        currentState.heightMapTexture.value = heightMapTexId
                                        currentState.riverMapTexture.value = riverMapTexId
                                        val linearDistanceScaleInKilometers = (((currentMapScale * currentMapScale) / 400.0f) * 990000 + 10000) / 1000
                                        heightMapScaleFactor.value = ((-Math.log10(linearDistanceScaleInKilometers - 9.0) - 1) * 28 + 122).toFloat()
                                        imageMode.value = 3
                                        displayMode.value = DisplayMode.MESH
                                    }
                                }
                            }
                        },
                        disableButton("Show mesh", { displayMode.value == DisplayMode.MESH }) {
                            editToggleSet.suspend {
                                generationLock.doWithLock {
                                    val currentHeightMap = currentState.heightMapTexture.value
                                    val currentRiverMap = currentState.riverMapTexture.value
                                    if (currentHeightMap != null && currentRiverMap != null) {
                                        meshViewport.setHeightmap(Pair(currentHeightMap, currentRiverMap), 4096)
                                        imageMode.value = 3
                                        displayMode.value = DisplayMode.MESH
                                    }
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

