package com.example.phonecamsender

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureServiceTest {
    @Test fun serviceStartsForegroundAndKeepsGateOpenWhenNoActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gate = CaptureGate()

        CaptureService.start(context, gate)
        assertTrue(gate.serviceRunning)
        // No activity is in the foreground here; capture must still be allowed.
        assertTrue(gate.allowCapture())

        CaptureService.stop(context)
        assertFalse(gate.serviceRunning)
        assertFalse(gate.allowCapture())
    }
}
