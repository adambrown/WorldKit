package com.grimfox.gec.ui.widgets

import com.grimfox.gec.util.ref
import java.nio.ByteBuffer

class StaticTextReference(private var text: Text = NO_TEXT) : Text {

    override var style: TextStyle
        get() = text.style
        set(value) {
            text.style = value
        }

    override val data: ByteBuffer
        get() = text.data

    override val length: Int
        get() = text.length

    override fun draw(nvg: Long, block: Block, scale: Float) = text.draw(nvg, block, scale)

    override fun width(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Float = text.width(nvg, scale, scaleChanged, runId)

    override fun height(scale: Float): Float = text.height(scale)

    override fun dimensions(nvg: Long, scale: Float, scaleChanged: Boolean, runId: Long): Pair<Float, Float> = text.dimensions(nvg, scale, scaleChanged, runId)

    val reference = ref(text).addListener { _, new ->
        text = new
    }
}
