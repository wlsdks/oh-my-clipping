package com.ohmyclipping.stress

import com.ohmyclipping.service.notification.OperationsNotificationService
import com.ohmyclipping.config.ClippingMcpServerProperties
import com.ohmyclipping.model.*
import com.ohmyclipping.service.dto.clipping.*
import com.ohmyclipping.observability.ClippingMetrics
import com.ohmyclipping.resilience.TokenBucketRateLimiter
import com.ohmyclipping.service.*
import com.ohmyclipping.service.port.ImportanceScreeningResult
import com.ohmyclipping.service.port.LlmArticleSummaryResult
import com.ohmyclipping.service.port.LlmSummarizationPort
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.LlmRunStore
import com.ohmyclipping.store.RssItemStore
import com.ohmyclipping.store.SummaryCacheStore
import com.ohmyclipping.store.SummaryEnrichmentStore
import com.ohmyclipping.support.TestSleeper
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 파이프라인 복원력 스트레스 테스트.
 * Gemini/Slack 호출 없이 mock 기반으로 5가지 장애 시나리오에서
 * delivery rate(성공 + fallback) / 전체를 검증한다.
 */
@Tag("stress")
class PipelineStressTest {

    // ──────────────────────────────────────────────────────────────
    // Scenario 1 — Normal (1500 articles, 5% error)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `시나리오 1 — 정상 부하 1500건, 5퍼센트 에러율에서 delivery rate 99퍼센트 이상`() {
        val totalItems = 1500
        val errorRate = 0.05
        val service = buildService(errorRate = errorRate, delayRange = 1L..3L)

        val results = runParallel(service, totalItems, threadCount = 30)

