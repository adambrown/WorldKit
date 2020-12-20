package wk.api

import wk.internal.ext.fastFloorI
import kotlin.math.*
import kotlin.random.Random

private val smallFloat = sqrt(Float.MIN_VALUE) * 8.0f

private const val MIN_STEPS = 25
private const val MAX_STEPS = 1000

private const val MIN_VOLUME = 1.0f
private const val MAX_VOLUME = 30.0f
private const val VOLUME_DELTA = MAX_VOLUME - MIN_VOLUME

private const val MIN_DRAG = 0.005f
private const val MAX_DRAG = 0.21f
private const val DRAG_DELTA = MAX_DRAG - MIN_DRAG

private const val MIN_INERTIA = 0.1f
private const val MAX_INERTIA = 1.2f
private const val INERTIA_DELTA = MAX_INERTIA - MIN_INERTIA

private const val MIN_TRANSFER_RADIUS = 1.5f
private const val MAX_TRANSFER_RADIUS = 5.0f
private const val MIN_TRANSFER_RADIUS_INT = 1
private const val MAX_TRANSFER_RADIUS_INT = 5
private const val TRANSFER_RADIUS_DELTA = MAX_TRANSFER_RADIUS - MIN_TRANSFER_RADIUS

private const val TRANSFER_BUFFER_SIZE = (MAX_TRANSFER_RADIUS_INT * 2 + 1) * (MAX_TRANSFER_RADIUS_INT * 2 + 1)

private const val MIN_RANDOM_SEDIMENT = 0.0f
private const val MAX_RANDOM_SEDIMENT = 0.9f
private const val RANDOM_SEDIMENT_DELTA = MAX_RANDOM_SEDIMENT - MIN_RANDOM_SEDIMENT

private fun heightsToNormalizedAngles(pixelSize: Float, heightDiffCap: Float, precision: Int): Pair<FloatArray, Float> {
    val nFactor = 2.0 / PI
    val increment = heightDiffCap.toDouble() / precision
    val values = FloatArray(precision)
    for (i in 0 until precision) {
        values[i] = (atan((i * increment) / pixelSize) * nFactor).toFloat()
    }
    return values to precision / heightDiffCap
}

private fun heightsToNormalizedAngle(height1: Float, height2: Float, lookupFactor: Float, lut: FloatArray): Float {
    val delta = height2 - height1
    val lookup = min(abs(delta) * lookupFactor, lut.size - 1.0f)
    val i0 = fastFloorI(lookup)
    val i1 = min(i0 + 1, lut.size - 1)
    val alpha = lookup - i0
    val iAlpha = 1.0f - alpha
    val magnitude = lut[i0] * iAlpha + lut[i1] * alpha
    return if (height1 < height2) magnitude else -magnitude
}

