package com.grimfox.triangle

import com.grimfox.logging.LOG
import com.grimfox.triangle.Behavior.BoundarySplitMode
import com.grimfox.triangle.TriangleLocator.LocateResult
import com.grimfox.triangle.geometry.*
import com.grimfox.triangle.geometry.Vertex.VertexType
import com.grimfox.triangle.meshing.*
import com.grimfox.triangle.meshing.data.BadSubsegment
import com.grimfox.triangle.meshing.iterators.EdgeIterator
import com.grimfox.triangle.tools.*
import java.util.*

class Mesh(private val config: Configuration) {

    internal enum class InsertVertexResult {
        SUCCESSFUL,
        ENCROACHING,
        VIOLATING,
        DUPLICATE
    }

    enum class NodeNumbering {
        NONE,
        LINEAR,
        CUTHILL_MCKEE
    }

    companion object {

        internal val DUMMY = -1
    }

    internal constructor(mesh: Mesh) : this(mesh.config) {
        mesh.copyTo(this)
    }

    private var flipstack = Stack<OTri>()
    private var segmentHash = 0
    private var triHash = 0

    internal var _currentNumbering = NodeNumbering.NONE
    internal var _meshDimension: Int = 0
    internal var predicates: Predicates = config.predicates.invoke()
    internal var qualityMesher: QualityMesher? = null
    internal var _triangles: TrianglePool = config.trianglePool.invoke()
    internal var _subsegs: HashMap<Int, SubSegment> = HashMap()
    internal var _vertices: HashMap<Int, Vertex> = HashMap()
    internal var hash_vtx = 0
    internal var _holes: ArrayList<Point> = ArrayList()
    internal var regions: ArrayList<RegionPointer> = ArrayList()
    internal var _bounds: Rectangle = Rectangle()
    internal var numberOfInputPoints: Int = 0
    internal var insegments: Int = 0
    internal var undeads: Int = 0
    internal var hullsize: Int = 0
    internal var steinerleft: Int = -1
    internal var checksegments: Boolean = false
    internal var checkquality: Boolean = false
    internal var infvertex1: Vertex? = null
    internal var infvertex2: Vertex? = null
    internal var infvertex3: Vertex? = null
    internal var locator: TriangleLocator = TriangleLocator(this, predicates)
    internal var behavior: Behavior = Behavior()
    internal val dummytri: Triangle = Triangle()
    internal val dummysub: SubSegment = SubSegment()

    init {
        dummysub.hash = DUMMY
        dummysub.subsegs[0].segment = dummysub
        dummysub.subsegs[1].segment = dummysub
        dummytri.id = DUMMY
        dummytri.hash = dummytri.id
        dummytri.neighbors[0].triangle = dummytri
        dummytri.neighbors[1].triangle = dummytri
        dummytri.neighbors[2].triangle = dummytri
        dummytri.subsegs[0].segment = dummysub
        dummytri.subsegs[1].segment = dummysub
        dummytri.subsegs[2].segment = dummysub
    }

    internal val numberOfEdges: Int
        get() = (3 * _triangles.size + hullsize) / 2

    internal val isPolygon: Boolean
        get() = insegments > 0

    val currentNumbering: NodeNumbering
        get() = _currentNumbering

    val dimension: Int
        get() = _meshDimension

    val vertices: List<Vertex>
        get() = _vertices.values.sortedBy { it.id }

    val edges: Iterable<Edge>
        get() {
            val mesh = this
            return object : Iterable<Edge> {
                override fun iterator(): Iterator<Edge> {
                    return EdgeIterator(mesh)
                }
            }
        }

    val segments: Collection<SubSegment>
        get() = _subsegs.values

    val triangles: List<Triangle>
        get() = _triangles.toList()

    val holes: List<Point>
        get() = _holes.toList()

    val bounds: Rectangle
        get() = Rectangle(_bounds)

    fun renumber() {
        renumber(NodeNumbering.LINEAR)
    }

    fun refine(quality: QualityOptions, delaunay: Boolean) {
        numberOfInputPoints = _vertices.size
        if (behavior.isPlanarStraightLineGraph) {
            insegments = if (behavior.useSegments) _subsegs.size else hullsize
        }
        reset()
        if (qualityMesher == null) {
            qualityMesher = QualityMesher(this, Configuration())
        }
        qualityMesher!!.apply(quality, delaunay)
        cleanup()
    }

