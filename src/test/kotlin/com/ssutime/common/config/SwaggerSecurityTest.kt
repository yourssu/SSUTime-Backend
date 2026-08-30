package com.ssutime.common.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerSecurityTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `swagger ui is accessible without authentication`() {
        mockMvc
            .get("/swagger-ui/index.html")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `openapi docs are accessible without authentication`() {
        mockMvc
            .get("/v3/api-docs")
            .andExpect {
                status { isOk() }
                jsonPath("$.info.title") { value("SSUTime API") }
                jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
                jsonPath("$.paths['/notifications/test-fcm'].post.summary") { value("FCM 메시지 dry-run 테스트") }
                jsonPath("$.paths['/notifications/test-fcm'].post.description") {
                    value(org.hamcrest.Matchers.containsString("실제 마감 알림은 title"))
                }
            }
    }

    @Test
    fun `token endpoint is accessible without authentication`() {
        mockMvc
            .post("/auth/tokens") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = """{"id":"20210001","password":"password-from-client"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.accessToken") { exists() }
            }
    }
}
