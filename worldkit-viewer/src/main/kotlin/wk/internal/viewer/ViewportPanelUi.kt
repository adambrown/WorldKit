package wk.internal.viewer

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import wk.api.*
import wk.internal.ui.UserInterface
import wk.internal.ui.style.SHAPE_BORDER_ONLY
import wk.internal.ui.style.meshViewport3D
import wk.internal.ui.widgets.*
import wk.internal.ui.widgets.TextureBuilder.buildTextureRedByte
import wk.internal.ui.widgets.TextureBuilder.buildTextureRedFloat
import wk.internal.ui.widgets.TextureBuilder.buildTextureRedShort
import wk.internal.ui.widgets.TextureBuilder.buildTextureRgbaByte
import wk.internal.ui.widgets.ViewportMode.*
import java.nio.ByteBuffer
import kotlin.math.ceil

fun Block.viewportPanel(ui: UserInterface) {
    block {
        hSizing = Sizing.GROW
        layout = Layout.HORIZONTAL
        hAlign = HorizontalAlignment.LEFT
        block {
            block {
                xOffset = 1.0f
                yOffset = 1.0f
                width = -2.0f
                height = -2.0f
                meshViewport3D(meshViewport, ui)
            }
            block {
                shape = SHAPE_BORDER_ONLY
                isMouseAware = false
                canOverflow = true
            }
        }
    }
}

private var lastMapScale: MapScale? = null

fun showTerrain(displayData: TerrainDisplayData) {
    val viewportTextures = displayData.toDisplayTextures()
    colorHeightScaleFactor.value = displayData.normalizedScaleFactor
    meshViewport.setHeightmap(viewportTextures, VIEWPORT_HEIGHTMAP_SIZE)
    if (lastMapScale != displayData.mapScale) {
        heightMapScaleFactor.value = displayData.mapScale.viewportScale
        waterShaderParams.level.value = displayData.mapScale.waterRatio
        lastMapScale = displayData.mapScale
    }
    imageMode.value = HeightMap
}

fun showImage(displayData: ImageDisplayData) {
    val texture = displayData.toDisplayTexture().texture
    meshViewport.setImage(texture, displayData.isRgb)
    imageMode.value = Image
}

fun showIndex(displayData: IndexDisplayData) {
    val texture = displayData.toDisplayTexture().texture
    meshViewport.setIndex(texture)
    imageMode.value = Index
}

private fun ImageDisplayData.toDisplayTexture(): ImageDisplayTexture {
    return when (this) {
        is ImageDisplayData.Rgb8 -> toDisplayTexture()
        is ImageDisplayData.GrayU8 -> toDisplayTexture()
        is ImageDisplayData.GrayU16 -> toDisplayTexture()
        is ImageDisplayData.GrayF32 -> toDisplayTexture()
    }
}

private fun ImageDisplayData.Rgb8.toDisplayTexture(): ImageDisplayTexture {
    val width = image.width
    val buffer = BufferUtils.createByteBuffer(width * width * 4)
    for (y in 0 until width) {
        val yOff = y * width
        for (x in 0 until width) {
            val i = yOff + x
            val rgb = image[i]
            val p = i * 4
            buffer[p] = ((rgb ushr 16) and 0xFF).toByte()
            buffer[p + 1] = ((rgb ushr 8) and 0xFF).toByte()
            buffer[p + 2] = (rgb and 0xFF).toByte()
            buffer[p + 3] = -1
        }
    }
    return ImageDisplayTexture(buildTextureRgbaByte(buffer, width, GL11.GL_LINEAR, GL11.GL_LINEAR))
}

private fun ImageDisplayData.GrayU8.toDisplayTexture(): ImageDisplayTexture {
    return ImageDisplayTexture(buildTextureRedByte(arrayToBuffer(image), image.width, GL11.GL_LINEAR, GL11.GL_LINEAR))
}

private fun ImageDisplayData.GrayU16.toDisplayTexture(): ImageDisplayTexture {
    return ImageDisplayTexture(buildTextureRedShort(image.array, image.width, GL11.GL_LINEAR, GL11.GL_LINEAR))
}

private fun ImageDisplayData.GrayF32.toDisplayTexture(): ImageDisplayTexture {
    return ImageDisplayTexture(buildTextureRedFloat(image.array, image.width, GL11.GL_LINEAR, GL11.GL_LINEAR))
}

