package com.ssutime.auth.infrastructure

import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import org.springframework.data.jpa.repository.JpaRepository

interface UserDeviceRepository : JpaRepository<UserDevice, Long> {
    fun findByUserAndFcmToken(
        user: User,
        fcmToken: String,
    ): UserDevice?

    fun findAllByUser(user: User): List<UserDevice>
}
