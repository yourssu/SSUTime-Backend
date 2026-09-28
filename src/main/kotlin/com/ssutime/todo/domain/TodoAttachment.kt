package com.ssutime.todo.domain

data class TodoAttachment(
    val url: String,
    val fileName: String,
) {
    val extension: String? = fileName.substringAfterLast('.', "").ifEmpty { null }
}
