package com.clipping.mcpserver.service

import com.clipping.mcpserver.error.InvalidInputException
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.service.query.ReviewPolicyQueryHelper
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.ReviewItemAuditStore
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * AdminReviewQueueService의 ensurePolicyReviewDecisions()과 listReviewItems()
 * 핵심 비즈니스 로직을 검증하는 테스트.
 */
class AdminReviewQueueServicePolicyTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val categoryStore = mockk<CategoryStore> {
        // 룰 엔진 경로에서 categoryStore.findById() 가 호출되지만 evaluator 가 PassThrough 로
        // mock 되어 있어 실제 Category 가 필요 없다. 기본적으로 null 반환 → 룰 평가를 건너뛴다.
        every { findById(any()) } returns null
    }
    private val categoryRuleService = mockk<AdminCategoryRuleService>()
    private val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>()
    private val reviewItemAuditStore = mockk<ReviewItemAuditStore>()
    private val reviewPolicyQueryHelper = mockk<ReviewPolicyQueryHelper>()
    private val ruleEvaluator = mockk<ReviewPolicyRuleEvaluator> {
        // 기본: 룰 엔진은 항상 PassThrough — 기존 threshold 로직 검증 유지
        every { evaluate(any(), any(), any()) } returns
            com.clipping.mcpserver.service.dto.RuleEvaluationResult.PassThrough
    }

    private val service = AdminReviewQueueService(
        batchSummaryStore = batchSummaryStore,
        categoryStore = categoryStore,
        categoryRuleService = categoryRuleService,
        reviewItemDecisionStore = reviewItemDecisionStore,
        reviewItemAuditStore = reviewItemAuditStore,
        reviewPolicyQueryHelper = reviewPolicyQueryHelper,
        ruleEvaluator = ruleEvaluator
    )

    // ── 공통 테스트 데이터 팩토리 ──

    private fun testSummary(
        id: String,
        categoryId: String = "cat-1",
        originalTitle: String = "Test Article $id",
        translatedTitle: String? = null,
        summary: String = "Article summary about AI technology",
        keywords: List<String> = listOf("AI", "technology"),
        importanceScore: Float = 0.5f,
        createdAt: Instant = Instant.now()
    ) = BatchSummary(
        id = id,
        originalTitle = originalTitle,
        translatedTitle = translatedTitle,
        summary = summary,
        keywords = keywords,
        importanceScore = importanceScore,
        sourceLink = "https://example.com/$id",
        categoryId = categoryId,
        rssItemId = "item-$id",
        createdAt = createdAt
    )

    private fun testRule(
        categoryId: String = "cat-1",
        includeKeywords: List<String> = emptyList(),
        excludeKeywords: List<String> = emptyList(),
        includeThreshold: Double = 0.55,
        reviewThreshold: Double = 0.35,
        uncertainToReview: Boolean = true,
        autoExcludeEnabled: Boolean = true
    ) = CategoryRule(
        categoryId = categoryId,
        includeKeywords = includeKeywords,
        excludeKeywords = excludeKeywords,
        includeThreshold = includeThreshold,
        reviewThreshold = reviewThreshold,
        uncertainToReview = uncertainToReview,
        autoExcludeEnabled = autoExcludeEnabled
    )

    private fun testCategory(id: String = "cat-1", name: String = "Tech News") = Category(
        id = id,
        name = name
    )

    // ════════════════════════════════════════════
    // ensurePolicyReviewDecisions
    // ════════════════════════════════════════════

    @Nested
    inner class `ensurePolicyReviewDecisions 메서드` {

        @Test
        fun `빈 리스트가 들어오면 빈 맵을 반환한다`() {
            val result = service.ensurePolicyReviewDecisions(emptyList())

            result shouldBe emptyMap()
            verify(exactly = 0) { reviewItemDecisionStore.findBySummaryIds(any()) }
        }

        @Test
        fun `이미 결정이 존재하는 항목은 스킵하고 기존 결정을 반환한다`() {
            val summary = testSummary("sum-1")
            val existingDecision = ReviewItemDecision(
                summaryId = "sum-1",
                categoryId = "cat-1",
                status = ReviewDecisionStatus.INCLUDE,
                reviewedBy = "admin"
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-1")) } returns listOf(existingDecision)

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 1
            result["sum-1"]?.status shouldBe ReviewDecisionStatus.INCLUDE
            // 새로운 결정을 upsert하지 않아야 한다
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 0) { reviewItemAuditStore.append(any()) }
        }

        @Test
        fun `제외 키워드 일치 시 EXCLUDE 결정을 생성한다`() {
            val summary = testSummary(
                "sum-2",
                originalTitle = "Breaking: cryptocurrency scam exposed",
                summary = "Major cryptocurrency scam ring busted"
            )
            val rule = testRule(
                excludeKeywords = listOf("scam"),
                autoExcludeEnabled = true
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-2")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers { decisionSlot.captured }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 1
            result["sum-2"]!!.status shouldBe ReviewDecisionStatus.EXCLUDE
            result["sum-2"]!!.reviewedBy shouldBe "policy-auto"
            decisionSlot.captured.reason shouldContain "제외 키워드 일치: scam"
            verify(exactly = 1) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 1) { reviewItemAuditStore.append(any()) }
        }

        @Test
        fun `제외 키워드가 일치하지만 autoExcludeEnabled가 false이면 포함 키워드로 넘어간다`() {
            val summary = testSummary(
                "sum-no-auto",
                originalTitle = "scam news but auto exclude disabled",
                summary = "about scam"
            )
            val rule = testRule(
                excludeKeywords = listOf("scam"),
                includeKeywords = listOf("news"),
                autoExcludeEnabled = false,
                includeThreshold = 0.9,
                reviewThreshold = 0.8
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-no-auto")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            // includeKeyword "news"가 일치하므로 INCLUDE → suggestStatus가 INCLUDE 반환
            // INCLUDE는 REVIEW/EXCLUDE가 아니므로 저장하지 않는다
            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 0
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
        }

        @Test
        fun `포함 키워드 일치 시 INCLUDE 제안이므로 결정을 저장하지 않는다`() {
            val summary = testSummary(
                "sum-3",
                originalTitle = "AI breakthrough in healthcare",
                keywords = listOf("AI", "healthcare")
            )
            val rule = testRule(includeKeywords = listOf("AI"))
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-3")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            // INCLUDE 제안은 저장하지 않는다 (REVIEW, EXCLUDE만 저장)
            result.size shouldBe 0
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
        }

        @Test
        fun `중요도가 reviewThreshold 이상이고 includeThreshold 미만이면 REVIEW 결정을 생성한다`() {
            val summary = testSummary(
                "sum-4",
                importanceScore = 0.45f
            )
            val rule = testRule(
                includeThreshold = 0.55,
                reviewThreshold = 0.35,
                uncertainToReview = true
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-4")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers { decisionSlot.captured }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 1
            result["sum-4"]!!.status shouldBe ReviewDecisionStatus.REVIEW
            decisionSlot.captured.reason shouldContain "검토 임계치"
        }

        @Test
        fun `중요도가 모든 임계치 미만이고 uncertainToReview가 true이면 REVIEW 결정을 생성한다`() {
            val summary = testSummary("sum-5", importanceScore = 0.1f)
            val rule = testRule(
                includeThreshold = 0.55,
                reviewThreshold = 0.35,
                uncertainToReview = true,
                autoExcludeEnabled = false
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-5")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers { decisionSlot.captured }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 1
            result["sum-5"]!!.status shouldBe ReviewDecisionStatus.REVIEW
            decisionSlot.captured.reason shouldContain "자동 분류 확신 부족"
        }

        @Test
        fun `중요도가 모든 임계치 미만이고 uncertainToReview가 false이고 autoExclude가 true이면 EXCLUDE 결정을 생성한다`() {
            val summary = testSummary("sum-6", importanceScore = 0.1f)
            val rule = testRule(
                includeThreshold = 0.55,
                reviewThreshold = 0.35,
                uncertainToReview = false,
                autoExcludeEnabled = true
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-6")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers { decisionSlot.captured }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 1
            result["sum-6"]!!.status shouldBe ReviewDecisionStatus.EXCLUDE
            decisionSlot.captured.reason shouldContain "검토 임계치 미달 항목 자동 제외"
        }

        @Test
        fun `중요도가 모든 임계치 미만이고 uncertainToReview도 false이고 autoExclude도 false이면 INCLUDE 제안이므로 저장하지 않는다`() {
            val summary = testSummary("sum-7", importanceScore = 0.1f)
            val rule = testRule(
                includeThreshold = 0.55,
                reviewThreshold = 0.35,
                uncertainToReview = false,
                autoExcludeEnabled = false
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-7")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 0
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
        }

        @Test
        fun `중요도가 includeThreshold 이상이면 INCLUDE 제안이므로 저장하지 않는다`() {
            val summary = testSummary("sum-8", importanceScore = 0.8f)
            val rule = testRule(includeThreshold = 0.55, reviewThreshold = 0.35)
            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-8")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 0
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
        }

        @Test
        fun `여러 카테고리의 항목이 있으면 카테고리별 규칙을 캐시하여 조회한다`() {
            val summaryA = testSummary("sum-a", categoryId = "cat-A", importanceScore = 0.1f)
            val summaryB = testSummary("sum-b", categoryId = "cat-B", importanceScore = 0.1f)
            val summaryA2 = testSummary("sum-a2", categoryId = "cat-A", importanceScore = 0.1f)

            val ruleA = testRule(categoryId = "cat-A", uncertainToReview = true, autoExcludeEnabled = false)
            val ruleB = testRule(categoryId = "cat-B", uncertainToReview = false, autoExcludeEnabled = true)

            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-A") } returns ruleA
            every { categoryRuleService.getCategoryRule("cat-B") } returns ruleB
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            val result = service.ensurePolicyReviewDecisions(listOf(summaryA, summaryB, summaryA2))

            // cat-A 규칙은 1번만 조회되어야 한다 (캐시 검증)
            verify(exactly = 1) { categoryRuleService.getCategoryRule("cat-A") }
            verify(exactly = 1) { categoryRuleService.getCategoryRule("cat-B") }

            // cat-A: uncertainToReview=true → REVIEW 2건
            // cat-B: autoExcludeEnabled=true → EXCLUDE 1건
            result.size shouldBe 3
        }

        @Test
        fun `키워드 매칭은 대소문자를 구분하지 않는다`() {
            val summary = testSummary(
                "sum-case",
                originalTitle = "BREAKING NEWS About SCAM",
                summary = "some content"
            )
            val rule = testRule(
                excludeKeywords = listOf("scam"),
                autoExcludeEnabled = true
            )
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result.size shouldBe 1
            result["sum-case"]!!.status shouldBe ReviewDecisionStatus.EXCLUDE
        }

        @Test
        fun `키워드 매칭은 translatedTitle과 keywords 필드도 검사한다`() {
            val summary = testSummary(
                "sum-trans",
                originalTitle = "Neutral title",
                translatedTitle = "This has a scam word in it",
                summary = "clean summary",
                keywords = listOf("neutral")
            )
            val rule = testRule(
                excludeKeywords = listOf("scam"),
                autoExcludeEnabled = true
            )
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            val result = service.ensurePolicyReviewDecisions(listOf(summary))

            result["sum-trans"]!!.status shouldBe ReviewDecisionStatus.EXCLUDE
        }

        @Test
        fun `감사 이력에 fromStatus는 null이고 toStatus와 reviewedBy가 policy-auto로 기록된다`() {
            val summary = testSummary("sum-audit", importanceScore = 0.1f)
            val rule = testRule(uncertainToReview = true)
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            val auditSlot = slot<ReviewItemAudit>()
            every { reviewItemAuditStore.append(capture(auditSlot)) } answers { firstArg() }

            service.ensurePolicyReviewDecisions(listOf(summary))

            auditSlot.captured.fromStatus shouldBe null
            auditSlot.captured.toStatus shouldBe ReviewDecisionStatus.REVIEW
            auditSlot.captured.reviewedBy shouldBe "policy-auto"
        }
    }

    // ════════════════════════════════════════════
    // listReviewItems
    // ════════════════════════════════════════════

    @Nested
    inner class `listReviewItems 메서드` {

        @Test
        fun `limit가 0이면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.listReviewItems(null, null, 0)
            }
        }

        @Test
        fun `limit가 301이면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.listReviewItems(null, null, 301)
            }
        }

        @Test
        fun `limit가 -1이면 InvalidInputException을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.listReviewItems(null, null, -1)
            }
        }

        @Test
        fun `존재하지 않는 categoryId이면 NotFoundException을 던진다`() {
            every { categoryStore.findById("no-such-cat") } returns null

            shouldThrow<NotFoundException> {
                service.listReviewItems("no-such-cat", null, 10)
            }
        }

        @Test
        fun `지원하지 않는 status 문자열이면 InvalidInputException을 던진다`() {
            every { categoryStore.list() } returns emptyList()
            every { batchSummaryStore.findUnsent(null) } returns emptyList()

            shouldThrow<InvalidInputException> {
                service.listReviewItems(null, "INVALID_STATUS", 10)
            }
        }

        @Test
        fun `status가 ALL이면 전체 조회한다`() {
            every { categoryStore.list() } returns listOf(testCategory())
            every { batchSummaryStore.findUnsent(null) } returns emptyList()

            val result = service.listReviewItems(null, "ALL", 10)

            result.shouldBeEmpty()
        }

        @Test
        fun `status가 null이면 전체 조회한다`() {
            every { categoryStore.list() } returns listOf(testCategory())
            every { batchSummaryStore.findUnsent(null) } returns emptyList()

            val result = service.listReviewItems(null, null, 10)

            result.shouldBeEmpty()
        }

        @Test
        fun `unsent 요약이 없으면 빈 리스트를 반환한다`() {
            every { categoryStore.list() } returns listOf(testCategory())
            every { batchSummaryStore.findUnsent(null) } returns emptyList()

            val result = service.listReviewItems(null, null, 10)

            result.shouldBeEmpty()
        }

        @Test
        fun `정상 조회 시 우선순위에 따라 정렬된 결과를 반환한다`() {
            val now = Instant.now()
            val summaryHigh = testSummary("sum-high", importanceScore = 0.9f, createdAt = now)
            val summaryLow = testSummary("sum-low", importanceScore = 0.2f, createdAt = now)
            val rule = testRule(
                includeThreshold = 0.55,
                reviewThreshold = 0.35,
                uncertainToReview = true
            )

            every { categoryStore.list() } returns listOf(testCategory())
            every { batchSummaryStore.findUnsent(null) } returns listOf(summaryLow, summaryHigh)
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.listReviewItems(null, null, 10)

            result shouldHaveSize 2
            // REVIEW 항목(low)이 INCLUDE 항목(high)보다 우선순위가 높다 (priorityScore 기준)
            // sum-low는 REVIEW(90+) + importanceScore*40 + age bonus
            // sum-high는 INCLUDE(60) + importanceScore*40 + age bonus
        }

        @Test
        fun `categoryId가 지정되면 해당 카테고리만 조회한다`() {
            val cat = testCategory("cat-specific", "Specific Category")
            every { categoryStore.findById("cat-specific") } returns cat
            every { categoryStore.list() } returns listOf(cat)
            every { batchSummaryStore.findUnsent("cat-specific") } returns emptyList()

            val result = service.listReviewItems("cat-specific", null, 10)

            result.shouldBeEmpty()
            verify(exactly = 1) { batchSummaryStore.findUnsent("cat-specific") }
        }

        @Test
        fun `status 필터가 적용되면 해당 상태의 항목만 반환한다`() {
            val summaryInc = testSummary("sum-inc", importanceScore = 0.9f)
            val summaryRev = testSummary("sum-rev", importanceScore = 0.1f)
            val rule = testRule(
                includeThreshold = 0.55,
                reviewThreshold = 0.35,
                uncertainToReview = true
            )

            every { categoryStore.list() } returns listOf(testCategory())
            every { batchSummaryStore.findUnsent(null) } returns listOf(summaryInc, summaryRev)
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.listReviewItems(null, "REVIEW", 10)

            // sum-rev(importanceScore=0.1)는 reviewThreshold(0.35) 미만 + uncertainToReview=true → REVIEW
            // sum-inc(importanceScore=0.9)는 includeThreshold(0.55) 이상 → INCLUDE
            result.all { it.currentStatus == ReviewDecisionStatus.REVIEW } shouldBe true
        }

        @Test
        fun `limit로 결과 수가 제한된다`() {
            val summaries = (1..5).map { testSummary("sum-$it", importanceScore = 0.1f) }
            val rule = testRule(uncertainToReview = true)

            every { categoryStore.list() } returns listOf(testCategory())
            every { batchSummaryStore.findUnsent(null) } returns summaries
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.listReviewItems(null, null, 3)

            result shouldHaveSize 3
        }

        @Test
        fun `translatedTitle이 있으면 제목으로 사용하고 없으면 originalTitle을 사용한다`() {
            val summaryWithTranslation = testSummary(
                "sum-tr",
                originalTitle = "Original English Title",
                translatedTitle = "번역된 한글 제목",
                importanceScore = 0.9f
            )
            val summaryWithoutTranslation = testSummary(
                "sum-no-tr",
                originalTitle = "Only Original Title",
                translatedTitle = null,
                importanceScore = 0.9f
            )
            val rule = testRule(includeThreshold = 0.55)

            every { categoryStore.list() } returns listOf(testCategory())
            every { batchSummaryStore.findUnsent(null) } returns listOf(
                summaryWithTranslation,
                summaryWithoutTranslation
            )
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.listReviewItems(null, null, 10)

            val tr = result.first { it.summaryId == "sum-tr" }
            val noTr = result.first { it.summaryId == "sum-no-tr" }

            tr.title shouldBe "번역된 한글 제목"
            noTr.title shouldBe "Only Original Title"
        }

        @Test
        fun `기존 결정이 있으면 currentStatus에 반영하고 suggestedStatus와 독립적으로 표시한다`() {
            val summary = testSummary("sum-decided", importanceScore = 0.9f)
            val existingDecision = ReviewItemDecision(
                summaryId = "sum-decided",
                categoryId = "cat-1",
                status = ReviewDecisionStatus.EXCLUDE,
                reason = "관리자 제외",
                reviewedBy = "admin"
            )
            val rule = testRule(includeThreshold = 0.55)

            every { categoryStore.list() } returns listOf(testCategory())
            every { batchSummaryStore.findUnsent(null) } returns listOf(summary)
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns listOf(existingDecision)
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.listReviewItems(null, null, 10)

            result shouldHaveSize 1
            val item = result.first()
            // 제안은 INCLUDE (importanceScore=0.9 > 0.55)이지만
            // 기존 결정이 EXCLUDE이므로 currentStatus는 EXCLUDE
            item.suggestedStatus shouldBe ReviewDecisionStatus.INCLUDE
            item.currentStatus shouldBe ReviewDecisionStatus.EXCLUDE
            item.statusReason shouldBe "관리자 제외"
            item.reviewedBy shouldBe "admin"
        }

        @Test
        fun `categoryName은 categoryStore에서 조회한 이름이 사용된다`() {
            val summary = testSummary("sum-name", importanceScore = 0.9f)
            val cat = testCategory("cat-1", "My Cool Category")
            val rule = testRule(includeThreshold = 0.55)

            every { categoryStore.list() } returns listOf(cat)
            every { batchSummaryStore.findUnsent(null) } returns listOf(summary)
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            val result = service.listReviewItems(null, null, 10)

            result.first().categoryName shouldBe "My Cool Category"
        }
    }
}
