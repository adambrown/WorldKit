package com.grimfox.gec

import com.grimfox.gec.ui.JSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

private val LOG: Logger = LoggerFactory.getLogger(Preferences::class.java)

val WORLD_KIT_DIR = File(File(System.getProperty("user.home")), "WorldKit")
val WORLD_KIT_DATA_DIR = File(WORLD_KIT_DIR, "data")
val DEFAULT_TEMP_DIR = File(WORLD_KIT_DATA_DIR, "temp")
val DEFAULT_PROJECTS_DIR = File(WORLD_KIT_DIR, "projects")
val CONFIG_DIR = File(WORLD_KIT_DATA_DIR, "config")
val OFFLINE_HELP_DIR = File(WORLD_KIT_DATA_DIR, "offline-help")
val OFFLINE_HELP_INDEX_FILE = File(OFFLINE_HELP_DIR, "index.html")
val PREFERENCES_FILE = File(CONFIG_DIR, "preferences.json")
val WINDOW_STATE_FILE = File(CONFIG_DIR, "window-state.json")

data class Preferences(
        var rememberWindowState: Boolean = true,
        var projectDir: File = DEFAULT_PROJECTS_DIR,
        var tempDir: File = DEFAULT_TEMP_DIR,
        var windowState: WindowState? = null
)

data class WindowState(
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int,
        var isMaximized: Boolean
)

fun loadPreferences(): Preferences {
    ensureDirectoryExists(WORLD_KIT_DIR)
    ensureDirectoryExists(WORLD_KIT_DATA_DIR)
    ensureDirectoryExists(CONFIG_DIR)
    ensureDirectoryExists(OFFLINE_HELP_DIR)
    val preferences = if (!PREFERENCES_FILE.isFile) {
        Preferences()
    } else if (!PREFERENCES_FILE.canRead()) {
        LOG.warn("Unable to read from existing preferences file.")
        Preferences()
    } else {
        try {
            val preferences = PREFERENCES_FILE.inputStream().buffered().use {
                JSON.readValue(it, Preferences::class.java)
            }
            preferences
        } catch (e: Exception) {
            LOG.warn("Error reading from preferences file.")
            Preferences()
        }
    }
    if (preferences.rememberWindowState) {
        preferences.windowState = loadWindowState()
    }
    ensureDirectoryExists(preferences.tempDir)
    val tempDir = preferences.tempDir
    Runtime.getRuntime().addShutdownHook(Thread({
        tempDir.deleteRecursively()
    }))
    ensureDirectoryExists(preferences.projectDir)
    return preferences
}

fun loadWindowState(): WindowState? {
    if (!WINDOW_STATE_FILE.isFile) {
        return null
    } else if (!WINDOW_STATE_FILE.canRead()) {
        LOG.warn("Unable to read from existing window state file.")
        return null
    } else {
        try {
            return WINDOW_STATE_FILE.inputStream().buffered().use {
                JSON.readValue(it, WindowState::class.java)
            }
        } catch (e: Exception) {
            LOG.warn("Error reading from window state file.")
            return null
        }
    }
}

fun saveWindowState(windowState: WindowState) {
    try {
        WINDOW_STATE_FILE.outputStream().buffered().use {
            JSON.writeValue(it, windowState)
        }
    } catch (e: Exception) {
        LOG.error("Error writing window state file.")
    }
}

private fun ensureDirectoryExists(dir: File) {
    try {
        dir.mkdirs()
        if (!dir.isDirectory || !dir.canWrite()) {
            throw IllegalStateException("Unable to write to ${dir.canonicalPath} dir.")
        }
    } catch (e: Exception) {
        LOG.error("Unable to create ${dir.canonicalPath}.", e)
        throw e
    }
}
