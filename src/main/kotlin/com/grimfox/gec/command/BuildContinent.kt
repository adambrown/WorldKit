package com.grimfox.gec.command

import com.grimfox.gec.Main
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.util.Coastline.refineCoastline
import com.grimfox.gec.util.Mask.applyMask
import com.grimfox.gec.util.Regions.buildRegions
import com.grimfox.gec.util.Triangulate.buildGraph
import com.grimfox.gec.util.Utils.generatePoints
import io.airlift.airline.Command
import io.airlift.airline.Option
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

@Command(name = "build-continent", description = "Builds a continent.")
class BuildContinent() : Runnable {

    @Option(name = arrayOf("-r", "--random"), description = "The random seed to use.", required = false)
    var randomSeed: Long = System.currentTimeMillis()

    @Option(name = arrayOf("-s", "--strides"), description = "The number of points in the stride of each iteration.", required = false)
    var strides: ArrayList<Int> = ArrayList()

    @Option(name = arrayOf("-f", "--file"), description = "The data file to write as output.", required = true)
    var outputFile: File = File(Main.workingDir, "output.bin")

    override fun run() {
        if (strides.isEmpty()) {
            strides.addAll(listOf(7, 40, 80, 140, 256))
        } else {
            strides.sort()
        }
        for (test in 1..10000) {
            val virtualWidth = 100000.0f
            val outputWidth = 256
            val random = Random(test.toLong())
            var lastGraph: Graph? = null
            var regionMask: Matrix<Int> = ArrayListMatrix(0)
            strides.forEachIndexed { i, stride ->
                val localLastGraph = lastGraph
                val graph: Graph
                if (localLastGraph == null) {
                    val (g, m) = buildRegions(stride, random, 8)
                    graph = g
                    regionMask = m
                } else {
                    val points = generatePoints(stride, virtualWidth, random)
                    graph = buildGraph(stride, virtualWidth, points)
                    regionMask = applyMask(graph, localLastGraph, regionMask)
                    refineCoastline(graph, random, regionMask, landPercent = 0.4f, minPerturbation = 0.17f - (i * 0.04f), maxIterations = 5 - i, largeIsland = 2.0f, smallIsland = 0.02f)
                }
                lastGraph = graph
//                drawGraph(graph, outputWidth, "test-graph-${String.format("%05d", i)}")
//                drawBorder(graph, outputWidth, "test-border-${String.format("%05d", i)}")
//                drawRegions(graph, regionMask, outputWidth, "test-new-${String.format("%05d", test)}-r$i", Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.DARK_GRAY)
            }
//            drawMask(lastGraph, spokes, outputWidth, "test-new-${String.format("%05d", test)}-rs")
//            drawSpokes(spokes, outputWidth, "test-new-${String.format("%05d", test)}-m")
//            drawMask(lastGraph, lastMask, outputWidth, "test-new-${String.format("%05d", test)}-m", false)
            drawRegions(lastGraph!!, regionMask, outputWidth, "test-new-${String.format("%05d", test)}-r", Color.BLACK, Color.BLUE, Color.GREEN, Color.RED, Color.MAGENTA, Color.CYAN, Color.ORANGE, Color.PINK, Color.YELLOW, Color.WHITE, Color.DARK_GRAY)
//            drawConcavity(lastGraph, concavityWeights, outputWidth, "test-new-${String.format("%05d", test)}-c")
        }
    }

    /*-------------------------------------------------------------------------------------------------------------





    =============================================== Draw Functions ================================================
                                                             |
                                                             |
                                                             |
                                                            \|/
                                                             '
    -------------------------------------------------------------------------------------------------------------*/

    private fun drawGraph(graph: Graph, outputWidth: Int, name: String, vararg features: Any) {
        val multiplier = outputWidth.toFloat()
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.WHITE
        graphics.clearRect(0, 0, outputWidth, outputWidth)
        graphics.color = Color.RED

        graph.triangles.forEach {
            drawTriangle(graphics, multiplier, it)
        }

        graphics.color = Color.BLACK

        graph.triangles.forEach { mid ->
            val center = mid.center
            mid.adjacentTriangles.forEach {
                drawEdge(graphics, multiplier, center, it.center)
            }
        }

        graphics.color = Color.BLUE

        graph.vertices.forEach {
            drawVertex(graphics, multiplier, it, 2)
        }

        features.forEach {
            if (it is Vertex) {
                graphics.color = Color.MAGENTA
                drawVertex(graphics, multiplier, it, 4)
            } else if (it is Point) {
                graphics.color = Color.CYAN
                drawPoint(graphics, multiplier, it, 4)
            } else if (it is Triangle) {
                graphics.color = Color.GREEN
                drawTriangle(graphics, multiplier, it)
            } else if (it is Cell) {
                graphics.color = Color.ORANGE
                drawCell(graphics, multiplier, it)
            }
        }

        ImageIO.write(image, "png", File("output/$name.png"))
    }

