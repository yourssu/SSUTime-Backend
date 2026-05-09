package com.ssutime.auth.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class SSOClient(
    @Value("\${sso.client-id}") private val clientId: String,
    @Value("\${sso.client-secret}") private val clientSecret: String,
    @Value("\${sso.redirect-uri}") private val redirectUri: String,
) {
    private val webClient = WebClient.builder()
        .baseUrl("https://sso.soongsil.ac.kr")
        .build()

    fun getStudentId(code: String): String {
        val tokenResponse = webClient.post()
            .uri("/auth/realms/soongsil/protocol/openid-connect/token")
            .bodyValue(
                mapOf(
                    "grant_type" to "authorization_code",
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "redirect_uri" to redirectUri,
                    "code" to code,
                ),
            )
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: error("SSO token request failed")

        val accessToken = tokenResponse["access_token"] as? String
            ?: error("No access_token in SSO response")

        val userInfo = webClient.get()
            .uri("/auth/realms/soongsil/protocol/openid-connect/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: error("SSO userinfo request failed")

        return userInfo["student_id"] as? String
            ?: userInfo["preferred_username"] as? String
            ?: error("No student_id in SSO userinfo")
    }
}
