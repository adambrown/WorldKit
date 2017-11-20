package com.grimfox.gec.ui.widgets

import com.grimfox.gec.model.ObservableMutableList
import com.grimfox.gec.ui.nvgproxy.*
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.HorizontalTruncation.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.ui.widgets.VerticalTruncation.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.Utils.LOG
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memAddress
import java.lang.Math.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

enum class HorizontalAlignment {
    LEFT,
    RIGHT,
    CENTER
}

enum class VerticalAlignment {
    TOP,
    BOTTOM,
    MIDDLE
}

enum class HorizontalTruncation {
    TRUNCATE_LEFT,
    TRUNCATE_RIGHT,
    TRUNCATE_CENTER
}

enum class VerticalTruncation {
    TRUNCATE_TOP,
    TRUNCATE_BOTTOM,
    TRUNCATE_MIDDLE
}

enum class Layout {
    ABSOLUTE,
    HORIZONTAL,
    VERTICAL
}

enum class Sizing {
    STATIC,
    SHRINK,
    SHRINK_GROUP,
    GROW,
    RELATIVE
}

interface Fill {

    fun draw(nvg: Long, block: Block, scale: Float)
}

private class FillNone : Fill {

    override fun draw(nvg: Long, block: Block, scale: Float) {
    }
}

class FillColor(val color: NPColor) : Fill {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        nvgFillColor(nvg, color)
        nvgFill(nvg)
    }
}

class FillImage(private val image: Int) : Fill {

    private val paint = NPPaint.create()

    override fun draw(nvg: Long, block: Block, scale: Float) {
        nvgImagePattern(nvg, Math.round(block.x * scale).toFloat(), Math.round(block.y * scale).toFloat(), Math.round(block.width * scale).toFloat(), Math.round(block.height * scale).toFloat(), 0.0f, image, 1.0f, paint)
        nvgFillPaint(nvg, paint)
        nvgFill(nvg)
    }
}

class FillBoxGradient(private val innerColor: NPColor, private val outerColor: NPColor, private val cornerRadius: Float, private val feather: Float) : Fill {

    private val paint = NPPaint.create()

    override fun draw(nvg: Long, block: Block, scale: Float) {
        nvgBoxGradient(nvg, block.x * scale, block.y * scale, block.width * scale, block.height * scale, cornerRadius, feather, innerColor, outerColor, paint)
        nvgFillPaint(nvg, paint)
        nvgFill(nvg)
    }
}

interface Stroke {

    val size: Float

    fun draw(nvg: Long, block: Block, scale: Float)
}

private class StrokeNone : Stroke {

    override val size = 0.0f

    override fun draw(nvg: Long, block: Block, scale: Float) {
    }
}

class StrokeColor(val color: NPColor, override val size: Float) : Stroke {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        nvgStrokeColor(nvg, color)
        nvgStrokeWidth(nvg, size * scale)
        nvgStroke(nvg)
    }
}

open class TextStyle(open val size: Reference<Float>, open val font: Reference<Int>, open val color: Reference<NPColor>)

interface Text {

    var style: TextStyle
    val data: ByteBuffer
    val length: Int

    fun draw(nvg: Long, block: Block, scale: Float)

