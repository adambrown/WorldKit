package com.grimfox.gec.learning

import com.grimfox.gec.extensions.twr
import org.lwjgl.BufferUtils
import org.lwjgl.nanovg.*
import org.lwjgl.opengl.GL

import java.nio.*
import java.util.Arrays

import com.grimfox.gec.util.loadResource
import java.lang.Math.*
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.opengl.ARBTimerQuery.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.stb.STBImageWrite.*
import org.lwjgl.system.MemoryStack.*
import org.lwjgl.system.MemoryUtil.*

class DemoData {

    val entypo = loadResource("/demo/nanovg/entypo.ttf", 40 * 1024)
    val RobotoRegular = loadResource("/demo/nanovg/Roboto-Regular.ttf", 150 * 1024)
    val RobotoBold = loadResource("/demo/nanovg/Roboto-Bold.ttf", 150 * 1024)

    var fontNormal: Int = 0
    var fontBold: Int = 0
    var fontIcons: Int = 0

    var images = IntArray(12)
}

class PerfGraph {
    var style: Int = 0
    var name = BufferUtils.createByteBuffer(32)
    var values = FloatArray(GRAPH_HISTORY_COUNT)
    var head: Int = 0
}

class GPUtimer {
    var supported: Boolean = false
    var cur: Int = 0
    var ret: Int = 0
    var queries = BufferUtils.createIntBuffer(GPU_QUERY_COUNT)
}

private val ICON_SEARCH = cpToUTF8(0x1F50D)
private val ICON_CIRCLED_CROSS = cpToUTF8(0x2716)
private val ICON_CHEVRON_RIGHT = cpToUTF8(0xE75E)
private val ICON_CHECK = cpToUTF8(0x2713)
private val ICON_LOGIN = cpToUTF8(0xE740)
private val ICON_TRASH = cpToUTF8(0xE729)

val colorA = NVGColor.create()
val colorB = NVGColor.create()
val colorC = NVGColor.create()

val paintA = NVGPaint.create()
val paintB = NVGPaint.create()
val paintC = NVGPaint.create()

private val rows = NVGTextRow.create(3)
private val glyphs = NVGGlyphPosition.create(100)
private val paragraph = memUTF8(
        "This is longer chunk of text.\n\nWould have used lorem ipsum but she was busy jumping over the lazy dog with the fox and all the men who came to the aid of the party."
)

private val lineh = BufferUtils.createFloatBuffer(1)
private val bounds = BufferUtils.createFloatBuffer(4)

private val hoverText = memASCII("Hover your mouse over the text to see calculated caret position.")

val mx = BufferUtils.createDoubleBuffer(1)
val my = BufferUtils.createDoubleBuffer(1)

val winWidth = BufferUtils.createIntBuffer(1)
val winHeight = BufferUtils.createIntBuffer(1)

val fbWidth = BufferUtils.createIntBuffer(1)
val fbHeight = BufferUtils.createIntBuffer(1)

val gpuTimes = BufferUtils.createFloatBuffer(3)

private fun maxf(a: Float, b: Float): Float {
    return if (a > b) a else b
}

private fun clampf(a: Float, mn: Float, mx: Float): Float {
    return if (a < mn) mn else if (a > mx) mx else a
}

private fun isBlack(col: NVGColor): Boolean {
    return col.r() == 0.0f && col.g() == 0.0f && col.b() == 0.0f && col.a() == 0.0f
}

private fun cpToUTF8(cp: Int): ByteBuffer {
    return memUTF8(String(Character.toChars(cp)), true)
}

fun rgba(r: Int, g: Int, b: Int, a: Int, color: NVGColor): NVGColor {
    return nvgRGBA(r.toByte(), g.toByte(), b.toByte(), a.toByte(), color)
}

private fun drawWindow(vg: Long, title: String, x: Float, y: Float, w: Float, h: Float) {
    val cornerRadius = 3.0f
    val shadowPaint = paintA
    val headerPaint = paintB

    nvgSave(vg)
    //nvgClearState(vg);

    // Window
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x, y, w, h, cornerRadius)
    nvgFillColor(vg, rgba(28, 30, 34, 192, colorA))
    //nvgFillColor(vg, rgba(0,0,0,128, color));
    nvgFill(vg)

    // Drop shadow
    nvgBoxGradient(vg, x, y + 2, w, h, cornerRadius * 2, 10f, rgba(0, 0, 0, 128, colorA), rgba(0, 0, 0, 0, colorB), shadowPaint)
    nvgBeginPath(vg)
    nvgRect(vg, x - 10, y - 10, w + 20, h + 30)
    nvgRoundedRect(vg, x, y, w, h, cornerRadius)
    nvgPathWinding(vg, NVG_HOLE)
    nvgFillPaint(vg, shadowPaint)
    nvgFill(vg)

    // Header
    nvgLinearGradient(vg, x, y, x, y + 15, rgba(255, 255, 255, 8, colorA), rgba(0, 0, 0, 16, colorB), headerPaint)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + 1, y + 1, w - 2, 30f, cornerRadius - 1)
    nvgFillPaint(vg, headerPaint)
    nvgFill(vg)
    nvgBeginPath(vg)
    nvgMoveTo(vg, x + 0.5f, y + 0.5f + 30f)
    nvgLineTo(vg, x + 0.5f + w - 1, y + 0.5f + 30f)
    nvgStrokeColor(vg, rgba(0, 0, 0, 32, colorA))
    nvgStroke(vg)

    nvgFontSize(vg, 18.0f)
    nvgFontFace(vg, "sans-bold")
    nvgTextAlign(vg, NVG_ALIGN_CENTER or NVG_ALIGN_MIDDLE)

    twr(stackPush()) { stack ->
        val titleText = stack.ASCII(title)

        nvgFontBlur(vg, 2f)
        nvgFillColor(vg, rgba(0, 0, 0, 128, colorA))
        nvgText(vg, x + w / 2, y + 16f + 1f, titleText, NULL)

        nvgFontBlur(vg, 0f)
        nvgFillColor(vg, rgba(220, 220, 220, 160, colorA))
        nvgText(vg, x + w / 2, y + 16, titleText, NULL)
    }

    nvgRestore(vg)
}

private fun drawSearchBox(vg: Long, text: String, x: Float, y: Float, w: Float, h: Float) {
    val bg = paintA
    val cornerRadius = h / 2 - 1

    // Edit
    nvgBoxGradient(vg, x, y + 1.5f, w, h, h / 2, 5f, rgba(0, 0, 0, 16, colorA), rgba(0, 0, 0, 92, colorB), bg)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x, y, w, h, cornerRadius)
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    /*nvgBeginPath(vg);
nvgRoundedRect(vg, x+0.5f,y+0.5f, w-1,h-1, cornerRadius-0.5f);
nvgStrokeColor(vg, rgba(0,0,0,48, colorA));
nvgStroke(vg);*/

    nvgFontSize(vg, h * 1.3f)
    nvgFontFace(vg, "icons")
    nvgFillColor(vg, rgba(255, 255, 255, 64, colorA))
    nvgTextAlign(vg, NVG_ALIGN_CENTER or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + h * 0.55f, y + h * 0.55f, ICON_SEARCH, NULL)

    nvgFontSize(vg, 20.0f)
    nvgFontFace(vg, "sans")
    nvgFillColor(vg, rgba(255, 255, 255, 32, colorA))

    nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + h * 1.05f, y + h * 0.5f, text, NULL)

    nvgFontSize(vg, h * 1.3f)
    nvgFontFace(vg, "icons")
    nvgFillColor(vg, rgba(255, 255, 255, 32, colorA))
    nvgTextAlign(vg, NVG_ALIGN_CENTER or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + w - h * 0.55f, y + h * 0.55f, ICON_CIRCLED_CROSS, NULL)
}

