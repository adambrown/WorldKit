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
        val useBiomeFile: ObservableMutableReference<Boolean>,
        val displayMode: ObservableMutableReference<DisplayMode>) {

    fun build(parameters: BiomeParameters, refreshOnly: Boolean = false) {
        val currentBiomeGraph = currentState.biomeGraph.value
        val currentBiomeMask = currentState.biomeMask.value
        val (biomeGraph, biomeMask) = if (refreshOnly && currentBiomeGraph != null && currentBiomeMask != null) {
            currentBiomeGraph to currentBiomeMask
        } else {
            val finalBiomeFile = biomeFile.reference.value
            if (useBiomeFile.value && finalBiomeFile.isNotBlank()) {
                val mask = loadBiomeMaskFromImage(File(finalBiomeFile))
                for (i in 0..mask.size.toInt() - 1) {
                    mask[i] = ((mask[i].toInt() % parameters.biomes.size) + 1).toByte()
                }
                val graph = Graphs.generateGraph(BIOME_GRAPH_WIDTH, parameters.biomesSeed, 0.8)
                Pair(graph, mask)
            } else {
                val scale = ((parameters.biomesMapScale * parameters.biomesMapScale) / 400.0f).coerceIn(0.0f, 1.0f)
                val biomeScale = round(scale * 21) + 7
                val graph = Graphs.generateGraph(BIOME_GRAPH_WIDTH, parameters.biomesSeed, 0.8)
                BuildContinent.buildBiomeMaps(executor, parameters.biomesSeed, graph, parameters.biomes.size, biomeScale)
            }
        }
        currentState.biomeParameters.value = parameters
        currentState.biomeGraph.value = biomeGraph
        currentState.biomeMask.value = biomeMask
        currentState.biomes.value = parameters.biomes.map { ordinalToBiome(it) }
        currentState.heightMapTexture.value = null
        currentState.riverMapTexture.value = null
        val biomeTextureId = Rendering.renderRegions(biomeGraph, biomeMask)
        val currentSplines = currentState.regionSplines.value
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
        for (y in 0..BIOME_GRAPH_WIDTH_M1) {
            for (x in 0..BIOME_GRAPH_WIDTH_M1) {
                val actualX = round(((x + 0.5f) / BIOME_GRAPH_WIDTH_F) * widthM1)
                val actualY = round(((y + 0.5f) / BIOME_GRAPH_WIDTH_F) * heightM1)
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
        return ByteArrayMatrix(REGION_GRAPH_WIDTH) { i ->
            (colorMap[bufferedImage.getRGB(round((((i % BIOME_GRAPH_WIDTH) + 0.5f) / BIOME_GRAPH_WIDTH_F) * widthM1), round((((i / BIOME_GRAPH_WIDTH) + 0.5f) / BIOME_GRAPH_WIDTH_F) * heightM1)) and 0X00FFFFFF]!! and 0x00FFFFFF).toByte()
        }
    }
}