package com.ssutime.notification.infrastructure

import com.google.firebase.messaging.FirebaseMessagingException
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.notification.application.NotificationService
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class NotificationScheduler(
    private val notificationService: NotificationService,
    private val userTodoStatusRepository: UserTodoStatusRepository,
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val fcmClient: FcmClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    fun sendDailyTodos() {
        val seoul = ZoneId.of("Asia/Seoul")
        val until = LocalDate.now(seoul).atTime(6, 0).atZone(seoul)
        // createdAt uses the server's local time, so convert the Seoul window before querying.
        val from = until.minusDays(1).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        val end = until.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        userTodoStatusRepository.findNewTodos(from, end).groupBy { it.userId }.forEach { (userId, items) ->
            val user = userRepository.findById(userId).orElse(null) ?: return@forEach
            if (!user.notificationEnabled) return@forEach
            val title = "새 할 일 ${items.size}개"
            val body = items.first().todo.title + if (items.size > 1) " 외 ${items.size - 1}개" else ""
            userDeviceRepository.findAllByUser(user).forEach { device ->
                try {
                    fcmClient.sendPush(device.fcmToken, title, body)
                } catch (exception: FirebaseMessagingException) {
                    log.warn("Daily todo notification could not be sent", exception)
                }
            }
        }
    }

    // Deadlines are stored in Seoul local time.
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    fun sendDeadlineApproachingNotifications() =
        notificationService.sendDeadlineApproachingNotifications(LocalDateTime.now(ZoneId.of("Asia/Seoul")))

    // Clients crawl LMS two minutes ahead so todos completed just before the reminder are excluded.
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    fun triggerCrawlBeforeDeadlineApproaching() =
        notificationService.triggerCrawlBeforeDeadlineApproaching(LocalDateTime.now(ZoneId.of("Asia/Seoul")))
}
