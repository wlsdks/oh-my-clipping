package com.ohmyclipping.service

import com.ohmyclipping.error.ConflictException
import com.ohmyclipping.error.InvalidInputException
import com.ohmyclipping.error.NotFoundException
import com.ohmyclipping.model.ReviewDecisionStatus
import com.ohmyclipping.model.ReviewItemAudit
import com.ohmyclipping.model.ReviewItemDecision
import com.ohmyclipping.service.query.ReviewPolicyQueryHelper
import com.ohmyclipping.store.AutoExcludedRow
import com.ohmyclipping.store.BatchSummaryStore
import com.ohmyclipping.store.CategoryStore
import com.ohmyclipping.store.ReviewItemAuditStore
import com.ohmyclipping.store.ReviewItemDecisionStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [AdminReviewQueueService.listAutoExcluded] / [AdminReviewQueueService.restoreFromAutoExclude]
 * 단위 테스트.
 *
 * 검증 관점:
 *  - 필터 파라미터(categoryId/reason/days)가 store 호출로 그대로 전달된다
 *  - page/size 가 clamp 되고 offset 이 정확히 계산된다
 *  - reasonBreakdown 이 그대로 응답에 매핑된다
 *  - restore 는 policy-auto EXCLUDE 만 허용 (NotFound / Conflict 가드)
 *  - restore 성공 시 decision.upsert + audit.append 가 각각 정확히 1 회
 */
class AdminReviewQueueServiceAutoExcludeAuditTest {

    private val batchSummaryStore = mockk<BatchSummaryStore>(relaxed = true)
    private val categoryStore = mockk<CategoryStore>(relaxed = true)
    private val categoryRuleService = mockk<AdminCategoryRuleService>(relaxed = true)
    private val reviewItemDecisionStore = mockk<ReviewItemDecisionStore>(relaxed = true)
    private val reviewItemAuditStore = mockk<ReviewItemAuditStore>(relaxed = true)
    private val reviewPolicyQueryHelper = mockk<ReviewPolicyQueryHelper>(relaxed = true)
    private val ruleEvaluator = mockk<ReviewPolicyRuleEvaluator>(relaxed = true)

    private val service = AdminReviewQueueService(
        batchSummaryStore = batchSummaryStore,
        categoryStore = categoryStore,
        categoryRuleService = categoryRuleService,
        reviewItemDecisionStore = reviewItemDecisionStore,
        reviewItemAuditStore = reviewItemAuditStore,
        reviewPolicyQueryHelper = reviewPolicyQueryHelper,
        ruleEvaluator = ruleEvaluator,
    )

    private fun row(
        summaryId: String,
        reason: String = "rule:event_type_blacklist",
        score: Float = 0.4f,
        summary: String = "Summary body for $summaryId",
        sourceUrl: String? = "https://example.com/article/$summaryId",
        sourceName: String? = "Example News",
        publishedAt: Instant? = Instant.parse("2026-04-17T09:00:00Z"),
        eventType: String? = "OTHER",
        sentiment: String? = "NEUTRAL",
        originalTitle: String = "Title $summaryId",
        translatedTitle: String? = null,
        categoryId: String = "cat-a",
    ) = AutoExcludedRow(
        summaryId = summaryId,
        title = translatedTitle ?: originalTitle,
        originalTitle = originalTitle,
        translatedTitle = translatedTitle,
        categoryId = categoryId,
        categoryName = "Cat A",
        score = score,
        reason = reason,
        excludedAt = Instant.parse("2026-04-18T10:00:00Z"),
        summary = summary,
        sourceUrl = sourceUrl,
        sourceName = sourceName,
        publishedAt = publishedAt,
        eventType = eventType,
        sentiment = sentiment,
    )