    fun cleanup() {
        if (undeads > 0) {
            val removes = ArrayList<Pair<Int, Vertex>>()
            _vertices.forEach { i, vertex ->
                if (vertex.type == VertexType.UNDEAD_VERTEX) {
                    removes.add(Pair(i, vertex))
                }
            }
            if (removes.isNotEmpty()) {
                removes.forEach {
                    vertexDealloc(it.second)
                    _vertices.remove(it.first)
                }
                undeads = 0
                _currentNumbering = NodeNumbering.NONE
                renumber()
            }
        }
    }

    fun findCircumcenter(tri: ITriangle): Point {
        return predicates.findCircumcenter(tri.getVertex(0)!!, tri.getVertex(1)!!, tri.getVertex(2)!!)
    }

    fun findOrientation(a: Point, b: Point, c: Point): Double { // positive is counter clockwise
        return predicates.counterClockwise(a, b, c)
    }

    internal fun renumber(num: NodeNumbering) {
        if (num == _currentNumbering) {
            return
        }
        var id: Int
        if (num === NodeNumbering.LINEAR) {
            id = 0
            for (node in _vertices.values.sortedBy { it.id }) {
                node.id = id++
            }
        } else if (num === NodeNumbering.CUTHILL_MCKEE) {
            val rcm = CuthillMcKee()
            val iperm = rcm.renumber(this)
            for (node in _vertices.values) {
                node.id = iperm[node.id]
            }
        }
        _currentNumbering = num
        id = 0
        for (item in _triangles) {
            item.id = id++
        }
    }

    internal fun copyTo(target: Mesh) {
        target._vertices = _vertices
        target._triangles = _triangles
        target._subsegs = _subsegs
        target._holes = _holes
        target.regions = regions
        target.hash_vtx = hash_vtx
        target.segmentHash = segmentHash
        target.triHash = triHash
        target._currentNumbering = _currentNumbering
        target.hullsize = hullsize
    }

    internal fun transferNodes(points: List<Vertex>) {
        numberOfInputPoints = points.size
        _meshDimension = 2
        _bounds = Rectangle()
        if (numberOfInputPoints < 3) {
            LOG.error("Input must have at least three input vertices.")
            throw Exception("Input must have at least three input vertices.")
        }
        val v = points[0]
        val userId = v.id != points[1].id
        for (p in points) {
            if (userId) {
                p.hash = p.id
                hash_vtx = Math.max(p.hash + 1, hash_vtx)
            } else {
                p.id = hash_vtx++
                p.hash = p.id
            }
            _vertices.put(p.hash, p)
            _bounds.expand(p)
        }
    }

    internal fun makeVertexMap() {
        val tri = OTri()
        var triorg: Vertex
        for (t in _triangles) {
            tri.triangle = t
            tri.orient = 0
            while (tri.orient < 3) {
                triorg = tri.org()!!
                tri.copy(triorg.tri)
                tri.orient++
            }
        }
    }

    internal fun makeTriangle(newotri: OTri) {
        val tri = _triangles.get()
        tri.subsegs[0].segment = dummysub
        tri.subsegs[1].segment = dummysub
        tri.subsegs[2].segment = dummysub
        tri.neighbors[0].triangle = dummytri
        tri.neighbors[1].triangle = dummytri
        tri.neighbors[2].triangle = dummytri
        newotri.triangle = tri
        newotri.orient = 0
    }

    internal fun makeSegment(newsubseg: OSub) {
        val seg = SubSegment()
        seg.hash = segmentHash++
        seg.subsegs[0].segment = dummysub
        seg.subsegs[1].segment = dummysub
        seg.triangles[0].triangle = dummytri
        seg.triangles[1].triangle = dummytri
        newsubseg.segment = seg
        newsubseg.orient = 0
        _subsegs.put(seg.hash, seg)
    }

