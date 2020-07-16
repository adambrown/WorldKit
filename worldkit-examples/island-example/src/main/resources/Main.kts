@file:DependsOn("Common.kt")
@file:DependsOn("TerrainProfiles.kt")
@file:DependsOn("TerraformPasses.kt")

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import wk.api.*

projectPath = resolveProjectPath()

val cache = Cache()

val landSeed = 13
val areaSeed = 12

val islandShapesPrefix = "$islandShapesPath/shape"
val islandShapeSourceFile = "$islandShapesPrefix.$landSeed.$baseNoiseSize"
val landShapeFile = "$ioPath/land-shape"
val areaShapesPrefix = "$areaShapesPath/areas"
val areaIndexSourceFile = "$areaShapesPrefix.$areaSeed.$outputWidth"
val landSdfFile = "$ioPath/land-sdf"
val areaIndexFile = "$ioPath/area-index"
val upliftMapFile = "$ioPath/uplift-map"
val startingHeightsFile = "$ioPath/starting-heights"
val terraformFilePrefix = "$ioPath/terraform"

val landShapeParamsIsland = GenerateLandShapeParams(
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

val areaIndexParams = GenerateRandomAreaIndexParams(
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

fun ByteArrayMatrix.refineLandShape() = refineLandShape(
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

fun buildStartingHeights(landSdf: ShortArrayMatrix, areaIndex: ByteArrayMatrix, upliftMap: ShortArrayMatrix) =
        buildStartingHeights(
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

val areaIndexSource = cache.grayU8(areaIndexSourceFile) {
    generateRandomAreaIndex(areaSeed.toLong(), areaIndexParams)
}

val landShapeSource = cache.grayU8(islandShapeSourceFile) {
    generateLandShape(landSeed.toLong(), landShapeParamsIsland)
}

val refinedLandShape = cache.grayU8(landShapeFile) {
    landShapeSource.value.await().refineLandShape()
}

val landSdf = cache.grayU16(landSdfFile) {
    refinedLandShape.value.await().toSdf()
}

val areaIndex = cache.grayU8(areaIndexFile) {
    landSdf.value.await().applyMask(areaIndexSource.value.await(), 32_760)
            .compressIndex(true)
}

val upliftMap = cache.grayU16(upliftMapFile) {
    buildUpliftMap(outputWidth, areaIndex.value.await(), terrainProfiles)
}

val startingHeights = cache.grayF32(startingHeightsFile) {
    buildStartingHeights(landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

val terraformA = cache.terraformResult("$terraformFilePrefix-a") {
    terraformPassA(startingHeights.value.await(), landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

val terraformB = cache.terraformResult("$terraformFilePrefix-b") {
    terraformPassB(terraformA.value.await().heightMap, landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

val terraformC = cache.terraformResult("$terraformFilePrefix-c") {
    terraformPassC(terraformB.value.await().heightMap, landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

val terraformD = cache.terraformResult("$terraformFilePrefix-d") {
    terraformPassD(terraformC.value.await().heightMap, landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

val terraformE = cache.terraformResult("$terraformFilePrefix-e") {
    terraformPassE(terraformD.value.await().heightMap, landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

val terraformF = cache.terraformResult("$terraformFilePrefix-f") {
    terraformPassF(terraformE.value.await().heightMap, landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

val preview = cache.terrainDisplayData("$ioPath/preview") {
    val displayData = terraformD.value.await().toTerrainDisplayData(mapScale)
    GlobalScope.launch(IO) { displayData.riverLines.writePolyLines("$ioPath/preview-rivers.obj", mapScale.mapSizeMeters) }
    displayData
}

val terrain = cache.terrainDisplayData("$ioPath/terrain") {
    val displayData = terraformF.value.await().toTerrainDisplayData(mapScale)
    GlobalScope.launch(IO) { displayData.riverLines.writePolyLines("$ioPath/terrain-rivers.obj", mapScale.mapSizeMeters) }
    displayData
}

@Executable
fun batchGenerateIslandShapes() = batchGenerateLandShapes(
        startSeed = 0,
        count = 400,
        outFilePrefix = islandShapesPrefix,
        params = landShapeParamsIsland
)

@Executable
fun batchGenerateAreaIndices() = batchGenerateRandomAreaIndices(
        startSeed = 0,
        count = 20,
        outFilePrefix = areaShapesPrefix,
        params = areaIndexParams
)

@Executable
fun clearCache() = cache.clear()

@Executable
fun buildPreview() = runBlocking { preview.value.await() }

@Executable
fun buildTerrain() = runBlocking { terrain.value.await() }

@Executable
fun rebuildPreview() = runBlocking {
    cache.clear()
    preview.value.await()
}

@Executable
fun rebuildTerrain() = runBlocking {
    cache.clear()
    terrain.value.await()
}

@Output
fun preview() = buildPreview()

@Output
fun terrain() = buildTerrain()

@Output
fun previewNormals() = runBlocking { preview.value.await().normalMap.toImageDisplayData() }

@Output
fun previewOcclusion() = runBlocking { preview.value.await().occlusionMap.toImageDisplayData() }

@Output
fun terrainNormals() = runBlocking { terrain.value.await().normalMap.toImageDisplayData() }

@Output
fun terrainOcclusion() = runBlocking { terrain.value.await().occlusionMap.toImageDisplayData() }

@Output
fun areaIndexSource() = runBlocking { areaIndexSource.value.await().toImageDisplayData() }

@Output
fun landShapeSource() = runBlocking { landShapeSource.value.await().toImageDisplayData() }

@Output
fun refinedLandShape() = runBlocking { refinedLandShape.value.await().toImageDisplayData() }

@Output
fun landSdf() = runBlocking { landSdf.value.await().normalize().toImageDisplayData() }

@Output
fun areaIndex() = runBlocking { areaIndex.value.await().toIndexDisplayData() }

@Output
fun upliftMap() = runBlocking { upliftMap.value.await().normalize().toImageDisplayData() }