private class ErosionParticle(
    val heightMap: FloatArrayMatrix,
    val output: FloatArrayMatrix,
    val widthInMeters: Float,
    val normalScale: Float,
    val angleLookup: FloatArray,
    val angleLookupFactor: Float,
    randomSeed: Long,
    val easeInSteps: Int = 10,
    minSteps: Int = 200,
    maxSteps: Int = 400,
    chaosFrequency: Float = 0.1f,
    chaosPower: Float = 0.5f,
    val chaosOffsetX: Float = 0.0f,
    val chaosOffsetY: Float = 0.0f,
    minRadius: Float =  0.0f,
    maxRadius: Float = 1.0f,
    minVolume: Float = 0.0f,
    maxVolume: Float = 1.0f,
    minDrag: Float = 0.0f,
    maxDrag: Float = 1.0f,
    minInertia: Float = 0.0f,
    maxInertia: Float = 1.0f,
    minRandomSediment: Float = 0.0f,
    maxRandomSediment: Float = 1.0f
) {
    val widthM1 = output.width - 1
    val heightM1 = output.height - 1
    val random = Random(randomSeed)
    val minSteps = min(minSteps, maxSteps).coerceIn(MIN_STEPS, MAX_STEPS)
    val deltaSteps = max(minSteps, maxSteps).coerceIn(MIN_STEPS, MAX_STEPS) - this.minSteps
    val minRadius = min(minRadius, maxRadius).coerceIn(0.0f, 1.0f) * TRANSFER_RADIUS_DELTA + MIN_TRANSFER_RADIUS
    val deltaRadius = max(minRadius, maxRadius).coerceIn(0.0f, 1.0f) * TRANSFER_RADIUS_DELTA + MIN_TRANSFER_RADIUS - this.minRadius
    val minVolume = min(minVolume, maxVolume).coerceIn(0.0f, 1.0f) * VOLUME_DELTA + MIN_VOLUME
    val deltaVolume = max(minVolume, maxVolume).coerceIn(0.0f, 1.0f) * VOLUME_DELTA + MIN_VOLUME - this.minVolume
    val minRandomSediment = min(minRandomSediment, maxRandomSediment).coerceIn(0.0f, 1.0f) * RANDOM_SEDIMENT_DELTA + MIN_RANDOM_SEDIMENT
    val deltaRandomSediment = max(minRandomSediment, maxRandomSediment).coerceIn(0.0f, 1.0f) * RANDOM_SEDIMENT_DELTA + MIN_RANDOM_SEDIMENT - this.minRandomSediment
    val widthInMetersI = 1.0f / widthInMeters
    val pixelSize = widthInMeters / heightMap.width
    val minDrag = min(minDrag, maxDrag).coerceIn(0.0f, 1.0f) * DRAG_DELTA + MIN_DRAG
    val deltaDrag = max(minDrag, maxDrag).coerceIn(0.0f, 1.0f) * DRAG_DELTA + MIN_DRAG - this.minDrag
    val minInertia = min(minInertia, maxInertia).coerceIn(0.0f, 1.0f) * INERTIA_DELTA + MIN_INERTIA
    val deltaInertia = max(minInertia, maxInertia).coerceIn(0.0f, 1.0f) * INERTIA_DELTA + MIN_INERTIA - this.minInertia
    val chaosPower = chaosPower.coerceIn(0.0f, 1.0f)
    val chaosFrequency = chaosFrequency.coerceIn(0.0f, 1.0f) * 499.95f + 0.05f

    val gradient = vec3()
    val chaosVector = vec2()

    val transferBuffer = FloatArray(TRANSFER_BUFFER_SIZE)
    val transferIndexBuffer = IntArray(TRANSFER_BUFFER_SIZE)

    fun process() {
        val r = deltaRadius * random.nextFloat() + minRadius
        val r2 = r * r
        val r2i = 1.0f / r2
        val ri = ceil(r).roundToInt().coerceIn(MIN_TRANSFER_RADIUS_INT, MAX_TRANSFER_RADIUS_INT)
        val manning = 1.0f / (deltaDrag * random.nextFloat() + minDrag)
        val chaosEffect = chaosPower * random.nextFloat() * 0.95f
        var volume = r * (deltaVolume * random.nextFloat() + minVolume)
        val evaporation = volume / (deltaSteps * random.nextFloat() + minSteps)
        val stepCap = minSteps + deltaSteps
        val inertia = deltaInertia * random.nextFloat() + minInertia
        val inertiaFactor = 1.0f / (1.0f + inertia)
        var positionX = random.nextFloat()
        var positionY = random.nextFloat()
        var sediment = 0.0f
        var velocityX = 0.0f
        var velocityY = 0.0f
        for (i in 0..minSteps + deltaSteps) {
            if (positionX !in 0.0f..1.0f || positionY !in 0.0f..1.0f) {
                break
            }
            val currentHeight = heightMap.getHeightAndDownSlopeVector(positionX, positionY, normalScale, gradient)
            var targetVelocityX: Float
            var targetVelocityY: Float
            if (abs(gradient.x) > smallFloat || abs(gradient.y) > smallFloat || abs(gradient.z) > smallFloat) {
                val gradientXY2 = gradient.x * gradient.x + gradient.y * gradient.y
                val gradientZ2 = gradient.z * gradient.z
                val gradientRun = sqrt(gradientXY2)
                val gradientSlope = abs(gradient.z) / gradientRun
                val maxVelocity = manning * sqrt(gradientSlope)
                val gradientMagnitude = sqrt(gradientXY2 + gradientZ2)
                val gradientToAccelerationVector = maxVelocity / gradientMagnitude
                curl(positionX + chaosOffsetX, positionY + chaosOffsetY, chaosFrequency, chaosEffect * maxVelocity, 1.0f, chaosVector)
                targetVelocityX = gradient.x * gradientToAccelerationVector + chaosVector.x
                targetVelocityY = gradient.y * gradientToAccelerationVector + chaosVector.y
            } else {
                targetVelocityX = 0.0f
                targetVelocityY = 0.0f
            }

            var dx = (velocityX * inertia + targetVelocityX) * inertiaFactor
            var dy = (velocityY * inertia + targetVelocityY) * inertiaFactor

            if (abs(dx) < smallFloat && abs(dy) < smallFloat) {
                dx = targetVelocityX
                dy = targetVelocityY
            }

            val dxn: Float
            val dyn: Float
            if (abs(dx) < smallFloat && abs(dy) < smallFloat) {
                dxn = 0.0f
                dyn = 0.0f
            } else {
                val time = pixelSize / sqrt(dx * dx + dy * dy)
                dxn = dx * time
                dyn = dy * time
            }

            val lastX = positionX
            val lastY = positionY

            velocityX = dx
            velocityY = dy

            positionX += dxn * widthInMetersI
            positionY += dyn * widthInMetersI

            val nextHeight = heightMap[positionX, positionY]

            val normalizedAngle = heightsToNormalizedAngle(currentHeight, nextHeight, angleLookupFactor, angleLookup)

            volume -= evaporation
            if (volume < smallFloat && sediment < smallFloat) {
                break
            }

            val currentCapacity = volume * max(0.0f, -normalizedAngle)
            if (i == 0) {
                sediment = currentCapacity * (deltaRandomSediment * random.nextFloat() + minRandomSediment)
            }
            val freeCapacity = currentCapacity - sediment

            var transfer = max(-sediment, min(freeCapacity, (currentHeight - nextHeight) * 0.25f))

            if (i < easeInSteps) {
                transfer /= (easeInSteps - i)
            }

            if (positionX !in 0.0f..1.0f || positionY !in 0.0f..1.0f || i == stepCap) {
                transfer = -sediment
            }

            sediment = max(0.0f, sediment + performTransfer(lastX, lastY, transfer, max(currentHeight, nextHeight), ri, r2, r2i))
        }
    }

    private fun performTransfer(
        lastX: Float,
        lastY: Float,
        transfer: Float,
        cap: Float,
        ri: Int,
        r2: Float,
        r2i: Float
    ): Float {
        val x = (lastX * widthM1).coerceIn(0.0f, widthM1.toFloat())
        val y = (lastY * heightM1).coerceIn(0.0f, heightM1.toFloat())
        val xi = x.roundToInt()
        val yi = y.roundToInt()

        var maximumHeight = cap
        var sum = 0.0f
        var transferCount = 0
        for (oyi in max(0, yi - ri)..min(heightM1, yi + ri)) {
            val yOff = oyi * (widthM1 + 1)
            for (oxi in max(0, xi - ri)..min(widthM1, xi + ri)) {
                val dx = (x - oxi)
                val dy = (y - oyi)
                val d2 = dx * dx + dy * dy
                if (d2 < r2) {
                    val a = 1.0f - (d2 * r2i)
                    val c = a * a * a
                    sum += c
                    val index = yOff + oxi
                    transferIndexBuffer[transferCount] = index
                    transferBuffer[transferCount] = c
                    transferCount++
                    val height = output[index]
                    if (height > maximumHeight) {
                        maximumHeight = height
                    }
                }
            }
        }

        var transferSum = 0.0f
        val factor = 1.0f / sum
        for (i in 0 until transferCount) {
            val c = transferBuffer[i]
            val index = transferIndexBuffer[i]
            val moving = c * factor * transfer
            val initial = output[index]
            var postMove = initial - moving
            if (postMove > maximumHeight) {
                postMove = max(initial, maximumHeight)
            }
            val moved = initial - postMove
            output[index] = postMove
            transferSum += moved
        }

        return transferSum
    }
}

