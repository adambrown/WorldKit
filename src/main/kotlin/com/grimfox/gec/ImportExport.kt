package com.grimfox.gec

import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.HistoryQueue
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.ui.JSON
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.Block
import com.grimfox.gec.util.BuildContinent
import com.grimfox.gec.util.FileDialogs
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun importProjectFile(file: File): Project? {
return null
}

fun exportProjectFile(project: Project, file: File) {
    DataOutputStream(GZIPOutputStream(file.outputStream()).buffered()).use { stream ->
        val currentState = project.currentState
        val regionParameters = currentState.regionParameters
        val regionGraph = currentState.regionGraph
        val regionMask = currentState.regionMask
        if (regionParameters != null && regionGraph != null && regionMask != null) {
            stream.writeBoolean(true)
            stream.writeRegionsHistoryItem(RegionsHistoryItem(regionParameters, regionGraph.seed, regionMask))
        } else {
            stream.writeBoolean(false)
        }
        val regionSplines = currentState.regionSplines
        if (regionSplines != null) {
            stream.writeBoolean(true)
            stream.writeRegionSplines(regionSplines)
        } else {
            stream.writeBoolean(false)
        }
        val biomeParameters = currentState.biomeParameters
        val biomeGraph = currentState.biomeGraph
        val biomeMask = currentState.biomeMask
        if (biomeParameters != null && biomeGraph != null && biomeMask != null) {
            stream.writeBoolean(true)
            stream.writeBiomesHistoryItem(BiomesHistoryItem(biomeParameters, biomeGraph.seed, biomeMask))
        } else {
            stream.writeBoolean(false)
        }
        stream.writeHistoryQueue(project.historyRegionsBackQueue) {
            writeRegionsHistoryItem(it)
        }
        val regionsCurrentValue = project.historyRegionsCurrent.value
        if (regionsCurrentValue == null) {
            stream.writeBoolean(false)
        } else {
            stream.writeBoolean(true)
            stream.writeRegionsHistoryItem(regionsCurrentValue)
        }
        stream.writeHistoryQueue(project.historyRegionsForwardQueue) {
            writeRegionsHistoryItem(it)
        }
        stream.writeHistoryQueue(project.historySplinesBackQueue) {
            writeRegionSplines(it)
        }
        val splinesCurrentValue = project.historySplinesCurrent.value
        if (splinesCurrentValue == null) {
            stream.writeBoolean(false)
        } else {
            stream.writeBoolean(true)
            stream.writeRegionSplines(splinesCurrentValue)
        }
        stream.writeHistoryQueue(project.historySplinesForwardQueue) {
            writeRegionSplines(it)
        }

        stream.writeHistoryQueue(project.historyBiomesBackQueue) {
            writeBiomesHistoryItem(it)
        }
        val biomesCurrentValue = project.historyBiomesCurrent.value
        if (biomesCurrentValue == null) {
            stream.writeBoolean(false)
        } else {
            stream.writeBoolean(true)
            stream.writeBiomesHistoryItem(biomesCurrentValue)
        }
        stream.writeHistoryQueue(project.historyBiomesForwardQueue) {
            writeBiomesHistoryItem(it)
        }
    }
}

private fun <T> DataOutputStream.writeHistoryQueue(queue: HistoryQueue<T>, serializer: DataOutputStream.(T) -> Unit) {
    val (buffer, head, tail, size, limit) = queue.serializableData()
    writeInt(head)
    writeInt(tail)
    writeInt(size)
    writeInt(limit)
    buffer.forEach {
        if (it == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            serializer(it)
        }
    }
}

