@file:DependsOn("Common.kt")
@file:DependsOn("TerrainProfiles.kt")
@file:DependsOn("TerraformPasses.kt")

import kotlinx.coroutines.*
import wk.api.*
import kotlin.math.pow

projectPath = resolveProjectPath()

val cache = Cache()

val landSeed = 73

val islandShapesPrefix = "$islandShapesPath/shape"
val islandShapeSourceFile = "$islandShapesPrefix.$landSeed.$baseNoiseSize"
val landShapeFile = "$ioPath/land-shape"
val landSdfFile = "$ioPath/land-sdf"
val noiseBaseFile = "$ioPath/noise-base"
val shapedNoiseFile = "$ioPath/noise-shaped"
val refinedNoiseFile = "$ioPath/noise-refined"
val refinedSdfFile = "$ioPath/sdf-refined"
val startingHeightsFile = "$ioPath/starting-heights"
val terrainAFile = "$ioPath/terrain-a"
val terrainBFile = "$ioPath/terrain-b"
val terrainCFile = "$ioPath/terrain-c"
val terrainDFile = "$ioPath/terrain-d"
val terrainEFile = "$ioPath/terrain-e"
val terrainFFile = "$ioPath/terrain-f"
val displayAFile = "$ioPath/display-a"
val displayBFile = "$ioPath/display-b"
val displayCFile = "$ioPath/display-c"
val displayDFile = "$ioPath/display-d"
val displayEFile = "$ioPath/display-e"
val displayFFile = "$ioPath/display-f"

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

fun Cached<ShortArrayMatrix>.toTerrainDisplay() = runBlocking {
    val noiseMapDeferred = async {
        val factor = 0.25f
        val min = -mapScale.waterDepthMeters
        val max = min + mapScale.heightRangeMeters * factor
        val delta = mapScale.heightRangeMeters * factor
        value.await().toFloatMatrix().adjust(delta, min, min..max)
    }
    noiseMapDeferred.await().toTerrainDisplayData()
}

fun simplex1(seed: Int) = generateNoiseMultifractal(
        randomSeed = seed.toLong(),
        octaves = 1,
        width = baseNoiseSize,
        frequency = 10.0f,
        noiseSource = NoiseSource.Simplex()
).normalize().toShortMatrix()

fun simplexNoise(seed: Int) = generateNoiseMultifractal(
        randomSeed = seed.toLong(),
        octaves = 8,
        width = baseNoiseSize,
        frequency = 20.0f,
        gain = 2.0f,
        roughness = 0.5f,
        noiseSource = NoiseSource.Simplex(),
        fractalFunction = FractalFunction.Fbm
).normalize().toShortMatrix()

fun iqSimplexNoise(seed: Int, x: Float, y: Float) = generateNoiseIQ(
        width = baseNoiseSize,
        octaves = 16,
        scale = 10.0f / baseNoiseSize,
        xOffset = x,
        yOffset = y,
        basis = IqNoiseBasis.Simplex(seed.toLong(), 256, 500.0f)
)

fun iqRandomNoise(seed: Int, x: Float, y: Float) = generateNoiseIQ(
        width = baseNoiseSize,
        octaves = 16,
        scale = 8.0f / baseNoiseSize,
        xOffset = x,
        yOffset = y,
        basis = IqNoiseBasis.Random(seed.toLong(), 256)
)

fun worleyNoise(seed: Int) = generateNoiseWorley(
        randomSeed = seed.toLong(),
        width = baseNoiseSize,
        octaves = 10,
        roughness = 0.8f,
        zJitter = 0.65f,
        pointStride = 12,
        pointConstraint = 0.96f,
        searchRadius = 3,
        distanceFunction = DistanceFunction.Euclidean,
        fractalFunction = FractalFunction.FbmTerrain,
        noiseFunction = { f1, f2, f3 -> f3 + f2 - f1 }
).normalize().toShortMatrix()

fun cubicNoise(seed: Int) = generateNoiseCubic(
        randomSeed = seed.toLong(),
        width = baseNoiseSize,
        octaves = 8,
        roughness = 0.425f,
        pointStride = 45,
        pointConstraint = 0.9f,
        fractalFunction = FractalFunction.Terrain,
        noiseFunction = { v -> v }
).normalize().toShortMatrix()

