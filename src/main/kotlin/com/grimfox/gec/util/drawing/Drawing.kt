package com.grimfox.gec.util.drawing

import com.grimfox.gec.command.BuildContinent
import com.grimfox.gec.command.BuildContinent.RiverSegment
import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.Rivers
import com.grimfox.gec.util.Rivers.RiverNode
import java.awt.*
import java.awt.geom.CubicCurve2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import javax.sound.sampled.Line

class Image(val multiplier: Float, val shift: Vector2F, val graphics: Graphics2D)

fun draw(outputWidth: Int, name: String, background: Color = Color.WHITE, zoom: Float? = null, shift: Vector2F = Vector2F(0.0f, 0.0f), drawCalls: Image.() -> Unit) {
    val multiplier = if (zoom == null) { outputWidth.toFloat() } else { outputWidth.toFloat() * zoom }
    val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
    val graphics = image.createGraphics()
    graphics.background = background
    graphics.clearRect(0, 0, outputWidth, outputWidth)

    drawCalls(Image(multiplier, shift, graphics))

    ImageIO.write(image, "png", File("output/$name.png"))
}

fun Image.drawVertex(vertex: Vertex, radius: Int) {
    drawPoint(vertex.point, radius)
}

fun Image.drawPoint(point: Point2F, radius: Int) {
    val diameter = radius * 2 + 1
    val ip = interpolateInt(point)
    graphics.fillOval(ip.x - radius, ip.y - radius, diameter, diameter)
}

fun Image.interpolateInt(point: Point2F): Point2I {
    return Point2I(Math.round((point.x + shift.a) * multiplier), Math.round((point.y + shift.b) * multiplier))
}

fun Image.interpolateFloat(point: Point2F): Point2F {
    return Point2F((point.x + shift.a) * multiplier, (point.y + shift.b) * multiplier)
}

fun Image.drawEdge(p1: Point2F, p2: Point2F) {
    val ip1 = interpolateInt(p1)
    val ip2 = interpolateInt(p2)
    graphics.drawLine(ip1.x, ip1.y, ip2.x, ip2.y)
}

fun Image.drawEdgeWithGradient(p1: Point2F, p2: Point2F, c1: Color, c2: Color) {
    val ip1 = interpolateInt(p1)
    val ip2 = interpolateInt(p2)
    val prePaint = graphics.paint
    graphics.paint = GradientPaint(ip1.x.toFloat(), ip1.y.toFloat(), c1, ip2.x.toFloat(), ip2.y.toFloat(), c2)
    graphics.drawLine(ip1.x, ip1.y, ip2.x, ip2.y)
    graphics.paint = prePaint
}

fun Image.drawTriangle(triangle: Triangle, drawPoints: Boolean = true) {
    drawPolygon(Polygon2F(listOf(triangle.a.point, triangle.b.point, triangle.c.point), true), drawPoints)
}

fun Image.drawPolygon(polygon: Polygon2F, drawPoints: Boolean = true) {
    val points = polygon.points
    for (i in 1..points.size - if (polygon.isClosed) 0 else 1) {
        drawEdge(points[i - 1], points[i % points.size])
    }
    if (drawPoints) {
        points.forEach {
            drawPoint(it, 2)
        }
    }
}

fun Image.drawCell(cell: Cell) {
    val interpolated = cell.border.map { interpolateInt(it) }
    val xVals = interpolated.map { it.x }.toIntArray()
    val yVals = interpolated.map { it.y }.toIntArray()
    graphics.fillPolygon(xVals, yVals, xVals.size)
}

