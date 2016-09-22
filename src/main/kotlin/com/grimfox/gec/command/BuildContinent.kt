package com.grimfox.gec.command

import com.grimfox.gec.Main
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.CellEdge
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.util.Coastline.applyMask
import com.grimfox.gec.util.Coastline.getBorders
import com.grimfox.gec.util.Coastline.getCoastline
import com.grimfox.gec.util.Coastline.refineCoastline
import com.grimfox.gec.util.drawing.*
import com.grimfox.gec.util.Regions.buildRegions
import com.grimfox.gec.util.Rivers
import com.grimfox.gec.util.Rivers.RiverNode
import com.grimfox.gec.util.Rivers.buildRiverGraph
import com.grimfox.gec.util.Rivers.buildRivers
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils
import com.grimfox.gec.util.Utils.generatePoints
import com.grimfox.gec.util.Utils.getLineIntersection
import com.grimfox.gec.util.Utils.midPoint
import com.sun.scenario.animation.SplineInterpolator
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.awt.BasicStroke
import java.awt.Color
import java.io.File
import java.util.*

@Command(name = "build-continent", description = "Builds a continent.")
class BuildContinent() : Runnable {

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var randomSeed: Long = System.currentTimeMillis()

    @Option(name = arrayOf("-s", "--strides"), description = "The number of points in the stride of each iteration.", required = false)
    var strides: ArrayList<Int> = ArrayList()

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    var draw = false

    data class Parameters(
            var stride: Int,
            var landPercent: Float,
            var minPerturbation: Float,
            var maxIterations: Int,
            var protectedInset: Float,
            var protectedRadius: Float,
            var minRegionSize: Float,
            var largeIsland: Float,
            var smallIsland: Float)

    data class ParameterSet(
            var seed: Long = System.currentTimeMillis(),
            var stride: Int = 7,
            var regionCount: Int = 8,
            var connectedness: Float = 0.11f,
            var regionSize: Float = 0.035f,
            var initialReduction: Int = 7,
            var regionPoints: Int = 2,
            var maxRegionTries: Int = 200,
            var maxIslandTries: Int = 2000,
            var islandDesire: Int = 1,
            var parameters: ArrayList<Parameters> = arrayListOf(
                    Parameters(30, 0.35f, 0.05f, 4, 0.1f, 0.05f, 0.035f, 2.0f, 0.0f),
                    Parameters(80, 0.39f, 0.05f, 3, 0.1f, 0.05f, 0.035f, 2.0f, 0.005f),
                    Parameters(140, 0.39f, 0.03f, 2, 0.1f, 0.05f, 0.035f, 2.0f, 0.01f),
                    Parameters(256, 0.39f, 0.01f, 2, 0.1f, 0.05f, 0.035f, 2.0f, 0.015f)
            ),
            var currentIteration: Int = 0)

