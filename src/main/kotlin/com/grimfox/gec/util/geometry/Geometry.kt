package com.grimfox.gec.util.geometry

import com.grimfox.gec.model.Matrix
import com.grimfox.gec.model.geometry.Point3F
import com.grimfox.gec.model.geometry.complement
import java.lang.Math.*

fun renderTriangle(a: Point3F, b: Point3F, c: Point3F, heightMap: Matrix<Float>) {
    val stride = heightMap.width
    val strideF = stride.toFloat()
    val p1 = Point3F(a.x * strideF, a.y * strideF, a.z)
    val p2 = Point3F(b.x * strideF, b.y * strideF, b.z)
    val p3 = Point3F(c.x * strideF, c.y * strideF, c.z)
    val normal = (p2 - p1).cross(p3 - p1)
    val d = -normal.dot(p1)
    val na = normal.a
    val nb = normal.b
    val nc = normal.c
    val minZ = min(p1.z, p2.z, p3.z)
    val maxZ = max(p1.z, p2.z, p3.z)
    if (nc == 0.0f || p1.z.isNaN() || p2.z.isNaN() || p3.z.isNaN()) {
        return
    }

    fun interpolateZ(x: Int, y: Int): Float {
        return clamp(-((na * x) + (nb * y) + d) / nc, minZ, maxZ)
    }

    val ax: Int = round(16.0f * p1.x)
    val bx: Int = round(16.0f * p2.x)
    val cx: Int = round(16.0f * p3.x)

    val ay: Int = round(16.0f * p1.y)
    val by: Int = round(16.0f * p2.y)
    val cy: Int = round(16.0f * p3.y)

    val dxAb: Int = ax - bx
    val dxBc: Int = bx - cx
    val dxCa: Int = cx - ax

    val dyAb: Int = ay - by
    val dyBc: Int = by - cy
    val dyCa: Int = cy - ay

    val fdxAb: Int = dxAb shl 4
    val fdxBc: Int = dxBc shl 4
    val fdxCa: Int = dxCa shl 4

    val fdyAb: Int = dyAb shl 4
    val fdyBc: Int = dyBc shl 4
    val fdyCa: Int = dyCa shl 4

    var minX: Int = (min(ax, bx, cx) + 0xF) shr 4
    val maxX: Int = (max(ax, bx, cx) + 0xF) shr 4
    var minY: Int = (min(ay, by, cy) + 0xF) shr 4
    val maxY: Int = (max(ay, by, cy) + 0xF) shr 4

    val q: Int = 8

    minX = minX and (q - 1).complement()
    minY = minY and (q - 1).complement()

    var c1: Int = dyAb * ax - dxAb * ay
    var c2: Int = dyBc * bx - dxBc * by
    var c3: Int = dyCa * cx - dxCa * cy

    if(dyAb < 0 || (dyAb == 0 && dxAb > 0)) c1++
    if(dyBc < 0 || (dyBc == 0 && dxBc > 0)) c2++
    if(dyCa < 0 || (dyCa == 0 && dxCa > 0)) c3++

    var blockYOffset: Int = minY * stride

    var y: Int = minY
    while (y < maxY) {
        var x: Int = minX
        while (x < maxX) {
            val x0: Int = x shl 4
            val x1: Int = (x + q - 1) shl 4
            val y0: Int = y shl 4
            val y1: Int = (y + q - 1) shl 4

            val a00: Int = if (c1 + dxAb * y0 - dyAb * x0 > 0) 1 else 0
            val a10: Int = if (c1 + dxAb * y0 - dyAb * x1 > 0) 1 else 0
            val a01: Int = if (c1 + dxAb * y1 - dyAb * x0 > 0) 1 else 0
            val a11: Int = if (c1 + dxAb * y1 - dyAb * x1 > 0) 1 else 0
            val hs1 = a00 or (a10 shl 1) or (a01 shl 2) or (a11 shl 3)

            val b00: Int = if (c2 + dxBc * y0 - dyBc * x0 > 0) 1 else 0
            val b10: Int = if (c2 + dxBc * y0 - dyBc * x1 > 0) 1 else 0
            val b01: Int = if (c2 + dxBc * y1 - dyBc * x0 > 0) 1 else 0
            val b11: Int = if (c2 + dxBc * y1 - dyBc * x1 > 0) 1 else 0
            val hs2: Int = b00 or (b10 shl 1) or (b01 shl 2) or (b11 shl 3)

            val c00: Int = if (c3 + dxCa * y0 - dyCa * x0 > 0) 1 else 0
            val c10: Int = if (c3 + dxCa * y0 - dyCa * x1 > 0) 1 else 0
            val c01: Int = if (c3 + dxCa * y1 - dyCa * x0 > 0) 1 else 0
            val c11: Int = if (c3 + dxCa * y1 - dyCa * x1 > 0) 1 else 0
            val hs3: Int = c00 or (c10 shl 1) or (c01 shl 2) or (c11 shl 3)

            if (hs1 == 0x0 || hs2 == 0x0 || hs3 == 0x0) {
                x += q
                continue
            }

            var yOffset: Int = blockYOffset

            if (hs1 == 0xF && hs2 == 0xF && hs3 == 0xF) {
                var iy: Int = y
                val endY = y + q
                while (iy < endY) {
                    var ix: Int = x
                    val endX = x + q
                    while (ix < endX) {
                        val index = yOffset + ix
                        if (index < heightMap.size) {
                            heightMap[index] = interpolateZ(ix, iy)
                        }
                        ix++
                    }
                    yOffset += stride
                    iy++
                }
            } else {
                var cy1: Int = c1 + dxAb * y0 - dyAb * x0
                var cy2: Int = c2 + dxBc * y0 - dyBc * x0
                var cy3: Int = c3 + dxCa * y0 - dyCa * x0

                var iy = y
                val endY = y + q
                while (iy < endY) {
                    var cx1: Int = cy1
                    var cx2: Int = cy2
                    var cx3: Int = cy3

                    var ix = x
                    val endX = x + q
                    while (ix < endX) {
                        if(cx1 > 0 && cx2 > 0 && cx3 > 0) {
                            val index = yOffset + ix
                            if (index < heightMap.size) {
                                heightMap[index] = interpolateZ(ix, iy)
                            }
                        }
                        cx1 -= fdyAb
                        cx2 -= fdyBc
                        cx3 -= fdyCa
                        ix++
                    }

                    cy1 += fdxAb
                    cy2 += fdxBc
                    cy3 += fdxCa

                    yOffset += stride
                    iy++
                }
            }
            x += q
        }
        blockYOffset += q * stride
        y += q
    }
}

fun max(a: Int, b: Int, c: Int) = max(max(a, b), c)

fun min(a: Int, b: Int, c: Int) = min(min(a, b), c)

fun min(a: Float, b: Float, c: Float) = min(min(a, b), c)

fun max(a: Float, b: Float, c: Float) = max(max(a, b), c)

fun clamp(f: Float, min: Float, max: Float) = min(max(min, f), max)