val landShapeParamsIsland = GenerateLandShapeParams(
        octaves = 8,
        width = baseNoiseSize,
        radius = 0.25f,
        radialVariance = 0.6f,
        radialFrequency = 0.6f,
        positionalVariance = 0.8f,
        positionalFrequency = 6.0f,
        cornerBias = 0.80f,
        whiteOnBlack = true,
        minIsland = 35,
        noiseSourceRadial = NoiseSource.Simplex(),
        noiseFunctionRadial = { v -> v },
        noiseSourcePositional = NoiseSource.Simplex(),
        noiseFunctionPositional = { v -> v }
)

val landShapeSource = cache.grayU8(islandShapeSourceFile) {
    generateLandShape(landSeed.toLong(), landShapeParamsIsland)
}

val refinedLandShape = cache.grayU8(landShapeFile) {
    landShapeSource.value.await().refineLandShape(
            randomSeed = 4687,
            octaves = 3,
            maskWidth = baseNoiseSize - 64,
            frameWidth = baseNoiseSize,
            frameValue = 0,
            frequency = 20.0f,
            power = 0.007f,
            blur = 4.0f,
            minIsland = 1000,
            whiteOnBlack = true
    )
}

val landSdf = cache.grayU16(landSdfFile) {
    refinedLandShape.value.await().toSdf()
}

val noiseBase = cache.grayU16(noiseBaseFile) {
    val cubicDeferred = async { cubicNoise(3462) }
    val worleyDeferred = async { worleyNoise(48905) }
    val blend1Deferred = async { simplex1(4973768) }
    val iq1Deferred = async { iqRandomNoise(23465, -1.5f, -0.5f) }
    val iq2Deferred = async { iqSimplexNoise(342727, 2.0f, -1.0f) }
    val distortionDeferredX = async { simplexNoise(24752752) }
    val distortionDeferredY = async { simplexNoise(3576937) }
    val blended1 = async {
        val cubic = cubicDeferred.await()
        val worley = worleyDeferred.await()
        cubic.blend(worley, blend1Deferred.await())
    }
    val blended2 = async {
        val iq1 = iq1Deferred.await()
        val iq2 = iq2Deferred.await()
        iq1.add(iq2).normalize().toShortMatrix()
    }
    blended1.await().blend(blended2.await(), 0.4f)
            .distortByNoise(distortionDeferredX.await(), distortionDeferredY.await(), 0.035f)
}

val shapedNoise = cache.grayU16(shapedNoiseFile) {
    val noiseBaseDeferred = async { noiseBase.value.await() }
    val landSdfDeferred = async { landSdf.value.await() }
    val shapedNoise = noiseBaseDeferred.await().applySdf(landSdfDeferred.await(), output = ShortArrayMatrix(baseNoiseSize)) { d: Float, v: Short ->
        if (d < 0.0f) {
            val blend = (1.0f + d * 5.0f).coerceIn(0.0f, 1.0f)
            val iBlend = 1.0f - blend
            underwaterCurveControl[-d].first * 32768.0f * blend + (v.toInt() and 0xFFFF) * 0.5f * iBlend
        } else {
            32768.0f + (d * 24.0f).coerceIn(0.0f, 1.0f) * ((v.toInt() and 0xFFFF) / 65535.0f).pow(1.5f) * 32767.0f
        }
    }
    shapedNoise
}

val refinedNoise = cache.grayU16(refinedNoiseFile) {
    val shapedNoiseDeferred = async { shapedNoise.value.await() }
    val distortionDeferredX = async { simplexNoise(34774475) }
    val distortionDeferredY = async { simplexNoise(3459567) }
    shapedNoiseDeferred.await().distortByNoise(distortionDeferredX.await(), distortionDeferredY.await(), 0.01f)
}

val refinedSdf = cache.grayU16(refinedSdfFile) {
    refinedNoise.value.await()
            .upSample(outputWidth)
            .applyMask(ByteArrayMatrix(outputWidth), on = { -1 }, off = { 0 })
            .toSdf()
}

val startingHeights = cache.grayF32(startingHeightsFile) {
    val factor = 0.25f
    val min = -mapScale.waterDepthMeters
    val max = min + mapScale.heightRangeMeters * factor
    val delta = mapScale.heightRangeMeters * factor
    refinedNoise.value.await().toFloatMatrix().adjust(delta, min, min..max)
}