private fun drawDropDown(vg: Long, text: String, x: Float, y: Float, w: Float, h: Float) {
    val bg = paintA
    val cornerRadius = 4.0f

    nvgLinearGradient(vg, x, y, x, y + h, rgba(255, 255, 255, 16, colorA), rgba(0, 0, 0, 16, colorB), bg)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + 1, y + 1, w - 2, h - 2, cornerRadius - 1)
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + 0.5f, y + 0.5f, w - 1, h - 1, cornerRadius - 0.5f)
    nvgStrokeColor(vg, rgba(0, 0, 0, 48, colorA))
    nvgStroke(vg)

    nvgFontSize(vg, 20.0f)
    nvgFontFace(vg, "sans")
    nvgFillColor(vg, rgba(255, 255, 255, 160, colorA))
    nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + h * 0.3f, y + h * 0.5f, text, NULL)

    nvgFontSize(vg, h * 1.3f)
    nvgFontFace(vg, "icons")
    nvgFillColor(vg, rgba(255, 255, 255, 64, colorA))
    nvgTextAlign(vg, NVG_ALIGN_CENTER or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + w - h * 0.5f, y + h * 0.5f, ICON_CHEVRON_RIGHT, NULL)
}

private fun drawLabel(vg: Long, text: String, x: Float, y: Float, w: Float, h: Float) {
    nvgFontSize(vg, 18.0f)
    nvgFontFace(vg, "sans")
    nvgFillColor(vg, rgba(255, 255, 255, 128, colorA))

    nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
    nvgText(vg, x, y + h * 0.5f, text, NULL)
}

private fun drawEditBoxBase(vg: Long, x: Float, y: Float, w: Float, h: Float) {
    val bg = paintA

    // Edit
    nvgBoxGradient(vg, x + 1, y + 1f + 1.5f, w - 2, h - 2, 3f, 4f, rgba(255, 255, 255, 32, colorA), rgba(32, 32, 32, 32, colorB), bg)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + 1, y + 1, w - 2, h - 2, (4 - 1).toFloat())
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + 0.5f, y + 0.5f, w - 1, h - 1, 4 - 0.5f)
    nvgStrokeColor(vg, rgba(0, 0, 0, 48, colorA))
    nvgStroke(vg)
}

private fun drawEditBox(vg: Long, text: String, x: Float, y: Float, w: Float, h: Float) {
    drawEditBoxBase(vg, x, y, w, h)

    nvgFontSize(vg, 20.0f)
    nvgFontFace(vg, "sans")
    nvgFillColor(vg, rgba(255, 255, 255, 64, colorA))
    nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + h * 0.3f, y + h * 0.5f, text, NULL)
}

private fun drawEditBoxNum(
        vg: Long,
        text: String, units: String, x: Float, y: Float, w: Float, h: Float
) {

    val uw: Float

    drawEditBoxBase(vg, x, y, w, h)

    uw = nvgTextBounds(vg, 0f, 0f, units, NULL, null as FloatBuffer?)

    nvgFontSize(vg, 18.0f)
    nvgFontFace(vg, "sans")
    nvgFillColor(vg, rgba(255, 255, 255, 64, colorA))
    nvgTextAlign(vg, NVG_ALIGN_RIGHT or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + w - h * 0.3f, y + h * 0.5f, units, NULL)

    nvgFontSize(vg, 20.0f)
    nvgFontFace(vg, "sans")
    nvgFillColor(vg, rgba(255, 255, 255, 128, colorA))
    nvgTextAlign(vg, NVG_ALIGN_RIGHT or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + w - uw - h * 0.5f, y + h * 0.5f, text, NULL)
}

private fun drawCheckBox(vg: Long, text: String, x: Float, y: Float, w: Float, h: Float) {
    val bg = paintA

    nvgFontSize(vg, 18.0f)
    nvgFontFace(vg, "sans")
    nvgFillColor(vg, rgba(255, 255, 255, 160, colorA))

    nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + 28, y + h * 0.5f, text, NULL)

    nvgBoxGradient(vg, x + 1, y + (h * 0.5f).toInt() - 9 + 1, 18f, 18f, 3f, 3f, rgba(0, 0, 0, 32, colorA), rgba(0, 0, 0, 92, colorB), bg)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + 1, y + (h * 0.5f).toInt() - 9, 18f, 18f, 3f)
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    nvgFontSize(vg, 40f)
    nvgFontFace(vg, "icons")
    nvgFillColor(vg, rgba(255, 255, 255, 128, colorA))
    nvgTextAlign(vg, NVG_ALIGN_CENTER or NVG_ALIGN_MIDDLE)
    nvgText(vg, x + 9f + 2f, y + h * 0.5f, ICON_CHECK, NULL)
}

private fun drawButton(vg: Long, preicon: ByteBuffer?, text: String, x: Float, y: Float, w: Float, h: Float, col: NVGColor) {
    val bg = paintA
    val cornerRadius = 4.0f
    var tw: Float
    var iw = 0f

    nvgLinearGradient(vg, x, y, x, y + h, rgba(255, 255, 255, if (isBlack(col)) 16 else 32, colorB), rgba(0, 0, 0, if (isBlack(col)) 16 else 32, colorC), bg)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + 1, y + 1, w - 2, h - 2, cornerRadius - 1)
    if (!isBlack(col)) {
        nvgFillColor(vg, col)
        nvgFill(vg)
    }
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + 0.5f, y + 0.5f, w - 1, h - 1, cornerRadius - 0.5f)
    nvgStrokeColor(vg, rgba(0, 0, 0, 48, colorA))
    nvgStroke(vg)

    twr(stackPush()) { stack ->
        val textEncoded = stack.ASCII(text)

        nvgFontSize(vg, 20.0f)
        nvgFontFace(vg, "sans-bold")
        tw = nvgTextBounds(vg, 0f, 0f, textEncoded, NULL, null as FloatBuffer?)
        if (preicon != null) {
            nvgFontSize(vg, h * 1.3f)
            nvgFontFace(vg, "icons")
            iw = nvgTextBounds(vg, 0f, 0f, preicon, NULL, null as FloatBuffer?)
            iw += h * 0.15f
        }

        if (preicon != null) {
            nvgFontSize(vg, h * 1.3f)
            nvgFontFace(vg, "icons")
            nvgFillColor(vg, rgba(255, 255, 255, 96, colorA))
            nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
            nvgText(vg, x + w * 0.5f - tw * 0.5f - iw * 0.75f, y + h * 0.5f, preicon, NULL)
        }

        nvgFontSize(vg, 20.0f)
        nvgFontFace(vg, "sans-bold")
        nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
        nvgFillColor(vg, rgba(0, 0, 0, 160, colorA))
        nvgText(vg, x + w * 0.5f - tw * 0.5f + iw * 0.25f, y + h * 0.5f - 1, textEncoded, NULL)
        nvgFillColor(vg, rgba(255, 255, 255, 160, colorA))
        nvgText(vg, x + w * 0.5f - tw * 0.5f + iw * 0.25f, y + h * 0.5f, textEncoded, NULL)
    }
}

fun drawButton(vg: Long, text: String, x: Float, y: Float, unused: Float, h: Float, color: NVGColor, font: Int) {
    twr(stackPush()) { stack ->
        val bg = paintA
        val cornerRadius = 4.0f
        nvgFontSize(vg, 20.0f)
        nvgFontFaceId(vg, font)
        val textEncoded = stack.UTF8(text)
        val w = nvgTextBounds(vg, 0f, 0f, textEncoded, NULL, null as FloatBuffer?) + 20

        nvgLinearGradient(vg, x, y, x, y + h, rgba(255, 255, 255, if (isBlack(color)) 16 else 32, colorB), rgba(0, 0, 0, if (isBlack(color)) 16 else 32, colorC), bg)
        nvgBeginPath(vg)
        nvgRoundedRect(vg, x + 1, y + 1, w - 2, h - 2, cornerRadius - 1)
        if (!isBlack(color)) {
            nvgFillColor(vg, color)
            nvgFill(vg)
        }
        nvgFillPaint(vg, bg)
        nvgFill(vg)

        nvgBeginPath(vg)
        nvgRoundedRect(vg, x + 0.5f, y + 0.5f, w - 1, h - 1, cornerRadius - 0.5f)
        nvgStrokeColor(vg, rgba(0, 0, 0, 48, colorA))
        nvgStroke(vg)

        nvgTextAlign(vg, NVG_ALIGN_CENTER or NVG_ALIGN_MIDDLE)
        nvgFillColor(vg, rgba(0, 0, 0, 160, colorA))
        nvgText(vg, x + w * 0.5f, y + h * 0.5f - 1, textEncoded, NULL)
        nvgFillColor(vg, rgba(255, 255, 255, 160, colorA))
        nvgText(vg, x + w * 0.5f, y + h * 0.5f, textEncoded, NULL)
    }
}



