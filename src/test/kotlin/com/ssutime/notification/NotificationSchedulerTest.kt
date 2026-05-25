package com.ssutime.notification

import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.notification.domain.event.DeadlineApproaching
import com.ssutime.notification.infrastructure.NotificationScheduler
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.Optional

class NotificationSchedulerTest {
    private val userTodoStatusRepository: UserTodoStatusRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val userDeviceRepository: UserDeviceRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()
    private val scheduler =
        NotificationScheduler(
            userTodoStatusRepository,
            userRepository,
            userDeviceRepository,
            eventPublisher,
        )

    @Test
    fun `checkDeadlines - 알림이 꺼진 계정은 이벤트를 발행하지 않음`() {
        val user = User(id = 1L, authKey = "auth-1L", maskedStudentId = "20****01", notificationEnabled = false)
        val todo =
            Todo.create(
                subjectId = 10L,
                materialCode = 100001L,
                type = TodoType.ASSIGNMENT,
                dueDate = LocalDateTime.of(2026, 5, 10, 23, 59),
                title = "테스트 과제",
            )
        val status = UserTodoStatus.create(user.id, todo, 60)

        every { userTodoStatusRepository.findPendingNotifications(any()) } returns listOf(status)
        every { userRepository.findById(user.id) } returns Optional.of(user)

        scheduler.checkDeadlines()

        verify(exactly = 0) { userDeviceRepository.findAllByUser(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<DeadlineApproaching>()) }
    }

    @Test
    fun `checkDeadlines - 알림이 켜진 계정은 디바이스마다 이벤트를 발행함`() {
        val user = User(id = 1L, authKey = "auth-1L", maskedStudentId = "20****01", notificationEnabled = true)
        val device = UserDevice.create(user, "fcm-token")
        val todo =
            Todo.create(
                subjectId = 10L,
                materialCode = 100001L,
                type = TodoType.ASSIGNMENT,
                dueDate = LocalDateTime.of(2026, 5, 10, 23, 59),
                title = "테스트 과제",
            )
        val status = UserTodoStatus.create(user.id, todo, 60)

        every { userTodoStatusRepository.findPendingNotifications(any()) } returns listOf(status)
        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { userDeviceRepository.findAllByUser(user) } returns listOf(device)
        justRun { eventPublisher.publishEvent(any<DeadlineApproaching>()) }

        scheduler.checkDeadlines()

        verify { eventPublisher.publishEvent(any<DeadlineApproaching>()) }
    }
}
