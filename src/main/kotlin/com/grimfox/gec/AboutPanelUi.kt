package com.grimfox.gec

import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.SHRINK

fun aboutPanel(ui: UserInterface) {
    panelLayer {
        aboutPanel = panel(650.0f) {
            vSizing = SHRINK
            vSpacer(LARGE_SPACER_SIZE)
            block {
                layout = VERTICAL
                vSizing = SHRINK
                val iconSize = 64.0f
                icon(icon.value, iconSize, iconSize, iconSize + 2 * SMALL_SPACER_SIZE, iconSize + 2 * SMALL_SPACER_SIZE)
                hSpacer(LARGE_SPACER_SIZE)
                block {
                    layout = HORIZONTAL
                    vSizing = SHRINK
                    vSpacer(MEDIUM_SPACER_SIZE)
                    vLabelRow(LARGE_ROW_HEIGHT, text("WorldKit", TEXT_STYLE_LARGE_MESSAGE))
                    vSpacer(LARGE_SPACER_SIZE)
                    vLabelRow(LARGE_ROW_HEIGHT, text("Version 0.12.0-alpha", TEXT_STYLE_NORMAL))
                    vLabelRow(LARGE_ROW_HEIGHT, text("Copyright 2018 Intellirithmic Inc. All rights reserved.", TEXT_STYLE_NORMAL))
                }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("Close"), DIALOG_BUTTON_STYLE) {
                    aboutPanel.isVisible = false
                    panelLayer.isVisible = false
                }.with { width = 60.0f }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
        }
    }
}
