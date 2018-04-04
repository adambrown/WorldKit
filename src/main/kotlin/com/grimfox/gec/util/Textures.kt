package com.grimfox.gec.util

import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.ui.nvgproxy.*
import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.joml.SimplexNoise.noise
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL30.*
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

val BLANK_TEXTURE = TextureId(loadTexture2D(GL_LINEAR_MIPMAP_NEAREST, GL_LINEAR, "/textures/blank.png", true, true).first)

fun loadImagePixels(resource: String): Triple<Int, Int, ByteBuffer> {
    val bufferedImage = getResourceStream(resource).use { ImageIO.read(it) }
    val width = bufferedImage.width
    val height = bufferedImage.height
    val sampleModel = bufferedImage.sampleModel
    val dataBuffer = bufferedImage.raster.dataBuffer
    val imageBuffer = ByteBuffer.allocateDirect(width * height * 4)
    val bands = intArrayOf(0, 1, 2, 3)
    var offset = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            for (band in 0..3) {
                imageBuffer.put(offset++, sampleModel.getSample(x, y, bands[band], dataBuffer).toByte())
            }
        }
    }
    return Triple(width, height, imageBuffer)
}

fun loadColorsAsTexture(colors: Iterable<NPColor>, width: Int, skip: Int): Int {
    val data = ByteBuffer.allocateDirect(width * width * 3).order(ByteOrder.nativeOrder())
    colors.drop(skip).take(width * width).forEach {
        data.put(it.rByte)
        data.put(it.gByte)
        data.put(it.bByte)
    }
    data.flip()
    val textureId = GL11.glGenTextures()
    glBindTexture(GL_TEXTURE_2D, textureId)
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, width, width, 0, GL_RGB, GL_UNSIGNED_BYTE, data)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    glBindTexture(GL_TEXTURE_2D, 0)
    return textureId
}


fun loadGraphPointsAsTexture(graph: Graph): Triple<Int, Int, Int> {
    val width = graph.stride!!
    val bytes = ByteBuffer.allocateDirect(width * width * 8).order(ByteOrder.nativeOrder())
    val data = bytes.asFloatBuffer()
    data.put(graph.vertexIdsToPoints)
    data.flip()
    val textureId = GL11.glGenTextures()
    glBindTexture(GL_TEXTURE_2D, textureId)
    glPixelStorei(GL_UNPACK_ALIGNMENT, 8)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RG32F, width, width, 0, GL_RG, GL_FLOAT, data)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    glBindTexture(GL_TEXTURE_2D, 0)
    return Triple(textureId, width, width)
}

fun loadRegionMaskAsTexture(regionMask: Matrix<Byte>): Triple<Int, Int, Int> {
    val width = regionMask.width
    val data = ByteBuffer.allocateDirect(width * width)
    for (i in 0 until regionMask.size.toInt()) {
        data.put(Math.round((com.grimfox.gec.util.geometry.clamp(regionMask[i].toFloat(), 0.0f, 16.0f) / 16.0f) * 255).toByte())
    }
    data.flip()
    val textureId = GL11.glGenTextures()
    glBindTexture(GL_TEXTURE_2D, textureId)
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, width, 0, GL_RED, GL_UNSIGNED_BYTE, data)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    glBindTexture(GL_TEXTURE_2D, 0)
    return Triple(textureId, width, width)
}

fun loadTexture2D(minFilter: Int, magFilter: Int, baseImage: String, generateMipMaps: Boolean, clampToEdge: Boolean, noiseAmount: Int = 0, noiseScale: Float = 0.0f, vararg mipMaps: String): Triple<Int, Int, Int> {
    val bufferedImage = getResourceStream(baseImage).use { ImageIO.read(it) }
    val mipMapImages = Array<BufferedImage>(mipMaps.size) {
        getResourceStream(mipMaps[it]).use { ImageIO.read(it) }
    }
    return loadTexture2D(minFilter, magFilter, bufferedImage, generateMipMaps, clampToEdge, noiseAmount, noiseScale, *mipMapImages)
}

fun loadTexture2D(minFilter: Int, magFilter: Int, bufferedImage: BufferedImage, generateMipMaps: Boolean, clampToEdge: Boolean, noiseAmount: Int = 0, noiseScale: Float = 0.0f, vararg mipMaps: BufferedImage): Triple<Int, Int, Int> {
    return prepareImageDataForLoading(minFilter, magFilter, bufferedImage, generateMipMaps, clampToEdge, noiseAmount, noiseScale, *mipMaps).loadImageDataIntoOpengl()
}