fun Image.drawGraph(graph: Graph, vararg features: Any) {
    graphics.color = Color.RED

    graph.triangles.forEach {
        drawTriangle(it)
    }

    graphics.color = Color.BLACK

    graph.vertices.forEach { vertex ->
        vertex.cell.borderEdges.forEach {
            drawEdge(it.a, it.b)
        }
    }

    graphics.color = Color.BLUE

    graph.vertices.forEach {
        drawVertex(it, 2)
    }

    features.forEach {
        if (it is Vertex) {
            graphics.color = Color.MAGENTA
            drawVertex(it, 4)
        } else if (it is Point2F) {
            graphics.color = Color.CYAN
            drawPoint(it, 4)
        } else if (it is Triangle) {
            graphics.color = Color.GREEN
            drawTriangle(it)
        } else if (it is Cell) {
            graphics.color = Color.ORANGE
            drawCell(it)
        } else if (it is LineSegment2F) {
            graphics.color = Color.GREEN
            drawEdge(it.a, it.b)
        }
    }
}

fun Image.drawVertexIds(graph: Graph) {
    graphics.font = Font("Courier", Font.BOLD, 12)
    graph.vertices.forEach { vertex ->
        val ip = interpolateFloat(vertex.point)
        graphics.drawString(vertex.id.toString(), ip.x,  ip.y)
    }
}

fun Image.drawSlopes(graph: Graph, slopes: Map<Int, Float>) {
    val minSlope = slopes.values.min()!!
    val maxSlope = slopes.values.max()!!
    val delta = maxSlope - minSlope

    graph.vertices.forEach {
        val slope = slopes[it.id]
        if (slope != null) {
            val cell = it.cell
            val adjustedSlope = (slope - minSlope) * delta
            graphics.color = Color(adjustedSlope, adjustedSlope, adjustedSlope)
            drawCell(cell)
        }
    }
}

fun Image.drawRivers(graph: Graph, mask: Matrix<Int>, rivers: Collection<TreeNode<RiverNode>>, coastline: Collection<Spline2F>, borders: Collection<Polygon2F>) {
    graphics.stroke = BasicStroke(1.5f)

    graphics.color = Color(180, 255, 160)

    coastline.forEach {
        fillSpline(it)
    }

    graphics.color = Color(0, 0, 0)
    graphics.stroke = BasicStroke(1.3f)

    coastline.forEach {
        drawSpline(it, false)
    }

    graphics.color = Color(0, 20, 170)
    graphics.stroke = BasicStroke(1.5f)

    drawRiverElevations(rivers, false)

    graphics.color = Color(160, 0, 0)
    graphics.stroke = BasicStroke(3.0f)

    borders.forEach {
        drawPolygon(it, false)
    }
}

fun Image.drawRiver(riverNode: TreeNode<RiverNode>, drawPoints: Boolean = true) {
    val p1 = riverNode.value.pointLocation
    riverNode.children.forEach {
        drawEdge(p1, it.value.pointLocation)
        drawRiver(it, drawPoints)
    }
    if (drawPoints) {
        drawPoint(p1, 2)
    }
}

fun Image.drawRiverElevations(rivers: Collection<TreeNode<RiverNode>>, drawPoints: Boolean = true) {
    fun maxElevation(river: TreeNode<RiverNode>): Float {
        return Math.max(river.value.elevation, river.children.map { maxElevation(it) }.max() ?: Float.MIN_VALUE)
    }
    fun minElevation(river: TreeNode<RiverNode>): Float {
        return Math.min(river.value.elevation, river.children.map { minElevation(it) }.min() ?: Float.MAX_VALUE)
    }
    fun drawRiverElevations(riverNode: TreeNode<RiverNode>, minElevation: Float, delta: Float, drawPoints: Boolean) {
        val normalizedElevation = (riverNode.value.elevation - minElevation) / delta
        val color1 = Color(normalizedElevation, 0.0f, 1.0f - normalizedElevation)
        val p1 = riverNode.value.pointLocation
        riverNode.children.forEach {
            val childElevation = (it.value.elevation - minElevation) / delta
            val color2 = Color(childElevation, 0.0f, 1.0f - childElevation)
            drawEdgeWithGradient(p1, it.value.pointLocation, color1, color2)
            drawRiverElevations(it, minElevation, delta, drawPoints)
        }
        if (drawPoints) {
            drawPoint(p1, 2)
        }
    }
    val maxElevation = rivers.map { maxElevation(it) }.max() ?: 1.0f
    val minElevation = rivers.map { minElevation(it) }.min() ?: 0.0f
    val delta = maxElevation - minElevation
    rivers.forEach {
        drawRiverElevations(it, minElevation, delta, drawPoints)
    }
}

