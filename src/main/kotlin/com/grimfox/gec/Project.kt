package com.grimfox.gec

import com.fasterxml.jackson.core.JsonParseException
import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.HistoryQueue
import com.grimfox.gec.model.ObservableCollection.ModificationEvent
import com.grimfox.gec.ui.JSON
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.Block
import com.grimfox.gec.ui.widgets.DropdownList
import com.grimfox.gec.ui.widgets.DynamicTextReference
import com.grimfox.gec.ui.widgets.ErrorDialog
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.util.BLANK_TEXTURE
import com.grimfox.gec.util.Biomes.Biome
import com.grimfox.gec.util.BuildContinent.BiomeParameters
import com.grimfox.gec.util.BuildContinent.RegionParameters
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.gec.util.FileDialogs.saveFileDialog
import com.grimfox.gec.util.FileDialogs.selectFile
import com.grimfox.gec.util.ObservableMutableReference
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.ref
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

private val log: Logger = LoggerFactory.getLogger(Project::class.java)

class CurrentState(
        val regionParameters: ObservableMutableReference<RegionParameters?> = ref(null),
        val regionGraph: ObservableMutableReference<Graph?> = ref(null),
        val regionMask: ObservableMutableReference<ByteArrayMatrix?> = ref(null),
        val regionSplines: ObservableMutableReference<RegionSplines?> = ref(null),
        val biomeParameters: ObservableMutableReference<BiomeParameters?> = ref(null),
        val biomeGraph: ObservableMutableReference<Graph?> = ref(null),
        val biomeMask: ObservableMutableReference<ByteArrayMatrix?> = ref(null),
        val biomes: ObservableMutableReference<List<Biome>?> = ref(null),
        val customElevationPowerMap: ObservableMutableReference<TextureId> = ref(BLANK_TEXTURE),
        val customStartingHeightsMap: ObservableMutableReference<TextureId> = ref(BLANK_TEXTURE),
        val customSoilMobilityMap: ObservableMutableReference<TextureId> = ref(BLANK_TEXTURE),
        val heightMapTexture: ObservableMutableReference<TextureId?> = ref(null),
        val riverMapTexture: ObservableMutableReference<TextureId?> = ref(null)) {

    fun copy(): CurrentState {
        return CurrentState(
                ref(regionParameters.value?.copy()),
                ref(regionGraph.value),
                copyMatrixRef(regionMask),
                ref(regionSplines.value),
                ref(biomeParameters.value?.copy()),
                ref(biomeGraph.value),
                copyMatrixRef(biomeMask)
        )
    }

    private fun copyMatrixRef(inputMatrixRef: ObservableMutableReference<ByteArrayMatrix?>): ObservableMutableReference<ByteArrayMatrix?> {
        val inputMatrixVal = inputMatrixRef.value
        return if (inputMatrixVal != null) {
            ref(ByteArrayMatrix(inputMatrixVal.width, Arrays.copyOf(inputMatrixVal.array, inputMatrixVal.array.size)))
        } else {
            ref(null)
        }
    }
}

data class Project(
        var file: File? = null,
        var isModifiedSinceSave: ObservableMutableReference<Boolean> = ref(false),
        var currentState: ObservableMutableReference<CurrentState> = ref(CurrentState()),
        val historyRegionsBackQueue: HistoryQueue<RegionsHistoryItem> = HistoryQueue(1000),
        val historyRegionsCurrent: ObservableMutableReference<RegionsHistoryItem?> = ref(null),
        val historyRegionsForwardQueue: HistoryQueue<RegionsHistoryItem> = HistoryQueue(1000),
        val historySplinesBackQueue: HistoryQueue<RegionSplines> = HistoryQueue(1000),
        val historySplinesCurrent: ObservableMutableReference<RegionSplines?> = ref(null),
        val historySplinesForwardQueue: HistoryQueue<RegionSplines> = HistoryQueue(1000),
        val historyBiomesBackQueue: HistoryQueue<BiomesHistoryItem> = HistoryQueue(1000),
        val historyBiomesCurrent: ObservableMutableReference<BiomesHistoryItem?> = ref(null),
        val historyBiomesForwardQueue: HistoryQueue<BiomesHistoryItem> = HistoryQueue(1000)) {

    private val valueModifiedListener: (Any?, Any?) -> Unit = { old, new ->
        if (old != new) {
            isModifiedSinceSave.value = true
        }
    }

    private val queueModifiedListener: (ModificationEvent<Any?>) -> Unit = {
        isModifiedSinceSave.value = true
    }

    init {
        historyRegionsBackQueue.addListener(queueModifiedListener)
        historyRegionsCurrent.addListener(valueModifiedListener)
        historyRegionsForwardQueue.addListener(queueModifiedListener)
        historySplinesBackQueue.addListener(queueModifiedListener)
        historySplinesCurrent.addListener(valueModifiedListener)
        historySplinesForwardQueue.addListener(queueModifiedListener)
        historyBiomesBackQueue.addListener(queueModifiedListener)
        historyBiomesCurrent.addListener(valueModifiedListener)
        historyBiomesForwardQueue.addListener(queueModifiedListener)
        currentState.value.regionParameters.addListener(valueModifiedListener)
        currentState.value.regionGraph.addListener(valueModifiedListener)
        currentState.value.regionMask.addListener(valueModifiedListener)
        currentState.value.regionSplines.addListener(valueModifiedListener)
        currentState.value.biomeParameters.addListener(valueModifiedListener)
        currentState.value.biomeGraph.addListener(valueModifiedListener)
        currentState.value.biomeMask.addListener(valueModifiedListener)
    }

    fun copy(): Project {
        return Project(
                file = file,
                isModifiedSinceSave = ref(isModifiedSinceSave.value),
                currentState = ref(currentState.value.copy()),
                historyRegionsBackQueue = historyRegionsBackQueue.copy(),
                historyRegionsCurrent = ref(historyRegionsCurrent.value?.copy()),
                historyRegionsForwardQueue = historyRegionsForwardQueue.copy(),
                historySplinesBackQueue = historySplinesBackQueue.copy(),
                historySplinesCurrent = ref(historySplinesCurrent.value?.copy()),
                historySplinesForwardQueue = historySplinesForwardQueue.copy(),
                historyBiomesBackQueue = historyBiomesBackQueue.copy(),
                historyBiomesCurrent = ref(historyBiomesCurrent.value?.copy()),
                historyBiomesForwardQueue = historyBiomesForwardQueue.copy()
        )
    }
}

