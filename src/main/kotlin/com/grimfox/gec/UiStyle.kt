package com.grimfox.gec

import com.grimfox.gec.model.ObservableMutableList
import com.grimfox.gec.ui.KeyboardHandler
import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.nvgproxy.*
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.HorizontalTruncation.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.FileDialogs.saveFile
import com.grimfox.gec.util.FileDialogs.selectFile
import com.grimfox.gec.util.FileDialogs.selectFolder
import org.lwjgl.glfw.GLFW
import java.io.File
import java.math.BigDecimal
import java.nio.ByteBuffer

val textFont = ref(-1)
val glyphFont = ref(-1)

val SMALL_SPACER_SIZE = 6.0f
val MEDIUM_SPACER_SIZE = 12.0f
val LARGE_SPACER_SIZE = 24.0f
val MEGA_SPACER_SIZE = 48.0f

val HALF_ROW_HEIGHT = 10.0f
val SMALL_ROW_HEIGHT = 20.0f
val MEDIUM_ROW_HEIGHT = 26.0f
val LARGE_ROW_HEIGHT = 32.0f

val PANEL_WIDTH = 800.0f

val COLOR_DROP_SHADOW_BLACK = color(5, 5, 6, 72)
val COLOR_DROP_SHADOW_BLACK_TRANSPARENT = color(5, 5, 6, 0)

val COLOR_DROP_SHADOW_DARK_BLACK = color(5, 5, 6, 132)
val COLOR_DROP_SHADOW_DARK_BLACK_TRANSPARENT = color(5, 5, 6, 0)

val COLOR_BACKGROUND = color(45, 45, 48)
val FILL_BACKGROUND = FillColor(COLOR_BACKGROUND)
val BACKGROUND_RECT = ShapeRectangle(FILL_BACKGROUND, NO_STROKE)

val COLOR_BEVELS = color(34, 34, 35)
val COLOR_BEVELS_LIGHTER = color(38, 38, 39)

val COLOR_TEXT_BOX_BACKGROUND_NORMAL = color(51, 51, 55)
val COLOR_TEXT_BOX_BACKGROUND_MOUSE_OVER = color(61, 61, 65)

val COLOR_CLICK_ITEMS_DARKER = color(62, 62, 64)
val COLOR_CLICK_ITEMS = color(82, 82, 84)
val COLOR_CLICK_ITEMS_LIGHTER = color(102, 102, 104)
val COLOR_CLICK_ITEMS_HOVER = color(122, 122, 124)

//val COLOR_ACTIVE_HIGHLIGHT_DARK = color(11, 50, 77)
val COLOR_ACTIVE_HIGHLIGHT = color(0, 122, 204)
//val COLOR_ACTIVE_HIGHLIGHT_BRIGHT = color(133, 199, 242)

val COLOR_NORMAL_TEXT = color(153, 153, 153)
val COLOR_BUTTON_TEXT = color(243, 243, 243)


val COLOR_BUTTON_MOUSE_OVER = color(64, 62, 64)

val COLOR_POSITIVE_HIGHLIGHT = color(67, 128, 64)
val COLOR_NEGATIVE_HIGHLIGHT = color(128, 67, 64)

val FILL_POSITIVE_HIGHLIGHT = FillColor(COLOR_POSITIVE_HIGHLIGHT)
val FILL_NEGATIVE_HIGHLIGHT = FillColor(COLOR_NEGATIVE_HIGHLIGHT)

val FILL_BUTTON_MOUSE_OVER = FillColor(COLOR_BUTTON_MOUSE_OVER)
val FILL_BUTTON_MOUSE_DOWN = FillColor(COLOR_ACTIVE_HIGHLIGHT)

val SHAPE_BUTTON_NORMAL = NO_SHAPE
val SHAPE_BUTTON_MOUSE_OVER = ShapeRectangle(FILL_BUTTON_MOUSE_OVER, NO_STROKE)
val SHAPE_BUTTON_MOUSE_DOWN = ShapeRectangle(FILL_BUTTON_MOUSE_DOWN, NO_STROKE)

val STROKE_BUTTON_DIALOG = StrokeColor(COLOR_BUTTON_MOUSE_OVER, 1.0f)
val SHAPE_BUTTON_DIALOG = ShapeRectangle(FILL_BACKGROUND, STROKE_BUTTON_DIALOG)

val SHAPE_BUTTON_POSITIVE = ShapeRectangle(FILL_POSITIVE_HIGHLIGHT, NO_STROKE)
val SHAPE_BUTTON_NEGATIVE = ShapeRectangle(FILL_NEGATIVE_HIGHLIGHT, NO_STROKE)

val COLOR_WHITE = color(255, 255, 255, 255)
val COLOR_BLACK = color(0, 0, 0, 255)

val STROKE_WHITE_1 = StrokeColor(COLOR_WHITE, 1.0f)
val STROKE_BLACK_1 = StrokeColor(COLOR_BLACK, 1.0f)

val SWITCH_HEIGHT = 12.0f
val ELEMENT_INSET = 3.0f

val TOGGLE_WIDTH = 52.0f
val TOGGLE_HEIGHT = SWITCH_HEIGHT + (ELEMENT_INSET * 2.0f)
val TOGGLE_CORNER_RADIUS = TOGGLE_HEIGHT / 2.0f

val FILL_TOGGLE_BACKGROUND_NORMAL = FillColor(COLOR_BEVELS)
val FILL_TOGGLE_BACKGROUND_MOUSE_OVER = FillColor(COLOR_BEVELS_LIGHTER)

val FILL_TOGGLE_SWITCH_NORMAL_OFF = FillColor(COLOR_CLICK_ITEMS_DARKER)
val FILL_TOGGLE_SWITCH_MOUSE_OVER_OFF = FillColor(COLOR_CLICK_ITEMS)
val FILL_TOGGLE_SWITCH_MOUSE_DOWN_OFF = FillColor(COLOR_ACTIVE_HIGHLIGHT)

val FILL_TOGGLE_SWITCH_NORMAL_ON = FillColor(COLOR_CLICK_ITEMS_LIGHTER)
val FILL_TOGGLE_SWITCH_MOUSE_OVER_ON = FillColor(COLOR_CLICK_ITEMS_HOVER)
val FILL_TOGGLE_SWITCH_MOUSE_DOWN_ON = FillColor(COLOR_ACTIVE_HIGHLIGHT)

val TOGGLE_BACKGROUND_NORMAL = ShapeRoundedRectangle(FILL_TOGGLE_BACKGROUND_NORMAL, NO_STROKE, TOGGLE_CORNER_RADIUS)
val TOGGLE_BACKGROUND_MOUSE_OVER = ShapeRoundedRectangle(FILL_TOGGLE_BACKGROUND_MOUSE_OVER, NO_STROKE, TOGGLE_CORNER_RADIUS)

val TOGGLE_SWITCH_NORMAL_OFF = ShapeCircle(FILL_TOGGLE_SWITCH_NORMAL_OFF, NO_STROKE)
val TOGGLE_SWITCH_MOUSE_OVER_OFF = ShapeCircle(FILL_TOGGLE_SWITCH_MOUSE_OVER_OFF, NO_STROKE)
val TOGGLE_SWITCH_MOUSE_DOWN_OFF = ShapeCircle(FILL_TOGGLE_SWITCH_MOUSE_DOWN_OFF, NO_STROKE)

