package com.ssutime.todo

import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoAttachment

fun Todo.setAttachmentLinksForTest(attachments: List<TodoAttachment>) {
    Todo::class.java
        .getDeclaredField("attachmentLinksText")
        .apply { isAccessible = true }
        .set(this, Todo.joinAttachmentLinks(attachments))
}
