package com.ssutime.todo.presentation

import com.ssutime.todo.domain.TodoType
import java.time.LocalDateTime

data class TodoReportRequest(
    val subjectId: Long,
    val materialCode: String,
    val type: TodoType,
    val dueDate: LocalDateTime,
    val title: String,
    val thresholdMinutes: Int = 60,
)
