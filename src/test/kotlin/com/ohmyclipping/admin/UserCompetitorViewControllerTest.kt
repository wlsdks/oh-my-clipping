package com.ohmyclipping.admin

import com.ohmyclipping.service.competitor.CompetitorSnapshotService
import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.dto.CompetitorSnapshotResponse
import com.ohmyclipping.service.dto.CompetitorTimelineResponse
import com.ohmyclipping.service.dto.SovPeriod
import com.ohmyclipping.service.dto.SovResponse
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserCompetitorViewControllerTest {

    private val snapshotService = mockk<CompetitorSnapshotService>()
    private val watchlistService = mockk<CompetitorWatchlistService>()
    private val controller = UserCompetitorViewController(snapshotService, watchlistService)

    @Nested
    inner class `경쟁사 스냅샷 조회` {

        @Test
        fun `기본 파라미터로 스냅샷을 조회한다`() {
            // 기본값: days=7, limit=5
            val expected = CompetitorSnapshotResponse(items = emptyList())
            every { snapshotService.getSnapshot(7, 5) } returns expected

            val result = controller.getSnapshot(days = 7, limit = 5)

            result shouldBe expected
            verify(exactly = 1) { snapshotService.getSnapshot(7, 5) }
        }

        @Test
        fun `커스텀 파라미터로 스냅샷을 조회한다`() {
            val expected = CompetitorSnapshotResponse(items = emptyList())
            every { snapshotService.getSnapshot(14, 10) } returns expected

            val result = controller.getSnapshot(days = 14, limit = 10)

            result shouldBe expected
        }
    }

    @Nested
    inner class `경쟁사 타임라인 조회` {

        @Test
        fun `기본 파라미터로 타임라인을 조회한다`() {
            // 기본값: days=30, competitorId=null, eventType=null
            val expected = CompetitorTimelineResponse(items = emptyList())
            every { watchlistService.getTimeline(30, null, null) } returns expected

            val result = controller.getTimeline(days = 30, competitorId = null, eventType = null)

            result shouldBe expected
            verify(exactly = 1) { watchlistService.getTimeline(30, null, null) }
        }

        @Test
        fun `필터 파라미터를 지정하여 타임라인을 조회한다`() {
            val expected = CompetitorTimelineResponse(items = emptyList())
            every { watchlistService.getTimeline(60, "comp-1", "NEWS") } returns expected

            val result = controller.getTimeline(days = 60, competitorId = "comp-1", eventType = "NEWS")

            result shouldBe expected
        }
    }

    @Nested
    inner class `Share of Voice 조회` {

        @Test
        fun `기본 파라미터로 SoV를 조회한다`() {
            // 기본값: days=30
            val expected = SovResponse(
                period = SovPeriod(from = "2026-02-06", to = "2026-03-08"),
                totalArticles = 0,
                shares = emptyList()
            )
            every { watchlistService.getShareOfVoice(30) } returns expected

            val result = controller.getShareOfVoice(days = 30)

            result shouldBe expected
            verify(exactly = 1) { watchlistService.getShareOfVoice(30) }
        }
    }
}
