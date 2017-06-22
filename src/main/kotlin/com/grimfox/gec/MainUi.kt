package com.grimfox.gec

import com.fasterxml.jackson.core.JsonParseException
import com.grimfox.gec.command.BuildContinent
import com.grimfox.gec.command.BuildContinent.ParameterSet
import com.grimfox.gec.command.BuildContinent.RegionSplines
import com.grimfox.gec.extensions.value
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.HistoryQueue
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.opengl.loadTexture2D
import com.grimfox.gec.ui.LOG
import com.grimfox.gec.ui.layout
import com.grimfox.gec.ui.set
import com.grimfox.gec.ui.ui
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.CENTER
import com.grimfox.gec.ui.widgets.HorizontalAlignment.LEFT
import com.grimfox.gec.ui.widgets.Layout.HORIZONTAL
import com.grimfox.gec.ui.widgets.Layout.VERTICAL
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.renderMapImage
import com.grimfox.gec.ui.widgets.VerticalAlignment.MIDDLE
import com.grimfox.gec.util.clamp
import com.grimfox.gec.util.mRef
import com.grimfox.gec.util.ref
import nl.komponents.kovenant.task
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.*
import java.util.concurrent.locks.ReentrantLock

object MainUi {

    private class CurrentState(val parameters: ParameterSet,
                               val graph: Graph,
                               val regionMask: Matrix<Byte>,
                               val regionSplines: RegionSplines)

