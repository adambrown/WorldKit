package com.grimfox.triangle.tools

import com.grimfox.logging.LOG
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.Mesh.NodeNumbering
import com.grimfox.triangle.Reference

class CuthillMcKee {

    fun renumber(mesh: Mesh): IntArray {
        mesh.renumber(NodeNumbering.LINEAR)
        return renumber(AdjacencyMatrix(mesh))
    }

    fun renumber(matrix: AdjacencyMatrix): IntArray {
        val bandwidth1 = matrix.bandwidth()
        val pcol = matrix.columnPointers
        shift(pcol, true)
        val perm = generateRcm(matrix)
        val perm_inv = permInverse(matrix, perm)
        val bandwidth2 = permBandwidth(matrix, perm, perm_inv)
        LOG.debug { "Reverse Cuthill-McKee (Bandwidth: $bandwidth1 > $bandwidth2)" }
        shift(pcol, false)
        return perm_inv
    }

    internal fun generateRcm(matrix: AdjacencyMatrix): IntArray {
        val n = matrix.size
        val perm = IntArray(n)
        val root = Reference(0)
        val iccsze = Reference(0)
        val level_num = Reference(0)
        val level_row = IntArray(n + 1)
        val mask = IntArray(n)
        var i = 0
        while (i < n) {
            mask[i] = 1
            i++
        }
        var num = 1
        i = 0
        while (i < n) {
            if (mask[i] != 0) {
                root.value = i
                findRoot(matrix, root, mask, level_num, level_row, perm, num - 1)
                rcm(matrix, root.value, mask, perm, num - 1, iccsze)
                num += iccsze.value
                if (n < num) {
                    return perm
                }
            }
            i++
        }
        return perm
    }

    internal fun rcm(matrix: AdjacencyMatrix, root: Int, mask: IntArray, perm: IntArray, offset: Int, iccsze: Reference<Int>) {
        val pcol = matrix.columnPointers
        val irow = matrix.rowIndices
        var fnbr: Int
        var i: Int
        var j: Int
        var k: Int
        var l: Int
        var jstop: Int
        var jstrt: Int
        var lbegin: Int
        var lperm: Int
        var nbr: Int
        var node: Int
        val n = matrix.size
        val deg = IntArray(n)
        degree(matrix, root, mask, deg, iccsze, perm, offset)
        mask[root] = 0
        if (iccsze.value <= 1) {
            return
        }
        var lvlend = 0
        var lnbr = 1
        while (lvlend < lnbr) {
            lbegin = lvlend + 1
            lvlend = lnbr
            i = lbegin
            while (i <= lvlend) {
                node = perm[offset + i - 1]
                jstrt = pcol[node]
                jstop = pcol[node + 1] - 1
                fnbr = lnbr + 1
                j = jstrt
                while (j <= jstop) {
                    nbr = irow[j - 1]
                    if (mask[nbr] != 0) {
                        lnbr += 1
                        mask[nbr] = 0
                        perm[offset + lnbr - 1] = nbr
                    }
                    j++
                }
                if (lnbr > fnbr) {
                    k = fnbr
                    while (k < lnbr) {
                        l = k
                        k += 1
                        nbr = perm[offset + k - 1]
                        while (fnbr < l) {
                            lperm = perm[offset + l - 1]
                            if (deg[lperm - 1] <= deg[nbr - 1]) {
                                break
                            }
                            perm[offset + l] = lperm
                            l -= 1
                        }
                        perm[offset + l] = nbr
                    }
                }
                i++
            }
        }
        reverseVector(perm, offset, iccsze.value)
        return
    }

    internal fun findRoot(matrix: AdjacencyMatrix, root: Reference<Int>, mask: IntArray, level_num: Reference<Int>, level_row: IntArray, level: IntArray, offset: Int) {
        val pcol = matrix.columnPointers
        val irow = matrix.rowIndices
        var j: Int
        var jstrt: Int
        var k: Int
        var kstop: Int
        var kstrt: Int
        var mindeg: Int
        var nghbor: Int
        var ndeg: Int
        var node: Int
        val level_num2 = Reference(0)
        getLevelSet(matrix, root, mask, level_num, level_row, level, offset)
        val iccsze = level_row[level_num.value] - 1
        if (level_num.value == 1 || level_num.value == iccsze) {
            return
        }
        while (true) {
            mindeg = iccsze
            jstrt = level_row[level_num.value - 1]
            root.value = level[offset + jstrt - 1]
            if (jstrt < iccsze) {
                j = jstrt
                while (j <= iccsze) {
                    node = level[offset + j - 1]
                    ndeg = 0
                    kstrt = pcol[node - 1]
                    kstop = pcol[node] - 1
                    k = kstrt
                    while (k <= kstop) {
                        nghbor = irow[k - 1]
                        if (mask[nghbor] > 0) {
                            ndeg += 1
                        }
                        k++
                    }
                    if (ndeg < mindeg) {
                        root.value = node
                        mindeg = ndeg
                    }
                    j++
                }
            }
            getLevelSet(matrix, root, mask, level_num2, level_row, level, offset)
            if (level_num2.value <= level_num.value) {
                break
            }
            level_num.value = level_num2.value
            if (iccsze <= level_num.value) {
                break
            }
        }
        return
    }

