package com.example.phonecamsender

/**
 * Receives the acquired slot and does the encoding + upload. Encoding is the
 * caller's job, not the pipeline's, so the pipeline stays JVM-testable.
 * The first argument is the frame width the caller passed to handle().
 */
fun interface FrameSender {
    fun send(width: Int, slot: UploadSlots.Lease)
}

/** Frame decision order: gate -> interval -> slot -> sender. Never queues. */
class FramePipeline(
    val gate: CaptureGate,
    val slots: UploadSlots,
    val intervalMs: Long,
    val sender: FrameSender,
    val clock: () -> Long
) {
    init { require(intervalMs >= 0) }

    @Volatile var sent: Int = 0
        private set
    @Volatile var dropped: Int = 0
        private set

    private var lastSentTs = -1L

    fun handle(width: Int, height: Int): Boolean {
        if (!gate.allowCapture()) return false
        val now = clock()
        if (lastSentTs >= 0 && now - lastSentTs < intervalMs) return false
        lastSentTs = now

        val slot = slots.tryAcquire()
        if (slot == null) {
            dropped++
            return false
        }
        sent++
        sender.send(width, slot)
        return true
    }
}
