package com.ssutime.todo.infrastructure

import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.UserTodoStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
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
}
