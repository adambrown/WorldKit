package com.grimfox.gec

import com.grimfox.gec.TalusAngleType.*
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.Biomes.Biome
import kotlinx.coroutines.runBlocking
import org.lwjgl.opengl.GL11
import java.io.*
import java.util.*
import javax.imageio.ImageIO
import kotlin.collections.ArrayList
import kotlin.math.*

private val STARTING_HEIGHTS_NOISE: ObservableMutableReference<Int> = ref(0)
private val STARTING_HEIGHTS_NOISE_SCALE: ObservableMutableReference<Float> = ref(0.0f)
private val STARTING_HEIGHTS_FILE = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val ELEVATION_POWER_NOISE: ObservableMutableReference<Int> = ref(0)
private val ELEVATION_POWER_NOISE_SCALE: ObservableMutableReference<Float> = ref(0.0f)
private val ELEVATION_POWER_FILE = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val SOIL_MOBILITY_NOISE: ObservableMutableReference<Int> = ref(0)
private val SOIL_MOBILITY_NOISE_SCALE: ObservableMutableReference<Float> = ref(0.0f)
private val SOIL_MOBILITY_FILE = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)

enum class TalusAngleType { LINEAR, STEPPED, NORMAL }

class CustomBiomeProperties(
        val startingHeightsNoise: ObservableMutableReference<Int> = STARTING_HEIGHTS_NOISE,
        val startingHeightsNoiseScale: ObservableMutableReference<Float> = STARTING_HEIGHTS_NOISE_SCALE,
        val startingHeightsFile: ObservableMutableReference<String> = STARTING_HEIGHTS_FILE.reference,
        val elevationPowerNoise: ObservableMutableReference<Int> = ELEVATION_POWER_NOISE,
        val elevationPowerNoiseScale: ObservableMutableReference<Float> = ELEVATION_POWER_NOISE_SCALE,
        val elevationPowerFile: ObservableMutableReference<String> = ELEVATION_POWER_FILE.reference,
        val soilMobilityNoise: ObservableMutableReference<Int> = SOIL_MOBILITY_NOISE,
        val soilMobilityNoiseScale: ObservableMutableReference<Float> = SOIL_MOBILITY_NOISE_SCALE,
        val soilMobilityFile: ObservableMutableReference<String> = SOIL_MOBILITY_FILE.reference) {

    fun reset() {
        startingHeightsNoise.value = 0
        startingHeightsNoiseScale.value = 0.0f
        startingHeightsFile.value = ""
        elevationPowerNoise.value = 0
        elevationPowerNoiseScale.value = 0.0f
        elevationPowerFile.value = ""
        soilMobilityNoise.value = 0
        soilMobilityNoiseScale.value = 0.0f
        soilMobilityFile.value = ""
    }

    fun addListener(valueModifiedListener: (Any?, Any?) -> Unit) {
        fun ObservableMutableReference<*>.checkListeners() {
            if (!listeners.contains(valueModifiedListener)) {
                addListener(valueModifiedListener)
            }
        }
        startingHeightsNoise.checkListeners()
        startingHeightsNoiseScale.checkListeners()
        startingHeightsFile.checkListeners()
        elevationPowerNoise.checkListeners()
        elevationPowerNoiseScale.checkListeners()
        elevationPowerFile.checkListeners()
        soilMobilityNoise.checkListeners()
        soilMobilityNoiseScale.checkListeners()
        soilMobilityFile.checkListeners()
    }

    fun copy(): CustomBiomeProperties {
        return CustomBiomeProperties(
                ref(startingHeightsNoise.value),
                ref(startingHeightsNoiseScale.value),
                ref(startingHeightsFile.value),
                ref(elevationPowerNoise.value),
                ref(elevationPowerNoiseScale.value),
                ref(elevationPowerFile.value),
                ref(soilMobilityNoise.value),
                ref(soilMobilityNoiseScale.value),
                ref(soilMobilityFile.value))
    }
}

class CustomBiomeTemplate(
        val name: String,
        val talusHeightMultiplier: Float,
        val largeFeaturesBlendWeight: Float,
        val largeFeaturePowerMultiplier: Float,
        val largeFeatureMobilityMultiplier: Float,
        val mediumFeaturesBlendWeight: Float,
        val mediumFeaturePowerMultiplier: Float,
        val mediumFeatureMobilityMultiplier: Float,
        val smallFeaturesBlendWeight: Float,
        val smallFeaturePowerMultiplier: Float,
        val smallFeatureMobilityMultiplier: Float,
        val talusAngleTemplate: TalusAngleTemplate) {

    fun addCustomBiome(): Biome? {
        val biomeTemplates = BIOME_TEMPLATES_REF.value
        return biomeTemplates?.addCustomBiome(toBiome(biomeTemplates), BIOME_NAMES_AS_TEXT)
    }

    fun replaceCustomBiome(oldBiome: Biome): Biome? {
        val biomeTemplates = BIOME_TEMPLATES_REF.value
        return biomeTemplates?.replaceCustomBiome(toBiome(biomeTemplates), oldBiome, BIOME_NAMES_AS_TEXT)
    }

    private fun toBiome(biomeTemplates: Biomes): Biome {
        return biomeTemplates.customBiome(
                name = name,
                talusAngles = talusAngleTemplate.buildTalusAngles(biomeTemplates),
                heightMultiplier = talusHeightMultiplier,
                largeFeaturesBlendWeight = largeFeaturesBlendWeight,
                largeFeaturePowerMultiplier = largeFeaturePowerMultiplier,
                largeFeatureMobilityMultiplier = largeFeatureMobilityMultiplier,
                mediumFeaturesBlendWeight = mediumFeaturesBlendWeight,
                mediumFeaturePowerMultiplier = mediumFeaturePowerMultiplier,
                mediumFeatureMobilityMultiplier = mediumFeatureMobilityMultiplier,
                smallFeaturesBlendWeight = smallFeaturesBlendWeight,
                smallFeaturePowerMultiplier = smallFeaturePowerMultiplier,
                smallFeatureMobilityMultiplier = smallFeatureMobilityMultiplier)
    }
}

class TalusAngleTemplate(
        val talusAngleType: TalusAngleType,
        val linearTalusMinAngle: Float,
        val linearTalusDeltaAngle: Float,
        val linearTalusJitter: Float,
        val normalTalusScale: Float,
        val normalTalusStandardDeviation: Float,
        val normalTalusMean: Float,
        val normalTalusJitter: Float,
        val steps: Array<Pair<Int, Float>>) {

    fun buildTalusAngles(biomeTemplates: Biomes): Triple<FloatArray, FloatArray, FloatArray?> {
        return when (talusAngleType) {
            LINEAR -> biomeTemplates.buildLinearTalusAngles(linearTalusMinAngle, linearTalusDeltaAngle, linearTalusJitter)
            STEPPED -> biomeTemplates.buildSteppedTalusAngles(steps)
            NORMAL -> biomeTemplates.buildNormalTalusAngles(normalTalusScale, normalTalusStandardDeviation, normalTalusMean, normalTalusJitter)
        }
    }
}

