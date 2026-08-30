package com.ssutime.todo.infrastructure

import com.ssutime.todo.domain.Todo
import org.springframework.data.jpa.repository.JpaRepository

interface TodoRepository : JpaRepository<Todo, Long> {
    fun findBySubjectIdAndMaterialCode(
        subjectId: Long,
        materialCode: Long,
    ): Todo?
}
