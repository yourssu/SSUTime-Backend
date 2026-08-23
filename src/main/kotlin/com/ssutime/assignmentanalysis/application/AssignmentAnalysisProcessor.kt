package com.ssutime.assignmentanalysis.application

import com.ssutime.aisummary.infrastructure.OpenAIClient
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisPrepared
import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisStatus
import com.ssutime.assignmentanalysis.infrastructure.AssignmentAnalysisRepository
import com.ssutime.todo.infrastructure.TodoRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AssignmentAnalysisProcessor(
    private val assignmentAnalysisRepository: AssignmentAnalysisRepository,
    private val todoRepository: TodoRepository,
    private val openAIClient: OpenAIClient,
) {
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
            val aiAnalysis = openAIClient.analyzeAssignment(analysis.sanitizedContent)
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
}
