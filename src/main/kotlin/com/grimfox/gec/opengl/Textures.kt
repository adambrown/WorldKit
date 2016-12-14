package com.grimfox.gec.opengl

import com.grimfox.gec.util.getResourceStream
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

fun loadTexture2D(minFilter: Int, magFilter: Int, baseImage: String, generateMipMaps: Boolean, vararg mipMaps: String): Triple<Int, Int, Int> {
    val bufferedImage = getResourceStream(baseImage).use { ImageIO.read(it) }
    val width = bufferedImage.width
    val height = bufferedImage.height
    val components = bufferedImage.colorModel.numComponents
    val componentBytes = bufferedImage.colorModel.componentSize.max()!! / 8
    val type: Int
    val bufferType: Int
    val usableImage: BufferedImage
    if (componentBytes == 1) {
        bufferType = DataBuffer.TYPE_BYTE
        val colorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), components == 4, false, Transparency.TRANSLUCENT, bufferType)
        val raster = colorModel.createCompatibleWritableRaster(width, height)
        usableImage = BufferedImage(colorModel, raster, false, null)
        val converter = ColorConvertOp(null)
        converter.filter(bufferedImage, usableImage)
        type = GL_UNSIGNED_BYTE
    } else if (componentBytes == 2) {
        bufferType = DataBuffer.TYPE_USHORT
        val colorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), components == 4, false, Transparency.TRANSLUCENT, bufferType)
        val raster = colorModel.createCompatibleWritableRaster(width, height)
        usableImage = BufferedImage(colorModel, raster, false, null)
        val converter = ColorConvertOp(null)
        converter.filter(bufferedImage, usableImage)
        type = GL_UNSIGNED_SHORT
    } else {
        bufferType = DataBuffer.TYPE_FLOAT
        val colorModel = ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), components == 4, false, Transparency.TRANSLUCENT, bufferType)
        val raster = colorModel.createCompatibleWritableRaster(width, height)
        usableImage = BufferedImage(colorModel, raster, false, null)
        val converter = ColorConvertOp(null)
        converter.filter(bufferedImage, usableImage)
        type = GL_FLOAT
    }
    val data: ByteBuffer = readImageData(usableImage, components, bufferType)
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
    val textureId = GL11.glGenTextures()
    glBindTexture(GL_TEXTURE_2D, textureId)
    glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment)
    glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, usableImage.width, usableImage.height, 0, format, type, data)
    if (generateMipMaps) {
        glGenerateMipmap(GL_TEXTURE_2D)
    }
    mipMaps.forEachIndexed { i, path ->
        val mipMapImage = getResourceStream(path).use { ImageIO.read(it) }
        try {
            val mipMapData = readImageData(mipMapImage, components, bufferType)
            glTexImage2D(GL_TEXTURE_2D, i + 1, internalFormat, mipMapImage.width, mipMapImage.height, 0, format, type, mipMapData)
        } catch (e: Exception) {
            throw IllegalStateException("unable to load mip map data with format not matching base image", e)
        }
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter)
    glBindTexture(GL_TEXTURE_2D, 0)
    return Triple(textureId, width, height)
}

private fun readImageData(image: BufferedImage, components: Int, bufferType: Int): ByteBuffer {
    val sampleModel = image.sampleModel
    val dataBuffer = image.raster.dataBuffer
    val width = image.width
    val height = image.height
    val bands = intArrayOf(0, 1, 2, 3)
    val data: ByteBuffer = when (bufferType) {
        DataBuffer.TYPE_BYTE -> {
            val textureData = ByteBuffer.allocateDirect(width * height * components)
            var offset = 0
            for (y in 0..height - 1) {
                for (x in 0..width - 1) {
                    for (band in 0..components - 1) {
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
            for (y in 0..height - 1) {
                for (x in 0..width - 1) {
                    for (band in 0..components - 1) {
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
            for (y in 0..height - 1) {
                for (x in 0..width - 1) {
                    for (band in 0..components - 1) {
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
    return data
}