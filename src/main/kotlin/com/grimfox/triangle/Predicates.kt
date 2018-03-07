package com.grimfox.triangle

import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.tools.Statistic

class Predicates {

    companion object {

        val default: Predicates by lazy {
            Predicates()
        }

        private val splitter: Double

        private val resultErrBound: Double

        private val ccErrBoundA: Double

        private val ccErrBoundB: Double

        private val ccErrBoundC: Double

        private val iCcErrBoundA: Double

        private val iCcErrBoundB: Double

        private val iCcErrBoundC: Double

        init {
            val half: Double = 0.5
            var check: Double
            var lastCheck: Double
            var everyOther: Boolean
            everyOther = true
            var epsilon = 1.0
            var splitter = 1.0
            check = 1.0
            do {
                lastCheck = check
                epsilon *= half
                if (everyOther) {
                    splitter *= 2.0
                }
                everyOther = !everyOther
                check = 1.0 + epsilon
            } while (check != 1.0 && check != lastCheck)
            splitter += 1.0
            this.splitter = splitter
            resultErrBound = (3.0 + 8.0 * epsilon) * epsilon
            ccErrBoundA = (3.0 + 16.0 * epsilon) * epsilon
            ccErrBoundB = (2.0 + 12.0 * epsilon) * epsilon
            ccErrBoundC = (9.0 + 64.0 * epsilon) * epsilon * epsilon
            iCcErrBoundA = (10.0 + 96.0 * epsilon) * epsilon
            iCcErrBoundB = (4.0 + 48.0 * epsilon) * epsilon
            iCcErrBoundC = (44.0 + 576.0 * epsilon) * epsilon * epsilon
        }
    }

    fun counterClockwise(pa: Point, pb: Point, pc: Point, noExact: Boolean = false): Double { // positive if counterclockwise
        Statistic.counterClockwiseCount.andIncrement
        val detLeft = (pa.x - pc.x) * (pb.y - pc.y)
        val detRight = (pa.y - pc.y) * (pb.x - pc.x)
        val det = detLeft - detRight
        if (noExact) {
            return det
        }
        val detSum: Double
        if (detLeft > 0.0) {
            if (detRight <= 0.0) {
                return det
            } else {
                detSum = detLeft + detRight
            }
        } else if (detLeft < 0.0) {
            if (detRight >= 0.0) {
                return det
            } else {
                detSum = -detLeft - detRight
            }
        } else {
            return det
        }
        val errBound = ccErrBoundA * detSum
        if (det >= errBound || -det >= errBound) {
            return det
        }
        Statistic.counterClockwiseAdaptCount.andIncrement
        return counterClockwiseAdapt(pa, pb, pc, detSum)
    }

    fun inCircle(pa: Point, pb: Point, pc: Point, pd: Point, noExact: Boolean): Double {
        Statistic.inCircleCount.andIncrement
        val adx = pa.x - pd.x
        val bdx = pb.x - pd.x
        val cdx = pc.x - pd.x
        val ady = pa.y - pd.y
        val bdy = pb.y - pd.y
        val cdy = pc.y - pd.y
        val bdxCdy = bdx * cdy
        val cdxBdy = cdx * bdy
        val aLift = adx * adx + ady * ady
        val cdxAdy = cdx * ady
        val adxCdy = adx * cdy
        val bLift = bdx * bdx + bdy * bdy
        val adxBdy = adx * bdy
        val bdxAdy = bdx * ady
        val cLift = cdx * cdx + cdy * cdy
        val det = aLift * (bdxCdy - cdxBdy) + bLift * (cdxAdy - adxCdy) + cLift * (adxBdy - bdxAdy)
        if (noExact) {
            return det
        }
        val permanent = (Math.abs(bdxCdy) + Math.abs(cdxBdy)) * aLift + (Math.abs(cdxAdy) + Math.abs(adxCdy)) * bLift + (Math.abs(adxBdy) + Math.abs(bdxAdy)) * cLift
        val errBound = iCcErrBoundA * permanent
        if (det > errBound || -det > errBound) {
            return det
        }
        Statistic.inCircleAdaptCount.andIncrement
        return inCircleAdapt(pa, pb, pc, pd, permanent)
    }

    fun nonRegular(pa: Point, pb: Point, pc: Point, pd: Point): Double {
        return inCircle(pa, pb, pc, pd, false)
    }

    fun findCircumcenter(org: Point, dest: Point, apex: Point, xi: Reference<Double>, eta: Reference<Double>, offConstant: Double, noExact: Boolean): Point {
        Statistic.circumcenterCount.andIncrement
        val xdo = dest.x - org.x
        val ydo = dest.y - org.y
        val xao = apex.x - org.x
        val yao = apex.y - org.y
        val doDist = xdo * xdo + ydo * ydo
        val aoDist = xao * xao + yao * yao
        val daDist = (dest.x - apex.x) * (dest.x - apex.x) + (dest.y - apex.y) * (dest.y - apex.y)
        val denominator: Double
        if (noExact) {
            denominator = 0.5 / (xdo * yao - xao * ydo)
        } else {
            denominator = 0.5 / counterClockwise(dest, apex, org, false)
            Statistic.counterClockwiseCount.andDecrement
        }
        var dx = (yao * doDist - ydo * aoDist) * denominator
        var dy = (xdo * aoDist - xao * doDist) * denominator
        if (doDist < aoDist && doDist < daDist) {
            if (offConstant > 0.0) {
                val dxOff = 0.5 * xdo - offConstant * ydo
                val dyOff = 0.5 * ydo + offConstant * xdo
                if (dxOff * dxOff + dyOff * dyOff < dx * dx + dy * dy) {
                    dx = dxOff
                    dy = dyOff
                }
            }
        } else if (aoDist < daDist) {
            if (offConstant > 0.0) {
                val dxOff = 0.5 * xao + offConstant * yao
                val dyOff = 0.5 * yao - offConstant * xao
                if (dxOff * dxOff + dyOff * dyOff < dx * dx + dy * dy) {
                    dx = dxOff
                    dy = dyOff
                }
            }
        } else {
            if (offConstant > 0.0) {
                val dxOff = 0.5 * (apex.x - dest.x) - offConstant * (apex.y - dest.y)
                val dyOff = 0.5 * (apex.y - dest.y) + offConstant * (apex.x - dest.x)
                if (dxOff * dxOff + dyOff * dyOff < (dx - xdo) * (dx - xdo) + (dy - ydo) * (dy - ydo)) {
                    dx = xdo + dxOff
                    dy = ydo + dyOff
                }
            }
        }
        xi.value = (yao * dx - xao * dy) * (2.0 * denominator)
        eta.value = (xdo * dy - ydo * dx) * (2.0 * denominator)
        return Point(org.x + dx, org.y + dy)
    }

    fun findCircumcenter(org: Point, dest: Point, apex: Point, xi: Reference<Double>? = null, eta: Reference<Double>? = null, noExact: Boolean = false): Point {
        Statistic.circumcenterCount.andIncrement
        val xdo = dest.x - org.x
        val ydo = dest.y - org.y
        val xao = apex.x - org.x
        val yao = apex.y - org.y
        val doDist = xdo * xdo + ydo * ydo
        val aoDist = xao * xao + yao * yao
        val denominator: Double
        if (noExact) {
            denominator = 0.5 / (xdo * yao - xao * ydo)
        } else {
            denominator = 0.5 / counterClockwise(dest, apex, org, false)
            Statistic.counterClockwiseCount.andDecrement
        }
        val dx = (yao * doDist - ydo * aoDist) * denominator
        val dy = (xdo * aoDist - xao * doDist) * denominator
        xi?.value = (yao * dx - xao * dy) * (2.0 * denominator)
        eta?.value = (xdo * dy - ydo * dx) * (2.0 * denominator)
        return Point(org.x + dx, org.y + dy)
    }

