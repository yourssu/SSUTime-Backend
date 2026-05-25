package com.ssutime.assignmentanalysis.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "LMS-API 로그인 이후 클라이언트가 보유한 Canvas/LMS 세션 쿠키입니다. 서버는 이 값을 저장하지 않습니다.")
data class LmsSessionRequest(
    @field:Schema(description = "Canvas/LMS 세션 쿠키 목록")
    val cookies: List<LmsSessionCookieRequest> = emptyList(),
)

data class LmsSessionCookieRequest(
    val name: String,
    val value: String,
    val domain: String = "canvas.ssu.ac.kr",
    val path: String = "/",
)
