package com.ssutime.aisummary.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class OpenAIClient(
    @Value("\${openai.api-key}") private val apiKey: String,
) {
    private val webClient = WebClient.builder()
        .baseUrl("https://api.openai.com")
        .defaultHeader("Authorization", "Bearer $apiKey")
        .defaultHeader("content-type", "application/json")
        .build()

    fun summarizeAssignment(title: String): String {
        if (apiKey.isBlank()) return ""

        val requestBody = mapOf(
            "model" to "gpt-5.6-luna",
            "input" to "다음 과제 내용을 읽고 핵심 내용을 한 문장으로 요약해줘: $title",
        )

        return webClient.post()
            .uri("/v1/responses")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response ->
                @Suppress("UNCHECKED_CAST")
                val output = response["output"] as? List<Map<String, Any>>
                val content = output
                    ?.firstOrNull { it["type"] == "message" }
                    ?.get("content") as? List<Map<String, Any>>
                content
                    ?.firstOrNull { it["type"] == "output_text" }
                    ?.get("text") as? String ?: ""
            }
            .block() ?: ""
    }
}