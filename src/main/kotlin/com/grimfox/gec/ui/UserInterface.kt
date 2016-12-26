package com.grimfox.gec.ui

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.grimfox.gec.extensions.twr
import com.grimfox.gec.opengl.loadImagePixels
import com.grimfox.gec.ui.widgets.Block
import com.grimfox.gec.ui.widgets.uiRoot
import com.grimfox.gec.util.clamp
import com.grimfox.gec.util.loadResource
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.*
import org.lwjgl.opengl.ARBDebugOutput
import org.lwjgl.opengl.GL.createCapabilities
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL43
import org.lwjgl.opengl.GLUtil.setupDebugMessageCallback
import org.lwjgl.opengl.KHRDebug
import org.lwjgl.system.Callback
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.lang.Math.round
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.*

val LOG: Logger = LoggerFactory.getLogger(UserInterface::class.java)
val JSON = jacksonObjectMapper()

fun layout(block: UiLayout.(UserInterface) -> Unit) = block

fun ui(layoutBlock: UiLayout.(UserInterface) -> Unit, width: Int, height: Int, tick: UserInterface.() -> Unit) {
    val ui = UserInterfaceInternal(createWindow(width, height))
    try {
        ui.layout.layoutBlock(ui)
        ui.mouseClickHandler = { button, x, y, isDown ->
            ui.root.handleMouseAction(button, x, y, isDown)
        }
        ui.scrollHandler = { x, y ->
            ui.root.handleScroll(x, y)
        }
        ui.show()
        while (!ui.shouldClose()) {
            ui.handleFrameInput()
            ui.handleDragAndResize()
            val frameWidth = ui.width
            val frameHeight = ui.height
            ui.clearViewport(frameWidth, frameHeight)
            ui.tick()
            ui.drawFrame(frameWidth, frameHeight)
            ui.swapBuffers()
        }
    } catch (e: Throwable) {
        LOG.error("Error in main UI loop", e)
    } finally {
        ui.hide()
        ui.close()
    }
}

interface UiLayout {

    val background: NVGColor

    var root: Block

    var dragArea: Block

    fun createFont(resource: String, name: String): Int

    fun createGlfwImage(resource: String): GLFWImage

    fun createGlfwImages(vararg resources: String): GLFWImage.Buffer

//    fun createImage(resource: String, options: Int): Int
//
    fun createImage(textureHandle: Int, width: Int, height: Int, options: Int): Int

    fun root(builder: Block.() -> Unit) {
        root = uiRoot(0, 0, 0, 0, builder)
    }
}

interface UserInterface {

    val layout: UiLayout

    val width: Int
    val height: Int
    val pixelWidth: Int
    val pixelHeight: Int
    val mouseX: Int
    val mouseY: Int
    val relativeMouseX: Int
    val relativeMouseY: Int
    val scrollX: Float
    val scrollY: Float
    val isMaximized: Boolean
    val isMinimized: Boolean
    val isMouse1Down: Boolean
    val isMouse2Down: Boolean
    var mouseClickHandler: (button: Int, x: Int, y: Int, isDown: Boolean) -> Unit
    var scrollHandler: (x: Double, y: Double) -> Unit
    var maximizeHandler: () -> Unit
    var minimizeHandler: () -> Unit
    var restoreHandler: () -> Unit

    fun setWindowIcon(images: GLFWImage.Buffer)

    fun show()

    fun hide()

    fun closeWindow()

    fun minimizeWindow()

    fun toggleMaximized()

    fun maximizeWindow()

    fun restoreWindow()

    operator fun invoke(block: UserInterface.() -> Unit) {
        this.block()
    }
}

private class UserInterfaceInternal internal constructor(internal val window: WindowContext) : UserInterface {

    internal val nvg: Long by lazy { window.nvg }
    internal val root: Block by lazy { layout.root }

