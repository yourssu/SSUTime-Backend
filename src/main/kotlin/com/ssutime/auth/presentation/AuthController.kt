package com.ssutime.auth.presentation

import com.ssutime.auth.application.AuthService
import com.ssutime.auth.infrastructure.SSOClient
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val ssoClient: SSOClient,
    private val authService: AuthService,
) {
    @PostMapping("/callback")
    fun callback(
        @RequestParam code: String,
    ): ResponseEntity<TokenResponse> {
        val studentId = ssoClient.getStudentId(code)
        return ResponseEntity.ok(authService.loginOrRegister(studentId))
    }

    @PostMapping("/devices")
    fun registerDevice(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: DeviceRegistrationRequest,
    ): ResponseEntity<Unit> {
        authService.registerDevice(userId, request.fcmToken)
        return ResponseEntity.ok().build()
    }
}
