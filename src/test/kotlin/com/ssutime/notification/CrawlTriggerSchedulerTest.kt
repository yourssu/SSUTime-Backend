package com.ssutime.notification

import com.ssutime.notification.infrastructure.isAutomaticCrawlAllowed
import com.ssutime.notification.infrastructure.isSemesterInSession
import com.ssutime.notification.infrastructure.shouldTriggerCrawlForUser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

class CrawlTriggerSchedulerTest {
    @Test
    fun `automatic crawl is blocked from midnight until 6 AM`() {
        val semesterDate = LocalDate.of(2026, 3, 2)

        assertFalse(isAutomaticCrawlAllowed(semesterDate, LocalTime.MIDNIGHT))
        assertFalse(isAutomaticCrawlAllowed(semesterDate, LocalTime.of(3, 0)))
        assertFalse(isAutomaticCrawlAllowed(semesterDate, LocalTime.of(5, 59, 59)))
        assertTrue(isAutomaticCrawlAllowed(semesterDate, LocalTime.of(6, 0)))
        assertTrue(isAutomaticCrawlAllowed(semesterDate, LocalTime.of(23, 59, 59)))
    }

    @Test
    fun `automatic crawl is allowed from March 2 through July 21`() {
        assertFalse(isSemesterInSession(LocalDate.of(2026, 3, 1)))
        assertTrue(isSemesterInSession(LocalDate.of(2026, 3, 2)))
        assertTrue(isSemesterInSession(LocalDate.of(2026, 7, 21)))
        assertFalse(isSemesterInSession(LocalDate.of(2026, 7, 22)))
    }

    @Test
    fun `automatic crawl is allowed from September 1 through January 21`() {
        assertFalse(isSemesterInSession(LocalDate.of(2026, 8, 31)))
        assertTrue(isSemesterInSession(LocalDate.of(2026, 9, 1)))
        assertTrue(isSemesterInSession(LocalDate.of(2027, 1, 21)))
        assertFalse(isSemesterInSession(LocalDate.of(2027, 1, 22)))
    }

    @Test
    fun `automatic crawl remains blocked all day during vacation`() {
        val vacationDate = LocalDate.of(2026, 8, 1)

        assertFalse(isAutomaticCrawlAllowed(vacationDate, LocalTime.of(6, 0)))
        assertFalse(isAutomaticCrawlAllowed(vacationDate, LocalTime.of(23, 59, 59)))
    }

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
