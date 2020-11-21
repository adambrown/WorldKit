import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import wk.api.*
import kotlin.math.abs

val projectPath = resolveProjectPath()

val cache = Cache()

val exampleSeed = 0

val mapScale = MapScale.MapScale20K
val outputWidth = 8192

val ioPath = "$projectPath/io"

val simplexPrefix = "$ioPath/simplex-"
val simplexNoiseFile = "$simplexPrefix$exampleSeed"
val simplexDisplayFile = "$simplexNoiseFile-d"

val perlinPrefix = "$ioPath/perlin-"
val perlinNoiseFile = "$perlinPrefix$exampleSeed"
val perlinDisplayFile = "$perlinNoiseFile-d"

val ridgedPrefix = "$ioPath/ridged-"
val ridgedNoiseFile = "$ridgedPrefix$exampleSeed"
val ridgedDisplayFile = "$ridgedNoiseFile-d"

val turbulencePrefix = "$ioPath/turbulence-"
val turbulenceNoiseFile = "$turbulencePrefix$exampleSeed"
val turbulenceDisplayFile = "$turbulenceNoiseFile-d"

val iqSimplexPrefix = "$ioPath/iqs-"
val iqSimplexNoiseFile = "$iqSimplexPrefix$exampleSeed"
val iqSimplexDisplayFile = "$iqSimplexNoiseFile-d"

val iqRandomPrefix = "$ioPath/iqr-"
val iqRandomNoiseFile = "$iqRandomPrefix$exampleSeed"
val iqRandomDisplayFile = "$iqRandomNoiseFile-d"

val worleyPrefix = "$ioPath/worley-"
val worleyNoiseFile = "$worleyPrefix$exampleSeed"
val worleyDisplayFile = "$worleyNoiseFile-d"

val cubicPrefix = "$ioPath/cubic-"
val cubicNoiseFile = "$cubicPrefix$exampleSeed"
val cubicDisplayFile = "$cubicNoiseFile-d"

