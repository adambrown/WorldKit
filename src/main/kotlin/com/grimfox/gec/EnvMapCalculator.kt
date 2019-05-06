package com.grimfox.gec

import com.grimfox.gec.hdr.*
import com.grimfox.gec.model.FloatArrayMatrix
import com.grimfox.gec.model.geometry.*
import com.grimfox.gec.util.clamp
import java.awt.image.BufferedImage
import java.io.*
import java.lang.Math.pow
import java.util.zip.GZIPOutputStream
import javax.imageio.ImageIO
import kotlin.math.*


class IrradianceCoefficients private constructor(val r: DoubleArray, val g: DoubleArray, val b: DoubleArray) {
    constructor() : this(DoubleArray(9), DoubleArray(9), DoubleArray(9))
}

object EnvMapCalculator {

    const val gamma = 1 / 2.2

    @JvmStatic
    fun main(vararg args: String) {
        val fileBase = File(args[1])
        val fileParent = fileBase.parentFile
        val fileName = fileBase.nameWithoutExtension

        val brdfFile = File(fileParent, "$fileName-brdf.tex")
        val brdfOut = DataOutputStream(GZIPOutputStream(brdfFile.outputStream().buffered()).buffered())
        val outputBrdf = BufferedImage(256, 256, BufferedImage.TYPE_3BYTE_BGR)
        val outputBrdfData = outputBrdf.raster
        brdfOut.use { brdfStream ->
            brdfStream.writeInt(1)
            brdfStream.writeInt(256)
            for (y in 0 until 256) {
                val roughness = clamp(1.0f - ((y + 0.5f) / 256.0f), 0.0f, 1.0f)
                for (x in 0 until 256) {
                    val nov = clamp((x + 0.5f) / 256.0f, 0.0f, 1.0f)
                    val rg = integrateBrdf(roughness, nov)
                    brdfStream.writeFloat(rg.a)
                    brdfStream.writeFloat(rg.b)
                    outputBrdfData.setSample(x, y, 0, linearFloatToLinearByte(rg.a))
                    outputBrdfData.setSample(x, y, 1, linearFloatToLinearByte(rg.b))
                }
            }
            ImageIO.write(outputBrdf, "png", File(fileParent, "$fileName-brdf.png"))
        }

        val hdr = HDREncoder.readHDR(File(args[0]), true)

        val cubeWidth = 2048
        val (cubemapFaces, irradianceCoefficients) = equirectToCubemapFaces(hdr, cubeWidth, args[2].toDouble(), args[3].toDouble(), args[4].toDouble())

        val irradianceWidth = 256
        val irradianceFaces = computeIrradianceMap(irradianceCoefficients, irradianceWidth)

        val specularWidth = 256
        val specMapWidths = arrayOf(
                specularWidth,
                specularWidth / 2,
                specularWidth / 4,
                specularWidth / 8,
                specularWidth / 16,
                specularWidth / 32,
                specularWidth / 64,
                specularWidth / 128)
        val specMaps = ArrayList<Array<Triple<FloatArrayMatrix, FloatArrayMatrix, FloatArrayMatrix>>>(8)
        specMaps.add(computeSpecularMap(hdr, specMapWidths[0], 0.0f))
        specMaps.add(computeSpecularMap(hdr, specMapWidths[1], 0.16666666667f))
        specMaps.add(computeSpecularMap(hdr, specMapWidths[2], 0.33333333333f))
        specMaps.add(computeSpecularMap(hdr, specMapWidths[3], 0.5f))
        specMaps.add(computeSpecularMap(hdr, specMapWidths[4], 0.66666666667f))
        specMaps.add(computeSpecularMap(hdr, specMapWidths[5], 0.83333333333f))
        specMaps.add(computeSpecularMap(hdr, specMapWidths[6], 1.0f))
        specMaps.add(computeSpecularMap(hdr, specMapWidths[7], 1.0f))

        val skyFile = File(fileParent, "$fileName-sky.cub")
        val skyOut = DataOutputStream(GZIPOutputStream(skyFile.outputStream().buffered()).buffered())
        val irradianceFile = File(fileParent, "$fileName-irra.cub")
        val irradianceOut = DataOutputStream(GZIPOutputStream(irradianceFile.outputStream().buffered()).buffered())
        val specFile = File(fileParent, "$fileName-spec.cub")
        val specOut = DataOutputStream(GZIPOutputStream(specFile.outputStream().buffered()).buffered())
        irradianceOut.use { irradianceStream -> specOut.use { specStream -> skyOut.use { skyStream ->
            skyOut.writeInt(1)
            skyOut.writeInt(cubeWidth)
            irradianceOut.writeInt(1)
            irradianceOut.writeInt(irradianceWidth)
            specOut.writeInt(specMaps.size)
            specOut.writeInt(specularWidth)
            for (i in 0 until 6) {
                val cubeFace = cubemapFaces[i]
                val outputCube = BufferedImage(cubeWidth, cubeWidth, BufferedImage.TYPE_3BYTE_BGR)
                val outputCubeData = outputCube.raster

                for (y in 0 until cubeWidth) {
                    for (x in 0 until cubeWidth) {
                        val r = linearFloatToGammaByte(cubeFace.first[x, y])
                        val g = linearFloatToGammaByte(cubeFace.second[x, y])
                        val b = linearFloatToGammaByte(cubeFace.third[x, y])
                        skyStream.writeByte(r)
                        skyStream.writeByte(g)
                        skyStream.writeByte(b)
                        outputCubeData.setSample(x, y, 0, r)
                        outputCubeData.setSample(x, y, 1, g)
                        outputCubeData.setSample(x, y, 2, b)
                    }
                }
                ImageIO.write(outputCube, "png", File(fileParent, "$fileName-cube-$i.png"))

                val irradianceFace = irradianceFaces[i]
                val outputIrradiance = BufferedImage(irradianceWidth, irradianceWidth, BufferedImage.TYPE_3BYTE_BGR)
                val outputIrradianceData = outputIrradiance.raster

                for (y in 0 until irradianceWidth) {
                    for (x in 0 until irradianceWidth) {
                        val r = irradianceFace.first[x, y]
                        val g = irradianceFace.second[x, y]
                        val b = irradianceFace.third[x, y]
                        irradianceStream.writeFloat(r)
                        irradianceStream.writeFloat(g)
                        irradianceStream.writeFloat(b)
                        outputIrradianceData.setSample(x, y, 0, linearFloatToGammaByte(r))
                        outputIrradianceData.setSample(x, y, 1, linearFloatToGammaByte(g))
                        outputIrradianceData.setSample(x, y, 2, linearFloatToGammaByte(b))
                    }
                }
                ImageIO.write(outputIrradiance, "png", File(fileParent, "$fileName-irra-$i.png"))

                for (s in 0 until specMaps.size) {
                    val width = specMapWidths[s]
                    val specFace = specMaps[s][i]
                    val outputSpec = BufferedImage(width, width, BufferedImage.TYPE_3BYTE_BGR)
                    val outputSpecData = outputSpec.raster

                    for (y in 0 until width) {
                        for (x in 0 until width) {
                            val r = specFace.first[x, y]
                            val g = specFace.second[x, y]
                            val b = specFace.third[x, y]
                            specStream.writeFloat(r)
                            specStream.writeFloat(g)
                            specStream.writeFloat(b)
                            outputSpecData.setSample(x, y, 0, linearFloatToGammaByte(r))
                            outputSpecData.setSample(x, y, 1, linearFloatToGammaByte(g))
                            outputSpecData.setSample(x, y, 2, linearFloatToGammaByte(b))
                        }
                    }
                    ImageIO.write(outputSpec, "png", File(fileParent, "$fileName-spec$s-$i.png"))
                }
            }
        } } }
    }

