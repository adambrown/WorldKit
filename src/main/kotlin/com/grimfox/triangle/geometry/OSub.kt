package com.grimfox.triangle.geometry

class OSub {

    companion object {

        fun isDead(sub: SubSegment): Boolean {
            return sub.subsegs[0].segment == null
        }

        fun kill(sub: SubSegment) {
            sub.subsegs[0].segment = null
            sub.subsegs[1].segment = null
        }
    }

    var segment: SubSegment? = null

    var orient = 0

    fun sym(os: OSub) {
        os.segment = segment
        os.orient = 1 - orient
    }

    fun sym() {
        orient = 1 - orient
    }

    fun pivot(os: OSub) {
        segment!!.subsegs[orient].copy(os)
    }

    fun pivot(ot: OTri) {
        segment!!.triangles[orient].copy(ot)
    }

    fun next(os: OSub) {
        segment!!.subsegs[1 - orient].copy(os)
    }

    operator fun next() {
        segment!!.subsegs[1 - orient].copy(this)
    }

    fun org(): Vertex? {
        return segment!!.vertices[orient]
    }

    fun dest(): Vertex? {
        return segment!!.vertices[1 - orient]
    }

    fun copy(os: OSub) {
        os.segment = segment
        os.orient = orient
    }

    fun reset() {
        segment = null
        orient = 0
    }

    fun setOrg(vertex: Vertex?) {
        segment!!.vertices[orient] = vertex
    }

    fun setDest(vertex: Vertex?) {
        segment!!.vertices[1 - orient] = vertex
    }

    fun segOrg(): Vertex? {
        return segment!!.vertices[2 + orient]
    }

    fun segDest(): Vertex? {
        return segment!!.vertices[3 - orient]
    }

    fun setSegOrg(vertex: Vertex?) {
        segment!!.vertices[2 + orient] = vertex
    }

    fun setSegDest(vertex: Vertex?) {
        segment!!.vertices[3 - orient] = vertex
    }

    fun bond(os: OSub) {
        os.copy(segment!!.subsegs[orient])
        this.copy(os.segment!!.subsegs[os.orient])
    }

    fun dissolve(dummy: SubSegment) {
        segment!!.subsegs[orient].segment = dummy
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as OSub
        if (segment != other.segment) return false
        if (orient != other.orient) return false
        return true
    }

    fun equals(os: OSub): Boolean {
        return segment === os.segment && orient == os.orient
    }

    fun triDissolve(dummy: Triangle) {
        segment!!.triangles[orient].triangle = dummy
    }

    override fun hashCode(): Int {
        var result = segment?.hashCode() ?: 0
        result = 31 * result + orient
        return result
    }

    override fun toString(): String {
        return "OSub(segment=${segment?.hash}, orient=$orient)"
    }
}
