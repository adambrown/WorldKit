package com.grimfox.gec

import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import java.io.File
import java.util.*

private val cachedGraph256 = preferences.cachedGraph256!!
private val cachedGraph512 = preferences.cachedGraph512!!
private val cachedGraph1024 = preferences.cachedGraph1024!!
private val cachedGraph2048 = preferences.cachedGraph2048!!
private val cachedGraph4096 = preferences.cachedGraph4096!!

fun exportPanel(ui: UserInterface) {
    val shrinkGroup = hShrinkGroup()
    val baseFile = DynamicTextReference("${preferences.projectDir}${File.separator}exports${File.separator}export", 1024, TEXT_STYLE_NORMAL)
    val useBaseFile = ref(false)
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
    val peakFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val usePeakFile = ref(false)
    val riverFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useRiverFile = ref(false)
    val biomeBorderFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useBiomeBorderFile = ref(false)
    val landMaskFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useLandMaskFile = ref(false)
    val riverBorderFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useRiverBorderFile = ref(false)
    val mountainBorderFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useMountainBorderFile = ref(false)
    val coastalBorderFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useCoastalBorderFile = ref(false)
    val meshFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useMeshFile = ref(false)
    if (DEMO_BUILD) {
        meshFile.reference.value = "Mesh export is disabled for the demo."
    }
    fun pngExtensionFilter(textReference: DynamicTextReference): (String, String) -> Unit = { old, new ->
        if (old != new) {
            if (new.isNotBlank() && !new.endsWith(".png", true)) {
                textReference.reference.value = "$new.png"
            }
        }
    }
    fun objExtensionFilter(textReference: DynamicTextReference): (String, String) -> Unit = { old, new ->
        if (old != new) {
            if (DEMO_BUILD) {
                textReference.reference.value = "Mesh export is disabled for the demo."
            } else {
                if (new.isNotBlank() && !new.endsWith(".obj", true)) {
                    textReference.reference.value = "$new.obj"
                }
            }
        }
    }
    fun fileNameChangeListener(useFileReference: ObservableMutableReference<Boolean>): (String, String) -> Unit = { old, new ->
        if (old.isBlank() && new.isNotBlank()) {
            val file = File(new)
            if ((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) {
                useFileReference.value = true
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
    peakFile.reference.addListener(pngExtensionFilter(peakFile))
    riverFile.reference.addListener(pngExtensionFilter(riverFile))
    biomeBorderFile.reference.addListener(pngExtensionFilter(biomeBorderFile))
    landMaskFile.reference.addListener(pngExtensionFilter(landMaskFile))
    riverBorderFile.reference.addListener(pngExtensionFilter(riverBorderFile))
    mountainBorderFile.reference.addListener(pngExtensionFilter(mountainBorderFile))
    coastalBorderFile.reference.addListener(pngExtensionFilter(coastalBorderFile))
    meshFile.reference.addListener(objExtensionFilter(meshFile))
    if (DEMO_BUILD) {
        useMeshFile.addListener { old, new ->
            if (old != new && new) {
                task {
                    useMeshFile.value = false
                }
            }
        }
    }
    panelLayer {
        exportPanel = panel(PANEL_WIDTH) {
            vSizing = Sizing.SHRINK
            vSpacer(LARGE_SPACER_SIZE)
            vSaveFileRowWithToggle(baseFile, useBaseFile, LARGE_ROW_HEIGHT, text("Base name:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui)
            vSaveFileRowWithToggle(elevationFile, useElevationFile, LARGE_ROW_HEIGHT, text("Elevation file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(normalFile, useNormalFile, LARGE_ROW_HEIGHT, text("Normal file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(slopeFile, useSlopeFile, LARGE_ROW_HEIGHT, text("Slope file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(soilDensityFile, useSoilDensityFile, LARGE_ROW_HEIGHT, text("Soil density file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(biomeFile, useBiomeFile, LARGE_ROW_HEIGHT, text("Biome file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(waterFlowFile, useWaterFlowFile, LARGE_ROW_HEIGHT, text("Water flow file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(peakFile, usePeakFile, LARGE_ROW_HEIGHT, text("Peak file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(riverFile, useRiverFile, LARGE_ROW_HEIGHT, text("River file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(biomeBorderFile, useBiomeBorderFile, LARGE_ROW_HEIGHT, text("Biome border file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(landMaskFile, useLandMaskFile, LARGE_ROW_HEIGHT, text("Land mask file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(riverBorderFile, useRiverBorderFile, LARGE_ROW_HEIGHT, text("River border file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(mountainBorderFile, useMountainBorderFile, LARGE_ROW_HEIGHT, text("Mountain border file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(coastalBorderFile, useCoastalBorderFile, LARGE_ROW_HEIGHT, text("Coastal border file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(meshFile, useMeshFile, LARGE_ROW_HEIGHT, text("Mesh file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "obj")
            val outputSizes = if (DEMO_BUILD) {
                arrayOf(256, 256)
            } else {
                arrayOf(8192, 8129, 4096, 4033, 2048, 2017, 1024, 1009, 512, 505, 256, 253)
            }
            val outputSizesAsText = if (DEMO_BUILD) {
                listOf(text("256 x 256 px", TEXT_STYLE_BUTTON), text("Output size is limited to 256 x 256 px for the demo.", TEXT_STYLE_BUTTON))
            } else {
                outputSizes.map { text("$it x $it px", TEXT_STYLE_BUTTON) }
            }
            val selectedOutputSize: ObservableMutableReference<Int> = ref(0)
            fun updateOutputs() {
                val file = fileFromTextReference(useBaseFile, baseFile)
                if (file != null) {
                    val baseName = file.absolutePath
                    val outputSize = outputSizes[selectedOutputSize.value]
                    elevationFile.reference.value = "$baseName-elevation-$outputSize.png"
                    slopeFile.reference.value = "$baseName-slope-$outputSize.png"
                    normalFile.reference.value = "$baseName-normal-$outputSize.png"
                    soilDensityFile.reference.value = "$baseName-density-$outputSize.png"
                    biomeFile.reference.value = "$baseName-biome-$outputSize.png"
                    waterFlowFile.reference.value = "$baseName-flow-$outputSize.png"
                    peakFile.reference.value = "$baseName-peaks-$outputSize.png"
                    riverFile.reference.value = "$baseName-rivers-$outputSize.png"
                    biomeBorderFile.reference.value = "$baseName-biome-borders-$outputSize.png"
                    landMaskFile.reference.value = "$baseName-land-$outputSize.png"
                    riverBorderFile.reference.value = "$baseName-river-borders-$outputSize.png"
                    mountainBorderFile.reference.value = "$baseName-mountain-borders-$outputSize.png"
                    coastalBorderFile.reference.value = "$baseName-coastal-borders-$outputSize.png"
                    meshFile.reference.value = "$baseName-mesh.obj"
                }
            }
            useBaseFile.addListener { old, new ->
                if (new && old != new) {
                    updateOutputs()
                }
            }
            selectedOutputSize.addListener { old, new ->
                if (old != new && useBaseFile.value) {
                    updateOutputs()
                }
            }
            baseFile.reference.addListener { old, new ->
                if (old != new && useBaseFile.value) {
                    updateOutputs()
                }
            }
            elevationFile.reference.addListener(fileNameChangeListener(useElevationFile))
            slopeFile.reference.addListener(fileNameChangeListener(useSlopeFile))
            normalFile.reference.addListener(fileNameChangeListener(useNormalFile))
            soilDensityFile.reference.addListener(fileNameChangeListener(useSoilDensityFile))
            biomeFile.reference.addListener(fileNameChangeListener(useBiomeFile))
            waterFlowFile.reference.addListener(fileNameChangeListener(useWaterFlowFile))
            peakFile.reference.addListener(fileNameChangeListener(usePeakFile))
            riverFile.reference.addListener(fileNameChangeListener(useRiverFile))
            biomeBorderFile.reference.addListener(fileNameChangeListener(useBiomeBorderFile))
            landMaskFile.reference.addListener(fileNameChangeListener(useLandMaskFile))
            riverBorderFile.reference.addListener(fileNameChangeListener(useRiverBorderFile))
            mountainBorderFile.reference.addListener(fileNameChangeListener(useMountainBorderFile))
            coastalBorderFile.reference.addListener(fileNameChangeListener(useCoastalBorderFile))
            meshFile.reference.addListener(fileNameChangeListener(useMeshFile))
            vDropdownRow(selectedOutputSize, outputSizesAsText, LARGE_ROW_HEIGHT, text("Output size:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogDropdownLayer)
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("Export"), DIALOG_BUTTON_STYLE) {
                    var outputSize = outputSizes[selectedOutputSize.value]
                    val objFileValue = if (DEMO_BUILD) {
                        outputSize = 256
                        null
                    } else {
                        fileFromTextReference(useMeshFile, meshFile)
                    }
                    val exportFiles = WaterFlows.ExportFiles(
                            outputSize = outputSize,
                            elevationFile = fileFromTextReference(useElevationFile, elevationFile),
                            slopeFile = fileFromTextReference(useSlopeFile, slopeFile),
                            normalFile = fileFromTextReference(useNormalFile, normalFile),
                            soilDensityFile = fileFromTextReference(useSoilDensityFile, soilDensityFile),
                            biomeFile = fileFromTextReference(useBiomeFile, biomeFile),
                            waterFlowFile = fileFromTextReference(useWaterFlowFile, waterFlowFile),
                            peakFile = fileFromTextReference(usePeakFile, peakFile),
                            riverFile = fileFromTextReference(useRiverFile, riverFile),
                            biomeBorderFile = fileFromTextReference(useBiomeBorderFile, biomeBorderFile),
                            landMaskFile = fileFromTextReference(useLandMaskFile, landMaskFile),
                            riverBorderFile = fileFromTextReference(useRiverBorderFile, riverBorderFile),
                            mountainBorderFile = fileFromTextReference(useMountainBorderFile, mountainBorderFile),
                            coastalBorderFile = fileFromTextReference(useCoastalBorderFile, coastalBorderFile),
                            objFile = objFileValue)
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
                        flowGraphSuper = cachedGraph2048.value,
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