package com.grimfox.gec

import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.util.getPathForResource
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

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

        val SMALL_SPACER_SIZE = 10
        val MEDIUM_SPACER_SIZE = 24

        val SMALL_ROW_HEIGHT = 32
        val MEDIUM_ROW_HEIGHT = 40
        val LARGE_ROW_HEIGHT = 48

        val COLOR_BEVELS = nvgRGBA(34.toByte(), 34.toByte(), 35.toByte(), 255.toByte(), NVGColor.create())
        val COLOR_BEVELS_LIGHTER = nvgRGBA(38.toByte(), 38.toByte(), 39.toByte(), 255.toByte(), NVGColor.create())

        val COLOR_CLICK_ITEMS_DARKER = nvgRGBA(62.toByte(), 62.toByte(), 64.toByte(), 255.toByte(), NVGColor.create())
        val COLOR_CLICK_ITEMS = nvgRGBA(82.toByte(), 82.toByte(), 84.toByte(), 255.toByte(), NVGColor.create())
        val COLOR_CLICK_ITEMS_LIGHTER = nvgRGBA(102.toByte(), 102.toByte(), 104.toByte(), 255.toByte(), NVGColor.create())
        val COLOR_CLICK_ITEMS_HOVER = nvgRGBA(122.toByte(), 122.toByte(), 124.toByte(), 255.toByte(), NVGColor.create())

        val COLOR_ACTIVE_HIGHLIGHT_DARK = nvgRGBA(11.toByte(), 50.toByte(), 77.toByte(), 255.toByte(), NVGColor.create())
        val COLOR_ACTIVE_HIGHLIGHT = nvgRGBA(0.toByte(), 122.toByte(), 204.toByte(), 255.toByte(), NVGColor.create())
        val COLOR_ACTIVE_HIGHLIGHT_BRIGHT = nvgRGBA(133.toByte(), 199.toByte(), 242.toByte(), 255.toByte(), NVGColor.create())

        val COLOR_NORMAL_TEXT = nvgRGBA(153.toByte(), 153.toByte(), 153.toByte(), 255.toByte(), NVGColor.create())

        val COLOR_BUTTON_TEXT = nvgRGBA(243.toByte(), 243.toByte(), 243.toByte(), 255.toByte(), NVGColor.create())
        val COLOR_BUTTON_MOUSE_OVER = nvgRGBA(64.toByte(), 62.toByte(), 64.toByte(), 255.toByte(), NVGColor.create())

        val FILL_BUTTON_MOUSE_OVER = FillColor(COLOR_BUTTON_MOUSE_OVER)
        val FILL_BUTTON_MOUSE_DOWN = FillColor(COLOR_ACTIVE_HIGHLIGHT)

        val BUTTON_NORMAL = NO_SHAPE
        val BUTTON_MOUSE_OVER = ShapeRectangle(FILL_BUTTON_MOUSE_OVER, NO_STROKE)
        val BUTTON_MOUSE_DOWN = ShapeRectangle(FILL_BUTTON_MOUSE_DOWN, NO_STROKE)

        val FILL_TOGGLE_BACKGROUND_NORMAL = FillColor(COLOR_BEVELS)
        val FILL_TOGGLE_BACKGROUND_MOUSE_OVER = FillColor(COLOR_BEVELS_LIGHTER)
        val FILL_TOGGLE_BACKGROUND_MOUSE_DOWN = FillColor(COLOR_ACTIVE_HIGHLIGHT_DARK)

        val FILL_TOGGLE_SWITCH_NORMAL_OFF = FillColor(COLOR_CLICK_ITEMS_DARKER)
        val FILL_TOGGLE_SWITCH_MOUSE_OVER_OFF = FillColor(COLOR_CLICK_ITEMS)
        val FILL_TOGGLE_SWITCH_MOUSE_DOWN_OFF = FillColor(COLOR_ACTIVE_HIGHLIGHT)

        val FILL_TOGGLE_SWITCH_NORMAL_ON = FillColor(COLOR_CLICK_ITEMS_LIGHTER)
        val FILL_TOGGLE_SWITCH_MOUSE_OVER_ON = FillColor(COLOR_CLICK_ITEMS_HOVER)
        val FILL_TOGGLE_SWITCH_MOUSE_DOWN_ON = FillColor(COLOR_ACTIVE_HIGHLIGHT)

        val TOGGLE_WIDTH = 76
        val TOGGLE_HEIGHT = 32
        val TOGGLE_CORNER_RADIUS = TOGGLE_HEIGHT / 2.0f

        val TOGGLE_BACKGROUND_NORMAL = ShapeRoundedRectangle(FILL_TOGGLE_BACKGROUND_NORMAL, NO_STROKE, TOGGLE_CORNER_RADIUS)
        val TOGGLE_BACKGROUND_MOUSE_OVER = ShapeRoundedRectangle(FILL_TOGGLE_BACKGROUND_MOUSE_OVER, NO_STROKE, TOGGLE_CORNER_RADIUS)
        val TOGGLE_BACKGROUND_MOUSE_DOWN = ShapeRoundedRectangle(FILL_TOGGLE_BACKGROUND_MOUSE_DOWN, NO_STROKE, TOGGLE_CORNER_RADIUS)

        val TOGGLE_SWITCH_NORMAL_OFF = ShapeCircle(FILL_TOGGLE_SWITCH_NORMAL_OFF, NO_STROKE)
        val TOGGLE_SWITCH_MOUSE_OVER_OFF = ShapeCircle(FILL_TOGGLE_SWITCH_MOUSE_OVER_OFF, NO_STROKE)
        val TOGGLE_SWITCH_MOUSE_DOWN_OFF = ShapeCircle(FILL_TOGGLE_SWITCH_MOUSE_DOWN_OFF, NO_STROKE)

        val TOGGLE_SWITCH_NORMAL_ON = ShapeCircle(FILL_TOGGLE_SWITCH_NORMAL_ON, NO_STROKE)
        val TOGGLE_SWITCH_MOUSE_OVER_ON = ShapeCircle(FILL_TOGGLE_SWITCH_MOUSE_OVER_ON, NO_STROKE)
        val TOGGLE_SWITCH_MOUSE_DOWN_ON = ShapeCircle(FILL_TOGGLE_SWITCH_MOUSE_DOWN_ON, NO_STROKE)

        val DEFAULT_HEIGHT_SCALE = 0.5f
        val heightMapScaleFactor = Reference(DEFAULT_HEIGHT_SCALE)
        val waterPlaneOn = Reference(true)
        val perspectiveOn = Reference(true)
        val rotateAroundCamera = Reference(false)
        val resetView = Reference(false)

        val mainStyle = layout { ui, nk, nvg ->
            ui {
                val iconNvg = nvgCreateImage(nvg, getPathForResource("/textures/wk-icon-128.png"), NVG_IMAGE_GENERATE_MIPMAPS)

                val ICON_SHAPE = ShapeRectangle(FillImageDynamic(iconNvg), NO_STROKE)

                val textFont = getNvgFont(createNvgFont("/fonts/FiraSans.ttf", "FiraSans", nvg))
                val glyphFont = getNvgFont(createNvgFont("/fonts/WorldKitUi.ttf", "Glyphs", nvg))

                val TEXT_STYLE_NORMAL = TextStyle(22.0f, textFont, COLOR_NORMAL_TEXT)
                val TEXT_STYLE_BUTTON = TextStyle(22.0f, textFont, COLOR_BUTTON_TEXT)
                val TEXT_STYLE_GLYPH = TextStyle(22.0f, glyphFont, COLOR_BUTTON_TEXT)

                val TOGGLE_STYLE = ToggleStyle(
                        4,
                        TOGGLE_BACKGROUND_NORMAL,
                        TOGGLE_SWITCH_NORMAL_ON,
                        TOGGLE_SWITCH_NORMAL_OFF,
                        TEXT_STYLE_NORMAL,
                        TEXT_STYLE_NORMAL,
                        TOGGLE_BACKGROUND_MOUSE_OVER,
                        TOGGLE_SWITCH_MOUSE_OVER_ON,
                        TOGGLE_SWITCH_MOUSE_OVER_OFF,
                        TEXT_STYLE_NORMAL,
                        TEXT_STYLE_NORMAL,
                        TOGGLE_BACKGROUND_MOUSE_DOWN,
                        TOGGLE_SWITCH_MOUSE_DOWN_ON,
                        TOGGLE_SWITCH_MOUSE_DOWN_OFF,
                        TEXT_STYLE_NORMAL,
                        TEXT_STYLE_NORMAL)

                val WINDOW_DECORATE_BUTTON_STYLE = ButtonStyle(
                        BUTTON_NORMAL,
                        TEXT_STYLE_GLYPH,
                        BUTTON_MOUSE_OVER,
                        TEXT_STYLE_GLYPH,
                        BUTTON_MOUSE_DOWN,
                        TEXT_STYLE_GLYPH,
                        template = BlockTemplate(
                                hSizing = STATIC,
                                vSizing = STATIC,
                                width = LARGE_ROW_HEIGHT,
                                height = SMALL_ROW_HEIGHT,
                                hAlign = LEFT,
                                vAlign = TOP,
                                layout = HORIZONTAL))

                val NORMAL_TEXT_BUTTON_STYLE = ButtonStyle(
                        BUTTON_NORMAL,
                        TEXT_STYLE_BUTTON,
                        BUTTON_MOUSE_OVER,
                        TEXT_STYLE_BUTTON,
                        BUTTON_MOUSE_DOWN,
                        TEXT_STYLE_BUTTON,
                        template = BlockTemplate(
                                hSizing = SHRINK,
                                vSizing = STATIC,
                                height = SMALL_ROW_HEIGHT,
                                vAlign = BOTTOM,
                                layout = HORIZONTAL),
                        textShapeTemplate = BlockTemplate(
                                hAlign = CENTER,
                                vAlign = MIDDLE,
                                hSizing = SHRINK,
                                vSizing = SHRINK,
                                padLeft = SMALL_SPACER_SIZE,
                                padRight = SMALL_SPACER_SIZE))


                fun text(value: String, style: TextStyle = TEXT_STYLE_NORMAL): Text {
                    return StaticTextUtf8(value, style)
                }

                fun glyph(value: String, style: TextStyle = TEXT_STYLE_GLYPH): Text {
                    return StaticTextUtf8(value, style)
                }

                fun glyph(value: ByteBuffer, style: TextStyle = TEXT_STYLE_GLYPH): Text {
                    return DynamicTextUtf8(value, style)
                }

                val TEXT_ON = text("On")
                val TEXT_OFF = text("Off")

                fun Block.label(text: Text, width: Int): Block {
                    return block {
                        hSizing = STATIC
                        this.width = width
                        layout = HORIZONTAL
                        block {
                            hAlign = RIGHT
                            vAlign = MIDDLE
                            hSizing = SHRINK
                            vSizing = SHRINK
                            this.text = text
                            isMouseAware = false
                        }
                        isMouseAware = false
                    }
                }

                fun Block.toggle(value: Reference<Boolean>): Block {
                    return toggle(value, TOGGLE_WIDTH, TOGGLE_HEIGHT, TEXT_ON, TEXT_OFF, TOGGLE_STYLE)
                }

                fun Block.supplantEvents(other: Block): Block {
                    other.isMouseAware = false
                    onMouseOver = other.onMouseOver
                    onMouseOut = other.onMouseOut
                    onMouseDown = other.onMouseDown
                    onMouseUp = other.onMouseUp
                    onMouseRelease = other.onMouseRelease
                    onMouseClick = other.onMouseClick
                    return this
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

                fun Block.toggleRow(value: Reference<Boolean>, height: Int, label: Text, labelWidth: Int, gap: Int): Block {
                    return block {
                        val row = this
                        vSizing = STATIC
                        this.height = height
                        layout = VERTICAL
                        label(label, labelWidth)
                        hSpacer(gap)
                        block {
                            hSizing = GROW
                            layout = HORIZONTAL
                            val toggle = toggle(value)
                            row.supplantEvents(toggle)
                            isMouseAware = false
                        }
                    }
                }

                background.set(45, 45, 48)

                root {
                    block {
                        vSizing = STATIC
                        height = MEDIUM_ROW_HEIGHT
                        layout = VERTICAL
                        block {
                            hSizing = STATIC
                            vSizing = STATIC
                            width = MEDIUM_ROW_HEIGHT
                            height = MEDIUM_ROW_HEIGHT
                            layout = HORIZONTAL
                            block {
                                hAlign = CENTER
                                vAlign = MIDDLE
                                hSizing = STATIC
                                vSizing = STATIC
                                height = SMALL_ROW_HEIGHT
                                width = SMALL_ROW_HEIGHT
                                shape = ICON_SHAPE
                                isMouseAware = false
                            }
                            isMouseAware = false
                        }
                        hSpacer(SMALL_SPACER_SIZE)
                        block {
                            hSizing = SHRINK
                            vSizing = STATIC
                            height = SMALL_ROW_HEIGHT
                            vAlign = BOTTOM
                            layout = HORIZONTAL
                            block {
                                hAlign = CENTER
                                vAlign = MIDDLE
                                hSizing = SHRINK
                                vSizing = SHRINK
                                padLeft = SMALL_SPACER_SIZE
                                padRight = SMALL_SPACER_SIZE
                                text = text("File", TEXT_STYLE_BUTTON)
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
                            height = SMALL_ROW_HEIGHT
                            vAlign = BOTTOM
                            layout = HORIZONTAL
                            block {
                                hAlign = CENTER
                                vAlign = MIDDLE
                                hSizing = SHRINK
                                vSizing = SHRINK
                                padLeft = SMALL_SPACER_SIZE
                                padRight = SMALL_SPACER_SIZE
                                text = text("Settings", TEXT_STYLE_BUTTON)
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
                            height = SMALL_ROW_HEIGHT
                            vAlign = BOTTOM
                            layout = HORIZONTAL
                            block {
                                hAlign = CENTER
                                vAlign = MIDDLE
                                hSizing = SHRINK
                                vSizing = SHRINK
                                padLeft = SMALL_SPACER_SIZE
                                padRight = SMALL_SPACER_SIZE
                                text = text("Help", TEXT_STYLE_BUTTON)
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
                                    println("mouse click Help")
                                }
                            }
                        }
                        hSpacer(SMALL_ROW_HEIGHT)
                        dragArea = block {
                            hSizing = GROW
                            layout = HORIZONTAL
                            block {
                                hAlign = CENTER
                                vAlign = TOP
                                hSizing = SHRINK
                                vSizing = STATIC
                                height = SMALL_ROW_HEIGHT
                                layout = HORIZONTAL
                                block {
                                    hAlign = LEFT
                                    vAlign = MIDDLE
                                    hSizing = SHRINK
                                    vSizing = SHRINK
                                    text = text("WorldKit - Edit Mode")
                                    isMouseAware = false
                                }
                                isMouseAware = false
                            }
                            isMouseAware = false
                        }
                        hSpacer(SMALL_SPACER_SIZE)
                        block {
                            hSizing = SHRINK
                            layout = HORIZONTAL
                            button(glyph(glyphMinimize), WINDOW_DECORATE_BUTTON_STYLE) { minimizeWindow() }
                            button(glyph(maxRestoreGlyph), WINDOW_DECORATE_BUTTON_STYLE) { toggleMaximized() }
                            button(glyph(glyphClose), WINDOW_DECORATE_BUTTON_STYLE) { closeWindow() }
                        }
                    }
                    block {
                        vSizing = GROW
                        layout = VERTICAL
                        hAlign = LEFT
                        vAlign = TOP
                        block {
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
                                block {
                                    vSizing = STATIC
                                    height = LARGE_ROW_HEIGHT
                                    layout = VERTICAL
                                    block {
                                        hSizing = SHRINK
                                        vSizing = SHRINK
                                        hAlign = CENTER
                                        vAlign = MIDDLE
                                        button(text("Reset view"), NORMAL_TEXT_BUTTON_STYLE) { resetView.value = true }
                                        button(text("Reset height"), NORMAL_TEXT_BUTTON_STYLE) { heightMapScaleFactor.value = DEFAULT_HEIGHT_SCALE }
                                    }
                                }
                            }
                            hSpacer(MEDIUM_SPACER_SIZE)
                        }
                        block {
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

        ui(mainStyle, 1280, 720, resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor) { nk, nvg ->
            if (isMaximized && !lastMaximized) {
                setRestore()
            } else if (!isMaximized && lastMaximized) {
                setMaximize()
            }
            lastMaximized = isMaximized
        }
    }
}
