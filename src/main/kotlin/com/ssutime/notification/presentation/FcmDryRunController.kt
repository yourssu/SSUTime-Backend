package com.ssutime.notification.presentation

import com.ssutime.notification.infrastructure.FcmClient
import com.ssutime.notification.infrastructure.FcmDryRunResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notifications", description = "알림 및 Firebase Cloud Messaging 테스트 API")
class FcmDryRunController(
    private val fcmClient: FcmClient,
) {
    @PostMapping("/test-fcm")
    @Operation(
        summary = "FCM 메시지 dry-run 테스트",
        description = "실제 푸시를 전달하지 않고 Firebase Admin SDK 인증 정보와 메시지 형식만 검증합니다. 요청한 topic을 대상으로 title/body를 가진 dry-run 알림 메시지를 보냅니다. 실제 마감 알림은 title '마감 임박', body '마감 {dueDate}이 임박했습니다.' 형식으로 발송됩니다. LMS 크롤링 트리거용 silent push는 data-only payload { action: 'crawl_lms' } 형식으로 발송됩니다.",
    )
    fun testFcm(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: FcmDryRunRequest,
    ): ResponseEntity<FcmDryRunResult> =
        ResponseEntity.ok(
            fcmClient.sendDryRunToTopic(
                topic = request.topic,
                title = request.title,
                body = request.body,
            ),
        )
}

@Schema(description = "FCM dry-run 요청입니다. 백엔드 Firebase 설정을 검증하지만 실제 디바이스로 푸시를 전달하지 않습니다.")
data class FcmDryRunRequest(
    @field:Schema(description = "dry-run 대상 형식을 검증하기 위해 사용하는 FCM topic입니다.", example = "ssutime-dry-run")
    val topic: String = "ssutime-dry-run",

    @field:Schema(description = "dry-run 메시지에서 검증할 알림 제목입니다.", example = "FCM dry run")
    val title: String = "FCM dry run",

    @field:Schema(description = "dry-run 메시지에서 검증할 알림 본문입니다.", example = "SSUTime FCM configuration test")
    val body: String = "SSUTime FCM configuration test",
)
