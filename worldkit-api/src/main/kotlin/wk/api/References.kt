package wk.api

import java.io.File
import java.lang.IllegalArgumentException
import java.util.ArrayList
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

typealias Listener<T> = (oldValue: T, newValue: T) -> Unit
typealias NullListener<T> = (oldValue: T?, newValue: T) -> Unit

interface Reference<out T> {

    val value: T

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T
}

interface MutableReference<T> : Reference<T> {

    override var value: T

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T)
}

interface ObservableReference<T> : Reference<T> {

    val listeners: List<(oldValue: T, newValue: T) -> Unit>

    override val value: T

    fun addListener(listener: Listener<T>): ObservableReference<T>

    fun addNullListener(listener: NullListener<T>): ObservableReference<T> = addListener(listener)

    fun removeListener(listener: Listener<T>): Boolean
}

interface ObservableMutableReference<T> : ObservableReference<T>, MutableReference<T> {

    override val listeners: List<Listener<T>>

    override var value: T

    override fun addListener(listener: Listener<T>): ObservableMutableReference<T>

    override fun removeListener(listener: Listener<T>): Boolean
}

private class CRef<out T>(override val value: T) : Reference<T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

private class MRef<T>(override var value: T) : MutableReference<T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

private class Ref<T>(value: T) : ObservableMutableReference<T> {

    override val listeners: MutableList<Listener<T>> = ArrayList()

    private var _value: T = value
    override var value: T
        get() = _value
        set(value) {
            val old = _value
            _value = value
            listeners.forEach { it(old, value) }
        }

    override fun addListener(listener: Listener<T>): ObservableMutableReference<T> {
        listeners.add(listener)
        return this
    }

    override fun removeListener(listener: Listener<T>): Boolean {
        return listeners.remove(listener)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

private class LazyRef<T : Any> : ObservableMutableReference<T> {

    override val listeners: MutableList<Listener<T>> = ArrayList()
    private val nListeners: MutableList<NullListener<T>> = ArrayList()

    private var _value: T? = null
    override var value: T
        get() = _value ?: throw UninitializedPropertyAccessException()
        set(value) {
            val old = _value
            _value = value
            if (old == null) {
                nListeners.forEach { it(old, value) }
            } else {
                listeners.forEach { it(old, value) }
            }
        }

    override fun addListener(listener: Listener<T>): ObservableMutableReference<T> {
        listeners.add(listener)
        return this
    }

    override fun addNullListener(listener: NullListener<T>): ObservableMutableReference<T> {
        nListeners.add(listener)
        listeners.add(listener)
        return this
    }

    override fun removeListener(listener: Listener<T>): Boolean {
        nListeners.remove(listener)
        return listeners.remove(listener)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

private class EvalRef<T>(private val get: () -> T, private val set: (T) -> Unit) : ObservableMutableReference<T> {

    override var value
        get() = get()
        set(value) {
            val old = this.value
            set(value)
            listeners.forEach { it(old, value) }
        }

    override val listeners: MutableList<Listener<T>> = ArrayList()

    override fun addListener(listener: Listener<T>): ObservableMutableReference<T> {
        listeners.add(listener)
        return this
    }

    override fun removeListener(listener: Listener<T>): Boolean {
        return listeners.remove(listener)
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

private class ImmutableRef<T>(override val value: T) : ObservableReference<T> {

    override val listeners: List<(oldValue: T, newValue: T) -> Unit> = emptyList()

    override fun addListener(listener: Listener<T>): ObservableReference<T> {
        return this
    }

    override fun removeListener(listener: Listener<T>): Boolean {
        return true
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
}

fun <T> cRef(value: T): Reference<T> {
    return CRef(value)
}

fun <T> mRef(value: T): MutableReference<T> {
    return MRef(value)
}

fun <T : Any> ref(): ObservableMutableReference<T> {
    return LazyRef()
}

fun <T> ref(value: T): ObservableMutableReference<T> {
    return Ref(value)
}

fun <T> ref(get: () -> T): ObservableMutableReference<T> {
    return EvalRef(get, {})
}

fun <T> ref(get: () -> T, set: (T) -> Unit): ObservableMutableReference<T> {
    return EvalRef(get, set)
}

fun <T : Any> after(waitFor: KProperty0<*>, eval: () -> T): ObservableMutableReference<T> {
    waitFor.isAccessible = true
    if (waitFor.getDelegate() !is ObservableReference<*>) {
        throw IllegalArgumentException("Property to wait for must be backed by an ObservableReference.")
    }
    val lazyRef = LazyRef<T>()
    (waitFor.getDelegate() as ObservableReference<*>).addNullListener { _, _ -> lazyRef.value = eval() }
    return lazyRef
}

fun dir(path: String): String {
    val file = File(path)
    file.mkdirs()
    return file.absolutePath
}

fun dir(get: () -> String): ObservableMutableReference<String> {
    return ref { dir(get()) }
}

fun dir(dependsOn: KProperty0<*>, path: () -> String): ObservableMutableReference<String> {
    return after(dependsOn) { dir(path()) }
}

fun <T> iRef(value: T): ObservableReference<T> {
    return ImmutableRef(value)
}