fun Image.drawBorder(graph: Graph) {
    graph.vertices.forEach {
        val cell = it.cell
        if (!cell.isBorder) {
            drawCell(cell)
        }
    }
}

fun Image.drawMask(graph: Graph, mask: Set<Int>, positive: Boolean = true) {
    graph.vertices.forEach {
        val cell = it.cell
        if ((positive && mask.contains(cell.id)) || (!positive && !mask.contains(cell.id))) {
            drawCell(cell)
        }
    }
}

fun Image.drawRegions(graph: Graph, mask: Matrix<Int>, vararg regionColors: Color) {
    graph.vertices.forEach {
        val cell = it.cell
        val regionId = mask[it.id]
        if (regionId != 0) {
            graphics.color = regionColors[regionId]
            drawCell(cell)
        }
    }
}

fun Image.drawBorders(graph: Graph, mask: Matrix<Int>, borders: Pair<List<Polygon2F>, List<Polygon2F>>) {
    graphics.color = Color.WHITE

    graph.vertices.forEach {
        val cell = it.cell
        val regionId = mask[it.id]
        if (regionId != 0) {
            drawCell(cell)
        }
    }

    graphics.color = Color.RED

    borders.first.forEach {
        drawPolygon(it, false)
    }

    graphics.color = Color.BLUE

    borders.second.forEach {
        drawPolygon(it, false)
    }
}

fun Image.drawOffLimits(graph: Graph, mask: Matrix<Int>, offLimits: LinkedHashSet<Int>) {
    graphics.color = Color.WHITE

    graph.vertices.forEach {
        val cell = it.cell
        val regionId = mask[it.id]
        if (regionId != 0) {
            drawCell(cell)
        }
    }

    graphics.color = Color.RED

    offLimits.forEach {
        drawCell(graph.vertices[it].cell)
    }
}

