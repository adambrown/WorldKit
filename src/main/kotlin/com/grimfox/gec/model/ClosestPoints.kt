package com.grimfox.gec.model

import java.util.*

class ClosestPoints private constructor(private val internalPoints: Array<Pair<Int, Float>?>) : Iterable<Pair<Int, Float>?> {

    constructor(p0: Pair<Int, Float>? = null,
                p1: Pair<Int, Float>? = null,
                p2: Pair<Int, Float>? = null,
                p3: Pair<Int, Float>? = null,
                p4: Pair<Int, Float>? = null) : this(arrayOf(p0, p1, p2, p3, p4))


    constructor() : this(arrayOfNulls(9))

    var p0 : Pair<Int, Float>?
        get() { return internalPoints[0] }
        set(value) { internalPoints[0] = value }

    var p1 : Pair<Int, Float>?
        get() { return internalPoints[1] }
        set(value) { internalPoints[1] = value }

    var p2 : Pair<Int, Float>?
        get() { return internalPoints[2] }
        set(value) { internalPoints[2] = value }

    var p3 : Pair<Int, Float>?
        get() { return internalPoints[3] }
        set(value) { internalPoints[3] = value }

    var p4 : Pair<Int, Float>?
        get() { return internalPoints[4] }
        set(value) { internalPoints[4] = value }

    val points: Array<Pair<Int, Float>?>
    get() { return Arrays.copyOf(internalPoints, internalPoints.size) }

    operator fun get(index: Int): Pair<Int, Float>? {
        return points[index]
    }

    operator fun set(index: Int, value: Pair<Int, Float>?) {
        points[index] = value
    }

    override fun iterator(): Iterator<Pair<Int, Float>?> {
        return points.toList().iterator()
    }
}