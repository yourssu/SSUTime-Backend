package com.ssutime.auth.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

const val ACCOUNT_WITHDRAWAL_REASON_MAX_LENGTH = 500

@Entity
@Table(
    name = "account_withdrawal_requests",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id"])],
)
class AccountWithdrawalRequest private constructor(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(nullable = false, length = 8)
    val maskedStudentId: String,
    @Column(length = ACCOUNT_WITHDRAWAL_REASON_MAX_LENGTH)
    val reason: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AccountWithdrawalRequestStatus = AccountWithdrawalRequestStatus.REQUESTED,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    companion object {
        fun create(
            user: User,
            reason: String?,
        ): AccountWithdrawalRequest {
            val normalizedReason = reason?.trim()?.takeIf { it.isNotBlank() }
            require(normalizedReason == null || normalizedReason.length <= ACCOUNT_WITHDRAWAL_REASON_MAX_LENGTH) {
                "reason must be at most $ACCOUNT_WITHDRAWAL_REASON_MAX_LENGTH characters"
            }
            return AccountWithdrawalRequest(
                userId = user.id,
                maskedStudentId = user.maskedStudentId,
                reason = normalizedReason,
            )
        }
    }
}