fun importRegionsFile(dialogLayer: Block, preferences: Preferences, ui: UserInterface): RegionsHistoryItem? {
    return FileDialogs.selectFile(dialogLayer, true, ui, preferences.projectDir, "wkr") { file ->
        if (file == null) {
            null
        } else {
            val historyItem = DataInputStream(GZIPInputStream(file.inputStream()).buffered()).use { stream ->
                stream.readInt()
                val parameters = JSON.readValue(stream.readUTF(), BuildContinent.RegionParameters::class.java)
                val graphSeed = stream.readLong()
                val maskWidth = stream.readInt()
                val maskBytes = ByteArray(maskWidth * maskWidth)
                stream.readFully(maskBytes)
                val regionMask = ByteArrayMatrix(maskWidth, maskBytes)
                RegionsHistoryItem(parameters, graphSeed, regionMask)
            }
            historyItem
        }
    }
}

fun exportRegionsFile(regions: RegionsHistoryItem?, dialogLayer: Block, preferences: Preferences, ui: UserInterface): Boolean {
    if (regions != null) {
        ui.ignoreInput = true
        dialogLayer.isVisible = true
        try {
            val saveFile = FileDialogs.saveFileDialog(preferences.projectDir, "wkr")
            if (saveFile != null) {
                val fullNameWithExtension = "${saveFile.name.removeSuffix(".wkr")}.wkr"
                val actualFile = File(saveFile.parentFile, fullNameWithExtension)
                DataOutputStream(GZIPOutputStream(actualFile.outputStream()).buffered()).use { stream ->
                    stream.writeRegionsHistoryItem(regions)
                }
                return true
            } else {
                return false
            }
        } finally {
            dialogLayer.isVisible = false
            ui.ignoreInput = false
        }
    }
    return false
}

private fun DataOutputStream.writeRegionsHistoryItem(regions: RegionsHistoryItem) {
    writeInt(1)
    val parameters = JSON.writeValueAsString(regions.parameters)
    writeUTF(parameters)
    writeLong(regions.graphSeed)
    writeInt(regions.mask.width)
    write(regions.mask.array)
}

fun importSplinesFile(dialogLayer: Block, preferences: Preferences, ui: UserInterface): BuildContinent.RegionSplines? {
    return FileDialogs.selectFile(dialogLayer, true, ui, preferences.projectDir, "wks") { file ->
        if (file == null) {
            null
        } else {
            val historyItem = DataInputStream(GZIPInputStream(file.inputStream()).buffered()).use { stream ->
                stream.readInt()
                BuildContinent.RegionSplines(
                        hasCustomizations = stream.readBoolean(),
                        coastEdges = stream.readCoastEdges(),
                        coastPoints = stream.readCoastPoints(),
                        riverOrigins = stream.readPoint2FListList(),
                        riverEdges = stream.readLineSegment2FListList(),
                        riverPoints = stream.readPoint2FListList(),
                        mountainOrigins = stream.readPoint2FListList(),
                        mountainEdges = stream.readLineSegment2FListList(),
                        mountainPoints = stream.readPoint2FListList(),
                        ignoredOrigins = stream.readPoint2FListList(),
                        ignoredEdges = stream.readLineSegment2FListList(),
                        ignoredPoints = stream.readPoint2FListList(),
                        deletedOrigins = stream.readPoint2FListList(),
                        deletedEdges = stream.readLineSegment2FListList(),
                        deletedPoints = stream.readPoint2FListList(),
                        customRiverEdges = stream.readLineSegment2FListList(),
                        customRiverPoints = stream.readPoint2FListList(),
                        customMountainEdges = stream.readLineSegment2FListList(),
                        customMountainPoints = stream.readPoint2FListList(),
                        customIgnoredEdges = stream.readLineSegment2FListList(),
                        customIgnoredPoints = stream.readPoint2FListList()
                )
            }
            historyItem
        }
    }
}

