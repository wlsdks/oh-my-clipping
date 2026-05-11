package com.clipping.mcpserver.service

import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.service.competitor.CompetitorArticleSummarizationService
import com.clipping.mcpserver.service.competitor.CompetitorCollectionScheduler
import com.clipping.mcpserver.service.competitor.CompetitorCollectionService
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CompetitorCollectionSchedulerTest {

    private val competitorCollectionService = mockk<CompetitorCollectionService>()
    private val competitorArticleSummarizationService = mockk<CompetitorArticleSummarizationService>()
    private val metrics = mockk<ClippingMetrics>()
    private val scheduler = CompetitorCollectionScheduler(competitorCollectionService, competitorArticleSummarizationService, metrics)

    @BeforeEach
    fun setUp() {
        // metrics.recordSchedulerRun은 블록을 그대로 실행한다
        every { metrics.recordSchedulerRun<Unit>(any(), any()) } answers {
            val block = secondArg<() -> Unit>()
            block()
        }
    }

    @Nested
    inner class `경쟁사 수집` {

        @Test
        fun `collectCompetitorNews가 CompetitorCollectionService를 호출한다`() {
            every { competitorCollectionService.collectAll(any()) } returns listOf(
                CompetitorCollectionService.CollectionResult(
                    competitorId = "c1",
                    competitorName = "Test",
                    newItemCount = 3,
                    linkedItemCount = 1,
                    error = null
                )
            )

            scheduler.collectCompetitorNews()

            // 12시간 수집 간격 + 2시간 버퍼 = hoursBack 14
            verify(exactly = 1) { competitorCollectionService.collectAll(hoursBack = 14) }
        }

        @Test
        fun `수집 결과가 없어도 정상 완료된다`() {
            every { competitorCollectionService.collectAll(any()) } returns emptyList()

            val result = runCatching { scheduler.collectCompetitorNews() }

            result.isSuccess shouldBe true
            verify(exactly = 1) { competitorCollectionService.collectAll(any()) }
        }

        @Test
        fun `수집 실패 시 예외가 전파된다`() {
            every { competitorCollectionService.collectAll(any()) } throws RuntimeException("test error")

            val result = runCatching { scheduler.collectCompetitorNews() }

            result.isFailure shouldBe true
        }

        @Test
        fun `메트릭이 기록된다`() {
            every { competitorCollectionService.collectAll(any()) } returns emptyList()

            scheduler.collectCompetitorNews()

            verify { metrics.recordSchedulerRun<Unit>("competitor_collection", any()) }
        }
    }
}
