package com.ssutime.notification

import com.ssutime.notification.infrastructure.NotificationDeliveryRepository
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.time.LocalDate
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationQueryTest
    @Autowired
    constructor(
        private val repository: UserTodoStatusRepository,
        private val deliveryRepository: NotificationDeliveryRepository,
        private val entityManager: TestEntityManager,
    ) {
        @Test
        fun `threshold query includes due items and excludes past due, sent, completed and future items`() {
            val now = LocalDateTime.of(2026, 9, 18, 14, 0)
            val included = status(now.plusMinutes(30))
            status(now.minusMinutes(1))
            status(now)
            status(now.plusMinutes(61))
            status(now.plusMinutes(10)).notificationSent = true
            status(now.plusMinutes(20)).updateCompletion(true)
            // A zero threshold schedules the alert at the deadline itself, so no pre-deadline alert is sent.
            status(now, thresholdMinutes = 0)
            entityManager.flush()
            entityManager.clear()
            assertEquals(listOf(included.id), repository.findThresholdNotifications(now).map { it.id })
        }

        @Test
        fun `crawl window query includes reminders due in the half open window only`() {
            val start = LocalDateTime.of(2026, 9, 18, 14, 1)
            val end = start.plusMinutes(1)
            val includedWithSeconds = status(start.plusSeconds(30).plusMinutes(60))
            val includedAtEnd = status(end.plusMinutes(60))
            status(start.plusMinutes(60))
            status(end.plusSeconds(1).plusMinutes(60))
            status(end.plusMinutes(60)).notificationSent = true
            status(end.plusMinutes(60)).updateCompletion(true)
            // A zero threshold sends no reminder, so it needs no crawl either.
            status(end, thresholdMinutes = 0)
            entityManager.flush()
            entityManager.clear()
            assertEquals(
                listOf(includedWithSeconds.id, includedAtEnd.id),
                repository.findThresholdNotificationsBetween(start, end).map { it.id }.sorted(),
            )
        }

        @Test
        fun `mark notification sent removes item from threshold query without bumping version`() {
            val now = LocalDateTime.of(2026, 9, 18, 14, 0)
            val item = status(now.plusMinutes(30))
            entityManager.flush()
            entityManager.clear()
            assertEquals(1, repository.markNotificationSent(item.id))
            entityManager.clear()
            assertEquals(emptyList<Long>(), repository.findThresholdNotifications(now).map { it.id })
            assertEquals(item.version, repository.findById(item.id).get().version)
        }

        @Test
        fun `delivery slot is claimed once and stays closed after success`() {
            val date = LocalDate.of(2026, 9, 18)
            val now = LocalDateTime.of(2026, 9, 18, 18, 0)
            assertEquals(1, deliveryRepository.insertIfAbsent(10, "deadlineApproaching", date, "todo:42", now))
            assertEquals(0, deliveryRepository.insertIfAbsent(10, "deadlineApproaching", date, "todo:42", now))
            assertEquals(
                1,
                deliveryRepository.claim(
                    userDeviceId = 10,
                    notificationType = "deadlineApproaching",
                    scheduledDate = date,
                    groupKey = "todo:42",
                    claimToken = "claim-1",
                    now = now,
                    expiredBefore = now.minusMinutes(30),
                ),
            )
            assertEquals(
                0,
                deliveryRepository.claim(
                    userDeviceId = 10,
                    notificationType = "deadlineApproaching",
                    scheduledDate = date,
                    groupKey = "todo:42",
                    claimToken = "claim-2",
                    now = now,
                    expiredBefore = now.minusMinutes(30),
                ),
            )
            assertEquals(
                false,
                deliveryRepository.existsByUserDeviceIdAndNotificationTypeAndScheduledDateAndGroupKeyAndStatus(
                    10,
                    "deadlineApproaching",
                    date,
                    "todo:42",
                    "SENT",
                ),
            )
            assertEquals(1, deliveryRepository.markSent("claim-1", now.plusSeconds(1)))
            assertEquals(
                true,
                deliveryRepository.existsByUserDeviceIdAndNotificationTypeAndScheduledDateAndGroupKeyAndStatus(
                    10,
                    "deadlineApproaching",
                    date,
                    "todo:42",
                    "SENT",
                ),
            )
            assertEquals(
                0,
                deliveryRepository.claim(
                    10,
                    "deadlineApproaching",
                    date,
                    "todo:42",
                    "claim-3",
                    now.plusHours(1),
                    now.plusMinutes(30),
                ),
            )
        }

        @Test
        fun `failed delivery can be claimed again`() {
            val date = LocalDate.of(2026, 9, 18)
            val now = LocalDateTime.of(2026, 9, 18, 9, 0)
            deliveryRepository.insertIfAbsent(10, "deadlineApproaching", date, "todo:42", now)
            assertEquals(1, deliveryRepository.claim(10, "deadlineApproaching", date, "todo:42", "claim-1", now, now.minusMinutes(30)))
            assertEquals(1, deliveryRepository.release("claim-1", now.plusSeconds(1)))
            assertEquals(
                1,
                deliveryRepository.claim(10, "deadlineApproaching", date, "todo:42", "claim-2", now.plusMinutes(1), now.minusMinutes(29)),
            )
        }

        private var materialCode = 0L

        private fun status(
            due: LocalDateTime,
            thresholdMinutes: Int = 60,
        ): UserTodoStatus {
            val todo = entityManager.persist(Todo.create(10, ++materialCode, TodoType.ASSIGNMENT, due, "Assignment"))
            return entityManager.persist(UserTodoStatus.create(1, todo, thresholdMinutes))
        }
    }