    internal fun insertVertex(newvertex: Vertex, searchtri: OTri, splitseg: OSub, segmentflaws: Boolean, triflaws: Boolean, noExact: Boolean): InsertVertexResult {
        val horiz = OTri()
        val top = OTri()
        val botleft = OTri()
        val botright = OTri()
        val topleft = OTri()
        val topright = OTri()
        val newbotleft = OTri()
        val newbotright = OTri()
        val newtopright = OTri()
        val botlcasing = OTri()
        val botrcasing = OTri()
        val toplcasing = OTri()
        val toprcasing = OTri()
        val testtri = OTri()
        val botlsubseg = OSub()
        val botrsubseg = OSub()
        val toplsubseg = OSub()
        val toprsubseg = OSub()
        val brokensubseg = OSub()
        val checksubseg = OSub()
        val rightsubseg = OSub()
        val newsubseg = OSub()
        val encroached: BadSubsegment
        val first: Vertex
        var leftvertex: Vertex
        var rightvertex: Vertex
        val botvertex: Vertex
        val topvertex: Vertex
        var farvertex: Vertex
        val segmentorg: Vertex
        val segmentdest: Vertex
        var region: Int
        var area: Double
        var success: InsertVertexResult
        val intersect: LocateResult
        var doflip: Boolean
        val mirrorflag: Boolean
        var enq: Boolean
        if (splitseg.segment == null) {
            if (searchtri.triangle!!.id == DUMMY) {
                horiz.triangle = dummytri
                horiz.orient = 0
                horiz.sym()
                intersect = locator.locate(newvertex, horiz, noExact)
            } else {
                searchtri.copy(horiz)
                intersect = locator.preciseLocate(newvertex, horiz, true, noExact)
            }
        } else {
            searchtri.copy(horiz)
            intersect = LocateResult.ON_EDGE
        }
        if (intersect == LocateResult.ON_VERTEX) {
            horiz.copy(searchtri)
            locator.update(horiz)
            return InsertVertexResult.DUPLICATE
        }
        if (intersect == LocateResult.ON_EDGE || intersect == LocateResult.OUTSIDE) {
            if (checksegments && splitseg.segment == null) {
                horiz.pivot(brokensubseg)
                if (brokensubseg.segment!!.hash != DUMMY) {
                    if (segmentflaws) {
                        enq = behavior.boundarySplitMode != BoundarySplitMode.NO_SPLIT
                        if (enq && behavior.boundarySplitMode == BoundarySplitMode.SPLIT_INTERNAL_ONLY) {
                            horiz.sym(testtri)
                            enq = testtri.triangle!!.id != DUMMY
                        }
                        if (enq) {
                            encroached = BadSubsegment()
                            brokensubseg.copy(encroached.subsegment)
                            encroached.org = brokensubseg.org()
                            encroached.dest = brokensubseg.dest()
                            qualityMesher!!.addBadSubseg(encroached)
                        }
                    }
                    horiz.copy(searchtri)
                    locator.update(horiz)
                    return InsertVertexResult.VIOLATING
                }
            }
            horiz.lprev(botright)
            botright.sym(botrcasing)
            horiz.sym(topright)
            mirrorflag = topright.triangle!!.id != DUMMY
            if (mirrorflag) {
                topright.lnext()
                topright.sym(toprcasing)
                makeTriangle(newtopright)
            } else {
                hullsize++
            }
            makeTriangle(newbotright)
            rightvertex = horiz.org()!!
            leftvertex = horiz.dest()!!
            botvertex = horiz.apex()!!
            newbotright.setOrg(botvertex)
            newbotright.setDest(rightvertex)
            newbotright.setApex(newvertex)
            horiz.setOrg(newvertex)
            newbotright.triangle!!.label = botright.triangle!!.label
            if (behavior.applyMaxTriangleAreaConstraints) {
                newbotright.triangle!!.area = botright.triangle!!.area
            }
            if (mirrorflag) {
                topvertex = topright.dest()!!
                newtopright.setOrg(rightvertex)
                newtopright.setDest(topvertex)
                newtopright.setApex(newvertex)
                topright.setOrg(newvertex)
                newtopright.triangle!!.label = topright.triangle!!.label
                if (behavior.applyMaxTriangleAreaConstraints) {
                    newtopright.triangle!!.area = topright.triangle!!.area
                }
            }
            if (checksegments) {
                botright.pivot(botrsubseg)
                if (botrsubseg.segment!!.hash != DUMMY) {
                    botright.segDissolve(dummysub)
                    newbotright.segBond(botrsubseg)
                }
                if (mirrorflag) {
                    topright.pivot(toprsubseg)
                    if (toprsubseg.segment!!.hash != DUMMY) {
                        topright.segDissolve(dummysub)
                        newtopright.segBond(toprsubseg)
                    }
                }
            }
            newbotright.bond(botrcasing)
            newbotright.lprev()
            newbotright.bond(botright)
            newbotright.lprev()
            if (mirrorflag) {
                newtopright.bond(toprcasing)
                newtopright.lnext()
                newtopright.bond(topright)
                newtopright.lnext()
                newtopright.bond(newbotright)
            }
            if (splitseg.segment != null) {
                splitseg.setDest(newvertex)
                segmentorg = splitseg.segOrg()!!
                segmentdest = splitseg.segDest()!!
                splitseg.sym()
                splitseg.pivot(rightsubseg)
                insertSubseg(newbotright, splitseg.segment!!.label)
                newbotright.pivot(newsubseg)
                newsubseg.setSegOrg(segmentorg)
                newsubseg.setSegDest(segmentdest)
                splitseg.bond(newsubseg)
                newsubseg.sym()
                newsubseg.bond(rightsubseg)
                splitseg.sym()
                if (newvertex.label == 0) {
                    newvertex.label = splitseg.segment!!.label
                }
            }
            if (checkquality) {
                flipstack.clear()
                flipstack.push(OTri())
                flipstack.push(horiz.copy())
            }
            horiz.lnext()
        } else {
            horiz.lnext(botleft)
            horiz.lprev(botright)
            botleft.sym(botlcasing)
            botright.sym(botrcasing)
            makeTriangle(newbotleft)
            makeTriangle(newbotright)
            rightvertex = horiz.org()!!
            leftvertex = horiz.dest()!!
            botvertex = horiz.apex()!!
            newbotleft.setOrg(leftvertex)
            newbotleft.setDest(botvertex)
            newbotleft.setApex(newvertex)
            newbotright.setOrg(botvertex)
            newbotright.setDest(rightvertex)
            newbotright.setApex(newvertex)
            horiz.setApex(newvertex)
            newbotleft.triangle!!.label = horiz.triangle!!.label
            newbotright.triangle!!.label = horiz.triangle!!.label
            if (behavior.applyMaxTriangleAreaConstraints) {
                area = horiz.triangle!!.area
                newbotleft.triangle!!.area = area
                newbotright.triangle!!.area = area
            }
            if (checksegments) {
                botleft.pivot(botlsubseg)
                if (botlsubseg.segment!!.hash != DUMMY) {
                    botleft.segDissolve(dummysub)
                    newbotleft.segBond(botlsubseg)
                }
                botright.pivot(botrsubseg)
                if (botrsubseg.segment!!.hash != DUMMY) {
                    botright.segDissolve(dummysub)
                    newbotright.segBond(botrsubseg)
                }
            }
            newbotleft.bond(botlcasing)
            newbotright.bond(botrcasing)
            newbotleft.lnext()
            newbotright.lprev()
            newbotleft.bond(newbotright)
            newbotleft.lnext()
            botleft.bond(newbotleft)
            newbotright.lprev()
            botright.bond(newbotright)
            if (checkquality) {
                flipstack.clear()
                flipstack.push(horiz.copy())
            }
        }
        success = InsertVertexResult.SUCCESSFUL
        if (newvertex.tri.triangle != null) {
            newvertex.tri.setOrg(rightvertex)
            newvertex.tri.setDest(leftvertex)
            newvertex.tri.setApex(botvertex)
        }
        first = horiz.org()!!
        rightvertex = first
        leftvertex = horiz.dest()!!
        while (true) {
            doflip = true
            if (checksegments) {
                horiz.pivot(checksubseg)
                if (checksubseg.segment!!.hash != DUMMY) {
                    doflip = false
                    if (segmentflaws) {
                        if (qualityMesher!!.checkSeg4Encroach(checksubseg) > 0) {
                            success = InsertVertexResult.ENCROACHING
                        }
                    }
                }
            }
            if (doflip) {
                horiz.sym(top)
                if (top.triangle!!.id == DUMMY) {
                    doflip = false
                } else {
                    farvertex = top.apex()!!
                    if (leftvertex === infvertex1 || leftvertex === infvertex2 || leftvertex === infvertex3) {
                        doflip = predicates.counterClockwise(newvertex, rightvertex, farvertex, noExact) > 0.0
                    } else if (rightvertex === infvertex1 || rightvertex === infvertex2 || rightvertex === infvertex3) {
                        doflip = predicates.counterClockwise(farvertex, leftvertex, newvertex, noExact) > 0.0
                    } else if (farvertex === infvertex1 || farvertex === infvertex2 || farvertex === infvertex3) {
                        doflip = false
                    } else {
                        doflip = predicates.inCircle(leftvertex, newvertex, rightvertex, farvertex, noExact) > 0.0
                    }
                    if (doflip) {
                        top.lprev(topleft)
                        topleft.sym(toplcasing)
                        top.lnext(topright)
                        topright.sym(toprcasing)
                        horiz.lnext(botleft)
                        botleft.sym(botlcasing)
                        horiz.lprev(botright)
                        botright.sym(botrcasing)
                        topleft.bond(botlcasing)
                        botleft.bond(botrcasing)
                        botright.bond(toprcasing)
                        topright.bond(toplcasing)
                        if (checksegments) {
                            topleft.pivot(toplsubseg)
                            botleft.pivot(botlsubseg)
                            botright.pivot(botrsubseg)
                            topright.pivot(toprsubseg)
                            if (toplsubseg.segment!!.hash == DUMMY) {
                                topright.segDissolve(dummysub)
                            } else {
                                topright.segBond(toplsubseg)
                            }
                            if (botlsubseg.segment!!.hash == DUMMY) {
                                topleft.segDissolve(dummysub)
                            } else {
                                topleft.segBond(botlsubseg)
                            }
                            if (botrsubseg.segment!!.hash == DUMMY) {
                                botleft.segDissolve(dummysub)
                            } else {
                                botleft.segBond(botrsubseg)
                            }
                            if (toprsubseg.segment!!.hash == DUMMY) {
                                botright.segDissolve(dummysub)
                            } else {
                                botright.segBond(toprsubseg)
                            }
                        }
                        horiz.setOrg(farvertex)
                        horiz.setDest(newvertex)
                        horiz.setApex(rightvertex)
                        top.setOrg(newvertex)
                        top.setDest(farvertex)
                        top.setApex(leftvertex)
                        region = Math.min(top.triangle!!.label, horiz.triangle!!.label)
                        top.triangle!!.label = region
                        horiz.triangle!!.label = region
                        if (behavior.applyMaxTriangleAreaConstraints) {
                            if (top.triangle!!.area <= 0.0 || horiz.triangle!!.area <= 0.0) {
                                area = -1.0
                            } else {
                                area = 0.5 * (top.triangle!!.area + horiz.triangle!!.area)
                            }
                            top.triangle!!.area = area
                            horiz.triangle!!.area = area
                        }
                        if (checkquality) {
                            flipstack.push(horiz.copy())
                        }
                        horiz.lprev()
                        leftvertex = farvertex
                    }
                }
            }
            if (!doflip) {
                if (triflaws) {
                    qualityMesher!!.testTriangle(horiz)
                }
                horiz.lnext()
                horiz.sym(testtri)
                if (leftvertex === first || testtri.triangle!!.id == DUMMY) {
                    horiz.lnext(searchtri)
                    val recenttri = OTri()
                    horiz.lnext(recenttri)
                    locator.update(recenttri)
                    return success
                }
                testtri.lnext(horiz)
                rightvertex = leftvertex
                leftvertex = horiz.dest()!!
            }
        }
    }

