package com.ohmyclipping.service.source

import com.ohmyclipping.service.port.OpsNotificationEvent
import com.ohmyclipping.model.RssSource
import com.ohmyclipping.service.port.RssCollectionPort
import com.ohmyclipping.service.port.SourceOpsNotificationPort
import com.ohmyclipping.service.port.SourceSchedulerMetricsPort
import com.ohmyclipping.store.RssSourceStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class SourceHealthSchedulerTest {

    private val rssSourceStore = mockk<RssSourceStore>(relaxed = true)
    private val rssFeedCollector = mockk<RssCollectionPort>()
    private val notificationService = mockk<SourceOpsNotificationPort>(relaxed = true)
    private val metrics = mockk<SourceSchedulerMetricsPort>(relaxed = true) {
        every { recordSourceSchedulerRun(any<String>(), any<() -> Any?>()) } answers {
            secondArg<() -> Any?>().invoke()
        }
    }
    private val reliabilityCalculator = mockk<SourceReliabilityCalculator>(relaxed = true)

    private val crawlLogStore = mockk<com.ohmyclipping.store.SourceCrawlLogStore>(relaxed = true)

    private val scheduler = SourceHealthScheduler(
        rssSourceStore, rssFeedCollector, notificationService, metrics, reliabilityCalculator, crawlLogStore
    )

    private fun testSource(
        id: String = "src-1",
        name: String = "Test Source",
        url: String = "https://example.com/feed",
        failCount: Int = 10,
        isActive: Boolean = true
    ) = RssSource(
        id = id,
        name = name,
        url = url,
        categoryId = "cat-1",
        crawlApproved = true,
        isActive = isActive,
        crawlFailCount = failCount,
        createdAt = Instant.now()
    )

    @Nested
    inner class `deactivateFailedSources 정상 동작` {

        @Test
        fun `연속 실패 소스가 없으면 아무것도 하지 않는다`() {
            every { rssSourceStore.findFailedSources(any()) } returns emptyList()

            scheduler.deactivateFailedSources()

            verify(exactly = 0) { rssSourceStore.deactivate(any()) }
            verify(exactly = 0) { notificationService.sendOps(any(), any(), any()) }
        }

        @Test
        fun `임계값 이상 실패한 소스를 비활성화하고 알림을 전송한다`() {
            val source = testSource(failCount = 12)
            every { rssSourceStore.findFailedSources(10) } returns listOf(source)

            scheduler.deactivateFailedSources()

            verify(exactly = 1) { rssSourceStore.deactivate("src-1") }
            verify(exactly = 1) { notificationService.sendOps(eq(OpsNotificationEvent.SOURCE_AUTO_DISABLED), match { it.contains("자동 비활성화") }, any()) }
        }

        @Test
        fun `여러 소스가 실패하면 각각 비활성화한다`() {
            val sources = listOf(
                testSource(id = "src-1", name = "Source A"),
                testSource(id = "src-2", name = "Source B")
            )
            every { rssSourceStore.findFailedSources(10) } returns sources

            scheduler.deactivateFailedSources()

            verify(exactly = 1) { rssSourceStore.deactivate("src-1") }
            verify(exactly = 1) { rssSourceStore.deactivate("src-2") }
        }
    }

    @Nested
    inner class `retryDeactivatedSources 정상 동작` {

        @Test
        fun `비활성화된 소스가 없으면 아무것도 하지 않는다`() {
            every { rssSourceStore.findDeactivated() } returns emptyList()

            scheduler.retryDeactivatedSources()

            verify(exactly = 0) { rssFeedCollector.collect(any(), any()) }
        }

        @Test
        fun `수집 성공 시 실패 카운트를 초기화하고 재활성화한다`() {
            val source = testSource(isActive = false)
            every { rssSourceStore.findDeactivated() } returns listOf(source)
            every { rssFeedCollector.collect(match { it.id == source.id && it.url == source.url }, 1) } returns emptyList()

            scheduler.retryDeactivatedSources()

            verify(exactly = 1) { rssSourceStore.resetFailCount("src-1") }
            verify(exactly = 1) { rssSourceStore.reactivate("src-1") }
            verify(exactly = 1) { notificationService.sendOps(eq(OpsNotificationEvent.SOURCE_RETRY_RESULT), match { it.contains("복구") }, any()) }
        }

        @Test
        fun `수집 실패 시 재활성화하지 않는다`() {
            val source = testSource(isActive = false)
            every { rssSourceStore.findDeactivated() } returns listOf(source)
            every {
                rssFeedCollector.collect(match { it.id == source.id && it.url == source.url }, 1)
            } throws RuntimeException("Feed unavailable")

            scheduler.retryDeactivatedSources()

            verify(exactly = 0) { rssSourceStore.resetFailCount(any()) }
            verify(exactly = 0) { rssSourceStore.reactivate(any()) }
            // 복구 실패 시에는 알림을 보내지 않는다 (0건 복구)
            verify(exactly = 0) { notificationService.sendOps(any(), any(), any()) }
        }
    }

    @Nested
    inner class `updateReliabilityScores 정상 동작` {

        @Test
        fun `계산된 점수를 스토어에 저장한다`() {
            val scores = mapOf("src-1" to 85, "src-2" to 42)
            every { reliabilityCalculator.calculateAll() } returns scores

            scheduler.updateReliabilityScores()

            verify(exactly = 1) { rssSourceStore.updateReliabilityScores(scores) }
        }

        @Test
        fun `계산 결과가 비어있으면 스토어를 호출하지 않는다`() {
            every { reliabilityCalculator.calculateAll() } returns emptyMap()

            scheduler.updateReliabilityScores()

            verify(exactly = 0) { rssSourceStore.updateReliabilityScores(any()) }
        }

        @Test
        fun `계산 실패 시 예외를 전파하지 않는다`() {
            every { reliabilityCalculator.calculateAll() } throws RuntimeException("calc error")

            scheduler.updateReliabilityScores()

            verify(exactly = 0) { rssSourceStore.updateReliabilityScores(any()) }
        }
    }

    @Nested
    inner class `에러 격리` {

        @Test
        fun `findFailedSources 실패 시 예외를 전파하지 않는다`() {
            every { rssSourceStore.findFailedSources(any()) } throws RuntimeException("DB error")

            // 예외 없이 완료되어야 한다
            scheduler.deactivateFailedSources()
        }

        @Test
        fun `deactivate 실패 시 다음 소스 처리를 계속한다`() {
            val sources = listOf(
                testSource(id = "src-1"),
                testSource(id = "src-2")
            )
            every { rssSourceStore.findFailedSources(10) } returns sources
            every { rssSourceStore.deactivate("src-1") } throws RuntimeException("DB error")

            scheduler.deactivateFailedSources()

            // src-1 실패해도 src-2는 시도한다
            verify(exactly = 1) { rssSourceStore.deactivate("src-2") }
        }
    }
}
