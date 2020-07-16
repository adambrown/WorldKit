package wk.utils

import wk.api.*
import wk.api.timeIt
import wk.internal.ext.fastFloorI
import wk.utils.hdr.HdrImage
import wk.utils.hdr.readHdr
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.math.*

typealias Vec3 = M3<Float, Float, Float>
typealias Vec2 = M2<Float, Float>
typealias CVec2 = C2<Float, Float>

private const val PI: Float = Math.PI.toFloat()

private fun vec3(a: Float = 0.0f, b: Float = 0.0f, c: Float = 0.0f): Vec3 = T3(a, b, c)

private fun vec2(a: Float = 0.0f, b: Float = 0.0f): Vec2 = T2(a, b)

private class IrradianceCoefficients private constructor(val r: DoubleArray, val g: DoubleArray, val b: DoubleArray) {
    constructor() : this(DoubleArray(9), DoubleArray(9), DoubleArray(9))
}

private class CustomFloat(val s: Int, e: Int, m: Int) {

    private val shiftS = e + m
    private val maskS = (1 shl s) - 1 shl shiftS
    private val maskSInv = maskS.inv()
    private val maskE = (1 shl e) - 1 shl m
    private val maskEInv = maskE.inv()
    private val maskMInv = ((1 shl m) - 1).inv()
    private val fromInfinity = (1 shl e) - 1 shl 23
    private val fromMagic = java.lang.Float.intBitsToFloat((1 shl e - 1) - 1 shl 23)
    private val toInfinity = java.lang.Float.intBitsToFloat(0x80 + ((1 shl e - 1) - 1) shl 23)
    private val toMagic = java.lang.Float.intBitsToFloat(0xFE - ((1 shl e - 1) - 1) shl 23)
    private val a = 1 shl (22 - m)
    private val b = (a - 1).inv()
    private val c = 1 shl (m - 1)
    private val d = 23 - m
    private val e = (1 shl (e + m)) - 1
    private val f = 0xFF shl 23
    private val g = 31 - shiftS

    fun fromFloat(f: Float): Int {
        var out = 0
        if (s == 0 && f < 0.0f) {
            return out
        }
        var bits = java.lang.Float.floatToIntBits(f)
        val sign = bits ushr 31
        bits = bits and 0x7FFFFFFF
        if ((bits ushr 23) and 0xFF == 0xFF) {
            out = (out and maskEInv) or maskE
            out = (out and maskMInv) or (if (bits and 0x7FFFFF == 0) 0 else c)
        } else {
            bits = bits and b
            bits += a
            bits = java.lang.Float.floatToIntBits(java.lang.Float.intBitsToFloat(bits) * fromMagic)
            bits = if (bits < fromInfinity) bits else fromInfinity
            out = bits ushr d
        }
        out = (out and maskSInv) or (sign shl shiftS)
        return out
    }

    fun toFloat(i: Int): Float {
        var out = (i and e) shl d
        out = java.lang.Float.floatToIntBits(java.lang.Float.intBitsToFloat(out) * toMagic)
        if (java.lang.Float.intBitsToFloat(out) >= toInfinity) {
            out = out or f
        }
        out = out or ((i and maskS) shl g)
        return java.lang.Float.intBitsToFloat(out)
    }
}

private object GLR11G11B10 {

    private val fp11 = CustomFloat(0, 5, 6)
    private val fp10 = CustomFloat(0, 5, 5)

    fun fromFloats(r: Float, g: Float, b: Float): Int {
        val rBits = fp11.fromFloat(r)
        val gBits = fp11.fromFloat(g)
        val bBits = fp10.fromFloat(b)
        return rBits or (gBits shl 11) or (bBits shl 22)
    }

    fun toFloats(bits: Int, ret: Vec3): Vec3 {
        ret.first = fp11.toFloat(bits and 0x7FF)
        ret.second = fp11.toFloat((bits ushr 11) and 0x7FF)
        ret.third = fp10.toFloat((bits ushr 22) and 0x3FF)
        return ret
    }
}

object BrdfCalculator {