fun fakeErosion(decomp: List<FloatArrayMatrix>, sdf: ShortArrayMatrix, areaIndex: ByteArrayMatrix, upliftMap: ShortArrayMatrix): TerraformResult {
    var result = terraformPassA2(decomp[0], sdf, areaIndex, upliftMap)
    result = terraformPassB(result.heightMap.add(decomp[1].blur(1.0)), sdf, areaIndex, upliftMap)
    result = terraformPassC(result.heightMap.add(decomp[2].blur(1.0)), sdf, areaIndex, upliftMap)
    return result
}

val terrainA = cache.terraformResult(terrainAFile) {
    val sdfDeferred = async { refinedSdf.value.await() }
    val decomp = startingHeights.value.await().decompose(3)
    val areaIndex = ByteArrayMatrix(outputWidth) { 1 }
    val upliftMap = ShortArrayMatrix(outputWidth)
    fakeErosion(decomp, sdfDeferred.await(), areaIndex, upliftMap)
}

val terrainB = cache.terraformResult(terrainBFile) {
    val sdfDeferred = async { refinedSdf.value.await() }
    val decomp = terrainA.value.await().heightMap.decompose(3)
    val areaIndex = ByteArrayMatrix(outputWidth) { 1 }
    val upliftMap = ShortArrayMatrix(outputWidth)
    fakeErosion(decomp, sdfDeferred.await(), areaIndex, upliftMap)
}

val terrainC = cache.terraformResult(terrainCFile) {
    val sdfDeferred = async { refinedSdf.value.await() }
    val decomp = terrainB.value.await().heightMap.decompose(3)
    val areaIndex = ByteArrayMatrix(outputWidth) { 1 }
    val upliftMap = ShortArrayMatrix(outputWidth)
    fakeErosion(decomp, sdfDeferred.await(), areaIndex, upliftMap)
}

val terrainD = cache.terraformResult(terrainDFile) {
    val sdfDeferred = async { refinedSdf.value.await() }
    val areaIndex = ByteArrayMatrix(outputWidth) { 1 }
    val upliftMap = ShortArrayMatrix(outputWidth)
    terraformPassD(terrainC.value.await().heightMap, sdfDeferred.await(), areaIndex, upliftMap)
}

val terrainE = cache.terraformResult(terrainEFile) {
    val sdfDeferred = async { refinedSdf.value.await() }
    val areaIndex = ByteArrayMatrix(outputWidth) { 1 }
    val upliftMap = ShortArrayMatrix(outputWidth)
    terraformPassE(terrainD.value.await().heightMap, sdfDeferred.await(), areaIndex, upliftMap)
}

val terrainF = cache.terraformResult(terrainFFile) {
    val sdfDeferred = async { refinedSdf.value.await() }
    val areaIndex = ByteArrayMatrix(outputWidth) { 1 }
    val upliftMap = ShortArrayMatrix(outputWidth)
    terraformPassF(terrainE.value.await().heightMap, sdfDeferred.await(), areaIndex, upliftMap)
}

val erosionBase = cache.grayF32("$ioPath/erosionTestBase") {
    terrainF.value.await().heightMap
}

val erosion1 = cache.grayF32("$ioPath/erosionTestOutput1") {
    erosionBase.value.await()
        .blur(4.0)
        .erode(
            mapScale = MapScale.MapScale20K,
            randomSeed = 2364895,
            easeInSteps = 5,
            minSteps = 200,
            maxSteps = 300,
            chaosFrequency = 0.2f,
            chaosPower = 0.5f,
            minRadius = 0.0f,
            maxRadius = 1.0f,
            minVolume = 0.4f,
            maxVolume = 1.0f,
            minDrag = 0.2f,
            maxDrag = 0.5f,
            minInertia = 0.2f,
            maxInertia = 0.5f,
            minRandomSediment = 0.02f,
            maxRandomSediment = 0.1f,
            particleCount = 5000000
        )
        .blur(2.0)
}

val erosion2 = cache.grayF32("$ioPath/erosionTestOutput2") {
    erosion1.value.await()
        .upSample(16384)
        .erode(
            mapScale = MapScale.MapScale32K,
            randomSeed = 2364895,
            easeInSteps = 5,
            minSteps = 300,
            maxSteps = 400,
            chaosFrequency = 0.2f,
            chaosPower = 0.5f,
            minRadius = 0.0f,
            maxRadius = 1.0f,
            minVolume = 0.4f,
            maxVolume = 1.0f,
            minDrag = 0.2f,
            maxDrag = 0.5f,
            minInertia = 0.2f,
            maxInertia = 0.5f,
            minRandomSediment = 0.02f,
            maxRandomSediment = 0.1f,
            particleCount = 5000000
        )
        .blur(1.0)
}

