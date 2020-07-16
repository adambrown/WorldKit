package wk.internal.application

import java.io.File
import java.io.IOException
import java.util.*

private val directories = Directories(System.getProperties(), System.getenv())

val appDir = File(directories.appDir!!, "WorldKit").ensure()
val cacheDir = File(appDir, "Cache").ensure()
val configDir = File(appDir, "Config").ensure()
val userDir = directories.userHome!!.ensure()

private fun File.ensure(): File {
    if (this.isDirectory) {
        return this
    }
    if (!mkdirs()) {
        throw IOException("Failed to create required directory: $path")
    }
    return this
}

private class Directories(
        private val systemProperties: Properties,
        private val environment: Map<String, String>) {

    val appDir: File?
        get() = when (os) {
            OSKind.Windows -> getEnv("LOCALAPPDATA")?.toFile() ?: temp
            OSKind.OSX -> userHome?.resolve("Library/Caches")
            OSKind.Unix -> getEnv("XDG_CACHE_HOME")?.toFile() ?: userHome?.resolve(".cache")
            OSKind.Unknown -> userHome?.resolve(".cache")
        }

    val userHome: File?
        get() = getProperty("user.home")?.toFile()

    val temp: File?
        get() = getProperty("java.io.tmpdir")?.toFile()

    private enum class OSKind {
        Windows,
        OSX,
        Unix,
        Unknown
    }

    private val os: OSKind
        get() = getProperty("os.name")?.toLowerCase().let { name ->
            when {
                name == null -> OSKind.Unknown
                name.startsWith("windows") -> OSKind.Windows
                name.startsWith("mac os") -> OSKind.OSX
                name.contains("unix") -> OSKind.Unix
                name.startsWith("linux") -> OSKind.Unix
                name.contains("bsd") -> OSKind.Unix
                name.startsWith("irix") -> OSKind.Unix
                name.startsWith("mpe/ix") -> OSKind.Unix
                name.startsWith("aix") -> OSKind.Unix
                name.startsWith("hp-ux") -> OSKind.Unix
                name.startsWith("sunos") -> OSKind.Unix
                name.startsWith("sun os") -> OSKind.Unix
                name.startsWith("solaris") -> OSKind.Unix
                else -> OSKind.Unknown
            }
        }

    private fun getProperty(name: String) = systemProperties.getProperty(name).nullIfBlank()

    private fun getEnv(name: String) = environment[name].nullIfBlank()

    private fun String.toFile() = File(this)

    private fun String?.nullIfBlank() = if (this == null || this.isBlank()) null else this
}