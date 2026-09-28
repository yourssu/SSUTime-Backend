package com.ssutime.notification.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "notification_deliveries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notification_delivery_slot",
            columnNames = ["user_device_id", "notification_type", "scheduled_date", "group_key"],
        ),
    ],
    indexes = [
        Index(name = "idx_notification_delivery_claim", columnList = "status,claimed_at"),
        Index(name = "idx_notification_delivery_claim_token", columnList = "claim_token"),
    ],
)
class NotificationDelivery(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_device_id", nullable = false)
    val userDeviceId: Long,
    @Column(name = "notification_type", nullable = false, length = 32)
    val notificationType: String,
    @Column(name = "scheduled_date", nullable = false)
    val scheduledDate: LocalDate,
    @Column(name = "group_key", nullable = false, length = 64)
    val groupKey: String,
    @Column(nullable = false, length = 16)
    val status: String = "PENDING",
    @Column(name = "claim_token", length = 36)
    val claimToken: String? = null,
    @Column(name = "claimed_at")
    val claimedAt: LocalDateTime? = null,
    @Column(name = "sent_at")
    val sentAt: LocalDateTime? = null,
) : BaseEntity()
