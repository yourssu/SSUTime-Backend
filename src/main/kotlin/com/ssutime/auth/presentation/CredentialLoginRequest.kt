package com.ssutime.auth.presentation

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "학번/비밀번호 기반 토큰 발급 요청입니다. 서버는 원문 학번과 비밀번호를 저장하지 않고 두 값을 조합한 단방향 해시만 사용자 인증키로 저장합니다. 표시용 학번은 가운데 4자리를 마스킹해서 저장합니다.")
data class CredentialLoginRequest(
    @field:Schema(
        description = "사용자가 입력한 8자리 학번입니다. 서버에는 원문 학번이 저장되지 않고, 표시용 값은 예를 들어 20210001 -> 20****01 형태로 마스킹 저장됩니다.",
        example = "20210001",
        minLength = 8,
        maxLength = 8,
    )
    val id: String,

    @field:Schema(
        description = "사용자가 입력한 비밀번호입니다. 서버에는 원문 값이 저장되지 않습니다.",
        example = "password-from-client",
    )
    val password: String,
)