    override val isMaximized: Boolean get() = window.isMaximized
    override val isMinimized: Boolean get() = window.isMinimized
    override val layout: UiLayout get() = window.layout
    override val width: Int get() = window.currentWidth
    override val height: Int get() = window.currentHeight
    override val pixelWidth: Int get() = window.currentPixelWidth
    override val pixelHeight: Int get() = window.currentPixelHeight
    override val mouseX: Int get() = window.mouseX
    override val mouseY: Int get() = window.mouseY
    override val relativeMouseX: Int get() = round(window.relativeMouseX.toFloat())
    override val relativeMouseY: Int get() = round(window.relativeMouseY.toFloat())
    override val scrollX: Float get() = window.scrollX
    override val scrollY: Float get() = window.scrollY
    override val isMouse1Down: Boolean get() = window.isMouse1Down
    override val isMouse2Down: Boolean get() = window.isMouse2Down
    override var mouseClickHandler: (Int, Int, Int, Boolean) -> Unit
        get() = window.mouseClickHandler
        set(value) {
            window.mouseClickHandler = value
        }
    override var scrollHandler: (Double, Double) -> Unit
        get() = window.scrollHandler
        set(value) {
            window.scrollHandler = value
        }
    override var maximizeHandler: () -> Unit
        get() = window.maximizeHandler
        set(value) {
            window.maximizeHandler = value
        }
    override var minimizeHandler: () -> Unit
        get() = window.minimizeHandler
        set(value) {
            window.minimizeHandler = value
        }
    override var restoreHandler: () -> Unit
        get() = window.restoreHandler
        set(value) {
            window.restoreHandler = value
        }

    override fun setWindowIcon(images: GLFWImage.Buffer) {
        glfwSetWindowIcon(window.id, images)
    }

    override fun show() {
        glfwShowWindow(window.id)
    }

    override fun hide() {
        glfwHideWindow(window.id)
    }

    override fun closeWindow() {
        glfwSetWindowShouldClose(window.id, true)
    }

    override fun minimizeWindow() {
        window.isMinimized = true
        minimizeHandler()
        glfwIconifyWindow(window.id)
    }

    override fun maximizeWindow() {
        window.maximize()
    }

    override fun restoreWindow() {
        window.restore()
    }

    override fun toggleMaximized() {
        if (isMaximized) {
            restoreWindow()
        } else {
            maximizeWindow()
        }
    }

    internal fun close() {
        window.close()
    }

    internal fun handleFrameInput() {
        window.handleFrameInput()
    }

    internal fun handleDragAndResize() {
        window.handleDragAndResize()
    }

    internal fun shouldClose(): Boolean {
        return glfwWindowShouldClose(window.id)
    }

    internal fun clearViewport(width: Int, height: Int) {
        glViewport(0, 0, width, height)
        glClearColor(layout.background.r, layout.background.g, layout.background.b, layout.background.a)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    internal fun drawFrame(width: Int, height: Int) {
        if (window.currentPixelWidth < 0 || window.currentPixelHeight < 0 && !isMinimized) {
            window.isMinimized = true
            minimizeHandler()
        }
        if (isMinimized) {
            return
        }
        glViewport(0, 0, width, height)
        nvgSave(nvg)
        val scale = clamp(Math.round((Math.round((window.currentMonitor.scaleFactor * window.currentMonitor.overRender) * 4.0) / 4.0) * 100.0) / 100.0, 1.0, 2.5).toFloat()
        root.width = (width / scale).toInt()
        root.height = (height / scale).toInt()
        root.handleNewMousePosition(nvg, Math.round(relativeMouseX / scale), Math.round(relativeMouseY / scale))
        nvgBeginFrame(nvg, width, height, pixelWidth / width.toFloat())
        root.draw(nvg, scale)
        glViewport(0, 0, width, height)
        nvgEndFrame(nvg)
        nvgRestore(nvg)
    }

    internal fun swapBuffers() {
        glfwSwapBuffers(window.id)
    }
}

private class UiLayoutInternal internal constructor(val nvg: Long) : UiLayout {

    internal val fonts: ArrayList<ByteBuffer> = ArrayList()

    override val background: NVGColor = NO_COLOR

    override lateinit var root: Block
    override lateinit var dragArea: Block

    override fun createFont(resource: String, name: String): Int {
        val fontData = loadResource(resource, 160 * 1024)
        fonts.add(fontData)
        return nvgCreateFontMem(nvg, name, fontData, 0)
    }

    override fun createGlfwImage(resource: String): GLFWImage {
        val (width, height, data) = loadImagePixels(resource)
        val image = GLFWImage.create()
        image.set(width, height, data)
        return image
    }

    override fun createGlfwImages(vararg resources: String): GLFWImage.Buffer {
        val buffer = GLFWImage.Buffer(BufferUtils.createByteBuffer(resources.size * GLFWImage.SIZEOF))
        resources.forEach {
            buffer.put(createGlfwImage(it))
        }
        buffer.flip()
        return buffer
    }

//    override fun createImage(resource: String, options: Int): Int {
//        return nvgCreateImage(nvg, getPathForResource(resource), options)
//    }
//
    override fun createImage(textureHandle: Int, width: Int, height: Int, options: Int): Int {
        return nvglCreateImageFromHandle(nvg, textureHandle, width, height, options)
    }

