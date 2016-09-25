package com.grimfox.gec.command

import com.grimfox.gec.Main
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.CellEdge
import com.grimfox.gec.model.Graph.Vertices
import com.grimfox.gec.model.geometry.*
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

    var draw = true

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
            val outputWidth = 8192
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
                val coastSpline = Spline2F(coastline)
                val riverSet = body.second
                val border = borders[i]
                val riverGraph = buildRiverGraph(riverSet)
                val riverFlows = calculateRiverFlows(riverGraph.vertices, coastline, riverSet, 1600000000.0f, 0.39f)
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-rivers$i", Color(160, 200, 255)) {
//                    drawRivers(graph, regionMask, riverSet, listOf(coastSpline), border)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-graph$i", Color.WHITE) {
//                    drawGraph(riverGraph)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-mask$i", Color.BLACK) {
//                    graphics.color = Color.WHITE
//                    fillSpline(coastSpline)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-coast$i") {
//                    graphics.color = Color.BLACK
//                    drawSpline(coastSpline, false)
//                }
//                draw(outputWidth, "test-new-${String.format("%05d", test)}-ids$i") {
//                    graphics.color = Color.BLACK
//                    drawVertexIds(riverGraph)
//                }
                val riverSplines = calculateRiverSegments(riverGraph, random, riverSet, riverFlows, test)

//                val flows = riverSplines.filter { it.value.slope != 0.0f }.map { it.value.flow }.sorted()
//                val flowMax = flows.last()
//                val flowMin = flows.first()
//                val flowMed = flows[(flows.size * 0.5).toInt()]
//                val flowAvg = flows.sum() / flows.size
//
//                val elevations = riverSplines.filter { it.value.slope != 0.0f }.map { (it.value.elevations.a + it.value.elevations.b) * 0.5f }.sorted()
//                val elevationMax = elevations.last()
//                val elevationMin = elevations.first()
//                val elevationMed = elevations[(elevations.size * 0.6).toInt()]
//                val elevationAvg = elevations.sum() / elevations.size
//
//                val slopes = riverSplines.map { it.value.slope }.filter { it != 0.0f }.sorted()
//                val slopeMax = slopes.last()
//                val slopeMin = slopes.first()
//                val slopeMed = slopes[(slopes.size * 0.5).toInt()]
//                val slopeAvg = slopes.sum() / slopes.size

