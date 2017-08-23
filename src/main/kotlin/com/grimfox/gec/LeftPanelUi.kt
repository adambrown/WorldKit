package com.grimfox.gec

import com.grimfox.gec.model.ByteArrayMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.ShortArrayMatrix
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Polygon2F
import com.grimfox.gec.ui.JSON
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
import com.grimfox.gec.util.FileDialogs.saveFileDialog
import com.grimfox.gec.util.FileDialogs.selectFile
import com.grimfox.gec.util.Rendering.renderRegions
import org.lwjgl.glfw.GLFW
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.Math.round
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO
import kotlin.collections.LinkedHashMap

class SplinePointPicker(val currentState: CurrentState, val currentSplines: RegionSplines, val splineMap: LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>, val mask: Matrix<Short>, val texture: MutableReference<TextureId>): PointPicker {

    private val maskWidth = mask.width
    private val maskWidthM1 = maskWidth - 1

    override fun onMouseDown(x: Float, y: Float) {
        val selectedId = mask[round(x * maskWidthM1), round(y * maskWidthM1)].toInt()
        if (selectedId > 0) {
            val splineToToggle = splineMap[selectedId]
            if (splineToToggle != null) {
                splineMap.put(selectedId, Quadruple(splineToToggle.first, if (splineToToggle.second < 3) (splineToToggle.second + 1) % 3 else ((splineToToggle.second - 2) % 3) + 3, splineToToggle.third, splineToToggle.fourth))
                val riverOrigins = ArrayList<List<Point2F>>()
                val riverEdges = ArrayList<List<LineSegment2F>>()
                val riverPoints = ArrayList<List<Point2F>>()
                val mountainOrigins = ArrayList<List<Point2F>>()
                val mountainEdges = ArrayList<List<LineSegment2F>>()
                val mountainPoints = ArrayList<List<Point2F>>()
                val ignoredOrigins = ArrayList<List<Point2F>>()
                val ignoredEdges = ArrayList<List<LineSegment2F>>()
                val ignoredPoints = ArrayList<List<Point2F>>()
                val customRiverEdges = ArrayList<List<LineSegment2F>>()
                val customRiverPoints = ArrayList<List<Point2F>>()
                val customMountainEdges = ArrayList<List<LineSegment2F>>()
                val customMountainPoints = ArrayList<List<Point2F>>()
                val customIgnoredEdges = ArrayList<List<LineSegment2F>>()
                val customIgnoredPoints = ArrayList<List<Point2F>>()
                splineMap.forEach {
                    when (it.value.second) {
                        0 -> {
                            riverOrigins.add(it.value.third.first)
                            riverEdges.add(it.value.fourth)
                            riverPoints.add(it.value.third.second)
                        }
                        1 -> {
                            mountainOrigins.add(it.value.third.first)
                            mountainEdges.add(it.value.fourth)
                            mountainPoints.add(it.value.third.second)
                        }
                        2 -> {
                            ignoredOrigins.add(it.value.third.first)
                            ignoredEdges.add(it.value.fourth)
                            ignoredPoints.add(it.value.third.second)
                        }
                        3 -> {
                            customRiverEdges.add(it.value.fourth)
                            customRiverPoints.add(it.value.third.second)
                        }
                        4 -> {
                            customMountainEdges.add(it.value.fourth)
                            customMountainPoints.add(it.value.third.second)
                        }
                        else -> {
                            customIgnoredEdges.add(it.value.fourth)
                            customIgnoredPoints.add(it.value.third.second)
                        }
                    }
                }
                currentState.regionSplines = RegionSplines(
                        currentSplines.coastEdges,
                        currentSplines.coastPoints,
                        riverOrigins,
                        riverEdges,
                        riverPoints,
                        mountainOrigins,
                        mountainEdges,
                        mountainPoints,
                        ignoredOrigins,
                        ignoredEdges,
                        ignoredPoints,
                        currentState.regionSplines?.deletedOrigins ?: listOf(),
                        currentState.regionSplines?.deletedEdges ?: listOf(),
                        currentState.regionSplines?.deletedPoints ?: listOf(),
                        customRiverEdges,
                        customRiverPoints,
                        customMountainEdges,
                        customMountainPoints,
                        customIgnoredEdges,
                        customIgnoredPoints)
                executor.call {
                    if (texture.value.id < 0) {
                        texture.value = renderMapImage(currentSplines.coastPoints, riverPoints + customRiverPoints, mountainPoints + customMountainPoints, ignoredPoints + customIgnoredPoints)
                    } else {
                        renderMapImage(currentSplines.coastPoints, riverPoints + customRiverPoints, mountainPoints + customMountainPoints, ignoredPoints + customIgnoredPoints, listOf(), texture.value)
                    }
                }
            }
        }
    }
}

class SplineDeletePicker(val currentState: CurrentState, val currentSplines: RegionSplines, val splineMap: LinkedHashMap<Int, Quintuple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>, Boolean>>, val mask: Matrix<Short>, val texture: MutableReference<TextureId>): PointPicker {

    private val maskWidth = mask.width
    private val maskWidthM1 = maskWidth - 1

