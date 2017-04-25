package com.grimfox.gec.util

import com.grimfox.gec.extensions.call
import com.grimfox.gec.extensions.join
import com.grimfox.gec.extensions.value
import com.grimfox.gec.model.FloatArrayMatrix
import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.ByteArrayMatrix
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Point3F
import com.grimfox.gec.util.geometry.renderTriangle
import java.awt.image.BufferedImage
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

object WaterFlows {

    private val MIN_U = 0.000004f
    private val MAX_U = 0.0005f
    private val DELTA_U = MAX_U - MIN_U
    private val K = 0.000000561f
    private val M = 0.5
    private val DT = 250000.0f
    private val DISTANCE_SCALE = 50000.0f
    private val AREA_SCALE = DISTANCE_SCALE * DISTANCE_SCALE
    private val TALUS = 0.5773672f

    class WaterNode(val id: Int,
                    val isExternal: Boolean,
                    val area: Float,
                    val uplift: Float,
                    var height: Float,
                    var drainageArea: Float) {

        var lake: Int = -1
        var parent: WaterNode = this
        val children: ArrayList<WaterNode> = ArrayList()
    }

    data class PassKey(val lake1: Int, val lake2: Int)

    class Pass(val passKey: PassKey, val id1: Int, val id2: Int, val height: Float)

