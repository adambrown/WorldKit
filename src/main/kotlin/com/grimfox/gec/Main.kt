package com.grimfox.gec

import com.fasterxml.jackson.core.JsonParseException
import com.grimfox.gec.ui.*
import com.grimfox.gec.util.BuildContinent.RegionParameters
import com.grimfox.gec.util.BuildContinent.generateRegions
import com.grimfox.gec.util.loadTexture2D
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.LEFT
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import nl.komponents.kovenant.task
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

object Main {

    @JvmStatic fun main(vararg args: String) {
        val executor = executor
        val preferences = preferences

        for (i in 1..2) {
            task { generateRegions(RegionParameters(regionsSeed = i.toLong()), executor) }
        }

        val titleText = DynamicTextReference("WorldKit - No Project", 67, TEXT_STYLE_NORMAL)

        val uiLayout = layout { ui ->
            val uiLayout = this
            ui {
                disableCursor.listener { old, new ->
                    if (old != new) {
                        if (new) {
                            disableCursor()
                        } else {
                            enableCursor()
                        }
                    }
                }
                hideCursor.listener { old, new ->
                    if (old != new) {
                        if (new) {
                            hideCursor()
                        } else {
                            showCursor()
                        }
                    }
                }
                background.set(45, 45, 48)

                textFont.value = createFont("/fonts/FiraSans.ttf", "FiraSans")
                glyphFont.value = createFont("/fonts/WorldKitUi.ttf", "Glyphs")

                val maxRestoreGlyph = MemoryUtil.memUTF8(if (isMaximized) GLYPH_RESTORE else GLYPH_MAXIMIZE, true)
                maximizeHandler = {
                    MemoryUtil.memUTF8(GLYPH_RESTORE, true, maxRestoreGlyph, 0)
                }
                restoreHandler = {
                    MemoryUtil.memUTF8(GLYPH_MAXIMIZE, true, maxRestoreGlyph, 0)
                }

                val (texId, texWidth, texHeight) = loadTexture2D(GL11.GL_LINEAR_MIPMAP_NEAREST, GL11.GL_LINEAR, "/textures/wk-icon-1024.png", true, true,
                        "/textures/wk-icon-512.png",
                        "/textures/wk-icon-256.png",
                        "/textures/wk-icon-128.png",
                        "/textures/wk-icon-64.png",
                        "/textures/wk-icon-32.png",
                        "/textures/wk-icon-16.png")
                val icon = createImage(texId, texWidth, texHeight, 0)

                setWindowIcon(createGlfwImages(
                        "/textures/wk-icon-16.png",
                        "/textures/wk-icon-24.png",
                        "/textures/wk-icon-32.png",
                        "/textures/wk-icon-40.png",
                        "/textures/wk-icon-48.png",
                        "/textures/wk-icon-64.png",
                        "/textures/wk-icon-96.png",
                        "/textures/wk-icon-128.png",
                        "/textures/wk-icon-192.png",
                        "/textures/wk-icon-256.png"
                ))

                meshViewport.init()

                var topBar = NO_BLOCK
                var contentPanel = NO_BLOCK
                var rightPanel = NO_BLOCK

                root {
                    mainLayer = block {
                        isFallThrough = true
                    }
                    menuLayer = block {
                        isFallThrough = true
                    }
                    panelLayer = block {
                        isFallThrough = false
                        isMouseAware = true
                        isVisible = false
                        shape = FILL_GREY_OUT
                    }
                    dropdownLayer = block {
                        isFallThrough = true
                    }
                    dialogLayer = block {
                        isFallThrough = false
                        isMouseAware = true
                        isVisible = false
                        shape = FILL_GREY_OUT
                    }
                    mouseOverlayLayer = block {
                        isFallThrough = true
                    }
                }
                val errorHandler: ErrorDialog = object : ErrorDialog {

                    override fun displayErrorMessage(message: String?) {
                        errorMessageReference.value = message ?: "An unknown error occurred."
                        dialogLayer.isVisible = true
                        errorMessageDialog.isVisible = true
                        dialogCallback.value = {
                            dialogCallback.value = noop
                        }
                    }
                }
                dialogLayer {
                    overwriteWarningDialog = dialog(400.0f, 160.0f, overwriteWarningText, BLOCK_GLYPH_WARNING(60.0f)) {
                        button(text("Yes"), DIALOG_BUTTON_STYLE) {
                            overwriteWarningDialog.isVisible = false
                            val saved = saveProject(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                            dialogLayer.isVisible = false
                            if (saved) {
                                dialogCallback.value()
                            }
                        }.with { width = 60.0f }
                        hSpacer(SMALL_SPACER_SIZE)
                        button(text("No"), DIALOG_BUTTON_STYLE) {
                            overwriteWarningDialog.isVisible = false
                            dialogLayer.isVisible = false
                            dialogCallback.value()
                        }.with { width = 60.0f }
                        hSpacer(SMALL_SPACER_SIZE)
                        button(text("Cancel"), DIALOG_BUTTON_STYLE) {
                            overwriteWarningDialog.isVisible = false
                            dialogLayer.isVisible = false
                        }.with { width = 60.0f }
                    }
                    errorMessageDialog = dialog(500.0f, 190.0f, errorMessageText, BLOCK_GLYPH_ERROR(60.0f)) {
                        button(text("OK"), DIALOG_BUTTON_STYLE) {
                            errorMessageDialog.isVisible = false
                            dialogLayer.isVisible = false
                            dialogCallback.value()
                        }.with { width = 60.0f }
                    }

                }
                currentProject.listener { _, new ->
                    updateTitle(titleText, new)
                    doesActiveProjectExist.value = new != null
                    if (new != null) {
                        addProjectToRecentProjects(new.file, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                    }
                }
                preferencesPanel(ui)
                exportPanel(ui)
                mainLayer {
                    topBar = block {
                        vSizing = STATIC
                        height = MEDIUM_ROW_HEIGHT
                        layout = VERTICAL
                        icon(icon, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT)
                        hSpacer(SMALL_SPACER_SIZE)
                        menuBar(menuLayer, MEDIUM_ROW_HEIGHT, TEXT_STYLE_BUTTON, COLOR_DISABLED_CLICKABLE) {
                            menu("File") {
                                menuItem("New project", "Ctrl+N", BLOCK_GLYPH_NEW_FILE) {
                                    newProject(overwriteWarningReference, overwriteWarningDialog, dialogCallback, noop)
                                }
                                menuItem("Open project...", "Ctrl+O", BLOCK_GLYPH_OPEN_FOLDER) {
                                    val openFun = {
                                        try {
                                            val openedProject = openProject(dialogLayer, preferences, ui)
                                            if (openedProject != null) {
                                                currentProject.value = openedProject
                                            }
                                        } catch (e: JsonParseException) {
                                            errorHandler.displayErrorMessage("The selected file is not a valid project.")
                                        } catch (e: IOException) {
                                            errorHandler.displayErrorMessage("Unable to read from the selected file while trying to open project.")
                                        } catch (e: Exception) {
                                            errorHandler.displayErrorMessage("Encountered an unexpected error while trying to open project.")
                                        }
                                    }
                                    if (currentProject.value != null) {
                                        dialogLayer.isVisible = true
                                        overwriteWarningReference.value = "Do you want to save the current project before opening a different one?"
                                        overwriteWarningDialog.isVisible = true
                                        dialogCallback.value = openFun
                                    } else {
                                        openFun()
                                    }
                                }
                                menuItem("Save", "Ctrl+S", BLOCK_GLYPH_SAVE, isActive = doesActiveProjectExist) {
                                    saveProject(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                                }
                                menuItem("Save as...", "Shift+Ctrl+S", BLOCK_GLYPH_SAVE, isActive = doesActiveProjectExist) {
                                    saveProjectAs(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                                }
                                menuItem("Close", isActive = doesActiveProjectExist) {
                                    if (currentProject.value != null) {
                                        dialogLayer.isVisible = true
                                        overwriteWarningReference.value = "Do you want to save the current project before closing?"
                                        overwriteWarningDialog.isVisible = true
                                        dialogCallback.value = {
                                            historyRegionsBackQueue.clear()
                                            historyRegionsForwardQueue.clear()
                                            currentState = CurrentState()
                                            meshViewport.reset()
                                            imageMode.value = 3
                                            currentProject.value = null
                                            dialogCallback.value = noop
                                        }
                                    }
                                }
                                menuDivider()
                                subMenu("Open recent", isActive = recentProjectsAvailable) {
                                    recentProjectsDropdown.value = this
                                    menuDivider()
                                    menuItem("Clear recent file list", isActive = recentProjectsAvailable) {
                                        clearRecentProjects()
                                    }
                                }
                                menuDivider()
                                menuItem("Export maps...", "Ctrl+E", isActive = doesActiveProjectExist) {
                                    panelLayer.isVisible = true
                                    exportPanel.isVisible = true
                                }
                                menuDivider()
                                menuItem("Exit", "Alt+F4", BLOCK_GLYPH_CLOSE) {
                                    closeWindow()
                                }
                            }
                            menu("Settings") {
                                menuItem("Preferences", "Ctrl+P", BLOCK_GLYPH_GEAR) {
                                    panelLayer.isVisible = true
                                    preferencesPanel.isVisible = true
                                }
                                menuItem("Restore default preferences") {
                                    val defaults = Preferences()
                                    preferences.rememberWindowState = defaults.rememberWindowState
                                    preferences.projectDir = defaults.projectDir
                                    preferences.tempDir = defaults.tempDir
                                    rememberWindowState.value = preferences.rememberWindowState
                                    projectDir.reference.value = preferences.projectDir.canonicalPath
                                    tempDir.reference.value = preferences.tempDir.canonicalPath
                                    savePreferences(preferences)
                                }
                            }
                            menu("Help") {
                                menuItem("Help", "Ctrl+F1", BLOCK_GLYPH_HELP) {
                                    if (OFFLINE_HELP_INDEX_FILE.isFile && OFFLINE_HELP_INDEX_FILE.canRead()) {
                                        openWebPage(OFFLINE_HELP_INDEX_FILE.toURI(), errorHandler)
                                    } else {
                                        errorHandler.displayErrorMessage("The offline help files are not currently installed. Please install the offline help files, or select one of the online help options.")
                                    }
                                }
                                menuDivider()
                                menuItem("Getting started") {
                                    openWebPage("http://www.google.com", errorHandler)
                                }
                                menuItem("Tutorials") {
                                    openWebPage("http://www.google.com", errorHandler)
                                }
                                menuDivider()
                                menuItem("Website") {
                                    openWebPage("http://www.google.com", errorHandler)
                                }
                                menuItem("Wiki") {
                                    openWebPage("http://www.google.com", errorHandler)
                                }
                                menuItem("Forum") {
                                    openWebPage("http://www.google.com", errorHandler)
                                }
                                menuDivider()
                                menuItem("Install offline help") {
                                    openWebPage("http://www.google.com", errorHandler)
                                }
                                menuDivider()
                                menuItem("About WorldKit") {
                                    println("About WorldKit")
                                }
                            }
                        }
                        hSpacer(SMALL_SPACER_SIZE)
                        dragArea = dragArea(titleText.text)
                        hSpacer(SMALL_SPACER_SIZE)
                        button(glyph(GLYPH_MINIMIZE), WINDOW_DECORATE_BUTTON_STYLE) { minimizeWindow() }
                        button(glyph(maxRestoreGlyph), WINDOW_DECORATE_BUTTON_STYLE) { toggleMaximized() }
                        button(glyph(GLYPH_CLOSE), WINDOW_DECORATE_BUTTON_STYLE) { closeWindow() }
                    }
                    loadRecentProjects(dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                    contentPanel = block {
                        vSizing = GROW
                        layout = VERTICAL
                        hAlign = LEFT
                        leftPanel(ui, uiLayout, dialogLayer)
                        rightPanel = block {
                            hSizing = GROW
                            layout = HORIZONTAL
                            hAlign = LEFT
                            block {
                                yOffset = SMALL_SPACER_SIZE
                                width = -SMALL_SPACER_SIZE
                                height = -2 * SMALL_SPACER_SIZE
                                block {
                                    xOffset = 1.0f
                                    yOffset = 1.0f
                                    width = -2.0f
                                    height = -2.0f
                                    meshViewport3D(meshViewport, ui)
                                    block {
                                        val toolbar = this
                                        vSizing = STATIC
                                        height = MEDIUM_ROW_HEIGHT
                                        var tools = NO_BLOCK
                                        var expandToolbarButton = NO_BLOCK
                                        var collapseToolbarButton = NO_BLOCK
                                        isFallThrough = true
                                        hButtonRow {
                                            expandToolbarButton = button(text("+"), LARGE_TEXT_BUTTON_STYLE) {
                                                tools.isVisible = true
                                                tools.isMouseAware = true
                                                collapseToolbarButton.isVisible = true
                                                collapseToolbarButton.isMouseAware = true
                                                expandToolbarButton.isVisible = false
                                                expandToolbarButton.isMouseAware = false
                                                toolbar.shape = BACKGROUND_RECT
                                            }
                                            collapseToolbarButton = button(text("-"), LARGE_TEXT_BUTTON_STYLE) {
                                                tools.isVisible = false
                                                tools.isMouseAware = false
                                                collapseToolbarButton.isVisible = false
                                                collapseToolbarButton.isMouseAware = false
                                                expandToolbarButton.isVisible = true
                                                expandToolbarButton.isMouseAware = true
                                                toolbar.shape = NO_SHAPE
                                            }
                                            collapseToolbarButton.isVisible = false
                                        }
                                        tools = block {
                                            isVisible = false
                                            hSizing = GROW
                                            layout = HORIZONTAL
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hToggleRow(waterPlaneOn, text("Water:"), MEDIUM_SPACER_SIZE)
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hDivider()
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hToggleRow(perspectiveOn, text("Perspective:"), MEDIUM_SPACER_SIZE)
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hDivider()
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hToggleRow(rotateAroundCamera, text("Rotate camera:"), MEDIUM_SPACER_SIZE)
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hDivider()
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hSliderRow(heightMapScaleFactor, 144.0f, text("Height scale:"), MEDIUM_SPACER_SIZE, heightScaleFunction, heightScaleFunctionInverse)
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hDivider()
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hButtonRow {
                                                button(text("Reset view"), NORMAL_TEXT_BUTTON_STYLE) { resetView.value = true }
                                            }
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hDivider()
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            hButtonRow {
                                                button(text("Reset height"), NORMAL_TEXT_BUTTON_STYLE) { heightMapScaleFactor.value = DEFAULT_HEIGHT_SCALE }
                                            }
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                        }
                                    }
                                }
                                block {
                                    shape = SHAPE_BORDER_ONLY
                                    canOverflow = true
                                    isMouseAware = false
                                }
                            }
                        }
                    }
                    resizeAreaSouthEast = resizeArea(ShapeTriangle.Direction.SOUTH_EAST)
                    resizeAreaSouthWest = resizeArea(ShapeTriangle.Direction.SOUTH_WEST)
                }
                mouseOverlayLayer {
                    brushShapeOuter = block {
                        layout = ABSOLUTE
                        hSizing = STATIC
                        vSizing = STATIC
                        width = 10.0f
                        height = 10.0f
                        shape = ShapeCircle(NO_FILL, STROKE_WHITE_1)
                        isFallThrough = true
                        isMouseAware = false
                    }
                    brushShapeInner = block {
                        layout = ABSOLUTE
                        hSizing = STATIC
                        vSizing = STATIC
                        width = 8.0f
                        height = 8.0f
                        shape = ShapeCircle(NO_FILL, STROKE_BLACK_1)
                        isFallThrough = true
                        isMouseAware = false
                    }
                }
                brushActive.listener { _, new ->
                    mouseOverlayLayer.isVisible = new
                    mouseOverlayLayer.isMouseAware = new
                    brushShapeOuter.isVisible = new
                    brushShapeOuter.isMouseAware = new
                    brushShapeInner.isVisible = new
                    brushShapeInner.isMouseAware = new
                    if (new) {
                        mouseOverlayLayer.onTick = { x, y ->
                            if (brushActive.value) {
                                val brushSize = brushSize.value
                                brushShapeOuter.xOffset = x - (brushSize / 2.0f)
                                brushShapeOuter.yOffset = y - (brushSize / 2.0f)
                                brushShapeOuter.width = brushSize
                                brushShapeOuter.height = brushSize
                                brushShapeInner.xOffset = x - ((brushSize - 2) / 2.0f)
                                brushShapeInner.yOffset = y - ((brushSize - 2) / 2.0f)
                                brushShapeInner.width = brushSize - 2
                                brushShapeInner.height = brushSize - 2
                            }
                        }
                    } else {
                        mouseOverlayLayer.onTick = null
                    }
                }
                brushActive.value = false
                if (!EXPERIMENTAL_BUILD) {
                    experimentalWidgets.forEach {
                        it.isVisible = false
                    }
                }
            }
        }
        var wasResizing = false
        var wasMinimized = true
        var wasJustMaximized = false
        ui(uiLayout, preferences.windowState) {
            if (isResizing) {
                wasResizing = true
            } else {
                if (wasResizing) {
                    onWindowResize()
                }
                wasResizing = false
            }
            if (wasJustMaximized) {
                onWindowResize()
                wasJustMaximized = false
            }
            if (isMaximized) {
                if (wasMinimized) {
                    wasJustMaximized = true
                }
                wasMinimized = false
            } else {
                wasMinimized = true
            }
        }
    }
}

private fun openWebPage(uri: URI, onError: ErrorDialog? = null) {
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

private fun openWebPage(url: URL, onError: ErrorDialog? = null) {
    try {
        openWebPage(url.toURI(), onError)
    } catch (e: URISyntaxException) {
        LOG.error("Unable to open web page: $url", e)
        onError?.displayErrorMessage(e.message)
    }
}

private fun openWebPage(url: String, onError: ErrorDialog? = null) {
    try {
        openWebPage(URL(url), onError)
    } catch (e: Exception) {
        LOG.error("Unable to open web page: $url", e)
        onError?.displayErrorMessage(e.message)
    }
}
