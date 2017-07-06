package com.grimfox.gec.ui

import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG

var NVGColor.rByte: Byte
    get() = colorFloatToByte(r())
    set(value) {
        r(colorByteToFloat(value))
    }
var NVGColor.gByte: Byte
    get() = colorFloatToByte(g())
    set(value) {
        g(colorByteToFloat(value))
    }
var NVGColor.bByte: Byte
    get() = colorFloatToByte(b())
    set(value) {
        b(colorByteToFloat(value))
    }
var NVGColor.aByte: Byte
    get() = colorFloatToByte(a())
    set(value) {
        a(colorByteToFloat(value))
    }

var NVGColor.r: Float
    get() = r()
    set(value) {
        r(value)
    }
var NVGColor.g: Float
    get() = g()
    set(value) {
        g(value)
    }
var NVGColor.b: Float
    get() = b()
    set(value) {
        b(value)
    }
var NVGColor.a: Float
    get() = a()
    set(value) {
        a(value)
    }

var NVGColor.rInt: Int
    get() = colorFloatToInt(r)
    set(value) {
        r = colorIntToFloat(value)
    }
var NVGColor.gInt: Int
    get() = colorFloatToInt(g)
    set(value) {
        g = colorIntToFloat(value)
    }
var NVGColor.bInt: Int
    get() = colorFloatToInt(b)
    set(value) {
        b = colorIntToFloat(value)
    }
var NVGColor.aInt: Int
    get() = colorFloatToInt(a)
    set(value) {
        a = colorIntToFloat(value)
    }

fun NVGColor.set(r: Int, g: Int, b: Int, a: Int): NVGColor {
    return NanoVG.nvgRGBA(r.toByte(), g.toByte(), b.toByte(), a.toByte(), this)
}

fun NVGColor.set(r: Int, g: Int, b: Int): NVGColor {
    return NanoVG.nvgRGB(r.toByte(), g.toByte(), b.toByte(), this)
}

fun NVGColor.set(r: Byte, g: Byte, b: Byte, a: Byte): NVGColor {
    return NanoVG.nvgRGBA(r, g, b, a, this)
}

fun NVGColor.set(r: Byte, g: Byte, b: Byte): NVGColor {
    return NanoVG.nvgRGB(r, g, b, this)
}

fun NVGColor.set(r: Float, g: Float, b: Float, a: Float): NVGColor {
    return NanoVG.nvgRGBAf(r, g, b, a, this)
}

fun NVGColor.set(r: Float, g: Float, b: Float): NVGColor {
    return NanoVG.nvgRGBf(r, g, b, this)
}

private fun colorByteToInt(b: Byte) = (b.toInt() and 0xFF)
private fun colorByteToFloat(b: Byte) = colorIntToFloat(colorByteToInt(b))
private fun colorIntToByte(i: Int) = i.toByte()
private fun colorFloatToByte(f: Float) = colorIntToByte(colorFloatToInt(f))
private fun colorIntToFloat(i: Int) = i / 255.0f
private fun colorFloatToInt(f: Float) = Math.round(f * 255)

fun color(r: Int, g: Int, b: Int, a: Int): NVGColor {
    return NVGColor.create().set(r, g, b, a)
}

fun color(r: Int, g: Int, b: Int): NVGColor {
    return NVGColor.create().set(r, g, b, 255)
}

fun color(r: Float, g: Float, b: Float): NVGColor {
    return NVGColor.create().set(r, g, b, 1.0f)
}

fun color(r: Float, g: Float, b: Float, a: Float): NVGColor {
    return NVGColor.create().set(r, g, b, a)
}

val NO_COLOR = color(0, 0, 0, 0)
