package com.ssutime.notification.domain.event

import java.time.LocalDateTime

data class DeadlineApproaching(
    val userTodoStatusId: Long,
    val userId: Long,
    val todoId: Long,
    val fcmToken: String,
    val dueDate: LocalDateTime,
)