    @Nested
    inner class `listAutoExcluded 정상 경로` {

        @Test
        fun `store 결과를 그대로 응답으로 매핑하고 breakdown 을 포함한다`() {
            // Given: store 가 2 개 row + totalCount=7 + breakdown 반환
            every {
                reviewItemDecisionStore.findAutoExcluded(any(), null, null, 20, 0)
            } returns listOf(row("s1"), row("s2", reason = "rule:zero_signal", score = 0.0f))
            every {
                reviewItemDecisionStore.countAutoExcluded(any(), null, null)
            } returns 7
            every {
                reviewItemDecisionStore.breakdownAutoExcludedByReason(any(), null, null)
            } returns mapOf("rule:event_type_blacklist" to 5, "rule:zero_signal" to 2)

            // When
            val resp = service.listAutoExcluded()

            // Then: 매핑된 응답이 DTO 구조를 유지한다
            resp.items shouldHaveSize 2
            resp.items[0].summaryId shouldBe "s1"
            resp.items[0].reason shouldBe "rule:event_type_blacklist"
            resp.items[1].reason shouldBe "rule:zero_signal"
            resp.totalCount shouldBe 7
            resp.reasonBreakdown shouldContainKey "rule:event_type_blacklist"
            resp.reasonBreakdown["rule:zero_signal"] shouldBe 2
        }

        @Test
        fun `categoryId 필터가 store 에 그대로 전달된다`() {
            // capture 로 store 호출 인자를 검증
            val capturedCategory = slot<String>()
            every {
                reviewItemDecisionStore.findAutoExcluded(any(), capture(capturedCategory), null, any(), any())
            } returns emptyList()
            every {
                reviewItemDecisionStore.countAutoExcluded(any(), capture(capturedCategory), null)
            } returns 0
            every {
                reviewItemDecisionStore.breakdownAutoExcludedByReason(any(), capture(capturedCategory), null)
            } returns emptyMap()

            service.listAutoExcluded(categoryId = "cat-xyz")

            capturedCategory.captured shouldBe "cat-xyz"
        }

        @Test
        fun `reason prefix 필터가 store 에 전달된다`() {
            val capturedReason = slot<String>()
            every {
                reviewItemDecisionStore.findAutoExcluded(any(), null, capture(capturedReason), any(), any())
            } returns emptyList()
            every {
                reviewItemDecisionStore.countAutoExcluded(any(), null, capture(capturedReason))
            } returns 0
            every {
                reviewItemDecisionStore.breakdownAutoExcludedByReason(any(), null, capture(capturedReason))
            } returns emptyMap()

            service.listAutoExcluded(reason = "rule:event_type_blacklist")

            capturedReason.captured shouldBe "rule:event_type_blacklist"
        }

        @Test
        fun `page size 는 1~100 으로 clamp 되고 offset 이 page size 로 계산된다`() {
            // size=500 → 100 으로 clamp, page=2 → offset=200
            val capturedLimit = slot<Int>()
            val capturedOffset = slot<Int>()
            every {
                reviewItemDecisionStore.findAutoExcluded(any(), null, null, capture(capturedLimit), capture(capturedOffset))
            } returns emptyList()
            every { reviewItemDecisionStore.countAutoExcluded(any(), null, null) } returns 0
            every { reviewItemDecisionStore.breakdownAutoExcludedByReason(any(), null, null) } returns emptyMap()

            service.listAutoExcluded(page = 2, size = 500)

            capturedLimit.captured shouldBe 100
            capturedOffset.captured shouldBe 200
        }

        @Test
        fun `AutoExcludedRow 의 신규 9 개 필드가 AutoExcludedItem 으로 그대로 전달된다`() {
            // Given: 상세 drawer 용 신규 필드(summary/sourceUrl/sourceName/publishedAt/
            // originalTitle/translatedTitle/categoryId/eventType/sentiment) 를 모두 채운 row
            val storeRow = row(
                summaryId = "s-detail",
                summary = "이 기사는 광고성 콘텐츠로 판정되어 자동 제외되었습니다.",
                sourceUrl = "https://news.example.com/articles/42",
                sourceName = "Example Daily",
                publishedAt = Instant.parse("2026-04-17T09:00:00Z"),
                eventType = "OTHER",
                sentiment = "NEUTRAL",
                originalTitle = "Original Headline",
                translatedTitle = "번역된 제목",
                categoryId = "cat-deep",
            )
            every {
                reviewItemDecisionStore.findAutoExcluded(any(), null, null, any(), any())
            } returns listOf(storeRow)
            every { reviewItemDecisionStore.countAutoExcluded(any(), null, null) } returns 1
            every { reviewItemDecisionStore.breakdownAutoExcludedByReason(any(), null, null) } returns emptyMap()

            val resp = service.listAutoExcluded()

            // 신규 9 개 필드가 DTO 로 정확히 전달됐는지 각각 검증
            val item = resp.items.single()
            item.summaryId shouldBe "s-detail"
            item.title shouldBe "번역된 제목"
            item.originalTitle shouldBe "Original Headline"
            item.translatedTitle shouldBe "번역된 제목"
            item.categoryId shouldBe "cat-deep"
            item.summary shouldBe "이 기사는 광고성 콘텐츠로 판정되어 자동 제외되었습니다."
            item.sourceUrl shouldBe "https://news.example.com/articles/42"
            item.sourceName shouldBe "Example Daily"
            item.publishedAt shouldBe Instant.parse("2026-04-17T09:00:00Z")
            item.eventType shouldBe "OTHER"
            item.sentiment shouldBe "NEUTRAL"
        }

        @Test
        fun `rss_items 가 orphan 인 경우 sourceUrl sourceName publishedAt 이 null 로 전달된다`() {
            // LEFT JOIN 방어: rss_items FK 가 존재하지만 CASCADE 없이 삭제된 이론적 케이스.
            // store 가 null 을 돌려줘도 매퍼가 그대로 AutoExcludedItem.null 로 전달해야 한다.
            val orphanRow = row(
                summaryId = "s-orphan",
                sourceUrl = null,
                sourceName = null,
                publishedAt = null,
            )
            every {
                reviewItemDecisionStore.findAutoExcluded(any(), null, null, any(), any())
            } returns listOf(orphanRow)
            every { reviewItemDecisionStore.countAutoExcluded(any(), null, null) } returns 1
            every { reviewItemDecisionStore.breakdownAutoExcludedByReason(any(), null, null) } returns emptyMap()

            val resp = service.listAutoExcluded()

            val item = resp.items.single()
            item.sourceUrl shouldBe null
            item.sourceName shouldBe null
            item.publishedAt shouldBe null
            // 나머지 배치 필드는 여전히 채워져야 한다
            item.summaryId shouldBe "s-orphan"
            item.title shouldBe "Title s-orphan"
            item.summary shouldBe "Summary body for s-orphan"
        }

        @Test
        fun `blank 필터는 null 과 동일하게 처리된다`() {
            // UI 가 초기화 직후 빈 문자열을 보내는 경우 대비
            every {
                reviewItemDecisionStore.findAutoExcluded(any(), null, null, any(), any())
            } returns emptyList()
            every { reviewItemDecisionStore.countAutoExcluded(any(), null, null) } returns 0
            every { reviewItemDecisionStore.breakdownAutoExcludedByReason(any(), null, null) } returns emptyMap()

            service.listAutoExcluded(categoryId = "   ", reason = "")

            // store 에 null 이 전달됨을 verify
            verify { reviewItemDecisionStore.findAutoExcluded(any(), null, null, 20, 0) }
        }
    }

