package com.ssutime.assignmentanalysis.presentation

import com.ssutime.assignmentanalysis.application.AssignmentAnalysisQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/assignment-analysis")
@Tag(name = "Assignment Analysis", description = "과제 첨부 AI 분석 상태 조회 API")
class AssignmentAnalysisController(
    private val assignmentAnalysisQueryService: AssignmentAnalysisQueryService,
) {
    @GetMapping("/{analysisId}")
    @Operation(
        summary = "과제 첨부 AI 분석 상태 조회",
        description = "report-with-analysis 응답의 analysisId로 비동기 AI 분석 상태만 조회합니다. SUCCEEDED이면 /todo/todos에서 aiSummary를 확인할 수 있습니다.",
    )
    fun getAnalysisStatus(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @PathVariable analysisId: Long,
    ): ResponseEntity<AssignmentAnalysisResponse> =
        ResponseEntity.ok(
            assignmentAnalysisQueryService.getAnalysisStatus(
                userId = userId,
                analysisId = analysisId,
            ),
        )
}
