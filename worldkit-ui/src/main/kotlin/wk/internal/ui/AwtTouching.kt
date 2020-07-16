package wk.internal.ui

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.util.*

internal class WindowsScreenInfoFetcher : ScreenInfoFetcher {

    override fun getScreens(): LinkedHashMap<ScreenIdentity, ScreenSpec> {
        val screens = LinkedHashMap<ScreenIdentity, ScreenSpec>()
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
            if (x1 > lastX || currentMode.width != width) {
                val pixelWidth = currentMode.width
                val pixelHeight = currentMode.height
                val maximizedX1 = x1 + insets.left
                val maximizedY1 = y1 + insets.top
                val maximizedWidth = pixelWidth - insets.left - insets.right
                val maximizedHeight = pixelHeight - insets.top - insets.bottom
                val maximizedX2 = maximizedX1 + maximizedWidth
                val maximizedY2 = maximizedY1 + maximizedHeight
                val scaleFactor = currentMode.width / width.toDouble()
                lastIdentity = ScreenIdentity(lastX, Math.round(y1 * scaleFactor).toInt(), currentMode.width, currentMode.height)
                screens[lastIdentity] = ScreenSpec(
                        x1 = x1,
                        y1 = y1,
                        x2 = x2,
                        y2 = y2,
                        width = width,
                        height = height,
                        pixelWidth = pixelWidth,
                        pixelHeight = pixelHeight,
                        maximizedX1 = maximizedX1,
                        maximizedY1 = maximizedY1,
                        maximizedX2 = maximizedX2,
                        maximizedY2 = maximizedY2,
                        maximizedWidth = maximizedWidth,
                        maximizedHeight = maximizedHeight,
                        scaleFactor = scaleFactor)
                lastX += currentMode.width
            } else {
                val maximizedX1 = x1 + insets.left
                val maximizedY1 = y1 + insets.top
                val maximizedWidth = width - insets.left - insets.right
                val maximizedHeight = height - insets.top - insets.bottom
                val maximizedX2 = maximizedX1 + maximizedWidth
                val maximizedY2 = maximizedY1 + maximizedHeight
                lastIdentity = ScreenIdentity(x1, y1, currentMode.width, currentMode.height)
                screens.put(lastIdentity, ScreenSpec(x1, y1, x2, y2, currentMode.width, currentMode.height, currentMode.width, currentMode.height,
                        maximizedX1, maximizedY1, maximizedX2, maximizedY2, maximizedWidth, maximizedHeight, 1.0))
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
            screens.forEach { (_, screenSpec) ->
                screenSpec.scaleFactor *= correctionFactor
                screenSpec.scaleFactor = (Math.round((Math.round(screenSpec.scaleFactor * 4.0) / 4.0) * 100.0) / 100.0).coerceIn(1.0, 2.5)
            }
        }
        return screens
    }
}

