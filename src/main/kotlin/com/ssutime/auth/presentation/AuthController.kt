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
        description = "8자리 학번과 비밀번호로 JWT를 발급합니다. 원문 인증정보는 저장하지 않고 단방향 해시와 마스킹 학번만 저장합니다.",
    )
    fun issueToken(
        @RequestBody request: CredentialLoginRequest,
    ): ResponseEntity<TokenResponse> = ResponseEntity.ok(authService.loginWithCredentials(request.id, request.password))

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

    @PostMapping("/withdrawal-requests")
    @Operation(
        summary = "계정 탈퇴 요청",
        description = "인증된 계정의 탈퇴 요청을 접수합니다. 이미 접수된 계정은 기존 요청을 반환하여 중복 요청을 만들지 않습니다.",
    )
    fun requestAccountWithdrawal(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody(required = false) request: AccountWithdrawalRequestRequest?,
    ): ResponseEntity<AccountWithdrawalRequestResponse> =
        ResponseEntity.ok(
            authService.requestAccountWithdrawal(
                userId = userId,
                reason = request?.reason,
            ),
        )

    @GetMapping("/notification-settings")
    @Operation(
        summary = "알림 설정 조회",
        description = "시스템 알림 설정을 조회합니다. 정시 알림은 한국 시간 09시와 18시, 게시판 신설 알림은 즉시 발송합니다.",
    )
    fun getNotificationSettings(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<NotificationSettingsResponse> = ResponseEntity.ok(authService.getNotificationSettings(userId))

    @PutMapping("/notification-settings")
    @Operation(
        summary = "알림 설정 변경",
        description = "시스템 알림 사용 여부를 변경합니다. notificationThresholdMinutes는 이전 앱 호환용이며 발송 시각에는 적용되지 않습니다.",
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
            ),
        )
}
