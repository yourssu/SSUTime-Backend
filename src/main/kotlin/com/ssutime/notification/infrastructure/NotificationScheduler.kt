package com.ssutime.notification.infrastructure

import com.ssutime.notification.application.NotificationService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class NotificationScheduler(
    private val notificationService: NotificationService,
) {
    // Deadlines are stored in Seoul local time.
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    fun sendDeadlineApproachingNotifications() =
        notificationService.sendDeadlineApproachingNotifications(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
}
