package wk.utils

import wk.utils.RcMatrix
import wk.utils.RcMatrix.Companion.ALL
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.*

object TerrainDictionaryBuilder {

    @JvmStatic
    fun main(vararg args: String) {
        if (args[0].equals("low", true)) {
            buildLow(File(args[1]), File(args[2]), File(args[3]), File(args[4]), File(args[5]), args[6].toInt(), args[7].toInt())
        } else if (args[0].equals("high", true)) {
            buildHigh(File(args[1]), File(args[2]), File(args[3]), File(args[4]), File(args[5]), args[6].toInt(), args[7].toInt())
        }
    }

    private fun buildLow(exampleTerrainFile: File, exampleMaskFile: File, optimizationTerrainFile: File, optimizationTerrainMaskFile: File, outputFile: File, offsetAnalysis: Int, offsetOptimization: Int) {
        val random = Random(0)
        val maskSize = 8
        val example4 = TerrainAmplification.imageToMatrix(ImageIO.read(exampleTerrainFile))
        val example2 = TerrainAmplification.downscaleImage(example4, 2)
        val exampleLow = TerrainAmplification.downscaleImage(example4, 4)
        val exampleMaskLow = TerrainAmplification.downscaleImage(TerrainAmplification.imageToMatrix(ImageIO.read(exampleMaskFile)), 4)

        val optimization = TerrainAmplification.imageToMatrix(ImageIO.read(optimizationTerrainFile))
        val optimizationMask = TerrainAmplification.imageToMatrix(ImageIO.read(optimizationTerrainMaskFile))

        val mask = TerrainAmplification.buildMask(maskSize)
        val mask2 = TerrainAmplification.buildMask(maskSize * 2)
        val mask4 = TerrainAmplification.buildMask(maskSize * 4)
        val offsetAnalysis2 = offsetAnalysis * 2
        val offsetAnalysis4 = offsetAnalysis * 4
        val dictionaryHeight = (exampleLow.rows - maskSize) / offsetAnalysis
        val dictionaryWidth = (exampleLow.columns - maskSize) / offsetAnalysis
        val usefulIndices = TerrainAmplification.buildMaskUsefulIndices(mask)

        val dictionaries = time("dictionary") {
            val (dictionaryLow, atomsLow, keeps) = buildDictionary(mask, offsetAnalysis, dictionaryHeight, dictionaryWidth, exampleLow, exampleMaskLow)
            val (dictionary2) = buildDictionary(mask2, offsetAnalysis2, dictionaryHeight, dictionaryWidth, example2, null, atomsLow, keeps)
            val (dictionary4) = buildDictionary(mask4, offsetAnalysis4, dictionaryHeight, dictionaryWidth, example4, null, atomsLow, keeps)
            val optimizationDictionary = buildOptimizationSet(maskSize, mask, offsetOptimization, optimization, optimizationMask)
            val dictionaryLowArrays = dictionaryLow.allColumns.map { dictionaryLow[ALL, it].array }
            val optimizationDictionaryArrays = optimizationDictionary.allColumns.map { optimizationDictionary[ALL, it].array }

            println("base dictionary size: ${dictionaryLow.columns}")
            println("optimization dictionary size: ${optimizationDictionary.columns}")

            val optimized1 = time("optimize dictionary 1") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, randomSelect(random, dictionaryLow, 4096)) }
            val optimized2 = time("optimize dictionary 2") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized1, 2048)) }
            val optimized3 = time("optimize dictionary 3") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized2, 1024)) }
