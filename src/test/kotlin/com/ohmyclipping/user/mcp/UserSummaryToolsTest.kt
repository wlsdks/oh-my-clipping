package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.mcp.dto.DtoSanitizer
import com.ohmyclipping.model.Category
import com.ohmyclipping.service.dto.clipping.CategoryInfo
import com.ohmyclipping.service.dto.clipping.SummaryInfo
import com.ohmyclipping.service.dto.clipping.SummaryListResult
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.port.ClippingQueryPort
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
 * 사용자 요약 MCP 도구 단위 테스트.
 * sinceDays 필터, limit 검증, 에러 전파 경로를 검증한다.
 */
class UserSummaryToolsTest {

    private val categoryService = mockk<CategoryService>()
    private val clippingService = mockk<ClippingQueryPort>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val sanitizer = DtoSanitizer()
    private val tools = UserSummaryTools(categoryService, clippingService, rateLimiter, sanitizer)

    private val sampleCategory = Category(id = "c1", name = "AI News")

    private fun summaryInfo(id: String, createdAt: String) = SummaryInfo(
        id = id, originalTitle = "Title $id", translatedTitle = "번역 $id",
        summary = "요약 $id", keywords = listOf("AI"), importanceScore = 0.8f,
        sourceLink = "https://example.com/$id", isSentToSlack = false,
        categoryId = "c1", createdAt = createdAt,
    )

    @Nested
    inner class `user_list_recent_summaries` {

        @Test
        fun `sinceDays 범위를 벗어난 항목은 제외한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.resolveCategory("AI News") } returns sampleCategory
            val fresh = summaryInfo("s1", Instant.now().toString())
            every { clippingService.listRecentForCategory("c1", sinceDays = 7, limit = 10) } returns
                SummaryListResult(summaries = listOf(fresh), totalCount = 1)

            val json = tools.user_list_recent_summaries("AI News", limit = 10, sinceDays = 7)

            json shouldContain "\"id\":\"s1\""
            json shouldNotContain "\"id\":\"s2\""
        }

        @Test
        fun `category가 있으면 중요도순 getSummaries가 아니라 최신순 전용 서비스를 호출한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.resolveCategory("AI News") } returns sampleCategory
            every { clippingService.listRecentForCategory("c1", sinceDays = 3, limit = 5) } returns
                SummaryListResult(summaries = listOf(summaryInfo("recent", Instant.now().toString())), totalCount = 1)

            val json = tools.user_list_recent_summaries("AI News", limit = 5, sinceDays = 3)

