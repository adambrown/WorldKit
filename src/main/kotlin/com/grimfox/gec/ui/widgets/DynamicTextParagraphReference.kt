package com.grimfox.gec.ui.widgets

import com.grimfox.gec.util.ref
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

class DynamicTextParagraphReference(initialString: String, private val sizeLimit: Int, verticalSpace: Float, textStyle: TextStyle) {

    private val buffer = ByteBuffer.allocateDirect(sizeLimit + 1)
    val reference = ref("").addListener { _, new ->
        val newValue = if (new.length <= sizeLimit) {
            new
        } else {
            new.substring(0, sizeLimit)
        }
        val byteCount = MemoryUtil.memUTF8(newValue, false, buffer, 0)
        buffer.limit(byteCount)
    }
    val text = DynamicTextParagraphUtf8(buffer, verticalSpace, textStyle)
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