    internal fun close() {
        fonts.clear()
    }
}

private data class MonitorSpec(
        val id: Long,
        val dpiX: Double,
        val dpiY: Double,
        val physicalWidth: Int,
        val physicalHeight: Int,
        val virtualWidth: Int,
        val virtualHeight: Int,
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val mouseSpaceX1: Int,
        val mouseSpaceY1: Int,
        val mouseSpaceX2: Int,
        val mouseSpaceY2: Int,
        val mouseSpaceWidth: Int,
        val mouseSpaceHeight: Int,
        val centerX: Int,
        val centerY: Int,
        val maximizedWidth: Int,
        val maximizedHeight: Int,
        var maximizedX1: Int,
        val maximizedY1: Int,
        val maximizedX2: Int,
        val maximizedY2: Int,
        val scaleFactor: Double,
        val overRender: Double,
        val redBits: Int,
        val greenBits: Int,
        val blueBits: Int,
        val refreshRate: Int)

private val NO_MONITOR = MonitorSpec(-1, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.0, 1.0, 0, 0, 0, 0)

private data class ScreenIdentity(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int)

private data class WarpLine(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val warpX: Int,
        val warpY: Int)

private data class ScreenSpec(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val width: Int,
        val height: Int,
        val pixelWidth: Int,
        val pixelHeight: Int,
        val maximizedX1: Int,
        val maximizedY1: Int,
        val maximizedX2: Int,
        val maximizedY2: Int,
        val maximizedWidth: Int,
        val maximizedHeight: Int,
        var scaleFactor: Double,
        var overRender: Double)

private class WindowContext(
        var id: Long = 0,

        val debugProc: Callback,
        val nvg: Long,
        val layout: UiLayoutInternal = UiLayoutInternal(nvg),

        var isMaximized: Boolean = false,
        var isMinimized: Boolean = false,
        var isResizing: Boolean = false,
        var isDragging: Boolean = false,
        var hasMoved: Boolean = false,

        var resizeAreaWidth: Int = 20,
        var resizeAreaHeight: Int = resizeAreaWidth,

        var x: Int = 0,
        var y: Int = 0,

        var mouseX: Int = 0,
        var mouseY: Int = 0,

        var relativeMouseX: Double = 0.0,
        var relativeMouseY: Double = 0.0,

        var dragMouseStartX: Int = 0,
        var dragMouseStartY: Int = 0,

        var dragWindowStartX: Int = 0,
        var dragWindowStartY: Int = 0,

        var isMouse1Down: Boolean = false,
        var isMouse2Down: Boolean = false,

        var scrollX: Float = 0.0f,
        var scrollY: Float = 0.0f,

        var width: Int = 800,
        var height: Int = 600,

        var resizeMouseStartX: Int = 0,
        var resizeMouseStartY: Int = 0,

        var resizeWindowStartX: Int = 0,
        var resizeWindowStartY: Int = 0,

        var resizeMove: Boolean = false,

        var resizeWindowStartWidth: Int = 0,
        var resizeWindowStartHeight: Int = 0,

        var currentWidth: Int = width,
        var currentHeight: Int = height,

        var currentPixelWidth: Int = width,
        var currentPixelHeight: Int = height,

        var restoreX: Int = 0,
        var restoreY: Int = 0,

        var monitors: List<MonitorSpec> = emptyList(),
        var warpLines: List<WarpLine> = emptyList(),

        var currentMonitor: MonitorSpec = NO_MONITOR,
        var mouseClickHandler: (Int, Int, Int, Boolean) -> Unit = { button, x, y, isDown -> },
        var scrollHandler: (Double, Double) -> Unit = { x, y -> },
        var maximizeHandler: () -> Unit = {},
        var minimizeHandler: () -> Unit = {},
        var restoreHandler: () -> Unit = {}

) {

    internal fun handleFrameInput() {
        twr(stackPush()) { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)

            glfwGetWindowSize(id, w, h)
            currentWidth = w.get(0)
            currentHeight = h.get(0)

            glfwGetFramebufferSize(id, w, h)
            currentPixelWidth = w.get(0)
            currentPixelHeight = h.get(0)

            if (currentPixelWidth <= 0 || currentPixelHeight <= 0 && !isMinimized) {
                isMinimized = true
                minimizeHandler()
            } else if (currentPixelWidth > 0 && currentPixelHeight > 0 && isMinimized) {
                isMinimized = false
            }

            val lastMouseX = mouseX
            val lastMouseY = mouseY
            glfwGetWindowPos(id, w, h)
            x = w[0]
            y = h[0]
            val pointerLocation = MouseInfo.getPointerInfo()?.location
            if (pointerLocation == null) {
                val x = stack.mallocDouble(1)
                val y = stack.mallocDouble(1)
                glfwGetCursorPos(id, x, y)
                val newMouseX = Math.round(mouseX + (x[0] - relativeMouseX)).toInt()
                val newMouseY = Math.round(mouseY + (y[0] - relativeMouseY)).toInt()
                val (mouseWarpX, mouseWarpY) = getWarp(lastMouseX, lastMouseY, newMouseX, newMouseY)
                mouseX = newMouseX + mouseWarpX
                mouseY = newMouseY + mouseWarpY
            } else {
                mouseX = pointerLocation.x
                mouseY = pointerLocation.y
            }
            relativeMouseX = mouseX.toDouble() - x
            relativeMouseY = mouseY.toDouble() - y
        }
        glfwPollEvents()
    }

