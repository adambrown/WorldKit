package com.grimfox.gec

import com.grimfox.gec.opengl.loadTexture2D
import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.util.geometry.max
import com.grimfox.gec.util.mRef
import com.grimfox.gec.util.ref
import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil

object MainUi {

    @JvmStatic fun main(vararg args: String) {
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
//        val dynamicTextRef = ref("some dynamic text")

        val meshViewport = MeshViewport3D(resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor)

        val uiLayout = layout { ui ->
            ui {
                background.set(45, 45, 48)

                textFont.value = createFont("/fonts/FiraSans.ttf", "FiraSans")
                glyphFont.value = createFont("/fonts/WorldKitUi.ttf", "Glyphs")

                val glyphIndex = Array(95) { i -> (i + 32).toChar().toString() }
                val glyphClose = glyphIndex[0]
                val glyphMinimize = glyphIndex[1]
                val glyphRestore = glyphIndex[2]
                val glyphMaximize = glyphIndex[3]
                val glyphSave = glyphIndex[4]
                val glyphFile = glyphIndex[5]
                val glyphFolder = glyphIndex[6]
                val glyphLoadArrow = glyphIndex[7]
                val glyphStar = glyphIndex[8]

                val maxRestoreGlyph = MemoryUtil.memUTF8(if (ui.isMaximized) glyphRestore else glyphMaximize, true)
                ui.maximizeHandler = {
                    MemoryUtil.memUTF8(glyphRestore, true, maxRestoreGlyph, 0)
                }
                ui.restoreHandler = {
                    MemoryUtil.memUTF8(glyphMaximize, true, maxRestoreGlyph, 0)
                }

//                val dynamicTextBuffer = ByteBuffer.allocateDirect(250)
//
//                val dynamicTextBytes = MemoryUtil.memUTF8(dynamicTextRef.value, true, dynamicTextBuffer, 0)
//                dynamicTextBuffer.limit(dynamicTextBytes + 1)
//                val dynamicText = DynamicTextUtf8(dynamicTextBuffer, TEXT_STYLE_NORMAL)
//                dynamicTextRef.listener { old, new ->
//                    val byteCount = MemoryUtil.memUTF8(dynamicTextRef.value, true, dynamicTextBuffer, 0)
//                    dynamicTextBuffer.limit(byteCount + 1)
//                }

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

                var layer0 = NO_BLOCK
                var layer1 = NO_BLOCK

                var topBar = NO_BLOCK
                var contentPanel = NO_BLOCK
                var leftPanel = NO_BLOCK
                var rightPanel = NO_BLOCK

                root {
                    layer0 = block {
                        isFallThrough = true
                    }
                    layer1 = block {
                        isFallThrough = true
                    }
                }
                layer0 {
                    topBar = block {
                        vSizing = STATIC
                        height = MEDIUM_ROW_HEIGHT
                        layout = VERTICAL
                        icon(icon, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT)
                        hSpacer(SMALL_SPACER_SIZE)




                        block {
                            val menu = this
                            topBar.renderChildren.remove(menu)
                            layer1.renderChildren.add(menu)
                            var active = false
                            var mouseDownOnActivator = false
                            var mouseOverActivator = false
                            var mouseOverDeActivator = false
                            hSizing = SHRINK
                            vSizing = STATIC
                            height = MEDIUM_ROW_HEIGHT
                            layout = HORIZONTAL
                            isFallThrough = true
                            block {
                                hSizing = SHRINK
                                vSizing = STATIC
                                vAlign = BOTTOM
                                height = SMALL_ROW_HEIGHT
                                isFallThrough = true
                                val activator = block {
                                    hSizing = SHRINK
                                    vSizing = STATIC
                                    height = SMALL_ROW_HEIGHT
                                    padLeft = SMALL_SPACER_SIZE
                                    padRight = SMALL_SPACER_SIZE
                                    block {
                                        hSizing = SHRINK
                                        vSizing = SHRINK
                                        hAlign = CENTER
                                        vAlign = MIDDLE
                                        text = text("File", TEXT_STYLE_BUTTON)
                                        isMouseAware = false
                                    }
                                }
                                val dropDown = block {
                                    val dropDown = this
                                    isVisible = true
                                    isMouseAware = true
                                    layout = ABSOLUTE
                                    yOffset = SMALL_ROW_HEIGHT - 1.0f
                                    xOffset = -1.0f
                                    hSizing = STATIC
                                    vSizing = STATIC
                                    width = 0.0f
                                    height = 0.0f
                                    canOverflow = true
                                    isFallThrough = true
                                    block {
                                        layout = ABSOLUTE
                                        hSizing = SHRINK
                                        vSizing = SHRINK
                                        canOverflow = true
                                        block {
                                            xOffset = 3.0f
                                            yOffset = 3.0f
                                            canOverflow = true
                                            shape = SHAPE_DROP_SHADOW
                                        }
                                        block {
                                            layout = ABSOLUTE
                                            hSizing = SHRINK
                                            vSizing = SHRINK
                                            canOverflow = true
                                            shape = SHAPE_MENU_BORDER
                                            block {
                                                hSizing = SHRINK
                                                vSizing = SHRINK
                                                padLeft = 1.0f
                                                padRight = 1.0f
                                                padTop = 1.0f
                                                padBottom = 1.0f
                                                shape = SHAPE_MENU_BACKGROUND
                                                block {
                                                    hSizing = SHRINK
                                                    vSizing = SHRINK
                                                    padLeft = 2.0f
                                                    padRight = 2.0f
                                                    padTop = 2.0f
                                                    padBottom = 2.0f
                                                    val shrinkGroup = hShrinkGroup()
                                                    menuItem(text("New project", TEXT_STYLE_BUTTON),
                                                            createMultiGlyph(
                                                                    GlyphLayer(glyphFile, glyphFont, 16.0f, COLOR_GLYPH_WHITE, 0.0f, 0.0f),
                                                                    GlyphLayer(glyphStar, glyphFont, 11.5f, COLOR_GLYPH_BLACK, -1.9f, -2.0f),
                                                                    GlyphLayer(glyphStar, glyphFont, 9.0f, COLOR_GLYPH_GREEN, -0.6f, -1.0f)),
                                                            text("Ctrl+N", TEXT_STYLE_BUTTON), MEDIUM_ROW_HEIGHT,
                                                            shrinkGroup)
                                                    menuItem(text("Open project", TEXT_STYLE_BUTTON),
                                                            createMultiGlyph(
                                                                    GlyphLayer(glyphFolder, glyphFont, 16.0f, COLOR_GLYPH_YELLOW, 0.0f, 0.0f),
                                                                    GlyphLayer(glyphLoadArrow, glyphFont, 14.5f, COLOR_GLYPH_BLACK, -2.0f, -4.0f),
                                                                    GlyphLayer(glyphLoadArrow, glyphFont, 12.0f, COLOR_GLYPH_BLUE, -1.0f, -3.5f)),
                                                            text("Ctrl+O", TEXT_STYLE_BUTTON), MEDIUM_ROW_HEIGHT,
                                                            shrinkGroup)
                                                    menuItem(text("Save project", TEXT_STYLE_BUTTON), createMultiGlyph(GlyphLayer(glyphSave, glyphFont, 16.0f, COLOR_GLYPH_BLUE, 0.0f, 0.0f)), text("Ctrl+S", TEXT_STYLE_BUTTON), MEDIUM_ROW_HEIGHT, shrinkGroup)
                                                    menuDivider(3.0f, shrinkGroup)
                                                    menuItem(text("Export maps...", TEXT_STYLE_BUTTON), { block {} }, text("Ctrl+E", TEXT_STYLE_BUTTON), MEDIUM_ROW_HEIGHT, shrinkGroup)
                                                    menuDivider(3.0f, shrinkGroup)
                                                    menuItem(text("Exit", TEXT_STYLE_BUTTON), createMultiGlyph(GlyphLayer(glyphClose, glyphFont, 16.0f, COLOR_GLYPH_RED, 0.0f, 0.0f)), text("Alt+F4", TEXT_STYLE_BUTTON), MEDIUM_ROW_HEIGHT, shrinkGroup) {
                                                        closeWindow()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    block {
                                        layout = ABSOLUTE
                                        hSizing = SHRINK
                                        vSizing = STATIC
                                        height = SMALL_ROW_HEIGHT + 1
                                        yOffset = -SMALL_ROW_HEIGHT
                                        canOverflow = true
                                        shape = SHAPE_MENU_BORDER
                                        block {
                                            hSizing = SHRINK
                                            vSizing = STATIC
                                            height = SMALL_ROW_HEIGHT + 2
                                            padLeft = 1.0f
                                            padRight = 1.0f
                                            padTop = 1.0f
                                            canOverflow = true
                                            shape = SHAPE_MENU_BACKGROUND
                                            isMouseAware = false
                                            block {
                                                hSizing = SHRINK
                                                vSizing = SHRINK
                                                hAlign = CENTER
                                                vAlign = MIDDLE
                                                yOffset = -1.0f
                                                padLeft = SMALL_SPACER_SIZE
                                                padRight = SMALL_SPACER_SIZE
                                                text = text("File", TEXT_STYLE_BUTTON)
                                            }
                                        }
                                        onMouseClick { button, x, y ->
                                            dropDown.isVisible = false
                                            dropDown.isMouseAware = false
                                            activator.isMouseAware = true
                                            layer1.isFallThrough = true
                                        }
                                        onMouseOver {
                                            mouseOverDeActivator = true
                                        }
                                        onMouseOut {
                                            mouseOverDeActivator = false
                                        }
                                    }
                                }
                                dropDown.isVisible = false
                                dropDown.isMouseAware = false
                                activator.onMouseOver {
                                    mouseOverActivator = true
                                }
                                activator.onMouseOut {
                                    mouseOverActivator = false
                                }
                                activator.onMouseDown { button, x, y ->
                                    mouseDownOnActivator = true
                                    dropDown.isVisible = true
                                    dropDown.isMouseAware = true
                                    activator.isMouseAware = false
                                    layer1.isFallThrough = false
                                }
                                activator.onMouseRelease { button, x, y ->
                                    if (mouseDownOnActivator && !(mouseOverDeActivator || mouseOverActivator)) {
                                        dropDown.isVisible = false
                                        dropDown.isMouseAware = false
                                        activator.isMouseAware = true
                                        layer1.isFallThrough = true
                                    }
                                    mouseDownOnActivator = false
                                }
                                layer1.onMouseClick { button, x, y ->
                                    dropDown.isVisible = false
                                    dropDown.isMouseAware = false
                                    activator.isMouseAware = true
                                    layer1.isFallThrough = true
                                }
                            }
                        }





                        button(text("Settings"), MENU_TEXT_BUTTON_STYLE) { println("mouse click Settings") }
                        button(text("Help"), MENU_TEXT_BUTTON_STYLE) { println("mouse click Help") }
                        hSpacer(SMALL_ROW_HEIGHT)
                        dragArea = dragArea(text("WorldKit - Edit Mode"))
                        hSpacer(SMALL_SPACER_SIZE)
                        button(glyph(glyphMinimize), WINDOW_DECORATE_BUTTON_STYLE) { minimizeWindow() }
                        button(glyph(maxRestoreGlyph), WINDOW_DECORATE_BUTTON_STYLE) { toggleMaximized() }
                        button(glyph(glyphClose), WINDOW_DECORATE_BUTTON_STYLE) { closeWindow() }
                    }
                    contentPanel = block {
                        vSizing = GROW
                        layout = VERTICAL
                        hAlign = LEFT
                        block {
                            leftPanel = this
                            val labelWidth = 92.0f
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
                                                vSpacer(MEDIUM_SPACER_SIZE)
                                                vToggleRow(waterPlaneOn, LARGE_ROW_HEIGHT, text("Water:"), labelWidth, MEDIUM_SPACER_SIZE)
                                                vToggleRow(perspectiveOn, LARGE_ROW_HEIGHT, text("Perspective:"), labelWidth, MEDIUM_SPACER_SIZE)
                                                vToggleRow(rotateAroundCamera, LARGE_ROW_HEIGHT, text("Rotate camera:"), labelWidth, MEDIUM_SPACER_SIZE)
                                                vSliderRow(heightMapScaleFactor, LARGE_ROW_HEIGHT, text("Height scale:"), labelWidth, MEDIUM_SPACER_SIZE, heightScaleFunction, heightScaleFunctionInverse)
                                                vButtonRow(LARGE_ROW_HEIGHT) {
                                                    button(text("Reset view"), NORMAL_TEXT_BUTTON_STYLE) { resetView.value = true }
                                                    button(text("Reset height"), NORMAL_TEXT_BUTTON_STYLE) { heightMapScaleFactor.value = DEFAULT_HEIGHT_SCALE }
                                                }
//                                                val dynamicLabel = label(dynamicText)
//                                                dynamicLabel.layout = VERTICAL
//                                                dynamicLabel.vAlign = TOP
//                                                dynamicLabel.vSizing = STATIC
//                                                dynamicLabel.height = LARGE_ROW_HEIGHT
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

        ui(uiLayout, 1280, 720) {
//            dynamicTextRef.value = "$width x $height / $pixelWidth x $pixelHeight / $mouseX : $mouseY"
        }
    }
}
