package com.grimfox.gec.ui.widgets

import com.grimfox.gec.util.ref
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

class DynamicTextReference(initialString: String, private val sizeLimit: Int, textStyle: TextStyle) {

    private val buffer = ByteBuffer.allocateDirect(sizeLimit + 1)
    val reference = ref("").listener { old, new ->
        val newValue = if (new.length <= sizeLimit) {
            new
        } else {
            new.substring(0, sizeLimit)
        }
        val byteCount = MemoryUtil.memUTF8(newValue, true, buffer, 0)
        buffer.limit(byteCount + 1)
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