    fun generateWaterFlows(random: Random, resolution: Int, inputGraph: Graph, inputMask: Matrix<Byte>, executor: ExecutorService, outputWidth: Int): BufferedImage {
        val graph = Graphs.generateGraph(resolution, random, 0.8, false, false)
        val vertices = graph.vertices
        val (idMask, water) = Coastline.applyMask(graph, inputGraph, inputMask, executor)
        val beach = extractBeachFromGraphAndWater(vertices, water)
        val regions = extractRegionsFromIdMask(vertices, idMask)
        val land = extractLandFromGraphAndWater(vertices, water)
        val regionBeachFutures = regions.map { region ->
            executor.call {
                LinkedHashSet(region.filter { beach.contains(it) })
            }
        }
        val regionBeaches = regionBeachFutures.map { it.value }
        val regionBorderFutures = regions.map { region ->
            executor.call {
                LinkedHashSet(region.filter { isBorderPoint(vertices, water, region, it) })
            }
        }
        val regionBorders = regionBorderFutures.map { it.value }
        regionBeaches.forEachIndexed { i, regionBeach ->
            regionBeach.removeAll(regionBorders[i])
        }
        val upliftMap = ByteArrayMatrix(resolution) { -128 }
        val upliftMapFutures = regions.mapIndexed { i, region ->
            executor.call {
                calculateUplift(vertices, region, regionBeaches[i], regionBorders[i], upliftMap)
            }
        }
        upliftMapFutures.forEach { it.join() }

        val riverMouths = LinkedHashSet(beach)

        val nodes = ArrayList<WaterNode>(land.size)
        val nodeIndex = arrayOfNulls<WaterNode>(graph.vertices.size)
        land.forEach {
            val tempLift = upliftMap[it].toInt() + 128
            val uplift: Float = if (tempLift == 0) {
                0.0f
            } else {
                (tempLift / 256.0f) * DELTA_U + MIN_U
            }
            val isExternal = riverMouths.contains(it)
            val area = vertices[it].cell.area * AREA_SCALE
            val node = WaterNode(it, isExternal, area, uplift, 0.0f, area)
            nodes.add(node)
            nodeIndex[it] = node
        }

        val rivers = ArrayList<WaterNode>()
        riverMouths.forEach { id ->
            rivers.add(nodeIndex[id]!!)
        }

        val unused = LinkedHashSet(land)
        val used = LinkedHashSet(riverMouths)
        unused.removeAll(used)
        val next = LinkedHashSet(riverMouths)
        while (unused.isNotEmpty()) {
            val nextOrder = ArrayList(next)
            Collections.shuffle(nextOrder, random)
            next.clear()
            nextOrder.forEach { id ->
                val node = nodeIndex[id]!!
                graph.vertices.getAdjacentVertices(node.id).forEach {
                    val otherNode = nodeIndex[it]
                    if (otherNode != null && !used.contains(otherNode.id)) {
                        otherNode.parent.children.remove(otherNode)
                        otherNode.parent = node
                        node.children.add(otherNode)
                        next.add(otherNode.id)
                        used.add(otherNode.id)
                        unused.remove(otherNode.id)
                    }
                }
            }
        }
        rivers.forEach { recurseArea(it) }
        var deltaTime = DT * 8
        rivers.forEach { recurseHeights(vertices, it, deltaTime) }

        val lakes = ArrayList<WaterNode>()
        val passes = LinkedHashMap<PassKey, Pass>()

        for (i in 1..100) {
            if (i == 10) {
                deltaTime /= 2
            }
            if (i == 30) {
                deltaTime /= 2
            }
            if (i == 60) {
                deltaTime /= 2
            }
            lakes.clear()
            passes.clear()

            nodes.forEach { node ->
                node.lake = -1
                if (!node.isExternal) {
                    var minHeight = node.height
                    var minNode = node
                    graph.vertices.getAdjacentVertices(node.id).forEach {
                        val otherNode = nodeIndex[it]
                        if (otherNode != null) {
                            if (otherNode.height < minHeight) {
                                minNode = otherNode
                                minHeight = otherNode.height
                            }
                        }
                    }
                    if (minNode != node.parent) {
                        node.parent.children.remove(node)
                        node.parent = minNode
                        if (minNode != node) {
                            minNode.children.add(node)
                        }
                    }
                    if (minNode == node) {
                        lakes.add(node)
                    }
                }
            }

            rivers.forEachIndexed { id, waterNode ->
                recurseSetLake(id, waterNode)
            }
            lakes.forEachIndexed { id, waterNode ->
                recurseSetLake(id + rivers.size, waterNode)
            }
            lakes.forEach { waterNode ->
                recurseFindPasses(vertices, nodeIndex, waterNode, passes)
            }
            val expandedPasses = ArrayList<Pass>(passes.size * 2)
            passes.values.forEach {
                expandedPasses.add(it)
                expandedPasses.add(Pass(PassKey(it.passKey.lake2, it.passKey.lake1), it.id2, it.id1, it.height))
            }
            expandedPasses.sortByDescending { it.height }
            val outflowing = LinkedHashSet<Int>()
            rivers.forEach {
                outflowing.add(it.lake)
            }
            while (expandedPasses.isNotEmpty()) {
                for (j in (expandedPasses.size - 1) downTo 0) {
                    val currentPass = expandedPasses[j]
                    if (outflowing.contains(currentPass.passKey.lake1)) {
                        expandedPasses.removeAt(j)
                        continue
                    } else if (outflowing.contains(currentPass.passKey.lake2)) {
                        outflowing.add(currentPass.passKey.lake1)
                        expandedPasses.removeAt(j)
                        val childNode = recurseFindParent(nodeIndex[currentPass.id1]!!)
                        val parentNode = nodeIndex[currentPass.id2]!!
                        parentNode.children.add(childNode)
                        childNode.parent = parentNode
                        break
                    }
                }
            }

            rivers.forEach { recurseArea(it) }
            rivers.forEach { recurseHeights(vertices, it, deltaTime) }
        }

        val heightMap = FloatArrayMatrix(outputWidth) { - 1.0f }
        renderTriangles(executor, graph, nodeIndex, heightMap, 16)
        return writeUpliftData(heightMap)
    }

    private fun recurseFindParent(waterNode: WaterNode): WaterNode {
        if (waterNode.isExternal || waterNode.parent == waterNode) {
            return waterNode
        }
        return recurseFindParent(waterNode.parent)
    }

