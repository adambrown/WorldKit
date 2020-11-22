package wk.api

import cern.colt.list.IntArrayList
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wk.internal.application.LOG
import wk.internal.ext.toIntList
import wk.internal.ext.toMutableIntList
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt
import java.awt.image.DataBufferUShort
import java.io.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.imageio.ImageIO
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

private object IoWaiter {

    @Volatile
    var running = true

    val ioTasks: MutableList<Job> = Collections.synchronizedList(ArrayList())

    val ioWaiters: MutableList<() -> Unit> = Collections.synchronizedList(ArrayList())

    val ioWaitThread: Thread

    init {
        ioWaitThread = thread(
                start = true,
                isDaemon = false,
                name = "io-wait"
        ) {
            runBlocking {
                while (running) {
                    try {
                        Thread.sleep(200)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    val watchList = synchronized(ioTasks) { ArrayList(ioTasks) }
                    watchList.forEach {
                        it.join()
                    }
                    synchronized(ioTasks) {
                        for (i in ioTasks.indices.reversed()) {
                            if (ioTasks[i].isCompleted) {
                                ioTasks.removeAt(i)
                            }
                        }
                        if (ioTasks.isEmpty()) {
                            synchronized(ioWaiters) {
                                ioWaiters.forEach { it() }
                                ioWaiters.clear()
                            }
                        }
                    }
                }
                val waitForAll = synchronized(ioTasks) { ArrayList(ioTasks) }
                waitForAll.forEach {
                    it.join()
                }
            }
        }
    }
}

@PublicApi
fun waitForBackgroundIo() {
    var done = false
    IoWaiter.ioWaiters.add {
        done = true
    }
    while (!done) {
        try {
            Thread.sleep(200)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

@PublicApi
fun finishIoTasksOnShutdown() {
    IoWaiter.running = false
}

private fun CharSequence.ensureSuffix(suffix: CharSequence, ignoreCase: Boolean = false): String {
    return if (endsWith(suffix, ignoreCase)) this.toString() else this.toString() + suffix
}

private fun String.toFile(suffix: String) = File(File(ensureSuffix(suffix)).absolutePath)

private fun String.toPngFile() = toFile(".png")

private fun String.toPfmFile() = toFile(".pfm")

private fun String.toTndFile() = toFile(".tnd")

private fun String.toTerFile() = toFile(".ter")

private fun String.toTddFile() = toFile(".tdd")

@PublicApi
fun readGrayU8(inFileName: String): ByteArrayMatrix {
    return readImageFile(inFileName.toPngFile()) { bufferedImage: BufferedImage ->
        val output = ByteArrayMatrix(bufferedImage.width, bufferedImage.height)
        val input = (bufferedImage.raster.dataBuffer as DataBufferByte).data
        System.arraycopy(input, 0, output.array, 0, output.array.size)
        output
    }
}

@PublicApi
fun ByteArrayMatrix.writeGrayU8(outFileName: String): File {
    return writeFile(outFileName.toPngFile()) {
        val output = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        val buffer = (output.raster.dataBuffer as DataBufferByte).data
        System.arraycopy(array, 0, buffer, 0, array.size)
        ImageIO.write(output, "png", it)
    }
}

@PublicApi
fun readRgb8(inFileName: String): IntArrayMatrix {
    return readImageFile(inFileName.toPngFile()) { bufferedImage: BufferedImage ->
        val output = IntArrayMatrix(bufferedImage.width, bufferedImage.height)
        val input = (bufferedImage.raster.dataBuffer as DataBufferByte).data
        (0 until bufferedImage.height).inParallel { y ->
            val yOff = y * bufferedImage.width
            for (x in 0 until bufferedImage.width) {
                val i = (yOff + x) * 3
                val b = input[i].toInt() and 0xFF
                val g = input[i + 1].toInt() and 0xFF
                val r = input[i + 2].toInt() and 0xFF
                output[yOff + x] = (r shl 16) or (g shl 8) or b
            }
        }
        output
    }
}

@PublicApi
fun IntArrayMatrix.writeRgb8(outFileName: String): File {
    return writeFile(outFileName.toPngFile()) {
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val buffer = (output.raster.dataBuffer as DataBufferInt).data
        System.arraycopy(array, 0, buffer, 0, array.size)
        ImageIO.write(output, "png", it)
    }
}

@PublicApi
fun readGrayU16(inFileName: String): ShortArrayMatrix {
    return readImageFile(inFileName.toPngFile()) { bufferedImage: BufferedImage ->
        val output = ShortArrayMatrix(bufferedImage.width, bufferedImage.height)
        val input = (bufferedImage.raster.dataBuffer as DataBufferUShort).data
        System.arraycopy(input, 0, output.array, 0, output.array.size)
        output
    }
}

@PublicApi
fun ShortArrayMatrix.writeGrayU16(outFileName: String): File {
    return writeFile(outFileName.toPngFile()) {
        val output = BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY)
        val buffer = (output.raster.dataBuffer as DataBufferUShort).data
        System.arraycopy(array, 0, buffer, 0, array.size)
        ImageIO.write(output, "png", it)
    }
}

@Serializable
private data class PfmMeta(val min: Float, val max: Float)

@PublicApi
fun readGrayF32(inFileName: String): FloatArrayMatrix {
    val file = inFileName.toPfmFile()
    return DataInputStream(file.inputStream().buffered()).use { stream ->
        val header = ArrayList<String>()
        val comments = ArrayList<String>()
        val buffer = ByteArrayOutputStream()
        var lastByte = 0
        while (lastByte > -1) {
            lastByte = stream.read()
            if (lastByte == 0x0A) {
                val line = String(buffer.toByteArray(), Charsets.US_ASCII).trim()
                buffer.reset()
                if (line.isNotBlank()) {
                    if (line.startsWith('#')) {
                        comments.add(line)
                    } else {
                        if (header.size == 1) {
                            line.split(' ').forEach {
                                header.add(it)
                            }
                        } else {
                            header.add(line)
                        }
                    }
                }
                if (header.size == 4) {
                    break
                }
            } else {
                buffer.write(lastByte)
            }
        }
        if (header.first() != "Pf" || header.size < 4) {
            throw IOException("Unable to read image from file. Incorrect data format.")
        }
        val width = header[1].toInt()
        val height = header[2].toInt()
        if (header[3].toFloat() != 1.0f) {
            throw IOException("Unable to read image from file. Must be big-endian.")
        }
        var min = 0.0f
        var max = 1.0f
        for (c in comments) {
            if (c.startsWith("#@meta:")) {
                val pfmMeta = Json.decodeFromString(PfmMeta.serializer(), c.substring(7))
                min = pfmMeta.min
                max = pfmMeta.max
                break
            }
        }
        val scale = max - min
        FloatArrayMatrix(width, height) {
            (stream.readFloat() * scale + min).coerceIn(min, max)
        }
    }
}

@PublicApi
fun FloatArrayMatrix.writeGrayF32(outFileName: String): File {
    return writeFile(outFileName.toPfmFile()) { file ->
        val (min, max) = parallelMinMax(Float.MAX_VALUE, -Float.MAX_VALUE)
        val delta = max - min
        val scale = 1.0f / delta
        val jsonHeader = Json.encodeToString(PfmMeta.serializer(), PfmMeta(min, max))
        DataOutputStream(file.outputStream().buffered()).use { stream ->
            stream.write("Pf\n#@meta:$jsonHeader\n${width}\n${width}\n1.0000\n".toByteArray(Charsets.US_ASCII))
            array.forEach {
                stream.writeFloat(((it - min) * scale).coerceIn(0.0f, 1.0f))
            }
        }
    }
}

@PublicApi
fun readOrCacheGrayU8(fileName: String, generate: suspend CoroutineScope.() -> ByteArrayMatrix) = readOrCache(fileName, ::readGrayU8, { writeGrayU8(it) }, generate)

@PublicApi
fun readOrCacheGrayU16(fileName: String, generate: suspend CoroutineScope.() -> ShortArrayMatrix) = readOrCache(fileName, ::readGrayU16, { writeGrayU16(it) }, generate)

@PublicApi
fun readOrCacheGrayF32(fileName: String, generate: suspend CoroutineScope.() -> FloatArrayMatrix) = readOrCache(fileName, ::readGrayF32, { writeGrayF32(it) }, generate)

@PublicApi
fun readOrCacheRgb8(fileName: String, generate: suspend CoroutineScope.() -> IntArrayMatrix) = readOrCache(fileName, ::readRgb8, { writeRgb8(it) }, generate)

@PublicApi
fun readOrCacheTerraformResult(fileName: String, generate: suspend CoroutineScope.() -> TerraformResult) = readOrCache(fileName, ::readTerraformResult, { writeTerraformResult(it) }, generate)

@PublicApi
fun readOrCacheTerraformNodeData(fileName: String, generate: suspend CoroutineScope.() -> TerraformNodeData) = readOrCache(fileName, ::readTerraformNodeData, { writeTerraformNodeData(it) }, generate)

@PublicApi
fun readOrCacheTerrainDisplayData(fileName: String, generate: suspend CoroutineScope.() -> TerrainDisplayData) = readOrCache(fileName, ::readTerrainDisplayData, { writeTerrainDisplayData(it) }, generate)

private inline fun <T> readOrCache(fileName: String, read: (String) -> T, crossinline write: T.(String) -> Unit, crossinline generate: suspend CoroutineScope.() -> T): T {
    return try {
        read(fileName)
    } catch (t: Throwable) {
        val output = runBlocking { generate() }
        launchIo { write(output, fileName) }
        output
    }
}

private inline fun launchIo(crossinline task: () -> Unit) {
    IoWaiter.ioTasks.add(GlobalScope.launch(IO) {
        try {
            task()
        } catch (t: Throwable) {
            LOG.error("Error executing fire-and-forget task.", t)
        } finally {
            System.gc()
        }
    })
}

@PublicApi
interface Cached<T> {
    val value: Deferred<T>
    fun evict()
}

private class CachedImpl<T>(private val file: File, private val generate: CoroutineScope.() -> T) : Cached<T> {
    val lock = ReentrantLock()

    var v: T? = null

    override val value: Deferred<T> get() {
        val fv1 = v
        return if (fv1 != null) {
            GlobalScope.async { fv1 }
        } else {
            val job = GlobalScope.async(IO) {
                lock.withLock {
                    val fv2 = v
                    if (fv2 != null) {
                        fv2
                    } else {
                        val fv3 = generate()
                        v = fv3
                        fv3
                    }
                }
            }
            IoWaiter.ioTasks.add(job)
            job
        }
    }

    override fun evict() {
        lock.withLock {
            v = null
            if (file.isFile) {
                file.delete()
            }
        }
    }
}

typealias Generator<T> = suspend CoroutineScope.() -> T

@PublicApi
class Cache(private val defaultProfile: String = "default") {

    private val cache = ConcurrentHashMap<String, MutableSet<CachedImpl<*>>>()

    @PublicApi
    fun grayU8(sourcePath: String, vararg cacheProfiles: String, generate: Generator<ByteArrayMatrix>): Cached<ByteArrayMatrix> {
        return cached(sourcePath.toPngFile(), cacheProfiles) { readOrCacheGrayU8(sourcePath, generate) }
    }

    @PublicApi
    fun grayU16(sourcePath: String, vararg cacheProfiles: String, generate: Generator<ShortArrayMatrix>): Cached<ShortArrayMatrix> {
        return cached(sourcePath.toPngFile(), cacheProfiles) { readOrCacheGrayU16(sourcePath, generate) }
    }

    @PublicApi
    fun grayF32(sourcePath: String, vararg cacheProfiles: String, generate: Generator<FloatArrayMatrix>): Cached<FloatArrayMatrix> {
        return cached(sourcePath.toPfmFile(), cacheProfiles) { readOrCacheGrayF32(sourcePath, generate) }
    }

    @PublicApi
    fun rgb8(sourcePath: String, vararg cacheProfiles: String, generate: Generator<IntArrayMatrix>): Cached<IntArrayMatrix> {
        return cached(sourcePath.toPngFile(), cacheProfiles) { readOrCacheRgb8(sourcePath, generate) }
    }

    @PublicApi
    fun terraformResult(sourcePath: String, vararg cacheProfiles: String, generate: Generator<TerraformResult>): Cached<TerraformResult> {
        return cached(sourcePath.toTerFile(), cacheProfiles) { readOrCacheTerraformResult(sourcePath, generate) }
    }

    @PublicApi
    fun terraformNodeData(sourcePath: String, vararg cacheProfiles: String, generate: Generator<TerraformNodeData>): Cached<TerraformNodeData> {
        return cached(sourcePath.toTndFile(), cacheProfiles) { readOrCacheTerraformNodeData(sourcePath, generate) }
    }

    @PublicApi
    fun terrainDisplayData(sourcePath: String, vararg cacheProfiles: String, generate: Generator<TerrainDisplayData>): Cached<TerrainDisplayData> {
        return cached(sourcePath.toTddFile(), cacheProfiles) { readOrCacheTerrainDisplayData(sourcePath, generate) }
    }

    @PublicApi
    fun clear(vararg cacheProfiles: String) {
        (cacheProfiles(cacheProfiles)).forEach { profileName ->
            val cachedValues = cache.getOrPut(profileName) { Collections.synchronizedSet(HashSet()) }
            synchronized(cachedValues) { cachedValues.forEach(Cached<*>::evict) }
        }
    }

    private fun <T> cached(sourceFile: File, cacheProfiles: Array<out String>, generate: CoroutineScope.() -> T): Cached<T> {
        val profiles = cacheProfiles(cacheProfiles)
        val cachedItem = CachedImpl(sourceFile, generate)
        cachedItem.lock.withLock {
            profiles.forEach {
                cache.getOrPut(it) { Collections.synchronizedSet(HashSet()) }.add(cachedItem)
            }
        }
        return cachedItem
    }

    private fun cacheProfiles(cacheProfiles: Array<out String>) =
            if (cacheProfiles.isEmpty()) {
                arrayOf(defaultProfile)
            } else {
                cacheProfiles
            }

    protected fun finalize() {
        cache.values.forEach { items ->
            synchronized(items) {
                items.forEach { item ->
                    item.v = null
                }
            }
        }
    }
}

@PublicApi
fun readTerraformNodeData(inFileName: String): TerraformNodeData {
    return readBinaryFile(inFileName.ensureSuffix(".tnd")) { stream ->
        var count = stream.readInt()
        val nodeIndex = ByteArray(count)
        stream.readFully(nodeIndex)
        count = stream.readInt()
        val landIndex = BitMatrix64(count) {
            stream.readLong()
        }
        count = stream.readInt()
        val coastIds = IntArrayList(count)
        repeat(count) {
            coastIds.add(stream.readInt())
        }
        count = stream.readInt()
        val borderIds = IntArrayList(count)
        repeat(count) {
            borderIds.add(stream.readInt())
        }
        TerraformNodeData(
                ByteBuffer.wrap(nodeIndex),
                landIndex,
                coastIds.toIntList(),
                borderIds.toMutableIntList())
    }
}

@PublicApi
fun TerraformNodeData.writeTerraformNodeData(outFileName: String): File {
    return writeFile(outFileName.toTndFile()) { file ->
        try {
            DataOutputStream(file.outputStream().buffered()).use { stream ->
                val nodeIndexArray = nodeIndex.array()
                stream.writeInt(nodeIndexArray.size)
                stream.write(nodeIndexArray)
                val landIndex = landIndex as BitMatrix64
                stream.writeInt(landIndex.width)
                landIndex.array.forEach {
                    stream.writeLong(it)
                }
                stream.writeInt(coastIds.size)
                coastIds.forEach {
                    stream.writeInt(it)
                }
                stream.writeInt(borderIds.size)
                borderIds.forEach {
                    stream.writeInt(it)
                }
            }
        } catch (e: Exception) {
            throw IOException("Failed to write binary file: ${file.absolutePath}", e)
        }
    }
}

@PublicApi
fun readTerraformResult(inFileName: String): TerraformResult {
    val file = File(inFileName.ensureSuffix(".ter"))
    return readBinaryFile(file.absolutePath) { stream ->
        val graphId = stream.readInt()
        val heightMap = readGrayF32(File(file.parentFile, stream.readUTF()).absolutePath)
        val nodeData = readTerraformNodeData(File(file.parentFile, stream.readUTF()).absolutePath)
        TerraformResult(heightMap, graphId, nodeData)
    }
}

@PublicApi
fun TerraformResult.writeTerraformResult(outFileName: String): File {
    return writeFile(outFileName.toTerFile()) { file ->
        try {
            runBlocking {
                val heightMapFileDeferred = async(IO) { heightMap.writeGrayF32(outFileName).relativeTo(file.parentFile) }
                val nodeDataFileDeferred = async(IO) { nodeData.writeTerraformNodeData(outFileName).relativeTo(file.parentFile) }
                DataOutputStream(file.outputStream().buffered()).use { stream ->
                    stream.writeInt(graphId)
                    stream.writeUTF(heightMapFileDeferred.await().path)
                    stream.writeUTF(nodeDataFileDeferred.await().path)
                }
            }
        } catch (e: Exception) {
            throw IOException("Failed to write binary file: ${file.absolutePath}", e)
        }
    }
}

@PublicApi
fun readTerrainDisplayData(inFileName: String): TerrainDisplayData {
    val file = File(inFileName.ensureSuffix(".tdd"))
    return readBinaryFile(file.absolutePath) { stream ->
        val mapScale = MapScale[stream.readByte().toInt() and 0xFF]
        val normalizedScaleFactor = stream.readFloat()
        val riverLineCount = stream.readInt()
        val polyLines = ArrayList<PolyLine>(riverLineCount)
        repeat(riverLineCount) {
            val pointCount = stream.readInt()
            val polyLine = PolyLine(pointCount)
            polyLines.add(polyLine)
            repeat(pointCount) {
                val x = stream.readFloat()
                val y = stream.readFloat()
                val z = stream.readFloat()
                polyLine.add(point3(x, y, z))
            }
        }
        val heightMap = readGrayU16(File(file.parentFile, stream.readUTF()).absolutePath)
        val normalMap = readRgb8(File(file.parentFile, stream.readUTF()).absolutePath)
        val occlusionMap = readGrayU8(File(file.parentFile, stream.readUTF()).absolutePath)
        TerrainDisplayData(mapScale, normalizedScaleFactor, heightMap, normalMap, occlusionMap, polyLines)
    }
}

@PublicApi
fun TerrainDisplayData.writeTerrainDisplayData(outFileName: String): File {
    return writeFile(outFileName.toTddFile()) { file ->
        try {
            runBlocking {
                val heightMapFileDeferred = async(IO) { heightMap.writeGrayU16("$outFileName-h").relativeTo(file.parentFile) }
                val normalMapFileDeferred = async(IO) { normalMap.writeRgb8("$outFileName-n").relativeTo(file.parentFile) }
                val occlusionMapFileDeferred = async(IO) { occlusionMap.writeGrayU8("$outFileName-o").relativeTo(file.parentFile) }
                DataOutputStream(file.outputStream().buffered()).use { stream ->
                    stream.writeByte(mapScale.ordinal)
                    stream.writeFloat(normalizedScaleFactor)
                    stream.writeInt(riverLines.size)
                    riverLines.forEach { polyLine ->
                        stream.writeInt(polyLine.size)
                        polyLine.forEach {
                            stream.writeFloat(it.x)
                            stream.writeFloat(it.y)
                            stream.writeFloat(it.z)
                        }
                    }
                    stream.writeUTF(heightMapFileDeferred.await().path)
                    stream.writeUTF(normalMapFileDeferred.await().path)
                    stream.writeUTF(occlusionMapFileDeferred.await().path)
                }
            }
        } catch (e: Exception) {
            throw IOException("Failed to write binary file: ${file.absolutePath}", e)
        }
    }
}

private inline fun <T> readImageFile(file: File, crossinline load: (BufferedImage) -> T): T {
    try {
        return load(ImageIO.read(file))
    } catch (e: Exception) {
        throw IOException("Failed to load mask from file: ${file.absolutePath}", e)
    }
}

private inline fun writeFile(file: File, crossinline write: (File) -> Any?): File {
    if (!file.parentFile.exists()) {
        if (!file.parentFile.mkdirs()) {
            throw IOException("Failed to create directory: ${file.parentFile.absolutePath}")
        }
    }
    if ((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) {
        try {
            write(file)
            return file
        } catch (e: Exception) {
            throw IOException("Failed to write to file: ${file.absolutePath}", e)
        }
    } else {
        throw IOException("File does not exist, could not be written to, or could not be created: ${file.absolutePath}")
    }
}

private inline fun <T> readBinaryFile(inFileName: String, crossinline load: (DataInputStream) -> T): T {
    val file = File(File(inFileName).absolutePath)
    return try {
        DataInputStream(file.inputStream().buffered()).use {
            load(it)
        }
    } catch (e: Exception) {
        throw IOException("Failed to read binary file: ${file.absolutePath}", e)
    }
}