    fun width(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Float

    fun height(scale: Float): Float

    fun dimensions(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Pair<Float, Float>
}

private class TextNone : Text {

    override var style: TextStyle = TextStyle(cRef(0.0f), cRef(-1), cRef(NO_COLOR))
    override val data: ByteBuffer = ByteBuffer.wrap(ByteArray(0))
    override val length: Int = 0

    override fun draw(nvg: Long, block: Block, scale: Float) {
    }

    override fun width(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Float = 0.0f

    override fun height(scale: Float): Float = 0.0f

    override fun dimensions(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Pair<Float, Float> = 0.0f to 0.0f
}

open class DynamicTextParagraphUtf8(override val data: ByteBuffer, private val verticalSpace: Float, override var style: TextStyle) : Text {

    override val length: Int get() = data.limit()
    private val lineHeight = BufferUtils.createFloatBuffer(1)
    private val rows = NPTextRow.create(3)

    override fun draw(nvg: Long, block: Block, scale: Float) {
        var (x , y, alignMask) = calculatePositionAndAlignmentForText(block)
        nvgFontFaceId(nvg, style.font.value)
        nvgFontSize(nvg, style.size.value * scale)
        nvgTextAlign(nvg, alignMask)
        nvgTextMetrics(nvg, null, null, lineHeight)
        val scaledHeight = lineHeight.get(0) + (verticalSpace * scale)
        nvgFillColor(nvg, style.color.value)
        x *= scale
        y *= scale
        val width = block.width * scale
        var start = memAddress(data)
        val end = start + data.remaining()
        var rowCount: Int = nnvgTextBreakLines(nvg, start, end, width, rows.memAddress(), 3)
        if (block.vAlign == TOP) {
            while (rowCount != 0) {
                for (i in 0 until rowCount) {
                    val row = rows.get(i)
                    nnvgText(nvg, x, y, row.start(), row.end())
                    y += scaledHeight
                }
                start = rows.get(rowCount - 1).next()
                rowCount = nnvgTextBreakLines(nvg, start, end, width, rows.memAddress(), 3)
            }
        } else {
            var yDelta = 0.0f
            while (rowCount != 0) {
                for (i in 0 until rowCount) {
                    yDelta += scaledHeight
                }
                start = rows.get(rowCount - 1).next()
                rowCount = nnvgTextBreakLines(nvg, start, end, width, rows.memAddress(), 3)
            }
            yDelta -= verticalSpace * scale
            if (block.vAlign == MIDDLE) {
                y -= (yDelta - lineHeight.get(0)) * 0.5f
            }
            if (block.vAlign == BOTTOM) {
                y -= yDelta - lineHeight.get(0)
            }
            start = memAddress(data)
            rowCount = nnvgTextBreakLines(nvg, start, end, width, rows.memAddress(), 3)
            while (rowCount != 0) {
                for (i in 0 until rowCount) {
                    val row = rows.get(i)
                    nnvgText(nvg, x, y, row.start(), row.end())
                    y += scaledHeight
                }
                start = rows.get(rowCount - 1).next()
                rowCount = nnvgTextBreakLines(nvg, start, end, width, rows.memAddress(), 3)
            }
        }
    }

    override fun width(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Float = 0.0f

    override fun height(scale: Float): Float = 0.0f

    override fun dimensions(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Pair<Float, Float> = 0.0f to 0.0f
}

class StaticTextParagraphUtf8(string: String, verticalSpace: Float, style: TextStyle) : DynamicTextParagraphUtf8(MemoryUtil.memUTF8(string, false), verticalSpace, style) {

    override val length: Int = data.limit()
}

open class DynamicTextUtf8(override val data: ByteBuffer, override var style: TextStyle) : Text {

    override val length: Int get() = data.limit()

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val (x , y, alignMask) = calculatePositionAndAlignmentForText(block)
        nvgFontFaceId(nvg, style.font.value)
        nvgFontSize(nvg, style.size.value * scale)
        nvgTextAlign(nvg, alignMask)
        nvgFillColor(nvg, style.color.value)
        nvgText(nvg, x * scale, y * scale, data)
    }

    protected fun widthInternal(nvg: Long, scale: Float, font: Int, size: Float): Float {
        twr(stackPush()) { stack ->
            val bounds = stack.mallocFloat(4)
            nvgFontFaceId(nvg, font)
            nvgFontSize(nvg, size * scale)
            nvgTextBounds(nvg, 0f, 0f, data, bounds)
            return Math.ceil(Math.ceil(bounds[2] - bounds[0].toDouble()) / scale).toFloat()
        }
    }

    override fun width(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Float = widthInternal(nvg, scale, style.font.value, style.size.value)

    override fun height(scale: Float) = Math.ceil(Math.ceil(style.size.value * scale.toDouble()) / scale).toFloat()

    override fun dimensions(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long) = width(nvg, scale, scaleChanged, runId) to height(scale)
}

class StaticTextUtf8(string: String, style: TextStyle) : DynamicTextUtf8(MemoryUtil.memUTF8(string, false), style) {

    private var cachedScale = -1.0f
    private var cachedSize = -1.0f
    private var cachedFont = -1

    private var cachedRunId = -1L
    private var cachedWidth = -1.0f

    override val length: Int = data.limit()

    override fun width(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Float {
        val size = style.size.value
        val font = style.font.value
        if ((scaleChanged && cachedRunId != runId) || cachedScale != scale || cachedSize != size || cachedFont != font || cachedWidth < 0.0f) {
            cachedScale = scale
            cachedSize = size
            cachedFont = font
            cachedRunId = runId
            cachedWidth = widthInternal(nvg, scale, style.font.value, style.size.value)
        }
        return cachedWidth
    }

    override fun dimensions(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long) = width(nvg, scale, scaleChanged, runId) to height(scale)
}

private fun calculatePositionAndAlignmentForText(block: Block): Triple<Float, Float, Int> {
    val x: Float
    val hAlignMask = when (block.hAlign) {
        LEFT -> {
            x = block.x
            NVG_ALIGN_LEFT
        }
        RIGHT -> {
            x = block.x + block.width
            NVG_ALIGN_RIGHT
        }
        CENTER -> {
            x = block.x + block.width / 2.0f
            NVG_ALIGN_CENTER
        }
    }
    val y: Float
    val vAlignMask = when (block.vAlign) {
        TOP -> {
            y = block.y
            NVG_ALIGN_TOP
        }
        BOTTOM -> {
            y = block.y + block.height
            NVG_ALIGN_BOTTOM
        }
        MIDDLE -> {
            y = block.y + block.height / 2.0f
            NVG_ALIGN_MIDDLE
        }
    }
    val alignMask = hAlignMask or vAlignMask
    return Triple(x, y, alignMask)
}

interface Shape {

    val fill: Fill
    val stroke: Stroke

    fun draw(nvg: Long, block: Block, scale: Float)
}

private class ShapeNone : Shape {

    override val fill: Fill = NO_FILL
    override val stroke: Stroke = NO_STROKE

    override fun draw(nvg: Long, block: Block, scale: Float) {
    }
}

val NO_FILL: Fill = FillNone()

val NO_STROKE: Stroke = StrokeNone()

val NO_SHAPE: Shape = ShapeNone()

val NO_TEXT: Text = TextNone()


class ShapeRectangle(override val fill: Fill, override val stroke: Stroke) : Shape {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgRect(nvg, (block.x + halfStroke) * scale, (block.y + halfStroke) * scale, (block.width - stroke.size) * scale, (block.height - stroke.size) * scale)
        fill.draw(nvg, block, scale)
        stroke.draw(nvg, block, scale)
    }
}

class ShapeCursor(override val fill: Fill, override val stroke: Stroke, private val selectFill: Fill, private val caret: Caret, var timeOffset: Long = 0) : Shape {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        if (caret.selection == 0) {
            if (((System.currentTimeMillis() - timeOffset) / 500) % 2 == 0L) {
                val caretOffset = caret.getOffset(scale)
                val halfStroke = stroke.size / 2.0f
                nvgBeginPath(nvg)
                nvgRect(nvg, (block.x + halfStroke + caretOffset) * scale, (block.y + halfStroke) * scale, (block.width - stroke.size) * scale, (block.height - stroke.size) * scale)
                fill.draw(nvg, block, scale)
                stroke.draw(nvg, block, scale)
            }
        } else {
            val (start, end) = caret.getOffsets(scale)
            val halfStroke = stroke.size / 2.0f
            nvgBeginPath(nvg)
            nvgRect(nvg, (block.x + halfStroke + start) * scale, (block.y + halfStroke) * scale, (end - start) * scale, (block.height - stroke.size) * scale)
            selectFill.draw(nvg, block, scale)
        }
    }
}

class ShapeCircle(override val fill: Fill, override val stroke: Stroke) : Shape {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgCircle(nvg, (block.x + (block.width / 2.0f)) * scale, (block.y + (block.height / 2.0f)) * scale, (min(block.width, block.height) / 2.0f - halfStroke) * scale)
        fill.draw(nvg, block, scale)
        stroke.draw(nvg, block, scale)
    }
}

class ShapeEllipse(override val fill: Fill, override val stroke: Stroke) : Shape {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgEllipse(nvg, ((block.x + block.width) / 2.0f) * scale, ((block.y + block.height) / 2.0f) * scale, (block.width / 2.0f - halfStroke) * scale, (block.height / 2.0f - halfStroke) * scale)
        fill.draw(nvg, block, scale)
        stroke.draw(nvg, block, scale)
    }
}

class ShapeRoundedRectangle(override val fill: Fill, override val stroke: Stroke, private val cornerRadius: Float) : Shape {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgRoundedRect(nvg, (block.x + halfStroke) * scale, (block.y + halfStroke) * scale, (block.width - stroke.size) * scale, (block.height - stroke.size) * scale, (min(cornerRadius, min(block.width, block.height) / 2.0f)) * scale)
        fill.draw(nvg, block, scale)
        stroke.draw(nvg, block, scale)
    }
}

class ShapeDropShadow(override val fill: Fill, override val stroke: Stroke, private val inset: Float, private val cornerRadius: Float) : Shape {

    private val insetX2 = inset * 2

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        val x = (block.x + halfStroke) * scale
        val y = (block.y + halfStroke) * scale
        val width = (block.width - stroke.size) * scale
        val height = (block.height - stroke.size) * scale
        val actualCornerRadius = (min(cornerRadius, min(block.width, block.height) / 2.0f)) * scale
        val scaledInset = inset * scale
        val scaledInsetX2 = insetX2 * scale
        nvgRoundedRect(nvg, x + scaledInset, y + scaledInset, width - scaledInsetX2, height - scaledInsetX2, max(actualCornerRadius - scaledInset, 0.0f))
        nvgRoundedRect(nvg, x, y, width, height, actualCornerRadius)
        nvgPathWinding(nvg, NVG_HOLE)
        fill.draw(nvg, block, scale)
        stroke.draw(nvg, block, scale)
    }
}

private const val TRIANGLE_SIDE = 0.70710678118

class ShapeTriangle(override val fill: Fill, override val stroke: Stroke, private val direction: Direction) : Shape {