    @JvmStatic fun main(vararg args: String) {
        val executor = executor
        val preferences = preferences

        for (i in 1..2) {
            task { BuildContinent().generateRegions(ParameterSet(seed = i.toLong()), executor) }
        }
        val random = Random()

        val cachedGraph256 = preferences.cachedGraph256!!

        val cachedGraph512 = preferences.cachedGraph512!!

        val cachedGraph1024 = preferences.cachedGraph1024!!

        val DEFAULT_HEIGHT_SCALE = 50.0f
        val MAX_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 10
        val MIN_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 0
        val HEIGHT_SCALE_CONST = DEFAULT_HEIGHT_SCALE * 0.968746f
        val HEIGHT_SCALE_MULTIPLIER = DEFAULT_HEIGHT_SCALE * 32.00223
        val heightScaleFunction = { scale: Float ->
            Math.min(MAX_HEIGHT_SCALE, Math.max(MIN_HEIGHT_SCALE, if (scale <= 0.5f) {
                scale * (2 * DEFAULT_HEIGHT_SCALE)
            } else {
                (HEIGHT_SCALE_CONST + (HEIGHT_SCALE_MULTIPLIER * Math.pow(scale - 0.46874918, 2.0))).toFloat()
            }))
        }
        val heightScaleFunctionInverse = { value: Float ->
            Math.min(1.0f, Math.max(0.0f, if (value <= DEFAULT_HEIGHT_SCALE) {
                value / (2 * DEFAULT_HEIGHT_SCALE)
            } else {
                (Math.sqrt((value - HEIGHT_SCALE_CONST) / HEIGHT_SCALE_MULTIPLIER) + 0.46874918).toFloat()
            }))
        }
        fun linearClampedScaleFunction(range: IntRange): (Float) -> Int {
            return { scale: Float ->
                clamp(Math.round(scale * (range.last - range.first)) + range.first, range.first, range.last)
            }
        }
        fun linearClampedScaleFunctionInverse(range: IntRange): (Int) -> Float {
            return { value: Int ->
                clamp(Math.abs((value - range.first).toFloat() / (range.last - range.first)), 0.0f, 1.0f)
            }
        }
        fun linearClampedScaleFunction(min: Float, max: Float): (Float) -> Float {
            return { scale: Float ->
                clamp((scale * (max - min)) + min, min, max)
            }
        }
        fun linearClampedScaleFunctionInverse(min: Float, max: Float): (Float) -> Float {
            return { value: Float ->
                clamp(Math.abs((value - min) / (max - min)), 0.0f, 1.0f)
            }
        }
        val heightMapScaleFactor = ref(DEFAULT_HEIGHT_SCALE)
        val waterPlaneOn = ref(true)
        val perspectiveOn = ref(true)
        val rotateAroundCamera = ref(false)
        val resetView = mRef(false)
        val imageMode = ref(2)

        val titleText = DynamicTextReference("WorldKit - No Project", 67, TEXT_STYLE_NORMAL)

        val overwriteWarningDynamic = dynamicParagraph("", 300)
        val overwriteWarningText = overwriteWarningDynamic.text
        val overwriteWarningReference = overwriteWarningDynamic.reference

        val errorMessageDynamic = dynamicParagraph("", 600)
        val errorMessageText = errorMessageDynamic.text
        val errorMessageReference = errorMessageDynamic.reference

        val meshViewport = MeshViewport3D(resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor, imageMode)

        val uiLayout = layout { ui ->
            val uiLayout = this
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
                currentProject.listener { _, new ->
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
                        vFolderRow(projectDir, LARGE_ROW_HEIGHT, text("Project folder:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, false, ui)
                        vFolderRow(tempDir, LARGE_ROW_HEIGHT, text("Temporary folder:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, false, ui)
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
                                menuItem("Export maps...", "Ctrl+E", isActive = doesActiveProjectExist) {
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
                            width = 350.0f
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
                                        xOffset = 1.0f
                                        yOffset = 1.0f
                                        width = -2.0f
                                        height = -2.0f
                                        block {
                                            hSpacer(MEDIUM_SPACER_SIZE)
                                            val currentState = ref<CurrentState?>(null)
                                            block {
                                                hSizing = GROW
                                                layout = HORIZONTAL
                                                val shrinkGroup = hShrinkGroup()
                                                vSpacer(MEDIUM_SPACER_SIZE)
                                                val allowUnsafe = ref(false)
                                                val seed = ref(1L)
                                                val mapScale = ref(4)
                                                val regions = ref(8)
                                                val islands = ref(1)
                                                val stride = ref(7)
                                                val startPoints = ref(2)
                                                val reduction = ref(7)
                                                val connectedness = ref(0.114f)
                                                val regionSize = ref(0.034f)
                                                val iterations = ref(5)
                                                val historyBackQueue = HistoryQueue<ParameterSet>(100)
                                                val historyCurrent = ref<ParameterSet?>(null)
                                                val historyForwardQueue = HistoryQueue<ParameterSet>(100)
                                                val onUpdateParamsFun = {
                                                    if (!allowUnsafe.value) {
                                                        stride.value = ((regions.value + 3) / 4) + 5
                                                        val stride2 = stride.value * stride.value
                                                        val approxInland = stride2 - (4 * stride.value) + 4
                                                        startPoints.value = Math.min(6, Math.max(1, Math.floor(approxInland / (regions.value.toDouble() + (islands.value / 2))).toInt() - 1))
                                                        reduction.value = Math.floor((approxInland - (startPoints.value * regions.value)) * 0.8).toInt()
                                                        connectedness.value = Math.round(((1.0f / stride.value) * 0.77f) * 1000.0f) / 1000.0f
                                                        val unitLength = 1.0f / stride.value
                                                        val unit2 = unitLength * unitLength
                                                        regionSize.value = Math.round((((unit2 * (approxInland - reduction.value)) / regions.value) * 0.763f) * 1000.0f) / 1000.0f
                                                    }
                                                }
                                                onUpdateParamsFun()
                                                regions.listener { _, _ ->
                                                    onUpdateParamsFun()
                                                }
                                                islands.listener { _, _ ->
                                                    onUpdateParamsFun()
                                                }
                                                vLongInputRow(seed, LARGE_ROW_HEIGHT, text("Seed:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, uiLayout) {
                                                    hSpacer(SMALL_SPACER_SIZE)
                                                    button(text("Randomize"), NORMAL_TEXT_BUTTON_STYLE) {
                                                        val randomSeed = random.nextLong()
                                                        val randomString = randomSeed.toString()
                                                        if (randomString.length > 18) {
                                                            seed.value = randomString.substring(0, 18).toLong()
                                                        } else {
                                                            seed.value = randomSeed
                                                        }
                                                    }
                                                }
                                                vSliderWithValueRow(mapScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map Scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
                                                vSliderWithValueRow(regions, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Regions:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
                                                vSliderWithValueRow(islands, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Island strength:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..5), linearClampedScaleFunctionInverse(0..5))
                                                vSliderRow(iterations, LARGE_ROW_HEIGHT, text("Iterations:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(2..30), linearClampedScaleFunctionInverse(2..30))
                                                vSpacer(MEDIUM_ROW_HEIGHT)
                                                vToggleRow(allowUnsafe, LARGE_ROW_HEIGHT, text("Unsafe mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                                val unsafeBlocks = ArrayList<Block>()
                                                unsafeBlocks.add(vSliderWithValueRow(stride, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Stride:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(5..10), linearClampedScaleFunctionInverse(5..10)).with { isMouseAware = allowUnsafe.value})
                                                unsafeBlocks.add(vSliderWithValueRow(startPoints, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Start points:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..6), linearClampedScaleFunctionInverse(1..6)).with { isMouseAware = allowUnsafe.value})
                                                unsafeBlocks.add(vSliderWithValueRow(reduction, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Reduction:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..40), linearClampedScaleFunctionInverse(0..40)).with { isMouseAware = allowUnsafe.value})
                                                unsafeBlocks.add(vSliderWithValueRow(connectedness, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Connection:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.004f, 0.2f), linearClampedScaleFunctionInverse(0.004f, 0.2f)).with { isMouseAware = allowUnsafe.value})
                                                unsafeBlocks.add(vSliderWithValueRow(regionSize, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Region size:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.005f, 0.05f), linearClampedScaleFunctionInverse(0.005f, 0.05f)).with { isMouseAware = allowUnsafe.value})
                                                allowUnsafe.listener { _, new ->
                                                    if (!new) {
                                                        onUpdateParamsFun()
                                                    }
                                                    unsafeBlocks.forEach {
                                                        it.isMouseAware = new
                                                    }
                                                }
//                                                vSliderRow(heightMapScaleFactor, LARGE_ROW_HEIGHT, text("Height scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, heightScaleFunction, heightScaleFunctionInverse)
//                                                vToggleRow(waterPlaneOn, LARGE_ROW_HEIGHT, text("Water:"), shrinkGroup, MEDIUM_SPACER_SIZE)
//                                                vToggleRow(perspectiveOn, LARGE_ROW_HEIGHT, text("Perspective:"), shrinkGroup, MEDIUM_SPACER_SIZE)
//                                                vToggleRow(rotateAroundCamera, LARGE_ROW_HEIGHT, text("Rotate camera:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                                                vSpacer(MEDIUM_ROW_HEIGHT)

                                                val generateLock = ReentrantLock()
                                                var generating = false

                                                var generateButton = NO_BLOCK
                                                var generateLabel = NO_BLOCK
                                                var generateRandomButton = NO_BLOCK
                                                var generateRandomLabel = NO_BLOCK
                                                var buildButton = NO_BLOCK
                                                var buildLabel = NO_BLOCK

                                                var backButton = NO_BLOCK
                                                var forwardButton = NO_BLOCK
                                                var backLabel = NO_BLOCK
                                                var forwardLabel = NO_BLOCK

                                                fun disableGenerateButtons() {
                                                    generateButton.isVisible = false
                                                    generateLabel.isVisible = true
                                                    generateRandomButton.isVisible = false
                                                    generateRandomLabel.isVisible = true
                                                    buildButton.isVisible = false
                                                    buildLabel.isVisible = true
                                                    backButton.isVisible = false
                                                    backLabel.isVisible = true
                                                    forwardButton.isVisible = false
                                                    forwardLabel.isVisible = true
                                                }

                                                fun enableGenerateButtons() {
                                                    generateLabel.isVisible = false
                                                    generateButton.isVisible = true
                                                    generateRandomLabel.isVisible = false
                                                    generateRandomButton.isVisible = true
                                                    buildLabel.isVisible = false
                                                    buildButton.isVisible = true
                                                    backButton.isVisible = historyBackQueue.size != 0
                                                    backLabel.isVisible = historyBackQueue.size == 0
                                                    forwardButton.isVisible = historyForwardQueue.size != 0
                                                    forwardLabel.isVisible = historyForwardQueue.size == 0
                                                }

                                                fun doGeneration(doWork: () -> Unit) {
                                                    if (generateLock.tryLock()) {
                                                        try {
                                                            if (!generating) {
                                                                generating = true
                                                                try {
                                                                    disableGenerateButtons()
                                                                    try {
                                                                        doWork()
                                                                    } finally {
                                                                        enableGenerateButtons()
                                                                    }
                                                                } finally {
                                                                    generating = false
                                                                }
                                                            }
                                                        } finally {
                                                            generateLock.unlock()
                                                        }
                                                    }
                                                }

                                                vButtonRow(LARGE_ROW_HEIGHT) {

                                                    generateButton = button(text("Generate"), NORMAL_TEXT_BUTTON_STYLE) {
                                                        doGeneration {
                                                            val parameters = ParameterSet(
                                                                    seed = seed.value,
                                                                    mapScale = mapScale.value,
                                                                    regionCount = regions.value,
                                                                    stride = stride.value,
                                                                    islandDesire = islands.value,
                                                                    regionPoints = startPoints.value,
                                                                    initialReduction = reduction.value,
                                                                    connectedness = connectedness.value,
                                                                    regionSize = regionSize.value,
                                                                    maxRegionTries = iterations.value * 10,
                                                                    maxIslandTries = iterations.value * 100)
                                                            val builder = BuildContinent()
                                                            val resultPair = builder.generateRegions(parameters.copy(), executor)
                                                            val regionSplines = builder.generateRegionSplines(random, resultPair, parameters.mapScale)
                                                            val regionTextureId = renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints, regionSplines.mountainPoints)
                                                            meshViewport.setImage(regionTextureId)
                                                            currentState.value = CurrentState(parameters.copy(), resultPair.first, resultPair.second, regionSplines)
                                                            imageMode.value = 1
                                                            val historyLast = historyCurrent.value
                                                            if (historyLast != null) {
                                                                if ((historyBackQueue.size == 0 || historyBackQueue.peek() != historyLast) && parameters != historyLast) {
                                                                    historyBackQueue.push(historyLast.copy())
                                                                }
                                                            }
                                                            historyForwardQueue.clear()
                                                            historyCurrent.value = parameters.copy()
                                                        }
                                                    }
                                                    generateLabel = button(text("Generate"), DISABLED_TEXT_BUTTON_STYLE) {}
                                                    generateLabel.isMouseAware = false
                                                    generateLabel.isVisible = false
                                                    hSpacer(SMALL_SPACER_SIZE)
                                                    generateRandomButton = button(text("Generate random"), NORMAL_TEXT_BUTTON_STYLE) {
                                                        doGeneration {
                                                            val randomSeed = random.nextLong()
                                                            val randomString = randomSeed.toString()
                                                            if (randomString.length > 18) {
                                                                seed.value = randomString.substring(0, 18).toLong()
                                                            } else {
                                                                seed.value = randomSeed
                                                            }
                                                            val parameters = ParameterSet(
                                                                    seed = seed.value,
                                                                    mapScale = mapScale.value,
                                                                    regionCount = regions.value,
                                                                    stride = stride.value,
                                                                    islandDesire = islands.value,
                                                                    regionPoints = startPoints.value,
                                                                    initialReduction = reduction.value,
                                                                    connectedness = connectedness.value,
                                                                    regionSize = regionSize.value,
                                                                    maxRegionTries = iterations.value * 10,
                                                                    maxIslandTries = iterations.value * 100)
                                                            val builder = BuildContinent()
                                                            val resultPair = builder.generateRegions(parameters.copy(), executor)
                                                            val regionSplines = builder.generateRegionSplines(random, resultPair, parameters.mapScale)
                                                            val regionTextureId = renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints, regionSplines.mountainPoints)
                                                            meshViewport.setImage(regionTextureId)
                                                            currentState.value = CurrentState(parameters.copy(), resultPair.first, resultPair.second, regionSplines)
                                                            imageMode.value = 1
                                                            val historyLast = historyCurrent.value
                                                            if (historyLast != null) {
                                                                if ((historyBackQueue.size == 0 || historyBackQueue.peek() != historyLast) && parameters != historyLast) {
                                                                    historyBackQueue.push(historyLast.copy())
                                                                }
                                                            }
                                                            historyForwardQueue.clear()
                                                            historyCurrent.value = parameters.copy()
                                                        }
                                                    }
                                                    generateRandomLabel = button(text("Generate random"), DISABLED_TEXT_BUTTON_STYLE) {}
                                                    generateRandomLabel.isMouseAware = false
                                                    generateRandomLabel.isVisible = false
                                                    hSpacer(SMALL_SPACER_SIZE)
                                                    fun syncParameterValues(parameters: ParameterSet) {
                                                        val randomSeed = parameters.seed
                                                        val randomString = randomSeed.toString()
                                                        if (randomString.length > 18) {
                                                            seed.value = randomString.substring(0, 18).toLong()
                                                        } else {
                                                            seed.value = randomSeed
                                                        }
                                                        mapScale.value = parameters.mapScale
                                                        regions.value = parameters.regionCount
                                                        stride.value = parameters.stride
                                                        islands.value = parameters.islandDesire
                                                        startPoints.value = parameters.regionPoints
                                                        reduction.value = parameters.initialReduction
                                                        connectedness.value = parameters.connectedness
                                                        regionSize.value = parameters.regionSize
                                                        iterations.value = parameters.maxRegionTries / 10
                                                        iterations.value = parameters.maxIslandTries / 100
                                                    }
                                                    backButton = button(text("Back"), NORMAL_TEXT_BUTTON_STYLE) {
                                                        doGeneration {
                                                            val parameters = historyBackQueue.pop()
                                                            if (parameters != null) {
                                                                val historyLast = historyCurrent.value
                                                                if (historyLast != null) {
                                                                    historyForwardQueue.push(historyLast.copy())
                                                                }
                                                                syncParameterValues(parameters)
                                                                val builder = BuildContinent()
                                                                val resultPair = builder.generateRegions(parameters.copy(), executor)
                                                                val regionSplines = builder.generateRegionSplines(random, resultPair, parameters.mapScale)
                                                                val regionTextureId = renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints, regionSplines.mountainPoints)
                                                                meshViewport.setImage(regionTextureId)
                                                                currentState.value = CurrentState(parameters.copy(), resultPair.first, resultPair.second, regionSplines)
                                                                imageMode.value = 1
                                                                historyCurrent.value = parameters.copy()
                                                            }
                                                        }
                                                    }
                                                    backLabel = button(text("Back"), DISABLED_TEXT_BUTTON_STYLE) {}
                                                    backLabel.isMouseAware = false
                                                    hSpacer(SMALL_SPACER_SIZE)
                                                    forwardButton = button(text("Forward"), NORMAL_TEXT_BUTTON_STYLE) {
                                                        doGeneration {
                                                            val parameters = historyForwardQueue.pop()
                                                            if (parameters != null) {
                                                                val historyLast = historyCurrent.value
                                                                if (historyLast != null) {
                                                                    historyBackQueue.push(historyLast.copy())
                                                                }
                                                                syncParameterValues(parameters)
                                                                val builder = BuildContinent()
                                                                val resultPair = builder.generateRegions(parameters.copy(), executor)
                                                                val regionSplines = builder.generateRegionSplines(random, resultPair, parameters.mapScale)
                                                                val regionTextureId = renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints, regionSplines.mountainPoints)
                                                                meshViewport.setImage(regionTextureId)
                                                                currentState.value = CurrentState(parameters.copy(), resultPair.first, resultPair.second, regionSplines)
                                                                imageMode.value = 1
                                                                historyCurrent.value = parameters.copy()
                                                            }
                                                        }
                                                    }
                                                    forwardLabel = button(text("Forward"), DISABLED_TEXT_BUTTON_STYLE) {}
                                                    forwardLabel.isMouseAware = false
                                                    backButton.isVisible = false
                                                    forwardButton.isVisible = false
                                                }
                                                vButtonRow(LARGE_ROW_HEIGHT) {
                                                    buildButton = button(text("Build mesh"), NORMAL_TEXT_BUTTON_STYLE) {
                                                        doGeneration {
                                                            val currentStateValue = currentState.value
                                                            if (currentStateValue != null) {
                                                                meshViewport.setHeightmap(BuildContinent().generateWaterFlows(currentStateValue.parameters, currentStateValue.graph, currentStateValue.regionMask, currentStateValue.regionSplines, cachedGraph256.value, cachedGraph512.value, cachedGraph1024.value, executor), 4096)
//                                                            meshViewport.setTexture(BuildContinent().generateLandmass(currentStateValue.first, currentStateValue.second, currentStateValue.third, executor))
                                                                imageMode.value = 2
                                                            }
                                                        }
                                                    }
                                                    buildLabel = button(text("Build mesh"), DISABLED_TEXT_BUTTON_STYLE) {}
                                                    buildLabel.isMouseAware = false
                                                    buildLabel.isVisible = false
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
                                        shape = SHAPE_BORDER_ONLY
                                        canOverflow = true
                                        isMouseAware = false
                                    }
                                }
                            }
                            block {
                                hSizing = STATIC
                                width = SMALL_SPACER_SIZE
                                layout = HORIZONTAL
                                val grabber = button(NO_TEXT, NORMAL_TEXT_BUTTON_STYLE {
                                    copy(
                                            template = template.copy(
                                                    vSizing = STATIC,
                                                    height = 3 * LARGE_ROW_HEIGHT,
                                                    vAlign = MIDDLE,
                                                    hSizing = RELATIVE,
                                                    width = -2.0f,
                                                    hAlign = CENTER))
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
                                        val adjustedDelta = Math.max(350.0f, Math.min(root.width * 0.75f, leftPanel.width + delta)) - leftPanel.width
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
                                yOffset = SMALL_SPACER_SIZE
                                width = -SMALL_SPACER_SIZE
                                height = -2 * SMALL_SPACER_SIZE
                                block {
                                    xOffset = 1.0f
                                    yOffset = 1.0f
                                    width = -2.0f
                                    height = -2.0f
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
