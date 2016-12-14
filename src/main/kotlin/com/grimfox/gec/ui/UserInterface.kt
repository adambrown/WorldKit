package com.grimfox.gec.ui

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.learning.LessonEightRenderer
import com.grimfox.gec.util.loadResource
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.nanovg.NanoVG
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.*
import org.lwjgl.nuklear.*
import org.lwjgl.nuklear.Nuklear.*
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL.createCapabilities
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GLUtil.setupDebugMessageCallback
import org.lwjgl.system.Callback
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.lang.Math.round
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*

fun style(block: UiStyle.(NkContext, Long) -> Unit) = block

fun mouseClickHandler(mouseClickHandler: (Int, Int, Int, Boolean) -> Unit) = mouseClickHandler

fun ui(styleBlock: UiStyle.(NkContext, Long) -> Unit, mouseClickHandler: (Int, Int, Int, Boolean) -> Unit, width: Int, height: Int, resetView: IntBuffer, rotateAroundCamera: IntBuffer, perspectiveOn: IntBuffer, waterPlaneOn: IntBuffer, heightMapScaleFactor: FloatBuffer, uiBlock: UserInterface.(NkContext, Long) -> Unit) {
    val style = UiStyleInternal()
    val ui = UserInterfaceInternal(createNkContext(width, height, style))
    ui.mouseClickHandler = mouseClickHandler
    try {
        style.styleBlock(ui.nk, ui.nvg)
        ui.show()
        val lesson8: LessonEightRenderer = LessonEightRenderer(resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor)
        lesson8.onSurfaceCreated()
        while (!ui.shouldClose()) {
            ui.handleFrameInput()
            ui.handleDragAndResize()
            ui.clearViewport()
            lesson8.onDrawFrame(534, 42, ui.pixelWidth, ui.pixelHeight, ui.relativeMouseX, ui.relativeMouseY, ui.scrollY, ui.isMouse1Down, ui.isMouse2Down)
            nvgSave(ui.nvg)
            nvgBeginFrame(ui.nvg, ui.width, ui.height, ui.pixelWidth / ui.width.toFloat())
            ui.uiBlock(ui.nk, ui.nvg)
            ui.drawFrame()
            nvgEndFrame(ui.nvg)
            nvgRestore(ui.nvg)
            ui.swapBuffers()
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    } finally {
        ui.hide()
        ui.close()
    }
}

interface UiStyle {

    val background: NkColor

    var dragAreaLeftMargin: Int
    var dragAreaRightMargin: Int
    var dragAreaTopMargin: Int
    var dragAreaHeight: Int

    fun createNkFont(resource: String, height: Float, codePointOffset: Int, codePointCount: Int, textureWidth: Int, textureHeight: Int, font: NkUserFont = NkUserFont.create()): Int

    fun getNkFont(id: Int): NkUserFont

    fun createNvgFont(resource: String, name: String, nvg: Long): Int

    fun getNvgFont(id: Int): Int
}

interface UserInterface {

    val style: UiStyle

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
    val isMouse1Down: Boolean
    val isMouse2Down: Boolean
    var mouseClickHandler: (button: Int, x: Int, y: Int, isDown: Boolean) -> Unit

    fun show()

    fun hide()

    fun closeWindow()

    fun minimizeWindow()

    fun toggleMaximized()

    fun maximizeWindow()

    fun restoreWindow()
}

var NkColor.r: Byte
    get() = r()
    set(value) { r(value) }
var NkColor.g: Byte
    get() = g()
    set(value) { g(value) }
var NkColor.b: Byte
    get() = b()
    set(value) { b(value) }
var NkColor.a: Byte
    get() = a()
    set(value) { a(value) }

var NkColor.rFloat: Float
    get() = colorByteToFloat(r)
    set(value) { r = colorFloatToByte(value) }
var NkColor.gFloat: Float
    get() = colorByteToFloat(g)
    set(value) { g = colorFloatToByte(value) }
var NkColor.bFloat: Float
    get() = colorByteToFloat(b)
    set(value) { b = colorFloatToByte(value) }
var NkColor.aFloat: Float
    get() = colorByteToFloat(a)
    set(value) { a = colorFloatToByte(value) }

var NkColor.rInt: Int
    get() = colorByteToInt(r)
    set(value) { r = colorIntToByte(value) }
var NkColor.gInt: Int
    get() = colorByteToInt(g)
    set(value) { g = colorIntToByte(value) }
var NkColor.bInt: Int
    get() = colorByteToInt(b)
    set(value) { b = colorIntToByte(value) }
var NkColor.aInt: Int
    get() = colorByteToInt(a)
    set(value) { a = colorIntToByte(value) }

fun NkColor.set(r: Int, g: Int, b: Int, a: Int) {
    nk_rgba(r, g, b, a, this)
}

fun NkColor.set(r: Int, g: Int, b: Int) {
    nk_rgb(r, g, b, this)
}

private fun colorByteToInt(b: Byte) = (b.toInt() and 0xFF)
private fun colorByteToFloat(b: Byte) = colorByteToInt(b) / 255.0f
private fun colorIntToByte(i: Int) = i.toByte()
private fun colorFloatToByte(f: Float) = colorIntToByte(Math.round(f * 255))

private class UserInterfaceInternal internal constructor(internal val context: NuklearContext) : UserInterface {

    internal val window = context.window
    internal val nk = context.nk
    internal val nvg: Long = context.nvg

    override val isMaximized: Boolean get() = window.isMaximized
    override val style: UiStyle get() = context.style
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
        context.close()
    }

    internal fun handleFrameInput() {
        window.handleFrameInput(context.nk)
    }

    internal fun handleDragAndResize() {
        window.handleDragAndResize()
    }

    internal fun shouldClose(): Boolean {
        return glfwWindowShouldClose(window.id)
    }

    internal fun clearViewport() {
        glViewport(0, -32, window.currentPixelWidth, window.currentPixelHeight + 32)
        glClearColor(style.background.rFloat, style.background.gFloat, style.background.bFloat, style.background.aFloat)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    }

    internal fun drawFrame() {
        context.drawFrame()
    }

    internal fun swapBuffers() {
        glfwSwapBuffers(window.id)
    }
}

