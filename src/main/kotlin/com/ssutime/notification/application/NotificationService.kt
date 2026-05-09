package com.ssutime.notification.application

import com.ssutime.notification.domain.event.DeadlineApproaching
import com.ssutime.notification.infrastructure.FcmClient
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class NotificationService(
    private val fcmClient: FcmClient,
    private val userTodoStatusRepository: UserTodoStatusRepository,
) {
    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDeadlineApproaching(event: DeadlineApproaching) {
        fcmClient.sendPush(
            fcmToken = event.fcmToken,
            title = "마감 임박",
            body = "마감 ${event.dueDate}이 임박했습니다.",
        )
        markNotificationSent(event.userTodoStatusId)
    }

    @Transactional
    fun markNotificationSent(userTodoStatusId: Long) {
        userTodoStatusRepository.findById(userTodoStatusId).ifPresent { status ->
            status.markNotificationSent()
            userTodoStatusRepository.save(status)
        }
    }
}
