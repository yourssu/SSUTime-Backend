package com.ssutime.auth.application

import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import com.ssutime.auth.infrastructure.JwtTokenProvider
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.auth.presentation.NotificationSettingsResponse
import com.ssutime.auth.presentation.TokenResponse
import com.ssutime.common.exception.InvalidRequestException
import com.ssutime.common.exception.ResourceNotFoundException
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userTodoStatusRepository: UserTodoStatusRepository,
) {
    fun loginOrRegister(
        authKey: String,
        maskedStudentId: String,
    ): TokenResponse {
        val user =
            userRepository.findByAuthKey(authKey)
                ?: userRepository.save(
                    User.create(
                        authKey = authKey,
                        maskedStudentId = maskedStudentId,
                    ),
                )
        return TokenResponse(jwtTokenProvider.generateToken(user.id))
    }

    fun loginWithCredentials(
        id: String,
        password: String,
    ): TokenResponse {
        val normalizedId = id.trim()
        if (!normalizedId.matches(STUDENT_ID_PATTERN)) {
            throw InvalidRequestException("id must be an 8-digit student id")
        }
        if (password.isBlank()) {
            throw InvalidRequestException("password must not be blank")
        }

        return loginOrRegister(
            authKey = "credentials:${credentialHash(normalizedId, password)}",
            maskedStudentId = normalizedId.maskStudentId(),
        )
    }

    fun registerDevice(
        userId: Long,
        fcmToken: String,
    ) {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("User not found: $userId") }
        userDeviceRepository.findByUserAndFcmToken(user, fcmToken)
            ?: userDeviceRepository.save(UserDevice.create(user, fcmToken))
    }

    @Transactional(readOnly = true)
    fun getNotificationSettings(userId: Long): NotificationSettingsResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("User not found: $userId") }
        return NotificationSettingsResponse(
            notificationEnabled = user.notificationEnabled,
            notificationThresholdMinutes = user.notificationThresholdMinutes,
        )
    }

    fun updateNotificationSettings(
        userId: Long,
        notificationEnabled: Boolean,
        notificationThresholdMinutes: Int,
    ): NotificationSettingsResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("User not found: $userId") }
        user.updateNotificationSettings(
            enabled = notificationEnabled,
            thresholdMinutes = notificationThresholdMinutes,
        )
        userRepository.save(user)
        userTodoStatusRepository.findAllByUserId(userId).forEach { status ->
            status.recalculateNotifyAt(user.notificationThresholdMinutes)
            userTodoStatusRepository.save(status)
        }
        return NotificationSettingsResponse(
            notificationEnabled = user.notificationEnabled,
            notificationThresholdMinutes = user.notificationThresholdMinutes,
        )
    }

    private fun credentialHash(
        id: String,
        password: String,
    ): String {
        val source = "${id.length}:$id:${password.length}:$password"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun String.maskStudentId(): String = replaceRange(2, 6, "****")

    companion object {
        private val STUDENT_ID_PATTERN = Regex("\\d{8}")
    }
}
