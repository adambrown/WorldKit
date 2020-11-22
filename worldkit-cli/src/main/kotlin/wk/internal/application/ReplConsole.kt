package wk.internal.application

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import wk.api.*
import wk.internal.ext.functionNameToText
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

object ReplConsole {

    @Volatile
    private var running = true

    private val mainScript: ObservableMutableReference<Any?> = ref(null)
    private val scripts = LinkedHashMap<String, () -> Unit>()
    private var loadedProject: String? = null

    init {
        getFlowGraphSpec(0)
    }

    @JvmStatic
    fun mainRepl(vararg args: String) {
        val uiThread = Thread { runConsole(*args) }
        uiThread.isDaemon = false
        uiThread.start()
    }

    private fun runConsole(vararg args: String) {
        val preparedLines = args.joinToString(" ") { it.trim() }.split(';')
        preparedLines.forEach {
            handleLine(it)
        }
        print("\n===[ Starting WorldKit console ]===\n> ")
        while (running) {
            val line = readLine()!!
            handleLine(line)
            print("> ")
        }
        finishIoTasksOnShutdown()
    }

    private fun handleLine(line: String) {
        try {
            val parts = line.trim().split(Regex("\\s+"), 2)
            val command = parts.first()
            val args = if (parts.size == 2) {
                parts.last()
            } else {
                ""
            }
            when (command) {
                "quit" -> running = false
                "load" -> loadProject(args)
                "reload" -> loadProject(loadedProject ?: "")
                "list" -> listScripts()
                "waitIo" -> waitIo()
                "run" -> runCancellableTask(args)
                else -> runCancellableTask(command)
            }
        } catch (t: Throwable) {
            if (t.message != null) {
                println(t.message)
            } else {
                t.printStackTrace()
            }
        }
    }

    private fun listScripts() {
        if (scripts.isEmpty()) {
            println("  No functions loaded")
        } else {
            println("  Executable functions:")
            scripts.keys.forEach {
                println("    $it")
            }
        }
    }

    private fun runCancellableTask(scriptName: String) {
        val script = scripts[scriptName]
        if (script == null) {
            println("  Unrecognized command: $scriptName")
            return
        }
        runCancellableTask(script)
    }

    private fun runCancellableTask(script: () -> Unit) {
        runBlocking {
            val task = async {
                doWithCancellationSupport {
                    script()
                }
            }
            val waiter = async(IO) {
                val buffer = ByteArrayOutputStream(7)
                while (task.isActive && this.isActive) {
                    if (System.`in`.available() > 0) {
                        val byte = System.`in`.read()
                        if (byte == -1) break
                        if (byte.toChar() == '\n') {
                            val token = String(buffer.toByteArray())
                            buffer.reset()
                            if (token.trim().equals("cancel", true)) {
                                cancelRequestCount++
                                break
                            }
                        }
                        buffer.write(byte)
                    }
                }
            }
            task.await()
            if (waiter.isActive) {
                waiter.cancel()
            }
        }
    }

    private fun loadProject(projectPath: String) {
        if (projectPath.isBlank()) {
            println("  Specify project to load")
            return
        }
        println("  Loading $projectPath")
        ProjectPaths.fromFile(projectPath).run {
            scripts.clear()
            val (script, executableFunctions) = run {
                val loader = KotlinScriptLoader(projectDirectory.absolutePath)
                val script = loader.executeKts(resolvePath(projectData.scriptFile))
                val executableFunctions = ArrayList<Pair<KFunction<*>, Int>>()
                script.javaClass.declaredMethods.forEach { func ->
                    if (func.kotlinFunction != null
                            && func.canAccess(script)
                            && func.parameterCount == 0) {
                        val execAnn = func.annotations.find { it is Executable } as? Executable
                        val outputAnn = func.annotations.find { it is Output } as? Output
                        if (execAnn != null) {
                            executableFunctions.add(func.kotlinFunction!! to execAnn.index)
                        } else if (outputAnn != null) {
                            executableFunctions.add(func.kotlinFunction!! to outputAnn.index)
                        }
                    }
                }
                script to executableFunctions.sortedBy { it.second }.map { it.first }
            }
            mainScript.value = script
            executableFunctions.forEach {
                val humanName = it.name.functionNameToText()
                scripts[it.name] = {
                    println("  Running $humanName")
                    try {
                        val localScript = mainScript.value
                        if (localScript != null) {
                            timeIt("  Finished running in") { it.call(localScript) }
                        }
                    } catch (t: Throwable) {
                        handleTaskError(t)
                    }
                }
            }
            loadedProject = projectPath
            System.gc()
            println("  Finished loading")
        }
    }

    private fun waitIo() {
        println("  Waiting for background IO tasks to complete")
        try {
            timeIt("  IO tasks completed in") { waitForBackgroundIo() }
        } catch (t: Throwable) {
            handleTaskError(t)
        }
    }

    private fun handleTaskError(t: Throwable) {
        var c: Throwable? = t
        while (c?.cause != null) c = c.cause
        if (c is CancellationException) {
            println("  Cancelled")
        } else {
            if (c?.message != null) {
                println("  Error executing task.\n  ${c.message}")
            } else {
                t.printStackTrace()
            }
        }
    }
}