    internal fun handleDragAndResize() {
        handleDragging()
        handleResizing()
    }

    private fun handleResizing() {
        if (isResizing) {
            isMaximized = false
            restoreHandler()
            val deltaMouseX = if (resizeMove) resizeMouseStartX - mouseX else mouseX - resizeMouseStartX
            val deltaMouseY = mouseY - resizeMouseStartY
            val deltaWindowX = width - resizeWindowStartWidth
            val deltaWindowY = height - resizeWindowStartHeight
            var resizeX = deltaMouseX - deltaWindowX
            val resizeY = deltaMouseY - deltaWindowY
            if (Math.abs(resizeX) > 0.5 || Math.abs(resizeY) > 0.5) {
                var newWidth = width + resizeX
                var newHeight = height + resizeY
                if (resizeMove) {
                    val newWindowX1 = x - resizeX
                    if (newWindowX1 < currentMonitor.maximizedX1) {
                        newWidth = (x + width) - currentMonitor.maximizedX1
                    } else if (newWidth < 640) {
                        newWidth = 640
                    }
                    resizeX = newWidth - width
                } else {
                    val newWindowX2 = x + newWidth
                    if (newWindowX2 > currentMonitor.maximizedX2) {
                        newWidth = currentMonitor.maximizedX2 - x
                    } else if (newWidth < 640) {
                        newWidth = 640
                    }
                }
                val newWindowY2 = y + newHeight
                if (newWindowY2 > currentMonitor.maximizedY2) {
                    newHeight = currentMonitor.maximizedY2 - y
                } else if (newHeight < 480) {
                    newHeight = 480
                }
                width = newWidth
                height = newHeight
                currentWidth = newWidth
                currentHeight = newHeight
                glfwSetWindowSize(id, currentWidth, currentHeight)
                if (resizeMove) {
                    x -= resizeX
                    restoreX = x
                    glfwSetWindowPos(id, x, y)
                }
            }
        }
    }

    private fun handleDragging() {
        if (isDragging) {
            if (hasMoved) {
                isMaximized = false
                restoreHandler()
            }
            val deltaMouseX = mouseX - dragMouseStartX
            val deltaMouseY = mouseY - dragMouseStartY
            val deltaWindowX = x - dragWindowStartX
            val deltaWindowY = y - dragWindowStartY
            val moveX = deltaMouseX - deltaWindowX
            val moveY = deltaMouseY - deltaWindowY
            if (Math.abs(moveX) > 0.5 || Math.abs(moveY) > 0.5) {
                var newWindowX = Math.round(x + moveX.toDouble()).toInt()
                var newWindowY = Math.round(y + moveY.toDouble()).toInt()
                if (newWindowY < 0) {
                    if (hasMoved && (newWindowY <= -15 || mouseY < 0.1)) {
                        maximize()
                        hasMoved = false
                    }
                } else {
                    if (!hasMoved && (Math.abs(dragWindowStartX - newWindowX) > 10 || Math.abs(dragWindowStartY - newWindowY) > 10)) {
                        newWindowX = restore(newWindowX)
                        hasMoved = true
                    }
                    if (hasMoved) {
                        val lastCurrentWidth = currentWidth
                        val lastCurrentHeight = currentHeight
                        monitors.forEachIndexed { i, monitorSpec ->
                            if (mouseX >= monitorSpec.mouseSpaceX1 && mouseX <= monitorSpec.mouseSpaceX2 && mouseY >= monitorSpec.mouseSpaceY1 && mouseY <= monitorSpec.mouseSpaceY2) {
                                adjustForCurrentMonitor(monitorSpec, this)
                            }
                        }
                        val roomToGrowX = currentMonitor.maximizedWidth - currentWidth
                        val roomToGrowY = currentMonitor.maximizedHeight - currentHeight
                        val adjustSizeX = if (roomToGrowX < 0) roomToGrowX else 0
                        val adjustSizeY = if (roomToGrowY < 0) roomToGrowY else 0
                        if (adjustSizeX < 0 || adjustSizeY < 0) {
                            currentWidth += adjustSizeX
                            currentHeight += adjustSizeY
                            width = currentWidth
                            height = currentHeight
                            glfwSetWindowSize(id, width, height)
                        }
                        if (newWindowX < currentMonitor.maximizedX1) {
                            newWindowX = currentMonitor.maximizedX1
                        } else if (newWindowX > (currentMonitor.maximizedX1 + currentMonitor.maximizedWidth) - width) {
                            newWindowX = (currentMonitor.maximizedX1 + currentMonitor.maximizedWidth) - width
                        }
                        if (newWindowY < currentMonitor.maximizedY1) {
                            newWindowY = currentMonitor.maximizedY1
                        } else if (newWindowY > (currentMonitor.maximizedY1 + currentMonitor.maximizedHeight) - height) {
                            newWindowY = (currentMonitor.maximizedY1 + currentMonitor.maximizedHeight) - height
                        }
                        glfwSetWindowPos(id, newWindowX, newWindowY)
                        if (currentWidth != lastCurrentWidth || currentHeight != lastCurrentHeight) {
                            glfwSetWindowSize(id, currentWidth, currentHeight)
                        }
                        x = newWindowX
                        y = newWindowY
                        restoreX = x
                        restoreY = y
                    }
                }
            }
        }
    }

