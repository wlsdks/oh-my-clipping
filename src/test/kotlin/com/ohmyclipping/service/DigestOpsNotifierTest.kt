package com.ohmyclipping.service

import com.ohmyclipping.service.digest.*

import com.ohmyclipping.service.port.DigestFailure
import com.ohmyclipping.service.port.OpsLogNotifier
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class DigestOpsNotifierTest {

    private val opsLogNotifier = mockk<OpsLogNotifier>(relaxed = true)
    private val notifier = DigestOpsNotifier(opsLogNotifier)

    private fun sampleFailure(
        categoryId: String = "cat-1",
        categoryName: String = "경제뉴스",
        targetLabel: String = "#channel",
        errorMessage: String = "Connection timeout",
    ) = DigestFailure(
        categoryId = categoryId,
        categoryName = categoryName,
        targetLabel = targetLabel,
        errorMessage = errorMessage,
        failedAt = Instant.now(),
    )

    @Nested
    inner class `notifyTickSummary — 실패 없음` {

        @Test
        fun `빈 실패 목록에서는 OpsLogNotifier를 호출하지 않는다`() {
            notifier.notifyTickSummary(emptyList())

            verify(exactly = 0) { opsLogNotifier.postDigestFailures(any()) }
        }
    }

    @Nested
    inner class `notifyTickSummary — 실패 있음` {

        @Test
        fun `실패가 있으면 postDigestFailures를 1회 호출한다`() {
            val failures = listOf(sampleFailure())

            notifier.notifyTickSummary(failures)

            verify(exactly = 1) { opsLogNotifier.postDigestFailures(failures) }
        }

        @Test
        fun `여러 실패 항목도 1회 호출로 전달한다`() {
            val failures = listOf(
                sampleFailure(categoryId = "cat-1", categoryName = "경제뉴스"),
                sampleFailure(categoryId = "cat-2", categoryName = "기술뉴스"),
            )

            notifier.notifyTickSummary(failures)

            verify(exactly = 1) { opsLogNotifier.postDigestFailures(failures) }
        }
    }

    @Nested
    inner class `notifyDelivered — 로그만 남김` {

        @Test
        fun `성공 시 OpsLogNotifier를 호출하지 않는다`() {
            notifier.notifyDelivered("경제뉴스", "C123456", 5)

            verify(exactly = 0) { opsLogNotifier.postDigestFailures(any()) }
        }

        @Test
        fun `DM 대상도 호출 없이 처리된다`() {
            notifier.notifyDelivered("기술뉴스", "D7890AB", 3)

            verify(exactly = 0) { opsLogNotifier.postDigestFailures(any()) }
        }
    }
}
