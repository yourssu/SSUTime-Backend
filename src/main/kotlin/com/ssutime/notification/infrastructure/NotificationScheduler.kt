package com.ssutime.notification.infrastructure

import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.notification.domain.event.DeadlineApproaching
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class NotificationScheduler(
    private val userTodoStatusRepository: UserTodoStatusRepository,
    private val userRepository: UserRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Scheduled(fixedRate = 60_000)
    @Transactional(readOnly = true)
    fun checkDeadlines() {
        val pending = userTodoStatusRepository.findPendingNotifications(LocalDateTime.now())
        pending.forEach { status ->
            val user = userRepository.findById(status.userId).orElse(null) ?: return@forEach
            if (!user.notificationEnabled) return@forEach
            userDeviceRepository.findAllByUser(user).forEach { device ->
                applicationEventPublisher.publishEvent(
                    DeadlineApproaching(
                        userTodoStatusId = status.id,
                        userId = status.userId,
                        todoId = status.todo.id,
                        fcmToken = device.fcmToken,
                        dueDate = status.todo.dueDate,
                    ),
                )
            }
        }
    }
}
