package com.grimfox.gec.util

import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Point
import java.util.*

object Triangulate {

    private data class Circle(var c: Point = Point(0.0f, 0.0f), var r: Float = -1.0f)
    
    private data class PointWrapper(var p: Point = Point(0.0f, 0.0f), var e: Point = Point(0.0f, 0.0f), var d2: Float = 0.0f, var id: Int = 0, var tId: Int = 0)

    private data class Triangle(var a: Int = 0, var b: Int = 0, var c: Int = 0, var ab: Int = -1, var bc: Int = -1, var ac: Int = -1, var cc: Circle = Circle()) {

        fun copy(other: Triangle): Triangle {
            a = other.a
            b = other.b
            c = other.c

            ab = other.ab
            bc = other.bc
            ac = other.ac

            cc.r = other.cc.r
            cc.c = other.cc.c

            return this
        }
    }

    private class VisibilityTest(p1: Point, p2: Point, var dx: Float = p1.x - p2.x, var dy: Float = p1.y - p2.y) {

        constructor(p1: PointWrapper, p2: PointWrapper) : this(p1.p, p2.p)

        fun isVisible(point: Point): Boolean {
            return -dy * point.x + dx * point.y < 0
        }
    }

    fun buildGraph(stride: Int, width: Float, points: List<Point>): Graph {
        val wrappedPoints = wrapPoints(points)

        val p1 = chooseSeedPoint(wrappedPoints, stride)
        val p2 = findClosestPointToSeed(wrappedPoints, p1)
        val p3 = findPointWithSmallestCircle(p1, p2, wrappedPoints)

        val midCircle = findAndSortByCenter(wrappedPoints, p1, p2, p3)

        val pointIndex = buildPointIndex(wrappedPoints)

        val triangles = ArrayList<Triangle>()

        val hull = buildInitialHull(triangles, p1, p2, p3, midCircle)

        buildTriangles(wrappedPoints, triangles, hull)

        var ids = flipTriangles(wrappedPoints, pointIndex, triangles)

        var iteration = 0
        while (ids.size > 0 && iteration < 50) {
            ids = flipTriangles(wrappedPoints, pointIndex, triangles, ids)
            iteration++
        }

        ids = flipEdges(wrappedPoints, pointIndex, triangles)

        iteration = 0
        while(ids.size > 0 && iteration < 100){
            ids = flipTriangles(wrappedPoints, pointIndex, triangles, ids)
            iteration++
        }

        return buildGraph(stride, wrappedPoints, pointIndex, triangles, width)
    }

    private fun wrapPoints(points: List<Point>): ArrayList<PointWrapper> {
        val wrappedPoints = ArrayList<PointWrapper>(points.size)
        points.forEachIndexed { id, point ->
            wrappedPoints.add(PointWrapper(point, id = id))
        }
        return wrappedPoints
    }

