package com.grimfox.gec.ui.widgets

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.ui.NO_COLOR
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.HorizontalTruncation.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import com.grimfox.gec.ui.widgets.VerticalTruncation.*
import com.grimfox.gec.util.Reference
import com.grimfox.gec.util.Utils.LOG
import com.grimfox.gec.util.cRef
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NVGTextRow
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAddress
import java.lang.Math.max
import java.lang.Math.min
import java.nio.ByteBuffer
import java.util.*

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

class FillColor(val color: NVGColor) : Fill {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        nvgFillColor(nvg, color)
        nvgFill(nvg)
    }
}

class FillImageDynamic(val image: Int) : Fill {

    private val paint = NVGPaint.create()

    override fun draw(nvg: Long, block: Block, scale: Float) {
        nvgImagePattern(nvg, Math.round(block.x * scale).toFloat(), Math.round(block.y * scale).toFloat(), Math.round(block.width * scale).toFloat(), Math.round(block.height * scale).toFloat(), 0.0f, image, 1.0f, paint)
        nvgFillPaint(nvg, paint)
        nvgFill(nvg)
    }
}

class FillImageStatic(val image: Int, val width: Int, val height: Int) : Fill {

    private val paint = NVGPaint.create()

    override fun draw(nvg: Long, block: Block, scale: Float) {
        nvgImagePattern(nvg, Math.round(block.x * scale).toFloat(), Math.round(block.y * scale).toFloat(), Math.round(block.width * scale).toFloat(), Math.round(block.height * scale).toFloat(), 0.0f, image, 1.0f, paint)
        nvgFillPaint(nvg, paint)
        nvgFill(nvg)
    }
}

class FillBoxGradient(val innerColor: NVGColor, val outerColor: NVGColor, val cornerRadius: Float, val feather: Float) : Fill {

    private val paint = NVGPaint.create()

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

class StrokeInvisible(override val size: Float) : Stroke {

    override fun draw(nvg: Long, block: Block, scale: Float) {
    }
}

class StrokeColor(val color: NVGColor, override val size: Float) : Stroke {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        nvgStrokeColor(nvg, color)
        nvgStrokeWidth(nvg, size * scale)
        nvgStroke(nvg)
    }
}

open class TextStyle(open val size: Reference<Float>, open val font: Reference<Int>, open val color: Reference<NVGColor>)

interface Text {

    var style: TextStyle
    val data: ByteBuffer
    val length: Int

    fun draw(nvg: Long, block: Block, scale: Float)

    fun dimensions(nvg: Long): Pair<Float, Float>
}

private class TextNone : Text {

    override var style: TextStyle = TextStyle(cRef(0.0f), cRef(-1), cRef(NO_COLOR))
    override val data: ByteBuffer = ByteBuffer.wrap(ByteArray(0))
    override val length: Int = 0

    override fun draw(nvg: Long, block: Block, scale: Float) {
    }

    override fun dimensions(nvg: Long): Pair<Float, Float> {
        return Pair(0.0f, 0.0f)
    }
}

open class DynamicTextParagraphUtf8(override val data: ByteBuffer, val verticalSpace: Float, override var style: TextStyle) : Text {

