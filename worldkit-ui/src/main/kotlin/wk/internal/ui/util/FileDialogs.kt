package wk.internal.ui.util

import wk.internal.application.LOG
import wk.internal.ui.FileDialogs
import wk.internal.ui.UserInterface
import wk.internal.ui.widgets.Block
import java.io.File

object FileDialogs {

    fun <T> selectFile(dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface, defaultDir: File, vararg extensions: String, callback: (File?) -> T): T {
        ui.ignoreInput = true
        var dialogWasVisible = false
        if (useDialogLayer) {
            dialogWasVisible = dialogLayer.isVisible
            dialogLayer.isVisible = true
        }
        try {
            return callback(selectFileDialog(defaultDir, *extensions))
        } catch (e: Exception) {
            LOG.error("Unexpected error opening file.", e)
            throw e
        } finally {
            if (useDialogLayer) {
                dialogLayer.isVisible = dialogWasVisible
            }
            ui.ignoreInput = false
        }
    }

    fun <T> saveFile(dialogLayer: Block, useDialogLayer: Boolean, ui: UserInterface, defaultDir: File, vararg extensions: String, callback: (File?) -> T): T {
        ui.ignoreInput = true
        if (useDialogLayer) {
            dialogLayer.isVisible = true
        }
        try {
            return callback(saveFileDialog(defaultDir, *extensions))
        } catch (e: Exception) {
            LOG.error("Unexpected error saving file.", e)
            throw e
        } finally {
            if (useDialogLayer) {
                dialogLayer.isVisible = false
            }
            ui.ignoreInput = false
        }
    }

    fun saveFileDialog(defaultFolder: File, vararg filters: String): File? {
        val saveFileName = if (filters.isEmpty()) {
            FileDialogs.saveFile(null, defaultFolder.canonicalPath)
        } else {
            FileDialogs.saveFile(filters.joinToString(","), defaultFolder.canonicalPath)
        }
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