package com.grimfox.triangle.voronoi

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.Predicates
import com.grimfox.triangle.geometry.Rectangle
import com.grimfox.triangle.tools.IntersectionHelper

class StandardVoronoi(mesh: Mesh, box: Rectangle = Rectangle(mesh._bounds), factory: IVoronoiFactory = DefaultVoronoiFactory(), predicates: Predicates = mesh.predicates) : VoronoiBase(mesh, factory, predicates) {

    init {
        box.expand(mesh._bounds)
        postProcess(box)
    }

    private fun postProcess(box: Rectangle) {
        for (edge in rays) {
            val v1 = edge.origin!!
            val v2 = edge.twin!!.origin!!
            if (box.contains(v1) || box.contains(v2)) {
                IntersectionHelper.boxRayIntersection(box, v1, v2, v2)
            }
        }
    }
}