    @JvmStatic
    fun main(vararg args: String) {
        val fileBase = File(args[1])
        val fileParent = fileBase.parentFile
        val fileName = fileBase.nameWithoutExtension

        val brdf = timeIt("brdf") {
            val brdf = FloatArray(256 * 256 * 2)
            (0 until 256).inParallel { y ->
                val yOff = y * 512
                val t2 = vec2()
                val t3 = vec3()
                val roughness = (1.0f - ((y + 0.5f) / 256.0f)).coerceIn(0.0f, 1.0f)
                for (x in 0 until 256) {
                    val xOff = x * 2
                    val nov = ((x + 0.5f) / 256.0f).coerceIn(0.0f, 1.0f)
                    val rg = integrateBrdf(roughness, nov, t3, t2)
                    brdf[yOff + xOff] = rg.first
                    brdf[yOff + xOff + 1] = rg.second
                }
            }
            brdf
        }

        val brdfFile = File(fileParent, "$fileName-brdf.tex")
        val brdfOut = DataOutputStream(GZIPOutputStream(brdfFile.outputStream().buffered()).buffered())
        brdfOut.use { brdfStream ->
            brdfStream.writeInt(1)
            brdfStream.writeInt(256)
            for (y in 0 until 256) {
                val yOff = y * 512
                for (x in 0 until 256) {
                    val xOff = x * 2
                    val r = brdf[yOff + xOff]
                    val g = brdf[yOff + xOff + 1]
                    brdfStream.writeInt(((r * 65535.0f).roundToInt().coerceIn(0, 65535) shl 16) or (g * 65535.0f).roundToInt().coerceIn(0, 65535))
                }
            }
        }
    }
}

object EnvMapCalculator {

    @JvmStatic
    fun main(vararg args: String) {
        val fileBase = File(args[1])
        val fileParent = fileBase.parentFile
        val fileName = fileBase.nameWithoutExtension

        val hdr = timeIt("readHdr") { readHdr(File(args[0])) }

        val cubeWidth = 2048
        val (cubeMapFaces, irradianceCoefficients) = timeIt("equirectToCubeMapFaces") { equirectToCubeMapFaces(hdr, cubeWidth, args[2].toFloat(), args[3].toFloat(), args[4].toFloat()) }

        val irradianceWidth = 256
        val irradianceFaces = timeIt("computeIrradianceMap") { computeIrradianceMap(irradianceCoefficients, irradianceWidth) }

        val specularWidth = 256
        val (specMapWidths, specMaps) = timeIt("computeSpecularMap") {
            val specMapWidths = arrayOf(
                    specularWidth,
                    specularWidth / 2,
                    specularWidth / 4,
                    specularWidth / 8,
                    specularWidth / 16,
                    specularWidth / 32,
                    specularWidth / 64,
                    specularWidth / 128)
            val specMaps = ArrayList<Array<FloatArray>>(8)
            specMaps.add(computeSpecularMap(hdr, specMapWidths[0], 0.0f))
            specMaps.add(computeSpecularMap(hdr, specMapWidths[1], 0.16666666667f))
            specMaps.add(computeSpecularMap(hdr, specMapWidths[2], 0.33333333333f))
            specMaps.add(computeSpecularMap(hdr, specMapWidths[3], 0.5f))
            specMaps.add(computeSpecularMap(hdr, specMapWidths[4], 0.66666666667f))
            specMaps.add(computeSpecularMap(hdr, specMapWidths[5], 0.83333333333f))
            specMaps.add(computeSpecularMap(hdr, specMapWidths[6], 1.0f))
            specMaps.add(computeSpecularMap(hdr, specMapWidths[7], 1.0f))
            specMapWidths to specMaps
        }

        timeIt("write") {
            val iblFile = File(fileParent, "$fileName.ibl")
            val iblOut = DataOutputStream(iblFile.outputStream().buffered())
            iblOut.use { stream ->
                fun writeFace(face: FloatArray, width: Int) {
                    for (y in 0 until width) {
                        val yOff = y * width * 3
                        for (x in 0 until width) {
                            val i = yOff + x * 3
                            stream.writeInt(GLR11G11B10.fromFloats(face[i], face[i + 1], face[i + 2]))
                        }
                    }
                }

                stream.writeInt(1)
                stream.writeInt(cubeWidth)
                (0 until 6).forEach {
                    writeFace(cubeMapFaces[it], cubeWidth)
                }

                stream.writeInt(1)
                stream.writeInt(irradianceWidth)
                (0 until 6).forEach {
                    writeFace(irradianceFaces[it], irradianceWidth)
                }

                stream.writeInt(specMaps.size)
                stream.writeInt(specularWidth)
                (0 until 6).forEach { i ->
                    for (s in 0 until specMaps.size) {
                        writeFace(specMaps[s][i], specMapWidths[s])
                    }
                }
            }
        }
    }
}