private fun drawSlider(vg: Long, pos: Float, x: Float, y: Float, w: Float, h: Float) {
    val bg = paintA
    val knob = paintB
    val cy = y + (h * 0.5f).toInt()
    val kr = (h * 0.25f).toInt().toFloat()

    nvgSave(vg)
    //nvgClearState(vg);

    // Slot
    nvgBoxGradient(vg, x, cy - 2 + 1, w, 4f, 2f, 2f, rgba(0, 0, 0, 32, colorA), rgba(0, 0, 0, 128, colorB), bg)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x, cy - 2, w, 4f, 2f)
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    // Knob Shadow
    nvgRadialGradient(vg, x + (pos * w).toInt(), cy + 1, kr - 3, kr + 3, rgba(0, 0, 0, 64, colorA), rgba(0, 0, 0, 0, colorB), bg)
    nvgBeginPath(vg)
    nvgRect(vg, x + (pos * w).toInt() - kr - 5f, cy - kr - 5f, kr * 2 + 5f + 5f, kr * 2 + 5f + 5f + 3f)
    nvgCircle(vg, x + (pos * w).toInt(), cy, kr)
    nvgPathWinding(vg, NVG_HOLE)
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    // Knob
    nvgLinearGradient(vg, x, cy - kr, x, cy + kr, rgba(255, 255, 255, 16, colorA), rgba(0, 0, 0, 16, colorB), knob)
    nvgBeginPath(vg)
    nvgCircle(vg, x + (pos * w).toInt(), cy, kr - 1)
    nvgFillColor(vg, rgba(40, 43, 48, 255, colorA))
    nvgFill(vg)
    nvgFillPaint(vg, knob)
    nvgFill(vg)

    nvgBeginPath(vg)
    nvgCircle(vg, x + (pos * w).toInt(), cy, kr - 0.5f)
    nvgStrokeColor(vg, rgba(0, 0, 0, 92, colorA))
    nvgStroke(vg)

    nvgRestore(vg)
}

private fun drawEyes(vg: Long, x: Float, y: Float, w: Float, h: Float, mx: Float, my: Float, t: Float) {
    val gloss = paintA
    val bg = paintB
    val ex = w * 0.23f
    val ey = h * 0.5f
    val lx = x + ex
    val ly = y + ey
    val rx = x + w - ex
    val ry = y + ey
    var dx: Float
    var dy: Float
    var d: Float
    val br = (if (ex < ey) ex else ey) * 0.5f
    val blink = 1 - pow(sin((t * 0.5f).toDouble()).toFloat().toDouble(), 200.0).toFloat() * 0.8f

    nvgLinearGradient(vg, x, y + h * 0.5f, x + w * 0.1f, y + h, rgba(0, 0, 0, 32, colorA), rgba(0, 0, 0, 16, colorB), bg)
    nvgBeginPath(vg)
    nvgEllipse(vg, lx + 3.0f, ly + 16.0f, ex, ey)
    nvgEllipse(vg, rx + 3.0f, ry + 16.0f, ex, ey)
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    nvgLinearGradient(vg, x, y + h * 0.25f, x + w * 0.1f, y + h, rgba(220, 220, 220, 255, colorA), rgba(128, 128, 128, 255, colorB), bg)
    nvgBeginPath(vg)
    nvgEllipse(vg, lx, ly, ex, ey)
    nvgEllipse(vg, rx, ry, ex, ey)
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    dx = (mx - rx) / (ex * 10)
    dy = (my - ry) / (ey * 10)
    d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    if (d > 1.0f) {
        dx /= d
        dy /= d
    }
    dx *= ex * 0.4f
    dy *= ey * 0.5f
    nvgBeginPath(vg)
    nvgEllipse(vg, lx + dx, ly + dy + ey * 0.25f * (1 - blink), br, br * blink)
    nvgFillColor(vg, rgba(32, 32, 32, 255, colorA))
    nvgFill(vg)

    dx = (mx - rx) / (ex * 10)
    dy = (my - ry) / (ey * 10)
    d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    if (d > 1.0f) {
        dx /= d
        dy /= d
    }
    dx *= ex * 0.4f
    dy *= ey * 0.5f
    nvgBeginPath(vg)
    nvgEllipse(vg, rx + dx, ry + dy + ey * 0.25f * (1 - blink), br, br * blink)
    nvgFillColor(vg, rgba(32, 32, 32, 255, colorA))
    nvgFill(vg)

    nvgRadialGradient(vg, lx - ex * 0.25f, ly - ey * 0.5f, ex * 0.1f, ex * 0.75f, rgba(255, 255, 255, 128, colorA), rgba(255, 255, 255, 0, colorB), gloss)
    nvgBeginPath(vg)
    nvgEllipse(vg, lx, ly, ex, ey)
    nvgFillPaint(vg, gloss)
    nvgFill(vg)

    nvgRadialGradient(vg, rx - ex * 0.25f, ry - ey * 0.5f, ex * 0.1f, ex * 0.75f, rgba(255, 255, 255, 128, colorA), rgba(255, 255, 255, 0, colorB), gloss)
    nvgBeginPath(vg)
    nvgEllipse(vg, rx, ry, ex, ey)
    nvgFillPaint(vg, gloss)
    nvgFill(vg)
}

private val samples = FloatArray(6)
private val sx = FloatArray(6)
private val sy = FloatArray(6)

private fun drawGraph(vg: Long, x: Float, y: Float, w: Float, h: Float, t: Float) {
    val bg = paintA

    val dx = w / 5.0f
    var i: Int

    samples[0] = (1 + sin((t * 1.2345f + cos((t * 0.33457f).toDouble()).toFloat() * 0.44f).toDouble()).toFloat()) * 0.5f
    samples[1] = (1 + sin((t * 0.68363f + cos((t * 1.3f).toDouble()).toFloat() * 1.55f).toDouble()).toFloat()) * 0.5f
    samples[2] = (1 + sin((t * 1.1642f + cos(t * 0.33457).toFloat() * 1.24f).toDouble()).toFloat()) * 0.5f
    samples[3] = (1 + sin((t * 0.56345f + cos((t * 1.63f).toDouble()).toFloat() * 0.14f).toDouble()).toFloat()) * 0.5f
    samples[4] = (1 + sin((t * 1.6245f + cos((t * 0.254f).toDouble()).toFloat() * 0.3f).toDouble()).toFloat()) * 0.5f
    samples[5] = (1 + sin((t * 0.345f + cos((t * 0.03f).toDouble()).toFloat() * 0.6f).toDouble()).toFloat()) * 0.5f

    i = 0
    while (i < 6) {
        sx[i] = x + i * dx
        sy[i] = y + h * samples[i] * 0.8f
        i++
    }

    // Graph background
    nvgLinearGradient(vg, x, y, x, y + h, rgba(0, 160, 192, 0, colorA), rgba(0, 160, 192, 64, colorB), bg)
    nvgBeginPath(vg)
    nvgMoveTo(vg, sx[0], sy[0])
    i = 1
    while (i < 6) {
        nvgBezierTo(vg, sx[i - 1] + dx * 0.5f, sy[i - 1], sx[i] - dx * 0.5f, sy[i], sx[i], sy[i])
        i++
    }
    nvgLineTo(vg, x + w, y + h)
    nvgLineTo(vg, x, y + h)
    nvgFillPaint(vg, bg)
    nvgFill(vg)

    // Graph line
    nvgBeginPath(vg)
    nvgMoveTo(vg, sx[0], sy[0] + 2)
    i = 1
    while (i < 6) {
        nvgBezierTo(vg, sx[i - 1] + dx * 0.5f, sy[i - 1] + 2, sx[i] - dx * 0.5f, sy[i] + 2, sx[i], sy[i] + 2)
        i++
    }
    nvgStrokeColor(vg, rgba(0, 0, 0, 32, colorA))
    nvgStrokeWidth(vg, 3.0f)
    nvgStroke(vg)

    nvgBeginPath(vg)
    nvgMoveTo(vg, sx[0], sy[0])
    i = 1
    while (i < 6) {
        nvgBezierTo(vg, sx[i - 1] + dx * 0.5f, sy[i - 1], sx[i] - dx * 0.5f, sy[i], sx[i], sy[i])
        i++
    }
    nvgStrokeColor(vg, rgba(0, 160, 192, 255, colorA))
    nvgStrokeWidth(vg, 3.0f)
    nvgStroke(vg)

    // Graph sample pos
    i = 0
    while (i < 6) {
        nvgRadialGradient(vg, sx[i], sy[i] + 2, 3.0f, 8.0f, rgba(0, 0, 0, 32, colorA), rgba(0, 0, 0, 0, colorB), bg)
        nvgBeginPath(vg)
        nvgRect(vg, sx[i] - 10, sy[i] - 10 + 2, 20f, 20f)
        nvgFillPaint(vg, bg)
        nvgFill(vg)
        i++
    }

    nvgBeginPath(vg)
    i = 0
    while (i < 6) {
        nvgCircle(vg, sx[i], sy[i], 4.0f)
        i++
    }
    nvgFillColor(vg, rgba(0, 160, 192, 255, colorA))
    nvgFill(vg)
    nvgBeginPath(vg)
    i = 0
    while (i < 6) {
        nvgCircle(vg, sx[i], sy[i], 2.0f)
        i++
    }
    nvgFillColor(vg, rgba(220, 220, 220, 255, colorA))
    nvgFill(vg)

    nvgStrokeWidth(vg, 1.0f)
}

