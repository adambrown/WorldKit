@file:DependsOn("Common.kt")
@file:DependsOn("TerrainProfiles.kt")

import wk.api.*
import kotlin.math.max
import kotlin.math.min

val terraformRenderMode by cRef(TerraformRenderMode.Gaussian)
val terraformBlur by cRef(0.75f)

val beachShelfMaxFun = { currentValue: Float, sdfDistance: Float, waterDepth: Float ->
    val height = (1.0f - beachShelfMaxControl[-sdfDistance].first) * -waterDepth
    val alpha1 = (1.0f + sdfDistance * 5.0f).coerceIn(0.0f, 1.0f)
    val alpha2 = 1.0f - alpha1
    max(currentValue, height) * alpha1 + currentValue * alpha2
}

val beachShelfMinFun = { currentValue: Float, sdfDistance: Float, _: Float ->
    val height = (1 - beachShelfMinControl[-sdfDistance].first) * -15.0f
    min(currentValue, height)
}

fun terraformPassA0(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 368384,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 0,
        previousMap = previousMap,
        erosionSettingsIndex = 0,
        iterations = 150,
        waterIterations = 30,
        sdfMultiplier = 800.0f,
        sdfNoiseFrequency = 1024.0f,
        sdfNoiseAmplitude = 0.03f,
        outputWidth = getFlowGraphWidth(0),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMaxFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassA1(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 98678,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 0,
        previousMap = previousMap,
        erosionSettingsIndex = 1,
        iterations = 30,
        waterIterations = 15,
        sdfMultiplier = 1600.0f,
        sdfNoiseFrequency = 1024.0f,
        sdfNoiseAmplitude = 0.03f,
        outputWidth = getFlowGraphWidth(0),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMaxFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassA2(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 757843865,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 0,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 30,
        waterIterations = 15,
        sdfMultiplier = 500.0f,
        sdfNoiseFrequency = 1024.0f,
        sdfNoiseAmplitude = 0.009f,
        outputWidth = getFlowGraphWidth(1),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMinFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassA(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
): TerraformResult {
    var temp = terraformPassA0(previousMap, landSdf, areaIndex, upliftMap).heightMap
    temp = terraformPassA1(temp, landSdf, areaIndex, upliftMap).heightMap
    return terraformPassA2(temp, landSdf, areaIndex, upliftMap)
}

fun terraformPassB0(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 7516389,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 1,
        previousMap = previousMap,
        erosionSettingsIndex = 1,
        iterations = 30,
        waterIterations = 15,
        sdfMultiplier = 1600.0f,
        sdfNoiseFrequency = 2048.0f,
        sdfNoiseAmplitude = 0.03f,
        outputWidth = getFlowGraphWidth(1),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMaxFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassB1(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 9428652,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 1,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 30,
        waterIterations = 15,
        sdfMultiplier = 325.0f,
        sdfNoiseFrequency = 2048.0f,
        sdfNoiseAmplitude = 0.006f,
        outputWidth = getFlowGraphWidth(2),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMinFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassB(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraformPassB1(terraformPassB0(previousMap, landSdf, areaIndex, upliftMap).heightMap, landSdf, areaIndex, upliftMap)

fun terraformPassC0(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 10864951,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 2,
        previousMap = previousMap,
        erosionSettingsIndex = 1,
        iterations = 30,
        waterIterations = 15,
        sdfMultiplier = 1600.0f,
        sdfNoiseFrequency = 2048.0f,
        sdfNoiseAmplitude = 0.03f,
        outputWidth = getFlowGraphWidth(2),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMaxFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassC1(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 57247457,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 2,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 30,
        waterIterations = 15,
        sdfMultiplier = 325.0f,
        sdfNoiseFrequency = 2048.0f,
        sdfNoiseAmplitude = 0.003f,
        outputWidth = getFlowGraphWidth(3),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMinFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassC(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraformPassC1(terraformPassC0(previousMap, landSdf, areaIndex, upliftMap).heightMap, landSdf, areaIndex, upliftMap)

fun terraformPassD0(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 32187945,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 3,
        previousMap = previousMap,
        erosionSettingsIndex = 1,
        iterations = 30,
        waterIterations = 10,
        sdfMultiplier = 1600.0f,
        sdfNoiseFrequency = 4096.0f,
        sdfNoiseAmplitude = 0.015f,
        outputWidth = getFlowGraphWidth(3),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMaxFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassD1(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 1456187,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 3,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 30,
        waterIterations = 10,
        sdfMultiplier = 150.0f,
        sdfNoiseFrequency = 4096.0f,
        sdfNoiseAmplitude = 0.0015f,
        outputWidth = getFlowGraphWidth(4),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMinFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassD(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraformPassD1(terraformPassD0(previousMap, landSdf, areaIndex, upliftMap).heightMap, landSdf, areaIndex, upliftMap)

fun terraformPassE0(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 98432655,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 4,
        previousMap = previousMap,
        erosionSettingsIndex = 1,
        iterations = 30,
        waterIterations = 10,
        sdfMultiplier = 1600.0f,
        sdfNoiseFrequency = 4096.0f,
        sdfNoiseAmplitude = 0.003f,
        outputWidth = getFlowGraphWidth(4),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMaxFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassE1(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 5367985563,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 4,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 12,
        waterIterations = 5,
        sdfMultiplier = 150.0f,
        sdfNoiseFrequency = 4096.0f,
        sdfNoiseAmplitude = 0.00075f,
        outputWidth = getFlowGraphWidth(5),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMinFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassE(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraformPassE1(terraformPassE0(previousMap, landSdf, areaIndex, upliftMap).heightMap, landSdf, areaIndex, upliftMap)

fun terraformPassF(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 901328452,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 5,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 8,
        waterIterations = 2,
        sdfMultiplier = 0.0f,
        sdfNoiseFrequency = 8192.0f,
        sdfNoiseAmplitude = 0.0f,
        outputWidth = outputWidth,
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMinFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)