        val deliveryRate = results.count { it.success }.toDouble() / totalItems
        deliveryRate shouldBeGreaterThanOrEqual 0.99
    }

    // ──────────────────────────────────────────────────────────────
    // Scenario 2 — Gemini full failure (100% error, 200 articles)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `시나리오 2 — Gemini 전면 장애 200건, delivery rate 100퍼센트, 모두 fallback`() {
        val totalItems = 200
        val service = buildService(errorRate = 1.0, delayRange = 1L..2L)

        val results = runParallel(service, totalItems, threadCount = 30)

        val deliveryRate = results.count { it.success }.toDouble() / totalItems
        deliveryRate shouldBe 1.0
        results.all { it.isFallback } shouldBe true
    }

    // ──────────────────────────────────────────────────────────────
    // Scenario 3 — Gemini intermittent failure (30% error, 500 articles)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `시나리오 3 — Gemini 간헐 장애 500건, 30퍼센트 에러, delivery rate 99퍼센트 이상`() {
        val totalItems = 500
        val service = buildService(errorRate = 0.30, delayRange = 1L..3L)

        val results = runParallel(service, totalItems, threadCount = 30)

        val deliveryRate = results.count { it.success }.toDouble() / totalItems
        deliveryRate shouldBeGreaterThanOrEqual 0.99
    }

    // ──────────────────────────────────────────────────────────────
    // Scenario 4 — Gemini recovery (100% error for first 50, then 0%)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `시나리오 4 — Gemini 복구 시나리오, 첫 50건 100퍼센트 실패 후 0퍼센트 에러로 전환`() {
        val totalItems = 200
        val recoveryPoint = 50
        // shouldFail 플래그로 mock 동작을 동적으로 전환한다
        val shouldFail = java.util.concurrent.atomic.AtomicBoolean(true)

        val service = buildServiceWithDynamicFailure(
            shouldFail = shouldFail,
            delayRange = 1L..2L
        )

        val category = testCategory()
        val results = mutableListOf<ItemSummarizationResult>()

        // Phase 1: 첫 50건은 Gemini가 실패한다.
        // 서킷 브레이커(failureThreshold=15)가 OPEN되므로 이후 호출은 즉시 fallback.
        for (i in 0 until recoveryPoint) {
            results.add(service.summarizeSingleItem(category, "item-$i", null))
        }

        // Phase 2: Gemini 복구 — 실패 플래그 해제 + 서킷 브레이커 리셋
        // 실제 운영에서는 resetTimeout 경과 후 HALF_OPEN → 성공 시 CLOSED 전환
        shouldFail.set(false)
        service.geminiCircuitBreaker.recordSuccess()

        for (i in recoveryPoint until totalItems) {
            results.add(service.summarizeSingleItem(category, "item-$i", null))
        }

        results.all { it.success } shouldBe true
        val fallbackCount = results.count { it.isFallback }
        val normalCount = results.count { !it.isFallback }
        fallbackCount shouldBeGreaterThan 0
        normalCount shouldBeGreaterThan 0
    }

    // ──────────────────────────────────────────────────────────────
    // Scenario 5 — Combined load (1000 articles, 30% error)
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `시나리오 5 — 복합 부하 1000건, 30퍼센트 에러, delivery rate 99퍼센트 이상`() {
        val totalItems = 1000
        val service = buildService(errorRate = 0.30, delayRange = 1L..5L)

        val results = runParallel(service, totalItems, threadCount = 30)

        val deliveryRate = results.count { it.success }.toDouble() / totalItems
        deliveryRate shouldBeGreaterThanOrEqual 0.99
    }

    // ──────────────────────────────────────────────────────────────
    // Rate Limiter validation test
    // ──────────────────────────────────────────────────────────────

    @Test
    fun `Rate Limiter — RPM 120에서 토큰 소진 후 대기가 발생한다`() {
        val limiter = TokenBucketRateLimiter(
            name = "stress-test-limiter",
            permitsPerMinute = 120,
            maxBurst = 5,
            maxWaitMs = 5_000
        )

        // burst 5개는 즉시 통과
        val burstStart = System.nanoTime()
        repeat(5) { limiter.acquire() }
        val burstElapsedMs = (System.nanoTime() - burstStart) / 1_000_000

        // burst는 50ms 미만으로 통과해야 한다
        assert(burstElapsedMs < 50) {
            "Burst of 5 tokens took ${burstElapsedMs}ms, expected < 50ms"
        }

        // 추가 5개는 토큰 충전을 기다려야 한다 (RPM 120 = 500ms/token)
        val waitStart = System.nanoTime()
        repeat(5) { limiter.acquire() }
        val waitElapsedMs = (System.nanoTime() - waitStart) / 1_000_000

        // 5 tokens * 500ms/token = ~2500ms 대기 예상 (최소 1500ms 이상)
        assert(waitElapsedMs >= 1500) {
            "Post-burst 5 tokens took ${waitElapsedMs}ms, expected >= 1500ms"
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Helper: buildService — 고정 에러율 mock 기반 서비스 구성
    // ══════════════════════════════════════════════════════════════

    private fun buildService(
        errorRate: Double,
        delayRange: LongRange,
    ): ItemSummarizationService {
        val callCounter = AtomicInteger(0)
        val itemStore = mockk<RssItemStore>()
        val summaryStore = mockk<BatchSummaryStore>(relaxed = true)
        val summaryEnrichmentStore = mockk<SummaryEnrichmentStore>(relaxed = true)
        val llmRunStore = mockk<LlmRunStore>(relaxed = true)
        val summaryCacheStore = mockk<SummaryCacheStore>(relaxed = true) {
            every { findByKey(any()) } returns null
        }
        val metrics = mockk<ClippingMetrics>(relaxed = true)
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val llmCostService = mockk<LlmCostService> {
            every { isMonthlyBudgetExceeded() } returns false
        }
        val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
        val transactionTemplate = mockk<TransactionTemplate>()
        val properties = ClippingMcpServerProperties(screeningThreshold = 0.0f)

        // itemStore.findById — 동적으로 RssItem 생성
        every { itemStore.findById(any()) } answers {
            val id = firstArg<String>()
            RssItem(
                id = id,
                title = "Stress test article $id",
                content = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                    "This is test content for stress testing the pipeline resilience layer.",
                link = "https://example.com/$id",
                categoryId = "stress-cat",
                screenedScore = 0.8f
            )
        }
        every { itemStore.markProcessed(any()) } just Runs
        every { runtimeSettingService.current() } returns runtimeSettings()

        // summarizer mock — 에러율과 지연을 설정
        val summarizer = mockk<LlmSummarizationPort>()
        every { summarizer.getLastTokenUsage() } returns null
        every { summarizer.getLastRejectReason() } returns null
        every { summarizer.screenImportance(any(), any()) } returns ImportanceScreeningResult(
            score = 0.8f,
            status = "SUCCEEDED",
            inputChars = 100,
            outputChars = 20,
            durationMs = 50
        )
        every { summarizer.summarizeArticle(any(), any(), any(), any()) } answers {
            val callNum = callCounter.incrementAndGet()
            TestSleeper.sleep(delayRange.random(), "stress summarizer latency")
            if (Math.random() < errorRate) {
                throw RuntimeException("Simulated Gemini failure #$callNum")
            }
            LlmArticleSummaryResult(
                translatedTitle = "Translated title",
                summary = "Test summary for stress test",
                keywords = listOf("stress", "test"),
                importanceScore = 0.85f,
                sentiment = "NEUTRAL",
                eventType = null
            )
        }

        // TransactionTemplate — 콜백을 즉시 실행
        every { transactionTemplate.execute<Any?>(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val callback = firstArg<TransactionCallback<Any?>>()
            callback.doInTransaction(mockk(relaxed = true))
        }

        // Rate limiter — 높은 RPM으로 테스트 속도에 영향 없도록
        val rateLimiter = TokenBucketRateLimiter(
            name = "stress-test",
            permitsPerMinute = 6000,
            maxBurst = 100
        )

        return ItemSummarizationService(
            itemStore = itemStore,
            summaryStore = summaryStore,
            summaryEnrichmentStore = summaryEnrichmentStore,
            llmRunStore = llmRunStore,
            summarizer = summarizer,
            runtimeSettingService = runtimeSettingService,
            properties = properties,
            transactionTemplate = transactionTemplate,
            llmCostService = llmCostService,
            operationsNotificationService = operationsNotificationService,
            aiModelName = "gemini-stress-test",
            summaryCacheStore = summaryCacheStore,
            metrics = metrics,
            geminiRateLimiter = rateLimiter
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  Helper: buildServiceWithRecovery — 복구 시나리오용 서비스
    // ══════════════════════════════════════════════════════════════

    private fun buildServiceWithDynamicFailure(
        shouldFail: java.util.concurrent.atomic.AtomicBoolean,
        delayRange: LongRange,
    ): ItemSummarizationService {
        val itemStore = mockk<RssItemStore>()
        val summaryStore = mockk<BatchSummaryStore>(relaxed = true)
        val summaryEnrichmentStore = mockk<SummaryEnrichmentStore>(relaxed = true)
        val llmRunStore = mockk<LlmRunStore>(relaxed = true)
        val summaryCacheStore = mockk<SummaryCacheStore>(relaxed = true) {
            every { findByKey(any()) } returns null
        }
        val metrics = mockk<ClippingMetrics>(relaxed = true)
        val runtimeSettingService = mockk<RuntimeSettingService>()
        val llmCostService = mockk<LlmCostService> {
            every { isMonthlyBudgetExceeded() } returns false
        }
        val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
        val transactionTemplate = mockk<TransactionTemplate>()
        val properties = ClippingMcpServerProperties(screeningThreshold = 0.0f)

        every { itemStore.findById(any()) } answers {
            val id = firstArg<String>()
            RssItem(
                id = id,
                title = "Recovery test article $id",
                content = "Content for recovery scenario stress testing.",
                link = "https://example.com/$id",
                categoryId = "stress-cat",
                screenedScore = 0.8f
            )
        }
        every { itemStore.markProcessed(any()) } just Runs
        every { runtimeSettingService.current() } returns runtimeSettings()

        val summarizer = mockk<LlmSummarizationPort>()
        every { summarizer.getLastTokenUsage() } returns null
        every { summarizer.getLastRejectReason() } returns null
        every { summarizer.screenImportance(any(), any()) } returns ImportanceScreeningResult(
            score = 0.8f,
            status = "SUCCEEDED",
            inputChars = 100,
            outputChars = 20,
            durationMs = 50
        )
        every { summarizer.summarizeArticle(any(), any(), any(), any()) } answers {
            TestSleeper.sleep(delayRange.random(), "stress recovery summarizer latency")
            if (shouldFail.get()) {
                throw RuntimeException("Simulated Gemini failure (recovery scenario)")
            }
            LlmArticleSummaryResult(
                translatedTitle = "Translated title",
                summary = "Test summary after recovery",
                keywords = listOf("recovery", "test"),
                importanceScore = 0.85f,
                sentiment = "NEUTRAL",
                eventType = null
            )
        }

        every { transactionTemplate.execute<Any?>(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            val callback = firstArg<TransactionCallback<Any?>>()
            callback.doInTransaction(mockk(relaxed = true))
        }

        val rateLimiter = TokenBucketRateLimiter(
            name = "stress-recovery",
            permitsPerMinute = 6000,
            maxBurst = 100
        )

        return ItemSummarizationService(
            itemStore = itemStore,
            summaryStore = summaryStore,
            summaryEnrichmentStore = summaryEnrichmentStore,
            llmRunStore = llmRunStore,
            summarizer = summarizer,
            runtimeSettingService = runtimeSettingService,
            properties = properties,
            transactionTemplate = transactionTemplate,
            llmCostService = llmCostService,
            operationsNotificationService = operationsNotificationService,
            aiModelName = "gemini-stress-test",
            summaryCacheStore = summaryCacheStore,
            metrics = metrics,
            geminiRateLimiter = rateLimiter
        )
    }

    // ══════════════════════════════════════════════════════════════
    //  Helper: parallel execution
    // ══════════════════════════════════════════════════════════════

    private fun runParallel(
        service: ItemSummarizationService,
        totalItems: Int,
        threadCount: Int,
    ): List<ItemSummarizationResult> {
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(totalItems)
        val results = java.util.concurrent.ConcurrentLinkedQueue<ItemSummarizationResult>()
        val category = testCategory()

        for (i in 0 until totalItems) {
            executor.submit {
                try {
                    val itemId = "item-$i"
                    val result = service.summarizeSingleItem(category, itemId, null)
                    results.add(result)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.MINUTES)
        executor.shutdown()
        executor.awaitTermination(30, TimeUnit.SECONDS)

        return results.toList()
    }

    // ══════════════════════════════════════════════════════════════
    //  Helper: test fixtures
    // ══════════════════════════════════════════════════════════════

    private fun testCategory(): Category = Category(
        id = "stress-cat",
        name = "Stress Test Category",
        slackChannelId = "C_STRESS"
    )

    private fun runtimeSettings(): RuntimeSettingService.RuntimeSettings =
        RuntimeSettingService.RuntimeSettings(
            defaultHoursBack = 24,
            summaryInputMaxChars = 5000,
            digestMinImportanceScore = 0.3f,
            digestDefaultMaxItems = 10,
            digestMaxMessageChars = 3000,
            digestItemSummaryMaxChars = 500,
            digestKeywordMaxCount = 5,
            jobWorkerBatchSize = 5,
            jobMaxAttempts = 3,
            jobInitialBackoffSeconds = 5,
            slackBotToken = "xoxb-test",
            slackDigestBlockKitTemplate = "default",
            slackAutoDigestEnabled = false,
            slackDigestCron = "0 0 9 * * MON-FRI",
            slackAutoDigestMaxItems = 10,
            slackAutoDigestUnsentOnly = true,
            slackDailyChannelMessageLimit = 50,
            updatedAt = null
        )
}
