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

inline fun <R, T1 : AutoCloseable> twr(resource: T1, block: CloseableResources.(T1) -> R): R {
    val resources = CloseableResources(listOf(resource))
    resources.use {
        return it.block(resource)
    }
}

fun <T> ExecutorService.call(callable: () -> T): Future<T> = submit<T>(callable)

val <T> Future<T>.value: T get() = get()

fun Future<*>.join() {
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