    private fun buildGraph(stride: Int, points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>, width: Float): Graph {
        val vertexIdsToPoints = FloatArray(points.size * 2)
        pointIndex.forEachIndexed { i, pointId ->
            val point = points[pointId]
            val o = i * 2
            vertexIdsToPoints[o] = point.p.x / width
            vertexIdsToPoints[o + 1] = point.p.y / width
        }
        val triangleToCenters = FloatArray(triangles.size * 2)
        val triangleToTriangles = IntArray(triangles.size * 3)
        val triangleToVertices = IntArray(triangles.size * 3)
        val vertexToTrianglesTemp = ArrayList<ArrayList<Int>>(points.size)
        for (i in 0..points.size - 1) {
            vertexToTrianglesTemp.add(ArrayList(5))
        }
        triangles.forEachIndexed { i, triangle ->
            val vertex = findCircleCenter(points[pointIndex[triangle.a]], points[pointIndex[triangle.b]], points[pointIndex[triangle.c]])
            var o = i * 2
            triangleToCenters[o++] = vertex.x / width
            triangleToCenters[o] = vertex.y / width
            o = i * 3
            triangleToTriangles[o++] = triangle.ab
            triangleToTriangles[o++] = triangle.bc
            triangleToTriangles[o] = triangle.ac
            o = i * 3
            triangleToVertices[o++] = triangle.a
            triangleToVertices[o++] = triangle.b
            triangleToVertices[o] = triangle.c
            vertexToTrianglesTemp[triangle.a].add(i)
            vertexToTrianglesTemp[triangle.b].add(i)
            vertexToTrianglesTemp[triangle.c].add(i)
        }
        val vertexToTriangles = ArrayList<List<Int>>(points.size)
        val vertexToVertices = ArrayList<List<Int>>(points.size)
        for (i in 0..points.size - 1) {
            val trianglesToOrder = vertexToTrianglesTemp[i].filter { it >= 0 }
            val hullEdges = ArrayList<Triple<Int, Int, Int>>(trianglesToOrder.size)
            trianglesToOrder.forEach {
                val triangle = triangles[it]
                if (triangle.a == i) {
                    hullEdges.add(Triple(it, triangle.b, triangle.c))
                } else if (triangle.b == i) {
                    hullEdges.add(Triple(it, triangle.c, triangle.a))
                } else {
                    hullEdges.add(Triple(it, triangle.a, triangle.b))
                }
            }
            val newHullEdges = ArrayList<Triple<Int, Int, Int>>(trianglesToOrder.size)
            newHullEdges.add(hullEdges.removeAt(0))
            for (j in 0..hullEdges.size - 1) {
                for (k in 0..hullEdges.size - 1) {
                    val nextEdge = hullEdges[k]
                    if (newHullEdges.last().third == nextEdge.second) {
                        newHullEdges.add(hullEdges.removeAt(k))
                        break
                    } else if (newHullEdges.last().third == nextEdge.third) {
                        newHullEdges.add(hullEdges.removeAt(k).copy(second = nextEdge.third, third = nextEdge.second))
                        break
                    } else if (newHullEdges.first().second == nextEdge.second) {
                        newHullEdges.add(0, hullEdges.removeAt(k).copy(second = nextEdge.third, third = nextEdge.second))
                        break
                    } else if (newHullEdges.first().second == nextEdge.third) {
                        newHullEdges.add(0, hullEdges.removeAt(k))
                        break
                    }
                }
            }
            val adjacentTriangles = newHullEdges.map { it.first }.toMutableList()
            val adjacentPoints = newHullEdges.map { it.second }.toMutableList()
            val possibleExtraPoint = newHullEdges.last().third
            if (adjacentPoints.first() != possibleExtraPoint) {
                adjacentPoints.add(possibleExtraPoint)
            }
            val a = adjacentPoints[0]
            val b = adjacentPoints[1]
            val center = i
            val pa = points[pointIndex[a]]
            val pb = points[pointIndex[b]]
            val pc = points[pointIndex[center]]
            if (areCounterClockwise(pa, pb, pc)) {
                adjacentTriangles.reverse()
                adjacentPoints.reverse()
            }
            vertexToTriangles.add(adjacentTriangles)
            vertexToVertices.add(adjacentPoints)
        }
        return Graph(stride, vertexIdsToPoints, vertexToVertices, vertexToTriangles, triangleToCenters, triangleToVertices, triangleToTriangles)
    }

    private fun buildInitialHull(triangles: ArrayList<Triangle>, p1: PointWrapper, p2: PointWrapper, p3: PointWrapper, midCircle: Circle): ArrayList<PointWrapper> {
        val hull = ArrayList<PointWrapper>()
        val rightHanded = isRightHanded(p1, p2, p3)
        if (rightHanded) {
            buildInitialTriangleAndAddToHull(hull, triangles, p1, p2, p3, midCircle)
        } else {
            buildInitialTriangleAndAddToHull(hull, triangles, p1, p3, p2, midCircle)
        }
        return hull
    }