private class UiStyleInternal internal constructor() : UiStyle {

    internal val fonts: ArrayList<Pair<Any, ByteBuffer>> = ArrayList()

    override val background: NkColor = NkColor.create()
    override var dragAreaLeftMargin: Int = 0
    override var dragAreaRightMargin: Int = 0
    override var dragAreaTopMargin: Int = 0
    override var dragAreaHeight: Int = 32

    override fun createNkFont(resource: String, height: Float, codePointOffset: Int, codePointCount: Int, textureWidth: Int, textureHeight: Int, font: NkUserFont): Int {
        val fontId = fonts.size
        val fontData = loadResource(resource, 160 * 1024)
        createNkFont(fontData, height, codePointOffset, codePointCount, textureWidth, textureHeight, font)
        fonts.add(Pair(font, fontData))
        return fontId
    }

    override fun createNvgFont(resource: String, name: String, nvg: Long): Int {
        val fontId = fonts.size
        val fontData = loadResource(resource, 160 * 1024)
        val fontPointer = nvgCreateFontMem(nvg, name, fontData, 0)
        fonts.add(Pair(fontPointer, fontData))
        return fontId
    }

    override fun getNkFont(id: Int): NkUserFont {
        return fonts[id].first as NkUserFont
    }

    override fun getNvgFont(id: Int): Int {
        return fonts[id].first as Int
    }

