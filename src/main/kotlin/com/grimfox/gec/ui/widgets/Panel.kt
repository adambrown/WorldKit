package com.grimfox.gec.ui.widgets

import com.grimfox.gec.*

fun Block.panel(width: Float, sizing: Sizing = Sizing.SHRINK, height: Float = 10000.0f, content: Block.() -> Unit): Block {
    val panel = block {
        isVisible = true
        hAlign = HorizontalAlignment.CENTER
        vAlign = VerticalAlignment.MIDDLE
        hSizing = Sizing.STATIC
        vSizing = sizing
        this.width = width
        this.height = height
        block {
            xOffset = 4.0f
            yOffset = 4.0f
            canOverflow = true
            shape = SHAPE_DROP_SHADOW_DARK
            isMouseAware = false
        }
        block {
            shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
            block {
                xOffset = 1.0f
                yOffset = 1.0f
                this.width = -2.0f
                this.height = -2.0f
                shape = BACKGROUND_RECT
            }
        }
        block {
            padLeft = LARGE_SPACER_SIZE
            padRight = LARGE_SPACER_SIZE
            vSizing = sizing
            block {
                content()
            }
        }
    }
    panel.isVisible = false
    return panel
}
