package com.ssutime.aisummary.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class AnthropicClient(
    @Value("\${anthropic.api-key}") private val apiKey: String,
) {
    private val webClient = WebClient.builder()
        .baseUrl("https://api.anthropic.com")
        .defaultHeader("x-api-key", apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .defaultHeader("content-type", "application/json")
        .build()

    fun summarizeAssignment(title: String): String {
        if (apiKey.isBlank()) return ""
        val requestBody = mapOf(
            "model" to "claude-haiku-4-5-20251001",
            "max_tokens" to 200,
            "messages" to listOf(mapOf("role" to "user", "content" to "다음 과제를 한 문장으로 요약해줘: $title")),
        )
        return webClient.post()
            .uri("/v1/messages")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response ->
                @Suppress("UNCHECKED_CAST")
                val content = response["content"] as? List<Map<String, Any>>
                content?.firstOrNull()?.get("text") as? String ?: ""
            }
            .block() ?: ""
    }
}
