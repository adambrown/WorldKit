@file:DependsOn("Common.kt")
@file:DependsOn("TerrainProfiles.kt")
@file:DependsOn("TerraformPasses.kt")

import kotlinx.coroutines.*
import wk.api.*

projectPath = resolveProjectPath()

val landSeed = 45
val areaSeed = 2

val islandShapesPrefix = "$islandShapesPath/shape"
val islandShapeSourceFile = "$islandShapesPrefix.$landSeed.$baseNoiseSize"
val areaShapesPrefix = "$areaShapesPath/areas"
val areaIndexSourceFile = "$areaShapesPrefix.$areaSeed.$outputWidth"

val areaIndexSource by ref {
    readOrCacheGrayU8(areaIndexSourceFile) {
        generateRandomAreaIndex(
                randomSeed = areaSeed.toLong(),
                params = GenerateRandomAreaIndexParams(
                        width = outputWidth,
                        frequency1 = 60.0f,
                        power1 = 0.007f,
                        frequency2 = 20.0f,
                        power2 = 0.012f,
                        types = listOf(
                                AreaType(0.001f, 2.4f),
                                AreaType(0.0013f, 0.75f),
                                AreaType(0.0011f, 1.8f),
                                AreaType(0.0013f, 1.0f),
                                AreaType(0.002f, 0.75f),
                                AreaType(0.0009f, 0.25f),
                                AreaType(0.0009f, 0.25f)
                        )
                )
        )
    }
}

val landShapeSource by ref {
    readOrCacheGrayU8(islandShapeSourceFile) {
        generateLandShape(
                seed = landSeed.toLong(),
                params = GenerateLandShapeParams(
                        octaves = 6,
                        width = baseNoiseSize,
                        radius = 0.3f,
                        radialVariance = 0.73f,
                        radialFrequency = 0.55f,
                        positionalVariance = 0.9f,
                        positionalFrequency = 12.3f,
                        cornerBias = 0.85f,
                        whiteOnBlack = true,
                        minIsland = 35,
                        noiseSourceRadial = NoiseSource.Simplex(),
                        noiseFunctionRadial = { v -> v },
                        noiseSourcePositional = NoiseSource.Worley(0.5f, 2, DistanceFunction.Euclidean) { f1, f2, _ -> f2 - f1 },
                        noiseFunctionPositional = { v -> v - 0.5f }
                )
        )
    }
}

fun generateTerrain(isPreview: Boolean) = runBlocking {
    val areaIndexSourceDeferred = async { areaIndexSource }
    val refinedLandShapeDeferred = async {
        landShapeSource.refineLandShape(
                randomSeed = 352,
                octaves = 2,
                maskWidth = maskWidth,
                frameWidth = outputWidth,
                frameValue = 0,
                frequency = 25.0f,
                power = 0.01f,
                blur = 3.0f,
                minIsland = 800,
                whiteOnBlack = true
        )
    }
    val landSdfDeferred = async { refinedLandShapeDeferred.await().toSdf() }
    val areaIndexDeferred = async {
        landSdfDeferred.await()
                .applyMask(areaIndexSourceDeferred.await(), 32_760)
                .compressIndex(true)
    }
    val upliftMapDeferred = async {
        buildUpliftMap(outputWidth, areaIndexDeferred.await(), terrainProfiles)
    }
    val landSdf = landSdfDeferred.await()
    val areaIndex = areaIndexDeferred.await()
    val upliftMap = upliftMapDeferred.await()
    val startingHeights = buildStartingHeights(
            randomSeed = 235276732,
            outputWidth = getFlowGraphWidth(0),
            mapScale = mapScale,
            landSdf = landSdf,
            areaIndex = areaIndex,
            upliftMap = upliftMap,
            terrainProfiles = terrainProfiles,
            underwaterProfile = underwaterProfile,
            landCurve = landCurveControl,
            underwaterCurve = underwaterCurveControl
    )
    var heightMap = terraformPassA(startingHeights, landSdf, areaIndex, upliftMap).heightMap
    heightMap = terraformPassB(heightMap, landSdf, areaIndex, upliftMap).heightMap
    heightMap = terraformPassC(heightMap, landSdf, areaIndex, upliftMap).heightMap
    var output = terraformPassD(heightMap, landSdf, areaIndex, upliftMap)
    if (!isPreview) {
        heightMap = terraformPassE(output.heightMap, landSdf, areaIndex, upliftMap).heightMap
        output = terraformPassF(heightMap, landSdf, areaIndex, upliftMap)
    }
    output.toTerrainDisplayData(mapScale)
}

@Output
fun preview() = generateTerrain(isPreview = true)

@Output
fun terrain() = generateTerrain(isPreview = false)
