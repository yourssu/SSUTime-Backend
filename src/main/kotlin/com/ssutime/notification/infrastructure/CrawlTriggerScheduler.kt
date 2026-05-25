package com.ssutime.notification.infrastructure

import com.google.firebase.messaging.FirebaseMessagingException
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class CrawlTriggerScheduler(
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val fcmClient: FcmClient,
) {
    @Scheduled(fixedRate = 60_000)
    fun triggerCrawl() {
        val bucket = LocalDateTime.now().minute % 15
        userRepository.findAll()
            .filter { it.id % 15 == bucket.toLong() }
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
