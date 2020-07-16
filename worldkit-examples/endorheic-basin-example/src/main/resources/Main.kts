@file:DependsOn("Common.kt")
@file:DependsOn("TerrainProfiles.kt")
@file:DependsOn("TerraformPasses.kt")

import kotlinx.coroutines.*
import wk.api.*

projectPath = resolveProjectPath()

val cache = Cache()

val basinSeed = 34
val areaSeed = 4
val lakeSeed = 1004

val basinShapesPrefix = "$basinShapesPath/shape"
val basinShapeSourceFile = "$basinShapesPrefix.$basinSeed.$baseNoiseSize"
val lakeShapesPrefix = "$lakeShapesPath/shape"
val lakeShapeSourceFile = "$lakeShapesPrefix.$lakeSeed.$baseNoiseSize"
val basinShapeFile = "$ioPath/basin-shape"
val basinSdfFile = "$ioPath/basin-sdf"
val landShapeFile = "$ioPath/land-shape"
val landSdfFile = "$ioPath/land-sdf"
val areaShapesPrefix = "$areaShapesPath/areas"
val areaIndexSourceFile = "$areaShapesPrefix.$areaSeed.$outputWidth"
val areaIndexFile = "$ioPath/area-index"
val upliftMapFile = "$ioPath/uplift-map"
val startingHeightsFile = "$ioPath/starting-heights"
val terraformFileBase = "$ioPath/terraform"
val terraformAFile = "$terraformFileBase-a"
val terraformBFile = "$terraformFileBase-b"
val terraformCFile = "$terraformFileBase-c"
val terraformDFile = "$terraformFileBase-d"
val terraformEFile = "$terraformFileBase-e"
val terraformFFile = "$terraformFileBase-f"
val displayPreviewFile = "$ioPath/preview"
val displayFile = "$ioPath/terrain"

fun FloatArrayMatrix.toTerrainDisplayData() = runBlocking {
    val heightMapDeferred = async {
        toStandardizedHeightMap(mapScale)
    }
    val normalMapDeferred = async { toNormalMap(mapScale) }
    val occlusionMapDeferred = async { toOcclusion(mapScale) }
    val (heightMap, normalizedScale) = heightMapDeferred.await()
    TerrainDisplayData(
            mapScale = mapScale,
            normalizedScaleFactor = normalizedScale,
            heightMap = heightMap,
            normalMap = normalMapDeferred.await(),
            occlusionMap = occlusionMapDeferred.await(),
            riverLines = emptyList()
    )
}