    internal fun restore(windowX: Int = x): Int {
        if (isMaximized && (currentWidth != width || currentWidth != height)) {
            val newWindowX = if (isDragging) {
                val newWindowX = Math.round(mouseX - (relativeMouseX / currentWidth.toDouble()) * width).toInt()
                dragWindowStartX = newWindowX
                dragMouseStartX = mouseX
                newWindowX
            } else {
                x = restoreX
                y = restoreY
                glfwSetWindowPos(id, x, y)
                restoreX
            }
            glfwSetWindowSize(id, width, height)
            isMaximized = false
            restoreHandler()
            return newWindowX
        }
        return windowX
    }

    internal fun maximize() {
        if (!isMaximized) {
            restoreX = x
            restoreY = y
            glfwSetWindowPos(id, currentMonitor.maximizedX1, currentMonitor.maximizedY1)
            glfwSetWindowSize(id, currentMonitor.maximizedWidth, currentMonitor.maximizedHeight)
            isMaximized = true
            maximizeHandler()
        }
    }

    private fun getWarp(lastX: Int, lastY: Int, currentX: Int, currentY: Int): Pair<Int, Int> {
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

    internal fun close() {
        try {
            layout.close()
        } finally {
            Callbacks.glfwFreeCallbacks(id)
            debugProc.free()
            glfwTerminate()
            glfwSetErrorCallback(null).free()
        }
    }
}

private fun createWindow(width: Int, height: Int): WindowContext {
    GLFWErrorCallback.createPrint().set()
    if (!glfwInit()) throw IllegalStateException("Unable to initialize glfw")
    val (screens, warpLines) = getScreensAndWarpLines()
    val (monitors, currentMonitor) = getMonitorInfo(screens)
    var windowId = NULL
    twr(stackPush()) { stack ->
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_RED_BITS, currentMonitor.redBits)
        glfwWindowHint(GLFW_GREEN_BITS, currentMonitor.greenBits)
        glfwWindowHint(GLFW_BLUE_BITS, currentMonitor.blueBits)
        glfwWindowHint(GLFW_REFRESH_RATE, currentMonitor.refreshRate)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE)
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)
        glfwWindowHint(GLFW_AUTO_ICONIFY, GLFW_FALSE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_SAMPLES, 4)
        if (Platform.get() === Platform.MACOSX) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        }
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE)
        LOG.info("Creating window with width: $width, height: $height")
        windowId = glfwCreateWindow(width, height, "GLFW Nuklear Demo", NULL, NULL)
        if (windowId == NULL) throw RuntimeException("Failed to create the GLFW window")
        glfwSetWindowPos(windowId, currentMonitor.centerX - width / 2 + 1, currentMonitor.centerY - height / 2 + 1)
    }
    glfwMakeContextCurrent(windowId)
    val caps = createCapabilities()
    val debugProc = setupDebugMessageCallback()
    if (caps.OpenGL43) {
        GL43.glDebugMessageControl(GL43.GL_DEBUG_SOURCE_API, GL43.GL_DEBUG_TYPE_OTHER, GL43.GL_DEBUG_SEVERITY_NOTIFICATION, null as IntBuffer?, false)
    } else if (caps.GL_KHR_debug) {
        KHRDebug.glDebugMessageControl(
                KHRDebug.GL_DEBUG_SOURCE_API,
                KHRDebug.GL_DEBUG_TYPE_OTHER,
                KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION,
                null as IntBuffer?,
                false
        )
    } else if (caps.GL_ARB_debug_output) {
        ARBDebugOutput.glDebugMessageControlARB(ARBDebugOutput.GL_DEBUG_SOURCE_API_ARB, ARBDebugOutput.GL_DEBUG_TYPE_OTHER_ARB, ARBDebugOutput.GL_DEBUG_SEVERITY_LOW_ARB, null as IntBuffer?, false)
    }
    val nvg = nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES or NVG_DEBUG)
    if (nvg == NULL) {
        throw RuntimeException("Could not init nanovg.")
    }
    glfwSwapInterval(1)
    val window = WindowContext(id = windowId, debugProc = debugProc, nvg = nvg, monitors = monitors, warpLines = warpLines, width = width, height = height)
    glfwSetScrollCallback(window.id) { windowId, xOffset, yOffset ->
        window.scrollX += xOffset.toFloat()
        window.scrollY += yOffset.toFloat()
        window.scrollHandler(xOffset, yOffset)
    }
    glfwSetCharCallback(window.id) { windowId, codePoint ->

    }
    glfwSetKeyCallback(window.id) { windowId, key, scanCode, action, mods ->
        when (key) {
            GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(windowId, true)
        }
    }
    glfwSetMouseButtonCallback(window.id) { windowId, button, action, mods ->
        twr(stackPush()) { stack ->
            val cx = stack.mallocDouble(1)
            val cy = stack.mallocDouble(1)
            glfwGetCursorPos(windowId, cx, cy)
            val x = cx.get(0)
            val y = cy.get(0)
            val dragAreaY1 = Math.round(window.layout.dragArea.y * window.currentMonitor.scaleFactor)
            val dragAreaY2 = Math.round((dragAreaY1 + window.layout.dragArea.height) * window.currentMonitor.scaleFactor)
            val dragAreaX1 = Math.round(window.layout.dragArea.x * window.currentMonitor.scaleFactor)
            val dragAreaX2 = Math.round((dragAreaX1 + window.layout.dragArea.width) * window.currentMonitor.scaleFactor)
            if (y >= dragAreaY1 && y < dragAreaY2 && x >= dragAreaX1 && x < dragAreaX2
                    && button == GLFW_MOUSE_BUTTON_LEFT
                    && action == GLFW_PRESS) {
                startDrag(stack, window)
                handleStandardMouseAction(window, action, button, x, y)
            } else if ((y >= window.currentHeight - window.resizeAreaHeight && y <= window.currentHeight)
                    && ((x >= window.currentWidth - window.resizeAreaWidth && x <= window.currentWidth) || (x >= 0 && x <= window.resizeAreaWidth))
                    && (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS)) {
                startResize(stack, window, x <= window.resizeAreaWidth)
                handleStandardMouseAction(window, action, button, x, y)
            } else {
                handleStandardMouseAction(window, action, button, x, y)
            }
        }
    }
    var windowSize = getWindowSize(windowId)
    if (windowSize.first != width && windowSize.second != height) {
        glfwSetWindowSize(windowId, width, height)
    }
    windowSize = getWindowSize(windowId)
    LOG.info("Created window with width: ${windowSize.first}, height: ${windowSize.second}")
    initializeWindowState(window)
    return window
}