    private fun chooseSeedPoint(points: ArrayList<PointWrapper>, stride: Int): PointWrapper {
        val halfStride = stride / 2
        val seedIndex = (halfStride * stride) + halfStride
        return points[seedIndex]
    }

    private fun findClosestPointToSeed(points: ArrayList<PointWrapper>, seed: PointWrapper): PointWrapper {
        val seedPoint = seed.p
        points.forEach { it.d2 = seedPoint.distanceSquaredTo(it.p) }
        seed.d2 = 0.0f
        points.sort()
        return points[1]
    }

    private fun findPointWithSmallestCircle(p1: PointWrapper, p2: PointWrapper, points: ArrayList<PointWrapper>): PointWrapper {
        var midId = -1
        var midCircle = Circle(r = Float.MAX_VALUE)
        for (i in 2..points.size - 1) {
            val circle = buildCircle(p1, p2, points[i])
            if (circle.r > 0.0f && circle.r < midCircle.r) {
                midId = i
                midCircle = circle
            } else if (midCircle.r != Float.MAX_VALUE && midCircle.r * 4 < points[i].d2) {
                break
            }
        }
        if (midId < 0) {
            throw IllegalStateException("Structure Cannot be triangulated.")
        }
        val point = points.removeAt(midId)
        points.add(2, point)
        return point
    }

    private fun findAndSortByCenter(points: ArrayList<PointWrapper>, p1: PointWrapper, p2: PointWrapper, p3: PointWrapper): Circle {
        val midCircle = buildCircle(p1, p2, p3)
        val center = midCircle.c

        points.removeAt(0)
        points.removeAt(0)
        points.removeAt(0)
        points.forEach {
            it.d2 = center.distanceSquaredTo(it.p)
        }
        points.sort()
        points.add(0, p3)
        points.add(0, p2)
        points.add(0, p1)

        return midCircle
    }

    private fun ArrayList<PointWrapper>.sort() = this.sort { p1, p2 ->
        if (p1.d2 == p2.d2) {
            if (p1.p.x == p2.p.x) {
                p1.p.y.compareTo(p2.p.y)
            } else {
                p1.p.x.compareTo(p2.p.x)
            }
        } else {
            p1.d2.compareTo(p2.d2)
        }
    }

    private fun buildPointIndex(points: ArrayList<PointWrapper>): IntArray {
        val pointIndex = IntArray(points.size)
        points.forEachIndexed { i, point -> pointIndex[point.id] = i }
        return pointIndex
    }

    private fun buildInitialTriangleAndAddToHull(hull: ArrayList<PointWrapper>, triangles: ArrayList<Triangle>, p1w: PointWrapper, p2w: PointWrapper, p3w: PointWrapper, midCircle: Circle) {
        val p1 = p1w.p
        val p2 = p2w.p
        val p3 = p3w.p

        p1w.e = Point(p2.x - p1.x, p2.y - p1.y)
        p1w.tId = 0
        hull.add(p1w)

        p2w.e = Point(p3.x - p2.x, p3.y - p2.y)
        p2w.tId = 0
        hull.add(p2w)

        p3w.e = Point(p1.x - p3.x, p1.y - p3.y)
        p3w.tId = 0
        hull.add(p3w)

        val tri = Triangle(p1w.id, p2w.id, p3w.id)
        tri.cc = midCircle.copy()

        triangles.add(tri)
    }

    private fun isRightHanded(p1w: PointWrapper, p2w: PointWrapper, p3w: PointWrapper): Boolean {
        val p1 = p1w.p
        val p2 = p2w.p
        val p3 = p3w.p

        val x = (p1.x + p2.x + p3.x) / 3.0f
        val y = (p1.y + p2.y + p3.y) / 3.0f

        val dx0 = p1.x - x
        val dy0 = p1.y - y

        val tx01 = p2.x - p1.x
        val ty01 = p2.y - p1.y

        val df = -tx01 * dy0 + ty01 * dx0
        return df < 0
    }