    override val length: Int get() = data.limit()
    private val lineHeight = BufferUtils.createFloatBuffer(1)
    private val rows = NVGTextRow.create(3)

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
        var rowCount: Int = nnvgTextBreakLines(nvg, start, end, width, memAddress(rows), 3)
        if (block.vAlign == TOP) {
            while (rowCount != 0) {
                for (i in 0..rowCount - 1) {
                    val row = rows.get(i)
                    nnvgText(nvg, x, y, row.start(), row.end())
                    y += scaledHeight
                }
                start = rows.get(rowCount - 1).next()
                rowCount = nnvgTextBreakLines(nvg, start, end, width, memAddress(rows), 3)
            }
        } else {
            var yDelta = 0.0f
            while (rowCount != 0) {
                for (i in 0..rowCount - 1) {
                    yDelta += scaledHeight
                }
                start = rows.get(rowCount - 1).next()
                rowCount = nnvgTextBreakLines(nvg, start, end, width, memAddress(rows), 3)
            }
            yDelta -= verticalSpace * scale
            if (block.vAlign == MIDDLE) {
                y -= (yDelta - lineHeight.get(0)) * 0.5f
            }
            if (block.vAlign == BOTTOM) {
                y -= yDelta - lineHeight.get(0)
            }
            start = memAddress(data)
            rowCount = nnvgTextBreakLines(nvg, start, end, width, memAddress(rows), 3)
            while (rowCount != 0) {
                for (i in 0..rowCount - 1) {
                    val row = rows.get(i)
                    nnvgText(nvg, x, y, row.start(), row.end())
                    y += scaledHeight
                }
                start = rows.get(rowCount - 1).next()
                rowCount = nnvgTextBreakLines(nvg, start, end, width, memAddress(rows), 3)
            }
        }
    }

    override fun dimensions(nvg: Long): Pair<Float, Float> {
        return Pair(0.0f, 0.0f)
    }
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
        nvgText(nvg, x * scale, y * scale, data, NULL)
    }

    override fun dimensions(nvg: Long): Pair<Float, Float> {
        twr(stackPush()) { stack ->
            val bounds = stack.mallocFloat(4)
            nvgFontFaceId(nvg, style.font.value)
            nvgFontSize(nvg, style.size.value)
            nvgTextBounds(nvg, 0f, 0f, data, NULL, bounds)
            return Pair(bounds[2] - bounds[0], bounds[3] - bounds[1])
        }
    }
}

class StaticTextUtf8(string: String, style: TextStyle) : DynamicTextUtf8(MemoryUtil.memUTF8(string, true), style) {

    override val length: Int = data.limit()
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

class ShapeCursor(override val fill: Fill, override val stroke: Stroke, val selectFill: Fill, val caret: Caret, var timeOffset: Long = 0) : Shape {

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

class ShapeRoundedRectangle(override val fill: Fill, override val stroke: Stroke, val cornerRadius: Float) : Shape {

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgRoundedRect(nvg, (block.x + halfStroke) * scale, (block.y + halfStroke) * scale, (block.width - stroke.size) * scale, (block.height - stroke.size) * scale, (min(cornerRadius, min(block.width, block.height) / 2.0f)) * scale)
        fill.draw(nvg, block, scale)
        stroke.draw(nvg, block, scale)
    }
}

class ShapeDropShadow(override val fill: Fill, override val stroke: Stroke, val inset: Float, val cornerRadius: Float) : Shape {

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

class ShapeTriangle(override val fill: Fill, override val stroke: Stroke, val direction: Direction) : Shape {

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

class ShapeMeshViewport3D(val viewport: MeshViewport3D) : Shape {

    override val fill = NO_FILL
    override val stroke = NO_STROKE

    override fun draw(nvg: Long, block: Block, scale: Float) {
        val doubleScale = scale.toDouble()
        nvgSave(nvg)
        nvgReset(nvg)
        viewport.onDrawFrame(Math.ceil(block.x * doubleScale).toInt(), Math.ceil(block.y * doubleScale).toInt(), Math.ceil(block.width * doubleScale).toInt(), Math.ceil(block.height * doubleScale).toInt(), Math.ceil(block.root.height * doubleScale).toInt(), scale)
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
        if (isCalculated) {
            return _size
        } else {
            var max = 0.0f
            blocks.forEach {
                if (horizontal) {
                    max = max(it.width, max)
                } else {
                    max = max(it.height, max)
                }
            }
            _size = max
            isCalculated = true
            return _size
        }
    }
}

fun hShrinkGroup(): ShrinkGroup {
    return ShrinkGroupInternal(ArrayList(), true)
}

fun vShrinkGroup(): ShrinkGroup {
    return ShrinkGroupInternal(ArrayList(), false)
}

abstract class Block {
    abstract val root: Block
    abstract val parent: Block
    abstract var nvg: Long
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
    abstract var onTick: (Block.(mouseX: Int, mouseY: Int) -> Unit)?
    abstract var inputOverride: Block?
    protected abstract var mouseOver: Block?
    protected abstract var lastMouseOver: Block?
    protected abstract var awaitingRelease: MutableList<Triple<Int, Block, Int>>
    protected abstract var awaitingMouseDownOverOther: MutableList<Triple<Int, Block, Int>>

    private var reprocess = false

    fun reprocessTick() {
        root.reprocess = true
    }

