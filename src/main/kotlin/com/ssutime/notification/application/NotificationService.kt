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
import com.ssutime.todo.domain.UserTodoStatus
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
import java.time.ZoneId
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
    fun sendMorningNotifications(today: LocalDate) {
        val cutoff = today.atTime(9, 0).atZone(ZoneId.of("Asia/Seoul"))
        sendTodoNotifications(
            userTodoStatusRepository.findDeadlineNotifications(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                cutoff.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
            ),
            NotificationType.DUE_TODAY,
            today,
        )
    }

    @Async("taskExecutor")
    fun sendEveningNotifications(today: LocalDate) {
        val cutoff = today.atTime(18, 0).atZone(ZoneId.of("Asia/Seoul"))
        sendTodoNotifications(
            userTodoStatusRepository.findDeadlineNotifications(
                today.plusDays(1).atStartOfDay(),
                today.plusDays(4).atStartOfDay(),
                cutoff.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
            ),
            NotificationType.DEADLINE_APPROACHING,
            today,
        )
        // createdAt is audited in the JVM time zone; deadlines use Seoul local time.
        sendTodoNotifications(
            userTodoStatusRepository.findNewNotifications(
                cutoff.minusDays(1).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
                cutoff.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
            ),
            NotificationType.NEW_TODO,
            today,
        )
    }

    private fun sendTodoNotifications(
        pending: List<UserTodoStatus>,
        type: NotificationType,
        scheduledDate: LocalDate,
    ) {
        if (pending.isEmpty()) return
        val subjectNames = subjectRepository.findAllById(pending.map { it.todo.subjectId }.distinct()).associate { it.id to it.name }
        pending.groupBy { it.userId }.forEach { (userId, items) ->
            val user = userRepository.findById(userId).orElse(null) ?: return@forEach
            if (!user.notificationEnabled) return@forEach
            val devices = userDeviceRepository.findAllByUser(user)
            val groups = if (type == NotificationType.DUE_TODAY) items.map { listOf(it) } else listOf(items)
            groups.forEach { group ->
                val first = group.minWith(compareBy({ it.todo.dueDate }, { it.todo.id })).todo
                val single = group.size == 1
                val groupKey = if (type == NotificationType.DUE_TODAY) "todo:${first.id}" else "group"
                val data =
                    buildMap {
                        put("type", type.wireName)
                        put("count", group.size.toString())
                        put("representative_todo_id", first.id.toString())
                        put("todo_title", first.title)
                        put("todo_type", first.type.toTodoType().name)
                        put("due_date", first.dueDate.toString())
                        subjectNames[first.subjectId]?.takeIf { it.isNotBlank() }?.let { put("subject_name", it) }
                        if (single) {
                            put("todo_id", first.id.toString())
                            // Existing clients dispatch single deadline messages using action + todo_id.
                            if (type != NotificationType.NEW_TODO) put("action", "deadline_approaching")
                        }
                    }
                devices.forEach { device ->
                    sendOnce(
                        userDeviceId = device.id,
                        fcmToken = device.fcmToken,
                        type = type,
                        scheduledDate = scheduledDate,
                        groupKey = groupKey,
                        data = data,
                    )
                }
            }
        }
    }

    private fun sendOnce(
        userDeviceId: Long,
        fcmToken: String,
        type: NotificationType,
        scheduledDate: LocalDate,
        groupKey: String,
        data: Map<String, String>,
    ) {
        val now = LocalDateTime.now()
        notificationDeliveryRepository.insertIfAbsent(userDeviceId, type.wireName, scheduledDate, groupKey, now)
        val claimToken = UUID.randomUUID().toString()
        val claimed =
            notificationDeliveryRepository.claim(
                userDeviceId = userDeviceId,
                notificationType = type.wireName,
                scheduledDate = scheduledDate,
                groupKey = groupKey,
                claimToken = claimToken,
                now = now,
                expiredBefore = now.minusMinutes(CLAIM_LEASE_MINUTES),
            )
        if (claimed == 0) return

        if (sendSilentPush(fcmToken, data)) {
            notificationDeliveryRepository.markSent(claimToken, LocalDateTime.now())
        } else {
            notificationDeliveryRepository.release(claimToken, LocalDateTime.now())
        }
    }

    private enum class NotificationType(
        val wireName: String,
    ) {
        DUE_TODAY("dueToday"),
        DEADLINE_APPROACHING("deadlineApproaching"),
        NEW_TODO("newTodo"),
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
    }
}
