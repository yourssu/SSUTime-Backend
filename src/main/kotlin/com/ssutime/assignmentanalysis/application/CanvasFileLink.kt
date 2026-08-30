package com.ssutime.assignmentanalysis.application

data class CanvasFileLink(
    val courseId: Long?,
    val fileId: Long,
    val sourceUrl: String,
    val label: String,
)
