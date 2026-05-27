package com.ssutime.assignmentanalysis

import com.fasterxml.jackson.databind.ObjectMapper
import com.ssutime.assignmentanalysis.application.CanvasFileMetadata
import com.ssutime.assignmentanalysis.infrastructure.SsuLmsCanvasClient
import com.ssutime.assignmentanalysis.presentation.LmsSessionCookieRequest
import com.ssutime.assignmentanalysis.presentation.LmsSessionRequest
import com.ssutime.common.exception.InvalidRequestException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SsuLmsCanvasClientSecurityTest {
    private val client = SsuLmsCanvasClient(ObjectMapper())

    @Test
    fun `downloadFile rejects non Canvas download URLs before fetching`() {
        val session = client.createSession(validCookieSession())
        val metadata =
            CanvasFileMetadata(
                fileId = 1L,
                displayName = "guide.txt",
                contentType = "text/plain",
                size = 1L,
                downloadUrl = "https://evil.example.com/files/1/download",
            )

        assertFailsWith<InvalidRequestException> {
            client.downloadFile(session, metadata)
        }
    }

    @Test
    fun `createSession accepts LMS session cookies without bearer token`() {
        client.createSession(validCookieSession())
    }

    @Test
    fun `createSession accepts root SSU cookie domain with leading dot`() {
        client.createSession(
            LmsSessionRequest(
                cookies =
                    listOf(
                        LmsSessionCookieRequest(
                            name = "_normandy_session",
                            value = "session",
                            domain = ".ssu.ac.kr",
                        ),
                    ),
            ),
        )
    }

    @Test
    fun `createSession accepts Smart ID cookie domain`() {
        client.createSession(
            LmsSessionRequest(
                cookies =
                    listOf(
                        LmsSessionCookieRequest(
                            name = "sToken",
                            value = "token",
                            domain = "smartid.ssu.ac.kr",
                        ),
                    ),
            ),
        )
    }

    @Test
    fun `createSession rejects missing LMS session cookies`() {
        assertFailsWith<InvalidRequestException> {
            client.createSession(LmsSessionRequest())
        }
    }

    @Test
    fun `createSession rejects unsupported cookie domains`() {
        assertFailsWith<InvalidRequestException> {
            client.createSession(
                LmsSessionRequest(
                    cookies =
                        listOf(
                            LmsSessionCookieRequest(
                                name = "xn_api_token",
                                value = "token",
                                domain = "evil.example.com",
                            ),
                        ),
                ),
            )
        }
    }

    private fun validCookieSession(): LmsSessionRequest =
        LmsSessionRequest(
            cookies =
                listOf(
                    LmsSessionCookieRequest(
                        name = "_normandy_session",
                        value = "session",
                        domain = "canvas.ssu.ac.kr",
                    ),
                ),
        )
}
