package com.clipping.mcpserver.admin

import com.clipping.mcpserver.service.AnalyticsContentLeversService
import com.clipping.mcpserver.service.dto.ContentLeversSummary
import com.clipping.mcpserver.service.dto.SourceQualityRow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AnalyticsContentLeversControllerTest {

    private val service = mockk<AnalyticsContentLeversService>()
    private val controller = AnalyticsContentLeversController(service)

    @Test
    fun `GET summary — service 를 호출하고 결과 그대로 반환`() {
        val expected = ContentLeversSummary(
            sourceQuality = listOf(
                SourceQualityRow(
                    sourceId = "src1",
                    sourceName = "TechCrunch",
                    delivered = 20,
                    uniqueUserClicks = 5,
                    clickRatePct = 25.0,
                    likes = 4,
                    dislikes = 1,
                    likeRatePct = 80.0,
                    statusLabel = "normal",
                    isActive = true,
                    updatedAt = Instant.parse("2026-04-10T00:00:00Z"),
                )
            ),
        )
        every { service.summary(any(), any()) } returns expected

        val result = controller.summary(period = "28d")
        result shouldBe expected
    }

    @Test
    fun `GET summary with period=7d — service 호출 성공`() {
        every { service.summary(any(), any()) } returns ContentLeversSummary(emptyList())
        controller.summary(period = "7d")
        verify(exactly = 1) { service.summary(any(), any()) }
    }

    @Test
    fun `GET summary with period=7d 는 7일 구간으로 service 를 호출한다`() {
        val fromSlot = slot<Instant>()
        val toSlot = slot<Instant>()
        every { service.summary(capture(fromSlot), capture(toSlot)) } returns ContentLeversSummary(emptyList())

        controller.summary(period = "7d")

        Duration.between(fromSlot.captured, toSlot.captured).toDays() shouldBe 7
    }
}
