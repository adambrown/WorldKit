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
        sdfMultiplier = 10.0f,
        sdfNoiseFrequency = 1024.0f,
        sdfNoiseAmplitude = 0.1f,
        outputWidth = getFlowGraphWidth(1),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMaxFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassB(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 27306503246,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 1,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 30,
        waterIterations = 15,
        sdfMultiplier = 10.0f,
        sdfNoiseFrequency = 2048.0f,
        sdfNoiseAmplitude = 0.1f,
        outputWidth = getFlowGraphWidth(2),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMaxFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassC(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 3147708945,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 2,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 30,
        waterIterations = 15,
        sdfMultiplier = 10.0f,
        sdfNoiseFrequency = 2048.0f,
        sdfNoiseAmplitude = 0.1f,
        outputWidth = getFlowGraphWidth(3),
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
) = terraform(
        randomSeed = 129387645,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 3,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 30,
        waterIterations = 10,
        sdfMultiplier = 0.0f,
        sdfNoiseFrequency = 1.0f,
        sdfNoiseAmplitude = 0.0f,
        outputWidth = getFlowGraphWidth(4),
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
) = terraform(
        randomSeed = 234562346,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 4,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 12,
        waterIterations = 5,
        sdfMultiplier = 0.0f,
        sdfNoiseFrequency = 1.0f,
        sdfNoiseAmplitude = 0.0f,
        outputWidth = getFlowGraphWidth(5),
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMinFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)

fun terraformPassF(
        previousMap: FloatArrayMatrix,
        landSdf: ShortArrayMatrix,
        areaIndex: ByteArrayMatrix,
        upliftMap: ShortArrayMatrix
) = terraform(
        randomSeed = 8498375,
        mapScale = mapScale,
        terrainProfiles = terrainProfiles,
        underwaterProfile = underwaterProfile,
        flowGraphId = 5,
        previousMap = previousMap,
        erosionSettingsIndex = 2,
        iterations = 8,
        waterIterations = 2,
        sdfMultiplier = 0.0f,
        sdfNoiseFrequency = 1.0f,
        sdfNoiseAmplitude = 0.0f,
        outputWidth = outputWidth,
        landSdf = landSdf,
        areaIndex = areaIndex,
        upliftMap = upliftMap,
        beachFunction = beachShelfMinFun,
        renderMode = terraformRenderMode,
        blur = terraformBlur
)
