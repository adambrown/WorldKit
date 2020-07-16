@file:DependsOn("Common.kt")

import wk.api.*

fun noiseTexture(name: String, generator: () -> ShortArrayMatrix): ObservableMutableReference<ShortArrayMatrix> {
    return after(::inputsPath) {
        readOrCacheGrayU16("$terrainProfilesPath/$name") {
            readOrCacheGrayU16("$inputsPath/$name") { generator() }
        }
    }
}

val seaFloorNoise by noiseTexture("sea-floor-noise") {
    generateNoiseWorley(
            randomSeed = 945352357,
            width = 1024,
            octaves = 6,
            roughness = 0.8f,
            zJitter = 0.5f,
            pointStride = 32,
            pointConstraint = 0.95f,
            searchRadius = 2,
            distanceFunction = DistanceFunction.Euclidean,
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = { f1 -> -f1 }
    ).normalize()
            .toShortMatrix()
}

fun controlValues(name: String, generator: () -> List<ControlValues>): ObservableMutableReference<List<ControlValues>> {
    return after(::inputsPath) {
        readOrCacheGrayU8("$terrainProfilesPath/$name") {
            readOrCacheGrayU8("$inputsPath/$name") {
                generator().toTexture()
            }.toControlValuesCleanup().toTexture()
        }.toControlValues()
    }
}

val landAngles by controlValues("land-angles") {
    buildLinearTalusAngles(25.0f, 12.0f, 5.0f)
}

val seaFloorAngles by controlValues("sea-floor-angles") {
    buildLinearTalusAngles(25.0f, -12.0f, 0.0f)
}

val beachShelfMinControl by controlValues("beach-shelf-min") {
    buildCubicControlValues(0.01f, 0.0f)
}

val beachShelfMaxControl by controlValues("beach-shelf-max") {
    buildCubicControlValues(0.07f, 0.0f)
}

val underwaterCurveControl by controlValues("underwater-curve") {
    buildLogisticControlValues(60.0f, -1.0f, 0.0308f, 0.1f, 0.0005f)
}

val terrainProfile by ref {
    TerrainProfile(
            talusAngles = landAngles,
            upliftConstant = 0.0f,
            upliftNoise = null,
            heightScale = 1.0f,
            erosionSettings = listOf(
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 1.0f),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 1.0f),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 0.07f)))
}

val underwaterProfile by ref {
    UnderwaterProfile(
            talusAngles = seaFloorAngles,
            seaFloorNoise = seaFloorNoise,
            heightScale = 1.0f,
            erosionPowers = listOf(0.13f, 0.10f, 0.08f))
}

val terrainProfiles by after(::inputsPath) { listOf(terrainProfile) }
