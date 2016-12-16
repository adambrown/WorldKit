package com.grimfox.gec

import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.ref
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryUtil

object MainUi {

    @JvmStatic fun main(vararg args: String) {

        val glyphIndex = Array(95) { i -> (i + 32).toChar().toString() }
        val glyphClose = glyphIndex[0]
        val glyphMinimize = glyphIndex[1]
        val glyphRestore = glyphIndex[2]
        val glyphMaximize = glyphIndex[3]

        val maxRestoreGlyph = MemoryUtil.memUTF8(glyphRestore, true)
        fun setRestore() {
            MemoryUtil.memUTF8(glyphRestore, true, maxRestoreGlyph, 0)
        }

        fun setMaximize() {
            MemoryUtil.memUTF8(glyphMaximize, true, maxRestoreGlyph, 0)
        }

        val DEFAULT_HEIGHT_SCALE = 130.0f
        val heightMapScaleFactor: MutableReference<Float> = ref(DEFAULT_HEIGHT_SCALE)
        val waterPlaneOn: MutableReference<Boolean> = ref(true)
        val perspectiveOn: MutableReference<Boolean> = ref(true)
        val rotateAroundCamera: MutableReference<Boolean> = ref(false)
        val resetView: MutableReference<Boolean> = ref(false)

        val uiLayout = layout { ui ->
            ui {
                background.set(45, 45, 48)

                textFont.value = createFont("/fonts/FiraSans.ttf", "FiraSans")
                glyphFont.value = createFont("/fonts/WorldKitUi.ttf", "Glyphs")

                val icon = createImage("/textures/wk-icon-128.png", NVG_IMAGE_GENERATE_MIPMAPS)

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
                            val labelWidth = 130
                            hSizing = STATIC
                            width = 534
                            layout = HORIZONTAL
                            hAlign = LEFT
                            vAlign = TOP
                            hSpacer(MEDIUM_SPACER_SIZE)
                            block {
                                hSizing = GROW
                                layout = HORIZONTAL
                                vSpacer(MEDIUM_SPACER_SIZE)
                                toggleRow(waterPlaneOn, LARGE_ROW_HEIGHT, text("Water:"), labelWidth, MEDIUM_SPACER_SIZE)
                                toggleRow(perspectiveOn, LARGE_ROW_HEIGHT, text("Perspective:"), labelWidth, MEDIUM_SPACER_SIZE)
                                toggleRow(rotateAroundCamera, LARGE_ROW_HEIGHT, text("Rotate camera:"), labelWidth, MEDIUM_SPACER_SIZE)
                                block {
                                    vSizing = STATIC
                                    height = LARGE_ROW_HEIGHT
                                    layout = VERTICAL
                                    block {
                                        hSizing = STATIC
                                        width = labelWidth
                                        layout = HORIZONTAL
                                        block {
                                            hAlign = RIGHT
                                            vAlign = MIDDLE
                                            hSizing = SHRINK
                                            vSizing = SHRINK
                                            text = text("Height scale:")
                                            isMouseAware = false
                                        }
                                    }
                                    hSpacer(MEDIUM_SPACER_SIZE)
                                    block {
                                        hSizing = GROW
                                        layout = HORIZONTAL

                                        //@TODO need to add slider here

                                    }
                                }
                                buttonRow(LARGE_ROW_HEIGHT) {
                                    button(text("Reset view"), NORMAL_TEXT_BUTTON_STYLE) { resetView.value = true }
                                    button(text("Reset height"), NORMAL_TEXT_BUTTON_STYLE) { heightMapScaleFactor.value = DEFAULT_HEIGHT_SCALE }
                                }
                            }
                            hSpacer(MEDIUM_SPACER_SIZE)
                        }
                        rightPanel = block {
                            hSizing = GROW
                            layout = HORIZONTAL
                            hAlign = LEFT
                            vAlign = TOP
                        }
                    }
                }
            }
        }

        var lastMaximized = true

        ui(uiLayout, 1280, 720, resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor) {
            if (isMaximized && !lastMaximized) {
                setRestore()
            } else if (!isMaximized && lastMaximized) {
                setMaximize()
            }
            lastMaximized = isMaximized
        }
    }
}
