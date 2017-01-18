package com.grimfox.gec.ui.widgets

import com.grimfox.gec.*

fun Block.dialog(width: Float, height: Float, text: Text, glyph: Block.() -> Block, buttons: Block.() -> Unit): Block {
    val dialog = block {
        hAlign = HorizontalAlignment.CENTER
        vAlign = VerticalAlignment.MIDDLE
        hSizing = Sizing.STATIC
        vSizing = Sizing.STATIC
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
            vSpacer(MEDIUM_SPACER_SIZE)
            block {
                layout = Layout.VERTICAL
                vSizing = Sizing.GROW
                block {
                    hSizing = Sizing.SHRINK
                    layout = Layout.HORIZONTAL
                    canOverflow = true
                    block {
                        hSizing = Sizing.SHRINK
                        vSizing = Sizing.SHRINK
                        hAlign = HorizontalAlignment.CENTER
                        vAlign = VerticalAlignment.MIDDLE
                        canOverflow = true
                        glyph()
                    }
                }
                hSpacer(LARGE_SPACER_SIZE)
                block {
                    hSizing = Sizing.GROW
                    layout = Layout.HORIZONTAL
                    hAlign = HorizontalAlignment.LEFT
                    vAlign = VerticalAlignment.MIDDLE
                    this.text = text
                }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT, buttons)
            vSpacer(MEDIUM_SPACER_SIZE)
        }
    }
    dialog.isVisible = false
    return dialog
}
