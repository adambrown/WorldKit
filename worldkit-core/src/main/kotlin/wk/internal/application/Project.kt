package wk.internal.application

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap

private val projectStringLookups = ConcurrentHashMap<String, String>()
private val projectClassLookups = ConcurrentHashMap<Class<*>, String>()

fun registerProjectWithId(uuid: String, projectPath: String) {
    projectStringLookups[uuid] = projectPath
}

@Suppress("unused")
fun Any.bindProject(uuid: String) {
    projectClassLookups[this.javaClass] = projectStringLookups[uuid] ?: throw IllegalArgumentException("No project registered for $uuid")
}

fun Any.lookupProjectPath(): String {
    return projectClassLookups[this.javaClass] ?: throw IllegalArgumentException("No project registered for ${this.javaClass.canonicalName}")
}

class ProjectPaths private constructor(
        val projectDirectory: File,
        val projectData: ProjectData) {

    companion object {

        fun fromFile(filePath: String): ProjectPaths {
            val absolutePath = Paths.get(filePath).toAbsolutePath()
            val projectFile = absolutePath.toFile()
            val projectDirectory = absolutePath.parent.toFile()
            val projectData: ProjectData
            if (projectFile.isFile && projectDirectory.isDirectory) {
                projectData = Json.decodeFromString(ProjectData.serializer(), projectFile.readText())
            } else {
                throw IllegalArgumentException("Project file: $filePath could not be found.")
            }
            return ProjectPaths(projectDirectory, projectData)
        }
    }

    fun resolvePath(fileName: String): String {
        return resolveFile(fileName).absolutePath
    }

    private fun resolveFile(fileName: String): File {
        return getProjectRelativeFile(projectDirectory, fileName)
    }

    private fun getProjectRelativeFile(projectDirectory: File, fileName: String): File {
        val file = File(fileName)
        if (!file.isAbsolute) {
            return File(projectDirectory, fileName)
        }
        return file
    }
}

@Serializable
data class ProjectData(val scriptFile: String = "src/main/resources/Main.kts")
