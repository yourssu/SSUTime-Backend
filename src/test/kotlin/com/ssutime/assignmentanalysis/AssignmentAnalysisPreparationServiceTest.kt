package com.ssutime.assignmentanalysis

import com.ssutime.assignmentanalysis.application.AssignmentAnalysisPreparationService
import com.ssutime.assignmentanalysis.application.AssignmentContentExtractor
import com.ssutime.assignmentanalysis.application.ExtractedAssignmentContent
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysis
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisPrepared
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisStatus
import com.ssutime.assignmentanalysis.infrastructure.AssignmentAnalysisRepository
import com.ssutime.assignmentanalysis.presentation.AssignmentAnalysisPayload
import com.ssutime.common.exception.InvalidRequestException
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssignmentAnalysisPreparationServiceTest {
    private val contentExtractor: AssignmentContentExtractor = mockk()
    private val analysisRepository: AssignmentAnalysisRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val service = AssignmentAnalysisPreparationService(contentExtractor, analysisRepository, eventPublisher)

    private val todo =
        Todo.create(10L, 20L, TodoType.ASSIGNMENT, LocalDateTime.of(2026, 4, 3, 23, 59), "실습과제 2")
    private val payload = AssignmentAnalysisPayload(44383L, 718158L, "<p>과제</p>")

    @Test
    fun `prepareAnalysis persists idempotent artifact and publishes async event`() {
        every { contentExtractor.extract(payload) } returns
            ExtractedAssignmentContent("과제 설명과 첨부 텍스트", listOf("image.png: unsupported file type"))
        every {
            analysisRepository.findByTodoAndCourseIdAndAssignmentIdAndContentHash(todo, 44383L, 718158L, any())
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
    fun `prepareAnalysis does not request persistence when no analyzable attachment exists`() {
        every { contentExtractor.extract(payload) } throws InvalidRequestException("No analyzable attachment")

        assertFailsWith<InvalidRequestException> { service.prepareAnalysis(todo, payload) }

        verify(exactly = 0) { analysisRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<AssignmentAnalysisPrepared>()) }
    }

    @Test
    fun `prepareAnalysis reuses existing analysis without publishing duplicate event`() {
        val extractedContent = ExtractedAssignmentContent("과제 설명과 첨부 텍스트", emptyList())
        val existingAnalysis =
            AssignmentAnalysis.create(todo, 44383L, 718158L, "a".repeat(64), extractedContent.sanitizedContent, "")
        every { contentExtractor.extract(payload) } returns extractedContent
        every {
            analysisRepository.findByTodoAndCourseIdAndAssignmentIdAndContentHash(todo, 44383L, 718158L, any())
        } returns existingAnalysis

        val response = service.prepareAnalysis(todo, payload)

        assertEquals(existingAnalysis.id, response.analysisId)
        assertEquals(AssignmentAnalysisStatus.PENDING, response.status)
        verify(exactly = 0) { analysisRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<AssignmentAnalysisPrepared>()) }
    }
}
