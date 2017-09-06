package com.grimfox.gec.brushes

import com.grimfox.gec.CurrentState
import com.grimfox.gec.executor
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Polygon2F
import com.grimfox.gec.ui.widgets.MeshViewport3D
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.renderMapImage
import com.grimfox.gec.util.BuildContinent.RegionSplines
import com.grimfox.gec.util.BuildContinent.buildOpenEdges
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.Quadruple
import com.grimfox.gec.util.Reference
import com.grimfox.gec.util.call
import java.lang.Math.round
import java.util.*

class SplineDrawBrushListener(
        val splineSmoothing: Reference<Int>,
        val currentState: CurrentState,
        val currentSplines: RegionSplines,
        val splineMap: LinkedHashMap<Int, Quadruple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>>>,
        val texture: MutableReference<TextureId>) : MeshViewport3D.BrushListener {

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
                    texture.value = renderMapImage(
                            currentSplines.coastPoints,
                            riverPoints + customRiverPoints,
                            mountainPoints + customMountainPoints,
                            ignoredPoints + customIgnoredPoints,
                            pendingPoints)
                } else {
                    renderMapImage(
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
                    texture.value = renderMapImage(
                            currentSplines.coastPoints,
                            riverPoints + customRiverPoints,
                            mountainPoints + customMountainPoints,
                            ignoredPoints + customIgnoredPoints)
                } else {
                    renderMapImage(
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