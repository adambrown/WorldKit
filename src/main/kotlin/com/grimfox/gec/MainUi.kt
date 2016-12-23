package com.grimfox.gec

import com.grimfox.gec.opengl.loadTexture2D
import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.util.mRef
import com.grimfox.gec.util.ref
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

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
        val dynamicTextRef = ref("some dynamic text")

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

                val maxRestoreGlyph = MemoryUtil.memUTF8(if (ui.isMaximized) glyphRestore else glyphMaximize, true)
                ui.maximizeHandler = {
                    MemoryUtil.memUTF8(glyphRestore, true, maxRestoreGlyph, 0)
                }
                ui.restoreHandler = {
                    MemoryUtil.memUTF8(glyphMaximize, true, maxRestoreGlyph, 0)
                }

                val dynamicTextBuffer = ByteBuffer.allocateDirect(250)

                val dynamicTextBytes = MemoryUtil.memUTF8(dynamicTextRef.value, true, dynamicTextBuffer, 0)
                dynamicTextBuffer.limit(dynamicTextBytes + 1)
                val dynamicText = DynamicTextUtf8(dynamicTextBuffer, TEXT_STYLE_NORMAL)
                dynamicTextRef.listener { old, new ->
                    val byteCount = MemoryUtil.memUTF8(dynamicTextRef.value, true, dynamicTextBuffer, 0)
                    dynamicTextBuffer.limit(byteCount + 1)
                }

                val icon = createImage("/textures/wk-icon-128.png", NVG_IMAGE_GENERATE_MIPMAPS)
//                val (texId, texWidth, texHeight) = loadTexture2D(GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_LINEAR, "/textures/wk-icon-1024.png", true,
//                        "/textures/wk-icon-512.png",
//                        "/textures/wk-icon-256.png",
//                        "/textures/wk-icon-128.png",
//                        "/textures/wk-icon-64.png",
//                        "/textures/wk-icon-32.png",
//                        "/textures/wk-icon-16.png")
//
//                val icon = createImage(texId, texWidth, texHeight, 0)

                meshViewport.init()

                var topBar = NO_BLOCK
                var contentPanel = NO_BLOCK
                var leftPanel = NO_BLOCK
                var rightPanel = NO_BLOCK

                root {
                    topBar = block {
                        vSizing = STATIC
                        height = MEDIUM_ROW_HEIGHT
                        layout = VERTICAL
                        icon(icon, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT)
                        hSpacer(SMALL_SPACER_SIZE)
                        button(text("File"), MENU_TEXT_BUTTON_STYLE) { println("mouse click File") }
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
                        vAlign = TOP
                        leftPanel = block {
                            val labelWidth = 92
                            hSizing = STATIC
                            width = 268
                            layout = HORIZONTAL
                            hAlign = LEFT
                            vAlign = TOP
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
                                val dynamicLabel = label(dynamicText)
                                dynamicLabel.layout = VERTICAL
                                dynamicLabel.vAlign = TOP
                                dynamicLabel.vSizing = STATIC
                                dynamicLabel.height = LARGE_ROW_HEIGHT
                            }
                            hSpacer(MEDIUM_SPACER_SIZE)
                        }
                        rightPanel = block {
                            hSizing = GROW
                            layout = HORIZONTAL
                            hAlign = LEFT
                            vAlign = TOP
                            block {
                                xOffset = SMALL_SPACER_SIZE
                                yOffset = SMALL_SPACER_SIZE
                                width = -2 * SMALL_SPACER_SIZE
                                height = -2 * SMALL_SPACER_SIZE
                                block {
                                    hSizing = STATIC
                                    width = 1
                                    hAlign = LEFT
                                    layout = HORIZONTAL
                                    shape = ShapeRectangle(FILL_BUTTON_MOUSE_OVER, NO_STROKE)
                                }
                                block {
                                    hSizing = GROW
                                    layout = HORIZONTAL
                                    block {
                                        vSizing = STATIC
                                        height = 1
                                        vAlign = TOP
                                        layout = VERTICAL
                                        shape = ShapeRectangle(FILL_BUTTON_MOUSE_OVER, NO_STROKE)
                                    }
                                    block {
                                        vSizing = GROW
                                        layout = VERTICAL
                                        block {
                                            layout = ABSOLUTE
                                            shape = ShapeMeshViewport3D(meshViewport)
                                        }
                                        block {
                                            val toolbar = this
                                            vSizing = STATIC
                                            height = MEDIUM_ROW_HEIGHT
                                            var tools = NO_BLOCK
                                            var expandToolbarButton = NO_BLOCK
                                            var collapseToolbarButton = NO_BLOCK
                                            hButtonRow {
                                                expandToolbarButton = button(text("+"), LARGE_TEXT_BUTTON_STYLE) { tools.isVisible = true; collapseToolbarButton.isVisible = true; expandToolbarButton.isVisible = false; toolbar.shape = BACKGROUND_RECT }
                                                collapseToolbarButton = button(text("-"), LARGE_TEXT_BUTTON_STYLE) { tools.isVisible = false; collapseToolbarButton.isVisible = false; expandToolbarButton.isVisible = true; toolbar.shape = NO_SHAPE }
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
                                                hSliderRow(heightMapScaleFactor, 144, text("Height scale:"), MEDIUM_SPACER_SIZE, heightScaleFunction, heightScaleFunctionInverse)
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
                                        height = 1
                                        vAlign = TOP
                                        layout = VERTICAL
                                        shape = ShapeRectangle(FILL_BUTTON_MOUSE_OVER, NO_STROKE)
                                    }
                                }
                                block {
                                    hSizing = STATIC
                                    width = 1
                                    hAlign = LEFT
                                    layout = HORIZONTAL
                                    shape = ShapeRectangle(FILL_BUTTON_MOUSE_OVER, NO_STROKE)
                                }
                            }
                        }
                    }
                }
            }
        }

        ui(uiLayout, 1280, 720) {
            meshViewport.doInput(relativeMouseX, relativeMouseY, scrollY, isMouse1Down, isMouse2Down)
            dynamicTextRef.value = "$width x $height / $pixelWidth x $pixelHeight / $mouseX : $mouseY"
        }
    }
}
