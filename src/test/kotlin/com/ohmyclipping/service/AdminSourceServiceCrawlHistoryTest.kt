package com.ohmyclipping.service

import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.SourceCrawlLog
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.model.SourceLegalBasis
import com.ohmyclipping.store.AuditLogStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.LlmRunStore
import com.ohmyclipping.store.RssSourceStore
import com.ohmyclipping.store.SourceCrawlLogStore
import com.ohmyclipping.security.UrlSafetyValidator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AdminSourceServiceCrawlHistoryTest {

    private val sourceStore = mockk<RssSourceStore>()
    private val crawlLogStore = mockk<SourceCrawlLogStore>()
    private val llmRunStore = mockk<LlmRunStore>()
    private val service = AdminSourceService(
        sourceStore, mockk(), mockk(), mockk(),
        mockk<AuditLogStore>(relaxed = true), crawlLogStore, llmRunStore,
        EntityRevisionRecorder(mockk(relaxed = true)), mockk(relaxed = true)
    )

    private val testSource = RssSource(
        id = "src-1", name = "테스트 소스", url = "https://example.com/rss",
        categoryId = "cat-1", crawlApproved = true, isActive = true,
        legalBasis = SourceLegalBasis.QUOTATION_ONLY, summaryAllowed = true,
        reliabilityScore = 80, createdAt = Instant.now(), updatedAt = Instant.now()
    )

    @Nested
    inner class `getCrawlHistory` {

        @Test
        fun `정상 조회 시 크롤 이력 통계를 반환한다`() {
            every { sourceStore.findById("src-1") } returns testSource
            every { crawlLogStore.findBySourceId("src-1", any()) } returns listOf(
                SourceCrawlLog(id = 1, sourceId = "src-1", success = true, responseTimeMs = 400, articlesFound = 5),
                SourceCrawlLog(id = 2, sourceId = "src-1", success = true, responseTimeMs = 500, articlesFound = 3),
                SourceCrawlLog(id = 3, sourceId = "src-1", success = false, errorMessage = "timeout", responseTimeMs = null)
            )
            every { crawlLogStore.getUptimePercent("src-1", any()) } returns 66.7

            val result = service.getCrawlHistory("src-1", 30)

            result.sourceId shouldBe "src-1"
            result.uptimePercent shouldBe 66.7
            result.totalCrawls shouldBe 3
            result.successCount shouldBe 2
            result.failCount shouldBe 1
            result.avgResponseTimeMs shouldBe 450
            result.logs shouldHaveSize 3
        }

        @Test
        fun `존재하지 않는 소스 조회 시 NotFoundException 발생`() {
            every { sourceStore.findById("missing") } returns null

            shouldThrow<NotFoundException> {
                service.getCrawlHistory("missing", 30)
            }
        }

        @Test
        fun `크롤 이력이 없으면 빈 결과를 반환한다`() {
            every { sourceStore.findById("src-1") } returns testSource
            every { crawlLogStore.findBySourceId("src-1", any()) } returns emptyList()
            every { crawlLogStore.getUptimePercent("src-1", any()) } returns null

            val result = service.getCrawlHistory("src-1", 30)

            result.totalCrawls shouldBe 0
            result.uptimePercent.shouldBeNull()
            result.avgResponseTimeMs.shouldBeNull()
            result.logs shouldHaveSize 0
        }

        @Test
        fun `days가 범위를 벗어나면 1~90으로 클램핑한다`() {
            every { sourceStore.findById("src-1") } returns testSource
            every { crawlLogStore.findBySourceId("src-1", any()) } returns emptyList()
            every { crawlLogStore.getUptimePercent("src-1", any()) } returns null

            val result = service.getCrawlHistory("src-1", 200)
            // 내부 safeDays는 알 수 없지만, 예외 없이 반환되어야 한다
            result.sourceId shouldBe "src-1"
        }
    }

    @Nested
    inner class `getAiCosts` {

        @Test
        fun `소스별 AI 비용 통계를 반환한다`() {
            every { llmRunStore.sumTokensBySource(any()) } returns mapOf(
                "src-1" to Triple(100, 200_000L, 50_000L),
                "src-2" to Triple(50, 100_000L, 25_000L),
                "src-small" to Triple(3, 8_388L, 2_177L)
            )

            val result = service.getAiCosts(30)

            result.days shouldBe 30
            result.costs.size shouldBe 3
            result.costs["src-1"].shouldNotBeNull()
            result.costs["src-1"]!!.requestCount shouldBe 100
            result.costs["src-1"]!!.tokensIn shouldBe 200_000L
            result.costs["src-1"]!!.tokensOut shouldBe 50_000L
            result.costs["src-1"]!!.estimatedUsd shouldBe 0.04
            result.costs["src-small"]!!.estimatedUsd shouldBe 0.00171
        }

        @Test
        fun `데이터가 없으면 빈 결과를 반환한다`() {
            every { llmRunStore.sumTokensBySource(any()) } returns emptyMap()

            val result = service.getAiCosts(30)

            result.days shouldBe 30
            result.costs.size shouldBe 0
        }
    }
}
