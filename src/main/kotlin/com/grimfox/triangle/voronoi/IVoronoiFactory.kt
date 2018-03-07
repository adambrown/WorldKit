package com.grimfox.triangle.voronoi

import com.grimfox.triangle.geometry.Vertex

interface IVoronoiFactory {

    fun initialize(vertexCount: Int, edgeCount: Int, faceCount: Int)

    fun reset()

    fun createVertex(x: Double, y: Double): DcelVertex

    fun createHalfEdge(origin: DcelVertex, face: DcelFace): DcelHalfEdge

    fun createFace(vertex: Vertex): DcelFace
}
