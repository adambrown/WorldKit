package com.grimfox.gec.extensions

import java.util.*


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
