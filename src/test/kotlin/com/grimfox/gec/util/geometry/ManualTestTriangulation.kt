package com.grimfox.gec.util.geometry

import com.google.common.util.concurrent.AtomicDouble
import com.grimfox.gec.model.HistoryQueue
import com.grimfox.gec.model.geometry.Point2F
import com.grimfox.gec.util.Graphs
import com.grimfox.gec.util.Triangulate
import com.grimfox.gec.util.Utils
import java.util.*

object ManualTestTriangulation {

    val accumulatedTime = AtomicDouble(0.0)

    private inline fun <T> timeIt(doBlock: () -> T): T {
        val time = System.nanoTime()
        val ret = doBlock()
        val nextTime = System.nanoTime()
        accumulatedTime.addAndGet((nextTime - time) / 1000000.0)
        return ret
    }

    @JvmStatic fun main(vararg args: String) {
        testTriangulation()
        testGraphs()
    }

    private fun testTriangulation() {
        for (i in 1..20000) {
            val random = Random(i.toLong())
            val graph = timeIt {
                val randomPoints = ArrayList<Point2F>(49)
                Utils.generateSemiUniformPointsF(7, 1.0f, random, 0.8f) { i, x, y -> randomPoints.add(Point2F(x, y)) }
                Triangulate.buildGraph(1.0f, randomPoints, 7)
            }
        }
        println("warm-up average: ${accumulatedTime.get() / 20000.0}")
        accumulatedTime.set(0.0)
        for (i in 20001..40000) {
            val random = Random(i.toLong())
            val graph = timeIt {
                val randomPoints = ArrayList<Point2F>(49)
                Utils.generateSemiUniformPointsF(7, 1.0f, random, 0.8f) { i, x, y -> randomPoints.add(Point2F(x, y)) }
                Triangulate.buildGraph(1.0f, randomPoints, 7)
            }
        }
        println("hot average: ${accumulatedTime.get() / 20000.0}")
        accumulatedTime.set(0.0)
    }

    private fun testGraphs() {
        for (i in 1..20000) {
            val random = Random(i.toLong())
            val graph = timeIt {
                Graphs.generateGraph(7, random, 0.8)
            }
        }
        println("warm-up average: ${accumulatedTime.get() / 20000.0}")
        accumulatedTime.set(0.0)
        for (i in 20001..40000) {
            val random = Random(i.toLong())
            val graph = timeIt {
                Graphs.generateGraph(7, random, 0.8)
            }
        }
        println("hot average: ${accumulatedTime.get() / 20000.0}")
        accumulatedTime.set(0.0)
    }
}