    @Nested
    inner class `listAutoExcluded 에러 경로` {

        @Test
        fun `page 가 음수면 InvalidInputException 을 던진다`() {
            shouldThrow<InvalidInputException> {
                service.listAutoExcluded(page = -1)
            }
        }
    }

    @Nested
    inner class `restoreFromAutoExclude 정상 경로` {

        @Test
        fun `policy-auto EXCLUDE 항목을 REVIEW 로 upsert 하고 감사 이력을 남긴다`() {
            val existing = ReviewItemDecision(
                summaryId = "s1",
                categoryId = "cat-1",
                status = ReviewDecisionStatus.EXCLUDE,
                suggestedStatus = ReviewDecisionStatus.EXCLUDE,
                reason = "rule:event_type_blacklist",
                reviewedBy = "policy-auto",
                reviewedAt = Instant.parse("2026-04-18T10:00:00Z"),
            )
            every { reviewItemDecisionStore.findBySummaryId("s1") } returns existing
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers { decisionSlot.captured }
            val auditSlot = slot<ReviewItemAudit>()
            every { reviewItemAuditStore.append(capture(auditSlot)) } answers { auditSlot.captured }

            val result = service.restoreFromAutoExclude(summaryId = "s1", actor = "admin-alice")

            // 응답 필드 검증
            result.summaryId shouldBe "s1"
            result.newStatus shouldBe "REVIEW"

            // upsert 페이로드 검증
            decisionSlot.captured.status shouldBe ReviewDecisionStatus.REVIEW
            decisionSlot.captured.reviewedBy shouldBe "admin-alice"
            decisionSlot.captured.reason shouldBe "manual_restore_from_auto_exclude"
            decisionSlot.captured.categoryId shouldBe "cat-1"

            // audit 이력 검증 — fromStatus=EXCLUDE, toStatus=REVIEW
            auditSlot.captured.fromStatus shouldBe ReviewDecisionStatus.EXCLUDE
            auditSlot.captured.toStatus shouldBe ReviewDecisionStatus.REVIEW
            auditSlot.captured.reason shouldBe "manual_restore_from_auto_exclude"
            auditSlot.captured.reviewedBy shouldBe "admin-alice"

            verify(exactly = 1) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 1) { reviewItemAuditStore.append(any()) }
        }

