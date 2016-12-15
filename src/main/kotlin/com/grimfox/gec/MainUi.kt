package com.grimfox.gec

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.HorizontalAlignment.*
import com.grimfox.gec.ui.Layout.*
import com.grimfox.gec.ui.Sizing.*
import com.grimfox.gec.ui.VerticalAlignment.*
import com.grimfox.gec.util.getPathForResource
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nuklear.*
import org.lwjgl.nuklear.Nuklear.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.util.*

object MainUi {

    @JvmStatic fun main(vararg args: String) {
        twr(stackPush()) { stack ->
            val nkMainFont = NkUserFont.create()
            val nkGlyphFont = NkUserFont.create()

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

            val WHITE = nvgRGBA(200.toByte(), 200.toByte(), 200.toByte(), 255.toByte(), NVGColor.create())
            val BLUE = nvgRGBA(40.toByte(), 40.toByte(), 200.toByte(), 255.toByte(), NVGColor.create())
            val GREEN = nvgRGBA(40.toByte(), 200.toByte(), 40.toByte(), 255.toByte(), NVGColor.create())
            val RED = nvgRGBA(200.toByte(), 40.toByte(), 40.toByte(), 255.toByte(), NVGColor.create())
            val MAGENTA = nvgRGBA(180.toByte(), 40.toByte(), 180.toByte(), 255.toByte(), NVGColor.create())
            val ORANGE = nvgRGBA(180.toByte(), 160.toByte(), 40.toByte(), 255.toByte(), NVGColor.create())

            val COLOR_NORMAL_TEXT = nvgRGBA(153.toByte(), 153.toByte(), 153.toByte(), 255.toByte(), NVGColor.create())

            val COLOR_BUTTON_TEXT = nvgRGBA(243.toByte(), 243.toByte(), 243.toByte(), 255.toByte(), NVGColor.create())
            val COLOR_BUTTON_MOUSE_OVER = nvgRGBA(64.toByte(), 62.toByte(), 64.toByte(), 255.toByte(), NVGColor.create())
            val COLOR_BUTTON_MOUSE_DOWN = nvgRGBA(0.toByte(), 122.toByte(), 204.toByte(), 255.toByte(), NVGColor.create())


            val FILL_BUTTON_MOUSE_OVER = FillColor(COLOR_BUTTON_MOUSE_OVER)
            val FILL_BUTTON_MOUSE_DOWN = FillColor(COLOR_BUTTON_MOUSE_DOWN)

            val FILL_BLUE = FillColor(BLUE)
            val FILL_MAGENTA = FillColor(MAGENTA)
            val STROKE_GREEN_THICK = StrokeColor(GREEN, 2.0f)
            val STROKE_ORANGE_THICK = StrokeColor(ORANGE, 2.0f)
            val STROKE_RED_THIN = NO_STROKE
//        val STROKE_RED_THIN = StrokeColor(RED, 1.0f)
            val BUTTON_NORMAL = ShapeRectangle(NO_FILL, NO_STROKE)
//        val ROUNDED_RECT = ShapeRoundedRectangle(FILL_BLUE, STROKE_GREEN_THICK, 6.0f)
            val RECT = ShapeRectangle(NO_FILL, STROKE_RED_THIN)

            val BUTTON_MOUSE_OVER = ShapeRectangle(FILL_BUTTON_MOUSE_OVER, NO_STROKE)
            val BUTTON_MOUSE_DOWN = ShapeRectangle(FILL_BUTTON_MOUSE_DOWN, NO_STROKE)

            val heightMapScaleFactor = stack.mallocFloat(1)
            heightMapScaleFactor.put(0, 0.5f)
            val waterPlaneOn = stack.mallocInt(1)
            waterPlaneOn.put(0, 1)
            val perspectiveOn = stack.mallocInt(1)
            perspectiveOn.put(0, 1)
            val rotateAroundCamera = stack.mallocInt(1)
            rotateAroundCamera.put(0, 0)
            val resetView = stack.mallocInt(1)
            resetView.put(0, 0)
            val windowBounds = nk_rect(0.0f, 0.0f, 100.0f, 100.0f, NkRect.mallocStack(stack))

            val mainStyle = layout { ui, nk, nvg ->
                ui {
                    val iconNvg = nvgCreateImage(nvg, getPathForResource("/textures/wk-icon-128.png"), NVG_IMAGE_GENERATE_MIPMAPS)

                    val ICON_SHAPE = ShapeRectangle(FillImageDynamic(iconNvg), NO_STROKE)

                    createNkFont("/fonts/FiraSans.ttf", 22.0f, 32, 95, 512, 512, nkMainFont)
                    createNkFont("/fonts/WorldKitUi.ttf", 22.0f, 32, 4, 512, 512, nkGlyphFont)

                    val textFont = getNvgFont(createNvgFont("/fonts/FiraSans.ttf", "FiraSans", nvg))
                    val glyphFont = getNvgFont(createNvgFont("/fonts/WorldKitUi.ttf", "Glyphs", nvg))

                    fun text(value: String, color: NVGColor = COLOR_NORMAL_TEXT): Text {
                        return StaticTextUtf8(value, 22.0f, textFont, color)
                    }

                    fun glyph(value: String, color: NVGColor = COLOR_NORMAL_TEXT): Text {
                        return StaticTextUtf8(value, 22.0f, glyphFont, color)
                    }

                    fun text(value: ByteBuffer, color: NVGColor = COLOR_NORMAL_TEXT): Text {
                        return DynamicTextUtf8(value, 22.0f, textFont, color)
                    }

                    fun glyph(value: ByteBuffer, color: NVGColor = COLOR_NORMAL_TEXT): Text {
                        return DynamicTextUtf8(value, 22.0f, glyphFont, color)
                    }

                    fun Block.hSpacer(space: Int) {
                        block {
                            hSizing = STATIC
                            width = space
                            layout = HORIZONTAL
                            isMouseAware = false
                        }
                    }

                    fun Block.vSpacer(space: Int) {
                        block {
                            vSizing = STATIC
                            height = space
                            layout = VERTICAL
                            isMouseAware = false
                        }
                    }

                    background.set(45, 45, 48)

                    root {
                        block {
                            vSizing = STATIC
                            height = 40
                            layout = VERTICAL
                            block {
                                hSizing = STATIC
                                vSizing = STATIC
                                width = 40
                                height = 40
                                layout = HORIZONTAL
                                block {
                                    hAlign = CENTER
                                    vAlign = MIDDLE
                                    hSizing = STATIC
                                    vSizing = STATIC
                                    height = 32
                                    width = 32
                                    shape = ICON_SHAPE
                                    isMouseAware = false
                                }
                                isMouseAware = false
                            }
                            hSpacer(10)
                            block {
                                hSizing = SHRINK
                                vSizing = STATIC
                                height = 32
                                vAlign = BOTTOM
                                layout = HORIZONTAL
                                block {
                                    hAlign = CENTER
                                    vAlign = MIDDLE
                                    hSizing = SHRINK
                                    vSizing = SHRINK
                                    padLeft = 10
                                    padRight = 10
                                    text = text("File", COLOR_BUTTON_TEXT)
                                    shape = RECT
                                    isMouseAware = false
                                }
                                shape = BUTTON_NORMAL
                                var mouseDownOver = false
                                var mouseOver = false
                                onMouseOver {
                                    mouseOver = true
                                    if (!mouseDownOver) {
                                        shape = BUTTON_MOUSE_OVER
                                    }
                                }
                                onMouseOut {
                                    mouseOver = false
                                    if (!mouseDownOver) {
                                        shape = BUTTON_NORMAL
                                    }
                                }
                                onMouseDown { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                        mouseDownOver = true
                                        shape = BUTTON_MOUSE_DOWN
                                    }
                                }
                                onMouseRelease { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                                        mouseDownOver = false
                                        shape = if (mouseOver) BUTTON_MOUSE_OVER else BUTTON_NORMAL
                                    }
                                }
                                onMouseClick { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                        println("mouse click File")
                                    }
                                }
                            }
                            block {
                                hSizing = SHRINK
                                vSizing = STATIC
                                height = 32
                                vAlign = BOTTOM
                                layout = HORIZONTAL
                                block {
                                    hAlign = CENTER
                                    vAlign = MIDDLE
                                    hSizing = SHRINK
                                    vSizing = SHRINK
                                    padLeft = 10
                                    padRight = 10
                                    text = text("Settings", COLOR_BUTTON_TEXT)
                                    shape = RECT
                                    isMouseAware = false
                                }
                                shape = BUTTON_NORMAL
                                var mouseDownOver = false
                                var mouseOver = false
                                onMouseOver {
                                    mouseOver = true
                                    if (!mouseDownOver) {
                                        shape = BUTTON_MOUSE_OVER
                                    }
                                }
                                onMouseOut {
                                    mouseOver = false
                                    if (!mouseDownOver) {
                                        shape = BUTTON_NORMAL
                                    }
                                }
                                onMouseDown { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                        mouseDownOver = true
                                        shape = BUTTON_MOUSE_DOWN
                                    }
                                }
                                onMouseRelease { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                                        mouseDownOver = false
                                        shape = if (mouseOver) BUTTON_MOUSE_OVER else BUTTON_NORMAL
                                    }
                                }
                                onMouseClick { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                        println("mouse click Settings")
                                    }
                                }
                            }
                            block {
                                hSizing = SHRINK
                                vSizing = STATIC
                                height = 32
                                vAlign = BOTTOM
                                layout = HORIZONTAL
                                block {
                                    hAlign = CENTER
                                    vAlign = MIDDLE
                                    hSizing = SHRINK
                                    vSizing = SHRINK
                                    padLeft = 10
                                    padRight = 10
                                    text = text("About", COLOR_BUTTON_TEXT)
                                    shape = RECT
                                    isMouseAware = false
                                }
                                shape = BUTTON_NORMAL
                                var mouseDownOver = false
                                var mouseOver = false
                                onMouseOver {
                                    mouseOver = true
                                    if (!mouseDownOver) {
                                        shape = BUTTON_MOUSE_OVER
                                    }
                                }
                                onMouseOut {
                                    mouseOver = false
                                    if (!mouseDownOver) {
                                        shape = BUTTON_NORMAL
                                    }
                                }
                                onMouseDown { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                        mouseDownOver = true
                                        shape = BUTTON_MOUSE_DOWN
                                    }
                                }
                                onMouseRelease { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                                        mouseDownOver = false
                                        shape = if (mouseOver) BUTTON_MOUSE_OVER else BUTTON_NORMAL
                                    }
                                }
                                onMouseClick { button, x, y ->
                                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                        println("mouse click About")
                                    }
                                }
                            }
                            hSpacer(32)
                            dragArea = block {
                                hSizing = GROW
                                layout = HORIZONTAL
                                block {
                                    hAlign = CENTER
                                    vAlign = TOP
                                    hSizing = SHRINK
                                    vSizing = STATIC
                                    height = 32
                                    layout = HORIZONTAL
                                    block {
                                        hAlign = LEFT
                                        vAlign = MIDDLE
                                        hSizing = SHRINK
                                        vSizing = SHRINK
                                        text = text("WorldKit - Edit Mode")
                                        shape = RECT
                                        isMouseAware = false
                                    }
                                    isMouseAware = false
                                }
                                isMouseAware = false
                            }
                            hSpacer(10)
                            block {
                                hSizing = STATIC
                                width = 144
                                layout = HORIZONTAL
                                block {
                                    hSizing = STATIC
                                    vSizing = STATIC
                                    width = 48
                                    height = 32
                                    hAlign = LEFT
                                    vAlign = TOP
                                    layout = HORIZONTAL
                                    shape = BUTTON_NORMAL
                                    block {
                                        hAlign = CENTER
                                        vAlign = MIDDLE
                                        hSizing = SHRINK
                                        vSizing = SHRINK
                                        text = glyph(glyphMinimize, COLOR_BUTTON_TEXT)
                                        isMouseAware = false
                                    }
                                    var mouseDownOver = false
                                    var mouseOver = false
                                    onMouseOver {
                                        mouseOver = true
                                        if (!mouseDownOver) {
                                            shape = BUTTON_MOUSE_OVER
                                        }
                                    }
                                    onMouseOut {
                                        mouseOver = false
                                        if (!mouseDownOver) {
                                            shape = BUTTON_NORMAL
                                        }
                                    }
                                    onMouseDown { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                            mouseDownOver = true
                                            shape = BUTTON_MOUSE_DOWN
                                        }
                                    }
                                    onMouseRelease { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                                            mouseDownOver = false
                                            shape = if (mouseOver) BUTTON_MOUSE_OVER else BUTTON_NORMAL
                                        }
                                    }
                                    onMouseClick { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                            minimizeWindow()
                                        }
                                    }
                                }
                                block {
                                    hSizing = STATIC
                                    vSizing = STATIC
                                    width = 48
                                    height = 32
                                    hAlign = LEFT
                                    vAlign = TOP
                                    layout = HORIZONTAL
                                    shape = BUTTON_NORMAL
                                    block {
                                        hAlign = CENTER
                                        vAlign = MIDDLE
                                        hSizing = SHRINK
                                        vSizing = SHRINK
                                        text = glyph(maxRestoreGlyph, COLOR_BUTTON_TEXT)
                                        isMouseAware = false
                                    }
                                    var mouseDownOver = false
                                    var mouseOver = false
                                    onMouseOver {
                                        mouseOver = true
                                        if (!mouseDownOver) {
                                            shape = BUTTON_MOUSE_OVER
                                        }
                                    }
                                    onMouseOut {
                                        mouseOver = false
                                        if (!mouseDownOver) {
                                            shape = BUTTON_NORMAL
                                        }
                                    }
                                    onMouseDown { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                            mouseDownOver = true
                                            shape = BUTTON_MOUSE_DOWN
                                        }
                                    }
                                    onMouseRelease { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                                            mouseDownOver = false
                                            shape = if (mouseOver) BUTTON_MOUSE_OVER else BUTTON_NORMAL
                                        }
                                    }
                                    onMouseClick { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                            toggleMaximized()
                                        }
                                    }
                                }
                                block {
                                    hSizing = STATIC
                                    vSizing = STATIC
                                    width = 48
                                    height = 32
                                    hAlign = LEFT
                                    vAlign = TOP
                                    layout = HORIZONTAL
                                    shape = BUTTON_NORMAL
                                    block {
                                        hAlign = CENTER
                                        vAlign = MIDDLE
                                        hSizing = SHRINK
                                        vSizing = SHRINK
                                        text = glyph(glyphClose, COLOR_BUTTON_TEXT)
                                        isMouseAware = false
                                    }
                                    var mouseDownOver = false
                                    var mouseOver = false
                                    onMouseOver {
                                        mouseOver = true
                                        if (!mouseDownOver) {
                                            shape = BUTTON_MOUSE_OVER
                                        }
                                    }
                                    onMouseOut {
                                        mouseOver = false
                                        if (!mouseDownOver) {
                                            shape = BUTTON_NORMAL
                                        }
                                    }
                                    onMouseDown { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                            mouseDownOver = true
                                            shape = BUTTON_MOUSE_DOWN
                                        }
                                    }
                                    onMouseRelease { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                                            mouseDownOver = false
                                            shape = if (mouseOver) BUTTON_MOUSE_OVER else BUTTON_NORMAL
                                        }
                                    }
                                    onMouseClick { button, x, y ->
                                        if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                            closeWindow()
                                        }
                                    }
                                }
                            }
                        }
                        block {
                            vSizing = GROW
                            layout = VERTICAL
                            hAlign = LEFT
                            vAlign = TOP
                            block {
                                val padding = 24
                                val rowHeight = 48
                                val labelWidth = 130
                                hSizing = STATIC
                                width = 534
                                layout = HORIZONTAL
                                hAlign = LEFT
                                vAlign = TOP
                                hSpacer(padding)
                                block {
                                    hSizing = GROW
                                    layout = HORIZONTAL
                                    vSpacer(padding)
                                    block {
                                        vSizing = STATIC
                                        height = rowHeight
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
                                                text = text("Water:")
                                                isMouseAware = false
                                            }
                                        }
                                        hSpacer(padding)
                                        block {
                                            hSizing = GROW
                                            layout = HORIZONTAL
                                        }
                                    }
                                    block {
                                        vSizing = STATIC
                                        height = rowHeight
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
                                                text = text("Perspective:")
                                                isMouseAware = false
                                            }
                                        }
                                        hSpacer(padding)
                                        block {
                                            hSizing = GROW
                                            layout = HORIZONTAL
                                        }
                                    }
                                    block {
                                        vSizing = STATIC
                                        height = rowHeight
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
                                                text = text("Rotate camera:")
                                                isMouseAware = false
                                            }
                                        }
                                        hSpacer(padding)
                                        block {
                                            hSizing = GROW
                                            layout = HORIZONTAL
                                        }
                                    }
                                    block {
                                        vSizing = STATIC
                                        height = rowHeight
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
                                        hSpacer(padding)
                                        block {
                                            hSizing = GROW
                                            layout = HORIZONTAL
                                        }
                                    }
                                    block {
                                        vSizing = STATIC
                                        height = rowHeight
                                        layout = VERTICAL
                                        block {
                                            hSizing = SHRINK
                                            vSizing = SHRINK
                                            hAlign = CENTER
                                            vAlign = MIDDLE
                                            block {
                                                hSizing = SHRINK
                                                vSizing = STATIC
                                                height = 32
                                                vAlign = BOTTOM
                                                layout = HORIZONTAL
                                                block {
                                                    hAlign = CENTER
                                                    vAlign = MIDDLE
                                                    hSizing = SHRINK
                                                    vSizing = SHRINK
                                                    padLeft = 10
                                                    padRight = 10
                                                    text = text("Reset view", COLOR_BUTTON_TEXT)
                                                    shape = RECT
                                                    isMouseAware = false
                                                }
                                                shape = BUTTON_NORMAL
                                                var mouseDownOver = false
                                                var mouseOver = false
                                                onMouseOver {
                                                    mouseOver = true
                                                    if (!mouseDownOver) {
                                                        shape = BUTTON_MOUSE_OVER
                                                    }
                                                }
                                                onMouseOut {
                                                    mouseOver = false
                                                    if (!mouseDownOver) {
                                                        shape = BUTTON_NORMAL
                                                    }
                                                }
                                                onMouseDown { button, x, y ->
                                                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                                        mouseDownOver = true
                                                        shape = BUTTON_MOUSE_DOWN
                                                    }
                                                }
                                                onMouseRelease { button, x, y ->
                                                    if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                                                        mouseDownOver = false
                                                        shape = if (mouseOver) BUTTON_MOUSE_OVER else BUTTON_NORMAL
                                                    }
                                                }
                                                onMouseClick { button, x, y ->
                                                    if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                                        resetView.put(0, 1)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                hSpacer(padding)
                            }
                            block {
                                hSizing = GROW
                                layout = HORIZONTAL
                                hAlign = LEFT
                                vAlign = TOP
                            }
                        }
                        block {
                            vSizing = STATIC
                            height = 32
                            layout = VERTICAL
                        }
                    }
                }
                twr(stackPush()) { stack ->
                    nk_style_set_font(nk, nkMainFont)

                    val window = NkStyleWindow.create()
                    window.background().set(background)
                    window.background(background)
                    window.border(0.0f)
                    window.padding().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    window.scrollbar_size().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    nk.style().window().set(NkStyleWindow.create())

                    val button = nk.style().button()
                    button.border(0.0f)
                    button.border_color().set(background)
                    button.rounding(0.0f)
                    button.padding().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    button.text_alignment(NK_TEXT_ALIGN_CENTERED or NK_TEXT_ALIGN_MIDDLE)
                    val buttonGlyphColor = NkColor.create()
                    buttonGlyphColor.set(243, 243, 243)
                    button.text_background().set(buttonGlyphColor)
                    button.text_active().set(buttonGlyphColor)
                    button.text_normal().set(buttonGlyphColor)
                    button.text_hover().set(buttonGlyphColor)
                    button.active(nk_style_item_color(nk_rgb(0, 122, 204, NkColor.create()), NkStyleItem.create()))
                    button.hover(nk_style_item_color(nk_rgb(100, 101, 103, NkColor.create()), NkStyleItem.create()))
                    button.normal(nk_style_item_color(background, NkStyleItem.create()))
                    nk.style().button().set(button)

                    val slider = nk.style().slider()
                    slider.border(0.0f)
                    slider.border_color().set(background)
                    slider.rounding(0.0f)
                    slider.padding().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    slider.spacing().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    slider.bar_height(1.0f)
                    slider.show_buttons(0)
                    slider.cursor_size().set(nk_vec2(22.0f, 22.0f, NkVec2.mallocStack(stack)))
                    slider.active(nk_style_item_color(background, NkStyleItem.create()))
                    slider.hover(nk_style_item_color(background, NkStyleItem.create()))
                    slider.normal(nk_style_item_color(background, NkStyleItem.create()))
                    slider.bar_active(nk_rgb(62, 62, 64, NkColor.create()))
                    slider.bar_hover(nk_rgb(62, 62, 64, NkColor.create()))
                    slider.bar_normal(nk_rgb(62, 62, 64, NkColor.create()))
                    slider.bar_filled(nk_rgb(100, 101, 103, NkColor.create()))
                    slider.cursor_active(nk_style_item_color(nk_rgb(100, 101, 103, NkColor.create()), NkStyleItem.create()))
                    slider.cursor_hover(nk_style_item_color(nk_rgb(100, 101, 103, NkColor.create()), NkStyleItem.create()))
                    slider.cursor_normal(nk_style_item_color(nk_rgb(100, 101, 103, NkColor.create()), NkStyleItem.create()))
                    nk.style().slider().set(slider)

                    val text = nk.style().text()
                    text.color(nk_rgb(153, 153, 153, NkColor.create()))
                    text.padding().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))

                    val checkBox = nk.style().checkbox()
                    checkBox.border(0.0f)
                    checkBox.border_color().set(background)
                    checkBox.padding().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    checkBox.spacing(0.0f)
                    checkBox.active(nk_style_item_color(nk_rgb(0, 122, 204, NkColor.create()), NkStyleItem.create()))
                    checkBox.normal(nk_style_item_color(nk_rgb(62, 62, 64, NkColor.create()), NkStyleItem.create()))
                    checkBox.hover(nk_style_item_color(nk_rgb(100, 101, 103, NkColor.create()), NkStyleItem.create()))
                    checkBox.cursor_hover(nk_style_item_color(nk_rgb(100, 101, 103, NkColor.create()), NkStyleItem.create()))
                    checkBox.cursor_normal(nk_style_item_color(nk_rgb(0, 122, 204, NkColor.create()), NkStyleItem.create()))
                    nk.style().checkbox().set(checkBox)
                }
            }

            var lastMaximized = true

            ui(mainStyle, 1280, 720, resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor) { nk, nvg ->
                twr(stackPush()) { stack ->

                    if (isMaximized && !lastMaximized) {
                        setRestore()
                    } else if (!isMaximized && lastMaximized) {
                        setMaximize()
                    }
                    lastMaximized = isMaximized


                    if (nk_begin(nk, "0", windowBounds, NK_WINDOW_BACKGROUND)) {
                        nk_window_set_bounds(nk, nk_rect(0.0f, 0.0f, width.toFloat(), height.toFloat(), windowBounds))

//                        nk_style_push_font(nk, glyphFont)
//                        nk.staticRow(32, width) {
//                            col {
//                                nk_style_push_font(nk, mainFont)
//                                nk_text(nk, "", NK_TEXT_ALIGN_CENTERED or NK_TEXT_ALIGN_MIDDLE)
//                                nk_style_pop_font(nk)
//                            }
//                            col(48) {
//                                if (nk_button_label(nk, glyphMinimize)) {
//                                    minimizeWindow()
//                                }
//                            }
//                            col(48) {
//                                val glyph = if (isMaximized) glyphRestore else glyphMaximize
//                                if (nk_button_label(nk, glyph)) {
//                                    toggleMaximized()
//                                }
//                            }
//                            col(48) {
//                                if (nk_button_label(nk, glyphClose)) {
//                                    closeWindow()
//                                }
//                            }
//                        }
//                        nk_style_pop_font(nk)

                        nk.staticRow(48, width) {
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(48, width) {
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(280) {
                                nk_checkbox_label(nk, "", waterPlaneOn)
                            }
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(48, width) {
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(280) {
                                nk_checkbox_label(nk, "", perspectiveOn)
                            }
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(48, width) {
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(280) {
                                nk_checkbox_label(nk, "", rotateAroundCamera)
                            }
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(48, width) {
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(320) {
                                nk_slider_float(nk, 0.0f, heightMapScaleFactor, 1.0f, 0.0001f)
                            }
                            col(24) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(4, width) {
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(40, width) {
                            col(196) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(196) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(4, width) {
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
//                        nk.staticRow(4, width) {
//                            col {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                        }
//                        nk.staticRow(40, width) {
//                            col(96) {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                            col(340) {
//                                nk_edit_string(nk, NK_EDIT_FIELD, editData, editDataLength, 100, ::nnk_filter_ascii)
//                            }
//                            col(96) {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                            col {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                        }
//                        nk.staticRow(4, width) {
//                            col {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                        }
//                        nk.staticRow(4, width) {
//                            col {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                        }
//                        nk.staticRow(40, width) {
//                            col(96) {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                            col(340) {
//                                val bytes = ByteArray(editDataLength[0])
//                                for (i in 0..bytes.size - 1) {
//                                    bytes[i] = editData[i]
//                                }
//                                nk_label(nk, String(bytes, Charsets.US_ASCII), NK_TEXT_ALIGN_CENTERED or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                            col(96) {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                            col {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                        }
//                        nk.staticRow(4, width) {
//                            col {
//                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
//                            }
//                        }

//                        nk_layout_row_static(context, 30f, 400, 1)
//                        nk_text(context, "mouse x: $mouseX, y: $mouseY", NK_TEXT_ALIGN_LEFT)
//                        nk_text(context, "window w: $width, h: $height", NK_TEXT_ALIGN_LEFT)
//
//
//                        nk_layout_row_dynamic(context, 30.0f, 1)
//                        if (nk_button_label(context, "button with really long name")) {
//                            println("button clicked")
//                        }
//                        nk_layout_row_dynamic(context, 30f, 2)
//                        if (nk_option_label(context, "easy", op == EASY)) {
//                            op = EASY
//                        }
//                        if (nk_option_label(context, "hard", op == HARD)) {
//                            op = HARD
//                        }
//
//                        nk_layout_row_dynamic(context, 25f, 1)
//                        nk_property_int(context, "Compression:", 0, compression, 100, 10, 1.0f)
//
//                        nk_layout_row_dynamic(context, 20f, 1)
//                        nk_label(context, "background:", NK_TEXT_LEFT)
//                        nk_layout_row_dynamic(context, 25f, 1)
//                        if (nk_combo_begin_color(context, style.background, NkVec2.mallocStack(stack).set(nk_widget_width(context), 400f))) {
//                            nk_layout_row_dynamic(context, 120f, 1)
//                            nk_color_picker(context, style.background, NK_RGBA)
//                            nk_layout_row_dynamic(context, 25f, 1)
//                            style.background.rInt = nk_propertyi(context, "#R:", 0, style.background.rInt, 255, 1, 1.0f)
//                            style.background.gInt = nk_propertyi(context, "#G:", 0, style.background.gInt, 255, 1, 1.0f)
//                            style.background.bInt = nk_propertyi(context, "#B:", 0, style.background.bInt, 255, 1, 1.0f)
//                            style.background.aInt = nk_propertyi(context, "#A:", 0, style.background.aInt, 255, 1, 1.0f)
//                            nk_combo_end(context)
//                        }
                    }
                    nk_end(nk)
                }
            }
        }
    }
}

data class StaticRow(var remainder: Int, var greedyCount: Int = 0, val columns: MutableList<Pair<Int, () -> Unit>> = ArrayList()) {

    fun col(width: Int, layout: () -> Unit = {}) {
        columns.add(Pair(width, layout))
        remainder -= width
    }

    fun col(layout: () -> Unit = {}) {
        columns.add(Pair(-1, layout))
        greedyCount++
    }
}

fun NkContext.staticRow(height: Int, width: Int, columns: StaticRow.() -> Unit) {
    val row = StaticRow(width)
    row.columns()
    nk_layout_row_begin(this, NK_STATIC, height.toFloat(), row.columns.size)
    row.columns.forEach { pair ->
        if (pair.first < 0) {
            val localWidth = row.remainder / row.greedyCount
            row.remainder -= localWidth
            row.greedyCount--
            nk_layout_row_push(this, localWidth.toFloat())
            pair.second()
        } else {
            nk_layout_row_push(this, pair.first.toFloat())
            pair.second()
        }
    }
    nk_layout_row_end(this)
}