    enum class Direction {
        NORTH,
        NORTH_EAST,
        EAST,
        SOUTH_EAST,
        SOUTH,
        SOUTH_WEST,
        WEST,
        NORTH_WEST
    }

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val halfStroke = stroke.size / 2.0
        val x1 = (block.x + halfStroke) * scale
        val x2 = x1 + (block.width - stroke.size) * scale
        val y1 = (block.y + halfStroke) * scale
        val y2 = y1 + (block.height - stroke.size) * scale
        nvgBeginPath(nvg)
        when (direction) {
            Direction.NORTH -> {
                val x3 = (x1 + x2) / 2.0
                val y3 = (y1 + y2) / 2.0
                nvgMoveTo(nvg, x3.toFloat(), y1.toFloat())
                nvgLineTo(nvg, x2.toFloat(), y3.toFloat())
                nvgLineTo(nvg, x1.toFloat(), y3.toFloat())
            }
            Direction.NORTH_EAST -> {
                val x3 = x2 - (x2 - x1) * TRIANGLE_SIDE
                val y3 = y1 + (y2 - y1) * TRIANGLE_SIDE
                nvgMoveTo(nvg, x2.toFloat(), y1.toFloat())
                nvgLineTo(nvg, x2.toFloat(), y3.toFloat())
                nvgLineTo(nvg, x3.toFloat(), y1.toFloat())
            }
            Direction.EAST -> {
                val x3 = (x1 + x2) / 2.0
                val y3 = (y1 + y2) / 2.0
                nvgMoveTo(nvg, x2.toFloat(), y3.toFloat())
                nvgLineTo(nvg, x3.toFloat(), y2.toFloat())
                nvgLineTo(nvg, x3.toFloat(), y1.toFloat())
            }
            Direction.SOUTH_EAST -> {
                val x3 = x2 - (x2 - x1) * TRIANGLE_SIDE
                val y3 = y2 - (y2 - y1) * TRIANGLE_SIDE
                nvgMoveTo(nvg, x2.toFloat(), y2.toFloat())
                nvgLineTo(nvg, x3.toFloat(), y2.toFloat())
                nvgLineTo(nvg, x2.toFloat(), y3.toFloat())
            }
            Direction.SOUTH -> {
                val x3 = (x1 + x2) / 2.0
                val y3 = (y1 + y2) / 2.0
                nvgMoveTo(nvg, x3.toFloat(), y2.toFloat())
                nvgLineTo(nvg, x1.toFloat(), y3.toFloat())
                nvgLineTo(nvg, x2.toFloat(), y3.toFloat())
            }
            Direction.SOUTH_WEST -> {
                val x3 = x1 + (x2 - x1) * TRIANGLE_SIDE
                val y3 = y2 - (y2 - y1) * TRIANGLE_SIDE
                nvgMoveTo(nvg, x1.toFloat(), y2.toFloat())
                nvgLineTo(nvg, x1.toFloat(), y3.toFloat())
                nvgLineTo(nvg, x3.toFloat(), y2.toFloat())
            }
            Direction.WEST -> {
                val x3 = (x1 + x2) / 2.0
                val y3 = (y1 + y2) / 2.0
                nvgMoveTo(nvg, x1.toFloat(), y3.toFloat())
                nvgLineTo(nvg, x3.toFloat(), y1.toFloat())
                nvgLineTo(nvg, x3.toFloat(), y2.toFloat())
            }
            Direction.NORTH_WEST -> {
                val x3 = x1 + (x2 - x1) * TRIANGLE_SIDE
                val y3 = y1 + (y2 - y1) * TRIANGLE_SIDE
                nvgMoveTo(nvg, x1.toFloat(), y1.toFloat())
                nvgLineTo(nvg, x3.toFloat(), y1.toFloat())
                nvgLineTo(nvg, x1.toFloat(), y3.toFloat())
            }
        }
        nvgClosePath(nvg)
        fill.draw(nvg, block, scale)
        stroke.draw(nvg, block, scale)
    }
}

class ShapeMeshViewport3D(private val viewport: MeshViewport3D) : Shape {

    override val fill = NO_FILL
    override val stroke = NO_STROKE

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val doubleScale = scale.toDouble()
        val xPosition = Math.ceil(block.x * doubleScale).toInt()
        val yPosition = Math.ceil(block.y * doubleScale).toInt()
        val width = Math.ceil(block.width * doubleScale).toInt()
        val height = Math.ceil(block.height * doubleScale).toInt()
        val rootHeight = Math.ceil(block.root.height * doubleScale).toInt()
        val flippedY = rootHeight - (yPosition + height)
        nvgSave(nvg)
        nvgReset(nvg)
        glEnable(GL_SCISSOR_TEST)
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        glScissor(xPosition, flippedY, width, height)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glDisable(GL_SCISSOR_TEST)
        viewport.prepareDrawFrame(xPosition, yPosition, width, height, scale)
        nvgRestore(nvg)
    }
}

fun uiRoot(x: Float, y: Float, width: Float, height: Float, builder: Block.() -> Unit): Block {
    val root = RootBlock(x, y, width, height)
    root.builder()
    return root
}

data class BlockTemplate(
        val isVisible: Boolean = true,
        val hAlign: HorizontalAlignment = LEFT,
        val vAlign: VerticalAlignment = TOP,
        val layout: Layout = ABSOLUTE,
        val xOffset: Float = 0.0f,
        val yOffset: Float = 0.0f,
        val hSizing: Sizing = RELATIVE,
        val width: Float = 10000.0f,
        val vSizing: Sizing = RELATIVE,
        val height: Float = 10000.0f,
        val padLeft: Float = 0.0f,
        val padRight: Float = 0.0f,
        val padTop: Float = 0.0f,
        val padBottom: Float = 0.0f)



interface ShrinkGroup {
    val isCalculated: Boolean
}

private class ShrinkGroupInternal(internal val blocks: MutableList<Block>, internal val horizontal: Boolean, override var isCalculated: Boolean = false): ShrinkGroup {

    private var _size: Float = 0.0f

    internal val size: Float
    get() {
        return if (isCalculated) {
            _size
        } else {
            var max = 0.0f
            blocks.forEach {
                max = if (horizontal) {
                    max(it.width, max)
                } else {
                    max(it.height, max)
                }
            }
            _size = max
            isCalculated = true
            _size
        }
    }
}

fun hShrinkGroup(): ShrinkGroup = ShrinkGroupInternal(ArrayList(), true)

fun vShrinkGroup(): ShrinkGroup = ShrinkGroupInternal(ArrayList(), false)

abstract class Block {
    abstract val root: Block
    abstract val scale: Float
    abstract val scaleChanged: Boolean
    abstract val parent: Block
    abstract val nvg: Long
    abstract val runId: Long
    abstract var movedOrResized: Boolean
    abstract val layoutChildren: MutableList<Block>
    abstract val renderChildren: MutableList<Block>
    abstract var isVisible: Boolean
    abstract var hAlign: HorizontalAlignment
    abstract var vAlign: VerticalAlignment
    abstract var hTruncate: HorizontalTruncation
    abstract var vTruncate: VerticalTruncation
    abstract var layout: Layout
    abstract var xOffset: Float
    abstract var yOffset: Float
    abstract var x: Float
    abstract var y: Float
    abstract var hSizing: Sizing
    abstract var hShrinkGroup: ShrinkGroup?
    abstract var width: Float
    abstract var vSizing: Sizing
    abstract var vShrinkGroup: ShrinkGroup?
    abstract var height: Float
    abstract var padLeft: Float
    abstract var padRight: Float
    abstract var padTop: Float
    abstract var padBottom: Float
    abstract var shape: Shape
    abstract var text: Text
    abstract var lastBlock: Block?
    abstract var isMouseAware: Boolean
    abstract var receiveChildEvents: Boolean
    abstract var isFallThrough: Boolean
    abstract var canOverflow: Boolean
    abstract var overflowCount: Int
    abstract var onMouseOver: (Block.() -> Unit)?
    abstract var onMouseOut: (Block.() -> Unit)?
    abstract var onMouseDown: (Block.(button: Int, x: Int, y: Int, mods: Int) -> Unit)?
    abstract var onMouseUp: (Block.(button: Int, x: Int, y: Int, mods: Int) -> Unit)?
    abstract var onMouseRelease: (Block.(button: Int, x: Int, y: Int, mods: Int) -> Unit)?
    abstract var onMouseDownOverOther: (Block.(button: Int, x: Int, y: Int, mods: Int) -> Unit)?
    abstract var onMouseClick: (Block.(button: Int, x: Int, y: Int, mods: Int) -> Unit)?
    abstract var onMouseDrag: (Block.(button: Int, x: Int, y: Int, mods: Int) -> Unit)?
    abstract var onScroll: (Block.(x: Double, y: Double) -> Unit)?
    abstract var onDrop: (Block.(strings: List<String>) -> Unit)?
    abstract var onTick: (Block.(mouseX: Int, mouseY: Int) -> Unit)?
    abstract var inputOverride: Block?
    protected abstract var mouseOver: Block?
    protected abstract var mouseOverParents: List<Block>?
    protected abstract var lastMouseOver: Block?
    protected abstract var awaitingRelease: MutableList<Triple<Int, Block, Int>>
    protected abstract var awaitingMouseDownOverOther: MutableList<Triple<Int, Block, Int>>

