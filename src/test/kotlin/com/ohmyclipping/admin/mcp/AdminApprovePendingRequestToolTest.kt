package com.ohmyclipping.admin.mcp

import com.ohmyclipping.error.RateLimitExceededException
import com.ohmyclipping.mcp.McpRateLimiter
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.UserClippingRequest
import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.service.CategoryService
import com.ohmyclipping.service.UserClippingRequestService
import com.ohmyclipping.service.dto.ApproveClippingRequestCommand
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * admin_approve_pending_request 단위 테스트.
 *
 * 검증 포인트:
 *  - 해피패스: echo 일치 + PENDING 요청 → approveRequest 위임, 응답 JSON 에 requestId 포함.
 *  - echo 불일치: InvalidInputException 으로 거부하고 서비스 미호출.
 *  - 상태 불일치(PENDING 아님): InvalidInputException.
 *  - rate limit 초과: 서비스 미호출.
 */
class AdminApprovePendingRequestToolTest {

    private val userClippingRequestService = mockk<UserClippingRequestService>()
    private val categoryService = mockk<CategoryService>()
    private val rateLimiter = mockk<McpRateLimiter>()

    private val tool = AdminApprovePendingRequestTool(
        userClippingRequestService = userClippingRequestService,
        categoryService = categoryService,
        rateLimiter = rateLimiter,
        reviewerUsername = "mcp-service",
    )

    private val pendingRequest = UserClippingRequest(
        id = "req-1",
        requesterUserId = "user-1",
        requestName = "AWS Tech News",
        sourceName = "AWS Blog",
        sourceUrl = "https://aws.amazon.com/blogs/aws/feed/",
        slackChannelId = "C1",
        personaName = "Cloud Engineer",
        personaPrompt = "prompt",
        status = UserClippingRequestStatus.PENDING,
    )

    private val approvedCategory = Category(id = "cat-1", name = "Cloud Tech")

    private val approvedResult = pendingRequest.copy(
        status = UserClippingRequestStatus.APPROVED,
        reviewedAt = Instant.parse("2026-04-18T00:00:00Z"),
        approvedCategoryId = "cat-1",
    )

    @Nested
    inner class `admin_approve_pending_request` {

        @Test
        fun `해피패스 — echo 일치하면 approveRequest 에 위임하고 결과 JSON 을 반환한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { userClippingRequestService.findRequestById("req-1") } returns pendingRequest
            every { userClippingRequestService.findRequesterUsername("user-1") } returns "alice"
            every { categoryService.findById(any()) } returns null
            val commandSlot = slot<ApproveClippingRequestCommand>()
            every {
                userClippingRequestService.approveRequest(
                    requestId = "req-1",
                    reviewerUsername = "mcp-service",
                    command = capture(commandSlot),
                )
            } returns approvedResult

            val json = tool.admin_approve_pending_request(
                requestId = "req-1",
                confirmationSummary = "AWS Tech News → AWS Blog (사용자: alice)",
                approveNote = null,
            )

            json shouldContain "\"id\":\"req-1\""
            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                userClippingRequestService.approveRequest("req-1", "mcp-service", any())
            }
            // QUOTATION_ONLY 로 고정된 안전 기본값이 들어가야 한다.
            assert(commandSlot.captured.legalBasis == "QUOTATION_ONLY")
            assert(!commandSlot.captured.fulltextAllowed)
            assert(commandSlot.captured.summaryAllowed)
        }

        @Test
        fun `echo 문구 불일치면 InvalidInputException 으로 서비스 미호출`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every { userClippingRequestService.findRequestById("req-1") } returns pendingRequest
            every { userClippingRequestService.findRequesterUsername("user-1") } returns "alice"
            every { categoryService.findById(any()) } returns null

            val json = tool.admin_approve_pending_request(
                requestId = "req-1",
                confirmationSummary = "Something else → Wrong (사용자: bob)",
                approveNote = null,
            )

            json shouldContain "\"error\""
            json shouldContain "확인 요약 불일치"
            verify(exactly = 0) {
                userClippingRequestService.approveRequest(any(), any(), any())
            }
        }

        @Test
        fun `PENDING 이 아닌 요청은 InvalidInputException`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            every {
                userClippingRequestService.findRequestById("req-1")
            } returns pendingRequest.copy(status = UserClippingRequestStatus.APPROVED)

            val json = tool.admin_approve_pending_request(
                requestId = "req-1",
                confirmationSummary = "AWS Tech News → AWS Blog (사용자: alice)",
                approveNote = null,
            )

            json shouldContain "\"error\""
            json shouldContain "PENDING"
            verify(exactly = 0) {
                userClippingRequestService.approveRequest(any(), any(), any())
            }
        }

        @Test
        fun `rate limit 초과 시 서비스 미호출`() {
            every {
                rateLimiter.checkOrThrow(
                    toolName = "admin_approve_pending_request",
                    maxRequests = 30,
                    windowSeconds = 3600,
                    dimension = null,
                    actor = null,
                )
            } throws RateLimitExceededException("Too many", retryAfterSeconds = 3600)

            val json = tool.admin_approve_pending_request(
                requestId = "req-1",
                confirmationSummary = "AWS Tech News → AWS Blog (사용자: alice)",
                approveNote = null,
            )

            json shouldContain "\"error\""
            json shouldContain "-32022"
            verify(exactly = 0) {
                userClippingRequestService.approveRequest(any(), any(), any())
            }
        }

        @Test
        fun `이미 카테고리가 매핑된 요청이면 categoryName 으로도 echo 가 일치한다`() {
            every { rateLimiter.checkOrThrow(any(), any(), any(), any(), any()) } just Runs
            val withCategory = pendingRequest.copy(approvedCategoryId = "cat-1")
            every { userClippingRequestService.findRequestById("req-1") } returns withCategory
            every { userClippingRequestService.findRequesterUsername("user-1") } returns "alice"
            every { categoryService.findById("cat-1") } returns approvedCategory
            every {
                userClippingRequestService.approveRequest("req-1", "mcp-service", any())
            } returns approvedResult

            val json = tool.admin_approve_pending_request(
                requestId = "req-1",
                // categoryName 을 echo 에 사용했을 때도 통과해야 한다.
                confirmationSummary = "AWS Tech News → Cloud Tech (사용자: alice)",
                approveNote = null,
            )

            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                userClippingRequestService.approveRequest("req-1", "mcp-service", any())
            }
        }
    }
}