//            val optimized4 = time("optimize dictionary 4") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized3, 512)) }
//            val optimized5 = time("optimize dictionary 5") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized4, 256)) }
//            val optimized6 = time("optimize dictionary 6") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized5, 128)) }
            val newDictionaries = dictionariesFromSelection(optimized3, dictionaryLow, dictionary2, dictionary4)
            for (i in 1 until newDictionaries.size) {
                newDictionaries[i] = newDictionaries[i].asyncTranspose()
            }
            newDictionaries
        }

        writeDictionary(dictionaries[2].asyncTranspose(), outputFile.parentFile, "visual-dictionary.png")

        TerrainAmplificationDictionary(maskSize, maskSize / 2, dictionaries[0], dictionaries[1], dictionaries[2]).write(outputFile)
    }

    private fun buildHigh(exampleTerrainFile: File, exampleMaskFile: File, optimizationTerrainFile: File, optimizationTerrainMaskFile: File, outputFile: File, offsetAnalysis: Int, offsetOptimization: Int) {
        val random = Random(0)
        val maskSize = 16
        val example8 = TerrainAmplification.imageToMatrix(ImageIO.read(exampleTerrainFile))
        val example4 = TerrainAmplification.downscaleImage(example8, 2)
        val example2 = TerrainAmplification.downscaleImage(example8, 4)
        val exampleLow = TerrainAmplification.downscaleImage(example8, 8)
        val exampleMaskLow = TerrainAmplification.downscaleImage(TerrainAmplification.imageToMatrix(ImageIO.read(exampleMaskFile)), 8)

        val optimization = TerrainAmplification.imageToMatrix(ImageIO.read(optimizationTerrainFile))
        val optimizationMask = TerrainAmplification.imageToMatrix(ImageIO.read(optimizationTerrainMaskFile))

        val mask = TerrainAmplification.buildMask(maskSize)
        val mask2 = TerrainAmplification.buildMask(maskSize * 2)
        val mask4 = TerrainAmplification.buildMask(maskSize * 4)
        val mask8 = TerrainAmplification.buildMask(maskSize * 8)
        val offsetAnalysis2 = offsetAnalysis * 2
        val offsetAnalysis4 = offsetAnalysis * 4
        val offsetAnalysis8 = offsetAnalysis * 8
        val dictionaryHeight = (exampleLow.rows - maskSize) / offsetAnalysis
        val dictionaryWidth = (exampleLow.columns - maskSize) / offsetAnalysis
        val usefulIndices = TerrainAmplification.buildMaskUsefulIndices(mask)

        val dictionaries = time("dictionary") {
            val (dictionaryLow, atomsLow, keeps) = buildDictionary(mask, offsetAnalysis, dictionaryHeight, dictionaryWidth, exampleLow, exampleMaskLow)
            val (dictionary2) = buildDictionary(mask2, offsetAnalysis2, dictionaryHeight, dictionaryWidth, example2, null, atomsLow, keeps)
            val (dictionary4) = buildDictionary(mask4, offsetAnalysis4, dictionaryHeight, dictionaryWidth, example4, null, atomsLow, keeps)
            val (dictionary8) = buildDictionary(mask8, offsetAnalysis8, dictionaryHeight, dictionaryWidth, example8, null, atomsLow, keeps)
            val optimizationDictionary = buildOptimizationSet(maskSize, mask, offsetOptimization, optimization, optimizationMask)
            val dictionaryLowArrays = dictionaryLow.allColumns.map { dictionaryLow[ALL, it].array }
            val optimizationDictionaryArrays = optimizationDictionary.allColumns.map { optimizationDictionary[ALL, it].array }

            println("base dictionary size: ${dictionaryLow.columns}")
            println("optimization dictionary size: ${optimizationDictionary.columns}")

            val optimized1 = time("optimize dictionary 1") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, randomSelect(random, dictionaryLow, 4096)) }
            val optimized2 = time("optimize dictionary 2") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized1, 2048)) }
