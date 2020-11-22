package wk.api

import cern.colt.Arrays
import cern.colt.list.LongArrayList
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.joml.SimplexNoise
import wk.api.TerraformRenderMode.Cubic
import wk.api.TerraformRenderMode.Gaussian
import wk.internal.ext.WkIntList
import wk.internal.ext.WkMutableIntList
import wk.internal.ext.intList
import wk.internal.ext.intListOf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import kotlin.random.Random

private const val SOIL_MOBILITY = 0.0000005609914f
private const val SIMPLEX_SCALE = 96.0f
private const val WATER_BIOME_ID: Byte = -1
private const val NO_BIOME_ID: Byte = -2

private val threadCount = Runtime.getRuntime().availableProcessors()

@PublicApi
class RegionData(
        val landIndex: BitMatrix<*>,
        val beach: List<Int>
)

@PublicApi
class TerraformNodeData(
        internal val nodeIndex: ByteBuffer,
        internal val landIndex: BitMatrix<*>,
        internal val coastIds: WkIntList,
        internal val borderIds: WkMutableIntList
)

@PublicApi
class TerraformResult(
        val heightMap: FloatArrayMatrix,
        val graphId: Int,
        val nodeData: TerraformNodeData
)

private class TerrainUpliftValues(
        val uplift: Short,
        val texture: ShortArrayMatrix?,
        val textureScale: Float,
        val valueScale: Float,
        val textureWidthM1: Int)

@PublicApi
fun TerraformResult.toStandardizedHeightMap(mapScale: MapScale) = heightMap.toStandardizedHeightMap(mapScale)

@PublicApi
fun TerraformResult.toOcclusionMap(mapScale: MapScale) = heightMap.toOcclusion(mapScale)

@PublicApi
fun TerraformResult.toNormalMap(mapScale: MapScale) = heightMap.toNormalMap(mapScale)


@PublicApi
fun buildUpliftMap(outputWidth: Int, areaIndex: Matrix<Byte>, terrainProfiles: List<TerrainProfile>): ShortArrayMatrix {
    val landMaskWidthInverse = 1.0f / outputWidth
    val uplifts = ArrayList<TerrainUpliftValues>()
    var count = 0
    terrainProfiles.forEach { profile ->
        taskYield()
        val textureWidthM1 = if (profile.upliftNoise != null) profile.upliftNoise.width - 1 else 1
        uplifts.add(
                TerrainUpliftValues(
                        uplift = (profile.upliftConstant * 65535.0f).roundToInt().coerceIn(0, 65535).toShort(),
                        texture = profile.upliftNoise,
                        textureScale = landMaskWidthInverse * textureWidthM1,
                        valueScale = 65535.0f,
                        textureWidthM1 = textureWidthM1))
        count++
    }
    val u16Tof = 1 / 65535.0f
    fun Short.asFloat() = (this.toInt() and 0xFFFF) * u16Tof
    val upliftMap = ShortArrayMatrix(outputWidth)
    (0 until outputWidth).toList().parallelStream().forEach { y ->
        taskYield()
        val yOff = y * outputWidth
        for (x in 0 until outputWidth) {
            val profileId = areaIndex[yOff + x] - 1
            if (profileId < 0) {
                upliftMap[yOff + x] = 100
                continue
            }
            val upliftValues = uplifts[profileId]
            if (upliftValues.texture == null) {
                upliftMap[yOff + x] = upliftValues.uplift
            } else {
                val tu = (x * upliftValues.textureScale).roundToInt().coerceIn(0, upliftValues.textureWidthM1)
                val tv = (y * upliftValues.textureScale).roundToInt().coerceIn(0, upliftValues.textureWidthM1)
                upliftMap[yOff + x] = (upliftValues.texture[tu, tv].asFloat() * upliftValues.valueScale).roundToInt().coerceIn(0, 65535).toShort()
            }
        }
    }
    return upliftMap
}

@PublicApi
fun buildStartingHeights(
        randomSeed: Long,
        outputWidth: Int,
        mapScale: MapScale,
        landSdf: Matrix<Short>,
        areaIndex: Matrix<Byte>,
        upliftMap: Matrix<Short>,
        terrainProfiles: List<TerrainProfile>,
        underwaterProfile: UnderwaterProfile,
        landCurve: List<ControlValues>,
        underwaterCurve: List<ControlValues>,
): FloatArrayMatrix {
    val regionData = regionDataFromLandSdf(landSdf)
    val maskWidth = landSdf.width
    val landMaskWidthInverse = 1.0f / maskWidth
    val underwaterStartingHeights = FloatArrayMatrix(maskWidth)
    val startingHeightsNoiseScale = landMaskWidthInverse * 512.0f
    val startingHeights = ShortArrayMatrix(maskWidth)
    val seaFloorNoise = underwaterProfile.seaFloorNoise
    val underWaterNoiseWidthM1 = seaFloorNoise.width - 1
    val underWaterNoiseScale = landMaskWidthInverse * underWaterNoiseWidthM1
    val u16ToSF = 1 / 32768.0f
    val u16ToUF = (1 / 32768.0f) * 0.25f
    fun Short.asSignedFloat() = ((this.toInt() and 0xFFFF) - 32768) * u16ToSF
    fun Short.asUnsignedFloat() = (this.toInt() and 0xFFFF) * u16ToUF
    (0 until maskWidth).toList().parallelStream().forEach { y ->
        taskYield()
        val yOff = y * maskWidth
        val vStartingHeights = y * startingHeightsNoiseScale
        val vMountain = (y * underWaterNoiseScale).roundToInt().coerceIn(0, underWaterNoiseWidthM1)
        (0 until maskWidth).forEach { x ->
            val i = yOff + x
            val uStartingHeights = x * startingHeightsNoiseScale
            val noise = SimplexNoise.noise(uStartingHeights, vStartingHeights)
            val uMountain = (x * underWaterNoiseScale).roundToInt().coerceIn(0, underWaterNoiseWidthM1)
            val sdfHeight = landSdf[i].asSignedFloat()
            val isWater = sdfHeight < 0.0
            if (isWater) {
                val (base, variance) = underwaterCurve[-sdfHeight]
                underwaterStartingHeights[i] = max((base + variance * noise), seaFloorNoise[uMountain, vMountain].asUnsignedFloat())
                startingHeights[i] = 0
            } else {
                val (base, variance) = landCurve[sdfHeight]
                startingHeights[i] = ((base + variance * noise) * 65535.0f).roundToInt().coerceIn(0, 65535).toShort()
                underwaterStartingHeights[i] = 1.0f
            }
        }
    }
    taskYield()
    val bootstrapData = bootstrapErosion(
            regionData = regionData,
            terrainProfiles = terrainProfiles,
            biomeMask = areaIndex,
            upliftMap = upliftMap,
            landStartingHeights = startingHeights,
            waterStartingHeights = underwaterStartingHeights,
            mapScale = mapScale,
            randomSeed = randomSeed,
            underwaterProfile = underwaterProfile)
    taskYield()
    return buildHeightMap(
            flowGraphId = 0,
            nodeData = bootstrapData,
            iterations = 1,
            waterIterations = 1,
            erosionSettingsIndex = 0,
            mapScale = mapScale,
            terrainProfiles = terrainProfiles,
            underwaterProfile = underwaterProfile,
            areaIndex = areaIndex,
            outputWidth = outputWidth,
            renderMode = Cubic,
            blur = 1.0f).heightMap
}

@PublicApi
enum class TerraformRenderMode {
    Cubic,
    Gaussian
}

