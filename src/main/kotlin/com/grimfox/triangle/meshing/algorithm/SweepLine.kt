package com.grimfox.triangle.meshing.algorithm

import com.grimfox.logging.LOG
import com.grimfox.triangle.Configuration
import com.grimfox.triangle.Mesh
import com.grimfox.triangle.Predicates
import com.grimfox.triangle.Reference
import com.grimfox.triangle.geometry.OTri
import com.grimfox.triangle.geometry.Point
import com.grimfox.triangle.geometry.Vertex
import com.grimfox.triangle.geometry.Vertex.VertexType
import com.grimfox.triangle.tools.Statistic
import java.util.*

class SweepLine : TriangulationAlgorithm {

    companion object {

        private val SAMPLE_RATE = 10
    }

    private class State(
            var predicates: Predicates,
            var mesh: Mesh,
            var splayNodes: ArrayList<SplayNode>,
            var randomSeed: Int = 1) {
        fun randomize(choices: Int): Int {
            randomSeed = (randomSeed * 1366 + 150889) % 714025
            return randomSeed / (714025 / choices + 1)
        }
    }

    private class SweepEvent {
        var xKey: Double = 0.toDouble()
        var yKey: Double = 0.toDouble()
        var vertexEvent: Vertex? = null
        val oTriEvent = OTri()
        var heapPosition: Int = 0
    }

    private class SweepEventVertex(var evt: SweepEvent) : Vertex()
    private class SplayNode {
        val keyEdge = OTri()
        var keyDest: Vertex? = null
        var leftChild: SplayNode? = null
        var rightChild: SplayNode? = null
    }