        @Test
        fun `actor 가 blank 면 admin 으로 fallback 한다`() {
            val existing = ReviewItemDecision(
                summaryId = "s2",
                categoryId = "cat-2",
                status = ReviewDecisionStatus.EXCLUDE,
                reviewedBy = "policy-auto",
            )
            every { reviewItemDecisionStore.findBySummaryId("s2") } returns existing
            val decisionSlot = slot<ReviewItemDecision>()
            every { reviewItemDecisionStore.upsert(capture(decisionSlot)) } answers { decisionSlot.captured }

            service.restoreFromAutoExclude(summaryId = "s2", actor = "   ")

            decisionSlot.captured.reviewedBy shouldBe "admin"
        }
    }

    @Nested
    inner class `restoreFromAutoExclude 에러 경로` {

        @Test
        fun `summary id 에 해당하는 결정이 없으면 NotFoundException`() {
            every { reviewItemDecisionStore.findBySummaryId("missing") } returns null

            val ex = shouldThrow<NotFoundException> {
                service.restoreFromAutoExclude(summaryId = "missing", actor = "admin")
            }
            ex.message.orEmpty() shouldContain "missing"
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
            verify(exactly = 0) { reviewItemAuditStore.append(any()) }
        }

        @Test
        fun `정책 자동이 아닌 EXCLUDE 는 ConflictException`() {
            // 사람이 직접 EXCLUDE 한 항목은 이 엔드포인트로 되돌릴 수 없다
            val existing = ReviewItemDecision(
                summaryId = "s3",
                categoryId = "cat-3",
                status = ReviewDecisionStatus.EXCLUDE,
                reviewedBy = "alice",
            )
            every { reviewItemDecisionStore.findBySummaryId("s3") } returns existing

            shouldThrow<ConflictException> {
                service.restoreFromAutoExclude(summaryId = "s3", actor = "admin")
            }
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
        }

        @Test
        fun `EXCLUDE 가 아닌 항목은 ConflictException`() {
            // 이미 REVIEW/INCLUDE 인 항목은 이 엔드포인트와 무관
            val existing = ReviewItemDecision(
                summaryId = "s4",
                categoryId = "cat-4",
                status = ReviewDecisionStatus.REVIEW,
                reviewedBy = "policy-auto",
            )
            every { reviewItemDecisionStore.findBySummaryId("s4") } returns existing

            shouldThrow<ConflictException> {
                service.restoreFromAutoExclude(summaryId = "s4", actor = "admin")
            }
            verify(exactly = 0) { reviewItemDecisionStore.upsert(any()) }
        }
    }
}
