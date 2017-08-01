package com.grimfox.gec

import com.grimfox.gec.model.*
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Polygon2F
import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.ui.widgets.MeshViewport3D.BrushListener
import com.grimfox.gec.ui.widgets.MeshViewport3D.PointPicker
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.extractTextureRgbaByte
import com.grimfox.gec.ui.widgets.TextureBuilder.renderMapImage
import com.grimfox.gec.ui.widgets.TextureBuilder.renderSplineSelectors
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.ParameterSet
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.gec.util.BuildContinent.buildBiomeMaps
import com.grimfox.gec.util.BuildContinent.buildOpenEdges
import com.grimfox.gec.util.BuildContinent.generateRegionSplines
import com.grimfox.gec.util.BuildContinent.generateRegions
import com.grimfox.gec.util.BuildContinent.generateWaterFlows
import com.grimfox.gec.util.Rendering.renderRegions
import org.lwjgl.glfw.GLFW
import java.io.File
import java.lang.Math.round
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO
import kotlin.collections.LinkedHashMap

class SplinePointPicker(val currentState: CurrentState, val currentSplines: RegionSplines, val splineMap: LinkedHashMap<Int, Quadruple<Int, Int, List<Point2F>, List<LineSegment2F>>>, val mask: Matrix<Short>, val texture: MutableReference<TextureId>): PointPicker {

    private val maskWidth = mask.width
    private val maskWidthM1 = maskWidth - 1

    override fun onMouseDown(x: Float, y: Float) {
        val selectedId = mask[round(x * maskWidthM1), round(y * maskWidthM1)].toInt()
        if (selectedId > 0) {
            val splineToToggle = splineMap[selectedId]
            if (splineToToggle != null) {
                splineMap.put(selectedId, Quadruple(splineToToggle.first, (splineToToggle.second + 1) % 3, splineToToggle.third, splineToToggle.fourth))
                val riverEdges = ArrayList<List<LineSegment2F>>()
                val riverPoints = ArrayList<List<Point2F>>()
                val mountainEdges = ArrayList<List<LineSegment2F>>()
                val mountainPoints = ArrayList<List<Point2F>>()
                val ignoredEdges = ArrayList<List<LineSegment2F>>()
                val ignoredPoints = ArrayList<List<Point2F>>()
                splineMap.forEach {
                    when (it.value.second) {
                        0 -> {
                            riverEdges.add(it.value.fourth)
                            riverPoints.add(it.value.third)
                        }
                        1 -> {
                            mountainEdges.add(it.value.fourth)
                            mountainPoints.add(it.value.third)
                        }
                        else -> {
                            ignoredEdges.add(it.value.fourth)
                            ignoredPoints.add(it.value.third)
                        }
                    }
                }
                currentState.regionSplines = RegionSplines(currentSplines.coastEdges, currentSplines.coastPoints, riverEdges, riverPoints, mountainEdges, mountainPoints, ignoredEdges, ignoredPoints)
                executor.call {
                    if (texture.value.id < 0) {
                        texture.value = renderMapImage(currentSplines.coastPoints, riverPoints, mountainPoints, ignoredPoints)
                    } else {
                        renderMapImage(currentSplines.coastPoints, riverPoints, mountainPoints, ignoredPoints, listOf(), texture.value)
                    }
                }
            }
        }
    }
}

class SplineDeletePicker(val currentState: CurrentState, val currentSplines: RegionSplines, val splineMap: LinkedHashMap<Int, Quintuple<Int, Int, List<Point2F>, List<LineSegment2F>, Boolean>>, val mask: Matrix<Short>, val texture: MutableReference<TextureId>): PointPicker {

    private val maskWidth = mask.width
    private val maskWidthM1 = maskWidth - 1

    override fun onMouseDown(x: Float, y: Float) {
        val selectedId = mask[round(x * maskWidthM1), round(y * maskWidthM1)].toInt()
        if (selectedId > 0) {
            val splineToToggle = splineMap[selectedId]
            if (splineToToggle != null) {
                val riverEdges = ArrayList<List<LineSegment2F>>()
                val riverPoints = ArrayList<List<Point2F>>()
                val mountainEdges = ArrayList<List<LineSegment2F>>()
                val mountainPoints = ArrayList<List<Point2F>>()
                val ignoredEdges = ArrayList<List<LineSegment2F>>()
                val ignoredPoints = ArrayList<List<Point2F>>()
                val pendingEdges = ArrayList<List<LineSegment2F>>()
                val pendingPoints = ArrayList<List<Point2F>>()
                splineMap.put(selectedId, Quintuple(splineToToggle.first, splineToToggle.second, splineToToggle.third, splineToToggle.fourth, !splineToToggle.fifth))
                splineMap.forEach {
                    if (it.value.fifth) {
                        pendingEdges.add(it.value.fourth)
                        pendingPoints.add(it.value.third)
                    } else {
                        when (it.value.second) {
                            0 -> {
                                riverEdges.add(it.value.fourth)
                                riverPoints.add(it.value.third)
                            }
                            1 -> {
                                mountainEdges.add(it.value.fourth)
                                mountainPoints.add(it.value.third)
                            }
                            else -> {
                                ignoredEdges.add(it.value.fourth)
                                ignoredPoints.add(it.value.third)
                            }
                        }
                    }
                }
                currentState.regionSplines = RegionSplines(currentSplines.coastEdges, currentSplines.coastPoints, riverEdges, riverPoints, mountainEdges, mountainPoints, ignoredEdges, ignoredPoints)
                executor.call {
                    if (texture.value.id < 0) {
                        texture.value = renderMapImage(currentSplines.coastPoints, riverPoints, mountainPoints, ignoredPoints, pendingPoints)
                    } else {
                        renderMapImage(currentSplines.coastPoints, riverPoints, mountainPoints, ignoredPoints, pendingPoints, texture.value)
                    }
                }
            }
        }
    }
}

class PickAndGoDrawBrushListener(val graph: Graph, val mask: Matrix<Byte>, val brushSize: Reference<Float>, val texture: MutableReference<TextureId>): BrushListener {

    private val maskWidth = mask.width
    private val maskWidthM1 = maskWidth - 1

    var currentValue = 0.toByte()

    override fun onMouseDown(x: Float, y: Float) {
        currentValue = mask[round(x * maskWidthM1), round(y * maskWidthM1)]
    }

    override fun onLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val maxDist = (brushSize.value / 2.0f)
        val maxDist2 = maxDist * maxDist
        val brushMargin = Math.ceil(maxDist.toDouble() * maskWidthM1).toInt() + 1
        val cpStartX = Math.max(0, round(Math.min(x1, x2) * maskWidthM1) - brushMargin)
        val cpEndX = Math.min(maskWidthM1, round(Math.max(x1, x2) * maskWidthM1) + brushMargin)
        val cpStartY = Math.max(0, round(Math.min(y1, y2) * maskWidthM1) - brushMargin)
        val cpEndY = Math.min(maskWidthM1, round(Math.max(y1, y2) * maskWidthM1) + brushMargin)
        val line = LineSegment2F(Point2F(x1, y1), Point2F(x2, y2))
        val vertices = graph.vertices
        for (x in cpStartX..cpEndX) {
            for (y in cpStartY..cpEndY) {
                val dist = line.distance2(vertices.getPoint(y * maskWidth + x))
                if (dist <= maxDist2) {
                    mask[x, y] = currentValue
                }
            }
        }
        executor.call {
            if (texture.value.id < 0) {
                texture.value = renderRegions(graph, mask)
            } else {
                renderRegions(graph, mask, texture.value)
            }
        }
    }

    override fun onMouseUp(x1: Float, y1: Float, x2: Float, y2: Float) {}
}

