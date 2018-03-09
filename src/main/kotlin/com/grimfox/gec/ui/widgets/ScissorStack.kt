package com.grimfox.gec.ui.widgets

import com.grimfox.gec.ui.nvgproxy.*
import com.grimfox.joml.Vector4f
import java.util.*

class ScissorStack(val nvg: Long) {

    private val stack = ArrayList<Vector4f>()

    fun push(clip: Vector4f, canOverflow: Boolean = false): Vector4f {
        if (stack.isNotEmpty() && !canOverflow) {
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
        apply(clip)
        stack.add(clip)
        return clip
    }

    fun apply(clip: Vector4f) {
        nvgScissor(nvg, clip.x, clip.y, clip.z - clip.x, clip.w - clip.y)
    }

    fun peek(): Vector4f? {
        if (stack.isNotEmpty()) {
            return stack.last()
        }
        return null
    }

    fun suspendIf(condition: Boolean, parents: Int, block: () -> Unit) {
        if (parents < 1) {
            suspendIf(condition, block)
        } else {
            if (condition) {
                suspendWhile(parents, block)
            } else {
                block()
            }
        }
    }

    fun suspendIf(condition: Boolean, block: () -> Unit) {
        if (condition) {
            suspendWhile(block)
        } else {
            block()
        }
    }

    fun suspendWhile(parents: Int, block: () -> Unit) {
        val suspended = ArrayList<Vector4f>()
        for (i in 1..parents) {
            if (stack.isNotEmpty()) {
                suspended.add(stack.removeAt(stack.size - 1))
            }
        }
        if (stack.isEmpty()) {
            suspend()
        } else {
            resume()
        }
        block()
        suspended.reverse()
        suspended.forEach {
            stack.add(it)
        }
        if (stack.isEmpty()) {
            suspend()
        } else {
            resume()
        }
    }

    inline fun suspendWhile(block: () -> Unit) {
        suspend()
        block()
        resume()
    }

    fun suspend() {
        nvgResetScissor(nvg)
    }

    fun resume() {
        if (!stack.isEmpty()) {
            val current = stack.last()
            nvgScissor(nvg, current.x, current.y, current.z - current.x, current.w - current.y)
        }
    }

    fun pop(): Vector4f? {
        if (stack.isEmpty()) {
            return null
        }
        val top = stack.removeAt(stack.size - 1)
        if (stack.isEmpty()) {
            suspend()
        } else {
            resume()
        }
        return top
    }
}