    private fun fastExpansionSumZeroElim(eLength: Int, e: DoubleArray, fLength: Int, f: DoubleArray, h: DoubleArray): Int {
        var Q: Double
        var Qnew: Double
        var hh: Double
        var bvirt: Double
        var avirt: Double
        var bround: Double
        var around: Double
        var enow: Double
        var fnow: Double
        enow = e[0]
        fnow = f[0]
        var findex = 0
        var eindex = findex
        if (fnow > enow == fnow > -enow) {
            Q = enow
            enow = e[++eindex]
        } else {
            Q = fnow
            fnow = f[++findex]
        }
        var hindex = 0
        if (eindex < eLength && findex < fLength) {
            if (fnow > enow == fnow > -enow) {
                Qnew = enow + Q
                bvirt = Qnew - enow
                hh = Q - bvirt
                enow = e[++eindex]
            } else {
                Qnew = fnow + Q
                bvirt = Qnew - fnow
                hh = Q - bvirt
                fnow = f[++findex]
            }
            Q = Qnew
            if (hh != 0.0) {
                h[hindex++] = hh
            }
            while (eindex < eLength && findex < fLength) {
                if (fnow > enow == fnow > -enow) {
                    Qnew = Q + enow
                    bvirt = Qnew - Q
                    avirt = Qnew - bvirt
                    bround = enow - bvirt
                    around = Q - avirt
                    hh = around + bround
                    enow = e[++eindex]
                } else {
                    Qnew = Q + fnow
                    bvirt = Qnew - Q
                    avirt = Qnew - bvirt
                    bround = fnow - bvirt
                    around = Q - avirt
                    hh = around + bround
                    fnow = f[++findex]
                }
                Q = Qnew
                if (hh != 0.0) {
                    h[hindex++] = hh
                }
            }
        }
        while (eindex < eLength) {
            Qnew = Q + enow
            bvirt = Qnew - Q
            avirt = Qnew - bvirt
            bround = enow - bvirt
            around = Q - avirt
            hh = around + bround
            enow = e[++eindex]
            Q = Qnew
            if (hh != 0.0) {
                h[hindex++] = hh
            }
        }
        while (findex < fLength) {
            Qnew = Q + fnow
            bvirt = Qnew - Q
            avirt = Qnew - bvirt
            bround = fnow - bvirt
            around = Q - avirt
            hh = around + bround
            fnow = f[++findex]
            Q = Qnew
            if (hh != 0.0) {
                h[hindex++] = hh
            }
        }
        if (Q != 0.0 || hindex == 0) {
            h[hindex++] = Q
        }
        return hindex
    }

    private fun scaleExpansionZeroElim(eLength: Int, e: DoubleArray, b: Double, h: DoubleArray): Int {
        var Q: Double
        var sum: Double
        var hh: Double
        var product1: Double
        var product0: Double
        var enow: Double
        var bvirt: Double
        var avirt: Double
        var bround: Double
        var around: Double
        var c: Double
        var abig: Double
        var ahi: Double
        var alo: Double
        val bhi: Double
        val blo: Double
        var err1: Double
        var err2: Double
        var err3: Double
        c = splitter * b
        abig = c - b
        bhi = c - abig
        blo = b - bhi
        Q = e[0] * b
        c = splitter * e[0]
        abig = c - e[0]
        ahi = c - abig
        alo = e[0] - ahi
        err1 = Q - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        hh = alo * blo - err3
        var hindex = 0
        if (hh != 0.0) {
            h[hindex++] = hh
        }
        var eindex = 1
        while (eindex < eLength) {
            enow = e[eindex]
            product1 = enow * b
            c = splitter * enow
            abig = c - enow
            ahi = c - abig
            alo = enow - ahi
            err1 = product1 - ahi * bhi
            err2 = err1 - alo * bhi
            err3 = err2 - ahi * blo
            product0 = alo * blo - err3
            sum = Q + product0
            bvirt = sum - Q
            avirt = sum - bvirt
            bround = product0 - bvirt
            around = Q - avirt
            hh = around + bround
            if (hh != 0.0) {
                h[hindex++] = hh
            }
            Q = product1 + sum
            bvirt = Q - product1
            hh = sum - bvirt
            if (hh != 0.0) {
                h[hindex++] = hh
            }
            eindex++
        }
        if (Q != 0.0 || hindex == 0) {
            h[hindex++] = Q
        }
        return hindex
    }

    private fun estimate(elen: Int, e: DoubleArray): Double {
        var Q: Double = e[0]
        var eindex: Int = 1
        while (eindex < elen) {
            Q += e[eindex]
            eindex++
        }
        return Q
    }

