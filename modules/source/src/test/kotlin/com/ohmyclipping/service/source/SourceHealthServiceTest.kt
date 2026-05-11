package com.ohmyclipping.service.source

import com.ohmyclipping.model.RssSource
import com.ohmyclipping.store.RssSourceStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SourceHealthServiceTest {
    private val fixedNow = Instant.parse("2026-04-08T12:00:00Z")
    private val clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val store = mockk<RssSourceStore>()
    private val service = SourceHealthService(store, clock)

    private fun source(
        id: String = "src-1",
        name: String = "TechCrunch",
        lastSuccessAt: Instant? = fixedNow,
        crawlFailCount: Int = 0,
        isActive: Boolean = true,
    ): RssSource = RssSource(
        id = id,
        name = name,
        url = "https://example.com/rss",
        categoryId = "cat-1",
        lastSuccessAt = lastSuccessAt,
        crawlFailCount = crawlFailCount,
        isActive = isActive,
    )

    @Nested
    inner class `getHealth 메서드` {
        @Test
        fun `모든 소스가 정상이면 healthyCount는 전체와 같고 unhealthy는 비어있다`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", lastSuccessAt = fixedNow.minusSeconds(3600)),
                source(id = "2", lastSuccessAt = fixedNow.minusSeconds(7200)),
            )

            val result = service.getHealth()

            result.totalCount shouldBe 2
            result.healthyCount shouldBe 2
            result.unhealthy.size shouldBe 0
        }

        @Test
        fun `lastSuccessAt이 24시간 이전이면 unhealthy로 분류한다`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", name = "Stale", lastSuccessAt = fixedNow.minusSeconds(86400 + 1)),
                source(id = "2", name = "Fresh", lastSuccessAt = fixedNow.minusSeconds(3600)),
            )

            val result = service.getHealth()

            result.totalCount shouldBe 2
            result.healthyCount shouldBe 1
            result.unhealthy.size shouldBe 1
            result.unhealthy[0].name shouldBe "Stale"
        }

        @Test
        fun `lastSuccessAt이 null이어도 unhealthy로 분류한다`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", name = "Never", lastSuccessAt = null),
            )

            val result = service.getHealth()

            result.unhealthy.size shouldBe 1
            result.unhealthy[0].name shouldBe "Never"
        }

        @Test
        fun `crawlFailCount가 3 이상이면 unhealthy로 분류한다`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", name = "Failing", crawlFailCount = 3),
                source(id = "2", name = "OK", crawlFailCount = 2),
            )

            val result = service.getHealth()

            result.healthyCount shouldBe 1
            result.unhealthy.size shouldBe 1
            result.unhealthy[0].name shouldBe "Failing"
        }

        @Test
        fun `unhealthy 리스트는 lastSuccessAt 오래된 순으로 정렬된다`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", name = "OneDay", lastSuccessAt = fixedNow.minusSeconds(86401)),
                source(id = "2", name = "ThreeDays", lastSuccessAt = fixedNow.minusSeconds(259201)),
                source(id = "3", name = "TwoDays", lastSuccessAt = fixedNow.minusSeconds(172801)),
            )

            val result = service.getHealth()

            result.unhealthy.size shouldBe 3
            result.unhealthy[0].name shouldBe "ThreeDays"
            result.unhealthy[1].name shouldBe "TwoDays"
            result.unhealthy[2].name shouldBe "OneDay"
        }

        @Test
        fun `unhealthy는 표시 최대 5건으로 제한되지만 healthyCount는 정확히 계산된다`() {
            val staleSources = (1..7).map {
                source(id = "$it", name = "Source$it", lastSuccessAt = fixedNow.minusSeconds(86400L + it * 100))
            }
            every { store.list(any()) } returns staleSources

            val result = service.getHealth()

            result.totalCount shouldBe 7
            result.healthyCount shouldBe 0
            result.unhealthy.size shouldBe 5
        }

        @Test
        fun `unhealthy reason은 시간 기준일 때 N일째 미수신을 포함한다`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", lastSuccessAt = fixedNow.minusSeconds(86400 * 3 + 100)),
            )

            val result = service.getHealth()

            result.unhealthy[0].reason shouldBe "3일째 미수신"
        }

        @Test
        fun `unhealthy reason은 lastSuccessAt이 null일 때 한 번도 수신되지 않음 표시`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", lastSuccessAt = null),
            )

            val result = service.getHealth()

            result.unhealthy[0].reason shouldBe "한 번도 수신되지 않음"
        }

        @Test
        fun `unhealthy reason은 실패 카운트가 큰 경우 연속 N회 실패 표시`() {
            every { store.list(any()) } returns listOf(
                source(
                    id = "1",
                    lastSuccessAt = fixedNow.minusSeconds(3600), // 시간은 정상
                    crawlFailCount = 5
                ),
            )

            val result = service.getHealth()

            result.unhealthy[0].reason shouldBe "연속 5회 실패"
        }

        @Test
        fun `비활성 소스는 집계에서 제외된다`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", isActive = true),
                source(id = "2", isActive = false, lastSuccessAt = null),
            )

            val result = service.getHealth()

            result.totalCount shouldBe 1
            result.healthyCount shouldBe 1
            result.unhealthy.size shouldBe 0
        }
    }

    @Nested
    inner class `staleHours override 지원` {

        @Test
        fun `staleHours 를 6 으로 주면 6시간 전 수신 소스도 불건강으로 본다`() {
            // 7시간 전 수신한 소스는 24시간 기본 기준으로는 건강하지만,
            // staleHours=6 을 주면 불건강으로 분류되어야 한다.
            every { store.list(any()) } returns listOf(
                source(id = "1", name = "SlightlyStale", lastSuccessAt = fixedNow.minusSeconds(7 * 3600)),
                source(id = "2", name = "Fresh", lastSuccessAt = fixedNow.minusSeconds(3600)),
            )

            val defaultResult = service.getHealth()
            defaultResult.unhealthy.size shouldBe 0

            val tightResult = service.getHealth(staleHours = 6)
            tightResult.unhealthy.size shouldBe 1
            tightResult.unhealthy[0].name shouldBe "SlightlyStale"
        }

        @Test
        fun `staleHours 가 0 이하이면 기본 24시간 임계값으로 폴백한다`() {
            // 7시간 전 수신 → 24시간 기본 기준에서는 건강하다.
            every { store.list(any()) } returns listOf(
                source(id = "1", lastSuccessAt = fixedNow.minusSeconds(7 * 3600)),
            )

            val result = service.getHealth(staleHours = 0)

            result.unhealthy.size shouldBe 0
        }

        @Test
        fun `staleHours 기준에 걸린 소스의 reason 은 시간 단위로 표시된다`() {
            every { store.list(any()) } returns listOf(
                source(id = "1", lastSuccessAt = fixedNow.minusSeconds(9 * 3600)),
            )

            val result = service.getHealth(staleHours = 6)

            result.unhealthy[0].reason shouldBe "9시간째 미수신"
        }
    }
}
