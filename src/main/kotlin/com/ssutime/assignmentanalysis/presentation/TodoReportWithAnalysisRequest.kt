package com.ssutime.assignmentanalysis.presentation

import com.ssutime.todo.domain.TodoType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "LMS 할 일 제보와 Canvas 과제 첨부 AI 분석을 함께 요청합니다.")
data class TodoReportWithAnalysisRequest(
    val subjectId: Long,
    val materialCode: Long,
    val type: TodoType,
    val dueDate: LocalDateTime,
    val title: String,
    val assignmentAnalysis: AssignmentAnalysisPayload,
)
