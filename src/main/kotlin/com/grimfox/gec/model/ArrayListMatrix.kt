package com.grimfox.gec.model

import com.grimfox.gec.util.Utils.pow
import com.grimfox.gec.util.Utils.init
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import java.util.*

class ArrayListMatrix<T>(override val width: Int, init: (Int) -> T = { null as T }) : Matrix<T> {

    override val exponent: Int get() = throw NotImplementedException()
    override val size = width.toLong().pow(2)

    val list = ArrayList<T>(size.toInt()).init(size.toInt(), init)

    override fun set(i: Int, value: T) {
        list[i] = value
    }

    override fun get(i: Int): T {
        return list[i]
    }

    override fun close() {
        list.clear()
        list.trimToSize()
    }

}