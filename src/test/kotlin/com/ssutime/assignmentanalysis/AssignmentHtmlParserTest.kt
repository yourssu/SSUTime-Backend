package com.ssutime.assignmentanalysis

import com.ssutime.assignmentanalysis.application.AssignmentHtmlParser
import kotlin.test.Test
import kotlin.test.assertEquals

class AssignmentHtmlParserTest {
    private val parser = AssignmentHtmlParser()

    @Test
    fun `parse extracts Canvas file links and sanitizes visible text`() {
        val parsed =
            parser.parse(
                """
                <p>실습과제 2 설명</p>
                <script>alert('x')</script>
                <a class="instructure_file_link"
                   href="https://canvas.ssu.ac.kr/courses/44383/files/4322266/download?wrap=1"
                   data-api-endpoint="https://canvas.ssu.ac.kr/api/v1/courses/44383/files/4322266">
                   project#2-1.zip
                </a>
                <a href="https://evil.example.com/steal">evil</a>
                """.trimIndent(),
            )

        assertEquals("실습과제 2 설명 project#2-1.zip evil", parsed.text)
        assertEquals(1, parsed.fileLinks.size)
        assertEquals(44383L, parsed.fileLinks.single().courseId)
        assertEquals(4322266L, parsed.fileLinks.single().fileId)
    }

    @Test
    fun `parse extracts relative Canvas file download links`() {
        val parsed =
            parser.parse(
                """
                <p>과제 설명</p>
                <a class="instructure_file_link"
                   title="project#4.zip"
                   href="/courses/44383/files/4550358/download?wrap=1"
                   target="_blank"
                   data-canvas-previewable="false">project#4.zip</a>
                """.trimIndent(),
            )

        assertEquals(1, parsed.fileLinks.size)
        assertEquals(44383L, parsed.fileLinks.single().courseId)
        assertEquals(4550358L, parsed.fileLinks.single().fileId)
    }
}