    internal fun insertSubseg(tri: OTri, subsegmark: Int) {
        val oppotri = OTri()
        val newsubseg = OSub()
        val triorg = tri.org()!!
        val tridest = tri.dest()!!
        if (triorg.label == 0) {
            triorg.label = subsegmark
        }
        if (tridest.label == 0) {
            tridest.label = subsegmark
        }
        tri.pivot(newsubseg)
        if (newsubseg.segment!!.hash == DUMMY) {
            makeSegment(newsubseg)
            newsubseg.setOrg(tridest)
            newsubseg.setDest(triorg)
            newsubseg.setSegOrg(tridest)
            newsubseg.setSegDest(triorg)
            tri.segBond(newsubseg)
            tri.sym(oppotri)
            newsubseg.sym()
            oppotri.segBond(newsubseg)
            newsubseg.segment!!.label = subsegmark
        } else if (newsubseg.segment!!.label == 0) {
            newsubseg.segment!!.label = subsegmark
        }
    }

    internal fun flip(flipedge: OTri) {
        val botleft = OTri()
        val botright = OTri()
        val topleft = OTri()
        val topright = OTri()
        val top = OTri()
        val botlcasing = OTri()
        val botrcasing = OTri()
        val toplcasing = OTri()
        val toprcasing = OTri()
        val botlsubseg = OSub()
        val botrsubseg = OSub()
        val toplsubseg = OSub()
        val toprsubseg = OSub()
        val farvertex: Vertex
        val rightvertex = flipedge.org()
        val leftvertex = flipedge.dest()
        val botvertex = flipedge.apex()
        flipedge.sym(top)
        farvertex = top.apex()!!
        top.lprev(topleft)
        topleft.sym(toplcasing)
        top.lnext(topright)
        topright.sym(toprcasing)
        flipedge.lnext(botleft)
        botleft.sym(botlcasing)
        flipedge.lprev(botright)
        botright.sym(botrcasing)
        topleft.bond(botlcasing)
        botleft.bond(botrcasing)
        botright.bond(toprcasing)
        topright.bond(toplcasing)
        if (checksegments) {
            topleft.pivot(toplsubseg)
            botleft.pivot(botlsubseg)
            botright.pivot(botrsubseg)
            topright.pivot(toprsubseg)
            if (toplsubseg.segment!!.hash == DUMMY) {
                topright.segDissolve(dummysub)
            } else {
                topright.segBond(toplsubseg)
            }
            if (botlsubseg.segment!!.hash == DUMMY) {
                topleft.segDissolve(dummysub)
            } else {
                topleft.segBond(botlsubseg)
            }
            if (botrsubseg.segment!!.hash == DUMMY) {
                botleft.segDissolve(dummysub)
            } else {
                botleft.segBond(botrsubseg)
            }
            if (toprsubseg.segment!!.hash == DUMMY) {
                botright.segDissolve(dummysub)
            } else {
                botright.segBond(toprsubseg)
            }
        }
        flipedge.setOrg(farvertex)
        flipedge.setDest(botvertex)
        flipedge.setApex(rightvertex)
        top.setOrg(botvertex)
        top.setDest(farvertex)
        top.setApex(leftvertex)
    }

