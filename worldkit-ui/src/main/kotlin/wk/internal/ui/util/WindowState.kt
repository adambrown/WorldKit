package wk.internal.ui.util

import wk.internal.application.LOG
import wk.internal.application.configDir
import wk.internal.application.userDir
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

private val WINDOW_STATE_FILE = File(configDir, "window-state")

val windowState = loadWindowState() ?: WindowState()

data class WindowState(
        var x: Int = -1,
        var y: Int = -1,
        var width: Int = 1280,
        var height: Int = 720,
        var isMaximized: Boolean = false,
        var monitorIndex: Int = -1,
        var lastFileDialogPath: File = userDir
) {

    companion object {

        fun deserialize(input: DataInputStream): WindowState {
            return WindowState(
                    x = input.readInt(),
                    y = input.readInt(),
                    width = input.readInt(),
                    height = input.readInt(),
                    isMaximized = input.readBoolean(),
                    monitorIndex = input.readInt(),
                    lastFileDialogPath = (File(input.readUTF()))
            )
        }
    }

    fun serialize(output: DataOutputStream) {
        output.writeInt(x)
        output.writeInt(y)
        output.writeInt(width)
        output.writeInt(height)
        output.writeBoolean(isMaximized)
        output.writeInt(monitorIndex)
        output.writeUTF(lastFileDialogPath.absolutePath)
    }
}

fun loadWindowState(): WindowState? {
    return when {
        !WINDOW_STATE_FILE.isFile -> null
        !WINDOW_STATE_FILE.canRead() -> {
            LOG.warn("Unable to read from existing window state file.")
            null
        }
        else -> try {
            DataInputStream(WINDOW_STATE_FILE.inputStream().buffered()).use {
                WindowState.deserialize(it)
            }
        } catch (e: Exception) {
            LOG.warn("Error reading from window state file.")
            null
        }
    }
}

fun saveWindowState(windowState: WindowState) {
    try {
        DataOutputStream(WINDOW_STATE_FILE.outputStream().buffered()).use {
            windowState.serialize(it)
        }
    } catch (e: Exception) {
        LOG.error("Error writing window state file.")
    }
}
