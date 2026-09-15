package com.ssutime.notification.presentation

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.ssutime.notification.domain.BoardReport
import com.ssutime.notification.domain.ReportedBoard
import io.swagger.v3.oas.annotations.media.Schema

data class BoardReportRequest(
    @field:Schema(description = "현재 사용자에게 /auth/devices로 등록한 요청 기기의 FCM 토큰")
    val fcmToken: String,
    val boards: List<BoardRequest>,
) {
    fun toDomain(): BoardReport =
        BoardReport(
            fcmToken = fcmToken,
            boards = boards.map { ReportedBoard(it.id, it.title, it.postWritableUserTypes, it.replyWritableUserTypes, it.totalPostCount) },
        )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class BoardRequest(
    val id: Long,
    val title: String,
    @param:JsonProperty("post_writable_user_types")
    val postWritableUserTypes: List<Int>,
    @param:JsonProperty("use_secret_post")
    val useSecretPost: Boolean,
    @param:JsonProperty("reply_writable_user_types")
    val replyWritableUserTypes: List<Int>,
    @param:JsonProperty("comment_writable_user_types")
    val commentWritableUserTypes: List<Int>,
    @param:JsonProperty("is_public")
    val isPublic: Boolean,
    val position: Int,
    @param:JsonProperty("total_post_count")
    val totalPostCount: Long,
)
