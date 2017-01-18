package com.grimfox.gec

import com.grimfox.gec.ui.FileDialogs
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.color
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.ui.widgets.toggle
import com.grimfox.gec.util.MonitoredReference
import com.grimfox.gec.util.cRef
import com.grimfox.gec.util.ref
import org.lwjgl.nanovg.NVGColor
import java.io.File
import java.nio.ByteBuffer

val textFont = ref(-1)
val glyphFont = ref(-1)

val SMALL_SPACER_SIZE = 6.0f
val MEDIUM_SPACER_SIZE = 12.0f
val LARGE_SPACER_SIZE = 24.0f
val MEGA_SPACER_SIZE = 48.0f

val SMALL_ROW_HEIGHT = 20.0f
val MEDIUM_ROW_HEIGHT = 26.0f
val LARGE_ROW_HEIGHT = 32.0f

val COLOR_DROP_SHADOW_BLACK = color(5, 5, 6, 72)
val COLOR_DROP_SHADOW_BLACK_TRANSPARENT = color(5, 5, 6, 0)

val COLOR_DROP_SHADOW_DARK_BLACK = color(5, 5, 6, 132)
val COLOR_DROP_SHADOW_DARK_BLACK_TRANSPARENT = color(5, 5, 6, 0)

val COLOR_BACKGROUND = color(45, 45, 48)
val FILL_BACKGROUND = FillColor(COLOR_BACKGROUND)
val BACKGROUND_RECT = ShapeRectangle(FILL_BACKGROUND, NO_STROKE)

val COLOR_BEVELS = color(34, 34, 35)
val COLOR_BEVELS_LIGHTER = color(38, 38, 39)

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

val COLOR_DISABLED_CLICKABLE = color(78, 78, 80)

val FILL_MENU_BACKGROUND = FillColor(COLOR_MENU_BACKGROUND)
val FILL_MENU_BORDER = FillColor(COLOR_MENU_HIGHLIGHT)
val FILL_BORDERS_AND_FRAMES = FillColor(COLOR_BORDERS_AND_FRAMES)


val SHAPE_MENU_BACKGROUND = ShapeRectangle(FILL_MENU_BACKGROUND, NO_STROKE)
val SHAPE_MENU_BORDER = ShapeRectangle(FILL_MENU_BORDER, NO_STROKE)
val SHAPE_BORDER_AND_FRAME_RECTANGLE = ShapeRectangle(FILL_BORDERS_AND_FRAMES, NO_STROKE)

val FONT_SIZE_15 = cRef(15.0f)
val FONT_SIZE_16 = cRef(16.0f)
val FONT_SIZE_22 = cRef(22.0f)

val TEXT_STYLE_NORMAL = TextStyle(FONT_SIZE_15, textFont, cRef(COLOR_NORMAL_TEXT))
val TEXT_STYLE_BUTTON = TextStyle(FONT_SIZE_15, textFont, cRef(COLOR_BUTTON_TEXT))
val TEXT_STYLE_GLYPH = TextStyle(FONT_SIZE_16, glyphFont, cRef(COLOR_BUTTON_TEXT))

val TEXT_STYLE_BUTTON_LARGE = TextStyle(FONT_SIZE_22, textFont, cRef(COLOR_BUTTON_TEXT))


val DIVIDER_DARK = ShapeRectangle(FillColor(COLOR_BEVELS), NO_STROKE)
val DIVIDER_LIGHT = ShapeRectangle(FillColor(COLOR_CLICK_ITEMS_DARKER), NO_STROKE)

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

fun text(value: String, style: TextStyle = TEXT_STYLE_NORMAL): Text {
    return StaticTextUtf8(value, style)
}

fun paragraph(value: String, style: TextStyle = TEXT_STYLE_NORMAL): Text {
    return StaticTextParagraphUtf8(value, SMALL_SPACER_SIZE, style)
}

fun dynamicParagraph(value: String, limit: Int, style: TextStyle = TEXT_STYLE_NORMAL): DynamicTextParagraphReference {
    return DynamicTextParagraphReference(value, limit, SMALL_SPACER_SIZE, style)
}

fun glyphStyle(size: Float, color: NVGColor): TextStyle {
    return TextStyle(cRef(size), glyphFont, cRef(color))
}

fun glyph(value: String, style: TextStyle = TEXT_STYLE_GLYPH): Text {
    return StaticTextUtf8(value, style)
}

fun glyph(value: String, size: Float, color: NVGColor): Text {
    return StaticTextUtf8(value, glyphStyle(size, color))
}

fun glyph(value: ByteBuffer, style: TextStyle = TEXT_STYLE_GLYPH): Text {
    return DynamicTextUtf8(value, style)
}

val TEXT_ON = text("On")
val TEXT_OFF = text("Off")

