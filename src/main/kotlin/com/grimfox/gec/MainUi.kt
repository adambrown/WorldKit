package com.grimfox.gec

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.ui.*
import org.lwjgl.BufferUtils
import org.lwjgl.nuklear.*
import org.lwjgl.nuklear.Nuklear.*
import org.lwjgl.system.MemoryStack.stackPush
import java.nio.charset.Charset
import java.util.*

object MainUi {

    @JvmStatic fun main(vararg args: String) {
        val mainFont = NkUserFont.create()
        val glyphFont = NkUserFont.create()
        val glyphIndex = Array(95) { i -> (i + 32).toChar().toString() }
        val glyphClose = glyphIndex[0]
        val glyphMinimize = glyphIndex[1]
        val glyphRestore = glyphIndex[2]
        val glyphMaximize = glyphIndex[3]
        val mainStyle = style {
            createFont("fonts/FiraSans.ttf", 22.0f, 32, 95, 512, 512, mainFont)
            createFont("fonts/WorldKitUi.ttf", 22.0f, 32, 4, 512, 512, glyphFont)

            background.set(45, 45, 48)

            dragAreaLeftMargin = 0
            dragAreaRightMargin = 146
            dragAreaTopMargin = 0
            dragAreaHeight = 32

            init { context ->
                twr(stackPush()) { stack ->
                    nk_style_set_font(context, mainFont)

                    val window = NkStyleWindow.create()
                    window.background().set(background)
                    window.background(background)
                    window.border(0.0f)
                    window.padding().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    window.scrollbar_size().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    context.style().window().set(NkStyleWindow.create())

                    val button = context.style().button()
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
                    context.style().button().set(button)

                    val slider = context.style().slider()
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
                    context.style().slider().set(slider)

                    val text = context.style().text()
                    text.color(nk_rgb(153, 153, 153, NkColor.create()))
                    text.padding().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))

                    val checkBox = context.style().checkbox()
                    checkBox.border(0.0f)
                    checkBox.border_color().set(background)
                    checkBox.padding().set(nk_vec2(0.0f, 0.0f, NkVec2.mallocStack(stack)))
                    checkBox.spacing(0.0f)
                    checkBox.active(nk_style_item_color(nk_rgb(0, 122, 204, NkColor.create()), NkStyleItem.create()))
                    checkBox.normal(nk_style_item_color(nk_rgb(62, 62, 64, NkColor.create()), NkStyleItem.create()))
                    checkBox.hover(nk_style_item_color(nk_rgb(100, 101, 103, NkColor.create()), NkStyleItem.create()))
                    checkBox.cursor_hover(nk_style_item_color(nk_rgb(100, 101, 103, NkColor.create()), NkStyleItem.create()))
                    checkBox.cursor_normal(nk_style_item_color(nk_rgb(0, 122, 204, NkColor.create()), NkStyleItem.create()))
                    context.style().checkbox().set(checkBox)
                }
            }
        }

//        val EASY = 0
//        val HARD = 1
//
//        var op = EASY
//
//        val compression = BufferUtils.createIntBuffer(1).put(0, 20)

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

            ui(mainStyle, 1280, 720, resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor) { context ->
                twr(stackPush()) { stack ->

                    if (nk_begin(context, "0", windowBounds, NK_WINDOW_BACKGROUND)) {
                        nk_window_set_bounds(context, nk_rect(0.0f, 0.0f, width.toFloat(), height.toFloat(), windowBounds))

                        nk_style_push_font(context, glyphFont)
                        context.staticRow(32, width) {
                            col {
                                nk_style_push_font(context, mainFont)
                                nk_text(context, "WorldKit", NK_TEXT_ALIGN_CENTERED or NK_TEXT_ALIGN_MIDDLE)
                                nk_style_pop_font(context)
                            }
                            col(48) {
                                if (nk_button_label(context, glyphMinimize)) {
                                    minimizeWindow()
                                }
                            }
                            col(48) {
                                val glyph = if (isMaximized) glyphRestore else glyphMaximize
                                if (nk_button_label(context, glyph)) {
                                    toggleMaximized()
                                }
                            }
                            col(48) {
                                if (nk_button_label(context, glyphClose)) {
                                    closeWindow()
                                }
                            }
                        }
                        nk_style_pop_font(context)

                        context.staticRow(48, width) {
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(48, width) {
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(context, "Water On:", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(280) {
                                nk_checkbox_label(context, "", waterPlaneOn)
                            }
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(48, width) {
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(context, "Perspective On:", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(280) {
                                nk_checkbox_label(context, "", perspectiveOn)
                            }
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(48, width) {
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(context, "Rotate Camera:", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(280) {
                                nk_checkbox_label(context, "", rotateAroundCamera)
                            }
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(48, width) {
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                nk_label(context, "Height Scale:", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(320) {
                                nk_slider_float(context, 0.0f, heightMapScaleFactor, 1.0f, 0.0001f)
                            }
                            col(24) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(4, width) {
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(40, width) {
                            col(196) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(140) {
                                if (nk_button_label(context, "Reset View")) {
                                    resetView.put(0, 1)
                                } else {
                                    resetView.put(0, 0)
                                }
                            }
                            col(196) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(4, width) {
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(4, width) {
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(40, width) {
                            col(96) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(340) {
                                nk_edit_string(context, NK_EDIT_FIELD, editData, editDataLength, 100, ::nnk_filter_ascii)
                            }
                            col(96) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(4, width) {
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(4, width) {
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(40, width) {
                            col(96) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(340) {
                                val bytes = ByteArray(editDataLength[0])
                                for (i in 0..bytes.size - 1) {
                                    bytes[i] = editData[i]
                                }
                                nk_label(context, String(bytes, Charsets.US_ASCII), NK_TEXT_ALIGN_CENTERED or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col(96) {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
                            }
                        }
                        context.staticRow(4, width) {
                            col {
                                nk_label(context, "", NK_TEXT_ALIGN_LEFT or NK_TEXT_ALIGN_MIDDLE)
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
                    nk_end(context)
                }
            }
        }
    }
}

data class StaticRow(var remainder: Int, var greedyCount: Int = 0, val columns: MutableList<Pair<Int, () -> Unit>> = ArrayList()) {

    fun col(width: Int, layout: () -> Unit) {
        columns.add(Pair(width, layout))
        remainder -= width
    }

    fun col(layout: () -> Unit) {
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