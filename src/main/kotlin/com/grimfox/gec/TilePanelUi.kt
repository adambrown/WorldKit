package com.grimfox.gec

import com.grimfox.gec.Tiler.tileHeightMap
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import java.io.File

private val cachedGraph256 = preferences.cachedGraph256!!
private val cachedGraph512 = preferences.cachedGraph512!!
private val cachedGraph1024 = preferences.cachedGraph1024!!
private val cachedGraph2048 = preferences.cachedGraph2048!!
private val cachedGraph4096 = preferences.cachedGraph4096!!

fun tilePanel(ui: UserInterface) {
    val shrinkGroup = hShrinkGroup()
    val heightInput = DynamicTextReference("${preferences.projectDir}${File.separator}exports${File.separator}export", 1024, TEXT_STYLE_NORMAL)
    val heightOutput = DynamicTextReference("${preferences.projectDir}${File.separator}exports${File.separator}export", 1024, TEXT_STYLE_NORMAL)
    val tileSize = ref(1017L)
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
    heightInput.reference.addListener(pngExtensionFilter(heightInput))
    panelLayer {
        tilePanel = panel(PANEL_WIDTH) {
            vSizing = Sizing.SHRINK
            vSpacer(LARGE_SPACER_SIZE)
            vFileRow(heightInput, LARGE_ROW_HEIGHT, text("Elevation input file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui)
            vSaveFileRow(heightOutput, LARGE_ROW_HEIGHT, text("Elevation output base:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui)
            vLongInputRow(tileSize, LARGE_ROW_HEIGHT, text("Tile size:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("Make elevation tiles"), DIALOG_BUTTON_STYLE) {
                    makeTiles(heightInput, heightOutput, tileSize)
                }.with { width = 135.0f }
                hSpacer(SMALL_SPACER_SIZE)
                button(text("Close"), DIALOG_BUTTON_STYLE) {
                    tilePanel.isVisible = false
                    panelLayer.isVisible = false
                }.with { width = 60.0f }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
        }
    }
}

private fun makeTiles(heightInput: DynamicTextReference, heightOutput: DynamicTextReference, tileSize: ObservableMutableReference<Long>) {
    val inputFile = File(heightInput.reference.value)
    val heightBase = File(heightOutput.reference.value)
    val heightOutputDir = heightBase.parentFile
    val outputName = heightBase.name
    val tileSizeInt = tileSize.value.toInt()
    if (inputFile.exists() && inputFile.isFile && (heightOutputDir.isDirectory || heightOutputDir.mkdirs()) && outputName.isNotBlank() && tileSizeInt > 0) {
        dialogLayer.isVisible = true
        generatingPrimaryMessage.reference.value = text("Exporting elevation tiles...", TEXT_STYLE_LARGE_MESSAGE)
        val listener: (Text, Text) -> Unit = { _, _ ->
            rootRef.value.movedOrResized = true
        }
        generatingPrimaryMessage.reference.addListener(listener)
        generatingSecondaryMessage.reference.value = text("Press ESC to cancel.", TEXT_STYLE_SMALL_MESSAGE)
        generatingMessageBlock.isVisible = true
        try {
            val canceled = ref(false)
            cancelCurrentRunningTask.value = canceled
            try {
                tileHeightMap(inputFile, heightOutputDir, outputName, tileSizeInt, generatingPrimaryMessage.reference, canceled)
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
            generatingPrimaryMessage.reference.removeListener(listener)
            generatingMessageBlock.isVisible = false
            dialogLayer.isVisible = false
        }
    }
}