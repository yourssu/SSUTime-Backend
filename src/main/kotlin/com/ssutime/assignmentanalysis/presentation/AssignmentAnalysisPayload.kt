package com.ssutime.assignmentanalysis.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Canvas 과제 본문과 첨부파일 분석 요청 정보입니다.")
data class AssignmentAnalysisPayload(
    @field:Schema(description = "Canvas course ID", example = "44383")
    val courseId: Long,
    @field:Schema(description = "Canvas assignment ID", example = "718158")
    val assignmentId: Long,
    @field:Schema(description = "Canvas 과제 HTML 설명", example = "<p>과제 설명</p>")
    val assignmentHtml: String,
    val lmsSession: LmsSessionRequest? = null,
)
