package com.ssutime.aisummary

import com.ninjasquad.springmockk.MockkBean
import com.ssutime.aisummary.infrastructure.OpenAIClient
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.event.TodoConfirmed
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.Executor

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
@Import(AISummaryRoutingTest.SynchronousAsyncConfig::class)
class AISummaryRoutingTest {
    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @MockkBean(relaxed = true)
    private lateinit var openAIClient: OpenAIClient

    @Test
    fun `TodoConfirmed does not trigger title based AI summary`() {
        val event =
            TodoConfirmed(
                todoId = 1L,
                type = TodoType.ASSIGNMENT,
                title = "첨부 없는 과제",
                dueDate = LocalDateTime.of(2026, 8, 31, 23, 59),
            )

        TransactionTemplate(transactionManager).executeWithoutResult {
            eventPublisher.publishEvent(event)
        }

        verify(exactly = 0) { openAIClient.analyzeAssignment(any()) }
    }

    class SynchronousAsyncConfig {
        @Bean(name = ["taskExecutor"])
        @Primary
        fun taskExecutor(): Executor = SyncTaskExecutor()
    }
}
