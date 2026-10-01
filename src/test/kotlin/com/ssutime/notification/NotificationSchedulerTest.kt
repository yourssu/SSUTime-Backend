package com.ssutime.notification

import com.ssutime.notification.application.NotificationService
import com.ssutime.notification.infrastructure.NotificationScheduler
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class NotificationSchedulerTest {
    @Test
    fun `deadline approaching notifications run every minute with Seoul local time`() {
        val schedule =
            NotificationScheduler::class.java
                .getMethod("sendDeadlineApproachingNotifications")
                .getAnnotation(Scheduled::class.java)
        assertEquals("0 * * * * *", schedule.cron)
        assertEquals("Asia/Seoul", schedule.zone)

        val service = mockk<NotificationService>(relaxed = true)
        val now = slot<LocalDateTime>()
        NotificationScheduler(service, mockk(), mockk(), mockk(), mockk()).sendDeadlineApproachingNotifications()
        verify(exactly = 1) { service.sendDeadlineApproachingNotifications(capture(now)) }
        assertTrue(Duration.between(now.captured, LocalDateTime.now(ZoneId.of("Asia/Seoul"))).abs() < Duration.ofMinutes(1))
    }

    @Test
    fun `crawl trigger before deadline approaching runs every minute with Seoul local time`() {
        val schedule =
            NotificationScheduler::class.java
                .getMethod("triggerCrawlBeforeDeadlineApproaching")
                .getAnnotation(Scheduled::class.java)
        assertEquals("0 * * * * *", schedule.cron)
        assertEquals("Asia/Seoul", schedule.zone)

        val service = mockk<NotificationService>(relaxed = true)
        val now = slot<LocalDateTime>()
        NotificationScheduler(service, mockk(), mockk(), mockk(), mockk()).triggerCrawlBeforeDeadlineApproaching()
        verify(exactly = 1) { service.triggerCrawlBeforeDeadlineApproaching(capture(now)) }
        assertTrue(Duration.between(now.captured, LocalDateTime.now(ZoneId.of("Asia/Seoul"))).abs() < Duration.ofMinutes(1))
    }
}
