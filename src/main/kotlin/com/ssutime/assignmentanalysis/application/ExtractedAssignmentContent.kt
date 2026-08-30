package com.ssutime.assignmentanalysis.application

data class ExtractedAssignmentContent(
    val sanitizedContent: String,
    val skippedFiles: List<String>,
)
