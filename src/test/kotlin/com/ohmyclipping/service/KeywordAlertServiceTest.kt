package com.ohmyclipping.service

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.service.dto.analytics.KeywordDailyCount
import com.ohmyclipping.service.dto.analytics.KeywordTrendItem
import com.ohmyclipping.service.dto.analytics.KeywordTrendPeriod
import com.ohmyclipping.service.dto.analytics.KeywordTrendResponse
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KeywordAlertServiceTest {

    private val keywordTrendService = mockk<KeywordTrendService>()
    private val notificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val service = KeywordAlertService(keywordTrendService, notificationService)

    private fun makeTrendResponse(vararg items: Pair<String, Double>): KeywordTrendResponse {
        return KeywordTrendResponse(
            period = KeywordTrendPeriod(from = "2026-02-22", to = "2026-03-07"),
            keywords = items.map { (keyword, changeRate) ->
                KeywordTrendItem(
                    keyword = keyword,
                    dailyCounts = emptyList(),
                    totalCount = 10,
                    changeRate = changeRate
                )
            }
        )
    }

    @Nested
    inner class `급변 감지` {

        @Test
        fun `변화율 30퍼센트 이상 키워드가 있으면 알림을 전송한다`() {
            every {
                keywordTrendService.getKeywordTrend(any(), any(), isNull())
            } returns makeTrendResponse("리스킬링" to 0.45, "AI교육" to 0.10)

            val count = service.checkAndAlert()

            count shouldBe 1
            verify(exactly = 1) { notificationService.sendOps(any(), any(), any()) }
        }

        @Test
        fun `변화율 -30퍼센트 이하 키워드도 감지한다`() {
            every {
                keywordTrendService.getKeywordTrend(any(), any(), isNull())
            } returns makeTrendResponse("법정교육" to -0.35, "마이크로러닝" to -0.10)

            val count = service.checkAndAlert()

            count shouldBe 1
        }

        @Test
        fun `변화율이 모두 임계치 미만이면 알림을 보내지 않는다`() {
            every {
                keywordTrendService.getKeywordTrend(any(), any(), isNull())
            } returns makeTrendResponse("AI교육" to 0.10, "법정교육" to -0.05)

            val count = service.checkAndAlert()

            count shouldBe 0
            verify(exactly = 0) { notificationService.sendOps(any(), any(), any()) }
        }

        @Test
        fun `키워드가 비어있으면 알림을 보내지 않는다`() {
            every {
                keywordTrendService.getKeywordTrend(any(), any(), isNull())
            } returns makeTrendResponse()

            val count = service.checkAndAlert()

            count shouldBe 0
        }
    }
}