val TOGGLE_SWITCH_NORMAL_ON = ShapeCircle(FILL_TOGGLE_SWITCH_NORMAL_ON, NO_STROKE)
val TOGGLE_SWITCH_MOUSE_OVER_ON = ShapeCircle(FILL_TOGGLE_SWITCH_MOUSE_OVER_ON, NO_STROKE)
val TOGGLE_SWITCH_MOUSE_DOWN_ON = ShapeCircle(FILL_TOGGLE_SWITCH_MOUSE_DOWN_ON, NO_STROKE)


val SLIDER_BAR_UNFILLED_NORMAL = ShapeRoundedRectangle(FillColor(COLOR_BEVELS), NO_STROKE, 3.0f)
val SLIDER_BAR_FILLED_NORMAL = ShapeRoundedRectangle(FillColor(COLOR_CLICK_ITEMS), NO_STROKE, 4.0f)
val SLIDER_SWITCH_NORMAL = ShapeCircle(FillColor(COLOR_CLICK_ITEMS), NO_STROKE)

val SLIDER_BAR_UNFILLED_MOUSE_OVER = ShapeRoundedRectangle(FillColor(COLOR_BEVELS_LIGHTER), NO_STROKE, 3.0f)
val SLIDER_BAR_FILLED_MOUSE_OVER = ShapeRoundedRectangle(FillColor(COLOR_CLICK_ITEMS_LIGHTER), NO_STROKE, 4.0f)
val SLIDER_SWITCH_MOUSE_OVER = ShapeCircle(FillColor(COLOR_CLICK_ITEMS_LIGHTER), NO_STROKE)

val SLIDER_BAR_FILLED_MOUSE_DOWN = ShapeRoundedRectangle(FillColor(COLOR_ACTIVE_HIGHLIGHT), NO_STROKE, 4.0f)
val SLIDER_SWITCH_MOUSE_DOWN = ShapeCircle(FillColor(COLOR_ACTIVE_HIGHLIGHT), NO_STROKE)

val COLOR_MENU_BACKGROUND = color(27, 27, 28)
val COLOR_MENU_HIGHLIGHT = color(62, 62, 64)
val COLOR_BORDERS_AND_FRAMES = color(63, 63, 70)
val COLOR_BORDERS_AND_FRAMES_LIGHT = color(68, 68, 75)

val STROKE_BORDER_ONLY = StrokeColor(COLOR_BORDERS_AND_FRAMES, 1.0f)
val SHAPE_BORDER_ONLY = ShapeRectangle(NO_FILL, STROKE_BORDER_ONLY)

val FILL_TEXT_BOX_BACKGROUND_NORMAL = FillColor(COLOR_TEXT_BOX_BACKGROUND_NORMAL)
val FILL_TEXT_BOX_BACKGROUND_MOUSE_OVER = FillColor(COLOR_TEXT_BOX_BACKGROUND_MOUSE_OVER)

val STROKE_TEXT_BOX_NORMAL = StrokeColor(COLOR_BORDERS_AND_FRAMES, 1.0f)
val STROKE_TEXT_BOX_MOUSE_OVER = StrokeColor(COLOR_BORDERS_AND_FRAMES_LIGHT, 1.0f)
val STROKE_TEXT_BOX_ACTIVE = StrokeColor(COLOR_ACTIVE_HIGHLIGHT, 1.0f)


val SHAPE_TEXT_BOX_BACKGROUND_NORMAL = ShapeRectangle(FILL_TEXT_BOX_BACKGROUND_NORMAL, STROKE_TEXT_BOX_NORMAL)
val SHAPE_TEXT_BOX_BACKGROUND_MOUSE_OVER = ShapeRectangle(FILL_TEXT_BOX_BACKGROUND_MOUSE_OVER, STROKE_TEXT_BOX_MOUSE_OVER)
val SHAPE_TEXT_BOX_BACKGROUND_ACTIVE = ShapeRectangle(FILL_TEXT_BOX_BACKGROUND_MOUSE_OVER, STROKE_TEXT_BOX_ACTIVE)

val COLOR_DISABLED_CLICKABLE = color(78, 78, 80)

val FILL_MENU_BACKGROUND = FillColor(COLOR_MENU_BACKGROUND)
val FILL_MENU_BORDER = FillColor(COLOR_MENU_HIGHLIGHT)
val FILL_BORDERS_AND_FRAMES = FillColor(COLOR_BORDERS_AND_FRAMES)


val SHAPE_MENU_BACKGROUND = ShapeRectangle(FILL_MENU_BACKGROUND, NO_STROKE)
val SHAPE_MENU_BORDER = ShapeRectangle(FILL_MENU_BORDER, NO_STROKE)
val SHAPE_BORDER_AND_FRAME_RECTANGLE = ShapeRectangle(FILL_BORDERS_AND_FRAMES, NO_STROKE)

val FONT_SIZE_15 = cRef(15.0f)
val FONT_SIZE_16 = cRef(16.0f)
val FONT_SIZE_18 = cRef(18.0f)
val FONT_SIZE_22 = cRef(22.0f)
val FONT_SIZE_36 = cRef(36.0f)

val TEXT_STYLE_DISABLED = TextStyle(FONT_SIZE_15, textFont, cRef(COLOR_DISABLED_CLICKABLE))
val TEXT_STYLE_NORMAL = TextStyle(FONT_SIZE_15, textFont, cRef(COLOR_NORMAL_TEXT))
val TEXT_STYLE_BUTTON = TextStyle(FONT_SIZE_15, textFont, cRef(COLOR_BUTTON_TEXT))
val TEXT_STYLE_GLYPH = TextStyle(FONT_SIZE_16, glyphFont, cRef(COLOR_BUTTON_TEXT))

val TEXT_STYLE_LARGE_MESSAGE = TextStyle(FONT_SIZE_36, textFont, cRef(COLOR_BUTTON_TEXT))
val TEXT_STYLE_SMALL_MESSAGE = TextStyle(FONT_SIZE_18, textFont, cRef(COLOR_BUTTON_TEXT))

val TEXT_STYLE_BUTTON_LARGE = TextStyle(FONT_SIZE_22, textFont, cRef(COLOR_BUTTON_TEXT))


val DIVIDER_DARK = ShapeRectangle(FillColor(COLOR_BEVELS), NO_STROKE)
val DIVIDER_LIGHT = ShapeRectangle(FillColor(COLOR_CLICK_ITEMS_DARKER), NO_STROKE)

val SHAPE_CURSOR = { caret: Caret ->
    ShapeCursor(FillColor(COLOR_BUTTON_TEXT), NO_STROKE, FILL_BUTTON_MOUSE_DOWN, caret)
}

val FILL_DROP_SHADOW = FillBoxGradient(COLOR_DROP_SHADOW_BLACK, COLOR_DROP_SHADOW_BLACK_TRANSPARENT, 6.0f, 12.0f)
val SHAPE_DROP_SHADOW = ShapeDropShadow(FILL_DROP_SHADOW, NO_STROKE, 6.0f, 6.0f)
val FILL_DROP_SHADOW_DARK = FillBoxGradient(COLOR_DROP_SHADOW_DARK_BLACK, COLOR_DROP_SHADOW_DARK_BLACK_TRANSPARENT, 8.0f, 16.0f)
val SHAPE_DROP_SHADOW_DARK = ShapeDropShadow(FILL_DROP_SHADOW_DARK, NO_STROKE, 8.0f, 8.0f)

val COLOR_GREY_OUT = color(5, 5, 6, 156)

