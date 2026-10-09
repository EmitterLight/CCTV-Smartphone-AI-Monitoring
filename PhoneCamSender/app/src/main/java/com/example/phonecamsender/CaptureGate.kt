package com.example.phonecamsender

/** Capture is allowed when the service owns the camera and the user did not stop it. */
class CaptureGate {
    @Volatile var serviceRunning: Boolean = false
    @Volatile var userStopped: Boolean = false

    fun allowCapture(): Boolean = serviceRunning && !userStopped
}
