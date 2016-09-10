package com.grimfox.gec.util

import com.grimfox.gec.model.ArrayListMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import java.util.*

object Mask {

    fun applyMask(graph: Graph, maskGraph: Graph, mask: Set<Int>, negate: Boolean = false) : HashSet<Int> {
        val vertices = graph.vertices
        val newMask = HashSet<Int>()
        for (y in 0..graph.stride - 1) {
            for (x in 0..graph.stride - 1) {
                val vertex = vertices[x, y]
                val closePoint = maskGraph.getClosestPoint(vertex.point)
                val masked = mask.contains(closePoint)
                if (masked) {
                    if (!negate) {
                        newMask.add(vertex.id)
                    }
                } else if (negate) {
                    newMask.add(vertex.id)
                }
            }
        }
        return newMask
    }

    fun applyMask(graph: Graph, maskGraph: Graph, mask: Matrix<Int>) : Matrix<Int> {
        val vertices = graph.vertices
        val newMask = ArrayListMatrix(graph.stride) { vertexId ->
            val vertex = vertices[vertexId]
            val point = vertex.point
            val closePoint = maskGraph.getClosestPoint(point)
            mask[closePoint]
        }
        return newMask
    }
}