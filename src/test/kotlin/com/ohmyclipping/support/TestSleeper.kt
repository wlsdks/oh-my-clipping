package com.ohmyclipping.support

import java.util.concurrent.TimeUnit

object TestSleeper {

    fun sleep(delayMs: Long, context: String) {
        if (delayMs <= 0) return
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AssertionError("Test sleep interrupted: $context", exception)
        }
    }
}
