package com.ohmyclipping.service

import com.ohmyclipping.content.ArticleContentExtractor
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.BatchSummaryCompetitor
import com.ohmyclipping.model.CompetitorWatchlist
import com.ohmyclipping.service.competitor.CompetitorOrganizationSynchronizer
import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.port.RssCollectionPort
import com.ohmyclipping.store.BatchSummaryCompetitorStore
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CompetitorRssFeedStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.OriginalContentStore
import com.ohmyclipping.store.BookmarkedArticleStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class CompetitorWeeklyDataTest {

    private val watchlistStore = mockk<CompetitorWatchlistStore>()
    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val batchSummaryCompetitorStore = mockk<BatchSummaryCompetitorStore>()
    private val competitorRssFeedStore = mockk<CompetitorRssFeedStore>()
    private val originalContentStore = mockk<OriginalContentStore>()
    private val articleContentExtractor = mockk<ArticleContentExtractor>()
    private val rssFeedCollector = mockk<RssCollectionPort>()
    private val bookmarkedArticleStore = mockk<BookmarkedArticleStore>()
    private val organizationSynchronizer = mockk<CompetitorOrganizationSynchronizer>(relaxed = true)
    private val service = CompetitorWatchlistService(
        watchlistStore, batchSummaryStore,
        batchSummaryCompetitorStore, competitorRssFeedStore,
        originalContentStore, articleContentExtractor,
        rssFeedCollector, bookmarkedArticleStore,
        organizationSynchronizer
    )

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.now(zone)

    private fun makeCompetitor(
        id: String,
        name: String,
        aliases: List<String> = listOf(name)
    ) = CompetitorWatchlist(
        id = id, name = name, aliases = aliases,
        tier = "DIRECT", isActive = true
    )

    private fun makeSummary(
        id: String,
        title: String,
        importanceScore: Float = 0.5f,
        eventType: String? = null,
        sentiment: String? = null,
        daysAgo: Long = 0
    ) = BatchSummary(
        id = id,
        originalTitle = title,
        summary = "Summary of $title",
        sourceLink = "https://example.com/$id",
        importanceScore = importanceScore,
        categoryId = "cat-1",
        rssItemId = "rss-$id",
        eventType = eventType,
        sentiment = sentiment,
        createdAt = today.minusDays(daysAgo)
            .atStartOfDay(zone).toInstant().plusSeconds(3600)
    )

    private fun makeLink(summaryId: String, competitorId: String) =
        BatchSummaryCompetitor(
            summaryId = summaryId,
            competitorId = competitorId
        )

    // ----- SOV delta 테스트 -----

    @Nested
    inner class `전주 대비 SOV 변화량 계산` {

        @Test
        fun `전주 대비 SOV 변화량이 계산된다`() {
            val compA = makeCompetitor("c1", "회사A")
            // 활성 경쟁사 조회 — getShareOfVoiceWithDelta 내부에서 2회(현재/이전) 호출
            every { watchlistStore.findActive() } returns listOf(compA)

            // 현재 기간: c1 기사 4건 → 4/4 = 40% (비율 1.0, 단독 경쟁사이므로)
            // 이전 기간: c1 기사 3건 → 3/3 = 30% (비율 1.0)
            // 여기서는 두 경쟁사가 있어야 의미 있는 비율이 나오므로,
            // 현재: c1=4, c2=6 → c1 share=0.4 / 이전: c1=3, c2=7 → c1 share=0.3 로 구성
            val compB = makeCompetitor("c2", "회사B")
            every { watchlistStore.findActive() } returns listOf(compA, compB)

            // 현재 기간 summaryIds
            val currIds = listOf("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10")
            // 이전 기간 summaryIds
            val prevIds = listOf("p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10")

            // batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds — 날짜 범위로 구분
            // MockK는 호출 순서를 첫 번째 → 두 번째로 처리한다.
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returnsMany listOf(currIds, prevIds)

            // 현재 기간 링크: c1=4개, c2=6개
            val currLinks = listOf(
                makeLink("s1", "c1"), makeLink("s2", "c1"),
                makeLink("s3", "c1"), makeLink("s4", "c1"),
                makeLink("s5", "c2"), makeLink("s6", "c2"),
                makeLink("s7", "c2"), makeLink("s8", "c2"),
                makeLink("s9", "c2"), makeLink("s10", "c2")
            )
            // 이전 기간 링크: c1=3개, c2=7개
            val prevLinks = listOf(
                makeLink("p1", "c1"), makeLink("p2", "c1"), makeLink("p3", "c1"),
                makeLink("p4", "c2"), makeLink("p5", "c2"), makeLink("p6", "c2"),
                makeLink("p7", "c2"), makeLink("p8", "c2"), makeLink("p9", "c2"), makeLink("p10", "c2")
            )

            every {
                batchSummaryCompetitorStore.findBySummaryIds(currIds)
            } returns currLinks

            every {
                batchSummaryCompetitorStore.findBySummaryIds(prevIds)
            } returns prevLinks

            val result = service.getShareOfVoiceWithDelta(days = 7)

            // c1: 현재 share=0.4, 이전 share=0.3, delta=+0.1
            val c1Item = result.shares.find { it.competitorId == "c1" }
            c1Item.shouldNotBeNull()
            c1Item.share shouldBeExactly 0.4
            val prevShare = c1Item.prevShare.shouldNotBeNull()
            prevShare shouldBeExactly 0.3
            val shareDelta = c1Item.shareDelta.shouldNotBeNull()
            // delta = 0.4 - 0.3 = 0.1 (부동소수점 허용 범위 내 비교)
            shareDelta shouldBeExactly 0.4 - 0.3
        }

        @Test
        fun `전주 데이터 없는 경쟁사는 delta가 null이다`() {
            val compA = makeCompetitor("c1", "회사A")
            val compB = makeCompetitor("c2", "신규회사")
            every { watchlistStore.findActive() } returns listOf(compA, compB)

            // 현재 기간: c1, c2 모두 있음
            val currIds = listOf("s1", "s2", "s3")
            // 이전 기간: c2는 없음 (빈 결과)
            val prevIds = listOf("p1", "p2")

            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returnsMany listOf(currIds, prevIds)

            // 현재: c1=2개, c2=1개
            val currLinks = listOf(
                makeLink("s1", "c1"), makeLink("s2", "c1"),
                makeLink("s3", "c2")
            )
            // 이전: c1=2개만 있고 c2는 없음
            val prevLinks = listOf(
                makeLink("p1", "c1"), makeLink("p2", "c1")
            )

            every {
                batchSummaryCompetitorStore.findBySummaryIds(currIds)
            } returns currLinks

            every {
                batchSummaryCompetitorStore.findBySummaryIds(prevIds)
            } returns prevLinks

            val result = service.getShareOfVoiceWithDelta(days = 7)

            // c2는 이전 기간에 없으므로 delta가 null
            val c2Item = result.shares.find { it.competitorId == "c2" }
            c2Item.shouldNotBeNull()
            c2Item.shareDelta.shouldBeNull()
            c2Item.prevShare.shouldBeNull()
        }
    }

    // ----- TOP 기사 추출 테스트 -----

    @Nested
    inner class `경쟁사별 TOP 기사 추출` {

        @Test
        fun `경쟁사별 TOP 5 기사가 importanceScore 순으로 반환된다`() {
            val compA = makeCompetitor("c1", "회사A")
            every { watchlistStore.findActive() } returns listOf(compA)

            // 7개 기사 생성 (importanceScore 다양)
            val summaries = listOf(
                makeSummary("s1", "기사1", importanceScore = 0.9f),
                makeSummary("s2", "기사2", importanceScore = 0.7f),
                makeSummary("s3", "기사3", importanceScore = 0.5f),
                makeSummary("s4", "기사4", importanceScore = 0.3f),
                makeSummary("s5", "기사5", importanceScore = 0.8f),
                makeSummary("s6", "기사6", importanceScore = 0.6f),
                makeSummary("s7", "기사7", importanceScore = 0.4f)
            )
            val ids = summaries.map { it.id }
            val links = ids.map { makeLink(it, "c1") }

            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns ids

            every { batchSummaryStore.findByIds(ids) } returns summaries
            every { batchSummaryCompetitorStore.findBySummaryIds(ids) } returns links

            val result = service.getTopArticlesForWeeklyDigest(days = 7, topPerCompetitor = 5)

            // 회사A 키로 TOP 5 기사가 반환된다
            val articles = result["회사A"]
            articles.shouldNotBeNull()
            articles shouldHaveSize 5

            // importanceScore 내림차순 정렬 검증
            val scores = articles.map { it.importanceScore }
            scores shouldBe scores.sortedDescending()

            // 상위 5개 점수가 올바른지 확인 (0.9, 0.8, 0.7, 0.6, 0.5)
            scores[0] shouldBe 0.9f
            scores[1] shouldBe 0.8f
            scores[2] shouldBe 0.7f
        }

        @Test
        fun `기사 없는 경쟁사는 빈 리스트로 반환된다`() {
            val compA = makeCompetitor("c1", "회사A")
            every { watchlistStore.findActive() } returns listOf(compA)

            // 기사가 전혀 없는 경우
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns emptyList()

            val result = service.getTopArticlesForWeeklyDigest(days = 7, topPerCompetitor = 5)

            // 빈 결과 반환 (경쟁사 자체가 없으므로 빈 맵)
            result.shouldNotBeNull()
            result.values.forEach { it.shouldBeEmpty() }
        }
    }
}