    private fun drawBorder(graph: Graph, outputWidth: Int, name: String) {
        val multiplier = outputWidth.toFloat()
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.BLACK
        graphics.clearRect(0, 0, outputWidth, outputWidth)
        graphics.color = Color.WHITE

        graph.vertices.forEach {
            val cell = it.cell
            if (!cell.isBorder) {
                drawCell(graphics, multiplier, cell)
            }
        }

        ImageIO.write(image, "png", File("output/$name.png"))
    }

    private fun drawMask(graph: Graph, mask: Set<Int>, outputWidth: Int, name: String, positive: Boolean = true) {
        val multiplier = outputWidth.toFloat()
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.BLACK
        graphics.clearRect(0, 0, outputWidth, outputWidth)
        graphics.color = Color.WHITE

        graph.vertices.forEach {
            val cell = it.cell
            if ((positive && mask.contains(cell.id)) || (!positive && !mask.contains(cell.id))) {
                drawCell(graphics, multiplier, cell)
            }
        }

        ImageIO.write(image, "png", File("output/$name.png"))
    }

    private fun drawRegions(graph: Graph, mask: Matrix<Int>, outputWidth: Int, name: String, vararg regionColors: Color) {
        val multiplier = outputWidth.toFloat()
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.BLACK
        graphics.clearRect(0, 0, outputWidth, outputWidth)
        graphics.color = Color.WHITE

        graph.vertices.forEach {
            val cell = it.cell
            val regionId = mask[it.id]
            if (regionId != 0) {
                graphics.color = regionColors[regionId]
                drawCell(graphics, multiplier, cell)
            }
        }

        ImageIO.write(image, "png", File("output/$name.png"))
    }

    private fun drawConcavity(graph: Graph, mask: Matrix<Float>, outputWidth: Int, name: String) {
        val multiplier = outputWidth.toFloat()
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.BLACK
        graphics.clearRect(0, 0, outputWidth, outputWidth)
        graphics.color = Color.WHITE

        var minConcavity = Float.MAX_VALUE
        var maxConcavity = Float.MIN_VALUE
        graph.vertices.forEach {
            val concavity = mask[it.id]
            if (concavity > 0.0f) {
                if (concavity < minConcavity) {
                    minConcavity = concavity
                }
                if (concavity > maxConcavity) {
                    maxConcavity = concavity
                }
            }
        }
        val concavityRange = maxConcavity - minConcavity

        graph.vertices.forEach {
            val cell = it.cell
            val concavity = mask[it.id]
            if (concavity > 0.0f) {
                val adjusted = (concavity - minConcavity) / concavityRange
                graphics.color = Color(adjusted, 1.0f - adjusted, 0.0f)
                drawCell(graphics, multiplier, cell)
            }
        }

        ImageIO.write(image, "png", File("output/$name.png"))
    }

    private fun drawSpokes(spokes: ArrayList<Pair<Point, Point>>, outputWidth: Int, name: String) {
        val multiplier = outputWidth.toFloat()
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.BLACK
        graphics.clearRect(0, 0, outputWidth, outputWidth)
        graphics.color = Color.WHITE

        spokes.forEach {
            drawEdge(graphics, multiplier, it.first, it.second)
        }

        ImageIO.write(image, "png", File("output/$name.png"))
    }


    private fun drawVertex(graphics: Graphics2D, multiplier: Float, vertex: Vertex, radius: Int) {
        drawPoint(graphics, multiplier, vertex.point, radius)
    }

    private fun drawPoint(graphics: Graphics2D, multiplier: Float, point: Point, radius: Int) {
        val diameter = radius * 2 + 1
        graphics.fillOval(Math.round(point.x * multiplier) - radius, Math.round(point.y * multiplier) - radius, diameter, diameter)
    }

    private fun drawTriangle(graphics: Graphics2D, multiplier: Float, triangle: Triangle) {
        val p1 = triangle.a.point
        val p2 = triangle.b.point
        val p3 = triangle.c.point
        val p1x = Math.round(p1.x * multiplier)
        val p1y = Math.round(p1.y * multiplier)
        val p2x = Math.round(p2.x * multiplier)
        val p2y = Math.round(p2.y * multiplier)
        val p3x = Math.round(p3.x * multiplier)
        val p3y = Math.round(p3.y * multiplier)
        graphics.drawLine(p1x, p1y, p2x, p2y)
        graphics.drawLine(p2x, p2y, p3x, p3y)
        graphics.drawLine(p3x, p3y, p1x, p1y)
    }

    private fun drawEdge(graphics: Graphics2D, multiplier: Float, p1: Point, p2: Point) {
        val p1x = Math.round(p1.x * multiplier)
        val p1y = Math.round(p1.y * multiplier)
        val p2x = Math.round(p2.x * multiplier)
        val p2y = Math.round(p2.y * multiplier)
        graphics.drawLine(p1x, p1y, p2x, p2y)
    }

    private fun drawCell(graphics: Graphics2D, multiplier: Float, cell: Cell) {
        if (cell.isClosed) {
            val xVals = cell.border.map { Math.round(it.x * multiplier) }.toIntArray()
            val yVals = cell.border.map { Math.round(it.y * multiplier) }.toIntArray()
            graphics.fillPolygon(xVals, yVals, xVals.size)
        }
    }
}
