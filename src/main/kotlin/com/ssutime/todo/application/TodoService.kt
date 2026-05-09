package com.ssutime.todo.application

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
    private val todoRepository: TodoRepository,
    private val todoReportRepository: TodoReportRepository,
    private val userTodoStatusRepository: UserTodoStatusRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun processReport(
        userId: Long,
        subjectId: Long,
        materialCode: String,
        type: TodoType,
        dueDate: LocalDateTime,
        title: String,
        thresholdMinutes: Int = 60,
    ) {
        TodoReport.create(userId, subjectId, materialCode, dueDate, title)
            .let { todoReportRepository.save(it) }

        val todo = todoRepository.findBySubjectIdAndMaterialCode(subjectId, materialCode)
            ?: todoRepository.save(Todo.create(subjectId, materialCode, type, dueDate, title))

        userTodoStatusRepository.findByUserIdAndTodo(userId, todo)
            ?.apply { recalculateNotifyAt() }
            ?: userTodoStatusRepository.save(UserTodoStatus.create(userId, todo, thresholdMinutes))

        applicationEventPublisher.publishEvent(TodoReported(subjectId, materialCode, userId))
    }

    @Transactional(readOnly = true)
    fun getUserTodoStatuses(userId: Long): List<UserTodoStatus> =
        userTodoStatusRepository.findAll().filter { it.userId == userId }
}
