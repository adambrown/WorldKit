package com.grimfox.gec

import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.ui.widgets.DynamicTextReference
import com.grimfox.gec.ui.widgets.TextureBuilder
import com.grimfox.gec.util.*
import java.io.File
import java.util.*
import javax.imageio.ImageIO

class RegionsBuilder(
        val regionFile: DynamicTextReference,
        val useRegionFile: Reference<Boolean>,
        val displayMode: ObservableMutableReference<DisplayMode>,
        val defaultToMap: ObservableMutableReference<Boolean>) {

    fun build(parameters: BuildContinent.RegionParameters, refreshOnly: Boolean = false, rebuildSplines: Boolean = true, updateView: Boolean = true) {
        val currentRegionGraph = currentState.regionGraph.value
        val currentRegionMask = currentState.regionMask.value
        val (regionGraph, regionMask) = if (refreshOnly && currentRegionGraph != null && currentRegionMask != null) {
            currentRegionGraph to currentRegionMask
        } else {
            val finalRegionFile = regionFile.reference.value
            if (useRegionFile.value && finalRegionFile.isNotBlank()) {
                val mask = loadRegionMaskFromImage(File(finalRegionFile))
                var water = 0
                var land = 0
                for (i in 0..mask.size.toInt() - 1) {
                    if (mask[i] < 1) {
                        water++
                    } else {
                        land++
                    }
                }
                val graph = Graphs.generateGraph(REGION_GRAPH_WIDTH, parameters.regionsSeed, 0.8)
                Pair(graph, mask)
            } else {
                BuildContinent.generateRegions(parameters.copy(), executor)
            }
        }
        val currentSplines = currentState.regionSplines.value
        val regionSplines = if (rebuildSplines || currentSplines == null) {
            var newSplines = BuildContinent.generateRegionSplines(Random(parameters.regionsSeed), regionGraph, regionMask, parameters.edgeDetailScale)
            if (currentSplines != null) {
                val newRiverOrigins = ArrayList(newSplines.riverOrigins)
                val newRiverPoints = ArrayList(newSplines.riverPoints)
                val newRiverEdges = ArrayList(newSplines.riverEdges)
                val newMountainOrigins = ArrayList(newSplines.mountainOrigins)
                val newMountainPoints = ArrayList(newSplines.mountainPoints)
                val newMountainEdges = ArrayList(newSplines.mountainEdges)
                val newIgnoredOrigins = ArrayList(newSplines.ignoredOrigins)
                val newIgnoredPoints = ArrayList(newSplines.ignoredPoints)
                val newIgnoredEdges = ArrayList(newSplines.ignoredEdges)
                val newDeletedOrigins = ArrayList(newSplines.deletedOrigins)
                val newDeletedPoints = ArrayList(newSplines.deletedPoints)
                val newDeletedEdges = ArrayList(newSplines.deletedEdges)
                for (i in newRiverOrigins.size - 1 downTo 0) {
                    val it = newRiverOrigins[i]
                    if (currentSplines.mountainOrigins.contains(it)) {
                        newMountainOrigins.add(newRiverOrigins.removeAt(i))
                        newMountainPoints.add(newRiverPoints.removeAt(i))
                        newMountainEdges.add(newRiverEdges.removeAt(i))
                    } else if (currentSplines.ignoredOrigins.contains(it)) {
                        newIgnoredOrigins.add(newRiverOrigins.removeAt(i))
                        newIgnoredPoints.add(newRiverPoints.removeAt(i))
                        newIgnoredEdges.add(newRiverEdges.removeAt(i))
                    } else if (currentSplines.deletedOrigins.contains(it)) {
                        newDeletedOrigins.add(newRiverOrigins.removeAt(i))
                        newDeletedPoints.add(newRiverPoints.removeAt(i))
                        newDeletedEdges.add(newRiverEdges.removeAt(i))
                    }
                }
                for (i in newMountainOrigins.size - 1 downTo 0) {
                    val it = newMountainOrigins[i]
                    if (currentSplines.riverOrigins.contains(it)) {
                        newRiverOrigins.add(newMountainOrigins.removeAt(i))
                        newRiverPoints.add(newMountainPoints.removeAt(i))
                        newRiverEdges.add(newMountainEdges.removeAt(i))
                    } else if (currentSplines.ignoredOrigins.contains(it)) {
                        newIgnoredOrigins.add(newMountainOrigins.removeAt(i))
                        newIgnoredPoints.add(newMountainPoints.removeAt(i))
                        newIgnoredEdges.add(newMountainEdges.removeAt(i))
                    } else if (currentSplines.deletedOrigins.contains(it)) {
                        newDeletedOrigins.add(newMountainOrigins.removeAt(i))
                        newDeletedPoints.add(newMountainPoints.removeAt(i))
                        newDeletedEdges.add(newMountainEdges.removeAt(i))
                    }
                }
                newSplines = BuildContinent.RegionSplines(
                        hasCustomizations = currentSplines.hasCustomizations,
                        coastEdges = newSplines.coastEdges,
                        coastPoints = newSplines.coastPoints,
                        riverOrigins = newRiverOrigins,
                        riverEdges = newRiverEdges,
                        riverPoints = newRiverPoints,
                        mountainOrigins = newMountainOrigins,
                        mountainEdges = newMountainEdges,
                        mountainPoints = newMountainPoints,
                        ignoredOrigins = newIgnoredOrigins,
                        ignoredEdges = newIgnoredEdges,
                        ignoredPoints = newIgnoredPoints,
                        deletedOrigins = newDeletedOrigins,
                        deletedEdges = newDeletedEdges,
                        deletedPoints = newDeletedPoints,
                        customRiverEdges = currentSplines.customRiverEdges,
                        customRiverPoints = currentSplines.customRiverPoints,
                        customMountainEdges = currentSplines.customMountainEdges,
                        customMountainPoints = currentSplines.customMountainPoints,
                        customIgnoredEdges = currentSplines.customIgnoredEdges,
                        customIgnoredPoints = currentSplines.customIgnoredPoints)
            }
            newSplines
        } else {
            currentSplines
        }
        currentState.regionParameters.value = parameters.copy()
        currentState.regionGraph.value = regionGraph
        currentState.regionMask.value = regionMask
        currentState.regionSplines.value = regionSplines
        currentState.heightMapTexture.value = null
        currentState.riverMapTexture.value = null
        if (updateView) {
            if (displayMode.value == DisplayMode.MAP || (defaultToMap.value && displayMode.value != DisplayMode.REGIONS)) {
                val mapTextureId = TextureBuilder.renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints + regionSplines.customRiverPoints, regionSplines.mountainPoints + regionSplines.customMountainPoints, regionSplines.ignoredPoints + regionSplines.customIgnoredPoints)
                meshViewport.setImage(mapTextureId)
                imageMode.value = 1
                displayMode.value = DisplayMode.MAP
            } else {
                val regionTextureId = Rendering.renderRegions(regionGraph, regionMask)
                meshViewport.setRegions(regionTextureId)
                imageMode.value = 0
                displayMode.value = DisplayMode.REGIONS
            }
        }
    }

    private fun loadRegionMaskFromImage(file: File): ByteArrayMatrix {
        val colorMap = LinkedHashMap<Int, Int>(16)
        colorMap[0] = 0
        val bufferedImage = ImageIO.read(file)
        val widthM1 = bufferedImage.width - 1
        val heightM1 = bufferedImage.height - 1
        var unknownColors = false
        for (y in 0..REGION_GRAPH_WIDTH_M1) {
            for (x in 0..REGION_GRAPH_WIDTH_M1) {
                val actualX = Math.round(((x + 0.5f) / REGION_GRAPH_WIDTH_F) * widthM1)
                val actualY = Math.round(((y + 0.5f) / REGION_GRAPH_WIDTH_F) * heightM1)
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
            colorMap.map { it.key to it.value }.filter { it.first != 0 }.sortedByDescending { it.first and 0x00FF0000 ushr 16 }.forEachIndexed { i, (first) -> colorMap[first] = i + 1 }
            colorMap[0] = 0
        } else {
            colorMap.clear()
            REGION_COLOR_INTS.forEachIndexed { i, value ->
                colorMap[value] = i
            }
        }
        return ByteArrayMatrix(256) { i ->
            (colorMap[bufferedImage.getRGB(Math.round((((i % REGION_GRAPH_WIDTH) + 0.5f) / REGION_GRAPH_WIDTH_F) * widthM1), Math.round((((i / REGION_GRAPH_WIDTH) + 0.5f) / REGION_GRAPH_WIDTH_F) * heightM1)) and 0X00FFFFFF]!! and 0x00FFFFFF).toByte()
        }
    }
}
