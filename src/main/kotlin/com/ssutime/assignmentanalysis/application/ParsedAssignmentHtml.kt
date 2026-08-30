package com.ssutime.assignmentanalysis.application

data class ParsedAssignmentHtml(
    val text: String,
    val fileLinks: List<CanvasFileLink>,
)
