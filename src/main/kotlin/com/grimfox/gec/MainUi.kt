package com.grimfox.gec

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.learning.drawButton
import com.grimfox.gec.learning.rgba
import com.grimfox.gec.opengl.loadTexture2D
import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.HorizontalAlignment.*
import com.grimfox.gec.ui.Layout.*
import com.grimfox.gec.ui.Sizing.*
import com.grimfox.gec.ui.VerticalAlignment.*
import com.grimfox.gec.util.loadResource
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG
import org.lwjgl.nanovg.NanoVG.nvgRGBA
import org.lwjgl.nuklear.*
import org.lwjgl.nuklear.Nuklear.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryStack.stackPush
import java.nio.charset.Charset
import java.util.*

object MainUi {

    class UiData(var font: Int = -1) {
        lateinit var root: Block
    }

    @JvmStatic fun main(vararg args: String) {
        val uiData = UiData()
        val mainFont = NkUserFont.create()
        val glyphFont = NkUserFont.create()
        val glyphIndex = Array(95) { i -> (i + 32).toChar().toString() }
        val glyphClose = glyphIndex[0]
        val glyphMinimize = glyphIndex[1]
        val glyphRestore = glyphIndex[2]
        val glyphMaximize = glyphIndex[3]

        val WHITE = nvgRGBA(200.toByte(), 200.toByte(), 200.toByte(), 255.toByte(), NVGColor.create())
        val BLUE = nvgRGBA(40.toByte(), 40.toByte(), 200.toByte(), 255.toByte(), NVGColor.create())
        val GREEN = nvgRGBA(40.toByte(), 200.toByte(), 40.toByte(), 255.toByte(), NVGColor.create())
        val RED = nvgRGBA(200.toByte(), 40.toByte(), 40.toByte(), 255.toByte(), NVGColor.create())
        val MAGENTA = nvgRGBA(180.toByte(), 40.toByte(), 180.toByte(), 255.toByte(), NVGColor.create())
        val ORANGE = nvgRGBA(180.toByte(), 160.toByte(), 40.toByte(), 255.toByte(), NVGColor.create())

        val FILL_BLUE = FillColor(BLUE)
        val FILL_MAGENTA = FillColor(MAGENTA)
        val STROKE_GREEN_THICK = StrokeColor(GREEN, 2.0f)
        val STROKE_ORANGE_THICK = StrokeColor(ORANGE, 2.0f)
        val STROKE_RED_THIN = StrokeColor(RED, 1.0f)
        val ROUNDED_RECT = ShapeRoundedRectangle(FILL_BLUE, STROKE_GREEN_THICK, 6.0f)
        val RECT = ShapeRectangle(NO_FILL, STROKE_RED_THIN)

        val MOUSE_OVER = ShapeRoundedRectangle(FILL_MAGENTA, STROKE_ORANGE_THICK, 6.0f)

        val mainStyle = style { nk, nvg ->
            val wkIcon = loadTexture2D(GL_UNSIGNED_BYTE, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR, "/textures/wk-icon-1024.png", true,
                    "/textures/wk-icon-512.png",
                    "/textures/wk-icon-256.png",
                    "/textures/wk-icon-128.png",
                    "/textures/wk-icon-64.png",
                    "/textures/wk-icon-32.png",
                    "/textures/wk-icon-16.png")

            createNkFont("/fonts/FiraSans.ttf", 22.0f, 32, 95, 512, 512, mainFont)
            createNkFont("/fonts/WorldKitUi.ttf", 22.0f, 32, 4, 512, 512, glyphFont)

            fun text(value: String): Text {
                return StaticTextUtf8(value, 24.0f, uiData.font, WHITE)
            }

            background.set(45, 45, 48)

            dragAreaLeftMargin = 0
            dragAreaRightMargin = 146
            dragAreaTopMargin = 0
            dragAreaHeight = 32

            uiData.font = getNvgFont(createNvgFont("/fonts/FiraSans.ttf", "FiraSans", nvg))
            uiData.root = uiRoot(0, 0, 0, 0) {
                block {
                    vSizing = STATIC
                    height = 40
                    layout = VERTICAL
                }
                block {
                    vSizing = STATIC
                    height = 40
                    layout = VERTICAL
                    block {
                        hSizing = SHRINK
                        layout = HORIZONTAL
                        block {
                            hAlign = CENTER
                            vAlign = MIDDLE
                            hSizing = SHRINK
                            vSizing = STATIC
                            height = 40
                            padLeft = 10
                            padRight = 10
                            text = text("this is 1")
                            shape = RECT
                            isMouseAware = false
                        }
                        shape = ROUNDED_RECT
                        onMouseOver {
                            shape = MOUSE_OVER
                        }
                        onMouseOut {
                            shape = ROUNDED_RECT
                        }
                        onMouseDown { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse down 1")
                            }
                        }
                        onMouseUp { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse up 1")
                            }
                        }
                        onMouseRelease { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse release 1")
                            }
                        }
                        onMouseClick { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse click 1")
                            }
                        }
                    }
                    block {
                        hSizing = SHRINK
                        layout = HORIZONTAL
                        block {
                            hAlign = CENTER
                            vAlign = MIDDLE
                            hSizing = SHRINK
                            vSizing = SHRINK
                            padLeft = 10
                            padRight = 10
                            text = text("this is 2")
                            shape = RECT
                            isMouseAware = false
                        }
                        shape = ROUNDED_RECT
                        onMouseOver {
                            shape = MOUSE_OVER
                        }
                        onMouseOut {
                            shape = ROUNDED_RECT
                        }
                        onMouseDown { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse down 2")
                            }
                        }
                        onMouseUp { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse up 2")
                            }
                        }
                        onMouseRelease { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse release 2")
                            }
                        }
                        onMouseClick { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse click 2")
                            }
                        }
                    }
                    block {
                        hSizing = SHRINK
                        layout = HORIZONTAL
                        block {
                            hAlign = CENTER
                            vAlign = MIDDLE
                            hSizing = SHRINK
                            vSizing = SHRINK
                            padLeft = 10
                            padRight = 10
                            text = text("this is 3")
                            shape = RECT
                            isMouseAware = false
                        }
                        shape = ROUNDED_RECT
                        onMouseOver {
                            shape = MOUSE_OVER
                        }
                        onMouseOut {
                            shape = ROUNDED_RECT
                        }
                        onMouseDown { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse down 3")
                            }
                        }
                        onMouseUp { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse up 3")
                            }
                        }
                        onMouseRelease { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse release 3")
                            }
                        }
                        onMouseClick { button, x, y ->
                            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                                println("mouse click 3")
                            }
                        }
                    }
                    block {
                        hSizing = GROW
                        layout = HORIZONTAL
                        shape = ROUNDED_RECT
                        block {
                            hAlign = CENTER
                            vAlign = MIDDLE
                            hSizing = SHRINK
                            vSizing = SHRINK
                            text = text("this is centered")
                            shape = RECT
                            isMouseAware = false
                        }
                    }
                    block {
                        hSizing = STATIC
                        width = 120
                        layout = HORIZONTAL
                        block {
                            hSizing = STATIC
                            width = 40
                            hAlign = LEFT
                            vAlign = TOP
                            layout = HORIZONTAL
                            shape = ROUNDED_RECT
                        }
                        block {
                            hSizing = STATIC
                            width = 40
                            hAlign = LEFT
                            vAlign = TOP
                            layout = HORIZONTAL
                            shape = ROUNDED_RECT
                        }
                        block {
                            hSizing = STATIC
                            width = 40
                            hAlign = LEFT
                            vAlign = TOP
                            layout = HORIZONTAL
                            shape = ROUNDED_RECT
                        }
                    }
                }
