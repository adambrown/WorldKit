package com.grimfox.gec.ui.widgets

import com.grimfox.gec.extensions.twr
import com.grimfox.gec.ui.widgets.HorizontalAlignment.*
import com.grimfox.gec.ui.widgets.Layout.*
import com.grimfox.gec.ui.widgets.Sizing.*
import com.grimfox.gec.ui.widgets.VerticalAlignment.*
import org.joml.Vector4f
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer
import java.util.*

private val NO_COLOR = nvgRGBA(0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(), NVGColor.create())

enum class HorizontalAlignment {
    LEFT,
    RIGHT,
    CENTER
}

enum class VerticalAlignment {
    TOP,
    MIDDLE,
    BOTTOM
}

enum class Layout {
    ABSOLUTE,
    HORIZONTAL,
    VERTICAL
}

enum class Sizing {
    STATIC,
    SHRINK,
    GROW,
    RELATIVE
}

class ScissorStack(val nvg: Long) {

    private val stack = ArrayList<Vector4f>()

    fun push(clip: Vector4f): Vector4f {
        if (stack.isNotEmpty()) {
            val current = stack.last()
            if (clip.x < current.x) {
                clip.x = current.x
            }
            if (clip.y < current.y) {
                clip.y = current.y
            }
            if (clip.z > current.z) {
                clip.z = current.z
            }
            if (clip.w > current.w) {
                clip.w = current.w
            }
        }
        nvgScissor(nvg, clip.x, clip.y, clip.z - clip.x, clip.w - clip.y)
        stack.add(clip)
        return clip
    }

    fun pop(): Vector4f? {
        if (stack.isEmpty()) {
            return null
        }
        val top = stack.removeAt(stack.size - 1)
        if (stack.isEmpty()) {
            nvgResetScissor(nvg)
        } else {
            val current = stack.last()
            nvgScissor(nvg, current.x, current.y, current.z - current.x, current.w - current.y)
        }
        return top
    }
}

interface Fill {

    fun draw(nvg: Long, block: Block)
}

private class FillNone() : Fill {

    override fun draw(nvg: Long, block: Block) {
    }
}

class FillColor(val color: NVGColor) : Fill {

    override fun draw(nvg: Long, block: Block) {
        nvgFillColor(nvg, color)
        nvgFill(nvg)
    }
}

class FillImageDynamic(val image: Int) : Fill {

    private val paint = NVGPaint.create()

    override fun draw(nvg: Long, block: Block) {
        nvgImagePattern(nvg, block.x.toFloat(), block.y.toFloat(), block.width.toFloat(), block.height.toFloat(), 0.0f, image, 1.0f, paint)
        nvgFillPaint(nvg, paint)
        nvgFill(nvg)
    }
}

class FillImageStatic(val image: Int, val width: Int, val height: Int) : Fill {

    private val paint = NVGPaint.create()

    override fun draw(nvg: Long, block: Block) {
        nvgImagePattern(nvg, 0.0f, 0.0f, width.toFloat(), height.toFloat(), 0.0f, image, 1.0f, paint)
        nvgFillPaint(nvg, paint)
        nvgFill(nvg)
    }
}

interface Stroke {

    val size: Float

    fun draw(nvg: Long, block: Block)
}

private class StrokeNone() : Stroke {

    override val size = 0.0f

    override fun draw(nvg: Long, block: Block) {
    }
}

class StrokeInvisible(override val size: Float) : Stroke {

    override fun draw(nvg: Long, block: Block) {
    }
}

class StrokeColor(val color: NVGColor, override val size: Float) : Stroke {

    override fun draw(nvg: Long, block: Block) {
        nvgStrokeColor(nvg, color)
        nvgStrokeWidth(nvg, size)
        nvgStroke(nvg)
    }
}

open class TextStyle(open val size: Float, open val font: Int, open val color: NVGColor)

interface Text {

    var style: TextStyle
    val data: ByteBuffer
    val length: Int

    fun draw(nvg: Long, block: Block)

    fun dimensions(nvg: Long): Pair<Float, Float>
}

private class TextNone() : Text {

