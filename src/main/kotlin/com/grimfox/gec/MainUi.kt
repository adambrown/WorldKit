package com.grimfox.gec

import com.fasterxml.jackson.core.JsonParseException
import com.grimfox.gec.opengl.loadTexture2D
import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.util.*
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import java.net.URISyntaxException
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL

object MainUi {

    @JvmStatic fun main(vararg args: String) {
        val preferences = loadPreferences()

        val DEFAULT_HEIGHT_SCALE = 130.0f
        val MAX_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 10
        val MIN_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 0
        val heightScaleFunction = { scale: Float ->
            Math.min(MAX_HEIGHT_SCALE, Math.max(MIN_HEIGHT_SCALE, if (scale <= 0.5f) {
                scale * 260
            } else {
                (125.937 + (4160.29 * Math.pow(scale - 0.46874918, 2.0))).toFloat()
            }))
        }
        val heightScaleFunctionInverse = { value: Float ->
            Math.min(1.0f, Math.max(0.0f, if (value <= 130) {
                value / 260
            } else {
                (Math.sqrt((value - 125.937) / 4160.29) + 0.46874918).toFloat()
            }))
        }
        val heightMapScaleFactor = ref(DEFAULT_HEIGHT_SCALE)
        val waterPlaneOn = ref(true)
        val perspectiveOn = ref(true)
        val rotateAroundCamera = ref(false)
        val resetView = mRef(false)

        val titleText = DynamicTextReference("WorldKit - No Project", 67, TEXT_STYLE_NORMAL)

        val overwriteWarningDynamic = dynamicParagraph("", 300)
        val overwriteWarningText = overwriteWarningDynamic.text
        val overwriteWarningReference = overwriteWarningDynamic.reference

        val errorMessageDynamic = dynamicParagraph("", 600)
        val errorMessageText = errorMessageDynamic.text
        val errorMessageReference = errorMessageDynamic.reference

        val meshViewport = MeshViewport3D(resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor)

        val uiLayout = layout { ui ->
            ui {
                background.set(45, 45, 48)

                textFont.value = createFont("/fonts/FiraSans.ttf", "FiraSans")
                glyphFont.value = createFont("/fonts/WorldKitUi.ttf", "Glyphs")

                val maxRestoreGlyph = MemoryUtil.memUTF8(if (ui.isMaximized) GLYPH_RESTORE else GLYPH_MAXIMIZE, true)
                ui.maximizeHandler = {
                    MemoryUtil.memUTF8(GLYPH_RESTORE, true, maxRestoreGlyph, 0)
                }
                ui.restoreHandler = {
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

                var mainLayer = NO_BLOCK
                var panelLayer = NO_BLOCK
                var menuLayer = NO_BLOCK
                var dialogLayer = NO_BLOCK

                var topBar = NO_BLOCK
                var contentPanel = NO_BLOCK
                var leftPanel = NO_BLOCK
                var rightPanel = NO_BLOCK

                root {
                    mainLayer = block {
                        isFallThrough = true
                    }
                    panelLayer = block {
                        isFallThrough = false
                        isMouseAware = true
                        isVisible = false
                        shape = FILL_GREY_OUT
                    }
                    menuLayer = block {
                        isFallThrough = true
                    }
                    dialogLayer = block {
                        isFallThrough = false
                        isMouseAware = true
                        isVisible = false
                        shape = FILL_GREY_OUT
                    }
                }
                var overwriteWarningDialog = NO_BLOCK
                var errorMessageDialog = NO_BLOCK
                var preferencesPanel = NO_BLOCK
                val noop = {}
                val dialogCallback = mRef(noop)
                val errorHandler: ErrorDialog = object: ErrorDialog {

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
                            saveProject(currentProject.value, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
                            dialogLayer.isVisible = false
                            dialogCallback.value()
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
                currentProject.listener { old, new ->
                    updateTitle(titleText, new)
                    doesActiveProjectExist.value = new != null
                    if (new != null) {
                        addProjectToRecentProjects(new.file, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                    }
                }
                val rememberWindowState = ref(preferences.rememberWindowState)
                val projectDir = DynamicTextReference(preferences.projectDir.canonicalPath, 1024, TEXT_STYLE_NORMAL)
                val tempDir = DynamicTextReference(preferences.tempDir.canonicalPath, 1024, TEXT_STYLE_NORMAL)
                panelLayer {
                    preferencesPanel = panel(650.0f) {
                        vSizing = SHRINK
                        val shrinkGroup = hShrinkGroup()
                        vSpacer(LARGE_SPACER_SIZE)
                        vToggleRow(rememberWindowState, LARGE_ROW_HEIGHT, text("Remember window state:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                        vFolderRow(projectDir, LARGE_ROW_HEIGHT, text("Project folder:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, ui)
                        vFolderRow(tempDir, LARGE_ROW_HEIGHT, text("Temporary folder:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, ui)
                        vSpacer(MEDIUM_SPACER_SIZE)
                        vButtonRow(LARGE_ROW_HEIGHT) {
                            button(text("Save"), DIALOG_BUTTON_STYLE) {
                                preferences.rememberWindowState = rememberWindowState.value
                                preferences.projectDir = File(projectDir.reference.value)
                                preferences.tempDir = File(tempDir.reference.value)
                                savePreferences(preferences)
                                preferencesPanel.isVisible = false
                                panelLayer.isVisible = false
                            }.with { width = 60.0f }
                            hSpacer(SMALL_SPACER_SIZE)
                            button(text("Cancel"), DIALOG_BUTTON_STYLE) {
                                rememberWindowState.value = preferences.rememberWindowState
                                projectDir.reference.value = preferences.projectDir.canonicalPath
                                tempDir.reference.value = preferences.tempDir.canonicalPath
                                preferencesPanel.isVisible = false
                                panelLayer.isVisible = false
                            }.with { width = 60.0f }
                        }
                        vSpacer(MEDIUM_SPACER_SIZE)
                    }
                }
                mainLayer {
                    topBar = block {
                        vSizing = STATIC
                        height = MEDIUM_ROW_HEIGHT
                        layout = VERTICAL
                        icon(icon, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT)
                        hSpacer(SMALL_SPACER_SIZE)
                        menuBar(menuLayer, MEDIUM_ROW_HEIGHT, TEXT_STYLE_BUTTON, COLOR_DISABLED_CLICKABLE) {
                            menu("File") {
                                menuItem("New...", "Ctrl+N", BLOCK_GLYPH_NEW_FILE) {
                                    if (currentProject.value != null) {
                                        dialogLayer.isVisible = true
                                        overwriteWarningReference.value = "Do you want to save the current project before creating a new one?"
                                        overwriteWarningDialog.isVisible = true
                                        dialogCallback.value = {
                                            currentProject.value = Project()
                                            dialogCallback.value = noop
                                        }
                                    } else {
                                        currentProject.value = Project()
                                    }
                                }
                                menuItem("Open...", "Ctrl+O", BLOCK_GLYPH_OPEN_FOLDER) {
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
                                menuItem("Export maps", "Ctrl+E", isActive = doesActiveProjectExist) {
                                    println("Export maps...")
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
                        block {
                            leftPanel = this
                            hSizing = STATIC
                            width = 268.0f
                            layout = HORIZONTAL
                            hAlign = LEFT
                            block {
                                hSizing = GROW
                                layout = HORIZONTAL
                                hAlign = LEFT
                                block {
                                    xOffset = SMALL_SPACER_SIZE
                                    yOffset = SMALL_SPACER_SIZE
                                    width = -SMALL_SPACER_SIZE
                                    height = -2 * SMALL_SPACER_SIZE
                                    block {
                                        hSizing = STATIC
                                        width = 1.0f
                                        hAlign = LEFT
                                        layout = HORIZONTAL
                                        shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                                    }
                                    block {
                                        hSizing = GROW
                                        layout = HORIZONTAL
                                        block {
                                            vSizing = STATIC
                                            height = 1.0f
                                            layout = VERTICAL
                                            shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                                        }
                                        block {
                                            vSizing = GROW
                                            layout = VERTICAL
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            block {
                                                hSizing = GROW
                                                layout = HORIZONTAL
                                                val shrinkGroup = hShrinkGroup()
                                                vSpacer(MEDIUM_SPACER_SIZE)
                                                vToggleRow(waterPlaneOn, LARGE_ROW_HEIGHT, text("Water:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                                vToggleRow(perspectiveOn, LARGE_ROW_HEIGHT, text("Perspective:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                                vToggleRow(rotateAroundCamera, LARGE_ROW_HEIGHT, text("Rotate camera:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                                vSliderRow(heightMapScaleFactor, LARGE_ROW_HEIGHT, text("Height scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, heightScaleFunction, heightScaleFunctionInverse)
                                                vButtonRow(LARGE_ROW_HEIGHT) {
                                                    button(text("Reset view"), NORMAL_TEXT_BUTTON_STYLE) { resetView.value = true }
                                                    button(text("Reset height"), NORMAL_TEXT_BUTTON_STYLE) { heightMapScaleFactor.value = DEFAULT_HEIGHT_SCALE }
                                                }
                                            }
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                        }
                                        block {
                                            vSizing = STATIC
                                            height = 1.0f
                                            layout = VERTICAL
                                            shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                                        }
                                    }
                                    block {
                                        hSizing = STATIC
                                        width = 1.0f
                                        hAlign = LEFT
                                        layout = HORIZONTAL
                                        shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                                    }
                                }
                            }
                            block {
                                hSizing = STATIC
                                width = SMALL_SPACER_SIZE
                                layout = HORIZONTAL
                                val grabber = button(NO_TEXT, NORMAL_TEXT_BUTTON_STYLE { copy(
                                        template = template.copy(
                                                vSizing = STATIC,
                                                height = 3 * LARGE_ROW_HEIGHT,
                                                vAlign = MIDDLE,
                                                hSizing = RELATIVE,
                                                width = -2.0f,
                                                hAlign = CENTER))})
                                var lastX = 0.0f
                                onMouseDown { button, x, y ->
                                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                                        lastX = x.toFloat()
                                    }
                                }
                                onMouseDrag { button, x, y ->
                                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                                        val delta = x - lastX
                                        val adjustedDelta = Math.min(root.width / 2.0f, Math.max(220.0f, leftPanel.width + delta)) - leftPanel.width
                                        lastX += adjustedDelta
                                        leftPanel.width += adjustedDelta
                                    }
                                }
                                supplantEvents(grabber)
                            }
                        }
                        rightPanel = block {
                            hSizing = GROW
                            layout = HORIZONTAL
                            hAlign = LEFT
                            block {
                                xOffset = 0.0f
                                yOffset = SMALL_SPACER_SIZE
                                width = -SMALL_SPACER_SIZE
                                height = -2 * SMALL_SPACER_SIZE
                                block {
                                    hSizing = STATIC
                                    width = 1.0f
                                    hAlign = LEFT
                                    layout = HORIZONTAL
                                    shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                                }
                                block {
                                    hSizing = GROW
                                    layout = HORIZONTAL
                                    block {
                                        vSizing = STATIC
                                        height = 1.0f
                                        layout = VERTICAL
                                        shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                                    }
                                    block {
                                        vSizing = GROW
                                        layout = VERTICAL
                                        meshViewport3D(meshViewport)
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
                                        vSizing = STATIC
                                        height = 1.0f
                                        layout = VERTICAL
                                        shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                                    }
                                }
                                block {
                                    hSizing = STATIC
                                    width = 1.0f
                                    hAlign = LEFT
                                    layout = HORIZONTAL
                                    shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                                }
                            }
                        }
                    }
                    resizeAreaSouthEast = resizeArea(ShapeTriangle.Direction.SOUTH_EAST)
                    resizeAreaSouthWest = resizeArea(ShapeTriangle.Direction.SOUTH_WEST)
                }
            }
        }

        ui(uiLayout, preferences.windowState) {

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