fun exportSplinesFile(splines: BuildContinent.RegionSplines?, dialogLayer: Block, preferences: Preferences, ui: UserInterface): Boolean {
    if (splines != null) {
        ui.ignoreInput = true
        dialogLayer.isVisible = true
        try {
            val saveFile = FileDialogs.saveFileDialog(preferences.projectDir, "wks")
            if (saveFile != null) {
                val fullNameWithExtension = "${saveFile.name.removeSuffix(".wks")}.wks"
                val actualFile = File(saveFile.parentFile, fullNameWithExtension)
                DataOutputStream(GZIPOutputStream(actualFile.outputStream()).buffered()).use { stream ->
                    stream.writeRegionSplines(splines)
                }
                return true
            } else {
                return false
            }
        } finally {
            dialogLayer.isVisible = false
            ui.ignoreInput = false
        }
    }
    return false
}

private fun DataOutputStream.writeRegionSplines(splines: BuildContinent.RegionSplines) {
    writeInt(1)
    writeBoolean(splines.hasCustomizations)
    writeCoastEdges(splines.coastEdges)
    writeCoastPoints(splines.coastPoints)
    writePoint2FListList(splines.riverOrigins)
    writeLineSegment2FListList(splines.riverEdges)
    writePoint2FListList(splines.riverPoints)
    writePoint2FListList(splines.mountainOrigins)
    writeLineSegment2FListList(splines.mountainEdges)
    writePoint2FListList(splines.mountainPoints)
    writePoint2FListList(splines.ignoredOrigins)
    writeLineSegment2FListList(splines.ignoredEdges)
    writePoint2FListList(splines.ignoredPoints)
    writePoint2FListList(splines.deletedOrigins)
    writeLineSegment2FListList(splines.deletedEdges)
    writePoint2FListList(splines.deletedPoints)
    writeLineSegment2FListList(splines.customRiverEdges)
    writePoint2FListList(splines.customRiverPoints)
    writeLineSegment2FListList(splines.customMountainEdges)
    writePoint2FListList(splines.customMountainPoints)
    writeLineSegment2FListList(splines.customIgnoredEdges)
    writePoint2FListList(splines.customIgnoredPoints)
}

fun importBiomesFile(dialogLayer: Block, preferences: Preferences, ui: UserInterface): BiomesHistoryItem? {
    return FileDialogs.selectFile(dialogLayer, true, ui, preferences.projectDir, "wkb") { file ->
        if (file == null) {
            null
        } else {
            val historyItem = DataInputStream(GZIPInputStream(file.inputStream()).buffered()).use { stream ->
                stream.readInt()
                val parameters = JSON.readValue(stream.readUTF(), BuildContinent.BiomeParameters::class.java)
                val graphSeed = stream.readLong()
                val maskWidth = stream.readInt()
                val maskBytes = ByteArray(maskWidth * maskWidth)
                stream.readFully(maskBytes)
                val biomesMask = ByteArrayMatrix(maskWidth, maskBytes)
                BiomesHistoryItem(parameters, graphSeed, biomesMask)
            }
            historyItem
        }
    }
}

fun exportBiomesFile(biomes: BiomesHistoryItem?, dialogLayer: Block, preferences: Preferences, ui: UserInterface): Boolean {
    if (biomes != null) {
        ui.ignoreInput = true
        dialogLayer.isVisible = true
        try {
            val saveFile = FileDialogs.saveFileDialog(preferences.projectDir, "wkb")
            if (saveFile != null) {
                val fullNameWithExtension = "${saveFile.name.removeSuffix(".wkb")}.wkb"
                val actualFile = File(saveFile.parentFile, fullNameWithExtension)
                DataOutputStream(GZIPOutputStream(actualFile.outputStream()).buffered()).use { stream ->
                    stream.writeBiomesHistoryItem(biomes)
                }
                return true
            } else {
                return false
            }
        } finally {
            dialogLayer.isVisible = false
            ui.ignoreInput = false
        }
    }
    return false
}

private fun DataOutputStream.writeBiomesHistoryItem(biomes: BiomesHistoryItem) {
    writeInt(1)
    val parameters = JSON.writeValueAsString(biomes.parameters)
    writeUTF(parameters)
    writeLong(biomes.graphSeed)
    writeInt(biomes.mask.width)
    write(biomes.mask.array)
}