    private fun integrateBrdf(roughness: Float, nov: Float): Vector2F {
        val v = Vector3F(sqrt(1.0f - nov * nov), 0.0f, nov)
        var a = 0.0
        var b = 0.0
        val numSamples = 1024
        for (i in 0 until numSamples) {
            val xi = hammersley(i, numSamples)
            val h = importanceSampleGgx(xi, roughness, Vector3F(0.0f, 0.0f, 1.0f))
            val l = 2 * v.dot(h) * h - v
            val nol = clamp(l.c, 0.0f, 1.0f)
            val noh = clamp(h.c, 0.0f, 1.0f)
            val voh = clamp(v.dot(h), 0.0f, 1.0f)
            if (nol > 0) {
                val g = gSmith(roughness, nov, nol)
                val gVis = g * voh / (noh * nov)
                val fc = pow(1.0 - voh, 5.0)
                a += (1 - fc) * gVis
                b += fc * gVis
            }
        }
        return Vector2F((a / numSamples).toFloat(), (b / numSamples).toFloat())
    }

    private fun gSmith(a: Float, nov: Float, nol: Float): Float {
        return ggx(nol, a * a) * ggx(nov, a * a)
    }
    private fun ggx(nov: Float, a: Float): Float {
        val k = a / 2
        return nov / (nov * (1.0f - k) + k)
    }

