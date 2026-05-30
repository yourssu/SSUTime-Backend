package com.ssutime.auth.presentation

import com.ssutime.auth.domain.AccountWithdrawalRequest
import com.ssutime.auth.domain.AccountWithdrawalRequestStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "계정 탈퇴 요청 응답입니다.")
data class AccountWithdrawalRequestResponse(
    @field:Schema(description = "탈퇴 요청 ID입니다.", example = "1")
    val id: Long,
    @field:Schema(description = "탈퇴 요청 상태입니다.", example = "REQUESTED")
    val status: AccountWithdrawalRequestStatus,
    @field:Schema(description = "탈퇴 요청 생성 시각입니다.", example = "2026-05-30T14:00:00")
    val requestedAt: LocalDateTime,
) {
    companion object {
        fun from(request: AccountWithdrawalRequest): AccountWithdrawalRequestResponse =
            AccountWithdrawalRequestResponse(
                id = request.id,
                status = request.status,
                requestedAt = request.createdAt,
            )
    }
}
