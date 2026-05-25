package com.ssutime.assignmentanalysis.application

import com.ssutime.assignmentanalysis.presentation.LmsSessionRequest

interface LmsCanvasClient {
    fun createSession(lmsSession: LmsSessionRequest): CanvasSession

    fun getFileMetadata(
        session: CanvasSession,
        courseId: Long,
        fileId: Long,
    ): CanvasFileMetadata

    fun downloadFile(
        session: CanvasSession,
        file: CanvasFileMetadata,
    ): ByteArray
}
