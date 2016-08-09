package com.grimfox.gec.filter

import com.grimfox.gec.model.ClosestPoints
import com.grimfox.gec.model.RawMatrixData
import com.grimfox.gec.model.RawMatrixData.Format
import com.grimfox.gec.util.Utils.exp2FromSize
import java.io.File

interface ClosestPointsDataFilter<T> : Runnable {

    val heightFunction: (ClosestPoints) -> T

    val format: Format<T>

    var inputFile: File

    var outputFile: File

    override fun run() {
        RawMatrixData.openAndUse<ClosestPoints>(inputFile) { closestPoints ->
            RawMatrixData.createAndUse(outputFile, exp2FromSize(closestPoints.rasterWidth), format) { heightMap ->
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
