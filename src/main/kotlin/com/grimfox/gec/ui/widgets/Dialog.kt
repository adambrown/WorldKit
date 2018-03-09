package com.grimfox.gec.ui.widgets

import com.grimfox.gec.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.CENTER
import com.grimfox.gec.ui.widgets.VerticalAlignment.MIDDLE

fun Block.dialog(width: Float, height: Float, text: Text, glyph: Block.() -> Block, buttons: Block.() -> Unit): Block {
    val dialog = block {
        hAlign = CENTER
        vAlign = MIDDLE
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
                        hAlign = CENTER
                        vAlign = MIDDLE
                        canOverflow = true
                        glyph()
                    }
                }
                hSpacer(LARGE_SPACER_SIZE)
                block {
                    hSizing = Sizing.GROW
                    layout = Layout.HORIZONTAL
                    hAlign = HorizontalAlignment.LEFT
                    vAlign = MIDDLE
                    this.text = text
                }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT, CENTER, MIDDLE, buttons)
            vSpacer(MEDIUM_SPACER_SIZE)
        }
    }
    dialog.isVisible = false
    return dialog
}
