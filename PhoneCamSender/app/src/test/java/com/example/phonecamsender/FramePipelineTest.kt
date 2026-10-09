package com.example.phonecamsender

import org.junit.Assert.*
import org.junit.Test

class FramePipelineTest {
    @Test fun intervalGateAndNoQueue() {
        val sent = mutableListOf<Int>()
        val gate = CaptureGate()
        gate.serviceRunning = true
        val slots = UploadSlots(2)
        var t = 0L
        val p = FramePipeline(gate, slots, 120, FrameSender { _, lease -> sent.add(1); lease.close() }, { t })

        t = 0L
        assertTrue(p.handle(640, 480))
        t = 50L
        assertFalse(p.handle(640, 480))
        t = 130L
        assertTrue(p.handle(640, 480))

        gate.userStopped = true
        assertFalse(p.handle(640, 480))
        assertEquals(2, sent.size)
        assertEquals(0, p.dropped)
    }

    @Test fun slotExhaustionDropsFrameInsteadOfQueueing() {
        val gate = CaptureGate()
        gate.serviceRunning = true
        val slots = UploadSlots(1)
        val p = FramePipeline(gate, slots, 0L, FrameSender { _, lease -> lease.close() }, { 0L })
        assertTrue(p.handle(640, 480))

        val busy = FramePipeline(gate, slots, 0L, FrameSender { _, lease -> }, { 0L })
        assertTrue(busy.handle(640, 480))
        assertFalse(busy.handle(640, 480))
        assertEquals(1, busy.dropped)
    }
}
