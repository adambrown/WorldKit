package com.grimfox.triangle

import java.util.*

fun Random.next(min: Int, max: Int): Int {
    return nextInt(max - min) + min
}