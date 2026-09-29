package com.ssutime.notification.infrastructure

import com.ssutime.notification.application.NotificationService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
class NotificationScheduler(
    private val notificationService: NotificationService,
) {
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    fun sendMorningNotifications() = notificationService.sendMorningNotifications(LocalDate.now(ZoneId.of("Asia/Seoul")))

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    fun retryMissedNotifications() {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
        notificationService.sendMorningNotifications(latestScheduledDate(now, LocalTime.of(9, 0)))
    }

    // Deadlines are stored in Seoul local time.
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    fun sendDeadlineApproachingNotifications() =
        notificationService.sendDeadlineApproachingNotifications(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
}

internal fun latestScheduledDate(
    now: ZonedDateTime,
    scheduledTime: LocalTime,
): LocalDate = if (now.toLocalTime().isBefore(scheduledTime)) now.toLocalDate().minusDays(1) else now.toLocalDate()
