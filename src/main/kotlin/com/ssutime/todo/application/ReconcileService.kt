package com.ssutime.todo.application

import com.ssutime.todo.domain.TodoStatus
import com.ssutime.todo.domain.event.TodoConfirmed
import com.ssutime.todo.domain.event.TodoReported
import com.ssutime.todo.infrastructure.TodoReportRepository
import com.ssutime.todo.infrastructure.TodoRepository
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime

private const val QUORUM_SIZE = 2
private const val RECONCILE_WINDOW_HOURS = 24L

@Service
class ReconcileService(
    private val todoRepository: TodoRepository,
    private val todoReportRepository: TodoReportRepository,
    private val userTodoStatusRepository: UserTodoStatusRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onTodoReported(event: TodoReported) {
        val todo = todoRepository.findBySubjectIdAndMaterialCode(event.subjectId, event.materialCode)
            ?: return

        val since = LocalDateTime.now().minusHours(RECONCILE_WINDOW_HOURS)
        val reports = todoReportRepository.findBySubjectIdAndMaterialCodeAndReportedAtAfter(
            event.subjectId, event.materialCode, since
        )

        if (reports.size < QUORUM_SIZE) return

        val majorityDueDate = reports.groupingBy { it.dueDate }.eachCount()
            .maxByOrNull { it.value }?.key ?: return
        val majorityTitle = reports.groupingBy { it.title }.eachCount()
            .maxByOrNull { it.value }?.key ?: return

        val dueDateChanged = todo.dueDate != majorityDueDate
        val wasProvisional = todo.status == TodoStatus.PROVISIONAL

        if (dueDateChanged) {
            todo.updateDueDate(majorityDueDate)
        }
        todo.title = majorityTitle

        if (dueDateChanged) {
            userTodoStatusRepository.findAllByTodo(todo).forEach { status ->
                status.recalculateNotifyAt()
                userTodoStatusRepository.save(status)
            }
        }

        if (wasProvisional) {
            todo.confirm()
            todoRepository.save(todo)
            applicationEventPublisher.publishEvent(
                TodoConfirmed(
                    todoId = todo.id,
                    type = todo.type,
                    title = todo.title,
                    dueDate = todo.dueDate,
                )
            )
        } else {
            todoRepository.save(todo)
        }
    }
}