private fun integrateBrdf(roughness: Float, nov: Float, tmp: Vec3, ret: Vec2): Vec2 {
    val k = (roughness * roughness) * 0.5f
    val ki = 1.0f - k
    val va = sqrt(1.0f - nov * nov)
    val vb = 0.0f
    var a = 0.0f
    var b = 0.0f
    for (i in 0 until 1024) {
        hammersley(i, ret)
        val (ha, hb, hc) = importanceSampleGgx(ret, roughness, 0.0f, 0.0f, 1.0f, tmp)
        val vDotH = (va * ha + (vb * hb) + (nov * hc))
        val lc = hc * vDotH * 2.0f - nov
        val nol = lc.coerceIn(0.0f, 1.0f)
        val noh = hc.coerceIn(0.0f, 1.0f)
        val voh = vDotH.coerceIn(0.0f, 1.0f)
        if (nol > 0) {
            val g = (nol / (nol * ki + k)) * (nov / (nov * ki + k))
            val gVis = g * voh / (noh * nov)
            val fc = (1.0f - voh).pow(5.0f)
            a += (1 - fc) * gVis
            b += fc * gVis
        }
    }
    ret.first = a / 1024.0f
    ret.second = b / 1024.0f
    return ret
}

private fun computeIrradianceMap(irradianceCoefficients: IrradianceCoefficients, faceWidth: Int): Array<FloatArray> {
    val faces = Array(6) { FloatArray(faceWidth * faceWidth * 3) }
    (0..5).inParallel(6) {
        computeIrradianceForFace(it, faceWidth, faces[it], irradianceCoefficients)
    }
    return faces
}

private fun computeIrradianceForFace(faceId: Int, faceWidth: Int, facePixels: FloatArray, irradianceCoefficients: IrradianceCoefficients) {
    val iFaceWidth2 = 2.0f / faceWidth
    (0 until faceWidth).inParallel(
            context = { vec3() }
    ) { t3, py ->
        val yOff = py * faceWidth * 3
        for (px in 0 until faceWidth) {
            val i = yOff + px * 3
            val (x, y, z) = lookupVector(iFaceWidth2, px, py, faceId, t3)
            facePixels[i] = computeIrradiance(irradianceCoefficients.r, x, y, z)
            facePixels[i + 1] = computeIrradiance(irradianceCoefficients.g, x, y, z)
            facePixels[i + 2] = computeIrradiance(irradianceCoefficients.b, x, y, z)
        }
    }
}

private fun computeIrradiance(c: DoubleArray, x: Float, y: Float, z: Float) =
        (0.429043 * c[8] * (x * x - y * y) +
                0.743125 * c[7] * z * z +
                0.886227 * c[0] -
                0.247708 * c[7] +
                0.858086 * (c[6] * x * y + c[4] * x * z + c[5] * y * z) +
                1.023328 * (c[1] * x + c[3] * y + c[2] * z)).toFloat()

private fun equirectToCubeMapFaces(image: HdrImage, faceWidth: Int, offset: Float, multiplier: Float, cap: Float): Pair<Array<FloatArray>, IrradianceCoefficients> {
    val faces = Array(6) { FloatArray(faceWidth * faceWidth * 3) }
    val irradianceCoefficients = IrradianceCoefficients()
    (0..5).inParallel(
            threads = 6,
            context = { IrradianceCoefficients() }
    ) { localCoeffs, i ->
        transformSingleFace(image, i, faceWidth, faces[i], localCoeffs, offset, multiplier, cap)
    }.forEach { localCoeffs ->
        localCoeffs.r.forEachIndexed { i, value ->
            irradianceCoefficients.r[i] += value
        }
        localCoeffs.g.forEachIndexed { i, value ->
            irradianceCoefficients.g[i] += value
        }
        localCoeffs.b.forEachIndexed { i, value ->
            irradianceCoefficients.b[i] += value
        }
    }
    return faces to irradianceCoefficients
}

