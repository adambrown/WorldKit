package com.grimfox.gec.ui.widgets

import com.grimfox.gec.*

fun Block.panel(width: Float, content: Block.() -> Unit): Block {
    val panel = block {
        hAlign = HorizontalAlignment.CENTER
        vAlign = VerticalAlignment.MIDDLE
        hSizing = Sizing.STATIC
        vSizing = Sizing.SHRINK
        this.width = width
        this.height = height
        isVisible = true
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
            vSizing = Sizing.SHRINK
            block {
                content()
            }
        }
    }
    panel.isVisible = false
    return panel
}