    override var style: TextStyle = TextStyle(0.0f, -1, NO_COLOR)
    override val data: ByteBuffer = ByteBuffer.wrap(ByteArray(0))
    override val length: Int = 0

    override fun draw(nvg: Long, block: Block) {
    }

    override fun dimensions(nvg: Long): Pair<Float, Float> {
        return Pair(0.0f, 0.0f)
    }
}

class StaticTextUtf8(string: String, override var style: TextStyle) : Text {

    override val data: ByteBuffer = MemoryUtil.memUTF8(string, true)
    override val length: Int = data.limit()

    override fun draw(nvg: Long, block: Block) {
        nvgFontFaceId(nvg, style.font)
        nvgFontSize(nvg, style.size)
        val (x , y, alignMask) = calculatePositionAndAlignmentForText(block)
        nvgTextAlign(nvg, alignMask)
        nvgFillColor(nvg, style.color)
        nvgText(nvg, x, y, data, NULL)
    }

    override fun dimensions(nvg: Long): Pair<Float, Float> {
        twr(stackPush()) { stack ->
            val bounds = stack.mallocFloat(4)
            nvgFontFaceId(nvg, style.font)
            nvgFontSize(nvg, style.size)
            nvgTextBounds(nvg, 0f, 0f, data, NULL, bounds)
            return Pair(bounds[2] - bounds[0], bounds[3] - bounds[1])
        }
    }
}

class DynamicTextUtf8(override val data: ByteBuffer, override var style: TextStyle) : Text {

    override val length: Int get() = data.limit()

    override fun draw(nvg: Long, block: Block) {
        nvgFontFaceId(nvg, style.font)
        nvgFontSize(nvg, style.size)
        val (x , y, alignMask) = calculatePositionAndAlignmentForText(block)
        nvgTextAlign(nvg, alignMask)
        nvgFillColor(nvg, style.color)
        nvgText(nvg, x, y, data, NULL)
    }

    override fun dimensions(nvg: Long): Pair<Float, Float> {
        twr(stackPush()) { stack ->
            val bounds = stack.mallocFloat(4)
            nvgFontFaceId(nvg, style.font)
            nvgFontSize(nvg, style.size)
            nvgTextBounds(nvg, 0f, 0f, data, NULL, bounds)
            return Pair(bounds[2] - bounds[0], bounds[3] - bounds[1])
        }
    }
}

private fun calculatePositionAndAlignmentForText(block: Block): Triple<Float, Float, Int> {
    val x: Float
    val hAlignMask = when (block.hAlign) {
        LEFT -> {
            x = block.x.toFloat()
            NVG_ALIGN_LEFT
        }
        RIGHT -> {
            x = block.x.toFloat() + block.width
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
            y = block.y.toFloat()
            NVG_ALIGN_TOP
        }
        BOTTOM -> {
            y = block.y.toFloat() + block.height
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

    fun draw(nvg: Long, block: Block)
}

private class ShapeNone() : Shape {

    override val fill: Fill = NO_FILL
    override val stroke: Stroke = NO_STROKE

    override fun draw(nvg: Long, block: Block) {
    }
}

val NO_FILL: Fill = FillNone()

val NO_STROKE: Stroke = StrokeNone()

val NO_SHAPE: Shape = ShapeNone()

val NO_TEXT: Text = TextNone()


class ShapeRectangle(override val fill: Fill, override val stroke: Stroke) : Shape {

    override fun draw(nvg: Long, block: Block) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgRect(nvg, block.x.toFloat() + halfStroke, block.y.toFloat() + halfStroke, block.width.toFloat() - stroke.size, block.height.toFloat() - stroke.size)
        fill.draw(nvg, block)
        stroke.draw(nvg, block)
    }
}

class ShapeCircle(override val fill: Fill, override val stroke: Stroke) : Shape {

    override fun draw(nvg: Long, block: Block) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgCircle(nvg, block.x + (block.width / 2.0f), block.y + (block.height / 2.0f), Math.min(block.width, block.height) / 2.0f - halfStroke)
        fill.draw(nvg, block)
        stroke.draw(nvg, block)
    }
}

class ShapeEllipse(override val fill: Fill, override val stroke: Stroke) : Shape {

