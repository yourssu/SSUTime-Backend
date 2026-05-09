package com.ssutime.subject.presentation

import com.ssutime.subject.application.EnrollmentService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/enrollments")
class EnrollmentController(
    private val enrollmentService: EnrollmentService,
) {
    @GetMapping
    fun getEnrollments(
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<List<EnrollmentResponse>> =
        ResponseEntity.ok(enrollmentService.getEnrollments(userId))

    @PostMapping
    fun enroll(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: EnrollRequest,
    ): ResponseEntity<Unit> {
        enrollmentService.enroll(userId, request.courseId, request.name, request.semester)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{id}")
    fun unenroll(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Unit> {
        enrollmentService.unenroll(userId, id)
        return ResponseEntity.noContent().build()
    }
}
