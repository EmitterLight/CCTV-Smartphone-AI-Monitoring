package com.example.phonecamsender

import androidx.lifecycle.Lifecycle
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

    @Test fun serviceLifecycleReachesStartedSoCameraXCanAttachUseCases() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CaptureService.start(context, CaptureGate())

        val service = awaitCurrent(3000)
        assertNotNull("service not created", service)
        // CameraX attaches the analyzer only while the owner is at least STARTED.
        assertEquals(Lifecycle.State.STARTED, awaitState(service!!, Lifecycle.State.STARTED, 3000))

        CaptureService.stop(context)
        assertEquals(Lifecycle.State.DESTROYED, awaitState(service, Lifecycle.State.DESTROYED, 3000))
    }

    private fun awaitCurrent(timeoutMs: Long): CaptureService? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (CaptureService.current() == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return CaptureService.current()
    }

    private fun awaitState(service: CaptureService, expected: Lifecycle.State, timeoutMs: Long): Lifecycle.State {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (service.lifecycle.currentState != expected && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return service.lifecycle.currentState
    }
}