private val PROJECT_MOD_LOCK: Lock = ReentrantLock(true)

val recentProjects = ArrayList<Pair<File, Block>>()
val recentAutosavesDivider = ref<Block?>(null)
val recentAutosaves = ArrayList<Pair<File, Block>>()
val recentProjectsDropdown = ref<DropdownList?>(null)
val recentProjectsAvailable = ref(false)
val currentProjectHasModifications = ref(false)
val currentProject = ref<Project?>(null).addListener { old, new ->
    if (old != new) {
        if (new == null) {
            currentProjectHasModifications.value = false
        } else {
            currentProjectHasModifications.value = new.isModifiedSinceSave.value
            new.isModifiedSinceSave.addListener { lastModified, nowModified ->
                if (lastModified != nowModified) {
                    currentProjectHasModifications.value = nowModified
                }
            }
        }
    }
}
val doesActiveProjectExist = ref(false)

private class RecentProjects(val recentProjects: List<String>)

fun loadRecentProjects(dialogLayer: Block, overwriteWarningReference: MutableReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, ui: UserInterface, errorHandler: ErrorDialog) {
    if (RECENT_PROJECTS_FILE.isFile && RECENT_PROJECTS_FILE.canRead()) {
        RECENT_PROJECTS_FILE.inputStream().buffered().use {
            JSON.readValue(it, RecentProjects::class.java)
        }?.recentProjects?.map(::File)?.reversed()?.forEach {
            addProjectToRecentProjects(it, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
        }
    }
}

fun loadRecentAutosaves(dialogLayer: Block, overwriteWarningReference: MutableReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, ui: UserInterface, errorHandler: ErrorDialog) {
    val autosaveDir = preferences.autosaveDir
    if (autosaveDir.isDirectory && autosaveDir.canRead()) {
        autosaveDir.listFiles { file ->
            file.isFile && file.name.matches(Regex("autosave-\\d\\.wkp"))
        }.sortedBy { it.lastModified() }.forEach {
            addAutosaveToRecentAutosaves(it, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
        }
    }
}

fun saveRecentProjects() {
    try {
        RECENT_PROJECTS_FILE.outputStream().buffered().use {
            JSON.writeValue(it, RecentProjects(recentProjects.map { it.first.canonicalPath }))
        }
    } catch (e: Exception) {
        log.error("Error writing recent projects file.")
    }
}

fun addProjectToRecentProjects(file: File?, dialogLayer: Block, overwriteWarningReference: MutableReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, ui: UserInterface, errorHandler: ErrorDialog) {
    val finalFile = file ?: return
    sync {
        val projectPath = finalFile.canonicalPath
        val showPath = if (projectPath.length < 55) {
            projectPath
        } else {
            "${projectPath.substring(0, 25)} ... ${projectPath.substring(projectPath.length - 25)}"
        }
        var index = -1
        recentProjects.forEachIndexed { i, (first) ->
            if (first == finalFile) {
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
                    val openedProject = openProject(finalFile, dialogLayer, ui)
                    if (openedProject != null) {
                        currentProject.value = openedProject
                        afterProjectOpen()
                    }
                } catch (e: JsonParseException) {
                    errorHandler.displayErrorMessage("The selected file is not a valid project.")
                } catch (e: IOException) {
                    errorHandler.displayErrorMessage("Unable to read from the selected file while trying to open project.")
                } catch (e: Exception) {
                    errorHandler.displayErrorMessage("Encountered an unexpected error while trying to open project.")
                }
            }
            if (currentProject.value != null && currentProjectHasModifications.value) {
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
            recentProjects.add(0, Pair(finalFile, projectBlock))
            recentProjectsDropdown.value?.moveItemToIndex(projectBlock, 0)
        }
        if (recentProjects.size > 10) {
            val pair = recentProjects.removeAt(recentProjects.size - 1)
            recentProjectsDropdown.value?.removeItem(pair.second)
        }
        recentProjectsAvailable.value = recentProjects.isNotEmpty() || recentAutosaves.isNotEmpty()
        recentAutosavesDivider.value?.isVisible = recentProjects.isNotEmpty() && recentAutosaves.isNotEmpty()
    }
}

fun addAutosaveToRecentAutosaves(file: File?, dialogLayer: Block, overwriteWarningReference: MutableReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, ui: UserInterface, errorHandler: ErrorDialog) {
    val finalFile = file ?: return
    sync {
        val projectPath = finalFile.canonicalPath
        val showPath = if (projectPath.length < 55) {
            projectPath
        } else {
            "${projectPath.substring(0, 25)} ... ${projectPath.substring(projectPath.length - 25)}"
        }
        var index = -1
        recentAutosaves.forEachIndexed { i, (first) ->
            if (first == finalFile) {
                index = i
            }
        }
        if (index > -1) {
            val pair = recentAutosaves.removeAt(index)
            recentProjectsDropdown.value?.removeItem(pair.second)
        }
        val projectBlock = recentProjectsDropdown.value?.menuItem(showPath) {
            val openFun = {
                try {
                    val openedProject = openProject(finalFile, dialogLayer, ui)
                    openedProject?.file = null
                    if (openedProject != null) {
                        currentProject.value = openedProject
                        afterProjectOpen()
                    }
                } catch (e: JsonParseException) {
                    errorHandler.displayErrorMessage("The selected file is not a valid project.")
                } catch (e: IOException) {
                    errorHandler.displayErrorMessage("Unable to read from the selected file while trying to open project.")
                } catch (e: Exception) {
                    errorHandler.displayErrorMessage("Encountered an unexpected error while trying to open project.")
                }
            }
            if (currentProject.value != null && currentProjectHasModifications.value) {
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
            recentAutosaves.add(0, Pair(finalFile, projectBlock))
            recentProjectsDropdown.value?.moveItemToIndex(projectBlock, if (recentProjects.isEmpty()) 0 else recentProjects.size + 1)
        }
        if (recentAutosaves.size > 10) {
            val pair = recentAutosaves.removeAt(recentAutosaves.size - 1)
            recentProjectsDropdown.value?.removeItem(pair.second)
        }
        recentProjectsAvailable.value = recentProjects.isNotEmpty() || recentAutosaves.isNotEmpty()
        recentAutosavesDivider.value?.isVisible = recentProjects.isNotEmpty() && recentAutosaves.isNotEmpty()
    }
}

fun clearRecentProjects() {
    doOnMainThread {
        sync {
            recentProjects.forEach {
                recentProjectsDropdown.value?.removeItem(it.second)
            }
            recentProjects.clear()
            recentProjectsAvailable.value = false
        }
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
            ui.ignoreInput = true
            try {
                exportProjectFile(project, file)
                doOnMainThread {
                    addProjectToRecentProjects(file, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                }
                return true
            } finally {
                dialogLayer.isVisible = false
                ui.ignoreInput = false
            }
        }
    }
    return false
}

fun autosave(project: Project?, dialogLayer: Block, overwriteWarningReference: MutableReference<String>, overwriteWarningDialog: Block, dialogCallback: MutableReference<() -> Unit>, ui: UserInterface, errorHandler: ErrorDialog) {
    if (project != null) {
        val folder = preferences.autosaveDir
        folder.mkdirs()
        val nextAutosave = (preferences.windowState?.autoSaveIndex ?: 0) % 10
        preferences.windowState?.autoSaveIndex = nextAutosave + 1
        val file = File(folder, "autosave-$nextAutosave.wkp")
        exportProjectFileBackground(project.copy(), file)
        doOnMainThread {
            addAutosaveToRecentAutosaves(file, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
        }
    }
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
                doOnMainThread {
                    addProjectToRecentProjects(actualFile, dialogLayer, overwriteWarningReference, overwriteWarningDialog, dialogCallback, ui, errorHandler)
                    updateTitle(titleText, project)
                }
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
        log.error("Unexpected error opening project.", e)
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

fun updateTitle(titleText: DynamicTextReference, new: Project?, modified: Boolean = false) {
    val name = if (new == null) {
        "No project"
    } else {
        new.file?.canonicalPath ?: "New unsaved project *"
    }
    val showPath = if (name.length < 55) {
        if (modified) {
            "$name *"
        } else {
            name
        }
    } else {
        if (modified) {
            "${name.substring(0, 25)} ... ${name.substring(name.length - 25)} *"
        } else {
            "${name.substring(0, 25)} ... ${name.substring(name.length - 25)}"
        }
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