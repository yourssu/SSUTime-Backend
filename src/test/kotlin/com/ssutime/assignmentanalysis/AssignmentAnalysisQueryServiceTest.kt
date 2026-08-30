package com.ssutime.assignmentanalysis

import com.ssutime.assignmentanalysis.application.AssignmentAnalysisQueryService
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysis
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisStatus
import com.ssutime.assignmentanalysis.infrastructure.AssignmentAnalysisRepository
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals

class AssignmentAnalysisQueryServiceTest {
    private val analysisRepository: AssignmentAnalysisRepository = mockk()
    private val userTodoStatusRepository: UserTodoStatusRepository = mockk()
    private val service = AssignmentAnalysisQueryService(analysisRepository, userTodoStatusRepository)

    @Test
    fun `getAnalysisStatus returns status for owning user`() {
        val todo = Todo.create(10L, 20L, TodoType.ASSIGNMENT, LocalDateTime.of(2026, 4, 3, 23, 59), "실습과제 2")
        val analysis = AssignmentAnalysis.create(todo, 44383L, 718158L, "a".repeat(64), "과제 설명", "image.png: unsupported file type")
        analysis.markSucceeded("한 줄 요약", 95)
        every { analysisRepository.findById(0L) } returns Optional.of(analysis)
        every { userTodoStatusRepository.findByUserIdAndTodo(1L, todo) } returns UserTodoStatus.create(1L, todo, 30)

        val response = service.getAnalysisStatus(userId = 1L, analysisId = 0L)

        assertEquals(0L, response.analysisId)
        assertEquals(AssignmentAnalysisStatus.SUCCEEDED, response.status)
        assertEquals(listOf("image.png: unsupported file type"), response.skippedFiles)
    }
}
