package com.ssutime.notification

import com.ssutime.notification.infrastructure.shouldTriggerCrawlForUser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrawlTriggerSchedulerTest {
    @Test
    fun `shouldTriggerCrawlForUser - interval 1 sends every user every minute`() {
        assertTrue(
            shouldTriggerCrawlForUser(userId = 1L, intervalMinutes = 1L, epochMinute = 100L),
        )
        assertTrue(
            shouldTriggerCrawlForUser(userId = 2L, intervalMinutes = 1L, epochMinute = 100L),
        )
        assertTrue(
            shouldTriggerCrawlForUser(userId = 999L, intervalMinutes = 1L, epochMinute = 101L),
        )
    }

    @Test
    fun `shouldTriggerCrawlForUser - interval 15 keeps one bucket per minute`() {
        val epochMinute = 100L
        val matchingUserId = 10L
        val nonMatchingUserId = 11L

        assertTrue(
            shouldTriggerCrawlForUser(matchingUserId, intervalMinutes = 15L, epochMinute = epochMinute),
        )
        assertFalse(
            shouldTriggerCrawlForUser(nonMatchingUserId, intervalMinutes = 15L, epochMinute = epochMinute),
        )
    }

    @Test
    fun `shouldTriggerCrawlForUser - invalid interval falls back to every minute`() {
        assertTrue(
            shouldTriggerCrawlForUser(userId = 1L, intervalMinutes = 0L, epochMinute = 100L),
        )
        assertTrue(
            shouldTriggerCrawlForUser(userId = 2L, intervalMinutes = -1L, epochMinute = 100L),
        )
    }
}
