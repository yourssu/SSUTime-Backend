package com.ssutime.notification.presentation

import com.ssutime.notification.application.NotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/boards")
@Tag(name = "Boards", description = "사용자별 새 게시판 감지 API")
class BoardReportController(
    private val notificationService: NotificationService,
) {
    @PostMapping("/report")
    @Operation(
        summary = "게시판 목록 제보",
        description = "사용자별 최초 수신 ID를 기록하고 학생의 글 또는 답글 작성이 가능하면 요청 기기에 newBoard 알림을 1회 시도합니다.",
    )
    fun report(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: BoardReportRequest,
    ): ResponseEntity<Unit> {
        notificationService.reportBoards(userId, request.toDomain())
        return ResponseEntity.ok().build()
    }
}