fun prepareImageDataForLoading(minFilter: Int, magFilter: Int, bufferedImage: BufferedImage, generateMipMaps: Boolean, clampToEdge: Boolean, noiseAmount: Int, noiseScale: Float, vararg mipMaps: BufferedImage): LoadableImageData {
    val width = bufferedImage.width
    val height = bufferedImage.height
    val components = bufferedImage.colorModel.numComponents
    val componentBytes = bufferedImage.colorModel.componentSize.max()!! / 8
    val type: Int
    val bufferType: Int
    val usableImage: BufferedImage
    when (componentBytes) {
        1 -> {
            bufferType = DataBuffer.TYPE_BYTE
            val colorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), components == 4, false, Transparency.TRANSLUCENT, bufferType)
            val raster = colorModel.createCompatibleWritableRaster(width, height)
            usableImage = BufferedImage(colorModel, raster, false, null)
            val converter = ColorConvertOp(null)
            converter.filter(bufferedImage, usableImage)
            type = GL_UNSIGNED_BYTE
        }
        2 -> {
            bufferType = DataBuffer.TYPE_USHORT
            val colorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), components == 4, false, Transparency.TRANSLUCENT, bufferType)
            val raster = colorModel.createCompatibleWritableRaster(width, height)
            usableImage = BufferedImage(colorModel, raster, false, null)
            val converter = ColorConvertOp(null)
            converter.filter(bufferedImage, usableImage)
            type = GL_UNSIGNED_SHORT
        }
        else -> {
            bufferType = DataBuffer.TYPE_FLOAT
            val colorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), components == 4, false, Transparency.TRANSLUCENT, bufferType)
            val raster = colorModel.createCompatibleWritableRaster(width, height)
            usableImage = BufferedImage(colorModel, raster, false, null)
            val converter = ColorConvertOp(null)
            converter.filter(bufferedImage, usableImage)
            type = GL_FLOAT
        }
    }
    val data: ByteBuffer = if (noiseAmount == 0) readImageData(usableImage, components, bufferType) else readImageDataWithNoise(usableImage, components, bufferType, noiseAmount, noiseScale)
    val unpackAlignment: Int
    val internalFormat: Int
    val format = when (components) {
        1 -> {
            internalFormat = when (bufferType) {
                DataBuffer.TYPE_BYTE -> {
                    unpackAlignment = 1
                    GL_R8
                }
                DataBuffer.TYPE_USHORT -> {
                    unpackAlignment = 2
                    GL_R16
                }
                DataBuffer.TYPE_FLOAT -> {
                    unpackAlignment = 4
                    GL_R32F
                }
                else -> {
                    throw IllegalStateException("unable to load texture with invalid data type")
                }
            }
            GL_RED
        }
        2 -> {
            internalFormat = when (bufferType) {
                DataBuffer.TYPE_BYTE -> {
                    unpackAlignment = 2
                    GL_RG8
                }
                DataBuffer.TYPE_USHORT -> {
                    unpackAlignment = 4
                    GL_RG16
                }
                DataBuffer.TYPE_FLOAT -> {
                    unpackAlignment = 8
                    GL_RG32F
                }
                else -> {
                    throw IllegalStateException("unable to load texture with invalid data type")
                }
            }
            GL_RG
        }
        3 -> {
            internalFormat = when (bufferType) {
                DataBuffer.TYPE_BYTE -> {
                    unpackAlignment = 1
                    GL_RGB8
                }
                DataBuffer.TYPE_USHORT -> {
                    unpackAlignment = 2
                    GL_RGB16
                }
                DataBuffer.TYPE_FLOAT -> {
                    unpackAlignment = 4
                    GL_RGB32F
                }
                else -> {
                    throw IllegalStateException("unable to load texture with invalid data type")
                }
            }
            GL_RGB
        }
        4 -> {
            internalFormat = when (bufferType) {
                DataBuffer.TYPE_BYTE -> {
                    unpackAlignment = 4
                    GL_RGBA8
                }
                DataBuffer.TYPE_USHORT -> {
                    unpackAlignment = 8
                    GL_RGBA16
                }
                DataBuffer.TYPE_FLOAT -> {
                    unpackAlignment = 8
                    GL_RGBA32F
                }
                else -> {
                    throw IllegalStateException("unable to load texture with invalid data type")
                }
            }
            GL_RGBA
        }
        else -> {
            throw IllegalStateException("unable to load texture with more than 4 components")
        }
    }
    val mipMapData = mipMaps.map {
        (if (noiseAmount == 0) readImageData(it, components, bufferType) else readImageDataWithNoise(it, components, bufferType, noiseAmount, noiseScale)) to it
    }
    return LoadableImageData(unpackAlignment, internalFormat, usableImage, format, type, data, generateMipMaps, mipMapData, minFilter, magFilter, clampToEdge, width, height)
}

