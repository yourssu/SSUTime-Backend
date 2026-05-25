package com.ssutime.auth.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(unique = true, nullable = false)
    val authKey: String,
    @Column(nullable = false, length = 8)
    val maskedStudentId: String,
    @Enumerated(EnumType.STRING)
    var academicStatus: AcademicStatus? = null,
    @Column(nullable = false, columnDefinition = "integer default 60")
    var notificationThresholdMinutes: Int = DEFAULT_NOTIFICATION_THRESHOLD_MINUTES,
    @Column(nullable = false, columnDefinition = "boolean default true")
    var notificationEnabled: Boolean = true,
) : BaseEntity() {
    fun updateNotificationSettings(
        enabled: Boolean,
        thresholdMinutes: Int,
    ) {
        require(thresholdMinutes >= 0) { "notificationThresholdMinutes must be non-negative" }
        notificationEnabled = enabled
        notificationThresholdMinutes = thresholdMinutes
    }

    companion object {
        const val DEFAULT_NOTIFICATION_THRESHOLD_MINUTES = 60

        fun create(
            authKey: String,
            maskedStudentId: String,
        ): User =
            User(
                authKey = authKey,
                maskedStudentId = maskedStudentId,
            )
    }
}