private fun drawSpinner(vg: Long, cx: Float, cy: Float, r: Float, t: Float) {
    val a0 = 0.0f + t * 6
    val a1 = NVG_PI + t * 6
    val r0 = r
    val r1 = r * 0.75f
    val ax: Float
    val ay: Float
    val bx: Float
    val by: Float
    val paint = paintA

    nvgSave(vg)

    nvgBeginPath(vg)
    nvgArc(vg, cx, cy, r0, a0, a1, NVG_CW)
    nvgArc(vg, cx, cy, r1, a1, a0, NVG_CCW)
    nvgClosePath(vg)
    ax = cx + cos(a0.toDouble()).toFloat() * (r0 + r1) * 0.5f
    ay = cy + sin(a0.toDouble()).toFloat() * (r0 + r1) * 0.5f
    bx = cx + cos(a1.toDouble()).toFloat() * (r0 + r1) * 0.5f
    by = cy + sin(a1.toDouble()).toFloat() * (r0 + r1) * 0.5f
    nvgLinearGradient(vg, ax, ay, bx, by, rgba(0, 0, 0, 0, colorA), rgba(0, 0, 0, 128, colorB), paint)
    nvgFillPaint(vg, paint)
    nvgFill(vg)

    nvgRestore(vg)
}

private fun drawThumbnails(vg: Long, x: Float, y: Float, w: Float, h: Float, images: IntArray, nimages: Int, t: Float) {
    val cornerRadius = 3.0f
    val shadowPaint = paintA
    val imgPaint = paintB
    val fadePaint = paintC
    var ix: Float
    var iy: Float
    var iw: Float
    var ih: Float
    val thumb = 60.0f
    val arry = 30.5f
    val stackh = nimages / 2 * (thumb + 10) + 10
    var i: Int
    val u = (1 + cos((t * 0.5f).toDouble()).toFloat()) * 0.5f
    val u2 = (1 - cos((t * 0.2f).toDouble()).toFloat()) * 0.5f
    val scrollh: Float
    val dv: Float

    nvgSave(vg)
    //nvgClearState(vg);

    // Drop shadow
    nvgBoxGradient(vg, x, y + 4, w, h, cornerRadius * 2, 20f, rgba(0, 0, 0, 128, colorA), rgba(0, 0, 0, 0, colorB), shadowPaint)
    nvgBeginPath(vg)
    nvgRect(vg, x - 10, y - 10, w + 20, h + 30)
    nvgRoundedRect(vg, x, y, w, h, cornerRadius)
    nvgPathWinding(vg, NVG_HOLE)
    nvgFillPaint(vg, shadowPaint)
    nvgFill(vg)

    // Window
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x, y, w, h, cornerRadius)
    nvgMoveTo(vg, x - 10, y + arry)
    nvgLineTo(vg, x + 1, y + arry - 11)
    nvgLineTo(vg, x + 1, y + arry + 11f)
    nvgFillColor(vg, rgba(200, 200, 200, 255, colorA))
    nvgFill(vg)

    nvgSave(vg)
    nvgScissor(vg, x, y, w, h)
    nvgTranslate(vg, 0f, -(stackh - h) * u)

    dv = 1.0f / (nimages - 1).toFloat()

    twr(stackPush()) { stack ->
        val imgw = stack.mallocInt(1)
        val imgh = stack.mallocInt(1)

        i = 0
        while (i < nimages) {
            var tx: Float
            var ty: Float
            val v: Float
            val a: Float
            tx = x + 10
            ty = y + 10
            tx += i % 2 * (thumb + 10)
            ty += i / 2 * (thumb + 10)
            nvgImageSize(vg, images[i], imgw, imgh)
            if (imgw.get(0) < imgh.get(0)) {
                iw = thumb
                ih = iw * imgh.get(0).toFloat() / imgw.get(0).toFloat()
                ix = 0f
                iy = -(ih - thumb) * 0.5f
            } else {
                ih = thumb
                iw = ih * imgw.get(0).toFloat() / imgh.get(0).toFloat()
                ix = -(iw - thumb) * 0.5f
                iy = 0f
            }

            v = i * dv
            a = clampf((u2 - v) / dv, 0f, 1f)

            if (a < 1.0f)
                drawSpinner(vg, tx + thumb / 2, ty + thumb / 2, thumb * 0.25f, t)

            nvgImagePattern(vg, tx + ix, ty + iy, iw, ih, 0.0f / 180.0f * NVG_PI, images[i], a, imgPaint)
            nvgBeginPath(vg)
            nvgRoundedRect(vg, tx, ty, thumb, thumb, 5f)
            nvgFillPaint(vg, imgPaint)
            nvgFill(vg)

            nvgBoxGradient(vg, tx - 1, ty, thumb + 2, thumb + 2, 5f, 3f, rgba(0, 0, 0, 128, colorA), rgba(0, 0, 0, 0, colorB), shadowPaint)
            nvgBeginPath(vg)
            nvgRect(vg, tx - 5, ty - 5, thumb + 10, thumb + 10)
            nvgRoundedRect(vg, tx, ty, thumb, thumb, 6f)
            nvgPathWinding(vg, NVG_HOLE)
            nvgFillPaint(vg, shadowPaint)
            nvgFill(vg)

            nvgBeginPath(vg)
            nvgRoundedRect(vg, tx + 0.5f, ty + 0.5f, thumb - 1, thumb - 1, 4 - 0.5f)
            nvgStrokeWidth(vg, 1.0f)
            nvgStrokeColor(vg, rgba(255, 255, 255, 192, colorA))
            nvgStroke(vg)
            i++
        }
    }
    nvgRestore(vg)

    // Hide fades
    nvgLinearGradient(vg, x, y, x, y + 6, rgba(200, 200, 200, 255, colorA), rgba(200, 200, 200, 0, colorB), fadePaint)
    nvgBeginPath(vg)
    nvgRect(vg, x + 4, y, w - 8, 6f)
    nvgFillPaint(vg, fadePaint)
    nvgFill(vg)

    nvgLinearGradient(vg, x, y + h, x, y + h - 6, rgba(200, 200, 200, 255, colorA), rgba(200, 200, 200, 0, colorB), fadePaint)
    nvgBeginPath(vg)
    nvgRect(vg, x + 4, y + h - 6, w - 8, 6f)
    nvgFillPaint(vg, fadePaint)
    nvgFill(vg)

    // Scroll bar
    nvgBoxGradient(vg, x + w - 12 + 1, y + 4f + 1f, 8f, h - 8, 3f, 4f, rgba(0, 0, 0, 32, colorA), rgba(0, 0, 0, 92, colorB), shadowPaint)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + w - 12, y + 4, 8f, h - 8, 3f)
    nvgFillPaint(vg, shadowPaint)
    //nvgFillColor(vg, rgba(255,0,0,128, color));
    nvgFill(vg)

    scrollh = h / stackh * (h - 8)
    nvgBoxGradient(vg,
            x + w - 12f - 1f,
            y + 4f + (h - 8f - scrollh) * u - 1,
            8f,
            scrollh,
            3f,
            4f,
            rgba(220, 220, 220, 255, colorA),
            rgba(128, 128, 128, 255, colorB),
            shadowPaint)
    nvgBeginPath(vg)
    nvgRoundedRect(vg, x + w - 12 + 1, y + 4f + 1f + (h - 8f - scrollh) * u, (8 - 2).toFloat(), scrollh - 2, 2f)
    nvgFillPaint(vg, shadowPaint)
    //nvgFillColor(vg, rgba(0,0,0,128, color));
    nvgFill(vg)

    nvgRestore(vg)
}