private fun transformSingleFace(hdrImage: HdrImage, faceId: Int, faceWidth: Int, facePixels: FloatArray, irradianceCoefficients: IrradianceCoefficients, offset: Float, multiplier: Float, cap: Float) {
    val iFaceWidth2 = 2.0f / faceWidth
    (0 until faceWidth).inParallel(
            context = { IrradianceCoefficients() to vec3() }
    ) { (localCoeffs, t3), py ->
        val yOff = py * faceWidth * 3
        for (px in 0 until faceWidth) {
            val (x, y, z) = lookupVector(iFaceWidth2, px, py, faceId, t3)
            val (r, g, b) = sampleEquirect(hdrImage, x, y, z, t3)
            val i = yOff + px * 3
            facePixels[i] = r
            facePixels[i + 1] = g
            facePixels[i + 2] = b
            val solidAngle = solidAngle(px, py, faceWidth)
            val c0 = 0.282095 * solidAngle
            localCoeffs.r[0] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c0
            localCoeffs.g[0] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c0
            localCoeffs.b[0] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c0
            val c1 = 0.488603 * x * solidAngle
            localCoeffs.r[1] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c1
            localCoeffs.g[1] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c1
            localCoeffs.b[1] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c1
            val c2 = 0.488603 * z * solidAngle
            localCoeffs.r[2] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c2
            localCoeffs.g[2] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c2
            localCoeffs.b[2] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c2
            val c3 = 0.488603 * y * solidAngle
            localCoeffs.r[3] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c3
            localCoeffs.g[3] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c3
            localCoeffs.b[3] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c3
            val c4 = 1.092548 * x * z * solidAngle
            localCoeffs.r[4] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c4
            localCoeffs.g[4] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c4
            localCoeffs.b[4] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c4
            val c5 = 1.092548 * y * z * solidAngle
            localCoeffs.r[5] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c5
            localCoeffs.g[5] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c5
            localCoeffs.b[5] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c5
            val c6 = 1.092548 * y * x * solidAngle
            localCoeffs.r[6] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c6
            localCoeffs.g[6] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c6
            localCoeffs.b[6] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c6
            val c7 = (0.946176 * z * z - 0.315392) * solidAngle
            localCoeffs.r[7] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c7
            localCoeffs.g[7] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c7
            localCoeffs.b[7] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c7
            val c8 = 0.54627 * (x * x - y * y) * solidAngle
            localCoeffs.r[8] += ((r - offset) * multiplier).coerceIn(0.0f, cap) * c8
            localCoeffs.g[8] += ((g - offset) * multiplier).coerceIn(0.0f, cap) * c8
            localCoeffs.b[8] += ((b - offset) * multiplier).coerceIn(0.0f, cap) * c8
        }
    }.forEach { (localCoeffs) ->
        (0..8).forEach { i ->
            irradianceCoefficients.r[i] += localCoeffs.r[i]
            irradianceCoefficients.g[i] += localCoeffs.g[i]
            irradianceCoefficients.b[i] += localCoeffs.b[i]
        }
    }
}

private fun lookupVector(iFaceWidth2: Float, x: Int, y: Int, faceId: Int, ret: Vec3): Vec3 {
    val a = iFaceWidth2 * x
    val b = iFaceWidth2 * y
    when (faceId) {
        0 -> { // right  (+x)
            ret.first = 1.0f - a
            ret.second = 1.0f
            ret.third = 1.0f - b
        }
        1 -> { // left   (-x)
            ret.first = a - 1.0f
            ret.second = -1.0f
            ret.third = 1.0f - b
        }
        2 -> { // top    (+y)
            ret.first = b - 1.0f
            ret.second = a - 1.0f
            ret.third = 1.0f
        }
        3 -> { // bottom (-y)
            ret.first = 1.0f - b
            ret.second = a - 1.0f
            ret.third = -1.0f
        }
        4 -> { // front  (+z)
            ret.first = 1.0f
            ret.second = a - 1.0f
            ret.third = 1.0f - b
        }
        else -> { // back   (-z)
            ret.first = -1.0f
            ret.second = 1.0f - a
            ret.third = 1.0f - b
        }
    }
    return ret
}

