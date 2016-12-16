package com.grimfox.gec

import com.grimfox.gec.ui.color
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.HORIZONTAL
import com.grimfox.gec.ui.widgets.Layout.VERTICAL
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.ui.widgets.toggle
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.cRef
import com.grimfox.gec.util.ref
import java.nio.ByteBuffer

val textFont = ref(-1)
val glyphFont = ref(-1)

val SMALL_SPACER_SIZE = 10
val MEDIUM_SPACER_SIZE = 24

val SMALL_ROW_HEIGHT = 32
val MEDIUM_ROW_HEIGHT = 40
val LARGE_ROW_HEIGHT = 48

val COLOR_BEVELS = color(34, 34, 35)
val COLOR_BEVELS_LIGHTER = color(38, 38, 39)

val COLOR_CLICK_ITEMS_DARKER = color(62, 62, 64)
val COLOR_CLICK_ITEMS = color(82, 82, 84)
val COLOR_CLICK_ITEMS_LIGHTER = color(102, 102, 104)
val COLOR_CLICK_ITEMS_HOVER = color(122, 122, 124)

val COLOR_ACTIVE_HIGHLIGHT_DARK = color(11, 50, 77)
val COLOR_ACTIVE_HIGHLIGHT = color(0, 122, 204)
val COLOR_ACTIVE_HIGHLIGHT_BRIGHT = color(133, 199, 242)

val COLOR_NORMAL_TEXT = color(153, 153, 153)

val COLOR_BUTTON_TEXT = color(243, 243, 243)
val COLOR_BUTTON_MOUSE_OVER = color(64, 62, 64)

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

val TEXT_STYLE_NORMAL = TextStyle(cRef(22.0f), textFont, cRef(COLOR_NORMAL_TEXT))
val TEXT_STYLE_BUTTON = TextStyle(cRef(22.0f), textFont, cRef(COLOR_BUTTON_TEXT))
val TEXT_STYLE_GLYPH = TextStyle(cRef(22.0f), glyphFont, cRef(COLOR_BUTTON_TEXT))

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
                vAlign = MIDDLE,
                layout = HORIZONTAL),
        textShapeTemplate = BlockTemplate(
                hAlign = CENTER,
                vAlign = MIDDLE,
                hSizing = SHRINK,
                vSizing = SHRINK,
                padLeft = SMALL_SPACER_SIZE,
                padRight = SMALL_SPACER_SIZE))

val MENU_TEXT_BUTTON_STYLE = NORMAL_TEXT_BUTTON_STYLE { copy(template = template.copy(vAlign = BOTTOM)) }

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

fun Block.label(text: Text): Block {
    val label = label(text, 0)
    label.hSizing = SHRINK
    return label
}

fun Block.dragArea(title: Text): Block {
    return block {
        hSizing = GROW
        layout = HORIZONTAL
        isMouseAware = false
        label(title).with {
            hAlign = CENTER
            vAlign = TOP
            vSizing = STATIC
            height = SMALL_ROW_HEIGHT
        }
    }
}

fun Block.toggle(value: MutableReference<Boolean>): Block {
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

fun Block.toggleRow(value: MutableReference<Boolean>, height: Int, label: Text, labelWidth: Int, gap: Int): Block {
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

fun Block.buttonRow(height: Int, buttons: Block.() -> Unit): Block {
    return block {
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        block {
            hSizing = SHRINK
            vSizing = SHRINK
            hAlign = CENTER
            vAlign = MIDDLE
            buttons()
        }
    }
}

fun Block.icon(imageRef: Int, imageWidth: Int, imageHeight: Int, layoutWidth: Int, layoutHeight: Int): Block {
    return block {
        hSizing = STATIC
        vSizing = STATIC
        width = layoutWidth
        height = layoutHeight
        layout = HORIZONTAL
        block {
            hAlign = CENTER
            vAlign = MIDDLE
            hSizing = STATIC
            vSizing = STATIC
            width = imageWidth
            height = imageHeight
            shape = ShapeRectangle(FillImageDynamic(imageRef), NO_STROKE)
            isMouseAware = false
        }
    }
}

fun Block.icon(imageRef: Int, imageSize: Int, layoutSize: Int): Block {
    return icon(imageRef, imageSize, imageSize, layoutSize, layoutSize)
}
