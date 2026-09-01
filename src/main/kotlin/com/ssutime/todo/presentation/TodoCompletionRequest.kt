package com.ssutime.todo.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용자 할 일의 완료 상태 변경 요청입니다.")
data class TodoCompletionRequest(
    @field:Schema(description = "완료 처리하려면 true, 미완료로 되돌리려면 false입니다.", example = "true")
    val isCompleted: Boolean,
)
