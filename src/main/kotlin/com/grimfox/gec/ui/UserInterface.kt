package com.grimfox.gec.ui

import com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.grimfox.gec.WindowState
import com.grimfox.gec.executor
import com.grimfox.gec.saveRecentProjects
import com.grimfox.gec.saveWindowState
import com.grimfox.gec.ui.nvgproxy.NPColor
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWDropCallback
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.ARBDebugOutput
import org.lwjgl.opengl.GL.createCapabilities
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_MULTISAMPLE
import org.lwjgl.opengl.GL43
import org.lwjgl.opengl.GLUtil.setupDebugMessageCallback
import org.lwjgl.opengl.KHRDebug
import org.lwjgl.system.Callback
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintStream
import java.lang.Math.round
import java.lang.Thread.sleep
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.*

import com.grimfox.gec.ui.nvgproxy.*

val LOG: Logger = LoggerFactory.getLogger(UserInterface::class.java)
val JSON: ObjectMapper = jacksonObjectMapper().setSerializationInclusion(ALWAYS).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private val isMac = System.getProperty("os.name").toLowerCase().contains("mac")
private val screenInfoFetcher = if (isMac) MacScreenInfoFetcher() else WindowsScreenInfoFetcher()

fun layout(block: UiLayout.(UserInterface) -> Unit) = block