private fun drawColorwheel(vg: Long, x: Float, y: Float, w: Float, h: Float, t: Float) {
    var i: Int
    val r0: Float
    val r1: Float
    var ax: Float
    var ay: Float
    var bx: Float
    var by: Float
    val cx: Float
    val cy: Float
    val aeps: Float
    val r: Float
    val hue = sin((t * 0.12f).toDouble()).toFloat()
    val paint = paintA

    nvgSave(vg)

    /*nvgBeginPath(vg);
nvgRect(vg, x,y,w,h);
nvgFillColor(vg, rgba(255,0,0,128, colorA));
nvgFill(vg);*/

    cx = x + w * 0.5f
    cy = y + h * 0.5f
    r1 = (if (w < h) w else h) * 0.5f - 5.0f
    r0 = r1 - 20.0f
    aeps = 0.5f / r1    // half a pixel arc length in radians (2pi cancels out).

    i = 0
    while (i < 6) {
        val a0 = i.toFloat() / 6.0f * NVG_PI * 2.0f - aeps
        val a1 = (i + 1.0f) / 6.0f * NVG_PI * 2.0f + aeps
        nvgBeginPath(vg)
        nvgArc(vg, cx, cy, r0, a0, a1, NVG_CW)
        nvgArc(vg, cx, cy, r1, a1, a0, NVG_CCW)
        nvgClosePath(vg)
        ax = cx + cos(a0.toDouble()).toFloat() * (r0 + r1) * 0.5f
        ay = cy + sin(a0.toDouble()).toFloat() * (r0 + r1) * 0.5f
        bx = cx + cos(a1.toDouble()).toFloat() * (r0 + r1) * 0.5f
        by = cy + sin(a1.toDouble()).toFloat() * (r0 + r1) * 0.5f
        nvgHSLA(a0 / (NVG_PI * 2), 1.0f, 0.55f, 255.toByte(), colorA)
        nvgHSLA(a1 / (NVG_PI * 2), 1.0f, 0.55f, 255.toByte(), colorB)
        nvgLinearGradient(vg, ax, ay, bx, by, colorA, colorB, paint)
        nvgFillPaint(vg, paint)
        nvgFill(vg)
        i++
    }

    nvgBeginPath(vg)
    nvgCircle(vg, cx, cy, r0 - 0.5f)
    nvgCircle(vg, cx, cy, r1 + 0.5f)
    nvgStrokeColor(vg, rgba(0, 0, 0, 64, colorA))
    nvgStrokeWidth(vg, 1.0f)
    nvgStroke(vg)

    // Selector
    nvgSave(vg)
    nvgTranslate(vg, cx, cy)
    nvgRotate(vg, hue * NVG_PI * 2f)

    // Marker on
    nvgStrokeWidth(vg, 2.0f)
    nvgBeginPath(vg)
    nvgRect(vg, r0 - 1, -3f, r1 - r0 + 2, 6f)
    nvgStrokeColor(vg, rgba(255, 255, 255, 192, colorA))
    nvgStroke(vg)

    nvgBoxGradient(vg, r0 - 3, -5f, r1 - r0 + 6, 10f, 2f, 4f, rgba(0, 0, 0, 128, colorA), rgba(0, 0, 0, 0, colorB), paint)
    nvgBeginPath(vg)
    nvgRect(vg, r0 - 2f - 10f, (-4 - 10).toFloat(), r1 - r0 + 4f + 20f, (8 + 20).toFloat())
    nvgRect(vg, r0 - 2, -4f, r1 - r0 + 4, 8f)
    nvgPathWinding(vg, NVG_HOLE)
    nvgFillPaint(vg, paint)
    nvgFill(vg)

    // Center triangle
    r = r0 - 6
    ax = cos((120.0f / 180.0f * NVG_PI).toDouble()).toFloat() * r
    ay = sin((120.0f / 180.0f * NVG_PI).toDouble()).toFloat() * r
    bx = cos((-120.0f / 180.0f * NVG_PI).toDouble()).toFloat() * r
    by = sin((-120.0f / 180.0f * NVG_PI).toDouble()).toFloat() * r
    nvgBeginPath(vg)
    nvgMoveTo(vg, r, 0f)
    nvgLineTo(vg, ax, ay)
    nvgLineTo(vg, bx, by)
    nvgClosePath(vg)
    nvgHSLA(hue, 1.0f, 0.5f, 255.toByte(), colorA)
    nvgLinearGradient(vg, r, 0f, ax, ay, colorA, rgba(255, 255, 255, 255, colorB), paint)
    nvgFillPaint(vg, paint)
    nvgFill(vg)
    nvgLinearGradient(vg, (r + ax) * 0.5f, (0 + ay) * 0.5f, bx, by, rgba(0, 0, 0, 0, colorA), rgba(0, 0, 0, 255, colorB), paint)
    nvgFillPaint(vg, paint)
    nvgFill(vg)
    nvgStrokeColor(vg, rgba(0, 0, 0, 64, colorA))
    nvgStroke(vg)

    // Select circle on triangle
    ax = cos((120.0f / 180.0f * NVG_PI).toDouble()).toFloat() * r * 0.3f
    ay = sin((120.0f / 180.0f * NVG_PI).toDouble()).toFloat() * r * 0.4f
    nvgStrokeWidth(vg, 2.0f)
    nvgBeginPath(vg)
    nvgCircle(vg, ax, ay, 5f)
    nvgStrokeColor(vg, rgba(255, 255, 255, 192, colorA))
    nvgStroke(vg)

    nvgRadialGradient(vg, ax, ay, 7f, 9f, rgba(0, 0, 0, 64, colorA), rgba(0, 0, 0, 0, colorB), paint)
    nvgBeginPath(vg)
    nvgRect(vg, ax - 20, ay - 20, 40f, 40f)
    nvgCircle(vg, ax, ay, 7f)
    nvgPathWinding(vg, NVG_HOLE)
    nvgFillPaint(vg, paint)
    nvgFill(vg)

    nvgRestore(vg)

    nvgRestore(vg)
}

private val pts = FloatArray(4 * 2)

private val joins = intArrayOf(NVG_MITER, NVG_ROUND, NVG_BEVEL)
private val caps = intArrayOf(NVG_BUTT, NVG_ROUND, NVG_SQUARE)

private fun drawLines(vg: Long, x: Float, y: Float, w: Float, h: Float, t: Float) {
    var i: Int
    var j: Int
    val pad = 5.0f
    val s = w / 9.0f - pad * 2

    nvgSave(vg)
    drawLinesPoints(pts, s, t)

    i = 0
    while (i < 3) {
        j = 0
        while (j < 3) {
            val fx = x + s * 0.5f + (i * 3 + j) / 9.0f * w + pad
            val fy = y - s * 0.5f + pad

            nvgLineCap(vg, caps[i])
            nvgLineJoin(vg, joins[j])

            nvgStrokeWidth(vg, s * 0.3f)
            drawLinesLine(vg, fx, fy)
            j++
        }
        i++
    }

    nvgRestore(vg)
}

private fun drawLinesPoints(pts: FloatArray, s: Float, t: Float) {
    pts[0] = -s * 0.25f + cos((t * 0.3f).toDouble()).toFloat() * s * 0.5f
    pts[1] = sin((t * 0.3f).toDouble()).toFloat() * s * 0.5f
    pts[2] = -s * 0.25f
    pts[3] = 0f
    pts[4] = s * 0.25f
    pts[5] = 0f
    pts[6] = s * 0.25f + cos((-t * 0.3f).toDouble()).toFloat() * s * 0.5f
    pts[7] = sin((-t * 0.3f).toDouble()).toFloat() * s * 0.5f
}