//            val optimized3 = time("optimize dictionary 3") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized2, 1024)) }
//            val optimized4 = time("optimize dictionary 4") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized3, 512)) }
//            val optimized5 = time("optimize dictionary 5") { optimizeDictionary(random, dictionaryLow, dictionaryLowArrays, optimizationDictionary, optimizationDictionaryArrays, usefulIndices, 50, stronglyUniqueSelect(random, dictionaryLow, optimized4, 256)) }
            val newDictionaries = dictionariesFromSelection(optimized2, dictionaryLow, dictionary2, dictionary4, dictionary8)
            for (i in 1 until newDictionaries.size) {
                newDictionaries[i] = newDictionaries[i].asyncTranspose()
            }
            newDictionaries
        }

        writeDictionary(dictionaries[3].asyncTranspose(), outputFile.parentFile, "visual-dictionary.png")

        TerrainAmplificationDictionary(maskSize, 6, dictionaries[0], dictionaries[1], dictionaries[2], dictionaries[3]).write(outputFile)
    }

    private fun optimizeDictionary(random: Random, dictionaryLow: RcMatrix, dictionaryLowArrays: List<FloatArray>, optimizationDictionary: RcMatrix, optimizationDictionaryArrays: List<FloatArray>, usefulIndices: IntArray, maxIterations: Int, indices: IntArray): IntArray {
        if (indices.size >= dictionaryLow.columns) {
            return indices
        }
        val chunkedColumnIndices = dictionaryLow.allColumns.toList().chunked(max(1, dictionaryLow.columns / 16))
        var cache = HashMap<Int, Set<Int>>()
        var nextCache = HashMap<Int, Set<Int>>()
        for (iteration in 0 until maxIterations) {
            val temp = cache
            cache = nextCache
            nextCache = temp
            nextCache.clear()
            val coefficients = TerrainAmplification.matching(dictionaryFromSelection(dictionaryLow, indices), optimizationDictionary, usefulIndices)
            val takenIndices = HashSet<Int>(indices.size)
            val unusedDIndices = ArrayList<Int>()
            var hasChanges = false
            coefficients.reverseIndex.forEachIndexed { dIndex, xIndices ->
                if (xIndices.isEmpty()) {
                    unusedDIndices.add(dIndex)
                } else {
                    val key = indices[dIndex]
                    val check = cache[key]
                    if (check != null && !takenIndices.contains(key) && check == HashSet<Int>(xIndices)) {
                        nextCache[key] = check
                        takenIndices.add(key)
                    } else {
                        val xAtoms = xIndices.map { xIndex ->
                            optimizationDictionaryArrays[xIndex]
                        }
                        var bestIndex = indices[dIndex]
                        var bestPsnr = -Float.MAX_VALUE
                        chunkedColumnIndices.parallelStream().map { chunk ->
                            var localBestIndex = indices[dIndex]
                            var localBestPsnr = -Float.MAX_VALUE
                            chunk.forEach { newDIndex ->
                                if (!takenIndices.contains(newDIndex)) {
                                    val currentPsnr = psnr(dictionaryLowArrays[newDIndex], xAtoms)
                                    if (currentPsnr > localBestPsnr) {
                                        localBestPsnr = currentPsnr
                                        localBestIndex = newDIndex
                                    }
                                }
                            }
                            localBestIndex to localBestPsnr
                        }.forEach { (currentIndex, currentPsnr) ->
                            if (currentPsnr > bestPsnr) {
                                bestPsnr = currentPsnr
                                bestIndex = currentIndex
                            }
                        }
                        if (indices[dIndex] != bestIndex) {
                            indices[dIndex] = bestIndex
                            hasChanges = true
                        } else {
                            nextCache[bestIndex] = HashSet<Int>(xIndices)
                        }
                        takenIndices.add(bestIndex)
                    }
                }
            }
            unusedDIndices.forEach { dIndex ->
                takenIndices.add(indices[dIndex])
                while (true) {
                    val potential = random.nextInt(dictionaryLow.columns)
                    if (!takenIndices.contains(potential)) {
                        indices[dIndex] = potential
                        hasChanges = true
                        break
                    }
                }
            }
            if (!hasChanges) {
                println("dictionary optimized naturally")
                break
            }
        }
        return indices
    }

    private fun buildOptimizationSet(maskSize: Int, mask: RcMatrix, offset: Int, optimization: RcMatrix, optimizationMask: RcMatrix): RcMatrix {
        val dictionaryHeight = (optimization.rows - maskSize) / offset
        val dictionaryWidth = (optimization.columns - maskSize) / offset
        val (dictionary) = buildDictionary(mask, offset, dictionaryHeight, dictionaryWidth, optimization, optimizationMask)
        return dictionary
    }

    private fun buildDictionary(mask: RcMatrix, offset: Int, dictionaryHeight: Int, dictionaryWidth: Int, exampleTerrain: RcMatrix, exampleTerrainMask: RcMatrix? = null, normDictionary: FloatArray? = null, keepIndex: BooleanArray? = null): Triple<RcMatrix, FloatArray, BooleanArray> {
        val maskSize = mask.columns
        val maskSize2 = maskSize * maskSize
        val maskMeans = TerrainAmplification.buildMeans(maskSize, offset, dictionaryHeight, dictionaryWidth, exampleTerrain)
        val (baseAtoms, newKeepsIndex) = buildAtomsWithVariants(mask, offset, dictionaryHeight, dictionaryWidth, maskMeans, exampleTerrain, exampleTerrainMask, keepIndex)
        val norms = normDictionary ?: baseAtoms.map { it.norm() }.toFloatArray()
        val dictionary = RcMatrix(maskSize * maskSize, norms.count { it != 0.0f })
        var dictionaryIndex = 0
        baseAtoms.forEachIndexed { i, atom ->
            val norm = norms[i]
            if (norm != 0.0f) {
                dictionary[ALL, dictionaryIndex] = RcMatrix(maskSize2, 1, atom / norm)
                dictionaryIndex++
            }
        }
        return Triple(dictionary, norms, newKeepsIndex)
    }

    private fun buildAtomsWithVariants(mask: RcMatrix, offset: Int, dictionaryHeight: Int, dictionaryWidth: Int, means: RcMatrix, terrain: RcMatrix, terrainMask: RcMatrix? = null, keeps: BooleanArray? = null): Pair<List<FloatArray>, BooleanArray> {
        val maskSize = mask.columns
        val maskSize2 = maskSize * maskSize
        val baseAtoms = ArrayList<FloatArray>()
        val keepsArray = BooleanArray(dictionaryWidth * dictionaryHeight)
        (0 until dictionaryHeight).forEach { i ->
            val iRange = i * offset until i * offset + maskSize
            for (j in 0 until dictionaryWidth) {
                val mean = means[i, j]
                var t = 0
                var dropIt = false
                val array = FloatArray(maskSize2)
                if (keeps != null && !keeps[i * dictionaryWidth + j]) {
                    continue
                }
                for (col in j * offset until j * offset + maskSize) {
                    for (row in iRange) {
                        if (mask[t] != 0.0f) {
                            if (!dropIt && terrainMask != null && terrainMask[row, col] < 0.5f) {
                                dropIt = true
                            }
                            array[t] = (terrain[row, col] - mean) * mask[t]
                        }
                        t++
                    }
                }
                if (!dropIt) {
                    baseAtoms.add(array)
                }
                keepsArray[i * dictionaryWidth + j] = !dropIt
            }
        }
//        val dictionarySize1 = dictionaryWidth * dictionaryHeight
//        val dictionarySize2 = dictionarySize1 * 2
//        val dictionarySize3 = dictionarySize1 * 3
//        val dictionarySize4 = dictionarySize1 * 4
//        val dictionarySize5 = dictionarySize1 * 5
//        val dictionarySize6 = dictionarySize1 * 6
//        val dictionarySize7 = dictionarySize1 * 7
//        (0 until dictionarySize1).forEach {
//            val seed = baseAtoms[ALL, it].reshape(maskSize, maskSize)
//            val transposed = seed.transpose()
//            seed.rotate90Clockwise()
//            baseAtoms[ALL, it + dictionarySize1] = seed.reshape(baseAtoms.rows, 1)
//            seed.rotate90Clockwise()
//            baseAtoms[ALL, it + dictionarySize2] = seed.reshape(baseAtoms.rows, 1)
//            seed.rotate90Clockwise()
//            baseAtoms[ALL, it + dictionarySize3] = seed.reshape(baseAtoms.rows, 1)
//            baseAtoms[ALL, it + dictionarySize4] = transposed.reshape(baseAtoms.rows, 1)
//            transposed.rotate90Clockwise()
//            baseAtoms[ALL, it + dictionarySize5] = transposed.reshape(baseAtoms.rows, 1)
//            transposed.rotate90Clockwise()
//            baseAtoms[ALL, it + dictionarySize6] = transposed.reshape(baseAtoms.rows, 1)
//            transposed.rotate90Clockwise()
//            baseAtoms[ALL, it + dictionarySize7] = transposed.reshape(baseAtoms.rows, 1)
//
//        }
        return baseAtoms to keepsArray
    }

    private fun psnrMax(testIndex: Int, testAtom: FloatArray, atomsToTestAgainst: List<Pair<Int, FloatArray>>, cache: FloatArray): Float {
        var min = Float.MAX_VALUE
        atomsToTestAgainst.forEach { (otherIndex, otherAtom) ->
            val currentError = sumOfError(testAtom, otherAtom, testIndex, otherIndex, cache)
            if (currentError < min) {
                min = currentError
            }
        }
        return if (min < 0.0000001f) Float.MAX_VALUE else 1.0f / min
    }

    private fun psnr(dAtom: FloatArray, xAtoms: List<FloatArray>): Float {
        var sum = 0.0f
        xAtoms.forEach { xAtom ->
            sum += sumOfError2(dAtom, xAtom)
        }
        val mse = sum / (xAtoms.size * dAtom.size)
        return if (mse < 0.0000001f) Float.MAX_VALUE else 1.0f / mse
    }

    private fun sumOfError2(dAtom: FloatArray, xAtom: FloatArray): Float {
        var sum = 0.0f
        for (i in 0 until dAtom.size) {
            val delta = dAtom[i] - xAtom[i]
            sum += delta * delta
        }
        return sum
    }

    private fun sumOfError(dAtom: FloatArray, xAtom: FloatArray, dIndex: Int, otherIndex: Int, cache: FloatArray): Float {
        if (dIndex == otherIndex) {
            return 0.0f
        }
        val y = min(dIndex, otherIndex)
        val x = max(dIndex, otherIndex)
        val index = (x * (x - 1) / 2 + y)
        val cacheVal = cache[index]
        if (cacheVal > 0.0f) {
            return cacheVal
        }
        var sum = 0.0f
        for (i in 0 until dAtom.size) {
            sum += Math.abs(dAtom[i] - xAtom[i])
        }
        cache[index] = sum
        return sum
    }

    private fun stronglyUniqueSelect(random: Random, dictionary: RcMatrix, indices: IntArray, count: Int): IntArray {
        if (count > indices.size) {
            return indices
        }
        val cache = FloatArray((dictionary.columns * (dictionary.columns - 1)) / 2)
        val stronglyUniqueSet = HashSet<Int>(count)
        stronglyUniqueSet.add(indices[random.nextInt(indices.size)])
        while (stronglyUniqueSet.size < count) {
            stronglyUniqueSet.add(stronglyUniqueSelect(dictionary, indices, stronglyUniqueSet, cache))
        }
        return stronglyUniqueSet.toIntArray()
    }

    private fun stronglyUniqueSelect(dictionary: RcMatrix, indices: IntArray, taken: Set<Int>, cache: FloatArray): Int {
        val takenAtoms = taken.map { tIndex ->
            tIndex to dictionary[ALL, tIndex].array
        }
        var bestIndex = -1
        var bestPsnr = Float.MAX_VALUE
        indices.asList().chunked(max(1, indices.size / 16)).parallelStream().map { chunk ->
            var localBestIndex = -1
            var localBestPsnr = Float.MAX_VALUE
            chunk.forEach { newDIndex ->
                if (!taken.contains(newDIndex)) {
                    val currentPsnr = psnrMax(newDIndex, dictionary[ALL, newDIndex].array, takenAtoms, cache)
                    if (currentPsnr < localBestPsnr) {
                        localBestPsnr = currentPsnr
                        localBestIndex = newDIndex
                    }
                }
            }
            localBestIndex to localBestPsnr
        }.forEach { (currentIndex, currentPsnr) ->
            if (currentPsnr < bestPsnr) {
                bestPsnr = currentPsnr
                bestIndex = currentIndex
            }
        }
        if (bestIndex == -1) {
            throw RuntimeException("dictionary has less variation than dictionary size")
        }
        return bestIndex
    }

    private fun randomSelect(random: Random, dictionaryLow: RcMatrix, count: Int): IntArray {
        return (0 until dictionaryLow.columns).shuffled(random).take(count).toIntArray()
    }

    private fun dictionariesFromSelection(selection: IntArray, vararg dictionaries: RcMatrix): Array<RcMatrix> {
        val newDictionaries = Array(dictionaries.size) { RcMatrix(dictionaries[it].rows, selection.size) }
        for (j in 0 until dictionaries.size) {
            val newDictionary = newDictionaries[j]
            val dictionary = dictionaries[j]
            for (i in 0 until selection.size) {
                newDictionary[ALL, i] = dictionary[ALL, selection[i]]
            }
        }
        return newDictionaries
    }

    private fun dictionariesFromSelectionWithTransforms(selection: IntArray, vararg dictionaries: RcMatrix): Array<RcMatrix> {
        val newDictionaries = Array(dictionaries.size) { RcMatrix(dictionaries[it].rows, selection.size * 8) }
        val maskSizes = IntArray(dictionaries.size) {
            Math.round(sqrt(newDictionaries[it].rows.toDouble())).toInt()
        }
        for (j in 0 until dictionaries.size) {
            val newDictionary = newDictionaries[j]
            val rows = newDictionary.rows
            val maskSize = maskSizes[j]
            val dictionary = dictionaries[j]
            var output = 0
            for (i in 0 until selection.size) {
                val base = dictionary[ALL, selection[i]]
                newDictionary[ALL, output++] = base
                val seed = base.reshape(maskSize, maskSize)
                val transposed = seed.transpose()
                seed.rotate90Clockwise()
                newDictionary[ALL, output++] = seed.reshape(rows, 1)
                seed.rotate90Clockwise()
                newDictionary[ALL, output++] = seed.reshape(rows, 1)
                seed.rotate90Clockwise()
                newDictionary[ALL, output++] = seed.reshape(rows, 1)
                newDictionary[ALL, output++] = transposed.reshape(rows, 1)
                transposed.rotate90Clockwise()
                newDictionary[ALL, output++] = transposed.reshape(rows, 1)
                transposed.rotate90Clockwise()
                newDictionary[ALL, output++] = transposed.reshape(rows, 1)
                transposed.rotate90Clockwise()
                newDictionary[ALL, output++] = transposed.reshape(rows, 1)
            }
        }
        return newDictionaries
    }

    private fun dictionaryFromSelection(dictionary: RcMatrix, selection: IntArray): RcMatrix {
        val newDictionary = RcMatrix(dictionary.rows, selection.size)
        for (i in 0 until selection.size) {
            newDictionary[ALL, i] = dictionary[ALL, selection[i]]
        }
        return newDictionary
    }

    private fun writeDictionary(dictionary: RcMatrix, outputDir: File, fileName: String) {
        val maskSize = Math.sqrt(dictionary.rows.toDouble()).roundToInt()
        val outputDim = Math.ceil(Math.sqrt(dictionary.columns.toDouble())).roundToInt()
        val output = RcMatrix(maskSize * outputDim, maskSize * outputDim)
        for (i in 0 until dictionary.columns) {
            val atom = dictionary[ALL, i]
            val outputRow = i / outputDim
            val outputCol = i % outputDim
            output[outputRow * maskSize until outputRow * maskSize + maskSize, outputCol * maskSize until outputCol * maskSize + maskSize] = atom.reshape(maskSize, maskSize)
        }
        writeNormalizedTestImage(output, outputDir, fileName)
    }

    fun writeNormalizedTestImage(matrixToWrite: RcMatrix, outputDir: File, fileName: String) {
        val output = BufferedImage(matrixToWrite.columns, matrixToWrite.rows, BufferedImage.TYPE_USHORT_GRAY)
        val outputData = output.raster
        val min = matrixToWrite.min()
        val max = matrixToWrite.max()
        val scale = (1.0f / (max - min)) * 65535.0f
        for (row in 0 until matrixToWrite.rows) {
            for (column in 0 until matrixToWrite.columns) {
                outputData.setSample(column, row, 0, ((matrixToWrite[row, column] - min) * scale).roundToInt().coerceIn(0, 65535) and 0xFFFF)
            }
        }
        println("normalization values for: $fileName = min: ${min.format(4)}, max: ${max.format(4)}, scale: ${scale.format(4)}")
        ImageIO.write(output, "png", File(outputDir, fileName))
    }

    private fun Float.format(digits: Int) = java.lang.String.format("%.${digits}f", this)!!

    private inline fun <T> time(operation: String, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val stop = System.nanoTime()
        println("$operation: ${((stop - start)/1000000)/1000.0f}s")
        return result
    }

    private fun FloatArray.norm(): Float {
        return sqrt(sumByDouble { it * it.toDouble() }).toFloat()
    }

    private operator fun FloatArray.div(f: Float): FloatArray {
        for (i in 0 until this.size) {
            this[i] /= f
        }
        return this
    }
}
