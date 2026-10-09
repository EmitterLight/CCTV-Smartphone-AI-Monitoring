package com.example.phonecamsender

import kotlin.math.min

/** Capped exponential reconnect delay; resets after a successful upload. */
class ReconnectPlanner(val baseMs: Long, val maxMs: Long) {
    init { require(baseMs > 0 && maxMs >= baseMs) }

    var attempts: Int = 0
        private set

    fun nextDelayMs(): Long {
        var delay = baseMs
        repeat(attempts) { delay = min(delay * 2, maxMs) }
        attempts++
        return delay
    }

    fun reset() {
        attempts = 0
    }
}