val FILL_GREY_OUT = ShapeRectangle(FillColor(COLOR_GREY_OUT), NO_STROKE)

val TOGGLE_STYLE = ToggleStyle(
        backgroundNormal = TOGGLE_BACKGROUND_NORMAL,
        switchNormalOn = TOGGLE_SWITCH_NORMAL_ON,
        switchNormalOff = TOGGLE_SWITCH_NORMAL_OFF,
        textNormalOn = TEXT_STYLE_NORMAL,
        textNormalOff = TEXT_STYLE_NORMAL,
        backgroundMouseOver = TOGGLE_BACKGROUND_MOUSE_OVER,
        switchMouseOverOn = TOGGLE_SWITCH_MOUSE_OVER_ON,
        switchMouseOverOff = TOGGLE_SWITCH_MOUSE_OVER_OFF,
        textMouseOverOn = TEXT_STYLE_NORMAL,
        textMouseOverOff = TEXT_STYLE_NORMAL,
        backgroundMouseDown = TOGGLE_BACKGROUND_MOUSE_OVER,
        switchMouseDownOn = TOGGLE_SWITCH_MOUSE_DOWN_ON,
        switchMouseDownOff = TOGGLE_SWITCH_MOUSE_DOWN_OFF,
        textMouseDownOn = TEXT_STYLE_NORMAL,
        textMouseDownOff = TEXT_STYLE_NORMAL,
        inset = ELEMENT_INSET,
        template = BlockTemplate(
                vAlign = MIDDLE,
                hSizing = STATIC,
                vSizing = STATIC,
                width = TOGGLE_WIDTH,
                height = TOGGLE_HEIGHT))

val SLIDER_STYLE = SliderStyle(
        barUnfilledNormal = SLIDER_BAR_UNFILLED_NORMAL,
        barFilledNormal = SLIDER_BAR_FILLED_NORMAL,
        switchNormal = SLIDER_SWITCH_NORMAL,
        barUnfilledMouseOver = SLIDER_BAR_UNFILLED_MOUSE_OVER,
        barFilledMouseOver = SLIDER_BAR_FILLED_MOUSE_OVER,
        switchMouseOver = SLIDER_SWITCH_MOUSE_OVER,
        barUnfilledMouseDown = SLIDER_BAR_UNFILLED_MOUSE_OVER,
        barFilledMouseDown = SLIDER_BAR_FILLED_MOUSE_DOWN,
        switchMouseDown = SLIDER_SWITCH_MOUSE_DOWN,
        template = BlockTemplate(
                hSizing = GROW,
                layout = HORIZONTAL),
        sliderTemplate = BlockTemplate(
                padLeft = SWITCH_HEIGHT / 2.0f,
                padRight = SWITCH_HEIGHT / 2.0f),
        switchTemplate = BlockTemplate(
                vAlign = MIDDLE,
                hSizing = STATIC,
                vSizing = STATIC,
                width = SWITCH_HEIGHT,
                height = SWITCH_HEIGHT,
                layout = HORIZONTAL),
        barUnfilledTemplate = BlockTemplate(
                vAlign = MIDDLE,
                vSizing = STATIC,
                height = (SWITCH_HEIGHT / 3.0f) - 2.0f),
        barFilledTemplate = BlockTemplate(
                hAlign = LEFT,
                hSizing = RELATIVE,
                vAlign = MIDDLE,
                vSizing = STATIC,
                height = SWITCH_HEIGHT / 3.0f))

val WINDOW_DECORATE_BUTTON_STYLE = ButtonStyle(
        normal = SHAPE_BUTTON_NORMAL,
        textNormal = TEXT_STYLE_GLYPH,
        mouseOver = SHAPE_BUTTON_MOUSE_OVER,
        textMouseOver = TEXT_STYLE_GLYPH,
        mouseDown = SHAPE_BUTTON_MOUSE_DOWN,
        textMouseDown = TEXT_STYLE_GLYPH,
        template = BlockTemplate(
                hSizing = STATIC,
                vSizing = STATIC,
                width = LARGE_ROW_HEIGHT,
                height = SMALL_ROW_HEIGHT,
                hAlign = LEFT,
                vAlign = TOP,
                layout = HORIZONTAL))