    internal fun deleteVertex(deltri: OTri, noExact: Boolean) {
        val countingtri = OTri()
        val firstedge = OTri()
        val lastedge = OTri()
        val deltriright = OTri()
        val lefttri = OTri()
        val righttri = OTri()
        val leftcasing = OTri()
        val rightcasing = OTri()
        val leftsubseg = OSub()
        val rightsubseg = OSub()
        val neworg: Vertex
        val delvertex = deltri.org()!!
        vertexDealloc(delvertex)
        deltri.onext(countingtri)
        var edgecount = 1
        while (!deltri.equals(countingtri)) {
            edgecount++
            countingtri.onext()
        }
        if (edgecount > 3) {
            deltri.onext(firstedge)
            deltri.oprev(lastedge)
            triangulatePolygon(firstedge, lastedge, edgecount, false, behavior.boundarySplitMode == BoundarySplitMode.SPLIT, noExact)
        }
        deltri.lprev(deltriright)
        deltri.dnext(lefttri)
        lefttri.sym(leftcasing)
        deltriright.oprev(righttri)
        righttri.sym(rightcasing)
        deltri.bond(leftcasing)
        deltriright.bond(rightcasing)
        lefttri.pivot(leftsubseg)
        if (leftsubseg.segment!!.hash != DUMMY) {
            deltri.segBond(leftsubseg)
        }
        righttri.pivot(rightsubseg)
        if (rightsubseg.segment!!.hash != DUMMY) {
            deltriright.segBond(rightsubseg)
        }
        neworg = lefttri.org()!!
        deltri.setOrg(neworg)
        if (behavior.boundarySplitMode == BoundarySplitMode.SPLIT) {
            qualityMesher!!.testTriangle(deltri)
        }
        triangleDealloc(lefttri.triangle!!)
        triangleDealloc(righttri.triangle!!)
    }

