package com.grimfox.triangle.meshing

import com.grimfox.triangle.Configuration
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.*
import com.grimfox.triangle.meshing.algorithm.Dwyer
import com.grimfox.triangle.meshing.algorithm.TriangulationAlgorithm
import java.util.*

class GenericMesher(
        private val triangulator: TriangulationAlgorithm = Dwyer(),
        private val config: Configuration = Configuration()) {

    fun triangulate(points: List<Vertex>): Mesh {
        val mesh = triangulator.triangulate(points, config)
        mesh.cleanup()
        return mesh
    }

    fun triangulate(polygon: Polygon, quality: QualityOptions): Mesh {
        return triangulate(polygon, null, quality)
    }

    fun triangulate(polygon: Polygon, options: ConstraintOptions? = null, quality: QualityOptions? = null): Mesh {
        val mesh = triangulator.triangulate(polygon.points, config)
        val noExact = mesh.behavior.disableExactMath
        val constraintMesher = ConstraintMesher(mesh, config)
        val qualityMesher = QualityMesher(mesh, config)
        mesh.qualityMesher = qualityMesher
        constraintMesher.apply(polygon, options, noExact)
        qualityMesher.apply(quality, noExact)
        mesh.cleanup()
        return mesh
    }

    companion object {
        fun structuredMesh(width: Double, height: Double, nx: Int, ny: Int): Mesh {
            if (width <= 0.0) {
                throw IllegalArgumentException("width")
            }
            if (height <= 0.0) {
                throw IllegalArgumentException("height")
            }
            return structuredMesh(Rectangle(0.0, 0.0, width, height), nx, ny)
        }

        fun structuredMesh(bounds: Rectangle, nx: Int, ny: Int): Mesh {
            val polygon = Polygon((nx + 1) * (ny + 1), false)
            var x: Double
            var y: Double
            val dx = bounds.width / nx
            val dy = bounds.height / ny
            val left = bounds.left
            val bottom = bounds.bottom
            var j: Int
            var k: Int
            var l: Int
            val points = ArrayList<Vertex>((nx + 1) * (ny + 1))
            var i = 0
            while (i <= nx) {
                x = left + i * dx
                j = 0
                while (j <= ny) {
                    y = bottom + j * dy
                    points.add(Vertex(x, y))
                    j++
                }
                i++
            }
            polygon.points.addAll(points)
            for ((n, v) in points.withIndex()) {
                v.hash = n
                v.id = n
            }
            val segments = ArrayList(polygon.segments)
            segments.ensureCapacity(2 * (nx + ny))
            var a: Vertex
            var b: Vertex
            j = 0
            while (j < ny) {
                a = points[j]
                b = points[j + 1]
                segments.add(Segment(a, b, 1))
                a.label = 1
                b.label = 1
                a = points[nx * (ny + 1) + j]
                b = points[nx * (ny + 1) + (j + 1)]
                segments.add(Segment(a, b, 1))
                a.label = 1
                b.label = 1
                j++
            }
            i = 0
            while (i < nx) {
                a = points[(ny + 1) * i]
                b = points[(ny + 1) * (i + 1)]
                segments.add(Segment(a, b, 1))
                a.label = 1
                b.label = 1
                a = points[ny + (ny + 1) * i]
                b = points[ny + (ny + 1) * (i + 1)]
                segments.add(Segment(a, b, 1))
                a.label = 1
                b.label = 1
                i++
            }
            val triangles = ArrayList<InputTriangle>(2 * nx * ny)
            i = 0
            while (i < nx) {
                j = 0
                while (j < ny) {
                    k = j + (ny + 1) * i
                    l = j + (ny + 1) * (i + 1)
                    if ((i + j) % 2 == 0) {
                        triangles.add(InputTriangle(k, l, l + 1))
                        triangles.add(InputTriangle(k, l + 1, k + 1))
                    } else {
                        triangles.add(InputTriangle(k, l, k + 1))
                        triangles.add(InputTriangle(l, l + 1, k + 1))
                    }
                    j++
                }
                i++
            }
            return Converter.toMesh(polygon, triangles)
        }
    }
}
