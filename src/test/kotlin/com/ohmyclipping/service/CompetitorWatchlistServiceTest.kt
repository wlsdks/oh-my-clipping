package com.ohmyclipping.service

import com.ohmyclipping.content.ArticleContentExtractor
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.BatchSummaryCompetitor
import com.ohmyclipping.model.CompetitorRssFeed
import com.ohmyclipping.model.CompetitorWatchlist
import com.ohmyclipping.model.RssItem
import com.ohmyclipping.service.collection.toRssCollectedItem
import com.ohmyclipping.service.competitor.CompetitorOrganizationSynchronizer
import com.ohmyclipping.service.competitor.CompetitorWatchlistService
import com.ohmyclipping.service.port.RssCollectionPort
import com.ohmyclipping.service.dto.analytics.RssFeedInput
import com.ohmyclipping.store.BatchSummaryCompetitorStore
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CompetitorRssFeedStore
import com.ohmyclipping.store.CompetitorWatchlistStore
import com.ohmyclipping.store.OriginalContentStore
import com.ohmyclipping.store.BookmarkedArticleStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class CompetitorWatchlistServiceTest {

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

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun makeCompetitor(
        id: String = "comp-1",
        name: String = "AlphaEd",
        aliases: List<String> = listOf("AlphaEd", "AlphaEd Holdings"),
        tier: String = "DIRECT",
        isActive: Boolean = true
    ) = CompetitorWatchlist(
        id = id, name = name, aliases = aliases,
        tier = tier, isActive = isActive
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

    @Nested
    inner class `경쟁사 목록 조회` {

        @Test
        fun `경쟁사 목록을 RSS 피드와 기사 통계와 함께 반환한다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "AlphaEd"),
                makeCompetitor(
                    id = "c2", name = "DeltaClass",
                    aliases = listOf("DeltaClass")
                )
            )
            every { watchlistStore.findAll() } returns competitors
            every {
                batchSummaryCompetitorStore.countByCompetitorIds(any())
            } returns mapOf("c1" to 10L, "c2" to 5L)
            every {
                batchSummaryCompetitorStore.countByCompetitorIdsSince(any(), any())
            } returns mapOf("c1" to 2L)
            every { competitorRssFeedStore.findByCompetitorId("c1") } returns listOf(
                CompetitorRssFeed(id = "f1", competitorId = "c1", feedUrl = "https://example.com/rss")
            )
            every { competitorRssFeedStore.findByCompetitorId("c2") } returns emptyList()

            val result = service.list()

            result shouldHaveSize 2
            result[0].name shouldBe "AlphaEd"
            result[0].articleCount shouldBe 10L
            result[0].last24hCount shouldBe 2L
            result[0].rssFeeds shouldHaveSize 1
            result[1].name shouldBe "DeltaClass"
            result[1].articleCount shouldBe 5L
            result[1].last24hCount shouldBe 0L
        }
    }

    @Nested
    inner class `경쟁사 생성` {

        @Test
        fun `이름이 비어있으면 예외를 던진다`() {
            assertThrows<InvalidInputException> {
                service.create(
                    name = "  ", aliases = listOf("test"),
                    tier = "DIRECT"
                )
            }
        }

        @Test
        fun `유효하지 않은 tier이면 예외를 던진다`() {
            assertThrows<InvalidInputException> {
                service.create(
                    name = "테스트", aliases = listOf("test"),
                    tier = "INVALID"
                )
            }
        }

        @Test
        fun `동일한 이름이 이미 존재하면 예외를 던진다`() {
            every { watchlistStore.findAll() } returns listOf(makeCompetitor(name = "AlphaEd"))
            every {
                batchSummaryCompetitorStore.countByCompetitorIds(any())
            } returns emptyMap()
            every {
                batchSummaryCompetitorStore.countByCompetitorIdsSince(any(), any())
            } returns emptyMap()
            every { competitorRssFeedStore.findByCompetitorId(any()) } returns emptyList()

            assertThrows<InvalidInputException> {
                service.create(
                    name = "AlphaEd",
                    aliases = listOf("키워드"),
                    tier = "DIRECT"
                )
            }
        }

        @Test
        fun `이름 대소문자가 달라도 중복으로 처리한다`() {
            every { watchlistStore.findAll() } returns listOf(makeCompetitor(name = "Kakao"))
            every {
                batchSummaryCompetitorStore.countByCompetitorIds(any())
            } returns emptyMap()
            every {
                batchSummaryCompetitorStore.countByCompetitorIdsSince(any(), any())
            } returns emptyMap()
            every { competitorRssFeedStore.findByCompetitorId(any()) } returns emptyList()

            assertThrows<InvalidInputException> {
                service.create(
                    name = "kakao",
                    aliases = listOf("MessengerCo"),
                    tier = "DIRECT"
                )
            }
        }

        @Test
        fun `정상 입력이면 경쟁사를 생성한다`() {
            // 중복 이름 체크를 위한 findAll 스텁 — 기존 경쟁사 없음
            every { watchlistStore.findAll() } returns emptyList()
            val slot = slot<CompetitorWatchlist>()
            every { watchlistStore.save(capture(slot)) } answers {
                slot.captured.copy(id = "new-id")
            }

            val result = service.create(
                name = "테스트 경쟁사",
                aliases = listOf("키워드1"), tier = "DIRECT"
            )

            result.name shouldBe "테스트 경쟁사"
            verify(exactly = 1) { watchlistStore.save(any()) }
        }

        @Test
        fun `RSS 피드를 함께 등록한다`() {
            // 중복 이름 체크를 위한 findAll 스텁 — 기존 경쟁사 없음
            every { watchlistStore.findAll() } returns emptyList()
            val slot = slot<CompetitorWatchlist>()
            every { watchlistStore.save(capture(slot)) } answers {
                slot.captured.copy(id = "new-id")
            }
            val feedSlot = slot<CompetitorRssFeed>()
            every { competitorRssFeedStore.save(capture(feedSlot)) } answers {
                feedSlot.captured.copy(id = "feed-1")
            }

            val result = service.create(
                name = "테스트 경쟁사",
                aliases = listOf("키워드1"),
                tier = "DIRECT",
                rssFeeds = listOf(RssFeedInput(url = "https://blog.example.com/rss", label = "공식 블로그"))
            )

            result.rssFeeds shouldHaveSize 1
            result.rssFeeds[0].feedUrl shouldBe "https://blog.example.com/rss"
            result.rssFeeds[0].label shouldBe "공식 블로그"
            verify(exactly = 1) { competitorRssFeedStore.save(any()) }
        }
    }

    @Nested
    inner class `경쟁사 수정` {

        @Test
        fun `rssFeeds가 제공되면 기존 피드를 교체한다`() {
            val existing = makeCompetitor(id = "c1")
            every { watchlistStore.findById("c1") } returns existing
            every { watchlistStore.update(any()) } answers { firstArg() }
            every { competitorRssFeedStore.deleteByCompetitorId("c1") } returns Unit
            val feedSlot = slot<CompetitorRssFeed>()
            every { competitorRssFeedStore.save(capture(feedSlot)) } answers {
                feedSlot.captured.copy(id = "feed-new")
            }
            every { competitorRssFeedStore.findByCompetitorId("c1") } returns listOf(
                CompetitorRssFeed(id = "feed-new", competitorId = "c1", feedUrl = "https://new.com/rss")
            )
            every { batchSummaryCompetitorStore.countByCompetitorId("c1") } returns 5L
            every { batchSummaryCompetitorStore.countByCompetitorIdSince("c1", any()) } returns 1L

            val result = service.update(
                id = "c1",
                name = null,
                aliases = null,
                tier = null,
                isActive = null,
                rssFeeds = listOf(RssFeedInput(url = "https://new.com/rss"))
            )

            verify(exactly = 1) { competitorRssFeedStore.deleteByCompetitorId("c1") }
            verify(exactly = 1) { competitorRssFeedStore.save(any()) }
            result.rssFeeds shouldHaveSize 1
            result.articleCount shouldBe 5L
        }
    }

    @Nested
    inner class `경쟁사 삭제` {

        @Test
        fun `존재하지 않는 경쟁사를 삭제하면 NotFoundException`() {
            every { watchlistStore.findById("nonexistent") } returns null

            assertThrows<NotFoundException> {
                service.delete("nonexistent")
            }
        }

        @Test
        fun `존재하는 경쟁사를 삭제하면 RSS 피드도 함께 삭제한다`() {
            every { watchlistStore.findById("c1") } returns makeCompetitor(id = "c1")
            every { competitorRssFeedStore.deleteByCompetitorId("c1") } returns Unit
            every { watchlistStore.delete("c1") } returns Unit

            service.delete("c1")

            verify(exactly = 1) { competitorRssFeedStore.deleteByCompetitorId("c1") }
            verify(exactly = 1) { watchlistStore.delete("c1") }
        }
    }

    @Nested
    inner class `타임라인 조회 (junction 기반)` {

        @Test
        fun `junction 테이블을 통해 경쟁사 연결된 기사를 반환한다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "AlphaEd"),
                makeCompetitor(id = "c2", name = "DeltaClass", aliases = listOf("DeltaClass"))
            )
            every { watchlistStore.findActive() } returns competitors
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1", "s2")

            val summaries = listOf(
                makeSummary("s1", "AlphaEd가 신규 과정 발표", eventType = "product_launch"),
                makeSummary("s2", "DeltaClass이 투자 유치", eventType = "funding")
            )
            every { batchSummaryStore.findByIds(listOf("s1", "s2")) } returns summaries
            every {
                batchSummaryCompetitorStore.findBySummaryIds(listOf("s1", "s2"))
            } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s2", "c2")
            )

            val result = service.getTimeline(
                days = 30, competitorId = null, eventType = null
            )

            result.items shouldHaveSize 2
            result.items.map { it.competitorName }.toSet() shouldBe
                setOf("AlphaEd", "DeltaClass")
        }

        @Test
        fun `eventType 필터가 적용된다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "AlphaEd")
            )
            every { watchlistStore.findActive() } returns competitors
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1", "s2")

            val summaries = listOf(
                makeSummary("s1", "AlphaEd 제품 출시", eventType = "product_launch"),
                makeSummary("s2", "AlphaEd 투자", eventType = "funding")
            )
            every { batchSummaryStore.findByIds(any()) } returns summaries
            every {
                batchSummaryCompetitorStore.findBySummaryIds(listOf("s1", "s2"))
            } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s2", "c1")
            )

            val result = service.getTimeline(
                days = 30, competitorId = null, eventType = "funding"
            )

            result.items shouldHaveSize 1
            result.items[0].eventType shouldBe "funding"
        }

        @Test
        fun `sentiment 필터가 적용된다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "AlphaEd")
            )
            every { watchlistStore.findActive() } returns competitors
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1", "s2")

            val summaries = listOf(
                makeSummary("s1", "AlphaEd 긍정 뉴스", sentiment = "POSITIVE"),
                makeSummary("s2", "AlphaEd 부정 뉴스", sentiment = "NEGATIVE")
            )
            every { batchSummaryStore.findByIds(any()) } returns summaries
            every {
                batchSummaryCompetitorStore.findBySummaryIds(listOf("s1", "s2"))
            } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s2", "c1")
            )

            val result = service.getTimeline(
                days = 30, competitorId = null,
                eventType = null, sentiment = "NEGATIVE"
            )

            result.items shouldHaveSize 1
            result.items[0].sentiment shouldBe "NEGATIVE"
        }

        @Test
        fun `활성 경쟁사가 없으면 빈 타임라인을 반환한다`() {
            every { watchlistStore.findActive() } returns emptyList()

            val result = service.getTimeline(
                days = 30, competitorId = null, eventType = null
            )

            result.items.shouldBeEmpty()
        }

        @Test
        fun `유사한 제목의 중복 기사를 제거한다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "AlphaEd")
            )
            every { watchlistStore.findActive() } returns competitors
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1", "s2", "s3")

            val summaries = listOf(
                makeSummary("s1", "AlphaEd가 신규 교육 과정 발표", importanceScore = 0.9f),
                makeSummary("s2", "AlphaEd가 신규 교육 과정 발표함", importanceScore = 0.7f),
                makeSummary("s3", "AlphaEd 투자 유치 성공", importanceScore = 0.8f)
            )
            every { batchSummaryStore.findByIds(any()) } returns summaries
            every {
                batchSummaryCompetitorStore.findBySummaryIds(any())
            } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s2", "c1"),
                BatchSummaryCompetitor("s3", "c1")
            )

            val result = service.getTimeline(
                days = 30, competitorId = null, eventType = null
            )

            // 유사 제목 중복 제거 후 2개만 남아야 한다.
            result.items shouldHaveSize 2
        }

        @Test
        fun `limit 파라미터로 결과 수를 제한한다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "AlphaEd")
            )
            every { watchlistStore.findActive() } returns competitors

            val ids = (1..5).map { "s$it" }
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns ids

            val titles = listOf(
                "AlphaEd 신규 과정 출시",
                "AlphaEd 시리즈B 투자 유치 성공",
                "AlphaEd 글로벌 진출 전략 발표",
                "AlphaEd AI 교육 플랫폼 런칭",
                "AlphaEd 연간 매출 1000억 돌파"
            )
            val summaries = titles.mapIndexed { i, title ->
                makeSummary(
                    "s${i + 1}", title,
                    importanceScore = (i + 1) / 10f,
                    daysAgo = i.toLong()
                )
            }
            every { batchSummaryStore.findByIds(any()) } returns summaries
            every {
                batchSummaryCompetitorStore.findBySummaryIds(any())
            } returns ids.map { BatchSummaryCompetitor(it, "c1") }

            val result = service.getTimeline(
                days = 30, competitorId = null,
                eventType = null, limit = 3
            )

            result.items shouldHaveSize 3
        }

        @Test
        fun `days와 limit이 범위 밖이면 안전한 범위로 보정한다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "AlphaEd")
            )
            every { watchlistStore.findActive() } returns competitors
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1")
            every { batchSummaryStore.findByIds(any()) } returns listOf(
                makeSummary("s1", "AlphaEd 신규 과정 출시")
            )
            every {
                batchSummaryCompetitorStore.findBySummaryIds(any())
            } returns listOf(BatchSummaryCompetitor("s1", "c1"))

            val result = service.getTimeline(
                days = -30, competitorId = null,
                eventType = null, limit = -1
            )

            result.items shouldHaveSize 1
            result.items[0].competitorName shouldBe "AlphaEd"
        }
    }

    @Nested
    inner class `SOV 조회 (junction 기반)` {

        @Test
        fun `경쟁사별 점유율을 junction 테이블에서 계산한다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "AlphaEd"),
                makeCompetitor(id = "c2", name = "DeltaClass", aliases = listOf("DeltaClass"))
            )
            every { watchlistStore.findActive() } returns competitors
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1", "s2", "s3")

            // s1, s2 -> c1, s3 -> c2
            every {
                batchSummaryCompetitorStore.findBySummaryIds(listOf("s1", "s2", "s3"))
            } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s2", "c1"),
                BatchSummaryCompetitor("s3", "c2")
            )

            val result = service.getShareOfVoice(days = 30)

            result.totalArticles shouldBe 3
            result.shares shouldHaveSize 2
            val alphaed = result.shares.first { it.name == "AlphaEd" }
            alphaed.count shouldBe 2
            alphaed.share shouldBeGreaterThan 0.6
            val deltaclass = result.shares.first { it.name == "DeltaClass" }
            deltaclass.count shouldBe 1
        }

        @Test
        fun `활성 경쟁사가 없으면 빈 SOV를 반환한다`() {
            every { watchlistStore.findActive() } returns emptyList()

            val result = service.getShareOfVoice(days = 30)

            result.totalArticles shouldBe 0
            result.shares.shouldBeEmpty()
        }

        @Test
        fun `days가 범위 밖이면 안전한 기간으로 보정한다`() {
            every { watchlistStore.findActive() } returns emptyList()

            val result = service.getShareOfVoice(days = -30)

            result.period.from shouldBe today.toString()
            result.period.to shouldBe today.toString()
        }

        @Test
        fun `하나의 기사가 여러 경쟁사에 카운트된다`() {
            val competitors = listOf(
                makeCompetitor(id = "c1", name = "A사"),
                makeCompetitor(id = "c2", name = "B사")
            )
            every { watchlistStore.findActive() } returns competitors
            every {
                batchSummaryCompetitorStore.findSummaryIdsByCompetitorIds(
                    any(), any(), any(), any()
                )
            } returns listOf("s1")

            // s1이 c1과 c2 양쪽에 연결
            every {
                batchSummaryCompetitorStore.findBySummaryIds(listOf("s1"))
            } returns listOf(
                BatchSummaryCompetitor("s1", "c1"),
                BatchSummaryCompetitor("s1", "c2")
            )

            val result = service.getShareOfVoice(days = 30)

            result.totalArticles shouldBe 2
            result.shares shouldHaveSize 2
            result.shares[0].count shouldBe 1
            result.shares[1].count shouldBe 1
        }
    }

    @Nested
    inner class `키워드 프리뷰` {

        @Test
        fun `빈 키워드이면 안내 메시지를 반환한다`() {
            val result = service.previewKeywords(emptyList())

            result.items.shouldBeEmpty()
            result.message shouldContain "키워드를 입력"
        }

        @Test
        fun `RSS 수집 성공 시 프리뷰 항목을 반환한다`() {
            val rssItems = (1..7).map { i ->
                RssItem(
                    id = "rss-$i",
                    title = "기사 제목 $i",
                    link = "https://example.com/$i",
                    categoryId = "",
                    publishedAt = Instant.now().minus(i.toLong(), ChronoUnit.HOURS)
                )
            }
            every { rssFeedCollector.collectByUrl(any(), 72) } returns rssItems.map { it.toRssCollectedItem() }

            val result = service.previewKeywords(listOf("테스트", "키워드"))

            result.items shouldHaveSize 5 // 최대 5개
            result.message shouldContain "5건"
        }

        @Test
        fun `RSS 수집 실패 시 에러 메시지를 반환한다`() {
            every {
                rssFeedCollector.collectByUrl(any(), 72)
            } throws RuntimeException("Connection refused")

            val result = service.previewKeywords(listOf("테스트"))

            result.items.shouldBeEmpty()
            result.message shouldContain "실패"
        }

        @Test
        fun `RSS 수집 결과가 없으면 안내 메시지를 반환한다`() {
            every { rssFeedCollector.collectByUrl(any(), 72) } returns emptyList()

            val result = service.previewKeywords(listOf("테스트"))

            result.items.shouldBeEmpty()
            result.message shouldContain "관련 기사가 없습니다"
        }
    }

    @Nested
    inner class `입력 제약 검증` {

        @Test
        fun `경쟁사 이름이 100자 초과면 InvalidInputException을 던진다`() {
            every { watchlistStore.findAll() } returns emptyList()

            val ex = assertThrows<InvalidInputException> {
                service.create(
                    name = "a".repeat(101),
                    aliases = emptyList(),
                    excludeKeywords = emptyList(),
                    tier = "DIRECT",
                    rssFeeds = emptyList()
                )
            }
            ex.message.orEmpty() shouldContain "경쟁사 이름"
        }

        @Test
        fun `경쟁사 이름 100자 경계값은 허용한다`() {
            every { watchlistStore.findAll() } returns emptyList()
            val slotCompetitor = slot<CompetitorWatchlist>()
            every { watchlistStore.save(capture(slotCompetitor)) } answers {
                firstArg<CompetitorWatchlist>().copy(id = "comp-new")
            }

            service.create(
                name = "가".repeat(100),
                aliases = emptyList(),
                excludeKeywords = emptyList(),
                tier = "DIRECT",
                rssFeeds = emptyList()
            )

            slotCompetitor.captured.name shouldBe "가".repeat(100)
        }

        @Test
        fun `별칭이 10개 초과면 InvalidInputException을 던진다`() {
            every { watchlistStore.findAll() } returns emptyList()

            val aliases = (1..11).map { "별칭$it" }
            assertThrows<InvalidInputException> {
                service.create(
                    name = "회사",
                    aliases = aliases,
                    excludeKeywords = emptyList(),
                    tier = "DIRECT",
                    rssFeeds = emptyList()
                )
            }
        }

        @Test
        fun `별칭 1건이 60자 초과면 InvalidInputException을 던진다`() {
            every { watchlistStore.findAll() } returns emptyList()

            assertThrows<InvalidInputException> {
                service.create(
                    name = "회사",
                    aliases = listOf("a".repeat(61)),
                    excludeKeywords = emptyList(),
                    tier = "DIRECT",
                    rssFeeds = emptyList()
                )
            }
        }

        @Test
        fun `제외 키워드가 20개 초과면 InvalidInputException을 던진다`() {
            every { watchlistStore.findAll() } returns emptyList()

            val keywords = (1..21).map { "kw$it" }
            assertThrows<InvalidInputException> {
                service.create(
                    name = "회사",
                    aliases = emptyList(),
                    excludeKeywords = keywords,
                    tier = "DIRECT",
                    rssFeeds = emptyList()
                )
            }
        }
    }
}
