package wk.internal.application

import java.util.ArrayList
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock

@Volatile
var cancelRequestCount = 0

@Volatile
var taskStartedAt = 0

@Volatile
var taskRunning = false

@Throws(CancellationException::class)
fun taskYield() { if (cancelRequestCount > taskStartedAt) throw CancellationException("User requested cancellation") }

fun <T> doWithCancellationSupport(task: () -> T): T {
    taskStartedAt = cancelRequestCount
    taskRunning = true
    try {
        return task()
    } finally {
        taskRunning = false
    }
}

class MainThreadTask<T>(private val latch: CountDownLatch? = null, val work: () -> T) {

    var returnVal: T? = null
    var error: Throwable? = null

    fun perform() {
        try {
            returnVal = work()
        } catch (t: Throwable) {
            error = t
        } finally {
            latch?.countDown()
        }
    }
}
object MainThread {

    private val drainLock = ReentrantLock()
    private val mainThreadTasks = LinkedBlockingQueue<MainThreadTask<*>>()

    fun performMainThreadTasks() {
        if (drainLock.tryLock()) {
            try {
                val count = mainThreadTasks.size
                if (count > 0) {
                    val sink = ArrayList<MainThreadTask<*>>(count)
                    mainThreadTasks.drainTo(sink)
                    sink.forEach(MainThreadTask<*>::perform)
                }
            } finally {
                drainLock.unlock()
            }
        }
    }

    fun doOnMainThread(callable: () -> Unit) {
        mainThreadTasks.put(MainThreadTask(null, callable))
    }
}
