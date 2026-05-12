package com.ohmyclipping.user.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.Category
import com.ohmyclipping.service.dto.clipping.CategoryInfo
import com.ohmyclipping.service.dto.clipping.SourceInfo
import com.ohmyclipping.service.CategoryService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 사용자 카테고리 MCP 도구 단위 테스트.
 * JSON 문자열 응답을 검증하며, 내부(`__`) 카테고리가 필터링되는지 확인한다.
 */
class UserCategoryToolsTest {

    private val categoryService = mockk<CategoryService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tools = UserCategoryTools(categoryService, rateLimiter)

    @Nested
    inner class `user_list_categories 호출 시` {

        @Test
        fun `공개 카테고리만 JSON 배열로 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.listCategories() } returns listOf(
                CategoryInfo(
                    id = "c1", name = "AI", description = "AI 뉴스",
                    slackChannelId = null, isActive = true, sourceCount = 2,
                    isPublic = true,
                ),
                CategoryInfo(
                    id = "__internal", name = "Internal", description = null,
                    slackChannelId = null, isActive = true, sourceCount = 0,
                    isPublic = true,
                ),
            )

            val json = tools.user_list_categories()

            json shouldContain "\"id\":\"c1\""
            json shouldContain "\"name\":\"AI\""
            json shouldNotContain "__internal"
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("user_list_categories", maxRequests = 60, windowSeconds = 3600)
            }
        }

        @Test
        fun `isPublic이 false인 카테고리는 응답에서 제외된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.listCategories() } returns listOf(
                CategoryInfo(
                    id = "public-cat", name = "Public AI", description = "공개",
                    slackChannelId = null, isActive = true, sourceCount = 1,
                    isPublic = true,
                ),
                CategoryInfo(
                    id = "private-cat", name = "Private Insights", description = "비공개",
                    slackChannelId = null, isActive = true, sourceCount = 3,
                    isPublic = false,
                ),
            )

            val json = tools.user_list_categories()

            json shouldContain "\"id\":\"public-cat\""
            json shouldNotContain "private-cat"
            json shouldNotContain "Private Insights"
        }

        @Test
        fun `서비스 예외는 에러 JSON으로 감싸진다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.listCategories() } throws RuntimeException("boom")

            val json = tools.user_list_categories()

            json shouldContain "\"error\""
            json shouldContain "boom"
        }

        @Test
        fun `rate limit 초과 시 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "user_list_categories",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tools.user_list_categories()

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { categoryService.listCategories() }
        }
    }

    @Nested
    inner class `user_list_sources 호출 시` {

        @Test
        fun `활성 소스만 JSON으로 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            val category = Category(id = "c1", name = "AI News")
            every { categoryService.resolveCategory("AI News") } returns category
            every { categoryService.listSources("c1") } returns listOf(
                SourceInfo(
                    id = "s1", name = "TechCrunch", url = "https://tc.com",
                    emoji = "\uD83D\uDCF0", isActive = true, sourceRegion = "GLOBAL",
                    categoryId = "c1", categoryName = "AI News",
                ),
                SourceInfo(
                    id = "s2", name = "Inactive", url = "https://dead.com",
                    emoji = null, isActive = false, sourceRegion = "GLOBAL",
                    categoryId = "c1", categoryName = "AI News",
                ),
            )

            val json = tools.user_list_sources("AI News")

            json shouldContain "TechCrunch"
            json shouldNotContain "Inactive"
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("user_list_sources", maxRequests = 60, windowSeconds = 3600)
            }
        }

        @Test
        fun `알 수 없는 카테고리는 에러 JSON으로 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.resolveCategory("Unknown") } throws
                com.ohmyclipping.error.NotFoundException("Category not found")

            val json = tools.user_list_sources("Unknown")

            json shouldContain "\"error\""
            json shouldContain "-32002"
        }

        @Test
        fun `빈 카테고리면 빈 JSON 배열을 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            val category = Category(id = "c1", name = "Empty")
            every { categoryService.resolveCategory("Empty") } returns category
            every { categoryService.listSources("c1") } returns emptyList()

            tools.user_list_sources("Empty") shouldBe "[]"
        }

        @Test
        fun `rate limit 초과 시 소스 조회 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "user_list_sources",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tools.user_list_sources("AI News")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { categoryService.resolveCategory(any()) }
            verify(exactly = 0) { categoryService.listSources(any()) }
        }
    }
}
