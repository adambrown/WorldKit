package wk.api

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val timers = ConcurrentHashMap<String, AtomicLong>()

@PublicApi
fun timer(name: String): AtomicLong = timers.getOrPut(name) { AtomicLong(0) }

@PublicApi
fun printTimers() {
    val stringBuilder = StringBuilder("timers:\n")
    timers.toList().sortedByDescending { it.second.get() }.forEach { (name, time) ->
        stringBuilder.append("  $name: ${(time.get() / 1000000) / 1000.0f}s\n")
    }
    print(stringBuilder)
}

@PublicApi
fun clearTimers() = timers.clear()

@PublicApi
inline fun <reified T> timeIt(accumulator: AtomicLong, callback: () -> T): T {
    return timeIt(null, accumulator, callback)
}

@PublicApi
inline fun <reified T> timeIt(message: String, callback: () -> T): T {
    return timeIt(message, null, callback)
}

@PublicApi
inline fun <reified T> timeIt(outRef: MutableReference<String>, callback: () -> T): T {
    val time = System.nanoTime()
    val ret = callback()
    val totalNano = System.nanoTime() - time
    outRef.value = "${(totalNano / 1000000) / 1000.0f}s"
    return ret
}

@PublicApi
inline fun <reified T> timeIt(message: String?, accumulator: AtomicLong?, callback: () -> T): T {
    val time = System.nanoTime()
    val ret = callback()
    val totalNano = System.nanoTime() - time
    if (message != null) {
        println("$message: ${(totalNano / 1000000) / 1000.0f}s")
    }
    accumulator?.addAndGet(totalNano)
    return ret
}
