package com.grimfox.gec

import com.grimfox.gec.ui.widgets.Block
import com.grimfox.gec.util.ObservableMutableReference
import com.grimfox.gec.util.MutableReference
import com.grimfox.gec.util.call
import com.grimfox.gec.util.ref
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService

class ToggleSet(val executor: ExecutorService) {

    private val currentPointer = ref<MutableReference<Boolean>?>(null)
    private val latch = ref<CountDownLatch?>(null)
    private var enabled = true

    private fun disableCurrentToggleIfEnabled() {
        val currentToggle = currentPointer.value
        if (currentToggle != null) {
            val waiter = CountDownLatch(1)
            latch.value = waiter
            currentToggle.value = false
            while (waiter.count > 0) {
                try {
                    waiter.await()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            latch.value = null
        }
    }

    fun disable() {
        disableCurrentToggleIfEnabled()
        enabled = false
    }

    fun enable() {
        enabled = true
    }

    fun suspend(work: () -> Unit) {
        disable()
        work()
        enable()
    }

    fun add(reference: ObservableMutableReference<Boolean>, onToggleOn: () -> Boolean, onToggleOff: () -> Unit, vararg dependents: Block) {
        var toggleActivated = false
        reference.addListener { old, new ->
            dependents.forEach {
                it.isMouseAware = new
                it.isVisible = new
            }
            if (old != new) {
                if (enabled) {
                    executor.call<Unit> {
                        if (new) {
                            disableCurrentToggleIfEnabled()
                            currentPointer.value = reference
                            if (onToggleOn()) {
                                toggleActivated = true
                            } else {
                                toggleActivated = false
                                if (currentPointer.value == reference) {
                                    currentPointer.value = null
                                }
                                reference.value = false
                            }
                        } else {
                            if (toggleActivated) {
                                onToggleOff()
                                toggleActivated = false
                                if (currentPointer.value == reference) {
                                    currentPointer.value = null
                                }
                            }
                            latch.value?.countDown()
                        }
                    }
                } else {
                    reference.value = false
                }
            }
        }
        reference.value = false
    }
}