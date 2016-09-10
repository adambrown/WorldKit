package com.grimfox.gec

import io.airlift.airline.Cli
import io.airlift.airline.Command
import io.airlift.airline.Help
import org.reflections.Reflections
import org.reflections.util.ConfigurationBuilder
import java.io.File

object Main {

    val workingDir = File(".")

    @JvmStatic
    fun main(vararg args: String) {
        com.grimfox.gec.Main.Launcher(
                listOf("com.grimfox.gec.command"),
                listOf("com.grimfox.gec.input"),
                listOf("com.grimfox.gec.generator"),
                listOf("com.grimfox.gec.filter"),
                listOf("com.grimfox.gec.output"),
                listOf("com.grimfox.gec.pipeline")
        ).launch(*args)
    }

    internal class Launcher(val commandPackages: List<String>,
                            val inputPackages: List<String>,
                            val generatorPackages: List<String>,
                            val filterPackages: List<String>,
                            val outputPackages: List<String>,
                            val pipelinePackages: List<String>) {

        internal fun launch(vararg args: String) {
            val commands = getCommandsFromPackages(commandPackages)
            val inputs = getCommandsFromPackages(inputPackages)
            val generators = getCommandsFromPackages(generatorPackages)
            val filters = getCommandsFromPackages(filterPackages)
            val outputs = getCommandsFromPackages(outputPackages)
            val pipelines = getCommandsFromPackages(pipelinePackages)
            val help = Help::class.java
            val cliBuilder = Cli.builder<Runnable>("gec")
                    .withDescription("Game Environment Generator. A toolkit for procedurally generating game-friendly environments.")
                    .withDefaultCommand(help)
                    .withCommands(help)
            cliBuilder.withCommands(commands.map { toRunnableClass(it) }.filterNotNull())
            cliBuilder.withGroup("input")
                    .withDescription("Input a source for processing.")
                    .withDefaultCommand(help)
                    .withCommands(inputs.map { toRunnableClass(it) }.filterNotNull())
            cliBuilder.withGroup("generate")
                    .withDescription("Generate a source for processing.")
                    .withDefaultCommand(help)
                    .withCommands(generators.map { toRunnableClass(it) }.filterNotNull())
            cliBuilder.withGroup("filter")
                    .withDescription("Execute a filter on a source.")
                    .withDefaultCommand(help)
                    .withCommands(filters.map { toRunnableClass(it) }.filterNotNull())
            cliBuilder.withGroup("output")
                    .withDescription("Output data in a usable format.")
                    .withDefaultCommand(help)
                    .withCommands(outputs.map { toRunnableClass(it) }.filterNotNull())
            cliBuilder.withGroup("pipeline")
                    .withDescription("Run a preset pipeline which includes multiple tasks.")
                    .withDefaultCommand(help)
                    .withCommands(pipelines.map { toRunnableClass(it) }.filterNotNull())
            cliBuilder.build().parse(*args).run()
        }

        private fun getCommandsFromPackages(packages: List<String>) = Reflections(ConfigurationBuilder().forPackages(*packages.toTypedArray())).getTypesAnnotatedWith(Command::class.java).filter { packages.contains(it.`package`.name) }

        private fun toRunnableClass(command: Class<*>): Class<Runnable>? {
            command.getAnnotation(Command::class.java) ?: return null
            if (Runnable::class.java.isAssignableFrom(command)) {
                return command as Class<Runnable>
            }
            return null
        }
    }
}
