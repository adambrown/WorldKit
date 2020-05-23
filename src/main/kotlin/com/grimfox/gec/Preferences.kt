package com.grimfox.gec

import com.grimfox.gec.model.*
import com.grimfox.gec.util.*
import com.grimfox.logging.LOG
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.*
import java.util.concurrent.*

val WORLD_KIT_APP_DIR = File(System.getProperty("wk.local.app.dir"))
val WORLD_KIT_DOC_DIR = File(System.getProperty("wk.local.doc.dir"))
val DEFAULT_TEMP_DIR = File(WORLD_KIT_APP_DIR, "Temp")
val CACHE_DIR = File(WORLD_KIT_APP_DIR, "Cache")
val DEFAULT_PROJECTS_DIR = File(WORLD_KIT_DOC_DIR, "Projects")
val CONFIG_DIR = File(WORLD_KIT_APP_DIR, "Config")
val OFFLINE_HELP_DIR = File(WORLD_KIT_APP_DIR, "Offline_Help")
val OFFLINE_HELP_INDEX_FILE = File(OFFLINE_HELP_DIR, "index.html")
val PREFERENCES_FILE = File(CONFIG_DIR, "preferences")
val RECENT_PROJECTS_FILE = File(CACHE_DIR, "recent-projects")
val WINDOW_STATE_FILE = File(CONFIG_DIR, "window-state")
val CACHED_GRAPH_256_FILE = File(CACHE_DIR, "cached-graph-256.graph")
val CACHED_GRAPH_1024_FILE = File(CACHE_DIR, "cached-graph-1024.graph")

val LOW_DATABASE_0 = File(WORLD_KIT_APP_DIR, "undersea-dictionary-low.bin")
val LOW_DATABASE_1 = File(WORLD_KIT_APP_DIR, "mountain-dictionary-low.bin")
val LOW_DATABASE_2 = File(WORLD_KIT_APP_DIR, "canyon-dictionary-low.bin")

val HIGH_DATABASE_0 = File(WORLD_KIT_APP_DIR, "undersea-dictionary-high.bin")
val HIGH_DATABASE_1 = File(WORLD_KIT_APP_DIR, "mountain-dictionary-high.bin")
val HIGH_DATABASE_2 = File(WORLD_KIT_APP_DIR, "canyon-top-dictionary-high.bin")
val HIGH_DATABASE_3 = File(WORLD_KIT_APP_DIR, "canyon-dictionary-high.bin")

data class Preferences(
        var rememberWindowState: Boolean = true,
        var projectDir: File = DEFAULT_PROJECTS_DIR,
        var tempDir: File = DEFAULT_TEMP_DIR,
        var isFirstRun: Boolean = true,
        var windowState: WindowState? = null,
        var cachedGraph256: Future<Graph>? = null,
        var cachedGraph1024: Future<Graph>? = null,
        var lowDictionaries4: Future<Triple<Int, Int, List<Pair<RcMatrix, RcMatrix>>>>? = null,
        var highDictionaries4: Future<Triple<Int, Int, List<Pair<RcMatrix, RcMatrix>>>>? = null,
        var highDictionaries8: Future<Triple<Int, Int, List<Pair<RcMatrix, RcMatrix>>>>? = null
) {
    val autosaveDir: File get() = File(projectDir, "autosaves").canonicalFile

    companion object {

        fun deserialize(input: DataInputStream): Preferences {
            val rememberWindowState = input.readBoolean()
            val projectDir = File(input.readUTF())
            val tempDir = File(input.readUTF())
            val isFirstRun = input.readBoolean()
            return Preferences(
                    rememberWindowState = rememberWindowState,
                    projectDir = projectDir,
                    tempDir = tempDir,
                    isFirstRun = isFirstRun)
        }
    }

    fun serialize(output: DataOutputStream) {
        output.writeBoolean(rememberWindowState)
        output.writeUTF(projectDir.canonicalPath)
        output.writeUTF(tempDir.canonicalPath)
        output.writeBoolean(isFirstRun)
    }
}

