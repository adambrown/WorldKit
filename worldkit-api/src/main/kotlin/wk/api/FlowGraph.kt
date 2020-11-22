package wk.api

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import wk.internal.application.cacheDir
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

@PublicApi
data class FlowGraphSpec(val width: Int, val graphDeferred: Deferred<GraphLite>) {
    val graph by lazy { runBlocking { graphDeferred.await() } }
}

private fun altGraph(width: Int) = FlowGraphSpec(width, GlobalScope.async {
    val file = File(cacheDir, "$width.graph")
    if (file.isFile) {
        DataInputStream(file.inputStream().buffered()).use {
            GraphLite.deserializeFrom(it)
        }
    } else {
        val graph = GraphLite.from(width * 31L, width)
        DataOutputStream(file.outputStream().buffered()).use {
            graph.serializeTo(it)
        }
        graph
    }
})

private val flowGraphs = arrayOf(
        altGraph(256),
        altGraph(512),
        altGraph(1024),
        altGraph(2048),
        altGraph( 4096),
        altGraph(8192))

@PublicApi
fun getFlowGraphIndices() = flowGraphs.indices

@PublicApi
fun isValidFlowGraphIndex(index: Int) = index in flowGraphs.indices

@PublicApi
fun getFlowGraphSpec(index: Int): FlowGraphSpec {
    if (isValidFlowGraphIndex(index)) {
        return flowGraphs[index]
    } else {
        throw IndexOutOfBoundsException("Invalid graph index: $index. Must be in range ${flowGraphs.indices.first} to ${flowGraphs.indices.last}")
    }
}

@PublicApi
fun getFlowGraph(index: Int) = getFlowGraphSpec(index).graph

@PublicApi
fun getFlowGraphAsync(index: Int) = getFlowGraphSpec(index).graphDeferred

@PublicApi
fun getFlowGraphWidth(index: Int) = getFlowGraphSpec(index).width