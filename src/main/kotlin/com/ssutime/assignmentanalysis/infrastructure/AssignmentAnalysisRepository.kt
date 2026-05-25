package com.ssutime.assignmentanalysis.infrastructure

import com.ssutime.assignmentanalysis.domain.AssignmentAnalysis
import com.ssutime.todo.domain.Todo
import org.springframework.data.jpa.repository.JpaRepository

interface AssignmentAnalysisRepository : JpaRepository<AssignmentAnalysis, Long> {
    fun findByTodoAndCourseIdAndAssignmentIdAndContentHash(
        todo: Todo,
        courseId: Long,
        assignmentId: Long,
        contentHash: String,
    ): AssignmentAnalysis?
}