private fun drawLinesLine(vg: Long, fx: Float, fy: Float) {
    val pts = pts

    nvgStrokeColor(vg, rgba(0, 0, 0, 160, colorA))
    nvgBeginPath(vg)
    nvgMoveTo(vg, fx + pts[0], fy + pts[1])
    nvgLineTo(vg, fx + pts[2], fy + pts[3])
    nvgLineTo(vg, fx + pts[4], fy + pts[5])
    nvgLineTo(vg, fx + pts[6], fy + pts[7])
    nvgStroke(vg)

    nvgLineCap(vg, NVG_BUTT)
    nvgLineJoin(vg, NVG_BEVEL)

    nvgStrokeWidth(vg, 1.0f)
    nvgStrokeColor(vg, rgba(0, 192, 255, 255, colorA))
    nvgBeginPath(vg)
    nvgMoveTo(vg, fx + pts[0], fy + pts[1])
    nvgLineTo(vg, fx + pts[2], fy + pts[3])
    nvgLineTo(vg, fx + pts[4], fy + pts[5])
    nvgLineTo(vg, fx + pts[6], fy + pts[7])
    nvgStroke(vg)
}

fun loadDemoData(vg: Long, data: DemoData): Int {
    var i: Int

    if (vg == NULL)
        return -1

    i = 0
    while (i < 12) {
        val file = "/demo/nanovg/images/image" + (i + 1) + ".jpg"
        val img = loadResource(file, 32 * 1024)
        data.images[i] = nvgCreateImageMem(vg, 0, img)
        if (data.images[i] == 0) {
            System.err.format("Could not load %s.\n", file)
            return -1
        }
        i++
    }

    data.fontIcons = nvgCreateFontMem(vg, "icons", data.entypo, 0)
    if (data.fontIcons == -1) {
        System.err.format("Could not add font icons.\n")
        return -1
    }
    data.fontNormal = nvgCreateFontMem(vg, "sans", data.RobotoRegular, 0)
    if (data.fontNormal == -1) {
        System.err.format("Could not add font italic.\n")
        return -1
    }
    data.fontBold = nvgCreateFontMem(vg, "sans-bold", data.RobotoBold, 0)
    if (data.fontBold == -1) {
        System.err.format("Could not add font bold.\n")
        return -1
    }

    return 0
}

fun freeDemoData(vg: Long, data: DemoData) {
    var i: Int

    if (vg == NULL)
        return

    i = 0
    while (i < 12) {
        nvgDeleteImage(vg, data.images[i])
        i++
    }

    memFree(hoverText)
    memFree(paragraph)

    memFree(ICON_TRASH)
    memFree(ICON_LOGIN)
    memFree(ICON_CHECK)
    memFree(ICON_CHEVRON_RIGHT)
    memFree(ICON_CIRCLED_CROSS)
    memFree(ICON_SEARCH)
}

private fun drawParagraph(vg: Long, x: Float, y: Float, width: Float, height: Float, mx: Float, my: Float) {
    var y = y
    val px: Float
    var a: Float
    var gx = 0.0f
    var gy = 0.0f
    var gutter = 0

    nvgSave(vg)

    nvgFontSize(vg, 18.0f)
    nvgFontFace(vg, "sans")
    nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
    nvgTextMetrics(vg, null, null, lineh)

    // The text break API can be used to fill a large buffer of rows,
    // or to iterate over the text just few lines (or just one) at a time.
    // The "next" variable of the last returned item tells where to continue.
    var start = memAddress(paragraph)
    val end = start + paragraph.remaining()
    var lnum = 0
    var nrows: Int = nnvgTextBreakLines(vg, start, end, width, memAddress(rows), 3)
    while (nrows != 0) {
        for (i in 0..nrows - 1) {
            val row = rows.get(i)
            val hit = mx > x && mx < x + width && my >= y && my < y + lineh.get(0)

            nvgBeginPath(vg)
            nvgFillColor(vg, rgba(255, 255, 255, if (hit) 64 else 16, colorA))
            nvgRect(vg, x, y, row.width(), lineh.get(0))
            nvgFill(vg)

            nvgFillColor(vg, rgba(255, 255, 255, 255, colorA))
            nnvgText(vg, x, y, row.start(), row.end())

            if (hit) {
                drawCaret(vg, row, lineh.get(0), x, y, mx)

                gutter = lnum + 1
                gx = x - 10
                gy = y + lineh.get(0) / 2
            }
            lnum++
            y += lineh.get(0)
        }
        // Keep going...
        start = rows.get(nrows - 1).next()
        nrows = nnvgTextBreakLines(vg, start, end, width, memAddress(rows), 3)
    }

    if (gutter != 0)
        drawGutter(vg, gutter, gx, gy, bounds)

    y += 20.0f

    nvgFontSize(vg, 13.0f)
    nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
    nvgTextLineHeight(vg, 1.2f)

    nvgTextBoxBounds(vg, x, y, 150f, hoverText, NULL, bounds)

    // Fade the tooltip out when close to it.
    gx = abs((mx - (bounds.get(0) + bounds.get(2)) * 0.5f) / (bounds.get(0) - bounds.get(2)))
    gy = abs((my - (bounds.get(1) + bounds.get(3)) * 0.5f) / (bounds.get(1) - bounds.get(3)))
    a = maxf(gx, gy) - 0.5f
    a = clampf(a, 0f, 1f)
    nvgGlobalAlpha(vg, a)

    nvgBeginPath(vg)
    nvgFillColor(vg, rgba(220, 220, 220, 255, colorA))
    nvgRoundedRect(vg, bounds.get(0) - 2, bounds.get(1) - 2, ((bounds.get(2) - bounds.get(0)).toInt() + 4).toFloat(), ((bounds.get(3) - bounds.get(1)).toInt() + 4).toFloat(), 3f)
    px = ((bounds.get(2) + bounds.get(0)) / 2).toInt().toFloat()
    nvgMoveTo(vg, px, bounds.get(1) - 10)
    nvgLineTo(vg, px + 7, bounds.get(1) + 1)
    nvgLineTo(vg, px - 7, bounds.get(1) + 1)
    nvgFill(vg)

    nvgFillColor(vg, rgba(0, 0, 0, 220, colorA))
    nvgTextBox(vg, x, y, 150f, hoverText, NULL)

    nvgRestore(vg)
}

private fun drawCaret(vg: Long, row: NVGTextRow, lineh: Float, x: Float, y: Float, mx: Float) {
    var caretx = if (mx < x + row.width() / 2) x else x + row.width()
    var px = x
    val nglyphs = nnvgTextGlyphPositions(vg, x, y, row.start(), row.end(), memAddress(glyphs), 100)
    for (j in 0..nglyphs - 1) {
        val glyphPosition = glyphs.get(j)
        val x0 = glyphPosition.x()
        val x1 = if (j + 1 < nglyphs) glyphs.get(j + 1).x() else x + row.width()
        val gx2 = x0 * 0.3f + x1 * 0.7f
        if (mx >= px && mx < gx2)
            caretx = glyphPosition.x()
        px = gx2
    }
    nvgBeginPath(vg)
    nvgFillColor(vg, rgba(255, 192, 0, 255, colorA))
    nvgRect(vg, caretx, y, 1f, lineh)
    nvgFill(vg)
}

private fun drawGutter(vg: Long, gutter: Int, gx: Float, gy: Float, bounds: FloatBuffer) {
    val txt = Integer.toString(gutter)

    nvgFontSize(vg, 13.0f)
    nvgTextAlign(vg, NVG_ALIGN_RIGHT or NVG_ALIGN_MIDDLE)

    nvgTextBounds(vg, gx, gy, txt, NULL, bounds)

    nvgBeginPath(vg)
    nvgFillColor(vg, rgba(255, 192, 0, 255, colorA))
    nvgRoundedRect(vg,
            (bounds.get(0).toInt() - 4).toFloat(),
            (bounds.get(1).toInt() - 2).toFloat(),
            ((bounds.get(2) - bounds.get(0)).toInt() + 8).toFloat(),
            ((bounds.get(3) - bounds.get(1)).toInt() + 4).toFloat(),
            (((bounds.get(3) - bounds.get(1)).toInt() + 4) / 2 - 1).toFloat())
    nvgFill(vg)

    nvgFillColor(vg, rgba(32, 32, 32, 255, colorA))
    nvgText(vg, gx, gy, txt, NULL)
}

