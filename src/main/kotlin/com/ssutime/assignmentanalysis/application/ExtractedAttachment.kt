package com.ssutime.assignmentanalysis.application

data class ExtractedAttachment(
    val fileName: String,
    val text: String,
    val skippedReason: String? = null,
)
