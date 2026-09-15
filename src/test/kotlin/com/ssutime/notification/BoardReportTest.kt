package com.ssutime.notification

import com.ninjasquad.springmockk.MockkBean
import com.ssutime.auth.domain.User
import com.ssutime.auth.domain.UserDevice
import com.ssutime.auth.infrastructure.JwtTokenProvider
import com.ssutime.auth.infrastructure.UserDeviceRepository
import com.ssutime.auth.infrastructure.UserRepository
import com.ssutime.common.exception.InvalidRequestException
import com.ssutime.notification.application.NotificationService
import com.ssutime.notification.domain.Board
import com.ssutime.notification.domain.BoardReport
import com.ssutime.notification.domain.ReportedBoard
import com.ssutime.notification.infrastructure.BoardRepository
import com.ssutime.notification.infrastructure.FcmClient
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@AutoConfigureMockMvc
class BoardReportTest {
    @Autowired private lateinit var service: NotificationService

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var devices: UserDeviceRepository

    @Autowired private lateinit var boards: BoardRepository

    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var jwt: JwtTokenProvider

    @Autowired private lateinit var transactionTemplate: TransactionTemplate

    @MockkBean(relaxed = true)
    private lateinit var fcm: FcmClient

    private lateinit var user: User
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        clearMocks(fcm)
        boards.deleteAll()
        user = users.save(User.create(UUID.randomUUID().toString(), "20****01"))
        token = "device-${user.id}"
        devices.save(UserDevice.create(user, token))
    }

    @Test
    fun `only first global receipt of post or reply writable boards sends to requesting device`() {
        devices.save(UserDevice.create(user, "other-device-${user.id}"))
        val items = listOf(board(1, post = listOf(1)), board(2, reply = listOf(1)), board(3))
        service.reportBoards(user.id, BoardReport(token, items + items.first()))
        service.reportBoards(user.id, BoardReport(token, items.map { it.copy(totalPostCount = 20) }))
        service.reportBoards(user.id, BoardReport(token, listOf(board(3, post = listOf(1)))))

        verify(timeout = 5000, exactly = 1) { fcm.sendSilentPush(token, mapOf("title" to "Board 1", "type" to "newBoard")) }
        verify(timeout = 5000, exactly = 1) { fcm.sendSilentPush(token, mapOf("title" to "Board 2", "type" to "newBoard")) }
        verify(exactly = 2) { fcm.sendSilentPush(any(), any()) }
        assertEquals(3, boards.findAllByBoardIdIn(listOf(1, 2, 3)).size)
    }

    @Test
    fun `same board is recorded globally and does not notify a second user`() {
        val other = users.save(User.create(UUID.randomUUID().toString(), "20****02"))
        devices.save(UserDevice.create(other, "second-token"))
        service.reportBoards(user.id, BoardReport(token, listOf(board(1, post = listOf(1)))))
        service.reportBoards(other.id, BoardReport("second-token", listOf(board(1, post = listOf(1)))))
        verify(timeout = 5000, exactly = 1) { fcm.sendSilentPush(token, any()) }
        verify(exactly = 0) { fcm.sendSilentPush("second-token", any()) }
        assertEquals(1, boards.findAllByBoardIdIn(listOf(1)).size)
    }

    @Test
    fun `existing board id stays silent even when writable permissions change`() {
        boards.save(Board(boardId = 1))
        service.reportBoards(user.id, BoardReport(token, listOf(board(1, post = listOf(1)))))
        service.reportBoards(user.id, BoardReport(token, listOf(board(2))))
        service.reportBoards(user.id, BoardReport(token, listOf(board(2, reply = listOf(1)))))

        assertEquals(2, boards.findAllByBoardIdIn(listOf(1, 2)).size)
        verify(exactly = 0) { fcm.sendSilentPush(any(), any()) }
    }

    @Test
    fun `unregistered token does not consume first receipt`() {
        assertThrows<InvalidRequestException> {
            service.reportBoards(user.id, BoardReport("unknown-token", listOf(board(1, post = listOf(1)))))
        }
        assertEquals(0, boards.findAllByBoardIdIn(listOf(1)).size)
        verify(exactly = 0) { fcm.sendSilentPush(any(), any()) }
    }

    @Test
    fun `invalid reports including duplicate entries are rejected before recording receipts`() {
        val valid = board(1, post = listOf(1))
        val invalidBoards =
            listOf(
                valid.copy(id = 0),
                valid.copy(title = " "),
                valid.copy(totalPostCount = -1),
            )
        invalidBoards.forEach { invalid ->
            val exception =
                assertThrows<InvalidRequestException> {
                    service.reportBoards(user.id, BoardReport(token, listOf(valid, invalid)))
                }
            assertEquals("게시판 ID, 제목, 게시글 수를 확인해주세요", exception.message)
        }
        val exception =
            assertThrows<InvalidRequestException> {
                service.reportBoards(user.id, BoardReport(" ", listOf(valid)))
            }
        assertEquals("fcmToken은 필수입니다", exception.message)
        assertEquals(0, boards.findAllByBoardIdIn(listOf(1)).size)
        verify(exactly = 0) { fcm.sendSilentPush(any(), any()) }
    }

    @Test
    fun `empty report still requires a registered device`() {
        service.reportBoards(user.id, BoardReport(token, emptyList()))
        assertThrows<InvalidRequestException> {
            service.reportBoards(user.id, BoardReport("unknown-token", emptyList()))
        }
        verify(exactly = 0) { fcm.sendSilentPush(any(), any()) }
    }

    @Test
    fun `committed report sends asynchronously outside the reporting transaction`() {
        val reportingThread = Thread.currentThread()
        val sent = CompletableFuture<Pair<Thread, Boolean>>()
        every { fcm.sendSilentPush(token, any()) } answers {
            sent.complete(Thread.currentThread() to TransactionSynchronizationManager.isActualTransactionActive())
            Unit
        }

        transactionTemplate.executeWithoutResult {
            service.reportBoards(user.id, BoardReport(token, listOf(board(1, post = listOf(1)))))
            verify(exactly = 0) { fcm.sendSilentPush(any(), any()) }
        }

        val (sendingThread, transactionActive) = sent.get(5, TimeUnit.SECONDS)
        assertNotEquals(reportingThread, sendingThread)
        assertFalse(transactionActive)
    }

    @Test
    fun `rollback does not send or retain receipt`() {
        transactionTemplate.executeWithoutResult { status ->
            service.reportBoards(user.id, BoardReport(token, listOf(board(1, post = listOf(1)))))
            status.setRollbackOnly()
        }
        assertEquals(0, boards.findAllByBoardIdIn(listOf(1)).size)
        verify(exactly = 0) { fcm.sendSilentPush(any(), any()) }
    }

    @Test
    fun `concurrent reports from different users store and send once`() {
        val other = users.save(User.create(UUID.randomUUID().toString(), "20****02"))
        val otherToken = "concurrent-${other.id}"
        devices.save(UserDevice.create(other, otherToken))
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                listOf(user.id to token, other.id to otherToken).map { (reporterId, target) ->
                    executor.submit {
                        start.await()
                        service.reportBoards(reporterId, BoardReport(target, listOf(board(1, post = listOf(1)))))
                    }
                }
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            verify(timeout = 5000, exactly = 1) { fcm.sendSilentPush(any(), mapOf("title" to "Board 1", "type" to "newBoard")) }
            assertEquals(1, boards.findAllByBoardIdIn(listOf(1)).size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `http accepts LMS snake case fields and requires authentication`() {
        val body =
            """
            {"fcmToken":"$token","boards":[{
              "id":40607,"title":"Q&A 게시판","post_writable_user_types":[6,5,1],
              "reply_writable_user_types":[6,5],"comment_writable_user_types":[1],
              "use_secret_post":true,"is_public":true,"position":1,"total_post_count":1,
              "slug":"qna"
            }]}
            """.trimIndent()
        mvc
            .post("/boards/report") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { is4xxClientError() } }
        mvc
            .post("/boards/report") {
                header("Authorization", "Bearer ${jwt.generateToken(user.id)}")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
        verify(timeout = 5000, exactly = 1) { fcm.sendSilentPush(token, mapOf("title" to "Q&A 게시판", "type" to "newBoard")) }
    }

    private fun board(
        id: Long,
        post: List<Int> = listOf(6, 5),
        reply: List<Int> = listOf(6, 5),
    ) = ReportedBoard(id, "Board $id", post, reply, 0)
}
