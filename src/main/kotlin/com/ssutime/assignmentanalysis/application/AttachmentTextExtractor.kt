package com.ssutime.assignmentanalysis.application

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFGroupShape
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFTable
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

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
                "pptx" -> ExtractedAttachment(metadata.displayName, extractPptx(bytes))
                "hwpx" -> ExtractedAttachment(metadata.displayName, extractHwpx(bytes))
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

    private fun extractPptx(bytes: ByteArray): String =
        XMLSlideShow(ByteArrayInputStream(bytes)).use { slideShow ->
            slideShow.slides
                .flatMap { slide -> slide.shapes.flatMap(::extractPptxShapeText) }
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .normalizeText()
                .take(AssignmentAnalysisLimits.MAX_ATTACHMENT_CHARS)
        }

    private fun extractPptxShapeText(shape: XSLFShape): List<String> =
        when (shape) {
            is XSLFTable ->
                shape.rows.flatMap { row ->
                    row.cells.map { cell -> cell.text }
                }
            is XSLFGroupShape -> shape.shapes.flatMap(::extractPptxShapeText)
            is XSLFTextShape -> listOf(shape.text)
            else -> emptyList()
        }

    private fun extractHwpx(bytes: ByteArray): String {
        val sections = mutableListOf<HwpxSection>()
        var entryCount = 0
        var expandedBytes = 0L
        var mediaType: String? = null

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                require(entryCount <= AssignmentAnalysisLimits.MAX_ZIP_ENTRIES) {
                    "hwpx entry count limit exceeded"
                }
                require(isSafeZipEntry(entry.name)) { "unsafe hwpx entry ${entry.name}" }
                if (entry.isDirectory) continue

                val shouldCapture = isHwpxSection(entry) || entry.name.equals("mimetype", ignoreCase = true)
                val entryBytes = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var entryBytesRead = 0L
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    entryBytesRead += read
                    expandedBytes += read
                    require(entryBytesRead <= AssignmentAnalysisLimits.MAX_ZIP_ENTRY_BYTES) {
                        "hwpx entry size limit exceeded ${entry.name}"
                    }
                    require(expandedBytes <= AssignmentAnalysisLimits.MAX_ZIP_EXPANDED_BYTES) {
                        "hwpx expanded size limit exceeded"
                    }
                    if (shouldCapture) entryBytes.write(buffer, 0, read)
                }

                when {
                    entry.name.equals("mimetype", ignoreCase = true) ->
                        mediaType = entryBytes.toString(StandardCharsets.US_ASCII).trim()
                    isHwpxSection(entry) -> sections += HwpxSection(entry.name, entryBytes.toByteArray())
                }
            }
        }
        require(mediaType == HWPX_MEDIA_TYPE) { "invalid hwpx media type" }
        require(sections.isNotEmpty()) { "invalid hwpx structure" }

        return sections
            .sortedBy { section -> section.order }
            .joinToString("\n") { section -> extractHwpxSectionText(section.bytes) }
            .normalizeText()
            .take(AssignmentAnalysisLimits.MAX_ATTACHMENT_CHARS)
    }

    private fun isHwpxSection(entry: ZipEntry): Boolean = HWPX_SECTION_PATTERN.matches(entry.name)

    private fun extractHwpxSectionText(bytes: ByteArray): String {
        val factory = XMLInputFactory.newFactory()
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false)
        val reader = factory.createXMLStreamReader(ByteArrayInputStream(bytes))
        val text = StringBuilder()
        var inTextElement = false
        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT ->
                        if (reader.localName == "t") inTextElement = true
                    XMLStreamConstants.CHARACTERS,
                    XMLStreamConstants.CDATA,
                    -> if (inTextElement) text.append(reader.text)
                    XMLStreamConstants.END_ELEMENT ->
                        if (reader.localName == "t") {
                            inTextElement = false
                            text.append('\n')
                        }
                }
            }
        } finally {
            reader.close()
        }
        return text.toString()
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
        private val SUPPORTED_EXTENSIONS = TEXT_EXTENSIONS + setOf("pdf", "docx", "pptx", "hwpx", "zip")
        private const val HWPX_MEDIA_TYPE = "application/hwp+zip"
        private val HWPX_SECTION_PATTERN = Regex("Contents/section(\\d+)\\.xml", RegexOption.IGNORE_CASE)
    }

    private data class HwpxSection(
        val name: String,
        val bytes: ByteArray,
    ) {
        val order: Int =
            HWPX_SECTION_PATTERN
                .matchEntire(name)
                ?.groupValues
                ?.get(1)
                ?.toInt() ?: Int.MAX_VALUE
    }
}