    internal fun close() {
        fonts.forEach {
            try {
                if (it.first is NkUserFont) {
                    val font = it.first as NkUserFont
                    glDeleteTextures(font.texture().id())
                    font.query().free()
                } else if (it.first is Int) {
                    val fontPointer = it.first as Int

                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

private data class MonitorSpec(val id: Long,
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

        var isMaximized: Boolean = false,
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
        var mouseClickHandler: (Int, Int, Int, Boolean) -> Unit = { button, x, y, isDown -> }

) {

    internal fun handleFrameInput(context: NkContext) {
        twr(stackPush()) { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)

            glfwGetWindowSize(id, w, h)
            currentWidth = w.get(0)
            currentHeight = h.get(0)

            glfwGetFramebufferSize(id, w, h)
            currentPixelWidth = w.get(0)
            currentPixelHeight = h.get(0)

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
        nk_input_begin(context)
        glfwPollEvents()
        val mouse = context.input().mouse()
        if (mouse.grab())
            glfwSetInputMode(id, GLFW_CURSOR, GLFW_CURSOR_HIDDEN)
        else if (mouse.grabbed()) {
            val prevX = mouse.prev().x()
            val prevY = mouse.prev().y()
            glfwSetCursorPos(id, prevX.toDouble(), prevY.toDouble())
            mouse.pos().x(prevX)
            mouse.pos().y(prevY)
        } else if (mouse.ungrab())
            glfwSetInputMode(id, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
        nk_input_end(context)
    }

    internal fun handleDragAndResize() {
        handleDragging()
        handleResizing()
    }

    private fun handleResizing() {
        if (isResizing) {
            isMaximized = false
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
}

private class NuklearContext internal constructor(
        val debugProc: Callback,
        var nullTexture: NkDrawNullTexture,
        val vertexLayout: NkDrawVertexLayoutElement.Buffer,
        val cmds: NkBuffer,
        var vbo: Int,
        var vao: Int,
        var ebo: Int,
        var prog: Int,
        var vertexShader: Int,
        var fragmentShader: Int,
        var uniform_tex: Int,
        var uniform_proj: Int,
        val allocator: NkAllocator,
        val nk: NkContext,
        val style: UiStyleInternal,
        val window: WindowContext,
        val nvg: Long) {

    internal fun close() {
        try {
            style.close()
        } finally {
            nk.clip().copy().free()
            nk.clip().paste().free()
            nk_buffer_free(cmds)
            nk_free(nk)
            allocator.alloc().free()
            allocator.mfree().free()
            glDetachShader(prog, vertexShader)
            glDetachShader(prog, fragmentShader)
            glDeleteShader(vertexShader)
            glDeleteShader(fragmentShader)
            glDeleteProgram(prog)
            glDeleteTextures(nullTexture.texture().id())
            glDeleteBuffers(vbo)
            glDeleteBuffers(ebo)
            Callbacks.glfwFreeCallbacks(window.id)
            debugProc.free()
            glfwTerminate()
            glfwSetErrorCallback(null).free()
        }
    }

    internal fun drawFrame(antiAliasing: Int = NK_ANTI_ALIASING_ON, maxVertexBuffer: Int = 524288, maxElementBuffer: Int = 131072) {
        twr(stackPush()) { stack ->
            // setup global state
            glEnable(GL_BLEND)
            GL14.glBlendEquation(GL14.GL_FUNC_ADD)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glDisable(GL_CULL_FACE)
            glDisable(GL_DEPTH_TEST)
            glEnable(GL_SCISSOR_TEST)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)

            // setup program
            glUseProgram(prog)
            glUniform1i(uniform_tex, 0)
            glUniformMatrix4fv(uniform_proj, false, stack.floats(
                    2.0f / window.currentWidth, 0.0f, 0.0f, 0.0f,
                    0.0f, -2.0f / window.currentHeight, 0.0f, 0.0f,
                    0.0f, 0.0f, -1.0f, 0.0f,
                    -1.0f, 1.0f, 0.0f, 1.0f
            ))
            glViewport(0, 0, window.currentPixelWidth, window.currentPixelHeight)
        }

        // convert from command queue into draw list and draw to screen

        // allocate vertex and element buffer
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)

        glBufferData(GL_ARRAY_BUFFER, maxVertexBuffer.toLong(), GL_STREAM_DRAW)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, maxElementBuffer.toLong(), GL_STREAM_DRAW)

        // load draw vertices & elements directly into vertex + element buffer
        val vertices = glMapBuffer(GL_ARRAY_BUFFER, GL_WRITE_ONLY, maxVertexBuffer.toLong(), null)
        val elements = glMapBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_WRITE_ONLY, maxElementBuffer.toLong(), null)
        twr(stackPush()) { stack ->
            // fill convert configuration
            val config = NkConvertConfig.callocStack(stack)
                    .vertex_layout(vertexLayout)
                    .vertex_size(20)
                    .vertex_alignment(4)
                    .null_texture(nullTexture)
                    .circle_segment_count(22)
                    .curve_segment_count(22)
                    .arc_segment_count(22)
                    .global_alpha(1.0f)
                    .shape_AA(antiAliasing)
                    .line_AA(antiAliasing)

            // setup buffers to load vertices and elements
            val vBuffer = NkBuffer.mallocStack(stack)
            val eBuffer = NkBuffer.mallocStack(stack)

            nk_buffer_init_fixed(vBuffer, vertices/*, max_vertex_buffer*/)
            nk_buffer_init_fixed(eBuffer, elements/*, max_element_buffer*/)
            nk_convert(nk, cmds, vBuffer, eBuffer, config)
        }
        glUnmapBuffer(GL_ELEMENT_ARRAY_BUFFER)
        glUnmapBuffer(GL_ARRAY_BUFFER)

        // iterate over and execute each draw command
        val fb_scale_x = window.currentPixelWidth.toFloat() / window.currentWidth.toFloat()
        val fb_scale_y = window.currentPixelHeight.toFloat() / window.currentHeight.toFloat()

        var offset = NULL
        var cmd: NkDrawCommand? = nk__draw_begin(nk, cmds)
        while (cmd != null) {
            if (cmd.elem_count() == 0) {
                cmd = nk__draw_next(cmd, cmds, nk)
                continue
            }
            glBindTexture(GL_TEXTURE_2D, cmd.texture().id())
            glScissor(
                    (cmd.clip_rect().x() * fb_scale_x).toInt(),
                    ((window.currentHeight - (cmd.clip_rect().y() + cmd.clip_rect().h()).toInt()) * fb_scale_y).toInt(),
                    (cmd.clip_rect().w() * fb_scale_x).toInt(),
                    (cmd.clip_rect().h() * fb_scale_y).toInt()
            )
            glDrawElements(GL_TRIANGLES, cmd.elem_count(), GL_UNSIGNED_SHORT, offset)
            offset += (cmd.elem_count() * 2).toLong()
            cmd = nk__draw_next(cmd, cmds, nk)
        }
        nk_clear(nk)

        // default OpenGL state
        glUseProgram(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
        glDisable(GL_BLEND)
        glDisable(GL_SCISSOR_TEST)
    }
}

private fun createNkContext(width: Int, height: Int, style: UiStyleInternal): NuklearContext {
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
    val nkShaderVersion = "#version 330\n"
    val vertex_shader = nkShaderVersion +
            "uniform mat4 ProjMtx;\n" +
            "in vec2 Position;\n" +
            "in vec2 TexCoord;\n" +
            "in vec4 Color;\n" +
            "out vec2 Frag_UV;\n" +
            "out vec4 Frag_Color;\n" +
            "void main() {\n" +
            "   Frag_UV = TexCoord;\n" +
            "   Frag_Color = Color;\n" +
            "   gl_Position = ProjMtx * vec4(Position.xy, 0, 1);\n" +
            "}\n"
    val fragment_shader = nkShaderVersion +
            "precision mediump float;\n" +
            "uniform sampler2D Texture;\n" +
            "in vec2 Frag_UV;\n" +
            "in vec4 Frag_Color;\n" +
            "out vec4 Out_Color;\n" +
            "void main(){\n" +
            "   Out_Color = Frag_Color * texture(Texture, Frag_UV.st);\n" +
            "}\n"
    val prog = glCreateProgram()
    val vertexShader = glCreateShader(GL_VERTEX_SHADER)
    val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(vertexShader, vertex_shader)
    glShaderSource(fragmentShader, fragment_shader)
    glCompileShader(vertexShader)
    glCompileShader(fragmentShader)
    if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) !== GL_TRUE) {
        throw IllegalStateException()
    }
    if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) !== GL_TRUE) {
        throw IllegalStateException()
    }
    glAttachShader(prog, vertexShader)
    glAttachShader(prog, fragmentShader)
    glLinkProgram(prog)
    if (glGetProgrami(prog, GL_LINK_STATUS) !== GL_TRUE) {
        throw IllegalStateException()
    }

    val uniformTex = glGetUniformLocation(prog, "Texture")
    val uniformProj = glGetUniformLocation(prog, "ProjMtx")
    val attribPos = glGetAttribLocation(prog, "Position")
    val attribUv = glGetAttribLocation(prog, "TexCoord")
    val attribCol = glGetAttribLocation(prog, "Color")

    val vbo = glGenBuffers()
    val ebo = glGenBuffers()
    val vao = GL30.glGenVertexArrays()

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)

