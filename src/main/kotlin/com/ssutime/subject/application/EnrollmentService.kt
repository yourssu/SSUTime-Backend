package com.ssutime.subject.application

import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.common.exception.ResourceNotFoundException
import com.ssutime.common.exception.UnauthorizedException
import com.ssutime.subject.domain.Enrollment
import com.ssutime.subject.domain.Subject
import com.ssutime.subject.infrastructure.EnrollmentRepository
import com.ssutime.subject.infrastructure.SubjectRepository
import com.ssutime.subject.presentation.EnrollmentResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class EnrollmentService(
    private val userRepository: UserRepository,
    private val subjectRepository: SubjectRepository,
    private val enrollmentRepository: EnrollmentRepository,
) {
    fun enroll(
        userId: Long,
        courseId: Long,
        name: String,
        semester: String,
    ) {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("User not found: $userId") }
        val subject =
            subjectRepository.findByCourseId(courseId)
                ?: subjectRepository.save(Subject.create(courseId, name, semester))
        enrollmentRepository.findByUserAndSubject(user, subject)
            ?: enrollmentRepository.save(Enrollment.create(user, subject))
    }

    fun unenroll(
        userId: Long,
        enrollmentId: Long,
    ) {
        val enrollment =
            enrollmentRepository
                .findById(enrollmentId)
                .orElseThrow { ResourceNotFoundException("Enrollment not found: $enrollmentId") }
        if (enrollment.user.id != userId) {
            throw UnauthorizedException("Not your enrollment")
        }
        enrollmentRepository.delete(enrollment)
    }

    @Transactional(readOnly = true)
    fun getEnrollments(userId: Long): List<EnrollmentResponse> {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("User not found: $userId") }
        return enrollmentRepository.findAllByUser(user).map { enrollment ->
            EnrollmentResponse(
                enrollmentId = enrollment.id,
                courseId = enrollment.subject.courseId,
                name = enrollment.subject.name,
                semester = enrollment.subject.semester,
            )
        }
    }
}