private fun solidAngle(u: Int, v: Int, size: Int): Float {
    val shiftedU = 2.0f * (u + 0.5f) / size - 1.0f
    val shiftedV = 2.0f * (v + 0.5f) / size - 1.0f

    val invResolution = 1.0f / size

    val x0 = shiftedU - invResolution
    val y0 = shiftedV - invResolution
    val x1 = shiftedU + invResolution
    val y1 = shiftedV + invResolution

    return areaElement(x0, y0) - areaElement(x0, y1) - areaElement(x1, y0) + areaElement(x1, y1)
}

private fun areaElement(x: Float, y: Float) = atan2(x * y, sqrt(x * x + y * y + 1))

private fun computeSpecularMap(hdrMap: HdrImage, faceWidth: Int, roughness: Float): Array<FloatArray> {
    val faces = Array(6) { FloatArray(faceWidth * faceWidth * 3) }
    (0..5).inParallel(6) {
        computeSpecularForFace(it, faceWidth, faces[it], hdrMap, roughness)
    }
    return faces
}

private fun computeSpecularForFace(faceId: Int, faceWidth: Int, facePixels: FloatArray,hdrMap: HdrImage, roughness: Float) {
    val iFaceWidth2 = 2.0f / faceWidth
    (0 until faceWidth).inParallel(
            threads = min(PARALLELISM, faceWidth),
            context = { vec3() }
    ) { t3, py ->
        val yOff = py * faceWidth * 3
        for (px in 0 until faceWidth) {
            val (x, y, z) = lookupVector(iFaceWidth2, px, py, faceId, t3)
            val (r, g, b) = prefilterEnvMap(roughness, x, y, z, hdrMap, t3)
            val i = yOff + px * 3
            facePixels[i] = r
            facePixels[i + 1] = g
            facePixels[i + 2] = b
        }
    }
}

private fun prefilterEnvMap(roughness: Float, rx: Float, ry: Float, rz: Float, hdrMap: HdrImage, ret: Vec3): Vec3 {
    var cr = 0.0f
    var cg = 0.0f
    var cb = 0.0f
    var totalWeight = 0.0f
    for (i in 0 until 1024) {
        hammersley(i, ret)
        val (ha, hb, hc) = importanceSampleGgx(ret, roughness, rx, ry, rz, ret)
        val rDotH2 = ((rx * ha) + (ry * hb) + (rz * hc)) * 2.0f
        val la = rDotH2 * ha - rx
        val lb = rDotH2 * hb - ry
        val lc = rDotH2 * hc - rz
        val rDotL = (rx * la) + (ry * lb) + (rz * lc)
        val nol = rDotL.coerceIn(0.0f, 1.0f)
        if (nol > 0) {
            val (sr, sg, sb) = sampleEquirect(hdrMap, la, lb, lc, ret)
            cr += sr.coerceIn(0.0f, 1.0f) * nol
            cg += sg.coerceIn(0.0f, 1.0f) * nol
            cb += sb.coerceIn(0.0f, 1.0f) * nol
            totalWeight += nol
        }
    }
    totalWeight = 1.0f / totalWeight
    ret.first = cr * totalWeight
    ret.second = cg * totalWeight
    ret.third = cb * totalWeight
    return ret
}

