package com.ssutime.auth.infrastructure

import com.ssutime.auth.domain.AccountWithdrawalRequest
import org.springframework.data.jpa.repository.JpaRepository

interface AccountWithdrawalRequestRepository : JpaRepository<AccountWithdrawalRequest, Long> {
    fun findByUserId(userId: Long): AccountWithdrawalRequest?
}