    private fun computeIrradianceMap(irradianceCoefficients: IrradianceCoefficients, faceSize: Int): Array<Triple<FloatArrayMatrix, FloatArrayMatrix, FloatArrayMatrix>> {
        val faces = Array(6) { Triple(FloatArrayMatrix(faceSize), FloatArrayMatrix(faceSize), FloatArrayMatrix(faceSize)) }
        faces.mapIndexed {i, it -> i to it}.parallelStream().forEach { (i, it) ->
            computeIrradianceForFace(i, it.first, it.second, it.third, irradianceCoefficients)
        }
        return faces
    }

    private fun computeIrradianceForFace(faceIdx: Int, facePixelsR: FloatArrayMatrix, facePixelsG: FloatArrayMatrix, facePixelsB: FloatArrayMatrix, irradianceCoefficients: IrradianceCoefficients) {
        val faceWidth = facePixelsR.width

        val iFaceWidth2 = 2.0 / faceWidth

        for (j in 0 until faceWidth) {
            for (i in 0 until faceWidth) {
                val a = iFaceWidth2 * i
                val b = iFaceWidth2 * j
                var x = 0.0
                var y = 0.0
                var z = 0.0

                when (faceIdx) {
                    0 -> { // right  (+x)
                        x = 1.0 - a
                        y = 1.0
                        z = 1.0 - b
                    }
                    1 -> { // left   (-x)
                        x = a - 1.0
                        y = -1.0
                        z = 1.0 - b
                    }
                    2 -> { // top    (+y)
                        x = b - 1.0
                        y = a - 1.0
                        z = 1.0
                    }
                    3 -> { // bottom (-y)
                        x = 1.0 - b
                        y = a - 1.0
                        z = -1.0
                    }
                    4 -> { // front  (+z)
                        x = 1.0
                        y = a - 1.0
                        z = 1.0 - b
                    }
                    5 -> { // back   (-z)
                        x = -1.0
                        y = 1.0 - a
                        z = 1.0 - b
                    }
                }

                facePixelsR[i, j] = computeIrradiance(irradianceCoefficients.r, x, y, z)
                facePixelsG[i, j] = computeIrradiance(irradianceCoefficients.g, x, y, z)
                facePixelsB[i, j] = computeIrradiance(irradianceCoefficients.b, x, y, z)
            }
        }
    }

    private fun computeIrradiance(c: DoubleArray, x: Double, y: Double, z: Double): Float =
            (
                    0.429043 * c[8] * (x * x - y * y) +
                    0.743125 * c[7] * z * z +
                    0.886227 * c[0] -
                    0.247708 * c[7] +
                    0.858086 * (c[6] * x * y + c[4] * x * z + c[5] * y * z) +
                    1.023328 * (c[1] * x + c[3] * y + c[2] * z)
            ).toFloat()

    private fun linearFloatToGammaByte(value: Float) = clamp(round(pow(value.toDouble(), gamma) * 255.0).toInt(), 0, 255)

    private fun linearFloatToLinearByte(value: Float) = clamp(Math.round(value * 255.0f), 0, 255)

    private fun equirectToCubemapFaces(image: HDRImage, faceSize: Int, offset: Double, multiplier: Double, cap: Double): Pair<Array<Triple<FloatArrayMatrix, FloatArrayMatrix, FloatArrayMatrix>>, IrradianceCoefficients> {
        val faces = Array(6) { Triple(FloatArrayMatrix(faceSize), FloatArrayMatrix(faceSize), FloatArrayMatrix(faceSize)) }
        val coeffs = IrradianceCoefficients()
        faces.mapIndexed {i, it -> i to it}.parallelStream().map { (i, it) ->
            val localCoeffs = IrradianceCoefficients()
            transformSingleFace(image, i, it.first, it.second, it.third, localCoeffs, offset, multiplier, cap)
            localCoeffs
        }.forEach { localCoeffs ->
            localCoeffs.r.forEachIndexed { i, value ->
                coeffs.r[i] += value
            }
            localCoeffs.g.forEachIndexed { i, value ->
                coeffs.g[i] += value
            }
            localCoeffs.b.forEachIndexed { i, value ->
                coeffs.b[i] += value
            }
        }
        return faces to coeffs
    }