    private var reprocess = false

    fun reprocessTick() {
        root.reprocess = true
    }

    fun handleMouseAction(button: Int, x: Int, y: Int, isDown: Boolean, mods: Int) {
        if (this === root) {
            val itemsToProcess = listOf(mouseOver) + (mouseOverParents ?: emptyList())
            var onlyOnFirst = true
            itemsToProcess.forEach { itemToProcess ->
                do {
                    reprocess = false
                    if (itemToProcess != null) {
                        if (isDown) {
                            val mouseDownFun = itemToProcess.onMouseDown
                            if (mouseDownFun != null) {
                                itemToProcess.mouseDownFun(button, x, y, mods)
                            }
                            var needToAdd = true
                            if (onlyOnFirst) {
                                val toRemove = ArrayList<Triple<Int, Block, Int>>(awaitingMouseDownOverOther.size)
                                awaitingMouseDownOverOther.forEach {
                                    if (it.first == button) {
                                        if (it.second == itemToProcess) {
                                            needToAdd = false
                                        } else {
                                            val mouseDownOverOtherFun = it.second.onMouseDownOverOther
                                            if (mouseDownOverOtherFun != null) {
                                                it.second.mouseDownOverOtherFun(button, x, y, mods)
                                            }
                                        }
                                    }
                                }
                                awaitingMouseDownOverOther.removeAll(toRemove)
                                onlyOnFirst = false
                            }
                            val pair = Triple(button, itemToProcess, mods)
                            awaitingRelease.add(pair)
                            if (needToAdd) {
                                awaitingMouseDownOverOther.add(pair)
                            }
                        } else {
                            val mouseUpFun = itemToProcess.onMouseUp
                            if (mouseUpFun != null) {
                                itemToProcess.mouseUpFun(button, x, y, mods)
                            }
                            if (onlyOnFirst) {
                                awaitingRelease.forEach {
                                    if (it.first == button && it.second == itemToProcess) {
                                        val mouseClickFun = itemToProcess.onMouseClick
                                        if (mouseClickFun != null) {
                                            itemToProcess.mouseClickFun(button, x, y, mods)
                                        }
                                    }
                                }
                                onlyOnFirst = false
                            }
                        }
                    }
                    if (!isDown) {
                        for (i in awaitingRelease.size - 1 downTo 0) {
                            val it = awaitingRelease[i]
                            if (it.first == button) {
                                awaitingRelease.removeAt(i)
                                val mouseReleaseFun = it.second.onMouseRelease
                                if (mouseReleaseFun != null) {
                                    it.second.mouseReleaseFun(button, x, y, mods)
                                }
                            }
                        }
                    }
                    if (reprocess) {
                        handleNewMousePosition(nvg, x, y)
                    }
                } while (reprocess)
            }
        } else {
            root.handleMouseAction(button, x, y, isDown, mods)
        }
    }

    fun handleScroll(scrollX: Double, scrollY: Double) {
        if (this === root) {
            reprocess = false
            val itemsToProcess = listOf(mouseOver) + (mouseOverParents ?: emptyList())
            itemsToProcess.forEach { itemToProcess ->
                val scrollFun = itemToProcess?.onScroll
                if (itemToProcess != null && scrollFun != null) {
                    itemToProcess.scrollFun(scrollX, scrollY)
                }
            }
        } else {
            root.handleScroll(scrollX, scrollY)
        }
    }

    fun handleDrop(strings: List<String>) {
        if (this === root) {
            reprocess = false
            val itemsToProcess = listOf(mouseOver) + (mouseOverParents ?: emptyList())
            itemsToProcess.forEach { itemToProcess ->
                val dropFun = itemToProcess?.onDrop
                if (itemToProcess != null && dropFun != null) {
                    itemToProcess.dropFun(strings)
                }
            }
        } else {
            root.handleDrop(strings)
        }
    }

    fun handleNewMousePosition(nvg: Long, mouseX: Int, mouseY: Int) {
        if (this === root) {
            (this as RootBlock).nvg = nvg
            val reprocessAfter = reprocess
            do {
                reprocess = false
                lastMouseOver = mouseOver
                val mouseOverPair = if (inputOverride != null) {
                    prepareAndGetMouseOverBlock(mouseX, mouseY)
                    inputOverride to emptyList<Block>()
                } else {
                    prepareAndGetMouseOverBlock(mouseX, mouseY)
                }
                mouseOver = mouseOverPair?.first
                if (mouseOver != lastMouseOver) {
                    val mouseOutFun = lastMouseOver?.onMouseOut
                    if (mouseOutFun != null) {
                        lastMouseOver?.mouseOutFun()
                    }
                    val mouseOverFun = mouseOver?.onMouseOver
                    if (mouseOverFun != null) {
                        mouseOver?.mouseOverFun()
                    }
                }
                mouseOverParents = mouseOverPair?.second
                awaitingRelease.forEach {
                    val mouseDragFun = it.second.onMouseDrag
                    if (mouseDragFun != null) {
                        it.second.mouseDragFun(it.first, mouseX, mouseY, it.third)
                    }
                }
            } while (reprocess)
            reprocess = reprocessAfter
        } else {
            root.handleNewMousePosition(nvg, mouseX, mouseY)
        }
    }

    private fun prepareAndGetMouseOverBlock(mouseX: Int, mouseY: Int): Pair<Block, List<Block>>? {
        if (isVisible) {
            val onTicks: MutableList<Block.(Int, Int) -> Unit> = ArrayList()
            try {
                prepareForIteration(onTicks)
            } catch (t: Throwable) {
                LOG.error("Error iterating ui blocks.", t)
            }
            onTicks.forEach { it(mouseX, mouseY) }
            return getMouseOverBlock(mouseX, mouseY)
        }
        return null
    }

    private fun getMouseOverBlock(mouseX: Int, mouseY: Int): Pair<Block, List<Block>>? {
        if (isVisible) {
            val parentsNeedUpdates = ArrayList<Block>()
            if (receiveChildEvents) {
                parentsNeedUpdates.add(this)
            }
            if (!isMouseAware || ((mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) && !isFallThrough)) {
                return null
            }
            (renderChildren.size - 1 downTo 0)
                    .mapNotNull { renderChildren[it].getMouseOverBlock(mouseX, mouseY) }
                    .forEach { return it.first to it.second + parentsNeedUpdates }
            if (isFallThrough) {
                return null
            }
            return this to emptyList()
        }
        return null
    }

    fun draw(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long) {
        val root = (root as RootBlock)
        root.nvg = nvg
        root.runId = runId
        root.scale = scale
        root.scaleChanged = scaleChanged
        val movedOrResized = root.getAndSetMovedOrResized(false)
        if (movedOrResized) {
            clearPositionAndSize(root)
            recalculatePositionAndSize(root)
        }
        if (isVisible) {
            prepareForIteration()
            draw(ScissorStack(nvg), scale)
        }
    }

    private fun clearPositionAndSize(block: Block) {
        block.clearPositionAndSize()
        block.layoutChildren.forEach {
            clearPositionAndSize(it)
        }
    }

    private fun recalculatePositionAndSize(block: Block) {
        block.recalculatePositionAndSize()
        block.layoutChildren.forEach {
            recalculatePositionAndSize(it)
        }
    }

    abstract protected fun clearPositionAndSize()

    abstract protected fun recalculatePositionAndSize()

