package com.ssutime.assignmentanalysis

import com.ssutime.aisummary.infrastructure.OpenAIClient
import com.ssutime.aisummary.infrastructure.AssignmentAiAnalysisResult
import com.ssutime.assignmentanalysis.application.AssignmentAnalysisService
import com.ssutime.assignmentanalysis.application.AssignmentContentExtractor
import com.ssutime.assignmentanalysis.application.ExtractedAssignmentContent
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysis
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisPrepared
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisStatus
import com.ssutime.assignmentanalysis.infrastructure.AssignmentAnalysisRepository
import com.ssutime.assignmentanalysis.presentation.AssignmentAnalysisPayload
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.infrastructure.TodoRepository
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals

class AssignmentAnalysisServiceTest {
    private val contentExtractor: AssignmentContentExtractor = mockk()
    private val analysisRepository: AssignmentAnalysisRepository = mockk()
    private val todoRepository: TodoRepository = mockk()
    private val userTodoStatusRepository: UserTodoStatusRepository = mockk()
    private val openAIClient: OpenAIClient = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val service =
        AssignmentAnalysisService(
            contentExtractor = contentExtractor,
            assignmentAnalysisRepository = analysisRepository,
            todoRepository = todoRepository,
            userTodoStatusRepository = userTodoStatusRepository,
            openAIClient = openAIClient,
            applicationEventPublisher = eventPublisher,
        )

    private val todo =
        Todo.create(
            subjectId = 10L,
            materialCode = 20L,
            type = TodoType.ASSIGNMENT,
            dueDate = LocalDateTime.of(2026, 4, 3, 23, 59),
            title = "실습과제 2",
        )
    private val payload =
        AssignmentAnalysisPayload(
            courseId = 44383L,
            assignmentId = 718158L,
            assignmentHtml = "<p>과제</p>",
        )

    @Test
    fun `prepareAnalysis persists idempotent artifact and publishes async event`() {
        every { contentExtractor.extract(payload) } returns
            ExtractedAssignmentContent(
                sanitizedContent = "과제 설명과 첨부 텍스트",
                skippedFiles = listOf("image.png: unsupported file type"),
            )
        every {
            analysisRepository.findByTodoAndCourseIdAndAssignmentIdAndContentHash(
                todo,
                44383L,
                718158L,
                any(),
            )
        } returns null
        every { analysisRepository.save(any()) } answers { firstArg() }
        val eventSlot = slot<AssignmentAnalysisPrepared>()
        justRun { eventPublisher.publishEvent(capture(eventSlot)) }

        val response = service.prepareAnalysis(todo, payload)

        assertEquals(AssignmentAnalysisStatus.PENDING, response.status)
        assertEquals(listOf("image.png: unsupported file type"), response.skippedFiles)
        assertEquals(0L, eventSlot.captured.analysisId)
    }

    @Test
    fun `onAssignmentAnalysisPrepared stores AI summary and estimated duration`() {
        val analysis =
            AssignmentAnalysis.create(
                todo = todo,
                courseId = 44383L,
                assignmentId = 718158L,
                contentHash = "a".repeat(64),
                sanitizedContent = "과제 설명",
                skippedFiles = "",
            )
        every { analysisRepository.findById(0L) } returns Optional.of(analysis)
        every { analysisRepository.save(any()) } answers { firstArg() }
        every { openAIClient.analyzeAssignment("과제 설명") } returns
            AssignmentAiAnalysisResult(
                summary = "한 줄 요약\n추가 줄은 저장되면 안 됨",
                estimatedDurationMinutes = 95,
            )
        every { todoRepository.save(todo) } returns todo

        service.onAssignmentAnalysisPrepared(AssignmentAnalysisPrepared(0L))

        assertEquals(AssignmentAnalysisStatus.SUCCEEDED, analysis.status)
        assertEquals("한 줄 요약", analysis.analysis)
        assertEquals(120, analysis.estimatedDurationMinutes)
        assertEquals("한 줄 요약", todo.aiSummary)
        assertEquals(120, todo.estimatedDurationMinutes)
        verify { todoRepository.save(todo) }
    }

    @Test
    fun `getAnalysisStatus returns status for owning user`() {
        val analysis =
            AssignmentAnalysis.create(
                todo = todo,
                courseId = 44383L,
                assignmentId = 718158L,
                contentHash = "a".repeat(64),
                sanitizedContent = "과제 설명",
                skippedFiles = "image.png: unsupported file type",
            )
        analysis.markSucceeded(
            summary = "한 줄 요약",
            estimatedDurationMinutes = 95,
        )
        every { analysisRepository.findById(0L) } returns Optional.of(analysis)
        every { userTodoStatusRepository.findByUserIdAndTodo(1L, todo) } returns UserTodoStatus.create(1L, todo, 30)

        val response = service.getAnalysisStatus(userId = 1L, analysisId = 0L)

        assertEquals(0L, response.analysisId)
        assertEquals(AssignmentAnalysisStatus.SUCCEEDED, response.status)
        assertEquals(listOf("image.png: unsupported file type"), response.skippedFiles)
    }
}
