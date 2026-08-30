package com.ssutime.notification

import com.ssutime.notification.application.NotificationService
import com.ssutime.notification.domain.event.DeadlineApproaching
import com.ssutime.notification.infrastructure.FcmClient
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class NotificationServiceTest {
    private val fcmClient: FcmClient = mockk(relaxed = true)
    private val userTodoStatusRepository: UserTodoStatusRepository = mockk()
    private val notificationService = NotificationService(fcmClient, userTodoStatusRepository)

    private val dueDate = LocalDateTime.of(2026, 5, 10, 23, 59)
    private val todo = Todo.create(10L, 100001L, TodoType.ASSIGNMENT, dueDate, "테스트 과제")
    private val status = UserTodoStatus.create(10L, todo, 60)

    @Test
    fun `onDeadlineApproaching - FCM push 발송 후 notificationSent 마킹`() {
        val statusId = 1L
        val event =
            DeadlineApproaching(
                userTodoStatusId = statusId,
                userId = 10L,
                todoId = 20L,
                fcmToken = "test-token",
                dueDate = dueDate,
            )
        every { userTodoStatusRepository.findById(statusId) } returns Optional.of(status)
        every { userTodoStatusRepository.save(any()) } returns status

        notificationService.onDeadlineApproaching(event)

        verify { fcmClient.sendSilentPush(fcmToken = "test-token", data = any()) }
        verify { userTodoStatusRepository.save(status) }
    }

    @Test
    fun `onDeadlineApproaching - status 없으면 save 호출 안 함`() {
        val event =
            DeadlineApproaching(
                userTodoStatusId = 99L,
                userId = 10L,
                todoId = 20L,
                fcmToken = "test-token",
                dueDate = dueDate,
            )
        every { userTodoStatusRepository.findById(99L) } returns Optional.empty()

        notificationService.onDeadlineApproaching(event)

        verify { fcmClient.sendSilentPush(any(), any()) }
        verify(exactly = 0) { userTodoStatusRepository.save(any()) }
    }
}