    override fun onMouseDown(x: Float, y: Float) {
        val selectedId = mask[round(x * maskWidthM1), round(y * maskWidthM1)].toInt()
        if (selectedId > 0) {
            val splineToToggle = splineMap[selectedId]
            if (splineToToggle != null) {
                val riverOrigins = ArrayList<List<Point2F>>()
                val riverEdges = ArrayList<List<LineSegment2F>>()
                val riverPoints = ArrayList<List<Point2F>>()
                val mountainOrigins = ArrayList<List<Point2F>>()
                val mountainEdges = ArrayList<List<LineSegment2F>>()
                val mountainPoints = ArrayList<List<Point2F>>()
                val ignoredOrigins = ArrayList<List<Point2F>>()
                val ignoredEdges = ArrayList<List<LineSegment2F>>()
                val ignoredPoints = ArrayList<List<Point2F>>()
                val deletedOrigins = ArrayList<List<Point2F>>()
                val deletedEdges = ArrayList<List<LineSegment2F>>()
                val deletedPoints = ArrayList<List<Point2F>>()
                val customRiverEdges = ArrayList<List<LineSegment2F>>()
                val customRiverPoints = ArrayList<List<Point2F>>()
                val customMountainEdges = ArrayList<List<LineSegment2F>>()
                val customMountainPoints = ArrayList<List<Point2F>>()
                val customIgnoredEdges = ArrayList<List<LineSegment2F>>()
                val customIgnoredPoints = ArrayList<List<Point2F>>()
                val pendingEdges = ArrayList<List<LineSegment2F>>()
                val pendingPoints = ArrayList<List<Point2F>>()
                splineMap.put(selectedId, Quintuple(splineToToggle.first, splineToToggle.second, splineToToggle.third, splineToToggle.fourth, !splineToToggle.fifth))
                splineMap.forEach {
                    if (it.value.fifth) {
                        pendingEdges.add(it.value.fourth)
                        pendingPoints.add(it.value.third.second)
                        when (it.value.second) {
                            0, 1, 2 -> {
                                deletedOrigins.add(it.value.third.first)
                                deletedEdges.add(it.value.fourth)
                                deletedPoints.add(it.value.third.second)
                            }
                        }
                    } else {
                        when (it.value.second) {
                            0 -> {
                                riverOrigins.add(it.value.third.first)
                                riverEdges.add(it.value.fourth)
                                riverPoints.add(it.value.third.second)
                            }
                            1 -> {
                                mountainOrigins.add(it.value.third.first)
                                mountainEdges.add(it.value.fourth)
                                mountainPoints.add(it.value.third.second)
                            }
                            2 -> {
                                ignoredOrigins.add(it.value.third.first)
                                ignoredEdges.add(it.value.fourth)
                                ignoredPoints.add(it.value.third.second)
                            }
                            3 -> {
                                customRiverEdges.add(it.value.fourth)
                                customRiverPoints.add(it.value.third.second)
                            }
                            4 -> {
                                customMountainEdges.add(it.value.fourth)
                                customMountainPoints.add(it.value.third.second)
                            }
                            else -> {
                                customIgnoredEdges.add(it.value.fourth)
                                customIgnoredPoints.add(it.value.third.second)
                            }
                        }
                    }
                }
                currentState.regionSplines = RegionSplines(
                        currentSplines.coastEdges,
                        currentSplines.coastPoints,
                        riverOrigins,
                        riverEdges,
                        riverPoints,
                        mountainOrigins,
                        mountainEdges,
                        mountainPoints,
                        ignoredOrigins,
                        ignoredEdges,
                        ignoredPoints,
                        deletedOrigins,
                        deletedEdges,
                        deletedPoints,
                        customRiverEdges,
                        customRiverPoints,
                        customMountainEdges,
                        customMountainPoints,
                        customIgnoredEdges,
                        customIgnoredPoints)
                executor.call {
                    if (texture.value.id < 0) {
                        texture.value = renderMapImage(currentSplines.coastPoints, riverPoints + customRiverPoints, mountainPoints + customMountainPoints, ignoredPoints + customIgnoredPoints, pendingPoints)
                    } else {
                        renderMapImage(currentSplines.coastPoints, riverPoints + customRiverPoints, mountainPoints + customMountainPoints, ignoredPoints + customIgnoredPoints, pendingPoints, texture.value)
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

class SplineDrawBrushListener(val splineSmoothing: Reference<Int>, val currentState: CurrentState, val currentSplines: RegionSplines, val splineMap: LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>, val texture: MutableReference<TextureId>): BrushListener {

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
            val customRiverEdges = ArrayList<List<LineSegment2F>>()
            val customRiverPoints = ArrayList<List<Point2F>>()
            val customMountainEdges = ArrayList<List<LineSegment2F>>()
            val customMountainPoints = ArrayList<List<Point2F>>()
            val customIgnoredEdges = ArrayList<List<LineSegment2F>>()
            val customIgnoredPoints = ArrayList<List<Point2F>>()
            splineMap.forEach {
                when (it.value.second) {
                    0 -> {
                        riverEdges.add(it.value.fourth)
                        riverPoints.add(it.value.third.second)
                    }
                    1 -> {
                        mountainEdges.add(it.value.fourth)
                        mountainPoints.add(it.value.third.second)
                    }
                    2 -> {
                        ignoredEdges.add(it.value.fourth)
                        ignoredPoints.add(it.value.third.second)
                    }
                    3 -> {
                        customRiverEdges.add(it.value.fourth)
                        customRiverPoints.add(it.value.third.second)
                    }
                    4 -> {
                        customMountainEdges.add(it.value.fourth)
                        customMountainPoints.add(it.value.third.second)
                    }
                    else -> {
                        customIgnoredEdges.add(it.value.fourth)
                        customIgnoredPoints.add(it.value.third.second)
                    }
                }
            }
            val smoothing = round(((splineSmoothing.value / 20.0f).coerceIn(0.0f, 1.0f) * 11) + 13)
            val pendingPoints = if (currentSpline.size == 1) {
                listOf(buildOpenEdges(Polygon2F(currentSpline + currentSpline, false), smoothing))
            } else {
                listOf(buildOpenEdges(Polygon2F(currentSpline, false), smoothing))
            }
            executor.call {
                if (texture.value.id < 0) {
                    texture.value = renderMapImage(currentSplines.coastPoints, riverPoints + customRiverPoints, mountainPoints + customMountainPoints, ignoredPoints + customIgnoredPoints, pendingPoints)
                } else {
                    renderMapImage(currentSplines.coastPoints, riverPoints + customRiverPoints, mountainPoints + customMountainPoints, ignoredPoints + customIgnoredPoints, pendingPoints, texture.value)
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
            val riverOrigins = ArrayList<List<Point2F>>()
            val riverEdges = ArrayList<List<LineSegment2F>>()
            val riverPoints = ArrayList<List<Point2F>>()
            val mountainOrigins = ArrayList<List<Point2F>>()
            val mountainEdges = ArrayList<List<LineSegment2F>>()
            val mountainPoints = ArrayList<List<Point2F>>()
            val ignoredOrigins = ArrayList<List<Point2F>>()
            val ignoredEdges = ArrayList<List<LineSegment2F>>()
            val ignoredPoints = ArrayList<List<Point2F>>()
            val customRiverEdges = ArrayList<List<LineSegment2F>>()
            val customRiverPoints = ArrayList<List<Point2F>>()
            val customMountainEdges = ArrayList<List<LineSegment2F>>()
            val customMountainPoints = ArrayList<List<Point2F>>()
            val customIgnoredEdges = ArrayList<List<LineSegment2F>>()
            val customIgnoredPoints = ArrayList<List<Point2F>>()
            splineMap.forEach {
                when (it.value.second) {
                    0 -> {
                        riverOrigins.add(it.value.third.first)
                        riverEdges.add(it.value.fourth)
                        riverPoints.add(it.value.third.second)
                    }
                    1 -> {
                        mountainOrigins.add(it.value.third.first)
                        mountainEdges.add(it.value.fourth)
                        mountainPoints.add(it.value.third.second)
                    }
                    2 -> {
                        ignoredOrigins.add(it.value.third.first)
                        ignoredEdges.add(it.value.fourth)
                        ignoredPoints.add(it.value.third.second)
                    }
                    3 -> {
                        customRiverEdges.add(it.value.fourth)
                        customRiverPoints.add(it.value.third.second)
                    }
                    4 -> {
                        customMountainEdges.add(it.value.fourth)
                        customMountainPoints.add(it.value.third.second)
                    }
                    else -> {
                        customIgnoredEdges.add(it.value.fourth)
                        customIgnoredPoints.add(it.value.third.second)
                    }
                }
            }
            val smoothing = round(((splineSmoothing.value / 20.0f).coerceIn(0.0f, 1.0f) * 11) + 13)
            val newPoints = if (currentSpline.size == 1) {
                buildOpenEdges(Polygon2F(currentSpline + currentSpline, false), smoothing)
            } else {
                buildOpenEdges(Polygon2F(currentSpline, false), smoothing)
            }
            val newEdges = (1..newPoints.size - 1).mapTo(ArrayList()) { LineSegment2F(newPoints[it - 1], newPoints[it]) }
            customIgnoredPoints.add(newPoints)
            customIgnoredEdges.add(newEdges)
            splineMap.put(splineMap.size + 1, Quadruple(splineMap.size + 1, 5, newPoints to newPoints, newEdges))
            currentState.regionSplines = RegionSplines(
                    currentSplines.coastEdges,
                    currentSplines.coastPoints,
                    riverOrigins,
                    riverEdges,
                    riverPoints,
                    mountainOrigins,
                    mountainEdges,
                    mountainPoints,
                    ignoredOrigins,
                    ignoredEdges,
                    ignoredPoints,
                    currentState.regionSplines?.deletedOrigins ?: listOf(),
                    currentState.regionSplines?.deletedEdges ?: listOf(),
                    currentState.regionSplines?.deletedPoints ?: listOf(),
                    customRiverEdges,
                    customRiverPoints,
                    customMountainEdges,
                    customMountainPoints,
                    customIgnoredEdges,
                    customIgnoredPoints)
            this.currentSpline = null
            executor.call {
                if (texture.value.id < 0) {
                    texture.value = renderMapImage(currentSplines.coastPoints, riverPoints + customRiverPoints, mountainPoints + customMountainPoints, ignoredPoints + customIgnoredPoints)
                } else {
                    renderMapImage(currentSplines.coastPoints, riverPoints + customRiverPoints, mountainPoints + customMountainPoints, ignoredPoints + customIgnoredPoints, listOf(), texture.value)
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

private val editToggleCurrentPointer = ref<MutableReference<Boolean>?>(null)
private val editToggleLatch = ref<CountDownLatch?>(null)

private val shrinkGroup = hShrinkGroup()
private val regionFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val biomeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
private val useRegionFile = ref(false)
private val useBiomeFile = ref(false)
private val allowUnsafe = ref(false)
private val editRegionsMode = ref(false)
private val drawSplinesMode = ref(false)
private val splineSmoothing = ref(10)
private val editSplinesMode = ref(false)
private val editSplinesSelectionRadius = ref(80)
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
private var importRegionsButton = NO_BLOCK
private var importRegionsLabel = NO_BLOCK
private var exportRegionsButton = NO_BLOCK
private var exportRegionsLabel = NO_BLOCK
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
    importRegionsButton.isVisible = false
    importRegionsLabel.isVisible = true
    exportRegionsButton.isVisible = false
    exportRegionsLabel.isVisible = true
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
}

private fun enableGenerateButtons() {
    importRegionsLabel.isVisible = false
    importRegionsButton.isVisible = true
    exportRegionsLabel.isVisible = false
    exportRegionsButton.isVisible = true
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
            val graph = Graphs.generateGraph(128, parameters.regionsSeed, 0.8)
            Pair(graph, mask)
        } else {
            generateRegions(parameters.copy(), executor)
        }
    }
    val currentSplines = currentState.regionSplines
    val regionSplines = if (rebuildSplines || currentSplines == null) {
        var newSplines = generateRegionSplines(Random(parameters.regionsSeed), regionGraph, regionMask, parameters.regionsMapScale)
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
            newSplines = RegionSplines(
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
    currentState.parameters = parameters.copy()
    currentState.regionGraph = regionGraph
    currentState.regionMask = regionMask
    currentState.regionSplines = regionSplines
    currentState.meshScale = parameters.regionsMapScale
    currentState.heightMapTexture = null
    currentState.riverMapTexture = null
    if (displayMode == DisplayMode.MAP || (defaultToMap && displayMode != DisplayMode.REGIONS)) {
        val mapTextureId = TextureBuilder.renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints + regionSplines.customRiverPoints, regionSplines.mountainPoints + regionSplines.customMountainPoints, regionSplines.ignoredPoints + regionSplines.customIgnoredPoints)
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
        TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
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

private fun openRegionsFile(dialogLayer: Block, preferences: Preferences, ui: UserInterface): RegionHistoryItem? {
    return selectFile(dialogLayer, true, ui, preferences.projectDir, "wkr") { file ->
        if (file == null) {
            null
        } else {
            val historyItem = DataInputStream(file.inputStream().buffered()).use { stream ->
                val parameters = JSON.readValue(stream.readUTF(), ParameterSet::class.java)
                val graphSeed = stream.readLong()
                val maskWidth = stream.readInt()
                val maskBytes = ByteArray(maskWidth * maskWidth)
                stream.readFully(maskBytes)
                val regionMask = ByteArrayMatrix(maskWidth, maskBytes)
                RegionHistoryItem(parameters, graphSeed, regionMask)
            }
            historyItem
        }
    }
}

private fun exportRegionsFile(regions: RegionHistoryItem?, dialogLayer: Block, preferences: Preferences, ui: UserInterface): Boolean {
    if (regions != null) {
        ui.ignoreInput = true
        dialogLayer.isVisible = true
        try {
            val saveFile = saveFileDialog(preferences.projectDir, "wkr")
            if (saveFile != null) {
                val fullNameWithExtension = "${saveFile.name.removeSuffix(".wkr")}.wkr"
                val actualFile = File(saveFile.parentFile, fullNameWithExtension)
                DataOutputStream(actualFile.outputStream().buffered()).use { stream ->
                    val parameters = JSON.writeValueAsString(regions.parameters)
                    stream.writeUTF(parameters)
                    stream.writeLong(regions.graphSeed)
                    stream.writeInt(regions.regionMask.width)
                    stream.write(regions.regionMask.array)
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

private fun Block.leftPanelWidgets(ui: UserInterface, uiLayout: UiLayout, dialogLayer: Block) {
    onUpdateParamsFun()
    regions.listener { _, _ -> onUpdateParamsFun() }
    islands.listener { _, _ -> onUpdateParamsFun() }
    block {
        hSizing = Sizing.GROW
        layout = Layout.HORIZONTAL
        val regionPanel = vExpandPanel("Edit regions", expanded = true) {
            vSpacer(HALF_ROW_HEIGHT)
            vButtonRow(LARGE_ROW_HEIGHT) {
                importRegionsButton = button(text("Import regions"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val historyItem = openRegionsFile(dialogLayer, preferences, ui)
                        if (historyItem != null) {
                            val historyLast = historyRegionsCurrent.value
                            if (historyLast != null) {
                                historyRegionsBackQueue.push(historyLast.copy())
                            }
                            syncParameterValues(historyItem.parameters)
                            currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                            currentState.regionMask = historyItem.regionMask
                            buildRegionsFun(historyItem.parameters, true, true)
                            historyRegionsCurrent.value = historyItem.copy()
                        }
                    }
                }
                importRegionsLabel = button(text("Import regions"), DISABLED_TEXT_BUTTON_STYLE) {}
                importRegionsLabel.isMouseAware = false
                importRegionsLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                exportRegionsButton = button(text("Export regions"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        exportRegionsFile(historyRegionsCurrent.value, dialogLayer, preferences, ui)
                    }
                }
                exportRegionsLabel = button(text("Import regions"), DISABLED_TEXT_BUTTON_STYLE) {}
                exportRegionsLabel.isMouseAware = false
                exportRegionsLabel.isVisible = false
            }
            vSpacer(HALF_ROW_HEIGHT)
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
            val regionScaleSlider = vSliderWithValueRow(regionsMapScale, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Edge detail:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20))
            var regionScaleSliderInitial = regionsMapScale.value
            val regionScaleSliderMouseDown = regionScaleSlider.onMouseDown
            regionScaleSlider.onMouseDown { a, b, c, d ->
                regionScaleSliderMouseDown?.invoke(regionScaleSlider, a, b, c, d)
                regionScaleSliderInitial = regionsMapScale.value
            }
            val regionScaleSliderMouseRelease = regionScaleSlider.onMouseRelease
            regionScaleSlider.onMouseRelease { a, b, c, d ->
                regionScaleSliderMouseRelease?.invoke(regionScaleSlider, a, b, c, d)
                executor.call {
                    doGeneration {
                        val currentGraph = currentState.regionGraph
                        val currentMask = currentState.regionMask
                        val currentSplines = currentState.regionSplines
                        if (regionsMapScale.value != regionScaleSliderInitial && currentGraph != null && currentMask != null && currentSplines != null) {
                            val parameters = extractCurrentParameters()
                            buildRegionsFun(parameters, true, true)
                            updateRegionsHistory(parameters, currentGraph, currentMask)
                            val currentRegionSplines = currentState.regionSplines
                            if (currentRegionSplines != null && displayMode == DisplayMode.MAP) {
                                val regionTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
                                meshViewport.setImage(regionTextureId)
                            }
                        }
                    }
                }
            }
            vSliderWithValueRow(regions, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Number of regions:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(1..16), linearClampedScaleFunctionInverse(1..16))
            vSliderWithValueRow(islands, 5, TEXT_STYLE_NORMAL, LARGE_ROW_HEIGHT, text("Prefer islands:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..5), linearClampedScaleFunctionInverse(0..5))
            debugWidgets.add(vSliderRow(iterations, LARGE_ROW_HEIGHT, text("Iterations:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(2..30), linearClampedScaleFunctionInverse(2..30)))
            debugWidgets.add(vToggleRow(allowUnsafe, LARGE_ROW_HEIGHT, text("Unsafe mode:"), shrinkGroup, MEDIUM_SPACER_SIZE))
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
            editRegionsToggle = vToggleRow(editRegionsMode, LARGE_ROW_HEIGHT, text("Manual edit:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            val editRegionsBlocks = ArrayList<Block>()
            editRegionsBlocks.add(vSliderRow(regionEditBrushSize, LARGE_ROW_HEIGHT, text("Brush size:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0.015625f, 0.15625f), linearClampedScaleFunctionInverse(0.015625f, 0.15625f)))
            var regionEditActivated = false
            editRegionsMode.listener { old, new ->
                editRegionsBlocks.forEach {
                    it.isMouseAware = new
                    it.isVisible = new
                }
                if (old != new) {
                    executor.call {
                        if (new) {
                            disableCurrentToggleIfEnabled()
                            val currentGraph = currentState.regionGraph
                            val currentMask = currentState.regionMask
                            if (currentGraph != null && currentMask != null) {
                                doGenerationStart()
                                editToggleCurrentPointer.value = editRegionsMode
                                regionEditActivated = true
                                val regionTextureId = renderRegions(currentGraph, currentMask)
                                meshViewport.setRegions(regionTextureId)
                                imageMode.value = 0
                                displayMode = DisplayMode.REGIONS
                                defaultToMap = false
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setRegions(newTexture)
                                    }
                                }
                                currentEditBrushSize.value = regionEditBrushSize
                                brushListener.value = PickAndGoDrawBrushListener(currentGraph, currentMask, regionEditBrushSize, textureReference)
                                brushOn.value = true
                            } else {
                                editRegionsMode.value = false
                            }
                        } else {
                            if (regionEditActivated) {
                                brushListener.value = null
                                brushOn.value = false
                                val parameters = extractCurrentParameters()
                                buildRegionsFun(parameters, true)
                                val currentRegionSplines = currentState.regionSplines
                                val currentGraph = currentState.regionGraph
                                val currentMask = currentState.regionMask
                                if (currentRegionSplines != null && currentGraph != null && currentMask != null) {
                                    updateRegionsHistory(parameters, currentGraph, currentMask)
                                    val mapTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
                                    meshViewport.setImage(mapTextureId)
                                    imageMode.value = 1
                                    displayMode = DisplayMode.MAP
                                    defaultToMap = true
                                }
                                regionEditActivated = false
                                if (editToggleCurrentPointer.value == editRegionsMode) {
                                    editToggleCurrentPointer.value = null
                                }
                                doGenerationStop()
                            }
                            editToggleLatch.value?.countDown()
                        }
                    }
                }
            }
            editRegionsMode.value = false
            vSpacer(HALF_ROW_HEIGHT)
            vButtonRow(LARGE_ROW_HEIGHT) {
                generateRegionsButton = button(text("Generate"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val parameters = extractCurrentParameters()
                        buildRegionsFun(parameters)
                        val currentGraph = currentState.regionGraph
                        val currentMask = currentState.regionMask
                        if (currentGraph != null && currentMask != null) {
                            updateRegionsHistory(parameters, currentGraph, currentMask)
                        }
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
                        val currentGraph = currentState.regionGraph
                        val currentMask = currentState.regionMask
                        if (currentGraph != null && currentMask != null) {
                            updateRegionsHistory(parameters, currentGraph, currentMask)
                        }
                    }
                }
                generateRandomRegionsLabel = button(text("Generate random"), DISABLED_TEXT_BUTTON_STYLE) {}
                generateRandomRegionsLabel.isMouseAware = false
                generateRandomRegionsLabel.isVisible = false
                hSpacer(SMALL_SPACER_SIZE)
                backRegionsButton = button(text("Back"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val historyItem = historyRegionsBackQueue.pop()
                        if (historyItem != null) {
                            val historyLast = historyRegionsCurrent.value
                            if (historyLast != null) {
                                historyRegionsForwardQueue.push(historyLast.copy())
                            }
                            syncParameterValues(historyItem.parameters)
                            currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                            currentState.regionMask = historyItem.regionMask
                            buildRegionsFun(historyItem.parameters, true, true)
                            historyRegionsCurrent.value = historyItem.copy()
                        }
                    }
                }
                backRegionsLabel = button(text("Back"), DISABLED_TEXT_BUTTON_STYLE) {}
                backRegionsLabel.isMouseAware = false
                hSpacer(SMALL_SPACER_SIZE)
                forwardRegionsButton = button(text("Forward"), NORMAL_TEXT_BUTTON_STYLE) {
                    doGeneration {
                        val historyItem = historyRegionsForwardQueue.pop()
                        if (historyItem != null) {
                            val historyLast = historyRegionsCurrent.value
                            if (historyLast != null) {
                                historyRegionsBackQueue.push(historyLast.copy())
                            }
                            syncParameterValues(historyItem.parameters)
                            currentState.regionGraph = Graphs.generateGraph(128, historyItem.graphSeed, 0.8)
                            currentState.regionMask = historyItem.regionMask
                            buildRegionsFun(historyItem.parameters, true, true)
                            historyRegionsCurrent.value = historyItem.copy()
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
        regionPanel.isVisible = false
        val mapPanel = vExpandPanel("Edit map", expanded = true) {
            drawSplinesToggle = vToggleRow(drawSplinesMode, LARGE_ROW_HEIGHT, text("Draw splines:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            val drawSplinesBlocks = ArrayList<Block>()
            drawSplinesBlocks.add(vSliderRow(splineSmoothing, LARGE_ROW_HEIGHT, text("Smoothing:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(0..20), linearClampedScaleFunctionInverse(0..20)))
            var drawSplinesActivated = false
            drawSplinesMode.listener { old, new ->
                drawSplinesBlocks.forEach {
                    it.isMouseAware = new
                    it.isVisible = new
                }
                if (old != new) {
                    executor.call {
                        if (new) {
                            disableCurrentToggleIfEnabled()
                            val currentSplines = currentState.regionSplines
                            if (currentSplines != null) {
                                doGenerationStart()
                                editToggleCurrentPointer.value = drawSplinesMode
                                drawSplinesActivated = true
                                val splineMap = LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>()
                                var index = 0
                                currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 0, pair, edges))
                                }
                                currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 1, pair, edges))
                                }
                                currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 2, pair, edges))
                                }
                                currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 3, points to points, edges))
                                }
                                currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 4, points to points, edges))
                                }
                                currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 5, points to points, edges))
                                }
                                if (displayMode != DisplayMode.MAP) {
                                    val mapTextureId = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints)
                                    meshViewport.setImage(mapTextureId)
                                    imageMode.value = 1
                                    displayMode = DisplayMode.MAP
                                    defaultToMap = true
                                }
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setImage(newTexture)
                                    }
                                }
                                currentEditBrushSize.value = drawSplineBrushSize
                                brushListener.value = SplineDrawBrushListener(splineSmoothing, currentState, currentSplines, splineMap, textureReference)
                                brushOn.value = true
                            } else {
                                drawSplinesMode.value = false
                            }
                        } else {
                            if (drawSplinesActivated) {
                                brushListener.value = null
                                brushOn.value = false
                                val parameters = extractCurrentParameters()
                                buildRegionsFun(parameters, true, false)
                                val currentGraph = currentState.regionGraph
                                val currentMask = currentState.regionMask
                                if (currentGraph != null && currentMask != null) {
                                    updateRegionsHistory(parameters, currentGraph, currentMask)
                                }
                                drawSplinesActivated = false
                                if (editToggleCurrentPointer.value == drawSplinesMode) {
                                    editToggleCurrentPointer.value = null
                                }
                                doGenerationStop()
                            }
                            editToggleLatch.value?.countDown()
                        }
                    }
                }
            }
            drawSplinesMode.value = false
            editSplinesToggle = vToggleRow(editSplinesMode, LARGE_ROW_HEIGHT, text("Toggle splines:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            val editSplinesBlocks = ArrayList<Block>()
            val splineSelectionRadiusSlider = vSliderRow(editSplinesSelectionRadius, LARGE_ROW_HEIGHT, text("Selection radius:"), shrinkGroup, MEDIUM_SPACER_SIZE, linearClampedScaleFunction(10..500), linearClampedScaleFunctionInverse(10..500))
            editSplinesBlocks.add(splineSelectionRadiusSlider)
            var splineSelectionRadiusSliderInitial = editSplinesSelectionRadius.value
            val splineSelectionRadiusSliderMouseDown = splineSelectionRadiusSlider.onMouseDown
            splineSelectionRadiusSlider.onMouseDown { a, b, c, d ->
                splineSelectionRadiusSliderMouseDown?.invoke(splineSelectionRadiusSlider, a, b, c, d)
                splineSelectionRadiusSliderInitial = editSplinesSelectionRadius.value
            }
            var splineSelectorMap: LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>? = null
            var splineSelectorMatrix: ShortArrayMatrix? = null
            val splineSelectionRadiusSliderMouseRelease = splineSelectionRadiusSlider.onMouseRelease
            splineSelectionRadiusSlider.onMouseRelease { a, b, c, d ->
                splineSelectionRadiusSliderMouseRelease?.invoke(splineSelectionRadiusSlider, a, b, c, d)
                executor.call {
                    val splineMap = splineSelectorMap
                    val selectorMatrix = splineSelectorMatrix
                    if (editSplinesSelectionRadius.value != splineSelectionRadiusSliderInitial && selectorMatrix != null && splineMap != null) {
                        val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, editSplinesSelectionRadius.value.toFloat()), 4096)
                        for (i in 0..16777215) {
                            var offset = i * 4
                            val r = splineSelectors[offset++].toInt() and 0x000000FF
                            val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                            selectorMatrix[i] = (r or g).toShort()
                        }
                    }
                }
            }
            var editSplinesActivated = false
            editSplinesMode.listener { old, new ->
                editSplinesBlocks.forEach {
                    it.isMouseAware = new
                    it.isVisible = new
                }
                if (old != new) {
                    executor.call {
                        if (new) {
                            disableCurrentToggleIfEnabled()
                            val currentSplines = currentState.regionSplines
                            if (currentSplines != null) {
                                doGenerationStart()
                                editToggleCurrentPointer.value = editSplinesMode
                                editSplinesActivated = true
                                val splineMap = LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>()
                                var index = 0
                                currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 0, pair, edges))
                                }
                                currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 1, pair, edges))
                                }
                                currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 2, pair, edges))
                                }
                                currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 3, points to points, edges))
                                }
                                currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 4, points to points, edges))
                                }
                                currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quadruple(index, 5, points to points, edges))
                                }
                                splineSelectorMap = splineMap
                                if (displayMode != DisplayMode.MAP) {
                                    val mapTextureId = TextureBuilder.renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints)
                                    meshViewport.setImage(mapTextureId)
                                    imageMode.value = 1
                                    displayMode = DisplayMode.MAP
                                    defaultToMap = true
                                }
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setImage(newTexture)
                                    }
                                }
                                val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, editSplinesSelectionRadius.value.toFloat()), 4096)
                                val selectorMatrix = ShortArrayMatrix(4096) { i ->
                                    var offset = i * 4
                                    val r = splineSelectors[offset++].toInt() and 0x000000FF
                                    val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                                    (r or g).toShort()
                                }
                                splineSelectorMatrix = selectorMatrix
                                pointPicker.value = SplinePointPicker(currentState, currentSplines, splineMap, selectorMatrix, textureReference)
                                pickerOn.value = true
                            } else {
                                editSplinesMode.value = false
                            }
                        } else {
                            if (editSplinesActivated) {
                                splineSelectorMatrix = null
                                pointPicker.value = null
                                pickerOn.value = false
                                val parameters = extractCurrentParameters()
                                buildRegionsFun(parameters, true, false)
                                val currentGraph = currentState.regionGraph
                                val currentMask = currentState.regionMask
                                if (currentGraph != null && currentMask != null) {
                                    updateRegionsHistory(parameters, currentGraph, currentMask)
                                }
                                editSplinesActivated = false
                                if (editToggleCurrentPointer.value == editSplinesMode) {
                                    editToggleCurrentPointer.value = null
                                }
                                doGenerationStop()
                            }
                            editToggleLatch.value?.countDown()
                        }
                    }
                }
            }
            editSplinesMode.value = false
            deleteSplinesToggle = vToggleRow(deleteSplinesMode, LARGE_ROW_HEIGHT, text("Delete splines:"), shrinkGroup, MEDIUM_SPACER_SIZE)
            deleteSplinesMode.listener { old, new ->
                if (old != new) {
                    executor.call {
                        if (new) {
                            val currentSplines = currentState.regionSplines
                            if (currentSplines != null) {
                                doGenerationStart()
                                val splineMap = LinkedHashMap<Int, Quintuple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>, Boolean>>()
                                var index = 0
                                currentSplines.riverOrigins.zip(currentSplines.riverPoints).zip(currentSplines.riverEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 0, pair, edges, false))
                                }
                                currentSplines.mountainOrigins.zip(currentSplines.mountainPoints).zip(currentSplines.mountainEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 1, pair, edges, false))
                                }
                                currentSplines.ignoredOrigins.zip(currentSplines.ignoredPoints).zip(currentSplines.ignoredEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 2, pair, edges, false))
                                }
                                currentSplines.deletedOrigins.zip(currentSplines.deletedPoints).zip(currentSplines.deletedEdges).forEach { (pair, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 2, pair, edges, true))
                                }
                                currentSplines.customRiverPoints.zip(currentSplines.customRiverEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 3, points to points, edges, false))
                                }
                                currentSplines.customMountainPoints.zip(currentSplines.customMountainEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 4, points to points, edges, false))
                                }
                                currentSplines.customIgnoredPoints.zip(currentSplines.customIgnoredEdges).forEach { (points, edges) ->
                                    index++
                                    splineMap.put(index, Quintuple(index, 5, points to points, edges, false))
                                }
                                val textureReference = ref(TextureId(-1))
                                textureReference.listener { oldTexture, newTexture ->
                                    if (oldTexture != newTexture) {
                                        meshViewport.setImage(newTexture)
                                    }
                                }
                                executor.call {
                                    textureReference.value = renderMapImage(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints, currentSplines.ignoredPoints + currentSplines.customIgnoredPoints, currentSplines.deletedPoints)
                                }
                                val splineSelectors = extractTextureRgbaByte(renderSplineSelectors(splineMap.values.map { it.first to it.third.second }, 80.0f), 4096)
                                val selectorMatrix = ShortArrayMatrix(4096) { i ->
                                    var offset = i * 4
                                    val r = splineSelectors[offset++].toInt() and 0x000000FF
                                    val g = (splineSelectors[offset].toInt() and 0x000000FF) shl 8
                                    (r or g).toShort()
                                }
                                pointPicker.value = SplineDeletePicker(currentState, currentSplines, splineMap, selectorMatrix, textureReference)
                                pickerOn.value = true
                            }
                        } else {
                            pointPicker.value = null
                            pickerOn.value = false
                            val parameters = extractCurrentParameters()
                            buildRegionsFun(parameters, true, false)
                            val currentGraph = currentState.regionGraph
                            val currentMask = currentState.regionMask
                            if (currentGraph != null && currentMask != null) {
                                updateRegionsHistory(parameters, currentGraph, currentMask)
                            }
                            doGenerationStop()
                        }
                    }
                }
            }
            deleteSplinesMode.value = false
        }
        mapPanel.isVisible = false
        val biomePanel = vExpandPanel("Edit biomes", expanded = true) {
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
        biomePanel.isVisible = false
        val mainButtonsRow = vButtonRow(LARGE_ROW_HEIGHT) {
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
                        val regionTextureId = TextureBuilder.renderMapImage(currentRegionSplines.coastPoints, currentRegionSplines.riverPoints + currentRegionSplines.customRiverPoints, currentRegionSplines.mountainPoints + currentRegionSplines.customMountainPoints, currentRegionSplines.ignoredPoints + currentRegionSplines.customIgnoredPoints)
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
                            TextureBuilder.renderSplines(currentSplines.coastPoints, currentSplines.riverPoints + currentSplines.customRiverPoints, currentSplines.mountainPoints + currentSplines.customMountainPoints)
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
        mainButtonsRow.isVisible = false
        val newProjectPanel = block {
            vSpacer(HALF_ROW_HEIGHT)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("New project"), NORMAL_TEXT_BUTTON_STYLE) {
                    newProject(overwriteWarningReference, overwriteWarningDialog, dialogCallback, noop)
                }
            }
        }
        currentProject.listener { _, new ->
            if (new != null) {
                newProjectPanel.isVisible = false
                if (new.isNew) {
                    regionPanel.isVisible = true
                    mapPanel.isVisible = true
                    biomePanel.isVisible = true
                    mainButtonsRow.isVisible = true
                }
                enableGenerateButtons()
            } else {
                regionPanel.isVisible = false
                biomePanel.isVisible = false
                mainButtonsRow.isVisible = false
                newProjectPanel.isVisible = true
                enableGenerateButtons()
            }
        }
        enableGenerateButtons()
    }
}

