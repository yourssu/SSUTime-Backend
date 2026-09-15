package com.ssutime.todo

import com.fasterxml.jackson.databind.ObjectMapper
import com.ssutime.todo.domain.Todo
import com.ssutime.todo.domain.TodoAttachment
import com.ssutime.todo.domain.TodoType
import com.ssutime.todo.domain.UserTodoStatus
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoAttachmentLinksTest {
    private val objectMapper: ObjectMapper = Jackson2ObjectMapperBuilder.json().build()
    private val todo = Todo.create(10L, 20L, TodoType.ASSIGNMENT, LocalDateTime.of(2026, 9, 20, 23, 59), "실습과제 3")
    private val attachments =
        listOf(
            TodoAttachment(url = "https://canvas.ssu.ac.kr/courses/44383/files/1/download", fileName = "실습자료.pptx"),
            TodoAttachment(url = "https://canvas.ssu.ac.kr/courses/44383/files/2/download", fileName = "guide"),
        )

    @Test
    fun `attachmentLinks is empty by default`() {
        assertEquals(emptyList(), todo.attachmentLinks)
    }

    @Test
    fun `attachmentLinks round-trips through joined storage text`() {
        todo.setAttachmentLinksForTest(attachments)

        assertEquals(attachments, todo.attachmentLinks)
    }

    @Test
    fun `attachment extension is derived from fileName, null when there is no extension`() {
        assertEquals("pptx", attachments[0].extension)
        assertNull(attachments[1].extension)
    }

    @Test
    fun `joinAttachmentLinks stores empty list as null`() {
        assertNull(Todo.joinAttachmentLinks(emptyList()))
    }

    @Test
    fun `joinAttachmentLinks rejects multi-line or blank url or fileName`() {
        assertFailsWith<IllegalArgumentException> {
            Todo.joinAttachmentLinks(listOf(TodoAttachment("https://canvas.ssu.ac.kr/a\nhttps://canvas.ssu.ac.kr/b", "f.pdf")))
        }
        assertFailsWith<IllegalArgumentException> {
            Todo.joinAttachmentLinks(listOf(TodoAttachment("https://canvas.ssu.ac.kr/a", " ")))
        }
    }

    @Test
    fun `todo list item serializes attachmentLinks as array of url, fileName, extension`() {
        todo.setAttachmentLinksForTest(attachments)

        val json = objectMapper.readTree(objectMapper.writeValueAsString(UserTodoStatus.create(1L, todo, 60)))
        val linksJson = json["todo"]["attachmentLinks"]

        assertTrue(linksJson.isArray)
        assertEquals("https://canvas.ssu.ac.kr/courses/44383/files/1/download", linksJson[0]["url"].asText())
        assertEquals("실습자료.pptx", linksJson[0]["fileName"].asText())
        assertEquals("pptx", linksJson[0]["extension"].asText())
        assertTrue(linksJson[1]["extension"].isNull)
        assertFalse(json["todo"].has("attachmentLinksText"))
    }

    @Test
    fun `todo without attachments serializes empty attachmentLinks array`() {
        val json = objectMapper.readTree(objectMapper.writeValueAsString(todo))

        assertTrue(json["attachmentLinks"].isArray)
        assertEquals(0, json["attachmentLinks"].size())
    }
}
