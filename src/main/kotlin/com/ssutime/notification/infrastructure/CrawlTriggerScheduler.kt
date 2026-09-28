package com.ssutime.notification.infrastructure

import com.google.firebase.messaging.FirebaseMessagingException
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
class CrawlTriggerScheduler(
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val fcmClient: FcmClient,
    @Value("\${notification.crawl-trigger.interval-minutes:15}")
    private val crawlTriggerIntervalMinutes: Long,
    @Value("\${notification.crawl-trigger.zone-id:Asia/Seoul}")
    private val crawlTriggerZoneId: String,
) {
    @Scheduled(fixedRate = 60_000)
    fun triggerCrawl() {
        val zoneId = ZoneId.of(crawlTriggerZoneId)
        val now = ZonedDateTime.now(zoneId)
        if (!isAutomaticCrawlAllowed(now.toLocalDate(), now.toLocalTime())) return

        val epochMinute = now.toInstant().epochSecond / 60
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

private val FIRST_SEMESTER_START = MonthDay.of(3, 2)
private val FIRST_SEMESTER_END = MonthDay.of(7, 21)
private val SECOND_SEMESTER_START = MonthDay.of(9, 1)
private val SECOND_SEMESTER_END = MonthDay.of(1, 21)

internal fun isAutomaticCrawlAllowed(
    localDate: LocalDate,
    localTime: LocalTime,
): Boolean = isSemesterInSession(localDate) && !localTime.isBefore(LocalTime.of(6, 0))

internal fun isSemesterInSession(localDate: LocalDate): Boolean {
    val monthDay = MonthDay.from(localDate)
    val isFirstSemester = monthDay >= FIRST_SEMESTER_START && monthDay <= FIRST_SEMESTER_END
    val isSecondSemester = monthDay >= SECOND_SEMESTER_START || monthDay <= SECOND_SEMESTER_END
    return isFirstSemester || isSecondSemester
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
