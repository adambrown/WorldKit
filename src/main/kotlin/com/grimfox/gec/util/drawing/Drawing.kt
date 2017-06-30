package com.grimfox.gec.util.drawing

import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.model.geometry.Point2I
import com.grimfox.gec.model.geometry.Vector2F
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class Image(val multiplier: Float, val shift: Vector2F, val graphics: Graphics2D)

fun draw(outputWidth: Int, name: String, outputPath: String = "output", background: Color = Color.WHITE, points: Collection<Point2F>, drawCalls: Image.() -> Unit) {
    val xVals = points.map { it.x }
    val yVals = points.map { it.y }
    val xMin = xVals.min()!!
    val xMax = xVals.max()!!
    val yMin = yVals.min()!!
    val yMax = yVals.max()!!
    val xDelta = xMax - xMin
    val yDelta = yMax - yMin
    val delta = Math.max(xDelta, yDelta)
    val zoom = 0.98f / delta
    val shift = Vector2F(-(xMin) + (0.01f / zoom), -(yMin) + (0.01f / zoom))
    draw(outputWidth, name, outputPath, background, zoom, shift, drawCalls)
}

fun draw(outputWidth: Int, name: String, outputPath: String = "output", background: Color = Color.WHITE, zoom: Float? = null, shift: Vector2F = Vector2F(0.0f, 0.0f), drawCalls: Image.() -> Unit) {
    val multiplier = if (zoom == null) { outputWidth.toFloat() } else { outputWidth.toFloat() * zoom }
    val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
    val graphics = image.createGraphics()
    graphics.background = background
    graphics.clearRect(0, 0, outputWidth, outputWidth)

    drawCalls(Image(multiplier, shift, graphics))

    ImageIO.write(image, "png", File("$outputPath/$name.png"))
}

fun Image.drawPoint(point: Point2F, radius: Int) {
    val diameter = radius * 2 + 1
    val ip = interpolateInt(point)
    graphics.fillOval(ip.x - radius, ip.y - radius, diameter, diameter)
}

fun Image.interpolateInt(point: Point2F): Point2I {
    return Point2I(Math.round((point.x + shift.a) * multiplier), Math.round((point.y + shift.b) * multiplier))
}

fun Image.drawEdge(p1: Point2F, p2: Point2F) {
    val ip1 = interpolateInt(p1)
    val ip2 = interpolateInt(p2)
    graphics.drawLine(ip1.x, ip1.y, ip2.x, ip2.y)
}

