@file:DependsOn("Common.kt")
@file:DependsOn("TerrainProfiles.kt")
@file:DependsOn("TerraformPasses.kt")

import kotlinx.coroutines.*
import wk.api.*

projectPath = resolveProjectPath()

val cache = Cache()

val landSeed = 34
val areaSeed = 4

val islandShapesPrefix = "$landShapesPath/shape"
val islandShapeSourceFile = "$islandShapesPrefix.$landSeed.$baseNoiseSize"
val landShapeFile = "$ioPath/land-shape"
val landSdfFile = "$ioPath/land-sdf"
val areaShapesPrefix = "$areaShapesPath/areas"
val areaIndexSourceFile = "$areaShapesPrefix.$areaSeed.$outputWidth"
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
    landSdf.value.await()
            .applyMask(areaIndexSource.value.await(), 32_760, output = ByteArrayMatrix(outputWidth))
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
    terraformPassE(terraformD.value.await().heightMap.blur(2.0), landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

val terraformF = cache.terraformResult("$terraformFilePrefix-f") {
    terraformPassF(terraformE.value.await().heightMap.blur(2.0), landSdf.value.await(), areaIndex.value.await(), upliftMap.value.await())
}

fun FloatArrayMatrix.toTerrainDisplayData() = runBlocking {
    val displayScale = MapScale.MapScale1K
    val heightMapDeferred = async {
        toStandardizedHeightMap(displayScale)
    }
    val normalMapDeferred = async { toNormalMap(displayScale) }
    val occlusionMapDeferred = async { toOcclusion(displayScale) }
    val (heightMap, normalizedScale) = heightMapDeferred.await()
    TerrainDisplayData(
            mapScale = displayScale,
            normalizedScaleFactor = normalizedScale,
            heightMap = heightMap,
            normalMap = normalMapDeferred.await(),
            occlusionMap = occlusionMapDeferred.await(),
            riverLines = emptyList()
    )
}

val erosionBase = cache.grayF32("$ioPath/erosionTestBase") {
    terraformF.value.await()
        .heightMap
        .copySlice(4911, 3953, 512, 512)
        .adjust(
            0.8f,
            0.0f,
            -MapScale.MapScale1K.waterDepthMeters..MapScale.MapScale1K.heightRangeMeters - MapScale.MapScale1K.waterDepthMeters
        )
}

val erosionBaseDisplay = cache.terrainDisplayData("$ioPath/erosionTestBaseDisplay") {
    erosionBase.value.await().toTerrainDisplayData()
}

val erosionOutput = cache.grayF32("$ioPath/erosionTestOutput") {
    erosionBase.value.await()
        .blur(0.75)
        .erode(
            mapScale = MapScale.MapScale1K,
            randomSeed = 2364895,
            easeInSteps = 5,
            minSteps = 50,
            maxSteps = 100,
            chaosFrequency = 0.07f,
            chaosPower = 0.7f,
            minRadius = 0.0f,
            maxRadius = 0.7f,
            minVolume = 0.2f,
            maxVolume = 0.7f,
            minDrag = 0.2f,
            maxDrag = 0.8f,
            minInertia = 0.2f,
            maxInertia = 0.6f,
            minRandomSediment = 0.02f,
            maxRandomSediment = 0.1f,
            particleCount = 25000
        )
        .blur(1.0)
        .upSample(1024)
        .erode(
            mapScale = MapScale.MapScale2K,
            randomSeed = 26484462,
            easeInSteps = 10,
            minSteps = 80,
            maxSteps = 120,
            chaosFrequency = 0.08f,
            chaosPower = 0.6f,
            minRadius = 0.0f,
            maxRadius = 0.8f,
            minVolume = 0.1f,
            maxVolume = 0.7f,
            minDrag = 0.3f,
            maxDrag = 0.8f,
            minInertia = 0.4f,
            maxInertia = 0.6f,
            minRandomSediment = 0.01f,
            maxRandomSediment = 0.04f,
            particleCount = 50000
        )
        .blur(1.0)
        .upSample(2048)
        .erode(
            mapScale = MapScale.MapScale2K,
            randomSeed = 6354845,
            easeInSteps = 10,
            minSteps = 90,
            maxSteps = 140,
            chaosFrequency = 0.08f,
            chaosPower = 0.7f,
            minRadius = 0.3f,
            maxRadius = 1.0f,
            minVolume = 0.1f,
            maxVolume = 0.3f,
            minDrag = 0.3f,
            maxDrag = 0.6f,
            minInertia = 0.3f,
            maxInertia = 0.7f,
            minRandomSediment = 0.005f,
            maxRandomSediment = 0.02f,
            particleCount = 100000
        )
        .blur(1.0)
        .halfRes()
}

@Executable
fun clearErosion() {
    erosionOutput.evict()
}

@Output
fun erosionBase() = runBlocking { erosionBaseDisplay.value.await() }

@Output
fun erosionOutput() = runBlocking { erosionOutput.value.await().toTerrainDisplayData() }

@Executable
fun exportHeightMap() = runBlocking {
    erosionOutput.value.await().halfRes().normalize().toShortMatrix().writeGrayU16("$ioPath/heightMap")
}













