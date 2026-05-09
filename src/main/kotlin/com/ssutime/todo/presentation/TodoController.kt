package com.ssutime.todo.presentation

import com.ssutime.todo.application.TodoService
import com.ssutime.todo.domain.UserTodoStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/todo")
class TodoController(
    private val todoService: TodoService,
) {
    @PostMapping("/report")
    fun report(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: TodoReportRequest,
    ): ResponseEntity<Unit> {
        todoService.processReport(
            userId = userId,
            subjectId = request.subjectId,
            materialCode = request.materialCode,
            type = request.type,
            dueDate = request.dueDate,
            title = request.title,
            thresholdMinutes = request.thresholdMinutes,
        )
        return ResponseEntity.ok().build()
    }

    @GetMapping("/todos")
    fun getTodos(
        @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<List<UserTodoStatus>> =
        ResponseEntity.ok(todoService.getUserTodoStatuses(userId))
}
