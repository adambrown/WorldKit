package com.grimfox.triangle.tools

import com.grimfox.triangle.Mesh
import java.util.*

class AdjacencyMatrix {

    internal var nnz: Int = 0

    var columnPointers: IntArray
        internal set

    var rowIndices: IntArray
        internal set

    val size: Int

    constructor(mesh: Mesh) {
        this.size = mesh._vertices.size
        this.columnPointers = adjacencyCount(mesh)
        this.nnz = columnPointers[size]
        this.rowIndices = adjacencySet(mesh, this.columnPointers)
        sortIndices()
    }

    constructor(pcol: IntArray, irow: IntArray) {
        this.size = pcol.size - 1
        this.nnz = pcol[size]
        this.columnPointers = pcol
        this.rowIndices = irow
        if (pcol[0] != 0) {
            throw IllegalArgumentException("Expected 0-based indexing.")
        }
        if (irow.size < nnz) {
            throw IllegalArgumentException()
        }
    }

    fun bandwidth(): Int {
        var col: Int
        var j: Int
        var band_lo = 0
        var band_hi = 0
        var i = 0
        while (i < size) {
            j = columnPointers[i]
            while (j < columnPointers[i + 1]) {
                col = rowIndices[j]
                band_lo = Math.max(band_lo, i - col)
                band_hi = Math.max(band_hi, col - i)
                j++
            }
            i++
        }
        return band_lo + 1 + band_hi
    }

    private fun adjacencyCount(mesh: Mesh): IntArray {
        val n = size
        var n1: Int
        var n2: Int
        var n3: Int
        var tid: Int
        var nid: Int
        val pcol = IntArray(n + 1)
        for (i in 0..n - 1) {
            pcol[i] = 1
        }
        for (tri in mesh._triangles) {
            tid = tri.id
            n1 = tri.vertices[0]!!.id
            n2 = tri.vertices[1]!!.id
            n3 = tri.vertices[2]!!.id
            nid = tri.neighbors[2].triangle!!.id
            if (nid < 0 || tid < nid) {
                pcol[n1] = pcol[n1] + 1
                pcol[n2] = pcol[n2] + 1
            }
            nid = tri.neighbors[0].triangle!!.id
            if (nid < 0 || tid < nid) {
                pcol[n2] = pcol[n2] + 1
                pcol[n3] = pcol[n3] + 1
            }
            nid = tri.neighbors[1].triangle!!.id
            if (nid < 0 || tid < nid) {
                pcol[n3] = pcol[n3] + 1
                pcol[n1] = pcol[n1] + 1
            }
        }
        for (i in n downTo 1) {
            pcol[i] = pcol[i - 1]
        }
        pcol[0] = 0
        for (i in 1..n) {
            pcol[i] = pcol[i - 1] + pcol[i]
        }
        return pcol
    }

    private fun adjacencySet(mesh: Mesh, pcol: IntArray): IntArray {
        val n = this.size
        val col = IntArray(n)
        System.arraycopy(pcol, 0, col, 0, n)
        val nnz = pcol[n]
        val list = IntArray(nnz)
        var i = 0
        while (i < n) {
            list[col[i]] = i
            col[i] = col[i] + 1
            i++
        }
        var n1: Int
        var n2: Int
        var n3: Int
        var tid: Int
        var nid: Int
        for (tri in mesh._triangles) {
            tid = tri.id
            n1 = tri.vertices[0]!!.id
            n2 = tri.vertices[1]!!.id
            n3 = tri.vertices[2]!!.id
            nid = tri.neighbors[2].triangle!!.id
            if (nid < 0 || tid < nid) {
                list[col[n1]++] = n2
                list[col[n2]++] = n1
            }
            nid = tri.neighbors[0].triangle!!.id
            if (nid < 0 || tid < nid) {
                list[col[n2]++] = n3
                list[col[n3]++] = n2
            }
            nid = tri.neighbors[1].triangle!!.id
            if (nid < 0 || tid < nid) {
                list[col[n1]++] = n3
                list[col[n3]++] = n1
            }
        }
        return list
    }

    fun sortIndices() {
        var k1: Int
        var k2: Int
        val n = size
        val list = this.rowIndices
        for (i in 0..n - 1) {
            k1 = columnPointers[i]
            k2 = columnPointers[i + 1]
            Arrays.sort(list, k1, k2 - k1)
        }
    }
}
