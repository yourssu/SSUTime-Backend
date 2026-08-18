package com.ssutime.aisummary.application

import com.ssutime.aisummary.infrastructure.OpenAIClient
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.event.TodoConfirmed
import com.ssutime.todo.infrastructure.TodoRepository
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class AISummaryService(
    private val openAIClient: OpenAIClient,
    private val todoRepository: TodoRepository,
) {
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(maxAttempts = 3, backoff = Backoff(delay = 1000))
    fun onTodoConfirmed(event: TodoConfirmed) {
        if (event.type != TodoType.ASSIGNMENT) return
        val summary = openAIClient.summarizeAssignment(event.title)
        if (summary.isBlank()) return
        todoRepository.findById(event.todoId).ifPresent { todo ->
            todo.updateAiSummary(summary)
            todoRepository.save(todo)
        }
    }
}
