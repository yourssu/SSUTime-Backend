package com.ssutime.subject.infrastructure

import com.ssutime.auth.domain.User
import com.ssutime.subject.domain.Enrollment
import com.ssutime.subject.domain.Subject
import org.springframework.data.jpa.repository.JpaRepository

interface EnrollmentRepository : JpaRepository<Enrollment, Long> {
    fun findByUserAndSubject(
        user: User,
        subject: Subject,
    ): Enrollment?

    fun findAllByUser(user: User): List<Enrollment>
}
