package com.grimfox.gec.model.geometry

import java.util.*

class PointSet2F constructor(private val epsilon: Float = 0.000001f): MutableSet<Point2F> {

    constructor(points: Iterable<Point2F>, epsilon: Float = 0.000001f) : this(epsilon) {
        addAll(points)
    }

    private val epsilon2 = epsilon * epsilon
    private val points = ArrayList<Point2F?>()
    private val sortedX = TreeMap<Float, MutableList<Int>>()
    private val sortedY = TreeMap<Float, MutableList<Int>>()

    override val size: Int get() = points.size

    override fun isEmpty() = points.isEmpty()

    operator fun get(index: Int) = points[index]

    operator fun get(point: Point2F) = indexOf(point)

    fun indexOf(element: Point2F): Int {
        val xSet = sortedX.subMap(element.x - epsilon, true, element.x + epsilon, true).flatMap { it.value }
        if (xSet.isEmpty()) {
            return -1
        }
        val ySet = sortedY.subMap(element.y - epsilon, true, element.y + epsilon, true).flatMap { it.value }
        if (ySet.isEmpty()) {
            return -1
        }
        val union = ySet.toHashSet()
        union.retainAll(xSet)
        if (union.isEmpty()) {
            return -1
        }
        union.forEach {
            if (points[it]?.distance2(element) ?: Float.MAX_VALUE <= epsilon2) {
                return it
            }
        }
        return -1
    }

    override fun contains(element: Point2F): Boolean {
        return indexOf(element) > -1
    }

    override fun containsAll(elements: Collection<Point2F>): Boolean {
        elements.forEach { if (!contains(it)) return false }
        return true
    }

    override fun add(element: Point2F): Boolean {
        if (!contains(element)) {
            val index = points.size
            points.add(element)
            sortedX.getOrPut(element.x, { ArrayList(1) }).add(index)
            sortedY.getOrPut(element.y, { ArrayList(1) }).add(index)
            return true
        }
        return false
    }

    fun addOrGetIndex(element: Point2F): Int {
        val index = indexOf(element)
        if (index < 0) {
            val newIndex = points.size
            points.add(element)
            sortedX.getOrPut(element.x, { ArrayList(1) }).add(newIndex)
            sortedY.getOrPut(element.y, { ArrayList(1) }).add(newIndex)
            return newIndex
        }
        return index
    }

    override fun addAll(elements: Collection<Point2F>): Boolean {
        var changed = false
        elements.forEach {
            if (add(it)) {
                changed = true
            }
        }
        return changed
    }

    override fun clear() {
        points.clear()
        sortedX.clear()
        sortedY.clear()
    }

    override fun iterator(): MutableIterator<Point2F> {
        val delegate = points.filterNotNull().iterator()
        return object: MutableIterator<Point2F> {

            override fun hasNext() = delegate.hasNext()

            override fun next() = delegate.next()

            override fun remove() {
                throw UnsupportedOperationException()
            }
        }
    }

    fun removeAt(index: Int): Point2F? {
        val point = points.set(index, null)
        if (point != null) {
            val xIndex = sortedX[point.x]
            if (xIndex != null) {
                if (xIndex.size > 1) {
                    xIndex.remove(index)
                } else {
                    sortedX.remove(point.x)
                }
            }
            val yIndex = sortedY[point.y]
            if (yIndex != null) {
                if (yIndex.size > 1) {
                    yIndex.remove(index)
                } else {
                    sortedY.remove(point.y)
                }
            }
        }
        return point
    }

    override fun remove(element: Point2F): Boolean {
        val index = get(element)
        if (index > -1) {
            removeAt(index)
            return true
        }
        return false
    }

    override fun removeAll(elements: Collection<Point2F>): Boolean {
        var modified = false
        elements.forEach {
            if (remove(it)) {
                modified = true
            }
        }
        return modified
    }

    override fun retainAll(elements: Collection<Point2F>): Boolean {
        val pointsToRemove = PointSet2F(this, epsilon)
        pointsToRemove.removeAll(elements)
        removeAll(pointsToRemove)
        return pointsToRemove.isNotEmpty()
    }
}