    private fun transformSingleFace(inPixels: HDRImage, faceIdx: Int, facePixelsR: FloatArrayMatrix, facePixelsG: FloatArrayMatrix, facePixelsB: FloatArrayMatrix, irradianceCoefficients: IrradianceCoefficients, offset: Double, multiplier: Double, cap: Double) {
        val inWidth = inPixels.width
        val inHeight = inPixels.height

        val faceWidth = facePixelsR.width

        val iFaceWidth2 = 2.0 / faceWidth

        for (j in 0 until faceWidth) {
            for (i in 0 until faceWidth) {
                val a = iFaceWidth2 * i
                val b = iFaceWidth2 * j
                var x = 0.0
                var y = 0.0
                var z = 0.0

                when (faceIdx) {
                    0 -> { // right  (+x)
                        x = 1.0 - a
                        y = 1.0
                        z = 1.0 - b
                    }
                    1 -> { // left   (-x)
                        x = a - 1.0
                        y = -1.0
                        z = 1.0 - b
                    }
                    2 -> { // top    (+y)
                        x = b - 1.0
                        y = a - 1.0
                        z = 1.0
                    }
                    3 -> { // bottom (-y)
                        x = 1.0 - b
                        y = a - 1.0
                        z = -1.0
                    }
                    4 -> { // front  (+z)
                        x = 1.0
                        y = a - 1.0
                        z = 1.0 - b
                    }
                    5 -> { // back   (-z)
                        x = -1.0
                        y = 1.0 - a
                        z = 1.0 - b
                    }
                }

                val theta = atan2(y, x)
                val rad = sqrt(x * x + y * y)
                val phi = atan2(z, rad)

                val uf = 2.0 * (inWidth / 4) * (theta + PI) / PI
                val vf = 2.0 * (inWidth / 4) * (PI / 2 - phi) / PI
                val ui = round(floor(uf)).toInt()
                val vi = round(floor(vf)).toInt()

                val u2 = ui + 1
                val v2 = vi + 1
                val mu = uf - ui
                val nu = vf - vi

                val uiClamp = ui % inWidth
                val u2Clamp = u2 % inWidth
                val viClamp = clamp(vi, 0, inHeight - 1)
                val v2Clamp = clamp(v2, 0, inHeight - 1)

                val rA = inPixels.getPixelValue(uiClamp, viClamp, 0)
                val rB = inPixels.getPixelValue(u2Clamp, viClamp, 0)
                val rC = inPixels.getPixelValue(uiClamp, v2Clamp, 0)
                val rD = inPixels.getPixelValue(u2Clamp, v2Clamp, 0)

                val gA = inPixels.getPixelValue(uiClamp, viClamp, 1)
                val gB = inPixels.getPixelValue(u2Clamp, viClamp, 1)
                val gC = inPixels.getPixelValue(uiClamp, v2Clamp, 1)
                val gD = inPixels.getPixelValue(u2Clamp, v2Clamp, 1)

                val bA = inPixels.getPixelValue(uiClamp, viClamp, 2)
                val bB = inPixels.getPixelValue(u2Clamp, viClamp, 2)
                val bC = inPixels.getPixelValue(uiClamp, v2Clamp, 2)
                val bD = inPixels.getPixelValue(u2Clamp, v2Clamp, 2)

                val red = ((rA * (1.0 - mu) * (1.0 - nu) + rB * mu * (1.0 - nu) + rC * (1.0 - mu) * nu + rD * mu * nu)).toFloat()
                val green = ((gA * (1.0 - mu) * (1.0 - nu) + gB * mu * (1.0 - nu) + gC * (1.0 - mu) * nu + gD * mu * nu)).toFloat()
                val blue = ((bA * (1.0 - mu) * (1.0 - nu) + bB * mu * (1.0 - nu) + bC * (1.0 - mu) * nu + bD * mu * nu)).toFloat()

                val solidAngle = solidAngle(i, j, faceWidth)

                facePixelsR[i, j] = red
                facePixelsG[i, j] = green
                facePixelsB[i, j] = blue

                val yCoefficients = DoubleArray(9)
                yCoefficients[0] = 0.282095
                yCoefficients[1] = 0.488603 * x
                yCoefficients[2] = 0.488603 * z
                yCoefficients[3] = 0.488603 * y
                yCoefficients[4] = 1.092548 * x * z
                yCoefficients[5] = 1.092548 * y * z
                yCoefficients[6] = 1.092548 * y * x
                yCoefficients[7] = 0.946176 * z * z - 0.315392
                yCoefficients[8] = 0.54627 * (x * x - y * y)

                yCoefficients.forEachIndexed { index, coefficient ->
                    irradianceCoefficients.r[index] += clamp((red - offset) * multiplier, 0.0, cap) * coefficient * solidAngle
                    irradianceCoefficients.g[index] += clamp((green - offset) * multiplier, 0.0, cap) * coefficient * solidAngle
                    irradianceCoefficients.b[index] += clamp((blue - offset) * multiplier, 0.0, cap) * coefficient * solidAngle
                }
            }
        }
    }

