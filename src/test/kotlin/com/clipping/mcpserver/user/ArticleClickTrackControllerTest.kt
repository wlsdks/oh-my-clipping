package com.clipping.mcpserver.user

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.service.UserEventService
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus

class ArticleClickTrackControllerTest {

    private val userEventService = mockk<UserEventService>(relaxed = true)
    private val controller = ArticleClickTrackController(userEventService)

    @Nested
    inner class `클릭 추적 및 리다이렉트` {

        @Test
        fun `유효한 HTTPS URL은 302 리다이렉트를 반환한다`() {
            val response = controller.trackClick(
                summaryId = "summary-1",
                targetUrl = "https://news.example.com/article/123",
                authentication = null
            )

            response.statusCode shouldBe HttpStatus.FOUND
            response.headers["Location"]?.first() shouldBe "https://news.example.com/article/123"
            response.headers["Cache-Control"]?.first() shouldBe "no-cache, no-store"
        }

        @Test
        fun `유효한 HTTP URL도 302 리다이렉트를 반환한다`() {
            val response = controller.trackClick(
                summaryId = "summary-2",
                targetUrl = "http://news.example.com/article/456",
                authentication = null
            )

            response.statusCode shouldBe HttpStatus.FOUND
            response.headers["Location"]?.first() shouldBe "http://news.example.com/article/456"
        }

        @Test
        fun `클릭 이벤트가 UserEventService에 저장된다`() {
            controller.trackClick(
                summaryId = "summary-1",
                targetUrl = "https://news.example.com/article/123",
                authentication = null
            )

            verify(exactly = 1) {
                userEventService.saveClick("anonymous", "summary-1", "https://news.example.com/article/123", null)
            }
        }
    }

    @Nested
    inner class `URL 검증` {

        @Test
        fun `javascript 스킴은 거부된다`() {
            assertThrows<InvalidInputException> {
                controller.trackClick(
                    summaryId = "summary-1",
                    targetUrl = "javascript:alert(1)",
                    authentication = null
                )
            }
        }

        @Test
        fun `data 스킴은 거부된다`() {
            assertThrows<InvalidInputException> {
                controller.trackClick(
                    summaryId = "summary-1",
                    targetUrl = "data:text/html,<h1>hacked</h1>",
                    authentication = null
                )
            }
        }

        @Test
        fun `file 스킴은 거부된다`() {
            assertThrows<InvalidInputException> {
                controller.trackClick(
                    summaryId = "summary-1",
                    targetUrl = "file:///etc/passwd",
                    authentication = null
                )
            }
        }

        @Test
        fun `빈 URL은 거부된다`() {
            assertThrows<InvalidInputException> {
                controller.trackClick(
                    summaryId = "summary-1",
                    targetUrl = "",
                    authentication = null
                )
            }
        }
    }

    @Nested
    inner class `이벤트 저장 실패 시 리다이렉트 보장` {

        @Test
        fun `이벤트 저장이 실패해도 302 리다이렉트를 반환한다`() {
            every {
                userEventService.saveClick(any(), any(), any(), any())
            } throws RuntimeException("DB connection failed")

            // saveClick 내부에서 예외를 삼키므로 여기서는 도달하지 않지만,
            // controller 자체가 예외를 던지지 않는 것을 보장한다.
            // 실제로 saveClick은 내부에서 try-catch하므로 이 테스트는
            // controller가 예외를 전파하지 않는 것을 검증한다.
            val response = controller.trackClick(
                summaryId = "summary-1",
                targetUrl = "https://news.example.com/article/123",
                authentication = null
            )

            response.statusCode shouldBe HttpStatus.FOUND
        }
    }
}