class PreSelectDrawBrushListener(val graph: Graph, val mask: Matrix<Byte>, val brushSize: Reference<Float>, val texture: MutableReference<TextureId>, val currentValue: Reference<Byte>): BrushListener {

    private val maskWidth = mask.width
    private val maskWidthM1 = maskWidth - 1

    override fun onMouseDown(x: Float, y: Float) {
        onLine(x, y, x, y)
    }

    override fun onLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val maxDist = (brushSize.value / 2.0f)
        val maxDist2 = maxDist * maxDist
        val brushMargin = Math.ceil(maxDist.toDouble() * maskWidthM1).toInt() + 1
        val cpStartX = Math.max(0, round(Math.min(x1, x2) * maskWidthM1) - brushMargin)
        val cpEndX = Math.min(maskWidthM1, round(Math.max(x1, x2) * maskWidthM1) + brushMargin)
        val cpStartY = Math.max(0, round(Math.min(y1, y2) * maskWidthM1) - brushMargin)
        val cpEndY = Math.min(maskWidthM1, round(Math.max(y1, y2) * maskWidthM1) + brushMargin)
        val line = LineSegment2F(Point2F(x1, y1), Point2F(x2, y2))
        val vertices = graph.vertices
        for (x in cpStartX..cpEndX) {
            for (y in cpStartY..cpEndY) {
                val dist = line.distance2(vertices.getPoint(y * maskWidth + x))
                if (dist <= maxDist2) {
                    mask[x, y] = currentValue.value
                }
            }
        }
        executor.call {
            if (texture.value.id < 0) {
                texture.value = renderRegions(graph, mask)
            } else {
                renderRegions(graph, mask, texture.value)
            }
        }
    }

    override fun onMouseUp(x1: Float, y1: Float, x2: Float, y2: Float) {}
}

class SplineDrawBrushListener(val currentState: CurrentState, val currentSplines: RegionSplines, val splineMap: LinkedHashMap<Int, Quadruple<Int, Int, List<Point2F>, List<LineSegment2F>>>, val texture: MutableReference<TextureId>): BrushListener {

    private var currentSpline: MutableList<Point2F>? = null

    override fun onMouseDown(x: Float, y: Float) {
        currentSpline = ArrayList()
        onLine(x, y, x, y)
    }

    override fun onLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val currentSpline = currentSpline
        if (currentSpline != null) {
            currentSpline.add(Point2F(x2, y2))
            val riverEdges = ArrayList<List<LineSegment2F>>()
            val riverPoints = ArrayList<List<Point2F>>()
            val mountainEdges = ArrayList<List<LineSegment2F>>()
            val mountainPoints = ArrayList<List<Point2F>>()
            val ignoredEdges = ArrayList<List<LineSegment2F>>()
            val ignoredPoints = ArrayList<List<Point2F>>()
            splineMap.forEach {
                when (it.value.second) {
                    0 -> {
                        riverEdges.add(it.value.fourth)
                        riverPoints.add(it.value.third)
                    }
                    1 -> {
                        mountainEdges.add(it.value.fourth)
                        mountainPoints.add(it.value.third)
                    }
                    else -> {
                        ignoredEdges.add(it.value.fourth)
                        ignoredPoints.add(it.value.third)
                    }
                }
            }
            val smoothing = round(((1.0f - ((currentState.parameters?.regionsMapScale ?: 4) / 20.0f)).coerceIn(0.0f, 1.0f) * 11) + 13)
            val pendingPoints = if (currentSpline.size == 1) {
                listOf(buildOpenEdges(Polygon2F(currentSpline + currentSpline, false), smoothing))
            } else {
                listOf(buildOpenEdges(Polygon2F(currentSpline, false), smoothing))
            }
            executor.call {
                if (texture.value.id < 0) {
                    texture.value = renderMapImage(currentSplines.coastPoints, riverPoints, mountainPoints, ignoredPoints, pendingPoints)
                } else {
                    renderMapImage(currentSplines.coastPoints, riverPoints, mountainPoints, ignoredPoints, pendingPoints, texture.value)
                }
            }
        }
    }

    override fun onMouseUp(x1: Float, y1: Float, x2: Float, y2: Float) {
        val currentSpline = currentSpline
        if (currentSpline != null) {
            if (x1 in 0.0f..1.0f && y1 in 0.0f..1.0f && x2 in 0.0f..1.0f && y2 in 0.0f..1.0f) {
                currentSpline.add(Point2F(x2, y2))
            }
            val riverEdges = ArrayList<List<LineSegment2F>>()
            val riverPoints = ArrayList<List<Point2F>>()
            val mountainEdges = ArrayList<List<LineSegment2F>>()
            val mountainPoints = ArrayList<List<Point2F>>()
            val ignoredEdges = ArrayList<List<LineSegment2F>>()
            val ignoredPoints = ArrayList<List<Point2F>>()
            splineMap.forEach {
                when (it.value.second) {
                    0 -> {
                        riverEdges.add(it.value.fourth)
                        riverPoints.add(it.value.third)
                    }
                    1 -> {
                        mountainEdges.add(it.value.fourth)
                        mountainPoints.add(it.value.third)
                    }
                    else -> {
                        ignoredEdges.add(it.value.fourth)
                        ignoredPoints.add(it.value.third)
                    }
                }
            }
            val smoothing = round(((1.0f - ((currentState.parameters?.regionsMapScale ?: 4) / 20.0f)).coerceIn(0.0f, 1.0f) * 11) + 13)
            val newPoints = if (currentSpline.size == 1) {
                buildOpenEdges(Polygon2F(currentSpline + currentSpline, false), smoothing)
            } else {
                buildOpenEdges(Polygon2F(currentSpline, false), smoothing)
            }
            val newEdges = (1..newPoints.size - 1).mapTo(ArrayList()) { LineSegment2F(newPoints[it - 1], newPoints[it]) }
            ignoredPoints.add(newPoints)
            ignoredEdges.add(newEdges)
            splineMap.put(splineMap.size + 1, Quadruple(splineMap.size + 1, 2, newPoints, newEdges))
            currentState.regionSplines = RegionSplines(currentSplines.coastEdges, currentSplines.coastPoints, riverEdges, riverPoints, mountainEdges, mountainPoints, ignoredEdges, ignoredPoints)
            this.currentSpline = null
            executor.call {
                if (texture.value.id < 0) {
                    texture.value = renderMapImage(currentSplines.coastPoints, riverPoints, mountainPoints, ignoredPoints, listOf())
                } else {
                    renderMapImage(currentSplines.coastPoints, riverPoints, mountainPoints, ignoredPoints, listOf(), texture.value)
                }
            }
        }
    }
}

