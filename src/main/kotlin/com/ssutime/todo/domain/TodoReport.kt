package com.ssutime.todo.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "todo_report")
class TodoReport private constructor(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val subjectId: Long,

    @Column(nullable = false)
    val materialCode: String,

    @Column(nullable = false)
    var dueDate: LocalDateTime,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    val reportedAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    companion object {
        fun create(
            userId: Long,
            subjectId: Long,
            materialCode: String,
            dueDate: LocalDateTime,
            title: String,
        ): TodoReport {
            require(materialCode.isNotBlank()) { "materialCode must not be blank" }
            require(title.isNotBlank()) { "title must not be blank" }
            return TodoReport(
                userId = userId,
                subjectId = subjectId,
                materialCode = materialCode,
                dueDate = dueDate,
                title = title,
            )
        }
    }
}
