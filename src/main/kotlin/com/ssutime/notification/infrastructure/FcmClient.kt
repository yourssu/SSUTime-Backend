package com.ssutime.notification.infrastructure

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import io.swagger.v3.oas.annotations.media.Schema
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

    fun sendDryRunToTopic(
        topic: String,
        title: String,
        body: String,
    ): FcmDryRunResult {
        if (!fcmAvailable) {
            return FcmDryRunResult(
                configured = false,
                success = false,
                message = "FCM 인증 정보가 설정되지 않았습니다. FCM_CREDENTIALS_PATH를 설정하세요.",
            )
        }

        val message = Message.builder()
            .setTopic(topic)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .build()
        val messageId = FirebaseMessaging.getInstance().send(message, true)

        return FcmDryRunResult(
            configured = true,
            success = true,
            message = "FCM dry-run 요청이 성공했습니다.",
            messageId = messageId,
        )
    }
}

@Schema(description = "FCM dry-run 검증 결과입니다.")
data class FcmDryRunResult(
    @field:Schema(description = "백엔드에 Firebase Admin SDK 인증 정보가 설정되어 있는지 여부입니다.", example = "true")
    val configured: Boolean,

    @field:Schema(description = "Firebase가 dry-run 요청을 정상적으로 수락했는지 여부입니다.", example = "true")
    val success: Boolean,

    @field:Schema(description = "사람이 읽을 수 있는 검증 결과 메시지입니다.", example = "FCM dry-run 요청이 성공했습니다.")
    val message: String,

    @field:Schema(description = "dry-run 요청 성공 시 Firebase가 반환한 메시지 ID입니다.", example = "projects/ssutime-v2/messages/0:1710000000000000%abcdef")
    val messageId: String? = null,
)
