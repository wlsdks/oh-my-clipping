package com.ohmyclipping.user

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.service.UserEventService
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication

class ArticleClickTrackControllerSourceTaggingTest {

    private val userEventService = mockk<UserEventService>()
    private val controller = ArticleClickTrackController(userEventService)

    @Nested
    @DisplayName("신규 /click/slack/{sid} 경로")
    inner class SlackPathRoute {

        @Test
        fun `slack 경로로 들어온 클릭은 source=slack 으로 저장`() {
            every { userEventService.saveClick(any(), any(), any(), any()) } just Runs

            val response = controller.trackClickFromSlack(
                summaryId = "s1",
                targetUrl = "https://example.com/a",
                authentication = null
            )

            response.statusCode shouldBe HttpStatus.FOUND
            response.headers["Location"]?.single() shouldBe "https://example.com/a"
            verify(exactly = 1) {
                userEventService.saveClick("anonymous", "s1", "https://example.com/a", "slack")
            }
        }

        @Test
        fun `인증된 사용자면 userId 반영`() {
            val auth = mockk<Authentication> { every { name } returns "user-42" }
            every { userEventService.saveClick(any(), any(), any(), any()) } just Runs

            controller.trackClickFromSlack(
                summaryId = "s1",
                targetUrl = "https://example.com/a",
                authentication = auth
            )

            verify(exactly = 1) {
                userEventService.saveClick("user-42", "s1", "https://example.com/a", "slack")
            }
        }

        @Test
        fun `잘못된 URL 스킴은 예외`() {
            assertThrows<InvalidInputException> {
                controller.trackClickFromSlack(
                    summaryId = "s1",
                    targetUrl = "javascript:alert(1)",
                    authentication = null
                )
            }
        }

        @Test
        fun `저장 실패해도 302 리다이렉트 보장`() {
            every { userEventService.saveClick(any(), any(), any(), any()) } throws RuntimeException("DB down")

            val response = controller.trackClickFromSlack(
                summaryId = "s1",
                targetUrl = "https://example.com/a",
                authentication = null
            )

            response.statusCode shouldBe HttpStatus.FOUND
        }
    }

    @Nested
    @DisplayName("기존 /click 경로 backward compat")
    inner class LegacyRoute {

        @Test
        fun `쿼리파라미터 경로는 source 없이 저장 (null 전달)`() {
            every { userEventService.saveClick(any(), any(), any(), any()) } just Runs

            controller.trackClick(
                summaryId = "s1",
                targetUrl = "https://example.com/a",
                authentication = null
            )

            verify(exactly = 1) {
                userEventService.saveClick("anonymous", "s1", "https://example.com/a", null)
            }
        }
    }
}
