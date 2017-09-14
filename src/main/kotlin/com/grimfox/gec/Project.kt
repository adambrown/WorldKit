package com.grimfox.gec

import com.fasterxml.jackson.core.JsonParseException
import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.HistoryQueue
import com.grimfox.gec.ui.JSON
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.BiomeParameters
import com.grimfox.gec.util.BuildContinent.RegionParameters
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.gec.util.FileDialogs.selectFile
import com.grimfox.gec.util.FileDialogs.saveFileDialog
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

private val LOG: Logger = LoggerFactory.getLogger(Project::class.java)

class CurrentState(var regionParameters: RegionParameters? = null,
                   var regionGraph: Graph? = null,
                   var regionMask: ByteArrayMatrix? = null,
                   var regionSplines: RegionSplines? = null,
                   var biomeParameters: BiomeParameters? = null,
                   var biomeGraph: Graph? = null,
                   var biomeMask: ByteArrayMatrix? = null,
                   var biomes: List<Biomes.Biome>? = null,
                   var heightMapTexture: TextureId? = null,
                   var riverMapTexture: TextureId? = null)

data class Project(
        var file: File? = null,
        var currentState: CurrentState = CurrentState(),
        val historyRegionsBackQueue: HistoryQueue<RegionsHistoryItem> = HistoryQueue(1000),
        val historyRegionsCurrent: MonitoredReference<RegionsHistoryItem?> = ref(null),
        val historyRegionsForwardQueue: HistoryQueue<RegionsHistoryItem> = HistoryQueue(1000),
        val historySplinesBackQueue: HistoryQueue<RegionSplines> = HistoryQueue(1000),
        val historySplinesCurrent: MonitoredReference<RegionSplines?> = ref(null),
        val historySplinesForwardQueue: HistoryQueue<RegionSplines> = HistoryQueue(1000),
        val historyBiomesBackQueue: HistoryQueue<BiomesHistoryItem> = HistoryQueue(1000),
        val historyBiomesCurrent: MonitoredReference<BiomesHistoryItem?> = ref(null),
        val historyBiomesForwardQueue: HistoryQueue<BiomesHistoryItem> = HistoryQueue(1000))

private val PROJECT_MOD_LOCK: Lock = ReentrantLock(true)

val recentProjects = ArrayList<Pair<File, Block>>()
val recentProjectsDropdown = ref<DropdownList?>(null)
val recentProjectsAvailable = ref(false)
val currentProject = ref<Project?>(null)
val doesActiveProjectExist = ref(false)

private class RecentProjects(val recentProjects: List<String>)

