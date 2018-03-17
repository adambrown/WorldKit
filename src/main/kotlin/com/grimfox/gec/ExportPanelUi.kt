package com.grimfox.gec

import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import java.io.File
import java.util.*

private val cachedGraph256 = preferences.cachedGraph256!!
private val cachedGraph512 = preferences.cachedGraph512!!
private val cachedGraph1024 = preferences.cachedGraph1024!!

fun exportPanel(ui: UserInterface) {
    val shrinkGroup = hShrinkGroup()
    val elevationFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useElevationFile = ref(false)
    val slopeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useSlopeFile = ref(false)
    val normalFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useNormalFile = ref(false)
    val soilDensityFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useSoilDensityFile = ref(false)
    val biomeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useBiomeFile = ref(false)
    val waterFlowFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useWaterFlowFile = ref(false)
    val objFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useObjFile = ref(false)
    fun pngExtensionFilter(textReference: DynamicTextReference): (String, String) -> Unit = { old, new ->
        if (old != new) {
            if (new.isNotBlank() && !new.endsWith(".png", true)) {
                textReference.reference.value = "$new.png"
            }
        }
    }
    fun objExtensionFilter(textReference: DynamicTextReference): (String, String) -> Unit = { old, new ->
        if (old != new) {
            if (new.isNotBlank() && !new.endsWith(".obj", true)) {
                textReference.reference.value = "$new.obj"
            }
        }
    }
    fun fileFromTextReference(useFile: Reference<Boolean>, fileReference: DynamicTextReference): File? {
        return if (useFile.value) {
            val file = File(fileReference.reference.value)
            if ((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) {
                file
            } else {
                null
            }
        } else {
            null
        }
    }
    elevationFile.reference.addListener(pngExtensionFilter(elevationFile))
    slopeFile.reference.addListener(pngExtensionFilter(slopeFile))
    normalFile.reference.addListener(pngExtensionFilter(normalFile))
    soilDensityFile.reference.addListener(pngExtensionFilter(soilDensityFile))
    biomeFile.reference.addListener(pngExtensionFilter(biomeFile))
    waterFlowFile.reference.addListener(pngExtensionFilter(waterFlowFile))
    objFile.reference.addListener(objExtensionFilter(objFile))
    panelLayer {
        exportPanel = panel(650.0f) {
            vSizing = Sizing.SHRINK
            vSpacer(LARGE_SPACER_SIZE)
            vSaveFileRowWithToggle(elevationFile, useElevationFile, LARGE_ROW_HEIGHT, text("Elevation file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(normalFile, useNormalFile, LARGE_ROW_HEIGHT, text("Normal file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(slopeFile, useSlopeFile, LARGE_ROW_HEIGHT, text("Slope file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(soilDensityFile, useSoilDensityFile, LARGE_ROW_HEIGHT, text("Soil density file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(biomeFile, useBiomeFile, LARGE_ROW_HEIGHT, text("Biome file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(waterFlowFile, useWaterFlowFile, LARGE_ROW_HEIGHT, text("Water flow file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(objFile, useObjFile, LARGE_ROW_HEIGHT, text("Mesh file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "obj")
            val outputSizes = arrayOf(256, 512, 1024, 2048, 4096)
            val outputSizesAsText = outputSizes.map { text("$it x $it px", TEXT_STYLE_BUTTON) }
            val selectedOutputSize: ObservableMutableReference<Int> = ref(0)
            block {
                vSizing = Sizing.STATIC
                this.height = LARGE_ROW_HEIGHT
                layout = Layout.VERTICAL
                label(text("Output size:"), shrinkGroup)
                hSpacer(MEDIUM_SPACER_SIZE)
                block {
                    hSizing = Sizing.GROW
                    layout = Layout.HORIZONTAL
                    val textRef = StaticTextReference(outputSizesAsText[selectedOutputSize.value])
                    dropdown(textRef, dialogDropdownLayer, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT, TEXT_STYLE_BUTTON, COLOR_DISABLED_CLICKABLE) {
                        outputSizesAsText.forEachIndexed { i, value ->
                            menuItem(value) {
                                selectedOutputSize.value = i
                            }
                        }
                    }.with {
                        vAlign = VerticalAlignment.MIDDLE
                    }
                    selectedOutputSize.addListener { old, new ->
                        if (old != new) {
                            textRef.reference.value = outputSizesAsText[new]
                        }
                    }
                }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("Export"), DIALOG_BUTTON_STYLE) {
                    var outputSize = outputSizes[selectedOutputSize.value]
                    if (DEMO_BUILD) {
                        outputSize = 256
                    }
                    val exportFiles = WaterFlows.ExportFiles(
                            outputSize = outputSize,
                            elevationFile = fileFromTextReference(useElevationFile, elevationFile),
                            slopeFile = fileFromTextReference(useSlopeFile, slopeFile),
                            normalFile = fileFromTextReference(useNormalFile, normalFile),
                            soilDensityFile = fileFromTextReference(useSoilDensityFile, soilDensityFile),
                            biomeFile = fileFromTextReference(useBiomeFile, biomeFile),
                            waterFlowFile = fileFromTextReference(useWaterFlowFile, waterFlowFile),
                            objFile = fileFromTextReference(useObjFile, objFile))
                    export(exportFiles)
                    exportPanel.isVisible = false
                    panelLayer.isVisible = false
                }.with { width = 60.0f }
                hSpacer(SMALL_SPACER_SIZE)
                button(text("Close"), DIALOG_BUTTON_STYLE) {
                    exportPanel.isVisible = false
                    panelLayer.isVisible = false
                }.with { width = 60.0f }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
        }
    }
}

private fun export(exportFiles: WaterFlows.ExportFiles) {
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
        generatingPrimaryMessage.reference.value = text("Exporting maps... 0:00", TEXT_STYLE_LARGE_MESSAGE)
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
                generatingPrimaryMessage.reference.value = text("Exporting maps... $minutes:$seconds", TEXT_STYLE_LARGE_MESSAGE)
                rootRef.value.movedOrResized = true
            }
        }, 1000, 1000)
        try {
            val canceled = ref(false)
            cancelCurrentRunningTask.value = canceled
            try {
                BuildContinent.generateWaterFlows(
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
                        customSoilMobilityMap = currentState.customSoilMobilityMap.value,
                        canceled = canceled,
                        biomeTemplates = BIOME_TEMPLATES_REF.value!!,
                        exportFiles = exportFiles)
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