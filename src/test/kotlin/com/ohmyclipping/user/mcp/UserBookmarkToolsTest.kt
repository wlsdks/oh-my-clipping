package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.BookmarkedArticle
import com.ohmyclipping.service.UserArticleHistoryService
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * user_toggle_bookmark / user_list_bookmarks 단위 테스트.
 *
 * 검증 포인트:
 *  - 토글 해피패스 — 주입된 userId 와 summaryId 로 서비스 경로 호출 + 새 상태 반환.
 *  - 토글 rate limit 초과 시 JSON-RPC 에러.
 *  - 목록 해피패스 — 반환된 BookmarkedArticle 리스트를 JSON 배열로 직렬화.
 *  - 목록 _onBehalfOfUserId 누락 시 InvalidInputException.
 */
class UserBookmarkToolsTest {

    private val service = mockk<UserArticleHistoryService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = UserBookmarkTools(service, rateLimiter)

    private fun sampleBookmark(id: String) = BookmarkedArticle(
        id = id,
        userId = "u1",
        summaryId = "s-$id",
        originalTitle = "Title $id",
        translatedTitle = "번역 $id",
        summary = "요약 본문",
        insights = null,
        keywords = listOf("AI"),
        importanceScore = 0.8f,
        sourceLink = "https://example.com/$id",
        categoryId = "c1",
        sentiment = null,
        eventType = null,
        articleCreatedAt = Instant.parse("2026-04-10T00:00:00Z"),
        bookmarkedAt = Instant.parse("2026-04-11T00:00:00Z"),
    )

    @Nested
    inner class `user_toggle_bookmark` {

        @Test
        fun `해피패스 — 주입된 userId 로 토글 후 새 상태를 JSON 으로 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.toggleBookmarkByUserId("u1", "s1") } returns true

            val json = tool.user_toggle_bookmark(summaryId = "s1", _onBehalfOfUserId = "u1")

            json shouldContain "\"summaryId\":\"s1\""
            json shouldContain "\"bookmarked\":true"
            json shouldNotContain "\"error\""
            verify(exactly = 1) { service.toggleBookmarkByUserId("u1", "s1") }
        }

        @Test
        fun `rate limit 초과 시 서비스는 호출되지 않는다`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "user_toggle_bookmark",
                    maxRequests = 120,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.user_toggle_bookmark(summaryId = "s1", _onBehalfOfUserId = "u1")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { service.toggleBookmarkByUserId(any(), any()) }
        }
    }

    @Nested
    inner class `user_list_bookmarks` {

        @Test
        fun `해피패스 — BookmarkedArticle 목록을 JSON 배열로 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.listBookmarksByUserId("u1", 0, 20) } returns listOf(sampleBookmark("a"), sampleBookmark("b"))

            val json = tool.user_list_bookmarks(limit = 20, offset = 0, _onBehalfOfUserId = "u1")

            json shouldContain "\"summaryId\":\"s-a\""
            json shouldContain "\"summaryId\":\"s-b\""
            json shouldContain "번역"
        }

        @Test
        fun `_onBehalfOfUserId 누락 시 InvalidInputException 으로 거부된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val json = tool.user_list_bookmarks(limit = 20, offset = 0, _onBehalfOfUserId = null)

            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { service.listBookmarksByUserId(any(), any(), any()) }
        }

        @Test
        fun `limit 범위 밖은 validation error`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val json = tool.user_list_bookmarks(limit = 999, offset = 0, _onBehalfOfUserId = "u1")

            json shouldContain "\"error\""
            json shouldContain "limit must be between"
        }
    }
}
