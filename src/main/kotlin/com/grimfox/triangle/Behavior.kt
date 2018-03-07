package com.grimfox.triangle

import com.grimfox.logging.LOG
import com.grimfox.triangle.geometry.ITriangle

class Behavior(quality: Boolean = false, minAngle: Double = 20.0) {

    enum class BoundarySplitMode {
        SPLIT,
        SPLIT_INTERNAL_ONLY,
        NO_SPLIT
    }

    var disableExactMath: Boolean = false

    var isPlanarStraightLineGraph = false

    private var _applyMeshQualityConstraints = false
    var applyMeshQualityConstraints: Boolean
        get() = _applyMeshQualityConstraints
        set(value) {
            _applyMeshQualityConstraints = value
            if (_applyMeshQualityConstraints) {
                update()
            }
        }

    var applyMaxTriangleAreaConstraints = false

    var encloseConvexHull = false

    var ignoreHolesInPolygons = false

    var makeConformingDelaunay = false

    var userDefinedTriangleConstraint: ((ITriangle, Double) -> Boolean)? = null

    var boundarySplitMode: BoundarySplitMode = BoundarySplitMode.SPLIT

    private var _minAngleConstraint = 0.0
    var minAngleConstraint: Double
        get() = _minAngleConstraint
        set(value) {
            _minAngleConstraint = value
            update()
        }

    private var _maxAngleConstraint = 0.0
    var maxAngleConstraint: Double
        get() = _maxAngleConstraint
        set(value) {
            _maxAngleConstraint = value
            update()
        }

    private var _maxAreaConstraint = -1.0
    var maxAreaConstraint: Double
        get() = _maxAreaConstraint
        set(value) {
            _maxAreaConstraint = value
            fixedArea = value > 0.0
        }

    var fixedArea = false

    var useSegments = true

    var useRegions = false

    var goodAngle = 0.0

    var maxGoodAngle = 0.0

    var offConstant = 0.0

    init {
        if (quality) {
            minAngleConstraint = minAngle
        }
    }

    private fun update() {
        _applyMeshQualityConstraints = true
        if (_minAngleConstraint < 0 || _minAngleConstraint > 60) {
            _minAngleConstraint = 0.0
            _applyMeshQualityConstraints = false
            LOG.warn("Invalid quality option (minimum angle).")
        }
        if (_maxAngleConstraint != 0.0 && (_maxAngleConstraint < 60 || _maxAngleConstraint > 180)) {
            _maxAngleConstraint = 0.0
            _applyMeshQualityConstraints = false
            LOG.warn("Invalid quality option (maximum angle).")
        }
        useSegments = isPlanarStraightLineGraph || _applyMeshQualityConstraints || encloseConvexHull
        goodAngle = Math.cos(_minAngleConstraint * Math.PI / 180.0)
        maxGoodAngle = Math.cos(_maxAngleConstraint * Math.PI / 180.0)
        if (goodAngle == 1.0) {
            offConstant = 0.0
        } else {
            offConstant = 0.475 * Math.sqrt((1.0 + goodAngle) / (1.0 - goodAngle))
        }
        goodAngle *= goodAngle
    }
}
