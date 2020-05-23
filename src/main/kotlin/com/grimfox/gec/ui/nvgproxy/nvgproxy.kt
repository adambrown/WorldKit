package com.grimfox.gec.ui.nvgproxy

import org.lwjgl.nanovg.*
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class NPColor(val delegate: NVGColor) {

    companion object {

        fun create(): NPColor {
            return NPColor(NVGColor.create())
        }
    }

    fun r() = delegate.r()

    fun g() = delegate.g()

    fun b() = delegate.b()

    fun a() = delegate.a()

    fun r(value: Float): NPColor {
        delegate.r(value)
        return this
    }

    fun g(value: Float): NPColor {
        delegate.g(value)
        return this
    }

    fun b(value: Float): NPColor {
        delegate.b(value)
        return this
    }

    fun a(value: Float): NPColor {
        delegate.a(value)
        return this
    }
}

class NPGlyphPosition(val delegate: NVGGlyphPosition.Buffer) {

    companion object {

        fun create(sizeLimit: Int): NPGlyphPosition {
            return NPGlyphPosition(NVGGlyphPosition.create(sizeLimit))
        }
    }

    fun get(index: Int): NVGGlyphPosition {
        return delegate.get(index)
    }

    fun clear() {
        delegate.clear()
    }
}

class NPPaint(val delegate: NVGPaint) {

    companion object {

        fun create(): NPPaint {
            return NPPaint(NVGPaint.create())
        }
    }
}

class NPTextRow(val delegate: NVGTextRow.Buffer) {

    companion object {

        fun create(capacity: Int): NPTextRow {
            return NPTextRow(NVGTextRow.create(capacity))
        }

    }

    fun get(index: Int): NVGTextRow {
        return delegate.get(index)
    }

    fun memAddress(): Long {
        return MemoryUtil.memAddress(delegate)
    }
}

val NVG_STENCIL_STROKES = NanoVGGL3.NVG_STENCIL_STROKES
val NVG_HOLE = NanoVG.NVG_HOLE
val NVG_SOLID = NanoVG.NVG_SOLID
val NVG_ALIGN_LEFT = NanoVG.NVG_ALIGN_LEFT
val NVG_ALIGN_RIGHT = NanoVG.NVG_ALIGN_RIGHT
val NVG_ALIGN_CENTER = NanoVG.NVG_ALIGN_CENTER
val NVG_ALIGN_TOP = NanoVG.NVG_ALIGN_TOP
val NVG_ALIGN_BOTTOM = NanoVG.NVG_ALIGN_BOTTOM
val NVG_ALIGN_MIDDLE = NanoVG.NVG_ALIGN_MIDDLE

val NO_COLOR = color(0, 0, 0, 0)

fun nvgSave(ctx: Long) {
    NanoVG.nvgSave(ctx)
}

fun nvgBeginFrame(ctx: Long, windowWidth: Int, windowHeight: Int, devicePixelRatio: Float) {
    NanoVG.nvgBeginFrame(ctx, windowWidth.toFloat(), windowHeight.toFloat(), devicePixelRatio)
}

fun nvgEndFrame(ctx: Long) {
    NanoVG.nvgEndFrame(ctx)
}

fun nvgRestore(ctx: Long) {
    NanoVG.nvgRestore(ctx)
}

fun nvgCreateFontMem(ctx: Long, name: String, data: ByteBuffer, freeData: Int): Int {
    return NanoVG.nvgCreateFontMem(ctx, name, data, freeData)
}

fun nvglCreateImageFromHandle(ctx: Long, textureId: Int, w: Int, h: Int, flags: Int): Int {
    return NanoVGGL3.nvglCreateImageFromHandle(ctx, textureId, w, h, flags)
}

fun nvgCreate(flags: Int): Long {
    return NanoVGGL3.nvgCreate(flags)
}

fun nvgFontFaceId(ctx: Long, font: Int) {
    NanoVG.nvgFontFaceId(ctx, font)
}

fun nvgFontSize(ctx: Long, size: Float) {
    NanoVG.nvgFontSize(ctx, size)
}

fun nvgTextAlign(ctx: Long, align: Int) {
    NanoVG.nvgTextAlign(ctx, align)
}

