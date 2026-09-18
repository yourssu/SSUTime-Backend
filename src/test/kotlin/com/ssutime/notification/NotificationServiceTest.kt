package com.ssutime.notification

import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.notification.application.NotificationService
import com.ssutime.notification.domain.event.NewBoardDetected
import com.ssutime.notification.infrastructure.FcmClient
import com.ssutime.notification.infrastructure.NotificationDeliveryRepository
import com.ssutime.subject.domain.Subject
import com.ssutime.subject.infrastructure.SubjectRepository
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

class NotificationServiceTest {
    private val statuses = mockk<UserTodoStatusRepository>()
    private val users = mockk<UserRepository>()
    private val devices = mockk<UserDeviceRepository>()
    private val subjects = mockk<SubjectRepository>()
    private val deliveries = mockk<NotificationDeliveryRepository>()
    private val fcm = mockk<FcmClient>()
    private val service = NotificationService(fcm, statuses, users, devices, mockk(), mockk(), subjects, deliveries)
    private val today = LocalDate.of(2026, 9, 18)
    private val user = User(id = 1, authKey = "key", maskedStudentId = "20****01")
    private val messages = mutableListOf<Map<String, String>>()
    private var nextTodoId = 40L

    @BeforeEach
    fun setUp() {
        every { users.findById(1) } returns Optional.of(user)
        every { devices.findAllByUser(user) } returns listOf(UserDevice.create(user, "token"))
        every { subjects.findAllById(any()) } returns listOf(Subject(10, 100, "데이터사이언스", "2026-2"))
        every { fcm.sendSilentPush(any(), any()) } answers {
            messages.add(arg<Map<String, String>>(1))
            Unit
        }
        every { statuses.findDeadlineNotifications(any(), any(), any()) } returns emptyList()
        every { statuses.findNewNotifications(any(), any()) } returns emptyList()
        every { deliveries.insertIfAbsent(any(), any(), any(), any(), any()) } returns 1
        every { deliveries.claim(any(), any(), any(), any(), any(), any(), any()) } returns 1
        every { deliveries.markSent(any(), any()) } returns 1
        every { deliveries.release(any(), any()) } returns 1
    }

    private fun item(
        days: Long,
        type: TodoType = TodoType.ASSIGNMENT,
    ): UserTodoStatus {
        val todo = Todo.create(10, 100, type, today.plusDays(days).atTime(23, 59), "제목")
        ReflectionTestUtils.setField(todo, "id", ++nextTodoId)
        return UserTodoStatus.create(1, todo, 60)
    }

    @Test
    fun `evening sends at most two groups and preserves overlap with earliest representative`() {
        val items = listOf(item(3), item(1, TodoType.QUIZ), item(2))
        val cutoff = today.atTime(18, 0).atZone(ZoneId.of("Asia/Seoul"))
        val start = cutoff.minusDays(1).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        val end = cutoff.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        every {
            statuses.findDeadlineNotifications(today.plusDays(1).atStartOfDay(), today.plusDays(4).atStartOfDay(), end)
        } returns items
        every { statuses.findNewNotifications(start, end) } returns items
        service.sendEveningNotifications(today)
        assertEquals(2, messages.size)
        assertEquals(listOf("deadlineApproaching", "newTodo"), messages.map { it["type"] })
        assertTrue(messages.all { it["count"] == "3" && it["representative_todo_id"] == "42" && it["todo_type"] == "QUIZ" })
        assertTrue(messages.all { "todo_id" !in it && "action" !in it && "destination" !in it && "body" !in it && "title" !in it })
    }

    @Test
    fun `morning sends each item with data for app rendering`() {
        val cutoff =
            today
                .atTime(9, 0)
                .atZone(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
        every { statuses.findDeadlineNotifications(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), cutoff) } returns
            listOf(item(0), item(0, TodoType.QUIZ))
        service.sendMorningNotifications(today)
        assertEquals(listOf("ASSIGNMENT", "QUIZ"), messages.map { it["todo_type"] })
        assertEquals(listOf("41", "42"), messages.map { it["todo_id"] })
        assertTrue(messages.all { it["count"] == "1" && it["type"] == "dueToday" && it["action"] == "deadline_approaching" })
    }

    @Test
    fun `single lecture passes source data for client rendering`() {
        every { statuses.findDeadlineNotifications(any(), any(), any()) } returns listOf(item(2, TodoType.COMMONS))
        every { statuses.findNewNotifications(any(), any()) } returns listOf(item(2, TodoType.COMMONS))
        service.sendEveningNotifications(today)
        assertTrue(messages.all { it["todo_type"] == "COMMONS" && it["todo_title"] == "제목" })
        assertTrue(messages.all { it["subject_name"] == "데이터사이언스" && it["due_date"] == "2026-09-20T23:59" })
        assertTrue(messages.all { "days_until_due" !in it })
    }

