package com.ssutime.subject.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "subjects")
class Subject(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(unique = true, nullable = false)
    val courseId: Long,
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false)
    val semester: String,
) : BaseEntity() {
    companion object {
        fun create(
            courseId: Long,
            name: String,
            semester: String,
        ): Subject = Subject(courseId = courseId, name = name, semester = semester)
    }
}