@PublicApi
fun terraform(
        randomSeed: Long,
        mapScale: MapScale,
        terrainProfiles: List<TerrainProfile>,
        underwaterProfile: UnderwaterProfile,
        landSdf: Matrix<Short>,
        areaIndex: Matrix<Byte>,
        upliftMap: Matrix<Short>,
        flowGraphId: Int,
        previousMap: FloatArrayMatrix,
        erosionSettingsIndex: Int,
        iterations: Int,
        waterIterations: Int,
        sdfMultiplier: Float,
        sdfNoiseFrequency: Float,
        sdfNoiseAmplitude: Float,
        outputWidth: Int,
        renderMode: TerraformRenderMode,
        blur: Float,
        beachFunction: (currentValue: Float, sdfDistance: Float, waterDepthMeters: Float) -> Float): TerraformResult {
    val nodeData = timeIt(timer("terraform.prepareGraphNodes")) { prepareGraphNodes(flowGraphId, landSdf, mapScale.mapSizeMeters) }
    val random = Random(randomSeed)
    val noiseOffsetX = random.nextFloat() * 10000
    val noiseOffsetY = random.nextFloat() * 10000
    val previousMapCopy = previousMap.copy()
    val convertId = landSdf.width.toFloat() / previousMapCopy.width
    val widthInverse = 1.0f / previousMapCopy.width
    val noiseTextureScale = widthInverse * sdfNoiseFrequency
    timeIt(timer("terraform.applyBeachFunctions")) {
        (0 until previousMapCopy.width).toList().parallelStream().forEach { y ->
            taskYield()
            val yOff = y * previousMapCopy.width
            val v = y * noiseTextureScale + noiseOffsetY
            val sy = (y * convertId).roundToInt().coerceIn(0 until landSdf.width)
            val syOff = sy * landSdf.width
            (0 until previousMapCopy.width).forEach { x ->
                val i = yOff + x
                val u = x * noiseTextureScale + noiseOffsetX
                val sx = (x * convertId).roundToInt().coerceIn(0 until landSdf.width)
                val si = syOff + sx
                val sdfDistance = landSdf.sdfFloat(si)
                if (sdfDistance < 0.0f) {
                    val currentValue = previousMapCopy[i]
                    previousMapCopy[i] = beachFunction(currentValue, sdfDistance, mapScale.waterDepthMeters)
                } else {
                    previousMapCopy[i] = max(0.0f, previousMapCopy[i] + 0.0f.coerceAtLeast((sdfDistance + SimplexNoise.noise(u, v) * sdfNoiseAmplitude) * sdfMultiplier))
                }
            }
        }
    }
    timeIt(timer("terraform.applyMapsToNodes")) {
        applyMapsToNodes(
                flowGraphId = flowGraphId,
                nodeData = nodeData,
                heightMap = previousMapCopy,
                elevationPowerMask = upliftMap,
                erosionSettings = terrainProfiles.map { it.erosionSettings[erosionSettingsIndex] },
                areaIndex = areaIndex,
                waterDepthMeters = mapScale.waterDepthMeters)
    }
    return timeIt(timer("terraform.buildHeightMap")) {
        buildHeightMap(
                flowGraphId = flowGraphId,
                nodeData = nodeData,
                mapScale = mapScale,
                iterations = iterations,
                waterIterations = waterIterations,
                erosionSettingsIndex = erosionSettingsIndex,
                terrainProfiles = terrainProfiles,
                underwaterProfile = underwaterProfile,
                areaIndex = areaIndex,
                outputWidth = outputWidth,
                renderMode = renderMode,
                blur = blur
        )
    }
}

private fun buildHeightMap(
        flowGraphId: Int,
        nodeData: TerraformNodeData,
        mapScale: MapScale,
        iterations: Int,
        waterIterations: Int,
        erosionSettingsIndex: Int,
        terrainProfiles: List<TerrainProfile>,
        underwaterProfile: UnderwaterProfile,
        areaIndex: Matrix<Byte>,
        outputWidth: Int,
        renderMode: TerraformRenderMode,
        blur: Float
): TerraformResult {
    runBlocking {
        val waterErosionFuture = async {
            timeIt(timer("terraform.buildHeightMap.performErosionWater")) {
                performErosionWater(
                        flowGraphId = flowGraphId,
                        nodeData = nodeData,
                        iterations = waterIterations,
                        underwaterProfile = underwaterProfile,
                        erosionPower = underwaterProfile.erosionPowers[erosionSettingsIndex],
                        distanceScale = mapScale.mapSizeMeters)
            }
        }
        val landErosionFuture = async {
            timeIt(timer("terraform.buildHeightMap.performErosionLand")) {
                performErosionLand(
                        flowGraphId = flowGraphId,
                        nodeData = nodeData,
                        iterations = iterations,
                        terrainProfiles = terrainProfiles,
                        erosionSettings = terrainProfiles.map { it.erosionSettings[erosionSettingsIndex] },
                        distanceScale = mapScale.mapSizeMeters)
            }
        }
        timeIt(timer("terraform.buildHeightMap.performAllErosion")) {
            waterErosionFuture.join()
            landErosionFuture.join()
        }
    }
    taskYield()
    val heightMap = timeIt(timer("terraform.buildHeightMap.renderHeightMap")) {
        val map = when (renderMode) {
            Gaussian -> timeIt(timer("terraform.buildHeightMap.renderHeightMapGauss")) { renderHeightMapGauss(flowGraphId, nodeData.nodeIndex, mapScale.waterDepthMeters) }
            Cubic -> timeIt(timer("terraform.buildHeightMap.renderHeightMapCubic")) { renderHeightMapCubic(flowGraphId, nodeData.nodeIndex, mapScale.waterDepthMeters) }
        }
        taskYield()
        val upMap = timeIt(timer("terraform.buildHeightMap.upSample")) { map.upSample(outputWidth) }
        taskYield()
        timeIt(timer("terraform.buildHeightMap.blur")) { upMap.blur(blur.toDouble()) }
    }
    taskYield()
    timeIt(timer("terraform.buildHeightMap.applyTerracing")) {
        val profileExtremes = Array(terrainProfiles.size) { Pair(mRef(Float.MAX_VALUE), mRef(-Float.MAX_VALUE)) }
        val nodePtr = TerraformNode(nodeData.nodeIndex)
        nodeData.landIndex.forEachSetBit {
            nodePtr.id = it
            val (min, max) = profileExtremes[nodePtr.biome.toInt()]
            val height = nodePtr.height
            if (height < min.value) {
                min.value = height
            }
            if (height > max.value) {
                max.value = height
            }
        }
        taskYield()
        applyTerracing(heightMap, areaIndex, terrainProfiles.map { it.erosionSettings[erosionSettingsIndex] }, profileExtremes)
    }
    return TerraformResult(heightMap, flowGraphId, nodeData)
}

private fun prepareGraphNodes(flowGraphId: Int, landSdf: Matrix<Short>, distanceScale: Float): TerraformNodeData {
    val graph = getFlowGraph(flowGraphId)
    val landIndex = BitMatrix64(graph.stride)
    splitLandAndWaterIds(graph, landSdf, landIndex)
    val border = graph.getBorder()
    val coast = extractBeachFromGraphLandAndWater(graph, landIndex)
    val nodeIndex = createWaterNodes(graph, coast, border, distanceScale)
    return TerraformNodeData(nodeIndex, landIndex, intListOf(*coast.toIntArray()), intListOf(*border.toIntArray()))
}

private fun bootstrapErosion(
        regionData: RegionData,
        terrainProfiles: List<TerrainProfile>,
        biomeMask: Matrix<Byte>,
        upliftMap: Matrix<Short>,
        landStartingHeights: Matrix<Short>,
        waterStartingHeights: Matrix<Float>,
        mapScale: MapScale,
        randomSeed: Long,
        underwaterProfile: UnderwaterProfile
): TerraformNodeData {
    val graph = getFlowGraph(0)
    val borderIds = graph.getBorder()
    val nodeIndex = createWaterNodesBootstrap(
            graph = graph,
            landIndex = regionData.landIndex,
            coast = regionData.beach,
            terrainProfiles = terrainProfiles,
            areaIndex = biomeMask,
            elevationMask = upliftMap,
            landStartingHeights = landStartingHeights,
            mapScale = mapScale,
            border = borderIds,
            underwaterStartingHeights = waterStartingHeights
    )
    val random = Random(randomSeed)
    val landIndex = regionData.landIndex
    val usedWater = BitMatrix64(landIndex.width)
    borderIds.forEach { usedWater[it] = true }
    val unusedWater = BitMatrix64(landIndex.width)
    var unusedCount = 0
    taskYield()
    usedWater.forEachUnsetBit {
        if (!landIndex[it]) {
            unusedWater[it] = true
            unusedCount++
        }
    }
    taskYield()
    var lastUnusedCount = unusedCount
    val nodePtr = TerraformNode(nodeIndex)
    val otherNodePtr = TerraformNode(nodeIndex)
    var frontier1 = intList()
    var frontier2 = intList()
    while (unusedCount > 0) {
        taskYield()
        frontier1.shuffle(random)
        frontier1.forEach { id ->
            nodePtr.id = id
            graph.forEachAdjacent(id) { otherNodeId ->
                otherNodePtr.id = otherNodeId
                if (!landIndex[otherNodeId] && !usedWater[otherNodeId]) {
                    otherNodePtr.parentCode = graph.getOffsetFromAdjacentIds(otherNodeId, id)
                    frontier2.add(otherNodeId)
                    usedWater[otherNodeId] = true
                    unusedWater[otherNodeId] = false
                    unusedCount--
                }
            }
        }
        if (unusedCount > 0 && lastUnusedCount == unusedCount) {
            val newSink = makeSink(unusedWater, nodePtr)
            borderIds.add(newSink)
            frontier2.add(newSink)
            usedWater[newSink] = true
            unusedWater[newSink] = false
            unusedCount--
        }
        lastUnusedCount = unusedCount
        val tmp = frontier1
        tmp.clear()
        frontier1 = frontier2
        frontier2 = tmp
    }
    val usedLand = BitMatrix64(landIndex.width)
    regionData.beach.forEach { usedLand[it] = true }
    unusedCount = 0
    taskYield()
    usedLand.forEachUnsetBit {
        if (landIndex[it]) {
            unusedCount++
        }
    }
    taskYield()
    frontier1.clear()
    frontier1.addAll(regionData.beach)
    frontier2.clear()
    while (unusedCount > 0) {
        taskYield()
        frontier1.shuffle(random)
        frontier1.forEach { id ->
            nodePtr.id = id
            graph.forEachAdjacent(id) { otherNodeId ->
                otherNodePtr.id = otherNodeId
                if (landIndex[otherNodeId] && !usedLand[otherNodeId]) {
                    otherNodePtr.parentCode = graph.getOffsetFromAdjacentIds(otherNodeId, id)
                    frontier2.add(otherNodeId)
                    usedLand[otherNodeId] = true
                    unusedCount--
                }
            }
        }
        val tmp = frontier1
        tmp.clear()
        frontier1 = frontier2
        frontier2 = tmp
    }
    val areaScale = mapScale.mapSizeMeters * mapScale.mapSizeMeters
    computeAreas(graph, nodeIndex, borderIds, areaScale)
    taskYield()
    computeHeights(graph, nodeIndex, mapScale.mapSizeMeters, borderIds, underwaterProfile, 1.0f)
    taskYield()
    computeAreas(graph, nodeIndex, regionData.beach, areaScale)
    taskYield()
    computeHeights(graph, nodeIndex, mapScale.mapSizeMeters, regionData.beach, terrainProfiles, terrainProfiles.map { ErosionSettings(1.0f, 1.0f, 1.0f) })
    return TerraformNodeData(nodeIndex, regionData.landIndex, intListOf(*regionData.beach.toIntArray()), intListOf(*borderIds.toIntArray()))
}