    private fun areCounterClockwise(a: PointWrapper, b: PointWrapper, c: PointWrapper): Boolean {
        val pa = a.p
        val pb = b.p
        val pc = c.p
        val ax = pa.x
        val ay = pa.y
        val bx = pb.x
        val by = pb.y
        val cx = pc.x
        val cy = pc.y
        return (((ax - cx) * (ay + cy)) + ((bx - ax) * (by + ay)) + ((cx - bx) * (cy + by))) > 0
    }

    private fun buildCircle(p1w: PointWrapper, p2w: PointWrapper, p3w: PointWrapper): Circle {
        val p1 = p1w.p
        val p2 = p2w.p
        val p3 = p3w.p

        val a1 = (p1.x + p2.x) / 2.0f
        val a2 = (p1.y + p2.y) / 2.0f
        val b1 = (p3.x + p2.x) / 2.0f
        val b2 = (p3.y + p2.y) / 2.0f

        val e2 = p1.x - p2.x
        val e1 = p2.y - p1.y

        val q2 = p3.x - p2.x
        val q1 = p2.y - p3.y

        if (e1 * -q2 + e2 * q1 == 0.0f) {
            return Circle(Point(0.0f, 0.0f), -1.0f)
        }

        val beta = (-e2 * (b1 - a1) + e1 * (b2 - a2)) / (e2 * q1 - e1 * q2)

        val cx = b1 + q1 * beta
        val cy = b2 + q2 * beta

        val radius = (p1.x - cx) * (p1.x - cx) + (p1.y - cy) * (p1.y - cy)
        return Circle(Point(cx, cy), radius)
    }

    private fun findCircleCenter(p1w: PointWrapper, p2w: PointWrapper, p3w: PointWrapper): Point {
        val p1 = p1w.p
        val p2 = p2w.p
        val p3 = p3w.p

        val a1 = (p1.x + p2.x) / 2.0f
        val a2 = (p1.y + p2.y) / 2.0f
        val b1 = (p3.x + p2.x) / 2.0f
        val b2 = (p3.y + p2.y) / 2.0f

        val e2 = p1.x - p2.x
        val e1 = p2.y - p1.y

        val q2 = p3.x - p2.x
        val q1 = p2.y - p3.y

        if (e1 * -q2 + e2 * q1 == 0.0f) {
            throw Exception("error finding circle center")
        }

        val beta = (-e2 * (b1 - a1) + e1 * (b2 - a2)) / (e2 * q1 - e1 * q2)

        val cx = b1 + q1 * beta
        val cy = b2 + q2 * beta

        return Point(cx, cy)
    }

    private fun buildTriangles(points: ArrayList<PointWrapper>, triangles: ArrayList<Triangle>, hull: ArrayList<PointWrapper>) {
        for (k in 3..points.size - 1) {

            val point = points[k]
            val newPoint = point.copy()
            val pointIndex = ArrayList<Int>()
            val triIndex = ArrayList<Int>()

            val visibleEdges = findVisibleEdges(hull, point)
            val hullId = adjustHull(hull, pointIndex, triIndex, visibleEdges, newPoint)
            val newTriangle = Triangle(newPoint.id)
            val triOffset = triangles.size
            if (pointIndex.size == 2) {
                addSingleTriangle(hull, pointIndex, triIndex, hullId, newTriangle, triangles)
            } else {
                addMultipleTriangles(hull, pointIndex, triIndex, hullId, newTriangle, triangles, triOffset)
            }
        }
    }