private fun handleStandardMouseAction(window: WindowContext, action: Int, button: Int, x: Double, y: Double) {
    if (action == GLFW_RELEASE) {
        window.isDragging = false
        window.hasMoved = false
        window.isResizing = false
    }
    if (button == GLFW_MOUSE_BUTTON_1) {
        if (action == GLFW_PRESS) {
            window.isMouse1Down = true
        } else if (action == GLFW_RELEASE) {
            window.isMouse1Down = false
        }
    } else if (button == GLFW_MOUSE_BUTTON_2) {
        if (action == GLFW_PRESS) {
            window.isMouse2Down = true
        } else if (action == GLFW_RELEASE) {
            window.isMouse2Down = false
        }
    }
    val scale = clamp(Math.round((Math.round((window.currentMonitor.scaleFactor * window.currentMonitor.overRender) * 4.0) / 4.0) * 100.0) / 100.0, 1.0, 2.5).toFloat()
    window.mouseClickHandler(button, Math.round(x / scale).toInt(), Math.round(y / scale).toInt(), action == GLFW_PRESS)
}

private fun startDrag(stack: MemoryStack, window: WindowContext) {
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    glfwGetWindowPos(window.id, w, h)
    window.x = w[0]
    window.y = h[0]
    window.restoreX = window.x
    window.restoreY = window.y
    window.isDragging = true
    window.hasMoved = false
    val pointerLocation = MouseInfo.getPointerInfo()?.location
    window.mouseX = pointerLocation?.x ?: window.mouseX
    window.mouseY = pointerLocation?.y ?: window.mouseY
    window.dragMouseStartX = window.mouseX
    window.dragMouseStartY = window.mouseY
    window.dragWindowStartX = window.x
    window.dragWindowStartY = window.y
}

