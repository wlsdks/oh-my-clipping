package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.port.OpsNotificationEvent

import com.clipping.mcpserver.service.notification.OperationsNotificationService
import com.clipping.mcpserver.service.port.LlmSummarizationPort
import com.clipping.mcpserver.config.ClippingMcpServerProperties
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.observability.ClippingMetrics
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.LlmRunStore
import com.clipping.mcpserver.store.RssItemStore
import com.clipping.mcpserver.store.SummaryCacheStore
import com.clipping.mcpserver.store.SummaryEnrichmentStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldNotEndWith
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate

class FallbackSummaryTest {

    private val itemStore = mockk<RssItemStore>()
    private val summaryStore = mockk<BatchSummaryStore>()
    private val summaryEnrichmentStore = mockk<SummaryEnrichmentStore>(relaxed = true)
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
    inner class `CB OPEN fallback 생성` {

        @Test
        fun `본문이 있는 아이템은 본문 발췌로 fallback summary를 생성한다 (Level 1)`() {
            val longContent = "이것은 테스트 본문입니다. 충분히 긴 내용을 포함합니다. " +
                "AI가 실패해도 사용자는 원문 발췌를 받습니다."
            val item = rssItem(id = "item-cb-l1", screenedScore = 0.8f, content = longContent)

            every { itemStore.findById("item-cb-l1") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { transactionTemplate.execute<Any?>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any?>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-cb-l1") } just Runs

            // 서킷 브레이커를 OPEN 상태로 만든다
            repeat(15) { service.geminiCircuitBreaker.recordFailure() }

            val result = service.summarizeSingleItem(testCategory, "item-cb-l1", null)

            result.success shouldBe true
            result.isFallback shouldBe true
            result.importanceScore shouldBe 0.8f

            // BatchSummary가 isFallback=true로 저장되는지 확인
            val summarySlot = slot<BatchSummary>()
            verify(exactly = 1) { summaryStore.save(capture(summarySlot)) }
            summarySlot.captured.isFallback shouldBe true
            summarySlot.captured.sentiment shouldBe "NEUTRAL"
            summarySlot.captured.summary shouldBe longContent // 200자 이내이므로 전문
        }

        @Test
        fun `본문이 없는 아이템은 제목으로 fallback summary를 생성한다 (Level 2)`() {
            val item = rssItem(id = "item-cb-l2", screenedScore = 0.7f, content = null)

            every { itemStore.findById("item-cb-l2") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { transactionTemplate.execute<Any?>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any?>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-cb-l2") } just Runs

            // 서킷 브레이커를 OPEN 상태로 만든다
            repeat(15) { service.geminiCircuitBreaker.recordFailure() }

            val result = service.summarizeSingleItem(testCategory, "item-cb-l2", null)

            result.success shouldBe true
            result.isFallback shouldBe true

            val summarySlot = slot<BatchSummary>()
            verify(exactly = 1) { summaryStore.save(capture(summarySlot)) }
            summarySlot.captured.summary shouldBe "테스트 아이템 item-cb-l2"
            summarySlot.captured.isFallback shouldBe true
        }
    }

    @Nested
    inner class `개별 AI 실패 fallback 생성` {

        @Test
        fun `CB CLOSED 상태에서 AI 호출 실패 시 fallback summary를 생성한다`() {
            val item = rssItem(id = "item-fail", screenedScore = 0.9f)

            every { itemStore.findById("item-fail") } returns item
            every { runtimeSettingService.current() } returns runtimeSettings()
            every { summarizer.summarizeArticle(any(), any(), any(), any()) } throws
                RuntimeException("Gemini 500 error")
            every { transactionTemplate.execute<Any?>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any?>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { llmRunStore.save(any()) } answers { firstArg() }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-fail") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-fail", null)

            result.success shouldBe true
            result.isFallback shouldBe true
            result.importanceScore shouldBe 0.9f

            // LlmRun FAILED 기록 + fallback summary 저장
            verify(exactly = 1) { llmRunStore.save(any()) }
            verify(exactly = 1) { summaryStore.save(any()) }
            verify(exactly = 1) { itemStore.markProcessed("item-fail") }
        }
    }

    @Nested
    inner class `예산 초과 fallback 생성` {

        @Test
        fun `월 예산 초과 시 fallback summary를 생성한다`() {
            val item = rssItem(id = "item-budget", screenedScore = 0.6f)

            every { llmCostService.isMonthlyBudgetExceeded() } returns true
            every { itemStore.findById("item-budget") } returns item
            every { transactionTemplate.execute<Any?>(any()) } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any?>>()
                callback.doInTransaction(mockk(relaxed = true))
            }
            every { summaryStore.save(any()) } answers { firstArg() }
            every { itemStore.markProcessed("item-budget") } just Runs

            val result = service.summarizeSingleItem(testCategory, "item-budget", null)

            result.success shouldBe true
            result.isFallback shouldBe true
            result.importanceScore shouldBe 0.6f

            // 운영 알림이 발송된다
            verify(exactly = 1) {
                operationsNotificationService.sendOps(
                    OpsNotificationEvent.BUDGET_EXCEEDED,
                    any(),
                    any()
                )
            }
            // AI 호출은 없어야 한다
            verify(exactly = 0) { summarizer.summarizeArticle(any(), any(), any(), any()) }
        }

        @Test
        fun `예산 초과 시 아이템이 없으면 success=false를 반환한다`() {
            every { llmCostService.isMonthlyBudgetExceeded() } returns true
            every { itemStore.findById("missing") } returns null

            val result = service.summarizeSingleItem(testCategory, "missing", null)

            result.success shouldBe false
            result.isFallback shouldBe false
        }
    }

    @Nested
    inner class `truncateToSentence 메서드` {

        @Test
        fun `maxChars 이내 텍스트는 그대로 반환한다`() {
            service.truncateToSentence("짧은 텍스트.", 200) shouldBe "짧은 텍스트."
        }

        @Test
        fun `ASCII 마침표에서 문장을 자른다`() {
            val text = "First sentence. Second sentence. Third sentence that is very long."
            val result = service.truncateToSentence(text, 35)
            result shouldBe "First sentence. Second sentence."
        }

        @Test
        fun `한국어 마침표 뒤에 공백이 있으면 문장 단위로 자른다`() {
            val text = "첫 번째 문장\u3002 두 번째 문장\u3002 세 번째 문장은 매우 깁니다."
            // maxChars=18이면 "첫 번째 문장。 두 번째 문장。" (17자)에서 마지막 。를 찾는다
            val result = service.truncateToSentence(text, 18)
            result shouldBe "첫 번째 문장\u3002 두 번째 문장\u3002"
        }

        @Test
        fun `문장 종결 부호가 없으면 ellipsis를 붙인다`() {
            val text = "이것은 마침표 없는 매우 긴 텍스트입니다 계속 이어집니다 끝이 보이지 않습니다"
            val result = service.truncateToSentence(text, 20)
            result shouldEndWith "\u2026"
        }

        @Test
        fun `느낌표로 끝나는 문장을 인식한다`() {
            val text = "Hello! World! This is a test."
            val result = service.truncateToSentence(text, 15)
            result shouldBe "Hello! World!"
        }

        @Test
        fun `물음표로 끝나는 문장을 인식한다`() {
            val text = "Is this working? Yes it is. Great!"
            val result = service.truncateToSentence(text, 20)
            result shouldBe "Is this working?"
        }
    }

    private fun rssItem(
        id: String,
        screenedScore: Float?,
        categoryId: String = "cat-1",
        content: String? = "테스트 본문 내용입니다."
    ): RssItem =
        RssItem(
            id = id,
            title = "테스트 아이템 $id",
            content = content,
            link = "https://example.com/$id",
            categoryId = categoryId,
            screenedScore = screenedScore
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
