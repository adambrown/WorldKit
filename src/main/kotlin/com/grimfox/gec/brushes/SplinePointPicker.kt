package com.grimfox.gec.brushes

import com.grimfox.gec.CurrentState
import com.grimfox.gec.executor
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.ui.widgets.MeshViewport3D
import com.grimfox.gec.ui.widgets.TextureBuilder
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.renderMapImage
import com.grimfox.gec.util.*
import java.lang.Math.round
import java.util.*

class SplinePointPicker(
        val currentState: CurrentState,
        val currentSplines: BuildContinent.RegionSplines,
        val splineMap: LinkedHashMap<Int, Quintuple<Int, Int, Pair<List<Point2F>, List<Point2F>>, List<LineSegment2F>, Boolean>>,
        val mask: Matrix<Short>,
        val texture: MutableReference<TextureId>,
        val renderAsSplines: Boolean) : MeshViewport3D.PointPicker {

    private val maskWidth = mask.width
    private val maskWidthM1 = maskWidth - 1

    override fun onMouseDown(x: Float, y: Float) {
        val selectedId = mask[round(x * maskWidthM1), round(y * maskWidthM1)].toInt()
        if (selectedId > 0) {
            val splineToToggle = splineMap[selectedId]
            if (splineToToggle != null) {
                splineMap.put(selectedId, Quintuple(
                        splineToToggle.first,
                        if (splineToToggle.second < 3) (splineToToggle.second + 1) % 3 else ((splineToToggle.second - 2) % 3) + 3,
                        splineToToggle.third,
                        splineToToggle.fourth,
                        false))
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
                currentState.regionSplines.value = BuildContinent.RegionSplines(
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
                executor.call {
                    if (renderAsSplines) {
                        if (texture.value.id < 0) {
                            texture.value = TextureBuilder.renderSplines(
                                    currentSplines.coastPoints,
                                    riverPoints + customRiverPoints,
                                    mountainPoints + customMountainPoints,
                                    ignoredPoints + customIgnoredPoints)
                        } else {
                            TextureBuilder.renderSplines(
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
    }
}