    override fun draw(nvg: Long, block: Block) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgEllipse(nvg, (block.x.toFloat() + block.width.toFloat()) / 2.0f, (block.y.toFloat() + block.height.toFloat()) / 2.0f, block.width / 2.0f - halfStroke, block.height / 2.0f - halfStroke)
        fill.draw(nvg, block)
        stroke.draw(nvg, block)
    }
}

class ShapeRoundedRectangle(override val fill: Fill, override val stroke: Stroke, val cornerRadius: Float) : Shape {

    override fun draw(nvg: Long, block: Block) {
        val halfStroke = stroke.size / 2.0f
        nvgBeginPath(nvg)
        nvgRoundedRect(nvg, block.x.toFloat() + halfStroke, block.y.toFloat() + halfStroke, block.width.toFloat() - stroke.size, block.height.toFloat() - stroke.size, Math.min(cornerRadius, Math.min(block.width, block.height) / 2.0f))
        fill.draw(nvg, block)
        stroke.draw(nvg, block)
    }

}

fun uiRoot(x: Int, y: Int, width: Int, height: Int, builder: Block.() -> Unit): Block {
    val root = RootBlock(x, y, width, height)
    root.builder()
    return root
}

abstract class Block {
    abstract val root: Block
    abstract val parent: Block
    abstract var nvg: Long
    abstract val children: MutableList<Block>
    abstract var isVisible: Boolean
    abstract var hAlign: HorizontalAlignment
    abstract var vAlign: VerticalAlignment
    abstract var layout: Layout
    abstract var xOffset: Int
    abstract var yOffset: Int
    abstract var x: Int
    abstract var y: Int
    abstract var hSizing: Sizing
    abstract var width: Int
    abstract var vSizing: Sizing
    abstract var height: Int
    abstract var padLeft: Int
    abstract var padRight: Int
    abstract var padTop: Int
    abstract var padBottom: Int
    abstract var shape: Shape
    abstract var text: Text
    abstract var lastBlock: Block?
    abstract var isMouseAware: Boolean
    abstract var onMouseOver: (Block.() -> Unit)?
    abstract var onMouseOut: (Block.() -> Unit)?
    abstract var onMouseDown: (Block.(button: Int, x: Int, y: Int) -> Unit)?
    abstract var onMouseUp: (Block.(button: Int, x: Int, y: Int) -> Unit)?
    abstract var onMouseRelease: (Block.(button: Int, x: Int, y: Int) -> Unit)?
    abstract var onMouseClick: (Block.(button: Int, x: Int, y: Int) -> Unit)?
    protected abstract var mouseOver: Block?
    protected abstract var lastMouseOver: Block?
    protected abstract var awaitingRelease: MutableList<Pair<Int, Block>>

