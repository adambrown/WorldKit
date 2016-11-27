package com.grimfox.gec.ui

import com.grimfox.gec.extensions.twr
import org.lwjgl.nuklear.NkUserFont
import org.lwjgl.nuklear.NkUserFontGlyph
import org.lwjgl.nuklear.Nuklear.nnk_utf_decode
import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.*
import org.lwjgl.stb.STBTruetype.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.*
import java.nio.ByteBuffer
import org.lwjgl.stb.STBTruetype.stbtt_ScaleForPixelHeight
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.stb.STBTTPackContext
import org.lwjgl.stb.STBTruetype.stbtt_PackSetOversampling
import org.lwjgl.stb.STBTruetype.stbtt_PackFontRange
import org.lwjgl.stb.STBTruetype.stbtt_PackEnd
import org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV
import org.lwjgl.nuklear.Nuklear.NK_UTF_INVALID
import org.lwjgl.stb.STBTTAlignedQuad
import org.lwjgl.system.MemoryUtil.memAllocInt

fun createFont(fontData: ByteBuffer, fontHeight: Float, codePointOffset: Int, codePointCount: Int, textureWidth: Int, textureHeight: Int, font: NkUserFont = NkUserFont.create()): NkUserFont {
    val fontTexID = glGenTextures()

    val fontInfo = STBTTFontinfo.create()
    val cdata = STBTTPackedchar.create(codePointCount)

    var scale: Float = 1.0f
    var descent: Float = 1.0f

    twr(stackPush()) { stack ->
        stbtt_InitFont(fontInfo, fontData)
        scale = stbtt_ScaleForPixelHeight(fontInfo, fontHeight)

        val d = stack.mallocInt(1)
        stbtt_GetFontVMetrics(fontInfo, null, d, null)
        descent = d.get(0) * scale

        val bitmap = memAlloc(textureWidth * textureHeight)

        val pc = STBTTPackContext.mallocStack(stack)
        stbtt_PackBegin(pc, bitmap, textureWidth, textureHeight, 0, 1, null)
        stbtt_PackSetOversampling(pc, 4, 4)
        stbtt_PackFontRange(pc, fontData, 0, fontHeight, codePointOffset, cdata)
        stbtt_PackEnd(pc)

        val texture = memAlloc(textureWidth * textureHeight * 4)
        for (i in 0..bitmap.capacity() - 1) {
            texture.putInt(((bitmap.get(i).toInt() and 0xFF) shl 24) or 0x00FFFFFF)
        }
        texture.flip()

        glBindTexture(GL_TEXTURE_2D, fontTexID)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, textureWidth, textureHeight, 0, GL_RGBA, GL_UNSIGNED_INT_8_8_8_8_REV, texture)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)

        memFree(texture)
        memFree(bitmap)
    }
    font.width width@ { handle, h, text, len ->
        var text_width = 0.0f
        twr(stackPush()) { stack ->
            val unicode = stack.mallocInt(1)

            var glyph_len = nnk_utf_decode(text, memAddress(unicode), len)
            var text_len = glyph_len

            if (glyph_len == 0) {
                return@width 0.0f
            }
            val advance = stack.mallocInt(1)
            while (text_len <= len && glyph_len != 0) {
                if (unicode.get(0) == NK_UTF_INVALID) {
                    break
                }
                /* query currently drawn glyph information */
                stbtt_GetCodepointHMetrics(fontInfo, unicode.get(0), advance, null)
                text_width += advance.get(0) * scale

                /* offset next glyph */
                glyph_len = nnk_utf_decode(text + text_len, memAddress(unicode), len - text_len)
                text_len += glyph_len
            }
        }
        text_width
    }.height(fontHeight).query { handle, font_height, glyph, codePoint, nextCodePoint ->
        twr(stackPush()) { stack ->
            val x = stack.floats(0.0f)
            val y = stack.floats(0.0f)

            val q = STBTTAlignedQuad.malloc()
            val advance = memAllocInt(1)

            stbtt_GetPackedQuad(cdata, textureWidth, textureHeight, codePoint - codePointOffset, x, y, q, false)
            stbtt_GetCodepointHMetrics(fontInfo, codePoint, advance, null)

            val ufg = NkUserFontGlyph.create(glyph)

            ufg.width(q.x1() - q.x0())
            ufg.height(q.y1() - q.y0())
            ufg.offset().set(q.x0(), q.y0() + (fontHeight + descent))
            ufg.xadvance(advance.get(0) * scale)
            ufg.uv(0).set(q.s0(), q.t0())
            ufg.uv(1).set(q.s1(), q.t1())

            memFree(advance)
            q.free()
        }
    }.texture().id(fontTexID)
    return font
}