fun ui(layoutBlock: UiLayout.(UserInterface) -> Unit, windowState: WindowState? = null, beforeDraw: UserInterface.() -> Unit = {}, afterDraw: UserInterface.() -> Unit = {}) {
    val ui = UserInterfaceInternal(createWindow(windowState))
    try {
        ui.layout.layoutBlock(ui)
        ui.mouseClickHandler = { button, x, y, isDown, mods ->
            ui.root.handleMouseAction(button, x, y, isDown, mods)
        }
        ui.scrollHandler = { x, y ->
            ui.root.handleScroll(x, y)
        }
        ui.dropHandler = { strings ->
            ui.root.handleDrop(strings)
        }
        ui.show()
        TextureBuilder.init(ui.nvg)
        executor.call { Biomes.init() }
        while (!ui.shouldClose()) {
            TextureBuilder.onDrawFrame()
            ui.handleFrameInput()
            val needsRefresh = ui.handleDragAndResize()
            if (needsRefresh) {
                ui.updatePositionAndSize()
            }
            val frameWidth = ui.width
            val frameHeight = ui.height
            ui.clearViewport(frameWidth, frameHeight)
            ui.beforeDraw()
            ui.drawFrame(frameWidth, frameHeight)
            ui.afterDraw()
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

    val background: NPColor

    var root: Block

    var dragArea: Block

    var resizeAreaSouthEast: Block

    var resizeAreaSouthWest: Block

    fun createFont(resource: String, name: String): Int

    fun createGlfwImage(resource: String): GLFWImage

    fun createGlfwImages(vararg resources: String): GLFWImage.Buffer

//    fun createImage(resource: String, options: Int): Int
//
    fun createImage(textureHandle: Int, width: Int, height: Int, options: Int): Int

    fun createMultiGlyph(vararg glyphs: GlyphLayer): Block.() -> Block

    fun createCaret(text: DynamicTextReference): Caret

    fun root(builder: Block.() -> Unit) {
        root = uiRoot(0.0f, 0.0f, 0.0f, 0.0f, builder)
    }
}

class KeyboardHandler(var onChar: ((codePoint: Int) -> Unit)?, var onKey: ((key: Int, scanCode: Int, action: Int, mods: Int) -> Unit)?)

class HotkeyHandler(var onKey: ((key: Int, scanCode: Int, action: Int, mods: Int) -> Boolean)?)

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
    val isResizing: Boolean
    var ignoreInput: Boolean
    var mouseClickHandler: (button: Int, x: Int, y: Int, isDown: Boolean, mods: Int) -> Unit
    var scrollHandler: (x: Double, y: Double) -> Unit
    var maximizeHandler: () -> Unit
    var minimizeHandler: () -> Unit
    var restoreHandler: () -> Unit
    var hotkeyHandler: HotkeyHandler?
    var keyboardHandler: KeyboardHandler?
    var dropHandler: (List<String>) -> Unit

    fun setWindowIcon(images: GLFWImage.Buffer)

    fun show()

    fun hide()

    fun saveWindowState()

    fun closeWindow()

    fun minimizeWindow()

    fun toggleMaximized()

    fun maximizeWindow()

    fun restoreWindow()

    fun setCursor(cursor: Long)

    fun disableCursor()

    fun enableCursor()

    fun hideCursor()

    fun showCursor()

    fun getClipboardString(): String

    fun setClipboardString(string: String)

    operator fun invoke(block: UserInterface.() -> Unit) {
        this.block()
    }
}

private fun getMousePosition(window: WindowContext): Pair<Int, Int> {
    return getMousePosition(window.id, window.x, window.y)
}

private fun getMousePosition(windowId: Long, windowX: Int, windowY: Int): Pair<Int, Int> {
    twr(stackPush()) { stack ->
        val x = stack.mallocDouble(1)
        val y = stack.mallocDouble(1)
        glfwGetCursorPos(windowId, x, y)
        val newMouseX = round(windowX + x[0]).toInt()
        val newMouseY = round(windowY + y[0]).toInt()
        return Pair(newMouseX, newMouseY)
    }
}

private fun getScreens(): LinkedHashMap<ScreenIdentity, ScreenSpec> {
    return screenInfoFetcher.getScreens()
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
    override val isResizing: Boolean get() = window.isResizing
    override var ignoreInput: Boolean
        get() = window.ignoreInput
        set(value) {
            window.ignoreInput = value
        }
    override var mouseClickHandler: (Int, Int, Int, Boolean, Int) -> Unit
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

    override var hotkeyHandler: HotkeyHandler?
        get() = window.hotkeyHandler
        set(value) {
            window.hotkeyHandler = value
        }

    override var keyboardHandler: KeyboardHandler?
        get() = window.keyboardHandler
        set(value) {
            window.keyboardHandler = value
        }

    override var dropHandler: (List<String>) -> Unit
        get() = window.dropHandler
        set(value) {
            window.dropHandler = value
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

    override fun saveWindowState() {
        saveRecentProjects()
        if (isMaximized) {
            saveWindowState(WindowState(window.restoreX, window.restoreY, Math.round(window.width / window.currentMonitor.scaleFactor).toInt(), Math.round(window.height / window.currentMonitor.scaleFactor).toInt(), true, window.currentMonitorIndex))
        } else {
            saveWindowState(WindowState(window.x, window.y, Math.round(window.currentWidth / window.currentMonitor.scaleFactor).toInt(), Math.round(window.currentHeight / window.currentMonitor.scaleFactor).toInt(), false, window.currentMonitorIndex))
        }
    }

    override fun closeWindow() {
        saveWindowState()
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

    override fun setCursor(cursor: Long) {
        glfwSetCursor(window.id, cursor)
    }

    override fun toggleMaximized() {
        if (isMaximized) {
            restoreWindow()
        } else {
            maximizeWindow()
        }
    }

    override fun disableCursor() {
        window.disableCursor()
    }

    override fun enableCursor() {
        window.enableCursor()
    }

    override fun getClipboardString(): String {
        return window.getClipboardString()
    }

    override fun hideCursor() {
        window.hideCursor()
    }

    override fun showCursor() {
        window.showCursor()
    }

    override fun setClipboardString(string: String) {
        window.setClipboardString(string)
    }

    internal fun close() {
        window.close()
    }

    internal fun handleFrameInput() {
        window.handleFrameInput()
    }

    internal fun handleDragAndResize(): Boolean {
        return window.handleDragAndResize()
    }

    internal fun updatePositionAndSize() {
        window.updatePositionAndSize()
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
        if (isMac) {
            glViewport(0, 0, window.currentPixelWidth, window.currentPixelHeight)
            glEnable(GL_MULTISAMPLE)
            nvgSave(nvg)
            val scale = if (window.isResizing) {
                window.resizeScaleFactor
            } else {
                clamp(Math.round((Math.round((window.currentPixelWidth / window.currentWidth) * 4.0) / 4.0) * 100.0) / 100.0, 1.0, 2.5).toFloat()
            }
            root.width = width.toFloat()
            root.height = height.toFloat()
            root.handleNewMousePosition(nvg, relativeMouseX, relativeMouseY)
            nvgBeginFrame(nvg, window.currentPixelWidth, window.currentPixelHeight, 1.0f)
            root.draw(nvg, scale)
            glViewport(0, 0, width, height)
            glViewport(0, 0, window.currentPixelWidth, window.currentPixelHeight)
        } else {
            glViewport(0, 0, width, height)
            glEnable(GL_MULTISAMPLE)
            nvgSave(nvg)
            val scale = clamp(Math.round((Math.round((window.currentMonitor.scaleFactor * window.currentMonitor.overRender) * 4.0) / 4.0) * 100.0) / 100.0, 1.0, 2.5).toFloat()
            root.width = width / scale
            root.height = height / scale
            root.handleNewMousePosition(nvg, Math.round(relativeMouseX / scale), Math.round(relativeMouseY / scale))
            nvgBeginFrame(nvg, width, height, pixelWidth / width.toFloat())
            root.draw(nvg, scale)
            glViewport(0, 0, width, height)
        }
        nvgEndFrame(nvg)
        nvgRestore(nvg)
    }

    internal fun swapBuffers() {
        glfwSwapBuffers(window.id)
    }
}

data class GlyphLayer(val text: String, val font: Reference<Int>, val size: Float, val color: NPColor, val xOffset: Float, val yOffset: Float)

private class UiLayoutInternal internal constructor(val nvg: Long) : UiLayout {

    internal val fonts: ArrayList<ByteBuffer> = ArrayList()

    override val background: NPColor = NO_COLOR

    override lateinit var root: Block
    override lateinit var dragArea: Block
    override lateinit var resizeAreaSouthEast: Block
    override lateinit var resizeAreaSouthWest: Block

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

    override fun createImage(textureHandle: Int, width: Int, height: Int, options: Int): Int {
        return nvglCreateImageFromHandle(nvg, textureHandle, width, height, options)
    }

    private data class ResolvedGlyphLayer(val text: Text, var xOffset: Float, var yOffset: Float, var width: Float, var height: Float)

    private fun glyphStyle(font: Int, size: Float, color: NPColor): TextStyle {
        return TextStyle(cRef(size), cRef(font), cRef(color))
    }

    private fun glyph(value: String, font: Int, size: Float, color: NPColor): Text {
        return StaticTextUtf8(value, glyphStyle(font, size, color))
    }

    override fun createMultiGlyph(vararg glyphs: GlyphLayer): Block.() -> Block {
        val resolvedGlyphs = ArrayList<ResolvedGlyphLayer>()
        var minXOffset = 0.0f
        var minYOffset = 0.0f
        glyphs.forEach {
            val text = glyph(it.text, it.font.value, it.size, it.color)
            val (width, height) = text.dimensions(nvg)
            resolvedGlyphs.add(ResolvedGlyphLayer(text, it.xOffset, it.yOffset, width, height))
            if (it.xOffset < minXOffset) {
                minXOffset = it.xOffset
            }
            if (it.yOffset < minYOffset) {
                minYOffset = it.yOffset
            }
        }
        var maxWidth = 0.0f
        var maxHeight = 0.0f
        resolvedGlyphs.forEach {
            it.xOffset -= minXOffset
            it.yOffset -= minYOffset
            val width = it.xOffset + it.width
            val height = it.yOffset + it.height
            if (width > maxWidth) {
                maxWidth = width
            }
            if (height > maxHeight) {
                maxHeight = height
            }
        }
        return {
            block {
                hSizing = Sizing.STATIC
                vSizing = Sizing.STATIC
                width = maxWidth
                height = maxHeight
                canOverflow = true
                resolvedGlyphs.forEach {
                    block {
                        canOverflow = true
                        hSizing = Sizing.STATIC
                        vSizing = Sizing.STATIC
                        xOffset = it.xOffset
                        yOffset = it.yOffset
                        width = it.width
                        height = it.height
                        text = it.text
                    }
                }
            }
        }
    }

    override fun createCaret(text: DynamicTextReference): Caret {
        return Caret(nvg, text)
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

internal data class ScreenIdentity(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int)

internal data class WarpLine(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val warpX: Int,
        val warpY: Int)

internal data class ScreenSpec(
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

        val debugProc: Callback?,
        val nvg: Long,
        val layout: UiLayoutInternal = UiLayoutInternal(nvg),

        var isMaximized: Boolean = false,
        var isMinimized: Boolean = false,
        var isResizing: Boolean = false,
        var isDragging: Boolean = false,
        var hasMoved: Boolean = false,

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

        var width: Int = 1280,
        var height: Int = 720,

        var resizeMouseStartX: Int = 0,
        var resizeMouseStartY: Int = 0,

        var resizeWindowStartX: Int = 0,
        var resizeWindowStartY: Int = 0,

        var resizeMove: Boolean = false,

        var resizeScaleFactor: Float = 1.0f,

        var resizeWindowStartWidth: Int = 0,
        var resizeWindowStartHeight: Int = 0,

        var currentWidth: Int = width,
        var currentHeight: Int = height,

        var currentPixelWidth: Int = width,
        var currentPixelHeight: Int = height,

        var restoreX: Int = 0,
        var restoreY: Int = 0,

        var monitors: List<MonitorSpec> = emptyList(),

        var ignoreInput: Boolean = false,

        var currentMonitor: MonitorSpec = NO_MONITOR,
        var mouseClickHandler: (Int, Int, Int, Boolean, Int) -> Unit = { _, _, _, _, _ -> },
        var scrollHandler: (Double, Double) -> Unit = { _, _ -> },
        var maximizeHandler: () -> Unit = {},
        var minimizeHandler: () -> Unit = {},
        var restoreHandler: () -> Unit = {},

        var hotkeyHandler: HotkeyHandler? = null,
        var keyboardHandler: KeyboardHandler? = null,

        var dropHandler: (List<String>) -> Unit = {}

) {

    val currentMonitorIndex: Int
        get() {
            val index = monitors.indexOf(currentMonitor)
            if (index > 0) {
                return index
            } else {
                return 0
            }
        }

    internal fun handleFrameInput() {
        if (!ignoreInput) {
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

                glfwGetWindowPos(id, w, h)
                x = w[0]
                y = h[0]
                val (newMouseX, newMouseY) = getMousePosition(this@WindowContext)
                mouseX = newMouseX
                mouseY = newMouseY
                relativeMouseX = mouseX.toDouble() - x
                relativeMouseY = mouseY.toDouble() - y
            }
        }
        glfwPollEvents()
    }

    internal fun updatePositionAndSize() {
        twr(stackPush()) { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            glfwGetWindowSize(id, w, h)
            currentWidth = w.get(0)
            currentHeight = h.get(0)
            glfwGetFramebufferSize(id, w, h)
            currentPixelWidth = w.get(0)
            currentPixelHeight = h.get(0)
            glfwGetWindowPos(id, w, h)
            x = w[0]
            y = h[0]
        }
    }

    internal fun handleDragAndResize(): Boolean {
        if (!ignoreInput) {
            handleDragging()
            return handleResizing()
        }
        return false
    }

    private fun handleResizing(): Boolean {
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
                if (resizeMove || isMac) {
                    if (resizeMove) {
                        x -= resizeX
                        restoreX = x
                    }
                    glfwSetWindowPos(id, x, y)
                    return true
                }
            }
        }
        return false
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
                if (!isMac && newWindowY < currentMonitor.mouseSpaceY1) {
                    if (hasMoved && (newWindowY <= currentMonitor.mouseSpaceY1 - 15 || mouseY < currentMonitor.mouseSpaceY1 + 0.1)) {
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
                        monitors.forEachIndexed { _, monitorSpec ->
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
        if (isMac) {
            if (isMaximized) {
                glfwRestoreWindow(id)
                isMaximized = false
                restoreHandler()
                restoreX
            }
            return x
        } else {
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
    }

    internal fun maximize() {
        if (!isMaximized) {
            restoreX = x
            restoreY = y
            if (isMac) {
                glfwMaximizeWindow(id)
            } else {
                glfwSetWindowPos(id, currentMonitor.maximizedX1, currentMonitor.maximizedY1)
                glfwSetWindowSize(id, currentMonitor.maximizedWidth, currentMonitor.maximizedHeight)
            }
            isMaximized = true
            maximizeHandler()
        }
    }

    internal fun close() {
        try {
            layout.close()
        } finally {
            Callbacks.glfwFreeCallbacks(id)
            debugProc?.free()
            glfwTerminate()
            glfwSetErrorCallback(null).free()
        }
    }

    internal fun disableCursor() {
        glfwSetInputMode(id, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
    }

    internal fun enableCursor() {
        glfwSetInputMode(id, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
    }

    internal fun hideCursor() {
        glfwSetInputMode(id, GLFW_CURSOR, GLFW_CURSOR_HIDDEN)
    }

    internal fun showCursor() {
        glfwSetInputMode(id, GLFW_CURSOR, GLFW_CURSOR_NORMAL)
    }

    fun getClipboardString(): String {
        return glfwGetClipboardString(id)
    }

    fun setClipboardString(string: String) {
        glfwSetClipboardString(id, string)
    }
}

private fun createWindow(windowState: WindowState?): WindowContext {
    val width = windowState?.width ?: 1280
    val height = windowState?.height ?: 720
    val currentStateMonitor = windowState?.monitorIndex
    GLFWErrorCallback.createPrint().set()
    if (!glfwInit()) throw IllegalStateException("Unable to initialize glfw")
    val screens = getScreens()
    val (monitors, currentMouseMonitor) = getMonitorInfo(screens)
    val currentMonitor = if (currentStateMonitor != null && monitors.size > currentStateMonitor) {
        monitors[currentStateMonitor]
    } else {
        currentMouseMonitor
    }
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
    val windowId = glfwCreateWindow(width, height, "WorldKit", NULL, NULL)
    if (windowId == NULL) throw RuntimeException("Failed to create the GLFW window")
    glfwSetWindowPos(windowId, windowState?.x ?: (currentMonitor.centerX - width / 2 + 1), windowState?.y ?: (currentMonitor.centerY - height / 2 + 1))
    glfwMakeContextCurrent(windowId)
    Configuration.DEBUG.set(true)
    val errorStream = object : PrintStream(System.err) {

        val buffer = StringBuffer()

        override fun println(x: String?) {
            if (buffer.isNotEmpty()) {
                LOG.error(buffer.toString().trim())
                buffer.delete(0, buffer.length)
            }
            buffer.append("$x\n")
        }

        override fun printf(format: String, vararg args: Any?): PrintStream {
            val string = String.format(format, *args)
            buffer.append(string)
            return this
        }

        override fun printf(l: Locale, format: String, vararg args: Any?): PrintStream {
            val string = String.format(l, format, *args)
            buffer.append(string)
            return this
        }

        init {
            Runtime.getRuntime().addShutdownHook(Thread({ println("") }))
        }
    }
    Configuration.DEBUG_STREAM.set(errorStream)
    val caps = createCapabilities()
    val debugProc = setupDebugMessageCallback(errorStream)
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
    val nvg = nvgCreate(NVG_STENCIL_STROKES)
    if (nvg == NULL) {
        throw RuntimeException("Could not init nanovg.")
    }
    glfwSwapInterval(1)
    val window = WindowContext(id = windowId, debugProc = debugProc, nvg = nvg, monitors = monitors, width = width, height = height)
    glfwSetScrollCallback(window.id) { _, xOffset, yOffset ->
        if (!window.ignoreInput) {
            try {
                window.scrollX += xOffset.toFloat()
                window.scrollY += yOffset.toFloat()
                window.scrollHandler(xOffset, yOffset)
            } catch (t: Throwable) {
                LOG.error("fatal error", t)
                println("")
                sleep(1000)
                throw t
            }
        }
    }
    glfwSetCharCallback(window.id) { _, codePoint ->
        if (!window.ignoreInput) {
            window.keyboardHandler?.onChar?.invoke(codePoint)
        }
    }
    glfwSetKeyCallback(window.id) { _, key, scanCode, action, mods ->
        if (!window.ignoreInput) {
            try {
                if (!(window.hotkeyHandler?.onKey?.invoke(key, scanCode, action, mods) ?: false)) {
                    window.keyboardHandler?.onKey?.invoke(key, scanCode, action, mods)
                }
            } catch (t: Throwable) {
                LOG.error("fatal error", t)
                println("")
                sleep(1000)
                throw t
            }
        }
    }
    glfwSetMouseButtonCallback(window.id) { id, button, action, mods ->
        if (!window.ignoreInput) {
            try {
                twr(stackPush()) { stack ->
                    val cx = stack.mallocDouble(1)
                    val cy = stack.mallocDouble(1)
                    glfwGetCursorPos(id, cx, cy)
                    val x = cx.get(0)
                    val y = cy.get(0)
                    val scale = if (isMac) {
                        1.0
                    } else {
                        clamp(Math.round((Math.round((window.currentMonitor.scaleFactor * window.currentMonitor.overRender) * 4.0) / 4.0) * 100.0) / 100.0, 1.0, 2.5)
                    }
                    if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS && mouseIsWithin(window.layout.dragArea, scale, x, y)) {
                        startDrag(stack, window)
                    } else if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS && mouseIsWithin(window.layout.resizeAreaSouthEast, scale, x, y)) {
                        startResize(stack, window, false)
                    } else if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS && mouseIsWithin(window.layout.resizeAreaSouthWest, scale, x, y)) {
                        startResize(stack, window, true)
                    }
                    handleStandardMouseAction(window, action, button, x, y, mods, scale)
                }
            } catch (t: Throwable) {
                LOG.error("fatal error", t)
                println("")
                sleep(1000)
                throw t
            }
        }
    }
    glfwSetDropCallback(window.id) { _, count, address ->
        val strings = (0..count - 1).mapTo(ArrayList<String>(count)) { GLFWDropCallback.getName(address, it) }
        window.dropHandler.invoke(strings)
    }
    var windowSize = getWindowSize(windowId)
    if (windowSize.first != width && windowSize.second != height) {
        glfwSetWindowSize(windowId, width, height)
    }
    windowSize = getWindowSize(windowId)
    LOG.info("Created window with width: ${windowSize.first}, height: ${windowSize.second}")
    initializeWindowState(window, windowState)
    return window
}

private fun mouseIsWithin(area: Block, scale: Double, xRaw: Double, yRaw: Double): Boolean {
    val x = xRaw / scale
    val y = yRaw / scale
    val dragAreaX1 = round(area.x)
    val dragAreaX2 = round(dragAreaX1 + area.width)
    val dragAreaY1 = round(area.y)
    val dragAreaY2 = round(dragAreaY1 + area.height)
    return y >= dragAreaY1 && y < dragAreaY2 && x >= dragAreaX1 && x < dragAreaX2
}

private fun handleStandardMouseAction(window: WindowContext, action: Int, button: Int, x: Double, y: Double, mods: Int, scale: Double) {
    if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_RELEASE) {
        window.isDragging = false
        window.hasMoved = false
        window.isResizing = false
    }
    if (button == GLFW_MOUSE_BUTTON_LEFT) {
        if (action == GLFW_PRESS) {
            window.isMouse1Down = true
        } else if (action == GLFW_RELEASE) {
            window.isMouse1Down = false
        }
    } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
        if (action == GLFW_PRESS) {
            window.isMouse2Down = true
        } else if (action == GLFW_RELEASE) {
            window.isMouse2Down = false
        }
    }
    window.mouseClickHandler(button, Math.round(x / scale).toInt(), Math.round(y / scale).toInt(), action == GLFW_PRESS, mods)
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
    val (newMouseX, newMouseY) = getMousePosition(window)
    window.mouseX = newMouseX
    window.mouseY = newMouseY
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
    window.resizeScaleFactor = clamp(Math.round((Math.round((window.currentPixelWidth / window.currentWidth) * 4.0) / 4.0) * 100.0) / 100.0, 1.0, 2.5).toFloat()
    val (newMouseX, newMouseY) = getMousePosition(window)
    window.mouseX = newMouseX
    window.mouseY = newMouseY
    window.resizeMouseStartX = window.mouseX
    window.resizeMouseStartY = window.mouseY
    window.resizeWindowStartX = window.x
    window.resizeWindowStartY = window.y
    window.resizeMove = moveWithResize
    window.resizeWindowStartWidth = window.width
    window.resizeWindowStartHeight = window.height
}


private fun initializeWindowState(window: WindowContext, windowState: WindowState?) {
    twr(stackPush()) { stack ->
        val x = stack.mallocInt(1)
        val y = stack.mallocInt(1)
        glfwGetWindowPos(window.id, x, y)
        window.x = x[0]
        window.y = y[0]
        window.restoreX = window.x
        window.restoreY - window.y
        val (newMouseX, newMouseY) = getMousePosition(window)
        window.mouseX = newMouseX
        window.mouseY = newMouseY
        window.relativeMouseX = window.mouseX.toDouble() - window.x
        window.relativeMouseY = window.mouseY.toDouble() - window.y
        glfwGetWindowSize(window.id, x, y)
        window.currentWidth = x.get(0)
        window.currentHeight = y.get(0)
        window.width = window.currentWidth
        window.height = window.currentHeight
        window.monitors.forEach { monitorSpec ->
            if (window.x >= monitorSpec.mouseSpaceX1 && window.x <= monitorSpec.mouseSpaceX2 && window.y >= monitorSpec.mouseSpaceY1 && window.y <= monitorSpec.mouseSpaceY2) {
                adjustForCurrentMonitor(monitorSpec, window)
            }
        }
        glfwSetWindowSize(window.id, window.currentWidth, window.currentHeight)
        if (windowState?.isMaximized ?: false) {
            window.maximize()
        }
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

internal interface ScreenInfoFetcher {

    fun getScreens(): LinkedHashMap<ScreenIdentity, ScreenSpec>
}

internal class MacScreenInfoFetcher: ScreenInfoFetcher {

    override fun getScreens(): LinkedHashMap<ScreenIdentity, ScreenSpec> {
        return LinkedHashMap()
    }
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
            var screen: ScreenSpec? = screens[ScreenIdentity(virtualX, virtualY, virtualWidth, virtualHeight)]
            if (screen == null && screens.isNotEmpty()) {
                screen = screens.entries.first().value
            }
            if (screen != null) {
//                val avgDpi = (dpiX + dpiY) / 2.0
//                screen.scaleFactor = (Math.round((Math.round((avgDpi / 100.0) * 4.0) / 4.0) * 100.0) / 100.0) / screen.scaleFactor
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
            } else {
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
                        mouseSpaceX1 = virtualX,
                        mouseSpaceY1 = virtualY,
                        mouseSpaceX2 = virtualX + virtualWidth,
                        mouseSpaceY2 = virtualY + virtualHeight,
                        mouseSpaceWidth = virtualWidth,
                        mouseSpaceHeight = virtualHeight,
                        maximizedWidth = virtualWidth,
                        maximizedHeight = virtualHeight,
                        maximizedX1 = virtualX,
                        maximizedY1 = virtualY,
                        maximizedX2 = virtualX + virtualWidth,
                        maximizedY2 = virtualY + virtualHeight,
                        scaleFactor = 1.0,
                        overRender = 1.0,
                        redBits = redBits,
                        greenBits = greenBits,
                        blueBits = blueBits,
                        refreshRate = refreshRate))
            }
        }
        currentMonitor = monitors[0]
        LOG.info(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Pair(screens, monitors)))
    }
    return Pair(monitors, currentMonitor)
}