//                println("max flow: $flowMax")
//                println("min flow: $flowMin")
//                println("med flow: $flowMed")
//                println("avg flow: $flowAvg")
//
//                println("max elevation: $elevationMax")
//                println("min elevation: $elevationMin")
//                println("med elevation: $elevationMed")
//                println("avg elevation: $elevationAvg")
//
//                println("max slope: $slopeMax")
//                println("min slope: $slopeMin")
//                println("med slope: $slopeMed")
//                println("avg slope: $slopeAvg")
                val segmentLength = 1.0f / 4096.0f
                draw(outputWidth, "test-new-${String.format("%05d", test)}-splines$i") {
                    graphics.color = Color.BLACK
                    graphics.stroke = BasicStroke(1.0f)
                    drawRiverPolyLines(riverSplines, segmentLength, 3, false)
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

    private fun calculateRiverSegments(graph: Graph, random: Random, rivers: ArrayList<TreeNode<RiverNode>>, flows: FloatArray, test: Int): ArrayList<TreeNode<RiverSegment>> {
        val trees = ArrayList<TreeNode<RiverSegment>>()
        rivers.forEach { outlet ->
            trees.add(calculateRiverSplines(graph, null, outlet,  flows))
        }
        var direction = -1.0f
        trees.forEach { tree ->
            tree.forEach { segment ->
                val profile = segment.profile
                val spline = segment.spline.subdivided(profile.spacing.nextValue(random), 3)
                if (spline.points.size > 2) {
                    for (i in 1..spline.points.size - 2) {
                        val modifyPoint = spline.points[i]
                        direction = -direction
                        val power = random.nextFloat()
                        val deviation = Math.max(0.0f, Math.min(profile.deviation.valueAt(power), 1.0f))
                        val magnitude = profile.strength.valueAt(power)
                        val originalVector = Vector2F(modifyPoint.p, modifyPoint.cp2)
                        val deviantVector = originalVector.getPerpendicular() * (deviation * direction)
                        val newUnitVector = ((originalVector * (1.0f - deviation)) + deviantVector).getUnit()
                        val newVector1 = newUnitVector * (originalVector.length * magnitude)
                        val newVector2 = -newUnitVector * (Vector2F(modifyPoint.p, modifyPoint.cp1).length * magnitude)
                        modifyPoint.cp1 = modifyPoint.p + newVector2
                        modifyPoint.cp2 = modifyPoint.p + newVector1
//                        if (draw) {
//                            val xVals = spline.points.flatMap { listOf(it.p.x, it.cp1.x, it.cp2.x) }
//                            val minX = xVals.min()!!
//                            val maxX = xVals.max()!!
//                            val yVals = spline.points.flatMap { listOf(it.p.y, it.cp1.y, it.cp2.y) }
//                            val minY = yVals.min()!!
//                            val maxY = yVals.max()!!
//                            val deltaX = maxX - minX
//                            val deltaY = maxY - minY
//                            val shift = Vector2F(-minX + 0.001f, -minY + 0.001f)
//                            draw(1024, "testing", 60.0f, shift) {
//                                graphics.stroke = BasicStroke(1.5f)
//                                graphics.color = Color.BLACK
//                                drawSpline(segment.spline, true, true, true, Color.BLUE)
//                                graphics.color = Color.RED
//                                drawSpline(spline, true, true, true, Color.GREEN)
//                            }
//                        }
                    }
                }
                segment.spline = spline
            }
        }
        return trees
    }

    class RiverNodeTransition(val elevation: Float, val point: Point2F, val vector: Vector2F, val edge: CellEdge)

    class Junction(val vertexId: Int, val point: Point2F, val flow: Float, val vector: Vector2F, val elevation: Float, var splineElevation: Float? = null, var spline: Spline2F? = null, var node: TreeNode<RiverNode>? = null, var edge: CellEdge? = null)

    class RiverSegment(var spline: Spline2F, var flow: Float, var elevations: Vector2F, var slope: Float, var vertexId: Int, var profile: RiverProfile)

    private fun calculateRiverSplines(graph: Graph, transition: RiverNodeTransition? , node: TreeNode<RiverNode>, flows: FloatArray): TreeNode<RiverSegment> {
//        if (node.pointIndex == 569) {
//            println("start this bitch")
//            draw = true
//        }
        val vertex = graph.vertices[node.value.pointIndex]
        val vertexElevation = node.value.elevation
        val cell = vertex.cell
        val drainVector: Vector2F
        val cellEdges = cell.borderEdges
        var drainEdgeIndex = -1
        val drainElevation: Float
        val drainPoint = if (transition == null) {
            if (node.children.isEmpty()) {
                drainVector = Vector2F(0.0f, 0.0f)
            } else {
                drainVector = Vector2F(vertex.point, node.children.first().value.pointLocation).getUnit()
            }
            drainElevation = vertexElevation
            vertex.point
        } else {
            for (i in 0..cellEdges.size - 1) {
                if (cellEdges[i] == transition.edge) {
                    drainEdgeIndex = i
                }
            }
            drainVector = -transition.vector
            drainElevation = transition.elevation
            transition.point
        }
        val entryPoints = ArrayList<Pair<Junction, Int>>()
        var inFlow = 0.0f
        node.children.forEach { child ->
            val childVertex = graph.vertices[child.value.pointIndex]
            val childElevation = child.value.elevation
            val childCell = childVertex.cell
            try {
                val childEdge = cell.sharedEdge(childCell)!!
                inFlow += flows[child.value.pointIndex]
                for (i in 0..cellEdges.size - 1) {
                    val cellEdge = cellEdges[i]
                    if (cellEdge == childEdge) {
                        val edge = LineSegment2F(cellEdge.tri1.center, cellEdge.tri2.center)
                        val entryPoint = edge.intersection(LineSegment2F(vertex.point, childVertex.point)) ?: edge.interpolate(0.5f)
                        entryPoints.add(Pair(Junction(vertex.id, entryPoint, flows[child.value.pointIndex], calculateEntryVectorFromEdge(edge), (childElevation + vertexElevation) * 0.5f, node = child, edge = cellEdge), i))
                    }
                }
            } catch (e: Exception) {
                draw(4096, "testing") {
                    graphics.color = Color.BLACK
                    cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
                    graphics.color = Color.RED
                    childCell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
                    graphics.color = Color.BLUE
                    drawEdge(vertex.point, childVertex.point)
                    graphics.color = Color.GREEN
                    drawPoint(vertex.point, 3)
                    graphics.color = Color.MAGENTA
                    drawPoint(childVertex.point, 3)
                }
                println()
            }
        }
        val entries = ArrayList(entryPoints.sortedBy { if (it.second < drainEdgeIndex) { it.second + cellEdges.size } else { it.second } }.map { it.first })
        var junctions: TreeNode<Junction> = if (entries.isEmpty()) {
            TreeNode(Junction(vertex.id, vertex.point, 0.0f, Vector2F(vertex.point, drainPoint).getUnit(), vertexElevation))
        } else {
            TreeNode(entries.removeAt(0))
        }
        while (entries.isNotEmpty()) {
            val newEntry = entries.removeAt(0)
            val (junction, spline1, spline2) = connectRiverEntries(vertex.id, drainPoint, drainElevation, junctions.value.point, junctions.value.elevation, junctions.value.flow, junctions.value.vector, newEntry.point, newEntry.elevation, newEntry.flow, newEntry.vector)
//            if (draw) {
//                draw(4096, "testing") {
//                    graphics.color = Color.BLACK
//                    cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
//                    graphics.color = Color.RED
//                    drawPoint(vertex.point, 3)
//                    graphics.color = Color.BLUE
//                    localSplines.forEach { drawSpline(it, false) }
//                    graphics.color = Color.MAGENTA
//                    entryPoints.forEach { drawPoint(it.first.first, 3) }
//                    graphics.color = Color.CYAN
//                    drawPoint(drainPoint, 3)
//                    graphics.color = Color.ORANGE
//                    drawSpline(junctionSplines.first)
//                    graphics.color = Color.PINK
//                    drawSpline(junctionSplines.second)
//                    graphics.color = Color.GREEN
//                    drawPoint(junction.first, 3)
//                }
//            }
            val newJunctionNode = TreeNode(junction)
            junctions.parent = newJunctionNode
            newJunctionNode.children.add(junctions)
            junctions.value.spline = spline1
            junctions.value.splineElevation = junction.elevation
            val entryNode = TreeNode(newEntry)
            entryNode.parent = newJunctionNode
            newEntry.spline = spline2
            newEntry.splineElevation = junction.elevation
            newJunctionNode.children.add(entryNode)
            junctions = newJunctionNode
        }
        val rootNode = if (junctions.value.point == vertex.point && drainPoint == vertex.point) {
            val spline = Spline2F(mutableListOf(SplinePoint2F(vertex.point)), false)
            val elevations = Vector2F(vertexElevation, vertexElevation)
            val flow = flows[vertex.id]
            val slope = calculateSlope(spline, elevations)
            TreeNode(RiverSegment(spline, flow, elevations, slope, vertex.id, calculateRiverType(flow, slope, elevations)))
        } else {
            junctions.value.spline = connectEntryToDrain(junctions.value.point, junctions.value.vector, drainPoint, drainVector)
            junctions.value.splineElevation = drainElevation
            toSplineTree(graph, junctions, flows)!!
        }
//        if (draw) {
//            draw(4096, "testing") {
//                graphics.color = Color.BLACK
//                cell.borderEdges.forEach { drawEdge(it.tri1.center, it.tri2.center) }
//                graphics.color = Color.RED
//                drawPoint(vertex.point, 3)
//                graphics.color = Color.BLUE
//                localSplines.forEach { drawSpline(it, false) }
//                graphics.color = Color.MAGENTA
//                entryPoints.forEach { drawPoint(it.first.first, 3) }
//                graphics.color = Color.CYAN
//                drawPoint(drainPoint, 3)
//                graphics.color = Color.GREEN
//                junctions.forEach {
//                    drawPoint(it, 3)
//                }
//            }
//        }
        return rootNode
    }

    private fun toSplineTree(graph: Graph, junctions: TreeNode<Junction>, flows: FloatArray): TreeNode<RiverSegment>? {
        val spline = junctions.value.spline ?: return null
        val elevations = Vector2F(junctions.value.splineElevation!!, junctions.value.elevation)
        val slope = calculateSlope(spline, elevations)
        val treeNode = TreeNode(RiverSegment(spline, junctions.value.flow, elevations, slope, junctions.value.vertexId, calculateRiverType(junctions.value.flow, slope, elevations)))
        val riverNode = junctions.value.node
        if (riverNode != null) {
            treeNode.children.add(calculateRiverSplines(graph, RiverNodeTransition(junctions.value.elevation, junctions.value.point, junctions.value.vector, junctions.value.edge!!), riverNode, flows))
        }
        junctions.children.forEach {
            val subTree = toSplineTree(graph, it, flows)
            if (subTree != null) {
                treeNode.children.add(subTree)
            }
        }
        return treeNode
    }

    private fun calculateSlope(spline: Spline2F, elevations: Vector2F): Float {
        val points = spline.points
        if (points.size < 2) {
            if (elevations.a == elevations.b) {
                return 0.0f
            } else {
                return 1.0f
            }
        } else {
            val start = points.first().p
            val end = points.last().p
            val dx = start.distance(end)
            if (dx == 0.0f) {
                if (elevations.a == elevations.b) {
                    return 0.0f
                } else {
                    return 1.0f
                }
            }
            val dy = Math.abs(elevations.b - elevations.a)
            return dy / (dx + dy)
        }
    }

    private fun  connectEntryToDrain(junction: Point2F, junctionVector: Vector2F, drain: Point2F, drainVector: Vector2F): Spline2F {
        val dist = junction.distance(drain) * 0.3666666667f
        val cp1 = junction + (junctionVector * dist)
        val cp2 = drain + (drainVector * dist)
        val sp1 = SplinePoint2F(junction, junction, cp1)
        val sp2 = SplinePoint2F(drain, cp2, drain)
        val ret = Spline2F(mutableListOf(sp1, sp2), false)
//        if (draw) {
//            draw(4096, "testing") {
//                graphics.color = Color.BLACK
//                drawPoint(drain, 3)
//                graphics.color = Color.RED
//                drawPoint(junction, 3)
//                graphics.color = Color.BLUE
//                drawEdge(junction, Point2F(junction.x + junctionVector.a, junction.y + junctionVector.b))
//                graphics.color = Color.MAGENTA
//                drawEdge(drain, Point2F(drain.x + drainVector.a, drain.y + drainVector.b))
//                graphics.color = Color.GREEN
//                drawSpline(ret)
//            }
//        }
        return ret
    }

    private fun calculateEntryVectorFromEdge(edge: LineSegment2F): Vector2F {
        return -edge.toVector().getPerpendicular().getUnit()
    }

    private fun connectRiverEntries(vertexId: Int, drainPoint: Point2F, drainElevation: Float, entry1: Point2F, elevation1: Float, flow1: Float, entryVector1: Vector2F, entry2: Point2F, elevation2: Float, flow2: Float, entryVector2: Vector2F): Triple<Junction, Spline2F, Spline2F> {
        val nanSafeFlow1 = if (flow1 < 0.00000001f) { 0.00000001f } else { flow1 }
        val nanSafeFlow2 = if (flow1 < 0.00000001f) { 0.00000001f } else { flow2 }
        val totalDist = (drainPoint.distance(entry1) + drainPoint.distance(entry2)) * 0.3f
        val totalFlow = nanSafeFlow1 + nanSafeFlow2
        val junctionOffset = nanSafeFlow1 / totalFlow
        val dist1 = junctionOffset * totalDist
        val dist2 = (nanSafeFlow2 / totalFlow) * totalDist
        val cp1 = entry1 + (entryVector1 * dist1)
        val cp2 = entry2 + (entryVector2 * dist2)
        val sp1 = SplinePoint2F(entry1, entry1, cp1)
        val sp2 = SplinePoint2F(entry2, cp2, entry2)
        val sp3 = sp1.interpolate(sp2, 1.0f - junctionOffset)
//        if (draw) {
//            draw(4096, "testing") {
//                graphics.color = Color.BLACK
//                drawPoint(drainPoint, 3)
//                graphics.color = Color.RED
//                drawPoint(entry1, 3)
//                graphics.color = Color.BLUE
//                drawPoint(entry2, 3)
//                graphics.color = Color.MAGENTA
//                drawEdge(entry1, Point2F(entry1.x + entryVector1.a, entry1.y + entryVector1.b))
//                graphics.color = Color.CYAN
//                drawEdge(entry2, Point2F(entry2.x + entryVector2.a, entry2.y + entryVector2.b))
//                graphics.color = Color.ORANGE
//                drawPoint(sp3.p, 3)
//                graphics.color = Color.GREEN
//                drawSpline(Spline(mutableListOf(sp1, sp2), false))
//            }
//        }
        sp1.cp2 = entry1 + (entryVector1 * (dist1 * 0.3f))
        sp2.cp1 = entry2
        sp2.cp2 = entry2 + (entryVector2 * (dist2 * 0.3f))
        val junction = sp3.p

        val avgEntryElevation = (elevation1 + elevation2) * 0.5f
        val dist1ToJ = entry1.distance(junction)
        val dist2ToJ = entry2.distance(junction)
        val avgDistToJ = (dist1ToJ + dist2ToJ) * 0.5f
        val distDToJ = drainPoint.distance(junction)
        val totalRun = avgDistToJ + distDToJ
        val jOffset = distDToJ / totalRun
        val elevationDiff = avgEntryElevation - drainElevation
        val desiredJunctionElevation = drainElevation + (elevationDiff * jOffset)
        val junctionElevation = if (desiredJunctionElevation >= elevation1 || desiredJunctionElevation >= elevation2) {
            drainElevation + ((Math.min(elevation1, elevation2) - drainElevation) * jOffset)
        } else {
            desiredJunctionElevation
        }
        val normalizedFlowVector = (Vector2F(entry1, drainPoint) + Vector2F(entry2, drainPoint) * (totalDist * 0.5f)).getUnit()
        val outputVector = ((entryVector1 * dist1.toFloat()) + (entryVector2 * dist2.toFloat()) + normalizedFlowVector).getUnit()
        sp3.cp1 = junction + (-outputVector * (totalDist * 0.15f))
        val ret = Triple(Junction(vertexId, junction, totalFlow, outputVector, junctionElevation), Spline2F(mutableListOf(sp1, sp3), false), Spline2F(mutableListOf(sp2, sp3), false))
//        if (draw) {
//            draw(4096, "testing") {
//                graphics.color = Color.BLACK
//                drawPoint(drainPoint, 3)
//                graphics.color = Color.RED
//                drawPoint(entry1, 3)
//                graphics.color = Color.BLUE
//                drawPoint(entry2, 3)
//                graphics.color = Color.MAGENTA
//                drawEdge(entry1, Point2F(entry1.x + entryVector1.x, entry1.y + entryVector1.y))
//                graphics.color = Color.CYAN
//                drawEdge(entry2, Point2F(entry2.x + entryVector2.x, entry2.y + entryVector2.y))
//                graphics.color = Color.ORANGE
//                drawPoint(junction, 3)
//                graphics.color = Color.PINK
//                drawEdge(junction, Point2F(junction.x + junctionOutput.x, junction.y + junctionOutput.y))
//                graphics.color = Color.GREEN
//                drawSpline(ret.second.first)
//                drawSpline(ret.second.second)
//            }
//        }
        return ret
    }

    private fun calculateRiverFlows(vertices: Vertices, coastline: Polygon2F, rivers: ArrayList<TreeNode<RiverNode>>, simulationSizeM2: Float, landPercent: Float): FloatArray {
        val standardArea = (simulationSizeM2 * landPercent) / vertices.size.toFloat()
        val flows = FloatArray(vertices.size)
        rivers.forEach {
            calculateRiverFlow(vertices, coastline, it, simulationSizeM2, standardArea, flows)
        }
        return flows
    }

    private fun calculateRiverFlow(vertices: Vertices, coastline: Polygon2F, river: TreeNode<RiverNode>, simulationSizeM2: Float, standardArea: Float, flows: FloatArray): Double {
        var shedArea = 0.0
        river.children.forEach {
            shedArea += calculateRiverFlow(vertices, coastline, it, simulationSizeM2, standardArea, flows)
        }
        val cell = vertices[river.value.pointIndex].cell
        if (cell.area != 0.0f && !cell.isBorder && !Polygon2F(cell.border, true).doesEdgeIntersect(coastline)) {
            shedArea += cell.area * simulationSizeM2
        } else {
            shedArea += standardArea
        }
        flows[river.value.pointIndex] = (0.42 * Math.pow(shedArea, 0.69)).toFloat()
        return shedArea
    }

    class RiverProfile(val spacing: Range2F, val strength: Range2F, val deviation: Range2F)

    private fun calculateRiverType(flow: Float, slope: Float, elevations: Vector2F): RiverProfile {
        val elevation = (elevations.a + elevations.b) * 0.5f
        val highElevation = elevation > 0.0085f
        val highFlow = flow > 14500.0f
        val highSlope = slope > 0.124f
        if (highElevation) {
            if (highSlope) {
                if (highFlow) {
                    return RiverProfile(Range2F(0.0035f, 0.0045f), Range2F(1.0f, 1.2f), Range2F(0.1f, 0.2f))
                } else {
                    return RiverProfile(Range2F(0.003f, 0.0035f), Range2F(1.0f, 1.4f), Range2F(0.1f, 0.45f))
                }
            } else {
                if (highFlow) {
                    return RiverProfile(Range2F(0.0035f, 0.0045f), Range2F(1.0f, 1.2f), Range2F(0.1f, 0.2f))
                } else {
                    return RiverProfile(Range2F(0.0025f, 0.0035f), Range2F(1.0f, 2.5f), Range2F(0.1f, 0.8f))
                }
            }
        } else {
            if (highSlope) {
                if (highFlow) {
                    return RiverProfile(Range2F(0.0045f, 0.005f), Range2F(1.0f, 1.2f), Range2F(0.1f, 0.2f))
                } else {
                    return RiverProfile(Range2F(0.004f, 0.0045f), Range2F(1.0f, 1.3f), Range2F(0.1f, 0.3f))
                }
            } else {
                if (highFlow) {
                    return RiverProfile(Range2F(0.0055f, 0.0065f), Range2F(1.0f, 1.1f), Range2F(0.1f, 0.3f))
                } else {
                    return RiverProfile(Range2F(0.004f, 0.005f), Range2F(1.0f, 2.5f), Range2F(0.1f, 0.95f))
                }
            }
        }
    }
}
