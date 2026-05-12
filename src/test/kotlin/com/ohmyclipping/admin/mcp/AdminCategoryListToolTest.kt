package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.dto.clipping.CategoryInfo
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminCategoryListToolTest {

    private val categoryService = mockk<CategoryService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminCategoryListTool(categoryService, rateLimiter)

    @Nested
    inner class `admin_list_categories` {

        @Test
        fun `전체 카테고리를 조회하기 전에 rate limit 을 확인한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { categoryService.listCategories() } returns listOf(
                CategoryInfo(
                    id = "c1",
                    name = "AI",
                    description = null,
                    slackChannelId = null,
                    isActive = true,
                    sourceCount = 2,
                ),
            )

            val json = tool.admin_list_categories()

            json shouldContain "\"id\":\"c1\""
            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow("admin_list_categories", maxRequests = 60, windowSeconds = 3600)
            }
            verify(exactly = 1) { categoryService.listCategories() }
        }

        @Test
        fun `rate limit 초과 시 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_list_categories",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_list_categories()

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { categoryService.listCategories() }
        }
    }
}
