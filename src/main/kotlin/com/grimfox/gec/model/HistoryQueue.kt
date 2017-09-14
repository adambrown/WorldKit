package com.grimfox.gec.model

import com.grimfox.gec.util.Quadruple
import com.grimfox.gec.util.Quintuple
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class HistoryQueue<T>(val limit: Int) {

    companion object {

        fun <T> deserialize(bufferData: List<T?>, head: Int, tail: Int, size: Int, limit: Int): HistoryQueue<T> {
            val newQueue = HistoryQueue<T>(limit)
            newQueue.buffer.addAll(bufferData)
            newQueue.head = head
            newQueue.tail = tail
            newQueue._size = size
            return newQueue
        }
    }

    fun serializableData(): Quintuple<List<T?>, Int, Int, Int, Int> {
        return Quintuple(ArrayList(buffer), head, tail, _size, limit)
    }

    private val buffer = ArrayList<T?>(limit)

    private var head = 0
    private var tail = 0
    private var _size = 0
    private val lock = ReentrantLock()

    val size: Int get() {
        lock.lock()
        try {
            return _size
        } finally {
            lock.unlock()
        }
    }

    fun push(value: T) {
        lock.lock()
        try {
            if (_size < limit) {
                if (buffer.size < limit && tail == buffer.size) {
                    buffer.add(value)
                    tail = (tail + 1) % limit
                } else {
                    buffer[tail] = value
                    tail = (tail + 1) % limit
                }
                _size++
            } else {
                buffer[tail] = value
                head = (head + 1) % limit
                tail = (tail + 1) % limit
            }
        } finally {
            lock.unlock()
        }
    }

    fun pop(): T? {
        lock.lock()
        try {
            if (_size < 1) {
                return null
            }
            tail = ((tail - 1) + limit) % limit
            _size--
            val temp = buffer[tail]
            buffer[tail] = null
            return temp
        } finally {
            lock.unlock()
        }
    }

    fun peek(): T {
        lock.lock()
        try {
            if (_size < 1) {
                throw IllegalStateException("Calling peek on empty queue.")
            }
            return buffer[((tail - 1) + limit) % limit]!!
        } finally {
            lock.unlock()
        }
    }

    fun clear() {
        lock.lock()
        try {
            head = 0
            tail = 0
            _size = 0
            buffer.clear()
        } finally {
            lock.unlock()
        }
    }
}