package com.clipping.mcpserver.service

import com.clipping.mcpserver.service.dto.BulkRevertItem
import com.clipping.mcpserver.service.query.ReviewPolicyQueryHelper
import com.clipping.mcpserver.error.NotFoundException
import com.clipping.mcpserver.model.*
import com.clipping.mcpserver.service.dto.clipping.*
import com.clipping.mcpserver.store.BatchSummaryStore
import com.clipping.mcpserver.store.CategoryStore
import com.clipping.mcpserver.store.ReviewItemAuditStore
import com.clipping.mcpserver.store.ReviewItemDecisionStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdminReviewQueueServiceTest {

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

    @Nested
    inner class `approve 메서드` {

        @Test
        fun `존재하지 않는 summaryId이면 NotFoundException을 던진다`() {
            every { batchSummaryStore.findById("missing-summary") } returns null

            val exception = shouldThrow<NotFoundException> {
                service.approve("missing-summary", "승인 사유", "admin")
            }

            exception.message shouldBe "Batch summary not found: missing-summary"
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 0) { reviewItemAuditStore.append(any()) }
        }

        @Test
        fun `정상 승인 시 INCLUDE 상태로 전이하고 감사 이력을 저장한다`() {
            val summary = testSummary("sum-1")
            every { batchSummaryStore.findById("sum-1") } returns summary
            every { reviewItemDecisionStore.findBySummaryId("sum-1") } returns null
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers {
                decisionSlot.captured
            }
            val auditSlot = slot<ReviewItemAudit>()
            every { reviewItemAuditStore.append(capture(auditSlot)) } answers {
                auditSlot.captured.copy(id = "audit-1")
            }

            val result = service.approve("sum-1", "좋은 기사", "admin-user")

            result.status shouldBe ReviewDecisionStatus.INCLUDE
            result.summaryId shouldBe "sum-1"
            result.categoryId shouldBe "cat-1"
            result.reason shouldBe "좋은 기사"
            result.reviewedBy shouldBe "admin-user"

            // 감사 이력 검증
            auditSlot.captured.summaryId shouldBe "sum-1"
            auditSlot.captured.fromStatus shouldBe null
            auditSlot.captured.toStatus shouldBe ReviewDecisionStatus.INCLUDE
            auditSlot.captured.reason shouldBe "좋은 기사"
            auditSlot.captured.reviewedBy shouldBe "admin-user"
        }

        @Test
        fun `기존 결정이 있으면 fromStatus에 이전 상태가 기록된다`() {
            val summary = testSummary("sum-2")
            val previousDecision = ReviewItemDecision(
                summaryId = "sum-2",
                categoryId = "cat-1",
                status = ReviewDecisionStatus.REVIEW,
                reason = "검토 필요",
                reviewedBy = "policy-auto"
            )
            every { batchSummaryStore.findById("sum-2") } returns summary
            every { reviewItemDecisionStore.findBySummaryId("sum-2") } returns previousDecision
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            val auditSlot = slot<ReviewItemAudit>()
            every { reviewItemAuditStore.append(capture(auditSlot)) } answers {
                auditSlot.captured.copy(id = "audit-2")
            }

            service.approve("sum-2", "승인", "admin-user")

            auditSlot.captured.fromStatus shouldBe ReviewDecisionStatus.REVIEW
            auditSlot.captured.toStatus shouldBe ReviewDecisionStatus.INCLUDE
        }

        @Test
        fun `reason이 공백만 있으면 null로 정규화된다`() {
            val summary = testSummary("sum-3")
            every { batchSummaryStore.findById("sum-3") } returns summary
            every { reviewItemDecisionStore.findBySummaryId("sum-3") } returns null
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers {
                decisionSlot.captured
            }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            val result = service.approve("sum-3", "   ", "admin")

            result.reason shouldBe null
        }
    }

    @Nested
    inner class `exclude 메서드` {

        @Test
        fun `존재하지 않는 summaryId이면 NotFoundException을 던진다`() {
            every { batchSummaryStore.findById("missing") } returns null

            val exception = shouldThrow<NotFoundException> {
                service.exclude("missing", "제외 사유", "admin")
            }

            exception.message shouldBe "Batch summary not found: missing"
        }

        @Test
        fun `정상 제외 시 EXCLUDE 상태로 전이하고 감사 이력을 저장한다`() {
            val summary = testSummary("sum-ex")
            every { batchSummaryStore.findById("sum-ex") } returns summary
            every { reviewItemDecisionStore.findBySummaryId("sum-ex") } returns null
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers {
                decisionSlot.captured
            }
            val auditSlot = slot<ReviewItemAudit>()
            every { reviewItemAuditStore.append(capture(auditSlot)) } answers {
                auditSlot.captured.copy(id = "audit-ex")
            }

            val result = service.exclude("sum-ex", "관련성 없음", "admin-user")

            result.status shouldBe ReviewDecisionStatus.EXCLUDE
            result.summaryId shouldBe "sum-ex"
            result.reason shouldBe "관련성 없음"

            auditSlot.captured.toStatus shouldBe ReviewDecisionStatus.EXCLUDE
            auditSlot.captured.reason shouldBe "관련성 없음"
            verify(exactly = 1) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 1) { reviewItemAuditStore.append(any()) }
        }

        @Test
        fun `INCLUDE에서 EXCLUDE로 상태 전이 시 fromStatus가 INCLUDE이다`() {
            val summary = testSummary("sum-ie")
            val previousDecision = ReviewItemDecision(
                summaryId = "sum-ie",
                categoryId = "cat-1",
                status = ReviewDecisionStatus.INCLUDE,
                reviewedBy = "admin"
            )
            every { batchSummaryStore.findById("sum-ie") } returns summary
            every { reviewItemDecisionStore.findBySummaryId("sum-ie") } returns previousDecision
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            val auditSlot = slot<ReviewItemAudit>()
            every { reviewItemAuditStore.append(capture(auditSlot)) } answers {
                auditSlot.captured.copy(id = "audit-ie")
            }

            service.exclude("sum-ie", "재검토 결과 제외", "admin-reviewer")

            auditSlot.captured.fromStatus shouldBe ReviewDecisionStatus.INCLUDE
            auditSlot.captured.toStatus shouldBe ReviewDecisionStatus.EXCLUDE
        }
    }

    @Nested
    inner class `markReview 메서드` {

        @Test
        fun `정상 호출 시 REVIEW 상태로 전이한다`() {
            val summary = testSummary("sum-mr")
            every { batchSummaryStore.findById("sum-mr") } returns summary
            every { reviewItemDecisionStore.findBySummaryId("sum-mr") } returns ReviewItemDecision(
                summaryId = "sum-mr",
                categoryId = "cat-1",
                status = ReviewDecisionStatus.INCLUDE
            )
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            val auditSlot = slot<ReviewItemAudit>()
            every { reviewItemAuditStore.append(capture(auditSlot)) } answers {
                auditSlot.captured.copy(id = "audit-mr")
            }

            val result = service.markReview("sum-mr", "재검토 필요", "admin")

            result.status shouldBe ReviewDecisionStatus.REVIEW
            auditSlot.captured.fromStatus shouldBe ReviewDecisionStatus.INCLUDE
            auditSlot.captured.toStatus shouldBe ReviewDecisionStatus.REVIEW
        }
    }

    @Nested
    inner class `listAudits 메서드` {

        @Test
        fun `존재하지 않는 summaryId이면 NotFoundException을 던진다`() {
            every { batchSummaryStore.findById("missing") } returns null

            val exception = shouldThrow<NotFoundException> {
                service.listAudits("missing", 10)
            }

            exception.message shouldBe "Batch summary not found: missing"
        }
    }

    @Nested
    inner class `autoApprove 정책` {

        @Test
        fun `importance가 autoApproveThreshold 이상이면 INCLUDE가 자동 저장된다`() {
            // importance=0.9, 임계값=0.85 → INCLUDE 자동 승인 대상
            val summary = testSummary("sum-auto-ok").copy(importanceScore = 0.9f)
            val rule = CategoryRule(categoryId = "cat-1", autoApproveThreshold = 0.85)

            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-auto-ok")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers {
                decisionSlot.captured
            }
            val auditSlot = slot<ReviewItemAudit>()
            every { reviewItemAuditStore.append(capture(auditSlot)) } answers {
                auditSlot.captured.copy(id = "audit-auto")
            }

            service.ensurePolicyReviewDecisions(listOf(summary))

            decisionSlot.captured.status shouldBe ReviewDecisionStatus.INCLUDE
            decisionSlot.captured.suggestedStatus shouldBe ReviewDecisionStatus.INCLUDE
            decisionSlot.captured.reviewedBy shouldBe "policy-auto"
            // 자동 승인 감사 이력은 INCLUDE 전이로 기록되어야 한다
            auditSlot.captured.toStatus shouldBe ReviewDecisionStatus.INCLUDE
            auditSlot.captured.reviewedBy shouldBe "policy-auto"
        }

        @Test
        fun `importance가 정확히 autoApproveThreshold와 같으면 INCLUDE가 자동 저장된다`() {
            // 경계값: importance == threshold → `>=` 비교이므로 INCLUDE 대상
            val summary = testSummary("sum-boundary").copy(importanceScore = 0.85f)
            val rule = CategoryRule(categoryId = "cat-1", autoApproveThreshold = 0.85)

            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-boundary")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers {
                decisionSlot.captured
            }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            service.ensurePolicyReviewDecisions(listOf(summary))

            decisionSlot.captured.status shouldBe ReviewDecisionStatus.INCLUDE
        }

        @Test
        fun `importance가 autoApproveThreshold 미만이면 INCLUDE 제안은 저장되지 않는다`() {
            // importance=0.84, 임계값=0.85 → INCLUDE 제안이지만 임계값 미달 → 기존 동작(미저장) 유지
            // 참고: includeThreshold(0.55) 이상이므로 INCLUDE 제안
            val summary = testSummary("sum-below").copy(importanceScore = 0.84f)
            val rule = CategoryRule(categoryId = "cat-1", autoApproveThreshold = 0.85)

            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-below")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            service.ensurePolicyReviewDecisions(listOf(summary))

            // INCLUDE 제안이지만 임계값 미달이므로 upsert 호출되지 않음
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 0) { reviewItemAuditStore.append(any()) }
        }

        @Test
        fun `autoApproveThreshold가 null이면 importance가 높아도 INCLUDE는 저장되지 않는다`() {
            // threshold=null(비활성) → importance=0.99여도 기존 동작 유지(미저장)
            val summary = testSummary("sum-null-thr").copy(importanceScore = 0.99f)
            val rule = CategoryRule(categoryId = "cat-1", autoApproveThreshold = null)

            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-null-thr")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            service.ensurePolicyReviewDecisions(listOf(summary))

            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
        }

        @Test
        fun `REVIEW 제안은 autoApproveThreshold와 무관하게 기존대로 REVIEW로 저장된다`() {
            // importance=0.4 → REVIEW 제안. threshold=0.85가 설정되어도 REVIEW는 그대로 저장
            val summary = testSummary("sum-review").copy(importanceScore = 0.4f)
            val rule = CategoryRule(categoryId = "cat-1", autoApproveThreshold = 0.85)

            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-review")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers {
                decisionSlot.captured
            }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            service.ensurePolicyReviewDecisions(listOf(summary))

            decisionSlot.captured.status shouldBe ReviewDecisionStatus.REVIEW
        }
    }

    @Nested
    inner class `listReviewItems perCategory 샘플링` {

        @Test
        fun `perCategory 지정 시 카테고리별 top-N씩 뽑아 합집합을 반환한다`() {
            // 3개 카테고리에 각 5/3/2건 있으면 perCategory=2 → 총 6건이 반환돼야 한다(카테고리당 2건).
            val summaries = listOf(
                // cat-1 (5건)
                summaryWithScore("s1-1", "cat-1", 0.9f),
                summaryWithScore("s1-2", "cat-1", 0.8f),
                summaryWithScore("s1-3", "cat-1", 0.7f),
                summaryWithScore("s1-4", "cat-1", 0.6f),
                summaryWithScore("s1-5", "cat-1", 0.5f),
                // cat-2 (3건)
                summaryWithScore("s2-1", "cat-2", 0.9f),
                summaryWithScore("s2-2", "cat-2", 0.8f),
                summaryWithScore("s2-3", "cat-2", 0.7f),
                // cat-3 (2건)
                summaryWithScore("s3-1", "cat-3", 0.9f),
                summaryWithScore("s3-2", "cat-3", 0.8f)
            )

            every { batchSummaryStore.findUnsent(null) } returns summaries
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryStore.list() } returns listOf(
                com.clipping.mcpserver.model.Category(id = "cat-1", name = "카테고리1"),
                com.clipping.mcpserver.model.Category(id = "cat-2", name = "카테고리2"),
                com.clipping.mcpserver.model.Category(id = "cat-3", name = "카테고리3")
            )
            every { categoryRuleService.getCategoryRule(any()) } returns CategoryRule(categoryId = "cat-1")

            val result = service.listReviewItems(
                categoryId = null,
                statusRaw = null,
                limit = 100,
                perCategory = 2
            )

            // 각 카테고리당 최대 2개씩, 총 6개
            result.size shouldBe 6
            result.count { it.categoryId == "cat-1" } shouldBe 2
            result.count { it.categoryId == "cat-2" } shouldBe 2
            result.count { it.categoryId == "cat-3" } shouldBe 2
            // 중복 없이 고유 ID
            result.map { it.summaryId }.toSet().size shouldBe 6
        }

        @Test
        fun `perCategory가 0이면 InvalidInputException을 던진다`() {
            val ex = shouldThrow<com.clipping.mcpserver.error.InvalidInputException> {
                service.listReviewItems(null, null, limit = 100, perCategory = 0)
            }
            ex.message shouldBe "perCategory must be between 1 and limit(100)"
        }

        @Test
        fun `perCategory가 limit 초과면 InvalidInputException을 던진다`() {
            val ex = shouldThrow<com.clipping.mcpserver.error.InvalidInputException> {
                service.listReviewItems(null, null, limit = 10, perCategory = 11)
            }
            ex.message shouldBe "perCategory must be between 1 and limit(10)"
        }

        @Test
        fun `categoryId 지정 시 perCategory는 무시되고 기존 동작대로 limit까지 반환한다`() {
            // cat-1에 3건 모두 존재 → categoryId=cat-1, perCategory=1이어도 3건 전부 반환 (필터링 없이)
            val summaries = listOf(
                summaryWithScore("s-a", "cat-1", 0.9f),
                summaryWithScore("s-b", "cat-1", 0.8f),
                summaryWithScore("s-c", "cat-1", 0.7f)
            )
            every { categoryStore.findById("cat-1") } returns
                com.clipping.mcpserver.model.Category(id = "cat-1", name = "카테고리1")
            every { batchSummaryStore.findUnsent("cat-1") } returns summaries
            every { reviewItemDecisionStore.findBySummaryIds(any()) } returns emptyList()
            every { categoryStore.list() } returns listOf(
                com.clipping.mcpserver.model.Category(id = "cat-1", name = "카테고리1")
            )
            every { categoryRuleService.getCategoryRule("cat-1") } returns CategoryRule(categoryId = "cat-1")

            val result = service.listReviewItems(
                categoryId = "cat-1",
                statusRaw = null,
                limit = 100,
                perCategory = 1
            )

            // perCategory=1이었더라도 categoryId 지정 시 무시되므로 3건 모두 반환
            result.size shouldBe 3
        }
    }

    @Nested
    inner class `suggestedStatus 영구 저장` {

        @Test
        fun `ensurePolicyReviewDecisions 호출 시 suggestedStatus가 저장된다`() {
            // importanceScore=0.4 → reviewThreshold(0.35) 이상, includeThreshold(0.55) 미만 → REVIEW 제안
            val summary = testSummary("sum-suggest").copy(importanceScore = 0.4f)
            val rule = CategoryRule(categoryId = "cat-1")

            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-suggest")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers {
                decisionSlot.captured
            }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            service.ensurePolicyReviewDecisions(listOf(summary))

            // suggestedStatus가 제안 상태(REVIEW)와 일치하는지 검증
            decisionSlot.captured.suggestedStatus shouldBe ReviewDecisionStatus.REVIEW
            decisionSlot.captured.status shouldBe ReviewDecisionStatus.REVIEW
        }

        @Test
        fun `importanceScore가 includeThreshold 이상이면 suggestedStatus가 INCLUDE이고 결정이 저장되지 않는다`() {
            // importanceScore=0.9 → INCLUDE 제안 → ensurePolicyReviewDecisions는 INCLUDE에 대해 저장 안 함
            val summary = testSummary("sum-include").copy(importanceScore = 0.9f)
            val rule = CategoryRule(categoryId = "cat-1")

            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-include")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule

            service.ensurePolicyReviewDecisions(listOf(summary))

            // INCLUDE 제안이므로 upsert를 호출하지 않는다
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
        }

        @Test
        fun `autoExcludeEnabled이면 reviewThreshold 미달 항목의 suggestedStatus가 EXCLUDE로 저장된다`() {
            // importanceScore=0.1 → reviewThreshold(0.35) 미달, uncertainToReview=false → autoExcludeEnabled → EXCLUDE 제안
            val summary = testSummary("sum-auto-ex").copy(importanceScore = 0.1f)
            val rule = CategoryRule(categoryId = "cat-1", autoExcludeEnabled = true, uncertainToReview = false)

            every { reviewItemDecisionStore.findBySummaryIds(listOf("sum-auto-ex")) } returns emptyList()
            every { categoryRuleService.getCategoryRule("cat-1") } returns rule
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers {
                decisionSlot.captured
            }
            every { reviewItemAuditStore.append(any()) } answers { firstArg() }

            service.ensurePolicyReviewDecisions(listOf(summary))

            decisionSlot.captured.suggestedStatus shouldBe ReviewDecisionStatus.EXCLUDE
            decisionSlot.captured.status shouldBe ReviewDecisionStatus.EXCLUDE
        }
    }

    @Nested
    inner class `bulkApprove 메서드` {

        @Test
        fun `3개 항목이 모두 REVIEW 상태이면 전부 성공하고 batchAppend를 한 번 호출한다`() {
            val ids = listOf("id-1", "id-2", "id-3")
            val decisions = ids.map {
                ReviewItemDecision(summaryId = it, categoryId = "cat-1", status = ReviewDecisionStatus.REVIEW)
            }
            every { reviewItemDecisionStore.findBySummaryIds(ids) } returns decisions
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            val auditListSlot = slot<List<ReviewItemAudit>>()
            every { reviewItemAuditStore.batchAppend(capture(auditListSlot)) } answers { firstArg() }

            val result = service.bulkApprove(ids, "일괄 승인")

            result.succeeded shouldBe ids
            result.failed shouldBe emptyList()
            verify(exactly = 3) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 1) { reviewItemAuditStore.batchAppend(any()) }
            auditListSlot.captured.size shouldBe 3
            auditListSlot.captured.all { it.toStatus == ReviewDecisionStatus.INCLUDE } shouldBe true
        }

        @Test
        fun `이미 INCLUDE 상태인 항목은 ALREADY_PROCESSED로 실패 처리한다`() {
            val alreadyIncluded = ReviewItemDecision(
                summaryId = "id-already",
                categoryId = "cat-1",
                status = ReviewDecisionStatus.INCLUDE
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("id-already")) } returns listOf(alreadyIncluded)

            val result = service.bulkApprove(listOf("id-already"), null)

            result.succeeded shouldBe emptyList()
            result.failed.size shouldBe 1
            result.failed[0].id shouldBe "id-already"
            result.failed[0].code shouldBe "ALREADY_PROCESSED"
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 0) { reviewItemAuditStore.batchAppend(any()) }
        }

        @Test
        fun `ids가 101개이면 InvalidInputException을 던진다`() {
            val ids = (1..101).map { "id-$it" }

            val ex = io.kotest.assertions.throwables.shouldThrow<
                com.clipping.mcpserver.error.InvalidInputException
            > {
                service.bulkApprove(ids, null)
            }

            ex.message shouldBe "벌크 처리는 1~100건까지 가능합니다 (요청: 101건)"
        }

        @Test
        fun `존재하지 않는 summaryId가 섞이면 NOT_FOUND 실패로 분리되고 나머지는 커밋된다`() {
            val ids = listOf("id-valid", "id-ghost")
            every { reviewItemDecisionStore.findBySummaryIds(ids) } returns emptyList()
            every { batchSummaryStore.findByIds(ids) } returns listOf(testSummary("id-valid"))
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            every { reviewItemAuditStore.batchAppend(any()) } answers { firstArg() }

            val result = service.bulkApprove(ids, null)

            result.succeeded shouldBe listOf("id-valid")
            result.failed shouldHaveSize 1
            result.failed[0].id shouldBe "id-ghost"
            result.failed[0].code shouldBe "NOT_FOUND"
        }
    }

    @Nested
    inner class `bulkExclude 메서드` {

        @Test
        fun `3개 항목 중 1개가 이미 EXCLUDE이면 2개 성공 + 1개 실패를 반환한다`() {
            val ids = listOf("ex-1", "ex-2", "ex-3")
            val decisions = listOf(
                ReviewItemDecision(summaryId = "ex-1", categoryId = "cat-1", status = ReviewDecisionStatus.REVIEW),
                ReviewItemDecision(summaryId = "ex-2", categoryId = "cat-1", status = ReviewDecisionStatus.EXCLUDE),
                ReviewItemDecision(summaryId = "ex-3", categoryId = "cat-1", status = ReviewDecisionStatus.INCLUDE)
            )
            every { reviewItemDecisionStore.findBySummaryIds(ids) } returns decisions
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            every { reviewItemAuditStore.batchAppend(any()) } answers { firstArg() }

            val result = service.bulkExclude(ids, "일괄 제외")

            result.succeeded shouldBe listOf("ex-1", "ex-3")
            result.failed.size shouldBe 1
            result.failed[0].id shouldBe "ex-2"
            result.failed[0].code shouldBe "ALREADY_PROCESSED"
            verify(exactly = 2) { reviewItemDecisionStore.upsert(any()) }
        }
    }

    @Nested
    inner class `bulkRevert 메서드` {

        @Test
        fun `2개 항목이 서로 다른 previousStatus(REVIEW, EXCLUDE)로 각각 복원된다`() {
            val reverts = listOf(
                BulkRevertItem(id = "rv-1", previousStatus = "REVIEW"),
                BulkRevertItem(id = "rv-2", previousStatus = "EXCLUDE")
            )
            val decisions = listOf(
                ReviewItemDecision(summaryId = "rv-1", categoryId = "cat-1", status = ReviewDecisionStatus.INCLUDE),
                ReviewItemDecision(summaryId = "rv-2", categoryId = "cat-1", status = ReviewDecisionStatus.INCLUDE)
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("rv-1", "rv-2")) } returns decisions
            val upsertSlots = mutableListOf<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(upsertSlots)) } answers { firstArg() }
            val auditListSlot = slot<List<ReviewItemAudit>>()
            every { reviewItemAuditStore.batchAppend(capture(auditListSlot)) } answers { firstArg() }

            val result = service.bulkRevert(reverts)

            // 두 항목 모두 성공해야 한다
            result.succeeded shouldBe listOf("rv-1", "rv-2")
            result.failed shouldBe emptyList()

            // rv-1은 REVIEW, rv-2는 EXCLUDE — 각자의 previousStatus로 복원되었는지 검증
            val rv1Decision = upsertSlots.first { it.summaryId == "rv-1" }
            val rv2Decision = upsertSlots.first { it.summaryId == "rv-2" }
            rv1Decision.status shouldBe ReviewDecisionStatus.REVIEW
            rv2Decision.status shouldBe ReviewDecisionStatus.EXCLUDE

            // 감사 이력도 개별 toStatus가 일치해야 한다
            val rv1Audit = auditListSlot.captured.first { it.summaryId == "rv-1" }
            val rv2Audit = auditListSlot.captured.first { it.summaryId == "rv-2" }
            rv1Audit.toStatus shouldBe ReviewDecisionStatus.REVIEW
            rv2Audit.toStatus shouldBe ReviewDecisionStatus.EXCLUDE

            verify(exactly = 1) { reviewItemAuditStore.batchAppend(any()) }
        }

        @Test
        fun `잘못된 previousStatus 값이 포함된 항목은 INVALID_STATUS로 실패 처리된다`() {
            val reverts = listOf(
                BulkRevertItem(id = "rv-ok", previousStatus = "REVIEW"),
                BulkRevertItem(id = "rv-bad", previousStatus = "UNKNOWN_STATUS")
            )
            val decisions = listOf(
                ReviewItemDecision(summaryId = "rv-ok", categoryId = "cat-1", status = ReviewDecisionStatus.INCLUDE)
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("rv-ok", "rv-bad")) } returns decisions
            every { batchSummaryStore.findByIds(listOf("rv-bad")) } returns emptyList()
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            every { reviewItemAuditStore.batchAppend(any()) } answers { firstArg() }

            val result = service.bulkRevert(reverts)

            result.succeeded shouldBe listOf("rv-ok")
            result.failed.size shouldBe 1
            result.failed[0].id shouldBe "rv-bad"
            result.failed[0].code shouldBe "INVALID_STATUS"
        }

        @Test
        fun `존재하지 않는 summaryId의 revert는 NOT_FOUND로 실패 처리되고 나머지는 커밋된다`() {
            val reverts = listOf(
                BulkRevertItem(id = "rv-ok", previousStatus = "REVIEW"),
                BulkRevertItem(id = "rv-ghost", previousStatus = "REVIEW")
            )
            every { reviewItemDecisionStore.findBySummaryIds(listOf("rv-ok", "rv-ghost")) } returns emptyList()
            every { batchSummaryStore.findByIds(listOf("rv-ok", "rv-ghost")) } returns listOf(
                testSummary("rv-ok")
            )
            every { reviewItemDecisionStore.upsert(any()) } answers { firstArg() }
            every { reviewItemAuditStore.batchAppend(any()) } answers { firstArg() }

            val result = service.bulkRevert(reverts)

            result.succeeded shouldBe listOf("rv-ok")
            result.failed shouldHaveSize 1
            result.failed[0].id shouldBe "rv-ghost"
            result.failed[0].code shouldBe "NOT_FOUND"
        }

        @Test
        fun `reverts가 101개이면 InvalidInputException을 던진다`() {
            val reverts = (1..101).map { BulkRevertItem(id = "id-$it", previousStatus = "REVIEW") }

            val ex = io.kotest.assertions.throwables.shouldThrow<
                com.clipping.mcpserver.error.InvalidInputException
            > {
                service.bulkRevert(reverts)
            }

            ex.message shouldBe "벌크 되돌리기는 1~100건까지 가능합니다"
        }
    }

    @Nested
    inner class `getReviewStats 메서드` {

        @Test
        fun `데이터가 없으면 전체 정확도 0과 빈 카테고리 목록을 반환한다`() {
            every { reviewItemDecisionStore.getAccuracyStats(any(), any()) } returns emptyList()

            val result = service.getReviewStats("7d")

            result.period shouldBe "7d"
            result.totalReviewed shouldBe 0
            result.overallAccuracy shouldBe 0.0
            result.includeAccuracy shouldBe 0.0
            result.excludeAccuracy shouldBe 0.0
            result.overriddenCount shouldBe 0
            result.previousPeriodAccuracy shouldBe null
            result.categoryBreakdown shouldBe emptyList()
        }

        @Test
        fun `일치하는 항목이 있으면 정확도를 올바르게 계산한다`() {
            val rows = listOf(
                com.clipping.mcpserver.store.AccuracyRow("cat-1", "카테고리1", "INCLUDE", "INCLUDE", 3),
                com.clipping.mcpserver.store.AccuracyRow("cat-1", "카테고리1", "INCLUDE", "EXCLUDE", 1),
                com.clipping.mcpserver.store.AccuracyRow("cat-1", "카테고리1", "EXCLUDE", "EXCLUDE", 2)
            )
            every { reviewItemDecisionStore.getAccuracyStats(any(), any()) } returnsMany listOf(rows, emptyList())

            val result = service.getReviewStats("7d")

            result.totalReviewed shouldBe 6
            result.overallAccuracy shouldBe (5.0 / 6.0)
            result.includeAccuracy shouldBe (3.0 / 4.0)
            result.excludeAccuracy shouldBe (2.0 / 2.0)
            result.overriddenCount shouldBe 1
            result.previousPeriodAccuracy shouldBe null
            result.categoryBreakdown.size shouldBe 1
            result.categoryBreakdown[0].categoryId shouldBe "cat-1"
            result.categoryBreakdown[0].totalReviewed shouldBe 6
        }

        @Test
        fun `이전 기간에 데이터가 있으면 previousPeriodAccuracy가 계산된다`() {
            val currentRows = listOf(
                com.clipping.mcpserver.store.AccuracyRow("cat-1", "카테고리1", "INCLUDE", "INCLUDE", 2)
            )
            val prevRows = listOf(
                com.clipping.mcpserver.store.AccuracyRow("cat-1", "카테고리1", "INCLUDE", "INCLUDE", 4),
                com.clipping.mcpserver.store.AccuracyRow("cat-1", "카테고리1", "INCLUDE", "EXCLUDE", 1)
            )
            every { reviewItemDecisionStore.getAccuracyStats(any(), any()) } returnsMany listOf(currentRows, prevRows)

            val result = service.getReviewStats("30d")

            result.period shouldBe "30d"
            result.previousPeriodAccuracy shouldBe (4.0 / 5.0)
        }
    }

    private fun testSummary(id: String): BatchSummary =
        BatchSummary(
            id = id,
            originalTitle = "테스트 기사 $id",
            summary = "요약 내용",
            keywords = listOf("테스트"),
            importanceScore = 0.7f,
            sourceLink = "https://example.com/$id",
            categoryId = "cat-1",
            rssItemId = "item-$id"
        )

    /** perCategory 샘플링 테스트용 — 카테고리/점수를 명시 지정한 BatchSummary 생성. */
    private fun summaryWithScore(id: String, categoryId: String, score: Float): BatchSummary =
        testSummary(id).copy(categoryId = categoryId, importanceScore = score)
}
