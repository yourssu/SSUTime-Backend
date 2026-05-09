package com.ssutime.aisummary

import com.ssutime.aisummary.application.AISummaryService
import com.ssutime.aisummary.infrastructure.AnthropicClient
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.event.TodoConfirmed
import com.ssutime.todo.infrastructure.TodoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class AISummaryServiceTest {
    private val anthropicClient: AnthropicClient = mockk()
    private val todoRepository: TodoRepository = mockk()
    private val aiSummaryService = AISummaryService(anthropicClient, todoRepository)

    private val dueDate = LocalDateTime.of(2026, 5, 15, 23, 59)
    private val todo = Todo.create(10L, 100001L, TodoType.ASSIGNMENT, dueDate, "운영체제 6장 과제")

    @Test
    fun `onTodoConfirmed - ASSIGNMENT 타입이면 AI 요약 생성 후 저장`() {
        val event =
            TodoConfirmed(
                todoId = 1L,
                type = TodoType.ASSIGNMENT,
                title = "운영체제 6장 과제",
                dueDate = dueDate,
            )
        every { anthropicClient.summarizeAssignment(event.title) } returns "운영체제 6장 과제 요약"
        every { todoRepository.findById(1L) } returns Optional.of(todo)
        every { todoRepository.save(any()) } returns todo

        aiSummaryService.onTodoConfirmed(event)

        verify { anthropicClient.summarizeAssignment("운영체제 6장 과제") }
        verify { todoRepository.save(todo) }
    }

    @Test
    fun `onTodoConfirmed - VIDEO 타입이면 처리하지 않음`() {
        val event =
            TodoConfirmed(
                todoId = 2L,
                type = TodoType.VIDEO,
                title = "강의 영상 시청",
                dueDate = dueDate,
            )

        aiSummaryService.onTodoConfirmed(event)

        verify(exactly = 0) { anthropicClient.summarizeAssignment(any()) }
        verify(exactly = 0) { todoRepository.save(any()) }
    }

    @Test
    fun `onTodoConfirmed - QUIZ 타입이면 처리하지 않음`() {
        val event =
            TodoConfirmed(
                todoId = 3L,
                type = TodoType.QUIZ,
                title = "퀴즈 1",
                dueDate = dueDate,
            )

        aiSummaryService.onTodoConfirmed(event)

        verify(exactly = 0) { anthropicClient.summarizeAssignment(any()) }
        verify(exactly = 0) { todoRepository.save(any()) }
    }

    @Test
    fun `onTodoConfirmed - AI 요약이 blank이면 저장하지 않음`() {
        val event =
            TodoConfirmed(
                todoId = 4L,
                type = TodoType.ASSIGNMENT,
                title = "과제",
                dueDate = dueDate,
            )
        every { anthropicClient.summarizeAssignment(any()) } returns ""

        aiSummaryService.onTodoConfirmed(event)

        verify(exactly = 0) { todoRepository.findById(any()) }
        verify(exactly = 0) { todoRepository.save(any()) }
    }
}