    override fun triangulate(points: List<Vertex>, config: Configuration): Mesh {
        val predicates = config.predicates.invoke()
        val mesh = Mesh(config)
        mesh.transferNodes(points)
        val noExact = mesh.behavior.disableExactMath
        val xMinExtreme = 10 * mesh._bounds.left - 9 * mesh._bounds.right
        val eventHeap: Array<SweepEvent>
        var nextEvent: SweepEvent
        var newEvent: SweepEvent
        var splayRoot: SplayNode?
        val bottommost = OTri()
        val searchTri = OTri()
        val flipTri = OTri()
        val leftTri = OTri()
        val rightTri = OTri()
        val farLeftTri = OTri()
        val farRightTri = OTri()
        val insertTri = OTri()
        val firstVertex: Vertex
        var secondVertex: Vertex
        var nextVertex: Vertex
        var lastVertex: Vertex
        var connectVertex: Vertex
        var leftVertex: Vertex
        var midVertex: Vertex
        var rightVertex: Vertex
        var leftTest: Double
        var rightTest: Double
        var heapSize: Int
        var checkForEvents: Boolean
        var farRightFlag: Boolean
        val splayNodes = ArrayList<SplayNode>()
        val state = State(predicates, mesh, splayNodes)
        splayRoot = null
        heapSize = points.size
        eventHeap = createHeap(state, heapSize)
        mesh.makeTriangle(leftTri)
        mesh.makeTriangle(rightTri)
        leftTri.bond(rightTri)
        leftTri.lnext()
        rightTri.lprev()
        leftTri.bond(rightTri)
        leftTri.lnext()
        rightTri.lprev()
        leftTri.bond(rightTri)
        firstVertex = eventHeap[0].vertexEvent!!
        heapDelete(eventHeap, heapSize, 0)
        heapSize--
        do {
            if (heapSize == 0) {
                LOG.error("Input vertices are all identical.")
                throw Exception("Input vertices are all identical.")
            }
            secondVertex = eventHeap[0].vertexEvent!!
            heapDelete(eventHeap, heapSize, 0)
            heapSize--
            if (firstVertex.x == secondVertex.x && firstVertex.y == secondVertex.y) {
                LOG.debug { "A duplicate vertex appeared and was ignored (ID ${secondVertex.id})." }
                secondVertex.type = VertexType.UNDEAD_VERTEX
                mesh.undeads++
            }
        } while (firstVertex.x == secondVertex.x && firstVertex.y == secondVertex.y)
        leftTri.setOrg(firstVertex)
        leftTri.setDest(secondVertex)
        rightTri.setOrg(secondVertex)
        rightTri.setDest(firstVertex)
        leftTri.lprev(bottommost)
        lastVertex = secondVertex
        while (heapSize > 0) {
            nextEvent = eventHeap[0]
            heapDelete(eventHeap, heapSize, 0)
            heapSize--
            checkForEvents = true
            if (nextEvent.xKey < mesh._bounds.left) {
                nextEvent.oTriEvent.copy(flipTri)
                flipTri.oprev(farLeftTri)
                heapSize = check4DeadEvent(farLeftTri, eventHeap, heapSize)
                flipTri.onext(farRightTri)
                heapSize = check4DeadEvent(farRightTri, eventHeap, heapSize)
                if (farLeftTri.equals(bottommost)) {
                    flipTri.lprev(bottommost)
                }
                mesh.flip(flipTri)
                flipTri.setApex(null)
                flipTri.lprev(leftTri)
                flipTri.lnext(rightTri)
                leftTri.sym(farLeftTri)
                if (state.randomize(SAMPLE_RATE) == 0) {
                    flipTri.sym()
                    leftVertex = flipTri.dest()!!
                    midVertex = flipTri.apex()!!
                    rightVertex = flipTri.org()!!
                    splayRoot = circleTopInsert(state, splayRoot, leftTri, leftVertex, midVertex, rightVertex, nextEvent.yKey, noExact)
                }
            } else {
                nextVertex = nextEvent.vertexEvent!!
                if (nextVertex.x == lastVertex.x && nextVertex.y == lastVertex.y) {
                    LOG.debug { "A duplicate vertex appeared and was ignored (ID ${nextVertex.id})." }
                    nextVertex.type = VertexType.UNDEAD_VERTEX
                    mesh.undeads++
                    checkForEvents = false
                } else {
                    lastVertex = nextVertex
                    val booleanReference = Reference(false)
                    splayRoot = frontLocate(state, splayRoot, bottommost, nextVertex, searchTri, booleanReference)
                    farRightFlag = booleanReference.value
                    heapSize = check4DeadEvent(searchTri, eventHeap, heapSize)
                    searchTri.copy(farRightTri)
                    searchTri.sym(farLeftTri)
                    mesh.makeTriangle(leftTri)
                    mesh.makeTriangle(rightTri)
                    connectVertex = farRightTri.dest()!!
                    leftTri.setOrg(connectVertex)
                    leftTri.setDest(nextVertex)
                    rightTri.setOrg(nextVertex)
                    rightTri.setDest(connectVertex)
                    leftTri.bond(rightTri)
                    leftTri.lnext()
                    rightTri.lprev()
                    leftTri.bond(rightTri)
                    leftTri.lnext()
                    rightTri.lprev()
                    leftTri.bond(farLeftTri)
                    rightTri.bond(farRightTri)
                    if (!farRightFlag && farRightTri.equals(bottommost)) {
                        leftTri.copy(bottommost)
                    }
                    if (state.randomize(SAMPLE_RATE) == 0) {
                        splayRoot = splayInsert(state, splayRoot, leftTri, nextVertex)
                    } else if (state.randomize(SAMPLE_RATE) == 0) {
                        rightTri.lnext(insertTri)
                        splayRoot = splayInsert(state, splayRoot, insertTri, nextVertex)
                    }
                }
            }
            if (checkForEvents) {
                leftVertex = farLeftTri.apex()!!
                midVertex = leftTri.dest()!!
                rightVertex = leftTri.apex()!!
                leftTest = predicates.counterClockwise(leftVertex, midVertex, rightVertex, noExact)
                if (leftTest > 0.0) {
                    newEvent = SweepEvent()
                    newEvent.xKey = xMinExtreme
                    newEvent.yKey = circleTop(leftVertex, midVertex, rightVertex, leftTest)
                    leftTri.copy(newEvent.oTriEvent)
                    heapInsert(eventHeap, heapSize, newEvent)
                    heapSize++
                    leftTri.setOrg(SweepEventVertex(newEvent))
                }
                leftVertex = rightTri.apex()!!
                midVertex = rightTri.org()!!
                rightVertex = farRightTri.apex()!!
                rightTest = predicates.counterClockwise(leftVertex, midVertex, rightVertex, noExact)
                if (rightTest > 0.0) {
                    newEvent = SweepEvent()
                    newEvent.xKey = xMinExtreme
                    newEvent.yKey = circleTop(leftVertex, midVertex, rightVertex, rightTest)
                    farRightTri.copy(newEvent.oTriEvent)
                    heapInsert(eventHeap, heapSize, newEvent)
                    heapSize++
                    farRightTri.setOrg(SweepEventVertex(newEvent))
                }
            }
        }
        splayNodes.clear()
        bottommost.lprev()
        mesh.hullsize = removeGhosts(state, bottommost)
        return mesh
    }

