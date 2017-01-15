package com.grimfox.gec

import com.grimfox.gec.ui.FileDialogs
import com.grimfox.gec.ui.JSON
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.Block
import com.grimfox.gec.ui.widgets.DropdownList
import com.grimfox.gec.ui.widgets.DynamicTextReference
import com.grimfox.gec.util.ref
import java.io.File
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class Project(
        var folder: File? = null) {


    fun open() {

    }

    fun close() {

    }
}

private val PROJECT_MOD_LOCK: Lock = ReentrantLock(true)

val recentProjects = ArrayList<Pair<File, Block>>()
val recentProjectsDropdown = ref<DropdownList?>(null)
val recentProjectsAvailable = ref(false)
val currentProject = ref<Project?>(null).listener { old, new ->
    doesActiveProjectExist.value = new != null
    if (new != null) {
        addProjectToRecentProjects(new)
        new.open()
    }
    old?.close()
}
val doesActiveProjectExist = ref(false)

fun addProjectToRecentProjects(project: Project) {
    val folder = project.folder ?: return
    sync {
        val projectPath = folder.canonicalPath
        val showPath = if (projectPath.length < 55) {
            projectPath
        } else {
            "${projectPath.substring(0, 25)} ... ${projectPath.substring(projectPath.length - 25)}"
        }
        var index = -1
        recentProjects.forEachIndexed { i, pair ->
            if (pair.first == project.folder) {
                index = i
            }
        }
        if (index > -1) {
            val pair = recentProjects.removeAt(index)
            recentProjectsDropdown.value?.removeItem(pair.second)
        }
        val projectBlock = recentProjectsDropdown.value?.menuItem(showPath) {

        }
        if (projectBlock != null) {
            recentProjects.add(0, Pair(folder, projectBlock))
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

fun saveProject(project: Project?, dialogLayer: Block, preferences: Preferences, ui: UserInterface, titleText: DynamicTextReference) {
    if (project != null) {
        val file = project.folder
        if (file == null) {
            saveProjectAs(project, dialogLayer, preferences, ui, titleText)
        } else {
            file.outputStream().buffered().use {
                JSON.writeValue(it, project)
            }
            addProjectToRecentProjects(project)
        }
    }
}

fun saveProjectAs(project: Project?, dialogLayer: Block, preferences: Preferences, ui: UserInterface, titleText: DynamicTextReference) {
    if (project != null) {
        ui.ignoreInput = true
        try {
            val saveFile = saveProjectDialog(preferences.projectDir, "wkp")
            if (saveFile != null) {
                val fullNameWithExtension = "${saveFile.name.removeSuffix(".wkp")}.wkp"
                val actualFile = File(saveFile.parentFile, fullNameWithExtension)
                actualFile.outputStream().buffered().use {
                    JSON.writeValue(it, project)
                }
                project.folder = actualFile
                addProjectToRecentProjects(project)
                updateTitle(titleText, project)
            }
            dialogLayer.isVisible = false
        } finally {
            ui.ignoreInput = false
        }
    }
}

fun updateTitle(titleText: DynamicTextReference, new: Project?) {
    val name = if (new == null) {
        "No project"
    } else {
        new.folder?.canonicalPath ?: "New unsaved project"
    }
    val showPath = if (name.length < 55) {
        name
    } else {
        "${name.substring(0, 25)} ... ${name.substring(name.length - 25)}"
    }
    titleText.reference.value = "WorldKit - $showPath"
}

private fun saveProjectDialog(defaultFolder: File, vararg filters: String): File? {
    val saveFileName = FileDialogs.saveFile(filters.joinToString(","), defaultFolder.canonicalPath)
    if (saveFileName != null && saveFileName.isNotBlank()) {
        return File(saveFileName)
    }
    return null
}

private fun <T> sync(codeBlock: () -> T): T {
    PROJECT_MOD_LOCK.lock()
    try {
        return codeBlock()
    } finally {
        PROJECT_MOD_LOCK.unlock()
    }
}