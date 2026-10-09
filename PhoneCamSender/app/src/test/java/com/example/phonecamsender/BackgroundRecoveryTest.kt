package com.example.phonecamsender

import org.junit.Assert.*
import org.junit.Test

class BackgroundRecoveryTest {
    @Test fun staleLastUploadTriggersServiceRestart() {
        val r = BackgroundRecovery(120_000)
        assertTrue(r.shouldRestart(0, 120_000))            // never uploaded
        assertFalse(r.shouldRestart(100_000, 150_000))     // recent success
        assertTrue(r.shouldRestart(100_000, 220_000))      // stale
    }
}
