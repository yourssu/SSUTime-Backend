package com.ssutime.notification

import com.google.firebase.FirebaseApp
import com.ssutime.notification.infrastructure.FcmClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FcmClientTest {
    private val fcmClient = FcmClient()

    @Test
    fun `sendDryRunToTopic - reports missing firebase configuration`() {
        FirebaseApp.getApps().forEach { it.delete() }

        val result = fcmClient.sendDryRunToTopic(
            topic = "ssutime-dry-run",
            title = "FCM dry run",
            body = "test",
        )

        assertEquals(false, result.configured)
        assertEquals(false, result.success)
    }
}