class CustomBiomeData(
        val startingHeightsNoise: Int = 0,
        val startingHeightsNoiseScale: Float = 0.0f,
        val startingHeightsFile: String = "",
        val elevationPowerNoise: Int = 0,
        val elevationPowerNoiseScale: Float = 0.0f,
        val elevationPowerFile: String = "",
        val soilMobilityNoise: Int = 0,
        val soilMobilityNoiseScale: Float = 0.0f,
        val soilMobilityFile: String = "",
        val customBiomes: List<CustomBiomeDataItem> = ArrayList()) {

    companion object {

        fun deserialize(input: DataInputStream): CustomBiomeData {
            val startingHeightsNoise = input.readInt()
            val startingHeightsNoiseScale = input.readFloat()
            val startingHeightsFile = input.readUTF()
            val elevationPowerNoise = input.readInt()
            val elevationPowerNoiseScale = input.readFloat()
            val elevationPowerFile = input.readUTF()
            val soilMobilityNoise = input.readInt()
            val soilMobilityNoiseScale = input.readFloat()
            val soilMobilityFile = input.readUTF()
            val biomeCount = input.readInt()
            val biomes = ArrayList<CustomBiomeDataItem>()
            for (i in 1..biomeCount) {
                biomes.add(CustomBiomeDataItem.deserialize(input))
            }
            return CustomBiomeData(
                    startingHeightsNoise = startingHeightsNoise,
                    startingHeightsNoiseScale = startingHeightsNoiseScale,
                    startingHeightsFile = startingHeightsFile,
                    elevationPowerNoise = elevationPowerNoise,
                    elevationPowerNoiseScale = elevationPowerNoiseScale,
                    elevationPowerFile = elevationPowerFile,
                    soilMobilityNoise = soilMobilityNoise,
                    soilMobilityNoiseScale = soilMobilityNoiseScale,
                    soilMobilityFile = soilMobilityFile,
                    customBiomes = biomes)
        }
    }

    fun serialize(output: DataOutputStream) {
        output.writeInt(startingHeightsNoise)
        output.writeFloat(startingHeightsNoiseScale)
        output.writeUTF(startingHeightsFile)
        output.writeInt(elevationPowerNoise)
        output.writeFloat(elevationPowerNoiseScale)
        output.writeUTF(elevationPowerFile)
        output.writeInt(soilMobilityNoise)
        output.writeFloat(soilMobilityNoiseScale)
        output.writeUTF(soilMobilityFile)
        output.writeInt(customBiomes.size)
        customBiomes.forEach { it.serialize(output) }
    }
}

class CustomBiomeDataItem(
        var name: String = "Custom",
        var talusHeightMultiplier: Float = 1.0f,
        var largeFeaturesBlendWeight: Float = 1.0f,
        var largeFeaturePowerMultiplier: Float = 1.0f,
        var largeFeatureMobilityMultiplier: Float = 1.0f,
        var mediumFeaturesBlendWeight: Float = 1.0f,
        var mediumFeaturePowerMultiplier: Float = 1.0f,
        var mediumFeatureMobilityMultiplier: Float = 1.0f,
        var smallFeaturesBlendWeight: Float = 1.0f,
        var smallFeaturePowerMultiplier: Float = 1.0f,
        var smallFeatureMobilityMultiplier: Float = 1.0f,
        var selectedTalusAngleType: Int = 0,
        var linearTalusMinAngle: Float = 15.0f,
        var linearTalusDeltaAngle: Float = 25.0f,
        var linearTalusJitter: Float = 4.0f,
        var normalTalusScale: Float = 30000.0f,
        var normalTalusStandardDeviation: Float = 270.0f,
        var normalTalusMean: Float = 512.0f,
        var normalTalusJitter: Float = 0.2f,
        var steps: List<Pair<Int, Float>> = ArrayList()) {

    companion object {

        fun deserialize(input: DataInputStream): CustomBiomeDataItem {
            val name = input.readUTF()
            val talusHeightMultiplier = input.readFloat()
            val largeFeaturesBlendWeight = input.readFloat()
            val largeFeaturePowerMultiplier = input.readFloat()
            val largeFeatureMobilityMultiplier = input.readFloat()
            val mediumFeaturesBlendWeight = input.readFloat()
            val mediumFeaturePowerMultiplier = input.readFloat()
            val mediumFeatureMobilityMultiplier = input.readFloat()
            val smallFeaturesBlendWeight = input.readFloat()
            val smallFeaturePowerMultiplier = input.readFloat()
            val smallFeatureMobilityMultiplier = input.readFloat()
            val selectedTalusAngleType = input.readInt()
            when (selectedTalusAngleType) {
                0 -> {
                    val linearTalusMinAngle = input.readFloat()
                    val linearTalusDeltaAngle = input.readFloat()
                    val linearTalusJitter = input.readFloat()
                    return CustomBiomeDataItem(
                            name = name,
                            talusHeightMultiplier = talusHeightMultiplier,
                            largeFeaturesBlendWeight = largeFeaturesBlendWeight,
                            largeFeaturePowerMultiplier = largeFeaturePowerMultiplier,
                            largeFeatureMobilityMultiplier = largeFeatureMobilityMultiplier,
                            mediumFeaturesBlendWeight = mediumFeaturesBlendWeight,
                            mediumFeaturePowerMultiplier = mediumFeaturePowerMultiplier,
                            mediumFeatureMobilityMultiplier = mediumFeatureMobilityMultiplier,
                            smallFeaturesBlendWeight = smallFeaturesBlendWeight,
                            smallFeaturePowerMultiplier = smallFeaturePowerMultiplier,
                            smallFeatureMobilityMultiplier = smallFeatureMobilityMultiplier,
                            selectedTalusAngleType = selectedTalusAngleType,
                            linearTalusMinAngle = linearTalusMinAngle,
                            linearTalusDeltaAngle = linearTalusDeltaAngle,
                            linearTalusJitter = linearTalusJitter)
                }
                1 -> {
                    val stepCount = input.readInt()
                    val steps = ArrayList<Pair<Int, Float>>()
                    for (i in 1..stepCount) {
                        steps.add(input.readInt() to input.readFloat())
                    }
                    return CustomBiomeDataItem(
                            name = name,
                            talusHeightMultiplier = talusHeightMultiplier,
                            largeFeaturesBlendWeight = largeFeaturesBlendWeight,
                            largeFeaturePowerMultiplier = largeFeaturePowerMultiplier,
                            largeFeatureMobilityMultiplier = largeFeatureMobilityMultiplier,
                            mediumFeaturesBlendWeight = mediumFeaturesBlendWeight,
                            mediumFeaturePowerMultiplier = mediumFeaturePowerMultiplier,
                            mediumFeatureMobilityMultiplier = mediumFeatureMobilityMultiplier,
                            smallFeaturesBlendWeight = smallFeaturesBlendWeight,
                            smallFeaturePowerMultiplier = smallFeaturePowerMultiplier,
                            smallFeatureMobilityMultiplier = smallFeatureMobilityMultiplier,
                            selectedTalusAngleType = selectedTalusAngleType,
                            steps = steps)
                }
                else -> {
                    val normalTalusScale = input.readFloat()
                    val normalTalusStandardDeviation = input.readFloat()
                    val normalTalusMean = input.readFloat()
                    val normalTalusJitter = input.readFloat()
                    return CustomBiomeDataItem(
                            name = name,
                            talusHeightMultiplier = talusHeightMultiplier,
                            largeFeaturesBlendWeight = largeFeaturesBlendWeight,
                            largeFeaturePowerMultiplier = largeFeaturePowerMultiplier,
                            largeFeatureMobilityMultiplier = largeFeatureMobilityMultiplier,
                            mediumFeaturesBlendWeight = mediumFeaturesBlendWeight,
                            mediumFeaturePowerMultiplier = mediumFeaturePowerMultiplier,
                            mediumFeatureMobilityMultiplier = mediumFeatureMobilityMultiplier,
                            smallFeaturesBlendWeight = smallFeaturesBlendWeight,
                            smallFeaturePowerMultiplier = smallFeaturePowerMultiplier,
                            smallFeatureMobilityMultiplier = smallFeatureMobilityMultiplier,
                            selectedTalusAngleType = selectedTalusAngleType,
                            normalTalusScale = normalTalusScale,
                            normalTalusStandardDeviation = normalTalusStandardDeviation,
                            normalTalusMean = normalTalusMean,
                            normalTalusJitter = normalTalusJitter)
                }
            }
        }
    }

    fun serialize(output: DataOutputStream) {
        output.writeUTF(name)
        output.writeFloat(talusHeightMultiplier)
        output.writeFloat(largeFeaturesBlendWeight)
        output.writeFloat(largeFeaturePowerMultiplier)
        output.writeFloat(largeFeatureMobilityMultiplier)
        output.writeFloat(mediumFeaturesBlendWeight)
        output.writeFloat(mediumFeaturePowerMultiplier)
        output.writeFloat(mediumFeatureMobilityMultiplier)
        output.writeFloat(smallFeaturesBlendWeight)
        output.writeFloat(smallFeaturePowerMultiplier)
        output.writeFloat(smallFeatureMobilityMultiplier)
        output.writeInt(selectedTalusAngleType)
        when (selectedTalusAngleType) {
            0 -> {
                output.writeFloat(linearTalusMinAngle)
                output.writeFloat(linearTalusDeltaAngle)
                output.writeFloat(linearTalusJitter)
            }
            1 -> {
                output.writeInt(steps.size)
                steps.forEach {
                    output.writeInt(it.first)
                    output.writeFloat(it.second)
                }
            }
            2 -> {
                output.writeFloat(normalTalusScale)
                output.writeFloat(normalTalusStandardDeviation)
                output.writeFloat(normalTalusMean)
                output.writeFloat(normalTalusJitter)
            }
        }
    }
}

