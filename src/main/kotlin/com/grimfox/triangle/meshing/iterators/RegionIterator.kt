package com.grimfox.triangle.meshing.iterators

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.OSub
import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.SubSegment
import com.grimfox.triangle.geometry.Triangle
import java.util.*

class RegionIterator {

    private val region: ArrayList<Triangle> = ArrayList()

    fun process(triangle: Triangle, boundary: Int = 0) {
        process(triangle, { tri ->
            tri.label = triangle.label
            tri.area = triangle.area
        }, boundary)
    }

    fun process(triangle: Triangle, action: (Triangle) -> Unit, boundary: Int = 0) {
        if (triangle.id == Mesh.DUMMY || OTri.isDead(triangle)) {
            return
        }
        region.add(triangle)
        triangle.infected = true
        if (boundary == 0) {
            processRegion(action, { seg -> seg.hash == Mesh.DUMMY })
        } else {
            processRegion(action, { seg -> seg.label != boundary })
        }
        region.clear()
    }

    private fun processRegion(action: (Triangle) -> Unit, protector: (SubSegment) -> Boolean) {
        val testTri = OTri()
        val neighbor = OTri()
        val neighborSubsegment = OSub()
        for (i in region.indices) {
            testTri.triangle = region[i]
            action.invoke(testTri.triangle!!)
            testTri.orient = 0
            while (testTri.orient < 3) {
                testTri.sym(neighbor)
                testTri.pivot(neighborSubsegment)
                if (neighbor.triangle!!.id != Mesh.DUMMY && !neighbor.isInfected && protector.invoke(neighborSubsegment.segment!!)) {
                    neighbor.infect()
                    region.add(neighbor.triangle!!)
                }
                testTri.orient++
            }
        }
        for (virus in region) {
            virus.infected = false
        }
        region.clear()
    }
}
