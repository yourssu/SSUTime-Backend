package com.ssutime.todo.presentation

import com.ssutime.assignmentanalysis.application.AssignmentAnalysisService
import com.ssutime.assignmentanalysis.presentation.AssignmentAnalysisResponse
import com.ssutime.assignmentanalysis.presentation.TodoReportWithAnalysisRequest
import com.ssutime.todo.application.TodoService
import com.ssutime.todo.domain.UserTodoStatus
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
@RequestMapping("/todo")
@Tag(name = "Todos", description = "할 일 제보 및 인증된 사용자의 할 일 상태 API")
class TodoController(
    private val todoService: TodoService,
    private val assignmentAnalysisService: AssignmentAnalysisService,
) {
    @PostMapping("/report")
    @Operation(
        summary = "LMS 할 일 제보",
        description = "LMS 콘텐츠에서 발견한 할 일을 생성하거나 갱신하고 인증된 사용자와 연결합니다. 알림 예정 시각은 dueDate에서 계정의 notificationThresholdMinutes를 뺀 값으로 계산됩니다.",
    )
    fun report(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: TodoReportRequest,
    ): ResponseEntity<Unit> {
        todoService.processReport(
            userId = userId,
            subjectId = request.subjectId,
            materialCode = request.materialCode,
            type = request.type,
            dueDate = request.dueDate,
            title = request.title,
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/report-with-analysis")
    @Operation(
        summary = "LMS 할 일 제보 및 과제 첨부 AI 분석 요청",
        description = "기존 할 일 제보를 처리한 뒤 요청 범위 LMS 인증정보로 Canvas 첨부를 다운로드/추출하고 비동기 AI 분석을 예약합니다.",
    )
    fun reportWithAnalysis(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: TodoReportWithAnalysisRequest,
    ): ResponseEntity<AssignmentAnalysisResponse> {
        val todo =
            todoService.processReport(
                userId = userId,
                subjectId = request.subjectId,
                materialCode = request.materialCode,
                type = request.type,
                dueDate = request.dueDate,
                title = request.title,
            )
        return ResponseEntity.ok(
            assignmentAnalysisService.prepareAnalysis(
                todo = todo,
                payload = request.assignmentAnalysis,
            ),
        )
    }

    @GetMapping("/todos")
    @Operation(
        summary = "사용자 할 일 목록 조회",
        description = "인증된 사용자의 할 일 상태를 조회합니다. 완료 여부와 알림 예정 시각 관련 필드가 포함됩니다.",
    )
    fun getTodos(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<List<UserTodoStatus>> = ResponseEntity.ok(todoService.getUserTodoStatuses(userId))

    @PutMapping("/report")
    @Operation(
        summary = "사용자 할 일 완료 여부 갱신",
        description = "LMS 할 일 제보와 같은 URL과 요청 본문에 완료 여부만 추가해 인증된 사용자의 할 일 완료 상태를 갱신합니다.",
    )
    fun updateTodoCompletion(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: TodoCompletionRequest,
    ): ResponseEntity<Unit> {
        todoService.updateTodoCompletion(
            userId = userId,
            subjectId = request.subjectId,
            materialCode = request.materialCode,
            isCompleted = request.isCompleted,
        )
        return ResponseEntity.ok().build()
    }
}