fun noiseToTerrainDisplay(noise: Cached<ShortArrayMatrix>) = runBlocking {
    val noiseMapDeferred = async {
        val factor = 0.25f
        val min = -mapScale.waterDepthMeters
        val max = min + mapScale.heightRangeMeters * factor
        val delta = mapScale.heightRangeMeters * factor
        noise.value.await().toFloatMatrix().adjust(delta, min, min..max)
    }
    val heightMapDeferred = async {
        noiseMapDeferred.await().toStandardizedHeightMap(mapScale)
    }
    val normalMapDeferred = async { noiseMapDeferred.await().toNormalMap(mapScale) }
    val occlusionMapDeferred = async { noiseMapDeferred.await().toOcclusion(mapScale) }
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

fun simplexNoise(seed: Int) = generateNoiseMultifractal(
        randomSeed = seed.toLong(),
        octaves = 8,
        width = outputWidth,
        frequency = 8.0f,
        gain = 1.95f,
        roughness = 0.47f,
        noiseSource = NoiseSource.Simplex(),
        fractalFunction = FractalFunction.Terrain,
        noiseFunction = { v -> v }
).normalize().toShortMatrix()

fun perlinNoise(seed: Int) = generateNoiseMultifractal(
        randomSeed = seed.toLong(),
        octaves = 8,
        width = outputWidth,
        frequency = 12.0f,
        gain = 1.95f,
        roughness = 0.45f,
        noiseSource = NoiseSource.Perlin(),
        fractalFunction = FractalFunction.Terrain,
        noiseFunction = { v -> v }
).normalize().toShortMatrix()

fun ridgedNoise(seed: Int) = generateNoiseMultifractal(
        randomSeed = seed.toLong(),
        octaves = 8,
        width = outputWidth,
        frequency = 6.0f,
        gain = 1.95f,
        roughness = 0.47f,
        noiseSource = NoiseSource.Simplex(),
        fractalFunction = FractalFunction.Terrain,
        noiseFunction = { v -> 1.0f - abs(v) }
).normalize().toShortMatrix()

fun turbulenceNoise(seed: Int) = generateNoiseMultifractal(
        randomSeed = seed.toLong(),
        octaves = 8,
        width = outputWidth,
        frequency = 8.0f,
        gain = 2.0f,
        roughness = 0.4f,
        noiseSource = NoiseSource.Simplex(),
        fractalFunction = FractalFunction.Terrain,
        noiseFunction = { v -> abs(v) }
).normalize().toShortMatrix()

fun iqSimplexNoise(seed: Int) = generateNoiseIQ(
        octaves = 16,
        width = outputWidth,
        scale = 10.0f / outputWidth,
        basis = IqNoiseBasis.Simplex(seed.toLong(), 256, 500.0f)
).normalize().toShortMatrix()

fun iqRandomNoise(seed: Int) = generateNoiseIQ(
        octaves = 16,
        width = outputWidth,
        scale = 8.0f / outputWidth,
        basis = IqNoiseBasis.Random(seed.toLong(), 256)
).normalize().toShortMatrix()

fun worleyNoise(seed: Int) = generateNoiseWorley(
        randomSeed = seed.toLong(),
        octaves = 10,
        width = outputWidth,
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
        octaves = 8,
        width = outputWidth,
        roughness = 0.425f,
        pointStride = 45,
        pointConstraint = 0.9f,
        fractalFunction = FractalFunction.Terrain,
        noiseFunction = { v -> v }
).normalize().toShortMatrix()

@Executable
fun clearCache() = cache.clear()

@Executable
fun generateSimplexNoise() {
    (1..10).inParallel {
        simplexNoise(it).writeGrayU16("$simplexPrefix$it")
    }
}

@Executable
fun generatePerlinNoise() {
    (1..10).inParallel {
        perlinNoise(it).writeGrayU16("$perlinPrefix$it")
    }
}

@Executable
fun generateRidgedNoise() {
    (1..10).inParallel {
        ridgedNoise(it).writeGrayU16("$ridgedPrefix$it")
    }
}

@Executable
fun generateTurbulenceNoise() {
    (1..10).inParallel {
        turbulenceNoise(it).writeGrayU16("$turbulencePrefix$it")
    }
}

@Executable
fun generateIqSimplexNoise() {
    (1..10).inParallel {
        iqSimplexNoise(it).writeGrayU16("$iqSimplexPrefix$it")
    }
}

@Executable
fun generateIqRandomNoise() {
    (1..10).inParallel {
        iqRandomNoise(it).writeGrayU16("$iqRandomPrefix$it")
    }
}

@Executable
fun generateWorleyNoise() {
    (1..10).inParallel {
        worleyNoise(it).writeGrayU16("$worleyPrefix$it")
    }
}

@Executable
fun generateCubicNoise() {
    (1..10).inParallel {
        cubicNoise(it).writeGrayU16("$cubicPrefix$it")
    }
}

val simplexNoise = cache.grayU16(simplexNoiseFile) { simplexNoise(exampleSeed) }

val simplexHeightMap = cache.terrainDisplayData(simplexDisplayFile) {
    noiseToTerrainDisplay(simplexNoise)
}

val perlinNoise = cache.grayU16(perlinNoiseFile) { perlinNoise(exampleSeed) }

val perlinHeightMap = cache.terrainDisplayData(perlinDisplayFile) {
    noiseToTerrainDisplay(perlinNoise)
}

val ridgedNoise = cache.grayU16(ridgedNoiseFile) { ridgedNoise(exampleSeed) }

val ridgedHeightMap = cache.terrainDisplayData(ridgedDisplayFile) {
    noiseToTerrainDisplay(ridgedNoise)
}

val turbulenceNoise = cache.grayU16(turbulenceNoiseFile) { turbulenceNoise(exampleSeed) }

val turbulenceHeightMap = cache.terrainDisplayData(turbulenceDisplayFile) {
    noiseToTerrainDisplay(turbulenceNoise)
}

val iqSimplexNoise = cache.grayU16(iqSimplexNoiseFile) { iqSimplexNoise(exampleSeed) }

val iqSimplexHeightMap = cache.terrainDisplayData(iqSimplexDisplayFile) {
    noiseToTerrainDisplay(iqSimplexNoise)
}

val iqRandomNoise = cache.grayU16(iqRandomNoiseFile) { iqRandomNoise(exampleSeed) }

val iqRandomHeightMap = cache.terrainDisplayData(iqRandomDisplayFile) {
    noiseToTerrainDisplay(iqRandomNoise)
}

val worleyNoise = cache.grayU16(worleyNoiseFile) { worleyNoise(exampleSeed) }

val worleyHeightMap = cache.terrainDisplayData(worleyDisplayFile) {
    noiseToTerrainDisplay(worleyNoise)
}

val cubicNoise = cache.grayU16(cubicNoiseFile) { cubicNoise(exampleSeed) }

val cubicHeightMap = cache.terrainDisplayData(cubicDisplayFile) {
    noiseToTerrainDisplay(cubicNoise)
}

@Output
fun simplexNoise() = runBlocking { simplexNoise.value.await().toImageDisplayData() }

@Output
fun simplexHeightMap() = runBlocking { simplexHeightMap.value.await() }

@Output
fun perlinNoise() = runBlocking { perlinNoise.value.await().toImageDisplayData() }

@Output
fun perlinHeightMap() = runBlocking { perlinHeightMap.value.await() }

@Output
fun ridgedNoise() = runBlocking { ridgedNoise.value.await().toImageDisplayData() }

@Output
fun ridgedHeightMap() = runBlocking { ridgedHeightMap.value.await() }

@Output
fun turbulenceNoise() = runBlocking { turbulenceNoise.value.await().toImageDisplayData() }

@Output
fun turbulenceHeightMap() = runBlocking { turbulenceHeightMap.value.await() }

@Output
fun iqSimplexNoise() = runBlocking { iqSimplexNoise.value.await().toImageDisplayData() }

@Output
fun iqSimplexHeightMap() = runBlocking { iqSimplexHeightMap.value.await() }

@Output
fun iqRandomNoise() = runBlocking { iqRandomNoise.value.await().toImageDisplayData() }

@Output
fun iqRandomHeightMap() = runBlocking { iqRandomHeightMap.value.await() }

@Output
fun worleyNoise() = runBlocking { worleyNoise.value.await().toImageDisplayData() }

@Output
fun worleyHeightMap() = runBlocking { worleyHeightMap.value.await() }

@Output
fun cubicNoise() = runBlocking { cubicNoise.value.await().toImageDisplayData() }

@Output
fun cubicHeightMap() = runBlocking { cubicHeightMap.value.await() }