//                block {
//                    vSizing = GROW
//                    layout = VERTICAL
//                    hAlign = LEFT
//                    vAlign = TOP
//                    shape = ROUNDED_RECT
//                }
//                block {
//                    vSizing = GROW
//                    layout = VERTICAL
//                    hAlign = LEFT
//                    vAlign = TOP
//                    shape = ROUNDED_RECT
//                }
//                block {
//                    vSizing = GROW
//                    layout = VERTICAL
//                    hAlign = LEFT
//                    vAlign = TOP
//                    shape = ROUNDED_RECT
//                }
            }
            twr(stackPush()) { stack ->
                nk_style_set_font(nk, mainFont)

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

//        val EASY = 0
//        val HARD = 1
//
//        var op = EASY
//
//        val compression = BufferUtils.createIntBuffer(1).put(0, 20)

        val mouseClickHandler = mouseClickHandler { button, x, y, isDown ->
            uiData.root.handleMouseAction(button, x, y, isDown)
        }

        twr(stackPush()) { stack ->
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

            val editData = stack.malloc(100)
            val editDataLength = stack.mallocInt(1)
            editDataLength.put(0, 0)

            val nvgButtonColor = NVGColor.create()
            rgba(180, 50, 40, 255, nvgButtonColor)

            ui(mainStyle, mouseClickHandler, 1280, 720, resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor) { nk, nvg ->
                twr(stackPush()) { stack ->
                    drawButton(nvg, "Click Me!", 200f, 200f, 200f, 32f, nvgButtonColor, uiData.font)
                    uiData.root.width = width
                    uiData.root.height = height
                    uiData.root.handleNewMousePosition(nvg, relativeMouseX, relativeMouseY)
                    uiData.root.draw(nvg)

                    if (nk_begin(nk, "0", windowBounds, NK_WINDOW_BACKGROUND)) {
                        nk_window_set_bounds(nk, nk_rect(0.0f, 0.0f, width.toFloat(), height.toFloat(), windowBounds))

                        nk_style_push_font(nk, glyphFont)
                        nk.staticRow(32, width) {
                            col {
                                nk_style_push_font(nk, mainFont)
                                nk_text(nk, "WorldKit", NK_TEXT_ALIGN_CENTERED or NK_TEXT_ALIGN_MIDDLE)
                                nk_style_pop_font(nk)
                            }
                            col(48) {
                                if (nk_button_label(nk, glyphMinimize)) {
                                    minimizeWindow()
                                }
                            }
                            col(48) {
                                val glyph = if (isMaximized) glyphRestore else glyphMaximize
                                if (nk_button_label(nk, glyph)) {
                                    toggleMaximized()
                                }
                            }
                            col(48) {
                                if (nk_button_label(nk, glyphClose)) {
                                    closeWindow()
                                }
                            }
                        }
                        nk_style_pop_font(nk)

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
                                nk_label(nk, "Water On:", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
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
                                nk_label(nk, "Perspective On:", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
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
                                nk_label(nk, "Rotate Camera:", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
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
                                nk_label(nk, "Height Scale:", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
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
                                if (nk_button_label(nk, "Reset View")) {
                                    resetView.put(0, 1)
                                } else {
                                    resetView.put(0, 0)
                                }
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
                        nk.staticRow(4, width) {
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(40, width) {
                            col(96) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(340) {
                                nk_edit_string(nk, NK_EDIT_FIELD, editData, editDataLength, 100, ::nnk_filter_ascii)
                            }
                            col(96) {
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
                        nk.staticRow(4, width) {
                            col {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        nk.staticRow(40, width) {
                            col(96) {
                                nk_label(nk, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(340) {
                                val bytes = ByteArray(editDataLength[0])
                                for (i in 0..bytes.size - 1) {
                                    bytes[i] = editData[i]
                                }
                                nk_label(nk, String(bytes, Charsets.US_ASCII), NK_TEXT_ALIGN_CENTERED or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(96) {
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