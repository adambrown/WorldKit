@file:DependsOn("Common.kt")

import wk.api.*

val upliftNoiseSize by cRef(1024)

fun noiseTexture(name: String, generator: () -> ShortArrayMatrix): ObservableMutableReference<ShortArrayMatrix> {
    return after(::inputsPath) {
        readOrCacheGrayU16("$terrainProfilesPath/$name") {
            readOrCacheGrayU16("$inputsPath/$name") { generator() }
        }
    }
}

val mountainsNoise by noiseTexture("mountains-noise") {
    generateNoiseWorley(
            randomSeed = 28592462,
            width = upliftNoiseSize,
            octaves = 6,
            roughness = 0.8f,
            zJitter = 0.5f,
            pointStride = 64,
            pointConstraint = 0.95f,
            searchRadius = 2,
            distanceFunction = DistanceFunction.Euclidean,
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = { f1 -> -f1 }
    ).normalize()
            .step(24)
            .adjust(0.17f, 0.01f, 0.0f..1.0f)
            .toShortMatrix()
}

val coastalMountainsNoise by noiseTexture("coastal-mountains-noise") {
    generateNoiseWorley(
            randomSeed = 2346346,
            width = upliftNoiseSize,
            octaves = 6,
            roughness = 0.7f,
            zJitter = 0.5f,
            pointStride = 54,
            pointConstraint = 0.95f,
            searchRadius = 2,
            distanceFunction = DistanceFunction.Euclidean,
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = { f1 -> -f1 }
    ).normalize()
            .step(32)
            .adjust(0.36f, 0.04f, 0.0f..1.0f)
            .toShortMatrix()
}

val foothillsNoise by noiseTexture("foothills-noise") {
    generateNoiseWorley(
            randomSeed = 3465375,
            width = upliftNoiseSize,
            octaves = 1,
            roughness = 0.5f,
            zJitter = 1.0f,
            pointStride = 256,
            pointConstraint = 0.98f,
            searchRadius = 3,
            distanceFunction = DistanceFunction.Euclidean,
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = { f1, f2 -> f2 - f1 }
    ).normalize()
            .levels(0.0f..0.6f, 0.44f)
            .step(16)
            .adjust(0.137f, 0.004f, 0.0f..1.0f)
            .toShortMatrix()
}

val rollingHillsNoise by noiseTexture("rolling-hills-noise") {
    generateNoiseWorley(
            randomSeed = 4533446,
            width = upliftNoiseSize,
            octaves = 1,
            roughness = 0.5f,
            zJitter = 1.0f,
            pointStride = 256,
            pointConstraint = 0.98f,
            searchRadius = 3,
            distanceFunction = DistanceFunction.Euclidean,
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = { f1 -> -f1 }
    ).normalize()
            .levels(0.35f..0.95f, 0.8f)
            .step(8)
            .adjust(0.049f, 0.005f, 0.0f..1.0f)
            .toShortMatrix()
}

val plainsNoise by noiseTexture("plains-noise") {
    generateNoiseWorley(
            randomSeed = 528694962,
            width = upliftNoiseSize,
            octaves = 2,
            roughness = 0.3f,
            zJitter = 0.5f,
            pointStride = 64,
            pointConstraint = 0.98f,
            searchRadius = 2,
            distanceFunction = DistanceFunction.Euclidean,
            fractalFunction = FractalFunction.Fbm,
            noiseFunction = { f1 -> -f1 }
    ).normalize()
            .levels(0.36f..0.96f, 0.67f)
            .step(8)
            .adjust(0.0036f, 0.0002f, 0.0f..1.0f)
            .toShortMatrix()
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

val mountainsAngles by controlValues("mountains-angles") {
    buildLinearTalusAngles(30.0f, 15.0f, 5.0f)
}

val coastalMountainsAngles by controlValues("coastal-mountains-angles") {
    buildLinearTalusAngles(25.0f, 20.0f, 3.0f)
}

val foothillsAngles by controlValues("foothills-angles") {
    buildLinearTalusAngles(20.0f, 20.0f, 2.0f)
}

val rollingHillsAngles by controlValues("rolling-hills-angles") {
    buildLinearTalusAngles(16.0f, -15.5f, 3.0f)
}

val plainsAngles by controlValues("plains-angles") {
    buildLinearTalusAngles(4.0f, -3.5f, 3.0f)
}

val lowPlateauAngles by controlValues("low-plateau-angles") {
    buildSplitTalusAngles(45.0f, -43.0f, 0.3f, 2.0f, 10.0f, 3.0f)
}

val highPlateauAngles by controlValues("high-plateau-angles") {
    buildSplitTalusAngles(45.0f, -43.0f, 0.3f, 2.0f, 10.0f, 4.0f)
}

val seaFloorAngles by controlValues("sea-floor-angles") {
    buildLinearTalusAngles(30.0f, -15.0f, 0.0f)
}

val beachShelfMinControl by controlValues("beach-shelf-min") {
    buildCubicControlValues(0.01f, 0.0f)
}

val beachShelfMaxControl by controlValues("beach-shelf-max") {
    buildCubicControlValues(0.07f, 0.0f)
}

val landCurveControl by controlValues("land-curve") {
    buildLinearControlValues(0.004f, 0.0015f, 0.0005f)
}

val underwaterCurveControl by controlValues("underwater-curve") {
    buildLogisticControlValues(60.0f, -1.0f, 0.0308f, 0.1f, 0.0005f)
}

val mountainsProfile by ref {
    TerrainProfile(
            talusAngles = mountainsAngles,
            upliftConstant = 0.0442f,
            upliftNoise = mountainsNoise,
            heightScale = 1.0f,
            erosionSettings = listOf(
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 1.5f),
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 1.5f),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 0.075f,
                            talusOverride = 0.65f)))
}

