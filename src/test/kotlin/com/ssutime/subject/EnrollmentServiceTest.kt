package com.ssutime.subject

import com.ssutime.auth.domain.User
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.common.exception.ResourceNotFoundException
import com.ssutime.common.exception.UnauthorizedException
import com.ssutime.subject.application.EnrollmentService
import com.ssutime.subject.domain.Enrollment
import com.ssutime.subject.domain.Subject
import com.ssutime.subject.infrastructure.EnrollmentRepository
import com.ssutime.subject.infrastructure.SubjectRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class EnrollmentServiceTest {
    private val userRepository: UserRepository = mockk()
    private val subjectRepository: SubjectRepository = mockk()
    private val enrollmentRepository: EnrollmentRepository = mockk()
    private val enrollmentService = EnrollmentService(userRepository, subjectRepository, enrollmentRepository)

    @Test
    fun `enroll - creates subject and enrollment when not existing`() {
        val user = User(id = 1L, authKey = "auth-1L", maskedStudentId = "20****01")
        val subject = Subject(id = 1L, courseId = 1001L, name = "데이터구조", semester = "2024-1")
        val enrollment = Enrollment.create(user, subject)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { subjectRepository.findByCourseId(1001L) } returns null
        every { subjectRepository.save(any()) } returns subject
        every { enrollmentRepository.findByUserAndSubject(user, subject) } returns null
        every { enrollmentRepository.save(any()) } returns enrollment

        enrollmentService.enroll(1L, 1001L, "데이터구조", "2024-1")

        verify(exactly = 1) { subjectRepository.save(any()) }
        verify(exactly = 1) { enrollmentRepository.save(any()) }
    }

    @Test
    fun `enroll - reuses existing subject`() {
        val user = User(id = 1L, authKey = "auth-1L", maskedStudentId = "20****01")
        val subject = Subject(id = 1L, courseId = 1001L, name = "데이터구조", semester = "2024-1")
        val enrollment = Enrollment.create(user, subject)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { subjectRepository.findByCourseId(1001L) } returns subject
        every { enrollmentRepository.findByUserAndSubject(user, subject) } returns null
        every { enrollmentRepository.save(any()) } returns enrollment

        enrollmentService.enroll(1L, 1001L, "데이터구조", "2024-1")

        verify(exactly = 0) { subjectRepository.save(any()) }
        verify(exactly = 1) { enrollmentRepository.save(any()) }
    }

    @Test
    fun `enroll - does not duplicate existing enrollment`() {
        val user = User(id = 1L, authKey = "auth-1L", maskedStudentId = "20****01")
        val subject = Subject(id = 1L, courseId = 1001L, name = "데이터구조", semester = "2024-1")
        val existing = Enrollment(id = 5L, user = user, subject = subject)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { subjectRepository.findByCourseId(1001L) } returns subject
        every { enrollmentRepository.findByUserAndSubject(user, subject) } returns existing

        enrollmentService.enroll(1L, 1001L, "데이터구조", "2024-1")

        verify(exactly = 0) { enrollmentRepository.save(any()) }
    }

    @Test
    fun `unenroll - removes enrollment`() {
        val user = User(id = 1L, authKey = "auth-1L", maskedStudentId = "20****01")
        val subject = Subject(id = 1L, courseId = 1001L, name = "데이터구조", semester = "2024-1")
        val enrollment = Enrollment(id = 5L, user = user, subject = subject)
        every { enrollmentRepository.findById(5L) } returns Optional.of(enrollment)
        every { enrollmentRepository.delete(enrollment) } returns Unit

        enrollmentService.unenroll(1L, 5L)

        verify(exactly = 1) { enrollmentRepository.delete(enrollment) }
    }

    @Test
    fun `unenroll - throws when enrollment not found`() {
        every { enrollmentRepository.findById(99L) } returns Optional.empty()

        assertThrows<ResourceNotFoundException> {
            enrollmentService.unenroll(1L, 99L)
        }
    }

    @Test
    fun `unenroll - throws when user does not own enrollment`() {
        val owner = User(id = 2L, authKey = "auth-2L", maskedStudentId = "20****02")
        val subject = Subject(id = 1L, courseId = 1001L, name = "데이터구조", semester = "2024-1")
        val enrollment = Enrollment(id = 5L, user = owner, subject = subject)
        every { enrollmentRepository.findById(5L) } returns Optional.of(enrollment)

        assertThrows<UnauthorizedException> {
            enrollmentService.unenroll(1L, 5L)
        }
    }

    @Test
    fun `getEnrollments - returns list of enrollment responses`() {
        val user = User(id = 1L, authKey = "auth-1L", maskedStudentId = "20****01")
        val subject = Subject(id = 1L, courseId = 1001L, name = "데이터구조", semester = "2024-1")
        val enrollment = Enrollment(id = 5L, user = user, subject = subject)
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { enrollmentRepository.findAllByUser(user) } returns listOf(enrollment)

        val result = enrollmentService.getEnrollments(1L)

        assertEquals(1, result.size)
        assertEquals(1001L, result[0].courseId)
        assertEquals("데이터구조", result[0].name)
        assertEquals("2024-1", result[0].semester)
    }
}
