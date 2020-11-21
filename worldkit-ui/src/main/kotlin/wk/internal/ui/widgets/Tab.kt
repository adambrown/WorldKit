package wk.internal.ui.widgets

import wk.api.ObservableMutableReference
import org.lwjgl.glfw.GLFW

data class TabStyle(
        val normal: Shape,
        val indicatorNormal: Shape,
        val textNormal: TextStyle,
        val textShapeNormal: Shape = NO_SHAPE,
        val active: Shape,
        val indicatorActive: Shape,
        val textActive: TextStyle,
        val textShapeActive: Shape = NO_SHAPE,
        val mouseOver: Shape,
        val indicatorMouseOver: Shape,
        val textMouseOver: TextStyle,
        val textShapeMouseOver: Shape = NO_SHAPE,
        val mouseDown: Shape,
        val indicatorMouseDown: Shape,
        val textMouseDown: TextStyle,
        val textShapeMouseDown: Shape = NO_SHAPE,
        val template: BlockTemplate = BlockTemplate(
                layout = Layout.HORIZONTAL),
        val indicatorTemplate: BlockTemplate = BlockTemplate(
                layout = Layout.ABSOLUTE,
                vAlign = VerticalAlignment.BOTTOM,
                vSizing = Sizing.STATIC,
                height = 2.0f),
        val textShapeTemplate: BlockTemplate = BlockTemplate(
                hAlign = HorizontalAlignment.CENTER,
                vAlign = VerticalAlignment.MIDDLE,
                hSizing = Sizing.SHRINK,
                vSizing = Sizing.SHRINK)) {

    inline operator fun invoke(builder: TabStyle.() -> TabStyle): TabStyle {
        return this.builder()
    }
}

fun Block.tab(text: Text, isActive: ObservableMutableReference<Boolean>, style: TabStyle, onClick: () -> Unit = {}): Block {
    text.style = if (isActive.value) style.textActive else style.textNormal
    return block {
        val mainBlock = this
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
        shape = if (isActive.value) style.active else style.normal
        val textShape = block {
            isVisible = style.textShapeTemplate.isVisible
            hAlign = style.textShapeTemplate.hAlign
            vAlign = style.textShapeTemplate.vAlign
            layout = style.textShapeTemplate.layout
            xOffset = style.textShapeTemplate.xOffset
            yOffset = style.textShapeTemplate.yOffset
            hSizing = style.textShapeTemplate.hSizing
            width = style.textShapeTemplate.width
            vSizing = style.textShapeTemplate.vSizing
            height = style.textShapeTemplate.height
            padLeft = style.textShapeTemplate.padLeft
            padRight = style.textShapeTemplate.padRight
            padTop = style.textShapeTemplate.padTop
            padBottom = style.textShapeTemplate.padBottom
            shape = if (isActive.value) style.textShapeActive else style.textShapeNormal
            this.text = text
            isMouseAware = false
        }
        val indicator = block {
            isVisible = style.indicatorTemplate.isVisible
            hAlign = style.indicatorTemplate.hAlign
            vAlign = style.indicatorTemplate.vAlign
            layout = style.indicatorTemplate.layout
            xOffset = style.indicatorTemplate.xOffset
            yOffset = style.indicatorTemplate.yOffset
            hSizing = style.indicatorTemplate.hSizing
            width = style.indicatorTemplate.width
            vSizing = style.indicatorTemplate.vSizing
            height = style.indicatorTemplate.height
            padLeft = style.indicatorTemplate.padLeft
            padRight = style.indicatorTemplate.padRight
            padTop = style.indicatorTemplate.padTop
            padBottom = style.indicatorTemplate.padBottom
            shape = if (isActive.value) style.indicatorActive else style.indicatorNormal
            isMouseAware = false
        }
        onMouseOver {
            mouseOver = true
            if (!mouseDownOver && !isActive.value) {
                mainBlock.shape = style.mouseOver
                text.style = style.textMouseOver
                textShape.shape = style.textShapeMouseOver
                indicator.shape = style.indicatorMouseOver
            }
        }
        onMouseOut {
            mouseOver = false
            if (!mouseDownOver) {
                val active = isActive.value
                mainBlock.shape = if (active) style.active else style.normal
                text.style = if (active) style.textActive else style.textNormal
                textShape.shape = if (active) style.textShapeActive else style.textShapeNormal
                indicator.shape = if (active) style.indicatorActive else style.indicatorNormal
            }
        }
        onMouseDown { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                mouseDownOver = true
                if (!isActive.value) {
                    mainBlock.shape = style.mouseDown
                    text.style = style.textMouseDown
                    textShape.shape = style.textShapeMouseDown
                    indicator.shape = style.indicatorMouseDown
                }
                callTaskSafe(onClick)
            }
        }
        onMouseRelease { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                val active = isActive.value
                mainBlock.shape = if (active) style.active else if (mouseOver) style.mouseOver else style.normal
                text.style = if (active) style.textActive else if (mouseOver) style.textMouseOver else style.textNormal
                textShape.shape = if (active) style.textShapeActive else if (mouseOver) style.textShapeMouseOver else style.textShapeNormal
                indicator.shape = if (active) style.indicatorActive else if (mouseOver) style.indicatorMouseOver else style.indicatorNormal

            }
        }
        isActive.addListener { _, new ->
            if (new) {
                mainBlock.shape = style.active
                text.style = style.textActive
                textShape.shape = style.textShapeActive
                indicator.shape = style.indicatorActive
            } else {
                if (!mouseOver && !mouseDownOver) {
                    mainBlock.shape = style.normal
                    text.style = style.textNormal
                    textShape.shape = style.textShapeNormal
                    indicator.shape = style.indicatorNormal
                }
            }
        }
    }
}
