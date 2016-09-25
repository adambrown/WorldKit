package com.grimfox.gec.model.geometry

import java.util.*

class SplinePoint2F(var p: Point2F, var cp1: Point2F = p, var cp2: Point2F = p) {

    fun interpolate(other: SplinePoint2F, interpolation: Float): SplinePoint2F {
        if (interpolation >= 1.0f) {
            return other
        }
        if (interpolation <= 0.0f) {
            return this
        }
        val dx1 = cp2.x - p.x
        val dy1 = cp2.y - p.y
        val dx2 = other.cp1.x - cp2.x
        val dy2 = other.cp1.y - cp2.y
        val dx3 = other.p.x - other.cp1.x
        val dy3 = other.p.y - other.cp1.y
        val p1x = p.x + dx1 * interpolation
        val p1y = p.y + dy1 * interpolation
        val p2x = cp2.x + dx2 * interpolation
        val p2y = cp2.y + dy2 * interpolation
        val p3x = other.cp1.x + dx3 * interpolation
        val p3y = other.cp1.y + dy3 * interpolation
        val qdx1 = p2x - p1x
        val qdy1 = p2y - p1y
        val qdx2 = p3x - p2x
        val qdy2 = p3y - p2y
        val qp1x = p1x + qdx1 * interpolation
        val qp1y = p1y + qdy1 * interpolation
        val qp2x = p2x + qdx2 * interpolation
        val qp2y = p2y + qdy2 * interpolation
        val dx = qp2x - qp1x
        val dy = qp2y - qp1y
        return SplinePoint2F(Point2F(qp1x + dx * interpolation, qp1y + dy * interpolation), Point2F(qp1x, qp1y), Point2F(qp2x, qp2y))
    }

    fun interpolationTimes(other: SplinePoint2F, desiredSegmentLength: Float, iterations: Int): Pair<Float, ArrayList<Float>> {
        val tries = LinkedHashSet<Int>()
        tries.add(3)
        var (length, times) = interpolationTimes(other, 3, iterations)
        var averageLength = length / (times.size - 1)
        while (tries.size < 12) {
            if (length == 0.0f && averageLength == 0.0f) {
                return Pair(length, ArrayList(listOf(0.0f, 1.0f)))
            }
            if (averageLength == desiredSegmentLength) {
                return Pair(length, times)
            }
            if (averageLength < desiredSegmentLength) {
                if (times.size == 2 || length / (times.size - 2) > desiredSegmentLength) {
                    return Pair(length, times)
                } else {
                    tries.add(times.size - 2)
                    val pair = interpolationTimes(other, times.size - 2, iterations)
                    length = pair.first
                    times = pair.second
                    averageLength = length / (times.size - 1)
                }
            } else {
                val nextTry = Math.ceil((length / desiredSegmentLength).toDouble()).toInt()
                if (tries.contains(nextTry)) {
                    return Pair(length, times)
                }
                tries.add(nextTry)
                val pair = interpolationTimes(other, nextTry, iterations)
                length = pair.first
                times = pair.second
                averageLength = length / (times.size - 1)
            }
        }
        return Pair(length, times)
    }

    fun interpolationTimes(other: SplinePoint2F, samples: Int, iterations: Int): Pair<Float, ArrayList<Float>> {
        val realIterations = Math.max(1, iterations)
        val increment = 1.0f / samples
        var guesses = ArrayList<Float>(samples + 1)
        guesses.add(0.0f)
        for (i in 1..samples - 1) {
            guesses.add(i * increment)
        }
        guesses.add(1.0f)
        var length = 0.0f
        for (i in 1..realIterations) {
            val samplePoints = ArrayList<SplinePoint2F>(samples + 1)
            for (g in 0..samples) {
                samplePoints.add(interpolate(other, guesses[g]))
            }
            length = 0.0f
            val offsets = ArrayList<Float>(samples + 1)
            offsets.add(0.0f)
            for (s in 1..samples) {
                val localLength = samplePoints[s - 1].p.distance(samplePoints[s].p)
                length += localLength
                offsets.add(length)
            }
            val newGuesses = ArrayList<Float>(samples + 1)
            newGuesses.add(0.0f)
            val estimatedSegment = length / samples
            for (n in 1..samples - 1) {
                val desiredOffset = estimatedSegment * n
                for(j in 1..offsets.size - 1) {
                    val currentOffset = offsets[j]
                    if (currentOffset == desiredOffset) {
                        newGuesses.add(guesses[j])
                        break
                    } else if (currentOffset < desiredOffset) {
                        continue
                    }
                    val lastOffset = offsets[j - 1]
                    val desiredDelta = desiredOffset - lastOffset
                    val actualDelta = currentOffset - lastOffset
                    val interpolation = desiredDelta / actualDelta
                    val currentGuess = guesses[j]
                    val lastGuess = guesses[j - 1]
                    val guessDelta = currentGuess - lastGuess
                    val newGuess = lastGuess + (guessDelta * interpolation)
                    newGuesses.add(newGuess)
                    break
                }
            }
            newGuesses.add(1.0f)
            guesses = newGuesses
        }
        return Pair(length, guesses)
    }
}