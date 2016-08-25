package com.grimfox.gec.model

import com.grimfox.gec.util.Utils
import com.grimfox.gec.util.Utils.toRandomAccessFileMode
import java.io.File
import java.io.RandomAccessFile
import java.lang.reflect.ParameterizedType
import java.nio.channels.FileChannel
import java.util.*
import kotlin.reflect.KClass

object DataFiles {

    private val TYPE_PAIRS = listOf<Pair<Int, Class<out Matrix<*>>>>(
            Pair(1, BitMatrix::class.java),
            Pair(2, PointMatrix::class.java),
            Pair(3, Uint24Matrix::class.java),
            Pair(4, FloatMatrix::class.java),
            Pair(5, ClosestPointsMatrix.M2::class.java),
            Pair(6, ClosestPointsMatrix.M3::class.java),
            Pair(7, ClosestPointsMatrix.M4::class.java),
            Pair(8, ClosestPointsMatrix.M5::class.java)
    )
    private val ID_TYPE_MAP = mapOf(*TYPE_PAIRS.toTypedArray())
    private val TYPE_ID_MAP = mapOf(*TYPE_PAIRS.map { Pair(it.second, it.first) }.toTypedArray())



    inline fun <reified M : Matrix<*>> createAndUse(file: File, exponent: Int, noinline codeBlock: (M) -> Unit) {
        createAndUse(file, exponent, M::class.java, codeBlock)
    }

    fun <M : Matrix<*>> createAndUse(file: File, exponent: Int, format: Class<M>, codeBlock: (M) -> Unit) {
        if (file.exists()) {
            file.delete()
        }
        val mode = FileChannel.MapMode.READ_WRITE
        fun createAndUse(matrixInit: (FileChannel) -> Matrix<*>) {
            RandomAccessFile(file, mode.toRandomAccessFileMode()).use { randomAccessFile ->
                randomAccessFile.writeByte(TYPE_ID_MAP[format]!!)
                randomAccessFile.writeByte(exponent)
                randomAccessFile.channel.use { channel ->
                    @Suppress("UNCHECKED_CAST") val matrix = matrixInit(channel) as M
                    useMatrix(matrix, codeBlock)
                }
            }
        }
        when (format) {
            BitMatrix::class.java -> createAndUse { BitMatrix(it, mode, exponent, 2) }
            PointMatrix::class.java -> createAndUse { PointMatrix(it, mode, exponent, 2) }
            Uint24Matrix::class.java -> createAndUse { Uint24Matrix(it, mode, exponent, 2) }
            FloatMatrix::class.java -> createAndUse { FloatMatrix(it, mode, exponent, 2) }
            ClosestPointsMatrix.M2::class.java -> createAndUse { ClosestPointsMatrix.M2(it, mode, exponent, 2) }
            ClosestPointsMatrix.M3::class.java -> createAndUse { ClosestPointsMatrix.M3(it, mode, exponent, 2) }
            ClosestPointsMatrix.M4::class.java -> createAndUse { ClosestPointsMatrix.M4(it, mode, exponent, 2) }
            ClosestPointsMatrix.M5::class.java -> createAndUse { ClosestPointsMatrix.M5(it, mode, exponent, 2) }
            else -> throw IllegalArgumentException("${format.canonicalName} is not a valid format for matrix data.")
        }
    }

    fun <T : Any> openAndUse(file: File, type: Class<T>, mode: FileChannel.MapMode = FileChannel.MapMode.READ_ONLY, codeBlock: (Matrix<T>) -> Unit) {
        useMatrix(checkRawMatrixDataType(createRawMatrixDataUntyped(file, mode), type), codeBlock)
    }

    fun <T : Any> openAndUse(file: File, type: KClass<T>, mode: FileChannel.MapMode = FileChannel.MapMode.READ_ONLY, codeBlock: (Matrix<T>) -> Unit) {
        openAndUse(file, type.java, mode, codeBlock)
    }

    inline fun <reified T : Any> openAndUse(file: File, mode: FileChannel.MapMode = FileChannel.MapMode.READ_ONLY, noinline codeBlock: (Matrix<T>) -> Unit) {
        openAndUse(file, T::class.java, mode, codeBlock)
    }

    fun openAndUseUntyped(file: File, mode: FileChannel.MapMode = FileChannel.MapMode.READ_ONLY, codeBlock: (Matrix<Any>) -> Unit) {
        openAndUse(file, Any::class.java, mode, codeBlock)
    }

    private fun <T : Any> checkRawMatrixDataType(matrix: Matrix<*>, type: Class<T>): Matrix<T> {
        val matrixType = getMatrixGenericType(matrix.javaClass)
        if (matrixType != null && (type.isAssignableFrom(matrixType) || type.isAssignableFrom(Utils.primitiveToWrapper(matrixType)))) {
            @Suppress("UNCHECKED_CAST") return matrix as Matrix<T>
        } else {
            throw IllegalArgumentException("Matrix type does not match: ${type.canonicalName}")
        }
    }

    private fun getMatrixGenericType(type: Class<*>): Class<*>? {
        val allPossibleTypes = LinkedHashSet<Class<*>>()
        addAllSuperTypes(allPossibleTypes, type)
        allPossibleTypes.forEach {
            it.genericInterfaces.forEach {
                if (it is ParameterizedType) {
                    if (it.rawType is Class<*>) {
                        val rawClass = it.rawType as Class<*>
                        if (Matrix::class.java.equals(rawClass)) {
                            val genericType = it.actualTypeArguments.firstOrNull()
                            if (genericType != null && genericType is Class<*>) {
                                return genericType
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun addAllSuperTypes(superTypes: MutableSet<Class<*>>, type: Class<*>) {
        superTypes.add(type)
        val superType = type.superclass
        if (superType != null && superType != type) {
            addAllSuperTypes(superTypes, superType)
            type.interfaces?.forEach { addAllSuperTypes(superTypes, it) }
        }
    }

    private fun createRawMatrixDataUntyped(file: File, mode: FileChannel.MapMode): Matrix<*> {
        if (!file.isFile) {
            throw IllegalArgumentException("File ${file.canonicalPath} does not exist.")
        }
        RandomAccessFile(file, mode.toRandomAccessFileMode()).use { randomAccessFile ->
            randomAccessFile.channel.use { channel ->
                val format = ID_TYPE_MAP[randomAccessFile.readByte().toInt()]
                val exponent = randomAccessFile.readByte().toInt()
                when (format) {
                    BitMatrix::class.java -> return BitMatrix(channel, mode, exponent, 2)
                    PointMatrix::class.java -> return PointMatrix(channel, mode, exponent, 2)
                    Uint24Matrix::class.java -> return Uint24Matrix(channel, mode, exponent, 2)
                    FloatMatrix::class.java -> return FloatMatrix(channel, mode, exponent, 2)
                    ClosestPointsMatrix.M2::class.java -> return ClosestPointsMatrix.M2(channel, mode, exponent, 2)
                    ClosestPointsMatrix.M3::class.java -> return ClosestPointsMatrix.M3(channel, mode, exponent, 2)
                    ClosestPointsMatrix.M4::class.java -> return ClosestPointsMatrix.M4(channel, mode, exponent, 2)
                    ClosestPointsMatrix.M5::class.java -> return ClosestPointsMatrix.M5(channel, mode, exponent, 2)
                    else -> throw IllegalArgumentException("Unknown format for matrix data.")
                }
            }
        }
    }

    private fun <M : Matrix<*>> useMatrix(matrix: M, codeBlock: (M) -> Unit) {
        try {
            codeBlock(matrix)
        } finally {
            matrix.close()
        }
    }
}