            json shouldContain "\"id\":\"recent\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("user_list_recent_summaries", maxRequests = 60, windowSeconds = 3600)
            }
            verify(exactly = 1) { clippingService.listRecentForCategory("c1", sinceDays = 3, limit = 5) }
            
        }

        @Test
        fun `limit이 30을 초과하면 validation error JSON을 반환한다`() {
            val json = tools.user_list_recent_summaries("AI News", limit = 31, sinceDays = 7)
            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `sinceDays가 0이면 validation error JSON을 반환한다`() {
            val json = tools.user_list_recent_summaries("AI News", limit = 10, sinceDays = 0)
            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `category 가 null 이면 cross-category 경로로 위임한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            // 전체 카테고리 최근순 조회 — resolveCategory 는 호출되지 않아야 한다.
            val crossSummary = summaryInfo("sx", Instant.now().toString())
            every { clippingService.listRecentAcrossCategories(sinceDays = 1, limit = 10) } returns
                SummaryListResult(summaries = listOf(crossSummary), totalCount = 1)
            every { categoryService.listCategories() } returns listOf(
                CategoryInfo(
                    id = "c1", name = "AI News", description = null,
                    slackChannelId = null, isActive = true, sourceCount = 0,
                ),
            )

            val json = tools.user_list_recent_summaries(category = null, limit = 10, sinceDays = 1)

            json shouldContain "\"id\":\"sx\""
            json shouldContain "\"categoryName\":\"AI News\""
        }

        @Test
        fun `category 가 빈 문자열이어도 cross-category 로 처리한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            val crossSummary = summaryInfo("sy", Instant.now().toString())
            every { clippingService.listRecentAcrossCategories(sinceDays = 1, limit = 5) } returns
                SummaryListResult(summaries = listOf(crossSummary), totalCount = 1)
            every { categoryService.listCategories() } returns emptyList()

            val json = tools.user_list_recent_summaries(category = "   ", limit = 5, sinceDays = 1)

            json shouldContain "\"id\":\"sy\""
        }
    }

    @Nested
    inner class `user_search_summaries` {

        @Test
        fun `카테고리 없이 검색하면 null categoryId로 위임한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.listCategories() } returns listOf(
                CategoryInfo(
                    id = "c1", name = "AI News", description = null,
                    slackChannelId = null, isActive = true, sourceCount = 0,
                ),
            )
            every {
                clippingService.searchSummaries(
                    categoryId = null,
                    query = "AI",
                    fromDate = null,
                    toDate = null,
                    limit = 10,
                )
            } returns SummaryListResult(
                summaries = listOf(summaryInfo("s3", Instant.now().toString())),
                totalCount = 1,
            )

            val json = tools.user_search_summaries(
                query = "AI",
                category = null,
                fromDate = null,
                toDate = null,
                limit = 10,
            )

            json shouldContain "\"id\":\"s3\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("user_search_summaries", maxRequests = 60, windowSeconds = 3600)
            }
        }

        @Test
        fun `category가 빈 문자열이면 전체 카테고리 검색으로 처리한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.listCategories() } returns emptyList()
            every {
                clippingService.searchSummaries(
                    categoryId = null,
                    query = "AI",
                    fromDate = null,
                    toDate = null,
                    limit = 10,
                )
            } returns SummaryListResult(
                summaries = listOf(summaryInfo("blank-category", Instant.now().toString())),
                totalCount = 1,
            )

            val json = tools.user_search_summaries(
                query = "AI",
                category = "   ",
                fromDate = null,
                toDate = null,
                limit = 10,
            )

            json shouldContain "\"id\":\"blank-category\""
            verify(exactly = 0) { categoryService.resolveCategory(any()) }
        }

        @Test
        fun `잘못된 날짜 포맷이면 validation error JSON을 반환한다`() {
            val json = tools.user_search_summaries(
                query = "AI",
                category = null,
                fromDate = "2026/04/01",
                toDate = null,
                limit = 10,
            )
            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `빈 query 는 rate limit 차감 없이 validation error 로 거부된다`() {
            val json = tools.user_search_summaries(
                query = "   ",
                category = null,
                fromDate = null,
                toDate = null,
                limit = 10,
            )

            json shouldContain "\"error\""
            json shouldContain "query must not be blank"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { clippingService.searchSummaries(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `query 는 trim 후 검색 서비스로 전달된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.listCategories() } returns emptyList()
            every {
                clippingService.searchSummaries(
                    categoryId = null,
                    query = "AI",
                    fromDate = null,
                    toDate = null,
                    limit = 10,
                )
            } returns SummaryListResult(
                summaries = listOf(summaryInfo("trimmed-query", Instant.now().toString())),
                totalCount = 1,
            )

            val json = tools.user_search_summaries(
                query = "  AI  ",
                category = null,
                fromDate = null,
                toDate = null,
                limit = 10,
            )

            json shouldContain "\"id\":\"trimmed-query\""
            verify(exactly = 1) {
                clippingService.searchSummaries(
                    categoryId = null,
                    query = "AI",
                    fromDate = null,
                    toDate = null,
                    limit = 10,
                )
            }
        }

        @Test
        fun `rate limit 초과 시 검색 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "user_search_summaries",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tools.user_search_summaries(
                query = "AI",
                category = null,
                fromDate = null,
                toDate = null,
                limit = 10,
            )

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { clippingService.searchSummaries(any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `user_list_top_summaries` {

        @Test
        fun `정상 흐름 - 중요도 필터 적용`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.resolveCategory("AI News") } returns sampleCategory
            every {
                clippingService.listTopSummaries(
                    categoryId = "c1",
                    days = 7,
                    minScore = 0.7,
                    limit = 5,
                )
            } returns SummaryListResult(
                summaries = listOf(summaryInfo("s4", Instant.now().toString())),
                totalCount = 1,
            )

            val json = tools.user_list_top_summaries("AI News", days = 7, minScore = 0.7, limit = 5)
            json shouldContain "\"id\":\"s4\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("user_list_top_summaries", maxRequests = 60, windowSeconds = 3600)
            }
        }

        @Test
        fun `minScore가 1을 초과하면 validation error`() {
            val json = tools.user_list_top_summaries("AI News", days = 7, minScore = 1.5, limit = 5)
            json shouldContain "-32024"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `빈 category 는 rate limit 차감 없이 validation error 로 거부된다`() {
            val json = tools.user_list_top_summaries(" ", days = 7, minScore = 0.7, limit = 5)

            json shouldContain "\"error\""
            json shouldContain "category must not be blank"
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { categoryService.resolveCategory(any()) }
            verify(exactly = 0) { clippingService.listTopSummaries(any(), any(), any(), any()) }
        }
    }
}