private fun makeSink(unusedWater: BitMatrix<*>, nodePtr: TerraformNode): Int {
    var minId = -1
    var min = Float.MAX_VALUE
    unusedWater.forEachSetBit {
        nodePtr.id = it
        if (!nodePtr.isPinned && nodePtr.height < min) {
            minId = nodePtr.id
            min = nodePtr.height
        }
    }
    nodePtr.id = minId
    nodePtr.isExternal = true
    nodePtr.freezeExternalHeight = true
    return minId
}

private fun createWaterNodesBootstrap(
        graph: GraphLite,
        landIndex: BitMatrix<*>,
        coast: List<Int>,
        terrainProfiles: List<TerrainProfile>,
        areaIndex: Matrix<Byte>,
        elevationMask: Matrix<Short>,
        landStartingHeights: Matrix<Short>,
        mapScale: MapScale,
        border: List<Int>,
        underwaterStartingHeights: Matrix<Float>): ByteBuffer {
    val externalPoints = BitMatrix64(landIndex.width, landIndex.height)
    border.forEach {
        if (!landIndex[it]) {
            externalPoints[it] = true
        }
    }
    coast.forEach {
        if (landIndex[it]) {
            externalPoints[it] = true
        }
    }
    val areaScale = mapScale.mapSizeMeters * mapScale.mapSizeMeters
    val nodeIndex = ByteBuffer.wrap(ByteArray(graph.size * TerraformNode.SIZE))
    nodeIndex.order(ByteOrder.nativeOrder())
    val areaIndexWidth = areaIndex.width
    val areaIndexWidthM1 = areaIndexWidth - 1
    val elevationWidth = elevationMask.width
    val elevationWidthM1 = elevationWidth - 1
    val landHeightWidth = landStartingHeights.width
    val landHeightWidthM1 = landHeightWidth - 1
    val underwaterHeightWidth = underwaterStartingHeights.width
    val underwaterHeightWidthM1 = underwaterHeightWidth - 1
    (0 until landIndex.size).inParallel(
            context = { TerraformNode(nodeIndex) }
    ) { nodePtr, id ->
        if (landIndex[id]) {
            val isExternal = externalPoints[id]
            val area = graph.getArea(id) * areaScale
            val point = graph.getPoint2F(id)
            val bIndex = ((point.y * areaIndexWidthM1).roundToInt() * areaIndexWidth) + (point.x * areaIndexWidthM1).roundToInt()
            val profileId = 0.coerceAtLeast(areaIndex[bIndex].toInt() - 1)
            val profile = terrainProfiles[profileId]
            val uIndex = ((point.y * elevationWidthM1).roundToInt() * elevationWidth) + (point.x * elevationWidthM1).roundToInt()
            val elevationPower = toElevationPower(elevationMask[uIndex], profile.erosionSettings[0].upliftPower)
            val hIndex = ((point.y * landHeightWidthM1).roundToInt() * landHeightWidth) + (point.x * landHeightWidthM1).roundToInt()
            val height = ((landStartingHeights[hIndex].toInt() and 0xFFFF) / 65536.0f) * 3000.0f
            nodePtr.id = id
            nodePtr.init(
                    isExternal = isExternal,
                    isPinned = isExternal,
                    elevationPower = elevationPower,
                    height = height,
                    drainageArea = area,
                    biome = profileId.toByte())
        } else {
            val isExternal = externalPoints[id]
            val area = graph.getArea(id) * areaScale
            val point = graph.getPoint2F(id)
            val hIndex = ((point.y * underwaterHeightWidthM1).roundToInt() * underwaterHeightWidth) + (point.x * underwaterHeightWidthM1).roundToInt()
            val height = if (isExternal) 0.0f else underwaterStartingHeights[hIndex] * mapScale.waterDepthMeters
            nodePtr.id = id
            nodePtr.init(
                    isExternal = isExternal,
                    isPinned = isExternal,
                    elevationPower = 0.0f,
                    height = height,
                    drainageArea = area,
                    biome = WATER_BIOME_ID)
        }
    }
    return nodeIndex
}

private fun performErosionLand(
        flowGraphId: Int,
        nodeData: TerraformNodeData,
        iterations: Int,
        terrainProfiles: List<TerrainProfile>,
        erosionSettings: List<ErosionSettings>,
        distanceScale: Float) {

    val nodeIndex = nodeData.nodeIndex
    val starts = nodeData.coastIds
    val graph = getFlowGraph(flowGraphId)
    val lakes = intList()
    val passes = PassIndex(PARALLELISM)
    val areaScale = distanceScale * distanceScale
    for (i in 0 until iterations) {
        taskYield()
        lakes.clear()
        timeIt(timer("performErosionLand.prepareNodesAndLakes")) { prepareNodesAndLakes(graph, nodeIndex, lakes, nodeData.landIndex, starts, true) }
        taskYield()
        passes.reset(starts.size + lakes.size)
        timeIt(timer("performErosionLand.computeLakeConnections")) { computeLakeConnections(graph, lakes, nodeIndex, nodeData.landIndex, passes, starts, true) }
        taskYield()
        timeIt(timer("performErosionLand.computeAreas")) { computeAreas(graph, nodeIndex, starts, areaScale) }
        taskYield()
        timeIt(timer("performErosionLand.computeHeights")) { computeHeights(graph, nodeIndex, distanceScale, starts, terrainProfiles, erosionSettings) }
    }
}

private fun performErosionWater(
        flowGraphId: Int,
        nodeData: TerraformNodeData,
        iterations: Int,
        underwaterProfile: UnderwaterProfile,
        erosionPower: Float,
        distanceScale: Float) {

    val nodeIndex = nodeData.nodeIndex
    val starts = nodeData.borderIds
    val graph = getFlowGraph(flowGraphId)
    val lakes = intList()
    val passes = PassIndex(PARALLELISM)
    val areaScale = distanceScale * distanceScale
    for (i in 0 until iterations) {
        taskYield()
        lakes.clear()
        timeIt(timer("performErosionWater.prepareNodesAndLakes")) { prepareNodesAndLakes(graph, nodeIndex, lakes, nodeData.landIndex, starts, false) }
        taskYield()
        passes.reset(starts.size + lakes.size)
        timeIt(timer("performErosionWater.computeLakeConnections")) { computeLakeConnections(graph, lakes, nodeIndex, nodeData.landIndex, passes, starts, false) }
        taskYield()
        timeIt(timer("performErosionWater.computeAreas")) { computeAreas(graph, nodeIndex, starts, areaScale) }
        taskYield()
        timeIt(timer("performErosionWater.computeHeights")) { computeHeights(graph, nodeIndex, distanceScale, starts, underwaterProfile, erosionPower) }
    }
}


