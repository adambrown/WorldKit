package wk.internal.ui.widgets

import wk.internal.ui.widgets.HorizontalAlignment.LEFT
import wk.internal.ui.widgets.Layout.HORIZONTAL
import wk.internal.ui.widgets.Sizing.RELATIVE
import wk.internal.ui.widgets.Sizing.STATIC
import wk.internal.ui.widgets.VerticalAlignment.MIDDLE
import wk.api.ObservableMutableReference
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import kotlin.math.roundToInt

class SliderStyle(
        val barUnfilledNormal: Shape,
        val barFilledNormal: Shape,
        val switchNormal: Shape,
        val barUnfilledMouseOver: Shape,
        val barFilledMouseOver: Shape,
        val switchMouseOver: Shape,
        val barUnfilledMouseDown: Shape,
        val barFilledMouseDown: Shape,
        val switchMouseDown: Shape,
        val template: BlockTemplate = BlockTemplate(),
        val sliderTemplate: BlockTemplate = BlockTemplate(),
        val barUnfilledTemplate: BlockTemplate = BlockTemplate(
                vAlign = MIDDLE,
                vSizing = STATIC,
                height = 6.0f),
        val barFilledTemplate: BlockTemplate = BlockTemplate(
                hAlign = LEFT,
                hSizing = RELATIVE,
                vAlign = MIDDLE,
                vSizing = STATIC,
                height = 8.0f),
        val switchTemplate: BlockTemplate = BlockTemplate(
                hAlign = LEFT,
                vAlign = MIDDLE,
                layout = HORIZONTAL)) {

    operator inline fun invoke(builder: SliderStyle.() -> SliderStyle): SliderStyle {
        return this.builder()
    }
}

fun Block.slider(valueReference: ObservableMutableReference<Int>, valueMin: Int, valueMax: Int, style: SliderStyle): Block {
    val valueRange = valueMax - valueMin
    return slider(valueReference, style,
                  { (((it * valueRange) + valueMin).roundToInt()).coerceIn(valueMin, valueMax) },
                  { ((it - valueMin) / valueRange.toFloat()).coerceIn(0.0f, 1.0f) })
}

fun Block.slider(valueReference: ObservableMutableReference<Float>, valueMin: Float, valueMax: Float, style: SliderStyle): Block {
    val valueRange = valueMax - valueMin
    return slider(valueReference, style,
            { ((it * valueRange) + valueMin).coerceIn(valueMin, valueMax) },
            { ((it - valueMin) / valueRange).coerceIn(0.0f, 1.0f) })
}

