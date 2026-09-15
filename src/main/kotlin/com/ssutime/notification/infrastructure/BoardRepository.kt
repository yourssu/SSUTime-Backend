package com.ssutime.notification.infrastructure

import com.ssutime.notification.domain.Board
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface BoardRepository : JpaRepository<Board, Long> {
    fun findAllByBoardIdIn(boardIds: Collection<Long>): List<Board>

    @Modifying
    @Query(
        value = "INSERT IGNORE INTO boards (board_id, created_at, updated_at) VALUES (:boardId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        nativeQuery = true,
    )
    fun insertIfAbsent(boardId: Long): Int
}