val basinShapeParams = GenerateLandShapeParams(
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

val lakeShapeParams = GenerateLandShapeParams(
        octaves = 6,
        width = baseNoiseSize,
        radius = 0.045f,
        radialVariance = 0.7f,
        radialFrequency = 0.3f,
        positionalVariance = 10.0f,
        positionalFrequency = 8.0f,
        cornerBias = 0.9f,
        whiteOnBlack = false,
        minIsland = 50,
        noiseSourceRadial = NoiseSource.Simplex(),
        noiseFunctionRadial = { v -> v },
        noiseSourcePositional = NoiseSource.Simplex(),
        noiseFunctionPositional = { v -> v - 0.2f }
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

val basinShapeSource = cache.grayU8(basinShapeSourceFile) {
    generateLandShape(basinSeed.toLong(), basinShapeParams)
}

val lakeShapeSource = cache.grayU8(lakeShapeSourceFile) {
    generateLandShape(lakeSeed.toLong(), lakeShapeParams)
}

val areaIndexSource = cache.grayU8(areaIndexSourceFile) {
    generateRandomAreaIndex(areaSeed.toLong(), areaIndexParams)
}

val basinShape = cache.grayU8(basinShapeFile) {
    val refinedShape = basinShapeSource.value.await().refineLandShape(
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
    refinedShape.removeSmallIslands(1000000)
    refinedShape
}

val basinSdf = cache.grayU16(basinSdfFile) {
    basinShape.value.await().toSdf()
}

val landShape = cache.grayU8(landShapeFile) {
    basinShapeSource.value.await()
            .applyMask(lakeShapeSource.value.await(), off = { -1 }, output = ByteArrayMatrix(baseNoiseSize))
            .refineLandShape(
                    randomSeed = 23574,
                    octaves = 2,
                    maskWidth = outputWidth - 10,
                    frameWidth = outputWidth,
                    frameValue = 0,
                    frequency = 25.0f,
                    power = 0.01f,
                    blur = 3.0f,
                    minIsland = 800,
                    whiteOnBlack = false
            )
}

val landSdf = cache.grayU16(landSdfFile) {
    landShape.value.await().toSdf()
}

val areaIndex = cache.grayU8(areaIndexFile) {
    val areaIndex = areaIndexSource.value.await().copy().compressIndex(true)
    basinShape.value.await().applyMask(areaIndex, off = { 8 })
    basinSdf.value.await().applyMask(areaIndex, threshold = 32_000, off = { 1 })
    val frame = ByteArrayMatrix(outputWidth - 256).frame(outputWidth, -1)
    frame.applyMask(areaIndex, on = { 8 }, off = { it })
    landSdf.value.await().applyMask(areaIndex, 32_760)
}

val upliftMap = cache.grayU16(upliftMapFile) {
    buildUpliftMap(outputWidth, areaIndex.value.await(), terrainProfiles)
}

val startingHeights = cache.grayF32(startingHeightsFile) {
    buildStartingHeights(
            randomSeed = 228546538,
            outputWidth = getFlowGraphWidth(0),
            mapScale = mapScale,
            landSdf = landSdf.value.await(),
            areaIndex = areaIndex.value.await(),
            upliftMap = upliftMap.value.await(),
            terrainProfiles = terrainProfiles,
            underwaterProfile = underwaterProfile,
            landCurve = landCurveControl,
            underwaterCurve = underwaterCurveControl
    )
}

data class TerraformInputs(val inputHeights: FloatArrayMatrix, val landSdf: ShortArrayMatrix, val areaIndex: ByteArrayMatrix, val upliftMap: ShortArrayMatrix)

suspend fun terraformInputs(getInputHeights: suspend () -> FloatArrayMatrix): TerraformInputs {
    val landSdf = landSdf.value
    val areaIndex = areaIndex.value
    val upliftMap = upliftMap.value
    return TerraformInputs(getInputHeights(), landSdf.await(), areaIndex.await(), upliftMap.await())
}

val terraformA = cache.terraformResult(terraformAFile) {
    val inputs = terraformInputs { startingHeights.value.await() }
    terraformPassA(inputs.inputHeights, inputs.landSdf, inputs.areaIndex, inputs.upliftMap)
}

val terraformB = cache.terraformResult(terraformBFile) {
    val inputs = terraformInputs { terraformA.value.await().heightMap }
    terraformPassB(inputs.inputHeights, inputs.landSdf, inputs.areaIndex, inputs.upliftMap)
}

val terraformC = cache.terraformResult(terraformCFile) {
    val inputs = terraformInputs { terraformB.value.await().heightMap }
    terraformPassC(inputs.inputHeights, inputs.landSdf, inputs.areaIndex, inputs.upliftMap)
}

val terraformD = cache.terraformResult(terraformDFile) {
    val inputs = terraformInputs { terraformC.value.await().heightMap }
    terraformPassD(inputs.inputHeights, inputs.landSdf, inputs.areaIndex, inputs.upliftMap)
}

val terraformE = cache.terraformResult(terraformEFile) {
    val inputs = terraformInputs { terraformD.value.await().heightMap }
    terraformPassE(inputs.inputHeights, inputs.landSdf, inputs.areaIndex, inputs.upliftMap)
}

val terraformF = cache.terraformResult(terraformFFile) {
    val inputs = terraformInputs { terraformE.value.await().heightMap }
    terraformPassF(inputs.inputHeights, inputs.landSdf, inputs.areaIndex, inputs.upliftMap)
}

val preview = cache.terrainDisplayData(displayPreviewFile) {
    terraformD.value.await().toTerrainDisplayData(mapScale)
}

val terrain = cache.terrainDisplayData(displayFile) {
    terraformF.value.await().toTerrainDisplayData(mapScale)
}

@Executable
fun clearCache() = cache.clear()

@Executable
fun batchGenerateLandShapes() {
    batchGenerateLandShapes(
            startSeed = 0,
            count = 400,
            outFilePrefix = basinShapesPrefix,
            params = basinShapeParams
    )
}

@Executable
fun batchGenerateLakeShapes() {
    batchGenerateLandShapes(
            startSeed = 1000,
            count = 400,
            outFilePrefix = lakeShapesPrefix,
            params = lakeShapeParams
    )
}

@Executable
fun batchGenerateAreaIndices() {
    batchGenerateRandomAreaIndices(
            startSeed = 0,
            count = 20,
            outFilePrefix = areaShapesPrefix,
            params = areaIndexParams
    )
}

@Output
fun basinShapeSource() = runBlocking { basinShapeSource.value.await().toImageDisplayData() }

@Output
fun lakeShapeSource() = runBlocking { lakeShapeSource.value.await().toImageDisplayData() }

@Output
fun areaIndexSource() = runBlocking { areaIndexSource.value.await().copy().compressIndex(true).toIndexDisplayData() }

@Output
fun basinShape() = runBlocking { basinShape.value.await().toImageDisplayData() }

@Output
fun landShape() = runBlocking { landShape.value.await().toImageDisplayData() }

@Output
fun basinSdf() = runBlocking { basinSdf.value.await().toImageDisplayData() }

@Output
fun landSdf() = runBlocking { landSdf.value.await().toImageDisplayData() }

@Output
fun areaIndex() = runBlocking { areaIndex.value.await().toIndexDisplayData() }

@Output
fun upliftMap() = runBlocking { upliftMap.value.await().toImageDisplayData() }

@Output
fun startingHeights() = runBlocking { startingHeights.value.await().toTerrainDisplayData() }

@Output
fun terrainPreview() = runBlocking { preview.value.await() }

@Output
fun previewHeights() = runBlocking { preview.value.await().heightMap.toImageDisplayData() }

@Output
fun previewNormals() = runBlocking { preview.value.await().normalMap.toImageDisplayData() }

@Output
fun previewOcclusion() = runBlocking { preview.value.await().occlusionMap.toImageDisplayData() }

@Output
fun terrain() = runBlocking { terrain.value.await() }

@Output
fun terrainHeights() = runBlocking { terrain.value.await().heightMap.toImageDisplayData() }

@Output
fun terrainNormals() = runBlocking { terrain.value.await().normalMap.toImageDisplayData() }

@Output
fun terrainOcclusion() = runBlocking { terrain.value.await().occlusionMap.toImageDisplayData() }
