package com.grimfox.gec.model

import com.grimfox.gec.model.ObservableCollection.ModificationEvent
import com.grimfox.gec.model.ObservableCollection.ModificationEvent.Type.*
import java.util.Comparator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate
import java.util.function.UnaryOperator

class ObservableMutableList<T>(private val delegate: MutableList<T>, private val offset: Int = 0, private val listeners: MutableList<(ModificationEvent<T?>) -> Unit> = CopyOnWriteArrayList()) : ObservableCollection<T>, MutableList<T> by delegate {

    private class ObservableMutableListIterator<T>(private val delegate: MutableListIterator<T>, private val listeners: MutableList<(ModificationEvent<T?>) -> Unit> = CopyOnWriteArrayList()) : MutableListIterator<T> by delegate {

        private var current: T? = null

        override fun next(): T {
            val current = delegate.next()
            this.current = current
            return current
        }

        override fun add(element: T) {
            delegate.add(element)
            sendAddEvent(element)
        }

        override fun remove() {
            delegate.remove()
            sendRemoveEvent(current)
        }

        override fun set(element: T) {
            delegate.set(element)
            sendReplaceEvent(element, current)
            current = element
        }

        private fun sendAddEvent(newElement: T) {
            val event = ModificationEvent(ModificationEvent.Type.ADD, newElement = newElement, changed = true)
            listeners.forEach { it(event) }
        }

        private fun sendRemoveEvent(oldElement: T?) {
            val event = ModificationEvent(ModificationEvent.Type.REMOVE, oldElement = oldElement, changed = true)
            listeners.forEach { it(event) }
        }

        private fun sendReplaceEvent(newElement: T?, oldElement: T?) {
            val event = ModificationEvent(ModificationEvent.Type.REMOVE, newElement = newElement, oldElement = oldElement, changed = true)
            listeners.forEach { it(event) }
        }
    }

    override fun addListener(listener: (ModificationEvent<T?>) -> Unit): ObservableMutableList<T> {
        listeners.add(listener)
        return this
    }

    override fun removeListener(listener: (ModificationEvent<T?>) -> Unit): Boolean {
        return listeners.remove(listener)
    }

    override fun add(element: T): Boolean {
        return sendAddEvent(element, delegate.add(element))
    }

    override fun add(index: Int, element: T) {
        sendAddEvent(element, delegate.add(element), index)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        return sendAddAllEvent(elements, delegate.addAll(index, elements), index)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        return sendAddAllEvent(elements, delegate.addAll(elements))
    }

    override fun clear() {
        delegate.clear()
        sendClearEvent()
    }

    override fun remove(element: T): Boolean {
        return sendRemoveEvent(element, delegate.remove(element))
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        return sendRemoveAllEvent(elements, delegate.removeAll(elements))
    }

    override fun removeAt(index: Int): T {
        return sendRemoveAtEvent(delegate.removeAt(index), index)
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        return sendRemoveIfEvent(delegate.retainAll(elements))
    }

    override fun set(index: Int, element: T): T {
        return sendReplaceEvent(element, delegate.set(index, element), index)
    }

    override fun sort(c: Comparator<in T>) {
        delegate.sortWith(c)
        sendReorderEvent()
    }

    override fun replaceAll(operator: UnaryOperator<T>) {
        delegate.replaceAll(operator)
        sendReplaceAllEvent()
    }

    override fun removeIf(filter: Predicate<in T>): Boolean {
        return sendRemoveIfEvent(delegate.removeIf(filter))
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return ObservableMutableList(delegate.subList(fromIndex, toIndex), fromIndex, listeners)
    }

    override fun listIterator(): MutableListIterator<T> {
        return ObservableMutableListIterator(delegate.listIterator(), listeners)
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        return ObservableMutableListIterator(delegate.listIterator(index), listeners)
    }

    private fun sendClearEvent() {
        val event = ModificationEvent<T>(CLEAR, changed = true)
        listeners.forEach { it(event) }
    }

    private fun sendReorderEvent() {
        val event = ModificationEvent<T>(REORDER, changed = true)
        listeners.forEach { it(event) }
    }

    private fun sendAddEvent(newElement: T, changed: Boolean): Boolean {
        val event = ModificationEvent(ADD, newElement = newElement, changed = changed)
        listeners.forEach { it(event) }
        return changed
    }

    private fun sendAddEvent(newElement: T, changed: Boolean, index: Int): Boolean {
        val event = ModificationEvent(ADD, newElement = newElement, changed = changed, index = index + offset)
        listeners.forEach { it(event) }
        return changed
    }

    private fun sendAddAllEvent(newElements: Collection<T>, changed: Boolean): Boolean {
        val event = ModificationEvent(ADD, newElements = newElements, changed = changed)
        listeners.forEach { it(event) }
        return changed
    }

    private fun sendAddAllEvent(newElements: Collection<T>, changed: Boolean, index: Int): Boolean {
        val event = ModificationEvent(ADD, newElements = newElements, changed = changed, index = index + offset)
        listeners.forEach { it(event) }
        return changed
    }

    private fun sendRemoveEvent(oldElement: T, changed: Boolean): Boolean {
        val event = ModificationEvent(REMOVE, oldElement = oldElement, changed = changed)
        listeners.forEach { it(event) }
        return changed
    }

    private fun sendRemoveAllEvent(oldElements: Collection<T>, changed: Boolean): Boolean {
        val event = ModificationEvent(REMOVE, oldElements = oldElements, changed = changed)
        listeners.forEach { it(event) }
        return changed
    }

    private fun sendRemoveAtEvent(oldElement: T, index: Int): T {
        val event = ModificationEvent(REMOVE, oldElement = oldElement, changed = true, index = index + offset)
        listeners.forEach { it(event) }
        return oldElement
    }

    private fun sendRemoveIfEvent(changed: Boolean): Boolean {
        val event = ModificationEvent<T>(REMOVE, changed = changed)
        listeners.forEach { it(event) }
        return changed
    }

    private fun sendReplaceEvent(newElement: T, oldElement: T, index: Int): T {
        val event = ModificationEvent(REPLACE, newElement = newElement, oldElement = oldElement, changed = true, index = index + offset)
        listeners.forEach { it(event) }
        return oldElement
    }

    private fun sendReplaceAllEvent() {
        val event = ModificationEvent<T>(REPLACE, changed = true)
        listeners.forEach { it(event) }
    }
}