val NORMAL_TEXT_BUTTON_STYLE = ButtonStyle(
        normal = SHAPE_BUTTON_NORMAL,
        textNormal = TEXT_STYLE_BUTTON,
        mouseOver = SHAPE_BUTTON_MOUSE_OVER,
        textMouseOver = TEXT_STYLE_BUTTON,
        mouseDown = SHAPE_BUTTON_MOUSE_DOWN,
        textMouseDown = TEXT_STYLE_BUTTON,
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

val NORMAL_GLYPH_BUTTON_STYLE = NORMAL_TEXT_BUTTON_STYLE.copy(textNormal = TEXT_STYLE_GLYPH, textMouseOver = TEXT_STYLE_GLYPH, textMouseDown = TEXT_STYLE_GLYPH)

val LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE = NORMAL_TEXT_BUTTON_STYLE.copy(
        textShapeTemplate = BlockTemplate(
                hAlign = LEFT,
                vAlign = MIDDLE))

val DISABLED_TEXT_BUTTON_STYLE = ButtonStyle(
        normal = SHAPE_BUTTON_NORMAL,
        textNormal = TEXT_STYLE_DISABLED,
        mouseOver = SHAPE_BUTTON_NORMAL,
        textMouseOver = TEXT_STYLE_DISABLED,
        mouseDown = SHAPE_BUTTON_NORMAL,
        textMouseDown = TEXT_STYLE_DISABLED,
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

val DIALOG_BUTTON_STYLE = ButtonStyle(
        normal = SHAPE_BUTTON_DIALOG,
        textNormal = TEXT_STYLE_BUTTON,
        mouseOver = SHAPE_BUTTON_MOUSE_OVER,
        textMouseOver = TEXT_STYLE_BUTTON,
        mouseDown = SHAPE_BUTTON_MOUSE_DOWN,
        textMouseDown = TEXT_STYLE_BUTTON,
        template = BlockTemplate(
                hSizing = STATIC,
                vSizing = STATIC,
                height = SMALL_ROW_HEIGHT,
                vAlign = MIDDLE,
                layout = HORIZONTAL),
        textShapeTemplate = BlockTemplate(
                hAlign = CENTER,
                vAlign = MIDDLE,
                hSizing = SHRINK,
                vSizing = SHRINK))

val POSITIVE_TEXT_BUTTON_STYLE = ButtonStyle(
        normal = SHAPE_BUTTON_POSITIVE,
        textNormal = TEXT_STYLE_BUTTON,
        mouseOver = SHAPE_BUTTON_MOUSE_OVER,
        textMouseOver = TEXT_STYLE_BUTTON,
        mouseDown = SHAPE_BUTTON_MOUSE_DOWN,
        textMouseDown = TEXT_STYLE_BUTTON,
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

val NEGATIVE_TEXT_BUTTON_STYLE = ButtonStyle(
        normal = SHAPE_BUTTON_NEGATIVE,
        textNormal = TEXT_STYLE_BUTTON,
        mouseOver = SHAPE_BUTTON_MOUSE_OVER,
        textMouseOver = TEXT_STYLE_BUTTON,
        mouseDown = SHAPE_BUTTON_MOUSE_DOWN,
        textMouseDown = TEXT_STYLE_BUTTON,
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

val LARGE_TEXT_BUTTON_STYLE = ButtonStyle(
        normal = SHAPE_BUTTON_NORMAL,
        textNormal = TEXT_STYLE_BUTTON_LARGE,
        mouseOver = SHAPE_BUTTON_MOUSE_OVER,
        textMouseOver = TEXT_STYLE_BUTTON_LARGE,
        mouseDown = SHAPE_BUTTON_MOUSE_DOWN,
        textMouseDown = TEXT_STYLE_BUTTON_LARGE,
        template = BlockTemplate(
                hSizing = SHRINK,
                vSizing = STATIC,
                height = MEDIUM_ROW_HEIGHT,
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

fun text(value: String, style: TextStyle = TEXT_STYLE_NORMAL): Text = StaticTextUtf8(value, style)

fun paragraph(value: String, style: TextStyle = TEXT_STYLE_NORMAL): Text = StaticTextParagraphUtf8(value, SMALL_SPACER_SIZE, style)

fun dynamicParagraph(value: String, limit: Int, style: TextStyle = TEXT_STYLE_NORMAL): DynamicTextParagraphReference = DynamicTextParagraphReference(value, limit, SMALL_SPACER_SIZE, style)

fun glyphStyle(size: Float, color: NPColor): TextStyle = TextStyle(cRef(size), glyphFont, cRef(color))

fun glyph(value: String, style: TextStyle = TEXT_STYLE_GLYPH): Text = StaticTextUtf8(value, style)

fun glyph(value: String, size: Float, color: NPColor): Text = StaticTextUtf8(value, glyphStyle(size, color))

fun glyph(value: ByteBuffer, style: TextStyle = TEXT_STYLE_GLYPH): Text = DynamicTextUtf8(value, style)

val TEXT_ON = text("On")
val TEXT_OFF = text("Off")

fun Block.label(text: Text, width: Float, hAlign: HorizontalAlignment = RIGHT): Block {
    return block {
        hSizing = STATIC
        this.width = width
        layout = HORIZONTAL
        block {
            this.hAlign = hAlign
            vAlign = MIDDLE
            hSizing = SHRINK
            vSizing = SHRINK
            this.text = text
            isMouseAware = false
        }
        isMouseAware = false
    }
}

fun Block.label(text: Text, shrinkGroup: ShrinkGroup, horizontalAlignment: HorizontalAlignment = RIGHT): Block {
    return block {
        hSizing = SHRINK_GROUP
        hShrinkGroup = shrinkGroup
        layout = HORIZONTAL
        block {
            hAlign = horizontalAlignment
            vAlign = MIDDLE
            hSizing = SHRINK
            vSizing = SHRINK
            this.text = text
            isMouseAware = false
        }
        isMouseAware = false
    }
}

fun Block.label(text: Text, hAlign: HorizontalAlignment = RIGHT): Block {
    val label = label(text, 0.0f, hAlign)
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

fun Block.toggle(value: ObservableMutableReference<Boolean>): Block = toggle(value, TEXT_ON, TEXT_OFF, TOGGLE_STYLE)

fun <T> Block.slider(value: ObservableMutableReference<T>, function: (Float) -> T, inverseFunction: (T) -> Float): Block = slider(value, SLIDER_STYLE, function, inverseFunction)

fun combineFunctions(f1: (Block.() -> Unit)?, f2: (Block.() -> Unit)?): (Block.() -> Unit)? {
    if (f1 == null && f2 == null) {
        return null
    }
    if (f1 == null) {
        return f2
    }
    if (f2 == null) {
        return f1
    }
    return { f1(); f2() }
}

fun <T1> combineFunctions(f1: (Block.(T1) -> Unit)?, f2: (Block.(T1) -> Unit)?): (Block.(T1) -> Unit)? {
    if (f1 == null && f2 == null) {
        return null
    }
    if (f1 == null) {
        return f2
    }
    if (f2 == null) {
        return f1
    }
    return { a -> f1(a); f2(a) }
}

fun <T1, T2> combineFunctions(f1: (Block.(T1, T2) -> Unit)?, f2: (Block.(T1, T2) -> Unit)?): (Block.(T1, T2) -> Unit)? {
    if (f1 == null && f2 == null) {
        return null
    }
    if (f1 == null) {
        return f2
    }
    if (f2 == null) {
        return f1
    }
    return { a, b -> f1(a, b); f2(a, b) }
}

fun <T1, T2, T3> combineFunctions(f1: (Block.(T1, T2, T3) -> Unit)?, f2: (Block.(T1, T2, T3) -> Unit)?): (Block.(T1, T2, T3) -> Unit)? {
    if (f1 == null && f2 == null) {
        return null
    }
    if (f1 == null) {
        return f2
    }
    if (f2 == null) {
        return f1
    }
    return { a, b, c -> f1(a, b, c); f2(a, b, c) }
}

fun <T1, T2, T3, T4> combineFunctions(f1: (Block.(T1, T2, T3, T4) -> Unit)?, f2: (Block.(T1, T2, T3, T4) -> Unit)?): (Block.(T1, T2, T3, T4) -> Unit)? {
    if (f1 == null && f2 == null) {
        return null
    }
    if (f1 == null) {
        return f2
    }
    if (f2 == null) {
        return f1
    }
    return { a, b, c, d -> f1(a, b, c, d); f2(a, b, c, d) }
}

fun Block.supplantEvents(other: Block): Block {
    other.isMouseAware = false
    onMouseOver = combineFunctions(onMouseOver, other.onMouseOver)
    onMouseOut = combineFunctions(onMouseOut, other.onMouseOut)
    onMouseDown = combineFunctions(onMouseDown, other.onMouseDown)
    onMouseUp = combineFunctions(onMouseUp, other.onMouseUp)
    onMouseRelease = combineFunctions(onMouseRelease, other.onMouseRelease)
    onMouseDownOverOther = combineFunctions(onMouseDownOverOther, other.onMouseDownOverOther)
    onMouseClick = combineFunctions(onMouseClick, other.onMouseClick)
    onMouseDrag = combineFunctions(onMouseDrag, other.onMouseDrag)
    onScroll = combineFunctions(onScroll, other.onScroll)
    onTick = combineFunctions(onTick, other.onTick)
    return this
}

fun Block.hSpacer(space: Float): Block {
    return block {
        hSizing = STATIC
        width = space
        layout = HORIZONTAL
        isMouseAware = false
    }
}

fun Block.vSpacer(space: Float): Block {
    return block {
        vSizing = STATIC
        height = space
        layout = VERTICAL
        isMouseAware = false
    }
}

fun Block.hDivider() {
    block {
        hSizing = STATIC
        width = SMALL_SPACER_SIZE
        height = -2.0f * SMALL_SPACER_SIZE
        yOffset = SMALL_SPACER_SIZE
        layout = HORIZONTAL
        isMouseAware = false
        block {
            hSizing = STATIC
            width = 1.0f
            shape = DIVIDER_DARK
            layout = HORIZONTAL
        }
        block {
            hSizing = STATIC
            width = 1.0f
            shape = DIVIDER_LIGHT
            layout = HORIZONTAL
        }
    }
}

fun Block.vToggleRow(value: ObservableMutableReference<Boolean>, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
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

fun Block.vBiomeDropdownRow(editModeOn: ObservableMutableReference<Boolean>, currentBrushValue: ObservableMutableReference<Byte>, menuLayer: Block, color: NPColor, values: ObservableMutableList<Text>, selected: ObservableMutableReference<Int>, index: Int, height: Float, shrinkGroup: ShrinkGroup, gap: Float): Block {
    return block {
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        var editIconBlock = NO_BLOCK
        block {
            hSizing = SHRINK_GROUP
            hShrinkGroup = shrinkGroup
            layout = HORIZONTAL
            block {
                layout = HORIZONTAL
                hAlign = RIGHT
                vAlign = MIDDLE
                hSizing = SHRINK
                vSizing = SHRINK
                editIconBlock = block {
                    layout = HORIZONTAL
                    vAlign = MIDDLE
                    hSizing = SHRINK
                    vSizing = SHRINK
                    text = glyph(GLYPH_BRUSH, 18.0f, COLOR_ACTIVE_HIGHLIGHT)
                    isVisible = false
                    editModeOn.addListener { old, new ->
                        if (old != new) {
                            isVisible = currentBrushValue.value.toInt() == index + 1 && new
                        }
                    }
                }
                block {
                    layout = HORIZONTAL
                    vAlign = MIDDLE
                    hSizing = SHRINK
                    vSizing = SHRINK
                    text = glyph(GLYPH_CIRCLE, 20.0f, color)
                }
                isMouseAware = false
            }
        }.onMouseClick { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && editModeOn.value) {
                currentBrushValue.value = (index + 1).toByte()
            }
        }
        currentBrushValue.addListener { old, new ->
            if (old != new) {
                editIconBlock.isVisible = new.toInt() == index + 1 && editModeOn.value
            }
        }
        hSpacer(gap)
        val textRef = if (values.size > selected.value) {
            StaticTextReference(values[selected.value])
        } else {
            StaticTextReference()
        }
        block {
            layout = HORIZONTAL
            hSizing = GROW
            var dropdownList: DropdownList? = null
            dropdown(textRef, menuLayer, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT, TEXT_STYLE_BUTTON, COLOR_DISABLED_CLICKABLE) {
                dropdownList = this
                values.forEachIndexed { i, value ->
                    menuItem(value, isDropdown = true) {
                        selected.value = i
                    }
                }
            }.with {
                vAlign = VerticalAlignment.MIDDLE
            }
            values.addListener { event ->
                if (event.changed) {
                    dropdownList?.clear()
                    values.forEachIndexed { i, value ->
                        dropdownList?.menuItem(value, isDropdown = true) {
                            selected.value = i
                        }
                    }
                    if (selected.value < values.size) {
                        textRef.reference.value = values[selected.value]
                    } else {
                        textRef.reference.value = if (values.size > 0) {
                            values[0]
                        } else {
                            NO_TEXT
                        }
                    }
                }
            }
        }
        selected.addListener { old, new ->
            if (old != new) {
                textRef.reference.value = if (values.size > new) {
                    values[new]
                } else {
                    NO_TEXT
                }
            }
        }
    }
}

private fun <T> Block.inputBox(reference: ObservableMutableReference<T>, sizeLimit: Int, converter: (String) -> T, keyboardHandlerBuilder: (Caret, ShapeCursor, () -> Unit) -> KeyboardHandler, textStyle: TextStyle, textColorActive: NPColor, ui: UserInterface, uiLayout: UiLayout): Block {
    val textValue = DynamicTextReference(reference.value.toString(), sizeLimit, textStyle)
    val textStyleActive = TextStyle(textStyle.size, textStyle.font, cRef(textColorActive))
    val caret = uiLayout.createCaret(textValue)
    textValue.reference.addListener { _, _ ->
        root.movedOrResized = true
    }
    reference.addListener { old, new ->
        if (new != old) {
            textValue.reference.value = new.toString()
            caret.position = textValue.reference.value.length
        }
    }
    val cursorShape = SHAPE_CURSOR(caret)
    var mouseOver = false
    var isActive = false
    return block {
        val textBox = this
        vAlign = MIDDLE
        hSizing = GROW
        vSizing = STATIC
        this.height = SMALL_ROW_HEIGHT + 2.0f
        layout = HORIZONTAL
        var cursorBlock = NO_BLOCK
        var textBlock = NO_BLOCK
        var selectRefBlock = NO_BLOCK
        block {
            xOffset = 4.0f
            width = -2.0f
            vSizing = STATIC
            this.height = SMALL_ROW_HEIGHT + 2.0f
            textBlock = block {
                layout = HORIZONTAL
                hAlign = LEFT
                hTruncate = TRUNCATE_RIGHT
                vAlign = MIDDLE
                hSizing = SHRINK
                vSizing = SHRINK
                isMouseAware = false
                cursorBlock = block {
                    layout = ABSOLUTE
                    hAlign = LEFT
                    vAlign = MIDDLE
                    hSizing = STATIC
                    vSizing = STATIC
                    width = 1.0f
                    this.height = textStyle.size.value
                    shape = NO_SHAPE
                    isMouseAware = false
                    canOverflow = true
                }
                selectRefBlock = block {
                    hSizing = SHRINK
                    vSizing = SHRINK
                    text = caret.dynamicText.text
                    isMouseAware = false
                    canOverflow = true
                    overflowCount = 2
                }
            }
        }
        val caretText = caret.dynamicText.text
        isMouseAware = false
        shape = SHAPE_TEXT_BOX_BACKGROUND_NORMAL
        var keyboardHandler: KeyboardHandler? = null
        val completeFun = {
            isActive = false
            textBox.shape = if (mouseOver) SHAPE_TEXT_BOX_BACKGROUND_MOUSE_OVER else SHAPE_TEXT_BOX_BACKGROUND_NORMAL
            caretText.style = if (mouseOver) textStyleActive else textStyle
            cursorBlock.shape = NO_SHAPE
            textBlock.hTruncate = TRUNCATE_RIGHT
            try {
                reference.value = converter(caret.dynamicText.reference.value)
                textValue.reference.value = reference.value.toString()
            } catch (ignore: Exception) {
                textValue.reference.value = reference.value.toString()
            }
            caret.position = textValue.reference.value.length
            if (ui.keyboardHandler === keyboardHandler) {
                ui.keyboardHandler = null
            }
        }
        keyboardHandler = keyboardHandlerBuilder(caret, cursorShape, completeFun)
        onMouseOver {
            mouseOver = true
            if (!isActive) {
                textBox.shape = SHAPE_TEXT_BOX_BACKGROUND_MOUSE_OVER
                caretText.style = textStyleActive
                root.movedOrResized = true
            }
        }
        onMouseOut {
            mouseOver = false
            if (!isActive) {
                textBox.shape = SHAPE_TEXT_BOX_BACKGROUND_NORMAL
                caretText.style = textStyle
                root.movedOrResized = true
            }
        }
        var isActivating = false
        onMouseDown { button, x, _, mods ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (isActive) {
                    if (mods and GLFW.GLFW_MOD_SHIFT != 0) {
                        caret.selection = caret.getPosition(x - selectRefBlock.x) - caret.position
                    } else {
                        caret.position = caret.getPosition(x - selectRefBlock.x)
                        caret.selection = 0
                        cursorShape.timeOffset = System.currentTimeMillis()
                    }
                } else {
                    isActive = true
                    isActivating = true
                    textBox.shape = SHAPE_TEXT_BOX_BACKGROUND_ACTIVE
                    caretText.style = textStyleActive
                    cursorShape.timeOffset = System.currentTimeMillis()
                    cursorBlock.shape = cursorShape
                    caret.position = 0
                    caret.selection = caret.dynamicText.reference.value.length
                    textBlock.hTruncate = TRUNCATE_LEFT
                    ui.keyboardHandler = keyboardHandler
                }
                root.movedOrResized = true
            }
        }
        onMouseUp { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActivating && isActive) {
                isActivating = false
                root.movedOrResized = true
            }
        }
        onMouseRelease { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActivating && isActive) {
                isActivating = false
                root.movedOrResized = true
            }
        }
        onMouseDrag { button, x, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive && !isActivating) {
                caret.selection = caret.getPosition(x - selectRefBlock.x) - caret.position
                root.movedOrResized = true
            }
        }
        onMouseDownOverOther { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive) {
                completeFun()
                root.movedOrResized = true
            }
        }
    }
}

private fun Block.longInputBox(reference: ObservableMutableReference<Long>, textStyle: TextStyle, textColorActive: NPColor, ui: UserInterface, uiLayout: UiLayout): Block {
    return inputBox(reference, 18, { BigDecimal(it).toLong() }, { caret, cursorShape, completeFun -> integerTextInputKeyboardHandler(ui, caret, cursorShape, completeFun) }, textStyle, textColorActive, ui, uiLayout)
}

fun Block.vLongInputRow(reference: ObservableMutableReference<Long>, height: Float, label: Text, textStyle: TextStyle, textColorActive: NPColor, shrinkGroup: ShrinkGroup, gap: Float, ui: UserInterface, uiLayout: UiLayout, buttons: Block.() -> Unit = {}): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            val inputBox = longInputBox(reference, textStyle, textColorActive, ui, uiLayout)
            buttons()
            row.supplantEvents(inputBox)
            isMouseAware = true
            isFallThrough = true
        }
    }
}