    private fun prepareForIteration(onTicks: MutableList<Block.(Int, Int) -> Unit>? = null) {
        val shrinkGroups = HashSet<ShrinkGroup>()
        prepareForIteration(shrinkGroups, onTicks)
        shrinkGroups.forEach {
            if (it is ShrinkGroupInternal) {
                it.size
            }
        }
    }

    open protected fun prepareForIteration(shrinkGroups: MutableSet<ShrinkGroup>, onTicks: MutableList<Block.(Int, Int) -> Unit>?) {
        var lastChild: Block? = null
        layoutChildren.forEach {
            it.lastBlock = lastChild
            if (onTicks != null) {
                val onTick = it.onTick
                if (onTick != null) {
                    onTicks.add(onTick)
                }
            }
            it.prepareForIteration(shrinkGroups, onTicks)
            lastChild = it
        }
    }

    private fun draw(scissorStack: ScissorStack, scale: Float) {
        if (isVisible) {
            scissorStack.suspendIf(canOverflow, overflowCount) {
                shape.draw(nvg, this, scale)
                val strokeSize = shape.stroke.size
                scissorStack.push(Vector4f((x + strokeSize) * scale, (y + strokeSize) * scale, (x + width - strokeSize) * scale, (y + height - strokeSize) * scale), canOverflow)
                scissorStack.suspendIf(canOverflow, overflowCount) {
                    text.draw(nvg, this, scale)
                }
                renderChildren.forEach {
                    it.draw(scissorStack, scale)
                }
                scissorStack.pop()
            }
        }
    }

    fun block(builder: Block.() -> Unit): Block {
        val block = DefaultBlock(root, this)
        this.layoutChildren.add(block)
        this.renderChildren.add(block)
        block.builder()
        return block
    }

    fun onMouseOver(onMouseOver: Block.() -> Unit) {
        this.onMouseOver = onMouseOver
    }

    fun onMouseOut(onMouseOut: Block.() -> Unit) {
        this.onMouseOut = onMouseOut
    }

    fun onMouseDown(onMouseDown: Block.(Int, Int, Int, Int) -> Unit) {
        this.onMouseDown = onMouseDown
    }

    fun onMouseUp(onMouseUp: Block.(Int, Int, Int, Int) -> Unit) {
        this.onMouseUp = onMouseUp
    }

    fun onMouseRelease(onMouseRelease: Block.(Int, Int, Int, Int) -> Unit) {
        this.onMouseRelease = onMouseRelease
    }

    fun onMouseDownOverOther(onMouseDownOverOther: Block.(Int, Int, Int, Int) -> Unit) {
        this.onMouseDownOverOther = onMouseDownOverOther
    }

    fun onMouseClick(onMouseClick: Block.(Int, Int, Int, Int) -> Unit) {
        this.onMouseClick = onMouseClick
    }

    fun onMouseDrag(onMouseDrag: Block.(Int, Int, Int, Int) -> Unit) {
        this.onMouseDrag = onMouseDrag
    }

    fun onScroll(onScroll: Block.(Double, Double) -> Unit) {
        this.onScroll = onScroll
    }

    fun onDrop(onDrop: Block.(List<String>) -> Unit) {
        this.onDrop = onDrop
    }

    fun onTick(onTick: Block.(mouseX: Int, mouseY: Int) -> Unit) {
        this.onTick = onTick
    }

    operator fun invoke(builder: Block.() -> Unit): Block {
        this.builder()
        return this
    }

    fun with(builder: Block.() -> Unit): Block = invoke(builder)
}

private val ignore = Unit

private open class RootBlock(x: Float, y: Float, width: Float, height: Float) : Block() {
    private var _movedOrResized: AtomicBoolean = AtomicBoolean(true)
    override var movedOrResized: Boolean
        get() = _movedOrResized.get()
        set(value) {
            _movedOrResized.set(value)
        }

    fun getAndSetMovedOrResized(value: Boolean): Boolean = _movedOrResized.getAndSet(value)

    private var _x = x
    override var x: Float
        get() = _x
        set(value) {
            if (value != _x) {
                _x = value
                movedOrResized = true
            }
        }

    private var _y = y
    override var y: Float
        get() = _y
        set(value) {
            if (value != _y) {
                _y = value
                movedOrResized = true
            }
        }

    private var _width = width
    override var width: Float
        get() = _width
        set(value) {
            if (value != _width) {
                _width = value
                movedOrResized = true
            }
        }

    private var _height = height
    override var height: Float
        get() = _height
        set(value) {
            if (value != _height) {
                _height = value
                movedOrResized = true
            }
        }

    override var scale: Float = 1.0f
    private var _scaleChanged = false
    override var scaleChanged: Boolean
        get() = _scaleChanged
        set(value) {
            _scaleChanged = value
            movedOrResized = value || movedOrResized
        }
    override var hShrinkGroup: ShrinkGroup?
        get() = null
        set(value) = ignore
    override var vShrinkGroup: ShrinkGroup?
        get() = null
        set(value) = ignore
    override val root: RootBlock
        get() = this
    override val parent: RootBlock
        get() = this
    override val layoutChildren = ObservableMutableList<Block>(ArrayList()).addListener { movedOrResized = true }
    override val renderChildren = ObservableMutableList<Block>(ArrayList()).addListener { movedOrResized = true }
    override var nvg: Long = -1
    override var runId: Long = -1
    override var isVisible: Boolean
        get() = true
        set(value) = ignore
    override var hAlign: HorizontalAlignment
        get() = LEFT
        set(value) = ignore
    override var vAlign: VerticalAlignment
        get() = TOP
        set(value) = ignore
    override var hTruncate: HorizontalTruncation
        get() = TRUNCATE_RIGHT
        set(value) = ignore
    override var vTruncate: VerticalTruncation
        get() = TRUNCATE_BOTTOM
        set(value) = ignore
    override var layout: Layout
        get() = ABSOLUTE
        set(value) = ignore
    override var xOffset: Float
        get() = 0.0f
        set(value) = ignore
    override var yOffset: Float
        get() = 0.0f
        set(value) = ignore
    override var hSizing: Sizing
        get() = STATIC
        set(value) = ignore
    override var vSizing: Sizing
        get() = STATIC
        set(value) = ignore
    override var padLeft: Float
        get() = 0.0f
        set(value) = ignore
    override var padRight: Float
        get() = 0.0f
        set(value) = ignore
    override var padTop: Float
        get() = 0.0f
        set(value) = ignore
    override var padBottom: Float
        get() = 0.0f
        set(value) = ignore
    override var shape: Shape
        get() = NO_SHAPE
        set(value) = ignore
    override var text: Text
        get() = NO_TEXT
        set(value) = ignore
    override var lastBlock: Block?
        get() = null
        set(value) = ignore
    override var isMouseAware: Boolean
        get() = true
        set(value) = ignore
    override var receiveChildEvents: Boolean
        get() = false
        set(value) = ignore
    override var isFallThrough: Boolean
        get() = false
        set(value) = ignore
    override var canOverflow: Boolean
        get() = false
        set(value) = ignore
    override var overflowCount: Int
        get() = -1
        set(value) = ignore
    override var onMouseOver: (Block.() -> Unit)?
        get() = null
        set(value) = ignore
    override var onMouseOut: (Block.() -> Unit)?
        get() = null
        set(value) = ignore
    override var onMouseDown: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) = ignore
    override var onMouseUp: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) = ignore
    override var onMouseRelease: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) = ignore
    override var onMouseDownOverOther: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) = ignore
    override var onMouseClick: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) = ignore
    override var onMouseDrag: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) = ignore
    override var onScroll: (Block.(Double, Double) -> Unit)?
        get() = null
        set(value) = ignore
    override var onDrop: (Block.(List<String>) -> Unit)?
        get() = null
        set(value) = ignore
    override var onTick: (Block.(Int, Int) -> Unit)?
        get() = null
        set(value) = ignore
    override var inputOverride: Block? = null
    override var awaitingRelease: MutableList<Triple<Int, Block, Int>> = ArrayList()
    override var mouseOver: Block? = null
    override var mouseOverParents: List<Block>? = null
    override var lastMouseOver: Block? = null
    override var awaitingMouseDownOverOther: MutableList<Triple<Int, Block, Int>> = ArrayList()
    override fun clearPositionAndSize() {}
    override fun recalculatePositionAndSize() {}
}

