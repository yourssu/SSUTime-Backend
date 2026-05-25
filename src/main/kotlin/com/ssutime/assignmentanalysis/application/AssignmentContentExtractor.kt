package com.ssutime.assignmentanalysis.application

import com.ssutime.assignmentanalysis.presentation.AssignmentAnalysisPayload
import com.ssutime.common.exception.InvalidRequestException
import org.springframework.stereotype.Component

@Component
class AssignmentContentExtractor(
    private val htmlParser: AssignmentHtmlParser,
    private val lmsCanvasClient: LmsCanvasClient,
    private val textExtractor: AttachmentTextExtractor,
) {
    fun extract(payload: AssignmentAnalysisPayload): ExtractedAssignmentContent {
        val parsed = htmlParser.parse(payload.assignmentHtml)
        val skipped = mutableListOf<String>()
        val attachmentTexts = mutableListOf<ExtractedAttachment>()

        if (parsed.fileLinks.isNotEmpty()) {
            val lmsSession =
                payload.lmsSession
                    ?: throw InvalidRequestException("lmsSession is required when assignmentHtml has file links")
            val session = lmsCanvasClient.createSession(lmsSession)
            var totalBytes = 0L

            parsed.fileLinks.forEach { link ->
                if (link.courseId != null && link.courseId != payload.courseId) {
                    skipped += "${link.label}: course mismatch"
                    return@forEach
                }

                val metadata = lmsCanvasClient.getFileMetadata(session, payload.courseId, link.fileId)
                val validationError = validateMetadata(metadata, totalBytes)
                if (validationError != null) {
                    skipped += "${metadata.displayName}: $validationError"
                    return@forEach
                }

                val bytes = lmsCanvasClient.downloadFile(session, metadata)
                if (bytes.size.toLong() > AssignmentAnalysisLimits.MAX_SINGLE_FILE_BYTES) {
                    skipped += "${metadata.displayName}: downloaded file too large"
                    return@forEach
                }
                totalBytes += bytes.size
                if (totalBytes > AssignmentAnalysisLimits.MAX_TOTAL_DOWNLOAD_BYTES) {
                    skipped += "${metadata.displayName}: total download limit exceeded"
                    return@forEach
                }

                attachmentTexts += textExtractor.extract(metadata, bytes)
            }
        }

        val allSkipped =
            skipped +
                attachmentTexts.mapNotNull { attachment ->
                    attachment.skippedReason?.let { "${attachment.fileName}: $it" }
                }
        val content = buildSanitizedContent(parsed.text, attachmentTexts)
        if (content.isBlank()) {
            throw InvalidRequestException("No analyzable assignment content")
        }
        return ExtractedAssignmentContent(
            sanitizedContent = content.take(AssignmentAnalysisLimits.MAX_EXTRACTED_CHARS),
            skippedFiles = allSkipped,
        )
    }

    private fun validateMetadata(
        metadata: CanvasFileMetadata,
        totalBytes: Long,
    ): String? {
        if (metadata.size > AssignmentAnalysisLimits.MAX_SINGLE_FILE_BYTES) {
            return "file too large"
        }
        if (totalBytes + metadata.size > AssignmentAnalysisLimits.MAX_TOTAL_DOWNLOAD_BYTES) {
            return "total download limit exceeded"
        }
        if (!textExtractor.supports(metadata)) {
            return "unsupported file type"
        }
        if (metadata.contentType.startsWith("image/") || metadata.contentType.startsWith("video/")) {
            return "unsupported media content type"
        }
        return null
    }

    private fun buildSanitizedContent(
        htmlText: String,
        attachments: List<ExtractedAttachment>,
    ): String =
        buildString {
            appendLine("[ASSIGNMENT_HTML_TEXT]")
            appendLine(htmlText)
            attachments
                .filter { it.skippedReason == null && it.text.isNotBlank() }
                .forEach { attachment ->
                    appendLine()
                    appendLine("[ATTACHMENT: ${attachment.fileName}]")
                    appendLine(attachment.text)
                }
        }
}
