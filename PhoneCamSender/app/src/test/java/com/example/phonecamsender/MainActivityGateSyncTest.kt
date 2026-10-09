package com.example.phonecamsender

import org.junit.Assert.*
import org.junit.Test

class MainActivityGateSyncTest {
    @Test fun settingsChangeUpdatesGateWithoutRestartingCamera() {
        val gate = CaptureGate()
        gate.serviceRunning = true

        GateSync.apply(gate, userStopped = true)
        assertFalse(gate.allowCapture())

        GateSync.apply(gate, userStopped = false)
        assertTrue(gate.allowCapture())
    }
}
