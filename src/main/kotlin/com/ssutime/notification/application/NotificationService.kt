package com.ssutime.notification.application

import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.common.exception.InvalidRequestException
import com.ssutime.common.exception.ResourceNotFoundException
import com.ssutime.notification.domain.BoardReport
import com.ssutime.notification.domain.event.DeadlineApproaching
import com.ssutime.notification.domain.event.NewBoardDetected
import com.ssutime.notification.infrastructure.BoardRepository
import com.ssutime.notification.infrastructure.FcmClient
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class NotificationService(
    private val fcmClient: FcmClient,
    private val userTodoStatusRepository: UserTodoStatusRepository,
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val boardRepository: BoardRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun reportBoards(
        userId: Long,
        request: BoardReport,
    ) {
        request.validate()
        val user = userRepository.findById(userId).orElseThrow { ResourceNotFoundException("사용자를 찾을 수 없습니다") }
        if (userDeviceRepository.findByUserAndFcmToken(user, request.fcmToken) == null) {
            throw InvalidRequestException("현재 사용자의 기기 토큰을 /auth/devices에 먼저 등록해주세요")
        }
        val boards = request.boards.distinctBy { it.id }
        if (boards.isEmpty()) return
        boards.forEach { board ->
            if (boardRepository.insertIfAbsent(board.id) == 1 && board.isWritableByStudent() && user.notificationEnabled) {
                eventPublisher.publishEvent(NewBoardDetected(request.fcmToken, board.title, user.id))
            }
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNewBoardDetected(event: NewBoardDetected) {
        val user = userRepository.findById(event.userId).orElse(null) ?: return
        if (!user.notificationEnabled) return
        if (userDeviceRepository.findByUserAndFcmToken(user, event.fcmToken) == null) return
        fcmClient.sendSilentPush(
            fcmToken = event.fcmToken,
            data = mapOf("title" to event.title, "type" to "newBoard"),
        )
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDeadlineApproaching(event: DeadlineApproaching) {
        fcmClient.sendSilentPush(
            fcmToken = event.fcmToken,
            data = mapOf("action" to "deadline_approaching", "todo_id" to event.todoId.toString()),
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
