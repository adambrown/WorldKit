package com.grimfox.gec.util

import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.LineSegment2F
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Vector2F
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.ui.widgets.TextureBuilder.renderTrianglesTexRedByte
import com.grimfox.gec.ui.widgets.TextureBuilder.renderTrianglesTexRedFloat
import com.grimfox.gec.ui.widgets.TextureBuilder.renderTrianglesToTexture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.GL_NEAREST
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

object Rendering {

    fun renderRegions(graph: Graph, regionMask: Matrix<Byte>, divisor: Float = 256.0f, offset: Float = 0.0f, skips: Int = 0, scale: Float = 1.0f): TextureId {
        val (vertexData, indexData) = renderInternal(offset, graph, regionMask, skips, divisor, scale)
        return renderTrianglesTexRedByte(vertexData.toFloatArray(), indexData.toIntArray(), GL11.GL_NEAREST, GL11.GL_NEAREST)
    }

    fun renderRegions(graph: Graph, regionMask: Matrix<Byte>, textureId: TextureId, divisor: Float = 256.0f, offset: Float = 0.0f, skips: Int = 0, scale: Float = 1.0f): TextureId {
        val (vertexData, indexData) = renderInternal(offset, graph, regionMask, skips, divisor, scale)
        renderTrianglesToTexture(vertexData.toFloatArray(), indexData.toIntArray(), textureId)
        return textureId
    }

    private fun renderInternal(offset: Float, graph: Graph, regionMask: Matrix<Byte>, skips: Int, divisor: Float, scale: Float = 1.0f): Pair<ArrayList<Float>, ArrayList<Int>> {
        val base = 1.0f - offset
        val vertexData = ArrayList<Float>()
        val indexData = ArrayList<Int>()
        val vertices = graph.vertices
        var vertexIndex = 0
        for (id in 0 until vertices.size) {
            val regionId = regionMask[id]
            if (regionId < 1) {
                continue
            }
            val region = (((regionId - skips) / divisor) * base) + offset
            val vertex = vertices[id]
            val border = vertex.cell.border

            fun buildVertex(point: Point2F): Int {
                vertexData.add(point.x * scale)
                vertexData.add(point.y * scale)
                vertexData.add(region)
                return vertexIndex++
            }

            fun buildTriangle(v1: Int, v2: Int, v3: Int) {
                indexData.add(v1)
                indexData.add(v3)
                indexData.add(v2)
            }

            val centerId = buildVertex(vertex.point)
            val borderIds = border.map { buildVertex(it) }

            for (i in 1..border.size) {
                buildTriangle(centerId, borderIds[i % border.size], borderIds[i - 1])
            }
        }
        return Pair(vertexData, indexData)
    }

    fun renderRegionBorders(executor: ExecutorService, graph: Graph, regionMask: Matrix<Byte>, threadCount: Int, scale: Float = 1.0f): TextureId {
        val vertices = graph.vertices
        val regions = ArrayList<LinkedHashSet<Int>>(16)
        for (i in 0 until vertices.size) {
            val maskValue = regionMask[i]
            if (maskValue >= 1) {
                val regionId = maskValue - 1
                if (regions.size < maskValue) {
                    for (j in 0..regionId - regions.size) {
                        regions.add(LinkedHashSet())
                    }
                }
                regions[regionId].add(i)
            }
        }
        val borderEdgeFutures = (0 until regions.size).map { i ->
            executor.call {
                val region = regions[i]
                val mask = LinkedHashSet<Int>()
                for (j in (i + 1 until regions.size)) {
                    mask.addAll(regions[j])
                }
                graph.findBorderEdges(region, mask, false, true)
            }
        }
        val edges = borderEdgeFutures.flatMap { it.value }
        return renderEdges(executor, edges, threadCount, scale)
    }

    fun renderCoastalBorders(executor: ExecutorService, graph: Graph, regionMask: Matrix<Byte>, threadCount: Int): TextureId {
        val vertices = graph.vertices
        val regions = ArrayList<LinkedHashSet<Int>>(16)
        val land = LinkedHashSet<Int>(vertices.size)
        for (i in 0 until vertices.size) {
            val maskValue = regionMask[i]
            if (maskValue >= 1) {
                val regionId = maskValue - 1
                land.add(i)
                if (regions.size < maskValue) {
                    for (j in 0..regionId - regions.size) {
                        regions.add(LinkedHashSet())
                    }
                }
                regions[regionId].add(i)
            }
        }
        val borderEdgeFutures = (0 until regions.size).map { i ->
            executor.call {
                val region = regions[i]
                val mask = LinkedHashSet<Int>()
                for (j in (i + 1 until regions.size)) {
                    mask.addAll(regions[j])
                }
                graph.findBorderEdges(region, land, true, true)
            }
        }
        val borderEdges = borderEdgeFutures.flatMap { it.value }
        return renderEdges(executor, borderEdges, threadCount)
    }