private val biomeValues = linkedMapOf(
        "Mountains" to 0,
        "Coastal mountains" to 1,
        "Foothills" to 2,
        "Rolling hills" to 3,
        "Plateaus" to 4,
        "Plains" to 5
)

private val cachedGraph256 = preferences.cachedGraph256!!

private val cachedGraph512 = preferences.cachedGraph512!!

private val cachedGraph1024 = preferences.cachedGraph1024!!

fun Block.leftPanel(ui: UserInterface, uiLayout: UiLayout, dialogLayer: Block): Block {
    return block {
        val leftPanel = this
        hSizing = Sizing.STATIC
        width = 370.0f
        layout = Layout.HORIZONTAL
        hAlign = HorizontalAlignment.LEFT
        block {
            hSizing = Sizing.GROW
            layout = Layout.HORIZONTAL
            hAlign = HorizontalAlignment.LEFT
            block {
                xOffset = SMALL_SPACER_SIZE
                yOffset = SMALL_SPACER_SIZE
                width = -SMALL_SPACER_SIZE
                height = -2 * SMALL_SPACER_SIZE
                block {
                    xOffset = 8.0f
                    yOffset = 8.0f
                    width = -16.0f
                    height = -16.0f
                    leftPanelWidgets(ui, uiLayout, dialogLayer)
                    block {
                        vSizing = Sizing.STATIC
                        height = 1.0f
                        layout = Layout.VERTICAL
                        shape = SHAPE_BORDER_AND_FRAME_RECTANGLE
                    }
                }
                block {
                    shape = SHAPE_BORDER_ONLY
                    canOverflow = true
                    isMouseAware = false
                }
            }
        }
        block {
            hSizing = Sizing.STATIC
            width = SMALL_SPACER_SIZE
            layout = Layout.HORIZONTAL
            val grabber = button(NO_TEXT, NORMAL_TEXT_BUTTON_STYLE {
                copy(template = template.copy(
                        vSizing = Sizing.STATIC,
                        height = 3 * LARGE_ROW_HEIGHT,
                        vAlign = VerticalAlignment.MIDDLE,
                        hSizing = Sizing.RELATIVE,
                        width = -2.0f,
                        hAlign = HorizontalAlignment.CENTER))
            })
            var lastX = 0.0f
            onMouseDown { button, x, _, _ ->
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    lastX = x.toFloat()
                }
            }
            onMouseDrag { button, x, _, _ ->
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    val delta = x - lastX
                    val adjustedDelta = Math.max(370.0f, Math.min(root.width * 0.75f, leftPanel.width + delta)) - leftPanel.width
                    lastX += adjustedDelta
                    leftPanel.width += adjustedDelta
                }
            }
            supplantEvents(grabber)
        }
    }
}

private val shrinkGroup = hShrinkGroup()
private val regionFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val biomeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val useRegionFile = ref(false)
private val useBiomeFile = ref(false)
private val allowUnsafe = ref(false)
private val editRegionsMode = ref(false)
private val drawSplinesMode = ref(false)
private val editSplinesMode = ref(false)
private val deleteSplinesMode = ref(false)
private val editBiomesMode = ref(false)
private val regionsSeed = ref(1L)
private val biomesSeed = ref(1L)
private val regionsMapScale = ref(4)
private val biomesMapScale = ref(4)
private val biomeCount = ref(1)
private val regions = ref(8)
private val islands = ref(1)
private val stride = ref(7)
private val startPoints = ref(2)
private val reduction = ref(7)
private val connectedness = ref(0.114f)
private val regionSize = ref(0.034f)
private val iterations = ref(5)
private val currentBiomeBrushValue = ref(1.toByte())
private val historyRegionsBackQueue = HistoryQueue<ParameterSet>(100)
private val historyRegionsCurrent = ref<ParameterSet?>(null)
private val historyRegionsForwardQueue = HistoryQueue<ParameterSet>(100)
private val historyBiomesBackQueue = HistoryQueue<ParameterSet>(100)
private val historyBiomesCurrent = ref<ParameterSet?>(null)
private val historyBiomesForwardQueue = HistoryQueue<ParameterSet>(100)
private val onUpdateParamsFun = {
    if (!allowUnsafe.value) {
        stride.value = ((regions.value + 3) / 4) + 5
        val stride2 = stride.value * stride.value
        val approxInland = stride2 - (4 * stride.value) + 4
        startPoints.value = Math.min(6, Math.max(1, Math.floor(approxInland / (regions.value.toDouble() + (islands.value / 2))).toInt() - 1))
        reduction.value = Math.floor((approxInland - (startPoints.value * regions.value)) * 0.8).toInt()
        connectedness.value = round(((1.0f / stride.value) * 0.77f) * 1000.0f) / 1000.0f
        val unitLength = 1.0f / stride.value
        val unit2 = unitLength * unitLength
        regionSize.value = round((((unit2 * (approxInland - reduction.value)) / regions.value) * 0.763f) * 1000.0f) / 1000.0f
    }
}
private val generateLock = ReentrantLock()
private var generating = false
private var generateRegionsButton = NO_BLOCK
private var generateRegionsLabel = NO_BLOCK
private var generateRandomRegionsButton = NO_BLOCK
private var generateRandomRegionsLabel = NO_BLOCK
private var buildButton = NO_BLOCK
private var buildLabel = NO_BLOCK
private var backRegionsButton = NO_BLOCK
private var forwardRegionsButton = NO_BLOCK
private var backRegionsLabel = NO_BLOCK
private var forwardRegionsLabel = NO_BLOCK
private var showRegionsButton = NO_BLOCK
private var showRegionsLabel = NO_BLOCK
private var showBiomesButton = NO_BLOCK
private var showBiomesLabel = NO_BLOCK
private var showMapButton = NO_BLOCK
private var showMapLabel = NO_BLOCK
private var showMeshButton = NO_BLOCK
private var showMeshLabel = NO_BLOCK
private var generateBiomesButton = NO_BLOCK
private var generateBiomesLabel = NO_BLOCK
private var generateRandomBiomesButton = NO_BLOCK
private var generateRandomBiomesLabel = NO_BLOCK
private var backBiomesButton = NO_BLOCK
private var forwardBiomesButton = NO_BLOCK
private var backBiomesLabel = NO_BLOCK
private var forwardBiomesLabel = NO_BLOCK
private var editRegionsToggle = NO_BLOCK
private var drawSplinesToggle = NO_BLOCK
private var editSplinesToggle = NO_BLOCK
private var deleteSplinesToggle = NO_BLOCK
private var editBiomesToggle = NO_BLOCK

private val biomes = ref(emptyList<Int>())
private val selectedBiomes = Array(16) { ref(it % biomeValues.size) }.toList()

private enum class DisplayMode { REGIONS, MAP, BIOMES, MESH }

private var displayMode = DisplayMode.MAP
private var defaultToMap = true