fun Block.label(text: Text, width: Float): Block {
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

fun Block.label(text: Text, shrinkGroup: ShrinkGroup): Block {
    return block {
        hSizing = SHRINK_GROUP
        hShrinkGroup = shrinkGroup
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
    val label = label(text, 0.0f)
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

fun Block.toggle(value: MonitoredReference<Boolean>): Block {
    return toggle(value, TEXT_ON, TEXT_OFF, TOGGLE_STYLE)
}

fun <T> Block.slider(value: MonitoredReference<T>, function: (Float) -> T, inverseFunction: (T) -> Float): Block {
    return slider(value, SLIDER_STYLE, function, inverseFunction)
}

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

fun combineFunctions(f1: (Block.(Int, Int, Int) -> Unit)?, f2: (Block.(Int, Int, Int) -> Unit)?): (Block.(Int, Int, Int) -> Unit)? {
    if (f1 == null && f2 == null) {
        return null
    }
    if (f1 == null) {
        return f2
    }
    if (f2 == null) {
        return f1
    }
    return { button, x, y -> f1(button, x, y); f2(button, x, y) }
}

fun Block.supplantEvents(other: Block): Block {
    other.isMouseAware = false
    onMouseOver = combineFunctions(onMouseOver, other.onMouseOver)
    onMouseOut = combineFunctions(onMouseOut, other.onMouseOut)
    onMouseDown = combineFunctions(onMouseDown, other.onMouseDown)
    onMouseUp = combineFunctions(onMouseUp, other.onMouseUp)
    onMouseRelease = combineFunctions(onMouseRelease, other.onMouseRelease)
    onMouseClick = combineFunctions(onMouseClick, other.onMouseClick)
    onMouseDrag = combineFunctions(onMouseDrag, other.onMouseDrag)
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

fun Block.vToggleRow(value: MonitoredReference<Boolean>, height: Float, label: Text, labelWidth: Float, gap: Float): Block {
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

fun Block.vToggleRow(value: MonitoredReference<Boolean>, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float): Block {
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

fun Block.vFolderRow(folder: DynamicTextReference, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, dialogLayer: Block, ui: UserInterface): Block {
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
                folder.reference.value = selectFolder(dialogLayer, ui, File(folder.reference.value)).canonicalPath
            }
            row.supplantEvents(button)
            isMouseAware = false
        }
    }
}

private fun selectFolder(dialogLayer: Block, ui: UserInterface, currentFolder: File): File {
    ui.ignoreInput = true
    dialogLayer.isVisible = true
    try {
        return selectFolderDialog(currentFolder) ?: currentFolder
    } finally {
        dialogLayer.isVisible = false
        ui.ignoreInput = false
    }
}

private fun selectFolderDialog(defaultFolder: File): File? {
    val folderName = FileDialogs.selectFolder(defaultFolder.canonicalPath)
    if (folderName != null && folderName.isNotBlank()) {
        return File(folderName)
    }
    return null
}

fun Block.hToggleRow(value: MonitoredReference<Boolean>, label: Text, gap: Float): Block {
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

fun <T> Block.vSliderRow(value: MonitoredReference<T>, height: Float, label: Text, labelWidth: Float, gap: Float, function: (Float) -> T, inverseFunction: (T) -> Float): Block {
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
            val toggle = slider(value, function, inverseFunction)
            row.supplantEvents(toggle)
            isMouseAware = false
        }
    }
}

fun <T> Block.vSliderRow(value: MonitoredReference<T>, height: Float, label: Text, shrinkGroup: ShrinkGroup, gap: Float, function: (Float) -> T, inverseFunction: (T) -> Float): Block {
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
            val toggle = slider(value, function, inverseFunction)
            row.supplantEvents(toggle)
            isMouseAware = false
        }
    }
}

fun <T> Block.hSliderRow(value: MonitoredReference<T>, width: Float, label: Text, gap: Float, function: (Float) -> T, inverseFunction: (T) -> Float): Block {
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

fun Block.vButtonRow(height: Float, buttons: Block.() -> Unit): Block {
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
            shape = ShapeRectangle(FillImageDynamic(imageRef), NO_STROKE)
            isMouseAware = false
        }
    }
}

fun Block.icon(imageRef: Int, imageSize: Float, layoutSize: Float): Block {
    return icon(imageRef, imageSize, imageSize, layoutSize, layoutSize)
}

fun Block.meshViewport3D(meshViewport: MeshViewport3D): Block {
    return block {
        layout = ABSOLUTE
        shape = ShapeMeshViewport3D(meshViewport)
        onMouseDown { button, x, y ->
            meshViewport.onMouseDown(button, x, y)
        }
        onMouseRelease { button, x, y ->
            meshViewport.onMouseRelease(button)
        }
        onMouseDrag { button, x, y ->
            meshViewport.onMouseDrag(x, y)
        }
        onScroll { x, y ->
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