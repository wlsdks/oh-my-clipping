package com.ohmyclipping.admin.mcp

import com.ohmyclipping.model.UserClippingRequestStatus
import com.ohmyclipping.service.UserClippingRequestService
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminListPendingRequestsToolTest {

    private val userClippingRequestService = mockk<UserClippingRequestService>()
    private val tool = AdminListPendingRequestsTool(userClippingRequestService)

    @Nested
    inner class `admin_list_pending_requests` {

        @Test
        fun `limit 이 null 이면 기본값 20 으로 승인 대기 요청을 조회한다`() {
            every {
                userClippingRequestService.listRecentRequests(UserClippingRequestStatus.PENDING, 20)
            } returns emptyList()

            val json = tool.admin_list_pending_requests(limit = null)

            json shouldNotContain "\"error\""
            verify(exactly = 1) {
                userClippingRequestService.listRecentRequests(UserClippingRequestStatus.PENDING, 20)
            }
        }

        @Test
        fun `범위 밖 limit 은 서비스 호출 없이 validation error 로 거부한다`() {
            val json = tool.admin_list_pending_requests(limit = 0)

            json shouldContain "\"error\""
            json shouldContain "limit must be between 1 and 50"
            verify(exactly = 0) {
                userClippingRequestService.listRecentRequests(any(), any())
            }
        }
    }
}