class LoadableImageData(
        private val unpackAlignment: Int,
        private val internalFormat: Int,
        private val usableImage: BufferedImage,
        private val format: Int,
        private val type: Int,
        private val data: ByteBuffer,
        private val generateMipMaps: Boolean,
        private val mipMapData: List<Pair<ByteBuffer, BufferedImage>>,
        private val minFilter: Int,
        private val magFilter: Int,
        private val clampToEdge: Boolean,
        private val width: Int,
        private val height: Int) {

    fun loadImageDataIntoOpengl(): Triple<Int, Int, Int> {
        val textureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, textureId)
        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment)
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, usableImage.width, usableImage.height, 0, format, type, data)
        if (generateMipMaps) {
            glGenerateMipmap(GL_TEXTURE_2D)
        }
        mipMapData.forEachIndexed { i, (levelData, levelImage) ->
            try {
                glTexImage2D(GL_TEXTURE_2D, i + 1, internalFormat, levelImage.width, levelImage.height, 0, format, type, levelData)
            } catch (e: Exception) {
                throw IllegalStateException("unable to load mip map data with format not matching base image", e)
            }
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
        if (clampToEdge) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        }
        glBindTexture(GL_TEXTURE_2D, 0)
        return Triple(textureId, width, height)
    }
}

private fun readImageData(image: BufferedImage, components: Int, bufferType: Int): ByteBuffer {
    val sampleModel = image.sampleModel
    val dataBuffer = image.raster.dataBuffer
    val width = image.width
    val height = image.height
    val bands = intArrayOf(0, 1, 2, 3)
    return when (bufferType) {
        DataBuffer.TYPE_BYTE -> {
            val textureData = ByteBuffer.allocateDirect(width * height * components)
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    for (band in 0 until components) {
                        textureData.put(offset++, sampleModel.getSample(x, y, bands[band], dataBuffer).toByte())
                    }
                }
            }
            textureData
        }
        DataBuffer.TYPE_USHORT -> {
            val bytes = ByteBuffer.allocateDirect(width * height * components * 2).order(ByteOrder.nativeOrder())
            val textureData = bytes.asShortBuffer()
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    for (band in 0 until components) {
                        textureData.put(offset++, sampleModel.getSample(x, y, bands[band], dataBuffer).toShort())
                    }
                }
            }
            bytes
        }
        DataBuffer.TYPE_FLOAT -> {
            val bytes = ByteBuffer.allocateDirect(width * height * components * 4).order(ByteOrder.nativeOrder())
            val textureData = bytes.asFloatBuffer()
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    for (band in 0 until components) {
                        textureData.put(offset++, sampleModel.getSampleFloat(x, y, bands[band], dataBuffer))
                    }
                }
            }
            bytes
        }
        else -> {
            throw IllegalStateException("unable to load texture with invalid data type")
        }
    }
}

private fun readImageDataWithNoise(image: BufferedImage, components: Int, bufferType: Int, noiseAmount: Int, noiseScale: Float): ByteBuffer {
    val noiseLinearScale = (Math.pow(2.0, noiseScale.toDouble()) * (1.0 / 4096.0)).toFloat() * (4096.0f / image.width)
    var noise = noiseAmount.coerceIn(0..127)
    val sampleModel = image.sampleModel
    val dataBuffer = image.raster.dataBuffer
    val width = image.width
    val height = image.height
    val bands = intArrayOf(0, 1, 2, 3)
    return when (bufferType) {
        DataBuffer.TYPE_BYTE -> {
            val textureData = ByteBuffer.allocateDirect(width * height * components)
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    for (band in 0 until components) {
                        val adjustment = Math.round(noise(x * noiseLinearScale, y * noiseLinearScale) * noise)
                        val sample = (sampleModel.getSample(x, y, bands[band], dataBuffer) + adjustment).coerceIn(0, 255)
                        textureData.put(offset++, sample.toByte())
                    }
                }
            }
            textureData
        }
        DataBuffer.TYPE_USHORT -> {
            noise *= 256
            val bytes = ByteBuffer.allocateDirect(width * height * components * 2).order(ByteOrder.nativeOrder())
            val textureData = bytes.asShortBuffer()
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    for (band in 0 until components) {
                        val adjustment = Math.round(noise(x * noiseLinearScale, y * noiseLinearScale) * noise)
                        val sample = (sampleModel.getSample(x, y, bands[band], dataBuffer) + adjustment).coerceIn(0, 65535)
                        textureData.put(offset++, sample.toShort())
                    }
                }
            }
            bytes
        }
        DataBuffer.TYPE_FLOAT -> {
            val noiseF = noise / 256.0f
            val bytes = ByteBuffer.allocateDirect(width * height * components * 4).order(ByteOrder.nativeOrder())
            val textureData = bytes.asFloatBuffer()
            var offset = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    for (band in 0 until components) {
                        val adjustment = noise(x * noiseLinearScale, y * noiseLinearScale) * noiseF
                        val sample = (sampleModel.getSampleFloat(x, y, bands[band], dataBuffer) + adjustment).coerceIn(0.0f, 1.0f)
                        textureData.put(offset++, sample)
                    }
                }
            }
            bytes
        }
        else -> {
            throw IllegalStateException("unable to load texture with invalid data type")
        }
    }
}