private fun disableCurrentToggleIfEnabled() {
    val currentToggle = editToggleCurrentPointer.value
    if (currentToggle != null) {
        val waiter = CountDownLatch(1)
        editToggleLatch.value = waiter
        currentToggle.value = false
        while (waiter.count > 0) {
            try {
                waiter.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        editToggleLatch.value = null
    }
}

private fun updateRegionsHistory(parameters: ParameterSet, graph: Graph, regionMask: ByteArrayMatrix) {
    val historyLast = historyRegionsCurrent.value
    if (historyLast != null) {
        if ((historyRegionsBackQueue.size == 0 || historyRegionsBackQueue.peek() != historyLast) && (parameters != historyLast.parameters || historyLast.graphSeed != graph.seed || !Arrays.equals(historyLast.regionMask.array, regionMask.array))) {
            historyRegionsBackQueue.push(historyLast.copy())
        }
    }
    historyRegionsForwardQueue.clear()
    historyRegionsCurrent.value = RegionHistoryItem(parameters.copy(), graph.seed, ByteArrayMatrix(regionMask.width, regionMask.array.copyOf()))
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

private fun loadRegionMaskFromImage(file: File): ByteArrayMatrix {
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
        colorMap.map { it.key to it.value }.filter { it.first != 0 }.sortedByDescending { it.first and 0x00FF0000 ushr 16 }.forEachIndexed { i, (first) -> colorMap[first] = i + 1 }
        colorMap[0] = 0
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