private fun drawWidths(vg: Long, x: Float, y: Float, width: Float) {
    var y = y
    var i: Int

    nvgSave(vg)

    nvgStrokeColor(vg, rgba(0, 0, 0, 255, colorA))

    i = 0
    while (i < 20) {
        val w = (i + 0.5f) * 0.1f
        nvgStrokeWidth(vg, w)
        nvgBeginPath(vg)
        nvgMoveTo(vg, x, y)
        nvgLineTo(vg, x + width, y + width * 0.3f)
        nvgStroke(vg)
        y += 10f
        i++
    }

    nvgRestore(vg)
}

private fun drawCaps(vg: Long, x: Float, y: Float, width: Float) {
    var i: Int
    val caps = intArrayOf(NVG_BUTT, NVG_ROUND, NVG_SQUARE)
    val lineWidth = 8.0f

    nvgSave(vg)

    nvgBeginPath(vg)
    nvgRect(vg, x - lineWidth / 2, y, width + lineWidth, 40f)
    nvgFillColor(vg, rgba(255, 255, 255, 32, colorA))
    nvgFill(vg)

    nvgBeginPath(vg)
    nvgRect(vg, x, y, width, 40f)
    nvgFillColor(vg, rgba(255, 255, 255, 32, colorA))
    nvgFill(vg)

    nvgStrokeWidth(vg, lineWidth)
    i = 0
    while (i < 3) {
        nvgLineCap(vg, caps[i])
        nvgStrokeColor(vg, rgba(0, 0, 0, 255, colorA))
        nvgBeginPath(vg)
        nvgMoveTo(vg, x, y + (i * 10).toFloat() + 5f)
        nvgLineTo(vg, x + width, y + (i * 10).toFloat() + 5f)
        nvgStroke(vg)
        i++
    }

    nvgRestore(vg)
}

private fun drawScissor(vg: Long, x: Float, y: Float, t: Float) {

    nvgSave(vg)

    // Draw first rect and set scissor to it's area.
    nvgTranslate(vg, x, y)
    nvgRotate(vg, nvgDegToRad(5f))
    nvgBeginPath(vg)
    nvgRect(vg, -20f, -20f, 60f, 40f)
    nvgFillColor(vg, rgba(255, 0, 0, 255, colorA))
    nvgFill(vg)
    nvgScissor(vg, -20f, -20f, 60f, 40f)

    // Draw second rectangle with offset and rotation.
    nvgTranslate(vg, 40f, 0f)
    nvgRotate(vg, t)

    // Draw the intended second rectangle without any scissoring.
    nvgSave(vg)
    nvgResetScissor(vg)
    nvgBeginPath(vg)
    nvgRect(vg, -20f, -10f, 60f, 30f)
    nvgFillColor(vg, rgba(255, 128, 0, 64, colorA))
    nvgFill(vg)
    nvgRestore(vg)

    // Draw second rectangle with combined scissoring.
    nvgIntersectScissor(vg, -20f, -10f, 60f, 30f)
    nvgBeginPath(vg)
    nvgRect(vg, -20f, -10f, 60f, 30f)
    nvgFillColor(vg, rgba(255, 128, 0, 255, colorA))
    nvgFill(vg)

    nvgRestore(vg)
}

fun renderDemo(
        vg: Long, mx: Float, my: Float, width: Float, height: Float,
        t: Float, blowup: Boolean, data: DemoData
) {
    val x: Float
    var y: Float
    val popy: Float

    drawEyes(vg, width - 250, 50f, 150f, 100f, mx, my, t)
    drawParagraph(vg, width - 450, 50f, 150f, 100f, mx, my)
    drawGraph(vg, 0f, height / 2, width, height / 2, t)
    drawColorwheel(vg, width - 300, height - 300, 250.0f, 250.0f, t)

    // Line joints
    drawLines(vg, 120f, height - 50, 600f, 50f, t)

    // Line caps
    drawWidths(vg, 10f, 50f, 30f)

    // Line caps
    drawCaps(vg, 10f, 300f, 30f)

    drawScissor(vg, 50f, height - 80, t)

    nvgSave(vg)
    if (blowup) {
        nvgRotate(vg, sin((t * 0.3f).toDouble()).toFloat() * 5.0f / 180.0f * NVG_PI)
        nvgScale(vg, 2.0f, 2.0f)
    }

    // Widgets
    drawWindow(vg, "Widgets `n Stuff", 50f, 50f, 300f, 400f)
    x = 60f
    y = 95f
    drawSearchBox(vg, "Search", x, y, 280f, 25f)
    y += 40f
    drawDropDown(vg, "Effects", x, y, 280f, 28f)
    popy = y + 14
    y += 45f

    // Form
    drawLabel(vg, "Login", x, y, 280f, 20f)
    y += 25f
    drawEditBox(vg, "Email", x, y, 280f, 28f)
    y += 35f
    drawEditBox(vg, "Password", x, y, 280f, 28f)
    y += 38f
    drawCheckBox(vg, "Remember me", x, y, 140f, 28f)
    drawButton(vg, ICON_LOGIN, "Sign in", x + 138, y, 140f, 28f, rgba(0, 96, 128, 255, colorA))
    y += 45f

    // Slider
    drawLabel(vg, "Diameter", x, y, 280f, 20f)
    y += 25f
    drawEditBoxNum(vg, "123.00", "px", x + 180, y, 100f, 28f)
    drawSlider(vg, 0.4f, x, y, 170f, 28f)
    y += 55f

    drawButton(vg, ICON_TRASH, "Delete", x, y, 160f, 28f, rgba(128, 16, 8, 255, colorA))
    drawButton(vg, null, "Cancel", x + 170, y, 110f, 28f, rgba(0, 0, 0, 0, colorA))

    // Thumbnails box
    drawThumbnails(vg, 365f, popy - 30, 160f, 300f, data.images, 12, t)

    nvgRestore(vg)
}

private fun mini(a: Int, b: Int): Int {
    return if (a < b) a else b
}

private fun unpremultiplyAlpha(image: ByteBuffer, w: Int, h: Int, stride: Int) {
    var x: Int
    var y: Int

    // Unpremultiply
    y = 0
    while (y < h) {
        var row = y * stride
        x = 0
        while (x < w) {
            val r = image.get(row + 0).toInt()
            val g = image.get(row + 1).toInt()
            val b = image.get(row + 2).toInt()
            val a = image.get(row + 3).toInt()
            if (a != 0) {
                image.put(row + 0, mini(r * 255 / a, 255).toByte())
                image.put(row + 1, mini(g * 255 / a, 255).toByte())
                image.put(row + 2, mini(b * 255 / a, 255).toByte())
            }
            row += 4
            x++
        }
        y++
    }

    // Defringe
    y = 0
    while (y < h) {
        var row = y * stride
        x = 0
        while (x < w) {
            var r = 0
            var g = 0
            var b = 0
            val a = image.get(row + 3).toInt()
            var n = 0
            if (a == 0) {
                if (x - 1 > 0 && image.get(row - 1).toInt() != 0) {
                    r += image.get(row - 4).toInt()
                    g += image.get(row - 3).toInt()
                    b += image.get(row - 2).toInt()
                    n++
                }
                if (x + 1 < w && image.get(row + 7).toInt() != 0) {
                    r += image.get(row + 4).toInt()
                    g += image.get(row + 5).toInt()
                    b += image.get(row + 6).toInt()
                    n++
                }
                if (y - 1 > 0 && image.get(row - stride + 3).toInt() != 0) {
                    r += image.get(row - stride).toInt()
                    g += image.get(row - stride + 1).toInt()
                    b += image.get(row - stride + 2).toInt()
                    n++
                }
                if (y + 1 < h && image.get(row + stride + 3).toInt() != 0) {
                    r += image.get(row + stride).toInt()
                    g += image.get(row + stride + 1).toInt()
                    b += image.get(row + stride + 2).toInt()
                    n++
                }
                if (n > 0) {
                    image.put(row + 0, (r / n).toByte())
                    image.put(row + 1, (g / n).toByte())
                    image.put(row + 2, (b / n).toByte())
                }
            }
            row += 4
            x++
        }
        y++
    }
}

