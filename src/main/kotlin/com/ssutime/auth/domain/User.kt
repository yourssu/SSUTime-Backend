package com.ssutime.auth.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(unique = true, nullable = false)
    val studentId: String,
    @Enumerated(EnumType.STRING)
    var academicStatus: AcademicStatus? = null,
) : BaseEntity() {
    companion object {
        fun create(studentId: String): User = User(studentId = studentId)
    }
}
