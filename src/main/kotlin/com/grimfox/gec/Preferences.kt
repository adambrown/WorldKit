package com.grimfox.gec

import com.grimfox.gec.extensions.call
import com.grimfox.gec.model.Graph
import com.grimfox.gec.ui.JSON
import com.grimfox.gec.util.Graphs
import com.grimfox.gec.util.timeIt
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

private val LOG: Logger = LoggerFactory.getLogger(Preferences::class.java)

val WORLD_KIT_DIR = File(File(System.getProperty("user.home")), "WorldKit")
val WORLD_KIT_DATA_DIR = File(WORLD_KIT_DIR, "data")
val DEFAULT_TEMP_DIR = File(WORLD_KIT_DATA_DIR, "temp")
val CACHE_DIR = File(WORLD_KIT_DATA_DIR, "cache")
val DEFAULT_PROJECTS_DIR = File(WORLD_KIT_DIR, "projects")
val CONFIG_DIR = File(WORLD_KIT_DATA_DIR, "config")
val OFFLINE_HELP_DIR = File(WORLD_KIT_DATA_DIR, "offline-help")
val OFFLINE_HELP_INDEX_FILE = File(OFFLINE_HELP_DIR, "index.html")
val PREFERENCES_FILE = File(CONFIG_DIR, "preferences.json")
val RECENT_PROJECTS_FILE = File(CACHE_DIR, "recent-projects.json")
val WINDOW_STATE_FILE = File(CONFIG_DIR, "window-state.json")
val CACHED_GRAPH_256_FILE = File(CACHE_DIR, "cached-graph-256.graph")
val CACHED_GRAPH_512_FILE = File(CACHE_DIR, "cached-graph-512.graph")
val CACHED_GRAPH_1024_FILE = File(CACHE_DIR, "cached-graph-1024.graph")

data class Preferences(
        var rememberWindowState: Boolean = true,
        var projectDir: File = DEFAULT_PROJECTS_DIR,
        var tempDir: File = DEFAULT_TEMP_DIR,
        var windowState: WindowState? = null,
        var cachedGraph256: Future<Graph>? = null,
        var cachedGraph512: Future<Graph>? = null,
        var cachedGraph1024: Future<Graph>? = null
)

data class WindowState(
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int,
        var isMaximized: Boolean
)

val threadCount = Runtime.getRuntime().availableProcessors()
val executor: ExecutorService = Executors.newWorkStealingPool()
val preferences = loadPreferences(executor)

fun loadPreferences(executor: ExecutorService): Preferences {
    ensureDirectoryExists(WORLD_KIT_DIR)
    ensureDirectoryExists(WORLD_KIT_DATA_DIR)
    ensureDirectoryExists(CONFIG_DIR)
    ensureDirectoryExists(OFFLINE_HELP_DIR)
    ensureDirectoryExists(CACHE_DIR)
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
    } else {
        preferences.windowState = null
    }
    ensureDirectoryExists(preferences.tempDir)
    val tempDir = preferences.tempDir
    Runtime.getRuntime().addShutdownHook(Thread({
        tempDir.deleteRecursively()
    }))
    ensureDirectoryExists(preferences.projectDir)

    preferences.cachedGraph256 = executor.call {
        var graph: Graph
        if (CACHED_GRAPH_256_FILE.exists() && CACHED_GRAPH_256_FILE.canRead()) {
            try {
                graph = Graphs.deserialize(CACHED_GRAPH_256_FILE.inputStream())
            } catch (e: Exception) {
                LOG.warn("Error reading from cached graph 256 file.")
                graph = Graphs.generateGraph(256, Random(0), 0.8, false, false)
                Graphs.serialize(graph, CACHED_GRAPH_256_FILE.outputStream())
                graph = Graphs.deserialize(CACHED_GRAPH_256_FILE.inputStream())
            }
        } else {
            graph = Graphs.generateGraph(256, Random(0), 0.8, false, false)
            Graphs.serialize(graph, CACHED_GRAPH_256_FILE.outputStream())
            graph = Graphs.deserialize(CACHED_GRAPH_256_FILE.inputStream())
        }
        graph
    }

    preferences.cachedGraph512 = executor.call {
        var graph: Graph
        if (CACHED_GRAPH_512_FILE.exists() && CACHED_GRAPH_512_FILE.canRead()) {
            try {
                graph = Graphs.deserialize(CACHED_GRAPH_512_FILE.inputStream())
            } catch (e: Exception) {
                LOG.warn("Error reading from cached graph 512 file.")
                graph = Graphs.generateGraph(512, Random(0), 0.8, false, false)
                Graphs.serialize(graph, CACHED_GRAPH_512_FILE.outputStream())
                graph = Graphs.deserialize(CACHED_GRAPH_512_FILE.inputStream())
            }
        } else {
            graph = Graphs.generateGraph(512, Random(0), 0.8, false, false)
            Graphs.serialize(graph, CACHED_GRAPH_512_FILE.outputStream())
            graph = Graphs.deserialize(CACHED_GRAPH_512_FILE.inputStream())
        }
        graph
    }

    preferences.cachedGraph1024 = executor.call {
        var graph: Graph
        if (CACHED_GRAPH_1024_FILE.exists() && CACHED_GRAPH_1024_FILE.canRead()) {
            try {
                graph = Graphs.deserialize(CACHED_GRAPH_1024_FILE.inputStream())
            } catch (e: Exception) {
                LOG.warn("Error reading from cached graph 1024 file.")
                graph = Graphs.generateGraph(1024, Random(0), 0.8, false, false)
                Graphs.serialize(graph, CACHED_GRAPH_1024_FILE.outputStream())
                graph = Graphs.deserialize(CACHED_GRAPH_1024_FILE.inputStream())
            }
        } else {
            graph = Graphs.generateGraph(1024, Random(0), 0.8, false, false)
            Graphs.serialize(graph, CACHED_GRAPH_1024_FILE.outputStream())
            graph = Graphs.deserialize(CACHED_GRAPH_1024_FILE.inputStream())
        }
        graph
    }

    return preferences
}

fun savePreferences(preferences: Preferences) {
    try {
        ensureDirectoryExists(WORLD_KIT_DIR)
        ensureDirectoryExists(WORLD_KIT_DATA_DIR)
        ensureDirectoryExists(CONFIG_DIR)
        ensureDirectoryExists(OFFLINE_HELP_DIR)
        ensureDirectoryExists(CACHE_DIR)
        PREFERENCES_FILE.outputStream().buffered().use {
            JSON.writeValue(it, preferences.copy(windowState = null, cachedGraph256 = null, cachedGraph512 = null, cachedGraph1024 = null))
        }
        ensureDirectoryExists(preferences.tempDir)
        val tempDir = preferences.tempDir
        Runtime.getRuntime().addShutdownHook(Thread({
            tempDir.deleteRecursively()
        }))
        ensureDirectoryExists(preferences.projectDir)
    } catch (e: Exception) {
        LOG.error("Error writing to preferences file.")
    }
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
