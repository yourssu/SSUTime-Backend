package com.ssutime.subject.presentation

data class EnrollRequest(
    val courseId: Long,
    val name: String,
    val semester: String,
)
