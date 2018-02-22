package com.grimfox.gec.ui.widgets

import com.grimfox.gec.ui.KeyboardHandler
import com.grimfox.gec.ui.UserInterface
import com.grimfox.gec.ui.nvgproxy.NPGlyphPosition
import com.grimfox.gec.util.ref
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import java.nio.ByteBuffer
import java.util.*

import com.grimfox.gec.ui.nvgproxy.*

class DynamicTextReference(initialString: String, val sizeLimit: Int, textStyle: TextStyle) {

    private val buffer = ByteBuffer.allocateDirect(sizeLimit)
    val reference = ref("").addListener { _, new ->
        val newValue = if (new.length <= sizeLimit) {
            new
        } else {
            new.substring(0, sizeLimit)
        }
        buffer.clear()
        val byteCount = MemoryUtil.memUTF8(newValue, false, buffer, 0)
        buffer.limit(byteCount)
    }
    val text = DynamicTextUtf8(buffer, textStyle)
    var style: TextStyle
        get() {
            return text.style
        }
        set(value) {
            text.style = value
        }
    init {
        reference.value = initialString
    }
}

class Caret(val nvg: Long, val dynamicText: DynamicTextReference, var position: Int = dynamicText.reference.value.length, var selection: Int = 0) {

    private val glyphPositions = NPGlyphPosition.create(dynamicText.sizeLimit)
    private var lastScale = 1.0f

    init {
        dynamicText.reference.addListener { _, new ->
            if (position > new.length) {
                position = new.length
            }
            if (position + selection > new.length ) {
                selection = new.length - position
            }
            if (position + selection < 0) {
                selection = 0
            }
        }
    }

    private fun getPositions(scale: Float = lastScale): List<Float> {
        lastScale = scale
        if (dynamicText.reference.value.isEmpty()) {
            return emptyList()
        }
        nvgFontFaceId(nvg, dynamicText.style.font.value)
        nvgFontSize(nvg, dynamicText.style.size.value * scale)
        nvgTextAlign(nvg, NVG_ALIGN_LEFT or NVG_ALIGN_MIDDLE)
        glyphPositions.clear()
        val count = nvgTextGlyphPositions(nvg, 0.0f, 0.0f, dynamicText.text.data, glyphPositions)
        val positions = ArrayList<Float>(count + 1)
        (0 until count).forEach {
            positions.add(glyphPositions.get(it).minx() / scale)
        }
        positions.add(glyphPositions.get(count - 1).maxx() / scale)
        return positions
    }

    fun getOffset(scale: Float = lastScale): Float {
        val caretPositions = getPositions(scale)
        if (caretPositions.isEmpty()) {
            return 0.0f
        }
        return clampedGet(position, caretPositions)
    }

    fun getOffsets(scale: Float = lastScale): Pair<Float, Float> {
        val caretPositions = getPositions(scale)
        if (caretPositions.isEmpty()) {
            return Pair(0.0f, 0.0f)
        }
        if (selection == 0) {
            val position = clampedGet(position, caretPositions)
            return Pair(position, position)
        }
        return Pair(clampedGet(position, caretPositions), clampedGet(position + selection, caretPositions))
    }

    fun getPosition(x: Float, scale: Float = lastScale): Int {
        val caretPositions = getPositions(scale)
        if (caretPositions.isEmpty() || x <= caretPositions.first()) {
            return 0
        }
        if (x >= caretPositions.last()) {
            return caretPositions.size - 1
        }
        val foundAt = Collections.binarySearch(caretPositions, x)
        if (foundAt >= 0) {
            return foundAt
        }
        val insertAt = -(foundAt + 1)
        if (insertAt == 0) {
            return 0
        }
        val previous = caretPositions[insertAt - 1]
        val next = caretPositions[insertAt]
        val deltaP = Math.abs(x - previous)
        val deltaN = Math.abs(next - x)
        if (deltaN - deltaP <= 0) {
            return insertAt
        }
        return insertAt - 1
    }

    private fun clampedGet(index: Int, caretPositions: List<Float>) = caretPositions[Math.min(caretPositions.size - 1, Math.max(0, index))]
}