val erosion3 = cache.grayF32("$ioPath/erosionTestOutput3") {
    erosion2.value.await()
        .blur(2.0)
        .upSample(32768)
        .erode(
            mapScale = MapScale.MapScale32K,
            randomSeed = 2364895,
            easeInSteps = 5,
            minSteps = 300,
            maxSteps = 400,
            chaosFrequency = 0.2f,
            chaosPower = 0.5f,
            minRadius = 0.0f,
            maxRadius = 1.0f,
            minVolume = 0.4f,
            maxVolume = 1.0f,
            minDrag = 0.2f,
            maxDrag = 0.5f,
            minInertia = 0.2f,
            maxInertia = 0.5f,
            minRandomSediment = 0.02f,
            maxRandomSediment = 0.1f,
            particleCount = 5000000
        )
        .blur(1.0)
        .halfRes()
}

val terrainADisplay = cache.terrainDisplayData(displayAFile) {
    terrainA.value.await().toTerrainDisplayData(mapScale)
}

val terrainBDisplay = cache.terrainDisplayData(displayBFile) {
    terrainB.value.await().toTerrainDisplayData(mapScale)
}

val terrainCDisplay = cache.terrainDisplayData(displayCFile) {
    terrainC.value.await().toTerrainDisplayData(mapScale)
}

val terrainDDisplay = cache.terrainDisplayData(displayDFile) {
    terrainD.value.await().toTerrainDisplayData(mapScale)
}

val terrainEDisplay = cache.terrainDisplayData(displayEFile) {
    terrainE.value.await().toTerrainDisplayData(mapScale)
}

val terrainFDisplay = cache.terrainDisplayData(displayFFile) {
    terrainF.value.await().toTerrainDisplayData(mapScale)
}

@Executable
fun clearCache() = cache.clear()

@Executable
fun batchGenerateIslandShapes() = batchGenerateLandShapes(
        startSeed = 0,
        count = 400,
        outFilePrefix = islandShapesPrefix,
        params = landShapeParamsIsland
)

@Output
fun landShapeSource() = runBlocking { landShapeSource.value.await().toImageDisplayData() }

@Output
fun refinedLandShape() = runBlocking { refinedLandShape.value.await().toImageDisplayData() }

@Output
fun landSdf() = runBlocking { landSdf.value.await().toImageDisplayData() }

@Output
fun noiseBase() = runBlocking { noiseBase.value.await().toImageDisplayData() }

@Output
fun noiseBaseHeight() = runBlocking { noiseBase.toTerrainDisplay() }

@Output
fun shapedNoise() = runBlocking { shapedNoise.value.await().toImageDisplayData() }

@Output
fun shapedNoiseHeight() = runBlocking { shapedNoise.toTerrainDisplay() }

@Output
fun refinedNoise() = runBlocking { refinedNoise.value.await().toImageDisplayData() }

@Output
fun refinedNoiseHeight() = runBlocking { refinedNoise.toTerrainDisplay() }

@Output
fun refinedSdf() = runBlocking { refinedSdf.value.await().toImageDisplayData() }

@Output
fun terrainA() = runBlocking { terrainADisplay.value.await() }

@Output
fun terrainB() = runBlocking { terrainBDisplay.value.await() }

@Output
fun terrainC() = runBlocking { terrainCDisplay.value.await() }

@Output
fun terrainD() = runBlocking { terrainDDisplay.value.await() }

@Output
fun terrainE() = runBlocking { terrainEDisplay.value.await() }

@Output
fun terrainF() = runBlocking { terrainFDisplay.value.await() }

@Executable
fun clearErosion1() {
    erosion1.evict()
}

@Executable
fun clearErosion2() {
    erosion2.evict()
}

@Executable
fun clearErosion3() {
    erosion3.evict()
}

@Output
fun erosion1() = runBlocking { erosion1.value.await().toTerrainDisplayData() }

@Output
fun erosion2() = runBlocking { erosion2.value.await().toTerrainDisplayData() }

@Output
fun erosion3() = runBlocking { erosion3.value.await().toTerrainDisplayData() }
