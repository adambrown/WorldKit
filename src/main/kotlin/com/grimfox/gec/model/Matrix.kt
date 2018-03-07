package com.grimfox.gec.model

interface Matrix<T> {

    val width: Int
    val size: Long

    operator fun set(x: Int, y: Int, value: T) {
        set(y * width + x, value)
    }

    operator fun get(x: Int, y: Int): T {
        return get(y * width + x)
    }

    operator fun set(i: Int, value: T)

    operator fun get(i: Int): T

    fun use(codeBlock: (Matrix<T>) -> Unit) {
        try {
            codeBlock(this)
        } finally {
            close()
        }
    }

    fun close()
}