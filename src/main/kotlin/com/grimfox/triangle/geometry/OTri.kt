package com.grimfox.triangle.geometry

class OTri {

    companion object {

        private val plus1Mod3 = intArrayOf(1, 2, 0)

        private val minus1Mod3 = intArrayOf(2, 0, 1)

        fun isDead(tri: Triangle): Boolean {
            return tri.neighbors[0].triangle == null
        }

        fun kill(tri: Triangle) {
            tri.neighbors[0].triangle = null
            tri.neighbors[2].triangle = null
        }
    }

    var triangle: Triangle? = null

    var orient = 0

    fun sym(ot: OTri) {
        ot.triangle = triangle!!.neighbors[orient].triangle
        ot.orient = triangle!!.neighbors[orient].orient
    }

    fun sym() {
        val tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
    }

    fun lnext(ot: OTri) {
        ot.triangle = triangle
        ot.orient = plus1Mod3[orient]
    }

    fun lnext() {
        orient = plus1Mod3[orient]
    }

    fun lprev(ot: OTri) {
        ot.triangle = triangle
        ot.orient = minus1Mod3[orient]
    }

    fun lprev() {
        orient = minus1Mod3[orient]
    }

    fun onext(ot: OTri) {
        ot.triangle = triangle
        ot.orient = minus1Mod3[orient]
        val tmp = ot.orient
        ot.orient = ot.triangle!!.neighbors[tmp].orient
        ot.triangle = ot.triangle!!.neighbors[tmp].triangle
    }

    fun onext() {
        orient = minus1Mod3[orient]
        val tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
    }

    fun oprev(ot: OTri) {
        ot.triangle = triangle!!.neighbors[orient].triangle
        ot.orient = triangle!!.neighbors[orient].orient
        ot.orient = plus1Mod3[ot.orient]
    }

    fun oprev() {
        val tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
        orient = plus1Mod3[orient]
    }

    fun dnext(ot: OTri) {
        ot.triangle = triangle!!.neighbors[orient].triangle
        ot.orient = triangle!!.neighbors[orient].orient
        ot.orient = minus1Mod3[ot.orient]
    }

    fun dnext() {
        val tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
        orient = minus1Mod3[orient]
    }

    fun dprev(ot: OTri) {
        ot.triangle = triangle
        ot.orient = plus1Mod3[orient]
        val tmp = ot.orient
        ot.orient = ot.triangle!!.neighbors[tmp].orient
        ot.triangle = ot.triangle!!.neighbors[tmp].triangle
    }

    fun dprev() {
        orient = plus1Mod3[orient]
        val tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
    }

    fun rnext(ot: OTri) {
        ot.triangle = triangle!!.neighbors[orient].triangle
        ot.orient = triangle!!.neighbors[orient].orient
        ot.orient = plus1Mod3[ot.orient]
        val tmp = ot.orient
        ot.orient = ot.triangle!!.neighbors[tmp].orient
        ot.triangle = ot.triangle!!.neighbors[tmp].triangle
    }

    fun rnext() {
        var tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
        orient = plus1Mod3[orient]
        tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
    }

    fun rprev(ot: OTri) {
        ot.triangle = triangle!!.neighbors[orient].triangle
        ot.orient = triangle!!.neighbors[orient].orient
        ot.orient = minus1Mod3[ot.orient]
        val tmp = ot.orient
        ot.orient = ot.triangle!!.neighbors[tmp].orient
        ot.triangle = ot.triangle!!.neighbors[tmp].triangle
    }

    fun rprev() {
        var tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
        orient = minus1Mod3[orient]
        tmp = orient
        orient = triangle!!.neighbors[tmp].orient
        triangle = triangle!!.neighbors[tmp].triangle
    }

    fun org(): Vertex? {
        return triangle!!.vertices[plus1Mod3[orient]]
    }

    fun dest(): Vertex? {
        return triangle!!.vertices[minus1Mod3[orient]]
    }

    fun apex(): Vertex? {
        return triangle!!.vertices[orient]
    }

    fun copy(ot: OTri) {
        ot.triangle = triangle
        ot.orient = orient
    }

    fun copy(): OTri {
        val newOtri = OTri()
        copy(newOtri)
        return newOtri
    }

    fun reset() {
        triangle = null
        orient = 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as OTri
        if (triangle != other.triangle) return false
        if (orient != other.orient) return false
        return true
    }

    fun equals(ot: OTri): Boolean {
        return triangle == ot.triangle && orient == ot.orient
    }

    fun setOrg(v: Vertex?) {
        triangle?.vertices?.set(plus1Mod3[orient], v)
    }

    fun setDest(v: Vertex?) {
        triangle?.vertices?.set(minus1Mod3[orient], v)
    }

    fun setApex(v: Vertex?) {
        triangle?.vertices?.set(orient, v)
    }

    fun bond(ot: OTri) {
        triangle!!.neighbors[orient].triangle = ot.triangle
        triangle!!.neighbors[orient].orient = ot.orient
        ot.triangle!!.neighbors[ot.orient].triangle = this.triangle
        ot.triangle!!.neighbors[ot.orient].orient = this.orient
    }

    fun dissolve(dummy: Triangle) {
        triangle!!.neighbors[orient].triangle = dummy
        triangle!!.neighbors[orient].orient = 0
    }

    fun infect() {
        triangle!!.infected = true
    }

    fun uninfect() {
        triangle!!.infected = false
    }

    val isInfected: Boolean
        get() = triangle!!.infected

    fun pivot(os: OSub) {
        triangle!!.subsegs[orient].copy(os)
    }

    fun segBond(os: OSub) {
        os.copy(triangle!!.subsegs[orient])
        copy(os.segment!!.triangles[os.orient])
    }

    fun segDissolve(dummy: SubSegment) {
        triangle!!.subsegs[orient].segment = dummy
    }

    override fun hashCode(): Int {
        var result = triangle?.hashCode() ?: 0
        result = 31 * result + orient
        return result
    }

    override fun toString(): String {
        return "OTri(com.grimfox.triangle=${triangle?.hash}, orient=$orient)"
    }
}
