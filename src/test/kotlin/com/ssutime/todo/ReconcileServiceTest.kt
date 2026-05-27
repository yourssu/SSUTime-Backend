package com.ssutime.todo

import com.ssutime.auth.domain.User
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.todo.application.ReconcileService
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoReport
import com.ssutime.todo.domain.TodoStatus
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.domain.event.TodoConfirmed
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

class ReconcileServiceTest {
    private val userRepository: UserRepository = mockk()
    private val todoRepository: TodoRepository = mockk()
    private val todoReportRepository: TodoReportRepository = mockk()
    private val userTodoStatusRepository: UserTodoStatusRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()

    private val reconcileService =
        ReconcileService(
            userRepository,
            todoRepository,
            todoReportRepository,
            userTodoStatusRepository,
            eventPublisher,
        )

    private val subjectId = 10L
    private val materialCode = 100001L
    private val dueDate = LocalDateTime.now().plusDays(3)
    private val title = "Test Assignment"

    @BeforeEach
    fun setUp() {
        justRun { eventPublisher.publishEvent(any<TodoConfirmed>()) }
    }

    @Test
    fun `quorum 미달 시 CONFIRMED 전이 없음`() {
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val singleReport =
            listOf(
                TodoReport.create(1L, subjectId, materialCode, dueDate, title),
            )

        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { todoReportRepository.findBySubjectIdAndMaterialCodeAndReportedAtAfter(subjectId, materialCode, any()) } returns singleReport

        reconcileService.onTodoReported(TodoReported(subjectId, materialCode, 1L))

        verify(exactly = 0) { todoRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<TodoConfirmed>()) }
        assertEquals(TodoStatus.PROVISIONAL, todo.status)
    }

    @Test
    fun `같은 사용자의 반복 report는 quorum에서 한 표로만 계산한다`() {
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val repeatedReports =
            listOf(
                TodoReport.create(1L, subjectId, materialCode, dueDate, title),
                TodoReport.create(1L, subjectId, materialCode, dueDate, title),
            )

        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every {
            todoReportRepository.findBySubjectIdAndMaterialCodeAndReportedAtAfter(
                subjectId,
                materialCode,
                any(),
            )
        } returns repeatedReports

        reconcileService.onTodoReported(TodoReported(subjectId, materialCode, 1L))

        verify(exactly = 0) { todoRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<TodoConfirmed>()) }
        assertEquals(TodoStatus.PROVISIONAL, todo.status)
    }

    @Test
    fun `quorum 충족 시 PROVISIONAL에서 CONFIRMED로 전이`() {
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val reports =
            listOf(
                TodoReport.create(1L, subjectId, materialCode, dueDate, title),
                TodoReport.create(2L, subjectId, materialCode, dueDate, title),
            )

        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { todoReportRepository.findBySubjectIdAndMaterialCodeAndReportedAtAfter(subjectId, materialCode, any()) } returns reports
        every { todoRepository.save(any()) } returns todo
        every { userTodoStatusRepository.findAllByTodo(todo) } returns emptyList()

        reconcileService.onTodoReported(TodoReported(subjectId, materialCode, 1L))

        assertEquals(TodoStatus.CONFIRMED, todo.status)
        verify { eventPublisher.publishEvent(any<TodoConfirmed>()) }
    }

    @Test
    fun `이미 CONFIRMED인 경우 이벤트 재발행 없음`() {
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title).apply { confirm() }
        val reports =
            listOf(
                TodoReport.create(1L, subjectId, materialCode, dueDate, title),
                TodoReport.create(2L, subjectId, materialCode, dueDate, title),
            )

        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { todoReportRepository.findBySubjectIdAndMaterialCodeAndReportedAtAfter(subjectId, materialCode, any()) } returns reports
        every { todoRepository.save(any()) } returns todo
        every { userTodoStatusRepository.findAllByTodo(todo) } returns emptyList()

        reconcileService.onTodoReported(TodoReported(subjectId, materialCode, 1L))

        verify(exactly = 0) { eventPublisher.publishEvent(any<TodoConfirmed>()) }
    }

    @Test
    fun `다수결로 dueDate 변경 시 UserTodoStatus notifyAt 재계산`() {
        val originalDueDate = LocalDateTime.now().plusDays(3)
        val newDueDate = LocalDateTime.now().plusDays(5)
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, originalDueDate, title)
        val status = UserTodoStatus.create(1L, todo, 60)
        val user = User(id = 1L, authKey = "auth-1L", maskedStudentId = "20****01", notificationThresholdMinutes = 60)

        val reports =
            listOf(
                TodoReport.create(1L, subjectId, materialCode, newDueDate, title),
                TodoReport.create(2L, subjectId, materialCode, newDueDate, title),
            )

        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { todoReportRepository.findBySubjectIdAndMaterialCodeAndReportedAtAfter(subjectId, materialCode, any()) } returns reports
        every { todoRepository.save(any()) } returns todo
        every { userTodoStatusRepository.findAllByTodo(todo) } returns listOf(status)
        every { userRepository.findById(1L) } returns java.util.Optional.of(user)
        every { userTodoStatusRepository.save(status) } returns status

        reconcileService.onTodoReported(TodoReported(subjectId, materialCode, 1L))

        assertEquals(newDueDate, todo.dueDate)
        assertEquals(newDueDate.minusMinutes(60), status.notifyAt)
        verify { userTodoStatusRepository.save(status) }
    }

    @Test
    fun `todo 존재하지 않으면 조기 반환`() {
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns null

        reconcileService.onTodoReported(TodoReported(subjectId, materialCode, 1L))

        verify(exactly = 0) { todoReportRepository.findBySubjectIdAndMaterialCodeAndReportedAtAfter(any(), any(), any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `TodoConfirmed 이벤트가 올바른 값으로 발행됨`() {
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val reports =
            listOf(
                TodoReport.create(1L, subjectId, materialCode, dueDate, title),
                TodoReport.create(2L, subjectId, materialCode, dueDate, title),
            )

        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { todoReportRepository.findBySubjectIdAndMaterialCodeAndReportedAtAfter(subjectId, materialCode, any()) } returns reports
        every { todoRepository.save(any()) } returns todo
        every { userTodoStatusRepository.findAllByTodo(todo) } returns emptyList()

        val eventSlot = slot<TodoConfirmed>()
        justRun { eventPublisher.publishEvent(capture(eventSlot)) }

        reconcileService.onTodoReported(TodoReported(subjectId, materialCode, 1L))

        val captured = eventSlot.captured
        assertEquals(TodoType.ASSIGNMENT, captured.type)
        assertEquals(title, captured.title)
        assertEquals(dueDate, captured.dueDate)
    }
}
