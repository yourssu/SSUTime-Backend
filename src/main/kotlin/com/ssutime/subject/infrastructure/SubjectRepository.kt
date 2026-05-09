package com.ssutime.subject.infrastructure

import com.ssutime.subject.domain.Subject
import org.springframework.data.jpa.repository.JpaRepository

interface SubjectRepository : JpaRepository<Subject, Long> {
    fun findByCourseId(courseId: Long): Subject?
}
