package com.ohmyclipping.admin.mcp

import com.ohmyclipping.service.dto.clipping.CollectResult
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.service.port.ClippingPipelinePort
import com.ohmyclipping.service.pipeline.toPipelineCollectResult
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * admin_collect 단위 테스트 — PR-06 sync guardrail 추가 후.
 *
 * 검증 포인트:
 *  - 해피패스: categoryId + hoursBack<=6 이면 ClippingService.collect 호출.
 *  - categoryId 생략 → InvalidInputException (전체 수집 차단).
 *  - hoursBack>6 → InvalidInputException (장기 수집 차단).
 *  - 가드레일 실패 시 rate limiter 및 collect 서비스 모두 호출되지 않는다.
 */
class AdminCollectToolTest {

    private val clippingService = mockk<ClippingPipelinePort>()
    private val rateLimiter = mockk<McpRateLimiter>()
    private val tool = AdminCollectTool(clippingService, rateLimiter)

    private val result = CollectResult(
        totalCollected = 3,
        newItems = 3,
        duplicateSkipped = 0,
        categories = emptyList(),
    )

    @Nested
    inner class `sync guardrail` {

        @Test
        fun `해피패스 — categoryId 와 hoursBack 6 이하면 collect 호출`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { clippingService.collect("c1", 3) } returns result.toPipelineCollectResult()

            val json = tool.admin_collect(categoryId = "c1", hoursBack = 3)

            json shouldContain "\"newItems\":3"
            json shouldNotContain "\"error\""
            verify(exactly = 1) { clippingService.collect("c1", 3) }
        }

        @Test
        fun `categoryId 는 trim 해서 rate limit dimension 과 collect 포트에 전달한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { clippingService.collect("c1", 3) } returns result.toPipelineCollectResult()

            val json = tool.admin_collect(categoryId = " c1 ", hoursBack = 3)

            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                rateLimiter.checkOrThrow(
                    toolName = "admin_collect",
                    maxRequests = 20,
                    windowSeconds = 3600,
                    dimension = "c1",
                )
            }
            verify(exactly = 1) { clippingService.collect("c1", 3) }
        }

        @Test
        fun `categoryId 가 없으면 InvalidInputException 으로 차단된다`() {
            val json = tool.admin_collect(categoryId = null, hoursBack = 1)

            json shouldContain "\"error\""
            json shouldContain "admin_collect_async"
            verify(exactly = 0) { clippingService.collect(any(), any()) }
            verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
        }

        @Test
        fun `hoursBack 7 이상이면 InvalidInputException 으로 차단된다`() {
            val json = tool.admin_collect(categoryId = "c1", hoursBack = 24)

            json shouldContain "\"error\""
            json shouldContain "admin_collect_async"
            verify(exactly = 0) { clippingService.collect(any(), any()) }
        }

        @TestFactory
        fun `hoursBack 0 이하이면 InvalidInputException 으로 차단된다`(): List<DynamicTest> =
            listOf(0, -1, -24).map { invalidHours ->
                DynamicTest.dynamicTest("hoursBack=$invalidHours") {
                    val json = tool.admin_collect(categoryId = "c1", hoursBack = invalidHours)

                    json shouldContain "\"error\""
                    json shouldContain "1~6"
                    verify(exactly = 0) { clippingService.collect(any(), any()) }
                    verify(exactly = 0) { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) }
                }
            }

        @Test
        fun `hoursBack 이 정확히 상한 6 이면 허용된다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { clippingService.collect("c1", 6) } returns result.toPipelineCollectResult()

            val json = tool.admin_collect(categoryId = "c1", hoursBack = 6)

            json shouldNotContain "\"error\""
            verify(exactly = 1) { clippingService.collect("c1", 6) }
        }
    }
}
