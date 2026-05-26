package com.ssutime.todo

import com.ssutime.auth.domain.User
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.todo.application.TodoService
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoReport
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
    private val userRepository: UserRepository = mockk()
    private val todoRepository: TodoRepository = mockk()
    private val todoReportRepository: TodoReportRepository = mockk()
    private val userTodoStatusRepository: UserTodoStatusRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()

    private val todoService =
        TodoService(
            userRepository,
            todoRepository,
            todoReportRepository,
            userTodoStatusRepository,
            eventPublisher,
        )

    private val userId = 1L
    private val subjectId = 10L
    private val materialCode = 100001L
    private val dueDate = LocalDateTime.now().plusDays(3)
    private val title = "Test Assignment"
    private val thresholdMinutes = 60
    private val user =
        User(
            id = userId,
            authKey = "auth-1",
            maskedStudentId = "20****01",
            notificationThresholdMinutes = thresholdMinutes,
        )

    @BeforeEach
    fun setUp() {
        justRun { eventPublisher.publishEvent(any<TodoReported>()) }
        every { userRepository.findById(userId) } returns java.util.Optional.of(user)
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

        todoService.processReport(userId, subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)

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

        todoService.processReport(userId, subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)

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

        todoService.processReport(userId, subjectId, materialCode, TodoType.VIDEO, dueDate, title)

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

        todoService.processReport(userId, subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)

        val capturedEvent = eventSlot.captured
        assertEquals(subjectId, capturedEvent.subjectId)
        assertEquals(materialCode, capturedEvent.materialCode)
        assertEquals(userId, capturedEvent.userId)
    }

    @Test
    fun `UserTodoStatus notifyAt은 dueDate에서 계정 알림 시간을 뺀 시각이어야 함`() {
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val status = UserTodoStatus.create(userId, todo, thresholdMinutes)

        val expectedNotifyAt = dueDate.minusMinutes(thresholdMinutes.toLong())
        assertEquals(expectedNotifyAt, status.notifyAt)
    }

    @Test
    fun `updateTodoCompletion - 사용자의 todo 상태를 완료 처리한다`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val status = UserTodoStatus.create(userId, todo, thresholdMinutes)

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, todo) } returnsMany listOf(status, status)
        every { userTodoStatusRepository.save(status) } returns status

        todoService.updateTodoCompletion(
            userId = userId,
            subjectId = subjectId,
            materialCode = materialCode,
            type = TodoType.ASSIGNMENT,
            dueDate = dueDate,
            title = title,
            isCompleted = true,
        )

        assertEquals(true, status.isCompleted)
        assertNotNull(status.completedAt)
        verify { todoReportRepository.save(any()) }
        verify { userTodoStatusRepository.save(status) }
        verify { eventPublisher.publishEvent(TodoReported(subjectId, materialCode, userId)) }
    }

    @Test
    fun `updateTodoCompletion - todo가 없으면 POST report처럼 생성한 뒤 완료 처리한다`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val status = UserTodoStatus.create(userId, todo, thresholdMinutes)

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns null
        every { todoRepository.save(any()) } returns todo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, todo) } returnsMany listOf(null, status)
        every { userTodoStatusRepository.save(any()) } returns status

        todoService.updateTodoCompletion(
            userId = userId,
            subjectId = subjectId,
            materialCode = materialCode,
            type = TodoType.ASSIGNMENT,
            dueDate = dueDate,
            title = title,
            isCompleted = true,
        )

        assertEquals(true, status.isCompleted)
        assertNotNull(status.completedAt)
        verify { todoRepository.save(any()) }
        verify(exactly = 2) { userTodoStatusRepository.save(any()) }
        verify { eventPublisher.publishEvent(TodoReported(subjectId, materialCode, userId)) }
    }

    @Test
    fun `updateTodoCompletion - todo는 있고 사용자 상태가 없으면 POST report처럼 연결한 뒤 완료 처리한다`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val status = UserTodoStatus.create(userId, todo, thresholdMinutes)

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, todo) } returnsMany listOf(null, status)
        every { userTodoStatusRepository.save(any()) } returns status

        todoService.updateTodoCompletion(
            userId = userId,
            subjectId = subjectId,
            materialCode = materialCode,
            type = TodoType.ASSIGNMENT,
            dueDate = dueDate,
            title = title,
            isCompleted = true,
        )

        assertEquals(true, status.isCompleted)
        assertNotNull(status.completedAt)
        verify(exactly = 0) { todoRepository.save(any()) }
        verify(exactly = 2) { userTodoStatusRepository.save(any()) }
        verify { eventPublisher.publishEvent(TodoReported(subjectId, materialCode, userId)) }
    }

    @Test
    fun `updateTodoCompletion - 이미 완료된 todo 상태는 완료 시각을 변경하지 않는다`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val status = UserTodoStatus.create(userId, todo, thresholdMinutes)
        status.updateCompletion(completed = true)
        val completedAt = status.completedAt

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, todo) } returnsMany listOf(status, status)

        todoService.updateTodoCompletion(
            userId = userId,
            subjectId = subjectId,
            materialCode = materialCode,
            type = TodoType.ASSIGNMENT,
            dueDate = dueDate,
            title = title,
            isCompleted = true,
        )

        assertEquals(completedAt, status.completedAt)
        verify { todoReportRepository.save(any()) }
        verify(exactly = 0) { userTodoStatusRepository.save(any()) }
    }

    @Test
    fun `updateTodoCompletion - 완료된 todo 상태를 미완료로 변경한다`() {
        val report = TodoReport.create(userId, subjectId, materialCode, dueDate, title)
        val todo = Todo.create(subjectId, materialCode, TodoType.ASSIGNMENT, dueDate, title)
        val status = UserTodoStatus.create(userId, todo, thresholdMinutes)
        status.updateCompletion(completed = true)

        every { todoReportRepository.save(any()) } returns report
        every { todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode) } returns todo
        every { userTodoStatusRepository.findByUserIdAndTodo(userId, todo) } returnsMany listOf(status, status)
        every { userTodoStatusRepository.save(status) } returns status

        todoService.updateTodoCompletion(
            userId = userId,
            subjectId = subjectId,
            materialCode = materialCode,
            type = TodoType.ASSIGNMENT,
            dueDate = dueDate,
            title = title,
            isCompleted = false,
        )

        assertEquals(false, status.isCompleted)
        assertEquals(null, status.completedAt)
        verify { userTodoStatusRepository.save(status) }
    }
}