private fun Block.floatInputBox(reference: ObservableMutableReference<Float>, textStyle: TextStyle, textColorActive: NPColor, ui: UserInterface, uiLayout: UiLayout): Block {
    return inputBox(reference, 18, { BigDecimal(it).toFloat() }, { caret, cursorShape, completeFun -> decimalTextInputKeyboardHandler(ui, caret, cursorShape, completeFun) }, textStyle, textColorActive, ui, uiLayout)
}

fun Block.vFloatInputRow(reference: ObservableMutableReference<Float>, height: Float, label: Text, textStyle: TextStyle, textColorActive: NPColor, shrinkGroup: ShrinkGroup, gap: Float, ui: UserInterface, uiLayout: UiLayout, buttons: Block.() -> Unit = {}): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            val inputBox = floatInputBox(reference, textStyle, textColorActive, ui, uiLayout)
            buttons()
            row.supplantEvents(inputBox)
            isMouseAware = true
            isFallThrough = true
        }
    }
}

private fun Block.textInputBox(reference: ObservableMutableReference<String>, sizeLimit: Int, textStyle: TextStyle, textColorActive: NPColor, ui: UserInterface, uiLayout: UiLayout): Block {
    return inputBox(reference, sizeLimit, { it }, { caret, cursorShape, completeFun -> textInputKeyboardHandler(ui, caret, cursorShape, completeFun) }, textStyle, textColorActive, ui, uiLayout)
}

