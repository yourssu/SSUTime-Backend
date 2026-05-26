package com.ssutime.todo.application

import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.common.exception.ResourceNotFoundException
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoReport
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import com.ssutime.todo.domain.event.TodoReported
import com.ssutime.todo.infrastructure.TodoReportRepository
import com.ssutime.todo.infrastructure.TodoRepository
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class TodoService(
    private val userRepository: UserRepository,
    private val todoRepository: TodoRepository,
    private val todoReportRepository: TodoReportRepository,
    private val userTodoStatusRepository: UserTodoStatusRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun processReport(
        userId: Long,
        subjectId: Long,
        materialCode: Long,
        type: TodoType,
        dueDate: LocalDateTime,
        title: String,
    ): Todo {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { ResourceNotFoundException("User not found: $userId") }

        TodoReport
            .create(userId, subjectId, materialCode, dueDate, title)
            .let { todoReportRepository.save(it) }

        val todo =
            todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode)
                ?: todoRepository.save(Todo.create(subjectId, materialCode, type, dueDate, title))

        userTodoStatusRepository
            .findByUserIdAndTodo(userId, todo)
            ?.apply { recalculateNotifyAt(user.notificationThresholdMinutes) }
            ?: userTodoStatusRepository.save(
                UserTodoStatus.create(
                    userId = userId,
                    todo = todo,
                    notificationThresholdMinutes = user.notificationThresholdMinutes,
                ),
            )

        applicationEventPublisher.publishEvent(TodoReported(subjectId, materialCode, userId))
        return todo
    }

    @Transactional(readOnly = true)
    fun getUserTodoStatuses(userId: Long): List<UserTodoStatus> = userTodoStatusRepository.findAllByUserId(userId)

    fun updateTodoCompletion(
        userId: Long,
        subjectId: Long,
        materialCode: Long,
        type: TodoType,
        dueDate: LocalDateTime,
        title: String,
        isCompleted: Boolean,
    ) {
        val todo = processReport(userId, subjectId, materialCode, type, dueDate, title)
        val status =
            userTodoStatusRepository.findByUserIdAndTodo(userId, todo)
                ?: throw ResourceNotFoundException("Todo status not found: subjectId=$subjectId, materialCode=$materialCode")

        if (status.updateCompletion(isCompleted)) {
            userTodoStatusRepository.save(status)
        }
    }
}