    private fun heapInsert(heap: Array<SweepEvent>, heapSize: Int, newEvent: SweepEvent) {
        var eventNum: Int
        var parent: Int
        var notDone: Boolean
        val eventX = newEvent.xKey
        val eventY = newEvent.yKey
        eventNum = heapSize
        notDone = eventNum > 0
        while (notDone) {
            parent = eventNum - 1 shr 1
            if (heap[parent].yKey < eventY || heap[parent].yKey == eventY && heap[parent].xKey <= eventX) {
                notDone = false
            } else {
                heap[eventNum] = heap[parent]
                heap[eventNum].heapPosition = eventNum
                eventNum = parent
                notDone = eventNum > 0
            }
        }
        heap[eventNum] = newEvent
        newEvent.heapPosition = eventNum
    }

    private fun heapify(heap: Array<SweepEvent>, heapSize: Int, eventNum: Int) {
        var newEventNum = eventNum
        val thisEvent: SweepEvent
        val eventX: Double
        val eventY: Double
        var leftChild: Int
        var rightChild: Int
        var smallest: Int
        var notDone: Boolean
        thisEvent = heap[newEventNum]
        eventX = thisEvent.xKey
        eventY = thisEvent.yKey
        leftChild = 2 * newEventNum + 1
        notDone = leftChild < heapSize
        while (notDone) {
            if (heap[leftChild].yKey < eventY || heap[leftChild].yKey == eventY && heap[leftChild].xKey < eventX) {
                smallest = leftChild
            } else {
                smallest = newEventNum
            }
            rightChild = leftChild + 1
            if (rightChild < heapSize) {
                if (heap[rightChild].yKey < heap[smallest].yKey || heap[rightChild].yKey == heap[smallest].yKey && heap[rightChild].xKey < heap[smallest].xKey) {
                    smallest = rightChild
                }
            }
            if (smallest == newEventNum) {
                notDone = false
            } else {
                heap[newEventNum] = heap[smallest]
                heap[newEventNum].heapPosition = newEventNum
                heap[smallest] = thisEvent
                thisEvent.heapPosition = smallest
                newEventNum = smallest
                leftChild = 2 * newEventNum + 1
                notDone = leftChild < heapSize
            }
        }
    }

    private fun heapDelete(heap: Array<SweepEvent>, heapSize: Int, eventNum: Int) {
        val eventX: Double
        val eventY: Double
        var parent: Int
        var notDone: Boolean
        val moveEvent = heap[heapSize - 1]
        var newEventNum = eventNum
        if (newEventNum > 0) {
            eventX = moveEvent.xKey
            eventY = moveEvent.yKey
            do {
                parent = newEventNum - 1 shr 1
                if (heap[parent].yKey < eventY || heap[parent].yKey == eventY && heap[parent].xKey <= eventX) {
                    notDone = false
                } else {
                    heap[newEventNum] = heap[parent]
                    heap[newEventNum].heapPosition = newEventNum
                    newEventNum = parent
                    notDone = newEventNum > 0
                }
            } while (notDone)
        }
        heap[newEventNum] = moveEvent
        moveEvent.heapPosition = newEventNum
        heapify(heap, heapSize - 1, newEventNum)
    }

    private fun createHeap(state: State, size: Int): Array<SweepEvent> {
        var thisVertex: Vertex
        var event: SweepEvent
        val maxEvents = 3 * size / 2
        val dummyEvent = SweepEvent()
        val newHeap = Array(maxEvents) { dummyEvent }
        for ((i, v) in state.mesh._vertices.values.withIndex()) {
            thisVertex = v
            event = SweepEvent()
            event.vertexEvent = thisVertex
            event.xKey = thisVertex.x
            event.yKey = thisVertex.y
            heapInsert(newHeap, i, event)
        }
        return newHeap
    }