    private fun solidAngle(u: Int, v: Int, size: Int): Double {
        val shiftedU = 2.0 * (u + 0.5) / size - 1.0
        val shiftedV = 2.0 * (v + 0.5) / size - 1.0

        val invResolution = 1.0 / size

        val x0 = shiftedU - invResolution
        val y0 = shiftedV - invResolution
        val x1 = shiftedU + invResolution
        val y1 = shiftedV + invResolution

        return areaElement(x0, y0) - areaElement(x0, y1) - areaElement(x1, y0) + areaElement(x1, y1)
    }

    private fun areaElement(x: Double, y: Double): Double = atan2(x * y, sqrt(x * x + y * y + 1))

    private fun computeSpecularMap(hdrMap: HDRImage, faceSize: Int, roughness: Float): Array<Triple<FloatArrayMatrix, FloatArrayMatrix, FloatArrayMatrix>> {
        val faces = Array(6) { Triple(FloatArrayMatrix(faceSize), FloatArrayMatrix(faceSize), FloatArrayMatrix(faceSize)) }
        faces.mapIndexed {i, it -> i to it}.parallelStream().forEach { (i, it) ->
            computeSpecularForFace(i, it.first, it.second, it.third, hdrMap, roughness)
        }
        return faces
    }

    private fun computeSpecularForFace(faceIdx: Int, facePixelsR: FloatArrayMatrix, facePixelsG: FloatArrayMatrix, facePixelsB: FloatArrayMatrix, hdrMap: HDRImage, roughness: Float) {
        val faceWidth = facePixelsR.width

        val iFaceWidth2 = 2.0 / faceWidth

        for (j in 0 until faceWidth) {
            for (i in 0 until faceWidth) {
                val a = iFaceWidth2 * i
                val b = iFaceWidth2 * j
                var x = 0.0
                var y = 0.0
                var z = 0.0

                when (faceIdx) {
                    0 -> { // right  (+x)
                        x = 1.0 - a
                        y = 1.0
                        z = 1.0 - b
                    }
                    1 -> { // left   (-x)
                        x = a - 1.0
                        y = -1.0
                        z = 1.0 - b
                    }
                    2 -> { // top    (+y)
                        x = b - 1.0
                        y = a - 1.0
                        z = 1.0
                    }
                    3 -> { // bottom (-y)
                        x = 1.0 - b
                        y = a - 1.0
                        z = -1.0
                    }
                    4 -> { // front  (+z)
                        x = 1.0
                        y = a - 1.0
                        z = 1.0 - b
                    }
                    5 -> { // back   (-z)
                        x = -1.0
                        y = 1.0 - a
                        z = 1.0 - b
                    }
                }

                val r = Vector3F(x.toFloat(), y.toFloat(), z.toFloat())
                val color = prefilterEnvMap(roughness, r, hdrMap)
                facePixelsR[i, j] = color.a
                facePixelsG[i, j] = color.b
                facePixelsB[i, j] = color.c
            }
        }
    }

    private fun prefilterEnvMap(roughness: Float, r: Vector3F, hdrMap: HDRImage): Vector3F {
        var totalWeight = 0.0f
        val prefilteredColor = Vector3F(0.0f, 0.0f, 0.0f)
        val numSamples = 1024
        for (i in 0 until numSamples) {
            val xi = hammersley(i, numSamples)
            val h = importanceSampleGgx(xi, roughness, r)
            val l = 2 * r.dot(h) * h - r
            val nol = clamp(r.dot(l), 0.0f, 1.0f)
            if (nol > 0) {
                prefilteredColor.plusAssign(sampleEquirect(hdrMap, l) * nol)
                totalWeight += nol
            }
        }
        return prefilteredColor / totalWeight
    }

