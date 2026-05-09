package com.ssutime.subject.domain

import com.ssutime.auth.domain.User
import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "enrollments",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "subject_id"])],
)
class Enrollment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    val subject: Subject,
) : BaseEntity() {
    companion object {
        fun create(
            user: User,
            subject: Subject,
        ): Enrollment = Enrollment(user = user, subject = subject)
    }
}
