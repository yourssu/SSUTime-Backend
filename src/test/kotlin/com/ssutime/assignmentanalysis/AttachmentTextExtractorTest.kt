package com.ssutime.assignmentanalysis

import com.ssutime.assignmentanalysis.application.AssignmentAnalysisLimits
import com.ssutime.assignmentanalysis.application.AttachmentTextExtractor
import com.ssutime.assignmentanalysis.application.CanvasFileMetadata
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentTextExtractorTest {
    private val extractor = AttachmentTextExtractor()

    @Test
    fun `zip extraction skips unsafe entries and keeps safe text entries`() {
        val bytes =
            zipBytes(
                "src/Main.kt" to "fun main() = println(\"ok\")",
                "../secret.txt" to "pwnd",
            )
        val metadata =
            CanvasFileMetadata(
                fileId = 1L,
                displayName = "project.zip",
                contentType = "application/zip",
                size = bytes.size.toLong(),
                downloadUrl = "https://canvas.ssu.ac.kr/files/1/download",
            )

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extracted.text.contains("src/Main.kt"))
        assertTrue(extracted.text.contains("unsafe zip entry"))
        assertFalse(extracted.text.contains("pwnd"))
    }

    @Test
    fun `zip extraction records entry count and entry size limits`() {
        val tooManyEntries =
            (1..(AssignmentAnalysisLimits.MAX_ZIP_ENTRIES + 1))
                .map { index ->
                    "entry$index.txt" to "ok"
                }.toTypedArray()
        val entryCountMetadata =
            CanvasFileMetadata(
                fileId = 2L,
                displayName = "too-many.zip",
                contentType = "application/zip",
                size = 100L,
                downloadUrl = "https://canvas.ssu.ac.kr/files/2/download",
            )
        val entryCountResult = extractor.extract(entryCountMetadata, zipBytes(*tooManyEntries))

        assertTrue(entryCountResult.text.contains("zip entry count limit exceeded"))

        val hugeEntryMetadata =
            CanvasFileMetadata(
                fileId = 3L,
                displayName = "huge-entry.zip",
                contentType = "application/zip",
                size = 100L,
                downloadUrl = "https://canvas.ssu.ac.kr/files/3/download",
            )
        val hugeEntryResult =
            extractor.extract(
                hugeEntryMetadata,
                zipBytes("huge.txt" to "x".repeat((AssignmentAnalysisLimits.MAX_ZIP_ENTRY_BYTES + 1).toInt())),
            )

        assertTrue(hugeEntryResult.text.contains("zip entry size limit exceeded"))
    }

    @Test
    fun `pdf extraction reads direct pdf attachments`() {
        val bytes = pdfBytes("Direct PDF assignment guide")
        val metadata =
            CanvasFileMetadata(
                fileId = 5L,
                displayName = "guide.pdf",
                contentType = "application/pdf",
                size = bytes.size.toLong(),
                downloadUrl = "https://canvas.ssu.ac.kr/files/5/download",
            )

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extracted.text.contains("Direct PDF assignment guide"))
    }

    @Test
    fun `zip extraction reads pdf entries`() {
        val bytes = zipBytes("docs/spec.pdf" to pdfBytes("PDF assignment specification"))
        val metadata =
            CanvasFileMetadata(
                fileId = 4L,
                displayName = "project-with-pdf.zip",
                contentType = "application/zip",
                size = bytes.size.toLong(),
                downloadUrl = "https://canvas.ssu.ac.kr/files/4/download",
            )

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extracted.text.contains("docs/spec.pdf"))
        assertTrue(extracted.text.contains("PDF assignment specification"))
    }

    @Test
    fun `pptx extraction reads text in slide order`() {
        val bytes = pptxBytes("First slide", "Second slide")
        val metadata = metadata("presentation.PPTX", "application/vnd.openxmlformats-officedocument.presentationml.presentation", bytes)

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extractor.supports(metadata))
        assertTrue(extracted.skippedReason == null, extracted.skippedReason)
        assertTrue(extracted.text.indexOf("First slide") < extracted.text.indexOf("Second slide"))
    }

    @Test
    fun `hwpx extraction reads section text in document order`() {
        val bytes =
            zipBytes(
                "mimetype" to "application/hwp+zip",
                "Contents/section1.xml" to hwpxSection("Second section"),
                "Contents/section0.xml" to hwpxSection("First section", "Table cell"),
            )
        val metadata = metadata("assignment.HWPX", "application/hwp+zip", bytes)

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extractor.supports(metadata))
        assertTrue(extracted.skippedReason == null, extracted.skippedReason)
        assertTrue(extracted.text.contains("Table cell"))
        assertTrue(extracted.text.indexOf("First section") < extracted.text.indexOf("Second section"))
    }

    @Test
    fun `hwpx extraction rejects malformed document`() {
        val bytes =
            zipBytes(
                "mimetype" to "application/hwp+zip",
                "Contents/section0.xml" to "<hp:section>",
            )
        val metadata = metadata("broken.hwpx", "application/hwp+zip", bytes)

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extracted.text.isEmpty())
        assertTrue(extracted.skippedReason?.startsWith("extract failed:") == true)
    }

    @Test
    fun `hwpx extraction rejects external entities`() {
        val section =
            """
            <!DOCTYPE section [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <hp:section xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph">
                <hp:t>&secret;</hp:t>
            </hp:section>
            """.trimIndent()
        val bytes =
            zipBytes(
                "mimetype" to "application/hwp+zip",
                "Contents/section0.xml" to section,
            )
        val metadata = metadata("external-entity.hwpx", "application/hwp+zip", bytes)

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extracted.text.isEmpty())
        assertTrue(extracted.skippedReason?.startsWith("extract failed:") == true)
    }

    @Test
    fun `hwpx extraction enforces archive entry count limit`() {
        val entries =
            (0..AssignmentAnalysisLimits.MAX_ZIP_ENTRIES)
                .map { index -> "Metadata/item$index.xml" to "<metadata/>" }
                .toTypedArray()
        val bytes = zipBytes(*entries)
        val metadata = metadata("too-many-entries.hwpx", "application/hwp+zip", bytes)

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extracted.text.isEmpty())
        assertTrue(extracted.skippedReason?.startsWith("extract failed:") == true)
    }

    @Test
    fun `hwpx extraction rejects oversized non-section entries`() {
        val bytes =
            zipBytes(
                "mimetype" to "application/hwp+zip",
                "BinData/image.bin" to ByteArray((AssignmentAnalysisLimits.MAX_ZIP_ENTRY_BYTES + 1).toInt()),
                "Contents/section0.xml" to hwpxSection("Assignment"),
            )
        val metadata = metadata("oversized-entry.hwpx", "application/hwp+zip", bytes)

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extracted.text.isEmpty())
        assertTrue(extracted.skippedReason?.startsWith("extract failed:") == true)
    }

    @Test
    fun `hwpx extraction rejects zip without hwpx media type`() {
        val bytes = zipBytes("Contents/section0.xml" to hwpxSection("Not actually HWPX"))
        val metadata = metadata("fake.hwpx", "application/zip", bytes)

        val extracted = extractor.extract(metadata, bytes)

        assertTrue(extracted.text.isEmpty())
        assertTrue(extracted.skippedReason?.startsWith("extract failed:") == true)
    }

    @Test
    fun `binary hwp and xlsx remain unsupported`() {
        val hwp = metadata("document.hwp", "application/x-hwp", byteArrayOf())
        val xlsx =
            metadata(
                "spreadsheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                byteArrayOf(),
            )

        assertFalse(extractor.supports(hwp))
        assertFalse(extractor.supports(xlsx))
    }

    private fun metadata(
        displayName: String,
        contentType: String,
        bytes: ByteArray,
    ): CanvasFileMetadata =
        CanvasFileMetadata(
            fileId = 10L,
            displayName = displayName,
            contentType = contentType,
            size = bytes.size.toLong(),
            downloadUrl = "https://canvas.ssu.ac.kr/files/10/download",
        )

    private fun pptxBytes(vararg slideTexts: String): ByteArray {
        val output = ByteArrayOutputStream()
        XMLSlideShow().use { slideShow ->
            slideTexts.forEach { text ->
                slideShow.createSlide().createTextBox().text = text
            }
            slideShow.write(output)
        }
        return output.toByteArray()
    }

    private fun hwpxSection(vararg texts: String): String =
        "<hp:section xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\">" +
            texts.joinToString("") { text -> "<hp:t>$text</hp:t>" } +
            "</hp:section>"

    private fun zipBytes(vararg entries: Pair<String, Any>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                val bytes =
                    when (content) {
                        is ByteArray -> content
                        else -> content.toString().toByteArray()
                    }
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun pdfBytes(text: String): ByteArray {
        val output = ByteArrayOutputStream()
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(50f, 700f)
                content.showText(text)
                content.endText()
            }
            document.save(output)
        }
        return output.toByteArray()
    }
}