private fun createWaterNodes(graph: GraphLite, coast: List<Int>, border: List<Int>, distanceScale: Float): ByteBuffer {
    val areaScale = distanceScale * distanceScale
    val nodeIndex = ByteBuffer.wrap(ByteArray(graph.size * TerraformNode.SIZE))
    val externalNodes = BitMatrix64(graph.stride)
    coast.forEach {
        externalNodes[it] = true
    }
    border.forEach {
        externalNodes[it] = true
    }
    runBlocking {
        val nodeFutures = (0 until threadCount).map { i ->
            async {
                val ptr = TerraformNode(nodeIndex)
                for (id in i until graph.size step threadCount) {
                    val isExternal = externalNodes[id]
                    val area = graph.getArea(id) * areaScale
                    ptr.id = id
                    ptr.init(
                            isExternal = isExternal,
                            isPinned = isExternal,
                            elevationPower = 0.0f,
                            height = 0.0f,
                            drainageArea = area,
                            biome = NO_BIOME_ID)
                }
            }
        }
        nodeFutures.forEach { it.join() }
    }
    return nodeIndex
}

private fun regionDataFromLandSdf(landSdf: Matrix<Short>): RegionData {
    val graph = getFlowGraph(0)
    val landIndex = BitMatrix64(graph.stride)
    splitLandAndWaterIds(graph, landSdf, landIndex)
    val beach = extractBeachFromGraphLandAndWater(graph, landIndex)
    return RegionData(landIndex, beach)
}

private fun extractBeachFromGraphLandAndWater(graph: GraphLite, landIndex: BitMatrix<*>): List<Int> {
    val beach = intList()
    landIndex.forEachSetBit {
        if (isCoastalPoint(graph, landIndex, it)) {
            beach.add(it)
        }
    }
    return beach
}

private fun isCoastalPoint(graph: GraphLite, landIndex: BitMatrix<*>, vertexId: Int): Boolean {
    if (!landIndex[vertexId]) {
        return false
    }
    graph.forEachAdjacent(vertexId) { adjacentVertexId ->
        if (!landIndex[adjacentVertexId]) {
            return true
        }
    }
    return false
}

private fun Matrix<Short>.sdfFloat(index: Int) = ((this[index].toInt() and 0xFFFF) - 32768.0f) * 0.00003051757f

private fun Matrix<Short>.sdfIsPositive(index: Int) = (this[index].toInt() and 0xFFFF) > 32767

private fun splitLandAndWaterIds(graph: GraphLite, landSdf: Matrix<Short>, landIndex: BitMatrix<*>) {
    val landMaskWidth = landSdf.width
    val landMaskWidthM1 = landMaskWidth - 1
    val point = point2()
    for (i in 0 until graph.size) {
        graph.getPoint2F(i, point)
        val index = ((point.y * landMaskWidthM1).roundToInt() * landMaskWidth) + (point.x * landMaskWidthM1).roundToInt()
        landIndex[i] = landSdf.sdfIsPositive(index)
    }
}

private fun applyMapsToNodes(flowGraphId: Int, nodeData: TerraformNodeData, heightMap: Matrix<Float>, elevationPowerMask: Matrix<Short>, erosionSettings: List<ErosionSettings>, areaIndex: Matrix<Byte>, waterDepthMeters: Float) {
    val graph = getFlowGraph(flowGraphId)
    val heightWidth = heightMap.width
    val biomeWidth = areaIndex.width
    val elevationWidth = elevationPowerMask.width
    val heightWidthM1 = heightWidth - 1
    val biomeWidthM1 = biomeWidth - 1
    val elevationWidthM1 = elevationWidth - 1
    val landIndex = nodeData.landIndex
    (0 until landIndex.size).inParallel(
            context = { point2() to TerraformNode(nodeData.nodeIndex) }
    ) { (point, nodePtr), id ->
        taskYield()
        if (landIndex[id]) {
            nodePtr.id = id
            if (nodePtr.isPinned) {
                graph.getPoint2F(nodePtr.id, point)
                val bIndex = ((point.y * biomeWidthM1).roundToInt() * biomeWidth) + (point.x * biomeWidthM1).roundToInt()
                val biomeId = 0.coerceAtLeast(areaIndex[bIndex].toInt() - 1)
                nodePtr.biome = biomeId.toByte()
            } else {
                graph.getPoint2F(nodePtr.id, point)
                val hIndex = ((point.y * heightWidthM1).roundToInt() * heightWidth) + (point.x * heightWidthM1).roundToInt()
                val height = heightMap[hIndex]
                val bIndex = ((point.y * biomeWidthM1).roundToInt() * biomeWidth) + (point.x * biomeWidthM1).roundToInt()
                val biomeId = 0.coerceAtLeast(areaIndex[bIndex].toInt() - 1)
                val uIndex = ((point.y * elevationWidthM1).roundToInt() * elevationWidth) + (point.x * elevationWidthM1).roundToInt()
                nodePtr.biome = biomeId.toByte()
                val settings = erosionSettings[biomeId]
                nodePtr.height = 0.2f.coerceAtLeast(height)
                nodePtr.elevationPower = toElevationPower(elevationPowerMask[uIndex], settings.upliftPower)
            }
        } else {
            nodePtr.id = id
            val height = if (nodePtr.isExternal && !nodePtr.freezeExternalHeight) {
                0.0f
            } else if (nodePtr.isPinned) {
                waterDepthMeters
            } else {
                graph.getPoint2F(nodePtr.id, point)
                val hIndex = ((point.y * heightWidthM1).roundToInt() * heightWidth) + (point.x * heightWidthM1).roundToInt()
                (heightMap[hIndex] + waterDepthMeters).coerceIn(0.0f, waterDepthMeters)
            }
            nodePtr.height = 0.0f.coerceAtLeast(height)
            nodePtr.elevationPower = 0.0f
            nodePtr.biome = WATER_BIOME_ID
        }
    }
    taskYield()
    addEndorheicSinks(graph, nodeData)
}

private fun addEndorheicSinks(graph: GraphLite, nodeData: TerraformNodeData) {
    val landIndex = nodeData.landIndex
    val oceanMask = BitMatrix64(landIndex.width)
    nodeData.borderIds.forEach { oceanMask[it] = true }
    var frontier1 = intList()
    var frontier2 = intList()
    frontier1.add(0)
    oceanMask[0] = true
    taskYield()
    do {
        frontier1.forEach { i ->
            graph.forEachAdjacent(i) { a ->
                if (!landIndex[a] && !oceanMask[a]) {
                    oceanMask[a] = true
                    frontier2.add(a)
                }
            }
        }
        val tmp = frontier1
        tmp.clear()
        frontier1 = frontier2
        frontier2 = tmp
    } while (frontier1.isNotEmpty())
    taskYield()
    val unusedWater = BitMatrix64(landIndex.width)
    var unusedCount = 0
    oceanMask.forEachUnsetBit {
        if (!landIndex[it]) {
            unusedWater[it] = true
            unusedCount++
        }
    }
    taskYield()
    var lastUnusedCount = unusedCount
    val nodePtr = TerraformNode(nodeData.nodeIndex)
    frontier1.clear()
    frontier2.clear()
    while (unusedCount > 0) {
        frontier1.forEach { id ->
            graph.forEachAdjacent(id) { otherNodeId ->
                if (!landIndex[otherNodeId] && !oceanMask[otherNodeId]) {
                    frontier2.add(otherNodeId)
                    oceanMask[otherNodeId] = true
                    unusedWater[otherNodeId] = false
                    unusedCount--
                }
            }
        }
        if (unusedCount > 0 && lastUnusedCount == unusedCount) {
            val newSink = makeSink(unusedWater, nodePtr)
            nodeData.borderIds.add(newSink)
            frontier2.add(newSink)
            oceanMask[newSink] = true
            unusedWater[newSink] = false
            unusedCount--
        }
        lastUnusedCount = unusedCount
        val tmp = frontier1
        tmp.clear()
        frontier1 = frontier2
        frontier2 = tmp
    }
}

private fun computeAreas(graph: GraphLite, nodeIndex: ByteBuffer, starts: List<Int>, areaScale: Float) {
    starts.inParallel { start ->
        iterateAreas(graph, nodeIndex, start, areaScale)
    }
}

private fun computeHeights(graph: GraphLite, nodeIndex: ByteBuffer, distanceScale: Float, starts: List<Int>, terrainProfiles: List<TerrainProfile>, erosionSettings: List<ErosionSettings>) {
    starts.inParallel { start ->
        iterateHeights(graph, nodeIndex, distanceScale, start, terrainProfiles, erosionSettings)
    }
}

