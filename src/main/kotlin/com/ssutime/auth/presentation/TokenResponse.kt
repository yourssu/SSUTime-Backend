package com.ssutime.auth.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "JWT 토큰 응답입니다.")
data class TokenResponse(
    @field:Schema(
        description = "JWT access token입니다. Swagger의 Authorize 또는 Authorization 헤더에 Bearer 토큰으로 사용하세요.",
        example = "eyJhbGciOiJIUzI1NiJ9...",
    )
    val accessToken: String,
)
