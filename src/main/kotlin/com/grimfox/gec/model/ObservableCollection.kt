package com.grimfox.gec.model

interface ObservableCollection<T> {

    class ModificationEvent<out E>(val type: Type, val newElement: E? = null, val oldElement: E? = null, val newElements: Collection<E>? = null, val oldElements: Collection<E>? = null, val changed: Boolean = false, val index: Int? = null) {

        enum class Type { ADD, REMOVE, REPLACE, REORDER, CLEAR }
    }

    fun addListener(listener: (ModificationEvent<T?>) -> Unit): ObservableCollection<T>

    fun removeListener(listener: (ModificationEvent<T?>) -> Unit): Boolean
}