private fun startResize(stack: MemoryStack, window: WindowContext, moveWithResize: Boolean) {
    val w = stack.mallocInt(1)
    val h = stack.mallocInt(1)
    glfwGetWindowSize(window.id, w, h)
    window.currentWidth = w[0]
    window.currentHeight = h[0]
    window.width = window.currentWidth
    window.height = window.currentHeight
    window.isResizing = true
    val pointerLocation = MouseInfo.getPointerInfo()?.location
    window.mouseX = pointerLocation?.x ?: window.mouseX
    window.mouseY = pointerLocation?.y ?: window.mouseY
    window.resizeMouseStartX = window.mouseX
    window.resizeMouseStartY = window.mouseY
    window.resizeWindowStartX = window.x
    window.resizeWindowStartY = window.y
    window.resizeMove = moveWithResize
    window.resizeWindowStartWidth = window.width
    window.resizeWindowStartHeight = window.height
}


private fun initializeWindowState(window: WindowContext) {
    twr(stackPush()) { stack ->
        val x = stack.mallocInt(1)
        val y = stack.mallocInt(1)
        val lastMouseX = window.mouseX
        val lastMouseY = window.mouseY
        val pointerLocation = MouseInfo.getPointerInfo()?.location
        window.mouseX = pointerLocation?.x ?: lastMouseX
        window.mouseY = pointerLocation?.y ?: lastMouseY
        glfwGetWindowPos(window.id, x, y)
        window.x = x[0]
        window.y = y[0]
        window.restoreX = window.x
        window.restoreY - window.y
        window.relativeMouseX = window.mouseX.toDouble() - window.x
        window.relativeMouseY = window.mouseY.toDouble() - window.y
        glfwGetWindowSize(window.id, x, y)
        window.currentWidth = x.get(0)
        window.currentHeight = y.get(0)
        window.width = window.currentWidth
        window.height = window.currentHeight
        window.monitors.forEachIndexed { i, monitorSpec ->
            if (window.mouseX >= monitorSpec.mouseSpaceX1 && window.mouseX <= monitorSpec.mouseSpaceX2 && window.mouseY >= monitorSpec.mouseSpaceY1 && window.mouseY <= monitorSpec.mouseSpaceY2) {
                adjustForCurrentMonitor(monitorSpec, window)
            }
        }
        glfwSetWindowSize(window.id, window.currentWidth, window.currentHeight)
    }
}

private fun getWindowSize(windowId: Long): Pair<Int, Int> {
    twr(stackPush()) { stack ->
        val w = stack.mallocInt(1)
        val h = stack.mallocInt(1)
        glfwGetWindowSize(windowId, w, h)
        return Pair(w.get(0), h.get(0))
    }
}

private fun adjustForCurrentMonitor(monitorSpec: MonitorSpec, window: WindowContext) {
    val lastMonitor = window.currentMonitor
    window.currentMonitor = monitorSpec
    if (lastMonitor != window.currentMonitor && lastMonitor.scaleFactor != window.currentMonitor.scaleFactor) {
        val sizeAdjustment = ((window.currentMonitor.scaleFactor / lastMonitor.scaleFactor) / lastMonitor.overRender) * window.currentMonitor.overRender
        LOG.info("sizeAdjustment: $sizeAdjustment, width: ${window.width}, height: ${window.height}, currentWidth: ${window.currentWidth}, currentHeight: ${window.currentHeight}")
        window.width = round(window.width * sizeAdjustment).toInt()
        window.height = round(window.height * sizeAdjustment).toInt()
        window.currentWidth = round(window.currentWidth * sizeAdjustment).toInt()
        window.currentHeight = round(window.currentHeight * sizeAdjustment).toInt()
    }
}

