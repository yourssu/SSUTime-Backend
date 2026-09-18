package com.ssutime.notification.domain.event

class NewBoardDetected(
    val fcmToken: String,
    val title: String,
    val userId: Long,
)
