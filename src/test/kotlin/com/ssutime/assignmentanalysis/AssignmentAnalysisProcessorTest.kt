package com.ssutime.assignmentanalysis

import com.ssutime.aisummary.infrastructure.AssignmentAiAnalysisResult
import com.ssutime.aisummary.infrastructure.OpenAIClient
import com.ssutime.assignmentanalysis.application.AssignmentAnalysisProcessor
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysis
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisPrepared
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisStatus
import com.ssutime.assignmentanalysis.infrastructure.AssignmentAnalysisRepository
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.infrastructure.TodoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals

class AssignmentAnalysisProcessorTest {
    private val analysisRepository: AssignmentAnalysisRepository = mockk()
    private val todoRepository: TodoRepository = mockk()
    private val openAIClient: OpenAIClient = mockk()
    private val processor = AssignmentAnalysisProcessor(analysisRepository, todoRepository, openAIClient)

    private val todo =
        Todo.create(10L, 20L, TodoType.ASSIGNMENT, LocalDateTime.of(2026, 4, 3, 23, 59), "실습과제 2")

    @Test
    fun `onAssignmentAnalysisPrepared stores AI summary and estimated duration`() {
        val analysis = AssignmentAnalysis.create(todo, 44383L, 718158L, "a".repeat(64), "과제 설명", "")
        every { analysisRepository.findById(0L) } returns Optional.of(analysis)
        every { analysisRepository.save(any()) } answers { firstArg() }
        every { openAIClient.analyzeAssignment("과제 설명") } returns AssignmentAiAnalysisResult("한 줄 요약\n추가 줄은 저장되면 안 됨", 95)
        every { todoRepository.save(todo) } returns todo

        processor.onAssignmentAnalysisPrepared(AssignmentAnalysisPrepared(0L))

        assertEquals(AssignmentAnalysisStatus.SUCCEEDED, analysis.status)
        assertEquals("한 줄 요약", analysis.analysis)
        assertEquals(120, analysis.estimatedDurationMinutes)
        assertEquals("한 줄 요약", todo.aiSummary)
        assertEquals(120, todo.estimatedDurationMinutes)
        verify(exactly = 1) { openAIClient.analyzeAssignment("과제 설명") }
        verify { todoRepository.save(todo) }
    }

    @Test
    fun `onAssignmentAnalysisPrepared does not call AI again for completed analysis`() {
        val analysis = AssignmentAnalysis.create(todo, 44383L, 718158L, "a".repeat(64), "과제 설명과 첨부 텍스트", "")
        analysis.markSucceeded("완료된 요약", 60)
        every { analysisRepository.findById(0L) } returns Optional.of(analysis)

        processor.onAssignmentAnalysisPrepared(AssignmentAnalysisPrepared(0L))

        verify(exactly = 0) { openAIClient.analyzeAssignment(any()) }
        verify(exactly = 0) { todoRepository.save(any()) }
        verify(exactly = 0) { analysisRepository.save(any()) }
    }
}
