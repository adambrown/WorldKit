package com.grimfox.gec.opengl

import com.grimfox.gec.util.getPathForResource
import com.grimfox.gec.util.getResourceStream
import com.grimfox.gec.util.getResourceUrl
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.*
import java.awt.image.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

fun loadTexture2D(dataType: Int, minFilter: Int, magFilter: Int, baseImage: String, generateMipMaps: Boolean, vararg mipMaps: String): Triple<Int, Int, Int> {
    val bufferedImage = getResourceStream(baseImage).use { ImageIO.read(it) }
    val width = bufferedImage.width
    val height = bufferedImage.height
    val dataBuffer = bufferedImage.raster.dataBuffer
    val dataBufferClass: Class<*>
    val type: Int
    val data: ByteBuffer = when (dataBuffer.dataType) {
        DataBuffer.TYPE_BYTE -> {
            if (dataBuffer is DataBufferByte) {
                dataBufferClass = DataBufferByte::class.java
                val textureData = ByteBuffer.allocateDirect(dataBuffer.size).order(ByteOrder.LITTLE_ENDIAN)
                textureData.put(dataBuffer.data)
                textureData.flip()
                if (dataType == GL_UNSIGNED_BYTE || dataType == GL_BYTE) {
                    type = dataType
                } else {
                    throw IllegalStateException("unable to load texture with data type not matching requested data type")
                }
                textureData
            } else {
                throw IllegalStateException("unable to load texture with invalid data type")
            }
        }
        DataBuffer.TYPE_USHORT -> {
            if (dataBuffer is DataBufferUShort) {
                dataBufferClass = DataBufferUShort::class.java
                val textureData = ByteBuffer.allocateDirect(dataBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                val typedData = textureData.asShortBuffer()
                typedData.put(dataBuffer.data)
                if (dataType == GL_UNSIGNED_SHORT || dataType == GL_SHORT) {
                    type = dataType
                } else {
                    throw IllegalStateException("unable to load texture with data type not matching requested data type")
                }
                textureData
            } else {
                throw IllegalStateException("unable to load texture with invalid data type")
            }
        }
        DataBuffer.TYPE_SHORT -> {
            if (dataBuffer is DataBufferShort) {
                dataBufferClass = DataBufferShort::class.java
                val textureData = ByteBuffer.allocateDirect(dataBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                val typedData = textureData.asShortBuffer()
                typedData.put(dataBuffer.data)
                if (dataType == GL_UNSIGNED_SHORT || dataType == GL_SHORT) {
                    type = dataType
                } else {
                    throw IllegalStateException("unable to load texture with data type not matching requested data type")
                }
                textureData
            } else {
                throw IllegalStateException("unable to load texture with invalid data type")
            }
        }
        DataBuffer.TYPE_INT -> {
            if (dataBuffer is DataBufferInt) {
                dataBufferClass = DataBufferInt::class.java
                val textureData = ByteBuffer.allocateDirect(dataBuffer.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                val typedData = textureData.asIntBuffer()
                typedData.put(dataBuffer.data)
                if (dataType == GL_UNSIGNED_INT || dataType == GL_INT) {
                    type = dataType
                } else {
                    throw IllegalStateException("unable to load texture with data type not matching requested data type")
                }
                textureData
            } else {
                throw IllegalStateException("unable to load texture with invalid data type")
            }
        }
        DataBuffer.TYPE_FLOAT -> {
            if (dataBuffer is DataBufferFloat) {
                dataBufferClass = DataBufferFloat::class.java
                val textureData = ByteBuffer.allocateDirect(dataBuffer.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                val typedData = textureData.asFloatBuffer()
                typedData.put(dataBuffer.data)
                if (dataType == GL_FLOAT) {
                    type = dataType
                } else {
                    throw IllegalStateException("unable to load texture with data type not matching requested data type")
                }
                textureData
            } else {
                throw IllegalStateException("unable to load texture with invalid data type")
            }
        }
        else -> {
            throw IllegalStateException("unable to load texture with invalid data type")
        }
    }
    val internalFormat: Int
    val format = when (bufferedImage.colorModel.numComponents) {
        1 -> {
            internalFormat = when (dataType) {
                GL_UNSIGNED_BYTE -> {
                    GL_R8
                }
                GL_BYTE -> {
                    GL_R8_SNORM
                }
                GL_UNSIGNED_SHORT -> {
                    GL_R16
                }
                GL_SHORT -> {
                    GL_R16_SNORM
                }
                GL_UNSIGNED_INT -> {
                    GL_R32UI
                }
                GL_INT -> {
                    GL_R32I
                }
                GL_FLOAT -> {
                    GL_R32F
                }
                else -> {
                    throw IllegalStateException("unable to load texture with invalid data type")
                }
            }
            GL_RED
        }
        2 -> {
            internalFormat = when (dataType) {
                GL_UNSIGNED_BYTE -> {
                    GL_RG8
                }
                GL_BYTE -> {
                    GL_RG8_SNORM
                }
                GL_UNSIGNED_SHORT -> {
                    GL_RG16
                }
                GL_SHORT -> {
                    GL_RG16_SNORM
                }
                GL_UNSIGNED_INT -> {
                    GL_RG32UI
                }
                GL_INT -> {
                    GL_RG32I
                }
                GL_FLOAT -> {
                    GL_RG32F
                }
                else -> {
                    throw IllegalStateException("unable to load texture with invalid data type")
                }
            }
            GL_RG
        }
        3 -> {
            internalFormat = when (dataType) {
                GL_UNSIGNED_BYTE -> {
                    GL_RGB8
                }
                GL_BYTE -> {
                    GL_RGB8_SNORM
                }
                GL_UNSIGNED_SHORT -> {
                    GL_RGB16
                }
                GL_SHORT -> {
                    GL_RGB16_SNORM
                }
                GL_UNSIGNED_INT -> {
                    GL_RGB32UI
                }
                GL_INT -> {
                    GL_RGB32I
                }
                GL_FLOAT -> {
                    GL_RGB32F
                }
                else -> {
                    throw IllegalStateException("unable to load texture with invalid data type")
                }
            }
            GL_RGB
        }
        4 -> {
            internalFormat = when (dataType) {
                GL_UNSIGNED_BYTE -> {
                    GL_RGBA8
                }
                GL_BYTE -> {
                    GL_RGBA8_SNORM
                }
                GL_UNSIGNED_SHORT -> {
                    GL_RGBA16
                }
                GL_SHORT -> {
                    GL_RGBA16_SNORM
                }
                GL_UNSIGNED_INT -> {
                    GL_RGBA32UI
                }
                GL_INT -> {
                    GL_RGBA32I
                }
                GL_FLOAT -> {
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
    glPixelStorei(GL_UNPACK_ALIGNMENT, 2)
    glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, bufferedImage.width, bufferedImage.height, 0, format, type, data)
    if (generateMipMaps) {
        glGenerateMipmap(GL_TEXTURE_2D)
    }
    mipMaps.forEachIndexed { i, path ->
        val mipMapImage = getResourceStream(path).use { ImageIO.read(it) }
        val mipMapBuffer = mipMapImage.raster.dataBuffer
        try {
            val mipMapData: ByteBuffer = if (dataBufferClass == DataBufferByte::class.java) {
                val textureData = ByteBuffer.allocateDirect(mipMapBuffer.size).order(ByteOrder.LITTLE_ENDIAN)
                textureData.put((mipMapBuffer as DataBufferByte).data)
                textureData.flip()
                textureData
            } else if (dataBufferClass == DataBufferUShort::class.java) {
                val textureData = ByteBuffer.allocateDirect(mipMapBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                textureData.asShortBuffer().put((mipMapBuffer as DataBufferUShort).data)
                textureData
            } else if (dataBufferClass == DataBufferShort::class.java) {
                val textureData = ByteBuffer.allocateDirect(mipMapBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                textureData.asShortBuffer().put((mipMapBuffer as DataBufferShort).data)
                textureData
            } else if (dataBufferClass == DataBufferInt::class.java) {
                val textureData = ByteBuffer.allocateDirect(mipMapBuffer.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                textureData.asIntBuffer().put((mipMapBuffer as DataBufferInt).data)
                textureData
            } else if (dataBufferClass == DataBufferFloat::class.java) {
                val textureData = ByteBuffer.allocateDirect(mipMapBuffer.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                textureData.asFloatBuffer().put((mipMapBuffer as DataBufferFloat).data)
                textureData
            } else {
                throw IllegalStateException("unable to load mip map data with format not matching base image")
            }
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