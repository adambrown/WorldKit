package com.grimfox.triangle

import com.grimfox.triangle.geometry.Triangle
import java.util.*

class TriangleSampler(
        private val mesh: Mesh,
        private val random: Random = Random(TriangleSampler.RANDOM_SEED)) : Iterable<Triangle> {

    companion object {

        private val RANDOM_SEED = 110503L

        private val SAMPLE_FACTOR = 11
    }

    private var samples = 1

    private var triangleCount = 0

    fun reset() {
        this.samples = 1
        this.triangleCount = 0
    }

    fun update() {
        val count = mesh._triangles.size
        if (triangleCount != count) {
            triangleCount = count
            while (SAMPLE_FACTOR * samples * samples * samples < count) {
                samples++
            }
        }
    }

    override fun iterator(): Iterator<Triangle> {
        return mesh._triangles.sample(samples, random).iterator()
    }
}
