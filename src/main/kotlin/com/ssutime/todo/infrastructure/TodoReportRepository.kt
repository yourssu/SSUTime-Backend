package com.ssutime.todo.infrastructure

import com.ssutime.todo.domain.TodoReport
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface TodoReportRepository : JpaRepository<TodoReport, Long> {
    fun existsByUserIdAndSubjectIdAndMaterialCodeAndDueDateAndTitle(
        userId: Long,
        subjectId: Long,
        materialCode: Long,
        dueDate: LocalDateTime,
        title: String,
    ): Boolean

    fun findBySubjectIdAndMaterialCodeAndReportedAtAfter(
        subjectId: Long,
        materialCode: Long,
        since: LocalDateTime,
    ): List<TodoReport>
}
