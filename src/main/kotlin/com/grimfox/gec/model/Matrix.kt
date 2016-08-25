package com.grimfox.gec.model

interface Matrix<T> {

    val exponent: Int
    val width: Int
    val size: Long

    operator fun set(x: Int, y: Int, value: T)

    operator fun get(x: Int, y: Int): T

    fun use(codeBlock: (Matrix<T>) -> Unit) {
        try {
            codeBlock(this)
        } finally {
            close()
        }
    }

    fun close()
}