fun loadRecentProjects(dialogLayer: Block, overwriteWarningReference: MutableReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, ui: UserInterface, errorHandler: ErrorDialog) {
    if (RECENT_PROJECTS_FILE.isFile && RECENT_PROJECTS_FILE.canRead()) {
        RECENT_PROJECTS_FILE.inputStream().buffered().use {
            JSON.readValue(it, RecentProjects::class.java)
        }?.recentProjects?.map(::File)?.forEach {
            addProjectToRecentProjects(it, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
        }
    }
}

fun saveRecentProjects() {
    try {
        RECENT_PROJECTS_FILE.outputStream().buffered().use {
            JSON.writeValue(it, RecentProjects(recentProjects.map { it.first.canonicalPath }))
        }
    } catch (e: Exception) {
        LOG.error("Error writing recent projects file.")
    }
}

fun addProjectToRecentProjects(folder: File?, dialogLayer: Block, overwriteWarningReference: MutableReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, ui: UserInterface, errorHandler: ErrorDialog) {
    val finalFolder = folder ?: return
    sync {
        val projectPath = finalFolder.canonicalPath
        val showPath = if (projectPath.length < 55) {
            projectPath
        } else {
            "${projectPath.substring(0, 25)} ... ${projectPath.substring(projectPath.length - 25)}"
        }
        var index = -1
        recentProjects.forEachIndexed { i, pair ->
            if (pair.first == finalFolder) {
                index = i
            }
        }
        if (index > -1) {
            val pair = recentProjects.removeAt(index)
            recentProjectsDropdown.value?.removeItem(pair.second)
        }
        val projectBlock = recentProjectsDropdown.value?.menuItem(showPath) {
            val openFun = {
                try {
                    val openedProject = openProject(finalFolder, dialogLayer, ui)
                    if (openedProject != null) {
                        currentProject.value = openedProject
                    }
                } catch (e: JsonParseException) {
                    errorHandler.displayErrorMessage("The selected file is not a valid project.")
                } catch (e: IOException) {
                    errorHandler.displayErrorMessage("Unable to read from the selected file while trying to open project.")
                } catch (e: Exception) {
                    errorHandler.displayErrorMessage("Encountered an unexpected error while trying to open project.")
                }
            }
            if (currentProject.value != null) {
                dialogLayer.isVisible = true
                overwriteWarningReference.value = "Do you want to save the current project before opening a different one?"
                overwriteWarningDialog.isVisible = true
                dialogCallback.value = {
                    openFun()
                    dialogCallback.value = {}
                }
            } else {
                openFun()
            }
        }
        if (projectBlock != null) {
            recentProjects.add(0, Pair(finalFolder, projectBlock))
            recentProjectsDropdown.value?.moveItemToIndex(projectBlock, 0)
        }
        if (recentProjects.size > 10) {
            val pair = recentProjects.removeAt(recentProjects.size - 1)
            recentProjectsDropdown.value?.removeItem(pair.second)
        }
        recentProjectsAvailable.value = !recentProjects.isEmpty()
    }
}

fun clearRecentProjects() {
    sync {
        recentProjects.forEach {
            recentProjectsDropdown.value?.removeItem(it.second)
        }
        recentProjects.clear()
        recentProjectsAvailable.value = false
    }
}

fun saveProject(project: Project?,
                dialogLayer: Block,
                preferences: Preferences,
                ui: UserInterface,
                titleText: DynamicTextReference,
                overwriteWarningReference: MutableReference<String>,
                overwriteWarningDialog: Block,
                dialogCallback: MutableReference<() -> Unit>,
                errorHandler: ErrorDialog): Boolean {
    if (project != null) {
        val file = project.file
        if (file == null) {
            return saveProjectAs(project, dialogLayer, preferences, ui, titleText, overwriteWarningReference, overwriteWarningDialog, dialogCallback, errorHandler)
        } else {
            exportProjectFile(project, file)
            addProjectToRecentProjects(file, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
            return true
        }
    }
    return false
}

fun saveProjectAs(project: Project?,
                  dialogLayer: Block,
                  preferences: Preferences,
                  ui: UserInterface,
                  titleText: DynamicTextReference,
                  overwriteWarningReference: MutableReference<String>,
                  overwriteWarningDialog: Block,
                  dialogCallback: MutableReference<() -> Unit>,
                  errorHandler: ErrorDialog): Boolean {
    if (project != null) {
        ui.ignoreInput = true
        dialogLayer.isVisible = true
        try {
            val saveFile = saveFileDialog(preferences.projectDir, "wkp")
            if (saveFile != null) {
                val fullNameWithExtension = "${saveFile.name.removeSuffix(".wkp")}.wkp"
                val actualFile = File(saveFile.parentFile, fullNameWithExtension)
                exportProjectFile(project, actualFile)
                project.file = actualFile
                addProjectToRecentProjects(actualFile, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                updateTitle(titleText, project)
                return true
            } else {
                return false
            }
        } finally {
            dialogLayer.isVisible = false
            ui.ignoreInput = false
        }
    }
    return false
}

fun openProject(file: File,
                dialogLayer: Block,
                ui: UserInterface): Project? {
    ui.ignoreInput = true
    dialogLayer.isVisible = true
    try {
        val project = importProjectFile(file)
        if (project != null) {
            project.file = file
        }
        return project
    } catch (e: Exception) {
        LOG.error("Unexpected error opening project.", e)
        throw e
    } finally {
        dialogLayer.isVisible = false
        ui.ignoreInput = false
    }
}

fun openProject(dialogLayer: Block,
                preferences: Preferences,
                ui: UserInterface): Project? {
    return selectFile(dialogLayer, true, ui, preferences.projectDir, "wkp") { file ->
        if (file == null) {
            null
        } else {
            val project = importProjectFile(file)
            if (project != null) {
                project.file = file
            }
            project
        }
    }
}

fun updateTitle(titleText: DynamicTextReference, new: Project?) {
    val name = if (new == null) {
        "No project"
    } else {
        new.file?.canonicalPath ?: "New unsaved project"
    }
    val showPath = if (name.length < 55) {
        name
    } else {
        "${name.substring(0, 25)} ... ${name.substring(name.length - 25)}"
    }
    titleText.reference.value = "WorldKit - $showPath"
}

private fun <T> sync(codeBlock: () -> T): T {
    PROJECT_MOD_LOCK.lock()
    try {
        return codeBlock()
    } finally {
        PROJECT_MOD_LOCK.unlock()
    }
}