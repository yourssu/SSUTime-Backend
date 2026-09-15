package com.ssutime.notification.infrastructure

import com.ssutime.notification.domain.UserBoardReceipt
import org.springframework.data.jpa.repository.JpaRepository

interface UserBoardReceiptRepository : JpaRepository<UserBoardReceipt, Long> {
    fun findAllByUserIdAndBoardIdIn(
        userId: Long,
        boardIds: Collection<Long>,
    ): List<UserBoardReceipt>
}