    override fun run() {
        val parameterSet = ParameterSet()
        if (strides.isEmpty()) {
            strides.addAll(listOf(7, 40, 80, 140, 256))
        } else {
            strides.sort()
        }
        for (test in 1..10000) {
            val virtualWidth = 100000.0f
            val outputWidth = 4096
            parameterSet.seed = test.toLong()
            val random = Random(parameterSet.seed)
            var (graph, regionMask) = buildRegions(parameterSet)
//            drawRegions(lastGraph, regionMask, outputWidth, "test-new-${String.format("%05d", test)}-ri", Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.DARK_GRAY)
            parameterSet.parameters.forEachIndexed { i, parameters ->
                parameterSet.currentIteration = i
                val localLastGraph = graph
                val localGraph: Graph
                val points = generatePoints(parameters.stride, virtualWidth, random)
                localGraph = buildGraph(virtualWidth, points, parameters.stride)
//                drawRegions(lastGraph, regionMask, outputWidth, "test-new-${String.format("%05d", test)}-r$i-pre", Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.DARK_GRAY)
                regionMask = applyMask(localGraph, localLastGraph, regionMask)
//                drawRegions(localGraph, regionMask, outputWidth, "test-new-${String.format("%05d", test)}-r$i-post", Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.DARK_GRAY)
//                drawOffLimits(localGraph, regionMask, offLimits, 2048, "test-new-${String.format("%05d", test)}-o$i")
//                drawRegions(localGraph, regionMask, outputWidth, "test-new-${String.format("%05d", test)}-r$i", Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.DARK_GRAY)
                refineCoastline(localGraph, random, regionMask, parameters)
                graph = localGraph
//                drawGraph(graph, outputWidth, "test-graph-${String.format("%05d", i)}")
//                drawBorder(graph, outputWidth, "test-border-${String.format("%05d", i)}")
//                drawRegions(graph, regionMask, outputWidth, "test-new-${String.format("%05d", test)}-r$i", Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.DARK_GRAY)
            }
            val rivers = buildRivers(graph, regionMask, random)
//            draw(outputWidth, "test-new-${String.format("%05d", test)}-concavity") {
//                graphics.color = Color.BLACK
//                drawConcavity(graph, rivers.first)
//            }
            val borders = getBorders(graph, regionMask)
            rivers.forEachIndexed { i, body ->
                val coastline = body.first
                val coastSpline = Spline(coastline)
                val riverSet = body.second
                val border = borders[i]
                val riverGraph = buildRiverGraph(riverSet)
                val riverFlows = calculateRiverFlows(riverGraph.vertices, coastline, riverSet, 1600000000.0f)
                draw(outputWidth, "test-new-${String.format("%05d", test)}-rivers$i", Color(160, 200, 255)) {
                    drawRivers(graph, regionMask, riverSet, listOf(coastSpline), border)
                }
                draw(outputWidth, "test-new-${String.format("%05d", test)}-graph$i", Color.WHITE) {
                    drawGraph(riverGraph)
                }
                draw(outputWidth, "test-new-${String.format("%05d", test)}-mask$i", Color.BLACK) {
                    graphics.color = Color.WHITE
                    fillSpline(coastSpline)
                }
                draw(outputWidth, "test-new-${String.format("%05d", test)}-coast$i") {
                    graphics.color = Color.BLACK
                    drawSpline(coastSpline, false)
                }
                draw(outputWidth, "test-new-${String.format("%05d", test)}-ids$i") {
                    graphics.color = Color.BLACK
                    drawVertexIds(riverGraph)
                }
                val riverSplines = calculateRiverSplines(riverGraph, riverSet, riverFlows)
                draw(outputWidth, "test-new-${String.format("%05d", test)}-splines$i") {
                    graphics.color = Color.BLACK
                    graphics.stroke = BasicStroke(3.0f)
                    riverSplines.forEach {
                        drawSpline(it, false)
                    }
                }
            }

//            drawSlopes(graph, slopes, outputWidth, "test-new-${String.format("%05d", test)}-slopes")
//            drawOffLimits(lastGraph!!, regionMask, offLimits, 2048, "test-new-${String.format("%05d", test)}-o")
//            drawBorders(lastGraph!!, regionMask, borders, 2048, "test-new-${String.format("%05d", test)}-b")
//            drawMask(lastGraph, spokes, outputWidth, "test-new-${String.format("%05d", test)}-rs")
//            drawSpokes(spokes, outputWidth, "test-new-${String.format("%05d", test)}-m")
//            drawMask(lastGraph, lastMask, outputWidth, "test-new-${String.format("%05d", test)}-m", false)
//            drawRegions(graph, regionMask, outputWidth, "test-new-${String.format("%05d", test)}-r", Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.DARK_GRAY)
//            drawConcavity(lastGraph, concavityWeights, outputWidth, "test-new-${String.format("%05d", test)}-c")
        }
    }

    private fun calculateRiverSplines(graph: Graph, rivers: HashSet<RiverNode>, flows: FloatArray): Collection<Spline> {
        val splines = ArrayList<Spline>()
        rivers.forEach { outlet ->
            calculateRiverSplines(graph, null, outlet,  flows, splines)
        }
        return splines
    }

