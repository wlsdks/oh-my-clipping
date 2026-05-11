package com.clipping.mcpserver.service

import com.clipping.mcpserver.store.ArticleEventRow
import com.clipping.mcpserver.store.ArticleMetadataRow
import com.clipping.mcpserver.store.DailyCount
import com.clipping.mcpserver.store.DeliveryLogStore
import com.clipping.mcpserver.store.PersonaStore
import com.clipping.mcpserver.store.WizardStepRow
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AnalyticsServiceTest {

    private val userEventService = mockk<UserEventService>(relaxed = true)
    private val personaStore = mockk<PersonaStore>(relaxed = true)
    private val deliveryLogStore = mockk<DeliveryLogStore>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val service = AnalyticsService(userEventService, personaStore, objectMapper, deliveryLogStore)

    private val from = Instant.parse("2026-03-01T00:00:00Z")
    private val to = Instant.parse("2026-03-08T00:00:00Z")

    @Nested
    inner class `DAU 조회` {

        @Test
        fun `dailyActiveUsers 데이터를 DauResponse로 매핑한다`() {
            every { userEventService.dailyActiveUsers(from, to) } returns listOf(
                DailyCount("2026-03-01", 5),
                DailyCount("2026-03-02", 8)
            )

            val result = service.getDau(from, to)

            result.data shouldHaveSize 2
            result.data[0].date shouldBe "2026-03-01"
            result.data[0].count shouldBe 5
            result.data[1].date shouldBe "2026-03-02"
            result.data[1].count shouldBe 8
            verify(exactly = 1) { userEventService.dailyActiveUsers(from, to) }
        }

        @Test
        fun `데이터가 없으면 빈 리스트를 반환한다`() {
            every { userEventService.dailyActiveUsers(from, to) } returns emptyList()

            val result = service.getDau(from, to)

            result.data.shouldBeEmpty()
        }
    }

    @Nested
    inner class `위자드 퍼널 분석` {

        @Test
        fun `wizard_step 이벤트에서 퍼널 데이터를 집계한다`() {
            every { userEventService.findWizardStepEvents(from, to) } returns listOf(
                WizardStepRow("""{"step":"topic","action":"enter"}""", "u1"),
                WizardStepRow("""{"step":"topic","action":"complete"}""", "u1"),
                WizardStepRow("""{"step":"topic","action":"enter"}""", "u2"),
                WizardStepRow("""{"step":"source","action":"enter"}""", "u1"),
                WizardStepRow("""{"step":"source","action":"complete"}""", "u1")
            )

            val result = service.getWizardFunnel(from, to)

            result.data shouldHaveSize 2
            // topic 단계: enter 2, complete 1 -> dropRate 50%
            val topic = result.data.first { it.step == "topic" }
            topic.enters shouldBe 2
            topic.completes shouldBe 1
            topic.dropRate shouldBe 50.0
            // source 단계: enter 1, complete 1 -> dropRate 0%
            val source = result.data.first { it.step == "source" }
            source.enters shouldBe 1
            source.completes shouldBe 1
            source.dropRate shouldBe 0.0
        }

        @Test
        fun `event_data가 null이면 해당 행을 건너뛴다`() {
            every { userEventService.findWizardStepEvents(from, to) } returns listOf(
                WizardStepRow(null, "u1"),
                WizardStepRow("""{"step":"topic","action":"enter"}""", "u2")
            )

            val result = service.getWizardFunnel(from, to)

            result.data shouldHaveSize 1
            result.data[0].step shouldBe "topic"
            result.data[0].enters shouldBe 1
        }

        @Test
        fun `이벤트가 없으면 빈 리스트를 반환한다`() {
            every { userEventService.findWizardStepEvents(from, to) } returns emptyList()

            val result = service.getWizardFunnel(from, to)

            result.data.shouldBeEmpty()
        }
    }

    @Nested
    inner class `기사 랭킹 조회` {

        @Test
        fun `클릭 수 기준으로 정렬된 기사 랭킹을 반환한다`() {
            // 기사 2건: s1(클릭 2, 노출 4), s2(클릭 1, 노출 2)
            every { userEventService.findArticleEvents(from, to) } returns listOf(
                ArticleEventRow("article_impression", """{"summaryId":"s1","title":"뉴스A"}""", "u1"),
                ArticleEventRow("article_impression", """{"summaryId":"s1","title":"뉴스A"}""", "u2"),
                ArticleEventRow("article_impression", """{"summaryId":"s1","title":"뉴스A"}""", "u3"),
                ArticleEventRow("article_impression", """{"summaryId":"s1","title":"뉴스A"}""", "u4"),
                ArticleEventRow("article_click", """{"summaryId":"s1","title":"뉴스A"}""", "u1"),
                ArticleEventRow("article_click", """{"summaryId":"s1","title":"뉴스A"}""", "u2"),
                ArticleEventRow("article_impression", """{"summaryId":"s2","title":"뉴스B"}""", "u1"),
                ArticleEventRow("article_impression", """{"summaryId":"s2","title":"뉴스B"}""", "u2"),
                ArticleEventRow("article_click", """{"summaryId":"s2","title":"뉴스B"}""", "u1")
            )
            every { userEventService.findArticleMetadata(any()) } returns listOf(
                ArticleMetadataRow("s1", "뉴스A", "cat1", "경제", "Example Daily", "2026-03-05T00:00:00Z"),
                ArticleMetadataRow("s2", "뉴스B", "cat2", "IT", "Example Press", "2026-03-06T00:00:00Z")
            )
            every { userEventService.countBookmarksBySummaryIds(any()) } returns mapOf(
                "s1" to 3L, "s2" to 1L
            )

            val result = service.getArticleRanking(from, to, "clicks", 20)

            result.data shouldHaveSize 2
            result.data[0].rank shouldBe 1
            result.data[0].summaryId shouldBe "s1"
            result.data[0].clicks shouldBe 2
            result.data[0].impressions shouldBe 4
            result.data[0].ctr shouldBe 50.0
            result.data[0].categoryName shouldBe "경제"
            result.data[0].sourceName shouldBe "Example Daily"
            result.data[0].bookmarks shouldBe 3
            result.data[1].rank shouldBe 2
            result.data[1].summaryId shouldBe "s2"
            result.data[1].clicks shouldBe 1
            result.data[1].bookmarks shouldBe 1
        }

        @Test
        fun `북마크 기준으로 정렬할 수 있다`() {
            every { userEventService.findArticleEvents(from, to) } returns listOf(
                ArticleEventRow("article_click", """{"summaryId":"s1","title":"뉴스A"}""", "u1"),
                ArticleEventRow("article_click", """{"summaryId":"s2","title":"뉴스B"}""", "u1"),
                ArticleEventRow("article_click", """{"summaryId":"s2","title":"뉴스B"}""", "u2")
            )
            every { userEventService.findArticleMetadata(any()) } returns listOf(
                ArticleMetadataRow("s1", "뉴스A", "cat1", "경제", "Example Daily", null),
                ArticleMetadataRow("s2", "뉴스B", "cat2", "IT", "Example Press", null)
            )
            every { userEventService.countBookmarksBySummaryIds(any()) } returns mapOf(
                "s1" to 5L, "s2" to 2L
            )

            val result = service.getArticleRanking(from, to, "bookmarks", 20)

            result.data shouldHaveSize 2
            result.data[0].rank shouldBe 1
            result.data[0].summaryId shouldBe "s1"
            result.data[0].bookmarks shouldBe 5
            result.data[1].rank shouldBe 2
            result.data[1].summaryId shouldBe "s2"
            result.data[1].bookmarks shouldBe 2
        }

        @Test
        fun `메타데이터 제목이 없으면 이벤트의 첫 제목을 fallback으로 사용한다`() {
            every { userEventService.findArticleEvents(from, to) } returns listOf(
                ArticleEventRow("article_impression", """{"summaryId":"s1","title":"이벤트 제목"}""", "u1"),
                ArticleEventRow("article_click", """{"summaryId":"s1","title":"다른 제목"}""", "u2")
            )
            every { userEventService.findArticleMetadata(any()) } returns emptyList()
            every { userEventService.countBookmarksBySummaryIds(any()) } returns emptyMap()

            val result = service.getArticleRanking(from, to, "clicks", 20)

            result.data shouldHaveSize 1
            result.data[0].summaryId shouldBe "s1"
            result.data[0].title shouldBe "이벤트 제목"
        }

        @Test
        fun `이벤트가 없으면 빈 리스트를 반환한다`() {
            every { userEventService.findArticleEvents(from, to) } returns emptyList()

            val result = service.getArticleRanking(from, to, "clicks", 20)

            result.data.shouldBeEmpty()
        }
    }

    @Nested
    inner class `카테고리 통계 조회` {

        @Test
        fun `2개 카테고리의 클릭, 노출, CTR, 점유율을 정확히 계산한다`() {
            every { userEventService.findArticleEvents(from, to) } returns listOf(
                ArticleEventRow("article_impression", """{"summaryId":"s1"}""", "u1"),
                ArticleEventRow("article_impression", """{"summaryId":"s1"}""", "u2"),
                ArticleEventRow("article_impression", """{"summaryId":"s1"}""", "u3"),
                ArticleEventRow("article_impression", """{"summaryId":"s1"}""", "u4"),
                ArticleEventRow("article_impression", """{"summaryId":"s1"}""", "u5"),
                ArticleEventRow("article_impression", """{"summaryId":"s1"}""", "u6"),
                ArticleEventRow("article_click", """{"summaryId":"s1"}""", "u1"),
                ArticleEventRow("article_click", """{"summaryId":"s1"}""", "u2"),
                ArticleEventRow("article_click", """{"summaryId":"s1"}""", "u3"),
                ArticleEventRow("article_impression", """{"summaryId":"s2"}""", "u1"),
                ArticleEventRow("article_impression", """{"summaryId":"s2"}""", "u2"),
                ArticleEventRow("article_impression", """{"summaryId":"s2"}""", "u3"),
                ArticleEventRow("article_impression", """{"summaryId":"s2"}""", "u4"),
                ArticleEventRow("article_click", """{"summaryId":"s2"}""", "u1"),
                ArticleEventRow("article_click", """{"summaryId":"s2"}""", "u2"),
                ArticleEventRow("article_impression", """{"summaryId":"s3"}""", "u1"),
                ArticleEventRow("article_impression", """{"summaryId":"s3"}""", "u2"),
                ArticleEventRow("article_click", """{"summaryId":"s3"}""", "u1")
            )
            every { userEventService.findArticleMetadata(any()) } returns listOf(
                ArticleMetadataRow("s1", "뉴스A", "cat1", "경제", "Example Daily", null),
                ArticleMetadataRow("s2", "뉴스B", "cat2", "IT", "Example Press", null),
                ArticleMetadataRow("s3", "뉴스C", "cat1", "경제", "Example Business Daily", null)
            )

            val result = service.getCategoryStats(from, to)

            result.data shouldHaveSize 2
            val econ = result.data.first { it.categoryId == "cat1" }
            econ.categoryName shouldBe "경제"
            econ.clicks shouldBe 4
            econ.impressions shouldBe 8
            econ.ctr shouldBe 50.0
            econ.sharePercent shouldBe 66.67
            val it = result.data.first { it.categoryId == "cat2" }
            it.categoryName shouldBe "IT"
            it.clicks shouldBe 2
            it.impressions shouldBe 4
            it.ctr shouldBe 50.0
            it.sharePercent shouldBe 33.33
            result.data[0].categoryId shouldBe "cat1"
            result.data[1].categoryId shouldBe "cat2"
        }

        @Test
        fun `이벤트가 없으면 빈 리스트를 반환한다`() {
            every { userEventService.findArticleEvents(from, to) } returns emptyList()

            val result = service.getCategoryStats(from, to)

            result.data.shouldBeEmpty()
        }

        @Test
        fun `단일 카테고리면 점유율이 100퍼센트이다`() {
            every { userEventService.findArticleEvents(from, to) } returns listOf(
                ArticleEventRow("article_impression", """{"summaryId":"s1"}""", "u1"),
                ArticleEventRow("article_impression", """{"summaryId":"s1"}""", "u2"),
                ArticleEventRow("article_click", """{"summaryId":"s1"}""", "u1")
            )
            every { userEventService.findArticleMetadata(any()) } returns listOf(
                ArticleMetadataRow("s1", "뉴스A", "cat1", "경제", "Example Daily", null)
            )

            val result = service.getCategoryStats(from, to)

            result.data shouldHaveSize 1
            result.data[0].categoryId shouldBe "cat1"
            result.data[0].categoryName shouldBe "경제"
            result.data[0].clicks shouldBe 1
            result.data[0].impressions shouldBe 2
            result.data[0].ctr shouldBe 50.0
            result.data[0].sharePercent shouldBe 100.0
        }
    }

    @Nested
    inner class `클릭률 일별 범위 조회` {

        @Test
        fun `여러 날짜의 클릭률을 범위 집계 결과로 계산한다`() {
            val day1 = java.time.LocalDate.of(2026, 3, 1)
            val day2 = java.time.LocalDate.of(2026, 3, 2)
            every {
                userEventService.countByEventTypeForDays("article_click", listOf(day1, day2))
            } returns mapOf(day1 to 2L, day2 to 1L)
            every { deliveryLogStore.dailyStats(day1, day2) } returns listOf(
                DeliveryLogStore.DailyStat(day1, sent = 4, failed = 0, skipped = 0),
                DeliveryLogStore.DailyStat(day2, sent = 0, failed = 1, skipped = 0)
            )

            val result = service.getClickRatesForDays(listOf(day1, day2))

            result[day1] shouldBe 50.0
            result[day2] shouldBe 0.0
            verify(exactly = 1) { userEventService.countByEventTypeForDays("article_click", listOf(day1, day2)) }
            verify(exactly = 1) { deliveryLogStore.dailyStats(day1, day2) }
        }
    }
}