private fun computeHeights(graph: GraphLite, nodeIndex: ByteBuffer, distanceScale: Float, starts: List<Int>, underwaterProfile: UnderwaterProfile, erosionPower: Float) {
    starts.inParallel { start ->
        iterateHeights(graph, nodeIndex, distanceScale, start, underwaterProfile, erosionPower)
    }
}

private fun computeLakeConnections(graph: GraphLite, lakes: WkIntList, nodeIndex: ByteBuffer, landIndex: BitMatrix<*>, passes: PassIndex, rivers: Collection<Int>, landIndexSetBit: Boolean) {
    timeIt(timer("computeLakeConnections.iterateFindPasses")) {
        data class Context(val thread: Int, val p1: TerraformNode, val p2: TerraformNode, val b1: WkMutableIntList, val b2: WkMutableIntList)
        lakes.indices.inParallel(
                context = { Context(it, TerraformNode(nodeIndex), TerraformNode(nodeIndex), intList(), intList()) }
        ) { (thread, p1, p2, b1, b2), lakeId ->
            iterateFindPasses(thread, graph, landIndex, lakes[lakeId], passes, p1, p2, b1, b2, landIndexSetBit)
        }
    }
    val outFlowing = timeIt(timer("computeLakeConnections.outFlowing")) {
        val outflowing = BitMatrix64(lakes.size + rivers.size, 1)
        val nodePtr = TerraformNode(nodeIndex)
        rivers.forEach {
            nodePtr.id = it
            outflowing[nodePtr.lake] = true
        }
        outflowing
    }
    timeIt(timer("computeLakeConnections.computeLinks")) {
        val iterator = passes.iterator()
        while (iterator.isNotEmpty()) {
            taskYield()
            for (i in iterator) {
                val lake1 = iterator.lake1
                if (outFlowing[lake1]) {
                    iterator.remove(i)
                } else if (outFlowing[iterator.lake2]) {
                    outFlowing[lake1] = true
                    iterator.remove(i)
                    flipNodesInPathToRoot(graph, nodeIndex, iterator.id1, iterator.id2)
                    break
                }
            }
            iterator.restart()
        }
    }
}

private fun prepareNodesAndLakes(graph: GraphLite, nodeIndex: ByteBuffer, lakes: WkMutableIntList, landIndex: BitMatrix<*>, starts: WkIntList, landIndexSetBit: Boolean) {
    val lakeElements = lakes.delegate
    (0 until landIndex.size).inParallel(
            context = { Triple(intList((lakeElements.elements().size / PARALLELISM) + 1), TerraformNode(nodeIndex), TerraformNode(nodeIndex)) }
    ) { (localLakes, nodePtr, otherNodePtr), i ->
        if (landIndex[i] == landIndexSetBit) {
            nodePtr.id = i
            nodePtr.lake = -1
            if (!nodePtr.isExternal) {
                var minHeight = nodePtr.height
                var minNodeId = nodePtr.id
                graph.forEachAdjacent(nodePtr.id) { otherNodeId ->
                    if (landIndex[otherNodeId] == landIndexSetBit) {
                        otherNodePtr.id = otherNodeId
                        if (otherNodePtr.height < minHeight) {
                            minNodeId = otherNodeId
                            minHeight = otherNodePtr.height
                        }
                    }
                }
                if (minNodeId != graph.getIdFromOffset(nodePtr.id, nodePtr.parentCode)) {
                    nodePtr.parentCode = graph.getOffsetFromAdjacentIds(nodePtr.id, minNodeId)
                }
                if (minNodeId == nodePtr.id) {
                    localLakes.add(nodePtr.id)
                }
            }
        }
    }.forEach { (localLakes) ->
        lakeElements.addAllOf(localLakes.delegate)
    }
    val startsElements = starts.delegate
    val riverCount = starts.size
    starts.indices.inParallel { id ->
        iterateSetLake(graph, nodeIndex, id, startsElements.getQuick(id))
    }
    lakes.indices.inParallel { id ->
        iterateSetLake(graph, nodeIndex, id + riverCount, lakeElements.getQuick(id))
    }
}

private fun toElevationPower(asShort: Short, multiplier: Float): Float {
    val temp = (asShort.toInt() and 0xFFFF)
    return if (temp == 0) {
        0.0f
    } else {
        ((((temp + 1) / 65536.0f) * 0.0005f) * multiplier).coerceAtLeast(0.0f)
    }
}

private fun renderHeightMapCubic(flowGraphId: Int, nodeIndex: ByteBuffer, waterDepthMeters: Float): FloatArrayMatrix {
    val graph = getFlowGraph(flowGraphId)
    val width = graph.stride
    val vertexPositions = FloatArray(graph.stride * graph.stride * 3)
    (0 until graph.stride).toList().parallelStream().forEach { y ->
        val nodePtr = TerraformNode(nodeIndex)
        val point = point2()
        val yOff = y * graph.stride
        (0 until graph.stride).forEach { x ->
            nodePtr.id = yOff + x
            graph.getPoint2F(nodePtr.id, point)
            val o = nodePtr.id * 3
            vertexPositions[o] = point.x
            vertexPositions[o + 1] = point.y
            vertexPositions[o + 2] = if (nodePtr.biome == WATER_BIOME_ID) nodePtr.height - waterDepthMeters else nodePtr.height
        }
    }
    val output = FloatArrayMatrix(width)
    val textureSize = graph.stride
    val scale = textureSize * 1.1f
    val outputToGraph = textureSize.toFloat() / width
    val widthInverse = 1.0f / (width - 1)
    val kernelSize = 3
    val kernelOffset = kernelSize / 2
    val kernelBufferSize = kernelSize * 3
    val lastKernelFetch = textureSize - kernelOffset

    val r = 1.25f
    val r2 = r * r
    val r2i = 1.0f / r2

    fun fetchKernelRow(row: Int, kernelId: Int, kernelXStart: Int, kernel: FloatArray) {
        System.arraycopy(vertexPositions, (row * textureSize + kernelXStart) * 3, kernel, kernelId * kernelBufferSize, kernelBufferSize)
    }

    (0 until width).inParallel { ox ->
        val kernel = FloatArray(kernelSize * kernelBufferSize)
        val kernelXStart = ((ox * outputToGraph).roundToInt().coerceIn(0 until textureSize) - kernelOffset).coerceIn(0, textureSize - kernelSize)
        (0 until kernelSize).forEach { kernelId ->
            fetchKernelRow(kernelId, kernelId, kernelXStart, kernel)
        }
        var nextKernelRow = 0
        var nextFetchedRow = kernelSize
        val u = ox * widthInverse
        for (oy in 0 until width) {
            val gy = (oy * outputToGraph).roundToInt().coerceIn(0 until textureSize)
            val v = oy * widthInverse
            if (gy in nextFetchedRow - kernelOffset until lastKernelFetch) {
                fetchKernelRow(nextFetchedRow, nextKernelRow, kernelXStart, kernel)
                nextKernelRow = (nextKernelRow + 1) % kernelSize
                nextFetchedRow++
            }
            var sum = 0.0f
            var cSum = Float.MIN_VALUE
            for (i in kernel.indices step 3) {
                val vx = kernel[i]
                val vy = kernel[i + 1]
                val vz = kernel[i + 2]
                val dx = (u - vx) * scale
                val dy = (v - vy) * scale
                val d2 = dx * dx + dy * dy
                val c = if (d2 < r2) {
                    val a = 1.0f - (d2 * r2i)
                    a * a * a
                } else {
                    0.0f
                }
                sum += vz * c
                cSum += c
            }
            output[oy * width + ox] = sum / cSum
        }
    }
    return output
}

