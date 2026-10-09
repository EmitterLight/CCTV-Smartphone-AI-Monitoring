package com.example.phonecamsender

import org.junit.Assert.*
import org.junit.Test

class ReconnectPlannerTest {
    @Test fun cappedBackoffAndResetAfterSuccess() {
        val p = ReconnectPlanner(2000L, 30000L)
        assertEquals(2000L, p.nextDelayMs())
        assertEquals(4000L, p.nextDelayMs())
        assertEquals(8000L, p.nextDelayMs())
        p.reset()
        assertEquals(2000L, p.nextDelayMs())
        assertEquals(1, p.attempts)
        val big = ReconnectPlanner(2000L, 30000L)
        repeat(6) { big.nextDelayMs() }
        assertEquals(30000L, big.nextDelayMs())
    }

    @Test fun invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReconnectPlanner(0L, 30000L) }
        assertThrows(IllegalArgumentException::class.java) { ReconnectPlanner(2000L, 1000L) }
    }
}
