package com.grimfox.gec

import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import java.io.File

fun preferencesPanel(ui: UserInterface) {
    panelLayer {
        exportPanel = panel(650.0f) {
            vSizing = Sizing.SHRINK
            val shrinkGroup = hShrinkGroup()
            vSpacer(LARGE_SPACER_SIZE)
            vToggleRow(rememberWindowState, LARGE_ROW_HEIGHT, text("Remember window state:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            vFolderRow(projectDir, LARGE_ROW_HEIGHT, text("Project folder:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, false, ui)
            vFolderRow(tempDir, LARGE_ROW_HEIGHT, text("Temporary folder:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, false, ui)
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("Save"), DIALOG_BUTTON_STYLE) {
                    preferences.rememberWindowState = rememberWindowState.value
                    preferences.projectDir = File(projectDir.reference.value)
                    preferences.tempDir = File(tempDir.reference.value)
                    savePreferences(preferences)
                    preferencesPanel.isVisible = false
                    panelLayer.isVisible = false
                }.with { width = 60.0f }
                hSpacer(SMALL_SPACER_SIZE)
                button(text("Cancel"), DIALOG_BUTTON_STYLE) {
                    rememberWindowState.value = preferences.rememberWindowState
                    projectDir.reference.value = preferences.projectDir.canonicalPath
                    tempDir.reference.value = preferences.tempDir.canonicalPath
                    preferencesPanel.isVisible = false
                    panelLayer.isVisible = false
                }.with { width = 60.0f }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
        }
    }
}
