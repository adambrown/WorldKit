package com.grimfox.triangle.meshing.data

import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.Vertex

class BadTriQueue {

    companion object {

        private const val SQRT_2 = 1.4142135623730950488016887242096980785696718753769480732
    }

    private var queueFront: Array<BadTriangle?> = arrayOfNulls(4096)

    private var queueTail: Array<BadTriangle?> = arrayOfNulls(4096)

    private var nextNonEmptyQ: IntArray = IntArray(4096)

    private var firstNonEmptyQ: Int = -1

    var size: Int = 0
        internal set

    fun enqueue(badTri: BadTriangle) {
        var length: Double
        var multiplier: Double
        var expIncrement: Int
        val queueNumber: Int
        val posExponent: Int
        var i: Int
        this.size++
        if (badTri.key >= 1.0) {
            length = badTri.key
            posExponent = 1
        } else {
            length = 1.0 / badTri.key
            posExponent = 0
        }
        var exponent = 0
        while (length > 2.0) {
            expIncrement = 1
            multiplier = 0.5
            while (length * multiplier * multiplier > 1.0) {
                expIncrement *= 2
                multiplier *= multiplier
            }
            exponent += expIncrement
            length *= multiplier
        }
        exponent = 2 * exponent + if (length > SQRT_2) 1 else 0
        if (posExponent > 0) {
            queueNumber = 2047 - exponent
        } else {
            queueNumber = 2048 + exponent
        }
        if (queueFront[queueNumber] == null) {
            if (queueNumber > firstNonEmptyQ) {
                nextNonEmptyQ[queueNumber] = firstNonEmptyQ
                firstNonEmptyQ = queueNumber
            } else {
                i = queueNumber + 1
                while (queueFront[i] == null) {
                    i++
                }
                nextNonEmptyQ[queueNumber] = nextNonEmptyQ[i]
                nextNonEmptyQ[i] = queueNumber
            }
            queueFront[queueNumber] = badTri
        } else {
            queueTail[queueNumber]!!.next = badTri
        }
        queueTail[queueNumber] = badTri
        badTri.next = null
    }

    fun enqueue(enqueueTri: OTri, minEdge: Double, apex: Vertex, org: Vertex, dest: Vertex) {
        val newBad = BadTriangle()
        enqueueTri.copy(newBad.poortri)
        newBad.key = minEdge
        newBad.apex = apex
        newBad.org = org
        newBad.dest = dest
        enqueue(newBad)
    }

    fun dequeue(): BadTriangle? {
        if (firstNonEmptyQ < 0) {
            return null
        }
        this.size--
        val result = queueFront[firstNonEmptyQ]
        queueFront[firstNonEmptyQ] = result!!.next
        if (result == queueTail[firstNonEmptyQ]) {
            firstNonEmptyQ = nextNonEmptyQ[firstNonEmptyQ]
        }
        return result
    }
}
