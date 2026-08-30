package com.ssutime.assignmentanalysis.presentation

import com.ssutime.assignmentanalysis.domain.AssignmentAnalysisStatus

data class AssignmentAnalysisResponse(
    val analysisId: Long,
    val status: AssignmentAnalysisStatus,
    val skippedFiles: List<String>,
)
