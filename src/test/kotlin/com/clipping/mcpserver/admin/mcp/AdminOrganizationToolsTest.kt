package com.clipping.mcpserver.admin.mcp

import com.clipping.mcpserver.error.RateLimitExceededException
import com.clipping.mcpserver.mcp.McpRateLimiter
import com.clipping.mcpserver.model.Organization
import com.clipping.mcpserver.model.OrganizationType
import com.clipping.mcpserver.service.OrganizationService
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
 * admin_list_organizations / admin_category_organizations 단위 테스트.
 *
 * 검증 포인트:
 *  - list: 타입 필터가 있으면 enum 으로 변환해 서비스에 전달.
 *  - list: 잘못된 type 은 InvalidInputException.
 *  - category_organizations: 비어있는 categoryId 는 InvalidInputException.
 *  - rate limit 초과 시 두 도구 모두 JSON-RPC 에러.
 */
class AdminOrganizationToolsTest {

    private val service = mockk<OrganizationService>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminOrganizationTools(service, rateLimiter)

    private fun org(id: String, type: OrganizationType = OrganizationType.COMPETITOR) = Organization(
        id = id, name = "Org $id", type = type,
    )

    @Nested
    inner class `admin_list_organizations` {

        @Test
        fun `type 이 없으면 전체 조직을 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.findAll(null) } returns listOf(org("o1"), org("o2"))

            val json = tool.admin_list_organizations(type = null)

            json shouldContain "\"id\":\"o1\""
            json shouldContain "\"id\":\"o2\""
            verify(exactly = 1) { service.findAll(null) }
        }

        @Test
        fun `type COMPETITOR 는 enum 으로 변환되어 전달된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.findAll(OrganizationType.COMPETITOR) } returns listOf(org("o1"))

            val json = tool.admin_list_organizations(type = "competitor")

            json shouldContain "\"id\":\"o1\""
            verify(exactly = 1) { service.findAll(OrganizationType.COMPETITOR) }
        }

        @Test
        fun `잘못된 type 은 validation error 로 거부된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val json = tool.admin_list_organizations(type = "BOGUS")

            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { service.findAll(any()) }
        }

        @Test
        fun `rate limit 초과 시 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_list_organizations",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_list_organizations(type = null)

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { service.findAll(any()) }
        }
    }

    @Nested
    inner class `admin_category_organizations` {

        @Test
        fun `해피패스 — 카테고리에 연결된 조직을 JSON 배열로 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { service.findByCategoryId("c1") } returns listOf(org("o1"), org("o2"))

            val json = tool.admin_category_organizations(categoryId = "c1")

            json shouldContain "\"id\":\"o1\""
            json shouldNotContain "\"error\""
        }

        @Test
        fun `빈 categoryId 는 InvalidInputException 으로 거부된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs

            val json = tool.admin_category_organizations(categoryId = "")

            json shouldContain "\"error\""
            json shouldContain "-32024"
            verify(exactly = 0) { service.findByCategoryId(any()) }
        }

        @Test
        fun `rate limit 초과 시 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_category_organizations",
                    maxRequests = 60,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_category_organizations(categoryId = "c1")

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) { service.findByCategoryId(any()) }
        }
    }
}