    internal fun undoVertex() {
        val fliptri = OTri()
        val botleft = OTri()
        val botright = OTri()
        val topright = OTri()
        val botlcasing = OTri()
        val botrcasing = OTri()
        val toprcasing = OTri()
        val gluetri = OTri()
        val botlsubseg = OSub()
        val botrsubseg = OSub()
        val toprsubseg = OSub()
        var botvertex: Vertex?
        var rightvertex: Vertex?
        while (flipstack.size > 0) {
            flipstack.pop().copy(fliptri)
            if (flipstack.size == 0) {
                fliptri.dprev(botleft)
                botleft.lnext()
                fliptri.onext(botright)
                botright.lprev()
                botleft.sym(botlcasing)
                botright.sym(botrcasing)
                botvertex = botleft.dest()
                fliptri.setApex(botvertex)
                fliptri.lnext()
                fliptri.bond(botlcasing)
                botleft.pivot(botlsubseg)
                fliptri.segBond(botlsubseg)
                fliptri.lnext()
                fliptri.bond(botrcasing)
                botright.pivot(botrsubseg)
                fliptri.segBond(botrsubseg)
                triangleDealloc(botleft.triangle!!)
                triangleDealloc(botright.triangle!!)
            } else if (flipstack.peek().triangle == null) {
                fliptri.lprev(gluetri)
                gluetri.sym(botright)
                botright.lnext()
                botright.sym(botrcasing)
                rightvertex = botright.dest()!!
                fliptri.setOrg(rightvertex)
                gluetri.bond(botrcasing)
                botright.pivot(botrsubseg)
                gluetri.segBond(botrsubseg)
                triangleDealloc(botright.triangle!!)
                fliptri.sym(gluetri)
                if (gluetri.triangle!!.id != DUMMY) {
                    gluetri.lnext()
                    gluetri.dnext(topright)
                    topright.sym(toprcasing)
                    gluetri.setOrg(rightvertex)
                    gluetri.bond(toprcasing)
                    topright.pivot(toprsubseg)
                    gluetri.segBond(toprsubseg)
                    triangleDealloc(topright.triangle!!)
                }
                flipstack.clear()
            } else {
                unflip(fliptri)
            }
        }
    }

