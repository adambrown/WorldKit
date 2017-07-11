package com.grimfox.gec.util

import java.io.Serializable
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future


class CloseableResources(resources: List<AutoCloseable> = emptyList()) : AutoCloseable {

    private val resources = ArrayList(resources)

    fun <T : AutoCloseable> T.use(): T {
        resources.add(this)
        return this
    }

    override fun close() {
        resources.reversed().forEach { it.close() }
    }
}

inline fun <R> twr(block: CloseableResources.() -> R): R {
    val holder = CloseableResources()
    try {
        return holder.block()
    } finally {
        holder.close()
    }
}

inline fun <R, T1 : AutoCloseable> twr(resource1: T1, block: CloseableResources.(T1) -> R): R {
    val resources = CloseableResources(listOf(resource1))
    try {
        return resources.block(resource1)
    } finally {
        resources.close()
    }
}

inline fun <R, T1 : AutoCloseable, T2 : AutoCloseable> twr(resource1: T1, resource2: T2, block: CloseableResources.(T1, T2) -> R): R {
    val resources = CloseableResources(listOf(resource1, resource2))
    try {
        return resources.block(resource1, resource2)
    } finally {
        resources.close()
    }
}

inline fun <R, T1 : AutoCloseable, T2 : AutoCloseable, T3 : AutoCloseable> twr(resource1: T1, resource2: T2, resource3: T3, block: CloseableResources.(T1, T2, T3) -> R): R {
    val resources = CloseableResources(listOf(resource1, resource2, resource3))
    try {
        return resources.block(resource1, resource2, resource3)
    } finally {
        resources.close()
    }
}

inline fun <R, T1 : AutoCloseable, T2 : AutoCloseable, T3 : AutoCloseable, T4 : AutoCloseable> twr(resource1: T1, resource2: T2, resource3: T3, resource4: T4, block: CloseableResources.(T1, T2, T3, T4) -> R): R {
    val resources = CloseableResources(listOf(resource1, resource2, resource3, resource4))
    try {
        return resources.block(resource1, resource2, resource3, resource4)
    } finally {
        resources.close()
    }
}

fun <T> ExecutorService.call(callable: () -> T): Future<T> {
    return submit<T>(callable)
}

val <T> Future<T>.value: T get() = get()

fun Future<*>.join(): Unit {
    get()
}

data class Quadruple<out A, out B, out C, out D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
) : Serializable {

    override fun toString(): String = "($first, $second, $third, $fourth)"
}

fun <T> Quadruple<T, T, T, T>.toList(): List<T> = listOf(first, second, third, fourth)

data class Quintuple<out A, out B, out C, out D, out E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
) : Serializable {

    override fun toString(): String = "($first, $second, $third, $fourth)"
}

fun <T> Quintuple<T, T, T, T, T>.toList(): List<T> = listOf(first, second, third, fourth)
