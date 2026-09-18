package com.ssutime.notification.infrastructure

import com.ssutime.notification.domain.NotificationDelivery
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

interface NotificationDeliveryRepository : JpaRepository<NotificationDelivery, Long> {
    @Modifying
    @Transactional
    @Query(
        value =
            """
            INSERT IGNORE INTO notification_deliveries
                (user_device_id, notification_type, scheduled_date, group_key, status, created_at, updated_at)
            VALUES (:userDeviceId, :notificationType, :scheduledDate, :groupKey, 'PENDING', :now, :now)
            """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        userDeviceId: Long,
        notificationType: String,
        scheduledDate: LocalDate,
        groupKey: String,
        now: LocalDateTime,
    ): Int

    @Modifying
    @Transactional
    @Query(
        value =
            """
            UPDATE notification_deliveries
            SET status = 'CLAIMED', claim_token = :claimToken, claimed_at = :now, updated_at = :now
            WHERE user_device_id = :userDeviceId
              AND notification_type = :notificationType
              AND scheduled_date = :scheduledDate
              AND group_key = :groupKey
              AND (status = 'PENDING' OR (status = 'CLAIMED' AND claimed_at < :expiredBefore))
            """,
        nativeQuery = true,
    )
    fun claim(
        userDeviceId: Long,
        notificationType: String,
        scheduledDate: LocalDate,
        groupKey: String,
        claimToken: String,
        now: LocalDateTime,
        expiredBefore: LocalDateTime,
    ): Int

    @Modifying
    @Transactional
    @Query(
        value =
            """
            UPDATE notification_deliveries
            SET status = 'SENT', sent_at = :now, updated_at = :now
            WHERE claim_token = :claimToken AND status = 'CLAIMED'
            """,
        nativeQuery = true,
    )
    fun markSent(
        claimToken: String,
        now: LocalDateTime,
    ): Int

    @Modifying
    @Transactional
    @Query(
        value =
            """
            UPDATE notification_deliveries
            SET status = 'PENDING', claim_token = NULL, claimed_at = NULL, updated_at = :now
            WHERE claim_token = :claimToken AND status = 'CLAIMED'
            """,
        nativeQuery = true,
    )
    fun release(
        claimToken: String,
        now: LocalDateTime,
    ): Int
}
