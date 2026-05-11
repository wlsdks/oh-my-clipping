package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CachedSummary
import com.clipping.mcpserver.store.LlmRunStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.ai.GeminiTruncationException
import com.clipping.mcpserver.resilience.InMemoryCircuitBreaker
import com.clipping.mcpserver.service.port.ImportanceScreeningResult
import com.clipping.mcpserver.service.port.LlmArticleSummaryResult
import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.store.SummaryCacheStore
import com.clipping.mcpserver.store.SummaryEnrichmentStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate
import java.io.IOException
import java.net.SocketTimeoutException

class ItemSummarizationServiceTest {

    private val itemStore = mockk<RssItemStore>()
    private val summaryStore = mockk<BatchSummaryStore>()
    private val summaryEnrichmentStore = mockk<SummaryEnrichmentStore>()
    private val llmRunStore = mockk<LlmRunStore>()
    private val summarizer = mockk<LlmSummarizationPort> {
        every { getLastTokenUsage() } returns null
    }
    private val runtimeSettingService = mockk<RuntimeSettingService>()
    private val properties = ClippingMcpServerProperties(screeningThreshold = 0.4f)
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val llmCostService = mockk<LlmCostService> {
        every { isMonthlyBudgetExceeded() } returns false
    }
    private val operationsNotificationService = mockk<OperationsNotificationService>(relaxed = true)
    private val summaryCacheStore = mockk<SummaryCacheStore>(relaxed = true) {
        every { findByKey(any()) } returns null
    }
    private val metrics = mockk<ClippingMetrics>(relaxed = true)

    private val service = ItemSummarizationService(
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
        aiModelName = "gemini-test",
        summaryCacheStore = summaryCacheStore,
        metrics = metrics
    )

    private val testCategory = Category(
        id = "cat-1",
        name = "테스트 카테고리",
        slackChannelId = "C_TEST"
    )

