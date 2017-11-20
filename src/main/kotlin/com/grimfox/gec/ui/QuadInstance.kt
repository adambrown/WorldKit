package com.grimfox.gec.ui

import com.grimfox.gec.ui.RenderableInstance.BaseRenderingInstance

data class QuadInstance(var x: Float, var y: Float, val width: Float, val height: Float) : BaseRenderingInstance() {

    override fun setInstanceValues(index: Int, positions: FloatArray) {
        val i4 = index * 4
        fillVec4(i4, positions, x, y, width, height)
    }
}