    internal fun triangleDealloc(dyingtriangle: Triangle) {
        OTri.kill(dyingtriangle)
        _triangles.release(dyingtriangle)
    }

    internal fun subsegDealloc(dyingsubseg: SubSegment) {
        OSub.kill(dyingsubseg)
        _subsegs.remove(dyingsubseg.hash)
    }

    internal fun isConsistent(): Boolean {
        val tri = OTri()
        val oppotri = OTri()
        val oppooppotri = OTri()
        var org: Vertex
        var dest: Vertex
        var apex: Vertex
        var oppoorg: Vertex
        var oppodest: Vertex
        val saveexact = behavior.disableExactMath
        behavior.disableExactMath = false
        var horrors = 0
        for (t in _triangles) {
            tri.triangle = t
            tri.orient = 0
            while (tri.orient < 3) {
                org = tri.org()!!
                dest = tri.dest()!!
                if (tri.orient == 0) {
                    apex = tri.apex()!!
                    if (predicates.counterClockwise(org, dest, apex, false) <= 0.0) {
                        LOG.debug { "Triangle is flat or inverted (ID ${t.id})." }
                        horrors++
                    }
                }
                tri.sym(oppotri)
                if (oppotri.triangle!!.id != DUMMY) {
                    oppotri.sym(oppooppotri)
                    if (tri.triangle !== oppooppotri.triangle || tri.orient != oppooppotri.orient) {
                        if (tri.triangle === oppooppotri.triangle) {
                            LOG.debug("Asymmetric com.grimfox.triangle-com.grimfox.triangle bond: (Right com.grimfox.triangle, wrong orientation)")
                        }
                        horrors++
                    }
                    oppoorg = oppotri.org()!!
                    oppodest = oppotri.dest()!!
                    if (org !== oppodest || dest !== oppoorg) {
                        LOG.debug("Mismatched edge coordinates between two triangles.")
                        horrors++
                    }
                }
                tri.orient++
            }
        }
        makeVertexMap()
        LOG.debug {
            val builder = StringBuilder()
            _vertices.values
                    .filter { it.tri.triangle == null }
                    .forEach { builder.appendln("Vertex (ID ${it.id}) not connected to meshing (duplicate input vertex?)") }
            builder.toString()
        }
        behavior.disableExactMath = saveexact
        return horrors == 0
    }

    internal fun isDelaunay(): Boolean {
        return isDelaunay(false)
    }

    private fun triangulatePolygon(firstedge: OTri, lastedge: OTri, edgecount: Int, doflip: Boolean, triflaws: Boolean, noExact: Boolean) {
        val testtri = OTri()
        val besttri = OTri()
        val tempedge = OTri()
        var testvertex: Vertex
        var bestvertex: Vertex
        var bestnumber = 1
        val leftbasevertex = lastedge.apex()!!
        val rightbasevertex = firstedge.dest()!!
        firstedge.onext(besttri)
        bestvertex = besttri.dest()!!
        besttri.copy(testtri)
        for (i in 2..edgecount - 2) {
            testtri.onext()
            testvertex = testtri.dest()!!
            if (predicates.inCircle(leftbasevertex, rightbasevertex, bestvertex, testvertex, noExact) > 0.0) {
                testtri.copy(besttri)
                bestvertex = testvertex
                bestnumber = i
            }
        }
        if (bestnumber > 1) {
            besttri.oprev(tempedge)
            triangulatePolygon(firstedge, tempedge, bestnumber + 1, true, triflaws, noExact)
        }
        if (bestnumber < edgecount - 2) {
            besttri.sym(tempedge)
            triangulatePolygon(besttri, lastedge, edgecount - bestnumber, true, triflaws, noExact)
            tempedge.sym(besttri)
        }
        if (doflip) {
            flip(besttri)
            if (triflaws) {
                besttri.sym(testtri)
                qualityMesher!!.testTriangle(testtri)
            }
        }
        besttri.copy(lastedge)
    }