fun Block.vTextInputRow(reference: ObservableMutableReference<String>, sizeLimit: Int, height: Float, label: Text, textStyle: TextStyle, textColorActive: NPColor, shrinkGroup: ShrinkGroup, gap: Float, ui: UserInterface, uiLayout: UiLayout, buttons: Block.() -> Unit = {}): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            val inputBox = textInputBox(reference, sizeLimit, textStyle, textColorActive, ui, uiLayout)
            buttons()
            row.supplantEvents(inputBox)
            isMouseAware = true
            isFallThrough = true
        }
    }
}

fun Block.vDropdownRow(selected: ObservableMutableReference<Int>, values: List<Text>, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, menuLayer: Block): Block {
    return block {
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            val textRef = StaticTextReference(values[selected.value])
            dropdown(textRef, menuLayer, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT, TEXT_STYLE_BUTTON, COLOR_DISABLED_CLICKABLE) {
                values.forEachIndexed { i, value ->
                    menuItem(value, isDropdown = true) {
                        selected.value = i
                    }
                }
            }.with {
                vAlign = VerticalAlignment.MIDDLE
            }
            selected.addListener { old, new ->
                if (old != new) {
                    textRef.reference.value = values[new]
                }
            }
        }
    }
}


fun Block.vFolderRow(folder: DynamicTextReference, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            block {
                hSizing = GROW
                layout = HORIZONTAL
                block {
                    hAlign = LEFT
                    vAlign = MIDDLE
                    hSizing = SHRINK
                    vSizing = SHRINK
                    text = folder.text
                    isMouseAware = false
                }
                isMouseAware = false
            }
            hSpacer(MEDIUM_SPACER_SIZE)
            val button = button(text("Select folder"), NORMAL_TEXT_BUTTON_STYLE) {
                folder.reference.value = selectFolder(dialogLayer, useDialogLayer, ui, File(folder.reference.value)).canonicalPath
            }
            row.supplantEvents(button)
            isMouseAware = false
        }
    }
}

fun Block.vFileRow(file: DynamicTextReference, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface, vararg extensions: String): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            block {
                hSizing = GROW
                layout = HORIZONTAL
                block {
                    hAlign = LEFT
                    vAlign = MIDDLE
                    hSizing = SHRINK
                    vSizing = SHRINK
                    text = file.text
                    isMouseAware = false
                }
                isMouseAware = false
            }
            hSpacer(MEDIUM_SPACER_SIZE)
            val button = button(text("Select file"), NORMAL_TEXT_BUTTON_STYLE) {
                file.reference.value = selectFile(dialogLayer, useDialogLayer, ui, File(file.reference.value).parentFile ?: preferences.projectDir, *extensions) { it }?.canonicalPath ?: file.reference.value
            }
            row.supplantEvents(button)
            isMouseAware = false
        }
        onDrop { strings ->
            if (strings.isNotEmpty()) {
                file.reference.value = strings.first()
            }
        }
    }
}

fun Block.vSaveFileRow(file: DynamicTextReference, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface, vararg extensions: String, callback: (File) -> Unit = {}): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            block {
                hSizing = GROW
                layout = HORIZONTAL
                block {
                    layout = HORIZONTAL
                    hAlign = LEFT
                    hTruncate = TRUNCATE_LEFT
                    vAlign = MIDDLE
                    hSizing = SHRINK
                    vSizing = SHRINK
                    text = file.text
                    isMouseAware = false
                }
                isMouseAware = false
            }
            hSpacer(MEDIUM_SPACER_SIZE)
            val button = button(text("Select file"), NORMAL_TEXT_BUTTON_STYLE) {
                saveFile(dialogLayer, useDialogLayer, ui, File(file.reference.value).parentFile ?: preferences.projectDir, *extensions) { callbackFile ->
                    file.reference.value = callbackFile?.canonicalPath ?: file.reference.value
                    if (callbackFile != null && ((!callbackFile.exists() && callbackFile.parentFile.isDirectory && callbackFile.parentFile.canWrite()) || callbackFile.canWrite())) {
                        callback(callbackFile)
                    }
                    callbackFile
                }
            }
            row.supplantEvents(button)
            isMouseAware = false
        }
        onDrop { strings ->
            if (strings.isNotEmpty()) {
                file.reference.value = strings.first()
            }
        }
    }
}

