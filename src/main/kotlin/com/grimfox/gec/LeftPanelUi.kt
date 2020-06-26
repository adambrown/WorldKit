package com.grimfox.gec

import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.generateWaterFlows
import com.grimfox.gec.util.Rendering.renderRegions
import org.lwjgl.glfw.GLFW
import java.util.*
import java.util.concurrent.CancellationException

private val cachedGraph128 = preferences.cachedGraph128!!
private val cachedGraph256 = preferences.cachedGraph256!!
private val cachedGraph512 = preferences.cachedGraph512!!
private val cachedGraph1024 = preferences.cachedGraph1024!!
private val cachedGraph2048 = preferences.cachedGraph1024!!

private val leftPanelLabelShrinkGroup = hShrinkGroup()

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
            doOnMainThread {
                val scrollerInternal = scroller.value
                scrollerInternal.clearPositionAndSize()
                scrollerInternal.onScroll?.invoke(scrollerInternal, 0.0, 0.0)
            }
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
            val regionPanel = editRegionsPanel(regionPanelExpanded, generationLock, editToggleSet, leftPanelLabelShrinkGroup, scroller, ui, uiLayout, dialogLayer)
            regionPanel.isVisible = false
            regionPanelExpanded.addListener(resetScrollerListener)
            val splinePanelExpanded = ref(true)
            val splinePanel = editMapPanel(splinePanelExpanded, generationLock, editToggleSet, leftPanelLabelShrinkGroup, scroller, ui, dialogLayer)
            splinePanel.isVisible = false
            splinePanelExpanded.addListener(resetScrollerListener)
            val biomePanelExpanded = ref(true)
            val biomePanel = editBiomesPanel(biomePanelExpanded, generationLock, editToggleSet, leftPanelLabelShrinkGroup, scroller, ui, uiLayout, dialogLayer)
            biomePanel.isVisible = false
            biomePanelExpanded.addListener(resetScrollerListener)
            val mapDetailScaleSlider = vSliderWithValueRow(mapDetailScale, 6, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map detail scale:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(mapDetailScales.indices), linearClampedScaleFunctionInverse(mapDetailScales.indices)) {
                "${mapDetailScales[it]} km"
            }
            mapDetailScaleSlider.isVisible = false
            mapDetailScale.addListener { old, new ->
                if (old != new) {
                    editToggleSet.suspend {
                        generationLock.doWithLock {
                            currentState.heightMapTexture.value = null
                            currentState.normalAoMapTexture.value = null
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
                                val regionTextureId = renderRegions(VIEWPORT_TEXTURE_SIZE, currentRegionGraph, currentRegionMask)
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
                                val regionTextureId = TextureBuilder.renderMapImage(VIEWPORT_TEXTURE_SIZE, currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
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
                                val biomeTextureId = renderRegions(VIEWPORT_TEXTURE_SIZE, currentBiomeGraph, currentBiomeMask)
                                val splineTextureId = if (currentSplines != null) {
                                    TextureBuilder.renderSplines(VIEWPORT_TEXTURE_SIZE, currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
                                } else {
                                    TextureBuilder.renderSplines(VIEWPORT_TEXTURE_SIZE, emptyList(), emptyList(), emptyList())
                                }
                                meshViewport.setBiomes(biomeTextureId, splineTextureId)
                                imageMode.value = 2
                                displayMode.value = DisplayMode.BIOMES
                            }
                        }
                    }
                }
                generationLock.disableOnLockButton(this, "Show mesh", { currentState.heightMapTexture.value != null && currentState.riverMapTexture.value != null && currentState.normalAoMapTexture.value != null && displayMode.value == DisplayMode.MESH }) {
                    editToggleSet.suspend {
                        generationLock.doWithLock {
                            val currentHeightMap = currentState.heightMapTexture.value
                            val currentRiverMap = currentState.riverMapTexture.value
                            val currentNormalAoMap = currentState.normalAoMapTexture.value
                            if (currentHeightMap != null && currentRiverMap != null) {
                                meshViewport.setHeightmap(Triple(currentHeightMap, currentRiverMap, currentNormalAoMap), VIEWPORT_HEIGHTMAP_SIZE)
                                imageMode.value = 3
                                displayMode.value = DisplayMode.MESH
                            }
                        }
                    }
                }
            }
            mainButtonsRow.isVisible = false
            val outputQualitySlider = vSliderWithValueRow(outputQuality, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Output quality:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..9), linearClampedScaleFunctionInverse(1..9))
            outputQualitySlider.isVisible = false
            mapDetailScale.addListener { old, new ->
                if (old != new) {
                    editToggleSet.suspend {
                        generationLock.doWithLock {
                            currentState.heightMapTexture.value = null
                            currentState.normalAoMapTexture.value = null
                            currentState.riverMapTexture.value = null
                        }
                    }
                }
            }
            val buildButtonsRow = vButtonRow(LARGE_ROW_HEIGHT) {
                generationLock.disableOnLockButton(this, "Build mesh", { currentState.regionGraph.value == null || currentState.regionMask.value == null || currentState.regionSplines.value == null || currentState.biomeGraph.value == null || currentState.biomeGraph.value == null }) {
                    addBuildButton(dialogLayer, outputQuality.value - 1)
                }
            }
            buildButtonsRow.isVisible = false
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
            currentProject.addListener { _, new ->
                if (new != null) {
                    newProjectPanel.isVisible = false
                    regionPanel.isVisible = true
                    splinePanel.isVisible = true
                    biomePanel.isVisible = true
                    mapDetailScaleSlider.isVisible = true
                    outputQualitySlider.isVisible = true
                    mainButtonsRow.isVisible = true
                    buildButtonsRow.isVisible = true
                    resetScroller()
                    generationLock.enable()
                } else {
                    regionPanel.isVisible = false
                    splinePanel.isVisible = false
                    biomePanel.isVisible = false
                    mapDetailScaleSlider.isVisible = false
                    outputQualitySlider.isVisible = false
                    mainButtonsRow.isVisible = false
                    buildButtonsRow.isVisible = false
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

private fun Block.addBuildButton(dialogLayer: Block, level: Int) {
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
                dialogLayer.isVisible = true
                generatingPrimaryMessage.reference.value = text("Generating mesh... 0:00", TEXT_STYLE_LARGE_MESSAGE)
                generatingSecondaryMessage.reference.value = text("Press ESC to cancel.", TEXT_STYLE_SMALL_MESSAGE)
                generatingMessageBlock.isVisible = true
                val startTime = System.currentTimeMillis()
                val generationTimer = Timer(true)
                generationTimer.schedule(object : TimerTask() {
                    override fun run() {
                        val currentTime = System.currentTimeMillis()
                        val elapsedTime = (currentTime - startTime)
                        val seconds = String.format("%02d", (elapsedTime / 1000).toInt() % 60)
                        val minutes = (elapsedTime / (1000 * 60) % 60).toInt()
                        generatingPrimaryMessage.reference.value = text("Generating mesh... $minutes:$seconds", TEXT_STYLE_LARGE_MESSAGE)
                        root.movedOrResized = true
                    }
                }, 1000, 1000)
                try {
                    val canceled = ref(false)
                    cancelCurrentRunningTask.value = canceled
                    try {
                        val (heightMapTexId, riverMapTexId, normalAoTexId) = generateWaterFlows(
                                parameterSet = currentParameters,
                                regionSplines = currentRegionSplines,
                                biomeGraph = currentBiomeGraph,
                                biomeMask = currentBiomeMask,
                                biomes = currentBiomes,
                                flowGraph1 = cachedGraph256.value,
                                flowGraph2 = cachedGraph512.value,
                                flowGraph3 = cachedGraph1024.value,
                                flowGraph4 = cachedGraph2048.value,
                                executor = executor,
                                mapScale = currentMapScale,
                                customElevationPowerMap = currentState.customElevationPowerMap.value,
                                customStartingHeightsMap = currentState.customStartingHeightsMap.value,
                                customSoilMobilityMap = currentState.customSoilMobilityMap.value,
                                canceled = canceled,
                                biomeTemplates = BIOME_TEMPLATES_REF.value!!,
                                renderLevel = level,
                                colorHeightScaleFactor = colorHeightScaleFactor)
                        meshViewport.setHeightmap(Triple(heightMapTexId, riverMapTexId, normalAoTexId), VIEWPORT_HEIGHTMAP_SIZE)
                        currentState.heightMapTexture.value = heightMapTexId
                        currentState.normalAoMapTexture.value = normalAoTexId
                        currentState.riverMapTexture.value = riverMapTexId
                        val mapScaleMeters = mapScaleToLinearDistanceMeters(currentMapScale)
                        heightRangeMeters.value = mapScaleToHeightRangeMeters(currentMapScale)
                        heightMapScaleFactor.value = linearDistanceMetersToViewportScaleFactor(mapScaleMeters, heightRangeMeters.value)
                        waterShaderParams.level.value = linearDistanceMetersToRenderScale(linearDistanceMetersToWaterLevelMeters(mapScaleMeters), heightRangeMeters.value)
                        imageMode.value = 3
                        displayMode.value = DisplayMode.MESH
                    } catch (w: Exception) {
                        if (!causedByCancellation(w)) {
                            throw w
                        }
                    } finally {
                        if (cancelCurrentRunningTask.value == canceled) {
                            cancelCurrentRunningTask.value = null
                        }
                    }
                } finally {
                    generationTimer.cancel()
                    generatingMessageBlock.isVisible = false
                    dialogLayer.isVisible = false
                }
            }
        }
    }
}

fun causedByCancellation(w: Throwable?): Boolean {
    if (w == null) return false
    if (w is CancellationException) return true
    return causedByCancellation(w.cause)
}