    private fun addMultipleTriangles(hull: ArrayList<PointWrapper>, pointIndex: ArrayList<Int>, triIndex: ArrayList<Int>, hullId: Int, newTriangle: Triangle, triangles: ArrayList<Triangle>, triOffset: Int) {
        newTriangle.ab = -1
        var p = 0
        while (p < pointIndex.size - 1) {
            newTriangle.b = pointIndex[p]
            newTriangle.c = pointIndex[p + 1]
            newTriangle.bc = triIndex[p]
            if (p > 0) {
                newTriangle.ab = triangles.size - 1
            }
            newTriangle.ac = triangles.size + 1
            indexTriangles(triangles, triIndex, newTriangle, p)
            triangles.add(Triangle().copy(newTriangle))
            p++
        }
        triangles[triangles.size - 1].ac = -1
        setTriangleIdsInHull(hull, hullId, triangles.size - 1, triOffset)
    }

    private fun addSingleTriangle(hull: ArrayList<PointWrapper>, pointIndex: ArrayList<Int>, triIndex: ArrayList<Int>, hullId: Int, newTriangle: Triangle, triangles: ArrayList<Triangle>) {
        newTriangle.b = pointIndex[0]
        newTriangle.c = pointIndex[1]
        newTriangle.bc = triIndex[0]
        newTriangle.ab = -1
        newTriangle.ac = -1
        indexTriangles(triangles, triIndex, newTriangle, 0)
        setTriangleIdsInHull(hull, hullId, triangles.size, triangles.size)
        triangles.add(newTriangle)
    }

    private fun setTriangleIdsInHull(hull: ArrayList<PointWrapper>, hullId: Int, triId1: Int, triId2: Int) {
        hull[hullId].tId = triId1
        if (hullId > 0)
            hull[hullId - 1].tId = triId2
        else {
            hull[hull.size - 1].tId = triId2
        }
    }

    private fun indexTriangles(triangles: ArrayList<Triangle>, triIndex: ArrayList<Int>, triangle: Triangle, index: Int) {
        val tri2 = triangles[triIndex[index]]
        if ((triangle.b == tri2.a && triangle.c == tri2.b) || (triangle.b == tri2.b && triangle.c == tri2.a)) {
            tri2.ab = triangles.size
        } else if ((triangle.b == tri2.a && triangle.c == tri2.c) || (triangle.b == tri2.c && triangle.c == tri2.a)) {
            tri2.ac = triangles.size
        } else if ((triangle.b == tri2.b && triangle.c == tri2.c) || (triangle.b == tri2.c && triangle.c == tri2.b)) {
            tri2.bc = triangles.size
        }
    }

    private fun findVisibleEdges(hull: ArrayList<PointWrapper>, point: PointWrapper): ArrayList<Int> {
        var visibleEdge = -1
        for (i in 0..hull.size - 1) {
            if (VisibilityTest(point, hull[i]).isVisible(hull[i].e)) {
                visibleEdge = i
                break
            }
        }
        val visibleEdges = ArrayList<Int>()
        if (visibleEdge == -1) {
            return ArrayList()
        }
        if (visibleEdge == 0) {
            var i = hull.size - 1
            while (i > 0) {
                if (VisibilityTest(point, hull[i]).isVisible(hull[i].e)) {
                    visibleEdges.add(0, i)
                } else {
                    break
                }
                i--
            }
        }
        visibleEdges.add(visibleEdge)
        for (i in visibleEdge + 1..hull.size - 1) {
            if (VisibilityTest(point, hull[i]).isVisible(hull[i].e)) {
                visibleEdges.add(i)
            } else {
                break
            }
        }
        return visibleEdges
    }