fun <T> Block.slider(valueReference: ObservableMutableReference<T>, style: SliderStyle, valueFunction: (Float) -> T, valueFunctionInverse: (T) -> Float): Block {
    return block {
        var barUnfilled = NO_BLOCK
        var barFilled = NO_BLOCK
        var switch = NO_BLOCK
        var mouseDownOver = false
        var mouseOver = false
        isVisible = style.template.isVisible
        hAlign = style.template.hAlign
        vAlign = style.template.vAlign
        layout = style.template.layout
        xOffset = style.template.xOffset
        yOffset = style.template.yOffset
        hSizing = style.template.hSizing
        width = style.template.width
        vSizing = style.template.vSizing
        height = style.template.height
        padLeft = style.template.padLeft
        padRight = style.template.padRight
        padTop = style.template.padTop
        padBottom = style.template.padBottom
        val slider = block {
            isVisible = style.sliderTemplate.isVisible
            hAlign = style.sliderTemplate.hAlign
            vAlign = style.sliderTemplate.vAlign
            layout = style.sliderTemplate.layout
            xOffset = style.sliderTemplate.xOffset
            yOffset = style.sliderTemplate.yOffset
            hSizing = style.sliderTemplate.hSizing
            width = style.sliderTemplate.width
            vSizing = style.sliderTemplate.vSizing
            height = style.sliderTemplate.height
            padLeft = style.sliderTemplate.padLeft
            padRight = style.sliderTemplate.padRight
            padTop = style.sliderTemplate.padTop
            padBottom = style.sliderTemplate.padBottom
            barUnfilled = block {
                isVisible = style.barUnfilledTemplate.isVisible
                hAlign = style.barUnfilledTemplate.hAlign
                vAlign = style.barUnfilledTemplate.vAlign
                layout = style.barUnfilledTemplate.layout
                xOffset = style.barUnfilledTemplate.xOffset
                yOffset = style.barUnfilledTemplate.yOffset
                hSizing = style.barUnfilledTemplate.hSizing
                width = style.barUnfilledTemplate.width
                vSizing = style.barUnfilledTemplate.vSizing
                height = style.barUnfilledTemplate.height
                padLeft = style.barUnfilledTemplate.padLeft
                padRight = style.barUnfilledTemplate.padRight
                padTop = style.barUnfilledTemplate.padTop
                padBottom = style.barUnfilledTemplate.padBottom
                shape = style.barUnfilledNormal
                isMouseAware = false
            }
            barFilled = block {
                isVisible = style.barFilledTemplate.isVisible
                hAlign = style.barFilledTemplate.hAlign
                vAlign = style.barFilledTemplate.vAlign
                layout = style.barFilledTemplate.layout
                xOffset = style.barFilledTemplate.xOffset
                yOffset = style.barFilledTemplate.yOffset
                vSizing = style.barFilledTemplate.vSizing
                height = style.barFilledTemplate.height
                padLeft = style.barFilledTemplate.padLeft
                padRight = style.barFilledTemplate.padRight
                padTop = style.barFilledTemplate.padTop
                padBottom = style.barFilledTemplate.padBottom
                hSizing = RELATIVE
                width = 10000.0f * valueFunctionInverse(valueReference.value)
                shape = style.barFilledNormal
                isMouseAware = false
            }
            switch = block {
                isVisible = style.switchTemplate.isVisible
                hAlign = style.switchTemplate.hAlign
                vAlign = style.switchTemplate.vAlign
                yOffset = style.switchTemplate.yOffset
                hSizing = style.switchTemplate.hSizing
                width = style.switchTemplate.width
                vSizing = style.switchTemplate.vSizing
                height = style.switchTemplate.height
                padLeft = style.switchTemplate.padLeft
                padRight = style.switchTemplate.padRight
                padTop = style.switchTemplate.padTop
                padBottom = style.switchTemplate.padBottom
                layout = HORIZONTAL
                xOffset = -(width / 2)
                shape = style.switchNormal
                isMouseAware = false
                canOverflow = true
                overflowCount = 1
            }
            isMouseAware = false
        }
        val updateSlider: Block.(Int, Int, Int, Int) -> Unit = { button, x, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                val minX = slider.x
                val maxX = minX + slider.width
                val scale = if (minX >= x) {
                    barFilled.width = 0.0f
                    0.0f
                } else if (x >= maxX) {
                    barFilled.width = 10000.0f
                    1.0f
                } else {
                    val range = maxX - minX
                    val scale = (x - minX) / range
                    barFilled.width = 10000.0f * scale
                    scale
                }
                valueReference.value = valueFunction(scale)
            }
        }
        onMouseOver {
            mouseOver = true
            if (!mouseDownOver) {
                barUnfilled.shape = style.barUnfilledMouseOver
                barFilled.shape = style.barFilledMouseOver
                switch.shape = style.switchMouseOver
            }
        }
        onMouseOut {
            mouseOver = false
            if (!mouseDownOver) {
                barUnfilled.shape = style.barUnfilledNormal
                barFilled.shape = style.barFilledNormal
                switch.shape = style.switchNormal
            }
        }
        onMouseDown { button, x, y, mods ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                mouseDownOver = true
                barUnfilled.shape = style.barUnfilledMouseDown
                barFilled.shape = style.barFilledMouseDown
                switch.shape = style.switchMouseDown
                updateSlider(button, x, y, mods)
            }
        }
        onMouseRelease { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                barUnfilled.shape = if (mouseOver) style.barUnfilledMouseOver else style.barUnfilledNormal
                barFilled.shape = if (mouseOver) style.barFilledMouseOver else style.barFilledNormal
                switch.shape = if (mouseOver) style.switchMouseOver else style.switchNormal
            }
        }
        onMouseDrag = updateSlider
        valueReference.addListener { _, new ->
            barFilled.width = 10000.0f * valueFunctionInverse(new)
        }
    }
}