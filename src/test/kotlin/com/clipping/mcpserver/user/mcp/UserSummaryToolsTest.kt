package com.clipping.mcpserver.user.mcp

import com.clipping.mcpserver.mcp.dto.DtoSanitizer
import com.clipping.mcpserver.model.Category
import com.clipping.mcpserver.service.dto.clipping.CategoryInfo
import com.clipping.mcpserver.service.dto.clipping.SummaryInfo
import com.clipping.mcpserver.service.dto.clipping.SummaryListResult
import com.clipping.mcpserver.service.CategoryService
import com.clipping.mcpserver.service.port.ClippingQueryPort
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
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
    private val sanitizer = DtoSanitizer()
    private val tools = UserSummaryTools(categoryService, clippingService, sanitizer)

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
            every { categoryService.resolveCategory("AI News") } returns sampleCategory
            every { clippingService.listRecentForCategory("c1", sinceDays = 3, limit = 5) } returns
                SummaryListResult(summaries = listOf(summaryInfo("recent", Instant.now().toString())), totalCount = 1)

            val json = tools.user_list_recent_summaries("AI News", limit = 5, sinceDays = 3)

            json shouldContain "\"id\":\"recent\""
            verify(exactly = 1) { clippingService.listRecentForCategory("c1", sinceDays = 3, limit = 5) }
            
        }

        @Test
        fun `limit이 30을 초과하면 validation error JSON을 반환한다`() {
            val json = tools.user_list_recent_summaries("AI News", limit = 31, sinceDays = 7)
            json shouldContain "\"error\""
            json shouldContain "-32024"
        }

        @Test
        fun `sinceDays가 0이면 validation error JSON을 반환한다`() {
            val json = tools.user_list_recent_summaries("AI News", limit = 10, sinceDays = 0)
            json shouldContain "\"error\""
            json shouldContain "-32024"
        }

        @Test
        fun `category 가 null 이면 cross-category 경로로 위임한다`() {
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
        }

        @Test
        fun `category가 빈 문자열이면 전체 카테고리 검색으로 처리한다`() {
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
        }
    }

    @Nested
    inner class `user_list_top_summaries` {

        @Test
        fun `정상 흐름 - 중요도 필터 적용`() {
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
        }

        @Test
        fun `minScore가 1을 초과하면 validation error`() {
            val json = tools.user_list_top_summaries("AI News", days = 7, minScore = 1.5, limit = 5)
            json shouldContain "-32024"
        }
    }
}
