package wk.api

import java.io.File
import java.nio.ByteBuffer
import java.text.DecimalFormat
import kotlin.math.ceil

typealias PolyLine = ArrayList<Point3F>

fun TerraformResult.toRiverLines(mapScale: MapScale) =
        buildRivers(nodeData.nodeIndex, nodeData.landIndex, graphId, mapScale.mapSizeMeters)

private fun buildRivers(nodeIndex: ByteBuffer, landIndex: BitMatrix<*>, flowGraphId: Int, distanceScale: Float): List<PolyLine> {
    val graph = getFlowGraph(flowGraphId)
    val nodePtr = TerraformNode(nodeIndex)
    var numFlows = 0
    var sumFlows = 0.0
    landIndex.forEachSetBit {
        nodePtr.id = it
        sumFlows += nodePtr.drainageArea
        numFlows++
    }
    val avgFlow = (sumFlows / numFlows).toFloat()
    numFlows = 0
    sumFlows = 0.0
    landIndex.forEachSetBit {
        nodePtr.id = it
        if (nodePtr.drainageArea > avgFlow) {
            sumFlows += nodePtr.drainageArea
            numFlows++
        }
    }
    val flowThreshold = (sumFlows / numFlows).toFloat() * 0.1f
    var numRiversWithLength = 0
    var sumRiverLengths = 0.0
    val rootsToConsider = ArrayList<Int>()
    landIndex.forEachSetBit {
        nodePtr.id = it
        if (nodePtr.parentCode == GraphLite.SELF_OFFSET) {
            iterateMaxUpstreamLengths(graph, nodeIndex, distanceScale, nodePtr.id, flowThreshold)
            if (nodePtr.maxUpstreamLength > 0.0f) {
                numRiversWithLength++
                sumRiverLengths += nodePtr.maxUpstreamLength
                rootsToConsider.add(nodePtr.id)
            }
        }
    }
    val avgUpstreamLength = (sumRiverLengths / numRiversWithLength).toFloat()
    numRiversWithLength = 0
    sumRiverLengths = 0.0
    rootsToConsider.forEach {
        nodePtr.id = it
        if (nodePtr.maxUpstreamLength > avgUpstreamLength) {
            sumRiverLengths += nodePtr.maxUpstreamLength
            numRiversWithLength++
        }
    }
    val riverLengthThreshold = (sumRiverLengths / numRiversWithLength).toFloat() * 0.4f
    val riverPolyLines = ArrayList<PolyLine>()
    rootsToConsider.forEach {
        nodePtr.id = it
        if (nodePtr.maxUpstreamLength > riverLengthThreshold) {
            iterateRiverLines(
                    startNodeId = nodePtr.id,
                    flowThreshold = flowThreshold,
                    leafThreshold = riverLengthThreshold * 0.3f,
                    graph = graph,
                    nodeIndex = nodeIndex,
                    riverPolyLines = riverPolyLines)
        }
    }
    return riverPolyLines
}

private fun iterateMaxUpstreamLengths(graph: GraphLite, nodeIndex: ByteBuffer, distanceScale: Float, startNodeId: Int, flowThreshold: Float) {
    val order = ArrayList<Int>()
    var buffer = arrayListOf(startNodeId)
    var children = ArrayList<Int>()
    val nodePtr = TerraformNode(nodeIndex)
    val childPtr = TerraformNode(nodeIndex)
    while (buffer.isNotEmpty()) {
        for (nodeId in buffer) {
            order.add(nodeId)
            nodePtr.id = nodeId
            nodePtr.forEachChild(graph, childPtr) { child ->
                if (child.drainageArea > flowThreshold) {
                    children.add(child.id)
                } else {
                    child.maxUpstreamLength = 0.0f
                }
            }
        }
        val tmp = buffer
        buffer = children
        tmp.clear()
        children = tmp
    }
    val retVal = T2(0, 0.0f)
    for (orderId in order.indices.reversed()) {
        nodePtr.id = order[orderId]
        val (_, distanceToParent) = graph.getIdAndDistanceFromOffset(nodePtr.id, nodePtr.parentCode, retVal)
        var maxChildLength = 0.0f
        nodePtr.forEachChild(graph, childPtr) { child ->
            if (child.maxUpstreamLength > maxChildLength) {
                maxChildLength = child.maxUpstreamLength
            }
        }
        nodePtr.maxUpstreamLength = distanceToParent * distanceScale + maxChildLength
    }
}

