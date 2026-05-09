package com.ssutime.todo

import com.ssutime.todo.application.TodoService
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoReport
import com.ssutime.todo.domain.TodoStatus
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.domain.event.TodoReported
import com.ssutime.todo.infrastructure.TodoReportRepository
import com.ssutime.todo.infrastructure.TodoRepository
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TodoServiceTest {

    private val todoRepository: TodoRepository = mockk()
    private val todoReportRepository: TodoReportRepository = mockk()
    private val userTodoStatusRepository: UserTodoStatusRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()

    private val todoService = TodoService(
        todoRepository,
        todoReportRepository,
        userTodoStatusRepository,
        eventPublisher,
    )

    private val userId = 1L
    private val subjectId = 10L
    private val materialCode = "MATERIAL_001"
    private val dueDate = LocalDateTime.now().plusDays(3)
    private val title = "Test Assignment"
    private val thresholdMinutes = 60

    @BeforeEach
    fun setUp() {
        justRun { eventPublisher.publishEvent(any<TodoReported>()) }
    }

    @Test
    fun `processReport - 신규 todo 생성 시나리오`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns null
        every { todoRepository.save(any()) } returns todo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, todo) } returns null
        every { userTodoStatusRepository.save(any()) } returns UserTodoStatus.create(userId, todo, thresholdMinutes)

        todoService.processReport(userId, subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title, thresholdMinutes)

        verify { todoReportRepository.save(any()) }
        verify { todoRepository.save(any()) }
        verify { userTodoStatusRepository.save(any()) }
        verify { eventPublisher.publishEvent(TodoReported(subjectId, materialCode, userId)) }
    }

    @Test
    fun `processReport - 기존 todo 존재 시 upsert 시나리오`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val existingTodo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val existingStatus = UserTodoStatus.create(userId, existingTodo, thresholdMinutes)

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns existingTodo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, existingTodo) } returns existingStatus

        todoService.processReport(userId, subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title, thresholdMinutes)

        verify { todoReportRepository.save(any()) }
        verify(exactly = 0) { todoRepository.save(any()) }
        verify(exactly = 0) { userTodoStatusRepository.save(any()) }
        verify { eventPublisher.publishEvent(TodoReported(subjectId, materialCode, userId)) }
    }

    @Test
    fun `processReport - 기존 todo 있고 UserTodoStatus 없을 때 새로 생성`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val existingTodo = Todo.create(subjectId, materialCode, TodoType.VIDEO, dueDate, title)

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns existingTodo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, existingTodo) } returns null
        every { userTodoStatusRepository.save(any()) } returns UserTodoStatus.create(userId, existingTodo, thresholdMinutes)

        todoService.processReport(userId, subjectId, materialCode, TodoType.VIDEO, dueDate, title, thresholdMinutes)

        verify { userTodoStatusRepository.save(any()) }
        verify { eventPublisher.publishEvent(TodoReported(subjectId, materialCode, userId)) }
    }

    @Test
    fun `processReport - TodoReported 이벤트가 올바른 파라미터로 발행됨`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns null
        every { todoRepository.save(any()) } returns todo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, todo) } returns null
        every { userTodoStatusRepository.save(any()) } returns UserTodoStatus.create(userId, todo, thresholdMinutes)

        val eventSlot = slot<TodoReported>()
        justRun { eventPublisher.publishEvent(capture(eventSlot)) }

        todoService.processReport(userId, subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title, thresholdMinutes)

        val capturedEvent = eventSlot.captured
        assertEquals(subjectId, capturedEvent.subjectId)
        assertEquals(materialCode, capturedEvent.materialCode)
        assertEquals(userId, capturedEvent.userId)
    }

    @Test
    fun `UserTodoStatus notifyAt은 dueDate에서 thresholdMinutes를 뺀 시각이어야 함`() {
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val status = UserTodoStatus.create(userId, todo, thresholdMinutes)

        val expectedNotifyAt = dueDate.minusMinutes(thresholdMinutes.toLong())
        assertEquals(expectedNotifyAt, status.notifyAt)
    }
}
