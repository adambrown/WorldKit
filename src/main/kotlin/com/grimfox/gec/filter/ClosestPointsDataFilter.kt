package com.grimfox.gec.filter

import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.DataFiles
import com.grimfox.gec.model.Matrix
import java.io.File

interface ClosestPointsDataFilter<T, M : Matrix<T>> : Runnable {

    val heightFunction: (ClosestPoints) -> T

    val format: Class<M>

    var inputFile: File

    var outputFile: File

    override fun run() {
        DataFiles.openAndUse<ClosestPoints>(inputFile) { closestPoints ->
            DataFiles.createAndUse(outputFile, closestPoints.exponent, format) { heightMap ->
                val end = heightMap.width - 1
                for (y in 0..end) {
                    for (x in 0..end) {
                        heightMap[x, y] = heightFunction(closestPoints[x, y])
                    }
                }
            }
        }
    }
}
