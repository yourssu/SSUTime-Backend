package com.ssutime.todo.domain.event

data class TodoReported(
    val subjectId: Long,
    val materialCode: Long,
    val userId: Long,
)
