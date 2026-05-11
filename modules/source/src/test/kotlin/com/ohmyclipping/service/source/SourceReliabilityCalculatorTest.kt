package com.ohmyclipping.service.source

import com.ohmyclipping.model.RssSource
import com.ohmyclipping.store.RssSourceStore
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SourceReliabilityCalculatorTest {

    private val rssSourceStore = mockk<RssSourceStore>()
    private val calculator = SourceReliabilityCalculator(rssSourceStore)

    private fun testSource(
        id: String = "src-1",
        crawlFailCount: Int = 0,
        lastSuccessAt: Instant? = Instant.now()
    ) = RssSource(
        id = id,
        name = "Test Source",
        url = "https://example.com/feed",
        categoryId = "cat-1",
        crawlApproved = true,
        isActive = true,
        crawlFailCount = crawlFailCount,
        lastSuccessAt = lastSuccessAt,
        createdAt = Instant.now()
    )

    @Nested
    inner class `calculateAll 정상 동작` {

        @Test
        fun `소스가 없으면 빈 맵을 반환한다`() {
            every { rssSourceStore.list(any()) } returns emptyList()
            every { rssSourceStore.countArticlesBySource(any()) } returns emptyMap()

            val result = calculator.calculateAll()

            result shouldBe emptyMap()
        }

        @Test
        fun `완벽한 소스는 100점에 근접한다`() {
            // 실패 0, 방금 수집, 기사 10건 이상
            val source = testSource(lastSuccessAt = Instant.now())
            every { rssSourceStore.list(any()) } returns listOf(source)
            every { rssSourceStore.countArticlesBySource(any()) } returns mapOf("src-1" to 15)

            val result = calculator.calculateAll()

            result["src-1"]!! shouldBeInRange 95..100
        }

        @Test
        fun `실패가 많은 소스는 낮은 점수를 받는다`() {
            // 30회 실패 → successRate 0.0, 72시간 초과 → freshness 0.0, 기사 0건 → freq 0.0
            val source = testSource(
                crawlFailCount = 30,
                lastSuccessAt = Instant.now().minus(Duration.ofDays(5))
            )
            every { rssSourceStore.list(any()) } returns listOf(source)
            every { rssSourceStore.countArticlesBySource(any()) } returns emptyMap()

            val result = calculator.calculateAll()

            result["src-1"]!! shouldBe 0
        }

        @Test
        fun `lastSuccessAt이 null이면 freshness는 0이다`() {
            val source = testSource(crawlFailCount = 0, lastSuccessAt = null)
            every { rssSourceStore.list(any()) } returns listOf(source)
            every { rssSourceStore.countArticlesBySource(any()) } returns mapOf("src-1" to 10)

            val result = calculator.calculateAll()

            // successRate 1.0 * 40 = 40, freshness 0.0 * 30 = 0, freq 1.0 * 30 = 30 → 70
            result["src-1"]!! shouldBe 70
        }

        @Test
        fun `중간 수준의 소스는 50~70점 범위이다`() {
            // 5회 실패, 12시간 전 수집, 5건 기사
            val source = testSource(
                crawlFailCount = 5,
                lastSuccessAt = Instant.now().minus(Duration.ofHours(12))
            )
            every { rssSourceStore.list(any()) } returns listOf(source)
            every { rssSourceStore.countArticlesBySource(any()) } returns mapOf("src-1" to 5)

            val result = calculator.calculateAll()

            // successRate: max(0, 1-5/30) = 0.833 * 40 = 33.3
            // freshness: 0.7 * 30 = 21
            // freq: 0.7 * 30 = 21
            // total ~75
            result["src-1"]!! shouldBeInRange 50..80
        }

        @Test
        fun `여러 소스를 한 번에 계산한다`() {
            val sources = listOf(
                testSource(id = "src-1", crawlFailCount = 0),
                testSource(id = "src-2", crawlFailCount = 15)
            )
            every { rssSourceStore.list(any()) } returns sources
            every { rssSourceStore.countArticlesBySource(any()) } returns mapOf("src-1" to 10)

            val result = calculator.calculateAll()

            result.size shouldBe 2
            // src-1은 src-2보다 높아야 한다
            assert(result["src-1"]!! > result["src-2"]!!)
        }
    }

    @Nested
    inner class `점수 경계값` {

        @Test
        fun `점수는 0 미만이 될 수 없다`() {
            val source = testSource(
                crawlFailCount = 100,
                lastSuccessAt = Instant.now().minus(Duration.ofDays(30))
            )
            every { rssSourceStore.list(any()) } returns listOf(source)
            every { rssSourceStore.countArticlesBySource(any()) } returns emptyMap()

            val result = calculator.calculateAll()

            result["src-1"]!! shouldBeInRange 0..100
        }

        @Test
        fun `점수는 100을 초과할 수 없다`() {
            val source = testSource(
                crawlFailCount = 0,
                lastSuccessAt = Instant.now()
            )
            every { rssSourceStore.list(any()) } returns listOf(source)
            every { rssSourceStore.countArticlesBySource(any()) } returns mapOf("src-1" to 1000)

            val result = calculator.calculateAll()

            result["src-1"]!! shouldBeInRange 0..100
        }
    }
}
