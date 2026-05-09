package com.ssutime.subject.presentation

data class EnrollmentResponse(
    val enrollmentId: Long,
    val courseId: Long,
    val name: String,
    val semester: String,
)