    private fun adjustHull(hull: ArrayList<PointWrapper>, pointIndex: ArrayList<Int>, triIndex: ArrayList<Int>, visibleEdges: ArrayList<Int>, newPoint: PointWrapper): Int {
        visibleEdges.forEach {
            pointIndex.add(hull[it].id)
            triIndex.add(hull[it].tId)
        }
        val nextEdge = (visibleEdges.last() + 1) % hull.size
        pointIndex.add(hull[nextEdge].id)
        if (nextEdge != 0) {
            triIndex.add(hull[nextEdge].tId)
        }
        val lastEdge = visibleEdges.first()
        val lastPoint = hull[lastEdge]
        val nextPoint = hull[nextEdge]
        var nextEdgeAdjusted = lastEdge + 1
        visibleEdges.subList(1, visibleEdges.size).sortedDescending().forEach {
            hull.removeAt(it)
            if (it < nextEdgeAdjusted) {
                nextEdgeAdjusted--
            }
        }
        nextEdgeAdjusted %= hull.size
        connectPointsInHull(lastPoint, nextPoint, newPoint)
        hull.add(nextEdgeAdjusted, newPoint)
        return nextEdgeAdjusted
    }

    private fun connectPointsInHull(last: PointWrapper, next: PointWrapper, newPoint: PointWrapper) {
        newPoint.e = Point(next.p.x - newPoint.p.x, next.p.y - newPoint.p.y)
        last.e = Point(newPoint.p.x - last.p.x, newPoint.p.y - last.p.y)
    }

    private fun needsFlipping(pa: PointWrapper, pb: PointWrapper, pc: PointWrapper, pd: PointWrapper): Boolean {
        val ax = pa.p.x
        val ay = pa.p.y
        val bx = pb.p.x
        val by = pb.p.y
        val cx = pc.p.x
        val cy = pc.p.y
        val dx = pd.p.x
        val dy = pd.p.y
        val v1x = bx - ax
        val v1y = by - ay
        val v2x = cx - ax
        val v2y = cy - ay
        val v3x = bx - dx
        val v3y = by - dy
        val v4x = cx - dx
        val v4y = cy - dy
        val cosA = v1x * v2x + v1y * v2y
        val cosD = v3x * v4x + v3y * v4y
        if (cosA < 0 && cosD < 0) {
            return true
        } else if (cosA > 0 && cosD > 0) {
            return false
        }
        val sinA = Math.abs(v1x * v2y - v1y * v2x)
        val sinD = Math.abs(v3x * v4y - v3y * v4x)
        if (cosA * sinD + sinA * cosD < 0) {
            return true
        } else {
            return false
        }
    }

    private fun flipTriangles(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>): HashSet<Int> {
        val ids = HashSet<Int>()
        for (id in 0..triangles.size - 1) {
            val triangle1 = triangles[id]
            tryFlipTriangle(points, pointIndex, triangles, triangle1, id, ids)
        }
        return ids
    }

    private fun flipTriangles(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>, ids: HashSet<Int>) : HashSet<Int> {
        val ids2 = HashSet<Int>()
        ids.forEach { id ->
            val triangle1 = triangles[id]
            tryFlipTriangle(points, pointIndex, triangles, triangle1, id, ids2)
        }
        return ids2
    }

    private fun tryFlipTriangle(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>, triangle1: Triangle, id1: Int, ids: HashSet<Int>) {
        var flipped = false
        if (triangle1.bc >= 0) {
            flipped = tryFlipEdge(points, pointIndex, triangles, id1, triangle1, ids)
        }
        if (!flipped && triangle1.ab >= 0) {
            val augmented = Triangle(triangle1.c, triangle1.a, triangle1.b, triangle1.ac, triangle1.ab, triangle1.bc)
            flipped = tryFlipEdge(points, pointIndex, triangles, id1, augmented, ids)
        }
        if (!flipped && triangle1.ac >= 0) {
            val augmented = Triangle(triangle1.b, triangle1.a, triangle1.c, triangle1.ab, triangle1.ac, triangle1.bc)
            tryFlipEdge(points, pointIndex, triangles, id1, augmented, ids)
        }
    }