class NewBiomeData(
        val name: ObservableMutableReference<String> = ref("Custom"),
        val talusHeightMultiplier: ObservableMutableReference<Float> = ref(1.0f),
        val largeFeaturesBlendWeight: ObservableMutableReference<Float> = ref(1.0f),
        val largeFeaturePowerMultiplier: ObservableMutableReference<Float> = ref(1.0f),
        val largeFeatureMobilityMultiplier: ObservableMutableReference<Float> = ref(1.0f),
        val mediumFeaturesBlendWeight: ObservableMutableReference<Float> = ref(1.0f),
        val mediumFeaturePowerMultiplier: ObservableMutableReference<Float> = ref(1.0f),
        val mediumFeatureMobilityMultiplier: ObservableMutableReference<Float> = ref(1.0f),
        val smallFeaturesBlendWeight: ObservableMutableReference<Float> = ref(1.0f),
        val smallFeaturePowerMultiplier: ObservableMutableReference<Float> = ref(1.0f),
        val smallFeatureMobilityMultiplier: ObservableMutableReference<Float> = ref(1.0f),
        val selectedTalusAngleType: ObservableMutableReference<Int> = ref(0),
        val linearTalusMinAngle: ObservableMutableReference<Float> = ref(15.0f),
        val linearTalusDeltaAngle: ObservableMutableReference<Float> = ref(25.0f),
        val linearTalusJitter: ObservableMutableReference<Float> = ref(4.0f),
        val normalTalusScale: ObservableMutableReference<Float> = ref(30000.0f),
        val normalTalusStandardDeviation: ObservableMutableReference<Float> = ref(270.0f),
        val normalTalusMean: ObservableMutableReference<Float> = ref(512.0f),
        val normalTalusJitter: ObservableMutableReference<Float> = ref(0.2f),
        val steps: ArrayList<Pair<ObservableMutableReference<Long>, ObservableMutableReference<Float>>> = ArrayList()) {

    fun reset() {
        name.value = "Custom"
        talusHeightMultiplier.value = 1.0f
        selectedTalusAngleType.value = 0
        linearTalusMinAngle.value = 15.0f
        linearTalusDeltaAngle.value = 25.0f
        linearTalusJitter.value = 4.0f
        normalTalusScale.value = 30000.0f
        normalTalusStandardDeviation.value = 270.0f
        normalTalusMean.value = 512.0f
        normalTalusJitter.value = 0.2f
        steps.clear()
    }

    fun toCustomBiomeTemplate(): CustomBiomeTemplate {
        return CustomBiomeTemplate(
                name = name.value,
                talusHeightMultiplier = talusHeightMultiplier.value,
                largeFeaturesBlendWeight = largeFeaturesBlendWeight.value,
                largeFeaturePowerMultiplier = largeFeaturePowerMultiplier.value,
                largeFeatureMobilityMultiplier = largeFeatureMobilityMultiplier.value,
                mediumFeaturesBlendWeight = mediumFeaturesBlendWeight.value,
                mediumFeaturePowerMultiplier = mediumFeaturePowerMultiplier.value,
                mediumFeatureMobilityMultiplier = mediumFeatureMobilityMultiplier.value,
                smallFeaturesBlendWeight = smallFeaturesBlendWeight.value,
                smallFeaturePowerMultiplier = smallFeaturePowerMultiplier.value,
                smallFeatureMobilityMultiplier = smallFeatureMobilityMultiplier.value,
                talusAngleTemplate = TalusAngleTemplate(
                        talusAngleType = when (selectedTalusAngleType.value) { 0 -> LINEAR; 1 -> STEPPED; else -> NORMAL },
                        linearTalusMinAngle = linearTalusMinAngle.value,
                        linearTalusDeltaAngle = linearTalusDeltaAngle.value,
                        linearTalusJitter = linearTalusJitter.value,
                        normalTalusScale = normalTalusScale.value,
                        normalTalusStandardDeviation = normalTalusStandardDeviation.value,
                        normalTalusMean = normalTalusMean.value,
                        normalTalusJitter = normalTalusJitter.value,
                        steps = steps.map { it.first.value.toInt() to it.second.value }.toTypedArray()))
    }

    fun toCustomBiomeDataItem(): CustomBiomeDataItem {
        return CustomBiomeDataItem(
                name = name.value,
                talusHeightMultiplier = talusHeightMultiplier.value,
                largeFeaturesBlendWeight = largeFeaturesBlendWeight.value,
                largeFeaturePowerMultiplier = largeFeaturePowerMultiplier.value,
                largeFeatureMobilityMultiplier = largeFeatureMobilityMultiplier.value,
                mediumFeaturesBlendWeight = mediumFeaturesBlendWeight.value,
                mediumFeaturePowerMultiplier = mediumFeaturePowerMultiplier.value,
                mediumFeatureMobilityMultiplier = mediumFeatureMobilityMultiplier.value,
                smallFeaturesBlendWeight = smallFeaturesBlendWeight.value,
                smallFeaturePowerMultiplier = smallFeaturePowerMultiplier.value,
                smallFeatureMobilityMultiplier = smallFeatureMobilityMultiplier.value,
                selectedTalusAngleType = selectedTalusAngleType.value,
                linearTalusMinAngle = linearTalusMinAngle.value,
                linearTalusDeltaAngle = linearTalusDeltaAngle.value,
                linearTalusJitter = linearTalusJitter.value,
                normalTalusScale = normalTalusScale.value,
                normalTalusStandardDeviation = normalTalusStandardDeviation.value,
                normalTalusMean = normalTalusMean.value,
                normalTalusJitter = normalTalusJitter.value,
                steps = steps.map { it.first.value.toInt() to it.second.value })
    }

    fun populateFrom(customBiomeData: CustomBiomeDataItem) {
        name.value = customBiomeData.name
        talusHeightMultiplier.value = customBiomeData.talusHeightMultiplier
        largeFeaturesBlendWeight.value = customBiomeData.largeFeaturesBlendWeight
        largeFeaturePowerMultiplier.value = customBiomeData.largeFeaturePowerMultiplier
        largeFeatureMobilityMultiplier.value = customBiomeData.largeFeatureMobilityMultiplier
        mediumFeaturesBlendWeight.value = customBiomeData.mediumFeaturesBlendWeight
        mediumFeaturePowerMultiplier.value = customBiomeData.mediumFeaturePowerMultiplier
        mediumFeatureMobilityMultiplier.value = customBiomeData.mediumFeatureMobilityMultiplier
        smallFeaturesBlendWeight.value = customBiomeData.smallFeaturesBlendWeight
        smallFeaturePowerMultiplier.value = customBiomeData.smallFeaturePowerMultiplier
        smallFeatureMobilityMultiplier.value = customBiomeData.smallFeatureMobilityMultiplier
        selectedTalusAngleType.value = customBiomeData.selectedTalusAngleType
        linearTalusMinAngle.value = customBiomeData.linearTalusMinAngle
        linearTalusDeltaAngle.value = customBiomeData.linearTalusDeltaAngle
        linearTalusJitter.value = customBiomeData.linearTalusJitter
        normalTalusScale.value = customBiomeData.normalTalusScale
        normalTalusStandardDeviation.value = customBiomeData.normalTalusStandardDeviation
        normalTalusMean.value = customBiomeData.normalTalusMean
        normalTalusJitter.value = customBiomeData.normalTalusJitter
        for (i in 0 until min(steps.size, customBiomeData.steps.size)) {
            val (height, angle) = steps[i]
            val (newHeight, newAngle) = customBiomeData.steps[i]
            height.value = newHeight.toLong()
            angle.value = newAngle
        }
    }
}

