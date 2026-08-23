package com.ssutime.assignmentanalysis.application

import com.ssutime.assignmentanalysis.domain.AssignmentAnalysis
import com.ssutime.assignmentanalysis.infrastructure.AssignmentAnalysisRepository
import com.ssutime.assignmentanalysis.presentation.AssignmentAnalysisResponse
import com.ssutime.common.exception.ResourceNotFoundException
import com.ssutime.todo.infrastructure.UserTodoStatusRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AssignmentAnalysisQueryService(
    private val assignmentAnalysisRepository: AssignmentAnalysisRepository,
    private val userTodoStatusRepository: UserTodoStatusRepository,
) {
    @Transactional(readOnly = true)
    fun getAnalysisStatus(
        userId: Long,
        analysisId: Long,
    ): AssignmentAnalysisResponse {
        val analysis =
            assignmentAnalysisRepository
                .findById(analysisId)
                .orElseThrow { ResourceNotFoundException("Assignment analysis not found: $analysisId") }

        if (userTodoStatusRepository.findByUserIdAndTodo(userId, analysis.todo) == null) {
            throw ResourceNotFoundException("Assignment analysis not found: $analysisId")
        }

        return analysis.toResponse()
    }

    private fun AssignmentAnalysis.toResponse(): AssignmentAnalysisResponse =
        AssignmentAnalysisResponse(
            analysisId = id,
            status = status,
            skippedFiles = skippedFiles.lines().filter { it.isNotBlank() },
        )
}