fun Block.vSaveFileRowWithToggle(file: DynamicTextReference, toggleValue: ObservableMutableReference<Boolean>, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface, vararg extensions: String): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            block {
                hSizing = GROW
                layout = HORIZONTAL
                block {
                    layout = HORIZONTAL
                    hAlign = LEFT
                    hTruncate = TRUNCATE_LEFT
                    vAlign = MIDDLE
                    hSizing = SHRINK
                    vSizing = SHRINK
                    text = file.text
                    isMouseAware = false
                }
                isMouseAware = false
            }
            hSpacer(MEDIUM_SPACER_SIZE)
            val button = button(text("Select file"), NORMAL_TEXT_BUTTON_STYLE) {
                saveFile(dialogLayer, useDialogLayer, ui, File(file.reference.value).parentFile ?: preferences.projectDir, *extensions) { callbackFile ->
                    file.reference.value = callbackFile?.canonicalPath ?: file.reference.value
                    if (callbackFile != null && ((!callbackFile.exists() && callbackFile.parentFile.isDirectory && callbackFile.parentFile.canWrite()) || callbackFile.canWrite())) {
                        toggleValue.value = true
                    }
                    callbackFile
                }
            }
            row.supplantEvents(button)
            isMouseAware = false
        }
        hSpacer(gap)
        block {
            layout = HORIZONTAL
            hSizing = SHRINK
            toggle(toggleValue)
        }
        onDrop { strings ->
            if (strings.isNotEmpty()) {
                file.reference.value = strings.first()
            }
        }
    }
}

fun Block.vFileRowWithToggle(file: DynamicTextReference, toggleValue: ObservableMutableReference<Boolean>, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface, vararg extensions: String): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            block {
                hSizing = GROW
                layout = HORIZONTAL
                block {
                    hAlign = LEFT
                    vAlign = MIDDLE
                    hSizing = SHRINK
                    vSizing = SHRINK
                    text = file.text
                    isMouseAware = false
                }
                isMouseAware = false
            }
            hSpacer(MEDIUM_SPACER_SIZE)
            val button = button(text("Select file"), NORMAL_TEXT_BUTTON_STYLE) {
                file.reference.value = selectFile(dialogLayer, useDialogLayer, ui, File(file.reference.value).parentFile ?: preferences.projectDir, *extensions) { it }?.canonicalPath ?: file.reference.value
            }
            row.supplantEvents(button)
            isMouseAware = false
        }
        hSpacer(gap)
        block {
            layout = HORIZONTAL
            hSizing = SHRINK
            toggle(toggleValue)
        }
        onDrop { strings ->
            if (strings.isNotEmpty()) {
                file.reference.value = strings.first()
            }
        }
    }
}

fun Block.hToggleRow(value: ObservableMutableReference<Boolean>, label: Text, gap: Float): Block {
    return block {
        val row = this
        hSizing = SHRINK
        layout = HORIZONTAL
        label(label)
        hSpacer(gap)
        block {
            hSizing = SHRINK
            layout = HORIZONTAL
            val toggle = toggle(value)
            row.supplantEvents(toggle)
            isMouseAware = false
        }
    }
}

fun <T> Block.vSliderRow(value: ObservableMutableReference<T>, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, function: (Float) -> T, inverseFunction: (T) -> Float): Block {
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            val slider = slider(value, function, inverseFunction)
            row.supplantEvents(slider)
            isMouseAware = false
        }
    }
}

fun <T> Block.vSliderWithValueRow(value: ObservableMutableReference<T>, valueSize: Int, textStyle: TextStyle, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, function: (Float) -> T, inverseFunction: (T) -> Float): Block {
    val dynamicText = DynamicTextReference(value.value.toString(), valueSize, textStyle)
    value.addListener { _, new ->
        dynamicText.reference.value = new.toString()
    }
    return block {
        val row = this
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        label(label, shrinkGroup)
        hSpacer(gap)
        block {
            hSizing = GROW
            layout = HORIZONTAL
            block {
                hSizing = STATIC
                width = Math.ceil(textStyle.size.value * valueSize * 0.5).toFloat()
                layout = HORIZONTAL
                isMouseAware = false
                label(dynamicText.text, LEFT)
            }
            block {
                hSizing = GROW
                layout = HORIZONTAL
                isMouseAware = false
                val slider = slider(value, function, inverseFunction)
                row.supplantEvents(slider)
            }
            isMouseAware = false
        }
    }
}

fun <T> Block.hSliderRow(value: ObservableMutableReference<T>, width: Float, label: Text, gap: Float, function: (Float) -> T, inverseFunction: (T) -> Float): Block {
    return block {
        val row = this
        hSizing = SHRINK
        layout = HORIZONTAL
        label(label)
        hSpacer(gap)
        block {
            hSizing = STATIC
            this.width = width
            layout = HORIZONTAL
            val toggle = slider(value, function, inverseFunction)
            row.supplantEvents(toggle)
            isMouseAware = false
        }
    }
}

fun Block.vLabelRow(height: Float, label: Text): Block {
    return block {
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        block {
            label(label)
        }
    }
}

fun Block.vLabelWithButtonRow(height: Float, label: Text, buttonLabel: Text, padding: Float, buttonStyle: ButtonStyle, onClick: () -> Unit): Block {
    return block {
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        block {
            hSpacer(padding)
            label(label)
            block {
                layout = HORIZONTAL
                hSizing = GROW
            }
            button(buttonLabel, buttonStyle, onClick)
        }
    }
}

fun Block.vExpandableButton(height: Float, openOrClosed: Text, openOrClosedWidth: Float, label: Text, style: ButtonStyle, onClick: () -> Unit = {}): Block {
    return block {
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        block {
            hSizing = Sizing.RELATIVE
            width = 10000.0f
            hAlign = LEFT
            val button = button({
                block {
                    layout = HORIZONTAL
                    vSizing = SHRINK
                    hSizing = STATIC
                    hAlign = LEFT
                    vAlign = MIDDLE
                    width = openOrClosedWidth
                    block {
                        vSizing = SHRINK
                        hSizing = SHRINK
                        hAlign = CENTER
                        vAlign = MIDDLE
                        text = openOrClosed
                    }
                }
                block {
                    layout = HORIZONTAL
                    vSizing = SHRINK
                    hSizing = SHRINK
                    hAlign = LEFT
                    vAlign = MIDDLE
                    text = label
                }
            }, style, onClick)
            button.hSizing = Sizing.RELATIVE
            button.width = 10000.0f
        }
    }
}

fun Block.vExpandPanel(panelName: String, scroller: Reference<Block>? = null, expanded: ObservableMutableReference<Boolean> = ref(false), panelBuilder: Block.() -> Unit = {}): Block {
    val panelNameText = text(panelName, LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE.textNormal)
    val openText = text("-", LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE.textNormal)
    val closedText = text("+", LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE.textNormal)
    val isOpenText = StaticTextReference(if (expanded.value) openText else closedText)
    val isExpanded = expanded.value
    return block {
        val expanderBlock = this
        vSizing = SHRINK
        layout = VERTICAL
        shape = NO_SHAPE
        vExpandableButton(LARGE_ROW_HEIGHT, isOpenText, 12.0f, panelNameText, LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE) {
            expanded.value = !expanded.value
        }
        val panelBlock = block {
            hSizing = RELATIVE
            vSizing = Sizing.SHRINK
            layout = Layout.VERTICAL
            panelBuilder()
        }
        expanded.addListener { _, new ->
            if (new) {
                isOpenText.reference.value = openText
                handleExpandStateChange(panelBlock, scroller, true)
            } else {
                isOpenText.reference.value = closedText
                handleExpandStateChange(panelBlock, scroller, false)
            }
        }
        expanded.value = isExpanded
    }
}

