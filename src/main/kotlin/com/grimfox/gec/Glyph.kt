package com.grimfox.gec

import com.grimfox.gec.ui.GlyphLayer
import com.grimfox.gec.ui.UiLayout
import com.grimfox.gec.ui.color
import com.grimfox.gec.ui.widgets.Block

private val glyphIndex = Array(95) { i -> (i + 32).toChar().toString() }

private val BASE_SCALE = 20.0f

val GLYPH_CLOSE = glyphIndex[0]
val GLYPH_MINIMIZE = glyphIndex[1]
val GLYPH_RESTORE = glyphIndex[2]
val GLYPH_MAXIMIZE = glyphIndex[3]
val GLYPH_SAVE = glyphIndex[4]
val GLYPH_FILE = glyphIndex[5]
val GLYPH_FOLDER = glyphIndex[6]
val GLYPH_LOAD_ARROW = glyphIndex[7]
val GLYPH_STAR = glyphIndex[8]
val GLYPH_GEAR = glyphIndex[9]
val GLYPH_CIRCLE = glyphIndex[10]
val GLYPH_HELP = glyphIndex[11]
val GLYPH_WARNING_TRIANGLE = glyphIndex[12]
val GLYPH_WARNING = glyphIndex[13]
val GLYPH_ERROR = glyphIndex[14]

private val COLOR_GLYPH_TRUE_WHITE = color(255, 255, 255)
private val COLOR_GLYPH_RED = color(255, 57, 43)
private val COLOR_GLYPH_BLUE = color(26, 161, 226)
private val COLOR_GLYPH_DARK_BLUE = color(0, 122, 204)
private val COLOR_GLYPH_YELLOW = color(217, 177, 114)
private val COLOR_GLYPH_GREEN = color(142, 210, 138)
private val COLOR_GLYPH_WHITE = color(243, 243, 243)
private val COLOR_GLYPH_LIGHT_GREY = color(152, 152, 154)
private val COLOR_GLYPH_BLACK = color(5, 5, 6)
private val COLOR_GLYPH_DARK_YELLOW = color(204, 177, 41)
private val COLOR_GLYPH_DARK_RED = color(191, 19, 19)

fun UiLayout.BLOCK_GLYPH_NEW_FILE(scale: Float): Block.() -> Block {
    val rescale = scale / BASE_SCALE
    return createMultiGlyph(GlyphLayer(GLYPH_FILE, glyphFont, 16.0f * rescale, COLOR_GLYPH_WHITE, 0.0f, 0.0f),
            GlyphLayer(GLYPH_STAR, glyphFont, 10.0f * rescale, COLOR_GLYPH_GREEN, -1.0f * rescale, -1.0f * rescale))
}

val UiLayout.BLOCK_GLYPH_NEW_FILE: Block.() -> Block get() = BLOCK_GLYPH_NEW_FILE(BASE_SCALE)

fun UiLayout.BLOCK_GLYPH_OPEN_FOLDER(scale: Float): Block.() -> Block {
    val rescale = scale / BASE_SCALE
    return createMultiGlyph(GlyphLayer(GLYPH_FOLDER, glyphFont, 16.0f * rescale, COLOR_GLYPH_YELLOW, 0.0f, 0.0f),
            GlyphLayer(GLYPH_LOAD_ARROW, glyphFont, 12.0f * rescale, COLOR_GLYPH_BLUE, -1.0f * rescale, -3.0f * rescale))
}

val UiLayout.BLOCK_GLYPH_OPEN_FOLDER: Block.() -> Block get() = BLOCK_GLYPH_OPEN_FOLDER(BASE_SCALE)

fun UiLayout.BLOCK_GLYPH_SAVE(scale: Float): Block.() -> Block {
    val rescale = scale / BASE_SCALE
    return createMultiGlyph(GlyphLayer(GLYPH_SAVE, glyphFont, 16.0f * rescale, COLOR_GLYPH_BLUE, 0.0f, 0.0f))
}

val UiLayout.BLOCK_GLYPH_SAVE: Block.() -> Block get() = BLOCK_GLYPH_SAVE(BASE_SCALE)

fun UiLayout.BLOCK_GLYPH_CLOSE(scale: Float): Block.() -> Block {
    val rescale = scale / BASE_SCALE
    return createMultiGlyph(GlyphLayer(GLYPH_CLOSE, glyphFont, 16.0f * rescale, COLOR_GLYPH_RED, 0.0f, 0.0f))
}

val UiLayout.BLOCK_GLYPH_CLOSE: Block.() -> Block get() = BLOCK_GLYPH_CLOSE(BASE_SCALE)

fun UiLayout.BLOCK_GLYPH_GEAR(scale: Float): Block.() -> Block {
    val rescale = scale / BASE_SCALE
    return createMultiGlyph(GlyphLayer(GLYPH_GEAR, glyphFont, 20.0f * rescale, COLOR_GLYPH_LIGHT_GREY, 0.0f, 0.0f))
}

val UiLayout.BLOCK_GLYPH_GEAR: Block.() -> Block get() = BLOCK_GLYPH_GEAR(BASE_SCALE)

fun UiLayout.BLOCK_GLYPH_HELP(scale: Float): Block.() -> Block {
    val rescale = scale / BASE_SCALE
    return createMultiGlyph(GlyphLayer(GLYPH_CIRCLE, glyphFont, 20.0f * rescale, COLOR_GLYPH_DARK_BLUE, 0.0f, 0.0f),
            GlyphLayer(GLYPH_HELP, glyphFont, 20.0f * rescale, COLOR_GLYPH_TRUE_WHITE, 0.0f, 0.0f))
}

val UiLayout.BLOCK_GLYPH_HELP: Block.() -> Block get() = BLOCK_GLYPH_HELP(BASE_SCALE)

fun UiLayout.BLOCK_GLYPH_WARNING(scale: Float): Block.() -> Block {
    val rescale = scale / BASE_SCALE
    return createMultiGlyph(GlyphLayer(GLYPH_WARNING_TRIANGLE, glyphFont, 20.0f * rescale, COLOR_GLYPH_DARK_YELLOW, 0.0f, 0.0f),
            GlyphLayer(GLYPH_WARNING, glyphFont, 20.0f * rescale, COLOR_GLYPH_BLACK, 0.0f, 0.0f))
}

val UiLayout.BLOCK_GLYPH_WARNING: Block.() -> Block get() = BLOCK_GLYPH_WARNING(BASE_SCALE)

fun UiLayout.BLOCK_GLYPH_ERROR(scale: Float): Block.() -> Block {
    val rescale = scale / BASE_SCALE
    return createMultiGlyph(GlyphLayer(GLYPH_CIRCLE, glyphFont, 20.0f * rescale, COLOR_GLYPH_DARK_RED, 0.0f, 0.0f),
            GlyphLayer(GLYPH_ERROR, glyphFont, 20.0f * rescale, COLOR_GLYPH_WHITE, 0.0f, 0.0f))
}

val UiLayout.BLOCK_GLYPH_ERROR: Block.() -> Block get() = BLOCK_GLYPH_ERROR(BASE_SCALE)
