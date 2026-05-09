package com.ssutime.todo.presentation

import com.ssutime.todo.domain.TodoType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "클라이언트가 LMS 콘텐츠를 읽은 뒤 서버에 제보하는 할 일 정보입니다.")
data class TodoReportRequest(
    @field:Schema(description = "LMS 자료가 속한 과목 ID입니다.", example = "10001")
    val subjectId: Long,

    @field:Schema(description = "LMS 자료의 고정 식별자입니다. subjectId와 함께 할 일 중복 제거에 사용됩니다.", example = "20260509001")
    val materialCode: Long,

    @field:Schema(description = "LMS 콘텐츠에서 추론한 할 일 유형입니다.", example = "ASSIGNMENT")
    val type: TodoType,

    @field:Schema(description = "할 일 마감 시각입니다. 서버 로컬 기준 ISO-8601 datetime 형식을 사용합니다.", example = "2026-05-10T23:59:00")
    val dueDate: LocalDateTime,

    @field:Schema(description = "사용자에게 표시하고 알림 문구에도 사용할 할 일 제목입니다.", example = "3장 과제")
    val title: String,
)