    private fun counterClockwiseAdapt(pa: Point, pb: Point, pc: Point, detSum: Double): Double {
        val acxTail: Double
        val acyTail: Double
        val bcxTail: Double
        val bcyTail: Double
        val detLeft: Double
        val detRight: Double
        val detLeftTail: Double
        val detRightTail: Double
        var det: Double
        val b = DoubleArray(5)
        val u = DoubleArray(5)
        val c1 = DoubleArray(8)
        val c2 = DoubleArray(12)
        val d = DoubleArray(16)
        val b3: Double
        val c1Length: Int
        val c2Length: Int
        val dLength: Int
        var u3: Double
        var s1: Double
        var t1: Double
        var s0: Double
        var t0: Double
        var bvirt: Double
        var avirt: Double
        var bround: Double
        var around: Double
        var c: Double
        var abig: Double
        var ahi: Double
        var alo: Double
        var bhi: Double
        var blo: Double
        var err1: Double
        var err2: Double
        var err3: Double
        var _i: Double
        var _j: Double
        var _0: Double
        val acx = pa.x - pc.x
        val bcx = pb.x - pc.x
        val acy = pa.y - pc.y
        val bcy = pb.y - pc.y
        detLeft = acx * bcy
        c = splitter * acx
        abig = c - acx
        ahi = c - abig
        alo = acx - ahi
        c = splitter * bcy
        abig = c - bcy
        bhi = c - abig
        blo = bcy - bhi
        err1 = detLeft - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        detLeftTail = alo * blo - err3
        detRight = acy * bcx
        c = splitter * acy
        abig = c - acy
        ahi = c - abig
        alo = acy - ahi
        c = splitter * bcx
        abig = c - bcx
        bhi = c - abig
        blo = bcx - bhi
        err1 = detRight - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        detRightTail = alo * blo - err3
        _i = detLeftTail - detRightTail
        bvirt = detLeftTail - _i
        avirt = _i + bvirt
        bround = bvirt - detRightTail
        around = detLeftTail - avirt
        b[0] = around + bround
        _j = detLeft + _i
        bvirt = _j - detLeft
        avirt = _j - bvirt
        bround = _i - bvirt
        around = detLeft - avirt
        _0 = around + bround
        _i = _0 - detRight
        bvirt = _0 - _i
        avirt = _i + bvirt
        bround = bvirt - detRight
        around = _0 - avirt
        b[1] = around + bround
        b3 = _j + _i
        bvirt = b3 - _j
        avirt = b3 - bvirt
        bround = _i - bvirt
        around = _j - avirt
        b[2] = around + bround
        b[3] = b3
        det = estimate(4, b)
        var errbound = ccErrBoundB * detSum
        if (det >= errbound || -det >= errbound) {
            return det
        }
        bvirt = pa.x - acx
        avirt = acx + bvirt
        bround = bvirt - pc.x
        around = pa.x - avirt
        acxTail = around + bround
        bvirt = pb.x - bcx
        avirt = bcx + bvirt
        bround = bvirt - pc.x
        around = pb.x - avirt
        bcxTail = around + bround
        bvirt = pa.y - acy
        avirt = acy + bvirt
        bround = bvirt - pc.y
        around = pa.y - avirt
        acyTail = around + bround
        bvirt = pb.y - bcy
        avirt = bcy + bvirt
        bround = bvirt - pc.y
        around = pb.y - avirt
        bcyTail = around + bround
        if (acxTail == 0.0 && acyTail == 0.0 && bcxTail == 0.0 && bcyTail == 0.0) {
            return det
        }
        errbound = ccErrBoundC * detSum + resultErrBound * if (det >= 0.0) det else -det
        det += acx * bcyTail + bcy * acxTail - (acy * bcxTail + bcx * acyTail)
        if (det >= errbound || -det >= errbound) {
            return det
        }
        s1 = acxTail * bcy
        c = splitter * acxTail
        abig = c - acxTail
        ahi = c - abig
        alo = acxTail - ahi
        c = splitter * bcy
        abig = c - bcy
        bhi = c - abig
        blo = bcy - bhi
        err1 = s1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        s0 = alo * blo - err3
        t1 = acyTail * bcx
        c = splitter * acyTail
        abig = c - acyTail
        ahi = c - abig
        alo = acyTail - ahi
        c = splitter * bcx
        abig = c - bcx
        bhi = c - abig
        blo = bcx - bhi
        err1 = t1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        t0 = alo * blo - err3
        _i = s0 - t0
        bvirt = s0 - _i
        avirt = _i + bvirt
        bround = bvirt - t0
        around = s0 - avirt
        u[0] = around + bround
        _j = s1 + _i
        bvirt = _j - s1
        avirt = _j - bvirt
        bround = _i - bvirt
        around = s1 - avirt
        _0 = around + bround
        _i = _0 - t1
        bvirt = _0 - _i
        avirt = _i + bvirt
        bround = bvirt - t1
        around = _0 - avirt
        u[1] = around + bround
        u3 = _j + _i
        bvirt = u3 - _j
        avirt = u3 - bvirt
        bround = _i - bvirt
        around = _j - avirt
        u[2] = around + bround
        u[3] = u3
        c1Length = fastExpansionSumZeroElim(4, b, 4, u, c1)
        s1 = acx * bcyTail
        c = splitter * acx
        abig = c - acx
        ahi = c - abig
        alo = acx - ahi
        c = splitter * bcyTail
        abig = c - bcyTail
        bhi = c - abig
        blo = bcyTail - bhi
        err1 = s1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        s0 = alo * blo - err3
        t1 = acy * bcxTail
        c = splitter * acy
        abig = c - acy
        ahi = c - abig
        alo = acy - ahi
        c = splitter * bcxTail
        abig = c - bcxTail
        bhi = c - abig
        blo = bcxTail - bhi
        err1 = t1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        t0 = alo * blo - err3
        _i = s0 - t0
        bvirt = s0 - _i
        avirt = _i + bvirt
        bround = bvirt - t0
        around = s0 - avirt
        u[0] = around + bround
        _j = s1 + _i
        bvirt = _j - s1
        avirt = _j - bvirt
        bround = _i - bvirt
        around = s1 - avirt
        _0 = around + bround
        _i = _0 - t1
        bvirt = _0 - _i
        avirt = _i + bvirt
        bround = bvirt - t1
        around = _0 - avirt
        u[1] = around + bround
        u3 = _j + _i
        bvirt = u3 - _j
        avirt = u3 - bvirt
        bround = _i - bvirt
        around = _j - avirt
        u[2] = around + bround
        u[3] = u3
        c2Length = fastExpansionSumZeroElim(c1Length, c1, 4, u, c2)
        s1 = acxTail * bcyTail
        c = splitter * acxTail
        abig = c - acxTail
        ahi = c - abig
        alo = acxTail - ahi
        c = splitter * bcyTail
        abig = c - bcyTail
        bhi = c - abig
        blo = bcyTail - bhi
        err1 = s1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        s0 = alo * blo - err3
        t1 = acyTail * bcxTail
        c = splitter * acyTail
        abig = c - acyTail
        ahi = c - abig
        alo = acyTail - ahi
        c = splitter * bcxTail
        abig = c - bcxTail
        bhi = c - abig
        blo = bcxTail - bhi
        err1 = t1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        t0 = alo * blo - err3
        _i = s0 - t0
        bvirt = s0 - _i
        avirt = _i + bvirt
        bround = bvirt - t0
        around = s0 - avirt
        u[0] = around + bround
        _j = s1 + _i
        bvirt = _j - s1
        avirt = _j - bvirt
        bround = _i - bvirt
        around = s1 - avirt
        _0 = around + bround
        _i = _0 - t1
        bvirt = _0 - _i
        avirt = _i + bvirt
        bround = bvirt - t1
        around = _0 - avirt
        u[1] = around + bround
        u3 = _j + _i
        bvirt = u3 - _j
        avirt = u3 - bvirt
        bround = _i - bvirt
        around = _j - avirt
        u[2] = around + bround
        u[3] = u3
        dLength = fastExpansionSumZeroElim(c2Length, c2, 4, u, d)
        return d[dLength - 1]
    }