private fun renderHeightMapGauss(flowGraphId: Int, nodeIndex: ByteBuffer, waterDepthMeters: Float): FloatArrayMatrix {
    val graph = getFlowGraph(flowGraphId)
    val width = graph.stride
    val vertexPositions = FloatArray(graph.stride * graph.stride * 3)
    (0 until graph.stride).toList().parallelStream().forEach { y ->
        val nodePtr = TerraformNode(nodeIndex)
        val point = point2()
        val yOff = y * graph.stride
        (0 until graph.stride).forEach { x ->
            nodePtr.id = yOff + x
            graph.getPoint2F(nodePtr.id, point)
            val o = nodePtr.id * 3
            vertexPositions[o] = point.x
            vertexPositions[o + 1] = point.y
            vertexPositions[o + 2] = if (nodePtr.biome == WATER_BIOME_ID) nodePtr.height - waterDepthMeters else nodePtr.height
        }
    }
    val output = FloatArrayMatrix(width)
    val textureSize = graph.stride
    val scale = textureSize * 1.1
    val outputToGraph = textureSize.toFloat() / width
    val widthInverse = 1.0f / (width - 1)
    val kernelSize = 5
    val kernelOffset = kernelSize / 2
    val kernelBufferSize = kernelSize * 3
    val lastKernelFetch = textureSize - kernelOffset

    fun fetchKernelRow(row: Int, kernelId: Int, kernelXStart: Int, kernel: FloatArray) {
        System.arraycopy(vertexPositions, (row * textureSize + kernelXStart) * 3, kernel, kernelId * kernelBufferSize, kernelBufferSize)
    }

    (0 until width).inParallel { ox ->
        val kernel = FloatArray(kernelSize * kernelBufferSize)
        val kernelXStart = ((ox * outputToGraph).roundToInt().coerceIn(0 until textureSize) - kernelOffset).coerceIn(0, textureSize - kernelSize)
        (0 until kernelSize).forEach { kernelId ->
            fetchKernelRow(kernelId, kernelId, kernelXStart, kernel)
        }
        var nextKernelRow = 0
        var nextFetchedRow = kernelSize
        val u = ox * widthInverse
        for (oy in 0 until width) {
            val gy = (oy * outputToGraph).roundToInt().coerceIn(0 until textureSize)
            val v = oy * widthInverse
            if (gy in nextFetchedRow - kernelOffset until lastKernelFetch) {
                fetchKernelRow(nextFetchedRow, nextKernelRow, kernelXStart, kernel)
                nextKernelRow = (nextKernelRow + 1) % kernelSize
                nextFetchedRow++
            }
            var sum = 0.0f
            var cSum = Float.MIN_VALUE
            for (i in kernel.indices step 3) {
                val vx = kernel[i]
                val vy = kernel[i + 1]
                val vz = kernel[i + 2]
                val dx = (u - vx) * scale
                val dy = (v - vy) * scale
                val d2 = dx * dx + dy * dy
                val c = 2.7182818284590452354.pow(-PI * d2).toFloat()
                sum += vz * c
                cSum += c
            }
            output[oy * width + ox] = sum / cSum
        }
    }
    return output
}

private fun <T : Reference<Float>> applyTerracing(heightMap: Matrix<Float>, biomeMask: Matrix<Byte>, erosionSettings: List<ErosionSettings>, biomeExtremes: Array<Pair<T, T>>) {
    val terraceFunctions = Array(erosionSettings.size + 1) {
        if (it == 0) {
            Triple(null, 0.0f, 0.0f)
        } else {
            val id = it -1
            val (min, max) = biomeExtremes[id]
            val biomeSettings = erosionSettings[id]
            Triple(biomeSettings.terraceFunction?.invoke(min.value, max.value), biomeSettings.terraceJitter, biomeSettings.terraceJitterFrequency)
        }
    }
    val convertId = biomeMask.width.toFloat() / heightMap.width
    (0 until heightMap.size).inParallel { i ->
        val x = i % heightMap.width
        val y = i / heightMap.width
        val bx = (x * convertId).roundToInt().coerceIn(0 until biomeMask.width)
        val by = (y * convertId).roundToInt().coerceIn(0 until biomeMask.width)
        val biomeId = by * biomeMask.width + bx
        val (terracing, jitter, jitterFrequency) = terraceFunctions[biomeMask[biomeId].toInt()]
        if (terracing != null) {
            val simplexX = (x / heightMap.width.toFloat()) * jitterFrequency
            val simplexY = (y / heightMap.width.toFloat()) * jitterFrequency
            val height = heightMap[i]
            val newHeight = terracing(height, simplexX, simplexY, jitter)
            if (newHeight != height) {
                heightMap[i] = newHeight
            }
        }
    }
}

private fun flipNodesInPathToRoot(graph: GraphLite, nodeIndex: ByteBuffer, waterNodeId: Int, newRootId: Int) {
    val lastNodePtr = TerraformNode(nodeIndex)
    val currentNodePtr = TerraformNode(nodeIndex)
    lastNodePtr.id = newRootId
    currentNodePtr.id = waterNodeId
    while (!(currentNodePtr.isExternal || currentNodePtr.parentCode == GraphLite.SELF_OFFSET)) {
        val nextInChain = graph.getIdFromOffset(currentNodePtr.id, currentNodePtr.parentCode)
        currentNodePtr.parentCode = graph.getOffsetFromAdjacentIds(currentNodePtr.id, lastNodePtr.id)
        lastNodePtr.id = currentNodePtr.id
        currentNodePtr.id = nextInChain
    }
    if (!currentNodePtr.isExternal) {
        currentNodePtr.parentCode = graph.getOffsetFromAdjacentIds(currentNodePtr.id, lastNodePtr.id)
    }
}

private fun iterateFindPasses(
        thread: Int,
        graph: GraphLite,
        landIndex: BitMatrix<*>,
        startNodeId: Int,
        passes: PassIndex,
        p1: TerraformNode,
        p2: TerraformNode,
        b1: WkMutableIntList,
        b2: WkMutableIntList,
        landIndexSetBit: Boolean
) {
    var buffer = b1
    var children = b2
    b1.clear()
    b2.clear()
    b1.add(startNodeId)
    while (buffer.isNotEmpty()) {
        for (nodeId in buffer) {
            p1.id = nodeId
            graph.forEachAdjacent(p1.id) { otherNodeId ->
                if (landIndex[otherNodeId] == landIndexSetBit) {
                    p2.id = otherNodeId
                    if (p2.lake != p1.lake) {
                        passes.put(thread, p1.lake, p2.lake, p1.id, p2.id, max(p1.height, p2.height))
                    } else if (p1.id == graph.getIdFromOffset(p2.id, p2.parentCode)) {
                        children.add(p2.id)
                    }
                }
            }
        }
        val tmp = buffer
        buffer = children
        tmp.clear()
        children = tmp
    }
}

private fun iterateSetLake(graph: GraphLite, nodeIndex: ByteBuffer, lakeId: Int, startNodeId: Int) {
    var buffer = intListOf(startNodeId)
    var children = intList()
    val nodePtr = TerraformNode(nodeIndex)
    val childPtr = TerraformNode(nodeIndex)
    while (buffer.isNotEmpty()) {
        for (nodeId in buffer) {
            nodePtr.id = nodeId
            nodePtr.lake = lakeId
            nodePtr.forEachChild(graph, childPtr) { child ->
                children.add(child.id)
            }
        }
        val tmp = buffer
        buffer = children
        tmp.clear()
        children = tmp
    }
}

private fun iterateAreas(graph: GraphLite, nodeIndex: ByteBuffer, startNodeId: Int, areaScale: Float) {
    val order = intList()
    var buffer = intListOf(startNodeId)
    var children = intList()
    val nodePtr = TerraformNode(nodeIndex)
    val childPtr = TerraformNode(nodeIndex)
    while (buffer.isNotEmpty()) {
        for (nodeId in buffer) {
            order.add(nodeId)
            nodePtr.id = nodeId
            nodePtr.forEachChild(graph, childPtr) { child ->
                children.add(child.id)
            }
        }
        val tmp = buffer
        buffer = children
        tmp.clear()
        children = tmp
    }
    for (orderId in order.indices.reversed()) {
        nodePtr.id = order[orderId]
        nodePtr.drainageArea = graph.getArea(nodePtr.id) * areaScale
        nodePtr.forEachChild(graph, childPtr) { child ->
            nodePtr.drainageArea += child.drainageArea
        }
    }
}

private fun iterateHeights(graph: GraphLite, nodeIndex: ByteBuffer, distanceScale: Float, startNodeId: Int, terrainProfiles: List<TerrainProfile>, erosionSettings: List<ErosionSettings>) {
    var buffer = intListOf(startNodeId)
    var children = intList()
    val nodePtr = TerraformNode(nodeIndex)
    val childPtr = TerraformNode(nodeIndex)
    val parentPtr = TerraformNode(nodeIndex)
    val retVal = T2(0, 0.0f)
    while (buffer.isNotEmpty()) {
        for (nodeId in buffer) {
            nodePtr.id = nodeId
            if (!nodePtr.isExternal && !nodePtr.isPinned) {
                val biome = terrainProfiles[0.coerceAtLeast(nodePtr.biome.toInt())]
                val settings = erosionSettings[0.coerceAtLeast(nodePtr.biome.toInt())]
                val talusAngles = biome.talusAngles
                val heightScale = biome.heightScale
                val erosionPower = settings.erosionPower
                val talusOverride = settings.talusOverride
                val talusFunction = { nodeHeightIndex: Int, variance: Float ->
                    if (talusOverride != null) {
                        (talusOverride + talusAngles[nodeHeightIndex].variance * variance)
                    } else {
                        val talusAngle = talusAngles[nodeHeightIndex]
                        (talusAngle.base + talusAngle.variance * variance)
                    }
                }
                calculateHeight(graph, nodePtr, parentPtr, distanceScale, erosionPower, heightScale, retVal, talusFunction)
            }
            nodePtr.forEachChild(graph, childPtr) { child ->
                children.add(child.id)
            }
        }
        val tmp = buffer
        buffer = children
        tmp.clear()
        children = tmp
    }
}