fun disableGenerateButtons() {
    generateRegionsButton.isVisible = false
    generateRegionsLabel.isVisible = true
    generateRandomRegionsButton.isVisible = false
    generateRandomRegionsLabel.isVisible = true
    backRegionsButton.isVisible = false
    backRegionsLabel.isVisible = true
    forwardRegionsButton.isVisible = false
    forwardRegionsLabel.isVisible = true
    generateBiomesButton.isVisible = false
    generateBiomesLabel.isVisible = true
    generateRandomBiomesButton.isVisible = false
    generateRandomBiomesLabel.isVisible = true
    backBiomesButton.isVisible = false
    backBiomesLabel.isVisible = true
    forwardBiomesButton.isVisible = false
    forwardBiomesLabel.isVisible = true
    showRegionsButton.isVisible = false
    showRegionsLabel.isVisible = true
    showBiomesButton.isVisible = false
    showBiomesLabel.isVisible = true
    showMapButton.isVisible = false
    val displayBuildLabel = buildButton.isVisible || buildLabel.isVisible
    showMapLabel.isVisible = true
    showMeshButton.isVisible = false
    showMeshLabel.isVisible = !displayBuildLabel
    buildButton.isVisible = false
    buildLabel.isVisible = displayBuildLabel
    editRegionsToggle.isVisible = editRegionsMode.value
    drawSplinesToggle.isVisible = drawSplinesMode.value
    editSplinesToggle.isVisible = editSplinesMode.value
    deleteSplinesToggle.isVisible = deleteSplinesMode.value
    editBiomesToggle.isVisible = editBiomesMode.value
}

private fun enableGenerateButtons() {
    generateRegionsLabel.isVisible = false
    generateRegionsButton.isVisible = true
    generateRandomRegionsLabel.isVisible = false
    generateRandomRegionsButton.isVisible = true
    backRegionsButton.isVisible = historyRegionsBackQueue.size != 0
    backRegionsLabel.isVisible = historyRegionsBackQueue.size == 0
    forwardRegionsButton.isVisible = historyRegionsForwardQueue.size != 0
    forwardRegionsLabel.isVisible = historyRegionsForwardQueue.size == 0
    generateBiomesLabel.isVisible = false
    generateBiomesButton.isVisible = true
    generateRandomBiomesLabel.isVisible = false
    generateRandomBiomesButton.isVisible = true
    backBiomesButton.isVisible = historyBiomesBackQueue.size != 0
    backBiomesLabel.isVisible = historyBiomesBackQueue.size == 0
    forwardBiomesButton.isVisible = historyBiomesForwardQueue.size != 0
    forwardBiomesLabel.isVisible = historyBiomesForwardQueue.size == 0
    val hasRegions = currentState.regionGraph != null && currentState.regionMask != null && displayMode != DisplayMode.REGIONS
    showRegionsButton.isVisible = hasRegions
    showRegionsLabel.isVisible = !hasRegions
    val hasBiomes = currentState.biomeGraph != null && currentState.biomeMask != null && displayMode != DisplayMode.BIOMES
    showBiomesButton.isVisible = hasBiomes
    showBiomesLabel.isVisible = !hasBiomes
    val hasRegionSplines = currentState.regionSplines != null && displayMode != DisplayMode.MAP
    showMapButton.isVisible = hasRegionSplines
    showMapLabel.isVisible = !hasRegionSplines
    val hasMesh = currentState.heightMapTexture != null && currentState.riverMapTexture != null
    val hasDataForMeshBuilding = currentState.regionGraph != null && currentState.regionMask != null && currentState.regionSplines != null && currentState.biomeGraph != null && currentState.biomeGraph != null
    if (hasMesh) {
        if (displayMode != DisplayMode.MESH) {
            showMeshLabel.isVisible = false
            buildLabel.isVisible = false
            buildButton.isVisible = false
            showMeshButton.isVisible = true
        } else {
            showMeshButton.isVisible = false
            buildLabel.isVisible = false
            buildButton.isVisible = false
            showMeshLabel.isVisible = true
        }
    } else {
        if (hasDataForMeshBuilding) {
            showMeshLabel.isVisible = false
            showMeshButton.isVisible = false
            buildLabel.isVisible = false
            buildButton.isVisible = true
        } else {
            showMeshLabel.isVisible = false
            showMeshButton.isVisible = false
            buildButton.isVisible = false
            buildLabel.isVisible = true
        }
    }
    editRegionsToggle.isVisible = currentState.regionGraph != null && currentState.regionMask != null && displayMode == DisplayMode.REGIONS
    drawSplinesToggle.isVisible = currentState.regionSplines != null && displayMode == DisplayMode.MAP
    editSplinesToggle.isVisible = currentState.regionSplines != null && displayMode == DisplayMode.MAP
    deleteSplinesToggle.isVisible = currentState.regionSplines != null && displayMode == DisplayMode.MAP
    editBiomesToggle.isVisible = currentState.biomeGraph != null && currentState.biomeMask != null && displayMode == DisplayMode.BIOMES
}

private fun doGeneration(doWork: () -> Unit) {
    if (generateLock.tryLock()) {
        try {
            if (!generating) {
                generating = true
                try {
                    disableGenerateButtons()
                    try {
                        doWork()
                    } finally {
                        enableGenerateButtons()
                    }
                } finally {
                    generating = false
                }
            }
        } finally {
            generateLock.unlock()
        }
    }
}

private fun doGenerationStart() {
    if (generateLock.tryLock()) {
        try {
            if (!generating) {
                generating = true
                disableGenerateButtons()
            }
        } finally {
            generateLock.unlock()
        }
    }
}

private fun doGenerationStop() {
    if (generateLock.tryLock()) {
        try {
            if (generating) {
                enableGenerateButtons()
                generating = false
            }
        } finally {
            generateLock.unlock()
        }
    }
}

private fun buildRegionsFun(parameters: ParameterSet, refreshOnly: Boolean = false, rebuildSplines: Boolean = true) {
    val currentRegionGraph = currentState.regionGraph
    val currentRegionMask = currentState.regionMask
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
            val landPercent = land.toFloat() / (water + land)
            val graph = Graphs.generateGraph(128, parameters.regionsSeed, 0.8)
            Coastline.refineCoastline(graph, Random(parameters.regionsSeed), mask, BuildContinent.Parameters(graph.stride!!, landPercent, 0.0f, 1, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f))
            Pair(graph, mask)
        } else {
            generateRegions(parameters.copy(), executor)
        }
    }
    val currentSplines = currentState.regionSplines
    val regionSplines = if (rebuildSplines || currentSplines == null) {
        generateRegionSplines(Random(parameters.regionsSeed), regionGraph, regionMask, parameters.regionsMapScale)
    } else {
        currentSplines
    }
    currentState.parameters = parameters.copy()
    currentState.regionGraph = regionGraph
    currentState.regionMask = regionMask
    currentState.regionSplines = regionSplines
    currentState.meshScale = parameters.regionsMapScale
    currentState.heightMapTexture = null
    currentState.riverMapTexture = null
    if (displayMode == DisplayMode.MAP || (defaultToMap && displayMode != DisplayMode.REGIONS)) {
        val mapTextureId = TextureBuilder.renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints, regionSplines.mountainPoints, regionSplines.ignoredPoints)
        meshViewport.setImage(mapTextureId)
        imageMode.value = 1
        displayMode = DisplayMode.MAP
    } else {
        val regionTextureId = renderRegions(regionGraph, regionMask)
        meshViewport.setRegions(regionTextureId)
        imageMode.value = 0
        displayMode = DisplayMode.REGIONS
    }
}