private fun sampleEquirect(hdrImage: HdrImage, x: Float, y: Float, z: Float, ret: Vec3): Vec3 {
    val inWidth = hdrImage.width
    val inHeight = hdrImage.height

    val theta = atan2(y, x)
    val rad = sqrt(x * x + y * y)
    val phi = atan2(z, rad)

    var uf = 2.0f * (inWidth / 4) * (theta + PI) / PI
    var vf = 2.0f * (inWidth / 4) * (PI / 2.0f - phi) / PI
    if (uf < 0.0) uf += inWidth
    if (vf < 0.0) vf += inHeight
    val ui = fastFloorI(uf)
    val vi = fastFloorI(vf)

    val u2 = ui + 1
    val v2 = vi + 1
    val mu = uf - ui
    val nu = vf - vi

    val uiClamp = ui % inWidth
    val u2Clamp = u2 % inWidth
    val viClamp = vi.coerceIn(0, inHeight - 1)
    val v2Clamp = v2.coerceIn(0, inHeight - 1)

    val rA = hdrImage[uiClamp, viClamp, 0]
    val rB = hdrImage[u2Clamp, viClamp, 0]
    val rC = hdrImage[uiClamp, v2Clamp, 0]
    val rD = hdrImage[u2Clamp, v2Clamp, 0]

    val gA = hdrImage[uiClamp, viClamp, 1]
    val gB = hdrImage[u2Clamp, viClamp, 1]
    val gC = hdrImage[uiClamp, v2Clamp, 1]
    val gD = hdrImage[u2Clamp, v2Clamp, 1]

    val bA = hdrImage[uiClamp, viClamp, 2]
    val bB = hdrImage[u2Clamp, viClamp, 2]
    val bC = hdrImage[uiClamp, v2Clamp, 2]
    val bD = hdrImage[u2Clamp, v2Clamp, 2]

    val imu = 1.0f - mu
    val inu = 1.0f - nu
    ret.first = rA * imu * inu + rB * mu * inu + rC * imu * nu + rD * mu * nu
    ret.second = gA * imu * inu + gB * mu * inu + gC * imu * nu + gD * mu * nu
    ret.third = bA * imu * inu + bB * mu * inu + bC * imu * nu + bD * mu * nu
    return ret
}

private fun importanceSampleGgx(xi: CVec2, roughness: Float, nx: Float, ny: Float, nz: Float, ret: Vec3): Vec3 {
    val a = roughness * roughness
    val phi = (2.0 * PI * xi.first).toFloat()
    val cosTheta = sqrt((1.0f - xi.second) / (1.0f + (a * a - 1.0f) * xi.second))
    val sinTheta = sqrt(1.0f - cosTheta * cosTheta)
    val ha = (sinTheta * cos(phi))
    val hb = (sinTheta * sin(phi))
    val up = abs(nz) < 0.999f
    val ux = if (up) 0.0f else 1.0f
    val uy = 0.0f
    val uz = if (up) 1.0f else 0.0f
    var txx = (uy * nz) - (uz * ny)
    var txy = (uz * nx) - (ux * nz)
    var txz = (ux * ny) - (uy * nx)
    val txl = 1.0f / sqrt(txx * txx + txy * txy + txz * txz)
    txx *= txl
    txy *= txl
    txz *= txl
    val tyx = (ny * txz) - (nz * txy)
    val tyy = (nz * txx) - (nx * txz)
    val tyz = (nx * txy) - (ny * txx)
    ret.first = nx * cosTheta + txx * ha + tyx * hb
    ret.second = ny * cosTheta + txy * ha + tyy * hb
    ret.third = nz * cosTheta + txz * ha + tyz * hb
    return ret
}

private fun hammersley(i: Int, ret: Vec2) {
    ret.first = i / 1024.0f
    var bits = (i shl 16) or (i shr 16)
    bits = ((bits and 0x55555555) shl 1) or ((bits and (0xAAAAAAAA).toInt()) ushr 1)
    bits = ((bits and 0x33333333) shl 2) or ((bits and (0xCCCCCCCC).toInt()) ushr 2)
    bits = ((bits and 0x0F0F0F0F) shl 4) or ((bits and (0xF0F0F0F0).toInt()) ushr 4)
    bits = ((bits and 0x00FF00FF) shl 8) or ((bits and (0xFF00FF00).toInt()) ushr 8)
    ret.second = (bits.toLong() and 0xFFFFFFFFL).toFloat() * 2.3283064365386963e-10f
}