    @Test
    fun `disabled user receives neither morning nor evening notifications`() {
        every { users.findById(1) } returns
            Optional.of(User(id = 1, authKey = "key", maskedStudentId = "20****01", notificationEnabled = false))
        every { statuses.findDeadlineNotifications(any(), any(), any()) } returns listOf(item(1))
        every { statuses.findNewNotifications(any(), any()) } returns listOf(item(1))
        service.sendMorningNotifications(today)
        service.sendEveningNotifications(today)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `missing subject does not abort remaining notifications`() {
        every { subjects.findAllById(any()) } returns emptyList()
        every { statuses.findDeadlineNotifications(any(), any(), any()) } returns listOf(item(0), item(0, TodoType.QUIZ))
        service.sendMorningNotifications(today)
        assertEquals(2, messages.size)
        assertTrue(messages.all { "subject_name" !in it })
    }

    @Test
    fun `failed device does not prevent other devices or notification types`() {
        every { devices.findAllByUser(user) } returns
            listOf(UserDevice.create(user, "broken"), UserDevice.create(user, "token"))
        every { fcm.sendSilentPush("broken", any()) } throws IllegalStateException("FCM unavailable")
        val pending = listOf(item(1))
        every { statuses.findDeadlineNotifications(any(), any(), any()) } returns pending
        every { statuses.findNewNotifications(any(), any()) } returns pending
        service.sendEveningNotifications(today)
        assertEquals(2, messages.size)
        verify(atLeast = 1) { deliveries.release(any(), any()) }
    }

    @Test
    fun `same delivery slot is not sent twice`() {
        every { statuses.findDeadlineNotifications(any(), any(), any()) } returns listOf(item(1))
        every {
            deliveries.claim(any(), "deadlineApproaching", today, "group", any(), any(), any())
        } returnsMany listOf(1, 0)

        service.sendEveningNotifications(today)
        service.sendEveningNotifications(today)

        assertEquals(1, messages.size)
        verify(exactly = 1) { deliveries.markSent(any(), any()) }
    }

    @Test
    fun `single deadline includes actual id and original action`() {
        every { statuses.findDeadlineNotifications(any(), any(), any()) } returns listOf(item(1))
        service.sendEveningNotifications(today)
        assertEquals(
            mapOf(
                "type" to "deadlineApproaching",
                "count" to "1",
                "representative_todo_id" to "41",
                "todo_title" to "제목",
                "todo_type" to "ASSIGNMENT",
                "due_date" to "2026-09-19T23:59",
                "subject_name" to "데이터사이언스",
                "todo_id" to "41",
                "action" to "deadline_approaching",
            ),
            messages.single(),
        )
    }

    @Test
    fun `new todo uses actual type and ties are resolved by todo id`() {
        val assignment = item(1)
        val quiz = item(1, TodoType.QUIZ)
        every { statuses.findNewNotifications(any(), any()) } returns listOf(quiz, assignment)
        service.sendEveningNotifications(today)
        assertEquals("41", messages.single()["representative_todo_id"])
        assertEquals("2", messages.single()["count"])
        assertEquals("ASSIGNMENT", messages.single()["todo_type"])

        every { statuses.findNewNotifications(any(), any()) } returns listOf(quiz)
        service.sendEveningNotifications(today)
        assertEquals("newTodo", messages.last()["type"])
        assertEquals("1", messages.last()["count"])
        assertEquals("42", messages.last()["todo_id"])
        assertEquals("QUIZ", messages.last()["todo_type"])
        assertTrue("action" !in messages.last())
    }

    @Test
    fun `board preserves legacy payload`() {
        every { devices.findByUserAndFcmToken(user, "token") } returns UserDevice.create(user, "token")
        service.onNewBoardDetected(NewBoardDetected("token", "제출 게시판", user.id))
        verify(exactly = 1) {
            fcm.sendSilentPush(
                "token",
                mapOf("type" to "newBoard", "title" to "제출 게시판"),
            )
        }
    }

    @Test
    fun `board notification does not send when reported token is no longer registered`() {
        every { devices.findByUserAndFcmToken(user, "old-token") } returns null
        service.onNewBoardDetected(NewBoardDetected("old-token", "제출 게시판", user.id))
        verify(exactly = 0) { fcm.sendSilentPush(any(), any()) }
    }

    @Test
    fun `board notification rechecks notification setting before sending`() {
        user.notificationEnabled = false
        service.onNewBoardDetected(NewBoardDetected("token", "제출 게시판", user.id))
        verify(exactly = 0) { fcm.sendSilentPush(any(), any()) }
        verify(exactly = 0) { devices.findByUserAndFcmToken(any(), any()) }
    }
}
