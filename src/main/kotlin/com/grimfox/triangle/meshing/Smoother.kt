package com.grimfox.triangle.meshing

import com.grimfox.triangle.Configuration
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.Predicates
import com.grimfox.triangle.TrianglePool
import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.geometry.Polygon
import com.grimfox.triangle.geometry.Vertex
import com.grimfox.triangle.geometry.Vertex.VertexType
import com.grimfox.triangle.voronoi.*
import java.util.*

class Smoother(
        internal var voronoiFactory: IVoronoiFactory = InternalVoronoiFactory(),
        internal var trianglePool: TrianglePool = TrianglePool(),
        internal var config: Configuration = Configuration(
                predicates = { Predicates.default },
                trianglePool = { trianglePool.restart() }),
        internal var constraintOptions: ConstraintOptions = ConstraintOptions()) {

    fun smooth(mesh: Mesh, limit: Int = 10) {
        val mesher = GenericMesher(config = config)
        val predicates = config.predicates.invoke()
        constraintOptions.boundarySplitMode = mesh.behavior.boundarySplitMode
        var newMesh: Mesh = Mesh(mesh)
        for (i in 1..limit) {
            step(newMesh, voronoiFactory, predicates)
            newMesh = mesher.triangulate(rebuild(newMesh), constraintOptions)
            voronoiFactory.reset()
        }
        newMesh.copyTo(mesh)
        mesh.cleanup()
    }

    private fun step(mesh: Mesh, factory: IVoronoiFactory, predicates: Predicates) {
        val voronoi = BoundedVoronoi(mesh, factory, predicates)
        for (face in voronoi.faces) {
            val generator = face.generator ?: continue
            if (generator.label == 0 && face.edge != null) {
                val point = centroid(face)
                generator.x = point.x
                generator.y = point.y
            }
        }
    }

    private fun centroid(face: DcelFace): Point {
        var ai: Double
        var aTemp: Double = 0.0
        var xTemp: Double = 0.0
        var yTemp: Double = 0.0
        var edge: DcelHalfEdge = face.edge!!
        val first = edge.next!!.id
        var p: Point
        var q: Point
        do {
            p = edge.origin!!
            q = edge.twin!!.origin!!
            ai = p.x * q.y - q.x * p.y
            aTemp += ai
            xTemp += (q.x + p.x) * ai
            yTemp += (q.y + p.y) * ai
            edge = edge.next!!
        } while (edge.next!!.id != first)
        return Point(xTemp / (3 * aTemp), yTemp / (3 * aTemp), 0, 0)
    }

    private fun rebuild(mesh: Mesh): Polygon {
        val data = Polygon(mesh._vertices.size, false)
        for (v in mesh._vertices.values) {
            v.type = VertexType.INPUT_VERTEX
            data.points.add(v)
        }
        data.segments.addAll(mesh._subsegs.values)
        data.holes.addAll(mesh._holes)
        data.regions.addAll(mesh.regions)
        return data
    }

    private class InternalVoronoiFactory : IVoronoiFactory {
        private var vertices: ObjectPool<DcelVertex> = ObjectPool()
        private var edges: ObjectPool<DcelHalfEdge> = ObjectPool()
        private var faces: ObjectPool<DcelFace> = ObjectPool()
        override fun initialize(vertexCount: Int, edgeCount: Int, faceCount: Int) {
            vertices.setCapacity(vertexCount)
            edges.setCapacity(edgeCount)
            faces.setCapacity(faceCount)
            for (i in vertices.count..vertexCount - 1) {
                vertices.put(DcelVertex(0.0, 0.0))
            }
            for (i in edges.count..edgeCount - 1) {
                edges.put(DcelHalfEdge(null))
            }
            for (i in faces.count..faceCount - 1) {
                faces.put(DcelFace(null))
            }
            reset()
        }

        override fun reset() {
            vertices.release()
            edges.release()
            faces.release()
        }

        override fun createVertex(x: Double, y: Double): DcelVertex {
            val vertex = vertices.tryGet()
            if (vertex != null) {
                vertex.x = x
                vertex.y = y
                vertex.leaving = null
                return vertex
            }
            val newVertex = DcelVertex(x, y)
            vertices.put(newVertex)
            return newVertex
        }

        override fun createHalfEdge(origin: DcelVertex, face: DcelFace): DcelHalfEdge {
            val edge = edges.tryGet()
            if (edge != null) {
                edge.origin = origin
                edge.face = face
                edge.next = null
                edge.twin = null
                if (face.edge == null) {
                    face.edge = edge
                }
                return edge
            }
            val newEdge = DcelHalfEdge(origin, face)
            edges.put(newEdge)
            return newEdge
        }

        override fun createFace(vertex: Vertex): DcelFace {
            val face = faces.tryGet()
            if (face != null) {
                face.id = vertex.id
                face.generator = vertex
                face.edge = null
                return face
            }
            val newFace = DcelFace(vertex)
            faces.put(newFace)
            return newFace
        }

        private class ObjectPool<T : Any>(capacity: Int = 3) {
            var index: Int = 0
            var count: Int = 0
            var pool: ArrayList<T> = ArrayList(capacity)
            fun tryGet(): T? {
                if (this.index < this.count) {
                    return this.pool[this.index++]
                }
                return null
            }

            fun put(obj: T) {
                this.pool.add(obj)
                this.count++
                this.index++
            }

            fun release() {
                this.index = 0
            }

            fun setCapacity(capacity: Int) {
                pool.ensureCapacity(capacity)
            }
        }
    }
}
