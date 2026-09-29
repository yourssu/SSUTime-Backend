package com.ssutime.auth.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "계정 단위 알림 설정 변경 요청입니다.")
data class NotificationSettingsRequest(
    @field:Schema(
        description = "시스템 알림 사용 여부입니다. false이면 마감 N분 전, 게시판 알림을 모두 발송하지 않습니다.",
        example = "true",
    )
    val notificationEnabled: Boolean = true,
    @field:Schema(
        description = "마감 몇 분 전에 개별 알림을 보낼지 나타내는 계정 설정입니다. 0이면 마감 N분 전 알림을 보내지 않습니다.",
        example = "60",
        minimum = "0",
    )
    val notificationThresholdMinutes: Int,
)
