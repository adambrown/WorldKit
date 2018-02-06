package com.grimfox.gec.brushes

import com.grimfox.gec.executor
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.ui.widgets.MeshViewport3D
import com.grimfox.gec.ui.widgets.TextureBuilder
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.Reference
import com.grimfox.gec.util.Rendering.renderRegions
import com.grimfox.gec.util.call
import java.lang.Math.*

class PreSelectDrawBrushListener(
        val graph: Graph,
        val mask: Matrix<Byte>,
        val brushSize: Reference<Float>,
        val texture: MutableReference<TextureBuilder.TextureId>,
        val currentValue: Reference<Byte>) : MeshViewport3D.BrushListener {

    private val maskWidth = mask.width
    private val maskWidthM1 = maskWidth - 1

    override fun onMouseDown(x: Float, y: Float) {
        onLine(x, y, x, y)
    }

    override fun onLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val maxDist = (brushSize.value / 2.0f)
        val maxDist2 = maxDist * maxDist
        val brushMargin = ceil(maxDist.toDouble() * maskWidthM1).toInt() + 1
        val cpStartX = max(0, round(Math.min(x1, x2) * maskWidthM1) - brushMargin)
        val cpEndX = min(maskWidthM1, round(Math.max(x1, x2) * maskWidthM1) + brushMargin)
        val cpStartY = max(0, round(Math.min(y1, y2) * maskWidthM1) - brushMargin)
        val cpEndY = min(maskWidthM1, round(Math.max(y1, y2) * maskWidthM1) + brushMargin)
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
                texture.value = renderRegions(graph, mask, texture.value)
            }
        }
    }

    override fun onMouseUp(x1: Float, y1: Float, x2: Float, y2: Float) {}
}