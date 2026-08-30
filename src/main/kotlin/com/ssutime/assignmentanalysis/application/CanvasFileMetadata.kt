package com.ssutime.assignmentanalysis.application

data class CanvasFileMetadata(
    val fileId: Long,
    val displayName: String,
    val contentType: String,
    val size: Long,
    val downloadUrl: String,
)
