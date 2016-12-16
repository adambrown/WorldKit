package com.grimfox.gec.ui.widgets

import com.grimfox.gec.ui.Reference
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT

class ToggleStyle(
        val switchInset: Int,
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
        val textMouseDownOff: TextStyle)

fun Block.toggle(toggleValue: Reference<Boolean>, width: Int, height: Int, textOn: Text, textOff: Text, style: ToggleStyle): Block {
    textOn.style = style.textNormalOn
    textOff.style = style.textNormalOff
    return block {
        val toggle = this
        var mouseDownOver = false
        var mouseOver = false
        vAlign = VerticalAlignment.MIDDLE
        hSizing = Sizing.STATIC
        vSizing = Sizing.STATIC
        this.width = width
        this.height = height
        shape = style.backgroundNormal
        val onBlock = block {
            isVisible = toggleValue.value
            block {
                hSizing = Sizing.GROW
                layout = Layout.HORIZONTAL
                block {
                    hAlign = HorizontalAlignment.CENTER
                    vAlign = VerticalAlignment.MIDDLE
                    hSizing = Sizing.SHRINK
                    vSizing = Sizing.SHRINK
                    text = textOn
                    isMouseAware = false
                }
                isMouseAware = false
            }
            block {
                hAlign = HorizontalAlignment.LEFT
                vAlign = VerticalAlignment.MIDDLE
                hSizing = Sizing.STATIC
                vSizing = Sizing.STATIC
                layout = Layout.HORIZONTAL
                this.width = height - (2 * style.switchInset)
                this.height = height - (2 * style.switchInset)
                padRight = style.switchInset
                shape = style.switchNormalOn
                isMouseAware = false
            }
            isMouseAware = false
        }
        val offBlock = block {
            isVisible = !toggleValue.value
            block {
                hAlign = HorizontalAlignment.LEFT
                vAlign = VerticalAlignment.MIDDLE
                hSizing = Sizing.STATIC
                vSizing = Sizing.STATIC
                layout = Layout.HORIZONTAL
                this.width = height - (2 * style.switchInset)
                this.height = height - (2 * style.switchInset)
                padLeft = style.switchInset
                shape = style.switchNormalOff
                isMouseAware = false
            }
            block {
                hSizing = Sizing.GROW
                layout = Layout.HORIZONTAL
                block {
                    hAlign = HorizontalAlignment.CENTER
                    vAlign = VerticalAlignment.MIDDLE
                    hSizing = Sizing.SHRINK
                    vSizing = Sizing.SHRINK
                    text = textOff
                    isMouseAware = false
                }
                isMouseAware = false
            }
            isMouseAware = false
        }
        val onToggle = onBlock.children[1]
        val offToggle = offBlock.children[0]
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
        onMouseDown { button, x, y ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                mouseDownOver = true
                onToggle.shape = style.switchMouseDownOn
                offToggle.shape = style.switchMouseDownOff
                toggle.shape = style.backgroundMouseDown
                textOn.style = style.textMouseDownOn
                textOff.style = style.textMouseDownOff
            }
        }
        onMouseRelease { button, x, y ->
            if (button == GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                onToggle.shape = if (mouseOver) style.switchMouseOverOn else style.switchNormalOn
                offToggle.shape = if (mouseOver) style.switchMouseOverOff else style.switchNormalOff
                toggle.shape = if (mouseOver) style.backgroundMouseOver else style.backgroundNormal
                textOn.style = if (mouseOver) style.textMouseOverOn else style.textNormalOn
                textOff.style = if (mouseOver) style.textMouseOverOff else style.textNormalOff
            }
        }
        onMouseClick { button, x, y ->
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                toggleValue.value = !toggleValue.value
                onBlock.isVisible = toggleValue.value
                offBlock.isVisible = !toggleValue.value
            }
        }
    }
}
