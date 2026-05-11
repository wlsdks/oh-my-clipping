package com.clipping.mcpserver.admin

import com.clipping.mcpserver.user.UserBriefingController
import com.clipping.mcpserver.service.BriefingService
import com.clipping.mcpserver.service.dto.BriefingListResponse
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication

class UserBriefingControllerTest {

    private val briefingService = mockk<BriefingService>()
    private val controller = UserBriefingController(briefingService)
    private val authentication = mockk<Authentication>(relaxed = true)

    @Nested
    inner class `오늘의 브리핑 조회` {

        @Test
        fun `categoryId 없이 전체 브리핑을 조회한다`() {
            // 전체 조회 시 서비스에 null을 전달한다
            val expected = BriefingListResponse(briefings = emptyList())
            every { briefingService.getTodayBriefings(null) } returns expected

            val result = controller.getTodayBriefings(null, authentication)

            result shouldBe expected
            verify(exactly = 1) { briefingService.getTodayBriefings(null) }
        }

        @Test
        fun `categoryId를 지정하면 해당 카테고리만 조회한다`() {
            // 카테고리 필터링 시 서비스에 categoryId를 전달한다
            val categoryId = "cat-123"
            val expected = BriefingListResponse(briefings = emptyList())
            every { briefingService.getTodayBriefings(categoryId) } returns expected

            val result = controller.getTodayBriefings(categoryId, authentication)

            result shouldBe expected
            verify(exactly = 1) { briefingService.getTodayBriefings(categoryId) }
        }
    }
}