data class WindowState(
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int,
        var isMaximized: Boolean,
        var monitorIndex: Int,
        var autoSaveIndex: Int = 0) {

    companion object {

        fun deserialize(input: DataInputStream): WindowState {
            return WindowState(
                    x = input.readInt(),
                    y = input.readInt(),
                    width = input.readInt(),
                    height = input.readInt(),
                    isMaximized = input.readBoolean(),
                    monitorIndex = input.readInt(),
                    autoSaveIndex = input.readInt()
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
        output.writeInt(autoSaveIndex)
    }
}

val threadCount = Runtime.getRuntime().availableProcessors()
val executor: ExecutorService = Executors.newWorkStealingPool()
val dispatcher = executor.asCoroutineDispatcher()
val preferences = loadPreferences(executor)

fun loadPreferences(executor: ExecutorService): Preferences {
    ensureDirectoryExists(WORLD_KIT_APP_DIR)
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
            val preferences = DataInputStream(PREFERENCES_FILE.inputStream().buffered()).use {
                Preferences.deserialize(it)
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
                graph = Graphs.generateGraph(256, 0L, 0.8, false, false)
                Graphs.serialize(graph, CACHED_GRAPH_256_FILE.outputStream())
                graph = Graphs.deserialize(CACHED_GRAPH_256_FILE.inputStream())
            }
        } else {
            graph = Graphs.generateGraph(256, 0L, 0.8, false, false)
            Graphs.serialize(graph, CACHED_GRAPH_256_FILE.outputStream())
            graph = Graphs.deserialize(CACHED_GRAPH_256_FILE.inputStream())
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
                graph = Graphs.generateGraph(1024, 2L, 0.8, false, false)
                Graphs.serialize(graph, CACHED_GRAPH_1024_FILE.outputStream())
                graph = Graphs.deserialize(CACHED_GRAPH_1024_FILE.inputStream())
            }
        } else {
            graph = Graphs.generateGraph(1024, 2L, 0.8, false, false)
            Graphs.serialize(graph, CACHED_GRAPH_1024_FILE.outputStream())
            graph = Graphs.deserialize(CACHED_GRAPH_1024_FILE.inputStream())
        }
        graph
    }

    preferences.lowDictionaries4 = executor.call {
        val (maskSize, offset, dictionaries) = TerrainAmplification.loadDictionaries(4, listOf(LOW_DATABASE_0, LOW_DATABASE_1, LOW_DATABASE_2))
        Triple(maskSize, offset, listOf(dictionaries[0], dictionaries[1], dictionaries[2], dictionaries[2]))
    }

    preferences.highDictionaries4 = executor.call {
        val (maskSize, offset, dictionaries) = TerrainAmplification.loadDictionaries(4, listOf(HIGH_DATABASE_0, HIGH_DATABASE_1, HIGH_DATABASE_2, HIGH_DATABASE_3))
        Triple(maskSize, offset, listOf(dictionaries[0], dictionaries[1], dictionaries[2], dictionaries[3]))
    }

    preferences.highDictionaries8 = executor.call {
        val (maskSize, offset, dictionaries) = TerrainAmplification.loadDictionaries(8, listOf(HIGH_DATABASE_0, HIGH_DATABASE_1, HIGH_DATABASE_2, HIGH_DATABASE_3))
        Triple(maskSize, offset, listOf(dictionaries[0], dictionaries[1], dictionaries[2], dictionaries[3]))
    }

    return preferences
}

fun savePreferences(preferences: Preferences) {
    try {
        ensureDirectoryExists(WORLD_KIT_APP_DIR)
        ensureDirectoryExists(CONFIG_DIR)
        ensureDirectoryExists(OFFLINE_HELP_DIR)
        ensureDirectoryExists(CACHE_DIR)
        DataOutputStream(PREFERENCES_FILE.outputStream().buffered()).use {
            preferences.copy(windowState = null, cachedGraph256 = null, cachedGraph1024 = null, lowDictionaries4 = null, highDictionaries4 = null, highDictionaries8 = null).serialize(it)
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
