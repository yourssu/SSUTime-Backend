package com.ssutime.auth.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "계정 탈퇴 요청 본문입니다.")
data class AccountWithdrawalRequestRequest(
    @field:Schema(
        description = "탈퇴 사유입니다. 비어 있으면 저장하지 않습니다.",
        example = "더 이상 서비스를 사용하지 않습니다.",
        maxLength = 500,
        nullable = true,
    )
    val reason: String? = null,
)
