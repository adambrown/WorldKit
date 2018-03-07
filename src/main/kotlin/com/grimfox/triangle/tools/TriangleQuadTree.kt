package com.grimfox.triangle.tools

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.geometry.Rectangle
import com.grimfox.triangle.geometry.Triangle
import java.util.*

class TriangleQuadTree(
        mesh: Mesh,
        var maxDepth: Int,
        var sizeBound: Int) {

    companion object {

        fun isPointInTriangle(p: Point, t0: Point, t1: Point, t2: Point): Boolean {
            val d0 = Point(t1.x - t0.x, t1.y - t0.y, 0, 0)
            val d1 = Point(t2.x - t0.x, t2.y - t0.y, 0, 0)
            val d2 = Point(p.x - t0.x, p.y - t0.y, 0, 0)
            val c0 = Point(-d0.y, d0.x, 0, 0)
            val c1 = Point(-d1.y, d1.x, 0, 0)
            val s = dotProduct(d2, c1) / dotProduct(d0, c1)
            val v = dotProduct(d2, c0) / dotProduct(d1, c0)
            if (s >= 0 && v >= 0 && s + v <= 1) {
                return true
            }
            return false
        }

        private fun dotProduct(p: Point, q: Point): Double {
            return p.x * q.x + p.y * q.y
        }
    }

    private var root: QuadNode
    private var triangles: List<Triangle> = ArrayList(mesh._triangles)

    init {
        root = QuadNode(mesh._bounds, this, true)
        root.createSubRegion(-1)
    }

    fun query(x: Double, y: Double): Triangle? {
        val point = Point(x, y, 0, 0)
        val indices = root.findTriangles(point)
        return indices
                .map { triangles[it] }
                .firstOrNull { isPointInTriangle(point, it.getVertex(0)!!, it.getVertex(1)!!, it.getVertex(2)!!) }
    }

    internal class QuadNode(box: Rectangle, var tree: TriangleQuadTree, init: Boolean = false) {
        var bounds: Rectangle = Rectangle(box.left, box.bottom, box.width, box.height)
        var pivot: Point = Point((box.left + box.right) / 2, (box.bottom + box.top) / 2, 0, 0)
        var regions: Array<QuadNode?>
        var triangles: ArrayList<Int>
        var bitRegions: Int = 0

        init {
            this.bitRegions = 0
            this.regions = arrayOfNulls<QuadNode>(4)
            this.triangles = ArrayList<Int>()
            if (init) {
                val count = tree.triangles.size
                for (i in 0..count - 1) {
                    triangles.add(i)
                }
            }
        }

        fun findTriangles(searchPoint: Point): ArrayList<Int> {
            val region = findRegion(searchPoint)
            if (regions[region] == null) {
                return triangles
            }
            return regions[region]!!.findTriangles(searchPoint)
        }

        fun createSubRegion(currentDepth: Int) {
            var box: Rectangle
            val width = bounds.right - pivot.x
            val height = bounds.top - pivot.y
            box = Rectangle(bounds.left, bounds.bottom, width, height)
            regions[0] = QuadNode(box, tree)
            box = Rectangle(pivot.x, bounds.bottom, width, height)
            regions[1] = QuadNode(box, tree)
            box = Rectangle(bounds.left, pivot.y, width, height)
            regions[2] = QuadNode(box, tree)
            box = Rectangle(pivot.x, pivot.y, width, height)
            regions[3] = QuadNode(box, tree)
            val triangle = Array(3) { Point() }
            for (index in triangles) {
                val tri = tree.triangles[index]
                triangle[0] = tri.getVertex(0)!!
                triangle[1] = tri.getVertex(1)!!
                triangle[2] = tri.getVertex(2)!!
                addTriangleToRegion(triangle, index)
            }
            (0..3).filter { regions[it]!!.triangles.size > tree.sizeBound && currentDepth < tree.maxDepth }
                    .forEach { regions[it]!!.createSubRegion(currentDepth + 1) }
        }

        fun addTriangleToRegion(triangle: Array<Point>, index: Int) {
            bitRegions = 0
            if (isPointInTriangle(pivot, triangle[0], triangle[1], triangle[2])) {
                addToRegion(index, SW)
                addToRegion(index, SE)
                addToRegion(index, NW)
                addToRegion(index, NE)
                return
            }
            findTriangleIntersections(triangle, index)
            if (bitRegions == 0) {
                val region = findRegion(triangle[0])
                regions[region]!!.triangles.add(index)
            }
        }

        fun findTriangleIntersections(triangle: Array<Point>, index: Int) {
            var k = 2
            var dx: Double
            var dy: Double
            var i = 0
            while (i < 3) {
                dx = triangle[i].x - triangle[k].x
                dy = triangle[i].y - triangle[k].y
                if (dx != 0.0) {
                    findIntersectionsWithX(dx, dy, triangle, index, k)
                }
                if (dy != 0.0) {
                    findIntersectionsWithY(dx, dy, triangle, index, k)
                }
                k = i++
            }
        }

        fun findIntersectionsWithX(dx: Double, dy: Double, triangle: Array<Point>, index: Int, k: Int) {
            var t = (pivot.x - triangle[k].x) / dx
            if (t < 1 + EPS && t > -EPS) {
                val yComponent = triangle[k].y + t * dy
                if (yComponent < pivot.y && yComponent >= bounds.bottom) {
                    addToRegion(index, SW)
                    addToRegion(index, SE)
                } else if (yComponent <= bounds.top) {
                    addToRegion(index, NW)
                    addToRegion(index, NE)
                }
            }
            t = (bounds.left - triangle[k].x) / dx
            if (t < 1 + EPS && t > -EPS) {
                val yComponent = triangle[k].y + t * dy
                if (yComponent < pivot.y && yComponent >= bounds.bottom) {
                    addToRegion(index, SW)
                } else if (yComponent <= bounds.top) {
                    addToRegion(index, NW)
                }
            }
            t = (bounds.right - triangle[k].x) / dx
            if (t < 1 + EPS && t > -EPS) {
                val yComponent = triangle[k].y + t * dy
                if (yComponent < pivot.y && yComponent >= bounds.bottom) {
                    addToRegion(index, SE)
                } else if (yComponent <= bounds.top) {
                    addToRegion(index, NE)
                }
            }
        }

        fun findIntersectionsWithY(dx: Double, dy: Double, triangle: Array<Point>, index: Int, k: Int) {
            var xComponent: Double
            var t = (pivot.y - triangle[k].y) / dy
            if (t < 1 + EPS && t > -EPS) {
                xComponent = triangle[k].x + t * dx
                if (xComponent > pivot.x && xComponent <= bounds.right) {
                    addToRegion(index, SE)
                    addToRegion(index, NE)
                } else if (xComponent >= bounds.left) {
                    addToRegion(index, SW)
                    addToRegion(index, NW)
                }
            }
            t = (bounds.bottom - triangle[k].y) / dy
            if (t < 1 + EPS && t > -EPS) {
                xComponent = triangle[k].x + t * dx
                if (xComponent > pivot.x && xComponent <= bounds.right) {
                    addToRegion(index, SE)
                } else if (xComponent >= bounds.left) {
                    addToRegion(index, SW)
                }
            }
            t = (bounds.top - triangle[k].y) / dy
            if (t < 1 + EPS && t > -EPS) {
                xComponent = triangle[k].x + t * dx
                if (xComponent > pivot.x && xComponent <= bounds.right) {
                    addToRegion(index, NE)
                } else if (xComponent >= bounds.left) {
                    addToRegion(index, NW)
                }
            }
        }

        @Throws(Exception::class)
        fun findRegion(point: Point): Int {
            var b = 2
            if (point.y < pivot.y) {
                b = 0
            }
            if (point.x > pivot.x) {
                b++
            }
            return b
        }

        fun addToRegion(index: Int, region: Int) {
            if (bitRegions and BIT_VECTOR[region] == 0) {
                regions[region]!!.triangles.add(index)
                bitRegions = bitRegions or BIT_VECTOR[region]
            }
        }

        companion object {
            val SW = 0
            val SE = 1
            val NW = 2
            val NE = 3
            val EPS = 1e-6
            val BIT_VECTOR = intArrayOf(0x1, 0x2, 0x4, 0x8)
        }
    }
}
