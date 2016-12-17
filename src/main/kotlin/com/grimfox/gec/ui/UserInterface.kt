package com.grimfox.gec.ui

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.learning.LessonEightRenderer
import com.grimfox.gec.ui.widgets.Block
import com.grimfox.gec.ui.widgets.uiRoot
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.Reference
import com.grimfox.gec.util.getPathForResource
import com.grimfox.gec.util.loadResource
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
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
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.lang.Math.round
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.*

fun layout(block: UiLayout.(UserInterface) -> Unit) = block

fun ui(layoutBlock: UiLayout.(UserInterface) -> Unit, width: Int, height: Int, resetView: MutableReference<Boolean>, rotateAroundCamera: Reference<Boolean>, perspectiveOn: Reference<Boolean>, waterPlaneOn: Reference<Boolean>, heightMapScaleFactor: Reference<Float>, tick: UserInterface.() -> Unit) {
    val ui = UserInterfaceInternal(createWindow(width, height))
    try {
        ui.layout.layoutBlock(ui)
        ui.mouseClickHandler = { button, x, y, isDown ->
            ui.root.handleMouseAction(button, x, y, isDown)
        }
        ui.show()
        val lesson8: LessonEightRenderer = LessonEightRenderer(resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor)
        while (!ui.shouldClose()) {
            ui.handleFrameInput()
            ui.handleDragAndResize()
            ui.clearViewport()
            ui.tick()
            lesson8.onDrawFrame(534, 40, ui.pixelWidth, ui.pixelHeight, ui.relativeMouseX, ui.relativeMouseY, ui.scrollY, ui.isMouse1Down, ui.isMouse2Down)
            ui.drawFrame()
            ui.swapBuffers()
        }
    } catch (e: Throwable) {
        e.printStackTrace()
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

    fun createImage(resource: String, options: Int): Int

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
    var maximizeHandler: () -> Unit
    var minimizeHandler: () -> Unit
    var restoreHandler: () -> Unit

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

    internal fun clearViewport() {
        glViewport(0, 0, window.currentPixelWidth, window.currentPixelHeight)
        glClearColor(layout.background.r, layout.background.g, layout.background.b, layout.background.a)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    internal fun drawFrame() {
        glViewport(0, 0, window.currentPixelWidth, window.currentPixelHeight)
        nvgSave(nvg)
        root.width = width
        root.height = height
        root.handleNewMousePosition(nvg, relativeMouseX, relativeMouseY)
        nvgBeginFrame(nvg, width, height, pixelWidth / width.toFloat())
        root.draw(nvg)
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

    override fun createImage(resource: String, options: Int): Int {
        return nvgCreateImage(nvg, getPathForResource(resource), options)
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
        val redBits: Int,
        val greenBits: Int,
        val blueBits: Int,
        val refreshRate: Int)

private val NO_MONITOR = MonitorSpec(-1, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

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
        val maximizedX1: Int,
        val maximizedY1: Int,
        val maximizedX2: Int,
        val maximizedY2: Int,
        val maximizedWidth: Int,
        val maximizedHeight: Int)

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

            if (currentPixelWidth < 0 || currentPixelHeight < 0) {
                isMinimized = true
            } else {
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
                        monitors.forEachIndexed { i, monitorSpec ->
                            if (mouseX >= monitorSpec.mouseSpaceX1 && mouseX <= monitorSpec.mouseSpaceX2 && mouseY >= monitorSpec.mouseSpaceY1 && mouseY <= monitorSpec.mouseSpaceY2) {
                                currentMonitor = monitorSpec
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
            val dragAreaY1 = window.layout.dragArea.y
            val dragAreaY2 = dragAreaY1 + window.layout.dragArea.height
            val dragAreaX1 = window.layout.dragArea.x
            val dragAreaX2 = dragAreaX1 + window.layout.dragArea.width
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
    window.mouseClickHandler(button, Math.round(x).toInt(), Math.round(y).toInt(), action == GLFW_PRESS)
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
                window.currentMonitor = monitorSpec
            }
        }
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
    for (device in devices) {
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
        if (x1 > lastX) {
            val scaleFactor = lastX / x1.toDouble()
            val iX1 = lastX
            val iY1 = Math.round(y1 * scaleFactor).toInt()
            val iWidth = Math.round(width * scaleFactor).toInt()
            val iHeight = Math.round(height * scaleFactor).toInt()
            screens.put(ScreenIdentity(iX1, iY1, iWidth, iHeight), ScreenSpec(x1, y1, x2, y2, width, height, maximizedX1, maximizedY1, maximizedX2, maximizedY2, maximizedWidth, maximizedHeight))
            warpLines.add(WarpLine(iX1, y1, x1, y1 + height, Math.abs(x1 - lastX), 0))
            lastX = iX1 + iWidth
        } else {
            screens.put(ScreenIdentity(x1, y1, width, height), ScreenSpec(x1, y1, x2, y2, width, height, maximizedX1, maximizedY1, maximizedX2, maximizedY2, maximizedWidth, maximizedHeight))
            lastX = x2
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
                    redBits = redBits,
                    greenBits = greenBits,
                    blueBits = blueBits,
                    refreshRate = refreshRate
            ))
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
    }
    return Pair(monitors, currentMonitor)
}
