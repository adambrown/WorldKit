package com.grimfox.gec

import com.grimfox.gec.ui.widgets.Block
import com.grimfox.gec.ui.widgets.DropdownList
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

private fun <T> sync(codeBlock: () -> T): T {
    PROJECT_MOD_LOCK.lock()
    try {
        return codeBlock()
    } finally {
        PROJECT_MOD_LOCK.unlock()
    }
}