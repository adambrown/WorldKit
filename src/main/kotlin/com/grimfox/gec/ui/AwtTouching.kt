package com.grimfox.gec.ui

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.util.clamp
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack.*
import java.awt.*
import java.util.ArrayList
import java.util.LinkedHashMap


internal class WindowsMouseFetcher : MouseFetcher {

    override fun getMousePosition(windowId: Long, windowX: Int, windowY: Int, mouseX: Int, mouseY: Int, relativeMouseX: Double, relativeMouseY: Double, warpLines: List<WarpLine>): Pair<Int, Int> {
        var returnX = mouseX
        var returnY = mouseY
        twr(stackPush()) { stack ->
            val lastMouseX = mouseX
            val lastMouseY = mouseY
            val pointerLocation = MouseInfo.getPointerInfo()?.location
            if (pointerLocation == null) {
                val x = stack.mallocDouble(1)
                val y = stack.mallocDouble(1)
                GLFW.glfwGetCursorPos(windowId, x, y)
                val newMouseX = Math.round(mouseX + (x[0] - relativeMouseX)).toInt()
                val newMouseY = Math.round(mouseY + (y[0] - relativeMouseY)).toInt()
                val (mouseWarpX, mouseWarpY) = getWarp(lastMouseX, lastMouseY, newMouseX, newMouseY, warpLines)
                returnX = newMouseX + mouseWarpX
                returnY = newMouseY + mouseWarpY
            } else {
                returnX = pointerLocation.x
                returnY = pointerLocation.y
            }
        }
        return Pair(returnX, returnY)
    }

    private fun getWarp(lastX: Int, lastY: Int, currentX: Int, currentY: Int, warpLines: List<WarpLine>): Pair<Int, Int> {
        var x = 0
        var y = 0
        for ((x1, y1, x2, y2, warpX, warpY) in warpLines) {
            if ((lastX <= x1 && currentX > x1) || (lastX >= x1 && currentX < x1) || (lastX <= x2 && currentX > x2) || (lastX >= x2 && currentX < x2)) {
                val interpolate = (x1 - lastX.toDouble()) / (currentX - lastX.toDouble())
                val yCrossing = lastY + ((currentY - lastY) * interpolate)
                if ((yCrossing <= y1 && yCrossing >= y2) || (yCrossing >= y1 && yCrossing <= y2)) {
                    x += Math.round(Math.signum(currentX.toDouble() - lastX) * warpX).toInt()
                    y += Math.round(Math.signum(currentY.toDouble() - lastY) * warpY).toInt()
                }
            }
        }
        return Pair(x, y)
    }
}

internal class WindowsScreenInfoFetcher : ScreenInfoFetcher {

    override fun getScreensAndWarpLines(): Pair<LinkedHashMap<ScreenIdentity, ScreenSpec>, List<WarpLine>> {
        val screens = LinkedHashMap<ScreenIdentity, ScreenSpec>()
        val warpLines = ArrayList<WarpLine>()
        val graphics = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val devices = ArrayList(graphics.screenDevices.toList())
        devices.sortBy { it.defaultConfiguration.bounds.x }
        var totalBounds = Rectangle()
        for (device in devices) {
            totalBounds = totalBounds.union(device.defaultConfiguration.bounds)
        }
        var lastX = 0
        var lastIdentity: ScreenIdentity?
        for (device in devices) {
            val currentMode = device.displayMode
            val graphicsConfiguration = device.defaultConfiguration
            val bounds = graphicsConfiguration.bounds
            val toolkit = Toolkit.getDefaultToolkit()
            val insets = toolkit.getScreenInsets(graphicsConfiguration)
            val x1 = bounds.x
            val y1 = bounds.y
            val width = bounds.width
            val height = bounds.height
            val x2 = x1 + width
            val y2 = y1 + height
            val maximizedX1 = x1 + insets.left
            val maximizedY1 = y1 + insets.top
            val maximizedWidth = width - insets.left - insets.right
            val maximizedHeight = height - insets.top - insets.bottom
            val maximizedX2 = maximizedX1 + maximizedWidth
            val maximizedY2 = maximizedY1 + maximizedHeight
            if (x1 > lastX || currentMode.width != width) {
                val scaleFactor = currentMode.width / width.toDouble()
                lastIdentity = ScreenIdentity(lastX, Math.round(y1 * scaleFactor).toInt(), currentMode.width, currentMode.height)
                screens.put(lastIdentity, ScreenSpec(x1, y1, x2, y2, width, height, currentMode.width, currentMode.height,
                        maximizedX1, maximizedY1, maximizedX2, maximizedY2, maximizedWidth, maximizedHeight, scaleFactor, width.toDouble() / currentMode.width))
                if (x1 > lastX) {
                    warpLines.add(WarpLine(lastX, y1, x1, y1 + height, Math.abs(x1 - lastX), 0))
                }
                lastX += currentMode.width
            } else {
                lastIdentity = ScreenIdentity(x1, y1, currentMode.width, currentMode.height)
                screens.put(lastIdentity, ScreenSpec(x1, y1, x2, y2, currentMode.width, currentMode.height, currentMode.width, currentMode.height,
                        maximizedX1, maximizedY1, maximizedX2, maximizedY2, maximizedWidth, maximizedHeight, 1.0, 1.0))
                lastX = x2
            }
        }
        var minScaleFactor = 1.0
        screens.forEach { _, screenSpec ->
            if (screenSpec.scaleFactor < minScaleFactor) {
                minScaleFactor = screenSpec.scaleFactor
            }
        }
        if (minScaleFactor < 1.0) {
            val correctionFactor = 1.0 / minScaleFactor
            screens.forEach { _, screenSpec ->
                screenSpec.scaleFactor *= correctionFactor
                screenSpec.scaleFactor = clamp(Math.round((Math.round(screenSpec.scaleFactor * 4.0) / 4.0) * 100.0) / 100.0, 1.0, 2.5)
            }
        }
        return Pair(screens, warpLines)
    }
}