    private fun calculateRiverSplines(graph: Graph, parentNode: RiverNode?, node: RiverNode, flows: FloatArray, globalSplines: ArrayList<Spline>) {
//        if (node.pointIndex == 569) {
//            println("start this bitch")
//            draw = true
//        }
        val localSplines = ArrayList<Spline>()
        val vertex = graph.vertices[node.pointIndex]
        val cell = vertex.cell
        val drainVector: Point
        val cellEdges = cell.borderEdges
        var drainEdgeIndex = -1
        val drainPoint = if (parentNode == null) {
            if (node.children.isEmpty()) {
                drainVector = Point(0.0f, 0.0f)
            } else {
                drainVector = toUnitVector(toVector(vertex.point, node.children.first().pointLocation))
            }
            vertex.point
        } else {
            val parentVertex = graph.vertices[parentNode.pointIndex]
            val parentCell = parentVertex.cell
            val sharedEdge = cell.sharedEdge(parentCell)!!
            for (i in 0..cellEdges.size - 1) {
                if (cellEdges[i] == sharedEdge) {
                    drainEdgeIndex = i
                }
            }
            val edge = Pair(sharedEdge.tri1.center, sharedEdge.tri2.center)
            drainVector = calculateEntryVectorFromEdge(edge)
            getLineIntersection(edge, Pair(parentVertex.point, vertex.point)) ?: midPoint(edge.first, edge.second)
        }
        val entryPoints = ArrayList<Pair<Triple<Point, Float, Point>, Int>>()
        var inFlow = 0.0f
        node.children.forEach {
            val childVertex = graph.vertices[it.pointIndex]
            val childCell = childVertex.cell
            var childEdge: CellEdge? = null
            try {
                childEdge = cell.sharedEdge(childCell)!!
            } catch (e: Exception) {
                if (draw) {
                    draw(4096, "testing") {
                        graphics.color = Color.BLACK
                        cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
                        graphics.color = Color.BLUE
                        childCell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
                        graphics.color = Color.RED
                        drawPoint(vertex.point, 3)
                        graphics.color = Color.ORANGE
                        drawPoint(childVertex.point, 3)
                        graphics.color = Color.CYAN
                        drawPoint(drainPoint, 3)
                        graphics.color = Color.GREEN
                        drawEdge(vertex.point, childVertex.point)
                    }
                }
                e.printStackTrace()
            }
            inFlow += flows[it.pointIndex]
            for (i in 0..cellEdges.size - 1) {
                val cellEdge = cellEdges[i]
                if (cellEdge == childEdge) {
                    try {
                        val edge = Pair(cellEdge.tri1.center, cellEdge.tri2.center)
                        val entryPoint = getLineIntersection(edge, Pair(vertex.point, childVertex.point)) ?: midPoint(edge.first, edge.second)
                        entryPoints.add(Pair(Triple(entryPoint, flows[it.pointIndex], calculateEntryVectorFromEdge(edge)), i))
                    } catch (e: Exception) {
                        if (draw) {
                            draw(4096, "testing") {
                                graphics.color = Color.BLACK
                                cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
                                graphics.color = Color.RED
                                drawPoint(vertex.point, 3)
                                graphics.color = Color.BLUE
                                entryPoints.forEach { drawPoint(it.first.first, 3) }
                                graphics.color = Color.CYAN
                                drawPoint(drainPoint, 3)
                                graphics.color = Color.ORANGE
                                drawEdge(childEdge!!.tri1.center, childEdge!!.tri2.center)
                                graphics.color = Color.GREEN
                                drawEdge(vertex.point, childVertex.point)
                            }
                        }
                        e.printStackTrace()
                    }
                }
            }
        }
        val entries = ArrayList(entryPoints.sortedBy { if (it.second < drainEdgeIndex) { it.second + cellEdges.size } else { it.second } }.map { it.first })
        var currentEntry = if (entries.isEmpty()) {
            Triple(vertex.point, 0.0f, toUnitVector(toVector(vertex.point, drainPoint)))
        } else {
            entries.removeAt(0)
        }
        val junctions = ArrayList<Point>()
        while (entries.isNotEmpty()) {
            val newEntry = entries.removeAt(0)
            val (junction, junctionSplines) = connectRiverEntries(drainPoint, currentEntry.first, currentEntry.second, currentEntry.third, newEntry.first, newEntry.second, newEntry.third)
            if (draw) {
                draw(4096, "testing") {
                    graphics.color = Color.BLACK
                    cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
                    graphics.color = Color.RED
                    drawPoint(vertex.point, 3)
                    graphics.color = Color.BLUE
                    localSplines.forEach { drawSpline(it, false) }
                    graphics.color = Color.MAGENTA
                    entryPoints.forEach { drawPoint(it.first.first, 3) }
                    graphics.color = Color.CYAN
                    drawPoint(drainPoint, 3)
                    graphics.color = Color.ORANGE
                    drawSpline(junctionSplines.first)
                    graphics.color = Color.PINK
                    drawSpline(junctionSplines.second)
                    graphics.color = Color.GREEN
                    drawPoint(junction.first, 3)
                }
            }
            junctions.add(junction.first)
            currentEntry = junction
            localSplines.add(junctionSplines.first)
            localSplines.add(junctionSplines.second)
        }
        if (currentEntry.first != vertex.point || drainPoint != vertex.point) {
            localSplines.add(connectEntryToDrain(currentEntry.first, currentEntry.third, drainPoint, drainVector))
        }
        if (draw) {
            draw(4096, "testing") {
                graphics.color = Color.BLACK
                cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
                graphics.color = Color.RED
                drawPoint(vertex.point, 3)
                graphics.color = Color.BLUE
                localSplines.forEach { drawSpline(it, false) }
                graphics.color = Color.MAGENTA
                entryPoints.forEach { drawPoint(it.first.first, 3) }
                graphics.color = Color.CYAN
                drawPoint(drainPoint, 3)
                graphics.color = Color.GREEN
                junctions.forEach {
                    drawPoint(it, 3)
                }
            }
        }
        node.children.forEach {
            calculateRiverSplines(graph, node, it,  flows, globalSplines)
        }
        globalSplines.addAll(localSplines)
    }