fun integerTextInputKeyboardHandler(ui: UserInterface, caret: Caret, cursorShape: ShapeCursor, complete: () -> Unit = {}): KeyboardHandler {
    return KeyboardHandler(
            onChar = { codePoint ->
                if (caret.selection != 0) {
                    val p1 = caret.position
                    val p2 = p1 + caret.selection
                    val min = Math.min(p1, p2)
                    val max = Math.max(p1, p2)
                    caret.position = min
                    caret.selection = 0
                    caret.dynamicText.reference.value = caret.dynamicText.reference.value.removeRange(min, max)
                }
                if (caret.position < caret.dynamicText.sizeLimit && caret.dynamicText.reference.value.length < caret.dynamicText.sizeLimit && (codePoint in 0x30..0x39) || (caret.position == 0 && codePoint == 0x2d)) {
                    val currentString = ArrayList(caret.dynamicText.reference.value.toCharArray().toList())
                    currentString.add(caret.position++, codePoint.toChar())
                    caret.dynamicText.reference.value = String(currentString.toCharArray())
                    cursorShape.timeOffset = System.currentTimeMillis()
                }
            },
            onKey = { key, _, action, mods ->
                if (action == GLFW_REPEAT || action == GLFW_PRESS) {
                    val ctrl = GLFW_MOD_CONTROL and mods != 0
                    val shift = GLFW_MOD_SHIFT and mods != 0
                    when (key) {
                        GLFW_KEY_A -> {
                            if (ctrl) {
                                caret.position = 0
                                caret.selection = caret.dynamicText.reference.value.length
                            }
                        }
                        GLFW_KEY_BACKSPACE -> {
                            if (caret.selection != 0) {
                                val p1 = caret.position
                                val p2 = p1 + caret.selection
                                val min = Math.min(p1, p2)
                                val max = Math.max(p1, p2)
                                caret.position = min
                                caret.selection = 0
                                caret.dynamicText.reference.value = caret.dynamicText.reference.value.removeRange(min, max)
                                cursorShape.timeOffset = System.currentTimeMillis()
                            } else {
                                if (caret.position > 0) {
                                    if (shift) {
                                        caret.dynamicText.reference.value = caret.dynamicText.reference.value.substring(caret.position)
                                        caret.position = 0
                                        cursorShape.timeOffset = System.currentTimeMillis()
                                    } else {
                                        val newString = StringBuilder(caret.dynamicText.reference.value)
                                        newString.delete(caret.position - 1, caret.position)
                                        caret.position--
                                        caret.dynamicText.reference.value = newString.toString()
                                        cursorShape.timeOffset = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                        GLFW_KEY_DELETE -> {
                            if (caret.selection != 0) {
                                val p1 = caret.position
                                val p2 = p1 + caret.selection
                                val min = Math.min(p1, p2)
                                val max = Math.max(p1, p2)
                                caret.position = min
                                caret.selection = 0
                                caret.dynamicText.reference.value = caret.dynamicText.reference.value.removeRange(min, max)
                                cursorShape.timeOffset = System.currentTimeMillis()
                            } else {
                                if (caret.position < caret.dynamicText.reference.value.length) {
                                    if (shift) {
                                        caret.dynamicText.reference.value = caret.dynamicText.reference.value.substring(0, caret.position)
                                        cursorShape.timeOffset = System.currentTimeMillis()
                                    } else {
                                        val newString = StringBuilder(caret.dynamicText.reference.value)
                                        newString.delete(caret.position, caret.position + 1)
                                        caret.dynamicText.reference.value = newString.toString()
                                        cursorShape.timeOffset = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                        GLFW_KEY_LEFT -> {
                            if (shift) {
                                if (caret.position + caret.selection > 0) {
                                    caret.selection--
                                }
                            } else {
                                if (caret.selection != 0) {
                                    caret.position = Math.min(caret.position, caret.position + caret.selection)
                                    caret.selection = 0
                                }
                                if (caret.position > 0) {
                                    caret.position--
                                    cursorShape.timeOffset = System.currentTimeMillis()
                                }
                            }
                        }
                        GLFW_KEY_RIGHT -> {
                            if (shift) {
                                if (caret.position + caret.selection < caret.dynamicText.reference.value.length) {
                                    caret.selection++
                                }
                            } else {
                                if (caret.selection != 0) {
                                    caret.position = Math.max(caret.position, caret.position + caret.selection)
                                    caret.selection = 0
                                }
                                if (caret.position < caret.dynamicText.reference.value.length) {
                                    caret.position++
                                    cursorShape.timeOffset = System.currentTimeMillis()
                                }
                            }
                        }
                        GLFW_KEY_ENTER -> {
                            complete()
                        }
                        GLFW_KEY_C -> {
                            if (ctrl) {
                                if (caret.selection != 0) {
                                    val p1 = caret.position
                                    val p2 = p1 + caret.selection
                                    val min = Math.min(p1, p2)
                                    val max = Math.max(p1, p2)
                                    ui.setClipboardString(caret.dynamicText.reference.value.substring(min, max))
                                } else {
                                    ui.setClipboardString(caret.dynamicText.reference.value)
                                }
                            }
                        }
                        GLFW_KEY_X -> {
                            if (ctrl) {
                                if (caret.selection != 0) {
                                    val p1 = caret.position
                                    val p2 = p1 + caret.selection
                                    val min = Math.min(p1, p2)
                                    val max = Math.max(p1, p2)
                                    ui.setClipboardString(caret.dynamicText.reference.value.substring(min, max))
                                    caret.position = min
                                    caret.selection = 0
                                    caret.dynamicText.reference.value = caret.dynamicText.reference.value.removeRange(min, max)
                                } else {
                                    ui.setClipboardString(caret.dynamicText.reference.value)
                                    caret.dynamicText.reference.value = ""
                                }
                            }
                        }
                        GLFW_KEY_V -> {
                            if (ctrl) {
                                if (caret.selection != 0) {
                                    val p1 = caret.position
                                    val p2 = p1 + caret.selection
                                    val min = Math.min(p1, p2)
                                    val max = Math.max(p1, p2)
                                    caret.position = min
                                    caret.selection = 0
                                    caret.dynamicText.reference.value = caret.dynamicText.reference.value.removeRange(min, max)
                                }
                                val codePoints = ui.getClipboardString()?.codePoints()
                                if (codePoints != null) {
                                    for (codePoint in codePoints) {
                                        if (caret.position < caret.dynamicText.sizeLimit && caret.dynamicText.reference.value.length < caret.dynamicText.sizeLimit && (codePoint in 0x30..0x39) || (caret.position == 0 && codePoint == 0x2d)) {
                                            val currentString = ArrayList(caret.dynamicText.reference.value.toCharArray().toList())
                                            currentString.add(caret.position++, codePoint.toChar())
                                            caret.dynamicText.reference.value = String(currentString.toCharArray())
                                            cursorShape.timeOffset = System.currentTimeMillis()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            })
}