    fun handleMouseAction(button: Int, x: Int, y: Int, isDown: Boolean) {
        val mouseOver = mouseOver
        if (mouseOver != null) {
            if (isDown) {
                val mouseDownFun = mouseOver.onMouseDown
                if (mouseDownFun != null) {
                    awaitingRelease.add(Pair(button, mouseOver))
                    mouseOver.mouseDownFun(button, x, y)
                }
            } else {
                val mouseUpFun = mouseOver.onMouseUp
                if (mouseUpFun != null) {
                    mouseOver.mouseUpFun(button, x, y)
                }
                awaitingRelease.forEach {
                    if (it.first == button && it.second == mouseOver) {
                        val mouseClickFun = mouseOver.onMouseClick
                        if (mouseClickFun != null) {
                            mouseOver.mouseClickFun(button, x, y)
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
                        it.second.mouseReleaseFun(button, x, y)
                    }
                }
            }
        }
    }

    fun handleNewMousePosition(nvg: Long, mouseX: Int, mouseY: Int) {
        if (this === root) {
            lastMouseOver = mouseOver
            mouseOver = getMouseOverBlock(nvg, mouseX, mouseY)
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
        } else {
            root.handleNewMousePosition(nvg, mouseX, mouseY)
        }
    }

    private fun getMouseOverBlock(nvg: Long, mouseX: Int, mouseY: Int): Block? {
        if (isVisible) {
            this.nvg = nvg
            prepareForIteration()
            return getMouseOverBlock(mouseX, mouseY)
        }
        return null
    }

    private fun getMouseOverBlock(mouseX: Int, mouseY: Int): Block? {
        if (isVisible) {
            if (!isMouseAware || mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
                return null
            }
            (children.size - 1 downTo 0)
                    .mapNotNull { children[it].getMouseOverBlock(mouseX, mouseY) }
                    .forEach { return it }
            return this
        }
        return null
    }

    fun draw(nvg: Long) {
        if (isVisible) {
            this.nvg = nvg
            prepareForIteration()
            draw(ScissorStack(nvg))
        }
    }

    private fun prepareForIteration() {
        var lastChild: Block? = null
        children.forEach {
            it.lastBlock = lastChild
            it.nvg = nvg
            it.prepareForIteration()
            lastChild = it
        }
    }

    private fun draw(scissorStack: ScissorStack) {
        if (isVisible) {
            shape.draw(nvg, this)
            val strokeSize = shape.stroke.size
            scissorStack.push(Vector4f(x.toFloat() + strokeSize, y.toFloat() + strokeSize, x.toFloat() + width - strokeSize, y.toFloat() + height - strokeSize))
            text.draw(nvg, this)
            children.forEach {
                it.draw(scissorStack)
            }
            scissorStack.pop()
        }
    }

    fun block(builder: Block.() -> Unit): Block {
        val block = DefaultBlock(root, this)
        this.children.add(block)
        block.builder()
        return block
    }

    fun onMouseOver(onMouseOver: Block.() -> Unit) {
        this.onMouseOver = onMouseOver
    }

    fun onMouseOut(onMouseOut: Block.() -> Unit) {
        this.onMouseOut = onMouseOut
    }

    fun onMouseDown(onMouseDown: Block.(Int, Int, Int) -> Unit) {
        this.onMouseDown = onMouseDown
    }

    fun onMouseUp(onMouseUp: Block.(Int, Int, Int) -> Unit) {
        this.onMouseUp = onMouseUp
    }

    fun onMouseRelease(onMouseRelease: Block.(Int, Int, Int) -> Unit) {
        this.onMouseRelease = onMouseRelease
    }

    fun onMouseClick(onMouseClick: Block.(Int, Int, Int) -> Unit) {
        this.onMouseClick = onMouseClick
    }
}

private class RootBlock(override var x: Int, override var y: Int, override var width: Int, override var height: Int) : Block() {
    override val root = this
    override val parent = this
    override val children = ArrayList<Block>()
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
    override var layout: Layout
        get() = ABSOLUTE
        set(value) {
        }
    override var xOffset: Int
        get() = 0
        set(value) {
        }
    override var yOffset: Int
        get() = 0
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
    override var padLeft: Int
        get() = 0
        set(value) {
        }
    override var padRight: Int
        get() = 0
        set(value) {
        }
    override var padTop: Int
        get() = 0
        set(value) {
        }
    override var padBottom: Int
        get() = 0
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
    override var onMouseOver: (Block.() -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseOut: (Block.() -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseDown: (Block.(Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseUp: (Block.(Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseRelease: (Block.(Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var onMouseClick: (Block.(Int, Int, Int) -> Unit)?
        get() = null
        set(value) {
        }
    override var awaitingRelease: MutableList<Pair<Int, Block>> = ArrayList()
    override var mouseOver: Block? = null
    override var lastMouseOver: Block? = null
}

private class DefaultBlock(
        override val root: Block,
        override val parent: Block,
        override var nvg: Long = -1,
        override val children: MutableList<Block> = ArrayList(),
        override var isVisible: Boolean = true,
        override var hAlign: HorizontalAlignment = LEFT,
        override var vAlign: VerticalAlignment = TOP,
        override var layout: Layout = ABSOLUTE,
        override var xOffset: Int = 0,
        override var yOffset: Int = 0,
        override var hSizing: Sizing = RELATIVE,
        override var vSizing: Sizing = RELATIVE,
        width: Int = 100,
        height: Int = 100,
        override var padLeft: Int = 0,
        override var padRight: Int = 0,
        override var padTop: Int = 0,
        override var padBottom: Int = 0,
        override var shape: Shape = NO_SHAPE,
        override var text: Text = NO_TEXT,
        override var lastBlock: Block? = null,
        override var isMouseAware: Boolean = parent.isMouseAware,
        override var onMouseOver: (Block.() -> Unit)? = null,
        override var onMouseOut: (Block.() -> Unit)? = null,
        override var onMouseDown: (Block.(Int, Int, Int) -> Unit)? = null,
        override var onMouseUp: (Block.(Int, Int, Int) -> Unit)? = null,
        override var onMouseRelease: (Block.(Int, Int, Int) -> Unit)? = null,
        override var onMouseClick: (Block.(Int, Int, Int) -> Unit)? = null) : Block() {

    override var awaitingRelease: MutableList<Pair<Int, Block>>
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

    private var _width = width
    private var _height = height

    override var width: Int
        get() {
            if (!isVisible) {
                return 0
            }
            when (hSizing) {
                STATIC -> {
                    return Math.max(0, _width)
                }
                RELATIVE -> {
                    if (_width < 0) {
                        return (parent.width - (parent.shape.stroke.size * 2) - padLeft - padRight).toInt() - _width
                    } else {
                        return ((Math.min(100, _width) / 100.0f) * (parent.width - (parent.shape.stroke.size * 2) - padLeft - padRight)).toInt()
                    }
                }
                SHRINK -> {
                    var leftWidth = 0
                    var horizontalWidth = 0
                    var lastHorizontal = 0
                    var centerWidth = 0
                    var rightWidth = 0
                    children.forEach {
                        if (it.hAlign == CENTER && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            centerWidth = Math.max(it.width + it.padLeft + it.padRight, centerWidth)
                        }
                        if (it.hAlign == RIGHT && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            rightWidth = Math.max(it.width + it.padLeft + it.padRight, rightWidth)
                        }
                        if (it.hAlign == LEFT && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            if (it.layout == ABSOLUTE) {
                                leftWidth = Math.max(it.width + it.padLeft + it.padRight, leftWidth)
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
                    leftWidth = Math.max(leftWidth, horizontalWidth)
                    return Math.max(Math.max(Math.max((textDimensions.first + 0.5f).toInt(), leftWidth), centerWidth), rightWidth) + ((2 * shape.stroke.size) + 0.5f).toInt()
                }
                GROW -> {
                    val parentWidth = if (parent.hSizing == SHRINK) {
                        0
                    } else {
                        parent.width
                    }
                    if (hAlign == CENTER || hAlign == RIGHT) {
                        return parentWidth
                    }
                    if (layout == ABSOLUTE) {
                        return Math.max(0, parentWidth - x)
                    }
                    var leftWidth = 0
                    var horizontalWidth = 0
                    var lastHorizontal = 0
                    var centerWidth = 0
                    var rightWidth = 0
                    var growCount = 0
                    var growIndexOfThis = 0
                    var thisBaseWidth = 0
                    var modifyBaseWidth = false
                    parent.children.forEach {
                        if (it == this) {
                            growIndexOfThis = growCount
                        }
                        if (it.hSizing == GROW) {
                            growCount++
                        }
                        if (it.hAlign == CENTER && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            centerWidth = Math.max(it.width + it.padLeft + it.padRight, centerWidth)
                        }
                        if (it.hAlign == RIGHT && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            rightWidth = Math.max(it.width + it.padLeft + it.padRight, rightWidth)
                        }
                        if (it.hAlign == LEFT && it.hSizing != RELATIVE && it.hSizing != GROW) {
                            if (it.layout == ABSOLUTE) {
                                leftWidth = Math.max(it.width + it.padLeft + it.padRight, leftWidth)
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
                    leftWidth = Math.max(leftWidth, horizontalWidth)
                    val fixedChildWidth = Math.max(Math.max(leftWidth, centerWidth), rightWidth)
                    val growWidth = if (parent.hSizing == SHRINK) {
                        0
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

    override var height: Int
        get() {
            if (!isVisible) {
                return 0
            }
            when (vSizing) {
                STATIC -> {
                    return Math.max(0, _height)
                }
                RELATIVE -> {
                    if (_height < 0) {
                        return (parent.height - (parent.shape.stroke.size * 2)).toInt() - _height
                    } else {
                        return ((Math.min(100, _height) / 100.0f) * (parent.height - (parent.shape.stroke.size * 2))).toInt()
                    }
                }
                SHRINK -> {
                    var topHeight = 0
                    var verticalHeight = 0
                    var lastVertical = 0
                    var middleHeight = 0
                    var bottomHeight = 0
                    children.forEach {
                        if (it.vAlign == MIDDLE && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            middleHeight = Math.max(it.height + it.padTop + it.padBottom, middleHeight)
                        }
                        if (it.vAlign == BOTTOM && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            bottomHeight = Math.max(it.height + it.padTop + it.padBottom, bottomHeight)
                        }
                        if (it.vAlign == TOP && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            if (it.layout == ABSOLUTE) {
                                topHeight = Math.max(it.height + it.padTop + it.padBottom, topHeight)
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
                    topHeight = Math.max(topHeight, verticalHeight)
                    return Math.max(Math.max(Math.max((textDimensions.second + 0.5f).toInt(), topHeight), middleHeight), bottomHeight) + ((2 * shape.stroke.size) + 0.5f).toInt()
                }
                GROW -> {
                    val parentHeight = if (parent.vSizing == SHRINK) {
                        0
                    } else {
                        parent.height
                    }
                    if (vAlign == MIDDLE || vAlign == BOTTOM) {
                        return parentHeight
                    }
                    if (layout == ABSOLUTE) {
                        return Math.max(0, parentHeight - y)
                    }
                    var topHeight = 0
                    var verticalHeight = 0
                    var lastVertical = 0
                    var middleHeight = 0
                    var bottomHeight = 0
                    var growCount = 0
                    var growIndexOfThis = 0
                    var thisBaseHeight = 0
                    var modifyBaseHeight = false
                    parent.children.forEach {
                        if (it == this) {
                            growIndexOfThis = growCount
                        }
                        if (it.vSizing == GROW) {
                            growCount++
                        }
                        if (it.vAlign == MIDDLE && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            middleHeight = Math.max(it.height + it.padTop + it.padBottom, middleHeight)
                        }
                        if (it.vAlign == BOTTOM && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            bottomHeight = Math.max(it.height + it.padTop + it.padBottom, bottomHeight)
                        }
                        if (it.vAlign == TOP && it.vSizing != RELATIVE && it.vSizing != GROW) {
                            if (it.layout == ABSOLUTE) {
                                topHeight = Math.max(it.height + it.padTop + it.padBottom, topHeight)
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
                    topHeight = Math.max(topHeight, verticalHeight)
                    val fixedChildHeight = Math.max(Math.max(topHeight, middleHeight), bottomHeight)
                    val growHeight = if (parent.vSizing == SHRINK) {
                        0
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

    override var x: Int
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

    override var y: Int
        get() {
            if (layout == ABSOLUTE || lastBlock == null) {
                return getYRelativeTo(parent.y, parent.height)
            }
            if (layout == HORIZONTAL) {
                return getYRelativeTo(parent.y, parent.height)
            }
            return getYRelativeTo(lastBlock!!.y + lastBlock!!.height + lastBlock!!.padBottom + padLeft, parent.height)

        }
        set(value) {
        }

    private fun getXRelativeTo(relativeX: Int, relativeWidth: Int): Int {
        if (hAlign == LEFT) {
            return relativeX + padLeft + xOffset
        }
        if (hAlign == RIGHT) {
            return relativeX + relativeWidth - width - padRight + xOffset
        }
        return relativeX + (relativeWidth - padLeft - padRight) / 2 - width / 2 + xOffset + padLeft
    }

    private fun getYRelativeTo(relativeY: Int, relativeHeight: Int): Int {
        if (vAlign == TOP) {
            return relativeY + padTop + yOffset
        }
        if (vAlign == BOTTOM) {
            return relativeY + relativeHeight - height - padBottom + yOffset
        }
        return relativeY + (relativeHeight - padTop - padBottom) / 2 - height / 2 + yOffset + padTop
    }
}