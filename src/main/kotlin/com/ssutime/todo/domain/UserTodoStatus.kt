package com.ssutime.todo.domain

import com.ssutime.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@Entity
@Table(
    name = "user_todo_status",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "todo_id"])],
    indexes = [Index(name = "idx_notify_at_sent", columnList = "notify_at, notification_sent")],
)
@DynamicUpdate
class UserTodoStatus private constructor(
    @Column(nullable = false)
    val userId: Long,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id", nullable = false)
    val todo: Todo,
    @Column(nullable = false)
    var isCompleted: Boolean = false,
    var completedAt: LocalDateTime? = null,
    @Column(nullable = false)
    var isManuallyCompleted: Boolean = false,
    @Column(nullable = false)
    var notifyAt: LocalDateTime,
    @Column(nullable = false)
    var notificationSent: Boolean = false,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Version
    var version: Long = 0

    fun recalculateNotifyAt(notificationThresholdMinutes: Int) {
        require(notificationThresholdMinutes >= 0) { "notificationThresholdMinutes must be non-negative" }
        notifyAt = todo.dueDate - notificationThresholdMinutes.minutes.toJavaDuration()
    }

    fun markNotificationSent() {
        notificationSent = true
    }

    fun updateCompletion(completed: Boolean): Boolean {
        if (isCompleted == completed) return false

        isCompleted = completed
        completedAt = if (completed) LocalDateTime.now() else null
        return true
    }

    fun updateCompletionFromReport(completed: Boolean): Boolean {
        if (!completed && isManuallyCompleted) return false

        val manualStateChanged = completed && isManuallyCompleted
        if (completed) isManuallyCompleted = false
        return updateCompletion(completed) || manualStateChanged
    }

    fun updateCompletionManually(completed: Boolean): Boolean {
        val manualStateChanged = isManuallyCompleted != completed
        isManuallyCompleted = completed
        return updateCompletion(completed) || manualStateChanged
    }

    companion object {
        fun create(
            userId: Long,
            todo: Todo,
            notificationThresholdMinutes: Int,
        ): UserTodoStatus {
            require(notificationThresholdMinutes >= 0) { "notificationThresholdMinutes must be non-negative" }
            return UserTodoStatus(
                userId = userId,
                todo = todo,
                notifyAt = todo.dueDate - notificationThresholdMinutes.minutes.toJavaDuration(),
            )
        }
    }
}