private fun DataOutputStream.writeCoastEdges(coastEdges: List<Pair<List<LineSegment2F>, List<List<LineSegment2F>>>>) {
    writeInt(coastEdges.size)
    coastEdges.forEach {
        writeLineSegment2FList(it.first)
        writeLineSegment2FListList(it.second)
    }
}

private fun DataOutputStream.writeCoastPoints(coastPoints: List<Pair<List<Point2F>, List<List<Point2F>>>>) {
    writeInt(coastPoints.size)
    coastPoints.forEach {
        writePoint2FList(it.first)
        writePoint2FListList(it.second)
    }
}

private fun DataOutputStream.writeLineSegment2FListList(lines: List<List<LineSegment2F>>) {
    writeInt(lines.size)
    lines.forEach {
        writeLineSegment2FList(it)
    }
}

private fun DataOutputStream.writeLineSegment2FList(lines: List<LineSegment2F>) {
    writeInt(lines.size)
    lines.forEach {
        writeLineSegment2F(it)
    }
}

private fun DataOutputStream.writePoint2FListList(points: List<List<Point2F>>) {
    writeInt(points.size)
    points.forEach {
        writePoint2FList(it)
    }
}

private fun DataOutputStream.writePoint2FList(points: List<Point2F>) {
    writeInt(points.size)
    points.forEach {
        writePoint2F(it)
    }
}

private fun DataOutputStream.writeLineSegment2F(line: LineSegment2F) {
    writePoint2F(line.a)
    writePoint2F(line.b)
}

private fun DataOutputStream.writePoint2F(point: Point2F) {
    writeFloat(point.x)
    writeFloat(point.y)
}

private fun DataInputStream.readCoastEdges(): List<Pair<List<LineSegment2F>, List<List<LineSegment2F>>>> {
    val size = readInt()
    val coastEdges = ArrayList<Pair<List<LineSegment2F>, List<List<LineSegment2F>>>>(size)
    for (i in 1..size) {
        coastEdges.add(readLineSegment2FList() to readLineSegment2FListList())
    }
    return coastEdges
}

private fun DataInputStream.readCoastPoints(): List<Pair<List<Point2F>, List<List<Point2F>>>> {
    val size = readInt()
    val coastPoints = ArrayList<Pair<List<Point2F>, List<List<Point2F>>>>(size)
    for (i in 1..size) {
        coastPoints.add(readPoint2FList() to readPoint2FListList())
    }
    return coastPoints
}

private fun DataInputStream.readLineSegment2FListList(): List<List<LineSegment2F>> {
    val size = readInt()
    val lines = ArrayList<List<LineSegment2F>>(size)
    for (i in 1..size) {
        lines.add(readLineSegment2FList())
    }
    return lines
}

private fun DataInputStream.readLineSegment2FList(): List<LineSegment2F> {
    val size = readInt()
    val lines = ArrayList<LineSegment2F>(size)
    for (i in 1..size) {
        lines.add(readLineSegment2F())
    }
    return lines
}

private fun DataInputStream.readPoint2FListList(): List<List<Point2F>> {
    val size = readInt()
    val points = ArrayList<List<Point2F>>(size)
    for (i in 1..size) {
        points.add(readPoint2FList())
    }
    return points
}

private fun DataInputStream.readPoint2FList(): List<Point2F> {
    val size = readInt()
    val points = ArrayList<Point2F>(size)
    for (i in 1..size) {
        points.add(readPoint2F())
    }
    return points
}

private fun DataInputStream.readLineSegment2F(): LineSegment2F {
    return (LineSegment2F(readPoint2F(), readPoint2F()))
}

private fun DataInputStream.readPoint2F(): Point2F {
    return Point2F(readFloat(), readFloat())
}