private fun IndexDisplayData.toDisplayTexture(): IndexDisplayTexture {
    return IndexDisplayTexture(buildTextureRedByte(arrayToBuffer(image), image.width, GL11.GL_NEAREST, GL11.GL_NEAREST))
}

private fun arrayToBuffer(image: ByteArrayMatrix): ByteBuffer {
    val width = image.width
    val buffer = BufferUtils.createByteBuffer(width * width)
    for (y in 0 until width) {
        val yOff = y * width
        for (x in 0 until width) {
            val i = yOff + x
            buffer[i] = image[i]
        }
    }
    return buffer
}

private fun TerrainDisplayData.toDisplayTextures(): TerrainDisplayTextures {

    return runBlocking {

        val width = heightMap.width

        val riverData = async {
            val riverPolyLines = riverLines
            val textureId = TextureBuilder.renderSplines(
                    width,
                    riverPolyLines.map {
                        buildEdges(getCurvePoints(it))
                    },
                    3.0f)
            ByteBufferMatrix(width, buffer = TextureBuilder.extractTextureRedByte(textureId, width))
        }

        val deferredHeightMapTexture = async {
            heightMapToTextureId(heightMap.array, width)
        }

        val deferredRiverTexture = async {
            val outputRivers = riverData.await()
            heightMapToTextureId(outputRivers.buffer, outputRivers.width)
        }

        val deferredNormalAndAo = async {
            val buffer = BufferUtils.createByteBuffer(width * width * 4)
            val normalMap = normalMap
            val aoMap = occlusionMap
            for (y in 0 until width) {
                val yOff = y * width
                for (x in 0 until width) {
                    val i = yOff + x
                    val rgb = normalMap[i]
                    val p = i * 4
                    buffer[p] = ((rgb ushr 16) and 0xFF).toByte()
                    buffer[p + 1] = ((rgb ushr 8) and 0xFF).toByte()
                    buffer[p + 2] = (rgb and 0xFF).toByte()
                    buffer[p + 3] = aoMap[i]
                }
            }
            normalAndAoToTextureId(buffer, width)
        }

        TerrainDisplayTextures(mapScale, deferredHeightMapTexture.await(), deferredRiverTexture.await(), deferredNormalAndAo.await())
    }
}

private fun heightMapToTextureId(input: ShortArray, width: Int): TextureId {
    return buildTextureRedShort(input, width, GL11.GL_LINEAR, GL11.GL_LINEAR)
}

private fun heightMapToTextureId(input: ByteBuffer, width: Int): TextureId {
    return buildTextureRedByte(input, width, GL11.GL_LINEAR, GL11.GL_LINEAR)
}

private fun normalAndAoToTextureId(input: ByteBuffer, width: Int): TextureId {
    return buildTextureRgbaByte(input, width, GL11.GL_LINEAR, GL11.GL_LINEAR)
}

private operator fun ByteBuffer.set(i: Int, b: Byte) = put(i, b)

val MapScale.viewportScale: Float get() = heightRangeMeters * (VIEWPORT_MESH_SCALE / mapSizeMeters)

private fun buildEdges(inPoints: List<Point2F>): MutableList<Point2F> {
    val outPoints = ArrayList<Point2F>()
    val start = inPoints.first()
    outPoints.add(start)
    for (i in 1 until inPoints.size) {
        val id = i % inPoints.size
        val lastId = i - 1
        val lastPoint = inPoints[lastId]
        val point = inPoints[id]
        if (i == 1) {
            outPoints[0] = lastPoint
        }
        outPoints.add(point)
    }
    return outPoints
}

private const val segmentSize = 0.00035
private const val smoothFactor = 2

private fun getCurvePoints(points: List<Point2F>): List<Point2F> {

    val newPoints = ArrayList<Point2F>()
    val copyPoints = ArrayList(points)

    val firstPoint = copyPoints.first()

    newPoints.add(point2(firstPoint.x, (1.0f - firstPoint.y)))
    for (i in 1 until copyPoints.size) {

        val lastPoint = copyPoints[i - 1]
        val thisPoint = copyPoints[i]

        val vector = thisPoint - lastPoint
        val length = vector.length
        if (length > segmentSize) {
            val segments = ceil(length / segmentSize).toInt()
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
                    for (i in size - 3 downTo 1 step 2) {
                        input.removeAt(i)
                        output.removeAt(i)
                    }
                    size = input.size
                }
            }
            for (i in 1..size - 2) {
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