private fun getScreensAndWarpLines(): Pair<LinkedHashMap<ScreenIdentity, ScreenSpec>, List<WarpLine>> {
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
    screens.forEach { screenIdentity, screenSpec ->
        if (screenSpec.scaleFactor < minScaleFactor) {
            minScaleFactor = screenSpec.scaleFactor
        }
    }
    if (minScaleFactor < 1.0) {
        val correctionFactor = 1.0 / minScaleFactor
        screens.forEach { screenIdentity, screenSpec ->
            screenSpec.scaleFactor *= correctionFactor
            screenSpec.scaleFactor = clamp(Math.round((Math.round(screenSpec.scaleFactor * 4.0) / 4.0) * 100.0) / 100.0, 1.0, 2.5)
        }
    }
    return Pair(screens, warpLines)
}

private fun getMonitorInfo(screens: Map<ScreenIdentity, ScreenSpec>): Pair<List<MonitorSpec>, MonitorSpec> {
    val monitors = ArrayList<MonitorSpec>()
    var currentMonitor = NO_MONITOR
    twr(stackPush()) { stack ->
        val intPointer1 = stack.mallocInt(1)
        val intPointer2 = stack.mallocInt(1)
        val monitorIds = glfwGetMonitors()
        while (monitorIds.hasRemaining()) {
            val monitorId = monitorIds.get()
            glfwGetMonitorPhysicalSize(monitorId, intPointer1, intPointer2)
            val physicalWidth = intPointer1[0]
            val physicalHeight = intPointer2[0]
            glfwGetMonitorPos(monitorId, intPointer1, intPointer2)
            val virtualX = intPointer1[0]
            val virtualY = intPointer2[0]
            val videoMode = glfwGetVideoMode(monitorId)
            val virtualWidth = videoMode.width()
            val virtualHeight = videoMode.height()
            val redBits = videoMode.redBits()
            val greenBits = videoMode.greenBits()
            val blueBits = videoMode.blueBits()
            val refreshRate = videoMode.refreshRate()
            val dpiX = (virtualWidth.toDouble() / physicalWidth) * 25.4
            val dpiY = (virtualHeight.toDouble() / physicalHeight) * 25.4
            val centerX = virtualX + (virtualWidth / 2)
            val centerY = virtualY + (virtualHeight / 2)
            var screen = screens[ScreenIdentity(virtualX, virtualY, virtualWidth, virtualHeight)]
            if (screen == null) {
                screen = screens.entries.first().value
            }
            val avgDpi = (dpiX + dpiY) / 2.0
            screen.scaleFactor = (Math.round((Math.round((avgDpi / 100.0) * 4.0) / 4.0) * 100.0) / 100.0) / screen.scaleFactor
            monitors.add(MonitorSpec(
                    id = monitorId,
                    dpiX = dpiX,
                    dpiY = dpiY,
                    physicalWidth = physicalWidth,
                    physicalHeight = physicalHeight,
                    virtualWidth = virtualWidth,
                    virtualHeight = virtualHeight,
                    x1 = virtualX,
                    y1 = virtualY,
                    x2 = virtualX + virtualWidth,
                    y2 = virtualY + virtualHeight,
                    centerX = centerX,
                    centerY = centerY,
                    mouseSpaceX1 = screen.x1,
                    mouseSpaceY1 = screen.y1,
                    mouseSpaceX2 = screen.x2,
                    mouseSpaceY2 = screen.y2,
                    mouseSpaceWidth = screen.width,
                    mouseSpaceHeight = screen.height,
                    maximizedWidth = screen.maximizedWidth,
                    maximizedHeight = screen.maximizedHeight,
                    maximizedX1 = screen.maximizedX1,
                    maximizedY1 = screen.maximizedY1,
                    maximizedX2 = screen.maximizedX2,
                    maximizedY2 = screen.maximizedY2,
                    scaleFactor = screen.scaleFactor,
                    overRender = screen.overRender,
                    redBits = redBits,
                    greenBits = greenBits,
                    blueBits = blueBits,
                    refreshRate = refreshRate))
        }
        val pointerLocation = MouseInfo.getPointerInfo()?.location
        val mouseX = pointerLocation?.x ?: 0
        val mouseY = pointerLocation?.y ?: 0
        currentMonitor = monitors[0]
        monitors.forEachIndexed { i, monitorSpec ->
            if (mouseX >= monitorSpec.mouseSpaceX1 && mouseX <= monitorSpec.mouseSpaceX2 && mouseY >= monitorSpec.mouseSpaceY1 && mouseY <= monitorSpec.mouseSpaceY2) {
                currentMonitor = monitorSpec
            }
        }
        LOG.info(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Pair(screens, monitors)))
    }
    return Pair(monitors, currentMonitor)
}
