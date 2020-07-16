package wk.internal.viewer

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import wk.internal.ui.UserInterface
import wk.internal.ui.style.*
import wk.internal.ui.widgets.*
import wk.internal.ui.widgets.TextureBuilder.buildTextureRedByte
import wk.internal.ui.widgets.TextureBuilder.buildTextureRedFloat
import wk.internal.ui.widgets.TextureBuilder.buildTextureRedShort
import wk.internal.ui.widgets.TextureBuilder.buildTextureRgbaByte
import wk.internal.ui.widgets.ViewportMode.*
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import wk.api.*
import wk.api.TextureId
import wk.api.ByteArrayMatrix
import wk.api.ByteBufferMatrix
import java.nio.ByteBuffer

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
                        buildOpenEdges(it, 0.00035f, 2)
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