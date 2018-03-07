package com.grimfox.triangle.geometry

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.meshing.ConstraintOptions
import com.grimfox.triangle.meshing.GenericMesher
import com.grimfox.triangle.meshing.QualityOptions
import com.grimfox.triangle.meshing.algorithm.Dwyer
import com.grimfox.triangle.meshing.algorithm.TriangulationAlgorithm
import java.util.*

class Polygon(initialCapacity: Int = 3, hasMarkers: Boolean = false) {

    var points: MutableList<Vertex> = ArrayList(initialCapacity)

    var holes: MutableList<Point> = ArrayList()

    var segments: MutableList<ISegment> = ArrayList()

    var regions: MutableList<RegionPointer> = ArrayList()

    var hasPointMarkers: Boolean = false

    var hasSegmentMarkers: Boolean = false

    val size: Int
        get() = points.size

    init {
        hasPointMarkers = hasMarkers
        hasSegmentMarkers = hasMarkers
    }

    fun addContour(points: List<Vertex>, marker: Int, hole: Boolean = false, convex: Boolean = false, noExact: Boolean = false) {
        add(Contour(points, marker, convex), hole, noExact)
    }

    fun addContour(points: List<Vertex>, marker: Int, hole: Point) {
        add(Contour(points, marker, false), hole)
    }

    fun bounds(): Rectangle {
        val bounds = Rectangle()
        bounds.expand(points)
        return bounds
    }

    fun add(vertex: Vertex) {
        points.add(vertex)
    }

    fun add(segment: ISegment, insert: Boolean = false) {
        segments.add(segment)
        if (insert) {
            points.add(segment.getVertex(0)!!)
            points.add(segment.getVertex(1)!!)
        }
    }

    fun add(segment: ISegment, index: Int) {
        segments.add(segment)
        points.add(segment.getVertex(index)!!)
    }

    fun add(contour: Contour, hole: Boolean, noExact: Boolean) {
        if (hole) {
            add(contour, contour.findInteriorPoint(noExact))
        } else {
            points.addAll(contour.points)
            segments.addAll(contour.segments)
        }
    }

    fun add(contour: Contour, hole: Point) {
        points.addAll(contour.points)
        segments.addAll(contour.segments)
        holes.add(hole)
    }

    fun triangulate(options: ConstraintOptions? = null, quality: QualityOptions? = null, triangulator: TriangulationAlgorithm = Dwyer()): Mesh {
        return GenericMesher(triangulator).triangulate(this, options, quality)
    }
}