private fun buildBiomesFun(parameters: ParameterSet, refreshOnly: Boolean = false) {
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
            val biomeScale = round(scale * 18) + 10
            val graph = Graphs.generateGraph(128, parameters.biomesSeed, 0.8)
            buildBiomeMaps(executor, parameters.biomesSeed, graph, parameters.biomes.size, biomeScale)
        }
    }
    currentState.biomeGraph = biomeGraph
    currentState.biomeMask = biomeMask
    currentState.biomes = parameters.biomes.map {
        when (it) {
            0 -> Biomes.MOUNTAINS_BIOME
            1 -> Biomes.COASTAL_MOUNTAINS_BIOME
            2 -> Biomes.FOOTHILLS_BIOME
            3 -> Biomes.ROLLING_HILLS_BIOME
            4 -> Biomes.PLATEAU_BIOME
            5 -> Biomes.PLAINS_BIOME
            else -> Biomes.MOUNTAINS_BIOME
        }
    }
    currentState.heightMapTexture = null
    currentState.riverMapTexture = null
    val biomeTextureId = renderRegions(biomeGraph, biomeMask)
    val currentSplines = currentState.regionSplines
    val splineTextureId = if (currentSplines != null) {
        TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints, currentSplines.mountainPoints)
    } else {
        TextureBuilder.renderSplines(emptyList(), emptyList(), emptyList())
    }
    meshViewport.setBiomes(biomeTextureId, splineTextureId)
    imageMode.value = 2
    displayMode = DisplayMode.BIOMES
}

private fun syncParameterValues(parameters: ParameterSet) {
    var randomSeed = parameters.regionsSeed
    var randomString = randomSeed.toString()
    if (randomString.length > 18) {
        regionsSeed.value = randomString.substring(0, 18).toLong()
    } else {
        regionsSeed.value = randomSeed
    }
    randomSeed = parameters.biomesSeed
    randomString = randomSeed.toString()
    if (randomString.length > 18) {
        biomesSeed.value = randomString.substring(0, 18).toLong()
    } else {
        biomesSeed.value = randomSeed
    }
    regionsMapScale.value = parameters.regionsMapScale
    biomesMapScale.value = parameters.biomesMapScale
    regions.value = parameters.regionCount
    stride.value = parameters.stride
    islands.value = parameters.islandDesire
    startPoints.value = parameters.regionPoints
    reduction.value = parameters.initialReduction
    connectedness.value = parameters.connectedness
    regionSize.value = parameters.regionSize
    iterations.value = parameters.maxRegionTries / 10
    iterations.value = parameters.maxIslandTries / 100
    biomes.value = parameters.biomes
    biomes.value.forEachIndexed { i, id ->
        selectedBiomes[i].value = id
    }
}

