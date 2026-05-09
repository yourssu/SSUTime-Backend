package com.ssutime.subject.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "등록된 수강 과목 정보입니다.")
data class EnrollmentResponse(
    @field:Schema(description = "수강 과목 등록 ID입니다.", example = "1")
    val enrollmentId: Long,

    @field:Schema(description = "LMS 또는 클라이언트 과목 목록에서 사용하는 과목 ID입니다.", example = "10001")
    val courseId: Long,

    @field:Schema(description = "사용자에게 표시할 과목명입니다.", example = "운영체제")
    val name: String,

    @field:Schema(description = "클라이언트에서 사용하는 학기 표기입니다.", example = "2026-1")
    val semester: String,
)
