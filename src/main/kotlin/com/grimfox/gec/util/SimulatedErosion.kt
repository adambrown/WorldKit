package com.grimfox.gec.util

import com.grimfox.gec.model.*
import java.lang.Math.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

object SimulatedErosion {

    fun simulateErosion(inputHeights: FloatArrayMatrix, distanceScale: Float, talus: Float): FloatArrayMatrix {
        val diagonalDistance = sqrt(2.0 * distanceScale * distanceScale).toFloat()

        val width = inputHeights.width
        val rock1 = FloatArrayMatrix(width) { i -> inputHeights[i] }
        val groundWater1 = FloatArrayMatrix(width)
        val sediment1 = FloatArrayMatrix(width) { i -> (Math.abs(inputHeights[i]) / 800.0f) * 20.0f }
        println("max sediment: ${sediment1.array.max()}")
        val rock2 = FloatArrayMatrix(width)
        val groundWater2 = FloatArrayMatrix(width)
        val sediment2 = FloatArrayMatrix(width)

        val directionOfFlow = ByteArrayMatrix(width)

        var currentRock = rock1
        var currentWater = groundWater1
        var currentSediment = sediment1

        var nextRock = rock2
        var nextWater = groundWater2
        var nextSediment = sediment2


        for (it in 1..25) {
            println("sim erosion iteration $it")
            calculateSiltBaseTransferDirection(currentRock, currentSediment, directionOfFlow, distanceScale, diagonalDistance, talus)
            for (i in 0 until nextSediment.size.toInt()) {
                nextSediment[i] = 0.0f
            }
            transferBaseSilt(currentRock, currentSediment, directionOfFlow, nextSediment, distanceScale, diagonalDistance, talus)
        }
        for (i in 0 until currentRock.size.toInt()) {
            currentRock[i] = currentRock[i] + currentSediment[i]
        }
        return currentRock
    }

    private fun transferBaseSilt(currentRock: FloatArrayMatrix, currentSediment: FloatArrayMatrix, directionOfFlow: ByteArrayMatrix, nextSediment: FloatArrayMatrix, horizontalDistance: Float, diagonalDistance: Float, talus: Float) {
        val maxRiseHorizontal = (tan(talus.toDouble()) * horizontalDistance).toFloat()
        val maxRiseDiagonal = (tan(talus.toDouble()) * diagonalDistance).toFloat()
        val flowMapping = intArrayOf(0, 8, 7, 6, 5, 4, 3, 2, 1)
        val width = currentRock.width
        val widthM1 = width - 1
        val locks = Array(width + 2) { ReentrantLock() }
        (0 until width).toList().parallelStream().forEach { y ->
            doWithLocks(locks[y], locks[y+1], locks[y+2], 5) {
                for (x in 0 until width) {
                    val transfers = ArrayList<Quintuple<Int, Int, Float, Float, Float>>(8)
                    var base = currentRock[x, y] + currentSediment[x, y]
                    var index = 0
                    for (i in -1..1) {
                        val oy = (y + i).coerceIn(0, widthM1)
                        for (j in -1..1) {
                            if (i == 0 && j == 0) {
                                continue
                            }
                            index++
                            val ox = (x + j).coerceIn(0, widthM1)
                            if (directionOfFlow[ox, oy].toInt() == flowMapping[index]) {
                                val sediment = currentSediment[ox, oy]
                                if (sediment == 0.0f) {
                                    continue
                                }
                                val test = currentRock[ox, oy] + sediment
                                val rise = test - base
                                if (rise <= 0.0f) {
                                    continue
                                }
                                val maxRise = if (i * i == j * j) maxRiseDiagonal else maxRiseHorizontal
                                val transfer = min(sediment, rise - maxRise)
                                if (transfer > 0.0f) {
                                    transfers.add(Quintuple(ox, oy, transfer, test, maxRise))
                                }
                            }
                        }
                    }
                    transfers.sortByDescending { it.third }
                    for ((ox, oy, transfer, test, maxRise) in transfers) {
                        val rise = test - base
                        if (rise <= 0.0f) {
                            continue
                        }
                        val actualTransfer = min(transfer, rise - maxRise)
                        if (actualTransfer > 0.0f) {
                            nextSediment[ox, oy] -= actualTransfer
                            nextSediment[x, y] += actualTransfer
                            base += actualTransfer
                        }
                    }
                }
            }
        }
        (0 until width).toList().parallelStream().forEach { y ->
            val yOff = y * width
            for (x in 0 until width) {
                val i = yOff + x
                currentSediment[i] = max(0.0f, currentSediment[i] + nextSediment[i])
            }
        }
    }

    private fun calculateSiltBaseTransferDirection(currentRock: FloatArrayMatrix, currentSediment: FloatArrayMatrix, directionOfFlow: ByteArrayMatrix, horizontalDistance: Float, diagonalDistance: Float, talus: Float) {
        val width = currentRock.width
        val widthM1 = width - 1
        (0 until width).toList().parallelStream().forEach { y ->
            for (x in 0 until width) {
                val base = currentRock[x, y] + currentSediment[x, y]
                var index = 0
                var maxIndex = 0
                var maxAngle = 0.0f
                for (i in -1..1) {
                    val oy = (y + i).coerceIn(0, widthM1)
                    for (j in -1..1) {
                        if (i == 0 && j == 0) {
                            continue
                        }
                        index++
                        val ox = (x + j).coerceIn(0, widthM1)
                        val sediment = currentSediment[ox, oy]
                        if (sediment == 0.0f) {
                            continue
                        }
                        val test = currentRock[ox, oy] + sediment
                        val rise = base - test
                        if (rise <= 0.0f) {
                            continue
                        }
                        val run = if (i * i == j * j) diagonalDistance else horizontalDistance
                        val angle = atan2(rise.toDouble(), run.toDouble()).toFloat()
                        if (angle > talus && angle > maxAngle) {
                            maxAngle = angle
                            maxIndex = index
                        }
                    }
                }
                directionOfFlow[x, y] = maxIndex.toByte()
            }
        }
    }

    private fun <T> doWithLocks(lock1: ReentrantLock, lock2: ReentrantLock, lock3: ReentrantLock, timeout: Long, timeUnit: TimeUnit = TimeUnit.MILLISECONDS, block: () -> T): T {
        while (true) {
            try {
                if (lock1.tryLock(timeout, timeUnit)) {
                    if (lock2.tryLock(timeout, timeUnit)) {
                        if (lock3.tryLock(timeout, timeUnit)) {
                            break
                        } else {
                            lock2.unlock()
                            lock1.unlock()
                        }
                    } else {
                        lock1.unlock()
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        try {
            return block()
        } finally {
            lock3.unlock()
            lock2.unlock()
            lock1.unlock()
        }
    }
}