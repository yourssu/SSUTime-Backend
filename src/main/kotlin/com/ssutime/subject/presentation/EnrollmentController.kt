package com.ssutime.subject.presentation

import com.ssutime.subject.application.EnrollmentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Enrollments", description = "인증된 사용자의 수강 과목 등록 API")
class EnrollmentController(
    private val enrollmentService: EnrollmentService,
) {
    @GetMapping
    @Operation(
        summary = "수강 과목 목록 조회",
        description = "인증된 사용자가 등록한 모든 수강 과목을 조회합니다.",
    )
    fun getEnrollments(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<List<EnrollmentResponse>> = ResponseEntity.ok(enrollmentService.getEnrollments(userId))

    @PostMapping
    @Operation(
        summary = "수강 과목 등록",
        description = "인증된 사용자에게 수강 과목을 등록합니다. 중복 과목 처리는 서비스 계층에서 수행합니다.",
    )
    fun enroll(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: EnrollRequest,
    ): ResponseEntity<Unit> {
        enrollmentService.enroll(userId, request.courseId, request.name, request.semester)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "수강 과목 삭제",
        description = "인증된 사용자가 소유한 수강 과목 등록 정보를 삭제합니다.",
    )
    fun unenroll(
        @Parameter(hidden = true)
        @AuthenticationPrincipal userId: Long,
        @Parameter(description = "삭제할 수강 과목 등록 ID입니다.", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<Unit> {
        enrollmentService.unenroll(userId, id)
        return ResponseEntity.noContent().build()
    }
}
