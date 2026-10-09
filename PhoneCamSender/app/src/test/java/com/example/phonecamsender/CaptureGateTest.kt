package com.example.phonecamsender

import org.junit.Assert.*
import org.junit.Test

class CaptureGateTest {
    @Test fun serviceRunningWithoutActivityAllowsCapture() {
        val gate = CaptureGate()
        assertFalse(gate.allowCapture())
        gate.serviceRunning = true
        assertTrue(gate.allowCapture())
    }

    @Test fun userStopWinsOverRunningService() {
        val gate = CaptureGate()
        gate.serviceRunning = true
        gate.userStopped = true
        assertFalse(gate.allowCapture())
    }
}
