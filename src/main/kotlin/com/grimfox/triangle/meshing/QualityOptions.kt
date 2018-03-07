package com.grimfox.triangle.meshing

import com.grimfox.triangle.geometry.ITriangle

class QualityOptions {

    var maximumAngle: Double = 0.0

    var minimumAngle: Double = 0.0

    var maximumArea: Double = 0.0

    var userTest: ((ITriangle, Double) -> Boolean)? = null

    var constrainArea: Boolean = false

    var steinerPoints: Int = 0
}