val NO_BLOCK: Block = object : RootBlock(-1.0f, -1.0f, -1.0f, -1.0f) {
    override var isVisible: Boolean
        get() = false
        set(value) = ignore
    override var inputOverride: Block?
        get() = null
        set(value) = ignore
}

private class DefaultBlock(
        override val root: Block,
        override val parent: Block) : Block() {

    override var movedOrResized: Boolean
        get() = root.movedOrResized
        set(value) {
            root.movedOrResized = value
        }
    override val layoutChildren = ObservableMutableList<Block>(ArrayList()).addListener { movedOrResized = true }
    override val renderChildren = ObservableMutableList<Block>(ArrayList()).addListener { movedOrResized = true }
    private var _isVisible: Boolean = true
    override var isVisible: Boolean
        get() = _isVisible
        set(value) {
            if (_isVisible != value) {
                _isVisible = value
                movedOrResized = true
            }
        }
    private var _hAlign: HorizontalAlignment = LEFT
    override var hAlign: HorizontalAlignment
        get() = _hAlign
        set(value) {
            if (_hAlign != value) {
                _hAlign = value
                movedOrResized = true
            }
        }
    private var _vAlign: VerticalAlignment = TOP
    override var vAlign: VerticalAlignment
        get() = _vAlign
        set(value) {
            if (_vAlign != value) {
                _vAlign = value
                movedOrResized = true
            }
        }
    private var _hTruncate: HorizontalTruncation = if (hAlign == RIGHT) TRUNCATE_LEFT else if (hAlign == LEFT) TRUNCATE_RIGHT else TRUNCATE_CENTER
    override var hTruncate: HorizontalTruncation
        get() = _hTruncate
        set(value) {
            if (_hTruncate != value) {
                _hTruncate = value
                movedOrResized = true
            }
        }
    private var _vTruncate: VerticalTruncation = if (vAlign == BOTTOM) TRUNCATE_TOP else if (vAlign == TOP) TRUNCATE_BOTTOM else TRUNCATE_MIDDLE
    override var vTruncate: VerticalTruncation
        get() = _vTruncate
        set(value) {
            if (_vTruncate != value) {
                _vTruncate = value
                movedOrResized = true
            }
        }
    private var _layout: Layout = ABSOLUTE
    override var layout: Layout
        get() = _layout
        set(value) {
            if (_layout != value) {
                _layout = value
                movedOrResized = true
            }
        }
    private var _xOffset: Float = 0.0f
    override var xOffset: Float
        get() = _xOffset
        set(value) {
            if (_xOffset != value) {
                _xOffset = value
                movedOrResized = true
            }
        }
    private var _yOffset: Float = 0.0f
    override var yOffset: Float
        get() = _yOffset
        set(value) {
            if (_yOffset != value) {
                _yOffset = value
                movedOrResized = true
            }
        }
    private var _hSizing: Sizing = RELATIVE
    override var hSizing: Sizing
        get() = _hSizing
        set(value) {
            if (_hSizing != value) {
                _hSizing = value
                movedOrResized = true
            }
        }
    private var _vSizing: Sizing = RELATIVE
    override var vSizing: Sizing
        get() = _vSizing
        set(value) {
            if (_vSizing != value) {
                _vSizing = value
                movedOrResized = true
            }
        }
    private var _padLeft: Float = 0.0f
    override var padLeft: Float
        get() = _padLeft
        set(value) {
            if (_padLeft != value) {
                _padLeft = value
                movedOrResized = true
            }
        }
    private var _padRight: Float = 0.0f
    override var padRight: Float
        get() = _padRight
        set(value) {
            if (_padRight != value) {
                _padRight = value
                movedOrResized = true
            }
        }
    private var _padTop: Float = 0.0f
    override var padTop: Float
        get() = _padTop
        set(value) {
            if (_padTop != value) {
                _padTop = value
                movedOrResized = true
            }
        }
    private var _padBottom: Float = 0.0f
    override var padBottom: Float
        get() = _padBottom
        set(value) {
            if (_padBottom != value) {
                _padBottom = value
                movedOrResized = true
            }
        }
    private var _shape: Shape = NO_SHAPE
    override var shape: Shape
        get() = _shape
        set(value) {
            if (_shape != value) {
                _shape = value
                movedOrResized = true
            }
        }
    private var _text: Text = NO_TEXT
    override var text: Text
        get() = _text
        set(value) {
            if (_text != value) {
                _text = value
                movedOrResized = true
            }
        }
    private var _canOverflow: Boolean = false
    override var canOverflow: Boolean
        get() = _canOverflow
        set(value) {
            if (_canOverflow != value) {
                _canOverflow = value
                movedOrResized = true
            }
        }
    override var overflowCount: Int = -1
    override var lastBlock: Block? = null
    override var isMouseAware: Boolean = parent.isMouseAware
    override var receiveChildEvents: Boolean = false
    override var isFallThrough: Boolean = false
    override var onMouseOver: (Block.() -> Unit)? = null
    override var onMouseOut: (Block.() -> Unit)? = null
    override var onMouseDown: (Block.(Int, Int, Int, Int) -> Unit)? = null
    override var onMouseUp: (Block.(Int, Int, Int, Int) -> Unit)? = null
    override var onMouseRelease: (Block.(Int, Int, Int, Int) -> Unit)? = null
    override var onMouseDownOverOther: (Block.(Int, Int, Int, Int) -> Unit)? = null
    override var onMouseClick: (Block.(Int, Int, Int, Int) -> Unit)? = null
    override var onMouseDrag: (Block.(Int, Int, Int, Int) -> Unit)? = null
    override var onScroll: (Block.(Double, Double) -> Unit)? = null
    override var onDrop: (Block.(List<String>) -> Unit)? = null
    override var onTick: (Block.(Int, Int) -> Unit)? = null

    override val nvg: Long
        get() = root.nvg

    override val runId: Long
        get() = root.runId

    override val scale: Float
        get() = root.scale

    override val scaleChanged: Boolean
        get() = root.scaleChanged

    override var inputOverride: Block?
        get() = root.inputOverride
        set(value) {
            root.inputOverride = value
        }

    override var awaitingRelease: MutableList<Triple<Int, Block, Int>>
        get() = ArrayList()
        set(value) = ignore
    override var mouseOver: Block?
        get() = null
        set(value) = ignore
    override var mouseOverParents: List<Block>?
        get() = null
        set(value) = ignore
    override var lastMouseOver: Block?
        get() = null
        set(value) = ignore
    override var awaitingMouseDownOverOther: MutableList<Triple<Int, Block, Int>>
        get() = ArrayList()
        set(value) = ignore

    private var _hShrinkGroup: ShrinkGroupInternal? = null
    private var _vShrinkGroup: ShrinkGroupInternal? = null

    override var hShrinkGroup: ShrinkGroup?
        get() = _hShrinkGroup
        set(value) {
            if (value is ShrinkGroupInternal) {
                _hShrinkGroup?.blocks?.remove(this)
                if (!value.blocks.contains(this)) {
                    value.blocks.add(this)
                }
                _hShrinkGroup = value
            } else if (value == null) {
                _hShrinkGroup?.blocks?.remove(this)
                _hShrinkGroup = null
            }
        }
    override var vShrinkGroup: ShrinkGroup?
        get() = _vShrinkGroup
        set(value) {
            if (value is ShrinkGroupInternal) {
                _vShrinkGroup?.blocks?.remove(this)
                if (!value.blocks.contains(this)) {
                    value.blocks.add(this)
                }
                _vShrinkGroup = value
            } else if (value == null) {
                _vShrinkGroup?.blocks?.remove(this)
                _vShrinkGroup = null
            }
        }

    override fun prepareForIteration(shrinkGroups: MutableSet<ShrinkGroup>, onTicks: MutableList<Block.(Int, Int) -> Unit>?) {
        var shrinkGroup = _hShrinkGroup
        if (shrinkGroup != null) {
            shrinkGroup.isCalculated = false
            shrinkGroups.add(shrinkGroup)
        }
        shrinkGroup = _vShrinkGroup
        if (shrinkGroup != null) {
            shrinkGroup.isCalculated = false
            shrinkGroups.add(shrinkGroup)
        }
        super.prepareForIteration(shrinkGroups, onTicks)
    }

    private var _width = 10000.0f
    private var _height = 10000.0f

    override var width: Float
        get() {
            val finalCachedWidth = cachedWidth
            if (finalCachedWidth != null) {
                return finalCachedWidth
            } else if (cachedRunId != runId) {
                recalculatePositionAndSize()
                val recalculatedWidth = cachedWidth
                if (recalculatedWidth != null) {
                    return recalculatedWidth
                }
            }
            if (!isVisible) {
                return 0.0f
            }
            when (hSizing) {
                STATIC -> {
                    return max(0.0f, _width)
                }
                RELATIVE -> {
                    return if (_width < 0.0f) {
                        parent.width - (parent.shape.stroke.size * 2.0f) - padLeft - padRight + _width
                    } else {
                        (min(10000.0f, _width) / 10000.0f) * (parent.width - (parent.shape.stroke.size * 2.0f) - padLeft - padRight)
                    }
                }
                SHRINK, SHRINK_GROUP -> {
                    val shrinkGroup = _hShrinkGroup
                    if (hSizing == SHRINK_GROUP && shrinkGroup != null && shrinkGroup.isCalculated) {
                        return shrinkGroup.size
                    } else {
                        var leftWidth = 0.0f
                        var horizontalWidth = 0.0f
                        var lastHorizontal = 0.0f
                        var centerWidth = 0.0f
                        var rightWidth = 0.0f
                        layoutChildren.forEach {
                            if (it.hAlign == CENTER && it.hSizing != RELATIVE && it.hSizing != GROW) {
                                centerWidth = max(it.width + it.padLeft + it.padRight, centerWidth)
                            }
                            if (it.hAlign == RIGHT && it.hSizing != RELATIVE && it.hSizing != GROW) {
                                rightWidth = max(it.width + it.padLeft + it.padRight, rightWidth)
                            }
                            if (it.hAlign == LEFT && it.hSizing != RELATIVE && it.hSizing != GROW) {
                                if (it.layout == ABSOLUTE) {
                                    leftWidth = max(it.width + it.padLeft + it.padRight, leftWidth)
                                } else if (it.layout == HORIZONTAL) {
                                    lastHorizontal = it.width + it.padLeft + it.padRight
                                    horizontalWidth += lastHorizontal
                                } else {
                                    val paddedWidth = it.width + it.padLeft + it.padRight
                                    if (paddedWidth > lastHorizontal) {
                                        horizontalWidth += paddedWidth - lastHorizontal
                                        lastHorizontal = paddedWidth
                                    }
                                }
                            }
                        }
                        val textWidth = text.width(nvg, scale, scaleChanged, runId)
                        leftWidth = max(leftWidth, horizontalWidth)
                        return max(max(max(textWidth, leftWidth), centerWidth), rightWidth) + (2 * shape.stroke.size)
                    }
                }
                GROW -> {
                    val parentWidth = if (parent.hSizing == SHRINK && (parent.hShrinkGroup == null || parent.hShrinkGroup?.isCalculated != true)) {
                        0.0f
                    } else {
                        parent.width
                    }
                    if (hAlign == CENTER || hAlign == RIGHT) {
                        return parentWidth
                    }
                    if (layout == ABSOLUTE) {
                        return max(0.0f, parentWidth - x)
                    }
                    var leftWidth = 0.0f
                    var horizontalWidth = 0.0f
                    var lastHorizontal = 0.0f
                    var centerWidth = 0.0f
                    var rightWidth = 0.0f
                    var growCount = 0.0f
                    var growIndexOfThis = 0.0f
                    var thisBaseWidth = 0.0f
                    var modifyBaseWidth = false
                    parent.layoutChildren.forEach {
                        if (it == this) {
                            growIndexOfThis = growCount
                        }
                        if (it.hSizing == GROW) {
                            growCount++
                        }
                        if (it.hAlign == CENTER && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            centerWidth = max(it.width + it.padLeft + it.padRight, centerWidth)
                        }
                        if (it.hAlign == RIGHT && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            rightWidth = max(it.width + it.padLeft + it.padRight, rightWidth)
                        }
                        if (it.hAlign == LEFT && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            if (it.layout == ABSOLUTE) {
                                leftWidth = max(it.width + it.padLeft + it.padRight, leftWidth)
                            } else if (it.layout == HORIZONTAL) {
                                lastHorizontal = it.width + it.padLeft + it.padRight
                                horizontalWidth += lastHorizontal
                                modifyBaseWidth = false
                            } else {
                                val paddedWidth = it.width + it.padLeft + it.padRight
                                if (paddedWidth > lastHorizontal) {
                                    horizontalWidth += paddedWidth - lastHorizontal
                                    lastHorizontal = paddedWidth
                                    if (modifyBaseWidth) {
                                        thisBaseWidth = lastHorizontal
                                    }
                                }
                            }
                        }
                        if (it == this && it.hAlign == LEFT && it.layout == VERTICAL) {
                            thisBaseWidth = lastHorizontal
                            modifyBaseWidth = true
                        }
                    }
                    leftWidth = max(leftWidth, horizontalWidth)
                    val fixedChildWidth = max(max(leftWidth, centerWidth), rightWidth)
                    val growWidth = if (parent.hSizing == SHRINK) {
                        0.0f
                    } else {
                        parentWidth - fixedChildWidth
                    }
                    val thisGrowth = (growWidth / growCount) + if (growWidth % growCount > growIndexOfThis) 1 else 0
                    return thisBaseWidth + thisGrowth - padLeft - padRight
                }
            }
        }
        set(value) {
            if (_width != value) {
                _width = value
                movedOrResized = true
            }
        }

    override var height: Float
        get() {
            val finalCachedHeight = cachedHeight
            if (finalCachedHeight != null) {
                return finalCachedHeight
            } else if (cachedRunId != runId) {
                recalculatePositionAndSize()
                val recalculatedHeight = cachedHeight
                if (recalculatedHeight != null) {
                    return recalculatedHeight
                }
            }
            if (!isVisible) {
                return 0.0f
            }
            when (vSizing) {
                STATIC -> {
                    return max(0.0f, _height)
                }
                RELATIVE -> {
                    return if (_height < 0) {
                        parent.height - (parent.shape.stroke.size * 2) + _height
                    } else {
                        (min(10000.0f, _height) / 10000.0f) * (parent.height - (parent.shape.stroke.size * 2.0f))
                    }
                }
                SHRINK, SHRINK_GROUP -> {
                    val shrinkGroup = _vShrinkGroup
                    if (vSizing == SHRINK_GROUP && shrinkGroup != null && shrinkGroup.isCalculated) {
                        return shrinkGroup.size
                    } else {
                        var topHeight = 0.0f
                        var verticalHeight = 0.0f
                        var lastVertical = 0.0f
                        var middleHeight = 0.0f
                        var bottomHeight = 0.0f
                        layoutChildren.forEach {
                            if (it.vAlign == MIDDLE && it.vSizing != RELATIVE && it.vSizing != GROW) {
                                middleHeight = max(it.height + it.padTop + it.padBottom, middleHeight)
                            }
                            if (it.vAlign == BOTTOM && it.vSizing != RELATIVE && it.vSizing != GROW) {
                                bottomHeight = max(it.height + it.padTop + it.padBottom, bottomHeight)
                            }
                            if (it.vAlign == TOP && it.vSizing != RELATIVE && it.vSizing != GROW) {
                                if (it.layout == ABSOLUTE) {
                                    topHeight = max(it.height + it.padTop + it.padBottom, topHeight)
                                } else if (it.layout == VERTICAL) {
                                    lastVertical = it.height + it.padTop + it.padBottom
                                    verticalHeight += lastVertical
                                } else {
                                    val paddedHeight = it.height + it.padTop + it.padBottom
                                    if (paddedHeight > lastVertical) {
                                        verticalHeight += paddedHeight - lastVertical
                                        lastVertical = paddedHeight
                                    }
                                }
                            }
                        }
                        val textHeight = text.height(scale)
                        topHeight = max(topHeight, verticalHeight)
                        return max(max(max(textHeight, topHeight), middleHeight), bottomHeight) + (2 * shape.stroke.size)
                    }
                }
                GROW -> {
                    val parentHeight = if (parent.vSizing == SHRINK && (parent.vShrinkGroup == null || parent.vShrinkGroup?.isCalculated != true)) {
                        0.0f
                    } else {
                        parent.height
                    }
                    if (vAlign == MIDDLE || vAlign == BOTTOM) {
                        return parentHeight
                    }
                    if (layout == ABSOLUTE) {
                        return max(0.0f, parentHeight - y)
                    }
                    var topHeight = 0.0f
                    var verticalHeight = 0.0f
                    var lastVertical = 0.0f
                    var middleHeight = 0.0f
                    var bottomHeight = 0.0f
                    var growCount = 0.0f
                    var growIndexOfThis = 0.0f
                    var thisBaseHeight = 0.0f
                    var modifyBaseHeight = false
                    parent.layoutChildren.forEach {
                        if (it == this) {
                            growIndexOfThis = growCount
                        }
                        if (it.vSizing == GROW) {
                            growCount++
                        }
                        if (it.vAlign == MIDDLE && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            middleHeight = max(it.height + it.padTop + it.padBottom, middleHeight)
                        }
                        if (it.vAlign == BOTTOM && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            bottomHeight = max(it.height + it.padTop + it.padBottom, bottomHeight)
                        }
                        if (it.vAlign == TOP && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            if (it.layout == ABSOLUTE) {
                                topHeight = max(it.height + it.padTop + it.padBottom, topHeight)
                            } else if (it.layout == VERTICAL) {
                                lastVertical = it.height + it.padTop + it.padBottom
                                verticalHeight += it.height
                                modifyBaseHeight = false
                            } else {
                                val paddedHeight = it.height + it.padTop + it.padBottom
                                if (paddedHeight > lastVertical) {
                                    verticalHeight += paddedHeight - lastVertical
                                    lastVertical = paddedHeight
                                    if (modifyBaseHeight) {
                                        thisBaseHeight = lastVertical
                                    }
                                }
                            }
                        }
                        if (it == this && it.vAlign == TOP && it.layout == HORIZONTAL) {
                            thisBaseHeight = lastVertical
                            modifyBaseHeight = true
                        }
                    }
                    topHeight = max(topHeight, verticalHeight)
                    val fixedChildHeight = max(max(topHeight, middleHeight), bottomHeight)
                    val growHeight = if (parent.vSizing == SHRINK) {
                        0.0f
                    } else {
                        parentHeight - fixedChildHeight
                    }
                    val thisGrowth = (growHeight / growCount) + if (growHeight % growCount > growIndexOfThis) 1 else 0
                    return thisBaseHeight + thisGrowth - padTop - padBottom
                }
            }
        }
        set(value) {
            if (_height != value) {
                _height = value
                movedOrResized = true
            }
        }

    override var x: Float
        get() {
            val finalCachedX = cachedX
            if (finalCachedX != null) {
                return finalCachedX
            } else if (cachedRunId != runId) {
                recalculatePositionAndSize()
                val recalculatedX = cachedX
                if (recalculatedX != null) {
                    return recalculatedX
                }
            }
            if (layout == ABSOLUTE || lastBlock == null) {
                return getXRelativeTo(parent.x, parent.width)
            }
            if (layout == HORIZONTAL) {
                return getXRelativeTo(lastBlock!!.x + lastBlock!!.width + lastBlock!!.padRight + padLeft, parent.width)
            }
            return getXRelativeTo(parent.x, parent.width)
        }
        set(value) = ignore

    override var y: Float
        get() {
            val finalCachedY = cachedY
            if (finalCachedY != null) {
                return finalCachedY
            } else if (cachedRunId != runId) {
                recalculatePositionAndSize()
                val recalculatedY = cachedY
                if (recalculatedY != null) {
                    return recalculatedY
                }
            }
            if (layout == ABSOLUTE || lastBlock == null) {
                return getYRelativeTo(parent.y, parent.height)
            }
            if (layout == HORIZONTAL) {
                return getYRelativeTo(parent.y, parent.height)
            }
            return getYRelativeTo(lastBlock!!.y + lastBlock!!.height + lastBlock!!.padBottom + padTop, parent.height)

        }
        set(value) = ignore

    private var cachedRunId: Long = -1
    private var cachedX: Float? = null
    private var cachedY: Float? = null
    private var cachedWidth: Float? = null
    private var cachedHeight: Float? = null

    override fun clearPositionAndSize() {
        cachedRunId = -1
        cachedX = null
        cachedY = null
        cachedWidth = null
        cachedHeight = null
    }

    override fun recalculatePositionAndSize() {
        if (cachedRunId != runId) {
            cachedRunId = runId
            cachedX = x
            cachedY = y
            cachedWidth = width
            cachedHeight = height
        }
    }

    private fun getXRelativeTo(relativeX: Float, relativeWidth: Float): Float {
        if (width > relativeWidth) {
            if (hTruncate == TRUNCATE_RIGHT) {
                return relativeX + xOffset
            }
            if (hTruncate == TRUNCATE_LEFT) {
                return relativeX + relativeWidth - width + xOffset
            }
            return relativeX + (relativeWidth - padLeft) / 2 - width / 2 + xOffset
        } else {
            if (hAlign == LEFT) {
                return relativeX + padLeft + xOffset
            }
            if (hAlign == RIGHT) {
                return relativeX + relativeWidth - width - padRight + xOffset
            }
            return relativeX + (relativeWidth - padLeft - padRight) / 2 - width / 2 + xOffset + padLeft
        }
    }

    private fun getYRelativeTo(relativeY: Float, relativeHeight: Float): Float {
        if (height > relativeHeight) {
            if (vTruncate == TRUNCATE_BOTTOM) {
                return relativeY + yOffset
            }
            if (vTruncate == TRUNCATE_TOP) {
                return relativeY + relativeHeight - height + yOffset
            }
            return relativeY + (relativeHeight - padTop) / 2 - height / 2 + yOffset
        } else {
            if (vAlign == TOP) {
                return relativeY + padTop + yOffset
            }
            if (vAlign == BOTTOM) {
                return relativeY + relativeHeight - height - padBottom + yOffset
            }
            return relativeY + (relativeHeight - padTop - padBottom) / 2 - height / 2 + yOffset + padTop
        }
    }
}