    glEnableVertexAttribArray(attribPos)
    glEnableVertexAttribArray(attribUv)
    glEnableVertexAttribArray(attribCol)

    glVertexAttribPointer(attribPos, 2, GL11.GL_FLOAT, false, 20, 0)
    glVertexAttribPointer(attribUv, 2, GL11.GL_FLOAT, false, 20, 8)
    glVertexAttribPointer(attribCol, 4, GL11.GL_UNSIGNED_BYTE, true, 20, 16)

    val nullTexID = GL11.glGenTextures()

    val nullTexture = NkDrawNullTexture.create()
    nullTexture.texture().id(nullTexID)
    nullTexture.uv().set(0.5f, 0.5f)

    glBindTexture(GL11.GL_TEXTURE_2D, nullTexID)
    twr(stackPush()) { stack ->
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_INT_8_8_8_8_REV, stack.ints(0xFFFFFFFF.toInt()))
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)

    glBindTexture(GL11.GL_TEXTURE_2D, 0)
    glBindBuffer(GL_ARRAY_BUFFER, 0)
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
    glBindVertexArray(0)
    val window = WindowContext(id = windowId, monitors = monitors, warpLines = warpLines, width = width, height = height)
    val vertexLayout = NkDrawVertexLayoutElement.create(4)
            .position(0).attribute(NK_VERTEX_POSITION).format(NK_FORMAT_FLOAT).offset(0)
            .position(1).attribute(NK_VERTEX_TEXCOORD).format(NK_FORMAT_FLOAT).offset(8)
            .position(2).attribute(NK_VERTEX_COLOR).format(NK_FORMAT_R8G8B8A8).offset(16)
            .position(3).attribute(NK_VERTEX_ATTRIBUTE_COUNT).format(NK_FORMAT_COUNT).offset(0)
            .flip()
    val allocator = NkAllocator.create()
    allocator.alloc { handle, old, size ->
        val mem = MemoryUtil.nmemAlloc(size)
        if (mem == NULL) {
            throw OutOfMemoryError()
        }
        mem
    }
    allocator.mfree { handle, ptr -> MemoryUtil.nmemFree(ptr) }
    val cmds = NkBuffer.create()
    nk_buffer_init(cmds, allocator, 4096L)
    val context = NkContext.create()
    glfwSetScrollCallback(window.id) { windowId, xOffset, yOffset ->
        window.scrollX += xOffset.toFloat()
        window.scrollY += yOffset.toFloat()
        Nuklear.nk_input_scroll(context, yOffset.toFloat())
    }
    glfwSetCharCallback(window.id) { windowId, codePoint ->
        Nuklear.nk_input_unicode(context, codePoint)
    }
    glfwSetKeyCallback(window.id) { windowId, key, scanCode, action, mods ->
        val press = action == GLFW_PRESS
        val repeat = action == GLFW_REPEAT
        when (key) {
            GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(windowId, true)
            GLFW_KEY_DELETE -> nk_input_key(context, NK_KEY_DEL, press || repeat)
            GLFW_KEY_ENTER -> nk_input_key(context, NK_KEY_ENTER, press || repeat)
            GLFW_KEY_TAB -> nk_input_key(context, NK_KEY_TAB, press || repeat)
            GLFW_KEY_BACKSPACE -> nk_input_key(context, NK_KEY_BACKSPACE, press || repeat)
            GLFW_KEY_UP -> nk_input_key(context, NK_KEY_UP, press || repeat)
            GLFW_KEY_DOWN -> nk_input_key(context, NK_KEY_DOWN, press || repeat)
            GLFW_KEY_HOME -> {
                nk_input_key(context, NK_KEY_TEXT_START, press)
                nk_input_key(context, NK_KEY_SCROLL_START, press)
            }
            GLFW_KEY_END -> {
                nk_input_key(context, NK_KEY_TEXT_END, press)
                nk_input_key(context, NK_KEY_SCROLL_END, press)
            }
            GLFW_KEY_PAGE_DOWN -> nk_input_key(context, NK_KEY_SCROLL_DOWN, press)
            GLFW_KEY_PAGE_UP -> nk_input_key(context, NK_KEY_SCROLL_UP, press)
            GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> nk_input_key(context, NK_KEY_SHIFT, press)
            GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> if (press) {
                nk_input_key(context, NK_KEY_COPY, glfwGetKey(windowId, GLFW_KEY_C) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_PASTE, glfwGetKey(windowId, GLFW_KEY_P) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_CUT, glfwGetKey(windowId, GLFW_KEY_X) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_TEXT_UNDO, glfwGetKey(windowId, GLFW_KEY_Z) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_TEXT_REDO, glfwGetKey(windowId, GLFW_KEY_R) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_TEXT_WORD_LEFT, glfwGetKey(windowId, GLFW_KEY_LEFT) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_TEXT_WORD_RIGHT, glfwGetKey(windowId, GLFW_KEY_RIGHT) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_TEXT_LINE_START, glfwGetKey(windowId, GLFW_KEY_B) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_TEXT_LINE_END, glfwGetKey(windowId, GLFW_KEY_E) == GLFW_PRESS)
            } else {
                nk_input_key(context, NK_KEY_LEFT, glfwGetKey(windowId, GLFW_KEY_LEFT) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_RIGHT, glfwGetKey(windowId, GLFW_KEY_RIGHT) == GLFW_PRESS)
                nk_input_key(context, NK_KEY_COPY, false)
                nk_input_key(context, NK_KEY_PASTE, false)
                nk_input_key(context, NK_KEY_CUT, false)
                nk_input_key(context, NK_KEY_SHIFT, false)
            }
        }
    }
    glfwSetCursorPosCallback(window.id) { windowId, x, y ->
        if (!window.isDragging && !window.isResizing) {
            Nuklear.nk_input_motion(context, x.toInt(), y.toInt())
        }
    }
    glfwSetMouseButtonCallback(window.id) { windowId, button, action, mods ->
        twr(stackPush()) { stack ->
            val cx = stack.mallocDouble(1)
            val cy = stack.mallocDouble(1)
            glfwGetCursorPos(windowId, cx, cy)
            val x = cx.get(0)
            val y = cy.get(0)
            if (y >= style.dragAreaTopMargin
                    && y < style.dragAreaTopMargin + style.dragAreaHeight
                    && x >= style.dragAreaLeftMargin
                    && x < window.currentWidth - style.dragAreaRightMargin
                    && button == GLFW_MOUSE_BUTTON_LEFT
                    && action == GLFW_PRESS) {
                startDrag(stack, window)
                handleStandardMouseAction(context, window, action, button, x, y)
            } else if ((y >= window.currentHeight - window.resizeAreaHeight && y <= window.currentHeight)
                    && ((x >= window.currentWidth - window.resizeAreaWidth && x <= window.currentWidth) || (x >= 0 && x <= window.resizeAreaWidth))
                    && (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS)) {
                startResize(stack, window, x <= window.resizeAreaWidth)
                handleStandardMouseAction(context, window, action, button, x, y)
            } else {
                handleStandardMouseAction(context, window, action, button, x, y)
            }
        }
    }
    nk_init(context, allocator, null)
    setCopyHandler(context, window.id)
    setPastHandler(context, window.id)
    initializeWindowState(window)
    return NuklearContext(debugProc, nullTexture, vertexLayout, cmds, vbo, vao, ebo, prog, vertexShader, fragmentShader, uniformTex, uniformProj, allocator, context, style, window, nvg)
}

private fun handleStandardMouseAction(context: NkContext, window: WindowContext, action: Int, button: Int, x: Double, y: Double) {
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
    val nkButton: Int
    when (button) {
        GLFW_MOUSE_BUTTON_RIGHT -> nkButton = NK_BUTTON_RIGHT
        GLFW_MOUSE_BUTTON_MIDDLE -> nkButton = NK_BUTTON_MIDDLE
        else -> nkButton = NK_BUTTON_LEFT
    }
    nk_input_button(context, nkButton, Math.round(x).toInt(), Math.round(y).toInt(), action == GLFW_PRESS)
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

private fun setPastHandler(context: NkContext, windowId: Long) {
    context.clip().paste { handle, edit ->
        val string = nglfwGetClipboardString(windowId)
        if (string != NULL) {
            nnk_textedit_paste(edit, string, nnk_strlen(string))
        }
    }
}

private fun setCopyHandler(context: NkContext, windowId: Long) {
    context.clip().copy { handle, text, length ->
        if (length > 0) {
            twr(stackPush()) { stack ->
                val string = stack.malloc(length + 1)
                MemoryUtil.memCopy(text, MemoryUtil.memAddress(string), length)
                string.put(length, 0.toByte())
                glfwSetClipboardString(windowId, string)
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
