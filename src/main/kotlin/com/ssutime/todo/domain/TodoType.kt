package com.ssutime.todo.domain

enum class TodoType {
    ASSIGNMENT,
    COMMONS,
    QUIZ,
    SUBMITTED,
    SUBMITTED_LATE,
    ;

    fun isCompletedAssignment(): Boolean = this == SUBMITTED || this == SUBMITTED_LATE

    fun toTodoType(): TodoType = if (isCompletedAssignment()) ASSIGNMENT else this
}
