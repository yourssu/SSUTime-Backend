package com.ssutime.notification.infrastructure

import com.google.firebase.messaging.FirebaseMessagingException
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class CrawlTriggerScheduler(
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val fcmClient: FcmClient,
    @Value("\${notification.crawl-trigger.interval-minutes:15}")
    private val crawlTriggerIntervalMinutes: Long,
) {
    @Scheduled(fixedRate = 60_000)
    fun triggerCrawl() {
        val epochMinute = Instant.now().epochSecond / 60
        userRepository
            .findAll()
            .filter { shouldTriggerCrawlForUser(it.id, crawlTriggerIntervalMinutes, epochMinute) }
            .forEach { user ->
                userDeviceRepository.findAllByUser(user).forEach { device ->
                    try {
                        fcmClient.sendSilentPush(device.fcmToken, mapOf("action" to "crawl_lms"))
                    } catch (_: FirebaseMessagingException) {
                        userDeviceRepository.delete(device)
                    }
                }
            }
    }
}

internal fun shouldTriggerCrawlForUser(
    userId: Long,
    intervalMinutes: Long,
    epochMinute: Long,
): Boolean {
    val safeIntervalMinutes = intervalMinutes.coerceAtLeast(1)
    return Math.floorMod(userId, safeIntervalMinutes) ==
        Math.floorMod(epochMinute, safeIntervalMinutes)
}