fun customBiomePanel(ui: UserInterface) {
    val shrinkGroup = hShrinkGroup()

    panelLayer {
        val scroller = ref(NO_BLOCK)
        val resetScroller: () -> Unit = {
            doOnMainThread {
                val scrollerInternal = scroller.value
                scrollerInternal.clearPositionAndSize()
                scrollerInternal.onScroll?.invoke(scrollerInternal, 0.0, 0.0)
            }
        }
        val resetScrollerListener: (Boolean, Boolean) -> Unit = { old, new ->
            if (old && !new) {
                resetScroller()
            }
        }
        customBiomePanel = panel(PANEL_WIDTH, Sizing.RELATIVE, MEDIUM_SPACER_SIZE * -2) {
            block {
                layout = Layout.HORIZONTAL
                scroller.value = block {
                    receiveChildEvents = true
                    vSizing = Sizing.SHRINK
                    layout = Layout.VERTICAL
                    val existingBiomesPanelExpanded = ref(false)
                    var existingBiomesPanel = NO_BLOCK
                    var existingBiomesExpander = NO_BLOCK
                    populateFromCustomBiomeData = { customBiomeData ->
                        populateFromCustomBiomeData(
                                customBiomeData = customBiomeData,
                                existingBiomesPanel = existingBiomesPanel,
                                shrinkGroup = shrinkGroup,
                                existingBiomesPanelExpanded = existingBiomesPanelExpanded,
                                existingBiomesExpander = existingBiomesExpander,
                                scroller = scroller,
                                resetScrollerListener = resetScrollerListener,
                                ui = ui)
                    }
                    currentProject.addListener { _, new ->
                        if (new != null) {
                            doOnMainThread {
                                val dataForImport = new.customBiomeDataForImport
                                if (dataForImport != null) {
                                    populateFromCustomBiomeData(dataForImport)
                                    new.customBiomeDataForImport = null
                                    clearCreatorPanel()
                                } else {
                                    BIOME_TEMPLATES_REF.value?.clearCustomBiomes(BIOME_NAMES_AS_TEXT)
                                    existingBiomesPanel.layoutChildren.clear()
                                    existingBiomesPanel.renderChildren.clear()
                                    clearCreatorPanel()
                                    existingBiomesExpander.isVisible = false
                                    new.customBiomeProperties.reset()
                                }
                            }
                        }
                    }

                    vSpacer(LARGE_SPACER_SIZE)
                    vButtonRow(LARGE_ROW_HEIGHT) {
                        button(text("Import custom biomes"), NORMAL_TEXT_BUTTON_STYLE) {
                            val customBiomes = importCustomBiomesFile(dialogLayer, preferences, ui)
                            if (customBiomes != null) {
                                doOnMainThread {
                                    populateFromCustomBiomeData(customBiomes)
                                }
                            }
                        }
                        hSpacer(SMALL_SPACER_SIZE)
                        button(text("Export custom biomes"), NORMAL_TEXT_BUTTON_STYLE) {
                            val project = currentProject.value
                            if (project != null) {
                                exportCustomBiomesFile(buildCustomBiomeData(project.customBiomeProperties, project.customBiomes), dialogLayer, preferences, ui)
                            }
                        }
                    }
                    val customBiomeBaseFilesExpanded = ref(true)
                    vExpandPanel("Custom biome base files", scroller = scroller, expanded = customBiomeBaseFilesExpanded) {
                        padLeft = LARGE_SPACER_SIZE
                        vSliderWithValueRow(STARTING_HEIGHTS_NOISE, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Starting heights noise:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..127), linearClampedScaleFunctionInverse(0..127))
                        vSliderRow(STARTING_HEIGHTS_NOISE_SCALE, LARGE_ROW_HEIGHT, text("Starting heights noise scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 12.0f), linearClampedScaleFunctionInverse(0.0f, 12.0f))
                        vFileRow(STARTING_HEIGHTS_FILE, LARGE_ROW_HEIGHT, text("Starting height file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui)
                        vSliderWithValueRow(ELEVATION_POWER_NOISE, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Elevation power noise:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..127), linearClampedScaleFunctionInverse(0..127))
                        vSliderRow(ELEVATION_POWER_NOISE_SCALE, LARGE_ROW_HEIGHT, text("Elevation power noise scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 12.0f), linearClampedScaleFunctionInverse(0.0f, 12.0f))
                        vFileRow(ELEVATION_POWER_FILE, LARGE_ROW_HEIGHT, text("Elevation power file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
                        vSliderWithValueRow(SOIL_MOBILITY_NOISE, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Soil mobility noise:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..127), linearClampedScaleFunctionInverse(0..127))
                        vSliderRow(SOIL_MOBILITY_NOISE_SCALE, LARGE_ROW_HEIGHT, text("Soil mobility noise scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.0f, 12.0f), linearClampedScaleFunctionInverse(0.0f, 12.0f))
                        vFileRow(SOIL_MOBILITY_FILE, LARGE_ROW_HEIGHT, text("Soil mobility file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui)
                        vSpacer(MEDIUM_SPACER_SIZE)
                        vButtonRow(LARGE_ROW_HEIGHT) {
                            button(text("Load base files"), NORMAL_TEXT_BUTTON_STYLE) {
                                dialogLayer.isVisible = true
                                generatingPrimaryMessage.reference.value = text("Loading textures... 0:00", TEXT_STYLE_LARGE_MESSAGE)
                                generatingSecondaryMessage.reference.value = text("Press ESC to cancel.", TEXT_STYLE_SMALL_MESSAGE)
                                generatingMessageBlock.isVisible = true
                                val startTime = System.currentTimeMillis()
                                val generationTimer = Timer(true)
                                generationTimer.schedule(object : TimerTask() {
                                    override fun run() {
                                        val currentTime = System.currentTimeMillis()
                                        val elapsedTime = (currentTime - startTime)
                                        val seconds = String.format("%02d", (elapsedTime / 1000).toInt() % 60)
                                        val minutes = (elapsedTime / (1000 * 60) % 60).toInt()
                                        generatingPrimaryMessage.reference.value = text("Loading textures... $minutes:$seconds", TEXT_STYLE_LARGE_MESSAGE)
                                        rootRef.value.movedOrResized = true
                                    }
                                }, 1000, 1000)
                                try {
                                    val canceled = ref(false)
                                    cancelCurrentRunningTask.value = canceled
                                    try {
                                        val project = currentProject.value
                                        if (project != null) {
                                            val customBiomeProperties = project.customBiomeProperties
                                            val startingHeightsDataToLoadDeferred = task {
                                                val file = File(customBiomeProperties.startingHeightsFile.value)
                                                if (file.canRead()) {
                                                    prepareImageDataForLoading(GL11.GL_LINEAR, GL11.GL_LINEAR, ImageIO.read(file), false, true, customBiomeProperties.startingHeightsNoise.value, customBiomeProperties.startingHeightsNoiseScale.value)
                                                } else null
                                            }
                                            val startingHeightsTextureDeffered = task {
                                                val result = runBlocking {
                                                    startingHeightsDataToLoadDeferred.await()
                                                }
                                                doOnMainThreadAndWait {
                                                    val id = result?.loadImageDataIntoOpengl()
                                                    if (id != null) {
                                                        TextureBuilder.TextureId(id.first)
                                                    } else null
                                                }
                                            }
                                            val elevationPowerDataToLoadDeferred = task {
                                                val file = File(customBiomeProperties.elevationPowerFile.value)
                                                if (file.canRead()) {
                                                    prepareImageDataForLoading(GL11.GL_LINEAR, GL11.GL_LINEAR, ImageIO.read(file), false, true, customBiomeProperties.elevationPowerNoise.value, customBiomeProperties.elevationPowerNoiseScale.value)
                                                } else null
                                            }
                                            val elevationPowerTextureDeffered = task {
                                                val result = runBlocking {
                                                    elevationPowerDataToLoadDeferred.await()
                                                }
                                                doOnMainThreadAndWait {
                                                    val id = result?.loadImageDataIntoOpengl()
                                                    if (id != null) {
                                                        TextureBuilder.TextureId(id.first)
                                                    } else null
                                                }
                                            }
                                            val soilMobilityDataToLoadDeferred = task {
                                                val file = File(customBiomeProperties.soilMobilityFile.value)
                                                if (file.canRead()) {
                                                    prepareImageDataForLoading(GL11.GL_LINEAR, GL11.GL_LINEAR, ImageIO.read(file), false, true, customBiomeProperties.soilMobilityNoise.value, customBiomeProperties.soilMobilityNoiseScale.value)
                                                } else null
                                            }
                                            val soilMobilityTextureDeffered = task {
                                                val result = runBlocking {
                                                    soilMobilityDataToLoadDeferred.await()
                                                }
                                                doOnMainThreadAndWait {
                                                    val id = result?.loadImageDataIntoOpengl()
                                                    if (id != null) {
                                                        TextureBuilder.TextureId(id.first)
                                                    } else null
                                                }
                                            }
                                            val (startingHeightsTexture, elevationPowerTexture, soilMobilityTexture) = runBlocking {
                                                Triple(startingHeightsTextureDeffered.await(), elevationPowerTextureDeffered.await(), soilMobilityTextureDeffered.await())
                                            }
                                            if (startingHeightsTexture != null) {
                                                currentState.customStartingHeightsMap.value = startingHeightsTexture
                                            }
                                            if (elevationPowerTexture != null) {
                                                currentState.customElevationPowerMap.value = elevationPowerTexture
                                            }
                                            if (soilMobilityTexture != null) {
                                                currentState.customSoilMobilityMap.value = soilMobilityTexture
                                            }
                                        }
                                    } catch (w: Exception) {
                                        if (!causedByCancellation(w)) {
                                            throw w
                                        }
                                    } finally {
                                        if (cancelCurrentRunningTask.value == canceled) {
                                            cancelCurrentRunningTask.value = null
                                        }
                                    }
                                } finally {
                                    generationTimer.cancel()
                                    generatingMessageBlock.isVisible = false
                                    dialogLayer.isVisible = false
                                }
                            }
                        }
                    }
                    customBiomeBaseFilesExpanded.addListener(resetScrollerListener)
                    existingBiomesExpander = vExpandPanel("Custom biomes", scroller = scroller, expanded = existingBiomesPanelExpanded) {
                        val container = this
                        existingBiomesPanel = block {
                            layout = Layout.VERTICAL
                            vSizing = Sizing.SHRINK
                            padLeft = LARGE_SPACER_SIZE
                        }
                        if (!existingBiomesPanelExpanded.value) {
                            container.layoutChildren.remove(existingBiomesPanel)
                            container.renderChildren.remove(existingBiomesPanel)
                        }
                        existingBiomesPanelExpanded.addListener { old, new ->
                            if (old != new) {
                                doOnMainThread {
                                    if (new) {
                                        container.layoutChildren.add(existingBiomesPanel)
                                        container.renderChildren.add(existingBiomesPanel)
                                    } else {
                                        container.layoutChildren.remove(existingBiomesPanel)
                                        container.renderChildren.remove(existingBiomesPanel)
                                    }
                                }
                            }
                        }
                    }
                    existingBiomesExpander.isVisible = false
                    existingBiomesPanelExpanded.addListener(resetScrollerListener)
                    val newBiomePanelExpanded = ref(true)
                    vExpandPanel("New biome", scroller = scroller, expanded = newBiomePanelExpanded) {
                        val newBiomePanel = this
                        val pair = newBiomePanel.buildNewCreatorPanel(1, shrinkGroup, scroller, resetScrollerListener, ui)
                        val newBiomeData = ref(pair.first)
                        val creatorPanel = ref(pair.second)
                        clearCreatorPanel = { clearCreatorPanel(newBiomePanel, newBiomeData, creatorPanel, shrinkGroup, scroller, resetScrollerListener, ui) }
                        vButtonRow(LARGE_ROW_HEIGHT) {
                            button(text("Add custom biome"), NORMAL_TEXT_BUTTON_STYLE) {
                                val addCustomBiomeButton = this
                                doOnMainThreadAndWait {
                                    val customBiomeTemplate = newBiomeData.value.toCustomBiomeTemplate()
                                    val newBiome = if (BIOME_NAMES_AS_TEXT.size < 64) {
                                        val newBiome = customBiomeTemplate.addCustomBiome()
                                        if (newBiome != null && BIOME_NAMES_AS_TEXT.size == 64) {
                                            addCustomBiomeButton.isVisible = false
                                        }
                                        newBiome
                                    } else {
                                        null
                                    }
                                    val project = currentProject.value
                                    if (newBiome != null && project != null) {
                                        existingBiomesExpander.isVisible = true
                                        swapOutCreatorPanel(newBiomePanel, newBiomeData, creatorPanel, newBiome, project.customBiomes, existingBiomesPanel, existingBiomesPanelExpanded, existingBiomesExpander, shrinkGroup, scroller, resetScrollerListener, ui)
                                    }
                                }
                            }
                        }
                    }
                    newBiomePanelExpanded.addListener(resetScrollerListener)
                    vSpacer(LARGE_SPACER_SIZE)
                    vButtonRow(LARGE_ROW_HEIGHT) {
                        button(text("Close"), DIALOG_BUTTON_STYLE) {
                            existingBiomesPanelExpanded.value = false
                            customBiomePanel.isVisible = false
                            panelLayer.isVisible = false
                            resetScroller()
                        }.with { width = 60.0f }
                    }
                    vSpacer(LARGE_SPACER_SIZE)
                }
                scroller.value.onScroll { _, y ->
                    val internalScroller = scroller.value
                    val scrollerHeight = internalScroller.height
                    val parentHeight = internalScroller.parent.height
                    if (scrollerHeight > parentHeight) {
                        val diff = parentHeight - scrollerHeight
                        val newOffset = internalScroller.yOffset + y * LARGE_ROW_HEIGHT * 3
                        val clampedOffset = Math.max(diff.toDouble(), Math.min(0.0, newOffset)).toFloat()
                        internalScroller.yOffset = clampedOffset
                    } else {
                        internalScroller.yOffset = 0.0f
                    }
                }
                val oldWindowResize = onWindowResize
                onWindowResize = {
                    oldWindowResize()
                    resetScroller()
                }
            }.onScroll { _, _ ->
                resetScroller()
            }
        }.with {
            vAlign = VerticalAlignment.TOP
            yOffset = MEDIUM_SPACER_SIZE
        }
        customBiomePanel.isVisibleRef.addListener { old, new ->
            if (!old && new) {
                resetScroller()
            }
        }
    }
}

fun buildCustomBiomeData(customBiomeProperties: CustomBiomeProperties, existingBiomes: List<Pair<NewBiomeData, Block>>): CustomBiomeData {
    return CustomBiomeData(
            customBiomeProperties.startingHeightsNoise.value,
            customBiomeProperties.startingHeightsNoiseScale.value,
            customBiomeProperties.startingHeightsFile.value,
            customBiomeProperties.elevationPowerNoise.value,
            customBiomeProperties.elevationPowerNoiseScale.value,
            customBiomeProperties.elevationPowerFile.value,
            customBiomeProperties.soilMobilityNoise.value,
            customBiomeProperties.soilMobilityNoiseScale.value,
            customBiomeProperties.soilMobilityFile.value,
            existingBiomes.map { it.first.toCustomBiomeDataItem() })
}

private var populateFromCustomBiomeData = { customBiomeData: CustomBiomeData -> }

private fun populateFromCustomBiomeData(
        customBiomeData: CustomBiomeData,
        existingBiomesPanel: Block,
        shrinkGroup: ShrinkGroup,
        existingBiomesPanelExpanded: ObservableMutableReference<Boolean>,
        existingBiomesExpander: Block,
        scroller: Reference<Block>,
        resetScrollerListener: (Boolean, Boolean) -> Unit,
        ui: UserInterface) {
    val project = currentProject.value
    val biomeTemplates = BIOME_TEMPLATES_REF.value
    if (project != null && biomeTemplates != null) {
        val startingHeightsNoise = project.customBiomeProperties.startingHeightsNoise
        val startingHeightsNoiseScale = project.customBiomeProperties.startingHeightsNoiseScale
        val startingHeightsFile = project.customBiomeProperties.startingHeightsFile
        val elevationPowerNoise = project.customBiomeProperties.elevationPowerNoise
        val elevationPowerNoiseScale = project.customBiomeProperties.elevationPowerNoiseScale
        val elevationPowerFile = project.customBiomeProperties.elevationPowerFile
        val soilMobilityNoise = project.customBiomeProperties.soilMobilityNoise
        val soilMobilityNoiseScale = project.customBiomeProperties.soilMobilityNoiseScale
        val soilMobilityFile = project.customBiomeProperties.soilMobilityFile
        val existingBiomes = project.customBiomes
        startingHeightsNoise.value = customBiomeData.startingHeightsNoise
        startingHeightsNoiseScale.value = customBiomeData.startingHeightsNoiseScale
        startingHeightsFile.value = customBiomeData.startingHeightsFile
        elevationPowerNoise.value = customBiomeData.elevationPowerNoise
        elevationPowerNoiseScale.value = customBiomeData.elevationPowerNoiseScale
        elevationPowerFile.value = customBiomeData.elevationPowerFile
        soilMobilityNoise.value = customBiomeData.soilMobilityNoise
        soilMobilityNoiseScale.value = customBiomeData.soilMobilityNoiseScale
        soilMobilityFile.value = customBiomeData.soilMobilityFile
        biomeTemplates.clearCustomBiomes(BIOME_NAMES_AS_TEXT)
        existingBiomes.clear()
        existingBiomesPanel.layoutChildren.clear()
        existingBiomesPanel.renderChildren.clear()
        var hasNew = false
        customBiomeData.customBiomes.forEach {
            val pair = existingBiomesPanel.buildNewCreatorPanel(max(it.steps.size, 1), shrinkGroup, scroller, resetScrollerListener, ui)
            val newBiomeData = pair.first
            val creatorPanel = pair.second
            newBiomeData.populateFrom(it)
            existingBiomesPanel.layoutChildren.remove(creatorPanel)
            existingBiomesPanel.renderChildren.remove(creatorPanel)
            val customBiomeTemplate = newBiomeData.toCustomBiomeTemplate()
            val newBiome = if (BIOME_NAMES_AS_TEXT.size < 64) {
                customBiomeTemplate.addCustomBiome()
            } else {
                null
            }
            if (newBiome != null) {
                hasNew = true
                existingBiomes.add(pair)
                existingBiomesPanel.buildNewEditorPanel(pair, newBiome, existingBiomes, existingBiomesPanelExpanded, existingBiomesExpander, scroller, resetScrollerListener)
            }
        }
        if (hasNew) {
            existingBiomesExpander.isVisible = true
        }
    }
}

private fun swapOutCreatorPanel(newBiomePanel: Block, newBiomeData: ObservableMutableReference<NewBiomeData>, creatorPanel: ObservableMutableReference<Block>, newBiome: Biome, existingBiomes: MutableList<Pair<NewBiomeData, Block>>, existingBiomesPanel: Block, existingBiomesPanelExpanded: ObservableMutableReference<Boolean>, existingBiomesExpander: Block, shrinkGroup: ShrinkGroup, scroller: Reference<Block>, resetScrollerListener: (Boolean, Boolean) -> Unit, ui: UserInterface) {
    newBiomePanel.layoutChildren.remove(creatorPanel.value)
    newBiomePanel.renderChildren.remove(creatorPanel.value)
    val pair = newBiomeData.value to creatorPanel.value
    existingBiomes.add(pair)
    existingBiomesPanel.buildNewEditorPanel(pair, newBiome, existingBiomes, existingBiomesPanelExpanded, existingBiomesExpander, scroller, resetScrollerListener)
    val (newNewBiomeData, newCreatorPanel) = newBiomePanel.buildNewCreatorPanel(1, shrinkGroup, scroller, resetScrollerListener, ui)
    newBiomeData.value = newNewBiomeData
    creatorPanel.value = newCreatorPanel
    newBiomePanel.layoutChildren.remove(newCreatorPanel)
    newBiomePanel.renderChildren.remove(newCreatorPanel)
    newBiomePanel.layoutChildren.add(0, newCreatorPanel)
    newBiomePanel.renderChildren.add(0, newCreatorPanel)
}

private var clearCreatorPanel = {  }

private fun clearCreatorPanel(newBiomePanel: Block, newBiomeData: ObservableMutableReference<NewBiomeData>, creatorPanel: ObservableMutableReference<Block>, shrinkGroup: ShrinkGroup, scroller: Reference<Block>, resetScrollerListener: (Boolean, Boolean) -> Unit, ui: UserInterface) {
    newBiomePanel.layoutChildren.remove(creatorPanel.value)
    newBiomePanel.renderChildren.remove(creatorPanel.value)
    val (newNewBiomeData, newCreatorPanel) = newBiomePanel.buildNewCreatorPanel(1, shrinkGroup, scroller, resetScrollerListener, ui)
    newBiomeData.value = newNewBiomeData
    creatorPanel.value = newCreatorPanel
    newBiomePanel.layoutChildren.remove(newCreatorPanel)
    newBiomePanel.renderChildren.remove(newCreatorPanel)
    newBiomePanel.layoutChildren.add(0, newCreatorPanel)
    newBiomePanel.renderChildren.add(0, newCreatorPanel)
}

private fun Block.buildNewCreatorPanel(stepCount: Int, shrinkGroup: ShrinkGroup, scroller: Reference<Block>, resetScrollerListener: (Boolean, Boolean) -> Unit, ui: UserInterface): Pair<NewBiomeData, Block> {
    val newBiomeData = NewBiomeData()
    val creatorPanel = block {
        layout = Layout.VERTICAL
        vSizing = Sizing.SHRINK
        padLeft = LARGE_SPACER_SIZE
        vTextInputRow(newBiomeData.name, 30, LARGE_ROW_HEIGHT, text("Biome name:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.largeFeaturesBlendWeight, LARGE_ROW_HEIGHT, text("Large scale blend multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.largeFeaturePowerMultiplier, LARGE_ROW_HEIGHT, text("Large scale elevation power multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.largeFeatureMobilityMultiplier, LARGE_ROW_HEIGHT, text("Large scale soil mobility multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.mediumFeaturesBlendWeight, LARGE_ROW_HEIGHT, text("Medium scale blend multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.mediumFeaturePowerMultiplier, LARGE_ROW_HEIGHT, text("Medium scale elevation power multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.mediumFeatureMobilityMultiplier, LARGE_ROW_HEIGHT, text("Medium scale soil mobility multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.smallFeaturesBlendWeight, LARGE_ROW_HEIGHT, text("Small scale blend multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.smallFeaturePowerMultiplier, LARGE_ROW_HEIGHT, text("Small scale elevation power multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.smallFeatureMobilityMultiplier, LARGE_ROW_HEIGHT, text("Small scale soil mobility multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        vFloatInputRow(newBiomeData.talusHeightMultiplier, LARGE_ROW_HEIGHT, text("Talus height multiplier:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        val talusAngleTypeText = listOf(text("Linear"), text("Stepped"), text("Normal"))
        vDropdownRow(newBiomeData.selectedTalusAngleType, talusAngleTypeText, LARGE_ROW_HEIGHT, text("Talus angle distribution:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogDropdownLayer)
        val linearTalusBlock = block {
            layout = Layout.VERTICAL
            vSizing = Sizing.SHRINK
            vFloatInputRow(newBiomeData.linearTalusMinAngle, LARGE_ROW_HEIGHT, text("Starting angle:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
            vFloatInputRow(newBiomeData.linearTalusDeltaAngle, LARGE_ROW_HEIGHT, text("Delta angle:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
            vFloatInputRow(newBiomeData.linearTalusJitter, LARGE_ROW_HEIGHT, text("Jitter:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        }
        val steppedTalusBlockExpanded = ref(true)
        val steppedTalusBlock = vExpandPanel("Steps", scroller = scroller, expanded = steppedTalusBlockExpanded) {
            padLeft = LARGE_SPACER_SIZE
            var step = 0
            val stepsBlock = block {
                layout = Layout.VERTICAL
                vSizing = Sizing.SHRINK
            }

            fun Block.addStep() {
                step++
                block {
                    layout = Layout.VERTICAL
                    vSizing = Sizing.SHRINK
                    val thisBlock = this
                    val heightRef = ref(1024L)
                    val angleRef = ref(45.0f)
                    val thisPair = heightRef to angleRef
                    vSpacer(HALF_ROW_HEIGHT)
                    vLabelWithButtonRow(LARGE_ROW_HEIGHT, text("Step $step"), glyph(GLYPH_CLOSE), MEDIUM_SPACER_SIZE, NORMAL_GLYPH_BUTTON_STYLE) {
                        doOnMainThreadAndWait {
                            stepsBlock.renderChildren.remove(thisBlock)
                            stepsBlock.layoutChildren.remove(thisBlock)
                            newBiomeData.steps.remove(thisPair)
                        }
                    }
                    vLongInputRow(heightRef, LARGE_ROW_HEIGHT, text("Step height:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
                    vFloatInputRow(angleRef, LARGE_ROW_HEIGHT, text("Step angle:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
                    newBiomeData.steps.add(thisPair)
                }
            }
            for (i in 1..stepCount) {
                stepsBlock.addStep()
            }
            vSpacer(HALF_ROW_HEIGHT)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("Add step"), NORMAL_TEXT_BUTTON_STYLE) {
                    doOnMainThreadAndWait {
                        stepsBlock.addStep()
                    }
                }
            }
        }
        steppedTalusBlockExpanded.addListener(resetScrollerListener)
        val normalTalusBlock = block {
            layout = Layout.VERTICAL
            vSizing = Sizing.SHRINK
            vFloatInputRow(newBiomeData.normalTalusScale, LARGE_ROW_HEIGHT, text("Scale:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
            vFloatInputRow(newBiomeData.normalTalusStandardDeviation, LARGE_ROW_HEIGHT, text("Standard deviation:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
            vFloatInputRow(newBiomeData.normalTalusMean, LARGE_ROW_HEIGHT, text("Mean:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
            vFloatInputRow(newBiomeData.normalTalusJitter, LARGE_ROW_HEIGHT, text("Jitter:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, ui.layout)
        }
        linearTalusBlock.isVisible = true
        steppedTalusBlock.isVisible = false
        normalTalusBlock.isVisible = false
        newBiomeData.selectedTalusAngleType.addListener { old, new ->
            if (old != new) {
                linearTalusBlock.isVisible = new == 0
                steppedTalusBlock.isVisible = new == 1
                normalTalusBlock.isVisible = new == 2
            }
        }
    }
    return newBiomeData to creatorPanel
}

private fun Block.buildNewEditorPanel(pair: Pair<NewBiomeData, Block>, customBiome: Biome, existingBiomes: MutableList<Pair<NewBiomeData, Block>>, existingBiomesPanelExpanded: ObservableMutableReference<Boolean>, existingBiomesExpander: Block, scroller: Reference<Block>, resetScrollerListener: (Boolean, Boolean) -> Unit): Block {
    val newBiomeData = pair.first
    val creatorPanel = pair.second
    val existingBiomesPanel = this
    val editorPanelExpanded = ref(false)
    val customBiomeRef = ref(customBiome)
    var expander = NO_BLOCK
    expander = vExpandPanel(newBiomeData.name, scroller = scroller, expanded = editorPanelExpanded) {
        val editorPanel = this
        creatorPanel.parent = editorPanel
        if (editorPanelExpanded.value) {
            editorPanel.layoutChildren.add(0, creatorPanel)
            editorPanel.renderChildren.add(0, creatorPanel)
        }
        editorPanelExpanded.addListener { old, new ->
            if (old != new) {
                if (new) {
                    editorPanel.layoutChildren.add(0, creatorPanel)
                    editorPanel.renderChildren.add(0, creatorPanel)
                } else {
                    editorPanel.layoutChildren.remove(creatorPanel)
                    editorPanel.renderChildren.remove(creatorPanel)
                }
            }
        }
        vButtonRow(LARGE_ROW_HEIGHT) {
            button(text("Update"), NORMAL_TEXT_BUTTON_STYLE) {
                doOnMainThreadAndWait {
                    val customBiomeTemplate = newBiomeData.toCustomBiomeTemplate()
                    customBiomeRef.value = customBiomeTemplate.replaceCustomBiome(customBiomeRef.value) ?: customBiomeRef.value
                }
            }
            hSpacer(MEDIUM_SPACER_SIZE)
            button(text("Delete"), NORMAL_TEXT_BUTTON_STYLE) {
                doOnMainThreadAndWait {
                    val biomeTemplates = BIOME_TEMPLATES_REF.value
                    if (biomeTemplates != null) {
                        if (biomeTemplates.removeCustomBiome(customBiomeRef.value, BIOME_NAMES_AS_TEXT) == null) {
                            existingBiomes.remove(pair)
                            existingBiomesPanel.layoutChildren.remove(expander)
                            existingBiomesPanel.renderChildren.remove(expander)
                            if (existingBiomes.isEmpty()) {
                                existingBiomesPanelExpanded.value = false
                                existingBiomesExpander.isVisible = false
                            }
                        }
                    }
                }
            }
        }
    }
    editorPanelExpanded.addListener(resetScrollerListener)
    return expander
}
