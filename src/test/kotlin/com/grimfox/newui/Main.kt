package com.grimfox.newui

import com.grimfox.gec.*
import com.grimfox.gec.ui.layout
import com.grimfox.gec.ui.nvgproxy.set
import com.grimfox.gec.ui.ui
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.loadTexture2D
import org.lwjgl.opengl.GL11
import org.lwjgl.system.MemoryUtil

object Main {

    @JvmStatic
    fun main(vararg args: String) {
        val uiLayout = layout { ui ->
            ui {
                background.set(45, 45, 48)

                textFont.value = createFont("/fonts/FiraSans.ttf", "FiraSans")
                glyphFont.value = createFont("/fonts/WorldKitUi.ttf", "Glyphs")

                val maxRestoreGlyph = MemoryUtil.memUTF8(if (isMaximized) GLYPH_RESTORE else GLYPH_MAXIMIZE, false)
                maximizeHandler = {
                    MemoryUtil.memUTF8(GLYPH_RESTORE, false, maxRestoreGlyph, 0)
                }
                restoreHandler = {
                    MemoryUtil.memUTF8(GLYPH_MAXIMIZE, false, maxRestoreGlyph, 0)
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
                }
                val titleText = DynamicTextReference("WorldKit - No Project", 67, TEXT_STYLE_NORMAL)
                mainLayer {
                    block {
                        vSizing = Sizing.STATIC
                        height = MEDIUM_ROW_HEIGHT
                        layout = Layout.VERTICAL
                        icon(icon, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT)
                        hSpacer(SMALL_SPACER_SIZE)
                        dragArea = dragArea(titleText.text)
                        hSpacer(SMALL_SPACER_SIZE)
                        button(glyph(GLYPH_MINIMIZE), WINDOW_DECORATE_BUTTON_STYLE) { minimizeWindow() }
                        button(glyph(maxRestoreGlyph), WINDOW_DECORATE_BUTTON_STYLE) { toggleMaximized() }
                        button(glyph(GLYPH_CLOSE), WINDOW_DECORATE_BUTTON_STYLE) {
                            closeWindowSafely()
                        }
                    }
                    resizeAreaSouthEast = resizeArea(ShapeTriangle.Direction.SOUTH_EAST)
                    resizeAreaSouthWest = resizeArea(ShapeTriangle.Direction.SOUTH_WEST)
                }
            }
        }
        ui(uiLayout) {

        }
    }
}