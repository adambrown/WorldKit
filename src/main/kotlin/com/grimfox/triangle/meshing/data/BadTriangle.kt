package com.grimfox.triangle.meshing.data

import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.Vertex

class BadTriangle {

    val poortri = OTri()

    var key: Double = 0.0

    var org: Vertex? = null

    var dest: Vertex? = null

    var apex: Vertex? = null

    var next: BadTriangle? = null

    override fun toString(): String {
        return "BadTriangle(poortri=$poortri, key=$key, org=$org, dest=$dest, apex=$apex, next=$next)"
    }
}