    fun renderEdges(executor: ExecutorService, edges: List<LineSegment2F>, threadCount: Int, scale: Float = 1.0f, minFilter: Int = GL_NEAREST, magFilter: Int = GL_NEAREST): TextureId {
        val vertexData = FloatArray(edges.size * 60)
        val indexData = IntArray(edges.size * 60)
        val futures = ArrayList<Future<*>>(threadCount)
        (0 until threadCount).mapTo(futures) {
            executor.submit {
                for (i in it until edges.size step threadCount) {
                    var vertexOffset = i * 60
                    var vertexIndex = i * 20
                    fun buildVertex(x: Float, y: Float, z: Float): Int {
                        vertexData[vertexOffset++] = x
                        vertexData[vertexOffset++] = y
                        vertexData[vertexOffset++] = z
                        return vertexIndex++
                    }
                    val edge = edges[i]
                    val spoke4 = Vector2F(edge).getUnit()
                    val perpendicular = spoke4.getPerpendicular()
                    val spoke2 = (spoke4 + perpendicular).getUnit()
                    val spoke1 = (perpendicular + spoke2).getUnit()
                    val spoke3 = (spoke4 + spoke2).getUnit()
                    val spoke5 = spoke1.getPerpendicular()
                    val spoke6 = spoke2.getPerpendicular()
                    val spoke7 = spoke3.getPerpendicular()
                    val p1 = buildVertex(edge.a.x * scale, edge.a.y * scale, 1.0f)
                    val p2 = buildVertex(edge.b.x * scale, edge.b.y * scale, 1.0f)
                    val p32d = edge.b + perpendicular
                    val p3 = buildVertex(p32d.x * scale, p32d.y * scale, 0.0f)
                    val p42d = edge.a + perpendicular
                    val p4 = buildVertex(p42d.x * scale, p42d.y * scale, 0.0f)
                    val p52d = edge.a - perpendicular
                    val p5 = buildVertex(p52d.x * scale, p52d.y * scale, 0.0f)
                    val p62d = edge.b - perpendicular
                    val p6 = buildVertex(p62d.x * scale, p62d.y * scale, 0.0f)
                    val p72d = edge.a + spoke7
                    val p7 = buildVertex(p72d.x * scale, p72d.y * scale, 0.0f)
                    val p82d = edge.a + spoke6
                    val p8 = buildVertex(p82d.x * scale, p82d.y * scale, 0.0f)
                    val p92d = edge.a + spoke5
                    val p9 = buildVertex(p92d.x * scale, p92d.y * scale, 0.0f)
                    val p102d = edge.a - spoke4
                    val p10 = buildVertex(p102d.x * scale, p102d.y * scale, 0.0f)
                    val p112d = edge.a - spoke3
                    val p11 = buildVertex(p112d.x * scale, p112d.y * scale, 0.0f)
                    val p122d = edge.a - spoke2
                    val p12 = buildVertex(p122d.x * scale, p122d.y * scale, 0.0f)
                    val p132d = edge.a - spoke1
                    val p13 = buildVertex(p132d.x * scale, p132d.y * scale, 0.0f)
                    val p142d = edge.b - spoke7
                    val p14 = buildVertex(p142d.x * scale, p142d.y * scale, 0.0f)
                    val p152d = edge.b - spoke6
                    val p15 = buildVertex(p152d.x * scale, p152d.y * scale, 0.0f)
                    val p162d = edge.b - spoke5
                    val p16 = buildVertex(p162d.x * scale, p162d.y * scale, 0.0f)
                    val p172d = edge.b + spoke4
                    val p17 = buildVertex(p172d.x * scale, p172d.y * scale, 0.0f)
                    val p182d = edge.b + spoke3
                    val p18 = buildVertex(p182d.x * scale, p182d.y * scale, 0.0f)
                    val p192d = edge.b + spoke2
                    val p19 = buildVertex(p192d.x * scale, p192d.y * scale, 0.0f)
                    val p202d = edge.b + spoke1
                    val p20 = buildVertex(p202d.x * scale, p202d.y * scale, 0.0f)

                    var indexOffset = i * 60
                    fun buildTriangle(v1: Int, v2: Int, v3: Int) {
                        indexData[indexOffset++] = v1
                        indexData[indexOffset++] = v3
                        indexData[indexOffset++] = v2
                    }

                    buildTriangle(p1, p2, p3)
                    buildTriangle(p3, p4, p1)
                    buildTriangle(p1, p5, p6)
                    buildTriangle(p6, p2, p1)

                    buildTriangle(p1, p4, p7)
                    buildTriangle(p7, p8, p1)
                    buildTriangle(p1, p8, p9)
                    buildTriangle(p9, p10, p1)
                    buildTriangle(p1, p10, p11)
                    buildTriangle(p11, p12, p1)
                    buildTriangle(p1, p12, p13)
                    buildTriangle(p13, p5, p1)

                    buildTriangle(p2, p6, p14)
                    buildTriangle(p14, p15, p2)
                    buildTriangle(p2, p15, p16)
                    buildTriangle(p16, p17, p2)
                    buildTriangle(p2, p17, p18)
                    buildTriangle(p18, p19, p2)
                    buildTriangle(p2, p19, p20)
                    buildTriangle(p20, p3, p2)
                }
            }
        }
        futures.forEach { it.join() }
        return renderTrianglesTexRedFloat(vertexData, indexData, minFilter, magFilter)
    }
}