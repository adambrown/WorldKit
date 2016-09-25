package com.grimfox.gec.model

import java.util.*

class Range2F(val min: Float, val max: Float) {

    val delta = max - min

    fun nextValue(random: Random): Float {
        return valueAt(random.nextFloat())
    }

    fun valueAt(f: Float): Float {
        return min + (f * delta)
    }
}