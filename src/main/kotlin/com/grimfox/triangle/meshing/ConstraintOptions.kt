package com.grimfox.triangle.meshing

import com.grimfox.triangle.Behavior.BoundarySplitMode

class ConstraintOptions {

    var makeConformingDelaunay: Boolean = false

    var encloseConvexHull: Boolean = false

    var boundarySplitMode: BoundarySplitMode = BoundarySplitMode.SPLIT
}
