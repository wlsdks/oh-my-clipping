package com.ohmyclipping.admin

import com.ohmyclipping.service.KeywordTrendService
import com.ohmyclipping.service.dto.KeywordTrendPeriod
import com.ohmyclipping.service.dto.KeywordTrendResponse
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserKeywordTrendControllerTest {

    private val keywordTrendService = mockk<KeywordTrendService>()
    private val controller = UserKeywordTrendController(keywordTrendService)

    private fun emptyTrendResponse() = KeywordTrendResponse(
        period = KeywordTrendPeriod(from = "2026-03-01", to = "2026-03-07"),
        keywords = emptyList()
    )

    @Nested
    inner class `키워드 트렌드 조회` {

        @Test
        fun `기본 파라미터로 트렌드를 조회한다`() {
            // 기본값: days=7, top=10, categoryId=null
            val expected = emptyTrendResponse()
            every { keywordTrendService.getKeywordTrend(7, 10, null) } returns expected

            val result = controller.getKeywordTrend(
                from = null, to = null, days = 7, top = 10, categoryId = null
            )

            result shouldBe expected
            verify(exactly = 1) { keywordTrendService.getKeywordTrend(7, 10, null) }
        }

        @Test
        fun `커스텀 파라미터로 트렌드를 조회한다`() {
            // 사용자 지정 파라미터를 서비스에 그대로 전달한다
            val expected = emptyTrendResponse()
            every { keywordTrendService.getKeywordTrend(30, 5, "cat-456") } returns expected

            val result = controller.getKeywordTrend(
                from = null, to = null, days = 30, top = 5, categoryId = "cat-456"
            )

            result shouldBe expected
            verify(exactly = 1) { keywordTrendService.getKeywordTrend(30, 5, "cat-456") }
        }
    }
}
