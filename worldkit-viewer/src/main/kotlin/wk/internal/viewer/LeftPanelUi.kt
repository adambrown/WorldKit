package wk.internal.viewer

import wk.internal.ui.*
import wk.internal.ui.style.*
import wk.internal.ui.widgets.*
import wk.api.*
import wk.internal.application.MainThread.doOnMainThread
import wk.internal.ext.functionNameToText
import wk.internal.application.ProjectPaths
import wk.internal.application.KotlinScriptLoader
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

private val leftPanelLabelShrinkGroup = hShrinkGroup()
private val scriptsBlock = ref(NO_BLOCK)
private val outputsBlock = ref(NO_BLOCK)

fun Block.leftPanel(ui: UserInterface, dialogLayer: Block): Block {
    return hDragWidthPanel(initialWidth = 370.0f, minimumExtraWidth = 250.0f, borderShape = SHAPE_BORDER_ONLY) {
        tabPanel(
                "Project" to projectPanelBuilder(ui, dialogLayer),
                "Viewport" to viewportControlPanelBuilder(ui, dialogLayer))
    }
}

private val mainScript: ObservableMutableReference<Any?> = ref(null)

private fun projectPanelBuilder(ui: UserInterface, dialogLayer: Block): Block.() -> Unit {
    return {
        vScrollPanel(onWindowResize) { scroller ->
            vExpandPanel("Project", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                vFileRowWithReload(projectFile, LARGE_ROW_HEIGHT, text("Project:"), leftPanelLabelShrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "json") {
                    ProjectPaths.fromFile(projectFile.reference.value).run {
                        val (script, executableFunctions, outputs) = doWithProgressMessage("Loading project", "", ui, dialogLayer, generatingMessageBlock, generatingPrimaryMessage, generatingSecondaryMessage) {
                            val loader = KotlinScriptLoader(projectDirectory.absolutePath)
                            val script: Any = loader.executeKts(resolvePath(projectData.scriptFile))
                            val executableFunctions = ArrayList<Pair<KFunction<*>, Int>>()
                            val outputs = ArrayList<Pair<KFunction<*>, Int>>()
                            script.javaClass.declaredMethods.forEach { func ->
                                if (func.kotlinFunction != null
                                        && func.canAccess(script)
                                        && func.parameterCount == 0) {
                                    val execAnn = func.annotations.find { it is Executable } as? Executable
                                    if (execAnn != null) {
                                        executableFunctions.add(func.kotlinFunction!! to execAnn.index)
                                    }
                                }
                                if (func.kotlinFunction != null
                                        && func.canAccess(script)
                                        && func.parameterCount == 0) {
                                    val outputAnn = func.annotations.find { it is Output } as? Output
                                    if (outputAnn != null) {
                                        if (TerrainDisplayData::class.java.isAssignableFrom(func.returnType)
                                                || ImageDisplayData::class.java.isAssignableFrom(func.returnType)
                                                || IndexDisplayData::class.java.isAssignableFrom(func.returnType)) {
                                            outputs.add(func.kotlinFunction!! to outputAnn.index)
                                        }
                                    }
                                }
                            }
                            Triple(script, executableFunctions.sortedBy { it.second }.map { it.first }, outputs.sortedBy { it.second }. map { it.first })
                        }
                        mainScript.value = script
                        doOnMainThread {
                            scriptsBlock.value.layoutChildren.clear()
                            scriptsBlock.value.renderChildren.clear()
                            executableFunctions.forEach {
                                scriptsBlock.value.vButtonRow(LARGE_ROW_HEIGHT, HorizontalAlignment.LEFT) {
                                    val humanName = it.name.functionNameToText()
                                    button(text(humanName.capitalize()), NORMAL_TEXT_BUTTON_STYLE) {
                                        doWithProgressMessage("Running $humanName", "Press ESC to cancel.", ui, dialogLayer, generatingMessageBlock, generatingPrimaryMessage, generatingSecondaryMessage) {
                                            val localScript = mainScript.value
                                            if (localScript != null) it.call(localScript)
                                        }
                                    }
                                }
                            }
                            outputsBlock.value.layoutChildren.clear()
                            outputsBlock.value.renderChildren.clear()
                            outputs.forEach {
                                outputsBlock.value.vButtonRow(LARGE_ROW_HEIGHT, HorizontalAlignment.LEFT) {
                                    val humanName = it.name.functionNameToText()
                                    button(text(humanName.capitalize()), NORMAL_TEXT_BUTTON_STYLE) {
                                        doWithProgressMessage("Preparing to display $humanName", "Press ESC to cancel.", ui, dialogLayer, generatingMessageBlock, generatingPrimaryMessage, generatingSecondaryMessage) {
                                            val localScript = mainScript.value
                                            if (localScript != null) {
                                                when (val output = it.call(localScript)) {
                                                    is TerrainDisplayData -> showTerrain(output)
                                                    is ImageDisplayData -> showImage(output)
                                                    is IndexDisplayData -> showIndex(output)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            System.gc()
                        }
                    }
                }
            }
            vExpandPanel("Scripts", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                scriptsBlock.value = this
            }
            vExpandPanel("Outputs", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                outputsBlock.value = this
            }
        }
    }
}

private fun viewportControlPanelBuilder(ui: UserInterface, dialogLayer: Block): Block.() -> Unit {
    return {
        vScrollPanel(onWindowResize) { scroller ->
            block {
                vSizing = Sizing.SHRINK
                layout = Layout.VERTICAL
                canOverflow = false
                val shrinkGroup = hShrinkGroup()

                fun Block.floatSlider(reference: ObservableMutableReference<Float>, label: String, min: Float, max: Float, valueSize: Int = 5, toString: (Float) -> String = { it.toString() }) {
                    vSliderWithValueRow(value = reference, valueSize = valueSize, textStyle = TEXT_STYLE_NORMAL, height = LARGE_ROW_HEIGHT, label = text("$label:"), shrinkGroup = shrinkGroup, gap = MEDIUM_SPACER_SIZE, function = linearClampedScaleFunction(min, max), inverseFunction = linearClampedScaleFunctionInverse(min, max), toString = toString)
                }

                fun Block.colorPanel(colorRef: Array<ObservableMutableReference<Float>>, min: Float = 0.0f, max: Float = 1.0f, indent: Float = 0.0f, panelName: String = "Color") {
                    fun Block.colorChannelSlider(channel: Int, label: String) {
                        floatSlider(colorRef[channel], label, min, max)
                    }
                    vExpandPanel(panelName, indent = indent, scroller = scroller) {
                        colorChannelSlider(0, "Red")
                        colorChannelSlider(1, "Green")
                        colorChannelSlider(2, "Blue")
                    }
                }

                fun Block.materialPanel(materialParams: Array<ObservableMutableReference<Float>>, min: Float = 0.0f, max: Float = 1.0f, indent: Float = 0.0f) {
                    fun Block.colorChannelSlider(channel: Int, label: String) {
                        floatSlider(materialParams[channel], label, min, max)
                    }
                    vExpandPanel("Surface", indent = indent, scroller = scroller) {
                        colorChannelSlider(0, "Metallic")
                        colorChannelSlider(1, "Roughness")
                        colorChannelSlider(2, "Specular")
                    }
                }

                vExpandPanel("Toggles", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                    vToggleRow(waterPlaneOn, LARGE_ROW_HEIGHT, text("Water:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                    vToggleRow(heightColorsOn, LARGE_ROW_HEIGHT, text("Colors:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                    vToggleRow(riversOn, LARGE_ROW_HEIGHT, text("Rivers:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                    vToggleRow(skyOn, LARGE_ROW_HEIGHT, text("Sky:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                    vToggleRow(fogOn, LARGE_ROW_HEIGHT, text("Fog:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                    vToggleRow(perspectiveOn, LARGE_ROW_HEIGHT, text("Perspective:"), shrinkGroup, MEDIUM_SPACER_SIZE)
                }
                vExpandPanel("Scale", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                    vSliderRow(heightMapScaleFactor, LARGE_ROW_HEIGHT, text("Height scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, heightScaleFunction, heightScaleFunctionInverse)
                    vSliderRow(waterShaderParams.level, LARGE_ROW_HEIGHT, text("Water level:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.003f, 0.175f), linearClampedScaleFunctionInverse(0.003f, 0.175f))
                }
                vExpandPanel("Light", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                    val iblFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
                    vFileRow(iblFile, LARGE_ROW_HEIGHT, text("IBL file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "ibl") {
                        meshViewport.loadIbl(iblFile.reference.value)
                    }
                    colorPanel(lightColor, 0.0f, 10.0f)
                    vExpandPanel("Direction", scroller = scroller) {
                        floatSlider(lightElevation, "Elevation", 0.5f, 85.0f)
                        floatSlider(lightHeading, "Heading", -180.0f, 180.0f)
                    }
                    vExpandPanel("Ambient", scroller = scroller) {
                        floatSlider(indirectIntensity, "Indirect light", 0.0f, 5.0f)
                        floatSlider(occlusionPower, "Occlusion power", 0.01f, 10.0f)
                    }
                }
                vExpandPanel("Land material", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                    val gradientFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
                    vFileRow(gradientFile, LARGE_ROW_HEIGHT, text("Gradient file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png") {
                        meshViewport.loadDemGradient(gradientFile.reference.value)
                    }
                    floatSlider(gradientOffset, "Gradient offset", -0.5f, 0.5f)
                    colorPanel(baseColor)
                    materialPanel(materialParams)
                }
                vExpandPanel("Water material", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                    colorPanel(waterShaderParams.color, panelName = "Deep color")
                    colorPanel(waterShaderParams.shallowColor, panelName = "Shallow color")
                    floatSlider(waterShaderParams.depthPower, "Depth power", 0.005f, 0.35f)
                    materialPanel(waterShaderParams.materialParams)
                    vExpandPanel("Normal offsets", scroller = scroller) {
                        val highPrecisionToString = { f: Float ->
                            "%.6f".format(f)
                        }
                        floatSlider(waterShaderParams.normalOffsets[0], "0", 0.0f, 0.5f, 8, highPrecisionToString)
                        floatSlider(waterShaderParams.normalOffsets[1], "1", 0.0f, 0.2f, 8, highPrecisionToString)
                        floatSlider(waterShaderParams.normalOffsets[2], "2", 0.0f, 0.1f, 8, highPrecisionToString)
                        floatSlider(waterShaderParams.normalOffsets[3], "3", 0.0f, 0.01f, 8, highPrecisionToString)
                        floatSlider(waterShaderParams.normalOffsets[4], "4", 0.0f, 0.005f, 8, highPrecisionToString)
                    }
                    vExpandPanel("Normal strengths", scroller = scroller) {
                        floatSlider(waterShaderParams.normalStrengths[0], "0", 0.0f, 3.0f)
                        floatSlider(waterShaderParams.normalStrengths[1], "1", 0.0f, 3.0f)
                        floatSlider(waterShaderParams.normalStrengths[2], "2", 0.0f, 3.0f)
                        floatSlider(waterShaderParams.normalStrengths[3], "3", 0.0f, 10.0f)
                    }
                    vExpandPanel("Fades", scroller = scroller) {
                        floatSlider(waterShaderParams.fadeStarts[0], "Start 0", 0.0f, 50.0f)
                        floatSlider(waterShaderParams.fadeEnds[0], "End 0", 0.0f, 150.0f)
                        floatSlider(waterShaderParams.fadeStarts[1], "Start 1", 0.0f, 3000.0f)
                        floatSlider(waterShaderParams.fadeEnds[1], "End 1", 0.0f, 3000.0f)
                        floatSlider(waterShaderParams.fadeStarts[2], "Start 2", 0.0f, 3000.0f)
                        floatSlider(waterShaderParams.fadeEnds[2], "End 2", 0.0f, 3000.0f)
                    }
                }
                vExpandPanel("Fog", indent = LARGE_SPACER_SIZE, scroller = scroller) {
                    colorPanel(fogShaderParams.color)
                    vExpandPanel("Density", scroller = scroller) {
                        floatSlider(fogShaderParams.atmosphericFogDensity, "Atmosphere density", 0.0f, 0.2f)
                        floatSlider(fogShaderParams.exponentialFogDensity, "Fog density", 0.0f, 0.2f)
                        floatSlider(fogShaderParams.exponentialFogHeightFalloff, "Fog falloff", 0.0f, 1.0f)
                        floatSlider(fogShaderParams.fogHeightClampPower, "Fog clamp", -100.0f, 100.0f)
                    }
                }
            }
        }
    }
}
