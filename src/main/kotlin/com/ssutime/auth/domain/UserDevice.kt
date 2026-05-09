package com.ssutime.auth.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "user_devices",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "fcm_token"])],
)
class UserDevice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @Column(nullable = false)
    val fcmToken: String,
) : BaseEntity() {
    companion object {
        fun create(
            user: User,
            fcmToken: String,
        ): UserDevice = UserDevice(user = user, fcmToken = fcmToken)
    }
}