    private fun recurseFindPasses(vertices: Graph.Vertices, nodeIndex: Array<WaterNode?>, node: WaterNode, passes: LinkedHashMap<PassKey, Pass>) {
        vertices.getAdjacentVertices(node.id).forEach {
            val otherNode = nodeIndex[it]
            if (otherNode != null && otherNode.lake != node.lake) {
                val minLakeId = Math.min(node.lake, otherNode.lake)
                val swapped = node.lake != minLakeId
                val passKey = PassKey(minLakeId, Math.max(node.lake, otherNode.lake))
                val passHeight = Math.max(node.height, otherNode.height)
                val currentMin = passes[passKey]
                if (currentMin == null || currentMin.height > passHeight) {
                    if (swapped) {
                        passes[passKey] = Pass(passKey, otherNode.id, node.id, passHeight)
                    } else {
                        passes[passKey] = Pass(passKey, node.id, otherNode.id, passHeight)
                    }
                }
            }
        }
        node.children.forEach { recurseFindPasses(vertices, nodeIndex, it, passes) }
    }

    private fun recurseSetLake(id: Int, node: WaterNode) {
        node.lake = id
        node.children.forEach { child ->
            recurseSetLake(id, child)
        }
    }

    private fun recurseArea(node: WaterNode): Float {
        if (node.children.isEmpty()) {
            node.drainageArea = node.area
            return node.drainageArea
        } else {
            var sum: Float = node.area
            node.children.forEach { child ->
                sum += recurseArea(child)
            }
            node.drainageArea = sum
            return sum
        }
    }

    private fun recurseHeights(vertices: Graph.Vertices, node: WaterNode, deltaTime: Float) {
        if (node.isExternal) {
            node.children.forEach { recurseHeights(vertices, it, deltaTime) }
        } else {
            val parent = node.parent
            val position = vertices[node.id].point
            val parentPosition = vertices[parent.id].point
            val parentHeight = parent.height
            val distance = position.distance(parentPosition) * DISTANCE_SCALE
            val flow = K * Math.pow(node.drainageArea.toDouble(), M)
            val erosion = flow / distance
            val denominator = 1.0 + (erosion * deltaTime)
            val numerator = node.height + (deltaTime * (node.uplift + (erosion * parentHeight)))
            node.height = (numerator / denominator).toFloat()
            if ((node.height - parent.height) / distance > TALUS) {
                node.height = (distance * TALUS) + parent.height
            }
            node.children.forEach { recurseHeights(vertices, it, deltaTime) }
        }
    }