private fun Block.leftPanelWidgets(ui: UserInterface, uiLayout: UiLayout, dialogLayer: Block) {
    onUpdateParamsFun()
    regions.listener { _, _ -> onUpdateParamsFun() }
    islands.listener { _, _ -> onUpdateParamsFun() }
    block {
        hSizing = Sizing.GROW
        layout = Layout.HORIZONTAL
        vExpandPanel("Region parameters", expanded = true) {
            vLongInputRow(regionsSeed, LARGE_ROW_HEIGHT, text("Seed:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, uiLayout) {
                hSpacer(SMALL_SPACER_SIZE)
                button(text("Randomize"), NORMAL_TEXT_BUTTON_STYLE) {
                    val randomSeed = RANDOM.nextLong()
                    val randomString = randomSeed.toString()
                    if (randomString.length > 18) {
                        regionsSeed.value = randomString.substring(0, 18).toLong()
                    } else {
                        regionsSeed.value = randomSeed
                    }
                }
            }
            vFileRowWithToggle(regionFile, useRegionFile, LARGE_ROW_HEIGHT, text("Region file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSliderWithValueRow(regionsMapScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
            vSliderWithValueRow(regions, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Regions:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
            vSliderWithValueRow(islands, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Island strength:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..5), linearClampedScaleFunctionInverse(0..5))
            vSliderRow(iterations, LARGE_ROW_HEIGHT, text("Iterations:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(2..30), linearClampedScaleFunctionInverse(2..30))
            vToggleRow(allowUnsafe, LARGE_ROW_HEIGHT, text("Unsafe mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            val unsafeBlocks = ArrayList<Block>()
            unsafeBlocks.add(vSliderWithValueRow(stride, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Stride:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(5..10), linearClampedScaleFunctionInverse(5..10)).with { isMouseAware = allowUnsafe.value })
            unsafeBlocks.add(vSliderWithValueRow(startPoints, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Start points:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..6), linearClampedScaleFunctionInverse(1..6)).with { isMouseAware = allowUnsafe.value })
            unsafeBlocks.add(vSliderWithValueRow(reduction, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Reduction:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..40), linearClampedScaleFunctionInverse(0..40)).with { isMouseAware = allowUnsafe.value })
            unsafeBlocks.add(vSliderWithValueRow(connectedness, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Connection:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.004f, 0.2f), linearClampedScaleFunctionInverse(0.004f, 0.2f)).with { isMouseAware = allowUnsafe.value })
            unsafeBlocks.add(vSliderWithValueRow(regionSize, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Region size:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.005f, 0.05f), linearClampedScaleFunctionInverse(0.005f, 0.05f)).with { isMouseAware = allowUnsafe.value })
            allowUnsafe.listener { _, new ->
                if (!new) {
                    onUpdateParamsFun()
                }
                unsafeBlocks.forEach {
                    it.isMouseAware = new
                    it.isVisible = new
                }
            }
            allowUnsafe.value = false
            editRegionsToggle = vToggleRow(editRegionsMode, LARGE_ROW_HEIGHT, text("Edit mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            editRegionsToggle.isVisible = false
            val editRegionsBlocks = ArrayList<Block>()
            editRegionsBlocks.add(vSliderRow(regionEditBrushSize, LARGE_ROW_HEIGHT, text("Brush size:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.015625f, 0.15625f), linearClampedScaleFunctionInverse(0.015625f, 0.15625f)))
            editRegionsMode.listener { old, new ->
                editRegionsBlocks.forEach {
                    it.isMouseAware = new
                    it.isVisible = new
                }
                if (old != new) {
                    executor.call {
                        if (new) {
                            val currentGraph = currentState.regionGraph
                            val currentMask = currentState.regionMask
                            if (currentGraph != null && currentMask != null) {
                                doGenerationStart()
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setRegions(newTexture)
                                    }
                                }
                                currentEditBrushSize.value = regionEditBrushSize
                                brushListener.value = PickAndGoDrawBrushListener(currentGraph, currentMask, regionEditBrushSize, textureReference)
                                brushOn.value = true
                            }
                        } else {
                            brushListener.value = null
                            brushOn.value = false
                            val parameters = extractCurrentParameters()
                            buildRegionsFun(parameters, true)
                            updateRegionsHistory(parameters)
                            doGenerationStop()
                        }
                    }
                }
            }
            editRegionsMode.value = false







            drawSplinesToggle = vToggleRow(drawSplinesMode, LARGE_ROW_HEIGHT, text("Draw splines mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            drawSplinesToggle.isVisible = false
            drawSplinesMode.listener { old, new ->
                if (old != new) {
                    executor.call {
                        if (new) {
                            val currentSplines = currentState.regionSplines
                            if (currentSplines != null) {
                                doGenerationStart()
                                val splineMap = LinkedHashMap<Int, Quadruple<Int, Int, List<Point2F>, List<LineSegment2F>>>()
                                var index = 0
                                currentSplines.riverPoints.zip(currentSplines.riverEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 0, points, edges))
                                }
                                currentSplines.mountainPoints.zip(currentSplines.mountainEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 1, points, edges))
                                }
                                currentSplines.ignoredPoints.zip(currentSplines.ignoredEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 2, points, edges))
                                }
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setImage(newTexture)
                                    }
                                }
                                currentEditBrushSize.value = drawSplineBrushSize
                                brushListener.value = SplineDrawBrushListener(currentState, currentSplines, splineMap, textureReference)
                                brushOn.value = true
                            }
                        } else {
                            brushListener.value = null
                            brushOn.value = false
                            val parameters = extractCurrentParameters()
                            buildRegionsFun(parameters, true, false)
                            updateRegionsHistory(parameters)
                            doGenerationStop()
                        }
                    }
                }
            }
            drawSplinesMode.value = false







            editSplinesToggle = vToggleRow(editSplinesMode, LARGE_ROW_HEIGHT, text("Toggle splines mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            editSplinesToggle.isVisible = false
            editSplinesMode.listener { old, new ->
                if (old != new) {
                    executor.call {
                        if (new) {
                            val currentSplines = currentState.regionSplines
                            if (currentSplines != null) {
                                doGenerationStart()
                                val splineMap = LinkedHashMap<Int, Quadruple<Int, Int, List<Point2F>, List<LineSegment2F>>>()
                                var index = 0
                                currentSplines.riverPoints.zip(currentSplines.riverEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 0, points, edges))
                                }
                                currentSplines.mountainPoints.zip(currentSplines.mountainEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 1, points, edges))
                                }
                                currentSplines.ignoredPoints.zip(currentSplines.ignoredEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 2, points, edges))
                                }
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setImage(newTexture)
                                    }
                                }
                                val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third }), 4096)
                                val splineSelectorMatrix = ShortArrayMatrix(4096) { i ->
                                    var offset = i * 4
                                    val r = splineSelectors[offset++].toInt() and 0x000000FF
                                    val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                                    (r or g).toShort()
                                }
                                pointPicker.value = SplinePointPicker(currentState, currentSplines, splineMap, splineSelectorMatrix, textureReference)
                                pickerOn.value = true
                            }
                        } else {
                            pointPicker.value = null
                            pickerOn.value = false
                            val parameters = extractCurrentParameters()
                            buildRegionsFun(parameters, true, false)
                            updateRegionsHistory(parameters)
                            doGenerationStop()
                        }
                    }
                }
            }
            editSplinesMode.value = false
            deleteSplinesToggle = vToggleRow(deleteSplinesMode, LARGE_ROW_HEIGHT, text("Delete splines mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            deleteSplinesToggle.isVisible = false
            deleteSplinesMode.listener { old, new ->
                if (old != new) {
                    executor.call {
                        if (new) {
                            val currentSplines = currentState.regionSplines
                            if (currentSplines != null) {
                                doGenerationStart()
                                val splineMap = LinkedHashMap<Int, Quintuple<Int, Int, List<Point2F>, List<LineSegment2F>, Boolean>>()
                                var index = 0
                                currentSplines.riverPoints.zip(currentSplines.riverEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 0, points, edges, false))
                                }
                                currentSplines.mountainPoints.zip(currentSplines.mountainEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 1, points, edges, false))
                                }
                                currentSplines.ignoredPoints.zip(currentSplines.ignoredEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 2, points, edges, false))
                                }
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setImage(newTexture)
                                    }
                                }
                                val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third }), 4096)
                                val splineSelectorMatrix = ShortArrayMatrix(4096) { i ->
                                    var offset = i * 4
                                    val r = splineSelectors[offset++].toInt() and 0x000000FF
                                    val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                                    (r or g).toShort()
                                }
                                pointPicker.value = SplineDeletePicker(currentState, currentSplines, splineMap, splineSelectorMatrix, textureReference)
                                pickerOn.value = true
                            }
                        } else {
                            pointPicker.value = null
                            pickerOn.value = false
                            val parameters = extractCurrentParameters()
                            buildRegionsFun(parameters, true, false)
                            updateRegionsHistory(parameters)
                            doGenerationStop()
                        }
                    }
                }
            }
            deleteSplinesMode.value = false
            vButtonRow(LARGE_ROW_HEIGHT) {
                generateRegionsButton = button(text("Generate"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = extractCurrentParameters()
                        buildRegionsFun(parameters)
                        updateRegionsHistory(parameters)
                    }
                }
                generateRegionsLabel = button(text("Generate"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateRegionsLabel.isMouseAware = false
                generateRegionsLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                generateRandomRegionsButton = button(text("Generate random"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val randomSeed = RANDOM.nextLong()
                        val randomString = randomSeed.toString()
                        if (randomString.length > 18) {
                            regionsSeed.value = randomString.substring(0, 18).toLong()
                        } else {
                            regionsSeed.value = randomSeed
                        }
                        val parameters = extractCurrentParameters()
                        buildRegionsFun(parameters)
                        updateRegionsHistory(parameters)
                    }
                }
                generateRandomRegionsLabel = button(text("Generate random"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateRandomRegionsLabel.isMouseAware = false
                generateRandomRegionsLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                backRegionsButton = button(text("Back"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = historyRegionsBackQueue.pop()
                        if (parameters != null) {
                            val historyLast = historyRegionsCurrent.value
                            if (historyLast != null) {
                                historyRegionsForwardQueue.push(historyLast.copy())
                            }
                            syncParameterValues(parameters)
                            buildRegionsFun(parameters)
                            historyRegionsCurrent.value = parameters.copy()
                        }
                    }
                }
                backRegionsLabel = button(text("Back"), DISABLED_TEXT_BUTTON_STYLE) {}
                backRegionsLabel.isMouseAware = false
                hSpacer(SMALL_SPACER_SIZE)
                forwardRegionsButton = button(text("Forward"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = historyRegionsForwardQueue.pop()
                        if (parameters != null) {
                            val historyLast = historyRegionsCurrent.value
                            if (historyLast != null) {
                                historyRegionsBackQueue.push(historyLast.copy())
                            }
                            syncParameterValues(parameters)
                            buildRegionsFun(parameters)
                            historyRegionsCurrent.value = parameters.copy()
                        }
                    }
                }
                forwardRegionsLabel = button(text("Forward"), DISABLED_TEXT_BUTTON_STYLE) {}
                forwardRegionsLabel.isMouseAware = false
                backRegionsButton.isVisible = false
                forwardRegionsButton.isVisible = false
            }
            vSpacer(HALF_ROW_HEIGHT)
        }
        vExpandPanel("Biome parameters", expanded = true) {
            vLongInputRow(biomesSeed, LARGE_ROW_HEIGHT, text("Seed:"), TEXT_STYLE_NORMAL, COLOR_BUTTON_TEXT, shrinkGroup, MEDIUM_SPACER_SIZE, ui, uiLayout) {
                hSpacer(SMALL_SPACER_SIZE)
                button(text("Randomize"), NORMAL_TEXT_BUTTON_STYLE) {
                    val randomSeed = RANDOM.nextLong()
                    val randomString = randomSeed.toString()
                    if (randomString.length > 18) {
                        biomesSeed.value = randomString.substring(0, 18).toLong()
                    } else {
                        biomesSeed.value = randomSeed
                    }
                }
            }
            vFileRowWithToggle(biomeFile, useBiomeFile, LARGE_ROW_HEIGHT, text("Region file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSliderWithValueRow(biomesMapScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Map scale:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
            vSliderWithValueRow(biomeCount, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Biome count:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
            val biomeRows = block {
                vAlign = VerticalAlignment.TOP
                hAlign = HorizontalAlignment.LEFT
                layout = Layout.VERTICAL
                hSizing = Sizing.RELATIVE
                vSizing = Sizing.SHRINK
            }
            biomeCount.listener { oldBiomeCount, newBiomeCount ->
                if (oldBiomeCount != newBiomeCount) {
                    val newBiomes = ArrayList(biomes.value)
                    if (biomeRows.layoutChildren.size > newBiomeCount) {
                        for (i in 1..biomeRows.layoutChildren.size - newBiomeCount) {
                            val removeAt = biomeRows.layoutChildren.size - 1
                            biomeRows.layoutChildren.removeAt(removeAt)
                            biomeRows.renderChildren.removeAt(removeAt)
                            newBiomes.removeAt(removeAt)
                        }
                    } else if (biomeRows.layoutChildren.size < newBiomeCount) {
                        for (i in 1..newBiomeCount - biomeRows.layoutChildren.size) {
                            val index = newBiomes.size
                            val selectedValue = selectedBiomes[index]
                            biomeRows.vBiomeDropdownRow(editBiomesMode, currentBiomeBrushValue, dropdownLayer, REGION_COLORS[biomeRows.layoutChildren.size + 1], biomeValues.keys.toList(), selectedValue, index, LARGE_ROW_HEIGHT, shrinkGroup, MEDIUM_SPACER_SIZE)
                            newBiomes.add(selectedValue.value)
                            selectedValue.listener { oldBiomeId, newBiomeId ->
                                if (oldBiomeId != newBiomeId) {
                                    val changedBiomes = ArrayList(biomes.value)
                                    changedBiomes[index] = newBiomeId
                                    biomes.value = changedBiomes
                                }
                            }
                        }
                    }
                    biomes.value = newBiomes
                }
            }
            biomeCount.value = 6
            editBiomesToggle = vToggleRow(editBiomesMode, LARGE_ROW_HEIGHT, text("Edit mode:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            editBiomesToggle.isVisible = false
            val editBlocks = ArrayList<Block>()
            editBlocks.add(vSliderRow(biomeEditBrushSize, LARGE_ROW_HEIGHT, text("Brush size:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.015625f, 0.15625f), linearClampedScaleFunctionInverse(0.015625f, 0.15625f)))
            editBiomesMode.listener { old, new ->
                editBlocks.forEach {
                    it.isMouseAware = new
                    it.isVisible = new
                }
                if (old != new) {
                    executor.call {
                        if (new) {
                            val currentGraph = currentState.biomeGraph
                            val currentMask = currentState.biomeMask
                            if (currentGraph != null && currentMask != null) {
                                doGenerationStart()
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setBiomes(newTexture)
                                    }
                                }
                                currentEditBrushSize.value = biomeEditBrushSize
                                brushListener.value = PreSelectDrawBrushListener(currentGraph, currentMask, biomeEditBrushSize, textureReference, currentBiomeBrushValue)
                                brushOn.value = true
                            }
                        } else {
                            brushListener.value = null
                            brushOn.value = false
                            val parameters = extractCurrentParameters()
                            buildBiomesFun(parameters, true)
                            updateBiomesHistory(parameters)
                            doGenerationStop()
                        }
                    }
                }
            }
            editBiomesMode.value = false
            vButtonRow(LARGE_ROW_HEIGHT) {
                generateBiomesButton = button(text("Generate"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = extractCurrentParameters()
                        buildBiomesFun(parameters)
                        updateBiomesHistory(parameters)
                    }
                }
                generateBiomesLabel = button(text("Generate"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateBiomesLabel.isMouseAware = false
                generateBiomesLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                generateRandomBiomesButton = button(text("Generate random"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val randomSeed = RANDOM.nextLong()
                        val randomString = randomSeed.toString()
                        if (randomString.length > 18) {
                            biomesSeed.value = randomString.substring(0, 18).toLong()
                        } else {
                            biomesSeed.value = randomSeed
                        }
                        val parameters = extractCurrentParameters()
                        buildBiomesFun(parameters)
                        updateBiomesHistory(parameters)
                    }
                }
                generateRandomBiomesLabel = button(text("Generate random"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateRandomBiomesLabel.isMouseAware = false
                generateRandomBiomesLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                backBiomesButton = button(text("Back"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = historyBiomesBackQueue.pop()
                        if (parameters != null) {
                            val historyLast = historyBiomesCurrent.value
                            if (historyLast != null) {
                                historyBiomesForwardQueue.push(historyLast.copy())
                            }
                            syncParameterValues(parameters)
                            buildBiomesFun(parameters)
                            historyBiomesCurrent.value = parameters.copy()
                        }
                    }
                }
                backBiomesLabel = button(text("Back"), DISABLED_TEXT_BUTTON_STYLE) {}
                backBiomesLabel.isMouseAware = false
                hSpacer(SMALL_SPACER_SIZE)
                forwardBiomesButton = button(text("Forward"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = historyBiomesForwardQueue.pop()
                        if (parameters != null) {
                            val historyLast = historyBiomesCurrent.value
                            if (historyLast != null) {
                                historyBiomesBackQueue.push(historyLast.copy())
                            }
                            syncParameterValues(parameters)
                            buildBiomesFun(parameters)
                            historyBiomesCurrent.value = parameters.copy()
                        }
                    }
                }
                forwardBiomesLabel = button(text("Forward"), DISABLED_TEXT_BUTTON_STYLE) {}
                forwardBiomesLabel.isMouseAware = false
                backBiomesButton.isVisible = false
                forwardBiomesButton.isVisible = false
            }
            vSpacer(HALF_ROW_HEIGHT)
        }
        vButtonRow(LARGE_ROW_HEIGHT) {
            showRegionsButton = button(text("Show regions"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentRegionGraph = currentState.regionGraph
                    val currentRegionMask = currentState.regionMask
                    if (currentRegionGraph != null && currentRegionMask != null) {
                        val regionTextureId = renderRegions(currentRegionGraph, currentRegionMask)
                        meshViewport.setRegions(regionTextureId)
                        imageMode.value = 0
                        displayMode = DisplayMode.REGIONS
                        defaultToMap = false
                    }
                }
            }
            showRegionsLabel = button(text("Show regions"), DISABLED_TEXT_BUTTON_STYLE) {}
            showRegionsLabel.isMouseAware = false
            showRegionsLabel.isVisible = false
            showMapButton = button(text("Show map"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentRegionSplines = currentState.regionSplines
                    if (currentRegionSplines != null) {
                        val regionTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints, currentRegionSplines.mountainPoints, currentRegionSplines.ignoredPoints)
                        meshViewport.setImage(regionTextureId)
                        imageMode.value = 1
                        displayMode = DisplayMode.MAP
                        defaultToMap = true
                    }
                }
            }
            showMapLabel = button(text("Show map"), DISABLED_TEXT_BUTTON_STYLE) {}
            showMapLabel.isMouseAware = false
            showMapLabel.isVisible = false
            showBiomesButton = button(text("Show biomes"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentBiomeGraph = currentState.biomeGraph
                    val currentBiomeMask = currentState.biomeMask
                    val currentSplines = currentState.regionSplines
                    if (currentBiomeGraph != null && currentBiomeMask != null) {
                        val biomeTextureId = renderRegions(currentBiomeGraph, currentBiomeMask)
                        val splineTextureId = if (currentSplines != null) {
                            TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints, currentSplines.mountainPoints)
                        } else {
                            TextureBuilder.renderSplines(emptyList(), emptyList(), emptyList())
                        }
                        meshViewport.setBiomes(biomeTextureId, splineTextureId)
                        imageMode.value = 2
                        displayMode = DisplayMode.BIOMES
                    }
                }
            }
            showBiomesLabel = button(text("Show biomes"), DISABLED_TEXT_BUTTON_STYLE) {}
            showBiomesLabel.isMouseAware = false
            showBiomesLabel.isVisible = false
            buildButton = button(text("Build mesh"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentParameters = currentState.parameters
                    val currentRegionGraph = currentState.regionGraph
                    val currentRegionMask = currentState.regionMask
                    val currentRegionSplines = currentState.regionSplines
                    val currentBiomeGraph = currentState.biomeGraph
                    val currentBiomeMask = currentState.biomeMask
                    val currentBiomes = currentState.biomes
                    val currentMapScale = currentState.meshScale
                    if (currentParameters != null && currentRegionGraph != null && currentRegionMask != null && currentRegionSplines != null && currentBiomeGraph != null && currentBiomeMask != null && currentBiomes != null && currentMapScale != null) {
                        val (heightMapTexId, riverMapTexId) = generateWaterFlows(currentParameters, currentRegionSplines, currentBiomeGraph, currentBiomeMask, currentBiomes, cachedGraph256.value, cachedGraph512.value, cachedGraph1024.value, executor, currentMapScale)
                        meshViewport.setHeightmap(Pair(heightMapTexId, riverMapTexId), 4096)
                        currentState.heightMapTexture = heightMapTexId
                        currentState.riverMapTexture = riverMapTexId
                        val linearDistanceScaleInKilometers = (((currentMapScale * currentMapScale) / 400.0f) * 990000 + 10000) / 1000
                        heightMapScaleFactor.value = ((-Math.log10(linearDistanceScaleInKilometers - 9.0) - 1) * 28 + 122).toFloat()
                        imageMode.value = 3
                        displayMode = DisplayMode.MESH
                    }
                }
            }
            buildLabel = button(text("Build mesh"), DISABLED_TEXT_BUTTON_STYLE) {}
            buildLabel.isMouseAware = false
            buildLabel.isVisible = false
            showMeshButton = button(text("Show mesh"), NORMAL_TEXT_BUTTON_STYLE) {
                doGeneration {
                    val currentHeightMap = currentState.heightMapTexture
                    val currentRiverMap = currentState.riverMapTexture
                    if (currentHeightMap != null && currentRiverMap != null) {
                        meshViewport.setHeightmap(Pair(currentHeightMap, currentRiverMap), 4096)
                        imageMode.value = 3
                        displayMode = DisplayMode.MESH
                    }
                }
            }
            showMeshLabel = button(text("Show mesh"), DISABLED_TEXT_BUTTON_STYLE) {}
            showMeshLabel.isMouseAware = false
            showMeshLabel.isVisible = false
        }
        enableGenerateButtons()
    }
}

private fun updateRegionsHistory(parameters: ParameterSet) {
    val historyLast = historyRegionsCurrent.value
    if (historyLast != null) {
        if ((historyRegionsBackQueue.size == 0 || historyRegionsBackQueue.peek() != historyLast) && parameters != historyLast) {
            historyRegionsBackQueue.push(historyLast.copy())
        }
    }
    historyRegionsForwardQueue.clear()
    historyRegionsCurrent.value = parameters.copy()
}

private fun updateBiomesHistory(parameters: ParameterSet) {
    val historyLast = historyBiomesCurrent.value
    if (historyLast != null) {
        if ((historyBiomesBackQueue.size == 0 || historyBiomesBackQueue.peek() != historyLast) && parameters != historyLast) {
            historyBiomesBackQueue.push(historyLast.copy())
        }
    }
    historyBiomesForwardQueue.clear()
    historyBiomesCurrent.value = parameters.copy()
}

private fun extractCurrentParameters(): ParameterSet {
    val parameters = ParameterSet(
            regionsSeed = regionsSeed.value,
            biomesSeed = biomesSeed.value,
            regionsMapScale = regionsMapScale.value,
            biomesMapScale = biomesMapScale.value,
            regionCount = regions.value,
            stride = stride.value,
            islandDesire = islands.value,
            regionPoints = startPoints.value,
            initialReduction = reduction.value,
            connectedness = connectedness.value,
            regionSize = regionSize.value,
            maxRegionTries = iterations.value * 10,
            maxIslandTries = iterations.value * 100,
            biomes = biomes.value)
    return parameters
}

private fun loadRegionMaskFromImage(file: File): Matrix<Byte> {
    val colorMap = LinkedHashMap<Int, Int>(16)
    colorMap[0] = 0
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
            colorMap[value] = i
        }
    }
    return ByteArrayMatrix(128) { i ->
        (colorMap[bufferedImage.getRGB(round((((i % 128) + 0.5f) / 128.0f) * widthM1), round((((i / 128) + 0.5f) / 128.0f) * heightM1)) and 0X00FFFFFF]!! and 0x00FFFFFF).toByte()
    }
}

private fun loadBiomeMaskFromImage(file: File): Matrix<Byte> {
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