    private fun inCircleAdapt(pa: Point, pb: Point, pc: Point, pd: Point, permanent: Double): Double {
        val fin1: DoubleArray = DoubleArray(1152)
        val fin2: DoubleArray = DoubleArray(1152)
        val abdet: DoubleArray = DoubleArray(64)
        val axbc: DoubleArray = DoubleArray(8)
        val axxbc: DoubleArray = DoubleArray(16)
        val aybc: DoubleArray = DoubleArray(8)
        val ayybc: DoubleArray = DoubleArray(16)
        val adet: DoubleArray = DoubleArray(32)
        val bxca: DoubleArray = DoubleArray(8)
        val bxxca: DoubleArray = DoubleArray(16)
        val byca: DoubleArray = DoubleArray(8)
        val byyca: DoubleArray = DoubleArray(16)
        val bdet: DoubleArray = DoubleArray(32)
        val cxab: DoubleArray = DoubleArray(8)
        val cxxab: DoubleArray = DoubleArray(16)
        val cyab: DoubleArray = DoubleArray(8)
        val cyyab: DoubleArray = DoubleArray(16)
        val cdet: DoubleArray = DoubleArray(32)
        val temp8: DoubleArray = DoubleArray(8)
        val temp16a: DoubleArray = DoubleArray(16)
        val temp16b: DoubleArray = DoubleArray(16)
        val temp16c: DoubleArray = DoubleArray(16)
        val temp32a: DoubleArray = DoubleArray(32)
        val temp32b: DoubleArray = DoubleArray(32)
        val temp48: DoubleArray = DoubleArray(48)
        val temp64: DoubleArray = DoubleArray(64)
        val axtbb = DoubleArray(8)
        val axtcc = DoubleArray(8)
        val aytbb = DoubleArray(8)
        val aytcc = DoubleArray(8)
        val bxtaa = DoubleArray(8)
        val bxtcc = DoubleArray(8)
        val bytaa = DoubleArray(8)
        val bytcc = DoubleArray(8)
        val cxtaa = DoubleArray(8)
        val cxtbb = DoubleArray(8)
        val cytaa = DoubleArray(8)
        val cytbb = DoubleArray(8)
        val axtbc = DoubleArray(8)
        val aytbc = DoubleArray(8)
        val bxtca = DoubleArray(8)
        val bytca = DoubleArray(8)
        val cxtab = DoubleArray(8)
        val cytab = DoubleArray(8)
        val axtbct = DoubleArray(16)
        val aytbct = DoubleArray(16)
        val bxtcat = DoubleArray(16)
        val bytcat = DoubleArray(16)
        val cxtabt = DoubleArray(16)
        val cytabt = DoubleArray(16)
        val axtbctt = DoubleArray(8)
        val aytbctt = DoubleArray(8)
        val bxtcatt = DoubleArray(8)
        val bytcatt = DoubleArray(8)
        val cxtabtt = DoubleArray(8)
        val cytabtt = DoubleArray(8)
        val abt = DoubleArray(8)
        val bct = DoubleArray(8)
        val cat = DoubleArray(8)
        val abtt = DoubleArray(4)
        val bctt = DoubleArray(4)
        val catt = DoubleArray(4)
        val bc = DoubleArray(4)
        val ca = DoubleArray(4)
        val ab = DoubleArray(4)
        val aa = DoubleArray(4)
        val bb = DoubleArray(4)
        val cc = DoubleArray(4)
        val u = DoubleArray(5)
        val v = DoubleArray(5)
        var det: Double
        var finnow: DoubleArray
        var finother: DoubleArray
        var finswap: DoubleArray
        var finlength: Int
        var ti1: Double
        var tj1: Double
        var ti0: Double
        var tj0: Double
        var u3: Double
        var v3: Double
        var temp8len: Int
        var temp16alen: Int
        var temp16blen: Int
        var temp16clen: Int
        var temp32alen: Int
        var temp32blen: Int
        var temp48len: Int
        var temp64len: Int
        var negate: Double
        var bvirt: Double
        var avirt: Double
        var bround: Double
        var around: Double
        var c: Double
        var abig: Double
        var ahi: Double
        var alo: Double
        var bhi: Double
        var blo: Double
        var err1: Double
        var err2: Double
        var err3: Double
        var _i: Double
        var _j: Double
        var _0: Double
        var axtbclen = 0
        var aytbclen = 0
        var bxtcalen = 0
        var bytcalen = 0
        var cxtablen = 0
        var cytablen = 0
        val adx = pa.x - pd.x
        val bdx = pb.x - pd.x
        val cdx = pc.x - pd.x
        val ady = pa.y - pd.y
        val bdy = pb.y - pd.y
        val cdy = pc.y - pd.y
        val bdxcdy1 = bdx * cdy
        c = splitter * bdx
        abig = c - bdx
        ahi = c - abig
        alo = bdx - ahi
        c = splitter * cdy
        abig = c - cdy
        bhi = c - abig
        blo = cdy - bhi
        err1 = bdxcdy1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        val bdxcdy0 = alo * blo - err3
        val cdxbdy1 = cdx * bdy
        c = splitter * cdx
        abig = c - cdx
        ahi = c - abig
        alo = cdx - ahi
        c = splitter * bdy
        abig = c - bdy
        bhi = c - abig
        blo = bdy - bhi
        err1 = cdxbdy1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        val cdxbdy0 = alo * blo - err3
        _i = bdxcdy0 - cdxbdy0
        bvirt = bdxcdy0 - _i
        avirt = _i + bvirt
        bround = bvirt - cdxbdy0
        around = bdxcdy0 - avirt
        bc[0] = around + bround
        _j = bdxcdy1 + _i
        bvirt = _j - bdxcdy1
        avirt = _j - bvirt
        bround = _i - bvirt
        around = bdxcdy1 - avirt
        _0 = around + bround
        _i = _0 - cdxbdy1
        bvirt = _0 - _i
        avirt = _i + bvirt
        bround = bvirt - cdxbdy1
        around = _0 - avirt
        bc[1] = around + bround
        val bc3 = _j + _i
        bvirt = bc3 - _j
        avirt = bc3 - bvirt
        bround = _i - bvirt
        around = _j - avirt
        bc[2] = around + bround
        bc[3] = bc3
        val axbclen = scaleExpansionZeroElim(4, bc, adx, axbc)
        val axxbclen = scaleExpansionZeroElim(axbclen, axbc, adx, axxbc)
        val aybclen = scaleExpansionZeroElim(4, bc, ady, aybc)
        val ayybclen = scaleExpansionZeroElim(aybclen, aybc, ady, ayybc)
        val alen = fastExpansionSumZeroElim(axxbclen, axxbc, ayybclen, ayybc, adet)
        val cdxady1 = cdx * ady
        c = splitter * cdx
        abig = c - cdx
        ahi = c - abig
        alo = cdx - ahi
        c = splitter * ady
        abig = c - ady
        bhi = c - abig
        blo = ady - bhi
        err1 = cdxady1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        val cdxady0 = alo * blo - err3
        val adxcdy1 = adx * cdy
        c = splitter * adx
        abig = c - adx
        ahi = c - abig
        alo = adx - ahi
        c = splitter * cdy
        abig = c - cdy
        bhi = c - abig
        blo = cdy - bhi
        err1 = adxcdy1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        val adxcdy0 = alo * blo - err3
        _i = cdxady0 - adxcdy0
        bvirt = cdxady0 - _i
        avirt = _i + bvirt
        bround = bvirt - adxcdy0
        around = cdxady0 - avirt
        ca[0] = around + bround
        _j = cdxady1 + _i
        bvirt = _j - cdxady1
        avirt = _j - bvirt
        bround = _i - bvirt
        around = cdxady1 - avirt
        _0 = around + bround
        _i = _0 - adxcdy1
        bvirt = _0 - _i
        avirt = _i + bvirt
        bround = bvirt - adxcdy1
        around = _0 - avirt
        ca[1] = around + bround
        val ca3 = _j + _i
        bvirt = ca3 - _j
        avirt = ca3 - bvirt
        bround = _i - bvirt
        around = _j - avirt
        ca[2] = around + bround
        ca[3] = ca3
        val bxcalen = scaleExpansionZeroElim(4, ca, bdx, bxca)
        val bxxcalen = scaleExpansionZeroElim(bxcalen, bxca, bdx, bxxca)
        val bycalen = scaleExpansionZeroElim(4, ca, bdy, byca)
        val byycalen = scaleExpansionZeroElim(bycalen, byca, bdy, byyca)
        val blen = fastExpansionSumZeroElim(bxxcalen, bxxca, byycalen, byyca, bdet)
        val adxbdy1 = adx * bdy
        c = splitter * adx
        abig = c - adx
        ahi = c - abig
        alo = adx - ahi
        c = splitter * bdy
        abig = c - bdy
        bhi = c - abig
        blo = bdy - bhi
        err1 = adxbdy1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        val adxbdy0 = alo * blo - err3
        val bdxady1 = bdx * ady
        c = splitter * bdx
        abig = c - bdx
        ahi = c - abig
        alo = bdx - ahi
        c = splitter * ady
        abig = c - ady
        bhi = c - abig
        blo = ady - bhi
        err1 = bdxady1 - ahi * bhi
        err2 = err1 - alo * bhi
        err3 = err2 - ahi * blo
        val bdxady0 = alo * blo - err3
        _i = adxbdy0 - bdxady0
        bvirt = adxbdy0 - _i
        avirt = _i + bvirt
        bround = bvirt - bdxady0
        around = adxbdy0 - avirt
        ab[0] = around + bround
        _j = adxbdy1 + _i
        bvirt = _j - adxbdy1
        avirt = _j - bvirt
        bround = _i - bvirt
        around = adxbdy1 - avirt
        _0 = around + bround
        _i = _0 - bdxady1
        bvirt = _0 - _i
        avirt = _i + bvirt
        bround = bvirt - bdxady1
        around = _0 - avirt
        ab[1] = around + bround
        val ab3 = _j + _i
        bvirt = ab3 - _j
        avirt = ab3 - bvirt
        bround = _i - bvirt
        around = _j - avirt
        ab[2] = around + bround
        ab[3] = ab3
        val cxablen = scaleExpansionZeroElim(4, ab, cdx, cxab)
        val cxxablen = scaleExpansionZeroElim(cxablen, cxab, cdx, cxxab)
        val cyablen = scaleExpansionZeroElim(4, ab, cdy, cyab)
        val cyyablen = scaleExpansionZeroElim(cyablen, cyab, cdy, cyyab)
        val clen = fastExpansionSumZeroElim(cxxablen, cxxab, cyyablen, cyyab, cdet)
        val ablen = fastExpansionSumZeroElim(alen, adet, blen, bdet, abdet)
        finlength = fastExpansionSumZeroElim(ablen, abdet, clen, cdet, fin1)
        det = estimate(finlength, fin1)
        var errbound = iCcErrBoundB * permanent
        if (det >= errbound || -det >= errbound) {
            return det
        }
        bvirt = pa.x - adx
        avirt = adx + bvirt
        bround = bvirt - pd.x
        around = pa.x - avirt
        val adxtail = around + bround
        bvirt = pa.y - ady
        avirt = ady + bvirt
        bround = bvirt - pd.y
        around = pa.y - avirt
        val adytail = around + bround
        bvirt = pb.x - bdx
        avirt = bdx + bvirt
        bround = bvirt - pd.x
        around = pb.x - avirt
        val bdxtail = around + bround
        bvirt = pb.y - bdy
        avirt = bdy + bvirt
        bround = bvirt - pd.y
        around = pb.y - avirt
        val bdytail = around + bround
        bvirt = pc.x - cdx
        avirt = cdx + bvirt
        bround = bvirt - pd.x
        around = pc.x - avirt
        val cdxtail = around + bround
        bvirt = pc.y - cdy
        avirt = cdy + bvirt
        bround = bvirt - pd.y
        around = pc.y - avirt
        val cdytail = around + bround
        if (adxtail == 0.0 && bdxtail == 0.0 && cdxtail == 0.0 && adytail == 0.0 && bdytail == 0.0 && cdytail == 0.0) {
            return det
        }
        errbound = iCcErrBoundC * permanent + resultErrBound * if (det >= 0.0) det else -det
        det += (adx * adx + ady * ady) * (bdx * cdytail + cdy * bdxtail - (bdy * cdxtail + cdx * bdytail)) + 2.0 * (adx * adxtail + ady * adytail) * (bdx * cdy - bdy * cdx) + ((bdx * bdx + bdy * bdy) * (cdx * adytail + ady * cdxtail - (cdy * adxtail + adx * cdytail)) + 2.0 * (bdx * bdxtail + bdy * bdytail) * (cdx * ady - cdy * adx)) + ((cdx * cdx + cdy * cdy) * (adx * bdytail + bdy * adxtail - (ady * bdxtail + bdx * adytail)) + 2.0 * (cdx * cdxtail + cdy * cdytail) * (adx * bdy - ady * bdx))
        if (det >= errbound || -det >= errbound) {
            return det
        }
        finnow = fin1
        finother = fin2
        if (bdxtail != 0.0 || bdytail != 0.0 || cdxtail != 0.0 || cdytail != 0.0) {
            val adxadx1 = adx * adx
            c = splitter * adx
            abig = c - adx
            ahi = c - abig
            alo = adx - ahi
            err1 = adxadx1 - ahi * ahi
            err3 = err1 - (ahi + ahi) * alo
            val adxadx0 = alo * alo - err3
            val adyady1 = ady * ady
            c = splitter * ady
            abig = c - ady
            ahi = c - abig
            alo = ady - ahi
            err1 = adyady1 - ahi * ahi
            err3 = err1 - (ahi + ahi) * alo
            val adyady0 = alo * alo - err3
            _i = adxadx0 + adyady0
            bvirt = _i - adxadx0
            avirt = _i - bvirt
            bround = adyady0 - bvirt
            around = adxadx0 - avirt
            aa[0] = around + bround
            _j = adxadx1 + _i
            bvirt = _j - adxadx1
            avirt = _j - bvirt
            bround = _i - bvirt
            around = adxadx1 - avirt
            _0 = around + bround
            _i = _0 + adyady1
            bvirt = _i - _0
            avirt = _i - bvirt
            bround = adyady1 - bvirt
            around = _0 - avirt
            aa[1] = around + bround
            val aa3 = _j + _i
            bvirt = aa3 - _j
            avirt = aa3 - bvirt
            bround = _i - bvirt
            around = _j - avirt
            aa[2] = around + bround
            aa[3] = aa3
        }
        if (cdxtail != 0.0 || cdytail != 0.0 || adxtail != 0.0 || adytail != 0.0) {
            val bdxbdx1 = bdx * bdx
            c = splitter * bdx
            abig = c - bdx
            ahi = c - abig
            alo = bdx - ahi
            err1 = bdxbdx1 - ahi * ahi
            err3 = err1 - (ahi + ahi) * alo
            val bdxbdx0 = alo * alo - err3
            val bdybdy1 = bdy * bdy
            c = splitter * bdy
            abig = c - bdy
            ahi = c - abig
            alo = bdy - ahi
            err1 = bdybdy1 - ahi * ahi
            err3 = err1 - (ahi + ahi) * alo
            val bdybdy0 = alo * alo - err3
            _i = bdxbdx0 + bdybdy0
            bvirt = _i - bdxbdx0
            avirt = _i - bvirt
            bround = bdybdy0 - bvirt
            around = bdxbdx0 - avirt
            bb[0] = around + bround
            _j = bdxbdx1 + _i
            bvirt = _j - bdxbdx1
            avirt = _j - bvirt
            bround = _i - bvirt
            around = bdxbdx1 - avirt
            _0 = around + bround
            _i = _0 + bdybdy1
            bvirt = _i - _0
            avirt = _i - bvirt
            bround = bdybdy1 - bvirt
            around = _0 - avirt
            bb[1] = around + bround
            val bb3 = _j + _i
            bvirt = bb3 - _j
            avirt = bb3 - bvirt
            bround = _i - bvirt
            around = _j - avirt
            bb[2] = around + bround
            bb[3] = bb3
        }
        if (adxtail != 0.0 || adytail != 0.0 || bdxtail != 0.0 || bdytail != 0.0) {
            val cdxcdx1 = cdx * cdx
            c = splitter * cdx
            abig = c - cdx
            ahi = c - abig
            alo = cdx - ahi
            err1 = cdxcdx1 - ahi * ahi
            err3 = err1 - (ahi + ahi) * alo
            val cdxcdx0 = alo * alo - err3
            val cdycdy1 = cdy * cdy
            c = splitter * cdy
            abig = c - cdy
            ahi = c - abig
            alo = cdy - ahi
            err1 = cdycdy1 - ahi * ahi
            err3 = err1 - (ahi + ahi) * alo
            val cdycdy0 = alo * alo - err3
            _i = cdxcdx0 + cdycdy0
            bvirt = _i - cdxcdx0
            avirt = _i - bvirt
            bround = cdycdy0 - bvirt
            around = cdxcdx0 - avirt
            cc[0] = around + bround
            _j = cdxcdx1 + _i
            bvirt = _j - cdxcdx1
            avirt = _j - bvirt
            bround = _i - bvirt
            around = cdxcdx1 - avirt
            _0 = around + bround
            _i = _0 + cdycdy1
            bvirt = _i - _0
            avirt = _i - bvirt
            bround = cdycdy1 - bvirt
            around = _0 - avirt
            cc[1] = around + bround
            val cc3 = _j + _i
            bvirt = cc3 - _j
            avirt = cc3 - bvirt
            bround = _i - bvirt
            around = _j - avirt
            cc[2] = around + bround
            cc[3] = cc3
        }
        if (adxtail != 0.0) {
            axtbclen = scaleExpansionZeroElim(4, bc, adxtail, axtbc)
            temp16alen = scaleExpansionZeroElim(axtbclen, axtbc, 2.0 * adx, temp16a)
            val axtcclen = scaleExpansionZeroElim(4, cc, adxtail, axtcc)
            temp16blen = scaleExpansionZeroElim(axtcclen, axtcc, bdy, temp16b)
            val axtbblen = scaleExpansionZeroElim(4, bb, adxtail, axtbb)
            temp16clen = scaleExpansionZeroElim(axtbblen, axtbb, -cdy, temp16c)
            temp32alen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32a)
            temp48len = fastExpansionSumZeroElim(temp16clen, temp16c, temp32alen, temp32a, temp48)
            finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
            finswap = finnow
            finnow = finother
            finother = finswap
        }
        if (adytail != 0.0) {
            aytbclen = scaleExpansionZeroElim(4, bc, adytail, aytbc)
            temp16alen = scaleExpansionZeroElim(aytbclen, aytbc, 2.0 * ady, temp16a)
            val aytbblen = scaleExpansionZeroElim(4, bb, adytail, aytbb)
            temp16blen = scaleExpansionZeroElim(aytbblen, aytbb, cdx, temp16b)
            val aytcclen = scaleExpansionZeroElim(4, cc, adytail, aytcc)
            temp16clen = scaleExpansionZeroElim(aytcclen, aytcc, -bdx, temp16c)
            temp32alen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32a)
            temp48len = fastExpansionSumZeroElim(temp16clen, temp16c, temp32alen, temp32a, temp48)
            finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
            finswap = finnow
            finnow = finother
            finother = finswap
        }
        if (bdxtail != 0.0) {
            bxtcalen = scaleExpansionZeroElim(4, ca, bdxtail, bxtca)
            temp16alen = scaleExpansionZeroElim(bxtcalen, bxtca, 2.0 * bdx, temp16a)
            val bxtaalen = scaleExpansionZeroElim(4, aa, bdxtail, bxtaa)
            temp16blen = scaleExpansionZeroElim(bxtaalen, bxtaa, cdy, temp16b)
            val bxtcclen = scaleExpansionZeroElim(4, cc, bdxtail, bxtcc)
            temp16clen = scaleExpansionZeroElim(bxtcclen, bxtcc, -ady, temp16c)
            temp32alen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32a)
            temp48len = fastExpansionSumZeroElim(temp16clen, temp16c, temp32alen, temp32a, temp48)
            finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
            finswap = finnow
            finnow = finother
            finother = finswap
        }
        if (bdytail != 0.0) {
            bytcalen = scaleExpansionZeroElim(4, ca, bdytail, bytca)
            temp16alen = scaleExpansionZeroElim(bytcalen, bytca, 2.0 * bdy, temp16a)
            val bytcclen = scaleExpansionZeroElim(4, cc, bdytail, bytcc)
            temp16blen = scaleExpansionZeroElim(bytcclen, bytcc, adx, temp16b)
            val bytaalen = scaleExpansionZeroElim(4, aa, bdytail, bytaa)
            temp16clen = scaleExpansionZeroElim(bytaalen, bytaa, -cdx, temp16c)
            temp32alen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32a)
            temp48len = fastExpansionSumZeroElim(temp16clen, temp16c, temp32alen, temp32a, temp48)
            finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
            finswap = finnow
            finnow = finother
            finother = finswap
        }
        if (cdxtail != 0.0) {
            cxtablen = scaleExpansionZeroElim(4, ab, cdxtail, cxtab)
            temp16alen = scaleExpansionZeroElim(cxtablen, cxtab, 2.0 * cdx, temp16a)
            val cxtbblen = scaleExpansionZeroElim(4, bb, cdxtail, cxtbb)
            temp16blen = scaleExpansionZeroElim(cxtbblen, cxtbb, ady, temp16b)
            val cxtaalen = scaleExpansionZeroElim(4, aa, cdxtail, cxtaa)
            temp16clen = scaleExpansionZeroElim(cxtaalen, cxtaa, -bdy, temp16c)
            temp32alen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32a)
            temp48len = fastExpansionSumZeroElim(temp16clen, temp16c, temp32alen, temp32a, temp48)
            finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
            finswap = finnow
            finnow = finother
            finother = finswap
        }
        if (cdytail != 0.0) {
            cytablen = scaleExpansionZeroElim(4, ab, cdytail, cytab)
            temp16alen = scaleExpansionZeroElim(cytablen, cytab, 2.0 * cdy, temp16a)
            val cytaalen = scaleExpansionZeroElim(4, aa, cdytail, cytaa)
            temp16blen = scaleExpansionZeroElim(cytaalen, cytaa, bdx, temp16b)
            val cytbblen = scaleExpansionZeroElim(4, bb, cdytail, cytbb)
            temp16clen = scaleExpansionZeroElim(cytbblen, cytbb, -adx, temp16c)
            temp32alen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32a)
            temp48len = fastExpansionSumZeroElim(temp16clen, temp16c, temp32alen, temp32a, temp48)
            finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
            finswap = finnow
            finnow = finother
            finother = finswap
        }
        if (adxtail != 0.0 || adytail != 0.0) {
            val bcttlen: Int
            val bctlen: Int
            if (bdxtail != 0.0 || bdytail != 0.0 || cdxtail != 0.0 || cdytail != 0.0) {
                ti1 = bdxtail * cdy
                c = splitter * bdxtail
                abig = c - bdxtail
                ahi = c - abig
                alo = bdxtail - ahi
                c = splitter * cdy
                abig = c - cdy
                bhi = c - abig
                blo = cdy - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                tj1 = bdx * cdytail
                c = splitter * bdx
                abig = c - bdx
                ahi = c - abig
                alo = bdx - ahi
                c = splitter * cdytail
                abig = c - cdytail
                bhi = c - abig
                blo = cdytail - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 + tj0
                bvirt = _i - ti0
                avirt = _i - bvirt
                bround = tj0 - bvirt
                around = ti0 - avirt
                u[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 + tj1
                bvirt = _i - _0
                avirt = _i - bvirt
                bround = tj1 - bvirt
                around = _0 - avirt
                u[1] = around + bround
                u3 = _j + _i
                bvirt = u3 - _j
                avirt = u3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                u[2] = around + bround
                u[3] = u3
                negate = -bdy
                ti1 = cdxtail * negate
                c = splitter * cdxtail
                abig = c - cdxtail
                ahi = c - abig
                alo = cdxtail - ahi
                c = splitter * negate
                abig = c - negate
                bhi = c - abig
                blo = negate - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                negate = -bdytail
                tj1 = cdx * negate
                c = splitter * cdx
                abig = c - cdx
                ahi = c - abig
                alo = cdx - ahi
                c = splitter * negate
                abig = c - negate
                bhi = c - abig
                blo = negate - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 + tj0
                bvirt = _i - ti0
                avirt = _i - bvirt
                bround = tj0 - bvirt
                around = ti0 - avirt
                v[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 + tj1
                bvirt = _i - _0
                avirt = _i - bvirt
                bround = tj1 - bvirt
                around = _0 - avirt
                v[1] = around + bround
                v3 = _j + _i
                bvirt = v3 - _j
                avirt = v3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                v[2] = around + bround
                v[3] = v3
                bctlen = fastExpansionSumZeroElim(4, u, 4, v, bct)
                ti1 = bdxtail * cdytail
                c = splitter * bdxtail
                abig = c - bdxtail
                ahi = c - abig
                alo = bdxtail - ahi
                c = splitter * cdytail
                abig = c - cdytail
                bhi = c - abig
                blo = cdytail - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                tj1 = cdxtail * bdytail
                c = splitter * cdxtail
                abig = c - cdxtail
                ahi = c - abig
                alo = cdxtail - ahi
                c = splitter * bdytail
                abig = c - bdytail
                bhi = c - abig
                blo = bdytail - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 - tj0
                bvirt = ti0 - _i
                avirt = _i + bvirt
                bround = bvirt - tj0
                around = ti0 - avirt
                bctt[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 - tj1
                bvirt = _0 - _i
                avirt = _i + bvirt
                bround = bvirt - tj1
                around = _0 - avirt
                bctt[1] = around + bround
                val bctt3 = _j + _i
                bvirt = bctt3 - _j
                avirt = bctt3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                bctt[2] = around + bround
                bctt[3] = bctt3
                bcttlen = 4
            } else {
                bct[0] = 0.0
                bctlen = 1
                bctt[0] = 0.0
                bcttlen = 1
            }
            if (adxtail != 0.0) {
                temp16alen = scaleExpansionZeroElim(axtbclen, axtbc, adxtail, temp16a)
                val axtbctlen = scaleExpansionZeroElim(bctlen, bct, adxtail, axtbct)
                temp32alen = scaleExpansionZeroElim(axtbctlen, axtbct, 2.0 * adx, temp32a)
                temp48len = fastExpansionSumZeroElim(temp16alen, temp16a, temp32alen, temp32a, temp48)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
                if (bdytail != 0.0) {
                    temp8len = scaleExpansionZeroElim(4, cc, adxtail, temp8)
                    temp16alen = scaleExpansionZeroElim(temp8len, temp8, bdytail, temp16a)
                    finlength = fastExpansionSumZeroElim(finlength, finnow, temp16alen, temp16a, finother)
                    finswap = finnow
                    finnow = finother
                    finother = finswap
                }
                if (cdytail != 0.0) {
                    temp8len = scaleExpansionZeroElim(4, bb, -adxtail, temp8)
                    temp16alen = scaleExpansionZeroElim(temp8len, temp8, cdytail, temp16a)
                    finlength = fastExpansionSumZeroElim(finlength, finnow, temp16alen, temp16a, finother)
                    finswap = finnow
                    finnow = finother
                    finother = finswap
                }
                temp32alen = scaleExpansionZeroElim(axtbctlen, axtbct, adxtail, temp32a)
                val axtbcttlen = scaleExpansionZeroElim(bcttlen, bctt, adxtail, axtbctt)
                temp16alen = scaleExpansionZeroElim(axtbcttlen, axtbctt, 2.0 * adx, temp16a)
                temp16blen = scaleExpansionZeroElim(axtbcttlen, axtbctt, adxtail, temp16b)
                temp32blen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32b)
                temp64len = fastExpansionSumZeroElim(temp32alen, temp32a, temp32blen, temp32b, temp64)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp64len, temp64, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
            }
            if (adytail != 0.0) {
                temp16alen = scaleExpansionZeroElim(aytbclen, aytbc, adytail, temp16a)
                val aytbctlen = scaleExpansionZeroElim(bctlen, bct, adytail, aytbct)
                temp32alen = scaleExpansionZeroElim(aytbctlen, aytbct, 2.0 * ady, temp32a)
                temp48len = fastExpansionSumZeroElim(temp16alen, temp16a, temp32alen, temp32a, temp48)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
                temp32alen = scaleExpansionZeroElim(aytbctlen, aytbct, adytail, temp32a)
                val aytbcttlen = scaleExpansionZeroElim(bcttlen, bctt, adytail, aytbctt)
                temp16alen = scaleExpansionZeroElim(aytbcttlen, aytbctt, 2.0 * ady, temp16a)
                temp16blen = scaleExpansionZeroElim(aytbcttlen, aytbctt, adytail, temp16b)
                temp32blen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32b)
                temp64len = fastExpansionSumZeroElim(temp32alen, temp32a, temp32blen, temp32b, temp64)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp64len, temp64, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
            }
        }
        if (bdxtail != 0.0 || bdytail != 0.0) {
            val catlen: Int
            val cattlen: Int
            if (cdxtail != 0.0 || cdytail != 0.0 || adxtail != 0.0 || adytail != 0.0) {
                ti1 = cdxtail * ady
                c = splitter * cdxtail
                abig = c - cdxtail
                ahi = c - abig
                alo = cdxtail - ahi
                c = splitter * ady
                abig = c - ady
                bhi = c - abig
                blo = ady - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                tj1 = cdx * adytail
                c = splitter * cdx
                abig = c - cdx
                ahi = c - abig
                alo = cdx - ahi
                c = splitter * adytail
                abig = c - adytail
                bhi = c - abig
                blo = adytail - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 + tj0
                bvirt = _i - ti0
                avirt = _i - bvirt
                bround = tj0 - bvirt
                around = ti0 - avirt
                u[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 + tj1
                bvirt = _i - _0
                avirt = _i - bvirt
                bround = tj1 - bvirt
                around = _0 - avirt
                u[1] = around + bround
                u3 = _j + _i
                bvirt = u3 - _j
                avirt = u3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                u[2] = around + bround
                u[3] = u3
                negate = -cdy
                ti1 = adxtail * negate
                c = splitter * adxtail
                abig = c - adxtail
                ahi = c - abig
                alo = adxtail - ahi
                c = splitter * negate
                abig = c - negate
                bhi = c - abig
                blo = negate - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                negate = -cdytail
                tj1 = adx * negate
                c = splitter * adx
                abig = c - adx
                ahi = c - abig
                alo = adx - ahi
                c = splitter * negate
                abig = c - negate
                bhi = c - abig
                blo = negate - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 + tj0
                bvirt = _i - ti0
                avirt = _i - bvirt
                bround = tj0 - bvirt
                around = ti0 - avirt
                v[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 + tj1
                bvirt = _i - _0
                avirt = _i - bvirt
                bround = tj1 - bvirt
                around = _0 - avirt
                v[1] = around + bround
                v3 = _j + _i
                bvirt = v3 - _j
                avirt = v3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                v[2] = around + bround
                v[3] = v3
                catlen = fastExpansionSumZeroElim(4, u, 4, v, cat)
                ti1 = cdxtail * adytail
                c = splitter * cdxtail
                abig = c - cdxtail
                ahi = c - abig
                alo = cdxtail - ahi
                c = splitter * adytail
                abig = c - adytail
                bhi = c - abig
                blo = adytail - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                tj1 = adxtail * cdytail
                c = splitter * adxtail
                abig = c - adxtail
                ahi = c - abig
                alo = adxtail - ahi
                c = splitter * cdytail
                abig = c - cdytail
                bhi = c - abig
                blo = cdytail - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 - tj0
                bvirt = ti0 - _i
                avirt = _i + bvirt
                bround = bvirt - tj0
                around = ti0 - avirt
                catt[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 - tj1
                bvirt = _0 - _i
                avirt = _i + bvirt
                bround = bvirt - tj1
                around = _0 - avirt
                catt[1] = around + bround
                val catt3 = _j + _i
                bvirt = catt3 - _j
                avirt = catt3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                catt[2] = around + bround
                catt[3] = catt3
                cattlen = 4
            } else {
                cat[0] = 0.0
                catlen = 1
                catt[0] = 0.0
                cattlen = 1
            }
            if (bdxtail != 0.0) {
                temp16alen = scaleExpansionZeroElim(bxtcalen, bxtca, bdxtail, temp16a)
                val bxtcatlen = scaleExpansionZeroElim(catlen, cat, bdxtail, bxtcat)
                temp32alen = scaleExpansionZeroElim(bxtcatlen, bxtcat, 2.0 * bdx, temp32a)
                temp48len = fastExpansionSumZeroElim(temp16alen, temp16a, temp32alen, temp32a, temp48)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
                if (cdytail != 0.0) {
                    temp8len = scaleExpansionZeroElim(4, aa, bdxtail, temp8)
                    temp16alen = scaleExpansionZeroElim(temp8len, temp8, cdytail, temp16a)
                    finlength = fastExpansionSumZeroElim(finlength, finnow, temp16alen, temp16a, finother)
                    finswap = finnow
                    finnow = finother
                    finother = finswap
                }
                if (adytail != 0.0) {
                    temp8len = scaleExpansionZeroElim(4, cc, -bdxtail, temp8)
                    temp16alen = scaleExpansionZeroElim(temp8len, temp8, adytail, temp16a)
                    finlength = fastExpansionSumZeroElim(finlength, finnow, temp16alen, temp16a, finother)
                    finswap = finnow
                    finnow = finother
                    finother = finswap
                }
                temp32alen = scaleExpansionZeroElim(bxtcatlen, bxtcat, bdxtail, temp32a)
                val bxtcattlen = scaleExpansionZeroElim(cattlen, catt, bdxtail, bxtcatt)
                temp16alen = scaleExpansionZeroElim(bxtcattlen, bxtcatt, 2.0 * bdx, temp16a)
                temp16blen = scaleExpansionZeroElim(bxtcattlen, bxtcatt, bdxtail, temp16b)
                temp32blen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32b)
                temp64len = fastExpansionSumZeroElim(temp32alen, temp32a, temp32blen, temp32b, temp64)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp64len, temp64, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
            }
            if (bdytail != 0.0) {
                temp16alen = scaleExpansionZeroElim(bytcalen, bytca, bdytail, temp16a)
                val bytcatlen = scaleExpansionZeroElim(catlen, cat, bdytail, bytcat)
                temp32alen = scaleExpansionZeroElim(bytcatlen, bytcat, 2.0 * bdy, temp32a)
                temp48len = fastExpansionSumZeroElim(temp16alen, temp16a, temp32alen, temp32a, temp48)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
                temp32alen = scaleExpansionZeroElim(bytcatlen, bytcat, bdytail, temp32a)
                val bytcattlen = scaleExpansionZeroElim(cattlen, catt, bdytail, bytcatt)
                temp16alen = scaleExpansionZeroElim(bytcattlen, bytcatt, 2.0 * bdy, temp16a)
                temp16blen = scaleExpansionZeroElim(bytcattlen, bytcatt, bdytail, temp16b)
                temp32blen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32b)
                temp64len = fastExpansionSumZeroElim(temp32alen, temp32a, temp32blen, temp32b, temp64)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp64len, temp64, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
            }
        }
        if (cdxtail != 0.0 || cdytail != 0.0) {
            val abtlen: Int
            val abttlen: Int
            if (adxtail != 0.0 || adytail != 0.0 || bdxtail != 0.0 || bdytail != 0.0) {
                ti1 = adxtail * bdy
                c = splitter * adxtail
                abig = c - adxtail
                ahi = c - abig
                alo = adxtail - ahi
                c = splitter * bdy
                abig = c - bdy
                bhi = c - abig
                blo = bdy - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                tj1 = adx * bdytail
                c = splitter * adx
                abig = c - adx
                ahi = c - abig
                alo = adx - ahi
                c = splitter * bdytail
                abig = c - bdytail
                bhi = c - abig
                blo = bdytail - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 + tj0
                bvirt = _i - ti0
                avirt = _i - bvirt
                bround = tj0 - bvirt
                around = ti0 - avirt
                u[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 + tj1
                bvirt = _i - _0
                avirt = _i - bvirt
                bround = tj1 - bvirt
                around = _0 - avirt
                u[1] = around + bround
                u3 = _j + _i
                bvirt = u3 - _j
                avirt = u3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                u[2] = around + bround
                u[3] = u3
                negate = -ady
                ti1 = bdxtail * negate
                c = splitter * bdxtail
                abig = c - bdxtail
                ahi = c - abig
                alo = bdxtail - ahi
                c = splitter * negate
                abig = c - negate
                bhi = c - abig
                blo = negate - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                negate = -adytail
                tj1 = bdx * negate
                c = splitter * bdx
                abig = c - bdx
                ahi = c - abig
                alo = bdx - ahi
                c = splitter * negate
                abig = c - negate
                bhi = c - abig
                blo = negate - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 + tj0
                bvirt = _i - ti0
                avirt = _i - bvirt
                bround = tj0 - bvirt
                around = ti0 - avirt
                v[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 + tj1
                bvirt = _i - _0
                avirt = _i - bvirt
                bround = tj1 - bvirt
                around = _0 - avirt
                v[1] = around + bround
                v3 = _j + _i
                bvirt = v3 - _j
                avirt = v3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                v[2] = around + bround
                v[3] = v3
                abtlen = fastExpansionSumZeroElim(4, u, 4, v, abt)
                ti1 = adxtail * bdytail
                c = splitter * adxtail
                abig = c - adxtail
                ahi = c - abig
                alo = adxtail - ahi
                c = splitter * bdytail
                abig = c - bdytail
                bhi = c - abig
                blo = bdytail - bhi
                err1 = ti1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                ti0 = alo * blo - err3
                tj1 = bdxtail * adytail
                c = splitter * bdxtail
                abig = c - bdxtail
                ahi = c - abig
                alo = bdxtail - ahi
                c = splitter * adytail
                abig = c - adytail
                bhi = c - abig
                blo = adytail - bhi
                err1 = tj1 - ahi * bhi
                err2 = err1 - alo * bhi
                err3 = err2 - ahi * blo
                tj0 = alo * blo - err3
                _i = ti0 - tj0
                bvirt = ti0 - _i
                avirt = _i + bvirt
                bround = bvirt - tj0
                around = ti0 - avirt
                abtt[0] = around + bround
                _j = ti1 + _i
                bvirt = _j - ti1
                avirt = _j - bvirt
                bround = _i - bvirt
                around = ti1 - avirt
                _0 = around + bround
                _i = _0 - tj1
                bvirt = _0 - _i
                avirt = _i + bvirt
                bround = bvirt - tj1
                around = _0 - avirt
                abtt[1] = around + bround
                val abtt3 = _j + _i
                bvirt = abtt3 - _j
                avirt = abtt3 - bvirt
                bround = _i - bvirt
                around = _j - avirt
                abtt[2] = around + bround
                abtt[3] = abtt3
                abttlen = 4
            } else {
                abt[0] = 0.0
                abtlen = 1
                abtt[0] = 0.0
                abttlen = 1
            }
            if (cdxtail != 0.0) {
                temp16alen = scaleExpansionZeroElim(cxtablen, cxtab, cdxtail, temp16a)
                val cxtabtlen = scaleExpansionZeroElim(abtlen, abt, cdxtail, cxtabt)
                temp32alen = scaleExpansionZeroElim(cxtabtlen, cxtabt, 2.0 * cdx, temp32a)
                temp48len = fastExpansionSumZeroElim(temp16alen, temp16a, temp32alen, temp32a, temp48)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
                if (adytail != 0.0) {
                    temp8len = scaleExpansionZeroElim(4, bb, cdxtail, temp8)
                    temp16alen = scaleExpansionZeroElim(temp8len, temp8, adytail, temp16a)
                    finlength = fastExpansionSumZeroElim(finlength, finnow, temp16alen, temp16a, finother)
                    finswap = finnow
                    finnow = finother
                    finother = finswap
                }
                if (bdytail != 0.0) {
                    temp8len = scaleExpansionZeroElim(4, aa, -cdxtail, temp8)
                    temp16alen = scaleExpansionZeroElim(temp8len, temp8, bdytail, temp16a)
                    finlength = fastExpansionSumZeroElim(finlength, finnow, temp16alen, temp16a, finother)
                    finswap = finnow
                    finnow = finother
                    finother = finswap
                }
                temp32alen = scaleExpansionZeroElim(cxtabtlen, cxtabt, cdxtail, temp32a)
                val cxtabttlen = scaleExpansionZeroElim(abttlen, abtt, cdxtail, cxtabtt)
                temp16alen = scaleExpansionZeroElim(cxtabttlen, cxtabtt, 2.0 * cdx, temp16a)
                temp16blen = scaleExpansionZeroElim(cxtabttlen, cxtabtt, cdxtail, temp16b)
                temp32blen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32b)
                temp64len = fastExpansionSumZeroElim(temp32alen, temp32a, temp32blen, temp32b, temp64)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp64len, temp64, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
            }
            if (cdytail != 0.0) {
                temp16alen = scaleExpansionZeroElim(cytablen, cytab, cdytail, temp16a)
                val cytabtlen = scaleExpansionZeroElim(abtlen, abt, cdytail, cytabt)
                temp32alen = scaleExpansionZeroElim(cytabtlen, cytabt, 2.0 * cdy, temp32a)
                temp48len = fastExpansionSumZeroElim(temp16alen, temp16a, temp32alen, temp32a, temp48)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp48len, temp48, finother)
                finswap = finnow
                finnow = finother
                finother = finswap
                temp32alen = scaleExpansionZeroElim(cytabtlen, cytabt, cdytail, temp32a)
                val cytabttlen = scaleExpansionZeroElim(abttlen, abtt, cdytail, cytabtt)
                temp16alen = scaleExpansionZeroElim(cytabttlen, cytabtt, 2.0 * cdy, temp16a)
                temp16blen = scaleExpansionZeroElim(cytabttlen, cytabtt, cdytail, temp16b)
                temp32blen = fastExpansionSumZeroElim(temp16alen, temp16a, temp16blen, temp16b, temp32b)
                temp64len = fastExpansionSumZeroElim(temp32alen, temp32a, temp32blen, temp32b, temp64)
                finlength = fastExpansionSumZeroElim(finlength, finnow, temp64len, temp64, finother)
                finnow = finother
            }
        }
        return finnow[finlength - 1]
    }
}
