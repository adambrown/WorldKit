package com.grimfox.triangle.voronoi

import com.grimfox.triangle.geometry.Vertex

class DefaultVoronoiFactory : IVoronoiFactory {

    override fun initialize(vertexCount: Int, edgeCount: Int, faceCount: Int) {}

    override fun reset() {}

    override fun createVertex(x: Double, y: Double) = DcelVertex(x, y)

    override fun createHalfEdge(origin: DcelVertex, face: DcelFace) = DcelHalfEdge(origin, face)

    override fun createFace(vertex: Vertex) = DcelFace(vertex)
}
