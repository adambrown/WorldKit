package com.grimfox.gec.util

import com.grimfox.gec.generator.Point
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO

object Triangulate {

    data class Circle(var c: Point = Point(0.0f, 0.0f), var r: Float = -1.0f)
    
    data class PointWrapper(var p: Point = Point(0.0f, 0.0f), var e: Point = Point(0.0f, 0.0f), var d2: Float = 0.0f, var id: Int = 0, var tId: Int = 0)

    data class Triangle(var a: Int = 0, var b: Int = 0, var c: Int = 0, var ab: Int = -1, var bc: Int = -1, var ac: Int = -1, var cc: Circle = Circle()) {

        fun copy(other: Triangle): Triangle {
            a = other.a
            b = other.b
            c = other.c

            ab = other.ab
            bc = other.bc
            ac = other.ac

            cc.r = other.cc.r
            cc.c.x = other.cc.c.x
            cc.c.y = other.cc.c.y

            return this
        }

        override fun toString(): String {
            return "\ntriangle:\n=====\na: $a\nb: $b\nc: $c\nab: $ab\nbc: $bc\nac: $ac\ncc.r: ${cc.r}\ncc.x: ${cc.c.x}\ncc.y: ${cc.c.y}\n"
        }
    }

    class VisibilityTest(p1: Point, p2: Point, var dx: Float = p1.x - p2.x, var dy: Float = p1.y - p2.y) {

        constructor(p1: PointWrapper, p2: PointWrapper) : this(p1.p, p2.p)

        fun isVisible(point: Point): Boolean {
            return -dy * point.x + dx * point.y < 0
        }
    }

    val debug = false
    val drawCount = AtomicInteger(0)

    val virtualWidth = 100000.0f
    val stride = 10
    val outputWidth = 1024
    val gridSquare = virtualWidth / stride
    val minDistSquared = (gridSquare / 4.0f) * (gridSquare / 4.0f)

    var points = ArrayList<PointWrapper>()
    var pointIndex = IntArray(0)
    var triangles = ArrayList<Triangle>()
    var hull = ArrayList<PointWrapper>()