    private fun  connectEntryToDrain(junction: Point, junctionVector: Point, drain: Point, drainVector: Point): Spline {
        val dist = Math.sqrt(junction.distanceSquaredTo(drain).toDouble()) * 0.3666666667
        val cp1 = Point(junction.x + (junctionVector.x * dist).toFloat(), junction.y + (junctionVector.y * dist).toFloat())
        val cp2 = Point(drain.x + (drainVector.x * dist).toFloat(), drain.y + (drainVector.y * dist).toFloat())
        val sp1 = SplinePoint(junction, junction, cp1)
        val sp2 = SplinePoint(drain, cp2, drain)
        val ret = Spline(mutableListOf(sp1, sp2), false)
        if (draw) {
            draw(4096, "testing") {
                graphics.color = Color.BLACK
                drawPoint(drain, 3)
                graphics.color = Color.RED
                drawPoint(junction, 3)
                graphics.color = Color.BLUE
                drawEdge(junction, Point(junction.x + junctionVector.x, junction.y + junctionVector.y))
                graphics.color = Color.MAGENTA
                drawEdge(drain, Point(drain.x + drainVector.x, drain.y + drainVector.y))
                graphics.color = Color.GREEN
                drawSpline(ret)
            }
        }
        return ret
    }

    private fun calculateEntryVectorFromEdge(edge: Pair<Point, Point>): Point {
        return toUnitVector(Point(edge.first.y - edge.second.y, -(edge.first.x - edge.second.x)))
    }

    private fun toUnitVector(vector: Point): Point {
        val length = Math.sqrt((vector.x * vector.x + vector.y * vector.y).toDouble())
        return Point((vector.x / length).toFloat(), (vector.y / length).toFloat())
    }

    private fun toVector(p1: Point, p2: Point): Point {
        return Point(p2.x - p1.x, p2.y - p1.y)
    }

