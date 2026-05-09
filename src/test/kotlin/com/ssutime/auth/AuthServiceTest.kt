package com.ssutime.auth

import com.ssutime.auth.application.AuthService
import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import com.ssutime.auth.infrastructure.JwtTokenProvider
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.common.exception.InvalidRequestException
import com.ssutime.common.exception.ResourceNotFoundException
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.Optional

class AuthServiceTest {
    private val userRepository: UserRepository = mockk()
    private val userDeviceRepository: UserDeviceRepository = mockk()
    private val jwtTokenProvider: JwtTokenProvider = mockk()
    private val userTodoStatusRepository: UserTodoStatusRepository = mockk()
    private val authService = AuthService(userRepository, userDeviceRepository, jwtTokenProvider, userTodoStatusRepository)

    @Test
    fun `loginOrRegister - existing user returns token`() {
        val user = User(id = 1L, authKey = "auth-1", maskedStudentId = "20****01")
        every { userRepository.findByAuthKey("auth-1") } returns user
        every { jwtTokenProvider.generateToken(1L) } returns "jwt-token"

        val response = authService.loginOrRegister("auth-1", "20****01")

        assertEquals("jwt-token", response.accessToken)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `loginOrRegister - new user is created with masked student id and token returned`() {
        val newUser = User(id = 2L, authKey = "auth-2", maskedStudentId = "20****02")
        val userSlot = slot<User>()
        every { userRepository.findByAuthKey("auth-2") } returns null
        every { userRepository.save(capture(userSlot)) } returns newUser
        every { jwtTokenProvider.generateToken(2L) } returns "new-jwt-token"

        val response = authService.loginOrRegister("auth-2", "20****02")

        assertEquals("new-jwt-token", response.accessToken)
        assertEquals("auth-2", userSlot.captured.authKey)
        assertEquals("20****02", userSlot.captured.maskedStudentId)
    }

    @Test
    fun `loginWithCredentials - creates user from credential hash and masked student id`() {
        val authKeySlot = slot<String>()
        val userSlot = slot<User>()
        val savedUser = User(id = 3L, authKey = "credentials:hashed", maskedStudentId = "20****01")
        every { userRepository.findByAuthKey(capture(authKeySlot)) } returns null
        every { userRepository.save(capture(userSlot)) } returns savedUser
        every { jwtTokenProvider.generateToken(3L) } returns "credential-jwt-token"

        val response = authService.loginWithCredentials("20210001", "password-from-client")

        assertEquals("credential-jwt-token", response.accessToken)
        assertEquals(true, authKeySlot.captured.startsWith("credentials:"))
        assertEquals(76, authKeySlot.captured.length)
        assertEquals(authKeySlot.captured, userSlot.captured.authKey)
        assertEquals("20****01", userSlot.captured.maskedStudentId)
    }

    @Test
    fun `loginWithCredentials - same credentials reuse existing user`() {
        val existingUser = User(id = 4L, authKey = "credentials:hashed", maskedStudentId = "20****01")
        every { userRepository.findByAuthKey(any()) } returns existingUser
        every { jwtTokenProvider.generateToken(4L) } returns "existing-credential-jwt-token"

        val response = authService.loginWithCredentials("20210001", "password-from-client")

        assertEquals("existing-credential-jwt-token", response.accessToken)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `loginWithCredentials - creates different auth keys for different passwords`() {
        val firstAuthKey = slot<String>()
        val secondAuthKey = slot<String>()
        val firstUser = User(id = 5L, authKey = "credentials:first", maskedStudentId = "20****01")
        val secondUser = User(id = 6L, authKey = "credentials:second", maskedStudentId = "20****01")
        every { userRepository.findByAuthKey(capture(firstAuthKey)) } returns null
        every { userRepository.save(any()) } returns firstUser
        every { jwtTokenProvider.generateToken(5L) } returns "first-token"

        authService.loginWithCredentials("20210001", "password-1")

        every { userRepository.findByAuthKey(capture(secondAuthKey)) } returns null
        every { userRepository.save(any()) } returns secondUser
        every { jwtTokenProvider.generateToken(6L) } returns "second-token"

        authService.loginWithCredentials("20210001", "password-2")

        assertEquals(false, firstAuthKey.captured == secondAuthKey.captured)
    }

    @Test
    fun `loginWithCredentials - rejects non 8 digit id`() {
        assertThrows<InvalidRequestException> {
            authService.loginWithCredentials("student01", "password-from-client")
        }
    }

    @Test
    fun `loginWithCredentials - rejects blank password`() {
        assertThrows<InvalidRequestException> {
            authService.loginWithCredentials("20210001", " ")
        }
    }

    @Test
    fun `registerDevice - registers new device`() {
        val user = User(id = 1L, authKey = "auth-1", maskedStudentId = "20****01")
        val device = UserDevice.create(user, "fcm-token-abc")
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userDeviceRepository.findByUserAndFcmToken(user, "fcm-token-abc") } returns null
        every { userDeviceRepository.save(any()) } returns device

        authService.registerDevice(1L, "fcm-token-abc")

        verify(exactly = 1) { userDeviceRepository.save(any()) }
    }

    @Test
    fun `registerDevice - does not duplicate existing device`() {
        val user = User(id = 1L, authKey = "auth-1", maskedStudentId = "20****01")
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
        val user = User(id = 1L, authKey = "auth-1", maskedStudentId = "20****01")
        every { userRepository.findByAuthKey("auth-1") } returns user
        every { jwtTokenProvider.generateToken(1L) } returns "token"

        val response = authService.loginOrRegister("auth-1", "20****01")

        assertNotNull(response)
        assertNotNull(response.accessToken)
    }

    @Test
    fun `getNotificationSettings - returns account notification threshold`() {
        val user = User(
            id = 1L,
            authKey = "auth-1",
            maskedStudentId = "20****01",
            notificationEnabled = false,
            notificationThresholdMinutes = 30,
        )
        every { userRepository.findById(1L) } returns Optional.of(user)

        val response = authService.getNotificationSettings(1L)

        assertEquals(false, response.notificationEnabled)
        assertEquals(30, response.notificationThresholdMinutes)
    }

    @Test
    fun `updateNotificationSettings - updates account notification threshold`() {
        val user = User(id = 1L, authKey = "auth-1", maskedStudentId = "20****01")
        val todo = Todo.create(
            subjectId = 10L,
            materialCode = 100001L,
            type = TodoType.ASSIGNMENT,
            dueDate = LocalDateTime.of(2026, 5, 10, 23, 59),
            title = "테스트 과제",
        )
        val status = UserTodoStatus.create(1L, todo, 60)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userRepository.save(user) } returns user
        every { userTodoStatusRepository.findAllByUserId(1L) } returns listOf(status)
        every { userTodoStatusRepository.save(status) } returns status

        val response = authService.updateNotificationSettings(
            userId = 1L,
            notificationEnabled = false,
            notificationThresholdMinutes = 120,
        )

        assertEquals(false, response.notificationEnabled)
        assertEquals(120, response.notificationThresholdMinutes)
        assertEquals(false, user.notificationEnabled)
        assertEquals(120, user.notificationThresholdMinutes)
        assertEquals(todo.dueDate.minusMinutes(120), status.notifyAt)
        verify { userRepository.save(user) }
        verify { userTodoStatusRepository.save(status) }
    }

    @Test
    fun `updateNotificationSettings - rejects negative threshold`() {
        val user = User(id = 1L, authKey = "auth-1", maskedStudentId = "20****01")
        every { userRepository.findById(1L) } returns Optional.of(user)

        assertThrows<IllegalArgumentException> {
            authService.updateNotificationSettings(
                userId = 1L,
                notificationEnabled = true,
                notificationThresholdMinutes = -1,
            )
        }
    }
}
