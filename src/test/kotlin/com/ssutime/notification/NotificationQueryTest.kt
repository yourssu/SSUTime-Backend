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
        fun `deadline window is half open and excludes completed items regardless of legacy sent flag`() {
            val start = LocalDateTime.of(2026, 9, 19, 0, 0)
            val end = start.plusDays(3)
            val included = status(start)
            included.notificationSent = true
            status(start.minusNanos(1000000))
            status(end)
            status(start.plusDays(1)).updateCompletion(true)
            entityManager.flush()
            entityManager.clear()
            assertEquals(
                listOf(included.id),
                repository.findDeadlineNotifications(start, end, included.createdAt.plusSeconds(1)).map { it.id },
            )
        }

        @Test
        fun `deadline retry excludes items registered at or after scheduled cutoff`() {
            val day = LocalDate.of(2026, 9, 18)
            for (hour in listOf(9, 18)) {
                val cutoff = day.atTime(hour, 0)
                val due = day.plusDays(if (hour == 9) 0 else 1).atTime(23, 59)
                val existing = status(due)
                val atCutoff = status(due)
                val late = status(due)
                entityManager.flush()
                for ((item, createdAt) in listOf(
                    existing to cutoff.minusSeconds(1),
                    atCutoff to cutoff,
                    late to day.atTime(23, 1),
                )) {
                    entityManager.entityManager
                        .createQuery("UPDATE UserTodoStatus u SET u.createdAt = :time WHERE u.id = :id")
                        .setParameter("time", createdAt)
                        .setParameter("id", item.id)
                        .executeUpdate()
                }
                entityManager.clear()
                val start = due.toLocalDate().atStartOfDay()
                repeat(2) {
                    assertEquals(
                        listOf(existing.id),
                        repository.findDeadlineNotifications(start, start.plusDays(1), cutoff).map { it.id },
                    )
                }
            }
        }

        @Test
        fun `new collection window includes previous 18 and excludes current 18`() {
            val end = LocalDateTime.of(2026, 9, 18, 18, 0)
            val start = end.minusDays(1)
            val included = status(end.plusDays(1))
            val excluded = status(end.plusDays(2))
            entityManager.flush()
            entityManager.entityManager
                .createQuery("UPDATE UserTodoStatus u SET u.createdAt = :time WHERE u.id = :id")
                .setParameter("time", start)
                .setParameter("id", included.id)
                .executeUpdate()
            entityManager.entityManager
                .createQuery("UPDATE UserTodoStatus u SET u.createdAt = :time WHERE u.id = :id")
                .setParameter("time", end)
                .setParameter("id", excluded.id)
                .executeUpdate()
            entityManager.clear()
            assertEquals(listOf(included.id), repository.findNewNotifications(start, end).map { it.id })
        }

        @Test
        fun `delivery slot is claimed once and stays closed after success`() {
            val date = LocalDate.of(2026, 9, 18)
            val now = LocalDateTime.of(2026, 9, 18, 18, 0)
            assertEquals(1, deliveryRepository.insertIfAbsent(10, "deadlineApproaching", date, "group", now))
            assertEquals(0, deliveryRepository.insertIfAbsent(10, "deadlineApproaching", date, "group", now))
            assertEquals(
                1,
                deliveryRepository.claim(
                    userDeviceId = 10,
                    notificationType = "deadlineApproaching",
                    scheduledDate = date,
                    groupKey = "group",
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
                    groupKey = "group",
                    claimToken = "claim-2",
                    now = now,
                    expiredBefore = now.minusMinutes(30),
                ),
            )
            assertEquals(1, deliveryRepository.markSent("claim-1", now.plusSeconds(1)))
            assertEquals(
                0,
                deliveryRepository.claim(
                    10,
                    "deadlineApproaching",
                    date,
                    "group",
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
            deliveryRepository.insertIfAbsent(10, "dueToday", date, "todo:42", now)
            assertEquals(1, deliveryRepository.claim(10, "dueToday", date, "todo:42", "claim-1", now, now.minusMinutes(30)))
            assertEquals(1, deliveryRepository.release("claim-1", now.plusSeconds(1)))
            assertEquals(
                1,
                deliveryRepository.claim(10, "dueToday", date, "todo:42", "claim-2", now.plusMinutes(1), now.minusMinutes(29)),
            )
        }

        private var materialCode = 0L

        private fun status(due: LocalDateTime): UserTodoStatus {
            val todo = entityManager.persist(Todo.create(10, ++materialCode, TodoType.ASSIGNMENT, due, "Assignment"))
            return entityManager.persist(UserTodoStatus.create(1, todo, 60))
        }
    }
