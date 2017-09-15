package com.grimfox.gec

import com.grimfox.gec.ui.HotkeyHandler
import com.grimfox.gec.ui.layout
import com.grimfox.gec.ui.set
import com.grimfox.gec.ui.ui
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.LEFT
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.GROW
import com.grimfox.gec.ui.widgets.Sizing.STATIC
import com.grimfox.gec.util.BuildContinent.RegionParameters
import com.grimfox.gec.util.BuildContinent.generateRegions
import com.grimfox.gec.util.loadTexture2D
import nl.komponents.kovenant.task
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil

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
                currentProjectHasModifications.listener { _, new ->
                    updateTitle(titleText, currentProject.value, new)
                }
                preferencesPanel(ui)
                exportPanel(ui)
                mainLayer {
                    block {
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
                                    openProject(ui, errorHandler)
                                }
                                menuItem("Save", "Ctrl+S", BLOCK_GLYPH_SAVE, isActive = currentProjectHasModifications) {
                                    saveProject(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                                }
                                menuItem("Save as...", "Shift+Ctrl+S", BLOCK_GLYPH_SAVE, isActive = doesActiveProjectExist) {
                                    saveProjectAs(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                                }
                                menuItem("Close", isActive = doesActiveProjectExist) {
                                    if (currentProject.value != null && currentProjectHasModifications.value) {
                                        dialogLayer.isVisible = true
                                        overwriteWarningReference.value = "Do you want to save the current project before closing?"
                                        overwriteWarningDialog.isVisible = true
                                        dialogCallback.value = {
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
                                    exportMaps()
                                }
                                menuDivider()
                                menuItem("Exit", "Ctrl+Q", BLOCK_GLYPH_CLOSE) {
                                    closeWindowSafely()
                                }
                            }
                            menu("Settings") {
                                menuItem("Preferences", "Ctrl+P", BLOCK_GLYPH_GEAR) {
                                    openPreferences()
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
                                    openHelp(errorHandler)
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
                        button(glyph(GLYPH_CLOSE), WINDOW_DECORATE_BUTTON_STYLE) {
                            closeWindowSafely()
                        }
                    }
                    loadRecentProjects(dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                    block {
                        vSizing = GROW
                        layout = VERTICAL
                        hAlign = LEFT
                        leftPanel(ui, uiLayout, dialogLayer)
                        block {
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
                hotkeyHandler = HotkeyHandler { key, _, action, mods ->
                    if (action == GLFW.GLFW_PRESS) {
                        val ctrl = GLFW.GLFW_MOD_CONTROL and mods != 0
                        val shift = GLFW.GLFW_MOD_SHIFT and mods != 0
                        if (ctrl) {
                            when (key) {
                                GLFW.GLFW_KEY_N -> {
                                    task { newProject(overwriteWarningReference, overwriteWarningDialog, dialogCallback, noop) }
                                    true
                                }
                                GLFW.GLFW_KEY_O -> {
                                    task { openProject(ui, errorHandler) }
                                    true
                                }
                                GLFW.GLFW_KEY_S -> {
                                    if (shift) {
                                        task { saveProjectAs(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler) }
                                    } else {
                                        task { saveProject(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler) }
                                    }
                                    true
                                }
                                GLFW.GLFW_KEY_E -> {
                                    task { exportMaps() }
                                    true
                                }
                                GLFW.GLFW_KEY_Q -> {
                                    task { closeWindowSafely() }
                                    true
                                }
                                GLFW.GLFW_KEY_P -> {
                                    task { openPreferences() }
                                    true
                                }
                                GLFW.GLFW_KEY_F1 -> {
                                    task { openHelp(errorHandler) }
                                    false
                                }
                                else -> {
                                    false
                                }
                            }
                        } else {
                            false
                        }
                    } else {
                        false
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
