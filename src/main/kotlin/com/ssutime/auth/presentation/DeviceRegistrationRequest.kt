package com.ssutime.auth.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "FCM registration token 등록 요청입니다.")
data class DeviceRegistrationRequest(
    @field:Schema(
        description = "클라이언트 디바이스의 Firebase SDK가 발급한 FCM registration token입니다. Firebase가 토큰을 갱신하면 다시 등록해야 합니다.",
        example = "fcm_registration_token_from_client",
    )
    val fcmToken: String,
)