private fun iterateHeights(graph: GraphLite, nodeIndex: ByteBuffer, distanceScale: Float, startNodeId: Int, underwaterProfile: UnderwaterProfile, erosionPower: Float) {
    var buffer = intListOf(startNodeId)
    var children = intList()
    val nodePtr = TerraformNode(nodeIndex)
    val childPtr = TerraformNode(nodeIndex)
    val parentPtr = TerraformNode(nodeIndex)
    val talusAngles = underwaterProfile.talusAngles
    val heightScale = underwaterProfile.heightScale
    val talusFunction = { nodeHeightIndex: Int, variance: Float ->
        val talusAngle = talusAngles[nodeHeightIndex]
        (talusAngle.base + talusAngle.variance * variance)
    }
    val retVal = T2(0, 0.0f)
    while (buffer.isNotEmpty()) {
        for (nodeId in buffer) {
            nodePtr.id = nodeId
            if (!nodePtr.isExternal && !nodePtr.isPinned) {
                calculateHeight(graph, nodePtr, parentPtr, distanceScale, erosionPower, heightScale, retVal, talusFunction)
            }
            nodePtr.forEachChild(graph, childPtr) { child ->
                children.add(child.id)
            }
        }
        val tmp = buffer
        buffer = children
        tmp.clear()
        children = tmp
    }
}

private inline fun calculateHeight(graph: GraphLite, nodePtr: TerraformNode, parentPtr: TerraformNode, distanceScale: Float, erosionPower: Float, heightScale: Float, retVal: M2<Int, Float>, talusFunction: (Int, Float) -> Float) {
    val (parentId, unscaledDistToParent) = graph.getIdAndDistanceFromOffset(nodePtr.id, nodePtr.parentCode, retVal)
    val distanceToParent = unscaledDistToParent * distanceScale
    parentPtr.id = parentId
    val parentHeight = parentPtr.height
    val flow = SOIL_MOBILITY * erosionPower * sqrt(nodePtr.drainageArea)
    val erosion = flow / distanceToParent
    val denominator = 1.0 + (erosion * 250000.0f)
    val numerator = nodePtr.height + (250000.0f * (nodePtr.elevationPower + (erosion * parentHeight)))
    nodePtr.height = (numerator / denominator).toFloat()
    val point = graph.getPoint2F(nodePtr.id)
    val variance = SimplexNoise.noise(point.x * SIMPLEX_SCALE, point.y * SIMPLEX_SCALE, nodePtr.height * 0.1f)
    val nodeHeightIndex = (nodePtr.height * heightScale).roundToInt().coerceIn(0, 1023)
    val normalizedTalusAngle = talusFunction(nodeHeightIndex, variance)
    val talusSlope = angleToSlope(normalizedTalusAngle)
    if ((nodePtr.height - parentPtr.height) / distanceToParent > talusSlope) {
        nodePtr.height = (distanceToParent * talusSlope) + parentPtr.height
    }
}

@PublicApi
enum class MapScale(
        private val mapSizeKilometers: Int,
        val heightRangeMeters: Float,
        val mapSizeMeters: Float = mapSizeKilometers * 1000.0f,
        val waterDepthMeters: Float = (((mapSizeMeters - 1000.0f) / 60.0f) + 30.0f).coerceIn(30.0f, 500.0f),
        val waterRatio: Float = waterDepthMeters / heightRangeMeters) {
    MapScale1K(1, 300.0f),
    MapScale2K(2, 400.0f),
    MapScale3K(3, 500.0f),
    MapScale4K(4, 700.0f),
    MapScale5K(5,  800.0f),
    MapScale6K(6,  1000.0f),
    MapScale8K(8,  1300.0f),
    MapScale10K(10,  1600.0f),
    MapScale12K(12,  1900.0f),
    MapScale16K(16,  2600.0f),
    MapScale20K(20,  3200.0f),
    MapScale24K(24,  3200.0f),
    MapScale32K(32,  3200.0f),
    MapScale48K(48,  3200.0f),
    MapScale64K(64,  3200.0f),
    MapScale128K(128,  3200.0f);

    companion object {
        @PublicApi
        val indices = values().indices

        @PublicApi
        operator fun get(ordinal: Int): MapScale {
            return values()[ordinal]
        }
    }
}

@PublicApi
class TerrainDisplayData(
        val mapScale: MapScale,
        val normalizedScaleFactor: Float,
        val heightMap: ShortArrayMatrix,
        val normalMap: IntArrayMatrix,
        val occlusionMap: ByteArrayMatrix,
        val riverLines: List<PolyLine>
)

@PublicApi
fun TerraformResult.toTerrainDisplayData(mapScale: MapScale): TerrainDisplayData {
    return runBlocking {
        val heightMapDeferred = async { toStandardizedHeightMap(mapScale) }
        val normalMap = async { toNormalMap(mapScale) }
        val occlusionMap = async { toOcclusionMap(mapScale) }
        val riverLineMap = async { toRiverLines(mapScale) }
        TerrainDisplayData(
                mapScale = mapScale,
                normalizedScaleFactor = heightMapDeferred.await().second,
                heightMap = heightMapDeferred.await().first,
                normalMap = normalMap.await(),
                occlusionMap = occlusionMap.await(),
                riverLines = riverLineMap.await()
        )
    }
}

private class PassIterator(
        private val passIndex: PassIndex,
        private val pointers: LongArray
): Iterator<Int> {

    private val forwardRefs = IntArray(pointers.size) {
        it + 1
    }

    private val backRefs = IntArray(pointers.size) {
        it - 1
    }

    private var head = 0
    private var cursor = 0
    private var buffer: IntArray = passIndex.buffers[0]
    private var offset = -1

    init {
        if (pointers.isNotEmpty()) {
            forwardRefs[forwardRefs.lastIndex] = -1
        } else {
            head = -1
            cursor = -1
        }
    }

    var size = pointers.size

    val lake1: Int get() = buffer[offset]
    val lake2: Int get() = buffer[offset + 1]
    val id1: Int get() = buffer[offset + 2]
    val id2: Int get() = buffer[offset + 3]
    val height: Float get() = java.lang.Float.intBitsToFloat(buffer[offset + 4])

    fun restart() {
        cursor = head
        offset = -1
        buffer = passIndex.buffers[0]
    }

    override fun hasNext() = cursor != -1

    override fun next(): Int {
        if (cursor == -1) return -1
        val tmp = cursor
        val code = pointers[tmp]
        buffer = passIndex.buffers[(code ushr 32).toInt()]
        offset = (code and 0xFFFFFFFF).toInt()
        cursor = forwardRefs[tmp]
        return tmp
    }

    fun remove(i: Int) {
        if (i !in pointers.indices) return
        val pointer = pointers[i]
        if (pointer == -1L) return
        val previous = backRefs[i]
        val next = forwardRefs[i]
        if (previous != -1) {
            forwardRefs[previous] = next
        }
        if (next != -1) {
            backRefs[next] = previous
        }
        if (i == head) {
            head = next
        }
        pointers[i] = -1L
        size--
    }

    fun isNotEmpty() = size != 0
}

private class PassIndex(val threads: Int, lakeCount: Int = 0) {

    companion object {
        private const val NO_INDEX = -1

        const val ELEMENT_SIZE = 5
    }

    private var pointers = IntArray(lakeCount)

    var buffers = Array(threads) { IntArray((lakeCount * 12) / threads) }

    private var cursors = Array(threads) { ref(0) }

    val size: Int get() = cursors.sumBy { it.value } / ELEMENT_SIZE

    init {
        reset(lakeCount)
    }

    fun dataSize(thread: Int): Int = cursors[thread].value

    private fun cursorOffset(thread: Int): Int = cursors.take(thread).sumBy { it.value } / ELEMENT_SIZE

    fun reset(lakeCount: Int) {
        pointers = Arrays.ensureCapacity(pointers, lakeCount)
        java.util.Arrays.fill(pointers, NO_INDEX)
        buffers.forEach {
            java.util.Arrays.fill(it, NO_INDEX)
        }
        cursors.forEach { it.value = 0 }
    }