    private fun calculateUplift(vertices: Graph.Vertices, region: LinkedHashSet<Int>, beach: LinkedHashSet<Int>, border: LinkedHashSet<Int>, upliftMap: Matrix<Byte>) {
        val remaining = HashSet(region)
        var currentIds = HashSet(border)
        var nextIds = HashSet<Int>(border.size)
        var currentUplift = 127.toByte()
        for (i in 0..11) {
            currentIds.forEach {
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = currentUplift
                    vertices.getAdjacentVertices(it).forEach { adjacentId ->
                        if (remaining.contains(adjacentId) && !currentIds.contains(adjacentId)) {
                            nextIds.add(adjacentId)
                        }
                    }
                }
            }
            val temp = currentIds
            currentIds = nextIds
            nextIds = temp
            nextIds.clear()
            currentUplift = (currentUplift - if (i % 2 == 0) 13 else 14).toByte()
        }
        currentIds.clear()
        currentIds.addAll(beach)
        nextIds.clear()
        currentUplift = -126
        for (i in 1..33) {
            currentIds.forEach {
                if (remaining.contains(it)) {
                    remaining.remove(it)
                    upliftMap[it] = currentUplift
                    vertices.getAdjacentVertices(it).forEach { adjacentId ->
                        if (remaining.contains(adjacentId) && !currentIds.contains(adjacentId)) {
                            nextIds.add(adjacentId)
                        }
                    }
                }
            }
            val temp = currentIds
            currentIds = nextIds
            nextIds = temp
            nextIds.clear()
            if (i < 33) {
                currentUplift = (currentUplift + if (i % 2 == 0) 2 else 3).toByte()
            }
        }
        remaining.forEach { upliftMap[it] = currentUplift }
    }

    private fun extractBeachFromGraphAndWater(vertices: Graph.Vertices, water: LinkedHashSet<Int>) =
            (0..vertices.size - 1).filterTo(LinkedHashSet<Int>()) { isCoastalPoint(vertices, water, it) }

    private fun extractLandFromGraphAndWater(vertices: Graph.Vertices, water: LinkedHashSet<Int>) =
            (0..vertices.size - 1).filterTo(LinkedHashSet<Int>()) { !water.contains(it) }

    private fun isCoastalPoint(vertices: Graph.Vertices, water: Set<Int>, vertexId: Int): Boolean {
        if (water.contains(vertexId)) {
            return false
        }
        vertices.getAdjacentVertices(vertexId).forEach { adjacentVertexId ->
            if (water.contains(adjacentVertexId)) {
                return true
            }
        }
        return false
    }

    private fun isBorderPoint(vertices: Graph.Vertices, water: Set<Int>, region: Set<Int>, vertexId: Int): Boolean {
        vertices.getAdjacentVertices(vertexId).forEach { adjacentPoint ->
            if (!water.contains(adjacentPoint) && !region.contains(adjacentPoint)) {
                return true
            }
        }
        return false
    }

    private fun extractRegionsFromIdMask(vertices: Graph.Vertices, idMask: Matrix<Byte>): ArrayList<LinkedHashSet<Int>> {
        val regions = ArrayList<LinkedHashSet<Int>>()
        for (i in 0..vertices.size - 1) {
            val maskValue = idMask[i]
            if (maskValue > 0) {
                val regionId = maskValue - 1
                if (regions.size < maskValue) {
                    for (j in 0..regionId - regions.size) {
                        regions.add(LinkedHashSet<Int>())
                    }
                }
                regions[regionId].add(i)
            }
        }
        return regions
    }

    fun renderTriangles(executor: ExecutorService, graph: Graph, nodeIndex: Array<WaterNode?>, heightMap: Matrix<Float>, threadCount: Int) {
        val futures = ArrayList<Future<*>>(threadCount)
        val triangles = graph.triangles
        for (i in 0..threadCount - 1) {
            futures.add(executor.submit {
                for (t in i..triangles.size - 1 step threadCount) {
                    val triangle = triangles[t]
                    val va = triangle.a
                    val vb = triangle.b
                    val vc = triangle.c
                    val na = nodeIndex[va.id]
                    val nb = nodeIndex[vb.id]
                    val nc = nodeIndex[vc.id]
                    if (na == null || nb == null || nc == null) {
                        continue
                    } else {
                        val pa = va.point
                        val pb = vb.point
                        val pc = vc.point
                        val a = Point3F(pa.x, pa.y, na.height)
                        val b = Point3F(pb.x, pb.y, nb.height)
                        val c = Point3F(pc.x, pc.y, nc.height)
                        val cross = (b - a).cross(c - a)
                        if (cross.c < 0) {
                            renderTriangle(a, b, c, heightMap)
                        } else {
                            renderTriangle(a, c, b, heightMap)
                        }
                    }
                }
            })
        }
        futures.forEach(Future<*>::join)
    }

    fun writeUpliftData(heightMap: Matrix<Float>): BufferedImage {
        val output = BufferedImage(heightMap.width, heightMap.width, BufferedImage.TYPE_USHORT_GRAY)
        val raster = output.raster
        var max = 0.0f
        for (i in 0..heightMap.size.toInt() - 1) {
            val height = heightMap[i]
            if (height > max) {
                max = height
            }
        }
        for (y in (0..heightMap.width - 1)) {
            for (x in (0..heightMap.width - 1)) {
                val heightValue = heightMap[x, y]
                if (heightValue < 0.0f) {
                    raster.setSample(x, y, 0, 0)
                } else {
                    val sample = Math.round((heightValue / max) * 55535.0f) + 9999
                    raster.setSample(x, y, 0, sample)
                }
            }
        }
        return output
    }
}