    private fun splay(state: State, splayTree: SplayNode?, searchPoint: Point, searchTri: OTri): SplayNode? {
        var child: SplayNode?
        val grandchild: SplayNode?
        val leftTree: SplayNode?
        val rightTree: SplayNode?
        var leftRight: SplayNode
        var checkVertex: Vertex?
        val rightOfRoot: Boolean
        val rightOfChild: Boolean
        if (splayTree == null) {
            return null
        }
        checkVertex = splayTree.keyEdge.dest()
        if (checkVertex === splayTree.keyDest) {
            rightOfRoot = rightOfHyperbola(splayTree.keyEdge, searchPoint)
            if (rightOfRoot) {
                splayTree.keyEdge.copy(searchTri)
                child = splayTree.rightChild
            } else {
                child = splayTree.leftChild
            }
            if (child == null) {
                return splayTree
            }
            checkVertex = child.keyEdge.dest()
            if (checkVertex !== child.keyDest) {
                child = splay(state, child, searchPoint, searchTri)
                if (child == null) {
                    if (rightOfRoot) {
                        splayTree.rightChild = null
                    } else {
                        splayTree.leftChild = null
                    }
                    return splayTree
                }
            }
            rightOfChild = rightOfHyperbola(child.keyEdge, searchPoint)
            if (rightOfChild) {
                child.keyEdge.copy(searchTri)
                grandchild = splay(state, child.rightChild, searchPoint, searchTri)
                child.rightChild = grandchild
            } else {
                grandchild = splay(state, child.leftChild, searchPoint, searchTri)
                child.leftChild = grandchild
            }
            if (grandchild == null) {
                if (rightOfRoot) {
                    splayTree.rightChild = child.leftChild
                    child.leftChild = splayTree
                } else {
                    splayTree.leftChild = child.rightChild
                    child.rightChild = splayTree
                }
                return child
            }
            if (rightOfChild) {
                if (rightOfRoot) {
                    splayTree.rightChild = child.leftChild
                    child.leftChild = splayTree
                } else {
                    splayTree.leftChild = grandchild.rightChild
                    grandchild.rightChild = splayTree
                }
                child.rightChild = grandchild.leftChild
                grandchild.leftChild = child
            } else {
                if (rightOfRoot) {
                    splayTree.rightChild = grandchild.leftChild
                    grandchild.leftChild = splayTree
                } else {
                    splayTree.leftChild = child.rightChild
                    child.rightChild = splayTree
                }
                child.leftChild = grandchild.rightChild
                grandchild.rightChild = child
            }
            return grandchild
        } else {
            leftTree = splay(state, splayTree.leftChild, searchPoint, searchTri)
            rightTree = splay(state, splayTree.rightChild, searchPoint, searchTri)
            state.splayNodes.remove(splayTree)
            if (leftTree == null) {
                return rightTree
            } else if (rightTree == null) {
                return leftTree
            } else if (leftTree.rightChild == null) {
                leftTree.rightChild = rightTree.leftChild
                rightTree.leftChild = leftTree
                return rightTree
            } else if (rightTree.leftChild == null) {
                rightTree.leftChild = leftTree.rightChild
                leftTree.rightChild = rightTree
                return leftTree
            } else {
                leftRight = leftTree.rightChild!!
                while (leftRight.rightChild != null) {
                    leftRight = leftRight.rightChild!!
                }
                leftRight.rightChild = rightTree
                return leftTree
            }
        }
    }

    private fun splayInsert(state: State, splayRoot: SplayNode?, newKey: OTri, searchPoint: Point): SplayNode {
        val newSplayNode = SplayNode()
        state.splayNodes.add(newSplayNode)
        newKey.copy(newSplayNode.keyEdge)
        newSplayNode.keyDest = newKey.dest()
        if (splayRoot == null) {
            newSplayNode.leftChild = null
            newSplayNode.rightChild = null
        } else if (rightOfHyperbola(splayRoot.keyEdge, searchPoint)) {
            newSplayNode.leftChild = splayRoot
            newSplayNode.rightChild = splayRoot.rightChild
            splayRoot.rightChild = null
        } else {
            newSplayNode.leftChild = splayRoot.leftChild
            newSplayNode.rightChild = splayRoot
            splayRoot.leftChild = null
        }
        return newSplayNode
    }

    private fun frontLocate(state: State, splayRoot: SplayNode?, bottomMost: OTri, searchVertex: Vertex, searchTri: OTri, farRight: Reference<Boolean>): SplayNode? {
        var farRightFlag: Boolean
        bottomMost.copy(searchTri)
        val newSPlayRoot = splay(state, splayRoot, searchVertex, searchTri)
        farRightFlag = false
        while (!farRightFlag && rightOfHyperbola(searchTri, searchVertex)) {
            searchTri.onext()
            farRightFlag = searchTri.equals(bottomMost)
        }
        farRight.value = farRightFlag
        return newSPlayRoot
    }

