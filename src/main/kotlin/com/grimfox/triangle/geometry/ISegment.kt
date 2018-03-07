package com.grimfox.triangle.geometry

interface ISegment : IEdge {

    fun getVertex(index: Int): Vertex?

    fun getTriangle(index: Int): ITriangle?
}
