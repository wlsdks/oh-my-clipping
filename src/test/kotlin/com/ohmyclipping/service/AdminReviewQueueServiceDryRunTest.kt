package com.ohmyclipping.service

import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.BatchSummary
import com.ohmyclipping.model.Category
import com.ohmyclipping.model.CategoryRule
import com.ohmyclipping.service.dto.RuleEvaluationResult
import com.ohmyclipping.service.query.ReviewPolicyQueryHelper
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.ReviewItemAuditStore
import com.ohmyclipping.store.ReviewItemDecisionStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [AdminReviewQueueService.dryRunRule] 단위 테스트.
 *
 * 검증 관점:
 *  - 기존 rule 을 base 로 proposed blacklist 만 override 해서 evaluator 에 넘긴다
 *  - analyzedCount / wouldAutoExclude / wouldStayUnchanged 집계가 정확하다
 *  - 카테고리 없음 → NotFoundException
 *  - days/maxSamples 입력 범위 검증 → InvalidInputException
 *  - DB 에 결정/감사 이력을 쓰지 않는다 (upsert/append 가 호출되지 않아야 함)
 *  - 샘플은 maxSamples 까지만 반환 (상한 준수)
 */
class AdminReviewQueueServiceDryRunTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>()
    private val categoryStore = mockk<CategoryStore>()
    private val categoryRuleService = mockk<AdminCategoryRuleService>()
    private val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>(relaxed = true)
    private val reviewItemAuditStore = mockk<ReviewItemAuditStore>(relaxed = true)
    private val reviewPolicyQueryHelper = mockk<ReviewPolicyQueryHelper>()
    private val ruleEvaluator = mockk<ReviewPolicyRuleEvaluator>()

    private val service = AdminReviewQueueService(
        batchSummaryStore = batchSummaryStore,
        categoryStore = categoryStore,
        categoryRuleService = categoryRuleService,
        reviewItemDecisionStore = reviewItemDecisionStore,
        reviewItemAuditStore = reviewItemAuditStore,
        reviewPolicyQueryHelper = reviewPolicyQueryHelper,
        ruleEvaluator = ruleEvaluator,
    )

    private val testCategoryId = "cat-dry-run"

    private fun testCategory() = Category(id = testCategoryId, name = "Dry-Run Test Category")

    private fun testRule(excludeEventTypes: List<String> = emptyList()) =
        CategoryRule(categoryId = testCategoryId, excludeEventTypes = excludeEventTypes)

    private fun testSummary(
        id: String,
        originalTitle: String = "Article $id",
        translatedTitle: String? = null,
        eventType: String? = null,
        importanceScore: Float = 0.5f,
    ) = BatchSummary(
        id = id,
        originalTitle = originalTitle,
        translatedTitle = translatedTitle,
        summary = "Summary for $id",
        sourceLink = "https://example.com/$id",
        categoryId = testCategoryId,
        rssItemId = "item-$id",
        eventType = eventType,
        importanceScore = importanceScore,
        createdAt = Instant.now(),
    )

    @Nested
    inner class `dryRunRule 정상 경로` {

        @Test
        fun `evaluator 의 Exclude 결과를 집계하고 샘플을 반환한다`() {
            // Given: 3 건 summary — 그 중 2 건을 evaluator 가 Exclude 로 판정
            val s1 = testSummary("s1", eventType = "OPINION", importanceScore = 0.9f)
            val s2 = testSummary("s2", eventType = "OTHER", importanceScore = 0.2f)
            val s3 = testSummary("s3", eventType = "NEWS", importanceScore = 0.7f)

            every { categoryStore.findById(testCategoryId) } returns testCategory()
            every { categoryRuleService.getCategoryRule(testCategoryId) } returns testRule()
            every { batchSummaryStore.findByDateRange(any(), any(), testCategoryId) } returns listOf(s1, s2, s3)
            every { ruleEvaluator.evaluate(s1, any(), any()) } returns
                RuleEvaluationResult.Exclude("event_type_blacklist")
            every { ruleEvaluator.evaluate(s2, any(), any()) } returns
                RuleEvaluationResult.Exclude("zero_signal")
            every { ruleEvaluator.evaluate(s3, any(), any()) } returns RuleEvaluationResult.PassThrough

            // When
            val result = service.dryRunRule(
                categoryId = testCategoryId,
                proposedExcludeEventTypes = listOf("OPINION"),
            )

            // Then: 집계가 정확하다
            result.analyzedCount shouldBe 3
            result.wouldAutoExclude shouldBe 2
            result.wouldStayUnchanged shouldBe 1
            result.samples shouldHaveSize 2
            result.samples.map { it.summaryId } shouldBe listOf("s1", "s2")
            result.samples.first().reason shouldBe "event_type_blacklist"
            result.samples.first().eventType shouldBe "OPINION"
            result.samples.first().score shouldBe 0.9f
        }

        @Test
        fun `proposed blacklist 는 기존 rule 의 excludeEventTypes 를 override 한다`() {
            // Given: 기존 rule 은 OPINION 만, 제안은 OPINION + SPECULATIVE
            val summary = testSummary("sx", eventType = "SPECULATIVE", importanceScore = 0.6f)
            val existingRule = testRule(excludeEventTypes = listOf("OPINION"))
            val capturedRule = slotRule()

            every { categoryStore.findById(testCategoryId) } returns testCategory()
            every { categoryRuleService.getCategoryRule(testCategoryId) } returns existingRule
            every { batchSummaryStore.findByDateRange(any(), any(), testCategoryId) } returns listOf(summary)
            every { ruleEvaluator.evaluate(any(), any(), capture(capturedRule)) } returns
                RuleEvaluationResult.PassThrough

            // When: 제안된 blacklist 로 호출
            service.dryRunRule(
                categoryId = testCategoryId,
                proposedExcludeEventTypes = listOf("OPINION", "SPECULATIVE"),
            )

            // Then: evaluator 에 넘어간 rule 은 proposed 값으로 override 되어 있다
            capturedRule.captured.excludeEventTypes shouldBe listOf("OPINION", "SPECULATIVE")
        }

        @Test
        fun `maxSamples 상한을 초과하는 Exclude 결과는 잘라낸다`() {
            // Given: 5 건 모두 Exclude 로 판정되지만 maxSamples=2 로 요청
            val summaries = (1..5).map { testSummary("m$it", eventType = "OPINION") }
            every { categoryStore.findById(testCategoryId) } returns testCategory()
            every { categoryRuleService.getCategoryRule(testCategoryId) } returns testRule()
            every { batchSummaryStore.findByDateRange(any(), any(), testCategoryId) } returns summaries
            every { ruleEvaluator.evaluate(any(), any(), any()) } returns
                RuleEvaluationResult.Exclude("event_type_blacklist")

            // When
            val result = service.dryRunRule(
                categoryId = testCategoryId,
                proposedExcludeEventTypes = listOf("OPINION"),
                maxSamples = 2,
            )

            // Then: analyzed/exclude 카운트는 전체를 반영하지만 샘플은 2 개까지만
            result.analyzedCount shouldBe 5
            result.wouldAutoExclude shouldBe 5
            result.samples shouldHaveSize 2
        }

        @Test
        fun `DB 에 결정이나 감사 이력을 쓰지 않는다 (read-only)`() {
            // Given
            val summary = testSummary("r1", eventType = "OPINION")
            every { categoryStore.findById(testCategoryId) } returns testCategory()
            every { categoryRuleService.getCategoryRule(testCategoryId) } returns testRule()
            every { batchSummaryStore.findByDateRange(any(), any(), testCategoryId) } returns listOf(summary)
            every { ruleEvaluator.evaluate(any(), any(), any()) } returns
                RuleEvaluationResult.Exclude("event_type_blacklist")

            // When
            service.dryRunRule(
                categoryId = testCategoryId,
                proposedExcludeEventTypes = listOf("OPINION"),
            )

            // Then: persistence stores 는 한 번도 호출되지 않아야 한다
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 0) { reviewItemAuditStore.append(any()) }
            verify(exactly = 0) { reviewItemAuditStore.batchAppend(any()) }
        }

        @Test
        fun `빈 summary 목록에도 안전하게 동작하고 zero 집계를 반환한다`() {
            every { categoryStore.findById(testCategoryId) } returns testCategory()
            every { categoryRuleService.getCategoryRule(testCategoryId) } returns testRule()
            every { batchSummaryStore.findByDateRange(any(), any(), testCategoryId) } returns emptyList()

            val result = service.dryRunRule(
                categoryId = testCategoryId,
                proposedExcludeEventTypes = listOf("OPINION"),
            )

            result.analyzedCount shouldBe 0
            result.wouldAutoExclude shouldBe 0
            result.wouldStayUnchanged shouldBe 0
            result.samples.shouldBeEmpty()
        }

        @Test
        fun `translatedTitle 이 있으면 샘플 title 로 사용된다`() {
            val summary = testSummary(
                "t1",
                originalTitle = "English headline",
                translatedTitle = "번역된 제목",
                eventType = "OPINION",
            )
            every { categoryStore.findById(testCategoryId) } returns testCategory()
            every { categoryRuleService.getCategoryRule(testCategoryId) } returns testRule()
            every { batchSummaryStore.findByDateRange(any(), any(), testCategoryId) } returns listOf(summary)
            every { ruleEvaluator.evaluate(any(), any(), any()) } returns
                RuleEvaluationResult.Exclude("event_type_blacklist")

            val result = service.dryRunRule(
                categoryId = testCategoryId,
                proposedExcludeEventTypes = listOf("OPINION"),
            )

            result.samples.first().title shouldBe "번역된 제목"
        }
    }

    @Nested
    inner class `dryRunRule 에러 경로` {

        @Test
        fun `존재하지 않는 카테고리는 NotFoundException 을 던진다`() {
            every { categoryStore.findById("missing") } returns null

            val ex = shouldThrow<NotFoundException> {
                service.dryRunRule(
                    categoryId = "missing",
                    proposedExcludeEventTypes = emptyList(),
                )
            }
            ex.message.orEmpty() shouldContain "missing"
        }

        @Test
        fun `days 범위 초과 시 InvalidInputException 을 던진다`() {
            // 카테고리 조회/룰 조회는 days 검증 이후 실행되므로 mock 필요 없음
            shouldThrow<InvalidInputException> {
                service.dryRunRule(
                    categoryId = testCategoryId,
                    proposedExcludeEventTypes = emptyList(),
                    days = 0,
                )
            }
            shouldThrow<InvalidInputException> {
                service.dryRunRule(
                    categoryId = testCategoryId,
                    proposedExcludeEventTypes = emptyList(),
                    days = 91,
                )
            }
        }

        @Test
        fun `maxSamples 범위 초과 시 InvalidInputException 을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.dryRunRule(
                    categoryId = testCategoryId,
                    proposedExcludeEventTypes = emptyList(),
                    maxSamples = 0,
                )
            }
            shouldThrow<InvalidInputException> {
                service.dryRunRule(
                    categoryId = testCategoryId,
                    proposedExcludeEventTypes = emptyList(),
                    maxSamples = 51,
                )
            }
        }
    }

    private fun slotRule() = io.mockk.slot<CategoryRule>()
}