fun nvgTextGlyphPositions(ctx: Long, x: Float, y: Float, string: ByteBuffer, positions: NPGlyphPosition): Int {
    return NanoVG.nvgTextGlyphPositions(ctx, x, y, string, positions.delegate)
}

fun nvgScissor(ctx: Long, x: Float, y: Float, w: Float, h: Float) {
    NanoVG.nvgScissor(ctx, x, y, w, h)
}

fun nvgResetScissor(ctx: Long) {
    NanoVG.nvgResetScissor(ctx)
}

fun nvgFillColor(ctx: Long, color: NPColor) {
    NanoVG.nvgFillColor(ctx, color.delegate)
}

fun nvgBeginPath(ctx: Long) {
    NanoVG.nvgBeginPath(ctx)
}

fun nvgFill(ctx: Long) {
    NanoVG.nvgFill(ctx)
}

fun nvgRGBA(r: Byte, g: Byte, b: Byte, a: Byte, result: NPColor): NPColor {
    NanoVG.nvgRGBA(r, g, b, a, result.delegate)
    return result
}

fun nvgRGBAf(r: Float, g: Float, b: Float, a: Float, result: NPColor): NPColor {
    NanoVG.nvgRGBAf(r, g, b, a, result.delegate)
    return result
}

fun nvgStrokeWidth(ctx: Long, size: Float) {
    NanoVG.nvgStrokeWidth(ctx, size)
}

fun nvgStrokeColor(ctx: Long, color: NPColor) {
    NanoVG.nvgStrokeColor(ctx, color.delegate)
}

fun nvgStroke(ctx: Long) {
    NanoVG.nvgStroke(ctx)
}

fun nvgPathWinding(ctx: Long, dir: Int) {
    NanoVG.nvgPathWinding(ctx, dir)
}

fun nvgClosePath(ctx: Long) {
    NanoVG.nvgClosePath(ctx)
}

fun nvgMoveTo(ctx: Long, x: Float, y: Float) {
    NanoVG.nvgMoveTo(ctx, x, y)
}

fun nvgLineTo(ctx: Long, x: Float, y: Float) {
    NanoVG.nvgLineTo(ctx, x, y)
}

fun nvgImagePattern(ctx: Long, ox: Float, oy: Float, ex: Float, ey: Float, angle: Float, image: Int, alpha: Float, result: NPPaint) {
    NanoVG.nvgImagePattern(ctx, ox, oy, ex, ey, angle, image, alpha, result.delegate)
}

fun nvgFillPaint(ctx: Long, paint: NPPaint) {
    NanoVG.nvgFillPaint(ctx, paint.delegate)
}

fun nvgBoxGradient(ctx: Long, x: Float, y: Float, w: Float, h: Float, r: Float, f: Float, icol: NPColor, ocol: NPColor, result: NPPaint) {
    NanoVG.nvgBoxGradient(ctx, x, y, w, h, r, f, icol.delegate, ocol.delegate, result.delegate)
}

fun nvgTextMetrics(ctx: Long, ascender: FloatBuffer?, descender: FloatBuffer?, lineh: FloatBuffer?) {
    NanoVG.nvgTextMetrics(ctx, ascender, descender, lineh)
}

fun nnvgTextBreakLines(ctx: Long, string: Long, end: Long, breakRowWidth: Float, rows: Long, maxRows: Int): Int {
    return NanoVG.nnvgTextBreakLines(ctx, string, end, breakRowWidth, rows, maxRows)
}

fun nnvgText(ctx: Long, x: Float, y: Float, string: Long, end: Long) {
    NanoVG.nnvgText(ctx, x, y, string, end)
}

fun nvgText(ctx: Long, x: Float, y: Float, string: ByteBuffer) {
    NanoVG.nvgText(ctx, x, y, string)
}

fun nvgTextBounds(ctx: Long, x: Float, y: Float, string: ByteBuffer, bounds: FloatBuffer?): Float {
    return NanoVG.nvgTextBounds(ctx, x, y, string, bounds)
}

fun nvgRect(ctx: Long, x: Float, y: Float, w: Float, h: Float) {
    NanoVG.nvgRect(ctx, x, y, w, h)
}

fun nvgCircle(ctx: Long, cx: Float, cy: Float, r: Float) {
    NanoVG.nvgCircle(ctx, cx, cy, r)
}

