package com.ssutime.assignmentanalysis

import com.ssutime.assignmentanalysis.application.AssignmentAnalysisLimits
import com.ssutime.assignmentanalysis.application.AssignmentContentExtractor
import com.ssutime.assignmentanalysis.application.AssignmentHtmlParser
import com.ssutime.assignmentanalysis.application.AttachmentTextExtractor
import com.ssutime.assignmentanalysis.application.CanvasFileMetadata
import com.ssutime.assignmentanalysis.application.CanvasSession
import com.ssutime.assignmentanalysis.application.LmsCanvasClient
import com.ssutime.assignmentanalysis.presentation.AssignmentAnalysisPayload
import com.ssutime.assignmentanalysis.presentation.LmsSessionCookieRequest
import com.ssutime.assignmentanalysis.presentation.LmsSessionRequest
import com.ssutime.common.exception.InvalidRequestException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AssignmentContentExtractorTest {
    private val lmsCanvasClient: LmsCanvasClient = mockk()
    private val extractor =
        AssignmentContentExtractor(
            htmlParser = AssignmentHtmlParser(),
            lmsCanvasClient = lmsCanvasClient,
            textExtractor = AttachmentTextExtractor(),
        )

    @Test
    fun `extract requires LMS session when file links exist`() {
        val payload =
            AssignmentAnalysisPayload(
                courseId = 44383L,
                assignmentId = 10L,
                assignmentHtml = "<a data-api-endpoint=\"https://canvas.ssu.ac.kr/api/v1/courses/44383/files/1\">f.txt</a>",
                lmsSession = null,
            )

        assertFailsWith<InvalidRequestException> { extractor.extract(payload) }
    }

    @Test
    fun `extract downloads allowed Canvas file and combines attachment text`() {
        val session = CanvasSession(httpClient = HttpClient.newHttpClient())
        val metadata =
            CanvasFileMetadata(
                fileId = 1L,
                displayName = "guide.txt",
                contentType = "text/plain",
                size = 12L,
                downloadUrl = "https://canvas.ssu.ac.kr/files/1/download",
            )
        every { lmsCanvasClient.createSession(validCookieSession()) } returns session
        every { lmsCanvasClient.getFileMetadata(session, 44383L, 1L) } returns metadata
        every { lmsCanvasClient.downloadFile(session, metadata) } returns "제출: report.pdf".toByteArray()

        val payload =
            AssignmentAnalysisPayload(
                courseId = 44383L,
                assignmentId = 10L,
                assignmentHtml =
                    """
                    <p>과제 설명</p>
                    <a data-api-endpoint="https://canvas.ssu.ac.kr/api/v1/courses/44383/files/1">guide.txt</a>
                    """.trimIndent(),
                lmsSession = validCookieSession(),
            )

        val extracted = extractor.extract(payload)

        assertTrue(extracted.sanitizedContent.contains("과제 설명"))
        assertTrue(extracted.sanitizedContent.contains("제출: report.pdf"))
    }

    @Test
    fun `extract rejects assignment when all attachments exceed metadata size limit`() {
        val session = CanvasSession(httpClient = HttpClient.newHttpClient())
        val metadata =
            CanvasFileMetadata(
                fileId = 1L,
                displayName = "huge.txt",
                contentType = "text/plain",
                size = AssignmentAnalysisLimits.MAX_SINGLE_FILE_BYTES + 1,
                downloadUrl = "https://canvas.ssu.ac.kr/files/1/download",
            )
        every { lmsCanvasClient.createSession(validCookieSession()) } returns session
        every { lmsCanvasClient.getFileMetadata(session, 44383L, 1L) } returns metadata

        val payload =
            AssignmentAnalysisPayload(
                courseId = 44383L,
                assignmentId = 10L,
                assignmentHtml =
                    """
                    <p>과제 설명</p>
                    <a data-api-endpoint="https://canvas.ssu.ac.kr/api/v1/courses/44383/files/1">huge.txt</a>
                    """.trimIndent(),
                lmsSession = validCookieSession(),
            )

        val exception =
            assertFailsWith<InvalidRequestException> {
                extractor.extract(payload)
            }

        assertEquals("No analyzable attachment", exception.message)
        verify(exactly = 0) { lmsCanvasClient.downloadFile(any(), any()) }
    }

    @Test
    fun `extract rejects assignment when downloaded attachment is too large`() {
        val session = CanvasSession(httpClient = HttpClient.newHttpClient())
        val metadata =
            CanvasFileMetadata(
                fileId = 1L,
                displayName = "guide.txt",
                contentType = "text/plain",
                size = 12L,
                downloadUrl = "https://canvas.ssu.ac.kr/files/1/download",
            )
        every { lmsCanvasClient.createSession(validCookieSession()) } returns session
        every { lmsCanvasClient.getFileMetadata(session, 44383L, 1L) } returns metadata
        every { lmsCanvasClient.downloadFile(session, metadata) } returns
            ByteArray(
                (AssignmentAnalysisLimits.MAX_SINGLE_FILE_BYTES + 1).toInt(),
            )

        val payload =
            AssignmentAnalysisPayload(
                courseId = 44383L,
                assignmentId = 10L,
                assignmentHtml =
                    """
                    <p>과제 설명</p>
                    <a data-api-endpoint="https://canvas.ssu.ac.kr/api/v1/courses/44383/files/1">guide.txt</a>
                    """.trimIndent(),
                lmsSession = validCookieSession(),
            )

        val exception =
            assertFailsWith<InvalidRequestException> {
                extractor.extract(payload)
            }

        assertEquals("No analyzable attachment", exception.message)
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
