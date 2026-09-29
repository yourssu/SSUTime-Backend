package com.ssutime.notification.application

import com.google.firebase.messaging.FirebaseMessagingException
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.common.exception.InvalidRequestException
import com.ssutime.common.exception.ResourceNotFoundException
import com.ssutime.notification.domain.BoardReport
import com.ssutime.notification.domain.event.NewBoardDetected
import com.ssutime.notification.infrastructure.BoardRepository
import com.ssutime.notification.infrastructure.FcmClient
import com.ssutime.notification.infrastructure.NotificationDeliveryRepository
import com.ssutime.subject.infrastructure.SubjectRepository
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class NotificationService(
    private val fcmClient: FcmClient,
    private val userTodoStatusRepository: UserTodoStatusRepository,
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val boardRepository: BoardRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val subjectRepository: SubjectRepository,
    private val notificationDeliveryRepository: NotificationDeliveryRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
        sendSilentPush(
            event.fcmToken,
            mapOf("type" to "newBoard", "title" to event.title),
        )
    }

    @Async("taskExecutor")
    fun sendDeadlineApproachingNotifications(now: LocalDateTime) {
        val pending = userTodoStatusRepository.findThresholdNotifications(now)
        if (pending.isEmpty()) return
        val subjectNames = subjectRepository.findAllById(pending.map { it.todo.subjectId }.distinct()).associate { it.id to it.name }
        pending.groupBy { it.userId }.forEach { (userId, items) ->
            val user = userRepository.findById(userId).orElse(null) ?: return@forEach
            if (!user.notificationEnabled) return@forEach
            val devices = userDeviceRepository.findAllByUser(user)
            items.forEach { status ->
                val todo = status.todo
                val data =
                    buildMap {
                        put("type", DEADLINE_APPROACHING)
                        put("count", "1")
                        put("representative_todo_id", todo.id.toString())
                        put("todo_title", todo.title)
                        put("todo_type", todo.type.toTodoType().name)
                        put("due_date", todo.dueDate.toString())
                        subjectNames[todo.subjectId]?.takeIf { it.isNotBlank() }?.let { put("subject_name", it) }
                        put("todo_id", todo.id.toString())
                        // Existing clients dispatch deadline messages using action + todo_id.
                        put("action", "deadline_approaching")
                    }
                // The slot date follows the deadline so a retry after midnight reuses the same delivery slot.
                val sentToAllDevices =
                    devices
                        .map { device ->
                            sendOnce(
                                userDeviceId = device.id,
                                fcmToken = device.fcmToken,
                                scheduledDate = todo.dueDate.toLocalDate(),
                                groupKey = "todo:${todo.id}",
                                data = data,
                            )
                        }.all { it }
                // Items stay unsent while any device fails so the next run retries them.
                if (sentToAllDevices) userTodoStatusRepository.markNotificationSent(status.id)
            }
        }
    }

    private fun sendOnce(
        userDeviceId: Long,
        fcmToken: String,
        scheduledDate: LocalDate,
        groupKey: String,
        data: Map<String, String>,
    ): Boolean {
        val now = LocalDateTime.now()
        notificationDeliveryRepository.insertIfAbsent(userDeviceId, DEADLINE_APPROACHING, scheduledDate, groupKey, now)
        val claimToken = UUID.randomUUID().toString()
        val claimed =
            notificationDeliveryRepository.claim(
                userDeviceId = userDeviceId,
                notificationType = DEADLINE_APPROACHING,
                scheduledDate = scheduledDate,
                groupKey = groupKey,
                claimToken = claimToken,
                now = now,
                expiredBefore = now.minusMinutes(CLAIM_LEASE_MINUTES),
            )
        // Another run holds the slot; it only counts once that run has actually sent it.
        if (claimed == 0) {
            return notificationDeliveryRepository.existsByUserDeviceIdAndNotificationTypeAndScheduledDateAndGroupKeyAndStatus(
                userDeviceId,
                DEADLINE_APPROACHING,
                scheduledDate,
                groupKey,
                "SENT",
            )
        }

        if (sendSilentPush(fcmToken, data)) {
            notificationDeliveryRepository.markSent(claimToken, LocalDateTime.now())
            return true
        }
        notificationDeliveryRepository.release(claimToken, LocalDateTime.now())
        return false
    }

    private fun sendSilentPush(
        fcmToken: String,
        data: Map<String, String>,
    ): Boolean =
        try {
            fcmClient.sendSilentPush(fcmToken, data)
            true
        } catch (exception: FirebaseMessagingException) {
            // A failed device must not prevent delivery to the remaining recipients.
            log.warn("Notification could not be sent ({})", exception.javaClass.simpleName, exception)
            false
        }

    companion object {
        private const val CLAIM_LEASE_MINUTES = 30L
        private const val DEADLINE_APPROACHING = "deadlineApproaching"
    }
}