    private fun indexOf(thread: Int, lake1: Int, lake2: Int, pointer: Int): Int {
        var p = pointer
        val cursor = cursors[thread]
        val buffer = buffers[thread]
        while (p < cursor.value) {
            val rawData = buffer[p]
            if (rawData != lake1) return -1
            if (buffer[p + 1] == lake2) return p
            p += ELEMENT_SIZE
        }
        return -1
    }

    fun put(thread: Int, lake1: Int, lake2: Int, id1: Int, id2: Int, height: Float) {
        val p = pointers[lake1]
        val index = if (p == -1) -1 else indexOf(thread, lake1, lake2, p)
        val cursor = cursors[thread]
        var buffer = buffers[thread]
        if (index == -1) {
            if (p == -1) {
                pointers[lake1] = cursor.value
            }
            buffer = Arrays.ensureCapacity(buffer, cursor.value + ELEMENT_SIZE)
            buffers[thread] = buffer
            buffer[cursor.value++] = lake1
            buffer[cursor.value++] = lake2
            buffer[cursor.value++] = id1
            buffer[cursor.value++] = id2
            buffer[cursor.value++] = java.lang.Float.floatToRawIntBits(height)
        } else {
            if (height < java.lang.Float.intBitsToFloat(buffer[index + 4])) {
                buffer[index + 2] = id1
                buffer[index + 3] = id2
                buffer[index + 4] = java.lang.Float.floatToRawIntBits(height)
            }
        }
    }

    inline fun forEach(apply: (Int, Int, Int, Int, Float) -> Unit) {
        for (t in 0 until threads) {
            val buffer = buffers[t]
            for (i in 0 until dataSize(t) step ELEMENT_SIZE) {
                apply(buffer[i],
                      buffer[i + 1],
                      buffer[i + 2],
                      buffer[i + 3],
                      java.lang.Float.intBitsToFloat(buffer[i + 4]))
            }
        }
    }

    fun iterator(): PassIterator {
        val size = size
        val index1 = LongArray(size)
        val ranges = IntArray(threads)
        buffers.asList().inParallel(threads) { thread, buffer ->
            val start = cursorOffset(thread)
            val length = cursors[thread].value / ELEMENT_SIZE
            ranges[thread] = start
            val threadMask = thread.toLong() shl 32
            for (i in 0 until length) {
                index1[start + i] = threadMask or (i * ELEMENT_SIZE).toLong()
            }
            val indexList = LongArrayList(index1)
            indexList.quickSortFromTo(start, start + length - 1) { a: Long, b: Long ->
                val aOff = (a and 0xFFFFFFFFL).toInt()
                val bOff = (b and 0xFFFFFFFFL).toInt()
                compareInternal(buffer, aOff, buffer, bOff)
            }
        }
        var fromBuffer = index1
        var toBuffer = LongArray(size)
        var fromRanges = ranges
        while(fromRanges.size > 1) {
            val nextRanges = IntArray(fromRanges.size / 2 + if (fromRanges.size % 2 != 0) 1 else 0)
            nextRanges.indices.inParallel { thread ->
                val offset = thread * 2
                val start1 = fromRanges[offset]
                nextRanges[thread] = start1
                if (offset + 1 == fromRanges.size) {
                    System.arraycopy(fromBuffer, start1, toBuffer, start1, size - start1)
                } else {
                    val start2 = fromRanges[offset + 1]
                    val end2 = if (offset + 2 == fromRanges.size) size else fromRanges[offset + 2]
                    mergeSegments(fromBuffer, start1, start2, end2, toBuffer)
                }
            }
            fromRanges = nextRanges
            val tmp = fromBuffer
            fromBuffer = toBuffer
            toBuffer = tmp
        }
        return PassIterator(this, fromBuffer)
    }

    private fun compareInternal(aBuffer: IntArray, aOff: Int, bBuffer: IntArray, bOff: Int): Int {
        val aHeight = java.lang.Float.intBitsToFloat(aBuffer[aOff + 4])
        val bHeight = java.lang.Float.intBitsToFloat(bBuffer[bOff + 4])
        val heightComp = aHeight.compareTo(bHeight)
        return if (heightComp != 0) {
            heightComp
        } else {
            val lake1Comp = aBuffer[aOff].compareTo(bBuffer[bOff])
            if (lake1Comp != 0) {
                lake1Comp
            } else {
                val lake2Comp = aBuffer[aOff + 1].compareTo(bBuffer[bOff + 1])
                if (lake2Comp != 0) {
                    lake2Comp
                } else {
                    val id1Comp = aBuffer[aOff + 2].compareTo(bBuffer[bOff + 2])
                    if (id1Comp != 0) {
                        id1Comp
                    } else {
                        aBuffer[aOff + 3].compareTo(bBuffer[bOff + 3])
                    }
                }
            }
        }
    }

    private fun mergeSegments(input: LongArray, start1: Int, start2: Int, end2: Int, output: LongArray) {
        var a = start1
        var b = start2
        var c = start1
        while (a < start2 && b < end2) {
            val aCode = input[a]
            val bCode = input[b]
            val aBuffer = (aCode ushr 32).toInt()
            val aOff = (aCode and 0xFFFFFFFFL).toInt()
            val bBuffer = (bCode ushr 32).toInt()
            val bOff = (bCode and 0xFFFFFFFFL).toInt()
            val comp = compareInternal(buffers[aBuffer], aOff, buffers[bBuffer], bOff)
            if (comp <= 0) output[c++] = input[a++] else output[c++] = input[b++]
        }
        while (a < start2) output[c++] = input[a++]
        while (b < end2) output[c++] = input[b++]
    }
}

internal class TerraformNode(val data: ByteBuffer) {
    companion object {
        const val SIZE = 22
    }

    private var _id = 0
    private var offset = 0

    var id
        get() = _id
        set(value) {
            _id = value
            offset = _id * SIZE
        }

    var elevationPower
        get() = data.getFloat(offset)
        set(value) {
            data.putFloat(offset, value)
        }

    var height
        get() = data.getFloat(offset + 4)
        set(value) {
            data.putFloat(offset + 4, value)
        }

    var drainageArea
        get() = data.getFloat(offset + 8)
        set(value) {
            data.putFloat(offset + 8, value)
        }

    var maxUpstreamLength
        get() = data.getFloat(offset + 12)
        set(value) {
            data.putFloat(offset + 12, value)
        }

    var lake
        get() = data.getInt(offset + 16)
        set(value) {
            data.putInt(offset + 16, value)
        }

    var biome
        get() = data.get(offset + 20)
        set(value) {
            data.put(offset + 20, value)
        }

    private var flags
        get() = data.get(offset + 21)
        set(value) {
            data.put(offset + 21, value)
        }

    var parentCode
        get() = (flags.toInt() and 0b00011111).toByte()
        set(value) {
            flags = ((flags.toInt() and 0b11100000) or value.toInt()).toByte()
        }

    var isExternal
        get() = (flags.toInt() and 0b10000000) != 0
        set(value) {
            flags = if (value) {
                (flags.toInt() or 0b10000000).toByte()
            } else {
                (flags.toInt() and 0b01111111).toByte()
            }
        }

    var freezeExternalHeight
        get() = (flags.toInt() and 0b01000000) != 0
        set(value) {
            flags = if (value) {
                (flags.toInt() or 0b01000000).toByte()
            } else {
                (flags.toInt() and 0b10111111).toByte()
            }
        }

    var isPinned
        get() = (flags.toInt() and 0b00100000) != 0
        set(value) {
            flags = if (value) {
                (flags.toInt() or 0b00100000).toByte()
            } else {
                (flags.toInt() and 0b11011111).toByte()
            }
        }

    fun init(
            isExternal: Boolean = false,
            isLand: Boolean = false,
            isPinned: Boolean = false,
            elevationPower: Float = 0.0f,
            height: Float = 0.0f,
            drainageArea: Float = 0.0f,
            biome: Byte = 0,
            parentCode: Byte = 12,
            lake: Int = -1,
            maxUpstreamLength: Float = 0.0f) {
        flags = ((if (isExternal) 0b10000000 else 0) or (if (isLand) 0b01000000 else 0) or (if (isPinned) 0b00100000 else 0) or parentCode.toInt()).toByte()
        this.elevationPower = elevationPower
        this.height = height
        this.drainageArea = drainageArea
        this.biome = biome
        this.lake = lake
        this.maxUpstreamLength = maxUpstreamLength
    }

    fun forEachChild(graph: GraphLite, childPtr: TerraformNode, callback: (child: TerraformNode) -> Unit) {
        graph.forEachAdjacent(id) { adjacentId ->
            childPtr.id = adjacentId
            if (id == graph.getIdFromOffset(childPtr.id, childPtr.parentCode)) {
                callback(childPtr)
            }
        }
    }
}