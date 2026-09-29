package com.ssutime.notification

import com.ssutime.notification.application.NotificationService
import com.ssutime.notification.infrastructure.NotificationScheduler
import com.ssutime.notification.infrastructure.latestScheduledDate
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationSchedulerTest {
    @Test
    fun `scheduler passes Seoul date to notification service`() {
        val service = mockk<NotificationService>(relaxed = true)
        val scheduler = NotificationScheduler(service)
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        scheduler.sendMorningNotifications()
        verify(exactly = 1) { service.sendMorningNotifications(today) }
    }

    @Test
    fun `schedules run at 9 in Seoul`() {
        val morning = NotificationScheduler::class.java.getMethod("sendMorningNotifications").getAnnotation(Scheduled::class.java)
        val retry = NotificationScheduler::class.java.getMethod("retryMissedNotifications").getAnnotation(Scheduled::class.java)
        assertEquals("0 0 9 * * *", morning.cron)
        assertEquals("0 */5 * * * *", retry.cron)
        assertEquals("Asia/Seoul", morning.zone)
        assertEquals("Asia/Seoul", retry.zone)
    }

    @Test
    fun `deadline approaching notifications run every minute with Seoul local time`() {
        val schedule =
            NotificationScheduler::class.java
                .getMethod(
                    "sendDeadlineApproachingNotifications",
                ).getAnnotation(Scheduled::class.java)
        assertEquals("0 * * * * *", schedule.cron)
        assertEquals("Asia/Seoul", schedule.zone)

        val service = mockk<NotificationService>(relaxed = true)
        val now = slot<LocalDateTime>()
        NotificationScheduler(service).sendDeadlineApproachingNotifications()
        verify(exactly = 1) { service.sendDeadlineApproachingNotifications(capture(now)) }
        assertTrue(Duration.between(now.captured, LocalDateTime.now(ZoneId.of("Asia/Seoul"))).abs() < Duration.ofMinutes(1))
    }

    @Test
    fun `retry uses most recent scheduled date`() {
        val zone = ZoneId.of("Asia/Seoul")
        val date = LocalDate.of(2026, 9, 18)
        assertEquals(date.minusDays(1), latestScheduledDate(ZonedDateTime.of(date, LocalTime.of(8, 59), zone), LocalTime.of(9, 0)))
        assertEquals(date, latestScheduledDate(ZonedDateTime.of(date, LocalTime.of(9, 0), zone), LocalTime.of(9, 0)))
    }
}