    private fun flipEdges(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>): HashSet<Int> {
        val ids = HashSet<Int>()
        for (id in 0..triangles.size - 1) {
            val triangle1 = triangles[id]
            var flipped = false
            if (triangle1.bc >= 0 && (triangle1.ac < 0 || triangle1.ab < 0)) {
                flipped = tryFlipEdge(points, pointIndex, triangles, id, triangle1, ids, false)
            }
            if (!flipped && triangle1.ab >= 0 && (triangle1.ac < 0 || triangle1.bc < 0)) {
                val augmented = Triangle(triangle1.c, triangle1.a, triangle1.b, triangle1.ac, triangle1.ab, triangle1.bc)
                flipped = tryFlipEdge(points, pointIndex, triangles, id, augmented, ids, false)
            }
            if (!flipped && triangle1.ac >= 0 && (triangle1.bc < 0 || triangle1.ab < 0)) {
                val augmented = Triangle(triangle1.b, triangle1.a, triangle1.c, triangle1.ab, triangle1.ac, triangle1.bc)
                tryFlipEdge(points, pointIndex, triangles, id, augmented, ids, false)
            }
        }
        return ids
    }

    private fun tryFlipEdge(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>, id1: Int, augmented: Triangle, ids: HashSet<Int>, safe: Boolean = true): Boolean {
        val pd: Int
        val id3: Int
        val limb3: Int
        val limb4: Int
        val id2 = augmented.bc
        val triangle2 = triangles[id2]
        if (triangle2.ab == id1) {
            id3 = triangle2.c
            pd = pointIndex[triangle2.c]
            if (augmented.b == triangle2.a) {
                limb3 = triangle2.ac
                limb4 = triangle2.bc
            } else {
                limb3 = triangle2.bc
                limb4 = triangle2.ac
            }
        } else if (triangle2.ac == id1) {
            id3 = triangle2.b
            pd = pointIndex[triangle2.b]
            if (augmented.b == triangle2.a) {
                limb3 = triangle2.ab
                limb4 = triangle2.bc
            } else {
                limb3 = triangle2.bc
                limb4 = triangle2.ab
            }
        } else if (triangle2.bc == id1) {
            id3 = triangle2.a
            pd = pointIndex[triangle2.a]
            if (augmented.b == triangle2.b) {
                limb3 = triangle2.ab
                limb4 = triangle2.ac
            } else {
                limb3 = triangle2.ac
                limb4 = triangle2.ab
            }
        } else {
            throw Exception("triangle flipping error")
        }
        val pa = pointIndex[augmented.a]
        val pb = pointIndex[augmented.b]
        val pc = pointIndex[augmented.c]
        if (needsFlipping(points[pa], points[pb], points[pc], points[pd])) {
            return performEdgeFlip(triangles, id1, id2, id3, limb3, limb4, augmented, ids, safe)
        }
        return false
    }

    private fun performEdgeFlip(triangles: ArrayList<Triangle>, id1: Int, id2: Int, id3: Int, limb3: Int, limb4: Int, augmented: Triangle, ids: HashSet<Int>, safe: Boolean = true): Boolean {
        var flipped = false
        val triangle2 = triangles[id2]
        val limb1 = augmented.ab
        val limb2 = augmented.ac
        if (!safe || (limb1 != limb3 && limb2 != limb4)) {
            ids.add(id1)
            ids.add(id2)
            flipped = true
            triangle2.copy(Triangle(augmented.a, augmented.c, id3, limb2, limb4, id1))
            triangles[id1].copy(Triangle(augmented.a, augmented.b, id3, limb1, limb3, id2))
            if (limb3 >= 0) {
                changeTriangleAdjacency(triangles, limb3, id1, id2)
            }
            if (limb2 >= 0) {
                changeTriangleAdjacency(triangles, limb2, id2, id1)
            }
        }
        return flipped
    }

    private fun changeTriangleAdjacency(triangles: ArrayList<Triangle>, limb3: Int, id1: Int, id2: Int) {
        val triangle3 = triangles[limb3]
        if (triangle3.ab == id2) {
            triangle3.ab = id1
        } else if (triangle3.bc == id2) {
            triangle3.bc = id1
        } else if (triangle3.ac == id2) {
            triangle3.ac = id1
        }
    }
}