private fun iterateRiverLines(startNodeId: Int, flowThreshold: Float, leafThreshold: Float, graph: GraphLite, nodeIndex: ByteBuffer, riverPolyLines: MutableList<PolyLine>) {
    var buffer = arrayListOf(startNodeId to ArrayList<Point3F>())
    var children = ArrayList<Pair<Int, ArrayList<Point3F>>>()
    val nodePtr = TerraformNode(nodeIndex)
    val childPtr = TerraformNode(nodeIndex)
    while (buffer.isNotEmpty()) {
        for ((nodeId, currentPolyLine) in buffer) {
            nodePtr.id = nodeId
            if (nodePtr.parentCode == GraphLite.SELF_OFFSET || nodePtr.drainageArea > flowThreshold) {
                val point = graph.getPoint2F(nodePtr.id)
                currentPolyLine.add(point3(point.x, point.y, nodePtr.height))
            }
            val childrenWithUpstreamLength = ArrayList<Int>()
            nodePtr.forEachChild(graph, childPtr) { child ->
                if (child.maxUpstreamLength > 0.0f) {
                    childrenWithUpstreamLength.add(child.id)
                }
            }
            childrenWithUpstreamLength.sortByDescending { nodePtr.id = it; nodePtr.maxUpstreamLength }
            if (childrenWithUpstreamLength.size == 1) {
                children.add(childrenWithUpstreamLength.first() to currentPolyLine)
            } else if (childrenWithUpstreamLength.size > 1) {
                if (currentPolyLine.size > 1) {
                    riverPolyLines.add(currentPolyLine)
                }
                val secondLongestUpstream = childrenWithUpstreamLength[1]
                nodePtr.id = secondLongestUpstream
                if (nodePtr.maxUpstreamLength < leafThreshold) {
                    children.add(childrenWithUpstreamLength.first() to currentPolyLine)
                } else {
                    for (childId in childrenWithUpstreamLength) {
                        nodePtr.id = childId
                        if (nodePtr.maxUpstreamLength >= leafThreshold) {
                            children.add(nodePtr.id to arrayListOf(currentPolyLine.last()))
                        } else {
                            break
                        }
                    }
                }
            } else {
                if (currentPolyLine.size > 1) {
                    riverPolyLines.add(currentPolyLine)
                }
            }

        }
        val tmp = buffer
        buffer = children
        tmp.clear()
        children = tmp
    }
}

private fun writePolyLineObj(polyLines: List<List<Point3F>>, outputFile: File, scaleFactor: Float = 1.0f) {
    val format = DecimalFormat("0.####")
    outputFile.outputStream().bufferedWriter().use { writer ->
        polyLines.forEach { polyLine ->
            polyLine.forEach { point ->
                writer.write("v ${format.format((-point.y + 0.5f) * scaleFactor)} ${format.format(point.z)} ${format.format((point.x - 0.5f) * scaleFactor)}\n")
            }
        }
        var vertexId = 1
        polyLines.forEach { polyLine ->
            writer.write("l")
            repeat(polyLine.size) {
                writer.write(" ")
                writer.write(vertexId.toString())
                vertexId++
            }
            writer.write("\n")
        }
    }
}

fun List<PolyLine>.writePolyLines(fileName: String, outputScale: Float) {
    val file = File(fileName)
    if ((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) {
        writePolyLineObj(this, file, outputScale)
    }
}

fun buildOpenEdges(points: List<Point2F>, segmentSize: Float, smoothFactor: Int): List<Point2F> {
    return buildEdges(getCurvePoints(points, false, segmentSize, smoothFactor), isClosed = false, moveToFirst = true)
}

private fun buildEdges(inPoints: List<Point2F>, isClosed: Boolean, moveToFirst: Boolean): MutableList<Point2F> {
    val outPoints = ArrayList<Point2F>()
    val start = inPoints.first()
    if (moveToFirst) {
        outPoints.add(start)
    }
    for (i in if (isClosed) 1..inPoints.size else 1 until inPoints.size) {
        val id = i % inPoints.size
        val lastId = i - 1
        val lastPoint = inPoints[lastId]
        val point = inPoints[id]
        if (i == 1 && moveToFirst) {
            outPoints[0] = lastPoint
        }
        outPoints.add(point)
    }
    return outPoints
}

private fun getCurvePoints(points: List<Point2F>, isClosed: Boolean, segmentSize: Float, smoothFactor: Int): List<Point2F> {

    val newPoints = ArrayList<Point2F>()
    val copyPoints = ArrayList(points)

    val firstPoint = copyPoints.first()
    if (isClosed) {
        copyPoints.add(firstPoint)
    }

    newPoints.add(point2(firstPoint.x, (1.0f - firstPoint.y)))
    for (i in 1 until copyPoints.size) {

        val lastPoint = copyPoints[i - 1]
        val thisPoint = copyPoints[i]

        val vector = thisPoint - lastPoint
        val length = vector.length
        if (length > segmentSize) {
            val segments = ceil(length / segmentSize.toDouble()).toInt()
            val offset = vector / segments.toFloat()
            (1 until segments)
                    .map { lastPoint + (offset * it.toFloat()) }
                    .mapTo(newPoints) { point2(it.x, (1.0f - it.y)) }
        }
        newPoints.add(point2(thisPoint.x, (1.0f - thisPoint.y)))
    }

    val newPoints2 = newPoints.mapTo(ArrayList(newPoints.size)) { point2(it.x, it.y) }
    var output: MutableList<Point2F> = newPoints2
    var input: MutableList<Point2F>
    var size = newPoints.size

    if (size > 3) {
        (1..smoothFactor).forEach { iteration ->
            input = if (iteration % 2 == 0) {
                output = newPoints
                newPoints2
            } else {
                output = newPoints2
                newPoints
            }
            if (iteration % 5 == 0) {
                if (size > 3) {
                    for (i in if (isClosed) size - 2 downTo 0 step 2 else size - 3 downTo 1 step 2) {
                        input.removeAt(i)
                        output.removeAt(i)
                    }
                    size = input.size
                }
            }
            for (i in if (isClosed) 1..size else 1..size - 2) {
                val initialPosition = input[i % size]
                var affectingPoint = input[i - 1]
                var x = affectingPoint.x
                var y = affectingPoint.y
                affectingPoint = input[(i + 1) % size]
                x += affectingPoint.x
                y += affectingPoint.y
                x *= 0.325f
                y *= 0.325f
                x += initialPosition.x * 0.35f
                y += initialPosition.y * 0.35f
                val nextPosition = output[i % size]
                nextPosition.x = x
                nextPosition.y = y
            }
        }
    }
    return output.map { point2(it.x, 1.0f - it.y) }
}
