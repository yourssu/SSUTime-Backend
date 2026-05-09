package com.ssutime.auth.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "계정 단위 알림 설정 응답입니다.")
data class NotificationSettingsResponse(
    @field:Schema(
        description = "마감 알림 사용 여부입니다.",
        example = "true",
    )
    val notificationEnabled: Boolean,

    @field:Schema(
        description = "마감 몇 분 전에 알림을 보낼지 나타내는 계정 설정입니다.",
        example = "60",
        minimum = "0",
    )
    val notificationThresholdMinutes: Int,
)
