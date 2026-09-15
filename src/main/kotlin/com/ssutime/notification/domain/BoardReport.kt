package com.ssutime.notification.domain

import com.ssutime.common.exception.InvalidRequestException

data class BoardReport(
    val fcmToken: String,
    val boards: List<ReportedBoard>,
) {
    fun validate() {
        if (fcmToken.isBlank()) throw InvalidRequestException("fcmToken은 필수입니다")
        if (boards.any { it.id <= 0 || it.title.isBlank() || it.totalPostCount < 0 }) {
            throw InvalidRequestException("게시판 ID, 제목, 게시글 수를 확인해주세요")
        }
    }
}

data class ReportedBoard(
    val id: Long,
    val title: String,
    val postWritableUserTypes: List<Int>,
    val replyWritableUserTypes: List<Int>,
    val totalPostCount: Long,
) {
    fun isWritableByStudent(): Boolean = 1 in postWritableUserTypes || 1 in replyWritableUserTypes
}
