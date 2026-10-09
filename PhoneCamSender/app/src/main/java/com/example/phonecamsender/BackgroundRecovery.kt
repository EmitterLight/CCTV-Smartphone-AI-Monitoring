package com.example.phonecamsender

/** Decides when a stale last-upload timestamp means the saved server must be re-verified. */
class BackgroundRecovery(val staleAfterMs: Long) {
    init { require(staleAfterMs > 0) }

    fun shouldRestart(lastUploadSuccessMs: Long, nowMs: Long): Boolean {
        return nowMs - lastUploadSuccessMs >= staleAfterMs
    }
}