fun Image.drawConcavity(graph: Graph, mask: HashMap<Int, Float>) {
    graphics.color = Color.WHITE

    var minConcavity = Float.MAX_VALUE
    var maxConcavity = Float.MIN_VALUE
    graph.vertices.forEach {
        val concavity = mask[it.id]
        if (concavity != null) {
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
        if (concavity != null) {
            val adjusted = (concavity - minConcavity) / concavityRange
            graphics.color = Color(adjusted, 1.0f - adjusted, 0.0f)
            drawCell(cell)
        }
    }
}

fun Image.drawEdges(edges: ArrayList<Pair<Point2F, Point2F>>) {
    edges.forEach {
        drawEdge(it.first, it.second)
    }
}

fun Image.fillSpline(spline: Spline2F) {
    val path = Path2D.Float()
    val points = spline.points
    for (i in 1..points.size - if (spline.isClosed) 0 else 1) {
        val splinePoint1 = points[i - 1]
        val splinePoint2 = points[i % points.size]
        val p1 = interpolateFloat(splinePoint1.p)
        val p2 = interpolateFloat(splinePoint1.cp2)
        val p3 = interpolateFloat(splinePoint2.cp1)
        val p4 = interpolateFloat(splinePoint2.p)
        path.append(CubicCurve2D.Float(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y), true)
    }
    graphics.fill(path)
}

fun Image.drawRiverTree(tree: TreeNode<RiverSegment>, drawPoints: Boolean = true, drawControlPoints: Boolean = drawPoints) {
    drawSpline(tree.value.spline, drawPoints, drawControlPoints)
    tree.children.forEach {
        drawRiverTree(it, drawPoints, drawControlPoints)
    }
}

fun Image.drawRiverPolyLines(trees: Collection<TreeNode<RiverSegment>>, desiredSegmentLength: Float, iterations: Int, drawPoints: Boolean = true) {
    trees.forEach {
        it.forEach {
            drawPolygon(it.spline.toPolygon(desiredSegmentLength, iterations), drawPoints)
        }
    }
}


fun Image.drawRiverElevations(trees: Collection<TreeNode<RiverSegment>>, drawPoints: Boolean = true, drawControlPoints: Boolean = drawPoints, drawControlLines: Boolean = drawControlPoints) {
    val maxElevation = trees.map { maxElevation(it) }.max() ?: 1.0f
    val minElevation = trees.map { minElevation(it) }.min() ?: 0.0f
    val delta = maxElevation - minElevation
    trees.forEach {
        drawRiverElevations(it, minElevation, delta, drawPoints, drawControlPoints, drawControlLines)
    }
}

private fun Image.drawRiverElevations(tree: TreeNode<RiverSegment>, minElevation: Float, delta: Float, drawPoints: Boolean = true, drawControlPoints: Boolean = drawPoints, drawControlLines: Boolean = drawControlPoints, controlColor: Color? = null, pointSize: Int = 2) {
    val normalizedElevation = (((tree.value.elevations.a + tree.value.elevations.b) * 0.5f) - minElevation) / delta
    graphics.color = Color(normalizedElevation, 0.0f, 1.0f - normalizedElevation)
    drawSpline(tree.value.spline, drawPoints, drawControlPoints, drawControlLines, controlColor, pointSize)
    tree.children.forEach {
        drawRiverElevations(it, minElevation, delta, drawPoints, drawControlPoints, drawControlLines, controlColor, pointSize)
    }
}

private fun maxElevation(river: TreeNode<RiverSegment>): Float {
    return Math.max(river.value.elevations.b, river.children.map { maxElevation(it) }.max() ?: Float.MIN_VALUE)
}

private fun minElevation(river: TreeNode<RiverSegment>): Float {
    return Math.min(river.value.elevations.a, river.children.map { minElevation(it) }.min() ?: Float.MAX_VALUE)
}

fun Image.drawSpline(spline: Spline2F, drawPoints: Boolean = true, drawControlPoints: Boolean = drawPoints, drawControlLines: Boolean = drawControlPoints, controlColor: Color? = null, pointSize: Int = 2) {
    val points = spline.points
    for (i in 1..points.size - if (spline.isClosed) 0 else 1) {
        drawCurve(points[i - 1], points[i % points.size], drawPoints, drawControlPoints, drawControlLines, controlColor, pointSize)
    }
}

fun Image.drawCurve(splinePoint1: SplinePoint2F, splinePoint2: SplinePoint2F, drawPoints: Boolean = true, drawControlPoints: Boolean = drawPoints, drawControlLines: Boolean = drawControlPoints, controlColor: Color? = null, pointSize: Int = 2) {
    val p1 = interpolateFloat(splinePoint1.p)
    val p2 = interpolateFloat(splinePoint1.cp2)
    val p3 = interpolateFloat(splinePoint2.cp1)
    val p4 = interpolateFloat(splinePoint2.p)
    graphics.draw(CubicCurve2D.Float(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y))
    if (drawPoints) {
        drawPoint(splinePoint1.p, pointSize)
        drawPoint(splinePoint2.p, pointSize)
    }
    val color = graphics.color
    if (controlColor != null) {
        graphics.color = controlColor
    }
    if (drawControlPoints) {
        drawPoint(splinePoint1.cp2, pointSize)
        drawPoint(splinePoint2.cp1, pointSize)
    }
    if (drawControlLines) {
        drawEdge(splinePoint1.p, splinePoint1.cp2)
        drawEdge(splinePoint2.cp1, splinePoint2.p)
    }
    graphics.color = color
}