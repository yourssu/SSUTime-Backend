package com.ssutime.todo.infrastructure

import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.UserTodoStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

interface UserTodoStatusRepository : JpaRepository<UserTodoStatus, Long> {
    fun findByUserIdAndTodo(
        userId: Long,
        todo: Todo,
    ): UserTodoStatus?

    fun findAllByTodo(todo: Todo): List<UserTodoStatus>

    fun findAllByUserId(userId: Long): List<UserTodoStatus>

    @Query(
        "SELECT u FROM UserTodoStatus u JOIN FETCH u.todo t " +
            "WHERE u.isCompleted = false AND t.dueDate >= :start AND t.dueDate < :end AND u.createdAt < :createdBefore",
    )
    fun findDeadlineNotifications(
        start: LocalDateTime,
        end: LocalDateTime,
        createdBefore: LocalDateTime,
    ): List<UserTodoStatus>

    @Query(
        "SELECT u FROM UserTodoStatus u JOIN FETCH u.todo t WHERE u.isCompleted = false AND u.createdAt >= :start AND u.createdAt < :end",
    )
    fun findNewNotifications(
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<UserTodoStatus>

    @Query(
        "SELECT u FROM UserTodoStatus u JOIN FETCH u.todo t " +
            "WHERE u.notifyAt <= :now AND u.notificationSent = false AND u.isCompleted = false AND t.dueDate > :now",
    )
    fun findThresholdNotifications(now: LocalDateTime): List<UserTodoStatus>

    @Query(
        "SELECT u FROM UserTodoStatus u JOIN FETCH u.todo t " +
            "WHERE u.notifyAt > :start AND u.notifyAt <= :end AND u.notificationSent = false " +
            "AND u.isCompleted = false AND t.dueDate > u.notifyAt",
    )
    fun findThresholdNotificationsBetween(
        start: LocalDateTime,
        end: LocalDateTime,
    ): List<UserTodoStatus>

    // Bulk update keeps the version untouched so it does not conflict with concurrent reconcile updates.
    @Modifying
    @Transactional
    @Query("UPDATE UserTodoStatus u SET u.notificationSent = true WHERE u.id = :id")
    fun markNotificationSent(id: Long): Int
}
