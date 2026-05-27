package com.ssutime.assignmentanalysis.domain

import com.ssutime.common.domain.BaseEntity
import com.ssutime.todo.domain.Todo
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "assignment_analysis",
    uniqueConstraints = [
        UniqueConstraint(
            columnNames = ["todo_id", "course_id", "assignment_id", "content_hash"],
        ),
    ],
)
class AssignmentAnalysis private constructor(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    val todo: Todo,
    @Column(nullable = false)
    val courseId: Long,
    @Column(nullable = false)
    val assignmentId: Long,
    @Column(nullable = false, length = 64)
    val contentHash: String,
    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    val sanitizedContent: String,
    @Lob
    @Column(nullable = false)
    var skippedFiles: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AssignmentAnalysisStatus = AssignmentAnalysisStatus.PENDING,
    @Lob
    var analysis: String? = null,
    var estimatedDurationMinutes: Int? = null,
    var errorCode: String? = null,
    var analyzedAt: LocalDateTime? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    fun markAnalyzing() {
        status = AssignmentAnalysisStatus.ANALYZING
        errorCode = null
    }

    fun markSucceeded(
        summary: String,
        estimatedDurationMinutes: Int,
    ) {
        val oneLineSummary = summary.toOneLineSummary()
        status = AssignmentAnalysisStatus.SUCCEEDED
        analysis = oneLineSummary
        this.estimatedDurationMinutes = estimatedDurationMinutes.toHalfHourMinutes()
        errorCode = null
        analyzedAt = LocalDateTime.now()
    }

    fun markFailed(errorCode: String) {
        status = AssignmentAnalysisStatus.FAILED
        this.errorCode = errorCode.take(MAX_ERROR_CODE_LENGTH)
        analyzedAt = LocalDateTime.now()
    }

    private fun Int.toHalfHourMinutes(): Int =
        coerceAtLeast(HALF_HOUR_MINUTES).let { minutes ->
            ((minutes + HALF_HOUR_MINUTES - 1) / HALF_HOUR_MINUTES) * HALF_HOUR_MINUTES
        }

    private fun String.toOneLineSummary(): String =
        lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.replace(Regex("""\s+"""), " ")
            .orEmpty()

    companion object {
        private const val HALF_HOUR_MINUTES = 30

        private const val MAX_ERROR_CODE_LENGTH = 255

        fun create(
            todo: Todo,
            courseId: Long,
            assignmentId: Long,
            contentHash: String,
            sanitizedContent: String,
            skippedFiles: String,
        ): AssignmentAnalysis {
            require(contentHash.matches(Regex("[a-f0-9]{64}"))) {
                "contentHash must be a sha256 hex string"
            }
            require(sanitizedContent.isNotBlank()) {
                "sanitizedContent must not be blank"
            }
            return AssignmentAnalysis(
                todo = todo,
                courseId = courseId,
                assignmentId = assignmentId,
                contentHash = contentHash,
                sanitizedContent = sanitizedContent,
                skippedFiles = skippedFiles,
            )
        }
    }
}