private fun setAlpha(image: ByteBuffer, w: Int, h: Int, stride: Int, a: Byte) {
    var x: Int
    var y: Int
    y = 0
    while (y < h) {
        val row = y * stride
        x = 0
        while (x < w) {
            image.put(row + x * 4 + 3, a)
            x++
        }
        y++
    }
}

private fun flipHorizontal(image: ByteBuffer, w: Int, h: Int, stride: Int) {
    var i = 0
    var j = h - 1
    var k: Int
    while (i < j) {
        val ri = i * stride
        val rj = j * stride
        k = 0
        while (k < w * 4) {
            val t = image.get(ri + k)
            image.put(ri + k, image.get(rj + k))
            image.put(rj + k, t)
            k++
        }
        i++
        j--
    }
}

fun saveScreenShot(w: Int, h: Int, premult: Boolean, name: String) {
    val image = memAlloc(w * h * 4) ?: return

    // TODO: Make this work for GLES
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, image)
    if (premult)
        unpremultiplyAlpha(image, w, h, w * 4)
    else
        setAlpha(image, w, h, w * 4, 255.toByte())
    flipHorizontal(image, w, h, w * 4)
    stbi_write_png(name, w, h, 4, image, w * 4)
    memFree(image)
}

// PERF

val GRAPH_RENDER_FPS = 0
val GRAPH_RENDER_MS = 1
val GRAPH_RENDER_PERCENT = 2

private val GRAPH_HISTORY_COUNT = 100

private val GPU_QUERY_COUNT = 5

// TODO: move to implementation
fun initGPUTimer(timer: GPUtimer) {
    //memset(timer, 0, sizeof(*timer));
    timer.supported = GL.getCapabilities().GL_ARB_timer_query
    timer.cur = 0
    timer.ret = 0
    BufferUtils.zeroBuffer(timer.queries)

    if (timer.supported)
        glGenQueries(timer.queries)
}

fun startGPUTimer(timer: GPUtimer) {
    if (!timer.supported)
        return
    glBeginQuery(GL_TIME_ELAPSED, timer.queries.get(timer.cur % GPU_QUERY_COUNT))
    timer.cur++
}

fun stopGPUTimer(timer: GPUtimer, times: FloatBuffer, maxTimes: Int): Int {
    var n = 0
    if (!timer.supported)
        return 0

    glEndQuery(GL_TIME_ELAPSED)

    twr(stackPush()) { stack ->
        val available = stack.ints(1)
        while (available.get(0) != 0 && timer.ret <= timer.cur) {
            // check for results if there are any
            glGetQueryObjectiv(timer.queries.get(timer.ret % GPU_QUERY_COUNT), GL_QUERY_RESULT_AVAILABLE, available)
            if (available.get(0) != 0) {
                val timeElapsed = stack.mallocLong(1)
                glGetQueryObjectui64v(timer.queries.get(timer.ret % GPU_QUERY_COUNT), GL_QUERY_RESULT, timeElapsed)
                timer.ret++
                if (n < maxTimes) {
                    times.put(n, (timeElapsed.get(0).toDouble() * 1e-9).toFloat())
                    n++
                }
            }
        }
    }
    return n
}

fun initGraph(fps: PerfGraph, style: Int, name: String) {
    fps.style = style
    memUTF8(name, true, fps.name)
    Arrays.fill(fps.values, 0f)
    fps.head = 0
}

fun updateGraph(fps: PerfGraph, frameTime: Float) {
    fps.head = (fps.head + 1) % GRAPH_HISTORY_COUNT
    fps.values[fps.head] = frameTime
}

fun getGraphAverage(fps: PerfGraph): Float {
    var avg = 0f
    for (i in 0..GRAPH_HISTORY_COUNT - 1) {
        avg += fps.values[i]
    }
    return avg / GRAPH_HISTORY_COUNT.toFloat()
}

fun renderGraph(vg: Long, x: Float, y: Float, fps: PerfGraph) {
    val avg = getGraphAverage(fps)

    val w = 200
    val h = 35

    nvgBeginPath(vg)
    nvgRect(vg, x, y, w.toFloat(), h.toFloat())
    nvgFillColor(vg, rgba(0, 0, 0, 128, colorA))
    nvgFill(vg)

    nvgBeginPath(vg)
    nvgMoveTo(vg, x, y + h)
    if (fps.style == GRAPH_RENDER_FPS) {
        for (i in 0..GRAPH_HISTORY_COUNT - 1) {
            var v = 1.0f / (0.00001f + fps.values[(fps.head + i) % GRAPH_HISTORY_COUNT])
            val vx: Float
            val vy: Float
            if (v > 1000.0f) v = 1000.0f
            vx = x + i.toFloat() / (GRAPH_HISTORY_COUNT - 1) * w
            vy = y + h - v / 1000.0f * h
            nvgLineTo(vg, vx, vy)
        }
    } else if (fps.style == GRAPH_RENDER_PERCENT) {
        for (i in 0..GRAPH_HISTORY_COUNT - 1) {
            var v = fps.values[(fps.head + i) % GRAPH_HISTORY_COUNT] * 1.0f
            val vx: Float
            val vy: Float
            if (v > 100.0f) v = 100.0f
            vx = x + i.toFloat() / (GRAPH_HISTORY_COUNT - 1) * w
            vy = y + h - v / 100.0f * h
            nvgLineTo(vg, vx, vy)
        }
    } else {
        for (i in 0..GRAPH_HISTORY_COUNT - 1) {
            var v = fps.values[(fps.head + i) % GRAPH_HISTORY_COUNT] * 1000.0f
            val vx: Float
            val vy: Float
            if (v > 4.0f) v = 4.0f
            vx = x + i.toFloat() / (GRAPH_HISTORY_COUNT - 1) * w
            vy = y + h - v / 4.0f * h
            nvgLineTo(vg, vx, vy)
        }
    }
    nvgLineTo(vg, x + w, y + h)
    nvgFillColor(vg, rgba(255, 192, 0, 128, colorA))
    nvgFill(vg)

    nvgFontFace(vg, "sans")

    if (fps.name.get(0).toChar() != '\u0000') {
        nvgFontSize(vg, 14.0f)
        nvgTextAlign(vg, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
        nvgFillColor(vg, rgba(240, 240, 240, 192, colorA))
        nvgText(vg, x + 3, y + 1, fps.name, NULL)
    }

    if (fps.style == GRAPH_RENDER_FPS) {
        nvgFontSize(vg, 18.0f)
        nvgTextAlign(vg, NVG_ALIGN_RIGHT or NVG_ALIGN_TOP)
        nvgFillColor(vg, rgba(240, 240, 240, 255, colorA))
        nvgText(vg, x + w - 3, y + 1, String.format("%.2f FPS", 1.0f / avg), NULL)

        nvgFontSize(vg, 15.0f)
        nvgTextAlign(vg, NVG_ALIGN_RIGHT or NVG_ALIGN_BOTTOM)
        nvgFillColor(vg, rgba(240, 240, 240, 160, colorA))
        nvgText(vg, x + w - 3, y + h - 1, String.format("%.2f ms", avg * 1000.0f), NULL)
    } else if (fps.style == GRAPH_RENDER_PERCENT) {
        nvgFontSize(vg, 18.0f)
        nvgTextAlign(vg, NVG_ALIGN_RIGHT or NVG_ALIGN_TOP)
        nvgFillColor(vg, rgba(240, 240, 240, 255, colorA))
        nvgText(vg, x + w - 3, y + 1, String.format("%.1f %%", avg * 1.0f), NULL)
    } else {
        nvgFontSize(vg, 18.0f)
        nvgTextAlign(vg, NVG_ALIGN_RIGHT or NVG_ALIGN_TOP)
        nvgFillColor(vg, rgba(240, 240, 240, 255, colorA))
        nvgText(vg, x + w - 3, y + 1, String.format("%.2f ms", avg * 1000.0f), NULL)
    }
}
