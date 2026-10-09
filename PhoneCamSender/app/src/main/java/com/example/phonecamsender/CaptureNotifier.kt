package com.example.phonecamsender

/** Android-free seam so the notification rule stays JVM-testable. */
interface NotificationSource {
    fun permissionGranted(): Boolean
    fun ensureChannel(): Boolean
    fun build(statusText: String): Any?
}

/**
 * Notification rule for the capture service. A denied POST_NOTIFICATIONS
 * permission must not stop capture, so nothing here gates on it and nothing
 * throws: null is returned only when the channel cannot be created.
 */
object CaptureNotifier {
    const val CHANNEL_ID = "camflow_capture"
    const val NOTIFICATION_ID = 4201
    const val TITLE = "CamFlow"

    fun build(source: NotificationSource, statusText: String): Any? {
        return try {
            if (!source.ensureChannel()) return null
            source.build(statusText)
        } catch (_: Exception) {
            null
        }
    }
}
