package com.grimfox.gec.ui.widgets

import com.grimfox.gec.ui.widgets.HorizontalAlignment.CENTER
import com.grimfox.gec.ui.widgets.HorizontalAlignment.LEFT
import com.grimfox.gec.ui.widgets.Layout.HORIZONTAL
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.MIDDLE
import com.grimfox.gec.util.MonitoredReference
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT

class ToggleStyle(
        val backgroundNormal: Shape,
        val switchNormalOn: Shape,
        val switchNormalOff: Shape,
        val textNormalOn: TextStyle,
        val textNormalOff: TextStyle,
        val backgroundMouseOver: Shape,
        val switchMouseOverOn: Shape,
        val switchMouseOverOff: Shape,
        val textMouseOverOn: TextStyle,
        val textMouseOverOff: TextStyle,
        val backgroundMouseDown: Shape,
        val switchMouseDownOn: Shape,
        val switchMouseDownOff: Shape,
        val textMouseDownOn: TextStyle,
        val textMouseDownOff: TextStyle,
        inset: Float = 4.0f,
        val template: BlockTemplate = BlockTemplate(
                vAlign = MIDDLE,
                vSizing = STATIC,
                height = 32.0f),
        switchSize: Float = template.height - (2.0f * inset),
        val onTemplate: BlockTemplate = BlockTemplate(),
        val onToggleTemplate: BlockTemplate = BlockTemplate(
                hAlign = LEFT,
                vAlign = MIDDLE,
                hSizing = STATIC,
                vSizing = STATIC,
                layout = HORIZONTAL,
                width = switchSize,
                height = switchSize,
                padRight = inset),
        val onTextWrapperTemplate: BlockTemplate = BlockTemplate(
                hSizing = GROW,
                layout = HORIZONTAL),
        val onTextShapeTemplate: BlockTemplate = BlockTemplate(
                hAlign = CENTER,
                vAlign = MIDDLE,
                hSizing = SHRINK,
                vSizing = SHRINK),
        val offTemplate: BlockTemplate = BlockTemplate(),
        val offToggleTemplate: BlockTemplate = BlockTemplate(
                hAlign = LEFT,
                vAlign = MIDDLE,
                hSizing = STATIC,
                vSizing = STATIC,
                layout = HORIZONTAL,
                width = switchSize,
                height = switchSize,
                padLeft = inset),
        val offTextWrapperTemplate: BlockTemplate = BlockTemplate(
                hSizing = GROW,
                layout = HORIZONTAL),
        val offTextShapeTemplate: BlockTemplate = BlockTemplate(
                hAlign = CENTER,
                vAlign = MIDDLE,
                hSizing = SHRINK,
                vSizing = SHRINK))

