package com.grimfox.gec.util

import com.grimfox.gec.model.geometry.*

object Rivers {

    enum class NodeType {
        CONTINUATION,
        SYMMETRIC,
        ASYMMETRIC
    }

    class RiverNode(var type: NodeType, var point: Point2F, var priority: Int, var riverPriority: Int, var elevation: Float, var maxTerrainSlope: Float, var region: Int)

}
