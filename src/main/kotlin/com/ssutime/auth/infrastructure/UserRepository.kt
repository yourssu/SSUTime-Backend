package com.ssutime.auth.infrastructure

import com.ssutime.auth.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByAuthKey(authKey: String): User?
}