fun nvgEllipse(ctx: Long, cx: Float, cy: Float, rx: Float, ry: Float) {
    NanoVG.nvgEllipse(ctx, cx, cy, rx, ry)
}

fun nvgRoundedRect(ctx: Long, x: Float, y: Float, w: Float, h: Float, r: Float) {
    NanoVG.nvgRoundedRect(ctx, x, y, w, h, r)
}

fun nvgReset(ctx: Long) {
    NanoVG.nvgReset(ctx)
}

fun nvgRGB(r: Byte, g: Byte, b: Byte, result: NPColor): NPColor {
    NanoVG.nvgRGB(r, g, b, result.delegate)
    return result
}

fun nvgRGBf(r: Float, g: Float, b: Float, result: NPColor): NPColor {
    NanoVG.nvgRGBf(r, g, b, result.delegate)
    return result
}

var NPColor.rByte: Byte
    get() = colorFloatToByte(r())
    set(value) {
        r(colorByteToFloat(value))
    }
var NPColor.gByte: Byte
    get() = colorFloatToByte(g())
    set(value) {
        g(colorByteToFloat(value))
    }
var NPColor.bByte: Byte
    get() = colorFloatToByte(b())
    set(value) {
        b(colorByteToFloat(value))
    }
var NPColor.aByte: Byte
    get() = colorFloatToByte(a())
    set(value) {
        a(colorByteToFloat(value))
    }

var NPColor.r: Float
    get() = r()
    set(value) {
        r(value)
    }
var NPColor.g: Float
    get() = g()
    set(value) {
        g(value)
    }
var NPColor.b: Float
    get() = b()
    set(value) {
        b(value)
    }
var NPColor.a: Float
    get() = a()
    set(value) {
        a(value)
    }

var NPColor.rInt: Int
    get() = colorFloatToInt(r)
    set(value) {
        r = colorIntToFloat(value)
    }
var NPColor.gInt: Int
    get() = colorFloatToInt(g)
    set(value) {
        g = colorIntToFloat(value)
    }
var NPColor.bInt: Int
    get() = colorFloatToInt(b)
    set(value) {
        b = colorIntToFloat(value)
    }
var NPColor.aInt: Int
    get() = colorFloatToInt(a)
    set(value) {
        a = colorIntToFloat(value)
    }

fun NPColor.set(r: Int, g: Int, b: Int, a: Int): NPColor {
    return nvgRGBA(r.toByte(), g.toByte(), b.toByte(), a.toByte(), this)
}

fun NPColor.set(r: Int, g: Int, b: Int): NPColor {
    return nvgRGB(r.toByte(), g.toByte(), b.toByte(), this)
}

fun NPColor.set(r: Byte, g: Byte, b: Byte, a: Byte): NPColor {
    return nvgRGBA(r, g, b, a, this)
}

fun NPColor.set(r: Byte, g: Byte, b: Byte): NPColor {
    return nvgRGB(r, g, b, this)
}

fun NPColor.set(r: Float, g: Float, b: Float, a: Float): NPColor {
    return nvgRGBAf(r, g, b, a, this)
}

fun NPColor.set(r: Float, g: Float, b: Float): NPColor {
    return nvgRGBf(r, g, b, this)
}

private fun colorByteToInt(b: Byte) = (b.toInt() and 0xFF)
private fun colorByteToFloat(b: Byte) = colorIntToFloat(colorByteToInt(b))
private fun colorIntToByte(i: Int) = i.toByte()
private fun colorFloatToByte(f: Float) = colorIntToByte(colorFloatToInt(f))
private fun colorIntToFloat(i: Int) = i / 255.0f
private fun colorFloatToInt(f: Float) = Math.round(f * 255)

fun color(r: Int, g: Int, b: Int, a: Int): NPColor {
    return NPColor.create().set(r, g, b, a)
}

fun color(r: Int, g: Int, b: Int): NPColor {
    return NPColor.create().set(r, g, b, 255)
}

fun color(r: Float, g: Float, b: Float): NPColor {
    return NPColor.create().set(r, g, b, 1.0f)
}

fun color(r: Float, g: Float, b: Float, a: Float): NPColor {
    return NPColor.create().set(r, g, b, a)
}