    private fun unflip(flipedge: OTri) {
        val botleft = OTri()
        val botright = OTri()
        val topleft = OTri()
        val topright = OTri()
        val top = OTri()
        val botlcasing = OTri()
        val botrcasing = OTri()
        val toplcasing = OTri()
        val toprcasing = OTri()
        val botlsubseg = OSub()
        val botrsubseg = OSub()
        val toplsubseg = OSub()
        val toprsubseg = OSub()
        val farvertex: Vertex
        val rightvertex = flipedge.org()
        val leftvertex = flipedge.dest()
        val botvertex = flipedge.apex()
        flipedge.sym(top)
        farvertex = top.apex()!!
        top.lprev(topleft)
        topleft.sym(toplcasing)
        top.lnext(topright)
        topright.sym(toprcasing)
        flipedge.lnext(botleft)
        botleft.sym(botlcasing)
        flipedge.lprev(botright)
        botright.sym(botrcasing)
        topleft.bond(toprcasing)
        botleft.bond(toplcasing)
        botright.bond(botlcasing)
        topright.bond(botrcasing)
        if (checksegments) {
            topleft.pivot(toplsubseg)
            botleft.pivot(botlsubseg)
            botright.pivot(botrsubseg)
            topright.pivot(toprsubseg)
            if (toplsubseg.segment!!.hash == DUMMY) {
                botleft.segDissolve(dummysub)
            } else {
                botleft.segBond(toplsubseg)
            }
            if (botlsubseg.segment!!.hash == DUMMY) {
                botright.segDissolve(dummysub)
            } else {
                botright.segBond(botlsubseg)
            }
            if (botrsubseg.segment!!.hash == DUMMY) {
                topright.segDissolve(dummysub)
            } else {
                topright.segBond(botrsubseg)
            }
            if (toprsubseg.segment!!.hash == DUMMY) {
                topleft.segDissolve(dummysub)
            } else {
                topleft.segBond(toprsubseg)
            }
        }
        flipedge.setOrg(botvertex)
        flipedge.setDest(farvertex)
        flipedge.setApex(leftvertex)
        top.setOrg(farvertex)
        top.setDest(botvertex)
        top.setApex(rightvertex)
    }

    private fun vertexDealloc(dyingvertex: Vertex) {
        dyingvertex.type = VertexType.DEAD_VERTEX
        _vertices.remove(dyingvertex.hash)
    }

    private fun isDelaunay(constrained: Boolean): Boolean {
        val loop = OTri()
        val oppotri = OTri()
        val opposubseg = OSub()
        var org: Vertex
        var dest: Vertex
        var apex: Vertex
        var oppoapex: Vertex?
        var shouldbedelaunay: Boolean
        val saveexact = behavior.disableExactMath
        behavior.disableExactMath = false
        var horrors = 0
        val inf1 = infvertex1
        val inf2 = infvertex2
        val inf3 = infvertex3
        for (tri in _triangles) {
            loop.triangle = tri
            loop.orient = 0
            while (loop.orient < 3) {
                org = loop.org()!!
                dest = loop.dest()!!
                apex = loop.apex()!!
                loop.sym(oppotri)
                oppoapex = oppotri.apex()
                shouldbedelaunay = loop.triangle!!.id < oppotri.triangle!!.id && !OTri.isDead(oppotri.triangle!!) && oppotri.triangle!!.id != DUMMY && org !== inf1 && org !== inf2 && org !== inf3 && dest !== inf1 && dest !== inf2 && dest !== inf3 && apex !== inf1 && apex !== inf2 && apex !== inf3 && oppoapex !== inf1 && oppoapex !== inf2 && oppoapex !== inf3
                if (constrained && checksegments && shouldbedelaunay) {
                    loop.pivot(opposubseg)
                    if (opposubseg.segment!!.hash != DUMMY) {
                        shouldbedelaunay = false
                    }
                }
                if (shouldbedelaunay) {
                    if (predicates.nonRegular(org, dest, apex, oppoapex!!) > 0.0) {
                        LOG.debug { "Non-regular pair of triangles found (IDs ${loop.triangle!!.id}/${oppotri.triangle!!.id})." }
                        horrors++
                    }
                }
                loop.orient++
            }
        }
        behavior.disableExactMath = saveexact
        return horrors == 0
    }

    private fun reset() {
        _currentNumbering = NodeNumbering.NONE
        undeads = 0
        checksegments = false
        checkquality = false
        Statistic.inCircleCount.set(0)
        Statistic.counterClockwiseCount.set(0)
        Statistic.inCircleAdaptCount.set(0)
        Statistic.counterClockwiseAdaptCount.set(0)
        Statistic.orient3dCount.set(0)
        Statistic.hyperbolaCount.set(0)
        Statistic.circleTopCount.set(0)
        Statistic.circumcenterCount.set(0)
    }
}
