package com.ssutime.auth.presentation

import com.ssutime.auth.application.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "JWT 발급 및 디바이스 등록 API")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/tokens")
    @Operation(
        summary = "학번/비밀번호로 JWT 발급",
        description = "8자리 학번과 비밀번호를 받아 JWT를 발급합니다. 서버는 원문 학번과 비밀번호를 저장하지 않고, 두 값을 조합한 단방향 해시만 사용자 인증키로 저장합니다. 표시용 학번은 가운데 4자리를 마스킹한 값만 저장합니다.",
    )
    fun issueToken(
        @RequestBody request: CredentialLoginRequest,
    ): ResponseEntity<TokenResponse> =
        ResponseEntity.ok(authService.loginWithCredentials(request.id, request.password))

    @PostMapping("/devices")
    @Operation(
        summary = "FCM 토큰 등록",
        description = "인증된 사용자에게 현재 디바이스의 FCM registration token을 등록합니다. 이 토큰은 마감 알림과 LMS 크롤링 silent push 발송에 사용됩니다.",
    )
    fun registerDevice(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: DeviceRegistrationRequest,
    ): ResponseEntity<Unit> {
        authService.registerDevice(userId, request.fcmToken)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/notification-settings")
    @Operation(
        summary = "알림 설정 조회",
        description = "인증된 계정의 마감 알림 설정을 조회합니다. notificationEnabled가 false이면 마감 알림을 발송하지 않습니다. notificationThresholdMinutes는 모든 할 일의 알림 예정 시각 계산에 공통으로 사용됩니다.",
    )
    fun getNotificationSettings(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<NotificationSettingsResponse> =
        ResponseEntity.ok(authService.getNotificationSettings(userId))

    @PutMapping("/notification-settings")
    @Operation(
        summary = "알림 설정 변경",
        description = "인증된 계정의 마감 알림 사용 여부와 알림 시간을 변경합니다. 이후 생성되거나 갱신되는 사용자 할 일의 notifyAt은 dueDate에서 notificationThresholdMinutes를 뺀 시각으로 계산됩니다.",
    )
    fun updateNotificationSettings(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: NotificationSettingsRequest,
    ): ResponseEntity<NotificationSettingsResponse> =
        ResponseEntity.ok(
            authService.updateNotificationSettings(
                userId = userId,
                notificationEnabled = request.notificationEnabled,
                notificationThresholdMinutes = request.notificationThresholdMinutes,
            )
        )
}