    private fun circleTopInsert(state: State, splayRoot: SplayNode?, newKey: OTri, pa: Vertex, pb: Vertex, pc: Vertex, topY: Double, noExact: Boolean): SplayNode {
        val searchPoint = Point()
        val dummyTri = OTri()
        val ccAbc = state.predicates.counterClockwise(pa, pb, pc, noExact)
        val xac = pa.x - pc.x
        val yac = pa.y - pc.y
        val xbc = pb.x - pc.x
        val ybc = pb.y - pc.y
        val acLength2 = xac * xac + yac * yac
        val bcLength2 = xbc * xbc + ybc * ybc
        searchPoint.x = pc.x - (yac * bcLength2 - ybc * acLength2) / (2.0 * ccAbc)
        searchPoint.y = topY
        return splayInsert(state, splay(state, splayRoot, searchPoint, dummyTri), newKey, searchPoint)
    }

    private fun rightOfHyperbola(frontTri: OTri, newSite: Point): Boolean {
        Statistic.hyperbolaCount.andIncrement
        val leftVertex = frontTri.dest()!!
        val rightVertex = frontTri.apex()!!
        if (leftVertex.y < rightVertex.y || leftVertex.y == rightVertex.y && leftVertex.x < rightVertex.x) {
            if (newSite.x >= rightVertex.x) {
                return true
            }
        } else if (newSite.x <= leftVertex.x) {
            return false
        }
        val dxa = leftVertex.x - newSite.x
        val dya = leftVertex.y - newSite.y
        val dxb = rightVertex.x - newSite.x
        val dyb = rightVertex.y - newSite.y
        return dya * (dxb * dxb + dyb * dyb) > dyb * (dxa * dxa + dya * dya)
    }

    private fun circleTop(pa: Vertex, pb: Vertex, pc: Vertex, ccAbc: Double): Double {
        Statistic.circleTopCount.andIncrement
        val xac = pa.x - pc.x
        val yac = pa.y - pc.y
        val xbc = pb.x - pc.x
        val ybc = pb.y - pc.y
        val xab = pa.x - pb.x
        val yab = pa.y - pb.y
        val acLength2 = xac * xac + yac * yac
        val bcLength2 = xbc * xbc + ybc * ybc
        val abLength2 = xab * xab + yab * yab
        return pc.y + (xac * bcLength2 - xbc * acLength2 + Math.sqrt(acLength2 * bcLength2 * abLength2)) / (2.0 * ccAbc)
    }

    private fun check4DeadEvent(checkTri: OTri, eventHeap: Array<SweepEvent>, inHeapSize: Int): Int {
        var outHeapSize = inHeapSize
        val eventVertex = if (checkTri.org() is SweepEventVertex) checkTri.org() as SweepEventVertex else null
        if (eventVertex != null) {
            heapDelete(eventHeap, outHeapSize, eventVertex.evt.heapPosition)
            outHeapSize -= 1
            checkTri.setOrg(null)
        }
        return outHeapSize
    }

    private fun removeGhosts(state: State, startGhost: OTri): Int {
        val searchEdge = OTri()
        val dissolveEdge = OTri()
        val deadTriangle = OTri()
        var markOrg: Vertex
        val noPoly = !state.mesh.behavior.isPlanarStraightLineGraph
        val dummyTri = state.mesh.dummytri
        startGhost.lprev(searchEdge)
        searchEdge.sym()
        dummyTri.neighbors[0] = searchEdge
        startGhost.copy(dissolveEdge)
        var hullSize = 0
        do {
            hullSize++
            dissolveEdge.lnext(deadTriangle)
            dissolveEdge.lprev()
            dissolveEdge.sym()
            if (noPoly) {
                if (dissolveEdge.triangle!!.id != Mesh.DUMMY) {
                    markOrg = dissolveEdge.org()!!
                    if (markOrg.label == 0) {
                        markOrg.label = 1
                    }
                }
            }
            dissolveEdge.dissolve(dummyTri)
            deadTriangle.sym(dissolveEdge)
            state.mesh.triangleDealloc(deadTriangle.triangle!!)
        } while (!dissolveEdge.equals(startGhost))
        return hullSize
    }
}
