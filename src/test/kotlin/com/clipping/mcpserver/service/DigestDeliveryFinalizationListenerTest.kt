package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.digest.*

import com.clipping.mcpserver.service.event.DigestDeliveryFinalizationRequestedEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * 다이제스트 후처리 리스너 테스트.
 * 이벤트 재시도와 예외 비전파가 유지되는지 검증한다.
 */
class DigestDeliveryFinalizationListenerTest {

    private val finalizationService = mockk<DigestDeliveryFinalizationService>()
    private val listener = DigestDeliveryFinalizationListener(finalizationService)

    @Test
    fun `후처리 성공 시 서비스에 한 번만 위임한다`() {
        every {
            finalizationService.finalizeDelivery(
                summaryIds = listOf("sum-1"),
                categoryId = "cat-1",
                sendAttempts = 1,
                sendSuccesses = 1,
                deliveryLogId = "log-1",
                slackMessageTs = "1712.100"
            )
        } just Runs

        listener.handle(
            DigestDeliveryFinalizationRequestedEvent(
                summaryIds = listOf("sum-1"),
                categoryId = "cat-1",
                sendAttempts = 1,
                sendSuccesses = 1,
                deliveryLogId = "log-1",
                slackMessageTs = "1712.100"
            )
        )

        verify(exactly = 1) {
            finalizationService.finalizeDelivery(
                summaryIds = listOf("sum-1"),
                categoryId = "cat-1",
                sendAttempts = 1,
                sendSuccesses = 1,
                deliveryLogId = "log-1",
                slackMessageTs = "1712.100"
            )
        }
    }

    @Test
    fun `후처리가 실패하면 재시도 후 예외를 전파하지 않는다`() {
        every {
            finalizationService.finalizeDelivery(
                summaryIds = listOf("sum-2"),
                categoryId = "cat-2",
                sendAttempts = 1,
                sendSuccesses = 1,
                deliveryLogId = null,
                slackMessageTs = null
            )
        } throws RuntimeException("db down")

        listener.handle(
            DigestDeliveryFinalizationRequestedEvent(
                summaryIds = listOf("sum-2"),
                categoryId = "cat-2",
                sendAttempts = 1,
                sendSuccesses = 1
            )
        )

        verify(exactly = DigestDeliveryFinalizationListener.MAX_RETRY_ATTEMPTS) {
            finalizationService.finalizeDelivery(
                summaryIds = listOf("sum-2"),
                categoryId = "cat-2",
                sendAttempts = 1,
                sendSuccesses = 1,
                deliveryLogId = null,
                slackMessageTs = null
            )
        }
    }
}
