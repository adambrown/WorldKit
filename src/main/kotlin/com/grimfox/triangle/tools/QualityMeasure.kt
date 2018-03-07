package com.grimfox.triangle.tools

import com.grimfox.triangle.Mesh
import com.grimfox.triangle.geometry.Point

class QualityMeasure {

    private var areaMeasure: AreaMeasure = AreaMeasure()

    private var alphaMeasure: AlphaMeasure = AlphaMeasure()

    private var qMeasure: Q_Measure = Q_Measure()

    internal var mesh: Mesh? = null

    val areaMinimum: Double
        get() = areaMeasure.area_min

    val areaMaximum: Double
        get() = areaMeasure.area_max

    val areaRatio: Double
        get() = areaMeasure.area_max / areaMeasure.area_min

    val alphaMinimum: Double
        get() = alphaMeasure.alpha_min

    val alphaMaximum: Double
        get() = alphaMeasure.alpha_max

    val alphaAverage: Double
        get() = alphaMeasure.alpha_ave

    val alphaArea: Double
        get() = alphaMeasure.alpha_area

    val q_Minimum: Double
        get() = qMeasure.q_min

    val q_Maximum: Double
        get() = qMeasure.q_max

    val q_Average: Double
        get() = qMeasure.q_ave

    val q_Area: Double
        get() = qMeasure.q_area

    fun update(mesh: Mesh) {
        this.mesh = mesh
        areaMeasure.reset()
        alphaMeasure.reset()
        qMeasure.reset()
        compute()
    }

    private fun compute() {
        var a: Point
        var b: Point
        var c: Point
        var ab: Double
        var bc: Double
        var ca: Double
        var lx: Double
        var ly: Double
        var area: Double
        var n = 0
        for (tri in mesh!!._triangles) {
            n++
            a = tri.vertices[0]!!
            b = tri.vertices[1]!!
            c = tri.vertices[2]!!
            lx = a.x - b.x
            ly = a.y - b.y
            ab = Math.sqrt(lx * lx + ly * ly)
            lx = b.x - c.x
            ly = b.y - c.y
            bc = Math.sqrt(lx * lx + ly * ly)
            lx = c.x - a.x
            ly = c.y - a.y
            ca = Math.sqrt(lx * lx + ly * ly)
            area = areaMeasure.measure(a, b, c)
            alphaMeasure.measure(ab, bc, ca, area)
            qMeasure.measure(ab, bc, ca, area)
        }
        alphaMeasure.normalize(n, areaMeasure.area_total)
        qMeasure.normalize(n, areaMeasure.area_total)
    }

    fun bandwidth(): Int {
        if (mesh == null)
            return 0
        var ml = 0
        var mu = 0
        var gi: Int
        var gj: Int
        for (tri in mesh!!._triangles) {
            for (j in 0..2) {
                gi = tri.getVertex(j)!!.id
                for (k in 0..2) {
                    gj = tri.getVertex(k)!!.id
                    mu = Math.max(mu, gj - gi)
                    ml = Math.max(ml, gi - gj)
                }
            }
        }
        return ml + 1 + mu
    }

    internal class AreaMeasure {
        var area_min = java.lang.Double.MAX_VALUE
        var area_max = -java.lang.Double.MAX_VALUE
        var area_total = 0.0
        var area_zero = 0
        fun reset() {
            area_min = java.lang.Double.MAX_VALUE
            area_max = -java.lang.Double.MAX_VALUE
            area_total = 0.0
            area_zero = 0
        }

        fun measure(a: Point, b: Point, c: Point): Double {
            val area = 0.5 * Math.abs(a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
            area_min = Math.min(area_min, area)
            area_max = Math.max(area_max, area)
            area_total += area
            if (area == 0.0) {
                area_zero += 1
            }
            return area
        }
    }

    internal class AlphaMeasure {
        var alpha_min: Double = 0.toDouble()
        var alpha_max: Double = 0.toDouble()
        var alpha_ave: Double = 0.toDouble()
        var alpha_area: Double = 0.toDouble()
        fun reset() {
            alpha_min = java.lang.Double.MAX_VALUE
            alpha_max = -java.lang.Double.MAX_VALUE
            alpha_ave = 0.0
            alpha_area = 0.0
        }

        fun acos(c: Double): Double {
            if (c <= -1.0) {
                return Math.PI
            } else if (1.0 <= c) {
                return 0.0
            } else {
                return Math.acos(c)
            }
        }

        fun measure(ab: Double, bc: Double, ca: Double, area: Double): Double {
            var alpha = java.lang.Double.MAX_VALUE
            val ab2 = ab * ab
            val bc2 = bc * bc
            val ca2 = ca * ca
            val a_angle: Double
            val b_angle: Double
            val c_angle: Double
            if (ab == 0.0 && bc == 0.0 && ca == 0.0) {
                a_angle = 2.0 * Math.PI / 3.0
                b_angle = 2.0 * Math.PI / 3.0
                c_angle = 2.0 * Math.PI / 3.0
            } else {
                if (ca == 0.0 || ab == 0.0) {
                    a_angle = Math.PI
                } else {
                    a_angle = acos((ca2 + ab2 - bc2) / (2.0 * ca * ab))
                }
                if (ab == 0.0 || bc == 0.0) {
                    b_angle = Math.PI
                } else {
                    b_angle = acos((ab2 + bc2 - ca2) / (2.0 * ab * bc))
                }
                if (bc == 0.0 || ca == 0.0) {
                    c_angle = Math.PI
                } else {
                    c_angle = acos((bc2 + ca2 - ab2) / (2.0 * bc * ca))
                }
            }
            alpha = Math.min(alpha, a_angle)
            alpha = Math.min(alpha, b_angle)
            alpha = Math.min(alpha, c_angle)
            alpha = alpha * 3.0 / Math.PI
            alpha_ave += alpha
            alpha_area += area * alpha
            alpha_min = Math.min(alpha, alpha_min)
            alpha_max = Math.max(alpha, alpha_max)
            return alpha
        }

        fun normalize(n: Int, area_total: Double) {
            if (n > 0) {
                alpha_ave /= n.toDouble()
            } else {
                alpha_ave = 0.0
            }
            if (0.0 < area_total) {
                alpha_area /= area_total
            } else {
                alpha_area = 0.0
            }
        }
    }

    internal class Q_Measure {
        var q_min: Double = 0.toDouble()
        var q_max: Double = 0.toDouble()
        var q_ave: Double = 0.toDouble()
        var q_area: Double = 0.toDouble()
        fun reset() {
            q_min = java.lang.Double.MAX_VALUE
            q_max = -java.lang.Double.MAX_VALUE
            q_ave = 0.0
            q_area = 0.0
        }

        fun measure(ab: Double, bc: Double, ca: Double, area: Double): Double {
            val q = (bc + ca - ab) * (ca + ab - bc) * (ab + bc - ca) / (ab * bc * ca)
            q_min = Math.min(q_min, q)
            q_max = Math.max(q_max, q)
            q_ave += q
            q_area += q * area
            return q
        }

        fun normalize(n: Int, area_total: Double) {
            if (n > 0) {
                q_ave /= n.toDouble()
            } else {
                q_ave = 0.0
            }
            if (area_total > 0.0) {
                q_area /= area_total
            } else {
                q_area = 0.0
            }
        }
    }
}
