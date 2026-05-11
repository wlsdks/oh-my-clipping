package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.DeliveryLogStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * 다이제스트 후처리 서비스 테스트.
 * sent 마킹, 통계 적재, delivery_log 복구를 한 경계에서 수행하는지 검증한다.
 */
class DigestDeliveryFinalizationServiceTest {

    private val summaryStore = mockk<BatchSummaryStore>()
    private val statsService = mockk<StatsService>()
    private val deliveryLogStore = mockk<DeliveryLogStore>()
    private val service = DigestDeliveryFinalizationService(
        summaryStore = summaryStore,
        statsService = statsService,
        deliveryLogStore = deliveryLogStore
    )

    @Test
    fun `summary와 delivery log가 있으면 sent 마킹 통계 갱신 로그 복구를 함께 수행한다`() {
        val summaryIds = listOf("sum-1", "sum-2")
        every { summaryStore.markSent(summaryIds) } just Runs
        every { statsService.recordDigestDelivery("cat-1", 1, 1) } just Runs
        every { deliveryLogStore.updateStatus("log-1", "SENT", 2, "1712.345") } just Runs

        service.finalizeDelivery(
            summaryIds = summaryIds,
            categoryId = "cat-1",
            sendAttempts = 1,
            sendSuccesses = 1,
            deliveryLogId = "log-1",
            slackMessageTs = "1712.345"
        )

        verify(exactly = 1) { summaryStore.markSent(summaryIds) }
        verify(exactly = 1) { statsService.recordDigestDelivery("cat-1", 1, 1) }
        verify(exactly = 1) { deliveryLogStore.updateStatus("log-1", "SENT", 2, "1712.345") }
    }

    @Test
    fun `summary가 비어도 delivery log 복구는 유지한다`() {
        every { deliveryLogStore.updateStatus("log-empty", "SENT", 0, "1712.999") } just Runs

        service.finalizeDelivery(
            summaryIds = emptyList(),
            categoryId = "cat-1",
            sendAttempts = 0,
            sendSuccesses = 0,
            deliveryLogId = "log-empty",
            slackMessageTs = "1712.999"
        )

        verify(exactly = 0) { summaryStore.markSent(any()) }
        verify(exactly = 0) { statsService.recordDigestDelivery(any(), any(), any()) }
        verify(exactly = 1) { deliveryLogStore.updateStatus("log-empty", "SENT", 0, "1712.999") }
    }
}
