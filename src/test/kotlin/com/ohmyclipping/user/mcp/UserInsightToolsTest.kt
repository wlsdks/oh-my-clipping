package com.ohmyclipping.user.mcp

import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.port.ClippingQueryPort
import com.ohmyclipping.service.KeywordTrendService
import com.ohmyclipping.service.dto.analytics.KeywordTrendItem
import com.ohmyclipping.service.dto.analytics.KeywordTrendPeriod
import com.ohmyclipping.service.dto.analytics.KeywordTrendResponse
import io.kotest.matchers.string.shouldContain
import io.mockk.every
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
    private val tools = UserInsightTools(categoryService, clippingService, keywordTrendService)

    @Test
    fun `trending keywords category가 빈 문자열이면 전체 카테고리 조회로 처리한다`() {
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
        verify(exactly = 0) { categoryService.resolveCategory(any()) }
    }
}