    private fun connectRiverEntries(drainPoint: Point, entry1: Point, flow1: Float, entryVector1: Point, entry2: Point, flow2: Float, entryVector2: Point): Pair<Triple<Point, Float, Point>, Pair<Spline, Spline>> {
        val nanSafeFlow1 = if (flow1 < 0.00000001f) { 0.00000001f } else { flow1 }
        val nanSafeFlow2 = if (flow1 < 0.00000001f) { 0.00000001f } else { flow2 }
        val totalDist = (Math.sqrt(drainPoint.distanceSquaredTo(entry1).toDouble()) + Math.sqrt(drainPoint.distanceSquaredTo(entry2).toDouble()) * 0.3)
        val totalFlow = nanSafeFlow1 + nanSafeFlow2
        val junctionOffset = nanSafeFlow1 / totalFlow
        val dist1 = junctionOffset * totalDist
        val dist2 = (nanSafeFlow2 / totalFlow) * totalDist
        val cp1 = Point(entry1.x + (entryVector1.x * dist1).toFloat(), entry1.y + (entryVector1.y * dist1).toFloat())
        val cp2 = Point(entry2.x + (entryVector2.x * dist2).toFloat(), entry2.y + (entryVector2.y * dist2).toFloat())
        val sp1 = SplinePoint(entry1, entry1, cp1)
        val sp2 = SplinePoint(entry2, cp2, entry2)
        val sp3 = sp1.interpolate(sp2, 1.0f - junctionOffset)
        if (draw) {
            draw(4096, "testing") {
                graphics.color = Color.BLACK
                drawPoint(drainPoint, 3)
                graphics.color = Color.RED
                drawPoint(entry1, 3)
                graphics.color = Color.BLUE
                drawPoint(entry2, 3)
                graphics.color = Color.MAGENTA
                drawEdge(entry1, Point(entry1.x + entryVector1.x, entry1.y + entryVector1.y))
                graphics.color = Color.CYAN
                drawEdge(entry2, Point(entry2.x + entryVector2.x, entry2.y + entryVector2.y))
                graphics.color = Color.ORANGE
                drawPoint(sp3.point, 3)
                graphics.color = Color.GREEN
                drawSpline(Spline(mutableListOf(sp1, sp2), false))
            }
        }
        sp1.cp2 = Point(entry1.x + (entryVector1.x * dist1 * 0.3).toFloat(), entry1.y + (entryVector1.y * dist1 * 0.3).toFloat())
        sp2.cp1 = entry2
        sp2.cp2 = Point(entry2.x + (entryVector2.x * dist2 * 0.3).toFloat(), entry2.y + (entryVector2.y * dist2 * 0.3).toFloat())
        val junction = sp3.point
        val entry1ToDrain = toVector(entry1, drainPoint)
        val entry2ToDrain = toVector(entry2, drainPoint)
        val normalizedFlowVector = toUnitVector(Point(((entry1ToDrain.x + entry2ToDrain.x) * totalDist * 0.5).toFloat(), ((entry1ToDrain.y + entry2ToDrain.y) * totalDist * 0.5).toFloat()))
        val avgX = entryVector1.x * dist1 + entryVector2.x * dist2 + normalizedFlowVector.x
        val avgY = entryVector1.y * dist1 + entryVector2.y * dist2 + normalizedFlowVector.y
        val length3 = Math.sqrt((avgX * avgX + avgY * avgY).toDouble())
        val junctionOutput = Point((avgX / length3).toFloat(), (avgY / length3).toFloat())
        sp3.cp1 = Point((junction.x + ((-junctionOutput.x * totalDist) * 0.15)).toFloat(), (junction.y + ((-junctionOutput.y * totalDist) * 0.15)).toFloat())
        val ret =  Pair(Triple(junction, totalFlow, junctionOutput), Pair(Spline(mutableListOf(sp1, sp3), false), Spline(mutableListOf(sp2, sp3), false)))
        if (draw) {
            draw(4096, "testing") {
                graphics.color = Color.BLACK
                drawPoint(drainPoint, 3)
                graphics.color = Color.RED
                drawPoint(entry1, 3)
                graphics.color = Color.BLUE
                drawPoint(entry2, 3)
                graphics.color = Color.MAGENTA
                drawEdge(entry1, Point(entry1.x + entryVector1.x, entry1.y + entryVector1.y))
                graphics.color = Color.CYAN
                drawEdge(entry2, Point(entry2.x + entryVector2.x, entry2.y + entryVector2.y))
                graphics.color = Color.ORANGE
                drawPoint(junction, 3)
                graphics.color = Color.PINK
                drawEdge(junction, Point(junction.x + junctionOutput.x, junction.y + junctionOutput.y))
                graphics.color = Color.GREEN
                drawSpline(ret.second.first)
                drawSpline(ret.second.second)
            }
        }
        return ret
    }

    private fun calculateRiverFlows(vertices: Vertices, coastline: Polygon, rivers: HashSet<RiverNode>, simulationSizeM2: Float): FloatArray {
        val flows = FloatArray(vertices.size)
        rivers.forEach {
            calculateRiverFlow(vertices, coastline, it, simulationSizeM2, flows)
        }
        return flows
    }

    private fun calculateRiverFlow(vertices: Vertices, coastline: Polygon, river: RiverNode, simulationSizeM2: Float, flows: FloatArray): Double {
        var shedArea = 0.0
        river.children.forEach {
            shedArea += calculateRiverFlow(vertices, coastline, it, simulationSizeM2, flows)
        }
        val cell = vertices[river.pointIndex].cell
        if (!cell.isBorder && !Polygon(cell.border, true).doesEdgeIntersect(coastline)) {
            shedArea += cell.area
        }
        flows[river.pointIndex] = (0.42 * Math.pow(shedArea, 0.69)).toFloat()
        return shedArea
    }
}
