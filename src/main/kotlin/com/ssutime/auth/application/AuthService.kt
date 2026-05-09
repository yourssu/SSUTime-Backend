package com.ssutime.auth.application

import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import com.ssutime.auth.infrastructure.JwtTokenProvider
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.auth.presentation.TokenResponse
import com.ssutime.common.exception.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    fun loginOrRegister(studentId: String): TokenResponse {
        val user = userRepository.findByStudentId(studentId)
            ?: userRepository.save(User.create(studentId))
        return TokenResponse(jwtTokenProvider.generateToken(user.id))
    }

    fun registerDevice(
        userId: Long,
        fcmToken: String,
    ) {
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found: $userId") }
        userDeviceRepository.findByUserAndFcmToken(user, fcmToken)
            ?: userDeviceRepository.save(UserDevice.create(user, fcmToken))
    }
}
