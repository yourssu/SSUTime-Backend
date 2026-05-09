package com.ssutime.todo.domain.event

import com.ssutime.todo.domain.TodoType
import java.time.LocalDateTime

data class TodoConfirmed(
    val todoId: Long,
    val type: TodoType,
    val title: String,
    val dueDate: LocalDateTime,
)
