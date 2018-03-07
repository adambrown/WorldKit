package com.grimfox.triangle

import com.grimfox.triangle.geometry.Triangle
import java.util.*

class TrianglePool : Collection<Triangle> {

    companion object {

        private val BLOCK_SIZE = 1024
    }

    private var _size: Int = 0

    internal var count: Int = 0

    internal var pool: Array<Array<Triangle?>?>

    internal var stack: Stack<Triangle> = Stack()

    override val size: Int get() = count - stack.size

    init {
        val n = Math.max(1, 65536 / BLOCK_SIZE)
        pool = arrayOfNulls<Array<Triangle?>?>(n)
        pool[0] = arrayOfNulls<Triangle>(BLOCK_SIZE)
        stack = Stack<Triangle>()
    }

    fun get(): Triangle {
        val triangle: Triangle
        if (stack.size > 0) {
            triangle = stack.pop()
            triangle.hash = -triangle.hash - 1
            cleanup(triangle)
        } else if (count < _size) {
            triangle = pool[count / BLOCK_SIZE]!![count % BLOCK_SIZE]!!
            triangle.id = triangle.hash
            cleanup(triangle)
            count++
        } else {
            triangle = Triangle()
            triangle.hash = _size
            triangle.id = triangle.hash
            val block = _size / BLOCK_SIZE
            if (pool[block] == null) {
                pool[block] = arrayOfNulls<Triangle>(BLOCK_SIZE)
                if (block + 1 == pool.size) {
                    pool = Arrays.copyOf(pool, 2 * pool.size)
                }
            }
            pool[block]!![_size % BLOCK_SIZE] = triangle
            count = ++_size
        }
        return triangle
    }

    fun release(triangle: Triangle) {
        stack.push(triangle)
        triangle.hash = -triangle.hash - 1
    }

    fun restart(): TrianglePool {
        for (triangle in stack) {
            triangle.hash = -triangle.hash - 1
        }
        stack.clear()
        count = 0
        return this
    }

    fun sample(k: Int, random: Random): Iterable<Triangle> {
        val outerK = k
        return object : Iterable<Triangle> {
            override fun iterator(): Iterator<Triangle> {
                return object : Iterator<Triangle> {
                    private var remaining = if (outerK > count) count else outerK
                    private var next: Triangle? = null
                    private var hasNext: Boolean = false

                    init {
                        advanceNext()
                    }

                    private fun advanceNext() {
                        if (remaining > 0) {
                            while (true) {
                                val i = random.next(0, count)
                                val t = pool[i / BLOCK_SIZE]?.get(i % BLOCK_SIZE)
                                if (t?.hash ?: -1 >= 0) {
                                    remaining--
                                    next = t
                                    hasNext = t != null
                                    break
                                }
                            }
                        } else {
                            next = null
                            hasNext = false
                        }
                    }

                    override fun hasNext() = hasNext
                    override fun next(): Triangle {
                        val temp = next!!
                        advanceNext()
                        return temp
                    }
                }
            }
        }
    }

    private fun cleanup(triangle: Triangle) {
        triangle.label = 0
        triangle.area = 0.0
        triangle.infected = false
        for (i in 0..2) {
            triangle.vertices[i] = null
            triangle.subsegs[i].reset()
            triangle.neighbors[i].reset()
        }
    }

    override fun containsAll(elements: Collection<Triangle>): Boolean {
        elements.forEach {
            if (!contains(it)) {
                return false
            }
        }
        return true
    }

    override fun contains(element: Triangle): Boolean {
        val i = element.hash
        if (i < 0 || i > _size) {
            return false
        }
        return pool[i / BLOCK_SIZE]?.get(i % BLOCK_SIZE)?.hash ?: -1 >= 0
    }

    internal class TrianglePoolIterator(pool: TrianglePool) : Iterator<Triangle> {
        var count: Int = 0
        var pool: Array<Array<Triangle?>?>
        var current: Triangle? = null
        var index: Int = 0
        var offset: Int = 0

        init {
            this.count = pool.size
            this.pool = pool.pool
            index = 0
            offset = 0
            advanceNext()
        }

        private var hasNext = false
        private var next: Triangle? = null
        private fun advanceNext() {
            try {
                hasNext = moveNext()
                if (hasNext)
                    next = current
            } catch (e: Exception) {
                hasNext = false
            }
        }

        override fun hasNext(): Boolean {
            return hasNext
        }

        override fun next(): Triangle {
            if (!hasNext) {
                throw NoSuchElementException()
            }
            val ret = next
            this.advanceNext()
            return ret!!
        }

        fun moveNext(): Boolean {
            while (index < count) {
                current = pool[offset / BLOCK_SIZE]?.get(offset % BLOCK_SIZE)
                offset++
                if (current?.hash ?: -1 >= 0) {
                    index++
                    return true
                }
            }
            return false
        }

        fun reset() {
            offset = 0
            index = offset
        }
    }

    override fun iterator(): Iterator<Triangle> {
        return TrianglePoolIterator(this)
    }

    override fun isEmpty(): Boolean {
        return size == 0
    }
}
