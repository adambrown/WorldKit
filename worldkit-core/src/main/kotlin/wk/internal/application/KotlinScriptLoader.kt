package wk.internal.application

import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import java.io.File
import java.net.URLClassLoader
import java.util.*
import javax.script.*
import kotlin.collections.HashSet

class KotlinScriptLoader(private val projectPath: String) {

    companion object {
        val packagePattern = Regex("\\s*package\\s+[^\n]+")
        val importPattern = Regex("\\s*import\\s+[^\n]+")
        val dependsOnPattern = Regex("\\s*@file\\s*:\\s*DependsOn\\(\\s*\"([^\"]+)\"\\s*\\)\\s*")
        val executablePattern = Regex("\\s*(@Executable)(\\s*|(\\s+[^(\\s].*))")
        val outputPattern = Regex("\\s*(@Output)(\\s*|(\\s+[^(\\s].*))")
    }

    private val engine: ScriptEngine = ScriptEngineManager(URLClassLoader(emptyArray(), this.javaClass.classLoader)).getEngineByExtension("kts")

    private val uuid = UUID.randomUUID().toString()

    private val projectImport = "import wk.internal.application.*"

    private val projectBind = """bindProject("$uuid")"""

    init {
        setIdeaIoUseFallback()
        registerProjectWithId(uuid, projectPath)
    }

    fun executeKts(fileName: String): Any {
        try {
            val (script, dependencies) = prepareAndBindScript(fileName)
            recursiveLoadDependencies(dependencies)
            return engine.eval("$script\n\nthis\n") ?: throw ScriptException("Script did not evaluate to an object")
        } catch (t: Throwable) {
            when (t) {
                is ScriptException -> {
                    throw t
                }
                is Exception -> {
                    throw ScriptException(t)
                }
                else -> {
                    throw ScriptException(t.message)
                }
            }
        }
    }

    private fun loadKtInclude(scriptContent: String) {
        try {
            engine.eval(scriptContent)
        } catch (t: Throwable) {
            when (t) {
                is ScriptException -> {
                    throw t
                }
                is Exception -> {
                    throw ScriptException(t)
                }
                else -> {
                    throw ScriptException(t.message)
                }
            }
        }
    }

    private fun recursiveLoadDependencies(dependencies: List<String>) {
        val toLoad = HashMap<String, Pair<String, List<String>>>()
        var currentLevel = HashSet<String>(dependencies)
        var nextLevel = HashSet<String>()
        while (currentLevel.isNotEmpty()) {
            currentLevel.forEach {
                if (!toLoad.contains(it)) {
                    val scriptAndDependencies = prepareAndBindScript(it)
                    toLoad[it] = scriptAndDependencies
                    nextLevel.addAll(scriptAndDependencies.second)
                }
            }
            val temp = currentLevel
            currentLevel = nextLevel
            temp.clear()
            nextLevel = temp
        }
        val loaded = HashSet<String>()
        while (toLoad.isNotEmpty()) {
            var loadedSomething = false
            toLoad.keys.toList().forEach {
                val (script, fileDeps) = toLoad[it]!!
                if (loaded.containsAll(fileDeps)) {
                    loadKtInclude(script)
                    loaded.add(it)
                    loadedSomething = true
                    toLoad.remove(it)
                }
            }
            if (!loadedSomething) {
                val builder = StringBuilder("Circular script dependencies detected. The following scripts could not be loaded:")
                toLoad.keys.forEach {
                    builder.append("\n  ").append(it)
                }
                throw ScriptException(builder.toString())
            }
        }
    }

    private fun prepareAndBindScript(fileName: String): Pair<String, List<String>> {
        val includeText = File(fileName).readLines()
        val outputLines = ArrayList<String>(includeText.size + 2)
        var insertProjectBindAt = 0
        val dependencies = arrayListOf<String>()
        var doneWithPackage = false
        for (i in includeText.indices) {
            val line = includeText[i]
            if (!doneWithPackage && line.matches(packagePattern)) {
                outputLines.add(line)
                outputLines.add(projectImport)
                doneWithPackage = true
                insertProjectBindAt = outputLines.size
            } else if (line.matches(importPattern)) {
                if (!doneWithPackage) {
                    outputLines.add(projectImport)
                    doneWithPackage = true
                }
                outputLines.add(line)
                insertProjectBindAt = outputLines.size
            } else if (line.matches(executablePattern)) {
                if (!doneWithPackage) {
                    outputLines.add(projectImport)
                    doneWithPackage = true
                }
                outputLines.add(line.replaceFirst("@Executable", "@Executable($i)"))
            } else if (line.matches(outputPattern)) {
                if (!doneWithPackage) {
                    outputLines.add(projectImport)
                    doneWithPackage = true
                }
                outputLines.add(line.replaceFirst("@Output", "@Output($i)"))
            } else if (!doneWithPackage) {
                val matcher = dependsOnPattern.matchEntire(line)
                if (matcher != null) {
                    val path = matcher.groups[1]?.value
                    if (path != null) {
                        dependencies.add("${projectPath}/src/main/kotlin/$path")
                    }
                } else {
                    outputLines.add(line)
                }
            } else {
                outputLines.add(line)
            }
        }
        outputLines.add(insertProjectBindAt, projectBind)
        if (!doneWithPackage) {
            outputLines.add(insertProjectBindAt, projectImport)
        }
        return (outputLines).joinToString("\n", postfix = "\n") to dependencies
    }
}