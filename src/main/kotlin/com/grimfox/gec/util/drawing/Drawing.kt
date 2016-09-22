package com.grimfox.gec.util.drawing

import com.grimfox.gec.model.*
import com.grimfox.gec.model.Graph.*
import com.grimfox.gec.model.Point
import com.grimfox.gec.model.Polygon
import com.grimfox.gec.util.Rivers.RiverNode
import java.awt.*
import java.awt.geom.CubicCurve2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

class Image(val multiplier: Float, val graphics: Graphics2D)

fun draw(outputWidth: Int, name: String, background: Color = Color.WHITE, drawCalls: Image.() -> Unit) {
    val multiplier = outputWidth.toFloat()
    val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
    val graphics = image.createGraphics()
    graphics.background = background
    graphics.clearRect(0, 0, outputWidth, outputWidth)

    drawCalls(Image(multiplier, graphics))

    ImageIO.write(image, "png", File("output/$name.png"))
}

fun Image.drawVertex(vertex: Vertex, radius: Int) {
    drawPoint(vertex.point, radius)
}

fun Image.drawPoint(point: Point, radius: Int) {
    val diameter = radius * 2 + 1
    graphics.fillOval(Math.round(point.x * multiplier) - radius, Math.round(point.y * multiplier) - radius, diameter, diameter)
}

fun Image.drawEdge(p1: Point, p2: Point) {
    val p1x = Math.round(p1.x * multiplier)
    val p1y = Math.round(p1.y * multiplier)
    val p2x = Math.round(p2.x * multiplier)
    val p2y = Math.round(p2.y * multiplier)
    graphics.drawLine(p1x, p1y, p2x, p2y)
}

fun Image.drawTriangle(triangle: Triangle, drawPoints: Boolean = true) {
    drawPolygon(Polygon(listOf(triangle.a.point, triangle.b.point, triangle.c.point), true), drawPoints)
}

fun Image.drawPolygon(polygon: Polygon, drawPoints: Boolean = true) {
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
    if (cell.isClosed) {
        val xVals = cell.border.map { Math.round(it.x * multiplier) }.toIntArray()
        val yVals = cell.border.map { Math.round(it.y * multiplier) }.toIntArray()
        graphics.fillPolygon(xVals, yVals, xVals.size)
    }
}

fun Image.drawGraph(graph: Graph, vararg features: Any) {
    graphics.color = Color.RED

    graph.triangles.forEach {
        drawTriangle(it)
    }

    graphics.color = Color.BLACK

    graph.vertices.forEach { vertex ->
        (vertex.cell.borderEdges.map { Pair(it.tri1.center, it.tri2.center) }).forEach {
            drawEdge(it.first, it.second)
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
        } else if (it is Point) {
            graphics.color = Color.CYAN
            drawPoint(it, 4)
        } else if (it is Triangle) {
            graphics.color = Color.GREEN
            drawTriangle(it)
        } else if (it is Cell) {
            graphics.color = Color.ORANGE
            drawCell(it)
        }
    }
}

fun Image.drawVertexIds(graph: Graph) {
    graphics.font = Font("Courier", Font.BOLD, 12)
    graph.vertices.forEach { vertex ->
        graphics.drawString(vertex.id.toString(), vertex.point.x * multiplier,  vertex.point.y * multiplier)
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

fun Image.drawRivers(graph: Graph, mask: Matrix<Int>, rivers: Collection<RiverNode>, coastline: Collection<Spline>, borders: Collection<Polygon>) {
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

    rivers.forEach {
        drawRiver(it, false)
    }

    graphics.color = Color(160, 0, 0)
    graphics.stroke = BasicStroke(3.0f)

    borders.forEach {
        drawPolygon(it, false)
    }
}

fun Image.drawRiver(riverNode: RiverNode, drawPoints: Boolean = true) {
    val p1 = riverNode.pointLocation
    riverNode.children.forEach {
        drawEdge(p1, it.pointLocation)
        drawRiver(it, drawPoints)
    }
    if (drawPoints) {
        drawPoint(p1, 2)
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

fun Image.drawBorders(graph: Graph, mask: Matrix<Int>, borders: Pair<List<Polygon>, List<Polygon>>) {
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

fun Image.drawOffLimits(graph: Graph, mask: Matrix<Int>, offLimits: HashSet<Int>) {
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

fun Image.drawEdges(edges: ArrayList<Pair<Point, Point>>) {
    edges.forEach {
        drawEdge(it.first, it.second)
    }
}

fun Image.fillSpline(spline: Spline) {
    val path = Path2D.Float()
    val points = spline.points
    for (i in 1..points.size - if (spline.isClosed) 0 else 1) {
        val splinePoint1 = points[i - 1]
        val splinePoint2 = points[i % points.size]
        path.append(CubicCurve2D.Float(splinePoint1.point.x * multiplier, splinePoint1.point.y * multiplier, splinePoint1.cp2.x * multiplier, splinePoint1.cp2.y * multiplier, splinePoint2.cp1.x * multiplier, splinePoint2.cp1.y * multiplier, splinePoint2.point.x * multiplier, splinePoint2.point.y * multiplier), true)
    }
    graphics.fill(path)
}

fun Image.drawSpline(spline: Spline, drawPoints: Boolean = true, drawControlPoints: Boolean = drawPoints) {
    val points = spline.points
    for (i in 1..points.size - if (spline.isClosed) 0 else 1) {
        drawCurve(points[i - 1], points[i % points.size], drawPoints, drawControlPoints)
    }
}

fun Image.drawCurve(splinePoint1: SplinePoint, splinePoint2: SplinePoint, drawPoints: Boolean = true, drawControlPoints: Boolean = drawPoints) {
    graphics.draw(CubicCurve2D.Float(splinePoint1.point.x * multiplier, splinePoint1.point.y * multiplier, splinePoint1.cp2.x * multiplier, splinePoint1.cp2.y * multiplier, splinePoint2.cp1.x * multiplier, splinePoint2.cp1.y * multiplier, splinePoint2.point.x * multiplier, splinePoint2.point.y * multiplier))
    if (drawPoints) {
        drawPoint(splinePoint1.point, 2)
        drawPoint(splinePoint2.point, 2)
    }
    if (drawControlPoints) {
        drawPoint(splinePoint1.cp2, 2)
        drawPoint(splinePoint2.cp1, 2)
        drawEdge(splinePoint1.point, splinePoint1.cp2)
        drawEdge(splinePoint2.cp1, splinePoint2.point)
    }
}