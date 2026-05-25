package com.ssutime.assignmentanalysis.application

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.zip.ZipInputStream

@Component
class AttachmentTextExtractor {
    fun supports(metadata: CanvasFileMetadata): Boolean = extension(metadata.displayName) in SUPPORTED_EXTENSIONS

    fun extract(
        metadata: CanvasFileMetadata,
        bytes: ByteArray,
    ): ExtractedAttachment =
        runCatching {
            when (extension(metadata.displayName)) {
                "pdf" -> ExtractedAttachment(metadata.displayName, extractPdf(bytes))
                "docx" -> ExtractedAttachment(metadata.displayName, extractDocx(bytes))
                "zip" -> ExtractedAttachment(metadata.displayName, extractZip(bytes))
                in TEXT_EXTENSIONS -> ExtractedAttachment(metadata.displayName, decodeText(bytes))
                else -> ExtractedAttachment(metadata.displayName, "", "unsupported file type")
            }
        }.getOrElse { throwable ->
            ExtractedAttachment(
                fileName = metadata.displayName,
                text = "",
                skippedReason = "extract failed: ${throwable.javaClass.simpleName}",
            )
        }

    private fun extractPdf(bytes: ByteArray): String =
        Loader.loadPDF(bytes).use { document ->
            PDFTextStripper()
                .getText(document)
                .normalizeText()
                .take(AssignmentAnalysisLimits.MAX_ATTACHMENT_CHARS)
        }

    private fun extractDocx(bytes: ByteArray): String =
        XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
            val paragraphs = document.paragraphs.joinToString("\n") { it.text }
            val tables =
                document.tables.joinToString("\n") { table ->
                    table.rows.joinToString("\n") { row ->
                        row.tableCells.joinToString("\t") { it.text }
                    }
                }
            "$paragraphs\n$tables"
                .normalizeText()
                .take(AssignmentAnalysisLimits.MAX_ATTACHMENT_CHARS)
        }

    private fun extractZip(bytes: ByteArray): String {
        var entryCount = 0
        var expandedBytes = 0L
        val texts = mutableListOf<String>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                if (entryCount > AssignmentAnalysisLimits.MAX_ZIP_ENTRIES) {
                    texts += "[SKIPPED: zip entry count limit exceeded]"
                    break
                }
                if (entry.isDirectory) continue

                val entryName = entry.name
                if (!isSafeZipEntry(entryName)) {
                    texts += "[SKIPPED: unsafe zip entry $entryName]"
                    continue
                }
                val ext = extension(entryName)
                if (ext == "zip") {
                    texts += "[SKIPPED: nested zip $entryName]"
                    continue
                }
                if (ext !in ZIP_EXTRACTABLE_EXTENSIONS) continue

                val entryBytes = readZipEntry(zip)
                if (entryBytes == null) {
                    texts += "[SKIPPED: zip entry size limit exceeded $entryName]"
                    continue
                }
                expandedBytes += entryBytes.size
                if (expandedBytes > AssignmentAnalysisLimits.MAX_ZIP_EXPANDED_BYTES) {
                    texts += "[SKIPPED: zip expanded size limit exceeded]"
                    break
                }
                val entryText = extractZipEntryText(ext, entryBytes)
                if (entryText.isNotBlank()) {
                    texts += "[ZIP_ENTRY: $entryName]\n$entryText"
                }
            }
        }

        return texts
            .joinToString("\n\n")
            .normalizeText()
            .take(AssignmentAnalysisLimits.MAX_ATTACHMENT_CHARS)
    }

    private fun extractZipEntryText(
        extension: String,
        bytes: ByteArray,
    ): String =
        when (extension) {
            "pdf" -> extractPdf(bytes)
            else -> decodeText(bytes)
        }

    private fun readZipEntry(zip: ZipInputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > AssignmentAnalysisLimits.MAX_ZIP_ENTRY_BYTES) {
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun isSafeZipEntry(name: String): Boolean {
        val normalized = Paths.get(name).normalize()
        return !normalized.isAbsolute && !normalized.startsWith("..") && !name.contains('\u0000')
    }

    private fun decodeText(bytes: ByteArray): String =
        Charsets.UTF_8
            .decodeOrFallback(bytes)
            .normalizeText()
            .take(AssignmentAnalysisLimits.MAX_ATTACHMENT_CHARS)

    private fun Charset.decodeOrFallback(bytes: ByteArray): String =
        runCatching {
            newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrElse {
            String(bytes, StandardCharsets.ISO_8859_1)
        }

    private fun String.normalizeText(): String =
        replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), " ")
            .replace(Regex("[ \t]+"), " ")
            .trim()

    private fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    companion object {
        private val TEXT_EXTENSIONS =
            setOf(
                "txt",
                "md",
                "markdown",
                "kt",
                "java",
                "py",
                "js",
                "ts",
                "json",
                "xml",
                "html",
                "css",
                "c",
                "cpp",
                "h",
                "hpp",
                "sql",
                "yml",
                "yaml",
                "gradle",
                "csv",
                "properties",
            )
        private val ZIP_EXTRACTABLE_EXTENSIONS = TEXT_EXTENSIONS + setOf("pdf")
        private val SUPPORTED_EXTENSIONS = TEXT_EXTENSIONS + setOf("pdf", "docx", "zip")
    }
}