    @JvmStatic fun main(vararg args: String) {

        val random = Random(78907890)

        points = generatePoints(stride, random, gridSquare, minDistSquared)

        val p1 = chooseSeedPoint(points, stride)
        val p2 = findClosestPointToSeed(points, p1)
        val p3 = findPointWithSmallestCircle(p1, p2, points)

        val midCircle = findAndSortByCenter(points, p1, p2, p3)

        pointIndex = buildPointIndex(points)

        triangles = ArrayList<Triangle>()

        hull = buildInitialHull(triangles, p1, p2, p3, midCircle)

        buildTriangles(points, triangles, hull)

        var ids = flipTriangles(points, pointIndex, triangles)

        var iteration = 0
        while (ids.size > 0 && iteration < 50) {
            ids = flipTriangles(points, pointIndex, triangles, ids)
            iteration++
        }

        ids = flipEdges(points, pointIndex, triangles)

        iteration = 0
        while(ids.size > 0 && iteration < 100){
            ids = flipTriangles(points, pointIndex, triangles, ids)
            iteration++
            draw(true)
        }
        draw(true)

        val vertices = FloatArray(triangles.size * 2)
        val edges = IntArray(triangles.size * 3)

        triangles.forEachIndexed { i, triangle ->
            val vertex = findCircleCenter(points[pointIndex[triangle.a]], points[pointIndex[triangle.b]], points[pointIndex[triangle.c]])
            var o = i * 2
            vertices[o++] = vertex.x
            vertices[o] = vertex.y
            o = i * 3
            edges[o++] = triangle.ab
            edges[o++] = triangle.bc
            edges[o] = triangle.ac
        }

        drawDualGraph(vertices, edges)

        drawVoronoi(vertices, edges)

        drawDelaunay(vertices, edges)
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

    private fun generatePoints(stride: Int, random: Random, gridSquare: Float, minDistSquared: Float): ArrayList<PointWrapper> {
        val points = ArrayList<PointWrapper>()
        for (y in 0..stride - 1) {
            val oy = y * gridSquare
            for (x in 0..stride - 1) {
                while (true) {
                    val px = x * gridSquare + random.nextFloat() * gridSquare
                    val py = oy + random.nextFloat() * gridSquare
                    val point = Point(px, py)
                    if (checkDistances(points, x, y, stride, minDistSquared, point)) {
                        points.add(PointWrapper(point, id = points.size))
                        break
                    }
                }
            }
        }
        return points
    }

    private fun checkDistances(points: List<PointWrapper>, x: Int, y: Int, stride: Int, minDistance: Float, point: Point): Boolean {
        for (yOff in -3..3) {
            for (xOff in -3..3) {
                val ox = x + xOff
                val oy = y + yOff
                if (oy >= 0 && oy < stride && ox >= 0 && ox < stride) {
                    if (oy < y || (oy == y && ox < x)) {
                        if (point.distanceSquaredTo(points[oy * stride + ox].p) < minDistance) {
                            return false
                        }
                    } else {
                        return true
                    }
                }
            }
        }
        return true
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

        p1w.e.x = p2.x - p1.x
        p1w.e.y = p2.y - p1.y
        p1w.tId = 0
        hull.add(p1w)

        p2w.e.x = p3.x - p2.x
        p2w.e.y = p3.y - p2.y
        p2w.tId = 0
        hull.add(p2w)

        p3w.e.x = p1.x - p3.x
        p3w.e.y = p1.y - p3.y
        p3w.tId = 0
        hull.add(p3w)

        val tri = Triangle(p1w.id, p2w.id, p3w.id)
        tri.cc = midCircle.copy()

        triangles.add(tri)

        draw()
        return
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

    fun buildCircle(p1w: PointWrapper, p2w: PointWrapper, p3w: PointWrapper): Circle {
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

    fun findCircleCenter(p1w: PointWrapper, p2w: PointWrapper, p3w: PointWrapper): Point {
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

            draw()

            p++
        }
        triangles[triangles.size - 1].ac = -1
        setTriangleIdsInHull(hull, hullId, triangles.size - 1, triOffset)

        draw()
        return
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

        draw()
        return
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
        newPoint.e.x = next.p.x - newPoint.p.x
        newPoint.e.y = next.p.y - newPoint.p.y

        last.e.x = newPoint.p.x - last.p.x
        last.e.y = newPoint.p.y - last.p.y
    }

    private fun drawDualGraph(vertices: FloatArray, edges: IntArray) {
        val multiplier = outputWidth / virtualWidth
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.WHITE
        graphics.clearRect(0, 0, outputWidth, outputWidth)
        graphics.color = Color.RED

        triangles.forEach {
            drawTriangle(graphics, multiplier, points, pointIndex, it)
        }

        graphics.color = Color.BLACK

        for (i in 0..triangles.size - 1) {
            val p0 = vertices.getPoint(i)
            val o = i * 3
            drawEdgeIfExists(graphics, multiplier, vertices, edges, p0, o)
            drawEdgeIfExists(graphics, multiplier, vertices, edges, p0, o + 1)
            drawEdgeIfExists(graphics, multiplier, vertices, edges, p0, o + 2)
        }

        graphics.color = Color.GREEN

        points.forEach {
            drawPoint(graphics, multiplier, it, 2)
        }

        ImageIO.write(image, "png", File("output/testing-${String.format("%05d", drawCount.incrementAndGet())}.png"))
    }

    private fun drawVoronoi(vertices: FloatArray, edges: IntArray) {
        val multiplier = outputWidth / virtualWidth
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.WHITE
        graphics.clearRect(0, 0, outputWidth, outputWidth)

        graphics.color = Color.BLACK

        for (i in 0..triangles.size - 1) {
            val p0 = vertices.getPoint(i)
            val o = i * 3
            drawEdgeIfExists(graphics, multiplier, vertices, edges, p0, o)
            drawEdgeIfExists(graphics, multiplier, vertices, edges, p0, o + 1)
            drawEdgeIfExists(graphics, multiplier, vertices, edges, p0, o + 2)
        }

        graphics.color = Color.RED

        points.forEach {
            drawPoint(graphics, multiplier, it, 2)
        }

        ImageIO.write(image, "png", File("output/testing-${String.format("%05d", drawCount.incrementAndGet())}.png"))
    }

    private fun drawDelaunay(vertices: FloatArray, edges: IntArray) {
        val multiplier = outputWidth / virtualWidth
        val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
        val graphics = image.createGraphics()
        graphics.background = Color.WHITE
        graphics.clearRect(0, 0, outputWidth, outputWidth)
        graphics.color = Color.BLACK

        triangles.forEach {
            drawTriangle(graphics, multiplier, points, pointIndex, it)
        }

        graphics.color = Color.RED

        points.forEach {
            drawPoint(graphics, multiplier, it, 2)
        }

        ImageIO.write(image, "png", File("output/testing-${String.format("%05d", drawCount.incrementAndGet())}.png"))
    }

    private fun drawEdgeIfExists(graphics: Graphics2D, multiplier: Float, vertices: FloatArray, edges: IntArray, point: Point, offset: Int) {
        val id = edges[offset]
        if (id > 0) {
            drawLine(graphics, multiplier, point, vertices.getPoint(id))
        }
    }

    private fun FloatArray.getPoint(i: Int): Point {
        val o = i * 2
        return Point(this[o], this[o + 1])
    }

    private fun draw(vararg highlightPoints: PointWrapper) {
        draw(false, *highlightPoints)
    }

    private fun draw(force: Boolean, vararg highlightPoints: PointWrapper) {
        if (debug || force) {
            if (debug) {
                println("\n\n\n========================\n\n\n$triangles\n\n\n")
            }
            val multiplier = outputWidth / virtualWidth
            val image = BufferedImage(outputWidth, outputWidth, BufferedImage.TYPE_3BYTE_BGR)
            val graphics = image.createGraphics()
            graphics.background = Color.WHITE
            graphics.clearRect(0, 0, outputWidth, outputWidth)
            graphics.color = Color.BLACK

            triangles.forEach {
                drawTriangle(graphics, multiplier, points, pointIndex, it)
            }

            points.forEach {
                drawPoint(graphics, multiplier, it, 1)
            }

            graphics.color = Color.GREEN

            hull.forEach {
                drawHullPoint(graphics, multiplier, it, 2)
            }

            graphics.color = Color.RED

            highlightPoints.forEach {
                drawHullPoint(graphics, multiplier, it, 3)
            }

            ImageIO.write(image, "png", File("output/testing-${String.format("%05d", drawCount.incrementAndGet())}.png"))
        }
    }

    private fun drawHullPoint(graphics: Graphics2D, multiplier: Float, point: PointWrapper, radius: Int) {
        drawPoint(graphics, multiplier, point, radius)
        if (point.e.x != 0.0f || point.e.y != 0.0f) {
            val endPoint = Point(point.p.x + point.e.x, point.p.y + point.e.y)
            drawLine(graphics, multiplier, point.p, endPoint)
        }
    }

    private fun drawPoint(graphics: Graphics2D, multiplier: Float, point: PointWrapper, radius: Int) {
        val diameter = radius * 2 + 1
        graphics.fillOval(Math.round(point.p.x * multiplier) - radius, Math.round(point.p.y * multiplier) - radius, diameter, diameter)
    }

    private fun drawTriangle(graphics: Graphics2D, multiplier: Float, points: ArrayList<PointWrapper>, pointIndex: IntArray, triangle: Triangle) {
        val p1 = points[pointIndex[triangle.a]]
        val p2 = points[pointIndex[triangle.b]]
        val p3 = points[pointIndex[triangle.c]]
        val p1x = Math.round(p1.p.x * multiplier)
        val p1y = Math.round(p1.p.y * multiplier)
        val p2x = Math.round(p2.p.x * multiplier)
        val p2y = Math.round(p2.p.y * multiplier)
        val p3x = Math.round(p3.p.x * multiplier)
        val p3y = Math.round(p3.p.y * multiplier)
        graphics.drawLine(p1x, p1y, p2x, p2y)
        graphics.drawLine(p2x, p2y, p3x, p3y)
        graphics.drawLine(p3x, p3y, p1x, p1y)
    }

    private fun drawLine(graphics: Graphics2D, multiplier: Float, p1: Point, p2: Point) {
        val p1x = Math.round(p1.x * multiplier)
        val p1y = Math.round(p1.y * multiplier)
        val p2x = Math.round(p2.x * multiplier)
        val p2y = Math.round(p2.y * multiplier)
        graphics.drawLine(p1x, p1y, p2x, p2y)
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

    private fun flipTriangles(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>): ArrayList<Int> {
        val ids = ArrayList<Int>()
        for (id in 0..triangles.size - 1) {
            val triangle1 = triangles[id]
            tryFlipTriangle(points, pointIndex, triangles, triangle1, id, ids)
        }
        return ids
    }

    private fun flipTriangles(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>, ids: ArrayList<Int>) : ArrayList<Int> {
        val ids2 = ArrayList<Int>()
        ids.forEach { id ->
            val triangle1 = triangles[id]
            tryFlipTriangle(points, pointIndex, triangles, triangle1, id, ids2)
        }
        return ids2
    }

    private fun tryFlipTriangle(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>, triangle1: Triangle, id1: Int, ids: ArrayList<Int>) {
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

    fun flipEdges(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>): ArrayList<Int> {
        val ids = ArrayList<Int>()
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

    private fun tryFlipEdge(points: ArrayList<PointWrapper>, pointIndex: IntArray, triangles: ArrayList<Triangle>, id1: Int, augmented: Triangle, ids: ArrayList<Int>, safe: Boolean = true): Boolean {
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

    private fun performEdgeFlip(triangles: ArrayList<Triangle>, id1: Int, id2: Int, id3: Int, limb3: Int, limb4: Int, augmented: Triangle, ids: ArrayList<Int>, safe: Boolean = true): Boolean {
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
