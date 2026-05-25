package com.ssutime.assignmentanalysis.application

import com.ssutime.common.exception.InvalidRequestException
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import java.net.URI

@Component
class AssignmentHtmlParser {
    fun parse(html: String): ParsedAssignmentHtml {
        if (html.length > AssignmentAnalysisLimits.MAX_HTML_CHARS) {
            throw InvalidRequestException("assignmentHtml is too large")
        }

        val document = Jsoup.parse(html)
        document.select("script, style, iframe, object, embed").remove()

        val text =
            document
                .text()
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(AssignmentAnalysisLimits.MAX_EXTRACTED_CHARS)

        val links =
            document
                .select("a[href], a[data-api-endpoint]")
                .flatMap { element ->
                    listOfNotNull(
                        element.attr("data-api-endpoint").takeIf { it.isNotBlank() },
                        element.attr("href").takeIf { it.isNotBlank() },
                    ).mapNotNull { rawUrl -> parseCanvasFileLink(rawUrl, element.text()) }
                }.distinctBy { it.fileId }
                .take(AssignmentAnalysisLimits.MAX_FILE_COUNT)

        return ParsedAssignmentHtml(text = text, fileLinks = links)
    }

    private fun parseCanvasFileLink(
        rawUrl: String,
        label: String,
    ): CanvasFileLink? {
        val uri = runCatching { resolveCanvasUri(rawUrl) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != CANVAS_HOST) return null

        val path = uri.path ?: return null
        val courseFileMatch =
            COURSE_FILE_API_REGEX.matchEntire(path)
                ?: COURSE_FILE_DOWNLOAD_REGEX.matchEntire(path)
        if (courseFileMatch != null) {
            return CanvasFileLink(
                courseId = courseFileMatch.groupValues[1].toLong(),
                fileId = courseFileMatch.groupValues[2].toLong(),
                sourceUrl = rawUrl,
                label = label,
            )
        }

        val fileMatch =
            FILE_API_REGEX.matchEntire(path)
                ?: FILE_DOWNLOAD_REGEX.matchEntire(path)
                ?: return null
        return CanvasFileLink(
            courseId = null,
            fileId = fileMatch.groupValues[1].toLong(),
            sourceUrl = rawUrl,
            label = label,
        )
    }

    private fun resolveCanvasUri(rawUrl: String): URI {
        val uri = URI(rawUrl)
        return if (uri.isAbsolute) uri else URI(CANVAS_ORIGIN).resolve(uri)
    }

    companion object {
        private const val CANVAS_ORIGIN = "https://canvas.ssu.ac.kr"
        const val CANVAS_HOST = "canvas.ssu.ac.kr"
        private val COURSE_FILE_API_REGEX = Regex("/api/v1/courses/(\\d+)/files/(\\d+)")
        private val COURSE_FILE_DOWNLOAD_REGEX = Regex("/courses/(\\d+)/files/(\\d+)/download")
        private val FILE_API_REGEX = Regex("/api/v1/files/(\\d+)")
        private val FILE_DOWNLOAD_REGEX = Regex("/files/(\\d+)/download")
    }
}