    internal fun getLevelSet(matrix: AdjacencyMatrix, root: Reference<Int>, mask: IntArray, level_num: Reference<Int>, level_row: IntArray, level: IntArray, offset: Int) {
        val pcol = matrix.columnPointers
        val irow = matrix.rowIndices
        var i: Int
        var j: Int
        var jstop: Int
        var jstrt: Int
        var lbegin: Int
        var lvlend: Int
        var lvsize: Int
        var nbr: Int
        var node: Int
        mask[root.value] = 0
        level[offset] = root.value
        level_num.value = 0
        lvlend = 0
        var iccsze = 1
        while (true) {
            lbegin = lvlend + 1
            lvlend = iccsze
            level_num.value = level_num.value + 1
            level_row[level_num.value - 1] = lbegin
            i = lbegin
            while (i <= lvlend) {
                node = level[offset + i - 1]
                jstrt = pcol[node]
                jstop = pcol[node + 1] - 1
                j = jstrt
                while (j <= jstop) {
                    nbr = irow[j - 1]
                    if (mask[nbr] != 0) {
                        iccsze += 1
                        level[offset + iccsze - 1] = nbr
                        mask[nbr] = 0
                    }
                    j++
                }
                i++
            }
            lvsize = iccsze - lvlend
            if (lvsize <= 0) {
                break
            }
        }
        level_row[level_num.value] = lvlend + 1
        i = 0
        while (i < iccsze) {
            mask[level[offset + i]] = 1
            i++
        }
        return
    }

    private fun degree(matrix: AdjacencyMatrix, root: Int, mask: IntArray, deg: IntArray, iccsze: Reference<Int>, ls: IntArray, offset: Int) {
        val pcol = matrix.columnPointers
        val irow = matrix.rowIndices
        var i: Int
        var ideg: Int
        var j: Int
        var jstop: Int
        var jstrt: Int
        var lbegin: Int
        var lvlend: Int
        var lvsize = 1
        var nbr: Int
        var node: Int
        ls[offset] = root
        pcol[root] = -pcol[root]
        lvlend = 0
        iccsze.value = 1
        while (lvsize > 0) {
            lbegin = lvlend + 1
            lvlend = iccsze.value
            i = lbegin
            while (i <= lvlend) {
                node = ls[offset + i - 1]
                jstrt = -pcol[node]
                jstop = Math.abs(pcol[node + 1]) - 1
                ideg = 0
                j = jstrt
                while (j <= jstop) {
                    nbr = irow[j - 1]
                    if (mask[nbr] != 0) {
                        ideg += 1
                        if (0 <= pcol[nbr]) {
                            pcol[nbr] = -pcol[nbr]
                            iccsze.value = iccsze.value + 1
                            ls[offset + iccsze.value - 1] = nbr
                        }
                    }
                    j++
                }
                deg[node] = ideg
                i++
            }
            lvsize = iccsze.value - lvlend
        }
        i = 0
        while (i < iccsze.value) {
            node = ls[offset + i]
            pcol[node] = -pcol[node]
            i++
        }
        return
    }

    private fun permBandwidth(matrix: AdjacencyMatrix, perm: IntArray, perm_inv: IntArray): Int {
        val pcol = matrix.columnPointers
        val irow = matrix.rowIndices
        var col: Int
        var band_lo = 0
        var band_hi = 0
        val n = matrix.size
        var i = 0
        while (i < n) {
            var j = pcol[perm[i]]
            while (j < pcol[perm[i] + 1]) {
                col = perm_inv[irow[j - 1]]
                band_lo = Math.max(band_lo, i - col)
                band_hi = Math.max(band_hi, col - i)
                j++
            }
            i++
        }
        return band_lo + 1 + band_hi
    }

    private fun permInverse(matrix: AdjacencyMatrix, perm: IntArray): IntArray {
        val n = matrix.size
        val perm_inv = IntArray(n)
        for (i in 0..n - 1) {
            perm_inv[perm[i]] = i
        }
        return perm_inv
    }

    private fun reverseVector(a: IntArray, offset: Int, size: Int) {
        var i = 0
        while (i < size / 2) {
            val j = a[offset + i]
            a[offset + i] = a[offset + size - 1 - i]
            a[offset + size - 1 - i] = j
            i++
        }
        return
    }

    private fun shift(a: IntArray, up: Boolean) {
        val length = a.size
        if (up) {
            var i = 0
            while (i < length) {
                a[i]++
                i++
            }
        } else {
            var i = 0
            while (i < length) {
                a[i]--
                i++
            }
        }
    }
}