val coastalMountainsProfile by ref {
    TerrainProfile(
            talusAngles = coastalMountainsAngles,
            upliftConstant = 0.1355f,
            upliftNoise = coastalMountainsNoise,
            heightScale = 1.0f,
            erosionSettings = listOf(
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 2.0f),
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 2.0f),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 0.08f,
                            talusOverride = 0.55f)))
}

val rollingHillsProfile by ref {
    TerrainProfile(
            talusAngles = rollingHillsAngles,
            upliftConstant = 0.0244f,
            upliftNoise = rollingHillsNoise,
            heightScale = 10.0f,
            erosionSettings = listOf(
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 2.25f),
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 2.25f),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 0.15f,
                            talusOverride = 0.3f)))
}

val foothillsProfile by ref {
    TerrainProfile(
            talusAngles = foothillsAngles,
            upliftConstant = 0.0582f,
            upliftNoise = foothillsNoise,
            heightScale = 1.0f,
            erosionSettings = listOf(
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 2.0f),
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 2.0f),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 0.1f,
                            talusOverride = 0.5f)))
}

val plainsProfile by ref {
    TerrainProfile(
            talusAngles = plainsAngles,
            upliftConstant = 0.0458f,
            upliftNoise = plainsNoise,
            heightScale = 15.0f,
            erosionSettings = listOf(
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 1.0f),
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 1.0f),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 0.03f,
                            talusOverride = 0.08f)))
}

val lowPlateauTerraceFunc = buildRandomTerraceFunction(
        randomSeed = 25423,
        minCount = 7,
        maxCount = 7,
        easeIn = 0.05f,
        easeOut = 0.05f,
        minSpacing = 0.1f,
        maxSpacing = 0.3f,
        minCompression = 0.38f,
        maxCompression = 0.42f
)

val lowPlateauProfile by ref {
    TerrainProfile(
            talusAngles = lowPlateauAngles,
            upliftConstant = 0.035f,
            upliftNoise = null,
            heightScale = 10.0f,
            erosionSettings = listOf(
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 0.35f,
                            terraceJitter = 10.0f,
                            terraceJitterFrequency = 80.0f,
                            terraceFunction = lowPlateauTerraceFunc),
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 0.2f,
                            terraceJitter = 10.0f,
                            terraceJitterFrequency = 80.0f,
                            terraceFunction = lowPlateauTerraceFunc),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 0.03f,
                            talusOverride = 0.98f)))
}

val highPlateauTerraceFunc = buildRandomTerraceFunction(
        randomSeed = 245727,
        minCount = 12,
        maxCount = 12,
        easeIn = 0.05f,
        easeOut = 0.05f,
        minSpacing = 0.07f,
        maxSpacing = 0.35f,
        minCompression = 0.45f,
        maxCompression = 0.65f
)

val highPlateauProfile by ref {
    TerrainProfile(
            talusAngles = highPlateauAngles,
            upliftConstant = 0.04f,
            upliftNoise = null,
            heightScale = 5.0f,
            erosionSettings = listOf(
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 0.35f,
                            terraceJitter = 15.0f,
                            terraceJitterFrequency = 80.0f,
                            terraceFunction = highPlateauTerraceFunc),
                    ErosionSettings(
                            upliftPower = 1.0f,
                            erosionPower = 0.2f,
                            terraceJitter = 15.0f,
                            terraceJitterFrequency = 80.0f,
                            terraceFunction = highPlateauTerraceFunc),
                    ErosionSettings(
                            upliftPower = 0.0f,
                            erosionPower = 0.03f,
                            talusOverride = 0.98f)))
}

val underwaterProfile by ref {
    UnderwaterProfile(
            talusAngles = seaFloorAngles,
            seaFloorNoise = seaFloorNoise,
            heightScale = 1.0f,
            erosionPowers = listOf(0.15f, 0.11f, 0.08f))
}

val terrainProfiles by after(::inputsPath) {
    listOf(
            mountainsProfile,
            coastalMountainsProfile,
            foothillsProfile,
            rollingHillsProfile,
            plainsProfile,
            lowPlateauProfile,
            highPlateauProfile,
            highPlateauProfile
    )
}
