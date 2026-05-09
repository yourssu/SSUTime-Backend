package com.ssutime.todo.infrastructure

import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.UserTodoStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface UserTodoStatusRepository : JpaRepository<UserTodoStatus, Long> {
    fun findByUserIdAndTodo(userId: Long, todo: Todo): UserTodoStatus?

    fun findAllByTodo(todo: Todo): List<UserTodoStatus>

    fun findAllByUserId(userId: Long): List<UserTodoStatus>

    @Query("SELECT u FROM UserTodoStatus u WHERE u.notifyAt <= :now AND u.notificationSent = false AND u.isCompleted = false")
    fun findPendingNotifications(now: LocalDateTime): List<UserTodoStatus>
}
