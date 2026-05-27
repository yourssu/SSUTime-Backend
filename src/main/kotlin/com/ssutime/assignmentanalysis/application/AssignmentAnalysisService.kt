package com.ssutime.assignmentanalysis.application

import com.ssutime.aisummary.infrastructure.AnthropicClient
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysis
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisPrepared
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisStatus
import com.ssutime.assignmentanalysis.infrastructure.AssignmentAnalysisRepository
import com.ssutime.assignmentanalysis.presentation.AssignmentAnalysisPayload
import com.ssutime.assignmentanalysis.presentation.AssignmentAnalysisResponse
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.infrastructure.TodoRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.security.MessageDigest

@Service
class AssignmentAnalysisService(
    private val contentExtractor: AssignmentContentExtractor,
    private val assignmentAnalysisRepository: AssignmentAnalysisRepository,
    private val todoRepository: TodoRepository,
    private val anthropicClient: AnthropicClient,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun prepareAnalysis(
        todo: Todo,
        payload: AssignmentAnalysisPayload,
    ): AssignmentAnalysisResponse {
        val extracted = contentExtractor.extract(payload)
        val contentHash = sha256(extracted.sanitizedContent)
        val analysis =
            assignmentAnalysisRepository.findByTodoAndCourseIdAndAssignmentIdAndContentHash(
                todo = todo,
                courseId = payload.courseId,
                assignmentId = payload.assignmentId,
                contentHash = contentHash,
            ) ?: assignmentAnalysisRepository
                .save(
                    AssignmentAnalysis.create(
                        todo = todo,
                        courseId = payload.courseId,
                        assignmentId = payload.assignmentId,
                        contentHash = contentHash,
                        sanitizedContent = extracted.sanitizedContent,
                        skippedFiles = extracted.skippedFiles.joinToString("\n"),
                    ),
                ).also { saved ->
                    applicationEventPublisher.publishEvent(AssignmentAnalysisPrepared(saved.id))
                }

        return AssignmentAnalysisResponse(
            analysisId = analysis.id,
            status = analysis.status,
            skippedFiles = analysis.skippedFiles.lines().filter { it.isNotBlank() },
        )
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onAssignmentAnalysisPrepared(event: AssignmentAnalysisPrepared) {
        val analysis = assignmentAnalysisRepository.findById(event.analysisId).orElse(null) ?: return
        if (analysis.status !in setOf(AssignmentAnalysisStatus.PENDING, AssignmentAnalysisStatus.FAILED)) {
            return
        }

        runCatching {
            analysis.markAnalyzing()
            assignmentAnalysisRepository.save(analysis)
            val aiAnalysis = anthropicClient.analyzeAssignment(analysis.sanitizedContent)
            if (aiAnalysis.summary.isBlank()) {
                analysis.markFailed("AI_EMPTY_RESPONSE")
            } else {
                analysis.markSucceeded(
                    summary = aiAnalysis.summary,
                    estimatedDurationMinutes = aiAnalysis.estimatedDurationMinutes,
                )
                analysis.todo.updateAssignmentAnalysis(
                    summary = analysis.analysis ?: aiAnalysis.summary,
                    estimatedDurationMinutes = analysis.estimatedDurationMinutes ?: aiAnalysis.estimatedDurationMinutes,
                )
                todoRepository.save(analysis.todo)
            }
        }.onFailure { throwable ->
            analysis.markFailed(throwable.javaClass.simpleName.ifBlank { "AI_ANALYSIS_FAILED" })
        }
        assignmentAnalysisRepository.save(analysis)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
