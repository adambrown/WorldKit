package com.grimfox.gec

import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.ui.widgets.DynamicTextReference
import com.grimfox.gec.ui.widgets.TextureBuilder
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.BiomeParameters
import java.io.File
import java.lang.Math.*
import javax.imageio.ImageIO

class BiomesBuilder(
        val biomeFile: DynamicTextReference,
        val useBiomeFile: MonitoredReference<Boolean>,
        val displayMode: MonitoredReference<DisplayMode>) {

    fun build(parameters: BiomeParameters, refreshOnly: Boolean = false) {
        val currentBiomeGraph = currentState.biomeGraph
        val currentBiomeMask = currentState.biomeMask
        val (biomeGraph, biomeMask) = if (refreshOnly && currentBiomeGraph != null && currentBiomeMask != null) {
            currentBiomeGraph to currentBiomeMask
        } else {
            val finalBiomeFile = biomeFile.reference.value
            if (useBiomeFile.value && finalBiomeFile.isNotBlank()) {
                val mask = loadBiomeMaskFromImage(File(finalBiomeFile))
                for (i in 0..mask.size.toInt() - 1) {
                    mask[i] = ((mask[i].toInt() % parameters.biomes.size) + 1).toByte()
                }
                val graph = Graphs.generateGraph(128, parameters.biomesSeed, 0.8)
                Pair(graph, mask)
            } else {
                val scale = ((parameters.biomesMapScale * parameters.biomesMapScale) / 400.0f).coerceIn(0.0f, 1.0f)
                val biomeScale = round(scale * 21) + 7
                val graph = Graphs.generateGraph(128, parameters.biomesSeed, 0.8)
                BuildContinent.buildBiomeMaps(executor, parameters.biomesSeed, graph, parameters.biomes.size, biomeScale)
            }
        }
        currentState.biomeParameters = parameters
        currentState.biomeGraph = biomeGraph
        currentState.biomeMask = biomeMask
        currentState.biomes = parameters.biomes.map { ordinalToBiome(it) }
        currentState.heightMapTexture = null
        currentState.riverMapTexture = null
        val biomeTextureId = Rendering.renderRegions(biomeGraph, biomeMask)
        val currentSplines = currentState.regionSplines
        val splineTextureId = if (currentSplines != null) {
            TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
        } else {
            TextureBuilder.renderSplines(emptyList(), emptyList(), emptyList())
        }
        meshViewport.setBiomes(biomeTextureId, splineTextureId)
        imageMode.value = 2
        displayMode.value = DisplayMode.BIOMES
    }

    private fun loadBiomeMaskFromImage(file: File): ByteArrayMatrix {
        val colorMap = LinkedHashMap<Int, Int>(16)
        val bufferedImage = ImageIO.read(file)
        val widthM1 = bufferedImage.width - 1
        val heightM1 = bufferedImage.height - 1
        var unknownColors = false
        for (y in 0..127) {
            for (x in 0..127) {
                val actualX = round(((x + 0.5f) / 128.0f) * widthM1)
                val actualY = round(((y + 0.5f) / 128.0f) * heightM1)
                val imageValue = bufferedImage.getRGB(actualX, actualY) and 0x00FFFFFF
                val curVal = colorMap.putIfAbsent(imageValue, colorMap.size)
                if (!unknownColors && curVal == null) {
                    if (!REGION_COLOR_INTS.contains(imageValue)) {
                        unknownColors = true
                    }
                }
            }
        }
        if (unknownColors) {
            colorMap.map { it.key to it.value }.sortedByDescending { it.first and 0x00FF0000 ushr 16 }.forEachIndexed { i, (first) -> colorMap[first] = i }
        } else {
            colorMap.clear()
            REGION_COLOR_INTS.forEachIndexed { i, value ->
                colorMap[value] = i - 1
            }
        }
        return ByteArrayMatrix(128) { i ->
            (colorMap[bufferedImage.getRGB(round((((i % 128) + 0.5f) / 128.0f) * widthM1), round((((i / 128) + 0.5f) / 128.0f) * heightM1)) and 0X00FFFFFF]!! and 0x00FFFFFF).toByte()
        }
    }
}