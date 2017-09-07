package com.grimfox.gec

import com.grimfox.gec.ui.widgets.Block
import java.util.ArrayList
import java.util.concurrent.locks.ReentrantLock

class DisableSetLock {

    private val lock = ReentrantLock()
    private var isLocked = false
    private val disableSets = ArrayList<DisableSet>()

    fun disableOnLockButton(block: Block, label: String, disableCondition: () -> Boolean = { false }, onClick: () -> Unit = {}) {
        disableSets.add(block.disableButton(label, disableCondition, onClick))
    }

    fun disableOnLockSet(selector: () -> Int, vararg sets: DisableSet) {
        disableSets.add(disableSet(selector, *sets))
    }

    fun disable() {
        disableSets.forEach(DisableSet::disable)
    }

    fun enable() {
        disableSets.forEach(DisableSet::enable)
    }

    fun doWithLock(doWork: () -> Unit) {
        if (lock.tryLock()) {
            try {
                if (!isLocked) {
                    isLocked = true
                    try {
                        disable()
                        try {
                            doWork()
                        } finally {
                            enable()
                        }
                    } finally {
                        isLocked = false
                    }
                }
            } finally {
                lock.unlock()
            }
        }
    }

    fun lock() {
        if (lock.tryLock()) {
            try {
                if (!isLocked) {
                    isLocked = true
                    disable()
                }
            } finally {
                lock.unlock()
            }
        }
    }

    fun unlock() {
        if (lock.tryLock()) {
            try {
                if (isLocked) {
                    enable()
                    isLocked = false
                }
            } finally {
                lock.unlock()
            }
        }
    }
}