fun Block.vExpandPanel(panelName: ObservableMutableReference<String>, scroller: Reference<Block>? = null, expanded: ObservableMutableReference<Boolean> = ref(false), panelBuilder: Block.() -> Unit = {}): Block {
    val panelNameText = StaticTextReference(text(panelName.value, LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE.textNormal))
    val openText = text("-", LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE.textNormal)
    val closedText = text("+", LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE.textNormal)
    panelName.addListener { old, new ->
        if (old != new) {
            panelNameText.reference.value = text(new, LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE.textNormal)
        }
    }
    val isOpenText = StaticTextReference(if (expanded.value) openText else closedText)
    val isExpanded = expanded.value
    return block {
        vSizing = SHRINK
        layout = VERTICAL
        shape = NO_SHAPE
        vExpandableButton(LARGE_ROW_HEIGHT, isOpenText, 12.0f, panelNameText, LEFT_ALIGN_NORMAL_TEXT_BUTTON_STYLE) {
            expanded.value = !expanded.value
        }
        val panelBlock = block {
            hSizing = RELATIVE
            vSizing = Sizing.SHRINK
            layout = Layout.VERTICAL
            panelBuilder()
        }
        expanded.addListener { _, new ->
            if (new) {
                isOpenText.reference.value = openText
                handleExpandStateChange(panelBlock, scroller, true)
            } else {
                isOpenText.reference.value = closedText
                handleExpandStateChange(panelBlock, scroller, false)
            }
        }
        expanded.value = isExpanded
    }
}

private fun handleExpandStateChange(panelBlock: Block, scroller: Reference<Block>?, state: Boolean) {
    if (scroller != null) {
        doOnMainThread {
            panelBlock.movedOrResized = true
            panelBlock.isVisible = state
            panelBlock.clearPositionAndSize()
            clearPositionAndSizeBetween(panelBlock, scroller.value)
        }
    } else {
        panelBlock.isVisible = state
    }
}

private fun clearPositionAndSizeBetween(childBlock: Block, ancestorBlock: Block) {
    val parentBlock = childBlock.parent
    if (parentBlock != childBlock && parentBlock != childBlock.root) {
        parentBlock.clearPositionAndSize()
        if (parentBlock != ancestorBlock) {
            clearPositionAndSizeBetween(parentBlock, ancestorBlock)
        }
    }
}

fun Block.vButtonRow(height: Float, horizontalAlignment: HorizontalAlignment = CENTER, verticalAlignment: VerticalAlignment = MIDDLE, buttons: Block.() -> Unit): Block {
    return block {
        vSizing = STATIC
        this.height = height
        layout = VERTICAL
        block {
            hSizing = SHRINK
            vSizing = SHRINK
            hAlign = horizontalAlignment
            vAlign = verticalAlignment
            buttons()
        }
    }
}

fun Block.hButtonRow(buttons: Block.() -> Unit): Block {
    return block {
        hSizing = SHRINK
        layout = HORIZONTAL
        block {
            hSizing = SHRINK
            vSizing = SHRINK
            hAlign = CENTER
            vAlign = MIDDLE
            buttons()
        }
    }
}

fun Block.icon(imageRef: Int, imageWidth: Float, imageHeight: Float, layoutWidth: Float, layoutHeight: Float): Block {
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
            shape = ShapeRectangle(FillImage(imageRef), NO_STROKE)
            isMouseAware = false
        }
    }
}

fun Block.icon(imageRef: Int, imageSize: Float, layoutSize: Float): Block = icon(imageRef, imageSize, imageSize, layoutSize, layoutSize)

fun Block.meshViewport3D(meshViewport: MeshViewport3D, ui: UserInterface): Block {
    return block {
        layout = ABSOLUTE
        shape = ShapeMeshViewport3D(meshViewport)
        onMouseDown { button, x, y, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                ui.keyboardHandler = meshViewport.keyboardHandler
            }
            meshViewport.onMouseDown(button, x, y)
        }
        onMouseRelease { button, x, y, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                ui.keyboardHandler = null
            }
            meshViewport.clearKeysPressed()
            meshViewport.onMouseRelease(button, x, y)
        }
        onMouseDrag { _, x, y, _ ->
            meshViewport.onMouseDrag(x, y)
        }
        onScroll { _, y ->
            meshViewport.onScroll(y)
        }
    }
}

fun Block.resizeArea(direction: ShapeTriangle.Direction): Block {
    val hAlign: HorizontalAlignment
    val xOffset = if (direction == ShapeTriangle.Direction.SOUTH_EAST) {
        hAlign = RIGHT
        4.0f
    } else {
        hAlign = LEFT
        0.0f
    }
    return block {
        hSizing = STATIC
        vSizing = STATIC
        width = SMALL_ROW_HEIGHT
        height = SMALL_ROW_HEIGHT
        layout = ABSOLUTE
        this.hAlign = hAlign
        vAlign = BOTTOM
        val grabber = button(NO_TEXT, NORMAL_TEXT_BUTTON_STYLE {
            copy(
                    template = BlockTemplate(
                            xOffset = xOffset,
                            yOffset = 4.0f,
                            width = -4.0f,
                            height = -4.0f),
                    mouseOver = ShapeTriangle(mouseOver.fill, mouseOver.stroke, direction),
                    mouseDown = ShapeTriangle(mouseDown.fill, mouseDown.stroke, direction))
        })
        supplantEvents(grabber)
    }
}

interface DisableSet {

    fun hide()

    fun disable()

    fun enable()
}

fun disablePair(enabled: Block, disabled: Block, shouldDisable: () -> Boolean = { false }) : DisableSet {

    return object: DisableSet {

        override fun hide() {
            enabled.isVisible = false
            disabled.isVisible = false
        }

        override fun disable() {
            enabled.isVisible = false
            disabled.isVisible = true
        }

        override fun enable() {
            if (shouldDisable()) {
                disable()
            } else {
                disabled.isVisible = false
                enabled.isVisible = true
            }
        }
    }
}

fun disableSet(selector: () -> Int, vararg sets: DisableSet) : DisableSet {

    return object: DisableSet {

        override fun hide() = sets.forEach(DisableSet::hide)

        override fun disable() {
            val selected = selector()
            sets.forEachIndexed { i, it ->
                if (i == selected) {
                    it.disable()
                } else {
                    it.hide()
                }
            }
        }

        override fun enable() {
            val selected = selector()
            sets.forEachIndexed { i, it ->
                if (i == selected) {
                    it.enable()
                } else {
                    it.hide()
                }
            }
        }
    }
}

fun Block.disableButton(label: String, disableCondition: () -> Boolean = { false }, onClick: () -> Unit = {}): DisableSet {
    val enabledButton = button(text(label), NORMAL_TEXT_BUTTON_STYLE, onClick)
    enabledButton.isMouseAware = true
    enabledButton.isVisible = true
    val disabledButton = button(text(label), DISABLED_TEXT_BUTTON_STYLE) {}
    disabledButton.isMouseAware = false
    disabledButton.isVisible = false
    return disablePair(enabledButton, disabledButton, disableCondition)
}
