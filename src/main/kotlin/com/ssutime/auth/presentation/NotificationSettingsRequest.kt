package com.ssutime.auth.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "계정 단위 알림 설정 변경 요청입니다.")
data class NotificationSettingsRequest(
    @field:Schema(
        description = "시스템 알림 사용 여부입니다. false이면 마감 임박, 당일 마감, 신규 할 일, 게시판 알림을 모두 발송하지 않습니다.",
        example = "true",
    )
    val notificationEnabled: Boolean = true,
    @field:Schema(
        description = "이전 앱 호환용 값입니다. 저장되지만 정시 알림 발송 시각에는 적용되지 않습니다.",
        deprecated = true,
        example = "60",
        minimum = "0",
    )
    val notificationThresholdMinutes: Int,
)
