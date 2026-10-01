package com.ssutime.notification

import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.notification.infrastructure.FcmClient
import com.ssutime.notification.infrastructure.NotificationScheduler
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals

class NotificationSchedulerDailyTodoTest {
    private val statuses: UserTodoStatusRepository = mockk()
    private val users: UserRepository = mockk()
    private val devices: UserDeviceRepository = mockk()
    private val fcm: FcmClient = mockk(relaxed = true)
    private val scheduler = NotificationScheduler(mockk(), statuses, users, devices, fcm)

    @Test
    fun `daily summary runs at six in Seoul`() {
        val schedule = NotificationScheduler::class.java.getMethod("sendDailyTodos").getAnnotation(Scheduled::class.java)
        assertEquals("0 0 6 * * *", schedule.cron)
        assertEquals("Asia/Seoul", schedule.zone)
    }

    @Test
    fun `sends one summary per device for previous Seoul day window`() {
        val user = User(id = 1L, authKey = "user-1", maskedStudentId = "20****01")
        val disabled = User(id = 2L, authKey = "user-2", maskedStudentId = "20****02", notificationEnabled = false)
        val first = todoStatus(user.id, 101L, "First")
        val second = todoStatus(user.id, 102L, "Second")
        val ignored = todoStatus(disabled.id, 103L, "Ignored")
        val from = slot<LocalDateTime>()
        val until = slot<LocalDateTime>()
        every { statuses.findNewTodos(capture(from), capture(until)) } returns listOf(first, second, ignored)
        every { users.findById(user.id) } returns Optional.of(user)
        every { users.findById(disabled.id) } returns Optional.of(disabled)
        every { devices.findAllByUser(user) } returns listOf(UserDevice.create(user, "token-1"), UserDevice.create(user, "token-2"))

        scheduler.sendDailyTodos()

        val seoul = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(seoul)
        val expectedFrom =
            today
                .minusDays(1)
                .atTime(LocalTime.of(6, 0))
                .atZone(seoul)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        val expectedUntil =
            today
                .atTime(LocalTime.of(6, 0))
                .atZone(seoul)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        assertEquals(expectedFrom, from.captured)
        assertEquals(expectedUntil, until.captured)
        verify(exactly = 1) { fcm.sendPush("token-1", "새 할 일 2개", "First 외 1개") }
        verify(exactly = 1) { fcm.sendPush("token-2", "새 할 일 2개", "First 외 1개") }
        verify(exactly = 0) { devices.findAllByUser(disabled) }
    }

    private fun todoStatus(
        userId: Long,
        materialCode: Long,
        title: String,
    ): UserTodoStatus =
        UserTodoStatus.create(
            userId,
            Todo.create(10L, materialCode, TodoType.ASSIGNMENT, LocalDateTime.of(2026, 10, 10, 23, 59), title),
            60,
        )
}