    private fun sampleEquirect(inPixels: HDRImage, lookup: Vector3F): Vector3F {
        val inWidth = inPixels.width
        val inHeight = inPixels.height

        val x = lookup.a
        val y = lookup.b
        val z = lookup.c

        val theta = atan2(y, x)
        val rad = sqrt(x * x + y * y)
        val phi = atan2(z, rad)

        var uf = 2.0 * (inWidth / 4) * (theta + PI) / PI
        var vf = 2.0 * (inWidth / 4) * (PI / 2 - phi) / PI
        if (uf < 0.0) uf += inWidth
        if (vf < 0.0) vf += inHeight
        val ui = round(floor(uf)).toInt()
        val vi = round(floor(vf)).toInt()

        val u2 = ui + 1
        val v2 = vi + 1
        val mu = uf - ui
        val nu = vf - vi

        val uiClamp = ui % inWidth
        val u2Clamp = u2 % inWidth
        val viClamp = clamp(vi, 0, inHeight - 1)
        val v2Clamp = clamp(v2, 0, inHeight - 1)

        val rA = inPixels.getPixelValue(uiClamp, viClamp, 0)
        val rB = inPixels.getPixelValue(u2Clamp, viClamp, 0)
        val rC = inPixels.getPixelValue(uiClamp, v2Clamp, 0)
        val rD = inPixels.getPixelValue(u2Clamp, v2Clamp, 0)

        val gA = inPixels.getPixelValue(uiClamp, viClamp, 1)
        val gB = inPixels.getPixelValue(u2Clamp, viClamp, 1)
        val gC = inPixels.getPixelValue(uiClamp, v2Clamp, 1)
        val gD = inPixels.getPixelValue(u2Clamp, v2Clamp, 1)

        val bA = inPixels.getPixelValue(uiClamp, viClamp, 2)
        val bB = inPixels.getPixelValue(u2Clamp, viClamp, 2)
        val bC = inPixels.getPixelValue(uiClamp, v2Clamp, 2)
        val bD = inPixels.getPixelValue(u2Clamp, v2Clamp, 2)

        val red = ((rA * (1.0 - mu) * (1.0 - nu) + rB * mu * (1.0 - nu) + rC * (1.0 - mu) * nu + rD * mu * nu)).toFloat()
        val green = ((gA * (1.0 - mu) * (1.0 - nu) + gB * mu * (1.0 - nu) + gC * (1.0 - mu) * nu + gD * mu * nu)).toFloat()
        val blue = ((bA * (1.0 - mu) * (1.0 - nu) + bB * mu * (1.0 - nu) + bC * (1.0 - mu) * nu + bD * mu * nu)).toFloat()

        return Vector3F(clamp(red, 0.0f, 1.0f), clamp(green, 0.0f, 1.0f), clamp(blue, 0.0f, 1.0f))
    }

    private fun importanceSampleGgx(xi: Vector2F, roughness: Float, n: Vector3F): Vector3F {
        val a = roughness * roughness
        val phi = 2 * PI * xi.a
        val cosTheta = sqrt ((1 - xi.b) / (1 + (a * a - 1) * xi.b))
        val sinTheta = sqrt (1 - cosTheta * cosTheta)
        val h = Vector3F((sinTheta * cos(phi)).toFloat(), (sinTheta * sin(phi)).toFloat(), cosTheta)
        val upVector = if (abs (n.c) < 0.999f) Vector3F(0.0f, 0.0f, 1.0f) else Vector3F(1.0f, 0.0f, 0.0f)
        val tangentX = upVector.cross(n).getUnit()
        val tangentY = n.cross(tangentX)
        return tangentX * h.a + tangentY * h.b + n * h.c
    }

    private fun hammersley(i: Int, n: Int): Vector2F = Vector2F(i.toFloat() / n.toFloat(), radicalInverseVdC(i))

    private fun radicalInverseVdC(bitsIn: Int): Float {
        var bits = (bitsIn shl 16) or (bitsIn shr 16)
        bits = ((bits and 0x55555555) shl 1) or ((bits and (0xAAAAAAAA).toInt()) ushr 1)
        bits = ((bits and 0x33333333) shl 2) or ((bits and (0xCCCCCCCC).toInt()) ushr 2)
        bits = ((bits and 0x0F0F0F0F) shl 4) or ((bits and (0xF0F0F0F0).toInt()) ushr 4)
        bits = ((bits and 0x00FF00FF) shl 8) or ((bits and (0xFF00FF00).toInt()) ushr 8)
        return (bits.toLong() and 0xFFFFFFFFL).toFloat() * 2.3283064365386963e-10f
    }

    operator fun Float.times(other: Vector3F) = other * this
}