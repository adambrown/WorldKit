package com.grimfox.gec.ui

interface RenderableInstance {

    fun setInstanceValues(index: Int, positions: FloatArray)

    abstract class BaseRenderingInstance : RenderableInstance {

        internal fun fillVec4(offset: Int, array: FloatArray, f1: Float, f2: Float, f3: Float, f4: Float) {
            var vOffset = offset
            array[vOffset++] = f1
            array[vOffset++] = f2
            array[vOffset++] = f3
            array[vOffset] = f4
        }
    }
}