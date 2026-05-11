package com.clipping.mcpserver.service

import com.clipping.mcpserver.model.BatchSummary
import com.clipping.mcpserver.model.CompetitorWatchlist
import com.clipping.mcpserver.service.competitor.CompetitorSnapshotService
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CompetitorWatchlistStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class CompetitorSnapshotServiceTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val competitorWatchlistStore = mockk<CompetitorWatchlistStore>()
    private val service = CompetitorSnapshotService(batchSummaryStore, competitorWatchlistStore)

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun makeSummary(
        id: String,
        title: String,
        importanceScore: Float = 0.5f,
        daysAgo: Long = 0
    ) = BatchSummary(
        id = id,
        originalTitle = title,
        summary = "Summary of $title",
        sourceLink = "https://example.com/$id",
        importanceScore = importanceScore,
        categoryId = "cat-1",
        rssItemId = "rss-$id",
        createdAt = today.minusDays(daysAgo)
            .atStartOfDay(zone).toInstant().plusSeconds(3600)
    )

    @Nested
    inner class `경쟁사 스냅샷 조회` {

        @Test
        fun `경쟁사 키워드에 매칭되는 기사를 반환한다`() {
            every {
                competitorWatchlistStore.findActive()
            } returns listOf(
                CompetitorWatchlist(
                    id = "c1", name = "AlphaEd",
                    aliases = listOf("AlphaEd", "AlphaEd Holdings")
                ),
                CompetitorWatchlist(
                    id = "c2", name = "DeltaClass",
                    aliases = listOf("DeltaClass")
                )
            )

            // 모든 키워드를 통합한 단일 쿼리 결과
            every {
                batchSummaryStore.findByKeywordsInRange(
                    any(), any(), any(), eq(true), any()
                )
            } returns listOf(
                makeSummary(
                    "s1", "AlphaEd가 신규 교육 과정 발표",
                    importanceScore = 0.87f
                ),
                makeSummary(
                    "s3", "DeltaClass이 시리즈B 투자 유치",
                    importanceScore = 0.75f
                )
            )

            val result = service.getSnapshot(days = 7, limit = 5)

            result.items shouldHaveSize 2
            // 중요도 순 정렬
            result.items[0].competitorName shouldBe "AlphaEd"
            result.items[0].importanceScore shouldBe 0.87f
            result.items[1].competitorName shouldBe "DeltaClass"
        }

        @Test
        fun `별칭 키워드로도 매칭된다`() {
            every {
                competitorWatchlistStore.findActive()
            } returns listOf(
                CompetitorWatchlist(
                    id = "c1", name = "AlphaEd",
                    aliases = listOf("AlphaEd", "AlphaEd Holdings")
                )
            )

            every {
                batchSummaryStore.findByKeywordsInRange(
                    any(), any(), any(), eq(true), any()
                )
            } returns listOf(
                makeSummary(
                    "s1", "AlphaEd Holdings가 AI 교육 사업 확대",
                    importanceScore = 0.8f
                )
            )

            val result = service.getSnapshot(days = 7, limit = 5)

            result.items shouldHaveSize 1
            result.items[0].competitorName shouldBe "AlphaEd"
        }

        @Test
        fun `경쟁사 키워드 설정이 없으면 빈 목록을 반환한다`() {
            every {
                competitorWatchlistStore.findActive()
            } returns emptyList()

            val result = service.getSnapshot(days = 7, limit = 5)

            result.items.shouldBeEmpty()
        }

        @Test
        fun `limit로 결과 수를 제한한다`() {
            every {
                competitorWatchlistStore.findActive()
            } returns listOf(
                CompetitorWatchlist(
                    id = "c1", name = "AlphaEd",
                    aliases = listOf("AlphaEd")
                )
            )

            val summaries = (1..10).map { i ->
                makeSummary(
                    "s$i", "AlphaEd 뉴스 $i",
                    importanceScore = i / 10f
                )
            }

            every {
                batchSummaryStore.findByKeywordsInRange(
                    any(), any(), any(), eq(true), any()
                )
            } returns summaries

            val result = service.getSnapshot(days = 7, limit = 3)

            result.items shouldHaveSize 3
        }

        @Test
        fun `days와 limit이 범위 밖이면 안전한 범위로 보정한다`() {
            every {
                competitorWatchlistStore.findActive()
            } returns listOf(
                CompetitorWatchlist(
                    id = "c1", name = "AlphaEd",
                    aliases = listOf("AlphaEd")
                )
            )

            every {
                batchSummaryStore.findByKeywordsInRange(
                    any(), any(), any(), eq(true), any()
                )
            } returns listOf(
                makeSummary(
                    "s1", "AlphaEd 신규 과정",
                    importanceScore = 0.8f
                )
            )

            val result = service.getSnapshot(days = -7, limit = -1)

            result.items shouldHaveSize 1
            result.items[0].competitorName shouldBe "AlphaEd"
        }

        @Test
        fun `findByKeywordsInRange를 모든 키워드 통합으로 1회 호출한다`() {
            every {
                competitorWatchlistStore.findActive()
            } returns listOf(
                CompetitorWatchlist(
                    id = "c1", name = "A사",
                    aliases = listOf("keyword-a")
                ),
                CompetitorWatchlist(
                    id = "c2", name = "B사",
                    aliases = listOf("keyword-b")
                )
            )

            every {
                batchSummaryStore.findByKeywordsInRange(
                    any(), any(), any(), any(), any()
                )
            } returns emptyList()

            service.getSnapshot(days = 7, limit = 5)

            // N+1 해소: 경쟁사별이 아닌 통합 1회 호출
            verify(exactly = 1) {
                batchSummaryStore.findByKeywordsInRange(
                    any(), any(), any(), eq(true), any()
                )
            }
        }

        @Test
        fun `중복 기사는 높은 importanceScore를 유지한다`() {
            every {
                competitorWatchlistStore.findActive()
            } returns listOf(
                CompetitorWatchlist(
                    id = "c1", name = "A사",
                    aliases = listOf("공통키워드")
                ),
                CompetitorWatchlist(
                    id = "c2", name = "B사",
                    aliases = listOf("공통키워드")
                )
            )

            val sharedSummary = makeSummary(
                "s1", "공통키워드 관련 기사",
                importanceScore = 0.9f
            )

            // 통합 쿼리에서 같은 기사가 반환된다.
            every {
                batchSummaryStore.findByKeywordsInRange(
                    any(), any(), any(), eq(true), any()
                )
            } returns listOf(sharedSummary)

            val result = service.getSnapshot(days = 7, limit = 5)

            // 중복 제거로 하나만 남아야 한다.
            result.items shouldHaveSize 1
            result.items[0].importanceScore shouldBe 0.9f
        }
    }
}
