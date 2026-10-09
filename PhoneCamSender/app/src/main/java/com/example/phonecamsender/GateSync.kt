package com.example.phonecamsender

/** Maps the settings screen to the gate; a settings change never restarts the camera. */
object GateSync {
    fun apply(gate: CaptureGate, userStopped: Boolean) {
        gate.userStopped = userStopped
    }
}
