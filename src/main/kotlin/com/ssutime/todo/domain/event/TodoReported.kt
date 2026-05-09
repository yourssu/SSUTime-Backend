package com.ssutime.todo.domain.event

data class TodoReported(
    val subjectId: Long,
    val materialCode: String,
    val userId: Long,
)
