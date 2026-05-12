package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.Category
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.port.ClippingQueryPort
import com.ohmyclipping.service.KeywordTrendService
import com.ohmyclipping.service.dto.CategoryOverview
import com.ohmyclipping.service.dto.analytics.KeywordTrendItem
import com.ohmyclipping.service.dto.analytics.KeywordTrendPeriod
import com.ohmyclipping.service.dto.analytics.KeywordTrendResponse
import io.kotest.matchers.string.shouldContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * 사용자 인사이트 MCP 도구 단위 테스트.
 *
 * 선택 파라미터 정규화와 서비스 위임 계약을 검증한다.
 */
class UserInsightToolsTest {

    private val categoryService = mockk<CategoryService>()
    private val clippingService = mockk<ClippingQueryPort>()
    private val keywordTrendService = mockk<KeywordTrendService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tools = UserInsightTools(categoryService, clippingService, keywordTrendService, rateLimiter)

    @Test
    fun `category overview 는 rate limit 확인 후 조회한다`() {
        every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
        every { categoryService.resolveCategory("AI") } returns Category(id = "c1", name = "AI")
        every { clippingService.getCategoryOverview("c1") } returns CategoryOverview(
            id = "c1",
            name = "AI",
            sourceCount = 3,
            subscriberCount = 4,
            recentItemCount7Days = 5,
            avgImportance7Days = 0.7,
            lastUpdatedAt = "2026-05-13T00:00:00Z",
        )

        val json = tools.user_get_category_overview(category = "AI")

        json shouldContain "\"sourceCount\":3"
        verify(exactly = 1) {
            rateLimiter.checkOrThrow("user_get_category_overview", maxRequests = 60, windowSeconds = 3600)
        }
        verify(exactly = 1) { clippingService.getCategoryOverview("c1") }
    }

    @Test
    fun `trending keywords category가 빈 문자열이면 전체 카테고리 조회로 처리한다`() {
        every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
        every {
            keywordTrendService.getKeywordTrend(days = 7, top = 15, categoryId = null)
        } returns KeywordTrendResponse(
            period = KeywordTrendPeriod(from = "2026-04-01", to = "2026-04-07"),
            keywords = listOf(
                KeywordTrendItem(
                    keyword = "AI",
                    dailyCounts = emptyList(),
                    totalCount = 3,
                    changeRate = 0.25,
                ),
            ),
        )

        val json = tools.user_get_trending_keywords(category = "   ", days = 7, limit = 15)

        json shouldContain "\"keyword\":\"AI\""
        verify(exactly = 1) {
            rateLimiter.checkOrThrow("user_get_trending_keywords", maxRequests = 60, windowSeconds = 3600)
        }
        verify(exactly = 0) { categoryService.resolveCategory(any()) }
    }

    @Test
    fun `trending keywords limit 검증 실패 시 rate limit 을 소진하지 않는다`() {
        val json = tools.user_get_trending_keywords(category = null, days = 7, limit = 51)

        json shouldContain "\"error\""
        json shouldContain "-32024"
        verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { keywordTrendService.getKeywordTrend(any(), any(), any()) }
    }

    @Test
    fun `rate limit 초과 시 trend 서비스 미호출`() {
        every {
            rateLimiter.checkOrThrow(
                toolName = "user_get_trending_keywords",
                maxRequests = 60,
                windowSeconds = 3600,
                dimension = null,
                actor = null,
            )
        } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

        val json = tools.user_get_trending_keywords(category = null, days = 7, limit = 15)

        json shouldContain "\"error\""
        json shouldContain "-32022"
        verify(exactly = 0) { keywordTrendService.getKeywordTrend(any(), any(), any()) }
    }
}
