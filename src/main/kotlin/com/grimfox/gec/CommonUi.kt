package com.grimfox.gec

import com.grimfox.gec.ui.widgets.MeshViewport3D
import com.grimfox.gec.util.clamp
import com.grimfox.gec.util.mRef
import com.grimfox.gec.util.ref
import java.util.*

val random = Random()
val DEFAULT_HEIGHT_SCALE = 50.0f
val MAX_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 10
val MIN_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 0
val HEIGHT_SCALE_CONST = DEFAULT_HEIGHT_SCALE * 0.968746f
val HEIGHT_SCALE_MULTIPLIER = DEFAULT_HEIGHT_SCALE * 32.00223
val heightScaleFunction = { scale: Float ->
    Math.min(MAX_HEIGHT_SCALE, Math.max(MIN_HEIGHT_SCALE, if (scale <= 0.5f) {
        scale * (2 * DEFAULT_HEIGHT_SCALE)
    } else {
        (HEIGHT_SCALE_CONST + (HEIGHT_SCALE_MULTIPLIER * Math.pow(scale - 0.46874918, 2.0))).toFloat()
    }))
}
val heightScaleFunctionInverse = { value: Float ->
    Math.min(1.0f, Math.max(0.0f, if (value <= DEFAULT_HEIGHT_SCALE) {
        value / (2 * DEFAULT_HEIGHT_SCALE)
    } else {
        (Math.sqrt((value - HEIGHT_SCALE_CONST) / HEIGHT_SCALE_MULTIPLIER) + 0.46874918).toFloat()
    }))
}
val heightMapScaleFactor = ref(DEFAULT_HEIGHT_SCALE)
val waterPlaneOn = ref(true)
val perspectiveOn = ref(true)
val rotateAroundCamera = ref(false)
val resetView = mRef(false)
val imageMode = ref(2)
val meshViewport = MeshViewport3D(resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor, imageMode)

fun linearClampedScaleFunction(range: IntRange): (Float) -> Int {
    return { scale: Float ->
        clamp(Math.round(scale * (range.last - range.first)) + range.first, range.first, range.last)
    }
}

fun linearClampedScaleFunctionInverse(range: IntRange): (Int) -> Float {
    return { value: Int ->
        clamp(Math.abs((value - range.first).toFloat() / (range.last - range.first)), 0.0f, 1.0f)
    }
}

fun linearClampedScaleFunction(min: Float, max: Float): (Float) -> Float {
    return { scale: Float ->
        clamp((scale * (max - min)) + min, min, max)
    }
}

fun linearClampedScaleFunctionInverse(min: Float, max: Float): (Float) -> Float {
    return { value: Float ->
        clamp(Math.abs((value - min) / (max - min)), 0.0f, 1.0f)
    }
}
