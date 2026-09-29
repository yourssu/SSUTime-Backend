package com.ssutime.notification

import com.google.firebase.ErrorCode
import com.google.firebase.messaging.FirebaseMessagingException
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
import org.junit.jupiter.api.assertThrows
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.LocalDateTime
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
    private val now = LocalDateTime.of(2026, 9, 19, 23, 0)
    private val user = User(id = 1, authKey = "key", maskedStudentId = "20****01")
    private val messages = mutableListOf<Map<String, String>>()
    private val deliveredTokens = mutableListOf<String>()
    private var nextTodoId = 40L

    @BeforeEach
    fun setUp() {
        every { users.findById(1) } returns Optional.of(user)
        every { devices.findAllByUser(user) } returns listOf(UserDevice.create(user, "token"))
        every { subjects.findAllById(any()) } returns listOf(Subject(10, 100, "데이터사이언스", "2026-2"))
        every { fcm.sendSilentPush(any(), any()) } answers {
            deliveredTokens.add(arg<String>(0))
            messages.add(arg<Map<String, String>>(1))
            Unit
        }
        every { statuses.findThresholdNotifications(any()) } returns emptyList()
        every { statuses.markNotificationSent(any()) } returns 1
        every { deliveries.insertIfAbsent(any(), any(), any(), any(), any()) } returns 1
        every { deliveries.claim(any(), any(), any(), any(), any(), any(), any()) } returns 1
        every { deliveries.markSent(any(), any()) } returns 1
        every { deliveries.release(any(), any()) } returns 1
        every {
            deliveries.existsByUserDeviceIdAndNotificationTypeAndScheduledDateAndGroupKeyAndStatus(any(), any(), any(), any(), "SENT")
        } returns true
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
    fun `deadline approaching sends each item with source data and legacy action`() {
        every { statuses.findThresholdNotifications(now) } returns listOf(item(1))

        service.sendDeadlineApproachingNotifications(now)

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
    fun `deadline approaching sends items separately and marks each sent`() {
        val quiz = item(1, TodoType.QUIZ)
        val lecture = item(1, TodoType.COMMONS)
        ReflectionTestUtils.setField(quiz, "id", 7L)
        ReflectionTestUtils.setField(lecture, "id", 8L)
        every { statuses.findThresholdNotifications(now) } returns listOf(quiz, lecture)

        service.sendDeadlineApproachingNotifications(now)

        assertEquals(listOf("41", "42"), messages.map { it["todo_id"] })
        assertEquals(listOf("QUIZ", "COMMONS"), messages.map { it["todo_type"] })
        verify(exactly = 1) { deliveries.claim(any(), "deadlineApproaching", today.plusDays(1), "todo:41", any(), any(), any()) }
        verify(exactly = 1) { statuses.markNotificationSent(7) }
        verify(exactly = 1) { statuses.markNotificationSent(8) }
    }

    @Test
    fun `missing subject does not abort remaining notifications`() {
        every { subjects.findAllById(any()) } returns emptyList()
        every { statuses.findThresholdNotifications(any()) } returns listOf(item(1), item(1, TodoType.QUIZ))
        service.sendDeadlineApproachingNotifications(now)
        assertEquals(2, messages.size)
        assertTrue(messages.all { "subject_name" !in it })
    }

    @Test
    fun `failed device does not prevent other devices and leaves item unsent for retry`() {
        every { devices.findAllByUser(user) } returns
            listOf(UserDevice.create(user, "broken"), UserDevice.create(user, "token"))
        every { fcm.sendSilentPush("broken", any()) } throws messagingFailure()
        every { statuses.findThresholdNotifications(any()) } returns listOf(item(1))

        service.sendDeadlineApproachingNotifications(now)

        assertEquals(listOf("token"), deliveredTokens)
        verify(exactly = 1) { deliveries.release(any(), any()) }
        verify(exactly = 0) { statuses.markNotificationSent(any()) }
    }

    @Test
    fun `unexpected programming error is propagated instead of treated as delivery failure`() {
        every { statuses.findThresholdNotifications(any()) } returns listOf(item(1))
        every { fcm.sendSilentPush(any(), any()) } throws IllegalStateException("Unexpected configuration error")

        assertThrows<IllegalStateException> { service.sendDeadlineApproachingNotifications(now) }

        verify(exactly = 0) { deliveries.markSent(any(), any()) }
        verify(exactly = 0) { deliveries.release(any(), any()) }
        verify(exactly = 0) { statuses.markNotificationSent(any()) }
    }

    @Test
    fun `same delivery slot is not sent twice`() {
        every { statuses.findThresholdNotifications(any()) } returns listOf(item(1))
        every {
            deliveries.claim(any(), "deadlineApproaching", today.plusDays(1), "todo:41", any(), any(), any())
        } returnsMany listOf(1, 0)

        service.sendDeadlineApproachingNotifications(now)
        service.sendDeadlineApproachingNotifications(now)

        assertEquals(1, messages.size)
        verify(exactly = 1) { deliveries.markSent(any(), any()) }
    }

    @Test
    fun `deadline approaching already sent to a device is not sent again but is marked sent`() {
        every { statuses.findThresholdNotifications(any()) } returns listOf(item(1))
        every { deliveries.claim(any(), any(), any(), any(), any(), any(), any()) } returns 0

        service.sendDeadlineApproachingNotifications(now)

        assertTrue(messages.isEmpty())
        verify(exactly = 1) { statuses.markNotificationSent(any()) }
    }

    @Test
    fun `deadline approaching claimed by an unfinished run stays unsent so it is retried`() {
        every { statuses.findThresholdNotifications(any()) } returns listOf(item(1))
        every { deliveries.claim(any(), any(), any(), any(), any(), any(), any()) } returns 0
        every {
            deliveries.existsByUserDeviceIdAndNotificationTypeAndScheduledDateAndGroupKeyAndStatus(any(), any(), any(), any(), "SENT")
        } returns false

        service.sendDeadlineApproachingNotifications(now)

        assertTrue(messages.isEmpty())
        verify(exactly = 0) { statuses.markNotificationSent(any()) }
    }

    @Test
    fun `deadline approaching skips disabled user without marking sent`() {
        user.notificationEnabled = false
        every { statuses.findThresholdNotifications(any()) } returns listOf(item(1))

        service.sendDeadlineApproachingNotifications(now)

        assertTrue(messages.isEmpty())
        verify(exactly = 0) { statuses.markNotificationSent(any()) }
    }

    @Test
    fun `deadline approaching for user without devices is marked sent`() {
        every { devices.findAllByUser(user) } returns emptyList()
        val pending = item(1)
        every { statuses.findThresholdNotifications(any()) } returns listOf(pending)

        service.sendDeadlineApproachingNotifications(now)

        verify(exactly = 1) { statuses.markNotificationSent(pending.id) }
    }

    private fun messagingFailure(): FirebaseMessagingException =
        // Firebase exposes no public constructor for messaging exceptions.
        FirebaseMessagingException::class.java
            .getDeclaredConstructor(ErrorCode::class.java, String::class.java)
            .apply { isAccessible = true }
            .newInstance(ErrorCode.UNAVAILABLE, "FCM unavailable")

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
