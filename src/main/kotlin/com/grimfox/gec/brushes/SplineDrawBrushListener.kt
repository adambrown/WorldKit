package com.grimfox.gec.brushes

import com.grimfox.gec.CurrentState
import com.grimfox.gec.executor
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Polygon2F
import com.grimfox.gec.ui.widgets.MeshViewport3D
import com.grimfox.gec.ui.widgets.TextureBuilder.renderSplines
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.renderMapImage
import com.grimfox.gec.util.*
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.gec.util.BuildContinent.buildClosedEdges
import com.grimfox.gec.util.BuildContinent.buildOpenEdges
import java.lang.Math.round
import java.util.*
import kotlin.collections.ArrayList

class SplineDrawBrushListener(
        val splineSmoothing: Reference<Int>,
        val isClosed: Reference<Boolean>,
        val currentState: CurrentState,
        val currentSplines: RegionSplines,
        val splineMap: LinkedHashMap<Int, Quintuple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>, Boolean>>,
        val texture: MutableReference<TextureId>,
        val renderAsSplines: Boolean) : MeshViewport3D.BrushListener {

    private var wholeSpline: MutableList<Point2F>? = null
    private var previousSplines: MutableList<List<Point2F>>? = null
    private var currentSpline: MutableList<Point2F>? = null

    override fun onMouseDown(x: Float, y: Float) {
        wholeSpline = ArrayList()
        previousSplines = ArrayList()
        currentSpline = ArrayList()
        onLine(x, y, x, y)
    }

    override fun onLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val wholeSpline = wholeSpline
        val previousSplines = previousSplines
        val currentSpline = currentSpline
        if (wholeSpline != null && previousSplines != null && currentSpline != null) {
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
                if (!it.value.fifth) {
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
            }
            val smoothing = round(((splineSmoothing.value / 20.0f).coerceIn(0.0f, 1.0f) * 11) + 13)
            val pendingPoints = if (currentSpline.size == 1) {
                listOf(buildEdges(ArrayList(currentSpline + currentSpline), smoothing))
            } else if (currentSpline.size >= 1000 && !isClosed.value) {
                val toFreeze = ArrayList(currentSpline.subList(0, 500))
                val toClear = currentSpline.subList(0, 499)
                wholeSpline.addAll(toClear)
                toClear.clear()
                val lastLine = buildEdges(toFreeze, smoothing)
                previousSplines.add(lastLine)
                val newList = ArrayList(previousSplines)
                val newLine = buildEdges(currentSpline, smoothing)
                newList.add(newLine)
                val adjustedPoint = Point2F(
                        (lastLine[lastLine.size - 2].x + lastLine[lastLine.size - 1].x + newLine[0].x + newLine[1].x) / 4.0f,
                        (lastLine[lastLine.size - 2].y + lastLine[lastLine.size - 1].y + newLine[0].y + newLine[1].y) / 4.0f)
                newLine[0] = adjustedPoint
                lastLine[lastLine.size - 1] = adjustedPoint
                newList
            } else {
                val newList = ArrayList(previousSplines)
                val newLine = buildEdges(currentSpline, smoothing)
                val adjustedPoint = previousSplines.lastOrNull()?.lastOrNull()
                if (adjustedPoint != null) {
                    newLine[0] = adjustedPoint
                }
                newList.add(newLine)
                newList
            }
            executor.call {
                if (renderAsSplines) {
                    if (texture.value.id < 0) {
                        texture.value = renderSplines(
                                currentSplines.coastPoints,
                                riverPoints + customRiverPoints,
                                mountainPoints + customMountainPoints,
                                ignoredPoints + customIgnoredPoints,
                                pendingPoints)
                    } else {
                        texture.value = renderSplines(
                                currentSplines.coastPoints,
                                riverPoints + customRiverPoints,
                                mountainPoints + customMountainPoints,
                                ignoredPoints + customIgnoredPoints,
                                pendingPoints,
                                texture.value)
                    }
                } else {
                    if (texture.value.id < 0) {
                        texture.value = renderMapImage(
                                currentSplines.coastPoints,
                                riverPoints + customRiverPoints,
                                mountainPoints + customMountainPoints,
                                ignoredPoints + customIgnoredPoints,
                                pendingPoints)
                    } else {
                        texture.value = renderMapImage(
                                currentSplines.coastPoints,
                                riverPoints + customRiverPoints,
                                mountainPoints + customMountainPoints,
                                ignoredPoints + customIgnoredPoints,
                                pendingPoints,
                                texture.value)
                    }
                }
            }
        }
    }

    private fun buildEdges(currentSpline: MutableList<Point2F>, smoothing: Int): MutableList<Point2F> {
        return if (isClosed.value) {
            buildClosedEdges(listOf(Polygon2F(currentSpline, false)), smoothing)
        } else {
            buildOpenEdges(Polygon2F(currentSpline, false), smoothing)
        }
    }

    override fun onMouseUp(x1: Float, y1: Float, x2: Float, y2: Float) {
        val wholeSpline = wholeSpline
        val currentSpline = currentSpline
        if (wholeSpline != null && currentSpline != null) {
            wholeSpline.addAll(currentSpline)
            val finishedSpline = wholeSpline
            if (x1 in 0.0f..1.0f && y1 in 0.0f..1.0f && x2 in 0.0f..1.0f && y2 in 0.0f..1.0f) {
                finishedSpline.add(Point2F(x2, y2))
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
                if (!it.value.fifth) {
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
            val smoothing = round(((splineSmoothing.value / 20.0f).coerceIn(0.0f, 1.0f) * 11) + 13)
            val newPoints = if (finishedSpline.size == 1) {
                buildEdges(ArrayList(finishedSpline + finishedSpline), smoothing)
            } else {
                buildEdges(finishedSpline, smoothing)
            }
            val newEdges = (1..newPoints.size - 1).mapTo(ArrayList()) { LineSegment2F(newPoints[it - 1], newPoints[it]) }
            customIgnoredPoints.add(newPoints)
            customIgnoredEdges.add(newEdges)
            splineMap.put(splineMap.size + 1, Quintuple(splineMap.size + 1, 5, newPoints to newPoints, newEdges, false))
            currentState.regionSplines.value = RegionSplines(
                    true,
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
                    currentState.regionSplines.value?.deletedOrigins ?: listOf(),
                    currentState.regionSplines.value?.deletedEdges ?: listOf(),
                    currentState.regionSplines.value?.deletedPoints ?: listOf(),
                    customRiverEdges,
                    customRiverPoints,
                    customMountainEdges,
                    customMountainPoints,
                    customIgnoredEdges,
                    customIgnoredPoints)
            this.currentSpline = null
            executor.call {
                if (renderAsSplines) {
                    if (texture.value.id < 0) {
                        texture.value = renderSplines(
                                currentSplines.coastPoints,
                                riverPoints + customRiverPoints,
                                mountainPoints + customMountainPoints,
                                ignoredPoints + customIgnoredPoints)
                    } else {
                        texture.value = renderSplines(
                                currentSplines.coastPoints,
                                riverPoints + customRiverPoints,
                                mountainPoints + customMountainPoints,
                                ignoredPoints + customIgnoredPoints,
                                listOf(),
                                texture.value)
                    }
                } else {
                    if (texture.value.id < 0) {
                        texture.value = renderMapImage(
                                currentSplines.coastPoints,
                                riverPoints + customRiverPoints,
                                mountainPoints + customMountainPoints,
                                ignoredPoints + customIgnoredPoints)
                    } else {
                        texture.value = renderMapImage(
                                currentSplines.coastPoints,
                                riverPoints + customRiverPoints,
                                mountainPoints + customMountainPoints,
                                ignoredPoints + customIgnoredPoints,
                                listOf(),
                                texture.value)
                    }
                }
            }
        }
    }
}