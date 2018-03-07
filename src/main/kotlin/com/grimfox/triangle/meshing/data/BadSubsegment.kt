package com.grimfox.triangle.meshing.data

import com.grimfox.triangle.geometry.OSub
import com.grimfox.triangle.geometry.Vertex

class BadSubsegment {

    val subsegment = OSub()

    var org: Vertex? = null

    var dest: Vertex? = null

    override fun equals(other: Any?) = super.equals(other)

    override fun hashCode() = subsegment.segment!!.hash

    override fun toString(): String {
        return "BadSubsegment(subsegment=$subsegment, org=$org, dest=$dest)"
    }
}
