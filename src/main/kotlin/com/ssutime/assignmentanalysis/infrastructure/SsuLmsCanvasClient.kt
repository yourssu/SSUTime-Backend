package com.ssutime.assignmentanalysis.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.ssutime.assignmentanalysis.application.AssignmentAnalysisLimits
import com.ssutime.assignmentanalysis.application.AssignmentHtmlParser
import com.ssutime.assignmentanalysis.application.CanvasFileMetadata
import com.ssutime.assignmentanalysis.application.CanvasSession
import com.ssutime.assignmentanalysis.application.LmsCanvasClient
import com.ssutime.assignmentanalysis.presentation.LmsSessionCookieRequest
import com.ssutime.assignmentanalysis.presentation.LmsSessionRequest
import com.ssutime.common.exception.InvalidRequestException
import org.springframework.stereotype.Component
import java.io.InputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class SsuLmsCanvasClient(
    private val objectMapper: ObjectMapper,
) : LmsCanvasClient {
    override fun createSession(lmsSession: LmsSessionRequest): CanvasSession {
        if (lmsSession.cookies.none { it.name.isNotBlank() && it.value.isNotBlank() }) {
            throw InvalidRequestException("LMS session cookies are required")
        }

        val cookieManager = newCookieManager()
        lmsSession.cookies.forEach { cookie -> addAllowedCookie(cookieManager, cookie) }
        return CanvasSession(httpClient = newHttpClient(cookieManager))
    }

    override fun getFileMetadata(
        session: CanvasSession,
        courseId: Long,
        fileId: Long,
    ): CanvasFileMetadata {
        val response =
            sendWithAuth(
                session = session,
                uri = "$CANVAS_ORIGIN/api/v1/courses/$courseId/files/$fileId",
            )
        if (response.statusCode() !in 200..299) {
            throw InvalidRequestException("Canvas file metadata request failed")
        }
        val root = objectMapper.readTree(response.body().removeCanvasJsonPrefix())
        val metadataFileId = root.path("id").asLong()
        if (metadataFileId != fileId) {
            throw InvalidRequestException("Canvas file metadata id mismatch")
        }
        return CanvasFileMetadata(
            fileId = metadataFileId,
            displayName = root.path("display_name").asText(root.path("filename").asText("file-$fileId")),
            contentType = root.path("content-type").asText(root.path("content_type").asText("")),
            size = root.path("size").asLong(0L),
            downloadUrl = root.path("url").asText(""),
        )
    }

    override fun downloadFile(
        session: CanvasSession,
        file: CanvasFileMetadata,
    ): ByteArray {
        if (file.downloadUrl.isBlank() || !isAllowedCanvasDownloadUri(file.downloadUrl)) {
            throw InvalidRequestException("Canvas file download URL was rejected")
        }
        val response = sendWithAuthFollowingSafeRedirects(session, file.downloadUrl)
        if (response.statusCode() !in 200..299) {
            throw InvalidRequestException("Canvas file download failed")
        }
        return response.body()
    }

    private fun sendWithAuth(
        session: CanvasSession,
        uri: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI(uri))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build()
        return session.httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun sendWithAuthFollowingSafeRedirects(
        session: CanvasSession,
        uri: String,
    ): HttpResponse<ByteArray> {
        var currentUri = uri
        repeat(AssignmentAnalysisLimits.MAX_REDIRECTS + 1) {
            if (!isAllowedCanvasDownloadUri(currentUri)) {
                throw InvalidRequestException("Canvas redirect target was rejected")
            }
            val request =
                HttpRequest
                    .newBuilder(URI(currentUri))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build()
            val response = session.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 300..399) {
                return HttpResponseAdapter(
                    delegate = response,
                    bodyBytes = response.body().readBoundedBytes(AssignmentAnalysisLimits.MAX_SINGLE_FILE_BYTES),
                )
            }
            response.body().close()
            currentUri =
                response
                    .headers()
                    .firstValue("Location")
                    .orElseThrow { InvalidRequestException("Canvas redirect without Location") }
        }
        throw InvalidRequestException("Canvas redirect limit exceeded")
    }

    private fun String.removeCanvasJsonPrefix(): String = removePrefix("while(1);")

    private fun addAllowedCookie(
        cookieManager: CookieManager,
        request: LmsSessionCookieRequest,
    ) {
        if (request.name.isBlank() || request.value.isBlank()) return
        val domain = request.domain.trim().lowercase()
        if (domain !in ALLOWED_COOKIE_DOMAINS) {
            throw InvalidRequestException("Unsupported LMS cookie domain")
        }
        val cookie =
            HttpCookie(request.name, request.value).apply {
                this.domain = domain
                path = request.path.takeIf { it.startsWith("/") } ?: "/"
                secure = true
            }
        cookieManager.cookieStore.add(URI("https://$domain"), cookie)
    }

    private fun InputStream.readBoundedBytes(maxBytes: Long): ByteArray =
        use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw InvalidRequestException("Canvas file exceeded download limit")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }

    private class HttpResponseAdapter<T>(
        private val delegate: HttpResponse<T>,
        private val bodyBytes: ByteArray,
    ) : HttpResponse<ByteArray> {
        override fun statusCode(): Int = delegate.statusCode()

        override fun request(): HttpRequest = delegate.request()

        override fun previousResponse(): java.util.Optional<HttpResponse<ByteArray>> = java.util.Optional.empty()

        override fun headers(): java.net.http.HttpHeaders = delegate.headers()

        override fun body(): ByteArray = bodyBytes

        override fun sslSession(): java.util.Optional<javax.net.ssl.SSLSession> = delegate.sslSession()

        override fun uri(): URI = delegate.uri()

        override fun version(): HttpClient.Version = delegate.version()
    }

    private fun newCookieManager(): CookieManager = CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }

    private fun newHttpClient(cookieManager: CookieManager): HttpClient =
        HttpClient
            .newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(REQUEST_TIMEOUT)
            .build()

    private fun isAllowedCanvasDownloadUri(raw: String): Boolean =
        runCatching {
            val uri = URI(raw)
            uri.scheme == "https" && uri.host == AssignmentHtmlParser.CANVAS_HOST
        }.getOrDefault(false)

    companion object {
        private const val CANVAS_ORIGIN = "https://canvas.ssu.ac.kr"
        private val ALLOWED_COOKIE_DOMAINS = setOf("canvas.ssu.ac.kr", "lms.ssu.ac.kr")
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
