package wk.internal.ext

import cern.colt.list.IntArrayList
import java.lang.UnsupportedOperationException
import kotlin.math.log2

fun IntArrayList.toIntList(): WkIntList = IntList(this)

fun IntArrayList.toMutableIntList(): WkMutableIntList = MutableIntList(this)

fun intList(initialSize: Int = 10): WkMutableIntList = MutableIntList(IntArrayList(initialSize))

fun intListOf(vararg ints: Int): WkMutableIntList = MutableIntList(IntArrayList(ints))

interface IntListWrapper {
    val delegate: IntArrayList
}

interface WkIntList : IntListWrapper, List<Int>

interface WkMutableIntList : WkIntList, MutableList<Int>

internal open class IntList(override val delegate: IntArrayList): WkIntList {
    override val size: Int get() = delegate.size()

    override fun contains(element: Int): Boolean {
        return delegate.contains(element)
    }

    override fun containsAll(elements: Collection<Int>): Boolean {
        elements.forEach {
            if (!delegate.contains(it)) {
                return false
            }
        }
        return true
    }

    override fun get(index: Int): Int {
        return delegate.getQuick(index)
    }

    override fun indexOf(element: Int): Int {
        return delegate.indexOf(element)
    }

    override fun isEmpty(): Boolean {
        return size == 0
    }

    override fun iterator(): Iterator<Int> {
        return IntIterator(delegate)
    }

    override fun lastIndexOf(element: Int): Int {
        return delegate.lastIndexOf(element)
    }

    override fun listIterator(): ListIterator<Int> {
        return listIterator(0)
    }

    override fun listIterator(index: Int): ListIterator<Int> {
        return IntListIterator(delegate, index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<Int> {
        throw UnsupportedOperationException()
    }
}

private open class IntIterator(protected val delegate: IntArrayList, protected var pointer: Int = 0) : Iterator<Int> {

    protected var last = -1

    override fun hasNext(): Boolean {
        return pointer < delegate.size()
    }

    override fun next(): Int {
        last = pointer
        val tmp = delegate.getQuick(pointer)
        pointer += 1
        return tmp
    }
}

private class MIntIterator(delegate: IntArrayList, pointer: Int = 0) : IntIterator(delegate, pointer), MutableIterator<Int> {

    override fun remove() {
        if (last in 0 until delegate.size()) {
            delegate.remove(last)
            pointer = last
            last = -1
        }
    }
}

private open class IntListIterator(protected val delegate: IntArrayList, protected var pointer: Int = 0) : ListIterator<Int> {

    protected var last = -1

    override fun hasNext(): Boolean {
        return pointer < delegate.size()
    }

    override fun next(): Int {
        last = pointer
        val tmp = delegate.getQuick(pointer)
        pointer += 1
        return tmp
    }

    override fun hasPrevious(): Boolean {
        return pointer > 0
    }

    override fun nextIndex(): Int {
        return pointer
    }

    override fun previous(): Int {
        pointer -= 1
        last = pointer
        return delegate.getQuick(pointer)
    }

    override fun previousIndex(): Int {
        return pointer - 1
    }
}

private class MIntListIterator(delegate: IntArrayList, pointer: Int = 0) : IntListIterator(delegate, pointer), MutableListIterator<Int> {

    override fun add(element: Int) {
        delegate.beforeInsert(pointer, element)
        if (last >= pointer) {
            last += 1
        }
        pointer += 1
    }

    override fun remove() {
        if (last in 0 until delegate.size()) {
            delegate.remove(last)
            pointer = last
            last = -1
        }
    }

    override fun set(element: Int) {
        if (last in 0 until delegate.size()) {
            delegate.set(last, element)
        }
    }
}


internal class MutableIntList(delegate: IntArrayList): IntList(delegate), WkMutableIntList {

    override fun iterator(): MutableIterator<Int> {
        return MIntIterator(delegate)
    }

    override fun listIterator(): MutableListIterator<Int> {
        return listIterator(0)
    }

    override fun listIterator(index: Int): MutableListIterator<Int> {
        return MIntListIterator(delegate, index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<Int> {
        throw UnsupportedOperationException()
    }

    override fun add(element: Int): Boolean {
        delegate.add(element)
        return true
    }

    override fun add(index: Int, element: Int) {
        delegate.beforeInsert(index, element)
    }

    override fun addAll(index: Int, elements: Collection<Int>): Boolean {
        delegate.beforeInsertAllOf(index, elements)
        return true
    }

    override fun addAll(elements: Collection<Int>): Boolean {
        delegate.addAllOf(elements)
        return true
    }

    override fun clear() {
        delegate.clear()
    }

    override fun remove(element: Int): Boolean {
        val i = delegate.indexOf(element)
        if (i == -1) {
            return false
        }
        delegate.remove(i)
        return true
    }

    override fun removeAll(elements: Collection<Int>): Boolean {
        var any = false
        elements.forEach {
            any = any || remove(it)
        }
        return any
    }

    override fun removeAt(index: Int): Int {
        val tmp = get(index)
        delegate.remove(index)
        return tmp
    }

    override fun retainAll(elements: Collection<Int>): Boolean {
        val lastIndex: Int = elements.size - 1
        val rawData = delegate.elements()
        val size = delegate.size()
        val searchSize = elements.size.toDouble()
        var insertAt = 0
        if ((searchSize + size) * log2(searchSize) < size * searchSize) {
            val var10 = IntArrayList(elements.toIntArray())
            var10.quickSort()
            for (i in 0 until size) {
                if (var10.binarySearchFromTo(rawData[i], 0, lastIndex) >= 0) {
                    rawData[insertAt++] = rawData[i]
                }
            }
        } else {
            for (i in 0 until size) {
                if (elements.contains(rawData[i])) {
                    rawData[insertAt++] = rawData[i]
                }
            }
        }
        delegate.setSize(insertAt)
        return insertAt != size
    }

    override fun set(index: Int, element: Int): Int {
        val tmp = delegate.getQuick(index)
        delegate.setQuick(index, element)
        return tmp
    }
}
