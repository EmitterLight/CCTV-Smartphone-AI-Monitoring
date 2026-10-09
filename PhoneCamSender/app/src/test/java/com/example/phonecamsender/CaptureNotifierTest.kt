package com.example.phonecamsender

import org.junit.Assert.*
import org.junit.Test

class CaptureNotifierTest {
    private class FakeSource(private val permissionGranted: Boolean) : NotificationSource {
        override fun permissionGranted() = permissionGranted
        override fun ensureChannel() = true
        override fun build(statusText: String): Any? = "notification:$statusText"
    }

    @Test fun buildReturnsNonNullWithoutNotificationPermission() {
        val n = CaptureNotifier.build(FakeSource(permissionGranted = false), "CamFlow running")
        assertNotNull(n)
    }

    @Test fun channelAndIdConstantsAreStable() {
        assertEquals("camflow_capture", CaptureNotifier.CHANNEL_ID)
        assertEquals(4201, CaptureNotifier.NOTIFICATION_ID)
    }

    @Test fun nullWhenChannelCannotBeCreated() {
        val source = object : NotificationSource {
            override fun permissionGranted() = true
            override fun ensureChannel() = false
            override fun build(statusText: String): Any? = "notification:$statusText"
        }
        assertNull(CaptureNotifier.build(source, "CamFlow running"))
    }

    @Test fun buildNeverThrowsWhenChannelCreationFails() {
        val source = object : NotificationSource {
            override fun permissionGranted() = false
            override fun ensureChannel() = throw IllegalStateException("channel creation failed")
            override fun build(statusText: String): Any? = "notification:$statusText"
        }
        assertNull(CaptureNotifier.build(source, "CamFlow running"))
    }
}
