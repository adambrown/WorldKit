package com.grimfox.triangle.meshing.algorithm

import com.grimfox.triangle.Configuration
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.Vertex

interface TriangulationAlgorithm {

    fun triangulate(points: List<Vertex>, config: Configuration): Mesh
}
