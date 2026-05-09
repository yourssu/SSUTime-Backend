package com.ssutime.auth

import com.ssutime.auth.application.AuthService
import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import com.ssutime.auth.infrastructure.JwtTokenProvider
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.common.exception.ResourceNotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class AuthServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userDeviceRepository: UserDeviceRepository = mockk()
    private val jwtTokenProvider: JwtTokenProvider = mockk()
    private val authService = AuthService(userRepository, userDeviceRepository, jwtTokenProvider)

    @Test
    fun `loginOrRegister - existing user returns token`() {
        val user = User(id = 1L, studentId = "20210001")
        every { userRepository.findByStudentId("20210001") } returns user
        every { jwtTokenProvider.generateToken(1L) } returns "jwt-token"

        val response = authService.loginOrRegister("20210001")

        assertEquals("jwt-token", response.accessToken)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `loginOrRegister - new user is created and token returned`() {
        val newUser = User(id = 2L, studentId = "20210002")
        every { userRepository.findByStudentId("20210002") } returns null
        every { userRepository.save(any()) } returns newUser
        every { jwtTokenProvider.generateToken(2L) } returns "new-jwt-token"

        val response = authService.loginOrRegister("20210002")

        assertEquals("new-jwt-token", response.accessToken)
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `registerDevice - registers new device`() {
        val user = User(id = 1L, studentId = "20210001")
        val device = UserDevice.create(user, "fcm-token-abc")
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userDeviceRepository.findByUserAndFcmToken(user, "fcm-token-abc") } returns null
        every { userDeviceRepository.save(any()) } returns device

        authService.registerDevice(1L, "fcm-token-abc")

        verify(exactly = 1) { userDeviceRepository.save(any()) }
    }

    @Test
    fun `registerDevice - does not duplicate existing device`() {
        val user = User(id = 1L, studentId = "20210001")
        val existingDevice = UserDevice.create(user, "fcm-token-abc")
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userDeviceRepository.findByUserAndFcmToken(user, "fcm-token-abc") } returns existingDevice

        authService.registerDevice(1L, "fcm-token-abc")

        verify(exactly = 0) { userDeviceRepository.save(any()) }
    }

    @Test
    fun `registerDevice - throws when user not found`() {
        every { userRepository.findById(99L) } returns Optional.empty()

        assertThrows<ResourceNotFoundException> {
            authService.registerDevice(99L, "token")
        }
    }

    @Test
    fun `loginOrRegister - returns non-null token response`() {
        val user = User(id = 1L, studentId = "20210001")
        every { userRepository.findByStudentId("20210001") } returns user
        every { jwtTokenProvider.generateToken(1L) } returns "token"

        val response = authService.loginOrRegister("20210001")

        assertNotNull(response)
        assertNotNull(response.accessToken)
    }
}