    @Nested
    inner class `summarizeSingleItem 메서드` {

        @Test
        fun `존재하지 않는 itemId이면 success=false를 반환한다`() {
            every { itemStore.findById("missing-item") } returns null

            val result = service.summarizeSingleItem(testCategory, "missing-item", null)

            result.success shouldBe false
            verify(exactly = 1) { itemStore.findById("missing-item") }
            verify(exactly = 0) { summarizer.summarizeArticle(any(), any(), any(), any()) }
        }

        @Test
        fun `item 카테고리가 요청 카테고리와 다르면 저장 없이 success=false를 반환한다`() {
            val item = rssItem(id = "item-category-mismatch", categoryId = "cat-other", screenedScore = 0.8f)
            every { itemStore.findById("item-category-mismatch") } returns item

            val result = service.summarizeSingleItem(testCategory, "item-category-mismatch", null)

            result.success shouldBe false
            verify(exactly = 0) { runtimeSettingService.current() }
            verify(exactly = 0) { llmRunStore.save(any()) }
            verify(exactly = 0) { summaryStore.save(any()) }
            verify(exactly = 0) { itemStore.markProcessed(any()) }
        }

        @Test
        fun `이미 처리된 item이면 요약 저장 없이 건너뛴다`() {
            val item = rssItem(id = "item-already-processed", screenedScore = 0.8f, isProcessed = true)
            every { itemStore.findById("item-already-processed") } returns item

            val result = service.summarizeSingleItem(testCategory, "item-already-processed", null)

            result.success shouldBe true
            result.skippedByScreening shouldBe true
            verify(exactly = 0) { runtimeSettingService.current() }
            verify(exactly = 0) { summarizer.summarizeArticle(any(), any(), any(), any()) }
            verify(exactly = 0) { summaryStore.save(any()) }
            verify(exactly = 0) { itemStore.markProcessed(any()) }
        }

        @Test
        fun `스크리닝 점수가 임계값 미달이면 처리를 건너뛰고 skippedByScreening=true를 반환한다`() {
            val item = rssItem(id = "item-low", screenedScore = 0.2f)
            every { itemStore.findById("item-low") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { itemStore.markProcessed("item-low") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-low", null)

            result.success shouldBe true
            result.skippedByScreening shouldBe true
            verify(exactly = 1) { itemStore.markProcessed("item-low") }
            verify(exactly = 0) { summarizer.summarizeArticle(any(), any(), any(), any()) }
        }

        @Test
        fun `스크리닝 점수가 null이면 AI 스크리닝을 실행하고 미달 시 건너뛴다`() {
            val item = rssItem(id = "item-null-score", screenedScore = null)
            every { itemStore.findById("item-null-score") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            // 스크리닝 AI가 낮은 점수를 반환
            every { summarizer.screenImportance(any(), any()) } returns ImportanceScreeningResult(
                score = 0.1f,
                status = "SUCCEEDED",
                inputChars = 120,
                outputChars = 24,
                durationMs = 80
            )
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { itemStore.updateScreenedScore("item-null-score", 0.1f) } just Runs
            every { itemStore.markProcessed("item-null-score") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-null-score", null)

            result.success shouldBe true
            result.skippedByScreening shouldBe true
            verify(exactly = 1) { summarizer.screenImportance(any(), any()) }
            val llmRunSlot = slot<LlmRun>()
            verify(exactly = 1) { llmRunStore.save(capture(llmRunSlot)) }
            llmRunSlot.captured.promptVersion shouldBe "screening.v1"
            llmRunSlot.captured.status shouldBe "SUCCEEDED"
            verify(exactly = 1) { itemStore.updateScreenedScore("item-null-score", 0.1f) }
        }

        @Test
        fun `스크리닝 파싱 실패 상태는 FAILED로 정규화해 저장한다`() {
            val item = rssItem(id = "item-parse-fail", screenedScore = null)
            every { itemStore.findById("item-parse-fail") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summarizer.screenImportance(any(), any()) } returns ImportanceScreeningResult(
                score = 0.1f,
                status = "FAILED_PARSE",
                inputChars = 120,
                outputChars = 24,
                durationMs = 80,
                errorMessage = "importanceScore missing"
            )
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { itemStore.updateScreenedScore("item-parse-fail", 0.1f) } just Runs
            every { itemStore.markProcessed("item-parse-fail") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-parse-fail", null)

            result.success shouldBe true
            result.skippedByScreening shouldBe true
            val llmRunSlot = slot<LlmRun>()
            verify(exactly = 1) { llmRunStore.save(capture(llmRunSlot)) }
            llmRunSlot.captured.promptVersion shouldBe "screening.v1"
            llmRunSlot.captured.status shouldBe "FAILED"
            llmRunSlot.captured.errorMessage shouldBe "screening_status=FAILED_PARSE | importanceScore missing"
            verify(exactly = 1) { itemStore.updateScreenedScore("item-parse-fail", 0.1f) }
        }

        @Test
        fun `AI 호출 성공 시 요약을 저장하고 success=true를 반환한다`() {
            val item = rssItem(id = "item-ok", screenedScore = 0.8f)
            val aiResponse = LlmArticleSummaryResult(
                translatedTitle = "번역된 제목",
                summary = "요약 내용",
                keywords = listOf("AI", "테스트"),
                importanceScore = 0.9f,
                sentiment = "POSITIVE",
                eventType = "PRODUCT_LAUNCH"
            )

            every { itemStore.findById("item-ok") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            // 서킷 브레이커가 정상 상태이므로 canCall() 호출 없이 진행
            every { summarizer.summarizeArticle(any(), any(), any(), any()) } returns aiResponse
            // TransactionTemplate.execute 콜백을 실제로 실행
            every { transactionTemplate.execute<ItemSummarizationResult>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<ItemSummarizationResult>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-ok") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-ok", null)

            result.success shouldBe true
            result.keywords shouldBe listOf("AI", "테스트")
            result.importanceScore shouldBe 0.9f
            verify(exactly = 1) { summaryStore.save(any()) }
            verify(exactly = 1) { llmRunStore.save(any()) }
            verify(exactly = 1) { itemStore.markProcessed("item-ok") }
        }

        @Test
        fun `AI 호출 시 예외가 발생하면 LlmRun에 FAILED 기록 후 fallback summary를 생성한다`() {
            val item = rssItem(id = "item-err", screenedScore = 0.8f)

            every { itemStore.findById("item-err") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summarizer.summarizeArticle(any(), any(), any(), any()) } throws
                RuntimeException("Gemini API error")
            // TransactionTemplate.execute 콜백을 실제로 실행
            every { transactionTemplate.execute<Any?>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any?>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-err") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-err", null)

            // fallback이 생성되므로 success=true, isFallback=true
            result.success shouldBe true
            result.isFallback shouldBe true
            // LlmRun은 FAILED 상태로 저장되어야 한다
            val llmRunSlot = slot<LlmRun>()
            verify(exactly = 1) { llmRunStore.save(capture(llmRunSlot)) }
            llmRunSlot.captured.status shouldBe "FAILED"
            llmRunSlot.captured.errorMessage shouldBe "Gemini API error"
            // fallback summary가 저장된다
            verify(exactly = 1) { summaryStore.save(any()) }
        }

        @Test
        fun `서킷 브레이커가 OPEN 상태이면 AI 호출 없이 fallback summary를 생성한다`() {
            val item = rssItem(id = "item-cb", screenedScore = 0.8f)

            every { itemStore.findById("item-cb") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { transactionTemplate.execute<Any?>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any?>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-cb") } just Runs

            // 서킷 브레이커를 OPEN 상태로 만든다 (연속 실패 기록)
            repeat(15) { service.geminiCircuitBreaker.recordFailure() }

            val result = service.summarizeSingleItem(testCategory, "item-cb", null)

            result.success shouldBe true
            result.isFallback shouldBe true
            verify(exactly = 0) { summarizer.summarizeArticle(any(), any(), any(), any()) }
        }

        @Test
        fun `캐시 히트 시 Gemini를 호출하지 않고 cachedHit=true를 반환한다`() {
            val item = rssItem(id = "item-cached", screenedScore = 0.8f)
            every { itemStore.findById("item-cached") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summaryCacheStore.findByKey(any()) } returns CachedSummary(
                cacheKey = "some-hash",
                summary = "캐시된 요약 내용",
                keywords = "[\"AI\",\"캐시\"]",
                importanceScore = 0.85f,
                sentiment = "NEUTRAL",
                eventType = "PRODUCT_LAUNCH"
            )
            every { transactionTemplate.execute<Unit>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-cached") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-cached", null)

            result.success shouldBe true
            result.cachedHit shouldBe true
            result.importanceScore shouldBe 0.85f
            result.keywords shouldBe listOf("AI", "캐시")
            verify(exactly = 0) { summarizer.summarizeArticle(any(), any(), any(), any()) }
            verify(exactly = 1) { itemStore.markProcessed("item-cached") }
        }

        @Test
        fun `캐시 히트 시 summary_cache hit 메트릭이 증가한다`() {
            val item = rssItem(id = "item-metric-hit", screenedScore = 0.8f)
            every { itemStore.findById("item-metric-hit") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summaryCacheStore.findByKey(any()) } returns CachedSummary(
                cacheKey = "h1",
                summary = "요약",
                keywords = null,
                importanceScore = 0.8f,
                sentiment = null,
                eventType = null
            )
            every { transactionTemplate.execute<Unit>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-metric-hit") } just Runs

            service.summarizeSingleItem(testCategory, "item-metric-hit", null)

            verify(exactly = 1) { metrics.recordSummaryCacheHit() }
            verify(exactly = 0) { metrics.recordSummaryCacheMiss() }
        }

        @Test
        fun `캐시 미스 시 summary_cache miss 메트릭이 증가한다`() {
            val item = rssItem(id = "item-metric-miss", screenedScore = 0.8f)
            every { itemStore.findById("item-metric-miss") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summaryCacheStore.findByKey(any()) } returns null
            every { summarizer.summarizeArticle(any(), any(), any(), any()) } returns LlmArticleSummaryResult(
                translatedTitle = null,
                summary = "요약",
                keywords = emptyList(),
                importanceScore = 0.8f
            )
            every { transactionTemplate.execute<ItemSummarizationResult>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<ItemSummarizationResult>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-metric-miss") } just Runs

            service.summarizeSingleItem(testCategory, "item-metric-miss", null)

            verify(exactly = 0) { metrics.recordSummaryCacheHit() }
            verify(exactly = 1) { metrics.recordSummaryCacheMiss() }
        }

        @Test
        fun `본문 앞 500자가 같아도 실제 LLM 입력이 다르면 캐시를 재사용하지 않는다`() {
            val localCache = mutableMapOf<String, CachedSummary>()
            val localSummaryCacheStore = mockk<SummaryCacheStore> {
                every { findByKey(any()) } answers { localCache[firstArg()] }
                every { save(any()) } answers {
                    val entry = firstArg<CachedSummary>()
                    localCache[entry.cacheKey] = entry
                }
            }
            val localService = ItemSummarizationService(
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
                aiModelName = "gemini-test",
                summaryCacheStore = localSummaryCacheStore,
                metrics = metrics
            )
            val commonPrefix = "공통 본문 ".repeat(63).take(500)
            val firstItem = rssItem(
                id = "item-cache-collision-a",
                title = "같은 제목",
                content = commonPrefix + "첫 번째 기사만의 핵심 내용",
                screenedScore = 0.8f
            )
            val secondItem = rssItem(
                id = "item-cache-collision-b",
                title = "같은 제목",
                content = commonPrefix + "두 번째 기사만의 다른 핵심 내용",
                screenedScore = 0.8f
            )

            every { itemStore.findById(firstItem.id) } returns firstItem
            every { itemStore.findById(secondItem.id) } returns secondItem
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summarizer.summarizeArticle(any(), match { it.contains("첫 번째") }, any(), any()) } returns
                LlmArticleSummaryResult(
                    translatedTitle = null,
                    summary = "첫 번째 요약",
                    keywords = emptyList(),
                    importanceScore = 0.8f
                )
            every { summarizer.summarizeArticle(any(), match { it.contains("두 번째") }, any(), any()) } returns
                LlmArticleSummaryResult(
                    translatedTitle = null,
                    summary = "두 번째 요약",
                    keywords = emptyList(),
                    importanceScore = 0.8f
                )
            every { transactionTemplate.execute<ItemSummarizationResult>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<ItemSummarizationResult>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed(any()) } just Runs

            val firstResult = localService.summarizeSingleItem(testCategory, firstItem.id, null)
            val secondResult = localService.summarizeSingleItem(testCategory, secondItem.id, null)

            firstResult.cachedHit shouldBe false
            secondResult.cachedHit shouldBe false
            localCache.size shouldBe 2
            verify(exactly = 2) { summarizer.summarizeArticle("같은 제목", any(), any(), any()) }
        }

        @Test
        fun `GeminiTruncationException 발생 시 summary_cache에 저장되지 않고 fallback이 생성된다`() {
            val item = rssItem(id = "item-trunc", screenedScore = 0.8f)
            every { itemStore.findById("item-trunc") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summaryCacheStore.findByKey(any()) } returns null
            every { summarizer.summarizeArticle(any(), any(), any(), any()) } throws
                GeminiTruncationException("Gemini 응답이 잘림")
            // TransactionTemplate.execute 콜백을 실제로 실행
            every { transactionTemplate.execute<Any?>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any?>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-trunc") } just Runs

            // GeminiTruncationException은 비일시적 오류 — fallback summary 생성
            val result = service.summarizeSingleItem(testCategory, "item-trunc", null)

            // 캐시 저장은 절대 발생해선 안 된다
            verify(exactly = 0) { summaryCacheStore.save(any()) }
            result.success shouldBe true
            result.isFallback shouldBe true
        }

        @Test
        fun `캐시 저장 실패 시 success=false를 반환한다`() {
            val item = rssItem(id = "item-cache-fail", screenedScore = 0.8f)
            every { itemStore.findById("item-cache-fail") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summaryCacheStore.findByKey(any()) } returns CachedSummary(
                cacheKey = "some-hash",
                summary = "캐시된 요약",
                keywords = null,
                importanceScore = 0.8f,
                sentiment = null,
                eventType = null
            )
            // transactionTemplate.execute가 예외를 던져 저장 실패를 시뮬레이션한다
            every { transactionTemplate.execute<Unit>(any()) } throws RuntimeException("constraint violation")

            val result = service.summarizeSingleItem(testCategory, "item-cache-fail", null)

            // 저장 실패 시 success=false 반환 — 호출자가 정확한 상태를 인지할 수 있어야 한다
            result.success shouldBe false
            result.cachedHit shouldBe false
        }

        @Test
        fun `content가 null이면 title을 사용하여 요약한다`() {
            val item = RssItem(
                id = "item-no-content",
                title = "제목만 있는 아이템",
                content = null,
                link = "https://example.com/no-content",
                categoryId = "cat-1",
                screenedScore = 0.8f
            )
            val aiResponse = LlmArticleSummaryResult(
                translatedTitle = null,
                summary = "제목 기반 요약",
                keywords = listOf("제목"),
                importanceScore = 0.5f
            )

            every { itemStore.findById("item-no-content") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summarizer.summarizeArticle(any(), any(), any(), any()) } returns aiResponse
            every { transactionTemplate.execute<ItemSummarizationResult>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<ItemSummarizationResult>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-no-content") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-no-content", null)

            result.success shouldBe true
            // summarizeArticle 호출 시 content 인자가 title과 같아야 한다
            verify(exactly = 1) {
                summarizer.summarizeArticle("제목만 있는 아이템", "제목만 있는 아이템", any(), any())
            }
        }
    }

    @Nested
    inner class `resummarizeFallbacks 메서드` {

        @Test
        fun `fallback 재요약 성공 시 llm_runs를 기록하고 기존 요약을 갱신한다`() {
            val summary = batchSummary(id = "summary-retry-ok", rssItemId = "item-retry-ok")
            val item = rssItem(
                id = "item-retry-ok",
                title = "재요약 대상",
                content = "재요약할 실제 본문",
                screenedScore = 0.8f
            )
            val aiResponse = LlmArticleSummaryResult(
                translatedTitle = "번역 제목",
                summary = "복구된 AI 요약",
                keywords = listOf("복구"),
                importanceScore = 0.91f,
                sentiment = "POSITIVE",
                eventType = "UPDATE"
            )

            every { summaryEnrichmentStore.findFallbacksWithin24h(limit = 200) } returns listOf(summary)
            every { itemStore.findById("item-retry-ok") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summarizer.summarizeArticle("재요약 대상", "재요약할 실제 본문", any(), null) } returns aiResponse
            every { transactionTemplate.execute<Unit>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { llmRunStore.save(any()) } answers { firstArg() }
            every {
                summaryEnrichmentStore.updateSummaryContent(
                    summaryId = "summary-retry-ok",
                    translatedTitle = "번역 제목",
                    summary = "복구된 AI 요약",
                    keywords = listOf("복구"),
                    importanceScore = 0.91f,
                    sentiment = "POSITIVE",
                    eventType = "UPDATE"
                )
            } just Runs

            val result = service.resummarizeFallbacks()

            result shouldBe 1
            val llmRunSlot = slot<LlmRun>()
            verify(exactly = 1) { llmRunStore.save(capture(llmRunSlot)) }
            llmRunSlot.captured.categoryId shouldBe "cat-1"
            llmRunSlot.captured.rssItemId shouldBe "item-retry-ok"
            llmRunSlot.captured.promptVersion shouldBe "article.v3"
            llmRunSlot.captured.status shouldBe "SUCCEEDED"
            llmRunSlot.captured.outputChars shouldBe "복구된 AI 요약".length
            verify(exactly = 1) {
                summaryEnrichmentStore.updateSummaryContent(
                    summaryId = "summary-retry-ok",
                    translatedTitle = "번역 제목",
                    summary = "복구된 AI 요약",
                    keywords = listOf("복구"),
                    importanceScore = 0.91f,
                    sentiment = "POSITIVE",
                    eventType = "UPDATE"
                )
            }
        }

        @Test
        fun `fallback 재요약이 빈 응답이면 EMPTY_RESULT 이력을 남기고 요약은 갱신하지 않는다`() {
            val summary = batchSummary(id = "summary-retry-empty", rssItemId = "item-retry-empty")
            val item = rssItem(
                id = "item-retry-empty",
                title = "빈 응답 대상",
                content = "품질 검증에서 거부될 본문",
                screenedScore = 0.8f
            )

            every { summaryEnrichmentStore.findFallbacksWithin24h(limit = 200) } returns listOf(summary)
            every { itemStore.findById("item-retry-empty") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summarizer.summarizeArticle("빈 응답 대상", "품질 검증에서 거부될 본문", any(), null) } returns null
            every { summarizer.getLastRejectReason() } returns "QUALITY_REJECT"
            every { llmRunStore.save(any()) } answers { firstArg() }

            val result = service.resummarizeFallbacks()

            result shouldBe 0
            val llmRunSlot = slot<LlmRun>()
            verify(exactly = 1) { llmRunStore.save(capture(llmRunSlot)) }
            llmRunSlot.captured.status shouldBe "EMPTY_RESULT"
            llmRunSlot.captured.errorMessage shouldBe "QUALITY_REJECT"
            verify(exactly = 0) {
                summaryEnrichmentStore.updateSummaryContent(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Nested
    inner class `isTransient 메서드` {

        @Test
        fun `SocketTimeoutException은 일시적 오류로 판별한다`() {
            service.isTransient(SocketTimeoutException("connect timed out")) shouldBe true
        }

        @Test
        fun `IOException은 일시적 오류로 판별한다`() {
            service.isTransient(IOException("connection reset")) shouldBe true
        }

        @Test
        fun `메시지에 429가 포함되면 일시적 오류로 판별한다`() {
            service.isTransient(RuntimeException("HTTP 429 Too Many Requests")) shouldBe true
        }

        @Test
        fun `메시지에 503이 포함되면 일시적 오류로 판별한다`() {
            service.isTransient(RuntimeException("HTTP 503 Service Unavailable")) shouldBe true
        }

        @Test
        fun `메시지에 504가 포함되면 일시적 오류로 판별한다`() {
            service.isTransient(RuntimeException("HTTP 504 Gateway Timeout")) shouldBe true
        }

        @Test
        fun `메시지에 UNAVAILABLE이 포함되면 일시적 오류로 판별한다`() {
            service.isTransient(RuntimeException("gRPC UNAVAILABLE")) shouldBe true
        }

        @Test
        fun `메시지에 DEADLINE_EXCEEDED가 포함되면 일시적 오류로 판별한다`() {
            service.isTransient(RuntimeException("DEADLINE_EXCEEDED: timeout")) shouldBe true
        }

        @Test
        fun `cause 메시지에 429가 포함되어도 일시적 오류로 판별한다`() {
            val cause = RuntimeException("429 rate limited")
            service.isTransient(RuntimeException("wrapped", cause)) shouldBe true
        }

        @Test
        fun `JSON 파싱 오류는 일시적 오류가 아니다`() {
            service.isTransient(
                com.fasterxml.jackson.core.JsonParseException(null, "Unexpected character")
            ) shouldBe false
        }

        @Test
        fun `IllegalArgumentException은 일시적 오류가 아니다`() {
            service.isTransient(IllegalArgumentException("bad input")) shouldBe false
        }

        @Test
        fun `일반 RuntimeException은 일시적 오류가 아니다`() {
            service.isTransient(RuntimeException("unknown error")) shouldBe false
        }
    }

    @Nested
    inner class `retryOnTransient 메서드` {

        @Test
        fun `일시적 실패 후 재시도하여 성공하면 결과를 반환한다`() {
            var callCount = 0
            val result = service.retryOnTransient(maxRetries = 2, delayMs = 0, sleeper = {}) {
                callCount++
                if (callCount == 1) throw SocketTimeoutException("timeout")
                "success"
            }

            result shouldBe "success"
            callCount shouldBe 2
        }

        @Test
        fun `비일시적 오류는 재시도 없이 즉시 예외를 전파한다`() {
            var callCount = 0
            assertThrows<IllegalArgumentException> {
                service.retryOnTransient(maxRetries = 2, delayMs = 0, sleeper = {}) {
                    callCount++
                    throw IllegalArgumentException("bad request")
                }
            }

            callCount shouldBe 1
        }

        @Test
        fun `최대 재시도 횟수 초과 시 마지막 예외를 전파한다`() {
            var callCount = 0
            val thrown = assertThrows<SocketTimeoutException> {
                service.retryOnTransient(maxRetries = 2, delayMs = 0, sleeper = {}) {
                    callCount++
                    throw SocketTimeoutException("timeout attempt $callCount")
                }
            }

            // 초기 시도 1회 + 재시도 2회 = 총 3회
            callCount shouldBe 3
            thrown.message shouldBe "timeout attempt 3"
        }

        @Test
        fun `첫 번째 시도에서 성공하면 재시도 없이 결과를 반환한다`() {
            var callCount = 0
            val result = service.retryOnTransient(maxRetries = 2, delayMs = 0, sleeper = {}) {
                callCount++
                "immediate success"
            }

            result shouldBe "immediate success"
            callCount shouldBe 1
        }

        @Test
        fun `maxRetries가 음수이면 NPE 없이 단일 시도 후 원본 예외를 전파한다`() {
            var callCount = 0
            val thrown = assertThrows<SocketTimeoutException> {
                service.retryOnTransient(maxRetries = -1, delayMs = 0, sleeper = {}) {
                    callCount++
                    throw SocketTimeoutException("negative retry")
                }
            }

            callCount shouldBe 1
            thrown.message shouldBe "negative retry"
        }

        @Test
        fun `서킷 브레이커가 OPEN이면 재시도를 중단하고 예외를 전파한다`() {
            // 서킷 브레이커를 OPEN 상태로 만든다
            repeat(15) { service.geminiCircuitBreaker.recordFailure() }

            var callCount = 0
            assertThrows<IOException> {
                service.retryOnTransient(maxRetries = 2, delayMs = 0, sleeper = {}) {
                    callCount++
                    throw IOException("connection refused")
                }
            }

            // 서킷 브레이커 OPEN이므로 1회 호출 후 재시도하지 않는다
            callCount shouldBe 1
        }

        @Test
        fun `재시도 사이에 sleeper가 호출된다`() {
            val sleepCalls = mutableListOf<Long>()
            var callCount = 0

            service.retryOnTransient(
                maxRetries = 2,
                delayMs = 2000,
                sleeper = { sleepCalls.add(it) }
            ) {
                callCount++
                if (callCount <= 2) throw SocketTimeoutException("timeout")
                "success"
            }

            sleepCalls.size shouldBe 2
            sleepCalls[0] shouldBe 2000
            sleepCalls[1] shouldBe 2000
        }

        @Test
        fun `HTTP 429 메시지가 포함된 RuntimeException은 재시도한다`() {
            var callCount = 0
            val result = service.retryOnTransient(maxRetries = 1, delayMs = 0, sleeper = {}) {
                callCount++
                if (callCount == 1) throw RuntimeException("HTTP 429 Too Many Requests")
                "recovered"
            }

            result shouldBe "recovered"
            callCount shouldBe 2
        }

        @Test
        fun `재시도 로그는 원본 예외의 stacktrace 를 포함한다`() {
            // 장애 원인 추적을 위해 log.warn 이 Throwable 인자 없이 호출되면 stacktrace 가 유실된다.
            // PR #440 회귀 방지를 위해 ListAppender 로 로그 이벤트의 throwable 존재를 확인한다.
            val slf4jLogger = LoggerFactory.getLogger(ItemSummarizationService::class.java) as LogbackLogger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            slf4jLogger.addAppender(appender)
            try {
                service.retryOnTransient(maxRetries = 1, delayMs = 0, sleeper = {}) {
                    throw SocketTimeoutException("transient-timeout-XYZ")
                }
                error("expected retryOnTransient to throw after exhausting retries")
            } catch (_: SocketTimeoutException) {
                val retryEvent = appender.list.firstOrNull {
                    it.level == Level.WARN && it.formattedMessage.contains("Transient failure")
                }
                retryEvent shouldNotBe null
                retryEvent!!.throwableProxy shouldNotBe null
                retryEvent.throwableProxy.className shouldBe SocketTimeoutException::class.java.name
            } finally {
                slf4jLogger.detachAppender(appender)
            }
        }
    }

    @Nested
    inner class `GeminiTruncationException 분류` {

        @Test
        fun `GeminiTruncationException은 일시적 오류가 아니다`() {
            service.isTransient(
                GeminiTruncationException("Gemini 응답이 잘림")
            ) shouldBe false
        }

        @Test
        fun `GeminiTruncationException은 재시도 없이 즉시 전파된다`() {
            var callCount = 0
            assertThrows<GeminiTruncationException> {
                service.retryOnTransient(maxRetries = 2, delayMs = 0, sleeper = {}) {
                    callCount++
                    throw GeminiTruncationException("truncated response")
                }
            }
            // 비일시적 오류이므로 1회만 호출
            callCount shouldBe 1
        }

        @Test
        fun `GeminiTruncationException 메시지가 보존된다`() {
            val thrown = assertThrows<GeminiTruncationException> {
                service.retryOnTransient(maxRetries = 2, delayMs = 0, sleeper = {}) {
                    throw GeminiTruncationException("finish_reason=LENGTH, context=article")
                }
            }
            thrown.message shouldBe "finish_reason=LENGTH, context=article"
        }
    }

    @Nested
    inner class `서킷 브레이커 임계값 검증` {

        @Test
        fun `14회 실패 시 서킷 브레이커는 여전히 CLOSED 상태다`() {
            val cb = InMemoryCircuitBreaker(
                name = "test_cb",
                failureThreshold = 15,
                resetTimeoutSeconds = 30
            )

            repeat(14) { cb.recordFailure() }

            cb.state() shouldBe InMemoryCircuitBreaker.State.CLOSED
            cb.canCall() shouldBe true
        }

        @Test
        fun `15회 실패 시 서킷 브레이커가 정확히 OPEN으로 전환된다`() {
            val cb = InMemoryCircuitBreaker(
                name = "test_cb",
                failureThreshold = 15,
                resetTimeoutSeconds = 30
            )

            repeat(15) { cb.recordFailure() }

            cb.state() shouldBe InMemoryCircuitBreaker.State.OPEN
            cb.canCall() shouldBe false
        }

        @Test
        fun `서비스의 geminiCircuitBreaker 임계값이 15로 설정되어 있다`() {
            // geminiCircuitBreaker가 15회 실패 후 OPEN으로 전환되는지 확인
            val freshService = ItemSummarizationService(
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
                aiModelName = "gemini-test",
                summaryCacheStore = summaryCacheStore,
                metrics = metrics
            )

            // 14회 실패 → 여전히 호출 가능
            repeat(14) { freshService.geminiCircuitBreaker.recordFailure() }
            freshService.geminiCircuitBreaker.canCall() shouldBe true

            // 15회째 실패 → OPEN으로 전환
            freshService.geminiCircuitBreaker.recordFailure()
            freshService.geminiCircuitBreaker.canCall() shouldBe false
        }

        @Test
        fun `성공 기록 후 실패 카운터가 리셋된다`() {
            val cb = InMemoryCircuitBreaker(
                name = "test_cb",
                failureThreshold = 15,
                resetTimeoutSeconds = 30
            )

            repeat(14) { cb.recordFailure() }
            cb.recordSuccess()

            // 리셋 후 14회 더 실패해도 CLOSED
            repeat(14) { cb.recordFailure() }
            cb.state() shouldBe InMemoryCircuitBreaker.State.CLOSED

            // 15회째에 OPEN
            cb.recordFailure()
            cb.state() shouldBe InMemoryCircuitBreaker.State.OPEN
        }
    }

    private fun rssItem(
        id: String,
        screenedScore: Float?,
        categoryId: String = "cat-1",
        title: String = "테스트 아이템 $id",
        content: String? = "테스트 본문 내용입니다.",
        isProcessed: Boolean = false
    ): RssItem =
        RssItem(
            id = id,
            title = title,
            content = content,
            link = "https://example.com/$id",
            categoryId = categoryId,
            screenedScore = screenedScore,
            isProcessed = isProcessed
        )

    private fun batchSummary(
        id: String,
        rssItemId: String?,
        categoryId: String = "cat-1"
    ): BatchSummary =
        BatchSummary(
            id = id,
            originalTitle = "fallback summary $id",
            summary = "fallback summary body",
            sourceLink = "https://example.com/summary/$id",
            categoryId = categoryId,
            rssItemId = rssItemId,
            isFallback = true
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