@PublicApi
fun FloatArrayMatrix.erode(
    mapScale: MapScale,
    randomSeed: Long,
    easeInSteps: Int = 10,
    minSteps: Int = 200,
    maxSteps: Int = 400,
    chaosFrequency: Float = 0.1f,
    chaosPower: Float = 0.5f,
    minRadius: Float =  0.0f,
    maxRadius: Float = 1.0f,
    minVolume: Float = 0.0f,
    maxVolume: Float = 1.0f,
    minDrag: Float = 0.0f,
    maxDrag: Float = 1.0f,
    minInertia: Float = 0.0f,
    maxInertia: Float = 1.0f,
    minRandomSediment: Float = 0.0f,
    maxRandomSediment: Float = 1.0f,
    particleCount: Int = 25000
): FloatArrayMatrix {
    val output = copy()
    val normalScale = mapScale.mapSizeMeters / width
    val pixelSize: Float = mapScale.mapSizeMeters / output.width
    val (angleLookup, angleLookupFactor) = heightsToNormalizedAngles(pixelSize, 50.0f, 1024)
    val random = Random(randomSeed)
    val chaosOffsetX = random.nextFloat() * 5000.0f
    val chaosOffsetY = random.nextFloat() * 5000.0f
    (1..particleCount).inParallel(
        context = {
            ErosionParticle(
                heightMap = output,
                output = output,
                widthInMeters = mapScale.mapSizeMeters,
                normalScale = normalScale,
                angleLookup = angleLookup,
                angleLookupFactor = angleLookupFactor,
                randomSeed = randomSeed + 31L * it,
                easeInSteps = easeInSteps,
                minSteps = minSteps,
                maxSteps = maxSteps,
                chaosFrequency = chaosFrequency,
                chaosPower = chaosPower,
                chaosOffsetX = chaosOffsetX,
                chaosOffsetY = chaosOffsetY,
                minRadius = minRadius,
                maxRadius = maxRadius,
                minVolume = minVolume,
                maxVolume = maxVolume,
                minDrag = minDrag,
                maxDrag = maxDrag,
                minInertia = minInertia,
                maxInertia = maxInertia,
                minRandomSediment = minRandomSediment,
                maxRandomSediment = maxRandomSediment
            )
        }
    ) { particle, _ ->
        particle.process()
    }

    return output
}
