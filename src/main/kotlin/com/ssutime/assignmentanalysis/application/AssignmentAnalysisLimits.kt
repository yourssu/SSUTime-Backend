package com.ssutime.assignmentanalysis.application

object AssignmentAnalysisLimits {
    const val MAX_HTML_CHARS = 100_000
    const val MAX_FILE_COUNT = 5
    const val MAX_SINGLE_FILE_BYTES = 5L * 1024L * 1024L
    const val MAX_TOTAL_DOWNLOAD_BYTES = 15L * 1024L * 1024L
    const val MAX_EXTRACTED_CHARS = 80_000
    const val MAX_ATTACHMENT_CHARS = 20_000
    const val MAX_ZIP_ENTRIES = 100
    const val MAX_ZIP_ENTRY_BYTES = 2L * 1024L * 1024L
    const val MAX_ZIP_EXPANDED_BYTES = 8L * 1024L * 1024L
    const val MAX_REDIRECTS = 5
}