    fun handleMouseAction(button: Int, x: Int, y: Int, isDown: Boolean, mods: Int) {
        if (this === root) {
            do {
                reprocess = false
                val mouseOver = mouseOver
                if (mouseOver != null) {
                    if (isDown) {
                        val mouseDownFun = mouseOver.onMouseDown
                        if (mouseDownFun != null) {
                            mouseOver.mouseDownFun(button, x, y, mods)
                        }
                        val toRemove = ArrayList<Triple<Int, Block, Int>>(awaitingMouseDownOverOther.size)
                        var needToAdd = true
                        awaitingMouseDownOverOther.forEach {
                            if (it.first == button) {
                                if (it.second == mouseOver) {
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
                        val pair = Triple(button, mouseOver, mods)
                        awaitingRelease.add(pair)
                        if (needToAdd) {
                            awaitingMouseDownOverOther.add(pair)
                        }
                    } else {
                        val mouseUpFun = mouseOver.onMouseUp
                        if (mouseUpFun != null) {
                            mouseOver.mouseUpFun(button, x, y, mods)
                        }
                        awaitingRelease.forEach {
                            if (it.first == button && it.second == mouseOver) {
                                val mouseClickFun = mouseOver.onMouseClick
                                if (mouseClickFun != null) {
                                    mouseOver.mouseClickFun(button, x, y, mods)
                                }
                            }
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
                    handleNewMousePosition(nvg, x , y)
                }
            } while (reprocess)
        } else {
            root.handleMouseAction(button, x, y, isDown, mods)
        }
    }

    fun handleScroll(scrollX: Double, scrollY: Double) {
        if (this === root) {
            reprocess = false
            val mouseOver = mouseOver
            val scrollFun = mouseOver?.onScroll
            if (mouseOver != null && scrollFun != null) {
                mouseOver.scrollFun(scrollX, scrollY)
            }
        } else {
            root.handleScroll(scrollX, scrollY)
        }
    }

    fun handleNewMousePosition(nvg: Long, mouseX: Int, mouseY: Int) {
        if (this === root) {
            val reprocessAfter = reprocess
            do {
                reprocess = false
                lastMouseOver = mouseOver
                mouseOver = if (inputOverride != null) {
                    getMouseOverBlock(nvg, mouseX, mouseY)
                    inputOverride
                } else {
                    getMouseOverBlock(nvg, mouseX, mouseY)
                }
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

    private fun getMouseOverBlock(nvg: Long, mouseX: Int, mouseY: Int): Block? {
        if (isVisible) {
            this.nvg = nvg
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

    private fun getMouseOverBlock(mouseX: Int, mouseY: Int): Block? {
        if (isVisible) {
            if (!isMouseAware || ((mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) && !isFallThrough)) {
                return null
            }
            (renderChildren.size - 1 downTo 0)
                    .mapNotNull { renderChildren[it].getMouseOverBlock(mouseX, mouseY) }
                    .forEach { return it }
            if (isFallThrough) {
                return null
            }
            return this
        }
        return null
    }

    fun draw(nvg: Long, scale: Float) {
        if (isVisible) {
            this.nvg = nvg
            prepareForIteration()
            draw(ScissorStack(nvg), scale)
        }
    }

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
            it.nvg = nvg
            if (onTicks!= null) {
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

    operator fun invoke(builder: Block.() -> Unit): Block {
        this.builder()
        return this
    }

    fun with(builder: Block.() -> Unit): Block {
        return invoke(builder)
    }
}

private open class RootBlock(override var x: Float, override var y: Float, override var width: Float, override var height: Float) : Block() {
    override var hShrinkGroup: ShrinkGroup?
        get() = null
        set(value) {
        }
    override var vShrinkGroup: ShrinkGroup?
        get() = null
        set(value) {
        }
    override val root: RootBlock
        get() = this
    override val parent: RootBlock
        get() = this
    override val layoutChildren = ArrayList<Block>()
    override val renderChildren = ArrayList<Block>()
    override var nvg: Long = -1
    override var isVisible: Boolean
        get() = true
        set(value) {
        }
    override var hAlign: HorizontalAlignment
        get() = LEFT
        set(value) {
        }
    override var vAlign: VerticalAlignment
        get() = TOP
        set(value) {
        }
    override var hTruncate: HorizontalTruncation
        get() = TRUNCATE_RIGHT
        set(value) {
        }
    override var vTruncate: VerticalTruncation
        get() = TRUNCATE_BOTTOM
        set(value) {
        }
    override var layout: Layout
        get() = ABSOLUTE
        set(value) {
        }
    override var xOffset: Float
        get() = 0.0f
        set(value) {
        }
    override var yOffset: Float
        get() = 0.0f
        set(value) {
        }
    override var hSizing: Sizing
        get() = STATIC
        set(value) {
        }
    override var vSizing: Sizing
        get() = STATIC
        set(value) {
        }
    override var padLeft: Float
        get() = 0.0f
        set(value) {
        }
    override var padRight: Float
        get() = 0.0f
        set(value) {
        }
    override var padTop: Float
        get() = 0.0f
        set(value) {
        }
    override var padBottom: Float
        get() = 0.0f
        set(value) {
        }
    override var shape: Shape
        get() = NO_SHAPE
        set(value) {
        }
    override var text: Text
        get() = NO_TEXT
        set(value) {
        }
    override var lastBlock: Block?
        get() = null
        set(value) {
        }
    override var isMouseAware: Boolean
        get() = true
        set(value) {
        }
    override var isFallThrough: Boolean
        get() = false
        set(value) {
        }
    override var canOverflow: Boolean
        get() = false
        set(value) {
        }
    override var overflowCount: Int
        get() = -1
        set(value) {
        }
    override var onMouseOver: (Block.() -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseOut: (Block.() -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseDown: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseUp: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseRelease: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseDownOverOther: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseClick: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseDrag: (Block.(Int, Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onScroll: (Block.(Double, Double) -> Unit)?
        get() = null
        set(value) {
        }
    override var onTick: (Block.(Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var inputOverride: Block? = null
    override var awaitingRelease: MutableList<Triple<Int, Block, Int>> = ArrayList()
    override var mouseOver: Block? = null
    override var lastMouseOver: Block? = null
    override var awaitingMouseDownOverOther: MutableList<Triple<Int, Block, Int>> = ArrayList()
}

val NO_BLOCK: Block = object : RootBlock(-1.0f, -1.0f, -1.0f, -1.0f) {
    override var isVisible: Boolean
        get() = false
        set(value) {
        }
    override var inputOverride: Block?
        get() = null
        set(value) {
        }
}

private class DefaultBlock(
        override val root: Block,
        override val parent: Block,
        override var nvg: Long = -1,
        override val layoutChildren: MutableList<Block> = ArrayList(),
        override val renderChildren: MutableList<Block> = ArrayList(),
        override var isVisible: Boolean = true,
        override var hAlign: HorizontalAlignment = LEFT,
        override var vAlign: VerticalAlignment = TOP,
        override var hTruncate: HorizontalTruncation = if (hAlign == RIGHT) TRUNCATE_LEFT else if (hAlign == LEFT) TRUNCATE_RIGHT else TRUNCATE_CENTER,
        override var vTruncate: VerticalTruncation = if (vAlign == BOTTOM) TRUNCATE_TOP else if (vAlign == TOP) TRUNCATE_BOTTOM else TRUNCATE_MIDDLE,
        override var layout: Layout = ABSOLUTE,
        override var xOffset: Float = 0.0f,
        override var yOffset: Float = 0.0f,
        override var hSizing: Sizing = RELATIVE,
        override var vSizing: Sizing = RELATIVE,
        width: Float = 10000.0f,
        height: Float = 10000.0f,
        override var padLeft: Float = 0.0f,
        override var padRight: Float = 0.0f,
        override var padTop: Float = 0.0f,
        override var padBottom: Float = 0.0f,
        override var shape: Shape = NO_SHAPE,
        override var text: Text = NO_TEXT,
        override var canOverflow: Boolean = false,
        override var overflowCount: Int = -1,
        override var lastBlock: Block? = null,
        override var isMouseAware: Boolean = parent.isMouseAware,
        override var isFallThrough: Boolean = false,
        override var onMouseOver: (Block.() -> Unit)? = null,
        override var onMouseOut: (Block.() -> Unit)? = null,
        override var onMouseDown: (Block.(Int, Int, Int, Int) -> Unit)? = null,
        override var onMouseUp: (Block.(Int, Int, Int, Int) -> Unit)? = null,
        override var onMouseRelease: (Block.(Int, Int, Int, Int) -> Unit)? = null,
        override var onMouseDownOverOther: (Block.(Int, Int, Int, Int) -> Unit)? = null,
        override var onMouseClick: (Block.(Int, Int, Int, Int) -> Unit)? = null,
        override var onMouseDrag: (Block.(Int, Int, Int, Int) -> Unit)? = null,
        override var onScroll: (Block.(Double, Double) -> Unit)? = null,
        override var onTick: (Block.(Int, Int) -> Unit)? = null) : Block() {

    override var inputOverride: Block?
        get() = root.inputOverride
        set(value) {
            root.inputOverride = value
        }

    override var awaitingRelease: MutableList<Triple<Int, Block, Int>>
        get() = ArrayList()
        set(value) {
        }

    override var mouseOver: Block?
        get() = null
        set(value) {
        }
    override var lastMouseOver: Block?
        get() = null
        set(value) {
        }

    override var awaitingMouseDownOverOther: MutableList<Triple<Int, Block, Int>>
        get() = ArrayList()
        set(value) {}

    private var _hShrinkGroup: ShrinkGroupInternal? = null
    private var _vShrinkGroup: ShrinkGroupInternal? = null

    override var hShrinkGroup: ShrinkGroup?
        get() = _hShrinkGroup
        set(value) {
            if (value is ShrinkGroupInternal) {
                _hShrinkGroup?.blocks?.remove(this)
                val shrinkGroup = value
                if (!shrinkGroup.blocks.contains(this)) {
                    shrinkGroup.blocks.add(this)
                }
                _hShrinkGroup = shrinkGroup
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
                val shrinkGroup = value
                if (!shrinkGroup.blocks.contains(this)) {
                    shrinkGroup.blocks.add(this)
                }
                _vShrinkGroup = shrinkGroup
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

    private var _width = width
    private var _height = height

    override var width: Float
        get() {
            if (!isVisible) {
                return 0.0f
            }
            when (hSizing) {
                STATIC -> {
                    return max(0.0f, _width)
                }
                RELATIVE -> {
                    if (_width < 0.0f) {
                        return parent.width - (parent.shape.stroke.size * 2.0f) - padLeft - padRight + _width
                    } else {
                        return (min(10000.0f, _width) / 10000.0f) * (parent.width - (parent.shape.stroke.size * 2.0f) - padLeft - padRight)
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
                        val textDimensions = text.dimensions(nvg)
                        leftWidth = max(leftWidth, horizontalWidth)
                        return max(max(max(textDimensions.first, leftWidth), centerWidth), rightWidth) + (2 * shape.stroke.size)
                    }
                }
                GROW -> {
                    val parentWidth = if (parent.hSizing == SHRINK && (parent.hShrinkGroup == null || !(parent.hShrinkGroup?.isCalculated ?: false))) {
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
            _width = value
        }

    override var height: Float
        get() {
            if (!isVisible) {
                return 0.0f
            }
            when (vSizing) {
                STATIC -> {
                    return max(0.0f, _height)
                }
                RELATIVE -> {
                    if (_height < 0) {
                        return parent.height - (parent.shape.stroke.size * 2) + _height
                    } else {
                        return (min(10000.0f, _height) / 10000.0f) * (parent.height - (parent.shape.stroke.size * 2.0f))
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
                        val textDimensions = text.dimensions(nvg)
                        topHeight = max(topHeight, verticalHeight)
                        return max(max(max(textDimensions.second, topHeight), middleHeight), bottomHeight) + (2 * shape.stroke.size)
                    }
                }
                GROW -> {
                    val parentHeight = if (parent.vSizing == SHRINK && (parent.vShrinkGroup == null || !(parent.vShrinkGroup?.isCalculated ?: false))) {
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
            _height = value
        }

    override var x: Float
        get() {
            if (layout == ABSOLUTE || lastBlock == null) {
                return getXRelativeTo(parent.x, parent.width)
            }
            if (layout == HORIZONTAL) {
                return getXRelativeTo(lastBlock!!.x + lastBlock!!.width + lastBlock!!.padRight + padLeft, parent.width)
            }
            return getXRelativeTo(parent.x, parent.width)
        }
        set(value) {
        }

    override var y: Float
        get() {
            if (layout == ABSOLUTE || lastBlock == null) {
                return getYRelativeTo(parent.y, parent.height)
            }
            if (layout == HORIZONTAL) {
                return getYRelativeTo(parent.y, parent.height)
            }
            return getYRelativeTo(lastBlock!!.y + lastBlock!!.height + lastBlock!!.padBottom + padTop, parent.height)

        }
        set(value) {
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