fun Block.toggle(toggleValue: MonitoredReference<Boolean>, textOn: Text, textOff: Text, style: ToggleStyle): Block {
    textOn.style = style.textNormalOn
    textOff.style = style.textNormalOff
    return block {
        val toggle = this
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
        shape = style.backgroundNormal
        var onToggle = NO_BLOCK
        val onBlock = block {
            isVisible = toggleValue.value
            hAlign = style.onTemplate.hAlign
            vAlign = style.onTemplate.vAlign
            layout = style.onTemplate.layout
            xOffset = style.onTemplate.xOffset
            yOffset = style.onTemplate.yOffset
            hSizing = style.onTemplate.hSizing
            width = style.onTemplate.width
            vSizing = style.onTemplate.vSizing
            height = style.onTemplate.height
            padLeft = style.onTemplate.padLeft
            padRight = style.onTemplate.padRight
            padTop = style.onTemplate.padTop
            padBottom = style.onTemplate.padBottom
            block {
                isVisible = style.onTextWrapperTemplate.isVisible
                hAlign = style.onTextWrapperTemplate.hAlign
                vAlign = style.onTextWrapperTemplate.vAlign
                layout = style.onTextWrapperTemplate.layout
                xOffset = style.onTextWrapperTemplate.xOffset
                yOffset = style.onTextWrapperTemplate.yOffset
                hSizing = style.onTextWrapperTemplate.hSizing
                width = style.onTextWrapperTemplate.width
                vSizing = style.onTextWrapperTemplate.vSizing
                height = style.onTextWrapperTemplate.height
                padLeft = style.onTextWrapperTemplate.padLeft
                padRight = style.onTextWrapperTemplate.padRight
                padTop = style.onTextWrapperTemplate.padTop
                padBottom = style.onTextWrapperTemplate.padBottom
                block {
                    isVisible = style.onTextShapeTemplate.isVisible
                    hAlign = style.onTextShapeTemplate.hAlign
                    vAlign = style.onTextShapeTemplate.vAlign
                    layout = style.onTextShapeTemplate.layout
                    xOffset = style.onTextShapeTemplate.xOffset
                    yOffset = style.onTextShapeTemplate.yOffset
                    hSizing = style.onTextShapeTemplate.hSizing
                    width = style.onTextShapeTemplate.width
                    vSizing = style.onTextShapeTemplate.vSizing
                    height = style.onTextShapeTemplate.height
                    padLeft = style.onTextShapeTemplate.padLeft
                    padRight = style.onTextShapeTemplate.padRight
                    padTop = style.onTextShapeTemplate.padTop
                    padBottom = style.onTextShapeTemplate.padBottom
                    text = textOn
                    isMouseAware = false
                }
                isMouseAware = false
            }
            onToggle = block {
                isVisible = style.onToggleTemplate.isVisible
                hAlign = style.onToggleTemplate.hAlign
                vAlign = style.onToggleTemplate.vAlign
                layout = style.onToggleTemplate.layout
                xOffset = style.onToggleTemplate.xOffset
                yOffset = style.onToggleTemplate.yOffset
                hSizing = style.onToggleTemplate.hSizing
                width = style.onToggleTemplate.width
                vSizing = style.onToggleTemplate.vSizing
                height = style.onToggleTemplate.height
                padLeft = style.onToggleTemplate.padLeft
                padRight = style.onToggleTemplate.padRight
                padTop = style.onToggleTemplate.padTop
                padBottom = style.onToggleTemplate.padBottom
                shape = style.switchNormalOn
                isMouseAware = false
            }
            isMouseAware = false
        }
        var offToggle = NO_BLOCK
        val offBlock = block {
            isVisible = !toggleValue.value
            hAlign = style.offTemplate.hAlign
            vAlign = style.offTemplate.vAlign
            layout = style.offTemplate.layout
            xOffset = style.offTemplate.xOffset
            yOffset = style.offTemplate.yOffset
            hSizing = style.offTemplate.hSizing
            width = style.offTemplate.width
            vSizing = style.offTemplate.vSizing
            height = style.offTemplate.height
            padLeft = style.offTemplate.padLeft
            padRight = style.offTemplate.padRight
            padTop = style.offTemplate.padTop
            padBottom = style.offTemplate.padBottom
            offToggle = block {
                isVisible = style.offToggleTemplate.isVisible
                hAlign = style.offToggleTemplate.hAlign
                vAlign = style.offToggleTemplate.vAlign
                layout = style.offToggleTemplate.layout
                xOffset = style.offToggleTemplate.xOffset
                yOffset = style.offToggleTemplate.yOffset
                hSizing = style.offToggleTemplate.hSizing
                width = style.offToggleTemplate.width
                vSizing = style.offToggleTemplate.vSizing
                height = style.offToggleTemplate.height
                padLeft = style.offToggleTemplate.padLeft
                padRight = style.offToggleTemplate.padRight
                padTop = style.offToggleTemplate.padTop
                padBottom = style.offToggleTemplate.padBottom
                shape = style.switchNormalOff
                isMouseAware = false
            }
            block {
                isVisible = style.offTextWrapperTemplate.isVisible
                hAlign = style.offTextWrapperTemplate.hAlign
                vAlign = style.offTextWrapperTemplate.vAlign
                layout = style.offTextWrapperTemplate.layout
                xOffset = style.offTextWrapperTemplate.xOffset
                yOffset = style.offTextWrapperTemplate.yOffset
                hSizing = style.offTextWrapperTemplate.hSizing
                width = style.offTextWrapperTemplate.width
                vSizing = style.offTextWrapperTemplate.vSizing
                height = style.offTextWrapperTemplate.height
                padLeft = style.offTextWrapperTemplate.padLeft
                padRight = style.offTextWrapperTemplate.padRight
                padTop = style.offTextWrapperTemplate.padTop
                padBottom = style.offTextWrapperTemplate.padBottom
                block {
                    isVisible = style.offTextShapeTemplate.isVisible
                    hAlign = style.offTextShapeTemplate.hAlign
                    vAlign = style.offTextShapeTemplate.vAlign
                    layout = style.offTextShapeTemplate.layout
                    xOffset = style.offTextShapeTemplate.xOffset
                    yOffset = style.offTextShapeTemplate.yOffset
                    hSizing = style.offTextShapeTemplate.hSizing
                    width = style.offTextShapeTemplate.width
                    vSizing = style.offTextShapeTemplate.vSizing
                    height = style.offTextShapeTemplate.height
                    padLeft = style.offTextShapeTemplate.padLeft
                    padRight = style.offTextShapeTemplate.padRight
                    padTop = style.offTextShapeTemplate.padTop
                    padBottom = style.offTextShapeTemplate.padBottom
                    text = textOff
                    isMouseAware = false
                }
                isMouseAware = false
            }
            isMouseAware = false
        }
        onMouseOver {
            mouseOver = true
            if (!mouseDownOver) {
                onToggle.shape = style.switchMouseOverOn
                offToggle.shape = style.switchMouseOverOff
                toggle.shape = style.backgroundMouseOver
                textOn.style = style.textMouseOverOn
                textOff.style = style.textMouseOverOff
            }
        }
        onMouseOut {
            mouseOver = false
            if (!mouseDownOver) {
                onToggle.shape = style.switchNormalOn
                offToggle.shape = style.switchNormalOff
                toggle.shape = style.backgroundNormal
                textOn.style = style.textNormalOn
                textOff.style = style.textNormalOff
            }
        }
        onMouseDown { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                mouseDownOver = true
                onToggle.shape = style.switchMouseDownOn
                offToggle.shape = style.switchMouseDownOff
                toggle.shape = style.backgroundMouseDown
                textOn.style = style.textMouseDownOn
                textOff.style = style.textMouseDownOff
            }
        }
        onMouseRelease { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                onToggle.shape = if (mouseOver) style.switchMouseOverOn else style.switchNormalOn
                offToggle.shape = if (mouseOver) style.switchMouseOverOff else style.switchNormalOff
                toggle.shape = if (mouseOver) style.backgroundMouseOver else style.backgroundNormal
                textOn.style = if (mouseOver) style.textMouseOverOn else style.textNormalOn
                textOff.style = if (mouseOver) style.textMouseOverOff else style.textNormalOff
            }
        }
        onMouseClick { button, _, _, _ ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                toggleValue.value = !toggleValue.value
                onBlock.isVisible = toggleValue.value
                offBlock.isVisible = !toggleValue.value
            }
        }
        toggleValue.listener { _, new ->
            onBlock.isVisible = new
            offBlock.isVisible = !new
        }
    }
}
