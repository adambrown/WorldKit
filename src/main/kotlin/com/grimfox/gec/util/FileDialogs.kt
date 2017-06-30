package com.grimfox.gec.util

import com.grimfox.gec.ui.FileDialogs
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.Block
import java.io.File

object FileDialogs {

    fun <T> selectFile(dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface, defaultDir: File, vararg extensions: String, callback: (File?) -> T): T {
        ui.ignoreInput = true
        if (useDialogLayer) {
            dialogLayer.isVisible = true
        }
        try {
            return callback(selectFileDialog(defaultDir, *extensions))
        } catch (e: Exception) {
            com.grimfox.gec.ui.LOG.error("Unexpected error opening file.", e)
            throw e
        } finally {
            if (useDialogLayer) {
                dialogLayer.isVisible = false
            }
            ui.ignoreInput = false
        }
    }

    fun saveFileDialog(defaultFolder: File, vararg filters: String): File? {
        val saveFileName = FileDialogs.saveFile(filters.joinToString(","), defaultFolder.canonicalPath)
        if (saveFileName != null && saveFileName.isNotBlank()) {
            return File(saveFileName)
        }
        return null
    }

    fun selectFileDialog(defaultFolder: File, vararg filters: String): File? {
        val fileName = FileDialogs.selectFile(filters.joinToString(","), defaultFolder.canonicalPath)
        if (fileName != null && fileName.isNotBlank()) {
            return File(fileName)
        }
        return null
    }

    fun selectFolder(dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface, currentFolder: File): File {
        ui.ignoreInput = true
        if (useDialogLayer) {
            dialogLayer.isVisible = true
        }
        try {
            return selectFolderDialog(currentFolder) ?: currentFolder
        } finally {
            if (useDialogLayer) {
                dialogLayer.isVisible = false
            }
            ui.ignoreInput = false
        }
    }

    fun selectFolderDialog(defaultFolder: File): File? {
        val folderName = FileDialogs.selectFolder(defaultFolder.canonicalPath)
        if (folderName != null && folderName.isNotBlank()) {
            return File(folderName)
        }
        return null
    }
}