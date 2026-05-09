package com.ssutime.notification.infrastructure

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.springframework.stereotype.Component

@Component
class FcmClient {
    private val fcmAvailable get() = FirebaseApp.getApps().isNotEmpty()

    fun sendPush(fcmToken: String, title: String, body: String) {
        if (!fcmAvailable) return
        val message = Message.builder()
            .setToken(fcmToken)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .build()
        FirebaseMessaging.getInstance().send(message)
    }

    fun sendSilentPush(fcmToken: String, data: Map<String, String>) {
        if (!fcmAvailable) return
        val message = Message.builder()
            .setToken(fcmToken)
            .putAllData(data)
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .build()
        FirebaseMessaging.getInstance().send(message)
    }
}
