package com.clipping.mcpserver.service

import com.clipping.mcpserver.store.ReportDeliveryLogStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class ReportDeliveryHistoryServiceTest {

    private val store: ReportDeliveryLogStore = mockk(relaxed = true)
    private val service = ReportDeliveryHistoryService(store)

    private fun sampleEntry(
        id: String = "a",
        reportType: String = "WEEKLY",
        status: String = "SENT",
        durationMs: Long? = 450,
        itemsProcessed: Int? = 10,
        errorMessage: String? = null
    ) = ReportDeliveryLogStore.HistoryEntry(
        id = id,
        reportType = reportType,
        periodKey = "2026-W15",
        channelId = "C123",
        status = status,
        snapshotId = "snap-1",
        slackMessageTs = "1700000000.000100",
        errorMessage = errorMessage,
        durationMs = durationMs,
        itemsProcessed = itemsProcessed,
        createdAt = Instant.parse("2026-04-10T09:00:00Z"),
        updatedAt = Instant.parse("2026-04-10T09:01:00Z")
    )

    @Nested
    inner class `listHistory 메서드` {

        @Test
        fun `해피패스 - 저장소 결과를 DTO로 변환한다`() {
            every { store.listHistory(any(), any()) } returns listOf(sampleEntry())

            val result = service.listHistory(reportType = "WEEKLY", limit = 50)

            result.size shouldBe 1
            result[0].id shouldBe "a"
            result[0].reportType shouldBe "WEEKLY"
            result[0].status shouldBe "SENT"
            result[0].durationMs shouldBe 450
            result[0].itemsProcessed shouldBe 10
            result[0].startedAt shouldBe "2026-04-10T09:00:00Z"
            result[0].finishedAt shouldBe "2026-04-10T09:01:00Z"
        }

        @Test
        fun `reportType null이면 저장소에 null을 전달한다`() {
            every { store.listHistory(null, 50) } returns emptyList()

            service.listHistory(reportType = null, limit = 50)

            verify(exactly = 1) { store.listHistory(null, 50) }
        }

        @Test
        fun `reportType 공백이면 null로 정규화된다`() {
            every { store.listHistory(null, 50) } returns emptyList()

            service.listHistory(reportType = "   ", limit = 50)

            verify(exactly = 1) { store.listHistory(null, 50) }
        }

        @Test
        fun `reportType 소문자 입력이 들어오면 대문자로 정규화된다`() {
            every { store.listHistory("WEEKLY", 50) } returns emptyList()

            service.listHistory(reportType = "weekly", limit = 50)

            verify(exactly = 1) { store.listHistory("WEEKLY", 50) }
        }

        @Test
        fun `빈 결과도 빈 리스트로 반환한다`() {
            every { store.listHistory(any(), any()) } returns emptyList()

            val result = service.listHistory(reportType = "MONTHLY", limit = 20)

            result.isEmpty() shouldBe true
        }

        @Test
        fun `실패 엔트리는 errorMessage를 보존한다`() {
            every { store.listHistory(any(), any()) } returns listOf(
                sampleEntry(status = "FAILED", errorMessage = "network timeout")
            )

            val result = service.listHistory(reportType = null, limit = 10)

            result[0].status shouldBe "FAILED"
            result[0].errorMessage shouldBe "network timeout"
        }

        @Test
        fun `nullable 필드는 그대로 null 유지한다`() {
            every { store.listHistory(any(), any()) } returns listOf(
                sampleEntry(durationMs = null, itemsProcessed = null)
            )

            val result = service.listHistory(reportType = null, limit = 10)

            result[0].durationMs shouldBe null
            result[0].itemsProcessed shouldBe null
        }
    }
}
